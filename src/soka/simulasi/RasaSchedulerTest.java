package soka.simulasi;

import java.util.List;
import java.util.Locale;

/**
 * Unit test kecil (tanpa dependency JUnit, biar langsung jalan dengan
 * `java` tanpa perlu setup tambahan) untuk validasi logic RASA
 * sebelum diintegrasikan ke RasaBroker (Peran 3).
 *
 * Jalankan: java soka.simulasi.RasaSchedulerTest
 */
public class RasaSchedulerTest {

    private static int failCount = 0;

    public static void main(String[] args) {
        testEctMatrix();
        testAlternatingLogicDenganDataDummy();
        testSemuaTaskTerassignTanpaDuplikat();
        testVmMipsNolHarusLemparException();
        testTieBreakTidakCrashDanTetapKonsisten();
        testParitasVmMenentukanRondePertama();
        testMinMinDanMaxMinMurni();
        testSkalaRealistikSepertiDatasetGoCJ();

        if (failCount == 0) {
            System.out.println("\nSEMUA TEST LULUS ✔");
        } else {
            System.out.println("\n" + failCount + " TEST GAGAL ✘");
            System.exit(1);
        }
    }

    // Test 1: matrix ECT dihitung dengan benar (ECT = panjang / MIPS)
    static void testEctMatrix() {
        double[] taskLengths = {4000, 1000};
        double[] vmMips = {1000, 2000};

        double[][] ect = RasaScheduler.computeEctMatrix(taskLengths, vmMips);

        assertEquals("ECT[0][0]", 4.0, ect[0][0]);
        assertEquals("ECT[0][1]", 2.0, ect[0][1]);
        assertEquals("ECT[1][0]", 1.0, ect[1][0]);
        assertEquals("ECT[1][1]", 0.5, ect[1][1]);
    }

    // Test 2: alternating Min-Min/Max-Min dengan 5 task x 3 VM (hasil hitung manual)
    static void testAlternatingLogicDenganDataDummy() {
        // 3 VM -> ganjil -> round 0 pakai MIN-MIN (paritas JUMLAH VM, sesuai paper)
        double[] taskLengths = {4000, 3000, 8000, 1000, 6000}; // MI, index = task ID
        double[] vmMips = {1000, 2000, 500};                    // MIPS, index = VM ID

        List<RasaScheduler.Assignment> hasil = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.RASA);

        System.out.println("=== Hasil RASA (5 task, 3 VM) ===");
        for (RasaScheduler.Assignment a : hasil) {
            System.out.println("  " + a);
        }

        // Urutan round yang diharapkan (hasil hitung manual):
        // round0 MIN-MIN : task3 -> VM1 @0.5
        // round1 MAX-MIN : task2 -> VM1 @4.5
        // round2 MIN-MIN : task1 -> VM0 @3.0
        // round3 MAX-MIN : task4 -> VM1 @7.5
        // round4 MIN-MIN : task0 -> VM0 @7.0
        assertEquals("jumlah assignment", 5, hasil.size());

        RasaScheduler.Assignment r0 = hasil.get(0);
        assertEquals("round0 task", 3, r0.taskIndex);
        assertEquals("round0 vm", 1, r0.vmIndex);
        assertEquals("round0 strategy", "MIN-MIN", r0.strategy);
        assertEquals("round0 ct", 0.5, r0.completionTime);

        RasaScheduler.Assignment r1 = hasil.get(1);
        assertEquals("round1 task", 2, r1.taskIndex);
        assertEquals("round1 vm", 1, r1.vmIndex);
        assertEquals("round1 strategy", "MAX-MIN", r1.strategy);
        assertEquals("round1 ct", 4.5, r1.completionTime);

        RasaScheduler.Assignment r2 = hasil.get(2);
        assertEquals("round2 task", 1, r2.taskIndex);
        assertEquals("round2 vm", 0, r2.vmIndex);
        assertEquals("round2 strategy", "MIN-MIN", r2.strategy);
        assertEquals("round2 ct", 3.0, r2.completionTime);

        RasaScheduler.Assignment r3 = hasil.get(3);
        assertEquals("round3 task", 4, r3.taskIndex);
        assertEquals("round3 vm", 1, r3.vmIndex);
        assertEquals("round3 strategy", "MAX-MIN", r3.strategy);
        assertEquals("round3 ct", 7.5, r3.completionTime);

        RasaScheduler.Assignment r4 = hasil.get(4);
        assertEquals("round4 task", 0, r4.taskIndex);
        assertEquals("round4 vm", 0, r4.vmIndex);
        assertEquals("round4 strategy", "MIN-MIN", r4.strategy);
        assertEquals("round4 ct", 7.0, r4.completionTime);

        double makespan = 0;
        for (RasaScheduler.Assignment a : hasil) makespan = Math.max(makespan, a.completionTime);
        System.out.printf(Locale.US, "  Makespan = %.2f%n", makespan);
        assertEquals("makespan", 7.5, makespan);
    }

    // Test 3: tiap task cuma boleh di-assign SATU KALI
    static void testSemuaTaskTerassignTanpaDuplikat() {
        double[] taskLengths = {5000, 2000, 9000, 1500, 7000, 3000}; // 4 VM -> genap -> mulai MAX-MIN
        double[] vmMips = {1000, 500, 2000, 750};

        List<RasaScheduler.Assignment> hasil = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.RASA);

        assertEquals("jumlah assignment == jumlah task", taskLengths.length, hasil.size());
        assertEquals("round pertama saat jumlah VM genap harus MAX-MIN", "MAX-MIN", hasil.get(0).strategy);

        boolean[] seen = new boolean[taskLengths.length];
        for (RasaScheduler.Assignment a : hasil) {
            if (seen[a.taskIndex]) {
                fail("task " + a.taskIndex + " ter-assign lebih dari sekali");
            }
            seen[a.taskIndex] = true;
        }
        for (int i = 0; i < seen.length; i++) {
            if (!seen[i]) fail("task " + i + " tidak pernah ter-assign");
        }
    }

    // Test 4: VM dengan MIPS 0 atau negatif harus ditolak
    static void testVmMipsNolHarusLemparException() {
        double[] taskLengths = {1000};
        double[] vmMips = {0};
        try {
            RasaScheduler.computeEctMatrix(taskLengths, vmMips);
            fail("harusnya lempar IllegalArgumentException untuk MIPS 0");
        } catch (IllegalArgumentException expected) {
            // lulus
        }
    }

    // Test 5: kondisi seri (tie) tidak crash dan hasilnya deterministik
    static void testTieBreakTidakCrashDanTetapKonsisten() {
        double[] taskLengths = {2000, 2000, 2000};
        double[] vmMips = {1000, 1000};

        List<RasaScheduler.Assignment> hasil1 = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.RASA);
        List<RasaScheduler.Assignment> hasil2 = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.RASA);

        assertEquals("jumlah assignment (tie case)", 3, hasil1.size());
        for (int i = 0; i < hasil1.size(); i++) {
            RasaScheduler.Assignment a = hasil1.get(i);
            RasaScheduler.Assignment b = hasil2.get(i);
            if (a.taskIndex != b.taskIndex || a.vmIndex != b.vmIndex) {
                fail("hasil tidak deterministik pada kondisi seri, round " + i);
            }
        }
        System.out.println("  [OK] tie-break deterministik (3 task identik, 2 VM identik)");
    }

    // Test 6: skala dataset (600 cloudlet GoCJ: Small 58, Medium 285, Large 215,
    // Extra Large 42, sesuai draft design bagian 1.2) x 15 VM (9 x 1000 MIPS + 6 x 500 MIPS).
    static void testSkalaRealistikSepertiDatasetGoCJ() {
        // Data identik dengan GoCjDatasetGenerator (seed 42)
        double[] taskLengths = GoCjDatasetGenerator.generate600();
        assertEquals("jumlah task dataset", 600, taskLengths.length);
        int[] hitung = new int[GoCjDatasetGenerator.KATEGORI.length];
        for (double v : taskLengths) hitung[GoCjDatasetGenerator.indeksKategori(v)]++;
        for (int k = 0; k < hitung.length; k++) {
            assertEquals("jumlah kategori " + GoCjDatasetGenerator.KATEGORI[k].nama,
                    GoCjDatasetGenerator.KATEGORI[k].jumlah, hitung[k]);
        }

        double[] vmMips = new double[15];
        for (int i = 0; i < 9; i++) vmMips[i] = 1000;   // VM Tipe 1
        for (int i = 9; i < 15; i++) vmMips[i] = 500;   // VM Tipe 2

        long t0 = System.nanoTime();
        List<RasaScheduler.Assignment> hasil = RasaScheduler.schedule(taskLengths, vmMips, RasaScheduler.Mode.RASA);
        long t1 = System.nanoTime();

        assertEquals("jumlah assignment == 600", 600, hasil.size());

        boolean[] seen = new boolean[taskLengths.length];
        for (RasaScheduler.Assignment a : hasil) {
            if (seen[a.taskIndex]) fail("task " + a.taskIndex + " dobel di skala 600");
            seen[a.taskIndex] = true;
        }

        double[] vmBusyUntil = new double[15];
        for (RasaScheduler.Assignment a : hasil) {
            vmBusyUntil[a.vmIndex] = Math.max(vmBusyUntil[a.vmIndex], a.completionTime);
        }
        double makespan = 0, sum = 0, min = Double.MAX_VALUE, max = 0;
        for (double v : vmBusyUntil) {
            makespan = Math.max(makespan, v);
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double avg = sum / vmBusyUntil.length;
        double degreeOfImbalance = (max - min) / avg;

        System.out.printf(Locale.US,
                "  [INFO] 600 task/15 VM -> waktu proses %.2f ms, makespan=%.2f, DI=%.4f%n",
                (t1 - t0) / 1e6, makespan, degreeOfImbalance);

        if (degreeOfImbalance > 0.5) {
            fail("Degree of Imbalance kelihatan terlalu besar (" + degreeOfImbalance + "), cek ulang logic");
        } else {
            System.out.println("  [OK] beban antar VM relatif merata (DI wajar untuk RASA)");
        }
    }

    // Test 7: ronde pertama ditentukan paritas JUMLAH VM (bukan jumlah task).
    static void testParitasVmMenentukanRondePertama() {
        // 4 task (genap), 3 VM (ganjil): versi lama -> MAX-MIN, versi final -> MIN-MIN
        List<RasaScheduler.Assignment> a = RasaScheduler.schedule(
                new double[]{4000, 3000, 8000, 1000}, new double[]{1000, 2000, 500}, RasaScheduler.Mode.RASA);
        assertEquals("4 task, 3 VM: ronde pertama", "MIN-MIN", a.get(0).strategy);
        assertEquals("4 task, 3 VM: ronde kedua", "MAX-MIN", a.get(1).strategy);

        // 5 task (ganjil), 4 VM (genap): versi lama -> MIN-MIN, versi final -> MAX-MIN
        List<RasaScheduler.Assignment> b = RasaScheduler.schedule(
                new double[]{4000, 3000, 8000, 1000, 6000}, new double[]{1000, 1000, 1000, 1000}, RasaScheduler.Mode.RASA);
        assertEquals("5 task, 4 VM: ronde pertama", "MAX-MIN", b.get(0).strategy);
        assertEquals("5 task, 4 VM: ronde kedua", "MIN-MIN", b.get(1).strategy);
    }

    // Test 8: mode pembanding Min-Min dan Max-Min murni tidak berselang-seling.
    static void testMinMinDanMaxMinMurni() {
        double[] t = {4000, 3000, 8000, 1000, 6000};
        double[] v = {1000, 2000, 500};
        for (RasaScheduler.Assignment x : RasaScheduler.schedule(t, v, RasaScheduler.Mode.MIN_MIN)) {
            assertEquals("Min-Min murni, task " + x.taskIndex, "MIN-MIN", x.strategy);
        }
        for (RasaScheduler.Assignment x : RasaScheduler.schedule(t, v, RasaScheduler.Mode.MAX_MIN)) {
            assertEquals("Max-Min murni, task " + x.taskIndex, "MAX-MIN", x.strategy);
        }
    }

    // Helper assert kecil-kecilan (biar tidak butuh JUnit)
    static void assertEquals(String label, double expected, double actual) {
        if (Math.abs(expected - actual) > 1e-6) {
            fail(label + " -> expected=" + expected + " actual=" + actual);
        } else {
            System.out.println("  [OK] " + label + " = " + actual);
        }
    }

    static void assertEquals(String label, String expected, String actual) {
        if (!expected.equals(actual)) {
            fail(label + " -> expected=" + expected + " actual=" + actual);
        } else {
            System.out.println("  [OK] " + label + " = " + actual);
        }
    }

    static void assertEquals(String label, int expected, int actual) {
        if (expected != actual) {
            fail(label + " -> expected=" + expected + " actual=" + actual);
        } else {
            System.out.println("  [OK] " + label + " = " + actual);
        }
    }

    static void fail(String message) {
        failCount++;
        System.out.println("  [GAGAL] " + message);
    }
}
