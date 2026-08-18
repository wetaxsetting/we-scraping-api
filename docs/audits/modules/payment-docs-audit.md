# Payment Documentation Re-Audit — 2026-06-23


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> Full re-audit of all Payment module documentation against the latest backend (`wecms-api/src`) and frontend (`wecms-web`) code. Code changed after the previous docs pass, so this pass reconciles stale/incomplete docs. **Latest code = source of truth.**
> Companion files: [payment-source-index.md](payment-source-index.md) · [payment-gap-analysis.md](payment-gap-analysis.md).
> Gap vocabulary used throughout: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**.

## Summary

| Module | Sub-module | Doc status (before → after) |
|---|---|---|
| Approval Box | — | Partially Outdated → **Accurate** |
| Internal Transfer | Scheduled Transfer | Outdated → **Accurate** |
| Internal Transfer | Transfer Result | Partially Outdated → **Accurate** |
| Vendor Payment | Scheduled Payment | Outdated (stub) → **Accurate** |
| Vendor Payment | Payment Result | Outdated (stub) → **Accurate** |
| Expense Payment | Scheduled Payment | Outdated (stub) → **Accurate** |
| Expense Payment | Payment Result | Partially Outdated → **Accurate** |
| Payroll | Scheduled Payment | Outdated (stub) → **Accurate** |
| Payroll | Payment Result | Outdated (stub) → **Accurate** |
| Overseas Payment | Scheduled Payment | Partially Outdated → **Accurate** |
| Overseas Payment | Payment Result | Partially Outdated → **Accurate** |
| Overseas Payment | Exchange Rate Inquiry | Outdated → **Accurate** |
| Corporate Card | Card Transactions | Outdated (design-phase) → **Accurate** |
| Cross-cutting | Notification / Dispatch callbacks / Validation | Partially documented → **Indexed** |

**Backend README staleness pattern.** Several `wecms-api/docs/features/payment/*/README.md` files were "Phase A / STUB / backfill pending" stubs written when only list endpoints existed; the code has since advanced to full CRUD + approval + dispatch + banking-match. The Payroll backend README is the one still flagged for reconciliation (see gap-analysis G-PAYR-1).

---

## Approval Box — Accurate (was Partially Outdated)

Core behaviour docs were correct; stale content was concentrated in Flyway versions, the notification collaborator, removed source write-back, test counts, and one FE `footerMode`.

**Files updated**
- `…/approval-box/source-code-index.md` — controller 8→12 endpoints (+4 popup); added `ApprovalDetailService`/`ApprovalAttachmentWriter`/`ApprovalDisplayFlagsResolver`, detail+line entities, `ApprovalTransactionRow`; notification collaborator → `ApprovalNotificationService` (BLTB); Flyway section rewritten; tests 1→4.
- `…/approval-box/database-mapping.md` — §5 Flyway filenames corrected (V55–V60/V140/V148/V156; old V109/V137/V207/V216/V213/V194/V211 do not exist); §7 View Setting seed corrected (V156, 11 cols, no CHECKBOX/ETC).
- `…/approval-box/functional-specification.md` — removed false source `C`/`I`/`X`/`APPR_STTS` write-back; added `PAY_END_*` stamping.
- `…/approval-box/business-flow.md` — DOC_NO/APPR_SEQ_NO via `wb_get_next_val`; notification calls → `approvalNotificationService.notifyApprovalAction` (BLTB).
- `…/approval-box/test-scenarios.md` — expanded to all 4 test files with real method names.
- `…/approval-box/interface-specification.md` — added 4 popup endpoints (detail/transactions/attachments/download).
- `…/approval-box/migration-guide.md` — removed `findMaxDocNoSeqByBizKeyAndPrefix`; D10 fixed.
- `wecms-web/…/approval-box/{ui-specification.md,component-index.md}` — `footerMode` `paginationWithStickyFooter` → `paginationWithTotals`.

**Corrections made (old → new)**
- Flyway: V109/V137/V207/V216/V213/V194/V211 → V55–V60 schema / V140+V148 keygen / V156 VS seed. No demo seed, no overseas backfill migration exist.
- Notification (DEAD LEGACY ref): `domain/notification/...NotificationService` → `domain/payment/notification/service/ApprovalNotificationService.notifyApprovalAction(...)` over BLTB.
- Source write-back (DEAD LEGACY): reject `APPR_STTS='X'` / approve source `C`/`I` → no source write-back; `clearApprSeqNo` nulls `APPR_SEQ_NO` only.
- Keygen: Java MAX+1 finder → `LegacyFunctionRepository.nextDocNo/nextApprSeqNo` → `wb_get_next_val`.
- Endpoints 8 → 12; tests 1 → 4 files; FE `footerMode` fixed.

---

## Internal Transfer — Accurate (was Outdated)

9 of 17 docs updated.

**Files updated**: `…/internal-transfer/{source-code-index,approval-flow,scheduled-transfer-flow,upload-create-flow,transfer-result-flow,refactor-gap-analysis,README}.md` (API) and `wecms-web/…/internal-transfer/{source-code-index,scheduled-transfer-flow,README}.md`.

**Corrections made (old → new)**
- `InternalTransferKeyGenerator`: "`<yyyyMMdd><6-seq>`, no wb_get_next_val" → routes through `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no')` (OD-2 reversal).
- Approve/reject error codes `WBE2094`/`WBE2095` → **`WBE2126`/`WBE2127`**.
- `SCDUL_PYMN_SUMR.APPR_STTS` (stamped/cleared/mirrored) → column removed; status derived via `wb_get_appr_stts`.
- Request-approval "writes DOC_NO back to `ERP_FILE_NUM`" → DOC_NO NOT back-stamped.
- `SCDUL_PYMN_NO` "VARCHAR(20)" → entity `length=10`.
- Result default sort `REG_DTM` → `TRNF_DTM`; result filter `Y`/`N` → route-aware proc-status `C`/`P`.
- Re-run reclassified MATCH → **DEAD LEGACY** everywhere it appeared.

---

## Vendor Payment — Accurate (was Outdated stubs)

Docs were "Phase A read-only / doc stub"; code is full-stack through Phases B–D + Banking Match.

**Files updated**: `wecms-api/…/vendor-payment/README.md`; `wecms-web/…/vendor-payment/{README,scheduled-payment,scheduled-payment-details,payment-result,payment-result-details}.md`.

**Corrections made (old → new)**
- Controller "13 methods" → ~21 (+withdraw-approval, change-line, reporting-account PATCH, 4 Banking Match; request-approval now multipart). `WSVC_ID` L109 → L113.
- API client "exposes only `requestVendorApproval`" → full 16+ function surface already shipped in `lib/api/vendorPayment.ts`.
- View Setting seeds "V141/V142/V150/V160" (do not exist) → consolidated into `V156__sys_scr_option_wecms.sql` (49 vendor rows).
- Detail JEX ids `pymn_0001_001`/`pymn_0002_001` → `pymn_0001_002`/`pymn_0002_002`; detail routes → `/[id]`.
- Result status filter "Incomplete/Processing/Success/Failed" → **Completed `C` / In Progress `P`** (route-aware `trnf_stts`); footer totals computed unfiltered (R008 parity).
- Documented F4b (clickable Approval Ref = `EXPT_RESOL_MSTR.DOC_NO`), F2 dispatched-download 'W' audit (`04`), result export `POST /results/export`.
- Re-run / C3 correctly **absent** (PO-deprecated — confirmed not re-introduced).

---

## Expense Payment — Accurate (was Outdated stub)

Backend README was a 2026-05-22 stub.

**Files updated**: `wecms-api/…/expense-payment/README.md`; `wecms-web/…/expense-payment/{scheduled-payment,scheduled-payment-details,payment-result,payment-result-details}.md`. Dispatch checklist verified **MATCH — no edit needed**.

**Corrections made (old → new)**
- "deferred for Phase D: Excel upload, withdrawal picker, reporting-account PATCH, file download" → all implemented; full 20-endpoint table added.
- API client "exposes only `requestExpenseApproval`" → full 18-function client.
- Keyseq "table name TBD" → reuses `InternalTransferKeyGenerator.nextScdulPymnNo`; `APPR_SEQ_NO` via `wb_get_next_val`.
- Documented dedicated `ExpenseDispatchExecutor`.
- Result filter "Incomplete/Processing/Success/Failed" → Completed `C` / In Progress `P`; F4b + F2 'W' (ERP `05`) documented; status derived via `wb_get_appr_stts`, rejected = `R` not `X`.

---

## Payroll — Accurate (was Outdated stubs)

All 5 WEB docs rewritten.

**Files updated**: `wecms-web/…/payroll/{README,scheduled-payment,scheduled-payment-details,payment-result,payment-result-details}.md`.

**Corrections made (old → new)**
- API base `/api/v1/payment/payroll` → **`/api/v1/payment/payroll-payment`** (was wrong on every doc).
- JEX ids `pymn_0005_001`/`pymn_0006_001` → `pymn_0005_003`/`pymn_0006_002`; detail routes → `/[id]`.
- Placeholder columns → real 11-col Scheduled shell + `APPR_FUND_TP` (Payroll Type); `SCDUL_PYMN_TYPE_CD="00"`.
- Result status filter "(Incomplete/Processing/Success/Failed)" → two-status Completed `C` / In Progress `P`.
- Corrected the "Result detail = 5-column Payroll-specific layout" claim → Result detail uses canonical `resultDetailColumns` projected to an 8-col View-Setting subset (the 5-col subset is **Scheduled** detail).
- Added F4b, Banking Match flow, Excel export `POST …/results/export`, `WBE2060`/`WBE2069` handling.

> The **backend** `wecms-api/docs/features/payment/payroll/README.md` still carries the stale "5-column Payroll layout" claim and a "STUB / backfill pending" label — flagged for reconciliation (gap-analysis G-PAYR-1).

---

## Overseas Payment — Accurate (was Partially Outdated)

**Files updated**: `wecms-api/…/overseas-payment/README.md`, `…/exchange-rate-inquiry.md`; `wecms-web/…/overseas-payment/{README,scheduled-payment,scheduled-payment-details,payment-result,payment-result-details}.md`, `…/exchange-rate-inquiry.md`.

**Corrections made (old → new)**
- Route guess `…/results` → actual `/result` (singular); single controller (not two).
- `OVRS_PYMN_DTLS` → actual entity/table `OVRS_PYMN_SUMR_DETL` (×3).
- Scheduled "create transfer" → full lifecycle (request-approval multipart, change/remove approval-line, approve/reject, dispatch, delete-draft, excel preview/upload, reporting-account, withdrawal-accounts, file download).
- Status: list/result UNION `OVRS_PYMN_SUMR` (E) ∪ `OVRS_PYMN_MSTR` (M); no `appr_stts` column → derived from `EXPT_RESOL_MSTR.APPR_TYPE_CD`.
- Result: status filter → `proc_stat` CSV (Completed `0,3` / In-Progress `6`), derived `trnf_stts` ∈ {C,F,P}; **F4b** documented (`appr_seq_no` on `OverseasResultRow`); two export paths distinguished (list `GET /result/export` vs per-batch `POST /result/{id}/export`); result authz override `payment-overseas-result`.
- Exchange Rate: controller is `domain.banking.ExchangeRateController` at `/api/v1/banking/exchange-rates` (`@Validated` + `@PreAuthorize`); JEX `pymn_0010_002` → read IDO `BANK_EXCH_RATE_INFM_R002`, write batch `EXCH_0001_001`, menu `payment-overseas-exchange`. ~12 previously-open gaps flipped to RESOLVED (validation, `seq_no` pref, backend bank/currency lists, sort, compare-bank fetch, date wiring).

---

## Corporate Card — Card Transactions — Accurate (was design-phase)

Docs described a "design-phase / list-only Sprint-2" feature; code is full-stack (~30 endpoints, 5 services, 44 tests; 16 components + 28-function client).

**Files updated**: `wecms-api/…/card-transactions.md`; `wecms-web/…/card-transactions.md`; both payment `README.md` indexes.

**Corrections made (old → new)**
- Status "Design phase — docs only" → Implemented full-stack.
- Route `/payment/card-transactions` → `/payment/corporate-card/card-transactions`; components `components/payment/card-transactions/` → `components/payment/corporate-card/`.
- Authz "no `@PreAuthorize`" → live `@menuAuthz.canAccess('payment-corporate-card-transactions')`.
- Endpoints 5 → ~30 (Card Management, scrape session+challenge, multipart request-approval, link card payments, receipts, purpose-options, memo CRUD, reporting-account clear).
- Wire row JSON: removed invented fields (`card_trns_usage_nm`, `chart_acct_nm`/`_en`/`_level_nm`, `appr_dv`, `post_seq_no`, `post_reg_id`); `approvers[]` flagged always-empty on ledger rows; added `using_type`/`auth_code`/`city`/`note`. Display names resolved client-side (APPROVED DEVIATION).
- DB tables corrected to wired entity set; approval write-path implemented here (`APPR_DV='33'`/`CORPORATE_CARD`).
- FE client `lib/api/payment.ts` + `mappers.ts` → `lib/api/cardTransactions.ts` + `cardTransactionMappers.ts`.

**Cross-cutting index added to both README.md files** — validation controller row, §2.2a dispatch callbacks, §2.2b notification/BLTB. Details in gap-analysis CC-1/CC-2/CC-3.

---

## Process notes

- All edits preserve existing doc structure; accurate sections were left untouched.
- Source-code references in updated docs were re-validated (file paths + line numbers); broken/moved citations fixed.
- Legacy JEX behavior is kept clearly separated from Spring backend and React frontend behavior in every updated file.
- **No application code was modified** by this audit (docs-only).
