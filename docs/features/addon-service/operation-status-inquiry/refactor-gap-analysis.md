# Operation Status Inquiry — Refactor Gap Analysis (Backend)

Snapshot date: 2026-06-04. Status: ✅ **feature complete** for the refactor R&D scope. This doc records coverage vs JEX, deliberate divergences, and the small residual items.

---

## 1. Coverage vs legacy JEX

| Legacy capability | Refactor status |
|---|---|
| List read (`aosv_0001_001_r001`) | ✅ Implemented — `GET /operation-status` with tenant scope, `q` + `companyStatus` filters, server-side pagination + sort whitelist |
| Single inquire / Get Business Info (`aosv_0001_002_r001`) | ✅ Implemented — live OSMC → GDT bridge, audit rows written |
| Save persisted row (`aosv_0001_002_c001`) | ✅ Implemented — `#create`, duplicate guard, audit stamp |
| Bulk update / re-poll (`aosv_0001_001_u001`) | ✅ Implemented — `#bulkUpdate`, all-or-selection, per-row counts |
| Excel export | ✅ Implemented — refactor addition via shared pipeline |
| Status localisation (`wb_get_operat_stts`) | ⛔ Not ported (intentional — see §3) |
| Per-row delete (`aosv_0001_002_d001`) | ⛔ Not ported (page exposes no delete) |
| Per-row audit-history surfacing | ⛔ Not exposed (matches JEX — audit tables written, not shown) |

> **Status correction:** earlier scattered docs described Inquire/Update/Export as deferred and Save as a stub. That is **stale** — all four (plus export) are implemented and frontend-wired (verified 2026-06-02). This folder reflects the completed state.

---

## 2. Residual items / TODOs

| Item | Severity | Notes |
|---|---|---|
| Export subheader i18n | low | `OperationStatusExportProvider` composes `"<n> compan(y\|ies) · As of <date>"` in plain English; Sprint 2 may route the labels through `MessageResolver`. |
| Stale Javadoc on `InquireOperationStatusResponse` | trivial | Class Javadoc says Save is "a separate endpoint in a later MR" — that endpoint now exists (`#create`). Code comment only; no behavioural impact. Fix opportunistically. |
| Automated test coverage | medium | Only the `#create` path has unit tests. `list` / `inquire` / `bulkUpdate` / export lack tests — see [test-scenarios.md §2](test-scenarios.md#2-coverage-gaps-recommended-additions). |
| GDT gateway default in local | medium (ops) | `wecms.osmc.*` defaults to the live Coocon gateway; a dev/test run **must** override it. Documented in the service Javadoc; consider a fail-safe default for non-PROD profiles. |
| KM (Khmer) i18n | low | Frontend `operation.*` keys cover EN/KO/VI only; backend error messages similarly. Add KM when the locale ships product-wide. |

None of these block the feature being considered complete for refactor R&D.

---

## 3. Deliberate divergences from JEX

| # | Divergence | Rationale |
|---|---|---|
| D1 | 3-state `status_cd` derived on read, added to the wire | Compact display key for badge styling; raw `company_status` stays authoritative; avoids a wire migration when display states tighten. |
| D2 | Raw Vietnamese status in export (not the 3-state code) | Preserves the full GDT response in the spreadsheet — collapsing to 3 states would lose detail. |
| D3 | Legacy WSVC ids retained only in audit sentinels (`BI09`, `aosv_0001_001_r001`, NXISAS class/module/job) | Keeps refactor-written audit rows aligned with live JEX data on a shared DB; everywhere else uses the slug (`addon-operation-status`). |
| D4 | Localisation helper not ported | `wb_get_operat_stts` is commented out in legacy and absent from local Flyway; frontend tints via regex; refactor adds `status_cd`. |
| D5 | No delete endpoint | The refactor list page exposes no per-row delete (matches the live screen's toolbar). |

---

## 4. Open questions

None outstanding for the backend. If a future feature needs cross-system audit-history reconciliation that requires reviving the legacy-WSVC-id-in-audit pattern beyond the pinned sentinels, that is a fresh PO decision per the workspace identifier-naming rule.
