package soka.realworld;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Worker HTTP service mandiri untuk eksekusi workload nyata RASA di guest VM.
 * Kompatibel dengan seluruh versi Java (Java 8 s.d. Java 17+), zero external dependency.
 */
public final class RasaWorker {

    public static final class WorkerConfig {
        public final String datacenterId;
        public final String hostId;
        public final String vmId;
        public final int slots;
        public final String bind;
        public final int port;
        public final String token;

        public WorkerConfig(String datacenterId, String hostId, String vmId, int slots, String bind, int port, String token) {
            this.datacenterId = datacenterId;
            this.hostId = hostId;
            this.vmId = vmId;
            this.slots = slots;
            this.bind = bind;
            this.port = port;
            this.token = token;
        }
    }

    public static void main(String[] args) {
        String datacenterId = "aws-sydney";
        String hostId = null;
        String vmId = null;
        int slots = 1;
        String bind = "0.0.0.0";
        int port = 8080;
        String tokenArg = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--datacenter-id".equals(arg)) {
                if (++i < args.length) datacenterId = args[i];
            } else if ("--host-id".equals(arg)) {
                if (++i < args.length) hostId = args[i];
            } else if ("--vm-id".equals(arg)) {
                if (++i < args.length) vmId = args[i];
            } else if ("--slots".equals(arg)) {
                if (++i < args.length) slots = Integer.parseInt(args[i]);
            } else if ("--bind".equals(arg)) {
                if (++i < args.length) bind = args[i];
            } else if ("--port".equals(arg)) {
                if (++i < args.length) port = Integer.parseInt(args[i]);
            } else if ("--token".equals(arg)) {
                if (++i < args.length) tokenArg = args[i];
            }
        }

        String envToken = System.getenv("SOKA_WORKER_TOKEN");
        String token = (envToken != null && !envToken.isBlank()) ? envToken.trim() : tokenArg;

        if (token == null || token.isBlank()) {
            System.err.println("Error: Token wajib disetel via SOKA_WORKER_TOKEN atau argumen --token");
            System.exit(1);
        }

        if (hostId == null || vmId == null) {
            System.err.println("Error: --host-id dan --vm-id wajib diisi.");
            System.exit(1);
        }

        WorkerConfig config = new WorkerConfig(datacenterId, hostId, vmId, slots, bind, port, token);

        try {
            startServer(config);
            System.out.printf(Locale.US, "RASA_WORKER_READY vmId=%s hostId=%s port=%d slots=%d%n",
                    vmId, hostId, port, slots);
            System.out.flush();
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("Gagal menjalankan worker: " + e.getMessage());
            System.exit(1);
        }
    }

    public static HttpServer startServer(WorkerConfig config) throws IOException {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown-host";
        }

        Semaphore slotSemaphore = new Semaphore(config.slots);
        ExecutorService executor = Executors.newFixedThreadPool(config.slots + 2, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        HttpServer server = HttpServer.create(new InetSocketAddress(config.bind, config.port), 0);
        server.setExecutor(executor);

        server.createContext("/health", new HealthHandler(config, hostname, slotSemaphore));
        server.createContext("/execute", new ExecuteHandler(config, hostname, slotSemaphore));
        server.createContext("/batch", new BatchExecuteHandler(config, hostname, slotSemaphore));

        server.start();
        return server;
    }

    private static boolean authenticate(HttpExchange exchange, String expectedToken) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }
        return expectedToken.equals(authHeader.substring("Bearer ".length()).trim());
    }

    private static void sendJsonResponse(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        String json = String.format(Locale.US, "{\"error\": \"%s\"}", escapeJson(message));
        sendJsonResponse(exchange, statusCode, json);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static class HealthHandler implements HttpHandler {
        private final WorkerConfig config;
        private final String hostname;
        private final Semaphore slotSemaphore;

        HealthHandler(WorkerConfig config, String hostname, Semaphore slotSemaphore) {
            this.config = config;
            this.hostname = hostname;
            this.slotSemaphore = slotSemaphore;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!authenticate(exchange, config.token)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            int busy = config.slots - slotSemaphore.availablePermits();
            String json = String.format(Locale.US,
                    "{\"datacenterId\": \"%s\", \"hostId\": \"%s\", \"vmId\": \"%s\", \"hostname\": \"%s\", \"slots\": %d, \"busySlots\": %d}",
                    escapeJson(config.datacenterId), escapeJson(config.hostId),
                    escapeJson(config.vmId), escapeJson(hostname), config.slots, Math.max(0, busy));

            sendJsonResponse(exchange, 200, json);
        }
    }

    private static class ExecuteHandler implements HttpHandler {
        private final WorkerConfig config;
        private final String hostname;
        private final Semaphore slotSemaphore;
        private final ThreadMXBean threadMXBean;

        ExecuteHandler(WorkerConfig config, String hostname, Semaphore slotSemaphore) {
            this.config = config;
            this.hostname = hostname;
            this.slotSemaphore = slotSemaphore;
            this.threadMXBean = ManagementFactory.getThreadMXBean();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!authenticate(exchange, config.token)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }

            int taskId = -1;
            long iterations = -1;

            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                // Simple JSON parser untuk taskId dan iterations
                Pattern pTask = Pattern.compile("\"taskId\"\\s*:\\s*([0-9]+)");
                Pattern pIter = Pattern.compile("\"iterations\"\\s*:\\s*([0-9]+)");
                Matcher mTask = pTask.matcher(body);
                Matcher mIter = pIter.matcher(body);

                if (mTask.find()) taskId = Integer.parseInt(mTask.group(1));
                if (mIter.find()) iterations = Long.parseLong(mIter.group(1));

                if (taskId < 0 || iterations <= 0) {
                    sendError(exchange, 400, "taskId dan iterations positif wajib disertakan dalam JSON body");
                    return;
                }
            } catch (Exception e) {
                sendError(exchange, 400, "Format JSON tidak valid: " + e.getMessage());
                return;
            }

            if (!slotSemaphore.tryAcquire()) {
                sendError(exchange, 429, "Seluruh slot komputasi worker sedang sibuk");
                return;
            }

            boolean released = false;
            try {
                long startNanos = System.nanoTime();
                boolean cpuSupported = threadMXBean.isCurrentThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled();
                long startCpuNanos = cpuSupported ? threadMXBean.getCurrentThreadCpuTime() : 0;

                long deadlineNanos = startNanos + TimeUnit.MILLISECONDS.toNanos(CpuWorkload.DEFAULT_DEADLINE_MS);
                String checksum;
                try {
                    checksum = CpuWorkload.run(iterations, deadlineNanos);
                } catch (TimeoutException e) {
                    sendError(exchange, 408, e.getMessage());
                    return;
                }

                double executionSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
                double cpuSeconds = cpuSupported ? (threadMXBean.getCurrentThreadCpuTime() - startCpuNanos) / 1_000_000_000.0 : executionSeconds;

                String json = String.format(Locale.US,
                        "{\"taskId\": %d, \"iterations\": %d, \"datacenterId\": \"%s\", \"hostId\": \"%s\", \"vmId\": \"%s\", \"hostname\": \"%s\", \"executionSeconds\": %.6f, \"cpuSeconds\": %.6f, \"checksum\": \"%s\"}",
                        taskId, iterations, escapeJson(config.datacenterId), escapeJson(config.hostId),
                        escapeJson(config.vmId), escapeJson(hostname), executionSeconds, cpuSeconds, checksum);

                slotSemaphore.release();
                released = true;
                sendJsonResponse(exchange, 200, json);
                System.out.printf(Locale.US, "[WORKER %s] Task %d selesai: %d iterasi dalam %.4f s (checksum=%s)%n",
                        config.vmId, taskId, iterations, executionSeconds, checksum);
            } finally {
                if (!released) slotSemaphore.release();
            }
        }
    }

    /**
     * Eksekusi satu antrean task dalam SATU request.
     * Body: {"tasks":[{"taskId":0,"iterations":123},...]}
     * Task dijalankan berurutan; respons memuat waktu mulai relatif + durasi tiap task.
     */
    private static class BatchExecuteHandler implements HttpHandler {
        private final WorkerConfig config;
        private final String hostname;
        private final Semaphore slotSemaphore;
        private final ThreadMXBean threadMXBean;

        BatchExecuteHandler(WorkerConfig config, String hostname, Semaphore slotSemaphore) {
            this.config = config;
            this.hostname = hostname;
            this.slotSemaphore = slotSemaphore;
            this.threadMXBean = ManagementFactory.getThreadMXBean();
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method Not Allowed");
                return;
            }
            if (!authenticate(exchange, config.token)) {
                sendError(exchange, 401, "Unauthorized");
                return;
            }
            List<long[]> jobs = new ArrayList<long[]>();
            try (InputStream is = exchange.getRequestBody()) {
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"taskId\"\\s*:\\s*([0-9]+)\\s*,\\s*\"iterations\"\\s*:\\s*([0-9]+)").matcher(body);
                while (m.find()) {
                    jobs.add(new long[]{Long.parseLong(m.group(1)), Long.parseLong(m.group(2))});
                }
            } catch (Exception e) {
                sendError(exchange, 400, "Format JSON tidak valid: " + e.getMessage());
                return;
            }
            if (jobs.isEmpty()) {
                sendError(exchange, 400, "tasks kosong atau format salah");
                return;
            }
            for (long[] j : jobs) {
                if (j[1] <= 0) {
                    sendError(exchange, 400, "iterations harus positif");
                    return;
                }
            }
            if (!slotSemaphore.tryAcquire()) {
                sendError(exchange, 429, "Seluruh slot komputasi worker sedang sibuk");
                return;
            }
            boolean released = false;
            try {
                boolean cpuSupported = threadMXBean.isCurrentThreadCpuTimeSupported() && threadMXBean.isThreadCpuTimeEnabled();
                long batchStart = System.nanoTime();
                StringBuilder sb = new StringBuilder();
                sb.append("{\"vmId\": \"").append(escapeJson(config.vmId)).append("\", \"hostId\": \"")
                  .append(escapeJson(config.hostId)).append("\", \"results\": [");
                for (int k = 0; k < jobs.size(); k++) {
                    long start = System.nanoTime();
                    long cpu0 = cpuSupported ? threadMXBean.getCurrentThreadCpuTime() : 0;
                    String checksum;
                    try {
                        checksum = CpuWorkload.run(jobs.get(k)[1], start + TimeUnit.MILLISECONDS.toNanos(CpuWorkload.DEFAULT_DEADLINE_MS));
                    } catch (TimeoutException e) {
                        sendError(exchange, 408, e.getMessage());
                        return;
                    }
                    double exec = (System.nanoTime() - start) / 1_000_000_000.0;
                    double cpu = cpuSupported ? (threadMXBean.getCurrentThreadCpuTime() - cpu0) / 1_000_000_000.0 : exec;
                    if (k > 0) sb.append(", ");
                    sb.append(String.format(Locale.US,
                            "{\"taskId\": %d, \"startOffsetSeconds\": %.6f, \"executionSeconds\": %.6f, \"cpuSeconds\": %.6f, \"checksum\": \"%s\"}",
                            jobs.get(k)[0], (start - batchStart) / 1_000_000_000.0, exec, cpu, checksum));
                }
                double total = (System.nanoTime() - batchStart) / 1_000_000_000.0;
                sb.append(String.format(Locale.US, "], \"totalSeconds\": %.6f}", total));
                slotSemaphore.release();
                released = true;
                sendJsonResponse(exchange, 200, sb.toString());
                System.out.printf(Locale.US, "[WORKER %s] Batch %d task selesai dalam %.4f s%n", config.vmId, jobs.size(), total);
            } finally {
                if (!released) slotSemaphore.release();
            }
        }
    }
}
