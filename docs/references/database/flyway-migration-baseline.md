# Flyway Migration Baseline — the 2026-06-09 rebaseline

> **Read this before citing any `V<n>__<name>.sql` file in documentation.**
> The migration set was fully rebaselined on 2026-06-09. Migration **numbers were reassigned**, so a
> version number quoted in a document written before that date almost certainly points at a
> **different migration today**. This page is the canonical explanation and the old → current map.

**Status:** active · **Last reviewed:** 2026-08-17

> ⚠️ **Maintainer note.** The "Old reference" column in §5 deliberately contains **pre-rebaseline
> filenames that no longer exist on disk**. Never run a find-and-replace over this file — a bulk rewrite
> will destroy the mapping it exists to record. Edit §5 by hand only.

---

## 1. What changed

Before 2026-06-09 the migration set was **feature-ordered**: each sprint/feature MR appended migrations
in chronological order. Numbers grew to ~V253 and encoded *when* a change landed.

After the rebaseline the set is **table-ordered**: one migration per legacy table, named after the
table, ordered alphabetically. Numbers now encode *what* the migration creates, not when it was written.

```
BEFORE (feature-ordered)                   AFTER (table-ordered)
V131__internal_transfer_schema.sql    -->  V86__scdul_pymn_sumr.sql
V155__wb_get_next_val_centralize.sql  -->  V140__wb_get_next_val.sql
V196__transfer_history_helper_index.sql -> (dropped; base V69__napas_remit_trns_hstr.sql)
```

**Consequence:** the same number means something different before and after. `V155` was the
`wb_get_next_val` centralization; today `V155__mrec_pic_store.sql` is an unrelated table. A reader
following a stale citation reads the wrong file and reaches a confident wrong conclusion — which is
worse than a broken link.

## 2. Current set — structure

| Range | Contents | Applied by |
|---|---|---|
| **V1 – V135** | One pure-DDL migration per legacy JEX table, named `V<n>__<table_name>.sql`, alphabetical by table. DDL copied verbatim from the live JEX DB (including triggers, `bpchar`, full column sets). | Flyway (disabled — see §3) |
| **V136 – V139** | Refactor-owned infrastructure tables: `shedlock`, `product_menu`, `menu_item`, `menu_item_translation`. **DDL only — these files contain no `INSERT`.** | Flyway (disabled) |
| **V140** | `wb_get_next_val` PL/pgSQL function (954 lines, the centralized 28-branch body). | Flyway (disabled) |
| **V143 – V155, V159** | Sequences (`sq_*`, `firm_seq_no`, …) plus `mrec_pic_store`. | Flyway (disabled) |
| **V156 – V158** | **Manual DBA scripts, not Flyway migrations.** The `V` prefix is for ordering only; each file's header says so explicitly. V156 = View Setting seed (28 popover pages / 289 rows). V157 = legacy `WEB_SVC_ID` → route-slug mapping (documentation only). V158 = `MENU_AUTH_ADMN_SYS` refactor route rows. | DBA, by hand |
| **V248 – V253** | Post-rebaseline additions (overseas-payment match columns, daily-financial-HS screen options, VAT declaration). | Flyway (disabled) / DBA |

## 3. Flyway is disabled

`spring.flyway.enabled=false` and `spring.jpa.hibernate.ddl-auto=validate` in every `wecms-api` profile.
The schema is treated as **external and pre-migrated** — the refactor runs against the live JEX DB in all
environments. These files are therefore **schema-of-record and DBA input**, not an auto-applied pipeline.

When DDL is genuinely needed, deliver a manual DB-execution script (the V156/V157/V158 pattern), never a
re-enabled Flyway migration. See the root `CLAUDE.md` §"Manual DDL execution policy".

## 4. Categories that no longer exist as migrations

The rebaseline kept schema and dropped everything else. When a pre-rebaseline doc cites one of these,
there is **no current migration to repoint it to** — say so explicitly rather than inventing a number.

| Old category | Where it lives now |
|---|---|
| **Demo / data seeds** (Sprint-1 seed, chart-of-accounts demo, exchange-rate demo, tenant seeds) | **Dropped.** No demo seed ships in the current set. |
| **View Setting seeds** (per-feature `*_view_setting_seed.sql`) | Consolidated into **`V156__sys_scr_option_wecms.sql`** (manual DBA script). Export-only screens are deliberately excluded — see memory `project_export_only_screens_no_seed`. Daily-Financial-HS has its own `V249__sys_scr_option_daily_financial_hs.sql`. |
| **Helper indexes** (`*_helper_index.sql`) | **Dropped.** Base table DDL only. |
| **Additive column / CHECK changes** | **Folded into** the base table migration. |
| **Refactor-only views** (`*_view.sql`, `v_purpose_in_use`, `V_FIRM_BANKING_HISTORY`) | **Replaced by Hibernate `@Subselect`** over the legacy base tables — no view object is created. See memory `project_v_firm_banking_history_subselect`. |
| **Navigation catalogue seed** | **Not in the current set.** V137–V139 create the tables but contain zero `INSERT`s (verified). A freshly-migrated DB has an empty `MENU_ITEM`; the seed must be applied separately. |
| **`wb_get_next_val` branch additions** (per-feature branch migrations) | All 28 branches consolidated into **`V140__wb_get_next_val.sql`**. |

## 5. Old → current map

Pre-rebaseline reference on the left; what to cite today on the right.
**Do not bulk-edit this table** — the left column intentionally holds filenames that no longer exist.

| Old reference (pre-2026-06-09) | What it did | Current reference |
|---|---|---|
| `V11__empl_infm_busi_admn_infm.sql` | EMPL_INFM + BUSI_ADMN_INFM | `V52__empl_infm.sql` + `V27__busi_admn_infm.sql` |
| `V16__sprint1_operation_status_schema.sql` | OPRT_STTS_INFO + EXT_TRAN_CALL_HIS | `V74__oprt_stts_info.sql` + `V61__ext_tran_call_his.sql` |
| `V17__sprint1_seed_data.sql` | demo seed | *(dropped)* |
| `V28__sprint1_db_parity_audit.sql` | Sprint 1 parity fixes | *(folded into per-table DDL)* |
| `V29__sprint1_real_dev_seed_round2.sql` | demo seed | *(dropped)* |
| `V43__view_setting_seed_banking_account_inquiry_fcy.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V44__bank_infm_detl_and_nickname_fns.sql` | BANK_INFM_DETL + nickname fns | `V12__bank_infm_detl.sql` *(the functions pre-exist in the live JEX DB)* |
| `V59__firm_banking_history_source_tables.sql` | TB_FIRM_TRAN + NXSIAS_MODULE_TRAN_LOG_HIS | `V104__tb_firm_tran.sql` + `V73__nxsias_module_tran_log_his.sql` |
| `V60__firm_banking_history_view_and_fns.sql` | view + fns | *(replaced by `@Subselect`)* |
| `V61__firm_banking_history_view_setting_seed.sql` | View Setting seed | *(excluded — export-only screen)* |
| `V62__firm_banking_history_demo_seed.sql` | demo seed | *(dropped)* |
| `V62__firm_banking_settings_source_table.sql` | TB_FIRM_CONF | `V100__tb_firm_conf.sql` |
| `V63__audit_logs_infm_prerequisite.sql` | AUDIT_LOGS_INFM | `V8__audit_logs_infm.sql` |
| `V64__firm_banking_settings_view.sql` | view | *(replaced by `@Subselect`)* |
| `V65__firm_banking_settings_demo_seed.sql` | demo seed | *(dropped)* |
| `V71__erp_trns_hstr_schema.sql` | ERP_TRNS_HSTR | `V54__erp_trns_hstr.sql` |
| `V72__erp_trns_hstr_demo_seed.sql` | demo seed | *(dropped)* |
| `V73__seed_haengsung_company_for_multi_tenant.sql` | tenant seed | *(dropped)* |
| `V75__erp_trns_hstr_numeric_to_bigint.sql` | type hotfix | *(folded into `V54__erp_trns_hstr.sql`)* |
| `V79__vas_account_view_setting_seed.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V80__busi_usag_set_infm_schema.sql` | BUSI_USAG_SET_INFM | `V35__busi_usag_set_infm.sql` |
| `V81__busi_usag_set_infm_purpose_in_use_view.sql` | view | *(replaced by `@Subselect`)* |
| `V82__busi_usag_set_infm_demo_seed.sql` | demo seed | *(dropped)* |
| `V94__chart_of_accounts_schema.sql` | CHART_ACCT_INFM | `V42__chart_acct_infm.sql` |
| `V95__wb_get_next_val_chart_acct_infm.sql` | seq branch | `V140__wb_get_next_val.sql` |
| `V96__chart_of_accounts_seed_restricted_roots.sql` | ACC-01/02/03 seed | *(dropped — seeded operationally)* |
| `V97__chart_of_accounts_seed_demo.sql` | demo seed | *(dropped)* |
| `V100__bank_cert_info.sql` | BANK_CERT_INFO | `V9__bank_cert_info.sql` |
| `V101__nxsias_module_tran_log_his.sql` | NXSIAS_MODULE_TRAN_LOG_HIS | `V73__nxsias_module_tran_log_his.sql` |
| `V102__firm_banking_history_view.sql` | view | *(replaced by `@Subselect`)* |
| `V109__approval_box_schema.sql` | EXPT_RESOL_* family | `V55__expt_resol_appr_line.sql` … `V60__expt_resol_pay_evid_file.sql` |
| `V124__cash_flow_report_view_setting_seed.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V127__bank_exch_rate_infm_table.sql` | BANK_EXCH_RATE_INFM | `V10__bank_exch_rate_infm.sql` |
| `V130__bank_exch_rate_infm_seed.sql` | demo seed | *(dropped)* |
| `V131__internal_transfer_schema.sql` | SCDUL_PYMN_SUMR (+DETL) | `V86__scdul_pymn_sumr.sql` + `V87__scdul_pymn_sumr_detl.sql` |
| `V143__expense_payment_view_setting_seed.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V144__payroll_payment_view_setting_seed.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V146__scdul_pymn_sumr_chk01_allow_payroll.sql` | CHECK relaxation | *(folded into `V86__scdul_pymn_sumr.sql`)* |
| `V148__dispatch_tables_schema.sql` | TB_PAY_TARGET + dispatch tables | `V109__tb_pay_target.sql` (+ `V101`–`V110` `tb_firm_*` / `tb_pay_*`) |
| `V149__blocked_chars_rule_schema_and_seed.sql` | BLOCKED_CHARS_RULE | `V14__blocked_chars_rule.sql` |
| `V155__wb_get_next_val_centralize.sql` | the centralized function | **`V140__wb_get_next_val.sql`** |
| `V155__transaction_history_view_setting_seed.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V172__smart_organizer_rmrk_org_infm_schema.sql` | RMRK_ORG_INFM | `V85__rmrk_org_infm.sql` |
| `V173__wb_get_next_val_rmrk_org_infm.sql` | seq branch | `V140__wb_get_next_val.sql` |
| `V174__smart_organizer_view_setting_seed.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V181__transfer_history_helper_index.sql` | helper index | *(dropped — base `V69__napas_remit_trns_hstr.sql`)* |
| `V184__card_trns_hstr_schema.sql` | CARD_TRNS_HSTR | `V39__card_trns_hstr.sql` |
| `V185__ovrs_pymn_mstr_chart_acct_columns.sql` | additive columns | *(folded into `V78__ovrs_pymn_mstr.sql`)* |
| `V189__payment_dispatch_tables_schema.sql` | dispatch tables | `V101`–`V110` (`tb_firm_*` / `tb_pay_*`) |
| `V195__napas_remit_trns_hstr_oat_idempotency.sql` | OAT idempotency key | *(dropped — feature removed, see memory `project_oat_idempotency_dropped`)* |
| `V196__transfer_history_helper_index.sql` | helper index | *(dropped — base `V69__napas_remit_trns_hstr.sql`)* |
| `V199__own_account_transfer_view_setting_seed.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V232__payroll_payment_scheduled_view_setting_legacy.sql` | View Setting seed | `V156__sys_scr_option_wecms.sql` |
| `V242__navigation_menu_catalogue.sql` | nav tables | `V137__product_menu.sql` + `V138__menu_item.sql` + `V139__menu_item_translation.sql` |
| `V243__navigation_menu_seed.sql` | nav seed | *(not in the current set — see §4)* |
| `V247__contacts_export_view_setting_seed.sql` | export View Setting seed | *(excluded — export-only screen)* |

## 6. Rules for citing a migration

1. **Verify before citing.** `ls src/main/resources/db/migration/ | grep <table>` — do not trust a number
   quoted in an older document.
2. **Cite the filename, not the bare number.** `V140__wb_get_next_val.sql` survives a future renumber
   review; "V140" alone does not.
3. **If the thing you are citing was dropped** (a seed, a helper index, a view), say *"no current
   migration — <reason>"* rather than pointing at whatever now occupies that number.
4. **In a dated audit**, keep the original number as written and add the current pointer beside it —
   the historical record stays accurate, the reader still gets to the right file. Label it
   `historical (pre-2026-06-09 numbering)`.

## Related

- [legacy-table-inventory.md](legacy-table-inventory.md) — the authoritative 141-table baseline
- [table-mapping/db-mapping.md](table-mapping/db-mapping.md) — JEX → refactor table mapping
- [manual-tenant-setup.md](manual-tenant-setup.md) — DBA tenant provisioning flow
- [../../audits/wb-get-next-val-centralization-2026-06-09.md](../../audits/historical/wb-get-next-val-centralization-2026-06-09.md) — the centralization record
- Root `CLAUDE.md` §"Refactor DB & Legacy-Compatibility Rules" · §"Manual DDL execution policy"
