# API Mapping — Spring REST endpoints in `wecms-api`

Cross-feature index of REST endpoints exposed by the refactor backend, with the Next.js consumer route and the JEX counterpart.

JEX is **read-only reference**. This file exists for traceability only.

## Endpoint table

| Method | Path | Auth | Domain | Consumed by | JEX Counterpart | Status | Refactor Doc |
|---|---|---|---|---|---|---|---|
| POST | `/api/v1/auth/login` | Public | `auth` | `/login` | `logn_0001_001_r001` | Working | [login-auth.md](../../features/auth/login-auth.md) |
| POST | `/api/v1/auth/logout` | Public | `auth` | profile dropdown | (client clears token) | Working (stub) | [login-auth.md](../../features/auth/login-auth.md) |
| GET | `/api/v1/auth/me` | Protected | `auth` | dashboard layout | `logn_0001_001_r002` | Working | [login-auth.md](../../features/auth/login-auth.md) |
| GET | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | Protected | `setting` | View Setting popover | `comm_0108_001_r001_osmc` | Working | [view-setting.md](../../features/common/view-setting.md) |
| GET | `/api/v1/screen-options/{webSvcId}/areas` | Protected | `setting` | View Setting popover | `SYS_SCR_OPTION_WECMS_R002` | Working | [view-setting.md](../../features/common/view-setting.md) |
| PUT | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | Protected | `setting` | View Setting save | `comm_0108_001_u002_osmc` | Working | [view-setting.md](../../features/common/view-setting.md) |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | Protected | `setting` | View Setting reset | `comm_0108_001_u001_osmc` | Working | [view-setting.md](../../features/common/view-setting.md) |
| GET | `/api/v1/banking/accounts` | Protected | `banking` | `/banking/account-inquiry` | `fnsh_0001_001_r001` | Working | [account-inquiry-vnd.md](../../../../docs/refactor-wecms/features/banking/account-inquiry-vnd.md) |
| POST | `/api/v1/banking/accounts/export` | Protected | `banking` | export button | `fnsh_0001_001` (export) | Working | [account-inquiry-vnd.md](../../../../docs/refactor-wecms/features/banking/account-inquiry-vnd.md) |
| GET/POST/PUT/DELETE | `/api/v1/banking/credentials` (+ `/{seqNo}`) | Protected | `banking` | `/banking/credentials` | `comm_0218_*` (BANK_CERT_INFO) | Working | [bank-credentials.md](../../features/banking/bank-credentials.md) |
| POST | `/api/v1/banking/scraping/{account-list,verify,{sessionId}/otp,{sessionId}/qna,{sessionId}/captcha}` | Protected | `banking` | `/banking/credentials` | `comm_0218_nxisas` | Working | [bank-credentials.md](../../features/banking/bank-credentials.md) |
| GET/PUT | `/api/v1/settings/environment-settings/{env_set_inf}` | Protected | `setting` | feature panels (e.g. Firm Banking Settings) | `comm_0600_001_r001` / `comm_0215_001_u001` | Working | [environment-settings.md](../../features/settings/environment-settings.md) |
| GET | `/actuator/health` | Public | infra | n/a | n/a | Working | n/a |
| (anything else) | any | Protected (401 if missing JWT) | — | — | — | — | — |

> Add a row here when adding a new endpoint. Keep `JEX Counterpart` populated wherever a JEX equivalent exists — empty cell = greenfield endpoint with no JEX counterpart.

## Conventions

- **Path prefix:** `/api/v1/...` for all business endpoints
- **Auth:** JWT in `Authorization: Bearer <token>` header; verified by `JwtAuthenticationFilter`
- **Tenant:** `BIZ_KEY` resolved from JWT claim — never accepted from request body or query
- **Response envelope:** `ApiResponse<T>` = `{ COMMON_HEAD: { ERROR, CODE, MESSAGE }, REC: T }`
- **Field naming:** JSON uses `UPPER_SNAKE_CASE` via `@JsonProperty` — matches legacy JEX exactly
- **Errors:** throw `BusinessException("WBExxxx", args…)` — `GlobalExceptionHandler` maps to envelope
- **Validation:** Jakarta Bean Validation on DTO records (`@NotBlank`, `@Pattern`, `@Size`)

## Reference

- JEX WSVC XMLs: `WECMS DEV/.../WECMS_ETC/xml/service/WSVC/`
- JEX action JSPs: `WECMS DEV/.../WECMS/web/WEB-INF/action/jex/wabooks/<module>/`
- Service mapping (field-level): [../migration/service-mapping.md](../migration/service-mapping.md)
- Page mapping (consumers): [../frontend/page-mapping.md](../../../../wecms-web/docs/references/page-mapping/page-mapping.md)
