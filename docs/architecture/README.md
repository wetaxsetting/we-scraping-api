# architecture/

System-level architecture for the Spring Boot + Next.js refactor.

## Purpose

Cross-feature architecture decisions for the new system: module boundaries, request flow, deployment
topology, integration patterns, security architecture, observability strategy.

## Subfolders

| Folder | Scope |
|---|---|
| [backend/](backend/) | Spring Boot module/package boundaries, request flow, auth + tenant model, error model, i18n, shared cross-cutting services |
| [security/](security/) | Security architecture — auth, API, audit logging (cross-system pointers + backend security-design links) |
| [integrations/](integrations/) | External-system wiring — bank scraping / firm-banking gateway, FX feed, NXiSAS, GDT credential path |
| [database/](database/) | Schema-wide architecture — parity stance, Flyway-only channel, DB-function migration strategy ([db-function-strategy.md](database/db-function-strategy.md)) |

## What goes here

- High-level system diagrams (Spring Boot service graph, Next.js → API → Postgres flow)
- Module / package boundaries for `wecms-api` and `wecms-web`
- Authentication / authorization architecture (JWT lifecycle, tenant isolation)
- Cross-cutting concerns: logging, error model, i18n strategy, caching, validation
- Deployment topology (local R&D — no DEV/UAT/PROD)
- ADRs (architecture decision records)

## What does NOT go here

- Per-feature design → [../features/](../features/)
- Per-feature service / repository design → [../references/business-rules/](../references/business-rules/)
- Individual API contracts → [../references/api-spec/](../references/api-spec/)
- Per-table DB schema → [../references/database/](../references/database/)
- JEX system architecture — read-only reference at [docs/jex/architecture/](../../../docs/jex/architecture/)

## Reference

JEX is **read-only reference**. When an architecture decision maps to a JEX behavior, link to the
relevant doc under [docs/jex/architecture/](../../../docs/jex/architecture/) instead of duplicating it.
