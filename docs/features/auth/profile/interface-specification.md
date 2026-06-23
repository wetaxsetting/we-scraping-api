# Auth > Profile — Interface Specification (Backend REST contract)

The auth-profile surface exposes one endpoint under base path **`/api/v1/users`**. Wire format is
lowercase snake_case (global Jackson `SNAKE_CASE`); envelope is
`{ status, data, timestamp?, path?, trace_id? }`. Controller:
[UserController.java](../../../../src/main/java/com/kosign/wecms/domain/auth/controller/UserController.java).

## Authorization

```text
JWT only — no @PreAuthorize / @menuAuthz on UserController.
UserController is allow-listed in EndpointAuthorizationCoverageTest (deny-or-declared coverage).
```

Any authenticated tenant user may call the endpoint (parity with legacy `comm_0192_001_r001`,
`NORM_USER_ONLY_YN = Y`). Missing / invalid JWT → 401 `WBE1401`. Tenant scope rides on the JWT
(`TenantContext.currentBizKey()`); the endpoint accepts no `bizKey`.

---

## 1. Tenant user directory

```
GET /api/v1/users
GET /api/v1/users?q=an
```

| Query param | Required | Rule |
|---|---|---|
| `q` | no | case-insensitive substring filter on `USER_NM`, ≤100 chars (`@Size`); blank / omitted → whole active directory |

### Response

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "total_count": 2,
    "users": [
      {
        "user_id": "alice@example.com",
        "user_nm": "Alice Tran",
        "posi_nm": "Accountant",
        "dept_nm": "Finance",
        "user_img_url": "/api/v1/users/alice@example.com/image"
      },
      {
        "user_id": "bob@example.com",
        "user_nm": "Bob Le",
        "posi_nm": null,
        "dept_nm": null,
        "user_img_url": null
      }
    ]
  }
}
```

- `total_count` is the length of `users` (no server-side pagination / windowing).
- `users` are **active tenant users only** (`USER_BUSI_CONN_INFM.DEL_YN = 'N'`), ordered
  `user_nm ASC, user_id ASC`.
- `user_img_url` is the slug-based template `/api/v1/users/{userId}/image` when the user carries a
  per-tenant `USER_BUSI_CONN_INFM.USER_IMG`, otherwise `null`. **The raw `USER_IMG` value is never
  returned.** (The streaming endpoint behind that URL is the Settings > Users path in the current tree;
  the auth-side streaming endpoint is an Approval Line follow-up MR — the frontend `<Avatar>` tolerates
  a 404.)

### Errors

| Code | HTTP | When |
|---|---|---|
| `WBE_VALIDATION` | 200/400 | `q` exceeds 100 chars |
| `WBE1401` | 401 | JWT missing / invalid / expired |
| `WBE9999` | 500 | No tenant on `TenantContext` (configuration-bug guard) |

An unknown / cross-tenant / soft-deleted user is **not** an error — it simply does not appear in
`users`. There is no per-menu grant, so no 403 path on this endpoint.

---

## Related endpoints (documented elsewhere)

| Endpoint | Where | Why not here |
|---|---|---|
| `GET /api/v1/auth/me` | **session-management** | session identity (`user_id` … `tax_id`, menu grants) — the "who am I" half of profile |
| `POST/GET/DELETE /api/v1/settings/users/{user_id}/image` | **Settings > Users** ([README](../../settings/user-management/README.md)) | avatar bytes CRUD — admin-gated, APPROVED DEVIATION (out of auth scope) |
| *(self-profile edit)* | — | **MISSING** — no auth endpoint exists; FE avatar-edit pencil is UI-only |

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_VALIDATION` | 200/400 | `q` length validation failure |
| `WBE1401` | 401 | JWT missing / invalid / expired |
| `WBE9999` | 500 | Unexpected server error (incl. missing-tenant guard) |

Frontend consumption:
[wecms-web/docs/features/auth/profile/api-consumption.md](../../../../../wecms-web/docs/features/auth/profile/api-consumption.md)
*(FE folder pending)*.
