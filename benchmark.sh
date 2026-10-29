#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
export SOKA_WORKER_TOKEN=x
CP=target/classes
REPS=${REPS:-3}
SIZES=${SIZES:-"100 200 300 400 500"}
ALGOS="RASA RR MIN_MIN MAX_MIN"
OUT=hasil_benchmark
rm -rf "$OUT"
mkdir -p "$OUT/log"
RAW="$OUT/raw.csv"
echo "tasks,algo,rep,makespan,throughput,dob,sched_ms,sukses,gagal" > "$RAW"

PIDS=()
cleanup() { kill "${PIDS[@]}" 2>/dev/null; }
trap cleanup EXIT

for spec in "rasa-host-1 rasa-vm1 18001" "rasa-host-1 rasa-vm2 18002" \
            "rasa-host-2 rasa-vm3 18003" "rasa-host-2 rasa-vm4 18004"; do
  set -- $spec
  java -cp $CP soka.realworld.RasaWorker --host-id $1 --vm-id $2 --port $3 --token x \
    > "$OUT/log/worker_$2.txt" 2>&1 &
  PIDS+=($!)
done
sleep 5

for N in $SIZES; do
  CAL="$OUT/calibration_${N}.properties"
  rm -f "$CAL"   # kalibrasi baru tiap ganti ukuran, lalu dipakai bersama semua algoritma
  for R in $(seq 1 "$REPS"); do
    for A in $ALGOS; do
      echo ">> N=$N rep=$R algo=$A"
      LOG="$OUT/log/${N}_${A}_${R}.txt"
      java -cp $CP soka.realworld.RasaRealController \
        --config config/workers.json \
        --dataset "dataset/GoCJ_Dataset_${N}.txt" \
        --limit "$N" --algorithm "$A" \
        --calibration-file "$CAL" \
        --output "$OUT/run_${N}_${A}_${R}" > "$LOG" 2>&1

      mk=$(awk -F': ' '/Makespan Aktual/{print $2+0}' "$LOG")
      th=$(awk -F': ' '/Throughput/{print $2+0}' "$LOG")
      db=$(awk -F': ' '/Degree of Imb/{print $2+0}' "$LOG")
      sc=$(awk -F': ' '/Waktu Scheduler/{print $2+0}' "$LOG")
      ok=$(awk -F': ' '/Task Sukses/{print $2+0}' "$LOG")
      gl=$(awk -F': ' '/Task Gagal/{print $2+0}' "$LOG")
      echo "$N,$A,$R,$mk,$th,$db,$sc,$ok,$gl" >> "$RAW"
    done
  done
done

{
  echo "tasks,algo,makespan_avg,throughput_avg,dob_avg,sched_ms_avg"
  awk -F, 'NR>1{k=$1","$2; m[k]+=$4; t[k]+=$5; d[k]+=$6; s[k]+=$7; c[k]++}
    END{for(k in c) printf "%s,%.4f,%.4f,%.4f,%.3f\n",k,m[k]/c[k],t[k]/c[k],d[k]/c[k],s[k]/c[k]}' "$RAW" \
    | sort -t, -k1,1n -k2,2
} > "$OUT/ringkasan.csv"

echo; column -s, -t "$OUT/ringkasan.csv"
echo; echo "Data mentah: $RAW | Ringkasan: $OUT/ringkasan.csv"
