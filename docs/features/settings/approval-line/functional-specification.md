# Approval Line — Functional Specification

Authoritative behavioural contract for the refactored Approval Line backend, verified against the
on-disk implementation in `com.kosign.wecms.domain.setting.approvalline`. Legacy behaviour cited from
`WECMS DEV/` as the source of truth. Deep design narrative: [../approval-line.md](../approval-line.md).

---

## Scope boundary

**In scope (this feature):** the line-configuration master/detail CRUD over `APPR_LINE_INFM` +
`APPR_LINE_INFM_DETL` and the five `comm_0222_*` services.

**Out of scope:** the "Approval Box" execution flow (per-document approve/reject state, per-step
audit trail). That is owned by other tables (`BLTB_DRAFT_POST_APPRVR_INFM`,
`EXPT_RESOL_APPRVR_INFM`, scheduled-payment / corporate-card approval state) and lands with each
consumer feature (Payment, Internal Transfer, Corporate Card, Expense, …) — not here. See
[docs/jex/business-rules/approval.md](../../../../../docs/jex/business-rules/approval.md) for the
runtime workflow (note that doc carries **stale** WSVC ids `comm_0194_*`/`comm_0195_*`; the live
config screen is `comm_0222_*`).

Also excluded (matching legacy): server-side pagination, Excel import/export, bulk delete, in-use
delete guard, View Setting registration, list-page drag-and-drop (reorder happens in the dialog only).

---

## Operations matrix

| Op | HTTP | Behaviour | Legacy parity |
|---|---|---|---|
| List | `GET /api/v1/settings/approval-line` | Tenant-scoped, name search (`q`, case-insensitive `LIKE`), sortable, no pagination; each row carries its full approver chain inline | `comm_0222_001_r001` |
| Detail | `GET …/{appr_line_no}` | One line + ordered approver chain; feeds Edit pre-fill | `comm_0222_003_r001` |
| Create | `POST …` | Master + 1–5 approvers in one tx; disp_ord normalised | `comm_0222_002_c001` |
| Update | `PUT …/{appr_line_no}` | Rename + diff-sync approvers in one tx | `comm_0222_003_u001` |
| Delete | `DELETE …/{appr_line_no}` | Hard delete master + detail rows in one tx | `comm_0222_001_d001` |

---

## Field-by-field validation

### Master — `appr_line_nm`
- Required, trimmed, max 100 chars (`VARCHAR(100)`).
- Bean validation `@NotBlank @Size(max=100)` on the request record **and** service-layer `requireApprLineNm(...)` → `WBE_APPROVAL_LINE_NAME_BLANK` on blank/whitespace-only.

### Approver list — `approvers[]`
- Required, **1 ≤ count ≤ 5**.
  - Bean validation `@NotNull @Size(min=1, max=ApprovalLineConstants.MAX_APPROVERS)` (= 5).
  - Service guard: empty → `WBE_APPROVAL_LINE_EMPTY_APPROVERS`; `> 5` → `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` (legacy `WBW1164` parity, `comm_0222_002_c001_act.jsp:90`).
- **Duplicate `apprvr_id`** rejected explicitly at the service layer → `WBE_APPROVAL_LINE_DUP_APPROVER({id})` (fail-fast before any DB write; legacy relied on the PK constraint).

### Per approver — `ApprovalLineApproverInput`
- `apprvr_id` — required, max 100 (`@NotBlank @Size(max=100)`).
- `apprvr_nm` — required, max 100 (`@NotBlank @Size(max=100)`) — **snapshot at save**, see below.
- `apprvr_posi`, `apprvr_dept` — optional, max 100; persisted as `""` (empty string, **never null**) when absent — mirrors legacy `comm_0222_003_u001_act.jsp:144-148, 164-167`.
- **No `apprvr_disp_ord` on the wire** — the server assigns it (see next section).

### Tenant-link guard (refactor addition)
Every `apprvr_id` in the request is verified against `USER_BUSI_CONN_INFM` via
`findAllByUserIdInAndBizKeyAndDelYn(ids, bizKey, "N")` (one batched query). Any id with no active
link → `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT({id})`. Legacy has **no** such guard (it trusts the
client-side picker which already filters by tenant). The refactor adds it because the picker is a
separate request and a malicious / buggy client could submit any string.

---

## Display-order normalisation

`apprvr_disp_ord` is **re-numbered `1..N` from request-array position on BOTH Create and Update**
(`apprvrDispOrd = i + 1`). The client never supplies `apprvr_disp_ord`.

- **Legacy:** Update re-numbers (`newDisplayOrder = i + 1`, `comm_0222_003_u001_act.jsp:133`); Create trusts the client order (`comm_0222_002_c001_act.jsp` `copyFrom`).
- **Refactor:** normalises on **both** — a deliberate generalisation (OQ-4). Stricter than legacy Create, but safe: clients already send `1..N` in order, and it removes a class of "the order changed unexpectedly after edit" bugs.

This means **reordering is expressed entirely by the order of the `approvers` array** — the
drag-and-drop UI reorders the array, the server re-derives `disp_ord` from it. Order persists across
save/reopen because the read path always orders `APPRVR_DISP_ORD ASC`.

---

## Update diff-sync

Mirrors legacy `comm_0222_003_u001_act.jsp:104-194` exactly, inside one `@Transactional` method:

1. Load existing detail rows; build `Map<apprvrId, row>` + `Set<apprvrId>`.
2. For each incoming approver (re-numbered `1..N` by array position):
   - id ∈ existing → **UPDATE** the managed row (new disp_ord, name, posi/dept, mod id/dtm).
   - id ∉ existing → **INSERT** a new row (reg id/dtm stamped; posi/dept coalesced to `""`).
3. After the loop: `removed = existingIds − incomingIds` → **DELETE** each by PK.
4. Master rename + `mod_id`/`mod_dtm` set on the managed entity (dirty-checked on flush).

A duplicate-approver in the request is rejected at validation **before** any write (so the PK
constraint never speaks through a generic DB error).

---

## Approver name / position / department — snapshot at save

The detail row stores `apprvr_nm` / `apprvr_posi` / `apprvr_dept` **as a snapshot at save time**, not
as a runtime join. What the picker handed back is what gets written; the read path never overwrites
`apprvr_nm` with a live `USER_INFM.USER_NM` lookup. Legacy parity
(`comm_0222_003_u001_act.jsp:141-167`) — keeps historical / audit views stable when an approver later
renames their account.

---

## `user_img_url` resolution

On every read (list + detail), `ApprovalLineService.resolveImageUrls(bizKey, apprvrIds)`:

1. One `userBusiRepo.findAllByUserIdInAndBizKeyAndDelYn(distinctIds, bizKey, "N")` lookup.
2. For each link whose `USER_IMG` is non-null/non-blank → map `apprvr_id → "/api/v1/users/{apprvr_id}/image"` (slug-based URI; legacy `szon_0012_003_r002.act` URL is **not** propagated).
3. Approvers with no/blank image are absent from the map → `toRow` emits `user_img_url = null`.

The image **streaming endpoint** (`GET /api/v1/users/{id}/image`) is itself **out of scope** (OQ-1);
the frontend tolerates a null/404 with its `<Avatar>` fallback.

---

## Delete semantics

- **Hard delete**, children-then-master, one transaction.
- **No in-use guard** (legacy parity). Consumer features snapshot the approver list at request time and hold no live FK to `APPR_LINE_INFM`, so deleting a line cannot retroactively break an in-flight payment.
- **Not found** (including a row owned by another tenant) → `WBE_APPROVAL_LINE_NOT_FOUND` (single envelope; tenant scope is implicit).
- No bulk delete (legacy list has no multi-select delete).

---

## Audit & permissions

### Authority
- **Login required** + **menu grant.** The controller carries class-level
  `@PreAuthorize("@menuAuthz.canAccess('settings-core-approval-line')")` (F-3 rollout 2026-06-06).
- **No admin-only gate.** Legacy WSVC flags are `<login>Y</login>` + `<NORM_USER_ONLY_YN>Y</NORM_USER_ONLY_YN>` + `<WTA_USER_ALLOW_YN>N</WTA_USER_ALLOW_YN>` — normal users, no WTA users, no admin-role gate. The refactor follows live JEX behaviour: any authenticated tenant user with the menu grant can CRUD approval lines (OQ-5). The conflicting "Admin users can configure approval chains" claim in `docs/jex/business-rules/approval.md` is a JEX-side documentation conflict, not a code gate.
- The stale "no `@PreAuthorize`" notes in the controller Javadoc and `wecms-api/CLAUDE.md` are logged in [refactor-gap-analysis.md §Authorization parity](refactor-gap-analysis.md#authorization-parity).

### Audit
- **No `AUDIT_LOGS_INFM` writes** on any operation. `ApprovalLineService` performs no `AuditLogService.append(...)` call and has no audit dependency. This is **legacy parity** — verified that none of the five `comm_0222_*` action JSPs import `AuditLogsInfmDAO` or write an audit row (OQ-6; deliberate divergence from the `firm-banking-settings` / `contacts` precedents which *do* add audit). No `audit.approval_line.*` i18n keys exist. A future MR can revisit if a business requirement for approval-line audit history emerges.

---

## Error code summary

| Code | Meaning | Legacy mapping |
|---|---|---|
| `WBE_APPROVAL_LINE_NOT_FOUND` | Line not found in tenant (merges "not found" + "wrong tenant") | `WBE0404` (`comm_0222_001_d001_act.jsp:99`) |
| `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` | More than 5 approvers | `WBW1164` (`comm_0222_002_c001_act.jsp:90`, `comm_0222_003_u001_act.jsp:125`) |
| `WBE_APPROVAL_LINE_DUP_APPROVER` | Same `apprvr_id` repeated | (refactor fail-fast; legacy relied on PK) |
| `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` | `apprvr_id` has no active tenant link | (refactor defensive guard) |
| `WBE_APPROVAL_LINE_EMPTY_APPROVERS` | `approvers[]` empty/missing | legacy `validateRecord` non-empty REC |
| `WBE_APPROVAL_LINE_NAME_BLANK` | `appr_line_nm` blank | legacy `validateRequired` |

Full request/response contract: [interface-specification.md](interface-specification.md).
