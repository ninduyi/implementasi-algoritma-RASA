#!/bin/bash
set -e

DATACENTER_ID="$1"
HOST_ID="$2"
VM_ID="$3"
SLOTS="$4"
TOKEN="$5"

if [ -z "$VM_ID" ] || [ -z "$TOKEN" ]; then
    echo "Usage: $0 <datacenter_id> <host_id> <vm_id> <slots> <token>"
    exit 1
fi

sudo mkdir -p /etc/soka
sudo tee /etc/soka/worker.env > /dev/null <<EOF
DATACENTER_ID=${DATACENTER_ID}
HOST_ID=${HOST_ID}
VM_ID=${VM_ID}
SLOTS=${SLOTS}
SOKA_WORKER_TOKEN=${TOKEN}
EOF
sudo chmod 600 /etc/soka/worker.env

sudo tee /etc/systemd/system/rasa-worker.service > /dev/null <<'EOF'
[Unit]
Description=SOKA RASA Compute Worker
After=network.target

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu
EnvironmentFile=/etc/soka/worker.env
ExecStart=/usr/bin/java -Xms32m -Xmx256m -cp /home/ubuntu/rasa-worker.jar soka.realworld.RasaWorker --datacenter-id ${DATACENTER_ID} --host-id ${HOST_ID} --vm-id ${VM_ID} --slots ${SLOTS} --bind 0.0.0.0 --port 8080
Restart=always
RestartSec=3

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now rasa-worker.service
sudo systemctl restart rasa-worker.service
sleep 1
sudo systemctl status rasa-worker.service --no-pager

