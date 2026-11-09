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

## 代码审查

审查者应阅读 PR 的变更和验证结果，确认需求与兼容性影响；涉及接口或签名时核对五语言行为和测试向量，涉及凭证、网络或发布时检查信任边界、密钥暴露与依赖来源。审查者应检查错误处理、回归测试和文档是否覆盖变更，并通过 PR 审查提出问题；未解决的缺陷或失败的必需检查不能作为可接受的合并结果。批准须来自作者以外的维护者；有新提交时以分支保护要求为准。

各语言的自动检查配置在 [CI 工作流](./.github/workflows/ci.yml)：Node.js 使用 Biome，Python 使用 Ruff，PHP 使用 PHPStan，Java 使用 Checkstyle，Go 使用 golangci-lint。贡献应符合这些配置，例外须在 PR 中说明并经审查。

## 本地验证

SDK 不需要安装运行时第三方依赖。完整开发环境需要 Git、Node.js 22/npm、Python 3.12/pip、PHP 8.2（含 cURL 和 JSON）/Composer、JDK 17/Maven 以及 Go 1.21 和 golangci-lint；CI 使用的工具版本和安装方式见 [CI 工作流](./.github/workflows/ci.yml)。在仓库根目录克隆后，依次安装检查工具：

```bash
cd nodejs && npm ci && cd ..
python3 -m pip install --require-hashes -r python/requirements-ci.txt
cd php && composer install && cd ..
```

Java、Go 和基础测试不需额外运行时库；Maven 与 golangci-lint 可能在首次执行时下载构建或检查依赖。提交 Pull Request 前至少运行：

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

覆盖率测量范围、CI 阈值和不同工具的口径见 [COVERAGE.md](./COVERAGE.md)。新增程序源码应包含 `SPDX-License-Identifier: MIT`；修改参考表生成器时也要保留生成文件的标识。

项目获得新的公开成就时，维护者应在 48 小时内将名称及链接加入仓库首页；现有徽章见 [README](./README.md)。

## 适合新贡献者的小任务

先在 [Issues](https://github.com/bebebus/SDK/issues) 搜索或提出计划，避免重复工作；以下任务适合按单一语言或单一文档分拆，不要求一次完成整个项目：

- 对照一种语言的 README 示例与当前公开方法，修复可复现的示例错误并运行该语言测试。
- 为某个尚未覆盖的签名边界或网络错误补充回归测试；先确认行为符合 [签名规范](./SIGNING.md)。
- 改进一种语言的安装、卸载或升级说明，并在干净环境验证相关命令。

需要接口变更或安全漏洞细节的工作先联系维护者；私密内容遵循 [安全策略](./SECURITY.md)。

## 参考代码表维护

国家、币种、银行代码统一维护在 [`appendix/reference-codes.json`](./appendix/reference-codes.json)，不维护支付方式表。`REFERENCE-CODES.md`、四份 CSV 与五种语言的 `catalog.*` 都是生成文件，请勿单独修改。

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

### 公开银行目录维护

`banks` 是项目已登记的助记码，`bank_directories` 是独立发布方的公开参考目录。通道支持子集不能作为某国银行全量。越南目录来源为 Casso 发布的 [VietQR.io Bank API](https://www.vietqr.io/api-bank)，不是越南监管机构的银行法人名录。

- 核验并下载 `https://api.vietqr.io/v2/banks` 的响应，确认业务码为 `00`，更新 `entries`、采集日期与原始响应字节的 SHA-256。不按 `transferSupported` 等外部能力标记过滤，也不把它们当成项目可用状态。
- `reference_code`、`bin`、`name`、`short_name` 保留发布方原文；外部代码可能包含空格或混合大小写，禁止自动改成项目银行码。BIN 保持字符串。
- `platform_bank_code` 仅关联同一国家、已有项目码册的明确对应项；尚未登记的机构设为 null。同品牌跨国法人和不同分支不能自动映射，例如公开目录的 HSBC Vietnam 不能关联香港 HSBC。
- 当前越南 76 项均已登记，项目码册共 77 项。新增登记必须先在主项目码册提交，再同步 SDK 来源提交和指纹；优先复用既有平台码，禁止根据外部代码单独造一套 SDK 编码。码册登记不扩展 adapter 的实际支持子集。
- 更名映射应记录核验来源，放在 `mapping_sources`，保留旧项目助记码以兼容既有接口。不得为凑齐目录新增未经后端码册登记的下单代码。
- 保留项目有码而公开目录暂未收录的条目，例如 LIOBANK。公开目录与项目码册有重叠，两个条数不能直接相加。
- 重新生成并运行检查，审查新增、消失及改名项；此过程不修改 API 的银行映射或扩大通道可用范围。

## 发布

版本和包索引发布流程见 [`PUBLISHING.md`](./PUBLISHING.md)。公共包版本不可覆盖，发布前必须先通过测试、确认版本一致性，并核对 npm、PyPI、Packagist、Go 和 GitHub Release 的同步状态。

## 行为与安全边界

讨论应围绕代码、文档和可复现问题，避免人身攻击、骚扰和公开敏感信息。OpenSSF Scorecard 是自动化供应链安全评估，不等同于人工代码审计或渗透测试；当前公开验证来源和修复进度见 [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md)。
