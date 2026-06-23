# Change Password — Source Code Index

Legacy ↔ refactor file map for the Change Password feature. JEX paths are **read-only reference**.
Frontend mirror:
[wecms-web/docs/features/auth/change-password/component-index.md](../../../../../wecms-web/docs/features/auth/change-password/component-index.md).

---

## Refactor — backend (`wecms-api`)

### Owned by `domain/auth`

| Layer | File | Key methods |
|---|---|---|
| Controller | [controller/AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java) | `changePassword(@Valid ChangePasswordRequest, HttpServletRequest)` (`@PostMapping("/change-password")`) — resolves client IP, delegates to the service, returns `ApiResponse.ok("Password changed", null)`. `clientIp(...)` reads `X-Forwarded-For` then `getRemoteAddr()`. |
| Service | [service/ChangePasswordService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/ChangePasswordService.java) | `changePassword(ChangePasswordRequest, String ipAddr)` `@Transactional` — caller resolution, eligibility re-check, 3-step validation, write `USER_PW` + append history. `isRecentlyUsed(userId, raw)` — last-3 reuse check. `REUSE_HISTORY_DEPTH = 3`. |
| Request DTO | [dto/ChangePasswordRequest.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/ChangePasswordRequest.java) | record `{ currentPassword, newPassword, confirmPassword }`, each `@NotBlank @Size(max=100)`; compact constructor trims all three |
| Entity | [entity/UserPwChgHis.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHis.java) | `@Table(USER_PW_CHG_HIS)`, `@IdClass(UserPwChgHisId)`; fields `userId`, `regSeqNo`, `userPw`, `regDtm`, `modDtm`, `ipAddr`. No `BaseEntity`. |
| Composite PK | [entity/UserPwChgHisId.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserPwChgHisId.java) | `{ userId, regSeqNo }` Serializable |
| Repository | [repository/UserPwChgHisRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserPwChgHisRepository.java) | `findTop3ByUserIdOrderByRegDtmDescRegSeqNoDesc(userId)` — last-3 read backing the reuse check |

### Reused cross-package collaborators (not re-implemented)

| Concern | File | Key methods |
|---|---|---|
| Coexistence password encoder | [common/security/WecmsPasswordEncoder.java](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java) | `encode()` → legacy SHA-256 (never BCrypt); `matches()` → SHA-256, BCrypt tolerated for leftover rows; `upgradeEncoding()` → always `false` |
| Legacy SHA-256 hash | [common/security/LegacyHashPasswordEncoder.java](../../../../src/main/java/com/kosign/wecms/common/security/LegacyHashPasswordEncoder.java) | `encode()` = `Base64(SHA-256(utf8(raw)))` (44 chars, replicates `public.hash()`); `matches()` constant-time compare. **Javadoc is STALE — says "BCrypt … transparent upgrade"; runtime is SHA-256-only.** |
| Live credential entity / repo | [entity/UserInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java) · [repository/UserInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserInfmRepository.java) | `USER_INFM` PK `USER_ID`; `isActive` / `isLocked` / `isDeleted`; `setUserPw` / `setModId` |
| `REG_SEQ_NO` funnel | [common/legacy/LegacyFunctionRepository.java](../../../../src/main/java/com/kosign/wecms/common/legacy/LegacyFunctionRepository.java) | `nextUserPwChgRegSeqNo(userId)` → `wb_get_next_val('user_pw_chg_his','reg_seq_no', userId, …)` (per-user MAX+1, V140 #27) |
| Caller identity | [common/tenant/TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | `currentUserId()` — from the signed JWT |
| Legacy datetime converter | [common/entity/LegacyDtmConverter.java](../../../../src/main/java/com/kosign/wecms/common/entity/LegacyDtmConverter.java) | `OffsetDateTime` ↔ legacy `VARCHAR(14)` `yyyyMMddHHmmss` for `REG_DTM` / `MOD_DTM` |

### Schema / config / messages

- Tables `USER_INFM`, `USER_PW_CHG_HIS` — legacy tables, in
  [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md).
- `wb_get_next_val` branch `user_pw_chg_his.reg_seq_no` (V140 #27) — in the funnel.
- Message keys in [messages.properties](../../../../src/main/resources/messages.properties) /
  `messages_en.properties`: `WBE_CHANGE_PASSWORD_MISMATCH`, `WBE_CHANGE_PASSWORD_REUSED`, `WBE2091`,
  `WBE1401`.

### Tests

No dedicated `ChangePasswordServiceTest` / `AuthControllerTest` (change-password) on disk in this pass
— see [test-scenarios.md §2](test-scenarios.md). `LegacyHashPasswordEncoder` / `WecmsPasswordEncoder`
carry hashing coverage in the auth/security tree.

---

## Refactor — frontend (`wecms-web`)

| Layer | File |
|---|---|
| Dialog | [components/layout/ChangePasswordDialog.tsx](../../../../../wecms-web/components/layout/ChangePasswordDialog.tsx) — shadcn feature dialog wired into ProfileMenu |
| Trigger | [components/layout/ProfileMenu.tsx](../../../../../wecms-web/components/layout/ProfileMenu.tsx) — change-password link |
| API client | [lib/auth/client.ts](../../../../../wecms-web/lib/auth/client.ts) — `changePassword(current, new, confirm)` → `POST /api/v1/auth/change-password` `{current_password,new_password,confirm_password}` |
| Wire type | [lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) — `ChangePasswordRequestRec { current_password, new_password, confirm_password }` |

Frontend Zod blocks new ≠ confirm before the call; backend codes consumed: `WBE2091`,
`WBE_CHANGE_PASSWORD_MISMATCH`, `WBE_CHANGE_PASSWORD_REUSED`, `WBE_VALIDATION`.

---

## Legacy JEX (`WECMS DEV/`) — read-only

| Artifact | Path |
|---|---|
| Change-password action (validation order + decrypt/hash guards) | `WECMS DEV/.../web/WEB-INF/action/jex/wabooks/comm/comm_0400_001_u001_act.jsp` (`:97-99` confirm; `:102-108` current-pw COUNT) |
| Change-password popup screen | `comm_0401_001` |
| Last-3 reuse query | `WECMS_ETC/xml/service/IDO/IDO.USER_PW_CHG_HIS_R003.xml` (`ORDER BY REG_DTM DESC LIMIT 3`) |
| Password hash function | PostgreSQL `public.hash(txt)` (`encode(digest(txt,'sha256'),'base64')`) |
| Entity / DAO | `WECMS DEV/WECMS/src/wabooks/entity/UserPwChgHis.java` · `dao/UserPwChgHisDAO.java` · `UserInfmDAO.java` |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/change-password/`
- Login coexistence audit: [docs/audits/login-legacy-compatibility-2026-06-10.md](../../../audits/login-legacy-compatibility-2026-06-10.md)
- Frontend feature folder: [wecms-web/docs/features/auth/change-password/](../../../../../wecms-web/docs/features/auth/change-password/README.md)
- Sibling auth modules: [login](../login/README.md) · [profile](../profile/README.md) · [session-management](../session-management/README.md)
