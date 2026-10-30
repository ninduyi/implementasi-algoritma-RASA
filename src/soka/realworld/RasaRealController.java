package soka.realworld;

import soka.simulasi.GoCjDatasetGenerator;
import soka.simulasi.RasaScheduler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controller Orkestrasi Real-World AWS untuk Penjadwalan Algoritma RASA.
 *
 * Menggunakan logika RASA murni dari soka.simulasi.RasaScheduler,
 * kompatibel penuh dengan seluruh versi Java (Java 8 s.d. Java 17+),
 * mengalokasikan task ke 4 VM guest AWS melalui HTTP Worker Agent,
 * mengukur durasi wall-clock dan CPU aktual, serta mengekspor artefak hasil eksperimen.
 */
public final class RasaRealController {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration EXECUTE_TIMEOUT = Duration.ofSeconds(120);
    private static final long CALIBRATION_ITERATIONS = 10_000_000L;

    public static final class WorkerConfig {
        public final String hostId;
        public final String vmId;
        public final String endpoint;
        public final int slots;
        public final double modeledMips;

        public WorkerConfig(String hostId, String vmId, String endpoint, int slots, double modeledMips) {
            this.hostId = hostId;
            this.vmId = vmId;
            this.endpoint = endpoint;
            this.slots = slots;
            this.modeledMips = modeledMips;
        }
    }

    public static final class ClusterConfig {
        public final String datacenterId;
        public final String region;
        public final String availabilityZone;
        public final List<WorkerConfig> workers;

        public ClusterConfig(String datacenterId, String region, String availabilityZone, List<WorkerConfig> workers) {
            this.datacenterId = datacenterId;
            this.region = region;
            this.availabilityZone = availabilityZone;
            this.workers = workers;
        }
    }

    public static final class HealthResult {
        public final WorkerConfig config;
        public final String hostname;
        public final int slots;
        public final int busySlots;

        public HealthResult(WorkerConfig config, String hostname, int slots, int busySlots) {
            this.config = config;
            this.hostname = hostname;
            this.slots = slots;
            this.busySlots = busySlots;
        }
    }

    public static class TaskResult {
        public final int taskId;
        public final double lengthMi;
        public final long iterations;
        public final String hostId;
        public final String vmId;
        public final String strategy;
        public final double expectedCt;
        public final double dispatchTimeSec;
        public final double completeTimeSec;
        public final double executionDurationSec;
        public final double cpuDurationSec;
        public final String checksum;
        public final boolean success;
        public final String error;

        public TaskResult(int taskId, double lengthMi, long iterations, String hostId, String vmId,
                          String strategy, double expectedCt, double dispatchTimeSec, double completeTimeSec,
                          double executionDurationSec, double cpuDurationSec, String checksum,
                          boolean success, String error) {
            this.taskId = taskId;
            this.lengthMi = lengthMi;
            this.iterations = iterations;
            this.hostId = hostId;
            this.vmId = vmId;
            this.strategy = strategy;
            this.expectedCt = expectedCt;
            this.dispatchTimeSec = dispatchTimeSec;
            this.completeTimeSec = completeTimeSec;
            this.executionDurationSec = executionDurationSec;
            this.cpuDurationSec = cpuDurationSec;
            this.checksum = checksum;
            this.success = success;
            this.error = error;
        }
    }

    public static void main(String[] args) {
        String configPathStr = "config/workers.json";
        String datasetPathStr = "dataset/GoCJ_Dataset_600.txt";
        String outputPathStr = null;
        int limit = 100;
        double iterationsPerMi = 10.0;
        boolean customIter = false;
        String algorithm = "RASA";
        boolean preflightOnly = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--config".equals(arg)) {
                if (++i < args.length) configPathStr = args[i];
            } else if ("--dataset".equals(arg)) {
                if (++i < args.length) datasetPathStr = args[i];
            } else if ("--output".equals(arg)) {
                if (++i < args.length) outputPathStr = args[i];
            } else if ("--limit".equals(arg)) {
                if (++i < args.length) limit = Integer.parseInt(args[i]);
            } else if ("--iterations-per-mi".equals(arg)) {
                if (++i < args.length) {
                    iterationsPerMi = Double.parseDouble(args[i]);
                    customIter = true;
                }
            } else if ("--algorithm".equals(arg)) {
                if (++i < args.length) algorithm = args[i].toUpperCase();
            } else if ("--preflight-only".equals(arg)) {
                preflightOnly = true;
            } else if ("--calibration-file".equals(arg)) {
                if (++i < args.length) calibrationFile = args[i];
            } else if ("--help".equals(arg) || "-h".equals(arg)) {
                printUsage();
                return;
            }
        }

        // Auto-select dataset sesuai skala (100, 200, 300, 400, 500, 600)
        if ("dataset/GoCJ_Dataset_600.txt".equals(datasetPathStr)) {
            Path scaleDataset = Path.of("dataset/GoCJ_Dataset_" + limit + ".txt");
            if (Files.exists(scaleDataset)) {
                datasetPathStr = scaleDataset.toString();
            }
        }

        // Jika limit >= 100 dan user tidak set rasio manual, gunakan 1.0 agar durasi eksekusi wajar
        if (!customIter && limit >= 100) {
            iterationsPerMi = 1.0;
        }

        String envToken = System.getenv("SOKA_WORKER_TOKEN");
        String token = (envToken != null && !envToken.isBlank()) ? envToken.trim() : "soka-rasa-secret-token-kel4";

        try {
            printBanner(algorithm, limit);
            ClusterConfig config = loadConfig(Path.of(configPathStr));
            HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

            // Tahap 1: Preflight
            System.out.println("[PREFLIGHT] Memeriksa konektivitas ke seluruh worker...");
            List<HealthResult> healthResults = performPreflight(client, config, token);
            printHealthTable(healthResults);
            System.out.println("[PREFLIGHT] Status: OK (" + healthResults.size() + " worker aktif dan siap)\n");

            if (preflightOnly) {
                System.out.println("[INFO] Mode preflight-only selesai.");
                return;
            }

            // Tahap 2: Kalibrasi IPS (Iterations Per Second)
            System.out.println("[CALIBRATE] Mengkalibrasi throughput komputasi SHA-256 worker...");
            double[] measuredIps = loadOrCalibrate(client, config, token);
            for (int i = 0; i < config.workers.size(); i++) {
                WorkerConfig w = config.workers.get(i);
                System.out.printf(Locale.US, "  - %-10s (%-12s): %,.0f iterasi/detik (median kalibrasi)%n",
                        w.vmId, w.hostId, measuredIps[i]);
            }
            System.out.println();

            // Tahap 3: Load Dataset
            System.out.printf(Locale.US, "[DATASET] Membaca %d task dari %s...%n", limit, datasetPathStr);
            double[] allTaskLengths = GoCjDatasetGenerator.loadFromFile(datasetPathStr);
            if (limit > allTaskLengths.length) limit = allTaskLengths.length;
            double[] taskLengths = Arrays.copyOf(allTaskLengths, limit);

            // Tahap 4: Penjadwalan RASA
            System.out.printf(Locale.US, "[SCHEDULE] Menjalankan algoritma %s...%n", algorithm);
            int nVm = config.workers.size();
            double[] vmMips = new double[nVm];
            for (int j = 0; j < nVm; j++) {
                vmMips[j] = measuredIps[j] / (iterationsPerMi > 0 ? iterationsPerMi : 10.0);
            }

            List<RasaScheduler.Assignment> assignments;
            long schedStartNanos = System.nanoTime();
            if ("RR".equals(algorithm)) {
                assignments = new ArrayList<RasaScheduler.Assignment>();
                for (int i = 0; i < taskLengths.length; i++) {
                    assignments.add(new RasaScheduler.Assignment(i, i % nVm, 0.0, "ROUND-ROBIN"));
                }
            } else if ("MIN_MIN".equals(algorithm)) {
                assignments = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.MIN_MIN);
            } else if ("MAX_MIN".equals(algorithm)) {
                assignments = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.MAX_MIN);
            } else {
                assignments = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.RASA);
            }
            double schedDurationMs = (System.nanoTime() - schedStartNanos) / 1_000_000.0;
            System.out.printf(Locale.US, "[SCHEDULE] Selesai dalam %.3f ms. %d assignment terbuat.%n%n",
                    schedDurationMs, assignments.size());

            // Tahap 5: Dispatch Workload ke Worker Nyata
            if (outputPathStr == null || outputPathStr.isBlank()) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                outputPathStr = "results/aws-demo-20/" + timestamp;
            }
            Path outputDir = Path.of(outputPathStr);
            Files.createDirectories(outputDir);

            System.out.println("[DISPATCH] Memulai eksekusi workload nyata pada VM guest AWS:");
            List<TaskResult> results = executeWorkload(client, config, token, taskLengths, assignments, iterationsPerMi);

            // Tahap 6: Perhitungan Metrik & Ekspor Hasil
            printSummary(algorithm, taskLengths.length, results, schedDurationMs, outputDir.toString());
            saveArtifacts(outputDir, algorithm, config, results, schedDurationMs, iterationsPerMi, datasetPathStr);

        } catch (Exception e) {
            System.err.println("\n[ERROR] Eksekusi eksperimen gagal: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printBanner(String algorithm, int tasks) {
        System.out.println("============================================================");
        System.out.println("       SOKA REAL-WORLD RASA SCHEDULER DEMO (AWS)");
        System.out.println("============================================================");
        System.out.printf(Locale.US, "[INFO] Algoritma   : %s%n", algorithm);
        System.out.printf(Locale.US, "[INFO] Topologi     : 2 EC2 Host, 4 VM Guest (KVM/libvirt)%n");
        System.out.printf(Locale.US, "[INFO] Jumlah Task : %d task independen (Dataset GoCJ)%n", tasks);
        System.out.println("------------------------------------------------------------\n");
    }

    private static void printUsage() {
        System.out.println("Penggunaan CLI RasaRealController:");
        System.out.println("  java soka.realworld.RasaRealController [options]");
        System.out.println("Opsi:");
        System.out.println("  --config <path>             Path file config JSON worker (default: config/workers.json)");
        System.out.println("  --dataset <path>            Path dataset GoCJ (default: dataset/GoCJ_Dataset_600.txt)");
        System.out.println("  --limit <n>                 Jumlah task yang dieksekusi (default: 20)");
        System.out.println("  --iterations-per-mi <d>     Rasio iterasi SHA-256 per MI (default: 10.0)");
        System.out.println("  --algorithm <name>          RASA (default), RR, MIN_MIN, MAX_MIN");
        System.out.println("  --output <dir>              Direktori penyimpanan hasil");
        System.out.println("  --preflight-only            Hanya uji koneksi dan status worker");
    }

    public static ClusterConfig loadConfig(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8);
        String dc = findJsonString(content, "datacenterId", "aws-sydney");
        String region = findJsonString(content, "region", "ap-southeast-2");
        String az = findJsonString(content, "availabilityZone", "ap-southeast-2a");

        List<WorkerConfig> workers = new ArrayList<WorkerConfig>();
        Pattern workerPattern = Pattern.compile("\\{[^\\{\\}]*\"vmId\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\\}");
        Matcher m = workerPattern.matcher(content);
        while (m.find()) {
            String block = m.group(0);
            String hostId = findJsonString(block, "hostId", "host");
            String vmId = findJsonString(block, "vmId", "vm");
            String endpoint = findJsonString(block, "endpoint", "http://127.0.0.1:8080");
            int slots = findJsonInt(block, "slots", 1);
            double mips = findJsonDouble(block, "modeledMips", 1000.0);
            workers.add(new WorkerConfig(hostId, vmId, endpoint, slots, mips));
        }

        if (workers.isEmpty()) {
            throw new IllegalArgumentException("Konfigurasi worker tidak ditemukan dalam " + path);
        }
        return new ClusterConfig(dc, region, az, workers);
    }

    private static String findJsonString(String json, String key, String defaultVal) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        return m.find() ? m.group(1) : defaultVal;
    }

    private static int findJsonInt(String json, String key, int defaultVal) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9]+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultVal;
    }

    private static double findJsonDouble(String json, String key, double defaultVal) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*([0-9.]+)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : defaultVal;
    }

    public static List<HealthResult> performPreflight(HttpClient client, ClusterConfig config, String token) throws Exception {
        List<HealthResult> results = new ArrayList<HealthResult>();
        for (WorkerConfig w : config.workers) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(w.endpoint + "/health"))
                    .timeout(HEALTH_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Worker " + w.vmId + " gagal /health: HTTP " + resp.statusCode());
            }

            String body = resp.body();
            String hostname = findJsonString(body, "hostname", "unknown");
            int slots = findJsonInt(body, "slots", w.slots);
            int busySlots = findJsonInt(body, "busySlots", 0);

            results.add(new HealthResult(w, hostname, slots, busySlots));
        }
        return results;
    }

    private static void printHealthTable(List<HealthResult> results) {
        System.out.println("+---------------+--------------+----------------------+-------+-----------+----------------------+");
        System.out.println("| Host ID       | VM ID        | Hostname Tamu        | Slots | BusySlots | Endpoint             |");
        System.out.println("+---------------+--------------+----------------------+-------+-----------+----------------------+");
        for (HealthResult r : results) {
            System.out.printf(Locale.US, "| %-13s | %-12s | %-20s | %-5d | %-9d | %-20s |%n",
                    r.config.hostId, r.config.vmId, r.hostname, r.slots, r.busySlots, r.config.endpoint);
        }
        System.out.println("+---------------+--------------+----------------------+-------+-----------+----------------------+");
    }

    private static String calibrationFile = null;

    private static double[] loadOrCalibrate(HttpClient client, ClusterConfig config, String token) throws Exception {
        int n = config.workers.size();
        if (calibrationFile != null) {
            Path p = Path.of(calibrationFile);
            if (java.nio.file.Files.exists(p)) {
                java.util.Properties props = new java.util.Properties();
                try (java.io.Reader r = java.nio.file.Files.newBufferedReader(p)) { props.load(r); }
                double[] ips = new double[n];
                boolean ok = true;
                for (int i = 0; i < n; i++) {
                    String v = props.getProperty(config.workers.get(i).vmId);
                    if (v == null) { ok = false; break; }
                    ips[i] = Double.parseDouble(v.trim());
                }
                if (ok) {
                    System.out.println("[CALIBRATE] Memakai kalibrasi tersimpan: " + p);
                    return ips;
                }
            }
        }
        double[] ips = calibrateCluster(client, config, token);
        if (calibrationFile != null) {
            java.util.Properties props = new java.util.Properties();
            for (int i = 0; i < n; i++) {
                props.setProperty(config.workers.get(i).vmId, Double.toString(ips[i]));
            }
            Path p = Path.of(calibrationFile);
            if (p.getParent() != null) java.nio.file.Files.createDirectories(p.getParent());
            try (java.io.Writer w = java.nio.file.Files.newBufferedWriter(p)) {
                props.store(w, "Kalibrasi IPS per VM");
            }
        }
        return ips;
    }

    public static double[] calibrateCluster(HttpClient client, ClusterConfig config, String token) throws Exception {
        int n = config.workers.size();
        double[] medians = new double[n];

        for (int i = 0; i < n; i++) {
            WorkerConfig w = config.workers.get(i);
            // 1 warm-up
            runCalibrationTask(client, w, token, CALIBRATION_ITERATIONS);

            // 3 sampel ukur
            double[] samples = new double[5];
            for (int s = 0; s < 5; s++) {
                double dur = runCalibrationTask(client, w, token, CALIBRATION_ITERATIONS);
                samples[s] = CALIBRATION_ITERATIONS / Math.max(dur, 0.001);
            }
            Arrays.sort(samples);
            medians[i] = samples[2]; // median dari 5 sampel
        }
        return medians;
    }

    private static double runCalibrationTask(HttpClient client, WorkerConfig worker, String token, long iter) throws Exception {
        String body = String.format(Locale.US, "{\"taskId\": 9999, \"iterations\": %d}", iter);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(worker.endpoint + "/execute"))
                .timeout(EXECUTE_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        long t0 = System.nanoTime();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        long t1 = System.nanoTime();
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Kalibrasi worker " + worker.vmId + " gagal: HTTP " + resp.statusCode());
        }
        return findJsonDouble(resp.body(), "executionSeconds", (t1 - t0) / 1_000_000_000.0);
    }

    public static List<TaskResult> executeWorkload(HttpClient client, ClusterConfig config, String token,
                                                   double[] taskLengths, List<RasaScheduler.Assignment> assignments,
                                                   double iterationsPerMi) {
        final List<TaskResult> results = Collections.synchronizedList(new ArrayList<TaskResult>());
        final long batchStartNanos = System.nanoTime();

        final Map<Integer, RasaScheduler.Assignment> assignMap = new HashMap<Integer, RasaScheduler.Assignment>();
        for (RasaScheduler.Assignment a : assignments) {
            assignMap.put(a.taskIndex, a);
        }

        // Kelompokkan task per VM
        int numWorkers = config.workers.size();
        final List<List<Integer>> vmQueues = new ArrayList<List<Integer>>();
        for (int v = 0; v < numWorkers; v++) {
            vmQueues.add(new ArrayList<Integer>());
        }
        for (int taskId = 0; taskId < taskLengths.length; taskId++) {
            RasaScheduler.Assignment a = assignMap.get(taskId);
            vmQueues.get(a.vmIndex).add(taskId);
        }

        // Cetak ringkasan antrian per VM
        for (int v = 0; v < numWorkers; v++) {
            WorkerConfig w = config.workers.get(v);
            List<Integer> q = vmQueues.get(v);
            System.out.printf(Locale.US, "[QUEUE] %-10s (%s): %d task dialokasikan%n",
                    w.vmId, w.hostId, q.size());
        }
        System.out.println("[PARALLEL] Mengeksekusi workload secara bersamaan (konkuren) pada 4 VM guest AWS...\n");

        ExecutorService executor = Executors.newFixedThreadPool(numWorkers);
        List<Future<?>> futures = new ArrayList<Future<?>>();

        for (int v = 0; v < numWorkers; v++) {
            final int vmIdx = v;
            final WorkerConfig worker = config.workers.get(vmIdx);
            final List<Integer> queue = vmQueues.get(vmIdx);

            futures.add(executor.submit(new Runnable() {
                @Override
                public void run() {
                    for (int taskId : queue) {
                        double lengthMi = taskLengths[taskId];
                        long iterations = (long) Math.ceil(lengthMi * iterationsPerMi);
                        RasaScheduler.Assignment a = assignMap.get(taskId);

                        double dispatchTimeSec = (System.nanoTime() - batchStartNanos) / 1_000_000_000.0;
                        String body = String.format(Locale.US, "{\"taskId\": %d, \"iterations\": %d}", taskId, iterations);
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(worker.endpoint + "/execute"))
                                .timeout(EXECUTE_TIMEOUT)
                                .header("Authorization", "Bearer " + token)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();

                        try {
                            long taskSendNanos = System.nanoTime();
                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                            long taskRecvNanos = System.nanoTime();
                            double completeTimeSec = (taskRecvNanos - batchStartNanos) / 1_000_000_000.0;

                            if (resp.statusCode() == 200) {
                                String respBody = resp.body();
                                double execSec = findJsonDouble(respBody, "executionSeconds", (taskRecvNanos - taskSendNanos) / 1e9);
                                double cpuSec = findJsonDouble(respBody, "cpuSeconds", execSec);
                                String checksum = findJsonString(respBody, "checksum", "verified");

                                if (taskLengths.length <= 40 || taskId % 25 == 0 || taskId == taskLengths.length - 1) {
                                    System.out.printf(Locale.US, "  └─ [WORKER] %-10s Task-%03d (%7.0f MI) -> Selesai: %6.4f s (CPU: %6.4f s) | Hash: %s...%n",
                                            worker.vmId, taskId, lengthMi, execSec, cpuSec, checksum.substring(0, Math.min(10, checksum.length())));
                                }

                                results.add(new TaskResult(taskId, lengthMi, iterations, worker.hostId, worker.vmId,
                                        a.strategy, a.completionTime, dispatchTimeSec, completeTimeSec, execSec, cpuSec, checksum, true, null));
                            } else {
                                System.err.printf(Locale.US, "  └─ [ERROR]  Task-%03d GAGAL pada %s: HTTP %d%n",
                                        taskId, worker.vmId, resp.statusCode());
                                results.add(new TaskResult(taskId, lengthMi, iterations, worker.hostId, worker.vmId,
                                        a.strategy, a.completionTime, dispatchTimeSec, completeTimeSec, 0, 0, null, false, "HTTP " + resp.statusCode()));
                            }
                        } catch (Exception e) {
                            double completeTimeSec = (System.nanoTime() - batchStartNanos) / 1_000_000_000.0;
                            System.err.printf(Locale.US, "  └─ [ERROR]  Task-%03d GAGAL pada %s: %s%n", taskId, worker.vmId, e.getMessage());
                            results.add(new TaskResult(taskId, lengthMi, iterations, worker.hostId, worker.vmId,
                                    a.strategy, a.completionTime, dispatchTimeSec, completeTimeSec, 0, 0, null, false, e.getMessage()));
                        }
                    }
                }
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (Exception e) {
                System.err.println("Thread execution interrupted: " + e.getMessage());
            }
        }
        executor.shutdown();

        // Urutkan hasil kembali berdasarkan taskId
        Collections.sort(results, new Comparator<TaskResult>() {
            @Override
            public int compare(TaskResult o1, TaskResult o2) {
                return Integer.compare(o1.taskId, o2.taskId);
            }
        });

        return results;
    }

    private static void printSummary(String algorithm, int totalTasks, List<TaskResult> results,
                                     double schedDurationMs, String outDir) {
        int successCount = 0;
        int failCount = 0;
        double sumExecutionSec = 0;
        double minComplete = Double.MAX_VALUE;
        double maxComplete = 0;

        Map<String, Integer> tasksPerVm = new HashMap<String, Integer>();
        Map<String, Double> durationPerVm = new HashMap<String, Double>();

        for (TaskResult r : results) {
            if (r.success) {
                successCount++;
                sumExecutionSec += r.executionDurationSec;
                minComplete = Math.min(minComplete, r.completeTimeSec);
                maxComplete = Math.max(maxComplete, r.completeTimeSec);

                tasksPerVm.merge(r.vmId, 1, Integer::sum);
                durationPerVm.merge(r.vmId, r.executionDurationSec, Double::sum);
            } else {
                failCount++;
            }
        }

        double makespan = maxComplete;
        double avgDuration = successCount > 0 ? sumExecutionSec / successCount : 0;
        double throughput = makespan > 0 ? (successCount / makespan) : 0;

        double maxVmDur = 0, minVmDur = Double.MAX_VALUE, sumVmDur = 0;
        for (double d : durationPerVm.values()) {
            maxVmDur = Math.max(maxVmDur, d);
            minVmDur = Math.min(minVmDur, d);
            sumVmDur += d;
        }
        double avgVmDur = durationPerVm.isEmpty() ? 1.0 : sumVmDur / durationPerVm.size();
        double degreeOfImbalance = (maxVmDur - minVmDur) / (avgVmDur > 0 ? avgVmDur : 1.0);

        System.out.println("\n============================================================");
        System.out.println("                   EXPERIMENT SUMMARY");
        System.out.println("============================================================");
        System.out.printf(Locale.US, "Algoritma         : %s%n", algorithm);
        System.out.printf(Locale.US, "Total Task        : %d%n", totalTasks);
        System.out.printf(Locale.US, "Task Sukses       : %d%n", successCount);
        System.out.printf(Locale.US, "Task Gagal        : %d%n", failCount);
        System.out.printf(Locale.US, "Makespan Aktual   : %.4f detik%n", makespan);
        System.out.printf(Locale.US, "Rata-rata Durasi  : %.4f detik / task%n", avgDuration);
        System.out.printf(Locale.US, "Throughput        : %.4f task / detik%n", throughput);
        System.out.printf(Locale.US, "Degree of Imb.    : %.4f%n", degreeOfImbalance);
        System.out.printf(Locale.US, "Waktu Scheduler   : %.3f ms%n", schedDurationMs);
        System.out.printf(Locale.US, "Direktori Output  : %s%n", outDir);
        System.out.println("------------------------------------------------------------");
        System.out.println("Distribusi Beban per VM:");
        for (String vm : durationPerVm.keySet()) {
            System.out.printf(Locale.US, "  - %-10s : %2d task | %,.4f detik eksekusi%n",
                    vm, tasksPerVm.getOrDefault(vm, 0), durationPerVm.get(vm));
        }
        System.out.println("============================================================\n");
    }

    private static void saveArtifacts(Path outputDir, String algorithm, ClusterConfig config,
                                      List<TaskResult> results, double schedDurationMs,
                                      double iterationsPerMi, String datasetPath) throws IOException {
        Path csvPath = outputDir.resolve("tasks.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            pw.println("TaskId,Length_MI,Iterations,HostId,VmId,Strategy,ExpectedCt,Dispatch_s,Complete_s,Execution_s,Cpu_s,Status,Checksum");
            for (TaskResult r : results) {
                pw.printf(Locale.US, "%d,%.2f,%d,%s,%s,%s,%.4f,%.6f,%.6f,%.6f,%.6f,%s,%s%n",
                        r.taskId, r.lengthMi, r.iterations, r.hostId, r.vmId, r.strategy, r.expectedCt,
                        r.dispatchTimeSec, r.completeTimeSec, r.executionDurationSec, r.cpuDurationSec,
                        r.success ? "SUCCESS" : "FAILED", r.checksum != null ? r.checksum : "NONE");
            }
        }

        int successCount = 0;
        double makespan = 0;
        double sumExecution = 0;
        Map<String, Integer> vmTasks = new HashMap<String, Integer>();
        Map<String, Double> vmDurations = new HashMap<String, Double>();

        for (TaskResult r : results) {
            if (r.success) {
                successCount++;
                sumExecution += r.executionDurationSec;
                makespan = Math.max(makespan, r.completeTimeSec);
                vmTasks.merge(r.vmId, 1, Integer::sum);
                vmDurations.merge(r.vmId, r.executionDurationSec, Double::sum);
            }
        }

        double maxVm = 0, minVm = Double.MAX_VALUE, sumVm = 0;
        for (double d : vmDurations.values()) {
            maxVm = Math.max(maxVm, d);
            minVm = Math.min(minVm, d);
            sumVm += d;
        }
        double avgVm = vmDurations.isEmpty() ? 1.0 : sumVm / vmDurations.size();
        double di = (maxVm - minVm) / (avgVm > 0 ? avgVm : 1.0);

        Path jsonPath = outputDir.resolve("summary.json");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(jsonPath, StandardCharsets.UTF_8))) {
            pw.println("{");
            pw.printf(Locale.US, "  \"status\": \"%s\",%n", (successCount == results.size() ? "SUCCESS" : "PARTIAL"));
            pw.printf(Locale.US, "  \"algorithm\": \"%s\",%n", algorithm);
            pw.printf(Locale.US, "  \"source\": \"soka.simulasi.RasaScheduler\",%n");
            pw.printf(Locale.US, "  \"datacenterId\": \"%s\",%n", config.datacenterId);
            pw.printf(Locale.US, "  \"region\": \"%s\",%n", config.region);
            pw.printf(Locale.US, "  \"availabilityZone\": \"%s\",%n", config.availabilityZone);
            pw.printf(Locale.US, "  \"dataset\": \"%s\",%n", datasetPath.replace("\\", "/"));
            pw.printf(Locale.US, "  \"tasksRequested\": %d,%n", results.size());
            pw.printf(Locale.US, "  \"tasksCompleted\": %d,%n", successCount);
            pw.printf(Locale.US, "  \"iterationsPerMi\": %.2f,%n", iterationsPerMi);
            pw.printf(Locale.US, "  \"makespanSeconds\": %.6f,%n", makespan);
            pw.printf(Locale.US, "  \"averageDurationSeconds\": %.6f,%n", successCount > 0 ? sumExecution / successCount : 0);
            pw.printf(Locale.US, "  \"throughputTasksPerSec\": %.6f,%n", makespan > 0 ? successCount / makespan : 0);
            pw.printf(Locale.US, "  \"degreeOfImbalance\": %.6f,%n", di);
            pw.printf(Locale.US, "  \"schedulerRuntimeMs\": %.4f%n", schedDurationMs);
            pw.println("}");
        }

        Path reportPath = outputDir.resolve("README.md");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(reportPath, StandardCharsets.UTF_8))) {
            pw.println("# Laporan Eksperimen Real-World AWS");
            pw.printf(Locale.US, "- **Waktu Run**: %s%n", LocalDateTime.now().toString());
            pw.printf(Locale.US, "- **Algoritma**: %s%n", algorithm);
            pw.printf(Locale.US, "- **Jumlah Task**: %d%n", results.size());
            pw.printf(Locale.US, "- **Task Sukses**: %d / %d%n", successCount, results.size());
            pw.printf(Locale.US, "- **Makespan Aktual**: %.4f detik%n", makespan);
            pw.printf(Locale.US, "- **Degree of Imbalance**: %.4f%n", di);
            pw.println("\n## Distribusi per VM");
            pw.println("| VM ID | Host ID | Jumlah Task | Durasi Eksekusi Aktual (s) |");
            pw.println("|---|---|:---:|:---:|");
            for (WorkerConfig w : config.workers) {
                pw.printf(Locale.US, "| %s | %s | %d | %.4f |%n",
                        w.vmId, w.hostId, vmTasks.getOrDefault(w.vmId, 0), vmDurations.getOrDefault(w.vmId, 0.0));
            }
        }
        System.out.println("[RESULT] Seluruh artefak tersimpan di: " + outputDir.toAbsolutePath());
    }
}
