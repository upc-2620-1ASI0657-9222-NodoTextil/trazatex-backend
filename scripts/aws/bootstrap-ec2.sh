#!/usr/bin/env bash
# Prepara una EC2 Ubuntu 24.04 recién creada: swap de 4 GB, Docker con el plugin compose y git.
# Uso (una sola vez, en la EC2):  curl -fsSL <url-raw-de-este-archivo> | sudo bash   o   sudo bash bootstrap-ec2.sh
set -euo pipefail

if ! swapon --show | grep -q /swapfile; then
  fallocate -l 4G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10
  echo 'vm.swappiness=10' > /etc/sysctl.d/99-trazatex.conf
fi

if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker ubuntu || true
apt-get install -y git

docker --version
docker compose version
free -h
