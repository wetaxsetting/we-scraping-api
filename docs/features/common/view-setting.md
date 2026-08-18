# Common > View Setting (Screen Options) — Summary (Backend)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/common/view-setting/`

**Domain:** `common` / `setting` (global, screen-agnostic) · **Slug:** `view-setting`
**Backend package:** `com.kosign.wecms.domain.setting` (`ScreenOptionController` / `ScreenOptionService`)
**Status:** ✅ Implemented — endpoints live; first wired screen `banking-account-inquiry-vnd`.

## Feature overview

The toolbar "list-view" popover (Figma 100:13382 / legacy `comm_0108_001`) lets a user toggle column
visibility, reorder, and reset to defaults on any list page. **Global feature** — one controller, one
service, one DTO set, two tables (`SYS_SCR_OPTION_WECMS` / `USER_SCR_OPTION_WECMS`), keyed by
`WEB_SVC_ID` (semantic kebab slug) + `DYM_AREA_ID`. Adopting a new page is a Flyway **seed-only** change.

## Endpoints (at a glance)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | merged sys+user (lazy-init) |
| GET | `/api/v1/screen-options/{webSvcId}/areas` | distinct areas + counts |
| PUT | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | save user overrides (delete-then-insert) |
| DELETE | `/api/v1/screen-options/{webSvcId}/{dymAreaId}` | reset → re-init from sys |

Errors: `WBE2001` (unknown screenId), `WBE_VALIDATION` (kebab pattern fail). Audit slug = `view-setting`.

## Detailed documentation

| Doc | Scope |
|---|---|
| [view-setting/README.md](view-setting/README.md) | Folder hub + full document index |
| [view-setting/business-flow.md](view-setting/business-flow.md) | load / save / reset / areas flows |
| [view-setting/functional-specification.md](view-setting/functional-specification.md) | merge rules, special fields, validation |
| [view-setting/database-mapping.md](view-setting/database-mapping.md) | tables, PKs, entity mapping, Flyway history |
| [view-setting/interface-specification.md](view-setting/interface-specification.md) | endpoint contracts + error codes |
| [view-setting/migration-guide.md](view-setting/migration-guide.md) | legacy `comm_0108_001` → Spring |
| [view-setting/refactor-gap-analysis.md](view-setting/refactor-gap-analysis.md) | parity matrix |
| [view-setting/source-code-index.md](view-setting/source-code-index.md) | verified file map |
| [view-setting/test-scenarios.md](view-setting/test-scenarios.md) | SIT/UAT scenarios |

## Related

- Frontend twin: [`../../../../wecms-web/docs/features/common/view-setting/README.md`](../../../../wecms-web/docs/features/common/view-setting/README.md)
- JEX functional reference: [`../../../../docs/jex/features/view-setting.md`](../../../../docs/jex/features/view-setting.md)
- Audit deliverables: [`../../audits/common-source-index.md`](../../audits/modules/common-source-index.md) · [`common-gap-analysis.md`](../../audits/modules/common-gap-analysis.md) · [`common-docs-audit.md`](../../audits/modules/common-docs-audit.md)
