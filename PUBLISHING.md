# 发布到包索引

五套 SDK 在一个 monorepo（`github.com/bebebus/SDK`，分支 `main`）。包名统一 scoped 到 `bebebus`，许可证 MIT，当前版本 `1.1.2`。

**仅以下 4 套上架公共包索引；Java 不发 Maven，只做源码引入**（把 `java/src/main/java` 加入工程或自行 `pom.xml` 打 jar）。故 `release.sh` 的发布闭环**不含 Java**。

| 索引 | 包名 | 安装方式 |
|------|------|----------|
| npm | `@bebebus/merchant-openapi-sdk` | `npm i @bebebus/merchant-openapi-sdk` |
| PyPI | `bebebus-merchant-openapi-sdk`（import 名仍 `openapi_sdk`） | `pip install bebebus-merchant-openapi-sdk` |
| Packagist | `bebebus/merchant-openapi-sdk` | `composer require bebebus/merchant-openapi-sdk` |
| Go (pkg.go.dev) | `github.com/bebebus/SDK/go` | `go get github.com/bebebus/SDK/go@v1.1.0` |
| Java | —（不发 Maven） | 源码引入：把 `java/src/main/java` 加入工程，或 `cd java && mvn package` 自行打 jar |

> ⚠️ 发布到公共索引**不可撤销**（版本号永久占用）。令牌只用于发布、勿入库。每次发版先 bump 版本号。

## GitHub Actions 发布流程

仓库提供手动触发的 [`release.yml`](./.github/workflows/release.yml)。它会先验证已存在的版本 Tag、运行五语言测试、生成发布文件、构建 provenance 并创建 GitHub Release。

默认只构建和创建 GitHub Release；只有在手动输入 `publish_registries=true` 且已配置 `release` 环境和 `PHP_MIRROR_TOKEN` 时，才会发布 npm、PyPI，并同步 PHP 镜像仓。npm/PyPI 使用 Trusted Publishing/OIDC，不在仓库中保存长期令牌。Java 仍然只发布源码归档，不发布 Maven 包。

## 一键发版（推荐）：`release.sh`

`./release.sh <version>` 一条命令完成：bump 版本 → 跑五套测试（不过即中止）→ 发布 npm + PyPI + Go tag + Packagist 镜像仓。令牌经环境变量注入、用完不留盘；各索引独立成败互不阻塞。

从 `v1.1.3` 起脚本还会创建 monorepo 根版本 tag（默认 annotated tag），供 GitHub Release 和构建 provenance 使用。发布机配置了 GPG 签名密钥时，可设置 `RELEASE_TAG_SIGNING=1` 创建签名 tag。

```bash
# 全部索引（npm/PyPI 需令牌；缺令牌则跳过对应项并告警）
NPM_TOKEN=npm_xxx PYPI_TOKEN=pypi_xxx ./release.sh 1.0.1
NPM_TOKEN=npm_xxx NPM_OTP=123456 PYPI_TOKEN=pypi_xxx ./release.sh 1.0.1   # 非 Automation 令牌补 2FA 码

./release.sh 1.1.0 go packagist     # 只发指定子集（Go/Packagist 无需令牌）
./release.sh 1.0.2 --dry-run        # 只跑测试 + 打印计划，不发布
```

下面各小节是**手动/原理说明**（脚本即按此执行）。

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

## Packagist（PHP）— 经独立镜像仓（已建好）

Packagist 只读仓库**根目录**的 `composer.json`，识别不了 monorepo 子目录。故 `php/` 已用 `git subtree split` 拆到独立只读镜像仓
**`git@github.com:bebebus/merchant-openapi-sdk-php.git`**（`main` 分支 + tag `v1.0.0` 已推，根目录即 `php/composer.json` = `bebebus/merchant-openapi-sdk`）。

**首次提交到 Packagist（一次性，需 Packagist 账号）**：
1. 登录 packagist.org → https://packagist.org/packages/submit
2. 填镜像仓 `https://github.com/bebebus/merchant-openapi-sdk-php` → Check → Submit
3. 自动更新：包页 Settings 配 GitHub 集成 / webhook，之后镜像仓每次 push 自动同步

**发新版本（每次 `php/` 改动后，在 monorepo 根）**：
```bash
git subtree split --prefix=php -b php-split
git push git@github.com:bebebus/merchant-openapi-sdk-php.git php-split:main   # 更新可加 --force
# 打稳定版 tag（zsh 下 $split:ref 会被误解析，用字面 SHA 或 ${split} 大括号）
split=$(git subtree split --prefix=php)
git tag -a phpsplit-v1.0.1 "$split" -m v1.0.1
git push git@github.com:bebebus/merchant-openapi-sdk-php.git phpsplit-v1.0.1:refs/tags/v1.0.1
git tag -d phpsplit-v1.0.1
git branch -D php-split
```

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
