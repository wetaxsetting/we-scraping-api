# Overseas Payment — Test Scenarios

> Backend test inventory + frontend coverage gap. Hub: [README.md](README.md). Verified 2026-06-23.

## Backend tests (exist)

| Test class | Scenarios |
|---|---|
| `OverseasPaymentDetailTest` | S-mode detail returns attachments + `legacy_mmode=false`; legacy M-mode renders a real remittance row (not header-only); M-mode resolved via DOC_NO→APPR_SEQ_NO fallback; id found in neither table still throws `WBE2045`. |
| `OverseasPaymentResultApprSeqNoTest` | F4b — `appr_seq_no` surfaced from the result query (col 17); null for drafts (Approval Ref renders as plain text). |
| `OverseasPaymentResultStatusTest` | Real success/failed/pending counts + computed `TRNF_STTS`; Completed checkbox → {C,F}; In-Progress → {P}; no filter → null; footer totals never status-filtered; list returns mapped rows. |
| `OverseasPaymentResultExportAuditTest` | List export writes one ERP 'W' row (code 14) and streams; M-mode batch emits a line from `OVRS_PYMN_MSTR`. |
| `OverseasPaymentResultDownloadAuditTest` | Dispatched (`TRNF_YN='Y'`) download writes a 'W' audit row; non-dispatched writes none. |
| `OverseasPaymentRequestApprovalBackstampTest` | H-1 — request-approval back-stamps `OUT_BANK_CD`/`WDRL_ACT_NO`/`WDRL_CCY_CD` onto `OVRS_PYMN_SUMR`. |
| `OverseasDispatchExecutorAutoReceiveTest` | File-path dispatch registers the reception (auto-receive) hand-off after commit. |
| `OverseasDispatchExecutorBankPrtnIdTest` | BANK_PRTN_ID read from `TB_FIRM_CONF`; FUND_TP='S' (Shinhan overseas remittance); dispatch blocked (`WBE2089`) when conf absent / BANK_PRTN_ID blank. |

## Backend coverage gaps (MISSING / suggested)
- No dedicated test for `requestApprovalMulti` (M-type multi-approval) — covered only indirectly.
- No test for `changeApprovalLine` / `removeApprovalLine` / `deleteDraftBatch` guard transitions (`WBE2128`/`WBE2098`/`WBE2099`).
- No test for `excelPreview` per-cell validation or `excelUpload` key allocation/retry.
- No test for the cutoff (weekend/SWIFT) dispatch block.

## Frontend tests (MISSING — G-OVRS-4)
No `*.test.tsx` / `*.spec.ts` for any overseas screen. Suggested baseline: list-page filter→fetch param mapping, Completed/In-Progress checkbox → `proc_stat`, Approval Ref click → Approval Details popup (F4b), Excel upload preview error gating, reporting-account save round-trip, banking-match dialog.

## Cross-links
- [refactor-gap-analysis.md](refactor-gap-analysis.md) · [source-code-index.md](source-code-index.md) · web tests: [`wecms-web/.../overseas-payment/test-scenarios.md`](../../../../../wecms-web/docs/features/payment/overseas-payment/test-scenarios.md)
