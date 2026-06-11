# Daily Financial — Business Flow

All line references are to the live JEX tree (`WECMS DEV/`) unless noted.

---

## 1. User workflow

1. User opens **Daily Financial** from the Financial (FNSH) left-nav. The page loads with an
   empty body and the prompt *"Please click the 'Search' button."*
   (`rept_0015_001_view.jsp:148`).
2. The filter bar pre-fills (`rept_0015_001.ts:269-274`, `132-142`):
   - **As of** date picker → today (`load()` → `datepicker("setDate", new Date())`).
   - **Currency** radio → **All** (checked).
   - **Show account nickname** → checked.
   - **Show active accounts only** → checked.
   - **Include internal transfers** → checked.
3. User adjusts filters and clicks **Search** (`.btn_search` handler, `rept_0015_001.ts:189-210`).
4. Frontend builds the request and calls `rept_0015_001_r001` synchronously
   (`fn_listData`, `rept_0015_001.ts:276-399`).
5. On success the body reveals the sections and the script splits the result:
   - `fn_finacialStatus(data)` → currency totals (`ts:401-449`).
   - `data.REC1` filtered by `ACCT_TYPE_CD` → loan / time-deposit / current / savings renderers
     (`ts:362-385`).
   - `fn_accBank(data.REC2)` → per-bank transaction-detail tables (`ts:743-924`).
   - `fn_cash(data.REC3)` → cash-book row (`ts:697-719`).
6. Currency-scope visibility is toggled (`ts:316-358`): *All* shows both local + USD blocks;
   local-only shows the local block; any FCY/USD shows the USD block.
7. User can **drill into** a row:
   - Click a current/savings account's Bank / Account No. / nickname → opens
     `rept_0015_002.act` (transaction history) via `openLayerPopup` (`ts:146-151`).
   - Click a confirmation link in the per-bank detail → `rept_0015_003.act` (receipt/payment
     detail) (`ts:153-160`).
   - Click a cash balance → `rept_0015_004.act` (cash-book detail) (`ts:162-167`).
8. User can **Excel-export** (`.excel` handler, `ts:229-265`) — posts a JSON blob to the hidden
   `rept_0015_001_excel.jsp` iframe form — or **print** (`.page-print`, `ts:212-227`).

---

## 2. Screen flow

```
Daily Financial (rept_0015_001)
  │  Search ─────────────► rept_0015_001_r001  (read API)
  │
  ├─ click account row ──► rept_0015_002 (layer popup)  Transaction history for one account
  ├─ click confirmation ─► rept_0015_003 (layer popup)  Receipt/Payment detail (by ACCT_SEQ_NO)
  ├─ click cash balance ─► rept_0015_004 (layer popup)  Cash-book transaction detail
  │
  ├─ Excel ──────────────► view/jexGrid/file/rept_0015_001_excel.jsp  (POI .xlsx, hidden iframe)
  └─ Print ──────────────► _v2common.app.callPrintThis (client-side)
```

---

## 3. Backend orchestration flow (`rept_0015_001_r001_act.jsp`)

```
1. validatInput(util, input)                              act.jsp:41-49, 497
     REF_DATE required · ACTV_ACCT_ONLY_YN required+Y/N · INCL_INTL_TRNS_YN required+Y/N
2. idoCon.beginTransaction()                              act.jsp:500
3. getCompareAndUpdateData(...)                           act.jsp:196-205, 501
     For each of the 5 summary tables: run the R002 freshness COUNT
     (compares the live source vs the materialized summary via EXCEPT/UNION).
     If CNT != 0  →  D002 (delete day) then C002 (re-insert day):
       a. DAILY_FINCL_REPT_DAILY_SUMR_DETL            (C002 → wb_insert_daily_fincl_rept_daily_sumr_detl_data)
       b. DAILY_FINCL_REPT_DAILY_CASH_SUMR            (C002 → wb_insert_daily_fincl_rept_daily_cash_sumr_data)
       c. DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR  (C002 → INSERT…SELECT from ACCT_TRNS_HSTR)
       d. DAILY_FINCL_REPT_DAILY_CASH_SUMR_DETL       (C002 → INSERT…SELECT from CASH_REPY_MSTR, running balance)
       e. DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR (C002 → INSERT…SELECT from ACCT_TRNS_RCV_PAY_INFM)
4. REC  = getREC(...)   → DAILY_FINCL_REPT_DAILY_SUMR_DETL_R003   act.jsp:505, 387-397
     currency totals (TOTL_NORM_AMT / TOTL_SAV_AMT / TOTL_ACCT_AMT / TOTL_LOAN_AMT + TOTL_CASH subquery)
5. REC1 = getRec1(...)  → DAILY_FINCL_REPT_DAILY_SUMR_DETL_R001   act.jsp:509, 429-436
     flat per-account rows (bank, acct, ccy, type, bal, snd/rcv cnt+amt, intl variants, nickname)
6. REC2 = getRec2(...)  → DAILY_FINCL_REPT_DAILY_SUMR_DETL_R004   act.jsp:511, 439-447
     accounts + nested REC2_1 json_agg of the day's transactions (uses wb_get_usag_nm, LANG_CD)
7. REC3 = getRec3(...)  → DAILY_FINCL_REPT_DAILY_CASH_SUMR_R003   act.jsp:514, 450-457
     cash rows + json_build_object of snd/rcv counts+amounts; act post-processes OBJECT into fields
8. result.put REC / REC1 / REC2 / REC3                   act.jsp:505,538-540
9. IdoUtils.commitAndCloseQuietly(idoCon)                act.jsp:541
```

### Dynamic WHERE assembly (the filters → SQL)

The action builds `IDODynamic` fragments (`act.jsp:207-381`):

- **`getDynamicTotInfo`** (`act.jsp:279-326`) — the common filter applied to REC / REC1 / REC2:
  `biz_key = ?`, a loan-maturity guard (`acct_type_cd = LOAN → expi_dt >= ref_date`),
  `ref_date = ?`, currency (`= ?`, or `<> 'VND'` for `FCY`), and an **active-account subquery**
  against `ACCT_MSTR (actv_yn = ?)` when `ACTV_ACCT_ONLY_YN = Y`.
- **`getDynamicForSort`** (`act.jsp:233-244`) — `ORDER BY (ccy_cd <> 'USD') → 1 else 2` (local first).
- **`getDynamic1ForRec2`** (`act.jsp:207-230`) — restricts REC2 to current/savings types and
  to rows that actually transacted (`snd_cnt>0 OR rcv_cnt>0`, plus the internal variants when
  `INCL_INTL_TRNS_YN = Y`).
- **`getDynamic0ForRec2`** (`act.jsp:248-276`) — when `INCL_INTL_TRNS_YN = N`, excludes
  account-sequences whose receipt/payment usage code is an internal-transfer code
  (`4000000008`, `6000000062`, `4000000104`, `6000000061`).
- **`getDynamic0ForRec3`** (`act.jsp:351-373`) — cash filter; when `INCL_INTL_TRNS_YN = N`
  excludes `use_usag_cd = 7000000061`.

---

## 4. Data-flow diagram

```
                         ┌─────────────────────────────────────────────┐
   Operational tables    │ wb_insert_daily_fincl_rept_daily_sumr_detl_data  (batch fn)
   ──────────────────    │ wb_insert_daily_fincl_rept_daily_cash_sumr_data  (batch fn)
   ACCT_MSTR ───────────►│ INSERT…SELECT (C002 IDOs in the action)
   ACCT_TRNS_HSTR ──────►│
   ACCT_TRNS_RCV_PAY_INFM►│            (recompute, on freshness drift)
   CASH_REPY_MSTR ──────►│
                         └──────────────┬──────────────────────────────┘
                                        ▼  materializes
   Summary tables   DAILY_FINCL_REPT_DAILY_SUMR_DETL          (REC, REC1, REC2 source)
   ──────────────   DAILY_FINCL_REPT_DAILY_ACCT_TRNS_HSTR_SUMR (REC2_1 nested txns)
                    DAILY_FINCL_REPT_DAILY_TRNS_RCV_PAY_HSTR_SUMR (REC2_1 purpose names)
                    DAILY_FINCL_REPT_DAILY_CASH_SUMR           (REC3 cash)
                    DAILY_FINCL_REPT_DAILY_CASH_SUMR_DETL      (REC3 cash detail)
                                        ▼  read (R001/R003/R004)
                              rept_0015_001_r001 action
                                        ▼  JexData { REC, REC1, REC2, REC3 }
                              rept_0015_001.ts renders 4 section groups
```

**Refactor data flow (current, 2026-06 — READY FOR SIT for the in-PROD scope):** the refactor
**skips** the legacy recompute and summary tables and live-computes from in-scope operational tables
(Strategy B). `ReportService.dailyFinancial` reads `ACCT_MSTR` for account metadata, derives each
account's **as-of end-of-day balance** and **per-day debit/credit + internal counts/amounts** from
`ACCT_TRNS_HSTR` (internal split via `ACCT_TRNS_RCV_PAY_INFM` usage codes) for the selected `asOf`
date, and builds the per-currency Financial Status (VND / USD / KRW; `total = current + saving`). The
**inline** per-account transaction list (REC2_1) is deferred (P1, non-blocker) — the **Transaction
History Details popup** serves the per-account drilldown, reading `ACCT_TRNS_HSTR` directly
(`ReportService.dailyFinancialTransactions`) with the UI aligned to the e-Invoice Latest Inquiry
History popup. See [README.md §5 SIT Readiness](README.md#5-sit-readiness-2026-06),
[migration-guide.md](migration-guide.md), and [refactor-gap-analysis.md](refactor-gap-analysis.md).

> **Time column (popup):** blank by design — `ACCT_TRNS_HSTR.TRSC_TM` is empty in source for the
> affected rows; backend → mapper → UI bind `TRSC_TM` correctly (legacy is identical). Not a defect —
> see [README.md §6](README.md#6-time-column-verification-transaction-history-details-popup).
