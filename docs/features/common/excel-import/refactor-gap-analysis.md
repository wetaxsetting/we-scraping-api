# Excel Import — Refactor Gap Analysis (Backend)

**Part of:** Common → excel-import
**Last verified:** 2026-06-23

Gap vocabulary follows the common-module standard: **DONE** (parity), **PARTIAL** (works but diverges from the ideal/shared pattern), **MISSING** (not built; tracked as an open decision). See [`../../../audits/common-gap-analysis.md`](../../../audits/common-gap-analysis.md).

## Headline gap — C3: shared backend import service = MISSING

| ID | Gap | Status |
|---|---|---|
| **C3** | **No single shared backend Excel-import service.** Each owning feature reimplements parse / validate / persist / audit. Glob `common/**/*Import*.java` → **no files**; `common/export/` is export-only. | **MISSING (open decision)** |

C3 is an **open decision**, not a bug to fix silently. Consolidating four feature contracts into a shared import service would touch Purpose, VAS, CoA, and Contacts at once and must be weighed against the migration-safety priority — it requires PO (Mr. Jung) sign-off before any work. Preserve this open decision; do not "resolve" it by implementation.

## Per-feature divergence — PARTIAL

| ID | Divergence | Status | Note |
|---|---|---|---|
| C3a | Two import architectures exist: **canonical** (Purpose, VAS — server multipart preview + atomic import) vs **variant** (CoA, Contacts — client parse + per-row non-atomic JSON) | **PARTIAL** | Both work; they are not the same contract shape |
| C3b | No server-side preview for CoA / Contacts (file parsed in browser) | **PARTIAL** | Only Purpose/VAS validate the workbook server-side |
| C3c | VAS Account key generation uses **Java MAX+1**, not `wb_get_next_val` | **PARTIAL / deviation** | Deliberate + documented in entity Javadoc, but not a listed exception in the funnel memo `project_wb_get_next_val_funnel.md` — track for PO confirmation |
| C3d | Atomicity differs (Purpose/VAS atomic; CoA/Contacts per-row) | **PARTIAL** | A row failure rolls back the batch only for canonical features |

## DONE (parity / working)

| Item | Status |
|---|---|
| Shared **frontend** shell `<ExcelUploadDialog>` + `templateValidator` reused across consumers | **DONE** |
| Server-side workbook validation (row cap / header / readable) with `WBE2041/2042/2043` (Purpose, VAS) | **DONE** |
| Import re-validates against live DB before persisting (Purpose, VAS) | **DONE** |
| Audit row appended on import with slug `WSVC_ID` (Purpose, VAS) | **DONE** |
| Import flows write to existing legacy tables (no refactor-only table) | **DONE** |

## Localization gap

⚠️ `WBE2041/2042/2043` live only in `messages.properties`, not `messages_en.properties` (only `WBE2040` is there). Flagged **PARTIAL** — confirm whether EN locale resolution falls back to the base file before treating this as a defect.

## Related

- [README.md](README.md) · [interface-specification.md](interface-specification.md) · [source-code-index.md](source-code-index.md)
- Common-module audit deliverables: [`../../../audits/common-gap-analysis.md`](../../../audits/common-gap-analysis.md) · [`../../../audits/common-source-index.md`](../../../audits/common-source-index.md)
