# Technical-Doc Classification Report — `docs/refactor-wecms/`

**Date:** 2026-05-22
**Run by:** Claude Code (workspace housekeeping — pass 2)
**Scope:** Classify the flat technical files in `backend/`, `frontend/`, `database/`, `migration/`, `audits/`, `planning/` into the new typed subfolders created in pass 1.
**Companion to:** [structure-refactor-report.md](structure-refactor-report.md) (pass 1).

---

## 1 · Summary

- **28 files moved** into typed subfolders.
- **16 files left in place** — every one is either a feature-spanning design doc that legitimately spans multiple layers, or a cross-cutting reference whose target category is genuinely ambiguous. Each is named in §3 with the reason.
- **0 files deleted.** No content changed inside any file. Only file paths and the markdown links that referenced them moved.
- **All inbound links re-resolved.** Internal links inside each moved file were rewritten (`](../X` → `](../../X`) for the +1 depth. Cross-doc references were rewritten by string substitution of the old path → new path.
- **0 files renamed.** All filenames remained kebab-case throughout.

---

## 2 · Files moved (28)

### backend/ → migration/

| From | To | Why |
|---|---|---|
| `backend/api-mapping.md` | `migration/api-mapping/api-mapping.md` | Cross-feature **index of refactor endpoints with JEX counterpart** — primary axis is JEX → refactor mapping, not a per-controller contract. |

### frontend/ → migration/

| From | To | Why |
|---|---|---|
| `frontend/page-mapping.md` | `migration/screen-mapping/page-mapping.md` | "JEX `_view.jsp` → Next.js Route" — explicit JEX-screen ↔ refactor-route translation. |

### database/ → database/table-mapping/

| From | To | Why |
|---|---|---|
| `database/db-mapping.md` | `database/table-mapping/db-mapping.md` | "Cross-feature index of tables touched by the refactor, with their JEX counterpart and the Flyway migration that introduced them." — the canonical table-mapping file. |

### migration/ → migration/api-mapping/

Per-feature JEX → Spring REST mapping files (filename ends with `-mapping.md`, content is the WSVC ↔ REST endpoint mapping for one feature).

| From | To |
|---|---|
| `migration/contacts-mapping.md` | `migration/api-mapping/contacts-mapping.md` |
| `migration/erp-transaction-history-mapping.md` | `migration/api-mapping/erp-transaction-history-mapping.md` |
| `migration/firm-banking-history-mapping.md` | `migration/api-mapping/firm-banking-history-mapping.md` |
| `migration/firm-banking-settings-mapping.md` | `migration/api-mapping/firm-banking-settings-mapping.md` |
| `migration/service-mapping.md` | `migration/api-mapping/service-mapping.md` |

### migration/ → migration/jex-analysis/

| From | To | Why |
|---|---|---|
| `migration/approval-line-add-flow-analysis.md` | `migration/jex-analysis/approval-line-add-flow-analysis.md` | Reverse-engineering write-up of the legacy `comm_0222_002_c001` add-flow — pure JEX analysis. |

### planning/ → planning/phase-1/

Sprint and feature plans whose scope sits in the Phase 1 deliverable window (login, banking, approval, contacts, accounting parity).

| From | To |
|---|---|
| `planning/account-inquiry-and-transaction-history-plan.md` | `planning/phase-1/account-inquiry-and-transaction-history-plan.md` |
| `planning/approval-line-plan.md` | `planning/phase-1/approval-line-plan.md` |
| `planning/chart-of-accounts-plan.md` | `planning/phase-1/chart-of-accounts-plan.md` |
| `planning/contacts-tasks.md` | `planning/phase-1/contacts-tasks.md` |
| `planning/task-sprint-0-login-refactor.md` | `planning/phase-1/task-sprint-0-login-refactor.md` |
| `planning/task-sprint-1-backend-golden-chipmunk.md` | `planning/phase-1/task-sprint-1-backend-golden-chipmunk.md` |
| `planning/transfer-history-plan.md` | `planning/phase-1/transfer-history-plan.md` |

### planning/ → planning/open-questions/

| From | To | Why |
|---|---|---|
| `planning/banking-reports-remaining-scope.md` | `planning/open-questions/banking-reports-remaining-scope.md` | "Pending PO / lead sign-off" — by the file's own status header, this is an OQ tracker, not a sprint plan. |

### planning/ → planning/roadmap/

| From | To | Why |
|---|---|---|
| `planning/task-plan-common-backend-functional-seahorse.md` | `planning/roadmap/task-plan-common-backend-functional-seahorse.md` | "Common Backend Strategy — DB Functions & First Company Account Bootstrap" — foundational/strategic, cuts across many sprints. |

### audits/ → audits/migration-audit/

Every existing audit compares the refactor implementation against the JEX behavioural source of truth. No file in the current audit set is purely API-only, security-only, or UX-only, so all 10 land under `migration-audit/` (the canonical "behavioural parity vs JEX" bucket).

| From | To |
|---|---|
| `audits/db-parity-audit-2026-04-30.md` | `audits/migration-audit/db-parity-audit-2026-04-30.md` |
| `audits/erp-transaction-history-audit.md` | `audits/migration-audit/erp-transaction-history-audit.md` |
| `audits/firm-banking-history-parity-checklist.md` | `audits/migration-audit/firm-banking-history-parity-checklist.md` |
| `audits/firm-banking-history-review-2026-05-08.md` | `audits/migration-audit/firm-banking-history-review-2026-05-08.md` |
| `audits/firm-banking-settings-parity-checklist.md` | `audits/migration-audit/firm-banking-settings-parity-checklist.md` |
| `audits/firm-banking-settings-review-2026-05-09.md` | `audits/migration-audit/firm-banking-settings-review-2026-05-09.md` |
| `audits/nxisas-vcb-spike.md` | `audits/migration-audit/nxisas-vcb-spike.md` |
| `audits/purpose-settings-audit.md` | `audits/migration-audit/purpose-settings-audit.md` |
| `audits/transfer-history-audit-2026-05-20.md` | `audits/migration-audit/transfer-history-audit-2026-05-20.md` |
| `audits/vas-account.md` | `audits/migration-audit/vas-account.md` |

---

## 3 · Files left in place (16)

Each of these is feature-named at its parent root but its content spans multiple technical concerns. Splitting any one of them would require a per-section content split (controller / service / repository / DTO / audit), which is editorial work the user asked **not** to do in this pass. They are functioning today as the **canonical per-feature design spine** at each layer.

### backend/ — feature-spanning design (controller + service + entity + repo + DTOs)

| File | Reason |
|---|---|
| `backend/approval-line.md` | One doc covering controller, service, entity, repository, validation, audit writes for the `approval-line` feature. Can be split into `backend/api/`, `backend/services/`, `backend/entities/`, `backend/repositories/` later — but only by content-split, not by file-move. |
| `backend/chart-of-accounts.md` | Same shape. |
| `backend/contacts-design.md` | Same shape. |
| `backend/vas-account.md` | Same shape. |

### frontend/ — feature-spanning design (route + components + hooks + state)

| File | Reason |
|---|---|
| `frontend/approval-line.md` | One doc covering route, page composition, components, hooks, state, i18n for the `approval-line` panel. |
| `frontend/chart-of-accounts.md` | Same shape. |
| `frontend/contacts-design.md` | Same shape. |
| `frontend/vas-account.md` | Same shape. |

### database/ — feature-spanning schema (DDL + indexing + constraints + parity notes)

| File | Reason |
|---|---|
| `database/chart-of-accounts.md` | DDL + indexes + UK / FK choices + soft-delete behaviour for one feature, all in one file. Can be split into `database/erd/`, `database/indexing/`, `database/table-mapping/` later. |
| `database/contacts-schema.md` | Same shape. |
| `database/vas-account.md` | Same shape. |
| `database/manual-tenant-setup.md` | DBA operational guide — fits none of `erd/`, `flyway/`, `indexing/`, `query-analysis/`, `table-mapping/` cleanly. Best left at the `database/` root until an `operations/` subfolder is added (out of scope for this pass). |

### migration/ — cross-cutting feature migration map (API + DB + screen + audit in one doc)

| File | Reason |
|---|---|
| `migration/approval-line.md` | Full feature migration: WSVC ↔ REST, IDO ↔ JPA, audit-row mapping, screen-host decision, in one file. Splitting requires editorial work. |
| `migration/chart-of-accounts.md` | Same shape. |
| `migration/purpose-settings.md` | Same shape. |
| `migration/transfer-history.md` | Same shape — companion to the feature spec. |
| `migration/vas-account.md` | Same shape. |

---

## 4 · Links updated

### Inside the 28 moved files

Each moved file dropped one directory level (e.g. `backend/api-mapping.md` → `migration/api-mapping/api-mapping.md`). All relative links inside those files were rewritten with `sed -E 's#\]\(\.\./#](../../#g'` — every `](../X` link became `](../../X` so it continues to resolve.

### Across every `.md` file in the tree

Twenty-eight path substitutions were applied across **every** `.md` file under `docs/refactor-wecms/` (`find . -name "*.md" -print0 | xargs -0 sed -i -E …`). One substitution per moved file, rewriting the old suffix path to the new suffix path. The relative-prefix length (`../`, `../../`, etc.) is unaffected because every move stays inside `docs/refactor-wecms/` and only adds one directory level — the prefix to the path stays the same, the path itself changes.

### Verification

The following greps return **no matches** after the pass:

```bash
# Stale links to moved files
grep -rnE 'backend/api-mapping\.md|frontend/page-mapping\.md|database/db-mapping\.md' .
grep -rnE 'migration/(contacts-mapping|erp-transaction-history-mapping|firm-banking-history-mapping|firm-banking-settings-mapping|service-mapping|approval-line-add-flow-analysis)\.md' .
grep -rnE 'audits/(db-parity-audit-2026-04-30|erp-transaction-history-audit|firm-banking-history-parity-checklist|firm-banking-history-review-2026-05-08|firm-banking-settings-parity-checklist|firm-banking-settings-review-2026-05-09|nxisas-vcb-spike|purpose-settings-audit|transfer-history-audit-2026-05-20|vas-account)\.md' .
grep -rnE 'planning/(account-inquiry-and-transaction-history-plan|approval-line-plan|banking-reports-remaining-scope|chart-of-accounts-plan|contacts-tasks|task-plan-common-backend-functional-seahorse|task-sprint-0-login-refactor|task-sprint-1-backend-golden-chipmunk|transfer-history-plan)\.md' .
```

All four return empty.

Spot-checks confirmed two representative cases:

- `audits/migration-audit/firm-banking-settings-review-2026-05-09.md:6` — links to feature spec resolve via `../../features/settings/firm-banking-settings.md` (depth +1 from the original `audits/` root).
- `planning/phase-1/transfer-history-plan.md:13` — links to feature spec resolve via `../../features/banking/transfer-history.md` (depth +1 from the original `planning/` root).

---

## 5 · Unresolved conflicts

### 5.1 — Audit type granularity

All ten audits landed in `migration-audit/`. The three other audit subfolders (`api-audit/`, `security-audit/`, `ux-audit/`) have **no current occupants**. This is honest reporting: every audit produced so far is a holistic feature-parity review that touches API + UX + DB + security at once. The audit-type subfolders exist as **landing zones for future, narrower audits** — e.g., a dedicated security review or a Tailwind-token UX sweep.

**No conflict to resolve today.** Re-classifying any of the ten requires extracting sub-findings into separate files — explicit content work the user did not authorise this pass.

### 5.2 — Phase classification of `task-plan-common-backend-functional-seahorse.md`

This file is a foundational backend-strategy plan (DB function policy, first-company bootstrap) that influences Phase 1 work but reads as a multi-phase / architectural decision. It was placed in `planning/roadmap/` rather than `planning/phase-1/` because:

- Its content survives any single sprint cycle.
- It defines policy (the DB-function funnel, `LegacyFunctionRepository`) that Phase 2 and later will also follow.

If Sonny would prefer it under `planning/phase-1/` for ownership reasons, moving it back is a one-file `mv` and a single substitution across the tree.

### 5.3 — `database/manual-tenant-setup.md` has no clean home

The five `database/` subfolders (`erd/`, `flyway/`, `indexing/`, `query-analysis/`, `table-mapping/`) are all about **schema design**. This file is an **operational runbook** for DBAs provisioning a tenant. It is correctly stored under `database/` (it's database-adjacent) but none of the current subfolders are a good fit.

**Recommendation (not executed):** add `database/operations/` next pass, then move this file there. Left in place until then.

### 5.4 — `references/crystalline-honking-sky.md` (unchanged)

Already flagged in the pass-1 report (§7.4). Still pending an intent decision (rename + move to `references/jex/` or `references/external/`). Out of scope for this pass; the user did not list `references/` in the targets for pass 2.

### 5.5 — `testing/vas-account.md` (unchanged)

Single feature-named file at the `testing/` root. The five testing subfolders (`uat/`, `sit/`, `regression/`, `integration/`, `performance/`) all exist with READMEs. Without reading the file's content to determine which test layer it documents, classification is a guess. Out of scope for this pass; the user did not list `testing/` in the targets for pass 2.

---

## 6 · Final tree (depth ≤ 3, only changed branches)

```
docs/refactor-wecms/
├── audits/
│   ├── README.md
│   ├── api-audit/                      (README only — landing zone)
│   ├── security-audit/                 (README only — landing zone)
│   ├── ux-audit/                       (README only — landing zone)
│   └── migration-audit/
│       ├── README.md
│       ├── db-parity-audit-2026-04-30.md
│       ├── erp-transaction-history-audit.md
│       ├── firm-banking-history-parity-checklist.md
│       ├── firm-banking-history-review-2026-05-08.md
│       ├── firm-banking-settings-parity-checklist.md
│       ├── firm-banking-settings-review-2026-05-09.md
│       ├── nxisas-vcb-spike.md
│       ├── purpose-settings-audit.md
│       ├── transfer-history-audit-2026-05-20.md
│       └── vas-account.md
│
├── backend/
│   ├── README.md
│   ├── approval-line.md                (LEFT — feature-spanning)
│   ├── chart-of-accounts.md            (LEFT — feature-spanning)
│   ├── contacts-design.md              (LEFT — feature-spanning)
│   ├── vas-account.md                  (LEFT — feature-spanning)
│   ├── addon/   banking/   settings/   (existing per-feature subfolders)
│   ├── api/   services/   entities/   repositories/   security/   integrations/   (READMEs only)
│
├── database/
│   ├── README.md
│   ├── chart-of-accounts.md            (LEFT — feature-spanning)
│   ├── contacts-schema.md              (LEFT — feature-spanning)
│   ├── manual-tenant-setup.md          (LEFT — no clean target; see §5.3)
│   ├── vas-account.md                  (LEFT — feature-spanning)
│   ├── addon/   banking/   settings/   (existing per-feature subfolders)
│   ├── erd/   flyway/   indexing/   query-analysis/   (READMEs only)
│   └── table-mapping/
│       ├── README.md
│       └── db-mapping.md
│
├── frontend/
│   ├── README.md
│   ├── approval-line.md                (LEFT — feature-spanning)
│   ├── chart-of-accounts.md            (LEFT — feature-spanning)
│   ├── contacts-design.md              (LEFT — feature-spanning)
│   ├── vas-account.md                  (LEFT — feature-spanning)
│   ├── addon/   banking/   settings/   (existing per-feature subfolders)
│   ├── routes/   components/   hooks/   view-setting/   state-management/   integrations/   (READMEs only)
│
├── migration/
│   ├── README.md
│   ├── approval-line.md                (LEFT — cross-cutting feature map)
│   ├── chart-of-accounts.md            (LEFT — cross-cutting feature map)
│   ├── purpose-settings.md             (LEFT — cross-cutting feature map)
│   ├── transfer-history.md             (LEFT — cross-cutting feature map)
│   ├── vas-account.md                  (LEFT — cross-cutting feature map)
│   ├── banking/                        (existing per-feature subfolder)
│   ├── api-mapping/
│   │   ├── README.md
│   │   ├── api-mapping.md              (moved from backend/)
│   │   ├── contacts-mapping.md
│   │   ├── erp-transaction-history-mapping.md
│   │   ├── firm-banking-history-mapping.md
│   │   ├── firm-banking-settings-mapping.md
│   │   └── service-mapping.md
│   ├── jex-analysis/
│   │   ├── README.md
│   │   └── approval-line-add-flow-analysis.md
│   ├── screen-mapping/
│   │   ├── README.md
│   │   └── page-mapping.md             (moved from frontend/)
│   ├── risk-analysis/                  (README only — landing zone)
│   └── rollout-plan/                   (README only — landing zone)
│
├── planning/
│   ├── README.md
│   ├── addon/                          (existing per-feature subfolder)
│   ├── settings/                       (existing per-feature subfolder)
│   ├── phase-1/
│   │   ├── README.md
│   │   ├── account-inquiry-and-transaction-history-plan.md
│   │   ├── approval-line-plan.md
│   │   ├── chart-of-accounts-plan.md
│   │   ├── contacts-tasks.md
│   │   ├── task-sprint-0-login-refactor.md
│   │   ├── task-sprint-1-backend-golden-chipmunk.md
│   │   └── transfer-history-plan.md
│   ├── phase-2/                        (README only — landing zone)
│   ├── open-questions/
│   │   ├── README.md
│   │   └── banking-reports-remaining-scope.md
│   └── roadmap/
│       ├── README.md
│       └── task-plan-common-backend-functional-seahorse.md
```

The unchanged top-level branches (`README.md`, `archive/`, `architecture/`, `features/`, `references/`, `reports/`, `testing/`) carry over from pass 1 — see [structure-refactor-report.md](structure-refactor-report.md) for their layout.

---

## 7 · Recommended next steps

In rough priority order — none auto-executed in this pass:

1. **Content-split the 16 left-in-place feature design docs.** Each one (e.g. `backend/approval-line.md`) is the per-feature spine that spans multiple layers. Splitting requires reading the doc and deciding what stays as a feature-anchor file vs what moves into `api/ services/ entities/ repositories/` under `backend/`. Same shape for `frontend/`, `database/`, `migration/`.
2. **Move `database/manual-tenant-setup.md`** into a new `database/operations/` subfolder (or `docs/_shared/` if it's tenant-onboarding for the live system as well).
3. **Decide on `task-plan-common-backend-functional-seahorse.md`** placement (roadmap vs phase-1).
4. **Backfill `references/crystalline-honking-sky.md`** with a meaningful name and a `references/jex/` or `references/external/` home.
5. **Classify `testing/vas-account.md`** into `testing/uat/`, `testing/sit/`, `testing/integration/`, etc., once its test-layer is identified.
6. **Add a "Last verified" date** to each per-feature design doc so future drift audits know when to re-check.

---

## 8 · Verification commands (for future regression checks)

```bash
# 1. Confirm no flat feature-named files remain in the typed-target parents
find docs/refactor-wecms/audits -maxdepth 1 -type f -name "*.md" | grep -v README     # → empty
find docs/refactor-wecms/planning -maxdepth 1 -type f -name "*.md" | grep -v README   # → empty

# 2. Confirm no stale links to the moved files exist
grep -rnE 'backend/api-mapping\.md|frontend/page-mapping\.md|database/db-mapping\.md' docs/refactor-wecms/
grep -rnE 'migration/(contacts-mapping|erp-transaction-history-mapping|firm-banking-history-mapping|firm-banking-settings-mapping|service-mapping|approval-line-add-flow-analysis)\.md' docs/refactor-wecms/
grep -rnE 'audits/(db-parity-audit-2026-04-30|erp-transaction-history-audit|firm-banking-history-parity-checklist|firm-banking-history-review-2026-05-08|firm-banking-settings-parity-checklist|firm-banking-settings-review-2026-05-09|nxisas-vcb-spike|purpose-settings-audit|transfer-history-audit-2026-05-20|vas-account)\.md' docs/refactor-wecms/
grep -rnE 'planning/(account-inquiry-and-transaction-history-plan|approval-line-plan|banking-reports-remaining-scope|chart-of-accounts-plan|contacts-tasks|task-plan-common-backend-functional-seahorse|task-sprint-0-login-refactor|task-sprint-1-backend-golden-chipmunk|transfer-history-plan)\.md' docs/refactor-wecms/

# 3. Confirm every new subfolder has a README
for d in $(find docs/refactor-wecms -mindepth 2 -type d -not -path '*/archive*' -not -path '*/reports*'); do
  [ -f "$d/README.md" ] || echo "MISSING README: $d"
done
```

All three return empty / clean output after this pass.
