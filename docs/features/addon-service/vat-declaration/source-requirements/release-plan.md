# Release Plan & Roadmap
## Project: VAT Declaration & 8% Tax Reduction Annex Module (WeCMS)

This document establishes the release plan and MoSCoW prioritization matrix for the development lifecycle.

---

## 1. Development Prioritization (MoSCoW Method)

To guarantee the project is delivered on schedule while satisfying core business requirements, tasks are divided into the following priorities:

### 1.1 MUST-HAVE (Core MVP features)
*   [ ] **Declaration Period Initialization:** Create monthly/quarterly tax declarations for any tax year.
*   [ ] **Auto-Calculations:** Implement real-time calculations of dependent indicators (`[22]` to `[43]`) on the main form.
*   [ ] **Manual Overrides:** Allow users to edit input-friendly white cells on the form.
*   [ ] **Sequential Lock/Unlock:** Add locking logic checks for preceding periods and block unlock requests if subsequent periods are locked.
*   [ ] **XML Export:** Export files compatible with GDT's HTKK software.
*   [ ] **Basic 8% Annex:** Display input/output invoice grids for the 8% annex and support direct adding/deleting of rows.

### 1.2 SHOULD-HAVE (Highly recommended for UX and operation)
*   [ ] **Auto Data Consolidation:** Read raw purchase and sales invoices in the database to pre-fill numbers during initialization.
*   [ ] **Localization (Multi-language):** Toggle UI translation (VI/EN/KO) in real-time.
*   [ ] **Data Resync (Sync CSDL):** Re-run database queries on raw invoices and overwrite draft declaration numbers.
*   [ ] **Accordion Invoice Details Modal:** Group invoices by product name, support collapse/expand, pagination, and real-time query filter.
*   [ ] **Excel/PDF Export:** Export detailed invoices to Excel and output the main declaration to A4 PDF.

### 1.3 COULD-HAVE (Nice-to-have features)
*   [ ] **Settings Integration:** Read default signatory, tax agent, and business addresses from the WeCMS *Company Settings* page.
*   [ ] **Excel Import for Annexes:** Allow users to upload an Excel file to bulk-insert invoice rows into the annex grid.

### 1.4 WON'T-HAVE (Reserved for Phase 2)
*   [ ] **Digital Signing (CA Integration):** Sign exported XML files directly using a USB token or cloud HSM in WeCMS.
*   [ ] **Direct Tax Portal Submission:** Submit tax files directly to the tax authority via T-VAN protocols.

---

## 2. Release Roadmap

### Phase 1: MVP Development (Sprint 1 - Sprint 2)
*   **Goal:** Build the database schema, core REST APIs (`GET`, `POST`, `PUT`, `DELETE`), indicator formula logic, and HTKK-compliant XML generation.
*   **Deliverable:** Internal test build demonstrating the flow: Create period $\rightarrow$ edit indicators $\rightarrow$ lock $\rightarrow$ export XML.

### Phase 2: UX Improvements & Multi-language (Sprint 3 - Sprint 4)
*   **Goal:** Integrate raw invoice data aggregation, build the 8% annex accordion grid, detail search modals, and release translation dictionaries (VI/EN/KO).
*   **Deliverable:** Complete UAT build ready for customer testing.

### Phase 3: Deployment & Maintenance
*   **Goal:** Assist businesses in filing their first VAT tax declaration. Resolve bugs encountered during HTKK file imports.

---

## 3. UAT Verification Checklist for PO/PA

*   [ ] **Formula Interconnection:** Edit `[23]`, verify that `[24]` and `[25]` automatically update correctly.
*   [ ] **Locking Constraint:** Create a Q2/2026 declaration. Attempt to lock it when Q1/2026 is unlocked. Verify that the system blocks and displays the correct error message.
*   [ ] **HTKK Import Verification:** Export XML, upload it into GDT's HTKK tool. Verify that all fields display without error and numbers match WeCMS exactly.
*   [ ] **Language Toggle:** Toggle languages on the dashboard and main modal, verify that no layout containers break.
