# Purpose — Refactor Gap Analysis (Final Parity Review)

Legacy JEX (`comm_0187` family, `BUSI_USAG_SET_INFM`) vs. the refactor (`purpose-settings`). Status
vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **Search / list** | `comm_0187_001_r001` + `_r002` (name search) | `GET /purpose-settings?keyword=` merges both | **MATCH** |
| **Create** | `comm_0187_001_c001` (dup code → `WBW1152`, dup name → `WBW1138`) | `POST /purpose-settings`, same dup codes, PK via `wb_get_next_val` | **MATCH** |
| **Update** | `comm_0187_001_u002` REC (name dup excl. self; no hand-code dup) | `PUT /purpose-settings/{id}`, identical rules (OQ-4) | **MATCH** |
| **Delete (soft)** | `comm_0187_001_u002` REC2 → `USE_YN='N'`, `WBE1199` if has children | `DELETE /purpose-settings/{id}` → `USE_YN='N'`, `WBE1199` | **MATCH** (+ broadened, see Soft-Delete Parity) |
| **Parent validation** | parent exists + active + level-1 | `validateParent` (exists, active, level-1, not self) | **MATCH** |
| **Child validation** | block delete when active children | `hasActiveChildren` → `WBE1199` | **MATCH** |
| **Hierarchy management** | two-level (parent/child), reorder via `_u004` | two-level enforced; `PUT /order` bulk reorder | **MATCH** |
| **Import** | `comm_0187_004_c001` one-shot upload | two-step `excel/preview` → `excel/import` | **APPROVED DEVIATION** (GAP-3) |
| **Export** | none (legacy has no Purpose export) | none (only a static template download) | **MATCH** (nothing to port) |
| **Authorization** | menu visibility only (no explicit check) | explicit `@menuAuthz.canAccess('settings-core-purpose')` | **APPROVED DEVIATION** (Authorization Parity) |
| **Audit `WSVC_ID`** | `comm_0187_001` | slug `purpose-settings` | **APPROVED DEVIATION** (workspace slug rule) |
| **Lang-detail table** | `BUSI_USAG_SET_INFM_LANG_DETL` | dropped (single-table model) | **APPROVED DEVIATION** |
| **Reset to system defaults** | `comm_0187_001_u003` | not ported | **DEAD LEGACY** (GAP-1) |
| **Deleted/unused tab + Restore** | `comm_0187_003_r001` / `_u001` REC | not ported | **DEAD LEGACY** (GAP-2) |
| **Permanent (physical) delete** | `comm_0187_003_u001` REC2 (`deleteByPk`) | not ported (refactor never physically deletes) | **DEAD LEGACY** (GAP-1) |
| **ERP account-code mapping** | `comm_0187_002_*` (`BUSI_USAG_ACCT_INFM_DETL`) | separate feature, not in Purpose Settings | **APPROVED DEVIATION** (scope boundary) |

---

## Specially-verified gaps

### GAP-1 — Dead legacy flow (reset-to-defaults + permanent delete)

- **Reset to system defaults** (`comm_0187_001_u003`) drives the `SYS_USAG_SET_INFM` system-default
  set and the per-group `USAG_GRP_CD` model. The refactor scopes exclusively to
  `usag_grp_cd IS NULL`, so this flow has no target. **Classification: DEAD LEGACY** — intentionally excluded.
- **Permanent delete** (`comm_0187_003_u001` REC2) physically deletes a soft-deleted row once it is
  no longer referenced. The refactor never physically deletes a purpose; soft-deleted rows persist
  with `USE_YN='N'`. **Classification: DEAD LEGACY** — intentionally excluded.

### GAP-2 — Deleted Purpose tab + Restore

The legacy "Deleted Purposes" tab (`comm_0187_003` view) listed `USE_YN='N'` rows and offered a
**Restore** action (`comm_0187_003_u001` REC → `USE_YN='Y'`). The refactor design (Figma reference
set) has **no** trash-bin tab and no restore affordance.

- **Reachable in legacy?** Yes — it was a working tab in the live JEX UI.
- **Refactor excludes it intentionally?** Yes — not in the approved Figma frames; soft-deleted rows
  remain in the DB (queryable) but have no UI surface and no API endpoint.
- **Classification: DEAD LEGACY** for the refactor scope. If a future PO decision wants a
  restore/trash view, it would be a new feature (the soft-delete data already supports it).

### GAP-3 — Excel template parity

The refactor replaced the legacy one-shot upload with a **two-step preview → import** flow (mirroring
the VAS Account precedent), using the same four columns (Purpose Code, Purpose Name, Description,
Parent Purpose) and a 500-row cap. Per-cell validation (`required`/`length`/`dup_file`/`dup_db`/
`parent_not_found`/`parent_not_root`) plus file-level codes (`WBE2041`/`WBE2042`/`WBE2043`) give
stronger pre-commit feedback than the legacy one-shot insert. Pure-append (no replace/reset mode —
the legacy table has no wipe path). **Classification: APPROVED DEVIATION** — functionally a superset
of legacy import; column set + parent semantics preserved.

### Soft-Delete Parity

Soft-delete is **preserved verbatim** (`USE_YN='Y' → 'N'`, never physical) and the legacy
`hasActiveChildren` → `WBE1199` guard is preserved. The refactor **broadens** `WBE1199` to also block
deletion when the row OR any active child is referenced by an active transaction (OQ-5 recursive
guard, via the `VPurposeInUse` `@Subselect`). Same wire code, one localized message covering both. **Status: MATCH**
(legacy behaviour fully preserved) **+ documented strengthening.** The `@Subselect`'s transaction-consumer
UNION branch (b) is wired for the parent-child case today; real `PYMN_*`/`TRNS_*`/`EXPS_*` branches
are added as those entities port — tracked as the OQ-5 follow-up (does not block parity for the
implemented scope).

### Authorization Parity

Legacy enforced admin-only access by **menu visibility** (the Settings → Purpose entry is admin-only
via `USER_MENU_AUTH_INFM`), with no explicit check in the action JSPs. The refactor enforces the same
effective boundary explicitly with `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")`
(F-3 server-side menu-authorization, 2026-06-06). **Status: APPROVED DEVIATION** — same effective
boundary, stronger enforcement.

> **Non-blocking cleanup:** the controller's class Javadoc still states "Per OQ-6 the controller
> carries no `@PreAuthorize`," which contradicts the annotation actually present on the next line.
> The annotation is authoritative; the comment is stale (predates F-3). Recommend a one-line Javadoc
> fix in the next stabilisation pass. Does not affect behaviour or parity.

---

## Migration status

```
Refactor Status: 100% Complete (for the in-scope feature surface)
```

Evidence for closed gaps:

- **All 8 endpoints implemented** (controller verified) with unit + controller tests green — see [test-scenarios.md §2](test-scenarios.md).
- **Soft-delete + recursive guard** verified by 4 service tests.
- **Parent/child/hierarchy rules** verified by create/update tests.
- **Excel preview/import** verified by 14 service tests incl. all per-cell + file-level codes.
- **Slug `purpose-settings` + `settings-core-purpose`** used end-to-end (URI, package, audit `WSVC_ID`, menu grant).
- **DEAD LEGACY flows** (GAP-1, GAP-2) confirmed unreachable-by-design in the refactor and intentionally excluded.

Residual (non-blocking, tracked):
1. OQ-5 follow-up — add real transaction-consumer UNION branches to the `VPurposeInUse` `@Subselect` as `PYMN_*`/`TRNS_*`/`EXPS_*` entities port.
2. Stale controller Javadoc (Authorization Parity note above).
3. OQ-7 — optional partial unique index on `(hand_use_usag_cd)` / `(use_usag_nm)` (deferred; service-layer guard is authoritative today).

None of the residual items affect the implemented feature's parity or correctness.
