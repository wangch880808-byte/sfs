#!/bin/sh
set -eu
ROOT=$(cd -- "$(dirname "$0")" && pwd)
export NUS_RELEASE_ROOT="$ROOT"
. "$ROOT/lib/common.sh"
require_root
require_ubuntu
requested_site_url=${NUS_SITE_URL:-}
requested_skywalking_url=${NUS_SKYWALKING_URL:-}
skywalking_ui_port=${NUS_SKYWALKING_UI_PORT:-8082}
case "$skywalking_ui_port" in
  ''|*[!0-9]*) die 'NUS_SKYWALKING_UI_PORT must be numeric' ;;
esac
[ -f "$ROOT/SHA256SUMS" ] || die 'SHA256SUMS missing'
(cd "$ROOT" && sha256sum --check SHA256SUMS) || die 'release checksum failed'
. "$ROOT/release.env"
: "${BASELINE_VERSION:?BASELINE_VERSION missing from release.env}"
case "$BASELINE_VERSION" in
  ''|*[!0-9]*) die 'BASELINE_VERSION must be numeric' ;;
esac
if ! command -v docker >/dev/null 2>&1; then
  set -- "$ROOT"/apt/*.deb
  [ -f "$1" ] || die 'Docker is missing and no offline .deb files are present in apt/'
  apt_stage=$(mktemp -d /var/tmp/nus-apt.XXXXXX)
  cleanup_apt_stage() { rm -rf "$apt_stage"; }
  trap cleanup_apt_stage 0 1 2 15
  cp "$ROOT"/apt/*.deb "$apt_stage/"
  chmod 0755 "$apt_stage"
  chmod 0644 "$apt_stage"/*.deb
  DEBIAN_FRONTEND=noninteractive dpkg -i "$apt_stage"/*.deb
  cleanup_apt_stage
  trap - 0 1 2 15
fi
mkdir -p "$DATA_ROOT"/config "$DATA_ROOT"/data/mysql "$DATA_ROOT"/data/redis "$DATA_ROOT"/data/elasticsearch "$DATA_ROOT"/data/horizon "$DATA_ROOT"/data/uploads "$DATA_ROOT"/data/tmp "$DATA_ROOT"/logs/backend "$DATA_ROOT"/backups
chmod 0755 "$DATA_ROOT/config"
chown -R 10001:10001 "$DATA_ROOT/data/uploads" "$DATA_ROOT/data/tmp" "$DATA_ROOT/logs/backend"
chown -R 1000:0 "$DATA_ROOT/data/elasticsearch"
chown -R 100:101 "$DATA_ROOT/data/horizon"
printf '%s\n' 'vm.max_map_count=262144' > /etc/sysctl.d/99-nus-observability.conf
sysctl -q -w vm.max_map_count=262144
if [ ! -f "$CONFIG_ROOT/.env" ]; then
  site_url=$requested_site_url
  if [ -z "$site_url" ]; then
    site_host=$(hostname -I | awk '{ print $1 }')
    site_url="http://${site_host:-127.0.0.1}"
  fi
  case "$site_url" in
    http://*|https://*) ;;
    *) die 'NUS_SITE_URL must start with http:// or https://' ;;
  esac
  case "$site_url" in
    https://*) monitor_cookie_secure=true ;;
    *) monitor_cookie_secure=false ;;
  esac
  skywalking_url=$requested_skywalking_url
  if [ -z "$skywalking_url" ]; then
    skywalking_url=$(default_skywalking_url "$site_url" "$skywalking_ui_port")
  fi
  case "$skywalking_url" in
    http://*|https://*) ;;
    *) die 'NUS_SKYWALKING_URL must start with http:// or https://' ;;
  esac
  umask 077
  cat > "$CONFIG_ROOT/.env" <<EOF
DATA_ROOT=$DATA_ROOT
CONFIG_ROOT=$CONFIG_ROOT
COMPOSE_PROJECT_NAME=nus
BACKEND_IMAGE=$BACKEND_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
MYSQL_IMAGE=$MYSQL_IMAGE
REDIS_IMAGE=$REDIS_IMAGE
ELASTICSEARCH_IMAGE=$ELASTICSEARCH_IMAGE
SKYWALKING_OAP_IMAGE=$SKYWALKING_OAP_IMAGE
SKYWALKING_UI_IMAGE=$SKYWALKING_UI_IMAGE
NUS_DB_NAME=nus
NUS_DB_USERNAME=nus
NUS_DB_PASSWORD=$(openssl rand -hex 24)
NUS_DB_ROOT_PASSWORD=$(openssl rand -hex 24)
NUS_REDIS_PASSWORD=$(openssl rand -hex 24)
NUS_MONITOR_USERNAME=monitor
NUS_MONITOR_PASSWORD=$(openssl rand -hex 16)
NUS_MONITOR_COOKIE_SECURE=$monitor_cookie_secure
NUS_MONITOR_TICKET_TTL=45s
NUS_MONITOR_SESSION_TTL=20m
NUS_DRUID_ENABLED=true
NUS_DRUID_ALLOW=127.0.0.1,172.16.0.0/12
NUS_SPRING_BOOT_ADMIN_ENABLED=true
NUS_SKYWALKING_ENABLED=true
NUS_SKYWALKING_SERVICE_NAME=nus-server
NUS_SKYWALKING_SAMPLE_N_PER_3_SECS=100
NUS_SKYWALKING_RECORD_TTL_DAYS=7
NUS_SKYWALKING_METRICS_TTL_DAYS=15
NUS_SKYWALKING_UI_PORT=$skywalking_ui_port
NUS_SKYWALKING_URL=$skywalking_url
NUS_API_ENCRYPT_REQUEST_KEY=$(openssl rand -hex 16)
NUS_API_ENCRYPT_RESPONSE_KEY=$(openssl rand -hex 16)
NUS_MYBATIS_ENCRYPTOR_PASSWORD=$(openssl rand -hex 24)
NUS_AI_EXTERNAL_STUDENT_DATA_ENABLED=true
NUS_AI_REVIEW_ENABLED=true
NUS_AI_MAX_CONCURRENT=4
NUS_AI_CALL_TIMEOUT=120s
NUS_AI_CONNECT_TIMEOUT=10s
NUS_AI_REVIEW_TASK_POOL_SIZE=2
NUS_AI_REVIEW_TASK_QUEUE_CAPACITY=100
NUS_SITE_URL=$site_url
EOF
  chmod 0600 "$CONFIG_ROOT/.env"
fi
ensure_monitor_credentials
if ! grep -q '^NUS_MONITOR_COOKIE_SECURE=' "$CONFIG_ROOT/.env"; then
  case "$NUS_SITE_URL" in
    https://*) printf '%s\n' 'NUS_MONITOR_COOKIE_SECURE=true' >> "$CONFIG_ROOT/.env" ;;
    *) printf '%s\n' 'NUS_MONITOR_COOKIE_SECURE=false' >> "$CONFIG_ROOT/.env" ;;
  esac
fi
ensure_env_default 'NUS_MONITOR_TICKET_TTL=45s'
ensure_env_default 'NUS_MONITOR_SESSION_TTL=20m'
ensure_env_default 'NUS_DRUID_ALLOW=127.0.0.1,172.16.0.0/12'
ensure_env_default 'NUS_SKYWALKING_UI_PORT=8082'
if ! grep -q '^NUS_SKYWALKING_URL=' "$CONFIG_ROOT/.env"; then
  set -a
  . "$CONFIG_ROOT/.env"
  set +a
  printf 'NUS_SKYWALKING_URL=%s\n' "$(default_skywalking_url "$NUS_SITE_URL" "$NUS_SKYWALKING_UI_PORT")" >> "$CONFIG_ROOT/.env"
fi
# Remove bootstrap flags left by older or interrupted installers. They must never
# become part of the long-lived service configuration.
sed -i '/^NUS_ADMIN_BOOTSTRAP_ENABLED=/d;/^NUS_ADMIN_BOOTSTRAP_PASSWORD_FILE=/d' "$CONFIG_ROOT/.env"
admin_password_source=${NUS_ADMIN_PASSWORD_FILE:-}
unset NUS_ADMIN_BOOTSTRAP_ENABLED NUS_ADMIN_BOOTSTRAP_PASSWORD_FILE
set -a
. "$CONFIG_ROOT/.env"
set +a
set -a
. "$ROOT/release.env"
set +a
sync_image_tags
docker load -i "$ROOT/images.tar" >/dev/null
ensure_horizon_credentials
set -a
. "$CONFIG_ROOT/.env"
set +a
compose up -d mysql redis elasticsearch skywalking-oap skywalking-ui
wait_healthy mysql 60
wait_healthy elasticsearch 90
wait_healthy skywalking-oap 90
wait_healthy skywalking-ui 60
tables=$(MYSQL_PWD=$NUS_DB_ROOT_PASSWORD compose exec -T -e MYSQL_PWD mysql mysql -uroot -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$NUS_DB_NAME';")
if [ "$tables" -eq 0 ]; then
  MYSQL_PWD=$NUS_DB_ROOT_PASSWORD compose exec -T -e MYSQL_PWD mysql mysql -uroot "$NUS_DB_NAME" < "$ROOT/$BASELINE_SQL"
  FLYWAY_NETWORK="${COMPOSE_PROJECT_NAME}_nus-internal" FLYWAY_IMAGE="$FLYWAY_IMAGE" MIGRATIONS_DIR="$ROOT/database/migrations" NUS_DB_USERNAME="$NUS_DB_USERNAME" NUS_DB_PASSWORD="$NUS_DB_PASSWORD" NUS_DB_NAME="$NUS_DB_NAME" "$ROOT/lib/flyway.sh" baseline -baselineVersion="$BASELINE_VERSION" -baselineDescription=ry261-test.sql
else
  MYSQL_PWD=$NUS_DB_ROOT_PASSWORD compose exec -T -e MYSQL_PWD mysql mysql -uroot -Nse "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$NUS_DB_NAME' AND table_name='flyway_schema_history';" | grep -q '^1$' || die 'non-empty database without trusted Flyway history'
fi
FLYWAY_NETWORK="${COMPOSE_PROJECT_NAME}_nus-internal" FLYWAY_IMAGE="$FLYWAY_IMAGE" MIGRATIONS_DIR="$ROOT/database/migrations" NUS_DB_USERNAME="$NUS_DB_USERNAME" NUS_DB_PASSWORD="$NUS_DB_PASSWORD" NUS_DB_NAME="$NUS_DB_NAME" "$ROOT/lib/flyway.sh" migrate
MYSQL_CONTAINER=$(compose ps -q mysql) NUS_DB_ROOT_PASSWORD="$NUS_DB_ROOT_PASSWORD" NUS_DB_NAME="$NUS_DB_NAME" NUS_SITE_URL="$NUS_SITE_URL" NUS_SKYWALKING_URL="$NUS_SKYWALKING_URL" NUS_FILE_BASE_PATH=/app/uploads "$ROOT/lib/configure-site.sh"
if [ ! -f "$CONFIG_ROOT/.admin-bootstrap-complete" ]; then
  compose stop frontend backend >/dev/null 2>&1 || true
  compose rm -f backend >/dev/null 2>&1 || true
  if [ -n "$admin_password_source" ]; then
    [ -f "$admin_password_source" ] || die 'NUS_ADMIN_PASSWORD_FILE does not exist'
    [ "$(stat -c '%a' "$admin_password_source")" = 600 ] || die 'admin password file must be mode 0600'
    cp "$admin_password_source" "$CONFIG_ROOT/admin-password"
  else
    [ -t 0 ] || die 'interactive admin password input required (or set NUS_ADMIN_PASSWORD_FILE to a 0600 file)'
    printf 'New admin password: ' >&2
    stty -echo
    read -r password
    stty echo
    printf '\nConfirm admin password: ' >&2
    stty -echo
    read -r confirmation
    stty echo
    printf '\n' >&2
    [ "$password" = "$confirmation" ] || die 'admin passwords do not match'
    printf '%s\n' "$password" > "$CONFIG_ROOT/admin-password"
    unset password confirmation
  fi
  chmod 0600 "$CONFIG_ROOT/admin-password"
  chown 10001:10001 "$CONFIG_ROOT/admin-password"
  if ! password_length=$(LC_ALL=C awk 'NR == 1 { sub(/\r$/, ""); print length($0) } NR > 1 { exit 1 }' "$CONFIG_ROOT/admin-password"); then
    rm -f "$CONFIG_ROOT/admin-password"
    die 'admin password file must contain exactly one line'
  fi
  case "$password_length" in
    12|13|14|15|16) ;;
    *) rm -f "$CONFIG_ROOT/admin-password"; die 'admin password length must be between 12 and 16 characters' ;;
  esac
  cleanup_admin_password() { rm -f "$CONFIG_ROOT/admin-password"; }
  trap cleanup_admin_password 0 1 2 15
  if ! compose run --rm --no-deps \
      -e NUS_ADMIN_BOOTSTRAP_ENABLED=true \
      -e NUS_ADMIN_BOOTSTRAP_PASSWORD_FILE=/run/nus/config/admin-password \
      -e NUS_SERVER_PORT=0 \
      -e NUS_MANAGEMENT_PORT=0 \
      backend; then
    die 'administrator password bootstrap failed; see the output above'
  fi
  cleanup_admin_password
  trap - 0 1 2 15
  touch "$CONFIG_ROOT/.admin-bootstrap-complete"
  chmod 0600 "$CONFIG_ROOT/.admin-bootstrap-complete"
fi
compose up -d backend
wait_healthy backend 90
compose up -d frontend
wait_healthy frontend 30
activate_release "$ROOT"
log 'Installation completed'
