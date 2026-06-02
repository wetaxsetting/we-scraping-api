# Firm Banking History — Independent Review (2026-05-08)

**Reviewer:** Independent (did not author Phases 2–3).
**Branch under review:** Phases 2–3d as committed at 2026-05-08.
**Scope:** Backend (`wecms-api`), frontend (`wecms-web`), and supporting docs/migrations under the Firm Banking History feature only. JEX trees and other features were inspected only as parity references.

Verification was performed by reading source files, repository queries via the read-only `postgres` MCP, and grep audits across the refactor docs. Summaries / completion reports from prior phases were treated as claims, not evidence.

---

## 1. Verdict

**CHANGES-REQUIRED.**

A single Blocker prevents `./gradlew test` from compiling on `wecms-api` (1-arg shortfall on a record constructor in the controller test, after a recent DTO field addition). Four Majors and five Minors must be triaged before merge. The Backend ↔ Frontend wire shape is correctly aligned end-to-end; the issues are concentrated in test coverage gaps, doc drift, and one frontend silent-fallback that masks unknown discriminator values.

If the Blocker is resolved and the Majors are accepted as follow-ups (with tickets), this could move to **APPROVED-WITH-FOLLOWUPS**. As shipped today, it does not build.

---

## 2. Findings

| # | Severity | Dimension | File:line | Issue | Suggested fix |
|---|---|---|---|---|---|
| **B1** | **Blocker** | Backend / Tests | [`wecms-api/.../firmbankinghistory/controller/FirmBankingHistoryControllerTest.java:92-103`](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/controller/FirmBankingHistoryControllerTest.java) | `new FirmBankingHistoryRow(...)` is called with **11 arguments**, but [`FirmBankingHistoryRow`](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/dto/FirmBankingHistoryRow.java) is now a record with **12 components** (`svcTypeKey` was inserted between `svcType` and `svcNm`). `./gradlew test` (and any IDE / CI build) fails to compile this class. The accompanying `requestParams_areReadFromSnakeCaseWireNames` test is unaffected because it only constructs a `FirmBankingHistoryListResponse` via `emptyResponse()`. | Insert the resolved `svcTypeKey` literal (e.g. `"settings.firm_banking_history.service_type.firm_banking_transaction"`) at position 3 of the constructor call AND assert it on the wire (see M2). |
| **M1** | Major | Code quality / Tests | [`wecms-api/.../common/enums/FirmBankingSvcType.java`](../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) (new in last edit) | The `FirmBankingSvcType` enum has **zero** test coverage. There is no test class anywhere under `src/test/java/com/kosign/wecms/common/enums/` (directory does not exist). Sibling enums (`TranTp`, the resolver) have a dedicated test class `FirmBankingServiceNameResolverTest`. Asymmetry — the new enum is the F-leg's wire-side i18n-key authority and its `fromCode`/`resolveKey` paths are not pinned by tests. | Add a parallel `FirmBankingSvcTypeTest` in either `common/enums/` or alongside `FirmBankingServiceNameResolverTest`. Mirror the assertions: 4 constants present, code values `F/S/G/B`, `i18nKey()` namespace, `fromCode` lenient parse + null/blank/unknown safety. |
| **M2** | Major | Backend ↔ Frontend consistency / Tests | [`FirmBankingHistoryControllerTest.java:124-134`](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/controller/FirmBankingHistoryControllerTest.java) | The wire-shape `jsonPath` block asserts `tarn_dtm`, `svc_type`, `svc_nm`, `svc_name_key`, `seq_no`, `dep_bank_cd`, `rslt_cd`, `rslt_msg`, `job`, `van_org_cd` — but does **not** assert `svc_type_key`. Even after B1 is fixed, the new wire field's serialization contract is unverified. The frontend [`ApiFirmBankingHistoryRow`](../../../../wecms-web/lib/types/api.ts) and [`extractFirmBankingServiceType`](../../../../wecms-web/lib/api/mappers.ts) consume `svc_type_key` directly; a backend-side wire regression would silently misclassify rows on the frontend (see m1). | After B1, add `.andExpect(jsonPath("$.data.list[0].svc_type_key", is("settings.firm_banking_history.service_type.firm_banking_transaction")))`. |
| **M3** | Major | Doc drift | [`docs/refactor-wecms/migration/firm-banking-history-mapping.md`](../migration/firm-banking-history-mapping.md), [`features/settings/firm-banking-history.md`](../../features/settings/firm-banking-history.md), [this checklist](firm-banking-history-parity-checklist.md) | None of the three primary docs mention `svc_type_key` / `svcTypeKey` / `FirmBankingSvcType`. The migration doc's field-mapping table explicitly enumerates the wire shape (it lists `svc_name_key` as a refactor-only Phase 3d addition) but is missing the newer `svc_type_key`. Verified by grep: zero matches across all three files. The wire contract is not what the docs say it is. | Add a row to the field-mapping table for `svc_type_key` (Phase 3d.2 / "modernized refactor enum"); note in the feature spec § 4 backend-architecture and § 5 API-contract sections; update the parity checklist B.3 wire-shape row to include `svc_type_key`. |
| **M4** | Major | Migration / Traceability | [`docs/refactor-wecms/migration/service-mapping.md`](../migration/service-mapping.md) | The cross-feature `service-mapping.md` registry **does not list** `comm_0221_001_r001` even though the feature is fully wired (backend + frontend + tests + docs). Phase 1 explicitly required: *"Add a row here when starting work on a new JEX service. Keep the kebab-case slug consistent across `features/`, `backend/`, `frontend/`, `database/`, and `migration/`."* That step was skipped. Future features looking up "what JEX services have been migrated" will not see this one. | Add a row: `\| comm_0221_001_r001 \| comm_0221_001_r001_act.jsp \| /api/v1/settings/firm-banking-history \| GET \| (Settings modal panel) \| setting \| Working \| features/settings/firm-banking-history.md (Needs Confirmation) \|`. |
| m1 | Minor | Frontend | [`mappers.ts:560-571`](../../../../wecms-web/lib/api/mappers.ts) `extractFirmBankingServiceType` | Returns `"firm_banking_transaction"` for `null` / blank / unknown / mis-namespaced `svcTypeKey`. A backend that ships an unrecognised code (e.g. a new leg added without updating `FirmBankingSvcType`) will silently render every such row as "Firm Banking Transaction" — wrong leg, wrong popover content (the popover branches on `serviceType`). Matches the **previous** frontend behaviour (`coerceFirmBankingServiceType` had the same default), but the previous default at least operated on `svc_type` directly so a mismatch would be self-evident; now the misclassification is one indirection deeper. | Either return a sentinel like `"unknown"` (and surface it in the table cell as the raw `svc_type` letter, not as the F-leg label), or `console.warn` once on first occurrence so devs see the misalignment. Add a unit test for the unknown / null path. |
| m2 | Minor | Performance | [`FirmBankingHistoryRepository.java:38`](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/repository/FirmBankingHistoryRepository.java) | `SUBSTRING(v.tarnDtm, 1, 8) BETWEEN :fromDate AND :toDate` is non-sargable. Postgres cannot use a `(BIZ_KEY, TARN_DTM)` btree even if one were added — a function on the indexed column disqualifies the index path. Per-leg, the V60 view applies this predicate via `WHERE 1=1` outside the leg, then over the materialised UNION. At R&D scale (≤ 10K rows / leg / tenant) this is a non-issue; at production scale it would force per-leg sequential scans. | Defer fix to production-scale work. Two options for then: (a) emit `tarnDtm` as `VARCHAR(14)` directly indexed and treat the predicate as `tarnDtm BETWEEN :fromDate||'00000000' AND :toDate||'99999999'`; (b) materialise the view as a table and index `(BIZ_KEY, LEFT(TARN_DTM,8))` via an expression index. |
| m3 | Minor | Code quality | [`mappers.ts:625`](../../../../wecms-web/lib/api/mappers.ts) | `id: \`${api.svc_type ?? ""}-${api.seq_no ?? ""}\`` synthesises the React row key as a `(svc_type, seq_no)` tuple. Uniqueness depends on the V60 view PK `(BIZ_KEY, TARN_DTM, SVC_TYPE, SEQ_NO)`. In single-tenant + same-second + same-leg + same-seq scenarios (e.g. duplicate gateway responses) two rows could collide. In practice unobserved, but unproven. | Prefix the key with `tarn_dtm` (`${tarn_dtm}-${svc_type}-${seq_no}`) — matches the view's true uniqueness shape. Two-line change in the mapper. |
| m4 | Minor | Localization | [`locales/ko.json`](../../../../wecms-web/locales/ko.json) / [`locales/vi.json`](../../../../wecms-web/locales/vi.json) | Neither file carries `fb_history.service.*` (10 keys) or `fb_history.result.*` (18 keys). Verified by grep — both files return zero matches. Operationally the workspace `LocaleProvider` falls back to EN automatically, so the page renders correctly in EN regardless of locale. KO and VI users will see English service-name and result-code labels in the Detail Popover. The feature spec's § Result-code i18n bundle and § Service-name mapping document this as "UNRESOLVED" with the rationale ("translations live in Jex Studio's ML config DB, outside source — do not invent"). | No code change needed. Acceptable per the workspace's "do not fabricate translations" rule. Track translator handoff in a separate ticket. |
| m5 | Minor | JEX parity | [`mappers.ts:580`](../../../../wecms-web/lib/api/mappers.ts) `FBH_SUCCESS_CODES` | `42110000` is excluded from the success set, matching the legacy `comm_0221_001.ts:298` (which also treats it as failure). This is correct legacy parity, but DEV's actual e-Invoice "no-data" responses commonly carry `42110000`, and the popover renders them as `Failed` with the localised "No transaction results found" message. The result-cell pill colour reads as red (failure ramp), which is misleading for a "no data" outcome. | Defer to a PO conversation — this matches legacy behaviour exactly. If the PO confirms the colour treatment is wrong, add `42110000` to `FBH_SUCCESS_CODES` AND verify the i18n text reads as informational rather than failure. |
| m6 | Minor | Code quality | [`FirmBankingHistoryView.java:37-43`](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/entity/FirmBankingHistoryView.java) | The entity is `@Immutable` (Hibernate skips dirty-checking) but Lombok still generates `@Setter` on every field. The setters are dead surface and let callers mutate field state in memory — Hibernate won't flush, but a confused caller could believe a `setSvcNm("…")` had effect. | Drop `@Setter` from the class. Spring Data uses the no-arg constructor + reflection on fields; tests that build sample rows currently use the setters and would need to switch to direct field reflection or a builder. Trade-off — leave as-is for testability, or refactor the test sample-row helper to use a small builder. |
| n1 | Nit | Doc fidelity | [`features/settings/firm-banking-history.md`](../../features/settings/firm-banking-history.md) §6.2 | The view-definition sketch in the schema doc says the F-leg uses `kr_to_vn_time(TFT.TRAN_DT‖TRAN_TM)` — accurate for legacy parity. The actual V60 SQL uses `kr_to_vn_time(TFT.TRAN_DT \|\| TFT.TRAN_TM)` (Postgres concat). Functionally identical, cosmetic only. | Optional: align the doc's pseudo-code with the migration's SQL syntax. |
| n2 | Nit | Doc | [`features/settings/firm-banking-history.md`](../../features/settings/firm-banking-history.md) §11 open questions | Open question 4 ("Demo seed scope") is marked "Resolved (Phase 2)" — accurate; V61 seeds 8 rows per leg. But the row labels in §11 still say "Resolved (Phase 2) — V61 seeds 8 rows per leg" without crossing the row out. Minor presentation; the strikethrough convention is used for #3 + #8 but not #4-7. | Optional: apply the same strikethrough convention to questions 4-7 if their resolutions are also intended to retire the items. |
| n3 | Nit | Tests | [`FirmBankingServiceNameResolverTest.java:74-86`](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankinghistory/service/FirmBankingServiceNameResolverTest.java) | The S/G/B-leg branch tests assert each English literal one-by-one. If V60's CASE-WHEN literals ever drift (e.g. someone adds a fifth scraping label), the resolver test fails — good. But the test does not exercise the resolver against the **actual** V60 SQL via an integration test. Drift between the V60 CASE-WHEN strings and the resolver's `switch` literals is a real risk. | Defer — at R&D scale the unit-test coverage is enough. If a fifth leg is added in production, add an integration test that runs the view + asserts every distinct `svc_nm` value resolves to a non-null key. |

---

## 3. Parity checklist — final ticked state

I read every checklist item against the source files and the JEX baseline. Updates relative to the current state of [the parity checklist](firm-banking-history-parity-checklist.md):

### A. Database parity
- A.1 Source-table schema — **mostly verified**. V59 declares all three tables with documented divergences. The TB_FIRM_TRAN PK matches DEV; the synthetic NXSIAS PK is documented. ✅
- A.2 DB functions — **verified by reading V60**. `kr_to_vn_time` + `wb_get_napas_bank_nm` (1-arg) ported verbatim. The 3-arg `wb_get_napas_bank_nm` is intentionally not ported (depends on `napas_bank_info` not migrated). ✅
- A.3 View — **verified by reading V60**. Five-leg UNION ALL matches `IDO.TB_FIRM_TRAN_R002.xml:24-129` modulo the two cosmetic typo fixes (`SVN_NM` → `SVC_NM`). ✅
- A.4 Indexes — V59 declares `ix_tb_firm_tran_biz_dt`, `ix_nxsias_biz_type`, `ix_nxsias_biz_job`. EXPLAIN against seeded data not run in this review. ⚠️ Phase-4 task.
- A.5 Demo seed — V61 inserts 8 firm + 8 NXSIAS + 2 bank-code rows. NAPAS already in V22, EXT_TRAN in V29. ✅

### B. API parity
- B.1 Endpoint — **verified**. `GET /api/v1/settings/firm-banking-history`, JWT-protected globally. ✅
- B.2 Request validation — **verified by reading service**. All 8 input rules implemented. ✅
- B.3 Response shape — **partially verified**. The wire-shape jsonPath in the controller test omits `svc_type_key` (M2). After B1 is fixed and M2 added, this row goes ✅.
- B.4 Tenant scoping — **verified**. `TenantContext.currentBizKey()` at `FirmBankingHistoryService:129`, test `list_alwaysUsesTenantContextBizKey`. ✅
- B.5 Sort — **verified**. Whitelist enum + default tiebreaker tested. ✅
- B.6 Pagination — **verified**. Default 15 (backend) / 10 (frontend, intentional divergence per Account Inquiry precedent), clamped to 100. ✅
- B.7 Filter behaviour — **mostly verified by code inspection**. JPQL + escape pattern match legacy. The cross-leg blend at `svcType=null` is verified by the JPQL `IS NULL OR` guard. Live data parity is a Phase-4 task.
- B.8 Tarn DTM semantics — **verified by reading the V60 view**. ✅

### C. Frontend parity
- C.1 Routing — Settings modal panel, no standalone route. ✅
- C.2 Layout — verified against the table/popover code. ✅
- C.3 Date range — Phase-3a `<InteractiveDateRangePicker>` wiring verified. ✅
- C.4 Service-type dropdown — verified. F/S/G/B → wire param. ✅
- C.5 Result column — 3-state coerce + colour ramp verified. m5 logged. ✅
- C.6 View Details popover — F/S/G/B branches all use the per-leg resolver chain. Result-message + service-name resolution verified. ✅
- **C.7 Service-name display** — Phase 3d Spring-owned mapping verified; legacy `SERVICE_NAME_MAP` retired. ✅ The remaining C.7 sub-items (table-cell override for G/B legs to show e-Invoice No / Tax ID) are still **deferred**.
- C.8 View Setting — NOT APPLICABLE; explicit guard in place. ✅
- C.9 Naming hygiene — verified by grep (no UPPER_SNAKE in components, no `COMMON_HEAD`). ✅

### D. Localization
- EN coverage — verified by grep against `locales/en.json` (10 service keys + 18 result keys + dictionary VAN bundle in `dictionaryVan.data.ts`). ✅
- KO/VI — m4: keys absent in `locales/{ko,vi}.json` (verified by grep). EN fallback via `LocaleProvider` is the documented behaviour. ⚠️
- KM — out of scope (workspace supports `en/ko/vi` only). ✅

### E. Error handling
- All items verified by reading `GlobalExceptionHandler` + service throws. ✅

### F. Performance / G. Out-of-scope / H. Sign-off
- F: m2 + m3 carry forward. Other items verified by code inspection.
- G: All Out-of-Scope items confirmed by absence in the controller surface.
- H: Sign-off remains unticked pending B1, M1–M4 resolution.

---

## 4. Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| **`./gradlew clean build` red on CI** | Certain (B1) | Blocks merge, blocks any downstream MR that touches the same module | Fix B1 immediately. Adding the missing `svcTypeKey` literal to the test constructor is a one-line change. |
| **Silent leg misclassification** when backend ships an unrecognised `svc_type_key` | Low (resolver currently emits one of 4 keys; new legs require a code change) | High user impact (popover renders wrong fields, table shows wrong leg label) | m1 — return a sentinel + surface raw `svc_type` letter on unknown values. Add a console.warn + a unit test. |
| **Doc / wire shape drift** | High (already drifted: `svc_type_key` undocumented) | Onboarding cost; wrong assumptions in downstream features that consume the same DTO | M3 — single round of doc edits brings the three primary docs back in sync. Add to merge gate. |
| **JPQL `SUBSTRING` non-sargable** | Low at R&D scale; high at production | Slow date-range queries on large tenants | m2 — defer to production-scale work. Note in the feature spec's Performance follow-ups. |
| **KO/VI display** falls back to EN for the new namespaces | Certain (m4) | Visible to KO/VI users on the Detail Popover; PO-acceptable per "no fabrication" rule | m4 — track translator handoff separately; no code blocker. |
| **`FirmBankingSvcType` enum drift** vs. the V60 view's `svc_type` set | Low (4 legs, no plan to add) | If a 5th leg is ever added without updating the enum, every row in that leg renders as F | M1 — add a unit test that asserts `FirmBankingSvcType.values().length == 4` (the legs the V60 view emits). The test fails if either side drifts. |
| **`FirmBankingHistoryView` `@Setter` dead surface** | Low | Confused contributor mutates fields in memory expecting Hibernate to flush | m6 — drop `@Setter` once the test sample-row helper is refactored to a builder. |

---

## 5. What was verified vs. what was not

**Verified by reading source / running queries:**
- All Java source files in the firm-banking-history backend package + the new `common/enums/FirmBankingSvcType.java`.
- All TypeScript files in `components/settings/firm-banking-history/` + the `lib/i18n/dictionaryVan.{ts,data.ts}` + `lib/i18n/scrapingResultCodes.{ts,data.ts}` + `lib/api/firmBankingHistory.ts` + `lib/api/mappers.ts` (Firm Banking History sections) + `lib/types/api.ts` (Firm Banking History sections).
- Flyway V59, V60, V61 reviewed for content matching the schema doc.
- `pg_indexes` queried against `wecmsvndev` for the four source tables (read-only, per `.claude/rules/database.md`).
- All three primary docs read end-to-end and grep-audited for stale references.
- JEX legacy source: `comm_0221_001_view.jsp`, `comm_0221_001_r001_act.jsp`, `comm_0221_001.ts`, `IDO.TB_FIRM_TRAN_R002.xml`, `WSVC.comm_0221_001_r001.xml`, `WECMS DEV/WECMS/src/wabooks/enums/TranTp.java`.

**Not verified (Phase-4 acceptance work):**
- `./gradlew clean build` not executed (Java 11 toolchain locally per memory `project_local_java_toolchain` — would fail anyway because of B1).
- `npm run lint` / `tsc --noEmit` not executed.
- EXPLAIN against the V60 view at production-shape data not executed.
- Live wire round-trip not executed.

---

## 6. Recommendation

Fix **B1** before any other work — it is one literal in one test, ~30 seconds of work. After that, M1–M4 are all small mechanical fixes (one new test class, one new `jsonPath` line, two short doc updates, one row in `service-mapping.md`). Once those land, this feature should be **APPROVED-WITH-FOLLOWUPS** for the m1–m6 follow-ups, none of which block merge but should each get a tracking ticket.

The Phase 1–3d code itself is well-structured: the leg-specific resolver split (`dictionaryVan` for F, `scrapingResultCodes` for S/G/B, `FirmBankingServiceNameResolver` for service names, `FirmBankingSvcType` for the leg discriminator) is cleanly factored, the wire format is consistently snake_case, the View Setting non-applicability is explicitly guarded, and the legacy `SERVICE_NAME_MAP` retirement is genuine — there is zero hardcoded service map left on the frontend. The issues found are all surface-level: a single arity bug in a test, missing test coverage on the newest enum, and doc drift around the most recently added wire field.
