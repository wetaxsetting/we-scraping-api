# Settings > Firm Banking History — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend + frontend wired, legacy parity verified (2026-06-05).
**Module / menu:** Settings → Firm Banking → **Firm Banking History**
**Refactor slug:** `firm-banking-history` (matches the JEX, backend, and frontend counterparts)
**Wire base path:** `GET /api/v1/settings/firm-banking-history` (+ `POST .../export`)
**Legacy JEX counterpart:** WSVC `comm_0221_001` (view) + `comm_0221_001_r001` (read API), IDO `TB_FIRM_TRAN_R002`

> This folder is the **primary documentation home** for Firm Banking History on the backend.
> It consolidates and indexes the material that previously lived in scattered single files; those
> files are **preserved and cross-linked** (see [§ Related & preserved docs](#related--preserved-docs)),
> not deleted. The JEX-side functional spec lives at
> [docs/jex/features/firm-banking-history/](../../../../../docs/jex/features/firm-banking-history/README.md)
> and is the read-only source of truth for legacy behaviour.

---

## What this feature is

A unified, paginated, **read-only audit history** of every outbound integration call WeCMS has
made on behalf of the current tenant for firm-banking-related services. Five distinct legacy
source legs are stitched into one row shape and surfaced as a single list with a date-range
filter, service-type filter, sequence-number keyword search, sortable columns, server-side
pagination, a per-row **View Details** popover, and an **XLSX export** (a refactor-only addition —
legacy has no export for this screen).

It is a **diagnostic / audit tool**: it never initiates a transaction and never writes to any
source table.

### The five legs (one `UNION ALL` view)

| Leg | `SVC_TYPE` | Source table | Service name |
|---|---|---|---|
| Firm Banking Transaction | `F` | `TB_FIRM_TRAN` | `TRAN_TP` ∈ {201, 202, 203, 204} |
| Funds Transfer (Scraping) | `S` | `NAPAS_REMIT_TRNS_HSTR` | "Funds Transfer (Scraping)" |
| Scraping Balance / Transaction / Card | `S` | `EXT_TRAN_CALL_HIS` (`SVC_ID` ∈ BALANCE_INQUIRY / REAL_TIME_ONLINE_INQUIRY / CARD_INQUIRY) | "Scraping Balance / Transaction / Card Transaction" |
| Vietnam GDT Inquiry (e-Invoice) | `G` | `NXSIAS_MODULE_TRAN_LOG_HIS` (`NXISAS_TYPE` ∈ E-INVOICE_SALES_INQUIRY / E-INVOICE_EXPENSE_INQUIRY) | "Sale / Expense E-Invoice Inquiry" |
| Vietnam Business Inquiry | `B` | `NXSIAS_MODULE_TRAN_LOG_HIS` (`NXISAS_JOB` ∈ BUSSINESS_REGISTRATION_DETAIL / _LIST) | "Vietnam Business Inquiry" |

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoint (list) | `GET /api/v1/settings/firm-banking-history` |
| Endpoint (export) | `POST /api/v1/settings/firm-banking-history/export` (XLSX) |
| Authority | `@PreAuthorize("hasAnyRole('ADMIN','USER')")` — both are normal company roles (legacy `NORM_USER_ONLY_YN=Y`, `WTA_USER_ALLOW_YN=N` parity) |
| Required params | `from_date`, `to_date` (`YYYYMMDD`, `from ≤ to`) |
| Optional params | `svc_type` (F/S/G/B), `keyword` (seq-no ILIKE), `sort_field`, `sort_direction`, `page`, `size` |
| Backend default page size | `15` (`DEFAULT_PAGE_SIZE`); max `100` (`MAX_PAGE_SIZE`) |
| Default sort | `TARN_DTM DESC, SEQ_NO DESC` |
| Tenant scope | `TenantContext.currentBizKey()` on every leg — never from a request param |
| Data shape | One JPA entity over a read-only view `V_FIRM_BANKING_HISTORY` (Flyway V60) |
| Write surface | None (read-only) |
| Audit log on read | None (intentional residual — see gap analysis) |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.firmbankinghistory
├── controller/FirmBankingHistoryController.java     GET (list) + POST /export
├── service/
│   ├── FirmBankingHistoryService.java                list() + listAll(); validation, paging, tenant scope
│   ├── FirmBankingHistorySortField.java              sort whitelist (6 FIELD_IDs)
│   └── FirmBankingServiceNameResolver.java            (svcType, svcNm) → i18n key
├── repository/FirmBankingHistoryRepository.java      single JPQL search()
├── entity/
│   ├── FirmBankingHistoryView.java                   @Immutable @Table(V_FIRM_BANKING_HISTORY)
│   └── FirmBankingHistoryViewId.java                 composite @IdClass
├── dto/
│   ├── FirmBankingHistoryRow.java                    12-field record
│   └── FirmBankingHistoryListResponse.java           list + paging envelope
└── export/FirmBankingHistoryExportProvider.java       screenId "firm-banking-history"
```

Supporting enums (in `common/enums/`): [`TranTp`](../../../../src/main/java/com/kosign/wecms/common/enums/TranTp.java) (F-leg `TRAN_TP` codes), [`FirmBankingSvcType`](../../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) (F/S/G/B discriminator).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end request flow: search → controller → service → repository/view → response → grid → detail popover → export |
| [functional-specification.md](functional-specification.md) | Search conditions, grid columns, sort/paging behaviour, detail popover, status mapping, loading/empty/error states |
| [database-mapping.md](database-mapping.md) | The five source tables, the `V_FIRM_BANKING_HISTORY` view, and the full legacy-column → entity-field → DTO-field → UI-column mapping |
| [interface-specification.md](interface-specification.md) | REST contract — request params, response shape, error envelope, export contract, status-code registry |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (JSP / `_act.jsp` / IDO / WSVC / DB functions) became its Spring counterpart |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios + the existing JUnit coverage map |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / PARTIAL / MISSING) with completion status |

---

## Related & preserved docs

These existing docs are **preserved** and remain authoritative for their slice. This README is the
entry point; deep detail lives here:

- **Detailed phase-by-phase implementation spec** (Phases 2–3d, i18n bundles — DICTIONARY_VAN port,
  scraping result codes, service-name resolver, EllipsisTooltip):
  [../firm-banking-history.md](../firm-banking-history.md) — the original 734-line spec, kept as the
  deep implementation narrative.
- **DB schema design:** [../../../references/database/settings/firm-banking-history-schema.md](../../../references/database/settings/firm-banking-history-schema.md)
- **JEX → Spring mapping:** [../../../references/migration/firm-banking-history-mapping.md](../../../references/migration/firm-banking-history-mapping.md)
- **Parity audits:** [parity checklist](../../../references/audits/firm-banking-history-parity-checklist.md) · [independent review 2026-05-08](../../../references/audits/firm-banking-history-review-2026-05-08.md)
- **JEX legacy spec set (read-only):** [docs/jex/features/firm-banking-history/](../../../../../docs/jex/features/firm-banking-history/README.md)
- **Frontend feature folder:** [wecms-web/docs/features/settings/firm-banking-history/](../../../../../wecms-web/docs/features/settings/firm-banking-history/README.md)
- **Settings ERD:** [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md)

> **Note on `docs/refactor-wecms/`:** triplicate copies of the mapping / schema / audit docs exist
> under the deprecated workspace-level `docs/refactor-wecms/` tree. That tree is **read-only legacy**
> per the workspace CLAUDE.md; the canonical copies are the `wecms-api/docs/...` paths linked above.
