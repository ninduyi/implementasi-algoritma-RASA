#!/bin/bash
# Usage: [ITER=50] ./demo.sh key.pem HOST1_IP HOST2_IP [TASKS] [ALGO]
set -e
KEY="$1"; H1="$2"; H2="$3"; TASKS="${4:-100}"; ALGO="${5:-RASA}"
[ -z "$H2" ] && { echo "Usage: $0 key.pem host1_ip host2_ip [tasks] [algo]"; exit 1; }
chmod 400 "$KEY"
cd "$(dirname "$0")"
ITER="${ITER:-1.0}"

mkdir -p target/classes
javac -d target/classes \
  src/soka/simulasi/RasaScheduler.java \
  src/soka/simulasi/GoCjDatasetGenerator.java \
  src/soka/realworld/*.java

SSH_OPTS="-i $KEY -N -o ExitOnForwardFailure=yes -o StrictHostKeyChecking=no -o ConnectTimeout=10"
ssh $SSH_OPTS -L 127.0.0.1:18001:192.168.122.156:8080 -L 127.0.0.1:18002:192.168.122.212:8080 ubuntu@$H1 & T1=$!
ssh $SSH_OPTS -L 127.0.0.1:18003:192.168.122.70:8080  -L 127.0.0.1:18004:192.168.122.87:8080  ubuntu@$H2 & T2=$!
trap 'kill $T1 $T2 2>/dev/null' EXIT
# tunggu sampai keempat port tunnel benar-benar listen (maks 30 detik per port)
for P in 18001 18002 18003 18004; do
  for _ in $(seq 1 30); do nc -z 127.0.0.1 $P 2>/dev/null && break; sleep 1; done
done

OUT="results/runs/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"
java -cp target/classes soka.realworld.RasaRealController \
  --config config/workers.json \
  --dataset "dataset/GoCJ_Dataset_${TASKS}.txt" \
  --limit "$TASKS" --iterations-per-mi "$ITER" \
  --algorithm "$ALGO" --output "$OUT" ${CALIB:+--calibration-file "$CALIB"}

python3 scripts/visualize.py --results-dir "$OUT"
echo "Dashboard: $OUT/dashboard.html"
open "$OUT/dashboard.html" 2>/dev/null || true
