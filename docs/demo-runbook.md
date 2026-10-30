# Runbook Presentasi & Demo SOKA Real-World RASA

Petunjuk praktis untuk mendemonstrasikan implementasi penjadwalan RASA di depan dosen atau kelas secara langsung dari Windows Terminal.

---

## 1. Persiapan Sebelum Demo

1. **Pastikan kedua Host EC2 AWS sedang dalam status `running`:**
   * Host 1 (`32.236.84.34`)
   * Host 2 (`3.106.226.40`)
   *(Jika IP publik berubah setelah reboot instance, cukup oper parameter `-Host1Ip <ip>` dan `-Host2Ip <ip>` saat menjalankan script demo)*.
2. **Pastikan file private key AWS (`rasa-host-key.pem`) berada di folder Downloads:**
   * Lokasi default: `C:\Users\Nindy\Downloads\rasa-host-key.pem`.

---

## 2. Cara Menjalankan Preflight (Uji Cepat Kesiapan)

Buka PowerShell di direktori proyek:

```powershell
.\preflight.ps1
```

**Hasil yang diharapkan:**
```text
[PREFLIGHT] Memeriksa compiler & runtime Java... OK (javac 17.0.18)
[NETWORK] Membuka SSH Tunnel ke Host 1 & Host 2... OK
+---------------+--------------+----------------------+-------+-----------+----------------------+
| Host ID       | VM ID        | Hostname Tamu        | Slots | BusySlots | Endpoint             |
+---------------+--------------+----------------------+-------+-----------+----------------------+
| rasa-host-1   | rasa-vm1     | rasa-vm1             | 1     | 0         | http://127.0.0.1:18001 |
| rasa-host-1   | rasa-vm2     | rasa-vm2             | 1     | 0         | http://127.0.0.1:18002 |
| rasa-host-2   | rasa-vm3     | rasa-vm3             | 1     | 0         | http://127.0.0.1:18003 |
| rasa-host-2   | rasa-vm4     | rasa-vm4             | 1     | 0         | http://127.0.0.1:18004 |
+---------------+--------------+----------------------+-------+-----------+----------------------+
[PREFLIGHT] Status: OK (4 worker aktif dan siap)
```

---

## 3. Menjalankan Demo Utama (100 Task RASA)

Jalankan perintah satu-baris:

### Opsi A: Jalankan langsung dari root (Rekomendasi)
```powershell
.\demo.ps1
```

### Opsi B: Klik ganda pada Windows Explorer
Klik dua kali file `demo.bat` pada folder proyek.

**Alur yang terjadi saat script berjalan:**
1. Otomatis menguji dependensi Java dan mengompilasi kode jika diperlukan.
2. Membuka SSH tunnel terenkripsi ke Host 1 & Host 2 di latar belakang.
3. Melakukan health check ke 4 VM guest.
4. Menjalankan kalibrasi throughput iterasi komputasi nyata per detik (IPS).
5. Memuat 100 task dari dataset Google Cloud Jobs (`dataset/GoCJ_Dataset_100.txt`).
6. Menjalankan algoritma RASA (paritas genap 4 VM $\rightarrow$ bergantian Max-Min dan Min-Min).
7. Mendispatch antrean task secara paralel ke 4 worker VM di AWS, mengeksekusi hashing SHA-256 nyata, dan menampilkan waktu eksekusi aktual serta hash kriptografis.
8. Menampilkan tabel ringkasan metrik akhir (Makespan, Degree of Imbalance, Throughput, alokasi beban per-VM).
9. Menyimpan hasil lengkap ke `results/latest/` dan arsip `results/runs/<timestamp>/` (`dashboard.html`, `summary.json`, `tasks.csv`, dan 3 grafik visualisasi PNG).
10. Membuka otomatis **Dashboard Presentasi Interaktif** di browser default.
11. Menutup seluruh tunnel SSH secara otomatis dan aman.

---

## 4. Menjalankan Benchmark Skala Penuh (100 s.d. 500 Task)

Untuk mengevaluasi performa sistem dari 100 hingga 500 task (naik perseratus):

```powershell
.\benchmark.ps1
```

Script akan menguji secara otomatis kedua algoritma (RASA dan Round-Robin) pada 5 skala:
* 100 Task
* 200 Task
* 300 Task
* 400 Task
* 500 Task

Setelah selesai, visualisasi komparatif `scaling_evaluation.png` dan `benchmark_dashboard.html` akan langsung dihasilkan dan dibuka di browser.

---

## 5. Menjalankan Perbandingan Baseline Tertarget

Untuk membandingkan performa RASA dengan Round-Robin pada skala tertentu (misal 100 task):

```powershell
# Uji RASA 100 task
.\demo.ps1 -Tasks 100 -Algorithm RASA

# Uji Round-Robin 100 task
.\demo.ps1 -Tasks 100 -Algorithm RR
```

**Temuan Utama yang Dapat Ditunjukkan kepada Dosen:**
* **Degree of Imbalance (DI)** RASA (~`0.0062`) jauh lebih rendah (97.9% lebih seimbang) daripada Round-Robin (~`0.3026`).
* Keempat VM selesai pada waktu yang hampir bersamaan (selisih milidetik), membuktikan efektivitas penjadwalan cerdas RASA.

---

## 6. Troubleshooting Cepat

* **Error: Gagal membuat SSH tunnel:**
  * Pastikan kedua instance EC2 di AWS Console berstatus *Running*.
  * Pastikan Security Group EC2 mengizinkan port 22 dari IP publik laptop Anda saat ini (*My IP*).
* **Periksa status service worker di dalam VM:**
  ```powershell
  ssh -i "C:\Users\Nindy\Downloads\rasa-host-key.pem" ubuntu@32.236.84.34 "ssh -i ~/.ssh/rasa_guest_ed25519 ubuntu@192.168.122.156 'sudo systemctl status rasa-worker.service'"
  ```
