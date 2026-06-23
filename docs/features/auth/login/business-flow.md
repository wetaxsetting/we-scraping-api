# Login — Business Flow (Backend)

End-to-end request flow for the login operation, with legacy JEX parity notes. Source of truth for the
refactor: the implemented Spring code in
[AuthController](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/AuthController.java)
→ [AuthService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java)
→ [LoginHistoryService](../../../../src/main/java/com/kosign/wecms/domain/auth/service/LoginHistoryService.java).
Legacy reference (read-only): `logn_0001_001_r001` → `SzonHelper.logn_2001_001_r001`.

```
Browser (Next.js LoginForm)
      │   POST /api/v1/auth/login  { user_id, user_pw, biz_key? }   (public — no JWT)
      ▼
AuthController.login
      │   ip = X-Forwarded-For[0] | getRemoteAddr()
      │   langCd = Accept-Language → EN/KO/KM/VI
      ▼
AuthService.login  @Transactional
      │
      ├─ UserInfmRepository.findById            (USER_INFM — flag gates)
      ├─ PasswordEncoder.matches                (verify-only SHA-256, no write-back)
      ├─ UserBusiConnInfmRepository.findAllByUserIdAndDelYn   (active links)
      │     ├─ 0 links  → WBE1009
      │     ├─ 1 link   → auto-pick
      │     └─ ≥2 links → chooser (no biz_key) | match (biz_key)
      ├─ UserBusiConnInfmRepository.save        (LST_LGN_DATE on finalize)
      ├─ LoginHistoryService.record  (REQUIRES_NEW)   (USER_LOGIN_HIS SUCCESS/FAIL)
      ├─ BusiInfmRepository.findById            (CMPN_NM, PRDT_TYPE, TAX_ID)
      ├─ BusiAdmnInfmRepository.existsByBizKeyAndUserId   (ADMIN | USER)
      └─ JwtTokenProvider.issue                 (sub, userNm, bizKey, langCd, authGrCd, prdtType?)
      ▼
ApiResponse<LoginResponse>   (authenticated | chooser; lowercase snake_case envelope)
```

---

## 1. Normal path — single-link auto-pick (production)

```
POST /api/v1/auth/login  { user_id, user_pw }     (no biz_key)
  → load USER_INFM (or WBE1009 + FAIL)
  → flag gates: DEL_YN→WBE1128, ACTV_YN→WBE1010, LOCK_YN→WBE1011  (each writes a FAIL row first)
  → passwordEncoder.matches(...)  (or WBE1009 + FAIL "PW_MISMATCH")   — verify-only, no write-back
  → findAllByUserIdAndDelYn = exactly one link → auto-pick that BIZ_KEY
       (if a stray biz_key was supplied and mismatches → WBE1009 + FAIL "TENANT_OUT_OF_SCOPE")
  → LST_LGN_DATE = now; save link
  → USER_LOGIN_HIS SUCCESS row
  → BUSI_INFM: cmpn_nm + verbatim prdt_type + verbatim tax_id
  → BUSI_ADMN_INFM present → ADMIN else USER
  → JwtTokenProvider.issue → LoginResponse.authenticated(...)
```

This is the **primary supported business flow** (one user = one company account). The user lands on the
dashboard directly — no chooser screen.

## 2. Exceptional path — multi-link chooser then finalize

```
Call 1:  POST { user_id, user_pw }                 (no biz_key)
  → ≥2 active links → buildChooserResponse(...)
       CompanyOption[] (biz_key, cmpn_nm, auth_gr_cd, prdt_type) — sorted by cmpn_nm, tie-broken on biz_key
  → return immediately: NO JWT, NO LST_LGN_DATE write, NO SUCCESS audit row

Call 2:  POST { user_id, user_pw, biz_key=<chosen> }
  → ≥2 links + biz_key → match the chosen link (unknown → WBE1009 + FAIL "TENANT_OUT_OF_SCOPE")
  → finalize exactly as the single-link path (steps LST_LGN_DATE → SUCCESS → JWT)
```

`AuthServiceTest.multiLink_finalizeSwitchesActivePrdtType_FthenH` proves the same credentials can finalize
to different companies (and therefore different `prdt_type`) across two calls.

## 3. Login-history (every terminal attempt)

`LoginHistoryService.record(...)` runs `@Transactional(REQUIRES_NEW)`, so the FAIL row commits even when
the outer `login` transaction rolls back on the thrown `BusinessException` — matching legacy, which
inserts the history row *before* throwing. SUCCESS rows carry `RSLT_CONT = null`; FAIL rows carry a
reason tag. The chooser branch writes no row.

---

## Legacy JEX parity (`SzonHelper.logn_2001_001_r001`)

| Aspect | Legacy `SzonHelper.logn_2001_001_r001` | Refactor `AuthService.login` | Status |
|---|---|---|---|
| Flag-gate order | DEL → ACTV → LOCK → password | identical order, identical WBE codes | **MATCH** |
| Login-history on terminal attempt | row inserted before the `WbException` is thrown | `LoginHistoryService.record` in `REQUIRES_NEW` before throw | **MATCH** |
| `LST_LGN_DATE` update on the resolved link | yes, on success | yes, on finalize (`USER_BUSI_CONN_INFM.LST_LGN_DATE`) | **MATCH** |
| Authority source | admin-master table (`BUSI_ADMN_INFM`); `USER_BUSI_CONN_INFM` has no `AUTH_GR_CD` | `BUSI_ADMN_INFM.existsByBizKeyAndUserId` → ADMIN/USER | **MATCH** |
| Product type read | `BUSI_INFM.PRDT_TYPE` | verbatim letter (null preserved, never defaulted to `F`) | **MATCH** |
| Password verification | SHA-256 `public.hash()` compare | `passwordEncoder.matches` against the same SHA-256 hash | **MATCH** |
| Password write-back on login | none (legacy never re-hashes on login) | none — **verify-only**, no write-back (coexistence) | **MATCH** (was wrongly documented as a BCrypt upgrade — corrected) |
| Tenant resolution | single configured tenant pin | per-user `USER_BUSI_CONN_INFM` lookup; single-link auto-pick is the configured-tenant pin's successor | **APPROVED DEVIATION** (one-user-one-company operational model) |
| Multi-company chooser | legacy WeCMS does not treat one user spanning companies as a normal case | chooser response retained as a safe generic fallback | **APPROVED DEVIATION** (exceptional path) |
| Session model | server-side session | stateless JWT (no server session, no refresh, no blacklist) | **APPROVED DEVIATION** (see jwt-authentication / session-management) |
| Response envelope | JEX `COMMON_HEAD` / `REC` | `{ status, data, … }` lowercase snake_case | **APPROVED DEVIATION** (CTO directive) |
| 5-failure auto-lock | legacy counts consecutive failures and locks | scaffold only (`USER_LOGIN_HIS` FAIL rows exist); counter not implemented | **MISSING** (deferred) |
| `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` post-auth gates | legacy checks extra business flags after password | not implemented | **MISSING / PARTIAL** (deferred) |
| Rate-limiting / brute-force protection | — | not implemented (mandatory before non-local deploy) | **MISSING** (deferred) |
| Identifier surface | WSVC id `logn_0001_001` | kebab slug `login` everywhere (route, package, docs) | **APPROVED DEVIATION** (slug rule) |

`/me` (session bootstrap), logout, and the password-hashing detail are **DEAD LEGACY / out of scope** for
this module — they are owned by [session-management](../session-management/README.md),
[logout](../logout/README.md), and [change-password](../change-password/README.md) respectively. Full
classification + evidence: [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## Invariants (every login call)

- **Tenant is never client-trusted.** The `biz_key` in the request is validated against the user's
  actual `USER_BUSI_CONN_INFM` rows before any token is issued — the frontend cannot spoof a tenant it
  has no link for.
- **No write-back on the password.** Login never mutates `USER_INFM.USER_PW` (coexistence).
- **No JWT on the chooser branch.** A token binds a single company; it is issued only on finalize.
- **FAIL audit survives rollback** via the `REQUIRES_NEW` login-history transaction.
