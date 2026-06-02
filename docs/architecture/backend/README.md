# architecture/backend/

Cross-feature **backend (Spring Boot)** architecture for `wecms-api`: module / package boundaries,
request flow, the JWT auth + tenant-isolation model, the global error model, i18n strategy, and the
shared cross-cutting services (View Setting, Excel export, common sort).

## What goes here

- Spring Boot package / module boundaries (`config`, `common`, `domain/*`)
- Request flow (filter chain → controller → service → repository → entity)
- Auth / tenant architecture (JWT lifecycle, `TenantContext`, `BIZ_KEY` isolation)
- Global error model + the `{ status, data, timestamp?, path?, trace_id? }` envelope
- Cross-cutting shared features (View Setting, Excel export, common-sort whitelist)
- Backend ADRs

## What does NOT go here

- Per-feature service / repository design → [references/business-rules/](../../references/business-rules/)
- API endpoint contracts → [references/api-spec/](../../references/api-spec/)
- DB schema → [database/](../database/) (this tree) + [references/database/](../../references/database/)
- Integration / external-system wiring → [../integrations/](../integrations/)

The canonical, always-current package map + coding rules live in the project guide
[wecms-api/CLAUDE.md](../../../CLAUDE.md) — this folder holds the narrative architecture docs that
expand on it.
