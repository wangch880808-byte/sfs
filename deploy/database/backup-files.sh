#!/usr/bin/env bash
set -euo pipefail

: "${UPLOADS_DIR:?UPLOADS_DIR is required}"
: "${ENV_FILE:?ENV_FILE is required}"
BACKUP_DIR=${BACKUP_DIR:-/srv/nus/backups}
timestamp=${TIMESTAMP:-$(date -u +%Y%m%dT%H%M%SZ)}
umask 077
log() { printf '[nus] %s\n' "$*" >&2; }
fail() { log "ERROR: $*"; exit 1; }
case "$timestamp" in
  ''|*[!A-Za-z0-9._-]*) fail '无效的备份时间戳' ;;
esac
[ -f "$ENV_FILE" ] && [ -r "$ENV_FILE" ] || fail ".env 不存在或不可读：$ENV_FILE"
mkdir -p "$BACKUP_DIR"
BACKUP_DIR=$(cd -- "$BACKUP_DIR" && pwd)

# Reserve both names together, including concurrent invocations with the same timestamp.
n=0
while :; do
  suffix=$timestamp
  [ "$n" -eq 0 ] || suffix="$timestamp-$n"
  files="$BACKUP_DIR/files-$suffix.tar.gz"
  env_copy="$BACKUP_DIR/env-$suffix.env"
  lock="$files.lock"
  if mkdir "$lock" 2>/dev/null; then
    occupied=false
    for candidate in "$files" "$files.sha256" "$env_copy" "$env_copy.sha256"; do
      if [ -e "$candidate" ] || [ -L "$candidate" ]; then occupied=true; fi
    done
    [ "$occupied" = true ] || break
    rmdir "$lock"
  else
    [ -e "$lock" ] || fail "无法在备份目录中预留文件：$BACKUP_DIR"
  fi
  n=$((n + 1))
done
files_published=false
env_published=false
cleanup() {
  status=$?
  if [ "$status" -ne 0 ]; then
    if [ "$files_published" = true ]; then rm -f "$files" "$files.sha256"; fi
    if [ "$env_published" = true ]; then rm -f "$env_copy" "$env_copy.sha256"; fi
  fi
  rm -rf "$lock"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP
files_tmp="$lock/$(basename "$files")"
env_tmp="$lock/$(basename "$env_copy")"
count_files() {
  # Count entries rather than lines of filenames (spaces and newlines are legal in filenames).
  find "$1" -type f -exec sh -c 'for path do printf .; done' sh {} + | wc -c | tr -d '[:space:]'
}

if [ -d "$UPLOADS_DIR" ]; then
  source_dir=$(cd -- "$UPLOADS_DIR" && pwd)
  source_count=$(count_files "$source_dir")
  [ "$(find "$source_dir" -mindepth 1 -maxdepth 1 -print | wc -l | tr -d '[:space:]')" != 0 ] \
    || log '附件目录为空，生成空附件包'
else
  [ ! -e "$UPLOADS_DIR" ] || fail "附件路径不是目录：$UPLOADS_DIR"
  log '附件目录不存在，生成空附件包'
  mkdir "$lock/empty"
  source_dir="$lock/empty"
  source_count=0
fi

# Normalize the archive root to uploads/ for any source directory name; keep symlink targets unchanged.
# GNU tar is used on servers, BSD tar on macOS development machines.
if tar --version 2>/dev/null | grep -q 'GNU tar'; then
  tar --transform='flags=rh;s|^\.$|uploads|;s|^\./|uploads/|' -C "$source_dir" -czf "$files_tmp" . \
    || fail '附件打包失败；半成品已清理'
else
  tar -s '|^\.$|uploads|S' -s '|^\./|uploads/|S' -C "$source_dir" -czf "$files_tmp" . \
    || fail '附件打包失败；半成品已清理'
fi
gzip -t "$files_tmp" || fail '附件包 gzip 完整性校验失败'
tar -tzf "$files_tmp" > "$lock/members" || fail '附件包无法列出'
# GNU tar prints hardlinks as h, BSD tar as ordinary files with a link suffix.
archive_count=$(tar -tvzf "$files_tmp" | awk '/^[-h]/ {n++} END {print n+0}')
[ "$source_count" = "$archive_count" ] && [ "$source_count" = "$(count_files "$source_dir")" ] \
  || fail '附件包与源目录的普通文件数不一致，请在上传较少时重试'
cat "$ENV_FILE" > "$env_tmp" || fail '.env 复制失败'
cmp -s "$ENV_FILE" "$env_tmp" || fail '.env 副本逐字节校验失败'
log '这个文件含口令和密钥，权限 0600，异地保存时要加密'
# Relative checksum paths stay usable when all backups are copied to another machine.
(cd "$lock" && sha256sum "$(basename "$files")" > "$(basename "$files").sha256" \
  && sha256sum "$(basename "$env_copy")" > "$(basename "$env_copy").sha256" \
  && sha256sum -c "$(basename "$files").sha256" >/dev/null \
  && sha256sum -c "$(basename "$env_copy").sha256" >/dev/null) || fail '备份校验和生成或校验失败'
chmod 0600 "$files_tmp" "$files_tmp.sha256" "$env_tmp" "$env_tmp.sha256"
# Publish only after both backups and their checksums passed every check.
files_published=true
mv "$files_tmp" "$files"
mv "$files_tmp.sha256" "$files.sha256"
env_published=true
mv "$env_tmp" "$env_copy"
mv "$env_tmp.sha256" "$env_copy.sha256"
printf '%s\n%s\n' "$files" "$env_copy"
