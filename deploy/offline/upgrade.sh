#!/bin/sh
# Upgrade an installed platform from an extracted release package (full or application-update).
# usage: sudo ./upgrade.sh [RELEASE_DIR]        (default: the directory of this script)
#
#   1. preflight  checksums, manifest, version not installed yet, new image tags unused, MySQL and Redis healthy,
#                 free disk space, Flyway info/validate against the live database. Changes nothing.
#   2. prepare    build or load the new images, stage the new .env as .env.next-*, start the observability
#                 services. The platform keeps serving.
#   3. switch     save .env, stop frontend and backend (downtime starts: point of no return), back up the
#                 database, install the staged .env, Flyway migrate, site settings, Redis cache cleanup.
#   4. start      start backend and frontend, health checks, point $DATA_ROOT/current at the new release.
# A failure in 1 or 2 leaves the platform untouched. A failure in 3 or 4 rolls back automatically: .env and
# the current pointer are restored, the database is restored from the backup of step 3 once Flyway has started,
# Redis caches are cleared and the previous images are started and health-checked. If that rollback fails too,
# the exact manual steps are printed. Logs: $DATA_ROOT/logs/upgrade/; records for nusctl rollback:
# $DATA_ROOT/upgrades/. Optional: NUS_UPGRADE_MIN_FREE_MB (default 2048) raises or lowers the free-space floor.
# shellcheck disable=SC2034  # OLD_*, COMPOSE_FILE and ENV_EDIT_FILE are read by helpers in lib/common.sh
set -eu
case "${1:-}" in
  -h|--help) sed -n '2,17p' "$0"; exit 0 ;;
  -*) printf 'unsupported option: %s\nusage: upgrade.sh [RELEASE_DIR]\n' "$1" >&2; exit 2 ;;
esac
[ "$#" -le 1 ] || { printf '%s\n' 'usage: upgrade.sh [RELEASE_DIR]' >&2; exit 2; }
ROOT=$(cd -- "$(dirname "$0")" && pwd)
TARGET=$(cd -- "${1:-$ROOT}" && pwd)
[ -f "$TARGET/SHA256SUMS" ] || { printf '%s\n' 'release checksum missing' >&2; exit 1; }
(cd "$TARGET" && sha256sum --check --quiet SHA256SUMS) \
  || { printf '%s\n' 'release checksum failed: the package is incomplete or was modified' >&2; exit 1; }
export NUS_RELEASE_ROOT="$TARGET"
# shellcheck source=lib/common.sh
. "$TARGET/lib/common.sh"
require_root
require_ubuntu
[ -f "$CONFIG_ROOT/.env" ] || die 'current installation not found'
load_env "$CONFIG_ROOT/.env"

# --- state used by the exit trap ----------------------------------------------------------------------------
STAMP=$(date -u +%Y%m%dT%H%M%SZ)
UPGRADE_STAGE=preflight
STAGE_LABEL='preflight checks'
ENV_NEXT=
ENV_BACKUP=
DB_BACKUP=
RECORD=
UPGRADE_LOG=
FAILED_APP_LOG=
TEE_PID=
ROLLBACK_RESULT=
RB_STATUS=0
NEW_RELEASE_VERSION=
NEW_RELEASE_DIR_CREATED=false
PREVIOUS_RELEASE_LINK=$(current_release_link)
PREVIOUS_RELEASE_ID=${PREVIOUS_RELEASE_LINK##*/}
INSTALLED_VERSION=
[ ! -f "$DATA_ROOT/current/release.env" ] || INSTALLED_VERSION=$(release_value "$DATA_ROOT/current/release.env" RELEASE_VERSION)

stage() {
  UPGRADE_STAGE=$1
  STAGE_LABEL=$2
  log "==> $STAGE_LABEL"
  [ -z "$RECORD" ] || record_set "$RECORD" STAGE "$1"
}

# Everything printed from here on also goes to the upgrade log. tee ignores the terminal's signals, so Ctrl-C
# or a dropped SSH session still lets the rollback write its summary.
start_upgrade_log() {
  log_dir="$DATA_ROOT/logs/upgrade"
  mkdir -p "$log_dir"
  chmod 0700 "$log_dir"
  UPGRADE_LOG="$log_dir/$STAMP-$NEW_RELEASE_VERSION.log"
  log_fifo="$log_dir/.upgrade-$$.fifo"
  rm -f "$log_fifo"
  mkfifo -m 600 "$log_fifo"
  if tee --output-error=warn -a /dev/null </dev/null >/dev/null 2>&1; then
    (trap '' HUP INT TERM; exec tee --output-error=warn -a "$UPGRADE_LOG" <"$log_fifo") &
  else
    (trap '' HUP INT TERM; exec tee -a "$UPGRADE_LOG" <"$log_fifo") &
  fi
  TEE_PID=$!
  exec >"$log_fifo" 2>&1
  rm -f "$log_fifo"
}
finish_log() {
  [ -n "$TEE_PID" ] || return 0
  exec >/dev/null 2>&1
  wait "$TEE_PID" 2>/dev/null || true
}

# Record updates from the exit trap must never end the trap itself.
record_note() {
  [ -n "$RECORD" ] || return 0
  ( record_set "$RECORD" "$1" "$2" ) || log "warning: could not write $1 to the upgrade record $RECORD"
}

run_flyway() {
  flyway_image=$1
  shift
  FLYWAY_NETWORK="${COMPOSE_PROJECT_NAME}_nus-internal" FLYWAY_IMAGE="$flyway_image" \
    MIGRATIONS_DIR="$TARGET/database/migrations" NUS_DB_USERNAME="$NUS_DB_USERNAME" \
    NUS_DB_PASSWORD="$NUS_DB_PASSWORD" NUS_DB_NAME="$NUS_DB_NAME" "$TARGET/lib/flyway.sh" "$@"
}

release_path() {
  case "$1" in
    /*) printf '%s' "$1" ;;
    *) printf '%s/%s' "$DATA_ROOT" "$1" ;;
  esac
}

# Exact commands for the operator when the automatic rollback could not finish. Every command is safe to repeat.
print_manual_rollback() {
  failed_step=$1
  db_needed=$2
  log '=================================================================================='
  log "AUTOMATIC ROLLBACK DID NOT FINISH (failed step: $failed_step)."
  log 'The platform is DOWN. Fix the cause shown above, then run as root, in this order:'
  log "  1. cp '$ENV_BACKUP' '$CONFIG_ROOT/.env' && chmod 600 '$CONFIG_ROOT/.env'"
  if [ -n "$PREVIOUS_RELEASE_LINK" ]; then
    log "  2. ln -sfn '$PREVIOUS_RELEASE_LINK' '$DATA_ROOT/current'"
  else
    log "  2. rm -f '$DATA_ROOT/current'    (there was no current pointer before the upgrade)"
  fi
  if [ "$db_needed" = true ]; then
    log "  3. '$TARGET/nusctl' restore '$DB_BACKUP' --no-safety-backup --yes"
    log '     (stops backend and frontend, drops and recreates the database, imports the pre-upgrade backup,'
    log '      clears the Redis caches, starts the previous images and waits for the health checks)'
  else
    log '  3. nusctl start    (the database was not changed: Flyway had not started)'
  fi
  log '  4. nusctl status; nusctl logs backend --since 30m --error'
  log "Upgrade log: $UPGRADE_LOG"
  [ -z "$FAILED_APP_LOG" ] || log "Logs of the failed new containers: $FAILED_APP_LOG"
  [ -z "$RECORD" ] || log "Upgrade record: $RECORD"
  record_note STATUS rollback-failed
}

# Runs one rollback step with errexit in a subshell, so a die inside it cannot end the rollback itself. Call it
# as a plain command and read RB_STATUS: in an "||" or "if" context the shell would ignore the step's errexit.
rollback_step() {
  rb_label=$1
  shift
  log "--> rollback: $rb_label"
  ( set -e; "$@" )
  RB_STATUS=$?
  if [ "$RB_STATUS" -eq 0 ]; then
    ROLLBACK_DONE="$ROLLBACK_DONE
[nus]   - $rb_label"
  else
    log "rollback step failed (exit $RB_STATUS): $rb_label"
  fi
}

rollback_after_failure() {
  failed_status=$1
  failed_label=$STAGE_LABEL
  db_touched=false
  case "$UPGRADE_STAGE" in
    migrating|migrated|starting|activating) db_touched=true ;;
  esac
  ROLLBACK_DONE=
  ROLLBACK_RESULT=failed
  log '=================================================================================='
  log "UPGRADE TO $NEW_RELEASE_VERSION FAILED during: $failed_label (exit $failed_status)"
  log "Rolling back automatically to ${PREVIOUS_RELEASE_ID:-the previous installation}"
  record_note STATUS rolling-back
  case "$UPGRADE_STAGE" in
    starting|activating)
      FAILED_APP_LOG="$DATA_ROOT/logs/upgrade/$STAMP-$NEW_RELEASE_VERSION-failed-containers.log"
      compose logs --no-color --tail=500 backend frontend >"$FAILED_APP_LOG" 2>&1 || true
      log "Saved the logs of the failed new containers to $FAILED_APP_LOG"
      ;;
  esac
  rollback_step 'stop backend and frontend' stop_writers
  [ "$RB_STATUS" -eq 0 ] || { print_manual_rollback 'stop backend and frontend' "$db_touched"; return 0; }
  [ -z "$ENV_NEXT" ] || rm -f "$ENV_NEXT"
  rollback_step "restore .env from $ENV_BACKUP" restore_env_file "$ENV_BACKUP"
  [ "$RB_STATUS" -eq 0 ] || { print_manual_rollback 'restore .env' "$db_touched"; return 0; }
  if [ "$(current_release_link)" != "$PREVIOUS_RELEASE_LINK" ]; then
    rollback_step "point $DATA_ROOT/current back to ${PREVIOUS_RELEASE_LINK:-nothing}" \
      set_current_release "$PREVIOUS_RELEASE_LINK"
    [ "$RB_STATUS" -eq 0 ] || { print_manual_rollback 'restore the current pointer' "$db_touched"; return 0; }
  fi
  if [ "$NEW_RELEASE_DIR_CREATED" = true ]; then
    rm -rf "$DATA_ROOT/releases/$NEW_RELEASE_VERSION"
  fi
  # From here Compose must read the previous release's file and the restored .env, nothing else.
  if [ -n "$PREVIOUS_RELEASE_LINK" ] && [ -f "$(release_path "$PREVIOUS_RELEASE_LINK")/runtime/compose.yaml" ]; then
    COMPOSE_FILE="$(release_path "$PREVIOUS_RELEASE_LINK")/runtime/compose.yaml"
  fi
  reload_env_for_compose
  if [ "$db_touched" = true ]; then
    rollback_step "restore the database from $DB_BACKUP (drop, recreate, import)" run_db_restore "$DB_BACKUP"
    [ "$RB_STATUS" -eq 0 ] || { print_manual_rollback 'restore the database' true; return 0; }
    rollback_step 'clear the Redis caches of the restored database' clear_restored_db_caches
    [ "$RB_STATUS" -eq 0 ] || { print_manual_rollback 'clear the Redis caches' true; return 0; }
  fi
  rollback_step "start backend $BACKEND_IMAGE and frontend $FRONTEND_IMAGE, health checks" start_apps
  [ "$RB_STATUS" -eq 0 ] || { print_manual_rollback 'start the previous version' "$db_touched"; return 0; }
  ROLLBACK_RESULT=ok
  record_note STATUS failed-rolled-back
  record_note FINISHED_AT "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  log '=================================================================================='
  log "UPGRADE FAILED - ${PREVIOUS_RELEASE_ID:-the previous version} IS RUNNING AGAIN"
  log "Failed step: $failed_label (exit $failed_status)"
  log "Rollback steps completed:$ROLLBACK_DONE"
  if [ "$db_touched" = true ]; then
    log "Database: restored from $DB_BACKUP (taken after the stop, so no data is lost)"
  else
    log "Database: unchanged (Flyway had not started); backup ${DB_BACKUP:-not taken yet} kept"
  fi
  log "Upgrade log (the cause is above the FAILED line): $UPGRADE_LOG"
  [ -z "$FAILED_APP_LOG" ] || log "Logs of the failed new containers: $FAILED_APP_LOG"
  [ -z "$RECORD" ] || log "Upgrade record: $RECORD"
  log "Kept for diagnosis: package $TARGET, images $NEW_BACKEND_IMAGE and $NEW_FRONTEND_IMAGE"
}

on_exit() {
  exit_status=$?
  trap - EXIT
  trap '' HUP INT TERM
  set +eu
  if [ "$UPGRADE_STAGE" = finished ]; then
    finish_log
    exit 0
  fi
  [ "$exit_status" -ne 0 ] || exit_status=1
  case "$UPGRADE_STAGE" in
    preflight|prepare)
      [ -z "$ENV_NEXT" ] || rm -f "$ENV_NEXT"
      record_note STATUS aborted-before-stop
      log "Upgrade${NEW_RELEASE_VERSION:+ to $NEW_RELEASE_VERSION} stopped during $STAGE_LABEL (exit $exit_status)."
      log 'Nothing was stopped: the platform keeps running, and .env, the database and the current pointer are unchanged.'
      if [ "$UPGRADE_STAGE" = prepare ]; then
        log 'New images may already be loaded and Elasticsearch/SkyWalking may run the package images; both are harmless.'
      fi
      [ -z "$UPGRADE_LOG" ] || log "Upgrade log: $UPGRADE_LOG"
      ;;
    *)
      rollback_after_failure "$exit_status"
      [ "$ROLLBACK_RESULT" = ok ] || exit_status=3
      ;;
  esac
  finish_log
  exit "$exit_status"
}
trap on_exit EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

# --- 1. preflight: nothing changes ----------------------------------------------------------------------------
NEW_RELEASE_VERSION=$(release_value "$TARGET/release.env" RELEASE_VERSION)
NEW_RELEASE_KIND=$(release_value "$TARGET/release.env" RELEASE_KIND)
NEW_RELEASE_KIND=${NEW_RELEASE_KIND:-full}
NEW_BACKEND_IMAGE=$(release_value "$TARGET/release.env" BACKEND_IMAGE)
NEW_FRONTEND_IMAGE=$(release_value "$TARGET/release.env" FRONTEND_IMAGE)
case "$NEW_RELEASE_VERSION" in
  ''|*[!A-Za-z0-9._-]*) NEW_RELEASE_VERSION=; die 'invalid RELEASE_VERSION in release.env' ;;
esac
start_upgrade_log
log "Upgrade ${INSTALLED_VERSION:-unknown version} -> $NEW_RELEASE_VERSION ($NEW_RELEASE_KIND) from $TARGET"
log "Log: $UPGRADE_LOG"
stage preflight 'preflight checks (nothing is changed)'
[ -n "$NEW_BACKEND_IMAGE" ] && [ -n "$NEW_FRONTEND_IMAGE" ] || die 'release.env must define BACKEND_IMAGE and FRONTEND_IMAGE'
case "$NEW_RELEASE_KIND" in
  application-update|full) ;;
  *) die "unsupported RELEASE_KIND: $NEW_RELEASE_KIND" ;;
esac
if [ "$NEW_RELEASE_VERSION" = "$INSTALLED_VERSION" ] || [ "$NEW_RELEASE_VERSION" = "$PREVIOUS_RELEASE_ID" ]; then
  die "$NEW_RELEASE_VERSION is already installed ($DATA_ROOT/current -> $PREVIOUS_RELEASE_LINK); every package needs a new version number"
fi
[ -e "$DATA_ROOT/releases/$NEW_RELEASE_VERSION" ] || NEW_RELEASE_DIR_CREATED=pending
ensure_domain_override
service_is_healthy mysql || die 'mysql is not healthy; check it with: sudo nusctl status'
service_is_healthy redis || die 'redis is not healthy; check it with: sudo nusctl status'
service_is_healthy backend || log 'note: the backend is not healthy before the upgrade'
CURRENT_BACKEND_IMAGE=${BACKEND_IMAGE:-$(running_image backend)}
CURRENT_FRONTEND_IMAGE=${FRONTEND_IMAGE:-$(running_image frontend)}
[ -n "$CURRENT_BACKEND_IMAGE" ] && [ -n "$CURRENT_FRONTEND_IMAGE" ] \
  || die 'cannot tell the current backend and frontend images (.env has no BACKEND_IMAGE/FRONTEND_IMAGE and no container runs)'
refuse_reused_image backend "$NEW_BACKEND_IMAGE" "$CURRENT_BACKEND_IMAGE" "$(running_image backend)"
refuse_reused_image frontend "$NEW_FRONTEND_IMAGE" "$CURRENT_FRONTEND_IMAGE" "$(running_image frontend)"
docker image inspect "$CURRENT_BACKEND_IMAGE" >/dev/null 2>&1 || die "current backend image is missing (needed for rollback): $CURRENT_BACKEND_IMAGE"
docker image inspect "$CURRENT_FRONTEND_IMAGE" >/dev/null 2>&1 || die "current frontend image is missing (needed for rollback): $CURRENT_FRONTEND_IMAGE"
MYSQL_IMAGE=${MYSQL_IMAGE:-$(running_image mysql)}
REDIS_IMAGE=${REDIS_IMAGE:-$(running_image redis)}
ELASTICSEARCH_IMAGE=${ELASTICSEARCH_IMAGE:-$(running_image elasticsearch)}
SKYWALKING_OAP_IMAGE=${SKYWALKING_OAP_IMAGE:-$(running_image skywalking-oap)}
SKYWALKING_UI_IMAGE=${SKYWALKING_UI_IMAGE:-$(running_image skywalking-ui)}
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:10.20.1-alpine}
# The images the rollback must start again, whatever the new release.env exports later.
OLD_BACKEND_IMAGE=$CURRENT_BACKEND_IMAGE
OLD_FRONTEND_IMAGE=$CURRENT_FRONTEND_IMAGE
OLD_MYSQL_IMAGE=$MYSQL_IMAGE
OLD_REDIS_IMAGE=$REDIS_IMAGE
OLD_FLYWAY_IMAGE=$FLYWAY_IMAGE
OLD_ELASTICSEARCH_IMAGE=$ELASTICSEARCH_IMAGE
OLD_SKYWALKING_OAP_IMAGE=$SKYWALKING_OAP_IMAGE
OLD_SKYWALKING_UI_IMAGE=$SKYWALKING_UI_IMAGE
case "$NEW_RELEASE_KIND" in
  application-update)
    [ -f "$TARGET/artifacts/backend/nus-server.jar" ] || die 'backend update artifact is missing'
    [ -f "$TARGET/artifacts/frontend/dist/index.html" ] || die 'frontend update artifact is missing'
    package_kb=$(du -sk "$TARGET/artifacts" | awk '{ print $1 }')
    ;;
  full)
    [ -f "$TARGET/images.tar" ] || die 'release image archive is missing'
    # docker load unpacks the archive, so count it twice.
    package_kb=$(( $(du -sk "$TARGET/images.tar" | awk '{ print $1 }') * 2 ))
    ;;
esac
# Disk: the backup and a later safety backup need about twice the database, the new images their own size.
min_free_mb=${NUS_UPGRADE_MIN_FREE_MB:-2048}
case "$min_free_mb" in ''|*[!0-9]*) die 'NUS_UPGRADE_MIN_FREE_MB must be a number of MiB' ;; esac
min_free_kb=$((min_free_mb * 1024))
db_bytes=$(mysql_root_query "SELECT COALESCE(SUM(data_length + index_length), 0) FROM information_schema.tables WHERE table_schema = '$NUS_DB_NAME';") \
  || die 'cannot read the database size'
case "$db_bytes" in ''|*[!0-9]*) die "cannot read the database size: $db_bytes" ;; esac
db_kb=$((db_bytes / 1024 + 1))
mkdir -p "$DATA_ROOT/backups"
docker_root=$(docker info -f '{{.DockerRootDir}}' 2>/dev/null || true)
[ -n "$docker_root" ] && [ -d "$docker_root" ] || docker_root=$DATA_ROOT
if [ "$(filesystem_of "$DATA_ROOT/backups")" = "$(filesystem_of "$docker_root")" ]; then
  need_kb=$((db_kb * 2 + package_kb))
  [ "$need_kb" -ge "$min_free_kb" ] || need_kb=$min_free_kb
  require_free_space "$DATA_ROOT/backups" "$need_kb" "backups (2 x database $((db_kb / 1024)) MiB) and new images ($((package_kb / 1024)) MiB)"
else
  need_kb=$((db_kb * 2))
  [ "$need_kb" -ge "$min_free_kb" ] || need_kb=$min_free_kb
  require_free_space "$DATA_ROOT/backups" "$need_kb" "backups (2 x database $((db_kb / 1024)) MiB)"
  need_kb=$package_kb
  [ "$need_kb" -ge "$min_free_kb" ] || need_kb=$min_free_kb
  require_free_space "$docker_root" "$need_kb" "new images ($((package_kb / 1024)) MiB)"
fi
# Flyway against the live database: failed or edited migrations, or a database newer than this package
# ("applied migration not resolved locally"), stop the upgrade here instead of during the downtime.
run_flyway "$OLD_FLYWAY_IMAGE" info || die 'flyway info failed against the live database'
run_flyway "$OLD_FLYWAY_IMAGE" validate '-ignoreMigrationPatterns=*:pending' \
  || die 'Flyway validation failed against the live database (see above); nothing was changed'

# --- 2. prepare: the platform keeps serving -------------------------------------------------------------------
stage prepare 'prepare images, the new .env and observability (the platform keeps serving)'
case "$NEW_RELEASE_KIND" in
  application-update)
    docker build --pull=false --build-arg "BASE_IMAGE=$CURRENT_BACKEND_IMAGE" \
      -t "$NEW_BACKEND_IMAGE" -f "$TARGET/artifacts/backend/Dockerfile" "$TARGET/artifacts/backend"
    docker build --pull=false --build-arg "BASE_IMAGE=$CURRENT_FRONTEND_IMAGE" \
      -t "$NEW_FRONTEND_IMAGE" -f "$TARGET/artifacts/frontend/Dockerfile" "$TARGET/artifacts/frontend"
    ;;
  full)
    docker load -i "$TARGET/images.tar" >/dev/null
    ;;
esac
ENV_NEXT="$CONFIG_ROOT/.env.next-$STAMP"
(umask 077 && cp "$CONFIG_ROOT/.env" "$ENV_NEXT")
chmod 0600 "$ENV_NEXT"
ENV_EDIT_FILE=$ENV_NEXT
for setting in \
  NUS_AI_EXTERNAL_STUDENT_DATA_ENABLED=true \
  NUS_AI_REVIEW_ENABLED=true \
  NUS_AI_MAX_CONCURRENT=4 \
  NUS_AI_CALL_TIMEOUT=120s \
  NUS_AI_CONNECT_TIMEOUT=10s \
  NUS_AI_REVIEW_TASK_POOL_SIZE=2 \
  NUS_AI_REVIEW_TASK_QUEUE_CAPACITY=100 \
  NUS_MONITOR_TICKET_TTL=45s \
  NUS_MONITOR_SESSION_TTL=20m \
  NUS_DRUID_ENABLED=true \
  NUS_SPRING_BOOT_ADMIN_ENABLED=true \
  NUS_SKYWALKING_ENABLED=true \
  NUS_SKYWALKING_SERVICE_NAME=nus-server \
  NUS_SKYWALKING_SAMPLE_N_PER_3_SECS=100 \
  NUS_SKYWALKING_RECORD_TTL_DAYS=7 \
  NUS_SKYWALKING_METRICS_TTL_DAYS=15; do
  ensure_env_default "$setting"
done
ensure_monitor_credentials
if ! grep -q '^NUS_MONITOR_COOKIE_SECURE=' "$ENV_NEXT"; then
  case "$NUS_SITE_URL" in
    https://*) printf '%s\n' 'NUS_MONITOR_COOKIE_SECURE=true' >> "$ENV_NEXT" ;;
    *) printf '%s\n' 'NUS_MONITOR_COOKIE_SECURE=false' >> "$ENV_NEXT" ;;
  esac
fi
ensure_env_default 'NUS_DRUID_ALLOW=127.0.0.1,172.16.0.0/12'
ensure_env_default 'NUS_SKYWALKING_UI_PORT=8082'
if ! grep -q '^NUS_SKYWALKING_URL=' "$ENV_NEXT"; then
  printf 'NUS_SKYWALKING_URL=%s\n' "$(default_skywalking_url "$NUS_SITE_URL" "${NUS_SKYWALKING_UI_PORT:-8082}")" >> "$ENV_NEXT"
fi
load_env "$ENV_NEXT"
: "${NUS_MYBATIS_ENCRYPTOR_PASSWORD:?NUS_MYBATIS_ENCRYPTOR_PASSWORD must be preserved in .env}"
load_env "$TARGET/release.env"
ensure_horizon_credentials
# From here the shell exports the new values, which take precedence over --env-file in Compose.
load_env "$ENV_NEXT"
load_env "$TARGET/release.env"
sync_image_tags
chmod 0600 "$ENV_NEXT"
prepare_observability_host
compose up -d elasticsearch skywalking-oap skywalking-ui
wait_healthy elasticsearch 90
wait_healthy skywalking-oap 90
wait_healthy skywalking-ui 60

# --- 3. switch: downtime, automatic rollback on any failure ---------------------------------------------------
record_dir=$(upgrade_record_dir)
mkdir -p "$record_dir"
chmod 0700 "$record_dir"
upgrade_record="$record_dir/$STAMP-$NEW_RELEASE_VERSION.env"
(umask 077 && : > "$upgrade_record")
for field in \
  "RECORD_VERSION=1" "STATUS=in-progress" "STARTED_AT=$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  "FROM_RELEASE=$PREVIOUS_RELEASE_ID" "FROM_RELEASE_LINK=$PREVIOUS_RELEASE_LINK" "FROM_VERSION=$INSTALLED_VERSION" \
  "TO_RELEASE=$NEW_RELEASE_VERSION" "PACKAGE=$TARGET" "LOG=$UPGRADE_LOG" \
  "FROM_BACKEND_IMAGE=$OLD_BACKEND_IMAGE" "FROM_FRONTEND_IMAGE=$OLD_FRONTEND_IMAGE" \
  "TO_BACKEND_IMAGE=$NEW_BACKEND_IMAGE" "TO_FRONTEND_IMAGE=$NEW_FRONTEND_IMAGE"; do
  record_set "$upgrade_record" "${field%%=*}" "${field#*=}"
done
RECORD=$upgrade_record
backup_env_file "$NEW_RELEASE_VERSION"
# shellcheck disable=SC2154  # env_backup is set by backup_env_file
ENV_BACKUP=$env_backup
record_set "$RECORD" ENV_BACKUP "$ENV_BACKUP"
stage stopping 'stop frontend and backend (downtime starts; from here a failure rolls back automatically)'
stop_writers
stage backup 'back up the database (no writers are running)'
DB_BACKUP=$(run_db_backup)
record_set "$RECORD" DB_BACKUP "$DB_BACKUP"
log "Database backup: $DB_BACKUP"
stage env-switched 'install the new .env'
mv "$ENV_NEXT" "$CONFIG_ROOT/.env"
ENV_NEXT=
chmod 0600 "$CONFIG_ROOT/.env"
stage migrating 'Flyway migrate'
run_flyway "$FLYWAY_IMAGE" migrate
stage migrated 'site settings and Redis cache cleanup'
site_cid=$(mysql_container)
MYSQL_CONTAINER=$site_cid NUS_DB_ROOT_PASSWORD="$NUS_DB_ROOT_PASSWORD" NUS_DB_NAME="$NUS_DB_NAME" \
  NUS_SITE_URL="$NUS_SITE_URL" NUS_SKYWALKING_URL="$NUS_SKYWALKING_URL" NUS_FILE_BASE_PATH=/app/uploads \
  "$TARGET/lib/configure-site.sh"
# The backend is stopped here, so migrated menus and grants take effect on its first start.
clear_permission_caches

# --- 4. start --------------------------------------------------------------------------------------------------
stage starting "start backend $NEW_BACKEND_IMAGE and frontend $NEW_FRONTEND_IMAGE, health checks"
start_apps
stage activating "activate release $NEW_RELEASE_VERSION"
[ "$NEW_RELEASE_DIR_CREATED" != pending ] || NEW_RELEASE_DIR_CREATED=true
activate_release "$TARGET"
record_set "$RECORD" STATUS completed
record_set "$RECORD" FINISHED_AT "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
UPGRADE_STAGE=finished
log '=================================================================================='
log "Upgrade to $NEW_RELEASE_VERSION completed; backend and frontend are healthy"
log "Previous release kept: ${PREVIOUS_RELEASE_LINK:-none} (images $OLD_BACKEND_IMAGE, $OLD_FRONTEND_IMAGE)"
log "Database backup taken during the downtime: $DB_BACKUP"
log "Previous .env: $ENV_BACKUP"
log 'To go back: sudo nusctl rollback previous'
log "Upgrade log: $UPGRADE_LOG"
