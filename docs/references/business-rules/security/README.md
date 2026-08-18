# business-rules/security/

Security design for `wecms-api`.

## Purpose

Document the Spring Security filter chain, authentication mechanism, session / token model, authorisation strategy (role / biz-key / approval-line), and audit-logging conventions.

## What goes here

- Filter chain composition (`SecurityFilterChain` beans)
- Authentication providers
- Authorisation policy per endpoint group
- Tenant-isolation rules (biz_key enforcement)
- Audit-log envelope (`AUDIT_LOGS_INFM` writes — `WSVC_ID` uses **kebab-case slugs**, not legacy WSVC ids, per workspace CLAUDE.md)

## Related

- API surface: [`../../api-spec/`](../../api-spec/)
- Cross-cutting security baseline: [`../../../security/`](../../../../../docs/security/) and [`../../../_shared/security-baseline.md`](../../../../../docs/_shared/security-baseline.md)
- Permission / role catalogue: [`../../../_shared/permission-roles.md`](../../../../../docs/_shared/permission-roles.md)
