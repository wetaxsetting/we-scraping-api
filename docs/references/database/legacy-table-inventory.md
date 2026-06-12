# Legacy-Copied Table Inventory — Refactor DB

**Recorded:** 2026-06-11 · **Count:** 141 tables · **Source:** table list supplied by the team (recorded as-is, not re-verified against the JEX DEV DB this pass).

**This document is the authoritative table baseline for the WeCMS refactor project** — the single source of truth for which tables exist in the refactor database. It **supersedes** the older "PROD Active Table List" ([wecms-api/CLAUDE.md](../../../../wecms-api/CLAUDE.md) §"PROD DB Table Scope for Refactoring") as the canonical baseline; that section is retained only for its derivation method, exclude rules, and per-table data-migration rule. **When the two disagree, this inventory wins.** Governing rules: root [CLAUDE.md](../../../../CLAUDE.md) §"Refactor DB & Legacy-Compatibility Rules".

Flat inventory of every table in the refactor PostgreSQL DB (`wecms-api`). The DDL for these tables is **copied from the legacy JEX schema** and must stay legacy-compatible for forward data migration.

This is a **snapshot of what physically exists**, distinct from:
- [table-ownership.md](table-ownership.md) — which module *owns* each table (write-path responsibility).
- [table-mapping/db-mapping.md](table-mapping/db-mapping.md) — entity ↔ table mapping for tables wired to a JPA entity so far.
- [database-coverage-matrix.md](database-coverage-matrix.md) — per-table refactor coverage status.

> **Why this matters:**
> - **Check-first.** Any new entity, repository, service, REST API, WEB feature, migration analysis, or schema review must verify its table(s) against this inventory before work begins. A table absent from the inventory gets no entity / repository / migration until the baseline is changed with PO approval.
> - **Do not** rename, remove, redesign, normalize, re-type, drop, or replace any table here without prior legacy-behavior analysis and explicit PO approval.
> - **Unused ≠ removable.** A table in the inventory that the refactor app does not yet use is **still part of the approved baseline** — preserve it unless the PO explicitly deprecates it.
> - **Migration safety is the primary objective** — keep the schema close to legacy so JEX data migrates cleanly.
> - Schema changes ship as **manual DBA-run SQL** (not auto-applied by Flyway) with a documented run order. See the root CLAUDE.md north-star + rules 2–6.

## Inventory (141)

```
acct_auth_info
acct_mstr
acct_trns_hstr
acct_trns_rcv_pay_infm
api_tran_log_his
appr_line_infm
appr_line_infm_detl
audit_logs_infm
bank_cert_info
bank_exch_rate_infm
bank_infm
bank_infm_detl
base_infm
blocked_chars_rule
bltb_comnt_infm
bltb_noti_cont_infm
bltb_noti_infm
bltb_post_infm
bltb_post_infm_mdph
bltb_user_new_post_infm
busa_doc_detl
busa_doc_detl_his
busa_doc_mstr
busa_doc_mstr_his
busa_doc_rcv_pay_his
busi_admn_actv_his
busi_admn_infm
busi_admn_login_his
busi_admn_mdtr
busi_env_set_infm
busi_env_set_mdtr
busi_infm
busi_match_log
busi_mdph
busi_usag_set_infm
busi_usag_set_infm_lang_detl
busi_vas_acct_infm
card_mstr
card_trns_hstr
card_trns_rcv_pay_infm
ccy_infm
chart_acct_infm
chnl_infm
citad_bank_cd_lookup
daily_cash_rept_daily_sumr_detl
daily_fincl_rept_daily_acct_trns_hstr_sumr
daily_fincl_rept_daily_sumr_detl
einv_fee_detl
einv_fee_detl_his
einv_tax_detl
einv_tax_detl_his
empl_infm
empl_mdph
erp_trns_hstr
expt_resol_appr_line
expt_resol_apprvr_infm
expt_resol_detl
expt_resol_file
expt_resol_mstr
expt_resol_pay_evid_file
ext_tran_call_his
gdt_cert_info
memo_infm
memo_infm_file
menu_auth_admn_sys
menu_item
menu_item_translation
mrec_pic_store
mrec_pic_store_file
msg_snd_his
napas_bank_info
napas_bank_remit_info
napas_remit_trns_hstr
natn_infm
non_coll_cond_daily_sumr
non_pay_cond_daily_sumr
nxsias_module_tran_log_his
oprt_stts_info
ovrs_pymn_mstr
ovrs_pymn_mstr_his
ovrs_pymn_sumr
ovrs_pymn_sumr_detl
ovrs_pymn_sumr_detl_his
ovrs_pymn_sumr_his
pgro_bsac_lnkn_his
pgro_bsac_lnkn_infm
product_menu
push_device_mstr
push_device_mstr_his
rmrk_org_infm
scdul_pymn_sumr
scdul_pymn_sumr_detl
scdul_pymn_sumr_detl_his
scdul_pymn_sumr_his
shedlock
sys_doc_id_gen
sys_dshbrd_infm
sys_env_set_infm
sys_scr_option_wecms
sys_usag_set_infm
tb_batch_schedule_dtl
tb_batch_schedule_info
tb_firm_bank_code
tb_firm_bank_code_cms
tb_firm_bat_api_tran
tb_firm_conf
tb_firm_file_data
tb_firm_file_mst
tb_firm_file_rslt
tb_firm_tran
tb_pay_file_data
tb_pay_file_data_his
tb_pay_file_mst
tb_pay_file_mst_his
tb_pay_target
tb_pay_target_his
tb_van_api_info
trco_acct_info
trco_actv_hstr
trco_addr_infm
trco_busi_infm_sumr
trco_grp_bsc_cstm
trco_grp_bsc_sys
trco_infm
trco_info_file
trco_mdph
trco_ovrs_infm
usag_infm
usag_infm_lang_detl
user_bltb_conn_infm
user_busi_conn_infm
user_busi_conn_infm_his
user_dshbrd_infm
user_infm
user_login_his
user_mdph
user_menu_auth_infm
user_pw_chg_his
user_scr_option_wecms
user_trco_use_his
user_usag_use_his
```

## Notes — not all 141 are legacy-copied

Most rows above are legacy JEX tables copied column-for-column. A few are **refactor-owned infrastructure or library tables** (created fresh by the refactor, not copied from JEX) — the "do not redesign copied legacy tables" rule does not apply to these the same way, because they have no legacy counterpart:

| Table | Nature | Reference |
|---|---|---|
| `menu_item` | Refactor-owned navigation catalogue (V242) — replaces lost JexAdmin menu trees; not a legacy JEX table | [wecms-api/CLAUDE.md](../../../../wecms-api/CLAUDE.md) §Migrations V242 |
| `menu_item_translation` | Refactor-owned navigation label table (V242) | V242 |
| `product_menu` | Refactor-owned per-product membership table (V242) | V242 |
| `shedlock` | ShedLock library lock table (scheduler infra) — not legacy, not in the PROD Active List | — |

These tables are **legacy but currently out of the PROD Active Table List** (refactor scope excludes them per the active-list rule — confirm before wiring an entity/migration):

| Table | Note |
|---|---|
| `memo_infm` / `memo_infm_file` | Legacy memo tables; `MEMO_INFM` explicitly **not** in the PROD Active List (key-gen exception #2 in CLAUDE.md) |
| `mrec_pic_store` / `mrec_pic_store_file` | Legacy; deferred-FK targets in V24, not in the PROD Active List |
| `sys_doc_id_gen` | Not in the PROD Active List — verify origin before treating as in-scope |

> These distinctions are observations cross-checked against the existing CLAUDE.md scope lists, **not** a fresh JEX DEV DB query. When scope decisions depend on them, run the parity check first (read-only `postgres_dev_server_jexframe` MCP) and confirm with the PO.

## Reference

- Governing rules: root [CLAUDE.md](../../../../CLAUDE.md) §"Refactor DB & Legacy-Compatibility Rules" (north-star + rules 1–6).
- Refactor scope: [wecms-api/CLAUDE.md](../../../../wecms-api/CLAUDE.md) §"PROD DB Table Scope for Refactoring".
- Schema parity rule: [wecms-api/CLAUDE.md](../../../../wecms-api/CLAUDE.md) §"Legacy Schema Parity Rule".
- Ownership map: [table-ownership.md](table-ownership.md) · Entity mapping: [table-mapping/db-mapping.md](table-mapping/db-mapping.md) · Coverage: [database-coverage-matrix.md](database-coverage-matrix.md).
