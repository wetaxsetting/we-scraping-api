# Purpose — Business Flow (Backend)

End-to-end request flow for every Purpose operation. Source of truth: the implemented Spring code
under `com.kosign.wecms.domain.setting.purposesettings`. Legacy behaviour reference:
[docs/jex/features/purpose/business-flow.md](../../../../../docs/jex/features/purpose/business-flow.md).

```
User (tenant admin)
      │
      ▼
Settings modal → Purpose panel
      │   (every request carries the JWT; @menuAuthz.canAccess('settings-core-purpose') gates the controller)
      ▼
PurposeSettingsController  ──►  PurposeSettingsService / PurposeExcelService
      │                               │
      │                               ├─ TenantContext.currentBizKey()  (tenant scope, never a param)
      │                               ├─ PurposeSettingsRepository      (active-scope JPQL + native PK-gen)
      │                               ├─ PurposeInUseRepository         (v_purpose_in_use exists / bulk lookup)
      │                               └─ AuditLogService.append(...)    (Create/Update/Delete/Reorder only)
      ▼
ApiResponse<...>  (lowercase snake_case envelope)
```

---

## 1. List + search

```
GET /api/v1/settings/purpose-settings[?keyword=…]
  → service.list(keyword)
      → repo.findActiveForTenant(bizKey, RESERVED_SYSTEM_CODES, keyword ?? "")
            WHERE biz_key = :bizKey
              AND usag_grp_cd IS NULL          -- refactor "level-2 hierarchy" scope
              AND use_yn = 'Y'                 -- active only (soft-delete filter)
              AND use_usag_cd NOT IN :reserved -- 1000000301, 3000000401..3000000408
              AND (:keyword = '' OR LOWER(use_usag_nm) LIKE LOWER('%'||:keyword||'%'))
            ORDER BY disp_ord ASC NULLS LAST, use_usag_cd ASC
      → resolveInUseCodes(...) via PurposeInUseRepository.findInUseCodes(...)
      → assembleTree(rows, inUse)             -- flat rows → level-1-with-children tree
  → PurposeSettingListResponse { list: PurposeSettingRow[] (each with children[]) }
```

- The keyword merges the legacy `comm_0187_001_r001` (full list) + `_r002` (name search) into one endpoint.
- `usedInTransactions` is computed **recursively** during tree assembly: a level-1 row is "in use" when it itself OR any active child is referenced.
- No server-side pagination — the tree is returned in full (legacy parity; the panel renders the whole tree).

## 2. Detail

```
GET /api/v1/settings/purpose-settings/{use_usag_cd}
  → service.detail(useUsagCd)
      → repo.findByBizKeyAndUseUsagCd(bizKey, useUsagCd)  (or 404)
      → resolve usedInTransactions from the view
  → PurposeSettingDetail  (flat — no children)
```

Convenience read for an edit pre-fetch. The list already carries every field, so the frontend rarely needs it.

## 3. Create

```
POST /api/v1/settings/purpose-settings   body: PurposeSettingCreateRequest
  → @Valid (bean validation: @NotBlank hand_use_usag_cd / use_usag_nm, @Size, disp_yn @Pattern[YN])
  → service.create(req)  @Transactional
      1. validate hand_use_usag_cd unique among active rows         → WBW1152 on dup
      2. validate use_usag_nm unique among active rows              → WBW1138 on dup
      3. validateParent(parent): must be active level-1, not self   → WBE_VALIDATION
      4. useUsagCd = repo.nextUseUsagCd(bizKey)                      -- wb_get_next_val branch, 10-digit
      5. dispOrd  = repo.findMaxDispOrd(bizKey).orElse(0) + 1        -- new row sorts last
      6. useYn='Y', bizKey/modId/modDtm stamped from context
      7. repo.save(entity)  (one-shot retry on PK DataIntegrityViolation)
      8. auditLogService.append(CREATE)
  → PurposeSettingDetail (post-write echo)
```

## 4. Update

```
PUT /api/v1/settings/purpose-settings/{use_usag_cd}   body: PurposeSettingUpdateRequest
  → service.update(useUsagCd, req)  @Transactional
      1. load row by PK                                             (or 404)
      2. hand_use_usag_cd: NO duplicate check (legacy parity, OQ-4)
      3. use_usag_nm: unique excluding self                         → WBW1138
      4. parent: active level-1; if assigning a parent to a row that
         itself has active children → validateNoDemoteWithChildren  → WBE_VALIDATION
      5. apply fields, stamp modId/modDtm
      6. auditLogService.append(UPDATE)
  → PurposeSettingDetail (refreshed)
```

## 5. Soft-delete

```
DELETE /api/v1/settings/purpose-settings/{use_usag_cd}
  → service.delete(useUsagCd)  @Transactional
      1. if repo.hasActiveChildren(bizKey, code)                    → WBE1199  (legacy "has children")
      2. OQ-5 recursive in-use check:
           codes = { code } ∪ repo.findActiveChildCodes(bizKey, code)
           if inUseRepo.existsAnyByBizKeyAndUseUsagCdIn(bizKey, codes) → WBE1199
      3. entity.useYn = 'N'; stamp modId/modDtm; repo.save(entity)   -- SOFT delete, never physical
      4. auditLogService.append(DELETE)
  → 200, data: null
```

The legacy "permanent (physical) delete" and "restore from trash" paths are **not** ported — see
[refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-1/GAP-2 (DEAD LEGACY).

## 6. Reorder

```
PUT /api/v1/settings/purpose-settings/order   body: PurposeReorderRequest { items: [{use_usag_cd, disp_ord}] }
  → service.reorder(req)  @Transactional
      1. reject duplicate use_usag_cd in payload                    → WBE_VALIDATION
      2. reject duplicate disp_ord in payload                       → WBE_VALIDATION
      3. every use_usag_cd must resolve to an active refactor row   → WBE_VALIDATION
      4. bulk-load by PK set; Hibernate dirty-checks the new disp_ord values; stamp modId/modDtm
      5. auditLogService.append(REORDER, args=[rows.size()])
  → PurposeSettingListResponse (refreshed tree)
```

## 7. Excel — preview then import (two-step)

```
POST /api/v1/settings/purpose-settings/excel/preview   multipart: file
  → excelService.preview(file)
      - Apache POI parse; header at row index 2, data from row 3 (title + spacer skipped)
      - MAX_ROWS = 500  → WBE2041 when exceeded (file-level)
      - per row, per cell validation → error codes:
          required · length · dup_file · dup_db · parent_not_found · parent_not_root
      - parent resolution: try roots-in-file first, then active level-1 in DB
  → PurposeExcelPreview { rows[] (valid+invalid, pre-localized errors), total_count, completed_count, error_count }

POST /api/v1/settings/purpose-settings/excel/import   body: PurposeExcelImportRequest { rows[] }
  → excelService.import_(req)  @Transactional
      - re-validate every row against LIVE DB (preview 'valid' flag is never trusted — race-safety)
      - insert order: ROOTS first, children second (children resolve parent's fresh use_usag_cd)
      - pure-append (no replace/reset mode); any row failure rolls back the whole batch → WBE_VALIDATION
      - delegates each row to the same create-path validation + PK generation
  → PurposeExcelImportResult { inserted_count }
```

The "Download Template" button is served by a static XLSX under `wecms-web/public/templates/purpose/`
(locale-aware EN/KO/VI) — no backend endpoint involved.

---

## Tenant + audit invariants (every mutating path)

- **Tenant** comes only from `TenantContext.currentBizKey()` (signed JWT) — never a request body/param. `bizKey` is the first PK component of every query.
- **Audit** rows are written for Create / Update / Delete / Reorder with `WSVC_ID = 'purpose-settings'`, `REF_NO = 'BUSI_USAG_SET_INFM'`, `REF_NO1 = use_usag_cd`, `REF_NO2 = hand_use_usag_cd`, and a localized detail string resolved from `audit.purpose_settings.*` keys. Reads and Excel preview write **no** audit row.
- See [functional-specification.md §Audit](functional-specification.md) for the column map.
