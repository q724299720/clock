#!/usr/bin/env bash
set -euo pipefail

SERVER_HOME="${SERVER_HOME:-/opt/smartclock/server}"
JAR_SOURCE="${1:-server/build/libs/server.jar}"
TARGET_JAR="${SERVER_HOME}/smartclock-server.jar"
BACKUP_JAR="${SERVER_HOME}/smartclock-server.prev.jar"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:8080/actuator/health}"

if [[ ! -f "$JAR_SOURCE" ]]; then
  echo "Jar not found: $JAR_SOURCE" >&2
  exit 1
fi

systemctl stop smartclock-server
if [[ -f "$TARGET_JAR" ]]; then
  cp "$TARGET_JAR" "$BACKUP_JAR"
fi
cp "$JAR_SOURCE" "$TARGET_JAR"
chown smartclock:smartclock "$TARGET_JAR"
systemctl start smartclock-server

for _ in {1..20}; do
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    echo "Deploy succeeded"
    exit 0
  fi
  sleep 3
done

echo "Health check failed, rolling back" >&2
systemctl stop smartclock-server
if [[ -f "$BACKUP_JAR" ]]; then
  cp "$BACKUP_JAR" "$TARGET_JAR"
  chown smartclock:smartclock "$TARGET_JAR"
fi
systemctl start smartclock-server
exit 1
