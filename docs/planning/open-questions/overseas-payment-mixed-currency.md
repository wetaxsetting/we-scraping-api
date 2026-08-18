# OPEN QUESTION (H2) — Overseas Payment Excel Upload: mixed-currency policy

> **Owner:** Mr. Jung (PO)
> **Raised:** 2026-06-29 (Overseas Payment Excel Upload parity fixes)
> **Status:** ✅ CLOSED 2026-06-29 — PO decision: **keep legacy behavior** (option 1). The
> already-shipped legacy-parity block is the final behaviour; no further code change required.
> **Feature:** Payment → Overseas Payment → Scheduled Payment → Excel Upload
> **Audit:** [../../audits/overseas-payment-excel-upload-audit.md](../../audits/legacy-parity/overseas-payment-excel-upload-audit.md) §10 H2

## DECISION (Mr. Jung, 2026-06-29) — CLOSED

**Keep legacy behavior.** Final business rule:
- Excel uploads must contain **only one transfer currency**.
- Mixed-currency Excel uploads are **rejected** (legacy `WBW1160` behaviour).
- One upload batch always represents one currency.
- `CCY_CD = "MULTI"` is **not supported** and must never be persisted.

**Reason:** the refactor follows **100% legacy parity** — no new business rules are introduced
unless explicitly approved in the future.

The interim implementation shipped 2026-06-29 already enforces exactly this, so it is now the
**final** behaviour (not interim). No additional change needed.

## WBW1160 message + UI parity (2026-06-29)

A follow-up confirmed the legacy WBW1160 *presentation* and aligned the refactor message text:

- **Legacy display is NOT a popup.** `pymn_0007_003` returns WBW1160 as a per-row cell error
  (`ERR_FLD`/`ERR_FLD_CAUS`); the JS (`pymn_0007_003.js:179-243`) renders it as an inline
  **Transfer Currency cell** red highlight (`err_click`) + hover tooltip. The upload modal stays open,
  the preview grid stays, the selected file is retained, and the Upload button is disabled while any
  error exists. There is no "OK to dismiss" popup.
- **Refactor already matched the display mechanism** — the shared `ExcelUploadDialog` tints the errored
  cell and shows a hover `Tooltip` from the per-cell message, and disables Upload on `errorCount>0`.
  No frontend change was required.
- **Message text aligned to legacy:** a `WBW1160` key was added to `messages.properties` with the
  **verbatim** legacy enum text — "The file is containing multiple currencies. Please check again."
  `OverseasPaymentExcelService.preview` resolves it via `MessageResolver` for the cell error;
  `doSubmit` throws `BusinessException("WBW1160")`. EN-only, matching legacy (no KO/VI/KM dictionary
  entry for WBW1160 exists in JEX).
- **Note (cross-module):** the 4 SCDUL payment Excel parsers use a reworded `WBE2120` for the same
  guard. Overseas now uses the verbatim `WBW1160` text per the explicit parity request; aligning the
  other four is out of scope here (separate decision if cross-module wording consistency is wanted).

## The question

Should an overseas-payment Excel upload be allowed to contain **more than one transfer
currency** in a single file?

## Legacy behavior (source of truth)

Legacy JEX **does not allow** mixed currencies in one upload. `pymn_0007_003_r001_act.jsp:333-342`
flags every row whose currency differs from the first row's with `WBW1160`
("Cannot mix currencies in a single upload"), which keeps the upload button disabled.
On save, `pymn_0007_003_c001_act.jsp:79-94` groups rows by currency and creates one
`OVRS_PYMN_SUMR` per currency — but because validation already blocks mixed files, each
upload is effectively single-currency and produces exactly one summary row.

## What the refactor was doing (the bug)

The refactor frontend computed `ccy_cd = ccyCodes.length === 1 ? ccyCodes[0] : "MULTI"` and the
backend accepted `"MULTI"`, persisting a single `OVRS_PYMN_SUMR` row with `CCY_CD = "MULTI"` — a
value legacy never writes. This breaks JEX → refactor data migration and the FX/dispatch path.

## Interim behaviour shipped (2026-06-29, approved direction "follow legacy parity")

Until the PO decides, the refactor now **blocks mixed currency**, matching legacy:

- **Preview** (`OverseasPaymentExcelService.preview`) flags the second-and-later distinct currency
  per row with "Cannot mix currencies in a single upload" (legacy `WBW1160` equivalent), so the
  upload button stays disabled.
- **Save** (`OverseasPaymentExcelService.doSubmit`) defensively rejects `ccy_cd = "MULTI"` and any
  payload with more than one distinct row currency → `WBE_VALIDATION`.
- **Frontend** never sends `"MULTI"` — it submits the single detected currency.

## Decision needed

1. **Keep legacy parity (recommended):** one currency per upload, forever. Close this OQ as "no
   change — interim behaviour is final."
2. **Allow mixed currency (new behaviour):** if the business wants multi-currency files, we must
   decide the persistence shape — either (a) replicate legacy per-currency grouping (one
   `OVRS_PYMN_SUMR` per currency from one file), or (b) introduce a genuinely multi-currency batch
   model. Either is a **structural change** that needs PO sign-off and a migration-safety review per
   the refactor DB rules (it increases drift from legacy).

**Until Mr. Jung decides, option 1 (block) stands.**
