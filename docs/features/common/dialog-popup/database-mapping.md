# Common → dialog-popup — Database Mapping (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.

> ⚠️ **No tables. No entities. No migrations.** Dialogs/popups are a frontend-only concern; nothing is
> persisted on their behalf. This file exists only to keep the Common 9-file structure complete.

## Tables touched

| Table | Role |
|---|---|
| — | None. `dialog-popup` reads/writes no table. |

There is no `DIALOG`, `POPUP`, or `MODAL` table in the legacy PROD active inventory or the refactor schema,
and none would be added — dialog state is ephemeral client UI state held in React, never persisted.

## Indirect data dependency

A *system error* dialog renders fields from the response **error envelope** (`status.code`, `trace_id`),
which itself is not a table — it is assembled per-request by `GlobalExceptionHandler` + `TraceIdFilter`.
The `trace_id` is grepable in server logs (MDC), not stored in a dialog table. See the error-envelope module:
[`../error-envelope/database-mapping.md`](../error-envelope/database-mapping.md).

## Migrations

None. No Flyway migration is owned by this module.

## Related
- WEB (authoritative): [`../../../../../wecms-web/docs/features/common/dialog-popup/README.md`](../../../../../wecms-web/docs/features/common/dialog-popup/README.md)
- Error envelope (the only backend contract a dialog reads): [`../error-envelope/README.md`](../error-envelope/README.md)
