# Banking > Transaction History (FCY) — Refactor Feature Spec

**Module:** Banking → Current & Savings (FCY)
**Menu:** Transaction History (FCY)
**Route:** `/banking/transaction-history-fcy/[slug]`
**Refactor screen ID / slug:** `banking-transaction-history-fcy`
**JEX counterpart:** `fnsh_0006_001` (view) / `fnsh_0006_001_r001` (read) — per [`docs/jex/screen-flows/banking.md`](../../../../../docs/jex/screen-flows/banking.md).
**Status:** Backend shared with VND (read/summary/detail/confirm/export implemented); frontend FCY client still uncontrolled (~early).

> Separate menu entry under the FCY Current & Savings group. **Shares the backend transaction endpoint
> with the VND twin** ([transaction-history-vnd.md](../current-savings-vnd/transaction-history.md)); differs by the
> account's foreign-currency context, the route, and multi-currency presentation.

## Overview
Per-account transaction list reached from Account Inquiry (FCY) ([account-inquiry-fcy.md](account-inquiry.md))
— click an FCY account → its transactions. Same capabilities as VND (list, summary, detail,
confirm/undo, export) with foreign-currency formatting.

- API contract: `api-spec/banking/transaction-history-fcy-api.md` *(to be authored — shares the VND endpoint)*
- DB schema: see [`../../database/banking/account-inquiry-fcy-db.md`](../../../references/database/banking/account-inquiry-fcy-db.md); dedicated transaction-history schema doc pending.
- Plan: [`../../planning/phase-1/account-inquiry-and-transaction-history-plan.md`](../../../planning/phase-1/account-inquiry-and-transaction-history-plan.md)
- Web/UI: [`../../../../wecms-web/docs/features/banking/transaction-history-fcy.md`](../../../../../wecms-web/docs/features/banking/current-savings-fcy/transaction-history.md)

## Related APIs
- `GET /api/v1/banking/accounts/{acctSeqNo}/transactions` — list (same endpoint as VND; the FCY account drives currency)
- summary + detail sub-resources; confirm / undo; `POST /api/v1/banking/accounts/transactions/export`

## Database Tables
- Same account transaction-history source as VND (see [transaction-history-vnd.md](../current-savings-vnd/transaction-history.md) → Database Tables). **Confirm exact table/columns** against `fnsh_0006_001_r001`.

## Business Rules
- Tenant + account scoped; currency taken from the account, not a query param.
- Multi-currency totals presentation differs from VND (mirrors the Account Inquiry FCY multi-line subheader pattern).

## Validation
- Date range, pagination clamp, `cnfm_yn` as in VND.

## Error Cases
- Standard envelope; `WBE_VALIDATION` / `WBE9999`; account-not-found.

## Notes
Backend is a single account-scoped service shared with VND; this doc exists because FCY is a distinct
menu entry and a distinct JEX screen (`fnsh_0006_001`).
