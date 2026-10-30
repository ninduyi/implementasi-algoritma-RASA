#!/usr/bin/env python3
"""Grafik perbandingan RASA vs RR, Min-Min, Max-Min dari hasil benchmark.sh.

Membaca raw.csv (semua ulangan) dan menggambar rata-rata dengan error bar
(simpangan baku antar ulangan).

Pakai:
    python3 scripts/grafik_benchmark.py [hasil_benchmark/raw.csv] [folder_output]
"""
import os
import sys

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

raw_path = sys.argv[1] if len(sys.argv) > 1 else "hasil_benchmark/raw.csv"
out_dir = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(raw_path)), "grafik")
os.makedirs(out_dir, exist_ok=True)

raw = pd.read_csv(raw_path)
raw = raw[(raw["gagal"] == 0) & (raw["makespan"] > 0)]  # buang run gagal / nol
reps = int(raw.groupby(["tasks", "algo"]).size().min())

WARNA = {"RASA": "#c0392b", "MIN_MIN": "#2980b9", "MAX_MIN": "#27ae60", "RR": "#7f8c8d"}
NAMA = {"RASA": "RASA", "MIN_MIN": "Min-Min", "MAX_MIN": "Max-Min", "RR": "Round-Robin"}
URUTAN = ["RASA", "MIN_MIN", "MAX_MIN", "RR"]
METRIK = [
    ("makespan", "Makespan (detik)", "Makespan (makin kecil makin baik)"),
    ("throughput", "Throughput (task/detik)", "Throughput (makin besar makin baik)"),
    ("dob", "Degree of Imbalance", "Degree of Imbalance (makin kecil makin seimbang)"),
    ("sched_ms", "Waktu scheduling (ms)", "Overhead waktu scheduling"),
]
sizes = sorted(raw["tasks"].unique())
lebar = 6  # offset kecil antar algoritma supaya error bar tidak menumpuk

ringkas = []
for kolom, label, judul in METRIK:
    plt.figure(figsize=(8, 4.8))
    for i, algo in enumerate(URUTAN):
        g = raw[raw["algo"] == algo].groupby("tasks")[kolom].agg(["mean", "std"]).reindex(sizes)
        x = [s + (i - 1.5) * lebar for s in sizes]
        plt.errorbar(x, g["mean"], yerr=g["std"].fillna(0), marker="o", capsize=3,
                     linewidth=2.6 if algo == "RASA" else 1.6, color=WARNA[algo], label=NAMA[algo])
        for s, (m, sd) in zip(sizes, zip(g["mean"], g["std"])):
            ringkas.append({"metrik": kolom, "tasks": s, "algo": algo, "mean": m, "std": sd})
    plt.xlabel("Jumlah task")
    plt.ylabel(label)
    plt.title(f"{judul}\nrata-rata ± simpangan baku, {reps} ulangan", fontsize=10.5, fontweight="bold")
    plt.xticks(sizes)
    plt.grid(alpha=0.3)
    plt.legend()
    plt.tight_layout()
    path = os.path.join(out_dir, f"benchmark_{kolom}.png")
    plt.savefig(path, dpi=200)
    plt.close()
    print("Tersimpan:", path)

pd.DataFrame(ringkas).round(4).to_csv(os.path.join(out_dir, "ringkasan_mean_std.csv"), index=False)
print("Tersimpan:", os.path.join(out_dir, "ringkasan_mean_std.csv"), f"(ulangan minimum per titik: {reps})")
