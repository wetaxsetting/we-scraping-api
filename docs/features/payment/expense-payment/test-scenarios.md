# Expense Payment — Test Scenarios (API)

> **Backend doc.** Source of truth = latest code + `src/test/.../expensepayment/`. Verified 2026-06-23.

## Existing backend tests

| Test | Covers |
|---|---|
| `ExpenseDispatchExecutorTest` | VND→`TB_PAY_TARGET` / non-VND→`TB_PAY_FILE_MST`/`_DATA`, `PROC_STAT='9'`, `BankChnlDiv='T'`, per-out-bank `ANT_DIV_CD`/`TRSC_CCY_CD`, `WBE2089` (no `TB_FIRM_CONF`), `WBE2088` (VCB USD→other-bank) |
| `ExpensePaymentDispatchApprovalGateTest` | dispatch blocked unless `APPR_TYPE_CD="01"` → `WBE2063` |
| `ExpensePaymentResultParityTest` | Result list parity (status filter, doc_no, title) |
| `ExpensePaymentResultFooterTotalsTest` | footer totals across full dispatched set, status filter not applied (F1) |
| `ExpensePaymentResultDownloadAuditTest` | F2 `W` ERP audit on dispatched-batch download |

## Missing backend coverage (dev to backfill)

| Endpoint | Scenario |
|---|---|
| `POST /uploads/parse` | valid workbook → parsed lines; bad cell → per-row validation error |
| `POST /` (create draft) | `SCDUL_PYMN_NO` generation + `MAX+1` race retry; detail numbering; type `"02"` stamped |
| `POST /{id}/request-approval` | multipart attachments; `APPR_SEQ_NO` global funnel; re-submit guard |
| `POST /{id}/approval-action` | approve → `C`; reject → `R`; derived status via `wb_get_appr_stts` |
| `DELETE /{id}/approval` · `PUT /{id}/approval-line` | withdraw / change-line happy + error paths |
| `PATCH /{id}/lines/{lineId}/reporting-account` | late-bind chart-of-accounts |
| `/{id}/lines/{lineId}/match*`, `/{id}/matches` | candidates / match / unmatch / unmatch-all (type `"02"`) |
| `GET /{id}/file` | `WBE2060` (missing), `WBE2069` (no file) |
| `GET /results/{id}` | `countByStatus` buckets (success/failed/in-progress invariant) |

## Validation / authz scenarios

- Date params: non-`YYYYMMDD` or `from > to` → `WBE_VALIDATION`.
- `id` path: numeric or `VO_YYMMDD_NNN`; malformed → 400.
- Authz: Scheduled endpoints require `payment-expense-scheduled`; Result endpoints additionally require `payment-expense-result`.
- Tenant isolation: every query scoped by `BIZ_KEY` + `SCDUL_PYMN_TYPE_CD="02"`.

## Dispatch SIT/UAT (manual — flag-gated)
See [dispatch-sit-uat-deployment-checklist.md](../../../deployment/expense-payment-dispatch-sit-uat-checklist.md) for the end-to-end transmission checklist (EFS/SFTP/00301/202 flags + B1/B2/`TB_FIRM_CONF` provisioning). Default-config DB-row-only validation is listed there.

## Frontend tests
None present for the Expense pages — see [wecms-web/docs/features/payment/expense-payment/test-scenarios.md](../../../../../wecms-web/docs/features/payment/expense-payment/test-scenarios.md).

---

Last verified: 2026-06-23.
