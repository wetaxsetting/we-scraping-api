# Technical Specifications & Integration Requirements
## Project: VAT Declaration & 8% Tax Reduction Annex Module (WeCMS)

This document provides technical specifications for the Database schema, APIs, invoice data aggregation logic, and the XML export schema for the HTKK software.

---

## 1. Database Schema Design

WeCMS uses PostgreSQL. Below is the specification for the two tables storing declaration data:

### 1.1 `vat_declarations` Table
Stores high-level configuration and lifecycle status of each VAT declaration.

```sql
CREATE TABLE vat_declarations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    period_type VARCHAR(10) NOT NULL, -- 'month' or 'quarter'
    period_value INT NOT NULL,        -- Month (1-12) or Quarter (1-4)
    year INT NOT NULL,                 -- e.g., 2026
    status VARCHAR(20) DEFAULT 'Draft', -- 'Draft' or 'Confirmed'
    company_name VARCHAR(255) NOT NULL,
    tax_code VARCHAR(20) NOT NULL,
    indicators JSONB DEFAULT '{}',     -- Additional configuration metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 1.2 `vat_declaration_indicators` Table
Stores parsed, typed indicator currency values for high-performance reporting and XML serialization.

```sql
CREATE TABLE vat_declaration_indicators (
    id SERIAL PRIMARY KEY,
    declaration_id UUID REFERENCES vat_declarations(id) ON DELETE CASCADE,
    ma_nganhnghe VARCHAR(10) DEFAULT '00',
    ten_nganhnghe VARCHAR(255) DEFAULT 'Hoạt động sản xuất kinh doanh thông thường',
    tieumuchachtoan VARCHAR(10) DEFAULT '1701',
    -- Indicators from [21] to [43]
    ct21 BIGINT DEFAULT 0,
    ct22 BIGINT DEFAULT 0,
    ct23 BIGINT DEFAULT 0, -- Purchase revenue
    ct24 BIGINT DEFAULT 0, -- Purchase VAT
    ct25 BIGINT DEFAULT 0, -- Deductible VAT
    ct26 BIGINT DEFAULT 0,
    ct27 BIGINT DEFAULT 0,
    ct28 BIGINT DEFAULT 0,
    ct29 BIGINT DEFAULT 0,
    ct30 BIGINT DEFAULT 0,
    ct31 BIGINT DEFAULT 0,
    ct32 BIGINT DEFAULT 0,
    ct33 BIGINT DEFAULT 0,
    ct32a BIGINT DEFAULT 0,
    ct34 BIGINT DEFAULT 0,
    ct35 BIGINT DEFAULT 0,
    ct36 BIGINT DEFAULT 0,
    ct37 BIGINT DEFAULT 0,
    ct38 BIGINT DEFAULT 0,
    ct39a BIGINT DEFAULT 0,
    ct40a BIGINT DEFAULT 0,
    ct40b BIGINT DEFAULT 0,
    ct40 BIGINT DEFAULT 0,
    ct41 BIGINT DEFAULT 0,
    ct42 BIGINT DEFAULT 0,
    ct43 BIGINT DEFAULT 0,
    -- Auxiliary info
    signatory_name VARCHAR(100) DEFAULT '',
    signing_date VARCHAR(20) DEFAULT '',
    agent_name VARCHAR(255) DEFAULT '',
    agent_cert VARCHAR(100) DEFAULT '',
    province VARCHAR(100) DEFAULT '',
    district VARCHAR(100) DEFAULT '',
    ward VARCHAR(100) DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 2. API Reference (Next.js API Routes)

### 2.1 Get Declarations List
*   **Endpoint:** `GET /api/declarations`
*   **Response (200 OK):**
    ```json
    [
      {
        "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
        "period_type": "quarter",
        "period_value": 2,
        "year": 2026,
        "status": "Confirmed",
        "company_name": "CÔNG TY TNHH WEBCASH VIỆT NAM",
        "tax_code": "1201496252"
      }
    ]
    ```

### 2.2 Create New Declaration
*   **Endpoint:** `POST /api/declarations`
*   **Payload:**
    ```json
    {
      "period_type": "quarter",
      "period_value": 2,
      "year": 2026,
      "industry_type": "Hoạt động sản xuất kinh doanh thông thường",
      "selected_annexes": ["PL 142/2024/QH15-174/2024/QH15-204/2025/QH15"]
    }
    ```
*   **Backend execution flow:**
    1.  Scan `tbl_pur_vat` JOIN `tbl_pur_mst` filtering by date within Q2/2026 (`kkkthue` matching `2026-04`, `2026-05`, `2026-06`) to calculate sum of purchase revenue (`[23]`), VAT (`[24]`).
    2.  Scan `tbl_sale_vat` JOIN `tbl_sale_mst` and distribute tax-exclusive revenues to `[26]`, `[29]`, `[30]`, `[32]`, `[32a]` depending on tax rates.
    3.  Create record entries in `vat_declarations` and `vat_declaration_indicators`.

### 2.3 Sync & Update Declaration
*   **Endpoint:** `PUT /api/declarations/[id]`
*   **Sync parameter:** `PUT /api/declarations/[id]?sync=true`
    *   *Backend action:* Rescan raw database invoices, compute values, and overwrite indicators (only valid when status is `Draft`).

---

## 3. HTKK XML Schema Export Specifications

During XML generation, backend outputs an XML tree structure compatible with GDT **Form 01/GTGT Circular 80/2021/TT-BTC**.

### 3.1 Filename Rule (Fixed length suffix)
The file downloaded must be formatted as:
`[MST_13_DIGITS]-01_GTGT_TT80-[TAX_PERIOD]-L[REVISION_NUMBER].xml`
*   *Tax Code normalization:* If the business's tax code is 10 digits (e.g. `1201496252`), pad with `000` to make it 13 digits (`1201496252000`).
*   *Tax Period format:* Monthly is `M052026` (May 2026), Quarterly is `Q22026` (Quarter 2 2026).

### 3.2 XML Node Mapping
Indicators from `vat_declaration_indicators` are mapped directly to corresponding XML tags under `<CTieuTKhaiChinh>`:

```xml
<ct21>0</ct21>
<ct22>110892042</ct22>
<GiaTriVaThueGTGTHHDVMuaVao>
  <ct23>2500000000</ct23>
  <ct24>250000000</ct24>
</GiaTriVaThueGTGTHHDVMuaVao>
<ct25>250000000</ct25>
<!-- ... -->
<ct43>110892042</ct43>
```
Also, the `<loaiTKhai>` tag receives `'C'` (Official) or `'B'` (Supplement), and the `<soLan>` tag receives the supplementary revision number (`0` for initial).
