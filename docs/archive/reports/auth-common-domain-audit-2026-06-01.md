# Feature Documentation Audit — Auth & Common Domains

**Date:** 2026-06-01 · **Owner:** Sonny / Arthur · **System:** Refactor (Spring Boot + Next.js)
**Status:** ✅ Executed — `auth/` + `common/` domains added on both `wecms-api/docs/features/` and `wecms-web/docs/features/`.
**Resolves:** OQ-DOC-1 (Login Authentication doc) + OQ-DOC-2 (View Setting doc) — see [`../../planning/open-questions/documentation-follow-up.md`](../../planning/open-questions/documentation-follow-up.md).

---

## Goal

Introduce two missing **system / cross-cutting** feature domains under `features/` on both refactor
doc trees, without reorganizing the existing Banking / Payment / Add-on Service / Settings folders.

```
features/
├── auth/          ← NEW (OQ-DOC-1)
├── common/        ← NEW (OQ-DOC-2)
├── banking/       ← unchanged
├── payment/       ← unchanged
├── addon-service/ ← unchanged
└── settings/      ← unchanged
```

## Key finding

The audit mapped exactly onto two already-open tracker items — OQ-DOC-1 and OQ-DOC-2 — which had
reserved the slugs `login-auth` and `view-setting`. **No existing feature doc needed to be moved**:
all prior Auth/Common material lived as shared docs, architecture scaffolds, planning docs, or JEX
read-only reference. The work was **create + cross-link**, not relocate.

## AUTH

- **Existing docs found:** sprint-0 login plan ([`../../archive/planning/phase-1/task-sprint-0-login-refactor.md`](../../archive/planning/phase-1/task-sprint-0-login-refactor.md)), security design scaffold ([`../../references/business-rules/security/README.md`](../../references/business-rules/security/README.md)), shared permission catalogue ([`../../../../docs/_shared/permission-roles.md`](../../../../docs/_shared/permission-roles.md)), JEX login refs (read-only). No refactor-side feature doc existed.
- **Created:** [`../../features/auth/README.md`](../../features/auth/README.md), [`../../features/auth/login-auth.md`](../../features/auth/login-auth.md); frontend [`../../../../wecms-web/docs/features/auth/README.md`](../../../../wecms-web/docs/features/auth/README.md), [`../../../../wecms-web/docs/features/auth/login-auth.md`](../../../../wecms-web/docs/features/auth/login-auth.md).
- **Explicitly NOT auth (kept in Settings):** `login-credentials.md` (bank/GDT scraping creds), `users.md` (user admin), `change-password.md` (ProfileMenu).

## COMMON

- **Existing docs found:** View Setting web architecture ([`../../../../wecms-web/docs/architecture/view-setting/README.md`](../../../../wecms-web/docs/architecture/view-setting/README.md)), JEX View Setting spec + common API patterns (read-only), audit-log design inside the security scaffold. No refactor-side feature doc existed.
- **Created:** [`../../features/common/README.md`](../../features/common/README.md), [`../../features/common/view-setting.md`](../../features/common/view-setting.md); frontend [`../../../../wecms-web/docs/features/common/README.md`](../../../../wecms-web/docs/features/common/README.md), [`../../../../wecms-web/docs/features/common/view-setting.md`](../../../../wecms-web/docs/features/common/view-setting.md).
- **Not yet in scope (no stub created):** Notifications, File Upload / Attachment, Dashboard, Common Codes, Tenant Context (documented inline in auth/security), Notifications. "Screen Options" = the REST surface name of View Setting (same feature).

## Per-document disposition

| Document | Path | Action |
|---|---|---|
| Login refactor plan | archive/planning/phase-1/task-sprint-0-login-refactor.md | Keep |
| Backend security design | references/business-rules/security/README.md | Keep + cross-link |
| Permissions (api/web) | docs/permissions.md | Cross-link (shared — not absorbed) |
| login-credentials / users / change-password | features/settings/ | Keep (Settings, not Auth) |
| View Setting architecture | wecms-web/docs/architecture/view-setting/README.md | Keep + cross-link |
| JEX login / auth / common / view-setting | docs/jex/… | Cross-link (read-only) |
| `auth/README.md` + `auth/login-auth.md` (×2 trees) | features/auth/ | **Created** |
| `common/README.md` + `common/view-setting.md` (×2 trees) | features/common/ | **Created** |

## Reference updates made

- **OQ tracker:** OQ-DOC-1 & OQ-DOC-2 marked **RESOLVED 2026-06-01** in [`../../planning/open-questions/documentation-follow-up.md`](../../planning/open-questions/documentation-follow-up.md) and [`../../planning/open-questions/README.md`](../../planning/open-questions/README.md). Historical records preserved.
- **Service mapping:** the `login-auth` / `view-setting` rows in [`../../references/migration/service-mapping.md`](../../references/migration/service-mapping.md) and [`../../references/api-spec/api-mapping.md`](../../references/api-spec/api-mapping.md) now link the new feature docs (were plain-text *Needs Confirmation*).
- **Feature indexes:** `auth/` + `common/` rows added to [`../../features/README.md`](../../features/README.md) and [`../../../../wecms-web/docs/features/README.md`](../../../../wecms-web/docs/features/README.md), labelled *(system)* — not WeCMS top-menu groups.

## Not touched (per scope)

Banking, Payment, Add-on Service, Settings folders — unchanged. No additional restructuring. All six
OQ-DOC references preserved (OQ-DOC-3–6 remain open).
