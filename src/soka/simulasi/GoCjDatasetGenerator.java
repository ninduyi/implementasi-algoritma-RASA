package soka.simulasi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Peran 3 — Generator dataset uji coba bertipe GoCJ (Google Cloud Jobs).
 *
 * GoCJ asli (Hussain &amp; Aleem, 2018, Mendeley Data:
 * https://data.mendeley.com/datasets/b7bp6xhrcd) hanya berisi daftar
 * panjang job mentah (MI) tanpa label kategori. Sesuai draft desain
 * (bagian 1.2), 600 job dipilih lalu dikelompokkan ke 4 kategori
 * ukuran dengan proporsi berikut:
 *
 * <pre>
 *   Small        : 3.000   - 47.999  MI  -> 35% (210 task)
 *   Medium       : 48.000  - 99.999  MI  -> 30% (180 task)
 *   Large        : 100.000 - 449.999 MI  -> 25% (150 task)
 *   Extra Large  : 450.000 - 999.999 MI  -> 10% (60  task)
 * </pre>
 *
 * Karena file mentah GoCJ tidak diunggah ke repo, kelas ini membangkitkan
 * ulang 600 panjang task pada rentang yang SAMA seperti pada
 * {@code RasaSchedulerTest#testSkalaRealistikSepertiDatasetGoCJ()} (Peran 2),
 * memakai Random seed yang sama (42) supaya angka yang dipakai pada
 * simulasi CloudSim penuh (Peran 3) dan pada unit test (Peran 2) identik
 * dan hasilnya bisa saling diverifikasi silang.
 *
 * <p><b>Catatan:</b> Jika file dataset GoCJ asli (mis. {@code GoCJ_Dataset_600})
 * sudah tersedia di repo, ganti pemanggilan {@link #generate600()} dengan
 * {@link #loadFromFile(String)} agar memakai data riil, bukan sintetis.
 */
public final class GoCjDatasetGenerator {

    public static final int SEED = 42;

    private GoCjDatasetGenerator() {
    }

    /** Satu kategori ukuran task pada dataset GoCJ hasil klasifikasi. */
    public static final class Category {
        public final String nama;
        public final int jumlah;
        public final long miMin;
        public final long miMax;

        public Category(String nama, int jumlah, long miMin, long miMax) {
            this.nama = nama;
            this.jumlah = jumlah;
            this.miMin = miMin;
            this.miMax = miMax;
        }
    }

    /** Urutan & kuota kategori = proporsi data asli GoCJ_Dataset_600 (lihat README bagian 3.2). */
    public static final Category[] KATEGORI = {
            new Category("Small", 58, 3_000, 47_999),
            new Category("Medium", 285, 48_000, 99_999),
            new Category("Large", 215, 100_000, 449_999),
            new Category("Extra Large", 42, 450_000, 999_999),
    };

    /**
     * Bangkitkan 600 panjang task (MI), diacak urutannya, dengan seed=42
     * (identik dengan data pada RasaSchedulerTest Peran 2).
     *
     * @return array 600 panjang task dalam MI
     */
    public static double[] generate600() {
        return generate(SEED);
    }

    /** Versi dengan seed custom, untuk keperluan uji ulang/sensitivitas. */
    public static double[] generate(long seed) {
        Random rnd = new Random(seed);
        List<Double> taskList = new ArrayList<>();
        for (Category c : KATEGORI) {
            addRange(taskList, rnd, c.jumlah, c.miMin, c.miMax);
        }
        Collections.shuffle(taskList, rnd);

        double[] out = new double[taskList.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = taskList.get(i);
        }
        return out;
    }

    private static void addRange(List<Double> list, Random rnd, int count, long lo, long hi) {
        for (int i = 0; i < count; i++) {
            list.add((double) (lo + rnd.nextInt((int) (hi - lo + 1))));
        }
    }


    /** @return indeks kategori (0=Small .. 3=Extra Large) untuk panjang task v, atau -1 jika di luar rentang. */
    public static int indeksKategori(double v) {
        for (int i = 0; i < KATEGORI.length; i++) {
            if (v >= KATEGORI[i].miMin && v <= KATEGORI[i].miMax) {
                return i;
            }
        }
        return -1;
    }

    /** Sama seperti {@link #generateDariPool(double[], int, long, double[])} dengan proporsi mengikuti pool. */
    public static double[] generateDariPool(double[] pool, int n, long seed) {
        return generateDariPool(pool, n, seed, null);
    }

    /**
     * Bangkitkan n task SINTETIS dengan cara stratified bootstrap dari data asli:
     * jumlah task per kategori ditentukan dari proporsi (largest remainder, jadi total
     * persis n), lalu tiap task diambil acak (dengan pengembalian) dari nilai asli
     * pada kategori yang sama, dan terakhir urutannya diacak.
     * Hasilnya sama persis untuk (pool, n, seed, bobot) yang sama, sehingga bisa direproduksi.
     *
     * @param pool  data asli (mis. isi GoCJ_Dataset_600)
     * @param n     jumlah task yang dibangkitkan (boleh lebih besar dari pool.length)
     * @param seed  seed Random
     * @param bobot proporsi 4 kategori (Small, Medium, Large, Extra Large; dinormalkan otomatis),
     *              atau null untuk memakai proporsi data pool
     */
    public static double[] generateDariPool(double[] pool, int n, long seed, double[] bobot) {
        int k = KATEGORI.length;
        List<List<Double>> kolam = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            kolam.add(new ArrayList<Double>());
        }
        for (double v : pool) {
            int idx = indeksKategori(v);
            if (idx >= 0) {
                kolam.get(idx).add(v);
            }
        }

        double[] p = new double[k];
        double jumlah = 0;
        for (int i = 0; i < k; i++) {
            p[i] = (bobot == null) ? kolam.get(i).size() : bobot[i];
            jumlah += p[i];
        }
        for (int i = 0; i < k; i++) {
            p[i] /= jumlah;
            if (p[i] > 0 && kolam.get(i).isEmpty()) {
                throw new IllegalArgumentException("Pool data asli tidak punya task kategori " + KATEGORI[i].nama);
            }
        }

        // Alokasi jumlah task per kategori (largest remainder method)
        int[] cnt = new int[k];
        double[] sisa = new double[k];
        int terbagi = 0;
        for (int i = 0; i < k; i++) {
            double x = p[i] * n;
            cnt[i] = (int) Math.floor(x);
            sisa[i] = x - cnt[i];
            terbagi += cnt[i];
        }
        while (terbagi < n) {
            int best = 0;
            for (int i = 1; i < k; i++) {
                if (sisa[i] > sisa[best]) {
                    best = i;
                }
            }
            cnt[best]++;
            sisa[best] = -1;
            terbagi++;
        }

        Random rnd = new Random(seed);
        List<Double> hasil = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            List<Double> src = kolam.get(i);
            for (int j = 0; j < cnt[i]; j++) {
                hasil.add(src.get(rnd.nextInt(src.size())));
            }
        }
        Collections.shuffle(hasil, rnd);

        double[] out = new double[hasil.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = hasil.get(i);
        }
        return out;
    }


    /**
     * Replikasi generator RESMI GoCJ (Hussain &amp; Aleem, 2018; file "GoCJ Java Generator.txt").
     * Algoritma aslinya: simpan 50 nilai dari Original_DataSet.txt pada indeks genap 0,2,...,98;
     * tiap job ambil rand = nextInt(100); rand genap -> nilai pada indeks rand, rand ganjil ->
     * nilai pada indeks genap terbesar yang &lt; rand (yaitu rand-1). Hasilnya: tiap dari 50 nilai
     * asli terpilih dengan peluang sama (1/50), sehingga ekuivalen dengan {@code original[rand / 2]}.
     * Bedanya dengan aslinya hanya Random diberi seed, supaya hasil bisa direproduksi.
     *
     * @param original isi Original_DataSet.txt (50 nilai MI)
     * @param n        jumlah job yang dibangkitkan
     * @param seed     seed Random
     */
    public static double[] generateResmi(double[] original, int n, long seed) {
        if (original.length != 50) {
            throw new IllegalArgumentException("Original_DataSet harus berisi 50 nilai, ditemukan " + original.length);
        }
        Random rnd = new Random(seed);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            int rand = rnd.nextInt(100);
            out[i] = original[rand / 2];
        }
        return out;
    }

    /**
     * Muat panjang task (MI) dari file dataset GoCJ asli, satu angka per baris
     * (format asli file GoCJ_Dataset_&lt;N&gt; dari Mendeley Data).
     * Baris kosong/tidak valid akan dilewati.
     *
     * @param path path file dataset, mis. "dataset/GoCJ_Dataset_600"
     * @return array panjang task (MI) sesuai isi file
     */
    public static double[] loadFromFile(String path) throws java.io.IOException {
        List<Double> taskList = new ArrayList<>();
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                try {
                    taskList.add(Double.parseDouble(line));
                } catch (NumberFormatException ignored) {
                    // lewati baris header/non-numerik jika ada
                }
            }
        }
        double[] out = new double[taskList.size()];
        for (int i = 0; i < out.length; i++) out[i] = taskList.get(i);
        return out;
    }
}