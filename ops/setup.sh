#!/usr/bin/env bash
set -euo pipefail

APP_USER="${APP_USER:-smartclock}"
APP_GROUP="${APP_GROUP:-smartclock}"
APP_HOME="${APP_HOME:-/opt/smartclock}"
SERVER_HOME="${SERVER_HOME:-$APP_HOME/server}"
ADMIN_HOME="${ADMIN_HOME:-/var/www/smartclock-admin}"
LOG_HOME="${LOG_HOME:-/var/log/smartclock}"
DB_NAME="${DB_NAME:-smartclock}"
DB_USER="${DB_USER:-smartclock}"
DB_PASSWORD="${DB_PASSWORD:-change-me}"
SSH_PORT="${SSH_PORT:-2222}"

echo "[1/8] Installing packages"
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  openjdk-17-jre-headless \
  mysql-server \
  nginx \
  fail2ban \
  certbot \
  python3-certbot-nginx

echo "[2/8] Creating application user and directories"
id "$APP_USER" >/dev/null 2>&1 || useradd --system --create-home --home-dir "$APP_HOME" --shell /usr/sbin/nologin "$APP_USER"
mkdir -p "$SERVER_HOME" "$ADMIN_HOME" "$LOG_HOME"
chown -R "$APP_USER:$APP_GROUP" "$APP_HOME" "$LOG_HOME"

echo "[3/8] Configuring MySQL timezone and local binding"
cat >/etc/mysql/mysql.conf.d/smartclock.cnf <<EOF
[mysqld]
bind-address = 127.0.0.1
default-time-zone = '+00:00'
EOF
systemctl restart mysql

echo "[4/8] Creating database and application user"
mysql <<EOF
CREATE DATABASE IF NOT EXISTS \`${DB_NAME}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${DB_NAME}\`.* TO '${DB_USER}'@'127.0.0.1';
FLUSH PRIVILEGES;
EOF

echo "[5/8] Installing systemd unit"
install -m 644 ops/systemd/smartclock-server.service /etc/systemd/system/smartclock-server.service
sed -i "s#__APP_USER__#${APP_USER}#g" /etc/systemd/system/smartclock-server.service
sed -i "s#__SERVER_HOME__#${SERVER_HOME}#g" /etc/systemd/system/smartclock-server.service
sed -i "s#__LOG_HOME__#${LOG_HOME}#g" /etc/systemd/system/smartclock-server.service
systemctl daemon-reload

echo "[6/8] Installing nginx config"
install -m 644 ops/nginx/smartclock.conf /etc/nginx/sites-available/smartclock.conf
ln -sf /etc/nginx/sites-available/smartclock.conf /etc/nginx/sites-enabled/smartclock.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl restart nginx

echo "[7/8] Installing fail2ban defaults"
cat >/etc/fail2ban/jail.d/smartclock.conf <<EOF
[sshd]
enabled = true
port = ${SSH_PORT}
EOF
systemctl enable --now fail2ban

echo "[8/8] Copy example config"
install -m 640 server/application-prod.example.yml "$SERVER_HOME/application-prod.yml"
chown "$APP_USER:$APP_GROUP" "$SERVER_HOME/application-prod.yml"

echo "Setup complete. Review:"
echo "  - $SERVER_HOME/application-prod.yml"
echo "  - /etc/nginx/sites-available/smartclock.conf"
echo "  - /etc/ssh/sshd_config (set Port ${SSH_PORT} manually if needed)"
