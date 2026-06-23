> 中文 | [English](./INTERFACES.en.md)

# 接口契约（所有语言 SDK 的单一事实源）

签名见 [`SIGNING.md`](./SIGNING.md)。本文件定义环境地址、鉴权、全部端点的请求/响应字段、回调与错误码。

## 一、环境与基址

| 环境 | Base URL | 说明 |
|------|----------|------|
| 正式（production） | **无内置默认值，必须显式传 baseUrl** | 真实正式地址按你的上级代理专有域名派生（形如 `https://api.<agent_domain>/api/open/v1`），由平台/代理提供 |
| 测试/本地（sandbox） | `http://127.0.0.1:3090/api/open/v1` | 自建/联调；亦可用「正式 baseUrl + 测试密钥」做沙箱（测试密钥下单标记 `is_test`，不动真钱，可调 `*/test/complete`） |

SDK 设计：`Environment.SANDBOX` 内置本地预设基址；`Environment.PRODUCTION`（默认）**不内置任何主机名**，必须显式传入 `baseUrl`（代理专有域名），否则构造时报错。所有请求 `POST`，`Content-Type: application/json`，请求体为 JSON。

## 二、鉴权与通用字段

每个请求体都含下列通用字段（与业务字段同级，一起参与签名）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `merchant_no` | string | 是 | 商户号 |
| `api_key` | string | 是 | API Key |
| `timestamp` | int | 是 | Unix 秒；服务端校验 **±300s** 窗口 |
| `nonce` | string | 否 | 防重放随机串；不传则服务端按签名指纹在 ±300s 内去重。**建议每请求生成唯一值** |
| `sign` | string | 是 | 见 SIGNING.md |

**密钥选择**：`pay/*`、`pay-methods/query`、`balance/query` 用 `api_secret_pay`；`payout/*` 用 `api_secret_payout`。

**统一响应信封**（业务失败通常仍是 HTTP 200，靠 `code` 判定）：
```json
{ "code": 0, "message": "ok", "data": { /* 业务数据；失败时可能无 data 或含 missing_fields 等 */ } }
```
`code === 0` 为成功，其余为错误（见错误码表）。SDK 应：HTTP 层错误抛网络异常；`code !== 0` 抛带 `code`/`message`/`data` 的业务异常（也可由调用方选择不抛、直接读 `code`，由 SDK 暴露原始响应）。

## 三、代收（Pay）

### POST `/merchant/pay/create` — 代收下单（密钥：pay）
请求（通用字段 +）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `out_order_no` | string | 是 | 商户订单号（幂等键，唯一） |
| `amount` | int | 是 | 金额最小单位整数，`[1, 1e12]` |
| `currency` | string | 是 | 币种码（如 PHP/USDT） |
| `pay_method` | string | 是 | 支付方式（gcash/maya/trc20…，见 pay-methods/query） |
| `country` | string | 否 | 国家 ISO 码；法币必填、加密货币可空 |
| `notify_url` | string | 是 | 回调地址 |
| `return_url` | string | 否 | 前端回跳地址 |
| `subject` | string | 否 | 订单标题 |
| `remark` | string | 否 | 备注 |
| `client_ip` | string | 否 | 终端用户 IP |
| `extra` | object | 否 | 扩展，参与签名；可含 `customer`：`{first_name,last_name,name,email,phone}` |

响应 `data`：`order_no, out_order_no, amount(int), currency, pay_url(可空), qrcode_content(可空), pay_params(可空), expire_at(可空 ISO8601), status(pending|success|failed)`。

### POST `/merchant/pay/query` — 代收查单（密钥：pay）
请求：`order_no` 或 `out_order_no`（**二选一，至少一个**）。
响应 `data`：`order_no, out_order_no, amount, currency, status(pending|success|failed), channel_order_no(可空), paid_at(可空), notify_status(pending|success|failed)`。

### POST `/merchant/pay-methods/query` — 可用支付方式（密钥：pay）
请求：`country`（可选过滤）。
响应 `data.methods[]`：`{pay_method, name, country(可空), currency(可空)}`。

### POST `/merchant/balance/query` — 余额查询（密钥：pay）
请求：`currency`（可选过滤）。
响应 `data.balances[]`：`{currency, available(int), frozen(int)}`。

### POST `/merchant/pay/test/complete` — 代收测试单完成（密钥：pay，**仅测试密钥**）
请求：`order_no` 或 `out_order_no`（二选一）+ `result`(`success`|`failed`, 必填) + `actual_amount`(int, 可选)。
响应 `data`：`order_no, out_order_no, amount, actual_amount, status`。正式密钥调用会被拒绝。

## 四、代付（Payout）

### POST `/merchant/payout/create` — 代付下单（密钥：payout）
请求（通用字段 +）：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `out_payout_no` | string | 是 | 商户代付单号（幂等键，唯一） |
| `amount` | int | 是 | 金额最小单位整数，`[1, 1e12]` |
| `currency` | string | 是 | 币种码 |
| `pay_method` | string | 是 | 支付方式 |
| `country` | string | 否 | 国家 ISO 码；法币必填 |
| `notify_url` | string | 是 | 回调地址 |
| `account_no` | string | 是 | 收款账号/地址（卡号/钱包地址等） |
| `account_name` | string | 否 | 收款人姓名（链上地址类可空） |
| `bank_code` | string | 否 | 银行类**必填**，取 banks/query 的 `code`；钱包类（gcash）不需要 |
| `bank_name` | string | 否 | 银行名（系统按 bank_code 回填，一般无需传） |
| `remark` | string | 否 | 备注 |
| `client_ip` | string | 否 | 终端用户 IP |
| `extra` | object | 否 | 扩展，参与签名；`customer` 同代收（代付顶层 `account_name` 已作客户名，通常仅补 email/phone） |

响应 `data`：`payout_no, out_payout_no, amount, currency, status, review_status(可空), fee_amount(可空), freeze_amount(可空)`。
> 创建成功=已受理并冻结余额，最终结果以异步回调与查单为准。

### POST `/merchant/payout/query` — 代付查单（密钥：payout）
请求：`payout_no` 或 `out_payout_no`（二选一）。
响应 `data`：`payout_no, out_payout_no, amount, currency, status, sub_state(处理中子态 accepted|reviewing|processing|verifying，终态为 null), channel_order_no(可空), finished_at(可空), failed_reason(可空), notify_status`。

### POST `/merchant/payout/banks/query` — 可用银行（密钥：payout）
请求：`pay_method`(必填) + `country`(法币必填) + `currency`(可选)。
响应 `data.banks[]`：`{code, name}`（钱包类或无银行类上游时为空数组）。下单 `bank_code` 取此 `code`。

### POST `/merchant/payout/proof/query` — 代付凭证查询（密钥：payout）
请求：`payout_no` 或 `out_payout_no`（二选一）。仅 `status=success` 可查。
响应 `data`：`payout_no, out_payout_no, proof_url, expires_in(秒，可空)`。凭证有时效，即取即用。
错误：`300408`(渠道不支持) / `300409`(上游查无/超窗) / `300410`(非成功态)。

### POST `/merchant/payout/receipt/query` — 代付收据（密钥：payout）
请求：`payout_no` 或 `out_payout_no`（二选一）+ `lang`(`en`|`zh-CN`|`zh-TW`, 可选) + `inline`(可选)。
> SDK 把 `inline` 以**整数 1/0** 发送（避免布尔跨语言签名歧义）：`1` 内联返回 base64 图片，`0`/省略返回带 token 的 URL。
响应：`inline=0` → `{payout_no, out_payout_no, receipt_url, expires_in}`；`inline=1` → `{payout_no, out_payout_no, mime, image_base64}`。

### POST `/merchant/payout/test/complete` — 代付测试单完成（密钥：payout，**仅测试密钥**）
请求：`payout_no` 或 `out_payout_no`（二选一）+ `result`(`success`|`failed`, 必填)。响应：`{payout_no, out_payout_no, amount, status}`。

## 五、回调（Notify）

平台在订单进入终态时 POST JSON 到 `notify_url`，体内含 `sign`。验签与应答见 SIGNING.md §六（**验签按"除 sign 外全部字段"通用计算，不要硬编码字段表**）。

- **代收回调**（密钥 `api_secret_pay`）常见字段：`merchant_no, order_no, out_order_no, amount, actual_amount(可空), fee_amount(可空), net_amount(可空), currency, status, channel_order_no(可空), paid_at(可空), sign`。
- **代付回调**（密钥 `api_secret_payout`）常见字段：`merchant_no, payout_no, out_payout_no, amount, currency, status, fee_amount(可空), channel_order_no(可空), finished_at(可空), failed_reason(可空), sign`。
- **退款回调**（密钥 `api_secret_pay`）常见字段：`merchant_no, order_no(可空), out_order_no(可空), refund_no, out_refund_no, amount, currency, status, channel_order_no(可空), finished_at(可空), failed_reason(可空), sign`。

> 字段集合可能随平台演进增删，故 SDK 验签器**只依赖"除 sign 外所有字段参与"这一规则**；业务处理按 `status` 分支并保持幂等。应答统一 HTTP 200 + 纯文本 `success`。

## 六、错误码

| code | 含义 |
|------|------|
| 0 | 成功 |
| 100000 | 认证失败（凭证无效/禁用/不存在等） |
| 100001 | 载荷校验失败（message 为具体字段错误，如 `"amount" must be a number`） |
| 100101 | 请求过期（timestamp 超 ±300s） |
| 100102 | IP 不在白名单 |
| 100104 | 签名错误 |
| 100105 | IP 在黑名单 |
| 300201 | 代付下单幂等冲突 |
| 300301 | 订单不存在 |
| 300402 | 通道配置不可用 |
| 300403 | 费率未配置 |
| 300404 | 无可用支付方式渠道 |
| 300405 | 缺少必要附加信息（`data.missing_fields`） |
| 300406 | 缺少必要下单参数（`data.missing_fields`） |
| 300407 | 银行编码无效 |
| 300408 | 渠道不支持凭证查询 |
| 300409 | 上游查无凭证/超出查询窗口 |
| 300410 | 订单非成功态（凭证/收据不可查） |
| 300501 | 余额不足 |

> 错误码以服务端为准，可能新增；SDK 异常应携带原始 `code`/`message`/`data` 供调用方判断，不要穷举写死分支。
