# Common → dialog-popup — Source Code Index (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.

> ⚠️ **No backend source files.** A code sweep on 2026-06-23 confirmed there is no dialog/popup/modal
> controller, service, repository, entity, DTO, or migration in `wecms-api/src`
> (`find wecms-api/src/main/java -iname '*dialog*' -o -iname '*popup*' -o -iname '*modal*'` → none).

## Backend files owned by this module

| File | Role |
|---|---|
| — | None. dialog-popup owns no backend source. |

## The backend contract a dialog consumes (owned by error-envelope, listed for traceability)

| File | Role |
|---|---|
| `common/response/ApiResponse.java` | `{ status, data, timestamp?, path?, trace_id? }` envelope a dialog renders on error |
| `common/exception/GlobalExceptionHandler.java` | maps `BusinessException` → error envelope + HTTP status |
| `common/exception/TraceId.java` / `TraceIdFilter.java` | the `trace_id` an error dialog shows |
| `common/i18n/MessageResolver.java` | resolves the `status.message` the dialog displays |

These are documented in full under [`../error-envelope/source-code-index.md`](../error-envelope/source-code-index.md) —
they are **not** owned by dialog-popup.

## Frontend source (authoritative — where the dialog system lives)

See [`../../../../../wecms-web/docs/features/common/dialog-popup/component-index.md`](../../../../../wecms-web/docs/features/common/dialog-popup/component-index.md)
(`useDialog`, `<SystemDialog>`, shadcn `<Dialog>` + `stackLevel` ladder).

## Related
- Common source index (consolidated): [`../../../audits/common-source-index.md`](../../../audits/modules/common-source-index.md)
- Error envelope: [`../error-envelope/README.md`](../error-envelope/README.md)
