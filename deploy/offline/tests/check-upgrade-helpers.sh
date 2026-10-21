#!/usr/bin/env bash
# Checks the upgrade helpers in deploy/offline/lib/common.sh; run with: bash deploy/offline/tests/check-upgrade-helpers.sh
# - clear_permission_caches against a throwaway Redis from a local image only (--pull=never, --network none);
#   a docker wrapper records every argv to prove the password never reaches a command line;
#   clear_restored_db_caches (after a database restore) on the same Redis;
# - release_value, refuse_reused_image, running_image and backup_env_file with a temporary CONFIG_ROOT;
# - env_file_set, sync_image_tags into a staged copy (ENV_EDIT_FILE), reload_env_for_compose, find_upgrade_record.
# The helpers run under dash when available (Ubuntu's /bin/sh); override with NUS_TEST_SH, and the image with
# NUS_TEST_REDIS_IMAGE (default redis:7.4.6-alpine, the production version).
# shellcheck disable=SC2015  # "check && pass || bad": pass only prints, so bad runs only when the check fails
# shellcheck disable=SC2016  # single-quoted scripts are expanded by the inner shell on purpose
set -uo pipefail
ROOT=$(cd -- "$(dirname "$0")/../../.." && pwd)
COMMON="$ROOT/deploy/offline/lib/common.sh"
REDIS_IMAGE=${NUS_TEST_REDIS_IMAGE:-redis:7.4.6-alpine}
TEST_SH=${NUS_TEST_SH:-$(command -v dash || command -v sh)}
REAL_DOCKER=$(command -v docker)
TMP_BASE=${TMPDIR:-/tmp}
WORK=$(mktemp -d "${TMP_BASE%/}/nus-upgrade-helpers-check.XXXXXX")
NAME="nus-redis-check-$(openssl rand -hex 4)"
PASSWORD=$(openssl rand -hex 24)   # throwaway, passed only through the environment and never printed
failures=0
pass() { printf 'PASS %s\n' "$*"; }
bad() { printf 'FAIL %s\n' "$*"; failures=$((failures + 1)); }
mode_of() { stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"; }
rcli() { REDISCLI_AUTH=$PASSWORD "$REAL_DOCKER" exec -i -e REDISCLI_AUTH "$NAME" redis-cli "$@"; }
cleanup() { "$REAL_DOCKER" rm -f "$NAME" >/dev/null 2>&1; rm -rf "$WORK"; }
trap cleanup EXIT

# --- clear_permission_caches -------------------------------------------------------------------------
REDIS_PASS=$PASSWORD "$REAL_DOCKER" run -d --pull=never --name "$NAME" --network none -e REDIS_PASS "$REDIS_IMAGE" \
  sh -c 'exec redis-server --appendonly no --requirepass "$REDIS_PASS"' >/dev/null \
  || { printf 'cannot start %s from a local image\n' "$REDIS_IMAGE" >&2; exit 1; }
for _ in $(seq 1 30); do rcli ping 2>/dev/null | grep -q PONG && break; sleep 1; done

targets=('permission_menu_ids:system:user:list' 'permission_menu_ids:infra:data-source-config:query'
  "permission_menu_ids:weird key \"q\"'s" 'menu_role_ids:1255' 'menu_role_ids:5110' 'user_role_ids:1'
  'user_role_ids:250' 'role:1' 'role:161' 'notify_template:edu.student.homework.redo'
  'infra_config_by_key:edu.pass.score' 'edu_current_semester:current' 'edu_semester_options:SimpleKey []'
  'capacity_indicator_tree:all' 'system_dict_data:edu_user_type')
keep=('oauth2_access_token:6f1c2d' 'oauth_client:1' 'captcha:abc' 'dept_children_ids:100'
  'notify_templatex:1' 'rolex:1' 'my_role:1' 'permission_menu_ids' 'menu_role_ids' 'notify_template'
  'user_role_idsX:1' 'role' 'social_auth_state:x' 'infra_config_by_keyX:1' 'system_dict_data')
{
  for key in "${targets[@]}" "${keep[@]}"; do
    printf 'SET "%s" 1\n' "$(printf '%s' "$key" | sed 's/\\/\\\\/g; s/"/\\"/g')"
  done
  for i in $(seq 1 3000); do printf 'SET role:bulk:%s 1\n' "$i"; done
} | rcli >/dev/null
total_targets=$(( ${#targets[@]} + 3000 ))
total_keys=$(( total_targets + ${#keep[@]} ))
[ "$(rcli DBSIZE)" = "$total_keys" ] && pass "seeded $total_targets cache keys and ${#keep[@]} other keys" || bad 'seeding'

mkdir -p "$WORK/bin"
cat > "$WORK/bin/docker" <<EOF
#!/bin/sh
printf '%s\n' "\$*" >> "$WORK/argv.log"
exec "$REAL_DOCKER" "\$@"
EOF
chmod +x "$WORK/bin/docker"
run_cleanup() {  # redis password to use, [helper]
  PATH="$WORK/bin:$PATH" NUS_REDIS_PASSWORD="$1" CONTAINER="$NAME" COMMON="$COMMON" HELPER="${2:-clear_permission_caches}" \
    "$TEST_SH" -c '
    set -eu
    . "$COMMON"
    compose() { [ "$1 $2 $3" = "ps -q redis" ] && printf "%s\n" "$CONTAINER"; }
    "$HELPER"
  '
}

if run_cleanup wrong-password >"$WORK/out" 2>&1; then
  bad 'wrong password must fail'
else
  grep -q 'failed to clear menu, permission and data caches' "$WORK/out" && [ "$(rcli DBSIZE)" = "$total_keys" ] \
    && pass 'wrong password exits non-zero and deletes nothing' || bad 'wrong password handling'
fi
if run_cleanup "$PASSWORD" >"$WORK/out" 2>&1; then
  grep -q "Cleared $total_targets cached menu/permission/notification-template/config/semester/indicator/dict entries" "$WORK/out" \
    && pass "cleanup reports $total_targets removed entries" || bad "cleanup count: $(cat "$WORK/out")"
else
  bad "cleanup run failed: $(cat "$WORK/out")"
fi
remaining=$(rcli --scan | sort)
expected=$(printf '%s\n' "${keep[@]}" | sort)
[ "$remaining" = "$expected" ] \
  && pass 'only permission, role, notification-template, config, semester, indicator and dict caches removed; tokens and other keys kept' \
  || bad "remaining keys differ: $(diff <(printf '%s\n' "$expected") <(printf '%s\n' "$remaining") | tr '\n' ' ')"
run_cleanup "$PASSWORD" >"$WORK/out" 2>&1 && grep -q 'Cleared 0 cached' "$WORK/out" \
  && pass 'second run removes nothing' || bad 'idempotent run'
if grep -qF "$PASSWORD" "$WORK/argv.log" "$WORK/out"; then
  bad 'password leaked into docker argv or output'
else
  pass "password absent from all $(wc -l < "$WORK/argv.log" | tr -d ' ') recorded docker argv lines and from the output"
fi
grep -q 'exec -e REDISCLI_AUTH ' "$WORK/argv.log" && pass 'password handed over as -e REDISCLI_AUTH (value from environment)' \
  || bad 'REDISCLI_AUTH not used'

# After a database restore every RedisKeyConstants cache goes; tokens, captcha and look-alike keys stay.
restored_targets=('permission_menu_ids:x' 'menu_role_ids:1' 'user_role_ids:1' 'role:1' 'notify_template:a'
  'dept_children_ids:100' 'oauth_client:1' 'mail_account:1' 'mail_template:a' 'sms_template:a' 'wxa_subscribe_template:a'
  'infra_config_by_key:a' 'edu_current_semester:a' 'edu_semester_options:a' 'capacity_indicator_tree:a' 'system_dict_data:a')
restored_keep=('oauth2_access_token:6f1c2d' 'oauth2_refresh_token:1' 'captcha:abc' 'notify_templatex:1' 'rolex:1'
  'dept_children_idsX:1' 'oauth_clientX' 'social_auth_state:x')
rcli FLUSHALL >/dev/null
for key in "${restored_targets[@]}" "${restored_keep[@]}"; do rcli SET "$key" 1 >/dev/null; done
if run_cleanup "$PASSWORD" clear_restored_db_caches >"$WORK/out" 2>&1; then
  remaining=$(rcli --scan | sort)
  expected=$(printf '%s\n' "${restored_keep[@]}" | sort)
  [ "$remaining" = "$expected" ] && grep -q "Cleared ${#restored_targets[@]} cached entries of the restored database" "$WORK/out" \
    && pass 'clear_restored_db_caches removes all 11 Spring caches, keeps tokens and look-alike keys' \
    || bad "restored-db cleanup: $(cat "$WORK/out"); left: $(printf '%s' "$remaining" | tr '\n' ' ')"
else
  bad "restored-db cleanup failed: $(cat "$WORK/out")"
fi

# --- release_value, refuse_reused_image, running_image, backup_env_file --------------------------------
CONF="$WORK/config"
mkdir -p "$CONF"
printf 'RELEASE_VERSION=V1.0.1.4\nRELEASE_KIND=application-update\nBACKEND_IMAGE=nus-backend:V1.0.1.4\n' > "$CONF/release.env"
printf 'BACKEND_IMAGE=nus-backend:V1.0.1.3\nFRONTEND_IMAGE=nus-front:V1.0.1.3\nNUS_TEST_SECRET=never-in-log\n' > "$CONF/.env"
chmod 600 "$CONF/.env"
(cd "$CONF" && CONFIG_ROOT="$CONF" COMMON="$COMMON" "$TEST_SH" -c '
set -eu
. "$COMMON"
set -a; . "$CONFIG_ROOT/.env"; set +a
check() { if "$@"; then printf "ok\n"; else printf "no\n"; fi; }
r=$(release_value "$CONFIG_ROOT/release.env" BACKEND_IMAGE); check [ "$r" = nus-backend:V1.0.1.4 ]
r=$(release_value "$CONFIG_ROOT/release.env" FRONTEND_IMAGE); check [ -z "$r" ]
check [ "$BACKEND_IMAGE" = nus-backend:V1.0.1.3 ]
if (refuse_reused_image backend nus-backend:V1.0.1.4 nus-backend:V1.0.1.3 "") >/dev/null 2>&1; then printf "ok\n"
else printf "no\n"; fi
if (refuse_reused_image backend nus-backend:V1.0.1.3 "" nus-backend:V1.0.1.3@sha256:abc) 2>err; then printf "no\n"
else grep -q "already in use" err && printf "ok\n" || printf "no\n"; fi
if (refuse_reused_image frontend nus-front:V1.0.1.3 nus-front:V1.0.1.3 "") 2>/dev/null; then printf "no\n"; else printf "ok\n"; fi
compose() { :; }
r=$(running_image backend); check [ -z "$r" ]
backup_env_file V1.0.1.4 >log
f=$(ls "$CONFIG_ROOT"/.env.before-V1.0.1.4-*)
check cmp -s "$f" "$CONFIG_ROOT/.env"
printf "mode=%s\n" "$(stat -c %a "$f" 2>/dev/null || stat -f %Lp "$f")"
grep -q "Saved current .env to" log && ! grep -q never-in-log log && printf "ok\n" || printf "no\n"
') > "$WORK/helpers" 2>&1
labels=('release_value reads the manifest value' "release_value does not inherit the caller's value"
  'caller environment untouched' 'different image tag accepted' 'tag already running (digest-qualified) refused'
  'tag already in .env refused' 'running_image is empty when the service has no container'
  '.env backup identical to .env')
results=()
while IFS= read -r line; do results+=("$line"); done < <(grep -v '^mode=' "$WORK/helpers")
for i in "${!labels[@]}"; do
  [ "${results[$i]:-}" = ok ] && pass "${labels[$i]}" || bad "${labels[$i]}"
done
grep -qx 'mode=600' "$WORK/helpers" && pass '.env backup has mode 600' || bad '.env backup mode'
[ "${results[8]:-}" = ok ] && pass '.env backup log names the file only' || bad '.env backup log'
[ "${#results[@]}" = 9 ] || bad "unexpected helper output: $(tr '\n' ' ' < "$WORK/helpers")"

# --- env_file_set, staged sync_image_tags, reload_env_for_compose, find_upgrade_record ---------------------------
CONF2="$WORK/config2"
DATA2="$WORK/data2"
mkdir -p "$CONF2" "$DATA2/upgrades"
printf 'A=1\nBACKEND_IMAGE=nus-backend:old\nB=x#y\nBACKEND_IMAGE=dup\n' > "$CONF2/.env"
chmod 600 "$CONF2/.env"
cp "$CONF2/.env" "$CONF2/.env.orig"
printf "STATUS='completed'\nFROM_RELEASE='V1'\nTO_RELEASE='V2'\n" > "$DATA2/upgrades/20260101T000000Z-V2.env"
printf "STATUS='failed-rolled-back'\nFROM_RELEASE='V2'\nTO_RELEASE='V3'\n" > "$DATA2/upgrades/20260102T000000Z-V3.env"
printf "STATUS='completed'\nFROM_RELEASE='V2'\nTO_RELEASE='V3'\n" > "$DATA2/upgrades/20260103T000000Z-V3.env"
CONFIG_ROOT="$CONF2" DATA_ROOT="$DATA2" COMMON="$COMMON" "$TEST_SH" -c '
set -eu
. "$COMMON"
check() { if "$@"; then printf "ok\n"; else printf "no\n"; fi; }
env_file_set "$CONFIG_ROOT/.env" B "a&b#c/d"
check grep -qx "B=a&b#c/d" "$CONFIG_ROOT/.env"
env_file_set "$CONFIG_ROOT/.env" NEW_KEY v
check [ "$(tail -n 1 "$CONFIG_ROOT/.env")" = NEW_KEY=v ]
env_file_set "$CONFIG_ROOT/.env" BACKEND_IMAGE nus-backend:mid
check [ "$(grep "^BACKEND_IMAGE=" "$CONFIG_ROOT/.env")" = BACKEND_IMAGE=nus-backend:mid ]
check [ "$(stat -c %a "$CONFIG_ROOT/.env" 2>/dev/null || stat -f %Lp "$CONFIG_ROOT/.env")" = 600 ]
cp "$CONFIG_ROOT/.env" "$CONFIG_ROOT/.env.next"
before=$(cat "$CONFIG_ROOT/.env")
BACKEND_IMAGE=nus-backend:new FRONTEND_IMAGE=nus-front:new MYSQL_IMAGE=m REDIS_IMAGE=r FLYWAY_IMAGE=f \
  ELASTICSEARCH_IMAGE=e SKYWALKING_OAP_IMAGE=o SKYWALKING_UI_IMAGE=u ENV_EDIT_FILE="$CONFIG_ROOT/.env.next" sync_image_tags
check grep -qx BACKEND_IMAGE=nus-backend:new "$CONFIG_ROOT/.env.next"
check grep -qx SKYWALKING_UI_IMAGE=u "$CONFIG_ROOT/.env.next"
check [ "$(cat "$CONFIG_ROOT/.env")" = "$before" ]
export BACKEND_IMAGE=nus-backend:new FRONTEND_IMAGE=nus-front:new
OLD_FRONTEND_IMAGE=nus-front:fallback
printf "BACKEND_IMAGE=nus-backend:old\n" > "$CONFIG_ROOT/.env"
reload_env_for_compose
check [ "$BACKEND_IMAGE" = nus-backend:old ]
check [ "$FRONTEND_IMAGE" = nus-front:fallback ]
check [ "$(find_upgrade_record TO_RELEASE V3)" = "$DATA_ROOT/upgrades/20260103T000000Z-V3.env" ]
check [ "$(find_upgrade_record FROM_RELEASE V1)" = "$DATA_ROOT/upgrades/20260101T000000Z-V2.env" ]
check [ -z "$(find_upgrade_record TO_RELEASE V9)" ]
check [ "$(find_upgrade_record TO_RELEASE V3 failed-rolled-back)" = "$DATA_ROOT/upgrades/20260102T000000Z-V3.env" ]
' > "$WORK/helpers2" 2>&1
labels2=('env_file_set writes values with & # / literally' 'env_file_set appends a missing key'
  'env_file_set collapses duplicate keys' 'env_file_set keeps mode 600' 'sync_image_tags writes the staged copy'
  'sync_image_tags writes all eight image keys' 'live .env untouched while the copy is staged'
  'reload_env_for_compose drops exported newer tags' 'reload_env_for_compose falls back to OLD_<KEY>'
  'find_upgrade_record picks the newest completed record' 'find_upgrade_record matches FROM_RELEASE'
  'find_upgrade_record is empty without a match' 'find_upgrade_record filters by status')
results2=()
while IFS= read -r line; do results2+=("$line"); done < "$WORK/helpers2"
for i in "${!labels2[@]}"; do
  [ "${results2[$i]:-}" = ok ] && pass "${labels2[$i]}" || bad "${labels2[$i]}"
done
[ "${#results2[@]}" = "${#labels2[@]}" ] || bad "unexpected helper output: $(tr '\n' ' ' < "$WORK/helpers2")"

printf '%s failure(s)\n' "$failures"
[ "$failures" -eq 0 ]
