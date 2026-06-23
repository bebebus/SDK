> [中文](./README.md) | English

# Merchant Payment OpenAPI — Node.js SDK (ESM)

[![npm](https://img.shields.io/npm/v/@bebebus/merchant-openapi-sdk?label=npm)](https://www.npmjs.com/package/@bebebus/merchant-openapi-sdk) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

Zero dependencies: uses only the Node standard library (`node:http` / `node:https`, `node:crypto`, `node:test`).
Signing, HTTP, JSON, and the test framework all rely on official built-ins, so **no `npm install` is required**.

- Signing algorithm: HMAC-SHA256 → lowercase hex, see [`SIGNING.md`](../SIGNING.en.md) at the repo root
- Interface contract: environments / authentication / 11 endpoints / callbacks / error codes, see [`INTERFACES.md`](../INTERFACES.en.md)
- Reference vectors: [`test-vectors.json`](../test-vectors.json)

## Importing (no dependency install needed)

Requires Node ≥ 18 (uses `node:test`, `crypto.randomUUID`, `crypto.timingSafeEqual`).

```js
// Import directly from source
import { Client, Config, Environment, ApiError, TransportError } from './src/index.js';
```

To reference it as a local package, just use a file-path dependency in your `package.json` (this package itself has empty `dependencies`):

```json
{ "dependencies": { "merchant-openapi-sdk": "file:../sdk/nodejs" } }
```

This package is ESM (`"type": "module"`), so callers must use `import` (or dynamic `import()` from CJS).

## Quick start

```js
import { Client, Config, Environment } from './src/index.js';

const client = new Client(new Config({
  merchantNo: 'M00000001',
  apiKey: 'ak_xxx',
  apiSecretPay: 'sk_pay_xxx',       // pay endpoints / collection & refund callbacks
  apiSecretPayout: 'sk_payout_xxx', // payout endpoints / payout callbacks
  // PRODUCTION has no built-in URL; you must explicitly pass a baseUrl derived from the agent-specific domain
  baseUrl: 'https://api.<agent_domain>/api/open/v1',
}));

// Create a collection order (amount is an integer in the smallest unit, 10000 = 1 unit of currency)
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

Each method returns `{ data, raw }`: `data` is the `data` field inside the unified envelope, and `raw` is the full `{code,message,data}` (keeping a way to access the original response).

## Dual environments + custom base URL

```js
import { Config, Environment } from './src/index.js';

// Preset: production (no built-in URL; you must explicitly pass baseUrl, otherwise it throws)
new Config({ /* ... */ environment: Environment.PRODUCTION, baseUrl: 'https://api.<agent_domain>/api/open/v1' });
// Preset: local/sandbox
new Config({ /* ... */ environment: Environment.SANDBOX });
// Custom (agent-specific domain or custom port) — baseUrl takes precedence over environment
new Config({ /* ... */ baseUrl: 'https://api.<agent_domain>/api/open/v1' });
```

| Preset | Base URL |
|------|----------|
| `Environment.PRODUCTION` | No built-in URL; derived from the agent-specific domain as `https://api.<agent_domain>/api/open/v1`, and `baseUrl` must be passed explicitly |
| `Environment.SANDBOX` | `http://127.0.0.1:3090/api/open/v1` |

## All 11 endpoints

| Method | Endpoint | Secret |
|------|------|------|
| `payCreate(params)` | `/merchant/pay/create` | pay |
| `payQuery(params)` | `/merchant/pay/query` | pay |
| `payMethodsQuery(params?)` | `/merchant/pay-methods/query` | pay |
| `balanceQuery(params?)` | `/merchant/balance/query` | pay |
| `payTestComplete(params)` | `/merchant/pay/test/complete` | pay (test secret only) |
| `payoutCreate(params)` | `/merchant/payout/create` | payout |
| `payoutQuery(params)` | `/merchant/payout/query` | payout |
| `payoutBanksQuery(params)` | `/merchant/payout/banks/query` | payout |
| `payoutProofQuery(params)` | `/merchant/payout/proof/query` | payout |
| `payoutReceiptQuery(params)` | `/merchant/payout/receipt/query` | payout |
| `payoutTestComplete(params)` | `/merchant/payout/test/complete` | payout (test secret only) |

Request-building notes:

- Automatically injects `merchant_no`, `api_key`, `timestamp` (Unix seconds), and a unique `nonce` (`crypto.randomUUID`).
- Fields whose value is `null` / `undefined` are **neither placed in the request body nor included in the signature**.
- The `inline` field of `payoutReceiptQuery` is converted to the **integer `1`/`0`** before sending (to avoid boolean signing ambiguity).
- Each client method automatically picks the right secret (pay endpoints use `apiSecretPay`, payout endpoints use `apiSecretPayout`).

## Error handling

```js
import { ApiError, TransportError } from './src/index.js';

try {
  await client.payQuery({ out_order_no: 'x' });
} catch (err) {
  if (err instanceof ApiError) {
    // Business failure: code !== 0, carries code/message/data and the original envelope raw
    console.error(err.code, err.message, err.data);
  } else if (err instanceof TransportError) {
    // HTTP/network layer: statusCode, body, cause
    console.error(err.statusCode, err.body);
  }
}
```

## Callback signature verification (timing-safe)

`verifyCallback` / `client.verifyPayCallback` / `client.verifyPayoutCallback` compute generically over "all fields except `sign`" (without hardcoding a field table) and use `crypto.timingSafeEqual` for a timing-safe comparison.

Collection/refund callbacks use `apiSecretPay`, payout callbacks use `apiSecretPayout`. On signature verification failure, **do not return a success acknowledgement** (let the platform retry); after successful processing, return **HTTP 200 + plain text `success`**, and the processing must be **idempotent**.

A complete example (demonstrating both collection and payout once each, including idempotency and tampering counter-examples) is in [`examples/callback_verify.js`](./examples/callback_verify.js):

```bash
node examples/callback_verify.js
```

## Running the examples

```bash
node examples/pay_create.js       # Collection order creation + query
node examples/payout_create.js    # Payout order creation + query + available banks query
node examples/callback_verify.js  # Callback signature verification + idempotent processing + acknowledgement (collection + payout)
```

Credentials can be passed via environment variables: `PP_MERCHANT_NO` / `PP_API_KEY` / `PP_API_SECRET_PAY` / `PP_API_SECRET_PAYOUT` / `PP_BASE_URL`.

## Running the tests

Standard-library test facilities (`node:test` + `node:assert`), with no dependencies to install:

```bash
node --test
# or
npm test
```

Test coverage:

- `tests/signer.test.js`: reads `../test-vectors.json` and, for **each vector**, asserts `buildSignBase == base` and `sign == sign`; plus a positive callback signature verification case and negative cases for tampering one byte / using the wrong secret / missing sign.
- `tests/client.test.js`: uses a local `node:http` stub server to verify request building (common fields / nonce uniqueness / null filtering / secret selection / `inline` integer conversion / path) and envelope parsing (`ApiError` / `TransportError`), entirely without any external network access.
