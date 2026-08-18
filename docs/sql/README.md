# sql/ — Manual DBA Scripts (repository-owned)

Hand-run SQL that belongs to `wecms-api` but is **not** applied by the application. Flyway is disabled
and `ddl-auto` is `validate` in every profile — the schema is external and DBA-applied (see
[`../references/database/flyway-migration-baseline.md`](../references/database/flyway-migration-baseline.md)).
These scripts are the delivery vehicle for the DDL/DML those docs describe.

**Status:** active · **Last reviewed:** 2026-08-17

## Layout

```
docs/sql/
├── README.md          ← this file (governance + navigation) — the ONLY file at the root
└── 2026/              ← every script, year-partitioned
    └── descriptive-name-YYYY-MM-DD.sql
```

**No SQL file belongs directly under `docs/sql/`.** Every script lives in its year folder and carries
its date in its filename. The last two root-level scripts were normalized into [`2026/`](2026/) on
2026-08-17 — see §"Root-level scripts normalized" below.

**Creating a SQL file here is NOT executing it.** Every script in this folder is manual unless a
document explicitly says otherwise. Execution is a separate operational action performed by a DBA or
an authorised operator per environment — see §"New scripts" below and [`../../CLAUDE.md`](../../CLAUDE.md) §14f.

## Why these files live in this repository (P2 decision, 2026-08-17)

An earlier cleanup note proposed moving the two original scripts out to the workspace-level
`Claude Code (AI)/sql/` folder. **That was reversed after checking the repository facts:**

| Fact | Consequence |
|---|---|
| Both files are **tracked in the `wecms-api` Git repository** (`git ls-files docs/sql/`) | Moving them out drops them from this repo's history |
| The workspace root `c:\WECMS PROJECT` is **not a Git repository**, and `Claude Code (AI)/` is not tracked by any repo | The proposed destination has **no version control at all** |
| The web-svc-id script is linked twice from [`../audits/governance/web-svc-id-route-migration-audit.md`](../audits/governance/web-svc-id-route-migration-audit.md) as the audit's deliverable | Moving it out of the repo breaks a documented audit → deliverable pairing |

> **This decision is about the repository, not the folder depth.** Both scripts were later moved
> *within* this repository into [`2026/`](2026/) (2026-08-17, `git mv` — history preserved, audit links
> repointed). They remain version-controlled and co-located with their specifying docs, so the P2
> decision holds unchanged.

Version-controlled, co-located with the audit/feature docs that specify them, beats an untracked
AI-output scratch directory. **They stay in this repo.** `Claude Code (AI)/sql/` remains the correct home for
*ad-hoc, session-scoped* scripts (seeds, one-off cleanups, index proposals) — see workspace
[`CLAUDE.md`](../../../CLAUDE.md) §"Manual DDL execution policy". The distinction:

- **`docs/sql/` (here)** — a script that is the **durable deliverable of a repository document**, and is
  expected to survive as part of the `wecms-api` record.
- **`Claude Code (AI)/sql/`** — a script produced for one investigation / one seeding run.

## New scripts — the docs-first DB-impact flow (added 2026-08-17)

This folder is now also the home for the manual SQL artifacts that the **docs-first database-impact
rule** requires. Full rule: [`../../CLAUDE.md`](../../CLAUDE.md) §14 "Database impact — mandatory
declaration and artifacts". In short:

| Change | Artifact |
|---|---|
| **New table** · **new DB function** | a `V<n>__<name>.sql` file in `src/main/resources/db/migration/` (schema-of-record; still DBA-applied by hand — Flyway is disabled) |
| **ALTER an existing table** · index · constraint · replace an existing function · **any data change** (`INSERT` / `UPDATE` / `DELETE`, seed, reference/config rows, backfill, corrective SQL, one-time migration) | **a manual script here** |

**Never** put an `ALTER TABLE`, an `UPDATE`, or corrective SQL into the migration folder because the
work happens to be part of a feature, and **never** edit an already-applied migration.

**Layout for new scripts — partitioned by year:**

```
docs/sql/<YYYY>/descriptive-name-YYYY-MM-DD.sql
```

Lowercase kebab-case, ISO date, named for the subject — `add-recipient-name-column-2026-08-17.sql`,
`backfill-vas-account-type-2026-08-17.sql`, `update-citad-bank-mapping-2026-08-17.sql`. **Forbidden:**
`P0.sql`, `fix.sql`, `update.sql`, `script.sql`, `final.sql`, `A1-01.sql`, or anything named after a
developer. Internal issue IDs belong in the header comments.

**Every new script carries the standard header and section order:**

```sql
-- WeCMS Manual Database Change
-- Date: YYYY-MM-DD
-- Related record: <relative path to the bug/issue/feature .md>
-- Type: DDL | DML | DDL+DML
-- Execution: MANUAL
-- Target environments: SIT | UAT | PROD | as applicable
-- Executor: DBA TEAM | DEV TEAM | TBD
-- Transaction requirement: <documented requirement>
-- Rollback: <documented approach, or "ROLLBACK REQUIRES DBA REVIEW">

-- 1. PRE-CHECK
-- 2. CHANGE
-- 3. POST-CHECK
-- 4. ROLLBACK / RECOVERY NOTES
```

- **Pre/post-checks must prove the change** — `information_schema.columns` / `pg_indexes` /
  `pg_constraint` around DDL, `SELECT COUNT(*)` on the affected predicate around DML. No checks that
  pass regardless. **Do not fabricate row counts.**
- **No destructive rollback SQL that could itself lose data.** Where rollback is unsafe, state
  **`ROLLBACK REQUIRES DBA REVIEW`**.
- **Destructive / data-risk changes** (`DROP`, `DELETE`, `TRUNCATE`, column narrowing, constraint
  tightening, large corrections, irreversible updates) carry
  **`DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED`** in the script *and* in the Markdown record.
- **Preparing a script is not executing it.** Execution is a separate operational action by the
  **DBA team**, an **authorized developer**, or the **deployment/operator team** depending on
  environment; the script names its expected executor, and the record tracks per-environment status
  (§14f–14g). **An AI assistant never executes shared-environment SQL.**
- The record explains **why**; the script states **what to run**. Cross-link both directions.

## Legacy archive migration — 2026-08-17 (COPY-only)

The workspace-level folder `C:\WECMS PROJECT\Claude Code (AI)\sql` accumulated hand-written SQL from
2026-03 onward while this repository had no year-partitioned home for it. On **2026-08-17** the durable
part of that archive was brought under repository governance into [`2026/`](2026/).

**What happened — and what did not:**

- **COPY only.** 50 files were copied. **Nothing was moved, renamed, modified, or deleted in the source
  folder**, which remains intact as the backup/reference archive until it is retired by hand.
- **No SQL was executed.** No database connection was opened. Copying a script into this folder says
  nothing about whether it has been run — several copied scripts carry an explicit *NOT EXECUTED*
  banner from their author.
- **No Flyway migration was created, renumbered, or modified.** Some copied scripts create a new table
  or DB function and therefore *would* normally belong in `src/main/resources/db/migration/`. Those
  carry a **`MIGRATION REVIEW REQUIRED`** line in their header: the placement decision is open, and no
  `V<number>` was invented for them.
- **Query-only SQL was not copied.** Investigation queries, audit extracts, and diagnostic worksheets
  stayed in the source folder — this folder holds operational scripts, and mixing read-only analysis
  in with executable DBA scripts is exactly the confusion the split avoids. There is no governed home
  for durable analysis SQL in this repository today; if one is wanted, it needs a decision first.

The Windows path above is **historical provenance only** — nothing at runtime, in the build, or in CI
depends on it. Each copied script records its origin in a `-- Source:` header line.

### Header added to copied scripts

Copied scripts carry a documentation header stating date, source, type, target environments, executor,
rollback position, and whether the original contains verification queries. Where the original supplied
no evidence for a field, the header says **TBD** or **VERIFICATION CHECK REQUIRED** rather than
inventing an answer. **No SQL statement, table name, column name, value, predicate, or transaction
boundary was altered** — the body below each header is byte-identical to the source file.

Two exceptions to the header placement, both deliberate:
`insert-erp-fcy-exchange-rates-2026-03-19.sql` and `...-2026-03-20.sql` open directly with an Oracle
PL/SQL `BEGIN` block, so their metadata block is **appended at the end** instead of prepended.

### Flags to look for in a copied script's header

| Flag | Meaning |
|---|---|
| `DESTRUCTIVE / DATA-RISK CHANGE — DBA REVIEW REQUIRED` | Contains `DROP` / `DELETE` / `TRUNCATE`, a column drop, or an irreversible update |
| `MIGRATION REVIEW REQUIRED` | Creates a new table/function with no counterpart in `db/migration/`; placement is an open decision |
| `SQL REVIEW REQUIRED` | Replaces a live DB function in place |
| `COLLISION NOTE` | An equivalent object already exists in `db/migration/`; the script is the by-hand twin, not a duplicate to apply blindly |
| `TEMPLATE` | Carries placeholders a DBA must fill in — do not run as-is |
| `VERIFICATION CHECK REQUIRED` | The original ships no executable pre/post-check query |

### Notable exclusions (still in the source archive, deliberately not copied)

| Source file | Why it was not copied |
|---|---|
| `audit_logs_infm_wsvc_id_widen_20260609.sql` | **SUPERSEDED** — [`2026/widen-screen-identifier-columns-and-rekey-card-memos-2026-07-02.sql`](2026/widen-screen-identifier-columns-and-rekey-card-memos-2026-07-02.sql) STEP 2 already widens `audit_logs_infm.wsvc_id` to `varchar(100)`, and it is the PO-gated in-repo deliverable |
| `oat_idempotency_one_shot_20260518.sql` · `napas_remit_trns_hstr_oat_idempotency_key_20260609.sql` | **SUPERSEDED** — [`V69__napas_remit_trns_hstr.sql`](../../src/main/resources/db/migration/V69__napas_remit_trns_hstr.sql) states plainly *"Do NOT add OAT_IDEMPOTENCY_KEY — it does not exist in JEX"*. Copying them here would put a forbidden change in the executable folder |
| `oat_view_setting_one_shot_20260519.sql` | **ALREADY GOVERNED** — the `own-account-transfer` rows are in `V156__sys_scr_option_wecms.sql`; the one-shot also writes the removed `FIX_YN` column |
| `nxisas_acct_type_mapping_20260617.sql` | **DUPLICATE** — identical 18 seed rows and DDL; the annotated 2026-07-16 version was copied instead |
| `BUSA_DOC_MSTR_20260703.sql` · `sys_scr_option_wecms_fix_yn_20260609.sql` | Retracted / void notices containing **no executable SQL** — decision history, not deliverables |
| `PROD_UAT.sql` | Empty (0 bytes) |
| `_source_sys_scr_option_dump.csv` | Not SQL — a supporting data dump cited by `V156__sys_scr_option_wecms.sql` |
| 6 query-only files (`data_extract_seojin_*`, `einv_0001_001_export_hansungbolt_*`, `nxisas_einvoice_*`, `tb_firm_file_mst_stranded_backlog_worksheet_*`) | **Analysis / audit queries** — no governed home in this repository; see the COPY-only note above |

### Scope note — this folder now holds JEX-targeted scripts too

Most of the migrated archive targets the **refactor** database, but a number of scripts target the
**live JEX** system (legacy IMPs, UAT tenant/account setup, HANSUNGBOLT UAT prep, JEX report
functions) and one pair targets the **HAENGSUNG Oracle ERP** schema. They are here because the
archive was migrated as a whole; the workspace documentation rules otherwise keep JEX and refactor
material apart. **Read each script's `-- Target environments:` header before doing anything with it**,
and raise a split with the PO if this folder should be scoped to refactor-only material.

## Root-level scripts normalized — 2026-08-17

The folder's two original scripts were the last files sitting directly under `docs/sql/`. They were
moved into [`2026/`](2026/) with `git mv` (history preserved) and dated from evidence, **not** from the
date of the cleanup. SQL semantics were not touched; only a governance header was prepended, plus —
for the web-svc-id script — three stale cross-reference paths inside comments.

| Script | Purpose | Run by | Specified in |
|---|---|---|---|
| [`2026/create-vat-declaration-tables-2026-08-04.sql`](2026/create-vat-declaration-tables-2026-08-04.sql)<br>*(was `vat-declaration-ddl.sql`)* | Creates the VAT Declaration tables for the Add-on Service VAT feature. **Step 0 of a five-step chain** — see the warning below | **DBA, by hand** — never by the application | [`../features/addon-service/vat-declaration/README.md`](../features/addon-service/vat-declaration/README.md) · schema reference [`../references/database/addon/vat-declaration-db.md`](../references/database/addon/vat-declaration-db.md) |
| [`2026/widen-screen-identifier-columns-and-rekey-card-memos-2026-07-02.sql`](2026/widen-screen-identifier-columns-and-rekey-card-memos-2026-07-02.sql)<br>*(was `web-svc-id-varchar-100-migration.sql`)* | Widens the nine `WEB_SVC_ID` columns **and** `audit_logs_infm.wsvc_id` to `VARCHAR(100)`, then re-keys the Card Transactions memos to the modern route | **DBA, by hand** — execution is **PO-gated**, not yet run | [`../audits/governance/web-svc-id-route-migration-audit.md`](../audits/governance/web-svc-id-route-migration-audit.md) (STEP 1 onward) |

**Dates come from evidence.** `2026-07-02` is the explicit `Date:` line in the web-svc-id script's own
header (corroborated by its commit). `2026-08-04` is Git — the VAT script has no authoring `Date:`
line, and its last DDL change (`efcf98f`, UNIQUE CONSTRAINT → partial UNIQUE INDEX) is the only
file-level date available. The renamed filenames say what the SQL actually does: the web-svc-id script
carries a DML step as well as the widening, which its old name did not convey.

> ### ⚠ The VAT DDL is the base of a chain, and it is pre-rename
>
> `create-vat-declaration-tables-2026-08-04.sql` is the **only** place the VAT tables are created — no
> Flyway migration creates them. Four follow-on scripts amend it, and they live in the *migration*
> folder rather than here: **V250** (ct42a/ct42b columns) · **V251** (`ANNEX_ALLOC_TELE_YN`) ·
> **V252** (renames `tax_declarations` → `VAT_DECL`, `tax_declaration_items` → `VAT_DECL_ITEMS`, and
> **drops** `tax_declaration_annex_items`) · **V253** (`BUSA_DOC_MSTR` marker columns).
>
> So the table names in the base DDL are **pre-V252**, while the JPA entities map the **post-V252**
> names. Its `TAX_DECLARATION_ANNEX_ITEMS` section is superseded. And on a database where V252 has
> already run, re-running the base DDL would create three new empty tables beside `VAT_DECL`.
>
> **This split is a governance inconsistency, not a conclusion** — the base `CREATE TABLE` sits in
> `docs/sql/` while its four `ALTER` successors sit in `db/migration/`. Reconciling it was out of scope
> for the folder normalization and is reported as **MIGRATION REVIEW REQUIRED**. Full detail is in the
> script's own header.

## Rules

- **Never executed by the application.** No Flyway pickup, no startup hook. A DBA runs these manually.
- **Each script must state its own run order and idempotency behaviour** in its header, and must leave
  live JEX rows untouched where the refactor coexists with the legacy system.
- **Read the specifying document first** — the audit / feature doc in the last column carries the
  gating decision, the pre-checks, and the rollback position. The script alone is not the authority.
- A new script lands here **only** when a repository document specifies it as a deliverable — which now
  explicitly includes a dated **BUG / ISSUE / FEATURE** record under
  [`../bugs/`](../bugs/) · [`../issues/`](../issues/) · [`../features/`](../features/) whose
  `## Database Impact` is `MANUAL DBA SCRIPT` (or `BOTH`). Ad-hoc, session-scoped scripts with no
  specifying document still belong in the workspace `Claude Code (AI)/sql/` folder.
- **The 2026-08-17 archive migration is the one exception to the rule above.** Several scripts under
  [`2026/`](2026/) predate the docs-first workflow and have **no** specifying document — their header
  reads `Related record: TBD`. They were brought in because they are durable manual DB changes worth
  version-controlling, not because a document specified them. Do not treat them as precedent for
  landing a new undocumented script here.

## Related

- [`../references/database/flyway-migration-baseline.md`](../references/database/flyway-migration-baseline.md) — why Flyway is disabled and how DDL actually reaches a database
- [`../references/database/legacy-table-inventory.md`](../references/database/legacy-table-inventory.md) — the authoritative table baseline any DDL must respect
- [`../deployment/README.md`](../deployment/README.md) — deployment run-books and checklists
