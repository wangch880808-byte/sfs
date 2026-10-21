#!/bin/sh
# Build an application update package (jar, dist, migrations, scripts) from committed sources.
#
# usage: BACKEND_IMAGE=nus-backend:V<version> FRONTEND_IMAGE=nus-front:V<version> \
#          [RELEASE_NOTES=/path/to/更新说明.md] [CLEAN_BACKEND_BUILD=true] \
#          deploy/offline/build-update.sh V<version> [OUTPUT_DIR]
#
# Refuses to build unless NUS-backend and NUS-front have no changes at all (untracked files included),
# and all other directories in shili-platform have no tracked changes.
# Untracked files in NUS-doc are user material that is never packaged.
# CLEAN_BACKEND_BUILD=true records that the jar came from a clean build (mvnd clean package).
# Produces OUTPUT_DIR, OUTPUT_DIR.tar.gz and OUTPUT_DIR.tar.gz.sha256.
set -eu

die() {
  printf '%s\n' "$*" >&2
  exit 1
}

ROOT=$(cd -- "$(dirname "$0")/../.." && pwd)
VERSION=${1:-$(date -u +%Y%m%dT%H%M%SZ)}
OUT=${2:-$ROOT/releases/nus-update-$VERSION-ubuntu24.04-amd64}
: "${BACKEND_IMAGE:?set BACKEND_IMAGE to a new backend image tag for this update}"
: "${FRONTEND_IMAGE:?set FRONTEND_IMAGE to a new frontend image tag for this update}"
BACKEND_JAR=${BACKEND_JAR:-$ROOT/NUS-backend/nus-server/target/nus-server.jar}
FRONTEND_DIST=${FRONTEND_DIST:-$ROOT/NUS-front/apps/web-antd/dist}
RELEASE_NOTES=${RELEASE_NOTES:-}
CLEAN_BACKEND_BUILD=${CLEAN_BACKEND_BUILD:-false}

case "$VERSION" in
  ''|*[!A-Za-z0-9._-]*) die "invalid version: $VERSION" ;;
esac
for image in "$BACKEND_IMAGE" "$FRONTEND_IMAGE"; do
  case "$image" in
    *@*|*[!A-Za-z0-9._/:-]*|*:latest) die "image must be a unique name:tag without digest: $image" ;;
  esac
  case "${image##*/}" in
    *:?*) ;;
    *) die "image must carry an explicit tag: $image" ;;
  esac
done
case "$CLEAN_BACKEND_BUILD" in
  true|false) ;;
  *) die 'CLEAN_BACKEND_BUILD must be true or false' ;;
esac
while [ "${OUT%/}" != "$OUT" ]; do OUT=${OUT%/}; done
TARBALL="$OUT.tar.gz"
[ ! -e "$OUT" ] || die "release output already exists: $OUT"
[ ! -e "$TARBALL" ] || die "release archive already exists: $TARBALL"
[ ! -e "$TARBALL.sha256" ] || die "release checksum already exists: $TARBALL.sha256"

# 1. Sources: one repository; artifact sources also reject untracked files.
git -C "$ROOT" rev-parse --verify -q HEAD >/dev/null || die "shili-platform is not a git checkout: $ROOT"
for check in NUS-backend:all NUS-front:all NUS-doc:no; do
  directory=${check%%:*}
  untracked=${check#*:}
  changes=$(git -C "$ROOT" status --porcelain --untracked-files="$untracked" -- "$directory/") \
    || die "cannot read $directory status"
  if [ "$untracked" = all ]; then
    problem='uncommitted or untracked files'
  else
    problem='uncommitted changes to tracked files'
  fi
  [ -z "$changes" ] || die "$directory has $problem; commit or remove them first:
$(printf '%s\n' "$changes" | head -20)"
done
changes=$(git -C "$ROOT" status --porcelain --untracked-files=no) || die 'cannot read shili-platform status'
[ -z "$changes" ] || die "shili-platform has uncommitted changes to tracked files outside NUS-backend/, NUS-front/ and NUS-doc/; commit them first:
$(printf '%s\n' "$changes" | head -20)"
REPOSITORY_HEAD=$(git -C "$ROOT" rev-parse HEAD)

# 2. Artifacts.
[ -f "$BACKEND_JAR" ] || die "backend jar is missing: $BACKEND_JAR"
[ -f "$FRONTEND_DIST/index.html" ] || die "frontend dist is missing index.html: $FRONTEND_DIST"
command -v unzip >/dev/null 2>&1 || die 'unzip is required to inspect the backend jar'
jar_entries=$(unzip -Z1 "$BACKEND_JAR") || die "cannot list backend jar: $BACKEND_JAR"
for required in BOOT-INF/classes/application.yaml BOOT-INF/classes/application-prod.yaml; do
  printf '%s\n' "$jar_entries" | grep -qxF "$required" || die "backend jar lacks $required: $BACKEND_JAR"
done
dev_config=$(printf '%s\n' "$jar_entries" | grep -E '(^|/)application-(local|dev)\.(ya?ml|properties)$' || true)
[ -z "$dev_config" ] || die "backend jar contains development configuration; rebuild without it:
$dev_config"
latest_migration=$(find "$ROOT/deploy/database/migrations" -maxdepth 1 -type f -name 'V*__*.sql' \
  | sed 's#.*/V\([0-9][0-9]*\)__.*#\1#' | sort -n | tail -1)
[ -n "$latest_migration" ] || die 'no Flyway migrations found'
latest_migration_file=$(find "$ROOT/deploy/database/migrations" -maxdepth 1 -type f -name "V${latest_migration}__*.sql")
if [ -n "$RELEASE_NOTES" ]; then
  [ -s "$RELEASE_NOTES" ] || die "release notes file is missing or empty: $RELEASE_NOTES"
else
  printf '%s\n' 'warning: RELEASE_NOTES not set; the package will not contain 更新说明.md' >&2
fi

# 3. Assemble; any failure from here removes the partial output.
created=false
cleanup() {
  status=$?
  if [ "$status" -ne 0 ] && [ "$created" = true ]; then
    rm -rf "$OUT"
    rm -f "$TARBALL" "$TARBALL.sha256" "$OUT.SHA256SUMS.$$"
  fi
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM
mkdir -p "$(dirname "$OUT")"
mkdir "$OUT"
created=true
mkdir -p "$OUT/runtime" "$OUT/database/migrations" "$OUT/lib" \
  "$OUT/artifacts/backend" "$OUT/artifacts/frontend/dist"
cp "$ROOT/deploy/compose.yaml" "$OUT/runtime/compose.yaml"
cp "$ROOT"/deploy/database/migrations/*.sql "$OUT/database/migrations/"
cp "$ROOT"/deploy/database/*.sh "$OUT/lib/"
cp "$ROOT/deploy/offline/upgrade.sh" "$ROOT/deploy/offline/nusctl" "$OUT/"
cp "$ROOT/deploy/offline/lib/common.sh" "$OUT/lib/common.sh"
cp "$ROOT/deploy/offline/update/Dockerfile.backend" "$OUT/artifacts/backend/Dockerfile"
cp "$ROOT/deploy/offline/update/Dockerfile.frontend" "$OUT/artifacts/frontend/Dockerfile"
cp "$BACKEND_JAR" "$OUT/artifacts/backend/nus-server.jar"
cp -R "$FRONTEND_DIST"/. "$OUT/artifacts/frontend/dist/"
find "$OUT/artifacts/frontend/dist" \( -name .DS_Store -o -name '._*' \) -type f -exec rm -f {} +
[ -z "$RELEASE_NOTES" ] || cp "$RELEASE_NOTES" "$OUT/更新说明.md"

cat > "$OUT/release.env" <<EOF
RELEASE_VERSION=$VERSION
TARGET_OS=ubuntu24.04
TARGET_ARCH=amd64
RELEASE_KIND=application-update
BACKEND_IMAGE=$BACKEND_IMAGE
FRONTEND_IMAGE=$FRONTEND_IMAGE
EOF

cat > "$OUT/README.txt" <<'EOF'
师砺平台应用增量升级包

适用范围：已完成首次安装的平台，仅升级前端、后端和 Flyway 数据库迁移。
不包含 Ubuntu deb、基础镜像、Vosk 模型和 SkyWalking Agent，不能用于首次安装。
目标机会完全离线地基于现有前后端镜像重建应用层，不会联网拉取镜像。

升级命令：sudo ./upgrade.sh /path/to/this-release（建议在 tmux 或 screen 中执行）
升级流程：先做只读预检（文件校验、版本未安装、新镜像标签未被占用、MySQL/Redis 健康、磁盘空间、Flyway 校验），
再构建新镜像、准备新的 .env，期间平台照常服务；随后停止前后端，在没有写入时备份数据库，换上新的 .env
（旧的保存为 /srv/nus/config/.env.before-<版本>-<时间>），执行 Flyway 迁移，清理 Redis 中的菜单、权限与通知模板缓存
（不影响已登录用户），最后启动新版本并做健康检查。停机后任何一步失败都会自动回滚到原版本（含数据库）。
已安装同一版本号，或新镜像标签与当前使用的标签相同时，升级会拒绝执行。
升级日志在 /srv/nus/logs/upgrade/；退回上一版本：sudo nusctl rollback previous
升级完成后：sudo nusctl status
已有的 domain-nginx 域名配置会自动保留，无需重新运行域名切换脚本。
构建来源见 BUILD-METADATA.json；随包提供 更新说明.md 时，其中列出改动与回滚步骤。
故障排查：sudo nusctl logs backend --since 30m --error
EOF

sha256_of() {
  sum=$(sha256sum "$1") || die "cannot hash $1"
  sum=${sum%% *}
  case "$sum" in
    *[!0-9a-f]*|'') die "cannot hash $1" ;;
  esac
  [ "${#sum}" -eq 64 ] || die "cannot hash $1"
  printf '%s' "$sum"
}
JAR_SHA=$(sha256_of "$OUT/artifacts/backend/nus-server.jar")
INDEX_SHA=$(sha256_of "$OUT/artifacts/frontend/dist/index.html")
MIGRATION_SHA=$(sha256_of "$latest_migration_file")
# Step 1 refuses every working-tree change that could reach the package, so packaged files match their commits.
cat > "$OUT/BUILD-METADATA.json" <<EOF
{
  "release_version": "$VERSION",
  "build_time": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "backend_image": "$BACKEND_IMAGE",
  "frontend_image": "$FRONTEND_IMAGE",
  "repository": {
    "name": "shili-platform",
    "head": "$REPOSITORY_HEAD",
    "includes_working_tree_changes": false
  },
  "artifacts": {
    "backend_jar": "$JAR_SHA",
    "frontend_index": "$INDEX_SHA",
    "migration_v$latest_migration": "$MIGRATION_SHA"
  },
  "clean_backend_build": $CLEAN_BACKEND_BUILD
}
EOF

checksum_tmp="$OUT.SHA256SUMS.$$"
(cd "$OUT" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum) > "$checksum_tmp"
mv "$checksum_tmp" "$OUT/SHA256SUMS"
(cd "$OUT" && sha256sum --check --quiet SHA256SUMS) || die 'SHA256SUMS self-check failed'

# 4. Archive without macOS metadata (no ._ files, xattrs or file flags) and with neutral ownership.
if tar --version 2>/dev/null | grep -q bsdtar; then
  set -- --no-xattrs --no-fflags --no-acls --uid 0 --gid 0 --uname root --gname root
else
  set -- --no-xattrs --no-acls --owner=0 --group=0 --numeric-owner
fi
(cd "$(dirname "$OUT")" && COPYFILE_DISABLE=1 tar "$@" -czf "$(basename "$TARBALL")" "$(basename "$OUT")")
if tar -tzf "$TARBALL" | grep -Eq '(^|/)(\._[^/]*|\.DS_Store)$'; then
  die "archive contains macOS metadata files: $TARBALL"
fi
(cd "$(dirname "$TARBALL")" && sha256sum "$(basename "$TARBALL")") > "$TARBALL.sha256"
(cd "$(dirname "$TARBALL")" && sha256sum --check --quiet "$(basename "$TARBALL").sha256") \
  || die 'archive checksum self-check failed'
printf '%s\n' "$OUT" "$TARBALL" "$TARBALL.sha256"
