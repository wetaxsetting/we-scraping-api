# VAS Account — Backend Design (Spring Boot)

Slug: `vas-account`. Package: `com.kosign.wecms.domain.setting.vasaccount`.
Base URI: `/api/v1/settings/vas-account`. Wire: lowercase snake_case (global
Jackson `SNAKE_CASE`). Envelope: `{ status, data, timestamp?, path?, trace_id? }`.

> **Signatures only.** This is planning, not code. Method bodies are described
> at the level of "what the method does" — the implementing MR writes the
> actual code.

---

## 1. Package layout

```
com.kosign.wecms.domain.setting.vasaccount
├── controller/
│   └── VasAccountController.java
├── service/
│   ├── VasAccountService.java
│   ├── VasAccountExcelService.java
│   ├── VasAccountSortField.java              — implements common/web/SortableField
│   └── VasAcctSeqNoGenerator.java            — Java-side replacement for wb_get_next_val branch
├── export/
│   └── VasAccountExportProvider.java         — implements common/export/ExportRowProvider
│                                                 (registered via @Component → ExportableScreenRegistry)
├── repository/
│   └── BusiVasAcctInfmRepository.java
├── entity/
│   ├── BusiVasAcctInfm.java                  — JPA entity, @IdClass(BusiVasAcctInfmId.class)
│   └── BusiVasAcctInfmId.java                — Serializable composite key (bizKey, vasAcctSeqNo)
└── dto/
    ├── VasAccountRow.java                    — list row + detail (same shape)
    ├── VasAccountListResponse.java           — paged list wrapper
    ├── VasAccountCreateRequest.java
    ├── VasAccountUpdateRequest.java
    ├── VasAccountExcelPreviewRow.java
    ├── VasAccountExcelPreview.java
    ├── VasAccountExcelImportRequest.java
    ├── VasAccountExcelImportResult.java
    └── VasAccountExcelFieldError.java        — { field, code, message } — per-cell preview error
```

> **`VasAccountCellValidationRequest` is intentionally absent** — the
> single-cell re-validation endpoint mapped to legacy
> `comm_0217_002_r002` was retired in the implementation MR. The PO
> call (2026-05-11) dropped Type / English Name from the upload preview,
> and the dialog no longer offers inline cell editing — so there is no
> consumer for a single-cell server-side re-validation. See audit F2.

Domain enums (shared, NOT under this package — they live in `common/enums/` per the modernized enum policy):

```
com.kosign.wecms.common.enums
├── VasAcctType.java          — DUAL("0"), CREDIT("1"), DEBIT("2"); i18nKey() returns settings.vas_account.type.{...}
└── AStts.java                — ACTIVE("A"), INACTIVE("I"); i18nKey() returns settings.vas_account.status.{...}
```

Both follow the standard refactor enum shape (`code()` + `fromCode()` + `i18nKey()` — no `@JsonValue`, no `@JsonCreator`, no `getLabel()`, no `EnumType.ORDINAL`). DB stays `CHAR(1)` carrying the legacy code; the service translates via `Enum.fromCode(...)` and wires the code+key pair into the DTO.

---

## 2. Entity

### `BusiVasAcctInfm`

```java
@Entity
@Table(name = "BUSI_VAS_ACCT_INFM")
@IdClass(BusiVasAcctInfmId.class)
public class BusiVasAcctInfm extends BaseEntity {
    @Id @Column(name = "BIZ_KEY", length = 20, nullable = false)         private String bizKey;
    @Id @Column(name = "VAS_ACCT_SEQ_NO", precision = 10)                private Long   vasAcctSeqNo;
    @Column(name = "VAS_ACCT_CD",   length = 15, nullable = false)        private String vasAcctCd;
    @Column(name = "VAS_ACCT_NM",   length = 50)                          private String vasAcctNm;
    @Column(name = "VAS_ACCT_ENM",  length = 50)                          private String vasAcctEnm;
    @Column(name = "VAS_ACCT_TYPE", length = 1, columnDefinition = "char(1)") @JdbcTypeCode(SqlTypes.CHAR)
                                                                          private String vasAcctType;   // legacy code "0"|"1"|"2"
    @Column(name = "STTS",          length = 1, columnDefinition = "char(1)") @JdbcTypeCode(SqlTypes.CHAR)
                                                                          private String stts;          // legacy code "A"|"I"
    @Column(name = "CONT",          length = 200)                         private String cont;
    @Column(name = "DEL_YN",        length = 1, columnDefinition = "char(1)") @JdbcTypeCode(SqlTypes.CHAR)
                                                                          private String delYn;
}
```

Notes:

- `BaseEntity` supplies `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM` (audit columns). The legacy DB stores `*_DTM` as `VARCHAR(14)` `yyyyMMddHHmmss`; matches the pattern already used for ERP Transaction History and Firm Banking — no entity-level change vs. the existing `BaseEntity`.
- Enums (`vasAcctType`, `stts`) stay as `String` in the entity (refactor enum policy — no `@Enumerated(EnumType.STRING)` on legacy-coded columns). Service-layer code uses `VasAcctType.fromCode(...)` / `AStts.fromCode(...)` to resolve.
- `delYn` similarly stays as `String` mapped via `@JdbcTypeCode(SqlTypes.CHAR)` (matches DEV's `bpchar(1)`).

### `BusiVasAcctInfmId`

```java
public class BusiVasAcctInfmId implements Serializable {
    private String bizKey;
    private Long   vasAcctSeqNo;
    // equals + hashCode + getters
}
```

---

## 3. Repository

### `BusiVasAcctInfmRepository extends JpaRepository<BusiVasAcctInfm, BusiVasAcctInfmId>`

```java
Optional<BusiVasAcctInfm> findByBizKeyAndVasAcctSeqNoAndDelYn(String bizKey, Long vasAcctSeqNo, String delYn);

// list — tenant + del_yn='N' baseline; search & status applied via Specification
Page<BusiVasAcctInfm> findAll(Specification<BusiVasAcctInfm> spec, Pageable pageable);  // pageable carries the Sort

boolean existsByBizKeyAndVasAcctCdAndDelYn(String bizKey, String vasAcctCd, String delYn);

// used by the Excel "replace all" path
@Modifying
@Query("UPDATE BusiVasAcctInfm v SET v.delYn='Y', v.modId = :modId, v.modDtm = :modDtm "
     + "WHERE v.bizKey = :bizKey AND v.delYn = 'N'")
int softDeleteAllForTenant(String bizKey, String modId, String modDtm);
```

A `VasAccountSpecifications` static factory inside the service package builds the `Specification<BusiVasAcctInfm>` from `(searchWd, statuses)`. The repository never sees user input strings directly.

---

## 4. DTOs (signatures only)

```java
public record VasAccountRow(
    String vasAcctSeqNo,   // wire: vas_acct_seq_no — STRING to dodge JS precision loss
    String vasAcctCd,
    String vasAcctNm,
    String vasAcctEnm,
    String vasAcctType,    // legacy code "0"|"1"|"2"
    String vasAcctTypeKey, // i18n key settings.vas_account.type.*
    String stts,           // legacy code "A"|"I"
    String sttsKey,        // i18n key settings.vas_account.status.*
    String cont,
    String modId,
    String modDtm
) {}

public record VasAccountListResponse(
    /** Rows on the requested page. */
    List<VasAccountRow> list,
    /** Unpaged filtered total (after search_wd + stts). */
    long totalCount,
    /** Zero-based page index (Spring Data convention). */
    int pageNumber,
    /** Rows per page actually used by the server (after clamp). */
    int pageSize,
    /** Total page count for the unpaged filtered set. */
    int totalPages
) {}

public record VasAccountCreateRequest(
    @NotBlank @Size(min=3, max=15) @Pattern(regexp="^[A-Za-z0-9_]+$") String vasAcctCd,
    @NotBlank @Size(max=50) String vasAcctNm,
    @Size(max=50) String vasAcctEnm,
    @NotBlank @Pattern(regexp="^[012]$") String vasAcctType,
    @NotBlank @Pattern(regexp="^[AI]$") String stts,
    @Size(max=200) String cont
) {}

public record VasAccountUpdateRequest(/* same fields as Create */) {}

public record VasAccountExcelPreviewRow(
    int rowIndex,
    String vasAcctCd,
    String vasAcctNm,
    String vasAcctType,
    String vasAcctEnm,
    String cont,
    String stts,
    Map<String, FieldError> errors      // per-field { code, message } — empty when row valid
) {}

public record VasAccountExcelPreview(int totCnt, int sucCnt, int errCnt, List<VasAccountExcelPreviewRow> rec) {}

public record VasAccountImportRequest(
    @NotBlank @Pattern(regexp="^[AR]$") String upldOptn,
    @NotEmpty List<VasAccountExcelPreviewRow> rec
) {}

public record VasAccountImportResult(int insertedCount, int replacedCount) {}

public record VasAccountCellValidationRequest(
    @NotBlank @Pattern(regexp="^(VAS_ACCT_CD|VAS_ACCT_NM|VAS_ACCT_TYPE|VAS_ACCT_ENM|CONT|STTS)$") String fieldId,
    String value,
    String currentVasAcctSeqNo            // optional — when validating an edit-in-place cell on an existing row
) {}
```

`FieldError` is a tiny record `{ String code, String message }` — same pattern as existing form-validation responses.

---

## 5. Controller (`VasAccountController`)

```java
@RestController
@RequestMapping("/api/v1/settings/vas-account")
@RequiredArgsConstructor
public class VasAccountController {

    private final VasAccountService vasAccountService;
    private final VasAccountExcelService vasAccountExcelService;
    private final ExcelExportService excelExportService;

    @GetMapping
    ApiResponse<VasAccountListResponse> list(
        @RequestParam(value = "search_wd",      required = false)         String       searchWd,
        @RequestParam(value = "stts",           required = false)         List<String> statuses,
        @RequestParam(value = "sort_field",     required = false)         String       sortField,
        @RequestParam(value = "sort_direction", required = false)         String       sortDirection,
        @RequestParam(value = "page",           defaultValue = "0")       int          page,
        @RequestParam(value = "size",           defaultValue = "0")       int          size);
        // size <= 0  → DEFAULT_PAGE_SIZE (20); size > MAX_PAGE_SIZE (100) → clamped.
        // page < 0   → 0. Mirrors ErpTrnsHistoryController.list contract.

    @GetMapping("/{vasAcctSeqNo}")
    ApiResponse<VasAccountRow> detail(@PathVariable Long vasAcctSeqNo);

    @PostMapping
    ApiResponse<VasAccountRow> create(@Valid @RequestBody VasAccountCreateRequest req);

    @PutMapping("/{vasAcctSeqNo}")
    ApiResponse<VasAccountRow> update(@PathVariable Long vasAcctSeqNo,
                                      @Valid @RequestBody VasAccountUpdateRequest req);

    @DeleteMapping("/{vasAcctSeqNo}")
    ApiResponse<Void> delete(@PathVariable Long vasAcctSeqNo);

    @PostMapping(value = "/excel/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<VasAccountExcelPreview> excelPreview(@RequestParam("file") MultipartFile file);

    @PostMapping("/excel/import")
    ApiResponse<VasAccountExcelImportResult> excelImport(@Valid @RequestBody VasAccountExcelImportRequest req);

    @PostMapping("/export")
    void export(@Valid @RequestBody ExcelExportRequest req, HttpServletResponse response);  // shared common/export/
}
```

> **No `/excel/cell/validate` endpoint** — retired per the PO call
> (2026-05-11) that dropped Type / English Name from the upload preview
> and the legacy inline-cell-edit UX. See audit F2.

Controller rules:

- **Thin.** Parse request, forward to service, wrap response. No business logic.
- **Authority gate (TODO).** Until `@PreAuthorize` lands at the project level, leave the controller un-annotated. Document the future `@PreAuthorize("hasRole('ADMIN')")` requirement in the class-level Javadoc + an inline TODO.
- **Tenant scope.** Never trust `biz_key` from the wire — service reads from `TenantContext.currentBizKey()`.
- **`vasAcctSeqNo` path param** is a `Long`. Spring's `MethodArgumentTypeMismatchException` handler returns `WBE_VALIDATION` for non-numeric input.

---

## 6. Service layer

### `VasAccountService` (CRUD + list)

```java
@Service
@RequiredArgsConstructor
public class VasAccountService {

    private final BusiVasAcctInfmRepository repo;
    private final VasAcctSeqNoGenerator     seqGen;
    private final AuditLogService           audit;       // existing shared service (firm-banking-settings precedent)
    private final TenantContext             tenant;      // static accessors per CLAUDE.md

    /** Default page size — small list, matches Settings-module convention. */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** Hard cap mirroring ErpTrnsHistoryService.MAX_PAGE_SIZE. */
    public static final int MAX_PAGE_SIZE     = 100;

    @Transactional(readOnly = true)
    public VasAccountListResponse list(String searchWd, List<String> statuses,
                                       String sortField, String sortDirection,
                                       int page, int size);

    @Transactional(readOnly = true)
    public VasAccountRow detail(Long vasAcctSeqNo);

    @Transactional
    public VasAccountRow create(VasAccountCreateRequest req);

    @Transactional
    public VasAccountRow update(Long vasAcctSeqNo, VasAccountUpdateRequest req);

    @Transactional
    public void softDelete(Long vasAcctSeqNo);
}
```

Method-level semantics:

- **`list`** — clamp `page` to ≥ 0 and `size` to `[1, MAX_PAGE_SIZE]` (≤ 0 → `DEFAULT_PAGE_SIZE`). Build a `Pageable` from `(page, size, Sort)` where `Sort` comes from `VasAccountSortField.parse(sortField)` + `SortDirection.parse(sortDirection)`, with `DEFAULT_SORT = Sort.by(asc("vasAcctCd"))`. Apply Specification `(biz_key=current) AND (del_yn='N') AND (search_wd ILIKE OR null) AND (stts IN (...) OR null)`. Hand the `Pageable` to `repo.findAll(spec, pageable)` → `Page<BusiVasAcctInfm>`. Map each entity → `VasAccountRow` via `VasAccountMapper.toRow(...)`. Wrap in `VasAccountListResponse(rows, page.getTotalElements(), page.getNumber(), page.getSize(), page.getTotalPages())`.
- **`detail`** — `findByBizKeyAndVasAcctSeqNoAndDelYn(biz_key, vasAcctSeqNo, "N")`. Missing → `BusinessException("WBE2014")`.
- **`create`** — call `seqGen.next(bizKey)`, set `vasAcctSeqNo`. Run dedup check (`existsByBizKeyAndVasAcctCdAndDelYn` → `WBE0003` if true). Set defaults (`delYn='N'`, `stts` from request, `vasAcctType` from request, audit columns via `@PrePersist` on `BaseEntity`). Persist. Emit audit row (`audit.log(wsvcId="vas-account", action="CREATE", refNo=seqNo, refNo1=cd, detail=jsonSnapshot)`). Return the new row mapped via `VasAccountMapper.toRow`.
- **`update`** — load existing row (404 → `WBE2014`). If the new `vas_acct_cd` differs from the existing, run dedup check against new code. Copy fields, set `modId / modDtm` (via `@PreUpdate`). Persist. Emit audit row with old-vs-new JSON snapshot in `audit_logs_actv_dtl`.
- **`softDelete`** — load row (404 → `WBE2014`). Set `delYn='Y'`. Persist (or call `softDeleteAllForTenant` if bulk in a future MR). Emit audit row.

### `VasAccountExcelService` (Excel preview / validate / import)

```java
@Service
@RequiredArgsConstructor
public class VasAccountExcelService {

    private final BusiVasAcctInfmRepository repo;
    private final VasAcctSeqNoGenerator     seqGen;
    private final AuditLogService           audit;
    private final TenantContext             tenant;
    private final MessageResolver           messages;

    /** Parses the uploaded sheet, runs per-row validation, returns the preview. */
    public VasAccountExcelPreview preview(MultipartFile file);

    /** Re-validates one field for one row — used by inline edit on the preview grid. */
    public FieldError validateCell(VasAccountCellValidationRequest req);

    /** Persists the (already-validated) preview rows. Mode A = append, R = replace-all. */
    @Transactional
    public VasAccountImportResult import_(VasAccountImportRequest req);
}
```

Notes:

- **`preview`** — Apache POI streaming reader. Header row check (row index 2) against EN / KO / VI labels. Row cap 500 → `WBE_VAS_EXCEL_TOO_MANY_ROWS`. Per-cell validation produces `Map<String, FieldError>` per row. Duplicate-code check runs **only against persisted live rows** in preview; the importer re-checks at insert time (race-safe).
- **`import_`** with `R` mode runs `repo.softDeleteAllForTenant(bizKey, currentUserId, nowYmd14)` first, then inserts each row (auto-allocates `vas_acct_seq_no` via `seqGen`). Two audit rows: one `EXCEL_IMPORT_REPLACE` summary; the row-level changes are reflected in the wholesale soft-delete + bulk insert.
- **`import_`** with `A` mode skips the soft-delete; re-runs dedup check per row; throws on first dupe (legacy parity — atomic-or-reject is preferred over partial insert).
- **No transaction on `preview`** — it's a read-only validation pass that does not need a DB transaction (no writes).

### `VasAccountSortField` (common-sort whitelist)

```java
public enum VasAccountSortField implements SortableField {
    VAS_ACCT_CD  ("vasAcctCd"),
    VAS_ACCT_NM  ("vasAcctNm"),
    VAS_ACCT_TYPE("vasAcctType"),
    STTS         ("stts");

    private final String entityProperty;
    VasAccountSortField(String prop) { this.entityProperty = prop; }
    @Override public String entityProperty() { return entityProperty; }
    public static VasAccountSortField parse(String raw) { /* lenient — return null on unknown */ }
}
```

Common-sort rule: every column with `SORT_YN='Y'` in `SYS_SCR_OPTION_WECMS` for `web_svc_id='vas-account'` MUST have a constant here. The View Setting seed and this enum stay in sync — if a column is removed from the enum, the seed must flip its `SORT_YN` to `'N'` in the same MR.

### `VasAccountExportProvider` (shared export pipeline)

```java
@Component
@RequiredArgsConstructor
public class VasAccountExportProvider implements ExportRowProvider {

    private final VasAccountService vasAccountService;

    @Override public String screenId() { return "vas-account"; }

    @Override public Set<String> allowedFieldIds() {
        return Set.of("VAS_ACCT_CD", "VAS_ACCT_NM", "VAS_ACCT_TYPE", "VAS_ACCT_ENM", "STTS", "CONT");
    }

    @Override public ExportFetch fetch(ExcelExportRequest req);
}
```

`fetch` calls `vasAccountService.list(...)` with the request filter / sort, maps each row into the FIELD_ID-keyed `Map<String, Object>`, and computes the subheader `"<count> VAS Account(s)"`. The shared `ExcelExportService` handles the rest (visibility intersection, column ordering, POI streaming, RFC 5987 filename).

### `VasAcctSeqNoGenerator` (Java replacement for `wb_get_next_val` branch)

```java
@Service
@RequiredArgsConstructor
public class VasAcctSeqNoGenerator {
    private final BusiVasAcctInfmRepository repo;
    public Long next(String bizKey);
}
```

Implementation: `MAX(vas_acct_seq_no) WHERE biz_key = ?` + 1, executed inside the surrounding `@Transactional` write so the read+insert is atomic for the tenant. (Race-safe within the tenant — the per-tenant write serializes naturally via the per-tenant row-level conflict. If parallel writers across tenants become a concern, a per-tenant `PESSIMISTIC_WRITE` lock on a sentinel row or a tenant-scoped `INSERT … RETURNING` with retry is the follow-up.)

Per `wecms-api/CLAUDE.md` "DB Function / Legacy Function Policy", this is preferred over adding a new `wb_get_next_val` branch.

---

## 7. Transaction boundaries

| Layer | Annotation | Notes |
|---|---|---|
| `list` / `detail` / `preview` / `validateCell` / `export` | `@Transactional(readOnly = true)` | read-only |
| `create` / `update` / `softDelete` | `@Transactional` | atomic per row |
| `import_` (`A` append) | `@Transactional` | atomic insert batch; failure on any row rolls the whole batch back (legacy parity) |
| `import_` (`R` replace) | `@Transactional` | soft-delete + bulk insert in one transaction; failure rolls back the wipe (critical — user expects all-or-nothing) |
| `audit.log(...)` | inner `@Transactional(propagation = REQUIRES_NEW)` | audit write survives outer rollback (existing audit-service contract) |

---

## 8. Exception mapping

Inherits `GlobalExceptionHandler`. Domain-specific throws:

| Throw | Exception | HTTP | Code | When |
|---|---|---|---|---|
| `new BusinessException("WBE2014")` | `BusinessException` | 200 | `WBE2014` | row not found / wrong tenant on detail / update / delete |
| `new BusinessException("WBE0003", vasAcctCd)` | `BusinessException` | 200 | `WBE0003` | duplicate `vas_acct_cd` |
| `new BusinessException("WBE_VAS_EXCEL_TOO_MANY_ROWS")` | `BusinessException` | 200 | `WBE2020`-class | Excel preview > 500 rows |
| `new BusinessException("WBE_VAS_EXCEL_HEADER_INVALID", cellTitle)` | `BusinessException` | 200 | `WBE2021`-class | Excel header row doesn't match any of EN / KO / VI |
| bean validation | `MethodArgumentNotValidException` | 200 | `WBE_VALIDATION` | global handler |
| unknown path | `NoResourceFoundException` | 404 | `WBE_NOT_FOUND` | global handler |
| anything else | `Exception` | 500 | `WBE9999` | global handler |

Final code numbers assigned in the implementation MR; placeholder aliases above.

---

## 9. Audit hook points

Every write emits exactly one `AUDIT_LOGS_INFM` row via the shared audit service. The hook points:

- `VasAccountService.create` → action `CREATE`.
- `VasAccountService.update` → action `UPDATE` (with old-vs-new JSON in `audit_logs_actv_dtl`).
- `VasAccountService.softDelete` → action `DELETE`.
- `VasAccountExcelService.import_` mode `A` → action `EXCEL_IMPORT_APPEND` (one summary row with `inserted_count`; per-row CREATEs are NOT individually audited because they're a single user intent).
- `VasAccountExcelService.import_` mode `R` → action `EXCEL_IMPORT_REPLACE` (one summary row capturing wipe count + insert count).

Every audit row carries `wsvc_id = 'vas-account'` (slug — never legacy WSVC id). `lang_cd` from `TenantContext.currentLangCd()`. `reg_id` / `reg_nm` from JWT principal. `reg_dtm` = current UTC `yyyyMMddHHmmss`.

---

## 10. i18n keys (`messages.properties`)

```
WBE2014=The requested VAS Account could not be found in this company.
WBE2020=Excel file exceeds the 500-row limit. Please split and re-upload.
WBE2021=Excel header row is invalid (unexpected cell: {0}).
```

(Final codes negotiated at MR time; placeholders mirror the Sprint 1 ERP Transaction History pattern.)

---

## 11. Out of scope (this MR)

- The legacy combo query `BUSI_VAS_ACCT_INFM_R002` (`DISP_VAS_ACCT_NM`) — exposed as a separate `/picker` endpoint when the first refactor consumer screen needs it.
- Bulk delete endpoint — no current consumer.
- `@PreAuthorize` annotations — wait for the project-wide auth-gate MR.
- A `VasAccountListController` route returning paginated results — see DN-2.
