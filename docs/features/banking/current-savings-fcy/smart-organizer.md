# Banking > Smart Organizer (FCY) — Refactor Feature Spec

> **⚠️ Superseded — preserved for history.** Smart Organizer is **one screen serving both VND and FCY**
> (currency is data-driven, not a separate screen). The primary backend documentation home is the
> 9-doc folder **[../smart-organizer/](../smart-organizer/README.md)** — see its R06 (FCY) coverage in
> [refactor-gap-analysis.md](../smart-organizer/refactor-gap-analysis.md). This single-file FCY spec
> predates that folder and is kept for traceability only.

**Module:** Banking → Current & Savings (FCY)
**Menu:** Smart Organizer (FCY)
**Route (proposed):** `/banking/smart-organizer-fcy`
**Refactor screen ID / slug:** `banking-smart-organizer-fcy`
**JEX counterpart:** `fnsh_0200_002` (view) — `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0200_002.ts` (FCY sibling of `fnsh_0200_001`); FCY source helper `fnsh_0006_001_osmc_VN.ts`.
**Status:** ✅ **Backend implemented** (shares the Smart Organizer controllers/services + migrations V172–V174 with the VND twin — see [smart-organizer-vnd.md](../current-savings-vnd/smart-organizer.md)). The FCY currency context is a presentation variant over the same backend. JEX-derived detail below is the functional spec; reconcile divergence against the shipped code.

## Overview
FCY variant of Smart Organizer — the auto-categorizing transaction view (system-assigned usage
`DISP_USAG_NM` + remark `RMRK`, governed by an Organizer Settings popup) applied to **foreign-currency**
accounts. Shares the Smart Organizer concept with the VND twin
([smart-organizer-vnd.md](../current-savings-vnd/smart-organizer.md)); differs by currency context and presentation.

- Web/UI: [`../../../../wecms-web/docs/features/banking/smart-organizer-fcy.md`](../../../../../wecms-web/docs/features/banking/current-savings-fcy/smart-organizer.md)
- Related: FCY transaction list — [transaction-history-fcy.md](transaction-history.md)

## Related APIs *(implemented — shared with VND)*
- `GET /api/v1/banking/smart-organizer` (shares the VND endpoint; FCY is a currency-context variant). Confirm the exact currency discriminator against `SmartOrganizerController`.

## Database Tables *(Needs Confirmation)*
- Same sources as Smart Organizer VND (see [smart-organizer-vnd.md](../current-savings-vnd/smart-organizer.md)); confirm against `fnsh_0200_002` IDO.

## Business Rules *(from JEX, Needs Confirmation)*
- Auto-categorization by usage/purpose; FCY currency context; multi-currency presentation likely (mirrors Account Inquiry FCY).

## Validation *(Needs Confirmation)*

## Error Cases *(Needs Confirmation)*

## Notes
Implemented on the backend alongside the VND twin (shared controllers/services + V172–V174). Remaining `Needs Confirmation` markers on table/rule detail reconcile against the shipped code in the next stabilisation pass.
