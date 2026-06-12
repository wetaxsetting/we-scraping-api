# Approval Line — Source Code Index

Legacy ↔ refactor file map. JEX paths are **read-only reference**. Frontend mirror:
[wecms-web/docs/features/settings/approval-line/component-index.md](../../../../../wecms-web/docs/features/settings/approval-line/component-index.md).

---

## Refactor — backend (`wecms-api`)

Package `com.kosign.wecms.domain.setting.approvalline`.

| Layer | File |
|---|---|
| Controller | [controller/ApprovalLineController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/controller/ApprovalLineController.java) |
| Service | [service/ApprovalLineService.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/service/ApprovalLineService.java) |
| Repository (master) | [repository/ApprovalLineRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/repository/ApprovalLineRepository.java) |
| Repository (approvers) | [repository/ApprovalLineApproverRepository.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/repository/ApprovalLineApproverRepository.java) |
| Entity (master) | [entity/ApprovalLine.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/entity/ApprovalLine.java) |
| Composite PK (master) | [entity/ApprovalLineId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/entity/ApprovalLineId.java) |
| Entity (approver) | [entity/ApprovalLineApprover.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/entity/ApprovalLineApprover.java) |
| Composite PK (approver) | [entity/ApprovalLineApproverId.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/entity/ApprovalLineApproverId.java) |
| DTOs | dto/ApprovalLineListResponse · ApprovalLineRow · ApprovalLineApproverRow · ApprovalLineCreateRequest · ApprovalLineUpdateRequest · ApprovalLineApproverInput · ApprovalLineSortField — all under [dto/](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/dto/) |
| Constants | [ApprovalLineConstants.java](../../../../src/main/java/com/kosign/wecms/domain/setting/approvalline/ApprovalLineConstants.java) (`SLUG = "approval-line"`, `MAX_APPROVERS = 5`) |
| Key-gen funnel | [LegacyFunctionRepository.nextApprLineNo(...)](../../../../src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java) |
| Tenant-link guard repo | [UserBusiConnInfmRepository.findAllByUserIdInAndBizKeyAndDelYn(...)](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserBusiConnInfmRepository.java) |
| Sort primitives (shared) | [common/web/SortDirection.java](../../../../src/main/java/com/kosign/wecms/common/web/SortDirection.java) · [common/web/SortableField.java](../../../../src/main/java/com/kosign/wecms/common/web/SortableField.java) |
| Flyway | [V90](../../../../src/main/resources/db/migration/V90__approval_line_schema.sql) (schema) · [V91](../../../../src/main/resources/db/migration/V91__wb_get_next_val_appr_line_infm.sql) (`wb_get_next_val` branch) · [V92](../../../../src/main/resources/db/migration/V92__approval_line_numeric_to_int_types.sql) (NUMERIC→BIGINT/INTEGER hotfix) |
| Error messages | `WBE_APPROVAL_LINE_*` in [messages.properties](../../../../src/main/resources/messages.properties) + [messages_en.properties](../../../../src/main/resources/messages_en.properties) |
| Tests | [ApprovalLineServiceTest](../../../../src/test/java/com/kosign/wecms/domain/setting/approvalline/service/ApprovalLineServiceTest.java) (13 cases — see [test-scenarios.md](test-scenarios.md)) |

Validation is Bean Validation on the record DTOs + service-layer guards (`validateApproverList`,
`verifyTenantLinks`, `requireApprLineNm`) — no standalone validator class.

> **Class-naming note.** Entity classes are `ApprovalLine` / `ApprovalLineApprover` (slug-derived),
> not the legacy `ApprLineInfm` / `ApprLineInfmDetl` names — only `@Table` / `@Column` carry the
> legacy `UPPER_SNAKE` identifiers.

---

## Refactor — frontend (`wecms-web`)

Folder `components/settings/approval-line/`.

| Layer | File |
|---|---|
| Panel | [components/settings/approval-line/ApprovalLinePanel.tsx](../../../../../wecms-web/components/settings/approval-line/ApprovalLinePanel.tsx) |
| Table | [components/settings/approval-line/ApprovalLineTable.tsx](../../../../../wecms-web/components/settings/approval-line/ApprovalLineTable.tsx) |
| Add/Edit dialog (drag-and-drop) | [components/settings/approval-line/ApprovalLineDialog.tsx](../../../../../wecms-web/components/settings/approval-line/ApprovalLineDialog.tsx) |
| Add-approver picker | [components/settings/approval-line/AddApproverPopover.tsx](../../../../../wecms-web/components/settings/approval-line/AddApproverPopover.tsx) |
| Approver hover card | [components/settings/approval-line/ApproverListPopover.tsx](../../../../../wecms-web/components/settings/approval-line/ApproverListPopover.tsx) |
| Hooks (list + mutations) | [components/settings/approval-line/useApprovalLine.ts](../../../../../wecms-web/components/settings/approval-line/useApprovalLine.ts) |
| Zod schema | [components/settings/approval-line/schema.ts](../../../../../wecms-web/components/settings/approval-line/schema.ts) |
| Mappers (wire↔in-memory) | [components/settings/approval-line/mappers.ts](../../../../../wecms-web/components/settings/approval-line/mappers.ts) |
| In-memory types | [components/settings/approval-line/types.ts](../../../../../wecms-web/components/settings/approval-line/types.ts) · [approver.ts](../../../../../wecms-web/components/settings/approval-line/approver.ts) (`MAX_APPROVERS = 5`) |
| API client | [lib/api/approvalLine.ts](../../../../../wecms-web/lib/api/approvalLine.ts) (`fetchApprovalLines` · `fetchApprovalLine` · `createApprovalLine` · `updateApprovalLine` · `deleteApprovalLine`) |
| Wire types | [lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) (`ApiApprovalLineRow` · `ApiApprovalLineApproverRow` · `ApiApprovalLineListResponse` · `ApiApprovalLineCreateRequest` · `ApiApprovalLineUpdateRequest` · `ApiApprovalLineApproverInput`) |
| Settings nav entry | [components/settings/SettingsNav.tsx](../../../../../wecms-web/components/settings/SettingsNav.tsx) (`key: "approval_line"`, slug `settings-core-approval-line`) |
| Settings shell mount | [components/settings/SettingsModal.tsx](../../../../../wecms-web/components/settings/SettingsModal.tsx) |

> **No mock data.** The feature folder has no `mockData.ts` / `APPROVER_POOL` — the panel fetches the
> real `/api/v1/settings/approval-line` and the approver picker fetches `/api/v1/users` via
> `fetchTenantUsers`. Confirmed end-to-end wired (no mock fallback).

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path |
|---|---|
| View JSP — list | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_001_view.jsp` |
| View JSP — create popup | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_002_view.jsp` |
| View JSP — edit popup | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0222_003_view.jsp` |
| TypeScript — list | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0222_001.ts` |
| TypeScript — create (jQuery-UI sortable) | `…/comm_0222_002.ts` (`.sortable()` at `:295-359`, `fn_saveAppr` at `:402-419`) |
| TypeScript — edit (jQuery-UI sortable) | `…/comm_0222_003.ts` (`initApproverSortable` at `:489-517`, `fn_updateData` at `:389-452`) |
| List action | `…/web/WEB-INF/action/jex/wabooks/comm/comm_0222_001_r001_act.jsp` (`:36-49` filter, `:52-62` pagination disabled, `:106-140` row shape + `user_img_url`) |
| Create action | `…/comm_0222_002_c001_act.jsp` (`:28-41` validation, `:90` `WBW1164` cap) |
| Read single | `…/comm_0222_003_r001_act.jsp` |
| Update action (diff-sync) | `…/comm_0222_003_u001_act.jsp` (`:104-194` HashSet diff-sync, `:133` `i+1` renumber, `:144-148` posi/dept `""`) |
| Delete action | `…/comm_0222_001_d001_act.jsp` (`:97-100` `WBE0404`, `:102-108` detail-then-master, no in-use guard) |
| IDO — list w/ `json_agg` | `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.APPR_LINE_INFM_DETL_R002.xml` (`:24-56` `json_agg(... ORDER BY APPRVR_DISP_ORD ASC)`) · `…_R002_CNT.xml` |
| WSVC | `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0222_001*.xml` · `…002*.xml` · `…003*.xml` (all `<login>Y` + `NORM_USER_ONLY_YN=Y` + `WTA_USER_ALLOW_YN=N`, `<mntLogYn>Y` framework log; no app audit) |
| Entity / PK — master | `WECMS DEV/WECMS/src/wabooks/entity/ApprLineInfm.java` · `ApprLineInfmPk.java` |
| Entity / PK — detail | `WECMS DEV/WECMS/src/wabooks/entity/ApprLineInfmDetl.java` · `ApprLineInfmDetlPk.java` |
| DAO | `WECMS DEV/WECMS/src/wabooks/dao/ApprLineInfmDAO.java` · `ApprLineInfmDetlDAO.java` |
| Approver picker (shared) | `…/comm_0192_001_r001_act.jsp` (directory user picker — refactor replaces with `/api/v1/users`) |

Full legacy analysis: [docs/jex/features/approval-line/source-code-index.md](../../../../../docs/jex/features/approval-line/source-code-index.md).

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/settings/approval-line/`
- Preserved flat spec: [../approval-line.md](../approval-line.md)
- Business rules / migration: see [README.md § Related & preserved docs](README.md#related--preserved-docs)
- Frontend feature folder: [wecms-web/docs/features/settings/approval-line/](../../../../../wecms-web/docs/features/settings/approval-line/README.md)
- JEX legacy spec set: [docs/jex/features/approval-line/](../../../../../docs/jex/features/approval-line/README.md)
