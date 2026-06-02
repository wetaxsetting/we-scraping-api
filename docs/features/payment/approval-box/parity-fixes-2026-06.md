# Approval Box — Parity Fixes & Decisions (2026-06-02)

**Feature slug:** `approval-box` · **Module:** `payment`
**Status:** Implemented & verified (local R&D). Documentation of changes that landed **after** the original Phase-1 plan.

This note records the confirmed Approval Box parity fixes and the explicit
deferred/out-of-scope items, so the spec reflects the live code. It supersedes
the now-stale "Deferred — Phase 2/3/4" rows in [migration-plan.md](migration-plan.md)
§1.1 and the File-Name deferral (D1) in [design-backend.md](design-backend.md) §13.
Traceability: derived from the Approval Box parity audit (legacy `resl_0001_001`
vs refactor) and approved change sets.

---

## 1 · Confirmed behaviors (keep)

### 1.1 · Excel export — all matching records

- Export returns **every** row matching the current filters (date range, tab,
  keyword, sort) — **not** the current page, **not** page-size-bounded, **not**
  capped at 100.
- The shared **50,000-row safety limit** (`ExcelExportService.MAX_ROWS`) is the
  only ceiling and is accepted.
- Implementation: `ApprovalBoxService.listForExport(...)` (page 0, size
  `MAX_EXPORT_ROWS = 50_000`, bypassing the on-screen `MAX_PAGE_SIZE = 100`) is
  consumed by `ApprovalBoxExportProvider.fetch(...)`. Sorting, column order,
  formatting and the per-currency subheader totals are preserved.
- Legacy parity: matches `resl_0001_001` Excel (runs the list query with
  `PAGE_PER_CNT = 0`).

> Source: `wecms-api-main/.../approvalbox/service/ApprovalBoxService.java`
> (`listForExport`, `MAX_EXPORT_ROWS`), `.../approvalbox/export/ApprovalBoxExportProvider.java`.

### 1.2 · Withdraw / Edit approval line — non-card types

- **Withdraw** and **Edit approval line** are available for all six payment
  types surfaced by the Approval Box:
  Internal Transfer · Vendor Payment · Expense Payment · Payroll ·
  Overseas Payment · Card Transactions.
- **Card** keeps its existing dedicated endpoints
  (`/api/v1/payment/card-transactions/by-approval/{apprSeqNo}` +
  `…/approval-line`); the frontend routes card rows there.
- Every **non-card** type routes through the new generic endpoints on
  `ApprovalBoxController`:
  - `DELETE /api/v1/payment/approval-box/{apprSeqNo}/withdraw`
  - `PUT /api/v1/payment/approval-box/{apprSeqNo}/approval-line`
- Both reuse the shared `ApprovalBoxFanoutService.withdrawApproval` /
  `changeApprovalLine` (approval-table mutations + Pending/In-Progress guard).
  Withdraw additionally clears the source batch stamp
  (`COALESCE` routing: OVERSEAS → `OVRS_PYMN_SUMR`, else → `SCDUL_PYMN_SUMR`),
  returning the batch to Draft. Each write appends one `AUDIT_LOGS_INFM` row
  (`WSVC_ID = "approval-box"`).
- Preserved: approval validation, approval-chain reset to Pending on edit,
  existing business rules, audit logging.

> Source: `ApprovalBoxController` (withdraw / approval-line endpoints),
> `ApprovalBoxService` (`withdraw`, `changeApprovalLine`, `clearSourceApprStamp`),
> `ApprovalBoxFanoutService`; frontend `wecms-web-main/components/payment/approval-box/ApprovalBoxPage.tsx`
> (per-kind dispatch) + `wecms-web-main/lib/api/approval-box.ts`.

### 1.3 · File Name column — `ORG_FILE_NM`

- File Name displays the **uploaded file name** from
  `COALESCE(SCDUL_PYMN_SUMR.ORG_FILE_NM, OVRS_PYMN_SUMR.ORG_FILE_NM)`
  (SCDUL precedence, nulls skipped). **Not** `TITL` (which now holds the typed
  document title in the refactor).
- This matches the `orgFileNm` source already used by the Scheduled Transfer /
  Transfer Result pages.
- Legacy used `COALESCE(SCDUL.TITL, OVRS.TITL)` because legacy stored the file
  name in `TITL`; the refactor moved it to `ORG_FILE_NM`, so the refactor
  sources `ORG_FILE_NM` to display the same value.
- **Display UX:** long names truncate with an ellipsis (Tailwind `truncate` =
  `overflow:hidden; text-overflow:ellipsis; white-space:nowrap`); the full value
  shows on hover via the native `title` attribute — the same pattern used by
  `ErpTransactionHistoryTable` (`title={row.orgFileNm}`). No custom tooltip
  component; row height / column width / layout unchanged.
- If `ORG_FILE_NM` is null (e.g. an approval with no uploaded batch, or an
  overseas batch whose submit path does not stamp it), the cell is blank.

> Source: `ScdulPymnSumrRepository.findOrgFileNmByBizKeyAndApprSeqNos`,
> `OvrsPymnSumrRepository.findOrgFileNmByBizKeyAndApprSeqNos`,
> `ApprovalBoxService.mapProjections`; wire field `file_name` on `ApprovalBoxRow`;
> frontend `wecms-web-main/components/payment/approval-box/ApprovalTable.tsx`
> (`TextSpan title=…`) + `lib/api/mappers.ts`.

### 1.4 · Pagination — default page size 10

- Default page size is **10**, by project decision. This is the accepted
  refactor behavior and intentionally diverges from the legacy default of 15.
- Pagination behavior, page-size options, sorting, and approval-status logic are
  unchanged by the fixes above.

---

## 2 · Explicitly not implemented / deferred

These are documented as out of scope and were **not** implemented:

| Item | Status | Notes |
|---|---|---|
| ERP status synchronization | **Deferred / likely out of scope** | Legacy gates ERP updates on `PrdtType.H` (Haengsung). The refactor is local-only R&D with no bank/ERP integration. Needs a PO scope decision before any work. |
| Approval notification + comment creation | **Deferred** | Legacy writes a `BLTB_COMNT_INFM` REMARK and notifies the next approver on approve/reject. No notification subsystem exists in the refactor yet. |
| Uploader visibility branch | **Deferred** | Legacy non-admin visibility = uploader **OR** requester **OR** approver; the refactor keeps requester + approver only (the uploader `EXISTS` branch over SCDUL/OVRS/CARD is dropped until those sibling features fully port). Strictly fewer rows — no over-exposure. |
| Vietnamese translated keyword search | **Deferred** | Legacy adds `wb_get_translate_text(TITL)` to the keyword `ILIKE` for VN; the refactor matches `DOC_NM` / `TITL` / `DOC_NO` only. Blocked by the no-new-legacy-DB-function policy; needs a PO decision on the translation source. |

---

## 3 · Suggested SIT verification checklist

Run on the running system (the `postgres` MCP reaches JEX DEV only, so the
refactor DB values must be confirmed in SIT). Restart the backend first so the
new queries/endpoints load.

**Excel export**
- [ ] Tenant with > 100 docs in range → export contains the full filtered set (≤ 50k); subheader count + per-currency totals reflect all rows, not the page.
- [ ] Sort a column, then export → workbook order matches.
- [ ] Tab + keyword + date filters applied → export contains exactly the filtered set.

**Withdraw (per type: Internal Transfer / Vendor / Expense / Payroll / Overseas)**
- [ ] Pending / In-Progress approval → Delete → master + approver rows removed; source batch returns to Draft (reappears as draft in the module's Scheduled list).
- [ ] Approved / Rejected approval → Delete → blocked (`WBE_VALIDATION`).
- [ ] Card withdraw still clears `CARD_TRNS_HSTR` (regression).
- [ ] One `AUDIT_LOGS_INFM` row, `wsvc_id = 'approval-box'`, withdraw detail.

**Edit approval line (per non-card type)**
- [ ] Pending / In-Progress → Edit → pick line → Save → approver chain replaced, first approver Pending, master reset to Pending; source `APPR_SEQ_NO` unchanged.
- [ ] Closed approval → Edit → blocked.
- [ ] Audit row written.

**File Name**
- [ ] Internal Transfer / Vendor / Expense / Payroll rows backed by an Excel upload → uploaded file name displays.
- [ ] Overseas → file name displays **if** `ORG_FILE_NM` is set; else blank (see §1.3 caveat — overseas submit path may not stamp it).
- [ ] Long name → ellipsis in the cell; hover shows the full name.
- [ ] Null `ORG_FILE_NM` → blank cell, no tooltip.

**Pagination**
- [ ] Default page size is 10; tab counts ignore the selected tab; sorting works server-side.

---

## 4 · Cross-references

- Hub: [../approval-box.md](../approval-box.md) (API) · `wecms-web-main/docs/features/payment/approval-box.md` (web).
- Original plan (historical): [migration-plan.md](migration-plan.md) — see its 2026-06-02 status callout.
- Backend contract: [design-backend.md](design-backend.md) · Frontend: [design-frontend.md](design-frontend.md).
- JEX reference: `WECMS DEV/.../resl/resl_0001_001_r001_act.jsp`, `IDO.EXPT_RESOL_MSTR_R003.xml`, `resl_0001_001_osmc.ts`.
