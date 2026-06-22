# 发布到包索引

五套 SDK 在一个 monorepo（`github.com/bebebus/SDK`，分支 `main`）。包名统一 scoped 到 `bebebus`，许可证 MIT，版本 `1.0.0`。

| 索引 | 包名 | 安装方式 |
|------|------|----------|
| npm | `@bebebus/merchant-openapi-sdk` | `npm i @bebebus/merchant-openapi-sdk` |
| PyPI | `bebebus-merchant-openapi-sdk`（import 名仍 `openapi_sdk`） | `pip install bebebus-merchant-openapi-sdk` |
| Packagist | `bebebus/merchant-openapi-sdk` | `composer require bebebus/merchant-openapi-sdk` |
| Go (pkg.go.dev) | `github.com/bebebus/SDK/go` | `go get github.com/bebebus/SDK/go@v1.0.0` |

> ⚠️ 发布到公共索引**不可撤销**（版本号永久占用）。令牌只用于发布、勿入库。每次发版先 bump 版本号。

## npm（scoped 公开）

前置：npm 账号须拥有 `@bebebus` scope（个人 scope=用户名 bebebus，或建同名组织）。

```bash
cd nodejs
# 用令牌鉴权（CI 风格，避免交互 npm login）
echo "//registry.npmjs.org/:_authToken=${NPM_TOKEN}" > ~/.npmrc
npm publish --access public      # package.json 已含 publishConfig.access=public
```
验证：`npm view @bebebus/merchant-openapi-sdk version`

## PyPI

前置：PyPI 账号 + API token（`pypi-` 开头）；构建工具 `build`/`twine`（非 SDK 运行依赖，仅发布用）。

```bash
cd python
python3 -m pip install --upgrade build twine
python3 -m build                 # 产出 dist/*.whl 与 *.tar.gz
TWINE_USERNAME=__token__ TWINE_PASSWORD="$PYPI_TOKEN" python3 -m twine upload dist/*
```
验证：`pip index versions bebebus-merchant-openapi-sdk`（或访问 pypi.org/project/bebebus-merchant-openapi-sdk）

## Packagist（PHP）— 需子树拆分

Packagist 只读仓库**根目录**的 `composer.json`，无法识别 monorepo 子目录。须把 `php/` 拆分到独立（只读镜像）仓库再提交：

```bash
# 在 monorepo 根
git subtree split --prefix=php -b php-split
# 推到一个专用仓库（先在 GitHub 建好，例如 bebebus/merchant-openapi-sdk-php）
git push git@github.com:bebebus/merchant-openapi-sdk-php.git php-split:main
```
然后到 https://packagist.org/packages/submit 提交该镜像仓 URL（一次性）；配置 GitHub webhook 后，之后每次推送自动更新。
> 镜像仓的 `composer.json` 即 `php/composer.json`（已是 `bebebus/merchant-openapi-sdk`）。

## Go（pkg.go.dev）— 无需账号

子目录模块用带前缀的 tag：

```bash
git tag go/v1.0.0
git push origin go/v1.0.0
# 触发索引（首次拉取后 pkg.go.dev 自动收录）
GOPROXY=proxy.golang.org go list -m github.com/bebebus/SDK/go@v1.0.0
```

## 发新版本

1. 改对应包清单的 `version`（npm `package.json` / PyPI `pyproject.toml`），PHP/Go 用 git tag。
2. 重跑该语言测试（见根 `README.md`）。
3. 按上面对应小节发布；Go 用新 tag（`go/vX.Y.Z`），Packagist 推新 tag 到镜像仓即可。
