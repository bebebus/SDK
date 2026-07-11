# 安全策略

## 报告安全问题

请优先通过 [GitHub Security Advisories](https://github.com/bebebus/SDK/security/advisories/new) 私下报告安全问题。请不要在公开 Issue、Pull Request 或讨论区发布可利用细节、密钥、真实商户数据或完整攻击步骤。

报告中请尽量包含：

- 受影响的 SDK 语言和版本；
- 受影响的文件、接口或调用方式；
- 可复现步骤或最小化示例；
- 影响范围和可能的缓解措施。

如果无法使用 Security Advisories，也可以先通过仓库 [Issues](https://github.com/bebebus/SDK/issues) 联系维护者，但请只说明需要私下沟通，不要公开漏洞细节。

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
| `1.1.x` | 支持 |
| 低于 `1.1.x` | 不承诺修复，请先升级并重新验证 |

Node.js、Python、PHP、Java 和 Go SDK 的发布渠道、版本号和 PHP 镜像仓库关系见 [`PUBLISHING.md`](./PUBLISHING.md)。

## 第三方验证边界

仓库接入的 [OpenSSF Scorecard](https://scorecard.dev/viewer/?uri=github.com/bebebus/SDK) 是持续运行的自动化开源供应链安全评估，不等同于独立安全公司的人工代码审计、渗透测试或合规认证。详细结果和修复记录见 [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md) 与 [`SECURITY-REMEDIATION-PLAN.md`](./SECURITY-REMEDIATION-PLAN.md)。
