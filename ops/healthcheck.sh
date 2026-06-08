#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-smartclock-server}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"
DB_NAME="${DB_NAME:-smartclock}"
DB_USER="${DB_USER:-smartclock}"
DB_PASSWORD="${DB_PASSWORD:-change-me}"

systemctl is-active --quiet "$SERVICE_NAME"
curl -fsS "$HEALTH_URL" >/dev/null
mysql -u"$DB_USER" -p"$DB_PASSWORD" -e "USE \`${DB_NAME}\`; SELECT 1;" >/dev/null
echo "Health check passed"
