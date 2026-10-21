#!/usr/bin/env bash
# 本机联调环境：一次性 MySQL/Redis（只绑定 127.0.0.1），指定文件的基线经 Flyway 升到最新，后端以 prod 配置运行。
# 只用于本机测试；口令随机生成，保存在状态目录（默认 ~/.cache/nus-e2e，权限 0700），不打印、不进仓库。
#
# 用法：
#   local-env.sh up [flyway|none]   建库并导入当前 NUS-doc 基线，flyway 时升到最新迁移
#     BASELINE_FILE=/path/to/file.sql 可指定旧基线；版本从文件头的 Flyway baseline version 标记读取
#   local-env.sh bootstrap          一次性模式运行后端：用随机口令替换基线里的管理员口令，完成后自动退出
#   local-env.sh backend            后台启动后端（端口 NUS_E2E_APP_PORT，默认 48280）
#   local-env.sh status | down      查看 / 停止并删除本套环境的容器与后端进程
# 同一台机器上再起一套环境（例如另一个工作区），以下变量要一起换掉，否则会删掉或杀掉别人那套：
#   NUS_E2E_STATE（状态目录）、NUS_E2E_MYSQL_NAME / NUS_E2E_REDIS_NAME（容器名）、
#   NUS_E2E_DB_PORT / NUS_E2E_REDIS_PORT / NUS_E2E_APP_PORT / NUS_E2E_MGMT_PORT（端口）、NUS_E2E_JAR（后端 jar）
# 等待时长：NUS_E2E_BOOTSTRAP_TIMEOUT（引导，默认 300 秒）、NUS_E2E_BACKEND_TIMEOUT（启动，默认 300 秒）
# 端到端脚本：NUS_E2E_BASE_URL=http://127.0.0.1:48280 NUS_E2E_ADMIN_USERNAME=admin \
#   NUS_E2E_ADMIN_PASSWORD_FILE=~/.cache/nus-e2e/admin.pw python3 deploy/tests/e2e/e2e_v1015.py
set -euo pipefail
ROOT=$(cd -- "$(dirname "$0")/../../.." && pwd)
STATE=${NUS_E2E_STATE:-$HOME/.cache/nus-e2e}
MYSQL=${NUS_E2E_MYSQL_NAME:-nus-e2e-mysql}
REDIS=${NUS_E2E_REDIS_NAME:-nus-e2e-redis}
DB_PORT=${NUS_E2E_DB_PORT:-13306}
REDIS_PORT=${NUS_E2E_REDIS_PORT:-16379}
APP_PORT=${NUS_E2E_APP_PORT:-48280}
MGMT_PORT=${NUS_E2E_MGMT_PORT:-48281}
JAR=${NUS_E2E_JAR:-$ROOT/NUS-backend/nus-server/target/nus-server.jar}
case "$JAR" in /*) ;; *) JAR="$PWD/$JAR" ;; esac
BOOTSTRAP_TIMEOUT=${NUS_E2E_BOOTSTRAP_TIMEOUT:-300}
BACKEND_TIMEOUT=${NUS_E2E_BACKEND_TIMEOUT:-300}
FLYWAY_IMAGE=flyway/flyway:10.20.1-alpine

mkdir -p "$STATE/logs" "$STATE/ai-work" && chmod 700 "$STATE"

secret() {
  [ -s "$1" ] && return 0
  python3 -c 'import secrets; print(secrets.token_urlsafe(24))' > "$1"
  chmod 600 "$1"
}

# 管理员口令须满足后端引导规则：12～16 位，含大小写字母、数字与特殊字符，且不含账号名
admin_secret() {
  [ -s "$1" ] && return 0
  python3 - "$1" <<'PY'
import os, secrets, string, sys
special = '!@#%^*-_=+'
alphabet = string.ascii_letters + string.digits + special
while True:
    pw = ''.join(secrets.choice(alphabet) for _ in range(16))
    if (any(c.islower() for c in pw) and any(c.isupper() for c in pw) and any(c.isdigit() for c in pw)
            and any(c in special for c in pw) and 'admin' not in pw.lower()):
        break
with open(sys.argv[1], 'w') as fh:
    fh.write(pw + '\n')
os.chmod(sys.argv[1], 0o600)
PY
}

mysql_exec() { docker exec -i -e MYSQL_PWD="$(cat "$STATE/db.pw")" "$MYSQL" mysql --default-character-set=utf8mb4 -uroot "$@"; }

flyway() {
  docker run --rm --pull=never --network "container:$MYSQL" -v "$ROOT/deploy/database/migrations:/flyway/sql:ro" \
    "$FLYWAY_IMAGE" -url="jdbc:mysql://127.0.0.1:3306/nus?useSSL=false&allowPublicKeyRetrieval=true" \
    -user=root -password="$(cat "$STATE/db.pw")" -locations=filesystem:/flyway/sql "$@"
}

up() {
  local mode=${1:-flyway} from baseline_file=${BASELINE_FILE:-$ROOT/NUS-doc/3-数据库文件/ry261-test.sql}
  [ -r "$baseline_file" ] && [ -f "$baseline_file" ] || { echo "基线文件不存在或不可读：$baseline_file" >&2; return 2; }
  from=$(head -n 50 "$baseline_file" | sed -n 's/^-- Flyway baseline version: \([0-9][0-9]*\)[[:space:]]*$/\1/p')
  [[ "$from" =~ ^[0-9]+$ ]] || { echo "基线文件头部须有唯一的 -- Flyway baseline version: N 标记：$baseline_file" >&2; return 2; }
  cp "$baseline_file" "$STATE/baseline.sql"
  secret "$STATE/db.pw"; admin_secret "$STATE/admin.pw"; secret "$STATE/monitor.pw"; secret "$STATE/redis.pw"
  docker rm -f "$MYSQL" "$REDIS" >/dev/null 2>&1 || true
  docker run -d --pull=never --name "$MYSQL" -p "127.0.0.1:$DB_PORT:3306" --tmpfs /var/lib/mysql \
    -v "$STATE/db.pw:/run/nus/db.pw:ro" -e MYSQL_ROOT_PASSWORD_FILE=/run/nus/db.pw mysql:8.4.6 >/dev/null
  docker run -d --pull=never --name "$REDIS" -p "127.0.0.1:$REDIS_PORT:6379" -v "$STATE/redis.pw:/run/nus/redis.pw:ro" \
    redis:7.4.6-alpine sh -c 'exec redis-server --requirepass "$(cat /run/nus/redis.pw)"' >/dev/null
  until docker logs "$MYSQL" 2>&1 | grep -q 'ready for connections.*port: 3306'; do sleep 2; done
  mysql_exec -e 'CREATE DATABASE nus DEFAULT CHARACTER SET utf8mb4'
  mysql_exec nus < "$STATE/baseline.sql"
  rm -f "$STATE/baseline.sql"
  if [ "$mode" = flyway ]; then
    flyway baseline -baselineVersion="$from" -baselineDescription=ry261-test.sql >/dev/null
    flyway migrate | grep -E 'Successfully applied|now at version|is up to date' || true
  fi
  echo "db ready (baseline $from, $mode)"
}

run_backend() {
  local bootstrap=$1
  stop_backend
  # 端口被其他程序占用时，请求会打到别的服务上，测试结果全错；直接报错让人换端口
  if command -v lsof >/dev/null 2>&1 && lsof -nP -iTCP:"$APP_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "端口 $APP_PORT 已被其他程序占用，请设置 NUS_E2E_APP_PORT（及 NUS_E2E_MGMT_PORT）换一个端口：" >&2
    lsof -nP -iTCP:"$APP_PORT" -sTCP:LISTEN >&2
    exit 1
  fi
  [ -f "$JAR" ] || { echo "后端 jar 不存在：${JAR}（先在 NUS-backend 下打包，见 docs/03-开发/环境搭建.md）" >&2; exit 1; }
  # 在状态目录里启动：本地文件存储用相对路径，否则上传文件会写进仓库
  cd "$STATE"
  NUS_DB_HOST=127.0.0.1 NUS_DB_PORT=$DB_PORT NUS_DB_NAME=nus NUS_DB_USERNAME=root \
  NUS_DB_PASSWORD="$(cat "$STATE/db.pw")" NUS_DB_USE_SSL=false \
  NUS_REDIS_HOST=127.0.0.1 NUS_REDIS_PORT=$REDIS_PORT NUS_REDIS_PASSWORD="$(cat "$STATE/redis.pw")" NUS_REDIS_DATABASE=0 \
  NUS_MONITOR_PASSWORD="$(cat "$STATE/monitor.pw")" NUS_API_ENCRYPT_ENABLED=false \
  NUS_ADMIN_BOOTSTRAP_ENABLED=$bootstrap NUS_ADMIN_BOOTSTRAP_USERNAME=admin NUS_ADMIN_BOOTSTRAP_PASSWORD_FILE="$STATE/admin.pw" \
  NUS_SERVER_PORT=$APP_PORT NUS_MANAGEMENT_PORT=$MGMT_PORT NUS_LOG_FILE="$STATE/logs/nus.log" \
  NUS_AI_REVIEW_WORK_DIR="$STATE/ai-work" NUS_SPRING_BOOT_ADMIN_ENABLED=false NUS_DRUID_ENABLED=false \
  NO_PROXY=127.0.0.1,localhost no_proxy=127.0.0.1,localhost \
  nohup java -jar "$JAR" --spring.profiles.active=prod --server.port="$APP_PORT" --nus.captcha.enable=false \
    > "$STATE/logs/stdout.log" 2>&1 &
  echo $! > "$STATE/backend.pid"
}

# 只停本套环境自己启动的后端（记录在 backend.pid），不按端口去杀，免得误杀别人的进程。
# 旧进程要等它真正退出、端口释放后再启动新的；否则就绪检查会打到正在退出的旧进程上
stop_backend() {
  local pid
  pid=$(cat "$STATE/backend.pid" 2>/dev/null || true)
  if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null && ps -o command= -p "$pid" | grep -q 'nus-server.jar'; then
    kill "$pid" 2>/dev/null || true
    for _ in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill -0 "$pid" 2>/dev/null && { kill -9 "$pid" 2>/dev/null || true; sleep 1; }
  fi
  rm -f "$STATE/backend.pid"
}

wait_http() {
  local pid
  pid=$(cat "$STATE/backend.pid" 2>/dev/null || true)
  for _ in $(seq 1 $((BACKEND_TIMEOUT / 2))); do
    # 只认本次启动的进程：进程已退出就直接失败
    [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null && return 1
    [ "$(curl -s --noproxy '*' -o /dev/null -w '%{http_code}' "http://127.0.0.1:$APP_PORT/admin-api/system/auth/social-login-config")" = 200 ] && return 0
    grep -q 'Application run failed' "$STATE/logs/stdout.log" 2>/dev/null && return 1
    sleep 2
  done
  return 1
}

cmd=${1:-}; shift || true
case "$cmd" in
  up) up "$@" ;;
  bootstrap)
    run_backend true
    for _ in $(seq 1 $((BOOTSTRAP_TIMEOUT / 2))); do
      grep -q 'Administrator bootstrap completed' "$STATE/logs/stdout.log" 2>/dev/null && { stop_backend; echo 'admin bootstrap completed'; exit 0; }
      grep -q 'Application run failed' "$STATE/logs/stdout.log" 2>/dev/null && { echo 'bootstrap failed, see logs' >&2; exit 1; }
      sleep 2
    done
    stop_backend
    echo "bootstrap 在 ${BOOTSTRAP_TIMEOUT} 秒内没有完成，已停掉后端；机器较忙时可设 NUS_E2E_BOOTSTRAP_TIMEOUT=600 重试，日志在 $STATE/logs/" >&2; exit 1 ;;
  backend) run_backend false; wait_http && echo "backend up on 127.0.0.1:$APP_PORT" || { echo 'backend failed, see logs' >&2; exit 1; } ;;
  status)
    docker ps --format '{{.Names}} {{.Status}}' | grep -E "^($MYSQL|$REDIS) " || echo '本套环境的容器没有运行'
    pid=$(cat "$STATE/backend.pid" 2>/dev/null || true)
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then echo "后端运行中：pid ${pid}，端口 ${APP_PORT}"; else echo '后端没有运行'; fi ;;
  down) stop_backend; docker rm -f "$MYSQL" "$REDIS" >/dev/null 2>&1 || true; echo down ;;
  *) echo "usage: $0 up [flyway|none] | bootstrap | backend | status | down" >&2; exit 2 ;;
esac
