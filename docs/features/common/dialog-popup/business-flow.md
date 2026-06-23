# Common → dialog-popup — Business Flow (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.

> ⚠️ This module has **no backend flow**. Dialogs/popups are a pure frontend concern in the refactor.
> The authoritative flow lives in
> [`../../../../../wecms-web/docs/features/common/dialog-popup/screen-flow.md`](../../../../../wecms-web/docs/features/common/dialog-popup/screen-flow.md).
> This file exists only to keep the Common 9-file structure complete + traceable.

## Why there is no backend flow

In JEX a "popup" (`layerPopup`) was a server-rendered JSP layer — a backend artifact. The refactor
deleted that model entirely:

- The Spring Boot API returns **data**, never a popup. There is no server-side modal state, no
  `_act.jsp` layer, no "popup envelope".
- Every error / success / confirmation / feature dialog is a **React component + portal**, owned by
  `wecms-web` (`useDialog()` / `<SystemDialog>` and the shadcn `stackLevel` ladder).

## The one indirect touch-point — the error envelope

The only way the backend ever participates in a dialog is **indirectly**: a system *error* dialog renders
an `ApiCallError` derived from the standard response envelope.

```
Backend throws BusinessException("WBExxxx")
        │
        ▼
GlobalExceptionHandler → { status:{error:true, code, message}, timestamp, path, trace_id }   (HTTP 200/4xx/5xx)
        │  (wire — lowercase snake_case)
        ▼
wecms-web axios interceptor → ApiCallError{ code, message, traceId }
        │
        ▼
useDialog().open({ type:"error", errorCode, description })   ← frontend decision, no backend involvement
```

That envelope contract is owned by the **error-envelope** module, not dialog-popup:
[`../error-envelope/business-flow.md`](../error-envelope/business-flow.md).

## Parity classification

| Concern | Class | Note |
|---|---|---|
| JEX server-rendered `layerPopup` | **DEAD LEGACY** | not ported; dialogs are client-only |
| Backend "popup endpoint" / modal state | **MISSING (by design)** | no such concept in a stateless JSON API |
| Error-envelope contract a dialog renders | **MATCH** | owned by error-envelope module |

## Related
- WEB (authoritative): [`../../../../../wecms-web/docs/features/common/dialog-popup/README.md`](../../../../../wecms-web/docs/features/common/dialog-popup/README.md)
- Backend contract consumed: [`../error-envelope/README.md`](../error-envelope/README.md)
- Category hub: [`../README.md`](../README.md)
