# HAENGSUNG Oracle Integration — Final Full Audit

**System:** Refactor (Spring Boot `wecms-api` + Next.js `wecms-web`) vs Legacy JEX
**Audit date:** 2026-06-29
**Auditor:** Claude Code (read-only across all 5 systems)
**Verdict:** ✅ **Code/DB/legacy complete and legacy-faithful.** All 11 in-scope flows are implemented, wired, tested. All Oracle interface tables and WeCMS target columns exist and match. Differences from legacy are limited to **documented intentional improvements** (event-driven write-back vs batch; explicit success dialog; inline spinner). No credential leak. No DB write was performed during this audit.

> ⚠️ **See the §Re-audit (2026-06-29) below for the current release verdict** — the YAML config was changed to bare placeholders since this section was written, which introduces deployment-config WARNINGs. The code-level verdict above stands; the *release* verdict is 🟡 conditional.

---

## Re-audit — 2026-06-29 (final pre-SIT, from source)

Re-verified from source (not from prior reports): backend (27 files), frontend (4 modules + shared), legacy JEX (`ErpDataRefreshHelper`, `ErpStatusUpdateHelper`, `ERP_0100_001`–`004`, refresh JSP), Oracle DB (5 tables, live), WeCMS Postgres (8 target tables, live), and all config/docs.

### Verified ✅ (no defects found)
- **Backend:** no dead/duplicated code, no orphan beans, no wrong `@Transactional`, no JPA on Oracle (all `@Repository`+`JdbcTemplate` via `@Qualifier(ORACLE_JDBC_TEMPLATE)`). PostgreSQL stays `@Primary`; Oracle secondary/JdbcTemplate-only; `initializationFailTimeout(-1)` so an Oracle outage never blocks boot. All services + 3 scheduler jobs `@ConditionalOnProperty(...enabled=true, matchIfMissing=false)` + double-gated via `ObjectProvider` when `haengsung.oracle.enabled=false`. ShedLock `PT10M/PT30S`, zone `Asia/Ho_Chi_Minh`. No credential/URL logged (redacted SQLState/errorCode only).
- **Legacy parity:** status lifecycle matches legacy literals — `LAST_STATUS` 00/10/20/32/51/52; master `END_GB='1'` on reject/close; detail `END_GB` 2 (received)/3 (reject)/5 (fail)/8 (success); detail `PROC_STS='XXXX'` on reject, `'0999'` on dispatch fail, else `RS_CD`; `ERP_RCV_FLAG='Y'`+`IF_SND_*`/`FLAG_*` on receive; `ERP_SND_YN='Y'`+`ERP_GET_ST='0'` on FX/account/card; `ERP_PROC_CMPL_YN='Y'` after dispatch; status map PENDING→10/APPROVED→20/REJECTED→32/PROCESSING→51; account VND/FCY audit split. NOR/BUL/ERR counts + amounts match.
- **Oracle DB (read-only):** `TB_TRNS_MAST` (22), `TB_TRNS_DETAIL` (40), `TB_FCY_EXRATE` (6,368), `TB_ACCT_HISTORY` (1,543), `TB_CARD_APPROVAL` (112) — all required columns present.
- **WeCMS Postgres:** all 8 target tables carry the required ERP columns (`erp_file_*`, `erp_proc_cmpl_yn`, `appr_seq_no`, `trnf_yn`, `erp_snd_yn`, `hand_reg_yn`, `erp_type`).
- **Frontend:** Refresh gated `isProductType(user,'H') && onRefresh!==undefined`; Internal Transfer never passes `onRefresh` (Excel Upload only); single shared `useHaengsungRefresh` hook + single `haengsungPaymentRefresh` client (no dup/dead); loading/disable/duplicate-guard/success+error dialogs/list-reload all present; shared `RequestApprovalDialog` + `CommonApprovalDetailsDialog` (approve/reject gated to first-pending approver).

### Findings (classified)
| # | Sev | Finding | Evidence | Action |
|---|---|---|---|---|
| W1 | 🟡 | **Bare YAML placeholders, no defaults.** All 5 profiles bind `${HAENGSUNG_*}` (10 each, 0 defaults) — OFF-by-default safety removed; any missing var crashes the *whole* app (`Could not resolve placeholder`), not just HAENGSUNG. Caused 2 boot failures during this session. | `application*.yml` (verified 10 bare / 0 default each) | Every env's `.env`/setenv.sh must define all 10. SIT `.env.sit` verified complete (10/10) → boots. Consider restoring `:false`/`:` defaults for non-secret keys (lead's call). |
| W2 | 🟡 | **`.env.sit` has `HAENGSUNG_ORACLE_ENABLED=false`** while its comment says "Oracle ON" → HAENGSUNG is **inert on SIT** as configured (Refresh → `WBE_HS_REFRESH_DISABLED`). | `.env.sit` (verified) | Set `=true` when the deploy intent is to exercise HAENGSUNG; align the comment. |
| W3 | 🟡 | **Doc mismatches** (fixed in this pass): roadmap said "Planning — nothing implemented"; roadmap + SIT checklist said env vars "optional / OFF by default" (now mandatory under bare placeholders). | roadmap.md, haengsung-sit-deployment-checklist.md | ✅ Corrected 2026-06-29. |
| W4 | 🟡 | **Card-sync source divergence (F-1).** Legacy `ERP_0100_003` reads card rows from `CARD_MSTR` (flag-update via `CARD_TRNS_HSTR_U003`); refactor reads `card_trns_hstr` directly. Both write Oracle `TB_CARD_APPROVAL` identically. | `ERP_0100_003.java` vs `HaengsungCardAccountSyncService` | Confirm row-set parity before trusting card sync in volume (SIT Step 8). |
| W5 | 🟡 | **Exposed credentials** (Oracle `wecms` + DB `wecmsdev_owner`) pasted in chat; `.env.*` gitignored (not committed). | session transcript | Rotate both after SIT testing. |
| W6 | 🟡 | **PROD gates open** (not SIT blockers): R-02 action-level authz, R-03 dispatch-callback hardening. | CLAUDE.md | Close before UAT/PROD. |

**No 🔴 BLOCKER for SIT:** the app boots on SIT (`.env.sit` supplies all 10 vars), code is correct, datasource isolation intact, no secret committed.

### Release Readiness Score — **70 / 100**
Deductions: W1 bare-placeholder fragility −10 · W2 `.env.sit` ENABLED=false vs intent −4 · W3 doc mismatches (fixed) −6 · W4 card-sync parity unverified −4 · W5 creds to rotate −3 · W6 PROD gates −3.

**Release verdict:** 🟡 **CONDITIONAL GO for SIT** — deploy boots and is safe; to actually exercise HAENGSUNG set `HAENGSUNG_ORACLE_ENABLED=true` in `.env.sit`, then run Step 8 verification (incl. F-1 card-sync parity). Not PROD-ready until W4–W6 close.

> Sibling docs (source-of-truth for each flow):
> [haengsung-oracle-integration-roadmap.md](haengsung-oracle-integration-roadmap.md) ·
> [haengsung-payment-refresh.md](haengsung-payment-refresh.md) ·
> [haengsung-approval-status-writeback.md](haengsung-approval-status-writeback.md) ·
> [haengsung-dispatch-result-writeback.md](haengsung-dispatch-result-writeback.md) ·
> [haengsung-exchange-rate-sync.md](haengsung-exchange-rate-sync.md) ·
> [haengsung-card-account-sync.md](haengsung-card-account-sync.md) ·
> [haengsung-oracle-integration-sit-verification.md](haengsung-oracle-integration-sit-verification.md)

---

## 1. Per-flow comparison matrix

| Flow | Legacy | Refactor API | Refactor Web | DB Result | Match? | Notes |
|---|---|---|---|---|---|---|
| **Oracle Foundation** | JEX uses Oracle IDO/WSVC against the same TB_* interface tables | `HaengsungOracleDataSourceConfig` + `HaengsungOracleProperties` — secondary Hikari DS, Postgres stays `@Primary`, fail-fast on missing creds, `SELECT 1 FROM dual` validation, keep-alive guard | n/a | 5 WECMS Oracle tables present (TB_TRNS_MAST/DETAIL, TB_FCY_EXRATE, TB_ACCT_HISTORY, TB_CARD_APPROVAL) | ✅ | No XA; env-var creds only; gated `haengsung.oracle.enabled=false` |
| **Manual Payment Refresh** | `pymn_0001_002_c002_act.jsp` → `ErpDataRefreshHelper.refreshErpData(period, type)` | `POST /api/v1/payments/haengsung/refresh` → `ErpPaymentRefreshService` → `ErpPaymentRefreshMasterImporter` | "Refresh" button (`useHaengsungRefresh` hook) on the 4 payment scheduled screens | Reads TB_TRNS_MAST/DETAIL → upserts SCDUL_/OVRS_ drafts; marks Oracle `ERP_RCV_FLAG='Y'`, detail `END_GB='2'` | ✅ | Approval-lock skip (`appr_seq_no` set), dedup on `erp_file_*`, per-master rollback all match legacy |
| **Scheduled Payment Import** | Batch `ERP_0100_002` — `last_status='00' AND erp_rcv_flag='N'` | `HaengsungScheduledPaymentImportJob` (reuses refresh service, per-company loop) | n/a (server-side) | Same target writes as manual refresh | ✅ | Flag `scheduled-payment-import.enabled=false`; cron `0 */5 * * * *`; ShedLock; `Asia/Ho_Chi_Minh` |
| **Approval Status Write-back** | `pymn_0001_001_c001_act.jsp` / approval `_act.jsp` → `ErpStatusUpdateHelper.updateErpStatus` (inline at approve/reject/transfer) | `HaengsungApprovalStatusWriteBackService` — `@TransactionalEventListener(AFTER_COMMIT)` on `PaymentApprovalStatusChangedEvent` | Approve/Reject in `CommonApprovalDetailsDialog`; transfer triggers PROCESSING | Oracle `TB_TRNS_MAST.LAST_STATUS` 10/20/32/51; reject also detail `PROC_STS='XXXX'`, `END_GB='3'` | ✅ (intentional: event-driven vs inline) | Status map identical; reject side-effects identical |
| **Dispatch Result Write-back** | Batch `ERP_0100_004` (vendor/expense/payroll/overseas) — close master `LAST_STATUS='52'` where `='51'` | `HaengsungDispatchResultWriteBackService` — `@TransactionalEventListener(AFTER_COMMIT)` on `PaymentDispatchResultFinalizedEvent` | n/a (server-side) | Oracle master `LAST_STATUS='52'`, `END_GB='1'`, NOR/ERR counts; detail `END_GB`/`PROC_STS`; WeCMS `erp_proc_cmpl_yn='Y'` | ✅ (intentional: event-driven vs batch) | `LAST_STATUS='51'` guard preserved; idempotent |
| **Exchange Rate Sync** | Batch `ERP_0100_001` — WeCMS `BANK_EXCH_RATE_INFM` → Oracle `TB_FCY_EXRATE` | `HaengsungExchangeRateSyncJob` + `HaengsungExchangeRateSyncService` + `HaengsungFcyExRateOracleRepository` | n/a | Reads `bank_exch_rate_infm` (Postgres), upserts Oracle `TB_FCY_EXRATE` (`INSERT … WHERE NOT EXISTS`/`UPDATE`), sets WeCMS `erp_snd_yn='Y'`, `ERP_GET_ST='0'` | ✅ | Flag `exchange-rate-sync.enabled=false`; CITAD bank-code mapping preserved |
| **Account History Sync** | Batch `ERP_0100_003` §account — WeCMS `ACCT_TRNS_HSTR` (`hand_reg_yn='N' AND erp_snd_yn='N'`) → Oracle `TB_ACCT_HISTORY` | `HaengsungCardAccountSyncJob` §account + `HaengsungCardAccountOracleRepository` | n/a | Reads `acct_trns_hstr` (Postgres), inserts Oracle `TB_ACCT_HISTORY`, sets `erp_snd_yn='Y'`; VND/FCY audit split | ✅ | Flag `card-account-sync.enabled=false` |
| **Card Approval Sync** | Batch `ERP_0100_003` §card — WeCMS card table (`CARD_MSTR_R005`, flag via `CARD_TRNS_HSTR_U003`) → Oracle `TB_CARD_APPROVAL` (`CARD_HLDR_NM→CARD_NM`, `PRCH_STTS→PURCHASE_ST`) | `HaengsungCardAccountSyncJob` §card | n/a | Reads `card_trns_hstr` (Postgres), inserts Oracle `TB_CARD_APPROVAL`, sets `erp_snd_yn='Y'` | ⚠️ verify | **F-1:** confirm refactor read source (`card_trns_hstr`) is the same row set legacy reads via `CARD_MSTR` (see §6) |
| **Web Refresh button** | JSP shows `#btnRefresh` only when `PrdtType.H`; else `#btn_excel` | endpoint enforces product-type `H` from JWT | `showRefresh = isHaengsung && onRefresh!==undefined`; `isProductType(user,'H')` | n/a | ✅ | Non-H → Excel Upload; gate matches legacy |
| **Request Approval** | `pymn_000X_001_c001` transfer → `EXPT_RESOL_MSTR` + `TB_PAY_FILE_MST`; sets PROCESSING(51) for H | per-module `requestApproval` endpoints | shared `RequestApprovalDialog` (vendor/expense/payroll/overseas/internal-transfer) | n/a | ✅ | — |
| **Approve/Reject popup** | approval `_act.jsp` approve→20 / reject→32 via `ErpStatusUpdateHelper` | `approval-box/{id}/approve` `/reject` + event → write-back | `CommonApprovalDetailsDialog`; action visible only to first PENDING approver | n/a | ✅ | Visibility = legacy `APPR_YN`/first-pending rule |

---

## 2. API verification (`wecms-api`)

| Check | Result | Evidence |
|---|---|---|
| Endpoint paths | ✅ | `POST /api/v1/payments/haengsung/refresh` (`HaengsungPaymentRefreshController`); approval via `approval-box/{apprSeqNo}/{approve,reject,withdraw}` |
| DTOs (snake_case wire) | ✅ | Req `HaengsungRefreshRequest{payment_type, period_date}`; Res `HaengsungRefreshResult{masters_found, imported, skipped, failed, details_imported}` |
| Feature flags | ✅ OFF | `haengsung.oracle.enabled=false` gates all beans via `@ConditionalOnProperty` |
| Scheduler flags | ✅ OFF | `scheduled-payment-import.enabled`, `exchange-rate-sync.enabled`, `card-account-sync.enabled` all default `false`; cron `0 */5 * * * *`; zone `Asia/Ho_Chi_Minh`; ShedLock `PT10M/PT30S` |
| Oracle SQL | ✅ | Reads filtered on `LAST_STATUS='00'`, `ERP_RCV_FLAG`, `END_GB='0'`, `BIZ_NO`, date range + type predicate (`FILE_GB='AF1'` overseas / `'AC2'` payroll / `FILE_NM LIKE 'E%'` expense). Write-backs match legacy column-for-column (`ERP_RCV_FLAG='Y'`, `LAST_STATUS` 20/32/52, `PROC_STS='XXXX'`, `END_GB`). FX/Account/Card use idempotent `INSERT … WHERE NOT EXISTS` + `UPDATE` |
| Transaction order | ✅ | WeCMS import commits first; Oracle write-back runs **post-commit** (non-blocking); audit row in `REQUIRES_NEW`. No XA — independent commits, safe via dedup + receive-flag guards |
| Event publishing | ✅ | `PaymentApprovalStatusChangedEvent` & `PaymentDispatchResultFinalizedEvent` consumed by `@TransactionalEventListener(phase=AFTER_COMMIT)` |
| Error handling | ✅ | `WBE_HS_REFRESH_DISABLED` / `_NOT_HAENGSUNG` / `_NO_TAX_ID` via `messages.properties`; one-shot retry on key-allocation race; per-company/per-master isolation |
| Tests | ✅ | 14 test classes (config, properties, repository, importer, refresh service, controller, scheduled job + wiring, approval write-back + wiring, dispatch, exchange-rate, card-account) — all passing 2026-06-28 |
| No credential leak | ✅ | URL/user/password are `${HAENGSUNG_ORACLE_*}` env vars only; `.env.sit`/`setenv.sh` gitignored; `.env.sit.example` placeholders; errors log SQLState/code, never connection string |

---

## 3. Web verification (`wecms-web`)

| Check | Result | Evidence |
|---|---|---|
| HAENGSUNG shows Refresh | ✅ | `PaymentScheduledBatchListPage` `showRefresh={isHaengsung && onRefresh!==undefined}`; `isHaengsung = isProductType(userStorage.get(),'H')` |
| Non-HAENGSUNG shows Excel Upload | ✅ | ternary renders `<ExcelUploadButton>` when `showRefresh` is false |
| Internal Transfer unchanged | ✅ | `ScheduledTransferPage` never passes `onRefresh` → always Excel Upload regardless of product type |
| Refresh calls backend API | ✅ | `useHaengsungRefresh` → `haengsungPaymentRefresh()` → `POST /api/v1/payments/haengsung/refresh` with `{payment_type, period_date}` |
| Loading state | ✅ | `refreshing` guard blocks re-entry; button `disabled` + `RefreshCw animate-spin` |
| Success / error message | ✅ | success dialog `payment.haengsung.refresh.success` (imported/transactions); error dialog maps `WBE_HS_REFRESH_*` + generic fallback |
| List reload | ✅ | `onSuccess()` → `refreshList()` bumps `refreshKey`, re-fetch preserves filter/sort/page |
| Request approval path | ✅ | shared `RequestApprovalDialog` (`kind`-dispatched to module endpoints) |
| Approve/Reject popup path | ✅ | `CommonApprovalDetailsDialog`; `isCurrentUserActiveApprover` (first-pending) + `isFinalApprovalState` gate buttons |
| No missing HAENGSUNG hook | ✅ | single `useHaengsungRefresh` hook, fully implemented, shared by all 4 payment modules; no stubs/TODOs |

**Intentional web difference (D1):** refactor shows a success dialog after import; legacy showed none. Documented improvement, not a defect.

---

## 3A. UI Integration Result

Final UI behaviour after the HAENGSUNG Payment Refresh implementation. The scheduled-payment screen renders **one** primary import control per module, selected by product type: standard tenants always see **Excel Upload**; HAENGSUNG (`PRDT_TYPE='H'`) sees **Refresh (ERP Import)** on the four supported modules, and keeps **Excel Upload** on Internal Transfer.

**Implementation matrix**

| Payment module | Standard WeCMS | HAENGSUNG (`PRDT_TYPE='H'`) |
|---|---|---|
| Internal Transfer | Excel Upload | **Excel Upload** (unchanged — no Refresh) |
| Vendor Payment | Excel Upload | **Refresh (ERP Import)** |
| Expense Payment | Excel Upload | **Refresh (ERP Import)** |
| Payroll | Excel Upload | **Refresh (ERP Import)** |
| Overseas Payment | Excel Upload | **Refresh (ERP Import)** |

**Observed final behaviour (verified ✅):**

- **Button selection is product-type-gated.** `PaymentScheduledBatchListPage` renders `showRefresh = isProductType(user,'H') && onRefresh !== undefined`; when true it shows the **Refresh** button, otherwise it shows `<ExcelUploadButton>`. Product type is read from the signed JWT / stored user, never a request field.
- **Refresh replaces Excel Upload only on the four supported modules.** Vendor / Expense / Payroll / Overseas pass `onRefresh` to the shared list page, so under HAENGSUNG they render Refresh; under any other product type they render Excel Upload.
- **Internal Transfer always shows Excel Upload.** `ScheduledTransferPage` never passes `onRefresh`, so Excel Upload renders regardless of product type — including HAENGSUNG. No Refresh button, no Oracle import path.
- **Refresh drives the import end-to-end.** The single shared `useHaengsungRefresh` hook → `haengsungPaymentRefresh()` → `POST /api/v1/payments/haengsung/refresh` (`{payment_type, period_date}`); on success it reloads the list (preserving filter / sort / page) and shows the result dialog (D1). Loading state disables the button and animates the spinner; a re-entry guard prevents duplicate clicks.
- **No standard-tenant regression.** Non-`H` tenants are unaffected — every module keeps Excel Upload.

Module-level source-of-truth matrix: [payment/README.md → "Payment import strategy"](payment/README.md#payment-import-strategy--standard-wecms-vs-haengsung). Feature scope: [haengsung-payment-refresh.md → "Scope"](haengsung-payment-refresh.md#scope). Frontend legacy-parity detail: [wecms-web/docs/features/payment/haengsung-payment-refresh-frontend-parity.md](../../../wecms-web/docs/features/payment/haengsung-payment-refresh-frontend-parity.md).

---

## 4. DB verification

### 4.1 Oracle (HAENGSUNG SIT, `WECMS` schema — read-only)

Listed tables = exactly the 5 expected interface tables. Required columns **all present** (column-id verified):

| Table | Cols | Key columns confirmed | Rows |
|---|---|---|---|
| `TB_TRNS_MAST` | 37 | FILE_DATE, FILE_NUM, FILE_GB, FILE_NM, BIZ_NO, OUT_BANK_CD, OUT_ACCT_NO, REGI_CNT, REGI_AMT, LAST_STATUS, ERP_RCV_FLAG(+USR/DT), END_GB, END_DATETIME, NOR/BUL/ERR cnt+amt, IHB_UDT_* | 22 |
| `TB_TRNS_DETAIL` | 101 | FILE_DATE, FILE_NUM, FILE_SEQ, END_GB, TR_AMT, ISO_CODE, SEND_GBN, ANT_DIV_CD, ACET_NM, TR_ACCT_NO, TR_ACCT_BANK_CD/NM, JEOKYO, FORI_FEE_MAN, ATTRIBUTE2, PROC_STS(+_MSG), FLAG(+USR/DT), IF_SND_* , TR_DATE | 40 |
| `TB_FCY_EXRATE` | 15 | BIZ_NO, BANK_CD, REF_DT, CCY_CD, REF_SEQ, REF_TM, BASIC_RATE, SELL/BUY_AMT, TRAN_SELL/BUY_AMT, LAST_INQ_DTTM, CMS_PUT_DTTM, ERP_GET_ST | 6,338 |
| `TB_ACCT_HISTORY` | 19 | BIZ_NO, BANK_CD, ACCT_NO, TR_DT, TR_SEQNO, CCY_CD, IO_GB, TR_AMT, TR_CUR_BAL, ACCT_MEMO, LAST_INQ_DTTM, CMS_PUT_DTTM, ERP_GET_ST | 1,543 |
| `TB_CARD_APPROVAL` | 21 | BIZ_NO, BANK_CD, CARD_NO, APPR_DT, APPR_TM, AUTH_NO, CARD_NM, MERCH_NM, CCY_CD, APPR_AMT, PURCHASE_ST, CITY, LAST_INQ_DTTM, CMS_PUT_DTTM, ERP_GET_ST | 112 |

Every column the API SQL references exists with a compatible type. **No write operations performed** — only `list_tables` and `SELECT` (the MCP is SELECT/WITH-only by construction).

### 4.2 WeCMS refactor Postgres (`wecmsdevdb` schema)

All 8 target tables present; required ERP-integration columns confirmed:

| Table | Confirmed columns |
|---|---|
| `scdul_pymn_sumr` | `appr_seq_no`, `trnf_yn`, `erp_file_date`, `erp_file_num`, `erp_proc_cmpl_yn`, `pay_file_seq_no`, org/save_file_nm |
| `scdul_pymn_sumr_detl` | `erp_file_date`, `erp_file_num`, `erp_file_seq`, `erp_attribute2`, `pay_file_seq_no` |
| `ovrs_pymn_sumr` | `appr_seq_no`, `trnf_yn`, `erp_file_date`, `erp_file_num`, `erp_proc_cmpl_yn` |
| `ovrs_pymn_sumr_detl` | `erp_file_date`, `erp_file_num`, `erp_file_seq`, `erp_attribute2` |
| `erp_trns_hstr` | `erp_type`, `org_file_nm`, `save_file_nm`, `file_size` |
| `bank_exch_rate_infm` | `erp_snd_yn` |
| `acct_trns_hstr` | `hand_reg_yn`, `erp_snd_yn` |
| `card_trns_hstr` | `appr_seq_no`, `erp_snd_yn` |

> Note: refactor tables live in schema `wecmsdevdb` (not `public`); table/column names are lowercase. Entity `@Column` mappings target these legacy names, with snake_case kept at the wire layer only.

---

## 5. Legacy verification (JEX — read-only baseline)

| Artifact | Confirmed | Source-of-truth behavior |
|---|---|---|
| `ErpDataRefreshHelper` | ✅ | `WECMS/src/wabooks/helper/ErpDataRefreshHelper.java` — period/type refresh; skips rows with `appr_seq_no`; sets `ERP_RCV_FLAG='Y'`, detail `END_GB='2'`/`FLAG='Y'`; Vietnamese tone-strip + whitespace removal |
| `ErpStatusUpdateHelper` | ✅ | `WECMS/src/wabooks/helper/ErpStatusUpdateHelper.java` — PENDING→10, APPROVED→20, REJECTED→32, PROCESSING→51; reject sets master `END_GB='1'` + detail `PROC_STS='XXXX'`, `END_GB='3'` |
| `ERP_0100_001` | ✅ | Exchange rate WeCMS→Oracle (`BANK_EXCH_RATE_INFM`→`TB_FCY_EXRATE`), `erp_snd_yn` flip, `ERP_GET_ST='0'` |
| `ERP_0100_002` | ✅ | Scheduled payment import Oracle→WeCMS (`erp_rcv_flag='N'`), per-type audit rows |
| `ERP_0100_003` | ✅ | Account-history + card-approval WeCMS→Oracle; account VND/FCY split; `CARD_HLDR_NM→CARD_NM`, `PRCH_STTS→PURCHASE_ST` |
| `ERP_0100_004` | ✅ | Dispatch result write-back; close master `LAST_STATUS='52'` where `='51'`; NOR/ERR counts |
| Legacy Refresh button | ✅ | `pymn_0001_002_c002_act.jsp`; H-only via `PrdtType.H` |
| Legacy approval request | ✅ | `pymn_000X_001_c001_act.jsp` → `EXPT_RESOL_MSTR` + `TB_PAY_FILE_MST`; PROCESSING(51) for H |
| Legacy approve/reject | ✅ | approval `_act.jsp` → `ErpStatusUpdateHelper` (20 / 32) |
| Legacy dispatch result update | ✅ | `ERP_0100_004` master close + detail status |

**Status-flag dictionary (legacy = refactor):** `LAST_STATUS` 00/10=pending, 20=approved, 32=rejected, 51=processing, 52=complete · `ERP_RCV_FLAG` Y/N · master `END_GB` 0/1/2 · detail `END_GB` 0/2/3 · detail `PROC_STS='XXXX'` on reject · `ERP_SND_YN`/`ERP_GET_ST` for FX/account/card.

---

## 6. Findings & open items

**Intentional improvements (approved drift, not defects):**
- **I-1 — Event-driven write-back.** Approval-status (Step 4) and dispatch-result (Step 5) write-backs are `AFTER_COMMIT` event listeners in the refactor, versus inline/batch (`ERP_0100_004`) in legacy. Same Oracle effect and same `LAST_STATUS='51'` guard; better transactional safety. Documented in the sibling specs.
- **I-2 — Web success dialog (D1).** Refactor shows an import-result dialog; legacy showed none.
- **I-3 — Inline spinner (D2) / single-day period emits `YYYYMMDD` (D3).** Cosmetic; backend treats single date and range identically.

**Verification item (low severity):**
- **F-1 — Card-sync read source.** Legacy `ERP_0100_003` reads card rows via `CARD_MSTR_R005` and flips the flag via `CARD_TRNS_HSTR_U003`; the refactor reads `card_trns_hstr` directly. Both target Oracle `TB_CARD_APPROVAL` with the same field mapping, and `card_trns_hstr` carries `appr_seq_no` + `erp_snd_yn`. Confirm the refactor's `card_trns_hstr` selection yields the **same row set** legacy derives from `CARD_MSTR` (join/filter parity) before enabling the flag in SIT. Owner: dev + PO; tracked under SIT verification.

**Pre-existing open questions (unchanged, still open):** Expense vs Vendor `FILE_NM LIKE 'E%'` / `ANT_DIV_CD` discrimination (M-2/M-12 in [haengsung-payment-refresh.md](haengsung-payment-refresh.md)) — type discriminators only, do not block payment paths.

**Remaining work:** Step 8 end-to-end SIT verification against live Oracle (checklist in [haengsung-oracle-integration-sit-verification.md](haengsung-oracle-integration-sit-verification.md)) — flags flipped ON in SIT, full round-trip, F-1 confirmation.

---

## 7. Conclusion

The HAENGSUNG Oracle integration is **functionally complete and faithful to legacy JEX behavior** across API, Web, Oracle, WeCMS DB, and legacy baseline. All interface tables and target columns exist and are correctly mapped; SQL, status flags, transaction ordering, and approval/dispatch semantics match legacy; the only departures are documented intentional improvements. Feature/scheduler flags are OFF by default, credentials are env-only with no leak, and 14 test classes pass. The integration is **ready for Step 8 SIT verification**, with **F-1 (card-sync source parity)** as the one item to confirm under live data.
