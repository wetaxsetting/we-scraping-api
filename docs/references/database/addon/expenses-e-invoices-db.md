# Add-on Service > Expenses e-Invoices — Database Design

**Module:** Add-on Service
**Refactor screen ID (proposed):** `addon-expenses-einvoices`
**JEX counterpart:** `einv_0001_001_r001` with `einvType=2` — `DOC_TYPE='B'` slice of `BUSA_DOC_MSTR`
**Feature spec:** [../../features/addon-service/expenses-e-invoices.md](../../../features/addon-service/expenses-e-invoices.md)
**Sister DB doc (primary — column-by-column reference):** [sales-e-invoices-db.md](sales-e-invoices-db.md)
**Status:** Schema is in place column-for-column with DEV (V15 + V23 parity migrations are shared with Sales — same physical table). Only seed migrations and an Expenses-side data audit remain.

JEX is **read-only reference**. Local schema changes go through Flyway only (`spring.jpa.hibernate.ddl-auto = validate`).

> **Inherits Phase 2 implementation rules from the Sales planning doc** — see Sales § Phase 2 implementation rules (locked decisions) _(removed — historical `planning/` folder deleted by project decision)_. The DB-level rules (IR-1 / IR-2 / IR-4 / IR-5 / IR-6 / IR-9) all apply identically. The single rule that bites Expenses harder than Sales is **IR-1** — `EXPENSE → 'B'` (NOT `'E'`).

---

## Note: same physical table as Sales

The Expenses listing reads the **same table** as Sales — `BUSA_DOC_MSTR` — discriminated by `DOC_TYPE`. There is no separate Expenses table, no view, no shadow copy. Schema parity, entity mapping, View Setting infrastructure — all shared.

This means **no DDL change is required for Expenses**. The only Expenses-specific DB work is:

1. **Audit `BUSA_DOC_MSTR` for `DOC_TYPE='B'` row coverage** under `BIZ_KEY=VN_VNM0004_000001`. The local seed (V22 — copied real DEV rows) was Sales-biased; the Expenses page may render an empty table even after the IR-1 fix lands.
2. **Seed `SYS_SCR_OPTION_WECMS`** for `WEB_SVC_ID = 'addon-expenses-einvoices'` — column-set seed migration only (no schema change).
3. **Optionally add a demo-seed migration** `V{N}__addon_expenses_einvoices_demo_seed.sql` if step 1 reveals sparse coverage. Real DEV rows only, max 30, idempotent.

Everything else — table parity, key columns, audit columns, FK relationships, View Setting tables — is identical to Sales. See [Sales DB § Refactor tables / entities](sales-e-invoices-db.md#refactor-tables--entities) for the full breakdown.

---

## Main table — `BUSA_DOC_MSTR`

Same physical table, same Flyway state (V15 → V17 → V22 → V23 → V24), same JPA entity ([`BusaDocMstr`](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) — 16 columns mapped today out of 100 in DEV).

Composite primary key: `(BIZ_KEY, DOC_ID)` via `@IdClass(BusaDocMstrId.class)`. Mirrors the legacy PK.

**Discriminator value for Expenses: `DOC_TYPE = 'B'`.**

The same Phase 2 priority columns apply — extending the entity / DTO benefits both pages at once. See [Sales DB § Columns NOT yet mapped](sales-e-invoices-db.md#columns-not-yet-mapped-phase-2-task-4-priorities). For Expenses specifically, **`COMPANYSTATUS` is the highest-value addition** — it carries the supplier's GDT MST status, which is the page's primary compliance signal.

---

## Key columns (Expenses-relevant subset)

Identical column set to Sales — see [Sales DB § Key columns](sales-e-invoices-db.md#key-columns-busa_doc_mstr-refactor-mapped-subset) for the full table. Expenses-specific notes per column:

| Column | Expenses delta vs. Sales |
|---|---|
| `DOC_TYPE` | **Always `'B'`** for Expenses rows (NOT `'E'`). The discriminator. |
| `ISSU_DT` | Same — `YYYYMMDD` issue date. Primary sort + `from`/`to` filter target. |
| `TRCO_TAX_ID` | Carries the **supplier's** MST (not the buyer's). Field name is a legacy holdover — `TRCO_*` describes the counterparty regardless of direction. |
| `TRCO_CMNM` / `TRCO_CMENM` | Supplier's company name (vi / en). |
| `COMPANYSTATUS` *(planned wire field)* | **Supplier's GDT MST status.** The compliance flag value `NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST` is the dominant Expenses-side compliance signal — input-VAT credit may be invalid. Phase 2 priority addition (already in the Sales planning doc Phase 2 task #6). |
| `EINV_INVOICESTATUS` | Same six logical states. The stored language may differ between Sales and Expenses rows in DEV — verify during the data-coverage audit (see Open DB questions below). |
| `EINV_INVOICESYMBOL` | Supplier-issued serial. Real shape per the supplier's GDT registration; mock seed uses `2C24SUPP-*` shape for the static UI. |
| `TOTL_AMT` / `SPLY_AMT_SUM` / `TAX_AMT_SUM` | Same numeric semantics. Currency dominated by VND in real Expenses data. |
| `PROV_STTS` | Same 6-value enum. **Listing baseline filter excludes `VD` and `EE`** — same rule as Sales. The OP-expansion (`PROV_STTS=OP` → `IN ('OP','PP','OD')`) applies on both pages. |
| `HAND_REG_YN` | Same. **Listing baseline filter requires `HAND_REG_YN <> 'Y'`** — hand-registered (manually keyed, not synced from GDT) rows are always hidden. |

The audit-column set (`BIZ_KEY`, `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM`) is identical.

`INV_TYPE` (Commercial-vs-Tax classifier — `'C'` → Commercial, otherwise → Tax) — same legacy semantics. Drop from the refactor wire per IR-7 (already on the Sales TODO).

---

## Filters (DB-level — applied by `EInvoiceService.list`)

Per IR-2, every Expenses query MUST apply all four legacy-parity baseline filters before any user-supplied filter is appended:

```sql
WHERE BIZ_KEY = :tenantBizKey
  AND DOC_TYPE = 'B'                         -- per IR-1; NOT 'E'
  AND PROV_STTS NOT IN ('VD', 'EE')          -- always; hides drafts + cancelled
  AND HAND_REG_YN <> 'Y'                     -- always; hides hand-registered rows
  AND EXISTS (
        SELECT 1 FROM BUSA_DOC_DETL d
        WHERE d.DOC_ID = m.DOC_ID
          AND d.BIZ_KEY = m.BIZ_KEY
      )                                       -- always; hides invoices with no line items
  AND ISSU_DT BETWEEN :fromDt AND :toDt      -- required (IR-3)
  -- optional user filters (invoiceStatus, keyword) appended below
```

When `PROV_STTS=OP` is passed explicitly, the predicate expands to `PROV_STTS IN ('OP','PP','OD')`. Other passed `PROV_STTS` values use exact match; blank means no additional filter beyond the baseline.

These four filters are **non-negotiable** for legacy parity. A unit test on `EInvoiceService.list` MUST assert each one is applied for `DOC_TYPE='B'` requests.

---

## Relationships

Same as Sales — **no JOINs** required for the listing page. Every column the listing needs comes from `BUSA_DOC_MSTR` directly. See [Sales DB § Relationships](sales-e-invoices-db.md#relationships-refactor-relevant) for the full FK table.

| Foreign key (legacy) | Used by Expenses listing? | Notes |
|---|---|---|
| `BUSA_DOC_MSTR.TRCO_KEY → TRCO_INFM.TRCO_KEY` | ❌ | Supplier name + MST + status are denormalised on `BUSA_DOC_MSTR` |
| `BUSA_DOC_MSTR.BIZ_KEY → BUSI_INFM.BIZ_KEY` | ❌ (tenant) | Tenancy via JWT, not JPA navigation |
| `BUSA_DOC_DETL.DOC_ID → BUSA_DOC_MSTR.DOC_ID` | ❌ | Line items — used by detail page |
| `EINV_TAX_DETL.DOC_ID → BUSA_DOC_MSTR.DOC_ID` | ❌ | Tax breakdown — detail page only |
| `EINV_FEE_DETL.DOC_ID → BUSA_DOC_MSTR.DOC_ID` | ❌ | Fee breakdown — detail page only |
| `BUSA_DOC_RCV_PAY_HIS.DOC_ID → BUSA_DOC_MSTR.DOC_ID` | ❌ | Bank-matching history (payment-side for Expenses) |

**Same as Sales: no new tables required for the listing page.** `EINV_TAX_DETL` / `EINV_FEE_DETL` exist in local Flyway (V15 ported, V29 seeded) for the future detail page; the listing does not touch them.

---

## Tenant filtering — `BIZ_KEY`

Identical to Sales. `BIZ_KEY` is sourced from `TenantContext.currentBizKey()` (set by `JwtAuthenticationFilter` from the validated JWT), never from request input — so a `%` wildcard tenant filter is impossible. Composite PK `(BIZ_KEY, DOC_ID)`. Repository finders take `bizKey` as the first argument. `BIZ_KEY` not echoed on the wire.

See [Sales DB § Tenant filtering — BIZ_KEY](sales-e-invoices-db.md#tenant-filtering--biz_key) for the full hard-rules list.

---

## View Setting seed (planned migration)

A new Flyway migration (number TBD — next free slot) seeds `SYS_SCR_OPTION_WECMS` for `WEB_SVC_ID = 'addon-expenses-einvoices'`. Idempotent via `INSERT … ON CONFLICT DO NOTHING`. Bundle with the Sales seed in the same migration when possible.

```sql
-- Pseudo-SQL — confirm field types against V40 / V41 / V42 before writing
INSERT INTO SYS_SCR_OPTION_WECMS (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD, USE_YN, FIX_YN, DISP_YN, SORT_YN, SIZE, REG_ID, REG_DTM, MOD_ID, MOD_DTM)
VALUES
    ('addon-expenses-einvoices', 'default', 'CHECKBOX',   'Checkbox',     0,  'Y', 'Y', 'N', 'N', '38px',  'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'DATE_TIME',  'Date/Time',    1,  'Y', 'N', 'Y', 'Y', '120px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'INVOICE_NO', 'Invoice No.',  2,  'Y', 'N', 'Y', 'Y', '160px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'BUYER',      'Supplier',     3,  'Y', 'N', 'Y', 'Y', '320px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'CURRENCY',   'Currency',     4,  'Y', 'N', 'Y', 'Y', '80px',  'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'TOTAL',      'Total',        5,  'Y', 'N', 'Y', 'Y', '140px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'STATUS',     'Status',       6,  'Y', 'N', 'Y', 'Y', '120px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'TAX',        'Tax',          7,  'Y', 'N', 'Y', 'Y', '140px', 'system', '20260504000000', 'system', '20260504000000'),
    ('addon-expenses-einvoices', 'default', 'PRETAX',     'Pre-tax',      8,  'Y', 'N', 'Y', 'Y', '140px', 'system', '20260504000000', 'system', '20260504000000')
ON CONFLICT (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID) DO NOTHING;
```

**Notes:**

- The `BUYER` `FIELD_ID` is reused (not renamed to `SUPPLIER`) — both spellings are technically valid because the seed PK is `(WEB_SVC_ID, FIELD_ID)`. The `FIELD_NM` is set to `Supplier` so any seed-level reader sees the right label. **PO can flip both `FIELD_ID` and `FIELD_NM` to `SUPPLIER`** if clarity is preferred — the underlying entity field stays `TRCO_CMNM` either way. See open question below.
- Width values are placeholders — replace with **Figma WB365** widths from the Expenses frame. The widths should be identical to Sales because the page shell is shared (per CLAUDE.md §7a, the colgroup cannot diverge).
- `CHECKBOX` (`DISP_ORD=0`, `FIX_YN='Y'`) is the special row required for the row-selection column. Locked on save / reset by `ScreenOptionService.SPECIAL_FIELD_IDS`. Even though the popover is not exposed (IR-9), the special row is still seeded so the lazy-init GET path copies it into `USER_SCR_OPTION_WECMS` on first read.
- **Do NOT seed `ETC`** — Expenses has no per-row action column.
- Register `SCREEN_IDS.ADDON_EXPENSES_EINVOICES = "addon-expenses-einvoices"` in [`wecms-web/lib/viewSettingScreens.ts`](../../../../../wecms-web/lib/viewSettingScreens.ts) in the same MR.

---

## Note: `EINV_TAX_DETL` and `EINV_FEE_DETL` not used for listing

The legacy invoice detail tables exist in local Flyway (V15 ported, V29 seeded) and are in the [PROD Active Table List](../../../../CLAUDE.md). They are NOT touched by the Expenses listing page — the listing reads denormalised summary columns (`TOTL_AMT`, `SPLY_AMT_SUM`, `TAX_AMT_SUM`) directly off `BUSA_DOC_MSTR`.

These tables become relevant only for the **per-invoice detail page** (separate sprint), where the Tax tab and Fee tab render line items joined on `(BIZ_KEY, DOC_ID)`. Do NOT add JPA `@OneToMany` mappings for them on the `BusaDocMstr` entity for the listing — adopting them would invite an N+1 query problem.

---

## Open DB questions

1. **Local DEV seed coverage for `DOC_TYPE='B'`.** The Phase 2 IR-1 fix (`EXPENSE → 'B'`) unblocks the wire path, but the local seed under `BIZ_KEY=VN_VNM0004_000001` may carry few or zero `'B'` rows. Stakeholder demos and Figma comparison shots will show an empty screen — visually indistinguishable from a still-broken `DOC_TYPE` mapping. **Audit before the IR-1 fix lands** so post-fix verification has a known-non-empty target. If sparse, copy real DEV rows into a `V{N}__addon_expenses_einvoices_demo_seed.sql` migration (max 30 rows per the [Data Migration Rule](../../../../CLAUDE.md), real data only — never fabricated). *Owner: Arthur.*

2. **Legacy `DOC_TYPE='E'` cleanup.** The Spring code's `EInvoiceService.resolveDocType` maps `EXPENSE → 'E'` (wrong). After the IR-1 fix lands, any existing `'E'` rows in DEV (if any) become invisible to the refactor — but the legacy listing already rejects `'E'`, so they were already invisible to legacy users too. Confirm via:
   ```sql
   SELECT DOC_TYPE, COUNT(*) FROM BUSA_DOC_MSTR WHERE BIZ_KEY = 'VN_VNM0004_000001' GROUP BY DOC_TYPE;
   ```
   If `'E'` rows exist in DEV at all, document the count and the source — they are likely orphans from a different flow (the `BusaDocType.EXPENSE` constant exists but the listing rejects it). No cleanup required on the refactor side; this is a JEX DB hygiene concern. *Owner: Arthur.*

3. **`BUYER` vs `SUPPLIER` `FIELD_ID` for the Expenses seed.** Reusing `BUYER` keeps the seed PK aligned with Sales (no analytics surprise where two screens carry different `FIELD_ID`s for the same entity field). Renaming to `SUPPLIER` is clearer for cross-screen analytics but requires the column registry constant to flip. Decide before the seed migration MR. *Owner: PO + Arthur.*

4. **Expenses-side `EINV_INVOICESTATUS` stored language.** IR-4 returns the raw stored value; the frontend regex maps to canonical key + badge tint. If real DEV `DOC_TYPE='B'` rows store the status in a different language than `DOC_TYPE='I'` rows (e.g. KO for Sales but VI for Expenses, or some Expenses rows in EN), the dropdown filter (which sends the stored Vietnamese form per IR-4) would behave differently across the two pages. Audit:
   ```sql
   SELECT DOC_TYPE, EINV_INVOICESTATUS, COUNT(*)
   FROM BUSA_DOC_MSTR
   WHERE BIZ_KEY = 'VN_VNM0004_000001'
   GROUP BY DOC_TYPE, EINV_INVOICESTATUS
   ORDER BY DOC_TYPE, COUNT(*) DESC;
   ```
   If languages diverge, tighten IR-4 (port `wb_get_invoice_status` to local Flyway, replicate canonical-key emission in Java, or normalise on save). *Owner: Arthur.*

5. **`COMPANYSTATUS` data quality on `DOC_TYPE='B'` rows.** The Expenses page's primary compliance value depends on this field carrying meaningful supplier-MST status text. Audit how often the field is populated:
   ```sql
   SELECT
     COUNT(*) total_rows,
     COUNT(COMPANYSTATUS) populated,
     COUNT(*) FILTER (WHERE COMPANYSTATUS LIKE '%NNT ngừng hoạt động%') suspended
   FROM BUSA_DOC_MSTR
   WHERE BIZ_KEY = 'VN_VNM0004_000001' AND DOC_TYPE = 'B';
   ```
   If systematically NULL, the Expenses page loses its core compliance feature. Decide whether to render `—` placeholder or hide the column for Expenses. *Owner: Arthur.*

6. **Column lengths column-by-column for `DOC_TYPE='B'` rows.** Same `EINV_INVOICESYMBOL` length / `EINV_INVOICESTATUS` 100-vs-500 questions apply on Expenses rows. Run the same column comparison Arthur runs for Sales — see [Sales DB § Open DB questions](sales-e-invoices-db.md#open-db-questions) #1 + #2. *Owner: Arthur.*

**Inherited from Sales (still relevant):**

- `wb_get_invoice_status` parity strategy — IR-4 defers porting; frontend regex handles display.
- `wb_get_translate_text` parity strategy — IR-5 defers porting; backend ships plain `LOWER(...) ILIKE`.
- `wb_get_memo_color` / `wb_get_memo_cont` — out of listing-page scope. `COLOR` and `MEMO` dropped from Phase 2 priority.
- Per-status totals (`TOT_OP_INV_*` etc.) — open question with PO.
- `ISSU_TM` policy — refactor-only proxy from `REG_DTM[8..14]`; PO decides whether to drop / relabel.
- View Setting widths from Figma WB365 — same source for both pages.

**Resolved (no longer open) — verified against legacy code:**

- ✅ `DOC_TYPE='B'` for Expenses (NOT `'E'`). Verified in `einv_0001_001_r001_act.jsp` and `BusaDocType.java`.
- ✅ Same physical table as Sales — `BUSA_DOC_MSTR`. No view, no shadow copy.
- ✅ `PROV_STTS` baseline exclusion (`VD`, `EE`) and `OP` expansion apply identically to both `DOC_TYPE='I'` and `'B'`.
- ✅ `HAND_REG_YN <> 'Y'` and `EXISTS (BUSA_DOC_DETL)` baseline filters apply identically.
- ✅ Audit columns + composite PK `(BIZ_KEY, DOC_ID)` are identical.

---

## Cross-references

- Feature spec: [../../features/addon-service/expenses-e-invoices.md](../../../features/addon-service/expenses-e-invoices.md)
- Sister DB doc (primary — column-by-column reference): [sales-e-invoices-db.md](sales-e-invoices-db.md)
- Frontend page: [../../frontend/addon/expenses-e-invoices-page.md](../../../../../wecms-web/docs/features/addon-service/expenses-e-invoices.md)
- Backend API: [../../backend/addon/expenses-e-invoices-api.md](../../api-spec/addon/expenses-e-invoices-api.md)
- Migration mapping: [../../migration/service-mapping.md](../../migration/service-mapping.md)
- Implementation plan: ../../planning/addon/expenses-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- Sales planning rules (IR-1 through IR-9 inherited): ../../planning/addon/sales-e-invoices-plan.md _(removed — historical `planning/` folder deleted by project decision)_
- DB parity audit (Sprint 1): [../../audits/db-parity-audit-2026-04-30.md](../../audits/db-parity-audit-2026-04-30.md)
- Schema parity rules: [wecms-api/CLAUDE.md → Legacy Schema Parity Rule](../../../../CLAUDE.md)
- PROD active table list: [wecms-api/CLAUDE.md → PROD DB Table Scope for Refactoring](../../../../CLAUDE.md)
- Local Flyway migration directory: [`wecms-api/src/main/resources/db/migration/`](../../../../src/main/resources/db/migration/)
