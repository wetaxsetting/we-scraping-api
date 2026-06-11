# Firm Banking History — Business Flow (Backend)

How a Firm Banking History request travels through the refactored Spring Boot stack, end to end.
Parent: [README.md](README.md). Legacy flow (read-only spec): [docs/jex/.../business-flow.md](../../../../../docs/jex/features/firm-banking-history/business-flow.md).

---

## High-level flow

```
User opens Settings → Firm Banking History
        ↓
Frontend mounts panel; default date range = last 30 days
        ↓
Search request  →  GET /api/v1/settings/firm-banking-history?from_date&to_date[&svc_type&keyword&sort_*&page&size]
        ↓
FirmBankingHistoryController.list(...)          (thin pass-through; @PreAuthorize ADMIN|USER)
        ↓
FirmBankingHistoryService.list(...)             (validate → build filters/sort → page → tenant scope)
        ↓
FirmBankingHistoryRepository.search(...)        (single JPQL over the read-only view)
        ↓
V_FIRM_BANKING_HISTORY  (5-leg UNION ALL, Flyway V60)
        ↓
Page<FirmBankingHistoryView>  →  toRow(...)     (resolve svc_type_key + svc_name_key i18n keys)
        ↓
FirmBankingHistoryListResponse { list, total_count, page_number, page_size, total_pages }
        ↓
ApiResponse envelope  →  Frontend maps wire → camelCase  →  grid render
        ↓
Row "View Details"  →  client-side popover (no extra HTTP call — every detail field is in the row)
        ↓
(optional)  Excel icon  →  POST /export  →  XLSX stream
```

---

## Step-by-step (list path)

1. **Controller receives the request.**
   [`FirmBankingHistoryController.list`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/controller/FirmBankingHistoryController.java) binds the snake_case wire params (`from_date`, `to_date`, `svc_type`, `keyword`, `sort_field`, `sort_direction`, `page`, `size`) to camelCase Java params via `@RequestParam(value = …)`. The class-level `@PreAuthorize("hasAnyRole('ADMIN','USER')")` gates both endpoints. The controller does no logic beyond delegating to the service and wrapping the result in `ApiResponse.ok(...)`.

2. **Service validates input** ([`FirmBankingHistoryService.list`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingHistoryService.java)):
   - `from_date` / `to_date` — required, exactly 8 chars, parseable as `LocalDate` via `yyyyMMdd`. Blank/length/parse failure → `BusinessException("WBE_VALIDATION")`.
   - period — `from ≤ to`, else `WBE_VALIDATION` (`"from_date: must be on or before to_date"`).
   - `svc_type` — blank → `null` (no filter); otherwise trimmed + uppercased and checked against `ALLOWED_SVC_TYPES = {F,S,G,B}`; unknown → `WBE_VALIDATION`.
   - `keyword` — blank → `null`; otherwise LIKE meta-chars (`%`, `_`, `!`) escaped with a `!` prefix, lowercased, wrapped `%…%`.

3. **Service builds paging + sort.**
   - `size ≤ 0` → `DEFAULT_PAGE_SIZE = 15`; otherwise `min(size, MAX_PAGE_SIZE = 100)`.
   - `page` → `max(0, page)` (zero-based on the wire).
   - sort — `FirmBankingHistorySortField.parse(sort_field)` resolves a whitelisted FIELD_ID to its entity property; `SortDirection.parse(sort_direction)` resolves ASC/DESC. Unknown / blank → `DEFAULT_SORT = TARN_DTM DESC, SEQ_NO DESC` (no exception, no echo of raw input into SQL).

4. **Service resolves the tenant.** `TenantContext.currentBizKey()` — sourced from the signed JWT, never from a request param.

5. **Repository runs one query** ([`FirmBankingHistoryRepository.search`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/repository/FirmBankingHistoryRepository.java)):
   ```jpql
   SELECT v FROM FirmBankingHistoryView v
   WHERE v.bizKey = :bizKey
     AND SUBSTRING(v.tarnDtm, 1, 8) BETWEEN :fromDate AND :toDate
     AND (:svcType IS NULL OR v.svcType = :svcType)
     AND (:keywordPattern IS NULL OR LOWER(v.seqNo) LIKE :keywordPattern ESCAPE '!')
   ```
   Spring Data appends `ORDER BY` + `LIMIT/OFFSET` from the `Pageable`. The query targets the read-only view `V_FIRM_BANKING_HISTORY`, so the five-leg union is resolved by PostgreSQL, not in Java.

6. **Service maps rows** (`toRow`): each `FirmBankingHistoryView` becomes a `FirmBankingHistoryRow`, computing two server-resolved i18n keys:
   - `svc_type_key` ← `FirmBankingSvcType.resolveKey(svcType)` → `settings.firm_banking_history.service_type.*`.
   - `svc_name_key` ← `FirmBankingServiceNameResolver.resolve(svcType, svcNm)` → `fb_history.service.*` (or `null` for unknown pairs).
   Raw `svc_type` / `svc_nm` are preserved verbatim alongside the keys.

7. **Response envelope.** The service returns `FirmBankingHistoryListResponse { list, total_count, page_number, page_size, total_pages }`; the controller wraps it in `ApiResponse.ok(...)`.

8. **Frontend renders.** The client maps wire → camelCase, latches stable data to avoid pagination flicker, renders the grid, and resolves service-type / service-name / result labels via `t(key)`.

---

## Detail-view flow

The **View Details** popover requires **no additional HTTP call**. Every field needed for the
per-leg detail (including `rslt_cd`, `rslt_msg`, `job`, `req_data`, `van_org_cd`) is already in the
list response row. The frontend opens a client-side popover and renders leg-specific fields:

- **F (firm banking):** result message resolved via `DICTIONARY_VAN` (4-char VAN codes); shows VAN code + withdrawal inst. code.
- **S (scraping):** result message via the 18-code scraping registry; shows withdrawal inst. code.
- **G (GDT e-Invoice):** parses `req_data` for the e-Invoice number; result message via scraping registry.
- **B (Vietnam business):** parses `req_data.search` for the Tax ID; result message via scraping registry.

See the frontend [screen-flow.md](../../../../../wecms-web/docs/features/settings/firm-banking-history/screen-flow.md) for the UI side.

---

## Export flow

```
Excel icon  →  POST /api/v1/settings/firm-banking-history/export  (ExcelExportRequest body)
        ↓
FirmBankingHistoryController.export(...)  →  ExcelExportService.exportTo(response, request)
        ↓
ExcelExportService resolves FirmBankingHistoryExportProvider (screenId "firm-banking-history")
        ↓
provider.fetch(...)  →  FirmBankingHistoryService.listAll(...)   (same filter/sort path, unpaged)
        ↓
rows → FIELD_ID-keyed maps; subheader "N record(s)"; columns intersected with View Setting (V165 seed)
        ↓
ExcelWorkbookWriter (POI SXSSF)  →  XLSX stream  (≤ 50,000 rows; RFC 5987 filename)
```

The export reuses the shared `common/export/` pipeline; the provider is the only feature-specific
piece. Column visibility is intersected with the `SYS_SCR_OPTION_WECMS` seed (`web_svc_id =
'firm-banking-history'`, Flyway V165) as defence-in-depth.

---

## Authority & tenant rules

- **Authority:** class-level `@PreAuthorize("hasAnyRole('ADMIN','USER')")`. Both roles are *normal
  company* roles stamped from `authGrCd` by `JwtAuthenticationFilter`. The refactor has no WTA/agent
  users (the admin portal is a separate system), so "normal company user" is faithfully mapped to
  "authenticated `ROLE_ADMIN` or `ROLE_USER`". Mirrors legacy `WSVC.comm_0221_001_r001.xml:24-27`
  (`NORM_USER_ONLY_YN=Y`, `WTA_USER_ALLOW_YN=N`).
- **Tenant:** every leg of the view filters on `BIZ_KEY = :bizKey`; the service supplies
  `TenantContext.currentBizKey()`. No cross-tenant read is possible.
- **Product-type gate:** the frontend hides the GDT (`G`) and Vietnam Business (`B`) service-type
  filter options for product type `H` (HAENGSUNG), mirroring legacy `comm_0221_001_view.jsp` (`prdtTypes === "H"`). The backend does not need a product gate on the read path — it returns whatever the tenant's rows contain.
