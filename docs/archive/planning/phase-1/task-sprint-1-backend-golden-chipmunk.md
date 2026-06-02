# Sprint 1 Backend — Banking + Add-on Service Refactor (PLAN)

**Sprint:** 1 of 4 (`23 Apr – 6 May 2026`) · **Stage:** Plan-mode only — no code yet.
**Target:** wecms-api/ (Needs Confirmation) — Spring Boot 3.5.13 / Java 21 / local PostgreSQL.
**Scope:** 9 Banking screens + 3 Add-on Service screens = 12 menus.
**Deliverable on approval:** two markdown documents (paths below). Code work happens in subsequent turns *after* this plan is signed off.

> **Plan-mode note:** Per the workflow, only this file (`task-sprint-1-backend-golden-chipmunk.md`) was edited during planning. The two final docs the user requested will be created on approval via ExitPlanMode.

> **2026-04-29 Plan-Mode Double-Check (this revision):** The Sprint 1 frontend
> static UI (12 menus) is ~90% complete in `wecms-web/`. This pass
> re-checks the original plan against (a) the actual local DB state,
> (b) the existing Flyway migrations V1–V12, (c) the legacy JEX DEV schema,
> and (d) the static UI contracts the frontend already locked in. New
> sections **§11 Double-Check Findings**, **§12 Backend Implementation
> Decision**, and **§13 Prompt 2 — Risk-Reduced Implementation Plan**
> capture the corrections. The original §1–§10 below are kept intact so
> the diff is auditable; where they conflict with the new sections,
> §11–§13 win.

---

## 1. Context

Mr. Jung's 2026-04-23 directive (see [wecms-menu-refactoring-timeline.md](../../../../../docs/jex/features/wecms-menu-refactoring-timeline.md)) compresses the full menu tree into 8 dev days. Sprint 0 already locked the foundation in wecms-api/ (Needs Confirmation) (auth, JWT, tenant context, `ApiResponse<T>`, `BaseEntity`, Flyway V1–V10). Sprint 1 must now deliver:

- **Banking** (Sonny) — 9 screens: VND/FCY account inquiry + transaction history, Own Account Transfer + Transfer History, 3 Reports.
- **Add-on Service** (Sonny) — 3 screens: Sales/Expense e-Invoices, Operation Status Inquiry.

The legacy JEX side is the **functional source of truth**, but ~half of these screens **don't read directly from a normal DB table**. They're fed by:

| Source | Screens affected |
|---|---|
| Live NXISAS local-PC engine (WebSocket + Seed-CBC) | `fnsh_0001_001`, `fnsh_0005_001` (status fields), `einv_0001_001`, `einv_0002_001` (invoice ingestion) |
| BCS batch jobs (`REPT_0200_001`, `REPT_0300_001`) | `rept_0002_001`, `rept_0003_001` |
| OSMC HTTP gateway → Vietnam tax authority (`TRCO_TRCO001_00003`) | `oprt_0001_001` |
| Pure DB read | `fnsh_0002_001`, `fnsh_0006_001`, `trns_0001_001`, `trns_0003_001`, `rept_0001_001` (nav only) |

Because R&D is **localhost-only** with no live engine/bank/tax integration, every external call must sit behind an adapter interface with a **mock implementation** for local dev. That decision is the central architectural choice of this sprint.

---

## 2. Recommended Approach

### 2.1 Package layout — extend the existing `domain/{module}/` convention

Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §Architecture Rules — new feature code goes under `domain/{jex-module-code}/`. Sprint 1 introduces three new domain modules + one shared adapter package.

```
com.kosign.wecms
├── common/
│   └── adapter/                          ← NEW — only after a 2nd domain consumes them
│       └── (will be created in Sprint 2 if needed; in Sprint 1 adapters live per-domain)
└── domain/
    ├── auth/                             ← existing, untouched
    ├── banking/                          ← NEW
    │   ├── controller/
    │   │   ├── AccountController         (fnsh_0001 / fnsh_0005)
    │   │   ├── TransactionController     (fnsh_0002 / fnsh_0006)
    │   │   ├── TransferController        (trns_0001 / trns_0003)
    │   │   └── ReportController          (rept_0001 / 0002 / 0003)
    │   ├── service/                      (one service per controller)
    │   ├── repository/                   (Spring Data JPA, derived names)
    │   ├── entity/                       (column-for-column legacy mapping)
    │   ├── dto/                          (Java records, @JsonProperty UPPER_SNAKE)
    │   └── adapter/
    │       ├── nxisas/                   ← live-engine port
    │       │   ├── NxisasEngineClient    (interface)
    │       │   └── MockNxisasEngineClient (@Profile("local"))
    │       ├── bank/
    │       │   ├── BankClient            (interface — Vietin/Shinhan/Vietcom/Woori/IBK)
    │       │   └── MockBankClient        (@Profile("local"))
    │       └── reportbatch/
    │           ├── ReportBatchClient     (interface — wraps BCS calls)
    │           └── MockReportBatchClient (@Profile("local"))
    ├── einvoice/                         ← NEW
    │   ├── controller/EInvoiceController (einv_0001 / einv_0002)
    │   ├── service/EInvoiceService
    │   ├── repository/
    │   ├── entity/                       (BUSA_DOC_MSTR, EINV_FEE_DETL, EINV_TAX_DETL)
    │   ├── dto/
    │   └── adapter/
    │       ├── EInvoiceEngineClient      (interface — wraps NXISAS for invoice ingest)
    │       └── MockEInvoiceEngineClient  (@Profile("local"))
    └── operation/                        ← NEW
        ├── controller/OperationStatusController (oprt_0001)
        ├── service/OperationStatusService
        ├── repository/
        ├── entity/                       (OPRT_STTS_INFO, EXT_TRAN_CALL_HIS)
        ├── dto/
        └── adapter/
            ├── TaxAuthorityClient        (interface — VN tax registration)
            └── MockTaxAuthorityClient    (@Profile("local"))
```

**Why module-local adapters first?** CLAUDE.md rule: *"Do not promote helpers to `common/` until more than one domain consumes them."* `NxisasEngineClient` could in theory be shared between Banking and E-Invoice — but the contracts diverge (status polling vs. invoice ingestion), so they're separate interfaces under their respective domains.

### 2.2 Adapter strategy

Every external integration is an interface with two implementations:

| Interface | Real impl (Sprint 2+) | Mock impl (Sprint 1) | Selection |
|---|---|---|---|
| `NxisasEngineClient` | `NxisasWebSocketClient` (WebSocket + ISASSeedCBC) | `MockNxisasEngineClient` returns canned status | `@Profile("local")` on mock; real impl `@Profile("!local")` later |
| `BankClient` | One impl per bank in Sprint 2 | `MockBankClient` returns fixture transfer responses | Same |
| `ReportBatchClient` | Wraps legacy BCS `REPT_0200_001` / `REPT_0300_001` | `MockReportBatchClient` reads from seeded summary tables | Same |
| `EInvoiceEngineClient` | NXISAS WebSocket + Seed-CBC (re-uses NX) | `MockEInvoiceEngineClient` reads JSON fixtures | Same |
| `TaxAuthorityClient` | OSMC HTTP via `WABOOKSOSMCAPIUtil` port | `MockTaxAuthorityClient` returns canned company-status JSON | Same |

**Profile wiring:** Spring's `@ConditionalOnProperty` or `@Profile("local")` selects the mock automatically. R&D `application-local.yml` keeps `local` profile active (it already does).

**Mock data strategy** — Sprint 1 mocks should be deterministic, not random:
- Fixtures live in `src/main/resources/fixtures/{adapter}/{scenario}.json`
- Each mock returns the fixture matching its input (account_no, tax_code, etc.)
- Fixtures are *commit-tracked* — they double as integration test golden files
- For DB-backed reads (rept_0002, rept_0003), seed `BUSI_COND_DAILY_SUMR`, `SELL_HIS_DAILY_SUMR`, `OPRT_STTS_INFO` via Flyway `V11__sprint1_seed_summary_tables.sql` rather than mock the adapter

### 2.3 Database — one Flyway migration per domain

Per CLAUDE.md *"Flyway is the only sanctioned schema channel."* Sprint 1 ships these migrations (numbers tentative — bumped if Sprint 0 patches land first):

| Version | Content |
|---|---|
| V11 | Banking schema: `ACCT_MSTR`, `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM`, `BUSA_DOC_RCV_PAY_HIS`, `ACCT_TRNS_RCV_PAY_EVID_FILE` + indexes |
| V12 | Reports schema: `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `BAT_CALL_HIS` |
| V13 | E-Invoice schema: `BUSA_DOC_MSTR` already partially exists from V5 — extend with `EINV_FEE_DETL`, `EINV_TAX_DETL`, missing e-invoice columns |
| V14 | Operation Status schema: `OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS` |
| V15 | Sprint 1 seed: 5–10 fixture rows per table for demo + UI showcase |

**Key rule:** every column name in the new entities MUST match the legacy JEX table column-for-column. Any column we *invent* must be flagged `NEEDS_CONFIRMATION` in the deliverable docs and verified via the read-only DEV `postgres` MCP before the migration is finalized.

### 2.4 API contract — keep the JEX/WSVC field shape

The Next.js frontend expects the legacy WSVC shape (UPPER_SNAKE field names). Per CLAUDE.md: *"Keep DTO JSON field names in UPPER_SNAKE matching the legacy JEX / WSVC contract."* Endpoint paths follow REST under `/api/v1/{module}/...` (matching the timeline doc §11 mapping). Response envelope is always `ApiResponse<T>` with `COMMON_HEAD` + `REC`.

---

## 3. Deliverable Documents (created on approval)

Two files per the user's prompt — created **after** ExitPlanMode is approved.

### 3.1 `docs/sprint-1-banking-addon-backend-plan.md` — analytical plan

Sections:
1. **Summary table** — all 12 menus × {service-id, data-source-type, endpoints, primary tables, adapter needed, mock strategy}
2. **Per-menu detail** — for each of the 12 menus, the 15 fields the user requested:
   1. Legacy file map (JSP / act.jsp APIs / TS / WSVC / IDO / DAO / helper)
   2. Related legacy APIs (r001/c001/u00x/d001 list with one-liner each)
   3. Related DB tables (actual names)
   4. Data source type (DB / scraping / engine.exe / bank / tax / batch / mocked)
   5. Required Spring Boot endpoint list (path + method + auth)
   6. DTO request/response design (record fields + JsonProperty names)
   7. Entity/table design (reuse vs new) with NEEDS_CONFIRMATION on uncertain columns
   8. Repository query design (derived names; @Query only when needed)
   9. Service logic design (steps, transactions, JEX path cited per CLAUDE.md style rule)
   10. Error handling (WBE codes — additions to messages.properties)
   11. Mock strategy (fixture path + scenario list)
   12. Security/auth requirement (always JWT-protected; tenant via TenantContext)
   13. Frontend API contract (the exact JSON shape Next.js will consume)
   14. Implementation order (entity → repo → service → adapter → controller → test)
   15. Risk list (per-menu)
3. **Endpoint contract table** — flat list of all Sprint 1 endpoints with request/response field names
4. **DB/table dependency table** — table → consuming menus → migration version
5. **Adapter/mock strategy** — full inventory + profile wiring + fixture format
6. **Implementation phase order** — see §4 below
7. **Open questions / NEEDS_CONFIRMATION** — see §5 below
8. **Risk list** — see §6 below

### 3.2 `docs/sprint-1-banking-addon-backend-checklist.md` — implementation checklist

A flat tickable checklist (`- [ ]` items) grouped by phase. Each item is small enough that a single Claude Code commit closes it. Phases below.

---

## 4. Implementation Phase Order

Phase ordering is dictated by **dependency** (entities first, controllers last) and **risk frontloading** (uncertain tables get confirmed before they're written).

| # | Phase | What lands | Why first/last |
|---:|---|---|---|
| 1 | **Schema confirmation** | Use `postgres` MCP (read-only DEV) to verify every legacy column name + type for the 9 tables we'll mirror. Resolve every NEEDS_CONFIRMATION. | Blocks every entity/migration. |
| 2 | **Flyway migrations V11–V15** | DDL + seed | Hibernate `ddl-auto: validate` will refuse to start without these. |
| 3 | **Entities + repositories** | All 9 tables × entity + repo, no business logic | Pure mechanical translation; Claude generates fast. |
| 4 | **Adapter interfaces + mocks** | 5 client interfaces + 5 `@Profile("local")` mocks + fixture JSON | Services depend on these to compile. |
| 5 | **Pure-DB services + controllers** | `fnsh_0002_001`, `fnsh_0006_001`, `trns_0003_001` (fnsh_0002 is the CRUD template the rest reuse — lock its shape first) | Lowest risk; locks the "JEX-port" service template. |
| 6 | **Mixed services** | `fnsh_0001_001`, `fnsh_0005_001` (DB + NxisasEngineClient mock) | Validates adapter wiring. |
| 7 | **Transfer wizard** | `trns_0001_001` (DB + BankClient mock) | Mock bank flow; multi-step state in service. |
| 8 | **Report services** | `rept_0001_001` (nav stub), `rept_0002_001`, `rept_0003_001` (read seeded summary tables) | Read-only on seeded data. |
| 9 | **E-Invoice services** | `einv_0001_001`, `einv_0002_001` (read BUSA_DOC_MSTR; mock NX ingest) | Re-uses BUSA_DOC_MSTR which is partially in V5. |
| 10 | **Operation Status service** | `oprt_0001_001` (read OPRT_STTS_INFO; mock TaxAuthorityClient) | Last because OSMC contract is shakiest. |
| 11 | **Tests + messages.properties + frontend contract handoff** | `@SpringBootTest` smoke per controller, new WBE codes, contract doc for Sonny's Next.js work | Caps the sprint. |

---

## 5. Open Questions / NEEDS_CONFIRMATION

These will be enumerated in the deliverable plan doc; calling them out here so the user can answer them up-front and reduce Sprint 1 churn.

| # | Question | Why it matters |
|---:|---|---|
| Q1 | **Confirm `wecms_dev` (local) currently has the 9 banking/e-invoice/operation tables present?** Or do we Flyway-seed them from scratch? | Determines whether V11–V14 are CREATE TABLE or ALTER. |
| Q2 | **`trns_0003_001` is a stub in legacy** (action JSP returns empty result). Do we (a) infer the contract from `trns_0001_001`'s output and design a transfer-history table (likely `TRNS_HSTR`), or (b) ship a stub that returns `[]` until JEX fills the gap? | Affects scope; option (a) adds ~0.5 dev-day. |
| Q3 | **`rept_0001_001` is navigation only** — does the Next.js frontend need any backend endpoint at all, or is it purely a client-side route? | If purely client, no backend work; remove from §3 endpoint table. |
| Q4 | **NXISAS engine — Sprint 1 mock or skip?** Real NXISAS is a local-PC EXE we can't run in R&D. Mock returns canned status fields. Frontend will show "engine offline" indicator. Acceptable? | Sets demo expectations with Mr. Jung. |
| Q5 | **OSMC API contract** — `TRCO_TRCO001_00003` request/response shape comes from `TrcoHelper.java` only. Is there a published OSMC spec we should target, or is reverse-engineering from `TrcoHelper` the source of truth? | Determines mock fidelity. |
| Q6 | **BCS batch jobs `REPT_0200_001` / `REPT_0300_001`** — Sprint 1 only reads the *result* tables (`BUSI_COND_DAILY_SUMR`, `SELL_HIS_DAILY_SUMR`). The batch itself is out of scope until Sprint 2/3. Confirm? | Caps Sprint 1 effort. |
| Q7 | **`ACCT_TRNS_HSTR` columns** — legacy has receipt/payment EXISTS subquery against `ACCT_TRNS_RCV_PAY_INFM` + `BUSA_DOC_RCV_PAY_HIS`. Translate to a JPA Specification, a derived `existsBy…` method, or a native query? | Influences repo style. Default proposal: derived `existsBy` on `AcctTrnsRcvPayInfmRepository`. |
| Q8 | **Tenant filter on banking tables** — confirm every Sprint 1 table has a `BIZ_KEY` column (per CLAUDE.md tenant rule). If any don't, what's the join path back to `BIZ_KEY`? | Tenant-isolation invariant; non-negotiable per CLAUDE.md. |
| Q9 | **`@JoinColumn` cross-aggregate FKs in entities** — CLAUDE.md says no JPA cascade unless concrete need; do we map FK columns as plain `@Column` `String` IDs only (loose coupling, fewer surprises)? Default: yes. | Affects all 9 entities. |
| Q10 | **Pagination DTO** — Transaction History tables can be large. Sprint 0 has no pagination DTO yet. Introduce one in Sprint 1, or page only via `?from=&to=&limit=` query params for now? | Default proposal: introduce `PageRequest` / `PageResponse<T>` records in `common/response/`. |

---

## 6. Risks

| Risk | Likelihood | Impact | Mitigation |
|---|:-:|:-:|---|
| Schema drift between legacy DEV and local mirror — entities won't validate against DDL | Med | High | Phase 1 confirms via `postgres` MCP before V11 lands |
| NXISAS engine mock too thin — frontend has nothing realistic to render | Med | Med | Seed 5–10 fixture invoices in V15; mock returns these |
| OSMC contract reverse-engineered wrong | Low | Low | Mock-only in Sprint 1; real impl deferred |
| Composite-key entities (`BUSI_COND_DAILY_SUMR_DETL` extends `…Pk` extends `…Pk`) — multi-level inheritance won't translate cleanly to JPA `@IdClass` | Med | Med | Flatten to single `@IdClass` per entity; no inheritance chain |
| `BUSA_DOC_MSTR` already in V5 (auth seed) but Sprint 1 extends its column set — migration ordering | Low | High | V13 = `ALTER TABLE` not `CREATE TABLE`; verify column gaps via MCP before writing |
| Tenant `BIZ_KEY` not present on `EXT_TRAN_CALL_HIS` (it's an audit log, not tenant-scoped) | Med | Low | Document as exception; service layer enforces by joining via `SVC_ID` ownership |
| Pagination over `ACCT_TRNS_HSTR` — local seed has only 5–10 rows, but real legacy has millions; performance shape is invisible | Med | Low | Out of scope for R&D; flag as Sprint 2+ concern |
| Adapter mocks committed without `@Profile("local")` guard — would silently hit nothing in a non-local profile | Low | High | Code-review checklist item; checklist doc has explicit task |
| `trns_0001_001` 10-modal wizard state — backend needs to choose stateful (server-stored draft) vs stateless (client sends full payload on submit) | Med | Med | Default: stateless; client posts the full transfer payload to a single `POST /transfers/own`. Document trade-off in plan. |
| Frontend (Next.js) and backend skew on field names if both teams freeze contracts independently | Low | Med | Plan doc §3 publishes the exact JSON shape; full-stack ownership rule means same dev owns both sides |

---

## 7. Critical Files (read-only references during implementation)

Legacy JEX functional spec:
- [WECMS DEV/WECMS/web/view/jex/wabooks/fnsh/](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/fnsh) — JSP views (fnsh_0001/0002/0005/0006)
- [WECMS DEV/WECMS/web/view/jex/wabooks/trns/](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/trns) — transfer JSPs
- [WECMS DEV/WECMS/web/view/jex/wabooks/rept/](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/rept) — report JSPs
- [WECMS DEV/WECMS/web/view/jex/wabooks/einv/](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/einv) — e-invoice JSPs
- `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/{fnsh,trns,rept,einv}/*_act.jsp` — action JSPs (one per API)
- `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.{service-id}.xml` — request/response field schema
- `WECMS DEV/WECMS/src/wabooks/helper/{Fnsh,Trns,Rept,Trco}Helper.java` — business logic to port
- `WECMS DEV/WECMS/src/bcs/jex/wabooks/oprt/OPRT_0001_001.java` — OSMC integration reference

wecms-api conventions to follow:
- [wecms-api/CLAUDE.md](../../../../CLAUDE.md) — architecture rules
- [wecms-api/src/main/java/com/kosign/wecms/common/response/ApiResponse.java](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) — envelope shape
- [wecms-api/src/main/java/com/kosign/wecms/common/entity/BaseEntity.java](../../../../src/main/java/com/kosign/wecms/common/entity/BaseEntity.java) — audit columns to extend
- [wecms-api/src/main/java/com/kosign/wecms/domain/auth/](../../../../src/main/java/com/kosign/wecms/domain/auth/) — reference module to mirror
- [wecms-api/src/main/resources/db/migration/](../../../../src/main/resources/db/migration/) — Flyway V1–V10 examples

---

## 8. Verification (post-implementation, not part of plan-mode)

The deliverable plan doc will include this verification section, summarized here so the user knows what "done" looks like before approving:

1. **Build:** `./gradlew build` green (Hibernate validate passes against V11–V15 schema).
2. **Tests:** `./gradlew test` green — at minimum one `@SpringBootTest` smoke per controller, mocks injected via profile.
3. **Boot:** `./gradlew bootRun --args='--spring.profiles.active=local'` starts on `:8080` against `localhost:5434/wecms_dev`.
4. **API smoke (curl):** for each of the 12 menus, one happy-path curl call returning `COMMON_HEAD.ERROR=false` and a non-empty `REC` from seed data.
5. **Frontend contract:** Sonny imports the OpenAPI/Axios stub on the Next.js side and confirms field names match — signed off by Sonny in PR review.
6. **MCP read-only verification (optional):** spot-check 1–2 entities against the live DEV schema via the `postgres` MCP to confirm column parity.

Mr. Youvita then runs the responsive QA matrix on Days 9–10.

---

## 9. What's NOT in this plan (out of scope)

- Real NXISAS WebSocket implementation — mock only, real impl deferred.
- Real bank adapters (Vietin/Shinhan/Vietcom/Woori/IBK) — mocks only.
- Real OSMC HTTP client — mock only.
- BCS batch job port (`REPT_0200_001` / `REPT_0300_001`) — Sprint 2/3.
- Pagination performance tuning — local seeds are small.
- SVN / Jex Studio sync — not applicable to refactor tree.
- DEV/UAT/PROD deploy — R&D is localhost + GitLab only.
- Cross-tenant data — single-tenant pinned to `VN_VNM0004_000001`.
- Auth changes — reuse Sprint 0's JWT + TenantContext as-is.

---

## 10. Approval flow

1. User reviews this plan file.
2. User approves via ExitPlanMode.
3. I create the two deliverable documents:
   - docs/sprint-1-banking-addon-backend-plan.md (Needs Confirmation)
   - docs/sprint-1-banking-addon-backend-checklist.md (Needs Confirmation)
4. User reviews the deliverables.
5. Implementation work (entities, repos, adapters, controllers, tests) begins in subsequent sessions — **strictly after** explicit user confirmation, per the prompt's "Do NOT implement code until I confirm" rule.

---

## 11. Plan-Mode Double-Check Findings (2026-04-29)

The original §1–§10 was written before V11/V12 landed and before the
static UI froze its data contracts. These corrections supersede the
relevant parts above.

### 11.1 Migration version numbers were wrong

`wecms-api/src/main/resources/db/migration/` already contains:

| File | Purpose |
|---|---|
| `V11__empl_infm_busi_admn_infm.sql` | Phase 1 EMPL_INFM + BUSI_ADMN_INFM bootstrap, `sq_biz_key` sequence, `wb_get_next_val` function. Idempotent — safe on legacy-imported DBs. |
| `V12__align_empl_infm_dates_to_legacy.sql` | Re-aligns `EMPL_INFM.CMPN_JOIN_DATE` / `CMPN_FIRE_DATE` to legacy `VARCHAR(8)` so Hibernate validates against legacy-imported tenant. |

Sprint 1 migrations therefore start at **V13**, not V11. The §2.3 table
must be re-numbered to V13–V17. See §13.4 for the corrected version
list.

### 11.2 Local DB only has 7 auth-related tables

Verified via `mcp__postgres_local__query`:

```
busi_admn_infm, busi_infm, empl_infm, flyway_schema_history,
user_busi_conn_infm, user_infm, user_login_his
```

None of the banking, e-invoice, or operation tables exist locally.
Sprint 1 migrations are pure `CREATE TABLE IF NOT EXISTS` — they will
**add** new tables and **not touch** existing data. The "skip when
present" requirement from the user is satisfied by:

- `CREATE TABLE IF NOT EXISTS …` for every new table.
- `INSERT … ON CONFLICT DO NOTHING` for every seed row.
- No `ALTER TABLE` on existing auth/empl tables.

This pattern is already established by V11 — copy that idempotence
contract for V13–V17.

### 11.3 All proposed legacy tables exist in JEX DEV

Verified via `mcp__postgres_dev__query` against `wecmsvndev` schema —
every table the plan proposes to mirror is present, with column counts
in parentheses:

```
acct_mstr (48)            acct_trns_hstr (28)
acct_trns_rcv_pay_infm (37)   acct_trns_rcv_pay_evid_file (13)
busa_doc_mstr (100)       busa_doc_rcv_pay_his (14)
busi_cond_daily_sumr (12)     busi_cond_daily_sumr_detl (16)
sell_his_daily_sumr (15)      sell_his_daily_sumr_detl (18)
einv_fee_detl (9)         einv_tax_detl (10)
oprt_stts_info (11)       ext_tran_call_his (14)   bat_call_his (12)
```

**OVERRIDDEN 2026-04-29 (legacy schema parity audit) — see §14 below.**
The original guidance below ("Sprint 1 maps only the columns the
frontend reads") was wrong for the DB schema. Partial legacy tables
break legacy data import and Sprint 2+ features. The new rule:

- **DB schema**: must match DEV column-for-column (all 100
  `busa_doc_mstr` columns, all 48 `acct_mstr` columns, etc.).
- **JPA entity**: may continue to map only the Sprint 1 column subset.
  Hibernate `validate` ignores DB columns the entity doesn't declare.
- **Seed data**: may insert only the columns the demo flow needs,
  provided unselected columns are nullable or have a DEFAULT.

Original (incorrect) text retained for diff visibility:

> ~~`busa_doc_mstr` is **100 columns** — copying it column-for-column for~~
> ~~Sprint 1 is excessive. The static UI uses only ~12 fields (date, time,~~
> ~~invoiceNo, invoiceType, buyerName, mstCode, buyerStatus, currency,~~
> ~~totalAmount, invoiceStatus, taxAmount, amountBeforeTax). Sprint 1 maps~~
> ~~**only** the columns the frontend reads; the remaining 88 stay~~
> ~~unmapped. Document this in the entity Javadoc and bring the gap up in~~
> ~~Sprint 2 if/when other modules need them.~~

### 11.4 trns_0003_001 is NOT a stub

The original §5 Q2 claimed `trns_0003_001` is a stub. Reading
[trns_0003_001_r001_act.jsp](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp)
shows it queries the `NAPAS_REMIT_TRNS_HSTR` IDO (`R003` + `R003_CNT`).
NAPAS is the Vietnamese real-time inter-bank remittance system, so
this table holds historical transfer records. Q2 in §5 should be
**withdrawn** — the table exists, the contract is real, and Sprint 1
just needs to mirror the columns the static UI reads (Date/Time, From
Bank, To Bank, Amount, Remarks, Results). See §13 for the corrected
table list.

### 11.5 The 4-vs-3 reports mismatch

§1 says "3 Reports". The static UI in
`wecms-web/app/(dashboard)/banking/reports/` has **four** routes:

```
cash-flow/  daily-cash/  daily-financial/  daily-financial-hs/
```

The HS variant is the HAENGSUNG-customer-specific layout
(`rept_0017_001`); the CMC variant maps to `rept_0015_001`. Both are
shipped as static UI in Sprint 1, so the backend either supports both
now or supports neither now and ships both behind a stub.

Decision below in §12: support **all four** report routes via the
same `ReportController` reading from seeded summary tables, but ship
HS as a thinner stub (single mock dataset) until §13's checklist
flushes the column shape.

### 11.6 rept_0001_001 = navigation hub, not an endpoint

There is **no** `rept_0001_001_*_act.jsp` under `WECMS DEV/.../action/jex/wabooks/rept/`
— only a view. The frontend already has separate routes for
`/banking/reports/{cash-flow|daily-cash|daily-financial|daily-financial-hs}`.
There is no top-level "/banking/reports" landing in Sprint 1 frontend —
it goes straight to a child via the sidebar. So `rept_0001_001` needs
**zero backend work**. Q3 in §5 is now answered: drop it from the
endpoint table.

### 11.7 Frontend type names are camelCase, contract is UPPER_SNAKE

The static UI's TypeScript types use camelCase
(`accountNo`, `companyName`, `taxCode`). Per
[wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) §"API / DB Rules"
and the existing `LoginResponse`, the **wire contract** is UPPER_SNAKE
(`ACCT_NO`, `COMPANY_NM`, `TAX_NO`) — the frontend will run a
field-mapper before rendering. Sprint 1 backend therefore emits
UPPER_SNAKE, mirroring the `WSVC` field names the legacy system uses.
A short mapper table per page goes into the deliverable plan doc so
Sonny can wire `wecms-web` lib functions in the same MR.

### 11.8 Bank/NXISAS/OSMC adapters: needed?

Original plan §2.2 specified five adapter interfaces. Re-checking
against what the static UI actually renders:

| Adapter | Static UI need | Sprint 1 outcome |
|---|---|---|
| `NxisasEngineClient` | UI shows mock account balances + a "last update" timestamp. **No live engine status field rendered.** | **Defer.** Backend returns `lastUpdate` from `ACCT_MSTR.LAST_UPD_DTM` (DB read). No adapter needed. |
| `BankClient` | Transfer Own Accounts page lists accounts; transfer dialog is UI-only. **No bank-side execution wired.** | **Defer.** Sprint 1 has no transfer-execution endpoint — see §12. |
| `ReportBatchClient` | Report pages render seeded summary rows. | **Defer.** Sprint 1 reads from seeded tables; the BCS batch that fills them is Sprint 2/3. |
| `EInvoiceEngineClient` | Sales/Expense pages render rows. **No live ingestion control on the page.** | **Defer.** Backend reads `BUSA_DOC_MSTR` joined with `EINV_*` tables. |
| `TaxAuthorityClient` | Operation Status page renders a list with `lastUpdate`. **No "Refresh" button visible in the static UI.** | **Defer.** Backend reads `OPRT_STTS_INFO`. |

Net: **drop all five adapter interfaces from Sprint 1.** Reintroduce
when a feature actually calls a remote system. This is the single
biggest scope reduction vs. the original plan and is what makes the
"backend support 90%-done static UI" target tractable in 8 dev days.

### 11.9 Tenant `BIZ_KEY` audit

Per CLAUDE.md, every tenant-scoped query constrains on
`BIZ_KEY = wecms.tenant.biz-key`. Verified column presence on the
proposed Sprint 1 tables (DEV schema):

| Table | `BIZ_KEY`? |
|---|---|
| `acct_mstr` | yes |
| `acct_trns_hstr` | yes |
| `acct_trns_rcv_pay_infm` | yes |
| `busa_doc_mstr` | yes |
| `busa_doc_rcv_pay_his` | yes |
| `busi_cond_daily_sumr(_detl)` | yes |
| `sell_his_daily_sumr(_detl)` | yes |
| `einv_fee_detl` / `einv_tax_detl` | yes (joined via `BUSA_DOC_MSTR`) |
| `oprt_stts_info` | yes |
| `ext_tran_call_his` | **no** — audit log; service layer enforces tenant via `SVC_ID` ownership |
| `bat_call_his` | yes |

`ext_tran_call_his` exception matches the original plan §6 risk row
and is acceptable for an audit-log table. Document the
non-tenant-scoped repository explicitly so it cannot be reused
casually.

### 11.10 ApiResponse / DTO conventions confirmed compatible

- `ApiResponse<T>` envelope (`COMMON_HEAD` + `REC`) is in place at
  [common/response/ApiResponse.java](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java).
  All Sprint 1 endpoints reuse it as-is.
- `BaseEntity` (REG_ID/REG_DTM/MOD_ID/MOD_DTM) is the audit base for
  any new entity that has those columns. `OPRT_STTS_INFO` and
  `EXT_TRAN_CALL_HIS` keep their own audit pattern — verify before
  inheriting.
- DTOs are Java records with `@JsonProperty("UPPER_SNAKE")` —
  matches the existing Sprint 0 contract.
- WBE codes go into `messages.properties` with the page added; never
  reuse JEX ML codes verbatim (CLAUDE.md i18n rule).

---

## 12. Backend Implementation Decision

For each of the 13 frontend routes that the static UI actually
exposes (§11.5 explains why "12 menus" rounds up to 13 routes), this
is the binding decision for Sprint 1. The deliverable plan doc
restates this table as its summary.

| # | Frontend route | Legacy service | Decision |
|---:|---|---|---|
| 1 | `/banking/account-inquiry` | `fnsh_0001_001` | **Implement now** (DB read of `ACCT_MSTR`) |
| 2 | `/banking/account-inquiry-fcy` | `fnsh_0005_001` | **Implement now** — same controller, different `CCY_CD` filter |
| 3 | `/banking/transaction-history` (+ per-account `[accountId]`) | `fnsh_0002_001` | **Implement now** (DB read of `ACCT_TRNS_HSTR`) |
| 4 | `/banking/transaction-history-fcy` (+ per-account) | `fnsh_0006_001` | **Implement now** — same controller, FCY filter |
| 5 | `/banking/transfer-own-accounts` | `trns_0001_001` (10-step wizard in legacy) | **Mock/stub now** — return account list only. **Defer execution.** Wizard frontend dialog stays mock. |
| 6 | `/banking/transfer-history` | `trns_0003_001` (NAPAS_REMIT_TRNS_HSTR) | **Implement now** (DB read with date-range + status filter) |
| 7 | `/banking/reports/daily-cash` | `rept_0009_001` | **Implement now** — read seeded `BUSI_COND_DAILY_SUMR(_DETL)` |
| 8 | `/banking/reports/daily-financial` (CMC) | `rept_0015_001` | **Implement now** — same summary tables, different aggregation |
| 9 | `/banking/reports/daily-financial-hs` (HAENGSUNG variant) | `rept_0017_001` | **Mock/stub now** — return seeded HS dataset; full ledger query deferred |
| 10 | `/banking/reports/cash-flow` | `rept_0009_001_r001` (variant) or downstream BCS | **Mock/stub now** — return seeded multi-month grid |
| 11 | `/addon/sales-einvoices` | `einv_0001_001` | **Implement now** — read `BUSA_DOC_MSTR` joined with `EINV_TAX_DETL` filtered to "sales" type |
| 12 | `/addon/expenses-einvoices` | `einv_0002_001` | **Implement now** — same query, "expense" filter |
| 13 | `/addon/operation-status` | `aosv_0001_001` | **Implement now** — DB read of `OPRT_STTS_INFO` |
| — | (none — sidebar nav only) | `rept_0001_001` | **No backend needed** — frontend has no `/banking/reports` index page |

### Implement now (8 endpoints)

Pure DB-read endpoints that the static UI will consume immediately:

- `GET /api/v1/banking/accounts` — VND + FCY share one endpoint with a
  `currency` query param. (#1, #2)
- `GET /api/v1/banking/accounts/{accountId}/transactions` — VND + FCY
  share one endpoint. (#3, #4)
- `GET /api/v1/banking/transfers/history` (#6)
- `GET /api/v1/banking/reports/daily-cash` (#7)
- `GET /api/v1/banking/reports/daily-financial` (#8)
- `GET /api/v1/addon/einvoices?type=sales` (#11)
- `GET /api/v1/addon/einvoices?type=expense` (#12)
- `GET /api/v1/addon/operation-status` (#13)

### Mock/stub now (3 endpoints)

Backend ships the endpoint and returns a deterministic seeded payload,
but the underlying query/business logic is intentionally minimal.
Each endpoint is annotated with `// TODO: Sprint 2 — replace with real
query` in the service:

- `GET /api/v1/banking/transfers/own-accounts` — returns account list
  only (reuses Account entity); no execution. (#5)
- `GET /api/v1/banking/reports/daily-financial-hs` — returns a fixed
  HAENGSUNG demo dataset. (#9)
- `GET /api/v1/banking/reports/cash-flow` — returns a fixed multi-month
  grid. (#10)

### Defer to Sprint 2+

- **Real `trns_0001_001` 10-step transfer wizard execution.** The
  static UI's transfer dialog is mock-only; backend execution is out
  of Sprint 1 scope. No `POST /transfers/own` endpoint in Sprint 1.
- **All five adapter interfaces** (NXISAS / Bank / ReportBatch /
  EInvoiceEngine / TaxAuthority) — see §11.8.
- **BCS batch port** for `REPT_0200_001` / `REPT_0300_001`.
- **Pagination DTO promotion to `common/response/`.** Sprint 1 uses
  query params (`?from=&to=&page=&size=`) and inlines page metadata in
  each response REC. Refactor in Sprint 2 once a second domain needs
  it.
- **Auto-refresh / live status fields** on Account Inquiry — backend
  returns `LAST_UPD_DTM` from the row; "engine offline" indicator on
  the page is a frontend-only decoration.
- **`busa_doc_mstr`'s 88 unmapped columns** — pulled in only when a
  feature needs them.

### No backend needed

- `/banking/reports` index — handled by Next.js sidebar; no controller.
  (Drops Q3 from §5 and removes `rept_0001_001` from the §3 endpoint
  table.)
- All "static UI only" interactions documented in
  [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Feature
  Status" table — buttons that have no handler yet (Print, Export,
  Excel, Update, Smart Organizer, Change Password) stay frontend-only
  in Sprint 1.

---

## 13. Prompt 2 — Risk-Reduced Implementation Plan

This is the implementation plan to follow once §12 is approved. It
replaces §4 (Implementation Phase Order) and §2.3 (Database) where
they conflict.

### 13.1 Sprint 1 deliverable map (replaces §3)

Two markdown deliverables — both still produced after the user
approves this revision:

1. `docs/sprint-1-banking-addon-backend-plan.md` — analytical plan,
   re-grounded on the §12 decision matrix.
2. `docs/sprint-1-banking-addon-backend-checklist.md` — flat ticked
   checklist matching §13.5 below.

### 13.2 Endpoint contract (final, frozen for Sprint 1)

All endpoints are JWT-protected, single-tenant via
`TenantContext.currentBizKey()`, and return `ApiResponse<T>`.

| Method | Path | REC type | Notes |
|---|---|---|---|
| GET | `/api/v1/banking/accounts` | `AccountListResponse` | Query: `currency=VND\|USD\|...` (optional). REC = `{ ACCOUNTS:[…], TOTAL_COUNT, AVAILABLE_BAL, BALANCE }` |
| GET | `/api/v1/banking/accounts/{acctSeqNo}/transactions` | `TransactionListResponse` | Query: `from`, `to` (YYYYMMDD), `type=DEPOSIT\|WITHDRAW\|ALL`, `status=ALL\|CONFIRMED\|UNCONFIRMED`, `page`, `size`. REC = `{ ROWS:[…], TOTALS:{…}, PAGE:{…} }` |
| GET | `/api/v1/banking/transfers/own-accounts` | `OwnAccountListResponse` | Stub-mode; reads same accounts table. REC = `{ ACCOUNTS:[…], TOTALS:{…} }` |
| GET | `/api/v1/banking/transfers/history` | `TransferHistoryResponse` | Query: `from`, `to`, `status=ALL\|SUCCESS\|FAILED`, `page`, `size` |
| GET | `/api/v1/banking/reports/daily-cash` | `DailyCashReport` | Query: `asOf=YYYYMMDD`, `currencyScope=ALL\|VND\|FCY` |
| GET | `/api/v1/banking/reports/daily-financial` | `DailyFinancialCmcReport` | Query: `asOf`, `currencyScope` |
| GET | `/api/v1/banking/reports/daily-financial-hs` | `DailyFinancialHsReport` | Stub. Query: `asOf`, `bankCd` (optional) |
| GET | `/api/v1/banking/reports/cash-flow` | `CashFlowReport` | Stub. Query: `from`, `to` (yearMonth) |
| GET | `/api/v1/addon/einvoices` | `EInvoiceListResponse` | Query: `type=SALES\|EXPENSE`, `from`, `to`, `status`, `page`, `size` |
| GET | `/api/v1/addon/operation-status` | `OperationStatusListResponse` | Query: `q` (search), `page`, `size` |

Field-name mapping table (UPPER_SNAKE wire ↔ camelCase frontend
display) is published with the deliverable plan doc so Sonny's
Next.js MR runs in lockstep.

### 13.3 Package layout (replaces §2.1)

Keep adapters out per §11.8. Three new domains, no shared adapter
package, no `common/adapter/` introduction in Sprint 1:

```
com.kosign.wecms
└── domain/
    ├── banking/
    │   ├── controller/
    │   │   ├── AccountController        (#1, #2 — VND/FCY merge)
    │   │   ├── TransactionController    (#3, #4)
    │   │   ├── TransferController       (#5 stub, #6)
    │   │   └── ReportController         (#7, #8, #9 stub, #10 stub)
    │   ├── service/
    │   ├── repository/
    │   ├── entity/                      (only the columns the static UI reads)
    │   └── dto/                         (Java records, UPPER_SNAKE @JsonProperty)
    ├── einvoice/
    │   ├── controller/EInvoiceController (#11, #12 — type filter)
    │   ├── service/, repository/, entity/, dto/
    └── operation/
        ├── controller/OperationStatusController (#13)
        ├── service/, repository/, entity/, dto/
```

### 13.4 Migrations (replaces §2.3) — V13–V17, all idempotent

Numbers continue from the existing V12. Every migration uses
`CREATE TABLE IF NOT EXISTS …` so re-runs and legacy-imported DBs
stay safe.

| Version | Content | Idempotence |
|---|---|---|
| V13 | Banking schema — `ACCT_MSTR`, `ACCT_TRNS_HSTR`, `ACCT_TRNS_RCV_PAY_INFM`, `BUSA_DOC_RCV_PAY_HIS`, `ACCT_TRNS_RCV_PAY_EVID_FILE`, `NAPAS_REMIT_TRNS_HSTR` (only the columns Sprint 1 reads — see §11.3). | `CREATE TABLE IF NOT EXISTS` + `CREATE INDEX IF NOT EXISTS`. |
| V14 | Reports schema — `BUSI_COND_DAILY_SUMR(_DETL)`, `SELL_HIS_DAILY_SUMR(_DETL)`, `BAT_CALL_HIS`. | Same. |
| V15 | E-Invoice schema — `BUSA_DOC_MSTR` (Sprint 1 column subset), `EINV_FEE_DETL`, `EINV_TAX_DETL`. **`BUSA_DOC_MSTR` may already partially exist** if a future legacy import lands first; use `IF NOT EXISTS` and `ALTER TABLE … ADD COLUMN IF NOT EXISTS` so V15 is safe in either starting state (mirrors the V11 pattern). | Same. |
| V16 | Operation Status schema — `OPRT_STTS_INFO`, `EXT_TRAN_CALL_HIS`. | Same. |
| V17 | Sprint 1 seed — 5–10 fixture rows per table for demo. `INSERT … ON CONFLICT DO NOTHING` keyed on PK so re-runs are no-ops. Seeds are pinned to `BIZ_KEY = VN_VNM0004_000001`. | `INSERT … ON CONFLICT DO NOTHING`. |

**Verification before each migration lands:** run the
`information_schema` schema check from `.claude/rules/database.md`
against the local DB to confirm no clashes; then run the same query
read-only against DEV (`mcp__postgres_dev__query`) to confirm column
parity for any column the entity maps. Drift between DEV and the
Sprint 1 column subset is acceptable — Sprint 1 is intentionally a
subset.

### 13.5 Implementation phases (replaces §4)

Smaller, narrower phases than the original plan — each phase ends in
a green build + a passing smoke test before the next starts.

| # | Phase | Scope | Done when |
|---:|---|---|---|
| 1 | **Schema confirmation** | For every column the Sprint 1 entities map, confirm name + type via `mcp__postgres_dev__query` against `wecmsvndev`. Drop any column the static UI doesn't need. | All NEEDS_CONFIRMATION cleared in the deliverable plan doc. |
| 2 | **V13–V17 migrations** | Idempotent CREATE/ALTER with `IF NOT EXISTS` + seed via `ON CONFLICT DO NOTHING`. | `./gradlew bootRun --args='--spring.profiles.active=local'` starts; `flyway_schema_history` shows V13–V17 success rows. |
| 3 | **Entities + repositories** | One entity per table (Sprint 1 column subset only), one repository per entity, all derived methods. No `@Query` unless §13.6 risk row R3 forces it. | `./gradlew compileJava` green; `ddl-auto: validate` passes. |
| 4 | **Pure-DB services + controllers — Banking core** | Phase delivers #1, #2, #3, #4. Lock the "JEX-port" service template here. | Smoke `curl` per endpoint returns `COMMON_HEAD.ERROR=false` from seeded data. |
| 5 | **Transfer history + own-accounts stub** | #5 (stub list), #6 (real DB read). | Same smoke gate. |
| 6 | **Reports — Daily Cash + Daily Financial CMC** | #7, #8 (real reads from seeded summary tables). | Same. |
| 7 | **Reports — HS variant + Cash Flow stubs** | #9, #10 (return seeded fixed datasets, single TODO comment per service). | Same. |
| 8 | **E-Invoice services + controller** | #11, #12 (one controller, `type` filter). | Same. |
| 9 | **Operation Status service + controller** | #13. | Same. |
| 10 | **Tests + messages.properties + frontend contract handoff** | Mockito unit tests per service (no Testcontainers — defer per CLAUDE.md), one `@SpringBootTest` smoke per controller, new WBE codes in `messages.properties`, frontend mapper table published in deliverable plan. | `./gradlew test` green; Sonny accepts the contract. |

Phases 4–9 are independent enough that they can be reshuffled if a
phase is blocked, but the contracts in §13.2 freeze first regardless
of order.

### 13.6 Risks & mitigations (replaces §6)

The single biggest risk reduction vs. the original plan is **dropping
adapters**. Remaining risks:

| Risk | Lik | Imp | Mitigation |
|---|:-:|:-:|---|
| R1: Schema drift between DEV and the Sprint 1 column subset (an entity column is wrong type / nullable wrong) | Med | High | Phase 1 verifies every mapped column via `postgres` MCP. Hibernate `validate` catches the rest at boot. |
| R2: V15 conflicts with a future legacy import of `BUSA_DOC_MSTR` | Med | Med | V15 uses `CREATE TABLE IF NOT EXISTS` + `ALTER TABLE … ADD COLUMN IF NOT EXISTS` per §13.4 — same pattern as V11. |
| R3: `ACCT_TRNS_HSTR` legacy uses an EXISTS subquery against `ACCT_TRNS_RCV_PAY_INFM` + `BUSA_DOC_RCV_PAY_HIS` for the "confirmed" flag | Med | Low | Sprint 1 implements as a derived `existsBy…` on `AcctTrnsRcvPayInfmRepository` plus an in-service join. Promote to `@Query` only if the derived shape can't express it. |
| R4: Static UI camelCase types vs. UPPER_SNAKE wire | Low | Med | Mapper table published with the deliverable; Sonny's MR adds the lib mappers in lockstep. |
| R5: `EXT_TRAN_CALL_HIS` lacks `BIZ_KEY` | Med | Low | Service-layer enforcement only; document as a known exception. |
| R6: 8-day budget over-runs | Med | High | Phases 4–9 are independent; if blocked, ship the unblocked subset behind a feature flag (frontend-side) and demo the rest as static UI. |
| R7: Pagination over `ACCT_TRNS_HSTR` and `NAPAS_REMIT_TRNS_HSTR` | Low | Low | Local seed is small (5–10 rows). Performance shape is a Sprint 2+ concern. |
| R8: `BUSA_DOC_MSTR` 100-column legacy table — entity drift | Med | Med | Entity maps only the ~12 columns the UI reads; `// only Sprint 1 fields — extend as needed` comment on the entity. |
| R9: Frontend `daily-financial-hs` Figma differs from CMC — backend stub returns wrong shape | Low | Low | Stub mirrors the camelCase types in [DailyFinancialHsPage](../../../../../wecms-web/components/banking/reports/daily-financial-hs/) — verify the JSON shape against that file before merging. |

### 13.7 Open questions for the user (Q1–Q5, replaces §5)

These are the only unanswered questions after this revision —
answers unblock §13.5 phases:

- **Q1 (was Q1):** Confirm Sprint 1 starts with empty banking/e-invoice/operation tables on local. Verified via `mcp__postgres_local__query` — none of these tables exist locally. → V13–V17 are pure CREATE migrations. ✅ Already answered.
- **Q2 (was Q4):** NXISAS / Bank / OSMC / EInvoice / ReportBatch — confirm Sprint 1 ships zero of these adapters; demo will show "engine offline" is implicit. **Need user sign-off** since this is the biggest scope reduction vs. the original plan.
- **Q3 (was Q7):** `ACCT_TRNS_HSTR` "confirmed" flag implementation — derived `existsBy…` vs. native query. **Need user preference** before phase 4.
- **Q4 (was Q9):** Cross-aggregate FKs in entities — plain `@Column` String IDs (no `@JoinColumn`)? Default proposal: yes. **Need user sign-off** before phase 3.
- **Q5 (new):** `daily-financial-hs` and `cash-flow` ship as stubs. Sonny's static UI is the visual reference for both. **Confirm** Sonny is OK with stub data until Sprint 2.

The original Q2 (`trns_0003_001` stub) is withdrawn per §11.4. Q3
(`rept_0001_001` nav-only) is resolved per §11.6. Q5 (OSMC contract),
Q6 (BCS batch out of scope), Q8 (`BIZ_KEY` audit), and Q10
(pagination DTO) are resolved by §11.8 / §11.9 / §13.2.

### 13.8 Verification gate (final, replaces §8)

Before declaring Sprint 1 done:

1. **Build:** `./gradlew build` green; Hibernate `validate` passes.
2. **Migrations re-runnable:** drop the local DB, restore from V1
   bootstrap, run `./gradlew bootRun` — V1 → V17 apply cleanly without
   touching auth seed data.
3. **Smoke per endpoint:** the 11 endpoints in §13.2 each return
   `COMMON_HEAD.ERROR=false` and a non-empty REC for at least one
   tenant (`VN_VNM0004_000001`).
4. **Frontend contract:** Sonny's Next.js MR maps every UPPER_SNAKE
   field to the camelCase static-UI types; both MRs merge in the same
   PR group.
5. **Tests:** unit tests per service (Mockito) green; one
   `@SpringBootTest` smoke per controller; coverage of the "happy
   path" only — error paths land in Sprint 2.
6. **Manual visual check:** Sonny opens each of the 13 routes against
   `localhost:8080` and confirms data renders in place of the mocks.
7. **Re-verify migrations on a legacy-imported DB shape** by running
   V13–V17 against a DB cloned from the V11 idempotent bootstrap —
   confirms the "skip when present" requirement.

After verification, Mr. Youvita runs the responsive QA matrix on
days 9–10 (per the original timeline).

---

## 14. Legacy Schema Parity Audit (2026-04-29 — overrides §11.3)

This audit was triggered after I discovered the §11.3 "map only what
the static UI reads" advice had been applied to the **DB schema** as
well as to entities. Partial DB schemas break legacy data import and
Sprint 2 features, so the local schema must mirror DEV
column-for-column.

### 14.1 Audit findings — DEV vs local column counts

| Table | DEV cols | Local before audit | Missing | Local after V23 |
|---|---:|---:|---:|---:|
| `acct_mstr` | 48 | 18 | **30** | **48 ✅** |
| `acct_trns_hstr` | 28 | 18 | **10** | **28 ✅** |
| `acct_trns_rcv_pay_infm` | 37 | 21 | **17** | **38** (37 + 1 V13 surplus `DOC_ID`, harmless) |
| `acct_trns_rcv_pay_evid_file` | 13 | 11 | **7** | **18** (13 + 5 V13 surplus / harmless) |
| `busa_doc_rcv_pay_his` | 14 | 8 | **10** | **18** (14 + 4 V13 surplus / harmless) |
| **`busa_doc_mstr`** | **100** | **20** | **80** | **100 ✅** |
| `bank_infm`, `bat_call_his`, `busi_cond_daily_sumr(_detl)`, `napas_remit_trns_hstr`, `oprt_stts_info`, `sell_his_daily_sumr(_detl)`, `einv_fee_detl`, `einv_tax_detl`, `ext_tran_call_his` | match | match | 0 | match ✅ |

**Critical mismatch:** `busa_doc_mstr` had 20/100 columns. The audit-driven V23 migration adds the missing 80 (issuer address columns, payment-term metadata, project tracking, full e-invoice header set including buyer/seller signature triplets, QR code, original-invoice references, etc.).

### 14.2 Corrective migration

`V23__sprint1_legacy_schema_parity.sql` — purely additive:
- `ALTER TABLE … ADD COLUMN IF NOT EXISTS` for every legacy column missing locally.
- Numeric precision/scale matches DEV exactly (e.g. `NUMERIC(20,6)` for amounts, `NUMERIC(5,0)` for sequence numbers).
- Legacy `DEFAULT` clauses preserved (`PROV_STTS = 'OP'`, `REG_CHNL = 'PC'`, `ITEM_DETL_USE_YN = 'N'`, `HAND_REG_YN = 'Y'`, `ACCT_CUST_TYPE = 'C'`, etc.).
- Legacy NOT-NULL constraints applied **after** backfilling existing seed rows so V22's seed data doesn't violate them.
- Idempotent — safe to re-run, safe on a fresh `V1 → V23` build.

### 14.3 Schema vs. entity vs. DTO — binding rule going forward

| Layer | Sprint 1 scope |
|---|---|
| **DB schema** | Must mirror DEV column-for-column (this audit). No subsets. |
| **JPA entity** | May map only the columns Sprint 1 reads/writes. Hibernate `validate` ignores DB columns the entity doesn't declare. Document the subset in the entity Javadoc with "extend when …" notes. Sprint 1 entities (e.g. `BusaDocMstr` mapping ~16 of 100 columns) stay as-is — that's correct. |
| **DTO** | Exposes only the wire fields the API contract needs. Independent of both. |
| **Seed data** | Inserts only the columns the demo flow needs, provided unselected columns are nullable or have a DEFAULT. |

This rule supersedes any earlier "subset only" advice in §11.3 / §13 / §10.x of this plan when applied to the **DB schema**. The "subset only" advice still applies (and is the right call) to entities and DTOs.

### 14.4 Verification

- `./gradlew compileJava` — clean (entities unchanged).
- Flyway V23 applied cleanly on local DB; `flyway_schema_history` shows V23 success row.
- Hibernate `ddl-auto: validate` passes — the existing entity-mapped columns still match their declared types, and the new columns are simply ignored by the validator.
- `Started WecmsApiMainApplication in 17.889 seconds` on port 18083.
- Local column counts now match DEV for the 6 previously-mismatched tables (verified via `mcp__postgres_local__query`).

---

## 15. Constraints / Indexes / Triggers / Function Parity Audit (2026-04-29)

V23 covered columns. This second-round audit adds constraint, index, trigger, and DB-function parity. The corrective migration is V24.

### 15.1 Per-table parity report

| Table | DEV cols | Local | DEV PK | Local PK after V24 | DEV UK | Local UK | DEV CHECK | Local CHECK | DEV FK | Local FK | Trigger / function deps |
|---|---:|---:|---|---|---|---|---|---|---|---|---|
| `bank_infm` | 8 | 8 | (bank_cd, start_date, end_date) | **realigned ✓** | — | — | — | — | — | — | — |
| `acct_mstr` | 48 | 48 | (biz_key, bank_cd, acct_no) | matches ✓ | — | — | 5 | **5 ✓** | → busi_infm | **added ✓** | `acct_mstr_log` + `reject_not_existing_bank_cd` deferred |
| `acct_trns_hstr` | 28 | 28 | (biz_key, acct_seq_no) | matches ✓ | 1 (11-col) | not added (cardinality + pre-existing data) | 2 | **2 ✓** | — | — | `acct_trns_hstr_log` + `sync_to_rept_trigger` deferred |
| `acct_trns_rcv_pay_infm` | 37 | 38 (V13 surplus DOC_ID) | (biz_key, acct_seq_no, rcv_pay_seq_no) | matches ✓ | — | — | — | — | → acct_trns_hstr **+** → cash_repy_mstr | acct_trns_hstr fk **added ✓**, cash_repy_mstr fk **deferred** | `acct_trns_rcv_pay_infm_log` deferred |
| `busa_doc_rcv_pay_his` | 14 | 18 (V13 surplus 4 cols) | none in DEV | local PK on (biz_key, doc_id, seq_no) — V13 vestigial | — | — | — | — | — | — | `busa_doc_rcv_pay_his_log` deferred |
| `acct_trns_rcv_pay_evid_file` | 13 | 18 (V13 surplus 5 cols) | (biz_key, acct_seq_no, rcv_pay_seq_no, file_id) | **realigned ✓** | — | — | — | — | → acct_trns_rcv_pay_infm | **added ✓** | — |
| `napas_remit_trns_hstr` | 21 | 21 | (biz_key, seq_no) | matches ✓ | — | — | — | — | — | — | — |
| `busi_cond_daily_sumr` | 12 | 12 | (biz_key, trco_key, trsc_dt, ccy_cd, prjt_no) | matches ✓ | — | — | 1 | **1 ✓** | → busi_infm | **added ✓** | — |
| `busi_cond_daily_sumr_detl` | 16 | 16 | (biz_key, trco_key, trsc_dt, ccy_cd, dtl_seq_no, prjt_no) | matches (V13 ordering swap, semantically equivalent) | — | — | 1 | **1 ✓** | — | — | `busi_cond_daily_sumr_detl_log` deferred |
| `sell_his_daily_sumr` | 15 | 15 | 8-col PK incl. trco_cmnm/cmenm | **realigned ✓** | — | — | 1 | **1 ✓** | → busi_infm | **added ✓** | — |
| `sell_his_daily_sumr_detl` | 18 | 18 | 10-col PK incl. trco_cmnm/cmenm/item_nm | **realigned ✓** | — | — | — | — | — | — | — |
| `bat_call_his` | 12 | 12 | 6-col PK incl. req_date/mod_uid | **realigned ✓** | — | — | — | — | — | — | — |
| **`busa_doc_mstr`** | **100** | **100** | (biz_key, doc_id) | matches ✓ | 1 (biz_key, reg_dt, seq_no) | **added ✓** | 3 | **3 ✓** | → mrec_pic_store | **deferred** | `busa_doc_mstr_log` deferred |
| `einv_fee_detl` | 9 | 9 | (biz_key, doc_id, disp_ord) | matches ✓ | — | — | — | — | — | — | `einv_fee_detl_log` deferred |
| `einv_tax_detl` | 10 | 10 | (biz_key, doc_id, disp_ord) | matches ✓ | — | — | — | — | — | — | `einv_tax_detl_log` deferred |
| `oprt_stts_info` | 11 | 11 | (biz_key, taxcode) | matches ✓ | — | — | — | — | — | — | — |
| `ext_tran_call_his` | 14 | 14 | (svc_id, ref_date, seq_no) | matches ✓ | — | — | — | — | — | — | — |

**Totals**:
- 17 PRIMARY KEYs — all aligned (5 realigned by V24, 12 already matched).
- 1 UNIQUE constraint added (`busa_doc_mstr_uk01`); the 11-col `acct_trns_hstr_uk01` is not added because the V22 seed doesn't have all 11 columns populated and adding the constraint would be cosmetic until Sprint 2 writes start.
- 13 CHECK constraints added.
- 5 FOREIGN KEYs added.
- 2 FOREIGN KEYs deferred (target tables out of scope).
- 28 triggers deferred (audit-log + sync trigger functions out of scope).

### 15.2 BUSA_DOC_MSTR mismatch / fix summary

| Concern | Before V23/V24 | After V23/V24 |
|---|---|---|
| Column count | 20 | **100 (matches DEV)** |
| PRIMARY KEY | (biz_key, doc_id) | (biz_key, doc_id) — already matched |
| UNIQUE | none | `busa_doc_mstr_uk01 (biz_key, reg_dt, seq_no)` ✓ |
| CHECK constraints | none | 3 added: `chk01` (DOC_TYPE in I/B/E), `chk02` (PAY_DUE_CD in 15/30/60/00), `chk07` (TRCO_TRSC_CCY_CD in VND/USD/999/998) |
| FOREIGN KEYs | none | `fk04 → mrec_pic_store(biz_key, reg_dt, seq_no)` **deferred** — `mrec_pic_store` not migrated |
| Trigger | none | `busa_doc_mstr_logtrigger` (AFTER INSERT/UPDATE/DELETE → `busa_doc_mstr_log()` → `busa_doc_mstr_log_h`) **deferred** — audit log function + history table not migrated |
| DEFAULTs | none | Carried over for `pay_due_cd='30'`, `prov_stts='OP'`, `reg_chnl='PC'`, `item_detl_use_yn='N'`, `prjt_use_yn='N'`, `hand_reg_yn='Y'` |
| NOT NULL columns | partial | `reg_chnl`, `item_detl_use_yn`, `prjt_use_yn`, `hand_reg_yn` are NOT NULL with backfill; the seven other DEV NOT NULL columns (biz_key, doc_id, issu_tax_id, doc_type, trco_key, issu_dt, reg_id, reg_dtm, mod_id, mod_dtm) were already NOT NULL via V13 |

### 15.3 BANK_INFM source confirmation

- DEV table: `wecmsvndev.bank_infm` (8 columns, 29 rows in DEV).
- Local table: `public.bank_infm` (8 columns, 29 rows seeded by V20).
- Schema parity: ✓ identical column set.
- PK parity: now ✓ `(bank_cd, start_date, end_date)` after V24 realignment.
- All Sprint 1 banking code resolves bank-name lookups through `BankInfmRepository` — no static map. Code-level audit clean.

### 15.4 DEV seed data import summary (carried over from §14)

V22 imports the latest 25 rows per populated table for tenant `VN_VNM0004_000001`:
- `bank_infm` 29 (V20, all rows — global registry)
- `acct_mstr` 25 / 41 in DEV
- `acct_trns_hstr` 40 (25 latest + 15 confirmed-matching)
- `acct_trns_rcv_pay_infm` 15 (cnfm_yn='Y' rows matched to TX seed)
- `napas_remit_trns_hstr` 25 / 51 in DEV
- `busa_doc_mstr` 50 (25 sales DOC_TYPE='I' + 25 expense 'E')
- `oprt_stts_info` 25 / 202 in DEV

V17 stub seed retained where DEV is empty: `busi_cond_daily_sumr(_detl)`, `sell_his_daily_sumr(_detl)`, `acct_trns_rcv_pay_evid_file`. The KRW row in `busi_cond_daily_sumr` was deleted by V24 step 2 (violated `chk01`).

All seed PK values are real DEV values — no synthetic IDs generated.

### 15.5 wb_get_next_val branch audit

Verified by direct call-test (`SELECT wecmsvndev.wb_get_next_val(table, field, ...)`):

| Table | Field | Returns | Sprint 1 dependency |
|---|---|---|---|
| `busi_infm` | `biz_key` | `'VN_VNM0099_000001'` (works) | Sprint 0 bootstrap (`BusinessKeyGeneratorService`) |
| `empl_infm` | `empl_no` | `'20260429_001'` (works) | Sprint 0 bootstrap (`EmployeeNumberGeneratorService`) |
| `acct_mstr.acct_seq_no`, `busa_doc_mstr.doc_id`, `napas_remit_trns_hstr.seq_no`, `acct_trns_hstr.acct_seq_no`, `acct_trns_rcv_pay_infm.rcv_pay_seq_no`, `acct_trns_rcv_pay_evid_file.file_id`, `busi_cond_daily_sumr.dtl_seq_no`, `oprt_stts_info.taxcode`, `ext_tran_call_his.seq_no`, `bat_call_his.job_id` | — | NULL (10 of 10) | **none** |

**Result**: zero Sprint 1 tables depend on `wb_get_next_val`. Sprint 1 endpoints are read-only, so no key generation is needed for normal traffic. Sprint 2+ writes that need legacy-format keys for these tables will use either:
- Java service-side generators (preferred per CLAUDE.md DB Function policy), or
- Dedicated DEV sequences (`sq_appr_seq_no`, `sq_acct_trco_key`, `sq_busa_actv_seq`, etc.) when the legacy semantics are documented.

### 15.6 Migration files changed/added

| File | Type | Summary |
|---|---|---|
| `V23__sprint1_legacy_schema_parity.sql` | Existing (round 1) | Adds 144 missing columns across 6 tables (most critically `BUSA_DOC_MSTR` 20→100). |
| **`V24__sprint1_legacy_constraints_parity.sql`** | **New (round 2)** | Aligns `BUSI_INFM.BIZ_KEY` 32→20; realigns 5 PRIMARY KEYs; adds 1 UNIQUE + 13 CHECK + 5 FOREIGN KEY constraints; cleans up V17 stub `busi_cond_daily_sumr` KRW row. Documents 2 deferred FKs and 28 deferred triggers in the trailing block. |
| `wecms-api/CLAUDE.md` | Updated | Migration table: V24 row added. Legacy Schema Parity Rule section: extended with "Constraints, indexes, and triggers — also part of parity" subsection + extended pre-migration checklist. |
| `docs/refactor-wecms/task-sprint-1-backend-golden-chipmunk.md` | Updated | This §15 added. |

### 15.7 Build / test result

| Step | Result |
|---|---|
| `./gradlew compileJava` | ✅ Clean (entities unchanged — V24 is DDL-only) |
| Flyway V24 applied | ✅ Applied cleanly to local DB |
| Hibernate `ddl-auto: validate` | ✅ Passes |
| `Started WecmsApiMainApplication in 40.778 seconds` on port 18084 | ✅ |
| Constraint counts post-V24 | ✅ 17 PKs aligned, 1 UK, 13 CHECKs, 5 FKs |
| BUSI_INFM.BIZ_KEY column type | ✅ VARCHAR(20) (down from 32) — matches legacy |

### 15.8 Remaining risks / TODOs

- **Deferred FKs (Sprint 2+)**:
  - `acct_trns_rcv_pay_infm_fk02` → `cash_repy_mstr(biz_key, trsc_dt, cash_repy_seq_no)` — port `cash_repy_mstr` first.
  - `busa_doc_mstr_fk04` → `mrec_pic_store(biz_key, reg_dt, seq_no)` — port `mrec_pic_store` first.
  - `acct_mstr.bank_cd → bank_infm.bank_cd` — DEV has no UNIQUE on `bank_infm.bank_cd` alone (PK is composite); so this FK can't be expressed in standard SQL. Legacy DEV uses the `reject_not_existing_bank_cd` BEFORE-INSERT trigger as the FK substitute. Defer the trigger port to Sprint 2 when audit-log infrastructure lands.
- **Deferred triggers (28 total)**:
  - 13 `*_logtrigger` triggers across 9 tables → all depend on `*_log()` functions that write to `*_log_h` history tables not in Sprint 1 scope.
  - `acct_mstr_before_insert_or_update` → `reject_not_existing_bank_cd()` — see above.
  - `sync_to_rept_trigger` on `acct_trns_hstr` → syncs to `rept_trns_hstr`; that target table + sync function are Sprint 2/3.
  - **Sprint 1 endpoints are READ-ONLY**, so missing triggers don't affect Sprint 1 traffic. Flyway seed inserts use `ON CONFLICT DO NOTHING`, so re-runs don't produce trigger fire. Safe to defer.
- **`acct_trns_hstr_uk01`** (11-column composite UNIQUE) — not added because the V22 seed has some columns NULL, and a future Sprint 2 write path may not populate every UK column. Add when the writer is finalised.
- **V13 surplus columns** in 3 tables (`busa_doc_rcv_pay_his` +4, `acct_trns_rcv_pay_evid_file` +5, `acct_trns_rcv_pay_infm` +1 (`DOC_ID`)) — kept for rollback safety. Drop in a future migration once we're confident no caller / seed depends on them.
- **Audit-log infrastructure** — port `*_log_h` history tables and `*_log()` functions before any non-local deploy of Sprint 2+. Production needs the audit trail.

### 15.9 Summary

Sprint 1 local DB now matches DEV across:
- ✅ All 17 tables: column count, names, types, lengths/precision/scale, nullability, defaults
- ✅ All 17 PRIMARY KEYs (5 realigned by V24)
- ✅ 1/1 in-scope UNIQUE constraints (`busa_doc_mstr_uk01`)
- ✅ 13/13 in-scope CHECK constraints
- ✅ 5/7 FOREIGN KEYs (2 deferred — target tables out of scope, documented)
- ⏸ 0/28 triggers (all deferred — depend on audit-log infrastructure, documented)
- ✅ wb_get_next_val: 0 Sprint 1 tables depend on it (verified by call-test)
- ✅ Seed data: real DEV rows only, real PK values, BIZ_KEY = `VN_VNM0004_000001` only

---

## 16. Key-Generation / wb_get_next_val Branch Audit (2026-04-29)

Follow-up audit triggered by the question "does any Sprint 1 table need a new
`wb_get_next_val` branch in local?". Answer: **no, but the audit is now
documented for Sprint 2+.**

### 16.1 DEV vs local branch list

The DEV `wecmsvndev.wb_get_next_val` function body (extracted via
`pg_get_functiondef`, parsed in `Claude Code (AI)/notes/_extract_branches.js`)
contains **164 distinct `(table, field)` branches**:

```
acct_mstr.prn_seq                          acct_trco_infm.acct_trco_key
acct_trns_hstr.acct_seq_no                 acct_trns_rcv_pay_evid_file.file_id
acct_trns_rcv_pay_infm.rcv_pay_seq_no      acct_trns_vas_actv_his.acct_trns_vas_actv_seq
adv_paym_doc_detl.adv_paym_doc_detl_seq    adv_paym_doc_mstr.adv_paym_doc_seq
adv_paym_mstr.adv_paym_seq                 adv_paym_trns_hstr.adv_paym_trns_seq
adv_paym_trns_hstr_evid_file.file_id       adv_paym_user_list.adv_paym_seq
appr_line_infm.appr_line_no                audit_logs_infm.audit_logs_seq_no
aut_cls_acct_trns_rmrk_infm.aut_acct_trns_rmrk_seq
bank_cert_info.seq_no                      bank_exch_rate_infm.exch_seq_no
bltb_comnt_infm.{comnt,post}_seq_no        bltb_draft_post_apprvr.draft_post_seq_no
bltb_draft_post_apprvr_infm.apprvr_line_no bltb_draft_post_detl.{draft_post_detl,draft_post}_seq_no
bltb_draft_post_detl_inf.sal_item_seq_no   bltb_draft_post_file.file_id
bltb_draft_post_mstr.draft_post_seq_no     bltb_infm.bltb_cd
bltb_noti_cont_infm.noti_seq_no            bltb_noti_infm.noti_seq_no
bltb_post_comnt_file.file_id               bltb_post_file.file_id
bltb_post_infm.post_seq_no                 bltb_user_new_post_infm.post_seq_no
busa_doc_detl.disp_ord                     busa_doc_detl_vas_item_infm.disp_ord
busa_doc_file.file_id                      busa_doc_mstr.doc_id
busa_doc_rcv_pay_his.rcv_pay_his_seq_no    busi_bankform_infm.seq_no
busi_coll_pay_infm.coll_seq_no             busi_cond_daily_sumr_detl.dtl_seq_no
busi_detl_infm.detl_infm_seq               busi_doc_vas_item_actv_his.busa_actv_seq
busi_erp_api_infm_mstr.seq_no              busi_infm.biz_key
busi_match_log.match_log_seq               busi_payroll_tax_infm.seq_no
busi_sal_acct_infm.seq_no                  busi_txtn_ptrn_set_infm.txtn_ptrn
busi_usag_acct_infm_detl.reg_seq_no        busi_usag_set_infm.{disp_ord,use_usag_cd}
busi_vas_acct_infm.vas_acct_seq_no         card_trns_hstr.card_seq_no
card_trns_rcv_pay_infm.rcv_pay_seq_no      cash_cheq_evid_file.file_id
cash_cheq_mstr.cash_cheq_seq_no            cash_repy_evid_file.file_id
cash_repy_mstr.cash_repy_seq_no            chart_acct_infm.chart_acct_seq_no
cstm_ldgr_infm.biz_key                     dat_imp_wiz_*.seq_no  (6 branches)
efiling_sumr_file.seq_no                   einv_fee_detl.disp_ord
einv_tax_detl.disp_ord                     empl_faml_infm.seq_no
empl_infm.empl_no                          empl_payroll_infm.empl_no
erp_trns_hstr.seq_no                       expt_resol_*.* (6 branches)
ext_tran_call_his.seq_no                   forw_set_rcv_pay_his.forw_rcv_pay_seq_no
gdt_cert_info.seq_no                       genl_noti_*.* (2 branches)
item_infm.item_cd                          memo_infm.{seq_no,file_id}
moc_srch_trco_*.* (12 branches)            mrec_pic_store.{seq_no,file_id}
msg_snd_his.reg_seq_no                     napas_remit_trns_hstr.seq_no
nbr_trco_infm_his.seq_no                   noti_*.* (3 branches)
ovrs_pymn_*.{ovrs_pymn_no,ovrs_pymn_detl_no} pgro_bsac_lnkn_infm.grp_cd
prjt_infm.prjt_no                          push_notice_infm.seq_no
rmrk_org_infm.rmrk_seq_no                  saly_book_*.* (8 branches)
scdul_pymn_*.* (3 branches)                smrt_asst_*.* (3 branches)
snd_serv_*.* (6 branches)                  sys_doc_id_gen.doc_id
tb_firm_tran.tran_seq                      tb_pay_*.* (4 branches)
tb_pay_target.tran_seq                     trco_acct_info.{disp_ord,trco_acct_key}
trco_actv_hstr.actv_seq                    trco_addr_infm.* (3 branches)
trco_grp_user.{disp_ord,grp_cd}            trco_infm.trco_key
trco_info_file.file_id                     trco_ovrs_infm.trco_ovrs_key
trco_rspb_infm.rspb_key                    trns_hstr_acct_expr_hstr.dp_seq_no
trns_hstr_loan_borw_hstr.loan_seq_no       trsc_adm_doc_*.* (3 branches)
user_point_*.* (2 branches)                user_pw_chg_his.reg_seq_no
user_scr_option.disp_ord                   user_trco_use_his.seq_no
user_usag_use_his.seq_no                   vas_acct_infm.vas_acct_seq
```

The **local** `public.wb_get_next_val` (created by V11) implements **2 of those 164 branches**:

```
busi_infm.biz_key       — Sprint 0 bootstrap (BusinessKeyGeneratorService)
empl_infm.empl_no       — Sprint 0 bootstrap (EmployeeNumberGeneratorService)
```

All other branches return NULL locally — by design.

### 16.2 Sprint 1 per-table key-generation audit

| # | Table | Primary key | DEV `wb_get_next_val` branch | Sprint 1 backend creates new rows? | Action |
|---:|---|---|---|:-:|---|
| 1 | `bank_infm` | (bank_cd, start_date, end_date) | none | No | Use real DEV bank-registry rows verbatim. Done by V20. |
| 2 | `acct_mstr` | (biz_key, bank_cd, acct_no) | `acct_mstr.prn_seq` (NOT a PK component — print sequence) | No | Use real DEV (biz_key, bank_cd, acct_no) values. Done by V22. |
| 3 | `acct_trns_hstr` | (biz_key, acct_seq_no) | `acct_trns_hstr.acct_seq_no` (PK component) | No | Use real DEV `acct_seq_no` values. Done by V22. |
| 4 | `acct_trns_rcv_pay_infm` | (biz_key, acct_seq_no, rcv_pay_seq_no) | `rcv_pay_seq_no` (PK component) | No | Use real DEV values. Done by V22. |
| 5 | `busa_doc_rcv_pay_his` | (V13 vestigial — DEV has no PK) | `rcv_pay_his_seq_no` (legacy column added by V23) | No | Not seeded; not consumed by Sprint 1 endpoints. |
| 6 | `acct_trns_rcv_pay_evid_file` | (biz_key, acct_seq_no, rcv_pay_seq_no, file_id) | `file_id` (PK component) | No | Empty in DEV for tenant; not seeded. |
| 7 | `napas_remit_trns_hstr` | (biz_key, seq_no) | `seq_no` (PK component) | No | Use real DEV `seq_no` values. Done by V22. |
| 8 | `busi_cond_daily_sumr` | (biz_key, trco_key, trsc_dt, ccy_cd, prjt_no) | none | No | Empty in DEV; V17 stub only (KRW row removed by V24). |
| 9 | `busi_cond_daily_sumr_detl` | (biz_key, trco_key, trsc_dt, ccy_cd, dtl_seq_no, prjt_no) | `dtl_seq_no` (PK component) | No | Empty in DEV; V17 stub only. |
| 10 | `sell_his_daily_sumr` | (8-col PK) | none | No | Empty in DEV; not seeded. |
| 11 | `sell_his_daily_sumr_detl` | (10-col PK) | none | No | Empty in DEV; not seeded. |
| 12 | `bat_call_his` | (job_id, biz_key, job_date, job_str_tm, req_date, mod_uid) | none | No | Job IDs are externally provided (BCS). Not seeded. |
| 13 | `busa_doc_mstr` | (biz_key, doc_id) | `busa_doc_mstr.doc_id` (PK component) | No | Use real DEV `doc_id` values (e.g. `1C25TWV-1348`, `R-20250806008`). Done by V22. |
| 14 | `einv_fee_detl` | (biz_key, doc_id, disp_ord) | `disp_ord` (PK component) | No | Not seeded. |
| 15 | `einv_tax_detl` | (biz_key, doc_id, disp_ord) | `disp_ord` (PK component) | No | Not seeded. |
| 16 | `oprt_stts_info` | (biz_key, taxcode) | none | No | Use real Vietnamese tax codes from DEV. Done by V22. |
| 17 | `ext_tran_call_his` | (svc_id, ref_date, seq_no) | `seq_no` (PK component) | No | Not seeded. |

### 16.3 Decisions applied

- **Zero new `wb_get_next_val` branches added locally.** Sprint 1 endpoints are entirely read-only — backend never INSERTs into these tables, so no key generation is required.
- **Seed migrations (V17, V22) use real DEV primary-key values.** No synthetic IDs are minted for any Sprint 1 row. `INSERT … ON CONFLICT DO NOTHING` conflict targets match the legacy PK column set exactly.
- **No Java generator services were added.** The existing Sprint 0 `BusinessKeyGeneratorService` and `EmployeeNumberGeneratorService` cover the only two branches Sprint 0 needed; no Sprint 1 equivalents are required.
- **Sprint 2+ writes** that create new rows in any of the 11 tables with DEV branches MUST implement a Java service-side generator (preferred per CLAUDE.md DB Function policy). Only as a temporary compatibility bridge — when a hard legacy interop deadline forces it — may the developer add a local `wb_get_next_val` branch, and only with explicit retirement-plan documentation in the CLAUDE.md "Surviving legacy functions" table.

### 16.4 Code / migration changes made

- **None to code or migrations.** Sprint 1 implementation was already correct: all seed PKs are real DEV values, no INSERT logic exists in services, no calls to `wb_get_next_val` from Sprint 1 packages (verified by `grep wb_get_next_val src/main/java/com/kosign/wecms/domain/{banking,einvoice,operation}` — zero matches).
- **CLAUDE.md** updated with the new "Key-generation rule for migrated legacy tables" subsection under "Surviving legacy functions" + Sprint 1 audit confirmation.
- **This Sprint 1 plan** §16 added with full DEV branch list and per-table audit.

### 16.5 Confirmation

- ✅ DEV branch list extracted (164 unique table.field pairs).
- ✅ Local branch list confirmed (2 — `busi_infm.biz_key`, `empl_infm.empl_no`).
- ✅ Sprint 1 implementation does NOT need any new `wb_get_next_val` branch.
- ✅ Sprint 1 seed data uses real DEV PKs throughout — no fake/generated IDs.
- ✅ Build & Hibernate validate continue to pass (no code changes, only doc updates).

---

## 17. wb_get_next_val Scope Decision (2026-04-30)

### What Happened

V25 (initial attempt) ported all 164 DEV branches of `wb_get_next_val` into local.
This was **immediately corrected by V26** after review. The full port was wrong for
these reasons:

- It imported key-generation logic for ~100 tables that are not migrated locally
  and are not in the current refactor scope.
- It created 11 sequences for DEV branches that no local code calls.
- It turns the local function into a hidden dependency catalogue instead of a
  targeted utility — any future change to a "live" branch can silently affect
  unintended paths.
- Sprint 1 is entirely read-only. No new rows. No key-generation needed.

### Rule Established (recorded in CLAUDE.md)

> **For `wb_get_next_val`, maintain branches only for locally migrated tables
> that have a DEV branch.** When a table is migrated to local Flyway AND DEV's
> `wb_get_next_val` has a branch for it, that branch belongs in local.
> Do not port branches for tables not yet migrated locally.
> Do not port the full DEV function blindly.
> For Sprint 2+ tables, the branch is added in the same migration sprint that
> ports the table — confirmed with the lead first.

### V25 → V26 → V27 Correction History

| Migration | What | Status |
|---|---|---|
| V25 | Full 164-branch DEV port (wrong — too broad) | Applied; superseded |
| V26 | Trimmed to 2 Sprint 0 branches (wrong — too narrow; excluded Sprint 1 tables) | Applied; superseded |
| V27 | **Correct**: Sprint 0 (2) + Sprint 1 migrated-table DEV branches (11) = 13 total | Applied; canonical |

**V27__wb_get_next_val_sprint1_scope.sql**:
- No new sequences (all Sprint 1 branches use `MAX()+1`, no `nextval`)
- 13 WHEN branches — exact DEV SQL, copied verbatim
- DECLARE includes only variables actually used by these branches

### Full DEV Branch List for Sprint 1 Tables (from DEV audit 2026-04-30)

166 total branches in DEV `wb_get_next_val`. Sprint 1 table scan:

**MATCHED — added to V27 (11):**

| DEV branch | V27 action | Notes |
|---|---|---|
| `acct_mstr.prn_seq` | ✅ Added | MAX+1 on biz_key |
| `acct_trns_hstr.acct_seq_no` | ✅ Added | MAX+1 on biz_key |
| `acct_trns_rcv_pay_infm.rcv_pay_seq_no` | ✅ Added | MAX+1 on biz_key+acct_seq_no |
| `acct_trns_rcv_pay_evid_file.file_id` | ✅ Added | MAX+1 on biz_key+acct+rcv_pay |
| `busa_doc_rcv_pay_his.rcv_pay_his_seq_no` | ✅ Added | MAX+1 on biz_key+doc_id |
| `busa_doc_mstr.doc_id` | ✅ Added | Date-prefix+seq per doc_type (complex, 30-line branch) |
| `busi_cond_daily_sumr_detl.dtl_seq_no` | ✅ Added | MAX+1 on multi-key (biz_key+lst_coll_date+trco_key+trsc_dt+ccy_cd) |
| `einv_fee_detl.disp_ord` | ✅ Added | MAX+1 on biz_key+doc_id |
| `einv_tax_detl.disp_ord` | ✅ Added | MAX+1 on biz_key+doc_id |
| `napas_remit_trns_hstr.seq_no` | ✅ Added | MAX+1 on biz_key |
| `ext_tran_call_his.seq_no` | ✅ Added | MAX+1 on svc_id+ref_date |

**NO DEV BRANCH — not in V27 (6):**

| Table | DEV branch? | Reason no generator |
|---|---|---|
| `bank_infm` | No | PK uses bank-registry data; rows inserted from external source |
| `busi_cond_daily_sumr` | No | Batch-inserted by BCS process; no row-level key generator |
| `sell_his_daily_sumr` | No | Batch-inserted by BCS process |
| `sell_his_daily_sumr_detl` | No | Batch-inserted by BCS process |
| `bat_call_his` | No | Scheduler-managed keys; no row-level generator |
| `oprt_stts_info` | No | Synced from external source; no row-level generator |

### Confirmation

- ✅ V25 applied (2026-04-30) — 164-branch full port (superseded).
- ✅ V26 applied (2026-04-30) — trimmed to 2 branches (superseded).
- ✅ V27 applied (2026-04-30) — 13 branches, correct scope.
- ✅ `./gradlew test` passes (Java 21 / Temurin).
- ✅ CLAUDE.md updated with correct `wb_get_next_val` rule.
- ✅ Surviving functions table reflects the 13-branch state.

---

## 18. Final DB Parity Audit (2026-04-30)

Full local-vs-DEV audit across all 23 in-scope tables. Detailed report at
[db-parity-audit-2026-04-30.md](../../../references/audits/db-parity-audit-2026-04-30.md). Highlights:

**Findings:**
- Column count: aligned for all 23 tables.
- Missing columns in local: 2 (`empl_infm._empl_nm`, `user_login_his.reg_uid`).
- Local-only convenience columns (NOT deleted per rule): 4 tables.
- Type mismatches: 116 — split into intentional divergences (CHAR↔VARCHAR, TIMESTAMPTZ↔VARCHAR(14)) and real gaps (NUMERIC precision, VARCHAR length).
- Missing UNIQUE: 2 (`acct_trns_hstr_uk01`, `busi_infm_uk01`).
- Missing CHECK: 1 (`busi_infm_chk01`).
- PK realignment needed: 2 tables (`busi_cond_daily_sumr_detl`, `user_login_his`).
- FK gaps: 4 — all DEFERRED (target tables out of scope).
- Trigger gaps: 15 — all DEFERRED (depend on log/sync infrastructure).
- Seed data: 5 tables empty locally with real DEV rows available.

**Corrective migrations:**
- **V28** — schema fixes (additive): missing columns, NUMERIC precision widening, VARCHAR length widening, UNIQUE/CHECK adds, PK realignments. No CHAR↔VARCHAR reversal, no TIMESTAMPTZ→VARCHAR reversal, no NN tightening (Sprint 2 follow-up).
- **V29** — real DEV seed round 2: 226 rows across 5 tables (`bat_call_his`, `busa_doc_rcv_pay_his`, `einv_fee_detl`, `einv_tax_detl`, `ext_tran_call_his`). Tenant-scoped where applicable; idempotent.

**Java-layer changes:** none required (entity/DTO/service unchanged — column names/types added are not mapped in Sprint 1 scope).

**Rule established:** Final parity audit is now mandatory at the end of every sprint. CLAUDE.md updated.

