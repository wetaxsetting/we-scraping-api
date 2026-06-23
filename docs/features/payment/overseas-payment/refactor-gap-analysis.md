# Overseas Payment — Refactor Gap Analysis

> Gaps classified with the project vocabulary: **MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING**, plus a gap *kind* (code-exists-doc-missing / backend-missing / frontend-missing / test-missing / PO-decision-required / uncertain-needs-confirmation). Hub: [README.md](README.md). Companion: [`../payment-gap-analysis.md`](../../../audits/payment-gap-analysis.md). Verified 2026-06-23.

## Gap table

| ID | Gap | Class | Kind | Owner |
|---|---|---|---|---|
| G-OVRS-1 | Legacy M-mode (`OVRS_PYMN_MSTR`) full per-line detail **parity coverage** — `legacy_mmode` flags/notice retained on the DTO; the Option A (full per-line for every legacy M shape) vs Option B decision is blocked on UAT/PROD M-mode counts (MCP reaches DEV only) | PARTIAL | PO-decision-required | PO + DBA |
| G-OVRS-4 | No frontend tests for overseas screens | MISSING | frontend/test-missing | dev |
| G-OVRS-5 | `OverseasResultRow.apprvr_nm` returns `null` (approver-name join deferred — complex join) | PARTIAL | backend-followup | dev |
| G-OVRS-6 | Dispatch writes real `TB_PAY_*` but no local bank daemon flips `PROC_STAT` → result counts degrade to pending in local R&D | PARTIAL | environmental (not a code gap) | infra |

## G-OVRS-1 — M-mode detail (remaining PO/DBA decision)

**Current code state (not a defect):** the Result/Scheduled detail path renders legacy M-mode records fully — `resolveLegacyMmodeOrThrow` loads real remittance line(s) via `findMmodeDetailRows`, sets `legacy_mmode=false`, and throws `WBE2045` only when an id resolves in neither `OVRS_PYMN_SUMR` nor `OVRS_PYMN_MSTR`. The detail DTO still **carries** the `legacy_mmode` / `legacy_mmode_notice` fields (defaulted false/null via the 19-arg ctor) as a graceful-degradation seam.

**Open decision:** whether the M-mode rendering needs to be widened (Option A — guarantee full per-line parity for **every** legacy M data shape, incl. empty-row / no-`WBE2045` edge cases) or left as-is (Option B). This is **blocked on UAT/PROD M-mode counts** — the MCP reaches only the DEV frame DB (DEV shows 44/77 M-mode rows, none after 2025-09-04), so the real-world M-mode volume that would justify Option A is unknown. **Owner: PO (Mr. Jung) + DBA.** See memory `project_approval_details_mmode_open_decision.md`.

## MATCH (confirmed parity, no action)
- `/result` singular + single controller — confirmed in `OverseasPaymentController`.
- Detail table = `OVRS_PYMN_SUMR_DETL` (not `OVRS_PYMN_DTLS`).
- Approval status derived from `EXPT_RESOL_MSTR.APPR_TYPE_CD` (no `appr_stts` column).
- F4b clickable Approval Ref — `appr_seq_no` surfaced on `OverseasResultRow` (verified by `OverseasPaymentResultApprSeqNoTest`).
- Result status filter = `proc_stat` CSV → derived `trnf_stts` ∈ {C,F,P}; footer never status-filtered (R005_CNT parity).
- Two export paths: list `GET /result/export` (`OverseasPaymentResultExcelWriter`) vs per-batch `POST /result/{id}/export` (`OverseasPaymentResultDetailExportProvider`).
- Result authz override `payment-overseas-result`.

## Cross-links
- [`../payment-gap-analysis.md`](../../../audits/payment-gap-analysis.md) (G-OVRS-1 carried) · [migration-guide.md](migration-guide.md) · [test-scenarios.md](test-scenarios.md)
