# Settings > Bank Account — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend (controller + 2 services + 1 repository + entity + 10 DTOs + `V1__acct_auth_info.sql` schema) and frontend wired (list / edit dialog / View+Withdrawal permission dialogs / audit-logs popup all call live endpoints, no mock fallback for the primary read). Legacy `comm_0183_*` parity verified against source 2026-06-23.
**Module / menu:** Settings → **Bank Account** (Settings modal panel — **no standalone route**)
**Refactor slug:** `bank-account` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire base path:** `/api/v1/settings/bank-account` (list · update · register · permissions GET/PUT · audit-logs GET)
**Menu permission slug:** `settings-core-bank-account`

> This folder is the **primary documentation home** for Bank Account on the backend. It consolidates
> and indexes material that previously lived in the single flat file
> [../bank-account.md](../bank-account.md); that file is **preserved and cross-linked** (see
> [§ Related & preserved docs](#related--preserved-docs)), not deleted. The flat file predates the
> permission + audit-log endpoints landing and labels them "Batch B" — **that status is stale**; this
> folder reflects the real, implemented surface.

---

## What this feature is

Settings → Bank Account is the per-tenant master of registered **bank-account rows** (`ACCT_MSTR`) —
the same accounts surfaced in Banking → Account Inquiry, here exposed with their maintenance fields
(nickname, description, account type, maturity, VAS-account mapping) plus per-user **View / Withdrawal
permissions** (`ACCT_AUTH_INFO`) and a per-account **audit-log** popup. A tenant administrator can:

- **List** the active accounts for the current `BIZ_KEY`, sortable by a View Setting `FIELD_ID`.
- **Edit** one account's maintenance fields (nickname, description, account type, maturity date, VAS mapping).
- **Register** accounts in bulk from a successful Coocon scrape (the Login Credentials Add-chain).
- **Manage permissions** — replace-semantics View (`AUTH_TYPE='V'`) / Withdrawal (`AUTH_TYPE='W'`) grantee sets.
- **Read the audit history** of one account (metadata edits + permission grants), latest-first.

There is **no create-from-scratch** and **no delete** of an account on this screen — accounts arrive
via the scrape Add-chain (`register`), and de-registration is out of scope (see
[refactor-gap-analysis.md](refactor-gap-analysis.md)).

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 6: GET (list) · PATCH `/{bankCd}/{acctNo}` (update) · POST `/register` (bulk insert) · GET `/{bankCd}/{acctNo}/permissions?type=` (roster) · PUT `/{bankCd}/{acctNo}/permissions` (replace) · GET `/{bankCd}/{acctNo}/audit-logs` |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-core-bank-account')")` — class-level on the controller (F-3 menu-auth rollout) |
| Tenant scope | `TenantContext.currentBizKey()` — never a request param; `BIZ_KEY` is the leading PK / filter column |
| Tables | `ACCT_MSTR` (master, owned by `domain/banking`) + `ACCT_AUTH_INFO` (`V1__acct_auth_info.sql`) + read-only `BUSI_VAS_ACCT_INFM` (VAS FK validation) + `AUDIT_LOGS_INFM` (write events + popup read) + `USER_INFM` / `BUSI_ADMN_INFM` (roster + actor names) |
| List scope | active accounts only (`ACTV_YN='Y'`) — legacy `comm_0183_001_r001` / `IDO.ACCT_MSTR_R012` parity (G11, business decision 2026-06-16 = follow legacy) |
| Permissions | replace-semantics diff; soft-revoke (`USE_YN='N'`) keeps audit history; admins excluded from the roster; self-revoke blocked for non-admins; Withdrawal grants restricted to 5 firm-banking banks (`WBE0452`) |
| Key generation | none on this surface — `register` writes the legacy composite PK `(BIZ_KEY, BANK_CD, ACCT_NO)` verbatim from the scrape (no `wb_get_next_val`) |
| Audit | every Update / Register / permission-change writes `AUDIT_LOGS_INFM` with `WSVC_ID='bank-account'` (slug); the audit-logs popup **reads** both the slug AND legacy `comm_0183_001` so it surfaces live-JEX rows on a shared DB |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.bankaccount
├── controller/SettingsBankAccountController.java     6 endpoints; class-level @menuAuthz gate
├── service/
│   ├── SettingsBankAccountService.java               list / update / register / audit-logs; bank-name + modifier-name resolution; audit
│   ├── BankAccountPermissionService.java             permission roster + replace-semantics diff; admin / self-revoke / withdrawal-bank guards; audit
│   ├── BankAccountSortField.java                      View Setting FIELD_ID → AcctMstr property whitelist (SortableField)
│   ├── BankAccountFilter.java                         optional (keyword / bankCd / ccyCd) filter record — wire-reserved, no filter row today
│   └── WithdrawalBankPolicy.java                      5-bank withdrawal whitelist (legacy comm_0183_002_u001 grpBankCd → WBE0452)
├── repository/AcctAuthInfoRepository.java            roster, active-count, first-name, exists, revoke-all (all BIZ_KEY-scoped)
├── entity/
│   ├── AcctAuthInfo.java                              @Table(ACCT_AUTH_INFO), @IdClass; AUTH_TYPE + USE_YN @JdbcTypeCode(CHAR)
│   ├── AcctAuthInfoId.java                            composite PK { bizKey, userId, bankCd, acctNo, authType }
│   └── BankAccountAuthType.java                       V/W/F enum — code() + wireName() + i18nKey(); fromCode / fromWireName
└── dto/
    ├── BankAccountRow.java · BankAccountListResponse.java
    ├── BankAccountUpdateRequest.java
    ├── BankAccountRegisterRequest.java · BankAccountRegisterResponse.java
    ├── BankAccountPermissionMember.java · BankAccountPermissionResponse.java · BankAccountPermissionUpdateRequest.java
    └── BankAccountAuditRow.java · BankAccountAuditResponse.java
```

The `ACCT_MSTR` entity / repository (`AcctMstr`, `AcctMstrRepository`) are **owned by `domain/banking`** and
reused here; this feature owns only `ACCT_AUTH_INFO`. The register fan-out (and account-auth provisioning)
also touches `domain/banking/service/AccountRegistrationService` for the scrape Add-chain.

Flyway: [V1__acct_auth_info.sql](../../../../src/main/resources/db/migration/V1__acct_auth_info.sql) creates
`ACCT_AUTH_INFO` (PK `(BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, AUTH_TYPE)`, FK to `ACCT_MSTR`). `ACCT_MSTR`
itself lands in the banking schema migration (`V13`, extended by `V23`).

> **Stale-comment note:** the `AcctAuthInfo` entity Javadoc references "V245" / "V127", and the controller
> + several DTOs reference "Batch B" and "Batch A placeholders." Those are **stale** — the live migration
> is `V1__acct_auth_info.sql` (post-2026-06-09 renumber), and permissions + audit-logs + the per-row
> permission label aggregates are all implemented. Tracked as non-blocking cleanup in
> [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: list → controller → service → repository → row response; update; register; permission roster + replace; audit-logs popup |
| [functional-specification.md](functional-specification.md) | Operations matrix, field-by-field validation, permission rules (admin exclusion / self-revoke / withdrawal-bank), audit expectations, error surface |
| [database-mapping.md](database-mapping.md) | `ACCT_MSTR` + `ACCT_AUTH_INFO` columns, FK / PK, and the full legacy-column → entity-field → DTO-field → UI-field mapping |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint, error-code registry, authorization, wire envelope |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (JSP / `_act.jsp` / IDO / WSVC / entity / DAO) became its Spring counterpart, incl. dropped/dead legacy flows |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios per operation (list / update / register / permissions / audit-logs) + which CRUD ops do NOT exist |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with status + evidence |

---

## Legacy & Migration Traceability

**Legacy JEX counterpart:** WSVC family `comm_0183_001` (list) · `comm_0183_002` (permissions) · `comm_0183_003` (detail/edit) · `comm_0183_004` (audit logs). Tables `ACCT_MSTR` + `ACCT_AUTH_INFO`.

---

## Related & preserved docs

These existing docs are **preserved** and remain authoritative for their slice. This README is the entry
point; deep detail lives here:

- **Preserved flat spec (backend):** [../bank-account.md](../bank-account.md) — the original single-file
  spec. Predates the permission + audit-log endpoints; read it for the early "Batch A/B" framing, but
  this folder is canonical for the implemented surface.
- **Frontend feature folder:** [wecms-web/docs/features/settings/bank-account/](../../../../../wecms-web/docs/features/settings/bank-account/README.md)
- **Preserved flat spec (frontend):** [wecms-web/docs/features/settings/bank-account.md](../../../../../wecms-web/docs/features/settings/bank-account.md)
- **JEX legacy spec set (read-only):** the live JEX `comm_0183_*` module under `WECMS DEV/` (see [source-code-index.md](source-code-index.md))

---

## Open items

1. **VAS-name composite** — `vas_account_bank` ships the compact `"VAS_ACCT_NM(CCY)"` echo; a richer
   join through `BUSI_VAS_ACCT_INFM` (e.g. `"BANK_NM(CCY)"`) is deferred. (`SettingsBankAccountService.composeVasAcctBank`.)
2. **`AUTH_TYPE='F'` (FULL_ACCESS)** — present in `BankAccountAuthType` + the DB enum for legacy DEV-data
   compatibility, but **not exposed** on the permission endpoints (the dialog is split View / Withdrawal
   only). A future "Full Access" surface would consume it.
3. **Stale "Batch A/B" Javadoc + entity migration references (V245 / V127)** — non-blocking; tracked in
   [refactor-gap-analysis.md](refactor-gap-analysis.md).
