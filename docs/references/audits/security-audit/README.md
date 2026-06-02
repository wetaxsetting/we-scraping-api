# audits/security-audit/

Security-focused audits for the refactor stack.

## Purpose

Capture findings from internal security reviews: authentication / session model, tenant isolation (biz_key enforcement), audit-log integrity, secret handling, dependency CVE posture.

## What goes here

- Per-review markdown (`<scope>-security-audit-<yyyy-mm-dd>.md`)
- Findings with severity (critical / high / medium / low / info)
- Cross-link to the security baseline ([`../../../_shared/security-baseline.md`](../../../../../docs/_shared/security-baseline.md))

## What does NOT go here

- Penetration test reports from external parties (those belong under [`../../../security/`](../../../../../docs/security/) with restricted access if applicable)
- JEX-side security issues (those belong under `docs/jex/`)
