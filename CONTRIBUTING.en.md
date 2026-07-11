# Contributing

Thank you for contributing to the Merchant Payment OpenAPI SDK. The project welcomes issue discussions and improvement proposals, as well as code, test, documentation, and workflow changes through pull requests.

## Before submitting

- Do not commit production secrets, real merchant data, access tokens, or other credentials. Follow [`SECURITY.md`](./SECURITY.md) for private vulnerability reports.
- Confirm the scope and compatibility impact of a change. Cross-language behavior changes must be checked against all five SDKs.
- Add or update tests with every feature change. Changes to signing behavior must update `test-vectors.json` and pass the vectors in every language.
- Document how documentation, test, and workflow changes were verified.

## Development workflow

1. Create a topic branch from the latest `main` and work on that branch.
2. Use concise commit messages with prefixes such as `feat`, `fix`, `docs`, `test`, or `chore`; this repository's default commit language is Simplified Chinese. Keep each commit focused.
3. Open a pull request describing the problem, solution, impact, and verification. Do not push directly to `main`.
4. Pull requests must pass the Node.js, Python, PHP, Java, and Go CI checks plus CodeQL, and receive approval from at least one maintainer.
5. Address review feedback on the branch. Before merging, ensure the latest commit passes all required checks and use one of the repository's allowed linear-history merge methods.

## Local verification

The SDK has no runtime third-party dependencies. Before opening a pull request, run at least:

```bash
cd nodejs && node --test && cd ..
cd python && python3 -m unittest discover -s tests && cd ..
cd php && php tests/run.php && cd ..
cd java && bash run-tests.sh && cd ..
cd go && go test -count=1 ./... && cd ..
```

Run the Go callback verification fuzz target separately when changing parsing or signing boundaries:

```bash
cd go
go test -fuzz=FuzzVerifyCallbackNeverPanics -fuzztime=30s .
```

GitHub Actions dependencies must be pinned to full commit SHAs. Run `actionlint` after changing `.github/workflows/` where possible.

## Releases

See [`PUBLISHING.md`](./PUBLISHING.md) for versioning and registry publication. Public package versions cannot be overwritten; run the tests, verify cross-language version consistency, and check npm, PyPI, Packagist, Go, and GitHub Release synchronization before publishing.

## Conduct and security boundaries

Keep discussions focused on code, documentation, and reproducible problems. Do not harass others or publish sensitive information. OpenSSF Scorecard is an automated supply-chain assessment, not a manual code audit or penetration test. See [`SECURITY-AUDIT.en.md`](./SECURITY-AUDIT.en.md) for public verification sources and remediation status.
