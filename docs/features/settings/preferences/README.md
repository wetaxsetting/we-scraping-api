# Settings > Preferences — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend (controller + 1 service + 3 DTOs + 2 logo-storage support classes, no new entity/repository) and frontend wired to the live API; the **company-profile half** of legacy `comm_0215_001` is ported; the **two preference toggles** (Use Purpose / Use VAS Account) live on the generic Environment Settings endpoint pair, not here.
**Module / menu:** Settings → **Preferences** (Settings modal panel — **no standalone route**)
**Refactor slug:** `preferences` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire base path:** `/api/v1/settings/preferences` (GET profile · PUT profile · `POST /logo` · `DELETE /logo` · `GET /logo` stream)
**Menu permission slug:** `settings-core-preference`

> This folder is the **primary documentation home** for the Preferences company-profile feature on the
> backend. It consolidates and indexes material that previously lived in a single flat file; that file
> is **preserved and cross-linked** (see [§ Related & preserved docs](#related--preserved-docs)), not
> deleted. The JEX-side functional reference is the live JEX `comm_0215_001` source.

---

## What this feature is

Preferences (Settings sidebar, between **Bank Account** and **Users**) carries the per-tenant
**company profile** — Vietnamese company name, display/English name, Tax ID, representative name +
email, tel/fax, the four-part representative business address, and the company **logo**. A tenant
administrator can:

- **Read** the company profile for the current `BIZ_KEY`.
- **Save** the editable profile fields in place (a pure UPDATE on the single `BUSI_INFM` row).
- **Upload** a new company logo (multipart, ≤ 2 MB, JPG/JPEG/PNG/GIF). SVG is **rejected** (F-02 — an inline-served SVG can carry `<script>`, i.e. stored XSS).
- **Delete** (clear) the company logo.
- **Stream** the raw logo bytes back for the frontend `<img>` (JWT-protected GET).

It is a **single-row resource** — there is no list, no pagination, no search. `BIZ_KEY` is the
`BUSI_INFM` primary key and comes from the JWT (`TenantContext.currentBizKey()`), never from request
input.

### Scope boundary — what this module does NOT own

The Preferences **panel** in the UI also renders two ON/OFF switches — *Use Purpose* and *Use VAS
Account*. **Those are NOT part of this backend module.** They route through the generic
[Environment Settings](../environment-settings.md) endpoint pair
(`GET / PUT /api/v1/settings/environment-settings/{env_set_inf}`) with codes `00077` (Use Purpose) and
`00074` (Use VAS Account) against `BUSI_ENV_SET_INFM` / `SYS_ENV_SET_INFM`. The legacy `comm_0215_001`
WSVC returned profile + toggles together as one `BUSI_INFM` row + a `REC[]` of env rows; the refactor
deliberately **split** the two persistence paths so the profile save stays a clean JSON-only UPDATE on
`BUSI_INFM` and each toggle is an independent env upsert. This doc covers the `preferences` module
(company profile + logo) **only**; for the toggles see [environment-settings.md](../environment-settings.md).

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 5: GET (read profile) · PUT (save profile) · POST `/logo` (upload) · DELETE `/logo` (clear) · GET `/logo` (stream bytes) |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-core-preference')")` — class-level on the controller (F-3 menu-auth rollout). See [interface-specification.md §Authorization](interface-specification.md). |
| Tenant scope | `TenantContext.currentBizKey()` — `BIZ_KEY` is the `BUSI_INFM` PK; never from a request param |
| Table | `BUSI_INFM` (single row per tenant; in the legacy-table inventory). **No new entity** — reuses the auth domain's `BusiInfm` + `BusiInfmRepository`. |
| Logo column | `BUSI_INFM.CMPN_LOGO` (VARCHAR(500)) holds the opaque server-generated save filename |
| Logo storage | per-tenant filesystem subtree `<storage-root>/<biz_key>/preferences/<save_file_nm>` (read-fallback to a legacy flat root); cap 2 MB; extensions JPG/JPEG/PNG/GIF (SVG **rejected** — F-02 stored-XSS) |
| Audit | profile **Update** writes one `AUDIT_LOGS_INFM` row with `WSVC_ID = 'preferences'` (slug). Read + logo upload/delete write **no** audit row (legacy parity). |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |
| Persistence change | UPDATE-only on a pre-existing `BUSI_INFM` row (created at company setup); never INSERT, never physical DELETE |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.preferences
├── controller/PreferencesController.java          5 endpoints; class-level @menuAuthz gate
├── service/
│   ├── PreferencesService.java                    get/update/uploadLogo/deleteLogo/openLogo; audit on update; LogoDownload record
│   ├── PreferencesLogoResolver.java               @Component; resolve() (per-tenant write) + resolveForRead() (legacy-flat read fallback); path-escape guard
│   ├── PreferencesLogoStorageProperties.java       @ConfigurationProperties("wecms.preferences"); storage-root + SLUG_FOLDER
│   └── PreferencesConfig.java                      @EnableConfigurationProperties wiring
└── dto/
    ├── PreferencesResponse.java                    GET / PUT echo (11 wire fields incl. address + logo_url)
    ├── PreferencesUpdateRequest.java               PUT body (bean validation; @Valid nested address)
    └── PreferencesAddress.java                     nested 4-part address (post/detail/city/state)
```

**No `entity/` or `repository/` package** — the feature maps onto the existing
[`BusiInfm`](../../../../src/main/java/com/kosign/wecms/domain/auth/entity/BusiInfm.java) entity and
[`BusiInfmRepository`](../../../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiInfmRepository.java)
from the `auth` domain (the company row is already loaded at login). This is intentional: `BUSI_INFM`
is a single shared tenant table, so the refactor does not duplicate the entity per consumer.

No new Flyway migration — `BUSI_INFM` and `CMPN_LOGO` already exist (auth schema V1 / V7 parity). The
logo storage root is config-only (`wecms.preferences.storage-root`).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: read profile · save profile · upload/delete/stream logo · tenant + audit invariants |
| [functional-specification.md](functional-specification.md) | Operations matrix, field-by-field validation, logo rules (size/extension/opaque filename), audit expectations, scope boundary vs. env toggles |
| [database-mapping.md](database-mapping.md) | `BUSI_INFM` columns the feature reads/writes + the full legacy-column → entity-field → DTO-field → UI-field mapping |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint, error-code registry, authorization, wire envelope |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (`_view.jsp` / `_act.jsp` / IDO / WSVC / entity / DAO) became its Spring counterpart, incl. the split toggles + dropped flows |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios (read / edit / save / cancel / logo upload-delete-stream / authz / errors) |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) |

---

## Legacy & Migration Traceability

**Legacy JEX counterpart:** WSVC family `comm_0215_001` (`_r001` read · `_u001` save profile · `_c001`/`_c005` logo upload · `_d001` logo clear; the `_u002`/`_u003`/`_u004` env-toggle actions are out of this module). Table `BUSI_INFM` (single tenant row) — logo stored in `BUSI_INFM.CMPN_LOGO`.

---

## Related & preserved docs

- **Preserved flat backend spec:** [../preferences.md](../preferences.md) — kept as the original
  confirmed-against-source note (also documents the env-toggle side).
- **Environment Settings (the two toggles' real home):** [../environment-settings.md](../environment-settings.md)
- **Frontend feature folder:** [wecms-web/docs/features/settings/preferences/](../../../../../wecms-web/docs/features/settings/preferences/README.md)
- **Legacy-table baseline:** [references/database/legacy-table-inventory.md](../../../references/database/legacy-table-inventory.md) (`busi_infm`)

---

## Open items / uncertainties

- **Status-label drift — "Static UI" is stale.** Both the workspace CLAUDE.md Feature-Status table and
  the `wecms-web` CLAUDE.md describe the Preferences panel as **Static UI**. The shipped panel
  ([PreferencesPanel.tsx](../../../../../wecms-web/components/settings/preferences/PreferencesPanel.tsx))
  is in fact **fully wired** to the live endpoints (read, save, logo upload/delete/stream, env toggles)
  with a mock-free path. The DTO/client Javadocs that say "logo upload not in scope / panel renders a
  placeholder SVG" are likewise stale (logo endpoints are implemented and consumed). Documented here as
  a non-blocking docs-cleanup; the code is authoritative. See
  [refactor-gap-analysis.md](refactor-gap-analysis.md) (Status drift).
- **No automated tests on disk for this module** (no `PreferencesServiceTest` / `…ControllerTest` found
  in `src/test/`). [test-scenarios.md](test-scenarios.md) lists SIT/UAT coverage; a JUnit suite is a
  recommended follow-up.
