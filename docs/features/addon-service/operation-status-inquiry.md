# Add-on Service > Operation Status Inquiry — Backend Summary (Root)

> ⚠️ **This document is a high-level feature summary.**
> The detailed source of truth is the module-folder documentation:
> [`docs/features/addon-service/operation-status-inquiry/`](operation-status-inquiry/README.md)
> (+ the implementation code under `com.kosign.wecms.domain.operation`). When this summary and the folder docs/code disagree, the **folder docs and code win**.

**Module:** Add-on Service · **Feature slug:** `operation-status-inquiry`
**Status:** ✅ **Completed** — backend implemented end-to-end (list + inquire + save + bulk-update + Excel export), live OSMC→GDT bridge, unit tests present, frontend wired (verified 2026-06-02).
**JEX counterpart (read-only reference):** `aosv_0001_001` (list view) · `aosv_0001_002` (inquire popup).

---

## Purpose

Lists every counterparty (customer / supplier) whose **Vietnamese tax-authority operating status** has been polled into WeCMS — one row per `(BIZ_KEY, TAXCODE)` in `OPRT_STTS_INFO`. The finance team uses it to spot counterparties whose tax registration (MST) is suspended/ceased (which invalidates input-VAT credit) and to refresh that status on demand from the GDT (Tổng cục Thuế).

## Feature overview

- Tenant-scoped list with keyword (`q`, name OR tax code), raw-status filter (`companyStatus`), server-side pagination + sort.
- **Inquire** — single Tax ID lookup via the live `OsmcGdtClient` → NXISAS → GDT bridge; writes `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS` audit rows.
- **Save** — persist the looked-up row (`#create`, create-only with a `(BIZ_KEY, TAXCODE)` duplicate guard).
- **Update** — bulk re-poll + upsert (whole tenant cache or a selected subset of tax codes); per-row counts.
- **Export** — XLSX via the shared export pipeline.
- GDT free-text status is mapped to a 3-state code in Java (`resolveStatusCd`); the legacy `wb_get_operat_stts` localisation helper is intentionally not ported.

## Route / menu / permission mapping

| Concern | Value |
|---|---|
| Frontend route | `/addon/operation-status` |
| Menu permission code (`@menuAuthz` / nav tree) | `addon-operation-status-inquiry` |
| View Setting / export screenId | `addon-operation-status` |
| REST base | `/api/v1/addon/operation-status` |
| Authorization | **class-level** `@PreAuthorize("@menuAuthz.canAccess('addon-operation-status-inquiry')")` |
| Java package | `com.kosign.wecms.domain.operation` |
| Product gating | F + M only (HAENGSUNG/H omits Add-on) |

> ⚠️ Menu permission code (`addon-operation-status-inquiry`) ≠ View-Setting/export screenId (`addon-operation-status`) — do not substitute one for the other.

## Endpoints (summary)

Full request/response contracts: [operation-status-inquiry/interface-specification.md](operation-status-inquiry/interface-specification.md).

| Operation | Endpoint | Service method | Legacy mirror |
|---|---|---|---|
| List | `GET /api/v1/addon/operation-status` | `#list` | `aosv_0001_001_r001` |
| Inquire (single Tax ID GDT lookup) | `POST .../operation-status/inquire` | `#inquire` | `aosv_0001_002_r001` |
| Save (persist looked-up row) | `POST .../operation-status` | `#create` | `aosv_0001_002_c001` |
| Update (bulk re-poll + upsert) | `POST .../operation-status/update` | `#bulkUpdate` | `aosv_0001_001_u001` |
| Export (XLSX) | `POST .../operation-status/export` | `ExcelExportService` + `OperationStatusExportProvider` | refactor addition |

- **Error codes** — `WBE_OPERATION_STATUS_DUPLICATE` (dup `(BIZ_KEY,TAXCODE)` on Save), the `WBE_OSMC_*` family (`_LOOKUP_FAILED`, `_NO_RESULT`, `_NOT_CONFIGURED`, `_TRANSPORT`, `_EMPTY`, `_BAD_INPUT`), `WBE_VALIDATION`.
- **Backing tables** — `OPRT_STTS_INFO` (+ audit `EXT_TRAN_CALL_HIS`, `NXSIAS_MODULE_TRAN_LOG_HIS`).

## Current implementation status

✅ Implemented end-to-end. **Remaining items** (see [`docs/audits/addon-service-gap-analysis.md`](../../audits/addon-service-gap-analysis.md)):
- Per-row delete (`aosv_0001_002_d001`) intentionally **not ported** (the refactor page exposes no delete — matches the JEX page).
- GDT status-string localisation (`wb_get_operat_stts`) not ported (3-state Java mapping instead).
- No controller/integration test beyond the service unit test.
- A non-PROD run must point `wecms.osmc.*` away from the live Coocon gateway.

## Detailed docs (source of truth)

[operation-status-inquiry/](operation-status-inquiry/README.md) — business-flow · functional-specification · database-mapping · interface-specification · source-code-index · migration-guide · test-scenarios · refactor-gap-analysis. **Frontend counterpart:** [`wecms-web/.../operation-status-inquiry/`](../../../../wecms-web/docs/features/addon-service/operation-status-inquiry/README.md).

## Design history

Original long-form rationale: implementation plan `../../planning/addon/operation-status-inquiry-plan.md` _(removed — historical `planning/` folder deleted by project decision)_; the prior verbose version of this file (Phase-1 read-only stub notes) is preserved in git history.
