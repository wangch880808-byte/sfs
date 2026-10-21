#!/bin/sh
set -eu

log() { printf '[nus] %s\n' "$*"; }
die() { printf '[nus] ERROR: %s\n' "$*" >&2; exit 1; }
ROOT_DIR=${NUS_RELEASE_ROOT:-$(cd -- "$(dirname "$0")/.." && pwd)}
COMPOSE_FILE="$ROOT_DIR/runtime/compose.yaml"
CONFIG_ROOT=${CONFIG_ROOT:-/srv/nus/config}
DATA_ROOT=${DATA_ROOT:-/srv/nus}
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-nus}
NUSCTL_LINK=${NUSCTL_LINK:-/usr/local/bin/nusctl}
NUS_IMAGE_KEYS='BACKEND_IMAGE FRONTEND_IMAGE MYSQL_IMAGE REDIS_IMAGE FLYWAY_IMAGE ELASTICSEARCH_IMAGE SKYWALKING_OAP_IMAGE SKYWALKING_UI_IMAGE'
compose() {
  if [ -f "$CONFIG_ROOT/domain-nginx/nginx.conf" ] && [ -f "$CONFIG_ROOT/domain-nginx/compose.override.yaml" ]; then
    docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$CONFIG_ROOT/.env" \
      -f "$COMPOSE_FILE" -f "$CONFIG_ROOT/domain-nginx/compose.override.yaml" "$@"
  else
    docker compose --project-name "$COMPOSE_PROJECT_NAME" --env-file "$CONFIG_ROOT/.env" -f "$COMPOSE_FILE" "$@"
  fi
}
ensure_domain_override() {
  [ -f "$CONFIG_ROOT/domain-nginx/nginx.conf" ] || return 0
  [ ! -f "$CONFIG_ROOT/domain-nginx/compose.override.yaml" ] || return 0
  cat > "$CONFIG_ROOT/domain-nginx/compose.override.yaml" <<'EOF'
services:
  frontend:
    command: ["nginx", "-c", "/etc/nus-domain/nginx.conf", "-g", "daemon off;"]
    volumes:
      - ${CONFIG_ROOT:-/srv/nus/config}/domain-nginx:/etc/nus-domain:ro
EOF
  chmod 0600 "$CONFIG_ROOT/domain-nginx/compose.override.yaml"
  log 'Existing domain Nginx configuration retained through persistent Compose override'
}
# Export every KEY=VALUE of an env file into the shell (and so into Compose interpolation).
load_env() {
  set -a
  # shellcheck source=/dev/null
  . "$1"
  set +a
}
# The env file that the ensure_* and sync_* helpers edit. upgrade.sh points ENV_EDIT_FILE at a staged copy so
# the live .env only changes after the database backup; install.sh edits .env directly.
env_edit_file() { printf '%s' "${ENV_EDIT_FILE:-$CONFIG_ROOT/.env}"; }
# Replace or append KEY=VALUE without sed -i: values may contain # or &, and the file keeps its mode and owner.
env_file_set() {
  env_set_file=$1
  env_set_tmp="$env_set_file.tmp.$$"
  (umask 077 && NUS_K=$2 NUS_V=$3 awk '
    index($0, ENVIRON["NUS_K"] "=") == 1 { if (!done) print ENVIRON["NUS_K"] "=" ENVIRON["NUS_V"]; done = 1; next }
    { print }
    END { if (!done) print ENVIRON["NUS_K"] "=" ENVIRON["NUS_V"] }
  ' "$env_set_file" > "$env_set_tmp") || { rm -f "$env_set_tmp"; die "cannot update $env_set_file"; }
  cat "$env_set_tmp" > "$env_set_file" || { rm -f "$env_set_tmp"; die "cannot update $env_set_file"; }
  rm -f "$env_set_tmp"
}
ensure_env_default() {
  key=${1%%=*}
  grep -q "^${key}=" "$(env_edit_file)" || printf '%s\n' "$1" >> "$(env_edit_file)"
}
service_is_healthy() {
  compose ps --format '{{.Service}}={{.Health}}' | grep -q "^$1=healthy$"
}
wait_healthy() {
  service=$1
  attempts=${2:-60}
  for _ in $(seq 1 "$attempts"); do
    service_is_healthy "$service" && return 0
    sleep "${NUS_HEALTH_POLL_SECONDS:-2}"
  done
  die "$service did not become healthy"
}
activate_release() {
  source_root=$1
  case "${RELEASE_VERSION:-}" in
    ''|*[!A-Za-z0-9._-]*) die 'invalid RELEASE_VERSION' ;;
  esac
  release_root="$DATA_ROOT/releases/$RELEASE_VERSION"
  mkdir -p "$release_root/runtime" "$release_root/database/migrations" "$release_root/lib"
  cp "$source_root/release.env" "$source_root/README.txt" "$source_root/nusctl" "$source_root/upgrade.sh" "$release_root/"
  # Kept for rollback: the notes name the rollback steps, the metadata the exact build.
  for optional in 更新说明.md BUILD-METADATA.json; do
    [ ! -f "$source_root/$optional" ] || cp "$source_root/$optional" "$release_root/"
  done
  cp "$source_root/runtime/compose.yaml" "$release_root/runtime/"
  cp "$source_root"/database/migrations/*.sql "$release_root/database/migrations/"
  cp "$source_root"/lib/*.sh "$release_root/lib/"
  ln -sfn "releases/$RELEASE_VERSION" "$DATA_ROOT/current"
  ln -sfn "$DATA_ROOT/current/nusctl" "$NUSCTL_LINK"
}
# Kernel and directory settings Elasticsearch and Horizon need; shared by install.sh and upgrade.sh.
prepare_observability_host() {
  mkdir -p "$DATA_ROOT/data/elasticsearch" "$DATA_ROOT/data/horizon"
  chown -R 1000:0 "$DATA_ROOT/data/elasticsearch"
  chown -R 100:101 "$DATA_ROOT/data/horizon"
  printf '%s\n' 'vm.max_map_count=262144' > /etc/sysctl.d/99-nus-observability.conf
  sysctl -q -w vm.max_map_count=262144
}
require_root() { [ "$(id -u)" -eq 0 ] || die 'must run as root'; }
require_ubuntu() {
  [ -r /etc/os-release ] || die '/etc/os-release is missing'
  . /etc/os-release
  [ "${ID:-}" = ubuntu ] && [ "${VERSION_ID:-}" = 24.04 ] || die 'Ubuntu 24.04 is required'
  [ "$(dpkg --print-architecture)" = amd64 ] || die 'amd64 host is required'
}

sync_image_tags() {
  for key in $NUS_IMAGE_KEYS; do
    value=$(eval "printf '%s' \"\${$key}\"")
    env_file_set "$(env_edit_file)" "$key" "$value"
  done
}

ensure_monitor_credentials() {
  monitor_env=$(env_edit_file)
  if ! grep -q '^NUS_MONITOR_USERNAME=' "$monitor_env"; then
    printf '%s\n' 'NUS_MONITOR_USERNAME=monitor' >> "$monitor_env"
  fi
  if ! grep -q '^NUS_MONITOR_PASSWORD=' "$monitor_env"; then
    printf 'NUS_MONITOR_PASSWORD=%s\n' "$(openssl rand -hex 16)" >> "$monitor_env"
  fi
  load_env "$monitor_env"
  case "$NUS_MONITOR_USERNAME" in
    ''|*[!A-Za-z0-9._-]*) die 'NUS_MONITOR_USERNAME contains unsupported characters' ;;
  esac
  [ -n "$NUS_MONITOR_PASSWORD" ] || die 'NUS_MONITOR_PASSWORD must not be empty'
  chmod 0600 "$monitor_env"
}

default_skywalking_url() {
  site_url=$1
  ui_port=$2
  site_host=$(printf '%s' "$site_url" | sed -E 's#^https?://([^/:]+)(:[0-9]+)?(/.*)?$#\1#')
  [ "$site_host" != "$site_url" ] || die 'cannot derive SkyWalking host from NUS_SITE_URL; set NUS_SKYWALKING_URL explicitly'
  printf 'http://%s:%s/\n' "$site_host" "$ui_port"
}

# Print one key of a release manifest without inheriting the caller's value of that key.
release_value() {
  (
    unset "$2"
    # shellcheck source=/dev/null
    . "$1"
    eval "printf '%s' \"\${$2:-}\""
  )
}

running_image() {
  container=$(compose ps -q "$1" 2>/dev/null || true)
  [ -n "$container" ] || return 0
  docker inspect -f '{{.Config.Image}}' "$container" 2>/dev/null || true
}

# Building or loading over an image tag that is in use would drop the image needed for rollback.
refuse_reused_image() {
  service=$1
  new_image=${2%@*}
  shift 2
  for current_image in "$@"; do
    [ -n "$current_image" ] || continue
    [ "$new_image" != "${current_image%@*}" ] || die "new $service image $new_image is already in use; build the update with a new tag (after an interrupted upgrade, first restore the .env.before-* backup that holds the previous tags)"
  done
}

backup_env_file() {
  env_backup="$CONFIG_ROOT/.env.before-$1-$(date -u +%Y%m%dT%H%M%SZ)"
  [ ! -e "$env_backup" ] || die "env backup already exists: $env_backup"
  (umask 077 && cp "$CONFIG_ROOT/.env" "$env_backup") || die "cannot save $CONFIG_ROOT/.env to $env_backup"
  chmod 0600 "$env_backup"
  log "Saved current .env to $env_backup"
}

# Spring caches holding menu/role/permission lookups and notification templates (RedisKeyConstants;
# keys are "<cache>:<key>", no global prefix): migrations rename menus and rewrite template wording,
# so stale entries would linger for their TTL. Tokens, captcha and other keys are left alone, so users
# stay signed in.
NUS_PERMISSION_CACHE_CLEAN_LUA='local removed = 0
for _, pattern in ipairs(ARGV) do
  local cursor = "0"
  repeat
    local reply = redis.call("SCAN", cursor, "MATCH", pattern, "COUNT", 1000)
    cursor = reply[1]
    for _, key in ipairs(reply[2]) do
      removed = removed + redis.call("DEL", key)
    end
  until cursor == "0"
end
return removed'
# Usage: clear_redis_caches WHAT_FAILED WHAT_CLEARED PATTERN...
clear_redis_caches() {
  cache_fail_label=$1
  cache_ok_label=$2
  shift 2
  : "${NUS_REDIS_PASSWORD:?NUS_REDIS_PASSWORD must be set in .env}"
  redis_container=$(compose ps -q redis)
  [ -n "$redis_container" ] || die "redis is not running; cannot clear $cache_fail_label"
  # The password reaches redis-cli only through REDISCLI_AUTH, never through argv or the log.
  removed=$(REDISCLI_AUTH=$NUS_REDIS_PASSWORD docker exec -e REDISCLI_AUTH "$redis_container" \
    redis-cli --raw EVAL "$NUS_PERMISSION_CACHE_CLEAN_LUA" 0 "$@") \
    || die "failed to clear $cache_fail_label in Redis"
  # redis-cli exits 0 on error replies such as NOAUTH, so only a plain count means success.
  case "$removed" in
    ''|*[!0-9]*) die "failed to clear $cache_fail_label in Redis: $removed" ;;
  esac
  log "Cleared $removed cached $cache_ok_label in Redis; login sessions are kept"
}
# Since V1.0.1.9 config, semester, indicator and dict reads are cached too; migrations may rewrite those rows.
clear_permission_caches() {
  clear_redis_caches 'menu, permission and data caches' 'menu/permission/notification-template/config/semester/indicator/dict entries' \
    'permission_menu_ids:*' 'menu_role_ids:*' 'user_role_ids:*' 'role:*' 'notify_template:*' \
    'infra_config_by_key:*' 'edu_current_semester:*' 'edu_semester_options:*' \
    'capacity_indicator_tree:*' 'system_dict_data:*'
}
# After a database restore every Spring cache may describe rows that no longer exist (or differ), so all
# RedisKeyConstants caches go; tokens stay, so users who still exist remain signed in.
clear_restored_db_caches() {
  clear_redis_caches 'caches of the restored database' 'entries of the restored database (menus, permissions, roles, departments, OAuth clients, templates)' \
    'permission_menu_ids:*' 'menu_role_ids:*' 'user_role_ids:*' 'role:*' 'notify_template:*' \
    'dept_children_ids:*' 'oauth_client:*' 'mail_account:*' 'mail_template:*' 'sms_template:*' \
    'wxa_subscribe_template:*' \
    'infra_config_by_key:*' 'edu_current_semester:*' 'edu_semester_options:*' \
    'capacity_indicator_tree:*' 'system_dict_data:*'
}

ensure_horizon_credentials() {
  if grep -q '^NUS_HORIZON_AUTH_LOCAL_USERS=' "$(env_edit_file)"; then
    return
  fi
  horizon_hash=$(printf '%s\n' "$NUS_MONITOR_PASSWORD" | docker run --rm -i --network none \
    --entrypoint node "$SKYWALKING_UI_IMAGE" --input-type=module -e \
    'import argon2 from "argon2"; import fs from "node:fs"; const password=fs.readFileSync(0,"utf8").replace(/\r?\n$/, ""); if (!password) process.exit(2); console.log(await argon2.hash(password,{type:argon2.argon2id}));')
  horizon_users=$(printf '[{"username":"%s","passwordHash":"%s","roles":["admin"]}]' \
    "$NUS_MONITOR_USERNAME" "$horizon_hash")
  printf "NUS_HORIZON_AUTH_LOCAL_USERS='%s'\n" "$horizon_users" >> "$(env_edit_file)"
  export NUS_HORIZON_AUTH_LOCAL_USERS=$horizon_users
  chmod 0600 "$(env_edit_file)"
}

# --- database, application and rollback helpers shared by upgrade.sh and nusctl ------------------------------

mysql_container() {
  mysql_cid=$(compose ps -q mysql 2>/dev/null || true)
  [ -n "$mysql_cid" ] || die 'mysql is not running; start it first with: sudo nusctl start'
  printf '%s\n' "$mysql_cid"
}
# Root query over the socket; the password reaches mysql only through MYSQL_PWD, never through argv or the log.
mysql_root_query() {
  : "${NUS_DB_ROOT_PASSWORD:?NUS_DB_ROOT_PASSWORD must be set in .env}"
  query_cid=$(mysql_container) || return 1
  MYSQL_PWD=$NUS_DB_ROOT_PASSWORD docker exec -e MYSQL_PWD "$query_cid" mysql --protocol=socket -uroot -Nse "$1"
}
# Prints the path of the verified backup.
run_db_backup() {
  backup_cid=$(mysql_container) || return 1
  BACKUP_DIR="$DATA_ROOT/backups" MYSQL_CONTAINER="$backup_cid" NUS_DB_ROOT_PASSWORD="$NUS_DB_ROOT_PASSWORD" \
    NUS_DB_NAME="$NUS_DB_NAME" "$ROOT_DIR/lib/backup.sh"
}
# Usage: run_db_restore [--check] FILE. Drops and recreates the database, so tables of newer migrations go too.
run_db_restore() {
  restore_cid=$(mysql_container) || return 1
  MYSQL_CONTAINER="$restore_cid" NUS_DB_ROOT_PASSWORD="$NUS_DB_ROOT_PASSWORD" NUS_DB_NAME="$NUS_DB_NAME" \
    "$ROOT_DIR/lib/restore.sh" "$@"
}
# The backend is the only writer to MySQL and Redis; the frontend goes first so nobody reaches a dead backend.
stop_writers() {
  compose stop frontend backend
}
# Nginx resolves "backend" once at start, so the frontend is recreated after every backend (re)start.
start_apps() {
  compose up -d backend || die 'cannot start the backend'
  wait_healthy backend "${NUS_BACKEND_HEALTH_ATTEMPTS:-90}"
  compose up -d --force-recreate frontend || die 'cannot start the frontend'
  wait_healthy frontend 30
}
# Release directory the current pointer names (e.g. releases/V1.0.1.6), empty when there is none.
current_release_link() {
  readlink "$DATA_ROOT/current" 2>/dev/null || true
}
# Point current at a release directory (relative link such as releases/V1.0.1.6), or remove it when empty.
set_current_release() {
  if [ -n "$1" ]; then
    ln -sfn "$1" "$DATA_ROOT/current" || die "cannot point $DATA_ROOT/current at $1"
  else
    rm -f "$DATA_ROOT/current" || die "cannot remove $DATA_ROOT/current"
  fi
}
restore_env_file() {
  [ -f "$1" ] || die "env backup not found: $1"
  (umask 077 && cp "$1" "$CONFIG_ROOT/.env.restore.$$") || die "cannot copy $1"
  chmod 0600 "$CONFIG_ROOT/.env.restore.$$"
  mv "$CONFIG_ROOT/.env.restore.$$" "$CONFIG_ROOT/.env" || die "cannot restore $CONFIG_ROOT/.env"
}
# Re-read .env for Compose: image variables exported from a newer release.env must not survive in the shell,
# because shell values win over --env-file. Missing keys fall back to the values passed as OLD_<KEY>.
reload_env_for_compose() {
  for key in $NUS_IMAGE_KEYS; do
    unset "$key"
  done
  load_env "$CONFIG_ROOT/.env"
  for key in $NUS_IMAGE_KEYS; do
    current_value=$(eval "printf '%s' \"\${$key:-}\"")
    fallback=$(eval "printf '%s' \"\${OLD_$key:-}\"")
    [ -n "$current_value" ] || [ -z "$fallback" ] || export "$key=$fallback"
  done
}
# Free space in KiB of the filesystem holding a path, and that filesystem's name.
free_kb() { df -Pk "$1" | awk 'NR == 2 { print $4 }'; }
filesystem_of() { df -Pk "$1" | awk 'NR == 2 { print $1 }'; }
# Usage: require_free_space PATH NEEDED_KIB WHAT
require_free_space() {
  avail=$(free_kb "$1")
  case "$avail" in ''|*[!0-9]*) die "cannot read free disk space of $1" ;; esac
  [ "$avail" -ge "$2" ] || die "not enough free disk space on $1 for $3: $((avail / 1024)) MiB free, $(($2 / 1024)) MiB needed; free space first (or lower NUS_UPGRADE_MIN_FREE_MB if only the minimum is not met)"
  log "Disk space on $1: $((avail / 1024)) MiB free, $(($2 / 1024)) MiB needed for $3"
}

# Upgrade records: $DATA_ROOT/upgrades/<UTC time>-<to release>.env, root only, KEY='value' lines without secrets.
upgrade_record_dir() { printf '%s/upgrades' "$DATA_ROOT"; }
record_set() {
  case "$3" in *"'"*|*'
'*) die "unsupported character in upgrade record value for $2" ;; esac
  env_file_set "$1" "$2" "'$3'"
}
# Usage: find_upgrade_record KEY VALUE [STATUS]; prints the newest record with KEY=VALUE and STATUS (default completed).
find_upgrade_record() {
  record_dir=$(upgrade_record_dir)
  [ -d "$record_dir" ] || return 0
  find "$record_dir" -maxdepth 1 -type f -name '*.env' | sort -r | while IFS= read -r record; do
    [ "$(release_value "$record" "$1")" = "$2" ] || continue
    [ "$(release_value "$record" STATUS)" = "${3:-completed}" ] || continue
    printf '%s\n' "$record"
  done | head -n 1
}
