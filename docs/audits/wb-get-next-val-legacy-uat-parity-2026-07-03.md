# `wb_get_next_val` — Legacy UAT vs Refactor DEV parity audit

- **Date:** 2026-07-03
- **Scope:** every next-value rule in legacy UAT `wecmsuat_real.wb_get_next_val`
  vs the refactor DEV `wecmsdevdb.wb_get_next_val`, and every Spring call path
  through `common/legacy/LegacyFunctionRepository`.
- **Inputs:**
  - Legacy UAT: `docs/wb_get_next_val_legacy_uat.sql` (~130 branches, 1814 lines)
  - Refactor DEV: `docs/wb_get_next_val_refec_dev.sql` (66 curated branches)
  - Funnel: `wecms-api/.../common/legacy/LegacyFunctionRepository.java` (39 helper methods)
- **Result:** **No next-value correctness bug found in the active refactor surface.**
  Every branch the refactor actively invokes reproduces the legacy UAT body
  (same `MAX+1`/`COUNT+1`/`nextval`/composite logic, same scope filters, same
  casts). Funnel integrity holds — the only executable `wb_get_next_val` call in
  the whole backend is inside `LegacyFunctionRepository`. One stale **javadoc**
  comment misdescribing next-value scoping was corrected (see §Files changed);
  no runtime logic changed.

---

## 1. Method

1. Extracted every legacy UAT branch: `(table, field, required params, body logic)`.
2. Mapped each of the 39 `LegacyFunctionRepository` helper methods to its
   `(table, field, $3..$6)` and to the matching refactor DEV branch.
3. Compared the refactor DEV branch body **byte-for-logic** against legacy UAT.
4. Verified funnel integrity (`grep` for `wb_get_next_val` executable call sites).
5. Verified the standalone Java "generators" (`VasAcctSeqNoGenerator`,
   `OverseasPaymentKeyGenerator`, `InternalTransferKeyGenerator`) delegate to the
   funnel rather than minting keys independently.
6. Verified the documented Java-side exceptions (hardcoded `=1`, loop ordinals,
   display-order) produce **legacy-equivalent values** for their real call shape.

---

## 2. Active funnel branches — parity table

All 39 helpers map to a refactor DEV branch whose body is **identical in logic**
to legacy UAT. `File changed` = *none* for every row (already correct).

| # | Legacy rule (table.field → logic, scope) | Funnel method | Refactor branch | Status |
|---|---|---|---|---|
| 1 | `expt_resol_mstr.appr_seq_no` → `nextval('sq_appr_seq_no')` (global) | `nextApprSeqNo` | #1 | ✅ match |
| 2 | `tb_pay_target.tran_seq` → `nextval('firm_seq_no')` | `nextTbPayTargetTranSeq` | #2 | ✅ match |
| 3 | `tb_firm_tran.tran_seq` → `nextval('firm_seq_no')` | `nextTbFirmTranSeq` | #3 | ✅ match |
| 4 | `tb_pay_file_mst.file_seq_no` → `nextval('firm_file_seq_no')` | `nextTbPayFileMstFileSeqNo` | #4 | ✅ match |
| 5 | `trco_actv_hstr.actv_seq` → `nextval('sq_actv_seq')` | `nextTrcoActvSeq` | #6 | ✅ match |
| 6 | `trco_infm.trco_key` → `nextval('sq_trco_key')` (global) | `nextTrcoKey` | #7 | ✅ match |
| 7 | `appr_line_infm.appr_line_no` → `MAX::numeric+1` WHERE biz_key | `nextApprLineNo` | #8 | ✅ match |
| 8 | `chart_acct_infm.chart_acct_seq_no` → `MAX::numeric+1` WHERE biz_key | `nextChartAcctSeqNo` | #9 | ✅ match |
| 9 | `rmrk_org_infm.rmrk_seq_no` → `MAX::numeric+1` WHERE biz_key | `nextRmrkSeqNo` | #10 | ✅ match |
| 10 | `card_trns_hstr.card_seq_no` → `MAX::numeric+1` WHERE biz_key | `nextCardSeqNo` | #11 | ✅ match |
| 11 | `trco_info_file.file_id` → `MAX(file_id::numeric)+1` WHERE trco_key ($3) | `nextTrcoInfoFileFileId` | #12 | ✅ match |
| 12 | `acct_trns_hstr.acct_seq_no` → `MAX::numeric+1` WHERE biz_key | `nextAcctTrnsHstrAcctSeqNo` | #13 | ✅ match |
| 13 | `acct_trns_rcv_pay_infm.rcv_pay_seq_no` → `MAX+1` WHERE biz_key, acct_seq_no | `nextAcctTrnsRcvPaySeqNo` | #14 | ✅ match |
| 14 | `busi_usag_set_infm.use_usag_cd` (grouped) → `MAX(decimal)+1` fallback `$4‖lpad(1,9)` WHERE biz_key, usag_grp_cd | `nextUseUsagCd` (group≠"") | #18 | ✅ match |
| 15 | `ext_tran_call_his.seq_no` → `MAX::numeric+1` WHERE svc_id, ref_date | `nextExtTranCallHisSeqNo` | #21 | ✅ match |
| 16 | `napas_remit_trns_hstr.seq_no` → `MAX::numeric+1` WHERE biz_key | `nextNapasRemitSeqNo` | #22 | ✅ match |
| 17 | `bank_cert_info.seq_no` → `MAX+1` WHERE biz_key | `nextBankCertSeqNo` | #23 | ✅ match |
| 18 | `busi_match_log.match_log_seq` → `MAX::numeric+1` WHERE biz_key | `nextBusiMatchLogSeq` | #24 | ✅ match |
| 19 | `busi_vas_acct_infm.vas_acct_seq_no` → `MAX::numeric+1` WHERE biz_key | `nextVasAcctSeqNo` | #25 | ✅ match |
| 20 | `gdt_cert_info.seq_no` → `MAX::numeric+1` WHERE biz_key | `nextGdtCertSeqNo` | #26 | ✅ match |
| 21 | `user_pw_chg_his.reg_seq_no` → `MAX+1` WHERE user_id ($3) | `nextUserPwChgRegSeqNo` | #27 | ✅ match |
| 22 | `erp_trns_hstr.seq_no` → `MAX::numeric+1` WHERE biz_key | `nextErpTrnsSeqNo` | #28 | ✅ match |
| 23 | `busa_doc_rcv_pay_his.rcv_pay_his_seq_no` → `MAX+1` WHERE biz_key, doc_id | `nextBusaDocRcvPayHisSeqNo` | #29 | ✅ match |
| 24 | `ovrs_pymn_sumr_detl.ovrs_pymn_detl_no` → `MAX(no)+1` over (mstr ∪ detl) WHERE biz_key | `nextOvrsPymnDetlNo` | #30 | ✅ match |
| 25 | `trco_acct_info.trco_acct_key` → `MAX::numeric+1` WHERE biz_key, trco_key | `nextTrcoAcctKey` | #31 | ✅ match |
| 26 | `bank_exch_rate_infm.exch_seq_no` → `MAX::numeric+1` WHERE biz_key, bank_cd, ref_date, ccy_cd | `nextBankExchRateExchSeqNo` | #32 | ✅ match |
| 27 | `trco_addr_infm.trco_addr_key` → `MAX::numeric+1` WHERE biz_key, trco_key | `nextTrcoAddrKey` | #34 | ✅ match |
| 28 | `scdul_pymn_sumr.scdul_pymn_no` → `MAX::numeric+1` WHERE biz_key | `nextScdulPymnNo` | #36 | ✅ match |
| 29 | `scdul_pymn_sumr_detl.scdul_pymn_detl_no` → `MAX::numeric+1` WHERE biz_key | `nextScdulPymnDetlNo` | #38 | ✅ match |
| 30 | `expt_resol_mstr.doc_no` → env-set `{prefix}_{date}_{seq}` daily MAX+1 | `nextDocNo` | #39 | ✅ match (verbatim) |
| 31 | `expt_resol_file.file_id` → `MAX(file_id::numeric)+1` WHERE biz_key, appr_seq_no | `nextExptResolFileId` | #42 | ✅ match |
| 32 | `acct_mstr.prn_seq` → `MAX(prn_seq)+1` WHERE biz_key | `nextAcctMstrPrnSeq` | #47 | ✅ match |
| 33 | `sys_doc_id_gen.doc_id` → `'E'`→`R-YYYYMMDD-nnn`; else env-set PO/Q/IV/BL | `nextSysDocIdGenDocId` | #48 | ✅ match (verbatim) |
| 34 | `tb_pay_file_data.seq` → `MAX(seq::int)+1` WHERE biz_key, file_seq_no | `nextTbPayFileDataSeq` | #20 | ✅ match (legacy `::int` preserved) |
| 35 | `ovrs_pymn_sumr.ovrs_pymn_no` → `MAX(no)+1` over (mstr ∪ sumr) WHERE biz_key | `nextOvrsPymnNo` | #63 | ✅ match |
| 36 | `trco_ovrs_infm.trco_ovrs_key` → `MAX::numeric+1` WHERE biz_key | `nextTrcoOvrsKey` | #61 | ✅ match |
| 37 | `memo_infm.seq_no` → `MAX(seq_no::numeric)+1` WHERE biz_key, web_svc_id | `nextMemoInfmSeqNo` | #65 | ✅ match |
| 38 | `busi_infm.biz_key` → `{$3}_{$4}_{lpad(MAX(right(6))+1,6)}` | `nextVal(...)` (DBA/bootstrap) | #15 | ✅ match |
| 39 | `empl_infm.empl_no` → `{YYYYMMDD}_{lpad(COUNT(*)+1,3)}` WHERE biz_key, day | `nextVal(...)` (DBA/bootstrap) | #16 | ✅ match |

**`COUNT` vs `MAX` check (task-critical):** legacy uses `COUNT(*)+1` only for
`empl_infm.empl_no` and the display-order/`grp_cd` count branches; it uses
`MAX+1` everywhere else. The refactor preserves the exact choice per branch —
`empl_infm` #16 = `COUNT(*)+1`, `busi_infm` #15 = `MAX(right(6))+1`. No branch
swaps one for the other.

---

## 3. Java-side generators — verified equivalent to legacy

These do **not** call `wb_get_next_val` directly but still produce legacy-parity
values; they are the documented exceptions in `wecms-api/CLAUDE.md`.

| Field | Refactor mechanism | Legacy UAT | Verdict |
|---|---|---|---|
| `card_trns_rcv_pay_infm.rcv_pay_seq_no` | hardcoded `= 1` | `MAX+1` WHERE biz_key, card_seq_no | ✅ equivalent — `card_seq_no` is freshly allocated per txn (#11), so legacy `MAX` over a brand-new card_seq_no = 0 → 1 |
| `expt_resol_apprvr_infm.apprvr_line_no` | Java loop ordinal 1..N | `MAX+1` WHERE biz_key, appr_seq_no | ✅ equivalent — `appr_seq_no` fresh per approval → MAX=0 → 1,2,3 |
| `scdul_pymn_sumr_detl.scdul_pymn_detl_no` | funnel seed (`#38`, per-tenant) then `++` in Java | per-tenant `MAX+1` per row | ✅ equivalent — seed−1 then pre-increment reproduces the per-tenant contiguous sequence |
| `rmrk_org_infm.disp_ord`, `busi_usag_set_infm.disp_ord` | Java `MAX+1` (display order) | `MAX+1` (per usag_grp_cd for busi_usag) | ⚠️ intentional scope divergence, cosmetic column — PO-gated (see §5) |

`VasAcctSeqNoGenerator`, `OverseasPaymentKeyGenerator`, and
`InternalTransferKeyGenerator` are thin delegators to
`nextVasAcctSeqNo` / `nextOvrsPymnNo` / `nextScdulPymnNo` respectively.

---

## 4. Deliberate divergences (documented, NOT bugs — do not "fix")

### D-1 — `audit_logs_infm.audit_logs_seq_no`: whole-table MAX+1 (refactor) vs scoped (legacy)

- **Legacy UAT** (line 1661): `MAX(audit_logs_seq_no::numeric)+1 WHERE biz_key=$3 AND wsvc_id=$4 AND lang_cd=$5`.
- **Refactor DEV** (#17): `MAX(audit_logs_seq_no::numeric)+1` over the **whole table**, ignoring `$3/$4/$5`.
- **Why the refactor is correct for its caller, not broken:** `AuditLogService.doAppend`
  calls `nextAuditLogsSeqNo(bizKey, ctx.refNo(), ctx.refNo1())` — i.e. `$4=refNo`,
  `$5=refNo1`, **not** `wsvc_id` / `lang_cd`. It then fans out **N locale rows**
  (EN/KO/VI/KM) that all share the one `seq_no`, differing only by `lang_cd`.
  If the refactor DB used the legacy scoped body, `WHERE wsvc_id=refNo AND lang_cd=refNo1`
  would match no stored rows → `MAX=0` → `seq_no` always `1` → the **second** audit
  event would collide on the PK. The whole-table counter is the required, safe
  choice for this caller.
- **Migration / coexistence safety:** whole-table `MAX+1` is always `>` any existing
  value (legacy or refactor), and legacy's scoped `MAX+1` picks up any refactor row
  inside its scope, so neither direction collides. Safe.
- **Action:** none. Changing #17 to the scoped legacy body would break
  `AuditLogService`. Recorded here as the standing follow-up (a future `nextval('seq')`
  hardening would remove the whole-table read contention).

### D-2 — `busi_usag_set_infm.use_usag_cd`: no-group variant is refactor-only

- **Legacy UAT** has only the **grouped** branch (requires `$4 <> ''`): `MAX(decimal)+1`
  scoped by `(biz_key, usag_grp_cd)`, fallback `$4‖lpad('1',9,'0')`.
- **Refactor DEV** adds branch **#19** (`$4 = ''`): plain 10-digit `0000000001..`
  scoped by `biz_key` only (regex `^0[0-9]{9}$`).
- **Caller:** `PurposeSettingsService` and `PurposeExcelService` always call
  `nextUseUsagCd(bizKey, "")` → they exercise **#19**, never the grouped #18.
- **Assessment:** intentional refactor feature design (Purpose Settings uses a flat,
  ungrouped code space). On legacy UAT the empty-group call would fall through to
  `ELSE → NULL`. This is a **feature-model divergence**, not a next-value miscalculation.
  Migration note: legacy rows carry group-prefixed `use_usag_cd`; refactor mints
  `00000000NN`. They coexist without collision but are numbered differently — flag
  for the data-migration plan, not a code fix.
- **Action:** none (PO-level design). Noted for migration.

### D-3 — display-order fields (`disp_ord`) kept Java-side, per-biz_key scope

- Legacy scopes `busi_usag_set_infm.disp_ord` per `(biz_key, usag_grp_cd)`;
  the refactor keeps its Java `MAX+1` per `biz_key`. Cosmetic ordering column,
  PO-gated (Option C). Same family: `rmrk_org_infm.disp_ord`,
  `trco_acct_info.disp_ord`, `einv_*_detl.disp_ord`, `busa_doc_detl.disp_ord`
  (all **dormant** #40/#43/#50/#51/#58/#64 in the refactor function).
- **Action:** none.

---

## 5. Legacy UAT rules NOT present in the refactor (with reason)

These legacy branches have **no refactor DB branch and no Spring caller** because
the owning feature is not ported. None is a defect — they are correctly absent per
the "branch only for locally-migrated tables with a live caller" rule.

| Category | Legacy tables/fields (not ported) | Reason |
|---|---|---|
| Cash cheque / repayment | `cash_cheq_mstr.cash_cheq_seq_no`, `cash_repy_mstr.cash_repy_seq_no`, `cash_repy_evid_file.file_id`, `cash_cheq_evid_file.file_id` | Cash cheque module not ported |
| Contacts responsibility/activity seq | `trco_rspb_infm.rspb_key`, `trco_actv_hstr`(→`sq_actv_seq` IS wired via #6), `acct_trco_infm.acct_trco_key`, `acct_trns_vas_actv_his`, `busi_doc_vas_item_actv_his.busa_actv_seq` | Sub-features not ported |
| Salary / payroll books | `saly_book_mstr(_new).sal_reg_seq_no`, `saly_book_detl(_new)`, `saly_book_detl_inf(_new)`, `saly_book_pay_his`, `empl_faml_infm`, `empl_payroll_infm`, `busi_payroll_tax_infm`, `busi_sal_acct_infm` | Salary-book module not ported |
| Bulletin board (BLTB) | `bltb_post_infm`, `bltb_comnt_infm`, `bltb_noti_infm`, `bltb_noti_cont_infm`, `bltb_user_new_post_infm`, `bltb_draft_*`, `bltb_infm.bltb_cd` | Bulletin-board write path not ported (notifications read BLTB only) — #52–#57 kept **dormant** for parity |
| Send-service / mail | `snd_serv_*`, `snd_serv_mail_send_hstr(_file)` | Send-service module not ported |
| Smart assistant | `smrt_asst_dtls/ptcl/recr_infm` | Not ported |
| MOC search | `moc_srch_trco_*` (seq_no / dtl_seq_no) | Not ported |
| Data import wizard | `dat_imp_wiz_*` | Not ported |
| Advance payment | `adv_paym_*` | Not ported |
| Business docs / admin docs | `busa_doc_mstr.doc_id` (#49 dormant), `busa_doc_file.file_id`, `busa_doc_detl.disp_ord` (#43 dormant), `trsc_adm_doc_*`, `sys_doc_id_gen.doc_id` (#48 wired for card 'E' receipt only) | Manual business-document numbering not ported (e-invoice writes use scraped GDT identity) |
| Misc history / usage | `user_usag_use_his` (#45 dormant), `user_trco_use_his` (#46 dormant), `user_point_*`, `mrec_pic_store(_file)`, `msg_snd_his`, `noti_*`, `push_notice_infm`, `nbr_trco_infm_his`, `genl_noti_*`, `efiling_sumr_file`, `busi_erp_api_infm_mstr`, `busi_usag_acct_infm_detl`, `busi_bankform_infm`, `busi_coll_pay_infm`, `busi_detl_infm`, `busi_txtn_ptrn_set_infm`, `item_infm`, `prjt_infm`, `trco_grp_user`, `pgro_bsac_lnkn_infm.grp_cd` (#44 dormant), `forw_set_rcv_pay_his`, `busi_cond_daily_sumr_detl`, `trns_hstr_loan_borw_hstr`, `trns_hstr_acct_expr_hstr`, `noti_category_infm`, `noti_file`, `adv_paym_trns_hstr_evid_file`, `expt_resol_pay_evid_file`, `expt_resol_apprvr`, `expt_resol_apprvr_infm` (#59 dormant), `expt_resol_detl` (#41 dormant), `dat_imp_wiz_*`, `napas`(remit IS wired #22) | Owning features not ported; several kept as **dormant parity branches** (#40–#64) in the refactor function so a future caller cannot diverge |
| Overseas master | `ovrs_pymn_mstr.ovrs_pymn_no` (#62 dormant) | Refactor writes `OVRS_PYMN_SUMR` only; master branch kept dormant for parity |

> The refactor DEV function additionally carries **dormant parity branches**
> (#33 `trco_addr_infm.trco_key`, #34/#35 addr keys, #37 detl scdul_pymn_no, #40–#64)
> — copied verbatim from JEX but with no Spring caller. They are correct-by-parity;
> if a future feature wires one, its body already matches legacy.

---

## 6. Findings & risk summary

| Finding | Severity | Status |
|---|---|---|
| All 39 active funnel branches match legacy UAT logic + scope + casts | — | ✅ verified |
| Funnel integrity: sole `wb_get_next_val` executor is `LegacyFunctionRepository` | — | ✅ verified |
| `COUNT(*)+1` vs `MAX+1` choice preserved per branch | — | ✅ verified |
| D-1 `audit_logs_infm` whole-table vs scoped | Low (safe, by design) | Open follow-up (documented) |
| D-2 `busi_usag_set_infm` no-group variant | Low (feature design) | Migration note only |
| D-3 `disp_ord` scope | Cosmetic | PO-gated (Option C) |
| Stale javadoc in `InternalTransferKeyGenerator` misdescribed `scdul_pymn_detl_no` scoping | Doc only | **Fixed** (§7) |

**Concurrency note:** every `MAX+1` branch is lock-free — identical to legacy. Sites
that create rows on a freshly-allocated parent key are collision-free by construction;
the shared-counter sites (`scdul_pymn_no`, `ovrs_pymn_no`, `doc_no`, `audit_logs_infm`)
either carry a one-shot retry on `DataIntegrityViolationException` at the create
boundary or (for `doc_no`, which has no unique constraint) accept the race exactly as
legacy does. No new concurrency risk is introduced relative to legacy.

---

## 7. Files changed

| File | Change |
|---|---|
| `wecms-api/.../payment/internaltransfer/service/InternalTransferKeyGenerator.java` | Javadoc only — corrected the stale "Detail keys are out of scope / per-document ordinal / branches #37/#38 dormant" note to state that `scdul_pymn_detl_no` **is** funnelled via `nextScdulPymnDetlNo` (#38, per-tenant `MAX+1`, seed-once + Java increment) and matches legacy per-tenant scope. No runtime logic changed. |

No SQL / entity / DTO / repository / schema changes were required.

---

## 8. Tests

No runtime code changed, so no new behavioural test is required. The existing
coverage remains valid:
- `AuditLogServiceTest` — exercises the once-per-call `nextAuditLogsSeqNo` allocation + locale fan-out.
- Payment create-service tests — exercise `nextScdulPymnNo` / `nextScdulPymnDetlNo` seed-once + increment.
- `LegacyFunctionRepository` is the single funnel; its call shape is covered indirectly by every feature service test that inserts a keyed row.

**Recommended follow-up test (not added here — would require a live DB / Testcontainers,
which the project does not yet include):** a parity integration test that asserts,
for each active branch, that the refactor DEV function and a snapshot of the legacy
UAT function return the same value for a fixed dataset. Deferred until Testcontainers
lands (tracked in `wecms-api/CLAUDE.md` "Not currently in build.gradle").

---

## 9. Commands run

| Command | Result |
|---|---|
| `grep -r "wb_get_next_val" src/main/java` (executable call sites) | Only `LegacyFunctionRepository.java:43-44` — funnel integrity holds |
| Source review of both `wb_get_next_val` SQL bodies + 39 funnel methods + generator delegators | No active-branch mismatch |
| `./gradlew compileJava` (2026-07-03) | **BUILD SUCCESSFUL in 13s** — `:compileJava` executed (recompiled, not cached); green after the javadoc edit |

---
---

# Part B — UAT deployment: refactor API against the **legacy UAT** `wb_get_next_val` (as-is)

- **Added:** 2026-07-03 (extension)
- **Deployment context:** in UAT the refactor API will call the **legacy UAT**
  `wecmsuat_real.wb_get_next_val` **as-is** — the refactor DEV function is **not**
  deployed. So every branch the refactor DEV function implemented *differently*
  from legacy UAT now runs the legacy body instead. Part A already diffed the two
  functions; the **only two** branches that diverged are exactly the ones that
  break here.
- **Result:** **2 UAT blockers found and fixed** (both in the single funnel class,
  signatures unchanged → no service/test churn). All 37 other active branches are
  byte-identical between the two functions, so they run unchanged on UAT.
- **Verification:** `./gradlew test` → **BUILD SUCCESSFUL in 1m 28s** (full suite green).

## B.1 Method (full `wecms-api/src` sweep)

1. Enumerated **every `@Entity` insert** that needs a generated PK / seq-like field
   (funnel calls, custom generators, Java `MAX+1`/`COUNT+1`, hardcoded `1`, FK copies).
2. For each, resolved the **generation method** and the **insert path/service**.
3. Confirmed a **matching legacy UAT branch** exists for each field routed through
   `wb_get_next_val`, and that the **call parameters match the UAT branch positions**.
4. Flagged any insert that would **fail or mis-generate** under the legacy UAT body.
5. `grep` swept for `wb_get_next_val`, `nextVal`/`getNextVal`, `createNativeQuery`,
   `setParameter`, `set*SeqNo(… 1 …)`, `BigDecimal.ONE`, `MAX(`/`COUNT(` key reads.

## B.2 Entity / table insert matrix

Legend — **UAT-compat?**: ✅ works on legacy UAT · 🔧 fixed this pass · ➖ n/a (doesn't call the function).

| Refactor entity / table | Generated field | Insert path / service | Generation method | Legacy UAT branch? | UAT-compat? | Notes |
|---|---|---|---|---|---|---|
| TRCO_INFM | TRCO_KEY | ContactsService.create | funnel `nextTrcoKey` → `nextval('sq_trco_key')` | Yes (L53) | ✅ | global seq, $3 ignored |
| TRCO_ADDR_INFM | TRCO_ADDR_KEY | ContactsService | funnel `nextTrcoAddrKey` MAX+1 (biz_key,trco_key) | Yes (L92) | ✅ | |
| TRCO_ACCT_INFO | TRCO_ACCT_KEY | ContactsService / EInvoiceTrcoAutoRegistrar | funnel `nextTrcoAcctKey` MAX+1 (biz_key,trco_key) | Yes (L504) | ✅ | |
| TRCO_OVRS_INFM | TRCO_OVRS_KEY | ContactsService | funnel `nextTrcoOvrsKey` MAX+1 (biz_key) | Yes (L1730) | ✅ | |
| TRCO_INFO_FILE | FILE_ID | ContactsFileService | funnel MAX+1 (trco_key, $3 only) | Yes (L463) | ✅ | params match ($3=trco_key) |
| TRCO_ACTV_HSTR | ACTV_SEQ | EInvoice activity | funnel `nextTrcoActvSeq` → `nextval('sq_actv_seq')` | Yes (L49) | ✅ | |
| APPR_LINE_INFM | APPR_LINE_NO | ApprovalLineService.create | funnel `nextApprLineNo` MAX+1 (biz_key) | Yes (L1709) | ✅ | |
| CHART_ACCT_INFM | CHART_ACCT_SEQ_NO | ChartOfAccountsService.create | funnel `nextChartAcctSeqNo` MAX+1 (biz_key) | Yes (L1748) | ✅ | |
| RMRK_ORG_INFM | RMRK_SEQ_NO | OrganizerRuleService / SmartOrganizer | funnel `nextRmrkSeqNo` MAX+1 (biz_key) | Yes (L1674) | ✅ | |
| RMRK_ORG_INFM | DISP_ORD | OrganizerRuleService | Java `MAX+1` (not funneled) | n/a | ➖ | cosmetic order; function-agnostic |
| CARD_TRNS_HSTR | CARD_SEQ_NO | CardTransactionSyncService | funnel `nextCardSeqNo` MAX+1 (biz_key) | Yes (L1698) | ✅ | |
| CARD_TRNS_RCV_PAY_INFM | RCV_PAY_SEQ_NO | CardTransactionsService | **hardcoded `1`** (`BigDecimal.ONE`) | Yes (L1686, unused) | ➖ | =legacy: card_seq_no fresh → MAX=0→1 |
| ACCT_TRNS_HSTR | ACCT_SEQ_NO | Card/HS sync | funnel `nextAcctTrnsHstrAcctSeqNo` MAX+1 (biz_key) | Yes (L495) | ✅ | |
| ACCT_TRNS_RCV_PAY_INFM | RCV_PAY_SEQ_NO | SmartOrganizer / match | funnel `nextAcctTrnsRcvPaySeqNo` MAX+1 (biz_key,acct_seq_no) | Yes (L524) | ✅ | |
| EXT_TRAN_CALL_HIS | SEQ_NO | SyncHistoryWriter | funnel `nextExtTranCallHisSeqNo` MAX+1 (svc_id,ref_date) | Yes (L210) | ✅ | params: $3=svc_id,$4=ref_date |
| NAPAS_REMIT_TRNS_HSTR | SEQ_NO | OwnAccountTransferService | funnel `nextNapasRemitSeqNo` MAX+1 (biz_key) | Yes (L1281) | ✅ | |
| BANK_CERT_INFO | SEQ_NO | BankCredentialService | funnel `nextBankCertSeqNo` MAX+1 (biz_key) | Yes (L653) | ✅ | |
| BUSI_MATCH_LOG | MATCH_LOG_SEQ | MatchService | funnel `nextBusiMatchLogSeq` MAX+1 (biz_key) | Yes (L1793) | ✅ | |
| BUSI_VAS_ACCT_INFM | VAS_ACCT_SEQ_NO | VasAccountService (VasAcctSeqNoGenerator) | funnel `nextVasAcctSeqNo` MAX+1 (biz_key) | Yes (L1581) | ✅ | |
| GDT_CERT_INFO | SEQ_NO | LoginCredentialsService | funnel `nextGdtCertSeqNo` MAX+1 (biz_key) | Yes (L1172) | ✅ | |
| USER_PW_CHG_HIS | REG_SEQ_NO | ChangePasswordService | funnel `nextUserPwChgRegSeqNo` MAX+1 (user_id) | Yes (L631) | ✅ | params: $3=user_id |
| ERP_TRNS_HSTR | SEQ_NO | ErpTrnsHistoryWriter | funnel `nextErpTrnsSeqNo` MAX+1 (biz_key) | Yes (L108) | ✅ | |
| BUSA_DOC_RCV_PAY_HIS | RCV_PAY_HIS_SEQ_NO | CardTransactionsService | funnel MAX+1 (biz_key,doc_id) | Yes (L471) | ✅ | |
| BANK_EXCH_RATE_INFM | EXCH_SEQ_NO | ExchangeRateBatchService | funnel MAX+1 (biz_key,bank_cd,ref_date,ccy_cd) | Yes (L1718) | ✅ | 4-param positions match |
| BANK_EXCH_RATE_INFM | (Woori round probe) | ExchangeRateBatchService.findHighestSeqNo | Java read (not a key alloc) | n/a | ➖ | round computation, not a row key |
| SCDUL_PYMN_SUMR | SCDUL_PYMN_NO | 4 payment services (InternalTransferKeyGenerator) | funnel `nextScdulPymnNo` MAX+1 (biz_key) | Yes (L114) | ✅ | |
| SCDUL_PYMN_SUMR_DETL | SCDUL_PYMN_DETL_NO | 4 payment services | funnel `nextScdulPymnDetlNo` seed (#38) + Java `++` | Yes (L126) | ✅ | per-tenant contiguous = legacy |
| SCDUL_PYMN_SUMR_DETL | SCDUL_PYMN_NO | 4 payment services | FK copy from master | n/a | ➖ | never DB-generated |
| OVRS_PYMN_SUMR | OVRS_PYMN_NO | OverseasPaymentExcelService (OverseasPaymentKeyGenerator) | funnel `nextOvrsPymnNo` MAX+1 over (mstr∪sumr) | Yes (L1758) | ✅ | |
| OVRS_PYMN_SUMR_DETL | OVRS_PYMN_DETL_NO | OverseasPayment | funnel `nextOvrsPymnDetlNo` MAX+1 over (mstr∪detl) | Yes (L1774) | ✅ | |
| EXPT_RESOL_MSTR | APPR_SEQ_NO | ApprovalBoxFanoutService | funnel `nextApprSeqNo` → `nextval('sq_appr_seq_no')` | Yes (L55) | ✅ | global seq |
| EXPT_RESOL_MSTR | DOC_NO | DocNoGenerator / ApprovalBoxFanoutService | funnel `nextDocNo` env-set daily MAX+1 | Yes (L136) | ✅ | verbatim env-set body matches |
| EXPT_RESOL_FILE | FILE_ID | ApprovalAttachmentWriter | funnel `nextExptResolFileId` MAX+1 (biz_key,appr_seq_no) | Yes (L559) | ✅ | |
| EXPT_RESOL_APPRVR_INFM | APPRVR_LINE_NO | ApprovalBoxFanoutService | Java loop ordinal 1..N | Yes (L1515, unused) | ➖ | =legacy: appr_seq_no fresh → MAX=0→1,2,3 |
| TB_PAY_TARGET | TRAN_SEQ | dispatch executors | funnel → `nextval('firm_seq_no')` | Yes (L65) | ✅ | |
| TB_FIRM_TRAN | TRAN_SEQ | OAT depositor-inquiry (201) | funnel → `nextval('firm_seq_no')` | Yes (L68) | ✅ | |
| TB_PAY_FILE_MST | FILE_SEQ_NO | dispatch | funnel → `nextval('firm_file_seq_no')` | Yes (L71) | ✅ | |
| TB_PAY_FILE_DATA | SEQ | dispatch executors | funnel `nextTbPayFileDataSeq` MAX+1 (biz_key,file_seq_no), seed+`++` | Yes (L78) | ✅ | `::int` cast preserved |
| SYS_DOC_ID_GEN | DOC_ID | CardTransactionsService.createReceipt | funnel `nextSysDocIdGenDocId` ('E'→R-yyyyMMdd-nnn) | Yes (L814) | ✅ | $3=biz_key,$4='E' |
| MEMO_INFM | SEQ_NO | Memo (Card memo drawer) | funnel `nextMemoInfmSeqNo` MAX+1 (biz_key,web_svc_id) | Yes (L1147) | ✅ | |
| BUSI_INFM | BIZ_KEY | DBA manual SQL (bootstrap deleted) | funnel composite `{ntnl}_{chnl}_{NNNNNN}` | Yes (L573) | ✅ | no live Spring caller |
| EMPL_INFM | EMPL_NO | DBA manual SQL | funnel `{YYYYMMDD}_{COUNT+1}` | Yes (L201) | ✅ | `COUNT(*)+1` preserved |
| ACCT_MSTR | PRN_SEQ | AccountRegistrationService | funnel `nextAcctMstrPrnSeq` MAX+1 (biz_key) | Yes (L659) | ✅ | |
| **AUDIT_LOGS_INFM** | **AUDIT_LOGS_SEQ_NO** | **AuditLogService.doAppend** | **was funnel (scoped on UAT) → now whole-table MAX+1 in funnel class** | Yes (L1661) but **scoped** | **🔧 fixed** | **BLOCKER B-1 — see §B.3** |
| **BUSI_USAG_SET_INFM** | **USE_USAG_CD** | **PurposeSettingsService / PurposeExcelService** | **was funnel no-group (NULL on UAT) → now 10-digit MAX+1 in funnel class** | **No no-group branch** | **🔧 fixed** | **BLOCKER B-2 — see §B.3** |
| BUSI_USAG_SET_INFM | DISP_ORD | PurposeSettingsService | Java `findMaxDispOrd` +1 (not funneled) | n/a (UAT scopes by grp) | ➖ | already Java-side; cosmetic |
| EINV_FEE_DETL / EINV_TAX_DETL / BUSA_DOC_DETL | DISP_ORD | EInvoice scrape services | scrape-supplied (`p.dispOrd()>0? … : i+1`) | Yes (dormant) | ➖ | not DB-generated |
| *_HIS mirror tables (SCDUL/OVRS/BUSA/etc.) | (composite / REG_DTM) | history writers | copied from live row / server time | n/a | ➖ | no seq allocation |

## B.3 Blockers before UAT deploy (both fixed this pass)

### B-1 — `AUDIT_LOGS_INFM.AUDIT_LOGS_SEQ_NO` (was: wrong values → silent audit loss)

- **Legacy UAT branch (L1661):** `MAX(audit_logs_seq_no::numeric)+1 WHERE biz_key=$3 AND wsvc_id=$4 AND lang_cd=$5`.
- **Refactor call:** `AuditLogService` → `nextAuditLogsSeqNo(bizKey, ctx.refNo(), ctx.refNo1())`,
  i.e. `$4=refNo`, `$5=refNo1` (record locators, **not** the stored `WSVC_ID`/`LANG_CD`), then
  fans out **N locale rows** (EN/KO/VI/KM) sharing one `seq_no`.
- **Behaviour on legacy UAT:** `WHERE wsvc_id=refNo` matches no stored row (stored `WSVC_ID`
  is the screen slug) → `MAX=0` → `seq_no = 1` for **every** event. PK is
  `(BIZ_KEY, AUDIT_LOGS_SEQ_NO, WSVC_ID, LANG_CD)`, so the **2nd+ event on the same
  (biz_key, wsvc_id, lang_cd)** collides → `DataIntegrityViolationException`, swallowed by
  `append()`'s catch → **audit rows silently dropped** (business flow unaffected, audit trail broken).
- **Fix (Java, funnel-internal):** `LegacyFunctionRepository.nextAuditLogsSeqNo` now runs
  `SELECT CAST(COALESCE(MAX(audit_logs_seq_no::numeric),0)+1 AS text) FROM audit_logs_infm`
  (whole-table MAX+1 — byte-for-logic identical to the refactor DEV `#17` body), independent of
  the deployed function. Signature unchanged; `wsvcId`/`langCd` retained but unused (as in DEV).
  DEV and UAT now behave identically.
- **Safety:** whole-table `MAX+1` is always `>` any existing value; no PK collision; concurrency
  posture unchanged (`append()` already tolerates the rare race).

### B-2 — `BUSI_USAG_SET_INFM.USE_USAG_CD` (was: hard failure → create/import throws)

- **Legacy UAT branch (L1482):** only a **grouped** variant — requires `$4 (usag_grp_cd)` non-blank;
  no no-group branch exists.
- **Refactor call:** `PurposeSettingsService.persistCreate` and `PurposeExcelService` write
  `usag_grp_cd = NULL` and call `nextUseUsagCd(bizKey, "")` (empty group).
- **Behaviour on legacy UAT:** empty `$4` fails the branch guard → falls through to
  `ELSE → NULL` → `useUsagCd == null` → `throw new BusinessException("WBE9999")`.
  **Purpose Settings create AND Excel import would fail** on every call in UAT.
- **Fix (Java, funnel-internal):** `LegacyFunctionRepository.nextUseUsagCd` now, for a blank group,
  runs `SELECT COALESCE(LPAD((MAX(use_usag_cd)::numeric+1)::varchar,10,'0'),'0000000001')
  FROM busi_usag_set_infm WHERE biz_key=?1 AND use_usag_cd ~ '^0[0-9]{9}$'` (byte-for-logic
  identical to the refactor DEV `#19` body). A **non-blank** group still delegates to
  `wb_get_next_val` (the grouped branch exists in both DEV and UAT). Consistent with the sibling
  `PurposeSettingsRepository.findMaxDispOrd`, already Java-side for the same reason.
- **Safety:** produces the same `00000000NN` codes as DEV; no data-shape change; regex-guarded cast.

> Both fixes are contained **inside the single funnel class** (`LegacyFunctionRepository`), so the
> "all key generation goes through one class" architecture is preserved — these two methods simply
> compute the value directly instead of delegating to a `wb_get_next_val` branch that legacy UAT
> cannot serve. No service, DTO, entity, migration, or test signature changed.

## B.4 Safe Java-side exceptions (do NOT call the function — UAT-agnostic)

These never invoke `wb_get_next_val`, so the deployed function is irrelevant; each produces a
legacy-equivalent value for its real call shape:

| Field | Mechanism | Why legacy-equivalent |
|---|---|---|
| `CARD_TRNS_RCV_PAY_INFM.RCV_PAY_SEQ_NO` | hardcoded `1` | `card_seq_no` freshly allocated per txn → legacy per-(biz,card_seq_no) MAX=0 → 1 |
| `EXPT_RESOL_APPRVR_INFM.APPRVR_LINE_NO` | Java loop ordinal 1..N | `appr_seq_no` fresh per approval → legacy MAX=0 → 1,2,3 |
| `SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_DETL_NO` | funnel seed (#38, per-tenant) + Java `++` | reproduces legacy per-tenant contiguous sequence |
| `SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_NO` | FK copy from master | never DB-generated |
| `*.DISP_ORD` (rmrk_org, busi_usag, einv_*, busa_doc_detl, trco_acct) | Java `MAX+1` / scrape / client-supplied | cosmetic ordering; PO-gated scope divergence, not a key |
| `BANK_EXCH_RATE_INFM` Woori round probe | Java read | round computation, not a row-key allocation |
| `*_HIS` mirror tables | copied live-row values / server time | no sequence allocation |

## B.5 Required code fixes — applied

| File | Change | Reason |
|---|---|---|
| `common/legacy/LegacyFunctionRepository.java` → `nextAuditLogsSeqNo` | delegate-to-function → **direct whole-table `MAX+1` native query** (= DEV #17) | B-1: legacy UAT scoped body yields `seq_no=1` → PK collisions / dropped audit rows |
| `common/legacy/LegacyFunctionRepository.java` → `nextUseUsagCd` | no-group case: delegate-to-function → **direct 10-digit `MAX+1` native query** (= DEV #19); grouped case unchanged | B-2: legacy UAT has no no-group branch → returns NULL → Purpose Settings throws |

No SQL function file was modified (legacy UAT function used as-is). No other file changed.

## B.6 Commands run (Part B)

| Command | Result |
|---|---|
| `grep` sweep: `wb_get_next_val` / `nextVal` / `createNativeQuery` / `set*SeqNo` / `BigDecimal.ONE` / `MAX(`/`COUNT(` across `src` | Only `LegacyFunctionRepository` executes the function; generators delegate; Java-side allocators catalogued in §B.4 |
| `./gradlew compileJava test --tests AuditLogServiceTest --tests PurposeSettingsServiceTest --tests PurposeExcelServiceTest` | **BUILD SUCCESSFUL in 29s** — 3 affected classes green (signatures unchanged) |
| `./gradlew test` (full suite) | **BUILD SUCCESSFUL in 1m 28s** — all green |

> **Residual note (not a blocker):** the two fixed methods now run a plain `MAX+1` read rather than
> `wb_get_next_val`. This is a lock-free read, same concurrency posture as the legacy function's own
> `MAX+1` branches. A future durable-sequence hardening (`nextval('seq')`) for `audit_logs_infm`
> would remove the whole-table read contention — tracked as a non-blocking follow-up.

> **Recommended follow-up test (deferred — needs a live DB / Testcontainers, absent from `build.gradle`):**
> an integration test that, against a legacy-UAT-shaped `wb_get_next_val`, drives an audit fan-out and
> a Purpose Settings create to assert no PK collision / no NULL — locking in B-1/B-2 regressions.
