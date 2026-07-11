# Release Notes

This file summarizes user-visible changes for each SDK release. It is maintained alongside the GitHub Releases page and is not a raw Git history export.

## v1.1.2 — Documentation and interface declaration updates

- Reworked Chinese and English OpenAPI documentation from a merchant developer perspective.
- Clarified the documented behavior and usage of order, payout, refund, callback, payment-method, bank-code, and proof fields.
- Synchronized README files, interface contracts, signing guidance, examples, and type comments across Node.js, Python, PHP, Go, and Java.
- No API endpoints, request parameters, signing algorithms, or business processing behavior changed.
- Upgrade impact: documentation and type-description update; existing valid SDK calls remain compatible.

## v1.1.1 — Type declarations and defense-in-depth hardening

- Added complete TypeScript declarations for the Node.js SDK.
- Added five-language GitHub Actions test-vector CI.
- Hardened Go nonce generation, Node.js callback signature validation, and Python per-operation secret handling.
- Unified SDK version and User-Agent sources and added missing interface error codes.
- Upgrade impact: backward compatible; the 11 standard signing vectors are unchanged byte-for-byte.

## v1.1.0 — Fail-closed callback verification hardening

- Rejects empty or invalid callback secrets and malformed signatures before HMAC verification.
- Makes callback verification fail closed for malformed input instead of allowing exceptions or empty-key verification paths.
- Correctly signs concrete Go containers such as `map[string]string`, `[]string`, and structs.
- Rejects unsafe numeric inputs and enforces HTTPS/transport hardening where applicable.
- Adds PHP raw callback verification that preserves large integer values.
- Upgrade impact: valid integer payloads and non-empty secrets remain compatible; previously tolerated invalid inputs now fail explicitly.

## v1.0.0 — Initial public release

- Published the PHP, Python, Java, Go, and Node.js SDKs for Merchant Payment OpenAPI.
- Covers collection, payout, callback verification, dual environments, and all 11 signed business endpoints.
- Provides byte-for-byte cross-language HMAC-SHA256 signing vectors and zero runtime third-party dependencies.

## Vulnerability disclosure note

No release listed above was created to remediate a publicly assigned CVE or other public runtime vulnerability identifier. Security hardening changes without a CVE are described in the relevant release section and in [`SECURITY-AUDIT.md`](./SECURITY-AUDIT.md).
