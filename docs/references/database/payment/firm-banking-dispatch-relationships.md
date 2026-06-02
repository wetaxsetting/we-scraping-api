# Firm-Banking Dispatch — Table Relationship Map

| Field | Value |
|---|---|
| Scope | The 12 tables + 2 sequences from the PO's inventory image (2026-05-18) |
| Source of truth | DEV `wecmsvndev` — verified 2026-05-18 via `postgres_dev` MCP |
| Companion docs | [firm-banking-dispatch-schema.md](firm-banking-dispatch-schema.md) (DDL for the 6 newly-created tables) · [internal-transfer-schema.md](internal-transfer-schema.md) (dispatch's master/detail peer) |

This doc maps how the 12 tables relate to each other. **DEV has zero FK constraints across these tables** — every relationship below is an *implicit* join discovered by matching key columns. Referential integrity is enforced entirely in application code.

---

## 1. The 12 tables at a glance

Verified against DEV 2026-05-18:

| Table | Cols | PK columns | DEV row count | Role |
|---|---:|---|---:|---|
| `TB_FIRM_BANK_CODE`        | 14 | `(ANT_DIV_CD, BANK_CD)` | 3,205 | Global bank registry |
| `TB_FIRM_CONF`             | 14 | `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)` | 14 | Per-tenant firm-banking channel binding |
| `TB_BATCH_SCHEDULE_INFO`   | 8  | `(BATCH_ID, OUT_BANK_CD)` | 4 | Per-bank batch schedule (global) |
| `TB_BATCH_SCHEDULE_DTL`    | 7  | `(BATCH_ID, OUT_BANK_CD, ANT_DIV_CD)` | 12 | Per-(account-class) schedule detail |
| `TB_PAY_TARGET`            | 23 | `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` | 93 | API-path dispatch request (refactor-side) |
| `TB_PAY_FILE_MST`          | 14 | `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO)` | 130 | File-path dispatch header (refactor-side) |
| `TB_PAY_FILE_DATA`         | 21 | `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO, SEQ)` | 338 | File-path dispatch detail (refactor-side) |
| `TB_FIRM_FILE_MST`         | 26 | `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM)` | 120 | Gateway-side file header |
| `TB_FIRM_FILE_DATA`        | 24 | `(TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)` | 261 | Gateway-side file detail |
| `TB_FIRM_FILE_RSLT`        | 22 | `(TRAN_DT, BIZ_KEY, FIRM_FILE_NM, SEQ)` | 65 | Gateway response (per detail line) |
| `TB_FIRM_BAT_API_TRAN`     | 15 | `(TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)` | 1,304 | Batch-level API call audit |
| `TB_FIRM_TRAN`             | 36 | `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` | 10,097 | Per-line gateway transaction audit |
| `firm_seq_no` (sequence)   | —  | — | — | Allocator for `TRAN_SEQ` (per `TB_PAY_TARGET` / `TB_FIRM_TRAN`) |
| `firm_file_seq_no` (sequence) | — | — | — | Allocator for `FILE_SEQ_NO` (per `TB_PAY_FILE_MST` / `TB_FIRM_FILE_MST`) |

**Constraints DEV does NOT have on any of these 12 tables:** `FOREIGN KEY`, `CHECK`, secondary `UNIQUE`, secondary indexes. Just the 12 named `PRIMARY KEY` constraints.

---

## 2. Functional groupings

```
┌──────────────────────────────────────────────────────────────────┐
│   LOOKUPS (read-mostly)                                          │
│   • TB_FIRM_BANK_CODE  (3205) — bank-code dictionary, global     │
│   • TB_FIRM_CONF       (14)   — outbound channel per tenant      │
├──────────────────────────────────────────────────────────────────┤
│   PER-BANK SCHEDULE (when can dispatch run)                      │
│   • TB_BATCH_SCHEDULE_INFO  ←─ TB_BATCH_SCHEDULE_DTL             │
├──────────────────────────────────────────────────────────────────┤
│   DISPATCH PATH                                                  │
│   ┌── API/VND ────────────────────────────────────────────┐      │
│   │   TB_PAY_TARGET                                        │      │
│   │      └─ writes to →  TB_FIRM_TRAN  (same PK shape)     │      │
│   │      audit row    →  TB_FIRM_BAT_API_TRAN              │      │
│   └────────────────────────────────────────────────────────┘      │
│   ┌── FILE/non-VND ───────────────────────────────────────┐      │
│   │   TB_PAY_FILE_MST  ←─ TB_PAY_FILE_DATA                 │      │
│   │       ↓ (gateway picks up the file)                    │      │
│   │   TB_FIRM_FILE_MST ←─ TB_FIRM_FILE_DATA                │      │
│   │       ↓ (gateway returns results)                      │      │
│   │   TB_FIRM_FILE_RSLT                                    │      │
│   │   audit row →  TB_FIRM_BAT_API_TRAN                    │      │
│   └────────────────────────────────────────────────────────┘      │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. Implicit relationships (every "FK" the schema doesn't declare)

### 3.1 Master / detail pairs

| Parent | Child | Join key | Notes |
|---|---|---|---|
| `TB_BATCH_SCHEDULE_INFO` | `TB_BATCH_SCHEDULE_DTL` | `(BATCH_ID, OUT_BANK_CD)` | Detail splits parent by `ANT_DIV_CD` |
| `TB_PAY_FILE_MST` | `TB_PAY_FILE_DATA` | `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO)` | Refactor-side file path |
| `TB_FIRM_FILE_MST` | `TB_FIRM_FILE_DATA` | `(TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM)` | Gateway-side file path. **NOTE:** the child PK drops `BIZ_KEY` — the column is still present and NOT NULL but is not part of the join key |
| `TB_FIRM_FILE_DATA` | `TB_FIRM_FILE_RSLT` | `(TRAN_DT, FIRM_FILE_NM, SEQ)` | Detail → response. `BIZ_KEY` re-enters the RSLT PK |

### 3.2 Refactor-side ↔ gateway-side bridges

| Left | Right | Join key | Direction |
|---|---|---|---|
| `TB_PAY_TARGET` | `TB_FIRM_TRAN` | `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` | **1:1 by design** — same PK shape. The API path writes both rows together for a single VND transfer. |
| `TB_PAY_FILE_MST` | `TB_FIRM_FILE_MST` | `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO)` | Refactor-side master inserts first; gateway-side master is created (or matched) when the file is registered with the bank gateway. The `FIRM_FILE_NM` column on the gateway side is set by the gateway, not the dispatcher. |
| `TB_PAY_FILE_DATA` | `TB_FIRM_FILE_DATA` | `(TRAN_DT, FILE_SEQ_NO, SEQ)` | Implicit — same line-number ordering. Note `BIZ_KEY` is NOT NULL on both but only the refactor side puts it in the PK. |

### 3.3 Lookups (every join target the dispatcher needs at runtime)

| Foreign table | Local column(s) | Resolves to |
|---|---|---|
| `TB_FIRM_BANK_CODE` | `(ANT_DIV_CD, BANK_CD)` | Bank registry — name / English abbreviation / branch info. Probed via `OUT_BANK_CD`, `TAR_BANK_CD`, `BANK_CD` (any column carrying a 15-char bank id). |
| `TB_FIRM_CONF` | `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)` | Which firm-banking channel does this tenant use for this outbound bank + account, what file type, what batch size limit. Required before any dispatch row is written. |
| `TB_BATCH_SCHEDULE_INFO`/`_DTL` | `(BATCH_ID, OUT_BANK_CD[, ANT_DIV_CD])` | Whether the current time is inside this bank's dispatch window. Read-only at dispatch time. |

### 3.4 Audit trails

| Audit table | Audits | Join key |
|---|---|---|
| `TB_FIRM_BAT_API_TRAN` | One row per batch-level API call (registration, status check, cancellation) against a `TB_FIRM_FILE_MST` batch *or* a `TB_FIRM_TRAN`-level operation. | `(TRAN_DT, BIZ_KEY, TRAN_SEQ, API_TRAN_TP, OUT_BANK_CD, BANK_PRTN_ID)` — six-column PK. Joins to `TB_FIRM_FILE_MST` via `FIRM_FILE_NM`, joins to `TB_FIRM_TRAN` via `(TRAN_DT, BIZ_KEY, TRAN_SEQ)`. |
| `TB_FIRM_TRAN` | One row per VND-API transfer attempt, end-to-end (request + response). | Naturally aligned with `TB_PAY_TARGET`. |

### 3.5 Sequences

| Sequence | Allocates | Consumed by |
|---|---|---|
| `firm_seq_no` | `TRAN_SEQ` (10-char string) | `TB_PAY_TARGET`, `TB_FIRM_TRAN`, `TB_FIRM_BAT_API_TRAN` (for the API path) |
| `firm_file_seq_no` | `FILE_SEQ_NO` (7-char string) | `TB_PAY_FILE_MST`, `TB_PAY_FILE_DATA`, `TB_FIRM_FILE_MST`, `TB_FIRM_FILE_DATA` |

Sequences are allocated through the legacy `wb_get_next_val` funnel; the legacy function reads from these PostgreSQL sequences and zero-pads the result to the column's char width.

---

## 3a. Payment-module bridge — how SCDUL_PYMN_SUMR connects to the 12 firm-banking tables

The 12 firm-banking tables don't stand alone. They are the **dispatch substrate** for the payment module's batch master tables (`SCDUL_PYMN_SUMR` + `SCDUL_PYMN_SUMR_DETL`). When a user clicks "Transfer" on Internal Transfer / Vendor / Expense / Payroll, the service writes the dispatch rows below and stamps the resulting keys back onto the payment-module rows.

### 3a.1 Payment-module tables involved

Verified against DEV 2026-05-18:

| Table | PK | Sprint state | Role |
|---|---|---|---|
| `SCDUL_PYMN_SUMR` | `(BIZ_KEY, SCDUL_PYMN_NO)` | Already local (V131) | Batch master across all 4 payment modules; discriminated by `SCDUL_PYMN_TYPE_CD` |
| `SCDUL_PYMN_SUMR_DETL` | `(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)` | Already local (V131) | Per-line within a batch |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | (none on DEV) | **Not local yet** | Bridge table — links payment detail rows to API-path dispatch rows |

Note the spelling: `SCDU_PYMN_…` for the conn table (typo preserved verbatim in DEV — do **not** "fix" it to `SCDUL_`).

### 3a.2 SCDUL_PYMN_TYPE_CD — the module discriminator

DEV row counts as of 2026-05-18:

| Code | Rows on DEV | Module |
|---|---:|---|
| `00` | 155 | Legacy/uncategorised (predates the type-coded scheme; do **not** introduce new `00` rows) |
| `01` | 132 | (to verify against `pymn_0010_002` legacy JSP) |
| `02` | 41  | (to verify against `pymn_0003_001` / `pymn_0005_001` / `pymn_0007_001`) |
| `03` | 53  | **Internal Transfer** (confirmed — see memory `project_payment_module_reference`) |

The local `SCDUL_PYMN_SUMR_CHK01` constraint allows `'01'..'06'` (V131 + V146); DEV does not have this CHECK. Map the remaining codes to Vendor / Expense / Payroll as each module is ported.

### 3a.3 Bridge to TB_PAY_TARGET — API/VND path

```
SCDUL_PYMN_SUMR_DETL                 SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN     TB_PAY_TARGET
─────────────────────                 ───────────────────────────────────     ─────────────
biz_key                ───────────►   biz_key                                  
scdul_pymn_no          ───────────►   scdul_pymn_no                            
scdul_pymn_detl_no     ───────────►   scdul_pymn_detl_no                       
                                      tran_dt                ───────────►     tran_dt
                                      tran_seq               ───────────►     tran_seq
                                                                               biz_key
```

The conn table carries both sides of the join — payment detail PK on the left and dispatch (`TRAN_DT, TRAN_SEQ`) on the right. Cardinality is 1:1 in normal operation: one payment detail row produces one TB_PAY_TARGET row when the API dispatch executes.

**Convenience columns on `SCDUL_PYMN_SUMR_DETL` that duplicate the conn-table fields:**

| Column | Type | Purpose |
|---|---|---|
| `PAY_TRAN_DT` | `VARCHAR(8)` | Mirror of the dispatch `TRAN_DT` once an API dispatch fires (lets the detail row resolve the dispatch row without a join through the conn table) |
| `PAY_TRAN_SEQ` | `VARCHAR(10)` | Mirror of the dispatch `TRAN_SEQ` |

These are populated by the service after `TB_PAY_TARGET` is inserted. Pre-dispatch rows have NULL here.

### 3a.4 Bridge to TB_PAY_FILE_MST / TB_PAY_FILE_DATA — file path (non-VND)

**There is no conn table for the file path.** Linkage is encoded directly on the payment-module rows:

```
SCDUL_PYMN_SUMR                                    TB_PAY_FILE_MST
─────────────────                                  ────────────────
biz_key                ─────────────────────────►  biz_key
pay_tran_dt            ─────────────────────────►  tran_dt
pay_file_seq_no        ─────────────────────────►  file_seq_no


SCDUL_PYMN_SUMR_DETL                               TB_PAY_FILE_DATA
─────────────────────                              ─────────────────
biz_key                ─────────────────────────►  biz_key
pay_tran_dt            ─────────────────────────►  tran_dt
pay_file_seq_no        ─────────────────────────►  file_seq_no
pay_seq                ─────────────────────────►  seq
```

So a non-VND batch produces:
- One row in `TB_PAY_FILE_MST` per batch master;
- N rows in `TB_PAY_FILE_DATA` per detail line.

The matching keys live on the payment side as ordinary nullable columns (pre-dispatch the values are NULL; post-dispatch they carry the file/seq allocations).

### 3a.5 Bridge to TB_FIRM_TRAN — gateway-side per-line audit (API path only)

Because `TB_PAY_TARGET` and `TB_FIRM_TRAN` share the `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` PK 1:1, a payment detail row reaches the gateway audit via the same conn table without an extra column:

```
SCDUL_PYMN_SUMR_DETL → conn (tran_dt, tran_seq) → TB_PAY_TARGET → TB_FIRM_TRAN
```

For after-the-fact identification, `SCDUL_PYMN_SUMR` *also* carries `BANK_TRNS_SHORT_ID` (20 chars) and `BANK_TRNS_FULL_ID` (100 chars). These are stamped by the dispatcher when the gateway returns its own transaction id — a redundant denormalised handle so the UI can show "Banking Txn ID" without joining four tables.

### 3a.6 Bridge to TB_FIRM_FILE_MST — gateway-side file header (file path only)

The refactor-side `TB_PAY_FILE_MST` and gateway-side `TB_FIRM_FILE_MST` share `(TRAN_DT, BIZ_KEY, FILE_SEQ_NO)`. The latter adds `FIRM_FILE_NM` (physical filename) to its PK to support multi-step uploads of the same logical batch under different filenames. The chain from payment is therefore:

```
SCDUL_PYMN_SUMR.(pay_tran_dt, biz_key, pay_file_seq_no)
   ─► TB_PAY_FILE_MST                            (refactor-side, header)
       ─► TB_FIRM_FILE_MST (+ firm_file_nm)      (gateway-side, header)
           ─► TB_FIRM_FILE_DATA                  (gateway-side, lines)
               ─► TB_FIRM_FILE_RSLT              (gateway response per line)
```

### 3a.7 Lookups required before any dispatch row is written

When the payment service decides "this batch goes through firm-banking", it must resolve **three lookups** before writing dispatch rows. All three are reads from the 12 firm-banking tables:

1. **`TB_FIRM_CONF`** by `(BIZ_KEY, OUT_BANK_CD, WDRL_ACT_NO)` — confirms a firm-banking channel exists for this tenant + bank + withdrawal account, and reads `FILE_TYPE` / `BANK_PRTN_ID` / `VAN_ORG_CD` / `BAT_TRAN_BRANCH_DIV` / `BAT_TRAN_BRANCH_LIMIT`.
2. **`TB_BATCH_SCHEDULE_INFO`** + **`_DTL`** by `(BATCH_ID, OUT_BANK_CD[, ANT_DIV_CD])` — confirms the current Vietnam-local time is within the bank's accepted window.
3. **`TB_FIRM_BANK_CODE`** by `(ANT_DIV_CD, BANK_CD)` for every distinct `TAR_BANK_CD` in the batch — confirms the destination bank is known to the gateway and resolves its English / NAPAS abbreviation.

If any of these three lookups fail, the dispatch service must NOT write the dispatch rows — instead surface a `BusinessException` to the caller (legacy error codes: `WBE1xxx` series for missing-conf / out-of-window / unknown-bank — to be ported per the migration mapping doc for each payment module).

### 3a.8 What this means for entity wiring in the refactor

For the dispatch service to function end-to-end:

- The 6 new entities under `domain/payment/dispatch/entity/` cover the gateway-side tables.
- `TbPayTarget`, `TbPayFileMst`, `TbPayFileData` (already present) cover the refactor-side dispatch tables.
- The bridge table `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` is now scripted (DDL appended to `firm_banking_dispatch_remaining_tables_20260518.sql` (Needs Confirmation — source file not found) §7) and entity'd as [`ScduPymnSumrDetlPayTargetConn`](../../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/ScduPymnSumrDetlPayTargetConn.java) under `domain/payment/dispatch/entity/`. DEV has a formal PK on `(BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)` (verified 2026-05-18).
- `TbFirmBankCode`, `TbFirmConf`, `TbFirmTran` (already present) handle the lookups.
- `TbBatchScheduleInfo` / `_Dtl` (new) handle the schedule-window guard.

The complete dispatch slice — payment master → refactor-side dispatch → gateway-side dispatch → result → audit — is now schema-covered. The next implementation step is the service layer (`TransferDispatchExecutor` already exists; needs the new entities/repositories wired in).

---

## 4. End-to-end dispatch flows

### 4.1 VND / API path (single transfer)

```
1. service.dispatch(line)
2. INSERT INTO TB_PAY_TARGET   (TRAN_DT, BIZ_KEY, TRAN_SEQ=nextval(firm_seq_no), …)
3. POST to bank API
4. INSERT INTO TB_FIRM_BAT_API_TRAN  (same TRAN_DT/BIZ_KEY/TRAN_SEQ + API_TRAN_TP + OUT_BANK_CD + BANK_PRTN_ID, batch counts)
5. ON response → INSERT INTO TB_FIRM_TRAN  (same TRAN_DT/BIZ_KEY/TRAN_SEQ + response fields, BAL_AMT after debit, RS_CD, PROC_STAT)
6. UPDATE TB_PAY_TARGET SET PROC_STAT = 'C' WHERE …
```

### 4.2 Non-VND / File path (batch upload)

```
1. service.dispatch(batch, lines[N])
2. INSERT INTO TB_PAY_FILE_MST   (TRAN_DT, BIZ_KEY, FILE_SEQ_NO=nextval(firm_file_seq_no), header)
3. INSERT INTO TB_PAY_FILE_DATA  ×N  (same TRAN_DT/BIZ_KEY/FILE_SEQ_NO + SEQ = 1..N + line fields)
4. Build the physical file on disk, store its name in FIRM_FILE_NM
5. INSERT INTO TB_FIRM_FILE_MST  (TRAN_DT, BIZ_KEY, FILE_SEQ_NO, FIRM_FILE_NM + initial PROC_STAT)
6. INSERT INTO TB_FIRM_FILE_DATA ×N  (TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ, BIZ_KEY + line fields)
7. POST file to bank gateway
8. INSERT INTO TB_FIRM_BAT_API_TRAN  (registration audit row)
9. Poll → INSERT INTO TB_FIRM_FILE_RSLT ×N  (per-line response)
10. UPDATE TB_FIRM_FILE_MST SET REG_CNT/SUCC_CNT/UNPROC_CNT etc.
```

---

## 5. Known schema-parity gaps (current local vs DEV)

| Item | Issue | Where to fix |
|---|---|---|
| `TB_FIRM_BANK_CODE` PK | DEV has `(ANT_DIV_CD, BANK_CD)`; the earlier local DDL (V59) declared `(BANK_CD)` only. Single-column PK is a real drift — under DEV, the same `BANK_CD` can recur with different `ANT_DIV_CD`. | Follow-up SQL: `ALTER TABLE TB_FIRM_BANK_CODE DROP CONSTRAINT pk_tb_firm_bank_code, ADD CONSTRAINT pk_tb_firm_bank_code PRIMARY KEY (ANT_DIV_CD, BANK_CD);` — only safe if no duplicate `BANK_CD` rows exist locally. Stage in a separate corrective script. |
| `TB_FIRM_BANK_CODE` row contents | Local V59 seed has only the bank codes referenced by the configured tenant's `TB_FIRM_TRAN` rows; DEV has the full 3,205-row registry. The 1-arg `wb_get_napas_bank_nm` function (V60) compensates by hardcoding two well-known codes. | Optional follow-up seed if a future feature needs the full registry locally. |
| `TB_FIRM_FILE_DATA` PK | Both DEV and the new DDL/entity use `(TRAN_DT, FILE_SEQ_NO, FIRM_FILE_NM, SEQ)` — no `BIZ_KEY` in PK. This is *DEV parity*, not a drift; documented here so future writers don't "fix" it. | — |
| `SCDUL_PYMN_SUMR.SCDUL_PYMN_NO` width | DEV is `VARCHAR(10)`; local V131 declared `VARCHAR(20)`. The new bridge column matches DEV (`VARCHAR(10)`) per the Legacy Schema Parity Rule. | Separate corrective: `ALTER TABLE SCDUL_PYMN_SUMR ALTER COLUMN SCDUL_PYMN_NO TYPE VARCHAR(10) USING SUBSTRING(SCDUL_PYMN_NO FROM 1 FOR 10);` — safe only if every existing row's value ≤ 10 chars. Verify before applying; bundle with the Internal Transfer Phase B MR. |

---

## 6. JPA entity coverage in the refactor

| Table | Entity | Package |
|---|---|---|
| `TB_FIRM_BANK_CODE` | `TbFirmBankCode` | `domain/setting/firmbankinghistory/entity/` |
| `TB_FIRM_CONF` | `TbFirmConf` | `domain/setting/firmbankingsettings/entity/` |
| `TB_FIRM_TRAN` | `TbFirmTran` | `domain/setting/firmbankinghistory/entity/` |
| `TB_PAY_TARGET` | `TbPayTarget` | `domain/payment/dispatch/entity/` |
| `TB_PAY_FILE_MST` | `TbPayFileMst` | `domain/payment/dispatch/entity/` |
| `TB_PAY_FILE_DATA` | `TbPayFileData` | `domain/payment/dispatch/entity/` |
| `TB_FIRM_FILE_MST` | **`TbFirmFileMst`** ← new | `domain/payment/dispatch/entity/` |
| `TB_FIRM_FILE_DATA` | **`TbFirmFileData`** ← new | `domain/payment/dispatch/entity/` |
| `TB_FIRM_FILE_RSLT` | **`TbFirmFileRslt`** ← new | `domain/payment/dispatch/entity/` |
| `TB_FIRM_BAT_API_TRAN` | **`TbFirmBatApiTran`** ← new | `domain/payment/dispatch/entity/` |
| `TB_BATCH_SCHEDULE_INFO` | **`TbBatchScheduleInfo`** ← new | `domain/payment/dispatch/entity/` |
| `TB_BATCH_SCHEDULE_DTL` | **`TbBatchScheduleDtl`** ← new | `domain/payment/dispatch/entity/` |
| `SCDU_PYMN_SUMR_DETL_PAY_TARGET_CONN` | **`ScduPymnSumrDetlPayTargetConn`** ← new | `domain/payment/dispatch/entity/` |

Each new entity carries `@IdClass(<Entity>Id.class)` matching the DEV PK column order; `CHAR(N)` columns get `@JdbcTypeCode(SqlTypes.CHAR)` so Hibernate's `validate` accepts the `bpchar` columns (precedent: `SysScrOptionWecms.sortYn`).

---

## 7. Verification queries (re-runnable against DEV)

```sql
-- (1) All PRIMARY KEYs across the 12 tables
SELECT c.conrelid::regclass::text AS table_name, c.conname, pg_get_constraintdef(c.oid)
FROM pg_constraint c
WHERE c.conrelid IN (
  'wecmsvndev.tb_firm_bank_code'::regclass, 'wecmsvndev.tb_firm_conf'::regclass,
  'wecmsvndev.tb_pay_target'::regclass,     'wecmsvndev.tb_firm_tran'::regclass,
  'wecmsvndev.tb_pay_file_mst'::regclass,   'wecmsvndev.tb_pay_file_data'::regclass,
  'wecmsvndev.tb_firm_file_mst'::regclass,  'wecmsvndev.tb_firm_file_data'::regclass,
  'wecmsvndev.tb_firm_file_rslt'::regclass, 'wecmsvndev.tb_firm_bat_api_tran'::regclass,
  'wecmsvndev.tb_batch_schedule_info'::regclass, 'wecmsvndev.tb_batch_schedule_dtl'::regclass
) AND contype='p'
ORDER BY 1;

-- (2) Row counts (live)
SELECT relname, n_live_tup FROM pg_stat_user_tables
WHERE schemaname='wecmsvndev' AND relname LIKE 'tb_%' AND relname IN (...);

-- (3) Sample one-line check — verify the API-path 1:1 with TB_FIRM_TRAN holds
SELECT count(*) FROM wecmsvndev.tb_pay_target t
LEFT JOIN wecmsvndev.tb_firm_tran f USING (tran_dt, biz_key, tran_seq)
WHERE f.tran_seq IS NULL;   -- expected: 0 once dispatch is complete
```
