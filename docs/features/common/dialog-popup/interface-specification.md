# Common → dialog-popup — Interface Specification (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.

> ⚠️ **No endpoints, no DTOs, no request/response shapes.** dialog-popup exposes no REST interface.
> This file keeps the Common 9-file structure complete and documents the one envelope a dialog reads.

## Endpoints

| Method | Path | Notes |
|---|---|---|
| — | — | None. dialog-popup adds no endpoint. |

## DTOs

None owned by this module.

## The envelope a system error dialog consumes (owned by error-envelope)

A dialog never calls its own endpoint — it renders whatever envelope the page's actual API call returned:

```jsonc
// error envelope (any failing endpoint) — lowercase snake_case wire
{
  "status":    { "error": true, "code": "WBE1009", "message": "ID or password does not match." },
  "timestamp": "2026-05-08T14:46:49Z",
  "path":      "/api/v1/auth/login",
  "trace_id":  "20260508144649PT06592776769567DEVL"
}
```

The frontend axios interceptor maps this to `ApiCallError{ code, message, traceId }` and the page chooses a
dialog variant. Full envelope contract: [`../error-envelope/interface-specification.md`](../error-envelope/interface-specification.md).

## Related
- WEB API consumption (how dialogs read responses): [`../../../../../wecms-web/docs/features/common/dialog-popup/api-consumption.md`](../../../../../wecms-web/docs/features/common/dialog-popup/api-consumption.md)
- Error envelope: [`../error-envelope/README.md`](../error-envelope/README.md)
