# Settings > Preferences — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/preferences/`

## Feature Overview
- **Purpose:** Per-tenant company profile — Vietnamese name, display/English name, Tax ID, representative name + email, tel/fax, the four-part representative business address, and the company logo. A single-row resource (`BUSI_INFM`, PK `BIZ_KEY`); no list, pagination, or search.
- **Business role:** A tenant administrator reads the profile, saves the editable fields in place (a pure UPDATE on the one `BUSI_INFM` row), and uploads / clears / streams the company logo.

## Menu / Route Mapping
- **Menu / panel:** Settings → Preferences (Settings modal panel — no standalone route; sidebar between Bank Account and Users)
- **Menu permission ID (slug):** `settings-core-preference` (class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-preference')")` on `PreferencesController`)
- **Wire base path:** `/api/v1/settings/preferences`
- **Frontend mount / route:** Settings modal panel key `preferences`
- **Legacy JEX counterpart:** WSVC family `comm_0215_001` (`_r001` read · `_u001` save profile · `_c001`/`_c005` logo upload · `_d001` logo clear); table `BUSI_INFM`, logo in `BUSI_INFM.CMPN_LOGO` — legacy ids cited for traceability only.

## Current Status
- ✅ **Implemented:** Full-stack. Five endpoints — GET (read profile) · PUT (save profile, single-row UPDATE) · `POST /logo` (multipart upload, ≤ 2 MB, JPG/JPEG/PNG/GIF/SVG, opaque save filename, per-tenant subtree) · `DELETE /logo` (idempotent clear) · `GET /logo` (JWT-protected binary stream with `?v=` cache-buster). Profile Update writes one `AUDIT_LOGS_INFM` row (`WSVC_ID='preferences'`); read + logo upload/delete write none (legacy parity). Frontend panel (`PreferencesPanel.tsx`) is fully wired with no mock fallback. No new entity/repository — reuses the auth domain's `BusiInfm` + `BusiInfmRepository`; no new Flyway migration.
- 🟡 **Partially implemented:** No automated backend tests on disk (no `PreferencesServiceTest` / `…ControllerTest`); SIT/UAT coverage only — a JUnit suite is a tracked follow-up.
- ⚪ **Deprecated / Legacy / Not ported:** The *Use Purpose* / *Use VAS Account* toggles rendered on this panel are NOT part of this module — they route through the generic Environment Settings endpoint pair (`GET/PUT /api/v1/settings/environment-settings/{env_set_inf}`, codes `00077` / `00074`). The legacy `comm_0215_001` returned profile + toggles in one screen; the refactor deliberately split persistence (toggle parity belongs to `environment-settings.md`). The legacy secondary read view `comm_0215_001_r002_view.jsp` is folded into the single read endpoint.

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [preferences/README.md](preferences/README.md) |
| Business flow | [preferences/business-flow.md](preferences/business-flow.md) |
| Database mapping | [preferences/database-mapping.md](preferences/database-mapping.md) |
| Functional specification | [preferences/functional-specification.md](preferences/functional-specification.md) |
| Interface specification | [preferences/interface-specification.md](preferences/interface-specification.md) |
| Migration guide | [preferences/migration-guide.md](preferences/migration-guide.md) |
| Refactor gap analysis | [preferences/refactor-gap-analysis.md](preferences/refactor-gap-analysis.md) |
| Source code index | [preferences/source-code-index.md](preferences/source-code-index.md) |
| Test scenarios | [preferences/test-scenarios.md](preferences/test-scenarios.md) |

## Historical Design Notes (preserved)
- **Status-label drift (resolved here):** earlier docs (and the workspace + `wecms-web` CLAUDE.md Feature-Status tables) called this panel "Static UI". That is **stale** — the panel is fully wired to all five live endpoints plus the two env toggles. Stale DTO/client Javadocs ("logo upload not in scope / placeholder SVG") predate the logo endpoints landing. The code is authoritative; the CLAUDE.md status labels are a tracked non-blocking docs cleanup.
- **[Not ported]** The old flat file's reference to a legacy plan naming the toggle table `ENV_SETN_INFM` was a documentation error — the real tables are `BUSI_ENV_SET_INFM` (per-tenant) + `SYS_ENV_SET_INFM` (system defaults). `ENV_SETN_INFM` appears in zero source files.
