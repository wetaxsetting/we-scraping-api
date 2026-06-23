# Authorization — Interface Specification (Backend)

**Authorization is an enforcement layer, not a resource — it exposes no dedicated endpoint.** It layers
on **every** business `/api/v1/**` endpoint via Spring method security. This doc documents the
`@PreAuthorize` SpEL contract the guard exposes, the `WBE1003` / 403 envelope a denial produces, and how
the layer composes with authentication and the per-endpoint contract.

Guard bean: [MenuAuthorizationGuard](../../../../src/main/java/com/kosign/wecms/domain/navigation/service/MenuAuthorizationGuard.java)
(`@Component("menuAuthz")`). Method security enabled by `@EnableMethodSecurity` on
[SecurityConfig](../../../../src/main/java/com/kosign/wecms/config/SecurityConfig.java). Envelope is the
standard lowercase snake_case `{ status, data, timestamp?, path?, trace_id? }`.

---

## 1. The `@PreAuthorize` SpEL contract

A business controller (class-level or method-level) carries one of the following SpEL forms. The string
literals are kebab permission-code slugs (see the sibling
[menu-authorization](../menu-authorization/README.md) for the catalogue) — never legacy WSVC ids, never
user input.

| SpEL form | When used | Resolves |
|---|---|---|
| `@menuAuthz.canAccess('<slug>')` | the normal case — one endpoint serves one screen | a single cd through the 4-step rule |
| `@menuAuthz.canAccessAny('<a>','<b>',…)` | one endpoint legitimately serves several screens; discriminator not a bound arg (e.g. body-driven export, account-keyed transaction sub-resource) | allow if **any** cd passes the 4-step rule |
| `@menuAuthz.canAccessAccountInquiry(#currency)` | the multiplexed Account Inquiry list (`?currency=`) | VND/default → `banking-vnd-account-inquiry`; FCY/foreign → `banking-fcy-update-inquire` |
| `@menuAuthz.canAccessEInvoice(#type)` | the multiplexed e-Invoice list (`?type=SALES\|EXPENSE`) | `SALES` → `addon-einvoices-sales`; `EXPENSE(S)` → `addon-einvoices-expenses`; else fail-closed |
| `… and hasRole('ADMIN')` | admin-only screens | stacked: both `@menuAuthz` and the role authority must pass |
| `… and @userMgmtAccess.*` | Settings > Users | stacked: defence-in-depth admin guard |

The param-aware overloads bind a controller argument (`#currency`, `#type`) into the SpEL. Each still
runs the full per-code 4-step rule (validity → product-tree gate → admin bypass → per-user grant), so
product scope and grant still bind. Behaviour detail:
[functional-specification.md §1–2](functional-specification.md).

### 1.1 Class-level vs. method-level

A class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-login-credentials')")` gates **every**
handler in the controller (the coverage test treats a class-level gate as covering all methods).
Method-level gates are used where handlers in one controller serve different screens.

---

## 2. How it layers on every business endpoint

```
HTTP request → SecurityFilterChain
   1. (public?)        permit-all: /auth/login, /auth/logout, /actuator/health,
                       /payment/dispatch/callbacks/**  → no JWT, no @menuAuthz
   2. (authenticated?) JwtAuthenticationFilter parses JWT → Authentication + TenantContext
                       missing/invalid → 401 (JwtAuthenticationEntryPoint, jwt-authentication layer)
   3. (authorized?)    @PreAuthorize SpEL on the controller → MenuAuthorizationGuard
                       false → AccessDeniedException → 403 WBE1003
   4. handler runs (already tenant-scoped via TenantContext.currentBizKey())
```

The authorization layer is tier 3. Tier 2 (authentication) is the sibling
[jwt-authentication](../jwt-authentication/README.md); the two are distinct (401 vs. 403). The same JWT
that authenticates the request also carries the `authGrCd` / `prdtType` / `bizKey` claims the guard reads
— no endpoint accepts those as parameters.

---

## 3. Denial envelope — 403 `WBE1003`

A guard `false` → Spring `AccessDeniedException` → `GlobalExceptionHandler` `AccessDeniedException`
handler → HTTP **403** with code **`WBE1003`** in the standard error envelope:

```json
{
  "status":    { "error": true, "code": "WBE1003", "message": "Access denied." },
  "timestamp": "2026-06-23T09:41:12Z",
  "path":      "/api/v1/banking/reports/cash-flow",
  "trace_id":  "20260623094112PT06592776769567DEVL"
}
```

- `status.error = true` is the frontend's primary failure signal.
- The response never reveals *which* of the four steps denied (validity / product / grant) — it is a
  flat 403. Cross-tenant probing returns the same 403 (`bizKey` is from the JWT, not the request).
- `trace_id` is echoed in the `X-Trace-Id` header for log correlation.

### 3.1 401 vs. 403

| Situation | HTTP | Code | Layer |
|---|---|---|---|
| Missing / invalid / expired JWT | 401 | `WBE1401` (parity legacy `SES001`) / `WBE1009` | `JwtAuthenticationEntryPoint` (jwt-authentication) — fires **before** method security |
| Authenticated but guard returns false | **403** | **`WBE1003`** | `MenuAuthorizationGuard` → `AccessDeniedException` → `GlobalExceptionHandler` |

The entry point fires before the controller chain, so the `@RestControllerAdvice` never sees the 401
case — it is wholly the authentication layer's concern.

---

## 4. Error-code registry (authorization layer)

| Code | HTTP | Meaning |
|---|---|---|
| `WBE1003` | 403 | Authenticated but unauthorized — the `@menuAuthz` guard (or a stacked role gate) returned false |
| `WBE1401` / `WBE1009` | 401 | (not this layer) missing/invalid JWT — `jwt-authentication` |

This layer adds no new business codes of its own — it produces only `WBE1003` (via the shared
`AccessDeniedException` mapping). The per-endpoint success / business codes belong to each feature.

---

## 5. Reference

- **Which controller carries which permission cd** (the full endpoint → cd matrix):
  [docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md](../../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md).
- **Permission-code catalogue + grant model:** [menu-authorization](../menu-authorization/README.md).
- **The authentication layer this builds on:** [jwt-authentication](../jwt-authentication/README.md).
- **Frontend (UX gating, never a security boundary):**
  [wecms-web/docs/features/auth/authorization/README.md](../../../../../wecms-web/docs/features/auth/authorization/README.md).
