> ⚠️ **SUPERSEDED (2026-06-05).** This is the original 2026-05-14 implementation *plan*. The
> feature shipped and now diverges from this document (no plan cap, `/api/v1/settings/users`
> base path, Menu Entitlement + Avatar implemented, split read/write authorization). The
> authoritative backend documentation is the folder
> **[user-management/](user-management/README.md)**; the drift is itemized in
> [user-management/refactor-gap-analysis.md §Plan-vs-shipped drift](user-management/refactor-gap-analysis.md#3-plan-vs-shipped-drift).
> Kept here for historical traceability — do not treat as current.

# User Management — Implementation Plan

**Slug:** `user-management`
**System:** Refactor (Spring Boot + Next.js)
**Screen:** Settings > Users (Figma 142:22998 empty · 142:23223 selected · 142:17631 add)
**JEX functional reference:** `admn_0021_*` WSVCs + `USER_INFM` / `USER_BUSI_CONN_INFM` / `BUSI_ADMN_INFM` / `USER_MENU_AUTH_INFM` tables
**Status:** Implemented — User Management endpoints are live (check-id, GET/PUT/DELETE /{user_id}, menu-entitlement GET/PUT, image upload/get/delete)
**Date:** 2026-05-14
**Author:** Claude (Opus 4.7)

---

## 1. Goal

Wire the **Settings > Users** panel end-to-end:

- Frontend UI is **already built** (static UI complete, `components/settings/users/*`). Forms are uncontrolled and Save is a no-op.
- Backend ships the full User Management surface via `UserManagementController` (`/api/v1/settings/users`).
- This MR adds: full CRUD over `USER_INFM` + tenant link in `USER_BUSI_CONN_INFM`, hard 5-user plan limit, admin-role toggle through `BUSI_ADMN_INFM`, menu-entitlement read/write through `USER_MENU_AUTH_INFM`, and the React Hook Form + API wiring on the panel.

Scope is **full-stack feature** with **hard plan cap**.

---

## 2. Out of Scope

- **No new Flyway migrations / no schema changes.** This MR uses ONLY tables that already exist in the local schema: `USER_INFM` (V1 / V7), `USER_BUSI_CONN_INFM` (V1), `BUSI_ADMN_INFM` (V11). No new tables, no `ALTER TABLE`, no `ddl-auto: update` — `ddl-auto` stays `validate`. The repo-wide rule "Flyway owns the schema" is preserved.
- **Menu Entitlement persistence.** `USER_MENU_AUTH_INFM` would require a new Flyway migration, which is excluded. The Menu Entitlement tab stays **static client-side** for this MR — the checkboxes remain `disabled` placeholders as in the current static UI. A follow-up MR adds the table + read/write endpoints.
- **Avatar upload pipeline.** UI shows a drag-drop dotted disc; no `USER_IMG` storage flow yet. The component (`UserAvatarUpload.tsx`) stays inert; the upload bytes route is deferred to a follow-up MR.
- **Password reset by admin** beyond the create-time password the admin sets. Self-service "Change Password" is a separate ProfileMenu flow (already noted as deferred).
- **Tenant subscription / plan-tier table.** The "5 users" cap is hard-coded in a service constant for this MR — a `BUSI_PLAN_INFM`-style table is a later MR if/when product tier-pricing lands.
- **5-failure auto-lock counter, `LOCK_YN` admin toggle from this screen.** Deferred to the same auth-stabilisation MR that completes `LOCK_YN`/`ACTV_YN` flows.
- **JEX coexistence writes.** Refactor is local-only R&D; no dual-writes to the live JEX DB.

---

## 3. Architecture summary

```
┌─ wecms-web ─────────────────────────────────────────────────────┐
│ components/settings/users/                                            │
│   UsersPanel.tsx           — list/view/edit/add state machine         │
│   AddUserInfoForm.tsx      — RHF + Zod, POST /users                   │
│   EditUserInfoForm.tsx     — RHF + Zod, PUT /users/{userId}           │
│   UserDetailView.tsx       — read-only render                         │
│   UserMenuEntitlementPanel — STATIC (placeholder, no API)             │
│   useUsers.ts (NEW)        — useApiData + mutation helpers            │
│   types.ts                 — SettingsUser (camelCase in-memory)       │
│                                                                       │
│ lib/api/users.ts (NEW)     — fetchUsers / createUser / updateUser     │
│                             deleteUser / checkLoginIdAvailable        │
│ lib/types/api.ts           — ApiUserRow / ApiUserCreateRequest …       │
└──────────────────────────────────────────────────────────────────────┘
                              │ HTTPS JWT-bound
                              ▼
┌─ wecms-api ─────────────────────────────────────────────────────┐
│ domain/setting/users/                                                 │
│   UserManagementController  GET/POST/PUT/DELETE /api/v1/users         │
│                             GET /api/v1/users/check-id?login_id=…     │
│   UserManagementService     CRUD + plan-cap guard + admin toggle      │
│   dto/                      records (camelCase Java → snake_case wire)│
│                                                                       │
│ Tables read/written (ALL ALREADY EXIST — no Flyway in this MR):       │
│   USER_INFM, USER_BUSI_CONN_INFM, BUSI_ADMN_INFM                      │
└──────────────────────────────────────────────────────────────────────┘
```

All endpoints tenant-scoped via `TenantContext.currentBizKey()`. Admin gate via `@PreAuthorize("hasRole('ADMIN')")` (admin-only screen — see §10 OQ-1).

---

## 4. Database

### 4.1 No new Flyway migrations

`USER_INFM`, `USER_BUSI_CONN_INFM`, `BUSI_ADMN_INFM` are **already in place** (V1 / V7 / V11). This MR reads/writes those tables only — no `CREATE TABLE`, no `ALTER TABLE`, no new `V{N}__*.sql` file. `spring.jpa.hibernate.ddl-auto` stays `validate` per the repo-wide rule.

### 4.2 No new entities

The required JPA entities already exist: `UserInfm`, `UserBusiConnInfm` (+ `UserBusiConnInfmId`), `BusiInfm`. This MR adds **new repositories / services / controllers / DTOs**, not new entities.

If a column the new service needs to write (e.g. `USER_INFM.POSI_NM`, `DEPT_NM`, `CLPH_NO`) is present on the DB but not yet mapped on the entity, extend the existing entity to add the `@Column` — no DDL required because the column is already there from the V7 legacy-shape alignment. Verify each column with the `postgres_dev_server_jexframe` MCP before extending.

### 4.3 No `wb_get_next_val` change

`USER_INFM` PK is the user-supplied `USER_ID` (login id). No sequence, no new branch.

---

## 5. Backend (`wecms-api`)

### 5.1 Package layout

```
domain/setting/users/
├── controller/UserManagementController.java
├── service/
│   ├── UserManagementService.java
│   └── UserManagementConstants.java    — MAX_USERS_PER_TENANT = 5
├── dto/
│   ├── UserRowResponse.java            — list / detail row
│   ├── UserCreateRequest.java
│   ├── UserUpdateRequest.java
│   └── CheckLoginIdResponse.java
└── repository/
    └── (reuse existing UserInfmRepository, UserBusiConnInfmRepository,
          BusiAdmnInfmRepository — add new methods as needed)
```

### 5.2 Entity additions

- **No new entities.** Reuse `UserInfm`, `UserBusiConnInfm` (+ `UserBusiConnInfmId`), `BusiAdmnInfm` (verify it exists; if absent, add `@Entity` mapped to the existing V11 table — still no DDL).
- If columns on `USER_INFM` need to be writable that aren't yet on the entity (`POSI_NM`, `DEPT_NM`, `CLPH_NO`, `EML`, `USER_NM`), add `@Column` annotations to the existing `UserInfm` — the columns are already in the DB.

### 5.3 Endpoints

| Method | Path | Returns | Notes |
|---|---|---|---|
| GET    | `/api/v1/users` | `UserRowResponse[]` + `total_count` + `plan_limit` | Tenant-scoped, ordered by REG_DTM DESC |
| GET    | `/api/v1/users/{userId}` | `UserRowResponse` | Includes admin flag, position, department |
| GET    | `/api/v1/users/check-id?login_id=…` | `CheckLoginIdResponse(available: boolean)` | For the "Check availability" button |
| POST   | `/api/v1/users` | `UserRowResponse` | Plan-cap enforced; `WBE_USERS_PLAN_LIMIT_REACHED` if count ≥ 5 |
| PUT    | `/api/v1/users/{userId}` | `UserRowResponse` | Updates `USER_NM`, `POSI_NM`, `DEPT_NM`, `CLPH_NO`, `EML`, optional password reset, admin flag |
| DELETE | `/api/v1/users/{userId}` | `200 + status` | Per-tenant unlink: `USER_BUSI_CONN_INFM.DEL_YN='Y'` + delete `BUSI_ADMN_INFM` row, in one tx. `USER_INFM` row left intact (see OQ-3). |

**Menu Entitlement endpoints are NOT in this MR** — see §2 Out of Scope. The tab in the panel remains the static placeholder.

### 5.4 Plan-cap rule (hard)

`UserManagementService.create(...)`:

1. Count active links: `userBusiConnRepo.countByBizKeyAndDelYn(bizKey, "N")`.
2. If `count >= UserManagementConstants.MAX_USERS_PER_TENANT (= 5)` → throw `BusinessException("WBE_USERS_PLAN_LIMIT_REACHED")`.
3. Otherwise proceed: `USER_INFM` insert (BCrypt password), `USER_BUSI_CONN_INFM` insert (`DEL_YN='N'`), optional `BUSI_ADMN_INFM` insert if `set_as_admin` is true.

Wrapped in `@Transactional` so the count + insert are atomic at the JPA isolation level. PK collision on `USER_INFM.USER_ID` surfaces as `WBE_USERS_DUPLICATE_LOGIN_ID` (we already validate via the check-id endpoint; this is a race-condition fallback).

### 5.5 Admin toggle

`set_as_admin = true` →
- if `BUSI_ADMN_INFM` row absent for `(BIZ_KEY, USER_ID)` → insert.
- if present → no-op.

`set_as_admin = false` →
- if row present → delete.
- if absent → no-op.

`USER_BUSI_CONN_INFM.DEL_YN` is **not** touched by the admin flag — the user stays linked; only the admin master changes.

### 5.5a Delete semantics

Soft-delete is **per-tenant unlink only** (see OQ-3 default): `UPDATE USER_BUSI_CONN_INFM SET DEL_YN='Y'` for the `(BIZ_KEY, USER_ID)` pair + `DELETE FROM BUSI_ADMN_INFM` for the same pair, both in one `@Transactional` method. `USER_INFM.DEL_YN` is left as-is so the user record survives if they also belong to another tenant. The deleted user no longer appears in the list for this tenant and no longer authenticates against this tenant.

### 5.6 Error codes (new in `messages.properties`)

| Code | Message |
|---|---|
| `WBE_USERS_NOT_FOUND` | User not found in tenant. |
| `WBE_USERS_PLAN_LIMIT_REACHED` | Your current plan can only add up to 5 users. |
| `WBE_USERS_DUPLICATE_LOGIN_ID` | This ID is already taken. |
| `WBE_USERS_LOGIN_ID_REQUIRED` | Login ID is required. |
| `WBE_USERS_PASSWORD_TOO_WEAK` | Password must be at least 8 characters and include a special character. |
| `WBE_USERS_CANNOT_DELETE_SELF` | You cannot delete your own account from this screen. |
| `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN` | At least one admin must remain. |

### 5.7 Audit

Every create / update / delete writes `AUDIT_LOGS_INFM` with `WSVC_ID='user-management'` (refactor slug, per "Refactor identifier naming — slugs, not WSVC ids"). Reuses the audit infrastructure introduced for `firm-banking-settings` / `contacts` / `chart-of-accounts`.

### 5.8 Authorisation

`@PreAuthorize("hasRole('ADMIN')")` on every write endpoint. List + check-id are admin-only too (the panel itself is admin-gated on the frontend). Self-update is **not** allowed through this screen — see `WBE_USERS_CANNOT_DELETE_SELF` and a matching guard on PUT to prevent demoting your own admin flag if you're the last admin.

---

## 6. Frontend (`wecms-web`)

### 6.1 New files

```
lib/api/users.ts                       — Axios wrappers, snake_case ↔ camelCase mappers
hooks/useUsers.ts                      — useApiData list + mutation helpers
components/settings/users/
  useUserForm.ts                       — Zod schema + RHF defaults (shared add/edit)
```

`UserMenuEntitlementPanel` is **NOT modified** in this MR — checkboxes stay `disabled` placeholders as today. No `useUserMenuEntitlement` hook, no entitlement Axios wrapper.

### 6.2 Wire types ([lib/types/api.ts](../../../../wecms-web/lib/types/api.ts))

```ts
export interface ApiUserRow {
    user_id: string;
    user_nm: string;
    eml: string;
    clph_no: string;
    posi_nm: string | null;
    dept_nm: string | null;
    is_admin: boolean;
    is_current_user: boolean;
    user_img_url: string | null;
}

export interface ApiUserListResponse {
    users: ApiUserRow[];
    total_count: number;
    plan_limit: number;       // = 5 for this MR
}

export interface ApiUserCreateRequest {
    user_id:        string;       // login id
    user_nm:        string;
    user_pw:        string;
    eml:            string;
    clph_no:        string;
    posi_nm:        string;
    dept_nm:        string;
    set_as_admin:   boolean;
}

export interface ApiUserUpdateRequest {
    user_nm:        string;
    eml:            string;
    clph_no:        string;
    posi_nm:        string;
    dept_nm:        string;
    set_as_admin:   boolean;
    user_pw?:       string;       // optional reset
}

export interface ApiCheckLoginIdResponse {
    available: boolean;
}
```

### 6.3 In-memory types ([types.ts](../../../../wecms-web/components/settings/users/types.ts))

Existing `SettingsUser` already covers the camelCase shape. Extend with:

```ts
export type UserListMeta = {
    totalCount:  number;
    planLimit:   number;        // 5
    isAtLimit:   boolean;       // totalCount >= planLimit
};
```

### 6.4 `UsersPanel` rewiring

Replace `mockUsers` with `useUsers()` (from `useApiData`). The panel state machine stays — only the data source changes. Three behavioural additions:

1. **Plan-limit subtitle.** `t("settings.users.description", { used: meta.totalCount, max: meta.planLimit })` so the existing description string ("Your current plan can only add up to 5 users") interpolates dynamically.
2. **Add-button disabled.** `<UsersListPanel>` receives `disabledAdd={meta.isAtLimit}`; the "+ Add a user" affordance renders with `cursor-not-allowed`, no hover, and `aria-disabled="true"` when full. Clicking shows a `useDialog({ type: "warning" })` with the same i18n key as the backend error.
3. **Backend error surfacing.** `WBE_USERS_PLAN_LIMIT_REACHED` (race-condition fallback) → `useDialog({ type: "error", errorCode })`.

### 6.5 Add / Edit form wiring

Both forms become **controlled** via React Hook Form + Zod (replacing the current uncontrolled inputs). Zod schema in `useUserForm.ts`:

```ts
const userFormSchema = z.object({
    fullName:     z.string().min(1, "settings.users.error.full_name_required"),
    position:     z.string().min(1, "settings.users.error.position_required"),
    department:   z.string().min(1, "settings.users.error.department_required"),
    phoneCode:    z.string().min(1),
    phoneNumber:  z.string().min(1, "settings.users.error.phone_required"),
    email:        z.string().email("settings.users.error.email_invalid"),
    loginId:      z.string().min(1, "settings.users.error.id_required"),
    password:     z.string().min(8).regex(/[^a-zA-Z0-9]/, "settings.users.error.password_weak"),
    confirm:      z.string(),
    setAsAdmin:   z.boolean(),
}).refine((v) => v.password === v.confirm, {
    message: "settings.users.error.confirm_mismatch",
    path:    ["confirm"],
});
```

Edit-mode variant: `password` + `confirm` are optional (omit-to-keep). `loginId` is read-only (immutable PK).

### 6.6 "Check availability" button

`onClick` → `checkLoginIdAvailable(loginId)` → toast via `useDialog({ type: "success" | "error" })`. Disabled until `loginId` is non-empty.

### 6.7 Menu Entitlement panel — unchanged (static)

`UserMenuEntitlementPanel` is **not in scope** for this MR (no `USER_MENU_AUTH_INFM` table, no read/write endpoint). The tab continues to render the static `ENTITLEMENT_SECTIONS` mock with `disabled` checkboxes. A follow-up MR adds Flyway V98 + the entitlement endpoints + interactive wiring.

### 6.8 i18n keys (new)

Add to `locales/{en,ko,vi}.json` (no `km.json`):

```
settings.users.description                "Your current plan can only add up to {max} users"
settings.users.plan_limit_reached.title   "Plan limit reached"
settings.users.plan_limit_reached.body    "You can only add up to {max} users on your current plan."
settings.users.error.full_name_required   "Full name is required."
settings.users.error.position_required    "Position is required."
settings.users.error.department_required  "Department is required."
settings.users.error.phone_required       "Phone is required."
settings.users.error.email_invalid        "Email is invalid."
settings.users.error.id_required          "ID is required."
settings.users.error.password_weak        "Password must be at least 8 characters with a special character."
settings.users.error.confirm_mismatch     "Passwords do not match."
settings.users.error.duplicate_id         "This ID is already taken."
settings.users.check_id.available         "This ID is available."
settings.users.check_id.taken             "This ID is already taken."
```

Korean + Vietnamese translations from the live `dev.wecms.vn` strings where they exist; otherwise omit (per `feedback` memory — never invent translations).

### 6.9 Dialog variants

Following the §3 dialog rules:

| Trigger | Variant | Title |
|---|---|---|
| Form Zod validation fails | `error` | `t("dialog.error")` |
| Backend rejects create (any WBE) | `error` | `t("dialog.error")` |
| User clicks Delete | `confirmation` | `t("dialog.confirmation")` |
| Save succeeds | `success` | `t("dialog.success")` |
| User clicks "+ Add" when at cap | `warning` | `t("dialog.warning")` |

---

## 7. View Setting

**Not adopted** for this screen. The Users panel has no toolbar list-icon button and no Figma-shown View Setting popover. Same precedent as `approval-line`, `erp-transaction-history`, `chart-of-accounts`. Slug reservation in `SCREEN_IDS` is **not** required for this MR.

---

## 8. Excel export

**Not in scope.** Users list has no toolbar Excel icon in Figma.

---

## 9. Docs

| Doc | Path | Action |
|---|---|---|
| Feature spec | `docs/refactor-wecms/features/admin/user-management.md` | **This file** |
| Migration mapping | `docs/refactor-wecms/migration/user-management-mapping.md` | New — JEX `admn_0021_*` WSVC ↔ Spring REST table |
| Backend design note | `docs/refactor-wecms/backend/user-management.md` | New — service/repository details |
| JEX functional spec | `docs/jex/features/user-management.md` | New — read-mirror of live JEX behaviour for traceability (slug must match) |

CLAUDE.md updates (same MR):

- `wecms-api/CLAUDE.md` → Feature Status table: add `domain/setting/users/` row.
- `wecms-api/CLAUDE.md` → Endpoints table.
- `wecms-api/CLAUDE.md` → Error codes table (7 new codes).
- `wecms-api/CLAUDE.md` → **No Flyway migrations table update** — this MR adds no migrations.
- `wecms-web/CLAUDE.md` → §3a Settings panels table: flip Users Info-tab from "Static UI" to "Working" (Menu Entitlement tab stays "Static UI").

---

## 10. Open Questions (raise with PO before coding)

| # | Question | Default if PO unavailable |
|---|---|---|
| OQ-1 | Should the Users screen be admin-only (`@PreAuthorize("hasRole('ADMIN')")`)? | **Yes** — every Settings admin screen so far follows this pattern. |
| OQ-2 | Is the "5 users" cap a global constant, a per-tenant column on `BUSI_INFM` (e.g. `MAX_USER_CNT`), or a future `BUSI_PLAN_INFM` table? | **Global constant** in `UserManagementConstants.MAX_USERS_PER_TENANT = 5` for this MR. Defer the table until tier-pricing lands. |
| OQ-3 | When deleting a user, should `USER_INFM.DEL_YN='Y'` be set globally, or only `USER_BUSI_CONN_INFM.DEL_YN='Y'` for this tenant (preserving the user record for multi-tenant scenarios)? | **Per-tenant unlink only**, leaving `USER_INFM` untouched. Operational model is one-user-one-company today, but the refactor's multi-link fallback exists, and per-tenant unlink is the safer behaviour. Confirm with PO. |
| OQ-4 | Does the Menu Entitlement tree's leaf set come from a static seed (current static mock) or a backend `MENU_INFM`-style table? | **Static client-side tree** for this MR; backend stores only the `(MENU_AUTH_CD → USE_YN)` map. Dynamic menu resolution is its own feature. |
| OQ-5 | Should admin demotion be allowed if it would leave the tenant with zero admins? | **No** — block with `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN`. |
| OQ-6 | Phone country code: enum or free-form? Figma shows `+84` placeholder with a dropdown chevron. | **Enum** of the supported ISO-3166 dialling codes used by the existing Login screen; reuse that list. Defer real picker UI to a follow-up. |

---

## 11. Verification checklist (before MR)

- [ ] **No new Flyway migration files** under `src/main/resources/db/migration/` in the diff
- [ ] **No change to `spring.jpa.hibernate.ddl-auto`** in any `application*.yml` (stays `validate`)
- [ ] `./gradlew bootRun` starts cleanly against the existing local DB — Hibernate `validate` passes against the new/extended entities
- [ ] Any `@Column` additions to `UserInfm` reference columns that already exist in the DB (verified via `postgres_dev_server_jexframe` MCP)
- [ ] All 7 new error codes added to `messages.properties` + `messages_en.properties`
- [ ] All new i18n keys present in `en.json` / `ko.json` / `vi.json` (omit `vi.json` entries with no verified translation; never invent)
- [ ] `@PreAuthorize("hasRole('ADMIN')")` on every write endpoint
- [ ] Tenant scope: every repo query filters on `BIZ_KEY = TenantContext.currentBizKey()`
- [ ] `WSVC_ID='user-management'` (slug — never `admn_0021_001`) in every audit row
- [ ] Plan-cap test: 5 active users → POST returns `WBE_USERS_PLAN_LIMIT_REACHED`
- [ ] Self-delete blocked: deleting `TenantContext.currentUserId()` returns `WBE_USERS_CANNOT_DELETE_SELF`
- [ ] Last-admin guard: demoting the only `BUSI_ADMN_INFM` row returns `WBE_USERS_CANNOT_DEMOTE_LAST_ADMIN`
- [ ] Frontend: at 5/5 users, "+ Add a user" is disabled with a warning dialog
- [ ] Frontend: Zod validation fires before submit; backend errors route to system error dialog
- [ ] Frontend: BCP — all field labels and placeholders go through `t(...)`
- [ ] Backend Jackson `SNAKE_CASE` strategy applied (no per-field `@JsonProperty` other than the OAuth-style exception)
- [ ] CLAUDE.md tables updated (both repos): endpoints, error codes, migrations, feature status, Settings panels

---

## 12. Estimated effort

| Stage | Effort |
|---|---|
| Backend (repos, service, controller, DTOs, tests — no migrations, no new entities) | 1–1.5 days |
| Frontend (RHF wiring, Zod, mutation hooks, dialog plumbing) | 1 day |
| Docs (3 new files + CLAUDE.md updates) | 0.5 day |
| **Total** | **2.5–3 days** for one dev |

The frontend UI is already production-quality static — most of the work is the backend service/controller slice and the wiring layer. Menu Entitlement persistence is a separate follow-up MR.
