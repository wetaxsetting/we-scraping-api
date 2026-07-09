# Auth > Profile — Functional Specification (Backend)

The implemented behaviour of the auth-profile backend surface. JEX behaviour reference (read-only):
`comm_0192_001_r001` ("Inquiry Approver") + IDO `USER_BUSI_CONN_INFM_R004`.

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | Tenant user directory (active users, optional `q` filter) | GET | `/api/v1/users` | `comm_0192_001_r001` (`USER_BUSI_CONN_INFM_R004`) | No |

`GET /api/v1/auth/me` (session identity) is documented in **session-management**, not here. Avatar
image CRUD + self-profile edit are **out of scope** — see [§ 4](#4-scope-boundary).

---

## 2. `GET /api/v1/users` behaviour

### 2.1 Inputs

| Param | In | Required | Length | Rule |
|---|---|---|---|---|
| `q` | query | no | ≤100 | case-insensitive substring filter on `USER_NM`; `@Size(max=100)` (class-level `@Validated`); null / blank → whole directory |

No `bizKey` is ever accepted — tenant scope rides on the JWT (`TenantContext.currentBizKey()`).

### 2.2 Filter

- `q` blank / omitted → returns the **whole active directory** for the tenant.
- `q` present → `LOWER(USER_NM) LIKE LOWER('%' || q || '%')` — case-insensitive substring on the user
  name only (verbatim legacy `ui.user_nm ilike '%'||?||'%'` semantic). `q` is trimmed before use.
- `q` over 100 chars → `WBE_VALIDATION` (bean-validation failure on the request param).

### 2.3 Tenant scope

- Rows are constrained to `USER_BUSI_CONN_INFM.BIZ_KEY = TenantContext.currentBizKey()`.
- A missing / blank tenant on the context throws `WBE9999` — a configuration-bug guard (the JWT filter
  is expected to populate `TenantContext` on every authenticated request), not a normal business path.

### 2.4 Returns **active** users only

- `USER_BUSI_CONN_INFM.DEL_YN = 'N'` filters soft-deleted tenant links; a user removed from the tenant
  never appears in the directory.
- One row per active tenant link, joined to `USER_INFM` for name / position / department.
- Order: `USER_NM ASC, USER_ID ASC` (name-sorted, id tiebreaker for deterministic ordering).
- No server-side pagination — the whole result set is returned (legacy parity).

### 2.5 Output

`TenantUserListResponse { total_count, users: TenantUserRow[] }` where `total_count` is the row count
(no windowing). Each `TenantUserRow`:

| Wire field (snake) | Type | Notes |
|---|---|---|
| `user_id` | string | picker value / row key |
| `user_nm` | string | display name; `q` filter target |
| `posi_nm` | string \| null | position |
| `dept_nm` | string \| null | department |
| `user_img_url` | string \| null | `/api/v1/users/{userId}/image` when `USER_IMG` is non-blank, else `null` — the raw `USER_IMG` value is never on the wire |

**No phone number, no admin/mobile sub-flags, no email** — those are deliberately not surfaced here
(see [§ 5](#5-intentional-divergences-from-legacy)).

---

## 3. Authorization

- **JWT only.** `UserController` carries **no** `@PreAuthorize` / `@menuAuthz` annotation. It is
  registered in `ALLOWLISTED_CONTROLLERS` in
  [EndpointAuthorizationCoverageTest](../../../../src/test/java/com/kosign/wecms/config/EndpointAuthorizationCoverageTest.java)
  (the deny-or-declared coverage test), so the build does not flag it as an un-annotated handler. This
  is intentional parity with legacy `comm_0192_001_r001` (`NORM_USER_ONLY_YN = Y`, no per-menu grant) —
  any authenticated tenant user may read the directory to pick an approver.
- Missing / invalid JWT → 401 `WBE1401` (via `JwtAuthenticationEntryPoint`).
- Param validation via `@Size` / `@Validated` → `WBE_VALIDATION` on an over-length `q`.

---

## 4. Scope boundary

This is the load-bearing distinction for this module.

| Surface | Owner | Endpoint | In auth-profile scope? |
|---|---|---|---|
| **Tenant user directory** | `domain/auth` | `GET /api/v1/users` | **Yes** — this spec |
| **Current session identity** | `domain/auth` | `GET /api/v1/auth/me` | Cross-ref — **session-management** module |
| **Avatar image up/get/del** | `domain/setting/users` | `POST/GET/DELETE /api/v1/settings/users/{user_id}/image` | **No** — APPROVED DEVIATION (Settings > Users) |
| **Self-profile edit** | — | *(none)* | **MISSING** — no auth endpoint; FE avatar pencil is UI-only |

- **`/me`** returns the session's own identity (`user_id`, `user_nm`, `biz_key`, `cmpn_nm`, `tax_id`,
  `auth_gr_cd`, `prdt_type`, menu grants) from `TenantContext` + `BUSI_INFM`. Documented in
  session-management. It does not return avatar bytes and does not allow edits.
- **Avatar CRUD** is admin-gated and writes `USER_BUSI_CONN_INFM.USER_IMG`; it lives in Settings >
  Users (`UserImageService`). The auth directory only reads the `USER_IMG` presence flag. See
  [../../settings/user-management/README.md](../../settings/user-management/README.md).
- **Self-profile edit** has no backend endpoint at all; profile changes are admin-driven via Settings >
  Users. Classified **MISSING** — the frontend's ProfileMenu avatar-edit pencil is a UI-only
  placeholder with no handler / endpoint.

---

## 5. Intentional divergences from legacy

Two `comm_0192_001_r001` columns are deliberately not ported into the directory projection (documented
in `UserDirectoryService` / `TenantUserRow` Javadoc):

- **`CLPH_NO`** (phone) — legacy decrypts it via the `decrypt()` DB function. The Approval Line picker
  has no phone column; porting a new legacy-function dependency for an unused field is not justified.
  **Status: DEAD LEGACY.**
- **`ADMIN_YN` / `MOB_YN`** sub-flags — inherited from the broader `USER_BUSI_CONN_INFM_R004` IDO; not
  consumed by the picker. **Status: DEAD LEGACY.**

These are not regressions — the legacy fields exist for IDO completeness but the Inquiry-Approver
picker does not render them. Full classification: [refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE_VALIDATION` | 200/400 | `q` exceeds 100 chars (bean validation) |
| `WBE1401` | 401 | JWT missing / invalid / expired (via `JwtAuthenticationEntryPoint`) |
| `WBE9999` | 500 | Missing tenant on `TenantContext` (configuration-bug guard) — not a normal path |

The directory has no business-not-found / forbidden codes: an unknown / cross-tenant user simply does
not appear in the result set (active-link + tenant filter), and there is no per-menu grant to deny.
Full request/response shapes: [interface-specification.md](interface-specification.md).
