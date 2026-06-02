# Banking > Smart Organizer (FCY) — Refactor Feature Spec

**Module:** Banking → Current & Savings (FCY)
**Menu:** Smart Organizer (FCY)
**Route (proposed):** `/banking/smart-organizer-fcy`
**Refactor screen ID / slug:** `banking-smart-organizer-fcy`
**JEX counterpart:** `fnsh_0200_002` (view) — `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0200_002.ts` (FCY sibling of `fnsh_0200_001`); FCY source helper `fnsh_0006_001_osmc_VN.ts`.
**Status:** ⚠️ **DISCOVERY — not yet in the refactor.** No endpoint/page/design exists; content derived from JEX source only. **Not a contract.**

## Overview
FCY variant of Smart Organizer — the auto-categorizing transaction view (system-assigned usage
`DISP_USAG_NM` + remark `RMRK`, governed by an Organizer Settings popup) applied to **foreign-currency**
accounts. Shares the Smart Organizer concept with the VND twin
([smart-organizer-vnd.md](../current-savings-vnd/smart-organizer.md)); differs by currency context and presentation.

- Web/UI: [`../../../../wecms-web/docs/features/banking/smart-organizer-fcy.md`](../../../../../wecms-web/docs/features/banking/current-savings-fcy/smart-organizer.md)
- Related: FCY transaction list — [transaction-history-fcy.md](transaction-history.md)

## Related APIs *(none yet — proposed)*
- `GET /api/v1/banking/smart-organizer?currency=FCY` *(proposed; may share the VND endpoint with a currency discriminator)*

## Database Tables *(Needs Confirmation)*
- Same sources as Smart Organizer VND (see [smart-organizer-vnd.md](../current-savings-vnd/smart-organizer.md)); confirm against `fnsh_0200_002` IDO.

## Business Rules *(from JEX, Needs Confirmation)*
- Auto-categorization by usage/purpose; FCY currency context; multi-currency presentation likely (mirrors Account Inquiry FCY).

## Validation *(Needs Confirmation)*

## Error Cases *(Needs Confirmation)*

## Notes
**Action required (discovery):** same as VND — confirm refactor scope before authoring a real spec.
