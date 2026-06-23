# Daily Financial — Runtime Data Source Check

**Date:** 2026-06-04
**Question:** is `/banking/reports/daily-financial` rendering **real API data** or **mock fallback**?
**Method:** code-path trace + dev-DB inspection. No business logic changed; no mock removed.

> **RESOLVED 2026-06-04 (config fix) — see §7.** Root cause confirmed: `NEXT_PUBLIC_API_BASE_URL` was unset in `wecms-web`, so Axios defaulted to `http://localhost:8080` (unreachable from a deployed browser). Fixed by adding `wecms-web/.env.local` (local → `http://localhost:8080`) + `wecms-web/.env.example` (per-environment template incl. `http://sample.coocon.co.kr:8080`). An **authenticated live call to the running backend returned HTTP 200 with real VND/USD/KRW data** (no business-logic / CORS change needed — CORS already allowed the sample origin). The deployed sample box still needs the env var set at its build/deploy (gitignored by design).

---

## TL;DR

| | |
|---|---|
| **Screen state (the screenshot)** | **MOCK FALLBACK** — not real API data. |
| **Exact reason** | The `GET /api/v1/banking/reports/daily-financial` call **threw** at runtime → `useApiData` set `data = null` → `DailyFinancialPage` fell back to `mockFinancialStatus` / `mockCurrentAccounts`. |
| **Proof it was mock** | The values rendered (`VND 21đ`, `10đ`, `107đ`, `USD $0`) are impossible from the real wire payload. They are the *pre-fix* `mockFinancialStatus` pre-formatted strings double-passed through `formatAmount`: `parseFloat("21.437.055.576₫") → 21.437 → "21₫"`; `parseFloat("$12,942.15") → NaN → "$0"`. Only the mock produces those exact numbers. |
| **Is the backend/data the problem?** | **No.** The dev DB has real, in-scope data for `asOf=20260604` (24 active current/saving accounts across VND/USD/KRW; 5 transactions that day). The backend query/filter is correct and would return a non-empty report. The failure is in the **frontend↔API transport**, not the backend or the data. |
| **API URL called** | `GET {NEXT_PUBLIC_API_BASE_URL}/api/v1/banking/reports/daily-financial?asOf=20260604&currencyScope=ALL&activeAccountsOnly=true&includeInternalTransfers=true` |

---

## 1. Where mock data is used + the exact fallback triggers

Chain: `DailyFinancialClient` → `useApiData(fetchDailyFinancial)` → `DailyFinancialPage` (mock fallback).

- [`DailyFinancialPage.tsx:198-202`](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx#L198-L202):
  ```ts
  const sourceStatus   = financialStatus ?? mockFinancialStatus;
  const sourceAccounts = currentAccounts && currentAccounts.length > 0
                         ? currentAccounts : mockCurrentAccounts;
  ```
- [`DailyFinancialClient.tsx:47-59`](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialClient.tsx#L47-L59): `financialStatus = data?.FINANCIAL_STATUS?.map(...)`, `currentAccounts = data?.CURRENT_ACCOUNTS?.map(...)` — both become `undefined` when `data` is `null`/`undefined`.
- [`useApiData.ts:48-60`](../../../../../wecms-web/lib/api/useApiData.ts#L48-L60): on a thrown loader it runs `setState({ data: null, ... })`.
- [`banking.ts:63-78` `unwrap`](../../../../../wecms-web/lib/api/banking.ts#L63-L78): throws `ApiCallError` when `status.error === true` **or** `!body.data`, and on any axios/network error throws `ApiCallError("NETWORK", ...)`.

**Fallback to `mockFinancialStatus` fires when (and only when):**

| Trigger | Result |
|---|---|
| Before first Search (`hasSearched=false`) | loader returns `Promise.resolve(undefined)` → `data=undefined`. *But the report region isn't rendered yet (placeholder shows), so mock isn't visible.* |
| **Fetch throws after Search** — network error / connection refused / CORS block / non-2xx / `status.error=true` / unresolved 401 | `data=null` → `financialStatus` undefined → **mock financial status**; `currentAccounts` undefined → **mock ledger** (which is then filtered out by the type-`11` partition → "No data to display"). ← **this is the screenshot.** |
| Fetch succeeds but `FINANCIAL_STATUS` is `null`/absent | mock. (Doesn't happen — backend always returns the array.) |
| Fetch succeeds with `FINANCIAL_STATUS: []` | **NOT** mock (`[].map()` is `[]`, not nullish) → renders zero sections. |

There is **no** `NODE_ENV`/dev-mode flag and **no** `try/catch` in the page itself — the only fallback mechanism is the `?? mock` / `length > 0 ? … : mock` operators above, driven entirely by whether the fetch resolved or threw.

The screenshot showed the **report region rendered** (the "As of 04-Jun-2026 · Currency: All" card + Financial Status sections + "No data to display") with garbled mock values ⇒ `hasSearched=true` **and** `data=null` ⇒ **the fetch threw.**

## 2. Endpoint correctness

- Frontend: [`banking.ts:782-787`](../../../../../wecms-web/lib/api/banking.ts#L782-L787) → `api.get("/api/v1/banking/reports/daily-financial", { params })`.
- Backend: [`ReportController.java:16,29-36`](../../../../../src/main/java/com/kosign/wecms/domain/banking/controller/ReportController.java#L29-L36) → `@RequestMapping("/api/v1/banking/reports")` + `@GetMapping("/daily-financial")`, params `asOf` / `currencyScope` / `activeAccountsOnly` / `includeInternalTransfers`.
- **Path + params match exactly.** The endpoint wiring is correct.
- **Base URL** ([`axios.ts:5-6`](../../../../../wecms-web/lib/axios.ts#L5-L6)): `process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080"`. On a **deployed** frontend (e.g. `sample.coocon.co.kr:3000`) where this env var is unset, the browser resolves `http://localhost:8080` against the **viewer's own machine**, not the server → connection refused → `ApiCallError("NETWORK")` → mock. This is the most probable runtime trigger.

## 3. Does the API have real data for `asOf=20260604` / `currencyScope=ALL`?

Inspected `postgres_dev_server_refactor` (`172.168.5.172/wecmsdevdb`, tenant `VN_VNM0004_000001`), read-only:

**Active current/savings accounts (the report's in-scope set):**

| CCY | Bucket | Accounts | Σ `ACCT_MSTR.bal` (indicative) |
|---|---|---|---|
| VND | CURRENT | 14 | 11,803,891,401 |
| VND | SAVING | 5 | 108,257,734 |
| USD | CURRENT | 3 | 12,817.28 |
| KRW | CURRENT | 2 | 0 |

**Transactions:** `5` rows on `TRSC_DT='20260604'`; latest `TRSC_DT = 20260604`; `107` total. So per-account debit/credit aggregates **and** as-of balances are computable for that date.

⇒ A reachable, authenticated call to `dailyFinancial(asOf=20260604, ALL)` returns a **non-empty** report: `FINANCIAL_STATUS` with **3 currency rows (VND, USD, KRW)** and `CURRENT_ACCOUNTS` with **24 ledger rows**. The backend query/filter is **not** the problem, and the data **is** seeded.

> Note: the real VND current total (≈ 11.80 B) ≠ the mock VND current (10,148,082,631). After the mock-coverage fix the mock now renders *clean* numbers (`10.255.547.462₫` / `$12,942.15` / `₩1,500,000`), so a quick way to tell real-vs-mock at a glance: **real** shows VND current `11.803.891.401₫`; **mock** shows `10.148.082.631₫`.

## 4. Why it threw — ranked (browser Network tab on the deployed host is needed to pin the exact one)

1. **API base URL unreachable (most likely).** Deployed FE with `NEXT_PUBLIC_API_BASE_URL` unset → `http://localhost:8080` → connection refused in the viewer's browser → `NETWORK`.
2. **CORS** — deployed FE origin not in backend `wecms.cors.allowed-origins` → browser blocks → axios network error → `NETWORK`.
3. **Auth (401)** — missing/expired JWT. Would normally open the re-auth dialog ([`axios.ts:59-83`](../../../../../wecms-web/lib/axios.ts#L59-L83)); if unresolved, the call rejects → mock. (Less likely — the user appears logged in.)
4. **`status.error=true` / 5xx** — backend reached but errored → mock.

Local probe this run: `GET http://localhost:8080/actuator/health` → **HTTP 404** (a server answered but not the wecms-api health route) — inconclusive for the remote host; documented only for completeness.

## 5. Files involved

| File | Role |
|---|---|
| [`components/.../daily-financial/DailyFinancialClient.tsx`](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialClient.tsx) | builds params, calls `fetchDailyFinancial`, maps `data?.FINANCIAL_STATUS` / `CURRENT_ACCOUNTS` |
| [`components/.../daily-financial/DailyFinancialPage.tsx`](../../../../../wecms-web/components/banking/reports/daily-financial/DailyFinancialPage.tsx) | the `?? mock` / `length>0 ? : mock` fallback |
| [`components/.../daily-financial/mockData.ts`](../../../../../wecms-web/components/banking/reports/daily-financial/mockData.ts) | `mockFinancialStatus` / `mockCurrentAccounts` |
| [`lib/api/banking.ts`](../../../../../wecms-web/lib/api/banking.ts) | `fetchDailyFinancial` + `unwrap` (throws on error/empty) |
| [`lib/api/useApiData.ts`](../../../../../wecms-web/lib/api/useApiData.ts) | sets `data=null` on throw |
| [`lib/axios.ts`](../../../../../wecms-web/lib/axios.ts) | `baseURL` (env var), JWT interceptor, 401 re-auth |
| [`lib/api/mappers.ts`](../../../../../wecms-web/lib/api/mappers.ts) | `mapFinancialStatus` / `mapCurrentAccountRow` (pass-through, no fallback) |
| [`ReportController.java`](../../../../../src/main/java/com/kosign/wecms/domain/banking/controller/ReportController.java) · [`ReportService.java`](../../../../../src/main/java/com/kosign/wecms/domain/banking/service/ReportService.java) | backend endpoint + query (verified correct, returns real data) |

## 6. Recommended fix (NOT applied — logic unchanged per instructions)

1. **Set `NEXT_PUBLIC_API_BASE_URL`** on the deployed frontend to the reachable `wecms-api` origin (never the default `localhost:8080`). Rebuild — it's inlined at Next.js build time.
2. **Confirm CORS + auth:** the deployed FE origin must be in the backend `wecms.cors.allowed-origins`, and a valid JWT must be attached (user logged in).
3. **Verification (no code):** open DevTools → Network on the page, click Search, inspect `GET /api/v1/banking/reports/daily-financial`. Expect **HTTP 200** with `data.FINANCIAL_STATUS` of 3 rows. If you see connection-refused / CORS / 401 / 5xx, apply (1)/(2). Confirm the VND current total reads `11.803.891.401₫` (real) — not `10.148.082.631₫` (mock).
4. **Follow-up (separate change — do not do now):** the silent `?? mock` fallback **masks** real API failures (a prod transport error looks like a populated-but-wrong report). Recommend making mock fallback **dev-only / opt-in** and surfacing API errors (error banner / `useDialog`) so a failed fetch is visible rather than disguised. This is a logic change — schedule it; this check only diagnoses.

---

## Final answer

- **Real API data or mock fallback?** → **Mock fallback** (the screenshot). Confirmed by the double-formatted mock values that the real wire payload cannot produce.
- **Exact reason:** the daily-financial fetch **threw** (`fetchDailyFinancial` → `unwrap` → `ApiCallError`), so `useApiData` set `data=null` and `DailyFinancialPage` rendered `mockFinancialStatus` / `mockCurrentAccounts`. Most probable transport cause: **`NEXT_PUBLIC_API_BASE_URL` unset on the deployed FE → `http://localhost:8080` unreachable from the browser** (CORS / 401 are secondary candidates; confirm via the Network tab on the deployed host).
- **API URL:** `GET {NEXT_PUBLIC_API_BASE_URL}/api/v1/banking/reports/daily-financial?asOf=20260604&currencyScope=ALL&activeAccountsOnly=true&includeInternalTransfers=true` — endpoint + params verified correct.
- **API response summary (expected from a reachable call):** non-empty — `FINANCIAL_STATUS` = 3 rows (VND, USD, KRW), `CURRENT_ACCOUNTS` = 24 ledger rows; data confirmed present in the dev DB (24 active current/saving accounts, 5 txns on 2026-06-04). The backend query/filter is correct and is **not** the cause.
- **Recommended fix:** set the deployed `NEXT_PUBLIC_API_BASE_URL` (+ verify CORS/JWT); then make the silent mock fallback dev-only / surface errors (separate, scheduled change).

---

## 7. Resolution (2026-06-04) — runtime/API connection config fix

**Scope:** runtime/API connection configuration only. No Daily Financial business logic, mock, Excel, REC2 grid, or popup touched. No backend code/config changed (CORS already correct).

### What was wrong
`wecms-web` had **no `.env*` files at all** → `NEXT_PUBLIC_API_BASE_URL` unset → `lib/axios.ts:5-6` fell back to `http://localhost:8080`. On a deployed frontend the browser resolves that against the *viewer's* machine → request fails → mock fallback.

### Config changed (files added in `wecms-web/`)

| File | Tracked? | Content | Purpose |
|---|---|---|---|
| `.env.local` | gitignored (`.gitignore: .env*`) | `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` | Local dev → the wecms-api running on this machine (port 8080). Makes the local page use real API data. |
| `.env.example` | **committed** (`!.env.example`) | documented template + per-environment table | The single tracked env file; deploys copy it / set the var. Documents: local → `http://localhost:8080`, sample → `http://sample.coocon.co.kr:8080`, SIT → `https://wecms-api.kosign.dev`. |

No change to `lib/axios.ts` / `lib/api/banking.ts` (already read the env var correctly). No backend change — `application-local.yml` `wecms.cors.allowed-origins` already lists `http://sample.coocon.co.kr:3000`, `https://wecms.kosign.dev`, `http://localhost:3000`.

> **Why not a committed `.env.production`:** `.gitignore` ignores `.env*` except `.env.example` (intentional — per-environment backends differ: sample vs SIT). So production API base URLs are injected at **build/deploy time** (deploy env var, which overrides `.env` files) — not committed. The sample box must set `NEXT_PUBLIC_API_BASE_URL=http://sample.coocon.co.kr:8080` in its build/deploy and rebuild (`NEXT_PUBLIC_*` is inlined at build time).

### Verification (live, this environment)

Backend confirmed running on `http://localhost:8080`:
- Unauthenticated `GET /api/v1/banking/reports/daily-financial` → **HTTP 401** (endpoint exists, JWT required — correct).
- Login `testuser` + `biz_key=VN_VNM0004_000001` → JWT issued.
- Authenticated `GET /api/v1/banking/reports/daily-financial?asOf=20260604&currencyScope=ALL&activeAccountsOnly=true&includeInternalTransfers=true` → **HTTP 200**, real data:

```
FINANCIAL_STATUS:
  VND  total 11,912,504,162  current 11,804,899,409  saving 107,604,753
  KRW  total 0               current 0               saving 0          ← KRW present
  USD  total 12,817.28       current 12,817.28        saving 0
CURRENT_ACCOUNTS: real ledger rows (Vietin Bank / BIDV Bank, real ACCT_NO + BAL),
                  TRANSACTIONS: []   (inline grid still P1/G-3, unchanged)
```

Real-vs-mock tell confirmed: VND current = `11,804,899,409` (**real**, date-aware as-of balance) — NOT the mock `10,148,082,631`, and not the garbled `21đ`. KRW renders (₩, value 0). Frontend `npx tsc --noEmit` → clean; `npm run build` → success (env files valid, base URL inlined).

### Final status

| Item | Result |
|---|---|
| **Final API base URL** | Local/this env: `http://localhost:8080` (via `.env.local`). Deployed sample: `http://sample.coocon.co.kr:8080` (set at that box's build/deploy — documented in `.env.example`). |
| **Request status** | `GET /api/v1/banking/reports/daily-financial …` → **HTTP 200** (authenticated), real payload. |
| **Real data now used?** | **Yes** in any environment where `NEXT_PUBLIC_API_BASE_URL` resolves to a reachable wecms-api + a valid JWT is present. Locally: yes. |
| **Remaining issue** | (1) The **deployed sample box must rebuild with the env var set** — gitignored, so it cannot propagate via git; operational step. (2) Pre-existing design smell (separate, scheduled — not in this scope): the silent `?? mock` fallback masks transport failures; make it dev-only / surface API errors so a future outage is visible rather than disguised. |

