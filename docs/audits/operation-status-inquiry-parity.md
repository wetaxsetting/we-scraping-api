# Operation Status Inquiry — Refactor Gap Analysis / Parity Audit

**Slug:** `operation-status-inquiry` · **JEX:** `aosv_0001_*` · **Refactor:** `domain/operation` + `wecms-web/components/addon/operation-status`
**Audit date:** 2026-06-03 · **Re-audit + Save/persist fix:** 2026-06-03 · **Method:** legacy source vs. refactor source, file-by-file.

> **Consolidated feature docs:** see [../features/addon-service/operation-status-inquiry/](../features/addon-service/operation-status-inquiry/) — especially [refactor-gap-analysis.md](../features/addon-service/operation-status-inquiry/refactor-gap-analysis.md). This file remains the detailed parity audit.

> **2026-06-03 update.** The one functional gap (Inquire-popup Save / persist,
> legacy `aosv_0001_002_c001`) is now **implemented** end-to-end:
> `POST /api/v1/addon/operation-status` (`OperationStatusController#create` →
> `OperationStatusService#create`), frontend `createOperationStatus` +
> `InquireOperationStatusDialog.handleSave`. The stale entity/service Javadoc
> flagged in §4 has been corrected in the same change. Remaining items are
> non-blocking divergences (§3) only.

Legacy source-of-truth: [`docs/jex/features/operation-status-inquiry/`](../../../docs/jex/features/operation-status-inquiry/README.md).
Migration mapping: migration/operation-status-inquiry.md _(removed — historical `migration/` folder deleted by project decision)_.

---

## 1. Coverage table

| Legacy capability | Status | Evidence (refactor) |
|---|---|---|
| List read (`aosv_0001_001_r001`) — paged/filtered/sorted grid of `OPRT_STTS_INFO` | ✅ Done | `OperationStatusController#list`; `OperationStatusService#list`; `OprtSttsInfoRepository#findFiltered` |
| Keyword search (taxcode OR companyname) | ✅ Done | `OprtSttsInfoRepository#findFiltered` (LIKE w/ `ESCAPE '!'`); `OperationStatusClient` debounced `q` |
| Status-filter dropdown (raw VN substring partial match) | ✅ Done | `OperationStatusFilter.companyStatus`; `OperationStatusPage` StatusFilterPopover |
| Sort (4 data columns) | ✅ Done | `OperationStatusSortField` whitelist (COMPANY/ADDRESS/OPERATION_STATUS/LAST_UPDATE); default `SEARCHDATETIME DESC, TAXCODE ASC` |
| Server-side pagination | ✅ Done (refactor improvement) | `page`/`size` clamped to 100; legacy was `PageInfo` paged too |
| Excel export | ✅ Done (refactor-only endpoint) | `POST /export`; `OperationStatusExportProvider` (`screenId=addon-operation-status`) |
| Print | ✅ Done (frontend, in-memory) | `OperationStatusPage.printReport` |
| Bulk Update / GDT re-poll (`aosv_0001_001_u001`) + upsert | ✅ Done | `OperationStatusService#bulkUpdate`; `OsmcGdtClient.lookup`; upserts `OPRT_STTS_INFO` |
| Multi-select bulk update (selected rows only) | ✅ Done | `UpdateOperationStatusRequest.taxCodes`; selection persists across pages |
| Live single lookup (`aosv_0001_002_r001`) | ✅ Done | `#inquire`; `OsmcGdtClient`; `InquireOperationStatusDialog` |
| **Popup Save / persist (`aosv_0001_002_c001`)** | ✅ **Done** (2026-06-03) | `OperationStatusController#create` (`POST /operation-status`) → `OperationStatusService#create`: bean-validated required fields, `existsById` duplicate guard → `WBE_OPERATION_STATUS_DUPLICATE`, `stampAudit` + `repo.save`, `DataIntegrityViolationException` race → same duplicate error. Frontend `createOperationStatus` + `InquireOperationStatusDialog.handleSave`. Tests: `OperationStatusServiceTest` (success / dup / race) |
| `EXT_TRAN_CALL_HIS` audit | ✅ Done | reused `banking.synchistory`; pre-call insert + post-call patch |
| `NXSIAS_MODULE_TRAN_LOG_HIS` log | ✅ Done | reused `banking.nxisas.audit`; per-row success/failure rows |
| OSMC/GDT bridge (BI09 / TRCO_TRCO001_00003) | ✅ Done | `OsmcGdtClient` (real RestTemplate client); `wecms.osmc.*` config |
| `representative_first_name` surfaced | ⚠️ Partial | on `InquireOperationStatusResponse` wire; `InquireOperationStatusDialog` now **stashes** it and sends it on Save (may be blank — not required), but still does not render it on screen (legacy also doesn't show it on the grid/popup) |
| View Setting popover on list | ⏸ Intentionally omitted | hook consumed for columns/export only; Figma has no list-view icon |

---

## 2. Missing items (detail)

### 2.1 ✅ Inquire-popup Save / persist — RESOLVED (2026-06-03)

- **Legacy:** `aosv_0001_002_c001` validates required fields, guards duplicate `(BIZ_KEY, TAXCODE)`
  with `WBE0003`, and inserts an `OPRT_STTS_INFO` row (`aosv_0001_002_c001_act.jsp:27-86`).
- **Refactor (now):** `POST /api/v1/addon/operation-status` exists.
  - `CreateOperationStatusRequest` bean-validates the legacy required set (`taxCode` w/ the
    `^\d{10}(-\d{3})?$` pattern, `companyStatus`, `officeAdd`, `searchDatetime` w/ `^\d{14}$`).
    `representativeFirstName` carries **no** `@NotBlank` — legacy `validateRequired` only checks key
    presence (empty string passes) and GDT returns it blank for some tax codes; the service
    coalesces a null wire value to `""` for the NOT-NULL column. `companyNm` optional.
    (Fixed 2026-06-03 — an earlier `@NotBlank` on `representativeFirstName` wrongly rejected blank
    GDT responses with `WBE_VALIDATION`.)
  - `OperationStatusService#create`: tenant from `TenantContext`, `existsById((BIZ_KEY,TAXCODE))`
    duplicate guard → `BusinessException("WBE_OPERATION_STATUS_DUPLICATE")` (legacy `WBE0003`
    parity), create-only (never upsert), `stampAudit` + `repo.save`. A concurrent insert race
    surfaces `DataIntegrityViolationException` → same duplicate error (no 500).
  - Frontend `createOperationStatus` (`lib/api/addon.ts`) + `InquireOperationStatusDialog.handleSave`
    (real call, success dialog, parent refetch on success, popup stays open on duplicate/error).
- **Tests:** `OperationStatusServiceTest` — persist + audit + tenant, duplicate guard (no save),
  concurrent-insert race → duplicate.

### 2.2 ⚠️ `representative_first_name` not rendered on the popup

Wire field is present (`InquireOperationStatusResponse.representativeFirstName`, `addon.ts`).
With the Save endpoint landed, `InquireOperationStatusDialog` now **captures** it from the Inquire
response and **sends** it on Save (it's a required `c001` field) — but it still does not render it
on screen. Legacy also omits it from both the grid and the popup, so this is parity-faithful and
low priority. Decide on-screen display only if a future Figma frame asks for it.

---

## 3. Divergences (working-as-designed, flag for sign-off)

| # | Divergence | Risk | Recommendation |
|---|---|---|---|
| D-1 | `status_cd` 3-state enum is refactor-only (legacy had a 12-string FE legend) | Low — raw `company_status` preserved | Keep; revisit if richer status display becomes a requirement |
| D-2 | Status-default mismatch: backend unknown → `not_registered`; FE `coerceOpStatus` → `active_certificate_issued` | Low — backend supplies `status_cd`, FE coercion rarely fires | Align the FE default to `not_registered` for safety |
| D-3 | `EXT_WEB_SVC_ID_R = "aosv_0001_001_r001"` (legacy id) written to `EXT_TRAN_CALL_HIS.WEB_SVC_ID` | None — audit-only sentinel, in-code justified | Keep; documented exception to the slug rule |
| D-4 | Export emits raw Vietnamese `COMPANYSTATUS` (not `status_cd`) | None — matches legacy Excel which writes the legend message | Acceptable; document column semantics |
| D-5 | View Setting popover not rendered | None — Figma has no list-view icon | Keep omitted; hook still drives columns/export |

---

## 4. Stale-documentation findings (correct these)

The existing refactor feature docs contain **stale statements** that contradict the live code:

1. ~~`wecms-api/.../OprtSttsInfo.java` Javadoc + parts of `OperationStatusService` header claim the
   OSMC→GDT refresh path is "intentionally NOT wired" / "Sprint 1 reads seeded data only".~~
   **CORRECTED 2026-06-03** — both Javadocs now describe the wired `inquire` / `bulkUpdate` / `create`
   paths and the env-config-driven gateway. (`inquire` and `bulkUpdate` call the real `OsmcGdtClient`,
   and `application.yml` ships live gateway defaults.)
2. Older sections of the wecms-api / wecms-web feature specs describe Inquire/Update/Export as "not
   wired". They are wired (verified 2026-06-02); only Save/persist is inert. The status header of
   `wecms-api/docs/features/addon-service/operation-status-inquiry.md` is already corrected — the
   in-body TODO list still reads as if buttons are inert and should be reconciled.

These are documentation defects, not code defects — but they will mislead a reader doing a fresh
gap analysis, so they are logged here.

---

## 5. Risk assessment

| Area | Risk | Why |
|---|---|---|
| ~~Save/persist gap~~ | **Resolved** (2026-06-03) | Popup Save now persists via `POST /operation-status` with a duplicate guard; the "add a brand-new company" flow works |
| Live GDT gateway in local default | **Medium** | `application.yml` ships real PROD Coocon gateway creds; a local run of `/inquire` or `/update` hits a live external service. Confirm env-override is set in non-PROD. |
| Status enum drift (D-2) | Low | Cosmetic badge default; backend `status_cd` is authoritative |
| Audit/tenant isolation | Low | Repository constrains `BIZ_KEY`; bulk update silently drops unknown/cross-tenant codes |
| Stale docs | Low | Misleads future contributors; no runtime effect |

---

## 6. Implementation priority

1. ~~**P1 — Inquire-popup Save endpoint**~~ **DONE 2026-06-03** — `POST /operation-status` +
   `createOperationStatus` + `handleSave` + `OperationStatusServiceTest`. Closed the only functional gap.
2. ~~**P2 — Reconcile stale docs** (entity/service Javadoc)~~ **DONE 2026-06-03** — entity + service
   class Javadoc corrected. (Feature-spec TODO lists, if any remain, are a separate doc pass.)
3. **P3 — Align FE status default** (D-2) to `not_registered`. ~15 min. *(still open)*
4. **P3 — Confirm non-PROD OSMC gateway override** so local/SIT don't hit the live PROD gateway. Config check.
5. **P4 — Decide `representative_first_name` display** in the popup (likely add when Save lands).

Nothing here requires a Flyway migration or a schema change. The table set (V16) is complete.
