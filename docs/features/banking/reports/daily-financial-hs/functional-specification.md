# Daily Financial (HS) — Functional Specification

> Behaviour of the refactor implementation, with the legacy `rept_0017_001` rule cited where the
> refactor mirrors it. UI-side detail (layout, tokens, components) lives in the frontend package:
> [wecms-web/docs/features/banking/reports/daily-financial-hs/ui-specification.md](../../../../../../wecms-web/docs/features/banking/reports/daily-financial-hs/ui-specification.md).

---

## 1. Search conditions (filter row)

| UI control | Wire param | Default | Sent to read API? | Notes |
|---|---|---|---|---|
| As-of date (`DateField`, display `DD-MM-YYYY`) | `asOf` (`yyyyMMdd`) | today | yes | Blank/absent → backend normalises to today (`ReportService.dailyFinancialHs`). Legacy: `PERIOD_DATE`, default today (`rept_0017_001.ts` page load). |
| Bank/account multi-select | `accountFilter` | `"ALL"` | yes | Empty selection → literal string `ALL`; otherwise a JSON array `[{"BANK_CD":"…","ACCT_NO":"…"}, …]` — verbatim legacy `rept_0017_001.js` contract. Options are derived client-side from the `ALL` report's HEADER rows (divergence from legacy `cash_0001_001_r003` — see [legacy-spec.md §6](legacy-spec.md#6-intentional-differences)). |
| Search button | — | — | trigger | Date/filter changes update state only; only Search refetches (legacy parity — auto-search commented out in legacy TS). |

There is **no keyword search field**, no currency-scope toggle, no active-only/internal-transfer
toggles (those belong to the CMC variant).

## 2. Buttons & actions

| Button | Location | Behaviour |
|---|---|---|
| Search | filter row | Re-runs `GET /daily-financial-hs` with current `asOf` + `accountFilter`. Disabled while loading. |
| List icon (View Setting) | title row | Opens the shared `<ViewSettingPopover>` for screenId `banking-reports-daily-financial-hs` / area `default` (12 columns). Save = PUT, Reset = DELETE on `/api/v1/screen-options/...`. |
| Download Excel | title row | `GET /daily-financial-hs/report?asOf&accountFilter&lang` → XLSX blob. Always exports **all 12 columns** regardless of View Setting (legacy `_r002_excel` has no visibility logic — confirmed in the final audit). |

No Print button, no pagination, no sorting (V249 seeds `sort_yn='N'` on every column; the page
does not wire the sort triplet — legacy parity).

## 3. Report body — rows, grouping, totals

Row stream (ordered by the DB) per account within each currency section:

| Row type | Rendered as | Amounts carried |
|---|---|---|
| `HEADER` | opens a bank/account group; bank label + account number (+ optional `Due date:` / `BRANCH` text embedded in the header string by the function) | none (nulls) |
| `BEFORE_BALANCE` | opening-balance line — **no input boxes** (fix 3: editable inputs render only on TRANSACTION rows); code/description cells blank | `before_balance` only |
| `TRANSACTION` | one ledger line; 5 editable cells | `receipt` / `payment` / `balance` |
| `ACCOUNT_TOTAL` | blue **SUB TOTAL** row per account | `before_balance`, `receipt`, `payment`, `balance` |
| `CURRENCY_TOTAL` | green **TOTAL** row closing the currency section (VND → USD → KRW) | same 4 + `balance_company_actual` → Memo column |

Rules:

- **All figures come from the DB function** — the backend performs no aggregation, no running
  balance, no currency conversion (there is none in this report; amounts stay in the account's
  native currency).
- Currency sections are ordered VND → USD → KRW → other; each section's TOTAL renders immediately
  after its last account group (currency-first ORDER BY — load-bearing, see
  [business-flow.md §3.1](business-flow.md#31-read-get-apiv1bankingreportsdaily-financial-hs)).
- Null amounts stay `null` on the wire and render as **blank cells, never "0"** (legacy parity).
- Exactly one row per data row — no spacer/blank separator rows (fix 11; legacy tbody parity
  `ΣTᵢ + 2A + C`).

## 4. Editable cells — per-field save

| Field | Wire key | DB column | Max length |
|---|---|---|---|
| Receipt Code | `rept_cd` | `REPT_CD` | 20 |
| Payment Code | `pymn_cd` | `PYMN_CD` | 20 |
| Description (English) | `desc_nm_en` | `DESC_NM_EN` | 255 |
| Description (Korean) | `desc_nm_ko` | `DESC_NM_KO` | 255 |
| Memo | `memo` | `MEMO` | 500 |

Rules (all verified in `DailyFinancialHsWriteService` + `DailyFinancialHsWriteServiceTest`):

1. **Only TRANSACTION rows are editable** — the frontend renders inputs only when
   `entry.editable` (BEFORE_BALANCE rows carry `acct_seq_no=""` and are non-persistable).
2. Save fires **per field on blur or Enter**, and only when the value actually changed
   (frontend `InputCell` change-tracking). Mirrors legacy `rept_0017_001_u001`
   (`{ACCT_SEQ_NO, FIELD_ID, FIELD_VAL}` one-field saves).
3. Partial-update semantics: `null` = leave unchanged, `""` = clear the column.
4. Values are sanitized (non-breaking-space strip — port of legacy `StringUtils.sanitize`,
   `ReptTrnsHstr.java:156-160`).
5. `MOD_ID` = current user, `MOD_DTM` = now; **amount/balance columns are never touched** (they
   are not even mapped on the entity).
6. Unknown `acctSeqNo` (or another tenant's row) → `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND`
   (single envelope merges "not found" + "wrong tenant"; legacy threw `WBE0404`).
7. No refetch after save — the persisted value appears in context (subtotals etc.) on the next
   Search; totals on screen are **not** recomputed client-side (legacy parity — legacy subtotals
   also refresh only on re-search since the function computes them).

## 5. Validation

| Rule | Where | Failure |
|---|---|---|
| `rept_cd`/`pymn_cd` ≤ 20, `desc_nm_en`/`desc_nm_ko` ≤ 255, `memo` ≤ 500 | `@Size` on `DailyFinancialHsEntryUpdate` record components (`@Valid @RequestBody`) | HTTP 200 + `WBE_VALIDATION` via `GlobalExceptionHandler` |
| `asOf` format | the DB function validates `^\d{8}$` and raises on bad format (repository Javadoc); blank is normalised to today before the call | DB error → HTTP 500 `WBE9999` (no dedicated 4xx — *no explicit Java-side format guard exists*) |
| `accountFilter` shape | passed through verbatim (`ALL` or JSON string); parsed inside the function | malformed JSON → function-side error → `WBE9999` |
| Tenant present | `TenantContext.currentBizKey()` asserted non-null in both services | `IllegalStateException` → `WBE9999` (unreachable for authenticated calls) |

## 6. Empty result & error handling

- **Empty ledger:** `ReportService.dailyFinancialHs` returns `rows: []`; the frontend renders the
  empty state; `DailyFinancialHsDownloadService` generates a template-only workbook without
  throwing (`emptyReportDoesNotThrow` test).
- **Row not found on save:** `WBE_DAILY_FINANCIAL_HS_ENTRY_NOT_FOUND` (messages.properties:
  "Daily Financial entry not found."), HTTP 200 business-error envelope.
- **Template/IO failure on download:** wrapped as `UncheckedIOException` → HTTP 500 `WBE9999`.
- **Unauthenticated / revoked JWT:** 401 via `JwtAuthenticationEntryPoint`; **wrong product
  type / missing grant:** 403 `WBE1003` via `@menuAuthz`.

## 7. Audit & logging

- **No `AUDIT_LOGS_INFM` writes** on any of the three endpoints — matches legacy `rept_0017_001`
  (no documented audit path). The save path's audit trail is the entity-level `MOD_ID`/`MOD_DTM`
  stamp only.
- **No `ERP_TRNS_HSTR` 'W' row** on the Excel download — the download is not an ERP-bound export
  and legacy writes none. (Deliberate contrast with the Payment/Approval-Box exports.)
- Download logs at INFO: `feature=daily-financial-hs report-download template={lang} asOf={date}
  filter={filter} rows={count}`.

## 8. Currency handling

- No exchange-rate logic exists anywhere in this feature — amounts render in each account's
  native currency, and per-currency TOTAL rows are computed by the DB function. (Any
  exchange-rate content in other reports does not apply here; documenting one would be invention.)
- Refactor includes **KRW** (and any other currency the function returns); legacy filtered
  `CURRENCY IN ('VND','USD')` — intentional divergence, PO-approved
  ([legacy-spec.md §6](legacy-spec.md#6-intentional-differences)).
- Excel number formats: `#,##0` for VND/KRW/JPY, `#,##0.00` otherwise (legacy parity).
