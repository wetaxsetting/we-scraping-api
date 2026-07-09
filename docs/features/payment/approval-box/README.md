# Approval Box — Backend Feature (wecms-api)

**Module:** Payment → Approval Management
**Feature slug:** `approval-box`
**Refactor screen ID / View Setting `WEB_SVC_ID` / audit `WSVC_ID` / export `screenId`:** `approval-box`
**REST base:** `/api/v1/payment/approval-box`
**Java package:** `com.kosign.wecms.domain.payment.approvalbox`
**Status:** ✅ **Completed** — list + tab counts + date/keyword filter + sort + approver chain + approve + reject + bulk-approve + withdraw + change-approval-line + Excel export all implemented; controller unit tests present; legacy parity verified; SIT/UAT verification complete.

> This folder is the consolidated, code-accurate home for the **completed** Approval Box backend feature. It supersedes the *status* claims in the older planning/design docs (which were written when only the read path was in scope — the mutation flows that were "deferred" at that time have since shipped). Those design-history docs are kept in place and cross-linked below; the naming-bug finding and JEX inventory they carry remain authoritative.

---

## What the feature does

Approval Box is the cross-feature approval **inbox**. Every Payment write flow that needs a multi-step approval (Internal Transfer, Vendor, Expense, Payroll, Overseas, Corporate Card) submits its batch through the shared fan-out writer, which inserts one master row into `EXPT_RESOL_MSTR` and one row per approver into `EXPT_RESOL_APPRVR_INFM`. Approval Box reads those rows back, lets an approver act on the items where it is their turn (approve / reject), and lets the requester withdraw or re-point the approval line while the document is still pending.

The screen surfaces six payment types only (`APPR_DV ∈ {21, 31, 32, 33, 34, 35}`), grouped into four status tabs (Pending / In Progress / Approved / Rejected) plus All.

Backend operations behind the page:

| Operation | Endpoint | Service method | Legacy mirror |
|---|---|---|---|
| List (filter + sort + paginate + tab counts) | `GET /api/v1/payment/approval-box` | `ApprovalBoxService#list` | `resl_0001_001_r001` (IDO `EXPT_RESOL_MSTR_R003` + `_R003_CNT`) |
| Approver chain for one document | `GET .../{apprSeqNo}/approvers` | `#approvers` | `EXPT_RESOL_APPRVR_INFM` REC3 query |
| Approve | `POST .../{apprSeqNo}/approve` | `#approve` | `comm_0195_001` approve action |
| Reject | `POST .../{apprSeqNo}/reject` | `#reject` | `comm_0195_001` reject action |
| Bulk approve | `POST .../bulk-approve` | `#bulkApprove` | refactor addition |
| Withdraw (delete pending/in-progress) | `DELETE .../{apprSeqNo}/withdraw` | `#withdraw` | `resl_0001_001_d001` |
| Change approval line | `PUT .../{apprSeqNo}/approval-line` | `#changeApprovalLine` | refactor addition (rebuild chain) |
| Export (XLSX) | `POST .../export` | `ExcelExportService` + `ApprovalBoxExportProvider` | client-side XLSX in `resl_0001_001_osmc.ts` |

The shared write-side fan-out (`ApprovalBoxFanoutService`) is owned by this package and called by the sibling Payment modules at submission time; it is documented here because the master/approver rows it produces are the data Approval Box reads.

---

## Documents in this folder

| Doc | Focus |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: request (fan-out) → search → detail → approve → reject → status update → persistence → UI refresh. Sequence of status writes, source write-back, notification + audit writes, tenant scope. |
| [functional-specification.md](functional-specification.md) | Business rules: scope (6 payment types), status model (master `APPR_TYPE_CD` + approver `APPRVR_STTS`), date/keyword/status filtering, tab counts, sort whitelist, validation rules, error codes, page-size caps. |
| [database-mapping.md](database-mapping.md) | `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM` columns, PK/index, cross-domain summary tables read for currency/file-name/write-back, Flyway migrations, status/code domain values. |
| [interface-specification.md](interface-specification.md) | REST contracts — request/response wire shapes (snake_case), query params, validation, error envelopes, examples for all 8 endpoints. |
| [source-code-index.md](source-code-index.md) | Every backend source file with a one-line role + key references. |
| [migration-guide.md](migration-guide.md) | JEX `resl` / `comm_0195_001` WSVC/IDO/JSP/SQL → Spring controller/service/repository/DTO mapping; field-by-field; deliberate divergences; migration status. |
| [test-scenarios.md](test-scenarios.md) | Existing controller unit tests + manual / integration / SIT-UAT scenarios. |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Completed-coverage-vs-JEX matrix (Search · Detail · Approval · Rejection · Status · Persistence · Security · UI · Error · Parity) with MATCH / PARTIAL / MISSING verdicts and the final status statement. |

---

## Legacy & Migration Traceability

**JEX counterpart (read-only reference):** module `resl` — list `resl_0001_001_r001`, delete `resl_0001_001_d001`, detail/approve/reject popup `comm_0195_001` (menu id `resl_0001_001`)

---

## Related docs (kept in place — design history & cross-references)

**Design-history docs in this folder (NOT deleted — superseded for *status* only):**
- Discovery (JEX inventory + scope + the `resl_0001_001` naming-bug finding): [discovery.md](discovery.md)
- Backend design (DTO/entity/JPQL draft): [design-backend.md](design-backend.md)
- Frontend design draft: [design-frontend.md](design-frontend.md)
- API flow (Mermaid sequence diagrams): [api-flow.md](api-flow.md)
- Database impact (schema-parity draft): [database-impact.md](database-impact.md)
- Phased migration plan + risk register: [migration-plan.md](migration-plan.md)
- Post-Phase-1 parity fixes (current source of truth for behaviour deltas): [parity-fixes-2026-06.md](parity-fixes-2026-06.md)

**Preserved category docs cross-linked (NOT moved):**
- Payment hub README: [../README.md](../README.md)
- DB ERD (payment): [../../../references/database/erd/payment-erd.md](../../../references/database/erd/payment-erd.md)
- Table ownership: [../../../references/database/table-ownership.md](../../../references/database/table-ownership.md)
- Screen / page mapping: [../../../references/migration/screen-mapping/page-mapping.md](../../../references/migration/screen-mapping/page-mapping.md)
- Migration playbook: [../../../references/migration/MIGRATION_PLAYBOOK.md](../../../references/migration/MIGRATION_PLAYBOOK.md)
- Open-questions follow-up: ../../../planning/open-questions/documentation-follow-up.md _(removed — historical `planning/` folder deleted by project decision)_

**Frontend counterpart:** [../../../../../wecms-web/docs/features/payment/approval-box/README.md](../../../../../wecms-web/docs/features/payment/approval-box/README.md)

**JEX functional source of truth (read-only):**
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp`
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_d001_act.jsp`
- `WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.EXPT_RESOL_MSTR_R003.xml`, `IDO.EXPT_RESOL_MSTR_R003_CNT.xml`
- `WECMS DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp`, `resl_0001_001_osmc.ts`
- `WECMS DEV/WECMS/src/wabooks/dao/ExptResolMstrDAO.java`, `ExptResolApprvrInfmDAO.java`

> **JEX naming note.** The screen is JEX module `resl` (canonical id `resl_0001_001`). The row in `docs/jex/screen-flows/payments.md` that labels it `pymn_0001_001` is a known typo — `pymn_0001_001` is actually Scheduled Payment (Vendor). The refactor uses the slug `approval-box` on every surface (REST URI, package, audit `WSVC_ID`, View Setting `WEB_SVC_ID`, export `screenId`). Full evidence in [discovery.md §1](discovery.md).
