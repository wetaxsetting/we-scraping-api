---
slug: transfer-history
jex_wsvc_id: trns_0003_001_r001  # citation only — never used as a refactor runtime identifier
owner: Sonny (Ran Sokny)
status: planning
last_updated: 2026-05-20
---

# Transfer History — JEX → Spring/Next.js Migration Mapping

Service-by-service mapping for the **Banking > Internal Transfer > Transfer
History** port. Companion to
[docs/refactor-wecms/features/banking/transfer-history.md](../../features/banking/own-account-transfer/transfer-history.md)
and [docs/refactor-wecms/archive/planning/phase-1/transfer-history-plan.md](../../archive/planning/phase-1/transfer-history-plan.md).

Naming and identifier rules per
[wecms-api/CLAUDE.md § Refactor identifier naming](../../../CLAUDE.md)
and the workspace [CLAUDE.md § Cross-Tree Rules](../../../../CLAUDE.md): the JEX
WSVC id `trns_0003_001_r001` appears below only in file:line citations and in
this prose; it is never used as a refactor runtime identifier.

---

## 1. Service mapping table

| # | JEX artifact (path:line) | Refactor equivalent | Status | Risk / notes |
|---|---|---|---|---|
| 1 | View JSP — page shell, filters, table, pagination ([WECMS DEV/WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp:88-330](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp)) | Next.js page: `wecms-web/app/(dashboard)/banking/internal-transfer/transfer-history/page.tsx` composing existing shared shell + feature components | planned | Low. Page mirrors the Banking > Account Inquiry (VND) baseline; layout already approved per the wecms-web reference set. |
| 2 | Page-level WSVC ([WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001.xml)) | N/A — Next.js routing replaces JEX page-service binding. The slug `transfer-history` IS the route. | n/a | — |
| 3 | TypeScript controller — input shape, AJAX, row render, footer ([WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0003_001.ts:39-453](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0003_001.ts)) | React feature component `wecms-web/components/banking/internal-transfer/TransferHistoryPage.tsx` + already-scaffolded `TransferHistoryTable.tsx` + `TransferHistoryTableColumns.tsx` + a new hook `useTransferHistory(...)` in `lib/banking/` | planned | Low. Shared `<PageHeader>`, `<SearchInput>`, `<DateRangePicker>`, `<Pagination>`, `<StatusBadge>`, `useDialog()`, `cn` are reused — no new abstractions. |
| 4 | Action JSP — read service ([WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp:32-159](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp)) | Spring controller `wecms-api/src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java` — single `@GetMapping("/api/v1/banking/internal-transfer/transfer-history")` returning `ApiResponse<TransferHistoryListResponse>` | planned | Low. Thin controller per [wecms-api/CLAUDE.md § Coding Rules](../../../CLAUDE.md). |
| 5 | Read service WSVC ([WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001_r001.xml:32-65](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001_r001.xml)) | `TransferHistoryListResponse` Java record + `TransferHistoryRow` Java record (DTOs) under `…/domain/banking/transferhistory/dto/` — wire shape declared by Java fields + global Jackson SNAKE_CASE | planned | Low. UPPER_SNAKE wire keys retired per CTO directive 2026-05-08. |
| 6 | IDO row query — `NAPAS_REMIT_TRNS_HSTR_R003` ([WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:24-103](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml)) | `NapasRemitTrnsHstrRepository.search(...)` — Spring Data JPA derived method or a `@Query` returning a projection. JEX SQL is faithful 1:1 (no JOIN to the source — the engine result lives in the row already). | planned | Medium. The legacy SQL's `wb_get_bank_nm` / `WB_GET_NAPAS_BANK_NM` / `WB_GET_USER_NM` function calls must be replaced — see rows 11, 12, 13 below. The two `??` DYNAMIC placeholders (WHERE + ORDER BY) become a `Specification<NapasRemitTrnsHstr>` or a hand-built `@Query` with `@Param`s. |
| 7 | IDO totals query — `NAPAS_REMIT_TRNS_HSTR_R003_CNT` ([WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml:24-34](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml)) | `NapasRemitTrnsHstrRepository.aggregate(...)` returning a typed `TransferHistoryTotals` projection (TOT_CNT, TOT_VND_AMT, TOT_USD_AMT, TOT_VND_CNT, TOT_USD_CNT) | planned | Low. Single query, no joins. |
| 8 | DAO — `NapasRemitTrnsHstrDAO.findByDynamic / patchByTrnsStlmId / create` ([WECMS DEV/WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java:24-133](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java)) | `NapasRemitTrnsHstrRepository` (Spring Data JPA, `JpaRepository<NapasRemitTrnsHstr, NapasRemitTrnsHstrId>`) + `TransferHistoryService` (read-side logic only — `patchByTrnsStlmId` / `create` are NOT ported in this feature; they belong to upstream / settlement features) | planned | Low. |
| 9 | Entity — `NapasRemitTrnsHstr` + composite PK ([WECMS DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstr.java:36-225](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstr.java), [WECMS DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstrPk.java:20-58](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstrPk.java)) | `NapasRemitTrnsHstr` JPA entity + `NapasRemitTrnsHstrId` `@Embeddable` (or `@IdClass`) under `…/domain/banking/transferhistory/entity/`. Extends `BaseEntity` (per [wecms-api/CLAUDE.md § Entities](../../../CLAUDE.md)). 21 DB columns map 1:1; legacy `@JsonProperty("UPPER_SNAKE")` annotations are **dropped** (CTO directive 2026-05-08); Java fields are camelCase, serialization handled by the global SNAKE_CASE Jackson strategy. | planned | Low. The legacy entity's `@JsonProperty("TRNS_STLM_YN ")` (trailing space — [NapasRemitTrnsHstr.java:107](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/NapasRemitTrnsHstr.java)) is a JEX bug; refactor field is plain `trnsStlmYn` and wires as `trns_stlm_yn`. |
| 10 | Flyway: `NAPAS_REMIT_TRNS_HSTR` table | Already ported by V13 (Sprint 1 banking schema) + V18 (CHAR→VARCHAR) + V19 (CONF_YN follow-up) + V23 / V24 / V28 / V29 (parity). **No new schema migration.** | done | Verified via DEV `information_schema.columns` (21 cols, PK on (BIZ_KEY, SEQ_NO), no UNIQUE / CHECK / FK). |
| 11 | Bank-name resolution (`wb_get_bank_nm`) ([IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:29](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml)) | `BankCdResolver` service (already exists; reads `BANK_INFM` ported in V20) — batched per request | planned | Low. `BANK_INFM` covers the Vietnamese-bank short-name lookup the legacy `WB_GET_BANK_NM(code, 'S')` call returns. |
| 12 | NAPAS-bank-name resolution (`WB_GET_NAPAS_BANK_NM`) ([IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:30](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml)) | Default plan: reuse `LegacyFunctionRepository.resolveNapasOrCitadBankName(code, langCd)` (Contacts precedent V86, see [wecms-api/CLAUDE.md § Surviving legacy functions](../../../CLAUDE.md)). Alternative: resolve from `BANK_INFM` if PO confirms NAPAS code-space coverage. | open | **MEDIUM risk.** Same parity ambiguity as Contacts OQ-7 (see feature-spec §10 OQ-1). Function is already ported in V86 — no new migration. Decision deferred to Mr. Jung. |
| 13 | User-name resolution (`WB_GET_USER_NM`) ([IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:31](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml)) | `LEFT JOIN UserInfm` keyed on `REG_ID` — same shape as the `erp-transaction-history` precedent | planned | Low. |
| 14 | ML codes (`trns_0003_001_m001..m010` + `trns_0003_001_js003..js011`) ([trns_0003_001_view.jsp:115-179](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp), [trns_0003_001.ts:93-101](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0003_001.ts)) | i18n keys under `banking.transfer_history.*` in `wecms-web/locales/{en,ko,vi}.json`; backend error messages in `wecms-api/src/main/resources/messages.properties` | planned | Low. Per [wecms-api § i18n Rules](../../../CLAUDE.md), translate to `WBExxxx` / `WBIxxxx` codes — never re-use the JEX ML id verbatim. |
| 15 | Validation — `ValidationHelper.validateRequired/Date8/Period/PageNo/PagePerCnt` ([trns_0003_001_r001_act.jsp:32-50](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp)) | Bean Validation on the request record (`@NotBlank`, `@Pattern("\\d{8}")`, `@Min(1)`, custom `@PageSize`) + service-level `to_dt >= from_dt` guard → `WBE_TRANSFER_HISTORY_INVALID_PERIOD` | planned | Low. |
| 16 | Sort handling — `SortHelper.getSortDynamic(util, "trns_0003_001_r001")` ([trns_0003_001_r001_act.jsp:146](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp)) | A new `TransferHistorySortField` enum (one constant per allowed `data-sort` value: `TRNS_DT` / `OUT_BANK_NM` / `IN_BANK_NM` / `TRSC_AMT` / `RMRK`) — follows the `ApprovalLineSortField` / `ContactSortField` precedent | planned | Low. Sort-whitelist rule already established in [wecms-api/CLAUDE.md § Common table sort](../../../CLAUDE.md). |
| 17 | Pagination — `PageInfo` + `JexUtils.setIDOPageInfo` ([trns_0003_001_r001_act.jsp:93-97](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp)) | Spring Data `Pageable` resolved from `page_no` + `page_per_cnt` query params; the response carries `total_count` separately (not Spring's `Page<T>` wrapper — wire shape must mirror legacy with explicit totals) | planned | Low. |
| 18 | UTC normalisation (`DateTimeUtils.normalizeResult` for `TRNS_DT`) ([trns_0003_001_r001_act.jsp:101-105](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp)) | `TransferHistoryService` splits `REG_DTM` (VARCHAR(14) `yyyyMMddHHmmss`, UTC) into `trns_dt` (yyyyMMdd) + `trns_tm` (HHmm) at projection time. Time-zone offset to operator-locale is done client-side in the existing `lib/format/datetime.ts` helper (already used by Account Inquiry). | planned | Low. The legacy JSP normalises to operator TZ in Java; refactor pushes that to the client to keep the controller deterministic. |
| 19 | Audit logging — JEX framework `mntLogYn=Y` ([WSVC.trns_0003_001_r001.xml:11](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001_r001.xml)) | No `AUDIT_LOGS_INFM` write planned (default — matches `approval-line` / `erp-transaction-history` precedent). If PO requests it later, the slug is `transfer-history`. | open | Decision OQ-4 in the feature spec. |
| 20 | Helper index `ix_napas_remit_trns_hstr_biz_regdtm` | Optional Flyway migration `V{N}__transfer_history_idx.sql` — defer to a perf MR unless baseline shows degradation. | open | Decision OQ-3 in the feature spec. |
| 21 | **Coocon iSAS-NX scraping engine wrapper** ([WECMS DEV/WECMS_STATIC/web/nxisas/js/isasscaping.js:1-130](../../../../WECMS%20DEV/WECMS_STATIC/web/nxisas/js/isasscaping.js), [trns_0002_001.ts:340-410](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/trns/trns_0002_001.ts)) | **Not in scope for Transfer History.** Documented in the feature spec §3 for completeness; ported by the separate **Internal Transfer Register** feature MR. Spring backend never invokes the engine (no `ProcessBuilder`, no subprocess, no `coocon.exe` path on the server) — engine runs on the operator PC and is driven by the Next.js client. | deferred | High — engine contract is non-trivial; deferred to its own MR so this read-side port stays small. |
| 22 | Upstream INSERT — `trns_0002_001_c001` + `NAPAS_REMIT_TRNS_HSTR_C001` IDO + `SeqHelper.getNextVal("seq_no", bizKey)` ([trns_0002_001_c001_act.jsp:101-108](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0002_001_c001_act.jsp), [NapasRemitTrnsHstrDAO.java:74-79](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java)) | Out of scope for Transfer History. Ported by the Internal Transfer Register MR; that MR needs to decide between adding a `wb_get_next_val` branch for `napas_remit_trns_hstr.seq_no` (DEV has the branch — see [wecms-api/CLAUDE.md § Sprint 1 key-generation audit](../../../CLAUDE.md)) **or** implementing a Java per-tenant generator (preferred per the policy). | deferred | — |
| 23 | Settlement linkage — `patchByTrnsStlmId` ([NapasRemitTrnsHstrDAO.java:114-130](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java)) + `NAPAS_REMIT_TRNS_HSTR_U001` IDO | Out of scope for Transfer History. Ported by the settlement / reconciliation feature MR. | deferred | — |
| 24 | Frontend wire-type cutover — `ApiTransferRow` / `ApiTransferHistoryResponse` (currently UPPER_SNAKE holdovers, [wecms-web/lib/types/api.ts](../../../../wecms-web/lib/types/api.ts) per [wecms-web § Holdovers](../../../../wecms-web/CLAUDE.md)) | Flip both types to lowercase snake_case **in the same MR** that lands the backend (workspace full-stack ownership rule, [CLAUDE.md](../../../../CLAUDE.md)). Update every consumer of those types — `tsc --noEmit` is the safety net. | planned | Medium. Coupled with the backend MR; cannot half-migrate. |

---

## 2. Field-level mapping — `TransferHistoryRow`

The "raw" column comes from the IDO/entity; "wire" is the snake_case JSON key.
All Java fields are camelCase; Jackson global SNAKE_CASE handles conversion.

| Java field | Wire key | JEX origin (`IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml`) | DB column |
|---|---|---|---|
| `seqNo` | `seq_no` | `SEQ_NO` :83 | `NAPAS_REMIT_TRNS_HSTR.SEQ_NO` |
| `trnsDt` | `trns_dt` | `REG_DTM AS TRNS_DT` :42 (date portion, 8 chars) | `REG_DTM` |
| `trnsTm` | `trns_tm` | `REG_DTM AS TRNS_TM` :68 (time portion, 4 chars HHmm) | `REG_DTM` |
| `outBankCode` | `out_bank_code` | :84 | `OUT_BANK_CODE` |
| `outBankNm` | `out_bank_nm` | `wb_get_bank_nm(T.OUT_BANK_CODE, 'S')` :29 | resolved server-side |
| `outAcctNo` | `out_acct_no` | :85 | `OUT_ACCT_NO` |
| `inBankCode` | `in_bank_code` | `T.IN_NAPAS_BANK_CODE AS IN_BANK_CODE` :33 (re-aliased) | `IN_NAPAS_BANK_CODE` |
| `inBankNm` | `in_bank_nm` | `WB_GET_NAPAS_BANK_NM(T.IN_BANK_CODE, 'S', 'EN')` :30 | resolved server-side |
| `inAcctNo` | `in_acct_no` | :91 | `IN_ACCT_NO` |
| `owanNm` | `owan_nm` | `T.OWAC_NM AS OWAN_NM` :41 (re-aliased — keep the wire key as `owan_nm` for parity) | `OWAC_NM` |
| `ccyCd` | `ccy_cd` | :92 | `CCY_CD` |
| `trscAmt` | `trsc_amt` | :93 | `TRSC_AMT` |
| `fee` | `fee` | :95 | `FEE` |
| `transType` | `trans_type` | :89 | `TRANS_TYPE` |
| `rmrk` | `rmrk` | :96 | `RMRK` |
| `trnsStts` | `trns_stts` | :100 | `TRNS_STTS` |
| `trnsMsg` | `trns_msg` | :101 | `TRNS_MSG` |
| `userNm` | `user_nm` | `WB_GET_USER_NM(T.REG_ID) AS USER_NM` :31 | resolved server-side |

Fields intentionally **omitted** from the wire (legacy IDO emits them but the
legacy view does not use them, and the refactor view does not need them):
`RMRK2`, `FEE_DFR_SBJ`, `TRNS_STLM_ID`, `TRNS_STLM_YN`. Add later if a real
consumer arrives.

---

## 3. Risk summary

| Risk | Severity | Mitigation |
|---|---|---|
| `in_bank_nm` parity drift between `BANK_INFM` and the legacy `WB_GET_NAPAS_BANK_NM` lookup | Medium | Default plan: reuse the V114 surviving legacy function via `LegacyFunctionRepository` (Contacts precedent). Resolve OQ-1 with PO before coding. |
| Sort-field whitelist is stricter than legacy (refactor whitelists; JEX takes any `paramSort` string) | Low | Document in the MR; matches the project-wide rule established for `approval-line` / `contacts`. |
| Wire-type cutover for `ApiTransferRow` cascades through every consumer | Medium | Coordinate frontend + backend in one MR; rely on `tsc --noEmit` to catch consumers; pre-merge dev run + Mr. Youvita's manual smoke test. |
| Helper index timing — ship vs defer | Low | Resolve OQ-3 with PO; default is to defer until baseline measurement. |
| Audit-log write decision | Low | Default to no audit write (precedent); resolve OQ-4 with PO. |

---

## 4. What this MR does NOT touch

- The Coocon iSAS-NX engine — its contract is documented in
  [docs/refactor-wecms/features/banking/transfer-history.md § 3](../../features/banking/own-account-transfer/transfer-history.md)
  for context but is ported by the Internal Transfer Register MR. **Spring
  never invokes a `.exe`.**
- `NAPAS_REMIT_TRNS_HSTR_C001` / `_U001` IDOs (INSERT / settlement) — owned by
  the Register and Settlement MRs respectively.
- DEV / UAT / PROD deploy — R&D scope only (local + GitLab).
- Any change to JEX trees (`WECMS DEV/`, `SOURCE_CODE_ADMIN/`,
  `SOURCE_CODED_OSMC/`, `DEV ADMIN/`).
