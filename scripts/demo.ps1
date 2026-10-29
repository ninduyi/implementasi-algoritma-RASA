<#
.SYNOPSIS
    SOKA Real-World RASA Scheduler Orchestrator
    Menjalankan demonstrasi satu script penjadwalan komputasi awan RASA pada 4 VM guest AWS.
#>

[CmdletBinding()]
param (
    [int]$Tasks = 100,
    [string]$Algorithm = "RASA",
    [switch]$PreflightOnly,
    [double]$IterationsPerMi = 0,
    [string]$KeyPath = "",
    [string]$Host1Ip = "32.236.84.34",
    [string]$Host2Ip = "3.106.226.40",
    [switch]$NoBrowser
)

if ($IterationsPerMi -le 0) {
    if ($Tasks -ge 100) { $IterationsPerMi = 1.0 } else { $IterationsPerMi = 10.0 }
}

# Set working directory ke root repository
if ($PSScriptRoot) {
    Set-Location (Join-Path $PSScriptRoot "..")
}
$RootDir = (Get-Location).Path

# Cari SSH private key di lokasi umum
if ([string]::IsNullOrWhiteSpace($KeyPath) -or -not (Test-Path $KeyPath -ErrorAction SilentlyContinue)) {
    $candidates = @(
        "$env:USERPROFILE\Downloads\rasa-host-key.pem",
        "C:\Users\Nindy\Downloads\rasa-host-key.pem",
        "$RootDir\rasa-host-key.pem"
    )
    foreach ($cand in $candidates) {
        if (Test-Path $cand -ErrorAction SilentlyContinue) {
            $KeyPath = $cand
            break
        }
    }
}

if (-not (Test-Path $KeyPath -ErrorAction SilentlyContinue)) {
    Write-Error "[FATAL] Private key AWS tidak ditemukan di $KeyPath"
    exit 1
}

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "       SOKA REAL-WORLD RASA SCHEDULER DEMO (AWS)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "[CONFIG] Algoritma   : $Algorithm" -ForegroundColor Yellow
Write-Host "[CONFIG] Jumlah Task : $Tasks task" -ForegroundColor Yellow
Write-Host "[CONFIG] Host 1      : $Host1Ip (rasa-vm1, rasa-vm2)" -ForegroundColor Yellow
Write-Host "[CONFIG] Host 2      : $Host2Ip (rasa-vm3, rasa-vm4)" -ForegroundColor Yellow
Write-Host "------------------------------------------------------------"

# 1. Cek Java SDK
Write-Host "[PREFLIGHT] Memeriksa compiler & runtime Java..." -NoNewline
$javacVer = & javac -version 2>&1
$javaVer = & java -version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host " GAGAL" -ForegroundColor Red
    Write-Error "[FATAL] Java SDK (javac/java) tidak terdeteksi di PATH."
    exit 1
}
Write-Host " OK ($javacVer)" -ForegroundColor Green

# 2. Build kode jika target/classes belum ada
if (-not (Test-Path "target\classes\soka\realworld\RasaRealController.class")) {
    Write-Host "[BUILD] Mengompilasi source code controller & RASA..." -NoNewline
    New-Item -ItemType Directory -Path "target\classes" -Force | Out-Null
    & javac -d target/classes `
        src/soka/simulasi/RasaScheduler.java `
        src/soka/simulasi/GoCjDatasetGenerator.java `
        src/soka/realworld/CpuWorkload.java `
        src/soka/realworld/RasaWorker.java `
        src/soka/realworld/RasaRealController.java
    if ($LASTEXITCODE -ne 0) {
        Write-Host " GAGAL" -ForegroundColor Red
        Write-Error "[FATAL] Kompilasi gagal."
        exit 1
    }
    Write-Host " OK" -ForegroundColor Green
}

# 3. Buka SSH Port Forwarding Tunnel ke Host 1 dan Host 2
Write-Host "[NETWORK] Membuka SSH Tunnel ke Host 1 ($Host1Ip) & Host 2 ($Host2Ip)..." -NoNewline
$t1 = Start-Process ssh -ArgumentList @(
    '-i', $KeyPath,
    '-N',
    '-o', 'ExitOnForwardFailure=yes',
    '-o', 'StrictHostKeyChecking=no',
    '-o', 'ConnectTimeout=10',
    '-L', '127.0.0.1:18001:192.168.122.156:8080',
    '-L', '127.0.0.1:18002:192.168.122.212:8080',
    "ubuntu@$Host1Ip"
) -PassThru

$t2 = Start-Process ssh -ArgumentList @(
    '-i', $KeyPath,
    '-N',
    '-o', 'ExitOnForwardFailure=yes',
    '-o', 'StrictHostKeyChecking=no',
    '-o', 'ConnectTimeout=10',
    '-L', '127.0.0.1:18003:192.168.122.70:8080',
    '-L', '127.0.0.1:18004:192.168.122.87:8080',
    "ubuntu@$Host2Ip"
) -PassThru

# Tunggu tunnel tersambung
Start-Sleep -Seconds 3

if ($t1.HasExited -or $t2.HasExited) {
    Write-Host " GAGAL" -ForegroundColor Red
    Write-Error "[FATAL] Gagal membuat SSH tunnel ke salah satu host. Periksa security group dan IP host."
    if (-not $t1.HasExited) { Stop-Process -Id $t1.Id -Force }
    if (-not $t2.HasExited) { Stop-Process -Id $t2.Id -Force }
    exit 1
}
Write-Host " OK" -ForegroundColor Green

# 4. Jalankan Controller
$exitCode = 0
$outputDir = ""
try {
    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $outputDir = "results\runs\$timestamp"
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
    
    $datasetFile = "dataset\GoCJ_Dataset_600.txt"
    if (Test-Path "dataset\GoCJ_Dataset_$Tasks.txt") {
        $datasetFile = "dataset\GoCJ_Dataset_$Tasks.txt"
    }

    $argsList = @(
        '-cp', 'target\classes',
        'soka.realworld.RasaRealController',
        '--config', 'config\workers.json',
        '--dataset', "$datasetFile",
        '--limit', "$Tasks",
        '--iterations-per-mi', "$IterationsPerMi",
        '--algorithm', "$Algorithm",
        '--output', "$outputDir"
    )

    if ($PreflightOnly) {
        $argsList += '--preflight-only'
    }

    Write-Host ""
    & java $argsList
    $exitCode = $LASTEXITCODE

    # 5. Visualisasi Otomatis jika bukan preflight-only
    if (-not $PreflightOnly -and (Test-Path "$outputDir\tasks.csv")) {
        Write-Host ""
        Write-Host "[VISUALIZE] Menghasilkan grafik gambar dan Dashboard Presentasi..." -ForegroundColor Cyan
        $visArgs = @('scripts\visualize.py', '--results-dir', $outputDir)
        if (-not $NoBrowser) {
            $visArgs += '--open'
        }
        & python $visArgs

        # Update folder results/latest
        New-Item -ItemType Directory -Path "results\latest" -Force | Out-Null
        Copy-Item -Path "$outputDir\*" -Destination "results\latest" -Recurse -Force
        Write-Host "[INFO] Hasil terbaru siap diakses di: results\latest\" -ForegroundColor Green
    }
} finally {
    # 6. Cleanup tunnel processes
    Write-Host "[CLEANUP] Menutup koneksi SSH tunnel..." -NoNewline
    if (-not $t1.HasExited) { Stop-Process -Id $t1.Id -Force -ErrorAction SilentlyContinue }
    if (-not $t2.HasExited) { Stop-Process -Id $t2.Id -Force -ErrorAction SilentlyContinue }
    Write-Host " OK" -ForegroundColor Green
}

exit $exitCode
