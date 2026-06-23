# Merchant Payment OpenAPI — PHP 8 SDK

> [中文](./README.md) | English

[![Packagist](https://img.shields.io/packagist/v/bebebus/merchant-openapi-sdk?label=Packagist)](https://packagist.org/packages/bebebus/merchant-openapi-sdk) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

A zero-dependency PHP 8 SDK covering all 11 endpoints of the Merchant Payment OpenAPI + HMAC-SHA256 signing + callback signature verification.
It uses only the PHP standard library and core extensions (`ext-curl`, `ext-json`), and **does not depend on any composer-installed package** (Guzzle, PHPUnit, etc. are all avoided).

The signing algorithm uses [`SIGNING.md`](../SIGNING.en.md) at the repository root as its single source of truth, and is verified byte-for-byte across languages via the [`test-vectors.json`](../test-vectors.json) signature test vectors.

## Requirements

- PHP >= 8.1
- Extensions: `ext-curl` (HTTP), `ext-json` (JSON) — both are PHP core extensions and require no composer fetch

## Importing (no dependency installation needed)

This SDK has no runtime dependencies. Two ways to import it:

### Option 1: directly require the built-in autoloader (recommended, zero install)

```php
require __DIR__ . '/path/to/php/autoload.php';

use Merchant\Openapi\Client;
use Merchant\Openapi\Config;
use Merchant\Openapi\Environment;
```

### Option 2: composer PSR-4 (registers autoload only, requires no third-party package)

`composer.json` only declares the PSR-4 mapping `Merchant\Openapi\` → `src/`, and its `require` section contains only the PHP version and core extensions.
If you already have a composer project, you can use this directory as a path repository or merge its `autoload` section:

```json
{
  "autoload": { "psr-4": { "Merchant\\Openapi\\": "src/" } }
}
```

Then run `composer dump-autoload` (this downloads no packages).

## Quick Start

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
    apiSecretPay: 'sk_pay_xxx',       // for collection-type calls and collection/refund callbacks
    apiSecretPayout: 'sk_payout_xxx', // for payout-type calls and payout callbacks
    environment: Environment::PRODUCTION,
    baseUrl: 'https://api.<agent_domain>/api/open/v1', // required in production: derived from your parent agent's dedicated domain
);

$client = new Client($config);

try {
    // Amounts are integers in the smallest unit: 10000 = 1.00
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
    // Non-zero business error code: $e->apiCode / $e->apiMessage / $e->data
} catch (TransportException $e) {
    // Network/HTTP error: $e->httpStatus / $e->rawBody
}
```

## Dual Environments (sandbox/production) and Custom Base URL

```php
// Preset: production (no built-in address; you must pass baseUrl explicitly, or construction throws)
new Config(..., environment: Environment::PRODUCTION, baseUrl: 'https://api.<agent_domain>/api/open/v1');
// Preset: local/sandbox (http://127.0.0.1:3090/api/open/v1)
new Config(..., environment: Environment::SANDBOX);

// Custom base URL override (agent's dedicated domain / self-hosted port); takes precedence over environment
new Config(..., baseUrl: 'https://api.<agent_domain>/api/open/v1');
```

> `PRODUCTION` **has no built-in production address**: the real production address is derived from your parent agent's dedicated domain (in the form `https://api.<agent_domain>/api/open/v1`), provided by the platform/agent, and must be passed explicitly via `baseUrl`. Choosing `PRODUCTION` without passing `baseUrl` throws an `InvalidArgumentException` when constructing `Config`.

## Endpoint Methods (all 11)

| Method | Endpoint | Secret |
|------|------|------|
| `payCreate($params)` | `/merchant/pay/create` | pay |
| `payQuery($params)` | `/merchant/pay/query` | pay |
| `payMethodsQuery($params = [])` | `/merchant/pay-methods/query` | pay |
| `balanceQuery($params = [])` | `/merchant/balance/query` | pay |
| `payTestComplete($params)` | `/merchant/pay/test/complete` (test secret only) | pay |
| `payoutCreate($params)` | `/merchant/payout/create` | payout |
| `payoutQuery($params)` | `/merchant/payout/query` | payout |
| `payoutBanksQuery($params)` | `/merchant/payout/banks/query` | payout |
| `payoutProofQuery($params)` | `/merchant/payout/proof/query` | payout |
| `payoutReceiptQuery($params)` | `/merchant/payout/receipt/query` | payout |
| `payoutTestComplete($params)` | `/merchant/payout/test/complete` (test secret only) | payout |

Conventions:

- Each request automatically injects `merchant_no` / `api_key` / `timestamp` (Unix seconds) / `nonce` (unique per request).
- Fields whose value is `null` are **neither put into the request body nor included in signing**.
- pay-type calls automatically sign with `api_secret_pay`, payout-type calls automatically sign with `api_secret_payout` — no manual selection needed.
- The `inline` field of `payoutReceiptQuery` is sent as an **integer 1/0** (to avoid boolean signing ambiguity): `true` → `1`, `false`/omitted → `0`.
- Amounts are always integers in the smallest unit (`10000` = 1.00) and must be passed as `int`.
- On success the `data` (associative array) is returned; use `$client->lastRawResponse()` to get the most recent full envelope `{code,message,data}`.

## Signing and Callback Signature Verification

```php
use Merchant\Openapi\Signer;

// Compute the signature (pay uses api_secret_pay, payout uses api_secret_payout)
$sign = Signer::sign($payload, $secret);

// Get the base string alone (handy for byte-for-byte assertions / troubleshooting)
$base = Signer::buildSignBase($payload, $secret);

// Callback signature verification (timing-safe comparison; every field except sign participates, with no hardcoded field list)
$ok = Signer::verifyCallback($callbackPayload, $secret);

// Recommended: big-integer-safe convenience verification — pass the raw body directly; internally it parses
// with JSON_BIGINT_AS_STRING, keeping big integers beyond PHP's integer range (such as 64-bit order numbers)
// as strings before verifying, avoiding precision loss that would diverge the signature.
$ok = Signer::verifyCallbackRaw($rawBody, $secret);

// Convenience methods (secret taken from Config)
$client->verifyPayCallback($payload);    // collection/refund callback, uses api_secret_pay
$client->verifyPayoutCallback($payload); // payout callback, uses api_secret_payout
```

Callback handling pattern (see `examples/callback_verify.php`): take the raw body → prefer `verifyCallbackRaw($rawBody, $secret)`
(or `json_decode($rawBody, true, 512, JSON_BIGINT_AS_STRING)` then `verifyCallback`, keeping big integers as strings) →
process **idempotently** by `status` (success/failed) → reply with **HTTP 200 + plain text `success`**. Do not reply success on verification failure; let the platform retry.

**Security constraints** (fail-closed; only rejects illegal input, never affects valid signature results):

- **Empty secret rejected**: when `secret` is an empty/all-whitespace string, `sign()` throws `InvalidArgumentException` and `verifyCallback()/verifyCallbackRaw()` return `false` directly (they will never compute any signature with an empty secret).
- **Verification exceptions become false**: if the callback body is not an object, or `sign` is not a string / not 64-char lowercase hex / has an abnormal length → `verifyCallback*()` returns `false` and never lets exceptions bubble up.
- **Numbers must be integers**: numeric values participating in signing may not be `float` (including `NaN`/`Infinity`/`1.0`) — pass amounts as integers in the smallest unit (`10000` = 1.00); passing a float throws `InvalidArgumentException`.
- **Transport https**: when `Config`'s `baseUrl` is not `localhost`/`127.0.0.1`, `https://` is enforced or construction is rejected; cURL pins certificate verification (`SSL_VERIFYPEER`/`SSL_VERIFYHOST`), restricts protocols to https|http, and disables redirect following.

## Examples

- `examples/pay_create.php` — collection order creation + query
- `examples/payout_create.php` — query banks + payout order creation + query
- `examples/callback_verify.php` — collection callback + payout callback verification and idempotent handling (ships with sample data; run directly with `php examples/callback_verify.php`)

## Running the Tests

A zero-dependency, hand-written runner (PHPUnit forbidden). It reads `../test-vectors.json` and, for each vector, asserts `buildSignBase == base` and `sign == sign`, and also covers positive/negative callback verification cases and boundary unit tests:

```bash
cd php
php tests/run.php
```

Exit code `0` = all green; non-`0` = there were failures (CI can decide directly from this).
