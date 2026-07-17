# Firm Banking History — Vietnam GDT Inquiry (e-Invoice) parity audit

- **Date:** 2026-07-17
- **System:** Refactor (wecms-api + wecms-web)
- **Screen:** Settings → Firm Banking History (`firm-banking-history`)
- **Legacy source of truth:** JEX `comm_0221_001` (Firm Banking History), `nxisas_logging` + `nxisas_einvoice_c001` (e-Invoice scrape)
- **Reported by:** Sonny · **PO direction:** must match legacy

> **Screen-ID correction.** The reported id `fnsh_0001_001` is actually the JEX
> "Account Inquiry" screen. The real Firm Banking History screen is
> **`comm_0221_001`**. Both refer to the same refactor feature slug
> `firm-banking-history`.

## 1. Symptom

On the refactor (`wecms.kosign.dev`) the Firm Banking History popup with the
**"Vietnam GDT Inquiry"** service-type filter shows **"Firm Banking History Not
Found"** (0 of 0), even for a company/period that clearly scraped e-Invoices
(the addon "e-Invoice Latest Inquiry History" modal shows Sales scrapes on
15–16 Jul 2026).

On legacy PROD (`www.wecms.vn/fnsh_0001_001.act` → Firm Banking History) the
same filter lists **one row per e-Invoice**, e.g.:

| Service Name | Seq. No. | Deposit Bank/Inst. Code | Results |
|---|---|---|---|
| Sale E-Invoice Inquiry | `82ccd178d8cd434a978f3e782e8052cc` | `1C26TWV-255` | Success |
| Sale E-Invoice Inquiry | `68ca2c30faa34670a53539456b0c0195` | `1C26TWV-256` | Success |

## 2. How legacy produces those rows (verified)

### 2.1 Read path — `comm_0221_001`

The grid is IDO `TB_FIRM_TRAN_R002.xml` — a 5-leg `UNION ALL`. The "Vietnam GDT
Inquiry" leg (`SVC_TYPE = 'G'`) reads:

```sql
SELECT NMTLH.REG_DTM                     AS TARN_DTM,
       'G'                               AS SVC_TYPE,
       CASE WHEN NXISAS_TYPE='E-INVOICE_SALES_INQUIRY'
            THEN 'Sale E-Invoice Inquiry' ELSE 'Expense E-Invoice Inquiry' END AS SVC_NM,
       NMTLH.UUID                        AS SEQ_NO,
       NMTLH.NXISAS_MODULE               AS DEP_BANK_CD,
       NMTLH.NXISAS_ERR_CODE             AS RSLT_CD,
       NMTLH.REQ_DATA                    AS REQ_DATA
FROM NXSIAS_MODULE_TRAN_LOG_HIS NMTLH
WHERE NMTLH.NXISAS_TYPE IN ('E-INVOICE_SALES_INQUIRY','E-INVOICE_EXPENSE_INQUIRY')
```

- `Seq. No.` = `NXSIAS_MODULE_TRAN_LOG_HIS.UUID` (32-hex).
- `Deposit Bank/Inst. Code` in SQL = `NXISAS_MODULE` (`'GLOBAL/VNM/PUBLIC_GDT'`),
  **but the client JS overrides the displayed cell** for G rows
  (`comm_0221_001.js:258-260`):
  ```js
  var json = JSON.parse(v.REQ_DATA);
  depositBankCode = `${json.numericSymbol}${json.invoiceSymbol}-${json.invoiceNo}`;
  ```
  i.e. the e-Invoice No. `1C26TWV-255`. The detail popup labels this field
  **"e-Invoice No."**, not a bank code (`comm_0221_001_j012`).

### 2.2 Write path — where the log rows come from

The e-Invoice scrape is **browser-driven** (local NXiSAS engine:
`LOGIN → INVOICE_LIST → INVOICE_DETAIL`). Legacy makes **two** server calls:

1. **`nxisas_logging_act.jsp`** — the browser POSTs the raw engine thread/job/
   result JSON. For each result the action inserts **one
   `NXSIAS_MODULE_TRAN_LOG_HIS` row per job** (per `INVOICE_DETAIL` = per
   invoice) via IDO `NXSIAS_MODULE_TRAN_LOG_HIS_C001`, stamping
   `NXISAS_TYPE = 'E-INVOICE_SALES_INQUIRY' | 'E-INVOICE_EXPENSE_INQUIRY'`
   only for the e-Invoice detail jobs (`nxisas_logging_act.jsp:213-231`,
   `:77-85`). `REQ_DATA` = the engine `Input` JSON
   (`{sellerTaxCode,endDate,numericSymbol,invoiceType,invoiceSymbol,invoiceNo,startDate}`).
2. **`nxisas_einvoice_c001_act.jsp`** — the final save of the decrypted
   invoices to `BUSA_DOC_MSTR` + one `EXT_TRAN_CALL_HIS` audit row. **This
   action does NOT write `NXSIAS_MODULE_TRAN_LOG_HIS`.**

So the Firm Banking History rows come from call **(1)**, not the save action.

Verified `NXSIAS_MODULE_TRAN_LOG_HIS` shape on JEX DEV
(`postgres_dev_server_jexframe`): `NXISAS_CLASS='COMMON'`,
`NXISAS_MODULE='GLOBAL/VNM/PUBLIC_GDT'`, `NXISAS_JOB='INVOICE_DETAIL'`,
`NXISAS_ERR_CODE='00000000'` (or `'80004204'` on a failed detail),
`REQ_DATA={"...","numericSymbol":"1","invoiceSymbol":"C25TWV","invoiceNo":"1279",...}`.

## 3. Root cause of the empty refactor result

The refactor **consolidated** the legacy two-call model into a single server
call `POST /api/v1/addon/einvoices/scrape-results`
(`EInvoiceScrapeService.saveScrapedInvoices`), which writes only
**`BUSA_DOC_MSTR`** + **`EXT_TRAN_CALL_HIS`** (`SVC_ID='GDT_INQUIRY_SALES|EXPENSES'`).
**It never ported the `nxisas_logging` leg for e-Invoices**, so no
`NXSIAS_MODULE_TRAN_LOG_HIS` rows with the `E-INVOICE_*_INQUIRY` types are ever
written.

The refactor Firm Banking History view (`FirmBankingHistoryView.java`) is a
faithful port of the legacy union — its GDT leg reads exactly
`WHERE NXISAS_TYPE IN ('E-INVOICE_SALES_INQUIRY','E-INVOICE_EXPENSE_INQUIRY')`.
With no such rows, the `G` leg is always empty.

Verified in the refactor DB (`postgres_dev_server_refactor`,
`wecmsdevdb.nxsias_module_tran_log_his`): **0** rows with
`NXISAS_TYPE IN (E-INVOICE_*)` and **0** rows for module
`GLOBAL/VNM/PUBLIC_GDT`, despite active `FOREIGN_EXCHANGE`/`LOGIN`/`BALANCE`/
`TRANSACTION`/`PUBLIC_MASOTHUE` scraping in Jun–Jul 2026. The only writer of
`NXSIAS_MODULE_TRAN_LOG_HIS` in the tree is `NxisasAuditLogger.record`, called
exclusively from `NxisasGatewayClient` (server-side bank/card/FX scrapes) with
`NXISAS_TYPE = <NxisasJob name>` — never an e-Invoice type, never exercised by
the e-Invoice flow.

## 4. Parity gaps

| # | Gap | Impact |
|---|---|---|
| **A** | Scrape does not write `NXSIAS_MODULE_TRAN_LOG_HIS` (`E-INVOICE_*_INQUIRY`) rows | GDT leg always empty → "Not Found" (**primary**) |
| **B** | FE table cell "Deposit Bank/Inst. Code" for `G` rows renders raw `dep_bank_cd` (`GLOBAL/VNM/PUBLIC_GDT`) instead of the e-Invoice No. | Even once rows exist, the grid would show the module name, not `1C26TWV-255` (the detail popover already composes it correctly) |
| C (related) | `PUBLIC_MASOTHUE` rows carry `NULL` type/job in the refactor DB | "Vietnam Business Inquiry" (`B` leg) likely also empty — separate follow-up |

## 5. Fix (Option A — legacy-faithful, PO-approved)

### 5.1 Backend — `EInvoiceScrapeService.saveScrapedInvoices`

After the `BUSA_DOC_MSTR` save + `EXT_TRAN_CALL_HIS` audit, write **one
`NXSIAS_MODULE_TRAN_LOG_HIS` row per scraped invoice** (append-only, in the
same tenant transaction), mirroring the legacy `nxisas_logging` INVOICE_DETAIL
rows:

- `UUID` = `UUID.randomUUID()` (32-hex, no dashes) — matches legacy.
- `REG_DTM` = the call `regDtm` (14-char UTC).
- `NXISAS_CLASS='COMMON'`, `NXISAS_MODULE='GLOBAL/VNM/PUBLIC_GDT'`,
  `NXISAS_JOB='INVOICE_DETAIL'`.
- `NXISAS_ERR_CODE='00000000'` (rows reaching the intake are successfully
  detailed).
- `NXISAS_TYPE='E-INVOICE_SALES_INQUIRY'` (Sales) / `'E-INVOICE_EXPENSE_INQUIRY'`
  (Expense/Bill).
- `REQ_DATA` = compact JSON
  `{"sellerTaxCode","endDate","numericSymbol","invoiceType","invoiceSymbol","invoiceNo","startDate"}`
  so the FE can compose the e-Invoice No. exactly as legacy does.
- One row per invoice in `req.invoices()` that has an invoice identity
  (mirrors legacy's per-`INVOICE_DETAIL` logging, independent of the
  accept/skip branch).

No schema change — `NXSIAS_MODULE_TRAN_LOG_HIS` is an existing legacy inventory
table with an existing entity + repository. No Flyway (schema is external).

**Documented fidelity note:** legacy also logs invoice-detail *failures* (engine
`ErrorCode` ≠ `00000000`), which never reach `saveScrapedInvoices` (they produce
no invoice row). The refactor therefore logs the successfully-detailed set only.
A fully faithful port would require the browser to POST the raw per-job engine
results (as legacy's `nxisas_logging` does) — deferred; the successful set is
what the Firm Banking History grid shows in practice.

### 5.2 Frontend — Firm Banking History table cell

For `serviceType === 'vietnam_gdt_inquiry'` rows, render the
"Deposit Bank/Inst. Code" column as the composed e-Invoice No.
(`numericSymbol + invoiceSymbol + '-' + invoiceNo` from `reqData`), matching
legacy `comm_0221_001.js:258-260` and the existing detail-popover logic. Extract
the compose helper so the table cell and the popover share one implementation
(two-call rule). (`vietnam_business_inquiry` → `reqData.search` for the same
reason, keeping the grid/popover consistent.)

### 5.3 Date/Time timezone — UTC storage, VN display (follow-up, 2026-07-17)

All non-`F` legs store `REG_DTM` in **UTC** (`NxisasAuditLogger` /
`EInvoiceScrapeService` / scrape-audit path all write `now(UTC)`), but the
view passed them through raw while the FE treats `tarn_dtm` as VN local
(no shift) — so GDT/business/scraping times displayed 7h behind. (The `F`
leg was already correct via `kr_to_vn_time`.)

Fix: the view now normalizes **every non-F leg** UTC→VN so the FE's
"tarn_dtm is VN, no shift" contract holds for all legs (no double-shift on
F, no FE change):

```sql
CASE WHEN length(REG_DTM) = 14
     THEN to_char((make_timestamp(<Y,M,D,h,m,s from substr>) AT TIME ZONE 'UTC')
                  AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYYMMDDHH24MISS')
     ELSE REG_DTM END
```

Applied to the `G` (GDT), `B` (Business), and both `S` (NAPAS + EXT_TRAN
scraping) legs. `make_timestamp` is session-timezone-independent (verified
against the refactor DB: `20260716104523` UTC → `20260716174523` VN). This
also fixes the date-range filter (`SUBSTRING(tarn_dtm,1,8) BETWEEN …`),
which now compares the user's VN date range against VN dates. Storage stays
UTC — display only. No schema change, no Flyway (read-only `@Subselect`).

## 6. Verification

- `NXSIAS_MODULE_TRAN_LOG_HIS` rows appear after a scrape with the correct
  type/module/job and a `REQ_DATA` carrying the invoice symbol/number.
- Firm Banking History → Vietnam GDT Inquiry lists one row per scraped invoice;
  Seq. No. = UUID; Deposit Bank/Inst. Code = e-Invoice No.; Results = Success.
- `wecms-api` compiles; `wecms-web` type-checks/builds.
