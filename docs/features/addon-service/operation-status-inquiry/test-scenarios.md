# Operation Status Inquiry — Backend Test Scenarios

## 1. Existing automated tests

[OperationStatusServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/operation/service/OperationStatusServiceTest.java) — Mockito unit tests for the Save (`#create`) path:

| Test | Asserts |
|---|---|
| `create_persistsNewRow_stampsTenantAndAudit` | Happy path — tenant from `TenantContext`, audit (`REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM`) stamped, row saved. |
| `create_blankRepresentative_persists_coalescesToEmpty` | GDT returns blank representative → persisted as `""` (NOT NULL column). |
| `create_duplicateTaxCode_throwsDuplicate_doesNotSave` | `existsById` true → `WBE_OPERATION_STATUS_DUPLICATE`, no save. |
| `create_concurrentInsertRace_mapsToDuplicate` | `DataIntegrityViolationException` on save → same duplicate error (not a 500). |

Run: `./gradlew test --tests "*OperationStatusServiceTest"`.

## 2. Coverage gaps (recommended additions)

| Area | Suggested scenario |
|---|---|
| `list` | filter null/blank skips predicate; `q` OR-matches name & tax code; `companyStatus` partial match; `size<=0`→10, `size>100`→100; default sort fallback on unknown `sortField`; `resolveStatusCd` mapping for each Vietnamese variant. |
| `inquire` | success returns matched registration; bridge failure → `WBE_OSMC_LOOKUP_FAILED`; empty detail → `WBE_OSMC_NO_RESULT`; `EXT_TRAN_CALL_HIS` + `NXSIAS_MODULE_TRAN_LOG_HIS` rows written; tax-code regex rejects bad input. |
| `bulkUpdate` | empty cache → `{0,0,0}`; null selection re-polls all; selection ∩ cache drops unknown codes; per-row detail missing → `failed++`, cache untouched; whole-batch failure → `WBE_OSMC_LOOKUP_FAILED`. |
| export | provider intersects with View Setting `USE_YN`; OPERATION_STATUS emits raw Vietnamese; subheader count + as-of date; unknown screenId → `WBE2010`. |
| tenant isolation | every method scoped to `currentBizKey()`; cross-tenant tax code never updated/read. |

## 3. Manual / integration scenarios

1. **List + filter + sort + paginate** — `GET /api/v1/addon/operation-status?q=abc&companyStatus=ngừng&sortField=COMPANY&sortDirection=ASC&page=0&size=20`; verify tenant scope, partial matches, order, page metadata.
2. **Inquire happy path** (DEV gateway override) — POST a known DEV tax code; verify returned fields + two audit rows.
3. **Inquire failure** — disconnect/override the gateway; verify `WBE_OSMC_LOOKUP_FAILED` and audit row with `RSLT_CD`/`ERR_CONT`.
4. **Save** — after a successful Inquire, POST the create body; verify row in `OPRT_STTS_INFO` with stamped audit; re-POST → `WBE_OPERATION_STATUS_DUPLICATE`.
5. **Bulk update — all** — POST `{}`; verify counts and refreshed `MOD_DTM`.
6. **Bulk update — selection** — POST `{ "tax_codes": [...] }`; verify only those rows updated; unknown code silently dropped.
7. **Export** — POST `ExcelExportRequest`; open the XLSX; verify columns, raw status string, subheader, filename.

> **Gateway safety:** all live-bridge scenarios require `wecms.osmc.*` pointed at a non-PROD Coocon endpoint (or a mock). Never run Inquire/Update against the live PROD gateway from a dev/test run.
