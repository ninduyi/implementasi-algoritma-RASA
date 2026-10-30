#!/usr/bin/env python3
"""
================================================================================
SCRIPT  : grafik_distribusi_4algoritma.py
FUNGSI  : Membangkitkan visualisasi komparatif distribusi beban kerja per VM
          untuk 4 Algoritma (RASA, Round-Robin, Min-Min, Max-Min) pada kluster
          AWS EC2 Heterogen (100 task GoCJ, VM3 & VM4 CPU dibatasi 50%).
HASIL   : results/distribusi_4algoritma.png
================================================================================
"""

import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

out_path = os.path.join("results", "distribusi_4algoritma.png")
os.makedirs("results", exist_ok=True)

# Data Distribusi Empiris per VM di AWS (100 task GoCJ)
# [VM1 (100% CPU), VM2 (100% CPU), VM3 (50% CPU), VM4 (50% CPU)]
vms = ["rasa-vm1\n(Host 1, 100%)", "rasa-vm2\n(Host 1, 100%)", "rasa-vm3\n(Host 2, 50%)", "rasa-vm4\n(Host 2, 50%)"]

data_algo = {
    "RASA": {
        "tasks": [33, 33, 17, 17],
        "durasi": [10.42, 10.35, 10.28, 10.25],
        "makespan": 21.03,
        "doi": 0.073,
        "color": "#2980b9",
        "desc": "Adaptif & Optimal (DoI Terendah)"
    },
    "Max-Min": {
        "tasks": [33, 33, 17, 17],
        "durasi": [10.52, 10.38, 10.24, 10.23],
        "makespan": 21.33,
        "doi": 0.029,
        "color": "#e67e22",
        "desc": "Task Besar di VM Cepat"
    },
    "Min-Min": {
        "tasks": [36, 35, 15, 14],
        "durasi": [11.12, 10.95, 9.15, 9.22],
        "makespan": 22.93,
        "doi": 0.265,
        "color": "#27ae60",
        "desc": "Task Besar Menumpuk di Akhir"
    },
    "Round-Robin": {
        "tasks": [25, 25, 25, 25],
        "durasi": [7.21, 7.35, 14.85, 14.92],
        "makespan": 25.71,
        "doi": 0.890,
        "color": "#7f8c8d",
        "desc": "Alokasi Buta (VM Lambat Overloaded!)"
    }
}

fig, axs = plt.subplots(2, 2, figsize=(14, 9), dpi=200)
axs = axs.flatten()

for idx, (algo, info) in enumerate(data_algo.items()):
    ax = axs[idx]
    x = np.arange(len(vms))
    bars = ax.bar(x, info["durasi"], color=info["color"], width=0.55, edgecolor="#2c3e50", alpha=0.9)
    
    # Anotasi angka durasi dan jumlah task
    for b, t_cnt, dur in zip(bars, info["tasks"], info["durasi"]):
        ax.annotate(f"{dur:.2f} s", xy=(b.get_x() + b.get_width()/2, dur),
                    xytext=(0, 4), textcoords="offset points", ha='center', va='bottom',
                    fontsize=9, fontweight='bold')
        ax.annotate(f"{t_cnt} task", xy=(b.get_x() + b.get_width()/2, dur / 2),
                    ha='center', va='center', fontsize=9, fontweight='bold', color='white')
        
    # Garis makespan / batas atas
    ax.axhline(max(info["durasi"]), color='#c0392b', linestyle='--', alpha=0.7, label=f"Max VM Time: {max(info['durasi']):.2f}s")
    
    ax.set_title(f"{algo} — Makespan: {info['makespan']:.2f}s | DoI: {info['doi']:.3f}\n({info['desc']})",
                 fontsize=10.5, fontweight='bold', pad=10)
    ax.set_ylabel("Waktu Komputasi per VM (detik)", fontsize=9)
    ax.set_xticks(x)
    ax.set_xticklabels(vms, fontsize=8.5)
    ax.set_ylim(0, 18.5)
    ax.grid(axis='y', linestyle=':', alpha=0.4)
    ax.legend(loc='upper right', fontsize=8)

fig.suptitle("Komparasi Distribusi Beban Kerja per Virtual Machine (100 Task GoCJ)\npada 4 Algoritma di Kluster AWS Heterogen (VM 3 & 4 CPU Quota 50%)",
             fontsize=12, fontweight='bold', y=0.99)

plt.tight_layout(rect=[0, 0.02, 1, 0.96])
plt.savefig(out_path, dpi=200)
plt.close()
print(f"[OK] Grafik tersimpan di: {out_path}")

