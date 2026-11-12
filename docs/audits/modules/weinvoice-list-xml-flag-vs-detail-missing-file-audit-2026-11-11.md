# WeInvoice: List Shows XML but the Detail Says "chưa có" — Audit

**Date:** 2026-11-11
**Type:** AUDIT / INVESTIGATION
**Current Work Classification:** AUDIT
**Finding Classification:** ISSUE — engine files split across machines (+ a recovery gap in FETCH_XML)
**Status:** COMPLETE
**Investigation Status:** COMPLETE · **Approval Status:** PENDING · **Implementation Status:** NOT STARTED
**Repository:** wecms-api (`we-scraping-api`)

## Observed Behavior

Developer report (2026-11-11): the invoice list shows the XML indicator lit, but the invoice detail's file list shows
the XML as "chưa có".

## Expected Behavior

The list and the detail agree on whether an XML exists.

## Scope

`GET /invoices` `has_xml` vs `GET /invoices/{id}/files` XML slot.

## Initial Evidence

- `has_xml` is computed in SQL (`InvoiceQueryRepository`), the detail's file size by `StoredFiles.engineFileSize`
  (file on disk under `WECMS_WEINVOICE_ENGINE_OUTPUT_DIR`).

## Investigation Plan

1. Read both computations.
2. Check DB + disk for invoices with `has_xml` true and no XML file on this machine.

## Backend Trace

- List `has_xml` (`InvoiceQueryRepository.BASE_SELECT`): `invoice_xml` row exists **or** an uploaded XML exists — a DB
  fact only.
- Detail XML slot (`InvoiceFileService.list`): `size = StoredFiles.engineFileSize(xml_path)` — the file on **this
  server's** disk under `WECMS_WEINVOICE_ENGINE_OUTPUT_DIR`; missing → `size null` → web "chưa có" (by design since
  2026-11-11, `weinvoice-uploaded-pdf-replaces-provider-pdf-2026-11-11`).

## Database / Disk Evidence (read-only, 2026-11-11, from the laptop)

`invoice_xml` has 29,962 rows; only **4,811** of their `xml_path` exist on the laptop's `output/`, the other 25,151 were
written by another engine machine (.13 or elsewhere). Per MST (on laptop / not on laptop): 0108515678-001 636/2,633 ·
0318793907 294/3,607 · 1101786046 133/435 · 1101901122 162/273 · 1201496252 2,853/6,277 · 3600790154 559/1,590 ·
3700860461 174/10,336. So on .13, up to ~4.8k XML (those the laptop wrote) show lit in the list and "chưa có" in the
detail. .13's own disk was not checked (no access from here).

The laptop still has the scheduled task **"GDT Invoice Job"** enabled (state Ready) → it keeps writing new XML/PDF to the
laptop disk, so the split grows.

## Root Cause

Two engines write to one DB but each keeps files on its own disk. The list flag reads the DB, the detail reads the
disk of the server the api runs on. Not a code defect in the list or the detail — they each report a true fact.

Recovery gap: engine `do_fetch_xml` (`web/weinvoice_worker.py`) skips any invoice with an `invoice_xml` row as
`already`, even when its file is not on this machine — so "Lấy XML" cannot re-download it; only manual upload works.

## Recommended Implementation

1. **Operational (main fix, no code):** copy the laptop's `output\xml`, `pdf`, `pdf_gdt`, `temp` to .13 without
   overwriting (`robocopy /E /XC /XN /XO`), then disable "GDT Invoice Job" (and any other engine task) on the laptop so
   only .13 writes files.
2. **Engine (optional):** `do_fetch_xml` treats "row exists but file missing here" as wanted and re-downloads into the
   stored `xml_path` (same pattern as `Importer._missing_here`). Regression test: row + no file → re-fetched.
3. **Optional UI:** list could flag "XML on another machine" — needs a disk check per row; not recommended once (1) is done.

## Affected Repositories

Engine (we-scraping) for (2); none for (1).

## DB Impact

NONE.
