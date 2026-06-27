# Login — Functional Specification (Backend)

The implemented behaviour of the Login backend. JEX behaviour reference (read-only):
`logn_0001_001_r001` screen action → `SzonHelper.logn_2001_001_r001` service. Source of truth for the
refactor: the implemented Spring code in
[AuthService.login](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java).

Scope = the login endpoint only. `/me`, logout and change-password are separate auth modules
(cross-linked from [README.md](README.md)); this doc references them but does not specify them.

---

## 1. Operation

| Operation | HTTP | URI | Maps to JEX | Auth | Audit |
|---|---|---|---|---|---|
| Login (single-link auto-pick / multi-link chooser / finalize) | POST | `/api/v1/auth/login` | `logn_0001_001_r001` / `SzonHelper.logn_2001_001_r001` | Public | `USER_LOGIN_HIS` (SUCCESS/FAIL) |

Public endpoint — permit-all in `SecurityConfig` (login is the credential exchange that mints the JWT
every other endpoint requires).

---

## 2. Field contract

### 2.1 Request — `LoginRequest`

| Wire field (snake) | Java field | Required | Rule |
|---|---|---|---|
| `user_id` | `userId` | yes | `@NotBlank @Size(max = 100)`; trimmed in compact constructor |
| `user_pw` | `userPw` | yes | `@NotBlank @Size(max = 100)`; trimmed |
| `biz_key` | `bizKey` | no | `@Size(max = 20)` + `@Pattern("^[A-Z0-9_]+$")` (no wildcards); trimmed; blank → null |

`biz_key` is the second step of the two-call flow: omitted on the first call, supplied only after the
user picks a company from the chooser. A bean-validation failure → `WBE_VALIDATION`.

### 2.2 Response — `LoginResponse` (two shapes; `companies_available` discriminates)

**Authenticated** (single-link user, or multi-link user finalising with `biz_key`):

| Wire field | Java field | Notes |
|---|---|---|
| `access_token` | `accessToken` | `@JsonProperty("access_token")` — the one explicit wire override (OAuth-style) |
| `token_type` | `tokenType` | `"Bearer"` |
| `expires_in` | `expiresIn` | seconds (`tokenProvider.expiresInSeconds()`, default 3600) |
| `user_id` | `userId` | |
| `user_nm` | `userNm` | display name |
| `biz_key` | `bizKey` | resolved tenant |
| `lang_cd` | `langCd` | `EN`/`KO`/`KM`/`VI` from `Accept-Language` |
| `cmpn_nm` | `cmpnNm` | `BUSI_INFM.CMPN_NM` (empty string when null) |
| `auth_gr_cd` | `authGrCd` | `ADMIN` / `USER` |
| `prdt_type` | `prdtType` | verbatim `BUSI_INFM.PRDT_TYPE` letter; null when the row has none (never defaulted to `F`) |
| `tax_id` | `taxId` | verbatim `BUSI_INFM.TAX_ID`; null when unset |

**Chooser** (multi-link user, first call without `biz_key`):

| Wire field | Java field | Notes |
|---|---|---|
| `user_id` | `userId` | |
| `user_nm` | `userNm` | |
| `companies_available` | `companiesAvailable` | `CompanyOption[]` — one per active link, sorted by company name |

`CompanyOption` = `{ biz_key, cmpn_nm, auth_gr_cd, prdt_type }`. Null fields are dropped from the wire
(`@JsonInclude(NON_NULL)`), so the chooser shape never carries a spurious `access_token: null`. No JWT,
no `LST_LGN_DATE` write, no `SUCCESS` audit row is produced on the chooser branch.

---

## 3. Authentication flow (numbered)

From `AuthService.login(LoginRequest, ipAddr, langCd)`. `langCd` is resolved in the controller from the
`Accept-Language` header (`ko`→`KO`, `km`→`KM`, `vi`→`VI`, else `EN`); `ipAddr` is the first
`X-Forwarded-For` hop or `getRemoteAddr()`.

1. **Load user.** `userRepo.findById(user_id)`. Missing → FAIL row (`USER_NOT_FOUND`) + `WBE1009`.
2. **Flag gates, in order** (each writes a FAIL row first, then throws):
   - `DEL_YN = 'Y'` → `WBE1128` (`DEL_YN=Y`)
   - `ACTV_YN ≠ 'Y'` → `WBE1010` (`ACTV_YN=N`)
   - `LOCK_YN = 'Y'` → `WBE1011` (`LOCK_YN=Y`)
3. **Password.** `passwordEncoder.matches(user_pw, USER_PW)`. Mismatch → FAIL (`PW_MISMATCH`) + `WBE1009`.
4. **Verify-only — no write-back.** `USER_INFM.USER_PW` is never re-encoded or saved during login. The
   legacy `public.hash()` SHA-256 value stays intact so the live JEX system authenticates the same
   credential (coexistence rule, 2026-06-10). The former SHA-256→BCrypt upgrade was removed.
5. **All active links.** `userBusiRepo.findAllByUserIdAndDelYn(user_id, "N")`. Zero rows → FAIL
   (`NO_TENANT_LINKS`) + `WBE1009` (user not provisioned at any company).
6. **Resolve the tenant** — three branches:
   - **One link** → auto-pick it. If the request carried a `biz_key` that doesn't match → FAIL
     (`TENANT_OUT_OF_SCOPE`) + `WBE1009`.
   - **≥ 2 links, no `biz_key`** → build and **return the chooser response immediately** (no JWT, no
     `LST_LGN_DATE`, no SUCCESS audit). One `CompanyOption` per link; per-option authority from
     `BUSI_ADMN_INFM.findAllByUserId`; sorted by `cmpn_nm` (nulls last), tie-broken on `biz_key`.
   - **≥ 2 links, `biz_key` present** → find the matching link. Unknown / non-matching → FAIL
     (`TENANT_OUT_OF_SCOPE`) + `WBE1009`.
7. **Finalize — update last-login.** `selectedLink.setLstLgnDate(OffsetDateTime.now())` + `MOD_ID` →
   `userBusiRepo.save(...)`.
8. **Audit SUCCESS.** `history.record(user_id, ipAddr, SUCCESS, null)`.
9. **Company lookup.** `busiRepo.findById(bizKey)` once → `cmpnNm` (empty string when null), verbatim
   `prdtType`, verbatim `taxId`.
10. **Authority.** `busiAdmnRepo.existsByBizKeyAndUserId(bizKey, user_id)` → `ADMIN` if present, else
    `USER`.
11. **Issue JWT.** `tokenProvider.issue(userId, userNm, bizKey, langCd, authGrCd, prdtType)` → return
    `LoginResponse.authenticated(...)` with the standard tenant fields + `auth_gr_cd` + `prdt_type` +
    `tax_id`.

---

## 4. Multi-company resolution

The **operational model is one user = one company account** (per `wecms-api/CLAUDE.md`), so the normal
production path is step 6 branch one — single link → auto-pick → JWT → home, **no chooser**. The
multi-link chooser branch is a **safe generic fallback** for the rare user who genuinely has ≥ 2 active
links; do not design domain logic around its routine presence. The V73 test fixture that lit up the
chooser locally was removed by V76, so the seeded DB is one-user-one-company too.

The chooser stamps the per-company `prdt_type` into each `CompanyOption` so the frontend can show a
product label next to each option, but the frontend must **not gate behaviour** on it — product-type
security lives in the signed JWT, which only exists after a company is selected.

---

## 5. Login-history audit

[LoginHistoryService.record](../../../../src/main/java/com/kosign/wecms/domain/auth/service/LoginHistoryService.java)
runs `@Transactional(propagation = REQUIRES_NEW)` so a FAIL row persists even when the outer login
transaction rolls back. Per call it writes a `USER_LOGIN_HIS` row:

| Column | Value |
|---|---|
| `USER_ID` | the attempted user id (PK part) |
| `REG_DTM` | UTC now `yyyyMMddHHmmss` (`WB_FORMATTER_DTM14`; PK part) |
| `REG_UID` | `UUID.randomUUID()` tiebreaker (PK part — DEV `(USER_ID, REG_DTM, REG_UID)`) |
| `IP_ADDR` | client IP |
| `LGN_CHNL` | `"PC"` (`UserLoginHis.CHNL_PC`) |
| `RSLT_CD` | `"SUCCESS"` / `"FAIL"` |
| `RSLT_CONT` | reason tag on FAIL (`USER_NOT_FOUND`, `DEL_YN=Y`, `ACTV_YN=N`, `LOCK_YN=Y`, `PW_MISMATCH`, `NO_TENANT_LINKS`, `TENANT_OUT_OF_SCOPE`); `null` on SUCCESS |

The chooser branch (step 6 branch two) writes **no** row — the user has not authenticated to any
specific company yet. A **5-failure auto-lock counter** reads these FAIL rows: `LoginLockoutService`
sets `USER_INFM.LOCK_YN='Y'` once the latest 5 login attempts for the user are all failures (formerly
deferred — implemented 2026 via F-10; see [refactor-gap-analysis.md](refactor-gap-analysis.md)). The
login endpoint is also throttled by an in-memory `RateLimiter` (key `login:<ip>`).

---

## 6. JWT claims issued

[JwtTokenProvider.issue](../../../../src/main/java/com/kosign/wecms/common/security/JwtTokenProvider.java)
signs (HS256 for a 32–63-byte secret, HS512 for ≥ 64) a token carrying:

| Claim | Source | Notes |
|---|---|---|
| `sub` | `USER_ID` | subject |
| `userNm` | `USER_INFM.USER_NM` | |
| `bizKey` | resolved link `BIZ_KEY` | never client-overridable post-login |
| `langCd` | `Accept-Language` resolution | |
| `authGrCd` | `BUSI_ADMN_INFM` presence | normalized to `ADMIN` / `USER` |
| `prdtType` | verbatim `BUSI_INFM.PRDT_TYPE` | **omitted from the claim set** when null or blank |
| `iss` / `iat` / `exp` | `wecms.jwt.*` | TTL `expires-in-seconds` (default 3600) |

Token issuance / parsing / the filter chain are specified in
[jwt-authentication](../jwt-authentication/README.md); this doc only states what login stamps.

---

## 7. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success (authenticated or chooser response) |
| `WBE1009` | 200 (business) | ID or password mismatch · user not found · zero active links · `biz_key` out of scope |
| `WBE1010` | 200 | Account not activated (`ACTV_YN ≠ Y`) |
| `WBE1011` | 200 | Account locked (`LOCK_YN = Y`) |
| `WBE1128` | 200 | Account deleted (`DEL_YN = Y`) |
| `WBE_VALIDATION` | 200/400 | Bean-validation failure on the request body |
| `WBE9999` | 500 | Unexpected server error (shared catch-all) |

`BusinessException` maps to HTTP 200 with `status.error = true` (the frontend reads `status.error`, not
the HTTP code) per the global exception mapping. `WBE1401` is the **session-management** code surfaced by
`JwtAuthenticationEntryPoint` on the protected endpoints — not part of the public login response. Full
request/response shapes: [interface-specification.md](interface-specification.md).
