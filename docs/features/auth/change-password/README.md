# Auth > Change Password — Backend Feature (Refactor)

**Status:** ✅ Implemented — backend endpoint (`POST /api/v1/auth/change-password`) + frontend wired (`<ChangePasswordDialog>` in `wecms-web`, no mock fallback). Self-service password change for the logged-in user.
**Module / menu:** Auth → **Change Password** (ProfileMenu dialog — **no standalone route**)
**Refactor slug:** `change-password` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire path:** **`POST /api/v1/auth/change-password`** (under the `/api/v1/auth` controller)
**Authorization:** JWT only — the global `anyRequest().authenticated()` rule. The caller is always changing **their own** credential; no user id is accepted from the body (resolved from `TenantContext.currentUserId()`).
**Legacy JEX counterpart:** WSVC `comm_0400_001_u001` (action `comm_0400_001_u001_act.jsp`, popup `comm_0401_001`). Tables `USER_INFM` (`USER_PW`), `USER_PW_CHG_HIS`. Legacy ML codes `WBE1052` / `WBE1053` / `WBE1017`.

> This folder is the **primary documentation home** for Change Password on the backend. The JEX-side
> behaviour (`comm_0400_001_u001`) is the read-only functional source of truth; the implemented Spring
> code is the final source of truth for the refactor.

---

## What this feature is

Change Password lets an authenticated user rotate their own login password. The user supplies their
current password plus the new password twice (new + confirm). The backend validates in a fixed order,
rewrites `USER_INFM.USER_PW` with the new hash, and appends a `USER_PW_CHG_HIS` ledger row that backs
the "cannot reuse your last 3 passwords" rule.

A logged-in user can:

- **Change their password** — `POST /api/v1/auth/change-password` with `{ current_password, new_password, confirm_password }`.

There is no admin-reset or expiry-forced flow in this slice. Admin-driven reset lives under
Settings → Users (`domain/setting/users`); password-expiry (`USER_INFM.PW_CHG_REQ_SEQ_NO`) is mapped
but **not** wired (deferred — see [refactor-gap-analysis.md](refactor-gap-analysis.md)).

---

## Password coexistence (read this first)

`USER_INFM.USER_PW` and `USER_PW_CHG_HIS.USER_PW` store the password hash in the **legacy
`public.hash()` SHA-256 format** — `Base64(SHA-256(utf8(raw)))`, 44 characters, unsalted, deterministic.
The refactored Spring app runs **alongside** the live JEX system against the same DB, so both must
authenticate the same `USER_INFM` row.

- **Write path** ([WecmsPasswordEncoder.encode](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java)
  → [LegacyHashPasswordEncoder.encode](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java)):
  always emits the legacy SHA-256 hash — **never BCrypt**. This is the single write path for all three
  flows (Settings → Users create/reset, change-password, login is verify-only).
- **Read path** (`matches`): primarily SHA-256; BCrypt (`$2a$`/`$2b$`/`$2y$`) is tolerated only for
  leftover local/dev rows. No new BCrypt is ever written; `upgradeEncoding()` is always `false`.

This is an **APPROVED DEVIATION** vs the typical BCrypt-on-write pattern (coexistence rule, 2026-06-10).
The **OLD doc claim that login "transparently upgrades SHA-256 → BCrypt on success" is STALE** — the
current code is verify-only with no write-back. The stale claim survives in the
[LegacyHashPasswordEncoder](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java)
and [UserPwChgHis](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHis.java)
Javadoc only — corrected here and flagged in [business-flow.md](business-flow.md) +
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoint | 1 — `POST /api/v1/auth/change-password` (see [interface-specification.md](interface-specification.md)) |
| Authority | JWT only (global `anyRequest().authenticated()`); no `@menuAuthz`, no role gate — every logged-in user changes their own password |
| Caller identity | `TenantContext.currentUserId()` (signed JWT) — never a body field |
| Tables | `USER_INFM` (`USER_PW`, `PW_CHG_REQ_SEQ_NO`), `USER_PW_CHG_HIS` (PK `USER_ID + REG_SEQ_NO`) — both in the legacy inventory |
| Validation order | (1) confirm ≠ new → `WBE_CHANGE_PASSWORD_MISMATCH`; (2) current ≠ stored → `WBE2091`; (3) new reuses last 3 → `WBE_CHANGE_PASSWORD_REUSED` |
| Reuse depth | `REUSE_HISTORY_DEPTH = 3` (last 3 history rows) |
| Hashing | legacy SHA-256 base64 (44 chars) via `WecmsPasswordEncoder` / `LegacyHashPasswordEncoder` — no BCrypt write, no migration (coexistence) |
| Key generation | `USER_PW_CHG_HIS.REG_SEQ_NO` via `LegacyFunctionRepository.nextUserPwChgRegSeqNo` (`wb_get_next_val` funnel, per-user MAX+1) |
| Audit | **No** `AUDIT_LOGS_INFM` row — the history ledger `USER_PW_CHG_HIS` is the record (legacy parity) |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend layout

```
com.kosign.wecms.domain.auth
├── controller/AuthController.java            POST /api/v1/auth/change-password
├── service/ChangePasswordService.java        validation order + write + history append
├── dto/ChangePasswordRequest.java            { current_password, new_password, confirm_password }
├── entity/UserPwChgHis.java                   @Table(USER_PW_CHG_HIS), @IdClass(UserPwChgHisId)
├── entity/UserPwChgHisId.java                 composite PK { userId, regSeqNo }
└── repository/UserPwChgHisRepository.java     findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc(userId)
```

Cross-package collaborators (reused, not re-implemented):
[WecmsPasswordEncoder](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java)
+ [LegacyHashPasswordEncoder](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java) (`common/security`),
[LegacyFunctionRepository](../../../../src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java) (`common/legacy`),
[UserInfm](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java) + [UserInfmRepository](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserInfmRepository.java) (auth),
[TenantContext](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) (`common/tenant`).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end request flow + legacy parity narrative; SHA-256-on-write classified APPROVED DEVIATION; stale "upgrade to BCrypt" claim corrected |
| [functional-specification.md](functional-specification.md) | Numbered flow, 3-step validation order, reuse check (last 3), hashing scheme (SHA-256 base64 / 44 chars), error codes |
| [database-mapping.md](database-mapping.md) | `USER_INFM` (`USER_PW`, `PW_CHG_REQ_SEQ_NO`) + `USER_PW_CHG_HIS` columns → entity → DTO; last-3 read |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes, error envelope, codes |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), key methods |
| [migration-guide.md](migration-guide.md) | `comm_0400_001_u001` → `ChangePasswordService` field/behaviour mapping; ML codes `WBE1052`/`WBE1053`/`WBE1017` → refactor codes |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (mismatch / wrong-current / reuse / success) + coverage notes |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with evidence |

---

## Related & sibling modules

- **Frontend counterpart:** [wecms-web/docs/features/auth/change-password/README.md](../../../../../wecms-web/docs/features/auth/change-password/README.md)
- **Sibling auth modules:** [login](../login/README.md) · [profile](../profile/README.md) · [logout](../logout/README.md) · [session-management](../session-management/README.md)
- **Login coexistence audit (password hashing):** [docs/audits/login-legacy-compatibility-2026-06-10.md](../../../audits/legacy-parity/login-legacy-compatibility-2026-06-10.md)
- **Legacy table inventory (baseline):** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md)

---

## Open / residual items

1. **Stale Javadoc** on [LegacyHashPasswordEncoder](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java)
   ("New passwords are always written with BCrypt … transparently upgrades legacy hashes on successful
   login") and [UserPwChgHis](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHis.java)
   ("`USER_PW` stores a BCrypt hash") — **STALE / DEAD**. The live encoder writes SHA-256 and never
   upgrades. Non-blocking comment cleanup; tracked in [refactor-gap-analysis.md](refactor-gap-analysis.md).
2. **No dedicated `ChangePasswordServiceTest` on disk** — see [test-scenarios.md](test-scenarios.md) §2.
3. **`PW_CHG_REQ_SEQ_NO` / password-expiry flow** — mapped on `UserInfm` but not read; deferred
   (MISSING). See [refactor-gap-analysis.md](refactor-gap-analysis.md).

None of the residual items affect the implemented feature's parity or correctness.
