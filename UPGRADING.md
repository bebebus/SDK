# SDK 升级指南

项目通常维护最新稳定版本的安全修复，支持状态见 [SECURITY.md](./SECURITY.md)。升级前阅读 [RELEASE_NOTES.md](./RELEASE_NOTES.md) 中从当前版本到目标版本的所有条目；在测试环境固定目标版本，回归签名、错误处理、回调验签和业务幂等，再按自己的发布流程上线。不同语言和包索引可能存在发布时差，请先核对目标版本存在；不要将示例中的测试地址和密钥用于生产。

包含本变更的版本中，Python 客户端不再自动跟随 HTTP 重定向；遇到 3xx 会抛出带状态码的 `TransportError`。如果部署依赖跳转，应向服务商确认最终 HTTPS 基址并显式配置，勿将生产凭证发送到未经确认的地址。此变更随包含它的后续版本发布，现有已发布包不会自动改变。

## 获取指定版本

- Node.js：`npm install @bebebus/merchant-openapi-sdk@<版本>`；回退时改回已验证的版本并恢复锁文件。
- Python：`python -m pip install 'bebebus-merchant-openapi-sdk==<版本>'`；使用项目的依赖锁定方式记录版本。
- PHP：`composer require bebebus/merchant-openapi-sdk:<版本>`；提交并部署更新后的 `composer.lock`。
- Go：`go get github.com/bebebus/SDK/go/v2@v<版本>`，再运行 `go mod tidy` 和项目测试；对现有项目要审查 `go.mod` 与 `go.sum` 差异。
- Java：本项目不发布 Maven 中央仓库包；使用目标 tag 中的 `java/src/main/java` 替换已引入源码，或自行构建并更新应用引用的 JAR。

不再使用时，先删除应用中的 SDK 调用和引用：Node.js 执行 `npm uninstall @bebebus/merchant-openapi-sdk`，Python 在对应虚拟环境执行 `python -m pip uninstall bebebus-merchant-openapi-sdk`，PHP 执行 `composer remove bebebus/merchant-openapi-sdk`，Go 删除导入后执行 `go mod tidy`；Java 删除引入的 SDK 源码或自行构建的 JAR 及其项目引用。按应用自身流程重新构建和测试。

## 从 1.x 迁移到 2.x

1. 服务端 OpenAPI v2 基址已移除，配置服务商提供的 `/api/open/v1` HTTPS 基址；SDK 不再对代付请求做 v2 到 v1 的自动回退。
2. 删除 v2 的请求绑定参数与 `METHOD\npath\n` 签名前缀；各语言签名方法改用 payload 与 secret 两个参数。Go 的 `BuildSignBaseWithBinding` / `SignWithBinding` 和 Java 的 `SignBinding` 已移除。
3. Go 的导入路径改为 `github.com/bebebus/SDK/go/v2`，执行上面的 `go get` 并更新应用导入。旧的 1.x 路径和标签仍可用于已固定的旧应用。
4. 检查生产基址、密钥来源和超时配置，在测试环境运行现有业务请求及回调回归。回滚时恢复原版本、依赖锁和应用配置；已经移除的服务端 v2 基址不会因客户端回滚而恢复。

完整变更及各语言 API 名称以 [v2.0.0 发布说明](./RELEASE_NOTES.md#v200-2026-08-18--openapi-v2-fully-withdrawn) 和各语言 README 为准。
