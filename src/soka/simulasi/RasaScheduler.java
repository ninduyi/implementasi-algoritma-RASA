package soka.simulasi;

import java.util.ArrayList;
import java.util.List;

/**
 * Peran 2 — Core Logic RASA (Resource Aware Scheduling Algorithm).
 *
 * Kelas ini SENGAJA dibuat lepas dari CloudSim (murni Java + array/List),
 * supaya:
 *  1. Gampang di-unit-test pakai data dummy tanpa perlu boot CloudSim.
 *  2. Peran 3 tinggal panggil schedule(...) di dalam RasaBroker dan
 *     memetakan index task/VM ke objek Cloudlet/Vm CloudSim.
 *
 * Algoritma (Parsa & Entezari-Maleki, 2009):
 *  1. Hitung matrix ECT (Expected Completion Time) task x VM:
 *       ECT[i][j] = panjang_task_i (MI) / MIPS_VM_j
 *  2. Completion Time aktual = ECT[i][j] + waktu VM j tersedia (readyTime).
 *  3. Tentukan strategi awal dari paritas JUMLAH TASK:
 *       - jika ganjil -> round pertama pakai Min-Min
 *       - jika genap  -> round pertama pakai Max-Min
 *     Selanjutnya strategi berselang-seling tiap round (ganjil/genap).
 *  4. Tiap round hanya SATU task yang di-assign:
 *       - Min-Min : dari semua task tersisa, cari (task, VM terbaiknya)
 *                   dengan completion time PALING KECIL.
 *       - Max-Min : dari semua task tersisa, cari VM terbaik masing-masing
 *                   task dulu, lalu pilih task yang completion time
 *                   terbaiknya PALING BESAR (mewakili task besar).
 *       - Assign task ke VM terpilih, lalu update available time VM itu
 *         menjadi completion time hasil assignment.
 *  5. Ulangi sampai semua task ter-assign.
 */
public final class RasaScheduler {

    /** Hasil satu penugasan task -> VM. */
    public static final class Assignment {
        public final int taskIndex;
        public final int vmIndex;
        public final double completionTime; // waktu selesai (absolut) task ini di VM tsb
        public final String strategy;        // "MIN-MIN" atau "MAX-MIN"

        public Assignment(int taskIndex, int vmIndex, double completionTime, String strategy) {
            this.taskIndex = taskIndex;
            this.vmIndex = vmIndex;
            this.completionTime = completionTime;
            this.strategy = strategy;
        }

        @Override
        public String toString() {
            return String.format(
                    "Task %2d -> VM %2d | selesai @ %8.4f | round: %s",
                    taskIndex, vmIndex, completionTime, strategy);
        }
    }

    private RasaScheduler() {
    }

    /**
     * Hitung matrix ECT (task x VM).
     *
     * @param taskLengthsMI panjang tiap task dalam Million Instructions (MI)
     * @param vmMips        kecepatan tiap VM dalam MIPS
     * @return matrix double[nTask][nVm] berisi ECT
     */
    public static double[][] computeEctMatrix(double[] taskLengthsMI, double[] vmMips) {
        int nTask = taskLengthsMI.length;
        int nVm = vmMips.length;
        double[][] ect = new double[nTask][nVm];
        for (int j = 0; j < nVm; j++) {
            if (vmMips[j] <= 0) {
                throw new IllegalArgumentException("MIPS VM index " + j + " harus > 0");
            }
        }
        for (int i = 0; i < nTask; i++) {
            for (int j = 0; j < nVm; j++) {
                ect[i][j] = taskLengthsMI[i] / vmMips[j];
            }
        }
        return ect;
    }

    /**
     * Jalankan algoritma RASA: alternating Min-Min / Max-Min per round.
     *
     * @param taskLengthsMI panjang tiap task (MI), index = ID task
     * @param vmMips        kecepatan tiap VM (MIPS), index = ID VM
     * @return daftar Assignment, terurut sesuai urutan round (bukan urutan task ID)
     */
    /**
     * @deprecated Versi AWAL: menentukan strategi ronde pertama dari paritas jumlah TASK.
     * Implementasi final (sesuai paper dan draft design) memakai paritas jumlah VM, yaitu
     * {@link #schedule(double[], double[], Mode)} dengan {@link Mode#RASA}. Metode ini hanya
     * dipertahankan karena masih dipakai RasaSchedulerTest.
     */
    @Deprecated
    public static List<Assignment> schedule(double[] taskLengthsMI, double[] vmMips) {
        int nTask = taskLengthsMI.length;
        int nVm = vmMips.length;
        List<Assignment> hasil = new ArrayList<>();
        if (nTask == 0 || nVm == 0) {
            return hasil;
        }

        double[][] ect = computeEctMatrix(taskLengthsMI, vmMips);
        double[] vmAvailable = new double[nVm]; // kapan VM j bebas lagi
        boolean[] done = new boolean[nTask];

        // Paritas JUMLAH TASK menentukan strategi round pertama (round=0).
        boolean round0IsMinMin = (nTask % 2 != 0); // ganjil -> Min-Min duluan

        int assignedCount = 0;
        int round = 0;
        while (assignedCount < nTask) {
            boolean useMinMin = (round % 2 == 0) == round0IsMinMin;

            int pickTask = -1;
            int pickVm = -1;
            double pickCt = useMinMin ? Double.MAX_VALUE : -Double.MAX_VALUE;

            for (int i = 0; i < nTask; i++) {
                if (done[i]) continue;

                // VM terbaik untuk task i saat ini (completion time terkecil)
                int localBestVm = -1;
                double localBestCt = Double.MAX_VALUE;
                for (int j = 0; j < nVm; j++) {
                    double ct = ect[i][j] + vmAvailable[j];
                    if (ct < localBestCt) {
                        localBestCt = ct;
                        localBestVm = j;
                    }
                }

                if (useMinMin) {
                    if (localBestCt < pickCt) {
                        pickCt = localBestCt;
                        pickTask = i;
                        pickVm = localBestVm;
                    }
                } else {
                    if (localBestCt > pickCt) {
                        pickCt = localBestCt;
                        pickTask = i;
                        pickVm = localBestVm;
                    }
                }
            }

            done[pickTask] = true;
            vmAvailable[pickVm] = pickCt;
            hasil.add(new Assignment(pickTask, pickVm, pickCt, useMinMin ? "MIN-MIN" : "MAX-MIN"));

            assignedCount++;
            round++;
        }

        return hasil;
    }

    /** Algoritma yang dijalankan oleh {@link #schedule(double[], double[], Mode)}. */
    public enum Mode { RASA, MIN_MIN, MAX_MIN }

    /**
     * Versi revisi: RASA sesuai paper (Parsa &amp; Entezari-Maleki, 2009) plus
     * Min-Min dan Max-Min murni sebagai pembanding.
     * Pada mode RASA, strategi round pertama ditentukan oleh paritas JUMLAH
     * RESOURCE (VM), bukan jumlah task: ganjil -> Min-Min dulu, genap -> Max-Min dulu.
     * Setelah itu strategi berselang-seling tiap round.
     *
     * <p><b>Aturan tie-break (hasil deterministik):</b> perbandingan memakai &lt; dan &gt; ketat,
     * jadi jika dua kandidat punya completion time sama, yang dipilih adalah yang pertama
     * ditemukan, yaitu VM dengan index terkecil (untuk VM terbaik suatu task) dan task dengan
     * index terkecil (untuk pemilihan task di tiap ronde). Karena itu data yang punya banyak
     * nilai kembar tetap menghasilkan jadwal yang sama pada urutan input yang sama.
     */
    public static List<Assignment> schedule(double[] taskLengthsMI, double[] vmMips, Mode mode) {
        int nTask = taskLengthsMI.length;
        int nVm = vmMips.length;
        List<Assignment> hasil = new ArrayList<>();
        if (nTask == 0 || nVm == 0) return hasil;

        double[][] ect = computeEctMatrix(taskLengthsMI, vmMips);
        double[] vmAvailable = new double[nVm];
        boolean[] done = new boolean[nTask];
        boolean round0IsMinMin = (nVm % 2 != 0);

        for (int round = 0; round < nTask; round++) {
            boolean useMinMin;
            switch (mode) {
                case MIN_MIN: useMinMin = true; break;
                case MAX_MIN: useMinMin = false; break;
                default:      useMinMin = (round % 2 == 0) == round0IsMinMin;
            }
            int pickTask = -1, pickVm = -1;
            double pickCt = useMinMin ? Double.MAX_VALUE : -Double.MAX_VALUE;
            for (int i = 0; i < nTask; i++) {
                if (done[i]) continue;
                int bestVm = -1; double bestCt = Double.MAX_VALUE;
                for (int j = 0; j < nVm; j++) {
                    double ct = ect[i][j] + vmAvailable[j];
                    if (ct < bestCt) { bestCt = ct; bestVm = j; }
                }
                if (useMinMin ? bestCt < pickCt : bestCt > pickCt) {
                    pickCt = bestCt; pickTask = i; pickVm = bestVm;
                }
            }
            done[pickTask] = true;
            vmAvailable[pickVm] = pickCt;
            hasil.add(new Assignment(pickTask, pickVm, pickCt, useMinMin ? "MIN-MIN" : "MAX-MIN"));
        }
        return hasil;
    }
}