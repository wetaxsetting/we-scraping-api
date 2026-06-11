# Approval Box — Test Scenarios (Backend)

Covers existing automated tests plus the manual / integration / SIT-UAT matrix. Test file: `src/test/java/com/kosign/wecms/domain/payment/approvalbox/controller/ApprovalBoxControllerTest.java` (MockMvc).

---

## 1. Existing automated tests (`ApprovalBoxControllerTest`)

| Test | Asserts |
|---|---|
| `list_missingFromDate_returnsValidationEnvelope` | Missing required `from_date` → `WBE_VALIDATION` envelope (HTTP 200, `status.error=true`) |
| `bulkApprove_emptyBody_rejectedByBeanValidation` | Empty `appr_seq_nos` → bean-validation rejection (`WBE_VALIDATION` / `WBE2054`) |
| (list/approvers/approve/reject wiring) | Endpoint routing, path-variable binding, response envelope shape |

> Service-layer logic (status transitions, write-back, counts) is exercised through the controller tests + the manual/SIT scenarios below. A dedicated `ApprovalBoxServiceTest` is a recommended follow-up (see [refactor-gap-analysis.md](refactor-gap-analysis.md) Open Questions).

---

## 2. Search & filtering

| # | Scenario | Expected |
|---|---|---|
| S1 | List with valid `from_date`/`to_date` | rows within `DRAFT_DATE` range, default sort `REG_DTM DESC` |
| S2 | `from_date` > `to_date` | `WBE_VALIDATION` "from_date: must be on or before to_date" |
| S3 | Malformed date (`2026-06-08`) | `WBE_VALIDATION` invalid format |
| S4 | Keyword matches title / doc-no / doc-name | only matching rows; escape of `% _` literal works |
| S5 | `appr_type_cd=00` | only Pending docs in `list`; `tab_counts` still show all four totals |
| S6 | Invalid `appr_type_cd=99` | `WBE_VALIDATION` |
| S7 | `size=0` | service substitutes default 15 |
| S8 | `size=500` | clamped to 100 |
| S9 | Sort by `TOT_PAY_AMT DESC` | rows ordered by `totPayKhrAmt` desc, `APPR_SEQ_NO` secondary |
| S10 | Sort by unknown field | falls back to default sort (no error) |
| S11 | Empty result set | `list=[]`, `total_count=0`, `total_pages=0`, counts all 0 |

## 3. Visibility (tenant + row-level)

| # | Scenario | Expected |
|---|---|---|
| V1 | Admin lists | sees every tenant row |
| V2 | Non-admin who drafted a doc | sees own drafted rows |
| V3 | Non-admin who is an assigned approver | sees rows they approve |
| V4 | Non-admin unrelated to a doc | row not visible (and tab counts exclude it) |
| V5 | No JWT | 401 / `WBE1009` |
| V6 | Authenticated but no `payment-approval-box` grant | 403 / `WBE1003` |

## 4. Approve workflow

| # | Scenario | Expected |
|---|---|---|
| A1 | First of 3 approvers approves | caller `P→A`; master `03` In Progress; next `W→P`; source `APPR_STTS='I'`; response `appr_stts="I"`, counts 1/3 |
| A2 | Last approver approves | master `01` Approved; source `APPR_STTS='C'`; response `appr_stts="C"`, counts 3/3 |
| A3 | Approve when not your turn (`W`) | `WBE2052` |
| A4 | Approve a doc you're not on | `WBE2051` |
| A5 | Approve an already-approved/rejected doc | `WBE2053` |
| A6 | Approve unknown / cross-tenant `apprSeqNo` | `WBE2050` |
| A7 | Audit row written | `AUDIT_LOGS_INFM` row, `WSVC_ID="approval-box"`, approve detail key, per locale |
| A8 | Notification fired | decision notification to chain (P/A/R) + admins, minus actor + requester; failure does not roll back |

## 5. Reject workflow

| # | Scenario | Expected |
|---|---|---|
| R1 | Approver rejects | caller `P→R`; all `W→S`; master `02`; source `APPR_STTS='X'`; response `appr_stts="R"` |
| R2 | Reject not your turn / not an approver / finalised | `WBE2052` / `WBE2051` / `WBE2053` |
| R3 | Downstream approvers after reject | cannot act (`S`) — attempting → `WBE2052` |

## 6. Bulk approve

| # | Scenario | Expected |
|---|---|---|
| B1 | 3 valid ids, all approvable | `successes` length 3, `failures` empty |
| B2 | Mixed (1 valid, 1 not-your-turn, 1 unknown) | `successes` 1, `failures` 2 with per-row codes (`WBE2052`, `WBE2050`) |
| B3 | Empty / >100 ids | `WBE2054` |
| B4 | All fail | `successes=[]`; no aggregate audit row |

## 7. Withdraw & change approval line

| # | Scenario | Expected |
|---|---|---|
| W1 | Withdraw a Pending doc | master + chain deleted; source `APPR_SEQ_NO`/`APPR_STTS` nulled (Draft); audit row |
| W2 | Withdraw an Approved/Rejected doc | `WBE_VALIDATION` (only Pending/In-Progress) |
| W3 | Withdraw unknown | `WBE_NOT_FOUND` |
| C1 | Change line on a Pending doc to a valid line | chain rebuilt (line 1 `P`, rest `W`); master `00`; audit |
| C2 | Change line with `appr_line_no ≤ 0` | `WBE_VALIDATION` |
| C3 | Change line to a template with no approvers | `WBE_VALIDATION` |
| C4 | Change line on a finalised doc | `WBE_VALIDATION` |

## 8. Export

| # | Scenario | Expected |
|---|---|---|
| E1 | Export current filter/tab/sort | XLSX with the 10 allow-listed columns in View-Setting order; subheader count + per-currency totals |
| E2 | Export with > 50,000 matches | `WBE2011` |
| E3 | Export with all columns hidden | `WBE2013` |
| E4 | Export unknown screenId | `WBE2010` |
| E5 | `PAY_END_YN` column content | shows approval-status label (Pending/Approved/Rejected/In Progress) |

## 9. Empty / error handling

| # | Scenario | Expected |
|---|---|---|
| EH1 | Blank `apprSeqNo` path on any mutation | `WBE_VALIDATION` |
| EH2 | Null tenant/user context | `WBE9999` |
| EH3 | Notification service throws | approval still commits (isolated tx) |
| EH4 | DB fault | `WBE_DB` / `WBE9999` via global handler |

## 10. Legacy parity (verified)

| # | Scenario | Expected |
|---|---|---|
| P1 | Six payment types only | `appr_dv IN (21,31,32,33,34,35)`; other codes excluded |
| P2 | Tab counts ignore selected tab | matches legacy `_R003_CNT` suppressing `APPR_TYPE_CD` |
| P3 | Date filter on `DRAFT_DATE` | matches legacy `draft_date between` |
| P4 | Approver chain order | `APPRVR_LINE_NO ASC` |
| P5 | Status derivation | `deriveApprStts` matches `wb_get_appr_stts` (`01→C`,`02→R`,`03→I`,else `P`) |
| P6 | Overseas backfill | V213 confirms `APPRVR_STTS` consistent with master `01`/`02` |

## 11. SIT / UAT

SIT/UAT verification is **complete** (per feature status). Cross-link the run records under [../../../testing/](../../../testing/) (`sit/`, `uat/`) when the Approval Box rows are added there; this doc is the scenario source of truth those runs execute against.

**Recommended follow-up:** add a `ApprovalBoxServiceTest` covering A1/A2/R1 transitions + write-back routing (OVRS vs SCDUL) with Mockito, so the status-transition logic has unit coverage independent of MockMvc.
