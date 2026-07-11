# 贡献指南

感谢参与 Merchant Payment OpenAPI SDK。项目欢迎通过 Issue 讨论问题和改进建议，并通过 Pull Request 提交代码、测试和文档。

## 提交前

- 不要提交生产密钥、真实商户数据、访问令牌或其他凭据；安全问题请遵循 [`SECURITY.md`](./SECURITY.md) 的私下报告流程。
- 先确认改动范围和兼容性影响；跨语言行为变更必须同步检查五套 SDK。
- 新增或修改功能时，同时补充对应语言测试；涉及签名规则时，更新 `test-vectors.json` 并验证所有语言的向量。
- 文档、测试和工作流变更也应说明验证方式。

## 开发流程

1. 从最新 `main` 创建主题分支，并在分支中完成改动。
2. 使用简体中文填写提交说明，采用 `feat`、`fix`、`docs`、`test`、`chore` 等前缀；一次提交聚焦一个目的。
3. 打开 Pull Request，描述问题、方案、影响范围和验证结果；不要直接 Push `main`。
4. Pull Request 必须通过 Node.js、Python、PHP、Java、Go 五套 CI 和 CodeQL 检查，并至少获得一名维护者审批。
5. 根据审查意见更新分支；合并前确保新增提交仍然通过全部必需检查，合并使用仓库允许的线性历史方式。

## 本地验证

SDK 不需要安装运行时第三方依赖。提交 Pull Request 前至少运行：

```bash
cd nodejs && node --test && cd ..
cd python && python3 -m unittest discover -s tests && cd ..
cd php && php tests/run.php && cd ..
cd java && bash run-tests.sh && cd ..
cd go && go test -count=1 ./... && cd ..
```

Go 的回调验签 fuzz 目标可单独运行：

```bash
cd go
go test -fuzz=FuzzVerifyCallbackNeverPanics -fuzztime=30s .
```

工作流 Action 必须固定到完整提交 SHA；修改 `.github/workflows/` 后建议使用 `actionlint` 检查语法和表达式。

## 发布

版本和包索引发布流程见 [`PUBLISHING.md`](./PUBLISHING.md)。公共包版本不可覆盖，发布前必须先通过测试、确认版本一致性，并核对 npm、PyPI、Packagist、Go 和 GitHub Release 的同步状态。

## 行为与安全边界

讨论应围绕代码、文档和可复现问题，避免人身攻击、骚扰和公开敏感信息。OpenSSF Scorecard 是自动化供应链安全评估，不等同于人工代码审计或渗透测试；当前公开验证来源和修复进度见 [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md)。
