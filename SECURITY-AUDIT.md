# SDK 第三方安全验证

本页记录 SDK 可公开复核的第三方安全验证来源。

> 重要边界：OpenSSF Scorecard 是持续运行的自动化开源供应链安全评估，不等同于由独立安全公司出具的人工代码审计、渗透测试或合规认证报告。任何对外宣传都应保留这一限定。

## OpenSSF Scorecard

SDK 已加入 OpenSSF Scorecard 工作流，扫描 `bebebus/SDK` 的仓库安全实践，并将结果发布到 Scorecard 公共结果服务和 GitHub Code Scanning。

- 最新结果：[OpenSSF Scorecard report](https://scorecard.dev/viewer/?uri=github.com/bebebus/SDK)
- 公共结果 API：[Scorecard API](https://api.scorecard.dev/projects/github.com/bebebus/SDK)
- 仓库工作流：[`.github/workflows/scorecard-analysis.yml`](./.github/workflows/scorecard-analysis.yml)
- 评估规则：[Scorecard checks](https://github.com/ossf/scorecard/blob/main/docs/checks.md)
- 结果来源项目：[OpenSSF Scorecard](https://github.com/ossf/scorecard)

报告会随 `main` 分支的 GitHub Actions 运行持续更新。当前基线结果（2026-07-11 07:23 UTC）为 **3.2/10**，扫描提交为 `b23e600`。具体问题与修复顺序见 [`SECURITY-REMEDIATION-PLAN.md`](./SECURITY-REMEDIATION-PLAN.md)。

## 依赖与供应链信息

依赖图、已知漏洞、许可证和包元数据可通过 Google Open Source Insights（deps.dev）复核：

- [Open Source Insights](https://deps.dev/)
- [Open Source Insights FAQ](https://docs.deps.dev/faq/)

SDK 运行时不声明第三方依赖；该来源主要用于复核发布包元数据、包来源和未来新增依赖的传递风险。

## 当前审计状态

| 项目 | 状态 |
| --- | --- |
| OpenSSF Scorecard 工作流 | 已配置，当前公开结果为 3.2/10；会随工作流持续更新 |
| GitHub Code Scanning | 由 Scorecard 工作流上传 SARIF |
| 依赖供应链复核 | 可通过 deps.dev 公开复核 |
| 独立人工代码审计 / 渗透测试 | 尚未委托，不宣称已完成 |

安全问题请通过 [GitHub Security Advisories](https://github.com/bebebus/SDK/security/advisories) 报告，不要在公开 Issue 中披露可利用细节。
