# Transfer History — Source Code Index

> All paths relative to repo roots. Legacy paths under `WECMS DEV/` are **read-only reference**.

## 1. Refactor backend (wecms-api) — clean implementation

| Layer | File |
|---|---|
| Controller | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java` |
| Service | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java` |
| Repository | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/repository/TransferHistoryRepository.java` |
| Totals projection | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/repository/TransferHistoryTotals.java` |
| DTO — request | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListRequest.java` |
| DTO — list response | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListResponse.java` |
| DTO — row | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryRow.java` |
| Sort whitelist | `src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistorySortField.java` |
| Entity | `src/main/java/com/kosign/wecms/domain/banking/entity/NapasRemitTrnsHstr.java` |
| Entity id | `src/main/java/com/kosign/wecms/domain/banking/entity/NapasRemitTrnsHstrId.java` |
| Flyway (helper index) | `src/main/resources/db/migration/V196__transfer_history_helper_index.sql` ⚠️ internal header reads `V181` (stale) |
| Message (error) | `src/main/resources/messages.properties:683` (`WBE_TRANSFER_HISTORY_INVALID_PERIOD`) |

### Shared / supporting (not feature-owned)

| Concern | File |
|---|---|
| Sort direction enum | `src/main/java/com/kosign/wecms/common/web/SortDirection.java` |
| Sortable field interface | `src/main/java/com/kosign/wecms/common/web/SortableField.java` |
| Out-bank name resolver | `domain/banking/service/BankCdResolver.java` (reads `BANK_INFM`) |
| In-bank (NAPAS/CITAD) name | `common/legacy/LegacyFunctionRepository.resolveNapasOrCitadBankName` → `wb_get_napas_bank_nm_or_citad_bank_nm` (V114) |
| User name lookup | `domain/auth/repository/UserInfmRepository.findAllById` |
| Tenant context | `common/tenant/TenantContext.java` |

## 2. Refactor backend — deprecated interim (Sprint 1) — DELETED 2026-06-07

The interim UPPER_SNAKE endpoint and its files were removed once it was confirmed to have no consumer:

| Layer | File | Status |
|---|---|---|
| Controller method | `TransferController.history()` (`GET /api/v1/banking/transfers/history`) | **removed** (the `/transfers/own-accounts` handler on `TransferController` stays) |
| Service | `domain/banking/service/TransferHistoryService.java` | **deleted** |
| DTO | `domain/banking/dto/TransferHistoryResponse.java`, `domain/banking/dto/TransferRow.java` | **deleted** (UPPER_SNAKE) |
| Repository | `domain/banking/repository/NapasRemitTrnsHstrRepository.java` | **kept** — shared with `OwnAccountTransferService` |

## 3. Tests (wecms-api)

| File | Type |
|---|---|
| `src/test/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryControllerTest.java` | MockMvc — validation envelope + happy-path snake_case + business-error surfacing (7 tests) |
| `src/test/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryServiceTest.java` | Mockito — projection, UTC→display zone, NAPAS-code name resolution, status filter mapping, sort/page rules, caches, locale collapse (15 tests) |

See [test-scenarios.md](test-scenarios.md) for per-test assertions.

## 4. Refactor frontend (wecms-web)

| Layer | File |
|---|---|
| Route page | `app/(dashboard)/banking/transfer-history/page.tsx` |
| Client (state + fetch) | `components/banking/internal-transfer/TransferHistoryClient.tsx` |
| Presentational page | `components/banking/internal-transfer/TransferHistoryPage.tsx` |
| Table | `components/banking/internal-transfer/TransferHistoryTable.tsx` |
| Colgroup | `components/banking/internal-transfer/TransferHistoryTableColumns.tsx` |
| Footer | `components/banking/internal-transfer/TransferHistoryTableFooter.tsx` |
| Types | `components/banking/internal-transfer/types.ts` |
| API client | `lib/api/banking.ts` → `fetchTransferHistory()` (lines ~662–697) |
| Wire types | `lib/types/api.ts` → `ApiTransferHistoryRow` / `ApiTransferHistoryResponse` (lines ~705–741) |
| Mappers | `lib/api/mappers.ts` → `mapTransferHistoryRow` / `mapTransferHistoryTotals` (lines ~418–464) |
| Sort hook | `hooks/useTableSort.ts` |
| Screen-id registry | `lib/viewSettingScreens.ts` → `SCREEN_IDS.TRANSFER_HISTORY = "transfer-history"`, `SCREEN_ID_TO_ROUTE → /banking/transfer-history` |
| Permission map | `lib/menuAuth.ts` → `/banking/transfer-history` → `banking-internal-transfer-history` |
| Sidebar nav | `components/layout/sidebarNav.ts` → leaf `banking-transfer-history` (label `sidebar.banking.transferHistory`) |
| Locale | `locales/{en,ko,vi}.json` → `transfer_history.*` |

(Full frontend index also in `wecms-web/docs/features/banking/transfer-history/component-index.md`.)

## 5. Legacy JEX sources (read-only reference, under `WECMS DEV/`)

| Artifact | Path |
|---|---|
| View JSP | `WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp` |
| View action (no-op) | `WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_act.jsp` |
| Read service action | `WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp` |
| TypeScript controller | `WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0003_001.ts` |
| IDO — list query | `WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml` |
| IDO — totals/count | `WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml` |
| DAO | `WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java` |
| Entity | `WECMS/src/wabooks/entity/NapasRemitTrnsHstr.java` |
| WSVC (page) | `WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001.xml` |
| WSVC (read) | `WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001_r001.xml` |

## 6. Existing documentation (cross-link targets — preserved)

| Doc | Path |
|---|---|
| Legacy JEX feature package (canonical) | `docs/jex/features/transfer-history/` (10 files) |
| JEX→Spring migration mapping | `wecms-api/docs/references/migration/transfer-history.md` |
| Parity audit | `wecms-api/docs/references/audits/transfer-history-audit-2026-05-20.md` |
| Archived Sprint-1 plan | `wecms-api/docs/archive/planning/phase-1/transfer-history-plan.md` |
| Frontend page-design (existing) | `wecms-web/docs/features/banking/own-account-transfer/transfer-history.md` |
| Page-mapping inventory | `wecms-web/docs/references/page-mapping/page-mapping.md` |
| Deprecated legacy mirror | `docs/refactor-wecms/...transfer-history...` (read-only) |
