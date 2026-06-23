> [中文](./README.md) | English

# Merchant Payment OpenAPI — Java SDK

A Java client with zero third-party dependencies, covering all **11 endpoints** of the merchant payment OpenAPI, implementing HMAC-SHA256 request signing and callback signature verification.

- **JDK**: 17+ (uses `java.net.http.HttpClient`, `javax.crypto.Mac`, `MessageDigest.isEqual`, all from the standard library).
- **Dependencies**: none. HTTP, JSON, HMAC, and tests all use JDK built-ins, **without pulling in any library through a package manager** (no Jackson/Gson/OkHttp/JUnit).
- **Package**: `cloud.cniia.openapi.sdk`.

For the signing algorithm and field contracts, see [`SIGNING.md`](../SIGNING.en.md) and [`INTERFACES.md`](../INTERFACES.en.md) in the repository root; for the canonical signature answers, see [`test-vectors.json`](../test-vectors.json).

## How to include (no dependency installation needed)

This SDK has no external dependencies. The simplest approach is to bring the source directory directly into your project:

```
src/main/java/cloud/cniia/openapi/sdk/*.java
```

Just add these 7 source files (`Signer / Config / Environment / Client / Json / ApiException / TransportException`, plus `ApiResponse`) to your compilation sources.

Or compile into classes / jar with `javac`:

```bash
# Compile into out/
javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name '*.java')
# Package into a jar
jar cf openapi-sdk.jar -C out .
```

A [`pom.xml`](./pom.xml) is also provided for building a jar with Maven later (`<dependencies>` is intentionally empty). **Tests do not depend on Maven.**

## Quick start

```java
import cloud.cniia.openapi.sdk.*;
import java.util.*;

Config config = Config.builder()
        .environment(Environment.SANDBOX)          // or Environment.PRODUCTION
        .merchantNo("M00000001")
        .apiKey("ak_demo_key")
        .apiSecretPay("sk_pay_xxx")                // for pay-type endpoints / collection callbacks
        .apiSecretPayout("sk_payout_xxx")          // for payout-type endpoints / payout callbacks
        .timeout(java.time.Duration.ofSeconds(30)) // optional, defaults to 30s
        .build();

Client client = new Client(config);

Map<String, Object> params = new LinkedHashMap<>();
params.put("out_order_no", "202501010001");
params.put("amount", 10000L);                      // smallest-unit integer: 10000 = 1.00
params.put("currency", "PHP");
params.put("pay_method", "gcash");
params.put("country", "PH");
params.put("notify_url", "https://merchant.example.com/api/notify/pay");

ApiResponse resp = client.payCreate(params);       // automatically injects timestamp/nonce and signs with the pay secret
System.out.println(resp.dataAsMap().get("order_no"));
```

### All 11 endpoints (client methods)

| Business | Method | Endpoint | Secret |
|------|------|------|------|
| Create collection | `payCreate` | `/merchant/pay/create` | pay |
| Query collection | `payQuery` | `/merchant/pay/query` | pay |
| Payment methods | `payMethodsQuery` | `/merchant/pay-methods/query` | pay |
| Balance | `balanceQuery` | `/merchant/balance/query` | pay |
| Complete collection test | `payTestComplete` | `/merchant/pay/test/complete` | pay (test secret only) |
| Create payout | `payoutCreate` | `/merchant/payout/create` | payout |
| Query payout | `payoutQuery` | `/merchant/payout/query` | payout |
| Payout banks | `payoutBanksQuery` | `/merchant/payout/banks/query` | payout |
| Payout proof | `payoutProofQuery` | `/merchant/payout/proof/query` | payout |
| Payout receipt | `payoutReceiptQuery` | `/merchant/payout/receipt/query` | payout |
| Complete payout test | `payoutTestComplete` | `/merchant/payout/test/complete` | payout (test secret only) |

Each method accepts a `Map<String, Object>` of business fields:

- **Use integers for amounts** (`Long`/`Integer`), `10000 = 1.00`, to avoid cross-language floating-point differences.
- **Fields whose value is `null` are not sent and do not participate in signing.**
- The common fields `merchant_no` / `api_key` / `timestamp` (Unix seconds) / `nonce` (unique per request) are **injected automatically** by the SDK.
- The `inline` field of `receipt/query` accepts a boolean; the SDK automatically normalizes it to the **integer 1/0** before signing and sending.
- Nested `extra` (such as `customer`) accepts a `Map`/`List`, which participates in signing using stable JSON serialization.

### Error handling

- `code != 0` → throws `ApiException` (carrying `code()` / `message` / `data()` / `rawBody()`).
- HTTP/network errors, non-2xx responses, non-JSON response bodies → throws `TransportException` (carrying `statusCode()` / `rawBody()`).
- To inspect the code yourself without throwing a business exception: use `client.callRaw(path, params, isPayout)` to obtain an `ApiResponse` (containing `code()` / `data()` / `rawBody()`).

### Dual environments (sandbox/production) and custom base URL

```java
// PRODUCTION has no built-in base URL: the production address is derived from your upstream agent's dedicated domain, so baseUrl must be passed explicitly
Config.builder()
      .environment(Environment.PRODUCTION)
      .baseUrl("https://api.<agent_domain>/api/open/v1")        // required, otherwise build() throws
      .merchantNo("M00000001").apiKey("ak").apiSecretPay("...")
      .build();

// SANDBOX uses the built-in local address
Config.builder().environment(Environment.SANDBOX) ...           // http://127.0.0.1:3090/api/open/v1

// Custom base URL override: takes priority over environment; a trailing slash is stripped
Config.builder()
      .baseUrl("https://api.<agent_domain>/api/open/v1")
      .merchantNo("M00000001").apiKey("ak").apiSecretPay("...")
      .build();
```

> When you choose `PRODUCTION` without passing `baseUrl`, `build()` throws `IllegalArgumentException`
> (`baseUrl is required: production base URL is provided per your agent domain ...`).

### Callback signature verification (verify + handle snippet, not a long-running service)

```java
// In your HTTP handler: rawBody is the raw request body POSTed by the platform
Map<String, Object> payload = Json.parseObject(rawBody);

// Timing-safe signature verification: collection/refund uses the pay secret, payout uses the payout secret (the SDK picks the secret automatically)
if (!client.verifyPayCallback(payload)) {       // collection/refund callback
    return /* not a success body or not 2xx, let the platform retry */;
}
String status = String.valueOf(payload.get("status"));
if ("success".equals(status)) {
    // Idempotent crediting (the same order may be called back multiple times)
} else if ("failed".equals(status)) {
    // Idempotently mark as failed
}
return "success"; // HTTP 200 + plain text success
```

Payout callbacks work the same way; use `client.verifyPayoutCallback(payload)` instead. The verifier **relies only on the general rule that "all fields except sign participate"**, does not hardcode a field table, and remains compatible when the platform adds or removes fields. For a complete runnable example, see [`examples/CallbackVerifyExample.java`](./examples/CallbackVerifyExample.java) (demonstrates collection + payout once each, including a tampering counter-example).

## examples/

| File | Content |
|------|------|
| `PayCreateExample.java` | Create collection (with a nested extra object) |
| `PayoutCreateExample.java` | Create payout (query the bank code first, then place the order) |
| `CallbackVerifyExample.java` | Callback signature verification + idempotent handling + response (collection + payout, including a tampering counter-example) |
| `QueryExample.java` | Query order / balance / payment methods / receipt (inline demo) |

Run any example (from the `java/` directory):

```bash
javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name '*.java') examples/CallbackVerifyExample.java
java -Dfile.encoding=UTF-8 -cp out CallbackVerifyExample
```

## How to run tests

```bash
cd java
bash run-tests.sh
```

`run-tests.sh` uses `javac` to compile `src/main/java` + `tests` into `out/`, then uses `java` to run the assertion runner `VectorTest`:

- **(a) Signature test vectors**: reads `../test-vectors.json` and, for each vector, asserts `buildSignBase == base` and `sign == sign`.
- **(b) Callback signature verification**: collection + payout positive cases pass verification; tampered field / tampered sign / missing sign / wrong secret are all counter-examples.
- Additional assertions: JSON integers parse as `Long` (not double), string-escaping edge cases, boolean coercion to `true/false`, `inline` normalization, environment base URL and custom override, and `null` filtering.

If any assertion fails → the runner exits non-zero → the script exits non-zero (can be wired directly into CI).

## Implementation notes (signature consistency)

- **Integers do not degrade to floats**: the JSON parser parses integer tokens into `Long`/`BigInteger`, ensuring the `String` form is `"10000"` rather than `"10000.0"` / `"1e+12"`.
- **String escaping aligns with JS `JSON.stringify`**: nested strings escape only `" \ \b\f\n\r\t` and the remaining `U+0000–U+001F` control characters (lowercase four-digit hex); they do **not** escape `/`, non-ASCII characters, or `<>&`. Top-level scalars are converted to strings directly, without quotes.
- **HMAC**: `HmacSHA256`, UTF-8 encoding, lowercase hexadecimal output; the HMAC key and the trailing `&secret=` in the base are the same secret.
- **Timing-safe callback verification**: `MessageDigest.isEqual` compares the expected sign with the callback sign.
