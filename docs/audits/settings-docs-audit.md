# Settings Documentation Audit — 2026-06-23

> Module-level documentation audit for the **Settings** menu group, created to complete the
> per-module audit-first set (Banking / Payment / Common / Add-on already had one; Settings did not).
> **Latest code = source of truth.** Companion files: [settings-source-index.md](settings-source-index.md) · [settings-gap-analysis.md](settings-gap-analysis.md).
> Gap vocabulary used throughout: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**.

## Scope

- **API:** `wecms-api/docs/features/settings/**` — 12 leaf module folders, each carrying the standard 9-doc set
  (README · business-flow · database-mapping · functional-specification · interface-specification ·
  migration-guide · refactor-gap-analysis · source-code-index · test-scenarios).
- **WEB:** `wecms-web/docs/features/settings/**` — 12 leaf module folders, each carrying the 6-doc set
  (README · screen-flow · ui-specification · component-index · api-consumption · test-scenarios).
- Methodology mirrors the Banking / Payment / Add-on doc audits.

## Module inventory (12 Settings submenus)

| # | Settings submenu | Leaf folder (`features/settings/`) | Flat summary sibling | Status |
|---|---|---|---|---|
| 1 | Purpose | `purpose/` | `purpose.md` | Backend implemented (+ tests) |
| 2 | VAS Account | `vas-account/` | `vas-account.md` | Implemented full-stack |
| 3 | Contacts | `contacts/` | `contacts.md` | Backend implemented |
| 4 | Approval Line | `approval-line/` | `approval-line.md` | Backend implemented (+ tests) |
| 5 | Chart of Accounts | `chart-of-accounts/` | `chart-of-accounts.md` | Backend implemented |
| 6 | Bank Account | `bank-account/` | `bank-account.md` | Backend implemented; FE present |
| 7 | Login Credentials | `login-credentials/` | `login-credentials.md` | Implemented full-stack |
| 8 | Preferences | `preferences/` | `preferences.md` | Backend implemented; FE present |
| 9 | Transaction History (ERP) | `transaction-history/` | `transaction-history.md` | Working (read-only ledger + export) |
| 10 | Firm Banking Settings | `firm-banking-settings/` | `firm-banking-settings.md` | Backend implemented (+ tests) |
| 11 | Firm Banking History | `firm-banking-history/` | `firm-banking-history.md` | Backend implemented (+ tests, export) |
| 12 | User Management | `user-management/` | `users.md` | Implemented full-stack (legacy parity 2026-06-05) |

Additional flat reference summaries present under `features/settings/`: `change-password.md`
(auth-adjacent), `environment-settings.md` (env-set seeding leg). Both are out-of-menu reference docs,
not Settings submenu leaves.

## Structure decision

- All 12 submenu folders already carry the full 9-doc (API) / 6-doc (WEB) set — **no required files were missing.**
- The old flat single-file specs (`purpose.md`, `bank-account.md`, …) are retained as **redirect summaries**
  carrying the standard ⚠️ banner pointing at the authoritative leaf folder — the same pattern used by
  Payment (`card-transactions.md` → `corporate-card/`). This satisfies the root-doc → summary requirement.
- The `users.md` flat summary correctly points at the `user-management/` folder (slug differs from filename
  by design — the JEX menu label is "Users", the refactor slug is `user-management`). Documented, not a defect.

## Summary (doc-vs-code reconciliation)

| Module | Doc status |
|---|---|
| Purpose | **Accurate** — `BUSI_USAG_SET_INFM` CRUD + reorder + Excel import; `v_purpose_in_use` `@Subselect` in-use guard |
| VAS Account | **Accurate** — full-stack; CRUD + lookup + Excel preview/import + export |
| Contacts | **Accurate** — `TRCO_INFM` (+ addr/acct/ovrs/pgro/file) CRUD + bulk-delete + attachments; View Setting adopted (V154/V187) |
| Approval Line | **Accurate** — `APPR_LINE_INFM(_DETL)` master/child CRUD with diff-sync; 5-approver cap; no audit-log (legacy parity); View Setting NOT adopted |
| Chart of Accounts | **Accurate** — `CHART_ACCT_INFM` CRUD + tree; restricted roots; 4-level cap; audit-log enabled. OQ-1 (USER role) / OQ-3 (parent-cleanup) pending PO |
| Bank Account | **Accurate** — backend (2 services) + FE Edit dialog |
| Login Credentials | **Accurate** — bank/GDT CRUD + reveal + audit-logs; base path `/api/v1/login-credentials`; cert cipher = legacy pgcrypto (cross-system compatible). **See gap S-1 (missing audit file).** |
| Preferences | **Accurate** — backend + FE read/edit toggle |
| Transaction History (ERP) | **Accurate** — read-only `ERP_TRNS_HSTR` ledger + download + export; View Setting NOT adopted (no toolbar list-icon in legacy) |
| Firm Banking Settings | **Accurate** — list/detail/CRUD/audit; audit `WSVC_ID` slug = `firm-banking-settings` |
| Firm Banking History | **Accurate** — `@Subselect` over legacy base tables; list + XLSX export; `FirmBankingSvcType` enum |
| User Management | **Accurate** — 11 endpoints; menu-entitlement via `USER_MENU_AUTH_INFM`; SHA-256 coexistence passwords; avatar upload |

## Findings

- **S-1 (✅ RESOLVED 2026-06-23).** The Login Credentials docs (`login-credentials/README.md`,
  `migration-guide.md`, `source-code-index.md`) plus `wecms-api/CLAUDE.md` and project memory referenced
  `docs/audits/login-credentials-legacy-vs-refactor-audit.md`, which was **absent**. Verified that
  `login-legacy-compatibility-2026-06-10.md` is a **different** document (auth password coexistence, not the
  cert feature) → **Option B: created** the dedicated audit
  [login-credentials-legacy-vs-refactor-audit.md](login-credentials-legacy-vs-refactor-audit.md) in the
  existing audit format; all dangling references now resolve. See [settings-gap-analysis.md](settings-gap-analysis.md) S-1.
- **S-2 (structure, resolved-by-design).** `users.md` filename vs `user-management/` folder slug — intentional;
  the flat summary banner already points at the folder. No action.
- **No Settings module audit existed before this pass.** This file + its two companions close that gap so all
  five modules (Banking, Payment, Common, Add-on, Settings) now have a module-level audit triplet under `docs/audits/`.

## Process notes

- Docs-only. **No application code modified.**
- Module statuses sourced from `wecms-api/CLAUDE.md` Feature-Status table (evidence-based reconciliation, 2026-06-02
  onward) and the per-module `refactor-gap-analysis.md` files.
- Created as part of the Phase-2 documentation governance pass (Banking link-drift repair + Banking/Payment audit
  relocation into `docs/audits/` + this Settings triplet).
