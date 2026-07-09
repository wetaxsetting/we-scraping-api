# Operation Status Inquiry — Backend Feature (wecms-api)

**Module:** Add-on Service
**Feature slug:** `operation-status-inquiry`
**Refactor screen ID / View Setting `WEB_SVC_ID`:** `addon-operation-status`
**REST base:** `/api/v1/addon/operation-status`
**Java package:** `com.kosign.wecms.domain.operation`
**JEX counterpart (read-only reference):** `aosv_0001_001` (list view) · `aosv_0001_002` (inquire popup)
**Status:** ✅ **Completed** — backend implemented end-to-end (list + inquire + save + bulk-update + Excel export), unit tests present, frontend wired against live endpoints (verified 2026-06-02 in `wecms-web`).

> This folder is the consolidated, code-accurate home for the **completed** Operation Status Inquiry backend feature. It supersedes the *status claims* in the older scattered docs (which predate the Inquire/Update/Save/Export wiring); those docs are kept in place as category-specific deep references and cross-linked below.

---

## What the feature does

Operation Status Inquiry lists every counterparty (customer / supplier) whose **Vietnamese tax-authority operating status** has been polled into WeCMS, one row per `(BIZ_KEY, TAXCODE)` pair in `OPRT_STTS_INFO`. Each row carries the company's tax code (MST), name, registered office address, the raw Vietnamese operating-status string returned by the General Department of Taxation (GDT — Tổng cục Thuế), and the last poll timestamp.

The finance team uses it to spot counterparties whose tax registration is suspended/ceased (those flags invalidate input-VAT credit on outbound e-invoices) and to refresh that status on demand against the GDT.

Five backend operations back the page:

| Operation | Endpoint | Service method | Legacy mirror |
|---|---|---|---|
| List (filter + sort + paginate) | `GET /api/v1/addon/operation-status` | `OperationStatusService#list` | `aosv_0001_001_r001` |
| Inquire (single Tax ID GDT lookup) | `POST .../operation-status/inquire` | `#inquire` | `aosv_0001_002_r001` |
| Save (persist looked-up row) | `POST .../operation-status` | `#create` | `aosv_0001_002_c001` |
| Update (bulk re-poll + upsert) | `POST .../operation-status/update` | `#bulkUpdate` | `aosv_0001_001_u001` |
| Export (XLSX) | `POST .../operation-status/export` | `ExcelExportService` + `OperationStatusExportProvider` | refactor addition |

The live OSMC → NXISAS → GDT bridge **is wired** (`OsmcGdtClient`); a non-PROD run must point `wecms.osmc.*` away from the live Coocon gateway.

---

## Documents in this folder

| Doc | Focus |
|---|---|
| [business-flow.md](business-flow.md) | Request → service → repository → DB flow for each operation; GDT bridge call sequence; audit-row writes; tenant scope. |
| [functional-specification.md](functional-specification.md) | Business rules: scope, status derivation, filters, pagination, sort, duplicate guard, error codes. |
| [database-mapping.md](database-mapping.md) | `OPRT_STTS_INFO` + audit tables, columns, PK/index, Flyway migrations, status-string mapping. |
| [interface-specification.md](interface-specification.md) | REST contracts — request/response wire shapes (snake_case), validation, error envelopes, examples. |
| [source-code-index.md](source-code-index.md) | Every backend source file with a one-line role + key line references. |
| [migration-guide.md](migration-guide.md) | JEX WSVC/IDO/JSP → Spring controller/service/repository mapping; field-by-field; divergences; migration status. |
| [test-scenarios.md](test-scenarios.md) | Existing unit tests + manual / integration scenarios. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Completed coverage vs JEX, remaining gaps, deliberate divergences. |

---

## Related docs (kept in place — cross-references)

**Existing category docs (deep reference):**
- Feature spec (history + design): [../operation-status-inquiry.md](../operation-status-inquiry.md)
- API contract: [../../../references/api-spec/addon/operation-status-inquiry-api.md](../../../references/api-spec/addon/operation-status-inquiry-api.md)
- DB design: [../../../references/database/addon/operation-status-inquiry-db.md](../../../references/database/addon/operation-status-inquiry-db.md)
- JEX → Spring migration: ../../../migration/operation-status-inquiry.md _(removed — historical `migration/` folder deleted by project decision)_
- Parity audit: [../../../audits/operation-status-inquiry-parity.md](../../../audits/operation-status-inquiry-parity.md)
- Implementation plan: ../../../planning/addon/operation-status-inquiry-plan.md _(removed — historical `planning/` folder deleted by project decision)_

**Frontend counterpart:** [../../../../../wecms-web/docs/features/addon-service/operation-status-inquiry/README.md](../../../../../wecms-web/docs/features/addon-service/operation-status-inquiry/README.md)

**JEX functional source of truth (read-only):** `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.aosv_0001_001_r001.xml`, `WECMS DEV/WECMS/web/view/jex/wabooks/aosv/aosv_0001_001_view.jsp`
