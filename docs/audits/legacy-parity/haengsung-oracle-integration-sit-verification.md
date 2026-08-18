# HAENGSUNG Oracle Integration — SIT End-to-End Verification (Step 8)

**Slug:** `haengsung-oracle-integration-sit-verification` · **Domain:** Payment / Banking (ERP integration) · **Product type:** `H` (HAENGSUNG Electronics) only · **Roadmap step:** 8 (P6 — gate to UAT) · **Verified:** 2026-06-28 · **Verifier:** Claude Code (AI), workspace `c:\WECMS PROJECT`

> **Scope:** verification only. No feature code was added or changed during this pass (per the Step-8 instruction). Steps 1–7 are implemented; this document records what was verified, how, and what remains to be executed on the live SIT box.
>
> Cross-refs: [haengsung-oracle-integration-roadmap.md](../../planning/haengsung-oracle-integration-roadmap.md) §Step 8 · per-feature specs [refresh](../../features/integrations/haengsung/payment-refresh.md) · [approval write-back](../../features/integrations/haengsung/approval-status-writeback.md) · [dispatch write-back](../../features/integrations/haengsung/dispatch-result-writeback.md) · [FX sync](../../features/integrations/haengsung/exchange-rate-sync.md) · [card/account sync](../../features/integrations/haengsung/card-account-sync.md).

---

## 0. Verification method & honest scope boundary

**Two verification surfaces exist for Step 8, and they are kept strictly separate in this report:**

| Surface | What it proves | Could it run from this workspace? |
|---|---|---|
| **A — Static** (code ↔ spec ↔ test ↔ repo hygiene) | The implementation writes the correct Oracle columns/literals, gates correctly, and commits no secrets | **Yes — executed.** Results below. |
| **B — Live SIT runtime** (real Oracle `DEV` instance + running SIT app + `catalina.out`) | The flows actually move data end-to-end against the real Oracle and produce the expected rows/logs | **No — not reachable from here.** Prepared, not executed. |

**Why Surface B could not be executed from this environment (material fact, not an omission):**

- The local developer `.env.sit` (gitignored) contains **no `HAENGSUNG_ORACLE_*` keys at all** — so this workspace cannot open the secondary Oracle datasource (`haengsung.oracle.enabled` resolves to the default `false`).
- The real Oracle host/SID + credentials live **only in the SIT Tomcat `bin/setenv.sh`** OS-env (per memory `reference_sit_server_config_setenv`), which is on the SIT box and not present here.
- `catalina.out` / SIT application logs live on the SIT host and were not accessible from this workspace.

**Therefore:** every runtime checklist item below is marked **`PENDING-SIT`** with the exact SQL / steps a SIT operator must run (Section 9). No runtime item is reported as PASS that was not actually observed — claiming otherwise would be false. Static items that *fully cover* a requirement at the code level are marked **`PASS (static)`**.

---

## 1. Environment

| Item | Value |
|---|---|
| Repo | `wecms-api` (Spring Boot 3.5.13, Java 21 Temurin), local workspace `c:\WECMS PROJECT\wecms-api` |
| Build/test toolchain | `JAVA_HOME=Eclipse Adoptium jdk-21.0.10.7-hotspot`; Gradle wrapper `./gradlew` |
| SIT app endpoints (reference) | `https://wecms-api.kosign.dev` / `https://wecms.kosign.dev` (memory `reference_sit_endpoints`) |
| SIT config source | Tomcat `bin/setenv.sh` OS env (authoritative on SIT; `.env.sit` is NOT read there) |
| Oracle target (reference) | external Oracle `DEV` instance — host/SID per DBA; **not** in the WeCMS Flyway inventory, no `@Entity` |
| WeCMS DB (SIT) | shared dev PostgreSQL (SIT shares the dev DB per memory `reference_sit_endpoints`) |
| HAENGSUNG tenant | `BIZ_KEY = VN_VNM0004_000014` (HAENGSUNG ELECTRONICS, `PRDT_TYPE='H'`); `BIZ_NO` used against Oracle = `BUSI_INFM.TAX_ID` of that tenant |

---

## 2. Configuration used (redacted)

Resolved binding (`application.yml` → env). **Defaults are OFF; only SIT `setenv.sh` flips them on.** No value below is a secret; the real URL/user/password are never printed here or in logs.

```yaml
haengsung:
  oracle:
    enabled:  ${HAENGSUNG_ORACLE_ENABLED:false}        # default false
    url:      ${HAENGSUNG_ORACLE_URL:}                  # jdbc:oracle:thin:@//<host>:1521/<service>  (set in setenv.sh)
    username: ${HAENGSUNG_ORACLE_USERNAME:}             # set in setenv.sh
    password: ${HAENGSUNG_ORACLE_PASSWORD:}             # set in setenv.sh — REDACTED, never logged
    # pool/keep-alive defaults: maxPoolSize=5, minIdle=1, connectionTimeoutMs=30000,
    # keepaliveMs=120000 (ORA-17008 guard), validationTimeoutMs=5000
  scheduled-payment-import:
    enabled: ${HAENGSUNG_SCHEDULED_IMPORT_ENABLED:false}        # Step 3, default false
    cron:    ${HAENGSUNG_SCHEDULED_IMPORT_CRON:0 */5 * * * *}    # ICT
  exchange-rate-sync:
    enabled: ${HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED:false}      # Step 6, default false
    cron:    ${HAENGSUNG_EXCHANGE_RATE_SYNC_CRON:0 */5 * * * *}
  card-account-sync:
    enabled: ${HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED:false}       # Step 7, default false
    cron:    ${HAENGSUNG_CARD_ACCOUNT_SYNC_CRON:0 */5 * * * *}
```

**On SIT, set in `bin/setenv.sh` (host-side; example, redacted):**

```sh
export HAENGSUNG_ORACLE_ENABLED=true
export HAENGSUNG_ORACLE_URL='jdbc:oracle:thin:@//<sit-oracle-host>:1521/<service-or-SID>'   # confirm SID vs service (roadmap OQ-12)
export HAENGSUNG_ORACLE_USERNAME='<erp_user>'
export HAENGSUNG_ORACLE_PASSWORD='********'                                                  # REDACTED
# enable the scheduled jobs only when the cadence is PO-approved:
# export HAENGSUNG_SCHEDULED_IMPORT_ENABLED=true
# export HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED=true
# export HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED=true
```

---

## 3. Pre-check results (credential & repo hygiene) — **PASS**

| Pre-check | Result | Evidence |
|---|---|---|
| No real credentials committed | **PASS** | `git grep` over tracked files (excluding docs) finds no real Oracle host (`172.168.5.x`), no `jdbc:oracle:thin:@//<realhost>`, no password literal. Only placeholders (`<sit-oracle-host>`, `<service>`) and fake test values (`localhost`/`host`/`secret`). |
| `.env.sit` ignored & untracked | **PASS** | `git check-ignore` → `.gitignore:42 (.env.*)`. `git ls-files` lists only `*.example` env files. Local `.env.sit` exists but is untracked + ignored. |
| `SIT oracle db.txt` ignored | **PASS** | `.gitignore:50-51` (`SIT oracle db.txt`, `*oracle*db*.txt`). File does not exist locally; rule covers it if a DBA drops one. |
| `HAENGSUNG_ORACLE_ENABLED=true` only in local/SIT env (not committed) | **PASS** | `application.yml` default `false`; `.env.sit.example` ships `false`; local `.env.sit` carries **no** HAENGSUNG keys (so locally OFF). True is set only in SIT `setenv.sh` (off-repo). |
| Real SIT uses Tomcat `bin/setenv.sh` | **PASS (design confirmed)** | `HaengsungOracleProperties` Javadoc + memory `reference_sit_server_config_setenv`; `.env.sit` is not read on SIT. |
| Oracle URL uses correct SID/service | **PENDING-SIT** | URL form is correct (`jdbc:oracle:thin:@//host:1521/<service>`); the **SID-vs-service** choice for instance `DEV` is roadmap blocker **OQ-12** — confirm with DBA before enabling. |
| Oracle connectivity (`SELECT 1 FROM dual`) | **PENDING-SIT** | Probe code is correct (Section 4); actual round-trip must run on SIT (Section 9.1). |

**No secret is exposed in this report.** Password is redacted everywhere; host/user are placeholders.

---

## 4. Static verification — per flow

Static verification was performed by reading each implementation class against its spec and the legacy JEX source of truth, quoting the exact Oracle SQL literals, and running the unit-test suite that encodes those literals (Section 5). Legend: **PASS (static)** = code fully matches spec at the literal level; **PENDING-SIT** = correctness depends on a live run.

### 4.1 Oracle Foundation (Step 1) — **PASS (static)**

| Check | Verdict | Evidence (file:line) |
|---|---|---|
| App boots with Oracle disabled | PASS (static) | `HaengsungOracleDataSourceConfig` + `HaengsungOracleHealthCheck` both `@ConditionalOnProperty(haengsung.oracle.enabled=true)` → no Oracle bean when off; Postgres autoconfig unchanged. |
| App boots with Oracle enabled | PENDING-SIT | beans wire correctly; actual boot-with-Oracle is a SIT step. |
| Oracle health check succeeds | PENDING-SIT | `HaengsungOracleHealthCheck.isReachable()` runs `SELECT 1 FROM dual`; never throws, never fails startup; logs OK/warn only. Run on SIT (Section 9.1). |
| Postgres remains primary | PASS (static) | `dataSource()` re-declared `@Primary` bound to `spring.datasource.*`; Oracle datasource is secondary, JdbcTemplate-only (no JPA/Flyway). `HaengsungOracleDataSourceConfig.java:55-67`. |
| No credential leakage in logs | PASS (static) | Properties Javadoc + `HaengsungOracleHealthCheck.describe()` emits exception class + `SQLState`/`errorCode` only — never URL/user/password. |
| ORA-17008 stale-connection guard | PASS (static) | Hikari `connectionTestQuery='SELECT 1 FROM dual'` + `keepaliveTime=120000ms` + `validationTimeout` + `initializationFailTimeout(-1)` (never blocks startup). `HaengsungOracleDataSourceConfig.java:85-96`. |
| Fail-fast on missing var when enabled | PASS (static) | compact constructor throws `IllegalStateException` naming the missing setting, never its value. `HaengsungOracleProperties.java:77-102`. |
| Oracle JDBC driver present, inert when off | PASS (static) | `build.gradle:69` `runtimeOnly 'com.oracle.database.jdbc:ojdbc11:23.4.0.24.05'`. |

### 4.2 Manual Payment Refresh (Step 2) — **PASS (static)**

Verified against [haengsung-payment-refresh.md](../../features/integrations/haengsung/payment-refresh.md) §5/§6/§7/§16/§9A.

| Check | Verdict | Evidence |
|---|---|---|
| Endpoint + product-type `H` guard + `@menuAuthz`; non-H rejected | PASS (static) | `HaengsungPaymentRefreshController` `POST /api/v1/payments/haengsung/refresh`, `@PreAuthorize @menuAuthz.canAccessAny(...)`; real guard in `ErpPaymentRefreshService.resolveAndGuard()` (reads `PrdtType` from JWT/TenantContext, never the request) → `WBE_HS_REFRESH_NOT_HAENGSUNG`. |
| Reads `TB_TRNS_MAST` (`LAST_STATUS='00'`, biz_no, period, type) | PASS (static) | `HaengsungErpOracleRepository.findMasters`: `WHERE LAST_STATUS='00' AND BIZ_NO=? AND FILE_DATE BETWEEN ? AND ?` + type predicate; `biz_no` ← `BUSI_INFM.TAX_ID`. |
| Reads `TB_TRNS_DETAIL` (`END_GB='0'`) | PASS (static) | `findDetails`: `WHERE FILE_DATE=? AND FILE_NUM=? AND END_GB='0'`. |
| Vendor / Expense / Payroll / Overseas / ALL | PASS (static) | `HaengsungPaymentType` 5 values; predicate OVERSEAS→`FILE_GB='AF1'`, PAYROLL→`FILE_GB='AC2'`, EXPENSE→`FILE_NM LIKE 'E%'`, VENDOR→complement, ALL→no narrowing. **M-2** (Expense `E%`) carried verbatim per spec — open PO blocker, not a code defect. |
| Upserts SCDUL_* / OVRS_* drafts | PASS (static) | `ErpPaymentRefreshMasterImporter` routes overseas→`OVRS_PYMN_SUMR(_DETL)`, else `SCDUL_PYMN_SUMR(_DETL)`. |
| Dedup idempotent; `END_GB<>'0'` skipped | PASS (static) | dedup key `findFirstByBizKeyAndErpFileNumAndErpFileDate`; existing→update, new→insert; details matched by `ERP_FILE_SEQ`; `END_GB<>'0'` never read. |
| Approval-lock skip | PASS (static) | `if existing has APPR_SEQ_NO → MasterImportOutcome.approvalLocked()`; counted skipped, no write-back, no audit. |
| Oracle receive write-back MAST `ERP_RCV_FLAG='Y'` | PASS (static) | `markMasterReceived`: `SET ERP_RCV_FLAG='Y', ERP_RCV_FLAG_USR='WEBCASH', ERP_RCV_FLAG_DT=nowVn, IHB_UDT_USR='WEBCASH', IHB_UDT_DT=nowVn`. |
| Oracle receive write-back DETAIL `END_GB='2'`, `FLAG='Y'` | PASS (static) | `markDetailReceived`: `SET END_GB='2', FLAG='Y', IF_SND_ID='WEBCASH', IF_SND_DT=nowVn, FLAG_USR='WEBCASH', FLAG_DT=nowVn, IHB_UDT_USR='WEBCASH', IHB_UDT_DT=nowVn`. |
| Per-master rollback; `ERP_TRNS_HSTR` audit | PASS (static) | per-master `@Transactional`; orchestrator catches per master and continues; audit on IMPORTED only. |
| Key gen via `wb_get_next_val` funnel | PASS (static) | `LegacyFunctionRepository.next{Scdul,Ovrs}Pymn(Detl)No`; no new ID scheme. |

### 4.3 Scheduled Import Batch (Step 3) — **PASS (static)**

| Check | Verdict | Evidence |
|---|---|---|
| Disabled by default | PASS (static) | `@ConditionalOnProperty(haengsung.scheduled-payment-import.enabled=true, matchIfMissing=false)`; yml default `false`. |
| Enabled only with env flag; no-op if Oracle off | PASS (static) | `runOnce()` no-ops when `refreshServiceProvider.getIfAvailable()==null` (service itself gated on `haengsung.oracle.enabled`). |
| Cron runs; reuses the SAME `ErpPaymentRefreshService` | PASS (static) | `@Scheduled(cron, zone=Asia/Ho_Chi_Minh)` → `service.importUnreceivedForCurrentTenant()`; no re-port of legacy `ERP_0100_002` inline logic (§9A honoured). |
| HAENGSUNG businesses only | PASS (static) | `busiInfmRepository.findByPrdtTypeAndDelYn("H","N")`. |
| Imports unreceived data only | PASS (static) | `findUnreceivedMasters`: `WHERE LAST_STATUS='00' AND ERP_RCV_FLAG='N' AND BIZ_NO=?`. |
| No duplicate import on manual+scheduled overlap | PASS (static) | `ERP_RCV_FLAG='N'` filter + dedup + approval-lock make overlap idempotent. |
| Concurrency guard | PASS (static) | `@SchedulerLock(name=..., lockAtMostFor=PT10M, lockAtLeastFor=PT30S)` (ShedLock); deps in `build.gradle`. |

### 4.4 Approval-Status Write-back (Step 4) — **PASS (static)** with 3 LOW notes

| Check | Verdict | Evidence |
|---|---|---|
| Pending→`LAST_STATUS='10'` | PASS (static) | `lastStatusCode` switch; non-reject path `updateMasterApprovalStatus(...,'10',...)`. |
| Approved→`'20'` | PASS (static) | same switch, literal `"20"`. |
| Processing→`'51'` | PASS (static) | same switch, literal `"51"`. |
| Rejected→`'32'` | PASS (static) | reject branch hard-codes `LAST_STATUS='32'` in SQL. |
| Reject detail `PROC_STS='XXXX'`, `END_GB='3'` | PASS (static) | `UPDATE TB_TRNS_DETAIL SET PROC_STS='XXXX', END_GB='3', IHB_UDT_DT=nowVn, TR_DATE=today`. |
| Reject master close `END_GB='1'`, `END_DATETIME`, `BUL_CNT/AMT`, `IHB_UDT_*` | PASS (static) | `UPDATE TB_TRNS_MAST SET LAST_STATUS='32', IHB_UDT_USR='WEBCASH', IHB_UDT_DT=nowVn, END_GB='1', END_DATETIME=nowVn, BUL_CNT=totCnt, BUL_AMT=totAmt`. |
| Trigger = approval state change (not batch) | PASS (static) — LOW note 1 | wired per-action via `@TransactionalEventListener(AFTER_COMMIT)` from the approval/dispatch services. Behaviourally per-action; *literally* "synchronous" wording in spec differs from the AFTER_COMMIT design (documented & intentional). |
| Non-ERP batch (blank `ERP_FILE_*`) skipped; unknown status no-op | PASS (static) | blank-key guard returns early; status is a closed 4-value enum so "unknown" is unrepresentable (structural no-op). |
| Fires only for `PRDT_TYPE='H'`; reuses Step 1 Oracle path | PASS (static) — LOW note 2 | `isHaengsung(bizKey)` gate; same `HaengsungErpOracleRepository` + secondary Oracle JdbcTemplate, `@ConditionalOnProperty`. Uses literal `"H".equals(...)` rather than `PrdtType.fromCode(...)` (convention divergence; functionally correct + adds `del_yn` guard). |
| No credential logging | PASS (static) — LOW note 3 | no credential ever logged. Failure path logs the **full exception** (stack), not a curated `SQLState/code`-only line — meets the no-credential intent but not roadmap §112's literal wording. |

### 4.5 Dispatch-Result Write-back (Step 5) — **PASS (static)** with 1 MEDIUM (by-design) + 1 LOW

| Check | Verdict | Evidence |
|---|---|---|
| Success line → DETAIL `END_GB='8'`, `PROC_STS=RS_CD` | PASS (static) | `detailResult`: `PROC_STAT='0' && RS_CD='0000'` → `("8", rsCd)`. |
| Fail `PROC_STAT='3'` → `END_GB='5'`, `PROC_STS='0999'` | PASS (static) | first branch `("5","0999")` (overrides regardless of RS_CD). |
| Fail `PROC_STAT='0' && RS_CD≠'0000'` → `END_GB='5'`, `PROC_STS=RS_CD` | PASS (static) | fallthrough `("5", rsCd)`. |
| All lines set `PROC_STS_MSG=RS_MSG`, `TR_DATE=TRAN_DT`, `IHB_UDT_DT` | PASS (static) | `updateDetailDispatchResult(...)` binds `PROC_STS_MSG=rsMsg`, `TR_DATE=payTranDt`, `IHB_UDT_DT=nowVn`. |
| Master close `LAST_STATUS='52'`, `END_GB='1'`, `NOR/ERR_CNT/AMT`, `END_DATETIME` | PASS (static) | `updateMasterDispatchResult`: `SET LAST_STATUS='52', ..., END_GB='1', END_DATETIME=?, NOR_CNT=?, NOR_AMT=?, ERR_CNT=?, ERR_AMT=? WHERE ... AND LAST_STATUS='51'`. |
| WeCMS summary `ERP_PROC_CMPL_YN='Y'` | PASS (static) | `markErpProcComplete(...)` called **only after** successful Oracle writes; skipped on Oracle failure (test `oracleFailure_doesNotFlipFlag...`). |
| Finalize only when ALL details complete; incomplete → skip + retry | PASS (static) | `isAllDetailsComplete` (any blank `procStat`/`rsCd` → false) gates both paths; missing dispatch row → `DispatchResult.EMPTY` → incomplete. |
| Only updates Oracle masters at `LAST_STATUS='51'` | PASS (static) | `WHERE ... AND LAST_STATUS='51'` (0-row no-op otherwise). |
| Single trigger | PASS (static) | `BulkExecutionResultService.finalizePayFileStatus` publishes `PaymentDispatchResultFinalizedEvent` exactly once on finalization; sole `@TransactionalEventListener(AFTER_COMMIT)` subscriber; swallows `RuntimeException`. |
| Gated; reuses Step 1 path; no credential logging | PASS (static) — LOW note | gated `@ConditionalOnProperty(haengsung.oracle.enabled=true)`; same Oracle JdbcTemplate; no credential logged; **but** error path logs full exception, not `SQLState/code`-only (same LOW as 4.4). |
| Audit-timestamp zone | **MEDIUM (by-design — confirm)** | Oracle `IHB_UDT_DT`/`END_DATETIME` use VN-zone `now()` (matches spec §7 + legacy); the WeCMS-side `markErpProcComplete` audit uses **UTC** (`utcNow()`). The two stamps for one operation differ by 7h. This is **consistent with the established WeCMS convention** (WeCMS `_DTM` columns stored UTC `yyyyMMddHHmmss` — see memory `project_payment_datetime_tz_locale`), and the Oracle column legitimately follows the Oracle/legacy VN convention. Recorded as a confirmation point, not a defect. |

### 4.6 Exchange-Rate Sync (Step 6) — **PASS (static)**

Verified against [haengsung-exchange-rate-sync.md](../../features/integrations/haengsung/exchange-rate-sync.md) + legacy `ERP_0100_001.java`.

| Check | Verdict | Evidence |
|---|---|---|
| Reads `BANK_EXCH_RATE_INFM` (today, per H tenant) | PASS (static) | `findByPrdtTypeAndDelYn("H","N")` → `findByBizKeyAndRefDate(bizKey, todayICT)`. |
| Writes Oracle `TB_FCY_EXRATE` (all required fields) | PASS (static) | INSERT/UPDATE write `REF_DT/REF_SEQ/REF_TM/BASIC_RATE/SELL_AMT/BUY_AMT/TRAN_SELL_AMT/TRAN_BUY_AMT/LAST_INQ_DTTM/CMS_PUT_DTTM/ERP_GET_ST='0'`; `CCY_CD=REF_CCY_CD`, `BIZ_NO=TAX_ID` (legacy-verbatim mapping). |
| Insert vs update | PASS (static) | `erp_snd_yn='Y'` → UPDATE on PK; else idempotent INSERT (`WHERE NOT EXISTS`). |
| `ERP_SND_YN='Y'` only AFTER Oracle success | PASS (static) | `pushRate`: Oracle write in try; `catch → return FAILED` (WeCMS untouched); `markErpSent` only after success. Safer than legacy (which flips inside the shared tx). |
| Bank-code reverse-map (CITAD↔original) | PASS (static) | forward map `{11704020→01663001, 11704088→79616001}` (exact inverse of legacy); unmapped bank → skipped + logged. |
| 0-row run writes `ERP_TRNS_HSTR` audit | PASS (static) | `recordBatchPush` called per company unconditionally (`_NO_DATA` suffix when 0); `ERP_TYPE='W'`, `REG_ID='SYSTEM'`, `FILE_SIZE=0`. |
| Default OFF / flag-gated / no-op if Oracle off / cron / HS-only | PASS (static) | service gated on `haengsung.oracle.enabled`; job gated on `haengsung.exchange-rate-sync.enabled` (default false) + `ObjectProvider.getIfAvailable()` no-op; `@Scheduled` ICT. |
| No XA; no credential logging | PASS (static) | 3 independent tx (Oracle JdbcTemplate / JPA flag update / `REQUIRES_NEW` audit); logs only `bizKey`/`bankCd`/`ccyCd`. |

### 4.7 Account-History / Card Sync (Step 7) — **PASS (static)**

Verified against [haengsung-card-account-sync.md](../../features/integrations/haengsung/card-account-sync.md).

| Check | Verdict | Evidence |
|---|---|---|
| Account: reads `ACCT_TRNS_HSTR` (`hand_reg_yn='N' AND coalesce(erp_snd_yn,'N')='N'`) | PASS (static) | `AcctTrnsHstrRepository` query (joins `ACCT_MSTR`; `BANK_CD` via `CITAD_BANK_CD_LOOKUP`). |
| Account: INSERTs `TB_ACCT_HISTORY` | PASS (static) | idempotent `INSERT ... SELECT ... WHERE NOT EXISTS` on PK `(BIZ_NO,BANK_CD,ACCT_NO,TR_DT,TR_SEQNO)`; `ERP_GET_ST='0'`, `BIZ_NO=taxId`, `CMS_PUT_DTTM=nowVn`. |
| Account: `ERP_SND_YN='Y'` only AFTER Oracle success | PASS (static) | `pushAcct`: Oracle insert in try; `catch → return false`; `markErpSent` per-row unreachable on failure (test confirms failed row not flagged). |
| Account: VND/FCY count; 0-row audit | PASS (static) | `ccyCd=="VND"?vnd:fcy` (on success); audit rows `01`(VND)/`08`(FCY) written unconditionally after loop. |
| Card: reads `CARD_MSTR`+`CARD_TRNS_HSTR` (`coalesce(erp_snd_yn,'N')='N'`) | PASS (static) | `CardTrnsHstrRepository` join query; `AUTH_NO` via legacy CASE on `bank_cd='11704020'`. |
| Card: INSERTs `TB_CARD_APPROVAL`, `CARD_NM=CARD_HLDR_NM`, `PURCHASE_ST=PRCH_STTS` | PASS (static) | `pushCard` passes `cardHldrNm` as `CARD_NM`, `prchStts` as `PURCHASE_ST` (test asserts both overrides). |
| Card: `ERP_SND_YN='Y'` only AFTER Oracle success | PASS (static) | same try/catch/early-return pattern; per-row flag on PK `(BIZ_KEY,CARD_SEQ_NO)`. |
| Audit types VND=01 / FCY=08 / Card=07 (`ERP_TYPE='W'`) | PASS (static) | constants `01`/`08`/`07`; `recordBatchPush` writes `ERP_TYPE='W'` (test asserts all three). |
| Default OFF / flag-gated / no-op if Oracle off / cron / HS-only / re-run idempotent | PASS (static) | job gated on `haengsung.card-account-sync.enabled` (default false) + Oracle-off no-op; `@SchedulerLock`; `findByPrdtTypeAndDelYn("H","N")`; re-run blocked by both `erp_snd_yn` filter AND `WHERE NOT EXISTS`. |
| No XA; no credential logging | PASS (static) | separate datasources, per-row commit; logs only `bizKey`/seq. |

---

## 5. Unit-test evidence (executed locally)

The unit tests that encode the exact Oracle column literals and gating were **executed clean** on this workspace (forced `cleanTest`, JDK 21):

```
./gradlew cleanTest test --tests "com.kosign.wecms.domain.payment.haengsung.*" \
                          --tests "com.kosign.wecms.config.haengsung.*"
BUILD SUCCESSFUL
```

| Test class | tests | failures | errors |
|---|---|---|---|
| `config.haengsung.HaengsungOracleDataSourceConfigTest` | 3 | 0 | 0 |
| `config.haengsung.HaengsungOraclePropertiesTest` | 7 | 0 | 0 |
| `…haengsung.approval.HaengsungApprovalStatusWriteBackServiceTest` | 10 | 0 | 0 |
| `…haengsung.approval.HaengsungApprovalStatusWriteBackWiringTest` | 3 | 0 | 0 |
| `…haengsung.batch.HaengsungScheduledPaymentImportJobTest` | 5 | 0 | 0 |
| `…haengsung.batch.HaengsungScheduledPaymentImportJobWiringTest` | 3 | 0 | 0 |
| `…haengsung.cardaccount.HaengsungCardAccountSyncServiceTest` | 8 | 0 | 0 |
| `…haengsung.controller.HaengsungPaymentRefreshControllerTest` | 2 | 0 | 0 |
| `…haengsung.dispatch.HaengsungDispatchResultWriteBackServiceTest` | 14 | 0 | 0 |
| `…haengsung.dto.HaengsungRefreshRequestValidationTest` | 6 | 0 | 0 |
| `…haengsung.exchangerate.HaengsungExchangeRateSyncServiceTest` | 9 | 0 | 0 |
| `…haengsung.oracle.HaengsungErpOracleRepositoryTest` | 11 | 0 | 0 |
| `…haengsung.service.ErpPaymentRefreshMasterImporterTest` | 6 | 0 | 0 |
| `…haengsung.service.ErpPaymentRefreshServiceTest` | 6 | 0 | 0 |
| **Total** | **93** | **0** | **0** |

These tests assert the exact literals (`END_GB='2'/'8'/'5'/'3'`, `LAST_STATUS='10'/'20'/'51'/'52'/'32'`, `PROC_STS='XXXX'/'0999'`, `ERP_RCV_FLAG='Y'`, `ERP_GET_ST='0'`, audit type codes `01/07/08`), the Oracle-success-before-flag ordering, the approval-lock skip, per-master rollback, and the default-OFF gating.

---

## 6. Pass/Fail summary table

| Flow | Static (code/test/hygiene) | Live SIT runtime |
|---|---|---|
| 1. Oracle Foundation | ✅ PASS | ⏳ PENDING-SIT (boot+probe) |
| 2. Manual Payment Refresh (Vendor/Expense/Payroll/Overseas/ALL) | ✅ PASS | ⏳ PENDING-SIT |
| 3. Scheduled Import Batch | ✅ PASS | ⏳ PENDING-SIT |
| 4. Approval-Status Write-back | ✅ PASS (3 LOW notes) | ⏳ PENDING-SIT |
| 5. Dispatch-Result Write-back | ✅ PASS (1 MEDIUM by-design + 1 LOW) | ⏳ PENDING-SIT |
| 6. Exchange-Rate Sync | ✅ PASS | ⏳ PENDING-SIT |
| 7. Account-History / Card Sync | ✅ PASS | ⏳ PENDING-SIT |
| Credential / repo hygiene | ✅ PASS | n/a |

**Static verdict: all seven flows PASS** with no HIGH/correctness bug. **Live SIT runtime: not yet executed from this workspace** (Section 0 boundary) — Section 9 is the runnable checklist for the SIT operator.

---

## 7. Bugs found

**No HIGH or correctness bug was found.** No Oracle column literal, status code, ordering rule, or gate was wrong. The items below are observations, not blockers — and per the Step-8 instruction, **none were fixed** (no code touched).

| ID | Severity | Flow | Observation | Recommendation |
|---|---|---|---|---|
| OBS-1 | LOW | 4 & 5 & 7 | Error catch blocks log the **full exception/stack**, not a curated `SQLState`/`errorCode`-only line as roadmap §112 phrases it. **No credential leak** (Oracle `SQLException` text carries `ORA-xxxxx` + message, not DSN/user/password — the *foundation* probe already logs SQLState/code-only). | Optional hardening: extract `SQLException.getSQLState()/getErrorCode()` in the write-back/sync catch blocks for log uniformity. Not required for SIT. |
| OBS-2 | MEDIUM (by-design) | 5 | One operation stamps Oracle `IHB_UDT_DT`/`END_DATETIME` in **VN** time but the WeCMS `ERP_PROC_CMPL_YN` audit in **UTC** (7h apart). | Confirm intent with PO/DBA. Consistent with the established WeCMS-UTC `_DTM` convention (memory `project_payment_datetime_tz_locale`) + Oracle/legacy VN convention — likely correct as-is. |
| OBS-3 | LOW (convention) | 4 | Product-type gate uses literal `"H".equals(...)` rather than the mandated `PrdtType.fromCode(...)` (CLAUDE.md product-type rule). Functionally correct (adds active-tenant `del_yn` guard); matches the project-wide HS fan-out pattern (`findByPrdtTypeAndDelYn("H","N")`). | Optional convention cleanup; not behavioural. |
| OBS-4 | INFO | 2/3 | **M-2** (Expense `FILE_NM LIKE 'E%'`) and **M-12** (`ANT_DIV_CD='T'`) are carried verbatim from legacy per spec directive — they remain **open PO/ERP blockers** (roadmap §5), not code defects. The import may select 0 Expense rows on SIT if HAENGSUNG marks Expense differently. | Resolve with PO/ERP before relying on Expense/`T`-routing in UAT. |
| OBS-5 | INFO | docs | The roadmap + refresh spec headers still say "docs only — nothing implemented." Stale: Steps 1–7 are implemented + tested. | Update the two spec headers (doc-only edit, out of this verification's scope). |

---

## 8. Fixes required

**None required to proceed to the SIT runtime run.** No fix was applied (verification-only mandate; no HIGH bug). Optional, non-blocking follow-ups: OBS-1 (log hygiene), OBS-3 (convention), and the doc-header refresh OBS-5. **OBS-2 and OBS-4 need a PO/DBA confirmation, not a code change.**

---

## 9. Runnable SIT checklist (Surface B — for the SIT operator)

Run these on the SIT box after setting `HAENGSUNG_ORACLE_*` in `setenv.sh` and restarting Tomcat. **Replace `<HS_BIZ_NO>` with the HAENGSUNG `BUSI_INFM.TAX_ID`** (the value bound as Oracle `BIZ_NO`), `<YYYYMMDD>` with the test period. Do not paste real credentials anywhere.

### 9.1 Foundation — connectivity & boot

1. With `HAENGSUNG_ORACLE_ENABLED=true`, restart and confirm `catalina.out` shows: `HAENGSUNG Oracle connectivity OK (SELECT 1 FROM dual).` (warn line if unreachable — app must still be up).
2. Oracle, as the configured ERP user:
   ```sql
   SELECT 1 FROM dual;
   ```
3. Idle-soak ≥ 5 min, then trigger any Oracle flow → confirm **no `ORA-17008`** in `catalina.out`.
4. Confirm the WeCMS Postgres flows (login, banking list) still work (Postgres stays primary).

### 9.2 Manual Refresh (run for Vendor, Expense, Payroll, Overseas, then ALL)

Seed/identify Oracle source rows, then call `POST /api/v1/payments/haengsung/refresh` (as an `H` admin) per type.

```sql
-- Oracle: source masters available to import (per type predicate)
SELECT FILE_DATE, FILE_NUM, FILE_GB, FILE_NM, LAST_STATUS, ERP_RCV_FLAG
FROM   TB_TRNS_MAST
WHERE  BIZ_NO = '<HS_BIZ_NO>' AND LAST_STATUS = '00'
       AND FILE_DATE BETWEEN '<YYYYMMDD>' AND '<YYYYMMDD>';

-- Oracle: AFTER refresh — receive write-back must be set
SELECT FILE_DATE, FILE_NUM, ERP_RCV_FLAG, ERP_RCV_FLAG_USR, ERP_RCV_FLAG_DT, IHB_UDT_DT
FROM   TB_TRNS_MAST
WHERE  BIZ_NO = '<HS_BIZ_NO>' AND FILE_DATE = '<YYYYMMDD>';
-- expect ERP_RCV_FLAG='Y', ERP_RCV_FLAG_USR='WEBCASH'

SELECT FILE_SEQ, END_GB, FLAG, IF_SND_ID, FLAG_USR, IHB_UDT_DT
FROM   TB_TRNS_DETAIL
WHERE  FILE_DATE = '<YYYYMMDD>' AND FILE_NUM = '<FILE_NUM>';
-- expect END_GB='2', FLAG='Y', IF_SND_ID='WEBCASH'
```

```sql
-- Postgres (WeCMS): drafts imported (scheduled types)
SELECT scdul_pymn_no, erp_file_date, erp_file_num, erp_file_seq, appr_seq_no
FROM   SCDUL_PYMN_SUMR
WHERE  biz_key = 'VN_VNM0004_000014' AND erp_file_date = '<YYYYMMDD>';
-- Overseas:
SELECT ovrs_pymn_no, erp_file_date, erp_file_num FROM OVRS_PYMN_SUMR
WHERE  biz_key = 'VN_VNM0004_000014' AND erp_file_date = '<YYYYMMDD>';

-- Dedup: run the same Refresh again → row counts unchanged (UPDATE, no new rows)
-- Approval-lock: set APPR_SEQ_NO on one imported summary, re-Refresh → that master skipped (no write-back)
-- Audit:
SELECT seq_no, erp_type, trns_type_cd, org_file_nm, reg_dtm
FROM   ERP_TRNS_HSTR WHERE biz_key='VN_VNM0004_000014' ORDER BY reg_dtm DESC;
```

### 9.3 Approval-Status Write-back (drive each status on an imported HS batch)

```sql
-- Oracle: after each WeCMS approval transition
SELECT FILE_DATE, FILE_NUM, LAST_STATUS, END_GB, END_DATETIME, BUL_CNT, BUL_AMT
FROM   TB_TRNS_MAST WHERE BIZ_NO='<HS_BIZ_NO>' AND FILE_DATE='<YYYYMMDD>';
-- Pending→'10'  Approved→'20'  Processing→'51'  Rejected→'32' (+END_GB='1', END_DATETIME, BUL_CNT/AMT)

-- Oracle: reject detail
SELECT FILE_SEQ, PROC_STS, END_GB, TR_DATE FROM TB_TRNS_DETAIL
WHERE  FILE_DATE='<YYYYMMDD>' AND FILE_NUM='<FILE_NUM>';
-- expect PROC_STS='XXXX', END_GB='3'
```

### 9.4 Dispatch-Result Write-back (needs Step-4 `'51'` + a live dispatch result)

```sql
-- Oracle master close
SELECT LAST_STATUS, END_GB, NOR_CNT, NOR_AMT, ERR_CNT, ERR_AMT, END_DATETIME
FROM   TB_TRNS_MAST WHERE BIZ_NO='<HS_BIZ_NO>' AND FILE_DATE='<YYYYMMDD>';
-- expect LAST_STATUS='52', END_GB='1'

-- Oracle per-line result
SELECT FILE_SEQ, END_GB, PROC_STS, PROC_STS_MSG, TR_DATE FROM TB_TRNS_DETAIL
WHERE  FILE_DATE='<YYYYMMDD>' AND FILE_NUM='<FILE_NUM>';
-- success: END_GB='8', PROC_STS='0000' ; fail: END_GB='5', PROC_STS in ('0999'|RS_CD)

-- Postgres: summary marked complete (no re-process)
SELECT scdul_pymn_no, erp_proc_cmpl_yn FROM SCDUL_PYMN_SUMR
WHERE  biz_key='VN_VNM0004_000014' AND erp_file_date='<YYYYMMDD>';   -- expect 'Y'
```

### 9.5 Exchange-Rate Sync (enable `HAENGSUNG_EXCHANGE_RATE_SYNC_ENABLED=true` or trigger the job)

```sql
-- Postgres source (today)
SELECT bank_cd, ccy_cd, ref_ccy_cd, seq_no, erp_snd_yn FROM BANK_EXCH_RATE_INFM
WHERE  biz_key='VN_VNM0004_000014' AND ref_date='<YYYYMMDD>';

-- Oracle target
SELECT BANK_CD, CCY_CD, REF_DT, REF_SEQ, BASIC_RATE, ERP_GET_ST FROM TB_FCY_EXRATE
WHERE  BIZ_NO='<HS_BIZ_NO>' AND REF_DT='<YYYYMMDD>';   -- expect rows; ERP_GET_ST='0'

-- Postgres: flag flipped only after Oracle success
SELECT bank_cd, ccy_cd, erp_snd_yn FROM BANK_EXCH_RATE_INFM
WHERE  biz_key='VN_VNM0004_000014' AND ref_date='<YYYYMMDD>';   -- expect 'Y' for pushed rows
```

### 9.6 Account-History & Card Sync (enable `HAENGSUNG_CARD_ACCOUNT_SYNC_ENABLED=true` or trigger the job)

```sql
-- Postgres source (unsent)
SELECT acct_seq_no, ccy_cd, hand_reg_yn, erp_snd_yn FROM ACCT_TRNS_HSTR
WHERE  biz_key='VN_VNM0004_000014' AND hand_reg_yn='N' AND COALESCE(erp_snd_yn,'N')='N';
SELECT card_seq_no, erp_snd_yn FROM CARD_TRNS_HSTR
WHERE  biz_key='VN_VNM0004_000014' AND COALESCE(erp_snd_yn,'N')='N';

-- Oracle targets
SELECT BANK_CD, ACCT_NO, TR_DT, TR_SEQNO FROM TB_ACCT_HISTORY WHERE BIZ_NO='<HS_BIZ_NO>';
SELECT CARD_NO, APPR_DT, APPR_TM, CARD_NM, PURCHASE_ST FROM TB_CARD_APPROVAL WHERE BIZ_NO='<HS_BIZ_NO>';
-- CARD_NM = card holder name; PURCHASE_ST = PRCH_STTS

-- Postgres: flags flipped only after Oracle success; re-run sends nothing new
-- Audit rows: ERP_TRNS_HSTR types 01 (Acct VND), 08 (Acct FCY), 07 (Card), all ERP_TYPE='W'
```

### 9.7 Log checks (`catalina.out` / app logs)

- [ ] No password printed; no full JDBC URL printed.
- [ ] No repeated `ORA-17008`.
- [ ] No duplicate-key spam (idempotent `WHERE NOT EXISTS` / dedup working).
- [ ] No unexpected rollback (per-master/per-row isolation holds; one bad row doesn't abort the batch).
- [ ] No payment-data corruption (WeCMS draft rows match Excel-upload shape).
- [ ] OBS-1: write-back/sync failures currently log a full stack — confirm it contains **no DSN/credentials** (expected: only `ORA-xxxxx` + message).

---

## 10. Final UAT-readiness decision

**Decision: CONDITIONALLY READY for the SIT runtime run; NOT YET signed off for UAT.**

- **Code, tests, and credential hygiene: PASS.** All seven flows are implemented to spec at the literal level, 93/93 HAENGSUNG unit tests pass, no secret is committed, the integration is OFF by default and fail-fast when misconfigured, and the ORA-17008 guard is in place. There is **no HIGH/correctness bug** and nothing to fix to begin SIT.
- **Gate to UAT (must clear before sign-off):**
  1. **Execute Surface B** (Section 9) on the live SIT box against the real Oracle `DEV` — currently un-run from this workspace. UAT sign-off requires observed PASS on the Oracle column writes + log checks.
  2. **OQ-12** — confirm the Oracle JDBC **SID vs service** form with the DBA before enabling.
  3. **OBS-4 / M-2 / M-12** — PO/ERP confirmation of the Expense (`E%`) split and `ANT_DIV_CD='T'` routing before relying on those paths.
  4. **OBS-2** — PO/DBA confirmation of the VN-vs-UTC audit-timestamp split in Step 5 (likely correct by convention).
- **Non-blocking:** OBS-1 (log SQLState/code-only hardening), OBS-3 (use `PrdtType.fromCode`), OBS-5 (refresh stale spec headers).

**Sign-off recorded when Section 9 returns PASS on SIT and items 2–4 above are cleared by the DBA/PO.**

---

## 11. Change log

| Date | Author | Note |
|---|---|---|
| 2026-06-28 | Claude Code (AI) | Initial Step-8 verification: static PASS on all 7 flows + foundation + hygiene (93/93 tests green); Surface B (live SIT) prepared as Section 9, not executed (no Oracle creds reachable from workspace). 5 observations (0 HIGH), conditional UAT-readiness. No code changed. |
