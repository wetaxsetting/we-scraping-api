# Setting › Purpose — Refactor Parity Audit (vs. Legacy JEX)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> **System:** Refactor (`wecms-api` + `wecms-web`) audited against JEX `comm_0187_001` family.
> **Audited:** 2026-06-07 · source-based (read actual code both sides, not docs).
> **Slug:** `purpose` / `purpose-settings` · **Legacy:** `comm_0187_001` (+ `_003`, `_004`, `comm_0180_001_c002`).

**Output-path note (per task):** the task suggested `docs/audits/purpose-refactor-parity-audit.md`. Per `wecms-api/CLAUDE.md` → Documentation Structure Rules, *"DB / parity audits (refactor vs JEX)"* live under `wecms-api/docs/audits/`. This audit spans both refactor trees but is filed there (the designated audits home) and cross-references `wecms-web`. JEX source stays read-only reference.

Legacy reference dossier: [docs/jex/features/purpose/](../../../../docs/jex/features/purpose/).

---

## Final Verdict

## ✅ GO FOR SIT  _(upgraded from 🟡 CONDITIONAL GO — final closure 2026-06-07)_

The **backend is strong parity** — list/create/update/soft-delete/excel all match JEX behavior, with unit + controller + excel tests (3 classes), correct tenant scoping, reserved-code exclusion, sequence keys, audit logging, and the soft-delete child-block rule (`WBE1199`). The **frontend is functionally wired** (reachable via the Settings modal, full CRUD + toggle + excel + hierarchy + i18n).

**Closure rationale — every prior blocker is resolved:**
- **GAP-3 (the only HIGH) — CLOSED.** Excel template `.xlsx.xlsx` naming + KO/VI 404 fixed; 3 single-extension localized templates ship; verified.
- **GAP-1 / GAP-2 — confirmed DEAD-LEGACY non-gaps (re-verified at source 2026-06-07).** The Deleted/Restore/Hard-delete flow and the drag-reorder flow are **not reachable by users in the live legacy page** — `comm_0187_003_view.jsp` is an empty shell, and the current `comm_0187_001_view.jsp` no longer contains the `#btn_edit`, `.deleted_sup_pur`, `#tbody_deleted`, or `#btn_restore` trigger elements (only leftover CSS at `:106`). The refactor correctly matches the live legacy page; nothing reachable is missing.
- **GAP-6 — server parity confirmed.** Legacy and refactor **both block** parent-with-children delete via `WBE1199`; the cascade-worded confirm is identical in both. Wording-only quality fix, not a parity break.

No HIGH-severity gap, no SIT blocker, and no user-reachable missing legacy flow remains. Residual items are documentation (GAP-4/7/8), recommended-after-SIT FE tests (GAP-5), a wording tweak (GAP-6), and low-risk hardening (GAP-9/10) — none gate SIT. See **"Final SIT Readiness"** at the end of this document.

---

## Summary Table (by audit area)

| # | Area | Status | Notes |
|---|---|---|---|
| 1 | Menu / Route / Authorization | ✅ Parity (better) | Modal-gated via `@menuAuthz.canAccess('settings-core-purpose')` + nav slug; tenant via JWT `BIZ_KEY`. No URL route (modal-only, by design). |
| 2 | List / Search / Hierarchy | ✅ Parity | `USAG_GRP_CD IS NULL` + `USE_YN='Y'`, reserved codes excluded, 2-level tree, `disp_ord` order, keyword `ilike`. Dormant legacy group-tabs correctly **not** ported. |
| 3 | Create | ✅ Parity (stronger) | dup hand→`WBW1152`, dup name→`WBW1138`, seq key + `disp_ord` MAX+1, defaults `Y/Y`, `usag_grp_cd` NULL, audit. Adds server-side 2-level enforcement (legacy was client-only). |
| 4 | Update | ✅ Parity | name-dup excl-self→`WBW1138`, no hand-cd re-check (legacy parity, OQ-4), parent + no-demote validation, `disp_yn` patch. |
| 5 | Delete / Soft delete | ✅ Parity (+ faithful contradiction) | Soft `USE_YN='N'`; blocks parent-with-active-children→`WBE1199`. **Reproduces the legacy cascade-confirm-vs-server-block contradiction** (see GAP-6). |
| 6 | Deleted / Restore / Hard delete | ✅ Parity (matches live legacy) | `comm_0187_003` is **dead/unreachable in the live legacy page** (empty view shell + trigger UI removed from `comm_0187_001_view.jsp`). Refactor correctly omits it — not a reachable legacy flow (GAP-1, re-verified). |
| 7 | Excel Upload / Download | ✅ Parity | Preview/import parity good; KO/VI template 404 **fixed** (GAP-3 closed — EN fallback). |
| 8 | Database mapping | ✅ Parity | `BUSI_USAG_SET_INFM` column-for-column (V80); PK `(biz_key, use_usag_cd)`; `wb_get_next_val` branch (V82); in-use view (V81, branch-a only). |
| 9 | API parity | ✅ Parity | All CRUD + reorder + excel present; absent restore/hard-delete/reset endpoints map to dead-legacy flows (non-gaps); active-toggle folded into `PUT` (acceptable). |
| 10 | UI parity | ✅ Parity (matches live legacy) | List/hierarchy/expand/add/edit/delete/toggle/excel/empty all present; reorder drag UI is absent on both sides — dead/unreachable in the live legacy page too (GAP-2, re-verified). |
| 11 | Messages / i18n | ✅ Parity (better) | Full EN/KO/VI; `WBW1152/1138/1199` mapped; misleading "restore" text correctly re-sourced. 2 trivial hardcoded strings (GAP-9). |
| 12 | Tests | 🟡 Backend only (not a blocker) | 3 backend test classes (45 tests). **Zero frontend tests** (GAP-5 — recommended after SIT). |

---

## Gap Table

| Gap ID | Severity | Legacy Behavior | Refactor Behavior | Required Action |
|---|---|---|---|---|
| **GAP-1** | ✅ **NON-GAP** (dead legacy; was MEDIUM) | `comm_0187_003` Deleted/Restore/Hard-remove — **dead/unreachable in the live legacy page** (empty view shell; trigger UI removed from `comm_0187_001_view.jsp`). See "Final Legacy Usage Re-check". | No deleted-list/restore/hard-delete endpoint or UI — **correctly matches the live legacy page**. | None for SIT. PO: confirm the deliberate exclusion. A future "manage deleted purposes" screen is **net-new scope**, not parity. |
| **GAP-2** | ✅ **NON-GAP** (dead legacy; was MEDIUM) | Drag-reorder (`comm_0187_001_u004`) is **dead/unreachable** — gated on `#btn_edit` edit-mode that no longer exists in the live view JSP; `u004` never called. | Backend `PUT /order` + `reorderPurposeSettings()` exist but have **zero UI consumers** — same as legacy (unused on both sides). | None for SIT. Reorder is net-new if ever wanted (backend already built). |
| **GAP-3** | ✅ **CLOSED** (was HIGH) | Language-specific template `WeCMS_Purpose_Upload_{EN\|VI\|KO}.xlsx` (single `.xlsx`, 3 locales) | **Fixed 2026-06-07 (two parts).** (a) **Naming bug root cause:** the refactor shipped one malformed file `WeCMS_Purpose_Bulk_Upload_EN.xlsx.xlsx` (double extension baked into both the on-disk filename *and* the URL literal — not a runtime double-append). (b) **Localization:** only that one EN file existed; KO/VI 404'd. **Now:** double extension removed; three single-extension files ship — `WeCMS_Purpose_Bulk_Upload_{EN,KO,VI}.xlsx` — with localized headers (KO `용도 코드/용도명/용도 설명/주요 용도`, VI `Mã mục đích/Tên mục đích/Mô tả mục đích/Mục đích chính`, matching `PurposeExcelService.HEADER_LABELS_*`). Download resolves per locale; KM→EN fallback. All 3 validated (zip integrity OK, header row at row 3 = refactor `HEADER_ROW_INDEX=2`). | **Done** — `wecms-web/lib/api/purposeSettings.ts:275-308` (`TEMPLATE_LOCALES=["EN","KO","VI"]`, single `.xlsx`, per-locale + KM→EN fallback) + 3 template files under `wecms-web/public/templates/purpose/` (malformed `.xlsx.xlsx` deleted). KO/VI generated from the known-good EN binary by swapping only `xl/sharedStrings.xml` (layout/styles/sample-data byte-identical, so the backend parser still accepts them). tsc `--noEmit` + eslint clean; resolution re-tested EN/KO/VI/KM. **No backend change.** |
| **GAP-4** | MEDIUM | n/a (legacy untested) | `v_purpose_in_use` (V81) implements only branch (a) "parent has active child"; branch (b) "referenced by an active transaction" is a commented template. So `used_in_transactions` semantically = "is a parent with children", and the OQ-5 transaction-reference delete-block **never fires**. | Confirm acceptable for SIT (soft-delete parity holds — legacy soft-delete also only blocks on children). Document that `used_in_transactions` is partial and the transaction-block lands when consumer tables port. Risk: the wire flag misleads if any UI keys off it as "used in a transaction". |
| **GAP-5** | MEDIUM | n/a | No automated frontend tests anywhere in `wecms-web` (no runner configured). | Recommended: add component/integration tests for create-validation, delete-confirm flow, toggle, excel-preview error rendering, hierarchy render. Backend tests are solid; FE is the gap. |
| **GAP-6** | LOW (wording-only; was MEDIUM) | Confirm `WBC1186` says children are removed with parent, but server **blocks** parent delete (`WBE1199`) — a legacy contradiction | **Faithfully reproduced** — server **blocks** identically (`WBE1199`). `confirm.delete_with_children` = *"This will be removed along with all sub-purposes under it…"* → Continue → `WBE1199` → `error.delete_blocked`. **Server parity confirmed.** | Not required for SIT (server behavior matches legacy). Optional quality fix: reword the confirm to match the block. Do **not** implement cascade (would diverge from legacy). |
| **GAP-7** | LOW | n/a | Controller Javadoc (`PurposeSettingsController.java:60-63`) says *"carries no @PreAuthorize"* but line 69 **does** carry `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")`. Behavior is correct (gated); comment is stale. | Doc-only: fix the stale Javadoc. No behavior change. |
| **GAP-8** | LOW | n/a | `wecms-web/docs/features/settings/purpose.md` is stale: describes drag-reorder (unbuilt), `/upload` endpoint (actually `/excel/preview`+`/excel/import`), `settings.purpose_settings.*` namespace (actually `settings.purpose.*`), trash pre-disabled by `usedInTransactions` (actually always-enabled). | Doc-only: update the frontend feature doc to match shipped code. |
| **GAP-9** | LOW | EN/KO/VI header title match | Excel header validation uses **case-sensitive** `startsWith` (`PurposeExcelService.java:684-692`) despite locale-aware label lists; 2 hardcoded English strings in `ParentPurposePicker.tsx:175,187` (`aria-label` + "No matches"). | Low: make header match case-insensitive; localize the 2 picker strings. |
| **GAP-10** | LOW | `wb_get_next_val('busi_usag_set_infm',…)` is bare MAX+1, no lock; no DB uniqueness on hand-code/name | Same — PK-gen is MAX+1 with a one-shot retry on `DataIntegrityViolationException`; uniqueness is service-`SELECT`-enforced only (no partial unique index). | Acceptable for local R&D (matches legacy). Before any multi-user load: add partial unique indexes (`WHERE use_yn='Y' AND usag_grp_cd IS NULL`). |

No HIGH-severity correctness/data-integrity bug was found in the backend logic.

---

## Final Legacy Usage Re-check for GAP-1 / GAP-2 / GAP-6

> **Re-verified 2026-06-07 against actual source** (legacy view JSP + TS + action JSP, refactor controller/service + panel). **Headline:** the legacy `comm_0187_001.ts` carries a *"full rewrite aligned to comm_0223_001"* (header comment, `comm_0187_001.ts:11`). That rewrite kept the **handlers** for the deleted-tab/restore and drag-reorder, but the rewritten **view JSP dropped their trigger elements** — so both features are **dead/unreachable in the live legacy page**. This materially downgrades GAP-1 and GAP-2 from the original audit (which assumed legacy still exposed them).

| Gap | Legacy Used? | Legacy Evidence | Refactor Evidence | SIT Required? | Final Recommendation |
|---|---|---|---|---|---|
| **GAP-1** Deleted/Restore/Hard-delete | **NO** (dead/unreachable) | `comm_0187_003_view.jsp:1-26` is an **empty shell** (JEX header block only, no body/markup). The deleted-purpose code is inline in `comm_0187_001.ts`, gated on UI elements the current view JSP **no longer contains**: tab handler `comm_0187_001.ts:404-407` binds via `$doc.find(".deleted_sup_pur").on(...)` — but `comm_0187_001_view.jsp` has **no `.deleted_sup_pur` element** (only a leftover CSS selector at `:106`); `fetchDataUnused` (`:579-606`, calls `comm_0187_003_r001`) targets `#tbody_deleted` — **absent from the view**; `restoreData` (`:953-966`, calls `comm_0187_003_u001`) needs `#btn_restore` — **absent**. `onload` (`:122-123`) calls only `fetchData({})` + `loadParentPurposes()`, never `fetchDataUnused`. No live code path reaches the deleted list, restore, or hard-delete. | No deleted-list / restore / hard-delete **endpoint** (`PurposeSettingsController` has none) and no **UI** panel. `delete()` is soft-only (`PurposeSettingsService.java:321` `setUseYn("N")`). | **NO** | **Refactor correctly matches the live legacy page** — neither surfaces deleted-purpose management. Treat as **dead/broken legacy → exclude** (not a parity gap). A future "manage deleted purposes" screen would be **net-new scope**, not parity. PO: confirm the deliberate exclusion. *(Downgraded from MEDIUM/PO-decision.)* |
| **GAP-2** Reorder drag UI | **NO** (dead/unreachable) | Drag-sort is initialized **only** inside the edit-mode toggle: `comm_0187_001.ts:144` `#btn_edit` handler → `:173` `if (active)` → `:199-202` `new Sortable($tbody[0], …)`. The save trigger is `$doc.on("drop", "tr.data_drag", …)` → `updateDisOrder` (`:437-439`) → `comm_0187_001_u004` (`:1287-1307`). **`comm_0187_001_view.jsp` has no `#btn_edit`, no `tr.data_drag`, no draggable trigger** (grep: only chevron-toggle CSS + `.btn-add`). `buildRow` data rows (`:701-709`) carry no `draggable`/`data_drag`. Edit-mode is never entered ⇒ Sortable never created ⇒ `u004` never called from the live UI. | Backend `PUT …/order` + `reorderPurposeSettings()` (`lib/api/purposeSettings.ts`) **exist but have zero consumers** — no drag handlers anywhere in `components/settings/purpose/`. | **NO** | **Not a parity gap** — both the live legacy page and the refactor lack a working reorder UI; the backend `/order` endpoint is unused on both sides (refactor built it speculatively). Treat legacy reorder as **dead → exclude**. If reorder is wanted, it's **net-new** (backend is already ready). *(Downgraded from MEDIUM.)* |
| **GAP-6** Delete confirm vs cascade | **YES** (live flow, both sides) | **Confirm:** `comm_0187_001.ts:273-289` — parent (`has-children`) shows `WBC1186` (cascade-worded warning, described `:264-272` as *"…will be removed along with all sub-purposes under it"*); leaf deletes directly. This flow **is live** (the `.delete-purpose` button is injected per-row by `buildRow`, `:752-758`). **Server:** `comm_0187_001_u002_act.jsp:155-158` — `if (hasActiveChildren(...)) throw WbRollbackException(WBE1199)`. The server **blocks**; it never cascades. | **Confirm:** `PurposePanel.tsx:257-272` — leaf → `performDelete` directly; parent-with-children → confirmation `settings.purpose.confirm.delete_with_children` (`en.json:787` = *"This will be removed along with all sub-purposes under it. This action cannot be undone."*) → `performDelete`. **Server:** `PurposeSettingsService.java:307-309` — `hasActiveChildren` → `WBE1199` (block). The in-code comment (`PurposePanel.tsx:245-256`) itself states both "the delete cascades" *and* that `WBE1199` lands — the same contradiction. | **NO** (server behavior already at parity) | **Legacy: block (`WBE1199`). Refactor: block (`WBE1199`).** Server behavior is **identical** — refactor faithfully reproduces legacy, including legacy's own misleading confirm wording. **Action = change wording only** (recommended quality fix: reword `delete_with_children` to "Remove its sub-purposes first…" so it matches the block), **not** required for SIT parity, and **do not** implement cascade (would diverge from legacy server behavior). |

### Per-item final answers (as requested)

**GAP-1 — Deleted / Restore / Hard-delete**
1. Reachable from `comm_0187_001` UI? **No** — the `.deleted_sup_pur` tab element was removed from the current view JSP; the handler binds to nothing.
2. Button/menu/link/openPopup for deleted list? **No** — no element in `comm_0187_001_view.jsp`; `comm_0187_003_view.jsp` is an empty shell; no `openLayerPopup`/`openNewTab` for `comm_0187_003` anywhere in `ts-src` (only the two inline ajax calls in `comm_0187_001.ts`).
3. APIs `comm_0187_003` calls: `comm_0187_003_r001` (read deleted — via `fetchDataUnused`) and `comm_0187_003_u001` (restore `REC` / hard-delete `REC2` — via `restoreData`). Both have no live caller.
4. Restores deleted rows? Code exists (`restoreData`→`u001` `REC`, `USE_YN='Y'`) but **unreachable**.
5. Hard-deletes rows? Code exists (`u001` `REC2`, `WBE1061` guard → `BUSI_USAG_SET_INFM_D001`) but **unreachable**.
6. Works for `USAG_GRP_CD IS NULL`? **No** — `comm_0187_003_r001_act.jsp` hard-codes `usag_grp_cd IN ('4','6')`, so even if reachable it would target only the dormant legacy groups, never the active NULL-group model.
7. Port / exclude / dead? **Dead/broken legacy → exclude.**
- **Used in real legacy flow? NO.**
- **Required for SIT parity? NO** (PO: confirm deliberate exclusion).

**GAP-2 — Reorder drag UI**
1. Drag/drop or move up/down in `comm_0187_001.ts`? **Drag/drop only** (`Sortable`, `:181-202`) — no move-up/down buttons.
2. `comm_0187_001_u004` called from UI? **Not in the live page** — only via the drop handler that fires inside edit-mode, which can't be entered.
3. Reorder save trigger: `drop` on `tr.data_drag` → `updateDisOrder` → `u004` (`:437-439`, `:1287-1307`).
4. Visible to normal users? **No** — gated on `#btn_edit` edit-mode, which is absent from the view.
5. Refactor equivalent UI? **No** — no drag handlers; `reorderPurposeSettings` is dead code.
6. Backend `/order` exists but frontend doesn't call it — real parity gap? **No** — the legacy frontend doesn't call `u004` either; both are unused. It's a speculative backend endpoint, not a parity miss.
- **Used in real legacy flow? NO.**
- **Required for SIT parity? NO.**

**GAP-6 — Delete confirm wording vs cascade**
1. Confirm before deleting a parent: `WBC1186` (legacy) / `settings.purpose.confirm.delete_with_children` (refactor).
2. Frontend says children will be deleted together? **Yes** (both — "…removed along with all sub-purposes…").
3. What server `u002` does with active children: throws `WBE1199` (legacy `comm_0187_001_u002_act.jsp:155-158`).
4. Cascade or block? **Block** (`WBE1199`) — never cascades.
5. Refactor: **Block** (`WBE1199`, `PurposeSettingsService.java:307-309`).
- **Legacy behavior: block delete (`WBE1199`).**
- **Refactor behavior: block delete (`WBE1199`).**
- **Required action before SIT: change wording only** (optional quality fix; *not* a parity blocker; do **not** implement cascade).

---

## Detailed findings + source evidence

### 1. Menu / Route / Authorization
- **Backend gate:** `PurposeSettingsController.java:69` — class-level `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")` + `@Validated` (`:68`). Every endpoint requires the menu grant. *(Javadoc `:60-63` contradicts this — stale; see GAP-7.)*
- **Path patterns:** `USE_USAG_CD_PATTERN = ^[A-Za-z0-9]{1,20}$` (`:76`), `KEYWORD_PATTERN` (`:81`).
- **Frontend reachability:** Settings top-nav → `settingsModal.open()` (`Header.tsx:132`), default panel `"purpose"` (`SettingsModalProvider.tsx:44`), nav entry gated by entitlement slug `settings-core-purpose` (`SettingsNav.tsx:54,127`). **No `app/(dashboard)/settings/**` route** — modal-only, by design (matches the approved Settings-modal baseline).
- **Tenant:** every service method calls `requireBizKey()` (`PurposeSettingsService.java:128,147,161,…`) from `TenantContext`; `bizKey` is part of the PK. Body/path can never override tenant. **Parity ✅** (legacy enforced `BIZ_KEY` server-side identically; refactor adds explicit menu-authz, which is stronger).

### 2. List / Search / Hierarchy
- **Backend:** `PurposeSettingsRepository.findActiveForTenant` (`:46-59`) — JPQL `WHERE biz_key=:bizKey AND usag_grp_cd IS NULL AND use_yn='Y' AND use_usag_cd NOT IN :reservedCodes AND (:keyword='' OR LOWER(use_usag_nm) LIKE …) ORDER BY disp_ord ASC NULLS LAST, use_usag_cd ASC`.
- **Reserved codes:** `RESERVED_SYSTEM_CODES` = `1000000301`, `3000000401`–`408` (`PurposeSettingsService.java:105-108`) — exact match to legacy `comm_0187_001_r001_act.jsp:58-67`.
- **Hierarchy:** `assembleTree` (`:416-466`) builds level-1→children; orphans surface as top-level (`:441-445`). Frontend `PurposeRow.tsx:77` indents children, auto-expands parents with children (`PurposePanel.tsx:128-142`).
- **Dormant legacy correctly dropped:** no group-tab model, no `u003` reset. **Parity ✅** with the active NULL-group model documented in the legacy dossier.

### 3. Create
- `PurposeSettingsService.create` (`:159-196`): dup hand-cd→`WBW1152` (`:172-174`), dup name→`WBW1138` (`:175-177`), `validateParent` (`:178`), `disp_ord = findMaxDispOrd+1` (`:180`), `persistCreate` seq key via `repo.nextUseUsagCd` (`:209`, native `wb_get_next_val`, V82), defaults `useYn/dispYn='Y'`, `usagGrpCd` NULL (`:216-226`), audit append slug `purpose-settings` (`:183-190`). DTO validation `@NotBlank @Size` (`PurposeSettingCreateRequest.java:23-43`). **Parity ✅**, plus server-side 3-level block (`validateParent :485-489`) that legacy enforced only client-side.

### 4. Update
- `update` (`:244-289`): **no hand-cd dup check** (`:259` — deliberate OQ-4 legacy parity, asserted by `update_persistsChanges_andDoesNotCheckHandCdDup_legacyParity`), name-dup excl-self→`WBW1138` (`:261-263`), `validateParent` + `validateNoDemoteWithChildren` (`:264-265`), `dispYn` set only when non-null (`:271`). **Parity ✅** with legacy `comm_0187_001_u002` REC path.

### 5. Delete (soft)
- `delete` (`:296-337`): soft `setUseYn("N")` (`:321`), block when `hasActiveChildren`→`WBE1199` (`:307-309`, mirrors `comm_0187_001_u002_act.jsp:75-91`), plus OQ-5 view check (`:314-319`) — **currently a no-op** because `v_purpose_in_use` (V81) only carries branch (a). **Soft-delete parity ✅.**

### 6. Deleted / Restore / Hard delete — **MISSING**
- No endpoint lists soft-deleted rows, restores them, or hard-deletes. `PurposeSettingsController` has no `/deleted`, `/restore`, or hard-delete path. Legacy `comm_0187_003_r001`/`_u001` (+`WBE1061` usage guard, `BUSI_USAG_SET_INFM_D001`) have no counterpart. **GAP-1.**

### 7. Excel
- **Preview** `PurposeExcelService.preview` (`:200`): header match (`:581-598`), row cap 500→`WBE2041` (`:216-220`), per-row validation + parent 2-level rule, returns `{rows, total, completed, errored}`. **Import** `import_` (`:357`): re-validates against live state, roots-first then children, atomic. **Backend parity ✅** with `comm_0187_004`.
- **Frontend:** `ExcelUploadDialog.tsx` — preview→`previewPurposeExcel`, import→`importPurposeExcel`, upload disabled when `errorCount>0` (`:203`). **Template download broken for KO/VI** — only `_EN.xlsx.xlsx` on disk (`public/templates/purpose/`), `downloadPurposeTemplate` (`purposeSettings.ts:293-305`) builds `_KO/_VI.xlsx.xlsx` URLs that 404. **GAP-3.**
- Header match is case-sensitive `startsWith` (`PurposeExcelService.java:684-692`). **GAP-9.**

### 8. Database mapping
- `V35__busi_usag_set_infm.sql` — column-for-column with JEX (15 cols, PK `(biz_key, use_usag_cd)`, partial parent index). `BusiUsagSet.java` maps all 15. Note: task field-name list (`USAG_CD`, `USAG_NM`, `USAG_DESC`, `PARN_USAG_CD`, `DEL_YN`) uses abbreviated names; the **actual** legacy + refactor columns are `USE_USAG_CD`, `USE_USAG_NM`, `USE_USAG_CONT`, `PARENT_USE_USAG_CD`, and soft-delete is `USE_YN` (there is **no** `DEL_YN` on this table).

| Legacy column | Refactor entity field | DTO wire | UI |
|---|---|---|---|
| `USE_USAG_CD` | `useUsagCd` (PK) | `use_usag_cd` | row id (not shown) |
| `HAND_USE_USAG_CD` | `handUseUsagCd` | `hand_use_usag_cd` | Purpose Code |
| `USE_USAG_NM` | `useUsagNm` | `use_usag_nm` | Purpose Name |
| `USE_USAG_CONT` | `useUsagCont` | `use_usag_cont` | Description |
| `PARENT_USE_USAG_CD` | `parentUseUsagCd` | `parent_use_usag_cd` | Parent Purpose |
| `USE_YN` | `useYn` | (not on wire) | soft-delete flag |
| `DISP_YN` | `dispYn` | `disp_yn` | visibility toggle |
| `DISP_ORD` | `dispOrd` | `disp_ord` | (reorder — UI missing) |
| `USAG_GRP_CD` | `usagGrpCd` | (NULL always) | n/a |

### 9. API parity

| Legacy API | Legacy behavior | Refactor API | Match? | Gap |
|---|---|---|---|---|
| `comm_0187_001_r001` | list (NULL group) | `GET /api/v1/settings/purpose-settings` | ✅ | — |
| `comm_0187_001_r002` | name search | `GET …?keyword=` | ✅ | — |
| `comm_0187_001_c001` | create | `POST …` | ✅ | — |
| `comm_0187_001_u002` (REC) | edit | `PUT …/{id}` | ✅ | overloaded `u002` cleanly split |
| `comm_0187_001_u001` | toggle `DISP_YN` | `PUT …/{id}` (disp_yn) | ✅ | folded into `PUT` — no dedicated toggle endpoint (acceptable divergence) |
| `comm_0187_001_u002` (REC2) | soft-delete | `DELETE …/{id}` | ✅ | — |
| `comm_0187_001_u004` | reorder | `PUT …/order` | ✅ (dead legacy) | endpoint exists; no UI either side — legacy reorder unreachable (GAP-2 non-gap) |
| `comm_0187_003_r001` | deleted list | — | ✅ (dead legacy) | legacy unreachable; correctly omitted (GAP-1 non-gap) |
| `comm_0187_003_u001` (REC) | restore | — | ✅ (dead legacy) | legacy unreachable; correctly omitted (GAP-1 non-gap) |
| `comm_0187_003_u001` (REC2) | hard delete | — | ✅ (dead legacy) | legacy unreachable; correctly omitted (GAP-1 non-gap) |
| `comm_0187_001_u003` | reset to defaults | — | ✅ (intentional) | legacy dormant — correctly dropped |
| `comm_0187_004_r001` | excel preview | `POST …/excel/preview` | ✅ | — |
| `comm_0187_004_c001` | excel import | `POST …/excel/import` | ✅ | — |
| template download | lang-specific xlsx | static asset (3 localized files) | ✅ | GAP-3 closed — single-extension EN/KO/VI; per-locale + KM→EN |
| `comm_0180_001_c002` | activity log | `AuditLogService.append` (slug wsvc) | ✅ | — |

### 10. UI parity
- Implemented (`components/settings/purpose/`): list via `useApiData(fetchPurposeSettings)` (`PurposePanel.tsx:103`); 2-level indent/expand (`PurposeRow.tsx`); inline add/edit (`PurposeFormRow.tsx`); delete — leaf immediate, parent shows confirm (`PurposePanel.tsx:257-273`); active toggle full-PUT (`:345-365`); parent picker level-1 only (`ParentPurposePicker.tsx`); excel upload; empty/loading states.
- **Missing:** reorder/drag — no handlers; `reorderPurposeSettings` is dead code (`purposeSettings.ts:260`). **GAP-2.** Trash button always-enabled (server-validated, not pre-gated by `usedInTransactions`) — divergence from stale doc; acceptable given server enforces `WBE1199`.

### 11. Messages / Localization
- Backend codes returned verbatim: `WBW1152`/`WBW1138`/`WBE1199` (`PurposeSettingsService.java:173,176,308`). Excel: `WBE2041/2042/2043` + `WBE_VALIDATION`.
- Frontend i18n complete EN/KO/VI (`en.json:743-797`): `error.duplicate_code`="A purpose with this code already exists." (the misleading legacy `WBW1152` "restore from deleted screen" text was **correctly dropped** — good, since there is no deleted screen), `error.duplicate_name`, `error.delete_blocked`, `confirm.delete_with_children`, `success.*`.
- The 5 legacy "Jex-Studio-only" codes (`WBI6002/WBE6021/WBC1186/WBC6003/WBE1041`) are **re-sourced** into i18n keys / DTO validation — **no Jex-Studio ML sourcing needed before SIT** (this resolves the open risk flagged in the legacy dossier). Two trivial hardcoded strings remain (GAP-9).

### 12. Tests
- Backend: `PurposeSettingsServiceTest` (22), `PurposeExcelServiceTest` (12), `PurposeSettingsControllerTest` (11) — cover dup codes/names, parent/child + 3-level block, delete-block, soft-delete, reorder validation, excel preview/import, snake_case wire. *(Note: controller test uses standalone MockMvc — `@PreAuthorize` is **not** exercised by any test.)*
- Frontend: **none** (no test runner). **GAP-5.**

---

## Proposed code changes (LISTED — not applied)

Per the task instruction, no business logic is modified. The following are proposed; **awaiting go-ahead** before any edit. Backend edits also require the `wecms-api` scope-guard trigger (explicit backend-change request).

1. ~~**GAP-3 (HIGH, frontend, safe):** KO/VI Excel template 404 + `.xlsx.xlsx` naming~~ — **DONE 2026-06-07.** Removed the malformed double-extension file; shipped 3 single-extension localized templates (`WeCMS_Purpose_Bulk_Upload_{EN,KO,VI}.xlsx`); `downloadPurposeTemplate` resolves per locale (KM→EN). `wecms-web/lib/api/purposeSettings.ts:275-308` + `public/templates/purpose/`. tsc + eslint clean. No backend change.
2. **GAP-7 (LOW, backend doc):** fix the stale Javadoc at `PurposeSettingsController.java:60-63` to state the `@menuAuthz` gate is present.
3. **GAP-8 (LOW, frontend doc):** update `wecms-web/docs/features/settings/purpose.md` (drag-reorder unbuilt, `/excel/*` endpoints, `settings.purpose.*` namespace, trash always-enabled).
4. **GAP-9 (LOW):** case-insensitive header match in `PurposeExcelService.java:684-692`; localize `ParentPurposePicker.tsx:175,187`.
5. **GAP-2 (MEDIUM, frontend feature):** wire the existing `/order` endpoint to a drag UI — design + PO confirm first.
6. **GAP-6 (MEDIUM, UX/PO):** reword `settings.purpose.confirm.delete_with_children` to match block-behavior, or implement true cascade — PO decision.
7. **GAP-1 (MEDIUM, scope):** port `comm_0187_003` (deleted/restore/hard-delete) — PO scope decision.

---

## Final Recommendation

**Is Purpose ready for SIT?** — **Yes (GO).** Core CRUD + hierarchy + excel import/download + authorization + tenant isolation are at parity and backend-tested; the only HIGH gap (GAP-3) is closed; GAP-1/GAP-2 are dead-legacy non-gaps; GAP-6 is server-parity with a wording-only follow-up. SIT can proceed.

**Must fix before SIT sign-off:**
- ~~**GAP-3** — KO/VI Excel template 404.~~ ✅ **CLOSED 2026-06-07** (EN fallback in `purposeSettings.ts`). No remaining HIGH-severity blocker.

**Re-verified 2026-06-07 — no longer blockers (see "Final Legacy Usage Re-check"):**
- **GAP-1** — Deleted/Restore/Hard-delete is **dead/unreachable in the live legacy page** (empty `comm_0187_003_view.jsp`; trigger UI removed from the view JSP). Refactor correctly matches legacy. **Not required for SIT.** PO: confirm the deliberate exclusion only.
- **GAP-2** — drag-reorder is **dead/unreachable in the live legacy page** (`#btn_edit` edit-mode absent from the view JSP; `u004` never called). Both sides lack reorder UI. **Not a parity gap; not required for SIT.**
- **GAP-6** — server behavior is at parity (both **block** with `WBE1199`). **Change wording only** (optional quality fix); do **not** implement cascade. **Not required for SIT.**

**Can be deferred (track, don't block SIT):**
- **GAP-4** — `used_in_transactions` / OQ-5 transaction-block lands when consumer tables port; document the partial coverage.
- **GAP-5** — frontend tests.
- **GAP-7/8/9/10** — stale docs, case-insensitive header, hardcoded strings, DB uniqueness index.

**Requires PO confirmation (all downgraded after the 2026-06-07 legacy re-check — none block SIT):**
- **GAP-1** — confirm the **deliberate exclusion** of Deleted/Restore/Hard-delete (it is dead/unreachable in the live legacy page; a future manage-deleted screen would be net-new, not parity).
- **GAP-2** — confirm reorder stays **deferred/net-new** (dead in live legacy; backend `/order` already exists if/when a UI is wanted).
- **GAP-6** — confirm the optional **confirm-wording** fix (reword to match the block) vs. leaving it identical to legacy. No cascade.

**Bottom line:** the refactor faithfully represents the *active* legacy Purpose model and avoids porting the dormant legacy group/tab machinery. GAP-3 is fixed; the "missing" restore/reorder capabilities are dead/unreachable in the live legacy page (so not parity gaps); GAP-6 is at server parity. **Purpose is SIT-ready.**

---

## Final SIT Readiness

**Date:** 2026-06-07 · **Verification:** source-re-read of `wecms-api` + `wecms-web` + legacy `WECMS DEV` view JSP (not docs-only).

### ✅ GO FOR SIT

No HIGH-severity gap, no SIT blocker, and no user-reachable missing legacy flow remains. Every prior blocker is closed or reclassified to a non-gap, documentation, recommendation, wording, or low-risk-hardening item.

| Area | Status | Notes |
|---|---|---|
| List / Search | ✅ Parity | `USAG_GRP_CD IS NULL` + `USE_YN='Y'`, reserved codes excluded, keyword `ilike`. `PurposeSettingsRepository.findActiveForTenant`; `PurposeSettingsController:89`. |
| Create | ✅ Parity (stronger) | dup hand→`WBW1152`, dup name→`WBW1138`, seq key + `disp_ord` MAX+1, defaults `Y/Y`, server-side 2-level enforce. `PurposeSettingsService.create`. |
| Update | ✅ Parity | name-dup excl-self→`WBW1138`, parent + no-demote validation, `disp_yn` patch. `PurposeSettingsService.update`. |
| Delete (soft) | ✅ Parity | `setUseYn("N")` + block parent-with-children→`WBE1199` (`PurposeSettingsService.java:307-308`). Matches legacy `comm_0187_001_u002_act.jsp:155-158`. |
| Hierarchy | ✅ Parity | 2-level parent→child tree, expand/collapse, `disp_ord` order; orphans surfaced top-level. Matches active legacy model. |
| Excel upload/download | ✅ Parity | Preview+import (`/excel/preview`,`/excel/import`); 3 single-extension localized templates EN/KO/VI on disk; per-locale download (GAP-3 closed). |
| Authorization | ✅ Parity (better) | `@PreAuthorize("@menuAuthz.canAccess('settings-core-purpose')")` (`PurposeSettingsController:69`); modal-gated via Settings nav slug. |
| Tenant scoping | ✅ Parity | `requireBizKey()` on every method; `bizKey` is part of the PK; body/path cannot override tenant. |
| Deleted/Restore/Hard-delete | ✅ Parity (dead legacy) | Unreachable in live legacy (`comm_0187_003_view.jsp` empty shell; no trigger UI in `comm_0187_001_view.jsp` — only leftover CSS `:106`). Refactor correctly omits it. |
| Reorder (drag) | ✅ Parity (dead legacy) | Unreachable in live legacy (`#btn_edit` edit-mode absent from view JSP; `u004` never called). Both sides lack a reorder UI. |
| Delete confirm wording | ✅ Server parity | Both block with `WBE1199`; cascade-worded confirm is identical in both. Wording-only follow-up. |
| Backend tests | ✅ Present | `PurposeSettingsServiceTest`, `PurposeExcelServiceTest`, `PurposeSettingsControllerTest`. |

### Remaining items — classified

**Required before SIT:** _None._ (No HIGH gap, no blocker, no reachable missing flow.)

**Recommended after SIT:**
- **GAP-5** — add frontend tests (create-validation, delete-confirm flow, toggle, excel-preview errors, hierarchy render).
- **GAP-6** — reword `settings.purpose.confirm.delete_with_children` to match the block (do **not** implement cascade).
- **GAP-9** — case-insensitive excel header match; localize 2 hardcoded picker strings.
- **GAP-10** — add partial unique indexes before multi-user load (low-risk hardening).

**Documentation only:**
- **GAP-4** — note `used_in_transactions` partial coverage (OQ-5 transaction-block lands when consumer tables port).
- **GAP-7** — fix stale `PurposeSettingsController` Javadoc (says "no @PreAuthorize"; line 69 has one).
- **GAP-8** — update stale `wecms-web/docs/features/settings/purpose.md`.

**PO decision only (none block SIT):**
- **GAP-1** — confirm deliberate exclusion of Deleted/Restore/Hard-delete (dead legacy; future screen = net-new scope).
- **GAP-2** — confirm reorder stays deferred/net-new (dead legacy; backend `/order` already exists).

### Approval

No HIGH-severity gap remains, and all remaining items are dead-legacy, documentation-only, recommendation, or wording-only.

**Purpose feature is approved for SIT.**
