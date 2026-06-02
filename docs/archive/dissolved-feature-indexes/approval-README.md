# features/approval/

Feature specs for the **approval-line (결재라인)** domain in the refactor.

## Purpose

Approval covers the approval-line master and detail model: the per-tenant chain of approvers that gates banking / payment / settings actions in WeCMS. JEX implements this via `comm_0222_*` services + `ApprLineInfm` / `ApprLineInfmDetl` entities. The refactor preserves the same business model but ships it as a Spring service + Next.js admin screen.

## Scope

| File | Refactor scope |
|---|---|
| [approval-line.md](../../features/settings/approval-line.md) | Approval-line CRUD, approver-chain ordering, normalisation rules |

## Document standards

- Frontmatter table: `JEX counterpart`, `Refactor status`, `Owner`, `Sprint`, related JEX services (`comm_0222_001` …).
- Cite the live JEX entities and the audit-table changes (`APPR_LINE_INFM`, `APPR_LINE_INFM_DETL`).
- Track Open Questions (OQ) inline with date + decision when resolved.

## Naming convention

- kebab-case slug matching the JEX counterpart under [`docs/jex/features/`](../../../../docs/jex/features/).
- No spaces, no uppercase.

## Related

- Backend service: [`../../backend/approval-line.md`](../../references/business-rules/approval-line.md)
- Frontend screen: [`../../frontend/approval-line.md`](../../../../wecms-web/docs/features/settings/approval-line.md)
- JEX → Spring migration: [`../../migration/approval-line.md`](../../references/migration/approval-line.md)
- Plan: [`../../planning/phase-1/approval-line-plan.md`](../../planning/phase-1/approval-line-plan.md)
