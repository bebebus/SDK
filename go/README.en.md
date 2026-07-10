# Merchant Payment OpenAPI · Go SDK

> [中文](./README.md) | English

[![Go Reference](https://pkg.go.dev/badge/github.com/bebebus/SDK/go.svg)](https://pkg.go.dev/github.com/bebebus/SDK/go) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

A zero-dependency Go SDK covering all 11 endpoints of the Merchant OpenAPI (5 collection + 6 payout), including
HMAC-SHA256 signing, dual-environment configuration, and timing-safe callback signature verification. It uses only the Go standard library (`net/http` / `crypto/hmac`
/ `crypto/sha256` / `encoding/json`), with **no `require` at all**.

- module: `github.com/bebebus/SDK/go`
- package: `openapi`
- Go: 1.26 (declared in `go.mod`; backward-compatible with standard-library features of 1.21+)

## Importing (no dependency installation needed)

This SDK depends on no external package. Two ways to use it:

1. Copy this `go/` directory into your project and import it by its module path; or
2. Add `require github.com/bebebus/SDK/go v0.0.0` to your own `go.mod` and use `replace`
   to point at a local path (while it is private and unpublished):

```go
import "github.com/bebebus/SDK/go"
```

## Quick Start

```go
package main

import (
    "context"
    "errors"
    "fmt"

    "github.com/bebebus/SDK/go"
)

func main() {
    client := openapi.NewClient(openapi.Config{
        MerchantNo:   "M00000001",
        APIKey:       "ak_demo_key",
        SecretPay:    "<api_secret_pay>",    // collection-type calls + collection/refund callbacks
        SecretPayout: "<api_secret_payout>", // payout-type calls + payout callbacks
        Environment:  openapi.Sandbox,       // or openapi.Production (BaseURL must be passed explicitly)
    })

    resp, err := client.PayCreate(context.Background(), map[string]any{
        "out_order_no": "202501010001",
        "amount":       10000, // integer in the smallest unit, 10000 = 1.00
        "currency":     "PHP",
        "pay_method":   "gcash",
        "country":      "PH",
        "notify_url":   "https://merchant.example.com/api/notify/pay",
    })
    if err != nil {
        var apiErr *openapi.APIError
        if errors.As(err, &apiErr) {
            fmt.Println("business failure:", apiErr.Code, apiErr.Message)
            return
        }
        fmt.Println("transport failure:", err) // *openapi.TransportError
        return
    }
    fmt.Println("order_no:", resp.Data["order_no"])
}
```

### The 11 Endpoint Methods

| Category | Method | Endpoint | Secret |
|------|------|------|------|
| collection | `PayCreate` | `/merchant/pay/create` | pay |
| collection | `PayQuery` | `/merchant/pay/query` | pay |
| collection | `PayMethodsQuery` | `/merchant/pay-methods/query` | pay |
| collection | `BalanceQuery` | `/merchant/balance/query` | pay |
| collection | `PayTestComplete` | `/merchant/pay/test/complete` | pay |
| payout | `PayoutCreate` | `/merchant/payout/create` | payout |
| payout | `PayoutQuery` | `/merchant/payout/query` | payout |
| payout | `PayoutBanksQuery` | `/merchant/payout/banks/query` | payout |
| payout | `PayoutProofQuery` | `/merchant/payout/proof/query` | payout |
| payout | `PayoutReceiptQuery` | `/merchant/payout/receipt/query` | payout |
| payout | `PayoutTestComplete` | `/merchant/payout/test/complete` | payout |

Each method automatically injects the common fields (`merchant_no` / `api_key` / `timestamp` / a unique `nonce`) and `sign`, and
automatically picks the correct secret. **Fields whose value is `nil` neither enter the request body nor participate in signing**. The `inline` field of `PayoutReceiptQuery`
is passed as a standalone boolean parameter, and the SDK automatically sends it as an **integer 1/0** (to avoid boolean signing ambiguity).

## Dual-Environment Configuration

```go
// Preset: production (no built-in URL; BaseURL must be passed explicitly)
openapi.NewClient(openapi.Config{Environment: openapi.Production, BaseURL: "https://api.<service_domain>/api/open/v1", /* ... */})
// Preset: local sandbox (http://127.0.0.1:3090/api/open/v1)
openapi.NewClient(openapi.Config{Environment: openapi.Sandbox, /* ... */})
// Custom base URL override (service-provider URL or another port) — takes precedence over Environment
openapi.NewClient(openapi.Config{BaseURL: "https://api.<service_domain>/api/open/v1", /* ... */})
```

- `Production` → **no built-in URL**. Obtain the production base URL from your service provider, in the form
  `https://api.<service_domain>/api/open/v1`, and provide it explicitly via `Config.BaseURL`.
  If you choose `Production` but do not pass `BaseURL`, `NewClient` does not panic, but the first request returns
  `ErrBaseURLRequired` (a clear error).
- `Sandbox` → `http://127.0.0.1:3090/api/open/v1`
- `Config.Timeout` defaults to 30s.

## Callback Signature Verification and Acknowledgement

Callbacks are computed generically as "every field except `sign` participates in signing" (field-agnostic, so callback fields may be added or removed); the comparison uses
`hmac.Equal` and is timing-safe. Use `VerifyPayCallback` for collection/refund callbacks, and `VerifyPayoutCallback` for payout callbacks.

```go
raw, _ := io.ReadAll(r.Body)

dec := json.NewDecoder(bytes.NewReader(raw))
dec.UseNumber() // keep integers in textual form, so big integers are not written as float64 like 1e+12
var payload map[string]any
_ = dec.Decode(&payload)

if !client.VerifyPayCallback(payload) { // for payout callbacks use VerifyPayoutCallback
    w.WriteHeader(http.StatusForbidden) // verification failed: do not reply success; the same order may be sent again
    return
}

switch payload["status"] {
case "success":
    // idempotent crediting/fulfillment (the same order may be called back multiple times)
case "failed":
    // idempotently mark as failed
}

w.WriteHeader(http.StatusOK)
io.WriteString(w, "success") // correct acknowledgement: HTTP 200 + plain text success
```

A complete runnable example is in `examples/callback_verify` (demonstrates collection + payout once each).

## Signing (usable standalone)

```go
base := openapi.BuildSignBase(payload, secret) // handy for byte-for-byte assertions
sign := openapi.Sign(payload, secret)
ok := openapi.VerifyCallback(payload, secret)  // timing-safe
```

The serialization is byte-for-byte identical to the server-side signing implementation: nested JSON keys are recursively sorted in ascending order by a custom implementation,
compact with no whitespace, not escaping `/`, non-ASCII, or `<>&`, escaping only `" \ \b\f\n\r\t` and the remaining control characters; top-level scalars are kept as-is
(`bool` → `true/false`, integers in decimal). On-the-wire serialization uses `Encoder.SetEscapeHTML(false)`.

## Error Handling

- Business failure (envelope `code != 0`) → `*openapi.APIError` (contains `Code`/`Message`/`Data`/`Raw`).
- HTTP/network/non-2xx/parse failure → `*openapi.TransportError` (contains `StatusCode`/`Raw`/`Err`, supports `errors.Unwrap`).
- Even when an error is returned, `*Response.Raw` still preserves the raw response body for troubleshooting.

Distinguish them with `errors.As`:

```go
var apiErr *openapi.APIError
var trErr  *openapi.TransportError
switch {
case errors.As(err, &apiErr):  // business code
case errors.As(err, &trErr):   // transport layer
}
```

## Running the Examples

```bash
go run ./examples/pay_create
go run ./examples/payout_create
go run ./examples/callback_verify   # offline demo of verification + handling + acknowledgement, no network needed
go run ./examples/query
```

## Running the Tests

```bash
cd go
go test ./...          # all
go test -v ./...       # verbose (includes a subtest per signature vector)
```

The unit tests read `../test-vectors.json` and, for each vector, assert `BuildSignBase == base` and `Sign == sign`,
and also cover a positive callback-verification case + a one-byte-tampered negative case + a wrong-secret negative case, as well as the client's common-field injection, secret selection,
`inline` integer conversion, and business/transport error classification. They use the standard-library `testing` package, with no third-party test framework.
