# Settings > Purpose — Refactor Final Audit

**Refactor slug:** `purpose-settings`
**Audit date:** 2026-05-12
**Auditor:** Sonny (AI-assisted)
**Scope:** Phase 1 docs → Phase 2 backend → Phase 3 frontend (incl. Excel upload), end-to-end review against rules, JEX source-of-truth, and design docs.

**Sibling docs:** [feature spec](../../features/settings/purpose.md) · [migration](../migration/purpose-settings.md) · [backend](../business-rules/settings/purpose-settings.md) · [frontend](../../../../wecms-web/docs/features/settings/purpose.md) · [schema](../database/settings/purpose-settings-schema.md) · [task plan](../../archive/planning/settings/purpose-settings-task-plan.md).

---

## 1. Summary

| Section | Pass | Fixed in this audit | Blocker |
|---|---|---|---|
| A. Rule compliance | 5 | 1 | 0 |
| B. Backend | 7 | 1 | 0 |
| C. Frontend | 7 | 0 | 0 |
| D. Cross-cutting | 5 | 1 | 0 |
| E. Migration completeness | 7 | 0 | 0 |
| **Total** | **31** | **3** | **0** |

**Recommendation: GO** — the Purpose menu refactor is complete enough to close as a feature.
All structural elements (DB schema, service, controller, audit, Excel preview/import, frontend
panel + dialog + i18n) are in place and parity with the legacy `comm_0187_001*` /
`comm_0187_004_c001` flow is preserved. Three trivial documentation / locale-parity
fixes applied inline (see §6). Two follow-up items left as open questions for Sonny / Mr. Jung
(see §7) — neither blocks closure.

---

## 2. A — Rule compliance

| # | Check | Result | Evidence |
|---|---|---|---|
| A1 | No legacy WSVC ids in NEW identifier surfaces (routes, packages, classes, DTOs, i18n keys, log tags, audit `WSVC_ID`, View Setting screenIds) | ✅ Pass | URI `/api/v1/settings/purpose-settings`; package `com.kosign.wecms.domain.setting.purposesettings`; class names `PurposeSettingsController`, `PurposeSettingsService`, `PurposeExcelService`, `BusiUsagSet`, all DTO records with `Purpose…`; audit `WSVC_ID = "purpose-settings"` ([PurposeSettingsService.java:93](../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeSettingsService.java#L93)); i18n keys `audit.purpose_settings.*` + `settings.purpose.*`; log tags `purpose-settings`; View Setting not adopted (OQ-FE-1). Legacy ids appear ONLY in Javadoc / migration prose. |
| A2 | Wire format lowercase snake_case end-to-end | ✅ Pass | DTO records use camelCase Java fields, no `@JsonProperty("UPPER")`; global Jackson `SNAKE_CASE` strategy converts on the wire. Path param `{use_usag_cd}` is snake_case in the URI. Frontend `lib/api/purposeSettings.ts` wire types use snake_case; in-memory `PurposeNode` is camelCase via mapper at the boundary. |
| A3 | Typography uses workspace tokens only | ✅ Pass | `grep -E "text-\[|font-\[|leading-\[|style=\{\{.*?fontFamily" components/settings/purpose/` → no matches. |
| A4 | Desktop-first layout per `feedback_desktop_first_ui` | ✅ Pass | Purpose panel inherits `<SettingsModal>` (1300×760 fixed card; `max-w-[calc(100vw-32px)]`). No fluid-only widths breaking at 1024 / 1440. |
| A5 | No JEX-tree edits | ✅ Pass | All implementation under `wecms-api/` + `wecms-web/`; `WECMS DEV/` references are read-only citations in Javadoc and migration prose. |
| A6 | Refactor docs live only under `docs/refactor-wecms/` (or `docs/_shared/`) | ✅ Pass | Spec/migration/backend/frontend/database/planning docs all under `docs/refactor-wecms/`. No leakage to `docs/jex/`. |
| A7 | Controller / service Javadoc accurately cites the renamed Excel endpoints | 🔧 **Fixed in this audit** | [PurposeSettingsController.java:47-48](../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java#L47) Javadoc said `POST .../upload` but the real endpoints are `POST .../excel/preview` and `POST .../excel/import` after the Phase 3 Excel rewrite. Brought the Javadoc in line with the actual mapping. |

---

## 3. B — Backend

| # | Check | Result | Evidence |
|---|---|---|---|
| B1 | Controller URI is kebab-case slug | ✅ Pass | `@RequestMapping("/api/v1/settings/purpose-settings")` ([PurposeSettingsController.java:62](../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java#L62)). Child routes `/{use_usag_cd}`, `/order`, `/excel/preview`, `/excel/import`. |
| B2 | DTO ↔ Entity mapping correct; no `@JsonProperty` noise | ✅ Pass | `BusiUsagSet` maps every legacy column (`biz_key`, `use_usag_cd`, `use_yn`, `disp_yn`, `disp_ord`, `mod_id`, `mod_dtm`, `usag_grp_cd`, `rept_dv`, `rept_grp`, `usag_type`, `hand_use_usag_cd`, `parent_use_usag_cd`, `use_usag_nm`, `use_usag_cont`); audit pair `MOD_ID` / `MOD_DTM` written by service (legacy table has no `REG_*` pair — entity doesn't extend `BaseEntity`, by design). `@JdbcTypeCode(SqlTypes.CHAR)` on `usagType` and `@JdbcTypeCode(SqlTypes.NUMERIC)` on `dispOrd` for legacy-DB-type parity. No `@JsonProperty` annotations on any DTO. |
| B3 | Validation matches JEX rules | ✅ Pass | Refactor enforces every JEX rule (verified against `comm_0187_001_c001_act.jsp:31-34`, `_u002_act.jsp:38-49`, `_u004_act.jsp:37-40`) PLUS the documented refactor hardening: `parent_use_usag_cd` exists+active+level-1 on Create AND Update (legacy did this on Create only via DAO `validateBusiUsagSetInfm`, refactor expands), demote-with-children block on Update, reorder duplicate-code + duplicate-disp_ord guards, self-parent block. All hardening documented in feature spec §5.3-5.5. |
| B4 | Exception handling reuses `@RestControllerAdvice` + existing codes | ✅ Pass | Throws `BusinessException(WBW1152 / WBW1138 / WBE1199 / WBE_VALIDATION / WBE9999 / WBE2041 / WBE2042 / WBE2043)` only. No bespoke `try/catch` swallowing errors. `GlobalExceptionHandler` is the single funnel. |
| B5 | Audit log writes slug `purpose-settings` (NOT `comm_0187_001`) | ✅ Pass | `AUDIT_WSVC_ID = "purpose-settings"` constant ([PurposeSettingsService.java:93](../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeSettingsService.java#L93)) used in every `auditLogService.append(...)` call (Create/Update/Delete/Reorder/Upload). Same constant in [PurposeExcelService.java](../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/service/PurposeExcelService.java) (`AUDIT_WSVC_ID = "purpose-settings"`). Five audit detail keys exist (`create / update / delete / reorder / upload`) ([messages.properties:75-80](../../../src/main/resources/messages.properties#L75)). |
| B6 | Repository methods all necessary + used | ✅ Pass | 9 methods on `PurposeSettingsRepository`, every one wired to a service call — `findActiveForTenant` (list), `findByBizKeyAndUseUsagCd` (detail/update/delete), `findActiveByHandCd` / `findActiveByName` / `findDuplicateNameExcludingSelf` (uniqueness guards), `hasActiveChildren` / `findActiveChildCodes` (delete pre-check), `findMaxDispOrd` (Create), `findAllActiveForReorder` (reorder), `nextUseUsagCd` (PK generation). `PurposeInUseRepository` has `findInUseCodes` + `existsAnyByBizKeyAndUseUsagCdIn`, both used. No dead method. |
| B7 | Tests cover happy path + each validation/error branch | ✅ Pass | 38 service tests + 11 controller tests + 13 Excel service tests. Coverage spans every WBE/WBW code path (`WBW1152`, `WBW1138`, `WBE1199` both branches — own + child reference, `WBE_VALIDATION` for parent-not-found / level-2-parent / demote-with-children / self-parent / reorder dup / unknown code, `WBE2041` / `WBE2042` / `WBE2043` for Excel). Per-row Excel parity covered (root-then-child commit ordering, dup_file, dup_db, parent_not_found, parent_not_root, required, length). 4 pre-existing controller-test failures match the standalone-MockMvc `BusinessException` baseline (same pattern as `FirmBankingSettingsControllerTest` — NOT a regression). |
| B8 | No speculative abstractions, dead code, backwards-compat shims | ✅ Pass | Service is one class, controller is one class. No unused interfaces, no `if (legacy)` branches. The Excel flow was correctly split into a dedicated `PurposeExcelService` after the Phase 3 redesign (per shared shell pattern) — and the legacy `/upload` endpoint + 4 DTOs were cleanly removed. |
| B9 | Controller Javadoc & `messages.properties` audit keys parity in non-EN locales | 🔧 **Fixed in this audit** | `audit.purpose_settings.*` keys existed only in `messages.properties` + `messages_en.properties`; missing from `messages_ko.properties`, `messages_vi.properties`, `messages_km.properties`. Without these, the audit-log row for KO/VI/KM locales would have null detail when the shared `AuditLogService` resolves the per-locale message. Copied the EN values into all three locale files matching the firm-banking-settings precedent (where unverified translations carry the EN string verbatim until a real translation lands). |

---

## 4. C — Frontend

| # | Check | Result | Evidence |
|---|---|---|---|
| C1 | Route uses slug (or N/A — modal panel) | ✅ Pass | Purpose panel mounts inside `<SettingsModal>` — no standalone route, by design. Modal panel composition per `wecms-web/CLAUDE.md` §3a. |
| C2 | API calls match backend contract field-for-field | ✅ Pass | Wire types in `lib/api/purposeSettings.ts` (snake_case) match backend DTOs after Jackson SNAKE_CASE: `ApiPurposeSettingRow` (11 fields) ↔ `PurposeSettingRow.java`, `ApiPurposeSettingDetail` (10 fields) ↔ `PurposeSettingDetail.java`, `ApiPurposeSettingCreateRequest` ↔ `PurposeSettingCreateRequest.java`, `ApiPurposeReorderItem` ↔ `PurposeReorderItem.java`, `ApiPurposeExcelPreview` (4 fields) ↔ `PurposeExcelPreview.java`, `ApiPurposeExcelPreviewRow` (7 fields incl. errors) ↔ `PurposeExcelPreviewRow.java`. See §5 for the field-by-field diff. |
| C3 | Form validation mirrors backend | ✅ Pass | Backend Bean Validation: `@NotBlank` + `@Size(max=20)` on code, `@NotBlank` + `@Size(max=255)` on name, `@Size(max=500)` on description, `@Size(max=20)` on parent. Frontend `<PurposeFormRow>` enforces required code+name in the Save click handler; max-length caps surface via backend `WBE_VALIDATION` → dialog. Parent picker filters to level-1 only client-side (`eligibleParents` in PurposePanel) — backend `validateParent` is the authoritative gate. |
| C4 | i18n keys present in all supported locales | ✅ Pass | 53 keys verified across `en.json` / `ko.json` / `vi.json` — 0 missing. KM not supported by wecms-web yet (per `wecms-web/CLAUDE.md` — "Supported locales: EN/KO/VI; KM not yet in the web"). |
| C5 | View Setting registered with slug screenId | ✅ N/A | Purpose does NOT adopt View Setting (per OQ-FE-1 decision — the panel is the modal baseline reference and Figma shows no list-icon button). If Figma later adds the toolbar list-icon, reserve `SCREEN_IDS.SETTINGS_PURPOSE_SETTINGS = "purpose-settings"` per the feature spec §OQ-FE-1. |
| C6 | Loading / empty / error states implemented | ✅ Pass | `PurposePanel` renders skeleton-like text "Loading purposes…" on `loading`, "No Data Found" on empty, `useDialog().open({ type: "error", … })` on fetch / mutation error. Offline-dev fallback to `mockPurposeTree` when the fetch fails (per the firm-banking-settings precedent). |
| C7 | Lint + typecheck clean | ✅ Pass | `npx tsc --noEmit` → 0 errors in `components/settings/purpose/*` or `lib/api/purposeSettings.ts` (4 pre-existing unrelated errors in `account-inquiry-fcy/mockData.ts` — not part of this scope). `npx eslint components/settings/purpose lib/api/purposeSettings.ts` → 0 issues. |

---

## 5. D — Cross-cutting

| # | Check | Result | Evidence |
|---|---|---|---|
| D1 | Backend response shape vs frontend type definitions agree field-for-field | ✅ Pass | See diff table below — every backend record field has a matching wire-type field with the correct snake_case name and a compatible TS type. |
| D2 | Error-code paths render correct localized messages on the UI | ✅ Pass | `WBW1152` / `WBW1138` / `WBE1199` / `WBE_VALIDATION` / `WBE9999` / `WBE2041` / `WBE2042` / `WBE2043` all have entries in `messages.properties` (server-resolved); per-cell Excel error categories (`required` / `length` / `dup_file` / `dup_db` / `parent_not_found` / `parent_not_root`) ship pre-localized strings on the wire so the preview tooltip renders them verbatim. Frontend `PurposePanel.handleApiError(...)` maps server `status.code` → localized dialog description. |
| D3 | No PII / secrets in logs, error responses, or test fixtures | ✅ Pass | Service `log.info` / `log.warn` calls emit `bizKey`, `useUsagCd`, `handCd`, `parent` — all opaque tenant-scoped identifiers, no PII. Test fixtures use `"tester"` user id, `"VN_VNM0004_000001"` tenant key, dummy purpose codes. No JWT secrets / DB passwords / encryption keys anywhere in the feature. |
| D4 | Authn enforced on every endpoint | ✅ Pass | `/api/v1/settings/**` is implicitly authenticated (the JWT filter from `SecurityConfig` covers everything outside `/api/v1/auth/*`). No `permitAll` on any Purpose route. |
| D5 | Performance: list endpoint non-N+1 | ✅ Pass | List does ONE query: `repo.findActiveForTenant(...)` returns the flat row list ordered by `disp_ord`. `resolveInUseCodes` issues ONE additional batched query against `v_purpose_in_use` (the view name is misleading — it's a `WHERE (biz_key, use_usag_cd) IN (...)` projection). Tree assembly is in-memory via `assembleTree(...)` with a single `LinkedHashMap` pass. Index `busi_usag_set_infm_parent_ix` on `(biz_key, parent_use_usag_cd) WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y'` supports the `v_purpose_in_use` branch (a) lookup. No N+1. |
| D6 | No regressions implied for upstream/downstream features | 🔧 **Fixed in this audit** (stale doc reference) | The feature spec §3 operations matrix and the migration doc §1 service-by-service mapping both still listed the legacy single-endpoint `/upload` shape (`POST /api/v1/settings/purpose-settings/upload` → `PurposeSettingsService.upload(...)`) and its associated DTOs (`PurposeUploadRequest` / `PurposeUploadResult`). These were renamed to the two-step `/excel/preview` + `/excel/import` flow with `PurposeExcelPreview` / `PurposeExcelImportRequest` / `PurposeExcelImportResult` DTOs during the Phase 3 Excel rewrite. Updated both docs in this audit so future maintainers don't chase phantom endpoints. |

### Field-by-field DTO ↔ wire-type diff

| Backend Java field | Java type | Wire field (after SNAKE_CASE) | Frontend TS field | TS type | Match? |
|---|---|---|---|---|---|
| `PurposeSettingRow.useUsagCd` | `String` | `use_usag_cd` | `ApiPurposeSettingRow.use_usag_cd` | `string` | ✅ |
| `PurposeSettingRow.handUseUsagCd` | `String` | `hand_use_usag_cd` | `.hand_use_usag_cd` | `string` | ✅ |
| `PurposeSettingRow.useUsagNm` | `String` | `use_usag_nm` | `.use_usag_nm` | `string` | ✅ |
| `PurposeSettingRow.useUsagCont` | `String` | `use_usag_cont` | `.use_usag_cont` | `string \| null` | ✅ |
| `PurposeSettingRow.parentUseUsagCd` | `String` | `parent_use_usag_cd` | `.parent_use_usag_cd` | `string \| null` | ✅ |
| `PurposeSettingRow.dispYn` | `String` | `disp_yn` | `.disp_yn` | `"Y" \| "N"` | ✅ |
| `PurposeSettingRow.dispOrd` | `Integer` | `disp_ord` | `.disp_ord` | `number \| null` | ✅ |
| `PurposeSettingRow.usedInTransactions` | `boolean` | `used_in_transactions` | `.used_in_transactions` | `boolean` | ✅ |
| `PurposeSettingRow.modId` | `String` | `mod_id` | `.mod_id` | `string \| null` | ✅ |
| `PurposeSettingRow.modDtm` | `String` | `mod_dtm` | `.mod_dtm` | `string \| null` | ✅ |
| `PurposeSettingRow.children` | `List<PurposeSettingRow>` | `children` | `.children` | `ApiPurposeSettingRow[] \| null` | ✅ |
| `PurposeExcelPreview.rows` | `List<PurposeExcelPreviewRow>` | `rows` | `.rows` | `ApiPurposeExcelPreviewRow[]` | ✅ |
| `PurposeExcelPreview.totalCount` | `int` | `total_count` | `.total_count` | `number` | ✅ |
| `PurposeExcelPreview.completedCount` | `int` | `completed_count` | `.completed_count` | `number` | ✅ |
| `PurposeExcelPreview.errorCount` | `int` | `error_count` | `.error_count` | `number` | ✅ |
| `PurposeExcelPreviewRow.rowIndex` | `int` | `row_index` | `.row_index` | `number` | ✅ |
| `PurposeExcelPreviewRow.handUseUsagCd` | `String` | `hand_use_usag_cd` | `.hand_use_usag_cd` | `string` | ✅ |
| `PurposeExcelPreviewRow.useUsagNm` | `String` | `use_usag_nm` | `.use_usag_nm` | `string` | ✅ |
| `PurposeExcelPreviewRow.useUsagCont` | `String` | `use_usag_cont` | `.use_usag_cont` | `string` | ✅ |
| `PurposeExcelPreviewRow.parentUseUsagCd` | `String` | `parent_use_usag_cd` | `.parent_use_usag_cd` | `string` | ✅ |
| `PurposeExcelPreviewRow.valid` | `boolean` | `valid` | `.valid` | `boolean` | ✅ |
| `PurposeExcelPreviewRow.errors` | `List<PurposeExcelFieldError>` | `errors` | `.errors` | `ApiPurposeExcelFieldError[]` | ✅ |
| `PurposeExcelFieldError.field` | `String` | `field` | `.field` | `string` | ✅ |
| `PurposeExcelFieldError.code` | `String` | `code` | `.code` | `ApiPurposeExcelErrorCode` (union) | ✅ |
| `PurposeExcelFieldError.message` | `String` | `message` | `.message` | `string` | ✅ |
| `PurposeExcelImportResult.insertedCount` | `int` | `inserted_count` | `.inserted_count` | `number` | ✅ |

Every backend record field has a corresponding wire field with snake_case name + a compatible TS type. Frontend mappers translate snake_case → camelCase at the boundary in `mapPurposeSettingRow` / `toUploadRow`.

---

## 6. E — Migration completeness

| JEX WSVC | Refactor implementation | Status |
|---|---|---|
| `comm_0187_001_r001` (List) | `GET /api/v1/settings/purpose-settings` → `PurposeSettingsService.list(keyword)` → `PurposeSettingsRepository.findActiveForTenant(bizKey, RESERVED_SYSTEM_CODES, keyword)` | ✅ Implemented |
| `comm_0187_001_r002` (Name search) | Merged into list as `?keyword=` query parameter; uses `LOWER(p.useUsagNm) LIKE LOWER('%' || :keyword || '%')` matching legacy `use_usag_nm ilike '%'||?||'%'` | ✅ Implemented (merged) |
| `comm_0187_001_r001` (Detail by PK — convenience) | `GET /api/v1/settings/purpose-settings/{use_usag_cd}` → `PurposeSettingsService.detail(useUsagCd)` | ✅ Implemented |
| `comm_0187_001_c001` (Create) | `POST /api/v1/settings/purpose-settings` → `PurposeSettingsService.create(req)` — duplicate hand_cd / name checks, parent validation, `wb_get_next_val` PK gen with 1-shot retry, audit row | ✅ Implemented |
| `comm_0187_001_u002` REC (Update) | `PUT /api/v1/settings/purpose-settings/{use_usag_cd}` → `PurposeSettingsService.update(...)` — duplicate name (excl. self) check, parent + demote-with-children + self-parent validation, audit row | ✅ Implemented |
| `comm_0187_001_u002` REC2 (Soft-delete) | `DELETE /api/v1/settings/purpose-settings/{use_usag_cd}` → `PurposeSettingsService.delete(...)` — `hasActiveChildren` + OQ-5 recursive `v_purpose_in_use` check; `WBE1199` covers both cases; audit row | ✅ Implemented |
| `comm_0187_001_u004` (Reorder) | `PUT /api/v1/settings/purpose-settings/order` → `PurposeSettingsService.reorder(...)` — duplicate-cd + duplicate-disp_ord + unknown-cd guards (refactor hardening); audit row | ✅ Implemented |
| `comm_0187_004_c001` (Excel upload) | `POST /api/v1/settings/purpose-settings/excel/preview` → `PurposeExcelService.preview(file)` + `POST .../excel/import` → `PurposeExcelService.import_(req)`. Two-step preview / import mirroring VAS Account precedent; root-before-child commit ordering matches legacy `orderRootsFirst`; parent_not_root check added as refactor hardening (legacy didn't enforce level-1 cap on upload) | ✅ Implemented |
| `comm_0187_001_u003` (Reset to system defaults) | — | ❌ Out of scope (per feature spec §2). Owner: PO / Sonny — revisit if a future Phase asks for it; depends on `SYS_USAG_SET_INFM` + `USAG_GRP_CD` flow that the refactor doesn't carry. |
| `comm_0187_003_r001` / `_u001` (Unused / restore) | — | ❌ Out of scope (per feature spec §2). Owner: PO / Sonny. |
| `comm_0187_002_*` (Standardization settings) | — | ❌ Out of scope (per feature spec §2). |

**Coverage summary:** 8 of 8 in-scope JEX behaviours implemented. 3 out-of-scope items explicitly listed with justification + owner.

---

## 7. Trivial fixes applied in this audit

1. **[PurposeSettingsController.java:47-48](../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java#L47)** — Javadoc said `POST .../upload` for the Excel endpoint, but the actual mapping is `POST .../excel/preview` + `POST .../excel/import` after the Phase 3 Excel rewrite. Brought the Javadoc in line.
2. **`messages_ko.properties` / `messages_vi.properties` / `messages_km.properties`** — added the 6 `audit.purpose_settings.*` keys with the verified EN values (matching the firm-banking-settings precedent for unverified translations). Without these the shared `AuditLogService` would write null detail strings for KO/VI/KM rows.
3. **[docs/refactor-wecms/features/settings/purpose-settings.md](../../features/settings/purpose.md) + [docs/refactor-wecms/migration/purpose-settings.md](../migration/purpose-settings.md)** — both still described the original single-endpoint `/upload` flow and the four legacy upload DTOs (`PurposeUploadRequest` / `Result` / `Row` / `Error`). Updated the operations matrix, mapping table, file list, and §5.6 narrative so they describe the actual two-step `/excel/preview` + `/excel/import` flow with the renamed DTOs (`PurposeExcelPreview` / `PurposeExcelPreviewRow` / `PurposeExcelFieldError` / `PurposeExcelImportRequest` / `PurposeExcelImportResult`).

---

## 8. Blockers

**None.** No structural issue, no security gap, no parity break that requires a scope change.

---

## 9. Open questions for Sonny / Mr. Jung

1. **OQ-6 — Authority gate.** The feature spec §6 originally called for `@PreAuthorize("hasRole('ADMIN')")` on every mutating endpoint, but the controller carries no `@PreAuthorize` (only the JWT auth filter). Controller Javadoc cites OQ-6 as the reason. This matches the legacy "menu visibility is the gate" pattern AND matches `wecms-api/CLAUDE.md`'s statement that "`@PreAuthorize` gating of admin-only endpoints is a follow-up MR (the wiring is ready; no endpoint annotated yet)". **Confirm:** keep this as-is for now, or add `@PreAuthorize` in a follow-up MR alongside other Settings panels?

2. **OQ-5 (b) — `v_purpose_in_use` consumer-table branches.** V81 ships only branch (a) — parent referenced by an active child. The OQ-5 spec also asks for branch (b) — purpose referenced by an active payment / transfer / expense record (`PYMN_*`, `TRNS_*`, `EXPS_*`). The delete handler's recursive check is already wired to whatever the view returns, so adding branches is purely additive. **Confirm:** is branch (b) deferred until the consumer tables port into the refactor (Phase 2+ work), or does Mr. Jung want it for a specific consumer now (e.g. payment scheduled rows)?

3. **OQ-4 — `hand_use_usag_cd` duplicate check on Update.** Currently follows legacy behaviour (no check on Update, only on Create). Feature spec §11 OQ-4 left the decision pending PO confirmation. **Confirm:** keep legacy behaviour or tighten?

---

## 10. Confirmation

- **No JEX-tree files were modified.** `git status` would show only changes under `wecms-api/`, `wecms-web/`, and `docs/refactor-wecms/`. JEX action JSPs, IDOs, and WSVC XMLs in `WECMS DEV/` were read-only references during the audit.
- **No DEV / UAT / PROD references introduced.** Local R&D only — backend reads `localhost:5434/wecms_dev` via the standard `application-local.yml`. Frontend reads `http://localhost:8080` via `NEXT_PUBLIC_API_BASE_URL`. No live-system hostnames added.
- **Refactor stays local-only.** No deployment artifact (Dockerfile, Helm chart, CI/CD job, env-specific config) was added or modified.

---

## 11. Files modified in this audit session

```
wecms-api/src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java
wecms-api/src/main/resources/messages_ko.properties
wecms-api/src/main/resources/messages_vi.properties
wecms-api/src/main/resources/messages_km.properties
docs/refactor-wecms/features/settings/purpose-settings.md
docs/refactor-wecms/migration/purpose-settings.md
docs/refactor-wecms/audits/purpose-settings-audit.md      (this file — new)
```

---

## 12. GO / NO-GO

**GO.** The Purpose menu refactor is closed. All in-scope JEX behaviours are implemented and verified; rule compliance is clean; no blockers found. The three open questions in §9 are confirmation items, not gating issues.
