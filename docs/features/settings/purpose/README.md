# Settings > Purpose — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend (controller + 2 services + 2 repositories + 2 entities + 12 DTOs + V35 schema + V140 `wb_get_next_val` branch, with unit + controller tests) and frontend wired; legacy parity verified; Purpose-specific audit review complete; SIT/UAT verified.
**Module / menu:** Settings → **Purpose** (Settings modal panel — **no standalone route**)
**Refactor slug:** `purpose-settings` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire base path:** `/api/v1/settings/purpose-settings` (list · detail · create · update · delete · `/order` · `/excel/preview` · `/excel/import`)
**Menu permission slug:** `settings-core-purpose`

> This folder is the **primary documentation home** for Purpose on the backend.
> It consolidates and indexes material that previously lived in a single flat file and in the
> reference trees; those files are **preserved and cross-linked** (see
> [§ Related & preserved docs](#related--preserved-docs)), not deleted. The JEX-side functional
> spec lives at [docs/jex/features/purpose/](../../../../../docs/jex/features/purpose/README.md)
> and is the read-only source of truth for legacy behaviour.

---

## What this feature is

Purpose is the per-tenant master list of **transaction purposes** (legacy schema column
`USE_USAG_CD` — "Use/Usage Code"). A purpose tags every payment / transfer / expense write so the
tenant can categorize cash flow for reporting and reconciliation. A tenant administrator can:

- **List / search** active purposes for the current `BIZ_KEY`, ordered by `DISP_ORD`.
- **Create** a purpose — user-visible code (`HAND_USE_USAG_CD`), name (`USE_USAG_NM`), description
  (`USE_USAG_CONT`), optional parent (`PARENT_USE_USAG_CD`).
- **Update** in place (rename, re-describe, re-parent).
- **Soft-delete** by flipping `USE_YN='Y' → 'N'`, blocked when the row or any active child is still in use.
- **Reorder** via a single bulk `(use_usag_cd, disp_ord)` payload.
- **Bulk-upload** from an Excel template through a two-step preview → import flow.

Hierarchy is **fixed at two levels** — a level-1 parent (`PARENT_USE_USAG_CD IS NULL`) may carry
any number of level-2 children, but children never carry grandchildren. The backend enforces this
on every create / update / import.

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 8: GET (list) · GET `/{use_usag_cd}` (detail) · POST (create) · PUT `/{use_usag_cd}` (update) · DELETE `/{use_usag_cd}` (soft-delete) · PUT `/order` (reorder) · POST `/excel/preview` · POST `/excel/import` |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")` — class-level on the controller (F-3 menu-auth rollout, 2026-06-06). See [interface-specification.md §Authorization](interface-specification.md) and the OQ note below. |
| Tenant scope | `TenantContext.currentBizKey()` — `bizKey` is part of the composite PK; never from a request param |
| Table | `BUSI_USAG_SET_INFM` (15 columns, V35) + read-only `VPurposeInUse` `@Subselect` over the same table (no DB view — none was ever provisioned) + `wb_get_next_val` `use_usag_cd` branch (V140) |
| Soft-delete | `USE_YN='N'`; no physical delete (the legacy "permanent delete" path is out of scope — DEAD LEGACY) |
| Delete guard | `WBE1199` when the row has active children **OR** the row / any active child is referenced by an active transaction (recursive, OQ-5) |
| Hierarchy | exactly two levels; parent must be an active level-1 row; self-parent + demote-with-children forbidden |
| PK generation | `wb_get_next_val('busi_usag_set_infm','use_usag_cd', biz_key, '')` → 10-digit zero-padded |
| Excel | two-step `preview` (POI parse + validate, ≤500 rows) → `import` (atomic, roots-first append) |
| Audit | every Create / Update / Delete / Reorder writes `AUDIT_LOGS_INFM` with `WSVC_ID = 'purpose-settings'` (slug). Reads + Excel preview do not audit. |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.purposesettings
├── controller/PurposeSettingsController.java        8 endpoints; class-level @menuAuthz gate
├── service/
│   ├── PurposeSettingsService.java                  list/detail/create/update/delete/reorder; soft-delete; tree assembly; audit
│   └── PurposeExcelService.java                     preview() + import_(); POI parse, ≤500 rows, per-cell validation
├── repository/
│   ├── PurposeSettingsRepository.java               active-scope queries, dup checks, child checks (PK-gen via LegacyFunctionRepository funnel, not native here)
│   └── PurposeInUseRepository.java                   in-use exists-check + bulk lookup
├── entity/
│   ├── BusiUsagSetInfm.java                          @Table(BUSI_USAG_SET_INFM), 15 cols, @IdClass
│   ├── BusiUsagSetId.java                            composite PK { bizKey, useUsagCd }
│   └── VPurposeInUse.java                            @Immutable @Subselect projection (no DB view)
└── dto/
    ├── PurposeSettingListResponse.java · PurposeSettingRow.java (tree) · PurposeSettingDetail.java
    ├── PurposeSettingCreateRequest.java · PurposeSettingUpdateRequest.java
    ├── PurposeReorderRequest.java · PurposeReorderItem.java
    └── PurposeExcelPreview.java · PurposeExcelPreviewRow.java · PurposeExcelFieldError.java
        · PurposeExcelImportRequest.java · PurposeExcelImportResult.java
```

Flyway: [V35](../../../../src/main/resources/db/migration) `BUSI_USAG_SET_INFM` schema (column-for-column DEV parity) · [V36](../../../../src/main/resources/db/migration) `BUSI_USAG_SET_INFM_LANG_DETL` schema (legacy lang-detail table — present in the inventory, but **not** an entity in this feature; single-table model) · [V140](../../../../src/main/resources/db/migration) the centralized `wb_get_next_val` carries the `busi_usag_set_infm.use_usag_cd` branch (no-group plain 10-digit, branch #19; grouped companion branch #18), PK-gen funnels through `LegacyFunctionRepository.nextUseUsagCd`. There is **no** `v_purpose_in_use` view migration — the in-use projection is a Hibernate `@Subselect` only. (The earlier V80–V82 numbering in this doc predated the 2026-06-09 migration renumber/consolidation and was stale.)

---

## Legacy & Migration Traceability

**Legacy JEX counterpart:** WSVC family `comm_0187_001` (+ `_r002` / `_c001` / `_u002` / `_u004`) and `comm_0187_004` (Excel). Table `BUSI_USAG_SET_INFM`.

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: search → controller → service → repository/view → tree response → grid → create/edit/delete/reorder → Excel preview/import |
| [functional-specification.md](functional-specification.md) | Operations matrix, field-by-field validation, hierarchy rules, soft-delete + recursive in-use guard, reserved system codes, audit expectations |
| [database-mapping.md](database-mapping.md) | `BUSI_USAG_SET_INFM` columns, the `VPurposeInUse` `@Subselect` (no DB view), PK-gen function, and the full legacy-column → entity-field → DTO-field → UI-field mapping |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint, error-code registry, authorization, wire envelope |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (JSP / `_act.jsp` / IDO / WSVC / entity / DAO) became its Spring counterpart, incl. dropped/dead legacy flows |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios + the existing JUnit coverage map |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with completion status + closed-gap evidence |

---

## Related & preserved docs

These existing docs are **preserved** and remain authoritative for their slice. This README is the
entry point; deep detail lives here:

- **Detailed phase-by-phase implementation spec** (operations matrix, field contract, OQ decisions
  OQ-1…OQ-8 + OQ-FE-1/2): [../purpose.md](../purpose.md) — the original ~480-line spec, kept as the
  deep design narrative.
- **Business rules / service design:** [references/business-rules/settings/purpose-settings.md](../../../references/business-rules/settings/purpose-settings.md)
- **DB schema design:** [references/database/settings/purpose-settings-schema.md](../../../references/database/settings/purpose-settings-schema.md)
- **JEX → Spring mapping:** [references/migration/purpose-settings.md](../../../references/migration/purpose-settings.md)
- **Parity / audit review:** [references/audits/purpose-settings-audit.md](../../../references/audits/purpose-settings-audit.md) · [audits/purpose-refactor-parity-audit.md](../../../audits/purpose-refactor-parity-audit.md)
- **Task plan (archived):** archive/planning/settings/purpose-settings-task-plan.md _(removed — historical `archive/` folder deleted by project decision)_
- **JEX legacy spec set (read-only):** [docs/jex/features/purpose/](../../../../../docs/jex/features/purpose/README.md)
- **Frontend feature folder:** [wecms-web/docs/features/settings/purpose/](../../../../../wecms-web/docs/features/settings/purpose/README.md)
- **Settings ERD:** [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md)

> **Note on `docs/refactor-wecms/`:** triplicate copies of the migration / schema / audit / backend
> docs exist under the deprecated workspace-level `docs/refactor-wecms/` tree. That tree is
> **read-only legacy** per the workspace CLAUDE.md; the canonical copies are the `wecms-api/docs/...`
> paths linked above.

---

## Open item — authorization annotation vs. its Javadoc

The controller carries `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")` at the class
level ([PurposeSettingsController.java:69](../../../../src/main/java/com/kosign/wecms/domain/setting/purposesettings/controller/PurposeSettingsController.java)).
This is the **live, authoritative** gate and matches the F-3 server-side menu-authorization rollout
(2026-06-06; see `wecms-api/CLAUDE.md` Authority model). The method-level Javadoc just above it still
reads "Per OQ-6 the controller carries no `@PreAuthorize`" — that comment is **stale** and predates
the F-3 change. Documentation here reflects the real annotation; the stale comment is logged as a
non-blocking cleanup in [refactor-gap-analysis.md](refactor-gap-analysis.md) (Authorization Parity).
