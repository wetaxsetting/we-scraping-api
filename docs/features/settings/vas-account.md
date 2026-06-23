# Settings > VAS Account — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/vas-account/`

## Feature Overview
- **Purpose:** VAS Account ("Value-Added Service" accounting code) is a per-tenant flat list of short accounting codes (e.g. `111` "Tiền mặt") that downstream banking surfaces reference by surrogate key (`VAS_ACCT_SEQ_NO`). It is **not** a hierarchy — the table has no parent FK, no display-order column, and no depth/cycle rules; the two-level tree shown on screen is a client-side display-only prefix grouping.
- **Business role:** A tenant administrator maintains the VAS code list from the Settings panel; the codes are consumed by bank-account tagging, transaction categorization, Smart Organizer, and the Edit Bank Account dialog (via the lookup picker).

## Menu / Route Mapping
- **Menu / panel:** Settings → VAS Account (Settings modal panel; no standalone route)
- **Menu permission ID (slug):** `settings-core-vas-account` (`@PreAuthorize("@menuAuthz.canAccess('settings-core-vas-account')")`, class-level on the controller)
- **Wire base path:** `/api/v1/settings/vas-account`
- **Frontend mount / route:** `<VasAccountPanel>` inside `<SettingsModal>`
- **Legacy JEX counterpart:** WSVC family `comm_0217_*` (`_001` list/delete · `_002` Excel upload · `_003` create · `_004` detail/update) + `comm_1002_001` (lookup picker); table `BUSI_VAS_ACCT_INFM`. (Legacy ids cited for traceability only. Note: not `comm_0223`, which is Chart of Accounts.)

## Current Status
- ✅ **Implemented:** Full-stack — list (search + Active/Inactive status filter), active-only lookup picker, detail, create, update (status A↔I happens here), soft-delete (`DEL_YN='Y'`), and Excel preview + import (Append / Replace modes). Sequence generation routes through `LegacyFunctionRepository.nextVasAcctSeqNo` (the `wb_get_next_val` funnel, per-`BIZ_KEY` MAX+1). Audit-log row per write (`wsvc_id='vas-account'`). Legacy `comm_0217_*` parity verified; SIT/UAT completed. Unit / controller / Excel / export-provider tests green.
- 🟡 **Partially implemented:** The XLSX **export** endpoint + `VasAccountExportProvider` are implemented and tested on the backend, but the frontend panel surfaces no export button yet (OQ-VAS-1 — panel shows Excel **Upload** only). `VasAccountIntegrationTest` is `@Disabled` pending Testcontainers/JWT-mock infra (F1 — test-infra gap only, not a functional gap). Sequence generation has no one-shot retry on a concurrent PK race — surfaces `WBE_DB` (OQ-VAS-2, accepted for R&D).
- ⚪ **Deprecated / Legacy / Not ported:** View Setting is NOT adopted (no `SYS_SCR_OPTION_WECMS` seed, no popover; `vas-account` is the reserved screenId). No bulk activate/deactivate and no separate status-change endpoint (status is an ordinary Create/Update field — absent in legacy too). No bulk delete and no hard delete (legacy `D001` IDO exists but is unused; refactor soft-deletes only). No backend template-download endpoint (template is a static frontend XLSX). The legacy per-cell re-validate endpoint (`comm_0217_002_r002`) was dropped — no inline cell editing in the preview grid.

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [vas-account/README.md](vas-account/README.md) |
| Business flow | [vas-account/business-flow.md](vas-account/business-flow.md) |
| Database mapping | [vas-account/database-mapping.md](vas-account/database-mapping.md) |
| Functional specification | [vas-account/functional-specification.md](vas-account/functional-specification.md) |
| Interface specification | [vas-account/interface-specification.md](vas-account/interface-specification.md) |
| Migration guide | [vas-account/migration-guide.md](vas-account/migration-guide.md) |
| Refactor gap analysis | [vas-account/refactor-gap-analysis.md](vas-account/refactor-gap-analysis.md) |
| Source code index | [vas-account/source-code-index.md](vas-account/source-code-index.md) |
| Test scenarios | [vas-account/test-scenarios.md](vas-account/test-scenarios.md) |

## Historical Design Notes (preserved)
- **Three commonly-assumed-but-false premises** (confirmed against code + the live DB): (1) there is **no parent-child hierarchy** in the DB or backend — the table is flat; the indented tree is purely client-side prefix grouping. (2) There is **no bulk activate/deactivate and no separate status endpoint** — status is a field on Create/Update. (3) The legacy WSVC family is **`comm_0217_*`**, not `comm_0223` (that id is Chart of Accounts, a different table with a real hierarchy).
- **Soft delete does NOT cascade.** `BUSI_VAS_ACCT_INFM` is a parent for `ACCT_MSTR` / `ACCT_TRNS_HSTR` / `ACCT_TRNS_RCV_PAY_INFM` via `vas_acct_seq_no`; downstream rows continue to reference soft-deleted parents — legacy behaviour, preserved.
- **Wire-shape note:** `vas_acct_seq_no` ships as a **string** on the full row (avoids JS number-precision loss) but as a **number** on the lookup row — both intentional.
