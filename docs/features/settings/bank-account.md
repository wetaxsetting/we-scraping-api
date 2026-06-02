# Bank Account

> Canonical Settings-menu entry for **Settings → Bank Account** (backend). **Confirmed against source
> 2026-06-01** — legacy `comm_0183_*`, refactor `domain/setting/bankaccount/`. Implemented (Batch A:
> list / update / register / permissions; Batch B items noted below).

## Overview
Settings → Bank Account is the per-tenant master of registered **bank-account rows** (`ACCT_MSTR`) —
the same accounts surfaced in Banking → Account Inquiry, here exposed with their maintenance fields
(nickname, description, account type, maturity, VAS-account mapping) plus per-user **View / Withdraw
permissions** and **bulk registration** from a Coocon scrape. Slug: `bank-account`. Mirrors legacy
JEX `comm_0183_*`.

### Confirmed source files
**JEX (read-only reference)**
- Views: `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0183_001_view.jsp` (list), `comm_0183_002_view.jsp`, `comm_0183_003_view.jsp` (detail/edit), `comm_0183_004_view.jsp`
- Actions: `.../comm/comm_0183_001_r001_act.jsp` (list), `comm_0183_001_u001_act.jsp`, `comm_0183_003_r001_act.jsp` (detail), `.../api/comm/comm_0183_003_u001_act.jsp` (update), `comm_0183_002_r001/_002_u001`, `comm_0183_004_r001`
- TS: `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0183_001.ts` (AUTH_TYPE codes at `comm_0183_001.ts:633`), `comm_0183_002/003/004.ts`

**Refactor (`wecms-api`)** — `src/main/java/com/kosign/wecms/domain/setting/bankaccount/`
- `controller/SettingsBankAccountController.java`
- `service/SettingsBankAccountService.java`, `BankAccountPermissionService.java`, `BankAccountSortField.java`, `BankAccountFilter.java`
- `entity/AcctAuthInfo.java` (+ `AcctAuthInfoId.java`, `BankAccountAuthType.java`)
- `repository/AcctAuthInfoRepository.java`
- `dto/` — `BankAccountRow`, `BankAccountListResponse`, `BankAccountRegisterRequest`/`Response`, `BankAccountUpdateRequest`, `BankAccountPermission{Member,Response,UpdateRequest}`
- Register path also uses `domain/banking/service/AccountRegistrationService.java` + `domain/banking/repository/AcctMstrRepository.java`

## Related APIs
Base URI `/api/v1/settings/bank-account` (JWT; tenant via `TenantContext` — no `bizKey` in routes; snake_case wire):

| METHOD | URI | Purpose | Legacy |
|---|---|---|---|
| `GET` | `/` | List accounts (`sortField`, `sortDirection`; `sortField` = View Setting `FIELD_ID` e.g. `BANK`, `BALANCE`) | `comm_0183_001_r001` |
| `PATCH` | `/{bankCd}/{acctNo}` | Patch one master row (nickname / description / account type / maturity / VAS mapping) | `comm_0183_003_u001_act.jsp` |
| `POST` | `/register` | Bulk-INSERT accounts picked from a Coocon scrape (closes the Login Credentials add-chain) | `comm_0218_003_c001` + `select_bank_accounts_popup` |
| `GET` | `/{bankCd}/{acctNo}/permissions?type=` | Permission roster for `(account, kind)` | — |
| `PUT` | `/{bankCd}/{acctNo}/permissions` | Replace-semantics grant/revoke (single audit event) | `comm_0220_*` revoke path |

Adjacent endpoints the panel also calls: `GET /api/v1/banking/scraping/account-list`,
`POST /api/v1/banking/accounts/register`, `/api/v1/login-credentials/bank`,
`GET /api/v1/settings/vas-account/lookup`, `/api/v1/screen-options/` (View Setting).

## Database Tables
- **`ACCT_MSTR`** — bank-account master (created in `V13__sprint1_banking_schema.sql`). Holds
  nickname, description, `ACCT_TYPE_CD`, maturity, `PRN_SEQ` (print order), balances, VAS mapping FK.
- **`ACCT_AUTH_INFO`** — per-user-per-account permission rows. ✅ Created in
  **`V153__bank_account_screen_seed.sql:49`** (`CREATE TABLE IF NOT EXISTS`, re-asserted idempotently in
  `V186__bank_account_screen_seed.sql:54`). PK `(BIZ_KEY, USER_ID, BANK_CD, ACCT_NO, AUTH_TYPE)`;
  `CHECK AUTH_TYPE IN ('V','W','F')`; `CHECK USE_YN IN ('Y','N')` (`USE_YN='N'` soft-revokes, keeps audit
  history); index `ix_acct_auth_info__acct (BIZ_KEY, BANK_CD, ACCT_NO, AUTH_TYPE)`; audit columns are
  legacy `VARCHAR(14)` `yyyyMMddHHmmss`. *(The `AcctAuthInfo` entity javadoc's "V127" reference is stale —
  V127 is `bank_exch_rate_infm`; the real DDL is V153.)*
- **`BUSI_VAS_ACCT_INFM`** — VAS-account master; the update path validates the VAS FK against it
  (`STTS='A'` + `DEL_YN='N'`, current tenant).
- **`AUDIT_LOGS_INFM`** — write-event audit rows via shared `AuditLogService` (`REF_NO = "ACCT_MSTR"`).

## Business Rules
- Tenant-scoped via JWT `TenantContext`.
- **List** mirrors `comm_0183_001_r001`; default `ORDER BY PRN_SEQ ASC, BANK_CD ASC, ACCT_NO ASC`;
  does **not** filter by `ACTV_YN` (users can see inactive accounts, matching legacy).
- **Update** patches a single `ACCT_MSTR` row and appends a per-locale audit log (mirrors
  `comm_0183_003_u001_act.jsp:154-191`). Maturity stored legacy `yyyyMMdd`, exposed as ISO `yyyy-MM-dd`.
- **VAS mapping** FK validated against `BUSI_VAS_ACCT_INFM` so a stale/malicious client can't stamp an
  arbitrary `seqNo`.
- **Permissions** are replace-semantics: client sends the full desired user-id set; service computes
  grant/revoke diff and writes one audit event. `AUTH_TYPE` (DDL `CHECK IN ('V','W','F')`): `V` = VIEW,
  `W` = WITHDRAW — a user can hold these independently; `F` is a third valid code whose meaning is an
  open item (see below). Permission writes guard against revoking the admin's own access
  (`WBE_CANNOT_REVOKE_SELF`) and require admin role (`WBE_ADMIN_REQUIRED`).
- **Register** bulk-inserts the accounts the user picked from a successful scrape.
- `ACCT_TYPE_CD` (`VARCHAR(2)`, `common/enums/AcctTypeCd`): `01` LOAN, `02` DEPOSIT, `03` CURRENT_KHR,
  `04` CURRENT_USD, `05` SAVINGS_KHR, `06` SAVINGS_USD, `07` CURRENT_KRW, `08` SAVINGS_KRW.

## Validation
- **Update** (`BankAccountUpdateRequest`): all-null body → `WBE_VALIDATION`; `nickname` ≤20,
  `description` ≤200, `accountTypeCd` ≤2 (rejected via `AcctTypeCd.fromCode`), `maturityDate` ISO ≤10,
  VAS FK validated.
- **Register** (`BankAccountRegisterRequest`): `bankCd` `@NotBlank` ≤20, `items` `@NotEmpty`; each
  item: `acctNo` ≤100, `currency` ≤3, `accountTypeCd` ≤2, `nickname` ≤20, `description` ≤200, `acctNm` ≤100.

## Error Cases
- `WBE_VALIDATION` (empty update / bad input)
- `WBE_BANK_ACCOUNT_NOT_FOUND`
- `WBE_BANK_ACCOUNT_INVALID_TYPE`
- `WBE_BANK_ACCOUNT_INVALID_DATE`
- `WBE_BANK_ACCOUNT_INVALID_VAS_ACCT`
- Permissions: `WBE_USER_NOT_IN_TENANT`, `WBE_ADMIN_REQUIRED`, `WBE_CANNOT_REVOKE_SELF`
- Standard envelope; generic `WBE9999`.

## Feature status (verified against code 2026-06-01)

**Implemented now**
- List (`GET /`) with default ORDER BY + View Setting sort.
- Update (`PATCH /{bankCd}/{acctNo}`) — incl. **VAS-account mapping set via the update body**
  (FK-validated against `BUSI_VAS_ACCT_INFM`).
- Register (`POST /register`) — bulk INSERT of scraped accounts.
- **View / Withdrawal permission management** (`GET`/`PUT .../permissions`) — real
  `BankAccountPermissionService` (roster, grant/revoke diff, admin guards, audit event).
  > The controller class javadoc still calls permission management "Batch B" — that comment is **stale**;
  > the permission endpoints and service are live.

**Placeholder / planned**
- **Per-row audit-log READ endpoint** — not implemented (no `…/audit` route). The panel's audit-log
  dialog shows **mock log rows**; `BankAccountRow.lastModifiedBy/At` feed the audit cell as an interim.
- **VAS-name composite** (`vasAccountBank` = "BANK_NM(CCY)") — Batch A echoes `vasAcctNm`; the full
  composite + any dedicated **VAS-mapping CRUD sub-resource** is planned.
- Frontend bank-logo marks render a brand-coloured **placeholder** until the real PNG asset is present
  (cosmetic, not a data gap).

**Needs confirmation**
- Meaning of `AUTH_TYPE = 'F'` (allowed by the DDL `CHECK` alongside `V`/`W`; `BankAccountAuthType`
  documents only View/Withdraw).
- Whether a standalone **VAS-mapping CRUD** sub-resource is still planned beyond the existing update path.

## Notes
Web/UI: [../../../wecms-web/docs/settings/bank-account.md](../../../../wecms-web/docs/features/settings/bank-account.md).
