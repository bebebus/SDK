> [中文](./SIGNING.md) | English

# Signing Specification (single source of truth for the SDKs in all languages)

Both requests and callbacks of the merchant OpenAPI are signed with **HMAC-SHA256**, output as **lowercase hexadecimal**. The algorithm defined in this file is **byte-for-byte identical** to the server's signing implementation; the signers of all five SDKs must reproduce the `base` and `sign` in [`test-vectors.json`](./test-vectors.json), otherwise they are considered non-compliant.

> Signing is the only part of the entire SDK where "not a single byte may be wrong". Most cross-language failures come from two places: **nested JSON serialization** and **scalar-to-string coercion**; be sure to check the "cross-language pitfalls" below item by item.

## 1. Algorithm Steps

Input: the key-value table `payload` made up of business fields (including common fields such as `merchant_no/api_key/timestamp`, but **excluding** `sign`), and the key `secret`.

1. **Filter**: drop the field whose key is `sign`; drop fields whose value is `null`/`undefined` (the corresponding `None`/`nil`/`null` in each language).
2. **Sort**: sort the remaining fields by **key name in ascending ASCII (code point) order** (equivalent to JS `Array.prototype.sort()` default behavior, Go `sort.Strings`, Python `sorted()`, Java `String.compareTo`).
3. **Value string** `valueForSign(v)`:
   - `v` is an **object / array** → use the "stable JSON serialization" `stableStringify(v)` below.
   - `v` is a **scalar** (string/number/boolean) → take its **raw string form** (see "scalar rules" below), **without quotes**.
4. **Concatenate base**: join each field as `key=value` with `&`, and **append** `&secret=<secret>` at the end:
   ```
   k1=v1&k2=v2&...&kN=vN&secret=<secret>
   ```
5. **Compute sign**: `HMAC_SHA256(base, key=secret)`, output as a **lowercase hexadecimal** string.
6. **Key selection**: pay-type endpoints and collection callbacks use `api_secret_pay`; payout-type endpoints and payout callbacks use `api_secret_payout`.
   (The HMAC key and the `&secret=` at the end of base use the **same** secret.)

Put the computed `sign` back into the request body and send it together.

## 2. Scalar Rules (top level)

Top-level scalars are converted to strings directly, **without quotes**, and must be exactly identical to JS `String(v)`:

| Type | Rule | Example |
|------|------|----|
| string | Output as-is (no escaping, no quotes, no URL encoding) | `订单/支付 <A&B>` → `订单/支付 <A&B>` |
| integer | Decimal signed/unsigned text | `10000` → `10000` |
| boolean | **Must** be `true` / `false` (lowercase) | `true` → `true` |

> ⚠️ Top-level strings containing `&`, `=`, spaces, or Chinese are all kept **as-is** (not escaped). The base may therefore "look ambiguous", but the server constructs it by the same rules, so verification is self-consistent and there is nothing to worry about.

> ⚠️ The API does not use floating-point amounts — all amounts are **minor-unit integers** (`10000 = 1 unit`). Use integer types for SDK amount parameters to avoid any cross-language floating-point formatting differences.

## 3. Stable JSON Serialization (nested object/array)

`stableStringify(value)` recursion rules (must align with JS `JSON.stringify` + ascending key order):

- `null` → `null`
- scalars (string/number/boolean) → `JSON.stringify(v)`:
  - string → **with double quotes**, with JSON escaping (see below)
  - number → numeric literal (`123`, `1.5`), no quotes
  - boolean → `true` / `false`
- array → `[` + each element recursively `stableStringify` joined with `,` + `]` (**preserve element order**)
- object → `{` + after sorting keys in **ascending** order, each pair `JSON.stringify(key) + ":" + stableStringify(value)` joined with `,` + `}` (**compact, no spaces**)

### JSON String Escaping (nested string values, must align with JS `JSON.stringify`)

- Escape: `"` → `\"`, `\` → `\\`, `\b \f \n \r \t` → the corresponding `\b \f \n \r \t`, other U+0000–U+001F control characters → `\u00XX` (lowercase hex, four digits).
- **Do not escape**: `/` (forward slash kept as-is), `<` `>` `&` (no HTML escaping), all non-ASCII (Chinese/emoji, etc. kept as raw UTF-8, **not** turned into `\uXXXX`).

**Counterexample anchor**: the nested string `中文"<>&/\` + newline + tab + `末` must be serialized as
`"中文\"<>&/\\\n\t末"` (Chinese and `/` `<>&` as-is, `"` and `\` escaped, newline and tab turned into `\n` `\t`).
Corresponds to vector `unicode_and_special_chars`.

## 4. Cross-Language Pitfalls (per-language landing points)

| Language | Nested JSON serialization (must configure) | Top-level scalar coercion (must special-case) |
|------|------------------------------|--------------------------|
| **Node.js** | `JSON.stringify` + self-written ascending-key recursion (`JSON.stringify` does not guarantee key order). The default escaping behavior is already correct: does not escape `/`, non-ASCII, or `<>&`. | `String(v)` is correct: `String(true)="true"`, `String(10000)="10000"`. |
| **Python** | `json.dumps(v, ensure_ascii=False, separators=(',',':'), sort_keys=True)`. **Must** use `ensure_ascii=False` (otherwise non-ASCII becomes `\uXXXX`). Python does not escape `/` or `<>&`, consistent with JS. | `str(True)=="True"` ✗ → **special-case** bool→`"true"/"false"` (and because `bool` is a subclass of `int`, check bool before int in order). |
| **PHP** | `json_encode(v, JSON_UNESCAPED_SLASHES \| JSON_UNESCAPED_UNICODE)` + self-written ascending-key sort (PHP associative arrays need recursive `ksort`). The default `json_encode` escapes `/` and non-ASCII, so these two flags are **required**. `<>&` are not escaped by default (unless `JSON_HEX_*` is added, do not add it). | `(string)true=="1"` ✗, `(string)false==""` ✗ → **special-case** bool→`"true"/"false"`. Integer `(string)10000` is correct. |
| **Java** | No built-in JSON: **self-write** `stableStringify` (recursive, ascending keys, compact), with string escaping strictly per the previous section (only escape `" \ \b\f\n\r\t` and other control characters as `\u00XX`, **do not** escape `/`, non-ASCII, or `<>&`). | `String.valueOf(true)=="true"` is correct; use `Long.toString`/`BigInteger.toString` for integers. Be careful to dispatch by value type. |
| **Go** | `encoding/json`: **must** `Encoder.SetEscapeHTML(false)` (the default escapes `<>&` to `<` etc.). Go by default does **not** escape `/` or non-ASCII (consistent with JS). Self-written ascending-key recursion is needed (`json.Marshal` automatically sorts `map` keys in ascending order, but explicit recursion is recommended for control). For numbers coming from JSON, use `json.Number` (`Decoder.UseNumber()`) to avoid `float64` writing large integers as `1e+12`. | `strconv.FormatBool(true)=="true"` is correct; integers `strconv.FormatInt`. `fmt.Sprint(float64)` produces scientific notation for large integers, so **do not** carry amounts as floats. |

> General: **the HMAC and base secret are the same**; output hex in **lowercase**; compute the HMAC after UTF-8 encoding.

## 5. Worked Example

Vector `pay_create_scalars`, `secret = sk_test_0123456789abcdef0123456789abcdef`:

base:
```
amount=10000&api_key=ak_demo_key&country=PH&currency=PHP&merchant_no=M00000001&notify_url=https://merchant.example.com/api/notify/pay&out_order_no=202501010001&pay_method=gcash&timestamp=1736073600&secret=sk_test_0123456789abcdef0123456789abcdef
```
sign:
```
9b65f090a2032f0241ba0587aad36768bed923b7543d711acbad0021d2f60568
```

## 6. Callback Signature Verification (generic, field-agnostic)

The service sends a JSON callback to the merchant's `notify_url`, with `sign` and several business fields in the body. Verification steps:

1. Parse the callback JSON into a key-value table.
2. **Take all top-level fields in the table except `sign`** (do not hard-code the field list — callback fields may be added or removed in future versions; the rule "all fields except sign participate" remains compatible).
3. Compute the expected `sign` using the algorithm in this file (use `api_secret_pay` for collection/refund callbacks, `api_secret_payout` for payout callbacks).
4. Compare with the `sign` in the callback using a **constant-time comparison** (`crypto.timingSafeEqual` / `hmac.compare_digest` / `MessageDigest.isEqual` / `hmac.Equal` / `hash_equals`).
5. On verification failure → reject processing and do not return a success response; the same order may be sent again.

### Merchant Response (determines whether callbacks continue)

An acknowledgement is considered successful when the HTTP status is 2xx and the body matches any of the following:

- Plain text `success` / `ok` (**case-insensitive**, whole-string match after trim)
- JSON `{"success": true}`
- JSON `{"code": 0}`
- JSON `{"message": "success"}` or `{"message":"ok"}` (case-insensitive)
- JSON string `"success"` / `"ok"`

Otherwise it is treated as a failure and retried (exponential backoff, about `1m,2m,5m,10m,30m,60m`, 6 times total). The SDK callback examples uniformly respond HTTP 200 + plain text `success`.
Processing must be **idempotent** (the same order may be called back multiple times).
