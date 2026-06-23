# Settings > Chart of Accounts — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/chart-of-accounts/`

## Feature Overview
- **Purpose:** Per-tenant chart of accounts master list used by the WABOOKS accounting / financial-report modules to classify booked transactions (banking, payment, card, e-invoice) under a hierarchical accounting code. Covers the CRUD master surface only; downstream report/transaction joins land with their owning modules.
- **Business role:** Tenant administrators (holding the `settings-core-chart-of-accounts` menu grant) maintain the per-tenant account tree. The three roots `ACC-01` (REVENUE), `ACC-02` (EXPENSES), `ACC-03` (INVESTMENT) are system-seeded and restricted (cannot be edited / deleted).

## Menu / Route Mapping
- **Menu / panel:** Settings → Chart of Accounts
- **Menu permission ID (slug):** `settings-core-chart-of-accounts` (verified against `ChartOfAccountsController` class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-chart-of-accounts')")`)
- **Wire base path:** `/api/v1/settings/chart-of-accounts`
- **Frontend mount / route:** Settings modal panel — no standalone route (`<ChartOfAccountsPanel>`, inline create / inline row edit)
- **Legacy JEX counterpart:** WSVC `comm_0223_001` (view + CRUD), `comm_0223_002` (Excel upload); table `CHART_ACCT_INFM` — legacy ids for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack, code-complete and SIT/UAT verified — 9 endpoints (list, tree, detail, create, update, bulk-upload, bulk-delete, bulk-deactivate, delete). Soft-delete (`DEL_YN`) with child re-parent, revive-on-recreate, and archive-rename-on-delete. Restricted roots blocked (`WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE`). Cycle / self-parent / depth-cap guards at write time. Server-side search + sort whitelist. Audit logging (`AUDIT_LOGS_INFM`, `WSVC_ID = "chart-of-accounts"`). PK generation funneled through `wb_get_next_val('chart_acct_infm','chart_acct_seq_no',…)`. Excel import is FE-parsed JSON → `POST /bulk-upload` (live, not inert).
- 🟡 **Partially implemented:** OQ-1 (USER-role read-only vs full CRUD) pending PO — ships without a `hasRole('ADMIN')` write gate. OQ-3 (legacy `comm_0223_001_u001_act.jsp:237-256` parent-cleanup block) dropped pending PO confirmation. Final closure pending PO ratification only.
- ⚪ **Deprecated / Legacy / Not ported:** Excel **export** not implemented (only a static template download exists; Account Inquiry is the sole `common/export` consumer). Bulk **Activate** not implemented (only bulk-deactivate; single-row re-activation via `PUT` status flip). View Setting not adopted. Downstream report (`fnsh_0200_001`) + `ACCT_TRNS_HSTR`/`CARD_TRNS_HSTR` FK validation port with their own modules.

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [chart-of-accounts/README.md](chart-of-accounts/README.md) |
| Business flow | [chart-of-accounts/business-flow.md](chart-of-accounts/business-flow.md) |
| Database mapping | [chart-of-accounts/database-mapping.md](chart-of-accounts/database-mapping.md) |
| Functional specification | [chart-of-accounts/functional-specification.md](chart-of-accounts/functional-specification.md) |
| Interface specification | [chart-of-accounts/interface-specification.md](chart-of-accounts/interface-specification.md) |
| Migration guide | [chart-of-accounts/migration-guide.md](chart-of-accounts/migration-guide.md) |
| Refactor gap analysis | [chart-of-accounts/refactor-gap-analysis.md](chart-of-accounts/refactor-gap-analysis.md) |
| Source code index | [chart-of-accounts/source-code-index.md](chart-of-accounts/source-code-index.md) |
| Test scenarios | [chart-of-accounts/test-scenarios.md](chart-of-accounts/test-scenarios.md) |

## Historical Design Notes (preserved)
- **Hierarchy depth cap is 3, not 4.** The pre-implementation design intended a 4-level cap; the shipped implementation enforces `MAX_HIERARCHY_DEPTH = 3` (level 1 reserved for the ACC-0x roots, so every user-created node has a parent — parent is **required** on Create and Update).
- **Soft-delete UK interplay (load-bearing).** The unique key `(BIZ_KEY, CHART_ACCT_CD, DEL_YN)` allows at most one active and one soft-deleted row per code. Create revives a same-code soft-deleted row (preserving its `CHART_ACCT_SEQ_NO`); Delete archive-renames the row's code (`prefix(0..4) + last5(seq_no) + 'D'`) when a deleted dup already exists. Both behaviours must be preserved together — they are the only mechanism keeping the UK from colliding over a tenant's lifetime.
- **Delete cascade re-parents children one level up** (to the deleting node's own parent); it does NOT recursively delete descendants. A deleted root's children become roots.
- **Refactor-added write-time guards** (cycle, self-parent, depth-cap) close legacy holes — legacy DEV data carries a self-reference (seq 104) and orphan parent (seq 112), grandfathered with `chart_acct_level=null`; guards apply only to new writes.
- **[Not ported]** The legacy `comm_0223_001_u001_act.jsp:237-256` "parent cleanup" block silently nullified an unrelated row's parent (reads like leftover debug instrumentation) — intentionally not ported pending PO confirmation.
