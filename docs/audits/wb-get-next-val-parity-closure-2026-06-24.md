# `wb_get_next_val` — Legacy Key-Generation Parity Closure

**System:** Refactor (wecms-api)
**Date:** 2026-06-24
**Type:** Parity closure record (documentation only — no code/DB/schema/migration/data change).
**Predecessor:** [wb-get-next-val-centralization-2026-06-09.md](wb-get-next-val-centralization-2026-06-09.md)
**Method:** read-only audit cross-referencing every refactor key generator against the **live legacy** `wb_get_next_val` source (`wecmsvndev`, 68,385 chars) inspected via the JEX DEV MCP, plus the `LegacyFunctionRepository` funnel surface.

---

## 1. No `wb_get_next_val` DB-function enhancement is required

**Closed.** Every column for which legacy JEX generates a value via `wb_get_next_val` is matched by the refactor — either by calling the same funnel or by producing an identical value. The live legacy function already contains correct, present branches for every relevant key (verified branch existence for `appr_line_infm`, `chart_acct_infm`, `audit_logs_infm`, `scdul_pymn_sumr_detl`, `ovrs_pymn_sumr_detl`, `trco_acct_info`/`trco_acct_key`, `memo_infm`). **No branch is missing, wrong, or in need of enhancement.** The database function must not be modified.

## 2. All in-scope business keys match legacy generation

**Confirmed.** All business primary keys, sequence numbers, document numbers, transaction numbers, approval numbers, payment numbers, account keys, file IDs, and the tenant business key generate the same way as legacy:

- **Via the funnel (identical mechanism):** `appr_line_no`, `appr_seq_no`, `chart_acct_seq_no`, `doc_no` (env-set logic inside the DB function), `scdul_pymn_no`, `ovrs_pymn_no`, `card_seq_no`, `rmrk_seq_no`, `audit_logs_seq_no`, `trco_key`, `trco_info_file.file_id`, `expt_resol_file.file_id`, `use_usag_cd`, `biz_key`, `ext_tran_call_his.seq_no`, `napas_remit seq_no`, `bank_cert/gdt_cert/vas/erp/busi_match seq`, `acct_trns_hstr.acct_seq_no`, `acct_trns_rcv_pay seq`, `acct_mstr.prn_seq`, `bank_exch_rate_infm.exch_seq_no`, `tb_firm_tran.tran_seq`, `tb_pay_target.tran_seq`, `tb_pay_file_mst.file_seq_no`, `tb_pay_file_data seq`, `trco_actv seq`, `busa_doc_rcv_pay_his seq`, `user_pw_chg reg_seq_no`.
- **Value-equivalent (different location, identical value):** the `*_DETL_NO` fields and the two items in §3/§4 below.

There are **no `@GeneratedValue` / JPA IDENTITY / SEQUENCE / AUTO** generators anywhere in the entity layer (verified: 0 occurrences).

## 3. `MEMO_INFM.seq_no` — value-equivalent to the legacy function branch

**Recorded.** Legacy branch:

```sql
when lower($1)='memo_infm' and lower($2)='seq_no' ... then
    newSeqNo := (select coalesce(max(seq_no::numeric),0)+1 from memo_infm
                 where biz_key = $3 and web_svc_id = $4);
```

Refactor `MemoInfmRepository.findMaxSeqNoByBizKeyAndWebSvcId`:

```java
@Query("SELECT COALESCE(MAX(m.seqNo), 0) FROM MemoInfm m " +
       "WHERE m.bizKey = :bizKey AND m.webSvcId = :webSvcId")   // service adds +1
```

Same scope `(biz_key, web_svc_id)`, same `MAX+1` logic → **identical value**. The only difference is location (computed in Java vs inside the DB function); the generated value matches legacy. `MEMO_INFM` is additionally outside the PROD active table list. **Not a parity gap.**

## 4. `TB_FIRM_FILE_MST.file_seq` — matches legacy IDO `MAX+1` behavior

**Recorded.** The live legacy `wb_get_next_val` has **no branch** for `tb_firm_file_mst` (verified). Legacy allocates this key with `MAX(FILE_SEQ)+1` in IDO `TB_FIRM_FILE_MST_R002`. The refactor `TbFirmFileMstRepository` uses the same `MAX+1`. The refactor therefore matches the **legacy mechanism and value** — funneling it would *diverge* from legacy, not converge. **Not a parity gap; no funnel routing warranted.**

## 5. Former value gap — `TRCO_ACCT_INFO.trco_acct_key` — **CLOSED (2026-06-24, OD-1 resolved)**

**Status: CLOSED.** OD-1 was implemented as a backend caller-routing change (no DB function / DDL / schema / migration / config / data change). `TRCO_ACCT_INFO.trco_acct_key` now routes through:

```
ContactsService → LegacyFunctionRepository.nextTrcoAcctKey(bizKey, trcoKey) → wb_get_next_val('trco_acct_info','trco_acct_key', bizKey, trcoKey)
```

- **Create:** every account is new → the key is **generated via the funnel**; the frontend placeholder is ignored; `saveAndFlush` between inserts so the next per-`(biz_key, trco_key)` `MAX+1` sees the prior row (mirrors `EInvoiceTrcoAutoRegistrar`).
- **Update:** an existing account (its key already present for `(biz_key, trco_key)`) **preserves** its key; a new account carrying a frontend placeholder **generates** via the funnel. New-vs-existing is decided by existence in `TRCO_ACCT_INFO`, not a blank check.
- **Delete:** unchanged.
- **Frontend placeholder keys are accepted but never persisted for new contact accounts** — the backend tolerates the `nextClientKey` placeholder (satisfies `@NotBlank`) and replaces it with the funnel value. No frontend change was required.
- **Verified by tests:** `ContactsServiceTest` (6 OD-1 cases — create generates not-placeholder, multiple distinct/sequential keys, update preserves existing, update generates for new placeholder, delete unchanged, funnel called only for new rows), `ContactsControllerTest` (unchanged, green), `compileJava` clean.

### Historical record (pre-closure)

- **Column:** `TRCO_ACCT_INFO.trco_acct_key`.
- **Refactor — contacts-create path (before 2026-06-24):** accepted a **client-supplied** value (`ContactsService` create mapped `e.setTrcoAcctKey(a.trcoAcctKey())`). The e-invoice path to the same table already funnelled.
- **Legacy:** the function branch **already exists** and is correct:
  ```sql
  when lower($1)='trco_acct_info' and lower($2)='trco_acct_key' ... then
      newSeqNo := (select coalesce(max(trco_acct_key::numeric),0)+1 from trco_acct_info
                   where biz_key = $3 and trco_key = $4);
  ```
  i.e. per-`(biz_key, trco_key)` `MAX+1`.
- **Why it is a true gap:** a refactor contacts-created account can carry a `trco_acct_key` that does not follow the legacy per-`(biz_key, trco_key)` `MAX+1` sequence → possible value divergence / collision against legacy-created rows for the same `trco_key`.
- **This cannot be fixed by modifying `wb_get_next_val`** — the branch is already present and correct; the refactor contacts-create caller simply does not invoke it. Enhancing the function would change nothing on this path.
- **Closure (DONE 2026-06-24):** the contacts-create (and update-new) path now routes through `LegacyFunctionRepository.nextTrcoAcctKey` — implemented as backend caller-routing only, as described in the CLOSED status above. No `wb_get_next_val` change was needed.

---

## 6. Final exhaustive post-OD-1 audit (2026-06-24)

A full read-only sweep of the backend source was run **after** OD-1 closed, verifying from actual code (not prior-audit assumptions): `@GeneratedValue`/`GenerationType`, `nextval(`, `AtomicLong`/`AtomicInteger`, `MAX(`/`COALESCE(MAX`, `+1`/`++` cursors, `UUID.randomUUID`, custom `next*`, repository next-value methods, service PK/sequence assignment, and DTO/frontend-supplied persisted keys — with special focus on contact/account, file, payment-detail, memo, audit/log, transaction-history, approval, chart-of-accounts, purpose/settings, and bank/cert/exchange-rate tables and every `seq_no`/`*_key`/`*_no`/`file_id`/`file_seq`/`detl_no`/`reg_seq` column.

**Result:**

- **No remaining parity gaps.**
- **Class-D (potential gap) findings: 0.**
- **Missing `wb_get_next_val` branches: 0.**
- **No DB-function enhancement required.**
- **No Java business-key generation remains outside the funnel.**
- `@GeneratedValue`/`GenerationType`: 0. `nextval(` outside the funnel: 0 (one Javadoc comment only). `AtomicLong`/`AtomicInteger` for keys: 0.

**Final standard — all business keys now follow:**

```
Service → LegacyFunctionRepository.nextXXX(...) → wb_get_next_val(...) → next value from old DB data
```

**Accepted non-gaps (confirmed, not parity gaps):**

- **Detail / file in-memory cursors are funnel-seeded and value-equivalent** — `scdul_pymn_detl_no` (Vendor/Internal/Expense/Payroll), `ovrs_pymn_detl_no` (OverseasPaymentExcelService), and `expt_resol_file.file_id` (InternalTransferService) seed once from the funnel (`nextScdulPymnDetlNo` / `nextOvrsPymnDetlNo` / `nextExptResolFileId`) then increment in memory across the batch, yielding identical sequential values in one transaction.
- **`MEMO_INFM.seq_no`** — Java `COALESCE(MAX(seq_no),0)+1` per `(biz_key, web_svc_id)`; identical scope and value to the legacy `wb_get_next_val('memo_infm','seq_no',…)` branch.
- **`TB_FIRM_FILE_MST.file_seq`** — Java `MAX(FILE_SEQ)+1` matching legacy IDO `TB_FIRM_FILE_MST_R002`; legacy has **no** funnel branch for this table, so the refactor matches legacy's mechanism.
- **Not business-key gaps:** `disp_ord` / presentation-ordering fields; `USER_LOGIN_HIS.reg_uid` and `NXSIAS_MODULE_TRAN_LOG_HIS.uuid` (log/history surrogates, no legacy funnel branch); vendor protocol transaction numbers (`Tno`); trace IDs; JWT IDs; session ids; temporary save-file names; and DTO-supplied **FK reference / data** fields (e.g. `rcv_pay_trco_key`, `chart_acct_seq_no`, `vas_acct_seq_no`, `acct_no`, `card_no`, `wdrl_act_no`, `finc_mda_no`, `ref_no`).

---

## Final verdict

**100% Legacy Key-Generation Parity Achieved.**
Zero `wb_get_next_val` enhancements required. All in-scope business keys match legacy generation (funnel or value-equivalent). The last remaining value gap — `TRCO_ACCT_INFO.trco_acct_key` on the contacts-create path (OD-1) — was **closed on 2026-06-24** by routing `ContactsService` through `LegacyFunctionRepository.nextTrcoAcctKey → wb_get_next_val` (backend caller-routing only). No DB function, DDL, schema, migration, config, or data changed. Verified by `ContactsServiceTest`, `ContactsControllerTest`, and `compileJava`.

> Excluded accepted exceptions (not parity gaps): `disp_ord` / presentation-ordering fields, `USER_LOGIN_HIS.reg_uid`, `NXSIAS_MODULE_TRAN_LOG_HIS.uuid`, vendor transaction UUIDs (`Tno`), trace IDs, JWT IDs, temporary file names.
