# Preferences — Refactor Gap Analysis (Final Parity Review)

Legacy JEX (`comm_0215_001`, `BUSI_INFM`) vs. the refactor (`preferences`). Status vocabulary:
**MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

Scope: this analysis covers the `preferences` backend module (company profile + logo). The two
preference toggles are scored under "Toggle split" but their detailed parity belongs to
[Environment Settings](../environment-settings.md).

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Profile read** | `comm_0215_001_r001` (`BUSI_INFM` half) | `GET /preferences` → `BusiInfmRepository.findById` | **MATCH** |
| **Profile save** | `comm_0215_001_u001` (UPDATE `BUSI_INFM` scoped to `BIZ_KEY`) | `PUT /preferences`, same single-row UPDATE | **MATCH** |
| **Profile fields** | name / display / tax id / rep name / email / tel / fax / 4-part address | identical column set, same required/optional split | **MATCH** |
| **Logo upload** | `comm_0215_001_c001` / `_c005` | `POST /preferences/logo` (≤2 MB, image allow-list, opaque filename) | **MATCH** (+ hardening, see Logo Parity) |
| **Logo clear** | `comm_0215_001_d001` | `DELETE /preferences/logo` (idempotent) | **MATCH** |
| **Logo serve** | static/asset path | `GET /preferences/logo` JWT-protected stream | **APPROVED DEVIATION** (Logo Parity) |
| **Use Purpose / Use VAS toggles** | `comm_0215_001_u002`/`_u003`/`_u004` (`BUSI_ENV_SET_INFM` `REC[]`) | generic Environment Settings endpoint, codes `00077` / `00074` | **APPROVED DEVIATION** (Toggle split) |
| **Authorization** | menu visibility only (no explicit check) | explicit `@menuAuthz.canAccess('settings-core-preference')` | **APPROVED DEVIATION** (Authorization Parity) |
| **Audit `WSVC_ID`** | `comm_0215_001` | slug `preferences` | **APPROVED DEVIATION** (workspace slug rule) |
| **Audit scope** | profile change audited; read + logo not | Update audited; read + logo upload/delete not | **MATCH** |
| **Secondary read view** | `comm_0215_001_r002_view.jsp` | folded into the single read endpoint | **APPROVED DEVIATION** (no behaviour loss) |
| **Tax-ID / email validation** | format-checked on save | `@NotBlank` + `@Email` + `@Size` (+ service guards) | **MATCH** |

---

## Specially-verified gaps

### Toggle split (APPROVED DEVIATION)

Legacy `comm_0215_001` returned the company profile (`BUSI_INFM`) **and** the *Use Purpose* / *Use VAS
Account* toggles (`BUSI_ENV_SET_INFM` `REC[]`) in one screen, with the toggles saved through
`_u002`/`_u003`/`_u004`. The refactor keeps the **same effective behaviour** but splits persistence:

- Company profile → the `preferences` module (this doc) — a clean JSON-only UPDATE on `BUSI_INFM`.
- Toggles → the generic [Environment Settings](../environment-settings.md) endpoint pair, one
  independent per-code upsert each (`00077` Use Purpose, `00074` Use VAS Account).

**Classification: APPROVED DEVIATION** — no behaviour loss; the toggles still work and still gate the
Purpose / VAS panels. The split prevents forking two persistence paths into one save (Environment
Settings MR V69/V70 rationale). The two switches remain visually on the Preferences panel.

### Logo Parity (MATCH + hardening / APPROVED DEVIATION on serve)

- **Upload / clear** preserve legacy behaviour: a logo filename is written to `BUSI_INFM.CMPN_LOGO`,
  upload/clear mutate only that one column, no audit. **MATCH.**
- **Hardening** added by the refactor: a 2 MB cap + image-only extension allow-list, an opaque
  unforgeable save filename (base64url SHA-256 over `bizKey + millis + 16 random bytes`), a per-tenant
  storage subtree with a path-escape guard, and write-new-before-delete-old ordering. These strengthen
  the legacy flow without changing its contract.
- **Serve** is a refactor addition: the bytes are streamed through a JWT-protected
  `GET /preferences/logo` (with a `?v=` cache-buster on `logo_url`) rather than a public asset path, so
  a tenant's logo stays behind auth. **APPROVED DEVIATION** — stronger isolation, same user-visible
  result.

### Authorization Parity (APPROVED DEVIATION)

Legacy enforced admin-only access by **menu visibility** (the Settings → Preferences entry is admin-only
via `USER_MENU_AUTH_INFM`), with no explicit check in the action JSPs. The refactor enforces the same
effective boundary explicitly with `@PreAuthorize("@menuAuthz.canAccess('settings-core-preference')")`
(F-3 server-side menu-authorization). **APPROVED DEVIATION** — same boundary, stronger enforcement.

### Status drift — "Static UI" label is stale (docs cleanup, non-blocking)

Both the workspace CLAUDE.md Feature-Status table and the `wecms-web` CLAUDE.md describe the Preferences
panel as **Static UI · read/edit mode toggle**. The shipped panel
([PreferencesPanel.tsx](../../../../../wecms-web/components/settings/preferences/PreferencesPanel.tsx))
is in fact **fully wired** to the live endpoints — read, save, logo upload/delete/stream, and the env
toggles — with **no mock fallback** in the company-profile path. Several DTO/client Javadocs also still
say "logo upload not in scope for this MR / panel renders the placeholder SVG"
([PreferencesResponse.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/dto/PreferencesResponse.java),
[PreferencesUpdateRequest.java](../../../../src/main/java/com/kosign/wecms/domain/setting/preferences/dto/PreferencesUpdateRequest.java),
[lib/types/api.ts](../../../../../wecms-web/lib/types/api.ts) `logo_url` comment) — those predate the
logo endpoints landing and are stale. **Classification: docs drift, not a behaviour gap** — the code is
authoritative (5 implemented endpoints, frontend consuming all of them). Recommend a one-line fix to the
status tables + the stale Javadocs in the next stabilisation pass.

### Test coverage (PARTIAL)

No `PreferencesServiceTest` / `PreferencesControllerTest` exists on disk
(`src/test/java/com/kosign/wecms/domain/setting/preferences/` is absent). The other Settings features
ship JUnit suites; Preferences relies on SIT/UAT scenarios ([test-scenarios.md](test-scenarios.md))
today. **Classification: PARTIAL** — the feature is implemented and frontend-wired, but automated backend
coverage is a tracked follow-up (recommend service tests for the `address` null guard, the four logo
validation error codes, the opaque-filename + previous-file-cleanup path, and the audit-on-update).

---

## Migration status

```
Refactor Status: Implemented full-stack (company profile + logo); toggles on Environment Settings.
```

Evidence:

- **All 5 endpoints implemented** (controller verified) and **frontend-wired** (the panel consumes
  read / save / logo upload / delete / stream + the two env toggles with no mock fallback).
- **Single-row UPDATE** semantics + trim/`trimToNull` + audit-on-update verified against the service.
- **Logo lifecycle** (opaque filename, per-tenant subtree, read-fallback, write-new-before-delete-old,
  WBE2080–WBE2087 error surface) verified against the service + resolver.
- **Slug `preferences` + `settings-core-preference`** used end-to-end (URI, package, audit `WSVC_ID`,
  menu grant).

Residual (non-blocking, tracked):
1. Stale "Static UI" status labels + stale logo/scope Javadocs (Status drift above) — docs cleanup.
2. No backend JUnit suite for this module (Test coverage above) — add in a follow-up.

Neither residual affects the implemented feature's parity or correctness.
