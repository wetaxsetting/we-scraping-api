# Phase A — Implementation Plan (Safe Improvements)

**System:** Refactor (wecms-api)
**Date:** 2026-06-24
**Source:** [database-remediation-roadmap.md](database-remediation-roadmap.md) → Phase A.
**Status:** Implementation **planning only** — no code is modified by this document.

Phase A items are **refactor-owned**: they change Java wiring or a DEV-only DB object and do **not** alter the legacy JEX schema, any wire/API contract, or the View/DTO surface. They are safe under `feedback_preserve_existing_code.md` (change only what is broken). None require a PO decision; A5 needs a DBA action on the DEV database only.

> **Scope guard (wecms-api/CLAUDE.md):** this tree is edited only on explicit backend request. This plan is the authorization artifact — implementation of A1–A5 is the explicit backend change being planned. Each item below is independently shippable; do not bundle.

**Grounding facts verified against the code/DB this pass:**
- `BusiAdmnInfmRepository` **already declares** `List<BusiAdmnInfm> findAllByBizKey(String bizKey)` ([BusiAdmnInfmRepository.java:41](../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiAdmnInfmRepository.java#L41)) — A1 needs no new repository method.
- The 4 A4 repositories each have **0 referencing files** (verified by grep).
- A5 trigger `busi_infm_init_data_trigger` fires `AFTER INSERT ON busi_infm` → `wb_init_busi_data(...)`; it exists in `wecmsdevdb` only, **not** in JEX `wecmsvndev`.

---

## A1. Tenant-scope correction — `BankAccountPermissionService`

**Severity:** High (cross-tenant data exposure shape) · **Effort:** Small (~½ day)

### Exact files & methods
- [BankAccountPermissionService.java:98](../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java#L98) — inside the list/roster method (admin-set build before the `members` loop).
- [BankAccountPermissionService.java:194](../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/BankAccountPermissionService.java#L194) — inside `replace(bankCd, acctNo, req)` (admin-set build for the self-revoke guard).
- Repository: `BusiAdmnInfmRepository.findAllByBizKey(String)` — **already exists** ([line 41](../../src/main/java/com/kosign/wecms/domain/auth/repository/BusiAdmnInfmRepository.java#L41)); no change.

Both sites are byte-identical:
```java
busiAdmnRepo.findAll().forEach(row -> {
    if (bizKey.equals(row.getBizKey())) { adminUserIds.add(row.getUserId()); }
});
```

### Implementation approach
Replace each `findAll().forEach(... if bizKey.equals ...)` with a scoped fetch:
```java
busiAdmnRepo.findAllByBizKey(bizKey)
        .forEach(row -> adminUserIds.add(row.getUserId()));
```
The in-Java `bizKey.equals(...)` guard becomes redundant (the query already constrains `BIZ_KEY`). This mirrors the anti-pattern already retired at `BusiInfmRepository.java:17` / `ExchangeRateBatchService.java:495`.

### Expected behavior change
- **Functional result identical** for a correctly-isolated DB: the same admin-userId set for the current tenant. The previous code already filtered by `bizKey`, so the *output* set does not change.
- **What changes:** the query stops loading other tenants' `BUSI_ADMN_INFM` rows into the app. Removes the cross-tenant scan and the latent exposure shape (any future code reading the pre-filter list would have leaked).
- No wire/API change; response DTOs untouched.

### Risk analysis
- **Low.** `findAllByBizKey` is an existing, tested derived query. PK on `BUSI_ADMN_INFM` is `(BIZ_KEY, USER_ID)` — `biz_key`-leading, so the query is index-supported.
- Edge: a tenant with zero admins returns an empty list (same as before). The downstream `adminUserIds.contains(...)` and the self-revoke guard behave identically on an empty set.
- No transaction-boundary change (both sites already run in their existing `@Transactional` / read context).

### Required tests
- Unit (Mockito): stub `findAllByBizKey(bizKey)` and assert the roster excludes admins and the self-revoke guard fires for an admin-less non-admin caller (existing `BankAccountPermissionServiceTest` cases should pass unchanged).
- New regression test: seed two tenants' admins; assert the method returns only the current tenant's admins **and** that `findAll()` is never invoked (`verify(busiAdmnRepo, never()).findAll()`).
- Multi-tenant integration check: confirm a request scoped to tenant A cannot observe tenant B's admin set.

### Rollback plan
Revert the two call sites to the `findAll().forEach` form. No schema/data/contract change, so rollback is a pure code revert with no migration impact.

---

## A2. N+1 reductions — per-element reads on unbounded lists

**Severity:** High · **Effort:** Medium (~1–1.5 days incl. tests)

Four sites. Each loops a request-supplied collection and issues one repository call per element. The fix is to batch-fetch once, then iterate the in-memory result — **preserving every existing per-element validation and error code**.

### A2-1 · `OverseasPaymentService` — `findById` per `ovrsPymnNos()`
- **File/method:** [OverseasPaymentService.java:786-799](../../src/main/java/com/kosign/wecms/domain/payment/overseas/service/OverseasPaymentService.java#L786-L799) (follow-up/approval path).
- **Current:** `for (String no : req.ovrsPymnNos())` → `ovrsPymnMstrRepository.findById(new OvrsPymnMstrId(bizKey, no))`, throwing `WBE_VALIDATION` (blank), `WBE_NOT_FOUND` (missing), `WBE2127` (already transferred), `WBE2046` (already approved).
- **Approach:** validate blanks first; build the id list; `findAllById(ids)` into a `Map<OvrsPymnMstrId, OvrsPymnMstr>`; iterate the **original** `ovrsPymnNos()` order, looking each up in the map → throw `WBE_NOT_FOUND` when absent, then apply the `WBE2127`/`WBE2046` guards. Preserve first-failure semantics by iterating in request order.

### A2-2 · `BankTransactionSyncService` — `findByBizKeyAndBankCdAndAcctNo` per account
- **File/method:** [BankTransactionSyncService.java:180-189](../../src/main/java/com/kosign/wecms/domain/banking/.../BankTransactionSyncService.java#L180-L189) (sync queue build).
- **Current:** `for (key : req.accounts())` → `acctRepo.findByBizKeyAndBankCdAndAcctNo(...)`, `WBE_BANK_ACCOUNT_NOT_FOUND` on miss, then a `HAND_REG_YN='Y'` skip filter.
- **Approach:** add a batch finder `findByBizKeyAndActvYn`-style method keyed on the `(bankCd, acctNo)` pairs — e.g. `List<AcctMstr> findByBizKeyAndBankCdAndAcctNoIn(...)` or `findByBizKeyAnd…` over a composite key set — load into a map keyed by `(bankCd,acctNo)`, then iterate `req.accounts()` for the not-found check + HAND_REG skip. Keep the existing ordering step that follows.

### A2-3 · `EInvoiceScrapeService` — `findById` per scraped invoice (upsert)
- **File/method:** [EInvoiceScrapeService.java:122-158](../../src/main/java/com/kosign/wecms/domain/einvoice/.../EInvoiceScrapeService.java#L122-L158).
- **Current:** `for (row : req.invoices())` computes `docId`, then `busaRepo.findById(id)` to decide insert-vs-update. This is a **read-then-upsert**, not a pure read.
- **Approach:** first pass computes all `docId`s for non-skipped rows; `findAllById(docIds)` into a `Map<BusaDocMstrId, BusaDocMstr>`; second pass does the insert/update decision against the map (still `save`/`saveAll` per the A3 note). The branch logic (`branchA`/`branchB`, void-status, `provStts`) is unchanged.
- **Note:** combine with A3-style `saveAll` for the write leg if upload volumes warrant.

### A2-4 · `SettingsBankAccountService` — ~4 `AcctAuthInfo` queries per row
- **File/methods:** [SettingsBankAccountService.java:143](../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/SettingsBankAccountService.java#L143) (`rows.stream().map(... resolvePermissionLabels ...)`), `resolvePermissionLabels` ([:182](../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/SettingsBankAccountService.java#L182)), `composePermissionLabel` (countActiveByAccount + findFirstActiveUserName, per row × authType `V`/`W`).
- **Current:** for N rows → up to 4 queries/row (count + first-name, twice).
- **Approach:** add a tenant-scoped aggregate query on `AcctAuthInfoRepository` that returns, per `(bankCd, acctNo, authType)`, the active grantee count + first grantee name in one result set for all accounts in the page (group-by / projection). Resolve into a `Map<(bankCd,acctNo,authType), Label>` once, then `composePermissionLabel` reads the map. Mirrors the existing `resolveModifierNames` batch pattern already in the same class ([:165-180](../../src/main/java/com/kosign/wecms/domain/setting/bankaccount/service/SettingsBankAccountService.java#L165) uses `findAllById`).

### Expected behavior change (all A2)
- Identical outputs, identical error codes, identical ordering. Only the query count drops from O(N) to O(1) per list.
- A2-1/A2-2: **first-failure error semantics preserved** by iterating in original request order against the prefetched map.
- No wire/API/DTO change.

### Risk analysis
- **Medium.** The behavioral risk is in faithfully reproducing per-element validation against a batch result:
  - Missing-id detection must remain per-element (`findAllById` silently returns fewer rows) — explicit map-miss → same error code.
  - Duplicate ids in the request must not collapse silently if the original loop would have processed each (overseas: duplicates would re-validate the same row — acceptable; document).
  - A2-4 requires a **new repository query** — validate its JPQL against the entity metamodel and confirm the count/first-name semantics match `countActiveByAccount` + `findFirstActiveUserName` exactly (active = `USE_YN='Y'`, ordering for "first" name).
- Tenant scope (`BIZ_KEY`) must be carried into every new batch query.

### Required tests
- Per site: unit test asserting (a) same result as the per-element version for a mixed valid/invalid input, (b) correct error code on the first invalid element, (c) the batch repo method is called once (`verify(...).findAllById(...)` / `times(1)`), and the per-element finder is never called.
- A2-4: a query test (sliceTest / `@DataJpaTest`) seeding multiple accounts with V/W grantees, asserting the aggregate matches the old per-row composition (including the "+ N more" label and null-when-empty).
- Regression: existing service tests for these four services must pass unchanged.

### Rollback plan
Each site is independent — revert any one site to its per-element loop without affecting the others. New batch repository methods are additive and can remain (harmless) even if a call site is reverted. No schema/data/contract change.

---

## A3. Save-loop review — per-row `save()` in loops

**Severity:** Medium (write amplification; correct but scales poorly) · **Effort:** Medium (~1 day, gated by JDBC batch config)

### Exact files & methods
- [VendorPaymentService.java:489](../../src/main/java/com/kosign/wecms/domain/payment/vendorpayment/.../VendorPaymentService.java#L489) — `for (line : req.lines())` → `detlRepository.save(detl)` (`ScdulPymnSumrDetl`).
- [ExpensePaymentService.java:486](../../src/main/java/com/kosign/wecms/domain/payment/expensepayment/.../ExpensePaymentService.java#L486) — same pattern (`ScdulPymnSumrDetl`).
- [InternalTransferService.java:554](../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/.../InternalTransferService.java#L554) — same pattern (`ScdulPymnSumrDetl`).
- [CardTransactionsService.java:829](../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/.../CardTransactionsService.java#L829) — `for (ent : stamped)` → `cardTrnsRepo.save(ent)` (`CardTrnsHstr`).
- [CardTransactionsService.java:1183](../../src/main/java/com/kosign/wecms/domain/payment/cardtransactions/.../CardTransactionsService.java#L1183) — `busaDocRcvPayHisRepo.save(row)` per doc (`BusaDocRcvPayHis`).
- [TransactionService.java:490](../../src/main/java/com/kosign/wecms/domain/banking/.../TransactionService.java#L490), [:559](../../src/main/java/com/kosign/wecms/domain/banking/.../TransactionService.java#L559), [:631](../../src/main/java/com/kosign/wecms/domain/banking/.../TransactionService.java#L631) — `for (rp : rcvPayRepo.findByBizKeyAndAcctSeqNo(...))` → `rcvPayRepo.save(rp)` (`AcctTrnsRcvPayInfm`). The read is already a single query; only the write leg loops.

### Implementation approach
- **Decision gate first:** confirm `spring.jpa.properties.hibernate.jdbc.batch_size` (and `order_inserts`/`order_updates`) is set in `application*.yml`. If unset, `saveAll` alone gives no batching win — either set batch properties (config change, note in MR) or leave the loop and only document.
- Where rows are independent (no per-row key generation that must observe the prior row), replace the loop with `repository.saveAll(list)`.
- **Key-generation constraint:** any per-row key from `wb_get_next_val` MUST stay funneled through `LegacyFunctionRepository` (`project_wb_get_next_val_funnel.md`). Do not swap to identity/sequence columns. If keys are allocated inside the loop, batch the *persist* but keep key allocation per-row (or pre-allocate the batch through the funnel) — do not change key format.
- Leave **bounded/by-design** loops alone (e.g. `ApprovalBoxService.java:478`, capped by `MAX_BULK_SIZE`, per-row savepoints — legacy-faithful).

### Expected behavior change
- Identical persisted rows; fewer SQL statements when batching is enabled. No wire/API change. Flush timing may move to batch boundaries — verify no code between iterations depends on a per-row flush (e.g. reading back a generated value mid-loop).

### Risk analysis
- **Medium-low.** Main risks: (1) a per-row flush dependency that `saveAll` defers; (2) larger transactions holding locks longer on big uploads; (3) accidentally changing key generation. Mitigated by keeping key allocation unchanged and only batching independent inserts.
- If batching properties are added, that is a config change — call out in the MR (CLAUDE.md treats `application*.yml` as a backend artifact).

### Required tests
- Unit: assert `saveAll` called once with the full collection (not N `save`s).
- Integration (`@DataJpaTest` or service slice): persist a multi-line payment / multi-row card batch and assert row count + field values match the per-row version; assert generated keys retain the legacy format.
- Volume sanity: a 100+ row upload completes and produces identical rows.

### Rollback plan
Revert to per-row `save()` (functionally identical). If JDBC batch properties were added, revert that YAML line too. No schema/data/contract change.

---

## A4. Unused repository review

**Severity:** Low (dead code) · **Effort:** Small (~½ day, mostly verification)

### Exact files (all confirmed 0 referencing files)
- [DailyCashReptDailySumrDetlRepository.java](../../src/main/java/com/kosign/wecms/domain/banking/repository/DailyCashReptDailySumrDetlRepository.java) → entity `DailyCashReptDailySumrDetl` (table `daily_cash_rept_daily_sumr_detl`).
- [EmplInfmRepository.java](../../src/main/java/com/kosign/wecms/domain/auth/repository/EmplInfmRepository.java) → entity `EmplInfm` (table `empl_infm`).
- [TbBatchScheduleInfoRepository.java](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/TbBatchScheduleInfoRepository.java) → entity `TbBatchScheduleInfo` (table `tb_batch_schedule_info`).
- [TbBatchScheduleDtlRepository.java](../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/TbBatchScheduleDtlRepository.java) → entity `TbBatchScheduleDtl` (table `tb_batch_schedule_dtl`).

### Implementation approach — **verify before deleting**
1. **`EmplInfmRepository`** — `EMPL_INFM` rows are created via the manual DBA SQL path (CLAUDE.md "account creation is manual SQL only"). **Recommended: keep**, add a Javadoc note that it has no Spring consumer by design, or remove only if confirmed never needed by a near-term feature.
2. **`TbBatchScheduleInfoRepository` / `TbBatchScheduleDtlRepository`** — belong to the batch dispatch module (`project_batch_refactor_real_impl.md`), which is flag-gated/inert. **Confirm with the batch owner** whether these are pending wiring (B-series infra) before removing. If pending, keep + add a `// pending: wired in batch B-series` note.
3. **`DailyCashReptDailySumrDetlRepository`** — confirm the daily-cash report path (now calling `rept_0900_001_rec*` functions directly) does not intend to read this table; if not, remove.
- The entities and their tables stay in the inventory regardless ("Unused ≠ removable") — only the unused **repository interface** is a deletion candidate.

### Expected behavior change
None at runtime — these interfaces are wired to nothing. Removal only reduces dead code and clarifies which tables are live.

### Risk analysis
- **Low.** The only risk is deleting a repository a not-yet-merged branch expects. Mitigated by the per-repo confirmation above. No schema/data/contract impact.

### Required tests
- Build + full test suite green after removal (compilation proves no hidden reference).
- `EndpointAuthorizationCoverageTest` and existing slice tests unaffected.

### Rollback plan
Restore the deleted interface file(s) from VCS. No schema/data/contract effect.

---

## A5. DEV-only DB drift reconciliation — `busi_infm_init_data_trigger`

**Severity:** Medium (DEV diverges from the UAT/PROD live-JEX target) · **Effort:** Small (investigation + DBA action)

### Exact object
- DB: trigger `busi_infm_init_data_trigger` `AFTER INSERT ON wecmsdevdb.busi_infm FOR EACH ROW EXECUTE FUNCTION busi_infm_init_data_tg()`; function `busi_infm_init_data_tg()` calls `wb_init_busi_data(NEW.biz_key, COALESCE(NEW.reg_id, …), …)` in the same transaction.
- Present in refactor DB `wecmsdevdb` **only**; **absent** in JEX `wecmsvndev`.
- Java side: `wb_init_busi_data` is invoked from application code (funnel/repository — 6 references found), i.e. the refactor already owns tenant-init in Java.
- No application-code file change in this item (it is a DB object). No Flyway migration auto-applies it (Flyway disabled, `ddl-auto: validate`).

### Implementation approach
1. **Determine duplication:** confirm whether the Java path calls `wb_init_busi_data` on tenant creation. If it does, the DEV trigger means tenant init can run **twice** on a DEV insert (trigger + Java) — or DEV relies on the trigger while UAT/PROD relies on Java, a silent behavioral split.
2. **Recommended:** since UAT/PROD run against the live JEX DB (no such trigger) and tenant creation is manual DBA SQL + Java-side `wb_init_busi_data`, **drop the DEV trigger** so DEV matches the real target. Deliver as a **manual DBA SQL script** under `Claude Code (AI)/sql/` (e.g. `busi_infm_init_data_trigger_drop_{YYYYMMDD}.sql`), DBA-run, not Flyway — per CLAUDE.md §2. Document that it removes a DEV-only convenience and that init now flows through Java/manual SQL on every environment.
3. If investigation shows the trigger is genuinely required (Java does **not** init), the correct fix is to make the Java path own init consistently across environments — not to add the trigger to JEX (that would be a legacy DDL change requiring PO + DBA).
4. Record the decision in `wecms-api/docs/database/`.

### Expected behavior change
- After dropping the DEV trigger: inserting a `BUSI_INFM` row in DEV no longer auto-runs `wb_init_busi_data` — DEV behaves like UAT/PROD. Tenant init must come from the Java path / manual SQL (which is already the operational model).
- No application-code or wire change.

### Risk analysis
- **Medium.** Risk: if some DEV flow relied on the trigger and the Java path does **not** init on that flow, new DEV tenants would miss init data. Mitigate by confirming the Java `wb_init_busi_data` call covers tenant creation **before** dropping the trigger, and by testing tenant creation on a DEV copy.
- DEV-only blast radius — cannot affect UAT/PROD (trigger absent there).

### Required tests
- On a DEV/local DB: create a new `BUSI_INFM` tenant via the supported path (manual SQL / Java) after the trigger is dropped; assert the expected child rows from `wb_init_busi_data` exist exactly once (no double-init, no missing-init).
- Confirm `ddl-auto: validate` still passes (the trigger is not part of Hibernate validation; no entity impact).

### Rollback plan
The trigger is DBA-managed. Re-create it from the captured definition (recorded in the decision doc) if init regressions appear. No application rollback needed.

---

## Execution order & dependencies

1. **A1** — first; correctness, smallest, repository method already exists.
2. **A2** — highest perf value; A2-1/A2-2/A2-3 are independent of one another; A2-4 needs one new aggregate query.
3. **A5** — investigation + DBA drop; independent of the Java items.
4. **A4** and **A3** — lowest urgency; A4 after the batch-owner confirmation, A3 after the JDBC-batch config gate.

Each item is a standalone MR. A2-3 may share an MR with the relevant A3 site (same `EInvoiceScrapeService`/payment write legs) if convenient, but is not required to.

## Cross-cutting constraints (apply to every item)
- **No legacy schema change.** No table/column/PK/index/function change in either DB except the A5 DEV-only trigger drop (DBA script, not Flyway).
- **Key generation stays funneled** through `LegacyFunctionRepository` → `wb_get_next_val`; no format change, no identity/sequence substitution.
- **No wire/API/DTO change** in any Phase A item — these are internal query/wiring fixes. If any item is found to require a contract change, it moves to Phase B.
- **Tenant scope (`BIZ_KEY`)** must be carried into every new/changed query.
- Follow `feedback_preserve_existing_code.md`: touch only the cited lines/methods; do not refactor adjacent completed code.
