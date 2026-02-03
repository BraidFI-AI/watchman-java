# Security Scanning Setup

## Summary
- Added automated security scanning with Semgrep (static analysis) and Trivy (dependency/container scan) for all commits and pushes.
- Integrated scans into CI (GitHub Actions) and local developer workflow (pre-commit, pre-push hooks).

## Scope
- Applies to all code in watchman-java repo.
- Scans run on every commit, push, and in CI for main branch and PRs.

## Design notes
- `.github/workflows/security-scan.yml`: Runs Semgrep and Trivy on push/PR.
- `.husky/pre-commit`, `.husky/pre-push`: Run Semgrep and Trivy locally before commit/push.
- `scripts/pre-commit-security.sh`, `scripts/pre-push-security.sh`: Script logic for local hooks.
- No changes to application logic or build pipeline.

## How to validate
- Make a commit: Semgrep and Trivy should run, blocking on findings.
- Push to main/PR: GitHub Actions runs both scans, artifacts uploaded.
- Reports: `semgrep-report.json`, `trivy-report.json` in CI artifacts.

## Assumptions and open questions
- Assumes developer has Python/pip and sudo access for local installs.
- Trivy install may require manual approval on some systems.
- Out of scope: Custom Semgrep/Trivy rules, scan suppression, or reporting integration.
