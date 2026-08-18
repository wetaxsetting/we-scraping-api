# Settings > Bank Account — Feature Summary (Refactor)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/bank-account/`

## Feature Overview
- **Purpose:** Per-tenant master of registered bank-account rows (`ACCT_MSTR`) — the same accounts surfaced in Banking → Account Inquiry, here exposed with their maintenance fields (nickname, description, account type, maturity, VAS-account mapping) plus per-user View / Withdrawal permissions and a per-account audit-log popup.
- **Business role:** A tenant administrator lists active accounts, edits one account's maintenance fields, bulk-registers accounts from a Coocon scrape (the Login Credentials Add-chain), manages replace-semantics View/Withdrawal grantee sets, and reads an account's audit history. There is no create-from-scratch and no delete on this screen.

## Menu / Route Mapping
- **Menu / panel:** Settings → Bank Account (Settings modal panel — no standalone route)
- **Menu permission ID (slug):** `settings-core-bank-account` (class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-bank-account')")` on `SettingsBankAccountController`)
- **Wire base path:** `/api/v1/settings/bank-account`
- **Frontend mount / route:** Settings modal panel key `bank-account`
- **Legacy JEX counterpart:** WSVC family `comm_0183_001` (list) · `comm_0183_002` (permissions) · `comm_0183_003` (detail/edit) · `comm_0183_004` (audit logs); tables `ACCT_MSTR` + `ACCT_AUTH_INFO` — legacy ids cited for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack. Six endpoints — GET (list, View Setting sort) · `PATCH /{bankCd}/{acctNo}` (update incl. FK-validated VAS mapping) · `POST /register` (bulk insert, silent dup-skip) · `GET /{bankCd}/{acctNo}/permissions?type=` (roster) · `PUT /{bankCd}/{acctNo}/permissions` (replace-semantics diff, soft-revoke via `USE_YN='N'`, admin-exclusion, self-revoke guard, 5-bank withdrawal whitelist → `WBE0452`) · `GET /{bankCd}/{acctNo}/audit-logs` (reads both slug `bank-account` and legacy `comm_0183_001` for shared-DB coexistence). Frontend wired with no mock fallback for the primary read. Backend tests present (3 JUnit classes / 11 `@Test`).
- 🟡 **Partially implemented:** `AUTH_TYPE='F'` (FULL_ACCESS) round-trips in the enum + DB but is rejected by the permission endpoints (dialog is View/Withdrawal only) — no UI surface (GAP-2). `vas_account_bank` ships the compact `"VAS_ACCT_NM(CCY)"` echo; the richer `"BANK_NM(CCY)"` join is deferred (GAP-3).
- ⚪ **Deprecated / Legacy / Not ported:** Account de-registration / `ACTV_YN` flip — no `DELETE` endpoint; accounts arrive via `register` and the list scopes to `ACTV_YN='Y'` (GAP-1, DEAD LEGACY by design).

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [bank-account/README.md](bank-account/README.md) |
| Business flow | [bank-account/business-flow.md](bank-account/business-flow.md) |
| Database mapping | [bank-account/database-mapping.md](bank-account/database-mapping.md) |
| Functional specification | [bank-account/functional-specification.md](bank-account/functional-specification.md) |
| Interface specification | [bank-account/interface-specification.md](bank-account/interface-specification.md) |
| Migration guide | [bank-account/migration-guide.md](bank-account/migration-guide.md) |
| Refactor gap analysis | [bank-account/refactor-gap-analysis.md](bank-account/refactor-gap-analysis.md) |
| Source code index | [bank-account/source-code-index.md](bank-account/source-code-index.md) |
| Test scenarios | [bank-account/test-scenarios.md](bank-account/test-scenarios.md) |

## Historical Design Notes (preserved)
- **[Legacy JEX]** `AUTH_TYPE` is `CHECK IN ('V','W','F')` on `ACCT_AUTH_INFO`; `V` = VIEW, `W` = WITHDRAW are independently holdable. The meaning of `F` was an open item in the old flat file — now resolved as FULL_ACCESS, carried for legacy DEV-data compatibility but not exposed (see GAP-2).
- The old flat file's "Batch A / Batch B" framing (permissions called "Batch B placeholder", per-row audit-log READ called a mock) is **stale and removed** — permissions, audit-logs, and the per-row permission labels are all implemented. (Residual stale Javadoc references to "Batch B" / migration "V245"/"V127" are tracked as non-blocking cleanup in the folder gap analysis; the live schema migration is `V1__acct_auth_info.sql`.)
