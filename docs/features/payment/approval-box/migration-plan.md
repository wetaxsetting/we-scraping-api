# Approval Box — Migration Plan (JEX → Spring Boot + Next.js)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

**Feature slug:** `approval-box`
**Module:** `payment`
**Date:** 2026-05-13
**Status:** Plan only — no code in `wecms-api/` or `wecms-web/` for this MR yet.

Companion docs: [discovery.md](discovery.md), [design-backend.md](design-backend.md), [design-frontend.md](design-frontend.md), [database-impact.md](database-impact.md), [api-flow.md](api-flow.md).

> **Status update (2026-06-02) — this is a historical plan; several "Deferred" rows below have since shipped.**
> Implemented & verified beyond Phase 1: detail popup (Phase 2), approve/reject (Phase 3), withdraw/delete + change-approval-line for non-card types and card (Phase 4 scope, via the generic endpoints + `ApprovalBoxFanoutService`), and bulk-approve (backend; UI deferred). Excel export now returns **all** matching rows (bounded by the shared 50k cap), not the original single-page cap. The File-Name column sources `COALESCE(SCDUL.ORG_FILE_NM, OVRS.ORG_FILE_NM)` — superseding the §1.1 "search hits raw TITL only" and the [design-backend.md](design-backend.md) §13 D1 file-name deferral. Default page size is **10** by project decision (not the legacy 15). Still deferred / out of scope: **ERP synchronization, approval notification/comment creation, uploader visibility branch, Vietnamese translated keyword search**.
> Single source of truth for the post-Phase-1 behaviors, decisions, and the SIT checklist: [parity-fixes-2026-06.md](../../../audits/historical/approval-box-parity-fixes-2026-06.md). The phase tables below are retained as the original plan-of-record.

---

## 1 · JEX-vs-Spring component mapping

> Legacy WSVC / file ids are JEX **citations** only — never identifier surfaces in the refactor. Slug `approval-box` everywhere on the new side.

### 1.1 · API mapping

| JEX (read-only reference) | Refactor (slug-based) | Status |
|---|---|---|
| WSVC `resl_0001_001_r001`, [WSVC.resl_0001_001_r001.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.resl_0001_001_r001.xml) | `GET /api/v1/payment/approval-box` | **In scope** — this MR. |
| Action [resl_0001_001_r001_act.jsp:280-352](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L280) (controller equiv.) | `ApprovalBoxController.list` | In scope. |
| Action `_act.jsp:54-225` (validate + dynamic SQL + IDO call) | `ApprovalBoxService.list` + `ApprovalBoxRepository.findApprovalBoxList` | In scope. |
| IDO `EXPT_RESOL_MSTR_R003`, [IDO.EXPT_RESOL_MSTR_R003.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.EXPT_RESOL_MSTR_R003.xml) | Single JPQL in `ApprovalBoxRepository.findApprovalBoxList` | In scope. |
| IDO `EXPT_RESOL_MSTR_R003_CNT`, [IDO.EXPT_RESOL_MSTR_R003_CNT.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.EXPT_RESOL_MSTR_R003_CNT.xml) | `ApprovalBoxRepository.fetchTabCounts` + `fetchCurrencyTotals` | In scope. |
| Client-side XLSX builder ([resl_0001_001_osmc.ts:299-306](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L299)) | `POST /api/v1/payment/approval-box/export` + `ApprovalBoxExportProvider` | In scope. |
| WSVC `resl_0001_001_d001` (delete) | `DELETE /api/v1/payment/approval-box/{apprSeqNo}` | **Deferred** — Phase 4. |
| `comm_0195_001` (detail popup read) | `GET /api/v1/payment/approval-box/{apprSeqNo}` | **Deferred** — Phase 2. |
| `comm_0195_001_uxxx` (approve / reject) | `POST /api/v1/payment/approval-box/{apprSeqNo}/approve` + `…/reject` | **Deferred** — Phase 3. |
| (no JEX equivalent) | `POST /api/v1/payment/approval-box/bulk-approve` | **Deferred — pending OQ-2.** |
| File download (owned by `comm_0192_*`) | `GET /api/v1/payment/approval-box/{apprSeqNo}/files/{fileId}/download` | **Deferred** — bundled with Phase 2 detail port. |

### 1.2 · Entity / DAO mapping

| JEX | Refactor | Notes |
|---|---|---|
| `wabooks.entity.ExptResolMstr` | `com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr` | Column-for-column parity ([design-backend.md](design-backend.md) §8). |
| `wabooks.entity.ExptResolMstrPk` | `…entity.ExptResolMstrId` (composite `@IdClass`) | |
| `wabooks.entity.ExptResolApprvrInfm` | `…entity.ExptResolApprvrInfm` | Same. |
| `wabooks.entity.ExptResolApprvrInfmPk` | `…entity.ExptResolApprvrInfmId` | |
| `wabooks.entity.ExptResolFile` | `…entity.ExptResolFile` | Used by detail-port MR. |
| `wabooks.entity.ExptResolPayEvidFile` | `…entity.ExptResolPayEvidFile` | Used by detail-port MR. |
| `wabooks.dao.ExptResolMstrDAO` | `…repository.ApprovalBoxRepository` (Spring Data JPA) | Method-derived queries + one `@Query` for the big list. |
| `wabooks.dao.ExptResolApprvrInfmDAO` | (folded into `ApprovalBoxRepository`) | One `@Query` for the chain fetch. |
| `wabooks.helper.ReslHelper` | (dissolved into service) | No reusable helper needed in the refactor — the math is trivial. |
| `wabooks.helper.SortHelper` | `ApprovalBoxSortField` enum + Spring `Sort.by(...)` | |
| `wabooks.helper.LocaleHelper` | `TenantContext.currentLangCd()` | |
| `wabooks.helper.ValidationHelper` | Bean Validation + service `validatePeriod` | |
| DB helper `wb_get_appr_stts` | `ApprovalBoxService.deriveApprStts` | OQ-3 option (a) — Java derivation. |
| DB helper `wb_get_expt_resol_mstr_doc_nm` | Java enum `ApprDv` + FE i18n on `type_label` | |
| DB helper `wb_get_memo_cont` / `wb_get_memo_color` | **Dropped** | Not in OSMC layout. |
| DB helper `wb_get_translate_text` | **Dropped** | Search hits raw `TITL` only. |

### 1.3 · Frontend mapping

| JEX | Refactor | Notes |
|---|---|---|
| View [resl_0001_001_view_osmc.jsp](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp) | Next.js route `/payment/approval-box` already exists at [wecms-web/app/(dashboard)/payment/approval-box/page.tsx](<../../../../../wecms-web/app/(dashboard)/payment/approval-box/page.tsx>) | This MR wires it to the real API. |
| TS [resl_0001_001_osmc.ts](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts) (AJAX + render + hover) | `components/payment/approval-box/*` + `lib/api/approvalBox.ts` | Existing static-UI components are correct; the MR adds data + mapper layer. |
| ML codes `resl_0001_001_m001..m008`, `_kh*`, `_ml*` | `approval_box.*` keys in `locales/{en,ko,vi}.json` | [design-frontend.md](design-frontend.md) §8. |
| ML codes `resl_0001_001_j033..j047` (status / type labels) | `approval_box.status.*` / `approval_box.type.*` | |
| View Setting `WEB_SVC_ID = 'resl_0001_001'` (legacy registry retired in V32) | `SCREEN_IDS.APPROVAL_BOX = "approval-box"` in `lib/viewSettingScreens.ts` | Per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"Routing". |

### 1.4 · Schema mapping

| JEX table | Refactor entity / migration | Status |
|---|---|---|
| `EXPT_RESOL_MSTR` | `ExptResolMstr` entity + V{N+0} migration | **In scope** — schema parity port. |
| `EXPT_RESOL_APPRVR_INFM` | `ExptResolApprvrInfm` entity + same migration | In scope. |
| `EXPT_RESOL_FILE` | DDL only (entity in detail-port MR) | DDL in scope to satisfy FK. |
| `EXPT_RESOL_PAY_EVID_FILE` | DDL only | DDL in scope to satisfy FK. |
| `EXPT_RESOL_APPR_LINE` | Owned by Settings → Approval Line port | **Deferred.** |
| `BLTB_POST_INFM` | Minimal column projection in same migration | Full port owned by Bulletin feature MR. |
| `OVRS_PYMN_MSTR` / `OVRS_PYMN_SUMR` | Minimal projection | Full port owned by Overseas Payment feature MR. |
| `SCDUL_PYMN_SUMR` | Minimal projection | Full port owned by Scheduled Payment feature MR. |
| `CARD_TRNS_HSTR` | Already present from V58/V72 demo seed | No DDL change. |
| `BUSA_DOC_MSTR` / `SALY_BOOK_MSTR` | Minimal projection (for `PAY_YN` subquery) | Full port owned by respective payment features. |

---

## 2 · Phased order of work (cut-over points)

The Approval Box port is large enough to split into four phases. **This MR is Phase 1** — read-only list + Excel export. Each phase lands as its own GitLab MR pair (api + web).

### Phase 1 — Read-only list + Excel export  (this MR)

**Scope:**
- Flyway migration (V84-or-next) with schema-parity DDL for the four EXPT_RESOL_* tables + minimal projections of joined siblings + View Setting seed.
- Backend: `ApprovalBoxController` + `ApprovalBoxService` + `ApprovalBoxRepository` + entities + DTOs + `ApprovalBoxSortField` + `ApprovalBoxExportProvider`. Error codes `WBE_APPROVAL_BOX_INVALID_PERIOD` / `_INVALID_SORT` registered in `messages.properties`.
- Frontend: existing `/payment/approval-box` route migrated from mock to real data; `lib/api/approvalBox.ts` + wire types in `lib/types/api.ts` + mappers in `lib/api/mappers.ts` + new approver hover card. View Setting screenId registered.
- Real DEV seed (~325 rows) shipped as a separate `Claude Code (AI)/sql/` script.

**Acceptance gate (cut-over to Phase 2):**
- `./gradlew build` green on api side.
- `npm run lint && npm run dev` green on web side.
- Manual browser walk-through: open `/payment/approval-box`, change tab → counts switch, change date range → rows reload, search → narrows, sort each column → server-side sort works, Excel icon → file downloads with sub-header.
- Tab counts ignore current tab; row list respects current tab (D9 parity).
- Non-admin user sees only their own / their-approval rows (Redmine #42046 parity).

### Phase 2 — Detail popup (read-only)

**Scope:**
- `GET /api/v1/payment/approval-box/{apprSeqNo}` returning header + per-type transaction rows + files + approver chain.
- Entity classes for `ExptResolFile` + `ExptResolPayEvidFile` already exist from Phase 1 DDL; full entity body lands here.
- Frontend: `ApprovalDetailsModal` component, row-click handler wired in `ApprovalBoxPage`.
- File download endpoint `GET /api/v1/payment/approval-box/{apprSeqNo}/files/{fileId}/download` reusing the firm-banking-history / erp-transaction-history download precedent.

**Cut-over gate:** Manual: row click → modal opens with all sections, file links download.

### Phase 3 — Approve / Reject

**Scope:**
- `POST /…/approve` and `POST /…/reject` with audit log (`WSVC_ID = "approval-box"`).
- Status badge updates locally + via `refetch list`.
- Error code `WBE_APPROVAL_BOX_NOT_YOUR_TURN`.
- Pre-conditions: only `apprvr_stts = 'P'` AND all prior steps are `'A'` AND the user is the next-step approver.

**Cut-over gate:** End-to-end test on local DB: admin user creates an approval-request row + 3 approvers (via direct SQL), each approver logs in via JWT, approves in turn, status flows `P → I → I → C`. Rejection short-circuits to `R` and downstream stops to `S`.

### Phase 4 — Delete + bulk-approve (conditional on OQ-2)

**Scope:**
- `DELETE /api/v1/payment/approval-box/{apprSeqNo}` mirroring [resl_0001_001_d001_act.jsp](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_d001_act.jsp) cascade.
- `POST /…/bulk-approve` IF PO confirms.

**Cut-over gate:** Audit log written on each delete. Cascade integrity verified on local DB (orphan approvers / files all removed).

---

## 3 · Backend-before-frontend rule

Per [migration-playbook.md](../../../references/migration/migration-playbook.md) Step 5/6 — backend lands first inside the same MR pair. The static-UI page in `wecms-web/components/payment/approval-box/` continues to render mocks until the backend `GET /api/v1/payment/approval-box` is reachable on `localhost:8090`. The frontend half of Phase 1 (real fetch + mapper + types) lands together with the backend; mock data stays in the file system but is not imported anywhere after the swap.

---

## 4 · Risk register

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Migration slot conflict** — last applied is V83, but other in-flight MRs may grab V84 before this one. | Low | Lock the slot in merge sequencing; the SQL content is independent of slot number. |
| R2 | **Status derivation drift** — Java `deriveApprStts` could diverge from legacy `wb_get_appr_stts(...)`. | Medium | Pin to OQ-3 option (a); add a parity test that compares Java derivation against DEV-on-DB function for the 325 seeded rows. |
| R3 | **Sibling-table coupling** — minimal projections of `BLTB_POST_INFM` / `OVRS_PYMN_MSTR` / `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` clash with later full-port MRs. | Medium | `CREATE TABLE IF NOT EXISTS` + later `ALTER TABLE … ADD COLUMN IF NOT EXISTS`. Document the precedent in the migration's trailing comment. |
| R4 | **Per-user visibility filter** — getting the Redmine #42046 visibility rule wrong is a security boundary regression. | High | Mirror the JEX `EXISTS` blocks exactly. Manual test: log in as non-admin user, confirm rows from other users do not appear. |
| R5 | **Performance: N+1 approver chain fetch** — page-size 100 × per-row query for `EXPT_RESOL_APPRVR_INFM`. | Medium | Acceptable in MR1 (chain is rarely >5 rows). If profiling shows hotspot, switch to one JOIN + result-set mapping. |
| R6 | **Approval-line cascade** — Phase 3 reject must mark downstream approvers as `S` ('Unable, blocked-by-reject'); legacy logic is non-trivial. | High | Phase 3 design doc will trace the exact SQL from `comm_0195_001_uxxx_act.jsp`. Out-of-scope for Phase 1. |
| R7 | **Audit gaps** — Phase 1 writes no audit (read-only is fine), but Phase 3 must. Forgetting it would break compliance traceability. | High | Phase 3 acceptance gate verifies `AUDIT_LOGS_INFM` row written with `WSVC_ID='approval-box'` after each approve / reject. |
| R8 | **Currency-column collapse delta (D1)** — frontend code reading `tot_pay_khr_amt` somewhere else will break. | Low | Wire-name search confirms `tot_pay_khr_amt` is not referenced anywhere outside the legacy WECMS DEV tree. New refactor uses `tot_pay_amt`. |
| R9 | **Tab count = 5 vs 6** (OQ-1) — static UI ships 6 tabs; legacy ships 5. | Low | Default: drop `draft` until PO confirms. UI change is small. |
| R10 | **Sort-whitelist drift** — frontend sends `field_id`s; if backend whitelist gets out of sync, sort fails silently. | Medium | View Setting seed in the migration sets `FIELD_ID` values; the same constants live in `ApprovalBoxSortField` enum + `SCREEN_IDS.APPROVAL_BOX` registry. Cross-tree consistency check in Step 7 of MIGRATION_PLAYBOOK. |
| R11 | **Helper-function policy** — accidentally introducing a new `wb_*` PL/pgSQL helper would break the policy. | High | Refactor reimplements every helper in Java (D2-D5). Code review checklist explicitly flags any `CREATE FUNCTION` in the migration. |
| R12 | **Multi-language coverage** — KO/VI string fidelity. | Medium | Phase 1 uses Jex Studio's currently-registered KO/VI strings as the source for `approval_box.*` entries — copy verbatim. KH (`km.json`) deferred. |
| R13 | **`pymn_0001_001` typo in JEX docs** — could mislead future readers. | Low | Filed as a `docs/bug-reports/` follow-up; not blocking. |

---

## 5 · Test plan (Phase 1)

| Test | Surface | Pass criteria |
|---|---|---|
| Unit: `ApprovalBoxService.validatePeriod` | api | throws `WBE_APPROVAL_BOX_INVALID_PERIOD` on `fromDt > toDt`. |
| Unit: `ApprovalBoxService.deriveApprStts` | api | exhaustive `appr_type_cd` cases → expected `P/I/C/R`. |
| Unit: `ApprovalBoxSortField.valueOfWhitelisted` | api | rejects unknown values with `WBE_APPROVAL_BOX_INVALID_SORT`. |
| Integration: `GET /api/v1/payment/approval-box` against local DB | api | Returns 200, `data.total_count` matches direct SQL count. |
| Integration: tab filter | api | `appr_type_cd=01` returns only `appr_type_cd='01'` rows. |
| Integration: visibility filter | api | non-admin JWT only sees rows where user ∈ {requestor, approver, uploader of underlying SCDUL/OVRS/CARD row}. |
| Integration: sort | api | `sort_field=DOC_NO&sort_direction=ASC` returns lex-ordered rows. |
| E2E (browser, manual): page load | web | rows + tabs + totals render; existing UI baseline preserved. |
| E2E (browser, manual): tab switch | web | row list refreshes; tab counts do NOT recompute (per D9). |
| E2E (browser, manual): Excel download | web | `.xlsx` file downloads; first sheet has the seeded columns + sub-header. |

Tests run locally via `./gradlew test` (api) and `npm run lint && npm run dev` (web). No production deploy (R&D rule).

---

## 6 · MR (merge request) checklist for Phase 1

GitLab branch convention: `feature/approval-box` on both `wecms-api` and `wecms-web`. Cross-link in MR descriptions.

**API MR description:**
```
Feature: Approval Box list endpoint (slug: approval-box)

Mirrors: WECMS DEV resl_0001_001 (Approval Box list page).

In scope:
- GET /api/v1/payment/approval-box
- POST /api/v1/payment/approval-box/export
- Flyway V{N} (schema-parity port + View Setting seed)
- New error codes: WBE_APPROVAL_BOX_INVALID_PERIOD, _INVALID_SORT

Deferred (follow-up MRs): detail, approve, reject, delete, bulk-approve.

Acceptance:
[x] ./gradlew build green
[x] flyway migration applies cleanly on fresh local DB
[x] integration tests for list + tab counts + visibility filter pass
[x] cross-tree slug check: SCREEN_IDS.APPROVAL_BOX = "approval-box" matches WEB_SVC_ID seed

Cross-MR link: kosign-wecms/wecms-web!{web-MR-id}
```

**Web MR description:**
```
Feature: Approval Box wire-data (slug: approval-box)

Migrates /payment/approval-box from static mock to GET /api/v1/payment/approval-box.

In scope:
- lib/api/approvalBox.ts (new)
- lib/api/mappers.ts (new exports: mapApprovalRow, mapApprovalBoxList)
- lib/types/api.ts (new ApiApprovalBox* interfaces)
- components/payment/approval-box/* (page bound to API; new ApproverHoverCard)
- lib/viewSettingScreens.ts (new SCREEN_IDS.APPROVAL_BOX)
- locales/{en,ko,vi}.json (new approval_box.* keys)

Acceptance:
[x] npm run lint green
[x] npm run dev — open /payment/approval-box, verify rows, tabs, totals, sort, Excel
[x] dialog variants per CLAUDE.md (warning for invalid period, error for network)

Cross-MR link: kosign-wecms/wecms-api!{api-MR-id}
```

---

## 7 · Open Questions for PO (Mr. Jung)

Summarised from [discovery.md](discovery.md) §10 — kept here in plan form so the PO sees the decisions blocking the start of code.

- **OQ-1 — 5 tabs or 6?** Default: drop `draft` from refactor (parity with live JEX).
- **OQ-2 — Bulk approve in scope?** Default: defer to Phase 4 alongside delete; revisit when detail popup ports.
- **OQ-3 — Status derivation source.** Default: option (a), Java service derivation (no new `wb_*` helper).
- **OQ-4 — Audit policy on list path?** Confirmed (no audit for list path); audit kicks in at Phase 3 with `WSVC_ID="approval-box"`.
- **OQ-5 — Sort whitelist trim?** Default: mirror the legacy frontend `data-sort` set (10 fields).
- **OQ-6 — Excel sub-header format?** Default: `"Inquiry Period (<from> ~ <to>)"` + total count + currency totals — match the legacy line at [resl_0001_001_osmc.ts:300-306](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L300) verbatim.
- **OQ-B1 (backend)** — Wire response: keep both `appr_dv` digit and `type_label` slug. Default: yes for one MR; later MR can drop the digit.
- **OQ-B2 (backend)** — Tab counts respect visibility filter? Default: yes (parity).

---

## 8 · Cross-references

- [discovery.md](discovery.md) — JEX entry points + DB inspection.
- [design-backend.md](design-backend.md) — Spring contract.
- [design-frontend.md](design-frontend.md) — Next.js contract.
- [database-impact.md](database-impact.md) — Flyway draft.
- [api-flow.md](api-flow.md) — sequence diagrams.
- [docs/refactor-wecms/MIGRATION_PLAYBOOK.md](../../../references/migration/migration-playbook.md) — 10-step playbook.
- [docs/refactor-wecms/migration/erp-transaction-history-mapping.md](../../../references/migration/erp-transaction-history-mapping.md) — closest precedent (read-only list + tabs + Excel).
- [docs/refactor-wecms/migration/firm-banking-settings-mapping.md](../../../references/migration/firm-banking-settings-mapping.md) — canonical mapping format reference.
