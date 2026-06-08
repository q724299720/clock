#!/usr/bin/env bash
set -euo pipefail

DB_NAME="${DB_NAME:-smartclock}"
DB_USER="${DB_USER:-smartclock}"
DB_PASSWORD="${DB_PASSWORD:-change-me}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/smartclock}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$BACKUP_DIR"
mysqldump --single-transaction --quick --set-gtid-purged=OFF \
  -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  | gzip > "${BACKUP_DIR}/${DB_NAME}-${STAMP}.sql.gz"

find "$BACKUP_DIR" -type f -name "${DB_NAME}-*.sql.gz" -mtime +7 -delete
echo "Backup written to ${BACKUP_DIR}/${DB_NAME}-${STAMP}.sql.gz"
