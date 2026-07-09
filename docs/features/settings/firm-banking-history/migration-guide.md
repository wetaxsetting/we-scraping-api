# Firm Banking History — Migration Guide (JEX → Spring)

How each legacy JEX layer became its refactor counterpart. Parent: [README.md](README.md).
Full field-by-field mapping: [references/migration/firm-banking-history-mapping.md](../../../references/migration/firm-banking-history-mapping.md).
Legacy migration notes (read-only): [docs/jex/.../migration-guide.md](../../../../../docs/jex/features/firm-banking-history/migration-guide.md).

---

## 1. Layer-by-layer mapping

| JEX layer | JEX artifact | Refactor counterpart |
|---|---|---|
| View JSP | `comm_0221_001_view.jsp` | `FirmBankingHistoryPanel.tsx` (+ Filters / Table / DetailPopover) — Settings modal panel |
| Frontend JS | `comm_0221_001.ts` (`loadData` / `fnSelect` / `fnDetails`) | `FirmBankingHistoryPanel.tsx` state + `mapFirmBankingHistoryRow` + `FirmBankingHistoryDetailPopover.tsx` |
| Read action | `comm_0221_001_r001_act.jsp` | `FirmBankingHistoryController.list` + `FirmBankingHistoryService` |
| Service wire | `WSVC.comm_0221_001_r001.xml` | `GET /api/v1/settings/firm-banking-history` contract |
| Data query | `IDO.TB_FIRM_TRAN_R002.xml` (5-leg union) | Hibernate `@Subselect` on `FirmBankingHistoryView` (5-leg union, no DB view) + `FirmBankingHistoryRepository.search()` JPQL |
| DB helpers | `kr_to_vn_time`, `wb_get_napas_bank_nm`, `wb_get_bank_nm` | called by the `@Subselect`; resolved against the target JEX DB (not created by a refactor Flyway migration) |
| Frontend `SERVICE_NAME_MAP` | JS object in `comm_0221_001.ts:40-52` | server-owned `FirmBankingServiceNameResolver` + `svc_name_key` on the wire |
| F-leg enum | `wabooks/enums/TranTp.java` | `common/enums/TranTp.java` (code + `i18nKey`) |
| `resultMap` (18 codes) | JS object in `comm_0221_001.ts:459-478` | `lib/i18n/scrapingResultCodes.{ts,data.ts}` (frontend) |
| `jexjs.getCode("DICTIONARY_VAN", …)` | `WECMS_ETC/xml/code/DICTIONARY_VAN/` | `lib/i18n/dictionaryVan.{ts,data.ts}` (69 entries, frontend) |
| Date init | `loadinitializeDate(..., -30, …)` | `defaultDateRange()` (last 30 days) + shared `<InteractiveDateRangePicker>` |

---

## 2. Request parameter mapping

| JEX `<in>` (WSVC) | Refactor `@RequestParam` | Change |
|---|---|---|
| `FROM_DT` | `from_date` → `fromDate` | snake_case wire; same `YYYYMMDD` rule |
| `TO_DT` | `to_date` → `toDate` | same |
| `SRCH_WD` | `keyword` → `keyword` | renamed; same seq-no ILIKE semantics |
| `SVC_TYPE` | `svc_type` → `svcType` | same; refactor adds explicit `{F,S,G,B}` validation |
| `PAGE_NO` (1-based) | `page` (0-based) | re-based to Spring's zero-based page index |
| `PAGE_PER_CNT` | `size` | clamped to `100`; backend default `15` |
| sort params | `sort_field` + `sort_direction` | whitelisted enum instead of free-string `SortHelper` |

## 3. Response mapping

| JEX `<out>` | Refactor wire | Change |
|---|---|---|
| `COMMON_HEAD` | `status` envelope | retired legacy head → `{ error, code, message }` |
| `TOT_CNT` | `data.total_count` | same value |
| `REC[]` | `data.list[]` | per-row 10 legacy fields → 12 wire fields (adds `svc_type_key`, `svc_name_key`) |
| — | `page_number` / `page_size` / `total_pages` | new explicit paging metadata |

The two new wire fields (`svc_type_key`, `svc_name_key`) move domain-code → label ownership from the
legacy frontend `SERVICE_NAME_MAP` to the Spring backend; the frontend renders via `t(key)` and
falls back to the raw value when the key is `null`.

---

## 4. Architectural decisions

1. **One `@Subselect`, one entity, one query.** JPA does not natively `UNION` heterogeneous entities.
   Rather than five repositories + Java-side merge (breaks server-side sort/paging) or a 90-line native
   query string, the five-leg union is carried by a Hibernate **`@Subselect`** on one `@Immutable`
   entity over the legacy base tables — there is **no** `V_FIRM_BANKING_HISTORY` DB view (an earlier
   refactor materialized one, archived; not provisioned — see
   `project_v_firm_banking_history_subselect.md`). This keeps parity with `IDO.TB_FIRM_TRAN_R002` and
   makes the repository trivial, while `ddl-auto: validate` never looks for a view relation.

2. **Parity-critical SQL helpers stay in the DB.** `kr_to_vn_time` and `wb_get_napas_bank_nm` are
   called by the `@Subselect` and resolved against the target JEX DB's existing functions (consistent
   with the `wb_get_bank_nm` decision), rather than re-implemented in Java, so timezone + bank-name
   results match the live system.

3. **Server-owned i18n keys, frontend-owned labels.** Following the modernized enum policy, the
   backend emits stable i18n keys (`svc_type_key`, `svc_name_key`); the localized strings live in
   `wecms-web/locales/*.json`. No localized label is baked into a Java enum.

4. **No View Setting popover; no View Setting seed either.** The list screen has no "Manage Columns"
   popover (legacy has none), and Firm Banking History is **not seeded** into `SYS_SCR_OPTION_WECMS`
   (the manual `V156__sys_scr_option_wecms.sql` lists it in its EXCLUDED set). The export pipeline does
   not depend on a seed: `ScreenOptionService.load` throws `WBE2001` for the unseeded screen and
   `ExcelExportService.resolveColumns` falls back to request columns ∩ the provider allow-list (memory
   `project_export_only_screens_no_seed.md`). See [§ 4.4 of the detailed spec](../firm-banking-history.md).

5. **Export is net-new.** Legacy has no export for this screen; the refactor adds one through the
   shared `common/export/` pipeline (a refactor enhancement, not a parity port).

---

## 5. PROD Active Table scope

All five source tables are in the PROD Active Table List. The local Flyway migrations mirror DEV
column-for-column per the Legacy Schema Parity Rule; the demo seed copies a ≤30-row slice for tenant
`VN_VNM0004_000001` per the Data Migration Rule.

> **Migration set (post 2026-06-09 renumber).** The source-table migrations are **V73**
> (`NXSIAS_MODULE_TRAN_LOG_HIS`), **V97** (`TB_FIRM_BANK_CODE`), **V104** (`TB_FIRM_TRAN`); plus the
> existing V13 (`NAPAS_REMIT_TRNS_HSTR`) and V16 (`EXT_TRAN_CALL_HIS`). There is **no View Setting seed**
> for `firm-banking-history` — it is an export-only screen and `V156__sys_scr_option_wecms.sql`
> explicitly excludes it (export uses the `WBE2001` → request-cols ∩ allow-list fallback; see §4.4).
> There is also **no** view-creating migration — the five-leg union is a Hibernate `@Subselect` (§4.1).
> The earlier V59/V60/V61/V165 numbering (and any `V102__firm_banking_history_view.sql`) no longer
> exists in the migration tree.

---

## 6. What did NOT carry over (deliberate)

- **`PAGE_NO` 1-based indexing** → re-based to 0-based (`page`).
- **Free-string sort** (`SortHelper` accepts any `paramSort`) → whitelisted enum, unknown → default.
- **Frontend `SERVICE_NAME_MAP` / `coerceFirmBankingServiceType`** → server resolver + wire keys.
- **`COMMON_HEAD` / `REC` envelope** → `{ status, data }`.
- **Read-side maintenance-log write** (`mntLogYn=Y`) → not ported (see [refactor-gap-analysis.md](refactor-gap-analysis.md), GAP-5).
- **Legacy WSVC ids in identifiers** → slug `firm-banking-history` everywhere (URI, package, i18n keys, export screenId).
