#!/usr/bin/env bash
#
# 一键发版：bump 版本 → 跑五套测试 → 发布到 npm / PyPI / Go / Packagist。
#
# 用法：
#   ./release.sh <version> [registries...] [--dry-run]
#   <version>      形如 1.0.1 或 v1.0.1（脚本内部统一处理）
#   [registries]   留空=全部；可指定子集：npm pypi go packagist
#   --dry-run      只跑测试 + 打印计划，不 bump/不提交/不发布
#
# 令牌经环境变量注入（缺哪个就跳过对应索引并告警）：
#   NPM_TOKEN      npm 发布令牌（建议 Automation token，绕 2FA）
#   NPM_OTP        可选：npm 2FA 的一次性码（TOTP 或恢复码），用于非 Automation 令牌
#   PYPI_TOKEN     PyPI API token（pypi- 开头）
#
# 示例：
#   NPM_TOKEN=npm_xxx PYPI_TOKEN=pypi_xxx ./release.sh 1.0.1
#   ./release.sh 1.1.0 go packagist            # 只发 Go + Packagist（无需令牌）
#   ./release.sh 1.0.2 --dry-run               # 只测 + 看计划

set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")"
ROOT="$(pwd)"
PHP_MIRROR="git@github.com:bebebus/merchant-openapi-sdk-php.git"
GO_MOD_DIR="go"

# ---------- 参数解析 ----------
DRY_RUN=0
VERSION=""
REGS=()
for a in "$@"; do
  case "$a" in
    --dry-run) DRY_RUN=1 ;;
    --help|-h) sed -n '2,30p' "$0"; exit 0 ;;
    npm|pypi|go|packagist) REGS+=("$a") ;;
    -*) echo "未知选项: $a"; exit 2 ;;
    *) [ -z "$VERSION" ] && VERSION="$a" || { echo "多余参数: $a"; exit 2; } ;;
  esac
done
[ -z "$VERSION" ] && { echo "缺少版本号。用法: ./release.sh <version> [npm pypi go packagist] [--dry-run]"; exit 2; }
[ ${#REGS[@]} -eq 0 ] && REGS=(npm pypi go packagist)

# 规范化：VER=纯数字 1.0.1；TAG=v1.0.1
VER="${VERSION#v}"
TAG="v${VER}"
if ! [[ "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.]+)?$ ]]; then
  echo "版本号格式不对: ${VERSION}（应形如 1.0.1）"; exit 2
fi

want() { for r in "${REGS[@]}"; do [ "$r" = "$1" ] && return 0; done; return 1; }

declare -a RESULTS
record() { RESULTS+=("$1"); }

echo "════════ 发版 $TAG ════════"
echo "目标索引: ${REGS[*]}    dry-run: $DRY_RUN"
echo ""

# ---------- 1. 跑测试（任何一个失败即中止，发布前的硬门槛）----------
echo "──── 跑五套测试 ────"
test_fail=0
run() { echo "› $1"; if ! eval "$2" >/tmp/rel_test.log 2>&1; then echo "  ❌ 失败:"; tail -8 /tmp/rel_test.log | sed 's/^/    /'; test_fail=1; else echo "  ✅"; fi; }
run "Node"   "cd '$ROOT/nodejs' && node --test"
run "Python" "cd '$ROOT/python' && python3 -m unittest discover -s tests"
run "PHP"    "cd '$ROOT/php' && php tests/run.php"
run "Java"   "cd '$ROOT/java' && bash run-tests.sh"
run "Go"     "cd '$ROOT/go' && go test -count=1 ./..."
[ "$test_fail" -ne 0 ] && { echo ""; echo "⛔ 有测试失败，发布中止。"; exit 1; }
echo "全部测试通过。"; echo ""

if [ "$DRY_RUN" -eq 1 ]; then
  echo "──── dry-run 计划（不会真的发布）────"
  want npm      && echo "  npm:       bump nodejs/package.json → ${VER}；npm publish --access public"
  want pypi     && echo "  PyPI:      bump python/pyproject.toml → ${VER}；build + twine upload"
  want go       && echo "  Go:        打并推 tag go/${TAG}（monorepo）"
  want packagist&& echo "  Packagist: subtree split php/ → 推镜像仓 main + tag ${TAG}（自动同步）"
  echo ""; echo "dry-run 结束。去掉 --dry-run 即真正发布。"; exit 0
fi

# ---------- 2. bump 版本（npm/pypi 有版本字段；go/packagist 用 tag）----------
echo "──── bump 版本到 $VER ────"
node -e "const f='$ROOT/nodejs/package.json';const fs=require('fs');const p=JSON.parse(fs.readFileSync(f));p.version='$VER';fs.writeFileSync(f,JSON.stringify(p,null,2)+'\n')" && echo "  nodejs/package.json ✓"
perl -0pi -e 's/^version = "[^"]*"/version = "'"$VER"'"/m' "$ROOT/python/pyproject.toml" && echo "  python/pyproject.toml ✓"
# [L19] 同步 php/java/go 的 VERSION 常量与 java pom（UA 由这些常量单一派生）。
perl -0pi -e "s/(const VERSION = ')[^']*(';)/\${1}$VER\${2}/" "$ROOT/php/src/Client.php" && echo "  php/src/Client.php VERSION ✓"
perl -0pi -e "s/(public static final String VERSION = \")[^\"]*(\";)/\${1}$VER\${2}/" "$ROOT/java/src/main/java/cloud/cniia/openapi/sdk/Client.java" && echo "  java Client.VERSION ✓"
perl -0pi -e "s|(<artifactId>merchant-openapi-sdk</artifactId>\s*<version>)[^<]*(</version>)|\${1}$VER\${2}|s" "$ROOT/java/pom.xml" && echo "  java/pom.xml ✓"
perl -0pi -e "s/(const Version = \")[^\"]*(\")/\${1}$VER\${2}/" "$ROOT/go/client.go" && echo "  go client.go Version ✓"

# ---------- 3. 提交 bump 到 monorepo 并推送（tag 要指向 bump 后的提交）----------
if ! git diff --quiet; then
  git add -A
  git commit -q -m "release: $TAG"
  # [L26] set -e 未开，push 失败必须显式判返回码并中止：
  # 否则 tag 会指向未推送的 bump 提交，导致各索引发布与远端历史不一致。
  if git push origin main >/dev/null 2>&1; then
    echo "  monorepo 已提交并推送 ($TAG)"
  else
    echo "  ⛔ 推送 main 失败，发布中止（已本地提交 bump，请手动排查后重试）。"; exit 1
  fi
else
  echo "  无版本变更（版本号未变），跳过提交"
fi
echo ""

# ---------- 3.1 创建 monorepo 根版本 Tag（供 GitHub Release / provenance 使用）----------
# 旧版本只有 Go 子模块 tag；从这里开始补齐根 tag。默认使用 annotated tag，
# 如发布机已配置签名密钥，可设置 RELEASE_TAG_SIGNING=1 改用签名 tag。
if git ls-remote --tags origin "refs/tags/$TAG" 2>/dev/null | grep -q "$TAG"; then
  echo "  根版本 tag $TAG 已存在，跳过"
else
  if [ "${RELEASE_TAG_SIGNING:-0}" = "1" ]; then
    git tag -s "$TAG" -m "$TAG"
  else
    git tag -a "$TAG" -m "$TAG"
  fi
  if git push origin "$TAG" >/dev/null 2>&1; then
    echo "  根版本 tag $TAG 已推送"
  else
    echo "  ⛔ 根版本 tag 推送失败，发布中止。"; exit 1
  fi
fi
echo ""

# ---------- 4. 逐索引发布 ----------

# npm
if want npm; then
  echo "──── npm ────"
  if [ -z "${NPM_TOKEN:-}" ]; then echo "  ⏭ 跳过：未设 NPM_TOKEN"; record "npm: 跳过(无令牌)"; else
    NPMRC=/tmp/.rel_npmrc; printf '//registry.npmjs.org/:_authToken=%s\n' "$NPM_TOKEN" > "$NPMRC"
    otp_arg=""; [ -n "${NPM_OTP:-}" ] && otp_arg="--otp=${NPM_OTP}"
    if (cd "$ROOT/nodejs" && NPM_CONFIG_USERCONFIG="$NPMRC" npm publish --access public $otp_arg) 2>&1 | tail -3; then
      record "npm: ✅ @bebebus/merchant-openapi-sdk@$VER"
    else record "npm: ❌ 发布失败(看上方输出；2FA 需 Automation 令牌或 NPM_OTP)"; fi
    rm -f "$NPMRC"
  fi; echo ""
fi

# PyPI
if want pypi; then
  echo "──── PyPI ────"
  if [ -z "${PYPI_TOKEN:-}" ]; then echo "  ⏭ 跳过：未设 PYPI_TOKEN"; record "PyPI: 跳过(无令牌)"; else
    VENV=/tmp/.rel_pyvenv
    [ -d "$VENV" ] || python3 -m venv "$VENV"
    "$VENV/bin/pip" install -q --upgrade build twine >/dev/null 2>&1
    (cd "$ROOT/python" && rm -rf dist build ./*.egg-info && "$VENV/bin/python" -m build >/tmp/rel_build.log 2>&1)
    if [ -d "$ROOT/python/dist" ] && ls "$ROOT/python/dist"/* >/dev/null 2>&1; then
      if TWINE_USERNAME=__token__ TWINE_PASSWORD="$PYPI_TOKEN" "$VENV/bin/twine" upload "$ROOT/python/dist"/* 2>&1 | tail -2; then
        record "PyPI: ✅ bebebus-merchant-openapi-sdk@$VER"
      else record "PyPI: ❌ 上传失败(可能版本已存在或令牌无权)"; fi
    else echo "  ❌ 构建失败:"; tail -8 /tmp/rel_build.log | sed 's/^/    /'; record "PyPI: ❌ 构建失败"; fi
    rm -rf "$ROOT/python/dist" "$ROOT/python/build" "$ROOT"/python/*.egg-info
  fi; echo ""
fi

# Go（monorepo 子目录 tag go/vX.Y.Z）
if want go; then
  echo "──── Go ────"
  GOTAG="go/$TAG"
  if git ls-remote --tags origin "refs/tags/$GOTAG" 2>/dev/null | grep -q "$GOTAG"; then
    echo "  ⏭ 远程已存在 tag ${GOTAG}，跳过"; record "Go: 跳过(tag 已存在)"
  else
    git tag "$GOTAG" && git push origin "$GOTAG" >/dev/null 2>&1 \
      && { GOPROXY=https://proxy.golang.org go list -m "github.com/bebebus/SDK/go@$TAG" >/dev/null 2>&1 || true; record "Go: ✅ github.com/bebebus/SDK/go@$TAG"; echo "  ✅ 已推 $GOTAG"; } \
      || record "Go: ❌ 打/推 tag 失败"
  fi; echo ""
fi

# Packagist（subtree split php/ → 推镜像仓 main + tag；镜像仓 webhook 自动同步）
if want packagist; then
  echo "──── Packagist（镜像仓 split）────"
  SPLIT=$(git subtree split --prefix=php 2>/dev/null | tail -1)
  if [ -n "$SPLIT" ]; then
    git push --force "$PHP_MIRROR" "$SPLIT:refs/heads/main" >/dev/null 2>&1 && echo "  ✅ 镜像仓 main 已更新"
    if git ls-remote --tags "$PHP_MIRROR" "refs/tags/$TAG" 2>/dev/null | grep -q "$TAG"; then
      echo "  ⏭ 镜像仓已存在 tag $TAG"; record "Packagist: ✅ main 已更新(tag $TAG 已存在)"
    else
      git tag -a "relsplit-$TAG" "$SPLIT" -m "$TAG" 2>/dev/null
      git push "$PHP_MIRROR" "relsplit-$TAG:refs/tags/$TAG" >/dev/null 2>&1 && echo "  ✅ 镜像仓 tag $TAG 已推（Packagist 将自动同步）"
      git tag -d "relsplit-$TAG" >/dev/null 2>&1
      record "Packagist: ✅ bebebus/merchant-openapi-sdk@$TAG"
    fi
  else record "Packagist: ❌ subtree split 失败"; fi
  git branch -D php-split >/dev/null 2>&1 || true
  echo ""
fi

# ---------- 5. 汇总 ----------
echo "════════ 发版结果 $TAG ════════"
for r in "${RESULTS[@]}"; do echo "  $r"; done
echo ""
echo "提示：发布完到各平台撤销/轮换本次用到的令牌；npm 用 Automation 令牌可免 OTP。"
