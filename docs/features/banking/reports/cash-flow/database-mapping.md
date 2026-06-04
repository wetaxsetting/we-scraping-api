# Cash Flow — Database Mapping

Every table, column and function used by `cash_0001_001`, and the legacy → refactor mapping. Verified
against the JEX DEV database (read-only MCP), the IDO/WSVC contracts, and the refactor Java/Flyway.

> **No persisted summary.** Cash Flow is computed on-the-fly from raw transactions on every call —
> there is **no** cash-flow summary table, no batch, no trigger, no materialized view (Confirmed). This
> is the key performance consideration (gap G10): every request also re-derives the entire **prior**
> year for the opening balance, scanning the four source tables twice.

---

## 1. Object inventory

| Object | Kind | Legacy use | Refactor counterpart |
|---|---|---|---|
| `get_cashflow_report(...)` | **plpgsql function** | `_r001` / IDO `CASH_FLOW_R001` — computes the whole report | **Not ported** — reimplemented in `CashFlowReportService` + `cashflow/*` |
| `wb_get_chart_acct_level_text` / `wb_get_chart_acct_level` | functions | account depth → `'Level 1..5'` | `ChartOfAccountsHierarchy.levelOf` (Java parent-walk) |
| `wb_get_appr_stts` / `wb_get_admin_yn` | functions | approval status `P/C/R/I` | `ApprovalStatusResolver` (reduces to `appr_type_cd='01'`) |
| `chart_acct_infm` | table | row backbone (L1/L2/L3 tree) | `ChartAcctInfm` entity + `ChartAcctInfmRepository` |
| `scdul_pymn_sumr` / `scdul_pymn_sumr_detl` | tables | source #1 (scheduled payments) | `ScdulPymnSumr*` entity + `ScdulPymnSumrDetlRepository.findCashFlowSourceRows` |
| `ovrs_pymn_mstr` | table | source #2 (overseas payments) | `OvrsPymnMstr` entity + `OvrsPymnMstrRepository.findCashFlowSourceRows` |
| `card_trns_hstr` | table | source #3 (corporate card) | `CardTrnsHstr` entity + `CardTrnsHstrRepository.findClassifiedByBizKeyAndYear` |
| `acct_trns_hstr` | table | source #4 (bank txns) | `AcctTrnsHstr` entity + `AcctTrnsHstrRepository.findCashFlowSourceRows` |
| `acct_trns_rcv_pay_infm` | table | source #4 confirm gate (`cnfm_yn`) | `AcctTrnsRcvPayInfm` (EXISTS subquery, `cnfm_yn='Y'`) |
| `expt_resol_mstr` | table | sources #1-3 approval join | `ExptResolMstr` entity + `ExptResolMstrRepository.findCompletedApprSeqNos` |
| `expt_resol_apprvr_infm` | table | per-approver status (inside `wb_get_appr_stts`) | not needed — the gate reduces to `appr_type_cd='01'` (parity audit N0-A) |
| `bank_exch_rate_infm` | table | `_r002` FX rate | `ExchangeRateService` / `GET /api/v1/banking/exchange-rates` (selection logic differs — N3) |
| `acct_mstr` | table | `_r003` bank filter list | `AccountService` / `GET /api/v1/banking/accounts` (Haengsung hide-closed rule not ported — N4) |

All four source tables are in the **PROD Active Table List** ([wecms-api/CLAUDE.md](../../../../../CLAUDE.md)).

---

## 2. `chart_acct_infm` — Chart of Accounts (central table)

| Column | Type | Role in Cash Flow |
|---|---|---|
| `biz_key` | varchar(20) | tenant key |
| `chart_acct_seq_no` | varchar(14) | account sequence (PK part) |
| `chart_acct_cd` | varchar(20) | account code (`ACC-01..03`, leaf codes) |
| `chart_acct_nm_en` | varchar(255) | English name → L1/L2 display + L3 `item_nm` |
| `chart_acct_nm_ko` | varchar(255) | Korean name → L1/L2 display + L3 `display_nm` |
| `chart_acct_nm_vn` | varchar(255) | Vietnamese name (not used by the report) |
| `parent_chart_acct_cd` | varchar(20) | self-reference → builds L1/L2/L3 tree |
| `stts` | char(1) | report requires `'A'` (active) |
| `del_yn` | char(1) | report requires `'N'` |
| `cal_rule` | char(1) | **`P`=add / `M`=subtract** — ACC-03 signing only |

**Hierarchy:** depth via `parent_chart_acct_cd` walk. L1 = `ACC-01/02/03` (fixed roots, seeded for every
tenant in V96). DEV holds **41** accounts for tenant 000001, all `stts='A'`, `cal_rule ∈ {P,M}`.

| Level 1 root | EN | KO | Meaning | Balance contribution |
|---|---|---|---|---|
| `ACC-01` | REVENUE | 수입 | cash **inflow** | + (plain sum) |
| `ACC-02` | EXPENSES | 지출 | cash **outflow** | − (plain sum) |
| `ACC-03` | INVESTMENT | 투자 | investment/financing | only the 4 hardcoded codes, `cal_rule`-signed |

> ⚠️ **`Needs Verification` (G2):** the INVESTMENT subset `('2001026','2001027','2002003','2002002')`
> hardcoded in the End/Opening-balance branch returns **0 rows** in DEV. Verify on UAT/PROD.

---

## 3. Source tables — columns the aggregator reads

Only columns actually referenced are listed; year filtering is `SUBSTRING(date,1,4) = :year` in each
refactor repository query; `chart_acct_cd` must be non-null/non-empty.

- **#1 Scheduled** — `scdul_pymn_sumr_detl` (`biz_key, scdul_pymn_no, chart_acct_seq_no, chart_acct_cd,
  tran_amt`) ⋈ `scdul_pymn_sumr` (`biz_key, scdul_pymn_no, appr_seq_no, ccy_cd, out_bank_cd, wdrl_act_no,
  reg_dtm`). Refactor: `ScdulPymnSumrDetlRepository.findCashFlowSourceRows` joins detail↔header on
  `(biz_key, scdul_pymn_no)`, year on header `reg_dtm`, projects `ScdulPymnCashFlowRow`.
- **#2 Overseas** — `ovrs_pymn_mstr` (`biz_key, appr_seq_no, chart_acct_seq_no, chart_acct_cd, trnf_amt,
  trnf_ccy_cd, dbt_acct_cd, dbt_acct_no, reg_dtm`). The `chart_acct_cd`/`chart_acct_seq_no` columns were
  added to the local table in **V185** so overseas rows can be classified.
- **#3 Card** — `card_trns_hstr` (`biz_key, appr_seq_no, chart_acct_seq_no, chart_acct_cd, trsc_amt,
  ccy_cd, bank_cd, card_no, trsc_dt`). Local schema created in **V184**.
- **#4 Bank** — `acct_trns_hstr` (`biz_key, acct_seq_no, chart_acct_seq_no, chart_acct_cd, trsc_amt,
  ccy_cd, bank_cd, acct_no, trsc_dt`) with an **EXISTS** subquery on `acct_trns_rcv_pay_infm` joined by
  `(biz_key, acct_seq_no)` requiring `cnfm_yn='Y'` (the bank-side confirmation gate).
- **Approval** — `expt_resol_mstr` (`biz_key, appr_seq_no, reg_id, appr_type_cd`); refactor reads
  `findCompletedApprSeqNos` (`appr_type_cd='01'`) and batches the candidate `appr_seq_no` set.

---

## 4. Column mapping — Legacy → Entity → DTO → UI

The report's wire row is built by `CashFlowRowAssembler`, not by a single entity, so the "entity"
column below is the *source* of each value. Wire names are auto snake_cased by the global Jackson
`SNAKE_CASE` strategy from the camelCase DTO fields.

| Legacy `get_cashflow_report` column / WSVC field | Source (entity / computed) | DTO field (`CashFlowRow`) | Wire (snake_case) | Frontend (camelCase) | UI column |
|---|---|---|---|---|---|
| `sort_order` / `DISP_ORD` | assembler sequence (zero-padded 5) | `displayOrder` | `display_order` | `displayOrder` | (ordering only — not a visible column) |
| `display_code` / `DISP_CD` | `chart_acct_cd` / `ACC-0x` / `S. TOTAL` / `NON SALES PROFIT` / `SALES PROFIT` / `Opening Balance` / `End Balance` | `displayCd` | `display_cd` | `displayCd` | Code |
| `display_name` / `DISP_NM` | L1/L2 = `chart_acct_nm_ko + ' ' + _en`; L3 = `_ko`; bands = fixed labels | `displayNm` | `display_nm` | `displayNm` | Display Name |
| `item_code` / `ITEM_CD` | L3 `chart_acct_cd` | `itemCd` | `item_cd` | `itemCd` | Item code |
| `item_name` / `ITEM_NM` | L3 `chart_acct_nm_en` | `itemNm` | `item_nm` | `itemNm` | Item name |
| `row_type` / `ROW_TYPE` | row category (7 verbatim values) | `rowType` | `row_type` | `rowType` | (drives row styling, not a column) |
| `group_key` / `GRP_CD` | `ACC-0x` / `AAAAA-OPENING-BALANCE` / `ZZZZZ-SALES-PROFIT` / `ZZZZZ-END-BALANCE` | `groupCd` | `group_cd` | `groupCd` | (grouping only) |
| `parent_l2_code` / `PARENT_L2_CD` | L2 parent code for L3 rows | `parentL2Cd` | `parent_l2_cd` | `parentL2Cd` | (L3 bucketing) |
| `jan_amount..dec_amount` / `JAN_AMT..DEC_AMT` | `MonthlyAmountAccumulator` (FX-converted, rolled up) | `monthly.{jan..dec}` | `monthly.{jan..dec}` | `monthly.{jan..dec}` | Jan … Dec (12 monthly columns) |
| `total_amount` / `TOTAL_AMT` | annual roll-up (0 for Opening; End(Dec) for End) | `annualTotal` | `annual_total` | `annualTotal` | **Excel only** (no on-screen total column) |
| `display_currency` / `DISP_CCY_CD` | = `baseCurrency` | `displayCurrency` | `display_currency` | `displayCurrency` | (cell currency formatting) |

Special computed rows (no single source column):

| Logical row | `row_type` | `display_cd` | Monthly value |
|---|---|---|---|
| Opening Balance | `Opening Balance` | `Opening Balance` | `BalanceCalculator.openingBalance[]`; `annual_total = 0` |
| Sales Profit | `Sales Profit` | `SALES PROFIT` | `BalanceCalculator.salesProfit[]` (= revenue − expense) |
| NON SALES PROFIT | `Level 1 Subtotal` | `NON SALES PROFIT` | ACC-03 subtotal (sum of all ACC-03 L3, `cal_rule`-signed) |
| End Balance | `End Balance` | `End Balance` | `BalanceCalculator.endBalance[]`; `annual_total = End(Dec)` |

---

## 5. `bank_exch_rate_infm` — FX rates (legacy `_r002`)

IDO `BANK_EXCH_RATE_INFM_R002` outputs `BANK_CD, REF_DATE, REF_CCY_CD, CCY_CD, TRAN_SELL_AMT,
TRAN_BUY_AMT, BUY_AMT, SELL_AMT`. Legacy selection: latest **weekday** `REF_DATE ≤ today`, preferring
`SEQ_NO='2'` (closing) then `'1'`, for `REF_CCY_CD IN ('USD','KRW')`; the TS calls with
`BANK_CD='11704088'` (Shinhan) and reads `TRAN_SELL_AMT`.

**Refactor divergence (N3):** `GET /api/v1/banking/exchange-rates` selects the **latest seq per currency**
(no weekday rule, no `SEQ_NO=2` preference). The Cash Flow page still prefetches USD/KRW `sell` to seed
the rate input, so the "prefill from Shinhan" UX survives; the prefilled value can differ on
weekends/holidays or when two seq rows exist. **`Needs Verification`** with PO/finance.

## 6. `acct_mstr` — account list (legacy `_r003`)

Legacy `_r003` → `AcctMstrDAO.getRows` projecting `BANK_CD, BANK_NM, ACCT_NO`, filtered to
`actv_yn='Y'` + current tenant, with the **Haengsung-only** hide-closed-savings clause
(`ACCT_TYPE_CD NOT IN ('05','06','08') OR ((EXPI_DT IS NULL OR TO_DATE(EXPI_DT)>=CURRENT_DATE) AND
BAL>0)`, redmine #42633 — see [docs/jex/features/haengsung-hide-closed-bank-accounts.md](../../../../../../docs/jex/features/haengsung-hide-closed-bank-accounts.md)).
**Refactor divergence (N4):** the bank multi-select is populated from `GET /api/v1/banking/accounts`;
no Haengsung hide-closed rule is applied (consistent with Haengsung product-gates deferred in R&D).
**`Needs Verification`** before any Haengsung-facing release.

---

## 7. Flyway migrations (refactor schema)

| Version | Summary |
|---|---|
| `V124__cash_flow_report_view_setting_seed.sql` | Seeds 19 `SYS_SCR_OPTION_WECMS` columns for `web_svc_id='cash-flow-report'` / `default` (DISP_CD, DISP_NM, ITEM_CD, ITEM_NM, ROW_TYPE, JAN_AMT..DEC_AMT, TOTAL_AMT, DISP_CCY_CD), all `USE_YN='Y'`, `SORT_YN='N'`. **Only** unlocks the export visibility filter — the report page has no column-toggle popover. `ON CONFLICT DO NOTHING`. |
| `V184__card_trns_hstr_schema.sql` *(internal header says "V166" — known stale-header pattern)* | Creates `CARD_TRNS_HSTR` (26 cols, PK `(BIZ_KEY, CARD_SEQ_NO)`) — Cash Flow source #3. |
| `V185__ovrs_pymn_mstr_chart_acct_columns.sql` *(internal header "V167")* | Adds `CHART_ACCT_CD` + `CHART_ACCT_SEQ_NO` to `OVRS_PYMN_MSTR` (additive, nullable) so overseas rows can be classified — Cash Flow source #2. |

`CHART_ACCT_INFM` schema (V94) + restricted roots (V96) + demo seed (V97) land with Chart of Accounts;
`ACCT_TRNS_HSTR`/`ACCT_TRNS_RCV_PAY_INFM` (V13/V21/V23), `EXPT_RESOL_MSTR` (V109), `SCDUL_PYMN_SUMR(_DETL)`
(V131) land with their owning features. **No DB function** (`get_cashflow_report`) is created locally —
it is reimplemented in Java per the [DB Function / Legacy Function Policy](../../../../../CLAUDE.md).

## 8. Keys / indexes — `Needs Verification`

PK/FK/index definitions were not enumerated in this pass. Logical keys inferred from join predicates:
`chart_acct_infm` PK `(biz_key, chart_acct_seq_no)`, self-FK `parent_chart_acct_cd → chart_acct_cd`;
sources joined to `expt_resol_mstr` on `(biz_key, appr_seq_no)`; bank txn on `(biz_key, acct_seq_no)`;
`bank_exch_rate_infm` key `(biz_key, bank_cd, ref_date, ref_ccy_cd, seq_no)`. Run `pg_constraint` /
`pg_indexes` before relying on these for migration DDL.
