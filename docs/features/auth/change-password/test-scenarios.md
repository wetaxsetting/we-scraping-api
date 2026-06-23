# Change Password — Test Scenarios (Backend)

SIT/UAT scenarios + coverage map. Frontend scenarios:
[wecms-web/docs/features/auth/change-password/test-scenarios.md](../../../../../wecms-web/docs/features/auth/change-password/test-scenarios.md).

---

## 1. SIT / UAT scenarios

### Validation order

| # | Scenario | Expected |
|---|---|---|
| V1 | Any of the 3 fields blank | `WBE_VALIDATION` (bean validation, before the service) |
| V2 | Any field > 100 chars | `WBE_VALIDATION` |
| V3 | All three rules would fail at once | first failing code surfaces — order: `WBE_VALIDATION` → `WBE_CHANGE_PASSWORD_MISMATCH` → `WBE2091` → `WBE_CHANGE_PASSWORD_REUSED` |

### Mismatch (new ≠ confirm)

| # | Scenario | Expected |
|---|---|---|
| M1 | `new_password` ≠ `confirm_password` | `WBE_CHANGE_PASSWORD_MISMATCH`; no write; checked **before** the current-password check |
| M2 | `new` and `confirm` differ only by leading/trailing spaces | both are trimmed first — equal trimmed values pass M1, differing values fail M1 |

### Wrong current password

| # | Scenario | Expected |
|---|---|---|
| C1 | `current_password` does not match stored hash | `WBE2091` "Password confirmation failed."; no write; checked **after** mismatch, **before** reuse |
| C2 | `current_password` correct, `new`==`confirm`, new not reused | proceeds to write |

### Reuse (last 3)

| # | Scenario | Expected |
|---|---|---|
| R1 | New password equals the most-recent `USER_PW_CHG_HIS` hash | `WBE_CHANGE_PASSWORD_REUSED` |
| R2 | New password equals the 2nd or 3rd most-recent history hash | `WBE_CHANGE_PASSWORD_REUSED` (depth = 3) |
| R3 | New password equals the 4th-most-recent (older than 3) | passes — depth is exactly 3 |
| R4 | User with < 3 history rows (or none) | reuse check passes (nothing to collide with) |
| R5 | Reuse compared against a legacy-imported (SHA-256) history row | matches deterministically — same plaintext → same hash |

### Success

| # | Scenario | Expected |
|---|---|---|
| S1 | Valid current + new==confirm + not reused | 200 `{ status:{code:WBI0000,message:"Password changed"}, data:null }` |
| S2 | After S1 — `USER_INFM.USER_PW` rewritten | new value = legacy SHA-256 base64 (44 chars); `MOD_ID` = caller |
| S3 | After S1 — `USER_PW_CHG_HIS` appended | one row: `USER_ID`=caller, `REG_SEQ_NO`=per-user MAX+1, `USER_PW`=same hash as `USER_INFM`, `IP_ADDR` set, `REG_DTM`/`MOD_DTM`=now |
| S4 | After S1 — login with the new password | `AuthService.login` succeeds (verify-only, SHA-256) |
| S5 | After S1 — live JEX login with the new password | succeeds on the shared DB (coexistence — SHA-256, not BCrypt) |
| S6 | Transaction atomicity | if the history append fails, the `USER_INFM` write rolls back (single `@Transactional`) |

### Authentication / eligibility

| # | Scenario | Expected |
|---|---|---|
| A1 | No / invalid / expired JWT | 401 `WBE1401` (via `JwtAuthenticationEntryPoint`, before the controller) |
| A2 | Valid JWT but caller deleted / inactive / locked since issue | `WBE1401` (service eligibility re-check, fail closed) |
| A3 | Request body cannot name another user | only the caller's own credential changes (no user id in the body) |

---

## 2. Coverage notes

- **No dedicated `ChangePasswordServiceTest` / `AuthController` change-password test on disk** in this
  pass. The slice currently leans on the security-tree coverage of its reused collaborators:
  - `LegacyHashPasswordEncoder` — SHA-256 base64 round-trip + 44-char output (security tests).
  - `WecmsPasswordEncoder` — write emits SHA-256, `matches` tolerates BCrypt, `upgradeEncoding`=false.
  - `LegacyFunctionRepository.nextUserPwChgRegSeqNo` — funnel branch behaviour (legacy-function tests).

  **Recommended additions** (GAP-3 in [refactor-gap-analysis.md](refactor-gap-analysis.md)):
  - `ChangePasswordServiceTest` (Mockito) asserting the validation **order** (M1 before C1 before R1),
    the reuse check at depth 3 (R2/R3 boundary), the two writes (`USER_INFM` + `USER_PW_CHG_HIS` with
    funneled `REG_SEQ_NO`), and the `WBE1401` eligibility fail-closed path (A2).
  - A `@WebMvcTest(AuthController.class)` test asserting the lowercase snake_case request shape
    (`current_password` / `new_password` / `confirm_password`), the 200 success envelope, and the
    business-error envelope for `WBE2091` / `WBE_CHANGE_PASSWORD_*`.

- **Cross-system coexistence (S5)** can only be confirmed against a shared JEX DB — change the password
  via the refactor, then authenticate the same `USER_INFM` row through the live JEX login (or
  `SELECT user_pw = public.hash(?)` read-only via MCP). Cannot be exercised headless from a unit test.

- **Reuse vs legacy-imported rows (R5)** relies on the legacy SHA-256 hash being deterministic; a unit
  test can seed a known SHA-256 base64 string and assert `matches` returns true for the same plaintext.
