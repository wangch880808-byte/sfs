#!/usr/bin/env bash
# usage: restore.sh [--check] BACKUP.sql.gz
# Replaces the whole database NUS_DB_NAME with the backup: the database is dropped and recreated first, so
# tables, views and routines created after the backup (e.g. by newer Flyway migrations) do not survive.
# Grants on the database are kept (MySQL does not revoke them on DROP DATABASE).
# --check only validates the archive and touches nothing. Stop the backend before a real restore
# (nusctl restore and nusctl rollback do that).
set -euo pipefail

check_only=false
if [ "${1:-}" = --check ]; then
  check_only=true
  shift
fi
: "${MYSQL_CONTAINER:?MYSQL_CONTAINER is required}"
: "${NUS_DB_ROOT_PASSWORD:?NUS_DB_ROOT_PASSWORD is required}"
: "${1:?usage: restore.sh [--check] BACKUP.sql.gz}"
backup=$1
NUS_DB_NAME=${NUS_DB_NAME:-nus}

fail() {
  printf '[nus] ERROR: %s\n' "$*" >&2
  exit 1
}
# The password reaches mysql only through MYSQL_PWD, never through argv.
mysql_root() {
  MYSQL_PWD=$NUS_DB_ROOT_PASSWORD docker exec -i -e MYSQL_PWD "$MYSQL_CONTAINER" \
    mysql --protocol=socket -uroot "$@"
}

case "$NUS_DB_NAME" in
  ''|*[!A-Za-z0-9_]*) fail "unsupported database name: $NUS_DB_NAME" ;;
esac

# Validate the whole archive before anything reaches MySQL.
[ -f "$backup" ] || fail "backup file not found: $backup"
[ -s "$backup" ] || fail "backup file is empty: $backup"
gzip -t "$backup" || fail "backup failed gzip integrity check: $backup"
if [ -f "$backup.sha256" ]; then
  expected=$(awk 'NR == 1 { print $1 }' "$backup.sha256")
  actual=$(sha256sum "$backup" | awk '{ print $1 }')
  [ -n "$expected" ] && [ "$expected" = "$actual" ] || fail "backup checksum does not match $backup.sha256"
fi
# One pass over the dump: its last line (mysqldump's completion marker), the first USE and whether it
# creates the database itself (mysqldump --databases, as backup.sh writes it).
summary=$(gzip -dc "$backup" | awk '
  !use && /^USE `/ { use = $0; sub(/^USE `/, "", use); sub(/`;$/, "", use) }
  !create && /^CREATE DATABASE / { create = 1 }
  { last = $0 }
  END { printf "%s|%s|%s\n", (last ~ /^-- Dump completed/ ? "complete" : "truncated"), use, (create ? "create" : "") }
')
# "|" is not whitespace, so empty fields keep their position.
IFS='|' read -r completeness dump_db dump_creates <<EOF
$summary
EOF
[ "$completeness" = complete ] \
  || fail "backup lacks the mysqldump completion marker (truncated dump): $backup"
if [ -n "$dump_db" ] && [ "$dump_db" != "$NUS_DB_NAME" ]; then
  fail "backup is a dump of database $dump_db, not $NUS_DB_NAME: $backup"
fi
if [ "$check_only" = true ]; then
  printf '[nus] Backup verified (gzip, checksum, completion marker): %s\n' "$backup"
  exit 0
fi

mysql_root -e "DROP DATABASE IF EXISTS \`$NUS_DB_NAME\`;" \
  || fail "restore failed: could not drop database $NUS_DB_NAME; nothing was imported"
if [ -n "$dump_creates" ]; then
  # The dump recreates the database with the character set and collation it had when it was taken.
  gzip -dc "$backup" | mysql_root \
    || fail 'restore failed; the database may be partially restored, restore a verified backup before restarting'
else
  mysql_root -e "CREATE DATABASE \`$NUS_DB_NAME\`;" \
    || fail "restore failed: could not create database $NUS_DB_NAME; nothing was imported"
  gzip -dc "$backup" | mysql_root "$NUS_DB_NAME" \
    || fail 'restore failed; the database may be partially restored, restore a verified backup before restarting'
fi
mysql_root -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${NUS_DB_NAME}';" \
  | awk '$1 > 0 { ok=1 } END { exit(ok ? 0 : 1) }' \
  || fail "database ${NUS_DB_NAME} has no tables after restore"
printf '[nus] Database %s replaced with %s\n' "$NUS_DB_NAME" "$backup"
