# project-p 商户支付 OpenAPI —— Node.js SDK（ESM）

零第三方依赖：仅使用 Node 标准库（`node:http` / `node:https`、`node:crypto`、`node:test`）。
签名、HTTP、JSON、测试框架全部用官方内建，**无需 `npm install`**。

- 签名算法：HMAC-SHA256 → hex 小写，见仓库根 [`SIGNING.md`](../SIGNING.md)
- 接口契约：环境/鉴权/11 端点/回调/错误码，见 [`INTERFACES.md`](../INTERFACES.md)
- 标准答案向量：[`test-vectors.json`](../test-vectors.json)

## 引入（无需安装依赖）

要求 Node ≥ 18（用到 `node:test`、`crypto.randomUUID`、`crypto.timingSafeEqual`）。

```js
// 直接从源码导入
import { Client, Config, Environment, ApiError, TransportError } from './src/index.js';
```

如作为本地包引用，在你的 `package.json` 用文件路径依赖即可（本包自身 `dependencies` 为空）：

```json
{ "dependencies": { "@project-p/merchant-openapi-sdk": "file:../project-p-sdk/nodejs" } }
```

本包是 ESM（`"type": "module"`），调用方需用 `import`（或在 CJS 中用动态 `import()`）。

## 快速开始

```js
import { Client, Config, Environment } from './src/index.js';

const client = new Client(new Config({
  merchantNo: 'M00000001',
  apiKey: 'ak_xxx',
  apiSecretPay: 'sk_pay_xxx',       // pay 类接口 / 代收·退款回调
  apiSecretPayout: 'sk_payout_xxx', // payout 类接口 / 代付回调
  environment: Environment.PRODUCTION,
}));

// 代收下单（金额是最小单位整数，10000 = 1 元）
const { data, raw } = await client.payCreate({
  out_order_no: 'ORDER_' + Date.now(),
  amount: 10000,
  currency: 'PHP',
  pay_method: 'gcash',
  country: 'PH',
  notify_url: 'https://merchant.example.com/api/notify/pay',
});
console.log(data.pay_url, raw.code);
```

每个方法返回 `{ data, raw }`：`data` 是统一信封里的 `data` 字段，`raw` 是完整 `{code,message,data}`（保留拿原始响应的途径）。

## 双环境 + 自定义基址

```js
import { Config, Environment } from './src/index.js';

// 预设：正式
new Config({ /* ... */ environment: Environment.PRODUCTION });
// 预设：本地/沙箱
new Config({ /* ... */ environment: Environment.SANDBOX });
// 自定义（代理专有域名或自定义端口）—— baseUrl 优先于 environment
new Config({ /* ... */ baseUrl: 'https://api.<agent_domain>/api/open/v1' });
```

| 预设 | Base URL |
|------|----------|
| `Environment.PRODUCTION` | `https://api.project-p-merchant.cniia.cloud/api/open/v1` |
| `Environment.SANDBOX` | `http://127.0.0.1:3090/api/open/v1` |

## 全部 11 个端点

| 方法 | 端点 | 密钥 |
|------|------|------|
| `payCreate(params)` | `/merchant/pay/create` | pay |
| `payQuery(params)` | `/merchant/pay/query` | pay |
| `payMethodsQuery(params?)` | `/merchant/pay-methods/query` | pay |
| `balanceQuery(params?)` | `/merchant/balance/query` | pay |
| `payTestComplete(params)` | `/merchant/pay/test/complete` | pay（仅测试密钥） |
| `payoutCreate(params)` | `/merchant/payout/create` | payout |
| `payoutQuery(params)` | `/merchant/payout/query` | payout |
| `payoutBanksQuery(params)` | `/merchant/payout/banks/query` | payout |
| `payoutProofQuery(params)` | `/merchant/payout/proof/query` | payout |
| `payoutReceiptQuery(params)` | `/merchant/payout/receipt/query` | payout |
| `payoutTestComplete(params)` | `/merchant/payout/test/complete` | payout（仅测试密钥） |

请求构建说明：

- 自动注入 `merchant_no`、`api_key`、`timestamp`（Unix 秒）与唯一 `nonce`（`crypto.randomUUID`）。
- 值为 `null` / `undefined` 的字段**不放入请求体也不参与签名**。
- `payoutReceiptQuery` 的 `inline` 会被转成**整数 `1`/`0`** 发送（避免布尔签名歧义）。
- 客户端各方法自动选对密钥（pay 类用 `apiSecretPay`，payout 类用 `apiSecretPayout`）。

## 错误处理

```js
import { ApiError, TransportError } from './src/index.js';

try {
  await client.payQuery({ out_order_no: 'x' });
} catch (err) {
  if (err instanceof ApiError) {
    // 业务失败：code !== 0，携带 code/message/data 与原始信封 raw
    console.error(err.code, err.message, err.data);
  } else if (err instanceof TransportError) {
    // HTTP/网络层：statusCode、body、cause
    console.error(err.statusCode, err.body);
  }
}
```

## 回调验签（时序安全）

`verifyCallback` / `client.verifyPayCallback` / `client.verifyPayoutCallback` 按「除 `sign` 外所有字段参与」通用计算（不硬编码字段表），并用 `crypto.timingSafeEqual` 做时序安全比较。

代收/退款回调用 `apiSecretPay`，代付回调用 `apiSecretPayout`。验签失败时**不要回成功应答**（让平台重试）；成功处理后回 **HTTP 200 + 纯文本 `success`**，处理务必**幂等**。

完整示例（代收 + 代付各演示一次、含幂等与篡改反例）见 [`examples/callback_verify.js`](./examples/callback_verify.js)：

```bash
node examples/callback_verify.js
```

## 运行示例

```bash
node examples/pay_create.js       # 代收下单 + 查单
node examples/payout_create.js    # 代付下单 + 查单 + 查可用银行
node examples/callback_verify.js  # 回调验签 + 幂等处理 + 应答（代收 + 代付）
```

凭证可用环境变量传入：`PP_MERCHANT_NO` / `PP_API_KEY` / `PP_API_SECRET_PAY` / `PP_API_SECRET_PAYOUT` / `PP_BASE_URL`。

## 跑测试

标准库测试设施（`node:test` + `node:assert`），无需安装任何依赖：

```bash
node --test
# 或
npm test
```

测试内容：

- `tests/signer.test.js`：读取 `../test-vectors.json`，对**每个向量**断言 `buildSignBase == base` 且 `sign == sign`；回调验签正例 + 篡改一字节/换错密钥/缺 sign 的反例。
- `tests/client.test.js`：用本地 `node:http` 桩服务器校验请求构建（通用字段/nonce 唯一/null 过滤/密钥选择/`inline` 整数化/path）与信封解析（`ApiError` / `TransportError`），全程不依赖外网。
