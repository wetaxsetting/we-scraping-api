# `wb_get_next_val` Centralization Audit — FINAL STATUS


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](../README.md)._

- **Date:** 2026-06-09
- **System:** Refactor (wecms-api)
- **Scope:** Route every in-scope business key / sequence generation through the single `LegacyFunctionRepository` → `wb_get_next_val` funnel, matching the live JEX key-generation so the refactor never mints a colliding key on a shared JEX database.
- **Authoritative migration (as written 2026-06-09, historical numbering):** `V155__wb_get_next_val_centralize.sql` (superseded the then-current V140).
  > ⚠️ **Current pointer:** that file was renumbered by the [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md) and the live function now ships as
  > [`V140__wb_get_next_val.sql`](../../../src/main/resources/db/migration/V140__wb_get_next_val.sql) (954 lines, the curated 28-branch body).
  > `V155` today is `V155__mrec_pic_store.sql` — an **unrelated** table. Read V140, not V155.
- **Closure (2026-06-24): 100% legacy key-generation parity achieved.** Parity verified against the live legacy function — **no `wb_get_next_val` enhancement required**; all in-scope business keys match legacy (funnel or value-equivalent). The last value gap, `TRCO_ACCT_INFO.trco_acct_key` (OD-1), is now **CLOSED** — `ContactsService` routes through `LegacyFunctionRepository.nextTrcoAcctKey → wb_get_next_val` (backend caller-routing only; no DB function/DDL/schema/migration/config/data change). A **final exhaustive post-OD-1 source sweep** then confirmed **0 Class-D gaps, 0 missing `wb_get_next_val` branches, and no Java business-key generation outside the funnel** — every business key now follows `Service → LegacyFunctionRepository.nextXXX → wb_get_next_val → old DB data`. See [wb-get-next-val-parity-closure-2026-06-24.md](../governance/wb-get-next-val-parity-closure-2026-06-24.md) §6.

---

## 1. Final status

- ✅ **All in-scope business key generation now goes through `LegacyFunctionRepository` → `wb_get_next_val`.**
- ✅ **V155 contains the curated 28 required branches** (10 kept from V140 + 2 regressed branches restored + 16 added).
- ✅ **Compile (`compileJava` + `compileTestJava`) and affected tests pass.**
- ✅ **Single executable gateway verified** (2026-06-09): exactly one executable `wb_get_next_val` SQL call exists, in `LegacyFunctionRepository`.

### Funnel architecture (confirmed)

```text
Spring services
    → LegacyFunctionRepository            (the ONLY executor: em.createNativeQuery("SELECT wb_get_next_val(...)"))
    → wb_get_next_val()                   (V155, 28 curated branches, verbatim from live JEX)
    → JEX-compatible key generation
```

**Rules (non-negotiable):**

1. Exactly one executable SQL call to `wb_get_next_val` may exist.
2. The only executor is `LegacyFunctionRepository`.
3. No repository/service may bypass the funnel (no native `@Query` / `createNativeQuery` calling `wb_get_next_val` elsewhere).
4. Do not add direct `wb_get_next_val` calls outside `LegacyFunctionRepository`. New allocation = V155 branch (verbatim from live JEX) + named funnel helper + call the helper.
5. Remaining Java-side generators are documented exceptions only (see §4).

---

## 2. V155 branch inventory (28)

| # | table.column | Mechanism (verbatim from live JEX) | Primary caller(s) |
|---|---|---|---|
| 1 | `trco_infm.trco_key` | `nextval('sq_trco_key')` | ContactsService, EInvoiceTrcoAutoRegistrar |
| 2 | `appr_line_infm.appr_line_no` | per-tenant MAX+1 | ApprovalLineService |
| 3 | `chart_acct_infm.chart_acct_seq_no` | per-tenant MAX+1 | ChartOfAccountsService |
| 4 | `rmrk_org_infm.rmrk_seq_no` | per-tenant MAX+1 | OrganizerRuleService |
| 5 | `card_trns_hstr.card_seq_no` | per-tenant MAX+1 | CardTransactionSyncService |
| 6 | `trco_info_file.file_id` | per-contact MAX+1 | ContactsFileService |
| 7 | `acct_trns_hstr.acct_seq_no` | per-tenant MAX+1 | BankTransactionSyncService |
| 8 | `acct_trns_rcv_pay_infm.rcv_pay_seq_no` | per (biz_key, acct_seq_no) MAX+1 | SmartOrganizerService, **TransactionService** |
| 9 | `busi_infm.biz_key` | `{ntnl}_{chnl}_{NNNNNN}` | BusinessKeyGeneratorService (inert) |
| 10 | `empl_infm.empl_no` | `{YYYYMMDD}_{NNN}` | EmployeeNumberGeneratorService (inert) |
| 11 | `audit_logs_infm.audit_logs_seq_no` | whole-table MAX+1 (V66 body restored) | AuditLogService |
| 12 / 12b | `busi_usag_set_infm.use_usag_cd` | per (biz_key, grp) / plain 10-digit | PurposeSettingsService, PurposeExcelService |
| 13 | `ext_tran_call_his.seq_no` | per (svc_id, ref_date) MAX+1 | SyncHistoryWriter, EInvoiceScrapeService, OatNxisasService, OperationStatusService |
| 14 | `napas_remit_trns_hstr.seq_no` | per-tenant MAX+1 | OwnAccountTransferService |
| 15 | `bank_cert_info.seq_no` | per-tenant MAX+1 | BankCredentialService, LoginCredentialsService |
| 16 | `busi_match_log.match_log_seq` | per-tenant MAX+1 | MatchService, DomesticPaymentLineMatchService, PayrollBankingMatchService |
| 17 | `busi_vas_acct_infm.vas_acct_seq_no` | per-tenant MAX+1 | VasAcctSeqNoGenerator |
| 18 | `gdt_cert_info.seq_no` | per-tenant MAX+1 | LoginCredentialsService |
| 19 | `user_pw_chg_his.reg_seq_no` | per-user MAX+1 | ChangePasswordService |
| 20 | `erp_trns_hstr.seq_no` | per-tenant MAX+1 | ErpTrnsHistoryWriter |
| 21 | `busa_doc_rcv_pay_his.rcv_pay_his_seq_no` | per (biz_key, doc_id) MAX+1 | CardTransactionsService |
| 22 | `ovrs_pymn_sumr_detl.ovrs_pymn_detl_no` | per-tenant MAX+1 (mstr∪detl) | OverseasPaymentExcelService |
| 23 | `bank_exch_rate_infm.exch_seq_no` | per (biz_key, bank_cd, ref_date, ccy_cd) MAX+1 | ExchangeRateBatchService |
| 24 | `expt_resol_mstr.appr_seq_no` | **global `nextval('sq_appr_seq_no')`** | Vendor/Expense/Payroll/Internal/Overseas + CardTransactions |
| 25 | `tb_pay_target.tran_seq` | **global `nextval('firm_seq_no')`** | DispatchKeyGenerator |
| 26 | `tb_pay_file_mst.file_seq_no` | **global `nextval('firm_file_seq_no')`** | DispatchKeyGenerator |
| 27 | `trco_actv_hstr.actv_seq` | **global `nextval('sq_actv_seq')`** | EInvoiceTrcoAutoRegistrar |
| 28 | `trco_acct_info.trco_acct_key` | per (biz_key, trco_key) MAX+1 | EInvoiceTrcoAutoRegistrar |

> Bold mechanisms are the high-impact semantic shifts: the Java per-tenant emulations were replaced with the JEX **global sequences**, removing the cross-system collision risk. Supporting sequences (`sq_appr_seq_no`/`firm_seq_no`/`firm_file_seq_no`/`sq_actv_seq`) pre-existed in V143/V144/V147/V148; V155 adds raise-only `setval` floors.

---

## 3. Gateway verification (2026-06-09)

Search of `src/main` + `src/test` for `wb_get_next_val(`, `createNativeQuery("…wb_get_next_val…")`, and every `@Query(nativeQuery=true)`:

- **Executable SQL containing `wb_get_next_val`: exactly 1** — `LegacyFunctionRepository` (`SELECT wb_get_next_val(...)`). All other `.java` mentions are Javadoc/comments or error-message string literals (`"wb_get_next_val returned null …"` in `AuditLogService` / `CompanyBootstrapService`).
- **Former bypasses removed:** `AuditLogSeqNoRepository` (native `@Query`) deleted; `PurposeSettingsRepository.nextUseUsagCd` (native `@Query`) removed.
- **No repository/service bypasses the funnel.** Other `createNativeQuery`/`nativeQuery=true` sites (SmartOrganizer list SQL, `*_KEY_SEQ` upserts, OverseasPayment queries) contain no `wb_get_next_val`.

---

## 4. Documented exceptions (not parity gaps)

> **Stale historical notes corrected on 2026-06-24.** The original §4 (preserved in git history) listed `DocNoGenerator` and the payment-key generators (`InternalTransferKeyGenerator` / `OverseasPaymentKeyGenerator` / detail numbering) as Java-side exceptions. Those entries reflected the pre-2026-06-09 state and **no longer represent current status**: `doc_no`, `scdul_pymn_no`, `ovrs_pymn_no`, `scdul_pymn_detl_no`, and `ovrs_pymn_detl_no` are now **funnel-matched or value-equivalent** to legacy (the env-set prefix/date logic for `doc_no` lives **inside** the DB function via `DocNoGenerator → LegacyFunctionRepository.nextDocNo → wb_get_next_val`; the date-prefixed `*_KEY_SEQ` counter-table architecture / OD-2 is **retired**). They have been removed from the exception list below. **The current authoritative status is** [wb-get-next-val-parity-closure-2026-06-24.md](../governance/wb-get-next-val-parity-closure-2026-06-24.md).

The following are the only retained exceptions — each is a **non-key field, a value-equivalent computation, or a non-business-key surrogate**, not a parity gap:

| # | Site | table.column | Why it is not a parity gap |
|---|---|---|---|
| 1 | `MemoInfmRepository` (via `CardTransactionsService`) | `memo_infm.seq_no` | **Value-equivalent** to the legacy `wb_get_next_val('memo_infm','seq_no',…)` branch — same `(biz_key, web_svc_id)` `MAX+1` logic; the only difference is location (Java vs DB function), the value is identical. `MEMO_INFM` is also outside the PROD Active Table List. |
| 2 | `TbFirmFileMstRepository` | `tb_firm_file_mst.file_seq` | Matches legacy IDO `TB_FIRM_FILE_MST_R002` `MAX(FILE_SEQ)+1`. Legacy has **no** `wb_get_next_val` branch for this table — funneling it would *diverge* from legacy, not converge. |
| 3 | `OrganizerRuleService.nextDispOrd`, `PurposeSettingsService` disp_ord | `rmrk_org_infm.disp_ord`, `busi_usag_set_infm.disp_ord` | Presentation / UI ordering — not a business key. The row PKs (`rmrk_seq_no`, `use_usag_cd`) ARE funneled. |
| 4 | `ExchangeRateBatchService.findHighestSeqNo` | `bank_exch_rate_infm.seq_no` (Woori round) | Round-probe read, not a row-key allocator. The row PK `exch_seq_no` IS funneled (branch #23). |
| 5 | Various | `USER_LOGIN_HIS.reg_uid`, `NXSIAS_MODULE_TRAN_LOG_HIS.uuid`, vendor transaction UUIDs (`Tno`), trace IDs, JWT IDs, temporary file names | UUID / log / trace / temp surrogates — **not** legacy `wb_get_next_val` business keys, so no parity gap. |

---

## 5. Open decisions (recorded, not actioned)

_(Historical `planning/open-questions/` folder removed by project decision; the OD record is retained inline here and in the 2026-06-24 closure doc.)_

- **OD-1 — Contacts `trco_acct_key`: CLOSED (2026-06-24).** The contacts-create (and update-new) path in `ContactsService` now allocates `trco_acct_key` via `LegacyFunctionRepository.nextTrcoAcctKey(bizKey, trcoKey) → wb_get_next_val('trco_acct_info','trco_acct_key', …)` (branch #28), matching the e-Invoice path. Existing accounts preserve their key on update (existence-checked, not blank-checked); deletes are unchanged; the frontend placeholder key is accepted but never persisted for new accounts. Implemented as **backend caller-routing only** — no DB function/DDL/schema/migration/config/data change. Verified by `ContactsServiceTest`, `ContactsControllerTest`, `compileJava`.
- **OD-2 — Date-prefixed payment keys: RETIRED / CLOSED (2026-06-24).** The former date-prefixed `*_KEY_SEQ` counter-table architecture was reversed on 2026-06-09; `scdul_pymn_no` / `ovrs_pymn_no` now route through the funnel and the detail numbers (`scdul_pymn_detl_no` / `ovrs_pymn_detl_no`) are funnel-seeded and value-equivalent. The 2026-06-24 closure audit confirms these now match legacy. No open decision remains.

---

## 6. Verification commands

```bash
./gradlew compileJava compileTestJava      # success (pre-existing unrelated -Xlint notes only)
./gradlew test --tests <touched suites>    # green (audit-log, purpose, oat, einvoice, operation, vas)
```
