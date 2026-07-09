# Table Sort — Database Mapping

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

## No own table

Table sort owns **no table**. It is a query-param contract resolved into a Spring Data `Sort` that
the existing list query applies as `ORDER BY`. The only DB touch-points are:

1. The **entity property → DB column** mapping that every `*SortField` enum constant ultimately
   targets (via `entityProperty()` → the JPA `@Column` on the entity).
2. The **`SYS_SCR_OPTION_WECMS.SORT_YN`** flag (owned by View Setting) that must agree with the
   whitelist enum — see the agreement rule in `functional-specification.md`.

## FIELD_ID → entityProperty → DB column

The chain is three hops; the enum owns the middle hop:

```
wire sortField (FIELD_ID)  ──*SortField──▶  entityProperty (JPA)  ──@Column──▶  DB column
        "BALANCE"                              "bal"                              BAL
```

Spring Data `Sort.by("bal")` validates `"bal"` against the entity metamodel, so the DB column name
is never typed by hand in the sort path — it is whatever the entity's `@Column` declares.

## Per-domain enum → column examples (verified)

| Endpoint | Enum / file | FIELD_ID | entityProperty | Entity | DB column (legacy UPPER_SNAKE) |
|---|---|---|---|---|---|
| `GET /banking/accounts` | `AccountSortField` (`domain/banking/service/AccountSortField.java`) | `BANK` | `bankCd` | `AcctMstr` | `BANK_CD` |
| | | `ACCOUNT_NICKNAME` | `acctNickNm` | `AcctMstr` | `ACCT_NICK_NM` |
| | | `BALANCE` | `bal` | `AcctMstr` | `BAL` |
| | | `AVAILABLE_BALANCE` | `realAmt` | `AcctMstr` | `REAL_AMT` |
| | | `LAST_UPDATE` | `acctHisLstInqDt` | `AcctMstr` | `ACCT_HIS_LST_INQ_DT` |
| `GET /settings/contacts` | `ContactSortField` (`domain/setting/contacts/service/ContactSortField.java`) | `HAND_TRCO_KEY` | `handTrcoKey` | `TrcoInfm` | `HAND_TRCO_KEY` |
| | | `TRCO_CMNM` | `trcoCmnm` | `TrcoInfm` | `TRCO_CMNM` |
| | | `TRCO_TYPE` | `trcoType` | `TrcoInfm` | `TRCO_TYPE` |
| | | `TRCO_TAX_ID` | `trcoTaxId` | `TrcoInfm` | `TRCO_TAX_ID` |
| `GET /settings/chart-of-accounts` | `ChartOfAccountsSortField` (`domain/setting/chartofaccounts/dto/ChartOfAccountsSortField.java`) | `CHART_ACCT_CD` | `chartAcctCd` | `ChartAcctInfm` | `CHART_ACCT_CD` |
| | | `CHART_ACCT_NM_EN` | `chartAcctNmEn` | `ChartAcctInfm` | `CHART_ACCT_NM_EN` |
| | | `STTS` | `stts` | `ChartAcctInfm` | `STTS` |
| | | `MOD_DTM` | `modDtm` | `ChartAcctInfm` | `MOD_DTM` |
| `GET /addon/einvoices` | `EInvoiceSortField` (`domain/einvoice/service/EInvoiceSortField.java`) | `DATE_TIME` | `issuDt` | `BusaDocMstr` | `ISSU_DT` |
| | | `TOTAL` | `totlAmt` | `BusaDocMstr` | `TOTL_AMT` |
| | | `STATUS` | `einvInvoicestatus` | `BusaDocMstr` | `EINV_INVOICESTATUS` |
| `GET /operation-status` | `OperationStatusSortField` (`domain/operation/service/OperationStatusSortField.java`) | `COMPANY` | `companyname` | `OprtSttsInfo` | `COMPANYNAME` |
| | | `OPERATION_STATUS` | `companystatus` | `OprtSttsInfo` | `COMPANYSTATUS` |
| | | `LAST_UPDATE` | `searchdatetime` | `OprtSttsInfo` | `SEARCHDATETIME` |

> ⚠️ The "DB column" column above is derived from the entity property by the legacy UPPER_SNAKE
> convention. The `@Column` annotation on each entity is the authoritative source — verify against
> the entity if a column name is load-bearing for a migration.

## SORT_YN seed (owned by View Setting, governed jointly)

`SORT_YN` lives on `SYS_SCR_OPTION_WECMS` (View Setting's table). Table sort does not write it, but
the agreement rule binds them: `SORT_YN='Y'` ⟺ a whitelist enum constant exists. Documented seeds:

| Screen (`WEB_SVC_ID`) | Column | SORT_YN | Reason |
|---|---|---|---|
| `banking-account-inquiry-vnd` | `VAS_ACCOUNT` | `'N'` (V41) | no `AccountSortField` constant — no backing field yet |
| `contacts` | `BANK_NM`, `TRCO_BSUN_POST_ADRS` | `'N'` (V154) | sub-table projections — omitted from `ContactSortField` |

See `wecms-api/CLAUDE.md` Flyway table for V41 / V154. The sort contract itself adds **no Flyway
migration** — adopting a new sortable column is one whitelist-enum constant plus a `SORT_YN` flip in
the View Setting seed that already exists for the screen.
