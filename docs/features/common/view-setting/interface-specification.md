# View Setting — Interface Specification (Backend)

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

Source: `ScreenOptionController.java` + DTOs under `domain/setting/dto/`. Wire shape is lowercase
snake_case (global Jackson `SNAKE_CASE`); Java record fields are camelCase.

## Authorization

All four endpoints are JWT-protected (everything outside `/api/v1/auth/*` is `authenticated()`).
**No `@menuAuthz`, no `@PreAuthorize`** — View Setting is a global feature available to any
authenticated user. Tenant identity (`bizKey`, `userId`) is read from `TenantContext`, never the request.

## Endpoints

| Method | Path | Request body | Response `data` |
|---|---|---|---|
| GET | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | — | `ScreenOptionListResponse` |
| GET | `/api/v1/screen-options/{webSvcId}/areas` | — | `ScreenAreaListResponse` |
| PUT | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | `SaveScreenOptionRequest` | `ScreenOptionListResponse` |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | — | `ScreenOptionListResponse` |

Standard `{ status, data }` envelope on success; `{ status, timestamp, path, trace_id }` on error.

## Path-variable validation (`@Validated` on the controller)

| Var | Pattern (constant) | Effect |
|---|---|---|
| `webSvcId` | `SCREEN_ID_REGEX = ^[a-z][a-z0-9]*(-[a-z0-9]+)+$` | ≥2 kebab segments; rejects `fnsh_0001_001` |
| `dymAreaId` | `AREA_ID_REGEX = ^[a-z][a-z0-9]*(-[a-z0-9]+)*$` | single segment allowed → `default` passes |

`@Pattern` failure → `ConstraintViolationException` → `WBE_VALIDATION` (HTTP 200, business envelope).

## DTOs

### `ScreenOptionItem` (record) — one column row, GET response + PUT request

`@JsonInclude(NON_NULL)`. Fields (wire = snake_case):

| Java | Wire | Type | Meaning |
|---|---|---|---|
| `fieldId` | `field_id` | String | stable column key (e.g. `BANK`) |
| `fieldNm` | `field_nm` | String | display label |
| `dispOrd` | `disp_ord` | Integer | 1-based order |
| `dispYn` | `disp_yn` | String | `Y`/`N` render flag (sys-sourced) |
| `useYn` | `use_yn` | String | `Y`/`N` visibility (user-owned) |
| `size` | `size` | String | width hint e.g. `250px` (sys-sourced) |
| `sortYn` | `sort_yn` | String | `Y`=sortable (sys-sourced) |
| `htmlCont` | `html_cont` | String | optional pre-rendered header HTML |

### `ScreenOptionListResponse` (record)

`{ web_svc_id, dym_area_id, items: ScreenOptionItem[] }` — `items` merged + ordered by `disp_ord ASC`.

### `ScreenAreaListResponse` (record)

`{ web_svc_id, areas: [{ dym_area_id, column_count }] }` (`Area` nested record).
Note: this response has **no** `dym_area_id` at top level — only `web_svc_id` + the `areas` list.

### `SaveScreenOptionRequest` (record)

`{ items: ScreenOptionItem[] }` — `@NotNull @NotEmpty`. The frontend sends the FULL merged list
(including `use_yn='N'` rows) so order survives the delete-then-insert.

## Error codes

| Code | When |
|---|---|
| `WBE2001` | Unknown screenId — regex passed but no `SYS_SCR_OPTION_WECMS` row (load/save/reset/areas) |
| `WBE_VALIDATION` | `@Pattern` on path vars fails, or `items` null/empty on PUT |
| `WBE9999` | Unexpected server fault (catch-all) |

> `WBE2001` constant in the service is `SYS_NOT_DEFINED`. (Excel export uses `WBE2010` for an unknown
> export screenId — a different code/surface; do not conflate.)
