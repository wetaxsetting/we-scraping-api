# wecms-api-docs — Folder Structure & Placement Guide

Authoritative map for where backend docs go under `wecms-api/docs/`. Read alongside `api-doc-rules.md`.

> **Corrected 2026-08-17 to match the real tree.** This map previously showed `api-spec/` and
> `business-rules/` at top level, an `archive/` folder, `planning/roadmap|phase-1|phase-2|settings|addon/`
> subfolders, and `references/database/{indexing,query-analysis,flyway}/` — **none of those exist**. It also
> omitted `audits/`, `deployment/`, `security/`, `reports/`, and `sql/`. Verified against
> the tree; keep in sync with `SKILL.md`, `wecms-api/docs/README.md`, and `wecms-api/CLAUDE.md`.
>
> **Updated 2026-08-17 (P2).** `vat-declaration/` was removed (binaries relocated into the feature's
> `source-requirements/attachments/`), and the empty `testing/{integration,performance,regression}/`
> scaffolds were collapsed into `testing/README.md`.

## Directory map

```
wecms-api/docs/
├── README.md                      # docs root index — canonical folder list
├── workflow.md  error-codes.md  glossary.md  permissions.md   # pointer stubs → workspace root
│
├── features/                      # FEATURE-FIRST specs — primary home for feature docs
│   ├── README.md
│   ├── auth/  common/  banking/  payment/  addon-service/  settings/   # six menu domains
│   ├── wecms-batch/
│   └── integrations/haengsung/    # non-menu integration docs (Oracle sync / write-back)
│
├── architecture/                  # backend system architecture
│   ├── api-catalog/               # endpoint catalogue (auth … erd)
│   └── backend/  database/  integrations/  security/     # scope/placement guides (README-only today)
│
├── references/                    # supporting reference material (NOT feature docs)
│   ├── README.md
│   ├── api-spec/                  # REST endpoint contracts (*-api.md) + api-mapping.md
│   ├── business-rules/            # entities, repositories, services, security, settings
│   ├── audits/                    # ⚠️ MIGRATED 2026-08-17 — pointer stub only
│   ├── database/                  # SCHEMA references
│   │   ├── README.md
│   │   ├── legacy-table-inventory.md      # AUTHORITATIVE 141-table baseline
│   │   ├── flyway-migration-baseline.md   # READ BEFORE CITING ANY V<n>__*.sql
│   │   ├── database-coverage-matrix.md
│   │   ├── table-mapping/
│   │   ├── {domain}/              # banking/, payment/, settings/, addon/
│   │   └── erd/                   # {domain}-erd.md, full-project-erd.md, diagrams/*.mmd
│   ├── figma/  jex/               # Figma + JEX reference notes
│   └── migration/                 # JEX → Spring mapping + migration-playbook.md + jex-analysis/
│
├── bugs/                          # ADDED 2026-08-17 — dated BUG implementation records (docs-first)
│   └── README.md                  #   purpose / what belongs / filename / relationship to features + audits
├── issues/                        # ADDED 2026-08-17 — dated ISSUE implementation records (non-bug)
│   └── README.md
├── audits/                        # THE audit home — curated index in audits/README.md
│   ├── governance/  modules/  legacy-parity/  batch/  deployment/  historical/
├── deployment/                    # SIT/UAT run-books + checklists — index at deployment/README.md
├── security/                      # closure report, risk register, required secrets, rotation handoff
├── testing/                       # test plans + fixture conventions
│   └── sit/  uat/                 # integration/performance/regression → collapsed into testing/README.md
├── planning/
│   └── open-questions/            # LIVE — OQ tracking, DO NOT auto-close
├── reports/                       # one-off engineering reports
└── sql/                           # manual DBA .sql scripts, never app-run — see sql/README.md
    └── <YYYY>/                    #   docs-first DB-impact artifacts (existing-table + data changes)
```

## Placement decision guide

| You are documenting… | Put it under… |
|---|---|
| **A dated BUG implementation record** (docs-first — implemented behaviour is incorrect · something that worked is broken · behaviour differs from an accepted requirement · a defect causes wrong behaviour · parity behaviour is wrong and the task is to fix it) | `bugs/` as `descriptive-name-YYYY-MM-DD.md` — **never** `features/`, **never** `audits/` |
| **A dated ISSUE implementation record** (docs-first, non-bug — needed work that is not clearly a product defect · operational / configuration correction · an unresolved implementation concern · a tracked problem that does not fit BUG cleanly; **when unclear, prefer ISSUE**) | `issues/` as `descriptive-name-YYYY-MM-DD.md` — **never** `features/` |
| A feature's behavior, services, rules | `features/{domain}/` |
| A REST endpoint contract (request/response/DTO/errors) | `references/api-spec/` (link from the feature doc) |
| Entity / repository / service design | `references/business-rules/` |
| A table, schema, or table-mapping | `references/database/` (+ `{domain}/` subfolder) |
| An ERD | `references/database/erd/` |
| A JEX → Spring mapping | `references/migration/` |
| A **dated, point-in-time** parity gap vs JEX | `audits/<category>/` — **not** `features/`. `references/audits/` no longer exists as a content folder. |
| A deployment checklist / run-book / env-var reference | `deployment/` — **not** `features/` |
| A security closure / risk / secrets doc | `security/` |
| An unresolved question | `planning/open-questions/` (OQ-DOC or OQ-DB) |
| A test plan | `testing/` |
| A one-off engineering report | `reports/` |
| **A manual DBA `.sql` artifact** required by a bug/issue/feature record — existing-table alteration, index, constraint, existing-function replacement, or any data change (insert/update/delete, seed, reference data, backfill, corrective SQL) | `sql/<YYYY>/descriptive-name-YYYY-MM-DD.sql` — **not** `docs/bugs/`, **not** the migration folder, **not** an untracked scratch dir. New tables / new DB functions stay in `src/main/resources/db/migration/`. See `sql/README.md` + `CLAUDE.md` §14. |

> **One audit home.** `audits/` is the single authoritative location; `references/audits/` was
> consolidated into it on 2026-08-17 and is now a pointer stub. Write new audits into
> `audits/<category>/` — pick from `governance` · `modules` · `legacy-parity` · `batch` ·
> `deployment` · `historical`.

## Feature domains

The six **menu** domains are fixed: **auth, common, banking, payment, addon-service, settings**. Every menu-driven feature doc maps to exactly one. If a feature seems cross-domain, document its primary home under one domain and cross-link from the other.

Two non-menu feature areas also live under `features/`: **`wecms-batch/`** (the batch application) and **`integrations/haengsung/`** (customer-specific Oracle sync / write-back). These are not menu items, so they sit outside the six domains by design — do not force them into one.

## Placement rules

- A new feature doc → `features/{domain}/{feature-slug}.md` (or a subfolder for multi-doc features). Keep the slug identical to its JEX and frontend counterparts.
- **Dated implementation records** (docs-first — a **team-wide** rule binding every developer and every tool, see `wecms-api/CLAUDE.md` §"Docs-First Implementation Workflow (MANDATORY)" + §12 "Who must follow this") are named `descriptive-name-YYYY-MM-DD.md`, lowercase kebab-case, ISO date, subject-descriptive. **One task → one canonical record per repository** — never a per-developer copy (`…-dev-a-…`, `sokny-fix.md`); a second developer continuing the work updates the existing record. Never a work-code / sequence filename (`a1-01-…`, `k9-fix.md`, `p1-update.md`, `task-03.md`, `final-fix.md`); internal IDs go in the document metadata as `**Issue:**`. **Placement (revised 2026-08-17):** a BUG record lives in `bugs/`, an ISSUE record in `issues/` — **not** beside the feature. They are the one sanctioned exception to "reuse before create" — a record is always its own new file, and is finalized in place rather than superseded by a second "final" file.
- Never duplicate the same content in two places — pick the canonical home and cross-link. A dated record and the permanent feature docs are **not** duplication: the record says *why the change happened*, the feature docs say *how the system works now*.
- **There is no `archive/` folder** — it was removed 2026-06-23. Do not reference it and do not recreate it without an explicit decision.
- **Never create a new top-level folder under `docs/`.** If one is genuinely needed, update this file, `SKILL.md`, `wecms-api/docs/README.md` §Layout, and `wecms-api/CLAUDE.md` §"Documentation Structure Rules" in the same MR — all four must agree. *(`bugs/` and `issues/` were added exactly this way on 2026-08-17 and are now part of the approved set.)*
- **Never cite a Flyway migration number from memory or from an older doc.** The migration set was rebaselined 2026-06-09 (numbers reassigned, seeds/indexes/views dropped or consolidated). Verify against `src/main/resources/db/migration/` and read `docs/references/database/flyway-migration-baseline.md` before writing any `V<n>__*.sql` reference.
- Always update the nearest `README.md` index when adding a file.
