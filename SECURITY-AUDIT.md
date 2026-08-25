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

报告会随 `main` 分支的 GitHub Actions 运行持续更新。当前已复核基线（2026-08-22 07:06:24 UTC）为 **8.7/10**，扫描提交为 `59c4135`，Scorecard 版本为 `v5.5.0`。这是带时间戳的快照；查询最新状态时应以公共结果 API 为准。剩余扣分和修复边界见 [`SECURITY-REMEDIATION-PLAN.md`](./SECURITY-REMEDIATION-PLAN.md)。

当前结果中，Security-Policy、Code-Review、Dependency-Update-Tool、Token-Permissions、Signed-Releases、Vulnerabilities、Packaging、Fuzzing、SAST、License 和 CI-Tests 等检查均为 10 分。尚未满分的检查包括：Pinned-Dependencies（8）、Branch-Protection（8）、CII-Best-Practices（5），以及受仓库年龄或贡献者组织分布影响的 Maintained（0）和 Contributors（0）。

## 依赖与供应链信息

依赖图、已知漏洞、许可证和包元数据可通过 Google Open Source Insights（deps.dev）复核：

- [Open Source Insights](https://deps.dev/)
- [Open Source Insights FAQ](https://docs.deps.dev/faq/)

SDK 运行时不声明第三方依赖；该来源主要用于复核发布包元数据、包来源和未来新增依赖的传递风险。

## 当前审计状态

| 项目 | 状态 |
| --- | --- |
| OpenSSF Scorecard 工作流 | 已配置；2026-08-22 基线为 8.7/10，扫描提交 `59c4135` |
| CI / CodeQL | `59c4135` 对应的 [CI](https://github.com/bebebus/SDK/actions/runs/32558710542) 和 [CodeQL](https://github.com/bebebus/SDK/actions/runs/32558710548) 均成功 |
| GitHub Code Scanning | 2026-08-25 复核有 5 条 OpenSSF Pinned-Dependencies 中危告警，均来自工作流中的 npm 安装命令 |
| Dependabot 安全告警 | 2026-08-25 复核为 0 条 open 告警 |
| 依赖供应链复核 | 可通过 deps.dev 公开复核 |
| 独立人工代码审计 / 渗透测试 | 尚未委托，不宣称已完成 |

安全问题请通过 [GitHub Security Advisories](https://github.com/bebebus/SDK/security/advisories) 报告，不要在公开 Issue 中披露可利用细节。
