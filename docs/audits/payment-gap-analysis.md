# Payment Modules — Gap Analysis

> Consolidated remaining gaps after the 2026-06-23 documentation re-audit. Each gap is classified with the project vocabulary: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**, plus a gap *kind* (code-exists-doc-missing / doc-exists-feature-removed / backend-missing / frontend-missing / test-missing / PO-decision-required / uncertain-needs-confirmation).
> Companion files: [payment-docs-audit.md](payment-docs-audit.md) · [payment-source-index.md](payment-source-index.md).

## Consolidated gap table

| ID | Module | Gap | Class | Kind | Owner |
|---|---|---|---|---|---|
| G-APBX-1 | Approval Box | V156 sets `SORT_YN='Y'` on `CURRENCY`/`FILE_NM`/`APPRER_NM` but no `ApprovalBoxSortField` constant → inert sort glyphs | PARTIAL | PO-decision-required (seed change) | PO / dev |
| G-APBX-2 | Approval Box | No `ApprovalBoxServiceTest` for core approve/reject transitions | MISSING | test-missing | dev |
| G-APBX-3 | Approval Box | Bulk-approve UI built but not mounted; 5 dead FE components; missing `approval.action.*` locale keys; duplicate `approval.col.file_name` | MATCH (documented) | frontend-cleanup | dev |
| G-APBX-4 | Approval Box | 6 design-history docs carry pre-implementation "deferred Phase 2-4" status (disclaimed by README + parity-fixes) | PARTIAL | doc-history (left intact) | — |
| G-APBX-5 | Approval Box | V140 code comment "no ExptResolDetl entity" but `ExptResolDetl.java` exists | — | uncertain-needs-confirmation (code comment) | dev |
| G-INTR-1 | Internal Transfer | Re-run (legacy `pymn_0010_002.ts`) — no endpoint/service/client either side | DEAD LEGACY | doc-exists-feature-removed (now documented) | PO (closed) |
| G-INTR-2 | Internal Transfer | Dispatch writes real `TB_PAY_*` but no local bank daemon flips `PROC_STAT`; Result counts degrade to empty when dispatch tables unreachable | PARTIAL | environmental (not a code gap) | infra |
| G-INTR-3 | Internal Transfer | `updateReportingAccount` defers `CHART_ACCT_INFM` existence re-check; dispatch `APPR_TYPE_CD=='01'` read deferred to shared `isApproved` service | APPROVED DEVIATION | backend-followup | dev |
| G-INTR-4 | Internal Transfer | No dedicated `InternalTransferDispatchServiceTest` (covered via executor + controller tests) | PARTIAL | test-missing | dev |
| G-INTR-5 | Internal Transfer | FE README cites cutoff error codes `WBE1168/1167/1162` as examples — not re-validated against `messages.properties` | — | uncertain-needs-confirmation | dev |
| G-VEND-1 | Vendor Payment | No tests for `appr_type` server filter, `createDraft` key-race retry, Banking Match endpoints, reporting-account PATCH; no FE component tests | MISSING | test-missing | dev |
| G-VEND-2 | Vendor Payment | APPROVER (`APPRVR_NM`) sort renders glyph but backend falls back to default ORDER BY (EXPT_RESOL_APPRVR_INFM join blocker) | PARTIAL | backend-followup (documented) | dev |
| G-EXPN-1 | Expense Payment | `lib/api/expensePayment.ts` Javadoc + `ExpensePaymentResultService` class comment carry copy-pasted "Vendor / `'01'`" artifacts — code correctly uses `'02'` | DEAD LEGACY | code-comment-cleanup | dev |
| G-EXPN-2 | Expense Payment | No backend tests for Scheduled create/parse/request-approval/approval-action/reporting-account/banking-match; no FE tests | MISSING | test-missing | dev |
| G-EXPN-3 | Expense Payment | Companion docs (migration mapping, schema, parity checklist) not yet created | PARTIAL | code-exists-doc-missing | doc |
| G-PAYR-1 | Payroll | Backend `…/payroll/README.md` still says "5-column Payroll layout" + "STUB / backfill pending" though code is fully implemented (18 endpoints, banking match, export); FE docs already corrected | PARTIAL | doc-exists-stale | wecms-api-docs owner |
| G-PAYR-2 | Payroll | `PayrollPaymentResultExportProvider` exports `APPR_FUND_TP` blank until backend ships `EXPT_RESOL_MSTR` join | PARTIAL | backend-followup | dev |
| G-PAYR-3 | Payroll | No FE tests for payroll screens | MISSING | test-missing | dev |
| G-OVRS-1 | Overseas Payment | Legacy M-mode (`OVRS_PYMN_MSTR`) full per-line detail rendering — `legacy_mmode` flags, empty rows, no `WBE2045` | PARTIAL | PO-decision-required (blocked on UAT/PROD M-mode counts; MCP reaches DEV only) | PO + DBA |
| G-OVRS-2 | Exchange Rate | B-4 OpenAPI/Springdoc annotations absent (intentional — Springdoc not wired) | APPROVED DEVIATION | backend-followup | dev |
| G-OVRS-3 | Exchange Rate | B-2 timezone (`resolveDate` UTC+7), F-1/F-2/F-3 (mapper/formatRate location), F-5 (error dialog) marked ⚠️ re-verify | — | uncertain-needs-confirmation | dev |
| G-OVRS-4 | Overseas Payment | No frontend tests for overseas / exchange-rate screens | MISSING | test-missing | dev |
| G-CARD-1 | Card Transactions | `approvers[]` empty on ledger rows (chain only in Approval Details popup) | PARTIAL | intentional (documented) | — |
| G-CARD-2 | Card Transactions | Resolved display names not on wire; FE resolves from chart-tree + purpose-options | APPROVED DEVIATION | intentional (documented) | — |
| G-CARD-3 | Card Transactions | `MEMO_INFM` not in PROD active table list yet supports full memo CRUD (Java MAX+1 key-gen exception) | APPROVED DEVIATION | PO-decision-required (confirm baseline scope) | PO |
| G-CARD-4 | Card Transactions | OQ-3 `PRCH_STTS` value set still only `'1'` + null | — | PO-decision-required (carried) | PO |
| G-CARD-5 | Card Transactions | No frontend tests (backend has 44 test methods) | MISSING | test-missing | dev |
| CC-1 | Notification | `payment/notification/controller/NotificationController` read-side (list/unread-count/mark-read) is a stub; write-side `ApprovalNotificationService` (BLTB) is live; removed-module memory refers to a never-built refactor-only `NOTIFICATION` table | PARTIAL | code-exists-doc-thin + memory-clarification | PO / dev |
| CC-2 | Dispatch callbacks | `Bulk{Execution,Reception}ResultController` fully implemented + documented in batch audits; only ops TODOs (B7 IP-allowlist, B5 SIT byte-parity) | MATCH | operational (indexed in README §2.2a) | infra |
| CC-3 | Validation | `BlockedCharsRuleController` + `SpecialCharValidator` + V149 39-row seed implemented + documented; OV-1 runtime row count unverified | MATCH | uncertain-needs-confirmation (OV-1) | dev (DB access) |

---

## Cross-cutting findings (detail)

### CC-1 — Notification module (clarifies the "removed" memory)
`wecms-api/src/main/java/com/kosign/wecms/domain/payment/notification/controller/NotificationController.java` (base `/api/v1/notifications`) **exists in code**. The project memory ("NOTIFICATION table + module removed", 2026-06-10) is accurate **only** for a refactor-only `NOTIFICATION` table that was never built. The package backs approval notifications via legacy **BLTB_*** tables (in the active inventory):
- **Write-side** `ApprovalNotificationService` is **live** — writes `BLTB_POST_INFM`/`USER_NEW_POST_INFM`/`NOTI_INFM`/`NOTI_CONT_INFM`/`COMNT_INFM`; consumed by all 6 payment modules incl. Card.
- **Read-side** controller (list / unread-count / mark-read / mark-all-read) is a **stub** (`NotificationService` skeleton). FE client `lib/api/notification.ts`. Tests `NotificationControllerTest`, `NotificationServiceTest` exist.

**Recommendation**: finish the read-side per the existing notification gap audit; clarify the memory note so the BLTB write-side is not conflated with the removed refactor-only table. **Not DEAD LEGACY.**

### CC-2 — Dispatch callback controllers — MATCH
`payment/dispatch/callback/controller/` — `BulkExecutionResultController` (`POST …/dispatch/callbacks/bulk-execution-result`, 01302) and `BulkReceptionResultController` (`/bulk-reception-result`, 01301 + inline 00302) are fully implemented and documented in two batch audit files. Write `TB_FIRM_FILE_RSLT/_DATA/_MST`, `TB_PAY_*`, `TB_FIRM_BAT_API_TRAN`. Only operational TODOs remain (B7 IP-allowlist for external callers; B5 SIT byte-parity). Indexed in payment `README.md` §2.2a.

### CC-3 — Validation controller — MATCH
`payment/validation/controller/BlockedCharsRuleController.java` (`GET /api/v1/payment/blocked-chars`) — read-only blocked-chars lookup. `FieldCd` (RMKS/RCPT/MEMO/BENA/BEAD), `PymnType` (INTR/VEND/EXPN/PAYR/OVER), `SpecialCharValidator`, `BlockedCharsRule` entity/repo + V149 39-row seed. Documented in the special-char parity audit and per-module Excel-parser sections. Open item OV-1: confirm runtime seed row count at next DB access.

---

## Test coverage snapshot

**Backend tests are strong** across Result parity, footer totals, download audit, status counts, sort fields, dispatch executors, and approval back-stamp (see [payment-source-index.md](payment-source-index.md) per module).

**Consistent MISSING gaps**:
- **Frontend tests** — no `.test.*` files for any payment screen (all modules). Largest single coverage gap.
- **Backend Scheduled-side** create/parse/request-approval/approval-action/reporting-account/banking-match tests for Vendor and Expense.
- **Approval Box** core approve/reject service-level transition test.

---

## Priority follow-ups

1. **G-PAYR-1** — reconcile the stale Payroll backend README (doc-only, quick).
2. **CC-1** — finish notification read-side + clarify the memory note (PO + dev).
3. **G-OVRS-1** — Overseas M-mode rendering decision (blocked on UAT/PROD M-mode counts; PO + DBA).
4. **G-CARD-3 / G-CARD-4** — `MEMO_INFM` baseline scope + `PRCH_STTS` value set (PO).
5. **Frontend test coverage** — establish a payment FE test baseline (dev).
6. **Code-comment cleanup** — G-EXPN-1 vendor/`'01'` artifacts; G-APBX-5 stale V140 comment (code MR, out of doc scope).
