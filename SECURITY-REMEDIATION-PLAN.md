# OpenSSF Scorecard 修复方案

## 1. 基线与进展

初始基线生成于 **2026-07-11 07:23 UTC**：扫描提交 `b23e6008cd7de5a6581be2a2984154e7f65a755d`，Scorecard `v5.3.0`，总分 **3.2/10**。

最新已复核基线生成于 **2026-08-22 07:06:24 UTC**：扫描提交 `59c4135af0fae7ab3ebf0eac252936d5719a99ef`，Scorecard `v5.5.0`，总分 **8.7/10**。

- [OpenSSF Scorecard Viewer](https://scorecard.dev/viewer/?uri=github.com/bebebus/SDK)
- [公开 API 结果](https://api.scorecard.dev/projects/github.com/bebebus/SDK)

这份报告衡量的是仓库的开源供应链安全实践，不是人工代码审计、渗透测试或合规认证。每项修复都必须通过新的 Scorecard、CI、CodeQL 和 GitHub 告警状态复核，不能只以总分作为验收依据。

## 2. 当前状态

| 检查项 | 分数 | 当前判断 |
| --- | ---: | --- |
| Security-Policy、Code-Review、Dependency-Update-Tool | 10 | 安全策略、真实 PR 审批和自动依赖更新均已建立。 |
| Binary-Artifacts、Dangerous-Workflow、Token-Permissions | 10 | 未发现二进制污染或危险工作流模式，工作流令牌遵循最小权限。 |
| Signed-Releases、Vulnerabilities、Packaging | 10 | 已有签名/来源证明的 Release，未发现公开已知漏洞，发布流程可识别。 |
| Fuzzing、SAST、License、CI-Tests | 10 | 已接入 fuzz、CodeQL、MIT License 和五语言 CI。 |
| Pinned-Dependencies | 8 | 仍有 5 条 npmCommand 告警，详见下一节。 |
| Branch-Protection | 8 | 主分支保护已启用；当前只要求 1 个审批且未要求 CODEOWNERS 复核。 |
| CII-Best-Practices | 5 | 仍可继续完善 OpenSSF Best Practices 项目资料。 |
| Maintained | 0 | 仓库创建未满报告观察窗口，属于时间项。 |
| Contributors | 0 | 尚无来自多个组织的贡献记录，不应通过虚假活动刷分。 |

2026-08-25 的实时复核结果：`59c4135` 对应的 [CI](https://github.com/bebebus/SDK/actions/runs/32558710542)、[CodeQL](https://github.com/bebebus/SDK/actions/runs/32558710548) 和 [Scorecard](https://github.com/bebebus/SDK/actions/runs/32558710541) 均成功；Dependabot 有 0 条 open 安全告警；Code Scanning 有 5 条 OpenSSF Pinned-Dependencies 中危告警。

## 3. 剩余工作

### P1：消除 Pinned-Dependencies 告警

当前 5 条告警的来源如下：

- 4 条位于 `pull-request-reviewer.lock.yml` 和 `repository-maintainer.lock.yml`，是 `gh-aw` 自动生成的 Codex CLI npm 安装步骤；这些 lock 文件明确禁止手工修改。
- 1 条位于 `release.yml`，来源是发布前执行 `npm install -g npm@latest`。

修复原则：

1. 不通过把命令改写成扫描器识别不到的形式来规避告警。
2. `gh-aw` 生成项优先等待上游提供带完整性校验的安装方式；如需仓库侧修复，应使用独立、带 `package-lock.json` 的工具目录和 `npm ci`，并分别验证主 Agent 与 Threat Detection 作业，不能直接编辑生成的 lock 文件。
3. 发布流程应改为使用已满足 Trusted Publishing 要求的固定 Node/npm 运行时，移除 `npm@latest` 动态升级；变更后必须用非发布演练验证 npm OIDC 登录、幂等版本守卫和打包结果。

验收：新的 Scorecard 扫描将 Pinned-Dependencies 提升为 10，关联 Code Scanning 告警自动关闭，同时 SDK Pull Request Reviewer、SDK Repository Maintainer 和 Release 工作流均有成功验证记录。

### P2：治理与长期观察项

- Branch-Protection：只有在实际治理需要时提高审批数或要求 CODEOWNERS；不要仅为分数增加无效流程。
- CII-Best-Practices：补齐并持续维护项目资料，将徽章状态链接加入 README。
- Maintained / Contributors：随真实维护历史和外部贡献自然积累，不制造提交、审批或贡献组织记录。

## 4. 持续验收

每次安全治理变更后按以下顺序复核：

1. 对变更过的 workflow 运行语法/生成一致性检查；
2. 通过真实 PR 执行五语言 CI 和 CodeQL；
3. 核对 Code Scanning 与 Dependabot 的 open 告警；
4. 发布相关变更使用非发布演练，正式发布后独立验证签名和 provenance；
5. 等待 `main` 的 Scorecard 成功运行，再以新的公共 API 结果更新基线文档。

治理文件、工作流和测试本身不要求重新发布 SDK 包。修改发布产物时，仍需遵守现有多生态版本流程；已发布的 npm/PyPI 版本不得原地覆盖。
