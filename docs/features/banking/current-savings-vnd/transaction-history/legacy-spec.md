> **⚠️ Superseded — preserved for history (2026-06-06).** The primary documentation home for this
> feature is now the 9-doc folder **[transaction-history-vnd/](../transaction-history/README.md)**.
> This single-file spec predates the full-stack completion: its `Status: ~25% frontend` line and the
> `banking-transaction-history-vnd` slug are **stale** — the feature is refactor-complete and the
> actual View Setting screenId / export screenId is `banking-transaction-history` (one id shared by
> VND + FCY). Read the new folder for current facts; this file is kept for traceability only.

# Banking > Transaction History (VND) — Refactor Feature Spec

**Module:** Banking → Current & Savings (VND)
**Menu:** Transaction History (VND)
**Route:** `/banking/transaction-history`
**Refactor screen ID / slug:** `banking-transaction-history-vnd`
**JEX counterpart:** `fnsh_0002_001` (view) / `fnsh_0002_001_r001` (read) — per [`docs/jex/screen-flows/banking.md`](../../../../../../docs/jex/screen-flows/banking.md).
**Status:** Backend read/summary/detail + confirm/undo + export **implemented**; frontend **~25%** (list only — `cnfm_yn`/`keyword`/`from-to` not yet forwarded, no confirm modal/bulk bar). See plan below.

> Separate menu entry per the VND Current & Savings group. Shares the backend transaction endpoint
> with the FCY twin ([transaction-history-fcy.md](../transaction-history/legacy-spec.md)); the two differ by the
> account's currency context and route, not by service.

## Overview
Per-account transaction list reached from Account Inquiry (VND) ([account-inquiry-vnd.md](../account-inquiry/legacy-spec.md))
— click an account → its transactions. Supports list (paged), per-currency/summary totals, row detail,
transaction **confirmation** (confirm / undo), and Excel export.

- API contract: `api-spec/banking/transaction-history-vnd-api.md` *(to be authored — endpoints inline below until then)*
- DB schema: see [`../../database/banking/account-inquiry-vnd-db.md`](../../../../references/database/banking/account-inquiry-vnd-db.md) (account/transaction tables) — dedicated `transaction-history` schema doc pending.
- Plan: `../../planning/phase-1/account-inquiry-and-transaction-history-plan.md` _(removed — historical `planning/` folder deleted by project decision)_
- Web/UI: [`../../../../wecms-web/docs/features/banking/transaction-history-vnd.md`](../../../../../../wecms-web/docs/features/banking/current-savings-vnd/transaction-history/README.md)

## Related APIs
- `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` — list (paged; filters `from`/`to`, `keyword`, `cnfm_yn`)
- summary + detail sub-resources (per the plan: list ✅ + summary ✅ + detail ✅)
- confirm / undo — `…/transactions` confirmation (`fnsh_0002_001_u001` parity; bulk-confirm pending)
- `POST /api/v1/banking/accounts/transactions/export` — Excel export

## Database Tables
- Account transaction-history source table(s) keyed by `(BIZ_KEY, ACCT_SEQ_NO, …)`. **Confirm exact
  table/columns** against the JEX `fnsh_0002_001_r001` IDO and the account schema doc (candidate
  `ACCT_TRNS_HSTR` + `ACCT_TRNS_RCV_PAY_INFM`; verify before implementation).

## Business Rules
- Tenant-scoped by `BIZ_KEY`; account-scoped by `{acctSeqNo}` (currency-agnostic endpoint — VND context supplied by the originating account).
- Confirmation toggles a `cnfm_yn` flag with undo; bulk-confirm not yet implemented.
- List + export share one query path (parity with Account Inquiry's list/export sharing).

## Validation
- Date range (`from ≤ to`); pagination clamped; `cnfm_yn` constrained to known values.

## Error Cases
- Standard envelope `{ status, data, … }`; `WBE_VALIDATION` / `WBE9999`; account-not-found on bad `acctSeqNo`.

## Notes
JEX twin citation: the AI/TH plan loosely cites `trns_0001_001`; the authoritative screen map
(`jex/screen-flows/banking.md`) lists VND Transaction History as `fnsh_0002_001` — used here.
