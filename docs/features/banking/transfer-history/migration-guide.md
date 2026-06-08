# Transfer History — Migration Guide (JEX → Spring)

> Legacy `trns_0003_001` / `trns_0003_001_r001` → refactor `domain/banking/transferhistory/`
> See also: `wecms-api/docs/references/migration/transfer-history.md` (the original service-by-service mapping) and `docs/jex/features/transfer-history/migration-guide.md` (legacy package).

## 1. Artifact-by-artifact mapping

| Legacy JEX artifact | Refactor counterpart | Notes |
|---|---|---|
| `trns_0003_001_view.jsp` | `wecms-web` `TransferHistoryPage.tsx` (frontend) | UI; backend has no view |
| `trns_0003_001_act.jsp` (no-op page shell) | — | nothing to port (just `setResult(default)`) |
| `trns_0003_001_r001_act.jsp` (read service) | `TransferHistoryController` + `TransferHistoryQueryService` | the actual read logic |
| `IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml` (list SQL) | `TransferHistoryRepository.findAll(Specification, Pageable)` + `TransferHistoryQueryService.toRow(...)` | dynamic WHERE → Specification; SELECT aliases → DTO mapping |
| `IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml` (totals SQL) | `TransferHistoryRepository.aggregateTotals(...)` (JPQL) | CASE-SUM per currency |
| `NapasRemitTrnsHstrDAO.java` | `TransferHistoryRepository` (Spring Data JPA) | `create`/`patch` DAO methods are NOT ported (used by upstream register/settlement, not this screen) |
| `wabooks/entity/NapasRemitTrnsHstr.java` | `domain/banking/entity/NapasRemitTrnsHstr.java` | column-for-column subset |
| `SortHelper.getSortDynamic("trns_0003_001_r001")` | `TransferHistorySortField` enum + `common/web` | whitelist instead of dynamic ORDER BY string |
| `WB_GET_USER_NM(REG_ID)` | batched `UserInfmRepository.findAllById` | Java LEFT JOIN replaces the DB function |
| `wb_get_bank_nm(OUT_BANK_CODE,'S')` | `BankCdResolver.resolveBankNm` | reads `BANK_INFM` |
| `WB_GET_NAPAS_BANK_NM(IN_BANK_CODE,'S','EN')` | `LegacyFunctionRepository.resolveNapasOrCitadBankName` (keyed on `IN_NAPAS_BANK_CODE`) | legacy fn ported in V114 |
| `PageInfo` / `JexUtils.setIDOPageInfo` | Spring `Pageable` + `Page<>` | |
| `validatePeriod(...)` | service period guard → `WBE_TRANSFER_HISTORY_INVALID_PERIOD` | end ≥ start only |
| `normalizeTrnsDtFrmUTC` | `TransferHistoryQueryService.toDisplayZone14` (Asia/Ho_Chi_Minh) | UTC→local for display |
| ML codes `m005`–`m010` (column headers) | `transfer_history.col.*` locale keys (frontend) | translated client-side |

## 2. Behavioural deltas (intentional)

| Aspect | Legacy | Refactor | Reason |
|---|---|---|---|
| Status filter values | view radios `""`/`00000000`/`99999999`; SQL branch `= '00000000'` vs `<> '00000000'` | identical wire contract; null-safe `<>` predicate | parity |
| Failed predicate null-safety | `<> '00000000'` (NULL excluded by SQL) | `trns_stts IS NULL OR <> '00000000'` (NULL counts as failed) | refactor hardening |
| Sort tie-break | per IDO ORDER BY | always appends `seqNo DESC` | deterministic paging |
| User/bank name | DB functions inline in SQL | Java-side resolvers with per-request caching | "no new DB-function dependencies" policy (bank-name fn kept by exception, V114) |
| `WBE` codes | JEX ML codes | `WBE_TRANSFER_HISTORY_INVALID_PERIOD`, `WBE_VALIDATION` | code translation rule |
| Helper index | DEV table has PK only | adds `ix_napas_remit_trns_hstr_biz_regdtm` | extra index allowed |

## 3. Scope decisions (resolved open questions)

| OQ | Decision (2026-05-20) |
|---|---|
| OQ-4 (audit) | **No audit-log writes** — read-only endpoint. Slug `transfer-history` reserved as the future audit `WSVC_ID`. |
| OQ-5 (auth) | **No `@PreAuthorize`** — JWT only; legacy `NORM_USER_ONLY_YN=Y` excludes WTA users only. |
| OQ-8 (DTO fields) | `RMRK2`, `FEE_DFR_SBJ`, `TRNS_STLM_ID`, `TRNS_STLM_YN` omitted from the row DTO (not shown on screen). |
| Naming | Refactor slug `transfer-history`; wire path `/api/v1/banking/internal-transfer/transfer-history`. Legacy WSVC id `trns_0003_001` kept only in citations/prose. |

## 4. Tables in / out of scope

- **In scope (refactored):** `NAPAS_REMIT_TRNS_HSTR` (read), plus reference reads of `USER_INFM`, `BANK_INFM`, `tb_firm_bank_code`.
- **Out of scope:** `ACCT_TRNS_RCV_PAY_INFM`, `SCDUL_PYMN_SUMR(_DETL)`, `BUSI_MATCH_LOG`, `ACCT_TRNS_HSTR` — not referenced by `trns_0003_001`; they back the separate matching/scheduled-payment features.

## 5. Interim endpoint — REMOVED 2026-06-07

`GET /api/v1/banking/transfers/history` (`TransferController.history()` + `TransferHistoryService` + `TransferHistoryResponse` / `TransferRow`) was the Sprint-1 UPPER_SNAKE holdover. It was **deleted 2026-06-07** after a grep confirmed no consumer (the frontend `fetchTransferHistory()` calls the new `/internal-transfer/transfer-history` path). The 3 files + the controller method were removed; `NapasRemitTrnsHstrRepository` / `NapasRemitTrnsHstr` were kept (shared with `OwnAccountTransferService`). No UPPER_SNAKE surface remains.
