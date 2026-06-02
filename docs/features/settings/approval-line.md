# Approval Line — Refactor Feature Spec

| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `approval-line` |
| JEX counterpart | `docs/jex/features/approval-line.md` **(not present today — see § 14 OQ-2)**; JEX behavioural source of truth is the live source under `WECMS DEV/` (`comm_0222_*` services + `ApprLineInfm` / `ApprLineInfmDetl` entities + `APPR_LINE_INFM(_DETL)` tables) |
| Module | Settings → Approval Line |
| Status | Implemented (Working) — see wecms-api/CLAUDE.md → Feature Status (approval-line) |
| Spec author | Claude Code (R&D planning), Sonny |

> **Identifier rule.** Every refactor surface — Next.js route segment, Spring REST URI, Java package, DTO names, frontend feature folder, i18n key prefix, log tag, View Setting `screenId` (if/when adopted), and `AUDIT_LOGS_INFM.WSVC_ID` — uses the kebab-case slug `approval-line`. Legacy JEX WSVC ids (`comm_0222_001`, `comm_0222_002_c001`, `comm_0222_003_u001`, `comm_0222_001_d001`, `comm_0222_003_r001`) appear in this doc **only** as JEX file:line citations or in migration-traceability prose. See workspace [`CLAUDE.md`](../../../CLAUDE.md) § Cross-Tree Rules and [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) § "Refactor identifier naming".

---

## 1. Functional summary

A tenant-scoped configuration screen used to define **approval lines** (named sequences of approvers, max 5 per line) that the company can later attach to a payment / transfer request. Reached from the **Settings** modal in the global header; renders as a Settings panel inside [`<SettingsModal>`](../../../../wecms-web/components/settings/SettingsModal.tsx) — the static UI is already wired at [`wecms-web/components/settings/approval-line/`](../../../../wecms-web/components/settings/approval-line/) (Figma 9196:11717 / 9196:11969 / 9196:12153).

Capabilities mirrored from JEX:

- **List** approval lines for the tenant, with name search (ILIKE), each row carrying the approver chain inline (display order, name, position, department, optional user image URL).
- **Add** a new approval line with 1–5 approvers picked from the company's user directory.
- **Edit** an existing line — rename + add / remove / reorder approvers (server-side diff sync against the existing detail rows).
- **Delete** a single approval line — hard delete of the master row and its detail rows in one transaction.
- **View** an approval line's full approver chain inline in the list row (no separate detail dialog in the legacy JSP — the row already carries the chain in `REC1`).

**Scope is the line CONFIGURATION feature only.** The "Approval Box" approval-execution flow (per-document approval status, approve/reject buttons, per-step audit trail described in [`docs/jex/business-rules/approval.md`](../../../../docs/jex/business-rules/approval.md) § Approval Box / § Approval Details) is owned by other tables (`BLTB_DRAFT_POST_APPRVR_INFM`, `EXPT_RESOL_APPRVR_INFM`, scheduled-payment / corporate-card approval state). The `approval-line` feature ports **only** `APPR_LINE_INFM` + `APPR_LINE_INFM_DETL` and the five `comm_0222_*` services. Execution-side ports land with each consumer feature (Payment, Internal Transfer, Corporate Card, Expense, etc.) — not here.

Excluded from this refactor scope (out of scope, see § 12):

- Approval-line "usage" indicator (whether a line is currently referenced by a pending payment / scheduled transfer). JEX has no such guard — Delete is unconditional.
- Drag-and-drop reorder within the list page (JEX has no such control; reorder happens inside the dialog only).
- Excel export / import of approval lines.
- View Setting registration (legacy list has no toolbar list-icon button — same precedent as `erp-transaction-history`, see `docs/refactor-wecms/features/erp-transaction-history.md` (Needs Confirmation) § View Setting).
- Server-side pagination (JEX `comm_0222_001_r001_act.jsp:53-62` explicitly disables pagination — "load all data"). Refactor MVP also returns the full tenant list and lets the panel filter client-side; pagination is added only if a tenant approaches > 500 lines in the wild.
- A separate `GET /…/{id}` detail endpoint surface for read-only viewing — the list endpoint already returns the full chain inline; the dialog Edit pre-fill reuses the list response (matches JEX `comm_0222_003_r001` which is only invoked by the edit dialog). The endpoint still ships for the static-UI Edit pre-fill flow — see § 7.

Scope is intentionally a clean clone of the **core master-detail CRUD path** under the `APPR_LINE_INFM` / `APPR_LINE_INFM_DETL` table pair. Any approval-related execution feature can lift its own approver-tracking table behind a fresh PO decision.

## 2. JEX entry points (read-only reference)

| JEX WSVC | Role | File:line |
|---|---|---|
| `comm_0222_001` (view) | Approval-line list panel | [WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_001_view.jsp) |
| `comm_0222_002` (view) | Create approval-line popup | [WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_002_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_002_view.jsp) |
| `comm_0222_003` (view) | Edit approval-line popup | [WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_003_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_003_view.jsp) |
| `comm_0222_001_r001` | List read (tenant + search) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp:36-153](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp) |
| `comm_0222_002_c001` | Create (master + ≤5 detail rows) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp:28-111](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp) |
| `comm_0222_003_r001` | Read single line + detail rows | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_r001_act.jsp:34-117](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_r001_act.jsp) |
| `comm_0222_003_u001` | Update (rename + diff sync approvers) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp:36-198](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp) |
| `comm_0222_001_d001` | Delete (master + detail rows) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_d001_act.jsp:31-113](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_d001_act.jsp) |
| `APPR_LINE_INFM_DETL_R002` (IDO) | List query with `json_agg` approvers ordered by display order | [WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.APPR_LINE_INFM_DETL_R002.xml:24-56](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.APPR_LINE_INFM_DETL_R002.xml) |
| Frontend TS | jQuery list + popup orchestration | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_001.ts` / `comm_0222_002.ts` / `comm_0222_003.ts` |

Entities referenced (read-only):

- `ApprLineInfm` — [WECMS DEV/WECMS/src/wabooks/entity/ApprLineInfm.java:22-109](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfm.java)
- `ApprLineInfmPk` — [WECMS DEV/WECMS/src/wabooks/entity/ApprLineInfmPk.java:19-57](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfmPk.java)
- `ApprLineInfmDetl` — [WECMS DEV/WECMS/src/wabooks/entity/ApprLineInfmDetl.java:26-131](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfmDetl.java)
- `ApprLineInfmDetlPk` — [WECMS DEV/WECMS/src/wabooks/entity/ApprLineInfmDetlPk.java:19-63](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfmDetlPk.java)
- DAOs: `ApprLineInfmDAO`, `ApprLineInfmDetlDAO` (standard GenericDAO subclasses; no custom logic)

## 3. Data model touched

Two tables — verified against the JEX DEV DB on 2026-05-13 via `postgres` MCP (`information_schema.columns`, `pg_constraint`, `pg_indexes`).

### 3.1 `APPR_LINE_INFM` — approval-line master

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `BIZ_KEY` | `VARCHAR(20)` | NO | Tenant. Part of PK. |
| `APPR_LINE_NO` | `NUMERIC(15,0)` | NO | Per-tenant surrogate. Part of PK. Generated server-side. |
| `APPR_LINE_NM` | `VARCHAR(100)` | NO | User-facing name. |
| `REG_ID` | `VARCHAR(100)` | NO | Audit. |
| `REG_DTM` | `VARCHAR(14)` | NO | `yyyyMMddHHmmss` UTC. |
| `MOD_ID` | `VARCHAR(100)` | NO | Audit. |
| `MOD_DTM` | `VARCHAR(14)` | NO | `yyyyMMddHHmmss` UTC. |

PK: `(BIZ_KEY, APPR_LINE_NO)` — see `pk_appr_line_infm`. No additional indexes today.

### 3.2 `APPR_LINE_INFM_DETL` — approver rows

| Column | Type | Nullable | Notes |
|---|---|---|---|
| `BIZ_KEY` | `VARCHAR(20)` | NO | Tenant. Part of PK. |
| `APPR_LINE_NO` | `NUMERIC(15,0)` | NO | FK to master (logical — no `FOREIGN KEY` constraint on DEV). Part of PK. |
| `APPRVR_ID` | `VARCHAR(100)` | NO | `USER_INFM.USER_ID` of the approver. Part of PK. |
| `APPRVR_DISP_ORD` | `NUMERIC(1,0)` | NO | Display + approval order, 1..5 (DB type pins single digit, matches WBW1164 cap). |
| `APPRVR_NM` | `VARCHAR(100)` | NO | Snapshot of approver name at time of save (legacy denormalisation). |
| `APPRVR_POSI` | `VARCHAR(100)` | YES | Snapshot of approver position. JEX writes `""` (empty string) when not supplied (`comm_0222_003_u001_act.jsp:144-148`). |
| `APPRVR_DEPT` | `VARCHAR(100)` | YES | Snapshot of approver department. Same write-as-empty-string behaviour. |
| `REG_ID` | `VARCHAR(100)` | YES | Audit. |
| `REG_DTM` | `VARCHAR(14)` | YES | Audit. |
| `MOD_ID` | `VARCHAR(100)` | YES | Audit. |
| `MOD_DTM` | `VARCHAR(14)` | YES | Audit. |

PK: `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)` — see `pk_appr_line_infm_detl`. No additional indexes today.

> **Schema parity note.** Refactor Flyway migration mirrors both tables column-for-column (`UPPER_SNAKE` column names, `VARCHAR(14)` timestamp strings — matches the legacy parity rule in [`wecms-api/CLAUDE.md` § Legacy Schema Parity Rule](../../../CLAUDE.md)). Detail audit columns are kept nullable in the refactor entity for parity with DEV even though JEX always writes them (the production data has nulls in older rows).

Detailed schema (DDL + index decisions) lives in `docs/refactor-wecms/database/approval-line-schema.md` (Needs Confirmation) — file to be created by the backend MR.

## 4. List view — behaviour

### 4.1 Filtering

The JEX list filter is assembled at [`comm_0222_001_r001_act.jsp:36-49`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp). Refactor mirrors:

- **Tenant:** always filtered to `BIZ_KEY = TenantContext.currentBizKey()` (legacy line 40-41). Request body / query string **never** carries `biz_key`.
- **Name search (`q` / `srch_wd`):** optional ILIKE wrapper `'%' || ? || '%'` against `APPR_LINE_NM` (legacy lines 43-46). Empty / whitespace string → no clause. Case-insensitive.
- **No soft-delete column** — the master table has no `DEL_YN`, so the list shows every row.

### 4.2 Sort

Legacy uses `SortHelper.getSortDynamic(util, "comm_0222_001_r001")` (legacy line 101) — the live JEX configuration sorts by `REG_DTM DESC` as the default (the only sortable field surfaced in the live UI today). Refactor pins:

| Sort field (wire token) | Entity property | Default |
|---|---|---|
| `REG_DTM` | `regDtm` | ✅ default `DESC` |
| `APPR_LINE_NM` | `apprLineNm` | optional `ASC` / `DESC` |
| `MOD_DTM` | `modDtm` | optional `ASC` / `DESC` |

Per the common-sort whitelist contract ([`wecms-api/CLAUDE.md` § Common table sort](../../../CLAUDE.md)). Default ORDER BY: `reg_dtm DESC, appr_line_no DESC` — deterministic tie-break on the surrogate.

### 4.3 Pagination

**Not adopted in MVP.** JEX explicitly disables pagination in the list query ([`comm_0222_001_r001_act.jsp:52-66`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp) — the `PageInfo` branch is commented out, "load all data"). Refactor matches: no `page_no` / `page_per_cnt` query params, no `total_count` in the response. The frontend panel filters the full tenant list locally by name. If a tenant grows past ~500 lines, revisit (§ 14 OQ-3).

### 4.4 Row shape (response)

The legacy IDO `APPR_LINE_INFM_DETL_R002` aggregates approvers via a `json_agg(... ORDER BY APPRVR_DISP_ORD ASC)` correlated subquery ([`IDO.APPR_LINE_INFM_DETL_R002.xml:37-46`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.APPR_LINE_INFM_DETL_R002.xml)), then the action JSP parses the `APPROVERS` JSON, resolves each approver's `USER_IMG_URL` per row, and emits the nested REC1 shape ([`comm_0222_001_r001_act.jsp:106-140`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp)).

Refactor returns the **same nested shape on the wire**, lowercase snake_case (see § 7):

```
approval_line
├── appr_line_no              ← surrogate (BIGINT on the wire — JSON-safe)
├── appr_line_nm
├── reg_dtm                   ← VARCHAR(14)
├── approvers[]               ← legacy REC1, always ordered by apprvr_disp_ord ASC
│   ├── apprvr_disp_ord       ← 1..5
│   ├── apprvr_id
│   ├── apprvr_nm
│   ├── apprvr_posi
│   ├── apprvr_dept
│   └── user_img_url          ← resolved per-row (see § 4.5)
```

### 4.5 `user_img_url` resolution

JEX renders the approver's avatar by calling a per-row helper that:

1. Loads `USER_BUSI_CONN_INFM` for `(BIZ_KEY, APPRVR_ID)` ([`comm_0222_001_r001_act.jsp:121-128`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp)).
2. If `USER_IMG` is null → URL `"../img/img_thumb.png"` (legacy placeholder).
3. Otherwise → URL `"szon_0012_003_r002.act?USER_ID={apprvrId}"` (legacy JSP servlet that streams the image).

Refactor handling (decided here, confirms § 14 OQ-1):

- The **backend** returns `user_img_url = null` whenever the approver row has no image (no placeholder string from the server). The frontend renders the avatar fallback icon (already shipped in the static UI).
- When an image exists, the backend returns a **slug-based REST URI** — `"/api/v1/users/{apprvrId}/image"` — pointing at a new shared `domain/auth/UserController.image(...)` endpoint that streams the bytes (or a 404 if none). The legacy `szon_0012_003_r002.act` URL is **not** propagated; new code never references legacy `_act.jsp` URLs.
- The user-image endpoint itself is **out of scope for this MR** — the refactor returns the URL but ships the streaming endpoint with a follow-up MR (or with the first feature that genuinely needs the image, e.g. Approval Box). For Approval Line MVP the frontend tolerates 404 on the image URL gracefully (existing `<Avatar>` fallback chain in [`wecms-web/components/shared/`](../../../../wecms-web/components/shared/) — confirm at Phase 3).

## 5. Add / Edit form — behaviour

### 5.1 Required fields (master)

Mirror [`comm_0222_002_c001_act.jsp:28-41`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp) and [`comm_0222_003_u001_act.jsp:36-51`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp):

- `appr_line_nm` — required, trimmed, max 100 (`VARCHAR(100)`).
- `approvers[]` — required, **1 ≤ count ≤ 5**. The legacy cap is enforced at the action layer ([`comm_0222_002_c001_act.jsp:90`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp), [`comm_0222_003_u001_act.jsp:125`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp)) and surfaces as `WbMessageCode.WBW1164`. Refactor enforces the same bounds via Bean Validation **and** in the service layer (defence in depth) and maps the failure to `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` (see § 10).
- Per approver row — `apprvr_id`, `apprvr_nm`, `apprvr_disp_ord` are required ([`comm_0222_002_c001_act.jsp:38-41`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp)). `apprvr_posi`, `apprvr_dept` are optional and default to `""` (empty string) — never null — when persisted (mirrors [`comm_0222_003_u001_act.jsp:144-148, 165-167`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp)).

### 5.2 Display-order rule (defensive normalisation)

JEX **ignores the client-supplied `apprvr_disp_ord`** during update and re-numbers the chain sequentially from 1..N based on array position ([`comm_0222_003_u001_act.jsp:130-133`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp) — `int newDisplayOrder = i + 1`). Create does **not** re-normalise (it accepts whatever order the client sends, [`comm_0222_002_c001_act.jsp:94-104`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp)).

Refactor **normalises on both Create and Update** for consistency (1..N from array position, server-authoritative). This is a deliberate generalisation of the legacy guard:

- Same end-state for Create (clients today already send 1..N in order).
- Stricter than JEX Create today, but stricter is safe — the client never relied on Create accepting `[3, 1, 2]` in production, and re-normalising removes a class of "I edited the line and the order changed unexpectedly" bugs.
- Documented in OQ-4 below (resolved here — no parity break).

### 5.3 Duplicate approver guard

JEX's update routine uses a `HashSet<String>` of `APPRVR_ID` to compare existing vs new ([`comm_0222_003_u001_act.jsp:114-122`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp)) — which implicitly deduplicates if the client sends the same approver twice (the second occurrence simply overwrites the first in the map, then the diff still adds it once). The DB PK `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)` is the real guard.

Refactor enforces duplicate-approver rejection **explicitly at the controller layer** (`WBE_APPROVAL_LINE_DUP_APPROVER`, see § 10) before any DB write — fail-fast with a clear error code, never let the DB constraint speak through a generic `WBE_DB`.

### 5.4 Server-side approver validation

For every `apprvr_id` in the request, the service verifies the user is linked to the current tenant — `userBusiConnInfmRepo.findByUserIdAndBizKeyAndDelYn(apprvrId, currentBizKey, "N")` returns a row. If not, throw `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT`. JEX has no such guard today (the legacy form trusts whatever the picker submits, because the picker itself filters by tenant on the JS side). Refactor adds it because:

- The picker is a separate request — a malicious / buggy client could submit any string.
- The persisted `APPRVR_NM` is a snapshot — better to fail than to persist a name attached to a user-id that has no current claim on the tenant.

### 5.5 Approver name + position + department: snapshot at save

The detail row stores the approver's `APPRVR_NM` / `APPRVR_POSI` / `APPRVR_DEPT` **as a snapshot at save time**, not as a runtime join. Legacy behaviour ([`comm_0222_003_u001_act.jsp:141-148, 161-167`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp)) — what the user typed (or what the picker handed back) is what gets written. Refactor preserves this: never overwrite `APPRVR_NM` with a live lookup of `USER_INFM.USER_NM` at read time. The frontend renders the snapshot.

Rationale: legacy parity + makes historical / audit views stable when an approver later renames their account.

### 5.6 Update diff sync

Update is the only complex flow. JEX implementation ([`comm_0222_003_u001_act.jsp:104-194`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp)):

1. Load all existing detail rows for the line.
2. Build `Set<existingApproverIds>` + `Map<apprvrId, ApprLineInfmDetl>` of existing rows.
3. For each incoming approver (re-numbered 1..N by array position):
   - If `apprvrId` exists in the map → `UPDATE` the existing row (set new disp_ord, new name, new posi/dept, `mod_id` / `mod_dtm`).
   - Else → `INSERT` a new row (new posi/dept default to `""`).
4. After the loop, compute `removedIds = existingIds - newIds` and `DELETE` each one by PK.

Refactor reproduces this exactly inside one `@Transactional` service method (`ApprovalLineService.update(...)`). The whole update — master rename + diff sync of approvers — is one DB transaction. A duplicate-approver in the request is rejected at validation (§ 5.3) before any write touches the DB.

### 5.7 Transaction boundary

JEX wraps the whole create / update / delete in `idoCon.beginTransaction()` … `IdoUtils.commitAndCloseQuietly(idoCon)`. Refactor: `@Transactional` on `ApprovalLineService.create / update / delete`. Audit-log writes use `@Transactional(REQUIRES_NEW)` per the shared `AuditLogService` contract — audit failure logs at WARN and never aborts the business commit.

## 6. Delete — behaviour

JEX `comm_0222_001_d001` ([`comm_0222_001_d001_act.jsp:84-110`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_d001_act.jsp)):

1. Validate `APPR_LINE_NO` present.
2. `findByPk(BIZ_KEY, APPR_LINE_NO)` on the master — if not found, throw `WBE0404` (`WbMessageCode`). **No `WBE1190`-style in-use guard** — JEX does not check whether the line is referenced by a pending payment / scheduled transfer.
3. `deleteByFk(BIZ_KEY, APPR_LINE_NO)` on the detail table (cascade by app-layer iteration via the FK dynamic).
4. `deleteByPk(BIZ_KEY, APPR_LINE_NO)` on the master.
5. Commit.

Refactor mirrors exactly:

- **No in-use guard**, matching legacy parity. The reason JEX has no guard: the execution side (Approval Box / per-document approver tables) snapshots the approver list at request-creation time — they don't hold a live FK to `APPR_LINE_INFM` after the snapshot. Deleting an approval line does **not** retroactively break an in-flight payment.
- **Hard delete, not soft delete.** No `DEL_YN` column anywhere in this feature.
- **Endpoint:** `DELETE /api/v1/settings/approval-line/{apprLineNo}` — single-row delete, no bulk endpoint (JEX has no bulk delete; the static UI is single-row "Delete" per row).
- **Not found vs wrong tenant:** single envelope `WBE_APPROVAL_LINE_NOT_FOUND` for "row doesn't exist in this tenant" — the tenant scope is implicit (`TenantContext.currentBizKey()`), so a row that exists in another tenant looks identical to "no row" from the caller's perspective.

## 7. API surface (refactor)

Detail in [`docs/refactor-wecms/backend/approval-line.md`](../../references/business-rules/approval-line.md). Summary:

| Method | URI | Purpose |
|---|---|---|
| `GET` | `/api/v1/settings/approval-line` | List (name search + sort; no pagination) |
| `GET` | `/api/v1/settings/approval-line/{apprLineNo}` | Read one (master + nested approver chain — feeds the Edit dialog) |
| `POST` | `/api/v1/settings/approval-line` | Create (master + 1–5 approvers, single tx) |
| `PUT` | `/api/v1/settings/approval-line/{apprLineNo}` | Update (rename + diff sync, single tx) |
| `DELETE` | `/api/v1/settings/approval-line/{apprLineNo}` | Hard delete (master + detail rows, single tx) |

All endpoints are JWT-protected (the global rule at [`wecms-api/CLAUDE.md` § Auth Rules → SecurityConfig](../../../CLAUDE.md)). Tenant scoping is always from `TenantContext.currentBizKey()` — request body / query / path params **never** carry `biz_key`. Path segment `{apprLineNo}` is a `long`; the controller binds it via `@PathVariable long apprLineNo` and the service composes `new ApprovalLineId(bizKey, apprLineNo)` internally.

## 8. Permissions & audit

- **Login required.** Every legacy WSVC carries `<login>Y</login>`; refactor inherits via the default JWT guard.
- **Authority.** Legacy WSVC flags `<NORM_USER_ONLY_YN>Y</NORM_USER_ONLY_YN>` + `<WTA_USER_ALLOW_YN>N</WTA_USER_ALLOW_YN>` (e.g. [`WSVC.comm_0222_001_r001.xml:25-26`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_001_r001.xml)) — normal users only, no tax-agent users. Refactor's `JwtAuthenticationFilter` already excludes WTA users from this app surface; no extra `@PreAuthorize` gate today. A future role-gated MR (referenced in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md)) will pin the slugged endpoint when it lands.
- **Admin-only? — Out of scope for MVP (§ 14 OQ-5 resolved 2026-05-13).** [`docs/jex/business-rules/approval.md`](../../../../docs/jex/business-rules/approval.md) § Authorization claims "Admin users can configure approval chains for their business" — implying the screen is admin-only. JEX action JSPs **do not enforce** an admin-only check (only `NORM_USER_ONLY_YN`), and the live menu permissions live in `MENU_ENTL_INFM` (out of scope). **Refactor follows live JEX behaviour** — any authenticated normal user with a valid tenant link can create/edit/delete approval lines. No `@PreAuthorize("hasRole('ADMIN')")` annotations. A future role-gating MR can revisit if/when menu-permission resolution lands.
- **Audit.** **JEX writes no `AUDIT_LOGS_INFM` row** in any of the five `comm_0222_*` action JSPs (verified — none of them import `AuditLogsInfmDAO`). **Refactor keeps legacy parity** — `ApprovalLineService` performs no `AuditLogService.append(...)` calls; no rows are written to `AUDIT_LOGS_INFM` with `WSVC_ID = 'approval-line'`; no `audit.approval_line.*` i18n keys are added to `messages*.properties`. This is a deliberate divergence from the `firm-banking-settings` / `contacts` / `vas-account` precedents (which did add audit on the refactor side) — PO decision 2026-05-13 (§ 14 OQ-6 resolved): Approval Line audit history is not a current business requirement; the legacy gap is acceptable. A future MR can revisit if/when requirements change.

## 9. i18n keys (refactor namespace)

The legacy ML codes (`comm_0222_001_m001…`, `comm_0222_002_m001…`, `comm_0222_003_m001…`, plus the TS-side `_j001…` codes inside `comm_0222_*.ts`) are **not** ported verbatim — per the [naming rule](../../../CLAUDE.md), the refactor uses slug-based keys. Static-UI keys already in use (see [`wecms-web/locales/en.json`](../../../../wecms-web/locales/en.json) — confirm at Phase 3):

| Surface | Refactor key |
|---|---|
| Panel title | `settings.approval_line.title` |
| Panel description | `settings.approval_line.description` |
| Search placeholder | `settings.approval_line.search_placeholder` |
| Add button | `settings.approval_line.add` |
| Column: Name | `settings.approval_line.column.name` |
| Column: Approver | `settings.approval_line.column.approver` |
| Column: Create Date | `settings.approval_line.column.create_date` |
| Column: Action | `settings.approval_line.column.action` |
| Row action: Edit | `settings.approval_line.row.edit` |
| Row action: Delete | `settings.approval_line.row.delete` |
| Add dialog title | `settings.approval_line.dialog.add_title` |
| Edit dialog title | `settings.approval_line.dialog.edit_title` |
| Field: Name | `settings.approval_line.dialog.name_label` |
| Field: Name placeholder | `settings.approval_line.dialog.name_placeholder` |
| Approver chain header | `settings.approval_line.dialog.approver_chain` |
| Add Approver button | `settings.approval_line.dialog.add_approver` |
| Approver-popover search placeholder | `settings.approval_line.popover.search_placeholder` |
| Approver-popover empty state | `settings.approval_line.popover.empty` |
| Approver-popover count `{n} of {N} selected` | `settings.approval_line.popover.selected_count` |
| Approver-popover clear all | `settings.approval_line.popover.clear_all` |
| Approver-popover add button | `settings.approval_line.popover.add_button` |
| Empty list state | `settings.approval_line.empty` |
| Delete confirm body | `settings.approval_line.delete.confirm_body` |
| Delete success | `settings.approval_line.delete.success` |
| Save success | `settings.approval_line.save.success` |

EN / KO / VI land in [`wecms-web/locales/{en,ko,vi}.json`](../../../../wecms-web/locales) in the same MR. KM is omitted (per workspace policy — no KM translations on the refactor side).

No backend audit-message keys — refactor writes no `AUDIT_LOGS_INFM` rows for Approval Line (§ 8 audit bullet, § 14 OQ-6 resolved 2026-05-13).

## 10. Error codes (new)

Add to [`wecms-api/src/main/resources/messages.properties`](../../../src/main/resources/messages.properties) and `messages_en.properties` (plus `_ko` / `_vi` when translated):

| Code | HTTP | Meaning | Legacy mapping |
|---|---|---|---|
| `WBE_APPROVAL_LINE_NOT_FOUND` | 200 | Approval line not found in tenant (single envelope merges "not found" + "wrong tenant") | `WbMessageCode.WBE0404` at [`comm_0222_001_d001_act.jsp:99`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_d001_act.jsp) |
| `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS` | 200 | More than 5 approvers in the request | `WbMessageCode.WBW1164` at [`comm_0222_002_c001_act.jsp:91`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp) + [`comm_0222_003_u001_act.jsp:126`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp) |
| `WBE_APPROVAL_LINE_DUP_APPROVER` | 200 | Same `apprvr_id` repeated in `approvers[]` | (new — no legacy parity; refactor fail-fast for DB PK violation) |
| `WBE_APPROVAL_LINE_APPROVER_NOT_IN_TENANT` | 200 | `apprvr_id` is not an active `USER_BUSI_CONN_INFM` link for the current tenant | (new — defensive guard, see § 5.4) |
| `WBE_APPROVAL_LINE_EMPTY_APPROVERS` | 200 | `approvers[]` is empty / null | (new — legacy's `validateRecord` enforces non-empty REC, [`comm_0222_002_c001_act.jsp:30`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp); refactor codifies as a typed code instead of generic `WBE_VALIDATION`) |
| `WBE_APPROVAL_LINE_NAME_BLANK` | 200 | `appr_line_nm` missing / whitespace-only | (new — same rationale; legacy throws via `validateRequired`) |

All standard validation errors (missing required field, bad enum value, etc.) fall through to `WBE_VALIDATION` per the global handler in [`wecms-api/CLAUDE.md` § Exception → response mapping](../../../CLAUDE.md).

## 11. View Setting

**Not adopted in MVP.** The legacy JEX list page has no "View Setting" toolbar icon (consistent with `comm_0222_001_view.jsp` — only the "+ Add" button and the search input are surfaced). Refactor follows the same precedent as `erp-transaction-history` (see `docs/refactor-wecms/features/erp-transaction-history.md` § View Setting (Needs Confirmation)) — no `WEB_SVC_ID = 'approval-line'` seed, no `<ViewSettingPopover>` in the panel.

If a future MR adds a column-customisation surface, the seed migration will register the screen at:

- `WEB_SVC_ID = 'approval-line'`
- `DYM_AREA_ID = 'default'`

…with column whitelist `APPR_LINE_NM`, `APPRVR_NM_LIST` (the approver chain rendered as a comma-joined string), `REG_DTM`, plus the special `CHECKBOX` / `ETC` ids. Until then the panel renders the static four-column layout from Figma 9196:11717.

## 12. Excluded from refactor scope (deferred or never)

| Item | Reason | Reopen condition |
|---|---|---|
| Server-side pagination | Legacy intentionally "load all" — tenants don't have hundreds of approval lines in practice. Frontend filters in-memory. | Tenant exceeds ~500 lines AND the panel renders slowly. |
| Approval Box / per-document approval state | Owned by other tables (`BLTB_DRAFT_POST_APPRVR_INFM`, `EXPT_RESOL_APPRVR_INFM`, scheduled-payment columns) — out of scope for the line-configuration feature. Each payment-type feature ports its own execution-side approver tracking. | Each future payment-type MR (Vendor Payment, Expense, Payroll, Overseas, Corporate Card, Internal Transfer) lands its own approval execution. |
| Approval-execution business-rules document at [`docs/jex/business-rules/approval.md`](../../../../docs/jex/business-rules/approval.md) | Document covers the Approval Box runtime flow (state machine, badges, hover cards) — that surface is owned by future per-payment-type MRs, not this feature. | When the first payment-type Approval Box MR lands, the doc is split or its rule sections move under that feature. |
| Excel import / export of approval lines | Not in the live JEX UI for `comm_0222_*`. | PO request. |
| Bulk delete | JEX list has no row-multi-select for delete; the static UI follows. | PO request. |
| In-use guard (block delete if a pending payment references the line) | JEX has no such guard — execution side snapshots approvers at request-creation time and doesn't hold a live FK. | If a future payment feature changes the snapshot model to a live FK, revisit. |
| Drag-and-drop reorder on the list page | JEX has no such control. Inside the Add/Edit dialog the approver chain is reorderable (static-UI ships this); the list page is read-only ordering. | Never (different concern). |
| `user_img_url` streaming endpoint (`GET /api/v1/users/{id}/image`) | A shared infra concern, not specific to Approval Line. MVP returns the URL; the endpoint lands with a follow-up MR or the first feature that genuinely consumes it. | When Approval Box or another feature mandates avatar rendering. |
| Two separate `_view` JSP popups (`comm_0222_002` for Add, `comm_0222_003` for Edit) | Refactor uses one dialog component (`<ApprovalLineDialog>`) with `mode: "add" \| "update"` — already shipped in the static UI. | Never (subsumed). |

## 13. Reuse manifest (cross-stack)

Backend reuse (existing in `wecms-api/`):

- `common/response/ApiResponse` — envelope wrapper.
- `common/exception/BusinessException` + `GlobalExceptionHandler` — error funnel.
- `common/entity/BaseEntity` — `REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM` audit columns via `@PrePersist` / `@PreUpdate`.
- `common/tenant/TenantContext` — `currentBizKey()`, `currentUserId()`, `currentLangCd()`.
- `common/i18n/MessageResolver` + `messages*.properties` — locale resolution.
- `common/security/JwtAuthenticationFilter` — JWT → SecurityContext + TenantContext.
- `common/web/SortDirection` + `SortableField` — common-sort primitives.
- `common/auditlog/service/AuditLogService` — shared per-locale audit writer (used by `firm-banking-settings`, `contacts`).
- `domain/auth/entity/UserInfm` — read-only join target if name / authority needs verification (validation in § 5.4).
- `domain/auth/entity/UserBusiConnInfm` + `UserBusiConnInfmRepository.findByUserIdAndBizKeyAndDelYn(...)` — already exists; reused for the per-approver tenant-link guard.
- `wecms-api/src/main/resources/db/migration/V11__*.sql` — `wb_get_next_val(...)` legacy function is **already ported locally** for `BIZ_KEY` and `EMPL_NO` branches. The Approval Line MR adds an `('appr_line_infm', 'appr_line_no', biz_key, …)` branch to the same function (V{n}) — no new sequence, no Java-side generator. Mirrors `SeqHelper.getNextVal(util, idoCon, getEntityName(), "appr_line_no")` at the JEX DAO layer.

Frontend reuse (existing in `wecms-web/`):

- `<SettingsModal>` shell — see [`wecms-web/CLAUDE.md` § 3a](../../../../wecms-web/CLAUDE.md). Already wires the `approval-line` panel.
- `<SettingsNav>` — already includes the "Approval Line" entry (see [`components/settings/SettingsNav.tsx`](../../../../wecms-web/components/settings/SettingsNav.tsx)).
- Static-UI panel + dialog + popover already shipped:
  - [`components/settings/approval-line/ApprovalLinePanel.tsx`](../../../../wecms-web/components/settings/approval-line/ApprovalLinePanel.tsx)
  - [`components/settings/approval-line/ApprovalLineDialog.tsx`](../../../../wecms-web/components/settings/approval-line/ApprovalLineDialog.tsx)
  - [`components/settings/approval-line/ApprovalLineTable.tsx`](../../../../wecms-web/components/settings/approval-line/ApprovalLineTable.tsx)
  - [`components/settings/approval-line/AddApproverPopover.tsx`](../../../../wecms-web/components/settings/approval-line/AddApproverPopover.tsx)
  - [`components/settings/approval-line/types.ts`](../../../../wecms-web/components/settings/approval-line/types.ts) + `mockData.ts` + `approverMockData.ts`
- `<SearchInput>` — search row.
- `<Dialog>` primitive + the §3a feature-dialog rhythm — `<ApprovalLineDialog>` uses this with `stackLevel={2}` on top of the L1 Settings shell.
- `useDialog()` system dialog — Delete confirmation + Save success / error feedback.
- `lib/api/useApiData.ts` — list hook.
- `lib/axios.ts` `api` — HTTP client.
- `lib/types/api.ts` — wire types in lowercase snake_case.
- Form layer — React Hook Form + Zod (`@hookform/resolvers`), already standard.

Database reuse:

- `BaseEntity` audit columns (`VARCHAR(14)` in local refactor schema, matching legacy parity).
- `BIZ_KEY` tenant column (every row carries it; logical FK to `BUSI_INFM`).
- `wb_get_next_val` PL/pgSQL function (V11) — new branch added by this MR for `appr_line_no`.

## 14. Open questions for Sonny / PO

- ~~**OQ-1 — `user_img_url` endpoint shape.**~~ **Resolved 2026-05-13 — Use `GET /api/v1/users/{apprvrId}/image` for the shared user-image endpoint.** MVP returns `null` when no image is stored for the approver; the frontend falls back to the existing `<Avatar>` icon. The streaming endpoint itself remains a follow-up MR (or lands with the first feature that genuinely consumes the image, e.g. Approval Box) — Approval Line MVP just returns the URL string. Decision is reflected in [§ 4.5 `user_img_url` resolution](#45-user_img_url-resolution) and [migration § 4.1](../../references/migration/approval-line.md). Reopen if Approval Box needs the endpoint sooner.
- ~~**OQ-2 — Missing JEX feature doc.**~~ **Resolved 2026-05-13 — Will be created during the documentation pass before backend implementation begins.** The JEX-side spec `docs/jex/features/approval-line.md` will mirror this refactor slug and use the live JEX source under `WECMS DEV/` plus [`docs/jex/business-rules/approval.md`](../../../../docs/jex/business-rules/approval.md) as the behavioural reference points. Action item moved to the planning doc as prerequisite P-2 ([approval-line-plan.md § 0](../../archive/planning/phase-1/approval-line-plan.md)) — must clear before backend tasks B-1+ start.
- ~~**OQ-3 — Pagination.**~~ **Resolved 2026-05-13 — No pagination for MVP.** The Approval Line screen is expected to have a small dataset per tenant (tens, not hundreds), so server-side pagination is unnecessary. The list endpoint returns the full tenant set; the frontend filters client-side by name. No `page_no` / `page_per_cnt` query params, no `total_count` semantics beyond `list.size()`. Decision is reflected in [§ 4.3 Pagination](#43-pagination), [backend § 1.1 list response](../../references/business-rules/approval-line.md), and [frontend § 4.1 List query](../../../../wecms-web/docs/features/settings/approval-line.md). Reopen if a tenant grows past ~500 lines AND the panel renders slowly.
- ~~**OQ-4 — `apprvr_disp_ord` normalisation on Create.**~~ **Resolved here 2026-05-13 (proposed) — normalise on Create AND Update.** § 5.2 documents the deliberate divergence (JEX Create trusts the client; refactor re-numbers 1..N from array position). Awaiting Sonny / Mr. Jung sign-off; if rejected, fall back to legacy parity (Create trusts; Update normalises).
- ~~**OQ-5 — Admin-only access.**~~ **Resolved 2026-05-13 — Out of scope for MVP / refactor pass. Follow live JEX behaviour.** No `@PreAuthorize("hasRole('ADMIN')")` on write endpoints; any authenticated normal user with a valid tenant link can CRUD approval lines for their tenant — exactly as the live `comm_0222_*` action JSPs behave today (they enforce only `<login>Y` + `<NORM_USER_ONLY_YN>Y`, no admin gate). The conflicting claim in [`docs/jex/business-rules/approval.md`](../../../../docs/jex/business-rules/approval.md) § Authorization ("Admin users can configure approval chains for their business") is recorded as a JEX-side documentation conflict (see "Conflicts flagged" at the bottom of this doc), not a code gate. A future role-gating MR can revisit if/when menu-permission resolution lands. Decision is reflected in [§ 8 Permissions & audit](#8-permissions--audit) and [backend § 1 REST endpoint table](../../references/business-rules/approval-line.md) (`Auth = JWT`, no role annotation).
- **OQ-6 — Audit on the refactor side.** § 8 proposes writing `AUDIT_LOGS_INFM` rows on Create / Update / Delete even though JEX writes none. Aligns with `firm-banking-settings` / `contacts` precedent. Confirm.
- ~~**OQ-7 — `appr_line_no` generator.**~~ **Resolved 2026-05-13 — Reuse the existing DB function approach.** Extend the existing local `wb_get_next_val(...)` (V11) with the `('appr_line_infm', 'appr_line_no', biz_key, …)` branch to maintain legacy parity with `SeqHelper.getNextVal(util, idoCon, getEntityName(), "appr_line_no")` and stay aligned with the established refactor pattern documented in V11 / OQ-4 of [`docs/refactor-wecms/features/contacts/contacts.md`](contacts.md). No Java-side per-tenant counter, no new Postgres `SEQUENCE`. The Approval Line MR adds the new branch via a Flyway migration and routes the call through `common.legacy.LegacyFunctionRepository.nextVal(...)` — same funnel pattern the Contacts MR uses. This is a documented exception to the "no new legacy DB-function dependencies" rule in [`wecms-api/CLAUDE.md` § DB Function / Legacy Function Policy](../../../CLAUDE.md); the MR adds the new branch row to the "Surviving legacy functions" table with the retirement plan: drop the branch once the legacy WeCMS / WABOOKS systems no longer call `wb_get_next_val('appr_line_infm', …)`. Decision is reflected in [migration § 6](../../references/migration/approval-line.md), [backend § 6.2](../../references/business-rules/approval-line.md), and [planning B-2](../../archive/planning/phase-1/approval-line-plan.md).

---

## Conflicts flagged with existing docs

- **`docs/jex/business-rules/approval.md` § Configuration** claims approval lines live under WSVC `comm_0194_*` / `comm_0195_*` (lines 17-21). **Live JEX source uses `comm_0222_*`** — the business-rules doc is stale. The refactor cites `comm_0222_*` as the source of truth (verified against the live `WECMS DEV/` tree on 2026-05-13). A JEX-side doc cleanup PR to fix the business-rules entry is recommended but out of scope for this refactor MR.
- **`docs/jex/business-rules/approval.md` § Approval Flow** describes "parallel and sequential" approval configurations. **The `APPR_LINE_INFM_DETL` data model has only `APPRVR_DISP_ORD` — there is no parallel-vs-sequential flag.** All persisted approval lines today are sequential by `disp_ord`. The business-rules doc's parallel branch may describe future state or a different feature (Approval Box runtime rules); it is not realised in the line-configuration schema this MR ports. Cited here as a conflict to resolve in the JEX-side doc, not the refactor.
