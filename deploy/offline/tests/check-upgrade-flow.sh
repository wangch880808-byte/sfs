#!/usr/bin/env bash
# End-to-end dry run of deploy/offline/upgrade.sh and nusctl restore/rollback against a fake docker; run with:
#   bash deploy/offline/tests/check-upgrade-flow.sh
# Nothing touches a real container or /srv: a stub docker on PATH keeps containers, images, the "database"
# (one line: its schema) and an ordered event log in a temporary directory, and fails on request (FAIL_AT,
# FAIL_HEALTH_IMAGE, FAIL_IMPORT). The real backup.sh and restore.sh run on the stub's dump.
# Only require_root, require_ubuntu and prepare_observability_host are replaced (appended to the package's
# common.sh). Scripts run under dash when available (Ubuntu's /bin/sh); override with NUS_TEST_SH.
# shellcheck disable=SC2015  # "check && pass || bad": pass only prints, so bad runs only when the check fails
# shellcheck disable=SC2016  # single-quoted stub code is expanded by the stub
# shellcheck disable=SC2094  # SHA256SUMS is excluded from the files it hashes
set -uo pipefail
ROOT=$(cd -- "$(dirname "$0")/../../.." && pwd)
TEST_SH=${NUS_TEST_SH:-$(command -v dash || command -v sh)}
TMP_BASE=${TMPDIR:-/tmp}
WORK=$(mktemp -d "${TMP_BASE%/}/nus-upgrade-flow-check.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
failures=0
pass() { printf 'PASS %s\n' "$*"; }
bad() { printf 'FAIL %s\n' "$*"; failures=$((failures + 1)); }
OLD=V9.9.8
NEW=V9.9.9
ROOT_PW=$(openssl rand -hex 16)   # throwaway secrets: must never show up in any output or log
REDIS_PW=$(openssl rand -hex 16)
APP_PW=$(openssl rand -hex 16)

# --- stub docker ---------------------------------------------------------------------------------------------
mkdir -p "$WORK/fakebin"
cat > "$WORK/fakebin/docker" <<'STUB'
#!/bin/sh
S=$NUS_FAKE_STATE
reject_password() {
  echo 'password argument or environment rejected' >&2
  echo rejected >> "$NUS_FAKE_CHECK_DIR/password-violations"
  exit 97
}
# Check before recording argv, so a regression never persists the credential value.
for arg do
  case "$arg" in
    -p|-p?*|--password|--password=*|-password|-password=*|MYSQL_PWD=*|MYSQL_ROOT_PASSWORD=*|FLYWAY_PASSWORD=*) reject_password ;;
  esac
  for secret in "${NUS_DB_ROOT_PASSWORD:-}" "${NUS_DB_PASSWORD:-}" "${NUS_REDIS_PASSWORD:-}"; do
    [ -n "$secret" ] || continue
    case "$arg" in *"$secret"*) reject_password ;; esac
  done
done
check_mysql_env() {
  [ "$mysql_pwd_forwarded" = true ] || reject_password
  case " $* " in
    *' -uroot '*) expected_pwd=$NUS_DB_ROOT_PASSWORD ;;
    *) expected_pwd=$NUS_DB_PASSWORD ;;
  esac
  [ -n "$expected_pwd" ] && [ "${MYSQL_PWD:-}" = "$expected_pwd" ] || reject_password
}
printf '%s\n' "$*" >> "$S/calls"
ev() { printf '%s\n' "$*" >> "$S/events"; }
fail_if() { if [ "${FAIL_AT:-}" = "$1" ]; then printf 'injected failure: %s\n' "$1" >&2; exit 1; fi; }
env_backend() { sed -n 's/^BACKEND_IMAGE=//p' "$NUS_FAKE_CONFIG/.env"; }
running() { [ -f "$S/ctr/$1" ] && grep -q '^running=1$' "$S/ctr/$1"; }
image_of() { sed -n 's/^image=//p' "$S/ctr/$1"; }
key_of() {
  case "$1" in
    backend) echo BACKEND_IMAGE ;; frontend) echo FRONTEND_IMAGE ;; mysql) echo MYSQL_IMAGE ;; redis) echo REDIS_IMAGE ;;
    elasticsearch) echo ELASTICSEARCH_IMAGE ;; skywalking-oap) echo SKYWALKING_OAP_IMAGE ;; skywalking-ui) echo SKYWALKING_UI_IMAGE ;;
  esac
}
cmd=$1; shift
case "$cmd" in
  compose)
    envfile=; files=
    while [ "$#" -gt 0 ]; do
      case "$1" in
        --project-name) shift 2 ;;
        --env-file) envfile=$2; shift 2 ;;
        -f) files="$files $2"; shift 2 ;;
        *) break ;;
      esac
    done
    sub=$1; shift
    case "$sub" in
      ps)
        if [ "${1:-}" = -q ]; then running "$2" && echo "cid-$2"; exit 0; fi
        for f in "$S"/ctr/*; do
          svc=${f##*/}
          running "$svc" || continue
          if [ -n "${FAIL_HEALTH_IMAGE:-}" ] && [ "$(image_of "$svc")" = "$FAIL_HEALTH_IMAGE" ]; then
            echo "$svc=starting"
          else
            echo "$svc=healthy"
          fi
        done
        ;;
      stop) for svc in "$@"; do sed -i.bak 's/^running=1$/running=0/' "$S/ctr/$svc"; done; ev "stop $*" ;;
      up)
        for svc in "$@"; do
          case "$svc" in -*) continue ;; esac
          key=$(key_of "$svc")
          img=$(printenv "$key" || sed -n "s/^$key=//p" "$envfile")
          grep -qxF "$img" "$S/images" || { echo "no such image: $img" >&2; exit 1; }
          printf 'image=%s\nrunning=1\n' "$img" > "$S/ctr/$svc"
          ev "up $svc $img compose=$files"
        done
        ;;
      exec)
        mysql_pwd_forwarded=false
        while :; do
          case "$1" in
            -T|-i|-t) shift ;;
            -e) [ "$2" != MYSQL_PWD ] || mysql_pwd_forwarded=true; shift 2 ;;
            *) break ;;
          esac
        done
        svc=$1; prog=$2; shift 2
        if [ "$svc $prog" = 'mysql mysql' ]; then
          check_mysql_env "$@"
          ev 'compose mysql'
          echo 17
        fi
        ;;
      logs) echo 'fake container log line' ;;
      *) ;;
    esac
    ;;
  inspect) image_of "${3#cid-}" ;;
  image) [ "$1" = inspect ] && grep -qxF "$2" "$S/images" ;;
  build)
    fail_if build
    while [ "$#" -gt 0 ]; do [ "$1" = -t ] && { echo "$2" >> "$S/images"; ev "build $2"; }; shift; done
    ;;
  load) ;;
  info) echo "$S/dockerroot" ;;
  run)
    [ "${FLYWAY_PASSWORD:-}" = "$NUS_DB_PASSWORD" ] || reject_password
    case " $* " in *' -e FLYWAY_PASSWORD '*) ;; *) reject_password ;; esac
    for a in "$@"; do
      case "$a" in
        info) ev 'flyway info'; echo 'Schema version: old' ;;
        validate) fail_if validate; ev 'flyway validate' ;;
        migrate)
          ev "flyway migrate env_backend=$(env_backend)"
          if [ "${FAIL_AT:-}" = migrate ]; then echo partial > "$S/db"; echo 'injected failure: migrate' >&2; exit 1; fi
          echo new > "$S/db"
          ;;
      esac
    done
    ;;
  exec)
    mysql_pwd_forwarded=false
    while :; do
      case "$1" in
        -i|-t|-T) shift ;;
        -e) [ "$2" != MYSQL_PWD ] || mysql_pwd_forwarded=true; shift 2 ;;
        *) break ;;
      esac
    done
    cid=$1; prog=$2; shift 2
    case "$prog" in
      mysqldump)
        check_mysql_env "$@"
        fail_if backup
        backend_running=0; running backend && backend_running=1
        ev "dump env_backend=$(env_backend) backend_running=$backend_running db=$(cat "$S/db")"
        printf -- '-- MySQL dump\nCREATE DATABASE /*!32312 IF NOT EXISTS*/ `nus`;\nUSE `nus`;\n-- schema=%s\n-- Dump completed on now\n' "$(cat "$S/db")"
        ;;
      mysql)
        check_mysql_env "$@"
        sql=
        while [ "$#" -gt 0 ]; do case "$1" in -e|-Nse) sql=$2; shift ;; esac; shift; done
        case "$sql" in
          *SUM\(data_length*) echo 1048576 ;;
          *'DROP DATABASE'*) ev 'drop'; echo dropped > "$S/db" ;;
          *'CREATE DATABASE'*) ev 'create' ;;
          *'information_schema.tables'*) echo 42 ;;
          '')
            input=$(cat)
            case "$input" in
              *infra_file_config*) fail_if configure; ev 'configure-site' ;;
              *'-- schema='*)
                schema=$(printf '%s\n' "$input" | sed -n 's/^-- schema=//p')
                if [ -n "${FAIL_IMPORT:-}" ]; then echo 'injected failure: import' >&2; exit 1; fi
                echo "$schema" > "$S/db"; ev "import schema=$schema"
                ;;
            esac
            ;;
        esac
        ;;
      redis-cli)
        [ -n "${REDISCLI_AUTH:-}" ] || { echo NOAUTH; exit 0; }
        case "$*" in *oauth_client*) ev 'redis restored' ;; *) ev 'redis permissions' ;; esac
        echo 3
        ;;
    esac
    ;;
esac
exit 0
STUB
chmod +x "$WORK/fakebin/docker"
# doctor reads host information; keep these checks independent of the test host.
for cmd in free sysctl; do
  printf '#!/bin/sh\nexit 0\n' > "$WORK/fakebin/$cmd"
  chmod +x "$WORK/fakebin/$cmd"
done

# --- package and installed state -----------------------------------------------------------------------------
overrides() {  # appended to common.sh copies: no root, no Ubuntu, no sysctl on the test machine
  printf '\n%s\n' 'require_root() { :; }' 'require_ubuntu() { :; }' 'prepare_observability_host() { :; }'
}
make_release_tree() {  # dir version kind
  mkdir -p "$1/lib" "$1/runtime" "$1/database/migrations" "$1/artifacts/backend" "$1/artifacts/frontend/dist"
  cp "$ROOT/deploy/offline/upgrade.sh" "$ROOT/deploy/offline/nusctl" "$1/"
  cp "$ROOT"/deploy/database/*.sh "$1/lib/"
  { cat "$ROOT/deploy/offline/lib/common.sh"; overrides; } > "$1/lib/common.sh"
  cp "$ROOT/deploy/compose.yaml" "$1/runtime/compose.yaml"
  printf -- '-- test\n' > "$1/database/migrations/V1__test.sql"
  printf 'RELEASE_VERSION=%s\nTARGET_OS=ubuntu24.04\nTARGET_ARCH=amd64\nRELEASE_KIND=application-update\nBACKEND_IMAGE=nus-backend:%s\nFRONTEND_IMAGE=nus-front:%s\n' \
    "$2" "$2" "$2" > "$1/release.env"
  echo readme > "$1/README.txt"
  echo 'FROM scratch' > "$1/artifacts/backend/Dockerfile"
  echo jar > "$1/artifacts/backend/nus-server.jar"
  echo 'FROM scratch' > "$1/artifacts/frontend/Dockerfile"
  echo '<html></html>' > "$1/artifacts/frontend/dist/index.html"
  (cd "$1" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
}
setup() {  # fresh installed $OLD plus package $NEW (or the version given)
  rm -rf "$WORK/srv" "$WORK/pkg" "$WORK/state" "$WORK/link"
  D="$WORK/srv"; C="$D/config"; S="$WORK/state"; P="$WORK/pkg"
  mkdir -p "$C" "$D/releases" "$D/backups" "$S/ctr" "$S/dockerroot" "$WORK/link"
  make_release_tree "$D/releases/$OLD" "$OLD"
  ln -s "releases/$OLD" "$D/current"
  make_release_tree "$P" "${1:-$NEW}"
  cat > "$C/.env" <<EOF
DATA_ROOT=$D
CONFIG_ROOT=$C
COMPOSE_PROJECT_NAME=nus
BACKEND_IMAGE=nus-backend:$OLD
FRONTEND_IMAGE=nus-front:$OLD
MYSQL_IMAGE=mysql:8.4.6
REDIS_IMAGE=redis:7.4.6-alpine
FLYWAY_IMAGE=flyway/flyway:10.20.1-alpine
ELASTICSEARCH_IMAGE=elasticsearch:8.17.3
SKYWALKING_OAP_IMAGE=skywalking-oap:11.0.0
SKYWALKING_UI_IMAGE=horizon-ui:1.0.0
NUS_DB_NAME=nus
NUS_DB_USERNAME=nus
NUS_DB_PASSWORD=$APP_PW
NUS_DB_ROOT_PASSWORD=$ROOT_PW
NUS_REDIS_PASSWORD=$REDIS_PW
NUS_MONITOR_USERNAME=monitor
NUS_MONITOR_PASSWORD=$APP_PW
NUS_MONITOR_COOKIE_SECURE=false
NUS_SKYWALKING_UI_PORT=8082
NUS_SKYWALKING_URL=http://127.0.0.1:8082/
NUS_HORIZON_AUTH_LOCAL_USERS='[]'
NUS_MYBATIS_ENCRYPTOR_PASSWORD=$APP_PW
NUS_API_ENCRYPT_REQUEST_KEY=$APP_PW
NUS_API_ENCRYPT_RESPONSE_KEY=$APP_PW
NUS_SITE_URL=http://127.0.0.1
EOF
  chmod 600 "$C/.env"
  cp "$C/.env" "$WORK/env.original"
  printf '%s\n' "nus-backend:$OLD" "nus-front:$OLD" mysql:8.4.6 redis:7.4.6-alpine elasticsearch:8.17.3 \
    skywalking-oap:11.0.0 horizon-ui:1.0.0 > "$S/images"
  for pair in "mysql=mysql:8.4.6" "redis=redis:7.4.6-alpine" "elasticsearch=elasticsearch:8.17.3" \
    "skywalking-oap=skywalking-oap:11.0.0" "skywalking-ui=horizon-ui:1.0.0" "backend=nus-backend:$OLD" "frontend=nus-front:$OLD"; do
    printf 'image=%s\nrunning=1\n' "${pair#*=}" > "$S/ctr/${pair%%=*}"
  done
  echo old > "$S/db"
  : > "$S/events"
  : > "$S/calls"
}
run_env() {
  env PATH="$WORK/fakebin:$PATH" NUS_FAKE_STATE="$S" NUS_FAKE_CONFIG="$C" NUS_FAKE_CHECK_DIR="$WORK" CONFIG_ROOT="$C" \
    NUSCTL_LINK="$WORK/link/nusctl" NUS_HEALTH_POLL_SECONDS=0 NUS_BACKEND_HEALTH_ATTEMPTS=3 "$@"
}
upgrade() { run_env "$@" "$TEST_SH" "$P/upgrade.sh" "$P" >"$WORK/out" 2>&1; }
nusctl() { run_env "$TEST_SH" "$D/current/nusctl" "$@" </dev/null >"$WORK/out" 2>&1; }
line() { grep -n -m1 -e "$1" "$S/events" | cut -d: -f1; }
before() { local a b; a=$(line "$1"); b=$(line "$2"); [ -n "$a" ] && [ -n "$b" ] && [ "$a" -lt "$b" ]; }
ctr() { sed -n "s/^$2=//p" "$S/ctr/$1"; }
record() { sed -n "s/^$1='\\(.*\\)'$/\\1/p" "$(find "$D/upgrades" -name '*.env' | sort | tail -n 1)"; }
no_secrets() {  # files...
  ! grep -qF -e "$ROOT_PW" -e "$REDIS_PW" -e "$APP_PW" "$@"
}
logs_of_run() { ls "$D"/logs/upgrade/*.log 2>/dev/null; }
same_env() { cmp -s "$C/.env" "$WORK/env.original"; }
old_running() {
  [ "$(ctr backend image)" = "nus-backend:$OLD" ] && [ "$(ctr backend running)" = 1 ] \
    && [ "$(ctr frontend image)" = "nus-front:$OLD" ] && [ "$(ctr frontend running)" = 1 ]
}
untouched_before_stop() {  # label
  ! grep -q '^stop ' "$S/events" && ! grep -q '^dump' "$S/events" && same_env \
    && [ "$(readlink "$D/current")" = "releases/$OLD" ] && [ -z "$(ls "$C"/.env.next-* 2>/dev/null)" ] \
    && [ "$(cat "$S/db")" = old ] && old_running && grep -q 'Nothing was stopped' "$WORK/out" \
    && pass "$1" || bad "$1: $(tail -5 "$WORK/out" | tr '\n' ' ')"
}

# --- 1. success ------------------------------------------------------------------------------------------------
setup
if upgrade; then pass 'upgrade succeeds'; else bad "upgrade failed: $(tail -20 "$WORK/out")"; fi
before 'flyway validate' '^stop frontend backend' && pass 'preflight flyway validate runs before the stop' || bad 'validate order'
before '^build nus-backend' '^stop frontend backend' && pass 'images are built before the downtime' || bad 'build order'
before '^stop frontend backend' '^dump' && before '^dump' '^flyway migrate' \
  && grep -q "^dump env_backend=nus-backend:$OLD backend_running=0 db=old" "$S/events" \
  && pass 'backup runs after the stop (backend not running), before .env changes and Flyway' || bad "backup order: $(tr '\n' '|' < "$S/events")"
grep -q "^flyway migrate env_backend=nus-backend:$NEW" "$S/events" && pass '.env holds the new tags when Flyway runs' || bad '.env switch order'
before '^flyway migrate' '^configure-site' && before '^configure-site' '^redis permissions' \
  && before '^redis permissions' "^up backend nus-backend:$NEW" && before "^up backend nus-backend:$NEW" "^up frontend nus-front:$NEW" \
  && pass 'then site settings, permission caches, backend, frontend' || bad 'start order'
[ "$(readlink "$D/current")" = "releases/$NEW" ] && [ -f "$D/releases/$NEW/nusctl" ] \
  && [ "$(readlink "$WORK/link/nusctl")" = "$D/current/nusctl" ] && pass 'current and nusctl link activated' || bad 'activation'
[ "$(record STATUS)" = completed ] && [ "$(record FROM_RELEASE)" = "$OLD" ] && [ "$(record TO_RELEASE)" = "$NEW" ] \
  && [ -f "$(record DB_BACKUP)" ] && cmp -s "$(record ENV_BACKUP)" "$WORK/env.original" \
  && pass 'upgrade record: completed, backup file and pre-upgrade .env' || bad "record: $(cat "$D"/upgrades/*.env)"
grep -qx "BACKEND_IMAGE=nus-backend:$NEW" "$C/.env" && [ -z "$(ls "$C"/.env.next-* 2>/dev/null)" ] \
  && pass 'new .env installed, no staged copy left' || bad 'env after upgrade'
log_file=$(logs_of_run)
[ -n "$log_file" ] && grep -q 'Upgrade to V9.9.9 completed' "$log_file" && no_secrets "$WORK/out" "$log_file" "$S/calls" "$D"/upgrades/*.env \
  && pass 'upgrade log written; no secret in output, log or record' || bad 'log/secrets'

# --- 2. nusctl rollback previous (from the state above) ---------------------------------------------------------
UPGRADE_BACKUP=$(record DB_BACKUP)
: > "$S/events"
if nusctl rollback previous; then bad 'rollback without --yes and without a terminal must refuse'
else
  grep -q 'rerun with --yes' "$WORK/out" && [ ! -s "$S/events" ] && pass 'rollback without --yes refuses and changes nothing' \
    || bad "rollback refusal: $(tail -3 "$WORK/out")"
fi
if nusctl rollback previous --yes; then pass 'nusctl rollback previous succeeds'; else bad "rollback failed: $(tail -20 "$WORK/out")"; fi
before '^stop frontend backend' '^dump' && grep -q "^dump .* db=new" "$S/events" && before '^dump' '^drop' \
  && before '^drop' '^import schema=old' && [ "$(cat "$S/db")" = old ] \
  && pass 'rollback: stop, safety backup, drop, import of the upgrade backup' || bad "rollback db: $(tr '\n' '|' < "$S/events")"
same_env && ls "$C"/.env.before-rollback-from-"$NEW"-* >/dev/null 2>&1 && [ "$(readlink "$D/current")" = "releases/$OLD" ] \
  && pass 'rollback: .env restored (current one saved), current points at the previous release' || bad 'rollback env/current'
before '^redis restored' "^up backend nus-backend:$OLD" && grep -q "^up backend nus-backend:$OLD compose= $D/releases/$OLD/runtime/compose.yaml" "$S/events" \
  && old_running && pass "rollback: caches cleared, previous images started with $OLD's compose file" || bad "rollback start: $(tr '\n' '|' < "$S/events")"
[ "$(record STATUS)" = rolled-back ] && grep -q 'Safety backup:' "$WORK/out" && no_secrets "$WORK/out" "$S/calls" \
  && pass 'rollback: record marked rolled-back, safety backup reported, no secrets' || bad 'rollback record/output'
[ -f "$UPGRADE_BACKUP" ] && pass 'the upgrade backup is not overwritten by the safety backup' || bad 'upgrade backup lost'

# --- 3. failure at Flyway --------------------------------------------------------------------------------------
setup
if upgrade FAIL_AT=migrate; then bad 'Flyway failure must fail the upgrade'; else
  before '^flyway migrate' '^drop' && before '^drop' '^import schema=old' && [ "$(cat "$S/db")" = old ] \
    && pass 'Flyway failure: database dropped and restored from the fresh backup' || bad "flyway rollback db: $(tr '\n' '|' < "$S/events")"
  same_env && [ "$(readlink "$D/current")" = "releases/$OLD" ] && [ ! -e "$D/releases/$NEW" ] && [ -z "$(ls "$C"/.env.next-* 2>/dev/null)" ] \
    && pass 'Flyway failure: .env and current restored' || bad 'flyway rollback env'
  before '^redis restored' "^up backend nus-backend:$OLD" && old_running \
    && grep -q "compose= $D/releases/$OLD/runtime/compose.yaml" "$S/events" \
    && pass 'Flyway failure: caches cleared, previous images running and healthy' || bad 'flyway rollback start'
  grep -q 'IS RUNNING AGAIN' "$WORK/out" && grep -q 'Failed step: Flyway migrate' "$WORK/out" \
    && grep -q "Upgrade log (the cause is above the FAILED line): $D/logs/upgrade/" "$WORK/out" \
    && [ "$(record STATUS)" = failed-rolled-back ] && no_secrets "$WORK/out" "$S/calls" "$(logs_of_run)" \
    && pass 'Flyway failure: summary names the step and the log; record failed-rolled-back' || bad "flyway summary: $(tail -12 "$WORK/out")"
fi

# --- 4. failure at the health check ----------------------------------------------------------------------------
setup
if upgrade FAIL_HEALTH_IMAGE="nus-backend:$NEW"; then bad 'unhealthy backend must fail the upgrade'; else
  grep -q '^import schema=old' "$S/events" && [ "$(cat "$S/db")" = old ] && same_env && old_running \
    && [ "$(readlink "$D/current")" = "releases/$OLD" ] && grep -q 'start backend .* health checks (exit' "$WORK/out" \
    && pass 'health-check failure: database, .env, current and images restored' || bad "health rollback: $(tail -12 "$WORK/out")"
  ls "$D"/logs/upgrade/*-failed-containers.log >/dev/null 2>&1 && grep -q 'Logs of the failed new containers' "$WORK/out" \
    && pass 'health-check failure: logs of the failed containers kept' || bad 'failed container logs'
fi

# --- 5. failure at the backup: database untouched, no restore ----------------------------------------------------
setup
if upgrade FAIL_AT=backup; then bad 'backup failure must fail the upgrade'; else
  ! grep -q '^drop' "$S/events" && ! grep -q '^flyway migrate' "$S/events" && same_env && old_running \
    && grep -q 'Database: unchanged' "$WORK/out" && pass 'backup failure: no Flyway, no restore, previous version started' \
    || bad "backup failure: $(tail -8 "$WORK/out")"
fi

# --- 6. failure during the automatic restore: manual steps --------------------------------------------------------
setup
upgrade FAIL_AT=migrate FAIL_IMPORT=1
status=$?
if [ "$status" = 0 ]; then bad 'failed restore must fail'; else
  backup_file=$(record DB_BACKUP)
  [ "$status" = 3 ] && grep -q 'AUTOMATIC ROLLBACK DID NOT FINISH (failed step: restore the database)' "$WORK/out" \
    && grep -qF "cp '$(record ENV_BACKUP)' '$C/.env'" "$WORK/out" && grep -qF "ln -sfn 'releases/$OLD' '$D/current'" "$WORK/out" \
    && grep -qF "'$P/nusctl' restore '$backup_file' --no-safety-backup --yes" "$WORK/out" \
    && [ "$(record STATUS)" = rollback-failed ] && no_secrets "$WORK/out" \
    && pass 'restore failure: exit 3 with exact manual commands; record rollback-failed' || bad "manual steps (exit $status): $(tail -14 "$WORK/out")"
  # The printed manual restore works once the cause is gone.
  : > "$S/events"
  run_env "$TEST_SH" "$P/nusctl" restore "$backup_file" --no-safety-backup --yes </dev/null >"$WORK/out2" 2>&1 \
    && [ "$(cat "$S/db")" = old ] && old_running && grep -q "^up backend .* compose= $D/current/runtime/compose.yaml" "$S/events" \
    && [ "$(readlink "$D/current")" = "releases/$OLD" ] \
    && pass "the printed manual restore (package nusctl) brings $OLD back with its own compose file" || bad "manual restore: $(tail -5 "$WORK/out2")"
fi

# --- 7. refusals before the stop --------------------------------------------------------------------------------
setup
upgrade FAIL_AT=validate && bad 'Flyway validate failure must refuse' || untouched_before_stop 'Flyway validate failure refused before any change'
setup
upgrade NUS_UPGRADE_MIN_FREE_MB=999999999 && bad 'low disk must refuse' \
  || { grep -q 'not enough free disk space' "$WORK/out" && untouched_before_stop 'insufficient disk space refused before any change'; }
setup
upgrade FAIL_AT=build && bad 'image build failure must stop' || untouched_before_stop 'image build failure stops before the downtime'
setup "$OLD"
upgrade && bad 'already installed version must refuse' \
  || { grep -q "$OLD is already installed" "$WORK/out" && untouched_before_stop 'already installed version refused'; }

# --- 8. nusctl restore --------------------------------------------------------------------------------------------
setup
echo restorable > "$S/db"
nusctl backup && backup_file=$(tail -n 1 "$WORK/out") && [ -f "$backup_file" ] && pass 'nusctl backup prints the backup path' || bad 'nusctl backup'
echo changed > "$S/db"
: > "$S/events"
if nusctl restore "$backup_file" --yes; then
  before '^stop frontend backend' '^dump' && before '^dump' '^drop' && before '^drop' '^import schema=restorable' \
    && before '^import' '^redis restored' && before '^redis restored' '^up backend' && [ "$(cat "$S/db")" = restorable ] \
    && old_running && pass 'nusctl restore: stop writers, safety backup, drop, import, clear caches, start' \
    || bad "restore order: $(tr '\n' '|' < "$S/events")"
else
  bad "nusctl restore failed: $(tail -10 "$WORK/out")"
fi
: > "$S/events"
gzip -dc "$backup_file" | head -n 3 | gzip -n > "$WORK/cut.sql.gz"
nusctl restore "$WORK/cut.sql.gz" --yes && bad 'truncated backup must be refused' \
  || { grep -q 'completion marker' "$WORK/out" && [ ! -s "$S/events" ] && pass 'nusctl restore refuses a truncated backup before stopping anything' || bad 'truncated refusal'; }

# --- 9. nusctl rollback without an upgrade record --------------------------------------------------------------------
setup
nusctl rollback previous --yes && bad 'rollback without record must refuse' \
  || { grep -q 'no completed upgrade to' "$WORK/out" && [ ! -s "$S/events" ] && pass 'rollback without an upgrade record refuses and lists candidates' || bad 'no-record refusal'; }

# --- 10. doctor: Compose exec forwards the application password without an argv value -----------------------
setup
nusctl doctor && grep -q '^compose mysql$' "$S/events" && no_secrets "$S/calls" "$WORK/out" \
  && grep -q 'exec -T -e MYSQL_PWD mysql mysql -unus ' "$S/calls" \
  && pass 'nusctl doctor uses the application password through MYSQL_PWD' || bad 'doctor credentials'
[ ! -e "$WORK/password-violations" ] \
  && pass 'all Docker calls reject password argv and verify client environments' || bad 'Docker credential violation'

printf '%s failure(s)\n' "$failures"
[ "$failures" -eq 0 ]
