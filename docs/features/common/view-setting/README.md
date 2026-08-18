# View Setting (Screen Options) — Backend Feature Hub

**Part of:** Common → view-setting
**Last verified:** 2026-06-23

**Status:** Working — endpoints implemented; first wired screen `banking-account-inquiry-vnd`.
**Domain / slug:** `common` / `view-setting` (global, screen-agnostic).
**Backend package:** `com.kosign.wecms.domain.setting` (`ScreenOptionController` / `ScreenOptionService`).

View Setting is a **global / common** WeCMS feature: one shared controller, one service, one
DTO set, two tables — keyed only by `WEB_SVC_ID` + `DYM_AREA_ID`. Adopting a new list page is a
Flyway **seed-only** change; no controller / service / DTO change. "Screen Options" is the REST
surface name (`/api/v1/screen-options/...`); it is the same feature.

## Endpoints (one-line summary)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | Load merged sys+user columns for one area (lazy-init on first call) |
| GET | `/api/v1/screen-options/{webSvcId}/areas` | List configured areas + per-area column count |
| PUT | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | Save user overrides (delete-then-insert) |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | Reset (delete user rows → re-init from sys) |

All four are JWT-protected, global, no `@menuAuthz`. Identity from `TenantContext`.
Legacy WSVC: `comm_0108_001_r001_osmc` (load), `_u002_osmc` (save), `_u001_osmc` (reset),
`SYS_SCR_OPTION_WECMS_R002` (areas).

## Documents

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end + per-operation flow; service → repo → entity → table; lazy-init; tenant scope |
| [functional-specification.md](functional-specification.md) | Capabilities, merge rules, special-field handling, validation, edge cases |
| [database-mapping.md](database-mapping.md) | The two tables, columns, PKs, entity↔column map, Flyway history, SIZE deferral |
| [interface-specification.md](interface-specification.md) | The 4 endpoints, request/response DTOs, error codes, `@Pattern` validation, authz |
| [migration-guide.md](migration-guide.md) | Legacy `comm_0108_001` family → `ScreenOptionController`/`Service`; what changed |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Parity matrix (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |
| [source-code-index.md](source-code-index.md) | Backend / frontend / JEX / test file map |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios, JUnit coverage, gaps |

## Related

- Category hub: [`../README.md`](../README.md)
- Frontend twin: [`../../../../../wecms-web/docs/features/common/view-setting/README.md`](../../../../../wecms-web/docs/features/common/view-setting/README.md)
- JEX functional reference: [`../../../../../docs/jex/features/view-setting.md`](../../../../../docs/jex/features/view-setting.md)
- Audit deliverables: [`../../../audits/common-source-index.md`](../../../audits/modules/common-source-index.md) · [`common-gap-analysis.md`](../../../audits/modules/common-gap-analysis.md) · [`common-docs-audit.md`](../../../audits/modules/common-docs-audit.md)
- Implementation rules: `wecms-api/CLAUDE.md` §"Screen Option (View Setting)" + §"Common table sort"
