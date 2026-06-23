# Login — Test Scenarios (Backend)

Coverage map + scenarios. The on-disk test is
[AuthServiceTest.java](../../../../src/test/java/com/kosign/wecms/domain/auth/service/AuthServiceTest.java)
(Mockito unit test, 19 cases). Frontend scenarios:
[wecms-web/docs/features/auth/login/test-scenarios.md](../../../../../wecms-web/docs/features/auth/login/test-scenarios.md).

---

## 1. Implemented — `AuthServiceTest`

Mockito unit test; mocks all five repositories + `PasswordEncoder` + `JwtTokenProvider` +
`LoginHistoryService` + `MenuAuthorizationService`.

### Single-link auto-pick

| # | Test method | Asserts |
|---|---|---|
| S1 | `singleLink_companyF_carriesPrdtTypeF_andIssuesToken` | `prdt_type = "F"`, `auth_gr_cd = ADMIN`, token issued, `companies_available` null, SUCCESS audit, `LST_LGN_DATE` saved |
| S2 | `singleLink_companyH_carriesPrdtTypeH` | `prdt_type = "H"`, `auth_gr_cd = USER` |
| S3 | `singleLink_companyM_carriesPrdtTypeM` | `prdt_type = "M"` |
| S4 | `singleLink_companyWithNullPrdtType_shipsNullVerbatimNotF` | `prdt_type` null — never defaulted to `F` |
| S5 | `singleLink_companyWithDeferredLegacyLetter_carriedThroughVerbatim` | `prdt_type = "W"` round-trips verbatim (un-modelled letter) |
| S6 | `singleLink_requestedBizKeyMismatch_throwsWBE1009` | stray `biz_key` mismatch → `WBE1009` + FAIL; `save` never called |
| S7 | `successfulLogin_isVerifyOnly_neverWritesUserPw` | `encode`/`upgradeEncoding`/`userRepo.save` never called; legacy SHA-256 hash left intact |

### Multi-link chooser + finalize

| # | Test method | Asserts |
|---|---|---|
| M1 | `multiLink_firstCall_chooserOptionsCarryPerCompanyPrdtType` | no token; top-level `prdt_type` null; 2 options sorted by name (HAENGSUNG first), per-option `prdt_type` + `auth_gr_cd`; no `save`, no `issue` |
| M2 | `multiLink_finalizeSwitchesActivePrdtType_FthenH` | two finalize calls → different `biz_key`/`prdt_type`/token (company switch switches active product type) |
| M3 | `multiLink_finalizeWithUnknownBizKey_throwsWBE1009` | unknown `biz_key` → `WBE1009` + FAIL; no `save` |

### Flag-gate paths

| # | Test method | Asserts |
|---|---|---|
| F1 | `unknownUser_throwsWBE1009_andRecordsFail` | `WBE1009` + FAIL (`USER_NOT_FOUND`); `matches` never called |
| F2 | `deletedUser_throwsWBE1128_andRecordsFail` | `WBE1128` + FAIL (`DEL_YN=Y`) |
| F3 | `inactiveUser_throwsWBE1010_andRecordsFail` | `WBE1010` + FAIL (`ACTV_YN=N`) |
| F4 | `lockedUser_throwsWBE1011_andRecordsFail` | `WBE1011` + FAIL (`LOCK_YN=Y`) |
| F5 | `badPassword_throwsWBE1009_andRecordsFail` | `WBE1009` + FAIL (`PW_MISMATCH`) |
| F6 | `noActiveLinks_throwsWBE1009` | `WBE1009` + FAIL (`NO_TENANT_LINKS`); no `save` |

### `/me` (session-management scope; covered in the same test class)

| # | Test method | Asserts |
|---|---|---|
| ME1 | `me_readsPrdtTypeFromJwtClaim_notFromBusiInfm` | `/me` trusts the JWT `prdtType` claim, not the DB row |
| ME2 | `me_admin_bypassesMenuFiltering_allMenusTrue` | admin → `all_menus = true`, empty grants, resolver not hit |
| ME3 | `me_normalUser_returnsOnlyGrantedMenus` | user → `all_menus = false`, exact granted slugs |

> ME1–ME3 belong to [session-management](../session-management/README.md) / [menu-authorization](../menu-authorization/README.md); they live in `AuthServiceTest` because `me()` shares the service.

---

## 2. SIT / UAT scenarios (manual)

| # | Scenario | Expected |
|---|---|---|
| T1 | Login as a single-link active user | authenticated response, JWT, lands on dashboard; `LST_LGN_DATE` + SUCCESS row written |
| T2 | Login with a wrong password | `WBE1009`; FAIL row `PW_MISMATCH`; no token |
| T3 | Login as a deleted / inactive / locked user | `WBE1128` / `WBE1010` / `WBE1011` respectively; FAIL row |
| T4 | Login as a multi-link user with no `biz_key` | chooser response; no token; no SUCCESS row |
| T5 | Finalize the chooser with a valid `biz_key` | authenticated response bound to that company |
| T6 | Finalize with a `biz_key` the user has no link to | `WBE1009`; FAIL `TENANT_OUT_OF_SCOPE` |
| T7 | Login with `Accept-Language: ko` / `vi` / `km` | `lang_cd` echoes `KO` / `VI` / `KM`; JWT `langCd` matches |
| T8 | Login as a `BUSI_ADMN_INFM` member vs. non-member | `auth_gr_cd = ADMIN` vs. `USER`; `ROLE_ADMIN`/`ROLE_USER` on subsequent requests |
| T9 | Tenant with `PRDT_TYPE` null / un-modelled letter | `prdt_type` ships the verbatim value (or null); login still succeeds |
| T10 | Coexistence — log in via refactor then via live JEX with the same credential on a shared DB | both succeed; `USER_PW` unchanged after the refactor login |

---

## 3. Coverage gaps (MISSING)

- **`@WebMvcTest` controller test — MISSING.** No `AuthControllerTest` asserting the lowercase
  snake_case wire shape (`access_token` override, `companies_available` discriminator,
  `@JsonInclude(NON_NULL)` dropping nulls), the `Accept-Language` → `lang_cd` mapping, the `X-Forwarded-For`
  client-IP extraction, or the `WBE_VALIDATION` envelope on a malformed body. Add one.
- **Integration test against a real DB — MISSING.** All current coverage is Mockito; no Testcontainers /
  in-memory boot test exercises the actual JPA queries, the `REQUIRES_NEW` audit-row commit-on-rollback,
  or the legacy-DTM conversions (per `wecms-api/CLAUDE.md`, Testcontainers is deferred).
- **Coexistence cross-system check (T10) — manual only.** Cannot be exercised headless — needs a shared
  JEX DB and a live JEX login path; confirm in SIT, not in a unit test.
- **5-failure auto-lock — MISSING (feature not implemented).** No test because the counter does not exist
  (see [refactor-gap-analysis.md](refactor-gap-analysis.md) GAP-3).
- **Rate-limiting / brute-force — MISSING (feature not implemented).** GAP-5.
- **Post-auth gates (`NTNL_CD` / `CHNL_TYPE` / `BUSI_STTS`) — MISSING (feature not implemented).** GAP-4.
