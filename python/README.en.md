> [中文](./README.md) | English

# Merchant Payment OpenAPI — Python SDK

[![PyPI](https://img.shields.io/pypi/v/bebebus-merchant-openapi-sdk?label=PyPI)](https://pypi.org/project/bebebus-merchant-openapi-sdk/) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

A **zero-dependency** Python 3 SDK: HTTP via `urllib.request`, signing via `hmac`/`hashlib`, and tests via the standard-library `unittest`. No runtime dependency needs `pip install`.

The signing algorithm is byte-for-byte consistent with the server-side signing implementation, and the unit tests fully reproduce `base` and `sign` against [`../test-vectors.json`](../test-vectors.json).

## Importing (no dependency install needed)

Just drop the `openapi_sdk/` directory into your project (or add this directory to `sys.path` / `PYTHONPATH`):

```python
import sys
sys.path.insert(0, "/path/to/python")

from openapi_sdk import Client, Config, Environment
```

Optionally, install it via standard packaging (still zero dependencies):

```bash
cd python
pip install .        # or python3 -m build; it pulls in no third-party runtime dependencies
```

## Quick start

```python
from openapi_sdk import Client, Config, Environment, ApiError, TransportError

config = Config(
    merchant_no="M00000001",
    api_key="ak_xxx",
    api_secret_pay="sk_pay_xxx",        # pay endpoints + collection/refund callbacks
    api_secret_payout="sk_payout_xxx",  # payout endpoints + payout callbacks
    environment=Environment.SANDBOX,    # or Environment.PRODUCTION (base_url must be passed explicitly)
)
client = Client(config)

try:
    # Amount is an integer in the smallest unit: 10000 = 1 unit of currency
    order = client.pay_create(
        out_order_no="ORD1", amount=10000, currency="PHP",
        pay_method="gcash", country="PH",
        notify_url="https://m.example.com/api/notify/pay",
    )
    print(order["pay_url"])
except ApiError as e:        # business failure code != 0
    print(e.code, e.message, e.data)
except TransportError as e:  # HTTP / network / timeout
    print(e, e.status_code)
```

## Dual environments and custom base URL

| Environment | Base URL |
|------|------|
| `Environment.PRODUCTION` | No built-in base URL; **`base_url` must be passed explicitly** |
| `Environment.SANDBOX` | `http://127.0.0.1:3090/api/open/v1` |

Obtain the production address from your service provider (`https://api.<service_domain>/api/open/v1`) and pass it explicitly via `base_url=`. Choosing `PRODUCTION` without passing `base_url` raises a `ValueError` (with the message `baseUrl is required`):

```python
config = Config(
    merchant_no="M00000001", api_key="ak_xxx",
    api_secret_pay="...", api_secret_payout="...",
    base_url="https://api.service.example.com/api/open/v1",  # required in production
)
```

## All 11 endpoints

Collection (secret `api_secret_pay`, selected automatically):

| Method | Endpoint |
|------|------|
| `pay_create(...)` | `/merchant/pay/create` |
| `pay_query(order_no=, out_order_no=)` | `/merchant/pay/query` |
| `pay_methods_query(country=)` | `/merchant/pay-methods/query` |
| `balance_query(currency=)` | `/merchant/balance/query` |
| `pay_test_complete(result=, ...)` | `/merchant/pay/test/complete` (test secret only) |

Payout (secret `api_secret_payout`, selected automatically):

| Method | Endpoint |
|------|------|
| `payout_create(...)` | `/merchant/payout/create` |
| `payout_query(payout_no=, out_payout_no=)` | `/merchant/payout/query` |
| `payout_banks_query(pay_method=, country=, currency=)` | `/merchant/payout/banks/query` |
| `payout_proof_query(payout_no=, out_payout_no=)` | `/merchant/payout/proof/query` |
| `payout_receipt_query(..., inline=)` | `/merchant/payout/receipt/query` |
| `payout_test_complete(result=, ...)` | `/merchant/payout/test/complete` (test secret only) |

Conventions:

- Each request automatically injects `merchant_no`/`api_key`/`timestamp` (Unix seconds)/a unique `nonce` and `sign`.
- Parameters whose value is `None` are neither placed in the request body nor included in the signature.
- The `inline` field of `payout_receipt_query` is sent as the integer **1/0** (`True`→1 inlines a base64 image; `False`→0 returns a token-bearing URL).
- The amount is an integer in the smallest unit (`10000 = 1 unit of currency`), passed as `int`.
- On success, returns `data` (a dict); `code != 0` raises `ApiError` (carrying `code`/`message`/`data`); HTTP/network errors raise `TransportError`.
- When you need the raw envelope, use `client.call_raw(path, body, secret)` (which does not throw on `code != 0`).

## Signing utilities (usable standalone)

```python
from openapi_sdk import build_sign_base, sign, verify_callback

base = build_sign_base(payload, secret)   # byte-for-byte assertable signature base
sig  = sign(payload, secret)              # HMAC-SHA256 -> lowercase hex
ok   = verify_callback(callback, secret)  # timing-safe (hmac.compare_digest), field-agnostic
```

## Callback signature verification + processing

See [`examples/callback_verify.py`](examples/callback_verify.py): parse the raw body → `verify_callback` (timing-safe) → process idempotently by `status` (success/failed) → acknowledge with **HTTP 200 + plain text `success`**. Collection callbacks use `api_secret_pay` and payout callbacks use `api_secret_payout`; the example demonstrates each once. On signature verification failure, do not return success; the same order may be sent again. The processing must be idempotent (the same order may be called back multiple times).

## Examples

```bash
cd python
python3 examples/pay_create.py
python3 examples/payout_create.py
python3 examples/callback_verify.py   # self-demo: build a signed callback -> verify -> acknowledge -> tampering counter-example
```

## Running the tests

```bash
cd python
python3 -m unittest discover -s tests
```

The tests include:

- Reading `../test-vectors.json` and, for each vector, asserting `build_sign_base == base` and `sign == sign`;
- A positive callback signature verification case plus negative cases for tampering one byte (including the wrong secret and missing sign);
- Client request building (common field injection, `None` filtering, secret selection, `inline` integer conversion, envelope parsing and exception classification).
