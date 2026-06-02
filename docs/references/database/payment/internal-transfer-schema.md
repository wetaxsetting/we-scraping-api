# Internal Transfer — Refactor Database Design

| Field | Value |
|---|---|
| Slug | `internal-transfer` |
| Module | `payment` |
| Companion docs | [features/payment/internal-transfer.md](../../../features/payment/internal-transfer/README.md) · [features/payment/_payment-module-overview.md](../../../features/payment/_payment-module-overview.md) · [migration/internal-transfer-mapping.md](../../migration/internal-transfer-mapping.md) · [backend/internal-transfer.md](../../business-rules/internal-transfer.md) |
| Flyway versions (shipped) | **V131** schema (`SCDUL_PYMN_SUMR` + `_DETL` + `INTERNAL_TRANSFER_KEY_SEQ`) · **V133** demo seed · **V136** APPR_STTS column · **V146** `scdul_pymn_sumr_chk01` CHECK relaxation for Payroll · **V148** dispatch tables (TB_PAY_TARGET / TB_PAY_FILE_MST / TB_PAY_FILE_DATA / TB_FIRM_*) · **V149** `BLOCKED_CHARS_RULE` schema + seed · **V188** `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` schema · **V189** payment-dispatch tables follow-up. See [src/main/resources/db/migration/](../../../../src/main/resources/db/migration/) for the full set. |
| Reconciled against DEV | 2026-05-13 via `postgres` MCP `information_schema` queries |
| Status | **Implemented** — schema is the as-shipped state of `wecms-api` HEAD; refactor-only deviations from DEV documented inline. |

> **Schema design + as-shipped** — this doc was originally written as design intent then updated to mirror the shipped Flyway files. Where the as-shipped value diverges from the DEV column shape (e.g. `SCDUL_PYMN_NO VARCHAR(20)` instead of DEV's `VARCHAR(10)`), the deviation is called out as refactor-only and explained.

---

## 1. Scope rule

This feature touches **only** tables that appear in [api CLAUDE.md § "Active table list (PROD-confirmed, FINAL)"](../../../../CLAUDE.md). The "Refactoring enforcement rule" applies — even where legacy JEX joins additional tables, the refactor migrates only the in-scope subset.

## 2. Tables touched (verified column set)

All PKs / data types / NOT NULL / DEFAULT below are read from DEV `wecmsvndev.information_schema.columns` + `pg_constraint` on 2026-05-13. Refactor Flyway must match column-for-column per the Legacy Schema Parity Rule.

### 2.1 Read + write — core master/detail

| Table | PK | Sprint state |
|---|---|---|
| `SCDUL_PYMN_SUMR` | `(biz_key, scdul_pymn_no)` | **Local migration needed** — see § 3.1 column list |
| `SCDUL_PYMN_SUMR_DETL` | `(biz_key, scdul_pymn_no, scdul_pymn_detl_no)` | **Local migration needed** — see § 3.2 column list |
| `SCDUL_PYMN_SUMR_HIS` | `(biz_key, scdul_pymn_no, hist_mod_dtm)` | **Local migration needed** — schema-only (refactor uses `AUDIT_LOGS_INFM`, not `_HIS` trigger writes) |
| `SCDUL_PYMN_SUMR_DETL_HIS` | full detail PK + `hist_mod_dtm` | Same — schema-only |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | `(biz_key, scdul_pymn_no, scdul_pymn_detl_no)` | **Local migration needed** — written on dispatch (API path). NOTE: spelt `SCDU_` not `SCDUL_` in DEV — preserve verbatim. |

### 2.2 Read + write — dispatch tables

| Table | PK (verified) | Notes |
|---|---|---|
| `TB_PAY_TARGET` | `(tran_dt, biz_key, tran_seq)` | API-path dispatch target. `tran_seq VARCHAR(10)`. `tran_amt NUMERIC(23, 2)`. |
| `TB_PAY_FILE_MST` | `(tran_dt, biz_key, file_seq_no)` | File-path dispatch master (USD→USD, Shinhan USD→VND, VND→USD Shinhan). `file_seq_no VARCHAR(7)`. |
| `TB_PAY_FILE_DATA` | `(tran_dt, biz_key, file_seq_no, seq)` | File-path dispatch line. `seq NUMERIC(4, 0)`. |

> **No separate file-path conn table.** File-path linkage is encoded directly on `SCDUL_PYMN_SUMR.pay_file_seq_no` + `SCDUL_PYMN_SUMR_DETL.pay_file_seq_no` + `SCDUL_PYMN_SUMR_DETL.pay_seq`. The `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` table only exists for the API path.

### 2.3 Read-only — already migrated or owned by sibling MRs

| Table | Use | Sprint state |
|---|---|---|
| `BUSI_INFM` | Tenant lookup | already local (V1 + V5) |
| `USER_INFM` | Requestor / approver name resolution | already local (V1 + V5 + V28) |
| `BANK_INFM` | Bank code → name | already local (V20) |
| `ACCT_MSTR` | Withdrawal account validation + `WDRL_CCY_CD` fallback | already local (V13 + V23) |
| `CHART_ACCT_INFM` | Reporting Account validation | **Local migration needed** — column verification below |
| `EXPT_RESOL_MSTR` | Approval status check | **Local migration needed** — coordinate with `payment/approval-box` MR |
| `EXPT_RESOL_APPR_LINE` / `EXPT_RESOL_APPRVR_INFM` | Approver list join | **Local migration needed** — same — owned by approval-box MR |
| `ERP_TRNS_HSTR` | Audit-history write on draft delete | already local (V71 + V72) |
| `AUDIT_LOGS_INFM` | Audit log writes | already local. PK `(biz_key, audit_logs_seq_no, lang_cd)`, `wsvc_id VARCHAR(20)` — `'internal-transfer'` (17 chars) fits. |
| `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` | View Setting seed | already local — feature migration adds seed rows |

### 2.4 New — refactor-only helper table for key generation

| Table | Role | PK |
|---|---|---|
| `INTERNAL_TRANSFER_KEY_SEQ` | Java-side `SCDUL_PYMN_NO` generator | `(biz_key, key_dt)` |

`INTERNAL_TRANSFER_KEY_SEQ` is **NOT** in DEV / PROD — refactor-only helper. Replaces legacy `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no',BIZ_KEY)`.

```sql
CREATE TABLE IF NOT EXISTS INTERNAL_TRANSFER_KEY_SEQ (
    BIZ_KEY  VARCHAR(20) NOT NULL,
    KEY_DT   DATE        NOT NULL,
    LAST_SEQ INTEGER     NOT NULL DEFAULT 0,
    REG_DTM  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    MOD_DTM  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT internal_transfer_key_seq_pk PRIMARY KEY (BIZ_KEY, KEY_DT)
);
COMMENT ON TABLE INTERNAL_TRANSFER_KEY_SEQ IS
  'Refactor-only helper — Java SCDUL_PYMN_NO generator per (tenant, day). Replaces legacy wb_get_next_val.';
```

(Storage shape depends on which BLOCKER-A option is chosen — see § 7.)

## 3. Verified column lists

### 3.1 `SCDUL_PYMN_SUMR` (29 columns)

| Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|
| `biz_key` | `VARCHAR(20)` | NO | — | PK part 1 |
| `scdul_pymn_no` | `VARCHAR(20)` (refactor) / `VARCHAR(10)` (DEV) | NO | — | PK part 2. **Refactor-only widening** — [V131:70](../../../../src/main/resources/db/migration/V131__internal_transfer_schema.sql#L70) declares 20 to fit the Java generator (`YYYYMMDD<seq:6>` = 14 chars). BLOCKER-A resolved. |
| `scdul_pymn_type_cd` | `VARCHAR(2)` | NO | — | `"03"` for Internal Transfer |
| `ccy_cd` | `VARCHAR(3)` | NO | — | Withdrawal currency |
| `titl` | `VARCHAR(100)` | NO | — | Batch title |
| `total_tran_amt` | `NUMERIC(20, 6)` | NO | — | Sum of detail `tran_amt`; 6 decimals |
| `tot_trns_cnt` | `NUMERIC(10, 0)` | NO | — | Detail row count |
| `appr_seq_no` | `VARCHAR(15)` | YES | — | FK → `EXPT_RESOL_MSTR.appr_seq_no` |
| `reg_id` | `VARCHAR(100)` | NO | — | Audit |
| `reg_dtm` | `VARCHAR(14)` | NO | — | `yyyyMMddHHmmss` |
| `mod_id` | `VARCHAR(100)` | NO | — | Audit |
| `mod_dtm` | `VARCHAR(14)` | YES | — | `yyyyMMddHHmmss` |
| `out_bank_cd` | `VARCHAR(15)` | YES | — | Withdrawal bank |
| `wdrl_act_no` | `VARCHAR(100)` | YES | — | Withdrawal account no |
| `trnf_yn` | `CHAR(1)` | YES | `'N'::bpchar` | Y after dispatch |
| `org_file_nm` | `VARCHAR(500)` | YES | — | Original uploaded Excel name |
| `save_file_nm` | `VARCHAR(500)` | YES | — | Stored filename — `getFullPath()` prepends config-root |
| `file_size` | `VARCHAR(100)` | YES | — | Byte count as string |
| `pay_tran_dt` | `VARCHAR(8)` | YES | — | yyyyMMdd of dispatch (master pointer) |
| `pay_file_seq_no` | `VARCHAR(7)` | YES | — | File-path master pointer to `TB_PAY_FILE_MST.file_seq_no` |
| `trnf_dtm` | `VARCHAR(14)` | YES | — | Dispatch timestamp |
| `erp_file_date` | `VARCHAR(8)` | YES | — | ERP integration |
| `erp_file_num` | `VARCHAR(8)` | YES | — | ERP integration |
| `erp_proc_cmpl_yn` | `CHAR(1)` | YES | `'N'::bpchar` | ERP completion flag |
| `wdrl_ccy_cd` | `VARCHAR(3)` | YES | — | Withdrawal currency (may be NULL on pre-2026-01 rows — issue #42761) |
| `acct_seq_no` | `VARCHAR(20)` | YES | — | Withdrawal account FK |
| `bank_trns_short_id` | `VARCHAR(20)` | YES | — | Bank transaction id |
| `bank_trns_full_id` | `VARCHAR(100)` | YES | — | Bank transaction full id |
| `link_match_yn` | `CHAR(1)` | YES | `'N'::bpchar` | Reconciliation flag |

### 3.2 `SCDUL_PYMN_SUMR_DETL` (33 columns)

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `biz_key` | `VARCHAR(20)` | NO | PK part 1 |
| `scdul_pymn_no` | `VARCHAR(20)` (refactor) / `VARCHAR(10)` (DEV) | NO | PK part 2 — matches master refactor width |
| `scdul_pymn_detl_no` | `VARCHAR(20)` | NO | PK part 3 |
| `tran_amt` | `NUMERIC(20, 6)` | NO | 6 decimals (master/detail share scale) |
| `ant_div_cd` | `VARCHAR(5)` | YES | NAPAS / SWIFT / CITAD / INTER |
| `rcpt_nm` | `VARCHAR(100)` | YES | Recipient name |
| `tar_bank_cd` | `VARCHAR(15)` | YES | Counterparty bank |
| `finc_mda_no` | `VARCHAR(100)` | YES | Counterparty account |
| `tran_usag` | `VARCHAR(200)` | YES | Transfer purpose |
| `tar_acct_rmrk` | `VARCHAR(150)` | YES | Counterparty remark |
| `wdrl_acct_rmrk` | `VARCHAR(150)` | YES | Withdrawal remark |
| `pay_tran_dt` | `VARCHAR(8)` | YES | API-path pointer to `TB_PAY_TARGET.tran_dt` |
| `pay_tran_seq` | `VARCHAR(10)` | YES | API-path pointer to `TB_PAY_TARGET.tran_seq` |
| `pay_file_seq_no` | `VARCHAR(7)` | YES | File-path pointer |
| `pay_seq` | `NUMERIC(4, 0)` | YES | File-path pointer to `TB_PAY_FILE_DATA.seq` |
| `reg_id`, `reg_dtm`, `mod_id`, `mod_dtm` | audit cols | — | Same as master |
| `trco_key`, `hand_trco_key` | `VARCHAR(20)` each | YES | ERP keys |
| `doc_no` | `VARCHAR(50)` | YES | Document number |
| `note` | `VARCHAR(200)` | YES | **200 not 255** — corrected from earlier drafts |
| `citad_bank_cd` | `VARCHAR(50)` | YES | CITAD code |
| `trco_cmnm`, `trco_cmenm` | `VARCHAR(200)` each | YES | Trade company names |
| `chart_acct_cd` | `VARCHAR(20)` | YES | Reporting account code |
| `chart_acct_seq_no` | `VARCHAR(20)` | YES | Reporting account FK — **20 not 10** as WSVC declares |
| `erp_file_date`, `erp_file_num`, `erp_file_seq` | ERP integration | YES | |
| `trnf_ccy_cd` | `VARCHAR(3)` | YES | |
| `erp_attribute2` | `VARCHAR(150)` | YES | |
| `depo_ccy_cd` | `VARCHAR(3)` | YES | Drives routing |
| `acct_seq_no`, `bank_trns_short_id`, `bank_trns_full_id`, `link_match_yn` | reconciliation cols | YES | |

### 3.3 `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN`

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `biz_key` | `VARCHAR(20)` | NO | PK |
| `scdul_pymn_no` | `VARCHAR(20)` (refactor) | NO | PK |
| `scdul_pymn_detl_no` | `VARCHAR(20)` | NO | PK |
| `tran_dt` | `VARCHAR(8)` | YES | FK → `TB_PAY_TARGET.tran_dt` |
| `tran_seq` | `VARCHAR(10)` | YES | FK → `TB_PAY_TARGET.tran_seq` |

**Only 5 columns.** No audit cols. PK includes only the detail key — meaning one-to-one detail ↔ target binding.

### 3.4 `TB_PAY_TARGET` (selected cols — full column list in DDL)

PK `(tran_dt, biz_key, tran_seq)`. Notable columns:

- `tran_amt NUMERIC(23, 2) NOT NULL` (2 decimals — dispatch rounds 6→2)
- `ant_div_cd CHAR(5) NOT NULL`
- `proc_stat CHAR(1) DEFAULT '9'` — **see BLOCKER-B** (ProcStat.TRANSFER_PENDING is `"50"`, two chars)
- `trsc_ccy_cd CHAR(3) DEFAULT 'VND'`
- `withd_ccy_cd VARCHAR(3)`, `dep_ccy_cd VARCHAR(3)`, `apl_exc_rate NUMERIC(23, 2)`, `withd_amt NUMERIC(23, 2)`, `dep_amt NUMERIC(23, 2)`
- `proc_dttm VARCHAR(14)`, `rerun_div CHAR(1) DEFAULT 'N'`, `rerun_req_dttm VARCHAR(14)`, `user_id VARCHAR(100)`
- `finc_mda_owner_nm VARCHAR(150)`, `tran_usag VARCHAR(200)`

### 3.5 `TB_PAY_FILE_MST` (selected)

PK `(tran_dt, biz_key, file_seq_no)`. Notable columns:

- `bank_prtn_id VARCHAR(20) NOT NULL`
- `fund_tp CHAR(1) NOT NULL`, `tran_tp CHAR(1) NOT NULL`
- `proc_stat CHAR(1) DEFAULT '9' NOT NULL`
- `req_cnt NUMERIC(7, 0)`, `req_amt NUMERIC(23, 2)`
- `reg_dttm`, `proc_dttm VARCHAR(14)`, `rs_msg VARCHAR(200)`

### 3.6 `TB_PAY_FILE_DATA` (selected)

PK `(tran_dt, biz_key, file_seq_no, seq)`. Notable columns:

- `tar_bank_cd VARCHAR(15) NOT NULL`
- `finc_mda_div VARCHAR(1) DEFAULT 'A' NOT NULL`, `finc_mda_no VARCHAR(100) NOT NULL`
- `trsc_ccy_cd CHAR(3) DEFAULT 'VND' NOT NULL`
- `tran_amt NUMERIC(23, 2) NOT NULL`
- `bank_chnl_div CHAR(1) NOT NULL`
- `ant_div_cd CHAR(1)` — **only 1 char on file-path** (vs `CHAR(5)` on target)
- `finc_mda_owner_nm VARCHAR(150) NOT NULL`, `tran_usag` (not present — uses `tar_acct_rmrk`)
- `proc_rslt_div CHAR(1) DEFAULT 'N'`, `rs_cd VARCHAR(10)`

### 3.7 `CHART_ACCT_INFM` (verified)

PK `(biz_key, chart_acct_seq_no)`. Notable: `chart_acct_seq_no VARCHAR(14)`, `chart_acct_cd VARCHAR(20)`, `chart_acct_nm_en VARCHAR(255)` NOT NULL, multilingual `_vn` / `_ko` `VARCHAR(255)`, `del_yn CHAR(1)` NOT NULL.

> **Anomaly:** DEV `chart_acct_infm.chart_acct_seq_no` is `VARCHAR(14)` but `scdul_pymn_sumr_detl.chart_acct_seq_no` is `VARCHAR(20)`. The longer width on the FK side is harmless (extra room never used) but worth noting.

### 3.8 `AUDIT_LOGS_INFM` (verified, already local)

PK `(biz_key, audit_logs_seq_no, lang_cd)`. Three-language insert per write — `lang_cd` is the dictionary code (`EN`, `KO`, `VI`). Columns:

`biz_key VARCHAR(20)`, `audit_logs_seq_no NUMERIC`, `wsvc_id VARCHAR(20) NOT NULL`, `lang_cd VARCHAR(3) NOT NULL`, `ref_no VARCHAR(50) NOT NULL`, `ref_no1 VARCHAR(50) NOT NULL`, `ref_no2..5 VARCHAR(50)` nullable, `audit_logs_actv_dtl TEXT NOT NULL`, `reg_id VARCHAR(100) NOT NULL`, `reg_nm VARCHAR(100) NOT NULL`, `reg_dtm VARCHAR(14) NOT NULL`, `audit_logs_actv VARCHAR(50)` nullable.

`internal-transfer` (17 chars) fits `wsvc_id VARCHAR(20)` comfortably.

## 4. Schema-parity notes per table

For each table flagged "Local migration needed", the Flyway migration follows the [Legacy Schema Parity Rule](../../../../CLAUDE.md):

- Local migration column count == DEV column count (verified above).
- Every legacy NOT NULL has the legacy DEFAULT or is in the seed.
- PK + every `*_uk*` UNIQUE + every `*_chk*` CHECK carried over verbatim.
- FKs between in-scope tables added; FKs to out-of-scope tables (e.g. `BUSI_INFM` if not yet present) are deferred via a trailing comment.
- Helper indexes added on top:
  - `(biz_key, reg_dtm DESC)` on `SCDUL_PYMN_SUMR` (drives list query)
  - `(biz_key, scdul_pymn_no)` on `SCDUL_PYMN_SUMR_DETL` (overlaps PK prefix — kept for clarity)
  - `(biz_key, trnf_yn)` on `SCDUL_PYMN_SUMR` (filter for "dispatchable" batches)
  - `(biz_key, tran_dt, tran_seq)` on `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` (reverse-join from `TB_PAY_TARGET`)

Triggers (legacy `_log_h` audit triggers) — **deferred** per the Sprint 1 precedent (see V24). The refactor uses `AUDIT_LOGS_INFM` writes from the service layer; the legacy trigger-driven `_HIS` tables stay column-parity-only and are not trigger-populated.

## 5. View Setting seed (no schema change to `SYS_SCR_OPTION_WECMS`)

Per [api CLAUDE.md § Screen Option (View Setting)](../../../../CLAUDE.md), new pages add seed rows in the same Flyway migration. This feature seeds four screens:

| WEB_SVC_ID | DYM_AREA_ID | FIELD_IDs (DISP_ORD, FIX_YN where locked) |
|---|---|---|
| `payment-internal-transfer-scheduled` | `default` | `CHECKBOX` (0, FIX) · `REG_DTM` (1) · `TITL` (2) · `TOT_TRNS_CNT` (3) · `TOTAL_TRAN_AMT` (4) · `APPR_STTS` (5) · `REG_USER` (6) · `APPROVER` (7) · `APPR_SEQ_NO` (8) · `TRNF_STTS` (9) · `DOC_NO` (10) · `ETC` (99) |
| `payment-internal-transfer-result` | `default` | `REG_DTM` (1) · `TITL` (2) · `TOT_TRNS_CNT` (3) · `TOTAL_TRAN_AMT` (4) · `TRNF_STTS` (5) · `APPR_SEQ_NO` (6) · `DOC_NO` (7) · `ETC` (99) |
| `payment-internal-transfer-scheduled-detail` | `default` | one row per detail column: `SCDUL_PYMN_DETL_NO`, `TAR_BANK_CD`, `TAR_BANK_NM`, `RCPT_NM`, `FINC_MDA_NO`, `TRAN_AMT`, `TRAN_USAG`, `CHART_ACCT_CD`, `CHART_ACCT_NM`, `DOC_NO`, `NOTE`, `DEPO_CCY_CD` |
| `payment-internal-transfer-result-detail` | `default` | one row per detail column: `SCDUL_PYMN_DETL_NO`, `PROC_DTTM`, `TAR_BANK_NM`, `RCPT_NM`, `TRAN_AMT`, `NOTE`, `PROC_STAT`, `RSLT_DTTM`, `CHART_ACCT_NM`, `DOC_NO`, `BANK_TRNS_SHORT_ID` |

`SORT_YN` defaults to `'Y'` on every column **except** `CHECKBOX`, `ETC` (per [api CLAUDE.md § Common table sort](../../../../CLAUDE.md) — special FIELD_IDs are never sortable).

Seed SQL pattern:

```sql
INSERT INTO SYS_SCR_OPTION_WECMS (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, DISP_YN, FIX_YN, SORT_YN, SIZE, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
  ('payment-internal-transfer-scheduled', 'default', 'CHECKBOX', 'Checkbox',   0, 'Y','Y','Y','N','40px',  'system', now(), 'system', now()),
  ('payment-internal-transfer-scheduled', 'default', 'REG_DTM',  'Date / Time', 1, 'Y','Y','N','Y','150px', 'system', now(), 'system', now()),
  -- … one row per FIELD_ID per table above …
ON CONFLICT (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID) DO NOTHING;
```

## 6. Seed data (real DEV rows, ≤ 30 per table)

Per [api CLAUDE.md § Data Migration Rule](../../../../CLAUDE.md):

- Only migrate rows for `BIZ_KEY = 'VN_VNM0004_000001'` (live data has 365 SCDUL_PYMN_SUMR rows for this tenant).
- Maximum 30 rows per refactored active table — take the most recent by `REG_DTM DESC`.
- Pull from real DEV via the `postgres` MCP, `INSERT … ON CONFLICT DO NOTHING`.
- Insert parents first: `SCDUL_PYMN_SUMR` → `SCDUL_PYMN_SUMR_DETL` → `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` → `TB_PAY_TARGET` / `TB_PAY_FILE_MST` / `TB_PAY_FILE_DATA`.

Physical Excel files referenced by `save_file_nm` are **NOT** seeded — the file-download endpoint surfaces `WBE2015` (ERP Transaction History precedent) if the local file is missing.

## 7. Key-generation strategy — BLOCKER-A resolved 2026-05-14

The shipped strategy: refactor widens the column to `VARCHAR(20)` and uses a date-prefixed format (14 chars). The previously-proposed 10-char strategies (kept below for reference) were rejected in favour of legibility in logs.

**As-shipped format:** `<YYYYMMDD><6-digit-tenant-day-seq>` — 14 characters.

```
20260513000001
20260513000002
…
20260514000001  ← new day rolls over to a fresh per-(tenant, day) sequence
```

Vietnam-local date (UTC+7), per-tenant per-day sequence 1..999,999 (range-guarded in [`InternalTransferKeyGenerator.format`](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java#L90-L97)).

**Generator implementation** ([`InternalTransferKeyGenerator.nextScdulPymnNo`](../../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferKeyGenerator.java#L77)):

- `@Transactional(propagation = REQUIRES_NEW)` so the sequence advance commits even if the outer business-write transaction rolls back. Orphaned numbers are acceptable — there's no contiguous-id requirement.
- Backed by `INTERNAL_TRANSFER_KEY_SEQ` (`(BIZ_KEY, KEY_DT)` PK) — `INSERT … ON CONFLICT (BIZ_KEY, KEY_DT) DO UPDATE SET LAST_SEQ = LAST_SEQ + 1 RETURNING LAST_SEQ`. PostgreSQL's row-level lock serialises concurrent saves on the same `(tenant, day)`.
- Refactor-only deviation from DEV's `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no',BIZ_KEY)` branch. Documented in [api CLAUDE.md "DB Function / Legacy Function Policy"](../../../../CLAUDE.md#db-function--legacy-function-policy) — no new `wb_get_next_val` branch added.

> ### Reference: the two 10-char strategies that were proposed and rejected
>
> Kept here in case a future audit revisits the decision.
>
> **Option (a)** — pure numeric per-tenant lifetime sequence (10 digits):

```
Format: zero-padded(LAST_SEQ, 10)
Example: 0000000001 → 9999999999 (~10 billion rows per tenant lifetime)

Algorithm (InternalTransferKeyGenerator):

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextScdulPymnNo(String bizKey) {
        Integer next = entityManager.createNativeQuery(
            "INSERT INTO INTERNAL_TRANSFER_KEY_SEQ (BIZ_KEY, KEY_DT, LAST_SEQ) " +
            "VALUES (:bizKey, DATE '1970-01-01', 1) " +    // KEY_DT is a fixed sentinel for option (a)
            "ON CONFLICT (BIZ_KEY, KEY_DT) DO UPDATE SET LAST_SEQ = INTERNAL_TRANSFER_KEY_SEQ.LAST_SEQ + 1, MOD_DTM = now() " +
            "RETURNING LAST_SEQ", Integer.class)
            .setParameter("bizKey", bizKey)
            .getSingleResult();
        return String.format("%010d", next);
    }
```

**Option (b)** — `{YY}{MMDD}{4-digit-seq}` (10 chars), date-prefixed:

```
Format: YY + MMDD + zero-padded(LAST_SEQ, 4)
Example: 2026-05-13 → "260513" + 4-digit daily counter
  First batch of the day on tenant T: "2605130001"
  10,000th batch same day: rolls over to "2605139999" — alert before this happens

Algorithm uses (bizKey, key_dt=Vietnam-local-date) as the conflict target — sequence resets daily.
```

REQUIRES_NEW ensures the sequence advance survives outer-transaction rollback (the next attempt gets a fresh number; the orphaned number is acceptable — there is no requirement that `SCDUL_PYMN_NO`s be contiguous).

> Both 10-char options above were rejected. The shipped format is the 14-char date-prefixed variant described at the top of §7.

## 8. File storage layout

Mirror ERP Transaction History (V72) precedent. No `FILE_INFO` blob column in DEV — three separate columns:

- `org_file_nm VARCHAR(500)` — original upload name (preserved for download)
- `save_file_nm VARCHAR(500)` — stored filename; service composes full path at read time
- `file_size VARCHAR(100)` — byte count

`FULL_PATH` rewritten to point under `wecms.internal-transfer.storage-root` on save — never the legacy `C:/WECMS PROJECT/File Data/payment` path. Save Draft writes the uploaded file with `save_file_nm = <SCDUL_PYMN_NO>_<original-name>` so collisions across tenants/days are impossible.

Delete flow writes an `ERP_TRNS_HSTR` row (mirrors legacy `pymn_0009_001_d001_act.jsp:95-98`) and **moves** the file to a "deleted" subfolder for one retention period, then a separate sweeper deletes — not in scope this MR; for now the delete handler simply deletes the file inline.

## 9. Constraints & FK status

Following [api CLAUDE.md § Constraints, indexes, and triggers](../../../../CLAUDE.md):

| Constraint type | Status |
|---|---|
| PK on every table | Carried over verbatim (verified above) |
| `*_uk*` UNIQUE | Carried over verbatim |
| `*_chk*` CHECK | Carried over verbatim |
| FK `SCDUL_PYMN_SUMR_DETL → SCDUL_PYMN_SUMR` | Carried over |
| FK `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN → SCDUL_PYMN_SUMR_DETL` | Carried over |
| FK `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN → TB_PAY_TARGET` | Carried over once both tables are in local Flyway |
| FK to `EXPT_RESOL_MSTR` | Carried over once approval-box MR lands first; otherwise deferred via trailing comment |
| FK to `ACCT_MSTR.acct_no` from `SCDUL_PYMN_SUMR.wdrl_act_no` | Carried over if legacy declares it; otherwise application-layer only |
| FK to `BUSI_INFM` (BIZ_KEY) | Mirror legacy verbatim |
| FK to `CHART_ACCT_INFM` from `SCDUL_PYMN_SUMR_DETL.chart_acct_seq_no` | Carried over once `chart_acct_infm` migration lands; deferred if sibling MR not ready |
| Audit-history triggers | **Deferred** — refactor uses `AUDIT_LOGS_INFM` writes from the service layer; `_HIS` tables stay column-parity-only |

## 10. Open questions (echoed from feature spec § 12)

- **BLOCKER-A** `SCDUL_PYMN_NO` key generation — **RESOLVED 2026-05-14.** Refactor widens column to `VARCHAR(20)` in V131; Java generator emits 14-char `YYYYMMDD<seq:6>`.
- **BLOCKER-B** `proc_stat` column width vs enum value width — `CHAR(1)` column with `"50"` enum value. Internal Transfer's Phase A dispatch service stamps `TRNF_YN='Y'` + audit row but does NOT yet insert TB_PAY_TARGET rows, so the runtime impact is zero. Verify on the next parity sweep against shipped V148 / V189.
- **CLARIFY-5** Cut-off table source — refactor uses YAML properties (`wecms.internal-transfer.cutoff.*`) via `CutoffValidator`. No new `BANK_CUTOFF_INFM` table.
- **CLARIFY-7** `TB_PAY_FILE_*` and `TB_PAY_TARGET` migration ordering — **shipped together in V148** (initial dispatch schema) + **V189** (follow-up). Conn table in V188. No collision with sibling MRs.

## 11. Shared with siblings (Vendor / Expense / Payroll)

The four Payment sub-modules **share** `SCDUL_PYMN_SUMR` + `SCDUL_PYMN_SUMR_DETL`, discriminated by `SCDUL_PYMN_TYPE_CD`:

| Slug | `SCDUL_PYMN_TYPE_CD` | Verified at |
|---|---|---|
| `internal-transfer` | `"03"` | [pymn_0009_001_r001_act.jsp:61](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0009_001_r001_act.jsp#L61) + refactor enum `ScdulPymnTypeCd.INTERNAL` |
| `vendor-payment` | `"01"` | [pymn_0001_001_r001_act.jsp:69](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0001_001_r001_act.jsp#L69) + refactor enum `ScdulPymnTypeCd.VENDOR` |
| `expense-payment` | `"02"` | [pymn_0003_001_r001_act.jsp:68](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0003_001_r001_act.jsp#L68) + refactor enum `ScdulPymnTypeCd.EXPENSE` |
| `payroll` | `"00"` | [pymn_0005_001_r001_act.jsp:173](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/pymn/pymn_0005_001_r001_act.jsp#L173) + refactor enum `ScdulPymnTypeCd.PAYROLL` |

The schema in §3.1 + §3.2 is the **single** master/detail table shape used by all four siblings. Each sibling's list query adds `WHERE scdul_pymn_type_cd = '<code>'` to its derived-method finder.

V146 ("scdul_pymn_sumr_chk01_allow_payroll") **relaxes the CHECK constraint** on `scdul_pymn_sumr` to admit Payroll's `'00'` discriminator value. Don't tighten the CHECK without re-allowing all four discriminators.

The dispatch tables in §2.2 (`TB_PAY_TARGET`, `TB_PAY_FILE_MST`, `TB_PAY_FILE_DATA`, `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN`) are likewise shared infrastructure — single set of tables, all four siblings write into them via `TransferDispatchExecutor` (see [backend/internal-transfer.md §16.1](../../business-rules/internal-transfer.md)).

`INTERNAL_TRANSFER_KEY_SEQ` (§2.4) is **Internal-Transfer-specific** — Vendor / Expense / Payroll each maintain their own per-sibling key generators (separate helper tables) so day-rolls and seq exhaustion are independent. The naming convention is `{SIBLING}_KEY_SEQ` / `{Sibling}KeyGenerator`.

---

Last verified: 2026-05-21
