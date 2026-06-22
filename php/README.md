# 商户支付 OpenAPI — PHP 8 SDK

[![Packagist](https://img.shields.io/packagist/v/bebebus/merchant-openapi-sdk?label=Packagist)](https://packagist.org/packages/bebebus/merchant-openapi-sdk) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

零第三方依赖的 PHP 8 SDK，覆盖商户支付 OpenAPI 全部 11 个端点 + HMAC-SHA256 签名 + 回调验签。
仅使用 PHP 标准库与核心扩展（`ext-curl`、`ext-json`），**不依赖 composer 安装任何包**（如 Guzzle/PHPUnit 等一概不用）。

签名算法以仓库根的 [`SIGNING.md`](../SIGNING.md) 为单一事实源，并通过 [`test-vectors.json`](../test-vectors.json) 跨语言向量逐字节校验。

## 环境要求

- PHP >= 8.1
- 扩展：`ext-curl`（HTTP）、`ext-json`（JSON）——均为 PHP 核心扩展，无需 composer 拉取

## 引入（无需安装依赖）

本 SDK 无运行时依赖。两种引入方式：

### 方式一：直接 require 内置自动加载器（推荐，零安装）

```php
require __DIR__ . '/path/to/php/autoload.php';

use Merchant\Openapi\Client;
use Merchant\Openapi\Config;
use Merchant\Openapi\Environment;
```

### 方式二：composer PSR-4（仅注册 autoload，无 require 第三方包）

`composer.json` 只声明 `Merchant\Openapi\` → `src/` 的 PSR-4 映射，`require` 段仅有 PHP 版本与核心扩展。
若你已有 composer 工程，可把本目录作为 path 仓库或合并其 `autoload` 段：

```json
{
  "autoload": { "psr-4": { "Merchant\\Openapi\\": "src/" } }
}
```

然后 `composer dump-autoload` 即可（不会下载任何包）。

## 快速开始

```php
require __DIR__ . '/php/autoload.php';

use Merchant\Openapi\Client;
use Merchant\Openapi\Config;
use Merchant\Openapi\Environment;
use Merchant\Openapi\Exception\ApiException;
use Merchant\Openapi\Exception\TransportException;

$config = new Config(
    merchantNo: 'M00000001',
    apiKey: 'ak_demo_key',
    apiSecretPay: 'sk_pay_xxx',       // 代收类与代收/退款回调用
    apiSecretPayout: 'sk_payout_xxx', // 代付类与代付回调用
    environment: Environment::PRODUCTION,
    baseUrl: 'https://api.<agent_domain>/api/open/v1', // 正式必传：按上级代理专有域名派生
);

$client = new Client($config);

try {
    // 金额是最小单位整数：10000 = 1.00 元
    $data = $client->payCreate([
        'out_order_no' => 'ORDER20250101',
        'amount'       => 10000,
        'currency'     => 'PHP',
        'pay_method'   => 'gcash',
        'country'      => 'PH',
        'notify_url'   => 'https://merchant.example.com/api/notify/pay',
    ]);
    echo $data['order_no'], ' ', $data['status'], "\n";
} catch (ApiException $e) {
    // 业务错误码非 0：$e->apiCode / $e->apiMessage / $e->data
} catch (TransportException $e) {
    // 网络/HTTP 错误：$e->httpStatus / $e->rawBody
}
```

## 双环境与自定义基址

```php
// 预设：正式（无内置地址，必须显式传 baseUrl，否则构造时抛错）
new Config(..., environment: Environment::PRODUCTION, baseUrl: 'https://api.<agent_domain>/api/open/v1');
// 预设：本地/沙箱（http://127.0.0.1:3090/api/open/v1）
new Config(..., environment: Environment::SANDBOX);

// 自定义基址覆盖（代理专有域名 / 自建端口），优先级高于 environment
new Config(..., baseUrl: 'https://api.<agent_domain>/api/open/v1');
```

> `PRODUCTION` **不内置任何正式地址**：正式真实地址按上级代理专有域名派生（形如 `https://api.<agent_domain>/api/open/v1`），由平台/代理提供，必须用 `baseUrl` 显式传入。选 `PRODUCTION` 而不传 `baseUrl` 会在构造 `Config` 时抛 `InvalidArgumentException`。

## 端点方法（全 11 个）

| 方法 | 端点 | 密钥 |
|------|------|------|
| `payCreate($params)` | `/merchant/pay/create` | pay |
| `payQuery($params)` | `/merchant/pay/query` | pay |
| `payMethodsQuery($params = [])` | `/merchant/pay-methods/query` | pay |
| `balanceQuery($params = [])` | `/merchant/balance/query` | pay |
| `payTestComplete($params)` | `/merchant/pay/test/complete`（仅测试密钥） | pay |
| `payoutCreate($params)` | `/merchant/payout/create` | payout |
| `payoutQuery($params)` | `/merchant/payout/query` | payout |
| `payoutBanksQuery($params)` | `/merchant/payout/banks/query` | payout |
| `payoutProofQuery($params)` | `/merchant/payout/proof/query` | payout |
| `payoutReceiptQuery($params)` | `/merchant/payout/receipt/query` | payout |
| `payoutTestComplete($params)` | `/merchant/payout/test/complete`（仅测试密钥） | payout |

约定：

- 每请求自动注入 `merchant_no` / `api_key` / `timestamp`(Unix 秒) / `nonce`(每请求唯一)。
- 值为 `null` 的字段**不放入请求体也不参与签名**。
- pay 类自动用 `api_secret_pay`、payout 类自动用 `api_secret_payout` 签名，无需手动选。
- `payoutReceiptQuery` 的 `inline` 以**整数 1/0** 发送（避免布尔签名歧义）：`true`→`1`，`false`/省略→`0`。
- 金额一律最小单位整数（`10000` = 1 元），用 `int` 类型传。
- 成功返回 `data`（关联数组）；可用 `$client->lastRawResponse()` 取最近一次完整信封 `{code,message,data}`。

## 签名与回调验签

```php
use Merchant\Openapi\Signer;

// 计算签名（pay 用 api_secret_pay，payout 用 api_secret_payout）
$sign = Signer::sign($payload, $secret);

// 单独取 base 串（便于逐字节断言/排查）
$base = Signer::buildSignBase($payload, $secret);

// 回调验签（时序安全比较，除 sign 外所有字段参与，不硬编码字段表）
$ok = Signer::verifyCallback($callbackPayload, $secret);

// 便捷方法（密钥取自 Config）
$client->verifyPayCallback($payload);    // 代收/退款回调，用 api_secret_pay
$client->verifyPayoutCallback($payload); // 代付回调，用 api_secret_payout
```

回调处理范式（见 `examples/callback_verify.php`）：拿原始 body → `json_decode` → `verifyCallback` →
按 `status`（success/failed）**幂等**处理 → 回 **HTTP 200 + 纯文本 `success`**。验签失败不要回成功，让平台重试。

## 示例

- `examples/pay_create.php` — 代收下单 + 查单
- `examples/payout_create.php` — 查银行 + 代付下单 + 查单
- `examples/callback_verify.php` — 代收回调 + 代付回调验签与幂等处理（自带样例，可直接 `php examples/callback_verify.php` 跑通）

## 跑测试

零依赖手写 runner（禁 PHPUnit）。会读取 `../test-vectors.json` 对每个向量断言 `buildSignBase==base` 且 `sign==sign`，并覆盖回调验签正/反例与边界单测：

```bash
cd php
php tests/run.php
```

退出码 `0` = 全绿；非 `0` = 有失败（CI 可直接据此判定）。
