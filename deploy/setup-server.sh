#!/bin/sh
# One-time setup of a fresh Ubuntu 24.04 server for Beinema. Run as root.
# Installs Docker (from Iranian mirrors when the official ones are out of reach),
# a swap file, a firewall (SSH, HTTP, HTTPS only), log limits and /opt/beinema.
set -eu

# --- packages: Docker from Ubuntu's own archive (no download.docker.com needed) ---
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q docker.io docker-compose-v2 ufw curl ca-certificates

# Docker Hub refuses many Iranian addresses; pull through mirrors that do not
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://docker.arvancloud.ir", "https://mirror.gcr.io"],
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
EOF
systemctl enable docker
systemctl restart docker

# --- 2 GB swap, so a memory spike slows the server down instead of killing an app ---
if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
    sysctl -w vm.swappiness=10
    echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf
fi

# --- firewall: only SSH and the web ---
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

# --- the app folder ---
mkdir -p /opt/beinema/uploads /opt/beinema/backups
chmod 700 /opt/beinema

# --- old images go away a day after a deploy replaced them ---
cat > /etc/cron.daily/beinema-docker-prune <<'EOF'
#!/bin/sh
docker image prune -af --filter "until=24h" > /dev/null 2>&1
EOF
chmod 755 /etc/cron.daily/beinema-docker-prune

# --- nightly backup of the database and the uploads (deploy/backup.sh) ---
cat > /etc/cron.daily/beinema-backup <<'EOF'
#!/bin/sh
sh /opt/beinema/backup.sh > /var/log/beinema-backup.log 2>&1
EOF
chmod 755 /etc/cron.daily/beinema-backup

echo "setup done"
