# wecms-api-docs — Documentation Rules

Content conventions for maintaining `wecms-api/docs/`. Read alongside `api-doc-structure.md`.

## Core principles

- **Doc-only.** Never touch application source. Never deploy. This tree is local R&D.
- **In-place by default.** Do not move or rename existing files unless explicitly asked. Reorganization is a separate, confirmed task.
- **Reuse over create.** A new file is the last resort. First try: extend an existing doc, add a section, or cross-link.
- **Preserve content.** Never delete prose, tables, decisions, or open questions without confirmation. Mark superseded content as such rather than removing it.

## Naming

- All new identifier surfaces use **kebab-case slugs**: feature folders, doc filenames, REST URIs, i18n key prefixes, log tags. Example: `firm-banking-settings.md`, not `comm_0220_001.md`.
- Legacy JEX WSVC / menu ids are allowed **only** as:
  1. JEX `file:line` citations that point at the source-of-truth implementation, and
  2. migration-traceability prose (e.g. "migrated from `pymn_0010_002`").
- **Feature slugs must match across systems** (JEX `docs/jex/features/`, backend `wecms-api/docs/features/`, frontend `wecms-web/docs/features/`) for traceability.

## Feature-first structure

- Backend docs are organized by **feature domain**: `auth`, `common`, `banking`, `payment`, `addon-service`, `settings`.
- A feature doc belongs under `wecms-api/docs/features/{domain}/`.
- Each feature doc should cover: purpose, JEX source-of-truth pointers (file:line), entities/repositories/services involved, REST endpoints (link to `api-spec`/`references/api-spec`), database tables (link to `references/database`), business rules, validation, and open questions.
- Keep API contract detail (request/response shapes, DTO fields, error codes) in the api-spec docs and link to it from the feature doc — do not duplicate full payload tables in two places.

## Database & ERD references

- Schema, table-mapping, indexing, and coverage live under `wecms-api/docs/references/database/`.
- ERDs live under `wecms-api/docs/references/database/erd/` (per-domain `{domain}-erd.md` plus `full-project-erd.md`).
- When a feature touches new tables, cross-link the feature doc → the relevant database reference and ERD. Do not redraw schema inside the feature doc.

## Open questions (OQ)

- OQ items live under `wecms-api/docs/planning/open-questions/` (e.g. `documentation-follow-up.md`, `database-follow-up.md`).
- **OQ-DOC** = documentation open questions. **OQ-DB** = database open questions.
- You may **add** OQ items and **update** their status to "answered — pending confirmation."
- **Never close or delete an OQ-DOC or OQ-DB item without explicit user confirmation.** The human resolves them. If your edit answers an OQ, note the answer next to the item and flag it for confirmation in your final report.

## Cross-linking

- Use relative markdown links between docs.
- When you add a doc, link it from the nearest `README.md` index and from any sibling/parent doc that should reference it.
- When you edit a doc that other docs reference, check those back-references still make sense.

## Link validation

After every edit:
- Verify each relative link in touched files points to an existing file.
- Where practical, confirm heading anchors exist for `#anchor` links.
- Report broken or suspicious links; fix the ones you introduced.

## Final report (always)

Close every task with:
- Files **created** (full paths).
- Files **edited** (full paths + one-line summary of the change).
- Cross-links added or updated.
- OQ items touched (and any flagged for confirmation).
- Link-validation result (clean / list of issues).
