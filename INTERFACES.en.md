> [中文](./INTERFACES.md) | English

# Interface Contract (single source of truth for the SDKs in all languages)

For signing, see [`SIGNING.md`](./SIGNING.en.md). This file defines the environment addresses, authentication, request/response fields for all endpoints, callbacks, and error codes.

## 1. Environments and Base URLs

| Environment | Base URL | Description |
|------|----------|------|
| Production | **No built-in default; baseUrl must be passed explicitly** | Obtain the production address from your service provider, in the form `https://api.<service_domain>/api/open/v1` |
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

**Key selection**: `pay/*`, `pay-methods/query`, `countries/query`, `balance/query`, and `issue-ticket/*` use `api_secret_pay` (even when a ticket is for a payout order); `payout/*` uses `api_secret_payout`.

**Unified response envelope** (most business failures return HTTP 200; a few retryable system/rate-limit errors return non-2xx with a business envelope, such as `300304` on HTTP 503, `300302` on HTTP 500, and `100108` on HTTP 429):
```json
{ "code": 0, "message": "ok", "data": { /* business data; on failure there may be no data or it may contain missing_fields, etc. */ } }
```
`code === 0` means success (only valid on HTTP 2xx), anything else is an error (see the error code table). Unified semantics across all five SDKs:
- HTTP 2xx with `code === 0`: return business `data`;
- Any HTTP status whose body is a valid business JSON envelope with an integer `code !== 0` (including HTTP 503 + `300304`, HTTP 500 + `300302`, etc.): throw/return a business error (`ApiError` / `ApiException` / `*APIError`) carrying `code`, `message`, `data`, and the HTTP status code;
- Network/timeout errors, non-2xx responses without a valid non-zero business envelope (including non-2xx responses with `code === 0`), or invalid 2xx envelopes: fail-closed with a transport error (`TransportError` / `TransportException`), carrying the raw response body and the actual sent `requestSign` (for payout precheck recovery after timeouts).

## 3. Collection (Pay)

### POST `/merchant/pay/create` — create a collection order (key: pay)
Request (common fields +):

| Field | Type | Required | Description |
|------|------|------|------|
| `out_order_no` | string | Yes | Merchant order number (globally unique per merchant; duplicates are always rejected and never return the original order) |
| `amount` | int | Yes | Amount as a minor-unit integer (`10000 = 1` major unit). Per-currency bands: `PHP` `10–10,000,000`, `VND` `50,000–10,000,000,000`, `CNY` `1–1,000,000`, other valid currencies default `0.0001–100,000,000` major units; absolute cap `1e15` minor units |
| `currency` | string | Yes | Currency code (e.g. PHP/USDT) |
| `pay_method` | string | Yes | **Required.** Pay method (gcash/maya/trc20…, see pay-methods/query); `channel_code` is not accepted |
| `country` | string | No | Country ISO code; required for fiat, may be empty for cryptocurrency |
| `notify_url` | string | Yes | Callback address |
| `return_url` | string | No | Frontend redirect address |
| `subject` | string | No | Order subject |
| `remark` | string | No | Remark |
| `client_ip` | string | No | End-user IP |
| `extra` | object | No | Extension, participates in signing; may include `customer`: `{first_name,last_name,name,email,phone}` |

Response `data`: `order_no, out_order_no, amount(int), currency, pay_url(nullable, H5 payment link — open directly when non-empty), qrcode_content(nullable, raw QR payload — render as a QR code as-is without wrapping in another link), pay_params(nullable, App wake link such as gcash://xxxxxx — launch directly without re-encoding or passing to an SDK), expire_at(nullable ISO8601), status(pending|success|failed)`. On synchronous `code=0`, at least one of the three payment entries is non-empty and the others are `null`; opening/launching is not confirmation of credit — rely on order query or callback `success`.

### POST `/merchant/pay/query` — query a collection order (key: pay)
Request: `order_no` or `out_order_no` (**either one, at least one**).
Response `data`: `order_no, out_order_no, amount, actual_amount(nullable, end-user's actual paid amount — primary basis for crediting and reconciliation), fee_amount(nullable), net_amount(nullable, actual paid − fee), currency, status(pending|success|failed), channel_order_no(always null; use order_no or out_order_no for order queries and correlation), paid_at(nullable), notify_status(pending|success|failed)`.

### POST `/merchant/pay-methods/query` — available pay methods (key: pay)
Request: `country` (optional).
Response `data.methods[]`: `{pay_method, name, name_i18n{zh-CN,en-US}, logo_svg(nullable), country(nullable), currency(nullable)}`.

> `groupsQuery` (`/merchant/groups/query`) is a legacy, undocumented compatibility alias with envelope `{groups}`; new integrations should always use this endpoint instead.

### POST `/merchant/countries/query` — available countries (key: pay)
Request: no business fields (common fields only).
Response `data.countries[]`: `{country, name, name_i18n{zh-CN,en-US}, currencies[]}`.

### POST `/merchant/balance/query` — balance query (key: pay)
Request: `currency` (optional filter).
Response `data.balances[]`: `{currency, available(int), frozen(int)}`.

### POST `/merchant/pay/test/complete` — complete a collection test order (key: pay, **test key only**)
Request: `order_no` or `out_order_no` (either one) + `result`(`success`|`failed`, required) + `actual_amount`(int, optional).
Response `data`: `order_no, out_order_no, amount, actual_amount, status`. Calling with a production key is rejected.

## 4. Payout

> `pay_method` is required; `channel_code`/`group_code` are not accepted.

### POST `/merchant/payout/create` — create a payout order (key: payout)
Request (common fields +):

| Field | Type | Required | Description |
|------|------|------|------|
| `out_payout_no` | string | Yes | Merchant payout number (globally unique per merchant; duplicates are always rejected and never return the original order) |
| `amount` | int | Yes | Amount as a minor-unit integer (`10000 = 1` major unit). Per-currency bands: `PHP` `10–10,000,000`, `VND` `50,000–10,000,000,000`, `CNY` `1–1,000,000`, other valid currencies default `0.0001–100,000,000` major units; absolute cap `1e15` minor units |
| `currency` | string | Yes | Currency code |
| `pay_method` | string | Yes | **Required.** Pay method (bank/gcash/maya/trc20…, see pay-methods/query); `channel_code` is not accepted |
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
Request: `pay_method`(required, usually `bank` when querying banks) + `country`(country ISO code such as `PH`; documented as required) + `currency`(currency code such as `PHP`; documented as required).
Response `data.banks[]`: `{code, name}` (empty array for wallet types or payment methods with no available bank). Use this `code` as `bank_code` when creating the payout.

### POST `/merchant/payout/proof/query` — payout proof query (key: payout)
Request: `payout_no` or `out_payout_no` (either one). Only queryable when `status=success`.
Response `data`: `payout_no, out_payout_no, proof_url, expires_in(seconds, nullable), queried_at(nullable)`. The proof has a time limit, use it as soon as you obtain it.
Errors: `300408` (payment method does not support proof queries, or test credentials not supported) / `300409` (order is not in a successful state, proof not found, or outside the query window).

### POST `/merchant/payout/receipt/query` — payout receipt (key: payout)
Request: `payout_no` or `out_payout_no` (either one) + `lang`(`en`|`zh-CN`|`zh-TW`, optional) + `inline`(optional, `1`/`0` or boolean `true`/`false`).
> The SDK normalizes `inline` to an **integer 1/0** (to avoid cross-language boolean signing ambiguity): `1` returns a base64 image inline, `0`/omitted returns a site-root relative URL with a token (`receipt_url`; resolve it against the host origin and GET it, do not append to Base URL).
Response: `inline=0`/omitted → `{payout_no, out_payout_no, lang, receipt_url, expires_in}`; `inline=1` → `{payout_no, out_payout_no, lang, mime, image_base64}`.
Errors: `300410` (order is not in `success` state, or test credentials not supported) / `300411` (receipt generation failed).

### POST `/merchant/payout/test/complete` — complete a payout test order (key: payout, **test key only**)
Request: `payout_no` or `out_payout_no` (either one) + `result`(`success`|`failed`, required). Response: `{payout_no, out_payout_no, amount, status}`.

### Payout precheck (the merchant's own endpoint)

During review the platform POSTs JSON to the merchant's public HTTPS URL. This is not a platform endpoint and not the final callback. The SDK does not send this request. Call the helper inside the merchant endpoint and write its return value as the entire HTTP 200 body.

The body must be exactly these four non-empty strings:

| Field | Meaning |
|------|------|
| `out_payout_no` | Must equal the merchant payout number saved at create time |
| `payout_no` | Platform payout number. Required and non-empty. Not compared with the create request |
| `account_no` | Must equal the account number saved at create time |
| `original_sign` | Compared with the create request `sign` as equal-length UTF-8 bytes, in constant time |

After a successful create, save `out_payout_no`, `account_no`, and the `sign` that was actually sent. The SDK exposes that sign as: Node.js return value `sign`, Python `.sign` on the returned data, PHP `lastRequestSign()` read immediately after the call returns, Go `Response.Sign`, Java `ApiResponse.requestSign()`. If `payout/create` encounters a transport failure (e.g., timeout after the server already accepted the order and later triggers precheck), the sent `sign` is also exposed on the transport error: Node.js `err.requestSign`, Python `err.request_sign`, PHP `$e->requestSign` / `$client->lastRequestSign()`, Go `te.RequestSign`, Java `e.requestSign()`.

Helper names: Node.js `decidePayoutPrecheck`, Python `decide_payout_precheck`, PHP `PayoutPrecheck::decide`, Go `DecidePayoutPrecheck`, Java `PayoutPrecheck.decide`. The only return values are the exact strings `approve` and `reject`. Invalid input returns `reject` and does not throw. The HTTP body must be that string alone, with no added space, newline, BOM, or JSON. `approve` only passes this check and does not skip the existing review.

## 5. Callback (Notify)

When an order reaches a terminal state (or a ticket receives a new upstream reply), the service sends a JSON callback to `notify_url`, with `sign` in the body. For signature verification and the response, see SIGNING.md §6 (**verify generically over "all fields except sign", do not hard-code the field table**).

- **Collection callback** (key `api_secret_pay`) common fields: `merchant_no, order_no, out_order_no, amount, actual_amount(nullable), fee_amount(nullable), net_amount(nullable), currency, status, channel_order_no(always null; use order_no or out_order_no for order correlation), paid_at(nullable), sign`.
- **Payout callback** (key `api_secret_payout`) common fields: `merchant_no, payout_no, out_payout_no, amount, currency, status, fee_amount(nullable), channel_order_no(always null; use payout_no or out_payout_no for order correlation), finished_at(nullable), failed_reason(nullable), sign`.
- **Refund callback** (key `api_secret_pay`) common fields: `merchant_no, order_no(nullable), out_order_no(nullable), refund_no, out_refund_no, amount, currency, status, channel_order_no(always null), source_channel_order_no(always null, excluded from signature), finished_at(nullable), failed_reason(nullable), sign`.
- **Ticket reply callback** (key `api_secret_pay`, even for payout orders) fields: `merchant_no, ticket_no, order_no, order_type(pay|payout), status, content, sign`. Sent only when `notify_url` was passed to `issue-ticket/create`; each new upstream reply is posted once; does not include `out_order_no`, `created_at`, `forward_error`, or screenshot bytes.

> Callback fields may be added or removed in future versions, so the SDK's signature verifier **relies only on the rule "all fields except sign participate"**; business processing branches on `status` and stays idempotent. The response is uniformly HTTP 200 + plain text `success`.

## 6. Error Codes

| code | HTTP | Meaning |
|------|------|---------|
| 0 | 200 | Success |
| 100000 | 200 | Generic business failure / unified auth-failure code (invalid/disabled/nonexistent credential, etc.) |
| 100001 | 200 | Parameter validation failed (message is the specific field error, e.g. `"amount" must be a number`) |
| 100101 | 200 | Request expired (timestamp outside ±300s) |
| 100102 | 200 | IP not in the allowlist |
| 100103 | 200 | Replay / duplicate request (nonce or signature fingerprint repeated within 300s) |
| 100104 | 200 | Signature error |
| 100105 | 200 | IP in the blocklist |
| 100106 | 200 | Too many auth failures (rate limited; same merchant_no + IP fails auth 60 times within 60s) |
| 100108 | 429 | Too many landing-page reads (> 6000 calls per IP per 60s; `Retry-After` header included) |
| 200002 | 200 | Service account disabled |
| 210002 | 200 | Merchant disabled |
| 300101 | 200 | Duplicate collection order number (rejected whenever out_order_no already exists, regardless of parameter equality; query first when the result is unknown, and use a new number to re-submit) |
| 300201 | 200 | Duplicate payout order number (rejected whenever out_payout_no already exists, regardless of parameter equality) |
| 300301 | 200 | Order does not exist |
| 300302 | 500 | Order data temporarily unavailable (HTTP 500, retryable; persistence error or local order temporarily unreadable) |
| 300304 | 503 | Order create temporarily unavailable (nothing persisted, HTTP 503; retry later with the same `out_order_no`/`out_payout_no` — do not change the number) |
| 300308 | 200 | Ticket not found |
| 300309 | 200 | Current order status does not accept a ticket (see `message` for the specific reason) |
| 300401 | 200 | Payment method unavailable / missing |
| 300402 | 200 | Payment method configuration unavailable |
| 300403 | 200 | Fee rate not configured |
| 300404 | 200 | No payment method is available for the request |
| 300405 | 200 | Missing required additional info (`data.missing_fields`) |
| 300406 | 200 | Missing required order parameters (`data.missing_fields`) |
| 300407 | 200 | Invalid bank code |
| 300408 | 200 | Payment method does not support proof queries (or not available for test credentials) |
| 300409 | 200 | Payout proof temporarily unavailable (order not successful / proof not ready / outside the query window) |
| 300410 | 200 | Payout receipt temporarily unavailable (order not successful / not available for test credentials) |
| 300411 | 200 | Receipt generation failed |
| 300501 | 200 | Insufficient balance |

> Error codes are authoritative on the server side and may be added; each language's `appendix/error-codes.*` is synced with the server. SDK exceptions carry the raw `code`/`message`/`data` and HTTP status code for the caller to judge — do not exhaustively hard-code branches.

## 7. Issue Ticket

> Ticket creation, query, and reply callbacks are all signed with the collection secret `api_secret_pay` (even when the referenced order is a payout order — do not use `api_secret_payout`).

### POST `/merchant/issue-ticket/create` — create a ticket (key: pay)
Request (common fields +):

| Field | Type | Required | Description |
|------|------|------|------|
| `order_no` | string | Yes | Platform order number or merchant order number |
| `content` | string | No | Ticket text (a ticket is still created when both content and image are empty) |
| `image_base64` | string | No | Raw Base64 screenshot (participates in signing; no `data:` prefix; decoded size ≤ 5MB); provide together with `image_mime`, or omit both |
| `image_mime` | string | No | Only `image/jpeg`, `image/png`, `image/webp` |
| `notify_url` | string | No | Optional public HTTPS callback URL; omit/empty for no callback; internal address or invalid scheme returns `100001` and creates no ticket |

Response `data`: `ticket_no, order_no, out_order_no(nullable), order_type(pay|payout)` (does not include ticket status).
Common errors: `100001` (invalid params/image/notify_url) / `300301` (order not found) / `300309` (order status does not accept a ticket).

### POST `/merchant/issue-ticket/query` — query a ticket (key: pay)
Request: `ticket_no` or `order_no` (**provide at least one**; when only `order_no` is given, the latest ticket for that order is returned).
Response `data`: `ticket_no, order_type(pay|payout), order_no, out_order_no(nullable), status(open|forwarded|replied|forward_failed|closed), content(nullable), has_photo(bool), created_at(nullable ISO8601), replied_at(nullable ISO8601), replies[](at most 100 items, each with content/original_content/has_photo/has_attachment/created_at), replies_truncated(bool)`. Screenshot bytes and `forward_error` are not returned.
Common errors: `300308` (ticket not found) / `300301` (order not found).
