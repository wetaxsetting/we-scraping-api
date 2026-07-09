# Login — Migration Guide (JEX → Spring)

How each JEX layer of the login flow became its Spring counterpart. Legacy reference (read-only):
`logn_0001_001_r001` screen action → `SzonHelper.logn_2001_001_r001`. Parity classification +
evidence: [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen action | `logn_0001_001_r001_act.jsp` | `AuthController.login` (`POST /api/v1/auth/login`) |
| Login service | `SzonHelper.logn_2001_001_r001` | `AuthService.login(LoginRequest, ipAddr, langCd)` |
| Credential / flag gates | `USER_INFM` reads in `SzonHelper` | `UserInfmRepository.findById` + `UserInfm.isDeleted()/isActive()/isLocked()` |
| Password check | `public.hash()` SHA-256 compare | `PasswordEncoder.matches(...)` (verify-only, no write-back) |
| Tenant resolution | single configured-tenant pin | `UserBusiConnInfmRepository.findAllByUserIdAndDelYn(...)` → auto-pick / chooser / match |
| Last-login marker | `USER_BUSI_CONN_INFM.LST_LGN_DATE` update | `selectedLink.setLstLgnDate(now())` → `userBusiRepo.save(...)` |
| Login-history audit | `UserLoginHisDAO.create` (`DateTimeUtils.utcNow()` + `WB_FORMATTER_DTM14`) | `LoginHistoryService.record(...)` `@Transactional(REQUIRES_NEW)` |
| Authority | admin-master table | `BusiAdmnInfmRepository.existsByBizKeyAndUserId(...)` → `ADMIN`/`USER` |
| Product type | `BUSI_INFM.PRDT_TYPE` | verbatim letter → JWT `prdtType` claim + response `prdt_type` |
| Session token | server-side session | `JwtTokenProvider.issue(...)` — stateless HS256/HS512 JWT |
| Response envelope | JEX `COMMON_HEAD` / `REC` | `ApiResponse.ok(LoginResponse)` — `{ status, data }` lowercase snake_case |
| Entities / DAO | `wabooks.entity.{UserInfm,BusiInfm,UserBusiConnInfm,BusiAdmnInfm,UserLoginHis}` + DAOs | the matching `domain/auth/entity` + `domain/auth/repository` types |

---

## Field mapping (legacy → wire)

| Legacy source | Refactor entity field | Login wire field |
|---|---|---|
| `USER_INFM.USER_ID` | `UserInfm.userId` | `user_id` |
| `USER_INFM.USER_NM` | `UserInfm.userNm` | `user_nm` |
| `USER_INFM.USER_PW` | `UserInfm.userPw` | *(never on the wire — verified only)* |
| resolved `USER_BUSI_CONN_INFM.BIZ_KEY` | `UserBusiConnInfm.bizKey` | `biz_key` |
| `BUSI_INFM.CMPN_NM` | `BusiInfm.cmpnNm` | `cmpn_nm` |
| `BUSI_INFM.PRDT_TYPE` | `BusiInfm.prdtType` | `prdt_type` (+ JWT `prdtType` claim) |
| `BUSI_INFM.TAX_ID` | `BusiInfm.taxId` | `tax_id` |
| `BUSI_ADMN_INFM` presence | (derived) | `auth_gr_cd` (`ADMIN`/`USER`) + JWT `authGrCd` claim |
| `Accept-Language` | (controller) | `lang_cd` (+ JWT `langCd` claim) |
| issued token | — | `access_token` / `token_type` / `expires_in` |

---

## Identifier translation (slug, not WSVC id)

The refactor surface carries the kebab slug **`login`** — frontend route, Java package (`domain.auth`,
feature slug `login`), docs folder. Legacy WSVC ids (`logn_0001_001`, `logn_2001_001`) appear only in (1)
JEX file:line citations and (2) migration-traceability prose (this doc). No login identifier surface
emits a WSVC id.

---

## What changed (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| Tenant binding | single configured-tenant pin | per-user `USER_BUSI_CONN_INFM` resolution; single-link auto-pick is the production successor to the pin |
| Multi-company | one user spanning companies is not a normal case | chooser response retained as a safe generic fallback (exceptional path; V76 removed the local test fixture) |
| Password on login | none re-hashed | **verify-only**, explicitly no write-back (coexistence with the live JEX system on a shared DB) |
| Session | server-side session | stateless JWT — no server session, no refresh token; in-memory `jti` revocation denylist (logout / admin actions revoke server-side) |
| Response | `COMMON_HEAD` / `REC` | `{ status, data, timestamp?, path?, trace_id? }` lowercase snake_case (CTO directive) |
| Authority | resolved from admin tables | `BUSI_ADMN_INFM` presence stamped into the JWT `authGrCd` claim at finalize |
| ML codes | JEX `Pxxxxx` etc. | translated to `WBE1009/1010/1011/1128` + `WBI0000` in `messages*.properties` |

---

## Dead / out-of-scope legacy flows (not ported in this module)

| Legacy flow | Disposition |
|---|---|
| Server-side session creation / invalidation | **DEAD LEGACY** — replaced by stateless JWT; logout is client-side (see [logout](../logout/README.md)) |
| SHA-256 → BCrypt password upgrade on login | **DEAD LEGACY / corrected** — the refactor is verify-only, no write-back. The earlier "upgrade on login" claim in the old root doc is **stale** (the upgrade code was removed; it broke live JEX login). |
| 5-failure auto-lock counter | **IMPLEMENTED** (formerly MISSING, deferred — implemented 2026 via F-10) — `LoginLockoutService` sets `USER_INFM.LOCK_YN='Y'` after the latest 5 attempts are all failures (FAIL `USER_LOGIN_HIS` rows still written) |
| `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` post-auth gates | **MISSING / PARTIAL (deferred)** — columns mapped, gates not enforced |
| Rate-limiting / brute-force protection | **IMPLEMENTED** (formerly MISSING, deferred — implemented 2026) — in-memory `RateLimiter` throttles `POST /api/v1/auth/login` (key `login:<ip>`) + credential reveal. **Open:** in-memory limiter, per-node (durable / distributed deferred) |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification.

---

## Schema / key-gen migrations

| Item | Where |
|---|---|
| `USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_INFM` / `BUSI_ADMN_INFM` / `USER_LOGIN_HIS` schema | legacy tables, column-for-column DEV parity; auth schema landed across the V1 series + V11 (`BUSI_ADMN_INFM`) + V28 (`USER_LOGIN_HIS.REG_UID`); see `src/main/resources/db/migration/` |
| Key generation on the login path | none — `USER_LOGIN_HIS.REG_DTM`/`REG_UID` are produced in Java (UTC timestamp + `UUID`), not via `wb_get_next_val` |

Flyway is **disabled** (`ddl-auto: validate`); the schema is treated as external / pre-migrated.
