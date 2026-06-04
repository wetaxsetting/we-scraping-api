# Sales e-Invoices — Backend Test Scenarios

Tests cover the shared `com.kosign.wecms.domain.einvoice` services — they exercise both Sales (`DOC_TYPE='I'`) and Expenses (`DOC_TYPE='B'`) paths through the same code.

## 1. Existing automated tests

| File | Covers |
|---|---|
| [EInvoiceServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceServiceTest.java) | `resolveDocType` mapping (`SALES→'I'`, `EXPENSE/BILL→'B'`, `E`/`S`→`WBE2002`); `validateDateRange` (both required, `from ≤ to`); pagination clamp (`size<=0`→10, `>100`→100); sort fallback on unknown `sortField`; multi-currency totals from `aggregateByCurrency`. |
| [EInvoiceScrapeServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceScrapeServiceTest.java) | Phase-1 upsert by `(BIZ_KEY, DOC_ID)`; legacy `CHECK_RESULT_BRANCH_A/B` + `VOID_STATUS_BRANCH_A/B` derivation; saved/updated/skipped counts; `EXT_TRAN_CALL_HIS` audit row. |
| [EInvoiceTrcoAutoRegistrarTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/service/EInvoiceTrcoAutoRegistrarTest.java) | Counterparty resolution (SALES=buyer / EXPENSE=seller); TRCO reuse vs create; `ETC_TRCO_KEY` fallback when no tax id; `TRCO_ACTV_HSTR` row written with correct `DV_CD`/`TASK_CD`. |
| [EInvoiceExportProviderTest.java](../../../../src/test/java/com/kosign/wecms/domain/einvoice/export/EInvoiceExportProviderTest.java) | FIELD_ID map (`DATE_TIME` composite, `BUYER` = name+tax, raw `STATUS`, raw-decimal amounts); per-currency subheader composition. |

Run: `./gradlew test --tests "*EInvoice*"`.

## 2. Coverage gaps (recommended additions)

| Area | Suggested scenario |
|---|---|
| `list` filters | baseline `PROV_STTS NOT IN ('VD','EE')` + `HAND_REG_YN <> 'Y'` always applied; keyword OR across the 5 columns with `!`-escape; date range enforced. |
| `detail-history` | `type=SALES` returns rows matching `MOD_DTM`; `type=EXPENSE` → `WBE2002`; sort whitelist fallback. |
| `history` | success/fail filter on `RSLT_CD`; `normalizeErrCont` extracts `Output.ErrorMessage`. |
| phase-2 scrape | master enrichment; detail tables deleted + rewritten; auto-registrar invoked once per invoice. |
| detail export | each invoice expands into `PRODUCT`/`FEE`/`TAX` rows; empty detail → header row only. |
| tenant isolation | every method scoped to `currentBizKey()`; cross-tenant `DOC_ID` never read/written. |

## 3. Manual / integration scenarios

1. **List + filter + sort + paginate** — `GET /einvoices?type=SALES&from=20260101&to=20260131&keyword=ABC&sortField=TOTAL&sortDirection=DESC&page=0&size=20`; verify tenant scope, baseline filters, order, page metadata, per-currency totals stable across pages.
2. **List date-range required** — omit `from`/`to` → `WBE_VALIDATION`.
3. **Bad type** — `type=E` → `WBE2002`.
4. **Excel — list** — `POST /einvoices/export` with `screen_id=addon-sales-einvoices`; open the XLSX; verify 8 columns, raw status string, per-currency subheader, filename.
5. **Excel — details** — `POST /einvoices/export-details`; verify each invoice expands into line/fee/tax rows.
6. **Scrape phase-1** — POST `scrape-results`; verify masters upserted, branch-rule status derived, audit row written, counts returned.
7. **Scrape phase-2** — POST `scrape-detail-results` for a phase-1 invoice; verify detail tables rewritten + counterparty auto-registered (`TRCO_INFM` / `TRCO_ACCT_INFO` / `PGRO_BSAC_LNKN_INFM` / `TRCO_ACTV_HSTR`).
8. **Inquiry history** — `GET /einvoices/history?type=SALES&from=…&to=…&status=success`.
9. **Detail history** — `GET /einvoices/detail-history?type=SALES&from=…&to=…&mod_dtm=<batch ts>`; `type=EXPENSE` → `WBE2002`.

> Scrape endpoints accept already-fetched GDT data; no live tax-authority call is made by these tests. The live NXISAS/GDT pull is out of refactor R&D scope.
