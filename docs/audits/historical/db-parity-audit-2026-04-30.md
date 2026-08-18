# Local DB vs DEV DB — Full Parity Audit (2026-04-30)


> ⚠️ **Historical migration numbering (note added 2026-08-17).** This is a dated, point-in-time DB parity audit
> written on 2026-04-30, **before** the [2026-06-09 Flyway rebaseline](../../references/database/flyway-migration-baseline.md).
> Every `V<n>` below is a **pre-rebaseline number** and is preserved as written — the findings are only
> meaningful against the migration set as it stood that day. Those numbers do **not** identify the same
> migrations today (the set was renumbered to one-table-per-migration, and seeds / helper indexes / views
> were dropped entirely). To find the current migration for any table, see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5 or run
> `ls src/main/resources/db/migration/ | grep <table>`.

**Scope:** all 23 tables present in the local refactor DB at `localhost:5434/wecms_dev` (public schema), compared against `wecmsvndev` schema in DEV at the same date.

**Methodology:** read-only queries on both DBs (postgres-mcp). For each table: column list, PK/UK/CHECK/FK constraints, indexes, triggers, row counts. Function `wb_get_next_val` compared by branch list.

**Decision rules followed (from user instructions):**

1. DB schema must match DEV.
2. Java entity/DTO/repository/service updated only when needed.
3. No mapping every column into Java unnecessarily.
4. Do not delete local-only tables/columns automatically — classify only.
5. Do not add all DEV-only tables blindly.
6. Seed data for migrated business tables: prefer real DEV rows.
7. Tenant: `BIZ_KEY = VN_VNM0004_000001`.
8. `wb_get_next_val`: Sprint 0 + Sprint 1 migrated-table branches that exist in DEV; nothing else.

---

## 1. Full Local Table List (23 tables)

All 23 local tables exist in DEV under `wecmsvndev`.

| # | Table | Domain | Source migration |
|---|---|---|---|
| 1 | `acct_mstr` | banking | V13/V18/V23/V24 |
| 2 | `acct_trns_hstr` | banking | V13/V18/V23/V24 |
| 3 | `acct_trns_rcv_pay_evid_file` | banking | V13/V18/V21/V23/V24 |
| 4 | `acct_trns_rcv_pay_infm` | banking | V13/V18/V19/V21/V23/V24 |
| 5 | `bank_infm` | banking | V20/V24 |
| 6 | `bat_call_his` | reports | V14/V18/V24 |
| 7 | `busa_doc_mstr` | einvoice | V15/V17/V18/V23/V24 |
| 8 | `busa_doc_rcv_pay_his` | banking | V13/V18/V23 |
| 9 | `busi_admn_infm` | auth/admin | V11 |
| 10 | `busi_cond_daily_sumr` | reports | V14/V18/V24 |
| 11 | `busi_cond_daily_sumr_detl` | reports | V14/V18/V24 |
| 12 | `busi_infm` | auth | V1/V7/V8/V9/V24 |
| 13 | `einv_fee_detl` | einvoice | V15/V18 |
| 14 | `einv_tax_detl` | einvoice | V15/V18 |
| 15 | `empl_infm` | auth/admin | V11/V12/V18 |
| 16 | `ext_tran_call_his` | operation | V16/V18 |
| 17 | `napas_remit_trns_hstr` | banking | V13/V18 |
| 18 | `oprt_stts_info` | operation | V16/V18 |
| 19 | `sell_his_daily_sumr` | reports | V14/V18/V24 |
| 20 | `sell_his_daily_sumr_detl` | reports | V14/V18/V24 |
| 21 | `user_busi_conn_infm` | auth | V1/V3/V7 |
| 22 | `user_infm` | auth | V1/V3/V7 |
| 23 | `user_login_his` | auth | V1/V3 |

Plus `flyway_schema_history` (Flyway-internal; not compared).

No DEV-only tables that local **must** add right now — Sprint 1 endpoints are read-only and don't reference any out-of-scope DEV table.

---

## 2. Per-Table Parity Summary

| Table | Local cols | DEV cols | Missing in local | Extra in local | Type mismatches | Default mismatches | Constraint gaps |
|---|---|---|---|---|---|---|---|
| acct_mstr | 48 | 48 | — | — | 5 | 2 | (none) |
| acct_trns_hstr | 28 | 28 | — | — | 5 | — | uk01 missing in local (11-col UNIQUE) |
| acct_trns_rcv_pay_evid_file | 18 | 13 | — | 5 (file_seq_no, file_nm, file_path, mod_id, mod_dtm) | 2 | — | (none) |
| acct_trns_rcv_pay_infm | 38 | 37 | — | 1 (doc_id) | 5 | 1 | DEV fk02 → cash_repy_mstr DEFERRED |
| bank_infm | 8 | 8 | — | — | — | — | (clean) |
| bat_call_his | 12 | 12 | — | — | 3 | 1 | (none) |
| busa_doc_mstr | 100 | 100 | — | — | 6 | 3 | DEV fk04 → mrec_pic_store DEFERRED |
| busa_doc_rcv_pay_his | 18 | 14 | — | 4 (seq_no, acct_seq_no, rcv_pay_dt, amt) | 2 | — | (none) |
| busi_admn_infm | 7 | 7 | — | — | 5 | 2 | (none) |
| busi_cond_daily_sumr | 12 | 12 | — | — | 5 | 1 | (none) |
| busi_cond_daily_sumr_detl | 16 | 16 | — | — | 4 | 1 | **PK column order differs** |
| busi_infm | 45 | 45 | — | — | 28 | 9 | **uk01 missing**, **chk01 missing**, fk01/fk02 deferred |
| einv_fee_detl | 9 | 9 | — | — | 2 | — | (none) |
| einv_tax_detl | 10 | 10 | — | — | 3 | — | (none) |
| empl_infm | 43 | 43 | **1 (`_empl_nm`)** | 1 (`empl_nm`) | 11 | 3 | (none) |
| ext_tran_call_his | 14 | 14 | — | — | 1 | — | (none) |
| napas_remit_trns_hstr | 21 | 21 | — | — | 6 | — | (none) |
| oprt_stts_info | 11 | 11 | — | — | — | 1 | (clean) |
| sell_his_daily_sumr | 15 | 15 | — | — | 5 | 1 | (none) |
| sell_his_daily_sumr_detl | 18 | 18 | — | — | 7 | 2 | (none) |
| user_busi_conn_infm | 11 | 11 | — | — | 8 | 3 | (none) |
| user_infm | 20 | 20 | — | — | 9 | 5 | (none) |
| user_login_his | 6 | 7 | **1 (`reg_uid`)** | — | 3 | 2 | **PK is missing reg_uid** |

---

## 3. Type-Mismatch Categorization

The 116 type-mismatches fall into 4 categories. The first two are **intentional divergences** documented elsewhere (V18/V19 normalization, Java modernization). The last two are **real parity gaps** to fix.

### 3a. CHAR(N) → VARCHAR(N) — INTENTIONAL (do NOT revert)

Local converted DEV's `character(N)` (CHAR padded) to `character varying(N)` so Hibernate `validate` accepts the columns without `@Type` hacks. V18/V19 is the precedent. Affected fields include `acct_type_cd`, `ccy_cd`, `inout_dv`, `doc_type`, `actv_yn`, `gender_cd`, etc. (~50 occurrences across ~15 tables.)

### 3b. timestamp/date ↔ varchar(14)/varchar(8) — INTENTIONAL (do NOT revert)

Legacy DEV stores datetimes as `VARCHAR(14)` (YYYYMMDDHHMMSS) and dates as `VARCHAR(8)` (YYYYMMDD). Local refactor stores them as `TIMESTAMPTZ` / `DATE` so Spring/Java code uses native types. Affected fields: `reg_dtm`, `mod_dtm`, `lst_lgn_date`, `mob_lst_lgn_date`, `birth_date`, `join_dt`, `fst_use_date`, `lst_pay_date`, `use_date_from/to`, `free_use_date_from/to`, `paid_start_date`, `canc_date`. (~30 occurrences.)

### 3c. NUMERIC (no precision) → NUMERIC(N,M) — REAL GAP, FIX

Local `numeric` (no precision) should be `numeric(20,6)` for amounts and `numeric(N)` for sequence numbers per DEV. Specific instances:

| Table.Column | Local | DEV |
|---|---|---|
| acct_mstr.bal | numeric | numeric(20,6) |
| acct_mstr.real_amt | numeric | numeric(20,6) |
| acct_trns_hstr.trsc_amt | numeric | numeric(20,6) |
| acct_trns_hstr.bal | numeric | numeric(20,6) |
| acct_trns_rcv_pay_infm.rcv_pay_amt | numeric | numeric(20,6) |
| bat_call_his.tot_cnt | numeric | numeric(15) |
| bat_call_his.suc_cnt | numeric | numeric(15) |
| bat_call_his.err_cnt | numeric | numeric(15) |
| busa_doc_mstr.totl_amt | numeric | numeric(20,6) |
| busa_doc_mstr.sply_amt_sum | numeric | numeric(20,6) |
| busa_doc_mstr.tax_amt_sum | numeric | numeric(20,6) |
| busi_cond_daily_sumr.sell_amt | numeric NN | numeric(20,6) NN |
| busi_cond_daily_sumr.buy_amt | numeric NN | numeric(20,6) NN |
| busi_cond_daily_sumr.rcv_amt | numeric NN | numeric(20,6) NN |
| busi_cond_daily_sumr.snd_amt | numeric NN | numeric(20,6) NN |
| busi_cond_daily_sumr_detl.dtl_seq_no | numeric NN | numeric(5) NN |
| busi_cond_daily_sumr_detl.trsc_amt | numeric NN | numeric(20,6) NN |
| einv_fee_detl.disp_ord | numeric NN | numeric(10) NN |
| einv_fee_detl.einv_fee | numeric | numeric(20,6) |
| einv_tax_detl.disp_ord | numeric NN | numeric(10) NN |
| einv_tax_detl.amt | numeric | numeric(20,6) |
| einv_tax_detl.tax_amt | numeric | numeric(20,6) |
| ext_tran_call_his.seq_no | numeric NN | numeric(10) NN |
| napas_remit_trns_hstr.seq_no | numeric NN | numeric(10) NN |
| napas_remit_trns_hstr.trsc_amt | numeric NN | numeric(20,6) NN |
| napas_remit_trns_hstr.fee | numeric | numeric(20,6) |
| sell_his_daily_sumr.{sply_amt_sum, tax_amt_sum, sell_amt_sum, rcv_amt_sum} | numeric NN | numeric(20,6) NN |
| sell_his_daily_sumr_detl.{item_qunt, item_unit_amt, sply_amt, tax_amt, sell_amt, rcv_amt} | numeric NN | numeric(20,6) NN |

**Action:** widen-to-precision via `ALTER TABLE … ALTER COLUMN … TYPE NUMERIC(N,M)`. Safe — all current data fits.

### 3d. VARCHAR length mismatches — REAL GAP, FIX

| Table.Column | Local | DEV | Action |
|---|---|---|---|
| acct_trns_rcv_pay_evid_file.rcv_pay_seq_no | varchar(20) NN | numeric(5) NN | type mismatch — local widened during V21 align; KEEP varchar (FK target type) |
| acct_trns_rcv_pay_evid_file.file_size | numeric | varchar(100) | KEEP local numeric (more useful for filtering) |
| acct_trns_rcv_pay_infm.rcv_pay_seq_no | varchar(20) NN | numeric(5) NN | KEEP varchar (V21 align) |
| acct_trns_rcv_pay_infm.rcv_pay_trco_nm | varchar(200) | varchar(250) | **WIDEN to 250** |
| acct_trns_rcv_pay_infm.cnfm_by_user_id | varchar(100) | varchar(20) | KEEP local wider (matches user_id width) |
| busa_doc_mstr.einv_invoicesymbol | varchar(50) | varchar(10) | KEEP local wider (V17 widened for static UI) |
| busi_admn_infm.{user_id, reg_id, mod_id} | varchar(50) NN | varchar(100) NN | **WIDEN to 100** |
| busi_infm.* (many varchar columns matching length, only NN differs) | various | various NN | NN tightening risky — see §3e |
| empl_infm.{reg_id, mod_id} | varchar(50) | varchar(100) | **WIDEN to 100** |
| user_busi_conn_infm.user_id | varchar(50) NN | varchar(100) NN | **WIDEN to 100** |
| user_busi_conn_infm.empl_no | varchar(30) | varchar(20) | KEEP local wider |
| user_busi_conn_infm.{reg_id, mod_id} | varchar(50) | varchar(100) NN | **WIDEN to 100** |
| user_infm.user_id | varchar(50) NN | varchar(100) NN | **WIDEN to 100** |
| user_infm.eml | varchar(200) | varchar(100) | KEEP local wider |
| user_infm.{reg_id, mod_id} | varchar(50) | varchar(100) NN | **WIDEN to 100** |
| user_login_his.user_id | varchar(50) NN | varchar(100) NN | **WIDEN to 100** |

### 3e. NOT NULL tightening — DEFERRED (risk)

DEV has NN on many columns where local doesn't. Adding NN requires confirming no existing row has NULL there. For Sprint 1 read-only scope this is low-priority. Adding NN now would risk breaking existing seed rows. **DEFERRED to Sprint 2 audit when write features land.**

---

## 4. Constraint Gaps

### 4a. Missing UNIQUE constraints in local

| Constraint | Definition | Action |
|---|---|---|
| `acct_trns_hstr_uk01` | UNIQUE (biz_key, bank_cd, acct_no, trsc_dt, trsc_tm, ccy_cd, inout_dv, trsc_amt, bal, rmrk, rmrk2) | **ADD** (passes on 40 local rows — verified) |
| `busi_infm_uk01` | UNIQUE (chnl_id, tax_id, ntnl_cd) | **ADD** (single tenant row — passes) |

### 4b. Missing CHECK constraints in local

| Constraint | Definition | Action |
|---|---|---|
| `busi_infm_chk01` | CHECK (prdt_type ∈ ['W','R','B','E','L','S','P','T','U','C','D','F','M','H']) | **ADD** after verifying local row passes |

### 4c. Missing FOREIGN KEYs — DEFERRED (target table not migrated)

| Constraint | Target | Status |
|---|---|---|
| `acct_trns_rcv_pay_infm_fk02` | cash_repy_mstr | DEFERRED — target not migrated locally (V24 documented) |
| `busa_doc_mstr_fk04` | mrec_pic_store | DEFERRED — target not migrated locally (V24 documented) |
| `busi_infm_fk01` | natn_infm(ntnl_cd) | DEFERRED — target not migrated |
| `busi_infm_fk02` | chnl_infm(chnl_id) | DEFERRED — target not migrated |

### 4d. PK realignment

| Table | Local PK | DEV PK | Action |
|---|---|---|---|
| `busi_cond_daily_sumr_detl` | (biz_key, trco_key, trsc_dt, ccy_cd, prjt_no, dtl_seq_no) | (biz_key, trco_key, trsc_dt, ccy_cd, dtl_seq_no, prjt_no) | **REORDER** (drop+recreate; 3 local rows; safe) |
| `user_login_his` | (user_id, reg_dtm) | (user_id, reg_dtm, reg_uid) | **REBUILD after adding `reg_uid` column** |

### 4e. Local-only constraints (KEEP — safety, not in DEV)

- `user_busi_conn_infm.ck_ubci_del`, `fk_ubci_busi`, `fk_ubci_user`
- `user_infm.ck_user_infm_actv`, `ck_user_infm_del`, `ck_user_infm_lock`
- `user_login_his.ck_user_login_his_chnl`, `ck_user_login_his_rslt`
- `acct_mstr_chk*` already aligned
- `*_fk01` FKs added by V24

---

## 5. Trigger Parity

DEV has 15 triggers across 13 in-scope tables; local has **0**. All DEV triggers depend on out-of-scope objects:

- `*_logtrigger` (audit logging) — calls `*_log()` functions and writes to `*_log_h` history tables (none migrated)
- `acct_mstr_before_insert_or_update` — calls `reject_not_existing_bank_cd()` (not migrated)
- `sync_to_rept_trigger` (on acct_trns_hstr) — calls `sync_acct_trns_hstr_to_rept()` (not migrated; target `rept_trns_hstr` not migrated)

**Action: DEFERRED** to the sprint that ports the underlying audit-log infrastructure. CLAUDE.md V24 already documents this. No change in this audit.

---

## 6. Local-Only Columns (DEV does NOT have) — classification

Per rule 4: not auto-deleted. Classification:

| Table | Local-only column | Classification | Recommendation |
|---|---|---|---|
| `acct_trns_rcv_pay_evid_file` | `file_seq_no` | (b) Local convenience surrogate seq introduced by V13 | KEEP for now, deprecate when DEV `file_id` becomes the canonical identifier in Spring code |
| `acct_trns_rcv_pay_evid_file` | `file_nm` | (a) Refactor-only — display filename | KEEP |
| `acct_trns_rcv_pay_evid_file` | `file_path` | (a) Refactor-only — local storage path | KEEP |
| `acct_trns_rcv_pay_evid_file` | `mod_id`, `mod_dtm` | BaseEntity audit columns from V13 | KEEP — audit fields |
| `acct_trns_rcv_pay_infm` | `doc_id` | (a) Refactor-only convenience pointer to BUSA_DOC_MSTR | KEEP — used by Spring code? to verify |
| `busa_doc_rcv_pay_his` | `seq_no` | (b) Local surrogate from V13 (DEV uses rcv_pay_his_seq_no as PK part) | KEEP for now |
| `busa_doc_rcv_pay_his` | `acct_seq_no`, `rcv_pay_dt`, `amt` | (b) Convenience local copy of fields from joined tables | KEEP — denormalized for Sprint 1 reads |
| `empl_infm` | `empl_nm` | DEV uses `_empl_nm` (with leading underscore) — same field, different casing convention | KEEP local; ALSO add DEV's `_empl_nm` as nullable shadow column |

---

## 7. DEV-Only Tables Required by Current Scope

**None.** Sprint 1 endpoints read only the 23 migrated tables. No FK target needs adding now. The 4 deferred FKs in §4c document the future migration candidates:

- `cash_repy_mstr` — needed when Sprint 2 cash module lands
- `mrec_pic_store` — needed when Sprint 2 picture/upload registry lands
- `natn_infm` — country reference; nice-to-have
- `chnl_infm` — channel reference; nice-to-have

---

## 8. Seed Data Audit

| Table | Local rows | DEV rows (tenant) | Status | Action |
|---|---|---|---|---|
| acct_mstr | 25 | (real DEV — V22) | ✅ real | none |
| acct_trns_hstr | 40 | (real DEV — V22) | ✅ real | none |
| acct_trns_rcv_pay_evid_file | 0 | 0 | ✅ both empty | none |
| acct_trns_rcv_pay_infm | 15 | (real DEV — V22) | ✅ real | none |
| bank_infm | 29 | (real DEV — V20) | ✅ real | none |
| bat_call_his | 0 | **84** | ⚠️ local empty | **IMPORT** (small) |
| busa_doc_mstr | 50 | (real DEV — V22) | ✅ real | none |
| busa_doc_rcv_pay_his | 0 | **26** | ⚠️ local empty | **IMPORT** (small) |
| busi_admn_infm | 7 | (Sprint 0) | ✅ stub | none |
| busi_cond_daily_sumr | 3 | 0 | local has stubs, DEV empty | KEEP stubs (V17 demo) |
| busi_cond_daily_sumr_detl | 3 | 0 | local has stubs, DEV empty | KEEP stubs |
| busi_infm | 1 | 1 (single tenant) | ✅ real | none |
| einv_fee_detl | 0 | **16** | ⚠️ local empty | **IMPORT** (small) |
| einv_tax_detl | 0 | **669** | ⚠️ local empty | **IMPORT** (manageable) |
| empl_infm | 1 | (Sprint 0 stub) | stub | none |
| ext_tran_call_his | 0 | 23,995 | DEV has lots; not tenant-scoped | **IMPORT latest 100 only** |
| napas_remit_trns_hstr | 25 | (real DEV — V22) | ✅ real | none |
| oprt_stts_info | 25 | (real DEV — V22) | ✅ real | none |
| sell_his_daily_sumr | 0 | 0 | ✅ both empty | none |
| sell_his_daily_sumr_detl | 0 | 0 | ✅ both empty | none |
| user_busi_conn_infm | 99 | (real DEV — V5) | ✅ real | none |
| user_infm | 99 | (real DEV — V5) | ✅ real | none |
| user_login_his | 134 | (test data) | Sprint 0 | none |

---

## 9. wb_get_next_val Comparison

**Local (after V27):** 13 branches:
- Sprint 0: `busi_infm.biz_key`, `empl_infm.empl_no`
- Sprint 1: `acct_mstr.prn_seq`, `acct_trns_hstr.acct_seq_no`, `acct_trns_rcv_pay_infm.rcv_pay_seq_no`, `acct_trns_rcv_pay_evid_file.file_id`, `busa_doc_rcv_pay_his.rcv_pay_his_seq_no`, `busa_doc_mstr.doc_id`, `busi_cond_daily_sumr_detl.dtl_seq_no`, `einv_fee_detl.disp_ord`, `einv_tax_detl.disp_ord`, `napas_remit_trns_hstr.seq_no`, `ext_tran_call_his.seq_no`

**DEV:** 166 total branches (audit confirmed 11 match Sprint 1 migrated tables).

**Sprint 1 tables with no DEV branch (6):** `bank_infm`, `busi_cond_daily_sumr`, `sell_his_daily_sumr`, `sell_his_daily_sumr_detl`, `bat_call_his`, `oprt_stts_info` — DEV does not generate keys for these (batch-inserted or external-sourced).

**Verdict:** ✅ Local matches the rule exactly. No change needed.

**Read-only Sprint 1 endpoints do NOT call `wb_get_next_val`.** Confirmed by grep across `domain/banking`, `domain/einvoice`, `domain/operation`. The function exists for Sprint 2+ create/save flows.

---

## 10. Java-Layer Impact

Per rule 10 (Java-impact rule), entity/DTO/service updates are needed only if (a) mapped column has wrong name/type, (b) API filters/sorts that column, (c) Spring writes that column, (d) Hibernate validation requires it.

**Java changes required by this audit:** **none.**

- The numeric-precision widenings (§3c) don't affect mapped Java types — Hibernate `validate` accepts both unconstrained `numeric` and `numeric(N,M)` against `BigDecimal`.
- VARCHAR widenings (§3d) don't affect Java types — `String` length is unbounded at the Java level.
- Added `_empl_nm` and `reg_uid` columns are NOT mapped to entities in Sprint 1 scope.
- Added UNIQUE/CHECK constraints don't affect entities.
- PK realignment of `busi_cond_daily_sumr_detl` doesn't change the column set, only order — entity `@IdClass` is unaffected.

---

## 11. Proposed Corrective Plan (BEFORE applying)

Two new Flyway migrations, both additive/safe, no destructive changes:

### V28__sprint1_db_parity_audit.sql (schema fixes)

1. **Add columns:**
   - `empl_infm._empl_nm VARCHAR(100)` (DEV-name parity; nullable)
   - `user_login_his.reg_uid VARCHAR(100)` (DEV PK component; nullable initially, backfill later)

2. **Widen NUMERIC types** (§3c) — `numeric` → `numeric(20,6)` for amounts, `numeric(N)` for sequence numbers.

3. **Widen VARCHAR types** (§3d) — `varchar(50)` → `varchar(100)` for user_id/reg_id/mod_id, `varchar(200)` → `varchar(250)` for `rcv_pay_trco_nm`.

4. **Add missing UNIQUE constraints** (§4a) — `acct_trns_hstr_uk01`, `busi_infm_uk01`. Verify pass first.

5. **Add missing CHECK constraint** (§4b) — `busi_infm_chk01` on `prdt_type`. Verify local row passes first (UPDATE if needed).

6. **Realign PK on busi_cond_daily_sumr_detl** (§4d) — drop+recreate with DEV's column order.

7. **Realign PK on user_login_his** — done after step 1; drop+recreate as `(user_id, reg_dtm, reg_uid)`. Pre-fill existing rows' `reg_uid` so the PK can be added (use `reg_uid = user_id` as a safe placeholder — the column will be properly populated by future logins).

### V29__sprint1_real_dev_seed_round2.sql (seed imports)

Real DEV rows for tables where local is empty but DEV has data. Idempotent via `INSERT … ON CONFLICT DO NOTHING`:

- `bat_call_his` — 84 rows (tenant)
- `busa_doc_rcv_pay_his` — 26 rows (tenant)
- `einv_fee_detl` — 16 rows (tenant)
- `einv_tax_detl` — 669 rows (tenant)
- `ext_tran_call_his` — latest 100 rows (table is global-scoped, not tenant)

### Documentation

- Update CLAUDE.md migration table with V28 + V29.
- Update CLAUDE.md "Legacy Schema Parity Rule" with: "Run a full local-vs-DEV parity audit at the end of each sprint; ship corrective additive migrations as found."

---

## 12. Skipped (intentional, documented)

- CHAR ↔ VARCHAR (V18/V19 deliberate)
- TIMESTAMPTZ/DATE ↔ VARCHAR(14)/(8) (Java modernization)
- Adding NOT NULL to columns that are currently nullable (risk of breaking existing rows; Sprint 2 follow-up)
- Dropping local-only convenience columns (rule 4)
- Adding deferred FKs to non-migrated targets (V24 already documents)
- Adding triggers depending on out-of-scope log/sync functions (V24 already documents)

---

## 13. Remaining Risks / TODOs

1. **NN gaps** — many DEV-NOT-NULL columns are nullable locally (especially in `busi_infm`, `user_*`). Tighten in Sprint 2 when write paths exist.
2. **Local-only convenience columns** — review during Sprint 2 to confirm whether the Java code still needs them; deprecate if not.
3. **DEV-only triggers** — port when Sprint 2/3 ships the audit-log infrastructure.
4. **`empl_infm._empl_nm` vs `empl_nm`** — DEV name has a leading underscore which is unusual; coexist for now, but pick one canonical name when employee management features are built.
5. **`ext_tran_call_his`** — only top-100 sampled; full DEV history is 24K rows. Re-sample if Sprint 2 needs broader data.

---

**Audit complete. Proceeding to apply V28 + V29 (additive only) per Auto Mode and rule "apply only safe corrective migrations".**

---

## 14. Post-Apply Hotfix — `UserLoginHis` entity must map `reg_uid`

**Issue surfaced after V28 applied.** A login attempt failed at runtime with:

```
ERROR: null value in column "reg_uid" violates not-null constraint
INSERT INTO user_login_his (ip_addr, lgn_chnl, rslt_cd, rslt_cont, reg_dtm, user_id) VALUES (...)
```

Root cause: V28 made `reg_uid` part of the PK (NOT NULL), but the JPA entity
`UserLoginHis` did not map this column. Hibernate omitted it from the INSERT,
PostgreSQL rejected the row.

**This is exactly the case rule (c) from §10 calls out**: "Spring inserts/updates
that column" → entity must include it. The pre-apply Java-impact analysis missed
this one because it focused on read paths (Sprint 1 endpoints are read-only) and
overlooked the cross-cutting login-history insert that runs on every auth call.

**Fix (committed):**

1. [UserLoginHis.java](../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserLoginHis.java) — added `@Id @Column(name="REG_UID") String regUid`; widened `userId` length to 100 to match V28's VARCHAR widening.
2. [UserLoginHisId.java](../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserLoginHisId.java) — added `regUid` to the composite key.
3. [LoginHistoryService.java](../../../src/main/java/com/kosign/wecms/domain/auth/service/LoginHistoryService.java) — generate `UUID.randomUUID().toString()` for `regUid` on every record.

**Verified:** clean rebuild + tests pass; `bootRun` starts cleanly; login endpoint
returns proper `WBE1009` business error (not `WBE9999` server error) for invalid
credentials, with the FAIL audit row written with a UUID `reg_uid`. SUCCESS rows
also write with UUID `reg_uid`. The 134 pre-existing rows from V28 backfill keep
their user_id-as-placeholder `reg_uid` (benign — they're test data).

**Lesson for the audit checklist:** when a sprint-final audit adds a new NOT NULL
column to a table that any service inserts into (even auth/audit-log tables that
aren't part of the current sprint scope), the entity update must ship in the same
MR as the migration. Future audits should grep for `repository.save(...)` and
`@Transactional` writes against any audited table before declaring the schema fix
"safe to ship without entity changes".
