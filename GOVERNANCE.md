# 项目治理与维护

本仓库维护五种语言的商户 OpenAPI SDK。[baobao84](https://github.com/baobao84) 是项目维护者，负责组织问题分诊、审查贡献、维护版本与文档，并协调安全报告和发布工作。具有 GitHub 仓库权限不自动代表某人是项目维护者，也不表示其拥有各包索引的发布权限。

## 决策与贡献

任何人可以通过 Issue 讨论需求和缺陷，通过 Pull Request 提交改动。维护者结合接口兼容性、安全性、测试结果和维护成本决定是否接受；重大变更应在对应公开讨论或 PR 中说明原因与影响。贡献、代码审查和必需检查的具体要求见 [CONTRIBUTING.md](./CONTRIBUTING.md)。

`main` 受分支保护，合并前必须满足仓库配置的独立审批和必需检查；维护者自己的改动同样遵守这些规则。发布从合并后的 `main` 进行，由具有对应发布权限的人员按照 [PUBLISHING.md](./PUBLISHING.md) 执行。仓库权限、发布索引权限和凭证由各服务分别管理。

## 沟通与安全

一般问题通过 [Issues](https://github.com/bebebus/SDK/issues) 公开讨论；漏洞报告遵循 [SECURITY.md](./SECURITY.md)，不要在公开讨论中披露敏感细节。参与者应遵守 [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md)。

## 维护接替

当 baobao84 暂时无法工作时，[bebepay](https://github.com/bebepay) 可在一周内接手发布，包括 npm、PyPI 和 PHP 镜像。bebepay 具有 GitHub 仓库维护权限；涉及的各包索引和凭证仍分别管理。

项目应在人员、权限或接替安排变化时更新本文。上述安排尚未说明所有关键人员不可用时的接替方式，因此不据此声称满足完整的维护连续性或双维护者要求。
