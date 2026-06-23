# Cash Flow — Interface Specification

Field-by-field contract for the legacy APIs and the refactor REST endpoints.

---

## A. Legacy JEX APIs

### A1 — `cash_0001_001_r001` (List Cash Flow Data)

- Endpoint `cash_0001_001_r001.jct`; action [cash_0001_001_r001_act.jsp](../../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/cash/cash_0001_001_r001_act.jsp);
  contract [WSVC.cash_0001_001_r001.xml](../../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.cash_0001_001_r001.xml); IDO `CASH_FLOW_R001` (pass-through `SELECT … FROM get_cashflow_report(...)`).
- `<login>N</login>`. Validation: `DATA_YEAR`, `BASE_CCY_CD`, `USD_RATE_AMT`, `KRW_RATE_AMT` required.

**Inputs**

| Field | Type | Len | Req | Description |
|---|---|---|---|---|
| `DATA_YEAR` | char | 4 | ✅ | report year (`2025`) |
| `YEAR_QUAT` | char | 2 | — | quarter `Q1..Q4`; live UI always sends `null` |
| `BASE_CCY_CD` | char | 3 | ✅ | `VND`/`USD`/`KRW` |
| `USD_RATE_AMT` | NUMBER | 20 | ✅ | VND per 1 USD |
| `KRW_RATE_AMT` | NUMBER | 20 | ✅ | VND per 1 KRW |
| `BANK_FILT` | RECORD | — | — | array of `{BANK_CD(20), ACCT_NO(100)}` (JSON-stringified by TS) |

**Output — `REC` (RECORD list)**

| Field | Type | Len | Description |
|---|---|---|---|
| `DISP_ORD` | int | 5 | sort_order (0 Opening, 1 L1, 2 L2, 3 L3, 4 Subtotal, 5 SalesProfit, 6 EndBalance) |
| `DISP_CD` | str | 255 | display code |
| `DISP_NM` | str | 255 | display name (L1/L2 `ko en`; L3 `ko`) |
| `ITEM_CD` | str | 10 | leaf `chart_acct_cd` (L3 only) |
| `ITEM_NM` | str | 255 | leaf `chart_acct_nm_en` (L3 only) |
| `ROW_TYPE` | str | 255 | one of the seven verbatim values |
| `GRP_CD` | str | 500 | group_key |
| `JAN_AMT`..`DEC_AMT` | NUMBER | 20 | 12 monthly amounts in `BASE_CCY_CD` |
| `TOTAL_AMT` | NUMBER | 20 | annual total (0 for Opening) |
| `DISP_CCY_CD` | str | 3 | = `BASE_CCY_CD` |
| `PARENT_L2_CD` | str | 255 | L2 parent code for L3 rows |

> WSVC declares `JAN_AMT`/`FEB_AMT` **without** `typeIdo="NUMBER"` while `MAR_AMT..DEC_AMT`/`TOTAL_AMT`
> carry it — Jan/Feb transport as strings (legacy inconsistency **G12**). The refactor DTO uses
> `BigDecimal` throughout, so this is already resolved.

### A2 — `cash_0001_001_r002` (Exchange Rate)

Endpoint `cash_0001_001_r002.jct` → IDO `BANK_EXCH_RATE_INFM_R002`. Inputs `BANK_CD` (TS sends Shinhan
`11704088`), `SEQ_NO` (TS sends `2`). Output `REC_OUT`: `BANK_CD, REF_DATE, REF_CCY_CD, CCY_CD,
TRAN_SELL_AMT, TRAN_BUY_AMT, BUY_AMT, SELL_AMT`. Selection: latest weekday `REF_DATE ≤ today`, prefer
`SEQ_NO='2'`. TS reads `TRAN_SELL_AMT` per `REF_CCY_CD` to prefill `#exchange-rate`.

### A3 — `cash_0001_001_r003` (Bank list)

Endpoint `cash_0001_001_r003.jct` → `AcctMstrDAO.getRows`. No inputs. Output `REC`: `BANK_CD, BANK_NM,
ACCT_NO`, filtered `actv_yn='Y'` + tenant + Haengsung hide-closed clause.

### A-envelope

```json
{ "COMMON_HEAD": { "ERROR": false, "CODE": "...", "MESSAGE": "..." }, "REC": [ ... ] }
```

---

## B. Refactor REST API

Wire format **lowercase snake_case** (global Jackson `SNAKE_CASE`); standard `{ status, data }` envelope.
All endpoints **JWT-authenticated** (tenant from the signed JWT, never the wire).

### B1 — `GET /api/v1/banking/reports/cash-flow-report` (search)

[CashFlowReportController.search](../../../../../src/main/java/com/kosign/wecms/domain/banking/controller/CashFlowReportController.java)
→ `CashFlowReportService.search(...)`.

| Param | Type | Required | Legacy field | Notes |
|---|---|---|---|---|
| `year` | int | ✅ | `DATA_YEAR` | 1900–2999 |
| `quarter` | Integer 1–4 | — | `YEAR_QUAT` | validated + echoed; **does NOT affect the calculation** (full Jan–Dec always — G5/D-1) |
| `baseCurrency` | `VND`/`USD`/`KRW` | ✅ | `BASE_CCY_CD` | |
| `usdRate` | BigDecimal | conditional | `USD_RATE_AMT` | required (> 0) when `baseCurrency != USD` — **D2** |
| `krwRate` | BigDecimal | conditional | `KRW_RATE_AMT` | required (> 0) when `baseCurrency != KRW` — **D2** |
| `bankFilters` | URL-encoded JSON `[{bank_cd,acct_no}]` | — | `BANK_FILT` | malformed JSON → `WBE_VALIDATION` (parsed in controller) |

**Response** — `CashFlowReportResponse`
([CashFlowReportResponse.java](../../../../../src/main/java/com/kosign/wecms/domain/banking/dto/CashFlowReportResponse.java)):

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "year": 2025, "quarter": null, "base_currency": "VND",
    "usd_rate": "26000", "krw_rate": "19",
    "rows": [
      { "display_order": "00000", "display_cd": "Opening Balance", "display_nm": "Opening Balance",
        "item_cd": null, "item_nm": null, "row_type": "Opening Balance",
        "group_cd": "AAAAA-OPENING-BALANCE", "parent_l2_cd": null, "display_currency": "VND",
        "monthly": { "jan": "0", "feb": "55000", "...": "..." }, "annual_total": "0" },
      { "display_order": "00100", "display_cd": "ACC-01", "display_nm": "수입 REVENUE",
        "row_type": "Level 1", "group_cd": "ACC-01", "display_currency": "VND",
        "monthly": { "jan": "55000", "...": "..." }, "annual_total": "2510000" }
    ]
  }
}
```

DTO records: `CashFlowReportResponse(year, quarter, baseCurrency, usdRate, krwRate, List<CashFlowRow>
rows)`; `CashFlowRow(displayOrder, displayCd, displayNm, itemCd, itemNm, rowType, groupCd, parentL2Cd,
displayCurrency, MonthlyAmounts monthly, annualTotal)`; `MonthlyAmounts(jan…dec)` + `zero()`;
`BankFilter(@NotBlank bankCd, @NotBlank accountNo)`. No per-field `@JsonProperty`.

### B2 — `POST /api/v1/banking/reports/cash-flow-report/export` (Excel)

[CashFlowReportController:export](../../../../../src/main/java/com/kosign/wecms/domain/banking/controller/CashFlowReportController.java).
Body: the shared [`ExcelExportRequest`](../../../../../src/main/java/com/kosign/wecms/common/export/ExcelExportRequest.java)
(`filters` map carries `year`, `quarter`, `baseCurrency`, `usdRate`, `krwRate`, `bankFilters`).

> **Deliberate divergence from the shared export pipeline:** unlike Account Inquiry, this endpoint
> **bypasses** the generic `ExcelExportService`. It re-runs the same `service.search(...)` and hands the
> rows to [`CashFlowExcelWriter`](../../../../../src/main/java/com/kosign/wecms/domain/banking/export/CashFlowExcelWriter.java),
> a dedicated POI **XSSF** writer that owns the grouped/merged layout end-to-end (the request's
> `columns`/`sort` are ignored — the report's column shape is fixed). A
> [`CashFlowReportExportProvider`](../../../../../src/main/java/com/kosign/wecms/domain/banking/export/CashFlowReportExportProvider.java)
> (`screenId="cash-flow-report"`) is also registered for the generic dispatcher / View-Setting
> visibility wiring, but the **live** `/export` path is the writer. Response: `200`,
> `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, RFC 5987 UTF-8
> `Content-Disposition` filename (`.xlsx` appended). Note: export filter keys are **camelCase** inside
> the otherwise snake_case API (parity audit N6) — harmless contract wrinkle for non-UI clients.

### B-errors

| Code | When |
|---|---|
| `WBE_CASH_FLOW_RATE_REQUIRED` (200) | missing/≤0 rate for a non-base currency (arg = `USD`/`KRW`) |
| `WBE_VALIDATION` (200) | year out of range, bad quarter, malformed `bankFilters` JSON, bad enum |
| `401` | JWT missing/invalid (`JwtAuthenticationEntryPoint`) |
| `WBE9999` (500) | unexpected fault (global handler) |

---

## C. Legacy → refactor endpoint map

| Legacy | Refactor |
|---|---|
| `cash_0001_001_r001` + IDO `CASH_FLOW_R001` + fn `get_cashflow_report` | `GET /api/v1/banking/reports/cash-flow-report` (calls `get_cashflow_report` directly via `CashFlowReportRepository`, 2026-06-13; the earlier Java reimpl is retained but off the active path) |
| client ExcelJS export | `POST .../export` → `CashFlowExcelWriter` (server POI) |
| `cash_0001_001_r002` (FX rate) | `GET /api/v1/banking/exchange-rates` (selection logic differs — N3) |
| `cash_0001_001_r003` (bank list) | `GET /api/v1/banking/accounts` (Haengsung hide-closed not ported — N4) |
