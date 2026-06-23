# Change Password — Refactor Gap Analysis (Parity Review)

Legacy JEX (`comm_0400_001_u001` / `comm_0401_001`, tables `USER_INFM` / `USER_PW_CHG_HIS`) vs. the
refactor (`change-password`). Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** ·
**PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Endpoint** | `comm_0400_001_u001_act.jsp` | `POST /api/v1/auth/change-password` | **MATCH** |
| **Required-field validation** | `ValidationHelper.validateRequired` | `@NotBlank @Size(max=100)` → `WBE_VALIDATION` | **MATCH** |
| **Validation order** | confirm → current → reuse | confirm → current → reuse (`ChangePasswordService`) | **MATCH** |
| **New-vs-confirm check** | `:97-99` → `WBE1052` | step 1 → `WBE_CHANGE_PASSWORD_MISMATCH` | **MATCH** |
| **Current-password check** | `:102-108` COUNT `user_pw = hash(?)` → `WBE1053` | `matches(...)` → `WBE2091` | **MATCH** (code re-mapped) |
| **Reuse depth** | last **3** (`USER_PW_CHG_HIS_R003`, `WBE1017`) | `REUSE_HISTORY_DEPTH = 3` → `WBE_CHANGE_PASSWORD_REUSED` | **MATCH** |
| **Reuse comparison** | SQL hash compare on last-3 rows | `findTop3...` + `passwordEncoder.matches` per row | **MATCH** |
| **Write `USER_INFM.USER_PW`** | `UserInfmDAO.patch` | `userRepo.save` with new hash + `MOD_ID` | **MATCH** |
| **Append `USER_PW_CHG_HIS`** | `UserPwChgHisDAO.create` | `historyRepo.save(UserPwChgHis)` | **MATCH** |
| **`REG_SEQ_NO` key-gen** | `SeqHelper` → `wb_get_next_val` | `LegacyFunctionRepository.nextUserPwChgRegSeqNo` (funnel, V140 #27) | **MATCH** |
| **Audit-log row** | none (history ledger is the record) | none — no `AUDIT_LOGS_INFM` write | **MATCH** |
| **Hashing — SHA-256 on write (not BCrypt)** | `public.hash()` SHA-256 | `WecmsPasswordEncoder.encode` → SHA-256, never BCrypt | **APPROVED DEVIATION** (coexistence) |
| **Caller identity** | server session | `TenantContext.currentUserId()` (signed JWT), no body id | **APPROVED DEVIATION** (stronger / JWT) |
| **Error-code values** | `WBE1052` / `WBE1053` / `WBE1017` | `WBE_CHANGE_PASSWORD_MISMATCH` / `WBE2091` / `WBE_CHANGE_PASSWORD_REUSED` | **APPROVED DEVIATION** (ML-code translation rule) |
| **SHA-256 → BCrypt upgrade on change/login** | (OLD doc claim) | not implemented — `encode()` SHA-256-only, `upgradeEncoding()` false | **DEAD LEGACY** (GAP-1, stale claim) |
| **Password-expiry forced change (`PW_CHG_REQ_SEQ_NO`)** | column-driven expiry | mapped but unread; no expiry flow | **MISSING** (GAP-2) |
| **Dedicated automated test** | — | none on disk for `ChangePasswordService` / controller | **MISSING** (GAP-3) |

---

## Specially-verified gaps

### GAP-1 — "SHA-256 → BCrypt upgrade" is STALE (DEAD LEGACY)

The OLD root-doc / Javadoc claim that the app "transparently upgrades SHA-256 → BCrypt on successful
login" (and by extension on password write) is **WRONG** for the current code:

- [WecmsPasswordEncoder.encode](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java)
  always emits the legacy SHA-256 hash; `upgradeEncoding()` always returns `false`; login is
  verify-only with no write-back (coexistence rule, 2026-06-10).
- The stale wording survives in **comments only** —
  [LegacyHashPasswordEncoder](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java)
  Javadoc ("New passwords are always written with BCrypt … transparently upgrades legacy hashes on
  successful login") and [UserPwChgHis](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHis.java)
  Javadoc ("`USER_PW` stores a BCrypt hash").

**Status: DEAD LEGACY** — the BCrypt-upgrade path does not exist at runtime. Recommend a one-line
Javadoc fix on both files in the next stabilisation pass. Does not affect behaviour or parity. The
SHA-256-on-write choice itself is an **APPROVED DEVIATION** (see the matrix), not a gap.

### GAP-2 — Password-expiry / `PW_CHG_REQ_SEQ_NO` (MISSING)

`USER_INFM.PW_CHG_REQ_SEQ_NO` is mapped on the entity but never read. The legacy column drives a
password-expiry / forced-change flow; the refactor does not implement it (consistent with the
workspace Known-Issues entry "PW_CHG_REQ_SEQ_NO unused — password-expiry flow deferred"). Change
Password works without it; expiry enforcement is a separate, deferred feature. **Status: MISSING /
deferred** — wire to `/auth/change-password` before any non-local deploy. Owner: backend + PO.

### GAP-3 — No dedicated automated test (MISSING)

No `ChangePasswordServiceTest` or `AuthController` change-password `@WebMvcTest` is on disk in this
pass. The hashing primitives (`LegacyHashPasswordEncoder` / `WecmsPasswordEncoder`) have coverage in
the security tree, but the validation-order branches, the reuse check, and the `WBE2091` /
`WBE_CHANGE_PASSWORD_*` paths are not asserted. **Status: MISSING** — see
[test-scenarios.md §2](test-scenarios.md) for the recommended coverage.

---

## Migration status

```
Refactor Status: complete for the in-scope feature surface (change own password);
                 password-expiry (GAP-2) deferred; dedicated tests (GAP-3) absent.
```

Evidence for closed items:

- **Endpoint implemented** end-to-end ([AuthController.changePassword](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java)
  → [ChangePasswordService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/ChangePasswordService.java));
  frontend wired (`<ChangePasswordDialog>`, no mock fallback).
- **Validation order MATCH** — confirm → current → reuse, codes `WBE_CHANGE_PASSWORD_MISMATCH` /
  `WBE2091` / `WBE_CHANGE_PASSWORD_REUSED`.
- **Reuse depth = 3 MATCH** — `REUSE_HISTORY_DEPTH = 3` confirmed against legacy
  `USER_PW_CHG_HIS_R003` (last 3).
- **SHA-256-on-write APPROVED DEVIATION** — `WecmsPasswordEncoder.encode` → legacy `public.hash()`
  format; no BCrypt, no migration (coexistence).
- **`REG_SEQ_NO` through the funnel** — `LegacyFunctionRepository.nextUserPwChgRegSeqNo` (V140 #27).

Residual (non-blocking, tracked):
1. GAP-1 — stale "BCrypt upgrade" Javadoc cleanup on the two encoders + `UserPwChgHis` entity.
2. GAP-2 — password-expiry / `PW_CHG_REQ_SEQ_NO` flow deferred.
3. GAP-3 — add `ChangePasswordServiceTest` + a `@WebMvcTest` controller test.

None of the residual items affect the implemented feature's parity or correctness.
