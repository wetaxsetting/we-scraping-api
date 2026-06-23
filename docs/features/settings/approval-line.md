# Settings > Approval Line — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/approval-line/`

## Feature Overview
- **Purpose:** Tenant-scoped configuration of named, ordered approval chains (1–5 approvers) that a company attaches to payment / transfer / expense requests. Owns only the line **configuration** (master/detail CRUD over `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL`); the runtime "Approval Box" execution flow is owned by each consumer feature, not here.
- **Business role:** Any authenticated tenant user holding the `settings-core-approval-line` menu grant (admins always pass). Used to define reusable approver sequences picked from the company directory.

## Menu / Route Mapping
- **Menu / panel:** Settings → Approval Line
- **Menu permission ID (slug):** `settings-core-approval-line` (verified against `ApprovalLineController` class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")`)
- **Wire base path:** `/api/v1/settings/approval-line`
- **Frontend mount / route:** Settings modal panel — no standalone route (`<SettingsModal>` → Approval Line panel)
- **Legacy JEX counterpart:** WSVC family `comm_0222_*` (`_001`/`_r001`/`_d001`, `_002`/`_c001`, `_003`/`_r001`/`_u001`); tables `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL` — legacy ids for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack — 5 endpoints (list+search+sort, detail, create, update with diff-sync, hard delete); backend + frontend wired with no mock fallback; legacy parity verified; SIT/UAT verified. PK generation funneled through `wb_get_next_val('appr_line_infm','appr_line_no',…)`. 5-approver cap enforced (constant + bean validation + service guard + `NUMERIC(1,0)` column). `apprvr_disp_ord` re-normalised 1..N on both Create and Update. Per-approver tenant-link guard (`USER_BUSI_CONN_INFM`).
- 🟡 **Partially implemented:** `user_img_url` is resolved and emitted per-approver, but the image-streaming endpoint (`/api/v1/users/{apprvrId}/image`) is itself out of scope (OQ-1) — URL is `null` when no image is stored.
- ⚪ **Deprecated / Legacy / Not ported:** No audit logging (legacy parity — `comm_0222_*` JSPs write no `AUDIT_LOGS_INFM` rows; deliberate divergence from `firm-banking-settings`/`contacts`). No server-side pagination (load-all matches legacy). View Setting not adopted (legacy panel has no toolbar list-icon button). Excel import/export, bulk delete, and a delete in-use guard are out of scope (no legacy counterpart).

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [approval-line/README.md](approval-line/README.md) |
| Business flow | [approval-line/business-flow.md](approval-line/business-flow.md) |
| Database mapping | [approval-line/database-mapping.md](approval-line/database-mapping.md) |
| Functional specification | [approval-line/functional-specification.md](approval-line/functional-specification.md) |
| Interface specification | [approval-line/interface-specification.md](approval-line/interface-specification.md) |
| Migration guide | [approval-line/migration-guide.md](approval-line/migration-guide.md) |
| Refactor gap analysis | [approval-line/refactor-gap-analysis.md](approval-line/refactor-gap-analysis.md) |
| Source code index | [approval-line/source-code-index.md](approval-line/source-code-index.md) |
| Test scenarios | [approval-line/test-scenarios.md](approval-line/test-scenarios.md) |

## Historical Design Notes (preserved)
- **Authorization tightened vs legacy.** Legacy `comm_0222_*` gated only on `<login>Y` + `<NORM_USER_ONLY_YN>Y` (any authenticated normal user). The refactor additionally requires a `USE_YN='Y'` grant for `settings-core-approval-line` in `USER_MENU_AUTH_INFM` (admins bypass) — intended uniform Settings-module hardening, stricter than live JEX for normal users.
- **`apprvr_disp_ord` normalisation (OQ-4).** Legacy Create trusted client-supplied order; legacy Update re-numbered 1..N. The refactor re-numbers 1..N from request-array position on **both** Create and Update — a deliberate, stricter generalisation of the legacy guard.
- **Duplicate-approver + tenant-link guards** are refactor additions with no legacy parity — fail-fast (`WBE_APPROVAL_LINE_DUP_APPROVER`, `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT`) before any DB write, rather than relying on the composite-PK constraint.
- **[Legacy JEX]** The JEX `docs/jex/business-rules/approval.md` doc carries two known stale claims: it cites WSVC `comm_0194_*`/`comm_0195_*` for approval-line config (live source is `comm_0222_*`), and describes "parallel and sequential" approval flows — but `APPR_LINE_INFM_DETL` has only `APPRVR_DISP_ORD` (all persisted lines are sequential). These are JEX-side doc conflicts, not refactor behaviour.
