# Table Sort — Functional Specification

**Part of:** Common → table-sort
**Last verified:** 2026-06-23

## Capabilities

| # | Capability | Where enforced |
|---|---|---|
| F1 | Every list endpoint accepts two optional query params: `sortField` + `sortDirection` | each controller (`@RequestParam(required=false)`) |
| F2 | `sortField` is a View Setting `FIELD_ID` (uppercase semantic key) — the same key family as `SYS_SCR_OPTION_WECMS.field_id` and the Excel-export column contract | per-domain `*SortField` constants |
| F3 | `sortDirection` is `ASC` / `DESC`, case-insensitive | `SortDirection.parse` |
| F4 | The wire `sortField` is translated to a JPA entity property **only** through the per-domain whitelist enum — never concatenated into SQL | `SortableField.entityProperty()` |
| F5 | Unknown / blank `sortField` or `sortDirection` falls back to the endpoint's legacy default `ORDER BY` — no exception, no 4xx | `buildSort` returns `DEFAULT_SORT` |
| F6 | User-chosen sort always appends the default order as a stable secondary key | `Sort.by(primary).and(DEFAULT_SORT)` |
| F7 | Special FIELD_IDs (`CHECKBOX`, `ETC`) are never sortable | not registered in any whitelist |

## The whitelist-enum contract

`common/web/SortableField` is the interface every per-domain enum implements:

```java
public interface SortableField {
    String entityProperty();   // JPA property the wire FIELD_ID maps to (e.g. "bal", "acctNickNm")
}
```
(`SortableField.java:17-25`)

A per-domain enum (one per list endpoint) declares the allowed `FIELD_ID`s:

- **Constant name** = View Setting `FIELD_ID` (uppercase) — e.g. `BANK`, `BALANCE`, `LAST_UPDATE`.
- **Constant value** = the JPA entity property — e.g. `BANK("bankCd")`, `BALANCE("bal")`.
- The enum is the **only** place a wire string becomes a sort column. `Spring Data Sort.by(property)`
  validates the property against the entity metamodel, so a typo in the mapping fails fast at query
  time rather than reaching the database (`SortableField.java:19-24`).

Verified examples:

| Enum | File | Constants → property |
|---|---|---|
| `AccountSortField` | `domain/banking/service/AccountSortField.java:22-35` | `BANK→bankCd`, `ACCOUNT_NICKNAME→acctNickNm`, `BALANCE→bal`, `AVAILABLE_BALANCE→realAmt`, `LAST_UPDATE→acctHisLstInqDt` |
| `ContactSortField` | `domain/setting/contacts/service/ContactSortField.java:21-26` | `HAND_TRCO_KEY→handTrcoKey`, `TRCO_CMNM→trcoCmnm`, `TRCO_TYPE→trcoType`, `TRCO_TAX_ID→trcoTaxId` |
| `ChartOfAccountsSortField` | `domain/setting/chartofaccounts/dto/ChartOfAccountsSortField.java:32-37` | `CHART_ACCT_CD→chartAcctCd`, `CHART_ACCT_NM_EN→chartAcctNmEn`, `STTS→stts`, `MOD_DTM→modDtm` |
| `EInvoiceSortField` | `domain/einvoice/service/EInvoiceSortField.java:25-34` | `DATE_TIME→issuDt`, `INVOICE_NO→einvInvoicesymbol`, `BUYER→trcoCmnm`, `CURRENCY→trcoTrscCcyCd`, `TOTAL→totlAmt`, `STATUS→einvInvoicestatus`, `TAX→taxAmtSum`, `PRETAX→splyAmtSum` |
| `OperationStatusSortField` | `domain/operation/service/OperationStatusSortField.java:24-29` | `COMPANY→companyname`, `ADDRESS→officeadd`, `OPERATION_STATUS→companystatus`, `LAST_UPDATE→searchdatetime` |

## Lenient parse → default fallback

`SortDirection` (`SortDirection.java:21-30`):

```java
public static SortDirection parse(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    if (trimmed.isEmpty()) return null;
    try { return valueOf(trimmed.toUpperCase()); }
    catch (IllegalArgumentException e) { return null; }   // unknown ⇒ null, never throws
}
```

Each per-domain enum's `parse(...)` follows the same shape (some return `null`, some
`Optional.empty()` — e.g. `ContactSortField.parse` / `ChartOfAccountsSortField.parse` return
`Optional`). Either way the service treats the request as **unsorted** and applies the default
`ORDER BY`. A stale frontend bundle never produces a 4xx.

## Special-field exclusion

`CHECKBOX` (row-selection, `DISP_ORD=0`) and `ETC` (action column, `DISP_ORD=99`) ride through
View Setting for column ordering but are **never sortable**:

- Frontend filters them via `isSpecialViewSettingField(...)` before wiring a sort handler.
- Backend simply **does not register them** in any `*SortField` enum, so a malicious / stale request
  for `sortField=CHECKBOX` resolves to null → default `ORDER BY`. The exclusion is documented in the
  enum Javadoc (`AccountSortField.java:11-20`, `OperationStatusSortField.java:12-17`).

## SORT_YN ↔ whitelist agreement rule

Every column seeded `SORT_YN='Y'` in `SYS_SCR_OPTION_WECMS` **MUST** have a constant in the matching
whitelist enum. A column with no backing entity field is seeded `SORT_YN='N'` — never `'Y'` relying
on the silent null fallback, which would produce a visible-but-inert sort glyph that misleads users.

- **V41 precedent**: `VAS_ACCOUNT` is in the Account Inquiry View Setting registry but has no
  `AccountSortField` constant (no backing field yet), so V41 flips its `SORT_YN` to `'N'`. When the
  source data lands, the same migration that adds the enum constant flips `SORT_YN` back to `'Y'`.
- **Contacts precedent**: `BANK_NM` + `TRCO_BSUN_POST_ADRS` project from sub-tables (no clean entity
  property), so they are seeded `SORT_YN='N'` and deliberately omitted from `ContactSortField`
  (`ContactSortField.java:27-29`).

## Default ORDER BY mirrors the legacy listing

When the legacy sort key is safely mappable, the default `ORDER BY` reproduces the legacy default.
Account Inquiry: `DEFAULT_SORT = Sort.by(asc("prnSeq"), asc("bankCd"), asc("acctNo"))`
(`AccountService.java:131-132`), matching legacy `SortHelper#86` `prn_seq:asc`, with `bankCd, acctNo`
as deterministic tiebreakers. When the legacy key is **not** safely mappable (entity field missing
from the Sprint subset, NUMERIC overflow risk, value-coverage gap), keep the existing default and
document the deferral inline with a `// TODO:` referencing the legacy column — never silently
diverge.

## Edge cases

| Case | Behaviour |
|---|---|
| Both params absent | `DEFAULT_SORT` |
| `sortField` present, `sortDirection` absent | `DEFAULT_SORT` (both must be non-null to build a primary) |
| `sortField` = `CHECKBOX` / `ETC` / typo | not in whitelist → null → `DEFAULT_SORT` |
| `sortDirection` = `asc` / `DeSc` | accepted (case-insensitive) |
| `sortDirection` = `"UP"` | unknown → null → `DEFAULT_SORT` |
| Export path (unpaged) | `applyDefaultSortIfNeeded(...)` weaves the same `Sort` in so export ↔ list agree (`AccountService.java:594-599`) |
