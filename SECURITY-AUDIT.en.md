# Third-Party Security Verification

This page records publicly verifiable third-party security sources for the SDK.

> Important scope boundary: OpenSSF Scorecard is a continuously run automated open-source supply-chain security assessment. It is not a manual code audit, penetration test, or compliance certification issued by an independent security firm. Keep this limitation in any external security statement.

## OpenSSF Scorecard

The SDK includes an OpenSSF Scorecard workflow for the `bebebus/SDK` repository. Results are published to the Scorecard public service and uploaded to GitHub Code Scanning.

- Latest result: [OpenSSF Scorecard report](https://scorecard.dev/viewer/?uri=github.com/bebebus/SDK)
- Public result API: [Scorecard API](https://api.scorecard.dev/projects/github.com/bebebus/SDK)
- Repository workflow: [`.github/workflows/scorecard-analysis.yml`](./.github/workflows/scorecard-analysis.yml)
- Check definitions: [Scorecard checks](https://github.com/ossf/scorecard/blob/main/docs/checks.md)
- Source project: [OpenSSF Scorecard](https://github.com/ossf/scorecard)

The report is updated by GitHub Actions runs on `main`. The current verified baseline (2026-08-22 07:06:24 UTC) is **8.7/10**, covering commit `59c4135` with Scorecard `v5.5.0`. This is a timestamped snapshot; use the public result API for the latest state. See [`SECURITY-REMEDIATION-PLAN.en.md`](./SECURITY-REMEDIATION-PLAN.en.md) for the remaining deductions and remediation boundaries.

Security-Policy, Code-Review, Dependency-Update-Tool, Token-Permissions, Signed-Releases, Vulnerabilities, Packaging, Fuzzing, SAST, License, and CI-Tests are among the checks currently scoring 10. Checks that are not yet at full score include Pinned-Dependencies (8), Branch-Protection (8), CII-Best-Practices (5), and the repository-age or contributor-organization observations Maintained (0) and Contributors (0).

## Dependency and Supply-Chain Information

Dependency graphs, known vulnerabilities, licenses, and package metadata can be reviewed through Google's Open Source Insights (`deps.dev`):

- [Open Source Insights](https://deps.dev/)
- [Open Source Insights FAQ](https://docs.deps.dev/faq/)

The SDK declares no runtime dependencies. This source is mainly useful for reviewing published package metadata, package origins, and transitive risk if dependencies are added in the future.

## Current Verification Status

| Item | Status |
| --- | --- |
| OpenSSF Scorecard workflow | Configured; the 2026-08-22 baseline is 8.7/10 for commit `59c4135` |
| CI / CodeQL | [CI](https://github.com/bebebus/SDK/actions/runs/32558710542) and [CodeQL](https://github.com/bebebus/SDK/actions/runs/32558710548) both passed for `59c4135` |
| GitHub Code Scanning | [Five alerts were open](https://github.com/bebebus/SDK/security/code-scanning?query=is%3Aopen) on 2026-08-25; all were medium OpenSSF Pinned-Dependencies findings for npm installation commands in workflows |
| Dependabot security alerts | [Zero alerts were open](https://github.com/bebebus/SDK/security/dependabot?query=is%3Aopen) when verified on 2026-08-25 |
| Dependency supply-chain review | Publicly reviewable through deps.dev |
| Independent manual code audit / penetration test | Not commissioned; not claimed as completed |

Report security issues through [GitHub Security Advisories](https://github.com/bebebus/SDK/security/advisories). Do not disclose exploitable details in public Issues.
