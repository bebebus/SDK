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

The first result requires one successful GitHub Actions run on `main`; until then, the report link may not contain a result.

## Dependency and Supply-Chain Information

Dependency graphs, known vulnerabilities, licenses, and package metadata can be reviewed through Google's Open Source Insights (`deps.dev`):

- [Open Source Insights](https://deps.dev/)
- [Open Source Insights FAQ](https://docs.deps.dev/faq/)

The SDK declares no runtime dependencies. This source is mainly useful for reviewing published package metadata, package origins, and transitive risk if dependencies are added in the future.

## Current Verification Status

| Item | Status |
| --- | --- |
| OpenSSF Scorecard workflow | Configured; first public result pending a GitHub Actions run |
| GitHub Code Scanning | SARIF uploaded by the Scorecard workflow |
| Dependency supply-chain review | Publicly reviewable through deps.dev |
| Independent manual code audit / penetration test | Not commissioned; not claimed as completed |

Report security issues through [GitHub Security Advisories](https://github.com/bebebus/SDK/security/advisories). Do not disclose exploitable details in public Issues.
