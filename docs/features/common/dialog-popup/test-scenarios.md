# Common → dialog-popup — Test Scenarios (Backend)

**Part of:** Common → dialog-popup · **Last verified:** 2026-06-23
**Status:** ⚪ WEB-only — no backend surface.

> ⚠️ **No backend tests** — there is no backend code to test. This file keeps the Common 9-file structure
> complete. Dialog test scenarios live on the WEB side:
> [`../../../../../wecms-web/docs/features/common/dialog-popup/test-scenarios.md`](../../../../../wecms-web/docs/features/common/dialog-popup/test-scenarios.md).

## Backend test coverage

| Area | Coverage | Note |
|---|---|---|
| dialog-popup backend logic | n/a | nothing to test (no controller/service/DTO) |

## Indirect coverage (owned by error-envelope)

The contract a system error dialog renders is exercised by the **error-envelope** tests — the
`GlobalExceptionHandler` envelope shape, `WBExxxx` resolution, and `trace_id` propagation. See
[`../error-envelope/test-scenarios.md`](../error-envelope/test-scenarios.md). Those guarantee that whatever a
dialog displays (code + message + trace id) is correct at the source.

## Recommended (WEB)

All actionable dialog test scenarios — variant selection (error vs warning), stacking order, close-affordance
rules, 401 re-auth dialog — are frontend concerns and are listed in the WEB module's `test-scenarios.md`.
There is **no frontend test runner** in `wecms-web` yet, so those are currently **MISSING** (tracked WEB-side).

## Related
- Error-envelope tests (backend contract): [`../error-envelope/test-scenarios.md`](../error-envelope/test-scenarios.md)
- WEB dialog tests (authoritative): [`../../../../../wecms-web/docs/features/common/dialog-popup/test-scenarios.md`](../../../../../wecms-web/docs/features/common/dialog-popup/test-scenarios.md)
