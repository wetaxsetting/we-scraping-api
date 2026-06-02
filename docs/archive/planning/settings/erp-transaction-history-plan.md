# ERP Transaction History — Phased Implementation Plan

**Refactor slug:** `erp-transaction-history`
**Owner:** Sonny (frontend) + Arthur (backend). Mr. Youvita runs the testing phase.
**Sibling docs:** [feature spec](../../../features/settings/transaction-history.md) · [migration map](../../../references/migration/erp-transaction-history-mapping.md) · [DB schema](../../../references/database/settings/erp-transaction-history-schema.md) · [backend design](../../../references/business-rules/settings/erp-transaction-history.md) · [frontend design](../../../../../wecms-web/docs/features/settings/transaction-history.md).
**Status:** **Phase 1 — design only.** Implementation pending kickoff.
**Sprint slot:** Settings module — fits inside the existing 8-dev-day plan ([docs/jex/features/wecms-menu-refactoring-timeline.md](../../../../../docs/jex/features/wecms-menu-refactoring-timeline.md) line 764).

This plan is the work-order for taking the feature from "design complete"
to "Phase 2 backend wired + Phase 3 frontend wired". It also captures the
risks and open questions blocking each phase.

---

## 0. Pre-flight (Day 0)

| Step | Owner | Output |
|---|---|---|
| Review the four design docs (feature / migration / backend / frontend) in this folder | All | Sign-off in MR description before code lands |
| Re-run the schema-parity audit on `erp_trns_hstr` via the `postgres` MCP — confirm DEV column types match the entity design in [backend doc §2](../../../references/business-rules/settings/erp-transaction-history.md). | Arthur | Audit row in [docs/refactor-wecms/audits/](../../../references/audits/) if drift is found |
| ~~Inspect `SortHelper` for the legacy default sort~~ — **Resolved 2026-05-11**: refactor uses `regDtm DESC, seqNo DESC`. Future SortHelper inspection may adjust this with no contract change. | — | Already locked in backend doc §4 |
| ~~Confirm OQ-1 with PO (Mr. Jung)~~ — **Resolved 2026-05-11**: refactor does not port the upload action; each future Payment feature writes its own audit row | — | Already recorded in feature spec §9 |
| ~~Confirm OQ-2 with PO~~ — **Resolved 2026-05-11**: refactor is product-agnostic; no `prdt_type` on the wire; no `StoredUser.prdtType`; HAENGSUNG variants intentionally not mirrored | — | Already recorded in feature spec §9 |
| ~~Confirm OQ-FE-1 with the team~~ — **Resolved 2026-05-11**: download via `lib/file.ts` `downloadFile(url, filename)` (axios + blob + synthetic anchor click; `?token=` query rejected for log-leak risk) | — | Already locked in frontend doc §6 / §19.5 / §22 |
| ~~Confirm OQ-FE-2 with PO~~ — **Resolved 2026-05-11**: panel does not adopt View Setting (legacy JEX has no list-icon button); no popover, no `SYS_SCR_OPTION_WECMS` seed, no `SCREEN_IDS` registry entry | — | Already locked in frontend doc §9 and §22 |

Day-0 unblocks every downstream step. No code commits in this window.

---

## 1. Phase 2 — Backend wiring (estimated 1.5 dev-days)

Dependency: Day 0 sign-off complete.

### 1.1 Day 1 — Entity / repository / service skeleton

| Task | Owner | Notes |
|---|---|---|
| Create `domain/setting/erptransactionhistory/` package + entity (`ErpTrnsHstr` + `ErpTrnsHstrId`) | Arthur | per [backend doc §2](../../../references/business-rules/settings/erp-transaction-history.md) |
| Create repository with `search()` + `countAllDirections()` JPQL | Arthur | per [backend doc §3](../../../references/business-rules/settings/erp-transaction-history.md) |
| Create `ErpTrnsHistorySortField` enum | Arthur | per [backend doc §5](../../../references/business-rules/settings/erp-transaction-history.md) |
| Create DTOs (`Request` / `Row` / `Response` + Bean Validation) | Arthur | per [backend doc §6](../../../references/business-rules/settings/erp-transaction-history.md) |
| Add `WBE2014` / `WBE2015` / `WBE9001` + `settings.erp_transaction_history.*` keys to `messages.properties` (EN only initially) | Arthur | per [backend doc §9](../../../references/business-rules/settings/erp-transaction-history.md) |
| Add `wecms.erp-transaction-history` config + `ErpTrnsHistoryFileResolver` | Arthur | per [backend doc §10](../../../references/business-rules/settings/erp-transaction-history.md) |
| Flyway: schema-parity migration (additive) if Day-0 audit found drift | Arthur | header documents column delta |
| Flyway: seed `erp_trns_hstr` rows for tenant `VN_VNM0004_000001` (≤30 latest per direction `W` / `E` / `D` = up to 90 rows) | Arthur | `INSERT ... ON CONFLICT DO NOTHING` |
| ~~Flyway: seed `SYS_SCR_OPTION_WECMS` rows for the slug~~ — **dropped per OQ-FE-2.** Panel does not adopt View Setting; no seed. | — | per [migration doc §7](../../../references/migration/erp-transaction-history-mapping.md) — entire View Setting section now marked NOT ADOPTED |
| Fixture files committed under `src/test/resources/erp-transaction-history/fixtures/` | Arthur | small text files matching the seeded `save_file_nm` for ≤5 rows |

### 1.2 Day 2 — Service + controller + tests

| Task | Owner | Notes |
|---|---|---|
| Implement `ErpTrnsHistoryService#search` (validation, like-escape, pageable, default sort) | Arthur | mirrors `FirmBankingHistoryService` |
| Implement `ErpTrnsHistoryService#resolveDownload` + `#resolveThumbnail` (501 stub) | Arthur | |
| Implement `ErpTrnsHistoryController` (list + download + thumbnail) | Arthur | snake_case `@RequestParam` names per [wecms-api/CLAUDE.md §"API Naming"](../../../../CLAUDE.md) |
| Write `ErpTrnsHistoryServiceTest` (Mockito) | Arthur | per [backend doc §11](../../../references/business-rules/settings/erp-transaction-history.md) |
| Write `ErpTrnsHistoryControllerTest` (standalone MockMvc) | Arthur | snake_case wire shape + error envelope coverage |
| Run `./gradlew build` (compile + unit tests + Hibernate `validate`) | Arthur | gate to MR |
| Manual smoke against the local DB via curl / Bruno — verify all 7 manual smoke steps in [backend doc §11](../../../references/business-rules/settings/erp-transaction-history.md) | Arthur | screenshot the audit row showing `wsvc_id='erp-transaction-history'` |
| Open MR — wire backend + Flyway seeds + tests in one atomic change | Arthur | reviewer: Sonny |

**Phase 2 exit gate:**
- `GET /api/v1/settings/erp-transaction-history?from_dt=...&to_dt=...` returns the envelope with `list[]` + 4 `total_count_*` keys.
- `GET .../{seqNo}/download` streams a real file for at least one seeded row.
- Hibernate `validate` passes (no schema drift).
- All tests green; `./gradlew build` succeeds.
- Audit log shows the slug `erp-transaction-history` in `WSVC_ID`.
- No `SYS_SCR_OPTION_WECMS` rows seeded for the slug (View Setting NOT ADOPTED — OQ-FE-2).

---

## 2. Contract freeze (between Phase 2 and Phase 3)

| Step | Output |
|---|---|
| Pin the wire shape — Sonny + Arthur sign off on `ApiErpTransactionHistoryRow` + `ApiErpTransactionHistoryListResponse` exactly as Phase 2 ships. | No schema change in Phase 3 unless a bug surfaces. |
| Pin the URL surface — `/api/v1/settings/erp-transaction-history` + `/{seqNo}/download` + `/{seqNo}/thumbnail`. | Frontend can hard-code without future churn. |
| Pin the error code surface — `WBE2014` / `WBE2015` / `WBE_VALIDATION` / `WBE9001`. | Frontend dialog handling can be exhaustive. |
| Pin the seeded screenId and FIELD_IDs — `erp-transaction-history` + `default` + 5 FIELD_IDs (`REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `FILE`, `REG_USER`). | View Setting registry edit can land without backend churn. |

A 1-hour sync between Sonny + Arthur to walk through the contract is enough; the design docs already lock everything except the answers to the open questions.

---

## 3. Phase 3 — Frontend wiring (estimated 1 dev-day)

Dependency: Phase 2 deployed locally + contract frozen.

### 3.1 Static-UI cleanup

| Task | Owner | Notes |
|---|---|---|
| Rename `components/settings/transaction-history/` → `components/settings/erp-transaction-history/`; rename inner components, mock files, types | Sonny | per [frontend doc §1](../../../../../wecms-web/docs/features/settings/transaction-history.md) |
| Update `SettingsNav` to import the new panel name | Sonny | atomic with the rename |
| Move locale keys `settings.transaction_history.*` → `settings.erp_transaction_history.*` in `locales/{en,ko,vi}.json` | Sonny | delete the old namespace |
| Add new locale keys (full list in [migration doc §8](../../../references/migration/erp-transaction-history-mapping.md)) | Sonny | EN verified; KO / VI placeholders or omitted |

### 3.2 API plumbing

| Task | Owner | Notes |
|---|---|---|
| Add `ApiErpTransactionHistoryRow` + `ApiErpTransactionHistoryListResponse` to `lib/types/api.ts` | Sonny | snake_case wire shape per [frontend doc §5](../../../../../wecms-web/docs/features/settings/transaction-history.md) |
| Add `lib/api/erpTransactionHistory.ts` (`fetchErpTransactionHistory` + `getErpTransactionHistoryDownloadUrl`) | Sonny | per [frontend doc §6](../../../../../wecms-web/docs/features/settings/transaction-history.md) |
| Add `mapErpTransactionHistoryRow` to `lib/api/mappers.ts` | Sonny | per [frontend doc §7](../../../../../wecms-web/docs/features/settings/transaction-history.md) |
| Add `lib/file.ts` `downloadFile(url, filename)` — axios + blob + synthetic anchor click. Reads filename from `Content-Disposition` (preferred) or the caller-supplied fallback. Throws `ApiCallError` on JSON-envelope failures. | Sonny | First consumer = ERP transaction history File cell; future consumers = Payment file downloads, Smart Organizer attachments. Per OQ-FE-1 resolution. |
| ~~Add `prdtType` to `StoredUser` + auth wire types~~ — dropped per OQ-2 (refactor is product-agnostic) | — | No auth change |
| ~~Add `SCREEN_IDS.ERP_TRANSACTION_HISTORY` to `lib/viewSettingScreens.ts`~~ — **dropped per OQ-FE-2.** Panel does not adopt View Setting. | — | |

### 3.3 Panel rewrite

| Task | Owner | Notes |
|---|---|---|
| Flip `<ErpTransactionHistoryPanel>` from mock data to `useApiData` + `fetchErpTransactionHistory` + `mapErpTransactionHistoryRow` | Sonny | per [frontend doc §15](../../../../../wecms-web/docs/features/settings/transaction-history.md) |
| Replace the static date range with the shared `<InteractiveDateRangePicker>` (last-30-days default) | Sonny | mirrors `<FirmBankingHistoryPanel>` |
| Wire the universal Deleted-tab transaction-type subset filter (the only legacy per-tab filter preserved — applies to every tenant per OQ-2) | Sonny | per [frontend doc §11](../../../../../wecms-web/docs/features/settings/transaction-history.md) |
| Wire `useTableSort()` + `<TableHeaderCell sortable=...>` for the 4 sortable columns | Sonny | per [frontend doc §10](../../../../../wecms-web/docs/features/settings/transaction-history.md) |
| ~~Wire `<ViewSettingPopover>`~~ — **dropped per OQ-FE-2.** Panel does not adopt View Setting. | — | per [frontend doc §9](../../../../../wecms-web/docs/features/settings/transaction-history.md) — section now marked NOT ADOPTED |
| Wire the File column download via `lib/file.ts` | Sonny | |
| Switch error surface to `useDialog().open({ type: "error", errorCode, description })` | Sonny | global pattern |
| Verify all 4 breakpoints (375 / 768 / 1024 / 1440) | Sonny | per [wecms-web/CLAUDE.md §"Responsive rules"](../../../../../wecms-web/CLAUDE.md) |
| `npm run lint` + `npm run build` + `npm run test` | Sonny | gate to MR |
| Open MR — rename + plumbing + panel rewrite | Sonny | reviewer: Arthur |

**Phase 3 exit gate:**
- Settings modal → "ERP Transaction History" — panel renders live rows from the local API.
- Tabs / type dropdown / keyword / date range all wired to the backend; counts update on every filter change.
- HAENGSUNG behaviour verified: hide Deleted tab + hide File column on E tab + filter the type dropdown per tab.
- File download streams a real file for at least one seeded row.
- No TypeScript errors; `npm run build` succeeds; all 4 breakpoints render correctly.
- Slug renames complete — no `transaction_history` namespace remaining in `components/settings/` / `locales/`.

---

## 4. Test plan (Phase 4 — Mr. Youvita's 2-day window)

The shared testing window happens at the end of the sprint group. Specific to ERP Transaction History:

| Scenario | Steps | Expected |
|---|---|---|
| Initial mount | Open Settings → ERP Transaction History | Panel renders with last-30-days range, "All" tab active, type "All Transaction Type", non-empty rows from the seed |
| Tab switch — WeCMS → ERP | Click `WeCMS → ERP` | Rows narrow to `ERP_TYPE='W'`; counts unchanged; type dropdown stays unfiltered (all 17 codes visible — refactor is product-agnostic per OQ-2) |
| Tab switch — Deleted | Click `Deleted` | Deleted tab is always visible (no `prdtType` hide); rows = `ERP_TYPE='D'`; type dropdown shows only scheduled-payment subset (`""`, `02`, `03`, `12`, `13`, `16`) — universal legacy filter |
| Date range — Last 90 days | Click Last 90 Days preset | New fetch fires; counts adjust; page resets to 1 |
| Date range — Manual | Click Enter Manually; pick start + end; Apply | Same as preset behaviour |
| Type filter | Pick "Vendor Scheduled Payment" (`02`) | Rows narrow to `TRNS_TYPE_CD='02'`; counts respect the filter (so `total_count_*` numbers update) |
| Keyword search | Type `lamine` | Debounced 400 ms; rows narrow to matching `reg_id`; page resets |
| Sort REG_DTM ASC | Click Date header twice | Cycle goes `default → ASC`; rows reorder server-side |
| Sort REG_DTM default | Click Date header again (3rd click) | Cycle to default; rows return to default order |
| Pagination | Set page size to 50; navigate to page 2 | New fetch fires; `page_size=50&page=2` on the wire |
| File download (FILE_SIZE>0) | Click an entry under File column for a seeded fixture row | Browser saves the file with the seeded name |
| File download (FILE_SIZE=0) | Confirm the File cell renders empty for batch rows | No anchor; no `<a>` element in the DOM |
| Error — invalid date | Manually craft `from_dt = 19990101, to_dt = 20990101` via devtools | `WBE_VALIDATION` dialog opens with the error message |
| Error — file missing | Delete the fixture file from `storage-root`; try to download | `WBE2015` dialog opens |
| Session expiry | Manually expire the token | Global re-auth dialog fires; no panel-level error |
| Responsive — 375 px | Open the modal on mobile-sized viewport | Modal goes full-screen; filter row stacks; table scrolls horizontally |
| Responsive — 1440 px | Open the modal on desktop | Full layout per Figma; no horizontal scroll on the table |
| Audit row | After listing once, query `audit_logs_infm` via the postgres MCP | One row with `wsvc_id='erp-transaction-history'`, `user_id='lamine@gmail.com'` (or test user), `wb_status='S'` |

Mr. Youvita generates the test cases above into the team's test tracker and runs them on each breakpoint.

---

## 5. Review checklist (open MR — both Phase 2 + Phase 3)

Before requesting review:

- [ ] No file touched under `WECMS DEV/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/`, `DEV ADMIN/` (cross-tree write boundary)
- [ ] Every new identifier surface uses the kebab-case slug `erp-transaction-history` — no `comm_0219_001` in Java packages, REST URIs, View Setting `WEB_SVC_ID`, log tags, or audit `WSVC_ID` values
- [ ] Wire JSON is lowercase snake_case throughout; Jackson `SNAKE_CASE` strategy carries the conversion; no per-field `@JsonProperty` other than rare divergences
- [ ] Validation throws `BusinessException("WBExxxx")` for every known error path; new codes (`WBE2014`, `WBE2015`, `WBE9001`) are in `messages.properties`
- [ ] Flyway migrations are additive and idempotent (`INSERT ... ON CONFLICT DO NOTHING`); seed limited to tenant `VN_VNM0004_000001` and ≤30 rows per direction
- [ ] Hibernate `validate` passes — entity matches the local schema column-for-column
- [ ] Audit log writes `wsvc_id='erp-transaction-history'` for list and `erp-transaction-history/download` for downloads
- [ ] Frontend wire types in `lib/types/api.ts` carry snake_case property names; in-memory types stay camelCase semantic; translation lives in `lib/api/mappers.ts`
- [ ] No `text-[Npx]` / `font-[N]` / inline `style={{ fontFamily }}` in any new component
- [ ] Every visible string is behind `t(...)` and exists in all three (or fallback EN) locale files
- [ ] `npm run lint` + `npm run build` + `./gradlew build` all green
- [ ] Manual smoke covered (backend §11 + frontend Phase 3 exit gate)
- [ ] PR description quotes the four design docs and links the JEX file:line citations for any non-obvious behaviour
- [ ] Memory write — none required (this is implementation work, not project-decision state)

---

## 6. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| ~~OQ-2 (auth `prdt_type`)~~ | — | — | ✅ Resolved 2026-05-11 — refactor is product-agnostic; risk closed |
| OQ-7 — future SortHelper inspection finds a different legacy default | Low | Low | Refactor locks `regDtm DESC, seqNo DESC` as the design contract (resolved 2026-05-11). Any divergence is fixed in a single backend MR with no wire-contract change. |
| Schema-parity drift on `erp_trns_hstr` | Low | Low | Day-0 audit catches it; ship additive migration in Phase 2 |
| `wb_get_user_nm` cross-tenant collision | Low | Medium | LEFT JOIN `USER_INFM` mirrors legacy behaviour; if a collision is observed in QA, tighten the join with a `BIZ_KEY` predicate |
| Fixture file storage path differs from production NAS layout | Low | Low | Refactor uses `C:/WECMS PROJECT/File Data/<biz_key>/erp-transaction-history/<save_file_nm>` (slug, resolved 2026-05-11); production stays on `<NAS_ROOT>/<biz_key>/comm_0219_001_c001/<save_file_nm>` — clearly documented in feature spec §9 OQ-3 |
| Developer machine doesn't have `C:/WECMS PROJECT/File Data` (e.g. POSIX laptop, different drive) | Low | Low | Override via env var `WECMS_ERP_TRNS_HSTR_STORAGE_ROOT`; documented in backend doc §10 |
| ~~Download flow auth (OQ-FE-1)~~ | — | — | ✅ Resolved 2026-05-11 — `lib/file.ts` `downloadFile(url, filename)` lands in the same MR; risk closed |
| KO / VI translations not yet verified | Medium | Low | Omit unverified locale entries per the project's "no-invented-translations" rule; EN fallback covers gaps |

---

## 7. Open questions summary (deferred decisions)

All open questions are tracked in the design docs they affect; this section is the index.

| ID | Topic | Where it's tracked | Blocking? |
|---|---|---|---|
| OQ-1 | Refactor scope of `comm_0219_001_c001` upload action | [Feature spec §9](../../../features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — do not port; each future Payment feature writes its own audit row |
| OQ-2 | `prdtType` source from auth wire | [Feature spec §9](../../../features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — refactor is product-agnostic; no auth wire change |
| OQ-3 | NAS root configuration on refactor side | [Feature spec §9](../../../features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — `wecms.erp-transaction-history.storage-root="C:/WECMS PROJECT/File Data"` |
| OQ-4 | `wb_get_user_nm` parity strategy | [Feature spec §9](../../../features/settings/transaction-history.md) | Phase 2 — addressed via LEFT JOIN |
| OQ-5 | FILE column sortability | [Feature spec §9](../../../features/settings/transaction-history.md) | Resolved — `SORT_YN='N'` in the seed |
| OQ-6 | HAENGSUNG-only `TRNS_TYPE_CD` filter | [Feature spec §9](../../../features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — folded into OQ-2; all 17 codes visible to every tenant |
| OQ-7 | Legacy `SortHelper` default sort | [Feature spec §9](../../../features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — `regDtm DESC, seqNo DESC` |
| OQ-8 | Soft-delete write path | [Feature spec §9](../../../features/settings/transaction-history.md) | Per-Payment-feature scope — out of this plan |
| OQ-9 | `PageInfo` ↔ `Pageable` conversion | [Feature spec §9](../../../features/settings/transaction-history.md) | Resolved — service layer handles 1-based ↔ 0-based |
| OQ-FE-1 | Download flow auth (`<a>` vs. XHR+blob) | [Frontend doc §22](../../../../../wecms-web/docs/features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — XHR+blob via `lib/file.ts` `downloadFile(url, filename)` |
| OQ-FE-2 | View Setting list-icon button | [Frontend doc §22](../../../../../wecms-web/docs/features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — not adopted; legacy JEX has no equivalent button |
| OQ-FE-3 | `prdtType` source from auth wire (= OQ-2) | [Frontend doc §22](../../../../../wecms-web/docs/features/settings/transaction-history.md) | ✅ Resolved 2026-05-11 — folded into OQ-2 |

---

## 8. Changelog

- 2026-05-11 — v0.1 — initial plan (Sokny, AI-assisted).
