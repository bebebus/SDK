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

提交前还应运行五种语言的静态检查：

```bash
cd nodejs && npm ci && npm run lint && cd ..
cd python && python3 -m pip install -e '.[dev]' && ruff check . && cd ..
cd php && composer install && composer run lint && cd ..
cd java && mvn --batch-mode --no-transfer-progress verify && cd ..
cd go && golangci-lint run && cd ..
```

Go 的回调验签 fuzz 目标可单独运行：

```bash
cd go
go test -fuzz=FuzzVerifyCallbackNeverPanics -fuzztime=30s .
```

工作流 Action 必须固定到完整提交 SHA；修改 `.github/workflows/` 后建议使用 `actionlint` 检查语法和表达式。

## 参考代码表维护

国家、币种、银行代码统一维护在 [`appendix/reference-codes.json`](./appendix/reference-codes.json)，不维护支付方式表。`REFERENCE-CODES.md`、三份 CSV 与五种语言的 `catalog.*` 都是生成文件，请勿单独修改。

1. 核对源项目 `project-p`：国家/币种来自 `web-shared/src/openapi/catalog.ts` 与基线迁移；香港扩展来自 `api/src/channels/ttpay/config.js`；银行来自 `api/src/channels/_shared/{ph,hk,cn,bd,mm,vn}Banks.js`。只读取源码，不读取生产库或商户授权数据。
2. 新增、改名或停用地区、币种、银行代码时，在同一开发任务中检查 SDK 是否需要同步。更新 JSON 的数据、`updated_at`、`source.revision` 与所核对文件的 SHA-256。提交前确认源文件已提交，revision 能还原本次所用文件；指纹按文件原始字节计算。
3. 银行仅保留 `country/bank_code/bank_name/source`，不得复制 `upstream`、`clearing`、适配器配置、凭据或环境地址。HK 只取具有 `clearing` 的真实银行，再剔除该字段；FPS 为转账轨道。银行名沿用码册原文，跨国同码保留；新国家的码册需补入 `source.files`。
4. 运行生成和核验：

   ```bash
   node _tooling/reference-codes.mjs --verify-source ../project-p
   node _tooling/reference-codes.mjs
   node _tooling/reference-codes.mjs --check
   cd nodejs && node --test
   ```

   `--verify-source` 比较源文件指纹，发现变化会失败并提示核对，不自动接受源码变化。CI 的 Node.js 测试检查唯一键、关联国家/币种、内部字段隔离、CSV 转义及全部生成文件一致性，不需要访问源项目。

5. 审查生成差异与三类代码的条数、地区范围，并按上方开发流程提交 Pull Request。参考表包含预登记和历史项；商户当前可用国家、组合与银行始终通过接口确认。五语言 `catalog.*` 的原有字段与导出名保持兼容，银行完整数据通过通用 JSON/CSV 获取。

## 发布

版本和包索引发布流程见 [`PUBLISHING.md`](./PUBLISHING.md)。公共包版本不可覆盖，发布前必须先通过测试、确认版本一致性，并核对 npm、PyPI、Packagist、Go 和 GitHub Release 的同步状态。

## 行为与安全边界

讨论应围绕代码、文档和可复现问题，避免人身攻击、骚扰和公开敏感信息。OpenSSF Scorecard 是自动化供应链安全评估，不等同于人工代码审计或渗透测试；当前公开验证来源和修复进度见 [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md)。
