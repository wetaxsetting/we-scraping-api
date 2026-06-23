# Common → dialog-popup — Migration Guide (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.

> ⚠️ No backend migration path — there is no backend artifact to migrate. This file records how the JEX
> server-side popup concept *disappeared* on the backend in the refactor.

## JEX → refactor

| JEX (legacy) | Refactor backend | Class |
|---|---|---|
| `layerPopup` server-rendered JSP layer | (removed) — backend renders no UI | **DEAD LEGACY** |
| `_act.jsp` returning popup markup / `COMMON_HEAD`+`REC` driving a modal | stateless JSON `{ status, data, … }` envelope | **APPROVED DEVIATION** (owned by error-envelope) |
| Server-held modal/session state | none — dialog state is ephemeral React state | **DEAD LEGACY** |
| ML message codes (`P00001`) surfaced in a JSP alert | `WBExxxx`/`WBIxxxx` in `status.code`, rendered by FE `useDialog()` | **MATCH** (codes ported; rendering moved to FE) |

## What a backend author must know

- **Do not** add a "dialog"/"popup" controller, service, DTO, or table. There is no backend popup concept.
- **Do** return the standard envelope with a correct `WBExxxx` code + message; the frontend turns that into the
  right dialog variant. Add new codes to `messages.properties` first (see error-envelope migration guide).
- A new ML code that previously drove a JSP popup becomes a `WBExxxx` code — port it under
  [`../error-envelope/migration-guide.md`](../error-envelope/migration-guide.md), not here.

## Related
- WEB module (where the popup logic actually migrated to): [`../../../../../wecms-web/docs/features/common/dialog-popup/README.md`](../../../../../wecms-web/docs/features/common/dialog-popup/README.md)
- Error envelope migration: [`../error-envelope/migration-guide.md`](../error-envelope/migration-guide.md)
