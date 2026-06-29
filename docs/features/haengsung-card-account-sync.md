# HAENGSUNG Card / Account-History Sync — Backend Feature Spec (Separate ERP feature)

**Slug:** `haengsung-card-account-sync` · **Domain:** Banking/ERP-sync · **Status:** ✅ Implemented (Step 7, backend + unit tests; flag-gated OFF by default) · **Product type:** `H` only · **Last updated:** 2026-06-28

> **Implemented 2026-06-28.** Backend + unit tests landed (see [§8 Implementation](#8-implementation-2026-06-28)). Reuses the Step-1 Oracle datasource + existing WeCMS `ACCT_TRNS_HSTR` / `CARD_MSTR` / `CARD_TRNS_HSTR` source tables/entities — no new banking/card business logic, no payment tables touched. Legacy source of truth: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_003.java`.

## 1. Purpose

Push WeCMS-collected **account transaction history** and **corporate-card approvals** out to the Haengsung Oracle ERP. Pure outbound sync; no payment data, no `TB_TRNS_*`.

## 2. Direction

**WeCMS → Oracle** (one-way push).

## 3. Tables

| Section | Oracle target | WeCMS source |
|---|---|---|
| Account history | `TB_ACCT_HISTORY` (`TB_ACCT_HISTORY_C002`) | `ACCT_TRNS_HSTR` (read `ACCT_TRNS_HSTR_R029`; sent-flag `ACCT_TRNS_HSTR_U002`) |
| Card approval | `TB_CARD_APPROVAL` (`TB_CARD_APPROVAL_C002`) | `CARD_MSTR` (read `CARD_MSTR_R005`) + `CARD_TRNS_HSTR` (sent-flag `CARD_TRNS_HSTR_U003`) |

## 4. Flow

1. Select tenants `BUSI_INFM WHERE prdt_type='H' AND del_yn='N'`.
2. **Section 1 — Account history** (own transaction): read `ACCT_TRNS_HSTR` rows (`hand_reg_yn='N'`, `coalesce(erp_snd_yn,'N')='N'`); insert each into `TB_ACCT_HISTORY` (`BIZ_NO`=taxId, `CMS_PUT_DTTM`, `ERP_GET_ST='0'`); count VND vs FCY by `CCY_CD`; set WeCMS `erp_snd_yn='Y'`. Write two `ERP_TRNS_HSTR` rows (VND `TRNS_TYPE_CD=01`, FCY `08`), even on 0.
3. **Section 2 — Card approval** (own transaction): read card-approval rows (`coalesce(erp_snd_yn,'N')='N'`); insert each into `TB_CARD_APPROVAL` (`BIZ_NO`, `CMS_PUT_DTTM`, `ERP_GET_ST='0'`, `CARD_NM=CARD_HLDR_NM`, `PURCHASE_ST=PRCH_STTS`); set WeCMS `erp_snd_yn='Y'`. Write one `ERP_TRNS_HSTR` row (`TRNS_TYPE_CD=07`), even on 0.
4. All `ERP_TRNS_HSTR` rows are `ERP_TYPE='W'` (WeCMS→ERP).

## 5. Schedule / frequency

JEX batch (`ERP_0100_003`, `@JexDataInfo(type=BAT)`). No cron in source — configured in Jex Studio batch scheduler / `TB_BATCH_SCHEDULE_*`. Frequency not stated in source; confirm in Jex Studio.

## 6. Why this is separate from payment Refresh

- Touches **no** `TB_TRNS_MAST`/`TB_TRNS_DETAIL` and **no** WeCMS payment tables (`SCDUL_*`/`OVRS_*`).
- Opposite direction (WeCMS→Oracle push, vs Refresh's Oracle→WeCMS import).
- Different domains (account history + corporate card), different tables, independent schedule.
- Maps to separate refactor features — Account Inquiry / Transaction History and Card Transactions — not the payment Refresh.

**Impact if not built with Phase 1:** none on payment Refresh — account history + card approvals simply aren't pushed to ERP.

## 8. Implementation (2026-06-28)

Backend implemented as a flag-gated scheduled push, faithful to legacy `ERP_0100_003`. Reuses existing WeCMS source tables/repositories and the Step-1 Oracle datasource; no new banking/card business logic; **no payment tables** (`TB_TRNS_*` / `SCDUL_*` / `OVRS_*`) touched.

**Files changed.**
- `domain/payment/haengsung/cardaccount/HaengsungCardAccountSyncService.java` *(new)* — two-section sync (`sync()` → `Summary`). Gated `@ConditionalOnProperty(haengsung.oracle.enabled=true)`.
- `domain/payment/haengsung/cardaccount/HaengsungCardAccountOracleRepository.java` *(new)* — Oracle `TB_ACCT_HISTORY` + `TB_CARD_APPROVAL` inserts via the Step-1 secondary Oracle `JdbcTemplate`.
- `domain/payment/haengsung/cardaccount/{AcctHistorySyncRow,CardApprovalSyncRow}.java` *(new)* — read projections; `{OracleAcctHistory,OracleCardApproval}.java` *(new)* — mapped insert records.
- `domain/payment/haengsung/cardaccount/HaengsungCardAccountSyncJob.java` + `HaengsungCardAccountSyncProperties.java` *(new)* — disabled-by-default scheduler + config.
- `domain/banking/repository/AcctTrnsHstrRepository.java` — `+findHaengsungAcctHistoryForSync(...)` (native, mirrors `ACCT_TRNS_HSTR_R029`) + `+markErpSent(bizKey, acctSeqNo)` (legacy `ACCT_TRNS_HSTR_U002`, per-row).
- `domain/payment/cardtransactions/repository/CardTrnsHstrRepository.java` — `+findHaengsungCardApprovalForSync(...)` (native, mirrors `CARD_MSTR_R005`) + `+markErpSent(bizKey, cardSeqNo)` (legacy `CARD_TRNS_HSTR_U003`, per-row).
- `application.yml` — `haengsung.card-account-sync.{enabled:false, cron:0 */5 * * * *}` block.

**Source queries/repositories reused.** `ACCT_TRNS_HSTR` (+ `ACCT_MSTR` join) via `AcctTrnsHstrRepository`; `CARD_TRNS_HSTR` (+ `CARD_MSTR` join) via `CardTrnsHstrRepository`; `CITAD_BANK_CD_LOOKUP` (V44) for the bank-code mapping (all banks, not a hardcoded set); HAENGSUNG fan-out via the existing `BusiInfmRepository.findByPrdtTypeAndDelYn("H","N")`.

**Oracle SQL added (verbatim legacy `*_C002` IDOs).** Both are idempotent `INSERT … SELECT … FROM DUAL WHERE NOT EXISTS` on the PK — re-run safe (0 rows, never a dup-key error). `ERP_GET_ST='0'`; `ERP_GET_DTTM` + columns not produced by the read are null (legacy `putAll` parity).
- `TB_ACCT_HISTORY` PK `(BIZ_NO, BANK_CD, ACCT_NO, TR_DT, TR_SEQNO)`.
- `TB_CARD_APPROVAL` PK `(BIZ_NO, BANK_CD, CARD_NO, APPR_DT, APPR_TM, AUTH_NO)`.

**Account mapping (verbatim R029/C002).** `BIZ_NO`=`TAX_ID`; `BANK_CD`=CITAD of `ACCT_TRNS_HSTR.BANK_CD`; `ACCT_NO`; `TR_DT`=`TRSC_DT`; `TR_SEQNO`=`ACCT_SEQ_NO`; `CCY_CD`; `IO_GB`=`INOUT_DV`; `TR_AMT`=`TRSC_AMT`; `TR_CUR_BAL`=`BAL`; `ACCT_MEMO`=`RMRK`; `LAST_INQ_DTTM`=`ACCT_MSTR.ACCT_HIS_LST_INQ_DT`; `CMS_PUT_DTTM`=now(VN); `BANK_BRANCH`/`PURPOSE`/`VAS`/`USER_MENO`/`GDT_INVOICE_INFO`=null. Read filter `hand_reg_yn='N' AND coalesce(erp_snd_yn,'N')='N'`. VND vs FCY counted by `CCY_CD`.

**Card mapping (verbatim R005/C002).** `BIZ_NO`=`TAX_ID`; `BANK_CD`=CITAD of `CARD_TRNS_HSTR.BANK_CD`; `CARD_NO`; `APPR_DT`=`TRSC_DT`; `APPR_TM`=`TRSC_TM`; `AUTH_NO`= legacy CASE (`BANK_CD='11704020'` → `TRSC_DT||TRSC_TM`, else `AUTH_CODE`); **`CARD_NM`=`CARD_MSTR.CARD_HLDR_NM`** (legacy override); `MERCH_NM`; `CCY_CD`; `APPR_AMT`=`TRSC_AMT`; **`PURCHASE_ST`=`PRCH_STTS`**; `CITY`; `LAST_INQ_DTTM`=`CARD_MSTR.CARD_HIS_LST_INQ_DT`; `CMS_PUT_DTTM`=now(VN); `USAGE`/`PURPOSE`/`VAS`/`APPR_SEQNO`/`GDT_INVOICE_INFO`=null (legacy `putAll` leaves them unset — `APPR_SEQNO` is null because the legacy `putAll` key is `CARD_SEQ_NO`, which does not bind to `:APPR_SEQNO`). Read filter `coalesce(cth.erp_snd_yn,'N')='N'`.

**Scheduling.** `HaengsungCardAccountSyncJob` — `@Scheduled(cron = ${haengsung.card-account-sync.cron:0 */5 * * * *}, zone=Asia/Ho_Chi_Minh)` + `@SchedulerLock`. Bean only when `haengsung.card-account-sync.enabled=true` (default false); no-ops when `haengsung.oracle.enabled=false` (service bean absent → `ObjectProvider` null). Legacy cadence is Jex-Studio-defined (not in source); configurable, 5-minute default.

**Transaction / retry (no XA).** WeCMS and Oracle are separate datasources. Per row: Oracle insert commits on its own; the WeCMS `ERP_SND_YN` flip happens only after the insert succeeds (per-row, by the source PK — `ACCT_SEQ_NO` / `CARD_SEQ_NO`). On Oracle failure the WeCMS row is left unchanged, logged, and the loop continues — retried next run (idempotent insert ⇒ no dup-key). One company / section / row failure never blocks the rest. *(Documented divergence: legacy flags ALL unsent rows by `BIZ_KEY` in one bulk update inside the section transaction; without XA that would mark un-pushed rows as sent on a partial push, so the refactor flags per row — strictly safer and matches the prompt's per-row-continuation rule.)* Audit `ERP_TRNS_HSTR` rows (`ERP_TYPE='W'`): VND `'01'`, FCY `'08'`, Card `'07'` — one set per company, even on 0 records; never aborts the sync.

**Tests.** `HaengsungCardAccountSyncServiceTest` — VND/FCY split + per-row flag; verbatim account + card mapping (incl. `CARD_NM`=holder, `PURCHASE_ST`=`PRCH_STTS`, BIZ_NO=tax id); one account-row failure continues + leaves it unflagged; one card-row failure continues + leaves it unflagged; audit rows even at 0; HAENGSUNG-only fan-out; tax-id guard; summary counts; no payment-table repos injected. `./gradlew compileJava` + the card/account-sync suite pass.

**Remaining (Step 8 — NOT in scope here):** end-to-end SIT verification of all seven flows against the real Oracle `DEV` instance (the roadmap's gate to UAT) — see [haengsung-oracle-integration-roadmap.md §3 Step 8](haengsung-oracle-integration-roadmap.md).

## 7. Cross-references
- Scope boundary: [haengsung-payment-refresh.md §25](haengsung-payment-refresh.md#25-scope-decision-for-first-implementation).
- Sibling ERP sync: [haengsung-exchange-rate-sync.md](haengsung-exchange-rate-sync.md).
- Legacy source: `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_003.java`.
