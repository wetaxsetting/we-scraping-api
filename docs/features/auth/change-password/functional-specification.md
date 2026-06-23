# Change Password — Functional Specification (Backend)

The implemented behaviour of the Change Password backend. JEX behaviour reference (read-only):
`comm_0400_001_u001_act.jsp` (popup `comm_0401_001`) + `IDO.USER_PW_CHG_HIS_R003.xml`. Implementation
source of truth: [ChangePasswordService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/ChangePasswordService.java).

---

## 1. Operation

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | Change own password | POST | `/api/v1/auth/change-password` | `comm_0400_001_u001` | No (history ledger only) |

The endpoint changes **only the caller's own** credential. The user id is taken from
`TenantContext.currentUserId()` (the signed JWT), never from the request body.

---

## 2. Numbered flow

`ChangePasswordService.changePassword(ChangePasswordRequest req, String ipAddr)` —
`@Transactional`:

1. **Resolve the caller.** `userId = TenantContext.currentUserId()`. Null → `WBE1401` (fail closed —
   the JWT filter should already have rejected an unauthenticated request before the controller).
2. **Re-check eligibility.** Load `USER_INFM` by `userId`. Missing, or `DEL_YN='Y'` / `ACTV_YN='N'` /
   `LOCK_YN='Y'` → `WBE1401` (the bound user is no longer eligible).
3. **(Validation 1) Confirmation match.** `new_password` must equal `confirm_password`, else
   `WBE_CHANGE_PASSWORD_MISMATCH` (legacy `WBE1052`, `comm_0400_001_u001_act.jsp:97-99`).
4. **(Validation 2) Current password.** `passwordEncoder.matches(current_password, USER_INFM.USER_PW)`
   must be `true`, else `WBE2091` "Password confirmation failed." (legacy `WBE1053` + the
   `USER_INFM WHERE user_pw = hash(?)` COUNT check at `comm_0400_001_u001_act.jsp:102-108`).
5. **(Validation 3) Reuse check.** The new password must not match any of the **last 3**
   `USER_PW_CHG_HIS` rows, else `WBE_CHANGE_PASSWORD_REUSED` (legacy `WBE1017`,
   `IDO.USER_PW_CHG_HIS_R003.xml`). See §4.
6. **Hash + write the live credential.** `encoded = passwordEncoder.encode(new_password)` (legacy
   SHA-256 base64). Set `USER_INFM.USER_PW = encoded`, `MOD_ID = userId`; `userRepo.save`.
7. **Append the history row.** Insert `USER_PW_CHG_HIS` with `USER_ID = userId`,
   `REG_SEQ_NO` from `LegacyFunctionRepository.nextUserPwChgRegSeqNo(userId)` (per-user MAX+1 via the
   `wb_get_next_val` funnel), `USER_PW = encoded`, `IP_ADDR = ipAddr`, `REG_DTM = MOD_DTM = now`.
8. Return `200` with `{ status, data: null }` ("Password changed").

> **Bean-validation precedes the service.** All three fields are `@NotBlank @Size(max=100)` on
> [ChangePasswordRequest](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/ChangePasswordRequest.java);
> a blank or oversized field is rejected as `WBE_VALIDATION` before the service body runs (legacy
> `ValidationHelper.validateRequired`). Each field is trimmed in the record's compact constructor.

---

## 3. Validation order (matters)

The order is **fixed** and mirrors legacy exactly. A request that violates more than one rule surfaces
the **first** failing code:

```
WBE_VALIDATION            ← bean validation: any of the 3 fields blank / > 100 chars (pre-service)
        │
WBE_CHANGE_PASSWORD_MISMATCH   ← (1) new_password != confirm_password   (legacy WBE1052)
        │
WBE2091                        ← (2) current_password != stored hash     (legacy WBE1053)
        │
WBE_CHANGE_PASSWORD_REUSED     ← (3) new_password reuses last 3 history  (legacy WBE1017)
        │
        ▼  all pass → write USER_PW + append USER_PW_CHG_HIS → 200
```

---

## 4. Password-history reuse check (last 3)

```java
private static final int REUSE_HISTORY_DEPTH = 3;

historyRepo.findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc(userId)   // newest first, ≤ 3 rows
    .stream().limit(REUSE_HISTORY_DEPTH)
    .map(UserPwChgHis::getUserPw)
    .anyMatch(stored -> passwordEncoder.matches(rawNewPassword, stored));
```

- The candidate plaintext is matched against the **hash** of each of the 3 most-recent rows via the
  shared `PasswordEncoder` (so both SHA-256 and any leftover-BCrypt history rows are honoured).
- Because the legacy SHA-256 hash is **deterministic**, history rows imported from legacy DEV compare
  cleanly against a refactor-computed hash of the same plaintext.
- Fewer than 3 rows (or none) → no reuse, the check passes.
- Mirrors legacy `IDO.USER_PW_CHG_HIS_R003.xml` (`ORDER BY REG_DTM DESC LIMIT 3`); the legacy code
  raised `WBE1017` on a hit. `REUSE_HISTORY_DEPTH = 3` is a **MATCH** to the legacy depth.

---

## 5. Password hashing scheme

| Aspect | Value |
|---|---|
| Algorithm | SHA-256, unsalted |
| Encoding | Base64 |
| Java | `Base64.encode(SHA-256(UTF-8 bytes of raw))` |
| Output length | **44 characters** (32 bytes × 4/3, base64-padded) |
| Determinism | deterministic — equal plaintexts yield equal hashes |
| Legacy parity | byte-identical to PostgreSQL `public.hash(txt) = encode(digest(txt,'sha256'),'base64')` |
| Write path | [WecmsPasswordEncoder.encode](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java) → [LegacyHashPasswordEncoder.encode](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java) — **always SHA-256, never BCrypt** |
| Read path | `matches` — SHA-256 (constant-time compare); BCrypt tolerated only for leftover local/dev rows |
| Upgrade | `upgradeEncoding()` always `false` — change-password never silently re-encodes another field |

Both `USER_INFM.USER_PW` and `USER_PW_CHG_HIS.USER_PW` receive the **same** encoded value on each
change, so the live credential and the freshly-appended history row are identical.

**Coexistence (APPROVED DEVIATION):** writing SHA-256 (not BCrypt) is intentional so the live JEX
system can authenticate the same credential against the shared DB. Writing BCrypt would lock the user
out of legacy JEX. See [business-flow.md](business-flow.md) §"Legacy parity & the stale BCrypt claim".

---

## 6. Authorization

- **JWT only.** No `@menuAuthz` gate, no role gate — the endpoint is reachable by every authenticated
  user under the global `anyRequest().authenticated()` rule in `SecurityConfig`. There is no permission
  slug for self-service password change.
- Missing/invalid JWT is rejected by `JwtAuthenticationEntryPoint` (401) before the controller; the
  service still re-checks `TenantContext.currentUserId()` and fails closed with `WBE1401` if somehow
  reached unauthenticated.
- No body field can name a different user — the credential changed is always the caller's own.

---

## 7. Error surface

| Code | HTTP | Meaning | Legacy |
|---|---|---|---|
| `WBI0000` | 200 | Success ("Password changed") | — |
| `WBE_CHANGE_PASSWORD_MISMATCH` | 200 (business) | New password ≠ confirmation | `WBE1052` |
| `WBE2091` | 200 (business) | Current password ≠ stored hash ("Password confirmation failed.") | `WBE1053` |
| `WBE_CHANGE_PASSWORD_REUSED` | 200 (business) | New password reuses one of the last 3 | `WBE1017` |
| `WBE1401` | 200 (business) / 401 | Unauthenticated / session no longer valid / user no longer eligible | (session) |
| `WBE_VALIDATION` | 200/400 | Bean-validation failure (blank / > 100 chars) | `ValidationHelper.validateRequired` |
| `WBE9999` | 500 | Unexpected server error (shared) | — |

`WBE1401` is thrown as a `BusinessException` from inside the service (→ 200 envelope per the global
handler); the JWT entry-point path returns 401 with the same code for a missing/invalid token before
the controller is reached. Full request/response shapes: [interface-specification.md](interface-specification.md).
