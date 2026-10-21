#!/usr/bin/env bash
set -euo pipefail

: "${MYSQL_CONTAINER:?MYSQL_CONTAINER is required}"
: "${NUS_DB_ROOT_PASSWORD:?NUS_DB_ROOT_PASSWORD is required}"
NUS_DB_NAME=${NUS_DB_NAME:-nus}
BACKUP_DIR=${BACKUP_DIR:-/srv/nus/backups}
umask 077
mkdir -p "$BACKUP_DIR"
timestamp=$(date -u +%Y%m%dT%H%M%SZ)
out="$BACKUP_DIR/db-${NUS_DB_NAME}-${timestamp}.sql.gz"
# Two backups within one second (e.g. a safety backup right after another) must not replace each other.
n=1
while [ -e "$out" ] || [ -e "$out.partial" ]; do
  out="$BACKUP_DIR/db-${NUS_DB_NAME}-${timestamp}-${n}.sql.gz"
  n=$((n + 1))
done
partial="$out.partial"

fail() {
  printf '[nus] ERROR: %s\n' "$*" >&2
  exit 1
}
cleanup() {
  status=$?
  rm -f "$partial" "$out.sha256.partial"
  if [ "$status" -ne 0 ]; then
    rm -f "$out" "$out.sha256"
  fi
}
trap cleanup EXIT

# The dump is written under a temporary name and only renamed after every check passed.
if ! MYSQL_PWD=$NUS_DB_ROOT_PASSWORD docker exec -e MYSQL_PWD "$MYSQL_CONTAINER" mysqldump --protocol=socket -uroot \
  --single-transaction --routines --events --triggers --hex-blob --databases "$NUS_DB_NAME" \
  | gzip -n > "$partial"; then
  fail "mysqldump or gzip failed; incomplete backup removed"
fi
[ -s "$partial" ] || fail 'backup is empty; incomplete backup removed'
gzip -t "$partial" || fail 'backup failed gzip integrity check; incomplete backup removed'
# mysqldump writes this trailer only after dumping every object successfully.
gzip -dc "$partial" | tail -n 1 | grep -q '^-- Dump completed' \
  || fail 'backup lacks the mysqldump completion marker; incomplete backup removed'
mv "$partial" "$out"
sha256sum "$out" > "$out.sha256.partial"
mv "$out.sha256.partial" "$out.sha256"
printf '%s\n' "$out"
