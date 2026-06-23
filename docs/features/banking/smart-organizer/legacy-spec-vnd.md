# Banking > Smart Organizer (VND) — Refactor Feature Spec

> **⚠️ Superseded — preserved for history.** The primary, current backend documentation home for
> Smart Organizer is the 9-doc folder
> **[../smart-organizer/](../smart-organizer/README.md)** (full-stack refactor complete, all six
> R01/R03/R05/R06/R07/R11 blockers CLOSED). This single-file spec predates that folder and still carries
> `Needs Confirmation` markers; read the folder instead. Content kept for traceability only.

**Module:** Banking → Current & Savings (VND)
**Menu:** Smart Organizer (VND)
**Route (proposed):** `/banking/smart-organizer`
**Refactor screen ID / slug:** `banking-smart-organizer-vnd`
**JEX counterpart:** `fnsh_0200_001` (view) / `fnsh_0200_001_r001` (read) — `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/fnsh/fnsh_0200_001.ts`.
**Status:** ✅ **Backend implemented.** `SmartOrganizerController` + `OrganizerRuleController` + their services are live (migrations V172 `smart_organizer_rmrk_org_infm_schema`, V173 `wb_get_next_val_rmrk_org_infm`, V174 `smart_organizer_view_setting_seed`). The Next.js route `app/(dashboard)/banking/smart-organizer/` also exists. The JEX-derived behavioural detail below is retained as the functional spec; reconcile any divergence against the shipped controllers. Not present in the legacy `docs/jex/screen-flows/banking.md` 9-screen list (newer feature).

## Overview
Smart Organizer is an **auto-categorizing transaction view**: it lists account transactions with a
system-assigned usage/purpose (`DISP_USAG_NM`) and a remark (`RMRK`), driven by an "Organizer
Settings" rule popup. From the JEX page (`fnsh_0200_001`):
- A sortable transaction table (`#table-list-wrap[name='SmartOrg']`) with columns including remark and display-usage name.
- An **Organizer Settings** popup (`#btn_PopSmart`, also surfaced from the empty-state banner) that configures the auto-categorization rules.
- Read service `fnsh_0200_001_r001` fetches the Smart Organizer transaction list.

- Web/UI: [`../../../../wecms-web/docs/features/banking/smart-organizer-vnd.md`](../../../../../wecms-web/docs/features/banking/smart-organizer/README.md)
- Related: VND transaction list — [transaction-history-vnd.md](../current-savings-vnd/transaction-history/README.md); FCY twin — [smart-organizer-fcy.md](legacy-spec-fcy.md)

## Related APIs *(implemented — see `SmartOrganizerController` + `OrganizerRuleController`)*
- `GET /api/v1/banking/smart-organizer` — categorized transaction list
- Organizer settings (rule) CRUD via `OrganizerRuleController` (mirrors the `fnsh_0200` settings popup). Confirm exact paths against the live controllers.

## Database Tables *(Needs Confirmation)*
- Sources the account transaction history (as Transaction History) plus a usage/purpose mapping for
  `DISP_USAG_NM`. Confirm exact tables against `fnsh_0200_001_r001` IDO before implementation.

## Business Rules *(from JEX, Needs Confirmation for refactor)*
- Auto-assigns a usage/purpose to each transaction; the Organizer Settings rules govern the mapping.
- Tenant-scoped; VND currency context.

## Validation *(Needs Confirmation)*

## Error Cases *(Needs Confirmation)*

## Notes
Smart Organizer is implemented on the backend (`SmartOrganizerController` + `OrganizerRuleController` + V172–V174) and has a Next.js route. This doc still carries `Needs Confirmation` markers on the table/business-rule detail — those should be reconciled against the shipped code in the next stabilisation pass. JEX source files (functional reference): `fnsh_0200_001.ts` (+ `fnsh_0200_002.ts`), `fnsh_0002_001_osmc_VN.ts`.
