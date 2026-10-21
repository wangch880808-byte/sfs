#!/usr/bin/env bash
# Checks deploy/database/backup.sh and restore.sh; run with: bash deploy/database/tests/check-backup-restore.sh
# Uses one throwaway MySQL container from a local image only (--pull=never, --network none, tmpfs) and a stub
# docker for dumps that die midway; never connects to any other database. Override the image with
# NUS_TEST_MYSQL_IMAGE (default mysql:8.4.6, the production version).
# shellcheck disable=SC2015  # "check && pass || bad": pass only prints, so bad runs only when the check fails
set -uo pipefail
ROOT=$(cd -- "$(dirname "$0")/../../.." && pwd)
BACKUP="$ROOT/deploy/database/backup.sh"
RESTORE="$ROOT/deploy/database/restore.sh"
MYSQL_IMAGE=${NUS_TEST_MYSQL_IMAGE:-mysql:8.4.6}
REAL_DOCKER=$(command -v docker)
TMP_BASE=${TMPDIR:-/tmp}
WORK=$(mktemp -d "${TMP_BASE%/}/nus-backup-check.XXXXXX")
NAME="nus-backup-check-$(openssl rand -hex 4)"
PASSWORD=$(openssl rand -hex 16)   # throwaway, passed only through the environment and never printed
failures=0
pass() { printf 'PASS %s\n' "$*"; }
bad() { printf 'FAIL %s\n' "$*"; failures=$((failures + 1)); }
mode_of() { stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"; }
db() { MYSQL_PWD=$PASSWORD docker exec -i -e MYSQL_PWD "$NAME" mysql -uroot "$@"; }
cleanup() { docker rm -f "$NAME" >/dev/null 2>&1; rm -rf "$WORK"; }
trap cleanup EXIT

MYSQL_ROOT_PASSWORD=$PASSWORD docker run -d --pull=never --name "$NAME" --network none --tmpfs /var/lib/mysql \
  -e MYSQL_ROOT_PASSWORD -e MYSQL_DATABASE=nus "$MYSQL_IMAGE" >/dev/null \
  || { printf 'cannot start %s from a local image\n' "$MYSQL_IMAGE" >&2; exit 1; }
ready=false
for _ in $(seq 1 120); do
  if docker logs "$NAME" 2>&1 | grep -q 'MySQL init process done. Ready for start up.' \
     && MYSQL_PWD=$PASSWORD docker exec -e MYSQL_PWD "$NAME" mysqladmin ping -uroot >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
[ "$ready" = true ] || { printf 'MySQL did not start\n' >&2; exit 1; }
db nus <<'SQL'
CREATE TABLE sample (id INT PRIMARY KEY, label VARCHAR(20));
INSERT INTO sample VALUES (1, '师砺'), (2, 'restore');
SQL
export MYSQL_CONTAINER="$NAME" NUS_DB_ROOT_PASSWORD="$PASSWORD" NUS_DB_NAME=nus

# Reject credentials before logging argv; even a regression must not write a password to disk.
mkdir -p "$WORK/argv"
cat > "$WORK/argv/docker" <<'EOF'
#!/bin/sh
for arg do
  case "$arg" in
    -p|-p?*|--password|--password=*|-password|-password=*|MYSQL_PWD=*|MYSQL_ROOT_PASSWORD=*|FLYWAY_PASSWORD=*)
      printf '%s\n' 'password argument rejected' >&2; exit 97 ;;
  esac
  case "$arg" in
    *"$NUS_DB_ROOT_PASSWORD"*) printf '%s\n' 'password value in argv rejected' >&2; exit 97 ;;
  esac
done
[ "${MYSQL_PWD:-}" = "$NUS_DB_ROOT_PASSWORD" ] || { echo 'MYSQL_PWD missing or incorrect' >&2; exit 97; }
case " $* " in *' -e MYSQL_PWD '*) ;; *) echo 'bare -e MYSQL_PWD missing' >&2; exit 97 ;; esac
printf '%s\n' "$*" >> "$NUS_ARGV_LOG"
exec "$NUS_REAL_DOCKER" "$@"
EOF
chmod +x "$WORK/argv/docker"
export NUS_REAL_DOCKER="$REAL_DOCKER" NUS_ARGV_LOG="$WORK/backup-argv.log"

# 1. Successful backup
out=$(PATH="$WORK/argv:$PATH" BACKUP_DIR="$WORK/ok" "$BACKUP" 2>/dev/null) && [ -f "$out" ] && [ -f "$out.sha256" ] \
  && gzip -t "$out" && gzip -dc "$out" | tail -n 1 | grep -q '^-- Dump completed' \
  && [ "$(find "$WORK/ok" -type f | wc -l | tr -d ' ')" = 2 ] && [ "$(mode_of "$out")" = 600 ] \
  && sha256sum --check --quiet "$out.sha256" \
  && pass "backup writes a verified .sql.gz (0600) plus .sha256 and prints its path" || bad 'successful backup'
[ -s "$WORK/backup-argv.log" ] && ! grep -qF "$PASSWORD" "$WORK/backup-argv.log" \
  && grep -q 'exec -e MYSQL_PWD .* mysqldump ' "$WORK/backup-argv.log" \
  && pass 'backup passes the root password only through MYSQL_PWD' || bad 'password in backup argv'

# 2-3. mysqldump fails: wrong password, unknown database
if NUS_DB_ROOT_PASSWORD=wrong BACKUP_DIR="$WORK/badpw" "$BACKUP" >/dev/null 2>"$WORK/err"; then
  bad 'wrong password must fail'
else
  [ -z "$(ls -A "$WORK/badpw")" ] && grep -q 'mysqldump or gzip failed' "$WORK/err" \
    && pass 'mysqldump failure exits non-zero and leaves no file' || bad 'mysqldump failure cleanup'
fi
if NUS_DB_NAME=missing BACKUP_DIR="$WORK/missing" "$BACKUP" >/dev/null 2>&1; then
  bad 'unknown database must fail'
else
  [ -z "$(ls -A "$WORK/missing")" ] && pass 'unknown database exits non-zero and leaves no file' || bad 'unknown db cleanup'
fi

# 4-5. Stub docker: dump dies midway (exit 2) / exits 0 but is truncated
mkdir -p "$WORK/stub"
cat > "$WORK/stub/docker" <<'EOF'
#!/bin/sh
[ "${MYSQL_PWD:-}" = "$NUS_DB_ROOT_PASSWORD" ] || exit 97
case " $* " in *' -e MYSQL_PWD '*) ;; *) exit 97 ;; esac
for arg do
  case "$arg" in
    -p|-p?*|--password|--password=*|MYSQL_PWD=*|*"$NUS_DB_ROOT_PASSWORD"*) exit 97 ;;
  esac
done
printf -- '-- MySQL dump\nCREATE TABLE t (id int);\nINSERT INTO t VALUES (1'
exit "${STUB_EXIT:-0}"
EOF
chmod +x "$WORK/stub/docker"
if PATH="$WORK/stub:$PATH" STUB_EXIT=2 BACKUP_DIR="$WORK/die" "$BACKUP" >/dev/null 2>&1; then
  bad 'dump dying midway must fail'
else
  [ -z "$(ls -A "$WORK/die")" ] && pass 'dump dying midway exits non-zero and leaves no file' || bad 'midway cleanup'
fi
if PATH="$WORK/stub:$PATH" STUB_EXIT=0 BACKUP_DIR="$WORK/trunc" "$BACKUP" >/dev/null 2>"$WORK/err"; then
  bad 'truncated dump with exit 0 must fail'
else
  [ -z "$(ls -A "$WORK/trunc")" ] && grep -q 'completion marker' "$WORK/err" \
    && pass 'dump without completion marker is rejected and removed' || bad 'truncated cleanup'
fi

# 6. Restore round trip, also from a moved backup (only the hash in .sha256 is compared)
db -e 'DROP DATABASE nus;'
if "$RESTORE" "$out" >/dev/null 2>&1 \
   && [ "$(db -Nse 'SELECT GROUP_CONCAT(label ORDER BY id) FROM nus.sample' 2>/dev/null)" = '师砺,restore' ]; then
  pass 'restore round trip recreates the dropped database'
else
  bad 'restore round trip'
fi
mkdir -p "$WORK/moved" && cp "$out" "$out.sha256" "$WORK/moved/"
"$RESTORE" "$WORK/moved/$(basename "$out")" >/dev/null 2>&1 && pass 'restore accepts a moved backup with its .sha256' \
  || bad 'moved backup restore'

# 7-11. Refusals: nothing may reach MySQL
db -e 'CREATE TABLE nus.marker (id INT);'
marker_intact() {
  db -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nus' AND table_name='marker'" 2>/dev/null \
    | grep -qx 1
}
cp "$out" "$WORK/sum.sql.gz" && printf '%064d  %s\n' 0 "$WORK/sum.sql.gz" > "$WORK/sum.sql.gz.sha256"
"$RESTORE" "$WORK/sum.sql.gz" >/dev/null 2>"$WORK/err" && bad 'checksum mismatch must fail' \
  || { grep -q 'checksum does not match' "$WORK/err" && marker_intact && pass 'checksum mismatch refused before restore' || bad 'checksum refusal'; }
gzip -dc "$out" | head -n 20 | gzip -n > "$WORK/cut.sql.gz"
"$RESTORE" "$WORK/cut.sql.gz" >/dev/null 2>"$WORK/err" && bad 'truncated dump must fail' \
  || { grep -q 'completion marker' "$WORK/err" && marker_intact && pass 'truncated dump refused before restore' || bad 'truncated refusal'; }
head -c $(( $(wc -c < "$out") - 12 )) "$out" > "$WORK/broken.sql.gz"
"$RESTORE" "$WORK/broken.sql.gz" >/dev/null 2>"$WORK/err" && bad 'corrupt gzip must fail' \
  || { grep -q 'gzip integrity' "$WORK/err" && marker_intact && pass 'corrupt gzip refused before restore' || bad 'gzip refusal'; }
NUS_DB_ROOT_PASSWORD=wrong "$RESTORE" "$out" >/dev/null 2>"$WORK/err" && bad 'restore with wrong password must fail' \
  || { grep -q 'restore failed' "$WORK/err" && pass 'mysql client failure reported with non-zero exit' || bad 'mysql failure'; }
"$RESTORE" >/dev/null 2>&1 && bad 'missing argument must fail' || pass 'missing argument rejected'

# 12. Restore replaces the whole database: newer tables go, grants stay, the password never reaches argv
db <<'SQL'
CREATE TABLE nus.created_by_newer_migration (id INT);
CREATE USER IF NOT EXISTS 'appcheck'@'%' IDENTIFIED BY 'appcheck-pw';
GRANT ALL ON nus.* TO 'appcheck'@'%';
UPDATE nus.sample SET label = 'changed' WHERE id = 1;
SQL
export NUS_ARGV_LOG="$WORK/restore-argv.log"
if PATH="$WORK/argv:$PATH" "$RESTORE" "$out" >/dev/null 2>"$WORK/err"; then
  [ "$(db -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='nus' AND table_name IN ('created_by_newer_migration','marker')")" = 0 ] \
    && [ "$(db -Nse 'SELECT GROUP_CONCAT(label ORDER BY id) FROM nus.sample')" = '师砺,restore' ] \
    && pass 'restore drops tables created after the backup and restores the rows' || bad 'drop and recreate'
  MYSQL_PWD=appcheck-pw docker exec -e MYSQL_PWD "$NAME" mysql -uappcheck -Nse 'SELECT COUNT(*) FROM nus.sample' 2>/dev/null | grep -qx 2 \
    && pass 'grants on the database survive the restore' || bad 'grants lost'
  ! grep -qF "$PASSWORD" "$WORK/restore-argv.log" && grep -q 'exec -i -e MYSQL_PWD ' "$WORK/restore-argv.log" \
    && pass 'restore passes the root password only through MYSQL_PWD' || bad 'password in restore argv'
else
  bad "restore over newer tables failed: $(cat "$WORK/err")"
fi
db -e 'CREATE TABLE nus.marker (id INT);'
"$RESTORE" --check "$out" >"$WORK/check-out" 2>&1 && grep -q 'Backup verified' "$WORK/check-out" && marker_intact \
  && pass 'restore --check validates without touching the database' || bad '--check mode'
"$RESTORE" --check "$WORK/cut.sql.gz" >/dev/null 2>&1 && bad '--check must reject a truncated dump' \
  || pass '--check rejects a truncated dump'
# shellcheck disable=SC2016  # literal backticks of the USE statement
gzip -dc "$out" | sed 's/^USE `nus`;$/USE `other`;/' | gzip -n > "$WORK/other.sql.gz"
"$RESTORE" "$WORK/other.sql.gz" >/dev/null 2>"$WORK/err" && bad 'dump of another database must fail' \
  || { grep -q 'dump of database other' "$WORK/err" && marker_intact && pass 'dump of another database refused before restore' || bad 'other db refusal'; }

# 13. Two backups within one second keep both files
first=$(BACKUP_DIR="$WORK/same" "$BACKUP" 2>/dev/null)
second=$(BACKUP_DIR="$WORK/same" "$BACKUP" 2>/dev/null)
[ -n "$first" ] && [ -n "$second" ] && [ "$first" != "$second" ] && [ -f "$first" ] && [ -f "$second" ] \
  && pass 'consecutive backups never overwrite each other' || bad "backup names: $first $second"

# 14. Interface used by upgrade.sh and nusctl: environment in, backup path on stdout
out2=$(printf '%s\n' "$PASSWORD" | env -i PATH="$PATH" BACKUP_DIR="$WORK/compat" MYSQL_CONTAINER="$NAME" \
  NUS_DB_NAME=nus bash -c 'IFS= read -r NUS_DB_ROOT_PASSWORD; export NUS_DB_ROOT_PASSWORD; exec "$1"' _ "$BACKUP" 2>/dev/null) \
  && case "$out2" in "$WORK/compat/db-nus-"*".sql.gz") true ;; *) false ;; esac \
  && pass 'upgrade.sh/nusctl invocation form unchanged' || bad 'invocation compatibility'

# 15. Site configuration, all baseline queries and Flyway use environment-only credentials.
mkdir -p "$WORK/clients"
cat > "$WORK/clients/docker" <<'EOF'
#!/bin/sh
for arg do
  case "$arg" in
    -p|-p?*|--password|--password=*|-password|-password=*|MYSQL_PWD=*|MYSQL_ROOT_PASSWORD=*|FLYWAY_PASSWORD=*)
      echo 'password argument rejected' >&2; exit 97 ;;
  esac
  case "$arg" in *"$NUS_DB_ROOT_PASSWORD"*) echo 'password value in argv rejected' >&2; exit 97 ;; esac
done
case "$1" in
  exec)
    [ "${MYSQL_PWD:-}" = "$NUS_DB_ROOT_PASSWORD" ] || exit 97
    case " $* " in *' -e MYSQL_PWD '*) ;; *) exit 97 ;; esac
    case "$*" in
      *flyway_schema_history*) echo 1 ;;
      *information_schema.tables*) echo 0 ;;
      *) cat > "$NUS_CLIENT_SQL" ;;
    esac
    ;;
  run)
    [ "${FLYWAY_PASSWORD:-}" = "$NUS_DB_PASSWORD" ] || exit 97
    case " $* " in *' -e FLYWAY_PASSWORD '*) ;; *) exit 97 ;; esac
    ;;
  *) exit 97 ;;
esac
printf '%s\n' "$*" >> "$NUS_ARGV_LOG"
EOF
chmod +x "$WORK/clients/docker"
export NUS_ARGV_LOG="$WORK/client-argv.log" NUS_CLIENT_SQL="$WORK/site.sql"
if PATH="$WORK/clients:$PATH" NUS_SITE_URL=https://site.example.invalid \
    NUS_SKYWALKING_URL=https://monitor.example.invalid "$ROOT/deploy/database/configure-site.sh" \
    && grep -q 'UPDATE infra_file_config' "$NUS_CLIENT_SQL"; then
  pass 'configure-site sends SQL with the password only through MYSQL_PWD'
else
  bad 'configure-site credentials or SQL'
fi
PATH="$WORK/clients:$PATH" "$ROOT/deploy/database/baseline-gate.sh" check-empty \
  && PATH="$WORK/clients:$PATH" "$ROOT/deploy/database/baseline-gate.sh" check-baselined \
  && [ "$(grep -c 'exec -e MYSQL_PWD ' "$NUS_ARGV_LOG")" = 5 ] \
  && pass 'all five baseline queries use MYSQL_PWD' || bad 'baseline credentials'
PATH="$WORK/clients:$PATH" NUS_DB_PASSWORD="$PASSWORD" NUS_DB_USERNAME=nus FLYWAY_NETWORK=check \
  "$ROOT/deploy/database/flyway.sh" validate >/dev/null 2>&1 \
  && grep -q 'run .* -e FLYWAY_PASSWORD ' "$NUS_ARGV_LOG" && grep -q ' validate$' "$NUS_ARGV_LOG" \
  && ! grep -qF "$PASSWORD" "$NUS_ARGV_LOG" \
  && pass 'Flyway receives FLYWAY_PASSWORD and keeps the caller command' || bad 'Flyway credentials'

# The MySQL healthcheck in compose.yaml is intentionally unchanged in V1.0.1.7: changing it would make
# `docker compose up` recreate the MySQL container during an upgrade. It runs only inside the container.

printf '%s failure(s)\n' "$failures"
[ "$failures" -eq 0 ]
