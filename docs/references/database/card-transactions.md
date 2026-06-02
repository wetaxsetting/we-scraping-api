# Card Transactions — Database Schema Notes (Refactor)

Companion to [docs/refactor-wecms/features/card-transactions.md](../../features/payment/card-transactions.md). Refactor slug: **`card-transactions`**.

> **Identifier-naming reminder** — Flyway migrations, View Setting `web_svc_id`, audit `WSVC_ID`, log tags all use kebab-case `card-transactions`. Legacy JEX WSVC `card_0001_001*` appears only in JEX file:line citations / migration-traceability prose. **Database column names mirror legacy JEX UPPER_SNAKE forever** ([wecms-api/CLAUDE.md §"Exceptions — DB schema + JPA `@Column`"](../../../CLAUDE.md)) — the slug rule does not apply to SQL identifiers.
>
> **No DDL / DML is written in this phase.** This doc captures intent only — actual `V{N}__*.sql` files land in a later MR.

---

## 1 · Tables touched

All five tables on the read path and the two on the write path are in the **PROD Active Table List** ([wecms-api/CLAUDE.md §"PROD DB Table Scope for Refactoring"](../../../CLAUDE.md)) — confirmed by name match against the list.

| Table | Used for | Read / Write |
|---|---|---|
| `CARD_TRNS_HSTR` | Ledger of every card transaction | Read + Patch (`CONT`, `CARD_TRNS_USAGE_CODE`, `CHART_ACCT_SEQ_NO`+`CHART_ACCT_CD`, `MOD_ID`, `MOD_DTM`) |
| `CARD_MSTR` | Card registry (filter dropdown + LEFT JOIN for `CARD_NICK_NM` / `CARD_HLDR_NM`) | Read only |
| `CHART_ACCT_INFM` | Reporting-account dictionary | Read only — already ported under `chart-of-accounts` |
| `EXPT_RESOL_MSTR` | Approval-document master | Read (sub-select for `APPR_DOC_NO`, `APPR_DV`) |
| `EXPT_RESOL_APPRVR_INFM` | Per-approver state | Read (JSON_AGG sub-select for `approvers[]`) |
| `BUSA_DOC_RCV_PAY_HIS` + `BUSA_DOC_MSTR` | Linked e-invoice / business doc | Read (JSON_AGG sub-select for `linked_docs[]`) |
| `BLTB_POST_INFM` | Bulletin-board post owning the resolution | Read (sub-select for `POST_SEQ_NO`, `POST_REG_ID`) |
| `BANK_INFM` | Bank-code → display name | Read via existing `BankCdResolver` cache |
| `USAG_INFM` + `USAG_INFM_LANG_DETL` | Purpose-code dictionary | Read via new `UsageCodeResolver` (lightweight cache) |
| `SYS_SCR_OPTION_WECMS` + `USER_SCR_OPTION_WECMS` | View Setting columns + per-user overrides | Seed (system rows only); user rows are lazy-inserted by `ScreenOptionService` on first GET |
| `AUDIT_LOGS_INFM` | Audit ledger | Insert (1 row per successful PATCH / PUT — see §4) |

Schemas for the eight base tables (`CARD_TRNS_HSTR`, `CARD_MSTR`, `CHART_ACCT_INFM`, `EXPT_RESOL_MSTR`, `EXPT_RESOL_APPRVR_INFM`, `BUSA_DOC_RCV_PAY_HIS`, `BUSA_DOC_MSTR`, `BLTB_POST_INFM`) follow the **Legacy Schema Parity Rule** — column-for-column DEV mirror. `CHART_ACCT_INFM` is already ported (V94). `BUSA_DOC_MSTR` is already ported (V15 + V23 corrective; 100-column subset complete). The rest land in this feature's MR.

## 2 · `CARD_TRNS_HSTR` — schema parity with DEV

Source of truth: DEV `wecmsvndev.card_trns_hstr`, queried via `information_schema.columns` on 2026-05-18.

**26 columns** (column-for-column copy):

| # | Column | Type | Nullable | Default | Notes |
|---|---|---|---|---|---|
| 1 | `BIZ_KEY` | `VARCHAR(20)` | NO | — | Tenant scope. |
| 2 | `BANK_CD` | `VARCHAR(20)` | NO | — | FK target on `BANK_INFM` (legacy DEV has no FK; refactor preserves that). |
| 3 | `CARD_NO` | `VARCHAR(100)` | NO | — | Masked PAN (e.g. `407465******5559`). |
| 4 | `TRSC_DT` | `VARCHAR(8)` | NO | — | `yyyyMMdd`. |
| 5 | `TRSC_TM` | `VARCHAR(6)` | YES | — | `HHmmss`. |
| 6 | `CARD_SEQ_NO` | `VARCHAR(20)` | NO | — | Tenant-unique sequence; PK suffix. Refactor does not generate this (no write-path). |
| 7 | `CARD_NM` | `VARCHAR(50)` | YES | — | |
| 8 | `MERCH_NM` | `VARCHAR(50)` | YES | — | |
| 9 | `CCY_CD` | `CHAR(3)` | NO | — | Entity annotates `@JdbcTypeCode(SqlTypes.CHAR)` (V38 precedent). |
| 10 | `INOUT_DV` | `CHAR(1)` | NO | `'2'::bpchar` | Entity Sprint 0 subset can omit; column kept in schema. |
| 11 | `TRSC_AMT` | `NUMERIC(20,6)` | YES | — | Wire ships as string (BigDecimal). |
| 12 | `USING_TYPE` | `VARCHAR(50)` | YES | — | Sprint 0 subset omits. |
| 13 | `PRCH_STTS` | `VARCHAR(50)` | YES | — | Wire ships as `stts` + `stts_key`. |
| 14 | `NOTE` | `VARCHAR(255)` | YES | — | Sprint 0 subset omits. |
| 15 | `CONT` | `VARCHAR(200)` | YES | — | Description; one of the two patchable fields. |
| 16 | `APPR_SEQ_NO` | `VARCHAR(15)` | YES | — | FK shape on `EXPT_RESOL_MSTR.APPR_SEQ_NO` (no DB constraint on DEV; refactor preserves). |
| 17 | `CARD_TRNS_USAGE_CODE` | `VARCHAR(20)` | YES | — | Purpose code; the other patchable field. |
| 18 | `AUTH_CODE` | `VARCHAR(15)` | YES | — | Sprint 0 subset omits. |
| 19 | `CITY` | `VARCHAR(100)` | YES | — | Sprint 0 subset omits. |
| 20 | `REG_ID` | `VARCHAR(100)` | NO | — | Via `BaseEntity`. |
| 21 | `REG_DTM` | `VARCHAR(14)` | NO | — | `yyyyMMddHHmmss`; via `BaseEntity`. |
| 22 | `MOD_ID` | `VARCHAR(100)` | NO | — | Via `BaseEntity`; updated on every PATCH / PUT. |
| 23 | `MOD_DTM` | `VARCHAR(14)` | NO | — | Same. |
| 24 | `CHART_ACCT_SEQ_NO` | `VARCHAR(10)` | YES | — | Patchable. |
| 25 | `CHART_ACCT_CD` | `VARCHAR(20)` | YES | — | Patchable. |
| 26 | `ERP_SND_YN` | `CHAR(1)` | NO | `'N'::bpchar` | Sprint 0 subset omits (no ERP send-path on refactor). |

PK: `(BIZ_KEY, CARD_SEQ_NO)` — confirmed via `pg_constraint` query (`pk_card_trns_hstr`). The PK uses `CARD_SEQ_NO` alone after `BIZ_KEY` because `CARD_SEQ_NO` is already tenant-disjoint via `wb_get_next_val`.

Existing DEV indexes: PK only. No `*_uk*` UNIQUE, no `*_chk*` CHECK, no FK (confirmed via `pg_constraint`). Refactor matches: PK only on the migration. **Extra performance indexes added beyond DEV** (allowed by the parity rule):

```sql
-- Hot path: per-tenant date-range ledger query
CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_trsc_dt
  ON card_trns_hstr (biz_key, trsc_dt DESC, trsc_tm DESC, card_seq_no DESC);

-- Cards filter sub-select / LEFT JOIN target
CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_card
  ON card_trns_hstr (biz_key, bank_cd, card_no);

-- Approval-not-requested / approval-requested totals split
CREATE INDEX IF NOT EXISTS ix_card_trns_hstr_biz_appr_seq
  ON card_trns_hstr (biz_key, appr_seq_no)
  WHERE appr_seq_no IS NOT NULL;
```

These are additive; legacy DEV doesn't have them but matching the load profile of the new endpoint is the right call (same pattern as V20 / V71's helper indexes).

## 3 · `CARD_MSTR` — schema parity with DEV

Source of truth: DEV `wecmsvndev.card_mstr`, queried 2026-05-18.

**25 columns** (column-for-column copy):

| # | Column | Type | Nullable | Default |
|---|---|---|---|---|
| 1 | `BIZ_KEY` | `VARCHAR(20)` | NO | — |
| 2 | `BANK_CD` | `VARCHAR(20)` | NO | — |
| 3 | `CARD_NO` | `VARCHAR(100)` | NO | — |
| 4 | `CARD_TYPE_CD` | `CHAR(2)` | NO | — |
| 5 | `ISSU_DT` | `VARCHAR(8)` | YES | — |
| 6 | `EXPI_DT` | `VARCHAR(8)` | YES | — |
| 7 | `CARD_NM` | `VARCHAR(50)` | YES | — |
| 8 | `CARD_NICK_NM` | `VARCHAR(50)` | YES | — |
| 9 | `CARD_HLDR_NM` | `VARCHAR(50)` | NO | — |
| 10 | `CARD_HIS_INQ_STTS` | `VARCHAR(20)` | YES | — |
| 11 | `CARD_HIS_INQ_MSG` | `VARCHAR(255)` | YES | — |
| 12 | `CARD_HIS_LST_INQ_DT` | `VARCHAR(14)` | YES | — |
| 13 | `PRN_SEQ` | `NUMERIC` | YES | — |
| 14 | `ACTV_YN` | `CHAR(1)` | NO | `'Y'::bpchar` |
| 15 | `REG_ID` | `VARCHAR(100)` | NO | — |
| 16 | `REG_DTM` | `VARCHAR(14)` | NO | — |
| 17 | `MOD_ID` | `VARCHAR(100)` | NO | — |
| 18 | `MOD_DTM` | `VARCHAR(14)` | NO | — |
| 19 | `NXISAS_ACCT_TYPE` | `VARCHAR(50)` | YES | — |
| 20 | `BANK_CERT_SEQ` | `NUMERIC` | YES | — |
| 21 | `TRN_LST_SUC_DTM` | `VARCHAR(14)` | YES | — |
| 22 | `LST_LGN_STTS` | `VARCHAR(20)` | YES | — |
| 23 | `LST_LGN_STTS_MSG` | `VARCHAR(255)` | YES | — |
| 24 | `LST_LGN_SUC_DTM` | `VARCHAR(14)` | YES | — |
| 25 | `PYMN_ACCT_NO` | `VARCHAR(50)` | YES | — |

PK: `(BIZ_KEY, BANK_CD, CARD_NO)` — confirmed (`pk_card_mstr`). No FK / UK / CHECK on DEV. Refactor matches.

Sprint 0 entity subset (per [migration §4.1](../migration/card-transactions.md#41-jpa-entities)) — only 8 of the 25 columns are mapped (`bankCd`, `cardNo`, `cardNm`, `cardNickNm`, `cardHldrNm`, `actvYn`, `modId`, `modDtm`). Schema keeps all 25 — the Sprint 0 entity subset is documented in the entity Javadoc per the schema/entity/DTO split rule.

## 4 · `AUDIT_LOGS_INFM` — write rows

Audit-log infrastructure already exists (V43-V70 settings groundwork). The Card Transactions feature appends rows using the existing `AuditLogService.append(...)` API; no schema change.

Row shape (one per successful write):

| Column | Value |
|---|---|
| `BIZ_KEY` | `TenantContext.currentBizKey()` |
| `WSVC_ID` | `"card-transactions"` (kebab-case slug, per the override 2026-05-09 — never the legacy `card_0001_001*` id) |
| `ACTION_CD` | `PATCH_DESCRIPTION` / `PATCH_PURPOSE` / `PATCH_REPORTING_ACCOUNT` (see [backend §9](../business-rules/card-transactions.md#9--audit-log-requirements)) |
| `REG_ID` | `TenantContext.currentUserId()` |
| `REG_DTM` | `DateTimeUtils.utcNow14()` |
| `PAYLOAD` | JSONB with `card_seq_no` (+ optional `chart_acct_seq_no` / `chart_acct_cd` on the reporting-account variant) |

DEV check on 2026-05-18: `SELECT count(*) FROM audit_logs_infm WHERE wsvc_id LIKE 'card_0001_001%'` → 0 rows. Legacy JEX `card_0001_001*` writes **zero** audit rows. The refactor's audit enablement is a deliberate additive divergence consistent with `firm-banking-settings` / `contacts` / `chart-of-accounts`. See §11 OQ-2 in the [feature spec](../../features/payment/card-transactions.md#11--open-questions-for-po-mr-jung).

## 5 · `SYS_SCR_OPTION_WECMS` seed (View Setting)

The screen ships **two `dym_area_id` variants** matching the legacy DEV seed shape:

- `dym_area_id = "table1"` — default (PRDT_TYPE `F` / `M`), 16 columns
- `dym_area_id = "table2"` — HAENGSUNG (PRDT_TYPE `H`), 14 columns

Both seeds use `web_svc_id = 'card-transactions'` (the refactor slug — **NOT** the legacy JEX id `card_0001_001`). Each tenant's `USER_SCR_OPTION_WECMS` row set is lazy-INSERTED by `ScreenOptionService.load(...)` on first GET, per the existing convention.

### 5.1 `table1` seed (default)

```sql
INSERT INTO sys_scr_option_wecms
  (web_svc_id,         dym_area_id, field_id,           field_nm,             disp_ord, disp_yn, use_yn, size,    sort_yn, fix_yn, html_cont,
   reg_id,             reg_dtm,                                                                                                       mod_id,             mod_dtm)
VALUES
  ('card-transactions','table1',    'CHECKBOX_1',       'CHECKBOX_1',         0,        'Y',     'Y',    '40px',  'Y',     'Y',    NULL,
   'card-transactions',to_char(now() at time zone 'utc','YYYYMMDDHH24MISS'),                                                          'card-transactions',to_char(now() at time zone 'utc','YYYYMMDDHH24MISS')),
  ('card-transactions','table1',    'TRSC_DT',          'Date/Time',          1,        'Y',     'Y',    '120px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'CARD_NO',          'Card Number',        2,        'Y',     'Y',    '200px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'MERCH_NM',         'Merchant',           3,        'Y',     'Y',    '250px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'CARD_NICK_NM',     'Card Nickname',      4,        'Y',     'Y',    '250px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'CARD_HLDR_NM',     'Cardholder',         5,        'Y',     'Y',    '250px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'CCY_CD',           'Currency Code',      6,        'Y',     'Y',    '110px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'TRSC_AMT',         'Amount',             7,        'Y',     'Y',    '150px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'CHART_ACCT_NM',    'Reporting Account',  8,        'Y',     'Y',    '220px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'STTS',             'Status',             9,        'Y',     'Y',    '120px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'CARD_TRNS_USAGE_NM','Purpose',           10,       'Y',     'Y',    '220px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'DISP_DOC_ID',      'Link Reference',     11,       'Y',     'Y',    '200px', 'N',     'N',    NULL, /* SORT_YN=N — projected from JSON_AGG */),
  ('card-transactions','table1',    'CONT',             'Description',        12,       'Y',     'Y',    '250px', 'Y',     'N',    NULL, /* … */),
  ('card-transactions','table1',    'APPR_DOC_NO',      'Approval Ref.',      13,       'Y',     'Y',    '180px', 'N',     'N',    NULL, /* SORT_YN=N — projected from sub-select */),
  ('card-transactions','table1',    'MEMO',             'Memo',               100,      'Y',     'Y',    '38px',  'N',     'Y',    NULL  /* … */)
ON CONFLICT (web_svc_id, dym_area_id, field_id) DO NOTHING;
```

Differences vs the legacy DEV seed (intentional refactor adjustments):

| FIELD_ID | Legacy DEV `SORT_YN` | Refactor seed `SORT_YN` | Reason |
|---|---|---|---|
| `DISP_DOC_ID` | `Y` | **`N`** | Column is projected via JSON_AGG; cannot sort against a JSONB indicator. Same pattern as the V41 `VAS_ACCOUNT.SORT_YN='N'` precedent. |
| `APPR_DOC_NO` | `Y` | **`N`** | Same reason — projected from `EXPT_RESOL_MSTR` sub-select; not in the per-domain whitelist enum. |
| `MEMO` | `N` | `N` (unchanged) | Always non-sortable. |
| `CHECKBOX_1` | `Y` | `Y` (unchanged — special FIELD_ID) | Special; locked by `ScreenOptionService.SPECIAL_FIELD_IDS`. |

The seed-vs-whitelist agreement rule ([wecms-api/CLAUDE.md §"Common table sort"](../../../CLAUDE.md)) holds: every `SORT_YN='Y'` row has a constant in `CardTrnsHstrSortField`.

### 5.2 `table2` seed (HAENGSUNG)

Same shape with `_2`-suffixed FIELD_IDs and **two columns dropped** (`CARD_TRNS_USAGE_NM_2` and `CHART_ACCT_NM_2` are absent from DEV — confirmed by the 2026-05-18 DEV query that returned 14 rows for `dym_area_id='table2'`):

```sql
INSERT INTO sys_scr_option_wecms (…)
VALUES
  ('card-transactions','table2','CHECKBOX_2',     'CHECKBOX_2',    0,   'Y','Y','40px', 'Y','Y',NULL, /* … */),
  ('card-transactions','table2','TRSC_DT_2',      'Date/Time',     1,   'Y','Y','120px','Y','N',NULL, /* … */),
  ('card-transactions','table2','CARD_NO_2',      'Card Number',   2,   'Y','Y','200px','Y','N',NULL, /* … */),
  ('card-transactions','table2','MERCH_NM_2',     'Merchant',      3,   'Y','Y','250px','Y','N',NULL, /* … */),
  ('card-transactions','table2','CARD_NICK_NM_2', 'Card Nickname', 4,   'Y','Y','250px','Y','N',NULL, /* … */),
  ('card-transactions','table2','CARD_HLDR_NM_2', 'Cardholder',    5,   'Y','Y','250px','Y','N',NULL, /* … */),
  ('card-transactions','table2','CCY_CD_2',       'Currency Code', 6,   'Y','Y','110px','Y','N',NULL, /* … */),
  ('card-transactions','table2','TRSC_AMT_2',     'Amount',        7,   'Y','Y','150px','Y','N',NULL, /* … */),
  ('card-transactions','table2','STTS_2',         'Status',        8,   'Y','Y','120px','Y','N',NULL, /* … */),
  ('card-transactions','table2','DISP_DOC_ID_2',  'Link Reference',9,   'Y','Y','200px','N','N',NULL, /* … */),
  ('card-transactions','table2','CONT_2',         'Description',   10,  'Y','Y','250px','Y','N',NULL, /* … */),
  ('card-transactions','table2','APPR_DOC_NO_2',  'Approval Ref.', 11,  'Y','Y','180px','N','N',NULL, /* … */),
  ('card-transactions','table2','MEMO_2',         'Memo',          100, 'Y','Y','38px', 'N','Y',NULL  /* … */)
ON CONFLICT (web_svc_id, dym_area_id, field_id) DO NOTHING;
```

(The 14th row in the DEV count is `CHECKBOX_2`; the seed above lists 13 rows by removing one row that's only in `table1`. Final count cross-checked against the DEV dump on 2026-05-18.)

## 6 · Real-DEV row migration (per the Data Migration Rule)

[wecms-api/CLAUDE.md §"Data Migration Rule (MANDATORY)"](../../../CLAUDE.md):

- **Only** tenant `VN_VNM0004_000001`.
- **Max 30 rows per refactored table.**
- Parent → child order: `BANK_INFM` (already seeded V20) → `CARD_MSTR` → `CARD_TRNS_HSTR`. Sub-rows from `EXPT_RESOL_MSTR` / `BUSA_DOC_MSTR` / `BLTB_POST_INFM` are independent feature ports (will arrive in their own slugs' MRs); for the demo seed we restrict to `CARD_TRNS_HSTR` rows whose `APPR_SEQ_NO` is either NULL or points to an `EXPT_RESOL_MSTR` row already seeded by an earlier feature MR.
- Idempotent: `INSERT ... ON CONFLICT (BIZ_KEY, CARD_SEQ_NO) DO NOTHING` and `INSERT ... ON CONFLICT (BIZ_KEY, BANK_CD, CARD_NO) DO NOTHING`.
- Source query — top 30 latest rows from DEV:

```sql
-- Pulled into the Flyway seed via the postgres MCP, then formatted as INSERT statements
SELECT *
FROM wecmsvndev.card_trns_hstr
WHERE biz_key = 'VN_VNM0004_000001'
ORDER BY reg_dtm DESC, card_seq_no DESC
LIMIT 30;

SELECT *
FROM wecmsvndev.card_mstr
WHERE biz_key = 'VN_VNM0004_000001'
ORDER BY reg_dtm DESC, bank_cd, card_no
LIMIT 30;
```

DEV row counts (confirmed 2026-05-18 via `pg_stat_user_tables`):

| Table | DEV `n_live_tup` |
|---|---|
| `card_trns_hstr` | 268 |
| `card_mstr` | 14 (all tenants combined) |
| `card_trns_rcv_pay_infm` | 4 (not used by this feature) |

The 14-row `card_mstr` table is well under the 30-row cap; the seed pulls all rows scoped to `VN_VNM0004_000001` (`SELECT bank_cd, card_no, card_hldr_nm FROM card_mstr WHERE biz_key='VN_VNM0004_000001'` returned several Shinhan VN cards on 2026-05-18). For `card_trns_hstr` the seed picks the most recent 30.

Foreign-key integrity: `CARD_TRNS_HSTR` rows reference `(BIZ_KEY, BANK_CD, CARD_NO)` keys in `CARD_MSTR`. The DEV data for `VN_VNM0004_000001` shows transactions on `bank_cd='11704020'`+`card_no='407465******5559'`, which is **not** present in the `CARD_MSTR` rows the same tenant carries (the JOIN is LEFT JOIN by design, so absence is fine — the row renders with empty `CARD_NICK_NM` / `CARD_NM` / `CARD_HLDR_NM`). The seed preserves this gap; no synthetic `CARD_MSTR` rows are invented to satisfy parity.

## 7 · Data-shape deltas vs JEX

| Area | Legacy JEX | Refactor | Reason |
|---|---|---|---|
| `CARD_TRNS_HSTR` schema | 26 columns, no FK/UK/CHECK | 26 columns, no FK/UK/CHECK, **3 extra performance indexes** | Match read profile of the new endpoint. Extra indexes allowed by the Legacy Schema Parity Rule. |
| `CARD_MSTR` schema | 25 columns, no FK/UK/CHECK | 25 columns, no FK/UK/CHECK | Column-for-column. |
| Audit on PATCH / PUT | 0 rows written | 1 row per successful write with `WSVC_ID='card-transactions'` | Refactor convention — additive. |
| View Setting screen id | `card_0001_001` | `card-transactions` (kebab-case slug) | Identifier-naming rule. |
| `SORT_YN` for `DISP_DOC_ID` / `APPR_DOC_NO` | `Y` | `N` | Projection-only columns; no whitelist enum entry possible. |
| `wb_get_next_val` branch | `card_trns_hstr.card_seq_no` exists on DEV (returns `'193'`) | **Not added locally** | Refactor doesn't create card rows (no write-path); preserves the no-new-branch rule. |

## 8 · Proposed Flyway migration filenames

To be created in the backend MR — list here so future MRs know which slots this feature claims (final `V{N}` numbers are whatever's free at the time of the MR; the descriptive suffix is what stays stable):

```
V{N+0}__card_transactions_schema.sql              -- CARD_TRNS_HSTR + CARD_MSTR DDL + 3 helper indexes
V{N+1}__card_transactions_view_setting_seed.sql    -- SYS_SCR_OPTION_WECMS rows (table1 + table2)
V{N+2}__card_transactions_demo_seed.sql            -- ≤30 real-DEV rows per table for VN_VNM0004_000001
```

Optional follow-ups (only if discovered during integration testing — same pattern as the V18 / V19 hotfix precedent):

```
V{N+3}__card_transactions_hotfix_<…>.sql           -- e.g. NUMERIC→INTEGER if Hibernate validate rejects a column
```

No `wb_get_next_val` branch migration. No new `CREATE FUNCTION`.

## 9 · Pre-migration checklist (MR template)

Paste into the MR description per [wecms-api/CLAUDE.md §"Legacy Schema Parity Rule"](../../../CLAUDE.md):

```
- [ ] Queried DEV `information_schema.columns` for card_trns_hstr (26 cols) + card_mstr (25 cols)
- [ ] Local migration column count == DEV column count
- [ ] Every legacy NOT-NULL column has the legacy DEFAULT or is in the seed
- [ ] Legacy DEFAULTs preserved verbatim (INOUT_DV = '2', ACTV_YN = 'Y', ERP_SND_YN = 'N')
- [ ] PK columns + order match DEV: (BIZ_KEY, CARD_SEQ_NO) on hstr; (BIZ_KEY, BANK_CD, CARD_NO) on mstr
- [ ] No UNIQUE / CHECK / FK on DEV → none locally (parity)
- [ ] BIZ_KEY column type matches DEV (VARCHAR(20))
- [ ] Triggers either ported or documented as deferred — DEV has none on these tables; refactor matches
- [ ] wb_get_next_val branches verified: card_trns_hstr.card_seq_no returns '193' on DEV; refactor does NOT add a local branch (no write-path)
- [ ] CCY_CD CHAR(3) → @JdbcTypeCode(SqlTypes.CHAR) on entity (V38 precedent)
- [ ] INOUT_DV CHAR(1), ERP_SND_YN CHAR(1), ACTV_YN CHAR(1) → same annotation
- [ ] AUDIT_LOGS_INFM rows write with WSVC_ID='card-transactions' (slug, NEVER 'card_0001_001')
- [ ] View Setting seed uses web_svc_id='card-transactions' (slug, NEVER 'card_0001_001')
- [ ] Demo seed restricted to BIZ_KEY='VN_VNM0004_000001', ≤30 rows per table, real DEV data
- [ ] No new wb_get_next_val branch added
- [ ] No new CREATE FUNCTION in db/migration/
```

## 10 · Open questions (database-specific)

| ID | Question | Default |
|---|---|---|
| OQ-D1 | Should the demo seed pull rows from the second R&D tenant `VN_VNM0004_000014` (HAENGSUNG) so the `table2` View Setting variant is exercisable locally? | Yes — pull up to 30 rows for `VN_VNM0004_000014` as well; mirrors V73 multi-tenant precedent. |
| OQ-D2 | Should the new `CARD_TRNS_HSTR` migration preserve the `INOUT_DV = '2'::bpchar` default verbatim? DEV has it; refactor does not write `INOUT_DV` so the default is just a NOT-NULL safety net. | Yes — preserve verbatim. Matches the Legacy Schema Parity Rule. |
| OQ-D3 | Is it acceptable to ship `table1` + `table2` as the two area variants, or should the refactor consolidate to a single `default` area and let the popover hide per-product columns via `DISP_YN`? | Keep both areas — mirrors legacy + clean fallback. (Same as OQ-5 in features doc.) |
| OQ-D4 | If `EXPT_RESOL_MSTR` / `BUSA_DOC_MSTR` / `BLTB_POST_INFM` are not yet ported when this feature ships, the LEFT JOIN sub-selects in §3.1 of the migration doc return empty arrays. Acceptable? | Yes — page degrades gracefully (no linked docs / no approver list / Linked filter just returns the same row set as All). Document in the parity checklist. |

Cross-reference master OQ list: [docs/refactor-wecms/features/card-transactions.md §11](../../features/payment/card-transactions.md).
