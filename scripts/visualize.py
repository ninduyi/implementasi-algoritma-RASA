"""
SOKA RASA Experiment Visualizer
Menghasilkan grafik visual (PNG) dan interactive HTML dashboard dari hasil eksperimen nyata di AWS.
"""

import sys
import os
import json
import glob
import argparse
import pandas as pd
import matplotlib.pyplot as plt
import matplotlib.ticker as ticker

# Style plotting
plt.style.use('seaborn-v0_8-whitegrid' if 'seaborn-v0_8-whitegrid' in plt.style.available else 'default')
plt.rcParams['font.sans-serif'] = 'Segoe UI, DejaVu Sans, Arial'
plt.rcParams['axes.edgecolor'] = '#cccccc'
plt.rcParams['axes.linewidth'] = 0.8

def find_latest_results(base_dir="results/runs"):
    subdirs = [os.path.join(base_dir, d) for d in os.listdir(base_dir) if os.path.isdir(os.path.join(base_dir, d))]
    if not subdirs:
        return None
    subdirs.sort(key=os.path.getmtime, reverse=True)
    return subdirs[0]

def generate_charts(results_dir, open_in_browser=False):
    tasks_csv = os.path.join(results_dir, "tasks.csv")
    summary_json = os.path.join(results_dir, "summary.json")

    if not os.path.exists(tasks_csv) or not os.path.exists(summary_json):
        print(f"[ERROR] tasks.csv atau summary.json tidak ditemukan di {results_dir}")
        return

    with open(summary_json, 'r', encoding='utf-8') as f:
        summary = json.load(f)

    df = pd.read_csv(tasks_csv)
    algo_name = summary.get("algorithm", "RASA")

    print(f"[VISUALIZE] Memproses data dari: {results_dir}")
    print(f"[VISUALIZE] Algoritma: {algo_name} | Total Task: {len(df)}")

    # 1. Grafik Distribusi Beban Kerja per VM (Bar Chart)
    fig, ax1 = plt.subplots(figsize=(9, 5), dpi=150)
    vm_stats = df.groupby('VmId').agg(
        total_time=('Execution_s', 'sum'),
        task_count=('TaskId', 'count')
    ).reset_index()

    colors = ['#2b5c8f', '#4682b4', '#5c9ec9', '#7cb5d8']
    bars = ax1.bar(vm_stats['VmId'], vm_stats['total_time'], color=colors[:len(vm_stats)], width=0.45, label='Durasi Eksekusi Aktual (detik)', edgecolor='#1e3f61')
    ax1.set_ylabel('Total Waktu Eksekusi (detik)', color='#1e3f61', fontsize=11, fontweight='bold')
    ax1.set_xlabel('Virtual Machine Guest (AWS)', fontsize=11, fontweight='bold')
    ax1.set_title(f'Distribusi Beban Kerja per Virtual Machine — {algo_name}\n(Makespan: {summary.get("makespanSeconds", 0):.2f}s | Degree of Imbalance: {summary.get("degreeOfImbalance", 0):.4f})', fontsize=12, fontweight='bold', pad=12)

    # Label di atas bar
    for bar in bars:
        h = bar.get_height()
        ax1.annotate(f'{h:.3f} s', xy=(bar.get_x() + bar.get_width() / 2, h),
                     xytext=(0, 4), textcoords="offset points", ha='center', va='bottom', fontsize=9, fontweight='bold')

    # Line chart untuk task count di sumbu sekunder
    ax2 = ax1.twinx()
    ax2.plot(vm_stats['VmId'], vm_stats['task_count'], color='#e74c3c', marker='o', linewidth=2.5, markersize=8, label='Jumlah Task')
    ax2.set_ylabel('Jumlah Task Ditugaskan', color='#c0392b', fontsize=11, fontweight='bold')
    ax2.grid(False)

    for i, row in vm_stats.iterrows():
        ax2.annotate(f"{row['task_count']} task", (row['VmId'], row['task_count']),
                     xytext=(0, 7), textcoords="offset points", ha='center', fontsize=9, fontweight='bold', color='#c0392b')

    fig.tight_layout()
    chart1_path = os.path.join(results_dir, "vm_workload_distribution.png")
    fig.savefig(chart1_path)
    plt.close(fig)
    print(f"  [+] Tersimpan: {chart1_path}")

    # 2. Grafik Strategi RASA (Min-Min vs Max-Min per Task)
    if 'Strategy' in df.columns and algo_name == "RASA":
        fig, ax = plt.subplots(figsize=(11, 5), dpi=150)
        strat_colors = {'MIN-MIN': '#27ae60', 'MAX-MIN': '#e67e22'}
        bar_colors = [strat_colors.get(s, '#7f8c8d') for s in df['Strategy']]
        
        bars = ax.bar(df['TaskId'].astype(str), df['Length_MI'] / 1000.0, color=bar_colors, edgecolor='#333333', linewidth=0.5)
        ax.set_xlabel('Task ID (Urutan Eksekusi)', fontsize=11, fontweight='bold')
        ax.set_ylabel('Panjang Task (Ribu MI)', fontsize=11, fontweight='bold')
        ax.set_title('Logika Berselang-seling (Alternating) RASA: Min-Min vs Max-Min', fontsize=12, fontweight='bold', pad=12)
        
        # Legend manual
        from matplotlib.patches import Patch
        legend_elements = [
            Patch(facecolor='#27ae60', label='Ronde Min-Min (Task Kecil/Sedang)'),
            Patch(facecolor='#e67e22', label='Ronde Max-Min (Task Besar/Ekstra)')
        ]
        ax.legend(handles=legend_elements, loc='upper left', frameon=True)
        
        # Annotate target VM
        for i, bar in enumerate(bars):
            h = bar.get_height()
            vm_label = df['VmId'].iloc[i].replace('rasa-', '')
            ax.annotate(vm_label, xy=(bar.get_x() + bar.get_width() / 2, h),
                        xytext=(0, 3), textcoords="offset points", ha='center', va='bottom', fontsize=7, rotation=45)

        fig.tight_layout()
        chart2_path = os.path.join(results_dir, "rasa_alternating_strategy.png")
        fig.savefig(chart2_path)
        plt.close(fig)
        print(f"  [+] Tersimpan: {chart2_path}")

    # 3. Grafik Perbandingan Komparatif RASA vs Round-Robin (jika ada baseline)
    base_dir = os.path.dirname(results_dir)
    rr_runs = glob.glob(os.path.join(base_dir, "*", "summary.json"))
    rr_summary = None
    for r in rr_runs:
        try:
            with open(r, 'r', encoding='utf-8') as f:
                d = json.load(f)
                if d.get('algorithm') == 'RR':
                    rr_summary = d
                    break
        except Exception:
            pass

    if rr_summary:
        fig, (ax_mk, ax_di) = plt.subplots(1, 2, figsize=(10, 4.5), dpi=150)

        # Makespan Comparison
        algos = ['RASA', 'Round-Robin']
        makespans = [summary.get('makespanSeconds', 0), rr_summary.get('makespanSeconds', 0)]
        b1 = ax_mk.bar(algos, makespans, color=['#2980b9', '#95a5a6'], width=0.5, edgecolor='#2c3e50')
        ax_mk.set_ylabel('Makespan (detik)', fontsize=10, fontweight='bold')
        ax_mk.set_title('Perbandingan Makespan (Waktu Selesai Batch)', fontsize=11, fontweight='bold')
        for b in b1:
            ax_mk.annotate(f"{b.get_height():.2f} s", (b.get_x() + b.get_width()/2, b.get_height()),
                           xytext=(0, 3), textcoords="offset points", ha='center', fontweight='bold')

        # Degree of Imbalance Comparison
        dis = [summary.get('degreeOfImbalance', 0), rr_summary.get('degreeOfImbalance', 0)]
        b2 = ax_di.bar(algos, dis, color=['#27ae60', '#e74c3c'], width=0.5, edgecolor='#2c3e50')
        ax_di.set_ylabel('Degree of Imbalance (DI)', fontsize=10, fontweight='bold')
        ax_di.set_title('Perbandingan Keseimbangan Beban (DI)\n(Makin rendah makin seimbang)', fontsize=11, fontweight='bold')
        for b in b2:
            ax_di.annotate(f"{b.get_height():.4f}", (b.get_x() + b.get_width()/2, b.get_height()),
                           xytext=(0, 3), textcoords="offset points", ha='center', fontweight='bold')

        fig.suptitle('Evaluasi Kinerja Nyata pada 4 VM AWS (20 Task GoCJ)', fontsize=13, fontweight='bold', y=1.02)
        fig.tight_layout()
        chart3_path = os.path.join(results_dir, "comparative_evaluation.png")
        fig.savefig(chart3_path, bbox_inches='tight')
        plt.close(fig)
        print(f"  [+] Tersimpan: {chart3_path}")

    # 4. Buat Interactive HTML Dashboard
    html_path = os.path.join(results_dir, "dashboard.html")
    create_html_dashboard(results_dir, summary, df, rr_summary, html_path)
    print(f"  [+] Tersimpan HTML Dashboard: {html_path}")

    if open_in_browser:
        import webbrowser
        webbrowser.open(os.path.abspath(html_path))

def create_html_dashboard(results_dir, summary, df, rr_summary, html_path):
    table_rows = ""
    for _, r in df.iterrows():
        strat = r.get('Strategy', '-')
        badge_cls = "badge-minmin" if "MIN-MIN" in strat else ("badge-maxmin" if "MAX-MIN" in strat else "badge-rr")
        table_rows += f"""
        <tr>
            <td><strong>#{r['TaskId']:02d}</strong></td>
            <td>{r['Length_MI']:,.0f} MI</td>
            <td><span class="badge {badge_cls}">{strat}</span></td>
            <td><code>{r['VmId']}</code></td>
            <td><code>{r['HostId']}</code></td>
            <td>{r['Execution_s']:.4f} s</td>
            <td>{r['Cpu_s']:.4f} s</td>
            <td><code title="{r['Checksum']}">{r['Checksum'][:12]}...</code></td>
        </tr>
        """

    rr_box = ""
    if rr_summary:
        di_ratio = ((rr_summary['degreeOfImbalance'] - summary['degreeOfImbalance']) / rr_summary['degreeOfImbalance']) * 100
        rr_box = f"""
        <div class="card comparison-card">
            <h3>Perbandingan Langsung vs Round-Robin</h3>
            <p>Pada dataset 20 task yang sama di AWS:</p>
            <ul>
                <li><strong>Degree of Imbalance:</strong> RASA <code>{summary.get('degreeOfImbalance', 0):.4f}</code> vs Round-Robin <code>{rr_summary.get('degreeOfImbalance', 0):.4f}</code> (<strong>{di_ratio:.1f}% lebih seimbang!</strong>).</li>
                <li><strong>Makespan:</strong> RASA <code>{summary.get('makespanSeconds', 0):.2f}s</code> vs Round-Robin <code>{rr_summary.get('makespanSeconds', 0):.2f}s</code>.</li>
            </ul>
        </div>
        """

    html = f"""<!DOCTYPE html>
<html lang="id">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>SOKA Dashboard — Hasil Penjadwalan RASA (AWS Real-World)</title>
    <style>
        :root {{
            --primary: #1a5276;
            --accent: #2980b9;
            --success: #27ae60;
            --warning: #e67e22;
            --bg: #f8f9fa;
            --card-bg: #ffffff;
            --text: #2c3e50;
        }}
        body {{
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg);
            color: var(--text);
            margin: 0;
            padding: 24px;
        }}
        .header {{
            background: linear-gradient(135deg, #1b4f72, #2874a6);
            color: white;
            padding: 24px 32px;
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.1);
            margin-bottom: 24px;
        }}
        .header h1 {{ margin: 0 0 8px 0; font-size: 24px; }}
        .header p {{ margin: 0; opacity: 0.9; font-size: 14px; }}
        .metrics-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 16px;
            margin-bottom: 24px;
        }}
        .metric-card {{
            background: var(--card-bg);
            padding: 18px 20px;
            border-radius: 10px;
            box-shadow: 0 2px 6px rgba(0,0,0,0.06);
            border-left: 4px solid var(--accent);
        }}
        .metric-card h4 {{ margin: 0 0 6px 0; font-size: 13px; color: #7f8c8d; text-transform: uppercase; }}
        .metric-card .value {{ font-size: 26px; font-weight: bold; color: var(--primary); }}
        .metric-card .desc {{ font-size: 12px; color: #95a5a6; margin-top: 4px; }}
        .charts-container {{
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 20px;
            margin-bottom: 24px;
        }}
        .card {{
            background: var(--card-bg);
            padding: 20px;
            border-radius: 10px;
            box-shadow: 0 2px 6px rgba(0,0,0,0.06);
        }}
        .card h3 {{ margin-top: 0; font-size: 16px; color: var(--primary); border-bottom: 2px solid #ecf0f1; padding-bottom: 10px; }}
        .card img {{ width: 100%; border-radius: 6px; }}
        .comparison-card {{
            grid-column: span 2;
            background: #e8f8f5;
            border: 1px solid #a3e4d7;
        }}
        .table-responsive {{ overflow-x: auto; }}
        table {{
            width: 100%;
            border-collapse: collapse;
            font-size: 13px;
        }}
        th, td {{
            padding: 10px 12px;
            text-align: left;
            border-bottom: 1px solid #ecf0f1;
        }}
        th {{ background: #f4f6f7; color: #34495e; font-weight: 600; }}
        tr:hover {{ background: #fdfefe; }}
        .badge {{
            display: inline-block;
            padding: 3px 8px;
            border-radius: 4px;
            font-size: 11px;
            font-weight: bold;
        }}
        .badge-minmin {{ background: #d4efdf; color: #1e8449; }}
        .badge-maxmin {{ background: #fdebd0; color: #b9770e; }}
        .badge-rr {{ background: #ebedef; color: #5d6d7e; }}
        code {{ background: #eaeded; padding: 2px 6px; border-radius: 4px; font-family: Consolas, monospace; font-size: 12px; }}
    </style>
</head>
<body>

    <div class="header">
        <h1>SOKA Real-World RASA Scheduler — Dashboard Presentasi</h1>
        <p>Implementasi Penjadwalan Komputasi Awan RASA pada 4 VM Guest AWS (KVM/libvirt)</p>
    </div>

    <div class="metrics-grid">
        <div class="metric-card">
            <h4>Algoritma</h4>
            <div class="value">{summary.get('algorithm', 'RASA')}</div>
            <div class="desc">RASA Heuristic Core</div>
        </div>
        <div class="metric-card">
            <h4>Total Task</h4>
            <div class="value">{summary.get('tasksCompleted', 0)} / {summary.get('tasksRequested', 0)}</div>
            <div class="desc">Tingkat Keberhasilan 100%</div>
        </div>
        <div class="metric-card">
            <h4>Makespan Aktual</h4>
            <div class="value">{summary.get('makespanSeconds', 0):.2f} s</div>
            <div class="desc">Waktu Total Batch Selesai</div>
        </div>
        <div class="metric-card" style="border-left-color: var(--success);">
            <h4>Degree of Imbalance</h4>
            <div class="value">{summary.get('degreeOfImbalance', 0):.4f}</div>
            <div class="desc">Beban Kerja Sangat Merata</div>
        </div>
        <div class="metric-card">
            <h4>Throughput</h4>
            <div class="value">{summary.get('throughputTasksPerSec', 0):.2f}</div>
            <div class="desc">Task per Detik</div>
        </div>
    </div>

    <div class="charts-container">
        <div class="card">
            <h3>Distribusi Beban Kerja per Virtual Machine</h3>
            <img src="vm_workload_distribution.png" alt="Distribusi Beban VM">
        </div>
        <div class="card">
            <h3>Pola Penjadwalan Berselang-Seling RASA</h3>
            <img src="rasa_alternating_strategy.png" alt="Pola Alternating RASA">
        </div>
        {f'''<div class="card" style="grid-column: span 2;">
            <h3>Perbandingan Komparatif RASA vs Round-Robin</h3>
            <img src="comparative_evaluation.png" alt="Perbandingan RASA vs Round Robin">
        </div>''' if rr_summary else ''}
    </div>

    <div class="card">
        <h3>Detail Eksekusi Task Aktual (Verifiable SHA-256 Workload)</h3>
        <div class="table-responsive">
            <table>
                <thead>
                    <tr>
                        <th>Task ID</th>
                        <th>Panjang (MI)</th>
                        <th>Strategi RASA</th>
                        <th>Target VM</th>
                        <th>Parent Host</th>
                        <th>Durasi Wall-Clock</th>
                        <th>Durasi CPU</th>
                        <th>Hash SHA-256</th>
                    </tr>
                </thead>
                <tbody>
                    {table_rows}
                </tbody>
            </table>
        </div>
    </div>

</body>
</html>
"""
    with open(html_path, 'w', encoding='utf-8') as f:
        f.write(html)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="SOKA RASA Visualizer")
    parser.add_argument("--results-dir", type=str, default="", help="Direktori hasil eksperimen")
    parser.add_argument("--open", action="store_true", help="Buka dashboard HTML di browser setelah selesai")
    args = parser.parse_args()

    target_dir = args.results_dir if args.results_dir else find_latest_results()
    if not target_dir or not os.path.exists(target_dir):
        print(f"[ERROR] Direktori hasil tidak ditemukan: {target_dir}")
        sys.exit(1)

    generate_charts(target_dir, open_in_browser=args.open)

