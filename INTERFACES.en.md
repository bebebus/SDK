> [中文](./INTERFACES.md) | English

# Interface Contract (single source of truth for the SDKs in all languages)

For signing, see [`SIGNING.md`](./SIGNING.en.md). This file defines the environment addresses, authentication, request/response fields for all endpoints, callbacks, and error codes.

## 1. Environments and Base URLs

| Environment | Base URL | Description |
|------|----------|------|
| Production | **No built-in default; baseUrl must be passed explicitly** | Obtain the production address from your service provider (in the form `https://api.<service_domain>/api/open/v1`) |
| Sandbox (test/local) | `http://127.0.0.1:3090/api/open/v1` | Self-hosted / integration testing; you can also use "production baseUrl + test key" as a sandbox (orders created with a test key are flagged `is_test`, do not touch real money, and can call `*/test/complete`) |

SDK design: `Environment.SANDBOX` embeds the local preset base URL; `Environment.PRODUCTION` (the default) **embeds no host name** and requires `baseUrl` (obtain it from your service provider) to be passed explicitly, otherwise construction throws an error. All requests are `POST`, `Content-Type: application/json`, with a JSON request body.

## 2. Authentication and Common Fields

Every request body contains the following common fields (at the same level as business fields, and they participate in signing together):

| Field | Type | Required | Description |
|------|------|------|------|
| `merchant_no` | string | Yes | Merchant number |
| `api_key` | string | Yes | API Key |
| `timestamp` | int | Yes | Unix seconds; the server validates a **±300s** window |
| `nonce` | string | No | Anti-replay random string; if omitted, the server deduplicates by signature fingerprint within ±300s. **Generating a unique value per request is recommended** |
| `sign` | string | Yes | See SIGNING.md |

**Key selection**: `pay/*`, `pay-methods/query`, `balance/query` use `api_secret_pay`; `payout/*` uses `api_secret_payout`.

**Unified response envelope** (a business failure is usually still HTTP 200, judged by `code`):
```json
{ "code": 0, "message": "ok", "data": { /* business data; on failure there may be no data or it may contain missing_fields, etc. */ } }
```
`code === 0` means success, anything else is an error (see the error code table). The SDK should: throw a network exception on HTTP-layer errors; throw a business exception carrying `code`/`message`/`data` when `code !== 0` (the caller may also choose not to throw and read `code` directly, with the SDK exposing the raw response).

## 3. Collection (Pay)

### POST `/merchant/pay/create` — create a collection order (key: pay)
Request (common fields +):

| Field | Type | Required | Description |
|------|------|------|------|
| `out_order_no` | string | Yes | Merchant order number (idempotency key, unique) |
| `amount` | int | Yes | Amount as a minor-unit integer, `[1, 1e12]` |
| `currency` | string | Yes | Currency code (e.g. PHP/USDT) |
| `pay_method` | string | Yes | Pay method (gcash/maya/trc20…, see pay-methods/query) |
| `country` | string | No | Country ISO code; required for fiat, may be empty for cryptocurrency |
| `notify_url` | string | Yes | Callback address |
| `return_url` | string | No | Frontend redirect address |
| `subject` | string | No | Order subject |
| `remark` | string | No | Remark |
| `client_ip` | string | No | End-user IP |
| `extra` | object | No | Extension, participates in signing; may include `customer`: `{first_name,last_name,name,email,phone}` |

Response `data`: `order_no, out_order_no, amount(int), currency, pay_url(nullable), qrcode_content(nullable), pay_params(nullable), expire_at(nullable ISO8601), status(pending|success|failed)`.

### POST `/merchant/pay/query` — query a collection order (key: pay)
Request: `order_no` or `out_order_no` (**either one, at least one**).
Response `data`: `order_no, out_order_no, amount, currency, status(pending|success|failed), channel_order_no(always null; use order_no or out_order_no for order queries and correlation), paid_at(nullable), notify_status(pending|success|failed)`.

### POST `/merchant/pay-methods/query` — available pay methods (key: pay)
Request: `country` (optional filter).
Response `data.methods[]`: `{pay_method, name, country(nullable), currency(nullable)}`.

### POST `/merchant/balance/query` — balance query (key: pay)
Request: `currency` (optional filter).
Response `data.balances[]`: `{currency, available(int), frozen(int)}`.

### POST `/merchant/pay/test/complete` — complete a collection test order (key: pay, **test key only**)
Request: `order_no` or `out_order_no` (either one) + `result`(`success`|`failed`, required) + `actual_amount`(int, optional).
Response `data`: `order_no, out_order_no, amount, actual_amount, status`. Calling with a production key is rejected.

## 4. Payout

### POST `/merchant/payout/create` — create a payout order (key: payout)
Request (common fields +):

| Field | Type | Required | Description |
|------|------|------|------|
| `out_payout_no` | string | Yes | Merchant payout number (idempotency key, unique) |
| `amount` | int | Yes | Amount as a minor-unit integer, `[1, 1e12]` |
| `currency` | string | Yes | Currency code |
| `pay_method` | string | Yes | Pay method |
| `country` | string | No | Country ISO code; required for fiat |
| `notify_url` | string | Yes | Callback address |
| `account_no` | string | Yes | Payee account/address (card number/wallet address, etc.) |
| `account_name` | string | No | Payee name (may be empty for on-chain address types) |
| `bank_code` | string | No | **Required** for bank types, taken from the `code` in banks/query; not needed for wallet types (gcash) |
| `bank_name` | string | No | Bank name (the system backfills by bank_code, usually no need to pass) |
| `remark` | string | No | Remark |
| `client_ip` | string | No | End-user IP |
| `extra` | object | No | Extension, participates in signing; `customer` is the same as for collection (for payout the top-level `account_name` already serves as the customer name, so usually only email/phone need to be supplemented) |

Response `data`: `payout_no, out_payout_no, amount, currency, status, review_status(nullable), fee_amount(nullable), freeze_amount(nullable)`.
> Successful creation = accepted and balance frozen; the final result is determined by the asynchronous callback and order query.

### POST `/merchant/payout/query` — query a payout order (key: payout)
Request: `payout_no` or `out_payout_no` (either one).
Response `data`: `payout_no, out_payout_no, amount, currency, status, sub_state(in-progress sub-state accepted|reviewing|processing|verifying, null in a terminal state), channel_order_no(always null; use payout_no or out_payout_no for order queries and correlation), finished_at(nullable), failed_reason(nullable), notify_status`.

### POST `/merchant/payout/banks/query` — available banks (key: payout)
Request: `pay_method`(required) + `country`(required for fiat) + `currency`(optional).
Response `data.banks[]`: `{code, name}` (empty array for wallet types or payment methods with no available bank). Use this `code` as `bank_code` when creating the payout.

### POST `/merchant/payout/proof/query` — payout proof query (key: payout)
Request: `payout_no` or `out_payout_no` (either one). Only queryable when `status=success`.
Response `data`: `payout_no, out_payout_no, proof_url, expires_in(seconds, nullable)`. The proof has a time limit, use it as soon as you obtain it.
Errors: `300408` (payment method does not support proof queries) / `300409` (proof not found or outside the query window) / `300410` (order is not in a successful state).

### POST `/merchant/payout/receipt/query` — payout receipt (key: payout)
Request: `payout_no` or `out_payout_no` (either one) + `lang`(`en`|`zh-CN`|`zh-TW`, optional) + `inline`(optional).
> The SDK sends `inline` as an **integer 1/0** (to avoid cross-language boolean signing ambiguity): `1` returns a base64 image inline, `0`/omitted returns a URL with a token.
Response: `inline=0` → `{payout_no, out_payout_no, receipt_url, expires_in}`; `inline=1` → `{payout_no, out_payout_no, mime, image_base64}`.

### POST `/merchant/payout/test/complete` — complete a payout test order (key: payout, **test key only**)
Request: `payout_no` or `out_payout_no` (either one) + `result`(`success`|`failed`, required). Response: `{payout_no, out_payout_no, amount, status}`.

## 5. Callback (Notify)

When an order reaches a terminal state, the service sends a JSON callback to `notify_url`, with `sign` in the body. For signature verification and the response, see SIGNING.md §6 (**verify generically over "all fields except sign", do not hard-code the field table**).

- **Collection callback** (key `api_secret_pay`) common fields: `merchant_no, order_no, out_order_no, amount, actual_amount(nullable), fee_amount(nullable), net_amount(nullable), currency, status, channel_order_no(always null; use order_no or out_order_no for order correlation), paid_at(nullable), sign`.
- **Payout callback** (key `api_secret_payout`) common fields: `merchant_no, payout_no, out_payout_no, amount, currency, status, fee_amount(nullable), channel_order_no(always null; use payout_no or out_payout_no for order correlation), finished_at(nullable), failed_reason(nullable), sign`.
- **Refund callback** (key `api_secret_pay`) common fields: `merchant_no, order_no(nullable), out_order_no(nullable), refund_no, out_refund_no, amount, currency, status, channel_order_no(always null; use refund_no or out_refund_no for refund correlation), finished_at(nullable), failed_reason(nullable), sign`.

> Callback fields may be added or removed in future versions, so the SDK's signature verifier **relies only on the rule "all fields except sign participate"**; business processing branches on `status` and stays idempotent. The response is uniformly HTTP 200 + plain text `success`.

## 6. Error Codes

| code | Meaning |
|------|------|
| 0 | Success |
| 100000 | Generic business failure / unified auth-failure code (invalid/disabled/nonexistent credential, etc.) |
| 100001 | Parameter validation failed (message is the specific field error, e.g. `"amount" must be a number`) |
| 100101 | Request expired (timestamp outside ±300s) |
| 100102 | IP not in the allowlist |
| 100103 | Replay / duplicate request (nonce or signature fingerprint repeated within 300s) |
| 100104 | Signature error |
| 100105 | IP in the blocklist |
| 100106 | Too many auth failures (rate limited; same merchant_no + IP fails auth 60 times within 60s) |
| 200002 | Service account disabled |
| 210002 | Merchant disabled |
| 300101 | Collection idempotency conflict (out_order_no exists with mismatched params) |
| 300201 | Payout order idempotency conflict |
| 300301 | Order does not exist |
| 300401 | Payment method unavailable / missing |
| 300402 | Payment method configuration unavailable |
| 300403 | Fee rate not configured |
| 300404 | No payment method is available for the request |
| 300405 | Missing required additional info (`data.missing_fields`) |
| 300406 | Missing required order parameters (`data.missing_fields`) |
| 300407 | Invalid bank code |
| 300408 | The payment method does not support proof queries |
| 300409 | Proof not found / outside the query window |
| 300410 | Order not in a success state (proof/receipt not queryable) |
| 300411 | Receipt generation failed |
| 300501 | Insufficient balance |

> Error codes are authoritative on the server side and may be added; SDK exceptions should carry the raw `code`/`message`/`data` for the caller to judge, do not exhaustively hard-code branches.
