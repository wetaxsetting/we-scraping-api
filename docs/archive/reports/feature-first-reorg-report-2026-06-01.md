# Feature-First Documentation Reorg Report — `wecms-api/docs/`

**Date:** 2026-06-01
**Scope:** Reorganize `wecms-api/docs/` into a feature-first structure keyed off the live WeCMS menu.
**Method:** Audit → deterministic move/dedup script → relative-link rewrite (resolve-against-old, recompute-from-new) → README authoring → link validation. `docs/` is untracked in git, so moves were plain filesystem renames.

## Totals

| Action | Count |
|---|---|
| Files moved (relocated, same name) | 102 |
| Files renamed (moved + new name) | 9 |
| Files archived | 19 |
| Duplicate docs removed (merged to survivor) | 21 |
| Files containing rewritten links | 174 |
| README files created | 7 |
| README files rewritten | 5 |
| README link-fixes (sub-folder) | 2 |

## Renames (9)

- `features/admin/user-management.md` → `features/settings/users.md`
- `features/settings/erp-transaction-history.md` → `features/settings/transaction-history.md`
- `features/settings/purpose-settings.md` → `features/settings/purpose.md`
- `features/banking/account-inquiry-vnd.md` → `features/banking/current-savings-vnd/account-inquiry.md`
- `features/banking/transaction-history-vnd.md` → `features/banking/current-savings-vnd/transaction-history.md`
- `features/banking/smart-organizer-vnd.md` → `features/banking/current-savings-vnd/smart-organizer.md`
- `features/banking/account-inquiry-fcy.md` → `features/banking/current-savings-fcy/account-inquiry.md`
- `features/banking/transaction-history-fcy.md` → `features/banking/current-savings-fcy/transaction-history.md`
- `features/banking/smart-organizer-fcy.md` → `features/banking/current-savings-fcy/smart-organizer.md`

## Reference relocations (prefix moves under `references/`)

`api-spec/`, `database/`, `business-rules/`, `migration/`, `audits/` → `references/<same>/` (with substructure preserved).

## Settings consolidation → `features/settings/`

Detailed "Refactor Feature Spec" docs are now canonical, one per Settings screen:
`purpose`, `vas-account`, `contacts`, `approval-line`, `chart-of-accounts`, `login-credentials`,
`bank-account`, `preferences`, `users`, `transaction-history`, `firm-banking-settings`,
`firm-banking-history` (+ `change-password` ProfileMenu flow). Source docs were pulled in from
`features/accounting/`, `features/approval/`, `features/contacts/`, `features/admin/`, and the
`settings/` hub folder.

## Archived (19) → `archive/`

- `archive/settings-menu-hubs/` — 10 concise per-screen menu-index stubs (value folded into `features/settings/README.md`).
- `archive/dissolved-feature-indexes/` — 4 README stubs from dissolved `features/{accounting,approval,contacts,admin}/`.
- `archive/features/sales-and-expense-einvoices.md` — combined doc superseded by the addon-service split.
- `archive/reports/` — 2 historical housekeeping reports + this report.
- `archive/shared-pointers/` — `bug-reports/` + `change-log/` workspace redirect stubs.

## Duplicates removed (21) — confirmed link-depth/exact dups, inbound links retargeted to survivor

- `audits/migration-audit/` 8 files + README (twins of `audits/` root) → `references/audits/`; unique `nxisas-vcb-spike.md` + `transfer-history-audit-2026-05-20.md` preserved.
- `migration/api-mapping/` 7 files (staler twins referencing obsolete `backend/` paths) → root `references/migration/` + `references/api-spec/api-mapping.md`.
- `database/db-mapping.md` → `references/database/table-mapping/db-mapping.md`.
- `planning/` 4 root duplicates → `planning/phase-1/` + `planning/roadmap/`.

## READMEs

- **Created (7):** `features/banking/{current-savings-vnd,current-savings-fcy,own-account-transfer,reports}/README.md`, `architecture/{backend,integrations,database}/README.md`.
- **Rewritten (5):** root `README.md`, `features/README.md`, `features/settings/README.md`, `references/README.md`, `architecture/README.md`.
- **Link-fixed (2):** `references/migration/{jex-analysis,screen-mapping}/README.md` (stale `../api-mapping/` dir links → the relocated mapping docs).

Each group README states what the folder contains, how it maps to the WeCMS menu, the related
API/DB/business-rule/migration/audit docs, and links to child pages.

## Open questions — preserved, not resolved

- `planning/open-questions/documentation-follow-up.md` — kept; OQ-DOC items untouched.
- `planning/open-questions/banking-reports-remaining-scope.md` — kept.

## Validation

| Metric | Baseline (pre) | After | Δ |
|---|---|---|---|
| Files scanned | 238 | 224 | −14 (−21 dup deletes +7 new READMEs) |
| Relative links checked | 4,439 | 4,096 | — |
| **Dangling links** | 367 | **322** | **−45** |
| Invalid `src/main` (code) refs | 10 | **7** | −3 |

- **New dangling links introduced: 0.** Verified by resolved-target set comparison between the
  pre-migration tree and a sibling-depth migrated probe: every previously-working link still resolves;
  every "introduced" entry was a pre-existing broken link relocated with its file.
- **Remaining 322 dangling are all pre-existing**, dominated by: 134 legacy `backend/`/`frontend/`
  paths (from the deprecated `docs/refactor-wecms/` layout), 34 `wecms-web` frontend links, 10
  cross-system `docs/` pointers with wrong depth, ~136 never-created sibling stubs + malformed
  parenthesised `Claude Code (AI)/` paths.

## Remaining documentation gaps (pre-existing, out of scope for this reorg)

- Legacy `../backend/` / `../frontend/` cross-links inside moved files (these folders never existed
  at the post-relocation depth). Systematically fixable in a follow-up pass (`backend/` ≈ `business-rules/`).
- Cross-system pointers (`_shared/`, `docs/jex/`) with depth that predates the 2026-06-01 relocation.
- Several feature docs reference sibling stubs that were never authored (`login-auth.md`,
  `screen-options-view-setting.md`, etc.).
