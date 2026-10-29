package soka.realworld;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

/**
 * Beban kerja komputasi CPU nyata yang deterministik.
 * Menjalankan hashing SHA-256 berulang pada buffer 32-byte.
 * Tidak menggunakan sleep, menghasilkan checksum yang dapat diverifikasi.
 */
public final class CpuWorkload {
    public static final long DEFAULT_DEADLINE_MS = 120_000L;
    private static final int CHECK_INTERVAL = 4096;

    private CpuWorkload() {}

    public static String run(long iterations) {
        try {
            return run(iterations, -1L);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timeout tidak terduga pada run unbounded", e);
        }
    }

    public static String run(long iterations, long deadlineNanoTime) throws TimeoutException {
        if (iterations <= 0) {
            throw new IllegalArgumentException("Iterasi CPU harus bernilai positif: " + iterations);
        }

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 tidak tersedia di runtime JVM", e);
        }

        byte[] bufA = new byte[32];
        byte[] bufB = new byte[32];
        byte[] currentInput = bufA;
        byte[] currentOutput = bufB;

        for (long i = 0; i < iterations; i++) {
            if (deadlineNanoTime > 0 && (i % CHECK_INTERVAL == 0)) {
                if (System.nanoTime() >= deadlineNanoTime) {
                    throw new TimeoutException("Batas waktu eksekusi CPU terlampaui pada iterasi ke-" + i);
                }
                if (Thread.currentThread().isInterrupted()) {
                    throw new TimeoutException("Eksekusi CPU diinterupsi pada iterasi ke-" + i);
                }
            }

            md.update(currentInput);
            try {
                md.digest(currentOutput, 0, 32);
            } catch (Exception e) {
                throw new IllegalStateException("Gagal mendigest SHA-256", e);
            }

            byte[] temp = currentInput;
            currentInput = currentOutput;
            currentOutput = temp;
        }

        return toHex(currentInput);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int val = b & 0xFF;
            if (val < 16) sb.append('0');
            sb.append(Integer.toHexString(val));
        }
        return sb.toString();
    }
}

