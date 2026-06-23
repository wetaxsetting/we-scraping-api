# Login — Refactor Gap Analysis (Parity Review)

Legacy JEX (`logn_0001_001_r001` → `SzonHelper.logn_2001_001_r001`, tables `USER_INFM` /
`USER_BUSI_CONN_INFM` / `BUSI_INFM` / `BUSI_ADMN_INFM` / `USER_LOGIN_HIS`) vs. the refactor (`login`).
Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Flag-gate order** | DEL → ACTV → LOCK → password | identical order + WBE codes (`WBE1128`/`WBE1010`/`WBE1011`/`WBE1009`) | **MATCH** |
| **Password verification** | `public.hash()` SHA-256 compare | `PasswordEncoder.matches(...)` against the same SHA-256 hash | **MATCH** |
| **Password write-back on login** | none | none — **verify-only**, no re-encode/save (coexistence) | **APPROVED DEVIATION** (GAP-1 — corrects the stale "BCrypt upgrade" claim) |
| **Login-history audit** | row inserted before `WbException` | `LoginHistoryService.record` in `REQUIRES_NEW` before throw; SUCCESS + FAIL | **MATCH** |
| **`LST_LGN_DATE` update** | on success, on the resolved link | `USER_BUSI_CONN_INFM.LST_LGN_DATE` on finalize | **MATCH** |
| **Authority resolution** | admin-master table (`USER_BUSI_CONN_INFM` has no `AUTH_GR_CD`) | `BUSI_ADMN_INFM.existsByBizKeyAndUserId` → ADMIN/USER | **MATCH** |
| **Product type read** | `BUSI_INFM.PRDT_TYPE` | verbatim letter (null preserved, never defaulted to `F`) → JWT claim + wire | **MATCH** |
| **Tenant resolution** | single configured-tenant pin | per-user `USER_BUSI_CONN_INFM` lookup; single-link auto-pick | **APPROVED DEVIATION** (one-user-one-company model) |
| **Multi-company chooser** | not a normal case in legacy WeCMS | chooser response retained as a safe generic fallback (GAP-2) | **APPROVED DEVIATION** (exceptional path — chooser EXISTS) |
| **Session model** | server-side session | stateless JWT (no server session) | **APPROVED DEVIATION** |
| **Response envelope** | `COMMON_HEAD` / `REC` | `{ status, data, … }` lowercase snake_case | **APPROVED DEVIATION** (CTO directive) |
| **Identifier surface** | WSVC id `logn_0001_001` | kebab slug `login` everywhere | **APPROVED DEVIATION** (slug rule) |
| **SHA-256 → BCrypt login upgrade** | (old refactor build had this) | removed — verify-only | **DEAD LEGACY** (GAP-1) |
| **Server-side session invalidation** | yes | not ported (logout is client-side) | **DEAD LEGACY** (owned by [logout](../logout/README.md)) |
| **5-failure auto-lock counter** | locks after 5 consecutive failures | `USER_LOGIN_HIS` FAIL scaffold only; counter not implemented | **MISSING** (GAP-3, deferred) |
| **`NTNL_CD` post-auth gate** | legacy checks national-code after password | not enforced (column mapped, unused) | **MISSING** (GAP-4, deferred) |
| **`CHNL_TYPE` post-auth gate** | legacy checks channel type | not modelled / enforced | **MISSING** (GAP-4, deferred) |
| **`BUSI_STTS` post-auth gate** | legacy checks company status after password | column mapped on `BusiInfm`, not enforced at login | **PARTIAL** (GAP-4, deferred) |
| **Rate-limiting / brute-force protection** | — | not implemented | **MISSING** (GAP-5, deferred — mandatory before non-local deploy) |

---

## Specially-verified gaps

### GAP-1 — Verify-only password, no write-back (APPROVED DEVIATION; corrects a stale fact)

The old root doc (`../login-auth.md`) stated login "upgrades SHA-256 → BCrypt on success". This is
**WRONG / STALE**. The current `AuthService.login` is **verify-only**: `USER_INFM.USER_PW` is never
re-encoded or saved during login, so the live JEX system keeps authenticating the same row on a shared DB
(coexistence rule, 2026-06-10). The former transparent upgrade was removed because it mutated live data
and broke JEX login. `AuthServiceTest.successfulLogin_isVerifyOnly_neverWritesUserPw` proves
`passwordEncoder.encode(...)`, `upgradeEncoding(...)`, and `userRepo.save(...)` are never called and the
stored legacy SHA-256 hash is left intact. **Status: APPROVED DEVIATION** (verify-only by design); the
"BCrypt upgrade" wording is **DEAD LEGACY** and must not be propagated. Password-hashing detail lives in
[change-password](../change-password/README.md).

### GAP-2 — Multi-company chooser exists (APPROVED DEVIATION; corrects a stale fact)

The old root doc / login-UI note claimed there is "no BIZ_KEY chooser". This is **WRONG** for the
multi-link fallback. The backend builds a chooser response (`LoginResponse.chooser(...)` +
`CompanyOption[]`) when a user has ≥ 2 active links and supplies no `biz_key`, and the frontend renders a
`CompanyChooser`. It is an **exceptional / test-demo path** — the operational model is one user = one
company — but the chooser is real, tested
(`AuthServiceTest.multiLink_firstCall_chooserOptionsCarryPerCompanyPrdtType`), and must be documented as
present. **Status: APPROVED DEVIATION** (safe generic fallback, not a routine UX surface).

### GAP-3 — 5-failure auto-lock counter (MISSING)

Legacy locks a user after five consecutive failed logins. The refactor writes a FAIL `USER_LOGIN_HIS`
row on every failed attempt (the data scaffold), but no counter reads those rows to flip `LOCK_YN`.
**Status: MISSING** — deferred (Sprint 0 out-of-scope); implement the counter in `AuthService` against
the existing `USER_LOGIN_HIS` data before any non-local deploy.

### GAP-4 — `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` post-auth gates (MISSING / PARTIAL)

Legacy JEX checks additional business flags after the password passes. The refactor maps `BUSI_INFM`
columns (`NTNL_CD`, `BUSI_STTS`) but does not gate login on them, and `CHNL_TYPE` is not modelled.
**Status: `NTNL_CD` MISSING, `CHNL_TYPE` MISSING, `BUSI_STTS` PARTIAL** (mapped, not enforced) — deferred
to a pre-production hardening pass.

### GAP-5 — Rate-limiting / brute-force protection (MISSING)

No throttling on `POST /api/v1/auth/login`. **Status: MISSING** — out of scope for R&D, mandatory before
any non-local deploy.

---

## Migration status

```
Refactor Status: complete + unit-tested for the in-scope login surface (single-link auto-pick,
                 multi-link chooser, finalize, flag gates, verify-only password, USER_LOGIN_HIS audit,
                 authority + product-type resolution, JWT issuance).
                 Deferred (not parity-blocking for R&D): 5-failure auto-lock, post-auth gates,
                 rate-limiting.
```

Evidence for closed items:

- **All flow branches implemented** ([AuthService.login](../../../../src/main/java/com/kosign/wecms/domain/auth/service/AuthService.java)); frontend wired (no mock fallback) per the frontend login docs.
- **Verify-only password** proven by `AuthServiceTest.successfulLogin_isVerifyOnly_neverWritesUserPw`.
- **Chooser per-option product type + finalize switch** proven by the multi-link `AuthServiceTest` cases.
- **Flag-gate order + FAIL audit** proven by the dedicated `AuthServiceTest` gate cases.
- **Slug `login`** used end-to-end (route, package, docs).

Residual (non-blocking, tracked):
1. GAP-3 — 5-failure auto-lock counter (deferred).
2. GAP-4 — `NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS` post-auth gates (deferred).
3. GAP-5 — rate-limiting (deferred; pre-deploy mandatory).
4. No `@WebMvcTest` controller test for the wire shape yet (see [test-scenarios.md](test-scenarios.md)).
