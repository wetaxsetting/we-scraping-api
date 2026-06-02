# Purpose Settings — Spring Boot backend design

**Refactor slug:** `purpose-settings`
**Sibling docs:** [feature spec](../../../features/settings/purpose.md) · [JEX → Spring mapping](../../migration/purpose-settings.md) · DB schema (Needs Confirmation) · [frontend design](../../../../../wecms-web/docs/features/settings/purpose.md) · [task plan](../../../archive/planning/settings/purpose-settings-task-plan.md).
**Mirrors live JEX:** `comm_0187_001_r001/_r002/_c001/_u002/_u004` + `comm_0187_004_c001`. Out of scope: `_u003` reset, `comm_0187_003_*` unused/restore, `comm_0187_002` standardization.

This document is the design contract for the Phase 2 backend implementation. No Java code is written in Phase 1.

> Legacy WSVC ids appear in this document only as JEX file:line citations or in migration prose. Every new identifier surface (REST URI, Java package, class name, DTO record, i18n key, log tag, audit `WSVC_ID`) uses the kebab-case slug `purpose-settings`. See [docs/refactor-wecms/features/settings/purpose-settings.md §4](../../../features/settings/purpose.md) for the rule.

---

## 1. Package layout

```
com.kosign.wecms.domain.setting.purposesettings/
├── controller/
│   └── PurposeSettingsController.java            — @RequestMapping("/api/v1/settings/purpose-settings")
├── service/
│   ├── PurposeSettingsService.java               — list / detail / create / update / delete / reorder / upload
│   └── PurposeReorderValidator.java              — small util: validates no-duplicates / contiguous payload
├── repository/
│   ├── PurposeSettingsRepository.java            — JpaRepository<BusiUsagSet, BusiUsagSetId>
│   └── PurposeInUseRepository.java               — JpaRepository<PurposeInUseRow, BusiUsagSetId> (@Immutable view)
├── entity/
│   ├── BusiUsagSet.java                          — @Table("BUSI_USAG_SET_INFM"), @IdClass(BusiUsagSetId), extends BaseEntity
│   ├── BusiUsagSetId.java                        — Serializable composite key (bizKey, useUsagCd)
│   └── PurposeInUseRow.java                      — @Immutable @Table("v_purpose_in_use")
└── dto/
    ├── PurposeSettingRow.java                    — list-row record (incl. usedInTransactions)
    ├── PurposeSettingDetail.java                 — single-row record
    ├── PurposeSettingListResponse.java           — { list }
    ├── PurposeSettingCreateRequest.java          — record + Bean Validation
    ├── PurposeSettingUpdateRequest.java          — record + Bean Validation
    ├── PurposeReorderRequest.java                — record { items: List<PurposeReorderItem> }
    ├── PurposeReorderItem.java                   — record (use_usag_cd, disp_ord)
    ├── PurposeUploadRequest.java                 — record { rows: List<PurposeUploadRow> }
    ├── PurposeUploadRow.java                     — record (per-row create input)
    └── PurposeUploadResult.java                  — record (inserted, skipped, errors[])
```

Mirrors the `domain/setting/firmbankingsettings/` layout (per firm-banking-settings design (Needs Confirmation) if/when that backend doc lands; backend files exist already). No new shared infrastructure required — the shared `AuditLogService` was introduced by `firm-banking-settings` and is reused here.

---

## 2. Controller URI design

`@RequestMapping("/api/v1/settings/purpose-settings")`

| Verb | Path | Handler | Returns |
|---|---|---|---|
| GET | `` (base) | `list(@RequestParam(required=false) String keyword)` | `ApiResponse<PurposeSettingListResponse>` |
| GET | `/{useUsagCd}` | `detail(@PathVariable @Size(min=10,max=10) String useUsagCd)` | `ApiResponse<PurposeSettingDetail>` |
| POST | `` (base) | `create(@RequestBody @Valid PurposeSettingCreateRequest req)` | `ApiResponse<PurposeSettingDetail>` |
| PUT | `/{useUsagCd}` | `update(@PathVariable String useUsagCd, @RequestBody @Valid PurposeSettingUpdateRequest req)` | `ApiResponse<PurposeSettingDetail>` |
| DELETE | `/{useUsagCd}` | `delete(@PathVariable String useUsagCd)` | `ApiResponse<Void>` |
| PUT | `/order` | `reorder(@RequestBody @Valid PurposeReorderRequest req)` | `ApiResponse<PurposeSettingListResponse>` (returns refreshed list) |
| POST | `/upload` | `upload(@RequestBody @Valid PurposeUploadRequest req)` | `ApiResponse<PurposeUploadResult>` |

**URI rules:**
- Kebab-case slug `/purpose-settings` — never `/comm_0187_001`.
- Path parameter is the system `use_usag_cd` (10-char system PK), never the user-visible `hand_use_usag_cd` — exact same convention as `firm-banking-settings/{out_bank_cd}/{wdrl_act_no}`.
- Reorder takes the special path `/order` rather than overloading PUT-on-collection — explicit verb, less surprise.
- Upload takes `/upload` rather than `POST /import` — matches the in-house Settings vocabulary (every Settings panel that needs upload uses `/upload`).

**Controller method body — illustrative shape (no implementation in Phase 1):**

```java
@RestController
@RequestMapping("/api/v1/settings/purpose-settings")
@RequiredArgsConstructor
public class PurposeSettingsController {

    private final PurposeSettingsService service;

    @GetMapping
    public ApiResponse<PurposeSettingListResponse> list(
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return ApiResponse.ok(service.list(TenantContext.currentBizKey(), keyword));
    }

    @GetMapping("/{useUsagCd}")
    public ApiResponse<PurposeSettingDetail> detail(@PathVariable String useUsagCd) {
        return ApiResponse.ok(service.detail(TenantContext.currentBizKey(), useUsagCd));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PurposeSettingDetail> create(
            @RequestBody @Valid PurposeSettingCreateRequest req) {
        return ApiResponse.ok(service.create(
                TenantContext.currentBizKey(),
                TenantContext.currentUserId(),
                req));
    }

    @PutMapping("/{useUsagCd}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PurposeSettingDetail> update(
            @PathVariable String useUsagCd,
            @RequestBody @Valid PurposeSettingUpdateRequest req) {
        return ApiResponse.ok(service.update(
                TenantContext.currentBizKey(),
                TenantContext.currentUserId(),
                useUsagCd, req));
    }

    @DeleteMapping("/{useUsagCd}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String useUsagCd) {
        service.delete(TenantContext.currentBizKey(),
                       TenantContext.currentUserId(),
                       useUsagCd);
        return ApiResponse.ok(null);
    }

    @PutMapping("/order")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PurposeSettingListResponse> reorder(
            @RequestBody @Valid PurposeReorderRequest req) {
        return ApiResponse.ok(service.reorder(
                TenantContext.currentBizKey(),
                TenantContext.currentUserId(),
                req));
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PurposeUploadResult> upload(
            @RequestBody @Valid PurposeUploadRequest req) {
        return ApiResponse.ok(service.upload(
                TenantContext.currentBizKey(),
                TenantContext.currentUserId(),
                req));
    }
}
```

---

## 3. Service interface

```java
public interface PurposeSettingsService {

    PurposeSettingListResponse list(String bizKey, @Nullable String keyword);

    PurposeSettingDetail detail(String bizKey, String useUsagCd);

    /** Throws BusinessException("WBW1152") on hand_use_usag_cd dup,
     *  BusinessException("WBW1138") on use_usag_nm dup,
     *  BusinessException("WBE_VALIDATION", args) when parent_use_usag_cd does not
     *  resolve to an active level-1 row, or when parent == self (the latter can't
     *  happen on direct create but is a guard for upload payloads).
     *
     *  OQ-8 decided 2026-05-12 — reuse legacy codes only; no new WBE_PURPOSE_* family. */
    PurposeSettingDetail create(String bizKey, String userId, PurposeSettingCreateRequest req);

    /** Throws BusinessException("WBW1138") on use_usag_nm dup (excluding self),
     *  BusinessException("WBE_VALIDATION", args) for parent-invalid / self-parent /
     *  demote-blocked (row has active children and is being assigned a parent —
     *  would create three levels). */
    PurposeSettingDetail update(String bizKey, String userId, String useUsagCd,
                                 PurposeSettingUpdateRequest req);

    /** Throws BusinessException("WBE1199") when EITHER the row has active children
     *  (legacy parity) OR the row itself / any active child of the row is referenced
     *  by an active banking-transaction-history / payment / transfer record
     *  (OQ-5 decided 2026-05-12 — recursive hard-fail). One wire code covers both
     *  conditions; the localized message reads "this purpose or one of its
     *  sub-purposes is in use" so the user understands both branches. */
    void delete(String bizKey, String userId, String useUsagCd);

    /** Throws BusinessException("WBE_VALIDATION", args) on duplicate use_usag_cd
     *  or duplicate disp_ord in the payload. */
    PurposeSettingListResponse reorder(String bizKey, String userId, PurposeReorderRequest req);

    /** Bulk insert in one transaction. On any row failure, the whole batch rolls back
     *  with the failed row indices in the result. */
    PurposeUploadResult upload(String bizKey, String userId, PurposeUploadRequest req);
}
```

### 3.1 Implementation shape — non-obvious decisions

**`list(...)` builds a Specification, not raw SQL.** Predicates:

1. `biz_key = :bizKey`
2. `usag_grp_cd IS NULL` (refactor scope — never expose the legacy buckets)
3. `use_yn = 'Y'` (soft-delete filter — exposing the Unused tab is out of scope)
4. `use_usag_cd NOT IN (RESERVED_SYSTEM_CODES)` (9 codes per [PurposeSettingsService.RESERVED_SYSTEM_CODES](#))
5. `(:keyword IS NULL OR LOWER(use_usag_nm) LIKE LOWER(CONCAT('%', :keyword, '%')))` (merges legacy `_r002` into `_r001`)
6. ORDER BY `disp_ord ASC`

The list then LEFT JOINs against `v_purpose_in_use` to derive `used_in_transactions` in one query (no N+1). Mapper assembles the tree on the server side — level-1 nodes first, level-2 nodes nested under their parent's `children` array. Frontend `PurposeNode` matches this shape (see [types.ts](../../../../../wecms-web/components/settings/purpose/types.ts)).

**`create(...)` derives `use_usag_cd` via the ported legacy DB function `wb_get_next_val`** (OQ-3 decided 2026-05-12 — see [feature spec §11 OQ-3](../../../features/settings/purpose.md)). `disp_ord` defaults to in-service `MAX+1` (OQ-3 (p2) — Phase 2 owner re-verifies the legacy function's `disp_ord` branch for the `usag_grp_cd IS NULL` case and switches the call site if a matching branch exists):

```java
@Transactional
public PurposeSettingDetail create(String bizKey, String userId, PurposeSettingCreateRequest req) {
    // 1. Bean Validation already passed (@NotBlank, @Size).
    // 2. Service-layer duplicate checks (legacy parity)
    repo.findActiveByHandCd(bizKey, req.handUseUsagCd()).ifPresent(_r -> {
        throw new BusinessException("WBW1152");
    });
    repo.findActiveByName(bizKey, req.useUsagNm()).ifPresent(_r -> {
        throw new BusinessException("WBW1138");
    });
    // 3. Parent validation
    validateParent(bizKey, req.parentUseUsagCd(), /*selfUseUsagCd=*/ null);
    // 4. PK generation — ported legacy wb_get_next_val(...).
    //    The function's busi_usag_set_infm branch returns a 10-digit
    //    zero-padded string in the 0000000001..0999999999 range, scoped
    //    to (biz_key, usag_grp_cd IS NULL via the empty-string '' param).
    //    See feature spec OQ-3 for the function's SQL body and the
    //    concurrency-retry guard (PK-violation catch + one-shot retry).
    String useUsagCd = repo.nextUseUsagCd(bizKey);
    // 5. DISP_ORD generation — in-service MAX+1 within the refactor scope
    //    (usag_grp_cd IS NULL AND use_yn = 'Y'). If Phase 2 confirms a
    //    matching wb_get_next_val('busi_usag_set_infm','disp_ord',biz_key,'')
    //    branch on DEV, swap this for repo.nextDispOrd(bizKey).
    int nextOrd = repo.findMaxDispOrd(bizKey).orElse(0) + 1;
    // 6. Persist
    BusiUsagSet entity = new BusiUsagSet();
    entity.setBizKey(bizKey);
    entity.setUseUsagCd(useUsagCd);
    entity.setHandUseUsagCd(req.handUseUsagCd());
    entity.setUseUsagNm(req.useUsagNm());
    entity.setUseUsagCont(req.useUsagCont());
    entity.setParentUseUsagCd(req.parentUseUsagCd());
    entity.setDispYn(req.dispYn() != null ? req.dispYn() : "Y");
    entity.setDispOrd(nextOrd);
    entity.setUseYn("Y");
    entity.setUsagGrpCd(null);
    entity.setModId(userId);
    entity.setModDtm(DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC).format(Instant.now()));
    repo.save(entity);
    // 7. Audit log (REQUIRES_NEW)
    auditLogService.append(
        bizKey, userId,
        AuditLogContext.builder()
            .wsvcId("purpose-settings")
            .refNo("BUSI_USAG_SET_INFM")
            .refNo1(useUsagCd)
            .refNo2(req.handUseUsagCd())
            .actv("CREATE")
            .actvDtlKey("audit.purpose_settings.create")
            .actvDtlArgs(new Object[]{ req.handUseUsagCd(), req.useUsagNm() })
            .build());
    return mapper.toDetail(entity, /*usedInTransactions=*/ false);
}
```

**`update(...)` reuses the same parent / duplicate-name guards**, plus the legacy "no `hand_use_usag_cd` check on update" rule (OQ-4 default) and a service-layer demote-blocked check (refuse a level-1 → level-2 demote when active children exist — would create three levels). The demote-blocked guard throws `BusinessException("WBE_VALIDATION", args)` per OQ-8.

**`delete(...)` runs two pre-checks in order, then soft-deletes:**

```java
@Transactional
public void delete(String bizKey, String userId, String useUsagCd) {
    BusiUsagSet row = repo.findByBizKeyAndUseUsagCd(bizKey, useUsagCd)
        .orElseThrow(() -> new BusinessException("WBE9999"));

    // 1. Legacy parity check — block delete of a parent that still has active children.
    //    Mirrors comm_0187_001_u002_act.jsp:75-91.
    if (repo.hasActiveChildren(bizKey, useUsagCd)) {
        throw new BusinessException("WBE1199");
    }

    // 2. Recursive transaction-reference check — OQ-5 decided 2026-05-12.
    //    Block delete when the row itself OR any active child of the row is
    //    referenced by an active banking-transaction-history / payment / transfer
    //    record. The `v_purpose_in_use` view (database doc §2.2) is the single
    //    source of truth — it UNIONs branch (a) parent-child references with
    //    branch (b) consumer-table references, so one repository call covers
    //    both. The "recursive" part means we look up the deleted row's
    //    `use_usag_cd` AND every active child's `use_usag_cd` in the view.
    //
    //    OQ-8 decided 2026-05-12 — reuse the legacy WBE1199 code (its scope is
    //    broadened in the refactor to cover this case too; no new
    //    new WBE_PURPOSE_USE_IN_TRANSACTIONS code is introduced).
    Set<String> codesToCheck = new HashSet<>();
    codesToCheck.add(useUsagCd);
    codesToCheck.addAll(repo.findActiveChildCodes(bizKey, useUsagCd));
    if (purposeInUseRepo.existsAnyByBizKeyAndUseUsagCdIn(bizKey, codesToCheck)) {
        throw new BusinessException("WBE1199");
    }

    // 3. Soft-delete — never a hard DELETE. Legacy parity.
    row.setUseYn("N");
    row.setModId(userId);
    row.setModDtm(nowAsLegacyTs());
    repo.save(row);

    // 4. Audit (REQUIRES_NEW).
    auditLogService.append(/* … wsvc_id="purpose-settings", actv="DELETE" … */);
}
```

Two new repository methods land in `PurposeSettingsRepository`:

```java
@Query("""
    SELECT p.useUsagCd FROM BusiUsagSet p
    WHERE p.bizKey = :bizKey
      AND p.parentUseUsagCd = :parentUseUsagCd
      AND p.useYn = 'Y'
""")
List<String> findActiveChildCodes(String bizKey, String parentUseUsagCd);
```

and `PurposeInUseRepository.existsAnyByBizKeyAndUseUsagCdIn(bizKey, codes)` (Spring Data derived query — "exists any row where biz_key=? AND use_usag_cd IN ?"). The `v_purpose_in_use` view's UNION branch (b) — consumer-table references — is wired in [database doc §2.2](../../database/settings/purpose-settings-schema.md) as Phase 2 enumerates the consumer table set.

**`reorder(...)` runs in one `@Transactional` block:**

```java
@Transactional
public PurposeSettingListResponse reorder(String bizKey, String userId, PurposeReorderRequest req) {
    validator.validate(req);   // no dup use_usag_cd, no dup disp_ord
    Map<String, Integer> nextOrdByCd = req.items().stream()
        .collect(Collectors.toMap(PurposeReorderItem::useUsagCd, PurposeReorderItem::dispOrd));
    List<BusiUsagSet> rows = repo.findAllActiveForReorder(bizKey, nextOrdByCd.keySet());
    if (rows.size() != nextOrdByCd.size()) {
        throw new BusinessException("WBE_VALIDATION", new Object[]{ "reorder", "payload mismatch" });
    }
    String modDtm = nowAsLegacyTs();
    for (BusiUsagSet r : rows) {
        r.setDispOrd(nextOrdByCd.get(r.getUseUsagCd()));
        r.setModId(userId);
        r.setModDtm(modDtm);
    }
    // single audit-log row summarizing the reorder
    auditLogService.append(..., actv="REORDER", actvDtlKey="audit.purpose_settings.reorder",
                                  actvDtlArgs=new Object[]{ rows.size() });
    return list(bizKey, null);
}
```

**`upload(...)` validates per-row, fails the whole batch on first error** (the legacy `_c001` action is single-row; the batch upload sits on top of the same validation). Returns `{ inserted, skipped: 0, errors: [...] }` on failure and the inserted rows on success.

---

## 4. Repository methods

```java
public interface PurposeSettingsRepository extends JpaRepository<BusiUsagSet, BusiUsagSetId>,
                                                    JpaSpecificationExecutor<BusiUsagSet> {

    Optional<BusiUsagSet> findByBizKeyAndUseUsagCd(String bizKey, String useUsagCd);

    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
          AND p.handUseUsagCd = :handCd
    """)
    Optional<BusiUsagSet> findActiveByHandCd(String bizKey, String handCd);

    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
          AND p.useUsagNm = :name
    """)
    Optional<BusiUsagSet> findActiveByName(String bizKey, String name);

    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
          AND p.useUsagNm = :name
          AND p.useUsagCd <> :selfUseUsagCd
    """)
    Optional<BusiUsagSet> findDuplicateNameExcludingSelf(String bizKey, String name, String selfUseUsagCd);

    @Query("""
        SELECT count(p) > 0 FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.parentUseUsagCd = :useUsagCd
          AND p.useYn = 'Y'
    """)
    boolean hasActiveChildren(String bizKey, String useUsagCd);

    @Query("""
        SELECT MAX(p.dispOrd) FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.usagGrpCd IS NULL
          AND p.useYn = 'Y'
    """)
    Optional<Integer> findMaxDispOrd(String bizKey);

    /**
     * PK generation — delegates to the ported legacy DB function
     * `wb_get_next_val('busi_usag_set_infm', 'use_usag_cd', biz_key, '')`.
     * Returns a 10-digit zero-padded string in the 0000000001..0999999999 range.
     *
     * Concurrency: the legacy function uses bare MAX+1 (no advisory lock).
     * Caller wraps this call + the immediate INSERT in one @Transactional
     * block; the PK constraint catches collisions, and the service retries
     * once on DataIntegrityViolationException for the PK. See feature spec OQ-3.
     *
     * Per the OQ-3 (p1)/(p2) split: if Phase 2 verifies a matching `disp_ord`
     * branch for `usag_grp_cd IS NULL`, add a sibling `nextDispOrd(bizKey)`
     * method here using the same `wb_get_next_val` pattern.
     */
    @Query(value = "SELECT wb_get_next_val('busi_usag_set_infm', 'use_usag_cd', :bizKey, '')",
           nativeQuery = true)
    String nextUseUsagCd(String bizKey);

    /** For reorder — fetch all rows the payload targets in one query. */
    @Query("""
        SELECT p FROM BusiUsagSet p
        WHERE p.bizKey = :bizKey
          AND p.useYn = 'Y'
          AND p.useUsagCd IN :useUsagCds
    """)
    List<BusiUsagSet> findAllActiveForReorder(String bizKey, Set<String> useUsagCds);
}
```

```java
public interface PurposeInUseRepository extends JpaRepository<PurposeInUseRow, BusiUsagSetId> {
    boolean existsByBizKeyAndUseUsagCd(String bizKey, String useUsagCd);
}
```

The `PurposeInUseRow` entity is a read-only `@Immutable` projection over the `v_purpose_in_use` view — see database doc §3 (Needs Confirmation).

---

## 5. DTO + Entity field list

### 5.1 `BusiUsagSet` entity

```java
@Entity
@Table(name = "BUSI_USAG_SET_INFM")
@IdClass(BusiUsagSetId.class)
@Getter @Setter @NoArgsConstructor
public class BusiUsagSet extends BaseEntity {
    @Id @Column(name = "BIZ_KEY",       length = 20,  nullable = false) private String bizKey;
    @Id @Column(name = "USE_USAG_CD",   length = 20,  nullable = false) private String useUsagCd;

    @Column(name = "HAND_USE_USAG_CD",   length = 20)                   private String handUseUsagCd;
    @Column(name = "USE_USAG_NM",        length = 255)                  private String useUsagNm;
    @Column(name = "USE_USAG_CONT",      length = 500)                  private String useUsagCont;
    @Column(name = "PARENT_USE_USAG_CD", length = 20)                   private String parentUseUsagCd;

    @Column(name = "USE_YN",  length = 1, nullable = false)             private String useYn;   // 'Y' | 'N'
    @Column(name = "DISP_YN", length = 1, nullable = false)             private String dispYn;  // 'Y' | 'N'
    @Column(name = "DISP_ORD")                                          private Integer dispOrd;

    @Column(name = "USAG_GRP_CD", length = 3)                           private String usagGrpCd;  // always NULL in refactor scope
    @Column(name = "USAG_TYPE",   length = 2)                           private String usagType;
    @Column(name = "REPT_DV",     length = 2)                           private String reptDv;
    @Column(name = "REPT_GRP",    length = 2)                           private String reptGrp;
}
```

**Notes:**
- Extends `BaseEntity` so `MOD_ID` / `MOD_DTM` are auto-populated via `@PreUpdate`. The legacy table has only `MOD_ID` / `MOD_DTM` (no `REG_ID` / `REG_DTM`) — confirmed via DEV `postgres` MCP. `BaseEntity` will populate `MOD_*` correctly; `REG_*` columns don't exist on this table so the inherited `@PrePersist` writes nothing for them. If `BaseEntity` enforces `REG_*` writes, override `@PrePersist` for this entity. **OQ-Backend-1.**
- Legacy columns kept on the entity but never written by the refactor: `USAG_GRP_CD` (always NULL), `USAG_TYPE`, `REPT_DV`, `REPT_GRP`. They stay on the schema for legacy data import; the entity getters are package-private to discourage accidental access (Phase 2 hardening optional).
- No `@JsonProperty` annotations — wire format handled by the global Jackson `SNAKE_CASE` strategy.
- `dispOrd` is `Integer` not `Number` (legacy uses `Number`); the column is `numeric` in PG but always carries a small integer.
- `useYn` / `dispYn` stay `String` (not the legacy `YesOrNo` enum) — per [wecms-api/CLAUDE.md §"Domain enums"](../../../../CLAUDE.md), never map an enum to a legacy single-letter column. The enum conversion (if needed) happens at the service / mapper layer.

### 5.2 `BusiUsagSetId` composite key

```java
@EqualsAndHashCode
@AllArgsConstructor @NoArgsConstructor
public class BusiUsagSetId implements Serializable {
    private String bizKey;
    private String useUsagCd;
}
```

### 5.3 DTO records

```java
// List row — what the GET .../purpose-settings returns per row.
public record PurposeSettingRow(
        String useUsagCd,
        String handUseUsagCd,
        String useUsagNm,
        String useUsagCont,
        String parentUseUsagCd,
        String dispYn,
        Integer dispOrd,
        boolean usedInTransactions,
        String modId,
        String modDtm,
        @Nullable List<PurposeSettingRow> children   // populated for level-1 nodes
) {}

public record PurposeSettingListResponse(List<PurposeSettingRow> list) {}

public record PurposeSettingDetail(
        String useUsagCd,
        String handUseUsagCd,
        String useUsagNm,
        String useUsagCont,
        String parentUseUsagCd,
        String dispYn,
        Integer dispOrd,
        boolean usedInTransactions,
        String modId,
        String modDtm
) {}

public record PurposeSettingCreateRequest(
        @NotBlank @Size(max = 20)  String handUseUsagCd,
        @NotBlank @Size(max = 255) String useUsagNm,
        @Size(max = 500)            String useUsagCont,
        @Size(max = 20)             String parentUseUsagCd,
        @Pattern(regexp = "[YN]")   String dispYn
) {}

public record PurposeSettingUpdateRequest(
        @NotBlank @Size(max = 20)  String handUseUsagCd,
        @NotBlank @Size(max = 255) String useUsagNm,
        @Size(max = 500)            String useUsagCont,
        @Size(max = 20)             String parentUseUsagCd,
        @Pattern(regexp = "[YN]")   String dispYn
) {}

public record PurposeReorderItem(
        @NotBlank @Size(min = 10, max = 10) String useUsagCd,
        @NotNull @Positive                  Integer dispOrd
) {}

public record PurposeReorderRequest(
        @NotEmpty @Valid List<PurposeReorderItem> items
) {}

public record PurposeUploadRow(
        @NotBlank @Size(max = 20)  String handUseUsagCd,
        @NotBlank @Size(max = 255) String useUsagNm,
        @Size(max = 500)            String useUsagCont,
        @Size(max = 20)             String parentUseUsagCd
) {}

public record PurposeUploadRequest(
        @NotEmpty @Valid List<PurposeUploadRow> rows
) {}

public record PurposeUploadError(
        int row,             // 0-based row index in the input
        String field,        // hand_use_usag_cd | use_usag_nm | parent_use_usag_cd | use_usag_cont
        String code          // WBW1152 / WBW1138 / WBE_VALIDATION (per OQ-8 — legacy + generic codes only)
) {}

public record PurposeUploadResult(
        int inserted,
        int skipped,
        List<PurposeUploadError> errors
) {}
```

**Wire format:** lowercase snake_case via the global Jackson strategy (no per-field `@JsonProperty`). Sample success envelope:

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "use_usag_cd": "0000000007",
        "hand_use_usag_cd": "P01",
        "use_usag_nm": "Deposit",
        "use_usag_cont": "Deposit purpose",
        "parent_use_usag_cd": null,
        "disp_yn": "Y",
        "disp_ord": 192,
        "used_in_transactions": true,
        "mod_id": "lamine@gmail.com",
        "mod_dtm": "20250609035856",
        "children": [
          { "use_usag_cd": "0000000008", "hand_use_usag_cd": "P1.1",
            "use_usag_nm": "Sale", "use_usag_cont": "Sale business",
            "parent_use_usag_cd": "0000000007", "disp_yn": "Y", "disp_ord": 193,
            "used_in_transactions": false, "mod_id": "lamine@gmail.com",
            "mod_dtm": "20250609035856", "children": null }
        ]
      }
    ]
  }
}
```

---

## 6. Validation rules — Bean Validation summary

| Field | Annotation | Notes |
|---|---|---|
| `handUseUsagCd` (create / update / upload) | `@NotBlank @Size(max=20)` | Service-layer dup check on create only (legacy parity per OQ-4). |
| `useUsagNm` (create / update / upload) | `@NotBlank @Size(max=255)` | Service-layer dup check on create + update (excluding self). |
| `useUsagCont` | `@Size(max=500)` | Nullable. |
| `parentUseUsagCd` | `@Size(max=20)` | Service-layer parent-active check + self-parent + demote-with-children — all three throw `BusinessException("WBE_VALIDATION", args)` with contextual reason per OQ-8. |
| `dispYn` | `@Pattern(regexp="[YN]")` | Optional; defaults to `"Y"` on create. |
| Path `{useUsagCd}` | `@Size(min=10,max=10)` | The 10-char zero-padded system PK. |
| Reorder payload | per-item `@NotBlank @Size(min=10,max=10)` + `@Positive`, `@NotEmpty` on the list | Service rejects duplicates / unknown codes with `BusinessException("WBE_VALIDATION", args)` per OQ-8. |
| Upload rows | per-row Bean Validation + service-layer duplicate / parent checks | Whole batch rolls back on any failure. |

Validation failures land in `GlobalExceptionHandler.handleMethodArgumentNotValid(...)` → `WBE_VALIDATION` (HTTP 400) with a structured field-level error list. Same shape as `firm-banking-settings`.

---

## 7. Exception handling

All errors go through the existing `GlobalExceptionHandler` + `ApiResponse` envelope; no parallel error path. **OQ-8 decided 2026-05-12 — reuse legacy codes only; no new `WBE_PURPOSE_*` family.**

| Exception | Handler maps to | HTTP |
|---|---|---|
| `MethodArgumentNotValidException` (Bean Validation — `@NotBlank`, `@Size`, `@Pattern`) | `WBE_VALIDATION` | 400 |
| `BusinessException("WBW1152")` (duplicate `hand_use_usag_cd` on create) | `WBW1152` | 409 |
| `BusinessException("WBW1138")` (duplicate `use_usag_nm` on create or update-excluding-self) | `WBW1138` | 409 |
| `BusinessException("WBE1199")` (delete blocked — row has active children OR row/any-active-child is referenced by a banking-transaction-history / payment / transfer record per OQ-5) | `WBE1199` | 409 |
| `BusinessException("WBE_VALIDATION", args)` (parent-invalid / self-parent / demote-blocked / reorder-invalid) | `WBE_VALIDATION` | 400 |
| `AccessDeniedException` (failed `@PreAuthorize`) | `WBE1009` | 403 |
| Unexpected runtime / persistence failure | `WBE9999` | 500 |

**No new keys added to [messages.properties](../../../../src/main/resources/messages.properties).** Phase 2 owner verifies that the three legacy codes (`WBW1152`, `WBW1138`, `WBE1199`) carry English message strings in the refactor's message bundle; if any is missing, port the legacy `dictionary_*.properties` text into `messages_en.properties` (text reuse, not a new code). KO / VI translations follow the rule in [wecms-web/CLAUDE.md §13a-bis](../../../../../wecms-web/CLAUDE.md) — never invent translations; port only verified text from `dictionary_ko.properties` / `dictionary_vi.properties`, otherwise omit and let EN fall through.

---

## 8. Transactions & concurrency

- **Read operations** — no explicit transaction (Spring Boot's default read-only on `@Service` is fine; service methods that only read can be annotated `@Transactional(readOnly = true)`).
- **Write operations** — `@Transactional` on every mutating service method. Audit log append runs inside the same transaction via `AuditLogService.append(...)` which itself uses `@Transactional(propagation = REQUIRES_NEW)` so an audit insert failure does not roll back the business transaction.
- **Reorder concurrency** — runs in a single transaction; rows are fetched + updated atomically. Two concurrent reorder calls serialize via the row-level write locks (no advisory lock needed).
- **`use_usag_cd` generation** — delegates to the ported legacy `wb_get_next_val` DB function (OQ-3 decided 2026-05-12). The function uses bare MAX+1 with no locking; the service wraps `nextUseUsagCd(bizKey)` + the immediate `INSERT` in one transaction and retries once on `DataIntegrityViolationException` for the PK (the constraint catches a concurrent-create collision).

---

## 9. Logging

Every mutating service method logs at INFO via `@Slf4j`:

```java
log.info("purpose-settings.{} biz_key={} use_usag_cd={}", actv, bizKey, useUsagCd);
```

The `purpose-settings.{actv}` prefix lets ops grep all writes against this feature. Errors log at ERROR with the full exception. No `System.out.println`.

`AppLogManager` is the JEX legacy logger; the refactor uses the SLF4J `log` field from Lombok's `@Slf4j` instead.

---

## 10. Testing strategy

Phase 2 backend test plan (lands in [docs/refactor-wecms/testing/](../../../testing/) once Phase 2 completes):

- **`PurposeSettingsServiceTest`** — unit tests covering: list filters (keyword, reserved-codes exclusion, tenant scoping), create duplicate checks, update duplicate-name-excluding-self, delete-blocked-by-children, delete-blocked-by-payment-references (OQ-5), parent invalid / self / demote-blocked, reorder valid / duplicate-cd / duplicate-ord, upload happy-path / fail-on-first-error / rollback.
- **`PurposeSettingsControllerTest`** — Spring MVC slice tests for the URI / verb / auth contract. Verify `@PreAuthorize` blocks USER role on mutations. Verify the wire shape: snake_case property names, envelope `{ status, data }`.
- **`PurposeSettingsRepositoryTest`** — `@DataJpaTest` over an in-memory or test-container PG to verify the JPQL queries hit the right rows.
- **`PurposeSettingsAuditLogTest`** — integration covering create / update / delete / reorder / upload write three (or one for reorder) audit rows with the slug `purpose-settings`.

---

## 11. Open questions (backend-specific)

### OQ-Backend-1 — `BaseEntity` and missing `REG_*` columns

`BUSI_USAG_SET_INFM` has no `REG_ID` / `REG_DTM` columns — only `MOD_ID` / `MOD_DTM`. The shared `BaseEntity` writes both on `@PrePersist`. **Decision needed:** either (a) override `@PrePersist` in `BusiUsagSet` to populate only the `MOD_*` pair, or (b) use a different abstract base (`BaseEntityModOnly`) reused across legacy-tables-without-reg-columns. Default: **(a)** — fewer abstractions, scoped to this one entity. Phase 2 confirms with a quick scan for other tables in the same boat.

### OQ-Backend-2 — `use_usag_cd` advisory-lock scope — **OBSOLETE (OQ-3 decided 2026-05-12)**

The advisory-lock concern is moot — the refactor no longer implements MAX+1 in application code. PK generation delegates to the ported legacy `wb_get_next_val` DB function (no advisory lock, bare MAX+1). Concurrency is handled by the PK constraint + a one-shot retry on `DataIntegrityViolationException`. See [feature spec OQ-3](../../../features/settings/purpose.md) for the function body and the (p1)/(p2) split for `disp_ord`.

### OQ-Backend-3 — Tree assembly: server-side vs. client-side

The feature spec says the list returns level-1 nodes with `children: [...]` populated. Alternative: return a flat list and let the frontend assemble the tree. **Decision:** server-side assembly is the default — the frontend `PurposeNode` type already expects nested `children`, and the static-UI `mockPurposeTree` is shaped this way. Server-side keeps the contract explicit.

### OQ-Backend-4 — Pagination

Legacy list has `PAGE_NO` / `PAGE_PER_CNT`. The refactor design above returns the full list (no pagination). **Rationale:** Purpose lists are small (tens of rows per tenant) and the modal panel is the only consumer. **Risk:** a tenant with hundreds of purposes would render all rows. Phase 2 confirms with PO; if pagination is needed, add `?page=&size=` query params and switch to a `Page<PurposeSettingRow>` return.

---

## 12. References

- [docs/refactor-wecms/features/settings/purpose-settings.md](../../../features/settings/purpose.md) — functional spec (source of truth for behavior).
- [docs/refactor-wecms/migration/purpose-settings.md](../../migration/purpose-settings.md) — JEX → Spring mapping.
- docs/refactor-wecms/database/purpose-settings.md (Needs Confirmation) — schema + view + Flyway plan.
- [docs/refactor-wecms/backend/settings/erp-transaction-history.md](erp-transaction-history.md) — closest backend doc precedent (Settings panel + audit-log integration).
- [wecms-api/CLAUDE.md](../../../../CLAUDE.md) — package conventions, identifier rules, error envelope, Bean Validation.
