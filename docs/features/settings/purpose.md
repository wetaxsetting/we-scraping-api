# Settings > Purpose — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/purpose/`

## Feature Overview
- **Purpose:** Purpose is the per-tenant master list of transaction purposes (legacy column `USE_USAG_CD` — "Use/Usage Code"). A purpose tags every payment / transfer / expense write so the tenant can categorize cash flow for reporting and reconciliation. The hierarchy is fixed at two levels — a level-1 parent may carry any number of level-2 children, but children never carry grandchildren.
- **Business role:** Used by tenant administrators to list/search, create, update, soft-delete, reorder, and bulk-upload (Excel) the purposes that populate payment/transfer dropdowns across the product. Read access is broad (every module surfaces purposes in its dropdowns).

## Menu / Route Mapping
- **Menu / panel:** Settings → Purpose (Settings modal panel; no standalone route)
- **Menu permission ID (slug):** `settings-core-purpose` (`@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")`, class-level on the controller)
- **Wire base path:** `/api/v1/settings/purpose-settings`
- **Frontend mount / route:** `<PurposePanel>` inside `<SettingsModal>`
- **Legacy JEX counterpart:** WSVC family `comm_0187_001` (+ `_r002` / `_c001` / `_u002` / `_u004`) and `comm_0187_004` (Excel); table `BUSI_USAG_SET_INFM`. (Legacy ids cited for traceability only.)

## Current Status
- ✅ **Implemented:** Full-stack — list + keyword search, detail, create, update, soft-delete (`USE_YN='N'`), reorder, and Excel preview + import (8 endpoints). Two-level hierarchy enforcement, reserved-system-code exclusion, PK generation via `wb_get_next_val('busi_usag_set_infm','use_usag_cd', …)` through the `LegacyFunctionRepository` funnel, recursive delete guard (`WBE1199`), and audit-log writes (`WSVC_ID = 'purpose-settings'`) are all in place. Unit + controller tests green; legacy parity verified; SIT/UAT verified.
- 🟡 **Partially implemented:** `disp_ord` next-value is computed in-service rather than via the DB function (documented exception — same result). View Setting is intentionally NOT adopted (the panel is the approved modal baseline with no toolbar list-icon button); the slug is reserved should a future Figma frame add the popover.
- ⚪ **Deprecated / Legacy / Not ported:** Reset-to-system-defaults (`comm_0187_001_u003`), Unused/restore "trash bin" (`comm_0187_003_*`), and the language-detail table `BUSI_USAG_SET_INFM_LANG_DETL` (single-table model is authoritative) are not ported. The legacy `USAG_GRP_CD` filter is dropped on the wire (column retained in DB for legacy compatibility, scoped to `IS NULL`). Purpose-Account mapping (`BUSI_USAG_ACCT_INFM_DETL`) is a separate feature, out of scope.

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [purpose/README.md](purpose/README.md) |
| Business flow | [purpose/business-flow.md](purpose/business-flow.md) |
| Database mapping | [purpose/database-mapping.md](purpose/database-mapping.md) |
| Functional specification | [purpose/functional-specification.md](purpose/functional-specification.md) |
| Interface specification | [purpose/interface-specification.md](purpose/interface-specification.md) |
| Migration guide | [purpose/migration-guide.md](purpose/migration-guide.md) |
| Refactor gap analysis | [purpose/refactor-gap-analysis.md](purpose/refactor-gap-analysis.md) |
| Source code index | [purpose/source-code-index.md](purpose/source-code-index.md) |
| Test scenarios | [purpose/test-scenarios.md](purpose/test-scenarios.md) |

## Historical Design Notes (preserved)
A handful of design decisions are worth keeping as rationale; the implementation detail behind each now lives in the folder:

- **Recursive delete (OQ-5).** Delete is a hard-fail (no soft-warn, no force-delete) when the purpose OR any of its active children is referenced by an active banking-transaction-history / payment / transfer record. Source: Figma comments — *"If the child data is used in other areas, does that mean the parent cannot be deleted?" → "Yes it is."* The wire code `WBE1199` broadened to cover both the legacy "has active children" case and the transaction-reference case under one localized message.
- **Error-code policy (OQ-8).** No `WBE_PURPOSE_*` family was introduced — the feature reuses legacy/generic codes only (`WBW1152`, `WBW1138`, `WBE1199`, `WBE_VALIDATION`, `WBE1009`, `WBE9999`). New Purpose-specific codes would need an explicit PO/Sonny request.
- **[Legacy JEX]** Legacy enforced admin-only access via menu visibility (`USER_MENU_AUTH_INFM`); the refactor enforces the equivalent boundary explicitly via the `@menuAuthz.canAccess('settings-core-purpose')` gate.
