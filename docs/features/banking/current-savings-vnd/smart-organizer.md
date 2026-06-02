# Banking > Smart Organizer (VND) — Refactor Feature Spec

**Module:** Banking → Current & Savings (VND)
**Menu:** Smart Organizer (VND)
**Route (proposed):** `/banking/smart-organizer`
**Refactor screen ID / slug:** `banking-smart-organizer-vnd`
**JEX counterpart:** `fnsh_0200_001` (view) / `fnsh_0200_001_r001` (read) — `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0200_001.ts`.
**Status:** ⚠️ **DISCOVERY — not yet in the refactor.** No Spring Boot endpoint, no Next.js page, no refactor design doc existed before this scaffold. Content below is derived from the JEX source only; **do not treat as a contract.** Not present in the legacy `docs/jex/screen-flows/banking.md` 9-screen list (newer feature).

## Overview
Smart Organizer is an **auto-categorizing transaction view**: it lists account transactions with a
system-assigned usage/purpose (`DISP_USAG_NM`) and a remark (`RMRK`), driven by an "Organizer
Settings" rule popup. From the JEX page (`fnsh_0200_001`):
- A sortable transaction table (`#table-list-wrap[name='SmartOrg']`) with columns including remark and display-usage name.
- An **Organizer Settings** popup (`#btn_PopSmart`, also surfaced from the empty-state banner) that configures the auto-categorization rules.
- Read service `fnsh_0200_001_r001` fetches the Smart Organizer transaction list.

- Web/UI: [`../../../../wecms-web/docs/features/banking/smart-organizer-vnd.md`](../../../../../wecms-web/docs/features/banking/current-savings-vnd/smart-organizer.md)
- Related: VND transaction list — [transaction-history-vnd.md](transaction-history.md); FCY twin — [smart-organizer-fcy.md](../current-savings-fcy/smart-organizer.md)

## Related APIs *(none yet — proposed)*
- `GET /api/v1/banking/smart-organizer` — categorized transaction list *(proposed)*
- Organizer settings CRUD *(proposed — mirrors the `fnsh_0200` settings popup)*

## Database Tables *(Needs Confirmation)*
- Sources the account transaction history (as Transaction History) plus a usage/purpose mapping for
  `DISP_USAG_NM`. Confirm exact tables against `fnsh_0200_001_r001` IDO before implementation.

## Business Rules *(from JEX, Needs Confirmation for refactor)*
- Auto-assigns a usage/purpose to each transaction; the Organizer Settings rules govern the mapping.
- Tenant-scoped; VND currency context.

## Validation *(Needs Confirmation)*

## Error Cases *(Needs Confirmation)*

## Notes
**Action required (discovery):** confirm refactor scope for Smart Organizer — is it in scope for the
R&D refactor, or static-UI/deferred? Then promote this scaffold to a real spec. JEX source files:
`fnsh_0200_001.ts` (+ `fnsh_0200_002.ts`), `fnsh_0002_001_osmc_VN.ts`.
