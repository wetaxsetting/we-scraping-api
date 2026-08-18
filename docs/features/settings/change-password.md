# Settings > Change Password — Feature Summary (Refactor)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> ⚠️ This document is a high-level feature summary.
> Change Password has **no module folder** — it is a small, self-contained slice of the
> **`auth` domain**, not a Settings-menu screen. The authoritative source of truth is the code:
> backend `domain/auth/` (`ChangePasswordService` + the `POST /api/v1/auth/change-password`
> endpoint on `AuthController`) and frontend `components/layout/ChangePasswordDialog.tsx`
> wired into `ProfileMenu`. Related password-write context lives under
> [user-management/](user-management/README.md) (Settings → Users create/reset) and
> [login-credentials.md](login-credentials.md).

## Feature Overview

- **Purpose:** Let the logged-in user change their own WeCMS sign-in password from the header
  profile dropdown.
- **Business role:** Self-service credential maintenance. It is **not** a Settings-menu item —
  the entry point is the avatar **profile dropdown** (a "Change Password" row), reached from any
  page, the same as Logout.

## Menu / Route Mapping

- **Menu / panel:** Header → profile dropdown → "Change Password" row (opens a feature dialog).
  Not in the Settings modal, not in the sidebar.
- **Menu permission ID (slug):** none — no `@menuAuthz` / View Setting `WEB_SVC_ID`. The endpoint
  is gated only by the global `anyRequest().authenticated()` rule (JWT required); it is **not** in
  the `SecurityConfig` public list. The user id is read from `TenantContext` only — never accepted
  from the body (the credential being changed is always the caller's own).
- **Wire base path:** `POST /api/v1/auth/change-password` (verified in
  `domain/auth/controller/AuthController.java`).
- **Frontend mount / route:** no route — a shadcn feature dialog
  (`components/layout/ChangePasswordDialog.tsx`) opened from `ProfileMenu.tsx`; API client
  `changePassword(...)` in `lib/auth/client.ts`; wire type `ChangePasswordRequestRec` in
  `lib/types/api.ts`.
- **Legacy JEX counterpart:** popup `comm_0401_001` + action `comm_0400_001_u001` (traceability
  only). The refactor uses the `change-password` slug and the `auth` REST base, not the legacy
  WSVC ids.

## Current Status

⚪ **Not a standalone Settings module** — but the functionality itself is fully built.

- ✅ **Implemented (backend, `auth` domain):**
  - `POST /api/v1/auth/change-password` — JWT-protected; `ChangePasswordRequest`
    (`current_password` / `new_password` / `confirm_password`); `ChangePasswordService`
    (`@Transactional`).
  - Validation order mirrors legacy: required fields (`WBE_VALIDATION`) → new == confirm
    (`WBE_CHANGE_PASSWORD_MISMATCH`, legacy WBE1052) → current matches stored hash (`WBE2091`
    "Password confirmation failed.", legacy WBE1053) → not one of the last 3 history rows
    (`WBE_CHANGE_PASSWORD_REUSED`, legacy WBE1017).
  - On success: `USER_INFM.USER_PW` rewritten **via the shared `WecmsPasswordEncoder`**, plus one
    `USER_PW_CHG_HIS` row appended.
  - Entity / repo: `UserPwChgHis` + `UserPwChgHisId` + `UserPwChgHisRepository`; table
    `USER_PW_CHG_HIS` created by **Flyway V132** (PROD-active-list table).
  - `REG_SEQ_NO` is allocated through the single `wb_get_next_val` funnel
    (`LegacyFunctionRepository.nextUserPwChgRegSeqNo`, branch #27 — per-user MAX+1), not a private
    Java generator.
- ✅ **Implemented (frontend):**
  - `ChangePasswordDialog.tsx` — shadcn feature dialog, zod schema (required + new/confirm
    `refine`), translated chrome, success/error surfaced through the shared `useDialog()` system
    dialog. Double-submit guarded; fields cleared and dialog closed on success.
  - Wired into `ProfileMenu` — the "Change Password" row's `onClick` opens the dialog
    (`setPwOpen(true)`). i18n keys `change_password.*` in `locales/{en,ko,vi}.json`.
- ⚪ **Out of scope (deferred per `wecms-api/CLAUDE.md`):** password-expiry / forced-change
  (`USER_INFM.PW_CHG_REQ_SEQ_NO`), 5-failure auto-lock. Neither is wired here.

> **Doc note (2026-06-23):** two stale claims removed from the previous revision of this file —
> the password is written as the legacy **SHA-256** `public.hash()` value (coexistence rule), NOT
> BCrypt; and `REG_SEQ_NO` is allocated by the **`wb_get_next_val` funnel**, NOT a Java-side
> MAX+1 generator. The `USER_PW_CHG_HIS` table is **V132**, not V207. The previous "frontend
> handler is empty" status (still echoed in `wecms-web/CLAUDE.md`'s Feature Status table) is also
> stale — the dialog is fully wired.

## How it relates to other modules

Change Password shares the **single coexistence password-write path** with the other two flows
that write `USER_INFM.USER_PW`:

| Flow | Where | Encoder |
|---|---|---|
| Self-service change | `ChangePasswordService` (this doc) | `WecmsPasswordEncoder.encode()` |
| Settings → Users create / reset | `UserManagementService` (`user-management/`) | `WecmsPasswordEncoder.encode()` |
| Login | `AuthService.login` | verify-only — **no write-back** |

`WecmsPasswordEncoder.encode()` always emits the legacy `public.hash()` SHA-256 base64 value (via
`LegacyHashPasswordEncoder`) so the live JEX system can authenticate the same row — never BCrypt.
This is why a password set here is also valid for legacy JEX login, and why the reuse-history
check compares cleanly against legacy-imported `USER_PW_CHG_HIS` rows.

## Detailed Documentation

There is no `change-password/` folder. Authoritative references that **do** exist:

- Settings → Users password write path — [user-management/](user-management/README.md).
- Bank/GDT credential reveal + the shared `WBE2091` "verify the logged-in user's password"
  semantic — [login-credentials.md](login-credentials.md).
- Coexistence / SHA-256 password policy — `wecms-api/CLAUDE.md` §"Passwords
  (`WecmsPasswordEncoder` + `LegacyHashPasswordEncoder`)" and memory
  `project_login_password_coexistence_sha256.md`.
- Code: `domain/auth/service/ChangePasswordService.java`,
  `domain/auth/controller/AuthController.java`,
  `common/security/WecmsPasswordEncoder.java`, Flyway `V132__user_pw_chg_his.sql`.
