# Operation Status Inquiry — Backend Business Flow

Backend control flow for each of the five operations. Tenant scope (`BIZ_KEY`) is always read from the JWT via `TenantContext.currentBizKey()` — the wire never carries `BIZ_KEY`. Source: [OperationStatusService.java](../../../../src/main/java/com/kosign/wecms/domain/operation/service/OperationStatusService.java), [OperationStatusController.java](../../../../src/main/java/com/kosign/wecms/domain/operation/controller/OperationStatusController.java).

---

## 1. List — `GET /api/v1/addon/operation-status`

```
[Browser: OperationStatusClient]
   │ GET /api/v1/addon/operation-status?q=&companyStatus=&page=&size=&sortField=&sortDirection=
   ▼
[JwtAuthenticationFilter] → TenantContext.currentBizKey()
   ▼
[OperationStatusController#list]  → new OperationStatusFilter(q, companyStatus)
   ▼
[OperationStatusService#list(filter, sortField, sortDirection, page, size)]
   │ safeSize = size<=0 ? 10 : min(size, 100)   safePage = max(0, page)
   │ Sort = buildSort(OperationStatusSortField.parse(sortField), SortDirection.parse(sortDirection))
   │         → user primary order + DEFAULT_SORT tiebreaker (SEARCHDATETIME DESC, TAXCODE ASC)
   │ keywordPattern / companyStatusPattern = buildLikePattern(...)  (lower-cased, %…% wrapped, ESCAPE '!')
   ▼
[OprtSttsInfoRepository#findFiltered(bizKey, companyStatusPattern, keywordPattern, pageable)]
   │ WHERE biz_key = :bizKey
   │   AND (:companyStatusPattern IS NULL OR LOWER(companystatus) LIKE :p ESCAPE '!')
   │   AND (:keywordPattern IS NULL OR LOWER(companyname) LIKE :k OR LOWER(taxcode) LIKE :k)
   ▼
[PostgreSQL: OPRT_STTS_INFO]
   ▼
rows.map(toRow) → resolveStatusCd(companystatus) derives status_cd per row
   ▼
OperationStatusListResponse { rows, total_count, page_number, page_size, total_pages }
```

- **No baseline filters beyond tenant.** Unlike Sales e-Invoices, the legacy `aosv_0001_001_r001_act.jsp` hides nothing — every GDT-sourced row is visible.
- **Keyword (`q`)** OR-matches `COMPANYNAME` and `TAXCODE` (legacy fixed 2-column whitelist).
- **`companyStatus`** is a partial match against the raw Vietnamese `COMPANYSTATUS` string (the legacy `wb_get_operat_stts` localisation helper is commented out and **not** ported).
- **Export reuses this path** with `Pageable.unpaged()` (see §5).

---

## 2. Inquire — `POST .../operation-status/inquire`

"Get Business Info" — single Tax ID lookup against the GDT business-registration API via the OSMC bridge. Mirrors `aosv_0001_002_r001_act.jsp`.

```
[OperationStatusService#inquire(taxCode)]   (@Transactional)
 1. seqNo = ExtTranCallHisRepository.findMaxSeqNoForDay("BI09", yyyyMMdd) + 1
    INSERT EXT_TRAN_CALL_HIS (pre-call audit; SVC_ID=BI09, WEB_SVC_ID=aosv_0001_001_r001, BIZ_KEY, USER_ID, TAXCODE, REG_DTM)
 2. uniqueKey = generateUniqueKey()  (7-char SecureRandom alphanumeric)
    result = OsmcGdtClient.lookup([taxCode], uniqueKey)
 3. patch EXT_TRAN_CALL_HIS row: RSLT_CD = result.errorCode, ERR_CONT = truncate(result.errorMessage, 1000)
 4. INSERT NXSIAS_MODULE_TRAN_LOG_HIS (NXISAS audit: class COMMON, module GLOBAL/VNM/PUBLIC_MASOTHUE, job BUSSINESS_REGISTRATION_LIST)
 5. if !result.isSuccess()            → throw BusinessException("WBE_OSMC_LOOKUP_FAILED", msg)
    hit = first result.rows() whose taxCode matches
    if hit == null                    → throw BusinessException("WBE_OSMC_NO_RESULT")
    return InquireOperationStatusResponse(taxCode, companyNm, companyStatus, status_cd, officeAdd, representativeFirstName, searchDatetime)
```

The popup does **not** persist here — the user reviews the returned data and clicks Save (§3) or closes.

---

## 3. Save (Create) — `POST .../operation-status`

Persists the GDT-looked-up registration into `OPRT_STTS_INFO`. Mirrors `aosv_0001_002_c001_act.jsp`. **Create-only — never an upsert** (the Update path owns refresh/upsert).

```
[OperationStatusService#create(req)]   (@Transactional)
 1. bean validation on CreateOperationStatusRequest (taxCode regex, companyStatus/officeAdd/searchDatetime required)
 2. pk = (bizKey, req.taxCode())
    if repo.existsById(pk)            → throw BusinessException("WBE_OPERATION_STATUS_DUPLICATE", taxCode)
 3. build OprtSttsInfo:
      representativefirstname = req.representativeFirstName() ?? ""   (column NOT NULL; GDT may return blank)
      stampAudit(row, userId)         → REG_ID/REG_DTM (if new) + MOD_ID/MOD_DTM (always), 14-digit UTC
 4. try repo.save(row)
    catch DataIntegrityViolationException  → throw WBE_OPERATION_STATUS_DUPLICATE  (lost concurrent-insert race)
 5. return toRow(row)   (same wire shape as a list row, so the popup can reflect it)
```

---

## 4. Update (bulk re-poll) — `POST .../operation-status/update`

Toolbar "Update" — re-polls cached `OPRT_STTS_INFO` rows from the GDT and upserts the refreshed snapshot. Mirrors `aosv_0001_001_u001_act.jsp`. Body `{ tax_codes }` is optional.

```
[OperationStatusService#bulkUpdate(selectedTaxCodes)]   (@Transactional)
 1. cached = repo.findAllByBizKey(bizKey)
    if cached empty                   → return {0,0,0}
 2. working set:
      selectedTaxCodes null/empty     → every cached tax code
      non-empty                       → cached ∩ selection  (unknown / cross-tenant codes silently dropped)
    if working set empty              → return {0,0,0}
 3. INSERT EXT_TRAN_CALL_HIS (one batch row covers the whole multi-code request)
    result = OsmcGdtClient.lookup(taxCodes, uniqueKey)
    patch audit row with RSLT_CD / ERR_CONT
    if !result.isSuccess()            → write NXISAS audit + throw WBE_OSMC_LOOKUP_FAILED (cache untouched)
 4. index returned details by taxCode (first match wins)
    for each taxCode in working set:
      detail present → upsert OprtSttsInfo (findById ?? new), stampAudit, save; write NXISAS success audit; updated++
      detail absent  → write NXISAS failure audit (per-search ErrorCode or WBE_OSMC_NO_RESULT); failed++   (cached row untouched)
 5. return UpdateOperationStatusResponse(total_attempted = workingSet.size, updated, failed)
```

Per-row failures **do not abort the batch** — the counter is returned for the UI toast.

---

## 5. Export — `POST .../operation-status/export`

Shared common XLSX pipeline. The thin controller forwards `ExcelExportRequest` to `ExcelExportService.exportTo(response, request)`; the registry resolves `OperationStatusExportProvider` by `screenId = "addon-operation-status"`.

```
[ExcelExportService] resolve provider → intersect requested columns with allowedFieldIds ∩ persisted View Setting USE_YN
   ▼
[OperationStatusExportProvider#fetch]
   │ keyword = filters["keyword"] ?? filters["q"]   companyStatus = filters["companyStatus"]
   │ sort = request.sort() (field/direction)
   │ data = OperationStatusService.list(new OperationStatusFilter(keyword, companyStatus), sortField, sortDirection, Pageable.unpaged())
   │ rows → FIELD_ID map:  COMPANY="name / taxCode"  ADDRESS=officeAdd  OPERATION_STATUS=raw Vietnamese  LAST_UPDATE=lastUpdate
   │ subHeader = "<n> compan(y|ies)  ·  As of <YYYY-MM-DD>"   (export-run date, not per-row poll date)
   ▼
[ExcelWorkbookWriter] (Apache POI SXSSF) → streams XLSX, RFC 5987 UTF-8 Content-Disposition
```

Export and list draw from **one query path**, so filter + sort behaviour matches the screen exactly. `OPERATION_STATUS` emits the **raw** Vietnamese string (not the 3-state code) so the spreadsheet preserves the full GDT response.

---

## Audit trail summary

| Table | Written by | Purpose |
|---|---|---|
| `EXT_TRAN_CALL_HIS` | inquire (1/call), bulkUpdate (1/batch) | External-call audit — `SVC_ID=BI09`, `WEB_SVC_ID=aosv_0001_001_r001`, SEQ_NO per day, RSLT_CD/ERR_CONT patched post-call |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | inquire (1/call), bulkUpdate (1/row) | NXISAS module-call audit — class/module/job sentinels, per-row success/failure code |

Both legacy `aosv_0001_002_r001` sentinels (`BI09`, `aosv_0001_001_r001`, NXISAS class/module/job) are pinned verbatim so refactor-written audit rows align with live JEX data on a shared DB.
