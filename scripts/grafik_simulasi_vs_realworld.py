#!/usr/bin/env python3
"""
================================================================================
SCRIPT  : grafik_simulasi_vs_realworld.py
FUNGSI  : Membangkitkan grafik perbandingan komparatif logis antara
          Simulasi (CloudSim) dan Real-World (AWS EC2 Multi-Host)
          untuk 4 Algoritma (RASA, Round-Robin, Min-Min, Max-Min)
          mencakup 3 dimensi evaluasi komparatif.
HASIL   : results/simulasi_vs_realworld.png
================================================================================
"""

import os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

out_path = os.path.join("results", "simulasi_vs_realworld.png")
os.makedirs("results", exist_ok=True)

algoritma = ["RASA", "Max-Min", "Min-Min", "Round-Robin"]

# 1. Degree of Imbalance (DoI) pada Heterogenitas (Kapasitas 2:1)
doi_simulasi = [0.025, 0.003, 0.220, 1.168]
doi_realworld = [0.073, 0.063, 0.265, 0.890]

# 2. Persentase Reduksi Makespan terhadap Baseline Round-Robin (%)
reduksi_simulasi = [48.5, 48.8, 45.3, 0.0]   # CloudSim
reduksi_realworld = [18.2, 13.5, 10.8, 0.0]  # AWS Real-World

# 3. Konsistensi Ranking Waktu Eksekusi Relatif (Normalized Makespan vs RASA = 1.0)
norm_simulasi = [1.00, 0.99, 1.06, 1.94]
norm_realworld = [1.00, 1.06, 1.09, 1.22]

fig, (ax1, ax2, ax3) = plt.subplots(1, 3, figsize=(17, 5.2), dpi=200)

x = np.arange(len(algoritma))
width = 0.35

# Subplot 1: Degree of Imbalance (DoI)
rects1 = ax1.bar(x - width/2, doi_simulasi, width, label='Simulasi (CloudSim)', color='#3498db', edgecolor='#2980b9')
rects2 = ax1.bar(x + width/2, doi_realworld, width, label='Real-World (AWS EC2)', color='#e67e22', edgecolor='#d35400')

ax1.set_ylabel('Degree of Imbalance (DoI)\n(Makin kecil makin seimbang)', fontsize=9.5, fontweight='bold')
ax1.set_title('1. Keseimbangan Beban (DoI)\n(Rasio Heterogenitas VM 2:1)', fontsize=10.5, fontweight='bold')
ax1.set_xticks(x)
ax1.set_xticklabels(algoritma, fontsize=9, fontweight='bold')
ax1.legend(fontsize=8.5)
ax1.grid(axis='y', linestyle='--', alpha=0.4)

for r in rects1:
    h = r.get_height()
    ax1.annotate(f'{h:.3f}', xy=(r.get_x() + r.get_width() / 2, h),
                 xytext=(0, 3), textcoords="offset points", ha='center', va='bottom', fontsize=8, fontweight='bold')
for r in rects2:
    h = r.get_height()
    ax1.annotate(f'{h:.3f}', xy=(r.get_x() + r.get_width() / 2, h),
                 xytext=(0, 3), textcoords="offset points", ha='center', va='bottom', fontsize=8, fontweight='bold')

# Subplot 2: Reduksi Makespan Relatif terhadap Round-Robin
rects3 = ax2.bar(x - width/2, reduksi_simulasi, width, label='Simulasi (CloudSim)', color='#2ecc71', edgecolor='#27ae60')
rects4 = ax2.bar(x + width/2, reduksi_realworld, width, label='Real-World (AWS EC2)', color='#9b59b6', edgecolor='#8e44ad')

ax2.set_ylabel('Penghematan Makespan vs RR (%)\n(Makin tinggi makin efisien)', fontsize=9.5, fontweight='bold')
ax2.set_title('2. Efisiensi Penghematan Waktu vs RR\n(Dampak Physical Reality Tax)', fontsize=10.5, fontweight='bold')
ax2.set_xticks(x)
ax2.set_xticklabels(algoritma, fontsize=9, fontweight='bold')
ax2.legend(fontsize=8.5)
ax2.grid(axis='y', linestyle='--', alpha=0.4)

for r in rects3:
    h = r.get_height()
    lbl = f'+{h:.1f}%' if h > 0 else 'Baseline'
    ax2.annotate(lbl, xy=(r.get_x() + r.get_width() / 2, h),
                 xytext=(0, 3), textcoords="offset points", ha='center', va='bottom', fontsize=8, fontweight='bold' if h > 0 else 'normal')

for r in rects4:
    h = r.get_height()
    lbl = f'+{h:.1f}%' if h > 0 else 'Baseline'
    ax2.annotate(lbl, xy=(r.get_x() + r.get_width() / 2, h),
                 xytext=(0, 3), textcoords="offset points", ha='center', va='bottom', fontsize=8, fontweight='bold' if h > 0 else 'normal')

# Subplot 3: Konsistensi Perilaku Relatif
rects5 = ax3.bar(x - width/2, norm_simulasi, width, label='Simulasi (CloudSim)', color='#34495e', edgecolor='#1a252f')
rects6 = ax3.bar(x + width/2, norm_realworld, width, label='Real-World (AWS EC2)', color='#16a085', edgecolor='#0e6655')

ax3.set_ylabel('Rasio Makespan Relatif (RASA = 1.0)\n(Makin mendekati 1.0 makin setara RASA)', fontsize=9.5, fontweight='bold')
ax3.set_title('3. Konsistensi Ranking Algoritma\n(Perilaku Relatif Antar-Lingkungan)', fontsize=10.5, fontweight='bold')
ax3.set_xticks(x)
ax3.set_xticklabels(algoritma, fontsize=9, fontweight='bold')
ax3.legend(fontsize=8.5)
ax3.grid(axis='y', linestyle='--', alpha=0.4)

for r in rects5:
    h = r.get_height()
    ax3.annotate(f'{h:.2f}x', xy=(r.get_x() + r.get_width() / 2, h),
                 xytext=(0, 3), textcoords="offset points", ha='center', va='bottom', fontsize=8, fontweight='bold')

for r in rects6:
    h = r.get_height()
    ax3.annotate(f'{h:.2f}x', xy=(r.get_x() + r.get_width() / 2, h),
                 xytext=(0, 3), textcoords="offset points", ha='center', va='bottom', fontsize=8, fontweight='bold')

fig.suptitle('Evaluasi Komparatif Komprehensif: Simulasi CloudSim vs Real-World AWS EC2 pada 4 Algoritma\n(Infrastruktur Heterogen Rasio 2:1, Workload Nyata Google Cloud Jobs)', fontsize=12, fontweight='bold', y=0.98)
fig.text(0.5, 0.01, '*Analisis Ilmiah: Perbedaan penghematan relatif disebabkan oleh latensi jaringan TCP/HTTP riil pada AWS yang tidak ada di simulator.',
         ha='center', fontsize=8.5, style='italic', color='#555')

plt.tight_layout(rect=[0, 0.03, 1, 0.94])
plt.savefig(out_path, dpi=200)
plt.close()
print(f"[OK] Grafik tersimpan di: {out_path}")

