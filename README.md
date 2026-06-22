# 商户支付 OpenAPI 多语言 SDK

为商户支付开放接口（代收 / 代付 / 回调）提供 **PHP / Python / Java / Go / Node.js** 五套 SDK。

设计原则：**零第三方依赖**（仅用各语言标准库/官方内建：HTTP、JSON、HMAC、测试框架），**全部 11 个接口**，
**测试 + 正式双环境**，**跨语言签名逐字节一致**（同一份标准答案向量五套都绿）。

## 目录结构

```
SDK/
├── README.md            # 本文件
├── SIGNING.md           # 签名算法权威说明 + 逐语言序列化坑（实现/排错必读）
├── INTERFACES.md        # 11 端点字段级请求/响应、回调字段、错误码
├── test-vectors.json    # 跨语言签名「标准答案」向量（11 条；五套单测都断言它）
├── _tooling/
│   └── generate-vectors.mjs   # 向量生成器（经三处权威实现交叉校验后产出）
├── nodejs/   # Node.js (ESM)         —— node:https/http + node:crypto + node:test
├── python/   # Python 3             —— urllib.request + hmac/hashlib + unittest
├── php/      # PHP 8                —— cURL/ext-json + hash_hmac + 自写零依赖 runner
├── java/     # Java 17 (无 Maven)    —— java.net.http + javax.crypto + 自写断言 runner
└── go/       # Go 1.2x (stdlib)      —— net/http + crypto/hmac + testing
```

## 语言矩阵

| 语言 | 包/引入方式 | HTTP（无第三方） | 测试命令 |
|------|-------------|------------------|----------|
| Node.js | `package.json`（无 deps，ESM）；`import { Client } from './nodejs/src/index.js'` | `node:https` / `node:http` | `cd nodejs && node --test` |
| Python | `pyproject.toml`（无 deps）；`from openapi_sdk import Client` | `urllib.request` | `cd python && python3 -m unittest discover -s tests` |
| PHP | `composer.json`（仅 PSR-4 autoload，无 require）；`require 'php/autoload.php'` | cURL 扩展 | `cd php && php tests/run.php` |
| Java | 纯 JDK（`pom.xml` 仅供打包，无依赖）；`import cloud.cniia.openapi.sdk.Client` | `java.net.http.HttpClient` | `cd java && bash run-tests.sh` |
| Go | `go.mod`（无 require）；`import openapi "github.com/bebebus/SDK/go"` | `net/http` | `cd go && go test -count=1 ./...` |

> Go 测试读取外部 `test-vectors.json`，`go test` 的缓存不追踪该文件，**改向量后用 `-count=1`** 强制重跑。

## 涵盖的接口（每语言均实现）

**代收**：`payCreate`（下单）、`payQuery`（查单）、`payMethodsQuery`（可用支付方式）、`balanceQuery`（余额）、`payTestComplete`（测试单完成，仅测试密钥）
**代付**：`payoutCreate`、`payoutQuery`、`payoutBanksQuery`（可用银行）、`payoutProofQuery`（凭证）、`payoutReceiptQuery`（收据）、`payoutTestComplete`（测试单完成，仅测试密钥）
**回调**：`verifyPayCallback` / `verifyPayoutCallback`（验签，时序安全比较）

各方法的请求/响应字段见 [`INTERFACES.md`](./INTERFACES.md)。方法名按各语言习惯（Java/JS/PHP 驼峰、Python 蛇形 `pay_create`、Go 导出驼峰），语义一一对应。

## 环境

每套 SDK 都提供两个预设基址，并支持**自定义 baseUrl 覆盖**（优先级最高，会去掉末尾斜杠）：

| 预设 | 基址 |
|------|------|
| `PRODUCTION`（正式） | **无内置默认值，必须显式传 baseUrl** |
| `SANDBOX`（测试/本地） | `http://127.0.0.1:3090/api/open/v1` |

> 正式环境真实地址按你**上级代理的专有域名**派生（形如 `https://api.<agent_domain>/api/open/v1`），由平台/代理提供。SDK **不内置任何正式主机名**：选 `PRODUCTION`（默认）时必须通过 `baseUrl` 显式传入，否则构造时报错。
> 「测试密钥沙箱」可用上述正式 baseUrl + 测试密钥（测试单标记 `is_test`，不动真钱，可调 `*/test/complete`）。

## 快速开始（以 Node.js 为例，其余语言见各自 README）

```js
import { Client, Config, Environment } from './nodejs/src/index.js';

const client = new Client(new Config({
  merchantNo: process.env.MERCHANT_NO,
  apiKey: process.env.API_KEY,
  apiSecretPay: process.env.API_SECRET_PAY,
  apiSecretPayout: process.env.API_SECRET_PAYOUT,
  // 正式环境必须显式传 baseUrl（按上级代理专有域名：https://api.<agent_domain>/api/open/v1）
  baseUrl: process.env.API_BASE_URL,
  // 本地联调可改用：environment: Environment.SANDBOX,
}));

// 代收下单（金额为最小单位整数，10000 = 1 元）
const { data } = await client.payCreate({
  out_order_no: 'order-' + Date.now(),
  amount: 10000, currency: 'PHP', pay_method: 'gcash', country: 'PH',
  notify_url: 'https://merchant.example.com/api/notify/pay',
});
console.log(data.order_no, data.pay_url);
```

各语言对应的 `pay_create` / `payout_create` / `callback_verify` 可运行示例见 `<语言>/examples/`。

## 回调（验签 + 处理片段）

平台在订单进入终态时 POST JSON 到你的 `notify_url`，体内含 `sign`。处理要点（示例见各语言 `examples/callback_verify.*`）：

1. 读原始 body → 解析 JSON。
2. 验签：代收/退款回调用 `api_secret_pay`，代付回调用 `api_secret_payout`；**时序安全比较**；按"除 `sign` 外所有字段参与"通用计算（不硬编码字段表）。
3. 按 `status` **幂等**处理（同一单可能被回调多次）。
4. 应答：HTTP 200 + 纯文本 `success`（平台据此判定成功，否则按 `1m,2m,5m,10m,30m,60m` 退避重试约 6 次）。

## 金额约定

所有金额为**最小单位整数**（精度 0.0001，`10000 = 1 元`）。SDK 金额参数请用整数类型，避免浮点跨语言格式化差异。

## 签名

HMAC-SHA256 → 十六进制小写。算法与逐语言落地坑（嵌套 JSON 序列化必须对齐 JS：不转义 `/`、非 ASCII、`<>&`；顶层布尔归一 `true/false`；空对象 `{}` ≠ 空数组 `[]`）见 [`SIGNING.md`](./SIGNING.md)。

跨语言一致性由 [`test-vectors.json`](./test-vectors.json)（11 条标准答案向量）保证：五套 SDK 的单测都对每条向量断言 `base` 与 `sign` 逐字节相等。重算向量：`node _tooling/generate-vectors.mjs`（会用三处权威实现交叉校验后才写出）。

## 一次性跑全部测试

```bash
cd nodejs && node --test && cd ..
cd python && python3 -m unittest discover -s tests && cd ..
cd php    && php tests/run.php && cd ..
cd java   && bash run-tests.sh && cd ..
cd go     && go test -count=1 ./... && cd ..
```

## 安全约定

- **凭证从环境变量/配置注入**，示例与测试不含任何真实密钥（向量用合成密钥）。请勿把生产密钥提交进仓库。
- 回调验签务必用 SDK 提供的时序安全比较；验签失败一律拒绝、不回 `success`。
- 启用 IP 白名单时，出口 IP 需在商户后台登记。
