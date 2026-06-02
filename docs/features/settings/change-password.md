# Change Password (refactor slug: `change-password`)

Self-service password change for the logged-in user. Refactor port of legacy
JEX `comm_0401_001` (popup) + `comm_0400_001_u001` (action).

- **JEX spec (read-only reference):** `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0400_001_u001_act.jsp`, `comm_0401_001_view.jsp`, `IDO.USER_PW_CHG_HIS_R003.xml`, `IDO.USER_PW_CHG_HIS_C001.xml`.
- **Entry point:** Header → profile dropdown → "Change Password".

## Flow (parity with legacy)

1. **Required fields** — `currentPassword` / `newPassword` / `confirmPassword` all required. Frontend: zod (no API call on failure). Backend backstop: bean validation → `WBE_VALIDATION`. (Legacy `ValidationHelper.validateRequired`.)
2. **New == Confirm** — else `WBE_CHANGE_PASSWORD_MISMATCH` (legacy `WBE1052`). Caught first on the frontend by a zod `refine`.
3. **Current password matches the stored hash** — else `WBE2091` "Password confirmation failed." (legacy `WBE1053`; reuses the existing Login-Credentials-reveal code, same "verify the logged-in user's password" semantic).
4. **No reuse of the last 3 passwords** — else `WBE_CHANGE_PASSWORD_REUSED` (legacy `WBE1017`; `USER_PW_CHG_HIS_R003` parity). BCrypt comparison loop over the 3 newest `USER_PW_CHG_HIS` rows via the shared `PasswordEncoder`.
5. **On success** — `USER_INFM.USER_PW` rewritten with a fresh BCrypt hash (`WecmsPasswordEncoder`, same as login write path) + one `USER_PW_CHG_HIS` row appended (`REG_SEQ_NO` = per-user MAX+1, Java-side generator — no `wb_get_next_val` branch).

## Backend

- **Endpoint:** `POST /api/v1/auth/change-password` — JWT-protected (not in the SecurityConfig public list). User id is read from `TenantContext` only; never accepted from the body.
- **DTO:** `ChangePasswordRequest` (`current_password` / `new_password` / `confirm_password`).
- **Service:** `ChangePasswordService` (`@Transactional`).
- **Entity / repo / schema:** `UserPwChgHis` + `UserPwChgHisId` + `UserPwChgHisRepository`; table `USER_PW_CHG_HIS` (Flyway **V207**). PROD-active-list table. `REG_DTM`/`MOD_DTM` are `TIMESTAMPTZ` and `REG_SEQ_NO` is `BIGINT` — documented deviations from the legacy `VARCHAR(14)`/`NUMERIC` shapes (USER_LOGIN_HIS / V36 precedent).
- **Messages:** `WBE_CHANGE_PASSWORD_MISMATCH`, `WBE_CHANGE_PASSWORD_REUSED` (new); `WBE2091` reused.

## Frontend

- **API client:** `changePassword(...)` in `lib/auth/client.ts` (wire type `ChangePasswordRequestRec`); rejects with `LoginError` carrying `status.code`.
- **UI:** `components/layout/ChangePasswordDialog.tsx` — shadcn feature dialog (§3a rhythm), zod schema with translation keys, validation + success/error surfaced via the shared `useDialog()` system dialog. Double-submit guarded by `isSubmitting`; fields cleared and popup closed on success. Wired into `ProfileMenu`.
- **i18n:** `change_password.*` keys in `locales/{en,ko,vi}.json`.

## Out of scope

Password-expiry / forced-change (`PW_CHG_REQ_SEQ_NO`), 5-failure auto-lock — remain deferred per `wecms-api/CLAUDE.md`.
