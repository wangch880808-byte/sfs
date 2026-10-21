#!/bin/sh
set -eu
ROOT=$(cd -- "$(dirname "$0")/../.." && pwd)
VERSION=${1:-$(date -u +%Y%m%dT%H%M%SZ)}
OUT=${2:-$ROOT/releases/nus-offline-$VERSION-ubuntu24.04-amd64}
BASELINE_VERSION=21
: "${BACKEND_IMAGE:?set BACKEND_IMAGE to the backend image for this release}"
: "${FRONTEND_IMAGE:?set FRONTEND_IMAGE to the frontend image for this release}"
MYSQL_IMAGE=${MYSQL_IMAGE:-mysql:8.4.6@sha256:869218921e61d6c3c89820955d63cca42971f0e3e6c1e2792247bbd944ebc6e9}
REDIS_IMAGE=${REDIS_IMAGE:-redis:7.4.6-alpine@sha256:3b73847e72874be07e6657b129a94761662b79bc0f679273757d4218573b2a98}
FLYWAY_IMAGE=${FLYWAY_IMAGE:-flyway/flyway:10.20.1-alpine@sha256:15e105262c902c129e1a637142065654a1fb6d40d4f28fc5c143f88f957672b7}
ELASTICSEARCH_IMAGE=${ELASTICSEARCH_IMAGE:-docker.elastic.co/elasticsearch/elasticsearch:8.17.3@sha256:224c75e346bd745ce908f06a1cbad7bf10988961dcdcdfccb22556b3f856b3f0}
SKYWALKING_OAP_IMAGE=${SKYWALKING_OAP_IMAGE:-apache/skywalking-oap-server:11.0.0@sha256:835a0d8de39af9dda760a5df9a992d982376cf6e8934eb8dd69baf72a1612091}
SKYWALKING_UI_IMAGE=${SKYWALKING_UI_IMAGE:-ghcr.io/apache/skywalking-horizon-ui:1.0.0@sha256:eb08dcf2fe28fc805aaaeb48f25e0b67d141672a0b9a3a174bbfc4135dd6127d}
APT_CACHE_DIR=${APT_CACHE_DIR:-}
latest_migration_version=$(find "$ROOT/deploy/database/migrations" -maxdepth 1 -type f -name 'V*__*.sql' \
  | sed 's#.*/V\([0-9][0-9]*\)__.*#\1#' | sort -n | tail -1)
[ "$latest_migration_version" = "$BASELINE_VERSION" ] || {
  printf '%s\n' "baseline version $BASELINE_VERSION does not match latest migration $latest_migration_version" >&2
  exit 1
}
grep -q "^-- Flyway baseline version: $BASELINE_VERSION$" "$ROOT/NUS-doc/3-数据库文件/ry261-test.sql" || {
  printf '%s\n' "ry261-test.sql is not marked as Flyway baseline version $BASELINE_VERSION" >&2
  exit 1
}
[ -n "$APT_CACHE_DIR" ] && [ -d "$APT_CACHE_DIR" ] || {
  printf '%s\n' 'APT_CACHE_DIR must point to a complete Ubuntu 24.04 amd64 Docker package cache' >&2
  exit 1
}
for package in docker.io docker-compose-v2 containerd runc; do
  find "$APT_CACHE_DIR" -maxdepth 1 -type f -name "${package}_*.deb" | grep -q . || {
    printf '%s\n' "required offline package is missing: $package" >&2
    exit 1
  }
done
[ ! -e "$OUT" ] || {
  printf '%s\n' "release output already exists: $OUT" >&2
  exit 1
}
mkdir -p "$OUT/runtime" "$OUT/database/baseline" "$OUT/database/migrations" "$OUT/lib" "$OUT/apt"
find "$APT_CACHE_DIR" -maxdepth 1 -type f -name '*.deb' -exec cp {} "$OUT/apt/" \;
cp "$ROOT/deploy/compose.yaml" "$OUT/runtime/compose.yaml"
cp "$ROOT/NUS-doc/3-数据库文件/ry261-test.sql" "$OUT/database/baseline/ry261-test.sql"
cp "$ROOT"/deploy/database/migrations/*.sql "$OUT/database/migrations/"
cp "$ROOT"/deploy/database/*.sh "$OUT/lib/"
cp "$ROOT"/deploy/offline/install.sh "$ROOT"/deploy/offline/upgrade.sh "$ROOT"/deploy/offline/nusctl "$OUT/"
cp "$ROOT"/deploy/offline/lib/common.sh "$OUT/lib/common.sh"
for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE" "$MYSQL_IMAGE" "$REDIS_IMAGE" "$FLYWAY_IMAGE" "$ELASTICSEARCH_IMAGE" "$SKYWALKING_OAP_IMAGE" "$SKYWALKING_UI_IMAGE"; do
  platform=$(docker image inspect --platform linux/amd64 -f '{{.Os}}/{{.Architecture}}' "$image" 2>/dev/null) || {
    printf '%s\n' "required linux/amd64 image is missing: $image" >&2
    exit 1
  }
  [ "$platform" = linux/amd64 ] || {
    printf '%s\n' "image has unexpected platform ($platform): $image" >&2
    exit 1
  }
done
docker save --platform linux/amd64 -o "$OUT/images.tar" "${BACKEND_IMAGE%@*}" "${FRONTEND_IMAGE%@*}" "${MYSQL_IMAGE%@*}" "${REDIS_IMAGE%@*}" "${FLYWAY_IMAGE%@*}" "${ELASTICSEARCH_IMAGE%@*}" "${SKYWALKING_OAP_IMAGE%@*}" "${SKYWALKING_UI_IMAGE%@*}"
cat > "$OUT/release.env" <<EOF
RELEASE_VERSION=$VERSION
TARGET_OS=ubuntu24.04
TARGET_ARCH=amd64
BACKEND_IMAGE=${BACKEND_IMAGE%@*}
FRONTEND_IMAGE=${FRONTEND_IMAGE%@*}
MYSQL_IMAGE=${MYSQL_IMAGE%@*}
REDIS_IMAGE=${REDIS_IMAGE%@*}
FLYWAY_IMAGE=${FLYWAY_IMAGE%@*}
ELASTICSEARCH_IMAGE=${ELASTICSEARCH_IMAGE%@*}
SKYWALKING_OAP_IMAGE=${SKYWALKING_OAP_IMAGE%@*}
SKYWALKING_UI_IMAGE=${SKYWALKING_UI_IMAGE%@*}
BACKEND_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${BACKEND_IMAGE%@*}" | sed 's/.*@//')
FRONTEND_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${FRONTEND_IMAGE%@*}" | sed 's/.*@//')
MYSQL_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${MYSQL_IMAGE%@*}" | sed 's/.*@//')
REDIS_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${REDIS_IMAGE%@*}" | sed 's/.*@//')
FLYWAY_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${FLYWAY_IMAGE%@*}" | sed 's/.*@//')
ELASTICSEARCH_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${ELASTICSEARCH_IMAGE%@*}" | sed 's/.*@//')
SKYWALKING_OAP_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${SKYWALKING_OAP_IMAGE%@*}" | sed 's/.*@//')
SKYWALKING_UI_DIGEST=$(docker image inspect -f '{{index .RepoDigests 0}}' "${SKYWALKING_UI_IMAGE%@*}" | sed 's/.*@//')
BASELINE_SQL=database/baseline/ry261-test.sql
BASELINE_VERSION=$BASELINE_VERSION
VOSK_MODEL_VERSION=vosk-model-small-cn-0.22
SKYWALKING_AGENT_VERSION=9.7.0
SKYWALKING_UI_VERSION=1.0.0
EOF
cat > "$OUT/README.txt" <<'EOF'
师砺平台离线发布包
首次安装：sudo ./install.sh
无人值守首次安装：sudo NUS_SITE_URL=https://your-domain NUS_ADMIN_PASSWORD_FILE=/path/to/password-file ./install.sh
管理员密码文件必须为 0600 权限，密码为 12-16 位 ASCII，且同时包含大小写字母、数字和特殊字符。
升级：sudo ./upgrade.sh /path/to/release
后续只更新前端、后端和数据库时，在构建机使用 deploy/offline/build-update.sh 生成小型增量包。
运维：sudo ./nusctl status|logs|doctor|backup|restore|monitor-status|monitor-restart|monitor-credentials
安装完成后也可在任意目录执行：sudo nusctl status|logs|doctor|backup|restore|monitor-status|monitor-restart|monitor-credentials
日志定位：sudo nusctl logs backend --since 30m --error；按链路查询使用 --trace-id TRACE_ID，持续查看使用 --follow。
MySQL SQL监控、Java监控和SkyWalking默认启用。管理员从平台菜单进入，无需二次登录；
未登录或未分配 infra:monitor:access 权限的用户无法访问。SkyWalking 默认使用受保护的 8082 端口。
执行 sudo nusctl monitor-credentials 可查看内部应急凭据；Horizon、Actuator、OAP和Elasticsearch均不直接映射宿主机端口。
HTTPS 部署应让学校网关同时代理平台与 Horizon UI，并在首次安装前显式设置 NUS_SKYWALKING_URL；
NUS_MONITOR_COOKIE_SECURE 会根据 NUS_SITE_URL 自动设置，也可在 /srv/nus/config/.env 覆写。
教育 AI 默认启用学生数据外发和自动初评，使用前请确认学校的数据合规要求。
外部模型调用需要服务器具备互联网出口；本发布包不包含本地大模型。
升级不得删除或重新生成 NUS_MYBATIS_ENCRYPTOR_PASSWORD，否则已加密的 API 密钥将无法读取。
EOF
checksum_tmp="${OUT}.SHA256SUMS.$$"
trap 'rm -f "$checksum_tmp"' 0 1 2 15
(cd "$OUT" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum) > "$checksum_tmp"
mv "$checksum_tmp" "$OUT/SHA256SUMS"
trap - 0 1 2 15
printf '%s\n' "$OUT"
