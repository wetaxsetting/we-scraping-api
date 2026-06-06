# User Management — Test Scenarios (backend SIT/UAT)

Back to [README](README.md) · [functional-specification](functional-specification.md) · frontend [test-scenarios](../../../../../wecms-web/docs/features/settings/user-management/test-scenarios.md)

Covers backend behaviour. Automated coverage today: `UserManagementAuthorizationTest`,
`UserManagementAccessPolicyTest`. Cross-system scenarios:
[`docs/features/user-management/test-scenarios.md`](../../../../../docs/features/user-management/test-scenarios.md).

Legend: **MATCH** = behaves as spec. Run as ADMIN unless the row says otherwise.

---

## 1. User management — CRUD

| ID | Scenario | Steps | Expected |
|---|---|---|---|
| UM-01 | Create user | POST valid body, `set_as_admin=false` | 200; `USER_INFM` + `USER_BUSI_CONN_INFM(DEL_YN='N')` inserted; no `BUSI_ADMN_INFM` row; row in list |
| UM-02 | Create admin user | POST with `set_as_admin=true` | 200; `BUSI_ADMN_INFM` row created; `is_admin=true` |
| UM-03 | View user list | GET list | 200; only active links, newest first; `total_count` = active count |
| UM-04 | View user detail | GET `/{id}` | 200; `UserRowResponse`; password never present |
| UM-05 | Update profile | PUT changed name/email/phone/position/dept, no `user_pw` | 200; `USER_INFM` updated; password hash unchanged |
| UM-06 | Update with password reset | PUT with valid `user_pw` | 200; new BCrypt hash stored; login works with new password |
| UM-07 | Promote to admin | PUT `set_as_admin=true` on a non-admin | 200; `BUSI_ADMN_INFM` row inserted |
| UM-08 | Demote admin (not last) | PUT `set_as_admin=false`, ≥2 admins exist | 200; admin row removed |
| UM-09 | Activate / deactivate | (Not supported from this screen) | No endpoint — see gap analysis (deferred); status via DBA/auth |
| UM-10 | Delete (unlink) user | DELETE `/{id}` (other user) | 200; `USER_BUSI_CONN_INFM.DEL_YN='Y'`; admin row gone; `ACCT_AUTH_INFO` revoked; `USER_INFM` preserved; absent from list |

## 2. Permission management (menu entitlement)

| ID | Scenario | Steps | Expected |
|---|---|---|---|
| PM-01 | Load grants | GET `/{id}/menu-entitlement` | 200; `menu_auth_cds` = current `USE_YN='Y'` slugs |
| PM-02 | Assign permissions | PUT with a superset of slugs | 200; new rows inserted; GET reflects full set |
| PM-03 | Remove permissions | PUT with a subset | 200; removed slugs gone (delete-then-insert) |
| PM-04 | Revoke all | PUT `menu_auth_cds: []` | 200; no rows remain for the user |
| PM-05 | Verify menu access | Grant `settings-core-users`, re-login as that user | Menu filtering shows only granted items (Menu Authorization feature) |
| PM-06 | Save race | Two concurrent PUTs | Both resolve; retry-on-`DataIntegrityViolationException` keeps state consistent |

## 3. Read-only viewer (non-admin with `settings-core-users`)

| ID | Scenario | Steps | Expected |
|---|---|---|---|
| RO-01 | View-only access | Login as viewer; GET list / detail / avatar | 200 — reads allowed |
| RO-02 | Create blocked | Viewer POST create | **403 / `WBE1003`** (denied before DB access) |
| RO-03 | Update blocked | Viewer PUT update | **403 / `WBE1003`** |
| RO-04 | Delete blocked | Viewer DELETE | **403 / `WBE1003`** |
| RO-05 | Entitlement write blocked | Viewer PUT menu-entitlement | **403 / `WBE1003`** |
| RO-06 | check-id blocked | Viewer GET check-id | **403 / `WBE1003`** |
| RO-07 | Avatar write blocked | Viewer POST/DELETE image | **403 / `WBE1003`** |
| RO-08 | No-grant user | Non-admin, no `settings-core-users` grant, GET list | **403** — cannot view at all |
| RO-09 | UI: disabled controls | Open detail as viewer (frontend) | "Set as Admin" disabled, password masked `************`, no Add/Edit/Delete, Menu Entitlement tab hidden — see frontend test-scenarios |

## 4. Validation

| ID | Scenario | Input | Expected |
|---|---|---|---|
| VAL-01 | Missing required field | POST without `user_nm` | `WBE_VALIDATION` |
| VAL-02 | Duplicate login id | POST with existing `user_id` | `WBE_USERS_DUPLICATE_LOGIN_ID` |
| VAL-03 | check-id available | GET check-id, free id | `{ available: true }` |
| VAL-04 | check-id taken | GET check-id, existing id | `{ available: false }` |
| VAL-05 | check-id blank | GET check-id, empty `login_id` | `WBE_USERS_LOGIN_ID_REQUIRED` |
| VAL-06 | Invalid email | POST `eml="not-an-email"` | `WBE_VALIDATION` |
| VAL-07 | Weak password | POST `user_pw="abcdefgh"` (no special char) | `WBE_VALIDATION` (`@Pattern`) |
| VAL-08 | Short password | POST `user_pw="Aa1!"` | `WBE_VALIDATION` (`@Size min=8`) |
| VAL-09 | Update keep-password | PUT with blank/absent `user_pw` | 200; existing hash retained |
| VAL-10 | Avatar too large | POST image > 2 MB | `WBE2117` |
| VAL-11 | Avatar bad type | POST image `.bmp`/`.pdf` | `WBE2118` |

## 5. Security & escalation

| ID | Scenario | Steps | Expected |
|---|---|---|---|
| SEC-01 | Unauthenticated | Any endpoint with no JWT | 401 (`JwtAuthenticationEntryPoint`) |
| SEC-02 | Self-delete | ADMIN deletes own account | `WBE_USERS_CANNOT_DELETE_SELF` |
| SEC-03 | Last-admin demote | ADMIN demotes the only admin | `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN` |
| SEC-04 | Last-admin delete | ADMIN deletes the only admin | `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN` (delete path guard) |
| SEC-05 | Privilege escalation via create | Viewer POST `set_as_admin=true` | **403** before any insert (`UserManagementAuthorizationTest`) |
| SEC-06 | Cross-tenant access | Request a `user_id` linked only to another tenant | `WBE_USERS_NOT_FOUND` (queries scoped to `currentBizKey()`) |
| SEC-07 | Tenant override attempt | Send a `biz_key` in body/param | Ignored — tenant from signed JWT only |
| SEC-08 | Password never leaked | Inspect every list/detail/create/update response | No `user_pw` field present |

## 6. Automated test references

| Test class | Covers |
|---|---|
| `UserManagementAuthorizationTest` | RO-02..05, SEC-05 (non-admin denial before DB), admin pass-through reaching business logic |
| `UserManagementAccessPolicyTest` | RO-01/RO-08 read policy (admin always; granted user yes; ungranted no) |

Gaps (no automated coverage yet, manual SIT recommended): UM-06 password-reset login round-trip,
PM-06 race, VAL-10/11 avatar limits, SEC-06 cross-tenant. Logged in
[refactor-gap-analysis.md](refactor-gap-analysis.md).
