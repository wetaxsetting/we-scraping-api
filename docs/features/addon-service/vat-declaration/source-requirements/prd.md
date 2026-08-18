# Product Requirements Document (PRD)
## Project: VAT Declaration & 8% Tax Reduction Annex Module (WeCMS)

| Document Info | Details |
| :--- | :--- |
| **Project** | WeCMS - VAT Declaration Module (Form 01/GTGT) |
| **Version** | v2.0 |
| **Author** | Product Owner (PO) |
| **Date** | 2026-06-29 |
| **Target Audience** | Entire Project Team (Designers, Developers, QA) |

---

## 1. Vision & Goals
*   **Current Problem:** Businesses using WeCMS face difficulties in manually consolidating input/output invoices to declare VAT and prepare the 8% VAT reduction annex. This process is time-consuming and prone to data discrepancies.
*   **Product Goal:** Provide a module that automates the VAT declaration (Form 01/GTGT) and the 8% VAT Reduction Annex (in accordance with Resolution 204/2025/QH15) based on invoice data available in the WeCMS system.
*   **Key Performance Indicators (KPIs):**
    *   Reduce 90% of the time required for accountants to prepare VAT declarations.
    *   100% success rate when importing exported XML files into the tax authority's HTKK software.
    *   Fully support multi-language experience (Vietnamese, English, Korean) for FDI companies.

---

## 2. Product Scope

### 2.1 In-Scope
*   **Declaration List Management:** Search and filter by year/status (Draft / Confirmed).
*   **Declaration Period Initialization:** Allow creating declarations on a Monthly or Quarterly basis. Automatically carry forward the previous period's deductible balance (Indicator `[22]`).
*   **Automatic Data Consolidation:** Automatically aggregate revenue and VAT from raw input/output invoices in the database and populate them into corresponding indicators of the main declaration and the 8% reduction annex.
*   **Real-time Editing & Calculation:** Allow users to manually override certain indicators, with the system automatically recalculating dependent indicators in real-time.
*   **8% VAT Reduction Annex:** Detail input and output invoices eligible for the 2% tax reduction (from 10% down to 8%). Support adding/deleting rows directly on the grid.
*   **Sequential Sync & Locking:** Implement a locking mechanism based on chronological order to ensure data integrity.
*   **Data Export:** Export HTKK-compliant XML files, export invoice detail list to Excel, and export the main declaration to PDF for printing.
*   **Localization (Multi-language):** Real-time UI translations into Vietnamese, English, and Korean.

### 2.2 Out-of-Scope
*   Automatic digital signing (CA) directly on WeCMS.
*   Direct integration with the General Department of Taxation portal (T-VAN) to submit declarations online.
*   Editing raw source invoices directly from the VAT declaration interface.

---

## 3. User Stories & Acceptance Criteria (US & AC)

### US-01: Initialize a new tax declaration period
*   **As a** Corporate Accountant,
*   **I want to** select the declaration period (Month/Quarter), tax year, and associated annexes when creating a new declaration,
*   **So that** the system initializes the correct tax filing configuration.
*   **Acceptance Criteria (AC):**
    *   *Given* the user clicks "+ Create Declaration":
    *   *When* the user selects the period and year, and clicks "Create":
    *   *Then* the system must check whether a declaration for this period already exists. If yes, display a duplication error in the current active language.
    *   *Then* the system must check whether the preceding period is locked (if applicable). If not locked, block initialization and show an error message requesting to lock the previous period first.
    *   *Then* automatically retrieve indicator `[43]` of the previous period and populate it into indicator `[22]` of the current period.

### US-02: Auto-aggregate data from raw invoices
*   **As a** Corporate Accountant,
*   **I want** the system to automatically scan and calculate default values from WeCMS raw input/output invoices,
*   **So that** I do not have to manually sum up each invoice.
*   **Acceptance Criteria (AC):**
    *   *Given* a newly initialized declaration:
    *   *When* data is loaded:
    *   *Then* the system consolidates data from `tbl_pur_vat` and `tbl_sale_vat` matching the selected month/quarter and active status (`ctuktthue <> 'N'`).
    *   *Then* distribute tax-exclusive revenue and VAT accurately into indicators `[23]`, `[24]`, `[26]`, `[29]`, `[30]`, `[31]`, `[32]`, `[33]`, and `[32a]`.

### US-03: Chronological data locking
*   **As a** Chief Accountant,
*   **I want to** lock the declaration data after verification,
*   **So that** subordinate staff cannot modify numbers and the deductible carry-forward balance to the next period remains secure.
*   **Acceptance Criteria (AC):**
    *   *Given* a declaration in `Draft` status:
    *   *When* the user clicks the "Lock" button:
    *   *Then* the system verifies if the preceding period is locked. If yes, change the status of the declaration to `Confirmed` and disable all input fields and the sync button.
    *   *Then* when unlocking, the system checks that the subsequent period (if any) is in Draft status or has not been created before allowing the unlock action.
