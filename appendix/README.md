# 附录配置文件

本目录对应各语言可直接查看或下载的附录配置。附录数据以本仓库为唯一来源。占位符请换成商户后台凭据；国家 / 币种字典包含平台标准项与区域扩展，实际可用范围以 `countries/query` 为准；某组合当前是否可下单以 `pay-methods/query` 为准。

## 国家、币种、银行代码参考表

完整参考表：[在线阅读](./REFERENCE-CODES.md) · [JSON](./reference-codes.json) · CSV：[国家](./countries.csv) / [币种](./currencies.csv) / [银行代码](./banks.csv)。

越南另补 [公开银行/机构目录 CSV](./bank-directories.csv)：依据 VietQR.io 公共 API 采集的 76 项，保留公开代码、BIN、名称与项目码映射。现有 44 个项目越南银行码来自 AiPay 子集，不能当成越南全部银行；公开目录中 33 项尚未在项目码册登记，`platform_bank_code` 为 null，不能直接用于下单。目录含分支、数字品牌、钱包及金融机构，也不代表越南银行法人全量。

JSON 是这三类代码的唯一维护源，包含更新日期、源代码提交与文件指纹，以及公开目录的来源和采集日期；Markdown、CSV 和下方五语言国家/币种附录统一生成。项目银行按 `(country, bank_code)` 定位，公开目录保留发布方独立代码体系。实际代付银行以 `payout/banks/query` 返回为准；本表包含预登记项，不表示已开通。不维护支付方式表。

维护流程见 [贡献指南](../CONTRIBUTING.md#参考代码表维护)。

## 客户端配置模板

| 语言 | 格式 | 查看 | 下载 |
|------|------|------|------|
| Node.js | JSON | [config.example.json](https://github.com/bebebus/SDK/blob/main/nodejs/appendix/config.example.json) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/nodejs/appendix/config.example.json) |
| PHP | 数组 | [config.example.php](https://github.com/bebebus/SDK/blob/main/php/appendix/config.example.php) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/php/appendix/config.example.php) |
| Python | dict | [config.example.py](https://github.com/bebebus/SDK/blob/main/python/appendix/config.example.py) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/python/appendix/config.example.py) |
| Go | Go | [config.example.go](https://github.com/bebebus/SDK/blob/main/go/appendix/config.example.go) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/go/appendix/config.example.go) |
| Java | JSON | [config.example.json](https://github.com/bebebus/SDK/blob/main/java/appendix/config.example.json) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/java/appendix/config.example.json) |

## 国家 / 币种附录

| 语言 | 格式 | 查看 | 下载 |
|------|------|------|------|
| Node.js / 通用 | JSON | [catalog.json](https://github.com/bebebus/SDK/blob/main/nodejs/appendix/catalog.json) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/nodejs/appendix/catalog.json) · [文档站](https://openapi.overseas.cniia.cloud/appendix/catalog.json) |
| PHP | 数组 | [catalog.php](https://github.com/bebebus/SDK/blob/main/php/appendix/catalog.php) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/php/appendix/catalog.php) · [文档站](https://openapi.overseas.cniia.cloud/appendix/catalog.php) |
| Python | dict | [catalog.py](https://github.com/bebebus/SDK/blob/main/python/appendix/catalog.py) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/python/appendix/catalog.py) |
| Go | Go | [catalog.go](https://github.com/bebebus/SDK/blob/main/go/appendix/catalog.go) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/go/appendix/catalog.go) |
| Java | JSON | [catalog.json](https://github.com/bebebus/SDK/blob/main/java/appendix/catalog.json) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/java/appendix/catalog.json) |

## 错误码总表

| 语言 | 格式 | 查看 | 下载 |
|------|------|------|------|
| Node.js | JSON | [error-codes.json](https://github.com/bebebus/SDK/blob/main/nodejs/appendix/error-codes.json) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/nodejs/appendix/error-codes.json) |
| PHP | 数组 | [error-codes.php](https://github.com/bebebus/SDK/blob/main/php/appendix/error-codes.php) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/php/appendix/error-codes.php) |
| Python | dict | [error-codes.py](https://github.com/bebebus/SDK/blob/main/python/appendix/error-codes.py) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/python/appendix/error-codes.py) |
| Go | Go | [error-codes.go](https://github.com/bebebus/SDK/blob/main/go/appendix/error-codes.go) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/go/appendix/error-codes.go) |
| Java | JSON | [error-codes.json](https://github.com/bebebus/SDK/blob/main/java/appendix/error-codes.json) | [raw](https://raw.githubusercontent.com/bebebus/SDK/main/java/appendix/error-codes.json) |
