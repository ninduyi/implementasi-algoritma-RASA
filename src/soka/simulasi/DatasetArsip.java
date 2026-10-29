package soka.simulasi;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

/**
 * Penyimpan BUKTI dataset: tulis dataset sintetis ke file (satu angka per baris,
 * format sama seperti GoCJ_Dataset_N) dan buat ringkasan statistiknya
 * (jumlah task per kategori, panjang min/rata-rata/max, persen task pendek/panjang).
 *
 * Definisi (dipakai juga di README):
 *   task pendek  = Small + Medium  (&lt; 100.000 MI)
 *   task panjang = Large + Extra Large (&gt;= 100.000 MI)
 */
public final class DatasetArsip {

    private DatasetArsip() {
    }

    /** Simpan panjang task (MI), satu angka per baris. */
    public static void simpan(double[] mi, File f) throws IOException {
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (FileWriter w = new FileWriter(f)) {
            for (double v : mi) {
                w.write(String.format(Locale.US, "%d%n", (long) v));
            }
        }
    }

    public static String headerRingkasan() {
        return "nama,jumlah_task,total_mi,rata_mi,min_mi,max_mi,small,medium,large,extra_large,"
                + "small_pct,medium_pct,large_pct,extra_large_pct,pendek_pct,panjang_pct\n";
    }

    /** Satu baris ringkasan untuk satu dataset. */
    public static String baris(String nama, double[] mi) {
        int[] c = new int[GoCjDatasetGenerator.KATEGORI.length];
        double total = 0, min = Double.MAX_VALUE, max = 0;
        for (double v : mi) {
            total += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
            int k = GoCjDatasetGenerator.indeksKategori(v);
            if (k >= 0) {
                c[k]++;
            }
        }
        double n = mi.length;
        return String.format(Locale.US,
                "%s,%d,%.0f,%.2f,%.0f,%.0f,%d,%d,%d,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f%n",
                nama, mi.length, total, total / n, min, max, c[0], c[1], c[2], c[3],
                c[0] / n * 100, c[1] / n * 100, c[2] / n * 100, c[3] / n * 100,
                (c[0] + c[1]) / n * 100, (c[2] + c[3]) / n * 100);
    }
}