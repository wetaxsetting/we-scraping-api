# Add-on Service — Authorization & Product-Type Behaviour (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

**System:** Refactor (wecms-api) · **Status:** Verified against source 2026-06-06 (F-3 complete) · **Scope:** `/addon/sales-einvoices`, `/addon/expenses-einvoices`, `/addon/operation-status` and the **Add-on Service** top menu.

This doc is the single source of truth for how the backend authorizes the Add-on Service module per product type (H / W / M / F) and per user permission. It supersedes the older "product gate deferred" notes in the per-feature functional specs (now corrected).

---

## 1. Product types — what the code actually models

`common/enums/PrdtType` recognises **three** letters explicitly:

| Code | Product | Local R&D tenant |
|---|---|---|
| `F` | WeCMS Default | `VN_VNM0004_000001` |
| `M` | WeCMS Lumens | (no local tenant yet) |
| `H` | WeCMS HAENGSUNG Electronics | `VN_VNM0004_000014` |

The 11 legacy WABOOKS letters — **`W`** (WABOOKS Default), `R`, `B`, `E`, `U`, `L`, `S`, `P`, `C`, `D`, `T` — round-trip verbatim through the JWT (`prdtType` claim) and `TenantContext.currentPrdtType()`, but are **not modelled**: `PrdtType.fromCode("W")` returns `Optional.empty()`, and **no `PRODUCT_MENU` rows are seeded for them** (`V243` seeds F / M / H only).

> ⚠️ **Naming clarification needed (the "W" question).** The task asked about product types **"H, W, M"**. The refactor codebase does **not** use `W` for WeCMS — its WeCMS Default is **`F`**. `W` is the WABOOKS Default product and is *unmodelled* in the refactor. Two readings are possible and they behave very differently:
> - If "W" was shorthand for the WeCMS default → that is **`F`** in code (Add-on Service **enabled**, identical to M below).
> - If "W" literally means the WABOOKS `W` product → it is unmodelled: the user gets an **empty navigation tree** and **every** business endpoint (Banking, Payment, Add-on) returns 403. See §5.
>
> This should be confirmed with the PO before SIT sign-off. Everything below documents the **actual code behaviour** for each letter.

---

## 2. The authorization guard (`MenuAuthorizationGuard`, bean `@menuAuthz`)

`domain/navigation/service/MenuAuthorizationGuard.java` — the four-step rule applied to every gated endpoint:

1. **Validity** — the permission code must be a product-tree leaf (`MENU_ITEM.PERMISSION_CD`) or a `settings-*` code; unknown → deny (fail-closed).
2. **Product-tree gate** — a product-tree leaf must belong to **this tenant's product** (`PRODUCT_MENU` for `TenantContext.currentPrdtType()`). **Applies to admins too.** An H caller is denied any F/M-only code. Unmodelled letter / `NULL` product → no tree → denied.
3. **Admin bypass** — `authGrCd=ADMIN` skips the per-user grant, but **never** the product-tree gate.
4. **Per-user grant** — a normal user must hold `USE_YN='Y'` in `USER_MENU_AUTH_INFM` for the code.

Deny → Spring `AccessDeniedException` → **HTTP 403 / `WBE1003`**.

Two helper forms used by Add-on Service:
- `canAccessEInvoice(type)` — `SALES → addon-einvoices-sales`; `EXPENSE`/`EXPENSES → addon-einvoices-expenses`; unknown/blank/null → **false (fail-closed, no default)**.
- `canAccessAny(cd...)` — true if the caller may access *any* listed code; each code still runs the full four-step rule.

---

## 3. Where the gate is wired

| Endpoint | Method | Guard | Permission code(s) |
|---|---|---|---|
| `/api/v1/addon/einvoices` (list) | GET | `@menuAuthz.canAccessEInvoice(#p0)` | `addon-einvoices-sales` \| `addon-einvoices-expenses` (by `type`) |
| `/api/v1/addon/einvoices/history` | GET | `@menuAuthz.canAccessEInvoice(#p0)` | as above |
| `/api/v1/addon/einvoices/detail-history` | GET | `@menuAuthz.canAccessEInvoice(#p0)` | `addon-einvoices-sales` (Sales only) |
| `/api/v1/addon/einvoices/export` | POST | `@menuAuthz.canAccessAny(...)` | both e-Invoice codes |
| `/api/v1/addon/einvoices/export-details` | POST | `@menuAuthz.canAccessAny(...)` | both e-Invoice codes |
| `/api/v1/addon/einvoices/scrape-results` | POST | `@menuAuthz.canAccessAny(...)` | both e-Invoice codes |
| `/api/v1/addon/einvoices/scrape-detail-results` | POST | `@menuAuthz.canAccessAny(...)` | both e-Invoice codes |
| `/api/v1/addon/operation-status/**` | GET/POST | `@menuAuthz.canAccess('addon-operation-status-inquiry')` (class-level) | `addon-operation-status-inquiry` |

Sources: `domain/einvoice/controller/EInvoiceController.java` (lines 108, 149, 170, 192, 212, 234, 271); `domain/operation/controller/OperationStatusController.java` (class-level `@PreAuthorize`).

A Phase-F build test (`EndpointAuthorizationCoverageTest`) fails the build on any un-annotated, non-allow-listed handler — these gates cannot silently regress.

---

## 4. Product-tree seed (`V243`) — Add-on Service membership

`PRODUCT_MENU` rows for the Add-on subtree:

| Menu item (slug) | Permission code | F | M | H |
|---|---|:--:|:--:|:--:|
| `addon-service` (top menu) | — | ✅ | ✅ | ❌ |
| `addon-einvoice` (group) | — | ✅ | ✅ | ❌ |
| `addon-sales-einvoice` → `/addon/sales-einvoices` | `addon-einvoices-sales` | ✅ | ✅ | ❌ |
| `addon-expense-einvoice` → `/addon/expenses-einvoices` | `addon-einvoices-expenses` | ✅ | ✅ | ❌ |
| `addon-operation-status` (group) | — | ✅ | ✅ | ❌ |
| `addon-operation-status-inquiry` → `/addon/operation-status` | `addon-operation-status-inquiry` | ✅ | ✅ | ❌ |

`V243`: F = 41 nodes, M = 41 nodes (structurally identical to F; only VI/KO labels differ), **H = 33 nodes (no Add-on Service at all)**. H carries Banking + Payment only; its sole Reports difference is `banking-reports-daily-financial-hs` instead of the standard Daily Financial.

---

## 5. Expected behaviour per product type

### F (WeCMS Default) and M (WeCMS Lumens) — Add-on **ENABLED**

`addon-service` and all three leaves are in the product tree. Endpoints are reachable subject to the per-user grant.

### H (HAENGSUNG) — Add-on **DISABLED (all of it)**

No Add-on rows in the H product tree. Consequences:
- `GET /api/v1/navigation` for an H session returns **no `addon-service` node** → top menu + sidebar never show Add-on.
- Every Add-on endpoint returns **403 / `WBE1003`** — **even for an H admin** (product-tree gate is not admin-bypassable). A stale/mistaken `USER_MENU_AUTH_INFM` grant for an Add-on code on an H user is **inert** (no node, endpoint still 403).

### W (WABOOKS Default) and any other unmodelled letter / `NULL` — **EMPTY APP**

`PrdtType.fromCode` → empty; `NavigationService.resolve()` logs a warning and returns an **empty tree** (`new NavigationResponse(rawPrdt, null, List.of())`). The product-tree gate denies **every** product-tree leaf (Banking, Payment, Add-on alike) → all business endpoints 403. Only `settings-*` codes (product-invariant, grant-only) remain reachable. **This is not an Add-on-specific behaviour — a `W` tenant cannot use the app at all.** If the WeCMS product set is genuinely meant to include `W`, that is a missing seed (a `PRODUCT_MENU` tree for `W` + a `PrdtType.W` constant), not just an Add-on gap.

---

## 6. Permission behaviour (admin / normal-with / normal-without) on F/M

| Caller | Add-on top menu (navigation) | `/addon/*` endpoints |
|---|---|---|
| **Admin** (`authGrCd=ADMIN`) | All Add-on menus visible (product tree, grant bypassed) | All allowed (product-tree gate passes for F/M) |
| **Normal user WITH the matching grant** | Only the granted leaves appear; parent collapses if none granted | Allowed for the granted code(s); other Add-on endpoints 403 |
| **Normal user WITHOUT any Add-on grant** | `addon-service` collapses out (empty parent) → not shown | All Add-on endpoints 403 / `WBE1003` |

The same `USER_MENU_AUTH_INFM` grant set drives both `/api/v1/navigation` (what's visible) and `@menuAuthz` (what's callable) — by construction the two cannot drift.

---

## 7. Verdict

- **Backend authorization for Add-on Service is correctly implemented and SIT-ready for the modelled product types F, M, H** and for the admin / normal-with-grant / normal-without-grant cases. The product-tree gate + per-user grant + fail-closed `canAccessEInvoice` all match the intended design, and the navigation tree and endpoint guards share one source of truth.
- **One open item before sign-off — the "W" naming question (§1).** Confirm with the PO whether the team's "H, W, M" means `H` / *WeCMS-default* / `M` (i.e. `W` ≡ `F`, in which case behaviour is already correct) **or** the literal WABOOKS `W` product (in which case `W` is unmodelled and a `W` tenant is locked out of the entire app — a missing seed, not just an Add-on gap). No code change is warranted until this is clarified; document it as an open question.

---

## 8. Source files checked

- `common/enums/PrdtType.java`
- `domain/navigation/service/MenuAuthorizationGuard.java`
- `domain/navigation/service/NavigationService.java`
- `domain/einvoice/controller/EInvoiceController.java`
- `domain/operation/controller/OperationStatusController.java`
- `src/main/resources/db/migration/V243__navigation_menu_seed.sql` *(historical — NOT IN THE CURRENT SET; `V137`-`V139` create the nav tables but contain zero INSERTs, so the seed must be applied separately)*
- `wecms-api/CLAUDE.md` (Product-type rule; Authority-model F-3 bullet)
- Cross-ref: [docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md](../../../../docs/jex/features/menu-product-type-management/endpoint-authorization-matrix.md)

Frontend counterpart: [wecms-web/docs/features/addon-service/menu-route-authorization.md](../../../../wecms-web/docs/features/addon-service/menu-route-authorization.md).
