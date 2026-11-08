# 安全策略

## 报告安全问题

当前仓库尚未启用 GitHub Security Advisories 的 Private vulnerability reporting 入口，因此暂不接受通过该入口提交的私密报告。请先通过仓库 [Issues](https://github.com/bebebus/SDK/issues) 联系维护者，并只说明需要进行安全沟通，不要在公开 Issue、Pull Request 或讨论区发布可利用细节、密钥、真实商户数据或完整攻击步骤。私密报告入口启用后，本文件会同步更新。

在维护者提供私下沟通渠道后，报告中请尽量包含：

- 受影响的 SDK 语言和版本；
- 受影响的文件、接口或调用方式；
- 可复现步骤或最小化示例；
- 影响范围和可能的缓解措施。

在私密报告入口启用前，Issues 仅用于发起不含敏感信息的私下沟通请求，不用于提交漏洞细节。

## 响应与披露

- 维护者的目标是在 3 个工作日内确认收到报告；
- 目标是在确认后 10 个工作日内完成初步影响评估，并反馈修复计划或所需补充信息；
- 修复版本、受影响版本和公开披露时间由维护者与报告者结合风险协商确定；
- 已确认的问题会优先通过补丁版本、GitHub Security Advisory 和发布说明公开修复信息。

以上时间是响应目标，不构成对特定事件的服务等级承诺。

## 支持版本

通常只对最新稳定版本提供安全修复。当前发布线如下：

| 版本线 | 状态 |
| --- | --- |
| `2.2.x` | 当前稳定发布线 |
| 其他版本 | 不承诺修复，请升级至最新稳定版并重新验证 |

Node.js、Python、PHP、Java 和 Go SDK 的发布渠道、版本号和 PHP 镜像仓库关系见 [`PUBLISHING.md`](./PUBLISHING.md)。

## 安全边界与使用者责任

SDK 提供请求签名和回调验签，不替商户保管密钥、确认业务授权或保证订单处理幂等。商户应保护凭证和日志、校验上游地址及回调内容，并在验签成功后自行核对订单、金额与状态。生产请求应使用服务商提供的 HTTPS 地址；本地联调的 HTTP 例外不得用于传输生产凭证。Java 显式选择 SANDBOX 时允许自定义非 HTTPS 地址，应只用于可信测试环境。HTTPS 证书与协议能力还依赖运行时、系统信任库和调用方注入的传输实现。

SDK 不提供回调重放防护、服务端权限隔离、资金一致性或主机安全保障。信任边界、威胁与已知限制见 [`ARCHITECTURE.md`](./ARCHITECTURE.md)；签名契约见 [`SIGNING.md`](./SIGNING.md)。

## 第三方验证边界

仓库接入的 [OpenSSF Scorecard](https://scorecard.dev/viewer/?uri=github.com/bebebus/SDK) 是持续运行的自动化开源供应链安全评估，不等同于独立安全公司的人工代码审计、渗透测试或合规认证。详细结果和修复记录见 [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md) 与 [`SECURITY-REMEDIATION-PLAN.md`](./SECURITY-REMEDIATION-PLAN.md)。
