---
name: wecms-api-docs
description: Maintain WECMS refactor backend (Spring Boot) documentation under wecms-api/docs/. Use when creating, editing, restructuring, or cross-linking API/backend docs — feature specs, API contracts, database/ERD references, migration notes, or open questions. Enforces feature-first structure, reuse-before-create, open-question preservation, and link validation.
---

# wecms-api-docs

Maintains the **refactor backend** documentation tree at `wecms-api/docs/`. This is the Spring Boot 3.5.13 R&D target. JEX trees (`WECMS DEV/`, `SOURCE_CODE_ADMIN/`, …) are read-only functional reference — never write there.

## When this skill applies

Trigger when the task involves any of:
- Creating or editing a backend feature spec (`wecms-api/docs/features/...`).
- Writing or updating a REST API contract / endpoint doc.
- Adding or revising database references, table mappings, or ERDs.
- Recording, updating, or (with confirmation) resolving an open question.
- Reorganizing or cross-linking anything under `wecms-api/docs/`.

Do **not** apply this skill to frontend docs (`wecms-web/docs/` → use `wecms-web-docs`), to JEX docs (`docs/jex/`), or to application source code.

## Hard rules

1. **Never modify application source code.** Documentation only.
2. **Never move or reorganize existing docs** unless the user explicitly asks. Default to in-place edits.
3. **Write only under `wecms-api/docs/`** (plus the workspace cross-cutting folders the root CLAUDE.md allows: `docs/_shared/`, `docs/security/`, `docs/testing/`, `docs/bug-reports/`). Never write into `wecms-web/docs/` or `docs/jex/`.
4. **Audit before creating.** Never create a new doc without first checking whether one already covers the topic.
5. **Reuse, cross-link, and preserve.** Extend existing docs; link related docs; never delete content without confirmation.
6. **Identifier naming = kebab-case slugs**, not legacy JEX WSVC ids (`firm-banking-settings`, never `comm_0220_001`). Legacy ids appear only as JEX file:line citations or migration-traceability prose.
7. **Feature names match across systems** for traceability — a backend feature slug must match its JEX and frontend counterparts (e.g. `view-setting`).

## Folder structure

```
wecms-api/docs/
├── README.md
├── features/              # feature-first specs (one folder/file per feature)
│   ├── auth/
│   ├── common/
│   ├── banking/
│   ├── payment/
│   ├── addon-service/
│   └── settings/
├── api-spec/              # REST endpoint contracts (controllers, DTOs, validation, errors)
├── architecture/
├── business-rules/        # entities, repositories, services, security
├── references/
│   ├── database/          # schema design, table-mapping, indexing, coverage matrix
│   │   └── erd/           # ERDs (per-domain + full-project)
│   ├── api/  api-spec/  audits/  external/  figma/  jex/
│   └── migration/         # JEX → Spring service-by-service mapping
├── planning/
│   └── open-questions/    # OQ tracking (documentation-follow-up.md, database-follow-up.md, …)
├── testing/
└── archive/
```

See `references/api-doc-structure.md` for full placement rules and `references/api-doc-rules.md` for content conventions.

## Feature domains (feature-first)

API documentation **must follow feature-first structure**, organized under the six domains:
`auth`, `common`, `banking`, `payment`, `addon-service`, `settings`.

Every feature doc lives under `wecms-api/docs/features/{domain}/`. A complex feature gets its own subfolder; a simple one gets a single kebab-case `.md`.

## Required workflow

Follow these steps in order for any doc task:

1. **Audit existing docs first.** Glob/grep `wecms-api/docs/` (especially `features/{domain}/`, `references/database/`, and `README.md` index files) for the topic. List what already exists before writing anything.
2. **Reuse / move / cross-link before creating.** Prefer extending an existing doc. If a new doc is truly needed, place it per the structure rules and link it from the relevant `README.md` index and from sibling docs.
3. **Preserve open questions.** OQ items live in `wecms-api/docs/planning/open-questions/`. Never delete or close an **OQ-DOC** (documentation) or **OQ-DB** (database) item without explicit user confirmation. You may add new OQ items and update their status to "answered — pending confirmation," but the human closes them.
4. **Validate links.** After editing, check every relative link in touched files resolves to a real path (and that anchors exist where feasible). Report any broken links.
5. **Provide a final report.** Summarize files created/edited, cross-links added, OQ items touched, and link-validation results.

## References

- `references/api-doc-rules.md` — content conventions, naming, cross-linking, OQ handling.
- `references/api-doc-structure.md` — directory map and placement decision guide.
