# Login — Source Code Index


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

Every backend file the login flow touches, with full path, one-line role, and key methods. JEX paths are
read-only reference. Frontend mirror:
[wecms-web/docs/features/auth/login/component-index.md](../../../../../wecms-web/docs/features/auth/login/component-index.md).

All refactor paths are under `wecms-api/src/main/java/com/kosign/wecms/`.

---

## Refactor — backend (`wecms-api`)

### Owned by `domain/auth` (login slice)

| Layer | File | Role | Key methods |
|---|---|---|---|
| Controller | [domain/auth/controller/AuthController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java) | `@RequestMapping("/api/v1/auth")`; resolves client IP + `Accept-Language`→`LangCd`, delegates to the service | `login(LoginRequest, acceptLanguage, servletReq)`; `clientIp(...)`; `resolveLangCd(...)` (also hosts `me()`/`logout()`/`changePassword()` for other modules) |
| Service | [domain/auth/service/AuthService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java) | Port of `SzonHelper.logn_2001_001_r001`; flag gates, multi-company resolution, JWT issuance | `login(LoginRequest, ipAddr, langCd)` `@Transactional`; `buildChooserResponse(...)` (private); `me()` (session-management) |
| Service | [domain/auth/service/LoginHistoryService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/LoginHistoryService.java) | `USER_LOGIN_HIS` writer; survives outer rollback | `record(userId, ipAddr, rsltCd, rsltCont)` `@Transactional(REQUIRES_NEW)` |

### DTOs (`domain/auth/dto`)

| DTO | File | Role |
|---|---|---|
| `LoginRequest` | [dto/LoginRequest.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/LoginRequest.java) | request record — `userId` (`@NotBlank @Size 100`), `userPw` (`@NotBlank @Size 100`), `bizKey` (`@Size 20` + `@Pattern ^[A-Z0-9_]+$`); compact ctor trims + nulls blank `bizKey` |
| `LoginResponse` | [dto/LoginResponse.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/LoginResponse.java) | response record (`@JsonInclude(NON_NULL)`); `accessToken` is `@JsonProperty("access_token")`; factories `authenticated(...)` + `chooser(...)` |
| `CompanyOption` | [dto/CompanyOption.java](../../../../src/main/java/com/kosign/wecms/domain/auth/dto/CompanyOption.java) | one chooser row — `bizKey`, `cmpnNm`, `authGrCd`, `prdtType` |

### Entities (`domain/auth/entity`)

| Entity | File | Table / PK | Login role |
|---|---|---|---|
| `UserInfm` | [entity/UserInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserInfm.java) | `USER_INFM` / `USER_ID` | credential + flag gates; helpers `isActive()`/`isLocked()`/`isDeleted()` |
| `BusiInfm` | [entity/BusiInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java) | `BUSI_INFM` / `BIZ_KEY` | `CMPN_NM`, `PRDT_TYPE`, `TAX_ID` |
| `UserBusiConnInfm` | [entity/UserBusiConnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfm.java) | `USER_BUSI_CONN_INFM` / `(USER_ID, BIZ_KEY)` | active links + `LST_LGN_DATE` write |
| `UserBusiConnInfmId` | [entity/UserBusiConnInfmId.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserBusiConnInfmId.java) | composite key | — |
| `BusiAdmnInfm` | [entity/BusiAdmnInfm.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiAdmnInfm.java) | `BUSI_ADMN_INFM` / `(BIZ_KEY, USER_ID)` | authority source (presence ⇒ ADMIN) |
| `BusiAdmnInfmId` | [entity/BusiAdmnInfmId.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiAdmnInfmId.java) | composite key | — |
| `UserLoginHis` | [entity/UserLoginHis.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserLoginHis.java) | `USER_LOGIN_HIS` / `(USER_ID, REG_DTM, REG_UID)`; **no `BaseEntity`** | audit row; constants `RSLT_SUCCESS`/`RSLT_FAIL`/`CHNL_PC` |
| `UserLoginHisId` | [entity/UserLoginHisId.java](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/UserLoginHisId.java) | composite key | — |

### Repositories (`domain/auth/repository`)

| Repository | File | Login-relevant methods |
|---|---|---|
| `UserInfmRepository` | [repository/UserInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserInfmRepository.java) | `findById(userId)` (inherited); `existsByUserId(...)` (Settings>Users) |
| `BusiInfmRepository` | [repository/BusiInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiInfmRepository.java) | `findById(bizKey)` + `findAllById(bizKeys)` (inherited; chooser); `findByDelYnAndBusiSttsAndPrdtTypeIn(...)` (batch fan-out — not login) |
| `UserBusiConnInfmRepository` | [repository/UserBusiConnInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserBusiConnInfmRepository.java) | `findAllByUserIdAndDelYn(userId, "N")` (all links); `findByUserIdAndBizKeyAndDelYn(...)` (finalize / `/me`); `save(...)` (`LST_LGN_DATE`) |
| `BusiAdmnInfmRepository` | [repository/BusiAdmnInfmRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiAdmnInfmRepository.java) | `existsByBizKeyAndUserId(...)` (finalize authority); `findAllByUserId(...)` (chooser per-option authority) |
| `UserLoginHisRepository` | [repository/UserLoginHisRepository.java](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/UserLoginHisRepository.java) | `save(...)` (inherited) |

### Reused cross-package collaborators (not re-implemented)

| Concern | File | Package |
|---|---|---|
| JWT issue / parse / TTL | [common/security/JwtTokenProvider.java](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java) (`issue(userId, userNm, bizKey, langCd, authGrCd, prdtType)`, `expiresInSeconds()`) | `common/security` |
| Password verify-only | `PasswordEncoder` bean → [common/security/WecmsPasswordEncoder.java](../../../../src/main/java/com/kosign/wecms/common/security/WecmsPasswordEncoder.java) (`matches(...)`; `upgradeEncoding()` → `false`) — hashing detail in [change-password](../change-password/README.md) | `common/security` |
| Request-scoped tenant principal | [common/tenant/TenantContext.java](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) (`AUTH_GR_ADMIN` / `AUTH_GR_USER`) | `common/tenant` |
| Locale resolution | [common/enums/LangCd.java](../../../../src/main/java/com/kosign/wecms/common/enums/LangCd.java) | `common/enums` |
| Error envelope | [common/exception/BusinessException.java](../../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) + [GlobalExceptionHandler.java](../../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) | `common/exception` |
| Menu grants (used by `/me`, not login) | [domain/auth/service/MenuAuthorizationService.java](../../../../src/main/java/com/kosign/wecms/domain/auth/service/MenuAuthorizationService.java) | `domain/auth` (menu-authorization) |

### Schema / config

- Tables `USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_INFM`, `BUSI_ADMN_INFM`, `USER_LOGIN_HIS` — legacy
  tables in [legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md). The auth
  schema landed across the V1 series + later corrective migrations (`BUSI_ADMN_INFM` with the V11
  bootstrap group); `USER_LOGIN_HIS.REG_UID` was added by the V28 parity pass. Cite the table + see
  `src/main/resources/db/migration/` rather than a fixed version number.
- Config: `wecms.jwt.secret` (env `WECMS_JWT_SECRET`, ≥ 32 bytes), `wecms.jwt.expires-in-seconds`
  (default 3600), `wecms.jwt.issuer`.
- No `wb_get_next_val` funnel on the login path — `USER_LOGIN_HIS` keys are Java-side (timestamp + UUID).

### Tests

| Test | File | Covers |
|---|---|---|
| `AuthServiceTest` | [src/test/java/com/kosign/wecms/domain/auth/service/AuthServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/auth/service/AuthServiceTest.java) | Mockito unit test — single-link F/M/H + null/`W` prdtType, verify-only no write-back, chooser per-option, finalize switch, flag-gate paths, `/me` (session-management scope) |

A `@WebMvcTest` controller test asserting the wire shape is **MISSING** — see
[test-scenarios.md](test-scenarios.md).

---

## Legacy JEX (`WECMS DEV/`) — read-only reference

| Artifact | Path |
|---|---|
| Login screen action | `…/web/WEB-INF/action/jex/wabooks/logn/logn_0001_001_r001_act.jsp` |
| Login service helper | `WECMS DEV/WECMS/src/wabooks/helper/SzonHelper.java` (`logn_2001_001_r001`) |
| Login-history DAO | `WECMS DEV/WECMS/src/wabooks/dao/UserLoginHisDAO.java` (`create` → `DateTimeUtils.utcNow()` + `WB_FORMATTER_DTM14`) |
| Entities | `WECMS DEV/WECMS/src/wabooks/entity/{UserInfm, BusiInfm, UserBusiConnInfm, BusiAdmnInfm, UserLoginHis}.java` |

---

## Documentation

- Backend feature folder (this): `wecms-api/docs/features/auth/login/`
- Sibling auth modules: [session-management](../session-management/README.md) · [logout](../logout/README.md) · [change-password](../change-password/README.md) · [jwt-authentication](../jwt-authentication/README.md) · [authorization](../authorization/README.md) · [menu-authorization](../menu-authorization/README.md) · [profile](../profile/README.md)
- Frontend feature folder: [wecms-web/docs/features/auth/login/README.md](../../../../../wecms-web/docs/features/auth/login/README.md)
- JEX functional reference: [docs/jex/features/login-wecms.md](../../../../../docs/jex/features/login-wecms.md)
