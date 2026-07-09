# ERP Transaction History — Business Flow (Backend)

End-to-end request flow for every ERP Transaction History operation. Source of truth: the implemented
Spring code under `com.kosign.wecms.domain.setting.erptransactionhistory`. Legacy behaviour reference:
the JEX `comm_0219_001` family (see [source-code-index.md](source-code-index.md)) + the preserved flat
spec [../transaction-history.md](../transaction-history.md).

```
User (tenant user)
      │
      ▼
Settings modal → ERP Transaction History panel
      │   (every request carries the JWT; @menuAuthz.canAccess('settings-erp-transaction-history') gates the controller)
      ▼
ErpTrnsHistoryController  ──►  ErpTrnsHistoryService
      │                            │
      │                            ├─ TenantContext.currentBizKey()   (tenant scope, never a param)
      │                            ├─ ErpTrnsHstrRepository.search(...)            (page slice; LEFT JOIN UserInfm)
      │                            ├─ ErpTrnsHstrRepository.countAllDirections(...) (4 tab counts, ERP_TYPE dropped)
      │                            └─ ErpTrnsHistoryFileResolver.resolve(...)      (download only)
      ▼
ApiResponse<ErpTrnsHistoryListResponse>   (lowercase snake_case envelope)
   OR  binary InputStreamResource          (download)
   OR  XLSX stream via ExcelExportService   (export)
```

This is a **read-only** feature — there is no create / update / delete path. The only "write" code in
the package, `ErpTrnsHistoryWriter`, is a shared audit-row writer for *other* features and has no live
caller yet (see §5).

---

## 1. List + four tab counts

```
GET /api/v1/settings/erp-transaction-history?from_date=…&to_date=…[&erp_type=…&trns_type_cd=…&keyword=…&sort_field=…&sort_direction=…&page=…&size=…]
  → service.list(fromDate, toDate, erpType, trnsTypeCd, keyword, sortField, sortDirection, page, size)   @Transactional(readOnly = true)
      1. validateDate("from_date") + validateDate("to_date")     -- required, 8-digit, parseable (legacy validateDate8)
      2. validatePeriod(fromDt, toDt)                            -- from <= to (legacy validatePeriod)
      3. validateErpType(erpType)                                -- optional; ∈ {W,E,D} or WBE_VALIDATION (refactor add)
      4. validateTrnsTypeCd(trnsTypeCd)                          -- optional; "01".."17" or WBE_VALIDATION (refactor add)
      5. buildKeywordPattern(keyword)                            -- lower + LIKE-escape (!) + wrap %…%
      6. safeSize = size<=0 ? 15 : min(size,100); safePage = max(0,page)   -- DEFAULT_PAGE_SIZE=15, MAX_PAGE_SIZE=100
      7. sort = buildSort(ErpTrnsHistorySortField.parse(sortField), SortDirection.parse(sortDirection))  -- default REG_DTM DESC, SEQ_NO DESC
      8. bizKey = TenantContext.currentBizKey()
      9. repo.search(bizKey, fromDt, toDt, erpTypeFilter, trnsTypeFilter, keywordPattern, pageable)
            WHERE biz_key = :bizKey
              AND SUBSTRING(reg_dtm, 1, 8) BETWEEN :fromDate AND :toDate
              AND (:erpType IS NULL OR erp_type = :erpType)
              AND (:trnsTypeCd IS NULL OR trns_type_cd = :trnsTypeCd)
              AND (:keywordPattern IS NULL OR LOWER(reg_id) LIKE :keywordPattern ESCAPE '!')
            LEFT JOIN UserInfm u ON u.userId = e.regId        -- resolves USER_NM (replaces WB_GET_USER_NM)
     10. repo.countAllDirections(bizKey, fromDt, toDt, trnsTypeFilter, keywordPattern)   -- SAME WHERE minus the erp_type predicate
     11. map projections → ErpTrnsHistoryRow[] (resolve erp_type_key + trns_type_key)
  → ErpTrnsHistoryListResponse { list[], total_count, total_count_wecms, total_count_erp, total_count_deleted, page_number, page_size, total_pages }
```

- The **counts query intentionally drops the `ERP_TYPE` predicate** so all four tab chips reflect the
  same date / type / keyword scope simultaneously — mirrors legacy `getDynamicCNT`
  (`comm_0219_001_r001_act.jsp:77-108`). `SUM(CASE WHEN erp_type='W'…)` is wrapped in `COALESCE(…,0L)`
  so an empty set returns zero-counts (not NULL → NPE).
- `keyword` matches **`reg_id` only** — never `org_file_nm` / `save_file_nm` (legacy IDO parity).
- No `biz_key` field on the wire; even if a caller sent one it would be ignored.

## 2. Download

```
GET /api/v1/settings/erp-transaction-history/{seqNo}/download
  → controller: reject seqNo <= 0 → WBE_VALIDATION (defensive; the path converter already 400s non-numeric)
  → service.resolveDownload(seqNo)   @Transactional(readOnly = true)
      1. bizKey = TenantContext.currentBizKey()
      2. repo.findById(new ErpTrnsHstrId(bizKey, seqNo))  → WBE2014 if absent (row not found OR wrong tenant — one envelope)
      3. if file_size == null || == 0  → WBE2015          (batch row with no physical file)
      4. path = fileResolver.resolve(row.bizKey, row.saveFileNm)
      5. if !Files.isReadable(path) || !isRegularFile(path)  → WBE2015
      → DownloadResource(path, orgFileNm, saveFileNm, fileSize)
  → controller streams InputStreamResource:
        Content-Type: application/octet-stream
        Content-Disposition: attachment; filename*=UTF-8''<save_file_nm>   (RFC 5987)
        X-Content-Type-Options: nosniff
        Content-Length: <file_size>
```

- The `Content-Disposition` filename is the legacy **`SAVE_FILE_NM`** (NAS storage name), not the
  user's `ORG_FILE_NM` — intentional audit-trail parity with JEX
  `FileUtils.downloadFileAsBinary(out, util, file, save_file_nm)`.
- `WBE2014` deliberately merges "not found" + "wrong tenant" — never leak another tenant's row.

## 3. Thumbnail (stub)

```
GET /api/v1/settings/erp-transaction-history/{seqNo}/thumbnail
  → controller logs WARN, throws BusinessException("WBE9001")   ("Endpoint is not yet implemented")
```

Legacy `comm_0219_001_r002_view.jsp` serves a 640px JPEG via `?cmd=getthumb`. No current frontend
consumer needs thumbnails — surfaced as `WBE9001` so a future caller fails loudly rather than seeing a
misleading 404.

## 4. Export (XLSX)

```
POST /api/v1/settings/erp-transaction-history/export   body: ExcelExportRequest { screenId, dymAreaId, fileName, title, filters, sort, columns }
  → excelExportService.exportTo(response, request)        (shared common/export/ pipeline)
      → ExportableScreenRegistry resolves screenId="erp-transaction-history" → ErpTrnsHistoryExportProvider
      → provider.fetch(request):
          filters → fromDate / toDate / erpType / trnsTypeCd / keyword
          sort    → sort.field() / sort.direction()
          rows = service.listAll(...)                     -- SAME validation + filter + sort as list(), unpaged
          subHeader = "<N> record(s)"
          map each row → FIELD_ID-keyed map (REG_DTM, ERP_TYPE, TRNS_TYPE_CD, ORG_FILE_NM, FILE_SIZE, REG_USER, REG_ID)
      → service intersects request columns ∩ provider.allowedFieldIds() ∩ persisted View Setting visibility
      → ExcelWorkbookWriter (POI SXSSF) streams the workbook; cap MAX_ROWS = 50_000 → WBE2011
```

⚠️ Export depends on a `SYS_SCR_OPTION_WECMS` seed under `erp-transaction-history` that the live
`V156__sys_scr_option_wecms.sql` does **not** ship (it explicitly excludes the slug at line 94). Without
the seed the column intersection is empty → `WBE2013`. See
[refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-4.

---

## 5. The inert writer (`ErpTrnsHistoryWriter`)

The package carries a shared writer service for *other* features to record ERP-exchange files — it is
**not** part of any read/download/export flow and **has no live caller in the current tree**:

```
recordUploadedFile(trnsTypeCd, sourceFile, orgFileNm)   → ERP_TYPE='E'   (ERP → WeCMS upload)
recordDeletedFile(trnsTypeCd, sourceFile, orgFileNm)    → ERP_TYPE='D'   (soft-delete of an upload)
recordExportedFile(trnsTypeCd, sourceFile, orgFileNm)   → ERP_TYPE='W'   (WeCMS → ERP file pulled out)
  each (REQUIRES_NEW):
      1. validate trnsTypeCd / sourceFile readable; bail with WARN log if not (never throws to caller)
      2. copy source bytes into <root>/<biz_key>/erp-transaction-history/<save_file_nm>
      3. SEQ_NO via LegacyFunctionRepository.nextErpTrnsSeqNo(bizKey)   (single wb_get_next_val funnel)
      4. INSERT ERP_TRNS_HSTR row (saveAndFlush; one retry on DataIntegrityViolation PK race)
```

The writer mirrors legacy `ErpTrnsHisHelper.erpTrnsHisUploader`. It exists so that — when a future
Payment / Transfer feature lands — that feature calls the writer from its own `@Transactional` scope
(OQ-1 design), rather than this feature exposing an upload endpoint. Failures are swallowed + WARN-logged
so the caller's primary mutation is never rolled back by an audit-write failure.

---

## Tenant + audit invariants (every path)

- **Tenant** comes only from `TenantContext.currentBizKey()` (signed JWT) — never a request body/param.
  `bizKey` is the leading PK component of every query.
- **No audit row.** This read-only feature writes no `AUDIT_LOGS_INFM` row (mirrors the
  `firm-banking-history` precedent — the legacy `mntLogYn=Y` per-read filter is intentionally not
  ported for read paths). See [functional-specification.md §Audit](functional-specification.md).
