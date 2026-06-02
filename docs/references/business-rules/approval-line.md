# Approval Line — Backend Design (Spring Boot)

| Field | Value |
|---|---|
| Slug | `approval-line` |
| Package | `com.kosign.wecms.domain.setting.approvalline` |
| Feature spec | [docs/refactor-wecms/features/approval/approval-line.md](../../features/settings/approval-line.md) |
| Migration mapping | [docs/refactor-wecms/migration/approval-line.md](../migration/approval-line.md) |
| Schema | docs/refactor-wecms/database/approval-line-schema.md (Needs Confirmation) (created by the backend MR) |

This document is the implementation blueprint for `wecms-api`. Every section is a verification target during code review.

---

## 1. REST endpoint table

All endpoints live under `/api/v1/settings/approval-line` and inherit the default JWT guard from `SecurityConfig`. Wire JSON is lowercase snake_case (global `JacksonConfig` `PropertyNamingStrategies.SNAKE_CASE` — see [`wecms-api/CLAUDE.md` § API Naming](../../../CLAUDE.md)). Every error response follows the standard envelope (`status` + `timestamp` + `path` + `trace_id`).

| METHOD | URI | Auth | Request body | Response data | Success codes | Documented error codes |
|---|---|---|---|---|---|---|
| `GET` | `/api/v1/settings/approval-line` | JWT | — (query: `q?: string`, `sort_field?: REG_DTM\|APPR_LINE_NM\|MOD_DTM`, `sort_dir?: ASC\|DESC`) | `ApprovalLineListResponse` | `WBI0000` | `WBE_VALIDATION`, `WBE9999` |
| `GET` | `/api/v1/settings/approval-line/{apprLineNo}` | JWT | — | `ApprovalLineRow` | `WBI0000` | `WBE_APPROVAL_LINE_NOT_FOUND`, `WBE_VALIDATION`, `WBE9999` |
| `POST` | `/api/v1/settings/approval-line` | JWT | `ApprovalLineCreateRequest` | `ApprovalLineRow` (the freshly created row, mirroring list shape) | `WBI0000` | `WBE_APPROVAL_LINE_NAME_BLANK`, `WBE_APPROVAL_LINE_EMPTY_APPROVERS`, `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`, `WBE_APPROVAL_LINE_DUP_APPROVER`, `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT`, `WBE_VALIDATION`, `WBE_DB`, `WBE9999` |
| `PUT` | `/api/v1/settings/approval-line/{apprLineNo}` | JWT | `ApprovalLineUpdateRequest` | `ApprovalLineRow` (refreshed) | `WBI0000` | `WBE_APPROVAL_LINE_NOT_FOUND`, `WBE_APPROVAL_LINE_NAME_BLANK`, `WBE_APPROVAL_LINE_EMPTY_APPROVERS`, `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`, `WBE_APPROVAL_LINE_DUP_APPROVER`, `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT`, `WBE_VALIDATION`, `WBE_DB`, `WBE9999` |
| `DELETE` | `/api/v1/settings/approval-line/{apprLineNo}` | JWT | — | `Void` (`null` data — frontend just reads `status.error`) | `WBI0000` | `WBE_APPROVAL_LINE_NOT_FOUND`, `WBE_VALIDATION`, `WBE_DB`, `WBE9999` |

### 1.1 Request / response shapes

#### `GET /api/v1/settings/approval-line` — list response

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "total_count": 29,
    "approval_lines": [
      {
        "appr_line_no": 19,
        "appr_line_nm": "A Plus (Exp) +",
        "reg_dtm": "20250727105815",
        "approvers": [
          {
            "apprvr_disp_ord": 1,
            "apprvr_id": "lamine@gmail.com",
            "apprvr_nm": "Lamine T.",
            "apprvr_posi": "Manager",
            "apprvr_dept": "Finance",
            "user_img_url": null
          }
        ]
      }
    ]
  }
}
```

#### `POST /api/v1/settings/approval-line` — request body

```json
{
  "appr_line_nm": "Approval Line A",
  "approvers": [
    { "apprvr_id": "user1@example.com", "apprvr_nm": "User One", "apprvr_posi": "Manager",  "apprvr_dept": "Finance" },
    { "apprvr_id": "user2@example.com", "apprvr_nm": "User Two", "apprvr_posi": "Director", "apprvr_dept": "Finance" }
  ]
}
```

#### `PUT /api/v1/settings/approval-line/{apprLineNo}` — request body

Same shape as POST minus the path param. Diff-sync semantics (feature § 5.6).

### 1.2 Error response examples

```json
// WBE_APPROVAL_LINE_TOO_MANY_APPROVERS — HTTP 200, status.error=true
{
  "status": { "error": true, "code": "WBE_APPROVAL_LINE_TOO_MANY_APPROVERS", "message": "Approval line cannot have more than 5 approvers." },
  "timestamp": "2026-05-13T08:00:00Z",
  "path": "/api/v1/settings/approval-line",
  "trace_id": "20260513080000PT12345678901234DEVL"
}

// WBE_APPROVAL_LINE_NOT_FOUND — HTTP 200, status.error=true
{
  "status": { "error": true, "code": "WBE_APPROVAL_LINE_NOT_FOUND", "message": "Approval line not found." },
  "timestamp": "2026-05-13T08:00:00Z",
  "path": "/api/v1/settings/approval-line/999",
  "trace_id": "20260513080000PT12345678901234DEVL"
}
```

### 1.3 Error code → `messages.properties` keys

Adds to `wecms-api/src/main/resources/messages.properties` (and `messages_en.properties`; `_ko` / `_vi` follow as translations arrive):

```properties
WBE_APPROVAL_LINE_NOT_FOUND=Approval line not found.
WBE_APPROVAL_LINE_TOO_MANY_APPROVERS=Approval line cannot have more than 5 approvers.
WBE_APPROVAL_LINE_DUP_APPROVER=Approval line cannot have the same approver more than once.
WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT=Approver does not belong to the current company.
WBE_APPROVAL_LINE_EMPTY_APPROVERS=Approval line must have at least one approver.
WBE_APPROVAL_LINE_NAME_BLANK=Approval line name is required.
```

No `audit.approval_line.*` keys — refactor writes no `AUDIT_LOGS_INFM` rows (§ 7, feature § 14 OQ-6 resolved 2026-05-13).

---

## 2. Controller layer

`ApprovalLineController` lives at `com.kosign.wecms.domain.setting.approvalline.controller.ApprovalLineController`. Stays thin — parse input, delegate, wrap with `ApiResponse.ok(...)`.

### 2.1 Responsibilities

- Bind path / query / body parameters via Spring MVC.
- Apply Bean Validation (`@Valid`) on the request records.
- Delegate everything else to `ApprovalLineService`.
- Wrap the service response in `ApiResponse.ok(data)`.

### 2.2 Method skeletons (signatures only — no body)

```java
@RestController
@RequestMapping("/api/v1/settings/approval-line")
@RequiredArgsConstructor
public class ApprovalLineController {

    private final ApprovalLineService service;

    @GetMapping
    public ApiResponse<ApprovalLineListResponse> list(
        @RequestParam(value = "q",          required = false) String q,
        @RequestParam(value = "sort_field", required = false, defaultValue = "REG_DTM") ApprovalLineSortField sortField,
        @RequestParam(value = "sort_dir",   required = false, defaultValue = "DESC")    SortDirection         sortDir
    );

    @GetMapping("/{apprLineNo}")
    public ApiResponse<ApprovalLineRow> detail(@PathVariable long apprLineNo);

    @PostMapping
    public ApiResponse<ApprovalLineRow> create(@Valid @RequestBody ApprovalLineCreateRequest req);

    @PutMapping("/{apprLineNo}")
    public ApiResponse<ApprovalLineRow> update(
        @PathVariable long apprLineNo,
        @Valid @RequestBody ApprovalLineUpdateRequest req
    );

    @DeleteMapping("/{apprLineNo}")
    public ApiResponse<Void> delete(@PathVariable long apprLineNo);
}
```

### 2.3 Sort enum

```java
package com.kosign.wecms.domain.setting.approvalline.dto;

public enum ApprovalLineSortField implements SortableField {
    REG_DTM("reg_dtm"),
    APPR_LINE_NM("appr_line_nm"),
    MOD_DTM("mod_dtm");

    private final String column;
    ApprovalLineSortField(String column) { this.column = column; }
    @Override public String column() { return column; }
}
```

Default order: `reg_dtm DESC` (feature § 4.2). The common-sort handler appends `appr_line_no DESC` as the deterministic tie-break.

---

## 3. Service layer

`ApprovalLineService` lives at `com.kosign.wecms.domain.setting.approvalline.service.ApprovalLineService`. All business logic is here.

### 3.1 Responsibilities

- Tenant scoping via `TenantContext.currentBizKey()` and `TenantContext.currentUserId()`. **Never** read `biz_key` from the request.
- Generate `appr_line_no` on Create via the legacy DB function (§ 6).
- Enforce business rules: max-5 cap, name not blank, no duplicate `apprvr_id`, every `apprvr_id` belongs to the current tenant.
- Diff-sync detail rows on Update (feature § 5.6).
- Compose `ApprovalLineRow` from the master entity + ordered detail rows + per-approver `user_img_url` resolution.
- **No audit-log writes** — feature § 14 OQ-6 resolved 2026-05-13 (legacy parity). Service does not depend on `AuditLogService`.

### 3.2 Method skeletons (signatures + transaction boundaries)

```java
@Service
@RequiredArgsConstructor
public class ApprovalLineService {

    private final ApprovalLineRepository         lineRepo;
    private final ApprovalLineApproverRepository approverRepo;
    private final UserBusiConnInfmRepository     userBusiRepo;   // tenant-link guard + user_img lookup
    private final LegacyFunctionRepository       legacyFnRepo;   // wb_get_next_val funnel
    private final MessageResolver                msg;

    @Transactional(readOnly = true)
    public ApprovalLineListResponse list(String q, ApprovalLineSortField sortField, SortDirection sortDir);

    @Transactional(readOnly = true)
    public ApprovalLineRow detail(long apprLineNo);

    @Transactional
    public ApprovalLineRow create(ApprovalLineCreateRequest req);

    @Transactional
    public ApprovalLineRow update(long apprLineNo, ApprovalLineUpdateRequest req);

    @Transactional
    public void delete(long apprLineNo);
}
```

### 3.3 `list` algorithm

1. Resolve `bizKey` from `TenantContext`.
2. Build the sort `Sort.by(direction, sortField.column()).and(Sort.by(DESC, "apprLineNo"))`.
3. Load master rows: `lineRepo.findByBizKeyAndApprLineNmContainingIgnoreCase(bizKey, q == null ? "" : q, sort)` (the `Containing` derived method emits `ILIKE '%'||?||'%'`).
4. Single batched query for detail rows: `approverRepo.findAllByBizKeyAndApprLineNoInOrderByApprLineNoAscApprvrDispOrdAsc(bizKey, lineNos)` — one IN-query, not N+1.
5. Group detail rows by `apprLineNo` in memory.
6. For each detail row, resolve `userImgUrl`:
   - `userBusiRepo.findByUserIdAndBizKeyAndDelYn(apprvrId, bizKey, "N")`.
   - If present and `getUserImg() != null` → `"/api/v1/users/" + apprvrId + "/image"`.
   - Else → `null`.
   - Batch the lookups: build a `Set<String> distinctApprvrIds`, fire one `findAllByUserIdInAndBizKeyAndDelYn(...)`, build a `Map<String, UserBusiConnInfm>` for lookup.
7. Compose `ApprovalLineRow` list. Return `new ApprovalLineListResponse(rows.size(), rows)`.

> Trade-off captured: an alternative is a read-only `V_APPROVAL_LINE_WITH_APPROVERS` SQL view mirroring `IDO.APPR_LINE_INFM_DETL_R002.xml`'s `json_agg`. Rejected here — JPA's nested loading + a single IN-query is clearer, easier to test, and the result-set size (≤ ~500 lines × 5 approvers per tenant) doesn't warrant the view's added migration cost. If a tenant later spikes past ~5000 rows the trade-off flips; revisit then.

### 3.4 `detail` algorithm

1. Resolve `bizKey`.
2. `lineRepo.findById(new ApprovalLineId(bizKey, apprLineNo)).orElseThrow(() -> new BusinessException("WBE_APPROVAL_LINE_NOT_FOUND"))`.
3. `approverRepo.findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo)`.
4. Resolve `userImgUrl` for each (same batch pattern, but the batch is at most 5 rows).
5. Return composed `ApprovalLineRow`.

### 3.5 `create` algorithm

1. Validate request again at the service layer (defence in depth):
   - `req.apprLineNm()` not blank → else `WBE_APPROVAL_LINE_NAME_BLANK`.
   - `req.approvers()` non-empty → else `WBE_APPROVAL_LINE_EMPTY_APPROVERS`.
   - `req.approvers().size() <= 5` → else `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`.
   - Stream `req.approvers()` `apprvrId` into a `Set<String>` and assert `size == list.size` → else `WBE_APPROVAL_LINE_DUP_APPROVER`.
2. Tenant-link guard — `userBusiRepo.findAllByUserIdInAndBizKeyAndDelYn(apprvrIds, bizKey, "N")`. If returned set size != `apprvrIds` size, throw `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` (with the missing id list interpolated into the message for support).
3. Resolve `appr_line_no = legacyFnRepo.nextVal("appr_line_infm", "appr_line_no", bizKey)` (§ 6).
4. Build master entity:
   ```java
   ApprovalLine line = ApprovalLine.builder()
       .bizKey(bizKey)
       .apprLineNo(apprLineNo)
       .apprLineNm(req.apprLineNm().trim())
       .regId(currentUserId)
       .regDtm(utcNow14())
       .modId(currentUserId)
       .modDtm(utcNow14())
       .build();
   lineRepo.save(line);
   ```
5. Build detail entities, assigning `apprvrDispOrd = index + 1`:
   ```java
   for (int i = 0; i < req.approvers().size(); i++) {
       var a = req.approvers().get(i);
       approverRepo.save(ApprovalLineApprover.builder()
           .bizKey(bizKey)
           .apprLineNo(apprLineNo)
           .apprvrId(a.apprvrId())
           .apprvrDispOrd(i + 1)
           .apprvrNm(a.apprvrNm())
           .apprvrPosi(coalesce(a.apprvrPosi(), ""))    // legacy parity — never null
           .apprvrDept(coalesce(a.apprvrDept(), ""))
           .regId(currentUserId)
           .regDtm(utcNow14())
           .modId(currentUserId)
           .modDtm(utcNow14())
           .build());
   }
   ```
6. Compose and return `ApprovalLineRow` (refresh detail order by reading back). No audit-log write (§ 7, feature § 14 OQ-6 resolved 2026-05-13 — legacy parity).

### 3.6 `update` algorithm

1. Same five validation checks as Create.
2. `existingLine = lineRepo.findById(...).orElseThrow(WBE_APPROVAL_LINE_NOT_FOUND)`.
3. Rename: `existingLine.setApprLineNm(req.apprLineNm().trim())`, `setModId / setModDtm` to now.
4. `existingApprovers = approverRepo.findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(bizKey, apprLineNo)`.
5. Build:
   ```java
   Map<String, ApprovalLineApprover> existingMap = existingApprovers.stream()
       .collect(Collectors.toMap(ApprovalLineApprover::getApprvrId, identity()));
   Set<String> incomingIds = req.approvers().stream()
       .map(ApprovalLineApproverInput::apprvrId).collect(toSet());
   ```
6. For each `(i, a)` in `req.approvers()`:
   - If `existingMap.containsKey(a.apprvrId())` → load that row, set new `disp_ord = i + 1`, new `nm` / `posi` / `dept`, new `modId` / `modDtm`. (Dirty tracking flushes on commit.)
   - Else → INSERT (mirroring Create's row builder).
7. For each `removedId in existingMap.keySet() - incomingIds` → `approverRepo.deleteById(new ApprovalLineApproverId(bizKey, apprLineNo, removedId))`.
8. Save (master is dirty-tracked; flush on tx commit).
9. Compose and return refreshed `ApprovalLineRow`. No audit-log write (§ 7).

### 3.7 `delete` algorithm

1. Resolve `bizKey`.
2. `existing = lineRepo.findById(new ApprovalLineId(bizKey, apprLineNo)).orElseThrow(WBE_APPROVAL_LINE_NOT_FOUND)`.
3. `approverRepo.deleteAllByBizKeyAndApprLineNo(bizKey, apprLineNo)` — single DELETE.
4. `lineRepo.deleteById(new ApprovalLineId(bizKey, apprLineNo))`.
5. Return. No audit-log write (§ 7).

### 3.8 Utility helpers

- `utcNow14()` — formats `Instant.now()` as `yyyyMMddHHmmss` (UTC). Lives in `common/util/TimestampUtils` (already used by other features); add if not present.
- `coalesce(String maybeNull, String fallback)` — `maybeNull == null ? fallback : maybeNull`. Reuse `org.apache.commons.lang3.StringUtils.defaultString(...)` if the dependency is present; otherwise a local 3-line helper.

---

## 4. Repository / JPA Entity design

### 4.1 Entity: `ApprovalLine`

```java
package com.kosign.wecms.domain.setting.approvalline.entity;

@Entity
@Table(name = "APPR_LINE_INFM")
@IdClass(ApprovalLineId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalLine extends BaseEntity {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "APPR_LINE_NO", precision = 15, scale = 0, nullable = false)
    private Long apprLineNo;

    @Column(name = "APPR_LINE_NM", length = 100, nullable = false)
    private String apprLineNm;

    // REG_ID / REG_DTM / MOD_ID / MOD_DTM come from BaseEntity (already wired with @PrePersist/@PreUpdate
    // for refactor entities that target legacy VARCHAR(14) audit columns).
}
```

> The refactor `BaseEntity` already exists (per `wecms-api/CLAUDE.md`) and supplies `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM`. Confirm at implementation time that `BaseEntity`'s timestamp serialization writes `VARCHAR(14)` for legacy parity (matches `BUSI_INFM` / `USER_INFM` precedent). If not, override locally — but stay consistent with the existing `firm-banking-settings` pattern.

### 4.2 Composite-PK class: `ApprovalLineId`

```java
package com.kosign.wecms.domain.setting.approvalline.entity;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ApprovalLineId implements Serializable {
    private String bizKey;
    private Long   apprLineNo;
}
```

### 4.3 Entity: `ApprovalLineApprover`

```java
package com.kosign.wecms.domain.setting.approvalline.entity;

@Entity
@Table(name = "APPR_LINE_INFM_DETL")
@IdClass(ApprovalLineApproverId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalLineApprover extends BaseEntity {

    @Id @Column(name = "BIZ_KEY",      length = 20,  nullable = false) private String bizKey;
    @Id @Column(name = "APPR_LINE_NO", precision = 15, scale = 0, nullable = false) private Long apprLineNo;
    @Id @Column(name = "APPRVR_ID",    length = 100, nullable = false) private String apprvrId;

    @Column(name = "APPRVR_DISP_ORD", precision = 1, scale = 0, nullable = false)
    private Integer apprvrDispOrd;

    @Column(name = "APPRVR_NM",   length = 100, nullable = false) private String apprvrNm;
    @Column(name = "APPRVR_POSI", length = 100) private String apprvrPosi; // legacy parity: written as "" not null
    @Column(name = "APPRVR_DEPT", length = 100) private String apprvrDept; // same

    // REG_ID / REG_DTM / MOD_ID / MOD_DTM via BaseEntity (legacy detail columns are nullable — keep them nullable here too)
}
```

### 4.4 Composite-PK class: `ApprovalLineApproverId`

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ApprovalLineApproverId implements Serializable {
    private String bizKey;
    private Long   apprLineNo;
    private String apprvrId;
}
```

### 4.5 Repository: `ApprovalLineRepository`

```java
public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, ApprovalLineId> {

    List<ApprovalLine> findByBizKeyAndApprLineNmContainingIgnoreCase(String bizKey, String q, Sort sort);

    List<ApprovalLine> findByBizKey(String bizKey, Sort sort);   // q == null branch
}
```

### 4.6 Repository: `ApprovalLineApproverRepository`

```java
public interface ApprovalLineApproverRepository extends JpaRepository<ApprovalLineApprover, ApprovalLineApproverId> {

    List<ApprovalLineApprover> findAllByBizKeyAndApprLineNoOrderByApprvrDispOrdAsc(String bizKey, long apprLineNo);

    List<ApprovalLineApprover> findAllByBizKeyAndApprLineNoInOrderByApprLineNoAscApprvrDispOrdAsc(String bizKey, Collection<Long> apprLineNos);

    @Modifying
    @Query("delete from ApprovalLineApprover a where a.bizKey = :bizKey and a.apprLineNo = :apprLineNo")
    void deleteAllByBizKeyAndApprLineNo(@Param("bizKey") String bizKey, @Param("apprLineNo") long apprLineNo);
}
```

---

## 5. DTO records

All DTOs live under `com.kosign.wecms.domain.setting.approvalline.dto`.

```java
public record ApprovalLineListResponse(
    int                       totalCount,
    List<ApprovalLineRow>     approvalLines
) {}

public record ApprovalLineRow(
    long                              apprLineNo,
    String                            apprLineNm,
    String                            regDtm,
    List<ApprovalLineApproverRow>     approvers
) {}

public record ApprovalLineApproverRow(
    int    apprvrDispOrd,
    String apprvrId,
    String apprvrNm,
    String apprvrPosi,
    String apprvrDept,
    String userImgUrl       // nullable — null when no image is stored for the approver
) {}

public record ApprovalLineCreateRequest(
    @NotBlank @Size(max = 100)
    String apprLineNm,

    @NotNull @Size(min = 1, max = 5) @Valid
    List<ApprovalLineApproverInput> approvers
) {}

public record ApprovalLineUpdateRequest(
    @NotBlank @Size(max = 100)
    String apprLineNm,

    @NotNull @Size(min = 1, max = 5) @Valid
    List<ApprovalLineApproverInput> approvers
) {}

public record ApprovalLineApproverInput(
    @NotBlank @Size(max = 100) String apprvrId,
    @NotBlank @Size(max = 100) String apprvrNm,
    @Size(max = 100)           String apprvrPosi,    // optional
    @Size(max = 100)           String apprvrDept     // optional
    // apprvr_disp_ord absent — service assigns 1..N
) {}
```

---

## 6. DB impact — NEW vs REUSED tables

### 6.1 Tables — `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL`

| Status | Action |
|---|---|
| **NEW in refactor local DB** (these tables exist on JEX DEV/UAT/PROD but are not yet ported into the local `wecms_dev` schema — verified by reading `wecms-api/CLAUDE.md` § Database migrations: the V1–V23 + V32/V34/V67/etc list contains no `APPR_LINE_INFM` migration). | New Flyway migration: `V{N}__approval_line_schema.sql` mirroring the DEV column shape verified in feature § 3. |
| Two PKs (`(BIZ_KEY, APPR_LINE_NO)` and `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)`) | Inline `PRIMARY KEY(...)` in the `CREATE TABLE`. |
| No FK constraints on DEV today | Refactor adds **none** for parity. Document the logical FK in the schema doc. |
| No additional indexes on DEV today | Refactor adds **one** index in addition to the PKs: `CREATE INDEX ix_appr_line_infm_biz_key_reg_dtm ON APPR_LINE_INFM (BIZ_KEY, REG_DTM DESC)` — supports the default sort. Detail table needs no extra index (PK covers the only access pattern: by `(bizKey, apprLineNo)`). |

### 6.2 Function — `wb_get_next_val`

| Status | Action |
|---|---|
| **MODIFIED** — adds a new branch for `('appr_line_infm', 'appr_line_no', biz_key, …)` | New Flyway migration `V{N+1}__wb_get_next_val_appr_line_branch.sql` that re-creates the function with the additional branch (Postgres `CREATE OR REPLACE FUNCTION` — idempotent). Branch reads `MAX(appr_line_no) + 1` filtered on `biz_key` (mirrors the V11 EMPL_NO branch). |
| Documented exception to "no new legacy DB-function dependencies" rule | Update [`wecms-api/CLAUDE.md` § DB Function / Legacy Function Policy](../../../CLAUDE.md) "Surviving legacy functions" table with the new row + retirement plan: drop the branch once legacy WeCMS / WABOOKS no longer call `wb_get_next_val('appr_line_infm', …)`. |

### 6.3 Detailed Flyway migration plan

Two migrations land in this MR — order matters (schema first, then function).

| Version | Filename | Purpose |
|---|---|---|
| `V{N}` | `V{N}__approval_line_schema.sql` | `CREATE TABLE APPR_LINE_INFM`, `CREATE TABLE APPR_LINE_INFM_DETL`, supporting index. |
| `V{N+1}` | `V{N+1}__wb_get_next_val_appr_line_branch.sql` | `CREATE OR REPLACE FUNCTION wb_get_next_val(...)` with the new `'appr_line_no'` branch. |

The actual `N` is filled at MR-prep time by reading the current head of `src/main/resources/db/migration/`.

Full DDL goes in the schema doc — not duplicated here.

### 6.4 No PROD Active Table List concerns

Both tables (`APPR_LINE_INFM`, `APPR_LINE_INFM_DETL`) are in the JEX PROD Active Table List (verified by their existence in the live JEX schema as queried on DEV). The feature is in scope.

---

## 7. Audit logging

**Not adopted — legacy parity.** Feature § 14 OQ-6 resolved 2026-05-13: refactor keeps the JEX behaviour and writes **no** `AUDIT_LOGS_INFM` rows for Approval Line.

Concretely:

- `ApprovalLineService` does NOT inject `AuditLogService`.
- `ApprovalLineService.{create, update, delete}(...)` do NOT call `auditLog.append(...)`.
- `messages.properties` carries NO `audit.approval_line.*` keys.
- `AUDIT_LOGS_INFM` receives NO rows with `WSVC_ID = 'approval-line'`.

Divergent from the `firm-banking-settings` / `contacts` / `vas-account` precedents (which did add audit on the refactor side) — Approval Line audit history is not a current business requirement, the live JEX `comm_0222_*` actions also write no audit rows, and matching that gap is acceptable to the PO.

A future MR can revisit if/when business requirements change. The shared `common/auditlog/service/AuditLogService` remains untouched.

---

## 8. Approver picker source — out-of-scope decision

The frontend "Add Approver" popover needs a tenant-user directory to display the picker rows. **The endpoint that supplies this directory is out of scope for the Approval Line MR.** Two options for Phase 3:

| Option | Endpoint | When |
|---|---|---|
| **A — Reuse existing** | If `domain/auth/UserController` already exposes a tenant-users list (verify at Phase 3), reuse it. URI pattern: `GET /api/v1/users?biz_key_implicit=true`. | Preferred if shipped. |
| **B — New shared endpoint** | New `GET /api/v1/users` returning every active `USER_BUSI_CONN_INFM` row for the current tenant, projected to `{user_id, user_nm, posi, dept, user_img_url}`. Lives in `domain/auth/`, not `domain/setting/approvalline/` — many features will reuse it. | Ship in a sibling MR before the Approval Line frontend lands. |

If neither lands first, the Approval Line frontend MR uses the existing `APPROVER_POOL` static directory (`approverMockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced)) as a temporary stub and the dialog's Save still works (the backend's tenant-link guard at § 3.5 step 2 will reject any stub-id that doesn't match a real `USER_BUSI_CONN_INFM` row — surfacing the gap honestly).

This is flagged in the planning doc (`open question: tenant-users endpoint`) so it doesn't get lost.

---

## 9. View Setting

**Not adopted.** No `WEB_SVC_ID = 'approval-line'` seed. See feature § 11 for rationale. If/when adopted, the seed migration adds rows to `SYS_SCR_OPTION_WECMS` mirroring the `banking-account-inquiry-vnd` precedent (V32). No controller / service / DTO changes here — the existing global View Setting endpoints handle it.

---

## 10. Logging + observability

- Use SLF4J via `@Slf4j` on the controller and service. **Never** `System.out.println`.
- The request-scoped `traceId` (MDC key) is set by `TraceIdFilter` at the head of the filter chain — every log line automatically carries it via the `%X{traceId}` pattern in `application.yml`. No manual passing.
- Log at INFO on each mutation entry point — one line with `apprLineNo` + `bizKey` + `userId`. Log at WARN on `BusinessException` (already done by `GlobalExceptionHandler`).
- Log at ERROR with stack only for `WBE9999`-class exceptions (already done by the global handler).
