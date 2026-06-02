# Purpose Settings — Task plan (Phase 1 → Phase 4)

**Refactor slug:** `purpose-settings`
**Sibling docs:** [feature spec](../../../features/settings/purpose.md) · [JEX → Spring mapping](../../../references/migration/purpose-settings.md) · [backend design](../../../references/business-rules/settings/purpose-settings.md) · [frontend design](../../../../../wecms-web/docs/features/settings/purpose.md) · [DB schema](../../../references/database/settings/purpose-settings-schema.md).
**Budget context:** 8 dev days + 2 test days per memory `project_team_roster.md` (Sonny lead, Arthur dev, Mr. Youvita AI-assisted test, Mr. Jung PO).
**Phase 1 status:** Spec + design docs complete. Frontend static UI already exists. Backend not started.

This document is the task ordering + effort estimate for Phases 2, 3, and 4. It is the input that future "Prompt 2 / 3 / 4" sessions consume to write code.

> Refactor is local-only R&D (`localhost:5434/wecms_dev` + GitLab). No DEV/UAT/PROD deploy, no bank integration, no coexistence work. Per memory `project_refactoring_scope.md`.

---

## 1. Phase summary

| Phase | Owner | Output | Effort estimate |
|---|---|---|---|
| **Phase 1** — Design (this session) | AI + Sonny | 6 design docs under `docs/refactor-wecms/` | 0.5 day (this turn) — DONE |
| **Phase 2** — Backend | Arthur (AI-assisted) | Spring controller + service + repo + entities + DTOs + Flyway V80 + V81 + tests | **3 days** |
| **Phase 3** — Frontend wire-up | Arthur (AI-assisted) | Replace `mockPurposeTree` with live API; Excel upload wired; i18n keys filed; locale verification | **2 days** |
| **Phase 4** — Local QA | Mr. Youvita (AI-assisted) + Arthur | Manual + scripted regression against all 7 endpoints + 7 frontend states | **2 days** (1.5 dev + 0.5 test) |
| Buffer | — | Open-question follow-ups, code review fix-ups | **0.5 day** |
| **Total** | | | **8 days** dev + **2 days** test ≈ within budget |

The plan fits the 8+2 budget. Risk pad is 0.5 day; if any open question (especially OQ-5, OQ-FE-Frontend-1) requires a PO sync, the buffer absorbs one cycle.

---

## 2. Phase 2 — Backend tasks (ordered)

Effort estimate: **3 days** for Arthur with AI assistance. Each task is a discrete commit; PR slices land in the order below.

### Day 1 — Schema + entity + repository

1. **Flyway V80a — `wb_get_next_val_function.sql`** (1.5 hours — OQ-3 decided 2026-05-12)
   - Extract the full `pg_get_functiondef(...)` body for `wb_get_next_val` from DEV via the `postgres` MCP. Output is ~74k chars; save to a file under `Claude Code (AI)/sql/` and copy verbatim into the migration. Do NOT hand-edit branches.
   - Confirm the function lands under the local DB's default schema (probably `public`) and that the JPA repository's `SELECT wb_get_next_val(...)` native query resolves without a schema prefix.
   - Phase 2 owner: while extracting, re-grep for a `disp_ord` branch where `coalesce(trim($4), '') = ''` (empty `usag_grp_cd`) — if found, the create path uses `wb_get_next_val(..., 'disp_ord', biz_key, '')` instead of the in-service `MAX+1`. Document the result of this check in a comment on the V80a migration.

2. **Flyway V80 — `busi_usag_set_infm_schema.sql`** (1 hour)
   - Create the table per [database doc §3](../../../references/database/settings/purpose-settings-schema.md).
   - Run `./gradlew flywayMigrate` locally; verify against the verification queries in §8 of the DB doc.
   - **Risk:** JDK21 toolchain — memory `project_local_java_toolchain.md` notes the local `JAVA_HOME` issue. Arthur sets `JAVA_HOME=JDK21` before running Gradle.

3. **Flyway V81 — `busi_usag_set_infm_purpose_in_use_view.sql`** (30 min)
   - Create `v_purpose_in_use` view per [database doc §3](../../../references/database/settings/purpose-settings-schema.md).
   - Verification query: `SELECT * FROM v_purpose_in_use LIMIT 5;`.

4. **Entity + composite PK** (1 hour)
   - `BusiUsagSet.java` extends `BaseEntity` per [backend §5.1](../../../references/business-rules/settings/purpose-settings.md).
   - `BusiUsagSetId.java` Serializable composite key.
   - Resolve **OQ-Backend-1** — confirm `BaseEntity` `@PrePersist` doesn't break against the missing `REG_*` columns; override locally if needed.

5. **Repository** (1 hour)
   - `PurposeSettingsRepository` extends `JpaRepository` + `JpaSpecificationExecutor` per [backend §4](../../../references/business-rules/settings/purpose-settings.md).
   - Includes `nextUseUsagCd(bizKey)` native query against the ported `wb_get_next_val(...)` function — see backend §4 for the signature.
   - `PurposeInUseRepository` over the view, marked `@Immutable`.
   - `PurposeInUseRow` projection entity (Serializable, PK = composite `BusiUsagSetId` of (`biz_key`, `use_usag_cd`)).

6. **`@DataJpaTest` smoke tests** (1.5 hours)
   - Verify `findActiveByHandCd`, `findActiveByName`, `findDuplicateNameExcludingSelf`, `hasActiveChildren`, `findMaxDispOrd` queries against an empty schema + a 5-row seed.
   - Verify `nextUseUsagCd(bizKey)` returns `'0000000001'` on an empty refactor-scope, then `'0000000002'`, `'0000000003'`, … as rows accumulate. Verify it skips the legacy ranges (insert a sentinel `1xxxxxxxxx` row; confirm `nextUseUsagCd` still returns a `0xxxxxxxxx` code).
   - Confirm OQ-DB-5 (`numeric → Integer` mapping for `disp_ord`).

### Day 2 — Service + DTOs + audit-log integration

7. **DTO records** (1 hour)
   - `PurposeSettingRow`, `PurposeSettingDetail`, `PurposeSettingListResponse`, `PurposeSettingCreateRequest`, `PurposeSettingUpdateRequest`, `PurposeReorderItem`, `PurposeReorderRequest`, `PurposeUploadRow`, `PurposeUploadRequest`, `PurposeUploadError`, `PurposeUploadResult` per [backend §5.3](../../../references/business-rules/settings/purpose-settings.md).
   - Bean Validation annotations per [backend §6](../../../references/business-rules/settings/purpose-settings.md).

8. **Service skeleton + tree assembly** (2 hours)
   - `PurposeSettingsService.list()` + `detail()` first — read-only path.
   - `RESERVED_SYSTEM_CODES` constant (9 codes).
   - Server-side tree assembly (level-1 + nested `children`) per OQ-Backend-3.
   - LEFT JOIN against `v_purpose_in_use` to populate `usedInTransactions`.

9. **Service mutations** (3 hours)
   - `create(...)` — duplicate checks, parent validation, **`repo.nextUseUsagCd(bizKey)` via `wb_get_next_val(...)` for PK generation** (OQ-3 decided 2026-05-12), `MAX(disp_ord)+1` for disp_ord (or `wb_get_next_val(..., 'disp_ord', ...)` if Phase 2 task #1 confirms a matching branch), audit append. Wrap call + INSERT in one `@Transactional`; on `DataIntegrityViolationException` for the PK, retry once.
   - `update(...)` — duplicate-name-excluding-self, parent validation, demote-blocked check.
   - `delete(...)` — `hasActiveChildren` check + recursive transaction-reference check (OQ-5 decided 2026-05-12), soft-delete (`use_yn='N'`).
   - `reorder(...)` — `PurposeReorderValidator`, batch update in one transaction.

10. **Audit log integration** (1 hour)
    - Confirm the shared `AuditLogService` is the one introduced by `firm-banking-settings` (V63).
    - Append 3 locale rows (EN / KO / VI) per write under one `audit_logs_seq_no`. Resolve `audit.purpose_settings.create/update/delete/reorder/upload` via `MessageResolver`.

### Day 3 — Controller + upload + tests + i18n keys

11. **Controller** (1 hour)
    - `PurposeSettingsController` with all 7 endpoints per [backend §2](../../../references/business-rules/settings/purpose-settings.md).
    - `@PreAuthorize("hasRole('ADMIN')")` on every mutation.

12. **`upload(...)` service method** (1.5 hours)
    - Per-row validation, batch insert in one transaction, rollback on first failure.
    - Returns `PurposeUploadResult { inserted, skipped, errors[] }`.

13. **i18n keys + error codes** (45 min — OQ-8 decided 2026-05-12)
    - **No new `WBE_PURPOSE_*` codes.** Reuse legacy `WBW1152`, `WBW1138`, `WBE1199` verbatim; use the existing generic `WBE_VALIDATION` for every Purpose-specific validation failure (parent invalid / self-parent / demote-blocked / reorder-invalid).
    - Verify the three legacy codes have English message strings in `messages_en.properties`; if missing, port the verbatim text from JEX `dictionary_en.properties` (text reuse, not a new code).
    - **Broaden the `WBE1199` localized text** to read "Cannot delete — this purpose or one of its sub-purposes is in use." (covers both the legacy children case and the OQ-5 transaction-reference case under one wire code).
    - Add `audit.purpose_settings.create/update/delete/reorder/upload` audit message keys (not error codes — distinct namespace).

14. **Service unit tests** (2 hours)
    - `PurposeSettingsServiceTest` covering: list filters, create duplicate checks, update duplicate-name-excluding-self, delete-blocked-by-children, recursive delete-blocked-by-transactions (OQ-5), parent invalid / self / demote-blocked, reorder valid / invalid, upload happy-path + fail-on-first-error, PK-collision retry path (mock `wb_get_next_val` to return a duplicate code once and confirm the service retries).
    - Mock `AuditLogService` to verify audit calls without hitting the DB.

15. **Controller slice tests** (1 hour)
    - `PurposeSettingsControllerTest` — verify URI / verb / auth contract; verify the wire shape (snake_case property names, `{ status, data }` envelope); verify `@PreAuthorize` blocks USER role on mutations.

**Day 3 evening — backend PR up for review.** Single MR or 2-3 stacked MRs (Sonny's call):
- MR1: schema + entity + repository + Flyway V80a + V80 + V81.
- MR2: service + DTOs + audit-log + i18n keys + error codes (including PK-collision retry path).
- MR3: controller + tests.

---

## 3. Phase 3 — Frontend tasks (ordered)

Effort estimate: **2 days** for Arthur. Phase 3 starts after backend MR1+MR2 land (so the local `bootRun` serves real data).

### Day 4 — Wire types, mappers, API client, list/detail wiring

1. **New file: `lib/api/purposeSettings.ts`** (1 hour)
    - Wire types + 7 Axios wrappers per [frontend §3](../../../../../wecms-web/docs/features/settings/purpose.md).
    - `unwrap<T>(...)` helper (same shape as `fetchFirmBankingSettings`).

2. **Extend `types.ts`** (15 min)
    - Add `useUsagCd`, `modId?`, `modDtm?` to `PurposeNode` per [frontend §6](../../../../../wecms-web/docs/features/settings/purpose.md).

3. **Add `mapPurposeSettingRow` to `lib/api/mappers.ts`** (15 min)
    - Wire-snake_case → in-memory camelCase.

4. **Wire `<PurposePanel>` to live data** (1 hour)
    - Replace `useState<PurposeNode[]>(mockPurposeTree)` with `useApiData(fetchPurposeSettings, [])`.
    - Fall back to `mockPurposeTree` on fetch failure (offline-dev mode).
    - Verify the panel renders the 5 DEV rows for `VN_VNM0004_000001`.

5. **i18n keys** (1.5 hours)
    - Author `settings.purpose_settings.*` + `audit.purpose_settings.*` in `locales/en.json`.
    - Port verified KO entries from `WECMS DEV/WECMS/src/dictionary_ko.properties` into `locales/ko.json`. **Never invent translations** — omit missing keys, fall back to EN.
    - Port verified VI entries from `dictionary_vi.properties` into `locales/vi.json`.
    - Per memory `feedback_typography_source` / `wecms-web/CLAUDE.md §13a-bis` — no KM yet.

### Day 5 — Mutations, dialogs, Excel upload, manual QA

6. **Inline-form Save handlers** (2 hours)
    - Wire `<PurposeFormRow>` save → `createPurposeSetting` / `updatePurposeSetting`.
    - Success → `useDialog({ type: "success" })` + refetch + clear inline mode.
    - Error → `useDialog({ type: "error", errorCode })` with localized description from `errorKeyFor(code)`.

7. **Delete confirmation flow** (1 hour)
    - `<RowActions>` delete → `useDialog({ type: "confirmation" })` → `deletePurposeSetting`.
    - Handle `WBE1199` specifically (per OQ-8 — single wire code covers both "has children" and OQ-5 transaction-reference cases). Render the broadened i18n string "Cannot delete — this purpose or one of its sub-purposes is in use."

8. **Toggle flow** (45 min)
    - Optimistic flip + `updatePurposeSetting` PUT with full required fields.
    - Revert on error.

9. **Reorder flow** (1.5 hours)
    - Drag-and-drop wiring (use the existing `<PurposeTable>` drag mechanism — may already be implemented or need extending).
    - 300ms debounced `reorderPurposeSettings` per OQ-FE-Frontend-3.
    - Revert on error.

10. **Excel upload wiring** (1.5 hours)
    - `<ExcelUploadDialog>` Upload button → `uploadPurposeSettings(parsedRows)`.
    - Render per-row errors via the shared shell's error-highlight contract.
    - Refetch + close on success.

11. **Parent dropdown** (per OQ-FE-Frontend-1 — decided 2026-05-12) (45 min)
    - Static-UI inline form stays as-is. Wire the parent selector's option source to the live `fetchPurposeSettings()` result, filtered to **level-1 rows only** (`n.parentUseUsagCd === undefined && n.enabled`). Child (level-2) rows are NEVER selectable as a parent.
    - No Figma re-confirmation needed — Sonny confirmed the static UI direction (2026-05-12).

12. **Demo seed — Flyway V82** (30 min)
    - Idempotent INSERT seeding ~6 purposes for `VN_VNM0004_000001` per [database §3 V82](../../../references/database/settings/purpose-settings-schema.md).

**Day 5 evening — frontend PR up.** Single MR (it's all wire-up; no architectural splits).

---

## 4. Phase 4 — Local QA tasks (ordered)

Effort estimate: **2 days** (1.5 dev fix-up + 0.5 dedicated test by Mr. Youvita).

### Day 6 morning — Mr. Youvita scripted regression

For tenant `VN_VNM0004_000001`, walk every state per [feature §8](../../../features/settings/purpose.md):

1. **List loads** with 5 DEV + 6 seeded purposes = 11 rows.
2. **Add level-1** — verify POST 200 + row appears in list.
3. **Add level-2** — pick a level-1 parent; verify POST 200 + nested under parent.
4. **Add duplicate code** — verify 409 + `WBW1152` + localized message.
5. **Add duplicate name** — verify 409 + `WBW1138` + localized.
6. **Edit name** — verify PUT 200 + row updates.
7. **Edit name to duplicate of another** — verify 409 + `WBW1138`.
8. **Edit parent to non-existent code** — verify 400 + `WBE_VALIDATION` with a contextual message ("parent_use_usag_cd must reference an active level-1 purpose").
9. **Edit parent to self** — verify 400 + `WBE_VALIDATION` ("parent_use_usag_cd cannot equal use_usag_cd").
10. **Edit parent on a level-1 with children (demote)** — verify 400 + `WBE_VALIDATION` ("cannot assign a parent to a row that has active children — would create three levels").
11. **Soft-delete leaf** — verify DELETE 200 + row vanishes.
12. **Soft-delete level-1 with active child** — verify 409 + `WBE1199` + localized.
13. **Soft-delete a purpose used by a payment** — verify 409 + `WBE1199` (OQ-5 decided 2026-05-12 + OQ-8 decided 2026-05-12 — single wire code, broadened message).
13a. **Soft-delete a level-1 purpose whose CHILD is used by a payment** (recursive case) — verify 409 + `WBE1199` even though the level-1 row itself has no direct reference.
13b. **Soft-delete a level-2 (child) purpose used by a payment** — verify 409 + `WBE1199`.
13c. **List endpoint exposes `used_in_transactions`** — verify the wire field is `true` for any row whose own `use_usag_cd` OR an active child's `use_usag_cd` is in `v_purpose_in_use`, so the frontend trash button pre-disables correctly.
14. **Reorder via drag-drop** — drag row to new position; verify PUT 200 + new order persists across refresh.
15. **Toggle `disp_yn`** — verify PUT 200 + toggle persists.
16. **Excel upload — happy path** — upload 5-row XLSX; verify 5 new rows.
17. **Excel upload — duplicate inside batch** — verify 422 + structured errors + zero rows inserted.
18. **Excel upload — duplicate against existing** — same.
19. **Audit log** — open the DB and verify a Purpose audit row exists for each Create / Update / Delete / Reorder / Upload event; verify `wsvc_id = 'purpose-settings'`, `ref_no = 'BUSI_USAG_SET_INFM'`, `ref_no1 = use_usag_cd`, `ref_no2 = hand_use_usag_cd`.
20. **Permission gate** — log in as a non-admin user; verify all mutating endpoints return 403; verify GET still works.
21. **JWT expiry** — let the token expire; verify the global session-expired dialog fires per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md).

### Day 6 afternoon — Arthur fixes regressions

Bug fix-up time. Re-test any failed scenario from Day 6 morning. Update the feature spec / backend doc with any newly discovered behavior.

### Day 7 — Buffer + final review

Reserved for open-question follow-ups, PO sync (OQ-5, OQ-FE-Frontend-1), and the buffer for code review fix-ups.

---

## 5. Risks + open questions consolidated

Sourced from the sibling docs. Each must be answered before its dependent task lands.

| OQ | Sibling doc | Blocking task | Default decision (if PO doesn't override) |
|---|---|---|---|
| OQ-1 (Excel template format) | [feature](../../../features/settings/purpose.md) | Phase 3 task #10 — **DECIDED 2026-05-12** | Follow the existing VAS Account Excel upload template pattern. Purpose's static-UI `<ExcelUploadDialog>` is correct as-is. Final columns / validation can be adjusted later if PO supplies different requirements. |
| OQ-2 (Inline form parent input) | [feature](../../../features/settings/purpose.md) | Phase 3 task #11 — **DECIDED 2026-05-12** | Current static UI is correct. Parent selector shows level-1 rows only; child (level-2) rows never selectable as parent. No UI change needed. |
| OQ-3 (`use_usag_cd` generation) | [feature](../../../features/settings/purpose.md) | Phase 2 task #9 — **DECIDED 2026-05-12** | Port the legacy `wb_get_next_val(...)` DB function (V80a). Call `wb_get_next_val('busi_usag_set_infm', 'use_usag_cd', biz_key, '')` from the service via a native query. PK-collision retry-once handles the function's missing lock. `disp_ord` uses in-service `MAX+1` unless Phase 2 task #1 finds a matching `disp_ord` branch on DEV for `usag_grp_cd = ''`. |
| OQ-4 (`hand_use_usag_cd` dup on update) | [feature](../../../features/settings/purpose.md) | Phase 2 task #8 | Preserve legacy — no check |
| OQ-5 (Payment / transfer reference check on delete) | [feature](../../../features/settings/purpose.md) | Phase 2 task #8 — **DECIDED 2026-05-12** | **Recursive hard-fail.** Block delete when the row itself OR any of its active children is referenced by an active banking-transaction-history / payment / transfer record. No soft-warn. Wire code is `WBE1199` (per OQ-8 — single broadened code; no new `WBE_PURPOSE_*` family). Consumer-table list still TBD (Phase 2 task #8 enumerates `PYMN_*` / `TRNS_*` / `SCDUL_*` / `OVRS_*` / `EXPS_*` exhaustively as branch-(b) UNIONs in `v_purpose_in_use`). |
| OQ-6 (Permission boundary) | [feature](../../../features/settings/purpose.md) | Phase 2 task #10 | `@PreAuthorize("hasRole('ADMIN')")` on mutations |
| OQ-7 (Partial unique index) | [feature](../../../features/settings/purpose.md) | Phase 2.5 (optional) | Off by default |
| OQ-8 (Error code finalization) | [feature](../../../features/settings/purpose.md) | Phase 2 task #13 — **DECIDED 2026-05-12** | **No new `WBE_PURPOSE_*` family.** Reuse legacy `WBW1152` / `WBW1138` / `WBE1199` + generic `WBE_VALIDATION` / `WBE1009` / `WBE9999`. `WBE1199`'s wire scope broadens to cover both children case AND OQ-5 transaction-reference case under one localized message. |
| OQ-FE-1 (View Setting adoption) | [feature](../../../features/settings/purpose.md) | none — already decided | Do NOT adopt |
| OQ-FE-2 (Search input on panel) | [feature](../../../features/settings/purpose.md) | Phase 3 (only if Figma adds it) | Skip until Figma demands it |
| OQ-Backend-1 (`BaseEntity` REG_* on this table) | [backend](../../../references/business-rules/settings/purpose-settings.md) | Phase 2 task #3 | Override `@PrePersist` for `BusiUsagSet` |
| OQ-Backend-2 (Advisory-lock key) | [backend](../../../references/business-rules/settings/purpose-settings.md) | — | **OBSOLETE** — `wb_get_next_val` replaces in-service MAX+1, so the advisory lock isn't used. Concurrency handled by PK retry-once. |
| OQ-Backend-3 (Tree assembly server-side) | [backend](../../../references/business-rules/settings/purpose-settings.md) | Phase 2 task #7 | Server-side |
| OQ-Backend-4 (Pagination on list) | [backend](../../../references/business-rules/settings/purpose-settings.md) | Phase 2 task #7 | No pagination — full list |
| OQ-DB-1 (Materialized view) | [database](../../../references/database/settings/purpose-settings-schema.md) | Phase 2.5 (optional) | Regular VIEW |
| OQ-DB-2 (FK on `parent_use_usag_cd`) | [database](../../../references/database/settings/purpose-settings-schema.md) | Phase 2.5 (optional) | No FK |
| OQ-DB-3 (Partial unique indexes) | [database](../../../references/database/settings/purpose-settings-schema.md) | Phase 2.5 (optional) | Off |
| OQ-DB-5 (`numeric → Integer`) | [database](../../../references/database/settings/purpose-settings-schema.md) | Phase 2 task #5 | Verify in `@DataJpaTest`; bump to `INTEGER` only if Hibernate complains |
| OQ-FE-Frontend-1 (Parent dropdown) | [frontend](../../../../../wecms-web/docs/features/settings/purpose.md) | Phase 3 task #11 — **DECIDED 2026-05-12** | Current static UI is correct. Selector lists level-1 rows only. No UI change. |
| OQ-FE-Frontend-2 (Toggle endpoint shape) | [frontend](../../../../../wecms-web/docs/features/settings/purpose.md) | Phase 3 task #8 | Full PUT (no PATCH `/toggle`) |
| OQ-FE-Frontend-3 (Reorder debounce) | [frontend](../../../../../wecms-web/docs/features/settings/purpose.md) | Phase 3 task #9 | 300ms debounce |
| OQ-FE-Frontend-4 (Loading skeleton) | [frontend](../../../../../wecms-web/docs/features/settings/purpose.md) | Phase 3 task #4 | Simple `<tr>` placeholder rows |

**Critical-path questions for PO sync (Mr. Jung):**
- **OQ-5** — ~~needs explicit consumer-table list~~ **DECIDED 2026-05-12** (recursive hard-fail per Sonny / Figma). Consumer-table enumeration is a Phase 2 engineering task (not a PO decision), branches landed incrementally as each consumer entity ports into the refactor.
- ~~**OQ-2 / OQ-FE-Frontend-1**~~ **DECIDED 2026-05-12** — current static UI is correct; parent selector shows level-1 only; no UI change.
- ~~**OQ-1**~~ **DECIDED 2026-05-12** — Purpose Excel upload follows the VAS Account template pattern; columns/validation adjustable later.
- ~~**OQ-3**~~ **DECIDED 2026-05-12** — port the legacy `wb_get_next_val(...)` PL/pgSQL function (documented exception to the no-legacy-helpers policy). Refactor calls it via a native JPA query. Open Phase 2 sub-task: verify whether the function carries a `disp_ord` branch for `usag_grp_cd = ''`.
- ~~**OQ-8**~~ **DECIDED 2026-05-12** — reuse legacy `WBW1152` / `WBW1138` / `WBE1199` + generic `WBE_VALIDATION`. No new `WBE_PURPOSE_*` family. `WBE1199`'s scope broadens to cover both children case AND OQ-5 transaction-reference case.

---

## 6. Out-of-scope items (explicitly deferred)

| Item | Reason |
|---|---|
| Reset to system defaults (`comm_0187_001_u003`) | Legacy `SYS_USAG_SET_INFM` table not in refactor scope; reset UX not in Figma reference set |
| Unused / restore (`comm_0187_003_r001/_u001`) | Not in Figma reference set; soft-deleted rows queryable via DB |
| Standardization settings (`comm_0187_002`) | Separate Figma-less feature |
| Per-group hierarchy (legacy `USAG_GRP_CD ≠ NULL`) | Refactor scope is level-2 hierarchy only |
| Language-detail table (`BUSI_USAG_SET_INFM_LANG_DETL`) | Legacy comment in `_c001_act.jsp` says single-table model |
| Purpose-Account mapping (`BUSI_USAG_ACCT_INFM_DETL`) | Separate ERP feature |
| KM locale | Not yet supported in `wecms-web` |
| Cross-tenant copy / template | No JEX precedent |
| Bulk soft-delete | Refactor splits into per-row DELETE; bulk via UI not in Figma |

---

## 7. Definition of done

A feature is "done" when:

- [ ] Backend MR1+MR2+MR3 merged into `wecms-api` `main` branch
- [ ] Frontend MR merged into `wecms-web` `main` branch
- [ ] All 21 scripted regression scenarios in §4 pass on the local stack
- [ ] No compiler warnings, no failing tests on `./gradlew test` / `npm run lint && npm run build`
- [ ] `messages.properties` carries every error code referenced in the controller
- [ ] `locales/{en,ko,vi}.json` carry every `t(...)` key the panel renders
- [ ] Audit-log rows visible for each Create / Update / Delete / Reorder / Upload event via direct DB query
- [ ] No `@JsonProperty("UPPER_SNAKE")` in any of the new DTO records
- [ ] No legacy WSVC id (`comm_0187_001`, etc.) appears anywhere in the new code except JEX file:line citations
- [ ] No instance of `text-[Npx]` / `font-[Nweight]` / inline `style={{ fontFamily: ... }}` in the new frontend code
- [ ] [docs/refactor-wecms/audits/](../../../references/audits/) carries a Phase 4 parity audit comparing refactor behavior vs. JEX for the 21 scenarios

---

## 8. References

- [docs/refactor-wecms/features/settings/purpose-settings.md](../../../features/settings/purpose.md) — feature spec.
- [docs/refactor-wecms/migration/purpose-settings.md](../../../references/migration/purpose-settings.md) — JEX → Spring mapping.
- [docs/refactor-wecms/backend/settings/purpose-settings.md](../../../references/business-rules/settings/purpose-settings.md) — backend design.
- [docs/refactor-wecms/frontend/settings/purpose-settings.md](../../../../../wecms-web/docs/features/settings/purpose.md) — frontend design.
- [docs/refactor-wecms/database/settings/purpose-settings-schema.md](../../../references/database/settings/purpose-settings-schema.md) — DB schema.
- [docs/refactor-wecms/planning/settings/erp-transaction-history-plan.md](erp-transaction-history-plan.md) — closest planning precedent.
- Memory `project_team_roster.md` — 8+2 dev/test budget.
- Memory `project_local_java_toolchain.md` — JDK21 toolchain note.
- Memory `project_refactoring_scope.md` — local-only R&D guard rails.
