# OpenSSF Scorecard Remediation Plan

## 1. Baseline

Source: [OpenSSF Scorecard Viewer](https://scorecard.dev/viewer/?uri=github.com/bebebus/SDK), with the [public API result](https://api.scorecard.dev/projects/github.com/bebebus/SDK) generated at **2026-07-11 07:23 UTC**. The scan covers commit `b23e6008cd7de5a6581be2a2984154e7f65a755d` and uses Scorecard `v5.3.0`.

The current score is **3.2/10**.

Scorecard measures open-source supply-chain security practices. It is not a manual code audit, penetration test, or compliance certification. Completion must be verified by a new Scorecard run.

## 2. Findings

### Passing or observational items

Binary-Artifacts, Dangerous-Workflow, Vulnerabilities, and License currently score 10. Maintained is 0 because the repository is less than 90 days old; this is time-based. Contributors is 0 because there are no contributing organizations; it is not a code defect and should not be artificially changed.

### Repository changes required

| Priority | Check | Current | Finding | Target |
| --- | --- | ---: | --- | --- |
| P0 | Pinned-Dependencies | 3 | `ci.yml` uses unpinned action tags. | Pin every `uses:` entry to a full 40-character commit SHA with a version comment. |
| P0 | Token-Permissions | 0 | `ci.yml` has no top-level `permissions`. | Use read-only top-level permissions and only job-level permissions where needed. |
| P0 | Security-Policy | 0 | No root `SECURITY.md`. | Publish reporting, supported versions, response, and disclosure rules. |
| P0 | Dependency-Update-Tool | 0 | No Dependabot or Renovate configuration. | Automate updates for Actions and all language manifests. |
| P1 | SAST | 0 | No static-analysis tool detected. | Add CodeQL for JavaScript, Python, Java/Kotlin, and Go; evaluate Semgrep/PHPStan for PHP. |
| P1 | Packaging | -1 | No publishing workflow detected. | Put test, package, publish, and failure gates in GitHub Actions. |
| P1 | Signed-Releases | -1 | No recognizable signed releases. | Use protected tags, GitHub Releases, attestations, and ecosystem provenance. |
| P1 | CII-Best-Practices | 0 | No OpenSSF Best Practices Badge. | Apply after the governance baseline is complete. |
| P2 | Fuzzing | 0 | No fuzz integration detected. | Add Go fuzz tests for signing and parsing boundaries. |

### GitHub settings and collaboration evidence

Code-Review is 0 because the report found no approved changesets; CI-Tests is -1 because there was no auditable merged PR sample; Branch-Protection is 0 because `main` is not protected. Keep the existing `pull_request` CI, configure a `main` ruleset, require approval and all language checks, and use real PRs for future changes. These items cannot be solved by repository files alone.

## 3. Implementation phases

### Phase 1: Supply-chain baseline (P0)

1. Pin every Action in `.github/workflows/ci.yml` to a full commit SHA and retain a readable release comment.
2. Add `permissions: contents: read` to `ci.yml`; test jobs must not receive write permissions.
3. Add `.github/dependabot.yml` for `github-actions`, npm `/nodejs`, pip `/python`, Composer `/php`, Maven `/java`, and Go modules `/go`.
4. Add `SECURITY.md` with GitHub Security Advisories as the preferred reporting channel, supported versions, response targets, disclosure policy, and the boundary that Scorecard is not a manual audit.

Acceptance: no tag-only Actions remain in `ci.yml`; the five-language CI passes; the next Scorecard result no longer reports these four P0 causes.

### Phase 2: Static analysis and release trust (P1)

1. Add a pinned CodeQL workflow for JavaScript, Python, Java/Kotlin, and Go. Use only the minimum permissions (`contents: read`, `actions: read`, and `security-events: write`). Evaluate a separate PHP analyzer if PHP coverage is required.
2. Add a release workflow that runs the same five-language tests as `release.sh`, then publishes npm, PyPI, PHP, and Go artifacts. Prefer npm/PyPI Trusted Publishing/OIDC instead of long-lived repository tokens.
3. Preserve the PHP topology: publish the subtree to `bebebus/merchant-openapi-sdk-php`, then verify Packagist `bebebus/merchant-openapi-sdk` synchronization.
4. Keep Java source-only unless Maven coordinates, build, compatibility, and publication commitments are explicitly added.
5. Use protected release tags, GitHub artifact attestations or Sigstore, and ecosystem provenance where supported.

Acceptance: Scorecard detects a publishing workflow and a public Release; failed tests publish nothing; package registries, GitHub Release, and source tags agree; attestations or signatures can be independently verified.

### Phase 3: Governance and longer-term items (P2)

Configure a GitHub Ruleset for `main`: PR-only changes, at least one maintainer approval, required five-language checks and CodeQL/Scorecard checks once enabled, no force-push or deletion, and administrator enforcement. Apply the OpenSSF Best Practices Badge after the baseline is complete. Add bounded Go `Fuzz*` tests for signature parsing, normalization, and order/amount boundaries.

## 4. Scope and release policy

- Governance files, workflows, and tests alone do not require npm, PyPI, Go, or Java package releases; push to `main` and wait for Scorecard.
- Changes to package contents or release artifacts require the existing multi-ecosystem versioning flow; npm and PyPI versions cannot be overwritten in place.
- PHP still requires synchronization to `bebebus/merchant-openapi-sdk-php` before verifying Packagist.
- Scorecard score is not the sole security conclusion; also verify CI, package contents, vulnerabilities, signing evidence, and the vulnerability-reporting channel.

## 5. Definition of done

The remediation is complete when P0 changes pass CI, the `main` Ruleset is active, SAST and release/signing workflows each have a successful run, PHP-to-Packagist synchronization is verified once, and a new Scorecard report confirms that the original findings have disappeared. Time- or organization-dependent observations such as Maintained and Contributors should be documented rather than artificially manipulated.
