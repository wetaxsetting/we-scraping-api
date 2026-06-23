# Corporate Card — Database Mapping (Card Transactions)

Entity ↔ table ↔ column for the Card Transactions sub-module. Tables are mirrored column-for-column from legacy JEX DDL; entity mappings use legacy column names. Verify any change against [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md) before touching the schema.

> Package: `com.kosign.wecms.domain.payment.cardtransactions.entity.*` (own entities) + cross-domain reused entities.

---

## 1 · Owned entities

| Table | Role | Entity | PK |
|---|---|---|---|
| `CARD_TRNS_HSTR` | Ledger — one row per scraped card transaction (read + classify + stamp `APPR_SEQ_NO`) | `CardTrnsHstr` (+ `CardTrnsHstrId`) | composite — `(BIZ_KEY, CARD_SEQ_NO)` |
| `CARD_MSTR` | Card registry per `(BIZ_KEY, BANK_CD, CARD_NO)` — Management modal CRUD + bulk-create | `CardMstr` (+ `CardMstrId`) | `(BIZ_KEY, BANK_CD, CARD_NO)` |
| `CARD_TRNS_RCV_PAY_INFM` | Card-link master (one per linked card) | `CardTrnsRcvPayInfm` (+ `CardTrnsRcvPayInfmId`) | composite |
| `BUSA_DOC_RCV_PAY_HIS` | Per-doc card-link rows (`RCV_PAY_WAY_CD='R'`) | `BusaDocRcvPayHis` (+ `BusaDocRcvPayHisId`) | composite |
| `MEMO_INFM` | Polymorphic memo store (side panel) | `MemoInfm` (+ `MemoInfmId`) | composite incl. `SEQ_NO` |
| `USAG_INFM` (+ `USAG_INFM_LANG_DETL`) | Purpose-code dictionary | `UsagInfm` | code-based |

### Key `CardTransactionRow` columns (CARD_TRNS_HSTR)

`CARD_SEQ_NO` · `BANK_CD` · `CARD_NO` · `CARD_NM` · `MERCH_NM` · `CCY_CD` · `TRSC_AMT` (`BigDecimal`) · `TRSC_DT` (`yyyyMMdd`) · `TRSC_TM` (`HHmmss`) · `CARD_TRNS_USAGE_CODE` · `CHART_ACCT_SEQ_NO` · `CHART_ACCT_CD` · `CONT` (description) · `APPR_SEQ_NO` · `PRCH_STTS` (→ wire `stts`) · `USING_TYPE` · `AUTH_CODE` · `CITY` · `NOTE`. `CARD_HLDR_NM` / `CARD_NICK_NM` join from `CARD_MSTR`.

> **Not on the wire:** no resolved-name columns. `card_trns_usage_nm`, `chart_acct_nm`/`_en`/`_level_nm`, `appr_dv`, `post_seq_no`, `post_reg_id` are NOT emitted — display names resolve client-side (APPROVED DEVIATION; documented in `CardTransactionRow.java:61-67`).

---

## 2 · Reused cross-domain tables

| Table | Role | Entity (package) |
|---|---|---|
| `BUSA_DOC_MSTR` | E-invoice / receipt registry (link pickers + create-receipt) | `einvoice.entity.BusaDocMstr` |
| `CHART_ACCT_INFM` | Reporting-account dictionary (validate on PUT) | `chartofaccounts.entity.ChartAcctInfm` |
| `EXPT_RESOL_MSTR` / `EXPT_RESOL_DETL` / `EXPT_RESOL_APPRVR_INFM` / `EXPT_RESOL_FILE` | Approval master / detail / approver chain / files — written by Request Approval fan-out | `approvalbox.entity.*` |
| `APPR_LINE_INFM_DETL` | Approval-line approver chain (resolved on request-approval / change-line) | `approvalline.entity.ApprLineInfmDetl` |
| `BLTB_POST_INFM` / `BLTB_NOTI_INFM` / `BLTB_NOTI_CONT_INFM` / `BLTB_USER_NEW_POST_INFM` / `BLTB_COMNT_INFM` | Bulletin / notification fan-out on request-approval | `payment.notification.*` |
| `BANK_INFM` | Bank-code → display name (`BankCdResolver`) | `banking.entity.BankInfm` |
| `BANK_CERT_INFO` / `BANK_INFM_DETL` | Credential + scrape period-limit lookup (Update Cards) | `banking.*` |
| `AUDIT_LOGS_INFM` | Audit rows (`WSVC_ID='card-transactions'`) | `common/auditlog` |

---

## 3 · Key generation

| Table | Key | Generator | Notes |
|---|---|---|---|
| `EXPT_RESOL_MSTR` etc. | `APPR_SEQ_NO` | `wb_get_next_val` funnel (`LegacyFunctionRepository`) | Single-executor rule. |
| `BUSA_DOC_MSTR` | `DOC_ID` | `wb_get_next_val` funnel | Receipt create. |
| `CARD_TRNS_RCV_PAY_INFM` / `BUSA_DOC_RCV_PAY_HIS` | seq components | `wb_get_next_val` funnel | Link insert. |
| **`MEMO_INFM`** | `SEQ_NO` | **Java MAX+1 (documented exception)** | `MEMO_INFM` is NOT in the PROD active table list — per-`(biz_key, web_svc_id)` MAX+1, one-shot retry. APPROVED DEVIATION; confirm baseline scope with PO. |

> Per `project_wb_get_next_val_funnel.md`: all key gen funnels through `wb_get_next_val` except the documented exceptions (`MEMO_INFM` is one). Never bypass the funnel for the other tables.

---

## 4 · Migrations

- `EXPT_RESOL_*`, `BLTB_*`, `CARD_*`, `BUSA_DOC_*`, `CHART_ACCT_INFM`, `BANK_*` schemas land in their own feature/groundwork migrations — all column-for-column with DEV.
- View Setting seed: `web_svc_id='card-transactions'` with `table1` (F/M) + `table2` (H) area variants (V203-era seed; see functional-specification.md §"Column spec").
- No new DDL is owned by Card Transactions beyond seeds — every base table is in the legacy inventory. `MEMO_INFM` is the open baseline-scope item (G-CARD-3).

> Any DDL needed ships as a manual DBA-run SQL script (Flyway disabled, `ddl-auto=validate`) per the workspace DDL policy.
