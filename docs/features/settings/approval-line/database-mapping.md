# Approval Line — Database Mapping

Two tables, both in the PROD Active Table List. Schema verified column-for-column against JEX DEV
(`postgres_dev_server_jexframe`) and shipped in Flyway [V6](../../../../src/main/resources/db/migration/V6__appr_line_infm.sql) (`APPR_LINE_INFM`) + [V7](../../../../src/main/resources/db/migration/V7__appr_line_infm_detl.sql) (`APPR_LINE_INFM_DETL`).
Settings ERD: [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md).

---

## Tables

### `APPR_LINE_INFM` — approval-line master

| Column | Type (local / DEV) | Nullable | PK | Entity field |
|---|---|---|---|---|
| `BIZ_KEY` | `VARCHAR(20)` | NO | ✅ | `bizKey` |
| `APPR_LINE_NO` | `NUMERIC(15,0)` (V6; DEV parity) | NO | ✅ | `apprLineNo` (`Long`, `@JdbcTypeCode(NUMERIC)`) |
| `APPR_LINE_NM` | `VARCHAR(100)` | NO | | `apprLineNm` |
| `REG_ID` | `VARCHAR(100)` | NO | | `regId` |
| `REG_DTM` | `VARCHAR(14)` | NO | | `regDtm` (`yyyyMMddHHmmss` UTC string) |
| `MOD_ID` | `VARCHAR(100)` | NO | | `modId` |
| `MOD_DTM` | `VARCHAR(14)` | NO | | `modDtm` |

PK: `(BIZ_KEY, APPR_LINE_NO)` — composite, `@IdClass(ApprovalLineId.class)`.
Index beyond DEV: `ix_appr_line_infm_biz_reg_dtm (BIZ_KEY, REG_DTM DESC, APPR_LINE_NO DESC)` — materialises the default list sort.

### `APPR_LINE_INFM_DETL` — approver rows

| Column | Type (local / DEV) | Nullable | PK | Entity field |
|---|---|---|---|---|
| `BIZ_KEY` | `VARCHAR(20)` | NO | ✅ | `bizKey` |
| `APPR_LINE_NO` | `NUMERIC(15,0)` (V7; DEV parity) | NO | ✅ | `apprLineNo` (`Long`, `@JdbcTypeCode(NUMERIC)`) |
| `APPRVR_ID` | `VARCHAR(100)` | NO | ✅ | `apprvrId` (= `USER_INFM.USER_ID`) |
| `APPRVR_DISP_ORD` | `NUMERIC(1,0)` (V7; DEV parity) | NO | | `apprvrDispOrd` (`Integer`, 1..5, `@JdbcTypeCode(NUMERIC)`) |
| `APPRVR_NM` | `VARCHAR(100)` | NO | | `apprvrNm` (snapshot) |
| `APPRVR_POSI` | `VARCHAR(100)` | YES | | `apprvrPosi` (snapshot; written `""` not null) |
| `APPRVR_DEPT` | `VARCHAR(100)` | YES | | `apprvrDept` (snapshot; written `""` not null) |
| `REG_ID` | `VARCHAR(100)` | YES | | `regId` |
| `REG_DTM` | `VARCHAR(14)` | YES | | `regDtm` |
| `MOD_ID` | `VARCHAR(100)` | YES | | `modId` |
| `MOD_DTM` | `VARCHAR(14)` | YES | | `modDtm` |

PK: `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)` — composite, `@IdClass(ApprLineInfmDetlId.class)`.
**No FK constraint** to the master (DEV has none either) — the master→detail relationship is logical;
cascade-on-delete is service-layer (`deleteAllByBizKeyAndApprLineNo` then `deleteById`).

> **Why neither entity extends `BaseEntity`:** the audit columns are legacy `VARCHAR(14)`
> `yyyyMMddHHmmss` strings, stamped explicitly by the service from `TenantContext.currentUserId()` +
> `utcNow14()`, not the `TIMESTAMPTZ` columns `BaseEntity` manages.

---

## NUMERIC columns mapped via `@JdbcTypeCode` (Hibernate `validate` parity)

DEV declares `APPR_LINE_NO` as `NUMERIC(15,0)` and `APPRVR_DISP_ORD` as `NUMERIC(1,0)`, and the V6 / V7
schema keeps both columns `NUMERIC` (DEV parity). **There is no NUMERIC → BIGINT/INTEGER hotfix.**
Instead, the entities map the Java `Long` / `Integer` fields onto the unchanged `NUMERIC` columns via
`@JdbcTypeCode(SqlTypes.NUMERIC)`:

- `ApprovalLine.apprLineNo` (`Long`) → `APPR_LINE_INFM.APPR_LINE_NO` `NUMERIC(15,0)` (precision 15, scale 0)
- `ApprLineInfmDetl.apprLineNo` (`Long`) → `APPR_LINE_INFM_DETL.APPR_LINE_NO` `NUMERIC(15,0)`
- `ApprLineInfmDetl.apprvrDispOrd` (`Integer`) → `APPR_LINE_INFM_DETL.APPRVR_DISP_ORD` `NUMERIC(1,0)`

This keeps the local schema column-for-column identical to the live JEX DDL (the migration-safety
goal) while `ddl-auto: validate` still passes. The DB-level single-digit cap that `NUMERIC(1,0)` gives
`APPRVR_DISP_ORD` is reinforced by the service-layer 5-approver guard
(`ApprovalLineConstants.MAX_APPROVERS` + bean validation + `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`).

---

## PK generation — `APPR_LINE_NO`

Allocated via the legacy function funnel, **not** a Java generator:

```
ApprovalLineService.create
  → LegacyFunctionRepository.nextApprLineNo(bizKey)
  → wb_get_next_val('appr_line_infm', 'appr_line_no', biz_key)        -- V140 branch
  → SELECT COALESCE(MAX(appr_line_no::numeric), 0) + 1 FROM appr_line_infm WHERE biz_key = $3
```

Per-tenant `MAX+1` (not a sequence), copied **verbatim** from DEV. The `appr_line_infm.appr_line_no`
branch lives in the consolidated [V140](../../../../src/main/resources/db/migration/V140__wb_get_next_val.sql)
`wb_get_next_val` function (the single executable funnel). This is a documented exception to the "no new
legacy DB-function dependencies" rule — see `wecms-api/CLAUDE.md` → "Surviving legacy functions" (retire
when the legacy WeCMS / WABOOKS systems no longer call `wb_get_next_val('appr_line_infm', …)`).

---

## Column → field → DTO → UI mapping

The requested traceability chain. Wire is lowercase snake_case (global Jackson `SNAKE_CASE`);
in-memory frontend types are camelCase (translated in the feature mapper, see
[wecms-web/docs/features/settings/approval-line/api-consumption.md](../../../../../wecms-web/docs/features/settings/approval-line/api-consumption.md)).

### Master

| Concept | Legacy column | Refactor entity field | Wire / DTO field | Frontend in-memory | UI |
|---|---|---|---|---|---|
| Approval Line No | `APPR_LINE_NO` | `ApprovalLine.apprLineNo` | `appr_line_no` | `apprLineNo` | row id / path param |
| Approval Line Name | `APPR_LINE_NM` | `ApprovalLine.apprLineNm` | `appr_line_nm` | `apprLineNm` | "Approval Line Name" column / name input |
| Created Date | `REG_DTM` | `ApprovalLine.regDtm` | `reg_dtm` | `regDtm` | "Create Date" column (`yyyy-MM-dd`) |
| Updated Date | `MOD_DTM` | `ApprovalLine.modDtm` | (not on the wire) | — | — |
| Status | _(no status column — every row is active; no `DEL_YN`)_ | — | — | — | — |

### Approver (detail)

| Concept | Legacy column | Refactor entity field | Wire / DTO field | Frontend in-memory | UI |
|---|---|---|---|---|---|
| Approval Order / Display Order | `APPRVR_DISP_ORD` | `ApprLineInfmDetl.apprvrDispOrd` | `apprvr_disp_ord` (read-only out; **server-assigned**, never in) | `dispOrd` | `#` column (renders `index+1`) |
| Approver User ID | `APPRVR_ID` | `ApprLineInfmDetl.apprvrId` | `apprvr_id` | `id` | (hidden; picker key) |
| Approver Name | `APPRVR_NM` | `ApprLineInfmDetl.apprvrNm` | `apprvr_nm` | `name` | "Approver name" / chain name cell |
| Approver Position | `APPRVR_POSI` | `ApprLineInfmDetl.apprvrPosi` | `apprvr_posi` | `position` | chain "Position" cell |
| Approver Department | `APPRVR_DEPT` | `ApprLineInfmDetl.apprvrDept` | `apprvr_dept` | `department` | chain "Department" cell |
| Approver Avatar | `USER_BUSI_CONN_INFM.USER_IMG` (resolved) | — (computed in service) | `user_img_url` (slug URI or `null`) | `userImgUrl` | approver avatar (fallback when null) |

> **Status / Display Order / Approval Order note.** The data model has a single ordering column,
> `APPRVR_DISP_ORD` — it serves as **both** display order and approval sequence (there is no separate
> "approval order" column and no parallel-vs-sequential flag; all lines are sequential by
> `disp_ord`). There is no `Status` column on either table — every persisted row is active (no
> soft-delete). The PRD's "Status / Created Date / Updated Date" columns map to: Status → n/a,
> Created Date → `REG_DTM`, Updated Date → `MOD_DTM`.

---

## Referenced tables (read-only joins)

| Table | Used for |
|---|---|
| `USER_BUSI_CONN_INFM` | tenant-link guard (`findAllByUserIdInAndBizKeyAndDelYn`) + `USER_IMG` for avatar URL resolution |
| `USER_INFM` | (legacy resolves approver name here; refactor uses the snapshot in `APPR_LINE_INFM_DETL` — no live join on read) |

No `AUDIT_LOGS_INFM` participation (this feature writes no audit rows — see
[functional-specification.md §Audit](functional-specification.md#audit--permissions)).
