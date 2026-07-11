# OpenSSF Scorecard 修复方案

## 1. 基线

报告来源：[OpenSSF Scorecard Viewer](https://scorecard.dev/viewer/?uri=github.com/bebebus/SDK)；对应的[公开 API 结果](https://api.scorecard.dev/projects/github.com/bebebus/SDK)生成于 **2026-07-11 07:23 UTC**，扫描提交为 `b23e6008cd7de5a6581be2a2984154e7f65a755d`，Scorecard 版本为 `v5.3.0`。

当前总分为 **3.2/10**。

这份报告衡量的是仓库的开源供应链安全实践，不是人工代码审计、渗透测试或合规认证。修复完成后必须重新运行 Scorecard，以新报告作为验收依据。

## 2. 现状判断

### 已通过或暂不需要处理

| 检查项 | 当前分数 | 判断 |
| --- | ---: | --- |
| Binary-Artifacts | 10 | 未发现二进制文件。 |
| Dangerous-Workflow | 10 | 未发现报告定义的危险工作流模式。 |
| Vulnerabilities | 10 | 当前未发现已知漏洞。 |
| License | 10 | 已识别 MIT License。 |
| Maintained | 0 | 仓库创建未满 90 天，属于时间窗口观察项；持续维护后会自然消除。 |
| Contributors | 0 | 当前没有来自多个组织的贡献者，不是代码缺陷，不应通过制造贡献记录来刷分。 |

### 需要修复的仓库问题

| 优先级 | 检查项 | 当前分数 | 报告原因 | 目标 |
| --- | --- | ---: | --- | --- |
| P0 | Pinned-Dependencies | 3 | `ci.yml` 中 `checkout`、运行时安装 Action 未全部按提交 SHA 固定。 | 所有 `uses:` 均使用完整 40 位提交 SHA，并保留版本注释。 |
| P0 | Token-Permissions | 0 | `ci.yml` 未声明顶层 `permissions`。 | 顶层只读，任务级权限按需收紧；测试任务不授予写权限。 |
| P0 | Security-Policy | 0 | 缺少根目录 `SECURITY.md`。 | 公布漏洞报告入口、支持版本、响应和披露规则。 |
| P0 | Dependency-Update-Tool | 0 | 没有 Dependabot 或 Renovate 配置。 | 由自动更新工具持续维护 GitHub Actions 及各语言清单。 |
| P1 | SAST | 0 | 未检测到静态分析工具。 | 接入 CodeQL；必要时为 PHP 增加可被 CI 阻断的 Semgrep/PHPStan 检查。 |
| P1 | Packaging | -1 | 未检测到 GitHub/GitLab 发布工作流。 | 把测试、打包、发布和失败回滚条件纳入 GitHub Actions。 |
| P1 | Signed-Releases | -1 | 未检测到可识别的签名发布。 | 使用签名 Tag、GitHub Release、构建证明和包生态 provenance。 |
| P1 | CII-Best-Practices | 0 | 尚未申请 OpenSSF Best Practices Badge。 | 基础治理项完成后申请并持续维护徽章。 |
| P2 | Fuzzing | 0 | 未检测到 fuzz 集成。 | 先为 Go 的签名/解析边界增加原生 fuzz 测试，再评估跨语言方案。 |

### 需要 GitHub 设置或真实协作记录的问题

| 检查项 | 当前分数 | 原因 | 处理方式 |
| --- | ---: | --- | --- |
| Code-Review | 0 | 报告只找到 `0/20` 个已批准变更集。 | 在 `main` 上启用 PR 必须经过审批；后续通过真实 PR 累积样本。 |
| CI-Tests | -1 | 报告生成时没有可审计的已合并 PR 样本。 | 保留现有 `pull_request` CI，并把五语言检查设为必需状态检查；后续通过真实 PR 验证。 |
| Branch-Protection | 0 | `main` 未启用分支保护。 | 在 GitHub Rulesets/Branch protection 中配置，不能仅靠仓库文件完成。 |

## 3. 分阶段实施

### 阶段一：低风险供应链基线（P0）

1. 修改 `.github/workflows/ci.yml`：
   - 将全部 `actions/checkout`、`actions/setup-*` 和 `shivammathur/setup-php` 固定到完整提交 SHA；
   - 每个 SHA 后保留类似 `# v4.x.x` 的可读版本注释；
   - 增加 `permissions: contents: read`，不为测试 Job 授予 `write` 权限；
   - 保持现有 Node、Python、PHP、Java、Go 五套测试命令不变。
2. 新增 `.github/dependabot.yml`，至少覆盖：
   - `github-actions`：根目录，每周检查；
   - `npm`：`/nodejs`；
   - `pip`：`/python`；
   - `composer`：`/php`；
   - `maven`：`/java`；
   - `gomod`：`/go`。
3. 新增根目录 `SECURITY.md`：
   - 首选 GitHub Security Advisories；
   - 说明不应在公开 Issue 发布可利用细节；
   - 写明当前支持版本、响应时限、修复/披露流程；
   - 与现有 `SECURITY-AUDIT.md` 的“不是人工审计”边界保持一致。

验收：`git grep` 不再发现 `ci.yml` 中使用版本 Tag 的 Action；Scorecard 的 Pinned-Dependencies、Token-Permissions、Security-Policy、Dependency-Update-Tool 重新扫描后不再因上述原因告警；五语言 CI 全部通过。

### 阶段二：静态分析与发布可信度（P1）

1. 新增 CodeQL 工作流，分析 JavaScript、Python、Java/Kotlin 和 Go；所有 Action 继续按完整 SHA 固定，权限仅授予 `contents: read`、`actions: read` 和 `security-events: write`。PHP 若需要覆盖，另行评估 Semgrep 或 PHPStan，并将结果作为 PR 必需检查。
2. 新增发布工作流，建议流程为：
   - Tag/Release 触发；
   - 先运行与 `release.sh` 一致的五语言测试；
   - 生成 npm、PyPI、PHP 和 Go 对应发布输入；
   - npm、PyPI 优先使用 Trusted Publishing/OIDC，不把长期 Token 写入仓库；
   - PHP 继续同步到独立仓库 `bebebus/merchant-openapi-sdk-php`，再由 Packagist 同步 `bebebus/merchant-openapi-sdk`；
   - Java 当前是 source-only，不应伪装成 Maven Central 包；如需 Maven 发布，应单独补齐坐标、构建和兼容性承诺；
   - 生成 GitHub Release，并记录每个生态的版本、提交和校验信息。
3. 为 Release 和发布包增加可信来源：
   - 使用受保护的版本 Tag；
   - 使用 GitHub artifact attestations 或 Sigstore 生成构建证明；
   - npm/PyPI 发布启用 provenance（生态支持时）；
   - 在发布说明中明确 PHP 镜像仓库和 Packagist 的同步关系。

验收：Scorecard 能识别发布工作流和至少一个公开 Release；发布流程在无凭据时安全失败；测试失败时不发布任何包；发布后 npm、PyPI、Packagist、Go proxy 的版本与 GitHub Release 一致；签名/attestation 可独立验证。

### 阶段三：协作治理与长期项（P2）

1. 在 GitHub 为 `main` 配置 Ruleset：
   - 禁止直接 Push、强制 Push 和删除分支；
   - 必须通过 PR；
   - 至少 1 名维护者审批；
   - 五语言 CI、Scorecard/CodeQL（启用后）作为必需检查；
   - 对管理员也生效；
   - 发布 Tag 使用同等保护策略。
2. 后续每次改动走真实 PR，不创建空 PR 只为满足 Scorecard 样本要求。这样可同时积累 Code-Review 和 CI-Tests 的有效证据。
3. 申请 [OpenSSF Best Practices Badge](https://bestpractices.dev/)，将状态链接加入 README；徽章申请前先完成安全策略、分支保护、依赖更新、漏洞响应和发布说明。
4. 在 Go 测试中增加原生 `Fuzz*` 用例，优先覆盖签名解析、输入规范化、金额/订单字段边界；将 fuzz 用例纳入定期 CI，而不是每次 PR 无限运行。

## 4. 建议的执行顺序

```text
固定 CI Action + 最小权限
          ↓
SECURITY.md + Dependabot
          ↓
GitHub main Ruleset + 真实 PR 必检项
          ↓
CodeQL / PHP 静态分析
          ↓
发布工作流 + provenance / attestation
          ↓
OpenSSF Best Practices + Go fuzzing
          ↓
重新运行 Scorecard，按新报告关闭问题
```

## 5. 变更边界与发布策略

- 仅新增治理文件、工作流和测试时，不需要重新发布 npm、PyPI、Go 或 Java 包；推送到 `main` 后等待 Scorecard 运行即可。
- 修改包内 README、源代码或发布产物时，才需要按现有多生态发布流程提升版本；已经发布的 npm/PyPI 版本不能原地覆盖。
- PHP 发布仍需先同步 `bebebus/merchant-openapi-sdk-php`，再确认 Packagist 的 `bebebus/merchant-openapi-sdk` 已更新。
- 不把“Scorecard 分数提高”作为唯一安全结论；必须同时检查 CI、发布包、依赖漏洞、签名证明和漏洞响应入口。

## 6. 完成定义

修复项完成的最低标准：

1. P0 四项已提交并通过五语言 CI；
2. `main` Ruleset 已启用，且下一次真实 PR 能被必需检查拦截；
3. CodeQL/静态分析、发布工作流和签名证明至少各有一次成功运行；
4. PHP 镜像仓库与 Packagist 的同步链路通过一次非破坏性版本验证；
5. 重新生成 Scorecard 报告，并逐项核对报告原因已消失；
6. 对仍为 0 分的 Maintained、Contributors 等观察项写明“非缺陷/需时间或组织条件”，不通过虚假活动修复。
