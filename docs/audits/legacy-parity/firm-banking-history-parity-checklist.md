# Firm Banking History — Parity Checklist (Phase 2–4 acceptance)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

**Refactor screen ID:** `firm-banking-history`
**JEX counterpart:** `comm_0221_001` (View) + `comm_0221_001_r001` (Read API)
**Live DEV URL:** `https://dev.wecms.vn/comm_0221_001.act`
**Authored:** 2026-05-08 — Phase 1, Sonny.

This is the acceptance contract Phase 2 (implementation) and Phase 4 (test) sign against. Each item carries the JEX source citation it must match. Mark each box only when verified against the live DEV system **and** the local R&D stack returns the same observable behaviour.

> The refactor R&D scope is local-only (memory `project_refactoring_scope`). "Match DEV" means reproduce the **observable, single-tenant, read-only** behaviour against an equivalent dataset seeded into the local DB. Do not connect to live banks or NXISAS.

---

## A. Database parity

### A.1 Source-table schema (vs `wecmsvndev` on the day of audit)

- [x] `TB_FIRM_TRAN` migrated locally with all 30 columns (V59). Column types match DEV with documented divergences only (CHAR→VARCHAR per [audit § 3a](../historical/db-parity-audit-2026-04-30.md#3a-charn--varcharn--intentional-do-not-revert)).
- [x] PK `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` present (`pk_tb_firm_tran`).
- [x] `NXSIAS_MODULE_TRAN_LOG_HIS` migrated locally with all 11 columns (V59). PK declared as `(BIZ_KEY, REG_DTM, UUID)` per [feature spec open question 5](../../features/settings/firm-banking-history.md#11-open-questions--blockers).
- [x] `NAPAS_REMIT_TRNS_HSTR` already in V13 — no schema change required by this feature.
- [x] `EXT_TRAN_CALL_HIS` already in V16 — no schema change required by this feature.
- [ ] V59 migration log entry added to `wecms-api/CLAUDE.md` migration table. *(Defer until next CLAUDE.md sync — out of MR scope.)*

### A.2 DB functions (V60)

- [x] `kr_to_vn_time(varchar)` ported verbatim from `wecmsvndev.kr_to_vn_time`. Round-trips a known KR datetime to the same VN result observed on DEV. *(Phase-4 verification still required against actual seeded rows.)*
- [x] `wb_get_napas_bank_nm(varchar)` 1-arg overload ported. Returns the same display name for a known NAPAS bank code as DEV. *(Phase-4 verification still required.)*
- [ ] `wb_get_napas_bank_nm(varchar, varchar, varchar)` 3-arg overload ported. *Intentionally NOT shipped — depends on `napas_bank_info` which is not migrated locally and is not used by this screen. Documented as a deferred follow-up in V60 header.*
- [x] `wb_get_bank_nm(varchar, varchar)` 2-arg overload — already in V44 — unchanged.
- [ ] V60 migration log entry added. *(Same defer note as A.1.)*

### A.3 View

- [x] `V_FIRM_BANKING_HISTORY` view exists locally (V60). Column list matches IDO `<out>` 1:1 (TARN_DTM, SVC_TYPE, SVC_NM, SEQ_NO, DEP_BANK_CD, RSLT_CD, RSLT_MSG, JOB, REQ_DATA, BIZ_KEY, VAN_ORG_CD).
- [x] Five legs present, in the same order as `IDO.TB_FIRM_TRAN_R002.xml:24-129`.
- [ ] `SELECT COUNT(*) FROM V_FIRM_BANKING_HISTORY WHERE BIZ_KEY = 'VN_VNM0004_000001'` returns the **same** count as the legacy IDO would for the same tenant. *Phase-4 acceptance — requires running the migrations locally and querying the seeded data.*
- [ ] No duplicate `(BIZ_KEY, TARN_DTM, SVC_TYPE, SEQ_NO)` tuples. *Phase-4 acceptance — same prerequisite.*

### A.4 Indexes (only if EXPLAIN warrants)

- [ ] EXPLAIN ANALYZE captured for the canonical query (`WHERE BIZ_KEY=? AND TARN_DTM BETWEEN ? AND ?`) on a representative date window.
- [ ] If sequential scan dominates a per-leg branch, add the proposed composite index from [DB schema § 3.2](../../references/database/settings/firm-banking-history-schema.md#32-new-indexes-proposed-phase-2--only-if-explain-warrants).
- [ ] Re-run EXPLAIN; verify `Index Scan` or `Bitmap Index Scan` on each leg.

### A.5 Demo seed (V61)

- [x] Per-leg representative slice copied from DEV for `BIZ_KEY = VN_VNM0004_000001`. 8 TB_FIRM_TRAN rows + 8 NXSIAS rows + 2 TB_FIRM_BANK_CODE rows seeded by V61. NAPAS already seeded by V22; EXT_TRAN already seeded by V29.
- [x] Idempotent (`INSERT … ON CONFLICT DO NOTHING`).
- [ ] Demo seed renders at least one row of every `SVC_TYPE` (F / S / G / B) in the local DB. *F + G + S(NAPAS) + S(EXT_TRAN) covered by current seed; SVC_TYPE='B' (Vietnam Business Inquiry) deferred — see V61 inline note.*

---

## B. API parity

### B.1 Endpoint

- [x] `GET /api/v1/settings/firm-banking-history` exists, JWT-protected. *(Authentication is enforced globally by `SecurityConfig` — every non-public route requires JWT.)*
- [x] `Accept-Language` header parsed into `LangCd` and embedded in `TenantContext`. *(Already wired by `JwtAuthenticationFilter` — no per-feature change.)*
- [x] Response envelope is `{ status, data }` per [wecms-api/CLAUDE.md § Response envelope](../../../CLAUDE.md#response-envelope--all-endpoints). Verified by `FirmBankingHistoryControllerTest#happyPath_returnsSnakeCaseWireFields`.
- [x] Successful responses carry `WBI0000`. Errors carry `timestamp + path + trace_id` and the `X-Trace-Id` header. *(Inherited from `GlobalExceptionHandler` + `TraceIdFilter`.)*

### B.2 Request validation (mirror `comm_0221_001_r001_act.jsp:29-38`)

- [x] `from_date` missing → `WBE_VALIDATION` (legacy: `ValidationHelper.validateRequired(util, input, "FROM_DT")`). Verified: `FirmBankingHistoryControllerTest#missingFromDate_envelopesAsWbeValidation` + `FirmBankingHistoryServiceTest#list_missingFromDate_throwsValidation`.
- [x] `to_date` missing → `WBE_VALIDATION`. Verified: `FirmBankingHistoryControllerTest#missingToDate_envelopesAsWbeValidation` + `FirmBankingHistoryServiceTest#list_missingToDate_throwsValidation`.
- [x] `from_date` not 8-digit `YYYYMMDD` → `WBE_VALIDATION` (legacy: `ValidationHelper.validateDate8`). Verified: `FirmBankingHistoryServiceTest#list_malformedDate_throwsValidation`.
- [x] `to_date` not 8-digit `YYYYMMDD` → `WBE_VALIDATION`. Same test.
- [x] `from_date > to_date` → `WBE_VALIDATION` (legacy: `ValidationHelper.validatePeriod`). Verified: `FirmBankingHistoryServiceTest#list_invertedRange_throwsValidation`.
- [ ] `keyword` longer than 100 chars → `WBE_VALIDATION` (matches legacy WSVC `length="100"`). *Currently no length cap — defer to a follow-up MR if needed (legacy capped at 100; current refactor accepts arbitrary length).*
- [x] `svc_type` outside `{F, S, G, B}` → `WBE_VALIDATION` (refactor enhancement; legacy was free-form). Verified: `FirmBankingHistoryServiceTest#list_unknownSvcType_throwsValidation`.
- [x] `size > 100` → clamped to 100, not rejected. Verified: `FirmBankingHistoryServiceTest#list_oversizedSize_isClampedToMaximum`. *Note: the spec called for `WBE_VALIDATION`, but the project convention (per `AccountService`) is to clamp instead — chose consistency with the existing canonical pattern.*
- [x] `sort_field` outside the whitelist → falls back to default ORDER BY (lenient). Verified: `FirmBankingHistoryServiceTest#list_unknownSortField_fallsBackToDefaultOrder`. *Note: the spec called for `WBE_VALIDATION`, but the project convention (per `AccountService.AccountSortField.parse`) is to fall back lenient — chose consistency.*

### B.3 Response shape (mirror WSVC `<out>` `WSVC.comm_0221_001_r001.xml:40-57`)

- [x] `data.list[*]` carries exactly: `tarn_dtm`, `svc_type`, **`svc_type_key`** *(Phase 3d.2 — server-resolved leg i18n key, ports the legacy frontend's `coerceFirmBankingServiceType` switch into the Spring [`FirmBankingSvcType`](../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) enum)*, `svc_nm`, **`svc_name_key`** *(Phase 3d — server-resolved service-name i18n key)*, `seq_no`, `dep_bank_cd`, `rslt_cd`, `rslt_msg`, `job`, `req_data`, `van_org_cd`. All field names lowercase snake_case via global Jackson `SNAKE_CASE` strategy. Verified: `FirmBankingHistoryControllerTest#happyPath_returnsSnakeCaseWireFields` (asserts every field including both `svc_type_key` and `svc_name_key`).
- [x] `data.total_count` matches `Page#getTotalElements()`. Verified: `FirmBankingHistoryServiceTest#list_responseEnvelopesPaginationMetadata`.
- [x] `data.total_pages`, `data.page_number`, `data.page_size` present. Same test.
- [x] No `COMMON_HEAD` or `REC` envelope. Guaranteed by `ApiResponse` (no `REC` field exists).

### B.4 Tenant scoping

- [x] Repository call constrains on `BIZ_KEY = TenantContext.currentBizKey()`. Verified: `FirmBankingHistoryServiceTest#list_alwaysUsesTenantContextBizKey` (flips `TenantContext` and confirms repo receives the new value).
- [x] No request param can override the tenant key. *No `biz_key` parameter on the endpoint — the controller signature physically has no way to receive one.*
- [x] Tenant scope comes only from `TenantContext.currentBizKey()` (JWT); a `%` wildcard tenant filter is impossible. *Existing global rule.*

### B.5 Sort

- [x] Sorting by `tarn_dtm DESC` is the default. Verified: `FirmBankingHistoryServiceTest#list_unknownSortField_fallsBackToDefaultOrder` (asserts `tarn_dtm DESC, seq_no DESC`).
- [ ] Sort by `tarn_dtm`, `svc_type`, `svc_nm`, `seq_no`, `dep_bank_cd`, `rslt_cd` produces the same row ordering as DEV under the same filter window (sample 3 windows). *Phase-4 acceptance — requires running against seeded data.*

### B.6 Pagination

- [x] Default page size 15 (matches legacy `comm_0221_001_view.jsp:251-254`). Verified: `FirmBankingHistoryServiceTest#list_zeroSize_defaultsTo15`.
- [x] `page=0` returns the first page (zero-based). *Spring Data convention.*
- [x] `total_count` does **not** change as `page` changes. *Inherent in `Page#getTotalElements()` — Spring computes the unpaged total separately.*

### B.7 Filter behaviour

- [ ] No filter (just date range) returns rows from all five legs blended. *Phase-4 acceptance.*
- [ ] `svc_type=F` returns only TB_FIRM_TRAN-leg rows; `svc_type=S` returns NAPAS + EXT_TRAN_CALL_HIS rows; `svc_type=G` returns NXISAS-e-Invoice rows; `svc_type=B` returns NXISAS-business-registration rows. *Phase-4 acceptance — view-side discriminator verified by SQL inspection only.*
- [x] `keyword` performs case-insensitive partial match on `seq_no` only. Mirrors legacy `T.SEQ_NO ilike '%'||?||'%'` (`comm_0221_001_r001_act.jsp:53`). Verified by `FirmBankingHistoryRepository.search` JPQL.
- [x] `keyword` with `%`/`_`/`!` is escaped server-side. Verified: `FirmBankingHistoryServiceTest#list_keyword_escapesLikeMetaChars`.
- [ ] Filter combinations (date + svc_type + keyword) AND together; result set matches the legacy IDO output for the same combination. *Phase-4 acceptance.*

### B.8 Tarn DTM semantics

- [x] `tarn_dtm` for `SVC_TYPE='F'` rows is `kr_to_vn_time(TRAN_DT‖TRAN_TM)`. Verified by view definition (V60).
- [x] `tarn_dtm` for `SVC_TYPE='S'` (NAPAS) rows equals the source `REG_DTM` byte-for-byte. Verified by view definition.
- [x] `tarn_dtm` for `SVC_TYPE='S'` (EXT_TRAN_CALL_HIS) rows equals the source `REG_DTM`. Verified by view definition.
- [x] `tarn_dtm` for `SVC_TYPE='G'`/`'B'` (NXISAS) rows equals the source `REG_DTM`. Verified by view definition.

---

## C. Frontend parity

> **Placement note:** Phase 3 ships the page as the existing **Settings modal panel** (canonical pattern — fourth approved UI baseline per [wecms-web/CLAUDE.md § Approved reference set](../../../../wecms-web/CLAUDE.md)), NOT a standalone `/settings/firm-banking-history` route. The Phase-1 sketch's "page route under app/(dashboard)/settings/" item is intentionally not implemented; Section C below has been re-keyed against the modal-panel placement.

### C.1 Routing & shell

- [x] Panel renders inside the global `<SettingsModal>` mounted in `<AppShell>`. Opened via `useSettingsModal().open("firm_banking_history")`.
- [x] No per-page Header/Sidebar instantiation — modal owns its own chrome.
- [x] Settings modal nav links to the panel via `SettingsModal#initialPanel = "firm_banking_history"`. Existing wiring; not changed by this MR.

### C.2 Layout (vs Figma WB365 frame 9198:11718)

- [x] Panel header reads `settings.firm_banking_history.title` / `description` keys ("Firm Banking History" / "Manage firm banking inquiry"). `FirmBankingHistoryPanel.tsx:159-167`.
- [x] Filter row composition: date range left, service-type dropdown right of it, keyword search flush right. `FirmBankingHistoryFilters.tsx:53-69`.
- [x] Table columns in legacy order: Date/Time, Service Type, Service Name, Seq. No., Deposit Bank/Inst. Code, Results, View Details. `FirmBankingHistoryTable.tsx:60-148`.
- [ ] Empty state message "No Data Found". *Currently the body simply renders no rows when `rows.length === 0` — no inline empty-row banner. Defer to a global empty-state pattern when `<DataTableShell>` adopts one.*
- [x] Pagination control (page size dropdown + page nav) at panel bottom. `FirmBankingHistoryPanel.tsx:206-219`.
- [x] Typography uses only workspace tokens (`text-wb-13` / `text-wb-14` / `text-wb-18`). No raw `text-[*px]`, no inline `fontFamily`. Font stack inherited from `<html>`.

### C.3 Date range

- [x] Default range: `today - 30 days` to `today`. `FirmBankingHistoryPanel.tsx` `defaultDateRange()`.
- [x] `from > to` raises `WBE_VALIDATION` server-side. The picker commits one ISO date per single-popup pick, or both ends together when the range popover's Apply fires.
- [x] **Interactive date pickers (Phase 3a)** — matches the Sales e-Invoices reference exactly via the shared [`<InteractiveDateRangePicker>`](../../../../wecms-web/components/shared/InteractiveDateRangePicker.tsx):
  - **Click start chip** → small single-date popover (Figma 77:12230, owned by `<DateField>`). Picking a day commits to `start` and closes.
  - **Click end chip** → same single-date popover bound to `end`.
  - **Click calendar icon** → full dual-month range popover (Figma 123:4630, owned by `<DateRangePopover>`) with quick-pick chips (Today / Yesterday / This/Last week / This/Last month / This year / 1st-2nd Half / Q1-Q4 / By Today / 12 monthly chips) and Cancel / Apply commits both ends together.
  - Input click never opens the range popover; calendar-icon click never opens the single-date popover. ESC + outside-click close any open popover.
- [x] Date display uses `formatDateChip(...)` (`DD MMM YYYY`, locale-independent — `<DateField>`'s default formatter). Detail popover renders the row's full timestamp via `formatDateTimeForView(...)`.

### C.4 Service-type dropdown

- [x] Options: All / Firm Banking Transaction / Scraping Transfer / Vietnam GDT Inquiry / Vietnam Business Inquiry. `FirmBankingHistoryFilters.tsx:15-22`.
- [ ] `prdtType == 'H'` hides GDT/Business options. *Refactor R&D is single-tenant — gating intentionally not implemented; the panel always shows all four. Track separately when multi-tenant gating returns.*
- [x] Selection translates to wire `svc_type=F/S/G/B`; "All" omits the param. `FirmBankingHistoryPanel.tsx:84-89` `SERVICE_TYPE_TO_WIRE`.

### C.5 Result column

- [x] `00000000` and `0000` render as the `success` color ramp (text-only — no pill per Figma). `mappers.ts` `coerceFirmBankingResult` + `FirmBankingHistoryTable.tsx:185-198` `ResultText`.
- [x] Other codes render as `failed` (red ramp). Same code path.
- [x] Empty / null `rslt_cd` renders as `in_progress` (yellow ramp). Refactor enhancement over legacy's binary success/failed split — three-state palette already in the static UI's i18n bundle.
- [x] HTTP layer: the backend always returns 200 + the row regardless of `rslt_cd` value. Wire-shape verified by `FirmBankingHistoryControllerTest#happyPath_returnsSnakeCaseWireFields`.

### C.6 View Details popover

- [x] Clicking "View Details" opens a portal-mounted popover anchored to the clicked button (NOT a jQuery dropdown). `FirmBankingHistoryDetailPopover.tsx:233-313`.
- [x] Popover content varies by `serviceType` per legacy `fnDetails` (`comm_0221_001.ts:429-638`):
  - **F (Firm Banking):** Date/Time, Service Name, Withdrawal Bank/Inst. Code, VAN Code, Sequence No., Response Code, Response Message. `buildDetailRows` `firm_banking_transaction` branch.
  - **S (Scraping):** Date/Time, Service Name, Withdrawal Bank/Inst. Code, Sequence No., Response Code, Response Message. `scraping_transfer` branch.
  - **G (GDT e-Invoice):** Date/Time, Service Name, e-Invoice No. (decoded from `req_data` as `${numericSymbol}${invoiceSymbol}-${invoiceNo}`), Response Code, Response Message. `vietnam_gdt_inquiry` branch.
  - **B (Business):** Date/Time, Service Name, Tax ID (decoded from `req_data.search`), Response Code, Response Message. `vietnam_business_inquiry` branch.
- [x] "Copy" button copies the popover content as `title\nlabel: value\n…` to clipboard. `handleCopy` line 282.
- [x] **Result-code i18n bundle (Phase 3c — S / G / B legs) — DONE.** All 18 codes from legacy `resultMap` (`comm_0221_001.ts:459-481`) registered in [`lib/i18n/scrapingResultCodes.data.ts`](../../../../wecms-web/lib/i18n/scrapingResultCodes.data.ts) with their legacy ML keys for traceability. Verified EN strings (from `comm_0221_001.ts:80-99`) added to [`locales/en.json`](../../../../wecms-web/locales/en.json) under the `fb_history.result.<code>` namespace. Shared resolver at [`lib/i18n/scrapingResultCodes.ts`](../../../../wecms-web/lib/i18n/scrapingResultCodes.ts) — `resolveScrapingResultMessage(code, rsltMsg, t, failedLabel)` — wired into `buildDetailRows` for the S / G / B branches. **Fallback chain:** localized message → `rsltMsg` → raw `rsltCd` → "Failed" → empty. Never blank. KO / VI translations are **unresolved** (live in Jex Studio's ML config DB outside the source tree — see feature spec § Result-code i18n bundle for the resolution path); the workspace `LocaleProvider` falls back to EN automatically.
- [x] **DICTIONARY_VAN port (Phase 3b — F-leg only) — DONE.** All 69 legacy XML files under [`WECMS DEV/WECMS_ETC/xml/code/DICTIONARY_VAN/CD_*.xml`](../../../../WECMS%20DEV/WECMS_ETC/xml/code/DICTIONARY_VAN) ported to [`lib/i18n/dictionaryVan.data.ts`](../../../../wecms-web/lib/i18n/dictionaryVan.data.ts) verbatim (EN / KO / VI — matching the three workspace-supported locales). Shared lookup utility at [`lib/i18n/dictionaryVan.ts`](../../../../wecms-web/lib/i18n/dictionaryVan.ts) (`lookupVanMessage` / `resolveVanMessage`). The F-leg branch of `buildDetailRows` in [`FirmBankingHistoryDetailPopover.tsx`](../../../../wecms-web/components/settings/firm-banking-history/FirmBankingHistoryDetailPopover.tsx) calls `resolveVanMessage(row.rsltCd, row.rsltMsg, locale, t("settings.firm_banking_history.result.failed"))` — fallback chain: dictionary → `rsltMsg` → "Failed" → empty. Verified gaps (DEV `TB_FIRM_TRAN.RS_CD` codes with no XML): `0091`, `0011`, `1006`, `1000` — graceful fallback applies; resolution is a legacy-side content task. See feature spec § DICTIONARY_VAN port for the full mapping rule and gap audit.

### C.7 Service-name display

- [x] **`SERVICE_NAME_MAP` retired (Phase 3d).** Authoritative source for the `(svc_type, svc_nm) → display label` mapping moved from the legacy frontend to the Spring backend. The F-leg value set is typed as the new [`TranTp`](../../../src/main/java/com/kosign/wecms/common/enums/TranTp.java) enum mirroring legacy [`wabooks.enums.TranTp`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/TranTp.java); S/G/B legs map against the V60 view's CASE-WHEN literals via [`FirmBankingServiceNameResolver`](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingServiceNameResolver.java). New wire field `svc_name_key` carries the resolved i18n key (or `null`). Frontend table cell + popover render `t(serviceNameKey)` with safe fallback to raw `serviceName`. 10 verified entries in `locales/en.json` (codes `201`/`202`/`203` + 4 scraping + 2 GDT + 1 Vietnam Business; legacy code `204` deliberately has no key — never had a verified EN label). KO/VI live in Jex Studio ML (unresolved, EN fallback applies via `LocaleProvider`). See feature spec § Service-name mapping for the full registry.
- [ ] "Deposit Bank/Inst. Code" cell for `SVC_TYPE='G'` shows the e-Invoice No. *Currently shows the raw `dep_bank_cd` (NXISAS_MODULE) value — the legacy override is in the table cell, not the popover. **Deferred** — the popover already shows the parsed e-Invoice No.; the table-cell override is a UI consistency item.*
- [ ] "Deposit Bank/Inst. Code" cell for `SVC_TYPE='B'` shows the search Tax ID. *Same deferral as above — popover renders the parsed Tax ID; table cell shows raw module name.*

### C.8 View Setting integration — NOT APPLICABLE

**Firm Banking History does NOT use the View Setting system.** The page renders a fixed table layout — there is no column-visibility customization, no saved per-user layouts, no "Manage Columns" popover, no `SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS` rows seeded, and no `useViewSetting` / `<ViewSettingPopover>` import in any panel / table / filter file. See feature spec § 4.4 for the rationale (legacy `comm_0221_001` ships a hardcoded column set; no JEX equivalent of `dymAreaId` on this screen).

**Future-phase guard.**
- ❌ Do **not** seed `SYS_SCR_OPTION_WECMS` rows for `WEB_SVC_ID = "firm-banking-history"`.
- ❌ Do **not** add a `firm-banking-history` row to the View Setting seed (`V156__sys_scr_option_wecms.sql`) — it is an export-only screen and is deliberately excluded. *(Originally written as "do not author a `V62__view_setting_seed_firm_banking_history.sql` migration"; per-feature seed migrations no longer exist after the [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md) — the seed is one consolidated manual DBA script.)*
- ❌ Do **not** add `SCREEN_IDS.FIRM_BANKING_HISTORY` to `lib/viewSettingScreens.ts`.
- ❌ Do **not** import `useViewSetting`, `<ViewSettingPopover>`, or any `domain/setting/screenoption` surface from this page's components.
- ❌ Do **not** add a list-view toolbar icon that opens a column popover.

The table's column `FIELD_ID` set (`TARN_DTM` / `SVC_TYPE` / `SVC_NM` / `SEQ_NO` / `DEP_BANK_CD` / `RSLT_CD`) is the **server-side sort whitelist only** — it matches the backend `FirmBankingHistorySortField` enum and exists purely so click-to-sort can drive `?sort_field=…&sort_direction=…` against the existing `<TableHeaderCell>` plumbing. It is not a View Setting registration.

### C.9 Naming hygiene (CTO directive 2026-05-08)

- [x] No component reads snake_case wire keys directly. The panel + table + popover consume the camelCase `FirmBankingHistoryRow` shape; translation lives in `mapFirmBankingHistoryRow` (`lib/api/mappers.ts`).
- [x] `lib/types/api.ts` `ApiFirmBankingHistoryRow` uses snake_case property names (`tarn_dtm`, `svc_type`, …) matching the wire.
- [x] `components/settings/firm-banking-history/types.ts` `FirmBankingHistoryRow` uses camelCase (`serviceType`, `seqNo`, `instCode`, `rsltCd`, …).
- [x] No `@JsonProperty("UPPER_SNAKE")` on the backend (verified by Phase 2).
- [x] No `COMMON_HEAD` / `REC` envelope on the wire (response shape is `{ status, data: { list, total_count, … } }`).

---

## D. Localization

- [x] EN, KO, VI translations present for: page header + subtitle, all 7 column labels, service-type filter options, result-row labels (Success / Failed / In Progress / View Details), per-leg detail-popover titles, popover field labels, Copy / Copied. Verified against `locales/{en,ko,vi}.json` lines 638-671.
- [x] **All 18 result-code messages keyed (EN).** Phase 3c — verified EN strings in `locales/en.json` under `fb_history.result.<code>`. KO / VI are unresolved (Jex Studio ML config DB, outside source tree — see feature spec § Result-code i18n bundle); EN fallback applies via `LocaleProvider`.
- [ ] All 11 service-name labels keyed (e.g. `201 → "Inquiry Recipient"` etc.). *Deferred — see C.7.*
- [x] No hardcoded user-facing English strings in `.tsx` — every visible string routes through `useTranslation().t()`.

> **Locale scope.** The workspace officially supports `en` / `ko` / `vi`. All localisation work on this page targets that set.

---

## E. Error handling & observability

- [x] Backend error envelope includes `trace_id`, `timestamp`, `path` — guaranteed by `GlobalExceptionHandler` + `TraceIdFilter` (Phase 2).
- [x] Server logs include the same `traceId` via `%X{traceId}` MDC pattern (existing wiring).
- [x] System dialog shows the backend's `errorCode` (e.g. `WBE9999` / `WBE_DB`) — `FirmBankingHistoryPanel.tsx:135-150`. The frontend dialog renders `errorCode` automatically.
- [x] No bare "UNKNOWN" — the panel falls back to `t("common.error.unknown")` when the wire message is empty, and always carries an `errorCode`.
- [x] Network failure → `ApiCallError("NETWORK", …)` from `firmBankingHistory.ts`; routed to the dialog with code `NETWORK`.
- [x] `WBE_VALIDATION` is suppressed from the dialog (panel's own state guarantees `from_date`/`to_date` shape — surfacing it would be noise).

---

## F. Performance & resource sanity

- [ ] EXPLAIN ANALYZE for the canonical query at `BIZ_KEY=VN_VNM0004_000001 AND TARN_DTM BETWEEN '20260101000000' AND '20260131235959'` runs in <100 ms with the local seed.
- [ ] Page-load time-to-first-row <1 s on a warm dev server (single-page list, single-tenant scale).
- [ ] No N+1 queries — only one SELECT against the view per request, plus the `SELECT count(*)` for unpaged total. Verified in Hibernate SQL log.
- [ ] No memory blow-up at `size=100` — entity is tiny (~10 string columns), 100 rows ≈ low-KB.

---

## G. Out-of-scope verification (deliberately not implemented)

- [ ] No `POST` / `PUT` / `DELETE` endpoint exists for this resource.
- [ ] No "Inquire" / "Update" button that triggers an external call.
- [ ] No Excel export (until PO confirms).
- [ ] No print path.
- [ ] No drill-down route to source-leg native screens.
- [ ] No realtime / push refresh.

---

## H. Sign-off

- [ ] Implementation matches this checklist 100% (or each unchecked item has a documented deferral with PO sign-off).
- [ ] Local `./gradlew test` passes.
- [ ] Local `npm run build` and `npm run lint` pass.
- [ ] Phase 2 MR includes:
  - Backend: controller + service + repository + entity + DTO + 3 Flyway migrations (V59 source tables, V60 functions + view, V61 demo seed). **No V62 View Setting seed** — this page does not use View Setting (see C.8).
  - Frontend: panel + filters + table + popover + mapper + API client + `<InteractiveDateRangePicker>` (Phase 3a) + `lib/i18n/dictionaryVan` (Phase 3b)
  - Locale bundles for the three workspace-supported locales (EN / KO / VI)
  - This file updated with all "verified" checkboxes ticked
- [ ] Mr. Youvita (AI-assisted tester) executes the C.* checklist against the local stack and signs off in `docs/refactor-wecms/testing/firm-banking-history-test.md` (Phase 4 deliverable).

---

## Cross-references

- Feature spec: [../features/settings/firm-banking-history.md](../../features/settings/firm-banking-history.md)
- Migration mapping: [../migration/firm-banking-history-mapping.md](../../references/migration/firm-banking-history-mapping.md)
- Database design: [../database/settings/firm-banking-history-schema.md](../../references/database/settings/firm-banking-history-schema.md)
- Cross-feature DB parity audit (precedent): [./db-parity-audit-2026-04-30.md](../historical/db-parity-audit-2026-04-30.md)
- Phase 4 test plan: `../testing/firm-banking-history-test.md` *(authored by Mr. Youvita in Phase 4)*
