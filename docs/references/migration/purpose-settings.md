# Purpose Settings — JEX → Spring + Next.js mapping


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../database/flyway-migration-baseline.md) §5.

**Refactor slug:** `purpose-settings`
**Sibling docs:** [feature spec](../../features/settings/purpose.md) · backend design (Needs Confirmation) · [frontend design](../../../../wecms-web/docs/features/settings/purpose.md) · DB schema (Needs Confirmation) · task plan _(removed — historical `archive/` folder deleted by project decision)_.
**Mirrors live JEX:** `comm_0187_001_r001` (list), `_r002` (name search — merged into list), `_c001` (create), `_u002` (edit + soft-delete), `_u004` (reorder), `comm_0187_004_c001` (Excel upload). Out of scope: `_u003` (reset to system defaults), `comm_0187_003_*` (Unused / restore).

This document is the JEX-side ↔ refactor-side per-artifact mapping. Phase 1 only — no code.

> Legacy WSVC ids are preserved only as JEX file:line citations and in the migration-traceability prose below — never as new identifier surfaces. See [docs/refactor-wecms/features/settings/purpose-settings.md §4](../../features/settings/purpose.md) for the slug-vs-WSVC-id rule.

---

## 1. Service-by-service mapping

| JEX WSVC | Method | URI | Spring controller method | Spring service method | Status |
|---|---|---|---|---|---|
| `comm_0187_001_r001` (List) | `GET` | `/api/v1/settings/purpose-settings` | `PurposeSettingsController.list(@RequestParam keyword)` | `PurposeSettingsService.list(bizKey, keyword)` | Phase 2 |
| `comm_0187_001_r002` (Name search) | merged with list | — | — (subsumed by `keyword`) | — | merged |
| `comm_0187_001_r001` (Detail) — single-PK convenience | `GET` | `/api/v1/settings/purpose-settings/{useUsagCd}` | `PurposeSettingsController.detail(@PathVariable useUsagCd)` | `PurposeSettingsService.detail(bizKey, useUsagCd)` | Phase 2 |
| `comm_0187_001_c001` (Create) | `POST` | `/api/v1/settings/purpose-settings` | `PurposeSettingsController.create(@RequestBody PurposeSettingCreateRequest)` | `PurposeSettingsService.create(bizKey, userId, request)` | Phase 2 |
| `comm_0187_001_u002` REC (Update) | `PUT` | `/api/v1/settings/purpose-settings/{useUsagCd}` | `PurposeSettingsController.update(@PathVariable useUsagCd, @RequestBody PurposeSettingUpdateRequest)` | `PurposeSettingsService.update(bizKey, userId, useUsagCd, request)` | Phase 2 |
| `comm_0187_001_u002` REC2 (Soft-delete) | `DELETE` | `/api/v1/settings/purpose-settings/{useUsagCd}` | `PurposeSettingsController.delete(@PathVariable useUsagCd)` | `PurposeSettingsService.delete(bizKey, userId, useUsagCd)` | Phase 2 |
| `comm_0187_001_u004` (Reorder) | `PUT` | `/api/v1/settings/purpose-settings/order` | `PurposeSettingsController.reorder(@RequestBody PurposeReorderRequest)` | `PurposeSettingsService.reorder(bizKey, userId, request)` | Phase 2 |
| `comm_0187_004_r001` (Excel preview) | `POST` | `/api/v1/settings/purpose-settings/excel/preview` | `PurposeSettingsController.excelPreview(@RequestPart MultipartFile file)` | `PurposeExcelService.preview(file)` | Phase 3 |
| `comm_0187_004_c001` (Excel import) | `POST` | `/api/v1/settings/purpose-settings/excel/import` | `PurposeSettingsController.excelImport(@RequestBody PurposeExcelImportRequest)` | `PurposeExcelService.import_(req)` | Phase 3 |
| `comm_0187_001_u003` (Reset to system defaults) | — | — | — | — | **Out of scope** |
| `comm_0187_003_r001` / `_u001` (Unused / restore) | — | — | — | — | **Out of scope** |
| `comm_0187_002` (Standardization settings) | — | — | — | — | **Out of scope** |

---

## 2. Layer-by-layer mapping

### 2.1 Persistence — IDO → JPA repository

| JEX IDO | SQL shape | Spring repository method |
|---|---|---|
| `IDO.BUSI_USAG_SET_INFM_R001` (default read) | `SELECT … FROM BUSI_USAG_SET_INFM WHERE 1=1 ??` (DYNAMIC_0) | `PurposeSettingsRepository.findAll(spec)` — Spring Data JPA `Specification<BusiUsagSet>` covering tenant, `usag_grp_cd IS NULL`, `use_yn='Y'`, reserved-codes exclusion, optional keyword, ordered by `disp_ord`. The list endpoint additionally LEFT JOINs `v_purpose_in_use` (see database doc §3 (Needs Confirmation)). |
| `IDO.BUSI_USAG_SET_INFM_C001` | `INSERT INTO BUSI_USAG_SET_INFM (…) VALUES (…)` | `repo.save(entity)` |
| `IDO.BUSI_USAG_SET_INFM_U001` | `UPDATE BUSI_USAG_SET_INFM SET … WHERE biz_key=? AND use_usag_cd=?` | `repo.save(entity)` (dirty update on managed entity) |
| `IDO.BUSI_USAG_SET_INFM_U002` (partial update) | `UPDATE …` | merged into the single `repo.save(...)` path; the refactor does not split full/partial updates — every PUT writes the full entity |
| `IDO.BUSI_USAG_SET_INFM_U003` (reset-related) | `UPDATE …` | **Out of scope** (legacy `_u003_act.jsp` is the reset action) |
| `IDO.BUSI_USAG_SET_INFM_D001` | `DELETE …` | **Not used** — refactor preserves legacy soft-delete (`use_yn='N'`); hard-delete IDO is intentionally unmapped |
| `IDO.BUSI_USAG_SET_INFM_LANG_DETL_*` | INSERT/READ/UPDATE/DELETE on language detail table | **Out of scope** — refactor follows the single-table model documented in legacy [comm_0187_001_c001_act.jsp:86-88](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0187_001_c001_act.jsp); name + description live on `BUSI_USAG_SET_INFM` directly |

### 2.2 Entity / VO → DTO / Entity

| JEX class | Refactor class |
|---|---|
| [`wabooks.entity.BusiUsagSetInfm`](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/BusiUsagSetInfm.java) | `com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSet` |
| [`wabooks.entity.BusiUsagSetInfmPk`](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/BusiUsagSetInfmPk.java) | `com.kosign.wecms.domain.setting.purposesettings.entity.BusiUsagSetId` (composite `@IdClass`) |
| [`wabooks.dao.BusiUsagSetInfmDAO`](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/BusiUsagSetInfmDAO.java) | `com.kosign.wecms.domain.setting.purposesettings.repository.PurposeSettingsRepository` extends `JpaRepository<BusiUsagSet, BusiUsagSetId>` |
| JEX `JexData input.getString("USE_USAG_NM")` envelope | `PurposeSettingCreateRequest.useUsagNm` (record component) + `PurposeSettingUpdateRequest.useUsagNm` |
| JEX `result.put("REC", rows)` | `ApiResponse<PurposeSettingListResponse>` with `list: PurposeSettingRow[]` |

### 2.3 JEX field → wire field

JEX entity ships UPPER_SNAKE via `@JsonProperty` annotations ([BusiUsagSetInfm.java:34-75](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/BusiUsagSetInfm.java)); refactor strips every `@JsonProperty("UPPER_SNAKE")` per CTO directive (2026-05-08). Java fields stay camelCase; global Jackson `SNAKE_CASE` strategy converts on the wire.

| JEX wire field (legacy) | Refactor wire field (new) | Java field |
|---|---|---|
| `BIZ_KEY` | omitted from wire (server-resolved via `TenantContext`) | (entity-only) |
| `USE_USAG_CD` | `use_usag_cd` | `useUsagCd` |
| `HAND_USE_USAG_CD` | `hand_use_usag_cd` | `handUseUsagCd` |
| `USE_USAG_NM` | `use_usag_nm` | `useUsagNm` |
| `USE_USAG_CONT` | `use_usag_cont` | `useUsagCont` |
| `PARENT_USE_USAG_CD` | `parent_use_usag_cd` | `parentUseUsagCd` |
| `DISP_YN` | `disp_yn` | `dispYn` |
| `DISP_ORD` | `disp_ord` | `dispOrd` |
| `USE_YN` | not on wire (always `'Y'` for visible rows) | `useYn` (entity-only) |
| `USAG_GRP_CD` | not on wire (always NULL — refactor scope) | `usagGrpCd` (entity-only) |
| `REPT_DV`, `REPT_GRP`, `USAG_TYPE` | not on wire (legacy aux columns, never set in the level-2 hierarchy flow) | omitted |
| `MOD_ID` | `mod_id` | `modId` |
| `MOD_DTM` | `mod_dtm` | `modDtm` |
| (derived) | `used_in_transactions` | `usedInTransactions` |
| (legacy `REC` envelope) | `data.list[]` | — |
| (legacy `REPT_DV_NM`, `REPT_GRP_NM`, `USAG_NM`, `USAG_CD_YN`) | dropped — derived columns of the legacy view not used by refactor | — |

---

## 3. Layer-by-layer reuse — backend

The refactor consumes existing shared components instead of re-implementing them. Same pattern as `firm-banking-settings` (see [docs/refactor-wecms/features/settings/firm-banking-settings.md §4.1](../../features/settings/firm-banking-settings.md)).

| Concern | Reuse |
|---|---|
| Tenant scoping | `TenantContext.currentBizKey()` — every repository call is constrained on `biz_key`. |
| Auth / JWT | `JwtAuthenticationFilter` + `SecurityConfig`. The `/api/v1/settings/**` matcher already requires JWT. |
| Authority gate | `@PreAuthorize("hasRole('ADMIN')")` on every mutating method. The `authGrCd` claim already lives in the JWT (set at login finalize from `BUSI_ADMN_INFM`). |
| Locale resolution | `LangCd` enum + `MessageResolver`. Accept-Language → `LangCd`. |
| Error envelope | `ApiResponse<T>` + `BusinessException(code, args…)` + `GlobalExceptionHandler`. No parallel envelope. |
| Audit base columns on entity | `BaseEntity` (`REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM`) — see database doc §2 (Needs Confirmation) for the legacy-column-mapping note. |
| Audit log writer | Shared `AuditLogService` (lives at `common/auditlog/` per `firm-banking-settings`'s introduction). Appends in `@Transactional(REQUIRES_NEW)`. |
| Wire format | Global Jackson `SNAKE_CASE` strategy from [`JacksonConfig`](../../../src/main/java/com/kosign/wecms/config/JacksonConfig.java). No `@JsonProperty("UPPER_SNAKE")`. |
| Validation | Bean Validation (`@NotBlank`, `@Size`, `@Pattern`) on record components. |
| Tenant write idempotence | `JpaRepository.save(entity)` — JEX's manual `update()` calls map 1:1 onto Spring Data dirty updates. |

No new shared infrastructure required. The shared `AuditLogService` already exists (introduced by `firm-banking-settings` Phase 2 — see its [V63 migration](../../../src/main/resources/db/migration/V8__audit_logs_infm.sql)).

---

## 4. Layer-by-layer reuse — frontend

| Concern | Reuse |
|---|---|
| Panel shell | [`<SettingsModal>`](../../../../wecms-web/components/settings/SettingsModal.tsx) — Settings modal already wired; Phase 3 only swaps mock data. |
| Panel composition | [`<PurposePanel>`](../../../../wecms-web/components/settings/purpose/PurposePanel.tsx) — full layout already exists. |
| Table | [`<PurposeTable>`](../../../../wecms-web/components/settings/purpose/PurposeTable.tsx) + [`<PurposeRow>`](../../../../wecms-web/components/settings/purpose/PurposeRow.tsx) + [`<PurposeTableColumns>`](../../../../wecms-web/components/settings/purpose/PurposeTableColumns.tsx). |
| Inline add / edit | [`<PurposeFormRow>`](../../../../wecms-web/components/settings/purpose/PurposeFormRow.tsx). |
| Row actions | [`<RowActions>`](../../../../wecms-web/components/settings/purpose/RowActions.tsx) — already takes `deleteDisabled`. |
| Excel upload | [`<ExcelUploadDialog>`](../../../../wecms-web/components/settings/purpose/ExcelUploadDialog.tsx) — wraps shared [components/shared/excel-upload/ExcelUploadDialog.tsx](../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx). |
| Confirmation / error / success dialogs | `useDialog()` system dialogs per `wecms-web/CLAUDE.md` §Dialogs & modals. |
| HTTP transport | [`api` Axios instance](../../../../wecms-web/lib/axios.ts) with JWT interceptor. |
| Data fetch hook | [`useApiData`](../../../../wecms-web/lib/api/useApiData.ts). |
| Wire types | new file [lib/api/purposeSettings.ts](../../../../wecms-web/lib/api/purposeSettings.ts) — wire types + API client wrappers (`fetchPurposeSettings()`, `fetchPurposeSetting()`, `createPurposeSetting()`, `updatePurposeSetting()`, `deletePurposeSetting()`, `reorderPurposeSettings()`, `previewPurposeExcel()`, `importPurposeExcel()`, `downloadPurposeTemplate()`). |
| Mappers | new entries in [lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts): `mapPurposeSettingRow()`. |
| i18n keys | [locales/en.json](../../../../wecms-web/locales/en.json), [ko.json](../../../../wecms-web/locales/ko.json), [vi.json](../../../../wecms-web/locales/vi.json) — namespace `settings.purpose_settings.*` (panel chrome) + `audit.purpose_settings.*` (audit messages). |
| Settings nav entry | [`<SettingsNav>`](../../../../wecms-web/components/settings/SettingsNav.tsx) — already wired (the panel is the approved modal baseline). |
| Typography | global tokens (`text-page-title` / `text-section-title` / `text-body` / etc.) from [globals.css](../../../../wecms-web/app/globals.css) `@theme inline`. |
| View Setting popover | **NOT adopted** (OQ-FE-1 in feature spec). |

---

## 5. New vs. modified files

### Backend (paths only — no code in Phase 1)

**New files** (Phase 2):

```
wecms-api/src/main/java/com/kosign/wecms/domain/setting/purposesettings/
├── controller/PurposeSettingsController.java
├── service/PurposeSettingsService.java          (CRUD + reorder)
├── service/PurposeExcelService.java             (Excel preview + import — split out in Phase 3 to mirror the VAS Account precedent)
├── repository/PurposeSettingsRepository.java
├── repository/PurposeInUseRepository.java        (@Immutable view repo for `v_purpose_in_use`)
├── entity/BusiUsagSet.java                       (@Table("BUSI_USAG_SET_INFM"), @IdClass)
├── entity/BusiUsagSetId.java                     (Serializable composite key)
├── entity/PurposeInUseRow.java                   (@Immutable view projection)
└── dto/
    ├── PurposeSettingRow.java                    (list row record)
    ├── PurposeSettingDetail.java                 (single-row record)
    ├── PurposeSettingListResponse.java           ({ list })
    ├── PurposeSettingCreateRequest.java          (record + validation)
    ├── PurposeSettingUpdateRequest.java          (record + validation)
    ├── PurposeReorderRequest.java                (record + nested PurposeReorderItem)
    ├── PurposeReorderItem.java                   (record)
    ├── PurposeExcelFieldError.java               (per-cell validation error)
    ├── PurposeExcelPreviewRow.java               (one parsed Excel row + errors)
    ├── PurposeExcelPreview.java                  (preview response — rows + counts)
    ├── PurposeExcelImportRequest.java            (commit payload)
    └── PurposeExcelImportResult.java             (inserted_count)
```

**New Flyway migrations** (Phase 2 — next free slot is **V80**):

```
wecms-api/src/main/resources/db/migration/
├── V35__busi_usag_set_infm.sql            (CREATE TABLE matching DEV; columns + PK + nullability)
├── V81__busi_usag_set_infm_purpose_in_use_view.sql  (CREATE OR REPLACE VIEW v_purpose_in_use) *(historical — REPLACED BY CODE; `v_purpose_in_use` is a Hibernate `@Subselect`, no view migration exists)*
└── V82__busi_usag_set_infm_demo_seed.sql         (Phase 3 — 5-10 level-1 + level-2 rows for VN_VNM0004_000001) *(historical — DROPPED; demo seeds were removed in the 2026-06-09 rebaseline)*
```

V80 numbering follows the rolling-slot rule from [wecms-api/CLAUDE.md §Data Migration Rule](../../../CLAUDE.md): the next free slot at planning time. Phase 2 owner re-checks the slot at write time and bumps if another feature has claimed it in the meantime.

**Modified files** (Phase 2):

```
wecms-api/src/main/resources/
├── messages.properties                            (verify legacy WBW1152/WBW1138/WBE1199 carry EN strings — port from JEX dictionary if missing per OQ-8; no new WBE_PURPOSE_* keys)
├── messages_en.properties                         (+ new keys, EN copy)
├── messages_ko.properties (optional, Phase 3)
├── messages_vi.properties (optional, Phase 3)
└── messages_km.properties (optional, Phase 3)
```

No changes to `SecurityConfig` (the `/api/v1/settings/**` matcher already requires JWT). No changes to `JacksonConfig`. No changes to `application.yml`.

### Frontend (paths only — no code in Phase 1)

**New files** (Phase 3):

```
wecms-web/lib/api/purposeSettings.ts          (wire types + Axios wrappers)
```

**Modified files** (Phase 3):

```
wecms-web/components/settings/purpose/PurposePanel.tsx        (replace mockPurposeTree with useApiData)
wecms-web/components/settings/purpose/PurposeFormRow.tsx     (Save → POST/PUT, hook in dialog system)
wecms-web/components/settings/purpose/RowActions.tsx         (Delete → confirmation → DELETE; Edit → set inline state — already wired)
wecms-web/components/settings/purpose/PurposeTable.tsx       (toggle handlers fire PUT)
wecms-web/components/settings/purpose/ExcelUploadDialog.tsx  (Browse → POST .../excel/preview; Upload → POST .../excel/import)
wecms-web/components/settings/purpose/mockData.ts             (KEEP — used as offline-dev fallback; mirror live mapper output)
wecms-web/lib/api/mappers.ts                                  (+ mapPurposeSettingRow)
wecms-web/locales/en.json                                     (+ settings.purpose_settings.*, audit.purpose_settings.*)
wecms-web/locales/ko.json                                     (+ same keys, KO copy — verified only)
wecms-web/locales/vi.json                                     (+ same keys, VI copy — verified only)
```

No new routes (panel mounts inside `<SettingsModal>`). No changes to `<SettingsNav>` (entry already exists). No changes to `<AppShell>`, `<Header>`, `<Sidebar>`. No changes to `viewSettingScreens.ts` (Purpose does not adopt View Setting per OQ-FE-1).

### Documentation (already created in Phase 1)

```
docs/refactor-wecms/features/settings/purpose-settings.md
docs/refactor-wecms/migration/purpose-settings.md            (this file)
docs/refactor-wecms/backend/purpose-settings.md
docs/refactor-wecms/frontend/purpose-settings.md
docs/refactor-wecms/database/purpose-settings.md
docs/refactor-wecms/archive/planning/settings/purpose-settings-task-plan.md
```

---

## 6. Migration-traceability prose

This section is the only place outside JEX file:line citations where legacy WSVC ids may appear verbatim.

The refactor `purpose-settings` feature replaces seven JEX WSVC ids — `comm_0187_001` (the View), `comm_0187_001_r001` (list), `comm_0187_001_r002` (name search), `comm_0187_001_c001` (create), `comm_0187_001_u002` (edit + soft-delete), `comm_0187_001_u004` (reorder), and `comm_0187_004_c001` (Excel upload). It does NOT replace `comm_0187_001_u003` (reset to system defaults), `comm_0187_003_r001` / `comm_0187_003_u001` (Unused / restore), or `comm_0187_002` (Standardization settings) — those remain JEX-only and are reachable only through the live `dev.wecms.vn` / `test.wecms.vn` / `www.wecms.vn` systems, never through the refactor stack. The refactor scopes data to `USAG_GRP_CD IS NULL` (the level-2 hierarchy bucket, per the legacy `_r001_act.jsp` comment) and drops the legacy `LANG_DETL` lookups in favour of the single-table model already documented in `comm_0187_001_c001_act.jsp:86-88`.

The audit DB constant `AUDIT_LOGS_INFM.WSVC_ID` shifts from `'comm_0187_001'` (under the legacy JEX convention) to `'purpose-settings'` per the 2026-05-09 cutover. JEX queries do not run against the refactor's local DB, so the cross-system audit-history motivation does not apply (matches the firm-banking-settings precedent).

---

## 7. References

- [docs/refactor-wecms/features/settings/purpose-settings.md](../../features/settings/purpose.md) — functional spec (this mapping's source of truth).
- [docs/refactor-wecms/migration/firm-banking-settings-mapping.md](firm-banking-settings-mapping.md) — closest analog (CRUD on a tenant master table; same package layout).
- [docs/refactor-wecms/migration/erp-transaction-history-mapping.md](erp-transaction-history-mapping.md) — Settings panel + audit-log integration precedent.
- [wecms-api/CLAUDE.md](../../../CLAUDE.md) — package conventions, identifier rules, Flyway slot rule.
- [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) — Settings shell rules, panel composition.
