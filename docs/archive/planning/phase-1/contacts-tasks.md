# Contacts — Task Plan (Prompt 2 backend + Prompt 3 frontend)

| Field | Value |
|---|---|
| Refactor slug | `contacts` |
| Functional spec | [`docs/refactor-wecms/features/contacts/contacts.md`](../../../features/settings/contacts.md) |
| Backend design | [`docs/refactor-wecms/backend/contacts-design.md`](../../../references/business-rules/contacts-design.md) |
| Frontend design | [`docs/refactor-wecms/frontend/contacts-design.md`](../../../../../wecms-web/docs/features/settings/contacts.md) |
| Schema | [`docs/refactor-wecms/database/contacts-schema.md`](../../../references/database/contacts-schema.md) |
| Migration mapping | [`docs/refactor-wecms/migration/api-mapping/contacts-mapping.md`](../../../references/migration/contacts-mapping.md) |

This file is the step-by-step task list the implementation prompts will follow. Prompt 2 (backend MR) and Prompt 3 (frontend MR) each consume one half of this plan. Each task names the file(s) touched, the existing shared modules to reuse, and the exit criterion that gates the next task.

> **Open questions OQ-1 … OQ-8** in [`contacts.md` § 14](../../../features/settings/contacts.md) must be answered by Sonny / PO **before** Prompt 2 starts. They drive concrete code decisions (TRCO_KEY generator shape, in-use guard scope, etc.). The plan below assumes the answers are in.

---

## Sequencing summary

```
Prompt 2 (backend MR)
├── DB schema + seeds (Flyway)
├── Entities + IDs
├── Repositories
├── DTOs (records) + Bean Validation
├── ContactSortField (whitelist enum)
├── TrcoKeyGeneratorService
├── ContactsValidator
├── ContactsService
├── ContactsController + i18n + error codes
├── @WebMvcTest + @DataJpaTest + @SpringBootTest
└── Local smoke (./gradlew bootRun against localhost:5434)

Prompt 3 (frontend MR)
├── Wire types in lib/types/api.ts
├── Mappers in lib/api/mappers.ts
├── API client lib/api/contacts.ts
├── View Setting registry entry
├── SettingsNav config: add "Contacts" row
├── hooks/contacts/* (list, detail, mutations, selection)
├── components/settings/contacts/* (panel, table, dialog, sub-forms, badge)
├── Zod schema + RHF wiring
├── i18n keys (en/ko/vi)
├── Smoke test against local backend
└── Static-UI fallback verified (mockData.ts can stand in if backend slips)
```

Backend MR lands first; frontend MR builds against the running local backend. Both repos use Git/GitLab — no SVN.

---

## Prompt 2 — Backend MR

### Pre-flight (no code yet)

- [ ] Confirm OQ-1 — Group filter scope.
- [ ] Confirm OQ-2 — `WBE_CONTACTS_IN_USE` scope on bulk-delete vs. single-delete.
- [ ] Confirm OQ-3 — `TRCO_INQ_YN` default.
- [ ] Confirm OQ-4 — TRCO_KEY generator shape (sequence vs. composite). **Critical** — drives the schema migration.
- [ ] Confirm OQ-5 — Trash recovery in MVP yes/no.
- [ ] Confirm OQ-6 — Audit `WSVC_ID = 'contacts'` (legacy JEX consumers won't see refactor rows).
- [ ] Confirm OQ-7 — `BANK_INFM` carries both NAPAS + CITAD codes.
- [ ] Confirm OQ-8 — Address discrimination via `disp_ord` instead of legacy `TRCO_ADDR_KEY = "1"/"2"`.

### Task B1 — DEV parity audit (read-only)

- Run information-schema queries for `TRCO_INFM`, `TRCO_ADDR_INFM`, `TRCO_ACCT_INFO`, `TRCO_OVRS_INFM`, `PGRO_BSAC_LNKN_INFM` via `postgres_dev_server_jexframe` MCP per [`.claude/rules/database.md`](../../../../../.claude/rules/database.md) — columns, types, lengths, defaults, PK / UK / CHECK / FK, indexes.
- Record DEV column counts in `docs/refactor-wecms/database/contacts-schema.md` migration header.
- Confirm `wb_get_next_val('trco_infm','trco_key', …)` return shape (drives OQ-4 final decision).
- **Exit**: parity snapshot pasted into the MR description.

### Task B2 — Flyway migration V{n}__contacts_schema.sql

- File: `wecms-api/src/main/resources/db/migration/V{n}__contacts_schema.sql`.
- Use the outline in [`contacts-schema.md` § 4](../../../references/database/contacts-schema.md). Extend with every DEV column from the B1 audit.
- Use `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS` + `ALTER TABLE ADD COLUMN IF NOT EXISTS`.
- Create `sq_trco_key` (Option A) or per-tenant sequences (Option B) per OQ-4.
- Add deferrable FKs on the three child tables → `TRCO_INFM`.
- Document deferred triggers + deferred `TRCO_MDPH` FKs in the trailer comment.
- **Exit**: `./gradlew flywayMigrate` runs green on a fresh local DB; `./gradlew bootRun` starts up with `ddl-auto: validate` (no entities yet → empty validation passes).

### Task B3 — Flyway seed V{n+1}__contacts_seed.sql

- File: `wecms-api/src/main/resources/db/migration/V{n+1}__contacts_seed.sql`.
- Pull up to 30 most-recent rows from DEV via the `postgres_dev_server_jexframe` MCP, scoped to `BIZ_KEY = 'VN_VNM0004_000001'`. Order: parents first (`TRCO_INFM`), then children — avoids FK violations.
- Every insert uses `ON CONFLICT (...) DO NOTHING`.
- **Exit**: re-running `./gradlew bootRun` is idempotent; row counts match the cap.

### Task B4 — Flyway View Setting seed V{n+2}__contacts_view_setting_seed.sql

- File: `wecms-api/src/main/resources/db/migration/V{n+2}__contacts_view_setting_seed.sql`.
- 8 rows (`CHECKBOX`, 6 data columns, `ETC`) per [`contacts-schema.md` § 4 V{n+2}](../../../references/database/contacts-schema.md).
- `BANK_NM` + `TRCO_BSUN_POST_ADRS` get `SORT_YN='N'` so the whitelist+seed rule from [`wecms-api/CLAUDE.md` § Common table sort](../../../../CLAUDE.md) is satisfied.
- **Exit**: `SELECT * FROM SYS_SCR_OPTION_WECMS WHERE WEB_SVC_ID='contacts'` returns 8 rows.

### Task B5 — Entities + Composite IDs

- Files (one entity + one ID per row):
  - `domain/setting/contacts/entity/TrcoInfm.java` + `TrcoInfmId.java`
  - `domain/setting/contacts/entity/TrcoAddrInfm.java` + `TrcoAddrInfmId.java`
  - `domain/setting/contacts/entity/TrcoAcctInfo.java` + `TrcoAcctInfoId.java`
  - `domain/setting/contacts/entity/TrcoOvrsInfm.java` + `TrcoOvrsInfmId.java`
  - `domain/setting/contacts/entity/PgroBsacLnknInfm.java` + `PgroBsacLnknInfmId.java` (only if not already shared)
- Each extends [`BaseEntity`](../../../../src/main/java/com/kosign/wecms/common/entity/BaseEntity.java) for audit columns. `@IdClass` for the composite PK. `@Column(name = "UPPER_SNAKE")` per the legacy schema parity rule.
- Map only the **Sprint subset** of fields needed for read + write (not every DEV column). Document this in the entity Javadoc.
- **Exit**: `./gradlew compileJava` green; `bootRun` starts up with `ddl-auto: validate` matching the mapped columns.

### Task B6 — Repositories

- Files:
  - `domain/setting/contacts/repository/TrcoInfmRepository.java` (includes `search(...)` `@Query` per backend design § 10)
  - `domain/setting/contacts/repository/TrcoAddrInfmRepository.java`
  - `domain/setting/contacts/repository/TrcoAcctInfoRepository.java`
  - `domain/setting/contacts/repository/TrcoOvrsInfmRepository.java`
  - `domain/setting/contacts/repository/PgroBsacLnknInfmRepository.java` (if not shared)
- Extend the existing `AcctTrnsRcvPayInfmRepository` (banking domain) with `existsByBizKeyAndTrcoKey(...)`. If the repo doesn't exist yet (banking didn't need it), create it as a minimal interface in the existing banking package.
- Tenant rule: every method that returns rows constrains on `BIZ_KEY` — never a `%` wildcard.
- **Exit**: `compileJava` green; `@DataJpaTest` slices pass.

### Task B7 — DTOs (records)

- File: `domain/setting/contacts/dto/*.java` per the list in backend design § 1.
- Java fields stay camelCase. Bean-Validation annotations (`@NotBlank`, `@Size`, `@Pattern`) on each record component.
- No per-field `@JsonProperty` (global Jackson SNAKE_CASE handles the wire shape).
- **Exit**: `compileJava` green.

### Task B8 — ContactSortField

- File: `domain/setting/contacts/service/ContactSortField.java`.
- Implements `SortableField`. Lenient `parse(String)` returns `Optional.empty()` on unknown / blank.
- Constants: `HAND_TRCO_KEY`, `TRCO_CMNM`, `TRCO_TYPE`, `TRCO_TAX_ID`. `BANK_NM` and `TRCO_BSUN_POST_ADRS` deliberately omitted (matches V{n+2} `SORT_YN='N'`).
- **Exit**: enum compiles; unit test confirms `parse("UNKNOWN")` returns empty.

### Task B9 — TrcoKeyGeneratorService

- File: `domain/setting/contacts/service/TrcoKeyGeneratorService.java`.
- Implementation depends on OQ-4. Either `nextval('sq_trco_key')` (Option A) or composite `{biz_key}_{date}_{seq}` (Option B).
- No `wb_get_next_val` branch.
- **Exit**: `compileJava` green; an integration test allocates 2 keys, asserts they differ and increase monotonically.

### Task B10 — ContactsValidator

- File: `domain/setting/contacts/service/ContactsValidator.java`.
- Methods: `validateCreate(req, bizKey)`, `validateUpdate(req, existing, bizKey)`. Local helpers: `checkAccountDuplicates`, `checkOverseasDuplicates`, `hasAtLeastOneUpsertAccount`.
- Throws `BusinessException(code)` with the new `WBE_CONTACTS_*` codes (added in B12).
- **Exit**: `compileJava` green; unit tests cover the duplicate paths.

### Task B11 — ContactsService

- File: `domain/setting/contacts/service/ContactsService.java`.
- Public methods per backend design § 4. Each annotated `@Transactional` / `@Transactional(readOnly = true)`.
- `list(...)` batches sub-rows for the page (one extra query per child table — not N+1 per row).
- `update(...)` follows the discriminated-op dispatch (upsert vs. delete).
- `softDelete(...)` runs the `WBE_CONTACTS_IN_USE` guard via `AcctTrnsRcvPayInfmRepository`.
- Uses `BankCdResolver` for bank-name resolution.
- **Exit**: `compileJava` green; `@DataJpaTest` covers repository derivation; `@SpringBootTest` covers the single-tx update flow end-to-end.

### Task B12 — Error codes

- Append the 7 new codes from backend design § 11 to:
  - `wecms-api/src/main/resources/messages.properties`
  - `wecms-api/src/main/resources/messages_en.properties`
  - `wecms-api/src/main/resources/messages_ko.properties` (verified KO translations)
  - `wecms-api/src/main/resources/messages_vi.properties` (verified VI translations)
- KM is omitted (workspace policy).
- **Exit**: `bootRun` resolves a sample code (`MessageResolver.resolve("WBE_CONTACTS_NOT_FOUND")`).

### Task B13 — ContactsController

- File: `domain/setting/contacts/controller/ContactsController.java`.
- 6 endpoints per backend design § 8. Thin: parse request, delegate, wrap with `ApiResponse.ok(...)`.
- `@Validated` at class level to enable Bean-Validation on path / query params.
- **Exit**: `bootRun` green; `curl -H "Authorization: Bearer …" http://localhost:8080/api/v1/settings/contacts?page=1&size=15` returns the seeded list.

### Task B14 — Tests

- Files:
  - `domain/setting/contacts/ContactsControllerTest.java` (`@WebMvcTest`) — happy paths + each error envelope.
  - `domain/setting/contacts/repository/TrcoInfmRepositoryTest.java` (`@DataJpaTest`) — `search(...)` `@Query` coverage.
  - `domain/setting/contacts/service/ContactsServiceIT.java` (`@SpringBootTest(test profile)`) — end-to-end update with addresses + accounts + overseas + deletes; in-use guard; tenant scoping.
- **Exit**: `./gradlew test` green.

### Task B15 — Local smoke

- Run `./run-local.bat` (or `./gradlew bootRun --args='--spring.profiles.active=local'`).
- Use the API test page or `curl` / Postman:
  - `POST /api/v1/auth/login` to get a JWT for `testuser` / `Password!1`.
  - `GET /api/v1/settings/contacts?page=1&size=15` — returns ≥ 1 row from the seed.
  - `GET /api/v1/settings/contacts/{trcoKey}` — returns nested shape.
  - `POST /api/v1/settings/contacts` — creates a new contact (Local + at least one account).
  - `PUT /api/v1/settings/contacts/{trcoKey}` — edits and adds an `accounts_to_delete[]`.
  - `DELETE /api/v1/settings/contacts/{trcoKey}` — soft-deletes; verify `TRCO_STTS='C'`.
  - `POST /api/v1/settings/contacts/bulk-delete` — bulk soft-delete.
- Verify the `AUDIT_LOGS_INFM` table gains one row per write with `WSVC_ID='contacts'`.
- **Exit**: every endpoint round-trips; envelope shape matches the spec; trace_id in response header matches the audit row.

### Task B16 — Update CLAUDE.md

- Append a row to the "Feature Status" table in [`wecms-api/CLAUDE.md`](../../../../CLAUDE.md) for `domain/setting/contacts`.
- Add the new Flyway versions to the migrations table in the same file.
- Mention any new repository methods added to `AcctTrnsRcvPayInfmRepository`.
- **Exit**: documentation reflects the merged state.

---

## Prompt 3 — Frontend MR

### Pre-flight

- Backend MR is merged + running at `http://localhost:8080`.
- All `WBE_CONTACTS_*` codes resolve in `messages_en.properties` (verified manually).
- The View Setting seed is in place: `SELECT * FROM SYS_SCR_OPTION_WECMS WHERE WEB_SVC_ID='contacts'` returns 8 rows.

### Task F1 — Wire types

- File: `wecms-web/lib/types/api.ts` — add `ApiContact`, `ApiAddress`, `ApiAccount`, `ApiOverseas`, `ApiContactListResponse`, `ApiContactCreateRequest`, `ApiContactUpdateRequest`, plus the discriminated sub-records.
- Lowercase snake_case property names — matches the wire from day one.
- **Exit**: `tsc --noEmit` green.

### Task F2 — Mappers

- File: `wecms-web/lib/api/mappers.ts` — append `toContact`, `toAddress`, `toAccount`, `toOverseas` (wire → in-memory) and `toApiContactCreate` + `toApiContactUpdate` (in-memory → wire).
- **Exit**: `tsc --noEmit` green; a manual lint of the diff confirms no `response.data.user_id`-style direct snake_case reads anywhere outside this file.

### Task F3 — API client

- File: `wecms-web/lib/api/contacts.ts` — 6 exported functions per frontend design § 8. Each translates errors into `ApiCallError` with `{ code, message, traceId }`.
- **Exit**: `tsc --noEmit` green; a smoke call from the browser dev-tools console hits the backend.

### Task F4 — View Setting registry

- File: `wecms-web/lib/viewSettingScreens.ts` — add `"contacts"` entry per frontend design § 15.
- **Exit**: the shared `<ViewSettingPopover>` opens with screenId `contacts` and renders the 6 user-visible columns (CHECKBOX and ETC hidden by the popover per the special-FIELD_ID rule).

### Task F5 — SettingsNav entry

- File: `wecms-web/components/settings/SettingsNav.tsx` — append one entry to the **top group** `items` array with icon + label key + onClick → opens `<ContactsPanel />`.
- **Exit**: opening Settings shows "Contacts" in the sidebar; clicking it mounts an empty `<ContactsPanel />`.

### Task F6 — Hooks

- Files:
  - `wecms-web/hooks/contacts/useContacts.ts` — list hook over `useApiData`.
  - `wecms-web/hooks/contacts/useContactDetail.ts` — single record.
  - `wecms-web/hooks/contacts/useContactMutations.ts` — create / update / delete / bulk-delete.
  - `wecms-web/hooks/contacts/useContactsSelection.ts` — thin wrapper over the shared `useSelection`.
- **Exit**: `tsc --noEmit` green; hooks importable from the panel.

### Task F7 — Types + Zod schema

- Files:
  - `wecms-web/components/settings/contacts/types.ts` — camelCase in-memory types per frontend design § 5.
  - `wecms-web/components/settings/contacts/contactSchema.ts` — Zod schema with translation-key messages per frontend design § 10.
- **Exit**: `tsc --noEmit` green.

### Task F8 — ContactsPanel + table + row + badge

- Files:
  - `wecms-web/components/settings/contacts/ContactsPanel.tsx` — Settings panel shape per [`wecms-web/CLAUDE.md` § 3a](../../../../../wecms-web/CLAUDE.md).
  - `wecms-web/components/settings/contacts/ContactsTable.tsx`
  - `wecms-web/components/settings/contacts/ContactsTableRow.tsx`
  - `wecms-web/components/settings/contacts/RowActionMenu.tsx`
  - `wecms-web/components/settings/contacts/TrcoTypeBadge.tsx`
- Reuse: `<DataTableShell>`, `<TableHeaderCell>`, `<SearchInput>`, `<FilterDropdown>`, `<Pagination>` + `usePagination`, `useDialog()` for delete confirmation.
- Typography tokens: `text-page-title`, `text-wb-13`, `text-section-title`. No arbitrary `text-[Npx]`.
- **Exit**: panel renders with live data; sort glyph cycles; selection works; pagination works; filter chips reset to page 1.

### Task F9 — ContactFormDialog + sub-forms

- Files:
  - `wecms-web/components/settings/contacts/ContactFormDialog.tsx` — shadcn `<Dialog>` with `stackLevel={2}`, standard rhythm.
  - `wecms-web/components/settings/contacts/AddressSubForm.tsx`
  - `wecms-web/components/settings/contacts/AccountSubForm.tsx` — visible when `contactType='L'`.
  - `wecms-web/components/settings/contacts/OverseasSubForm.tsx` — visible when `contactType='O'`.
- RHF + Zod via `@hookform/resolvers`. Conditional mount + `key={editing.trcoKey}` re-seed.
- Cancel discards local state; Save calls `createContact` / `updateContact` and closes on success.
- **Exit**: create + edit + cancel flows work; client-side validation surfaces translation-key messages; backend duplicate errors render via `useDialog()` error variant.

### Task F10 — i18n

- Files:
  - `wecms-web/locales/en.json` — append the `settings.contacts.*` block per frontend design § 16.
  - `wecms-web/locales/ko.json` — verified KO translations.
  - `wecms-web/locales/vi.json` — verified VI translations.
- KM omitted.
- **Exit**: switching language re-renders the Contacts panel with the new locale; missing-key fallback never appears.

### Task F11 — Smoke test (UI)

- Per [`wecms-web/CLAUDE.md` § Doing tasks](../../../../../wecms-web/CLAUDE.md): start the dev server and walk the feature in a browser.
- `npm run dev` against the running local backend.
- Open Settings → Contacts. Verify:
  - 30 rows render with the View Setting columns.
  - Search filters down on company name.
  - Local / Overseas chips toggle.
  - Sort glyph cycles `default → ASC → DESC → default`.
  - Pagination flips pages and resets correctly on filter change.
  - Selecting rows shows the bulk-delete action; deleting bulk soft-deletes and refreshes.
  - "+ Add" opens the dialog; submitting a valid Local contact persists.
  - Edit dialog pre-fills correctly; adding an account then editing existing addresses then saving lands all changes in one tx.
  - Delete confirmation routes through `useDialog()` confirmation; error path renders the WBE code + traceId.
- **Exit**: every flow above works without console errors.

### Task F12 — Update CLAUDE.md

- Append the Contacts route to the "Feature Status" table in [`wecms-web/CLAUDE.md`](../../../../../wecms-web/CLAUDE.md). It is a Settings panel, not a top-level route — note that explicitly.
- Add `Settings > Contacts` to the Settings submenu list referenced in §3.
- **Exit**: documentation reflects the merged state.

---

## Reuse cheatsheet (cross-stack)

The full reuse manifests live in the backend and frontend design docs (§ 15 of each). The summary:

### Backend reuse (existing in `wecms-api/`)

- `common/response/ApiResponse` — envelope.
- `common/exception/BusinessException` + `GlobalExceptionHandler` — error funnel.
- `common/entity/BaseEntity` — audit columns + `@PrePersist`/`@PreUpdate`.
- `common/tenant/TenantContext` — current `BIZ_KEY` + `userId`.
- `common/i18n/MessageResolver` + `messages*.properties`.
- `common/web/SortDirection` + `SortableField`.
- `domain/setting/` View Setting (seed-only adopter).
- `common/security/JwtAuthenticationFilter` + `SecurityConfig` (no security wiring change).
- `BankCdResolver` + `BANK_INFM` registry (V20 seed) — replaces legacy `wb_get_napas_bank_nm_or_citad_bank_nm`.
- `TraceIdFilter` — already wired.

### Frontend reuse (existing in `wecms-web/`)

- `<SettingsModal>` + `<SettingsNav>` — Settings shell + sidebar.
- `<DataTableShell>` + `<TableHeaderCell>` — list shell + sort glyph.
- shadcn `<Table>` + `<Dialog>` — primitives.
- `<SearchInput>`, `<FilterDropdown>`, `<Pagination>` + `usePagination` — filter row.
- `useDialog()` system dialog — confirmation + success + error.
- `useApiData` — data-fetch hook.
- `lib/axios.ts` `api` — HTTP client.
- `lib/api/mappers.ts` (existing namespace) — wire↔in-memory translation.
- `lib/i18n/LocaleProvider` + `useTranslation()`.
- `<ViewSettingPopover>` — driven by `lib/viewSettingScreens.ts`.
- Form: RHF + Zod via `@hookform/resolvers`.

### New code introduced (do not exist today)

Backend:
- 4 entities (TrcoInfm/AddrInfm/AcctInfo/OvrsInfm) + 4 composite IDs + 1 maybe-new (PgroBsacLnknInfm).
- 5 repositories + 1 derived method on the existing `AcctTrnsRcvPayInfmRepository`.
- ~10 DTO records.
- `ContactsService`, `ContactsValidator`, `ContactSortField`, `TrcoKeyGeneratorService`.
- `ContactsController`.
- 3 Flyway migrations.

Frontend:
- 1 feature folder (`components/settings/contacts/`) with ~8 React files.
- 1 hook folder (`hooks/contacts/`) with 4 hooks.
- 1 API client file (`lib/api/contacts.ts`).
- Additions to `lib/types/api.ts`, `lib/api/mappers.ts`, `lib/viewSettingScreens.ts`, `locales/{en,ko,vi}.json`.
- 1 sidebar entry in `<SettingsNav>` config.

### Strictly forbidden (caught at review)

- Any new `@JsonProperty("UPPER_SNAKE")` on a Contacts DTO.
- Any new branch added to `wb_get_next_val(...)`.
- Any new `CREATE FUNCTION` in a Contacts migration.
- Any direct read of `response.data.trco_key` in a component (must go through the mapper).
- Any per-page scrollbar override; any inline `text-[Npx]` / `font-[N]`; any non-Inter / non-Noto-KR font family.
- Any new endpoint under `/api/v1/comm_0184_*` or a Spring package `com.kosign.wecms.domain.comm.comm_0184` — legacy WSVC ids are forbidden in refactor identifiers (workspace [`CLAUDE.md`](../../../../../CLAUDE.md) § Cross-Tree Rules).
- Editing anything under `WECMS DEV/`, `SOURCE_CODE_ADMIN/`, `SOURCED_OSMC/`, or `DEV ADMIN/`.

---

## Final acceptance checklist

Before Sonny approves the merged feature:

- [ ] Backend MR + frontend MR both merged on `main` (GitLab).
- [ ] `./gradlew build` green; `npm run build` green.
- [ ] DEV parity audit attached to the backend MR description.
- [ ] All 8 OQs answered with the decisions reflected in the code + docs.
- [ ] Audit rows in `AUDIT_LOGS_INFM` carry `WSVC_ID='contacts'`.
- [ ] Manual UI walk-through covers: list, search, sort, paginate, add, edit (incl. nested upsert + nested delete in one tx), single delete, bulk delete, in-use guard error, duplicate hand-key error, local-bank-required error.
- [ ] `tsc --noEmit` + `npm run lint` green.
- [ ] No legacy `comm_0184_*` identifier appears anywhere in refactor identifiers (REST URIs, Java packages, DTOs, i18n keys, View Setting screenIds, audit `WSVC_ID`, log tags). `grep -r "comm_0184" wecms-api/src wecms-web/{app,components,lib,hooks,locales}` returns zero matches outside Javadoc/migration comments.
