import json, glob, os, sys, statistics as st, collections
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

RUNS = sys.argv[1] if len(sys.argv) > 1 else "results/runs"
MULAI = sys.argv[2] if len(sys.argv) > 2 else "20261030-084009"
OUT = sys.argv[3] if len(sys.argv) > 3 else "results"

NAMA = {"RASA": "RASA", "RR": "Round-Robin", "MIN_MIN": "Min-Min", "MAX_MIN": "Max-Min"}
WARNA = {"RASA": "#2980b9", "RR": "#95a5a6", "MIN_MIN": "#27ae60", "MAX_MIN": "#e67e22"}
ORDER = ["RASA", "RR", "MIN_MIN", "MAX_MIN"]

g = collections.defaultdict(list)
for f in sorted(glob.glob(os.path.join(RUNS, "*", "summary.json"))):
    if os.path.basename(os.path.dirname(f)) < MULAI:
        continue
    s = json.load(open(f, encoding="utf-8"))
    if s.get("tasksCompleted") != s.get("tasksRequested"):
        continue
    g[s["algorithm"]].append((s["makespanSeconds"], s["degreeOfImbalance"], s["throughputTasksPerSec"]))

algos = [a for a in ORDER if g[a]]
sd = lambda x: st.stdev(x) if len(x) > 1 else 0.0
stat = {}
for a in algos:
    cols = list(zip(*g[a]))
    stat[a] = [(st.mean(c), sd(c), list(c)) for c in cols]

print(f"{'Algoritma':<13}{'n':>3}{'Makespan (s)':>20}{'DoI':>18}{'Throughput':>20}")
for a in algos:
    (mm, ms, _), (dm, ds, _), (tm, ts, _) = stat[a]
    print(f"{NAMA[a]:<13}{len(g[a]):>3}{mm:>13.2f} ± {ms:<4.2f}{dm:>11.3f} ± {ds:<5.3f}{tm:>12.2f} ± {ts:<4.2f}")

fig, axs = plt.subplots(1, 3, figsize=(14, 4.8), dpi=150)
judul = ["Makespan (detik)\nmakin kecil makin baik", "Degree of Imbalance\nmakin kecil makin seimbang",
         "Throughput (task/detik)\nmakin besar makin baik"]
for k, ax in enumerate(axs):
    xs = range(len(algos))
    means = [stat[a][k][0] for a in algos]
    sds = [stat[a][k][1] for a in algos]
    ax.bar(xs, means, yerr=sds, capsize=5, color=[WARNA[a] for a in algos], edgecolor="#2c3e50", alpha=0.9)
    for i, a in enumerate(algos):
        pts = stat[a][k][2]
        ax.scatter([i + (j - (len(pts) - 1) / 2) * 0.06 for j in range(len(pts))], pts,
                   color="black", s=12, zorder=3)
        ax.annotate(f"{means[i]:.3f}" if k == 1 else f"{means[i]:.2f}", (i, means[i] + sds[i]),
                    xytext=(0, 5), textcoords="offset points", ha="center", fontsize=9, fontweight="bold")
    ax.set_xticks(list(xs)); ax.set_xticklabels([NAMA[a] for a in algos], fontsize=9)
    ax.set_title(judul[k], fontsize=10, fontweight="bold")
    ax.set_ylim(0, max(m + s for m, s in zip(means, sds)) * 1.18)
n = min(len(g[a]) for a in algos)
fig.suptitle(f"Perbandingan algoritma di AWS (100 task GoCJ, 4 VM, vm3 & vm4 dibatasi CPU 50%) — rata-rata ± SD dari {n} ulangan",
             fontsize=11, fontweight="bold")
fig.text(0.5, 0.005, "Titik hitam = hasil tiap run. Heterogenitas dibuat dengan CPU quota (emulasi); semua algoritma memakai kalibrasi yang sama.",
         ha="center", fontsize=8, color="#555")
fig.tight_layout(rect=(0, 0.03, 1, 0.95))
os.makedirs(OUT, exist_ok=True)
png = os.path.join(OUT, "hasil_demo.png"); fig.savefig(png)
with open(os.path.join(OUT, "hasil_demo.csv"), "w") as fh:
    fh.write("algoritma,n,makespan_mean,makespan_sd,doi_mean,doi_sd,throughput_mean,throughput_sd\n")
    for a in algos:
        (mm, ms, _), (dm, ds, _), (tm, ts, _) = stat[a]
        fh.write(f"{a},{len(g[a])},{mm:.4f},{ms:.4f},{dm:.4f},{ds:.4f},{tm:.4f},{ts:.4f}\n")
print("Tersimpan:", png, "dan hasil_demo.csv")
