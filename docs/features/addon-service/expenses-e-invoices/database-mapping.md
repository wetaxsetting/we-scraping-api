# Expenses e-Invoices — Database Mapping

Expenses uses the **same tables and entities as Sales** — see the canonical [Sales database-mapping.md](../sales-e-invoices/database-mapping.md) for the full column maps (`BUSA_DOC_MSTR`, `BUSA_DOC_DETL`, `EINV_TAX_DETL`, `EINV_FEE_DETL`, `TRCO_ACTV_HSTR`, audit/counterparty tables). Expenses rows are simply those with **`DOC_TYPE = 'B'`**.

---

## Deltas vs Sales

| Aspect | Sales | Expenses |
|---|---|---|
| `BUSA_DOC_MSTR.DOC_TYPE` | `'I'` | `'B'` |
| Counterparty columns (`TRCO_CMNM`, `TRCO_TAX_ID`) | buyer | supplier |
| `TRCO_ACTV_HSTR.DV_CD` / `TASK_CD` | `S` / `S003` | `B` / `B003` |
| `PGRO_BSAC_LNKN_INFM.GRP_CD` | `S` | `B` |

No schema difference — Sales and Expenses are rows in the same tables.

## View Setting rows — `SYS_SCR_OPTION_WECMS`

Screen `WEB_SVC_ID = 'addon-expenses-einvoices'`, `DYM_AREA_ID = 'default'` — same column set as Sales (`CHECKBOX` · `DATE_TIME` · `INVOICE_NO` · `BUYER` · `CURRENCY` · `TOTAL` · `STATUS` · `TAX` · `PRETAX`). The `BUYER` FIELD_ID is reused (PK is `(WEB_SVC_ID, FIELD_ID)`, no collision); the header text localises to "Supplier". Seeded **V45**, demo-seeded **V52**, prod-aligned **V54**.

> Open question (not a blocker): PO may prefer `SUPPLIER` as the FIELD_ID — see [refactor-gap-analysis §4](refactor-gap-analysis.md#4-open-questions).

## Flyway migrations (Expenses-relevant)

| Version | Summary |
|---|---|
| V15 / V23 | Shared master schema + legacy parity (see Sales). |
| V238 / V239 | Shared detail schema + `TRCO_ACTV_HSTR`. |
| V45 | View Setting seed for both `addon-sales-einvoices` + `addon-expenses-einvoices`. |
| **V52** | `addon-expenses-einvoices` demo seed — real-DEV `DOC_TYPE='B'` rows, tenant `VN_VNM0004_000001`. Idempotent. |
| **V54** | View Setting prod alignment for `addon-expenses-einvoices`. |

> Confirm versions against `src/main/resources/db/migration/` — file headers can lag.
