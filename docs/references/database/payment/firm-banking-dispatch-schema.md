# Firm-Banking Dispatch — Refactor Database Design

| Field | Value |
|---|---|
| Slug | `firm-banking-dispatch` (umbrella; covers TB_FIRM_FILE_*, TB_FIRM_BAT_API_TRAN, TB_BATCH_SCHEDULE_*) |
| Module | `payment` |
| Companion docs | [internal-transfer-schema.md](internal-transfer-schema.md) (dispatch path peer) · `wecms-api/CLAUDE.md` §"PROD Active Table List" |
| DDL script | `firm_banking_dispatch_remaining_tables_20260518.sql` (Needs Confirmation — source file not found) — run manually against the local refactor DB. |
| Reconciled against DEV | 2026-05-18 via `postgres_dev_server_jexframe` MCP `information_schema.columns` + `pg_indexes` |

> Schema-only port. No service / repository / entity / DTO is wired in this MR — V176 is a pure DDL pre-step so the Internal Transfer Phase B dispatch path (and the Vendor / Expense / Payroll modules that follow per `project_payment_module_reference.md`) can build entities + repositories against a parity-checked schema.

---

## 1. Scope

The image from PO Mr. Jung (2026-05-18) lists 14 tables/sequences from the legacy JEX framework that the refactor's payment dispatch path needs. Six already exist in the local refactor DB from earlier work; six are newly created by this script.

| Item | Type | Status |
|---|---|---|
| `TB_FIRM_BANK_CODE` | table | already present locally |
| `TB_FIRM_CONF` | table | already present locally |
| `TB_PAY_TARGET` | table | already present locally |
| `TB_FIRM_TRAN` | table | already present locally |
| `TB_PAY_FILE_MST` | table | already present locally |
| `TB_PAY_FILE_DATA` | table | already present locally |
| `TB_FIRM_FILE_MST` | table | **created by the DDL script above** |
| `TB_FIRM_FILE_DATA` | table | **created by the DDL script above** |
| `TB_FIRM_FILE_RSLT` | table | **created by the DDL script above** |
| `TB_FIRM_BAT_API_TRAN` | table | **created by the DDL script above** |
| `TB_BATCH_SCHEDULE_INFO` | table | **created by the DDL script above** |
| `TB_BATCH_SCHEDULE_DTL` | table | **created by the DDL script above** |
| `firm_seq_no` | sequence | already present locally |
| `firm_file_seq_no` | sequence | already present locally |

All 12 tables are in the PROD Active Table List (`wecms-api/CLAUDE.md`); the two sequences back the legacy `wb_get_next_val` paths for firm-banking key generation.

---

## 2. DEV parity verification

DEV column metadata was pulled on 2026-05-18:

```sql
SELECT table_name, column_name, data_type, character_maximum_length,
       numeric_precision, numeric_scale, is_nullable, column_default,
       ordinal_position
FROM information_schema.columns
WHERE table_schema='wecmsvndev'
  AND table_name IN ('tb_firm_file_mst','tb_firm_file_data','tb_firm_file_rslt',
                     'tb_firm_bat_api_tran','tb_batch_schedule_info',
                     'tb_batch_schedule_dtl')
ORDER BY table_name, ordinal_position;
```

DEV constraint metadata (corrected query 2026-05-18, using `conrelid IN (...regclass...)`):

```sql
SELECT c.conrelid::regclass::text AS table_name, c.conname, c.contype,
       pg_get_constraintdef(c.oid) AS def
FROM pg_constraint c
WHERE c.conrelid IN ('wecmsvndev.tb_firm_file_mst'::regclass, …);
-- → 12 rows, one PRIMARY KEY (contype='p') per table named pk_<table>.
--   No FOREIGN KEY, no UNIQUE beyond the PK, no CHECK on any of the
--   12 tables.
```

**Interpretation.** DEV declares each PK as a formal `PRIMARY KEY` constraint, named `pk_<table>`. The DDL script's `CONSTRAINT pk_<table> PRIMARY KEY (...)` matches DEV byte-for-byte (name + column order). Referential integrity between the 12 tables is **app-enforced** — there are no FK constraints on the DEV side at all. (An earlier version of this doc claimed DEV had only unique indexes; that was based on a malformed `pg_constraint` query and has been corrected here.)

---

## 3. Column-by-column intent

### 3.1 TB_FIRM_FILE_MST (26 cols)

Firm-banking dispatch file header — one row per dispatched batch.

| # | Column | DEV type | NN | Default | Notes |
|---:|---|---|---|---|---|
| 1 | TRAN_DT | varchar(8) | Y | — | Vietnam-local date YYYYMMDD |
| 2 | BIZ_KEY | varchar(20) | Y | — | tenant |
| 3 | FILE_SEQ_NO | varchar(7) | Y | — | zero-padded, sourced from `firm_file_seq_no` |
| 4 | OUT_BANK_CD | varchar(15) | N | — | outbound bank |
| 5 | BANK_PRTN_ID | varchar(20) | N | — | bank partner id |
| 6 | VAN_ORG_CD | varchar(20) | N | — | VAN organisation |
| 7 | FILE_TYPE | char(3) | N | — | file type code |
| 8 | WDRL_ACT_NO | varchar(100) | N | — | withdrawal account |
| 9 | FUND_TP | char(1) | N | — | fund type |
| 10 | TRAN_TP | char(1) | N | — | transaction type |
| 11 | FILE_SEQ | varchar(3) | N | — | sub-sequence within batch |
| 12 | FIRM_FILE_NM | varchar(50) | Y | — | physical filename |
| 13 | PROC_STEP | char(1) | N | — | processing step |
| 14 | PROC_STAT | char(1) | Y | — | processing status |
| 15 | REQ_CNT | numeric(7,0) | N | — | requested count |
| 16 | REQ_AMT | numeric(23,2) | N | — | requested amount |
| 17 | REG_CNT | numeric(7,0) | N | — | registered count |
| 18 | REG_AMT | numeric(23,2) | N | — | registered amount |
| 19 | REG_RSLT_CD | varchar(4) | N | — | registration result code |
| 20 | REG_RSLT_MSG | varchar(150) | N | — | registration result message |
| 21 | SUCC_CNT | numeric(7,0) | N | — | success count |
| 22 | SUCC_AMT | numeric(23,2) | N | — | success amount |
| 23 | UNPROC_CNT | numeric(7,0) | N | — | unprocessed count |
| 24 | UNPROC_AMT | numeric(23,2) | N | — | unprocessed amount |
| 25 | REG_DTTM | varchar(14) | N | — | registration timestamp |
| 26 | PROC_DTTM | varchar(14) | N | — | processing timestamp |

PK: `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM)` (matches DEV `pk_tb_firm_file_mst`).

### 3.2 TB_FIRM_FILE_DATA (24 cols)

Firm-banking dispatch detail lines — one row per beneficiary under a TB_FIRM_FILE_MST batch. PK preserves DEV's *omission* of BIZ_KEY: `(TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)`. BIZ_KEY column itself stays NOT NULL for the join paths.

Defaults verbatim from DEV:
- `TRSC_CCY_CD CHAR(3) NOT NULL DEFAULT 'VND'`
- `PROC_RSLT_DIV CHAR(1) DEFAULT 'N'`

### 3.3 TB_FIRM_FILE_RSLT (22 cols)

Gateway response rows mirroring the corresponding TB_FIRM_FILE_DATA rows after dispatch.

PK: `(TRAN_DT, BIZ_KEY, FIRM_FILE_NM, SEQ)`.

Defaults verbatim from DEV:
- `TRSC_CCY_CD CHAR(3) NOT NULL DEFAULT 'VND'`
- `PROC_RSLT_DIV CHAR(1) NOT NULL DEFAULT 'N'`

### 3.4 TB_FIRM_BAT_API_TRAN (15 cols)

Batch-level API transaction audit — one row per dispatch attempt (registration request + aggregate counts/amounts + response).

PK: `(TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)` — six-column composite, matches DEV.

### 3.5 TB_BATCH_SCHEDULE_INFO (8 cols)

Per-bank batch schedule header. Drives the firm-banking dispatcher's run windows. **No BIZ_KEY** — DEV stores schedules globally per `(BATCH_ID, OUT_BANK_CD)`. Preserved verbatim.

| # | Column | DEV type | NN | Default |
|---:|---|---|---|---|
| 1 | BATCH_ID | varchar(50) | Y | — |
| 2 | OUT_BANK_CD | varchar(15) | Y | — |
| 3 | ANT_WHERE_USE_YN | char(1) | N | `'N'` |
| 4 | BATCH_START_TM | char(6) | Y | — |
| 5 | BATCH_END_TM | char(6) | Y | — |
| 6 | REG_DTTM | varchar(14) | N | — |
| 7 | MOD_DTTM | varchar(14) | N | — |
| 8 | LAST_EDTR | varchar(20) | N | — |

PK: `(BATCH_ID, OUT_BANK_CD)`.

### 3.6 TB_BATCH_SCHEDULE_DTL (7 cols)

Per-bank, per-account-class detail of a parent schedule.

PK: `(BATCH_ID, OUT_BANK_CD, ANT_DIV_CD)`.

---

## 4. Indexes beyond DEV's pk_*

None added in V176. The future entities will use the PK columns for tenant-scoped lookups (`BIZ_KEY` is the first or second component everywhere except TB_BATCH_SCHEDULE_* which is global). Performance indexes will be added in the MR that wires the read path, once a real query plan exists to justify them.

## 5. Deferred items

- **Foreign keys to BUSI_INFM (BIZ_KEY)** — DEV has none on these tables. Not added locally to match DEV. Application code will enforce tenant scoping.
- **Audit-history twins (`*_HIS`)** — legacy DEV uses BEFORE/AFTER triggers to populate per-table history tables (e.g. `TB_PAY_TARGET_HIS`). Refactor uses `AUDIT_LOGS_INFM` instead, so the trigger DDL is intentionally not ported. Three `*_HIS` peers (`TB_PAY_TARGET_HIS`, `TB_PAY_FILE_MST_HIS`, `TB_PAY_FILE_DATA_HIS`) are flagged in the V148 header as deferred-schema-parity-only; the same stance applies if a future MR needs `TB_FIRM_FILE_*_HIS`.
- **Demo seed rows** — none in V176. Seed migrations follow once entities + repositories land and the dispatch service has a stable shape. Real DEV rows (capped at 30 per the Data Migration Rule) will be sampled by `REG_DTTM DESC` for tenant `VN_VNM0004_000001`.
- **JPA entities + repositories** — out of scope for V176. The Internal Transfer Phase B dispatch MR will ship them alongside `TransferDispatchExecutor` per memory `project_payment_module_reference.md`.

## 6. Parity checklist

- [x] Queried DEV `information_schema.columns` for every table touched
- [x] Script column count == DEV column count (TB_FIRM_FILE_MST 26 / *_DATA 24 / *_RSLT 22 / *_BAT_API_TRAN 15 / TB_BATCH_SCHEDULE_INFO 8 / *_DTL 7)
- [x] Every legacy NOT-NULL column has the legacy DEFAULT or is supplied by the write path
- [x] Legacy DEFAULTs preserved verbatim (`TRSC_CCY_CD = 'VND'`, `PROC_RSLT_DIV = 'N'`, `ANT_WHERE_USE_YN = 'N'`)
- [x] PK column set + order matches DEV `pk_<table>` unique-index order
- [x] UNIQUE / CHECK / FK from DEV — none present on DEV; none added locally
- [x] BIZ_KEY columns typed `VARCHAR(20)` (not 32)
- [x] Triggers — none ported (refactor uses AUDIT_LOGS_INFM, documented above)
- [x] `firm_seq_no` and `firm_file_seq_no` sequences already exist locally; no new branches needed.

## 7. Reference

Legacy entities (read-only):
- `WECMS DEV/WECMS/src/wabooks/entity/TbFirmFileMst.java`
- `WECMS DEV/WECMS/src/wabooks/entity/TbFirmFileData.java`
- `WECMS DEV/WECMS/src/wabooks/entity/TbFirmFileRslt.java`
- `WECMS DEV/WECMS/src/wabooks/entity/TbFirmBatApiTran.java`

Earlier DDL that already created the sibling tables/sequences (kept for traceability — these were originally Flyway migrations from before the team moved off Flyway):
- `firm_seq_no` + `firm_file_seq_no` sequences
- TB_FIRM_TRAN, TB_FIRM_BANK_CODE, NXSIAS_MODULE_TRAN_LOG_HIS
- TB_FIRM_CONF
- TB_PAY_TARGET, TB_PAY_FILE_MST, TB_PAY_FILE_DATA
- SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL (read/write peer for the dispatch path)
