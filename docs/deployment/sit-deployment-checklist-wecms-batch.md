# WeCMS Batch — SIT Deployment Checklist

**Date:** 2026-07-21 · **Branch:** `wecms-ui-fixes` · **Artifact:** single `ROOT.war` (config bundled in
`WEB-INF/classes`, per `project_sit_deploy_war_config_hotfix`) · **Target:** SIT (`wecms-api.kosign.dev`,
shared dev DB `172.168.5.172:5432/wecmsdevdb`).

> **Deployment reality:** SIT deploys **one rebuilt WAR**, not individual `.class` files — the lists
> below are the changed *sources* the WAR contains (for review/traceability). Runtime config on SIT is
> read from the server `bin/setenv.sh` OS env, **not** `.env.sit` (`reference_sit_server_config_setenv`).
> File lists are grounded in `git status` vs branch HEAD (not memory).

---

## 1. Backend application (Java classes)

**Changed (M):**

| File (`src/main/java/com/kosign/wecms/...`) | Why | Mandatory for SIT? |
|---|---|---|
| `domain/payment/dispatch/service/ResultCheckService.java` | B1 (0001 definitive) + B3 (203 audit row) + B4 (ORG_STAT gate) + B5 (pacing) + H2 (failure-only alerts) + Item 1 (delegates atomic finalize) | **Yes** (ships in WAR; behaviour gated by result-check cron) |
| `domain/payment/dispatch/service/RealtimeDispatchService.java` | G2 atomic `9→6` claim + H2 (202 alert fail-only, drop 204-success) + D1 (202 FX cols) + 204 pre-step | **Yes** (gated by `realtime-dispatch`) |
| `domain/payment/dispatch/service/RealtimeTransferService.java` | H-4 (G999→pending) + H-5 (response VV) + A1 (TNO) + A3 | **Yes** |
| `domain/payment/dispatch/service/BulkReceptionService.java` | **Item 2 (per-split-file `REQUIRES_NEW`)** + C5 split + H1 tone + C2 | **Yes** (gated by `auto-receive`) |
| `domain/payment/dispatch/callback/service/BulkReceptionResultService.java` | C1 sibling fail-stop / G998 + A2 ack envelope | **Yes** (callback path) |
| `domain/payment/dispatch/callback/service/BulkExecutionResultService.java` | **G1 (01302 audit via REQUIRES_NEW)** + A2 ack + C3 substring match | **Yes** (callback path) |
| `domain/payment/dispatch/callback/controller/BulkReceptionResultController.java` | A2 — return EFS ack envelope | **Yes** |
| `domain/payment/dispatch/callback/controller/BulkExecutionResultController.java` | A2 — return EFS ack envelope | **Yes** |
| `domain/payment/dispatch/callback/service/BulkExecutionApiCommand.java` | A1 — TNO = TRAN_SEQ | Yes |
| `domain/payment/dispatch/service/BulkReceptionApiCommand.java` | A1 — TNO = TRAN_SEQ | Yes |
| `domain/payment/dispatch/service/RealtimeTransferResult.java` | H-4 — `recovered`→`pending` reshape | Yes |
| `domain/payment/dispatch/service/ResultCheckInquiryResult.java` | B1 — `notFound` factory | Yes |
| `domain/payment/dispatch/alert/OpsAlertService.java` | H2 — ID=vanOrgCd (M-12) + `[ENV][WAS_NO]` label + T/U/R mode | **Yes** (gated by `alert.enabled`) |
| `domain/payment/overseas/service/OverseasDispatchExecutor.java` | **Item 3** — 150→35 `RCPT_USE_ADDR_SI` cap + E1 town-name | **Yes** (overseas dispatch) |
| `domain/payment/service/OverseasPaymentExcelService.java` | Town-name origination (contact→preview→detail) | **Yes** (overseas save) |
| `domain/payment/service/OverseasPaymentService.java` · `dto/OverseasPaymentDetailRowResponse.java` · `dto/OverseasPaymentExcelDetailRow.java` · `dto/OverseasPaymentExcelPreviewRow.java` | Town-name DTO field carry | Yes |
| `domain/payment/vendorpayment/service/VendorDispatchExecutor.java` · `expensepayment/service/ExpenseDispatchExecutor.java` · `internaltransfer/service/InternalTransferDispatchExecutor.java` | F1 — VCB (970436) file-route switch + USD-only reject | **Yes** (VCB routing) |
| `domain/setting/contacts/service/ContactsService.java` · `controller/ContactsController.java` · `dto/ContactOverseasRow.java` · `dto/OverseasOp.java` | Contacts Overseas SWIFT/BIC dropdown + town auto-fill (`project_contacts_overseas_swift_bic_town`) | Yes (bundled sibling feature) |

**New (added):**

| File | Why | Mandatory? |
|---|---|---|
| `domain/payment/dispatch/service/DispatchResultTxService.java` | **Item 1** — atomic U004+U001 finalize (`REQUIRES_NEW`) | **Yes** |
| `domain/payment/dispatch/service/DispatchPacer.java` | **B5** — per-bank scan pacing (Woori 3000ms, Shinhan 100ms, **else 3000ms** — default raised from legacy's 100 on 2026-08-01, D-A) | **Yes** |
| `domain/payment/dispatch/service/StrandedRecoveryService.java` | **Item 4** — recovery (disabled) | **Yes** (inert until enabled) |
| `domain/payment/dispatch/scheduler/StrandedRecoveryJob.java` | **Item 4** — recovery scheduler (cron `-`) | **Yes** (inert) |
| `domain/payment/dispatch/callback/service/DispatchApiTranAuditWriter.java` | **G1** — REQUIRES_NEW callback audit | **Yes** |
| `domain/payment/dispatch/van/AmountSplitter.java` | H-6 — legacy `BigDecPaser` decimal form | **Yes** |
| `domain/setting/contacts/dto/SwiftBicListResponse.java` · `dto/SwiftBicRow.java` · `entity/SwiftBicMst.java` · `repository/SwiftBicMstRepository.java` | Contacts SWIFT/BIC master (bundled sibling feature) | Yes (needs `swift_bic_mst` table — see §6) |

> All main-source changes are compiled into the one WAR — every row above ships together; "mandatory"
> = functionally required for the batch subsystem to behave correctly once its flag is enabled.
> `LegacyFunctionRepository` is **unchanged** (the `nextTbFirmTranSeq` branch already existed).

---

## 2. Repository layer

| Repository | Change | New JPQL/SQL |
|---|---|---|
| `TbPayTargetRepository` | G2 + Item 4 | `@Modifying @Query` **`claimForDispatch`** = `UPDATE TbPayTarget SET procStat='6', procDttm=:d WHERE bizKey/tranDt/tranSeq AND procStat='9'`; derived finder `findByProcStatAndProcDttmLessThan` |
| `TbPayFileMstRepository` | Item 4 | derived finder `findByProcStatAndProcDttmLessThan` |
| `TbFirmFileMstRepository` | prior C3 | JPQL `findForExecutionResult` (substring(FIRM_FILE_NM,3) match) — already on branch |
| `SwiftBicMstRepository` (new) | Contacts SWIFT/BIC | derived finders over `swift_bic_mst` |

No native SQL added for the batch items. `claimForDispatch` is JPQL (no schema change).

---

## 3. Entity classes

| Entity | Change | Schema change needed? |
|---|---|---|
| `TbFirmTran` | +5 mappings: `ORG_TRAN_DT`, `ORG_TRAN_SEQ`, `ORG_PROC_TM`, `WDRL_ACCT_RMRK`, `TAR_ACCT_RMRK` (B3) | **No — columns already exist** (verified on wecmsdevdb) |
| `TbPayFileData` | +`RCPT_USE_ADDR_SI` (E1) | **No — exists (varchar 35)** |
| `TbFirmFileData` | +`RCPT_USE_ADDR_SI` (E1) | **No — exists (35)** |
| `TbFirmFileRslt` | +`RCPT_USE_ADDR_SI` (E1) | **No — exists (35)** |
| `OvrsPymnSumrDetl` | +`BNFC_TOWN_NM` | **No — exists (150)** |
| `TrcoOvrsInfm` | +`BENE_TOWN_NM` + SWIFT fields | **No — exists (150)** |
| `SwiftBicMst` (new) | maps `swift_bic_mst` | **No — table exists + seeded (99,060 rows)** on wecmsdevdb |

`ddl-auto=validate` passes against wecmsdevdb → every mapped column/table is present.

---

## 4. Configuration

**Changed config files (in the WAR):** `application-sit.yml`, `application-uat.yml`, `application-prod.yml`
— all gained the `wecms.dispatch.recovery` block. (`application.yml` unchanged; `application-local.yml`
is git-ignored, irrelevant to SIT.) No `@ConfigurationProperties` class changed; `OpsAlertService`
gained two `@Value` props (`wecms.dispatch.alert.env-mode`, `.was-no`) read directly.

**Environment variables (set in SIT `setenv.sh`):**

| Variable | Code default | Recommended **SIT** | Recommended **UAT** |
|---|---|---|---|
| `WECMS_EFS_ENABLED` | `false` (mock) | `false` (until OQ-1/B5 confirmed) | `false` |
| `WECMS_SFTP_ENABLED` | `false` (mock) | `false` | `false` |
| `WECMS_DISPATCH_REALTIME_DISPATCH` | `false` | `false` | `false` |
| `WECMS_DISPATCH_AUTO_RECEIVE` | `false` | `false` | `false` |
| `WECMS_DISPATCH_RECIPIENT_INQUIRY` | `false` | `false` | `false` |
| `WECMS_DISPATCH_RESULT_CHECK_CRON` | `-` (never) | `-` | `-` |
| `WECMS_DISPATCH_ALERT_ENABLED` | `false` | `false` | `false` |
| `WECMS_CALLBACK_ALLOWED_IPS` | empty | VAN gateway IP(s) once B7 known | VAN gateway IP(s) |
| `WECMS_DISPATCH_RECOVERY_ENABLED` **(new)** | `false` | `false` (coexistence-gated) | `false` |
| `WECMS_DISPATCH_RECOVERY_CRON` **(new)** | `-` (never) | `-` | `-` |
| `WECMS_DISPATCH_RECOVERY_GRACE_MINUTES` **(new)** | `30` | `30` | `30` |
| `wecms.dispatch.alert.env-mode` **(new, optional)** | profile fallback | `U`/`T` (only if alert on) | `U` |
| `wecms.dispatch.alert.was-no` **(new, optional)** | empty | SIT WAS no. (only if alert on) | UAT WAS no. |

Recommended posture for SIT: **all dispatch legs OFF** (mock gateway), matching committed `.env.sit`.
Flip individual flags on only for a controlled integration test, after §9.

---

## 5. Schedulers

| Scheduler | Enabled? | Cron | Purpose | Stay disabled in SIT? |
|---|---|---|---|---|
| `ResultCheckJob` | Off by default | `${WECMS_DISPATCH_RESULT_CHECK_CRON:-}` (`-`) | 203 processing-result scan of aged 202/`'6'` rows | **Yes** until dispatch activation is cleared |
| `StrandedRecoveryJob` **(new)** | Off by default | `${WECMS_DISPATCH_RECOVERY_CRON:-}` (`-`) | R-1 recovery of stranded `'9'`/`'6'` rows | **Yes** — double-gated (also `recovery.enabled=false`); keep off until UAT coexistence resolved |

Both use `@SchedulerLock` (ShedLock `shedlock` table present on wecmsdevdb). No other batch scheduler.

---

## 6. Database

**Migration SQL required: NOT REQUIRED.** No Flyway migration was added; `ddl-auto=validate` passes.

| Object | Type | Touched by | Migration SQL? |
|---|---|---|---|
| `TB_FIRM_TRAN` (+`ORG_TRAN_DT/ORG_TRAN_SEQ/ORG_PROC_TM/WDRL_ACCT_RMRK/TAR_ACCT_RMRK`) | table/cols | B3 read+write | No — columns exist |
| `TB_PAY_TARGET` | table | G2 claim UPDATE, Item 4 finder | No |
| `TB_PAY_FILE_MST` | table | Item 4 finder | No |
| `TB_FIRM_FILE_DATA` / `TB_FIRM_FILE_RSLT` (+`RCPT_USE_ADDR_SI`) | table/cols | E1 | No — cols exist (35) |
| `OVRS_PYMN_SUMR_DETL` (+`BNFC_TOWN_NM`) / `TRCO_OVRS_INFM` (+`BENE_TOWN_NM`) | table/cols | Item 3 town-name | No — cols exist (150) |
| `swift_bic_mst` | table + **data** | Contacts SWIFT/BIC dropdown | No — table exists + **seeded 99,060 rows** |
| `shedlock` | table | both schedulers (ShedLock) | No — exists |
| Sequences (`firm_seq_no`, `firm_file_seq_no`) / `wb_get_next_val` | seq/fn | key gen (unchanged funnel) | No — pre-existing |
| Views / Triggers | — | none added | No |

Nothing to run by a DBA for this deployment. (If a *future* env's DB lacks `swift_bic_mst`/`shedlock`
or the town/203 columns, they must be provisioned first — but wecmsdevdb already has them.)

---

## 7. Documentation to commit

| Doc | Status |
|---|---|
| `docs/audits/wecms-batch-refactor-parity-audit-2026-07-21.md` | New — parity audit (§0.1–0.3 status) |
| `docs/deployment/wecms-batch-activation-and-external-confirmations.md` | New — runtime config audit + coexistence + external confirmations |
| `docs/deployment/sit-deployment-checklist-wecms-batch.md` | New — **this checklist** |
| `docs/audits/README.md` | Modified — index entry |
| `docs/references/database/legacy-table-inventory.md` | Modified — `swift_bic_mst` baseline entry |

---

## 8. Deployment order (SIT)

1. **Backup** — snapshot current SIT `ROOT.war` + `setenv.sh`; note current `env | grep WECMS_`.
2. **Build** — `./gradlew clean build` (green: 1862 tests). Verify `application-sit.yml` has **no tab chars** (CI tab-guard; `project_sit_deploy_war_config_hotfix`).
3. **Deploy WAR** — stop Tomcat, replace `ROOT.war`, keep `setenv.sh` unchanged (all dispatch flags stay OFF).
4. **Verify configuration** — on startup, confirm no `application-sit.yml` bind errors; confirm `MockEfsApiClient`/`MockSftpClient` are the active beans (dispatch OFF).
5. **Verify DB connectivity** — app connects to `wecmsdevdb@172.168.5.172`; Hibernate `validate` passes (no missing-column error → confirms §3/§6).
6. **Verify scheduler** — logs show `ResultCheckJob`/`StrandedRecoveryJob` beans registered but **not firing** (cron `-`).
7. **Verify callback endpoint** — `/api/v1/payment/dispatch/callbacks/**` reachable (returns the EFS ack envelope shape); IP allowlist behaviour as configured.
8. **Smoke test** — `/actuator/health` UP; login; one Banking + one Payment read.
9. **Functional test** — §9.

---

## 9. SIT verification checklist (post-deploy)

**Baseline (dispatch OFF — the default posture):**

- [ ] App boots; `validate` passes; mock EFS/SFTP beans active.
- [ ] Payment save (Vendor/Expense/Internal/Overseas/Payroll) writes `TB_PAY_TARGET`/`TB_PAY_FILE_MST` at `PROC_STAT='9'` and does **not** dispatch (no `TB_FIRM_TRAN` rows created).
- [ ] Schedulers do not fire (cron `-`); recovery no-ops.
- [ ] Overseas save: `OVRS_PYMN_SUMR_DETL.BNFC_TOWN_NM` populated from the contact's `BENE_TOWN_NM`.
- [ ] Contacts Overseas: SWIFT/BIC dropdown loads from `swift_bic_mst`; town auto-fills.
- [ ] Logs contain no secrets (API keys / EV / VV / passwords).
- [ ] DB check: no unexpected new firm-tran/file rows from the deploy itself.

**Activation tests (only with the specific flag temporarily ON + real gateway, per an approved integration window):**

- [ ] **Realtime payment (202)** — Shinhan VND: `PROC_STAT 9→6→0`; `TB_FIRM_TRAN` 202 row; TNO == TRAN_SEQ.
- [ ] **Realtime Woori (204+202)** — non-VND: 204 FX row then 202; FX cols on pay-target + 202 row.
- [ ] **Bulk payment (00301)** — file built (1000-byte), SFTP-uploaded, audit before call; branch-split → per-file commit; a forced later-file failure does not roll back earlier files (Item 2).
- [ ] **Callback 01301** — reception result returns the rebuilt EV/VV ack; sibling fail-stop → `G998` on a pre-failed pay-file.
- [ ] **Callback 01302** — execution result parses; `TB_FIRM_FILE_RSLT` per line; **audit row survives a forced parse failure** (G1 REQUIRES_NEW).
- [ ] **Result-check (203)** — set `result-check.cron`; aged 202/`'6'` → 203 audit row created; original + pay-target finalized **atomically** (Item 1); ORG_STAT!='1' skipped (B4); per-bank pacing on skip/fail not on success (B5).
- [ ] **Overseas payment** — end-to-end; `RCPT_USE_ADDR_SI` reaches `TB_PAY_FILE_DATA`→`TB_FIRM_FILE_DATA`→`TB_FIRM_FILE_RSLT`; town >35 chars capped to 35 (Item 3).
- [ ] **VCB (970436)** — VND + USD route to the **file** path (never API); VCB VND→other not rejected; VCB VND carries `BANK_CHNL_DIV` T(same)/O(other) + uploaded NAPAS/CITAD `ANT_DIV_CD`; VCB USD→VCB `T`+null (OQ-2 CONFIRMED). Capture one VCB VND record as acceptance evidence.
- [ ] **Shinhan (970424) / Woori (970457)** — bank-specific routing + Woori 204 pre-step + Woori pacing.
- [ ] **Alert** — set `alert.enabled`; a forced failure fires a Slack alert with ID=vanOrgCd + `[ENV][WAS_NO]`; **no alert on success** (H2).
- [ ] **Recovery** — leave DISABLED in SIT; verify no-op only. (Do not enable until UAT coexistence resolved.)
- [ ] **Logs** — trace ids present; no secret leakage; per-file/per-row failures isolated.
- [ ] **Database verification** — status transitions match legacy; `data_dcm_amt` form captured for OQ-1; 1000-byte file diffed vs legacy for B5.

---

## 10. Final deployment summary

**Files to deploy:** one rebuilt `ROOT.war` containing 30 modified + 12 new main-source files (§1–3),
3 modified profile YAMLs (§4). Test sources (`src/test/**`) and docs do not ship in the WAR.

**Configuration to update:** none required for a safe (dispatch-OFF) deploy — keep SIT `setenv.sh` as-is
(all dispatch flags OFF). Optionally set `WECMS_CALLBACK_ALLOWED_IPS` and the new recovery vars (defaults
already safe). New vars documented in §4.

**Database migration:** **NOT REQUIRED.** All columns/tables/sequences exist on `wecmsdevdb`;
`swift_bic_mst` and `shedlock` are present + seeded; no Flyway migration added.

**Environment variables:** 3 new (`WECMS_DISPATCH_RECOVERY_ENABLED/_CRON/_GRACE_MINUTES`) + 2 optional
alert props — all with safe defaults; **no new *required* variable**.

**Scheduler status:** `ResultCheckJob` + `StrandedRecoveryJob` both registered, both DISABLED (cron `-`;
recovery additionally `enabled=false`). Keep disabled in SIT.

**Manual verification:** §9 baseline (dispatch-OFF) must pass on deploy; activation tests only in an
approved integration window with the relevant flag temporarily on.

**Safe for SIT deployment (the WAR, dispatch OFF): YES.**
- No schema migration; `validate` passes; all changes are inert while flags are OFF; full test suite green (1862/0).

**OQ-1 and OQ-2 are CLOSED (2026-07-21)** — they no longer block SIT activation:
- **OQ-1** `data_dcm_amt`: keep the legacy-parity `AmountSplitter` (`1000`→`000`, `1000.00`→`00`, `1000.05`→`05`, `1000.50`→`50`); tests cover it. SIT captures one request as evidence only.
- **OQ-2** VCB: file-based for **both VND and USD** is CONFIRMED (Mr. Jung) and IMPLEMENTED (Vendor/Expense/Internal); `BANK_CHNL_DIV`/`ANT_DIV_CD` values are proven from legacy `PymnHelper` (VCB VND→VCB `T`; VCB VND→Other `O` + uploaded NAPAS/CITAD; VCB USD→VCB `T`+null). SIT captures one VCB VND record as acceptance evidence only.

**Safe to ACTIVATE dispatch in SIT: NO — blocked only by the remaining external confirmations (not by the build, and not by OQ-1/OQ-2):**
1. **B1/B4/B7** EFS base-url/partner-id, SFTP host/paths/key, callback IP allowlist + reachability (Ops).
2. **B5** 1000-byte byte-parity capture vs legacy.
3. **Deployed flag readout** — confirm SIT `setenv.sh` values (only DB behaviour + committed `.env.sit` are known here).
4. Acceptance evidence captures for OQ-1 (`data_dcm_amt`) and OQ-2 (VCB VND record) during the SIT integration window.

**Recovery scheduler activation: NO (UAT-gated)** — enable only after Ops confirms the UAT refactor DB is
not the JEX UAT batch DB (DEV/SIT are DB-separated and safe; see the activation doc §2.2/§4).

---

## 2026-07-22 — legacy-output-parity alignment (pre-SIT)

The dispatch output was aligned to legacy for all source-derivable findings — see
[../audits/legacy-vs-refactor-output-parity-audit.md](../audits/legacy-parity/legacy-vs-refactor-output-parity-audit.md) §7
(full backend suite 1904 green; dispatch flags remain OFF). SIT items added/affected:

- **K (migration-critical):** API-rail `TB_PAY_TARGET.TRAN_DT`/`REG_TM` now stamped in **UTC** (legacy parity).
  Before enabling `result-check`, make `ResultCheckService` day-selection **UTC-aware** (or RQ_DTTM-based) —
  the 202 firm-tran now carries a UTC `TRAN_DT` while the scan still uses ICT-today (day-boundary edge).
- **M:** UAT Shinhan/Woori inquiry `PRTN_ID` override is now implemented but **UAT-profile-gated only**
  (`UatShinhanWooriOverride`); verify the SIT/PROD profiles do NOT activate it.
- **L:** Overseas M-mode (`OVRS_PYMN_TYPE='M'`) dispatch is now implemented; SIT should exercise an
  M-mode single-beneficiary batch alongside the S-mode summary path.
- Runtime confirmations unchanged: `data_dcm_amt` gateway acceptance, the 01301/01302 RC set,
  VCB-VND file-field acceptance (Van Center) — output-parity audit §5.
