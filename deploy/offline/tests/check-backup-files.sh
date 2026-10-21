#!/usr/bin/env bash
# 不依赖 Docker：附件、配置备份以及 nusctl backup 的离线回归。
set -euo pipefail
ROOT=$(cd -- "$(dirname "$0")/../../.." && pwd)
BACKUP="$ROOT/deploy/database/backup-files.sh"
TEST_SH=${NUS_TEST_SH:-$(command -v dash || command -v sh)}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/nus-backup-files-check.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
fail() { printf 'FAIL %s\n' "$*" >&2; exit 1; }
pass() { printf 'PASS %s\n' "$*"; }
mode_of() { stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1"; }
mtime_of() { stat -c '%Y' "$1" 2>/dev/null || stat -f '%m' "$1"; }
secret="backup-test-sentinel-$(date +%s)-$$"
mkdir -p "$WORK/source/附件目录/多级/子目录" "$WORK/backups"
printf 'SECRET=%s\n' "$secret" > "$WORK/source/.env"
printf '中文内容\n' > "$WORK/source/附件目录/多级/子目录/含 空格.txt"
printf 'hidden\n' > "$WORK/source/附件目录/.hidden"
printf 'plain\n' > "$WORK/source/附件目录/plain.txt"
ln "$WORK/source/附件目录/plain.txt" "$WORK/source/附件目录/hardlink.txt"
ln -s plain.txt "$WORK/source/附件目录/symlink.txt"
chmod 0640 "$WORK/source/附件目录/多级/子目录/含 空格.txt"
touch -t 202001020304 "$WORK/source/附件目录/多级/子目录/含 空格.txt"
run_backup() {
  UPLOADS_DIR="$1" ENV_FILE="$2" BACKUP_DIR="$3" TIMESTAMP=20261014T020000Z \
    bash "$BACKUP" > "$WORK/stdout" 2> "$WORK/stderr"
}
check_output() {
  [ "$(wc -l < "$WORK/stdout" | tr -d ' ')" = 2 ] || fail 'stdout 必须只有两行路径'
  files=$(sed -n '1p' "$WORK/stdout")
  env_copy=$(sed -n '2p' "$WORK/stdout")
  for f in "$files" "$env_copy"; do
    [ -f "$f" ] && [ -f "$f.sha256" ] || fail '产物或校验文件缺失'
    [ "$(mode_of "$f")" = 600 ] && [ "$(mode_of "$f.sha256")" = 600 ] || fail '产物权限不是 0600'
    (cd "$(dirname "$f")" && sha256sum -c "$(basename "$f").sha256") >/dev/null || fail 'sha256 不正确'
  done
  cmp "$WORK/source/.env" "$env_copy" || fail '.env 副本不同'
  if grep -qF "$secret" "$WORK/stdout" "$WORK/stderr"; then fail '输出泄露 .env 内容'; fi
  grep -q '这个文件含口令和密钥，权限 0600，异地保存时要加密' "$WORK/stderr" || fail '缺少密钥提醒'
}
run_backup "$WORK/source/附件目录" "$WORK/source/.env" "$WORK/backups"
check_output
mkdir "$WORK/extracted"
gzip -t "$files"
tar -tzf "$files" > "$WORK/list"
[ "$(sed -n '1p' "$WORK/list")" = uploads/ ] || fail '包内根目录不是 uploads/'
tar -xzf "$files" -C "$WORK/extracted"
diff -r "$WORK/source/附件目录" "$WORK/extracted/uploads" || fail '解包内容与源目录不同'
source_file="$WORK/source/附件目录/多级/子目录/含 空格.txt"
restored_file="$WORK/extracted/uploads/多级/子目录/含 空格.txt"
[ "$(mode_of "$source_file")" = "$(mode_of "$restored_file")" ] || fail '附件权限未保留'
[ "$(mtime_of "$source_file")" = "$(mtime_of "$restored_file")" ] || fail '附件修改时间未保留'
[ "$(readlink "$WORK/extracted/uploads/symlink.txt")" = plain.txt ] || fail '符号链接目标被改写'
pass '正常备份：多级、中英文空格、隐藏文件、硬链接、符号链接、权限、mtime、校验和、输出保密'
first_files=$files
first_env=$env_copy
sha256sum "$first_files" "$first_env" > "$WORK/first.sha256"
run_backup "$WORK/source/附件目录" "$WORK/source/.env" "$WORK/backups"
check_output
[ "$files" != "$first_files" ] && [ "$env_copy" != "$first_env" ] || fail '重复时间戳覆盖文件'
sha256sum -c "$WORK/first.sha256" >/dev/null
pass '同一时间戳重复备份不覆盖'
for kind in missing empty; do
  [ "$kind" != empty ] || mkdir "$WORK/empty"
  run_backup "$WORK/$kind" "$WORK/source/.env" "$WORK/$kind-backups"
  check_output
  mkdir "$WORK/$kind-extracted"
  tar -xzf "$files" -C "$WORK/$kind-extracted"
  [ -d "$WORK/$kind-extracted/uploads" ] || fail '空包缺少 uploads 目录'
  [ "$(find "$WORK/$kind-extracted/uploads" -type f | wc -l | tr -d ' ')" = 0 ] || fail '空包里有普通文件'
  grep -q '空' "$WORK/stderr" || fail '缺少空目录日志'
  pass "附件目录 $kind 生成空包"
done
mkdir "$WORK/failure-backups"
if run_backup "$WORK/source/附件目录" "$WORK/missing.env" "$WORK/failure-backups"; then fail '缺少 .env 应失败'; fi
[ -z "$(ls -A "$WORK/failure-backups")" ] || fail '失败后留下半成品'
[ ! -s "$WORK/stdout" ] || fail '失败时输出了成功路径'
pass '.env 不存在：非零退出，无半成品'

# 在已经写出附件和配置临时文件之后注入 cmp 失败，验证统一清理。
mkdir "$WORK/bin"
printf '#!/bin/sh\nexit 1\n' > "$WORK/bin/cmp"
chmod +x "$WORK/bin/cmp"
if PATH="$WORK/bin:$PATH" run_backup "$WORK/source/附件目录" "$WORK/source/.env" "$WORK/failure-backups"; then
  fail 'cmp 校验失败应退出非零'
fi
[ -z "$(ls -A "$WORK/failure-backups")" ] || fail 'cmp 失败后留下半成品'
[ ! -s "$WORK/stdout" ] || fail 'cmp 失败仍输出成功路径'
pass '副本校验失败清理全部半成品'
# 两个并行进程使用同一时间戳，路径必须不同，各自校验通过。
for job in 1 2; do
  UPLOADS_DIR="$WORK/source/附件目录" ENV_FILE="$WORK/source/.env" BACKUP_DIR="$WORK/concurrent" \
    TIMESTAMP=20261014T020000Z bash "$BACKUP" > "$WORK/concurrent-$job.out" 2> "$WORK/concurrent-$job.err" &
  if [ "$job" = 1 ]; then pid1=$!; else pid2=$!; fi
done
wait "$pid1"
wait "$pid2"
[ "$(cat "$WORK/concurrent-1.out" "$WORK/concurrent-2.out" | sort -u | wc -l | tr -d ' ')" = 4 ] \
  || fail '并发备份路径冲突'
for checksum in "$WORK/concurrent"/*.sha256; do
  (cd "$WORK/concurrent" && sha256sum -c "$checksum") >/dev/null || fail '并发备份损坏'
done
pass '并发同一时间戳备份不覆盖'

# 用临时发行目录替换 common.sh 和数据库备份，执行真实 nusctl，不要求 root 或 Docker。
mkdir -p "$WORK/release/lib" "$WORK/config" "$WORK/data/data/uploads"
cp "$ROOT/deploy/offline/nusctl" "$WORK/release/nusctl"
cp "$BACKUP" "$WORK/release/lib/backup-files.sh"
chmod +x "$WORK/release/lib/backup-files.sh"
cp "$WORK/source/.env" "$WORK/config/.env"
cat > "$WORK/release/lib/common.sh" <<'STUB'
log() { printf '[nus] %s\n' "$*"; }
die() { printf '[nus] ERROR: %s\n' "$*" >&2; exit 1; }
require_root() { :; }
load_env() { :; }
run_db_backup() {
  printf 'db\n' >> "$DATA_ROOT/calls"
  [ "${FAIL_DB:-0}" = 0 ] || return 1
  mkdir -p "$DATA_ROOT/backups"
  printf 'fake database\n' > "$DATA_ROOT/backups/db-test.sql.gz"
  printf '%s\n' "$DATA_ROOT/backups/db-test.sql.gz"
}
STUB
run_nusctl() {
  CONFIG_ROOT="$WORK/config" DATA_ROOT="$WORK/data" FAIL_DB="${FAIL_DB:-0}" \
    "$TEST_SH" "$WORK/release/nusctl" backup "$@" > "$WORK/ctl-out" 2> "$WORK/ctl-err"
}
run_nusctl --db-only
[ "$(cat "$WORK/ctl-out")" = "$WORK/data/backups/db-test.sql.gz" ] || fail '--db-only 输出改变'
[ "$(find "$WORK/data/backups" -type f | wc -l | tr -d ' ')" = 1 ] || fail '--db-only 产生额外备份'
pass 'nusctl backup --db-only 保持原输出'
if run_nusctl --unknown; then fail '未知参数未失败'; fi
if run_nusctl extra; then fail '额外位置参数未失败'; fi
[ "$(wc -l < "$WORK/data/calls" | tr -d ' ')" = 1 ] || fail '参数错误仍调用了数据库备份'
pass 'nusctl backup 未知参数在备份前报错'
FAIL_DB=1
if run_nusctl; then fail '数据库失败没有传播'; fi
FAIL_DB=0
[ "$(find "$WORK/data/backups" -type f | wc -l | tr -d ' ')" = 1 ] || fail '数据库失败仍备份附件'
pass '数据库备份失败不执行文件备份'
run_nusctl || { cat "$WORK/ctl-err" >&2; fail 'nusctl 完整备份失败'; }
[ "$(find "$WORK/data/backups" -name 'files-*.tar.gz' | wc -l | tr -d ' ')" = 1 ] || fail '完整备份缺少附件'
grep -q '异地' "$WORK/ctl-out" || fail '缺少异地拷贝提醒'
grep -q '含密钥' "$WORK/ctl-out" || fail '缺少配置密钥提醒'
grep -q '字节' "$WORK/ctl-out" || fail '汇总未打印大小'
if grep -qF "$secret" "$WORK/ctl-out" "$WORK/ctl-err"; then fail 'nusctl 输出泄露配置'; fi
pass 'nusctl 完整备份汇总与提醒'
# 注入文件备份失败，验证数据库保留且明确打印路径。
printf '#!/bin/sh\nexit 1\n' > "$WORK/release/lib/backup-files.sh"
if run_nusctl; then fail '文件备份失败没有传播'; fi
grep -q '数据库备份已成功' "$WORK/ctl-err" || fail '文件失败未说明数据库成功'
grep -qF "$WORK/data/backups/db-test.sql.gz" "$WORK/ctl-err" || fail '文件失败未打印数据库路径'
[ -f "$WORK/data/backups/db-test.sql.gz" ] || fail '文件失败时删除了数据库备份'
pass '文件备份失败返回非零，并保留、报告数据库备份'
printf '全部备份回归检查通过\n'
