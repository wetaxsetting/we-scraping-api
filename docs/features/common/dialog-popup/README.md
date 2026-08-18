# Common → dialog-popup (Backend)

**Part of:** Common → dialog-popup
**Last verified:** 2026-06-23

> **WEB-only — no backend surface.** `dialog-popup` has **no controller, no service, no
> repository, no endpoint, no DTO, no migration**. "Popup" / "dialog" is not a backend concept
> in the refactor: the API speaks the JSON `{ status, data, … }` envelope (lowercase snake_case)
> and renders no UI. The full documentation lives on the WEB side. This folder carries the standard
> Common 9-file structure for traceability, with every file honestly recording the absence of a
> backend surface and pointing to the one contract a dialog consumes (the **error-envelope**).

## Module files (9-file structure — all WEB-only / no-backend)

[business-flow.md](business-flow.md) · [database-mapping.md](database-mapping.md) ·
[functional-specification.md](functional-specification.md) · [interface-specification.md](interface-specification.md) ·
[migration-guide.md](migration-guide.md) · [refactor-gap-analysis.md](refactor-gap-analysis.md) ·
[source-code-index.md](source-code-index.md) · [test-scenarios.md](test-scenarios.md)

## Why this folder carries no backend implementation (not the usual 9 files of behavior)

The refactor split JEX's monolithic JSP screens into a stateless Spring Boot API + a Next.js SPA.
In JEX a "popup" was a server-rendered JSP layer (`layerPopup`) — a backend artifact. In the
refactor that disappeared entirely:

- The backend returns **data**, never a popup. There is no "popup envelope", no `_act.jsp`
  layer, no server-side modal state. Every error / success / confirmation surface is purely a
  **client** decision based on the response envelope (`status.error`, `status.code`).
- The system feedback dialogs (warning / error / success / confirmation) and the stacked
  feature-dialog modal ladder are **React components + a portal**, owned entirely by `wecms-web`.
- The only backend touch-point is indirect: a **system error dialog** displays an
  `ApiCallError` derived from the standard error envelope (`status.code` + `trace_id`). That
  contract is documented under the **error-envelope** common module (backend + WEB twins), not here.

Per the workspace rule "a refactor design note never lives where it doesn't belong", this folder
carries only this stub so the Common module index stays complete and traceable.

## Where it is documented

- **WEB feature docs (authoritative):**
  [`../../../../../wecms-web/docs/features/common/dialog-popup/README.md`](../../../../../wecms-web/docs/features/common/dialog-popup/README.md)
- **Category hub (backend):** [`../README.md`](../README.md)
- **Common audit deliverables:** [`../../../audits/common-source-index.md`](../../../audits/modules/common-source-index.md)

## Related (backend contract the error dialog consumes)

- **error-envelope (backend):** [`../error-envelope/README.md`](../error-envelope/README.md) — the
  `{ status, data, timestamp?, path?, trace_id? }` envelope + `WBExxxx` codes that a system error
  dialog renders. That is the only backend surface a dialog ever reads from, and it is owned by the
  error-envelope module, not dialog-popup.
