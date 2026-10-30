# Panduan Operasional & Eksekusi Program (SOKA RASA AWS)

Dokumen ini merupakan panduan langkah demi langkah bagi dosen penguji, penilai, dan anggota kelompok untuk menjalankan, menguji, dan memverifikasi implementasi real-world **Resource Aware Scheduling Algorithm (RASA)** pada kluster multi-host AWS.

---

## 1. Ringkasan Cepat (Quick Start)

Semua script eksekusi telah diotomatisasi dan dapat dijalankan langsung dari root direktori proyek di terminal **PowerShell**:

| Tujuan Pengujian | Perintah Eksekusi | Estimasi Durasi | Output Utama |
| :--- | :--- | :---: | :--- |
| **Uji Cepat Kesehatan 4 VM** | `.\preflight.ps1` | ~3 detik | Tabel status 4 worker VM |
| **Demo Utama RASA (100 Task)** | `.\demo.ps1` | ~10 detik | Terminal log + popup Dashboard Web |
| **Demo Cepat GUI (Klik Ganda)** | Klik ganda `demo.bat` | ~10 detik | Popup Dashboard Web otomatis |
| **Uji Baseline Round-Robin** | `.\demo.ps1 -Algorithm RR` | ~10 detik | Perbandingan performa RR |
| **Uji Skalabilitas (100 - 500 Task)** | `.\benchmark.ps1` | ~2 menit | Kurva grafik 3 panel + Dashboard multi-skala |

---

## 2. Prasyarat Lingkungan (Prerequisites)

Sebelum menjalankan script, pastikan lingkungan kerja memenuhi kebutuhan berikut:

1. **Sistem Operasi:** Windows 10 atau Windows 11 (PowerShell 5.1 bawaan atau PowerShell Core 7+).
2. **Java Development Kit (JDK):** OpenJDK 17 atau lebih baru (`javac` dan `java` sudah terdaftar di PATH lingkungan).
   ```powershell
   java -version
   javac -version
   ```
3. **Python 3:** Python 3.8+ beserta pustaka visualisasi `matplotlib`:
   ```powershell
   python --version
   pip install matplotlib
   ```
4. **SSH Key AWS:** File private key `rasa-host-key.pem` diletakkan pada folder `Downloads` (`C:\Users\<User>\Downloads\rasa-host-key.pem`) atau di root folder proyek ini.
5. **Konektivitas AWS:** Pastikan kedua instance EC2 di AWS Console berstatus **Running** dan Security Group mengizinkan akses port 22 dari IP internet Anda saat ini.

---

## 3. Skenario Pengujian Langkah demi Langkah

### Skenario 1: Uji Konektivitas Awal (Preflight Check)
Jalankan perintah ini di awal presentasi untuk membuktikan bahwa 4 VM di 2 host fisik EC2 menyala dan responsif:
```powershell
.\preflight.ps1
```
*Script akan membuka tunnel SSH sementara, mengirim permintaan `GET /health` ke setiap VM, mencetak tabel status worker, lalu menutup tunnel secara otomatis.*

---

### Skenario 2: Menjalankan Demo RASA (Default 100 Task)
Untuk menjalankan demonstrasi algoritma RASA dengan dataset standar 100 task:
```powershell
.\demo.ps1
```
**Alur yang otomatis dijalankan:**
1. Memeriksa compiler Java dan mengompilasi kode jika diperlukan.
2. Membuka SSH Port Forwarding tunnel ke Host 1 (`18001`, `18002`) dan Host 2 (`18003`, `18004`).
3. Mengukur throughput komputasi riil setiap VM (*calibration wave*).
4. Membaca dataset Google Cloud Jobs (`dataset/GoCJ_Dataset_100.txt`).
5. Mengeksekusi algoritma RASA (matriks ECT dan aturan paritas genap Max-Min $\leftrightarrow$ Min-Min).
6. Mengirimkan workload kriptografi SHA-256 secara konkuren ke 4 VM di AWS.
7. Mengumpulkan metrik aktual, menghitung Makespan dan Degree of Imbalance (DI).
8. Menghasilkan grafik gambar PNG dan membuka **Dashboard Interaktif Web** di browser default.
9. Menutup seluruh tunnel SSH secara bersih.

---

### Skenario 3: Menjalankan Skala Task Tertentu (200, 300, 400, 500 Task)
Anda dapat menguji jumlah task lain dengan menambahkan parameter `-Tasks`:
```powershell
# Menguji 200 task
.\demo.ps1 -Tasks 200

# Menguji 300 task
.\demo.ps1 -Tasks 300

# Menguji 400 task
.\demo.ps1 -Tasks 400

# Menguji 500 task
.\demo.ps1 -Tasks 500
```
*Script secara otomatis memilih file dataset yang sesuai (`dataset/GoCJ_Dataset_<N>.txt`) dan menyesuaikan rasio komputasi agar durasi eksekusi tetap efisien dan representatif.*

---

### Skenario 4: Membandingkan dengan Algoritma Baseline (Round-Robin)
Untuk memperlihatkan perbedaan performa dan ketimpangan beban (*Degree of Imbalance*) secara langsung di hadapan dosen penguji:
```powershell
# Eksekusi Round-Robin pada skala 100 task
.\demo.ps1 -Tasks 100 -Algorithm RR
```
Tunjukkan kepada penguji bahwa Degree of Imbalance Round-Robin jauh lebih tinggi dibandingkan RASA.

---

### Skenario 5: Benchmark Multi-Skala Lengkap (100 s.d. 500 Task Otomatis)
Jika dosen meminta evaluasi grafik kurva dari 100 sampai 500 task:
```powershell
.\benchmark.ps1
```
Script ini akan:
* Menjalankan RASA dan Round-Robin berturut-turut pada skala 100, 200, 300, 400, dan 500 task.
* Mencatat Makespan, Degree of Imbalance, dan Throughput ke dalam `results/benchmark/benchmark_summary.json`.
* Membuat grafik gabungan 3 panel: `results/latest/scaling_evaluation.png`.
* Otomatis membuka Dashboard Skalabilitas di browser: `results/latest/benchmark_dashboard.html`.

---

## 4. Daftar Parameter & Opsi Script

### Opsi pada `demo.ps1`:

| Parameter | Tipe | Nilai Default | Keterangan |
| :--- | :---: | :---: | :--- |
| `-Tasks` | `int` | `100` | Jumlah task yang akan dijadwalkan (e.g. 20, 100, 200, 300, 400, 500) |
| `-Algorithm` | `string` | `"RASA"` | Algoritma yang digunakan: `RASA` atau `RR` (Round-Robin) |
| `-IterationsPerMi` | `double` | Otomatis | Rasio iterasi hash SHA-256 per MI (default: 1.0 untuk $\ge 100$ task, 10.0 untuk $\le 40$ task) |
| `-KeyPath` | `string` | Auto-detect | Lokasi file private key AWS (`.pem`) |
| `-Host1Ip` | `string` | `"32.236.84.34"` | Alamat IP publik EC2 Host 1 |
| `-Host2Ip` | `string` | `"3.106.226.40"` | Alamat IP publik EC2 Host 2 |
| `-NoBrowser` | `switch` | `false` | Jangan membuka browser otomatis setelah eksekusi selesai |
| `-PreflightOnly` | `switch` | `false` | Hanya periksa status koneksi worker dan keluar |

Contoh penggunaan kombinasi parameter:
```powershell
.\demo.ps1 -Tasks 300 -Algorithm RASA -NoBrowser
```

---

## 5. Lokasi Artefak & Hasil Eksekusi

Semua hasil pengujian terbaru dapat diakses langsung pada direktori:
```text
implementasi-algoritma-RASA-1\results\latest\
```

| Nama File | Format | Deskripsi Isi |
| :--- | :---: | :--- |
| **`dashboard.html`** | HTML | Dashboard interaktif eksekusi per-run (KPI card, grafik, tabel audit 20/100 task) |
| **`benchmark_dashboard.html`** | HTML | Dashboard interaktif skala penuh 100 - 500 task |
| **`scaling_evaluation.png`** | Gambar PNG | Grafik 3 panel kurva Makespan, DI, dan Throughput (100 - 500 task) |
| **`vm_workload_distribution.png`** | Gambar PNG | Grafik batang distribusi waktu eksekusi aktual per VM |
| **`rasa_alternating_strategy.png`** | Gambar PNG | Visualisasi alokasi bergantian strategi Max-Min dan Min-Min |
| **`comparative_evaluation.png`** | Gambar PNG | Perbandingan langsung metrik RASA vs Round-Robin |
| **`summary.json`** | JSON | Ringkasan angka numerik metrik untuk pengolahan data otomatis |
| **`tasks.csv`** | CSV | Log audit mentah per-task (Task ID, MI, VM tujuan, durasi CPU, checksum SHA-256) |

---

## 6. Pemecahan Masalah (Troubleshooting)

### A. Gagal Membuat SSH Tunnel (`Connection timed out` atau `Connection refused`)
* **Penyebab:** Instance EC2 belum berstatus *Running*, atau IP publik laptop Anda berubah sehingga terblokir oleh AWS Security Group.
* **Solusi:**
  1. Buka AWS Management Console $\rightarrow$ EC2 $\rightarrow$ Instances.
  2. Pastikan kedua instance (`rasa-host-1` dan `rasa-host-2`) berstatus *Running*.
  3. Periksa Security Group instance pada tab *Inbound rules*: pastikan port 22 mengizinkan koneksi dari IP laptop Anda (*My IP*).
  4. Jika IP publik host berubah setelah di-restart, jalankan dengan parameter IP baru:
     ```powershell
     .\demo.ps1 -Host1Ip "<IP_BARU_HOST1>" -Host2Ip "<IP_BARU_HOST2>"
     ```

### B. Java Compiler Tidak Ditemukan (`javac is not recognized`)
* **Penyebab:** JDK belum terpasang atau variabel environment `PATH` belum dikonfigurasi.
* **Solusi:** Pasang OpenJDK 17+ (misal Eclipse Temurin 17) dan pastikan direktori `bin` JDK telah ditambahkan ke environment variable `PATH`.

### C. Port Lokal 18001 - 18004 Sudah Digunakan
* **Penyebab:** Terdapat proses SSH sebelumnya yang belum tertutup sempurna.
* **Solusi:** Jalankan perintah PowerShell berikut untuk membersihkan proses SSH yang tertinggal:
  ```powershell
  Get-Process ssh -ErrorAction SilentlyContinue | Stop-Process -Force
  ```

### D. Memeriksa Service Worker Langsung di Guest VM
Jika ingin memverifikasi bahwa service worker aktif di dalam VM tamu melalui SSH:
```powershell
# Akses ke Host 1 lalu ke VM 1
ssh -i "C:\Users\Nindy\Downloads\rasa-host-key.pem" ubuntu@32.236.84.34 "ssh -i ~/.ssh/rasa_guest_ed25519 ubuntu@192.168.122.156 'sudo systemctl status rasa-worker.service'"
```

---

## 7. Panduan Efisiensi Biaya AWS (Setelah Selesai Demo)

Agar tidak mengonsumsi biaya AWS saat infrastruktur tidak digunakan:
1. Buka **AWS Management Console $\rightarrow$ EC2 $\rightarrow$ Instances**.
2. Pilih kedua EC2 Host (`rasa-host-1` dan `rasa-host-2`).
3. Klik **Instance state $\rightarrow$ Stop instance**.
4. ⚠️ **JANGAN** pilih **Terminate instance**. Opsi *Stop instance* menjaga konfigurasi KVM, disk qcow2, dan service worker tetap aman, sehingga siap digunakan kembali kapan saja cukup dengan memilih *Start instance*.
