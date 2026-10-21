#!/usr/bin/env bash
# 一键校验：按改动范围选择要跑的检查，提交前必跑。
#
# 用法：
#   scripts/check-all.sh            按改动自动选择（与 BASE 比较，含未提交改动）
#   scripts/check-all.sh --all      全部检查（发版前）
#   scripts/check-all.sh --backend --front --db --docs   只跑指定部分，可组合
# 环境变量：
#   BASE=origin/dev   比较的基准，默认 dev 分支（不存在时用 master，再不行用 HEAD）
#   SKIP_DOCKER=1     跳过需要 Docker 的检查（迁移检查、权限矩阵）
set -uo pipefail

ROOT=$(cd -- "$(dirname "$0")/.." && pwd)
cd "$ROOT"

want_backend=0 want_front=0 want_db=0 want_docs=0 auto=1
for arg in "$@"; do
  case "$arg" in
    --all) want_backend=1 want_front=1 want_db=1 want_docs=1 auto=0 ;;
    --backend) want_backend=1 auto=0 ;;
    --front) want_front=1 auto=0 ;;
    --db) want_db=1 auto=0 ;;
    --docs) want_docs=1 auto=0 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "未知参数：${arg}（--help 查看用法）" >&2; exit 2 ;;
  esac
done

if [ -z "${BASE:-}" ]; then
  for candidate in origin/dev dev master HEAD; do
    git rev-parse -q --verify "$candidate" >/dev/null && { BASE=$candidate; break; }
  done
fi

# 改动文件：相对 BASE 的已提交改动 + 工作区改动 + 未跟踪文件
changed=$( { git diff --name-only "$BASE"...HEAD 2>/dev/null; git diff --name-only HEAD; git ls-files --others --exclude-standard; } | sort -u)

if [ "$auto" = 1 ]; then
  echo "$changed" | grep -q '^NUS-backend/' && want_backend=1
  echo "$changed" | grep -q '^NUS-front/' && want_front=1
  echo "$changed" | grep -qE '^(deploy/database/|NUS-doc/3-数据库文件/)' && want_db=1
  echo "$changed" | grep -qE '^NUS-backend/.*(Controller|endpoint-permissions)' && want_db=1
  want_docs=1
  if [ -z "$changed" ]; then
    echo "没有检测到改动（基准 ${BASE}）。需要全量检查时用 --all。"
  fi
fi

results=()
failed=0
run() { # run <名称> <命令…>
  local name=$1; shift
  echo
  echo "==> $name"
  if "$@"; then results+=("通过  $name"); else results+=("失败  $name"); failed=1; fi
}
skip() { results+=("跳过  $1（$2）"); }
have() { command -v "$1" >/dev/null 2>&1; }
mvn_cmd() { if have mvnd; then echo mvnd; else echo mvn; fi; }

# 1. 敏感信息
if have gitleaks; then
  run "敏感信息扫描（gitleaks）" gitleaks dir . --no-banner --redact -v
else
  skip "敏感信息扫描" "未安装 gitleaks，见 docs/03-开发/环境搭建.md"
fi

# 2. 后端
if [ "$want_backend" = 1 ]; then
  if have mvn || have mvnd; then
    run "后端单元测试" bash -c "cd NUS-backend && TZ=Asia/Shanghai $(mvn_cmd) -q test"
  else
    skip "后端单元测试" "未安装 Maven"
  fi
fi

# 3. 前端
if [ "$want_front" = 1 ]; then
  if have pnpm; then
    front_files=$(echo "$changed" | grep -E '^NUS-front/.*\.(ts|tsx|vue|js|mjs)$' | sed 's#^NUS-front/##' | while read -r f; do [ -f "NUS-front/$f" ] && echo "$f"; done)
    run "前端类型检查" bash -c "cd NUS-front && pnpm -s check:type"
    run "前端单元测试" bash -c "cd NUS-front && pnpm -s test:unit"
    if [ -n "$front_files" ]; then
      # shellcheck disable=SC2086
      run "前端 eslint（改动文件）" bash -c "cd NUS-front && npx eslint $(echo $front_files)"
      # shellcheck disable=SC2086
      run "前端 prettier（改动文件）" bash -c "cd NUS-front && npx prettier --check $(echo $front_files)"
    fi
  else
    skip "前端检查" "未安装 pnpm"
  fi
fi

# 4. 数据库与权限（需要 Docker）
if [ "$want_db" = 1 ]; then
  if [ "${SKIP_DOCKER:-0}" = 1 ] || ! have docker; then
    skip "迁移检查与权限矩阵" "需要 Docker（SKIP_DOCKER=1 或未安装）"
  else
    latest=$(ls deploy/database/migrations/V*__*.sql | sed -E 's#.*/V([0-9]+)__.*#\1#' | sort -n | tail -1)
    # 已发布的迁移不再改动，只有迁移或基线有改动时才需要跑最新的迁移检查
    if ! echo "$changed" | grep -qE '^(deploy/database/migrations/|NUS-doc/3-数据库文件/)'; then
      skip "迁移检查 check-v$latest" "迁移和基线没有改动"
    elif [ -f "deploy/database/tests/check-v$latest.py" ]; then
      run "迁移检查 check-v$latest" python3 "deploy/database/tests/check-v$latest.py"
    else
      results+=("失败  迁移检查：缺少 deploy/database/tests/check-v$latest.py（新迁移必须配 check 脚本）"); failed=1
    fi
    run "接口权限矩阵" python3 deploy/database/tests/check-permissions.py
  fi
fi

# 5. 文档同步提醒（不失败，只提醒）
if [ "$want_docs" = 1 ] && [ "$auto" = 1 ]; then
  code_changed=$(echo "$changed" | grep -E '^(NUS-backend|NUS-front)/.*\.(java|xml|ts|vue)$' | grep -vE '(Test\.java|\.test\.ts)$' || true)
  if [ -n "$code_changed" ] && ! echo "$changed" | grep -q '^CHANGELOG.md$'; then
    results+=("提醒  代码有改动但 CHANGELOG.md 没改；用户能看到的变化要写进“未发布”一节")
  fi
  if [ -n "$code_changed" ] && ! echo "$changed" | grep -q '^docs/06-交接/当前状态.md$'; then
    results+=("提醒  会话结束或提交 PR 前，更新 docs/06-交接/当前状态.md")
  fi
fi

echo
echo "================ 结果 ================"
printf '%s\n' "${results[@]}"
exit $failed
