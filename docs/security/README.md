# wecms-api — Security Documentation Index

Security docs for the WeCMS refactor backend. Start here.

**Current status (2026-06-24):** Spring security deep review **closed** for F-01–F-07 + F-10; **Conditionally Ready** for SIT/UAT. Remaining items (F-08/F-09/F-11, in-memory durability, secret rotation) are tracked in the risk register. See [`../../CLAUDE.md`](../../CLAUDE.md) → "Security & SIT Readiness Status".

## Documents

| Doc | Purpose |
|---|---|
| [Spring Security Deep Review](../../../docs/security/spring-security-deep-review.md) | Original adversarial review (2026-06-24, workspace `docs/security/`) + Remediation-Status banner. Findings-of-record. |
| [Security Remediation Closure — 2026-06-24](security-remediation-closure-2026-06-24.md) | Closure-of-record: scope, findings closed, files changed, verification, test/build results, UAT/PROD verdict. |
| [Remaining Security Risk Register](remaining-security-risk-register.md) | Open items (F-08/F-09/F-11, in-mem durability, rotation) with owner, env scope, UAT/PROD impact, decision, timeline. |
| [Ops Secret Rotation Handoff](ops-secret-rotation-handoff.md) | Which secrets leaked, what code enforces vs. cannot, Ops rotation responsibilities + verification. |
| [Required Environment Secrets](required-environment-secrets.md) | Authoritative env-var list for `sit`/`uat`/`prod` (fail-fast; no committed defaults). |
| [SIT Security Deployment Checklist](../deployment/sit-security-deployment-checklist.md) | Run-book for deploying the remediation to SIT (pre-deploy → smoke → headers → logging → rollback → post-deploy). |

## Reading order

1. **Reviewing the security work?** → Closure report, then the deep review for the original findings.
2. **Deploying to SIT?** → Required Environment Secrets + Ops Rotation Handoff, then the SIT Checklist.
3. **Planning UAT/PROD?** → Remaining Risk Register.
