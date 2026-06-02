# references/

Supporting **reference material** for the refactor — the layers that the feature-first docs under
[../features/](../features/) link out to. A feature doc is the spine; everything here is the detail it
points at.

## Reference categories

| Folder | Contents |
|---|---|
| [api-spec/](api-spec/) | REST endpoint / DTO contracts (`*-api.md`), per-domain API specs, [api-mapping.md](api-spec/api-mapping.md) cross-feature index |
| [database/](database/) | Schema design, ERD, Flyway notes, indexing, [table-mapping/](database/table-mapping/), [manual-tenant-setup.md](database/manual-tenant-setup.md), [db-function-classification.md](database/db-function-classification.md) |
| [business-rules/](business-rules/) | Service / domain design — entities, repositories, services, security, settings, per-feature business logic |
| [migration/](migration/) | JEX → Spring service/field mapping, [jex-analysis/](migration/jex-analysis/), screen-mapping, [MIGRATION_PLAYBOOK.md](migration/MIGRATION_PLAYBOOK.md) |
| [audits/](audits/) | Parity audits (refactor vs JEX) — per-feature parity checklists & dated reviews |
| [api/](api/) · [external/](external/) · [figma/](figma/) · [jex/](jex/) | Curated reference-note subfolders (framework cheatsheets, external links, Figma notes, JEX pointers) |

## What goes here

- Endpoint contracts, schema, JEX→Spring mappings, parity audits (the layers above)
- Framework / library reference notes (Spring Boot, Next.js, Tailwind, jjwt, etc.)
- Exploration / spike write-ups; JEX-vs-refactor comparison tables

## What does NOT go here

- Per-feature functional spec → [../features/](../features/)
- Sprint plans → [../planning/](../planning/)
- System architecture → [../architecture/](../architecture/)
- JEX framework reference — cross-cutting, both systems → [docs/_shared/jex-framework.md](../../../docs/_shared/jex-framework.md)

## Reference

JEX is **read-only reference**. Cite JEX docs by relative link rather than duplicating their content here.
