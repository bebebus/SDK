> [中文](./README.md) | English

# Merchant Payment OpenAPI Multi-Language SDK

[![npm](https://img.shields.io/npm/v/@bebebus/merchant-openapi-sdk?label=npm)](https://www.npmjs.com/package/@bebebus/merchant-openapi-sdk) [![PyPI](https://img.shields.io/pypi/v/bebebus-merchant-openapi-sdk?label=PyPI)](https://pypi.org/project/bebebus-merchant-openapi-sdk/) [![Packagist](https://img.shields.io/packagist/v/bebebus/merchant-openapi-sdk?label=Packagist)](https://packagist.org/packages/bebebus/merchant-openapi-sdk) [![Go Reference](https://pkg.go.dev/badge/github.com/bebebus/SDK/go.svg)](https://pkg.go.dev/github.com/bebebus/SDK/go) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](./LICENSE)

Provides five SDKs in **PHP / Python / Java / Go / Node.js** for the merchant payment open API (collection / payout / callback).

Design principles: **zero third-party dependencies** (only each language's standard library / official built-ins: HTTP, JSON, HMAC, test framework), **all 11 signed business endpoints** (plus non-signed endpoints such as `/version`; 13 HTTP routes total on the server), **dual environments (sandbox/production)**, **byte-for-byte identical signing across languages** (the same signature test vectors pass green in all five).

## Directory Structure

```
SDK/
├── README.md            # This file
├── SIGNING.md           # Authoritative signing algorithm description + per-language serialization pitfalls (required reading for implementation/debugging)
├── INTERFACES.md        # Field-level request/response for the 11 signed business endpoints, callback fields, error codes
├── test-vectors.json    # Cross-language signing test vectors (11 entries; asserted by all five test suites)
├── _tooling/
│   └── generate-vectors.mjs   # Vector generator (produced after cross-validation against three authoritative implementations)
├── nodejs/   # Node.js (ESM)         —— node:https/http + node:crypto + node:test
├── python/   # Python 3             —— urllib.request + hmac/hashlib + unittest
├── php/      # PHP 8                —— cURL/ext-json + hash_hmac + self-written zero-dependency runner
├── java/     # Java 17 (no Maven)    —— java.net.http + javax.crypto + self-written assertion runner
└── go/       # Go 1.2x (stdlib)      —— net/http + crypto/hmac + testing
```

## Language Matrix

npm / PyPI / Packagist / Go are published to their respective package indexes (scoped to `bebebus`, MIT, zero runtime dependencies);
**Java is NOT published to Maven — source import only** (add `java/src/main/java` to your project, or build a jar yourself via `pom.xml`). Publishing commands are in [`PUBLISHING.md`](./PUBLISHING.md).

| Language | Install / Import | HTTP (no third-party) | Test command |
|------|-------------|------------------|----------|
| Node.js | `npm i @bebebus/merchant-openapi-sdk`; `import { Client } from '@bebebus/merchant-openapi-sdk'` | `node:https` / `node:http` | `cd nodejs && node --test` |
| Python | `pip install bebebus-merchant-openapi-sdk`; `from openapi_sdk import Client` | `urllib.request` | `cd python && python3 -m unittest discover -s tests` |
| PHP | `composer require bebebus/merchant-openapi-sdk`; namespace `Merchant\Openapi` | cURL extension | `cd php && php tests/run.php` |
| Go | `go get github.com/bebebus/SDK/go@v1.1.0` (min Go 1.21); `import openapi "github.com/bebebus/SDK/go"` | `net/http` | `cd go && go test -count=1 ./...` |
| Java | **Source import (NOT published to Maven)**; `import cloud.cniia.openapi.sdk.Client` | `java.net.http.HttpClient` | `cd java && bash run-tests.sh` |

> The Go tests read the external `test-vectors.json`, and `go test`'s cache does not track that file, so **after changing the vectors use `-count=1`** to force a re-run.

## Covered Endpoints (implemented in every language)

**Collection**: `payCreate` (create order), `payQuery` (query order), `payMethodsQuery` (available pay methods), `balanceQuery` (balance), `payTestComplete` (complete test order, test key only)
**Payout**: `payoutCreate`, `payoutQuery`, `payoutBanksQuery` (available banks), `payoutProofQuery` (proof), `payoutReceiptQuery` (receipt), `payoutTestComplete` (complete test order, test key only)
**Callback**: `verifyPayCallback` / `verifyPayoutCallback` (callback signature verification, constant-time comparison)

The request/response fields for each method are in [`INTERFACES.md`](./INTERFACES.en.md). Method names follow each language's conventions (Java/JS/PHP camelCase, Python snake_case `pay_create`, Go exported camelCase), with a one-to-one semantic correspondence.

## Environment

Each SDK provides two preset base URLs and supports a **custom baseUrl override** (highest priority, trailing slash stripped):

| Preset | Base URL |
|------|------|
| `PRODUCTION` | **No built-in default; baseUrl must be passed explicitly** |
| `SANDBOX` (test/local) | `http://127.0.0.1:3090/api/open/v1` |

> The real production address is derived from **your upstream agent's dedicated domain** (in the form `https://api.<agent_domain>/api/open/v1`), provided by the platform/agent. The SDK **does not embed any production host name**: when choosing `PRODUCTION` (the default) you must pass `baseUrl` explicitly, otherwise construction throws an error.
> The "test key sandbox" can use the production baseUrl above + a test key (test orders are flagged `is_test`, do not touch real money, and can call `*/test/complete`).

## Quick Start (Node.js as an example; see each language's own README for the rest)

```js
import { Client, Config, Environment } from './nodejs/src/index.js';

const client = new Client(new Config({
  merchantNo: process.env.MERCHANT_NO,
  apiKey: process.env.API_KEY,
  apiSecretPay: process.env.API_SECRET_PAY,
  apiSecretPayout: process.env.API_SECRET_PAYOUT,
  // In production you must pass baseUrl explicitly (per the upstream agent's dedicated domain: https://api.<agent_domain>/api/open/v1)
  baseUrl: process.env.API_BASE_URL,
  // For local integration testing, switch to: environment: Environment.SANDBOX,
}));

// Create a collection order (amount is a minor-unit integer, 10000 = 1 unit)
const { data } = await client.payCreate({
  out_order_no: 'order-' + Date.now(),
  amount: 10000, currency: 'PHP', pay_method: 'gcash', country: 'PH',
  notify_url: 'https://merchant.example.com/api/notify/pay',
});
console.log(data.order_no, data.pay_url);
```

Runnable examples of `pay_create` / `payout_create` / `callback_verify` for each language are in `<language>/examples/`.

## Callback (signature verification + handling snippet)

When an order reaches a terminal state, the platform POSTs JSON to your `notify_url`, with `sign` in the body. Key handling points (examples in each language's `examples/callback_verify.*`):

1. Read the raw body → parse JSON.
2. Verify the signature: use `api_secret_pay` for collection/refund callbacks, and `api_secret_payout` for payout callbacks; use a **constant-time comparison**; compute generically over "all fields except `sign` participate" (do not hard-code the field table).
3. Process **idempotently** by `status` (the same order may be called back multiple times).
4. Respond: HTTP 200 + plain text `success` (the platform judges success by this; otherwise it retries with backoff `1m,2m,5m,10m,30m,60m`, about 6 times).

## Amount Convention

All amounts are **minor-unit integers** (= the actual amount × 10000 as an integer, e.g. `1.2345` is sent as `12345`; not currency-specific). Use integer types for SDK amount parameters to avoid cross-language floating-point formatting differences.

## Signing

HMAC-SHA256 → lowercase hexadecimal. For the algorithm and per-language landing pitfalls (nested JSON serialization must align with JS: do not escape `/`, non-ASCII, `<>&`; normalize top-level booleans to `true/false`; empty object `{}` ≠ empty array `[]`), see [`SIGNING.md`](./SIGNING.en.md).

Cross-language consistency is guaranteed by [`test-vectors.json`](./test-vectors.json) (11 signature test vectors): the unit tests of all five SDKs assert that `base` and `sign` are byte-for-byte equal for every vector. To recompute the vectors: `node _tooling/generate-vectors.mjs` (it cross-validates against three authoritative implementations before writing them out).

## Run All Tests at Once

```bash
cd nodejs && node --test && cd ..
cd python && python3 -m unittest discover -s tests && cd ..
cd php    && php tests/run.php && cd ..
cd java   && bash run-tests.sh && cd ..
cd go     && go test -count=1 ./... && cd ..
```

## Security Conventions

- **Inject credentials from environment variables/configuration**; examples and tests contain no real keys (vectors use synthetic keys). Do not commit production keys into the repository.
- Always use the SDK-provided constant-time comparison for callback signature verification; on verification failure, always reject and do not respond `success`.
- When IP allowlisting is enabled, the egress IP must be registered in the merchant backend.
