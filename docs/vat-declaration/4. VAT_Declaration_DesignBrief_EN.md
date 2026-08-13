# Wireframes & Design Brief
## Project: VAT Declaration & 8% Tax Reduction Annex Module (WeCMS)

This document provides UI/UX design directions for the Designer, specifying the Information Architecture (IA) and UI localization design guidelines.

---

## 1. UI/UX Style Guidelines

*   **Color Palette:**
    *   Use WeCMS's clean, neutral color palette.
    *   **Status Badges:**
        *   `Draft`: Light orange or blue background (e.g. HSL `200, 80%, 90%` with dark blue text).
        *   `Confirmed`: Soft green background (e.g. HSL `120, 70%, 90%` with dark green text) accompanied by a lock icon.
*   **Typography:**
    *   Use a modern, highly readable sans-serif font for tables, such as *Inter* or *Roboto*.
    *   Align all currency values to the right and use standard thousand separators (e.g., `110,892,042`).
*   **Localization-Friendly Layout Design:**
    *   **Label spacing:** Allocate ample width for indicator labels. Text lengths in English and Korean are often longer than in Vietnamese.
    *   Avoid using fixed-width buttons that contain text; use horizontal padding to let buttons scale dynamically with translated text.

---

## 2. Information Architecture (IA) Checklist

### 2.1 Dashboard Screen
*   [ ] **Header Area:** Screen title ("VAT Declaration"), language selection dropdown (VI/EN/KO), and a "+ Create Declaration" button.
*   [ ] **Filters:** Filter by tax year (Dropdown) and status (All / Draft / Confirmed).
*   [ ] **Declarations Table:** Mandatory columns:
    *   Tax Period
    *   Signatory Name
    *   Signing Date
    *   Status (Color-coded badges)
    *   Last Updated
    *   Actions (View/Edit, Lock/Unlock, Delete)
*   [ ] **Pagination:** Navigation buttons under the table.

### 2.2 Create Period Modal (Centered popup)
*   [ ] Period type dropdown: "Month" or "Quarter".
*   [ ] Period value dropdown.
*   [ ] Tax year input (Defaults to current year).
*   [ ] Annexes checklist (Annex 8% selected by default).
*   [ ] Action buttons: "Create" (Primary) and "Cancel".

### 2.3 Main Declaration Modal (Takes up 95% of viewport)
*   [ ] **Header Area:** Display period name and current status badge (Draft/Confirmed).
*   [ ] **Navigation Tabs:**
    *   Tab 1: "Main Form 01/GTGT"
    *   Tab 2: "8% VAT Reduction Annex (Resolution 204)"
*   [ ] **Tab 01/GTGT Table:**
    *   Column 1: Indicator Code (e.g., [22])
    *   Column 2: Indicator Name (Localized)
    *   Column 3: Value (Editable input or auto-calculated label)
    *   *Visual cue:* White background for editable cells, light grey for read-only auto-computed cells.
*   [ ] **Modal Footer Actions:**
    *   Action buttons: Save Draft, Sync, Lock/Unlock.
    *   Export buttons: Export XML, Export Excel, Print PDF.

### 2.4 Invoice Details Modal (Opened from the Annex tab)
*   [ ] Modal title ("Details of Input/Output Invoices Eligible for 8% VAT").
*   [ ] Search input (Placeholder: "Search by invoice number, symbol, date...").
*   [ ] Grouped table (Accordion-style grid):
    *   Clicking a group header expands or collapses the child invoice list.
    *   Group header displays: Product Name, count of invoices, total exclusive revenue, total tax reduced.
*   [ ] Pagination navigation.
*   [ ] Modal actions: "Refresh", "Export Excel", and "Close".
