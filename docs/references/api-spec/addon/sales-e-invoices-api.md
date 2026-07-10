# Add-on Service > Sales e-Invoices — Backend API Design

**Module:** Add-on Service
**Spring domain package:** `com.kosign.wecms.domain.einvoice`
**Refactor screen ID (proposed):** `addon-sales-einvoices`
**JEX counterpart:** `einv_0001_001_r001` (read service) — `DOC_TYPE='I'` slice of `BUSA_DOC_MSTR`
**Feature spec:** [../../features/addon-service/sales-e-invoices.md](../../../features/addon-service/sales-e-invoices.md)
**Status:** Skeleton implemented (controller + service + entity + repository + DTOs); filters / pagination / sort / Excel export NOT yet implemented.

JEX is **read-only reference**.

> **Phase 2 implementation rules are locked.** See planning § Phase 2 implementation rules (locked decisions) _(removed — historical `planning/` folder deleted by project decision)_. Backend implementation is approved to start. The relevant rules for this API: IR-1 (`SALES → 'I'`, `EXPENSE → 'B'` — fix the current `'E'` mapping bug), IR-2 (always-on baseline filters: `PROV_STTS NOT IN ('VD','EE')`, `HAND_REG_YN <> 'Y'`, `BUSA_DOC_DETL` exists, `OP` expansion), IR-3 (`from`/`to` required + `YYYYMMDD` validation), IR-4 (return raw `EINV_INVOICESTATUS`; do NOT port `wb_get_invoice_status` in Phase 2), IR-5 (5-column simplified `keyword` search), IR-6 (multi-currency totals computed unpaged), IR-7 (drop `INV_TYPE` from `EInvoiceRow`/`EInvoiceListResponse`), IR-9 (View Setting endpoints stay global / unchanged; the Sales e-Invoices page does NOT generate Save or Reset traffic — only the lazy-init GET).

---

## Endpoints

### Implemented today

| Method | Path | Returns | Notes |
|---|---|---|---|
| GET | `/api/v1/addon/einvoices` | `ApiResponse<EInvoiceListResponse>` | One endpoint serves Sales (`type=SALES`) and Expenses (`type=EXPENSE`); Spring resolves to `DOC_TYPE` server-side. Optional `from` / `to` (`YYYYMMDD`) for `ISSU_DT` range. |

### Planned (not yet implemented)

| Method | Path | Returns | Purpose |
|---|---|---|---|
| GET | `/api/v1/addon/einvoices` | `ApiResponse<EInvoiceListResponse>` *(extended)* | Add `invoiceStatus`, `keyword`, `page`, `size`, `sortField`, `sortDirection` query params. |
| POST | `/api/v1/addon/einvoices/export` | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (XLSX stream) | Per-resource Excel export per [§ Excel export](../../../../CLAUDE.md). New `EInvoiceExportProvider implements ExportRowProvider` registers `screenId="addon-sales-einvoices"`. |

Auth: every endpoint inherits the JWT-protected catch-all from [`SecurityConfig`](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java). Public bypass is limited to `/api/v1/auth/*` + `/actuator/health`.

---

## Request parameters

### `GET /api/v1/addon/einvoices`

| Param | In | Type / shape | Required | Today | Planned semantics |
|---|---|---|---|---|---|
| `type` | query | `"SALES" \| "EXPENSE"` (the controller currently also accepts the raw legacy `"I"` / `"E"` aliases — drop the aliases; the `E` alias is wrong, replace with `B`) | ✅ | Maps to `DOC_TYPE` server-side. **Today (BUG):** `SALES` → `'I'`, `EXPENSE` → `'E'`. **Legacy correct mapping:** `SALES` → `'I'`, `EXPENSE`/`BILL` → `'B'` (per [`BusaDocType.java`](../../../../../WECMS%20DEV/WECMS/src/wabooks/enums/BusaDocType.java) and the action-JSP `validateInput`). Legacy listing rejects `'E'` and `'S'` outright. Unknown / blank → `BusinessException("WBE2002")`. | Fix mapping to `EXPENSE` → `'B'`. Same validation. |
| `from` | query | `String` (`YYYYMMDD`) | ❌ today / ✅ legacy | When both `from` and `to` are non-blank, applies `WHERE ISSU_DT BETWEEN ? AND ?`; otherwise no date filter. | **Make required** for legacy parity — `validateInput` rejects requests without `FROM_DT` and `TO_DT`. |
| `to` | query | `String` (`YYYYMMDD`) | ❌ today / ✅ legacy | See `from`. | Make required (see `from`). |
| `invoiceStatus` | query | `String` — value of `EINV_INVOICESTATUS` (e.g. `"Original"` / `"Hóa đơn mới"` / `"세금계산서"` depending on the localisation strategy) | — | **Not implemented.** | `WHERE EINV_INVOICESTATUS = ?` exact match. Empty / missing = no filter. Mirrors legacy `einv_invoiceStatus` (length 500). **Open: localisation strategy** — see "Invoice status mapping" below. |
| `keyword` | query | `String`, length ≤ 100 | — | **Not implemented.** | Mirrors legacy `SRCH_WD` (length 100). Legacy is **View-Setting-driven** with per-field predicate variation across 16 candidate columns — see "Search behaviour" below. Phase 2 picks: replicate legacy verbatim, ship a fixed subset, or hybrid. |
| `page` | query | `Integer ≥ 0` | — | **Not implemented** (service returns the full filtered list). | Zero-based page index (Spring Data `Pageable`). Default `0`. |
| `size` | query | `Integer ≥ 1` | — | **Not implemented.** | Page size. Default `10` (`PAGE_SIZE_OPTIONS[0]`). Backend clamps to `MAX_PAGE_SIZE = 100`. |
| `sortField` | query | `String` — View Setting `FIELD_ID` (`DATE_TIME`, `INVOICE_NO`, `BUYER`, `CURRENCY`, `TOTAL`, `STATUS`, `TAX`, `PRETAX`) | — | **Not implemented.** | Resolved through the new `EInvoiceSortField` whitelist enum (per CLAUDE.md "Common table sort"). Unknown values silently fall back to default ORDER BY — never echoed into SQL. |
| `sortDirection` | query | `"ASC" \| "DESC"` | — | **Not implemented.** | `SortDirection.parse(...)`; null on blanks / unknowns. |

**Default sort** (when `sortField` / `sortDirection` are blank): `ISSU_DT DESC, REG_DTM DESC` — legacy parity with `einv_0001_001_r001`. Stable secondary key always appended via `Sort.by(primary).and(DEFAULT_SORT)` so equal values keep deterministic order across requests.

### Always-applied baseline filters (legacy parity — currently MISSING from the Spring skeleton)

The legacy [`einv_0001_001_r001_act.jsp`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp) adds these to every list query, regardless of user input. The refactor MUST replicate them:

1. **`PROV_STTS NOT IN ('VD', 'EE')`** — drafts (`EE`) and cancelled/void (`VD`) are never visible. ([action JSP lines 107-111](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))
2. **`HAND_REG_YN = 'N'`** — hand-registered (manually entered, not synced from GDT) rows are excluded. ([line 102-103](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))
3. **`(DOC_ID, BIZ_KEY) IN (SELECT DOC_ID, BIZ_KEY FROM BUSA_DOC_DETL …)`** — invoices without any `BUSA_DOC_DETL` line item are filtered out. ([line 105](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))
4. **`PROV_STTS=OP` filter expansion** — when `PROV_STTS=OP` is passed explicitly, the WHERE expands to `IN ('OP', 'PP', 'OD')`. ([line 137-147](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp))

Today's `EInvoiceService` skips all four. Phase 2 task to add — required for legacy parity.

### Search behaviour (`SRCH_WD` / `keyword`) — verified

Legacy `SRCH_WD` is implemented in [`SearchHelper.java#L89-L193`](../../../../../WECMS%20DEV/WECMS/src/wabooks/helper/SearchHelper.java) and is **View-Setting-driven**:

1. The action JSP looks up the user's `WEB_SVC_ID`/`DYM_AREA_ID` View Setting rows.
2. Only rows with `USE_YN='Y'` participate in search.
3. For each visible column whose `FIELD_ID` matches the per-WSVC whitelist, the helper appends one OR-clause.

**Whitelist for Sales (`dymId='table1'`)** — 16 candidate `FIELD_ID`s with **per-field predicate variation**:

| `FIELD_ID` | Predicate | Notes |
|---|---|---|
| `DOC_ID` | `DOC_ID ilike '%' || ? || '%'` | Internal document id |
| `TRCO_CMNM` | `wb_get_translate_text(TRCO_CMNM) ilike '%' || wb_get_translate_text(?) || '%'` | Buyer name — locale-folded |
| `TRCO_NICK_NM` | `wb_get_translate_text(TRCO_NICK_NM) ilike '%' || wb_get_translate_text(?) || '%'` | Buyer nickname |
| `TOTL_AMT` | `TOTL_AMT = cast(? as NUMERIC)` | Numeric equality only when input is numeric |
| `DOC_TITLE` | `wb_get_translate_text(cont) ilike '%' || wb_get_translate_text(?) || '%'` | Note: queries `cont`, not `doc_title` — legacy quirk |
| `REG_NM` | `wb_get_translate_text(USER_NM) ilike '%' || wb_get_translate_text(?) || '%'` | Note: queries `USER_NM`, not `reg_nm` — legacy quirk |
| `CONT` | `wb_get_translate_text(CONT) ilike '%' || wb_get_translate_text(?) || '%'` | Description text |
| `NOTE` | `wb_get_translate_text(APPR_DOC_NO) ilike '%' || wb_get_translate_text(?) || '%'` | Note: queries `APPR_DOC_NO`, not `note` — legacy quirk |
| `PRJT_NM` | `wb_get_translate_text(PRJT_NM) ilike '%' || wb_get_translate_text(?) || '%'` | Project name |
| `USAG_NM` | `USAG_NM ilike '%' || ? || '%'` | Usage name (no translate fold) |
| `DOC_NO` | `DOC_NO ilike '%' || ? || '%'` | Display document number |
| `USER_NM` | `wb_get_translate_text(USER_NM) ilike '%' || wb_get_translate_text(?) || '%'` | User name |
| `TRCO_TAX_ID` | `TRCO_TAX_ID ilike ?` | **Exact match — no wildcards** |
| `ISSU_DT` | `ISSU_DT = ?` | Date equality (after `StringUtils.forSearcDate`) |
| `TAX_AMT_SUM` | `TAX_AMT_SUM = cast(? as NUMERIC)` | Numeric equality only |
| `SPLY_AMT_SUM` | `SPLY_AMT_SUM = cast(? as NUMERIC)` | Numeric equality only |

**`EINV_INVOICESTATUS`, `EINV_INVOICESYMBOL`, and `INV_TYPE` are NOT searchable** in legacy. The static UI assumption that searches these is wrong.

**`wb_get_translate_text` is NOT in the local Flyway state.** It's a DB function for accent + case folding (Vietnamese-aware). Phase 2 must decide:

1. Port `wb_get_translate_text` to a Flyway migration (legacy parity).
2. Replicate folding in Java with `Collator` / `Normalizer` and use bare `LOWER(...) LIKE LOWER(?)` predicates server-side.
3. Accept different match results (no folding).

### `POST /api/v1/addon/einvoices/export` (planned)

Request body: shared [`ExcelExportRequest`](../../../../src/main/java/com/kosign/wecms/common/export/ExcelExportRequest.java) shape — `screenId`, `dymAreaId`, `title`, `fileName`, `filters: Map<String,Object>`, `sort: ExcelSortSpec?`, `columns: List<ExcelColumnSpec>`, optional `subHeader`. The `EInvoiceExportProvider` consumes the same `filters` keys (`type`, `from`, `to`, `invoiceStatus`, `keyword`) as the list endpoint.

`screenId` MUST equal `"addon-sales-einvoices"` for Sales (Expenses uses `"addon-expenses-einvoices"`). The `@Pattern` regex `^[a-z][a-z0-9]*(-[a-z0-9]+)+$` rejects legacy JEX ids (`einv_0001_001` would fail validation).

---

## Response structure

### Current shape (`EInvoiceListResponse`)

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "ROWS": [
      {
        "DOC_ID": "...",
        "ISSU_DT": "20241205",
        "ISSU_TM": "113950",
        "INV_TYPE": "Original",
        "EINV_INVOICESYMBOL": "1C24TLM-0405",
        "TRCO_CMNM": "CÔNG TY TNHH ADTECHNOLOGY VIỆT NAM",
        "TRCO_TAX_ID": "3702351124-003",
        "TRCO_TRSC_CCY_CD": "VND",
        "TOTL_AMT": "1000000.00",
        "SPLY_AMT_SUM": "900000.00",
        "TAX_AMT_SUM": "100000.00",
        "EINV_INVOICESTATUS": "Original"
      }
    ],
    "TOTAL_COUNT": 7,
    "TOTALS": [
      { "CCY_CD": "VND", "COUNT": 7, "AMOUNT": "200000000.00" },
      { "CCY_CD": "USD", "COUNT": 0, "AMOUNT": "0" }
    ]
  }
}
```

Backed by *(casing corrected 2026-06-06 — source-verified; the wire is **lowercase `snake_case`**, NOT `@JsonProperty("UPPER_SNAKE")`. `EInvoiceRow.java` / `EInvoiceListResponse.java` carry **no** `@JsonProperty` — they are camelCase Java records serialized by the global Jackson `SNAKE_CASE` strategy. See [field-casing-parity-audit-2026-06-06.md](../../../audits/field-casing-parity-audit-2026-06-06.md))*:

- [`EInvoiceListResponse`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceListResponse.java) — `record(rows, totalCount, totalPages, pageNumber, pageSize, totals)` → wire `rows`, `total_count`, `total_pages`, `page_number`, `page_size`, `totals`
- [`EInvoiceRow`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceRow.java) — 16-field record → wire `doc_id`, `disp_doc_id`, `issu_dt`, `issu_tm`, `totl_amt`, `tax_amt_sum`, `trco_trsc_ccy_cd`, … (all lowercase snake_case)
- `EInvoiceListResponse.CurrencyTotal` — `record(ccyCd, count, amount, taxAmount, pretaxAmount)` → wire `ccy_cd`, `count`, `amount`, `tax_amount`, `pretax_amount`

### Planned shape (extended)

Add pagination metadata + extended row fields (priority: `COLOR`, `COMPANYSTATUS`, `EINV_INVOICESTATUS_BEF`, `READ_YN`, `OVER_DUE_DAYS`, `DUE_DT`, `MEMO`, `NOTE`, `PROV_STTS`, `RCV_SND_YN`, `HAND_REG_YN`, `DISP_DOC_ID`).

```json
{
  "COMMON_HEAD": { "ERROR": false, "CODE": "WBI0000", "MESSAGE": "Success" },
  "REC": {
    "ROWS": [ /* extended EInvoiceRow records */ ],
    "TOTAL_COUNT": 247,
    "TOTAL_PAGES": 25,
    "PAGE_NUMBER": 0,
    "PAGE_SIZE": 10,
    "TOTALS": [
      { "CCY_CD": "VND", "COUNT": 240, "AMOUNT": "200000000.00", "TAX": "20000000.00", "PRETAX": "180000000.00" },
      { "CCY_CD": "USD", "COUNT":   7, "AMOUNT":      "5000.00", "TAX":      "500.00", "PRETAX":      "4500.00" }
    ]
  }
}
```

`TOTALS` is computed against the full filtered set (NOT the page slice) so footer numbers stay stable across pagination — must land in the same MR as server-side pagination. The legacy WSVC also returns per-status totals (`TOT_OP_INV_*`, `TOT_OD_INV_*`, `TOT_PA_INV_*`, `TOT_INV_*`) — open question whether the refactor exposes them.

### Wire-field mapping (legacy `WSVC.einv_0001_001_r001.xml` → refactor)

Top-level (subset wired today; legacy ships ~25 fields):

| Legacy `out` field | Refactor wire field | Java property | Status |
|---|---|---|---|
| `TOT_CNT` | `TOTAL_COUNT` | `totalCount` | ✅ wired |
| (computed per CCY) | `TOTALS[]` array | `totals` | ✅ wired |
| `TOT_VND_CNT`, `TOT_USD_CNT` | folded into `TOTALS[].COUNT` | — | ✅ |
| `TOT_VND_AMT`, `TOT_USD_AMT` | folded into `TOTALS[].AMOUNT` | — | ✅ |
| `TOT_VND_SPLY_AMT`, `TOT_USD_SPLY_AMT` | `TOTALS[].PRETAX` *(planned)* | — | ⏳ |
| `TOT_VND_TAX_AMT`, `TOT_USD_TAX_AMT` | `TOTALS[].TAX` *(planned)* | — | ⏳ |
| `TOT_OP_INV_*` / `TOT_OD_INV_*` / `TOT_PA_INV_*` / `TOT_INV_*` | (open question — likely `STATUS_TOTALS[]` array if PO confirms) | — | ❓ |
| `END_DATE`, `GDT_CERT_SEQ` | (not yet on wire — depends on GDT bridge scope) | — | ❌ |

`REC` row (12 of 41 wired today; the rest are TODO and listed in the feature spec).

---

## Invoice status mapping

`EINV_INVOICESTATUS` is a **localised** string. Verified against [`IDO.BUSA_DOC_MSTR_R022.xml:238`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml):

```sql
wb_get_invoice_status(coalesce(BDM.EINV_INVOICESTATUS, ''), :LANG_CD) EINV_INVOICESTATUS
```

The IDO resolves the stored value through a DB function that returns the localised form per the user's `LANG_CD`. The legacy frontend ([`einv_0001_001.ts:2788-2801`](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/einv/einv_0001_001.ts)) uses **regex pattern matching** that recognises the same logical state across vi / en / ko, applying a CSS class for badge tint.

**Six logical statuses** (verified against legacy TS `loadTextColorInvBillOut`):

| Logical status | Vietnamese (vi) | English (en) | Korean (ko) | Legacy TS badge class |
|---|---|---|---|---|
| Original | `Hóa đơn mới` | `Original` | `세금계산서` | `cBlue` |
| Replacement (new replacement issued) | `Hóa đơn thay thế` | `Replacement` | `대체세금계산서` | `cGreen` |
| Adjustment (new adjustment issued) | `Hóa đơn điều chỉnh` | `Adjustment` | `수정세금계산서` | `cGreen` |
| Replaced (this row was replaced) | `Hóa đơn đã bị thay thế` | `Replaced` | `대체된 세금계산서` | `cGreen` |
| Adjusted (this row was adjusted) | `Hóa đơn đã bị điều chỉnh` | `Adjusted` | `수정 (전)세금계산서` | `cGreen` |
| Cancelled | `Hóa đơn đã bị hủy` | `Cancelled` | `취소된 세금계산서` | `cRed` |

**Implementation blocker — `wb_get_invoice_status` is NOT in the local Flyway state.** Today's refactor `EInvoiceService` reads `BUSA_DOC_MSTR.einvInvoicestatus` directly. Without the localisation function, the wire returns the raw stored value (in whatever language it was written), and same logical state surfaces as different display strings across locales. Phase 2 must pick one resolution path:

1. **Port `wb_get_invoice_status` to local Flyway** — legacy parity. The wire stays per-locale; filter sends the locale-resolved string (mirrors legacy frontend that always sent the Vietnamese form).
2. **Replicate the regex matching in Java** and emit a stable canonical key on the wire (`ORIGINAL` / `REPLACEMENT` / `ADJUSTMENT` / `REPLACED` / `ADJUSTED` / `CANCELLED`). Frontend translates display labels via i18n. Best for the refactor — locale-independent contract.
3. **Replicate the regex matching in the frontend** (mirror legacy TS) and accept that the wire string varies by locale.

**`COLOR` is NOT a status badge field.** Verified against [`IDO.BUSA_DOC_MSTR_R022.xml:190`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml):

```sql
wb_get_memo_color(BDM.BIZ_KEY, 'einv_0001_001', BDM.DOC_ID, …) COLOR
```

`COLOR` is the per-document **memo sticker CSS class**, length ≤ 5, default `"white"`. The legacy TS uses it as a CSS class on the memo sticker `<div>` (e.g. `class="sticker {COLOR}"`), unrelated to the status column. The status column tint comes from regex matching of the localised `EINV_INVOICESTATUS` (legacy `loadTextColorInvBillOut`).

For the refactor listing-page scope:

- **Drop `COLOR` and `MEMO`** from the Phase 2 wire-extension priority. Both depend on porting `wb_get_memo_color` / `wb_get_memo_cont` and the underlying memo storage — out of listing-page scope.
- **Status badge tint** comes from a frontend regex/i18n match against the resolved `EINV_INVOICESTATUS` (or against the canonical key if Phase 2 picks option 2 above). Either way, the backend does NOT need to ship a `COLOR` field on the wire for status purposes.

**Prior-status field** (`EINV_INVOICESTATUS_BEF`) — carries the status the row had before its most recent status change. Useful for tooltip / detail-page diff. Today NOT on the refactor wire; planned in Phase 2 task #6.

**`INV_TYPE` is NOT the status field.** Verified against [`IDO.BUSA_DOC_MSTR_R022.xml:200`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml):

```sql
(CASE WHEN BDM.INV_TYPE = 'C' THEN 'Commercial' ELSE 'Tax' END) INV_TYPE
```

`INV_TYPE` is a **Commercial-vs-Tax classifier** (`'C'` → `Commercial`, anything else → `Tax`). The legacy WSVC `<out>` does NOT expose `INV_TYPE` and the legacy TS does not render it on the listing. The static UI mock that seeds `invoiceType: "Original"` is misnamed — those values are `EINV_INVOICESTATUS`, not `INV_TYPE`.

**Buyer-status (`COMPANYSTATUS` — separate field, not invoice-status):**

The `COMPANYSTATUS` field on the legacy `REC` carries the **buyer's MST status** as returned by the GDT (joined from `OPRT_STTS_INFO`). The refactor wire should preserve it verbatim — the field is a compliance signal, NOT a controlled enum. Common values:

- `Active` — buyer's tax registration is in good standing
- `On closing` — buyer winding down
- `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST` — buyer suspended without close-out → input-VAT credit may be invalid

The refactor MUST render this Vietnamese text verbatim (per the feature spec). Today the field is NOT on the wire; today's frontend mapper sets `buyerStatus = ""`.

## `PROV_STTS` vocabulary

Verified against [`ProvStts.java`](../../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ProvStts.java) — fixed 6-value enum:

| Code | Korean label | English meaning | Listing behaviour |
|---|---|---|---|
| `OP` | 납기내 | Open (within term) | Visible · `PROV_STTS=OP` filter expands to `IN ('OP','PP','OD')` |
| `PP` | 부분수납 | Partially paid | Visible (folded into `OP` via expansion above) |
| `PA` | 수납완료 | Paid in full | Visible |
| `OD` | 연체 | Overdue | Visible |
| `VD` | 취소 | Cancelled / void | **Always excluded** (baseline filter) |
| `EE` | 임시저장 | Draft / temporary | **Always excluded** (baseline filter) |

---

## Error handling

Standard `ApiResponse<T>` envelope with `COMMON_HEAD` carrying the code + locale-resolved message. HTTP status stays **200** for known business errors — client inspects `COMMON_HEAD.ERROR`. Unexpected exceptions return **500** with `WBE9999`.

| Code | When | Today |
|---|---|---|
| `WBI0000` | Success — `ERROR=false`, `REC` populated | ✅ |
| `WBE2002` | `type` is null / blank / not in `{SALES, I, EXPENSE, E}` | ✅ thrown by `EInvoiceService#resolveDocType` |
| `WBE_VALIDATION` | Bean-validation failure (e.g. `screenId` regex on the export request, `size > 100`, `from`/`to` not `YYYYMMDD`) | ⏳ caught by `GlobalExceptionHandler` once validation lands |
| `WBE2010` | Unknown `screenId` in `ExcelExportRequest` (no provider registered) | ⏳ raised by `ExportableScreenRegistry` |
| `WBE2011` | Excel export exceeds the 50,000-row cap | ⏳ raised by `ExcelExportService` |
| `WBE2013` | Excel request has no exportable columns after intersection with View Setting / allow-list | ⏳ raised by `ExcelExportService` |
| `WBE9999` | Unexpected runtime error (DB / mapper / etc.) | ✅ caught by `GlobalExceptionHandler` |

Error code semantics live in `messages*.properties`; never return a raw string message without a code per [API rules](../../../../CLAUDE.md).

**No 4xx for invalid sort.** Per CLAUDE.md "Common table sort", an unknown `sortField` MUST degrade silently to the default ORDER BY — never throw on a stale frontend bundle.

**No 4xx for invalid filter values.** Empty / unparseable date strings are treated as "no filter"; empty status strings are treated as "no filter". The intent: a stale or partially-loaded filter should not break the page.

---

## BIZ_KEY / tenant filtering

Every query in this domain is **scoped to the current tenant**, sourced from `TenantContext.currentBizKey()` (populated by `JwtAuthenticationFilter` from the validated JWT), never from request input — so a `%` wildcard tenant filter is impossible by construction.

Today's call:

```java
final String bizKey = TenantContext.currentBizKey();
final String docType = resolveDocType(type);
boolean hasRange = …;
List<BusaDocMstr> rows = hasRange
        ? repo.findByBizKeyAndDocTypeAndIssuDtBetweenOrderByIssuDtDescRegDtmDesc(bizKey, docType, fromDt, toDt)
        : repo.findByBizKeyAndDocTypeOrderByIssuDtDescRegDtmDesc(bizKey, docType);
```

**Hard rules** (enforce in review):

- Every `BusaDocMstrRepository` finder MUST take `bizKey` as the first argument (or carry it via Spring's `@PreFilter` if specs are used) — never a literal string, never a wildcard.
- `BIZ_KEY` MUST be part of every `WHERE` clause that touches `BUSA_DOC_MSTR`. The PROD table is multi-tenant; data leakage across tenants is a security bug, not a feature gap.
- The export endpoint inherits the same scope — `EInvoiceExportProvider#fetch` must call back into `EInvoiceService` (which carries the tenant filter) rather than going directly to the repository.
- `BIZ_KEY` must NOT appear as a wire field on requests or responses — the JWT is the authority. Echoing `BIZ_KEY` in the response is documented as redundant (the frontend already knows it from `userStorage`).

---

## Service / sort / aggregation design

### `EInvoiceSortField` (planned whitelist enum)

```java
public enum EInvoiceSortField implements SortableField {
    DATE_TIME    ("issuDt"),    // tiebreaker on regDtm via DEFAULT_SORT
    INVOICE_NO   ("einvInvoicesymbol"),
    BUYER        ("trcoCmnm"),
    CURRENCY     ("trcoTrscCcyCd"),
    TOTAL        ("totlAmt"),
    STATUS       ("einvInvoicestatus"),
    TAX          ("taxAmtSum"),
    PRETAX       ("splyAmtSum");
    …
}
```

`DEFAULT_SORT = Sort.by(desc("issuDt"), desc("regDtm"))` — legacy `einv_0001_001_r001` parity. Stable secondary key always appended.

### Aggregation query (multi-currency totals)

Today's loop runs over the in-memory `rows` list. With server-side pagination this becomes wrong (totals would reflect the page slice). Plan:

- Add `BusaDocMstrRepository#aggregateByCurrency(bizKey, docType, fromDt, toDt, invoiceStatus, keyword)` returning a list of `(ccyCd, count, totalSum, taxSum, pretaxSum)` projections.
- Run the same WHERE chain as the list query, GROUP BY `TRCO_TRSC_CCY_CD`.
- Use a `@Query` with the same parameter set; pin against the `BUSI_INFM`-tenant rule.
- Service composes both calls (`Page<...>` + aggregation) inside one `@Transactional(readOnly = true)`.

### Excel export provider

```java
@Component
public class EInvoiceExportProvider implements ExportRowProvider {
    public String screenId() { return "addon-sales-einvoices"; }
    public Set<String> allowedFieldIds() {
        return Set.of("DATE_TIME", "INVOICE_NO", "BUYER", "CURRENCY", "TOTAL", "STATUS", "TAX", "PRETAX");
    }
    public ExportFetch fetch(ExcelExportRequest req) {
        // Re-runs EInvoiceService.list(...) with Pageable.unpaged()
        // Composes subheader: <count> invoice(s) · Total (VND): … · Total (USD): …
        // Computes amount strings via AmountFormatter.forDisplay(value, ccyCd) for the subheader
        // Numeric data cells stay raw BigDecimal — POI's #,##0.## handles cell-level formatting
    }
}
```

The Expenses sibling registers a separate `screenId="addon-expenses-einvoices"` against the same provider type (or a sibling class with the same allowedFieldIds and the same fetch path filtered by `DOC_TYPE='E'`).

---

## Related backend files

**Implemented today:**

- [`controller/EInvoiceController.java`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/controller/EInvoiceController.java) — `@RestController @RequestMapping("/api/v1/addon")` with the single `@GetMapping("/einvoices")` handler
- [`service/EInvoiceService.java`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/service/EInvoiceService.java) — list + `resolveDocType` + `toRow` + per-currency `wireAmount` rounding (`HALF_UP`, 2 dp, trailing-zero strip)
- [`dto/EInvoiceListResponse.java`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceListResponse.java) — camelCase record `record(rows, totalCount, totalPages, pageNumber, pageSize, totals)` → lowercase snake_case wire (no `@JsonProperty`)
- [`dto/EInvoiceRow.java`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/dto/EInvoiceRow.java) — 16-field camelCase record → lowercase snake_case wire (**no** `@JsonProperty`; corrected 2026-06-06)
- [`entity/BusaDocMstr.java`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) — JPA entity for `BUSA_DOC_MSTR` (Sprint 1 column subset; DB schema is full DEV parity per V23)
- [`entity/BusaDocMstrId.java`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstrId.java) — `@IdClass` composite PK
- [`repository/BusaDocMstrRepository.java`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/repository/BusaDocMstrRepository.java) — derived finders; date-range + non-range variants; sort via method name

**To be added:**

- `service/EInvoiceSortField.java` — sort whitelist enum
- `export/EInvoiceExportProvider.java` (+ Expenses sibling) — `ExportRowProvider` registrations
- Repository methods accepting `Pageable` + `Sort` + `invoiceStatus` + `keyword` — adopt JPA Specifications or `@Query` with `LIKE LOWER(...)` + ESCAPE
- Repository `aggregateByCurrency(...)` — projection-based GROUP BY

**Shared infrastructure consumed:**

- [`common/response/ApiResponse.java`](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) + `CommonHead`
- [`common/exception/BusinessException.java`](../../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) + `GlobalExceptionHandler`
- [`common/i18n/MessageResolver.java`](../../../../src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java)
- [`common/security/JwtAuthenticationFilter.java`](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) + `TenantContext`
- [`common/web/SortDirection.java`](../../../../src/main/java/com/kosign/wecms/common/web/SortDirection.java) + `SortableField`
- [`common/export/ExcelExportService.java`](../../../../src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) + `ExportableScreenRegistry` + `ExcelWorkbookWriter` + `ExcelExportRequest` + `ExcelColumnSpec` + `AmountFormatter`
- [`domain/setting/service/ScreenOptionService.java`](../../../../src/main/java/com/kosign/wecms/domain/setting/service/ScreenOptionService.java) (used by the export pipeline to intersect with persisted View Setting visibility)

---

## Cross-references

- Feature spec: [../../features/addon-service/sales-e-invoices.md](../../../features/addon-service/sales-e-invoices.md)
- Frontend page: [../../frontend/addon/sales-e-invoices-page.md](../../../../../wecms-web/docs/features/addon-service/sales-e-invoices.md)
- Database: [../../database/addon/sales-e-invoices-db.md](../../database/addon/sales-e-invoices-db.md)
- Migration mapping: [../../migration/service-mapping.md](../../migration/service-mapping.md)
- Implementation plan: ../../planning/addon/sales-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- Legacy WSVC: `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.einv_0001_001_r001.xml`
- Legacy view: `WECMS DEV/WECMS/web/view/jex/wabooks/einv/einv_0001_001_view.jsp`
