# Bank Account — Refactor Gap Analysis (Parity Review)

Legacy JEX (`comm_0183_*` family, `ACCT_MSTR` + `ACCT_AUTH_INFO`) vs. the refactor (`bank-account`).
Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **List** | `comm_0183_001_r001` (`IDO.ACCT_MSTR_R012`, `ACTV_YN='Y'`, default `PRN_SEQ`) | `GET /bank-account`, same scope + default ORDER BY, View Setting sort | **MATCH** |
| **Per-row permission count** | `IDO.ACCT_MSTR_R012` `REC_1`/`REC_2` (admins excluded via `NOT EXISTS BUSI_ADMN_INFM`) | `countActiveByAccount` + `findFirstActiveUserName` with the same `NOT EXISTS` clause; `"X + N more"` label | **MATCH** |
| **Bank name** | `wb_get_bank_nm(_,'s')` short name | `BankCdResolver.resolveBankNm` (short → formal → raw) | **MATCH** |
| **Modifier name** | `WB_GET_USER_NM(MOD_ID)` (global) | `resolveModifierNames` (USER_INFM by id, global) | **MATCH** |
| **Update** | `comm_0183_003_u001` (nickname / desc / type / maturity / VAS) | `PATCH /{bankCd}/{acctNo}`, same fields, VAS FK-validated | **MATCH** |
| **Register (Add-chain)** | `comm_0218_003_c001` + `select_bank_accounts_popup` bulk INSERT, dup-skip | `POST /register`, bulk INSERT, silent dup-skip | **MATCH** |
| **Permission roster** | `comm_0183_002_r001` (`IDO.ACCT_AUTH_INFO_R004`, admins excluded) | `GET .../permissions?type=`, admins excluded, self-lock | **MATCH** |
| **Permission save** | `comm_0183_002_u001` (grant/revoke; `USE_YN='N'` soft-revoke) | `PUT .../permissions` replace-semantics diff, soft-revoke | **MATCH** |
| **Withdrawal-bank gate** | `comm_0183_002_u001:160-193` `grpBankCd` whitelist → `WBE0452` | `WithdrawalBankPolicy` (5 banks) → `WBE0452` | **MATCH** (kept, business decision 2026-06-16) |
| **Self-revoke guard** | (UI-level in legacy) | server-side `WBE_CANNOT_REVOKE_SELF` for non-admin | **APPROVED DEVIATION** (stronger enforcement) |
| **Audit logs read** | `comm_0183_004_r001` (`WSVC_ID='comm_0183_001'`, `REF_NO IN ('ACCT_MSTR','ACCT_AUTH_INFO')`) | `GET .../audit-logs`, reads **both** slug + `comm_0183_001` | **MATCH** (+ coexistence broadening) |
| **Audit `WSVC_ID` (write)** | `comm_0183_001` | slug `bank-account` | **APPROVED DEVIATION** (workspace slug rule) |
| **Authorization** | menu visibility only | explicit `@menuAuthz.canAccess('settings-core-bank-account')` | **APPROVED DEVIATION** (F-3 server-side enforcement) |
| **Account create-from-scratch** | none (scrape-only) | none | **MATCH** (nothing to port) |
| **Account delete / de-register** | (legacy de-register path) | not ported (no `DELETE`) | **DEAD LEGACY** (GAP-1) |
| **`AUTH_TYPE='F'` Full-Access** | DB enum value | carried in enum, not exposed on endpoints | **PARTIAL** (GAP-2) |
| **VAS-name composite** | full join "BANK_NM(CCY)" | `"VAS_ACCT_NM(CCY)"` echo | **PARTIAL** (GAP-3) |
| **Per-account detail GET** | (REC seed from row) | list row carries all fields; no detail endpoint | **APPROVED DEVIATION** |

---

## Specially-verified gaps

### GAP-1 — Account de-registration (DEAD LEGACY)

The live JEX screen could de-register / deactivate an account (`ACTV_YN` flip). The refactor exposes no
`DELETE /{bankCd}/{acctNo}` and never flips `ACTV_YN` from this surface — accounts arrive via `register`
and the list scopes to `ACTV_YN='Y'`. **Classification: DEAD LEGACY** — intentionally excluded for the
in-scope feature; a future de-register decision would be a new endpoint.

### GAP-2 — `AUTH_TYPE='F'` (FULL_ACCESS) — PARTIAL

`BankAccountAuthType.FULL_ACCESS` ('F') exists in the enum and is a valid `AUTH_TYPE` value on the
`ACCT_AUTH_INFO` column (the column is `bpchar(1)` with `DEFAULT 'V'` and no `CHECK` constraint in
`V1__acct_auth_info.sql`, so legacy DEV 'F' rows round-trip), but **both permission endpoints reject it**
(`WBE_VALIDATION`) — the dialog is
split View / Withdrawal only. **Classification: PARTIAL** — the code round-trips 'F' rows safely but
offers no UI surface. A future "Full Access" tab would consume it (no DB change needed).

### GAP-3 — VAS-name composite — PARTIAL

The list ships `vas_account_bank` as the compact `"VAS_ACCT_NM(CCY)"` echo
(`SettingsBankAccountService.composeVasAcctBank`) rather than a richer join through
`BUSI_VAS_ACCT_INFM` (e.g. `"BANK_NM(CCY)"`). **Classification: PARTIAL** — functionally complete for the
column; the richer composite is deferred. No parity impact on the maintenance fields.

### Permission Parity (admin exclusion + self-revoke)

Admins are excluded from the roster on both read and write paths (`NOT EXISTS BUSI_ADMN_INFM` in
`countActiveByAccount` / `findFirstActiveUserName`; the roster skips admin ids; the want-set never
carries admin ids), matching legacy `IDO.ACCT_AUTH_INFO_R004:80` `ADMIN_YN != 'Y'`. **Status: MATCH.**
The refactor adds an explicit server-side `WBE_CANNOT_REVOKE_SELF` guard for non-admin callers
(legacy enforced this at the UI). **Status: APPROVED DEVIATION** — same effective boundary, stronger
enforcement.

### Audit-logs coexistence broadening

The popup **reads** both `WSVC_ID='bank-account'` (refactor) and `'comm_0183_001'` (legacy) so a shared
DB surfaces rows written by the live JEX system. **Writes** use the slug only. **Status: MATCH** (legacy
behaviour preserved) **+ documented coexistence broadening.**

---

## Non-blocking cleanup (stale comments / dormant code)

These do **not** affect behaviour or parity — recommend fixing in the next stabilisation pass:

1. **`AcctAuthInfo` entity Javadoc** references "V245" / "V127" as the creating migration; the live file
   is `V1__acct_auth_info.sql` (2026-06-09 renumber).
2. **Controller + `SettingsBankAccountService` + several DTO Javadocs** still say "Batch B" / "Batch A
   placeholders" for permissions, audit-logs, and the per-row permission labels — all of which are
   **implemented**. The flat doc [../bank-account.md](../bank-account.md) carries the same stale framing.
3. **`WBE_ADMIN_REQUIRED`** is registered in `messages.properties` ("Admin user must remain granted.")
   but is **never thrown** — admins are excluded from the roster so there is no admin-must-remain guard.
   Either wire a guard or retire the message.
4. **`BankAccountFilter`** is a wire-reserved record with no filter row in the panel today — the list
   endpoint takes only `sortField` / `sortDirection`, so the filter is currently inert (kept for the
   future filter row + the export-request shape).

---

## Migration status

```
Refactor Status: Implemented (for the in-scope feature surface)
```

Evidence:

- **All 6 endpoints implemented** (controller verified): list / update / register / permission GET / PUT
  / audit-logs.
- **Slug `bank-account` + `settings-core-bank-account`** used end-to-end (URI, package, audit `WSVC_ID`,
  menu grant).
- **Permission diff + soft-revoke + admin-exclusion + self-revoke + withdrawal-bank gate** all in
  `BankAccountPermissionService`.
- **Frontend wired** (no mock fallback for the primary list read; edit / permission / audit-log dialogs
  call live endpoints) — see [wecms-web/docs/features/settings/bank-account/](../../../../../wecms-web/docs/features/settings/bank-account/README.md).
- **Backend tests present** — 3 JUnit classes / 11 `@Test` methods (roster admin-exclusion ×2,
  withdrawal `WBE0452` ×2, bank-name + active-only + permission labels + audit-logs ×7); see
  [test-scenarios.md §3](test-scenarios.md).
- **DEAD LEGACY** (GAP-1) confirmed unreachable-by-design; **PARTIAL** items (GAP-2/GAP-3) tracked.

Residual (non-blocking): the four cleanup items above + the GAP-2 / GAP-3 deferrals. None affect the
implemented feature's parity or correctness.
