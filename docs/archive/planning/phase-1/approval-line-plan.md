# Approval Line — Implementation Plan

| Field | Value |
|---|---|
| Slug | `approval-line` |
| Feature spec | [docs/refactor-wecms/features/approval/approval-line.md](../../../features/settings/approval-line.md) |
| Migration mapping | [docs/refactor-wecms/migration/approval-line.md](../../../references/migration/approval-line.md) |
| Backend design | [docs/refactor-wecms/backend/approval-line.md](../../../references/business-rules/approval-line.md) |
| Frontend design | [docs/refactor-wecms/frontend/approval-line.md](../../../../../wecms-web/docs/features/settings/approval-line.md) |
| Team | Sonny (lead), Arthur (dev), Mr. Youvita (AI-assisted tester), Mr. Jung (PO) |

This document is the step-by-step Phase 2 task list. Tasks are grouped Backend → Frontend → Integration and ordered by dependency. Acceptance criteria are verification targets — every item is a green check at MR review.

---

## 0. Prerequisites (must clear before Phase 2 starts)

| # | Task | Owner | Done when |
|---|---|---|---|
| P-1 | PO sign-off on the 7 OQs in [feature § 14](../../../features/settings/approval-line.md). Some are flagged as "proposed resolved" in this MR — they need explicit Mr. Jung confirmation before they're load-bearing for review. | Sonny → Mr. Jung | Decisions captured in the feature doc OQs (struck through with date + decision text). |
| P-2 | Create the missing JEX-side spec at `docs/jex/features/approval/approval-line.md` (OQ-2). One-page reverse engineering of the `comm_0222_*` services — purely from the live source. | Claude Code or Sonny | File exists; matches the slug; cites the same JEX file:line citations the refactor spec already cites. |
| P-3 | Decide whether the tenant-users endpoint (`GET /api/v1/users`, see [backend § 8](../../../references/business-rules/approval-line.md)) ships in this MR, in a sibling MR before this one, or is deferred (static `APPROVER_POOL` stays as a stub). | Sonny + Arthur | Decision recorded in the MR description. |
| P-4 | (If P-3 says "ship in a sibling MR") Open and merge the sibling MR before the frontend half of Approval Line starts. | Arthur | Endpoint live in `wecms-api`; `fetchTenantUsers` exists in `wecms-web`. |
| P-5 | Resolve the conflicts flagged at the end of [feature spec](../../../features/settings/approval-line.md): `docs/jex/business-rules/approval.md` cites wrong WSVC ids (`comm_0194_*` / `comm_0195_*` instead of `comm_0222_*`) and references a non-existent "parallel vs sequential" flag. **Decision:** out-of-scope JEX-doc cleanup, recorded in feature §14 conflicts section — does NOT block this MR. | Documentation pass (separate ticket) | Decision recorded; this MR proceeds with the correct WSVC citations. |

---

## 1. Backend tasks — `wecms-api`

Order: **schema → entity → repo → DTO → service → controller → tests**. (No audit step — feature § 14 OQ-6 resolved 2026-05-13, legacy parity.)

### B-1. Flyway migration: tables

| Item | Detail |
|---|---|
| File | `wecms-api/src/main/resources/db/migration/V{N}__approval_line_schema.sql` |
| Inputs | Verified DEV schema from `information_schema.columns` (feature § 3) — `APPR_LINE_INFM` (7 columns, PK on `(BIZ_KEY, APPR_LINE_NO)`), `APPR_LINE_INFM_DETL` (12 columns, PK on `(BIZ_KEY, APPR_LINE_NO, APPRVR_ID)`). |
| Outputs | Two `CREATE TABLE` statements + one supporting index `ix_appr_line_infm_biz_key_reg_dtm`. No FK constraints (parity with DEV). |
| Acceptance | `./gradlew flywayMigrate` succeeds locally. `\d APPR_LINE_INFM` + `\d APPR_LINE_INFM_DETL` in psql show the same columns / types / nullability as DEV. `spring.jpa.hibernate.ddl-auto = validate` passes when the entities land. |
| Risk | Column type mismatch (e.g. `NUMERIC(15,0)` vs `BIGINT`) — keep `NUMERIC(15,0)` to match DEV. Map to `Long` in JPA via `precision = 15, scale = 0`. |

### B-2. Flyway migration: `wb_get_next_val` branch

| Item | Detail |
|---|---|
| File | `wecms-api/src/main/resources/db/migration/V{N+1}__wb_get_next_val_appr_line_branch.sql` |
| Inputs | Existing V11 function definition + the legacy DEV implementation of the `'appr_line_no'` branch (verified via `pg_get_functiondef('wecmsvndev.wb_get_next_val'::regproc)` on DEV at planning time). |
| Outputs | `CREATE OR REPLACE FUNCTION wb_get_next_val(...)` adding the new branch `WHEN p_tablename = 'appr_line_infm' AND p_columnname = 'appr_line_no'`. Logic: `SELECT COALESCE(MAX(appr_line_no), 0) + 1 FROM appr_line_infm WHERE biz_key = p_biz_key`. |
| Acceptance | Function callable directly: `SELECT wb_get_next_val('appr_line_infm', 'appr_line_no', 'VN_VNM0004_000001', NULL)` returns `1` against an empty table, increments on subsequent inserts. |
| Risk | Re-creating the function silently breaks other branches if the COALESCE / IF structure is wrong. Mitigation: read the existing V11 SQL verbatim before adding the branch, keep all existing branches untouched, add only the new arm. |
| Action item for [`wecms-api/CLAUDE.md`](../../../../CLAUDE.md) | Update the "Surviving legacy functions" table with the new branch + retirement plan. |

### B-3. Entities + composite-PK classes

| Item | Detail |
|---|---|
| Files | `com/kosign/wecms/domain/setting/approvalline/entity/ApprovalLine.java`, `ApprovalLineId.java`, `ApprovalLineApprover.java`, `ApprovalLineApproverId.java` |
| Inputs | Verified column shape (feature § 3); existing `BaseEntity` (`REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM`); precedent from `domain/setting/firmbankingsettings/entity/` |
| Outputs | Four classes per [backend § 4](../../../references/business-rules/approval-line.md). `@IdClass` composite-PK pattern (no `@EmbeddedId`). |
| Acceptance | `./gradlew compileJava` clean. `./gradlew bootRun` starts without Hibernate validation errors (schema validate passes). A trivial integration test inserts + reads one row through `ApprovalLineRepository.save / findById`. |
| Risk | `apprvr_disp_ord NUMERIC(1,0)` mapped as `Integer` truncates silently. Mitigation: `@Column(precision = 1, scale = 0)` + a `@Range(min = 1, max = 5)` bean-validation annotation on the entity field for an extra DB-layer guard (advisory — main guard is service-layer). |

### B-4. Repositories

| Item | Detail |
|---|---|
| Files | `ApprovalLineRepository.java`, `ApprovalLineApproverRepository.java` |
| Inputs | Backend § 4.5 + § 4.6 method signatures. |
| Outputs | Two interfaces. The detail repo carries a `@Modifying @Query` for `deleteAllByBizKeyAndApprLineNo`. |
| Acceptance | `findByBizKeyAndApprLineNmContainingIgnoreCase` emits `ILIKE` (verify with `spring.jpa.show-sql=true` in test config). `findAllByBizKeyAndApprLineNoInOrderByApprLineNoAscApprvrDispOrdAsc` emits one batched IN-query. |
| Risk | Spring Data JPA's `IgnoreCase` derived keyword produces `LOWER(...) LIKE LOWER(...)` rather than `ILIKE` on Postgres. Acceptable — same semantic, slightly slower without a functional index; revisit only if list latency suffers. |

### B-5. DTOs

| Item | Detail |
|---|---|
| Files | `dto/ApprovalLineRow.java`, `ApprovalLineApproverRow.java`, `ApprovalLineListResponse.java`, `ApprovalLineCreateRequest.java`, `ApprovalLineUpdateRequest.java`, `ApprovalLineApproverInput.java`, `ApprovalLineSortField.java` |
| Inputs | Backend § 5. |
| Outputs | Seven Java records with Bean Validation annotations. No `@JsonProperty` (global SNAKE_CASE strategy handles wire names). |
| Acceptance | Round-trip JSON: serialize a `ApprovalLineRow` → wire shape exactly matches the example in [backend § 1.1](../../../references/business-rules/approval-line.md). Deserialize the Create request example → all fields populate; missing `appr_line_nm` triggers `MethodArgumentNotValidException`. |
| Risk | Records can't carry custom `@JsonInclude` on individual components — verify `userImgUrl = null` serialises as `"user_img_url": null` (the spec requires explicit null, not field omission). |

### B-6. `LegacyFunctionRepository` extension

| Item | Detail |
|---|---|
| File | `common/legacy/LegacyFunctionRepository.java` (extend) |
| Inputs | Backend § 6 + the existing funnel pattern used by `contacts` (OQ-4 resolved 2026-05-12 in [contacts.md](../../../features/settings/contacts.md)). |
| Outputs | New method `long nextVal(String tableName, String columnName, String bizKey)` invoking the legacy function via `entityManager.createNativeQuery(...)`. |
| Acceptance | Returns 1 on an empty `APPR_LINE_INFM`. Sequential calls produce `2`, `3`, … per tenant. Cross-tenant isolation: tenant A's next-val is independent of tenant B's. |
| Risk | If `LegacyFunctionRepository` doesn't already exist (OQ-4's "Contacts MR introduces it" hasn't landed yet), this MR adds it. Coordinate with the Contacts MR — first one to land introduces the class. |

### B-7. Service

| Item | Detail |
|---|---|
| File | `domain/setting/approvalline/service/ApprovalLineService.java` |
| Inputs | Backend § 3. |
| Outputs | Five `@Transactional` methods (`list`, `detail`, `create`, `update`, `delete`) + the helper for `userImgUrl` resolution + the validation guard `validateRequest(...)`. |
| Acceptance | Unit tests for each method (see B-9). For `update`: a representative test that proves the diff-sync handles INSERT + UPDATE + DELETE in one call. |
| Risk | Diff-sync ordering bug — if a row is both "removed" and "re-added" with new disp_ord under the same `apprvr_id`, the naive set-diff produces an UPDATE (correct). But if the same `apprvr_id` appears twice in the new list, the dup-guard catches it. Test both cases. |

### B-8. Controller + error-code registration

| Item | Detail |
|---|---|
| Files | `controller/ApprovalLineController.java`, `messages.properties` (+ `messages_en.properties`) |
| Inputs | Backend § 2 + § 1.3. |
| Outputs | Five endpoints with `@Valid` on request bodies, `@PathVariable long apprLineNo`. Six new `WBE_APPROVAL_LINE_*` keys in `messages.properties` (no `audit.approval_line.*` keys — feature § 14 OQ-6 resolved, legacy parity, no audit-log writes). |
| Acceptance | Manual smoke test via `curl`/Postman: POST → 200 success envelope; POST with 6 approvers → 200 error envelope with `WBE_APPROVAL_LINE_TOO_MANY_APPROVERS`. The `trace_id` is present in every error response and matches a log line. |
| Risk | Forgetting to add a code to `messages.properties` produces "No message available …" in the wire — caught by tests in B-9. |

### B-9. Tests

| Item | Detail |
|---|---|
| Files | `src/test/java/com/kosign/wecms/domain/setting/approvalline/` — service + controller tests |
| Inputs | Spring Boot test slice + a real Flyway-migrated H2 / Testcontainers Postgres (per the existing repo pattern; if Testcontainers isn't yet on the dependency list, add it in the sprint that genuinely needs it — see [`wecms-api/CLAUDE.md`](../../../../CLAUDE.md)). |
| Outputs | Coverage targets: `list`, `detail`, `create` (happy + 6-approver + dup + tenant-link), `update` (diff sync), `delete` (happy + not-found). Plus controller integration for at least one POST + one PUT. |
| Acceptance | `./gradlew test` green. Coverage report includes the new service + controller. |
| Risk | Testcontainers vs H2 — Postgres-specific features (`ILIKE`, `wb_get_next_val`) don't work on H2. Lean on Testcontainers + a Flyway-migrated container. If the sprint can't take that dependency, mock `LegacyFunctionRepository` and skip the ILIKE assertion. |

### B-10. Backend doc updates

| Item | Detail |
|---|---|
| File | [`wecms-api/CLAUDE.md`](../../../../CLAUDE.md) |
| Outputs | (a) Add a `domain/setting/approval-line/` row to the architecture tree (§ Architecture Rules); (b) Add an `Approval Line` row to the Feature Status table; (c) Update the Flyway migrations table with the two new versions; (d) Add the new error codes to the "Error codes in use" table; (e) Update the "Surviving legacy functions" table with the `wb_get_next_val('appr_line_infm', ...)` branch + retirement plan; (f) Update the "Endpoints" table with the five new URIs. |
| Acceptance | The MR reviewer can find every new artifact in the doc without digging through the source. |

---

## 2. Frontend tasks — `wecms-web`

Order: **wire types → mappers → API client → types extension → schema → components → locales → manual test**.

### F-1. Wire types

| Item | Detail |
|---|---|
| File | [`wecms-web/lib/types/api.ts`](../../../../../wecms-web/lib/types/api.ts) |
| Inputs | Frontend § 6. |
| Outputs | Six new `Api*` interfaces with `snake_case` property names. |
| Acceptance | `tsc --noEmit` clean. The wire types compile against the JSON shapes documented in [backend § 1.1](../../../references/business-rules/approval-line.md) (verify by hand). |

### F-2. Mappers

| Item | Detail |
|---|---|
| File | [`wecms-web/lib/api/mappers.ts`](../../../../../wecms-web/lib/api/mappers.ts) |
| Inputs | Frontend § 7. |
| Outputs | `mapApprovalLineRow`, `mapApprovalLineApprover`. |
| Acceptance | `tsc --noEmit` clean. A trivial vitest test (if vitest is wired; otherwise a manual node `tsx` run) round-trips a wire row through the mapper and produces the expected camelCase shape. |

### F-3. API client

| Item | Detail |
|---|---|
| File | `wecms-web/lib/api/approvalLine.ts` (NEW) |
| Inputs | Frontend § 5. |
| Outputs | `fetchApprovalLines`, `getApprovalLine`, `createApprovalLine`, `updateApprovalLine`, `deleteApprovalLine`. |
| Acceptance | `tsc --noEmit` clean. Manual: with the backend running locally, `fetchApprovalLines()` returns the live list. Network panel shows snake_case wire bodies. |

### F-4. In-memory types extension

| Item | Detail |
|---|---|
| Files | [`components/settings/approval-line/types.ts`](../../../../../wecms-web/components/settings/approval-line/types.ts), `approverMockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) |
| Inputs | Frontend § 8. |
| Outputs | Updated `ApprovalLineRow` (drops static `id`/`name`/`approverName`/`approverCount`/`createDate`; adds `apprLineNo`/`apprLineNm`/`regDtm`/`approvers[]`). Extended `ApproverRow` with optional `dispOrd?`/`userImgUrl?`. |
| Acceptance | `tsc --noEmit` clean against every consumer (panel, dialog, table, popover). Static mock data still loads in offline-dev mode. |
| Risk | Breaking change to the static-UI mock shape — must update `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) in the same commit to keep the offline-dev affordance compilable. |

### F-5. Zod schema

| Item | Detail |
|---|---|
| File | `components/settings/approval-line/approvalLineSchema.ts` (NEW) |
| Inputs | Frontend § 14. |
| Outputs | `approvalLineFormSchema` + `approverInputSchema` + `ApprovalLineFormValues` type. |
| Acceptance | Unit-level: invalid inputs (blank name, 6 approvers, duplicate `apprvrId`) trigger the expected message keys. |

### F-6. Panel wire-up

| Item | Detail |
|---|---|
| File | [`components/settings/approval-line/ApprovalLinePanel.tsx`](../../../../../wecms-web/components/settings/approval-line/ApprovalLinePanel.tsx) |
| Inputs | Frontend § 4.1 + § 4.4. |
| Outputs | List fetched via `useApiData(fetchApprovalLines)`. Local-keyword filter retained. Delete confirmation wired via `useDialog()`. Dialog onSuccess triggers `refetch()`. The `seedApproversFromRow` helper is removed. |
| Acceptance | UI parity with the static panel except the data is real. Delete confirmation surfaces the line name. Errors surface via system dialog. |

### F-7. Table wire-up

| Item | Detail |
|---|---|
| File | [`components/settings/approval-line/ApprovalLineTable.tsx`](../../../../../wecms-web/components/settings/approval-line/ApprovalLineTable.tsx) |
| Inputs | Frontend § 8. |
| Outputs | Renders `row.approvers[0].name` + chip when `row.approvers.length > 1`. Formats `regDtm` (yyyyMMddHHmmss) via a date util (`formatYmdHis` — add to `lib/utils.ts` if not present) into the locale-aware date string. Accepts `onDelete(row)` prop. |
| Acceptance | A row with three approvers shows the first approver's name + "+2" chip. The Create Date column matches the Figma format (`dd-mm-yyyy` per the Figma; confirm Phase 3). |

### F-8. Dialog wire-up

| Item | Detail |
|---|---|
| File | [`components/settings/approval-line/ApprovalLineDialog.tsx`](../../../../../wecms-web/components/settings/approval-line/ApprovalLineDialog.tsx) |
| Inputs | Frontend § 4.2, § 4.4, § 14. |
| Outputs | RHF + Zod form. On mount in `mode="update"`, fetches `getApprovalLine(apprLineNo)` and resets form. On submit, calls `createApprovalLine` / `updateApprovalLine` and routes outcome to `useDialog()`. Reorder handle (already in static UI) updates the approver array order — `disp_ord` is **not** sent in the wire (service assigns 1..N). |
| Acceptance | Add → list refreshes with the new row. Edit reorder → re-open Edit shows the new order. Save with blank name → Zod error inline. Save with 6 approvers → Zod error inline. |

### F-9. Popover wire-up

| Item | Detail |
|---|---|
| File | [`components/settings/approval-line/AddApproverPopover.tsx`](../../../../../wecms-web/components/settings/approval-line/AddApproverPopover.tsx) |
| Inputs | Frontend § 4.3. |
| Outputs | Replaces `APPROVER_POOL` import with `useApiData(fetchTenantUsers)`. Filters out already-selected ids client-side. |
| Acceptance | Popover shows the live tenant directory. Already-added approvers are filtered out. Search input narrows the list. |
| Risk | If P-3 / P-4 (tenant-users endpoint) hasn't landed, this task falls back to `APPROVER_POOL`. Document in MR. |

### F-10. Locales

| Item | Detail |
|---|---|
| Files | [`locales/en.json`](../../../../../wecms-web/locales/en.json), `ko.json`, `vi.json` |
| Inputs | Frontend § 12 + § 14. |
| Outputs | Add the full `settings.approval_line.*` namespace plus the five Zod message keys (`name_required`, `name_too_long`, `approver_required`, `approver_too_many`, `approver_duplicate`). |
| Acceptance | Switching language at runtime swaps every Approval Line surface to the picked locale. No raw key strings ("settings.approval_line.…") visible anywhere. |

### F-11. Manual test pass

Follow [frontend § 16 Manual test plan](../../../../../wecms-web/docs/features/settings/approval-line.md) end-to-end with the local backend running. Capture screenshots for the MR description per [`wecms-web/CLAUDE.md`](../../../../../wecms-web/CLAUDE.md) UI verification rule.

---

## 3. Integration / sequencing

```
┌─────────────────────────────────────────────────────────────┐
│ Step 0 — Prereqs (PO sign-off, JEX doc, tenant-users plan)   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Step 1 — Backend (B-1 → B-10)                                │
│  - Migrations land in main first                              │
│  - Endpoints work via curl/Postman before frontend starts    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Step 2 — Frontend wire-up (F-1 → F-11)                       │
│  - Static UI is already shipped — task is type + data only   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Step 3 — End-to-end smoke test (frontend § 16)               │
│  - 12 scenarios; pass/fail captured in MR description       │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│ Step 4 — MR review (full-stack ownership rule — one MR)     │
│  - Reviewer follows the verification targets in this doc     │
└─────────────────────────────────────────────────────────────┘
```

The full-stack ownership rule (per [`wecms-api/CLAUDE.md`](../../../../CLAUDE.md) refactoring rules step 6) requires backend and frontend changes for the same feature to ship in **one** MR. This MR's commit topology:

1. `feat(approval-line): flyway schema + wb_get_next_val branch`
2. `feat(approval-line): entities, repos, DTOs`
3. `feat(approval-line): service + controller`
4. `feat(approval-line): backend tests`
5. `feat(approval-line): wire types + API client + mappers`
6. `feat(approval-line): in-memory types + Zod schema`
7. `feat(approval-line): panel + dialog + popover wire-up`
8. `feat(approval-line): locales`
9. `docs(approval-line): refactor specs + CLAUDE.md updates`

(Order is suggestive — they can be squashed into 4–5 logical commits at MR-prep time.)

---

## 4. Risks and open questions

### Open questions still on the table (need PO answer)

| OQ | Section | Question |
|---|---|---|
| OQ-4 | [feature § 14](../../../features/settings/approval-line.md) | Confirm `apprvr_disp_ord` re-normalisation on Create AND Update (refactor proposes; JEX Create accepts client order). |

All other OQs resolved 2026-05-13:

- ~~OQ-1~~ — `GET /api/v1/users/{apprvrId}/image`, `null` when none.
- ~~OQ-2~~ — JEX-side spec to be created during documentation pass (prerequisite P-2).
- ~~OQ-3~~ — No pagination for MVP.
- ~~OQ-5~~ — No admin-only gate; follow live JEX behaviour.
- ~~OQ-6~~ — No audit-log writes; legacy parity.
- ~~OQ-7~~ — Extend `wb_get_next_val(...)` with new branch for `('appr_line_infm', 'appr_line_no', biz_key, …)`.

### Risks during implementation

| Risk | Likelihood | Mitigation |
|---|---|---|
| `wb_get_next_val` re-creation breaks existing branches | Medium | Read V11 verbatim before re-creating; one branch added, no others touched. |
| `apprvr_disp_ord NUMERIC(1,0)` overflow if a future change raises the cap to > 9 | Low | Cap is hard-coded at 5 in WBW1164; widening would be its own MR. |
| Tenant-users endpoint slips, blocking F-9 | Medium | Fallback to `APPROVER_POOL` static stub; ship the popover wire-up against a constant, replace in a follow-up. |
| Hibernate `validate` fails because legacy `varchar(14)` audit columns disagree with `BaseEntity`'s annotations | Medium | Verify `BaseEntity`'s column shape on an early B-3 dry-run before writing the full service. The `firm-banking-settings` precedent has already solved this; copy. |
| `ILIKE` vs `LOWER(...) LIKE LOWER(...)` performance | Low | Acceptable for MVP. Revisit only if list latency suffers on the developer laptop with a seeded list of > 200 lines. |
| Multi-tab Edit race producing `WBE_APPROVAL_LINE_NOT_FOUND` mid-edit | Low | Surface via the standard error dialog; user retries — no special concurrency handling needed for MVP. |

### Non-risks (deliberately not addressed)

- **Optimistic locking** — no `@Version` on entities. The diff-sync algorithm is idempotent enough; if two users edit the same line at the same time the second save wins. Acceptable for MVP.
- **Per-payment-type approval execution** — explicitly out of scope (feature § 1 / § 12).
- **Approval Box runtime flow** — out of scope.
- **Excel import / export** — out of scope.

---

## 5. Definition of Done (MR-level)

The MR is ready for review when:

- [ ] All Backend tasks B-1 to B-10 are merged into the feature branch.
- [ ] All Frontend tasks F-1 to F-11 are merged into the feature branch.
- [ ] `./gradlew build` and `./gradlew test` green in `wecms-api`.
- [ ] `npm run lint` + `tsc --noEmit` + `npm run build` green in `wecms-web`.
- [ ] The 12 manual scenarios in frontend § 16 are exercised, with screenshots in the MR description.
- [ ] [`wecms-api/CLAUDE.md`](../../../../CLAUDE.md) updates per B-10.
- [ ] [`wecms-web/CLAUDE.md`](../../../../../wecms-web/CLAUDE.md) Feature Status table updated with the `/api/v1/settings/approval-line` row.
- [ ] All OQs in [feature § 14](../../../features/settings/approval-line.md) either resolved (struck through with PO sign-off) or explicitly carried over to a follow-up MR.
- [ ] The two flagged conflicts at the end of the feature doc (the stale `docs/jex/business-rules/approval.md` WSVC ids + the missing parallel/sequential flag) are recorded as JEX-side cleanup tickets — not blocking this MR.
- [ ] The `docs/jex/features/approval/approval-line.md` JEX-side stub created in P-2 is committed in this MR (so feature naming traceability matches the slug rule).
