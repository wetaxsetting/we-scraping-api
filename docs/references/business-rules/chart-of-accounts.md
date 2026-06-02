# Chart of Accounts — Spring Boot Backend Design

**Slug:** `chart-of-accounts`
**Tree:** [wecms-api/](../../../)
**Package:** `com.kosign.wecms.domain.setting.chartofaccounts`
**Related docs:** [feature spec](../../features/settings/chart-of-accounts.md) · [migration map](../migration/chart-of-accounts.md) · [database](../database/chart-of-accounts.md) · [frontend](../../../../wecms-web/docs/features/settings/chart-of-accounts.md) · [plan](../../archive/planning/phase-1/chart-of-accounts-plan.md).

This document is the implementation reference for the Spring Boot side. It does not write code — it specifies the package layout, classes, endpoints, payloads, validations, transaction boundaries, and the audit-log + error-handling integration. The matching frontend lives in [wecms-web/components/settings/chart-of-accounts/](../../../../wecms-web/components/settings/chart-of-accounts/) (covered in the [frontend design](../../../../wecms-web/docs/features/settings/chart-of-accounts.md)).

---

## 1. Package layout

```
com.kosign.wecms.domain.setting.chartofaccounts
├── controller/
│   └── ChartOfAccountsController.java        — @RestController, @RequestMapping("/api/v1/settings/chart-of-accounts")
├── service/
│   ├── ChartOfAccountsService.java           — list / tree / create / update / delete; @Transactional
│   ├── ChartOfAccountsTreeBuilder.java       — flat list → level / has_children / breadcrumb projection
│   ├── ChartOfAccountsValidator.java         — cycle + depth + self-parent + restricted-code guards
│   └── ChartOfAccountsAuditLogger.java       — per-mutation AUDIT_LOGS_INFM writer (REQUIRES_NEW)
├── repository/
│   └── ChartOfAccountRepository.java         — JpaRepository<ChartOfAccount, ChartOfAccountId>
├── entity/
│   ├── ChartOfAccount.java                   — @Entity @Table(name = "CHART_ACCT_INFM"); extends BaseEntity
│   └── ChartOfAccountId.java                 — Serializable composite key (bizKey, chartAcctSeqNo)
├── dto/
│   ├── ChartOfAccountRow.java                — list/detail response record
│   ├── ChartOfAccountTreeNode.java           — parent-picker tree response record
│   ├── ChartOfAccountListResponse.java       — { items, total, restricted_codes }
│   ├── ChartOfAccountTreeResponse.java       — { nodes }
│   ├── ChartOfAccountCreateRequest.java      — record with validation annotations
│   └── ChartOfAccountUpdateRequest.java      — record with validation annotations
└── enums/
    ├── ChartOfAccountsSortField.java         — whitelist (CHART_ACCT_CD, CHART_ACCT_NM_EN, STTS, MOD_DTM)
    └── CalRule.java                          — P (PLUS), M (MINUS)
```

`AcctStts` (`A` / `I`) reuses the existing enum if one already exists under `common/enums/`; if not, add it there since it's a shared domain code (per [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Domain enums — `common/enums/`").

---

## 2. REST endpoints

All endpoints are JWT-protected (Settings module — every authenticated tenant user can read; write gating per OQ-1).

| Method | URI | Role gate | Returns | Notes |
|---|---|---|---|---|
| `GET` | `/api/v1/settings/chart-of-accounts` | USER+ | `ApiResponse<ChartOfAccountListResponse>` | Query params: `search`, `stts`, `sort_field`, `sort_direction`. Load-all (no pagination — load size in DEV is ~50 rows per tenant; keep parity with legacy panel). |
| `GET` | `/api/v1/settings/chart-of-accounts/tree` | USER+ | `ApiResponse<ChartOfAccountTreeResponse>` | Used by the inline parent picker. Includes level + has_children + breadcrumb. |
| `GET` | `/api/v1/settings/chart-of-accounts/{seqNo}` | USER+ | `ApiResponse<ChartOfAccountRow>` | Single-row read; used by frontend's "open in edit mode" path when navigating from outside (audit cross-link, etc.) |
| `POST` | `/api/v1/settings/chart-of-accounts` | ADMIN (OQ-1 default) | `ApiResponse<ChartOfAccountRow>` | Returns the freshly created (or revived) row. |
| `PUT` | `/api/v1/settings/chart-of-accounts/{seqNo}` | ADMIN | `ApiResponse<ChartOfAccountRow>` | Path `{seqNo}` is authoritative; body MUST NOT echo `chart_acct_seq_no` / `biz_key`. |
| `DELETE` | `/api/v1/settings/chart-of-accounts/{seqNo}` | ADMIN | `ApiResponse<Void>` | Soft delete. Re-parents children + renames archive collisions in the same tx. |
| `POST` | `/api/v1/settings/chart-of-accounts/bulk-import` | ADMIN | `ApiResponse<BulkImportResult>` | **Phase 2.** Multipart upload; rows-processed / rows-failed summary. Phase 1 returns `WBE9001` ("Endpoint is not yet implemented"). |

Role gating uses `@PreAuthorize("hasRole('ADMIN')")` on the mutating endpoints when OQ-1 settles on ADMIN-only writes. Until OQ-1 resolves, ship without the annotation (matches current JEX behaviour — every tenant user can write).

---

## 3. Request / response payloads

Wire format is lowercase snake_case throughout (global Jackson `SNAKE_CASE` per [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"API Naming & Serialization Standards"). Java DTOs are records with camelCase fields.

### 3.1 `ChartOfAccountListResponse`

```java
public record ChartOfAccountListResponse(
    List<ChartOfAccountRow> items,
    int totalCount,                       // -> total_count
    Set<String> restrictedCodes           // -> restricted_codes — currently {"ACC-01","ACC-02","ACC-03"}
) {}
```

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "items": [
      {
        "chart_acct_seq_no": 70,
        "chart_acct_cd": "ACC-01",
        "chart_acct_nm_en": "REVENUE",
        "chart_acct_nm_vn": "REVENUE",
        "chart_acct_nm_ko": "수입",
        "chart_acct_type": null,
        "parent_chart_acct_cd": null,
        "cal_rule": null,
        "stts": "A",
        "chart_acct_level": 1,
        "has_children": true,
        "restricted": true,
        "reg_id": "VN_VNM0004_000001",
        "reg_dtm": "2025-08-06T06:50:18Z",
        "mod_id": "VN_VNM0004_000001",
        "mod_dtm": "2025-08-06T06:50:18Z"
      }
    ],
    "total_count": 29,
    "restricted_codes": ["ACC-01", "ACC-02", "ACC-03"]
  }
}
```

### 3.2 `ChartOfAccountCreateRequest`

```java
public record ChartOfAccountCreateRequest(
    @NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_\\-]+") String chartAcctCd,
    @NotBlank @Size(max = 255)                                       String chartAcctNmEn,
    @Size(max = 255)                                                 String chartAcctNmVn,
    @Size(max = 255)                                                 String chartAcctNmKo,
    @Size(max = 100)                                                 String chartAcctType,
    @Size(max = 20)                                                  String parentChartAcctCd,  // null ⇒ root
                                                                     CalRule calRule,
    @NotNull                                                         AcctStts stts
) {}
```

### 3.3 `ChartOfAccountUpdateRequest`

Same shape as Create. The path `{seqNo}` carries the row identity — the body MUST NOT echo it.

### 3.4 `ChartOfAccountTreeNode`

```java
public record ChartOfAccountTreeNode(
    Long    chartAcctSeqNo,
    String  chartAcctCd,
    String  chartAcctNmEn,
    String  parentChartAcctCd,
    Integer chartAcctLevel,     // 1..4
    Boolean hasChildren,
    Boolean restricted,
    String  breadcrumb          // "ACC-01 - REVENUE / 1007009 - Collection 4"
) {}
```

---

## 4. Service contracts

### 4.1 `ChartOfAccountsService#list`

```
Inputs:  search (optional), stts (optional), sortField (optional), sortDirection (optional)
Tenant:  TenantContext.currentBizKey()

Flow:
1. Repository query: WHERE biz_key = :tenant AND del_yn = 'N'
   AND (optional) chart_acct_cd ILIKE %:search% OR chart_acct_nm_en ILIKE %:search%
   AND (optional) stts = :stts
   ORDER BY :resolved_sort  (fallback: chart_acct_cd ASC)
2. Compute hasChildren in a single grouping query (SELECT parent_chart_acct_cd, COUNT(*) GROUP BY parent_chart_acct_cd).
3. Compute chartAcctLevel by walking parents in memory (4-level cap; rows below level 4 are still surfaced in the list, but flagged with chart_acct_level=null so the frontend can render them under a "Deprecated depth" footnote — defensive against legacy data that pre-dates the cap).
4. Map to ChartOfAccountRow; stamp restricted = code ∈ {ACC-01, ACC-02, ACC-03}.
5. Return ChartOfAccountListResponse(items, items.size(), restrictedCodes).
```

### 4.2 `ChartOfAccountsService#tree`

```
1. Repository: load all rows for tenant where del_yn = 'N'.
2. Delegate to ChartOfAccountsTreeBuilder which:
   a. Indexes rows by chart_acct_cd.
   b. Walks each row's parent chain (up to 4 hops); produces level (1..4) and breadcrumb.
   c. Computes hasChildren via the same grouping query as #list.
   d. Filters to rows with level ∈ [1,4] (matches legacy R002 ORDER BY chart_acct_cd ASC + WHERE level IS NOT NULL).
3. Returns ChartOfAccountTreeResponse(nodes).
```

### 4.3 `ChartOfAccountsService#create` — `@Transactional`

```
1. Validate request (cycle / depth / self-parent / restricted-as-parent OK).
2. If request.chart_acct_cd is in restrictedCodes → still allowed (Create is not gated on restricted; only Update / Delete on existing rows are).
3. Look up active row with same (biz_key, chart_acct_cd, del_yn='N')
   → if present, throw WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE.
4. Look up deleted row with same (biz_key, chart_acct_cd, del_yn='Y'):
   a. If present: revive it.
      - existing.delYn = 'N'
      - existing.chartAcctNmEn / Vn / Ko / Type / parentCd / calRule / stts ← request
      - existing.modId = TenantContext.currentUserId()
      - existing.modDtm = OffsetDateTime.now()
      - JPA dirty-checking writes UPDATE.
   b. Else: insert new.
      - seqNo = LegacyFunctionRepository.nextChartAcctSeqNo(bizKey)  // per-tenant MAX+1
      - new entity with all fields + regId / regDtm.
      - chartOfAccountRepo.save(entity).
5. Audit log: ChartOfAccountsAuditLogger.logCreate(entity, prior=null).
6. Return ChartOfAccountRow projection.

Retry-on-collision: if save() throws DataIntegrityViolationException (race on (biz_key, code, del_yn)), retry once after re-running step 3. Second failure → WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE. Matches the one-shot retry pattern in ContactsService#create (V113).
```

### 4.4 `ChartOfAccountsService#update` — `@Transactional`

```
1. Load existing row (biz_key, seqNo) WHERE del_yn='N'.
   → not found: WBE_CHART_OF_ACCOUNTS_NOT_FOUND.
2. Restricted-code guard: if existing.chartAcctCd ∈ restrictedCodes → WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE.
3. If request.chart_acct_cd != existing.chart_acct_cd:
   a. Reject if request code ∈ restrictedCodes (cannot rename TO a restricted code).
   b. Reject if another active row in tenant has the new code → WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE.
4. If request.parent_chart_acct_cd != existing.parent_chart_acct_cd:
   a. If non-null:
      i.   Reject if equals request.chart_acct_cd → WBE_CHART_OF_ACCOUNTS_SELF_PARENT.
      ii.  Lookup parent row (active, same tenant); not found → WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND.
      iii. Walk new-parent ancestry; if existing.chart_acct_cd appears → WBE_CHART_OF_ACCOUNTS_CYCLE.
      iv.  Compute resulting depth of the subtree rooted at `existing`; if > 4 → WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED.
5. Apply field changes; existing.modId / modDtm.
6. (NOT ported: the legacy u001:237-256 parent-cleanup block.)
7. Audit log: ChartOfAccountsAuditLogger.logUpdate(existing, prior).
8. Return refreshed ChartOfAccountRow.
```

### 4.5 `ChartOfAccountsService#delete` — `@Transactional`

```
1. Load existing row (biz_key, seqNo) WHERE del_yn='N'.
   → not found: WBE_CHART_OF_ACCOUNTS_NOT_FOUND.
2. Restricted-code guard: existing.chartAcctCd ∈ restrictedCodes → WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE.
3. Re-parent children (mirrors legacy d001 step 3A; folds the comm_0223_001_u002 IDO in):
   newParent = existing.parentChartAcctCd  (may be null when existing is a root)
   chartOfAccountRepo.updateParentForChildren(bizKey, oldCode = existing.chartAcctCd, newParent, modUser, modDtm)
   via @Modifying @Query("UPDATE ChartOfAccount c SET c.parentChartAcctCd = :newParent, c.modId = :modUser, c.modDtm = :modDtm WHERE c.bizKey = :bizKey AND c.parentChartAcctCd = :oldCode AND c.delYn = 'N'")
4. Avoid UK collision on (biz_key, code, del_yn='Y'):
   Check whether another deleted row exists with same (biz_key, chart_acct_cd).
   If yes → rename existing.chartAcctCd to ChartOfAccountsService.archivedCodeOf(existing.chartAcctCd, existing.chartAcctSeqNo)
            // prefix(0..4) + last5(seq_no) + 'D' — mirrors legacy d001_act.jsp:120-126 (rule preserved verbatim for migration parity)
5. existing.delYn = 'Y'; existing.modId / modDtm bumped. JPA dirty-checking writes UPDATE.
6. Audit log: ChartOfAccountsAuditLogger.logDelete(existing, prior).
7. Return void; controller wraps in ApiResponse.ok().
```

---

## 5. Validation

All bean validation lives on the request records (see §3). Cross-field / cross-row validation runs in `ChartOfAccountsValidator` invoked by the service:

| Check | Method | Throws |
|---|---|---|
| Self-parent | `assertNotSelfParent(reqCode, reqParent)` | `WBE_CHART_OF_ACCOUNTS_SELF_PARENT` |
| Parent exists in tenant | `assertParentExists(bizKey, parentCd)` | `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND` |
| No cycle | `assertNoCycle(bizKey, rowCode, newParent)` | `WBE_CHART_OF_ACCOUNTS_CYCLE` |
| Depth ≤ 4 after edit | `assertDepthCap(bizKey, rowCode, newParent)` | `WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED` |
| Not a restricted code (Update / Delete) | `assertNotRestricted(rowCode)` | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` |
| Cannot rename TO restricted code | `assertNotRenamingToRestricted(oldCode, newCode)` | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` |

`ChartOfAccountsValidator` reads the full tenant slice once per write (cheap — ~50 rows in DEV) and walks the chain in memory. Don't issue four separate SELECTs per Update.

---

## 6. Error handling

| Error code | HTTP | English message | Trigger |
|---|---|---|---|
| `WBE_CHART_OF_ACCOUNTS_NOT_FOUND` | 200 (BusinessException) | "Chart of accounts entry not found." | Update / Delete on missing or wrong-tenant seq |
| `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` | 200 | "Account code is already in use." | Create / Update active dup |
| `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND` | 200 | "Parent account not found." | Create / Update parent-resolve |
| `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` | 200 | "This account is system-managed and cannot be edited or deleted." | Update / Delete on ACC-01/02/03; Update renaming TO restricted |
| `WBE_CHART_OF_ACCOUNTS_CYCLE` | 200 | "Selected parent would create a cycle in the hierarchy." | Update parent-cycle |
| `WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED` | 200 | "Account hierarchy cannot exceed 4 levels." | Create / Update depth-cap |
| `WBE_CHART_OF_ACCOUNTS_SELF_PARENT` | 200 | "An account cannot be its own parent." | Create / Update self-parent |
| `WBE9001` | 200 | "Endpoint is not yet implemented." | Phase-1 bulk-import stub |

Validation failures (`@NotBlank`, `@Size`, `@Pattern`, `@NotNull`) flow through `WBE_VALIDATION` via the existing `MethodArgumentNotValidException` handler in [GlobalExceptionHandler](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) — no new handler needed.

Add the new codes to [messages.properties](../../../src/main/resources/messages.properties) + `messages_en.properties` first; locale files follow once translations land. EN strings above are normative.

---

## 7. Audit logging

Per the migration-doc Δ9, refactor writes one `AUDIT_LOGS_INFM` row per CRUD operation. This is **additive** vs. legacy (`comm_0223_*` writes nothing today). Default decision until OQ-Audit resolves: **enabled**, matching the firm-banking-settings / contacts precedent.

`ChartOfAccountsAuditLogger` is `@Transactional(propagation = REQUIRES_NEW)` so an audit-row failure does NOT roll back the business mutation. Columns:

| Column | Source | Example |
|---|---|---|
| `WSVC_ID` | constant `"chart-of-accounts"` (slug, NOT `comm_0223_001`) | `chart-of-accounts` |
| `BIZ_KEY` | `TenantContext.currentBizKey()` | `VN_VNM0004_000001` |
| `USER_ID` | `TenantContext.currentUserId()` | `lamine@gmail.com` |
| `ACTION` | `CREATE` / `UPDATE` / `DELETE` | `CREATE` |
| `TARGET_TYPE` | constant `"CHART_ACCT_INFM"` | `CHART_ACCT_INFM` |
| `TARGET_ID` | `entity.chartAcctSeqNo` | `99` |
| `MESSAGE_KEY` | `"audit.chart_of_accounts.create"` (slug-prefixed) | — |
| `MESSAGE_ARGS` | JSON-encoded array of args (code + name_en) | `["1007009","Collection 4"]` |
| `BEFORE_JSON` | prior entity (Update/Delete only) | — |
| `AFTER_JSON` | new entity (Create/Update) | — |
| `REG_DTM` | now | — |

i18n entries (`audit.chart_of_accounts.create` / `.update` / `.delete`) land in `messages.properties` and the frontend audit-log popup renders via `t(message_key, message_args)`. Frontend already supports this via the existing `<AuditLogsDialog>` per [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §3a.

---

## 8. Reusable components leveraged

| Component | Source | Usage here |
|---|---|---|
| `BaseEntity` | [common/entity/BaseEntity.java](../../../src/main/java/com/kosign/wecms/common/entity/BaseEntity.java) | `ChartOfAccount` extends — inherits `REG_ID/REG_DTM/MOD_ID/MOD_DTM` audit columns + `@PrePersist/@PreUpdate` |
| `TenantContext` | [common/tenant/TenantContext.java](../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | Tenant + user resolution. Never read `biz_key` from request. |
| `BusinessException` | [common/exception/BusinessException.java](../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) | Throw with code + optional args. |
| `ApiResponse` | [common/response/ApiResponse.java](../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) | `ApiResponse.ok(data)` / propagated automatically for errors via `@RestControllerAdvice`. |
| `LegacyFunctionRepository` | [common/legacy/LegacyFunctionRepository.java](../../../src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java) | Extend with `nextChartAcctSeqNo(bizKey)` calling the new V?? `wb_get_next_val` branch. See [database doc](../database/chart-of-accounts.md) §"Surviving legacy functions". |
| `GlobalExceptionHandler` | [common/exception/GlobalExceptionHandler.java](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) | Picks up `BusinessException` + validation failures; emits the standard envelope. No new handler. |
| `JwtAuthenticationFilter` + `SecurityConfig` | [common/security/](../../../src/main/java/com/kosign/wecms/common/security/) | JWT-protected via the catch-all rule. No public exposure. |
| Common Settings audit infra (if generalised) | [common/audit/AuditLogsService.java](../../../src/main/java/com/kosign/wecms/common/auditlog/service/AuditLogService.java) (if/once a shared one lands; otherwise replicate the firm-banking-settings pattern locally) | `ChartOfAccountsAuditLogger` writes via the shared service. |

**No new shared components are needed.** The feature stays self-contained in `domain/setting/chartofaccounts/`.

---

## 9. Sort field whitelist

`ChartOfAccountsSortField` (per [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Table sort" defence-in-depth rule):

```java
public enum ChartOfAccountsSortField {
    CHART_ACCT_CD("chartAcctCd"),
    CHART_ACCT_NM_EN("chartAcctNmEn"),
    STTS("stts"),
    MOD_DTM("modDtm");

    private final String jpqlField;
    ...
    public static Optional<ChartOfAccountsSortField> resolve(String wireField) { ... }
}
```

Unknown / blank `sort_field` falls back to the default `CHART_ACCT_CD ASC` (matches legacy R001's `ORDER BY CHART_ACCT_CD`). Default sort direction is `ASC` unless `sort_field` resolves to `MOD_DTM` (which defaults to `DESC` — "recently changed first" matches the implicit `sysList → sort by mod_dtm desc` convention in other Settings panels).

`CHART_ACCT_NM_VN` / `CHART_ACCT_NM_KO` / `CHART_ACCT_TYPE` are **not** sortable in Phase 1 — adding them is a one-line whitelist extension when the frontend wires the column.

---

## 10. Concurrency & transaction boundaries

| Operation | Tx scope | Concurrency story |
|---|---|---|
| `list` / `tree` / `getOne` | `@Transactional(readOnly = true)` | Reads only; no locking. |
| `create` | `@Transactional` | Single-row INSERT or revive UPDATE. DB UK + one-shot retry handle the dup race. |
| `update` | `@Transactional` | Single-row UPDATE. Optimistic via `@Version` is NOT added (legacy has no version column; matches Settings precedent). Lost-update risk is acceptable for a low-write Settings table. |
| `delete` | `@Transactional` | Up-to-`#children` UPDATEs + 1 UPDATE on the deleting row. All in one tx; rollback on any failure. |
| `bulkImport` (Phase 2) | `@Transactional` per batch (chunk size 500) | Per-row validation; partial success returns `BulkImportResult{ processed, failed[] }`. |
| Audit logger | `@Transactional(propagation = REQUIRES_NEW)` | Audit row survives a downstream rollback; audit failure logged at WARN but never rethrown. |

---

## 11. Controller skeleton (reference shape — not the final code)

```java
@RestController
@RequestMapping("/api/v1/settings/chart-of-accounts")
@RequiredArgsConstructor
@Slf4j
public class ChartOfAccountsController {

    private final ChartOfAccountsService service;

    @GetMapping
    public ApiResponse<ChartOfAccountListResponse> list(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) AcctStts stts,
        @RequestParam(name = "sort_field", required = false)     String sortField,
        @RequestParam(name = "sort_direction", required = false) Sort.Direction sortDirection
    ) {
        return ApiResponse.ok(service.list(search, stts, sortField, sortDirection));
    }

    @GetMapping("/tree")
    public ApiResponse<ChartOfAccountTreeResponse> tree() {
        return ApiResponse.ok(service.tree());
    }

    @GetMapping("/{seqNo}")
    public ApiResponse<ChartOfAccountRow> getOne(@PathVariable Long seqNo) {
        return ApiResponse.ok(service.getOne(seqNo));
    }

    @PostMapping
    public ApiResponse<ChartOfAccountRow> create(@Valid @RequestBody ChartOfAccountCreateRequest req) {
        return ApiResponse.ok(service.create(req));
    }

    @PutMapping("/{seqNo}")
    public ApiResponse<ChartOfAccountRow> update(
        @PathVariable Long seqNo,
        @Valid @RequestBody ChartOfAccountUpdateRequest req
    ) {
        return ApiResponse.ok(service.update(seqNo, req));
    }

    @DeleteMapping("/{seqNo}")
    public ApiResponse<Void> delete(@PathVariable Long seqNo) {
        service.delete(seqNo);
        return ApiResponse.ok();
    }
}
```

Controller stays thin — no logic, no audit, no exception swallowing. Service layer owns everything.

---

## 12. Open questions deferred to the plan

The OQ list lives in the [feature spec §7](../../features/settings/chart-of-accounts.md#7-open-questions-for-mr-jung-po). The two that materially shape the backend:

- **OQ-1** — ADMIN-only writes vs. legacy "every user". Default: ship without `@PreAuthorize`; add it when OQ-1 lands ADMIN-only.
- **OQ-Audit** (introduced in this doc) — write audit rows for every CRUD op. Default: enabled. If OQ-Audit settles NO, drop `ChartOfAccountsAuditLogger` and the i18n keys.

Everything else (cycle / depth / self-parent guards, dropped cleanup branch) is documented behaviour change with backwards-compatible defaults — proceed without blocking on PO.
