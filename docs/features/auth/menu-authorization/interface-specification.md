# Menu Authorization — Interface Specification (Backend REST contract)

Wire format is lowercase snake_case (global Jackson `SNAKE_CASE`); envelope is
`{ status, data, timestamp?, path?, trace_id? }`. Controllers:
[NavigationController.java](../../../../src/main/java/com/kosign/wecms/domain/navigation/controller/NavigationController.java)
and (entitlement, reference) the `menu-entitlement` endpoints on
[UserManagementController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/users/controller/UserManagementController.java).

## Authorization

| Endpoint | Gate |
|---|---|
| `GET /api/v1/navigation` | JWT only (`anyRequest().authenticated()`; `NavigationController` is allow-listed — no `@menuAuthz`). Product + identity ride on the signed JWT |
| `GET / PUT /api/v1/settings/users/{user_id}/menu-entitlement` | `@PreAuthorize("hasRole('ADMIN') and @menuAuthz.canAccess('settings-core-users')")` + service-level `currentIsAdmin()` defense-in-depth on save |

Missing/invalid JWT → 401; navigation called without a populated context → `WBE1401`.
Authenticated-but-unauthorized → 403 `WBE1003`. No endpoint accepts a `bizKey` or `prdt_type` — both
come from `TenantContext`.

---

## 1. Resolve navigation tree

```
GET /api/v1/navigation
```
```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "product_type": "F",
    "menu_group_id": "WE_CMSVN",
    "top_menus": [
      {
        "id": "banking",
        "permission_cd": null,
        "parent_id": null,
        "depth": 0,
        "seq": 0,
        "label": "Banking",
        "route": null,
        "icon": "ico-banking",
        "legacy_mnu_id": "MNU_BANKING",
        "legacy_wsvc_id": null,
        "children": [
          {
            "id": "banking-reports",
            "permission_cd": null,
            "parent_id": "banking",
            "depth": 1,
            "seq": 0,
            "label": "Reports",
            "route": null,
            "icon": null,
            "legacy_mnu_id": "MNU_BANKING_REPORTS",
            "legacy_wsvc_id": null,
            "children": [
              {
                "id": "banking-report-daily-financial",
                "permission_cd": "banking-reports-daily-financial",
                "parent_id": "banking-reports",
                "depth": 2,
                "seq": 1,
                "label": "Daily Financial",
                "route": "/banking/reports/daily-financial",
                "icon": null,
                "legacy_mnu_id": "MNU_DAILY_FINANCIAL",
                "legacy_wsvc_id": "rept_0015_001",
                "children": []
              }
            ]
          }
        ]
      }
    ]
  }
}
```

- `top_menus` are the depth-0 roots, each carrying nested `children` — one resolved hierarchy, already
  filtered to what the caller may see (`Product Tree ∩ grants`, or the full product tree for an admin)
  and ordered by `seq` within each parent.
- `permission_cd` / `route` are `null` for pure parents.
- **`menu_group_id`** echoes the source tree (`F→WE_CMSVN`, `M→WE_CMSVN_LUMENS`,
  `H→WE_CMSVN_HAENGSUNG`); `null` when the product type is not a recognised navigation product.

**Degraded responses (well-formed, not errors):**

```json
// unrecognised / null product type (one of the 11 deferred letters, or NULL)
{ "status": {...}, "data": { "product_type": "W", "menu_group_id": null, "top_menus": [] } }

// recognised product but no membership seeded / no grants for a normal user
{ "status": {...}, "data": { "product_type": "F", "menu_group_id": "WE_CMSVN", "top_menus": [] } }
```

**H tenant** resolves the reduced tree — `banking-reports-daily-financial-hs` instead of the standard
Daily Financial, no Add-on subtree, no Daily Cash / Cash Flow.

---

## 2. Load menu entitlement (reference — Settings > Users)

```
GET /api/v1/settings/users/{user_id}/menu-entitlement
→ data: { "user_id": "alice@kosign.dev", "menu_auth_cds": ["addon-einvoices-sales", "banking-vnd-account-inquiry"] }
errors: WBE_USERS_NOT_FOUND (no active link in tenant) · WBE1003 (non-admin / missing settings-core-users)
```

Returns the user's `USE_YN='Y'` grant slugs in insertion order. The frontend renders the panel by
intersecting this list against its static `ENTITLEMENT_SECTIONS` tree.

---

## 3. Save menu entitlement (reference — Settings > Users)

```
PUT /api/v1/settings/users/{user_id}/menu-entitlement
{ "menu_auth_cds": ["addon-einvoices-sales", "addon-einvoices-expenses"] }
→ 200, data: { "user_id": "alice@kosign.dev", "menu_auth_cds": ["addon-einvoices-sales", "addon-einvoices-expenses"] }
errors: WBE_VALIDATION (menu_auth_cds null, or a slug outside 1..100 chars) · WBE_USERS_NOT_FOUND · WBE1003
```

- **Full-set replace** (delete-then-insert) — the body is the complete grant set; a missing slug = revoke.
- **Empty list is valid** — `"menu_auth_cds": []` revokes every grant. **Null is rejected** (`WBE_VALIDATION` —
  `@NotNull`), since null would be ambiguous with "untouched".
- **Idempotent + retry-once** on a concurrent PK race (last-writer-wins).
- Each slug is `@Size(min = 1, max = 100)`.

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE1401` | 200 | Navigation called without a populated session context |
| `WBE_USERS_NOT_FOUND` | 200 | Entitlement target user has no active link in the tenant |
| `WBE_VALIDATION` | 200/400 | Bean-validation failure (`menu_auth_cds` null / slug length) |
| `WBE1009` | 401 | JWT missing / invalid (Spring entry point) |
| `WBE1003` | 403 | Authenticated but lacks role / `settings-core-users` grant (non-admin save) |
| `WBE9999` | 500 | Unexpected server error |

Frontend consumption:
[wecms-web/docs/features/auth/menu-authorization/api-consumption.md](../../../../../wecms-web/docs/features/auth/menu-authorization/api-consumption.md).
