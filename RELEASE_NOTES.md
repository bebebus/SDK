# Release Notes

This file summarizes user-visible changes for each SDK release. It is maintained alongside the GitHub Releases page and is not a raw Git history export.

## v1.3.0 (unreleased) — Payout fully withdrawn from v2

- Appendix config files you can view or download: Node.js JSON and PHP array templates under `nodejs/appendix/` and `php/appendix/` (client config + country/currency catalog). Download URLs are listed in the root README and on the public docs site.

- Server contract (decided 2026-08-15): payout endpoints are registered only on the v1 base. On the v2 base every `payout/*` route — `payout/create`, `payout/query`, `payout/test/complete`, `payout/banks/query`, `payout/proof/query`, `payout/receipt/query`, and the receipt file — returns 404 (server v2 version 2.2.0).
- All five SDKs: when the configured baseUrl is a v2 base, payout methods automatically fall back to the corresponding v1 base (`/api/open/v2` → `/api/open/v1`) and sign body-only (no v2 METHOD+path binding prefix). Collection (pay) behavior on v2 is unchanged.
- **Breaking (Python)**: `payout_create` no longer accepts the `channel_code` parameter (removed from the signature and the request body); `pay_method` is now required and a missing `pay_method` raises `TypeError`. Callers passing `channel_code` will get a `TypeError` and must switch to `pay_method`.
- Docs (INTERFACES/SIGNING/README, zh/en): payout contract restored to v1 semantics — `pay_method` required, `channel_code`/`group_code` not accepted; the `channel_codes` dictionary from `pay-methods/query` lists collection groups only.
- Signing vectors are unchanged; payout vectors were already pure v1.

## v1.2.1 — Shared v2 signing vectors and release hygiene

- `test-vectors.json` now carries three v2 binding vectors (minimal anchor with lowercase-method normalization, real collection payload, nested `extra`), cross-verified byte-for-byte against the server implementation; all five language test harnesses consume the `binding` field.
- SIGNING.md (zh/en): explicit uppercase-method normalization rule; the shared vector file is again the single source of truth including v2.
- PUBLISHING.md: releases must be tagged on `main` (postmortem of the orphaned v1.2.0 tags after a squash merge).
- Source-run version fallbacks in Node.js/Python aligned; README Go install example points at the current tag.
- No API endpoints, request parameters, signing algorithms, or business processing behavior changed. Installed 1.2.0 packages sign identically; this release aligns repository assets and metadata.

## v1.2.0 — OpenAPI v2 group-code ordering

- Default sandbox Base URL is now `http://127.0.0.1:3090/api/open/v2`.
- Collection and payout create accept `channel_code` (recommended on v2) or `pay_method`. Existing `pay_method` callers stay valid.
- `payMethodsQuery` on v2 returns two arrays: `pay_methods` (payment method + description) and `channel_codes` (group code + description). Obtain `channel_code` from the operations team.
- v2 signing prepends `POST\n` + canonical path + `\n` to the existing canonical body (OpenAPI 2.1.0). v1 and callbacks stay body-only.
- `groupsQuery` remains as a compatibility alias of `/merchant/groups/query`; new integrations should read `channel_codes` from `payMethodsQuery`.
- Examples and interface docs use v2 + `channel_code`.
- Upgrade impact: callers that relied on the sandbox path `/api/open/v1` should either keep passing an explicit v1 `baseUrl`, or move to v2. v1 `payMethodsQuery` still returns `data.methods`.

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
