# Page Mapping — JEX `_view.jsp` → Next.js Route

Cross-feature index of JEX view pages translated (or planned) into Next.js App Router routes in `wecms-web`.

JEX is **read-only reference**. This file exists for traceability only.

## Mapping table

| JEX View JSP | JEX URL | Next.js Route | Layout / Group | Status | Refactor Doc |
|---|---|---|---|---|---|
| `logn_0001_001_view.jsp` | `https://www.wecms.vn/logn_0001_001.act` | `/login` | `app/login/` | Working | login-page.md (Needs Confirmation) |
| `fnsh_0001_001_view.jsp` | `https://www.wecms.vn/fnsh_0001_001.act` | `/banking/account-inquiry` | `(dashboard)` | Working (mock + real shell) | [account-inquiry-vnd.md](../../../../../docs/refactor-wecms/features/banking/account-inquiry-vnd.md) |
| `pymn_0001_001_view.jsp` | `https://www.wecms.vn/pymn_0001_001.act` | `/payment/approval-box` | `(dashboard)` | Working (mock data) | payment-approval-box.md (Needs Confirmation) |
| `comm_0108_001_view.jsp` (popover) | embedded | View Setting popover | shared component | Working | view-setting-popover.md (Needs Confirmation) |

> Add a row here when starting work on a new page. Keep the kebab-case slug consistent across `features/`, `backend/`, `frontend/`, `migration/`, and `database/`.

## Status legend

- **Working** — page implemented; rendering against local Spring API
- **Mock data** — page renders with seeded mock until backend endpoint exists
- **In progress** — design started; partial implementation
- **Planned** — slated for an upcoming sprint
- **Placeholder** — sidebar / nav entry exists without an `href`

## Route conventions

- Auth routes: `/login` (no layout group)
- App routes: under `(dashboard)` route group with sidebar + top nav chrome
- Module prefix matches JEX module code: `/banking/...`, `/payment/...`, `/setting/...`
- Sub-page slugs are kebab-case English (e.g. `account-inquiry`, `approval-box`)
- Post-login landing: `HOME_ROUTE = /banking/account-inquiry` — change only via [lib/routes.ts](../../../../../wecms-web/lib/routes.ts)

## API integration

Each page's API integration is documented in its own frontend doc:
- Axios fetcher path
- `lib/api/mappers.ts` translation (`UPPER_SNAKE` ↔ camelCase)
- `useApiData()` hook usage
- Error handling (`ApiCallError` carrying `COMMON_HEAD.CODE`)

## Reference

- JEX view JSPs: `WECMS DEV/.../WECMS/web/view/jex/wabooks/<module>/`
- JEX TypeScript: `WECMS DEV/.../WECMS_STATIC/ts-src/js/jex/wabooks/<module>/`
- JEX screen flows: [../../jex/screen-flows/](../../../../../docs/jex/screen-flows/)
- Live JEX system: `https://dev.wecms.vn/`, `https://www.wecms.vn/`
