# Common (Cross-Cutting) — Validated Source-Code Index

**Audit date:** 2026-06-23
**Scope:** the WeCMS refactor **Common / cross-cutting** surface — capabilities wired once and consumed product-wide (Banking / Payment / Add-on / Settings), plus shared infrastructure. Backend: `wecms-api/src/main/java/com/kosign/wecms/common/**` + `domain/{setting (screen-options),navigation,common,payment/notification}`. Frontend: `wecms-web/components/{shared,layout}/**` + `wecms-web/lib/**` + `wecms-web/hooks/**`.
**Rule:** code is the source of truth. References marked ✔ were opened and verified against the working tree on 2026-06-23; references marked ◦ were inventoried via fan-out exploration and should be re-opened before being cited in a per-module spec.
**Companions:** [common-docs-audit.md](common-docs-audit.md) · [common-gap-analysis.md](common-gap-analysis.md)

> **Module map.** "Common" is a **system feature group**, not a WeCMS top-menu. The canonical refactor Common module set (the standardization target) is **11 modules**:
> `view-setting` · `audit-log` · `excel-export` · `excel-import` · `file-management` · `notification` · `navigation` · `common-code-lookup` · `table-sort` · `dialog-popup` · `error-envelope`.
> Capabilities that the prompt grouped as "Common" but that are **owned by a domain** are documented in that domain and only *cross-linked* from Common: `approval` → Settings `approval-line` + Payment `approval-box`; `reporting-account` → Settings `chart-of-accounts`; `attachment`/contacts file upload → Settings `contacts`. `dashboard-common` has **no code** (DEAD — see gap analysis).

---

## 0. Backend `common/**` package inventory (verified ✔ via glob)

```
common/
├── auditlog/        AuditLogService, AuditLogContext, AuditLogsInfm(+Id), AuditLogEntryRepository
├── entity/          BaseEntity, LegacyDtmConverter
├── enums/           LangCd, YesOrNo, PrdtType, TranTp, InoutDv, AcctTypeCd,
│                    FirmBankingSvcType, VasAcctType, AStts
├── exception/       BusinessException, GlobalExceptionHandler, TraceId, TraceIdFilter
├── export/          ExcelExportService, ExportableScreenRegistry, ExcelWorkbookWriter,
│                    ExportRowProvider, ExportFetch, ExcelExportRequest, ExcelColumnSpec,
│                    ExcelColumnFormat, ExcelAlignment, ExcelSortSpec, FieldIdAlias
├── firmbanking/     FirmBankingInquiryService
├── i18n/            MessageResolver, RelativeTimeFormatter
├── legacy/          LegacyFunctionRepository  (the wb_get_next_val funnel)
├── response/        ApiResponse, CommonHead
├── security/        JwtTokenProvider, JwtAuthenticationFilter, JwtAuthenticationEntryPoint,
│                    WecmsPasswordEncoder, LegacyHashPasswordEncoder, LogMask
├── tenant/          TenantContext
├── tx/              TransactionalRetry
└── web/             SortDirection, SortableField, RequestResponseLoggingFilter
```

---

## 1. view-setting (Screen Options)

| Layer | Artifact | Path | Notes |
|---|---|---|---|
| Controller | `ScreenOptionController` ✔(CLAUDE-confirmed) | `domain/setting/controller/ScreenOptionController.java` | `@RequestMapping("/api/v1/screen-options")` — global, no `@menuAuthz` (allow-listed). |
| Service | `ScreenOptionService` | `domain/setting/service/ScreenOptionService.java` | merge sys+user, lazy-init, delete-then-insert save, reset-then-reinit; `SPECIAL_FIELD_IDS = {CHECKBOX, ETC}`; `isSpecialFieldId(...)`. |
| Entity | `SysScrOptionWecms`, `UserScrOptionWecms` | `domain/setting/entity/` | `@Table SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS`; `FIX_YN` removed 2026-06-11. |
| DTO | `ScreenOptionListResponse`, `ScreenOptionItem`, `ScreenAreaListResponse` | `domain/setting/dto/` | wire snake_case; values (`BANK`, `Y`) stay UPPER. |

**Endpoints** (CLAUDE-verified):

| Method | URI | Legacy |
|---|---|---|
| GET | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `comm_0108_001_r001_osmc` (lazy-init) |
| GET | `/api/v1/screen-options/{webSvcId}/areas` | `SYS_SCR_OPTION_WECMS_R002` |
| PUT | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `comm_0108_001_u002_osmc` |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `comm_0108_001_u001_osmc` |

**Error codes:** `WBE2001` (unknown screenId), `WBE_VALIDATION` (kebab pattern fail).
**Seeded screens:** `banking-account-inquiry-vnd`, `firm-banking-history`, `erp-transaction-history`, `contacts` (+ addon export-only screens).
**Frontend:** `components/shared/view-setting/{ViewSettingPopover.tsx, useViewSetting.ts, useViewSettingColumns.ts, index.ts}` ✔; `lib/api/screenOption.ts` ✔; `lib/viewSettingScreens.ts` (`SCREEN_IDS`, `DEFAULT_AREA_ID`, `SCREEN_ID_TO_ROUTE`, `isKnownScreenId`) ✔.
**Existing docs:** `wecms-api/docs/features/common/view-setting.md`, `wecms-web/docs/features/common/view-setting.md` (current; convert to folder in Phase 2). JEX: `docs/jex/features/view-setting.md`.

---

## 2. audit-log

| Layer | Artifact | Path | Notes |
|---|---|---|---|
| Service | `AuditLogService` ✔(glob) | `common/auditlog/service/AuditLogService.java` | `append(AuditLogContext)`; `@Transactional(REQUIRES_NEW)`; locale fan-out; never throws (failures logged WARN). |
| Context | `AuditLogContext` ✔ | `common/auditlog/service/AuditLogContext.java` | `wsvcId` (refactor **slug**, not legacy WSVC id), `refNo`/`refNo1`/`refNo2`, activity/detail i18n keys + args. |
| Entity | `AuditLogsInfm` (+`AuditLogsInfmId`) ✔ | `common/auditlog/entity/` | `@Table AUDIT_LOGS_INFM`; `WSVC_ID` stores the route slug (override 2026-05-09). |
| Repository | `AuditLogEntryRepository` ✔ | `common/auditlog/repository/` | save-only. Seq via `LegacyFunctionRepository.nextAuditLogsSeqNo`. |

**No standalone endpoint** — write-path only. Consumers: `FirmBankingSettingsService`, `ContactsService`, `ChartOfAccountsService`, `LoginCredentials*` (and any future CRUD). Audit-log *reads* surface per-feature (e.g. login-credentials `/audit-logs`), not a Common endpoint.
**Frontend (read view):** `components/shared/popups/AuditLogsDialog.tsx` ✔ — generic audit-history modal (`stackLevel=2`), host supplies pre-translated rows.
**Existing docs:** `wecms-api/docs/features/common/audit-log.md` (current; convert to folder).

---

## 3. excel-export

| Layer | Artifact | Path | Notes |
|---|---|---|---|
| Orchestrator | `ExcelExportService` ✔(glob) | `common/export/ExcelExportService.java` | resolve provider → intersect cols (allow-list ∩ View Setting visibility) → `MAX_ROWS=50_000` → stream XLSX; no outer `@Transactional`. |
| Registry | `ExportableScreenRegistry` ✔ | `common/export/` | auto-collects `@Component` providers; rejects dup screenIds; `WBE2010` unknown. |
| Writer | `ExcelWorkbookWriter` ✔ | `common/export/` | Apache POI **SXSSF** streaming; pooled cell styles. |
| Contracts | `ExcelExportRequest`, `ExcelColumnSpec`, `ExcelColumnFormat`, `ExcelAlignment`, `ExcelSortSpec`, `ExportFetch`, `ExportRowProvider`, `FieldIdAlias` ✔ | `common/export/` | wire body shared by every per-resource endpoint. |

**Registered providers** ◦ (verify list before citing): `AccountInquiryExportProvider` (`banking-account-inquiry-vnd`), `FirmBankingHistoryExportProvider` (`firm-banking-history`), `ErpTrnsHistoryExportProvider` (`erp-transaction-history`), `SalesEInvoiceExportProvider` (`addon-sales-einvoices`), `BillEInvoiceExportProvider` (`addon-expenses-einvoices`), `OperationStatusExportProvider` (`addon-operation-status`), plus VAS / others as they land.
**Endpoint pattern:** per-resource `POST /api/v1/{module}/{resource}/export` (never a generic dispatcher).
**Error codes:** `WBE2010` (unknown screenId), `WBE2011` (>50k rows), `WBE2012` (export failed), `WBE2013` (no exportable columns after intersection).
**Frontend:** `lib/excel.ts` (`exportExcel`) ✔; `hooks/useExcelExport.ts` ✔; `components/shared/PageActionButtons.tsx` (`showExcel`/`onExport`/`exporting`) ✔. **CSV path retired** — XLSX only.

---

## 4. excel-import

| Layer | Artifact | Path | Notes |
|---|---|---|---|
| Backend | per-feature import services ◦ | `domain/setting/purposesettings/`, `domain/setting/vasaccount/` | `POST .../preview` + `POST .../import` (preview→validate→execute). No single shared backend service — pattern repeated per feature. |
| Error codes ◦ | `WBE2040`–`WBE2043` | — | row-limit / invalid-header / not-readable family (verify exact mapping). |
| Frontend (shared shell) | `ExcelUploadDialog` ✔ | `components/shared/excel-upload/ExcelUploadDialog.tsx` | Download→Browse→Preview→Confirm; per-cell error highlight; columns/widths supplied per-feature. |
| Frontend | `templateValidator.ts` ✔ | `components/shared/excel-upload/` | `TemplateSpec` + predefined specs (PURPOSE, CHART_OF_ACCOUNTS, CONTACTS_LOCAL/OVERSEAS, VAS_ACCOUNT). |
| Frontend | `ExcelUploadButton.tsx` ✔, `lib/xlsxReader.ts` ◦ | `components/shared/` / `lib/` | brand-green trigger chip; XLSX parse wrapper. |

**Note:** excel-import is a **shared FRONTEND shell + per-feature BACKEND services** — the Common doc covers the shared shell + the import contract pattern, and cross-links to the owning feature (Purpose, VAS Account, Chart of Accounts, Contacts) for the table-specific validation.

---

## 5. file-management (upload · download · attachment)

| Concern | Artifact | Path | Notes |
|---|---|---|---|
| Upload (backend) | `ContactsFileService` ◦ | `domain/setting/contacts/service/` | `MultipartFile` upload, size cap (10 MB), extension allow-list, UUID save-name (path-traversal guard), tenant-scoped storage `<root>/<biz_key>/contacts/`. File-id via `LegacyFunctionRepository.nextTrcoInfoFileFileId`. |
| Download (backend) | ERP: `ErpTrnsHistoryController` ◦; Contacts: `ContactsFileService.openForDownload` ◦; Export: `ExcelExportService` ✔ | `domain/setting/erptransactionhistory/`, `domain/setting/contacts/`, `common/export/` | streams binary from configured storage root; RFC 5987 filename. |
| Entity | `TrcoInfoFile` (+`TrcoInfoFileId`) ◦ | `domain/setting/contacts/entity/` | `@Table TRCO_INFO_FILE`. |
| Frontend | `lib/file.ts` (`downloadFile`) ✔, `components/shared/icons/DownloadIcon.tsx` ✔, `components/settings/contacts/AttachmentsSection.tsx` ◦ | — | JWT XHR → blob → anchor; Content-Disposition + JSON-error-envelope sniff → `ApiCallError`. |

**Error codes:** `WBE2014` (ERP row not found), `WBE2015` (ERP file unavailable), `WBE_CONTACTS_FILE_*` (empty / too-large / type / name / not-found / unavailable / storage-failed) ◦.
**Scope note:** generic upload/download is **Common pattern + storage policy**; the concrete table (`TRCO_INFO_FILE`) is owned by Settings `contacts`. There is no `/api/v1/attachments` generic endpoint.

---

## 6. notification — VERIFIED LIVE (re-implemented on BLTB)

> **Major doc correction.** The old refactor-only `domain/notification/` package + `NOTIFICATION` table (V238) were **removed** (2026-06-10). Notification is now **live again** under **`domain/payment/notification/`**, backed by legacy **`BLTB_*`** tables — exactly the "use legacy BLTB tables, not a refactor-only table" pattern. The existing Common READMEs still describe the removed version and link a dead spec — corrected this pass.

| Layer | Artifact | Path (verified ✔) | Notes |
|---|---|---|---|
| Controller | `NotificationController` ✔ | `domain/payment/notification/controller/NotificationController.java` | `@RequestMapping("/api/v1/notifications")`. Ports legacy **`comm_0192_007`** family. **BLTB branch only** — General `TYPE='G'` (`GENL_NOTI_*`) intentionally NOT ported (out of legacy-table-inventory). No `@menuAuthz` (global header surface, allow-listed in `EndpointAuthorizationCoverageTest`). |
| Service | `NotificationService` ✔, `ApprovalNotificationService` ✔ | `domain/payment/notification/service/` | `DEFAULT_PAGE_SIZE`/`MAX_PAGE_SIZE`; tenant+recipient from `TenantContext`. `ApprovalNotificationService` = producer leg (approval request/approve/reject). |
| Entities | `BltbNotiInfm`, `BltbNotiContInfm`, `BltbComntInfm`, `BltbPostInfm`, `BltbUserNewPostInfm` (+ `*Id`) ✔ | `domain/payment/notification/entity/` | `@Table BLTB_NOTI_INFM` etc. — all in legacy table inventory. |
| Repositories | `BltbNotiInfmRepository`, `BltbNotiContInfmRepository`, `BltbComntInfmRepository`, `BltbPostInfmRepository`, `BltbUserNewPostInfmRepository` ✔ | `domain/payment/notification/repository/` | — |
| DTO | `NotificationListResponse`, `NotificationRow`, `UnreadCountResponse`, `NotificationProjection` ✔ | `domain/payment/notification/dto/` | wire snake_case. |

**Endpoints** (verified ✔ from `NotificationController.java`):

| Method | URI | Handler | Legacy |
|---|---|---|---|
| GET | `/api/v1/notifications?read_type={U\|A}&page&size&search` | `list` | `comm_0192_007_r001` |
| GET | `/api/v1/notifications/unread-count` | `unreadCount` | `comm_0192_007_r002` |
| PUT | `/api/v1/notifications/{notificationId}/read` | `markRead` | `comm_0192_007_u001` (single REC) |
| PUT | `/api/v1/notifications/read-all` | `markAllRead` | `comm_0192_007_u001` (full list) |

**Error codes:** `WBE_NOTIFICATION_NOT_FOUND` (unknown / other-tenant id).
**Frontend:** `components/layout/notifications/{NotificationPopup,NotificationTabs,NotificationSearch,NotificationItem,types}.tsx` ✔; `lib/api/notification.ts` (`fetchNotifications`, `fetchUnreadCount`, `markNotificationRead`, `markAllNotificationsRead`) ✔; bell rendered in `components/layout/Header.tsx`.
**Reference analyses:** `docs/audits/notification-popup-legacy-vs-refactor-analysis.md`, `docs/audits/notification-approval-detail-popup-parity-audit.md`.

---

## 7. navigation (product menu tree)

| Layer | Artifact | Path (verified ✔ glob) | Notes |
|---|---|---|---|
| Controller | `NavigationController` ✔ | `domain/navigation/controller/` | `GET /api/v1/navigation` — product-tree resolved from JWT `prdtType` (never a client param). |
| Service | `NavigationService` ✔, `MenuAuthorizationGuard` ✔ | `domain/navigation/service/` | Product Tree ∩ `USER_MENU_AUTH_INFM` grants (admin = full tree). `@menuAuthz` bean = `MenuAuthorizationGuard` (gates every business endpoint, F-3). |
| Entities | `MenuItem`, `ProductMenu`(+Id), `MenuItemTranslation`(+Id) ✔ | `domain/navigation/entity/` | **Refactor-owned infra tables** (V242 schema, V243 seed) — not legacy, flagged in legacy-table-inventory. |
| Repositories | `MenuItemRepository`, `ProductMenuRepository`, `MenuItemTranslationRepository` ✔ | `domain/navigation/repository/` | — |
| DTO | `NavigationResponse`, `NavMenuNode` ✔ | `domain/navigation/dto/` | — |

**Tests:** `NavigationServiceTest` (8 cases: F/M/H × admin/non-admin, out-of-tree-grant inert, no-grants empty, HS Daily-Financial split).
**Frontend:** `hooks/useNavigation.ts` ✔, `hooks/useTopMenuItems.ts` ✔, `lib/api/navigation.ts` ✔, `lib/menuAuth.ts` ✔, `components/layout/{Sidebar,Header,SidebarMenuItem,ProfileMenu,AppShell}.tsx` ✔, `components/layout/sidebarNav.ts` (`buildSidebarGroupsFromTree`) ◦.

---

## 8. common-code-lookup (reference data + shared selects)

| Concern | Artifact | Path | Notes |
|---|---|---|---|
| Country | `CountryController` ✔ | `domain/common/country/controller/` | `GET /api/v1/common/countries` — read-only, **no tenant filter** (shared). Legacy `comm_0110_001_r001`. |
| Country | `CountryService` ✔, `NatnInfm` ✔, `NatnInfmRepository` ✔, `CountryListResponse`/`CountryRow` ✔ | `domain/common/country/` | `@Table NATN_INFM`. |
| Bank | `BankCdResolver` ◦, `BankInfm`/`BankInfmRepository` ◦ | `domain/banking/...` | startup cache + DB fallback; `LegacyFunctionRepository.resolveNapasOrCitadBankName(code,lang)` (V114). |
| Currency | `CcyInfm` ◦ | — | `@Table CCY_INFM`. |
| Frontend selects | `PurposeSelect.tsx` ✔, `ReportingAccountSelect.tsx` ✔, `SearchSelect.tsx` ✔ (foundation), `FilterSelect.tsx` ✔, `FilterDropdown.tsx` ✔ | `components/shared/` | options sourced per-domain; no shared `lib/api/lookup.ts`. |

**Note:** only **Country** has a Common REST endpoint today; Bank/Currency are resolved inside owning-domain responses. Reporting-account picker is a **shared frontend select** but the data + CRUD belong to Settings `chart-of-accounts` (cross-link, no duplicate folder).

---

## 9. table-sort

| Layer | Artifact | Path | Notes |
|---|---|---|---|
| Backend | `SortDirection` ✔(glob), `SortableField` ✔(glob) | `common/web/` | lenient `parse(...)` → null on unknown (degrade to default ORDER BY); per-domain whitelist enums implement `SortableField.entityProperty()`. |
| Backend | `RequestResponseLoggingFilter` ✔(glob) + `LogMask` ✔ | `common/web/`, `common/security/` | request/response logging with sensitive-value masking. |
| Frontend | `hooks/useTableSort.ts` ✔, `components/shared/TableHeaderCell.tsx` ✔ | — | 3-state cycle (default→ASC→DESC→default); `sortParams` spread into the API call; sort is **DB-side**, never `Array.sort()`. |

Whitelist enums live next to each list service (`AccountSortField`, `ContactSortField`, `ApprovalLineSortField`, `ChartOfAccountsSortField`, `EInvoiceSortField`, `OperationStatusSortField`, …) — the Common doc owns the *contract*; each enum is owned by its domain.

---

## 10. dialog-popup (frontend-only)

| Layer | Artifact | Path (verified ✔) | Notes |
|---|---|---|---|
| System dialog | `DialogProvider.tsx`, `SystemDialog.tsx`, `DialogIcon.tsx`, `types.ts`, `index.ts` ✔ | `components/shared/dialog/` | `useDialog()`; 4 types (warning/error/success/confirmation); `z-[100]` above all stack levels. |
| Modal primitive | `components/ui/dialog.tsx` ◦ | `components/ui/` | shadcn `<DialogContent>` with `stackLevel` 1–5 (z-index + backdrop dim ladder). |
| Stack gating | `lib/modalStack.ts` ◦ | `lib/` | ESC gating for stacked modals. |
| Shared popups | `components/shared/popups/*Dialog.tsx` ✔ | — | `AuditLogsDialog`, `SelectBankDialog`, `LoginIdPasswordDialog`, `PasswordConfirmationDialog`, `GdtCredentialsDialog`, etc. |

**Backend:** none — "popup" is not a backend concept (JSON request/response only). The Common doc for this module is **WEB-only** (6-file folder under `wecms-web/docs/features/common/dialog-popup/`); the API side gets a one-line "no backend surface" note.

---

## 11. error-envelope (response · exception · trace · i18n)

| Layer | Artifact | Path (verified ✔ glob) | Notes |
|---|---|---|---|
| Envelope | `ApiResponse`, `CommonHead` | `common/response/` | `{ status, data, timestamp?, path?, trace_id? }`; `ok(...)` / `error(...)`. |
| Exception | `BusinessException`, `GlobalExceptionHandler` | `common/exception/` | single `@RestControllerAdvice`; exception→HTTP-status map; `WBE9999` catch-all. |
| Trace | `TraceId`, `TraceIdFilter` | `common/exception/` | JEX-style id in MDC + `X-Trace-Id` header. |
| i18n | `MessageResolver`, `RelativeTimeFormatter` | `common/i18n/` | `messages*.properties` (EN/KO/VI/KM); WBE/WBI codes. |
| Base entity | `BaseEntity`, `LegacyDtmConverter` | `common/entity/` | REG/MOD audit columns; legacy varchar(14) ↔ OffsetDateTime. |
| Security | `JwtTokenProvider`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint`, `WecmsPasswordEncoder`, `LegacyHashPasswordEncoder`, `LogMask` | `common/security/` | (also documented in `auth/`). |
| Tenant | `TenantContext` | `common/tenant/` | ThreadLocal principal accessors. |
| Tx | `TransactionalRetry` | `common/tx/` | one-shot retry on `DataIntegrityViolationException`. |
| Legacy fn | `LegacyFunctionRepository` | `common/legacy/` | sole `wb_get_next_val` executor (28 branches, V155). |

**Frontend mirror:** `lib/types/api.ts` (`ApiResponse<T>`), `lib/axios.ts` (interceptor + 401 handling), `lib/api/*.ts` (`ApiCallError`), `lib/i18n/LocaleProvider.tsx`, `lib/print.ts`.
**Error code registry:** cross-link `docs/_shared/error-code-registry.md` (do not duplicate the full list per module).

---

## 12. Cross-linked (owned by a domain — NO Common folder)

| Prompt "module" | Real owner | Doc location |
|---|---|---|
| approval (flow / status / references) | Payment `approval-box` (execution) + Settings `approval-line` (config) | `…/payment/approval-box/`, `…/settings/approval-line/`; shared FE `components/shared/approver/*` |
| reporting-account | Settings `chart-of-accounts` | `…/settings/chart-of-accounts/`; shared FE `ReportingAccountSelect.tsx` |
| attachment | Settings `contacts` (file upload) | `…/settings/contacts/` |
| search | `SearchInput.tsx` (+ per-endpoint `search` params) | folded into common-code-lookup / each list feature |

## 13. DEAD / MISSING

| Item | Evidence | Classification |
|---|---|---|
| `dashboard-common` (`SYS_DSHBRD_INFM` / `USER_DSHBRD_INFM`) | no `DashboardController`, no `/api/v1/dashboard`, tables never migrated to Flyway | **MISSING** (deferred; README note only, no stub folder) |
| old `domain/notification/` + `NOTIFICATION` table (V238) | package + table removed 2026-06-10; superseded by `domain/payment/notification` on BLTB | **DEAD LEGACY** (do not re-document the removed shape) |
| backend "popup" concept | refactor uses JSON; no popup envelope | **N/A by design** (dialog-popup is WEB-only) |

---

*Last verified: 2026-06-23. References marked ◦ should be re-opened and upgraded to ✔ as each per-module folder is authored in Phase 2.*
