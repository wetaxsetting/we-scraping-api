# Database Mapping Documentation Drift — Code vs Docs


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../references/database/flyway-migration-baseline.md) §5.

> Phase-3 truth audit (2026-06-23). **Implementation code is the sole source of truth.** Scope: every module's
> `database-mapping.md` vs entities (`@Table`/`@Column`/`@IdClass`), repositories, native SQL, DB functions, and the
> on-disk Flyway migration files. Flags: columns documented-but-unused · used-but-undocumented · outdated relationships ·
> outdated function descriptions · wrong migration version citations.
> Tags: **[CONFIRMED]** / **[SUSPECTED]**. Companion: [authorization-doc-drift.md](authorization-doc-drift.md) · [api-contract-drift.md](api-contract-drift.md) · [documentation-truth-audit.md](documentation-truth-audit.md).

## Headline

**Table / column / PK *structures* are accurate across the board** — spot-checks of `ACCT_MSTR`, `TRCO_INFM`,
`APPR_LINE_INFM(_DETL)`, `CHART_ACCT_INFM`, `BUSI_USAG_SET_INFM`, `OPRT_STTS_INFO`, `BUSA_DOC_MSTR`, `AUDIT_LOGS_INFM`,
`ERP_TRNS_HSTR`, `BUSI_VAS_ACCT_INFM`, the firm-banking entities, and GDT/Bank cert entities all matched their
`database-mapping.md` 1:1. The drift is concentrated in **(1) one stale function-call description (banking daily-financial),
(2) systematically wrong migration-version numbers (common module), (3) one PK column-ordering mismatch, and
(4) stale repository/entity Javadoc** — not in the documented column sets.

## A. Outdated DB-function descriptions — CONFIRMED

| # | Location | Documented | Actual code |
|---|---|---|---|
| DB-1 | `features/banking/reports/daily-financial/database-mapping.md:135,150` | `rept_0015_001_r001_rec1` is *"Not called; logic re-expressed in Java"* | `ReportService.dailyFinancial` calls it directly via `DailyFinancialCmcFunctionRepository` (`service/ReportService.java:147-225`) — 2026-06-14 pivot | **[CONFIRMED]** |
| DB-2 | `features/banking/reports/daily-financial/migration-guide.md:77-122` | Strategy-B live-compute is the read path; §121-122 instructs *"Do not add `rept_0015_001_r001_rec1` functions"* | code reads exactly that function | **[CONFIRMED]** — the migration guide actively forbids what the code now does |

(Cash-flow `database-mapping.md:17` correctly states `get_cashflow_report` is "Called directly (2026-06-13)" — accurate. Daily-cash function-repo mapping accurate.)

## B. Wrong migration-version citations — CONFIRMED (common module, systemic)

The leaf docs inherited migration version numbers from a stale/fabricated `wecms-api/CLAUDE.md`; the **on-disk `.sql`
files do not match**. Structures are right; the version IDs are wrong.

| # | Documented version | On-disk reality | Files affected |
|---|---|---|---|
| DB-3 | Navigation `MENU_ITEM` / `PRODUCT_MENU` / `MENU_ITEM_TRANSLATION` cited as **V242/V243** | actual `V137__product_menu.sql`, `V138__menu_item.sql`, `V139__menu_item_translation.sql` (no V242/V243 exist; highest is V248) | `features/common/navigation/{database-mapping,migration-guide,README,source-code-index}.md` | **[CONFIRMED]** |
| DB-4 | `audit_logs_infm.audit_logs_seq_no` key-gen cited as **V155 "28-branch centralization"** | live `wb_get_next_val` is `V140__wb_get_next_val.sql`; `V155__mrec_pic_store.sql` on disk is unrelated. `AuditLogService.java:69-71` itself correctly cites "V140 #17" | `features/common/audit-log/{database-mapping,migration-guide,README,refactor-gap-analysis,source-code-index}.md`, `audit-log.md`, `features/common/file-management/{database-mapping,migration-guide}.md` | **[CONFIRMED]** |

> Root cause is `wecms-api/CLAUDE.md`, which describes a non-existent "V155 28-branch centralization" and "V242/V243 navigation". The leaf docs copied it faithfully. Correcting the root file is the highest-leverage fix.

## C. Outdated relationship / PK mapping — CONFIRMED

| # | Location | Issue |
|---|---|---|
| DB-5 | `features/settings/bank-account/database-mapping.md:23-26,34` | documents `AcctAuthInfo` PK as `(BIZ_KEY, BANK_CD, ACCT_NO, USER_ID, AUTH_TYPE)`; code `AcctAuthInfoId.java:22-26` declares `(BIZ_KEY, USER_ID, BANK_CD, ACCT_NO, AUTH_TYPE)`. Same key set, **column ordering differs** — doc is the outlier. **[CONFIRMED]** |

## D. Stale entity/repository Javadoc on @Subselect "views" — CONFIRMED (code-comment defects)

The refactor replaced three legacy JEX views with Hibernate `@Subselect`/`@Immutable` entities (no real DB object).
`database-mapping.md` files correctly state "no real DB object"; the **repository Javadocs still claim real views**:

| # | Location | Stale claim |
|---|---|---|
| DB-6 | `PurposeInUseRepository.java:12` | "`v_purpose_in_use` view (V81)" — actually a `@Subselect` |
| DB-7 | `FirmBankingSettingViewRepository.java:9-10` | "`V_FIRM_BANKING_SETTING` / V59 / V64" — actually a `@Subselect` |

## E. Stale entity Javadoc on column semantics / key-gen — CONFIRMED

| # | Location | Stale claim | Reality |
|---|---|---|---|
| DB-8 | `BusiVasAcctInfm.java:54-57` + `VasAccountService.java:55-58` | "Java-side MAX+1, replaces `wb_get_next_val`" | `VasAcctSeqNoGenerator.java:30` funnels through `wb_get_next_val('busi_vas_acct_infm','vas_acct_seq_no',…)` (`LegacyFunctionRepository.java:274-276`) | **[CONFIRMED]** doc correct, Javadoc violates funnel rule |
| DB-9 | `GdtCertInfo.java:26` / `BankCertInfo` Javadoc | "AES-GCM" | legacy pgcrypto **AES-256-CBC** (`BankCertCryptoService.java:20-27`); already tracked as GAP-3 in `login-credentials/refactor-gap-analysis.md:117` | **[CONFIRMED]** |
| DB-10 | `BusiUsagSetInfm.java:17,80` | "V80" | actual `V35` per on-disk numbering | **[CONFIRMED]** |

## F. Suspected / low-confidence

| # | Item | Why suspected |
|---|---|---|
| DB-S1 | `UserPwChgHis` `REG_DTM`/`MOD_DTM` type | `change-password/database-mapping.md` says `OffsetDateTime` via `LegacyDtmConverter`; sibling `USER_LOGIN_HIS.REG_DTM` was remapped to `String varchar(14)` (memory `project_user_login_his_reg_dtm_string`). Entity not opened — if it followed the sibling, doc is stale. **[SUSPECTED]** |
| DB-S2 | firm-banking-history `SYS_SCR_OPTION_WECMS` seed | feature docs say NOT seeded (export-only, `WBE2001`→allow-list fallback); `wecms-api/CLAUDE.md` V153 row says it seeds 7 columns. Code-agnostic; CLAUDE.md row likely stale. **[SUSPECTED]** |

## G. Columns documented-but-unused / used-but-undocumented

**No confirmed column-level drift found.** The entities checked map exactly to their documented column sets, and no
service was found reading/writing a column absent from `database-mapping.md`. (`PW_CHG_REQ_SEQ_NO` is documented as
"mapped-but-unused" in `change-password/database-mapping.md` — correctly disclosed, not a defect.)

## Verdict

- **Documented schema structures: accurate.** Zero confirmed column-set drift.
- **Confirmed drift: 1 function-call description (DB-1/2, banking daily-financial), 2 systemic migration-version errors (DB-3/4, common), 1 PK ordering (DB-5), 5 stale code Javadocs (DB-6..10).**
- Highest risk: **DB-1/DB-2** — the daily-financial migration guide tells a future porter to NOT add the very function the code depends on; **DB-3/DB-4** — wrong version numbers will send anyone to the wrong/again non-existent migration file.
