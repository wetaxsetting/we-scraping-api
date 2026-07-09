# Refactor DEV `wb_get_next_val` — definitive branch list

- **Date:** 2026-07-03
- **Purpose:** given the legacy-UAT vs refactor-DEV parity audit, state **exactly**
  which branches should exist in the refactor DEV `wb_get_next_val`, classified as
  Must-exist / Missing / Dormant-parity / Removable, verified against real
  executable callers in `wecms-api/src`.
- **Inputs:** `docs/wb_get_next_val_legacy_uat.sql` (~130 legacy branches),
  `docs/wb_get_next_val_refec_dev.sql` (66 DEV branches, numbered #1–#66),
  `wecms-api/src` (LegacyFunctionRepository + all services/generators/repositories).
- **Caller method:** every `LegacyFunctionRepository` helper + every generic
  `legacyFunctions.nextVal(...)` call site was grep-verified for **real, non-test
  executable callers** (see §5). Two branches (`audit_logs_infm`, `busi_usag_set_infm`
  no-group) were moved **Java-side** on 2026-07-03 (prior fix) and are no longer
  invoked through the function — reflected below.

---

## 1. Summary

| Metric | Count |
|---|---|
| Total legacy UAT branches (distinct table.field cases) | ~130 |
| Total refactor DEV branches | **66** |
| **A. Must exist** (live caller via the function, incl. DBA-only) | **42** |
| **B. Should exist but missing** | **0** |
| **C. Dormant parity** (present, no live caller, safe to keep) | **22** |
| **D. Removable** (superseded / dead) | **2** |
| Legacy branches intentionally absent from DEV (feature not ported) | ~64 |

Breakdown of the 42 must-exist: **40** invoked by live Spring code through the
function + **2** invoked only by the DBA manual tenant-setup SQL
(`busi_infm.biz_key`, `empl_infm.empl_no`).

**Nothing the refactor calls through `wb_get_next_val` is missing from DEV (B = 0).**

---

## 2. A — Must exist in Refactor DEV (42)

Recommended status **Keep** for all. "Legacy" = present in legacy UAT (all are).
Logic shorthand: `MAX+1` = `coalesce(max(col::numeric),0)+1`; `nextval` = global sequence.

| DEV# | Table.Column | Legacy logic (scope) | In DEV? | Real caller(s) | Status |
|---|---|---|---|---|---|
| 1 | expt_resol_mstr.appr_seq_no | `nextval('sq_appr_seq_no')` | Yes | 6× payment create services (`nextApprSeqNo`) | Keep |
| 2 | tb_pay_target.tran_seq | `nextval('firm_seq_no')` | Yes | DispatchKeyGenerator | Keep |
| 3 | tb_firm_tran.tran_seq | `nextval('firm_seq_no')` | Yes | DepositorInquiryService | Keep |
| 4 | tb_pay_file_mst.file_seq_no | `nextval('firm_file_seq_no')` | Yes | DispatchKeyGenerator | Keep |
| 6 | trco_actv_hstr.actv_seq | `nextval('sq_actv_seq')` | Yes | EInvoiceTrcoAutoRegistrar | Keep |
| 7 | trco_infm.trco_key | `nextval('sq_trco_key')` | Yes | ContactsService, EInvoiceTrcoAutoRegistrar | Keep |
| 8 | appr_line_infm.appr_line_no | MAX+1 (biz_key) | Yes | ApprovalLineService | Keep |
| 9 | chart_acct_infm.chart_acct_seq_no | MAX+1 (biz_key) | Yes | ChartOfAccountsService | Keep |
| 10 | rmrk_org_infm.rmrk_seq_no | MAX+1 (biz_key) | Yes | OrganizerRuleService | Keep |
| 11 | card_trns_hstr.card_seq_no | MAX+1 (biz_key) | Yes | CardTransactionPersister | Keep |
| 12 | trco_info_file.file_id | MAX+1 (trco_key=$3) | Yes | ContactsFileService | Keep |
| 13 | acct_trns_hstr.acct_seq_no | MAX+1 (biz_key) | Yes | BankTransactionSyncService | Keep |
| 14 | acct_trns_rcv_pay_infm.rcv_pay_seq_no | MAX+1 (biz_key, acct_seq_no) | Yes | TransactionService, SmartOrganizerService | Keep |
| 15 | busi_infm.biz_key | `{$3}_{$4}_{lpad(MAX(right(6))+1,6)}` | Yes | **DBA manual setup SQL** (no Spring caller) | Keep |
| 16 | empl_infm.empl_no | `{YYYYMMDD}_{lpad(COUNT(*)+1,3)}` (biz_key, day) | Yes | **DBA manual setup SQL** (no Spring caller) | Keep |
| 20 | tb_pay_file_data.seq | MAX(seq::int)+1 (biz_key, file_seq_no) | Yes | 6× dispatch executors (`nextTbPayFileDataSeq`) | Keep |
| 21 | ext_tran_call_his.seq_no | MAX+1 (svc_id, ref_date) | Yes | SyncHistoryWriter, OatNxisas, EInvoiceScrape, OperationStatus(×2) | Keep |
| 22 | napas_remit_trns_hstr.seq_no | MAX+1 (biz_key) | Yes | OwnAccountTransferService | Keep |
| 23 | bank_cert_info.seq_no | MAX+1 (biz_key) | Yes | BankCredentialService, LoginCredentialsService | Keep |
| 24 | busi_match_log.match_log_seq | MAX+1 (biz_key) | Yes | MatchService, DomesticPaymentLineMatch, PayrollBankingMatch | Keep |
| 25 | busi_vas_acct_infm.vas_acct_seq_no | MAX+1 (biz_key) | Yes | VasAcctSeqNoGenerator | Keep |
| 26 | gdt_cert_info.seq_no | MAX+1 (biz_key) | Yes | LoginCredentialsService | Keep |
| 27 | user_pw_chg_his.reg_seq_no | MAX+1 (user_id=$3) | Yes | ChangePasswordService | Keep |
| 28 | erp_trns_hstr.seq_no | MAX+1 (biz_key) | Yes | ErpTrnsHistoryWriter | Keep |
| 29 | busa_doc_rcv_pay_his.rcv_pay_his_seq_no | MAX+1 (biz_key, doc_id) | Yes | CardTransactionsService | Keep |
| 30 | ovrs_pymn_sumr_detl.ovrs_pymn_detl_no | MAX+1 over (mstr∪detl) (biz_key) | Yes | OverseasPaymentExcelService, ErpPaymentRefreshMasterImporter | Keep |
| 31 | trco_acct_info.trco_acct_key | MAX+1 (biz_key, trco_key) | Yes | ContactsService, EInvoiceTrcoAutoRegistrar | Keep |
| 32 | bank_exch_rate_infm.exch_seq_no | MAX+1 (biz_key, bank_cd, ref_date, ccy_cd) | Yes | ExchangeRateBatchService | Keep |
| 34 | trco_addr_infm.trco_addr_key | MAX+1 (biz_key, trco_key) | Yes | ContactsService | Keep |
| 36 | scdul_pymn_sumr.scdul_pymn_no | MAX+1 (biz_key) | Yes | 6× via InternalTransferKeyGenerator | Keep |
| 38 | scdul_pymn_sumr_detl.scdul_pymn_detl_no | MAX+1 (biz_key) | Yes | 5× payment create services (seed+increment) | Keep |
| 39 | expt_resol_mstr.doc_no | env-set `{prefix}_{date}_{seq}` daily MAX+1 | Yes | 8× via DocNoGenerator/ApprovalBoxFanout | Keep |
| 41 | expt_resol_detl.appr_detl_seq_no | MAX+1 (appr_seq_no=$4; biz_key guard $3) | Yes | **ApprovalBoxFanoutService.writeResolutionDetail** | Keep ⚠️ |
| 42 | expt_resol_file.file_id | MAX+1 (biz_key, appr_seq_no) | Yes | ApprovalAttachmentWriter, InternalTransferService | Keep |
| 47 | acct_mstr.prn_seq | MAX+1 (biz_key) | Yes | AccountRegistrationService | Keep |
| 48 | sys_doc_id_gen.doc_id | 'E'→`R-YYYYMMDD-nnn` (biz_key, doc_type, req_date) | Yes | CardTransactionsService.createReceipt | Keep |
| 52 | bltb_post_infm.post_seq_no | MAX+1 (biz_key, bltb_cd) | Yes | **ApprovalNotificationService** | Keep |
| 54 | bltb_comnt_infm.comnt_seq_no | MAX+1 (biz_key, bltb_cd) | Yes | **ApprovalNotificationService** | Keep |
| 56 | bltb_noti_infm.noti_seq_no | MAX+1 (biz_key, bltb_cd) | Yes | **ApprovalNotificationService** (×2 paths) | Keep |
| 61 | trco_ovrs_infm.trco_ovrs_key | MAX+1 (biz_key) | Yes | ContactsService | Keep |
| 63 | ovrs_pymn_sumr.ovrs_pymn_no | MAX+1 over (mstr∪sumr) (biz_key) | Yes | OverseasPaymentKeyGenerator, ExcelService, ErpImporter | Keep |
| 65 | memo_infm.seq_no | MAX+1 (biz_key, web_svc_id) | Yes | CardTransactionsService (memo) | Keep |

> **⚠️ #41 note (behavioural, not a branch-existence issue):** `writeResolutionDetail`
> calls `nextVal("expt_resol_detl","appr_detl_seq_no", bizKey, null, …)` — `$4`
> (appr_seq_no) is null, so the branch computes `MAX WHERE appr_seq_no = null` →
> always returns `1`. Matches the legacy quirk (predicate checks `$3`, body keys on
> `$4`). The branch **must exist** regardless; whether the caller should pass the real
> `apprSeqNo` as `$4` is a separate correctness question (out of scope for this
> branch-inventory task — flag for a follow-up review).

**Required global sequences** (the `nextval` branches above depend on these existing
in the DEV DB): `sq_appr_seq_no`, `firm_seq_no`, `firm_file_seq_no`, `sq_actv_seq`,
`sq_trco_key`, `sq_biz_key`.

---

## 3. B — Should exist but missing (0)

**None.** Every field the refactor allocates through `wb_get_next_val` maps to an
existing DEV branch, and every call passes parameters in the positions the branch
expects (verified in the parity audit Part A + this pass). No branch needs to be added.

---

## 4. C — Dormant parity — present, no live caller, safe to keep (22)

Recommended status **Dormant (keep)**. These have **no executable caller** today but
are copied verbatim from legacy UAT; keeping them guards a future caller against
divergence (project policy: "keep dormant parity branches"). Sub-typed by reason.

| DEV# | Table.Column | Legacy logic | Why dormant | Keep rationale |
|---|---|---|---|---|
| 5 | tb_pay_file_data.file_seq_no | `nextval('firm_file_seq_no')` | executors reuse #4's allocation | twin of active #4 |
| 18 | busi_usag_set_infm.use_usag_cd (grouped) | MAX(decimal)+1 (biz_key, usag_grp_cd) | no caller passes a non-blank group | `nextUseUsagCd` still delegates here if a group is ever passed |
| 33 | trco_addr_infm.trco_key | `nextval('sq_trco_key')` | address rows inherit parent contact key | twin of active #7 |
| 35 | trco_addr_infm.trco_bsun_post_adrs | MAX+1 (biz_key, trco_key) | client-supplied | OD-1 client-key family |
| 37 | scdul_pymn_sumr_detl.scdul_pymn_no | MAX+1 (biz_key) | detail copies master FK | FK copy, never generated |
| 40 | trco_acct_info.disp_ord | MAX+1 (biz_key, trco_key) | client-supplied disp order | display-order family |
| 43 | busa_doc_detl.disp_ord | COUNT(*)+1 (biz_key, doc_id) | scrape-supplied | display-order family |
| 44 | pgro_bsac_lnkn_infm.grp_cd | `{user_id}-{count+1}` | custom-group feature not ported | parity |
| 45 | user_usag_use_his.seq_no | MAX+1 (biz_key, user_id) | usage-history feature not ported | parity (D-candidate) |
| 46 | user_trco_use_his.seq_no | MAX+1 (biz_key, user_id) | contact-use-history not ported | parity (D-candidate) |
| 49 | busa_doc_mstr.doc_id | 'E'→R-…, IV-/BL- | e-invoice writes scraped GDT id, not this | parity (D-candidate) |
| 50 | einv_fee_detl.disp_ord | MAX+1 (biz_key, doc_id) | scrape-supplied | display-order family |
| 51 | einv_tax_detl.disp_ord | MAX+1 (biz_key, doc_id) | scrape-supplied | display-order family |
| 53 | bltb_comnt_infm.post_seq_no | MAX+1 (biz_key, bltb_cd) | comnt reuses parent post seq | FK copy (feature IS ported) |
| 55 | bltb_user_new_post_infm.post_seq_no | MAX+1 (biz_key, bltb_cd, user_id) | unread-marker reuses post seq | FK copy (feature IS ported) |
| 57 | bltb_noti_cont_infm.noti_seq_no | MAX+1 (biz_key, lang_cd, bltb_cd) | cont rows reuse noti seq | FK copy (feature IS ported) |
| 58 | busi_usag_set_infm.disp_ord (grouped) | MAX(disp_ord)+1 (biz_key, usag_grp_cd) | disp_ord computed Java-side | display-order family |
| 59 | expt_resol_apprvr_infm.apprvr_line_no | MAX+1 (biz_key, appr_seq_no) | assigned as Java loop ordinal | parity |
| 60 | card_trns_rcv_pay_infm.rcv_pay_seq_no | MAX+1 (biz_key, card_seq_no) | hardcoded `1` in Java (=legacy for fresh card) | parity (feature IS ported) |
| 62 | ovrs_pymn_mstr.ovrs_pymn_no | MAX+1 (biz_key) | refactor writes OVRS_PYMN_SUMR only | twin of active #63 |
| 64 | busi_usag_set_infm.disp_ord (no-group) | NULL-safe MAX+1 (biz_key) | disp_ord computed Java-side | display-order family |
| 66 | memo_infm_file.file_id | MAX+1 (biz_key, web_svc_id, seq_no) | memo attachments feature not wired yet | parity (D-candidate) |

---

## 5. D — Removable — superseded / dead (2)

Recommended status **Remove** (or keep as harmless parity). These were once
function-active but no code path reaches them anymore.

| DEV# | Table.Column | Why removable | Note |
|---|---|---|---|
| 17 | audit_logs_infm.audit_logs_seq_no | **Superseded 2026-07-03** — `LegacyFunctionRepository.nextAuditLogsSeqNo` now runs a whole-table `MAX+1` **directly** (not via the function), because the legacy UAT scoped body would collide. No code path invokes this branch. | The DEV #17 body (whole-table MAX+1) is what the Java query now replicates; removing the branch is safe. Keeping it is harmless (identical logic). |
| 19 | busi_usag_set_infm.use_usag_cd (no-group) | **Superseded 2026-07-03** — `nextUseUsagCd` now runs the plain 10-digit `MAX+1` **directly** for a blank group. No code path invokes this branch (the grouped #18 is only reachable via the function if a non-blank group is passed, which no caller does). | Removing #19 is safe; #18 stays as the defensive grouped path. |

> These two are the **only** genuinely dead branches. The "feature-not-ported"
> dormant rows in §4 flagged `(D-candidate)` (#45, #46, #49, #66, plus #44) are also
> removable if a lean function is preferred, but they are **kept as dormant parity**
> per project policy — none is called, so keeping vs removing is a style choice, not
> a correctness one.

---

## 6. Verification — real executable callers (§task requirement 5)

- **Executable `wb_get_next_val` call site:** exactly one — `LegacyFunctionRepository`
  (`em.createNativeQuery("SELECT wb_get_next_val(...)")` in the generic `nextVal`),
  plus the two now-Java-side native queries in the same class. No repository/service
  calls the function directly. (Prior audit + re-grep 2026-07-03.)
- **Named helper callers:** all 39 `LegacyFunctionRepository.next*` helpers have ≥1
  live caller (grep of `.<method>(` across `src/main/java`, excluding the definition
  file and tests). Counts in §2.
- **Generic `nextVal(...)` callers (6 sites):** `SmartOrganizerService` → #14;
  `ApprovalBoxFanoutService` → #41; `ApprovalNotificationService` → #52, #54, #56 (×2).
  These are why #41/#52/#54/#56 are classified **Must exist**, not dormant.
- **DBA-only:** `busi_infm.biz_key` (#15) + `empl_infm.empl_no` (#16) have no Spring
  caller (bootstrap package deleted 2026-06-10) but are called by the manual
  tenant-setup SQL against the DB — required for provisioning.
- **Generators verified as thin funnel delegators (not independent minting):**
  `VasAcctSeqNoGenerator`, `OverseasPaymentKeyGenerator`, `InternalTransferKeyGenerator`,
  `DispatchKeyGenerator`. `DocNoGenerator` → `nextDocNo` (#39).
- **Java-side allocators that never touch the function** (function-agnostic): hardcoded
  `1` (card_trns_rcv_pay), loop ordinal (apprvr_line_no), `findMaxDispOrd` (busi_usag
  disp_ord), scrape/client disp_ord, `_HIS` mirror copies — see the parity audit §B.4.

---

## 7. Answer — minimum correct `wb_get_next_val` for a brand-new DEV DB

If a brand-new DEV database is built today, deploy a `wb_get_next_val` with:

### Required branches (42) — the function is INCORRECT without these
The 42 rows in **§2** (40 app-active + `busi_infm.biz_key` + `empl_infm.empl_no`),
**plus** the six global sequences they read: `sq_appr_seq_no`, `firm_seq_no`,
`firm_file_seq_no`, `sq_actv_seq`, `sq_trco_key`, `sq_biz_key`. Omitting any of these
breaks a live feature (payment, contacts, banking sync, approval, notifications,
e-invoice, card, VAS, credentials, reports) or tenant provisioning.

### Optional parity branches (22) — safe to include, not required
The 22 rows in **§4**. Recommended to **keep** (project policy: guard future callers
against divergence, zero runtime cost). None is called today.

### Obsolete branches (2) — omit from a fresh build
`audit_logs_infm.audit_logs_seq_no` (#17) and `busi_usag_set_infm.use_usag_cd`
no-group (#19). Both are superseded by the Java-side `MAX+1` allocations in
`LegacyFunctionRepository` (2026-07-03) and are invoked by no code path. Safe to
omit; harmless to keep.

**Bottom line:** a correct minimal DEV function needs **42 branches + 6 sequences**.
A parity-complete DEV function is **42 required + 22 dormant = 64 branches** (drop
the 2 obsolete). The current DEV function ships 66 — i.e. the 64 keepers plus the 2
now-obsolete branches.

> Constraint honoured: **no code and no SQL function file was modified in this task** —
> analysis/documentation only. (The audit uses the 2026-07-03 state in which
> `nextAuditLogsSeqNo` / `nextUseUsagCd` no-group already allocate Java-side.)
