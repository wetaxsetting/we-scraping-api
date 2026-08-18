# HAENGSUNG Oracle Integration Roadmap

**Slug:** `haengsung-oracle-integration-roadmap` · **Domain:** Payment / Banking (ERP integration) · **Status:** Implemented (Steps 1–7) — **Step 8 SIT verification pending** · **Product type:** `H` (HAENGSUNG Electronics) only · **Last updated:** 2026-06-29

> **Implemented (2026-06-29).** Steps 1–7 are built and unit-tested (foundation, manual refresh, scheduled import, approval + dispatch write-back, exchange-rate + account/card sync) and the frontend Refresh button is wired; only **Step 8 (end-to-end SIT verification)** remains. This file is the master plan + order of execution; per-feature detail lives in the linked specs. Re-audit verdict + open items: [haengsung-oracle-integration-final-audit.md](../audits/legacy-parity/haengsung-oracle-integration-final-audit.md).

---

## 1. Purpose

HAENGSUNG (`PRDT_TYPE='H'`) does not exchange data with WeCMS by Excel like other tenants — it integrates with an **external Oracle ERP DB** through **six distinct flows** (verified from legacy source 2026-06-28). The payment **Refresh import** is only the first. **All six must eventually be implemented** for full parity; this roadmap orders them, states what each needs, and defines "done" per step.

Two directions exist:
- **Oracle → WeCMS** (pull/import): payment Refresh import.
- **WeCMS → Oracle** (push/write-back): approval status, dispatch result, exchange rate, account-history/card.

The non-negotiable architectural decision: **one shared `ErpPaymentRefreshService`** backs both the manual Refresh and the scheduled import — the two legacy import copies (`ErpDataRefreshHelper` + `ERP_0100_002`) are **not** ported separately ([refresh spec §9A](../features/integrations/haengsung/payment-refresh.md#9a-implementation-boundary--phase-plan)).

Per-feature specs:
- [haengsung-payment-refresh.md](../features/integrations/haengsung/payment-refresh.md) — import (Steps 2–3) + the verified Oracle schema, status flow, mapping, mismatches (Appendix A/B).
- [haengsung-approval-status-writeback.md](../features/integrations/haengsung/approval-status-writeback.md) — Step 4.
- [haengsung-dispatch-result-writeback.md](../features/integrations/haengsung/dispatch-result-writeback.md) — Step 5.
- [haengsung-exchange-rate-sync.md](../features/integrations/haengsung/exchange-rate-sync.md) — Step 6.
- [haengsung-card-account-sync.md](../features/integrations/haengsung/card-account-sync.md) — Step 7.

---

## 2. Integration Map

| Step | Feature | Direction | Legacy Source | Oracle Tables | WeCMS Tables | Priority |
|---|---|---|---|---|---|---|
| 1 | Oracle Foundation (datasource + shared infra) | — | (new infra) | — (connects to `TB_*`) | — | **P0 — required first** · ✅ **Completed** |
| 2 | Payment Refresh Import (manual) | Oracle → WeCMS | `ErpDataRefreshHelper.java` | R `TB_TRNS_MAST`, `TB_TRNS_DETAIL`; W receive-status | W `SCDUL_PYMN_SUMR(_DETL)`, `OVRS_PYMN_SUMR(_DETL)`, `ERP_TRNS_HSTR` | **P1 — primary** · ✅ **Completed** |
| 3 | Scheduled Import Batch | Oracle → WeCMS | `ERP_0100_002.java` (trigger only) | same as Step 2 | same as Step 2 | P2 — optional, PO-gated |
| 4 | Approval-Status Write-back | WeCMS → Oracle | `ErpStatusUpdateHelper.java` | W `TB_TRNS_MAST.LAST_STATUS`; reject → `TB_TRNS_DETAIL.PROC_STS/END_GB` | R `SCDUL_*`/`OVRS_*` (keys) | P3 — follow-on, PO-gated |
| 5 | Dispatch-Result Write-back | WeCMS → Oracle | `ERP_0100_004.java` | W `TB_TRNS_MAST` (`LAST_STATUS=52`…), `TB_TRNS_DETAIL` (`END_GB=8/5`, `PROC_STS`…) | R `SCDUL_*`/`OVRS_*` + dispatch tbls; W summary `ERP_PROC_CMPL_YN='Y'` | P4 — follow-on, needs dispatch live |
| 6 | Exchange-Rate Sync | WeCMS → Oracle | `ERP_0100_001.java` | W `TB_FCY_EXRATE` | R `BANK_EXCH_RATE_INFM` | P5 — separate ERP feature |
| 7 | Account-History / Card Sync | WeCMS → Oracle | `ERP_0100_003.java` | W `TB_ACCT_HISTORY`, `TB_CARD_APPROVAL` | R `ACCT_TRNS_HSTR`, `CARD_MSTR`/`CARD_TRNS_HSTR` | P5 — separate ERP feature |
| 8 | End-to-end SIT verification | — | — | all of the above | all of the above | **P6 — gate to UAT** |

> Oracle tables (`TB_TRNS_*`, `TB_FCY_EXRATE`, `TB_ACCT_HISTORY`, `TB_CARD_APPROVAL`) live on the **external Oracle server** and are **never** added to the WeCMS Flyway inventory or given a primary-unit `@Entity` ([refresh spec §2](../features/integrations/haengsung/payment-refresh.md#2-two-datasource-architecture)).

---

## 3. Step-by-step Implementation Plan

### Step 1 — Oracle Foundation ✅ Completed

The shared base every later step reuses. Build once.

- **Files expected to change:** `build.gradle` (Oracle JDBC driver); `application-*.yml` (bind `${HAENGSUNG_ORACLE_*}` env vars, flag `haengsung.oracle.enabled` default OFF); a new secondary `DataSource` + `@ConfigurationProperties("haengsung.oracle")` config class; a shared Oracle access component (narrow `JdbcTemplate` or scoped secondary unit — OQ-7); `.env.sit.example` (placeholders only); SIT `setenv.sh` (off-repo, host-side).
- **Config needed:** `HAENGSUNG_ORACLE_URL` / `_USERNAME` / `_PASSWORD` / `_ENABLED` (+ pool/keep-alive). Local dev → `.env.sit` (gitignored). SIT host → `setenv.sh` OS env (authoritative — `.env.sit` is ignored on SIT, per memory `reference_sit_server_config_setenv`). WeCMS Postgres stays `@Primary`.
- **Validation needed:** fail-fast in the config constructor when `enabled=true` and a required var is missing; connection validation query (`SELECT 1 FROM dual`) + keep-alive/DCD to avoid the ORA-17008 stale-connection failure (memory `project_hs_prod_datasource_ora17008`); never log URL/user/password.
- **Test needed:** boot with flag OFF (no Oracle bean, app starts clean); boot with flag ON + valid creds → connectivity smoke (`SELECT 1 FROM dual`); boot with flag ON + missing var → fail-fast; confirm Postgres autoconfig unchanged.
- **Done condition:** secondary Oracle datasource connects from local + SIT, primary Postgres untouched, no credential in repo/logs, flag-gated OFF by default.

### Step 2 — Payment Refresh Import (manual) ✅ Completed

- **Files expected to change (backend):** new `ErpPaymentRefreshService` (ports `ErpDataRefreshHelper` logic — upsert + dedup + approval-lock); new controller `POST /api/v1/payments/haengsung/refresh` (product-type `H` guard, `@menuAuthz`); Oracle read access for `TB_TRNS_MAST`/`TB_TRNS_DETAIL`; reuse `ErpTrnsHistoryWriter`, `LegacyFunctionRepository` (`wb_get_next_val`), existing `SCDUL_*`/`OVRS_*` persistence; **add `erpFile*` columns to `OvrsPymnSumr`/`OvrsPymnSumrDetl` entities** (M-5; + DBA DDL if absent).
- **Frontend work:** repoint the existing HAENGSUNG Refresh button handler from list-only `refreshList` to call the new API, then reload the list ([refresh spec §5](../features/integrations/haengsung/payment-refresh.md#5-recommended-frontend-fix)). Toggle (Refresh vs Excel Upload for `H`) already exists.
- **Table mapping:** read `TB_TRNS_MAST` (`last_status='00'`, biz_no, period, payment-type filter) + `TB_TRNS_DETAIL` (`end_gb='0'`) → upsert `SCDUL_*` (Vendor/Expense/Payroll) or `OVRS_*` (Overseas, `FILE_GB='AF1'`). Full field map: [refresh spec §16](../features/integrations/haengsung/payment-refresh.md#16-oracle--wecms-mapping-confirmed). Receive write-back: MAST `ERP_RCV_FLAG='Y'`(+`_USR/_DT`,`IHB_UDT_*`), DETAIL `END_GB='2'`,`FLAG='Y'`,`IF_SND_*`,`FLAG_*`,`IHB_UDT_*` ([§7](../features/integrations/haengsung/payment-refresh.md#7-oracle-status-write-back)).
- **Test cases:** import each type (Vendor/Expense/Payroll/Overseas); dedup (re-Refresh same period → updates, no dupes); approval-lock (batch with `APPR_SEQ_NO` skipped); receive write-back values; per-master rollback on one bad master (others succeed); `ERP_TRNS_HSTR` audit row written (incl. 0-row case, M-7 decision); product-type guard (non-`H` rejected); idempotent re-run (DETAIL `end_gb<>'0'` skipped).
- **Done condition:** all four types import as drafts identical to Excel-upload output; Oracle receive-status written; re-runs safe; audit logged; frontend button drives it end-to-end on SIT.

### Step 3 — Scheduled Import Batch

- **Reuse the same `ErpPaymentRefreshService`** — the scheduled job is only a new trigger (Spring `@Scheduled` / ShedLock), calling the identical import service. **Do not duplicate legacy logic** (`ERP_0100_002`'s inline insert-only copy is NOT ported — M-8/M-9).
- **Files expected to change:** a thin scheduled-job class + schedule config; no change to the import service itself.
- **Test cases:** scheduled run imports all un-received batches for all `H` tenants; produces the **same** result as a manual Refresh over the same data; no double-insert when manual + scheduled overlap (receive-flag + dedup); ShedLock prevents concurrent runs.
- **Done condition:** one import implementation, two triggers; cadence matches PO decision (OQ-13/OQ-14); verified no divergence from manual path.

### Step 4 — Approval-Status Write-back

- **Files expected to change:** new approval-status write-back service (ports `ErpStatusUpdateHelper`); a hook in the refactor HAENGSUNG approval action; reuse Step 1 Oracle write path.
- **Trigger point:** WeCMS approval state change on a HAENGSUNG payment batch (pending / approve / reject / processing) — synchronous, not a batch.
- **Mapping:** `PENDING→10`, `APPROVED→20`, `REJECTED→32`, `PROCESSING→51` → `TB_TRNS_MAST.LAST_STATUS`. On `REJECTED`: also MAST `END_GB='1'`, `END_DATETIME`, `BUL_CNT/AMT`, `IHB_UDT_*`, and DETAIL `PROC_STS='XXXX'`, `END_GB='3'`, `IHB_UDT_DT`, `TR_DATE`. Detail: [haengsung-approval-status-writeback.md](../features/integrations/haengsung/approval-status-writeback.md).
- **Test cases:** each status maps to the right `LAST_STATUS`; reject writes detail `PROC_STS='XXXX'`/`END_GB='3'` + master close fields; non-ERP batch (blank `ERP_FILE_*`) is skipped; unknown status = no-op.
- **Done condition:** Oracle reflects the WeCMS approval lifecycle for HAENGSUNG batches; reject path verified on SIT.

### Step 5 — Dispatch-Result Write-back

- **Files expected to change:** new dispatch-result write-back service/job (ports `ERP_0100_004`); reuse Step 1 Oracle write path; read dispatch-result tables.
- **Trigger point:** batch run **after** the firm-banking dispatch engine produces a result; only finalizes a batch when **all** details have `PROC_STAT`+`RS_CD`, and only updates Oracle masters at `LAST_STATUS='51'` (so depends on Step 4 + a live dispatch engine).
- **Mapping (per line):** success (`PROC_STAT='0'` & `RS_CD='0000'`) → DETAIL `END_GB='8'`, `PROC_STS=RS_CD`; fail (`PROC_STAT='3'`) → `END_GB='5'`, `PROC_STS='0999'`; fail (`PROC_STAT='0'` & `RS_CD≠'0000'`) → `END_GB='5'`, `PROC_STS=RS_CD`. All set `PROC_STS_MSG=RS_MSG`, `TR_DATE=TRAN_DT`, `IHB_UDT_DT`. Master close: `LAST_STATUS='52'`, `END_GB='1'`, `NOR/ERR_CNT/AMT`, `END_DATETIME`. WeCMS summary `ERP_PROC_CMPL_YN='Y'`. Detail: [haengsung-dispatch-result-writeback.md](../features/integrations/haengsung/dispatch-result-writeback.md).
- **Test cases:** all-success batch → MAST `52`/details `8`; mixed/failed → details `5`+`PROC_STS`; incomplete details → batch skipped, retried next run; `ERP_PROC_CMPL_YN` flips so no re-process; only `last_status='51'` masters updated.
- **Done condition:** Oracle receives final per-line + batch result; reconciles success/fail; verified end-to-end with a real dispatch result on SIT.

### Step 6 — Exchange-Rate Sync

- **Files expected to change:** new exchange-rate push service/job (ports `ERP_0100_001`); reuse Step 1 Oracle write path.
- **Mapping:** read WeCMS `BANK_EXCH_RATE_INFM` (today, per `H` tenant) → upsert Oracle `TB_FCY_EXRATE` (`REF_DT`/`REF_SEQ`/`REF_TM`/`BASIC_RATE`/`SELL_AMT`/`BUY_AMT`/`TRAN_SELL_AMT`/`TRAN_BUY_AMT`/`LAST_INQ_DTTM`/`CMS_PUT_DTTM`/`ERP_GET_ST='0'`); set WeCMS `erp_snd_yn='Y'`; bank-code reverse-map (CITAD→original). Detail: [haengsung-exchange-rate-sync.md](../features/integrations/haengsung/exchange-rate-sync.md).
- **Test cases:** new rate → INSERT; previously-sent (`erp_snd_yn='Y'`) → UPDATE; `erp_snd_yn` flips; 0-row run writes an `ERP_TRNS_HSTR` audit; bank-code map correct.
- **Done condition:** WeCMS FX rates appear in Oracle `TB_FCY_EXRATE`; no payment-table impact.

### Step 7 — Account-History / Card-Approval Sync

- **Files expected to change:** new account-history + card push service/job (ports `ERP_0100_003`); reuse Step 1 Oracle write path.
- **Mapping:** Account history — read `ACCT_TRNS_HSTR` (`hand_reg_yn='N'`, `erp_snd_yn='N'`) → INSERT Oracle `TB_ACCT_HISTORY`; set `erp_snd_yn='Y'`; count VND/FCY. Card — read `CARD_MSTR`/`CARD_TRNS_HSTR` (`erp_snd_yn='N'`) → INSERT Oracle `TB_CARD_APPROVAL` (`CARD_NM=CARD_HLDR_NM`, `PURCHASE_ST=PRCH_STTS`); set `erp_snd_yn='Y'`. Audit rows VND=01/FCY=08/Card=07. Detail: [haengsung-card-account-sync.md](../features/integrations/haengsung/card-account-sync.md).
- **Test cases:** unsent account rows pushed + flagged; VND/FCY counted; card rows pushed + flagged; 0-row runs write audit rows; re-run sends nothing new.
- **Done condition:** WeCMS account history + card approvals appear in Oracle; no payment-table impact.

### Step 8 — End-to-end SIT Verification

- **Oracle DB connection test** — secondary datasource connects from SIT; keep-alive survives an idle period (ORA-17008 guard).
- **Payment import test** — Vendor/Expense/Payroll/Overseas import as drafts; receive write-back set.
- **Approval status test** — pending/approved/rejected/processing reflected in Oracle `LAST_STATUS`; reject detail `PROC_STS='XXXX'`.
- **Dispatch result test** — success/fail per line + batch close written to Oracle.
- **Exchange rate sync test** — `TB_FCY_EXRATE` populated.
- **Account history sync test** — `TB_ACCT_HISTORY` populated (VND+FCY).
- **Card approval sync test** — `TB_CARD_APPROVAL` populated.
- **No credential leakage check** — grep repo + logs for host/user/password; confirm `.env.sit`/`setenv.sh` not committed.
- **Logs check** — errors log SQLState/code only (no DSN); per-master rollback + retry observable; audit (`ERP_TRNS_HSTR`) rows present.
- **Done condition:** all flows verified on SIT against the real Oracle `DEV` instance; sign-off recorded.
- **Verification record:** [haengsung-oracle-integration-sit-verification.md](../audits/legacy-parity/haengsung-oracle-integration-sit-verification.md) — static PASS on all 7 flows + foundation + credential hygiene (93/93 HAENGSUNG unit tests green, 2026-06-28); the live-SIT runtime checklist (Surface B) + per-flow SQL is Section 9 there, to be executed on the SIT box.

### Final Step — Frontend Legacy Parity Verification

- **Status (2026-06-28): frontend wiring complete and functionally at legacy parity.** The refactor Refresh button (Vendor / Expense / Payroll / Overseas) now calls the real import API (`POST /api/v1/payments/haengsung/refresh`) instead of only re-fetching the list, and matches the legacy JEX frontend (`pymn_000{1,3,5,7}_001`) on every load-bearing behavior: HAENGSUNG-only button gating (else Excel Upload), Internal Transfer untouched, payment-type literals, period window, no confirmation popup, error popup, list reload, filter/page preservation, duplicate-click prevention, localization (EN/KO/VI), and permission/product-`H` gating.
- **Audit doc:** [wecms-web/docs/features/payment/haengsung-payment-refresh-frontend-parity.md](../../../wecms-web/docs/audits/haengsung-payment-refresh-frontend-parity-audit.md) — full legacy↔refactor comparison matrix + state transitions + parity checklist.
- **No required (missing) frontend behavior remains.** The refactor is not missing any legacy behavior. The only divergence is **one extra, additive UX behavior**: the refactor shows a **success popup** after a successful import (legacy shows none). This is a **PO decision (D1, Medium)** — keep it as a UX improvement, or remove it for byte-for-byte legacy parity (one-line change in `useHaengsungRefresh`). Two further differences (D2 inline spinner vs global progress overlay; D3 single-day `period_date` form) are **cosmetic and behavior-equivalent**. None block UAT.

---

## 4. Implementation Rules

- **Do not hardcode Oracle credentials** — bind from `${HAENGSUNG_ORACLE_*}` env only; fail-fast if missing while enabled.
- **Do not commit `.env.sit` or any password file** — only `.env.sit.example` (placeholders). SIT host uses `setenv.sh` OS env. If a credential is ever committed, rotate it.
- **Keep WeCMS Postgres as the primary datasource** (`@Primary`); the Oracle datasource is **secondary** and flag-gated. **Config note (2026-06-29):** per lead decision, `application-*.yml` binds the HAENGSUNG vars as **bare `${…}` placeholders with no YAML defaults**, so all 10 `HAENGSUNG_*` vars must be defined in every environment's `.env`/setenv.sh (value may be blank when `ENABLED=false`) — otherwise startup fails with `Could not resolve placeholder`. "OFF by default" therefore means `HAENGSUNG_ORACLE_ENABLED=false`, not a YAML default. The `*.example` files carry the full 10-var block.
- **Oracle tables must NOT be added to the WeCMS Flyway inventory** and get no primary-unit `@Entity`.
- **Do not duplicate legacy import logic** — `ErpDataRefreshHelper` + `ERP_0100_002` collapse into one `ErpPaymentRefreshService`; manual Refresh and scheduled import share it.
- **Use the upsert + dedup + approval-lock model** for import (not the batch's insert-only copy).
- **Use legacy as source of truth** for behaviour; **compare with the Oracle documents** (interface + status workbook) before coding every field/status. Where they disagree, the Oracle documents win for schema, legacy for behaviour — record any conflict as a mismatch.
- **Implement step by step; verify each step before the next** (Foundation gates everything; import gates write-backs that read its keys).
- **Key generation** funnels through `LegacyFunctionRepository`/`wb_get_next_val`; **no XA** across the two DBs — rely on per-master idempotency, not distributed transactions.

---

## 5. Blocking Items

Must be cleared before the step that depends on them (most before Step 2):

| Blocker | Affects | Detail |
|---|---|---|
| **M-1** — physical column block confirmation | Step 2 | Code against the physical `FILE_*` block, not the proposed `TRNS_*` block ([refresh §17](../features/integrations/haengsung/payment-refresh.md#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor)). PO confirm. |
| **M-2** — Vendor vs Expense split | Step 2 | `FILE_NM 'E%'` rule contradicted by sample data — confirm with PO/ERP how HAENGSUNG marks Expense. |
| **M-5** — Overseas `ERP_FILE_*` columns | Step 2 (Overseas) | Missing on `OvrsPymnSumr(_DETL)` entities; verify DDL + add (manual DBA DDL if absent). |
| **M-12** — `ANT_DIV_CD='T'` mapping | Step 2 | Two legacy copies disagree (INTER vs NAPAS); doc lists no `T`. Resolve with PO/ERP. |
| **Oracle SID vs service** (OQ-12) | Step 1 | Confirm JDBC URL form for instance `DEV` with DBA. |
| **Oracle datasource connectivity** | Step 1 | Reachability from local + SIT to `172.168.5.23:1521/DEV`; keep-alive policy. |
| **PO sign-off** | all | Scope + phase order + the mismatches above. |

Follow-on dependencies (not Step-2 blockers): Step 4 needs the refactor approval flow; Step 5 needs Step 4 + a live firm-banking dispatch engine.

---

## 6. Recommended Order

1. **Oracle Foundation** (Step 1) — P0, gates everything.
2. **Payment Refresh Import** (Step 2) — P1, the primary deliverable.
3. **Scheduled Import Batch** (Step 3) — P2, reuses Step 2 service.
4. **Approval-Status Write-back** (Step 4) — P3.
5. **Dispatch-Result Write-back** (Step 5) — P4 (needs dispatch live).
6. **Exchange-Rate Sync** (Step 6) — P5 (independent; can move earlier if prioritised).
7. **Account-History / Card-Approval Sync** (Step 7) — P5 (independent).
8. **Full SIT test** (Step 8) — P6, gate to UAT.
9. **Frontend Legacy Parity Verification** (Final Step) — done 2026-06-28; functionally at parity, one optional PO decision (D1 success popup) outstanding. See the Final Step subsection above + the [frontend parity audit](../../../wecms-web/docs/audits/haengsung-payment-refresh-frontend-parity-audit.md).

Steps 6–7 are independent of the payment chain (Steps 2–5) and could run in parallel once the Foundation (Step 1) exists, if a second developer is available — but they remain lower priority than the payment Refresh.

---

## 7. Cross-references
- Import + Oracle schema/status/mapping/mismatches: [haengsung-payment-refresh.md](../features/integrations/haengsung/payment-refresh.md)
- Write-backs: [haengsung-approval-status-writeback.md](../features/integrations/haengsung/approval-status-writeback.md) · [haengsung-dispatch-result-writeback.md](../features/integrations/haengsung/dispatch-result-writeback.md)
- Separate ERP sync: [haengsung-exchange-rate-sync.md](../features/integrations/haengsung/exchange-rate-sync.md) · [haengsung-card-account-sync.md](../features/integrations/haengsung/card-account-sync.md)
- Step 8 SIT verification record: [haengsung-oracle-integration-sit-verification.md](../audits/legacy-parity/haengsung-oracle-integration-sit-verification.md)
- Payment domain index: [payment/README.md](../features/payment/README.md)
- Memories: `reference_sit_server_config_setenv`, `project_hs_prod_datasource_ora17008`, `project_batch_refactor_real_impl`
