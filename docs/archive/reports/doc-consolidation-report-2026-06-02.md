# Documentation Consolidation & Cleanup Report — `wecms-api/docs`

**Date:** 2026-06-02 · **Owner:** Sonny / Arthur (via Claude) · **System:** Refactor (`wecms-api/`) · **Status:** ✅ Complete

One-time enterprise documentation audit + consolidation of the `wecms-api/docs` tree at ~90% project completion. Read-only audit ran first (six parallel content sweeps across features, references, planning, testing, archive, and the README hierarchy); destructive actions were executed only after explicit user confirmation of four judgment calls. This file is the historical record; it does not author new live content.

---

## 1. Audit summary

The tree was already in **strong shape**. The intended **3-layer pattern** — feature hub (`features/{domain}/`) → cross-linked supporting layers (`references/{business-rules,database,migration,audits}/`) — was intact and correctly cross-linked. Most apparent "duplicates" (e.g. `vas-account` in 5 locations) are **legitimate distinct layers**, not duplication. Pre-cleanup link health was already excellent (0 broken links across 267 relative links in the 10 index files).

Genuine problems were **localized**:
- `features/payment/` carried a structural anti-pattern (sibling `.md` + folder), a duplicate index, and 20 boilerplate screen sub-docs that contradicted the folder's own stated standard.
- One real, high-value doc sat under a nonsense filename at the `references/` root.
- 8 completed task-plans for shipped features lingered in active `planning/` with stale "not started" headers.
- 2 byte-identical stale snapshots sat in `archive/features/`.
- A set of speculative empty scaffold folders (README-only).

What was **verified healthy and left alone:** VND/FCY banking docs (~60% unique — FCY is a multi-currency enhancement); Smart Organizer discovery scaffolds (intentional); `references/api/` vs `api-spec/` (external vs internal APIs — distinct); all `*-review-<date>.md` vs `*-parity-checklist.md` audits (frozen-evidence companions, not superseded); `testing/vas-account.md` (genuine test plan, correctly placed); `architecture/` (system-design only, no feature docs leaked in).

---

## 2. Duplicate files removed (deleted)

| File | Reason |
|---|---|
| `archive/features/vas-account.md` | Byte-identical stale snapshot of the live hub `features/settings/vas-account.md` — zero unique content. |
| `archive/features/contacts.md` | Near-identical stale snapshot of `features/settings/contacts.md` — zero unique content. |
| `features/payment/_payment-module-overview.md` | Duplicate cross-sibling index; unique §§ merged into `features/payment/README.md`. |
| `features/payment/approval-box.md` | Thin stub duplicating what a folder README should be; content absorbed into the new `features/payment/approval-box/README.md`. |
| `features/payment/internal-transfer/{scheduled-transfer, scheduled-transfer-details, transfer-result, transfer-result-details}.md` | 4 boilerplate screen docs → folded into `internal-transfer/README.md` as a Screens section. |
| `features/payment/expense-payment/{scheduled-payment, scheduled-payment-details, payment-result, payment-result-details}.md` | 4 boilerplate screen docs → folded into `expense-payment/README.md`. |
| `features/payment/vendor-payment/{scheduled-payment, scheduled-payment-details, payment-result, payment-result-details}.md` | 4 boilerplate screen docs → folded into `vendor-payment/README.md`. |
| `features/payment/payroll/{scheduled-payment, scheduled-payment-details, payment-result, payment-result-details}.md` | 4 boilerplate screen docs → folded into `payroll/README.md`. |
| `features/payment/overseas-payment/{scheduled-payment, scheduled-payment-details, payment-result, payment-result-details}.md` | 4 boilerplate screen docs → folded into `overseas-payment/README.md`. |

**Total deleted: 24 files** (2 archive snapshots + 2 payment index/stub files + 20 payment screen boilerplate docs).

---

## 3. Files moved / renamed

| From | To | Reason |
|---|---|---|
| `references/crystalline-honking-sky.md` | `references/database/db-function-classification.md` | Real "DB Function Classification & Migration Priority" doc (~168 functions) had a nonsense filename at the wrong level. Distinct companion to `architecture/database/db-function-strategy.md` (inventory vs policy). Internal links re-pathed. |
| `planning/phase-1/approval-line-plan.md` | `archive/planning/phase-1/approval-line-plan.md` | Completed plan — Approval Line shipped (V90–V92). |
| `planning/phase-1/chart-of-accounts-plan.md` | `archive/planning/phase-1/chart-of-accounts-plan.md` | Completed — Chart of Accounts shipped (V94–V97). |
| `planning/phase-1/contacts-tasks.md` | `archive/planning/phase-1/contacts-tasks.md` | Completed — Contacts shipped (V84–V89). |
| `planning/phase-1/transfer-history-plan.md` | `archive/planning/phase-1/transfer-history-plan.md` | Completed — Transfer History shipped (2026-05-20). |
| `planning/phase-1/task-sprint-0-login-refactor.md` | `archive/planning/phase-1/task-sprint-0-login-refactor.md` | Completed — login working. |
| `planning/phase-1/task-sprint-1-backend-golden-chipmunk.md` | `archive/planning/phase-1/task-sprint-1-backend-golden-chipmunk.md` | Completed — Sprint 1 banking/add-on landed. |
| `planning/settings/erp-transaction-history-plan.md` | `archive/planning/settings/erp-transaction-history-plan.md` | Completed — ERP Transaction History shipped (V71–V72). |
| `planning/settings/purpose-settings-task-plan.md` | `archive/planning/settings/purpose-settings-task-plan.md` | Completed — Purpose Settings shipped (V80–V83). |

**27 inbound links** to the moved plans were re-pathed to `archive/planning/...` across live + archived docs; the 8 moved files' own outbound links were shifted one level deeper. `planning/phase-1/account-inquiry-and-transaction-history-plan.md` was **kept active** (Account Inquiry shipped, Transaction-History frontend is residual) with a status banner.

---

## 4. Folders removed

Speculative empty scaffold folders (README-only, no content docs, unlikely to be filled in local-R&D scope):

| Folder | Note |
|---|---|
| `references/database/query-analysis/` | Query-plan notes — no consumer in local R&D. |
| `references/migration/risk-analysis/` | Migration risk register — no live cutover in R&D. |
| `references/migration/rollout-plan/` | Cutover plan — no DEV/UAT/PROD deploy in scope. |
| `references/audits/api-audit/` | Empty audit subfolder. |
| `references/audits/security-audit/` | Empty audit subfolder. |
| `references/audits/ux-audit/` | Empty audit subfolder. |
| `planning/settings/` | Emptied after both plans were archived; removed. |

Parent-README links pointing at these were repointed (`references/README.md`, `database/README.md`, `database/indexing/README.md`, `figma/README.md`). **Kept** as intentional scaffolding (per user choice): `database/{indexing,flyway}`, `business-rules/{entities,repositories,services,security,integrations}`, `references/{external,figma,jex}`, `testing/{integration,sit,regression,performance,uat}`.

---

## 5. Files archived

Listed in §3 (the 8 completed plans moved to `archive/planning/`). The archive index (`archive/README.md`) gained a `planning/` row. No other live docs were archived — the existing archive contents (`settings-menu-hubs/`, `dissolved-feature-indexes/`, `shared-pointers/`, `reports/`, `features/sales-and-expense-einvoices.md`) were verified genuinely historical and left in place.

---

## 6. README & link hygiene fixes (edits, no content loss)

- `features/payment/README.md` — absorbed the 5 unique sections of the deleted module-overview (sibling matrix, shared backend infra, shared frontend shells, cross-cutting conventions, add-a-sibling procedure); fixed misleading family link display-text.
- `features/payment/approval-box/README.md` — **created** as the family hub; preserves the `resl_0001_001` vs `pymn_0001_001` JEX naming-bug finding pointer (lives in `discovery.md §1`).
- 5 payment family READMEs — added `## Screens` sections; stub backfill checklists + payroll slug-asymmetry note preserved.
- `docs/README.md` — payment menu table repointed to folder READMEs; dead module-overview link removed.
- `references/README.md`, `references/database/README.md`, `architecture/database/db-function-strategy.md` — repointed to the relocated `db-function-classification.md`.
- `references/migration/README.md` — corrected the retired `@JsonProperty UPPER_SNAKE` wire-mapping line to the current lowercase `snake_case` standard; fixed two stale `../backend/` / `../frontend/` display labels.
- `testing/README.md` + `planning/README.md` — added Subfolders nav tables.
- `architecture/security/README.md` — added "What goes / does NOT go here" ownership block.
- `references/database/erd/README.md` — added the missing `common-erd.md` row.
- `planning/open-questions/README.md` — added `database-follow-up.md` to the open-items index.
- 10 links broken by the moves/deletes/prunes were repaired; post-cleanup internal-link scan = **0 broken internal links**.

**OQ preservation:** no OQ-DOC / OQ-DB item was closed or deleted. All open items (OQ-DOC-3…13, OQ-DB-1…6, banking-reports OQ-1/2/3, and feature-local OQs) remain intact.

---

## 7. Final structure (top two levels)

```
wecms-api/docs/
├── README.md  WORKFLOW.md  glossary.md  permissions.md  error-codes.md
├── architecture/      backend/  database/(db-function-strategy.md)  integrations/  security/
├── features/
│   ├── auth/          README.md  login-auth.md
│   ├── common/        README.md  audit-log.md  view-setting.md
│   ├── banking/       bank-credentials.md  current-savings-vnd/  current-savings-fcy/  own-account-transfer/  reports/
│   ├── payment/       README.md  approval-box/  internal-transfer/  vendor-payment/  expense-payment/  payroll/  overseas-payment/  card-transactions.md  exchange-rate-inquiry.md
│   ├── addon-service/ README.md  sales-e-invoices.md  expenses-e-invoices.md  operation-status-inquiry.md
│   └── settings/      README.md  + one canonical hub per Settings menu item
├── references/
│   ├── api/  api-spec/(addon,banking)  external/  figma/  jex/
│   ├── business-rules/   per-feature design + entities/repositories/services/security/integrations/ + settings/
│   ├── database/         db-function-classification.md  database-coverage-matrix.md  table-ownership.md  manual-tenant-setup.md  erd/  flyway/  indexing/  table-mapping/  {addon,banking,payment,settings}/
│   ├── migration/        MIGRATION_PLAYBOOK.md  service-mapping.md  jex-analysis/  screen-mapping/  + per-feature mappings
│   └── audits/           per-feature parity checklists + dated reviews
├── planning/
│   ├── open-questions/   banking-reports-remaining-scope.md  database-follow-up.md  documentation-follow-up.md
│   ├── roadmap/  phase-1/(account-inquiry residual)  phase-2/  addon/
├── testing/             integration/  sit/  regression/  performance/  uat/  vas-account.md
└── archive/             planning/  reports/  features/  settings-menu-hubs/  dissolved-feature-indexes/  shared-pointers/
```

---

## 8. Remaining documentation gaps (not addressed here — for a follow-up content pass, not structural)

These are **content-accuracy** items surfaced during the audit; they are out of scope for a structure/dedup cleanup and are flagged for a separate pass / PO decision:

1. **Stale "not implemented" statuses** in shipped-feature docs: `features/settings/contacts.md`, `references/business-rules/contacts-design.md`, `references/database/contacts-schema.md` still say "Design — not implemented" (Contacts shipped V84–V89, and the actual decisions — V85 `wb_get_next_val` TRCO_KEY branch, `TRCO_INFO_FILE` in scope — went the other way).
2. **Retired `validateCell` endpoint** still referenced in `references/business-rules/vas-account.md` (§4/§6) and `testing/vas-account.md` (§4) — the hub + audit mark it removed.
3. **own-account-transfer status contradiction:** `own-account-transfer.md` header says "Phase 1 — design only" while its README + `transfer-history.md` say Implemented (17 resolved OQs, frontend landed).
4. **`fnsh_0200_001` WSVC-id semantic conflict:** documented as **Smart Organizer** (banking) and as **Income Statement / Trial Balance** (`references/migration/chart-of-accounts.md`). Verify against JEX source before promoting either (raise with Sonny). Tracked as OQ-DOC-6.
5. **daily-financial base URI drift:** `features/banking/reports/daily-financial.md` uses `/api/v1/reports/...` vs the `/api/v1/banking/reports/...` standard (daily-cash already corrected).
6. **Pervasive pre-relocation README titles** (`# backend/...`) and a few stale display-text labels left over from the 2026-06-01 `docs/refactor-wecms/` relocation — cosmetic, batchable.
7. **Test-plan coverage gap:** `testing/vas-account.md` is the only feature test plan; 7 shipped features (login, account-inquiry, approval-line, chart-of-accounts, contacts, transfer-history, erp-transaction-history) have none. Relevant ahead of the 2-day test phase.
8. **`references/migration/screen-mapping/page-mapping.md`** is a JEX→Next.js *route* map (a frontend concern). Conceptually belongs under `wecms-web/docs`; left in place pending a PO call (not moved — would write outside this tree).
9. **Naming nits (cosmetic):** `audits/vas-account.md` could be `vas-account-audit.md` for suffix consistency; `features/settings/users.md` self-identifies as slug `user-management` vs the README's `users`.
