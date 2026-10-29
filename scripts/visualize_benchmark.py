#!/usr/bin/env python3
"""
Generator Grafik Skala Benchmark SOKA Real-World (100 s.d. 500 Task)
Menghasilkan grafik garis skalabilitas Makespan, Degree of Imbalance, dan Throughput
untuk evaluasi performa RASA vs Round-Robin.
"""

import sys
import os
import json
import argparse
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

def main():
    parser = argparse.ArgumentParser(description="Visualize SOKA Scaling Benchmark")
    parser.add_argument("--summary-file", default="results/benchmark/benchmark_summary.json", help="Path to benchmark_summary.json")
    parser.add_argument("--output-dir", default="results/benchmark", help="Directory to save generated charts")
    args = parser.parse_args()

    if not os.path.exists(args.summary_file):
        print(f"[ERROR] File {args.summary_file} tidak ditemukan.")
        sys.exit(1)

    os.makedirs(args.output_dir, exist_ok=True)

    with open(args.summary_file, 'r', encoding='utf-8-sig') as f:
        data = json.load(f)

    # data structure: list of runs or dict with scales
    scales = sorted([int(k) for k in data.keys() if k.isdigit()])
    if not scales:
        print("[ERROR] Tidak ada data skala numerik di benchmark_summary.json")
        sys.exit(1)

    rasa_makespan = [data[str(s)]["RASA"]["makespan"] for s in scales]
    rr_makespan = [data[str(s)]["RR"]["makespan"] for s in scales]

    rasa_di = [data[str(s)]["RASA"]["degreeOfImbalance"] for s in scales]
    rr_di = [data[str(s)]["RR"]["degreeOfImbalance"] for s in scales]

    rasa_tp = [data[str(s)]["RASA"]["throughput"] for s in scales]
    rr_tp = [data[str(s)]["RR"]["throughput"] for s in scales]

    plt.style.use('seaborn-v0_8-whitegrid' if 'seaborn-v0_8-whitegrid' in plt.style.available else 'default')

    # 1. Grafik Gabungan Skalabilitas 3-Panel
    fig, axes = plt.subplots(1, 3, figsize=(16, 5), dpi=200)

    # Panel A: Makespan
    ax = axes[0]
    ax.plot(scales, rasa_makespan, marker='o', color='#2563eb', linewidth=2.5, markersize=8, label='RASA')
    ax.plot(scales, rr_makespan, marker='s', color='#dc2626', linewidth=2, linestyle='--', markersize=7, label='Round-Robin')
    ax.set_title('A. Makespan Aktual (Detik)', fontsize=12, fontweight='bold', pad=12)
    ax.set_xlabel('Jumlah Task (GoCJ Dataset)', fontsize=10)
    ax.set_ylabel('Total Waktu Eksekusi (detik)', fontsize=10)
    ax.set_xticks(scales)
    ax.legend(loc='upper left', frameon=True)
    ax.grid(True, linestyle=':', alpha=0.6)

    # Panel B: Degree of Imbalance (DI)
    ax = axes[1]
    ax.plot(scales, rasa_di, marker='o', color='#16a34a', linewidth=2.5, markersize=8, label='RASA (Lebih Seimbang)')
    ax.plot(scales, rr_di, marker='s', color='#ea580c', linewidth=2, linestyle='--', markersize=7, label='Round-Robin')
    ax.set_title('B. Degree of Imbalance (DI)', fontsize=12, fontweight='bold', pad=12)
    ax.set_xlabel('Jumlah Task (GoCJ Dataset)', fontsize=10)
    ax.set_ylabel('Indeks Ketimpangan Beban (DI)', fontsize=10)
    ax.set_xticks(scales)
    ax.legend(loc='upper right', frameon=True)
    ax.grid(True, linestyle=':', alpha=0.6)

    # Panel C: Throughput
    ax = axes[2]
    ax.plot(scales, rasa_tp, marker='o', color='#9333ea', linewidth=2.5, markersize=8, label='RASA')
    ax.plot(scales, rr_tp, marker='s', color='#64748b', linewidth=2, linestyle='--', markersize=7, label='Round-Robin')
    ax.set_title('C. Throughput Sistem (Task/Detik)', fontsize=12, fontweight='bold', pad=12)
    ax.set_xlabel('Jumlah Task (GoCJ Dataset)', fontsize=10)
    ax.set_ylabel('Throughput (Task / Detik)', fontsize=10)
    ax.set_xticks(scales)
    ax.legend(loc='lower right', frameon=True)
    ax.grid(True, linestyle=':', alpha=0.6)

    fig.suptitle('Evaluasi Skalabilitas Algoritma RASA vs Round-Robin di AWS Cloud (100 - 500 Task)', 
                 fontsize=14, fontweight='bold', y=1.03)
    fig.tight_layout()
    chart_path = os.path.join(args.output_dir, "scaling_evaluation.png")
    fig.savefig(chart_path, bbox_inches='tight')
    plt.close(fig)
    print(f"  [+] Tersimpan grafik gabungan: {chart_path}")

    # 2. Generate HTML Report
    html_content = f"""<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <title>SOKA Benchmark Skalabilitas RASA (100 - 500 Task)</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 24px; }}
        .container {{ max-width: 1200px; margin: 0 auto; }}
        .header {{ background: #1e293b; padding: 24px; border-radius: 12px; border: 1px solid #334155; margin-bottom: 24px; }}
        h1 {{ margin: 0 0 8px 0; font-size: 24px; color: #38bdf8; }}
        .badge {{ display: inline-block; padding: 4px 12px; border-radius: 999px; font-size: 12px; font-weight: 600; background: #16a34a; color: white; margin-right: 8px; }}
        .card {{ background: #1e293b; padding: 24px; border-radius: 12px; border: 1px solid #334155; margin-bottom: 24px; }}
        table {{ width: 100%; border-collapse: collapse; margin-top: 16px; font-size: 14px; }}
        th, td {{ padding: 12px; text-align: center; border-bottom: 1px solid #334155; }}
        th {{ background: #0f172a; color: #94a3b8; font-weight: 600; }}
        tr:hover {{ background: #26334d; }}
        .highlight {{ color: #4ade80; font-weight: bold; }}
        img {{ max-width: 100%; border-radius: 8px; border: 1px solid #334155; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <span class="badge">AWS REAL-WORLD CLOUD</span>
            <span class="badge" style="background:#2563eb;">TOPOLOGI 4 VM GUEST</span>
            <h1>Laporan Skalabilitas Benchmark RASA (100 s.d. 500 Task)</h1>
            <p style="color: #94a3b8; margin: 8px 0 0 0;">Evaluasi komparatif multi-skala antara algoritma RASA dan Round-Robin pada dataset Google Cloud Jobs.</p>
        </div>

        <div class="card">
            <h2 style="margin-top:0; color:#38bdf8; font-size:18px;">Grafik Kurva Skalabilitas Kinerja</h2>
            <img src="scaling_evaluation.png" alt="Scaling Evaluation Chart">
        </div>

        <div class="card">
            <h2 style="margin-top:0; color:#38bdf8; font-size:18px;">Tabel Data Evaluasi Multi-Skala</h2>
            <table>
                <thead>
                    <tr>
                        <th rowspan="2">Skala Task</th>
                        <th colspan="2">Makespan (Detik)</th>
                        <th colspan="2">Degree of Imbalance (DI)</th>
                        <th colspan="2">Throughput (Task/Detik)</th>
                        <th rowspan="2">Efisiensi Beban RASA</th>
                    </tr>
                    <tr>
                        <th>RASA</th><th>Round-Robin</th>
                        <th>RASA</th><th>Round-Robin</th>
                        <th>RASA</th><th>Round-Robin</th>
                    </tr>
                </thead>
                <tbody>
    """
    for s in scales:
        r_m = data[str(s)]["RASA"]["makespan"]
        rr_m = data[str(s)]["RR"]["makespan"]
        r_di = data[str(s)]["RASA"]["degreeOfImbalance"]
        rr_di = data[str(s)]["RR"]["degreeOfImbalance"]
        r_tp = data[str(s)]["RASA"]["throughput"]
        rr_tp = data[str(s)]["RR"]["throughput"]
        diff_di = ((rr_di - r_di) / max(rr_di, 0.001)) * 100
        html_content += f"""
                    <tr>
                        <td><strong>{s} Task</strong></td>
                        <td class="highlight">{r_m:.4f}s</td><td>{rr_m:.4f}s</td>
                        <td class="highlight">{r_di:.4f}</td><td>{rr_di:.4f}</td>
                        <td class="highlight">{r_tp:.2f}</td><td>{rr_tp:.2f}</td>
                        <td class="highlight">+{diff_di:.1f}% Lebih Seimbang</td>
                    </tr>
        """
    html_content += """
                </tbody>
            </table>
        </div>
    </div>
</body>
</html>
    """
    html_path = os.path.join(args.output_dir, "benchmark_dashboard.html")
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html_content)
    print(f"  [+] Tersimpan Dashboard HTML: {html_path}")

if __name__ == "__main__":
    main()
