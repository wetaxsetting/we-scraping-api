# WeInvoice "Add Tax Code" Flow — Audit

**Date:** 2026-11-06
**Type:** AUDIT / INVESTIGATION
**Current Work Classification:** AUDIT
**Finding Classification:** NO DEFECT in the add flow itself; ISSUE — the engine half of the flow does not exist yet
**Investigation Status:** COMPLETE
**Approval Status:** APPROVED (developer, 2026-11-06 — synchronous login test via the engine's HTTP API)
**Implementation Status:** see [issue record](../../issues/weinvoice-gdt-login-test-synchronous-2026-11-06.md)
**Repository:** wecms-api (checked out as `we-scraping-api`)

## Request

Developer (2026-11-06): check what happens when a tax code (MST) is added for a user, and confirm the
flow goes through WeInvoice's own functions only — nothing through VAN, Coocon or WeCMS.

## Scope

`POST /api/v1/weinvoice/companies` and the neighbouring tax-code operations (`PUT /{mst}/password`,
`PATCH /{mst}`, `DELETE /{mst}`): controller → service → repositories → database, plus what is queued
for the engine. Authentication of the request is in scope only as far as it decides the owner.

## Investigation Plan

1. Trace the call chain in source.
2. List every class and table each step touches; flag any WeCMS, VAN or Coocon dependency.
3. Record the observed behaviour (statuses, validation, quota, engine request).

## Findings

Source traced 2026-11-06 (static; the local backend was not running because
`WECMS_WEINVOICE_DB_PASSWORD` is blank, so no live call was made).

### Call chain of `POST /api/v1/weinvoice/companies`

| Step | Code | Touches |
|---|---|---|
| 1 | `WeInvoiceCompanyController.create` | — (no `@PreAuthorize`, no `@menuAuthz`; on the authorization allow-list) |
| 2 | `CompanyAccessGuard.ownerId()` | `TenantContext.currentUserId()` — the JWT user id |
| 3 | `WeInvoiceIds.requireTaxId` | format check → `WBE3001` |
| 4 | `CompanyConnectionRepository.find(owner, mst)` | `weinvoice.winv_cmpn_infm` → already active: `WBE3003` |
| 5 | `requireQuota` → `activeMsts(owner)` | `weinvoice.winv_cmpn_infm` → 5 already: `WBE3004` |
| 6 | `WeInvoiceSecretCipher.encrypt(password)` | WeInvoice-only AES-256-GCM key `WECMS_WEINVOICE_SECRET_KEY` |
| 7 | `db.tx()` — one transaction on the WeInvoice pool (`WeInvoiceDb`, engine PostgreSQL) | `INSERT … ON CONFLICT` `weinvoice.winv_cmpn_infm` (`conn_stts=PENDING`) + `INSERT` `weinvoice.winv_engn_rqst` (`LOGIN_TEST`, `MANUAL`) |
| 8 | response `RequestAccepted(request_id, mst)` | — |

`PUT /{mst}/password` is the same minus steps 4–5 (`UPDATE` + new `LOGIN_TEST`); `PATCH /{mst}` updates
label / default flag; `DELETE /{mst}` soft-deletes (`del_yn='Y'`, password erased). All four write only
`weinvoice.winv_cmpn_infm` / `weinvoice.winv_engn_rqst`. The list (`GET /companies`) also reads the engine's
`public.companies`, `public.partners`, `public.job_run_companies` (read-only, for name and last sync).

### VAN / Coocon / WeCMS

- **VAN, EFS, Coocon, NXiSAS:** not referenced anywhere in `domain/weinvoice` (grep of the whole module).
- **WeCMS database / tables** (`BUSI_INFM`, `GDT_CERT_INFO`, `USER_BUSI_CONN_INFM`, JPA repositories, the
  WeCMS `DataSource`): not used. WeInvoice has its own Hikari pool and never touches the WeCMS schema.
  The only mention of `GDT_CERT_INFO` is a comment explaining why the WeCMS cipher is NOT reused.
- **Shared WeCMS code used:** `BusinessException`, `ApiResponse`, `MessageResolver` (error texts),
  `TenantContext`. These are in-process helpers, no WeCMS data.
- **One real WeCMS dependency — identity.** The owner comes from the JWT issued by WeCMS
  `POST /api/v1/auth/login`, which checks the user against the WeCMS database (`USER_INFM`,
  `USER_BUSI_CONN_INFM`). WeInvoice has no user store or login of its own, so a WeInvoice user must
  exist in WeCMS. The add-MST function itself does not call WeCMS.

### What does NOT happen today (the gap)

- The `LOGIN_TEST` request is only queued. **No engine worker reads `winv_engn_rqst` yet**, so the row
  stays `PENDING` and the request `QUEUED`; the GDT password is never tested.
- The engine collects invoices only for MSTs in its own `public.companies`, with passwords from its
  `.env`. Adding an MST in WeInvoice does **not** register it there, so **no invoice is ever fetched for a
  newly added MST** — the user only sees invoices the engine already had for that MST.

## Recommendation

The add flow is WeInvoice-only as requested; no code change is needed for that. To make "add MST" actually
start data collection, the missing piece is on the engine side (we-scraping repo, outside this repository):
a worker that consumes `winv_engn_rqst` — `LOGIN_TEST` (decrypt with the shared key, log in to GDT, write
`CONNECTED` / `ERROR`) and, on success, register the MST for syncing (or read `winv_cmpn_infm` directly
instead of `public.companies` + `.env`). Contract: module `README.md` §"Engine contract".

Decision for the developer: whether WeInvoice may keep using the WeCMS login for identity (current
design), or needs its own login — the latter is a separate, larger change.

**Approval Status:** APPROVED 2026-11-06 — implemented as a synchronous engine call instead of the queue worker, see the [issue record](../../issues/weinvoice-gdt-login-test-synchronous-2026-11-06.md).
