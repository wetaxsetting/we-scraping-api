# Common (Cross-Cutting) — Gap Analysis

**Audit date:** 2026-06-23
**Companions:** [common-docs-audit.md](common-docs-audit.md) · [common-source-index.md](common-source-index.md)
**Convention:** every row is verified against the working tree. The gap vocabulary is fixed:

| Term | Meaning |
|---|---|
| **MATCH** | Code, docs (where they exist), and legacy behaviour are aligned. |
| **APPROVED DEVIATION** | Code intentionally diverges from legacy by a recorded PO decision; doc must explain the delta + cross-link. |
| **DEAD LEGACY** | A legacy (or earlier-refactor) flow was removed and is not coming back; docs must mark it historical, never re-document the removed shape. |
| **PARTIAL** | Capability is partly implemented; some paths live, others deferred. |
| **MISSING** | In legacy / in scope, but no Spring code (or no doc) exists yet. |

> Unlike the Settings / Payment / Add-on passes (where the drift was *stale lines in otherwise-complete docs*), the Common drift is mostly **absent documentation** — only `view-setting` and `audit-log` had a doc before this pass. The dominant gap class here is **D / E (docs missing for live code)**, not contradicted claims.

---

## A. Code exists but documentation is missing / understated

| # | Item | Evidence | Status |
|---|---|---|---|
| A1 | **excel-export** shared pipeline | `common/export/*` (11 classes) + 6 registered providers; `POST /{module}/{resource}/export`; `WBE2010–2013` | **MISSING DOC** — only mentioned in feature specs + CLAUDE.md; no Common module folder. → build `common/excel-export/`. |
| A2 | **excel-import** shared shell + per-feature services | `components/shared/excel-upload/ExcelUploadDialog.tsx` + `templateValidator.ts`; backend `purposesettings`/`vasaccount` preview+import | **MISSING DOC** — pattern undocumented as Common. → build `common/excel-import/`. |
| A3 | **file-management** (upload / download / attachment) | `ContactsFileService`, ERP download, `lib/file.ts`, storage-root policy; `WBE2014/2015`, `WBE_CONTACTS_FILE_*` | **MISSING DOC**. → build `common/file-management/`. |
| A4 | **notification** (LIVE) | `domain/payment/notification/*` on BLTB; `GET /notifications`, `/unread-count`, `PUT /{id}/read`, `/read-all`; `comm_0192_007` | **DOC CONTRADICTS CODE** — see B1 (highest priority). → build `common/notification/`. |
| A5 | **navigation** | `domain/navigation/*` (V242/V243); `GET /api/v1/navigation`; `MenuAuthorizationGuard` (F-3) | **MISSING DOC** in `common/` (design notes exist under `docs/jex/features/menu-product-type-management/`). → build `common/navigation/`. |
| A6 | **common-code-lookup** — Country endpoint | `CountryController` `GET /api/v1/common/countries` (legacy `comm_0110_001_r001`) | **MISSING DOC** — the one Common reference-data REST endpoint is undocumented. → build `common/common-code-lookup/`. |
| A7 | **table-sort** contract | `common/web/{SortDirection,SortableField}`, `hooks/useTableSort.ts`, `TableHeaderCell.tsx` | **DOC IN CLAUDE.md ONLY** — no feature-doc. → build `common/table-sort/`. |
| A8 | **dialog-popup** system | `components/shared/dialog/*`, `components/ui/dialog.tsx` stack ladder, `lib/modalStack.ts` | **DOC IN CLAUDE.md ONLY** (WEB). → build `common/dialog-popup/` (WEB 6-file). |
| A9 | **error-envelope** infra | `common/{response,exception,i18n,entity,security,tenant,tx,legacy}` | **DOC SPREAD ACROSS auth + CLAUDE.md** — no consolidated Common doc. → build `common/error-envelope/` (API-heavy). |

## B. Documentation exists but feature is absent / different in code

| # | Doc claim (current) | Reality (code) | Action |
|---|---|---|---|
| B1 | Common README (API + WEB): Notifications "✅ implemented" via **`domain/notification/`** + **`NOTIFICATION` table (V238)** + 4 endpoints under `/api/v1/notifications` + producers; spec link → `docs/refactor-wecms/features/notifications.md`. | `domain/notification/` + `NOTIFICATION` table were **removed 2026-06-10**. Notification is now `domain/payment/notification/` on **BLTB** tables; endpoints are `GET /notifications`, `GET /unread-count`, `PUT /{id}/read`, `PUT /read-all`; the linked spec is a **dead deprecated path**. | **Corrected this pass** in both READMEs (point to `common/notification/`, BLTB, real endpoints). |
| B2 | Common README scope row: "Notifications → spec in `docs/refactor-wecms/features/notifications.md`". | `docs/refactor-wecms/` is the **deprecated read-only** tree (relocated 2026-06-01); that spec describes the removed shape. | **Link removed / repointed** to the new `common/notification/` folder + `notification-popup-legacy-vs-refactor-analysis.md`. |
| B3 | Common README: "Dashboard — not yet in scope (OQ-DOC-13)". | Still true — no code. | **MATCH** (kept; reclassified as `MISSING`, README note only). |

## C. PO / developer decision required

| # | Item | Why it needs a decision |
|---|---|---|
| C1 | Notification **General branch** (`TYPE='G'`, `GENL_NOTI_*` tables) | Intentionally not ported (out of legacy-table-inventory per the controller Javadoc). Confirm it stays out, or schedule the table-inventory addition + PO sign-off. |
| C2 | Notification **delete** | Legacy `comm_0192_007` exposes no delete; refactor exposes none. A prior inventory mis-stated a `DELETE` endpoint — confirm none is wanted. |
| C3 | `excel-import` shared **backend** service | Today preview/import is re-implemented per feature (Purpose, VAS). Decide whether to extract a shared `common/import/` service (mirrors `common/export/`) or keep per-feature. Doc-only until decided. |
| C4 | Generic **`/api/v1/attachments`** endpoint | File upload/download is currently Settings-`contacts`-specific (`TRCO_INFO_FILE`). Decide whether a generic attachment surface is ever needed (would require a new inventory table + PO sign-off). |
| C5 | `dashboard-common` | DEAD/MISSING. Confirm it remains out of scope (no stub folder) vs. schedule a future feature. |
| C6 | `common-code-lookup` breadth | Only Country has a Common endpoint; Bank/Currency resolve inside domain responses. Decide whether to promote a shared `/api/v1/common/*` lookup surface (banks, currencies, usage codes) or keep them domain-embedded. |

## D. Backend implementation missing / partial

| # | Item | Status |
|---|---|---|
| D1 | Shared `common/import/` backend service | **MISSING** — per-feature today (see C3). |
| D2 | Generic attachment surface | **MISSING** — contacts-specific only (see C4). |
| D3 | Notification General (`TYPE='G'`) branch | **PARTIAL** — BLTB branch live; General branch not ported (C1). |
| D4 | Dashboard endpoints | **MISSING** — no code (C5). |
| D5 | Common lookup surface (banks/currencies/usage) | **PARTIAL** — Country only; rest domain-embedded (C6). |

## E. Frontend implementation missing / partial

| # | Item | Status |
|---|---|---|
| E1 | View Setting popover on addon screens | **Intentionally absent** — addon screenIds are export-only (documented in addon gap analysis E1). |
| E2 | KM (Khmer) i18n in `wecms-web` | **MISSING** — EN/KO/VI only (backend has `messages_km.properties`). |
| E3 | Dashboard shared UI | **MISSING** — feature modules own their own layouts; no shared dashboard shell. |

## F. Test coverage gaps

| # | Area | Status |
|---|---|---|
| F1 | `NotificationService` / `ApprovalNotificationService` | Verify dedicated tests exist; if not, **gap**. |
| F2 | `CountryService` | Likely no dedicated test (trivial read) — low priority. |
| F3 | excel-import per-feature services | `PurposeSettingsImportServiceTest` / `VasAccountImportServiceTest` exist (per addon-era inventory) — confirm. |
| F4 | `ExcelExportService` | `ExcelExportServiceErpAuditTest` exists; broaden coverage across providers. |
| F5 | Common infra | `GlobalExceptionHandlerTest`, `LegacyHashPasswordEncoderTest`, `LogMaskTest`, `RequestResponseLoggingFilterTest`, `RelativeTimeFormatterTest`, `AuditLogServiceTest`, `AuditLogServiceTransactionAnnotationTest`, `ScreenOptionServiceTest`, `NavigationServiceTest` — present (per inventory). |

## G. Documentation-structure hazards

| # | Item | Status |
|---|---|---|
| G1 | **Notification location ambiguity** | Backend lives under `domain/payment/notification/` (not `domain/common/`), yet it is a **Common** feature (global header bell). The Common doc owns the spec; the source-index records the physical package. Do not "move" the doc to payment — it is cross-cutting. |
| G2 | **`audit-log` `WSVC_ID` = slug, not legacy WSVC id** | Override 2026-05-09. Any Common doc citing audit writes must use the refactor slug (`firm-banking-settings`), never `comm_0220_001`. |
| G3 | **Two View Setting identifier namespaces** | `WEB_SVC_ID` (semantic kebab route slug) ≠ legacy JEX page id. The doc must reject legacy `fnsh_0001_001`-style ids. |
| G4 | **Deprecated `docs/refactor-wecms/` links** | Both Common READMEs linked into the deprecated tree (B2). Purge such links when authoring folders. |
| G5 | **CLAUDE.md is the de-facto Common spec** | Most Common rules live in `wecms-api/CLAUDE.md` / `wecms-web/CLAUDE.md`, not in `docs/`. The new folders should *summarize + cross-link* CLAUDE.md, not duplicate it wholesale (CLAUDE.md stays the operational rule source). |

---

## Priority summary

- **Done this pass (docs):** B1, B2 corrected in both Common READMEs (Notification re-implementation + dead-link purge); 3 audit deliverables created.
- **Phase 2 (build folders):** A1–A9 → 11 Common module folders (9-file API / 6-file WEB, per applicability) + convert the two existing flat specs (`view-setting`, `audit-log`) to folders + convert root READMEs to summary/navigation docs with the standard banner.
- **Needs PO:** C1 (General notification branch), C3 (shared import service), C4 (generic attachments), C5 (dashboard), C6 (lookup surface breadth).
- **Test debt:** F1 (notification), F4 (export provider breadth).
- **Out of `docs/` scope but to flag:** none — Common rules in CLAUDE.md are authoritative and stay; the folders cross-link them.
