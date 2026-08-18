# Daily Financial (HS) — Database Mapping


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../../references/database/flyway-migration-baseline.md) §5.

> Tables, functions and query behaviour, verified against the refactor entity/repository source,
> the legacy IDO XML, and the two audit records. The PL/pgSQL **body** of the report function
> lives only in the database (not in either source tree) — statements about its internals are
> limited to what the audits verified against the live refactor DB and are flagged as such.

---

## 1. Scope verdict

| Object | Kind | In refactor baseline? | Role here |
|---|---|---|---|
| `REPT_TRNS_HSTR` | table | ✅ **PO-approved baseline addition** (added out-of-band with the function, 2026-06-14 — it is NOT in the original PROD Active Table List; see [`wecms-api/CLAUDE.md`](../../../../../CLAUDE.md) §"Legacy report DB functions") | Read base of the function **and** write target of the 5 editable columns |
| `fn_get_haengsung_daily_financial_report(biz_key, as_of_date, account_filter)` | set-returning PL/pgSQL function | ✅ same PO-approved addition | The **entire** computation engine (row stream + all totals) |
| `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` | tables (existing) | ✅ | View Setting rows for screenId `banking-reports-daily-financial-hs`/`default` (V249 manual seed) |
| `AUDIT_LOGS_INFM`, `ERP_TRNS_HSTR` | tables | — | **Not touched by this feature** (no audit-log writes, no export audit row) |

Flyway is disabled in every profile (`ddl-auto=validate`); nothing here is auto-applied. The
function + table were hand-ported from DEV; V249 is a **manual DBA script**
(header: "Manual DBA Script — Flyway disabled/removed. Execute manually in DEV → UAT → PROD").

---

## 2. Tables

### 2.1 `REPT_TRNS_HSTR`

Per-transaction daily-report ledger for HAENGSUNG. PK `(BIZ_KEY, ACCT_SEQ_NO)`.

**Columns mapped by the refactor entity** (`domain/banking/entity/ReptTrnsHstr.java` — a
deliberate *Sprint column subset*; the physical table carries more columns, owned by the read
function):

| Column | Type (entity) | Null | Meaning | CRUD here |
|---|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | N | tenant (PK) | R (key) |
| `ACCT_SEQ_NO` | VARCHAR(20) | N | ledger row key (PK) | R (key) |
| `REPT_CD` | VARCHAR(20) | Y | Receipt Code — user-editable | U |
| `PYMN_CD` | VARCHAR(20) | Y | Payment Code — user-editable | U |
| `DESC_NM_EN` | VARCHAR(255) | Y | Description (English) — user-editable | U |
| `DESC_NM_KO` | VARCHAR(255) | Y | Description (Korean) — user-editable | U |
| `MEMO` | VARCHAR(500) | Y | Memo — user-editable | U |
| `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` | BaseEntity audit | — | `MOD_ID` set by the write service, `MOD_DTM` by `@PreUpdate` | U |

Unmapped-but-present columns (read via the function, never written by the refactor): transaction
date/time/seq, amount (`TRSC_AMT`), balance (`BAL`), currency, in/out flag, counterparty
(`rcv_pay_trco_nm` / `supp_rmrk`), `hand_reg_yn`, `erp_snd_yn`, `SUPP_RMRK`, … — the legacy
entity `WECMS DEV/WECMS/src/wabooks/entity/ReptTrnsHstr.java` documents the full field set. The
exact physical DDL of the refactor copy was **not re-dumped for this package** — *full column
inventory: not verified from implementation* (the legacy entity + the function's 24 output
columns are the best in-tree evidence).

Seed data: **70 rows** on the shared dev DB for `VN_VNM0004_000001` (dates 2026-05-18..2026-06-24)
— recorded in the legacy audit §3.1; there is no Flyway/manual seed file for them in the repo.

**Key generation:** none. The refactor **never inserts** into `REPT_TRNS_HSTR` — it only updates
existing rows by PK. (No `wb_get_next_val` branch involved; consistent with the funnel policy.)

### 2.2 `SYS_SCR_OPTION_WECMS` (View Setting seed — V249)

12 rows under `web_svc_id='banking-reports-daily-financial-hs'`, `dym_area_id='default'`,
all `sort_yn='N'`, idempotent `ON CONFLICT (web_svc_id, field_id) DO NOTHING`; legacy rows
(`web_svc_id='rept_0017_001'`, `dym_area_id='table1'`) are never touched.

FIELD_ID remap (legacy → refactor; widths in px):

| Legacy | Refactor FIELD_ID | Width |
|---|---|---|
| `BANK_ACCT` | `BANK` | 125 |
| `CCY_CD` | `CURRENCY` | 78 |
| `RECEIPT_CODE` | `RECEIPT_CODE` | 170 |
| `PAYMENT_CODE` | `PAYMENT_CODE` | 170 |
| `DESC_EN` | `DESCRIPTION_EN` | 200 |
| `DESC_KO` | `DESCRIPTION_KO` | 200 |
| `RCPT_NM` | `COUNTERPARTY` | 200 |
| `OPEN_BAL` | `BEFORE_BALANCE` | 135 |
| `RCPT` | `RECEIPT` | 135 |
| `PYMN` | `PAYMENT` | 135 |
| `CLOSE_BAL` | `BALANCE` | 135 |
| `MEMO` | `MEMO_NOTE` | 285 |

> The memo column is `MEMO_NOTE`, **not** `MEMO` — `MEMO` is a reserved special FIELD_ID
> (`VIEW_SETTING_SPECIAL_FIELD_IDS`) that the shared popover would hide (V249 header, lines
> 50–52).

---

## 3. Function

### `fn_get_haengsung_daily_financial_report(p_biz_key, p_as_of_date, p_account_filter)`

| Aspect | Value |
|---|---|
| Kind | read-only set-returning PL/pgSQL (legacy name `FN_GET_HAENGSUNG_DAILY_FINANCIAL_REPORT`) |
| Params | `p_biz_key` VARCHAR · `p_as_of_date` VARCHAR `yyyyMMdd` (validates `^\d{8}$`, raises on bad format) · `p_account_filter` VARCHAR (`'ALL'` or JSON `[{"BANK_CD","ACCT_NO"},…]`) |
| Returns | 24 output columns / 5 row types (`HEADER`, `BEFORE_BALANCE`, `TRANSACTION`, `ACCOUNT_TOTAL`, `CURRENCY_TOTAL`) — verified against the live refactor DB in the legacy audit §3.1 |
| Computation | opening balances, per-transaction running figures, per-account SUB TOTALs, per-currency grand TOTALs, `balance_company_actual`; embeds `Due date:` text into `BANK_ACCOUNT` for saving accounts; filters `hand_reg_yn='N'` (audit-verified) |
| Grand-total marker | `CURRENCY_TOTAL` rows carry `account_type_order = 999990/999991/999992` |
| Refactor call site | **exactly one** — `DailyFinancialHsReportRepository.fetchReport` (the documented single-funnel exception in `wecms-api/CLAUDE.md` §"Legacy report DB functions") |
| Body / DDL | lives in the database only — *not in either source tree; internals beyond the audit-verified facts above: not verified from implementation* |

## 4. The refactor read query (verbatim)

From [`DailyFinancialHsReportRepository.java:49-83`](../../../../../src/main/java/com/kosign/wecms/domain/banking/repository/DailyFinancialHsReportRepository.java#L49-L83):

```sql
SELECT row_type               AS "rowType",
       bank_account           AS "bankAccount",
       currency               AS "currency",
       rept_cd                AS "reptCd",
       pymn_cd                AS "pymnCd",
       description_english    AS "descriptionEnglish",
       description_korean     AS "descriptionKorean",
       customer_supplier      AS "customerSupplier",
       before_balance         AS "beforeBalance",
       receipt                AS "receipt",
       payment                AS "payment",
       balance                AS "balance",
       balance_company_actual AS "balanceCompanyActual",
       account_number         AS "accountNumber",
       account_type_order     AS "accountTypeOrder",
       acct_seq_no            AS "acctSeqNo",
       transaction_date       AS "transactionDate",
       transaction_time       AS "transactionTime",
       transaction_seq        AS "transactionSeq",
       memo                   AS "memo",
       bank_cd_out            AS "bankCdOut",
       bank_name_sort         AS "bankNameSort"
  FROM fn_get_haengsung_daily_financial_report(:bizKey, :periodDate, :accountFilter)
 ORDER BY CASE currency
              WHEN 'VND' THEN 1
              WHEN 'USD' THEN 2
              WHEN 'KRW' THEN 3
              ELSE 99
          END,
          account_type_order,
          bank_name_sort,
          account_number NULLS LAST,
          transaction_seq
```

vs the legacy IDO (`IDO.DAILY_FINCL_REPT_R001.xml:24-64`): same source function, same ORDER BY —
but the legacy adds `WHERE ROW_TYPE IN (…the 5 types…) AND CURRENCY IN ('VND','USD')`. The
refactor **deliberately drops the currency filter** (KRW inclusion, PO-approved) and carries no
ROW_TYPE filter (the function emits only those five types).

## 5. The write statement

`ReptTrnsHstrRepository.findByBizKeyAndAcctSeqNo` (derived query) + `save(entity)` → Hibernate
`UPDATE REPT_TRNS_HSTR SET <changed editable columns>, MOD_ID, MOD_DTM WHERE BIZ_KEY=? AND
ACCT_SEQ_NO=?` — the JPA equivalent of legacy IDO `REPT_TRNS_HSTR_U000`
(`SET :FIELDS_TO_UPDATE WHERE BIZ_KEY=? AND ACCT_SEQ_NO=?`). Tenant scope is structural: `bizKey`
always comes from `TenantContext`, so cross-tenant lookup is impossible.

## 6. Indexes / constraints

PK `(BIZ_KEY, ACCT_SEQ_NO)` is relied on by both paths. No feature-specific helper indexes exist
in the repo. *Further constraints/indexes on the refactor copy of the table: not verified from
implementation.*
