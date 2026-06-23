# Common → dialog-popup — Functional Specification (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.

> ⚠️ There is **no backend functional behavior** to specify for dialog-popup. The functional spec lives on
> the WEB side:
> [`../../../../../wecms-web/docs/features/common/dialog-popup/ui-specification.md`](../../../../../wecms-web/docs/features/common/dialog-popup/ui-specification.md)
> and [`screen-flow.md`](../../../../../wecms-web/docs/features/common/dialog-popup/screen-flow.md).
> This file keeps the Common 9-file structure complete + records the backend contract a dialog depends on.

## Backend responsibilities

**None directly.** The backend:

- does **not** open, close, stack, or manage any dialog;
- does **not** return a "show this popup" instruction;
- does **not** hold modal state.

The backend's only obligation is to return the standard JSON envelope so the **frontend** can decide which
dialog variant (error / warning / success / confirmation) to render.

## Contract the frontend dialog layer depends on

| Backend behavior | Where it is specified | What the FE dialog does with it |
|---|---|---|
| Success → `{ status:{ error:false, code:"WBI0000" }, data }` | [`../error-envelope/functional-specification.md`](../error-envelope/functional-specification.md) | optional success dialog (FE choice) |
| Business error → `{ status:{ error:true, code:"WBExxxx", message } }` (HTTP 200) | error-envelope | `useDialog({ type:"error", errorCode })` |
| Client/server fault → 4xx/5xx + same envelope + `trace_id` | error-envelope | error dialog renders code + trace id |
| 401 (`WBE1401`) | auth/session-management | global session-expired re-auth dialog |

Variant selection, stacking (`stackLevel`), close behavior, button colours, and the 500 px system-dialog cap
are all **frontend rules** — see the WEB module. The backend never influences any of them.

## Out of scope (owned elsewhere)
- Dialog variants / stacking / close rules → WEB `dialog-popup`.
- Envelope + `WBExxxx` codes + `trace_id` generation → [`../error-envelope/`](../error-envelope/README.md).
- 401 re-auth dialog trigger → [`../../auth/session-management/README.md`](../../auth/session-management/README.md).
