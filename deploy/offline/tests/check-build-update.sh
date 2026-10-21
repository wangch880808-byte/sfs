#!/usr/bin/env bash
# Hermetic dry run of deploy/offline/build-update.sh; run with: bash deploy/offline/tests/check-build-update.sh
# Builds one throwaway shili-platform repository (deploy/ plus ordinary NUS-backend, NUS-front and NUS-doc directories),
# a fake jar and dist, then checks the package and every refusal. Needs git >= 2.32, python3, unzip, tar and
# sha256sum. User and system git configuration are ignored; the real repository and releases/ are never touched.
# shellcheck disable=SC2015  # "check && pass || bad": pass only prints, so bad runs only when the check fails
set -uo pipefail
ROOT=$(cd -- "$(dirname "$0")/../../.." && pwd)
TMP_BASE=${TMPDIR:-/tmp}
WORK=$(mktemp -d "${TMP_BASE%/}/nus-build-update-check.XXXXXX")
trap 'rm -rf "$WORK"' EXIT
export GIT_CONFIG_GLOBAL=/dev/null GIT_CONFIG_NOSYSTEM=1
export GIT_AUTHOR_NAME=dry-run GIT_AUTHOR_EMAIL=dry-run@example.invalid
export GIT_COMMITTER_NAME=dry-run GIT_COMMITTER_EMAIL=dry-run@example.invalid
D="$WORK/project"
OUTDIR="$D/releases"
BUILD="$D/deploy/offline/build-update.sh"
releases_before=$(ls -A "$ROOT/releases" 2>/dev/null || true)
failures=0
pass() { printf 'PASS %s\n' "$*"; }
bad() { printf 'FAIL %s\n' "$*"; failures=$((failures + 1)); }
g() { git -c init.defaultBranch=main -c core.hooksPath=/dev/null "$@"; }
make_jar() {  # path, extra entries...
  python3 - "$@" <<'EOF'
import sys, zipfile
path, extra = sys.argv[1], sys.argv[2:]
with zipfile.ZipFile(path, 'w') as z:
    for name in ['BOOT-INF/classes/application.yaml', 'BOOT-INF/classes/application-prod.yaml',
                 'BOOT-INF/lib/demo.jar', 'META-INF/MANIFEST.MF', *extra]:
        z.writestr(name, 'x')
EOF
}
expect_refusal() {  # label, stderr pattern, command...
  local label=$1 pattern=$2
  shift 2
  if "$@" >"$WORK/out" 2>"$WORK/err"; then bad "$label: must refuse"; return; fi
  if ! grep -q -- "$pattern" "$WORK/err"; then bad "$label: unexpected error: $(head -3 "$WORK/err")"; return; fi
  if [ -n "$(find "$OUTDIR" -maxdepth 1 -name '*V9.9.8*' 2>/dev/null)" ]; then bad "$label: left partial output"; return; fi
  pass "$label"
}

# --- fixture ------------------------------------------------------------------------------------------
mkdir -p "$D"
cp -R "$ROOT/deploy" "$D/deploy"
for directory in NUS-backend NUS-front NUS-doc; do mkdir -p "$D/$directory"; done
printf 'target/\n' > "$D/NUS-backend/.gitignore" && echo backend > "$D/NUS-backend/README.md"
printf 'dist/\n' > "$D/NUS-front/.gitignore" && echo front > "$D/NUS-front/README.md"
mkdir -p "$D/NUS-doc/3-数据库文件" && echo '-- baseline' > "$D/NUS-doc/3-数据库文件/ry261-test.sql"
mkdir -p "$D/NUS-backend/nus-server/target" "$D/NUS-front/apps/web-antd/dist/js"
make_jar "$D/NUS-backend/nus-server/target/nus-server.jar"
echo '<html>师砺</html>' > "$D/NUS-front/apps/web-antd/dist/index.html"
echo 'console.log(1)' > "$D/NUS-front/apps/web-antd/dist/js/app.js"
touch "$D/NUS-front/apps/web-antd/dist/.DS_Store"
if command -v xattr >/dev/null 2>&1; then
  xattr -w com.apple.provenance demo "$D/NUS-front/apps/web-antd/dist/index.html" 2>/dev/null || true
elif command -v setfattr >/dev/null 2>&1; then
  setfattr -n user.demo -v 1 "$D/NUS-front/apps/web-antd/dist/index.html" 2>/dev/null || true
fi
printf '/releases/\n' > "$D/.gitignore"
g -C "$D" init -q && g -C "$D" add .gitignore deploy NUS-backend NUS-front NUS-doc 2>/dev/null && g -C "$D" commit -qm init
echo 'untracked is fine' > "$D/scratch-note.txt"
mkdir -p "$D/NUS-doc/1-需求说明书" && echo 'user material' > "$D/NUS-doc/1-需求说明书/需求.md"
printf '# 更新说明\n\n测试\n' > "$WORK/notes.md"
export BACKEND_IMAGE=nus-backend:V9.9.9 FRONTEND_IMAGE=nus-front:V9.9.9

# --- success --------------------------------------------------------------------------------------------
if RELEASE_NOTES="$WORK/notes.md" CLEAN_BACKEND_BUILD=true "$BUILD" V9.9.9 >"$WORK/out" 2>"$WORK/err"; then
  pass 'build succeeds; untracked files outside artifact sources are allowed'
else
  bad "build failed: $(cat "$WORK/err")"
fi
PKG="$OUTDIR/nus-update-V9.9.9-ubuntu24.04-amd64"
TGZ="$PKG.tar.gz"
[ "$(cat "$WORK/out")" = "$PKG
$TGZ
$TGZ.sha256" ] && pass 'prints package dir, archive and checksum paths' || bad "output: $(cat "$WORK/out")"
grep -Eq '^[0-9a-f]{64}  nus-update-V9\.9\.9-ubuntu24\.04-amd64\.tar\.gz$' "$TGZ.sha256" \
  && [ "$(wc -l < "$TGZ.sha256" | tr -d ' ')" = 1 ] \
  && (cd "$OUTDIR" && sha256sum --check --quiet "$(basename "$TGZ").sha256") \
  && pass '.sha256 is "<hex>  <file name>" and verifies' || bad '.sha256 format'
tar -tzf "$TGZ" > "$WORK/list"
! grep -Eq '(^|/)(\._|\.DS_Store)' "$WORK/list" && ! grep -q '需求' "$WORK/list" \
  && pass 'archive has no ._, .DS_Store or NUS-doc user material' || bad 'unexpected archive entries'
python3 - "$TGZ" <<'EOF' && pass 'archive carries no xattr/fflags pax headers; owner root' || bad 'pax headers/owner'
import sys, tarfile
bad = [(m.name, sorted(m.pax_headers)) for m in tarfile.open(sys.argv[1])
       if any(k.startswith(('LIBARCHIVE', 'SCHILY')) for k in m.pax_headers) or m.uid != 0 or m.uname != 'root']
sys.exit(1 if bad else 0)
EOF
mkdir "$WORK/extract" && tar -xzf "$TGZ" -C "$WORK/extract"
X="$WORK/extract/nus-update-V9.9.9-ubuntu24.04-amd64"
(cd "$X" && sha256sum --check --quiet SHA256SUMS) && ! grep -Evq '^[0-9a-f]{64}  \./' "$X/SHA256SUMS" \
  && grep -q '  ./BUILD-METADATA.json$' "$X/SHA256SUMS" && grep -q '  ./更新说明.md$' "$X/SHA256SUMS" \
  && pass 'SHA256SUMS keeps "<hex>  ./path", covers metadata and notes, verifies after extraction' || bad 'SHA256SUMS'
missing=0
for f in upgrade.sh nusctl README.txt release.env runtime/compose.yaml lib/common.sh lib/backup.sh lib/restore.sh \
  lib/flyway.sh artifacts/backend/Dockerfile artifacts/backend/nus-server.jar artifacts/frontend/Dockerfile \
  artifacts/frontend/dist/index.html artifacts/frontend/dist/js/app.js; do
  [ -f "$X/$f" ] || { bad "missing $f"; missing=1; }
done
latest=$(find "$ROOT/deploy/database/migrations" -maxdepth 1 -name 'V*__*.sql' | sed 's#.*/V\([0-9]*\)__.*#\1#' | sort -n | tail -1)
[ "$missing" = 0 ] && [ -f "$X/database/migrations/$(basename "$(find "$ROOT/deploy/database/migrations" -maxdepth 1 -name "V${latest}__*.sql")")" ] \
  && [ ! -e "$X/artifacts/frontend/dist/.DS_Store" ] && grep -q 'SPRING_PROFILES_ACTIVE=prod' "$X/artifacts/backend/Dockerfile" \
  && [ -x "$X/lib/backup.sh" ] && [ -x "$X/upgrade.sh" ] \
  && pass 'package layout complete (all migrations, .DS_Store dropped, prod ENV, exec bits kept)' || bad 'layout'
python3 - "$X/runtime/compose.yaml" <<'EOF' && pass 'packaged MySQL healthcheck uses MYSQL_PWD without password argv' || bad 'healthcheck password argv'
import json, re, sys
mysql = open(sys.argv[1]).read().split('\n  mysql:', 1)[1].split('\n  redis:', 1)[0]
kind, command = json.loads(re.search(r'^\s+test: (\[.*\])$', mysql, re.M).group(1))
assert kind == 'CMD-SHELL'
assert command.startswith('MYSQL_PWD="$${MYSQL_ROOT_PASSWORD}" mysqladmin ping ')
assert not re.search(r'(^|\s)(-p|--password)', command)
EOF
grep -qx 'BACKEND_IMAGE=nus-backend:V9.9.9' "$X/release.env" && grep -qx 'RELEASE_KIND=application-update' "$X/release.env" \
  && pass 'release.env records kind and tags' || bad 'release.env'
python3 - "$X/BUILD-METADATA.json" "$D" "$latest" <<'EOF' && pass "BUILD-METADATA.json: repository, tags, version, time, hashes, migration_v$latest, clean flag" || bad 'metadata'
import glob, hashlib, json, subprocess, sys
meta, root, latest = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
head = lambda p: subprocess.check_output(['git', '-C', p, 'rev-parse', 'HEAD'], text=True).strip()
sha = lambda p: hashlib.sha256(open(p, 'rb').read()).hexdigest()
assert meta['release_version'] == 'V9.9.9' and meta['backend_image'] == 'nus-backend:V9.9.9'
assert meta['frontend_image'] == 'nus-front:V9.9.9' and meta['build_time'].endswith('Z')
assert meta['repository'] == {'name': 'shili-platform', 'head': head(root), 'includes_working_tree_changes': False}
assert not {'NUS-project', 'NUS-backend', 'NUS-front', 'NUS-doc'} & meta.keys()
art = meta['artifacts']
assert set(art) == {'backend_jar', 'frontend_index', f'migration_v{latest}'}, art
assert art['backend_jar'] == sha(f'{root}/NUS-backend/nus-server/target/nus-server.jar')
assert art['frontend_index'] == sha(f'{root}/NUS-front/apps/web-antd/dist/index.html')
migration, = glob.glob(f'{root}/deploy/database/migrations/V{latest}__*.sql')
assert art[f'migration_v{latest}'] == sha(migration)
assert meta['clean_backend_build'] is True
EOF

if "$BUILD" V9.9.7 "$OUTDIR/doc-untracked" >"$WORK/out" 2>"$WORK/err"; then
  pass 'untracked file in NUS-doc does not block packaging'
else
  bad "NUS-doc untracked file blocked packaging: $(cat "$WORK/err")"
fi

# --- refusals (each must leave nothing behind) ------------------------------------------------------------
expect_refusal 'existing output refused' 'already exists' "$BUILD" V9.9.9
expect_refusal 'image without tag refused' 'explicit tag' env BACKEND_IMAGE=nus-backend "$BUILD" V9.9.8
expect_refusal 'latest tag refused' 'without digest' env FRONTEND_IMAGE=nus-front:latest "$BUILD" V9.9.8
expect_refusal 'digest refused' 'without digest' env BACKEND_IMAGE=nus-backend:V1@sha256:abc "$BUILD" V9.9.8
expect_refusal 'invalid version refused' 'invalid version' "$BUILD" 'V9 9'
echo change >> "$D/NUS-backend/README.md"
expect_refusal 'tracked change in NUS-backend refused' 'NUS-backend has uncommitted or untracked files' "$BUILD" V9.9.8
g -C "$D/NUS-backend" checkout -q README.md
echo new > "$D/NUS-front/untracked.md"
expect_refusal 'untracked file in NUS-front refused' 'NUS-front has uncommitted or untracked files' "$BUILD" V9.9.8
rm "$D/NUS-front/untracked.md"
echo '-- edited' >> "$D/NUS-doc/3-数据库文件/ry261-test.sql"
expect_refusal 'tracked change in NUS-doc refused' 'NUS-doc has uncommitted changes to tracked files' "$BUILD" V9.9.8
g -C "$D/NUS-doc" checkout -q -- .
echo '# change' >> "$D/deploy/compose.yaml"
expect_refusal 'tracked change outside NUS directories refused' 'shili-platform has uncommitted' "$BUILD" V9.9.8
g -C "$D" checkout -q deploy/compose.yaml
make_jar "$D/NUS-backend/nus-server/target/nus-server.jar" BOOT-INF/classes/application-local.yaml
expect_refusal 'jar with application-local.yaml refused' 'development configuration' "$BUILD" V9.9.8
make_jar "$D/NUS-backend/nus-server/target/nus-server.jar" BOOT-INF/classes/application-dev.yaml
expect_refusal 'jar with application-dev.yaml refused' 'development configuration' "$BUILD" V9.9.8
python3 -c "import sys, zipfile; zipfile.ZipFile(sys.argv[1], 'w').writestr('BOOT-INF/classes/application.yaml', 'x')" \
  "$D/NUS-backend/nus-server/target/nus-server.jar"
expect_refusal 'jar without application-prod.yaml refused' 'lacks BOOT-INF/classes/application-prod.yaml' "$BUILD" V9.9.8
make_jar "$D/NUS-backend/nus-server/target/nus-server.jar"
expect_refusal 'missing release notes file refused' 'release notes file' env RELEASE_NOTES="$WORK/none.md" "$BUILD" V9.9.8
g -C "$D" rm -q deploy/offline/update/Dockerfile.frontend && g -C "$D" commit -qm 'drop dockerfile'
expect_refusal 'failure during assembly removes partial output' 'No such file' "$BUILD" V9.9.8
g -C "$D" revert --no-edit HEAD >/dev/null

# --- defaults ---------------------------------------------------------------------------------------------
if "$BUILD" V9.9.8 >"$WORK/out" 2>"$WORK/err"; then
  grep -q 'RELEASE_NOTES not set' "$WORK/err" && ! grep -q '更新说明' "$OUTDIR/nus-update-V9.9.8-ubuntu24.04-amd64/SHA256SUMS" \
    && grep -q '"clean_backend_build": false' "$OUTDIR/nus-update-V9.9.8-ubuntu24.04-amd64/BUILD-METADATA.json" \
    && pass 'without RELEASE_NOTES/CLEAN_BACKEND_BUILD: warning, no notes, clean_backend_build false' || bad 'defaults'
else
  bad "default build failed: $(cat "$WORK/err")"
fi
[ "$(ls -A "$ROOT/releases" 2>/dev/null || true)" = "$releases_before" ] && pass 'real releases/ untouched' \
  || bad 'real releases/ changed'

printf '%s failure(s)\n' "$failures"
[ "$failures" -eq 0 ]
