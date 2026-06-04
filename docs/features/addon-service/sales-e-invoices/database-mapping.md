# Sales e-Invoices — Database Mapping

Tables, columns, keys, indexes, and Flyway migrations backing the feature. Deep DB design notes live in [../../../references/database/addon/sales-e-invoices-db.md](../../../references/database/addon/sales-e-invoices-db.md); this doc maps the *implemented* entity ↔ schema. All tables are shared with [Expenses e-Invoices](../expenses-e-invoices/database-mapping.md) — Sales and Expenses are rows in the same tables discriminated by `DOC_TYPE`.

> Per the Legacy Schema Parity Rule the DB schema mirrors DEV column-for-column (`BUSA_DOC_MSTR` is 100 columns); the JPA entity maps only the subset the feature reads/writes. The tables below list the **mapped** columns.

---

## 1. Master table — `BUSA_DOC_MSTR`

Entity: [BusaDocMstr.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstr.java) · PK class [BusaDocMstrId.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocMstrId.java). One row per e-invoice. **PK `(BIZ_KEY, DOC_ID)`** via `@IdClass`.

**List subset (read by `EInvoiceRow`):**

| Column | JPA property | Type | Notes |
|---|---|---|---|
| `BIZ_KEY` | `bizKey` (@Id) | VARCHAR(20) | tenant scope |
| `DOC_ID` | `docId` (@Id) | VARCHAR(50) | internal document id |
| `DOC_TYPE` | `docType` | CHAR/VARCHAR(1), NOT NULL | `'I'` Sales / `'B'` Expense |
| `ISSU_DT` | `issuDt` | VARCHAR(8), NOT NULL | issue date `YYYYMMDD` |
| `TRCO_TAX_ID` | `trcoTaxId` | VARCHAR(40) | counterparty tax code (MST) |
| `TRCO_CMNM` | `trcoCmnm` | VARCHAR(200) | counterparty Vietnamese name |
| `TRCO_TRSC_CCY_CD` | `trcoTrscCcyCd` | VARCHAR(3) | currency code |
| `TOTL_AMT` | `totlAmt` | NUMERIC | total amount (incl. tax) |
| `SPLY_AMT_SUM` | `splyAmtSum` | NUMERIC | supply amount (pre-tax) |
| `TAX_AMT_SUM` | `taxAmtSum` | NUMERIC | VAT |
| `EINV_INVOICESTATUS` | `einvInvoicestatus` | VARCHAR(100) | raw locale status string (IR-4) |
| `EINV_INVOICESTATUS_BEF` | `einvInvoicestatusBef` | VARCHAR(100) | prior status on replace/adjust |
| `EINV_INVOICESYMBOL` | `einvInvoicesymbol` | VARCHAR(10) | invoice serial/symbol |
| `DISP_DOC_ID` | `dispDocId` | VARCHAR(50) | display-friendly id |
| `PROV_STTS` | `provStts` | CHAR(2) | settlement status (`OP`/`PP`/`PA`/`OD`/`VD`/`EE`); `@JdbcTypeCode(CHAR)` |
| `HAND_REG_YN` | `handRegYn` | VARCHAR(1), NOT NULL, default `'Y'` | `'N'` synced / `'Y'` manual |
| `DUE_DT` | `dueDt` | VARCHAR(8) | due date |
| `OVER_DUE_DAYS` | `overDueDays` | VARCHAR(3) | days overdue |
| `REG_DTM` | `regDtm` | VARCHAR(14) | reg timestamp — `issu_tm` proxy sliced from `[8..14]` |

**Identity / write columns used by the scrape pipeline:** `INV_TYPE`, `ISSU_TAX_ID`, `TRCO_KEY`, `TRCO_CMENM`, `TOTL_DIS_AMT`, `BUSA_USAG_CD`, `CONT`, `HAND_DOC_ID`, `USE_DT`, `EINV_INVOICEINFO`, `EINV_CHECKRESULTS`, `EINV_NUMERICSYMBOL`, `EINV_TESTRESULTS` (`@JdbcTypeCode(CHAR)`), `USER_ID`, plus the master-enrichment block written by phase-2 detail intake (`EINV_TITLE`, `EINV_MCCQT`, `EINV_ORIGINAL_INVOICE`, buyer/seller bank + signature + address columns, `EINV_QRCODE`, `EINV_RATE`, issuer address columns) and audit `REG_ID`/`MOD_ID`/`MOD_DTM`. `INV_TYPE` is mapped but **never exposed on the wire** (IR-7 — it is the legacy Commercial/Tax classifier, not the invoice status).

---

## 2. Detail tables (written by phase-2 scrape, read by the detail export)

| Table | Entity | PK | Mapped columns |
|---|---|---|---|
| `BUSA_DOC_DETL` | [BusaDocDetl.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/BusaDocDetl.java) | `(BIZ_KEY, DOC_ID, DISP_ORD)` | `userId, itemCd, itemNm, itemSpec, itemQunt, itemUnitAmt, splyAmt, dcAmt, ccyCd, einvProductname, einvTaxrate` + audit. Unmapped legacy cols: `TAX_AMT`, `RCV_PAY_AMT`, `NOTE`, `TXTN_PTRN`, `ITEM_DETL`, `UOM`, `BEF_DIS_AMT`. |
| `EINV_TAX_DETL` | [EinvTaxDetl.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/EinvTaxDetl.java) | `(BIZ_KEY, DOC_ID, DISP_ORD)` | `einvTaxrate (VARCHAR 50), amt, taxAmt` + audit |
| `EINV_FEE_DETL` | [EinvFeeDetl.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/EinvFeeDetl.java) | `(BIZ_KEY, DOC_ID, DISP_ORD)` | `einvFeename (VARCHAR 100), einvFee` + audit |

---

## 3. Counterparty & audit tables (written by phase-2 / scrape)

| Table | Entity / role | Key columns used |
|---|---|---|
| `TRCO_ACTV_HSTR` | [TrcoActvHstr.java](../../../../src/main/java/com/kosign/wecms/domain/einvoice/entity/TrcoActvHstr.java) — activity log, PK `(BIZ_KEY, ACTV_SEQ)` | `actvSeq` (per-tenant MAX+1), `userId`, `trcoKey`, `trcoCmnm`, `apiApvKey` (dedup = master DOC_ID), `dvCd` (`S`/`B`), `taskCd` (`S003`/`B003`), `ccyCd`, `actvAmt`, `pcsnStts` (`'U'`), `rsltCd` (`'C'`), `trcoCnfmYn` (`'1'`) |
| `EXT_TRAN_CALL_HIS` | `domain.banking.synchistory` — GDT scrape-call audit (written by phase-1, read by inquiry history) | `SVC_ID` (`GDT_INQUIRY_SALES`/`…EXPENSES`), `RSLT_CD`, `ERR_CONT`, `REG_DTM` |
| `TRCO_INFM` / `TRCO_ACCT_INFO` / `PGRO_BSAC_LNKN_INFM` | auto-registered by `EInvoiceTrcoAutoRegistrar` (Contacts-domain tables) | trading-company master + bank accounts + group link (`USER_ID='ALL'`, `GRP_CD` `'S'`/`'B'`) |

All in the PROD Active Table List.

---

## 4. View Setting rows — `SYS_SCR_OPTION_WECMS`

Screen `WEB_SVC_ID = 'addon-sales-einvoices'`, `DYM_AREA_ID = 'default'` (seeded V45, prod-aligned V49):

| FIELD_ID | DISP_ORD | Notes |
|---|---|---|
| `CHECKBOX` | 0 | row-select special (sys-pinned, hidden in popover) |
| `DATE_TIME` | 1 | |
| `INVOICE_NO` | 2 | |
| `BUYER` | 3 | header localised "Buyer" (Sales) / "Supplier" (Expenses) |
| `CURRENCY` | 4 | |
| `TOTAL` | 5 | |
| `STATUS` | 6 | |
| `TAX` | 7 | |
| `PRETAX` | 8 | |

Each sortable column matches an `EInvoiceSortField` constant; the Excel export `allowedFieldIds()` = the eight non-checkbox FIELD_IDs. The user-facing View Setting popover is **not rendered** on this page (IR-9) — the seed feeds column metadata + Excel column intersection only.

---

## 5. Flyway migrations

| Version | Summary |
|---|---|
| V15 | Sprint-1 e-invoice schema — `BUSA_DOC_MSTR` (column subset), `EINV_FEE_DETL`, `EINV_TAX_DETL`. |
| V23 | Legacy schema parity — +80 columns on `BUSA_DOC_MSTR` (20 → 100), additive. |
| V17 / V22 / V50 / V51 / V53 | Master seed (real-DEV rows, tenant `VN_VNM0004_000001`); V51 replaces the seed; V53 resets the Sales slice. Idempotent. |
| V238 | e-Invoice **detail** schema — `BUSA_DOC_DETL` + detail columns on `EINV_TAX_DETL` / `EINV_FEE_DETL`. |
| V239 | `TRCO_ACTV_HSTR` schema (counterparty activity log for phase-2 auto-registration). |
| V45 | View Setting seed for `addon-sales-einvoices` + `addon-expenses-einvoices`. |
| V49 | View Setting prod alignment for `addon-sales-einvoices`. |
| V52 | `addon-expenses-einvoices` demo seed (`DOC_TYPE='B'` rows). |
| V54 | View Setting prod alignment for `addon-expenses-einvoices`. |

> Version numbers are authoritative as of this writing; confirm against `src/main/resources/db/migration/` before relying on a specific Vxx — file headers can lag (see the CLAUDE.md "drafted as Vxx" notes).
