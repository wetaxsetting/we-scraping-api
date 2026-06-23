# Corporate Card — Functional Specification (Card Transactions)

What the Card Transactions sub-module does, backend-side: filters, totals, list/detail, columns, update/sync, validation, rules.

---

## 1 · Filters (list `GET /`)

| Filter | Wire param | Behaviour | Required |
|---|---|---|---|
| Date range | `from_date`, `to_date` (`yyyyMMdd`) | `TRSC_DT BETWEEN` | **Yes** (`@Pattern ^\d{8}$`) |
| Keyword | `srch_wd` | `ILIKE` on `MERCH_NM` / `CARD_NM` / `CARD_NICK_NM` / `CARD_HLDR_NM` | No |
| Card companies | `bank_cd` (repeated) | `CTH.bank_cd IN (...)` | No |
| Cards | `card_no` (repeated) | `CTH.card_no IN (...)` | No |
| Link status | `link_yn` (`Y`/`N`/blank) | linked (`REC_1 IS NOT NULL`) / unlinked / all | No |
| Sort | `sort_field` + `sort_direction` | `CardTrnsHstrSortField` whitelist | No |
| Page | `page` (default 0), `size` (default 15, max 200) | server-side | No |

Default ORDER BY: `TRSC_DT DESC, TRSC_TM DESC, CARD_SEQ_NO DESC` (legacy `SortHelper` baseline).

## 2 · Summary / totals cards

`CardTransactionTotals` ships with the list response (computed from `CARD_TRNS_COUNT_R001` parity logic):

- **TOTAL** — `total_count` rows · `totl_amt`
- **APPROVAL REQUESTED** — `totl_appr_req_cnt` · `totl_appr_req_amt`
- **APPROVAL NOT REQUESTED** — `totl_appr_not_req_cnt` · `totl_appr_not_req_amt`

## 3 · List / detail

- List → `CardTransactionListResponse { page, page_size, total_count, totals, rows[] }`.
- Detail drill-ins are separate endpoints: `GET /by-approval/{apprSeqNo}` (Approval Details grid), `GET /receipts/{docId}` (linked-doc detail), `GET /memos` (side panel). The ledger row itself carries `linked_docs[]` (badge) and `appr_doc_no` (Approval Ref).

## 4 · Columns (View Setting seed)

`WEB_SVC_ID='card-transactions'` with two area variants:

- **`table1`** (default, PRDT_TYPE F / M) — 16 columns including `CHECKBOX_1` (special, locked), `TRSC_DT`, `CARD_NO`, `MERCH_NM`, `CARD_NICK_NM`, `CARD_HLDR_NM`, `CCY_CD`, `TRSC_AMT`, `CHART_ACCT_NM` (reporting account), `STTS`, `CARD_TRNS_USAGE_NM` (purpose), `DISP_DOC_ID` (link ref), `CONT` (description), `APPR_DOC_NO` (approval ref), `MEMO` (special, never sortable).
- **`table2`** (HAENGSUNG, PRDT_TYPE H) — 14 columns with `_2`-suffixed FIELD_IDs; `CARD_TRNS_USAGE_NM_2` and `CHART_ACCT_NM_2` absent.

Special FIELD_IDs `CHECKBOX_1/2` + `MEMO/MEMO_2` are admin-controlled — locked by `ScreenOptionService.SPECIAL_FIELD_IDS`, never in the sort whitelist.

Sortable FIELD_IDs (whitelist enum): `TRSC_DT`, `CARD_NO`, `MERCH_NM`, `CARD_NICK_NM`, `CARD_HLDR_NM`, `CCY_CD`, `TRSC_AMT`, `CHART_ACCT_NM`, `STTS`, `CARD_TRNS_USAGE_NM`, `CONT`. Projection-only columns (`DISP_DOC_ID`, `APPR_DOC_NO`) are not sortable.

## 5 · Update / sync

- **Classify** — `PATCH /{cardSeqNo}` (`{field:"description"|"purpose"}`); `PUT|DELETE /{cardSeqNo}/reporting-account`.
- **Card management** — activate / rename / delete / bulk-create.
- **Scrape** — `/sync` (deprecated headless), `/sync-session` (+ challenge resume), `/period-limits`.

## 6 · Validation & rules

- Date range required (`@Pattern ^\d{8}$`); `link_yn ∈ "" / Y / N`.
- `card_seq_no` path: `^[0-9]{1,20}$`; `bank_cd`: `^[0-9A-Za-z]{1,20}$`; `card_no`: `^[0-9A-Za-z*\-]{1,100}$`; `appr_seq_no`: `^[0-9]{1,15}$`.
- Reporting-account PUT validates `(seqNo, code)` against an active `CHART_ACCT_INFM` row → else `WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID`.
- Card delete in-use guard → `WBE1153`.
- Link amount: `SUM(dedu_amt) == TRSC_AMT` exact → else `WBE_LINK_AMOUNT_MISMATCH`.
- Memo delete restricted to author → `WBE_MEMO_NOT_AUTHOR`.
- Request Approval fan-out stamps `APPR_DV='33'` (CORPORATE_CARD); `APPR_SEQ_NO` via the funnel.
- Tenant scope: every read/write constrains on `TenantContext.currentBizKey()`.

## 7 · Status field

`PRCH_STTS` is a single-char code; wire ships raw `stts` + `stts_key` (`payment.card_transactions.status.*`). Only `'1'` + null observed in DEV (OQ-3 — full value set pending PO; frontend falls back to the raw code on unknown values).
