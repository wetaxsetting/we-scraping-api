# Settings > Approval Line — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend (controller + service + 2 repositories + 2 entities + 6 DTOs + V6/V7 schema + V140 `wb_get_next_val` branch, with a 12-case unit test) and frontend wired (no mock fallback); legacy parity verified; AL-GAP-01 (drag-and-drop reorder) / AL-GAP-02 (test coverage) / AL-GAP-03 (auth gate) all **RESOLVED**; SIT/UAT verified.
**Module / menu:** Settings → **Approval Line** (Settings modal panel — **no standalone route**)
**Refactor slug:** `approval-line` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire base path:** `/api/v1/settings/approval-line` (list · detail · create · update · delete)
**Menu permission slug:** `settings-core-approval-line`

> This folder is the **primary documentation home** for Approval Line on the backend.
> It consolidates and indexes material that previously lived in a single flat file and in the
> reference trees; those files are **preserved and cross-linked** (see
> [§ Related & preserved docs](#related--preserved-docs)), not deleted. The JEX-side functional
> spec lives at [docs/jex/features/approval-line/](../../../../../docs/jex/features/approval-line/README.md)
> and is the read-only source of truth for legacy behaviour.

---

## What this feature is

An Approval Line is a tenant-scoped, **named, ordered chain of 1–5 approvers** that a company
attaches to a payment / transfer / expense request. This feature owns only the **line
configuration** — the master/detail CRUD over `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL`. The
runtime "Approval Box" execution flow (per-document approve/reject state) is owned by other
tables and lands with each consumer feature, not here ([functional-specification.md §Scope](functional-specification.md#scope-boundary)).

A tenant user (no admin gate — see Authority below) can:

- **List / search** approval lines for the current `BIZ_KEY`, name-filtered (case-insensitive),
  each row carrying its full approver chain inline (ordered by `APPRVR_DISP_ORD ASC`).
- **Create** a line — name + 1–5 approvers picked from the company directory.
- **View / Edit** a line — rename + add / remove / **reorder** approvers (server-side diff-sync
  against the existing detail rows).
- **Delete** a line — hard delete of the master row and its detail rows in one transaction.

Reorder is a **drag-and-drop** affordance in the Edit/Add dialog; the order the user drags into is
the order persisted — the backend re-numbers `APPRVR_DISP_ORD` `1..N` from request-array position
on **both** Create and Update ([functional-specification.md §Display-order rule](functional-specification.md#display-order-normalisation)).

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 5: GET (list) · GET `/{appr_line_no}` (detail) · POST (create) · PUT `/{appr_line_no}` (update) · DELETE `/{appr_line_no}` (delete) |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")` — **class-level** on the controller (F-3 menu-auth rollout, 2026-06-06). No admin-only gate; any tenant user with the menu grant. See the OQ note below. |
| Tenant scope | `TenantContext.currentBizKey()` — `bizKey` is part of both composite PKs; never from a request param |
| Tables | `APPR_LINE_INFM` (7 cols) + `APPR_LINE_INFM_DETL` (11 cols) — V6 / V7; no FK constraint (DEV parity), cascade is service-layer |
| Approver cap | **5** — `ApprovalLineConstants.MAX_APPROVERS=5` + bean validation `@Size(max=5)` + service guard → `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` (legacy `WBW1164` parity); also pinned at the DB level by `APPRVR_DISP_ORD NUMERIC(1,0)` |
| Diff-sync (Update) | UPDATE rows in `existing ∩ incoming`, INSERT net-new, DELETE net-removed — one `@Transactional` (mirrors legacy `comm_0222_003_u001_act.jsp:104-194`) |
| Display order | `APPRVR_DISP_ORD` re-numbered `1..N` from request-array position on **Create AND Update** (stricter than legacy Create which trusts client order — OQ-4) |
| Tenant-link guard | every `apprvr_id` verified against `USER_BUSI_CONN_INFM` (active link) → `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` (refactor addition; legacy has no such guard) |
| PK generation | `wb_get_next_val('appr_line_infm','appr_line_no', biz_key)` → per-tenant `MAX+1` (V140 branch) |
| Pagination | **none** — load-all (legacy `comm_0222_001_r001` disables pagination; `total_count = rows.size()`) |
| Audit | **none** — no `AUDIT_LOGS_INFM` write (legacy parity: the five `comm_0222_*` JSPs write no audit rows either; OQ-6 deliberate divergence from `firm-banking-settings` / `contacts`) |
| View Setting | **not adopted** — legacy panel has no toolbar list-icon button (same precedent as `erp-transaction-history`) |
| `user_img_url` | resolved per-approver via `USER_BUSI_CONN_INFM.USER_IMG`; emits slug URI `/api/v1/users/{apprvrId}/image` when present, else `null` (streaming endpoint itself out of scope — OQ-1) |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.approvalline
├── controller/ApprovalLineController.java       5 endpoints; class-level @menuAuthz gate
├── service/ApprovalLineService.java             list/detail/create/update/delete; diff-sync;
│                                                 disp_ord normalisation; tenant-link guard;
│                                                 user_img_url resolution; NO audit
├── repository/
│   ├── ApprovalLineRepository.java              findByBizKey(+Sort) · findBy…NmContainingIgnoreCase
│   └── ApprovalLineApproverRepository.java       ordered detail/batch loads · bulk delete-by-FK
├── entity/
│   ├── ApprovalLine.java                         @Table(APPR_LINE_INFM), 7 cols, @IdClass; APPR_LINE_NO maps Long via @JdbcTypeCode(NUMERIC) over NUMERIC(15,0)
│   ├── ApprovalLineId.java                        composite PK { bizKey, apprLineNo }
│   ├── ApprLineInfmDetl.java                      @Table(APPR_LINE_INFM_DETL), 11 cols, @IdClass; APPR_LINE_NO Long + APPRVR_DISP_ORD Integer, both @JdbcTypeCode(NUMERIC)
│   └── ApprLineInfmDetlId.java                    composite PK { bizKey, apprLineNo, apprvrId }
├── dto/
│   ├── ApprovalLineListResponse.java · ApprovalLineRow.java · ApprovalLineApproverRow.java
│   ├── ApprovalLineCreateRequest.java · ApprovalLineUpdateRequest.java · ApprovalLineApproverInput.java
│   └── ApprovalLineSortField.java                 sort whitelist enum (REG_DTM / APPR_LINE_NM / MOD_DTM)
└── ApprovalLineConstants.java                     SLUG = "approval-line"; MAX_APPROVERS = 5
```

> **Class-naming note.** The master entity is the slug-derived `ApprovalLine` (not the legacy
> `ApprLineInfm`); the detail entity keeps the legacy name `ApprLineInfmDetl` / `ApprLineInfmDetlId`.
> The `@Table` / `@Column` annotations carry the legacy `UPPER_SNAKE` identifiers, per the API naming rule.

Flyway: [V6](../../../../src/main/resources/db/migration/V6__appr_line_infm.sql) `APPR_LINE_INFM` schema · [V7](../../../../src/main/resources/db/migration/V7__appr_line_infm_detl.sql) `APPR_LINE_INFM_DETL` schema · `wb_get_next_val('appr_line_infm','appr_line_no',…)` branch lives in the consolidated [V140](../../../../src/main/resources/db/migration/V140__wb_get_next_val.sql) function. No `NUMERIC → BIGINT/INTEGER` hotfix exists — the columns stay `NUMERIC(15,0)` / `NUMERIC(1,0)` and the entities map onto them via `@JdbcTypeCode(SqlTypes.NUMERIC)`.

---

## Legacy & Migration Traceability

**Legacy JEX counterpart:** WSVC family `comm_0222_001` (+ `_r001` / `_d001`), `comm_0222_002` (+ `_c001`), `comm_0222_003` (+ `_r001` / `_u001`). Tables `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL`.

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: search → controller → service → repository → inline-approver response → grid → create/edit/delete → drag-reorder → diff-sync save |
| [functional-specification.md](functional-specification.md) | Operations matrix, field-by-field validation, scope boundary, display-order normalisation, diff-sync, tenant-link guard, snapshot semantics, delete semantics, audit/permission stance |
| [database-mapping.md](database-mapping.md) | `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL` columns, PKs, indexes, PK-gen function, and the full legacy-column → entity-field → DTO-field → UI-field mapping |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint, error-code registry, authorization, wire envelope |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (JSP / `_act.jsp` / IDO / WSVC / entity / DAO) became its Spring counterpart, incl. dropped/dead legacy flows |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios + the existing JUnit coverage map |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / PARTIAL / MISSING) with completion status + closed-gap evidence (AL-GAP-01/02/03) |

---

## Related & preserved docs

These existing docs are **preserved** and remain authoritative for their slice. This README is the
entry point; deep detail lives here:

- **Detailed refactor feature spec** (14 sections, OQ-1…OQ-7 decision history):
  [../approval-line.md](../approval-line.md) — the original ~390-line spec, kept as the deep design narrative.
- **Business rules / REST design:** [references/business-rules/approval-line.md](../../../references/business-rules/approval-line.md)
- **JEX → Spring mapping:** [references/migration/approval-line.md](../../../references/migration/approval-line.md) · [jex-analysis](../../../references/migration/jex-analysis/approval-line-add-flow-analysis.md)
- **Phase plan (archived):** archive/planning/phase-1/approval-line-plan.md _(removed — historical `archive/` folder deleted by project decision)_
- **JEX legacy spec set (read-only):** [docs/jex/features/approval-line/](../../../../../docs/jex/features/approval-line/README.md) — incl. [refactor-audit.md](../../../../../docs/jex/features/approval-line/refactor-audit.md) (GO status + AL-GAP resolutions)
- **JEX approval business rules (workflow, has stale WSVC ids):** [docs/jex/business-rules/approval.md](../../../../../docs/jex/business-rules/approval.md)
- **Frontend feature folder:** [wecms-web/docs/features/settings/approval-line/](../../../../../wecms-web/docs/features/settings/approval-line/README.md)
- **Settings ERD:** [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md)

> **Note on `docs/refactor-wecms/`:** triplicate copies of the migration / backend / frontend / plan
> docs exist under the deprecated workspace-level `docs/refactor-wecms/` tree. That tree is
> **read-only legacy** per the workspace CLAUDE.md; the canonical copies are the `wecms-api/docs/...`
> paths linked above.

---

## Note — authorization annotation

The controller carries `@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")` at the
class level ([ApprovalLineController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/controller/ApprovalLineController.java)).
This is the **live, authoritative** gate and matches the F-3 server-side menu-authorization rollout
(2026-06-06; see `wecms-api/CLAUDE.md` Authority model). The controller's own class-level Javadoc now
fully documents this gate (it no longer reads "OQ-5 no `@PreAuthorize`"). The only stale note that
remains is the `wecms-api/CLAUDE.md` Feature-Status row ("OQ-5 no `@PreAuthorize` admin gate"); see
[refactor-gap-analysis.md](refactor-gap-analysis.md#authorization-parity).

The live Javadoc also notes the gate is **stricter than live JEX** for normal users: legacy
`NORM_USER_ONLY_YN=Y` admitted any authenticated normal user, whereas the refactor requires a
`USE_YN='Y'` grant for `settings-core-approval-line` in `USER_MENU_AUTH_INFM` (an ADMIN still passes
unconditionally). This is the intended uniform Settings-module hardening — see
[refactor-gap-analysis.md](refactor-gap-analysis.md#authorization-parity).
