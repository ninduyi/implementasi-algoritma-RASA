import json, os, sys
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

dirs = sys.argv[1:]
if not dirs:
    sys.exit("Usage: bandingkan.py run_dir1 run_dir2 ...")
NAMA = {"RASA": "RASA", "RR": "Round-Robin", "MIN_MIN": "Min-Min", "MAX_MIN": "Max-Min"}
WARNA = {"RASA": "#2980b9", "RR": "#95a5a6", "MIN_MIN": "#27ae60", "MAX_MIN": "#e67e22"}
rows = []
for d in dirs:
    s = json.load(open(os.path.join(d, "summary.json"), encoding="utf-8"))
    rows.append((s["algorithm"], s["makespanSeconds"], s["degreeOfImbalance"],
                 s["throughputTasksPerSec"], s["tasksCompleted"], s["tasksRequested"]))
order = ["RASA", "RR", "MIN_MIN", "MAX_MIN"]
rows.sort(key=lambda r: order.index(r[0]) if r[0] in order else 9)
print(f"{'Algoritma':<12}{'Makespan(s)':>13}{'DoI':>9}{'Throughput':>12}{'Sukses':>10}")
for a, m, di, tp, ok, n in rows:
    print(f"{NAMA.get(a,a):<12}{m:>13.2f}{di:>9.4f}{tp:>12.2f}{ok:>6}/{n}")

fig, axs = plt.subplots(1, 3, figsize=(13, 4.2), dpi=150)
for ax, idx, judul in zip(axs, (1, 2, 3), ("Makespan (detik)\nmakin kecil makin baik",
                                           "Degree of Imbalance\nmakin kecil makin seimbang",
                                           "Throughput (task/detik)\nmakin besar makin baik")):
    names = [NAMA.get(r[0], r[0]) for r in rows]
    vals = [r[idx] for r in rows]
    bars = ax.bar(names, vals, color=[WARNA.get(r[0], "#999") for r in rows], edgecolor="#2c3e50")
    ax.set_title(judul, fontsize=10, fontweight="bold")
    for b, v in zip(bars, vals):
        ax.annotate(f"{v:.2f}" if idx != 2 else f"{v:.3f}", (b.get_x() + b.get_width()/2, v),
                    xytext=(0, 3), textcoords="offset points", ha="center", fontsize=9, fontweight="bold")
    ax.tick_params(axis="x", labelsize=8)
fig.suptitle("Perbandingan algoritma pada AWS (1 run per algoritma, kalibrasi sama)", fontsize=11, fontweight="bold")
fig.tight_layout()
out = "results/perbandingan_algoritma.png"
fig.savefig(out); print("Tersimpan:", out)
