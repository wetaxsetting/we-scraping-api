# Change Password — Migration Guide (JEX → Spring)

How each JEX layer of `comm_0400_001_u001` (popup `comm_0401_001`) became its Spring counterpart.
Login coexistence audit: [docs/audits/login-legacy-compatibility-2026-06-10.md](../../../audits/login-legacy-compatibility-2026-06-10.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen / popup | `comm_0401_001` (change-password dialog) | `<ChangePasswordDialog>` (ProfileMenu dialog — no route) |
| Action / orchestration | `comm_0400_001_u001_act.jsp` | `POST /api/v1/auth/change-password` → `AuthController.changePassword` → `ChangePasswordService.changePassword` |
| Required-field check | `ValidationHelper.validateRequired` | `@NotBlank @Size(max=100)` on `ChangePasswordRequest` → `WBE_VALIDATION` |
| New-vs-confirm check | `comm_0400_001_u001_act.jsp:97-99` | `service` step 1 → `WBE_CHANGE_PASSWORD_MISMATCH` |
| Current-password check | `comm_0400_001_u001_act.jsp:102-108` (`USER_INFM WHERE user_pw = hash(?)` COUNT) | `passwordEncoder.matches(...)` → `WBE2091` |
| Reuse-last-3 check | `IDO.USER_PW_CHG_HIS_R003.xml` (`ORDER BY REG_DTM DESC LIMIT 3`) | `findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc` + `matches` → `WBE_CHANGE_PASSWORD_REUSED` |
| Write credential | `UserInfmDAO.patch` (`USER_PW`) | `userRepo.save(user)` with new hash + `MOD_ID` |
| Append history | `UserPwChgHisDAO.create` | `historyRepo.save(UserPwChgHis)` |
| Hash function | `public.hash(txt)` SHA-256 base64 | `LegacyHashPasswordEncoder` (via `WecmsPasswordEncoder`) — byte-identical SHA-256 base64 |
| Key-gen | `SeqHelper` → `wb_get_next_val('user_pw_chg_his','reg_seq_no',…)` | `LegacyFunctionRepository.nextUserPwChgRegSeqNo` (V140 #27, the funnel) |
| Entity / DAO | `wabooks.entity.UserPwChgHis` + `UserPwChgHisDAO` | `UserPwChgHis` + `UserPwChgHisRepository` |
| Caller identity | session user | `TenantContext.currentUserId()` (signed JWT) — never a body field |

---

## Field mapping (`comm_0400_001_u001` → `ChangePasswordRequest`)

| Legacy input | Refactor wire field | Refactor Java field |
|---|---|---|
| `USER_PW_PRE` (current) | `current_password` | `currentPassword` |
| `USER_PW` (new) | `new_password` | `newPassword` |
| `USER_PW2` (confirm) | `confirm_password` | `confirmPassword` |

All three are `@NotBlank @Size(max=100)` and trimmed in the record's compact constructor. The legacy
WSVC length cap and the `USER_INFM.USER_PW` column width are both honoured by `@Size(max=100)`.

---

## Error-code translation (ML codes → refactor codes)

Per the refactor rule "translate JEX ML codes into `WBExxxx`/`WBIxxxx`; reference the legacy code in
the migration doc" — the legacy codes are **not** reused verbatim:

| Legacy ML code | Legacy meaning | Refactor code | Refactor message |
|---|---|---|---|
| `WBE1052` | New password ≠ confirmation | `WBE_CHANGE_PASSWORD_MISMATCH` | "New password and confirmation do not match." |
| `WBE1053` | Current password mismatch | `WBE2091` | "Password confirmation failed." |
| `WBE1017` | Password reused within last 3 changes | `WBE_CHANGE_PASSWORD_REUSED` | "You cannot reuse any of your last 3 passwords." |
| (session) | Not logged in / session expired | `WBE1401` | "Your session is no longer valid. Please sign in again." |

> **Why `WBE2091` (not a new code) for the current-password mismatch.** The refactor reuses the
> existing `WBE2091` "Password confirmation failed." — the same "verify the logged-in user's own
> password" semantic already used by the Login Credentials reveal re-auth. The mapping is recorded in
> the [messages.properties](../../../../src/main/resources/messages.properties) comment block
> (`:556-567`).

---

## Identifier translation (slug, not WSVC id)

The refactor identifier surface carries the kebab slug **`change-password`** — feature folder, frontend
component family, and docs. The REST path is the auth-controller convention
`/api/v1/auth/change-password`. There is **no** menu permission slug (self-service, JWT-only). Legacy
WSVC ids (`comm_0400_001_u001`, `comm_0401_001`) appear only in (1) JEX file:line citations and (2)
this migration prose. There is **no audit `WSVC_ID`** for this feature — Change Password writes no
`AUDIT_LOGS_INFM` row (the `USER_PW_CHG_HIS` ledger is the record, legacy parity).

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Hashing | `public.hash()` SHA-256 (DB function) | `LegacyHashPasswordEncoder` SHA-256 in Java — byte-identical; **never BCrypt** (coexistence, APPROVED DEVIATION) |
| Current-pw check | SQL COUNT `WHERE user_pw = hash(?)` | `passwordEncoder.matches(...)` in Java (same SHA-256 comparison) |
| Caller identity | server session | `TenantContext.currentUserId()` from the signed JWT; no user id in the body |
| Key-gen | `wb_get_next_val` via DAO `SeqHelper` | same `wb_get_next_val` branch, funneled through `LegacyFunctionRepository` |

---

## Dead / out-of-scope legacy flows (not ported)

| Legacy flow | Disposition |
|---|---|
| SHA-256 → BCrypt write-back on change/login | **DEAD / STALE** — the OLD doc claim of a transparent upgrade is wrong; the encoder is SHA-256-only and never upgrades. Stale Javadoc remains on the two encoders/entity (cleanup tracked). |
| Password-expiry forced change (`USER_INFM.PW_CHG_REQ_SEQ_NO`) | **MISSING / deferred** — column mapped on `UserInfm` but not read; no expiry-forced flow ported. |
| Admin password reset | **Out of scope here** — lives under Settings → Users (`domain/setting/users`), not this slice. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification + evidence.

---

## Schema / key-gen

| Item | Where |
|---|---|
| `USER_INFM` / `USER_PW_CHG_HIS` schema | legacy tables, column-for-column DEV parity (Legacy Schema Parity Rule); in the inventory |
| `wb_get_next_val('user_pw_chg_his','reg_seq_no',…)` | V140 #27 (per-user MAX+1), in the funnel |

No Flyway is auto-run (`ddl-auto: validate`); the schema is treated as external / pre-migrated.
