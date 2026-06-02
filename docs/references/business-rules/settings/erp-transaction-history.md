# ERP Transaction History — Spring Boot backend design

**Refactor slug:** `erp-transaction-history`
**Sibling docs:** [feature spec](../../../features/settings/transaction-history.md) · [JEX → Spring mapping](../../migration/erp-transaction-history-mapping.md) · [DB schema](../../database/settings/erp-transaction-history-schema.md) · [frontend design](../../../../../wecms-web/docs/features/settings/transaction-history.md) · [plan](../../../planning/settings/erp-transaction-history-plan.md).
**Mirrors live JEX:** `comm_0219_001_r001` (list + 4 counts) and `comm_0219_001_r002` (download). Upload (`_c001`) and batch writers are out of scope — see feature spec §2.

This document is the design contract for the Phase 2 backend implementation. No Java code is written in Phase 1.

---

## 1. Package layout

```
com.kosign.wecms.domain.setting.erptransactionhistory/
├── controller/
│   └── ErpTrnsHistoryController.java
├── service/
│   ├── ErpTrnsHistoryService.java
│   ├── ErpTrnsHistorySortField.java          (implements common/web/SortableField)
│   └── ErpTrnsHistoryFileResolver.java       (path composition + existence check)
├── repository/
│   └── ErpTrnsHstrRepository.java
├── entity/
│   ├── ErpTrnsHstr.java                      (@Table("ERP_TRNS_HSTR"), @IdClass composite key)
│   └── ErpTrnsHstrId.java                    (Serializable, equals + hashCode)
├── dto/
│   ├── ErpTrnsHistoryListRequest.java        (record; query params)
│   ├── ErpTrnsHistoryListResponse.java       (record)
│   └── ErpTrnsHistoryRow.java                (record — one wire row)
└── mapper/
    └── ErpTrnsHistoryMapper.java             (entity row → DTO record, includes i18n key resolution)
```

Mirrors the `domain/setting/firmbankinghistory/` layout. No new shared
infrastructure required.

---

## 2. Entity

```java
@Entity
@Table(name = "ERP_TRNS_HSTR")
@IdClass(ErpTrnsHstrId.class)
public class ErpTrnsHstr {
    @Id @Column(name = "BIZ_KEY",   length = 20, nullable = false) private String bizKey;
    @Id @Column(name = "SEQ_NO",    nullable = false)              private Long   seqNo;

    @Column(name = "ORG_FILE_NM",  length = 500, nullable = false) private String orgFileNm;
    @Column(name = "SAVE_FILE_NM", length = 500, nullable = false) private String saveFileNm;
    @Column(name = "FILE_SIZE",    nullable = false)               private Long   fileSize;

    @Column(name = "ERP_TYPE",     length = 1,  nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR) /* CHAR(1) per DEV — same pattern as SysScrOptionWecms.sortYn */
    private String erpType;

    @Column(name = "TRNS_TYPE_CD", length = 2,  nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String trnsTypeCd;

    @Column(name = "REG_ID",       length = 100, nullable = false) private String regId;
    @Column(name = "REG_DTM",      length = 14,  nullable = false) private String regDtm; /* yyyyMMddHHmmss */
}
```

- Does **not** extend `BaseEntity` — the legacy table has only `REG_ID` /
  `REG_DTM` (no `MOD_*`). Same approach as `UserLoginHis`.
- `erp_type` / `trns_type_cd` stay as `String` rather than mapping to
  enums on the column ([wecms-api/CLAUDE.md §"Domain enums"](../../../../CLAUDE.md) — never `@Enumerated(EnumType.STRING)` on a legacy-code column). Conversion to/from the
  refactor enum happens at the service / mapper layer via `ErpType.fromCode(...)`.
- Composite id class `ErpTrnsHstrId` is a `Serializable` record (or POJO
  with `equals` + `hashCode`) holding `bizKey` + `seqNo`.

Flyway impact: the `erp_trns_hstr` table is already in the PROD Active
Table List. The local DB inherits it via the existing schema-parity import
once Sprint adoption fires. **A new migration is needed** to seed real DEV
rows for tenant `VN_VNM0004_000001` (≤30 rows, idempotent
`INSERT … ON CONFLICT DO NOTHING`, per [wecms-api/CLAUDE.md §"Data Migration Rule"](../../../../CLAUDE.md)).
Schema-parity audit (column count + types + PK) should be re-run as part
of Phase 2 kickoff and corrected via additive migration if drift is found.

---

## 3. Repository

```java
public interface ErpTrnsHstrRepository extends JpaRepository<ErpTrnsHstr, ErpTrnsHstrId> {

    @Query("""
        SELECT new com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryProjection(
            e.seqNo, e.orgFileNm, e.saveFileNm, e.fileSize, e.erpType, e.trnsTypeCd,
            e.regDtm, e.regId, u.userNm
        )
        FROM ErpTrnsHstr e
        LEFT JOIN UserInfm u ON u.userId = e.regId
        WHERE e.bizKey = :bizKey
          AND SUBSTRING(e.regDtm, 1, 8) BETWEEN :fromDt AND :toDt
          AND (:erpType    IS NULL OR e.erpType    = :erpType)
          AND (:trnsType   IS NULL OR e.trnsTypeCd = :trnsType)
          AND (:srchWd     IS NULL OR LOWER(e.regId) LIKE LOWER(CONCAT('%', :srchWd, '%')) ESCAPE '\\')
        """)
    Page<ErpTrnsHistoryProjection> search(
        @Param("bizKey")   String bizKey,
        @Param("fromDt")   String fromDt,
        @Param("toDt")     String toDt,
        @Param("erpType")  String erpType,
        @Param("trnsType") String trnsType,
        @Param("srchWd")   String srchWd,
        Pageable pageable);

    @Query("""
        SELECT new com.kosign.wecms.domain.setting.erptransactionhistory.dto.ErpTrnsHistoryCounts(
            COUNT(e),
            SUM(CASE WHEN e.erpType = 'W' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.erpType = 'E' THEN 1 ELSE 0 END),
            SUM(CASE WHEN e.erpType = 'D' THEN 1 ELSE 0 END)
        )
        FROM ErpTrnsHstr e
        WHERE e.bizKey = :bizKey
          AND SUBSTRING(e.regDtm, 1, 8) BETWEEN :fromDt AND :toDt
          AND (:trnsType IS NULL OR e.trnsTypeCd = :trnsType)
          AND (:srchWd   IS NULL OR LOWER(e.regId) LIKE LOWER(CONCAT('%', :srchWd, '%')) ESCAPE '\\')
        """)
    ErpTrnsHistoryCounts countAllDirections(
        @Param("bizKey")   String bizKey,
        @Param("fromDt")   String fromDt,
        @Param("toDt")     String toDt,
        @Param("trnsType") String trnsType,
        @Param("srchWd")   String srchWd);
}
```

Notes:

- The `LEFT JOIN UserInfm u ON u.userId = e.regId` replaces the legacy
  `WB_GET_USER_NM(REG_ID)`. **Important:** `USER_INFM` is currently global
  (no `BIZ_KEY` constraint in the join) — matches the legacy function which
  also ignored tenant. If audit reveals cross-tenant collisions, tighten
  the join in a follow-up.
- The counts query drops the `erpType` predicate (mirrors the JEX
  `getDynamicCNT` behaviour — all four tab counts honor the same date /
  type / search context simultaneously).
- `srchWd` is LIKE-escape-processed in the service before being passed in
  (mirror `FirmBankingHistoryService.escapeLikePattern`).
- Service issues **both queries inside the same `@Transactional(readOnly = true)`**
  scope so the four counts and the page slice are read at the same
  read-only snapshot.
- Projection records `ErpTrnsHistoryProjection` and `ErpTrnsHistoryCounts`
  are package-private inside `dto/` — never escape onto the wire.

---

## 4. Service

```java
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ErpTrnsHistoryService {

    /** Default sort — OQ-7 resolved 2026-05-11: regDtm DESC, seqNo DESC. */
    private static final Sort DEFAULT_SORT =
        Sort.by(Sort.Order.desc("regDtm"), Sort.Order.desc("seqNo"));
    private static final int  DEFAULT_PAGE_SIZE = 15;
    private static final int  MAX_PAGE_SIZE     = 100;

    private final ErpTrnsHstrRepository       repository;
    private final ErpTrnsHistoryFileResolver  fileResolver;
    private final ErpTrnsHistoryMapper        mapper;
    private final TenantProperties            tenant;

    public ErpTrnsHistoryListResponse search(ErpTrnsHistoryListRequest req) {
        String bizKey = TenantContext.currentBizKey();          /* never input */

        LocalDate fromDt = parseDate8(req.fromDt(), "from_dt"); /* throws BusinessException(WBE_VALIDATION) on parse */
        LocalDate toDt   = parseDate8(req.toDt(),   "to_dt");
        if (fromDt.isAfter(toDt)) {
            throw new BusinessException("WBE_VALIDATION", "from_dt must be on or before to_dt");
        }

        String erpType  = emptyToNull(req.erpType());           /* "" → null so the predicate falls off */
        String trnsType = emptyToNull(req.trnsTypeCd());
        String srchWd   = escapeLikePattern(req.srchWd());      /* null when blank */

        Pageable pageable = PageRequest.of(
            Math.max(0, req.page() - 1),                        /* wire is 1-based → Pageable is 0-based */
            clamp(req.pageSize(), 1, MAX_PAGE_SIZE, DEFAULT_PAGE_SIZE),
            resolveSort(req.sortField(), req.sortDirection())
        );

        Page<ErpTrnsHistoryProjection> page = repository.search(
            bizKey, fromDt.format(YYYYMMDD), toDt.format(YYYYMMDD),
            erpType, trnsType, srchWd, pageable);

        ErpTrnsHistoryCounts counts = repository.countAllDirections(
            bizKey, fromDt.format(YYYYMMDD), toDt.format(YYYYMMDD),
            trnsType, srchWd);

        List<ErpTrnsHistoryRow> rows =
            page.getContent().stream().map(mapper::toRow).toList();

        return new ErpTrnsHistoryListResponse(
            rows,
            counts.totalCount(),
            counts.totalCountWecms(),
            counts.totalCountErp(),
            counts.totalCountDeleted(),
            page.getTotalPages(),
            pageable.getPageNumber() + 1,
            pageable.getPageSize()
        );
    }

    public ResolvedFile resolveDownload(long seqNo) {
        String bizKey = TenantContext.currentBizKey();
        ErpTrnsHstr row = repository.findById(new ErpTrnsHstrId(bizKey, seqNo))
            .orElseThrow(() -> new BusinessException("WBE2014"));
        Path path = fileResolver.resolve(bizKey, row.getSaveFileNm());
        if (!Files.isReadable(path) || row.getFileSize() == 0L) {
            throw new BusinessException("WBE2015");
        }
        return new ResolvedFile(path, row.getOrgFileNm(), row.getSaveFileNm());
    }
}
```

- `DEFAULT_SORT` mirrors OQ-7 (resolved 2026-05-11 — `regDtm DESC, seqNo DESC`).
- `resolveSort(sortField, sortDirection)` falls back to `DEFAULT_SORT`
  on null / unknown input, per the common-sort contract; user-chosen
  primary is appended with `DEFAULT_SORT` as a stable tiebreaker.
- `ResolvedFile` is a controller-only record: `(Path path, String orgFileNm, String saveFileNm)`.
  Controller streams via `Files.newInputStream(path)` wrapped in
  `InputStreamResource`. Filename in `Content-Disposition` is
  `save_file_nm` (legacy parity); the `orgFileNm` is logged but not used
  for the header.

### Thumbnail variant

```java
public ResolvedFile resolveThumbnail(long seqNo, int maxWidth) {
    ResolvedFile original = resolveDownload(seqNo);
    /* JPEG thumbnail at maxWidth px; mirrors legacy FileUtils.downloadThumbFileAsBinary(640) */
    Path thumb = thumbnailGenerator.generate(original.path(), maxWidth);
    return new ResolvedFile(thumb, "thumb_" + original.orgFileNm(), "thumb_" + original.saveFileNm());
}
```

Thumbnail generation is **deferred** for Phase 2 (no consumer in the
current Settings panel). Document the endpoint but throw `WBE_NOT_FOUND`
until a real consumer surfaces. OQ on whether to ship the endpoint at all
in Phase 2 — recommend: ship the controller method but route to a "not
implemented" `BusinessException("WBE9001")` placeholder.

---

## 5. Sort whitelist

```java
public enum ErpTrnsHistorySortField implements SortableField {
    REG_DTM("regDtm"),
    ERP_TYPE("erpType"),
    TRNS_TYPE_CD("trnsTypeCd"),
    REG_USER("regId");
    /* FILE not whitelisted — see migration doc §6 */

    private final String entityProperty;
    ErpTrnsHistorySortField(String p) { this.entityProperty = p; }
    @Override public String entityProperty() { return entityProperty; }

    public static ErpTrnsHistorySortField parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try { return valueOf(raw.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException ignored) { return null; }
    }
}
```

`USER` FIELD_ID (legacy `REG_USER` column header) maps to the entity
property `regId` since `userNm` lives on the `UserInfm` join, not the
`ErpTrnsHstr` entity. Sorting by the join column is not in scope — the
legacy screen sorts by the login id too.

---

## 6. DTOs (Jakarta Bean Validation on records)

### 6.1 Request

```java
public record ErpTrnsHistoryListRequest(
    @NotBlank @Pattern(regexp = "\\d{8}") String fromDt,
    @NotBlank @Pattern(regexp = "\\d{8}") String toDt,
    @Pattern(regexp = "[WED]?")           String erpType,
    @Pattern(regexp = "[0-9]{2}?")        String trnsTypeCd,
    @Size(max = 100)                      String srchWd,
    @Min(1)                               Integer page,
    @Min(1) @Max(100)                     Integer pageSize,
    @Pattern(regexp = "[A-Z_]{2,30}?")    String sortField,
    @Pattern(regexp = "ASC|DESC|asc|desc|") String sortDirection
) {}
```

Controller resolves `null` page → `1`, `null` pageSize → `15`.

### 6.2 Row

```java
public record ErpTrnsHistoryRow(
    Long   seqNo,
    String orgFileNm,
    String saveFileNm,
    long   fileSize,
    String erpType,        /* W / E / D — raw code */
    String erpTypeKey,     /* "settings.erp_transaction_history.direction.<…>" */
    String trnsTypeCd,     /* "01"..."17" — raw code */
    String trnsTypeKey,    /* "settings.erp_transaction_history.type.<NN>" */
    String regDtm,         /* yyyyMMddHHmmss */
    String regId,
    String userNm          /* nullable — frontend falls back to regId */
) {}
```

### 6.3 Response

```java
public record ErpTrnsHistoryListResponse(
    List<ErpTrnsHistoryRow> list,
    long                    totalCount,
    long                    totalCountWecms,
    long                    totalCountErp,
    long                    totalCountDeleted,
    int                     totalPages,
    int                     pageNumber,    /* 1-based */
    int                     pageSize
) {}
```

Field naming on the wire: `JacksonConfig` global SNAKE_CASE strategy
converts `totalCountWecms` → `total_count_wecms` etc. No per-field
`@JsonProperty` needed.

---

## 7. Controller

```java
@RestController
@RequestMapping("/api/v1/settings/erp-transaction-history")
@RequiredArgsConstructor
@Validated
public class ErpTrnsHistoryController {

    private final ErpTrnsHistoryService service;

    @GetMapping
    public ApiResponse<ErpTrnsHistoryListResponse> list(
        @RequestParam("from_dt")        String  fromDt,
        @RequestParam("to_dt")          String  toDt,
        @RequestParam(value = "erp_type",       required = false) String  erpType,
        @RequestParam(value = "trns_type_cd",   required = false) String  trnsTypeCd,
        @RequestParam(value = "srch_wd",        required = false) String  srchWd,
        @RequestParam(value = "page",           required = false) Integer page,
        @RequestParam(value = "page_size",      required = false) Integer pageSize,
        @RequestParam(value = "sort_field",     required = false) String  sortField,
        @RequestParam(value = "sort_direction", required = false) String  sortDirection
    ) {
        var req = new ErpTrnsHistoryListRequest(
            fromDt, toDt, erpType, trnsTypeCd, srchWd, page, pageSize, sortField, sortDirection);
        return ApiResponse.ok(service.search(req));
    }

    @GetMapping("/{seqNo}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable @Positive long seqNo) throws IOException {
        var resolved = service.resolveDownload(seqNo);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION, contentDispositionRfc5987(resolved.saveFileNm()))
            .body(new InputStreamResource(Files.newInputStream(resolved.path())));
    }

    @GetMapping("/{seqNo}/thumbnail")
    public ResponseEntity<InputStreamResource> thumbnail(@PathVariable @Positive long seqNo) {
        /* Phase 2 deferred — surface as 501 / WBE_NOT_IMPLEMENTED for now. */
        throw new BusinessException("WBE9001");
    }
}
```

- Controller stays thin — no business logic, just unwrap to DTO + call
  service. Query parameter names are snake_case to mirror the wire JSON
  shape (per [wecms-api/CLAUDE.md §"API Naming"](../../../../CLAUDE.md)).
- `contentDispositionRfc5987` is the same helper `ExcelExportService` uses
  — extract once to `common/web/` if a second consumer appears.

### Filter chain

- `JwtAuthenticationFilter` protects everything (path is not in the public
  allow-list).
- `TraceIdFilter` runs first; the trace id ends up in every audit log line.
- Audit log row written with `WSVC_ID = 'erp-transaction-history'` for the
  list endpoint, `'erp-transaction-history/download'` for the download
  endpoint, per the slug convention.

---

## 8. Transactions & error handling

- Service is `@Transactional(readOnly = true)` at the class level — both
  the search and the count queries run in the same read-only snapshot.
- No mutating endpoints in this feature — no `@Transactional` override.
- Exceptions are routed through `GlobalExceptionHandler`:
  - `BusinessException("WBE_VALIDATION")` → HTTP 200 + envelope.
  - `BusinessException("WBE2014" | "WBE2015")` → HTTP 200 + envelope (business outcomes per the existing convention; the JSON `status.error = true`).
  - `MethodArgumentTypeMismatchException` (bad `seqNo` path) → HTTP 200 + `WBE_VALIDATION`.
  - `ConstraintViolationException` (path / param violation) → HTTP 200 + `WBE_VALIDATION`.
  - Anything else → `WBE9999`.
- The download endpoint produces binary; `GlobalExceptionHandler` still
  responds with the JSON envelope on failure since the response body is
  not yet committed when the exception is thrown.

---

## 9. messages.properties additions

```
WBE2014=ERP transaction history row not found.
WBE2015=ERP transaction history file is unavailable.
WBE9001=Endpoint is not yet implemented.
settings.erp_transaction_history.direction.wecms_to_erp=WeCMS → ERP
settings.erp_transaction_history.direction.erp_to_wecms=ERP → WeCMS
settings.erp_transaction_history.direction.deleted=Deleted
settings.erp_transaction_history.type.01=Transaction History (VND)
settings.erp_transaction_history.type.02=Vendor Scheduled Payment
... (17 entries total — see migration doc §8)
```

KO / VI / KM message files: add when verified translations land — leave EN
fallback in place until then ([wecms-api/CLAUDE.md §"Domain enums"](../../../../CLAUDE.md)).

---

## 10. Configuration

```yaml
# application-local.yml
wecms:
  erp-transaction-history:
    storage-root: "C:/WECMS PROJECT/File Data"   # resolved 2026-05-11 — OQ-3
    thumbnail:
      enabled: false                              # Phase 2 thumbnail is deferred
```

`ErpTrnsHistoryFileResolver` reads the property via a typed
`@ConfigurationProperties("wecms.erp-transaction-history")` record. Path
composition is `<storage-root>/<biz_key>/erp-transaction-history/<save_file_nm>` —
the refactor folder name is the slug, not the legacy `comm_0219_001_c001`
NAS subfolder.

**Concrete example for the test tenant:**

```
C:/WECMS PROJECT/File Data/VN_VNM0004_000001/erp-transaction-history/Account Inquiry(VND)_30042026.xls
```

The path contains a literal space (`File Data`). Java `Paths.get(storageRoot, bizKey, "erp-transaction-history", saveFileNm)`
handles the space natively on Windows — no quoting or escaping needed at
the Java level. The YAML value is quoted to keep the parser happy.

Per-developer override is supported via env var
`WECMS_ERP_TRNS_HSTR_STORAGE_ROOT` (Spring auto-binds the env var to the
nested property). No fail-fast — the YAML default covers Sokny / Arthur /
Mr. Youvita's standard workspace; a developer with a different drive layout
sets the env var locally.

Fixture files are committed under
`src/test/resources/erp-transaction-history/fixtures/` and copied to
`<storage-root>/VN_VNM0004_000001/erp-transaction-history/` on Spring
startup (or on first download call). Phase 2 ships with 3-5 fixture files
matching the seeded rows whose `FILE_SIZE > 0`. The fixture-copy step runs
only when `application-local` (and equivalent test profile) is active.

---

## 11. Tests

| Layer | Test | What it covers |
|---|---|---|
| `ErpTrnsHistoryServiceTest` (Mockito) | date parsing, from > to guard, sort whitelist (`REG_DTM` / `ERP_TYPE` / `TRNS_TYPE_CD` / `REG_USER` resolve; `FILE` / unknown → default), tenant scoping, page mapping (1-based ↔ 0-based), empty result envelope, like-escape on `srch_wd`, count math (W + E + D ≤ totalCount). | Pure service logic, repository mocked. |
| `ErpTrnsHistoryControllerTest` (standalone MockMvc + global `@ControllerAdvice`) | snake_case `@RequestParam` decoding; `from_dt` / `to_dt` missing → `WBE_VALIDATION` envelope; success returns `data.list[]` + four `total_count_*` keys; download endpoint sets `Content-Disposition` filename; 404-equivalent for unknown `seqNo` → `WBE2014`. | Wire shape end-to-end. |
| `ErpTrnsHistoryRepositoryTest` (`@DataJpaTest` against the local Postgres or H2) | search filters compose correctly; count query drops `erp_type` predicate but keeps `trns_type_cd` / `srch_wd` / date range; LEFT JOIN populates `userNm`; missing `UserInfm` row yields `null`. | JPQL correctness. |

Manual smoke (Phase 2 sign-off):
1. List with `from_dt = 30 days ago, to_dt = today` → counts match the seeded fixture set.
2. Filter `erp_type=E` → only ERP → WeCMS rows; `total_count_erp = total_count`.
3. Filter `srch_wd=lamine` → only rows with the matching `reg_id`.
4. Sort by `REG_DTM DESC` (default) → newest first; tiebreaker `seq_no DESC`.
5. Download `seq_no=754` (FILE_SIZE>0, fixture present) → 200 + bytes.
6. Download `seq_no=999` (not in tenant) → `WBE2014`.
7. Audit log shows `wsvc_id='erp-transaction-history'` for the list call, `'erp-transaction-history/download'` for the download.

---

## 12. Cross-cutting integrations

- **View Setting** — **not adopted (OQ-FE-2, resolved 2026-05-11).** Phase 2
  ships no `SYS_SCR_OPTION_WECMS` seed for `erp-transaction-history`.
  The legacy panel has no toolbar list-icon button and the refactor
  mirrors that. A future MR can adopt View Setting by seeding the rows
  and wiring the frontend popover without any controller / service / DTO
  change ([wecms-api/CLAUDE.md §"Screen Option"](../../../../CLAUDE.md) — endpoint set is global).
- **Common sort** — the new `ErpTrnsHistorySortField` enum slots into the
  existing common-sort contract; no shared primitive changes.
- **Excel export** — out of scope for Phase 2. The JEX screen does not
  expose an Excel export toolbar (the legacy view JSP has no Export
  button). Future need would adopt the shared `common/export/` pipeline
  ([wecms-api/CLAUDE.md §"Excel export"](../../../../CLAUDE.md)) by adding
  one `ErpTrnsHistoryExportProvider` — no service change.
- **Audit logging** — handled by the existing `AuditLogFilter` that writes
  `AUDIT_LOGS_INFM` for every authenticated request. Verify on Phase 2
  kickoff that the slug `erp-transaction-history` lands in `WSVC_ID`
  (per the 2026-05-09 cutover) and that the V67 back-fill rule does not
  apply (it's specifically for the firm-banking-settings legacy rows).

---

## 13. Changelog

- 2026-05-11 — v0.1 — initial backend design (Sokny, AI-assisted).
