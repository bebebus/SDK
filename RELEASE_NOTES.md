# Release Notes

This file summarizes user-visible changes for each SDK release. It is maintained alongside the GitHub Releases page and is not a raw Git history export.

## v2.0.0 (2026-08-18) — OpenAPI v2 fully withdrawn

Server contract (decided 2026-08-18): OpenAPI v2 has been physically removed from the server — the entire `/api/open/v2` base now returns 404 for every route, not only `payout/*`. There is no v2 base left to fall back from, so this release removes the now-dead v2 request-binding machinery from all five SDKs and returns every default/preset base URL to `/api/open/v1`. Only 11 of the 14 shared signing vectors (`test-vectors.json`) survive — the 3 `v2_binding_*` vectors are removed; the remaining 11 v1 vectors are byte-for-byte unchanged.

**Breaking changes (all five languages)**:
- **Request binding removed.** The `METHOD\npath\n` v2 signing prefix and its supporting API are gone:
  - Node.js: `buildSignBase(payload, secret)` / `sign(payload, secret)` no longer accept a trailing `binding` argument; the exported `SignBinding` TypeScript type is removed.
  - Python: `build_sign_base(payload, secret)` / `sign(payload, secret)` no longer accept a `binding` argument.
  - PHP: `Signer::buildSignBase()` / `Signer::sign()` no longer accept a `$binding` argument; the public `Signer::bindingPrefix()` method is removed.
  - Go: `BuildSignBaseWithBinding` / `SignWithBinding` and the exported `SignBinding` struct are removed; use the 2-argument `BuildSignBase(payload, secret)` / `Sign(payload, secret)` (now body-only, matching pre-1.2.0 behavior).
  - Java: `SignBinding.java` (public class) is deleted; the 3-argument `Signer.buildSignBase(...)` / `Signer.sign(...)` overloads are removed — use the 2-argument versions.
- **Payout v1<->v2 auto-fallback removed.** Since v2 no longer exists at all, the automatic base-URL rewrite (`/api/open/v2` → `/api/open/v1`) that payout methods performed under a v2 baseUrl has been deleted along with its helpers (`requestBinding`/`isPayoutPath`/`resolveRequestBase` and language equivalents). All requests now go straight to whatever `baseUrl` is configured — which must be a v1 base.
- **SANDBOX preset changed.** `Environment.SANDBOX` (and the `PRODUCTION`-without-`baseUrl` error hint) now points at `http://127.0.0.1:3090/api/open/v1` in every language, not `/api/open/v2`.
- **Breaking (Python only)**: `pay_create` drops the `channel_code` keyword argument (it never existed in the v1 server contract); `pay_method` is now required and a missing `pay_method` raises `TypeError`, mirroring the existing `payout_create` v1 contract. Callers passing `channel_code=` will get a `TypeError: pay_create() got an unexpected keyword argument 'channel_code'` and must switch to `pay_method`.
- **Docs (SIGNING/INTERFACES/README, zh/en)**: the v2 request-binding section, the "payout exception" callout, and every `channel_code` / `pay_methods` + `channel_codes` mention describing v2-only response shapes are removed. `pay/create` and `pay-methods/query` are documented as v1-only: `pay_method` required, `data.methods[]` response.
- Version bumped to `2.0.0` across all five SDKs (`package.json`, `pyproject.toml`, `Client::VERSION`/`Version`/`VERSION` constants, and the corresponding source-run version fallbacks).

Non-breaking: callback verification, all other request/response fields, error codes, and the underlying canonical-body signing algorithm (filter/sort/stringify/HMAC) are unchanged — only the v2 binding prefix that used to be prepended to it is gone.

## v1.3.0 (2026-08-15) — Payout fully withdrawn from v2

- Appendix config files you can view or download for all five languages: client config templates and country/currency catalogs under `<language>/appendix/` (Node.js/PHP/Python/Go/Java). Download URLs are listed in the root README and on the public docs site.

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
