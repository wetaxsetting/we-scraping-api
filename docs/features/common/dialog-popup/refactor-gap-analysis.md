# Common → dialog-popup — Refactor Gap Analysis (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.
**Gap vocabulary:** MATCH · APPROVED DEVIATION · DEAD LEGACY · PARTIAL · MISSING.

> ⚠️ There is intentionally **no backend implementation** for dialog-popup. The "gaps" below are
> by-design absences, not defects. The real gap analysis for the dialog system is on the WEB side.

## Gap table (backend perspective)

| ID | Item | Class | Kind | Owner |
|---|---|---|---|---|
| G-DLG-1 | No backend controller/service/DTO/endpoint for dialogs | APPROVED DEVIATION | by-design (stateless JSON API renders no UI) | — |
| G-DLG-2 | JEX `layerPopup` server-rendered popup layer not ported | DEAD LEGACY | doc-exists-feature-removed | PO (closed) |
| G-DLG-3 | No backend modal/session state | MISSING (by design) | n/a — no such concept | — |
| G-DLG-4 | Error-envelope contract a system error dialog renders | MATCH | owned by error-envelope module | — |
| G-DLG-5 | No backend test for dialog behavior | n/a | nothing to test (no backend code) | — |

## Notes

- The only backend surface a dialog ever reads from is the **error envelope** (`status.code`, `trace_id`),
  which is implemented, tested, and documented under [`../error-envelope/`](../error-envelope/refactor-gap-analysis.md).
- All meaningful dialog gaps (variant misuse, stacking, missing close affordances, FE test coverage) are
  tracked in the WEB module: [`../../../../../wecms-web/docs/features/common/dialog-popup/test-scenarios.md`](../../../../../wecms-web/docs/features/common/dialog-popup/test-scenarios.md).

## PO decisions / developer follow-ups
- **None on the backend.** Do not introduce a backend dialog/popup surface; if a future requirement seems to
  need one, it is almost certainly an error-envelope or notification concern instead — raise with the PO before
  adding any backend artifact here.
