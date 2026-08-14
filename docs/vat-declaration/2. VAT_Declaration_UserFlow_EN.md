# Business Flow & User Flow Specification
## Project: VAT Declaration & 8% Tax Reduction Annex Module (WeCMS)

This document specifies the user flow, screen transitions, and business logic interactions within the VAT Declaration module.

---

## 1. Business Logic Flow Chart

Below is the detailed flow chart of user interactions from the Dashboard to the successful XML export:

```mermaid
graph TD
    classDef startEnd fill:#f9e8d2,stroke:#d68a28,stroke-width:2px;
    classDef decision fill:#fff,stroke:#333,stroke-dasharray: 5 5,stroke-width:2px;
    classDef process fill:#f2b134,stroke:#e89005,stroke-width:1.5px;

    d_start([START - Log in WeCMS]) --> d_nav[Navigate to VAT Declaration Dashboard]
    d_nav --> d_lang[Select Language: VI / EN / KO]
    d_lang --> d_choice{Action?}
    
    %% New creation branch
    d_choice -- "Create New" --> d_create_modal[Open Create Period Modal]
    d_create_modal --> c_select[Select Period, Year & Annexes]
    c_select --> c_create[Click Create Declaration]
    c_create --> c_exists{Period already exists?}
    c_exists -- "Yes" --> c_error_trung[Show Error: Period already exists] --> c_select
    c_exists -- "No" --> c_seq{Preceding period locked?}
    c_seq -- "No" --> c_error_seq[Show Error: Preceding period not locked] --> c_select
    c_seq -- "Yes" --> c_init[Initialize Data & Compute Default Indicators]
    c_init --> m_open[Open Main Declaration Modal]

    %% List interaction branch
    d_choice -- "View/Edit" --> d_filter[Search/Filter list]
    d_filter --> m_open
    
    %% Main modal
    m_open --> m_tab{Select Tab}
    m_tab -- "01/GTGT main form" --> m_main_tab[Display main indicators table]
    m_tab -- "8% reduction annex" --> m_annex_tab[Display 3 Annex sections]
    
    m_main_tab --> m_edit[Input data in white cells]
    m_edit --> m_calc[System automatically recalculates real-time]
    
    m_annex_tab --> m_view_inv[Click 'Invoice list for tax reduction']
    m_view_inv --> i_open[Open Invoice List Modal]
    i_open --> i_action{Action in modal?}
    i_action -- "Search / Paginate" --> i_search[Real-time search & Change page] --> i_open
    i_action -- "Sync / Export Excel" --> i_sync[Sync DB / Export Excel] --> i_open
    i_action -- "Close" --> m_annex_tab
    
    m_calc --> m_save[Save Draft]
    m_calc --> m_lock{Lock declaration?}
    m_lock -- "Yes" --> m_lock_check{Preceding period locked?}
    m_lock_check -- "No" --> m_lock_err[Show Error: Must lock preceding period]
    m_lock_check -- "Yes" --> m_locked[Status updated to Confirmed]
    
    m_locked --> m_xml[Export XML & Download]
    
    class d_start,m_open,i_open startEnd;
    class d_choice,c_exists,c_seq,m_tab,m_lock,m_lock_check,i_action decision;
    class d_nav,d_lang,d_create_modal,c_select,c_create,c_error_trung,c_error_seq,c_init,m_main_tab,m_annex_tab,m_edit,m_calc,m_save,m_lock_err,m_locked,m_xml,i_search,i_sync process;
```

---

## 2. Detailed Steps of the User Journey

### Step 1: Navigation & Language Selection (Dashboard)
*   The user accesses the **VAT Declaration** item on the system menu.
*   **Localization experience:** The system instantly displays in the language saved in the session config or by default. If the user selects a different language (English / 한국어), the entire static text on the UI changes instantly.

### Step 2: Create a new declaration period (Create Period)
*   The user clicks **"+ Create Declaration"**.
*   A modal appears prompting the user to select:
    1.  Tax Period (Month or Quarter).
    2.  Period Value (Month 1-12 or Quarter 1-4).
    3.  Tax Year (Default is current year).
    4.  Checkboxes for associated annexes.
*   **Constraint checks:** The system only allows creation when the period doesn't overlap with an existing one, and the preceding period is in **Confirmed (Locked)** status.

### Step 3: View & Enter indicators (Main Declaration Modal)
*   When the declaration modal opens, the user lands on the **01/GTGT Main Form** tab.
*   The user checks the values automatically aggregated from raw invoices.
*   If adjustments are needed, the user clicks on the editable input fields (colored white).
*   **Real-time calculations:** As soon as focus leaves the input field or Enter is pressed, all dependent indicators (colored grey) automatically update based on the formulas.

### Step 4: Annex interaction & Invoice details modal
*   On the **8% VAT Reduction Annex** tab, the user views the summary table.
*   The user can right-click on any row in the annex table to:
    *   **Add row:** Add a blank row to manually record an external invoice eligible for tax reduction.
    *   **Delete row:** Remove the selected invoice row from the annex.
*   To view the original invoices forming these numbers, the user clicks **"Invoice list for tax reduction"**.
*   The invoice modal opens, letting the user:
    *   Collapse/expand invoice groups by product name to verify totals.
    *   Enter search keywords (Invoice number, symbol, date...) to find specific invoices.
    *   Click **"Export Excel"** to hand over the details to auditors.

### Step 5: Lock & Export XML
*   The user clicks **"Lock"** to confirm the numbers. The declaration changes from Draft to Confirmed, locking all inputs.
*   The user clicks **"Export XML"**. The XML file is downloaded, named in accordance with the 13-digit tax code and period formatting rules to be imported directly into the GDT's HTKK software.
