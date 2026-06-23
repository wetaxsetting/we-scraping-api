# `wb_get_next_val` Centralization Audit — FINAL STATUS

> _**Historical one-off audit.** A point-in-time record of a completed verification / remediation — findings are preserved unchanged. For the feature’s current state see its `features/.../refactor-gap-analysis.md`; for context see the [audits index](README.md)._

- **Date:** 2026-06-09
- **System:** Refactor (wecms-api)
- **Scope:** Route every in-scope business key / sequence generation through the single `LegacyFunctionRepository` → `wb_get_next_val` funnel, matching the live JEX key-generation so the refactor never mints a colliding key on a shared JEX database.
- **Authoritative migration:** [`V155__wb_get_next_val_centralize.sql`](../../src/main/resources/db/migration/V155__wb_get_next_val_centralize.sql) (supersedes V140).

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

## 4. Documented exceptions (Java-side generation kept by design — do NOT convert)

| # | Site | table.column | Why excluded |
|---|---|---|---|
| 1 | `DocNoGenerator` | `expt_resol_mstr.doc_no` | JEX branch is env-set-driven (`BUSI/SYS_ENV_SET_INFM` prefix + date); PO-declined port; no unique constraint (legacy accepts the race) |
| 2 | `CardTransactionsService.insertMemoOnce` | `memo_infm.seq_no` | `MEMO_INFM` not in the PROD Active Table List |
| 3 | `OrganizerRuleService.nextDispOrd`, `PurposeSettingsService` disp_ord | `rmrk_org_infm.disp_ord`, `busi_usag_set_infm.disp_ord` | Display-order / UI ordering (the PK `use_usag_cd` IS funneled) |
| 4 | `ExchangeRateBatchService.findHighestSeqNo` | `bank_exch_rate_infm.seq_no` (Woori round) | Round-probe read, not a row-key allocator (row PK `exch_seq_no` IS funneled) |
| 5 | `InternalTransferKeyGenerator`, `OverseasPaymentKeyGenerator`, `*PaymentService` detail numbering | `scdul_pymn_no`, `ovrs_pymn_no`, `scdul_pymn_detl_no` | Date-prefixed composite-key architecture via `*_KEY_SEQ` counter tables; different format than JEX plain-int; collision-free (disjoint range). See **Open Decision 2** |

---

## 5. Open decisions (recorded, not actioned)

See ../planning/open-questions/wb-get-next-val-open-decisions.md _(removed — historical `planning/` folder deleted by project decision)_.

- **OD-1 — Contacts `trco_acct_key`:** `ContactsService` accepts `trco_acct_key` from the client request instead of generating it via `wb_get_next_val` (the e-Invoice path to the same table IS funneled, branch #28). Create-vs-update key-contract review. **Do not change now.**
- **OD-2 — Date-prefixed payment keys:** exception #5 above. PO/architecture decision. **Do not change now.**

---

## 6. Verification commands

```bash
./gradlew compileJava compileTestJava      # success (pre-existing unrelated -Xlint notes only)
./gradlew test --tests <touched suites>    # green (audit-log, purpose, oat, einvoice, operation, vas)
```
