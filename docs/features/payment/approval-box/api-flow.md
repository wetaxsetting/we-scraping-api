# Approval Box — API Flow (Sequence Diagrams)

**Feature slug:** `approval-box`
**Module:** `payment`
**Date:** 2026-05-13
**Status:** Design — diagrams for the read-path land first; mutation flows are documented for completeness but ship in follow-up MRs (see [migration-plan.md](migration-plan.md)).

Diagrams use Mermaid. Each step is mapped to the JEX equivalent (file:line) so reviewers can trace parity. Legacy WSVC ids appear only as JEX citations — refactor surfaces use the `approval-box` slug per [discovery.md](discovery.md) §9.

---

## 1 · List (in-scope this MR)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as Next.js Page<br/>(ApprovalBoxPage.tsx)
    participant Map as Mapper<br/>(lib/api/mappers.ts)
    participant API as Spring REST<br/>ApprovalBoxController
    participant Svc as ApprovalBoxService
    participant Repo as ApprovalBoxRepository
    participant DB as PostgreSQL<br/>(EXPT_RESOL_MSTR + joins)

    User->>FE: open /payment/approval-box<br/>or change tab/filter/page
    FE->>FE: build query<br/>{from_dt, to_dt, srch_wd, appr_type_cd, page_no, page_per_cnt, sort_field, sort_direction}
    FE->>API: GET /api/v1/payment/approval-box?…
    Note over API: JwtAuthenticationFilter populates TenantContext<br/>(bizKey, userId, authGrCd, langCd)
    API->>Svc: list(req)
    Svc->>Svc: validatePeriod(req)<br/>↔ resl_0001_001_r001_act.jsp:54-61
    Svc->>Repo: findApprovalBoxList(bizKey, userId, isAdmin, …)
    Repo->>DB: SELECT … FROM EXPT_RESOL_MSTR ERM<br/>LEFT JOIN BLTB_POST_INFM / OVRS_PYMN_MSTR /<br/>SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR<br/>WHERE BIZ_KEY = :bizKey AND DEL_YN='N'<br/>AND APPR_DV IN ('21','31','32','33','34','35')<br/>AND draft_date BETWEEN :fromDt AND :toDt<br/>AND visibility-filter (if not admin)<br/>↔ resl_0001_001_r001_act.jsp:63-175
    DB-->>Repo: Page<ApprovalBoxProjection>
    Repo-->>Svc: page rows
    loop For each row
        Svc->>Repo: findApproversByDocument(bizKey, apprSeqNo)
        Repo->>DB: SELECT * FROM EXPT_RESOL_APPRVR_INFM<br/>WHERE BIZ_KEY=? AND APPR_SEQ_NO=?<br/>ORDER BY APPRVR_LINE_NO ASC<br/>↔ resl_0001_001_r001_act.jsp:325-327
        DB-->>Repo: approver list
        Repo-->>Svc: approvers
        Svc->>Svc: deriveApprStts(row)<br/>(replaces wb_get_appr_stts)
    end
    Svc->>Repo: fetchTabCounts(...) + fetchCurrencyTotals(...)
    Repo->>DB: aggregated counts<br/>↔ IDO.EXPT_RESOL_MSTR_R003_CNT.xml
    DB-->>Repo: scalars
    Repo-->>Svc: counts + totals
    Svc-->>API: ApprovalBoxListResponse
    API-->>FE: 200 { status: { error:false, code:"WBI0000" }, data: { total_count, tab_counts, currency_totals, rows[] } }
    FE->>Map: mapApprovalBoxList(res.data)
    Map-->>FE: camelCase domain shape
    FE->>FE: setState — bind table, tabs, totals, pagination
    FE-->>User: rendered table + tab counts + currency footer
```

**Parity checklist:**
- Validation: [resl_0001_001_r001_act.jsp:54-61](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L54) → service `validatePeriod`.
- Tenant BIZ_KEY filter: [resl_0001_001_r001_act.jsp:68-69](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L68) → JPQL `WHERE erm.bizKey = :bizKey`.
- 6-type filter (`21`/`31`/`32`/`33`/`34`/`35`): [resl_0001_001_r001_act.jsp:78](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L78) → JPQL `IN`.
- Visibility filter (Redmine #42046): [resl_0001_001_r001_act.jsp:114-162](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L114) → JPQL nested `EXISTS`.
- Approver chain expansion: [resl_0001_001_r001_act.jsp:325-338](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L325) → repository `findApproversByDocument`.
- Tab counts ignore `apprTypeCd`; rows respect it: preserved exactly (D9 in [design-backend.md](design-backend.md) §13).

---

## 2 · View detail (deferred — documented for context)

The detail popup is owned by `comm_0195_001` in JEX; the refactor port lands in a follow-up MR. Sketch:

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as ApprovalBoxPage / ApprovalDetailsModal
    participant API as ApprovalBoxController
    participant Svc as ApprovalBoxService
    participant DB as PostgreSQL

    User->>FE: click row<br/>↔ resl_0001_001_osmc.ts:181-196
    FE->>API: GET /api/v1/payment/approval-box/{apprSeqNo}
    API->>Svc: getDetail(apprSeqNo)
    Note over Svc: Tenant scope from TenantContext.<br/>Visibility re-checked: 404 if user is<br/>not admin and not owner/approver/uploader.
    Svc->>DB: SELECT master + files + approvers + transactions<br/>↔ comm_0195_001 family
    DB-->>Svc: master, REC2-equivalent files, REC3-equivalent approvers,<br/>per-type transaction list
    Svc-->>API: ApprovalBoxDetailResponse
    API-->>FE: 200 { status, data: { header, type-specific transaction table, approvers, files } }
    FE-->>User: modal: title + status badge, fields, transactions, [Reject][Approve][Close]
```

**Notes on transaction-table shape (deferred):** the rows-by-type panel — Internal Transfer / Vendor / Expense / Payroll / Overseas / Corporate Card — pulls from `SCDUL_PYMN_SUMR_DETL`, `OVRS_PYMN_SUMR_DETL`, or `CARD_TRNS_HSTR` per `appr_dv`. The detail-MR design doc will spell this out; for now [docs/jex/business-rules/approval.md:175-227](../../../../../docs/jex/business-rules/approval.md#L175) is the source of truth.

---

## 3 · Approve (deferred)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as ApprovalDetailsModal
    participant API as ApprovalBoxController
    participant Svc as ApprovalBoxService
    participant AuditSvc as AuditLogService
    participant DB as PostgreSQL

    User->>FE: click [Approve] (footer; only enabled when it is this user's turn)
    FE->>API: POST /api/v1/payment/approval-box/{apprSeqNo}/approve
    Note over API: Body intentionally empty —<br/>identity comes from JWT (TenantContext.currentUserId).
    API->>Svc: approve(apprSeqNo)
    Svc->>DB: 1) SELECT EXPT_RESOL_APPRVR_INFM<br/>WHERE BIZ_KEY=? AND APPR_SEQ_NO=?<br/>AND APPRVR_ID = currentUserId<br/>FOR UPDATE
    DB-->>Svc: approver row
    alt Not this user's turn (apprvr_stts != 'P' or earlier step still pending)
        Svc-->>API: BusinessException("WBE_APPROVAL_BOX_NOT_YOUR_TURN")
        API-->>FE: 200 { status: { error:true, code:"WBE_APPROVAL_BOX_NOT_YOUR_TURN" } }
        FE-->>User: warning dialog
    else This user's turn
        Svc->>DB: 2) UPDATE EXPT_RESOL_APPRVR_INFM<br/>SET APPRVR_STTS='A', APPR_DTM=now()<br/>WHERE composite-PK
        DB-->>Svc: 1 row affected
        Svc->>DB: 3) Are there more pending steps?
        DB-->>Svc: count
        alt All steps approved
            Svc->>DB: 4) UPDATE EXPT_RESOL_MSTR<br/>SET APPR_TYPE_CD='01'  /* Approved tab */<br/>WHERE BIZ_KEY=? AND APPR_SEQ_NO=?
        else More steps remain
            Svc->>DB: 4) UPDATE EXPT_RESOL_MSTR<br/>SET APPR_TYPE_CD='03'  /* In Progress */
        end
        Svc->>AuditSvc: append(WSVC_ID="approval-box", op="approve",<br/>actor=currentUserId, target=apprSeqNo)
        AuditSvc->>DB: INSERT INTO AUDIT_LOGS_INFM …
        Svc-->>API: Approval result
        API-->>FE: 200 { status: { error:false }, data: { appr_stts, apprvr_appr_tot_cnt, apprvr_tot_cnt } }
        FE->>FE: refresh row + tab counts (refetch list)
        FE-->>User: success dialog + updated badge
    end
```

**Maps to JEX:** `comm_0195_001_uxxx` approve handler (exact `_act.jsp` to be confirmed in the detail-MR discovery — typically `comm_0195_001_u001_act.jsp`). Status-derivation rules per [docs/jex/business-rules/approval.md:41-54](../../../../../docs/jex/business-rules/approval.md#L41) and [docs/jex/business-rules/approval.md:165-173](../../../../../docs/jex/business-rules/approval.md#L165).

---

## 4 · Reject (deferred)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as ApprovalDetailsModal
    participant API as ApprovalBoxController
    participant Svc as ApprovalBoxService
    participant AuditSvc as AuditLogService
    participant DB as PostgreSQL

    User->>FE: click [Reject]<br/>(optional reason text)
    FE->>API: POST /api/v1/payment/approval-box/{apprSeqNo}/reject<br/>{ reason?: string }
    API->>Svc: reject(apprSeqNo, reason)
    Svc->>DB: same FOR UPDATE check as Approve
    DB-->>Svc: approver row
    alt Not this user's turn
        Svc-->>API: BusinessException("WBE_APPROVAL_BOX_NOT_YOUR_TURN")
    else This user's turn
        Svc->>DB: 1) UPDATE EXPT_RESOL_APPRVR_INFM<br/>SET APPRVR_STTS='R', APPR_DTM=now()<br/>WHERE composite-PK
        Svc->>DB: 2) UPDATE later steps<br/>SET APPRVR_STTS='S' (blocked-by-reject)<br/>WHERE BIZ_KEY=? AND APPR_SEQ_NO=?<br/>AND APPRVR_LINE_NO > current
        Svc->>DB: 3) UPDATE EXPT_RESOL_MSTR<br/>SET APPR_TYPE_CD='02'  /* Rejected */
        Svc->>AuditSvc: append(WSVC_ID="approval-box", op="reject",<br/>actor=userId, target=apprSeqNo, payload={reason})
        AuditSvc->>DB: INSERT INTO AUDIT_LOGS_INFM …
        Svc-->>API: Reject result
        API-->>FE: 200 { status: { error:false }, data: { appr_stts:"R" } }
        FE-->>User: success dialog
    end
```

Rejection at any step cancels the entire workflow per [docs/jex/business-rules/approval.md:173](../../../../../docs/jex/business-rules/approval.md#L173). The `S` status on downstream approvers signals "blocked-by-reject" — same labelling as legacy ([resl_0001_001_osmc.ts:582-585](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L582)).

---

## 5 · Bulk approve (deferred — pending OQ-2)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as ApprovalBoxPage
    participant API as ApprovalBoxController
    participant Svc as ApprovalBoxService
    participant DB as PostgreSQL

    User->>FE: tick checkbox column → select N rows → click [Bulk Approve]
    FE->>API: POST /api/v1/payment/approval-box/bulk-approve<br/>{ appr_seq_nos: ["1540", "1539", ...] }
    API->>Svc: bulkApprove(apprSeqNos)
    loop For each apprSeqNo (single transaction with savepoints per row)
        Svc->>DB: same check + update as §3 Approve
        alt Per-row failure
            Svc->>Svc: collect into failures[]
        else success
            Svc->>Svc: collect into successes[]
        end
    end
    Svc-->>API: BulkApproveResult { successes:[…], failures:[{apprSeqNo, code}] }
    API-->>FE: 200 { status: { error:false }, data: { successes:[…], failures:[…] } }
    FE-->>User: success dialog with per-row breakdown
```

**Notes:**
- Legacy WeCMS does not implement bulk-approve today (not in OSMC view). Final scope depends on OQ-2 in [discovery.md](discovery.md).
- Transactional behaviour: per-row savepoint, NOT all-or-nothing. The legacy precedent (for bulk delete) at [resl_0001_001_d001_act.jsp:179-237](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_d001_act.jsp#L179) uses one big transaction with rollback-on-error, but approve semantically needs per-row partial success so users see exactly which rows failed (e.g. "not your turn" cases).

---

## 6 · Download (Excel export — in-scope this MR)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant FE as ApprovalBoxPage (Excel icon)
    participant API as ApprovalBoxController
    participant ExSvc as ExcelExportService<br/>(common/export)
    participant Provider as ApprovalBoxExportProvider<br/>(implements ExportRowProvider)
    participant Svc as ApprovalBoxService
    participant DB as PostgreSQL
    participant Writer as ExcelWorkbookWriter<br/>(Apache POI SXSSF)

    User->>FE: click Excel icon
    FE->>FE: build ExcelExportRequest body<br/>(screen_id="approval-box", current filter, sort)
    FE->>API: POST /api/v1/payment/approval-box/export
    API->>ExSvc: export("approval-box", req)
    ExSvc->>Provider: rows(req)
    Provider->>Svc: list(reqWithoutPagination)<br/>(reuse the same query path)
    Svc->>DB: same query as §1 but page_per_cnt=0 (full set)
    DB-->>Svc: all matching rows
    Svc-->>Provider: rows[]
    Provider-->>ExSvc: List<Map<String, Object>> keyed by FIELD_ID
    ExSvc->>Writer: write(workbook, fieldIds, rows, header, subheader)
    Writer-->>ExSvc: byte[] / stream
    ExSvc-->>API: ResponseEntity<Resource>
    API-->>FE: 200 application/vnd.openxmlformats-officedocument.spreadsheetml.sheet (binary)
    FE-->>User: browser-triggered .xlsx download
```

**Notes:**
- The XLSX is the same shape produced by `/api/v1/banking/accounts/export` ([wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Excel export") — one shared pipeline.
- Backend computes a count + total-amount sub-header in the header row, replacing the legacy frontend-side line at [resl_0001_001_osmc.ts:300-306](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L300). Final sub-header format pending OQ-6.
- The frontend never carries an XLSX library — POI on the backend is the single producer ([wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Tech Stack").

---

## 7 · Error envelope (every flow)

Every error response follows the standard `{ status, timestamp, path, trace_id }` shape, e.g.:

```json
{
  "status":    { "error": true, "code": "WBE_APPROVAL_BOX_INVALID_PERIOD",
                 "message": "Start date must be earlier than or equal to end date." },
  "timestamp": "2026-05-13T08:32:11Z",
  "path":      "/api/v1/payment/approval-box",
  "trace_id":  "20260513083211PT06592776769567DEVL"
}
```

The `trace_id` is the MDC value populated by `JwtAuthenticationFilter` / `TraceId` per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Error handling — every error envelope MUST carry". Frontend dialogs surface it verbatim.

---

## 8 · Cross-references

- [discovery.md](discovery.md) §2.4 — JEX `_act.jsp` files behind each step.
- [design-backend.md](design-backend.md) §4-§7 — controller / service / repository contracts.
- [design-frontend.md](design-frontend.md) §4-§6 — Axios client, page state, error dialogs.
- [docs/jex/business-rules/approval.md](../../../../../docs/jex/business-rules/approval.md) — canonical approval workflow.
