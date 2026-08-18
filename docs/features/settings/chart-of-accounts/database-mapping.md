# Chart of Accounts — Database Mapping


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

**Table:** `CHART_ACCT_INFM` (15 columns) · **Migration:** V94 (schema), V95 (seq branch), V96 (restricted-root seed), V97 (demo seed).
**Active table list:** `CHART_ACCT_INFM` is in the PROD active list. No related hierarchy/classification side-tables — hierarchy is self-referential via `PARENT_CHART_ACCT_CD`; classification (`CHART_ACCT_TYPE`) is a single column, unused in DEV.

Detailed DB reference (preserved): [../../../references/database/chart-of-accounts.md](../../../references/database/chart-of-accounts.md).

---

## 1. Table `CHART_ACCT_INFM`

| Column | Type | Null | Key | Notes |
|---|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | NO | PK1 | Tenant scope |
| `CHART_ACCT_SEQ_NO` | VARCHAR(14) | NO | PK2 | Per-tenant surrogate, numeric-text; `wb_get_next_val` MAX+1 |
| `CHART_ACCT_CD` | VARCHAR(20) | NO | UK | User-facing code |
| `CHART_ACCT_NM_EN` | VARCHAR(255) | NO | | English name |
| `CHART_ACCT_NM_VN` | VARCHAR(255) | YES | | Vietnamese name |
| `CHART_ACCT_NM_KO` | VARCHAR(255) | YES | | Korean name |
| `CHART_ACCT_TYPE` | VARCHAR(100) | YES | | Classification — **unused in DEV** (all NULL) |
| `PARENT_CHART_ACCT_CD` | VARCHAR(20) | YES | | Parent code; NULL = root. No FK (self-referential by value, mirrors DEV) |
| `CAL_RULE` | CHAR(1) | YES | | `P` / `M` / null; entity uses `@JdbcTypeCode(SqlTypes.CHAR)` |
| `STTS` | CHAR(1) | NO | | `A` Active / `I` Inactive |
| `DEL_YN` | CHAR(1) | NO | UK | `N` / `Y` soft-delete flag |
| `REG_ID` | VARCHAR(100) | NO | | Create user |
| `REG_DTM` | VARCHAR(14) | NO | | Create stamp `yyyyMMddHHmmss` |
| `MOD_ID` | VARCHAR(100) | NO | | Last-modify user |
| `MOD_DTM` | VARCHAR(14) | NO | | Last-modify stamp |

**Primary key:** `(BIZ_KEY, CHART_ACCT_SEQ_NO)` — `@IdClass(ChartAcctInfmId.class)`.
**Unique key:** `uk_chart_acct_cd (BIZ_KEY, CHART_ACCT_CD, DEL_YN)` — the 3-column UK is what enables revive-on-create and archive-rename-on-delete (one active + one soft-deleted row may share a code).
**Helper indexes (beyond DEV):** `ix_chart_acct_infm_biz_parent` (partial `WHERE del_yn='N'`) powers child re-parent + has-children; `ix_chart_acct_infm_biz_moddtm` supports the optional MOD_DTM sort.
**No CHECK / FK** on DEV → none locally (parity).

---

## 2. Column mapping — Legacy → Entity → DTO → UI

| Concept | Legacy column | Refactor entity field (`ChartAcctInfm`) | Wire / DTO (`ChartOfAccountRow`) | Frontend (`ChartOfAccountsRow`) | UI label |
|---|---|---|---|---|---|
| Account Code | `CHART_ACCT_CD` | `chartAcctCd: String` | `chart_acct_cd` | `accountCode` | Account Code |
| Account Name (EN) | `CHART_ACCT_NM_EN` | `chartAcctNmEn: String` | `chart_acct_nm_en` | `accountTypeEn` | Account Type (English) |
| English Name | (same as above) | `chartAcctNmEn` | `chart_acct_nm_en` | `accountTypeEn` | — |
| Account Name (VN) | `CHART_ACCT_NM_VN` | `chartAcctNmVn: String` | `chart_acct_nm_vn` | `accountTypeVi` | Account Type (Vietnamese) |
| Account Name (KO) | `CHART_ACCT_NM_KO` | `chartAcctNmKo: String` | `chart_acct_nm_ko` | `accountTypeKo` | Account Type (Korean) |
| Parent Account Code | `PARENT_CHART_ACCT_CD` | `parentChartAcctCd: String` | `parent_chart_acct_cd` | `parentCode` | Parent Account |
| Parent Account Name | — (resolved) | (resolved in `TreeBuilder` breadcrumb) | `breadcrumb` (tree node) | parent badge `{code} {name}` | Parent Account |
| Account Type / classification | `CHART_ACCT_TYPE` | `chartAcctType: String` | `chart_acct_type` | (not rendered) | — (unused) |
| Account Level | — (computed) | computed by `TreeBuilder.resolveLevel` | `chart_acct_level` (1–3/null) | `chartAcctLevel` | (drives indentation) |
| Cal Rule | `CAL_RULE` | `calRule: String` (`@JdbcTypeCode CHAR`) | `cal_rule` | `calcRule` (`+`/`-`/null) | Calc Rule |
| Display Order | — | **n/a** — no display-order column; ordering is derived (see §3) | — | — | — |
| Status | `STTS` | `stts: String` (`@JdbcTypeCode CHAR`) | `stts` | `status` (`active`/`inactive`) | Status |
| Soft-delete | `DEL_YN` | `delYn: String` (`@JdbcTypeCode CHAR`) | — (filtered server-side) | — | — |
| Created By / Date | `REG_ID` / `REG_DTM` | `regId` / `regDtm` | `reg_id` / `reg_dtm` | `audit.regId` / `regDtm` | — |
| Updated By / Date | `MOD_ID` / `MOD_DTM` | `modId` / `modDtm` | `mod_id` / `mod_dtm` | `audit.modId` / `modDtm` | — |
| Has children | — (computed) | computed | `has_children` | `hasChildren` (group/child kind) | — (group vs child rendering) |
| Restricted | — (literal match) | computed | `restricted` | `restricted` | disables Edit/Delete |

> **Note on "Account Level" / "Display Order":** the prompt's column checklist lists *Account Level* and *Display Order* as DB columns. In the actual schema, **`CHART_ACCT_LEVEL` does not exist as a column** — it is computed in Java by walking the parent chain. There is **no `DISP_ORD` column** — rows are ordered by `CHART_ACCT_CD` (default sort) and the tree DFS pre-order in `ChartOfAccountsTreeBuilder` (roots first → descendants → orphans). This is documented here to correct the assumption.

---

## 3. Ordering / display-order logic

There is no persisted display-order column. Output ordering is derived:

- **List default sort:** `CHART_ACCT_CD ASC, CHART_ACCT_SEQ_NO ASC` (legacy R001 parity); user can sort on `CHART_ACCT_CD` / `CHART_ACCT_NM_EN` / `STTS` / `MOD_DTM`.
- **Tree / grid render order** (`ChartOfAccountsTreeBuilder.buildRows`): DFS pre-order from the level-1 roots, sorted by code — `ACC-01` + descendants, `ACC-02` + descendants, `ACC-03` + descendants, then any user-created roots, then orphans/cycles (level=null) appended last by code. This matches the Figma display requirement (9196:12740 + PO directive 2026-05-25).

---

## 4. Sequence / key generation

`CHART_ACCT_SEQ_NO` is allocated via the legacy `wb_get_next_val('chart_acct_infm','chart_acct_seq_no', biz_key)` branch (V95, ported verbatim from DEV — per-tenant `COALESCE(MAX(chart_acct_seq_no::numeric),0)+1`), reached through `LegacyFunctionRepository.nextChartAcctSeqNo`. The create flow wraps allocation + INSERT in one `@Transactional` and retries once on `DataIntegrityViolationException`. Retirement plan: replace with a Java-side per-tenant generator once legacy WeCMS/WABOOKS no longer calls `wb_get_next_val` for this table (see wecms-api/CLAUDE.md → "Surviving legacy functions").

---

## 5. Seed data

| Migration | Contents |
|---|---|
| V94 | Schema (15 cols, PK, UK, 2 helper indexes) |
| V95 | `wb_get_next_val` branch for `chart_acct_seq_no` |
| V96 | Restricted roots `ACC-01`/`ACC-02`/`ACC-03` (REVENUE/EXPENSES/INVESTMENT) for every active tenant; `reg_id=mod_id=biz_key` (legacy convention); idempotent on the 3-col UK |
| V97 | Demo seed for `VN_VNM0004_000001` — 9 real DEV rows (seq 90..112), DEV seq values preserved. Two grandfathered shapes kept verbatim: seq 104 self-reference; seq 112 orphan parent (`Code009` missing) — both surface with `chart_acct_level=null` |
