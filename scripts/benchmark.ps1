<#
.SYNOPSIS
    SOKA Real-World Multi-Scale Benchmark Orchestrator (100 s.d. 500 Task)
    Menjalankan pengujian berjenjang pada skala 100, 200, 300, 400, 500 task
    pada 4 VM guest AWS untuk membandingkan RASA vs Round-Robin.
#>

[CmdletBinding()]
param (
    [int[]]$Scales = @(100, 200, 300, 400, 500),
    [string]$KeyPath = "",
    [string]$Host1Ip = "32.236.84.34",
    [string]$Host2Ip = "3.106.226.40",
    [switch]$NoBrowser
)

# Set working directory ke root
if ($PSScriptRoot) {
    Set-Location (Join-Path $PSScriptRoot "..")
}
$RootDir = (Get-Location).Path

# Cari SSH Key
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
Write-Host "   SOKA MULTI-SCALE BENCHMARK (100 s.d. 500 TASK) DI AWS" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "[CONFIG] Skala Task : $($Scales -join ', ') task" -ForegroundColor Yellow
Write-Host "[CONFIG] Algoritma  : RASA vs Round-Robin" -ForegroundColor Yellow
Write-Host "[CONFIG] Topologi   : 2 EC2 Host, 4 VM Guest (KVM)" -ForegroundColor Yellow
Write-Host "------------------------------------------------------------"

# 1. Compile
Write-Host "[BUILD] Memeriksa kompilasi source code..." -NoNewline
New-Item -ItemType Directory -Path "target\classes" -Force | Out-Null
& javac -cp "lib/*;src" -d target/classes src/soka/realworld/*.java src/soka/simulasi/*.java
if ($LASTEXITCODE -ne 0) {
    Write-Host " GAGAL" -ForegroundColor Red
    exit 1
}
Write-Host " OK" -ForegroundColor Green

# 2. Buka SSH Tunnel Tunggal untuk Seluruh Pengujian
Write-Host "[NETWORK] Membuka SSH Tunnel ke Host 1 & Host 2..." -NoNewline
$t1 = Start-Process ssh -ArgumentList @(
    '-i', $KeyPath, '-N',
    '-o', 'ExitOnForwardFailure=yes',
    '-o', 'StrictHostKeyChecking=no',
    '-o', 'ConnectTimeout=10',
    '-L', '127.0.0.1:18001:192.168.122.156:8080',
    '-L', '127.0.0.1:18002:192.168.122.212:8080',
    "ubuntu@$Host1Ip"
) -PassThru

$t2 = Start-Process ssh -ArgumentList @(
    '-i', $KeyPath, '-N',
    '-o', 'ExitOnForwardFailure=yes',
    '-o', 'StrictHostKeyChecking=no',
    '-o', 'ConnectTimeout=10',
    '-L', '127.0.0.1:18003:192.168.122.70:8080',
    '-L', '127.0.0.1:18004:192.168.122.87:8080',
    "ubuntu@$Host2Ip"
) -PassThru

Start-Sleep -Seconds 3
if ($t1.HasExited -or $t2.HasExited) {
    Write-Host " GAGAL" -ForegroundColor Red
    if (-not $t1.HasExited) { Stop-Process -Id $t1.Id -Force }
    if (-not $t2.HasExited) { Stop-Process -Id $t2.Id -Force }
    exit 1
}
Write-Host " OK" -ForegroundColor Green

$benchmarkDir = "results\benchmark"
New-Item -ItemType Directory -Path $benchmarkDir -Force | Out-Null

$summaryData = @{}

try {
    foreach ($scale in $Scales) {
        Write-Host ""
        Write-Host "============================================================" -ForegroundColor Yellow
        Write-Host "            MENGUJI SKALA: $scale TASK NYATA" -ForegroundColor Yellow
        Write-Host "============================================================" -ForegroundColor Yellow
        
        $datasetFile = "dataset\GoCJ_Dataset_$scale.txt"
        if (-not (Test-Path $datasetFile)) {
            $datasetFile = "dataset\GoCJ_Dataset_600.txt"
        }

        $summaryData["$scale"] = @{}

        # A. Run RASA
        $rasaOut = "$benchmarkDir\scale-$scale-rasa"
        New-Item -ItemType Directory -Path $rasaOut -Force | Out-Null
        Write-Host "[RUN] Mengeksekusi Algoritma RASA ($scale Task)..." -ForegroundColor Cyan
        & java -cp target/classes soka.realworld.RasaRealController `
            --config config/workers.json `
            --dataset $datasetFile `
            --limit $scale `
            --iterations-per-mi 1.0 `
            --algorithm RASA `
            --output $rasaOut
        
        if (Test-Path "$rasaOut\summary.json") {
            $rasaJson = Get-Content "$rasaOut\summary.json" -Raw | ConvertFrom-Json
            $summaryData["$scale"]["RASA"] = @{
                "makespan" = $rasaJson.makespanSeconds
                "degreeOfImbalance" = $rasaJson.degreeOfImbalance
                "throughput" = $rasaJson.throughputTasksPerSec
                "avgDuration" = $rasaJson.avgDurationSeconds
            }
        }

        # B. Run Round-Robin
        $rrOut = "$benchmarkDir\scale-$scale-rr"
        New-Item -ItemType Directory -Path $rrOut -Force | Out-Null
        Write-Host ""
        Write-Host "[RUN] Mengeksekusi Algoritma Round-Robin ($scale Task)..." -ForegroundColor Cyan
        & java -cp target/classes soka.realworld.RasaRealController `
            --config config/workers.json `
            --dataset $datasetFile `
            --limit $scale `
            --iterations-per-mi 1.0 `
            --algorithm RR `
            --output $rrOut

        if (Test-Path "$rrOut\summary.json") {
            $rrJson = Get-Content "$rrOut\summary.json" -Raw | ConvertFrom-Json
            $summaryData["$scale"]["RR"] = @{
                "makespan" = $rrJson.makespanSeconds
                "degreeOfImbalance" = $rrJson.degreeOfImbalance
                "throughput" = $rrJson.throughputTasksPerSec
                "avgDuration" = $rrJson.avgDurationSeconds
            }
        }
    }

    # Simpan summary JSON gabungan
    $jsonPath = "$benchmarkDir\benchmark_summary.json"
    $summaryData | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonPath -Encoding UTF8
    Write-Host ""
    Write-Host "[BENCHMARK] Ringkasan data benchmark tersimpan di: $jsonPath" -ForegroundColor Green

    # Jalankan Python visualizer untuk buat grafik perbandingan skala
    Write-Host "[VISUALIZE] Membuat grafik perbandingan skala (100 - 500 task)..." -ForegroundColor Cyan
    & python scripts/visualize_benchmark.py --summary-file $jsonPath --output-dir $benchmarkDir

    # Buka Dashboard di browser
    $dashPath = "$benchmarkDir\benchmark_dashboard.html"
    if (-not $NoBrowser -and (Test-Path $dashPath)) {
        Write-Host "[BROWSER] Membuka Dashboard Benchmark di browser..." -ForegroundColor Green
        Start-Process $dashPath
    }

} finally {
    Write-Host "[CLEANUP] Menutup koneksi SSH tunnel..." -NoNewline
    if (-not $t1.HasExited) { Stop-Process -Id $t1.Id -Force -ErrorAction SilentlyContinue }
    if (-not $t2.HasExited) { Stop-Process -Id $t2.Id -Force -ErrorAction SilentlyContinue }
    Write-Host " OK" -ForegroundColor Green
}

