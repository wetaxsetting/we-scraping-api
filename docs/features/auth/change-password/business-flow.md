# Change Password — Business Flow (Backend)

End-to-end request flow for the Change Password operation. Source of truth: the implemented Spring code
([AuthController.changePassword](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java)
→ [ChangePasswordService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/ChangePasswordService.java)).

```
User (logged-in)
      │
      ▼
ProfileMenu → Change Password dialog  (current_password, new_password, confirm_password)
      │   (request carries the JWT; global anyRequest().authenticated() gates the controller)
      ▼
AuthController.changePassword  ──►  ChangePasswordService.changePassword(req, ipAddr)
      │                                 │
      │                                 ├─ TenantContext.currentUserId()   (caller id — never a body field)
      │                                 ├─ UserInfmRepository              (load + re-check eligibility, write USER_PW)
      │                                 ├─ PasswordEncoder (WecmsPasswordEncoder)
      │                                 │     · matches() → verify current pw + reuse check
      │                                 │     · encode()  → legacy SHA-256 base64 (44 chars)
      │                                 ├─ UserPwChgHisRepository          (last-3 read + append row)
      │                                 └─ LegacyFunctionRepository        (REG_SEQ_NO via wb_get_next_val funnel)
      ▼
ApiResponse<Void>  ("Password changed" — { status, data: null })
```

---

## Narrative

1. **Caller resolution.** The controller forwards the client IP and delegates to the service. The
   service reads `TenantContext.currentUserId()` — the user whose password will change. There is **no
   user id in the request body**: a user can only change their own credential. Null id (somehow reached
   unauthenticated) → `WBE1401`, fail closed.

2. **Eligibility re-check.** The bound user is re-loaded from `USER_INFM` and must still be
   not-deleted, active, and unlocked. A user whose account was deleted/deactivated/locked after the
   token was issued cannot rotate their password → `WBE1401`. This mirrors the `AuthService.me()`
   re-verification stance (the JWT claim is trusted for identity, but eligibility is re-read).

3. **Three ordered validations** (see [functional-specification.md](functional-specification.md) §3):
   - **Confirmation match** — `new_password == confirm_password`, else `WBE_CHANGE_PASSWORD_MISMATCH`
     (legacy `WBE1052`). The frontend Zod schema also blocks new ≠ confirm before the call, but the
     backend is authoritative.
   - **Current password** — `matches(current_password, stored)`, else `WBE2091` "Password confirmation
     failed." (legacy `WBE1053`). The same `WBE2091` semantic ("verify the logged-in user's own
     password") is reused by the Login Credentials reveal re-auth path.
   - **Reuse** — the new password must not equal any of the **last 3** `USER_PW_CHG_HIS` hashes, else
     `WBE_CHANGE_PASSWORD_REUSED` (legacy `WBE1017`).

4. **Write + history append (one transaction).** On all-pass, the new password is hashed once and
   written to **both** `USER_INFM.USER_PW` (the live credential, `MOD_ID` stamped) and a fresh
   `USER_PW_CHG_HIS` row (`USER_PW` = same hash, `IP_ADDR`, `REG_DTM`/`MOD_DTM` = now). `REG_SEQ_NO` is
   allocated per-user MAX+1 through the single `wb_get_next_val` funnel. The whole method is
   `@Transactional` — a failure on either write rolls the change back.

5. **Response.** `200` with `{ status: { error:false, code:"WBI0000", message:"Password changed" }, data:null }`.

---

## Legacy parity (`comm_0400_001_u001`)

| Step | Legacy `comm_0400_001_u001_act.jsp` | Refactor |
|---|---|---|
| Inputs | `USER_PW_PRE` (current), `USER_PW` (new), `USER_PW2` (confirm) | `current_password`, `new_password`, `confirm_password` |
| Required-field check | `ValidationHelper.validateRequired` | `@NotBlank @Size(max=100)` → `WBE_VALIDATION` |
| New vs confirm | `:97-99` → `WBE1052` | `WBE_CHANGE_PASSWORD_MISMATCH` |
| Current vs stored | `:102-108` `USER_INFM WHERE user_pw = hash(?)` COUNT → `WBE1053` | `matches(...)` → `WBE2091` |
| Reuse last 3 | `IDO.USER_PW_CHG_HIS_R003.xml` (last 3) → `WBE1017` | `findTop3...` + `matches` → `WBE_CHANGE_PASSWORD_REUSED` |
| Write credential | `UserInfmDAO.patch` (USER_PW) | `userRepo.save(user)` with new hash + `MOD_ID` |
| Append history | `UserPwChgHisDAO.create` | `historyRepo.save(UserPwChgHis)` (REG_SEQ_NO via funnel) |
| Hash function | `public.hash()` SHA-256 base64 | `LegacyHashPasswordEncoder` — byte-identical SHA-256 base64 |
| Audit log | none (history ledger is the record) | none — **MATCH** |

The validation **order** is preserved exactly: confirm-match → current-match → reuse. This is the
load-bearing behavioural contract of the legacy screen.

---

## Legacy parity & the stale BCrypt claim (APPROVED DEVIATION)

The refactor writes the **legacy SHA-256 hash on every write path** — it does **not** write BCrypt.
This is the coexistence rule (2026-06-10): the Spring app and the live JEX system share the DB and both
must authenticate the same `USER_INFM.USER_PW` row. Writing BCrypt would lock the user out of legacy
JEX (which computes SHA-256 and compares). Classification: **APPROVED DEVIATION** from the typical
"BCrypt-on-write" Spring Security default, sanctioned by the coexistence rule.

> **Stale claim — corrected here.** The OLD root-doc narrative said *login (and by extension password
> writes) "transparently upgrade SHA-256 → BCrypt on successful login."* This is **WRONG / STALE** for
> the current code:
> - [WecmsPasswordEncoder.encode](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java)
>   always emits SHA-256; `upgradeEncoding()` always returns `false`; login is verify-only with no
>   write-back.
> - The stale wording still lives in the **Javadoc** of
>   [LegacyHashPasswordEncoder](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java)
>   ("New passwords are always written with BCrypt … transparently upgrades legacy hashes on successful
>   login") and [UserPwChgHis](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHis.java)
>   ("`USER_PW` stores a BCrypt hash"). These comments are **DEAD / STALE** — the runtime behaviour is
>   SHA-256-only. Tracked as a non-blocking Javadoc cleanup in
>   [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Invariants (every change)

- **Caller identity** comes only from `TenantContext.currentUserId()` (signed JWT) — never a body field.
- **One hash, two writes.** The new password is encoded once; the identical hash lands in both
  `USER_INFM.USER_PW` and the new `USER_PW_CHG_HIS` row, inside one `@Transactional`.
- **`REG_SEQ_NO`** is allocated only through `LegacyFunctionRepository.nextUserPwChgRegSeqNo`
  (`wb_get_next_val` funnel, per-user MAX+1) — never a Java-side scheme.
- **No audit-log row.** Change Password does not write `AUDIT_LOGS_INFM`; the `USER_PW_CHG_HIS` ledger
  is the record of change (legacy parity).
