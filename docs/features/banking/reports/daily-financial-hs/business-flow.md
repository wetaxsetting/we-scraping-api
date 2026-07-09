# Daily Financial (HS) — Business Flow

> Backed by `wecms-api` + `wecms-web` source and the legacy tree. Legacy citations use
> `file:line`; refactor citations name the class/method. See
> [source-code-index.md](source-code-index.md) for full paths.

---

## 1. User workflow

1. A HAENGSUNG (`PRDT_TYPE='H'`) user opens **Banking → Reports → Daily Financial (HS)**
   (`/banking/reports/daily-financial-hs`). The menu node exists only in the H product tree
   (permission cd `banking-reports-daily-financial-hs`).
2. The page **renders eagerly** — the report loads on mount for `asOf = today`,
   `accountFilter = "ALL"` (legacy parity: `rept_0017_001.ts` runs `fn_getData()` on page load
   with today's date and all bank checkboxes checked).
3. The user may change the **As-of date** and the **bank/account multi-select** — changing either
   updates state only; the report **refetches only when Search is clicked** (legacy parity:
   auto-search is commented out in the legacy TS).
4. The user edits an editable cell on a **TRANSACTION row** (Receipt Code / Payment Code /
   Description EN / Description KO / Memo). On **blur or Enter** the single field is saved
   immediately (`PATCH /entries/{acctSeqNo}`). No Save button exists.
5. The user may open the **View Setting popover** (list icon) to hide/reorder the 12 columns —
   persisted per user via the shared screen-options API.
6. The user clicks **Download Excel** — the backend re-runs the read function (fresh DB data,
   including saved edits) and streams a localized template-populated XLSX.

## 2. Screen flow

```
Sidebar: Banking → Reports → Daily Financial (HS)
        │
        ▼
/banking/reports/daily-financial-hs            (eager render — no empty-state Search gate)
  ├── Title row: "Daily Financial (HS)" + [list icon → View Setting popover] + [Download Excel]
  ├── Filter row: As-of DateField · Bank multi-select · [Search]
  └── <ReportLayout> — one bank-grouped ledger table
        ├── per account: HEADER → BEFORE_BALANCE → TRANSACTION× → SUB TOTAL (blue)
        └── per currency: TOTAL (green) closes the VND / USD / KRW section
```

There are no popups/dialogs beyond the shared View Setting popover and the shared `useDialog()`
error dialog. (The CMC variant's Transaction History popup does **not** exist here.)

## 3. Backend orchestration flow

### 3.1 Read (`GET /api/v1/banking/reports/daily-financial-hs`)

```
ReportController (@menuAuthz banking-reports-daily-financial-hs)
        │  asOf (yyyyMMdd, blank → today) · accountFilter ("ALL" | JSON array, blank → "ALL")
        ▼
ReportService.dailyFinancialHs(asOf, accountFilter)      @Transactional(readOnly = true)
  1. bizKey = TenantContext.currentBizKey()   (JWT-bound; IllegalStateException if absent)
  2. normalise asOf → today when blank; accountFilter → "ALL" when blank
        ▼
DailyFinancialHsReportRepository.fetchReport(bizKey, periodDate, accountFilter)
  — the SINGLE executable call site of the legacy function (native query):
  SELECT … FROM fn_get_haengsung_daily_financial_report(:bizKey, :periodDate, :accountFilter)
  ORDER BY CASE currency VND→1, USD→2, KRW→3 ELSE 99 END,
           account_type_order, bank_name_sort, account_number NULLS LAST, transaction_seq
        ▼
DailyFinancialHsRow (interface projection, 22 columns)
        ▼
ReportService — project rows → DailyFinancialHsReport.LedgerRow
  · BigDecimal amounts → wire strings via AmountFormatter.wire(...); NULL preserved (never "0")
        ▼
ApiResponse.ok(DailyFinancialHsReport)   → wire: { as_of_dt, account_filter, rows[] }
```

The leading currency key in the ORDER BY is **load-bearing**: the function emits `CURRENCY_TOTAL`
rows with `account_type_order = 999990/999991/999992`; without the currency key all grand-total
rows sort to the stream end instead of closing their own currency section (final audit §4 fix 8;
mirrors legacy `IDO.DAILY_FINCL_REPT_R001.xml:54-64`).

### 3.2 Per-field save (`PATCH /api/v1/banking/reports/daily-financial-hs/entries/{acctSeqNo}`)

```
ReportController (@menuAuthz, @Valid DailyFinancialHsEntryUpdate)
        ▼
DailyFinancialHsWriteService.updateEntry(acctSeqNo, update)   @Transactional
  1. bizKey = TenantContext.currentBizKey()
  2. ReptTrnsHstrRepository.findByBizKeyAndAcctSeqNo(bizKey, acctSeqNo)
       └─ empty → BusinessException("WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND")
  3. apply ONLY non-null fields (null = unchanged, "" = clear); each value sanitize()d (NBSP strip)
  4. MOD_ID ← TenantContext.currentUserId(); MOD_DTM ← BaseEntity @PreUpdate
  5. repo.save(entity)                       → UPDATE REPT_TRNS_HSTR … WHERE PK
```

Mirrors legacy `rept_0017_001_u001_act.jsp:66-99` (`FIELD_ID`/`FIELD_VAL` if-chain →
`ReptTrnsHstrDAO.patch` → IDO `REPT_TRNS_HSTR_U000`), except the refactor DTO can carry several
fields in one call; the frontend sends one field per blur/Enter, matching legacy.

### 3.3 Excel download (`GET /api/v1/banking/reports/daily-financial-hs/report`)

```
ReportController (@menuAuthz)  asOf · accountFilter · lang (EN|KO|VI, default EN)
        ▼
DailyFinancialHsDownloadService.generate(asOf, accountFilter, lang)
  1. report = ReportService.dailyFinancialHs(asOf, accountFilter)   ← FRESH DB read
  2. load classpath template templates/reports/daily-financial-hs/
       WeCMS_Daily_Financial_Report_{EN|KO|VI}.xlsx   (POI WorkbookFactory)
  3. writePeriodDate  → A2 (centred) + L3 (right)  — A1 title is TEMPLATE-OWNED, never written
  4. setColumnWidths  → legacy 12-column widths (30/10/20/20/25/25/30/18/18/18/18/30 × 256)
  5. populate rows from index 5: HEADER · BEFORE_BALANCE · TRANSACTION · SUB TOTAL (cyan,
     C:F merged) · TOTAL (grey, A:F merged); bank/ccy vertical merge bounded to
     [first data row .. ACCOUNT_TOTAL] — CURRENCY_TOTAL rows never absorbed (fix 7)
  6. number formats: VND/KRW/JPY #,##0 · others #,##0.00
        ▼
GeneratedReport(bytes, "WeCMS_Daily_Financial_Report_{LANG}.xlsx")
        ▼
HTTP 200 · Content-Type xlsx · Content-Disposition attachment; filename*=UTF-8''…
```

Because step 1 re-runs the read function, the workbook reflects **saved DB data** — the same rule
as legacy `rept_0017_001_r002_excel_view.jsp` (unsaved on-screen edits are not exported).

## 4. Data-flow diagram

```
 Next.js  DailyFinancialHsClient
   │  GET  /daily-financial-hs?asOf&accountFilter          (mount + Search)
   │  GET  /daily-financial-hs?accountFilter=ALL           (mount only → bank dropdown options)
   │  PATCH /daily-financial-hs/entries/{acctSeqNo}        (per-field, blur/Enter)
   │  GET  /daily-financial-hs/report?asOf&accountFilter&lang  (Excel blob)
   ▼
 wecms-api  ReportController → {ReportService | DailyFinancialHsWriteService | DailyFinancialHsDownloadService}
   │                                   │
   │ read: fn_get_haengsung_daily_financial_report(…)      │ write: UPDATE REPT_TRNS_HSTR (PK patch)
   ▼                                   ▼
 PostgreSQL (shared dev DB / live JEX schema)  — REPT_TRNS_HSTR is both the function's base table
                                                 and the write target of the 5 editable columns
```

## 5. Refactor data flow — what changed vs legacy

- The JSP action / IDO / `JexData` envelope is replaced by the three Spring endpoints; the DB
  function is unchanged and remains the **only** computation engine (no Java-side math).
- The legacy IDO's `CURRENCY IN ('VND','USD')` filter is **deliberately dropped** — the refactor
  includes KRW and any other currency (PO-approved 2026-07-02; documented in the repository
  Javadoc and [legacy-spec.md §6](legacy-spec.md#6-intentional-differences)).
- The legacy bank-list source (`cash_0001_001_r003`) is replaced on the frontend by deriving
  dropdown options from the unfiltered (`ALL`) report's HEADER rows.
- Excel stays template-populate; EN/VI template titles were localized on PO request (KO template
  remains byte-identical to legacy).
