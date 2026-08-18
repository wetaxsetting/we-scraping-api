# Approval Line — JEX → Spring + Next.js Migration Mapping


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../database/flyway-migration-baseline.md) §5.

**Companion to:** [docs/refactor-wecms/features/approval/approval-line.md](../../features/settings/approval-line.md)
**JEX functional spec:** WECMS DEV — `comm_0222_*` services + `ApprLineInfm` / `ApprLineInfmDetl` entities (`APPR_LINE_INFM` + `APPR_LINE_INFM_DETL` tables)
**Refactor target package:** `com.kosign.wecms.domain.setting.approvalline`
**Phase:** 1 — design only.

This document is the side-by-side translation table. When Phase 2 implementation begins, every row here becomes a verification target.

---

## 1. Service mapping table

| JEX service | JEX action JSP (file:line) | JEX WSVC (file) | JEX IDO used | Refactor controller method | Refactor service method | HTTP / URI |
|---|---|---|---|---|---|---|
| `comm_0222_001_r001` (List) | [comm_0222_001_r001_act.jsp:36-153](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp) | [WSVC.comm_0222_001_r001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_001_r001.xml) | `IDO.APPR_LINE_INFM_DETL_R002.xml` (master + `json_agg` approvers) + `IDO.APPR_LINE_INFM_DETL_R002_CNT` (count) + `IDO.USER_BUSI_CONN_INFM_R001` (per-row image lookup) | `ApprovalLineController.list(q, sort)` | `ApprovalLineService.list(criteria)` | `GET /api/v1/settings/approval-line?q=…&sort=…` |
| `comm_0222_002_c001` (Create) | [comm_0222_002_c001_act.jsp:28-111](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp) | [WSVC.comm_0222_002_c001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_002_c001.xml) | `IDO.APPR_LINE_INFM_C001`, `IDO.APPR_LINE_INFM_DETL_C001`, `wb_get_next_val('appr_line_infm','appr_line_no',biz_key)` | `ApprovalLineController.create(req)` | `ApprovalLineService.create(req, principal)` + `AuditLogService.append(...)` | `POST /api/v1/settings/approval-line` |
| `comm_0222_003_r001` (Detail) | [comm_0222_003_r001_act.jsp:34-117](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_r001_act.jsp) | [WSVC.comm_0222_003_r001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_003_r001.xml) | `IDO.APPR_LINE_INFM_DETL_R002` (single-line variant via dynamic) | `ApprovalLineController.detail(apprLineNo)` | `ApprovalLineService.detail(apprLineNo)` | `GET /api/v1/settings/approval-line/{apprLineNo}` |
| `comm_0222_003_u001` (Update) | [comm_0222_003_u001_act.jsp:36-198](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp) | [WSVC.comm_0222_003_u001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_003_u001.xml) | `IDO.APPR_LINE_INFM_R001` (PK check), `IDO.APPR_LINE_INFM_U001`, `IDO.APPR_LINE_INFM_DETL_R001` (existing-by-FK), `IDO.APPR_LINE_INFM_DETL_C001` / `U001` / `D001` | `ApprovalLineController.update(apprLineNo, req)` | `ApprovalLineService.update(apprLineNo, req, principal)` + `AuditLogService.append(...)` | `PUT /api/v1/settings/approval-line/{apprLineNo}` |
| `comm_0222_001_d001` (Delete) | [comm_0222_001_d001_act.jsp:31-113](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_d001_act.jsp) | [WSVC.comm_0222_001_d001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_001_d001.xml) | `IDO.APPR_LINE_INFM_R001` (existence check), `IDO.APPR_LINE_INFM_DETL_D002` (delete by FK), `IDO.APPR_LINE_INFM_D001` | `ApprovalLineController.delete(apprLineNo)` | `ApprovalLineService.delete(apprLineNo, principal)` + `AuditLogService.append(...)` | `DELETE /api/v1/settings/approval-line/{apprLineNo}` |

---

## 2. JEX DAO → Spring Repository mapping

| JEX | Refactor | Notes |
|---|---|---|
| `wabooks.dao.ApprLineInfmDAO` (master CRUD) | `ApprovalLineRepository extends JpaRepository<ApprovalLine, ApprovalLineId>` | Composite-id repo. PK class is `ApprovalLineId(bizKey, apprLineNo)`. |
| `ApprLineInfmDAO.findByPk(util, idoCon, pk)` | `repo.findById(id)` | `id = new ApprovalLineId(TenantContext.currentBizKey(), apprLineNo)`. |
| `ApprLineInfmDAO.findByDynamic(util, idoCon, dynamic0)` | n/a — list goes through `ApprovalLineDetailViewRepository` | The dynamic-WHERE pattern is replaced by JPQL parameters; the joined `json_agg` view becomes either a JPQL `JOIN FETCH` plus in-memory grouping, or a read-only `V_APPROVAL_LINE_WITH_APPROVERS` SQL view backed by an `@Immutable` entity. Choice fixed in [backend design § 3.3](../business-rules/approval-line.md). |
| `ApprLineInfmDAO.create(util, idoCon, entity)` | `repo.save(entity)` (new PK) | Service generates `appr_line_no` via `wb_get_next_val(...)` (legacy DB function, new branch added by this MR — see § 6) before calling `save`. |
| `ApprLineInfmDAO.update(util, idoCon, entity)` | `repo.save(entity)` (existing PK) | Service sets `mod_id = principal.userId()` + `mod_dtm = utcNow14()` (yyyyMMddHHmmss) before save. |
| `ApprLineInfmDAO.deleteByPk(util, idoCon, pk)` | `repo.deleteById(id)` | After `findById(...).orElseThrow(WBE_APPROVAL_LINE_NOT_FOUND)`. |
| `wabooks.dao.ApprLineInfmDetlDAO` (detail CRUD) | `ApprovalLineApproverRepository extends JpaRepository<ApprovalLineApprover, ApprovalLineApproverId>` | Composite-id repo. PK class is `ApprovalLineApproverId(bizKey, apprLineNo, apprvrId)`. |
| `ApprLineInfmDetlDAO.findByFk(util, idoCon, criteria)` (filter on `bizKey + apprLineNo`) | `detailRepo.findAllByBizKeyAndApprLineNo(bizKey, apprLineNo)` (ordered by `apprvrDispOrd ASC`) | Derived method. |
| `ApprLineInfmDetlDAO.create(util, idoCon, entity)` | `detailRepo.save(entity)` (new PK) | Service assigns `apprvrDispOrd = i + 1` based on request array index — see [feature § 5.2](../../features/settings/approval-line.md). |
| `ApprLineInfmDetlDAO.update(util, idoCon, entity)` | `detailRepo.save(entity)` (existing PK) | Service applies new disp_ord / name / posi / dept + audit fields. |
| `ApprLineInfmDetlDAO.deleteByPk(util, idoCon, pk)` | `detailRepo.deleteById(id)` | For each `removedApprvrId` during diff sync. |
| `ApprLineInfmDetlDAO.deleteByFk(util, idoCon, entity)` (used in Delete to wipe all detail rows in one shot) | `detailRepo.deleteAllByBizKeyAndApprLineNo(bizKey, apprLineNo)` | Custom `@Modifying @Query` on the detail repo — single DELETE statement (faster than per-row `deleteById` in a loop). |
| `wabooks.dao.UserBusiConnInfmDAO.findByPk(...)` (used at [comm_0222_001_r001_act.jsp:121-123](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp) to load `USER_IMG`) | `domain/auth/UserBusiConnInfmRepository.findByUserIdAndBizKeyAndDelYn(...)` — **already exists** (used by `AuthService`) | For the `user_img_url` resolution per row. Refactor returns `null` when no image; URL `"/api/v1/users/{apprvrId}/image"` when one exists. The streaming endpoint is out of scope (see feature § 4.5 + OQ-1). |

### 2.1 `JexConnection` / `IDOConnection` → JPA `@Transactional`

| JEX pattern | Refactor pattern |
|---|---|
| `JexConnection idoCon = JexConnectionManager.createIDOConnection();` (every action JSP, ~line 70-90) | Spring-managed connection — no manual lifecycle. |
| `idoCon.beginTransaction();` (e.g. [comm_0222_002_c001_act.jsp:78](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp)) | `@Transactional` on `ApprovalLineService.create / update / delete`. |
| `IdoUtils.commitAndCloseQuietly(idoCon);` (e.g. [comm_0222_002_c001_act.jsp:108](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp)) | implicit on `@Transactional` exit. |
| `WbRollbackException(util, WbMessageCode.WBW1164)` (max-5 cap) | `BusinessException("WBE_APPROVAL_LINE_TOO_MANY_APPROVERS")` — handler maps to HTTP 200 with `status.error = true`. |
| `WbRollbackException(util, WbMessageCode.WBE0404)` (delete missing line) | `BusinessException("WBE_APPROVAL_LINE_NOT_FOUND")`. |

---

## 3. JEX IDO → Spring DTO / repository query mapping

| JEX IDO | SQL essence | Refactor equivalent |
|---|---|---|
| `IDO.APPR_LINE_INFM_R001.xml` (read master by PK + dynamic) | `SELECT BIZ_KEY, APPR_LINE_NO, APPR_LINE_NM, REG_ID, REG_DTM, MOD_ID, MOD_DTM FROM APPR_LINE_INFM WHERE 1=1 ??` ([file:24-44](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.APPR_LINE_INFM_R001.xml)) | `repo.findById(id)` for the PK case. The dynamic variant isn't used in the refactor flow. |
| `IDO.APPR_LINE_INFM_DETL_R001.xml` (read detail by PK + dynamic) | `SELECT … FROM APPR_LINE_INFM_DETL WHERE 1=1 ??` ([file:24-52](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.APPR_LINE_INFM_DETL_R001.xml)) | `detailRepo.findAllByBizKeyAndApprLineNo(bizKey, apprLineNo)` for the per-line load (the only filter used in the refactor flow). |
| `IDO.APPR_LINE_INFM_DETL_R002.xml` (list + json_agg approvers) | `SELECT … (SELECT json_agg(json_build_object(…) ORDER BY APPRVR_DISP_ORD ASC) FROM APPR_LINE_INFM_DETL ALID WHERE ALID.BIZ_KEY = ALI.BIZ_KEY AND APPR_LINE_NO = ALI.APPR_LINE_NO) AS APPROVERS FROM APPR_LINE_INFM ALI WHERE 1=1 ??` ([file:24-56](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.APPR_LINE_INFM_DETL_R002.xml)) | List service: `approvalLineRepo.findAllByBizKey(bizKey)` + per-row eager fetch of approvers via `detailRepo.findAllByBizKeyAndApprLineNoInOrderByDispOrd(...)` (single batched IN-query, then group in-memory). **No `json_agg` SQL view in the refactor** — the JEX `json_agg` exists because JEX has no clean way to express nested records; JPA can. Per-row image lookup happens after this in the service, not inside the SQL. |
| `IDO.APPR_LINE_INFM_DETL_R002_CNT` (referenced at [comm_0222_001_r001_act.jsp:143](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp)) | `SELECT COUNT(*) FROM APPR_LINE_INFM WHERE 1=1 ??` (sibling IDO) | **Not used in MVP** — refactor skips pagination (feature § 4.3), so `total_count` is just `list.size()`. |
| `IDO.APPR_LINE_INFM_C001` | `INSERT INTO APPR_LINE_INFM (…)` | `repo.save()` (new entity). |
| `IDO.APPR_LINE_INFM_U001` | `UPDATE APPR_LINE_INFM SET … WHERE BIZ_KEY=? AND APPR_LINE_NO=?` | `repo.save()` (existing entity, dirty-tracked). |
| `IDO.APPR_LINE_INFM_D001` | `DELETE FROM APPR_LINE_INFM WHERE BIZ_KEY=? AND APPR_LINE_NO=?` | `repo.deleteById(id)`. |
| `IDO.APPR_LINE_INFM_DETL_C001` / `_U001` / `_D001` | Per-row CRUD on detail rows | `detailRepo.save(...)` / `detailRepo.deleteById(...)`. |
| `IDO.APPR_LINE_INFM_DETL_D002` (referenced at [comm_0222_001_d001_act.jsp:36-39](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_d001_act.jsp)) | `DELETE FROM APPR_LINE_INFM_DETL WHERE 1=1 ??` (delete-by-FK) | `detailRepo.deleteAllByBizKeyAndApprLineNo(...)` — custom `@Modifying @Query`. |

---

## 4. WSVC field → DTO field mapping (lowercase snake_case wire)

### 4.1 List response (`comm_0222_001_r001` → `ApprovalLineListResponse`)

WSVC source: [`WSVC.comm_0222_001_r001.xml:31-55`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_001_r001.xml). Wire form below (Java DTO field names are camelCase; serialized as snake_case via the global Jackson strategy — see [`wecms-api/CLAUDE.md` § API Naming](../../../CLAUDE.md)).

| WSVC `<item id>` (location) | WSVC length | DB column / type | Java DTO field | Wire JSON |
|---|---|---|---|---|
| `TOT_CNT` (legacy top-level) | 15 | — | `Integer totalCount` | `total_count` (always equals `list.size()` — no pagination, see feature § 4.3) |
| `REC` (legacy top-level → REC.subRule_1) | RECORD | — | `List<ApprovalLineRow> approvalLines` | `approval_lines` |
| `REC[].APPR_LINE_NO` | 15 | `appr_line_no NUMERIC(15,0)` | `Long apprLineNo` | `appr_line_no` |
| `REC[].APPR_LINE_NM` | 100 | `appr_line_nm VARCHAR(100)` | `String apprLineNm` | `appr_line_nm` |
| `REC[].REG_DTM` | 14 | `reg_dtm VARCHAR(14)` | `String regDtm` | `reg_dtm` |
| `REC[].REC1` (subRule_0) | RECORD | — | `List<ApprovalLineApproverRow> approvers` | `approvers` |
| `REC[].REC1[].APPR_LINE_NO` | 15 | `appr_line_no` (denormalised in legacy JSON) | **dropped** — redundant with parent | — |
| `REC[].REC1[].APPRVR_DISP_ORD` | 1 | `apprvr_disp_ord NUMERIC(1,0)` | `Integer apprvrDispOrd` | `apprvr_disp_ord` |
| `REC[].REC1[].APPRVR_ID` | 100 | `apprvr_id VARCHAR(100)` | `String apprvrId` | `apprvr_id` |
| `REC[].REC1[].APPRVR_NM` | 100 | `apprvr_nm VARCHAR(100)` | `String apprvrNm` | `apprvr_nm` |
| (added by act JSP, not in WSVC subRule) | — | `apprvr_posi VARCHAR(100)` | `String apprvrPosi` | `apprvr_posi` |
| (added by act JSP) | — | `apprvr_dept VARCHAR(100)` | `String apprvrDept` | `apprvr_dept` |
| `REC[].REC1[].USER_IMG_URL` | 200 | computed at JSP (`comm_0222_001_r001_act.jsp:121-130`) | `String userImgUrl` (nullable) | `user_img_url` |

> The legacy WSVC subRule_0 is sparse — it lists only the fields the JSP explicitly puts via `recItem.putAll(map)`. The action JSP actually emits `APPRVR_POSI` / `APPRVR_DEPT` too (from the `json_agg` map). Refactor formalises the full row shape via the typed DTO and drops the redundant `APPR_LINE_NO` denormalisation inside the approver row.
>
> The refactor replaces the legacy `USER_IMG_URL = "../img/img_thumb.png"` placeholder + `"szon_0012_003_r002.act?USER_ID={id}"` URLs with **slug-based REST URIs** — `null` when no image, `/api/v1/users/{apprvrId}/image` when one exists (see feature § 4.5).

### 4.2 Detail response (`comm_0222_003_r001` → `ApprovalLineDetail`)

WSVC source: [`WSVC.comm_0222_003_r001.xml:31-49`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_003_r001.xml). Same row shape as 4.1 minus the `REC1` wrapper — single line, flat. Refactor returns the same `ApprovalLineRow` type used by the list (which already nests `approvers[]`), so the detail endpoint is a strict subset:

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "appr_line_no": 19,
    "appr_line_nm": "A Plus (Exp) +",
    "reg_dtm": "20250727105815",
    "approvers": [
      { "apprvr_disp_ord": 1, "apprvr_id": "…", "apprvr_nm": "…", "apprvr_posi": "…", "apprvr_dept": "…", "user_img_url": null }
    ]
  }
}
```

### 4.3 Create request (`comm_0222_002_c001` IN → `ApprovalLineCreateRequest`)

WSVC source: [`WSVC.comm_0222_002_c001.xml:31-47`](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_002_c001.xml). Validation backed by [`comm_0222_002_c001_act.jsp:28-41`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp).

```java
public record ApprovalLineCreateRequest(
    @NotBlank @Size(max = 100) String apprLineNm,
    @NotNull @Size(min = 1, max = 5) @Valid List<ApprovalLineApproverInput> approvers
) {}

public record ApprovalLineApproverInput(
    @NotBlank @Size(max = 100) String apprvrId,
    @NotBlank @Size(max = 100) String apprvrNm,
    @Size(max = 100) String apprvrPosi,
    @Size(max = 100) String apprvrDept
    // apprvr_disp_ord is intentionally absent — service assigns 1..N from array position.
) {}
```

Wire JSON (snake_case):

```json
{
  "appr_line_nm": "Approval Line A",
  "approvers": [
    { "apprvr_id": "user1@example.com", "apprvr_nm": "User One",   "apprvr_posi": "Manager", "apprvr_dept": "Finance" },
    { "apprvr_id": "user2@example.com", "apprvr_nm": "User Two",   "apprvr_posi": "Director","apprvr_dept": "Finance" }
  ]
}
```

### 4.4 Update request (`comm_0222_003_u001` IN → `ApprovalLineUpdateRequest`)

`apprLineNo` comes from the path; the body carries the editable trio:

```java
public record ApprovalLineUpdateRequest(
    @NotBlank @Size(max = 100) String apprLineNm,
    @NotNull @Size(min = 1, max = 5) @Valid List<ApprovalLineApproverInput> approvers
) {}
```

Diff-sync semantics (matches legacy [comm_0222_003_u001_act.jsp:104-194](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_003_u001_act.jsp)):

- Compare incoming `approvers[*].apprvr_id` set against existing detail rows.
- `existing ∩ incoming` → UPDATE existing row (new disp_ord, new name, new posi/dept, audit fields).
- `incoming - existing` → INSERT new detail row.
- `existing - incoming` → DELETE removed detail row.

Duplicate `apprvr_id` inside `approvers[]` rejected pre-DB with `WBE_APPROVAL_LINE_DUP_APPROVER` (feature § 5.3) — defensive over JEX's implicit Map-overwrite.

### 4.5 Delete request (`comm_0222_001_d001` IN)

Path only — `DELETE /api/v1/settings/approval-line/{apprLineNo}`. No body. Validation backed by [`comm_0222_001_d001_act.jsp:31-33`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_d001_act.jsp).

### 4.6 Stripped legacy artifacts

| JEX entity / DTO | Refactor stance |
|---|---|
| `@JsonProperty("APPR_LINE_NM")` etc. on `wabooks.entity.ApprLineInfm` ([ApprLineInfm.java:26-46](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfm.java)) | **Stripped.** Java field name + global `JacksonConfig` SNAKE_CASE strategy emits the right wire name. ([`wecms-api/CLAUDE.md` § Refactoring rules step 1](../../../CLAUDE.md).) |
| `@JsonSerialize(using = DateTime14Serializer.class)` on `regDtm` / `modDtm` ([ApprLineInfm.java:34-47](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfm.java)) | **Removed.** Legacy column type is `varchar(14)` — refactor entity stores `String` directly to match DEV (verified via `information_schema.columns` 2026-05-13). No joda `DateTime` conversion. |
| `WbUser regUser` / `WbUser modUser` (joda-time + WbUser wrappers) | **Replaced** with plain `String regId` / `String modId`. The entity stores the user-id string only; the wire DTOs surface `reg_dtm` and (optionally) `reg_id`. |
| `BusiInfmPk` superclass on `ApprLineInfmPk` ([ApprLineInfmPk.java:19](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfmPk.java)) | **Replaced.** Refactor uses an explicit `@IdClass(ApprovalLineId)` with two fields (`bizKey`, `apprLineNo`). No multi-level PK inheritance. |
| `IDODynamic` filter assembly ([ApprLineInfmPk.java:43-56](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ApprLineInfmPk.java)) | **Removed.** All filters become JPQL parameters or repository derived methods. |
| `JexData.put / getString` in action JSPs | **Removed.** Inputs / outputs are typed records; deserialization through the global `ObjectMapper`. |
| `ObjectUtils.readValue(row.getString("APPROVERS"), …TypeReference<List<Map<String, String>>>…)` ([comm_0222_001_r001_act.jsp:115-118](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp)) | **Removed.** Refactor batch-loads detail rows via JPA and groups in-memory — no SQL → JSON → Java round-trip. |
| `validateRecord` / `validateRequired` helpers ([comm_0222_002_c001_act.jsp:30-41](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_002_c001_act.jsp)) | **Replaced** by Bean Validation (`@NotBlank`, `@NotNull`, `@Size`) on the record components + a service-layer guard for the duplicate-approver + tenant-link checks (§ 5.3, § 5.4 of feature spec). |

---

## 5. JEX TypeScript event handler → Next.js hook mapping

The static-UI panel + dialog + popover components already exist under `wecms-web/components/settings/approval-line/`. This mapping is the wire-up checklist for Phase 3.

| JEX TS handler (file:line) | Behavior | Next.js component target | New hook / API call |
|---|---|---|---|
| `comm_0222_001.ts onload()` → list fetch | Initial list load | [`ApprovalLinePanel.tsx`](../../../../wecms-web/components/settings/approval-line/ApprovalLinePanel.tsx) | `useApiData(fetchApprovalLines, ...)` — replaces the current `mockApprovalLineRows` import. |
| `comm_0222_001.ts add-button click` → `openLayerPopup('comm_0222_002.act')` | Open Add dialog | `ApprovalLinePanel` "+ Add Approval line" button → `setDialog({ mode: "add" })` | already wired. Dialog onSubmit → `createApprovalLine(req)`. |
| `comm_0222_001.ts edit row click` → `openLayerPopup('comm_0222_003.act?APPR_LINE_NO=…')` | Open Edit dialog | `ApprovalLinePanel` row Edit → `setDialog({ mode: "update", row })` | already wired. Dialog onMount fetches `getApprovalLine(apprLineNo)` to refresh approver list (the panel row carries only the primary approver — see `seedApproversFromRow` at [`ApprovalLinePanel.tsx:23`](../../../../wecms-web/components/settings/approval-line/ApprovalLinePanel.tsx)). |
| `comm_0222_001.ts delete row click` → confirm popup → `comm_0222_001_d001.jct` | Confirm-and-delete | `ApprovalLinePanel` row Delete → `useDialog().open({ type: "confirmation", … })` → `deleteApprovalLine(apprLineNo)` then invalidate list. | onConfirm. |
| `comm_0222_002.ts onload` → field state + Save button enable | Validate enable | [`ApprovalLineDialog.tsx`](../../../../wecms-web/components/settings/approval-line/ApprovalLineDialog.tsx) | React form state — RHF + Zod. |
| `comm_0222_002.ts fnAddApprover()` → open approver picker, append to chain (max 5) | Add approver to chain | [`AddApproverPopover.tsx`](../../../../wecms-web/components/settings/approval-line/AddApproverPopover.tsx) — already shipped | `useApiData(fetchTenantUsers, ...)` against a new `domain/auth/UserController.searchByTenant(...)` endpoint (or `domain/setting/approvalline/ApprovalLineController.users(...)` — see backend doc § 8). |
| `comm_0222_002.ts fnRemoveApprover(index)` | Remove approver | `ApprovalLineDialog` chain row "Remove" | local state only. |
| `comm_0222_002.ts fnReorderApprover(from, to)` | Reorder approver | `ApprovalLineDialog` drag handle | local state only — display order computed on submit (1..N by array position). |
| `comm_0222_002.ts fuctionCreateApprLine()` → POST + close + refetch | Create submit | `ApprovalLineDialog` onSubmit when `mode === "add"` | `createApprovalLine(req)` → `onSuccess()` from the panel → list invalidation. |
| `comm_0222_003.ts loadDataToUpdate(apprLineNo)` | Detail pre-fill | `ApprovalLineDialog` mount in `mode === "update"` | `useApiData(getApprovalLine, [apprLineNo])`. |
| `comm_0222_003.ts fuctionUpdateApprLine()` → PUT + close + refetch | Update submit | `ApprovalLineDialog` onSubmit when `mode === "update"` | `updateApprovalLine(apprLineNo, req)` → `onSuccess()`. |

### 5.1 Frontend file inventory (Phase 3 wire-up checklist)

| New / modified file | Purpose |
|---|---|
| [`lib/types/api.ts`](../../../../wecms-web/lib/types/api.ts) | Add `ApiApprovalLineRow`, `ApiApprovalLineApproverRow`, `ApiApprovalLineListResponse`, `ApiApprovalLineCreateRequest`, `ApiApprovalLineUpdateRequest`. Wire fields lowercase snake_case. |
| `lib/api/approvalLine.ts` (NEW) | `fetchApprovalLines(query)`, `getApprovalLine(apprLineNo)`, `createApprovalLine(req)`, `updateApprovalLine(apprLineNo, req)`, `deleteApprovalLine(apprLineNo)`. Snake_case → camelCase mapping via the wire types. |
| `lib/api/mappers.ts` | Add `mapApprovalLineRow`, `mapApprovalLineApproverRow`. Mirrors the `firm-banking-history` mapper pattern. |
| [`components/settings/approval-line/ApprovalLinePanel.tsx`](../../../../wecms-web/components/settings/approval-line/ApprovalLinePanel.tsx) | Replace `mockApprovalLineRows` import with `useApiData(fetchApprovalLines)`. Keep visual layout. |
| [`components/settings/approval-line/ApprovalLineDialog.tsx`](../../../../wecms-web/components/settings/approval-line/ApprovalLineDialog.tsx) | Wire onSubmit to `createApprovalLine` / `updateApprovalLine`. Replace `seedApproversFromRow` (which fakes a 1-approver chain) with `useApiData(getApprovalLine, [row.apprLineNo])` on mount. |
| [`components/settings/approval-line/AddApproverPopover.tsx`](../../../../wecms-web/components/settings/approval-line/AddApproverPopover.tsx) | Replace `APPROVER_POOL` static import with `useApiData(fetchTenantUsers)`. |
| `approverMockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) | Demote `APPROVER_POOL` to offline-dev affordance only. Keep `MAX_APPROVERS = 5` constant + `ApproverRow` type (still used by the picker). |
| `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) | Demote to offline-dev only. |
| [`components/settings/approval-line/types.ts`](../../../../wecms-web/components/settings/approval-line/types.ts) | Extend `ApprovalLineRow` so the in-memory shape matches the API: add `apprLineNo: number`, `approvers: ApproverRow[]`, `regDtm: string`. Keep `approverCount` derivable (`approvers.length - 1`) or drop in favour of computing in the table component. |

Form-state strategy (matches feature § 5.7):

- **Pessimistic** create / update — dialogs stay open until 200 OK. The `<SystemDialog type="success">` fires from the panel onSuccess hook after invalidation.
- **No unsaved-changes guard** — JEX has none; the dialog is the unit of work. (Confirm against Figma at Phase 3.)
- **Error rendering** — every `ApiCallError` flows through the global `<SystemDialog type="error">` via `useDialog()`, with the `trace_id` displayed for support.

---

## 6. `appr_line_no` generator — legacy DB function

JEX's `ApprLineInfmDAO.create(...)` calls `SeqHelper.getNextVal(util, idoCon, getEntityName(), "appr_line_no")` which internally invokes `wb_get_next_val('appr_line_infm', 'appr_line_no', biz_key, …)`. The refactor's local DB already has `wb_get_next_val(...)` (V11 — see [`wecms-api/CLAUDE.md` § Database (LOCAL)](../../../CLAUDE.md)) covering `BIZ_KEY` and `EMPL_NO` branches only.

This MR **adds a new branch** to the same function:

- New Flyway migration `V{N}__appr_line_no_generator_branch.sql` that re-creates `wb_get_next_val(...)` with an additional clause for `('appr_line_infm', 'appr_line_no', biz_key, …)`.
- The branch reads the current max `appr_line_no` for the tenant + 1 (legacy convention — verified on DEV — refactor copies the same pattern from V11's existing `EMPL_NO` branch).
- The Java service calls the function through the existing `common.legacy.LegacyFunctionRepository` funnel (per the same pattern used by `contacts` § OQ-4 — see [`docs/refactor-wecms/features/contacts/contacts.md`](../../features/settings/contacts.md)). **No scattered `@Query(nativeQuery = true)` per repository.**
- This is a **documented exception** to the "no new legacy DB-function dependencies" rule in [`wecms-api/CLAUDE.md` § DB Function / Legacy Function Policy](../../../CLAUDE.md). The Approval Line MR adds the row to the "Surviving legacy functions" table with the retirement plan: drop the branch and migrate to a Java-side per-tenant generator once the legacy WeCMS / WABOOKS systems no longer call `wb_get_next_val('appr_line_infm', …)`. See feature § 14 OQ-7.

---

## 7. Audit log mapping

**JEX writes no audit rows for `comm_0222_*`** — verified by reading every `_act.jsp` in the inventory; none import `AuditLogsInfmDAO` or call `auditLogsInfm.create(...)`. The legacy implementation predates the audit-log helper rollout for `comm_0220_*` (Firm Banking Settings).

**Refactor keeps legacy parity — writes NO audit rows.** Feature § 14 OQ-6 resolved 2026-05-13 (PO decision): Approval Line audit history is not a current business requirement; the legacy gap is acceptable.

Concrete consequences for the refactor implementation:

- `ApprovalLineService` does not inject `AuditLogService` and does not call `auditLog.append(...)` from any of `create / update / delete`.
- No `AUDIT_LOGS_INFM` rows are written with `WSVC_ID = 'approval-line'`.
- No `audit.approval_line.*` i18n keys are added to `messages*.properties`.
- This is a deliberate divergence from the `firm-banking-settings` / `contacts` / `vas-account` migration mappings, which DO add audit on the refactor side.
- A future MR can revisit if/when audit-history requirements land for Approval Line. The shared `common/auditlog/service/AuditLogService` remains untouched.

---

## 8. ML / dictionary code mapping

JEX dictionary references found in the `comm_0222_*` flow are limited to the view JSPs (`comm_0222_001_view.jsp` / `comm_0222_002_view.jsp` / `comm_0222_003_view.jsp`) and the TS-side `.ML.get(...)` calls inside `comm_0222_*.ts`. Refactor handling:

| JEX ML code (range) | Surface | Refactor handling |
|---|---|---|
| `comm_0222_001_m001 … mNNN` (panel labels) | `comm_0222_001_view.jsp` | Frontend i18n via the in-house `useTranslation()` — keys under `settings.approval_line.*` (see feature § 9). |
| `comm_0222_002_m001 … mNNN` (Add dialog labels) | `comm_0222_002_view.jsp` | Same, `settings.approval_line.dialog.add_*` + `.popover.*`. |
| `comm_0222_003_m001 … mNNN` (Edit dialog labels) | `comm_0222_003_view.jsp` | Same, `settings.approval_line.dialog.edit_*`. |
| `comm_0222_*_j001…` (TS-side via `.ML.get(...)`) | TS files | Already replaced by inline literals in the static-UI components — Phase 3 reviews each against the JEX TS to ensure no dictionary string is missed. |
| Audit activity / detail strings (NEW — none in JEX) | Refactor `messages*.properties` | New keys under `audit.approval_line.*` (consumed by `AuditLogService`). |

JEX ML code IDs are **not** reused verbatim in the refactor ([`wecms-api/CLAUDE.md` § i18n](../../../CLAUDE.md)). Audit DB rows reference the **refactor message values** in EN / KO / VI / KM, not the JEX ID.

---

## 9. Permission / WSVC ext-flag mapping

| WSVC `<ext>` flag | JEX meaning | Refactor handling |
|---|---|---|
| `<WTA_USER_ALLOW_YN>N` (all five WSVCs) | WTA users not allowed | Excluded by the existing `JwtAuthenticationFilter` — WTA users never reach this app surface. |
| `<NORM_USER_ONLY_YN>Y` (all five WSVCs) | Only normal users | Same. |
| `<login>Y` | Login required | Default `SecurityConfig` JWT guard. |
| `<mntLogYn>Y` | Maintenance log enabled | Out of scope — JEX framework feature with no Spring counterpart (refactor's `TraceIdFilter` covers the equivalent observability slot). |
| `<trTp>U` | Transaction type "U" (write?) | JEX framework metadata; not surfaced in the refactor. |

Authority gating (`ADMIN` vs `USER`) is **not** enforced in legacy `comm_0222_*` code. Refactor defaults to live-JEX behaviour (any normal user can CRUD) pending PO decision — feature § 14 OQ-5.

---

## 10. Unmapped legacy concepts

Items the refactor explicitly does **not** carry forward:

| JEX concept | Why dropped |
|---|---|
| `JexData` request / response envelope | Replaced by typed Java records + `ApiResponse<T>`. |
| `IDODynamic` `??` placeholder query assembly | Replaced by JPQL parameters or derived JPA queries. |
| `BusiInfmPk` multi-level PK inheritance | Replaced by flat `@IdClass`. |
| `WbRollbackException(util, WbMessageCode.WBW1164)` etc. | Replaced by `BusinessException("WBE_…")`; behaviour matches via the global handler. |
| `WABOOKSOSMCBaseData.getLocalLang()` | Replaced by `TenantContext.currentLangCd()` + the four-locale loop inside `AuditLogService`. |
| `data-jex-ml="…"` / `.ML.get(...)` on the frontend | Replaced by `settings.approval_line.*` i18n keys. |
| `_v2common.app.openLayerPopup(...)` flow | Replaced by Next.js component-state-driven dialogs (`setDialog({ mode: "add" })`). |
| `szon_0012_003_r002.act?USER_ID=…` user-image URL | Replaced by slug-based REST URI `/api/v1/users/{id}/image` (endpoint itself is a follow-up MR — feature § 4.5 + OQ-1). |
| Legacy SQL `json_agg(json_build_object(…))` in `IDO.APPR_LINE_INFM_DETL_R002.xml` | JPA handles the nested-record shape natively — no need to round-trip through Postgres JSON for this size of result set. |
| `SortHelper.getSortDynamic(util, "comm_0222_001_r001")` | Replaced by the refactor's common-sort whitelist contract — `ApprovalLineSortField` enum (feature § 4.2). |
| `comm_0222_001_r001`'s commented-out pagination block ([file:53-62](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp)) | Refactor follows the active "load all" branch — pagination is out of scope (feature § 4.3). |
