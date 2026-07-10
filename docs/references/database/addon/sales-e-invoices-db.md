# Add-on Service > Sales e-Invoices — Database Design

**Module:** Add-on Service
**Refactor screen ID (proposed):** `addon-sales-einvoices`
**JEX counterpart:** `einv_0001_001_r001` (Sales) / `einv_0002_001_r001` (Expenses) — both legacy services read the same physical table (`BUSA_DOC_MSTR`) discriminated by `DOC_TYPE`.
**Feature spec:** [../../features/addon-service/sales-e-invoices.md](../../../features/addon-service/sales-e-invoices.md)
**Status:** Schema is in place column-for-column with DEV. Only seed migrations remain (View Setting columns + optional demo data).

JEX is **read-only reference**. Local schema changes go through Flyway only — `spring.jpa.hibernate.ddl-auto = validate`, never `update` / `create`.

> **Phase 2 implementation rules are locked.** See planning § Phase 2 implementation rules (locked decisions) _(removed — historical `planning/` folder deleted by project decision)_. The DB-level rules: IR-1 (`SALES → 'I'`, `EXPENSE → 'B'`), IR-2 (always-on baseline filters in service queries), IR-4 (no port of `wb_get_invoice_status` in Phase 2), IR-5 (no port of `wb_get_translate_text` in Phase 2 — search uses plain `LOWER(...) ILIKE`), IR-6 (sibling unpaged aggregation query for `TOTALS[]`), IR-9 (View Setting **seed required**, but management UI is out of scope — page won't generate Save / Reset traffic). The `BUSA_DOC_MSTR` schema is column-for-column DEV; only the Sales-side View Setting seed migration is required for Phase 2.

---

## Legacy tables (JEX)

The Sales e-Invoices listing is a **read-only view** over the legacy invoice master tables in `wecmsvndev`. The list page does NOT touch the line-item / fee / tax detail tables — those belong to the per-invoice detail page (separate sprint).

| Legacy table | Used by Sales list | Notes |
|---|---|---|
| `BUSA_DOC_MSTR` | ✅ — primary read source | Master record for every invoice (sales / expense / other). Discriminated by `DOC_TYPE`. Multi-tenant via `BIZ_KEY`. PROD has 100 columns; the Sprint 1 entity maps ~16. |
| `BUSA_DOC_MSTR_HIS` | ❌ (history table) | Audit / change-history snapshot. Out of refactor scope — would only be needed for a per-invoice change-log view. |
| `BUSA_DOC_DETL` | ❌ (line items) | Per-invoice line-item detail. Used by the **detail page**, not the listing. |
| `BUSA_DOC_DETL_HIS` | ❌ (history) | Detail-side history. Out of scope. |
| `EINV_TAX_DETL` | ❌ (tax breakdown) | Tax-rate breakdown lines. Used by detail page. |
| `EINV_FEE_DETL` | ❌ (fee breakdown) | Fee detail lines. Used by detail page. |
| `EINV_TAX_DETL_HIS`, `EINV_FEE_DETL_HIS` | ❌ (history) | History of the above. Out of scope. |
| `BUSA_DOC_RCV_PAY_HIS` | ❌ (bank-matching) | Receipt/payment match history. Used by the bank-matching workflow, not by this listing. |
| `BANK_INFM`, `TRCO_INFM` | (read-only lookups, optional) | If the list page later renders the resolved bank name / counterparty nickname server-side, these are lookups. Today the list reads `TRCO_CMNM` / `TRCO_TAX_ID` directly off `BUSA_DOC_MSTR` — no JOIN needed. |

All legacy tables above are in the [PROD Active Table List](../../../../CLAUDE.md). The refactor enforcement rule says: refactor ONLY tables that appear in the active list.

**Legacy IDOs found** (`WECMS DEV/WECMS_ETC/xml/service/IDO/`):

- `IDO.EINV_TAX_DETL_R001`, `_C001`, `_U001`, `_D001` — default CRUD set on the tax detail
- `IDO.EINV_FEE_DETL_R001`, `_C001`, `_U001`, `_D001` — default CRUD set on the fee detail
- `IDO.BUSA_DOC_MSTR_*` — **not present** under the `EINV_*` namespace; the list query in `einv_0001_001_r001_act.jsp` is a custom IDO (not yet inspected). The refactor does NOT need to mirror the legacy IDO — JPA + Spring Data is the path per [wecms-api/CLAUDE.md](../../../../CLAUDE.md).

---

## Refactor tables / entities

### `BUSA_DOC_MSTR` — primary read source

**Local Flyway state:**

- **V15** (Sprint 1) — initial table create with the Sprint 1 column subset (~20 columns). Documented as a deliberate subset.
- **V17** — initial seed (synthetic, later replaced).
- **V22** — replaced V17 with real DEV rows for `BIZ_KEY = VN_VNM0004_000001` (max 30 rows per the Data Migration Rule).
- **V23** — corrective migration adding the **80 missing legacy columns** (additive only, `ALTER TABLE ADD COLUMN IF NOT EXISTS`). Brings local schema to **100 columns column-for-column with DEV** per the [Legacy Schema Parity Rule](../../../../CLAUDE.md).
- **V24** — added `BUSA_DOC_MSTR_UK01` UNIQUE constraint + 11 legacy CHECK constraints + the FKs that target migrated tables.

**JPA entity:** [`BusaDocMstr`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) — currently maps **16 columns**. The class Javadoc explicitly notes "Legacy carries 100 columns; Sprint 1 maps only ~16. Add columns here AND in V15/V23 (ALTER TABLE ADD COLUMN IF NOT EXISTS) when a feature needs them — do not silently widen the entity." This is the canonical pattern: schema = full DEV parity; entity = feature-scoped subset.

**Composite primary key:** `(BIZ_KEY, DOC_ID)` via `@IdClass(BusaDocMstrId.class)`. Mirrors the legacy PK.

**No new tables required for the listing page.** Detail-level joins (`EINV_TAX_DETL`, `EINV_FEE_DETL`, `BUSA_DOC_DETL`) are out of scope for this feature.

### View Setting tables (shared infrastructure — already exists)

- `SYS_SCR_OPTION_WECMS` — system defaults per `(WEB_SVC_ID, FIELD_ID)`. Created in V30; iterated through V42.
- `USER_SCR_OPTION_WECMS` — per-user overrides per `(BIZ_KEY, USER_ID, WEB_SVC_ID, FIELD_ID)`. Created in V30.

**No schema change needed for Sales e-Invoices** — adopting View Setting on this page is **seed-only**. New rows in `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-sales-einvoices'` land in a feature migration (number TBD at write-time).

**View Setting management UI is out of scope for this page (IR-9)** — the seed populates column metadata that the frontend reads via `useViewSetting(...)`, but the Sales e-Invoices page does not render the popover and does not expose any UI to toggle / reorder / reset columns. Save (`PUT`) and Reset (`DELETE`) traffic against `/api/v1/screen-options/...` is not generated by this page; only the lazy-init GET path runs (per the existing global behaviour). The seed declares the column set; the column set is treated as fixed for end users.

---

## Key columns (`BUSA_DOC_MSTR`, refactor-mapped subset)

| Column | Type | Null | Today on entity | Maps to wire field | Notes |
|---|---|---|---|---|---|
| `BIZ_KEY` | `VARCHAR(20)` | NN | ✅ `bizKey` (PK part 1) | (not echoed) | Tenant key. Sourced from `TenantContext.currentBizKey()` (JWT), never request input. |
| `DOC_ID` | `VARCHAR(50)` | NN | ✅ `docId` (PK part 2) | `DOC_ID` | Internal document id (legacy `wb_get_next_val('busa_doc_mstr','doc_id',…)`). |
| `DOC_TYPE` | `VARCHAR(1)` | NN | ✅ `docType` | (not echoed) | **Sales/Bill discriminator** (verified against [`einv_0001_001_r001_act.jsp:268-284`](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/einv/einv_0001_001_r001_act.jsp) and [`BusaDocType.java`](../../../../../WECMS%20DEV/WECMS/src/wabooks/enums/BusaDocType.java)). `'I'` = `INVOICE` (Sales), `'B'` = `BILL` (Expenses). `'E'` (`EXPENSE`) and `'S'` (`PAYROLLBOOK`) are rejected by the listing service. The refactor wire maps `SALES → 'I'`, `EXPENSE → 'B'` (the current `EInvoiceService.resolveDocType` mapping `EXPENSE → 'E'` is a bug). |
| `INV_TYPE` | `VARCHAR(1)` | nullable | ✅ `invType` | `INV_TYPE` (legacy WSVC drops it) | **Commercial-vs-Tax classifier** (verified against [`IDO.BUSA_DOC_MSTR_R022.xml:200`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml)). `'C'` → `Commercial`, anything else → `Tax`. **NOT** `Original`/`Replacement`/`Adjustment` — those are values of `EINV_INVOICESTATUS`. The legacy WSVC `<out>` does not expose `INV_TYPE` and the legacy TS does not render it. The static UI mock's `invoiceType: "Original"` is misnamed — recommend dropping `INV_TYPE` from the refactor wire and surfacing the status sub-line from `EINV_INVOICESTATUS`. |
| `ISSU_DT` | `VARCHAR(8)` | NN | ✅ `issuDt` | `ISSU_DT` | Issue date (`YYYYMMDD`). Used as the primary sort + the `from`/`to` filter target. |
| `ISSU_TAX_ID` | `VARCHAR(40)` | NN | ✅ `issuTaxId` | (not echoed today) | Issuer's MST. The seller's tax code (the company itself for a Sales invoice). |
| `TRCO_KEY` | `VARCHAR(20)` | NN | ✅ `trcoKey` | (not echoed today) | Counterparty internal key — joins to `TRCO_INFM`. |
| `TRCO_TAX_ID` | `VARCHAR(40)` | nullable | ✅ `trcoTaxId` | `TRCO_TAX_ID` | **Buyer's MST.** Rendered as the `MST : {code}` sub-line on the Buyer column. |
| `TRCO_CMNM` | `VARCHAR(200)` | nullable | ✅ `trcoCmnm` | `TRCO_CMNM` | Buyer's company name (Vietnamese / native). |
| `TRCO_CMENM` | `VARCHAR(200)` | nullable | ✅ `trcoCmenm` | (fallback to `TRCO_CMNM`) | Buyer's company name (English). Service falls back to this when `TRCO_CMNM` is null. |
| `TRCO_TRSC_CCY_CD` | `VARCHAR(3)` | nullable | ✅ `trcoTrscCcyCd` | `TRCO_TRSC_CCY_CD` | Invoice currency (`VND` / `USD` / `KRW` / `EUR` / …). Drives the per-currency footer totals. |
| `TOTL_AMT` | `NUMERIC(20,6)` | nullable | ✅ `totlAmt` | `TOTL_AMT` | Total amount (gross). Service rounds to 2 dp + strips trailing zeros for the wire string. |
| `SPLY_AMT_SUM` | `NUMERIC(20,6)` | nullable | ✅ `splyAmtSum` | `SPLY_AMT_SUM` | Amount before tax (sum of supply prices across line items). |
| `TAX_AMT_SUM` | `NUMERIC(20,6)` | nullable | ✅ `taxAmtSum` | `TAX_AMT_SUM` | Tax amount (sum across line items). |
| `EINV_INVOICESTATUS` | `VARCHAR(100)` | nullable | ✅ `einvInvoicestatus` | `EINV_INVOICESTATUS` | **Localised invoice status string.** Six logical states (`Original` / `Replacement` / `Adjustment` / `Replaced` / `Adjusted` / `Cancelled`) — legacy IDO resolves the stored value through `wb_get_invoice_status(value, LANG_CD)` so the displayed string is per-locale (vi / en / ko). **`wb_get_invoice_status` is NOT in the local Flyway state** — refactor must port the function, replicate regex in Java emitting canonical keys, or replicate regex frontend-side. Length widened to 100 in V18 (legacy WSVC declares 500 — verify with PO if any DEV rows exceed 100 chars). |
| `EINV_INVOICESYMBOL` | `VARCHAR(10)` | nullable | ✅ `einvInvoicesymbol` | `EINV_INVOICESYMBOL` | The displayed e-invoice serial (e.g. `1C24TLM-0405`). V17 widened to VARCHAR(50) for static-UI compat — confirm the entity reflects the right length. **Open: entity says length 10, V17 widened to 50, V23 may have widened further. Audit before relying on it.** |
| `REG_ID` | `VARCHAR(100)` | NN | ✅ `regId` (audit) | (not echoed) | User who registered the row. |
| `REG_DTM` | `VARCHAR(14)` | NN | ✅ `regDtm` (audit) | `ISSU_TM` (substring) | Registration timestamp `YYYYMMDDhhmmss`. Service slices `[8..14]` for the `ISSU_TM` wire field — proxy for "issue time" since legacy `ISSU_TM` field doesn't exist on `BUSA_DOC_MSTR`. |
| `MOD_ID` / `MOD_DTM` | `VARCHAR(100)` / `VARCHAR(14)` | NN | ✅ `modId` / `modDtm` (audit) | (not echoed) | Last modifier / modification time. |

### Columns NOT yet mapped (Phase 2 task #4 priorities)

The legacy `WSVC.einv_0001_001_r001.xml` `REC` carries 41 fields. The refactor entity maps 16. Missing high-value columns to add (DB schema already has them via V23 — only the entity / DTO / mapper need updating):

| Column | Type (DEV) | Wire field | Why it matters |
|---|---|---|---|
| `COMPANYSTATUS` | `VARCHAR(255)` | `COMPANYSTATUS` | Buyer's MST status from GDT (joined from `OPRT_STTS_INFO`) — compliance flag rendered next to the MST code |
| `EINV_INVOICESTATUS_BEF` | `VARCHAR(100)` | `EINV_INVOICESTATUS_BEF` | Prior invoice status — useful for tooltip / detail diff |
| `READ_YN` | `VARCHAR(1)` | `READ_YN` | Has the user opened/read this invoice — drives bold/unread styling. Note: legacy IDO computes this on the fly as `CASE WHEN TODAY = REG_DATE THEN 'Y' ELSE 'N' END`, NOT a stored column |
| `OVER_DUE_DAYS` | `NUMERIC(5)` | `OVER_DUE_DAYS` | Days past due — legacy IDO computes via `CURRENT_DATE - ISSU_DT - TRCO_PAYM_TERM` when `PROV_STTS NOT IN ('PA','PP','OD')` AND `NOTE IS NULL`; otherwise uses the stored value |
| `DUE_DT` | `VARCHAR(8)` | `DUE_DT` | Due date — paired with `OVER_DUE_DAYS` |
| `NOTE` | `VARCHAR(255)` | `NOTE` | Receipt/payment summary (legacy IDO builds via `json_agg(...)` over `BUSA_DOC_RCV_PAY_HIS` rows) |
| `PROV_STTS` | `VARCHAR(2)` | `PROV_STTS` | **Local payment / settlement status** — fixed 6-value enum verified against [`ProvStts.java`](../../../../../WECMS%20DEV/WECMS/src/wabooks/enums/ProvStts.java): `OP` (open), `PP` (partially paid), `PA` (paid), `OD` (overdue), `VD` (cancelled — always hidden), `EE` (draft — always hidden). Listing baseline filters out `VD` + `EE`. |
| `RCV_SND_YN` | `VARCHAR(1)` | `RCV_SND_YN` | Whether the invoice has any receipt/payment record. Legacy IDO computes via `CASE WHEN COUNT(*) FROM BUSA_DOC_RCV_PAY_HIS > 0 THEN 'Y' ELSE 'N' END` |
| `HAND_REG_YN` | `VARCHAR(1)` | `HAND_REG_YN` | Hand-registered (manually entered) vs synced from GDT. **Listing baseline filter requires `HAND_REG_YN = 'N'`** — hand-registered rows are always hidden. |
| `DISP_DOC_ID` | `VARCHAR(50)` | `DISP_DOC_ID` | Display-friendly document id (vs internal `DOC_ID`) |
| `RCV_PAY_NOTE` | `VARCHAR(5000)` | `RCV_PAY_NOTE` | Free-text receipt/payment note — legacy IDO formats the latest receipt/payment as `Transfer (BANK ACCT) DATE` / `Cash DATE` / etc. |

**Excluded from the Phase 2 priority list (clarification):**

- **`COLOR`** (`VARCHAR(5)`) — NOT a status badge field. Resolved by `wb_get_memo_color(...)` in the IDO; default value is the CSS class `"white"`. Tied to a separate per-document memo sticker UI element. Out of listing-page scope until memo functionality is in scope.
- **`MEMO`** (`VARCHAR(200)`) — paired with `COLOR`, resolved by `wb_get_memo_cont(...)`. Same reasoning — out of listing-page scope.
- **`INV_TYPE`** (`VARCHAR(1)`) — Commercial-vs-Tax classifier (`'C'` = Commercial, otherwise = Tax). Legacy WSVC drops it; the legacy TS doesn't render it. Recommend NOT exposing on the refactor wire either.

Adding these to the entity is a Phase 2 backend task — DB schema is already DEV-parity per V23, so no migration is needed.

---

## Invoice status column

| Aspect | Value |
|---|---|
| Column | `BUSA_DOC_MSTR.EINV_INVOICESTATUS` |
| Type | `VARCHAR(100)` (legacy WSVC declares length 500 but the local Flyway / entity is 100) |
| Source | Six logical states resolved server-side via `wb_get_invoice_status(stored_value, LANG_CD)` in [`IDO.BUSA_DOC_MSTR_R022.xml:238`](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.BUSA_DOC_MSTR_R022.xml). Underlying stored value comes from the GDT bridge. |
| Logical values | `Original`, `Replacement`, `Adjustment`, `Replaced`, `Adjusted`, `Cancelled` — each surfaces in the displayed locale (vi / en / ko) per the user's `LANG_CD`. |
| Refactor handling | Pick a Phase 2 strategy: (a) port `wb_get_invoice_status` to local Flyway for legacy parity, (b) replicate regex matching in Java emitting a canonical key (`ORIGINAL` / `REPLACEMENT` / `ADJUSTMENT` / `REPLACED` / `ADJUSTED` / `CANCELLED`), or (c) replicate regex in the frontend. **Status badge tint comes from regex matching, NOT from the `COLOR` field.** |
| Filter wire field | `invoiceStatus` query param (legacy `einv_invoiceStatus`) — exact match `WHERE EINV_INVOICESTATUS = ?`. The legacy frontend always sends the Vietnamese form. |
| Sort wire `FIELD_ID` | `STATUS` → entity property `einvInvoicestatus` (planned `EInvoiceSortField` enum) |

**Localisation parity is an implementation blocker.** The local Flyway state does NOT contain `wb_get_invoice_status` — the refactor `EInvoiceService` returns whatever raw value `BUSA_DOC_MSTR.EINV_INVOICESTATUS` holds. Without one of the three strategies above, the wire string will vary by stored locale, the filter (exact match) will silently miss rows, and the badge tint will fail to apply consistently.

---

## Date / time columns

The static UI's "Date / Time" column composes two stacked spans (date on top, time below). The wire today splits the value across two fields, but **only `ISSU_DT` has legacy parity**:

| Wire field | Source on `BUSA_DOC_MSTR` | Format | Legacy parity? |
|---|---|---|---|
| `ISSU_DT` | `BUSA_DOC_MSTR.ISSU_DT` (`VARCHAR(8)`) | Legacy `YYYYMMDD`. Used as the primary sort + the `from` / `to` filter target. | ✅ Exposed by legacy WSVC. |
| `ISSU_TM` | substring of `BUSA_DOC_MSTR.REG_DTM[8..14]` (`VARCHAR(14)`, full `YYYYMMDDhhmmss`) | Legacy `HHmmss` shape. Service-side (`EInvoiceService#toRow`) slices `regDtm.substring(8, 14)` when length ≥ 14, else empty string. **Caveat — this is the row's _registration_ time, NOT the e-invoice's actual issue time.** | ❌ **NOT in legacy.** The WSVC `<out>` does NOT declare `ISSU_TM`; the IDO does NOT compute it; the legacy TS does NOT render it. The refactor's value here is a refactor-only invention. |

**Date-range filter:** the `from` / `to` query params are matched against `ISSU_DT` (NOT `REG_DTM`). The service uses inclusive bounds via `BETWEEN`, mirroring the legacy WHERE clause semantics in `einv_0001_001_r001_act.jsp`. **Both `from` and `to` are required** by the legacy `validateInput` (`ValidationHelper.validateRequired`) — Spring should mirror this.

**Sort:** the default sort is `ISSU_DT DESC, REG_DTM DESC` (newest first). The frontend `DATE_TIME` `FIELD_ID` toggles only the `ISSU_DT` direction; `REG_DTM` always rides as a tiebreaker (per the [§ Common table sort](../../../../CLAUDE.md) "stable secondary key" rule).

**Policy decision needed for `ISSU_TM`** (verified — legacy doesn't expose this):

1. Drop the time row entirely from the UI — render date-only (legacy parity).
2. Keep the proxy and label it "Registered at" instead of "Issued at" so the semantic is honest.
3. Source from another `BUSA_DOC_MSTR` column if PO identifies one (no obvious candidate today).

Either way, do NOT call this "legacy parity" in the wire contract — it's a refactor-specific addition.

---

## Tenant filtering — `BIZ_KEY`

Every read on `BUSA_DOC_MSTR` MUST include `BIZ_KEY = ?` in the `WHERE` clause. `BIZ_KEY` comes from `TenantContext.currentBizKey()` (populated by `JwtAuthenticationFilter` from the validated JWT), never from request input.

**Hard rules** (enforced in code review):

- `BusaDocMstrRepository` finder methods MUST take `bizKey` as the first argument.
- Service-side: `final String bizKey = TenantContext.currentBizKey();` (sourced from the validated JWT claim — never hardcoded).
- `BIZ_KEY` is part of the **composite primary key** (`@Id` on `bizKey` and `docId` in `BusaDocMstr`), so a JPA `findById(...)` MUST construct a `BusaDocMstrId(bizKey, docId)` — single-arg lookups by `DOC_ID` alone are not supported and shouldn't be written.
- Tenancy MUST NOT be widened — never `LIKE 'VN_%'`, never `BIZ_KEY IN (...)`, never `BIZ_KEY = '%'`. Only the single concrete `BIZ_KEY` from `TenantContext.currentBizKey()` is ever used.
- The export endpoint (planned) inherits the same scope by going through `EInvoiceService.list(...)` rather than calling the repository directly.
- `BIZ_KEY` is **not echoed** in the wire response — the JWT is the authority and the frontend already has the value in `userStorage`.

---

## Relationships (refactor-relevant)

The Sales e-Invoices listing **does not JOIN** other tables. Every column the listing needs comes from `BUSA_DOC_MSTR` directly. The relationships below exist in the legacy schema and are listed for context — **do NOT add JPA `@ManyToOne` / `@OneToMany` mappings to the `BusaDocMstr` entity for them**; the listing reads denormalised columns.

| Foreign key (legacy) | Target | Used by listing? | Notes |
|---|---|---|---|
| `BUSA_DOC_MSTR.TRCO_KEY` | `TRCO_INFM.TRCO_KEY` | ❌ | The buyer name + MST + status are denormalised onto `BUSA_DOC_MSTR.TRCO_CMNM` / `.TRCO_TAX_ID` / `.COMPANYSTATUS` already. JOIN only needed if the listing later renders fields not yet denormalised (e.g. counterparty nickname). |
| `BUSA_DOC_MSTR.BIZ_KEY` | `BUSI_INFM.BIZ_KEY` | ❌ (tenant) | Tenancy is enforced via the JWT-sourced `bizKey`, not via JPA navigation. |
| `BUSA_DOC_DETL.DOC_ID` | `BUSA_DOC_MSTR.DOC_ID` (1 → many) | ❌ | Line items — used by the **detail page**, not the listing. |
| `EINV_TAX_DETL.DOC_ID` | `BUSA_DOC_MSTR.DOC_ID` (1 → many) | ❌ | Tax breakdown — detail page only. |
| `EINV_FEE_DETL.DOC_ID` | `BUSA_DOC_MSTR.DOC_ID` (1 → many) | ❌ | Fee breakdown — detail page only. |
| `BUSA_DOC_RCV_PAY_HIS.DOC_ID` | `BUSA_DOC_MSTR.DOC_ID` (1 → many) | ❌ | Bank-matching history — separate feature. |

The legacy `wb_get_next_val` audit (2026-04-29) lists `BUSA_DOC_MSTR.DOC_ID` as a generated-key branch. Sprint 1 endpoints are read-only — no Sales e-Invoice insert path exists in the refactor. When (and if) writes land, the generator MUST be implemented in a Java `@Service` per the [DB Function / Legacy Function Policy](../../../../CLAUDE.md), not via a new local `wb_get_next_val` branch.

---

## View Setting seed (planned migration)

A new Flyway migration (number TBD — next free slot) seeds `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-sales-einvoices'`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`.

```sql
-- Pseudo-SQL — confirm field types against V40 / V41 / V42 before writing
INSERT INTO SYS_SCR_OPTION_WECMS (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('addon-sales-einvoices', 'default', 'CHECKBOX',   'Checkbox',    0,  'Y', 'Y', 'N', 'N', '38px',  'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'DATE_TIME',  'Date/Time',   1,  'Y', 'N', 'Y', 'Y', '120px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'INVOICE_NO', 'Invoice No.', 2,  'Y', 'N', 'Y', 'Y', '160px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'BUYER',      'Buyer',       3,  'Y', 'N', 'Y', 'Y', '320px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'CURRENCY',   'Currency',    4,  'Y', 'N', 'Y', 'Y', '80px',  'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'TOTAL',      'Total',       5,  'Y', 'N', 'Y', 'Y', '140px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'STATUS',     'Status',      6,  'Y', 'N', 'Y', 'Y', '120px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'TAX',        'Tax',         7,  'Y', 'N', 'Y', 'Y', '140px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-sales-einvoices', 'default', 'PRETAX',     'Pre-tax',     8,  'Y', 'N', 'Y', 'Y', '140px', 'system', '20260504000000', 'system', '20260504000000')
ON CONFLICT (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID) DO NOTHING;
```

**Notes:**

- Width values shown above are placeholders — replace with **Figma WB365** widths from the Sales e-Invoices frame (mirror the V34 pattern from Account Inquiry, where Figma — NOT legacy DEV — is the layout authority).
- `CHECKBOX` (`DISP_ORD=0`, `FIX_YN='Y'`) is the special row required for the row-selection column. It's locked on save / reset by `ScreenOptionService.SPECIAL_FIELD_IDS`. Even though Sales e-Invoices doesn't expose a Save / Reset UI (IR-9), the special row is still seeded for parity with the column-set contract — the lazy-init GET path copies it into `USER_SCR_OPTION_WECMS` on first read.
- **Do NOT seed `ETC`** — Sales e-Invoices has no per-row action column.
- The Expenses sibling (`addon-expenses-einvoices`) gets its own seed in the same migration (or a sibling migration) — column set is identical except `BUYER` is relabelled `SUPPLIER`. Width tokens are identical because the page shell is shared.
- After landing the seed, register `SCREEN_IDS.ADDON_SALES_EINVOICES = "addon-sales-einvoices"` in [`wecms-web/lib/viewSettingScreens.ts`](../../../../../wecms-web/lib/viewSettingScreens.ts) as part of the same MR (frontend-side prerequisite for `useViewSetting(...)` to resolve the screen id).

---

## Open DB questions

1. **`EINV_INVOICESYMBOL` length.** Entity says length 10, V17 widened the column to VARCHAR(50) for static-UI compat, V23 brought everything to DEV parity. Confirm the entity reflects the right length — any DEV row > 10 chars would fail to load today. *Owner: Arthur.*

2. **`EINV_INVOICESTATUS` length cap.** Legacy WSVC declares length 500; the entity is `VARCHAR(100)`. If any DEV row has a status > 100 chars, the entity load fails. Audit and decide whether to widen or trust 100. *Owner: Arthur.*

3. **`wb_get_invoice_status` parity.** Legacy IDO uses this DB function to localise `EINV_INVOICESTATUS` per `LANG_CD`. The function is NOT in local Flyway. Pick: port the function, replicate regex in Java, or replicate regex in the frontend. Affects filter, sort, and badge tint behaviour. *Owner: Sonny + Arthur.*

4. **`wb_get_translate_text` parity.** Legacy `SearchHelper` uses this function for accent + case folding (Vietnamese-aware) on most search predicates. Not in local Flyway. Pick: port the function, replicate folding in Java with `Collator` / `Normalizer`, or accept divergence. *Owner: Arthur.*

5. **`wb_get_memo_color` / `wb_get_memo_cont` parity.** Drive the `COLOR` and `MEMO` fields. Out of listing-page scope; defer until memo functionality is in scope. Document the deferral. *Owner: Arthur.*

6. **Per-status totals.** The legacy WSVC returns `TOT_OP_INV_*` / `TOT_OD_INV_*` / `TOT_PA_INV_*` / `TOT_INV_*`. Are these driven off `EINV_INVOICESTATUS` values? Off `PROV_STTS`? Off something else? Confirm before designing the aggregation query. *Owner: PO + Arthur.*

7. **`ISSU_TM` policy.** Legacy doesn't expose any issue-time field. Pick: drop the time row from the UI, label it "Registered at" instead of "Issued at", or source from a different column if PO identifies one. *Owner: PO.*

8. **View Setting widths.** Pull the Figma WB365 widths for Sales e-Invoices (Figma node 9194:6696 region) and use them in the seed migration's `SIZE` column. Mirror the V34 pattern. *Owner: Designer + Arthur.*

9. **Column lengths column-by-column.** Run an automated comparison between the local Flyway state and DEV `information_schema.columns` for `BUSA_DOC_MSTR` before adding the next batch of entity-mapped columns — V23 brought columns over but length / scale drift is possible. *Owner: Arthur.*

10. **GDT-side fields beyond `EINV_INVOICESTATUS`.** Legacy returns `GDT_CERT_SEQ` at the top level. The refactor R&D scope excludes the GDT bridge — but does `BUSA_DOC_MSTR.GDT_CERT_SEQ` (or equivalent) hold the last-known value, or is it always pulled live? Read-only consumption is fine; live pull is out of scope. *Owner: Arthur.*

**Resolved (no longer open) — verified against legacy code:**

- ✅ `DOC_TYPE` for Sales: `'I'` (not `'S'`). For Bill/Expense: `'B'` (not `'E'`). Verified against `einv_0001_001_r001_act.jsp` and `BusaDocType.java`.
- ✅ `INV_TYPE` value-set: single-char `'C'` = Commercial, otherwise = Tax. Verified in `IDO.BUSA_DOC_MSTR_R022.xml:200`. NOT Original/Replacement/Adjustment.
- ✅ `PROV_STTS` vocabulary: 6 fixed codes (`OP`/`PP`/`PA`/`OD`/`VD`/`EE`). Verified in `ProvStts.java`. Listing baseline excludes `VD` + `EE`.
- ✅ `ISSU_TM` legacy presence: NOT a legacy field. Refactor's `REG_DTM[8..14]` slice is a refactor-only proxy.
- ✅ `COLOR` semantics: per-doc memo sticker CSS class (default `"white"`), NOT a status badge tint.

---

## Cross-references

- Feature spec: [../../features/addon-service/sales-e-invoices.md](../../../features/addon-service/sales-e-invoices.md)
- Frontend page: [../../frontend/addon/sales-e-invoices-page.md](../../../../../wecms-web/docs/features/addon-service/sales-e-invoices.md)
- Backend API: [../../backend/addon/sales-e-invoices-api.md](../../api-spec/addon/sales-e-invoices-api.md)
- Migration mapping: [../../migration/service-mapping.md](../../migration/service-mapping.md)
- Implementation plan: ../../planning/addon/sales-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- DB parity audit (Sprint 1): [../../audits/db-parity-audit-2026-04-30.md](../../audits/db-parity-audit-2026-04-30.md)
- Schema parity rules: [wecms-api/CLAUDE.md → Legacy Schema Parity Rule](../../../../CLAUDE.md)
- PROD active table list: [wecms-api/CLAUDE.md → PROD DB Table Scope for Refactoring](../../../../CLAUDE.md)
- Local Flyway migration directory: [`wecms-api/src/main/resources/db/migration/`](../../../../src/main/resources/db/migration/)
