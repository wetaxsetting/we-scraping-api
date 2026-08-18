# Contacts — Database Mapping (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

The six tables behind Contacts, the surviving legacy functions, and the full
legacy-column → entity-field → DTO-field → UI-field mapping. Schema source (current per-table Flyway
scheme after the 2026-06-09 consolidation — one legacy table per file, DDL copied verbatim from the
live JEX DB): [V112](../../../../src/main/resources/db/migration) `trco_acct_info` · V114
`trco_addr_infm` · V116 `trco_info_file` · V119 `trco_infm` · V121 `trco_ovrs_infm` · V82
`pgro_bsac_lnkn_infm`. `DISP_ORD` stays `NUMERIC(10,0)` in these files (mapped via
`@JdbcTypeCode(NUMERIC)`); there is **no** `disp_ord → INTEGER` hotfix in the current set. Column-for-column
parity with JEX DEV (verified via the `postgres_dev_server_jexframe` MCP).

---

## Core tables

### `TRCO_INFM` — contact master (47 cols; PK `TRCO_KEY` **alone** — global sequence)

| Column | Type | Entity field | Notes |
|---|---|---|---|
| `TRCO_KEY` | VARCHAR(20) NN | `trcoKey` | Global PK from `sq_trco_key` (not per-tenant) |
| `BIZ_KEY` | VARCHAR(20) NN | `bizKey` | Tenant scope — NOT in the PK |
| `TAX_ID` | VARCHAR(40) NN | `taxId` | Tenant tax-id snapshot (NOT-NULL fallback = `bizKey`) |
| `TRCO_CMNM` | VARCHAR(200) NN | `trcoCmnm` | Local-language name |
| `TRCO_CMENM` | VARCHAR(200) NN | `trcoCmenm` | English name |
| `TRCO_TAX_ID` | VARCHAR(40) | `trcoTaxId` | Contact's own tax id |
| `TRCO_TYPE` | VARCHAR(1) | `trcoType` | `L` / `O` |
| `TRCO_STTS` | CHAR(1) | `trcoStts` | `'C'` = trashed, null = active |
| `TRSC_CCY_CD` | CHAR(3) NN | `trscCcyCd` | CHECK `IN ('VND','USD')` |
| `HAND_TRCO_KEY` | VARCHAR(20) | `handTrcoKey` | User-facing Contact Code |
| `TRCO_NICK_NM` | VARCHAR(200) | `trcoNickNm` | UK with `BIZ_KEY` (`trco_infm_uk01`) |
| `TRCO_INQ_YN` | CHAR(1) NN DEFAULT 'Y' | `trcoInqYn` | OQ-3 |
| `REG_ID/REG_DTM/MOD_ID/MOD_DTM` | VARCHAR(100/14) | audit | reg NN, mod nullable (legacy `VARCHAR(14)` strings, not `BaseEntity`) |

Constraints: carried over verbatim from the live JEX DDL (PK on `TRCO_KEY` alone; UK / CHECK as DEV
defines them). The per-table V119 file copies the full DEV column set + keys; the refactor entity maps
only the Sprint subset. (Columns `TRCO_RPRS_TLPH` / `EML` exist in the schema for DEV parity but are
**not mapped** on the entity and **not on the wire** — Contacts has no Phone/Email feature surface.)

### `TRCO_ADDR_INFM` — addresses (PK `(TRCO_KEY, BIZ_KEY, TRCO_ADDR_KEY)`)

`TRCO_BSUN_POST_ADRS VARCHAR(400)` (`trcoBsunPostAdrs`) · `DISP_ORD NUMERIC(10,0) NN` (`dispOrd`;
1 = current, 2 = old — DEV-parity; entity maps it via `@JdbcTypeCode(NUMERIC)`) · `ISSU_DT` /
`DUE_DT VARCHAR(8) NN` (`yyyyMMdd`) · audit cols.

### `TRCO_ACCT_INFO` — Local bank accounts (PK `(BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY)`)

`ACCT_NO VARCHAR(100) NN` (`acctNo`) · `DISP_ORD NUMERIC(10,0) NN` (`dispOrd`; `@JdbcTypeCode(NUMERIC)`) ·
`BANK_NM VARCHAR(200)` (`bankNm` — stored display copy) · `NAPAS_BANK_CD VARCHAR(10)` · `CITAD_BANK_CD
VARCHAR(8)` · `ACCT_CCY_CD VARCHAR(3)` · `FINC_MDA_OWNER_NM VARCHAR(150)` (account-owner name) ·
`OWAC_ADDR VARCHAR(500)` · plus the DEV-parity legacy columns `BANK_CD` / `TRCO_NM` / `TRCO_ENM` /
`OWAC_NM` / `NAPAS_BANK_YN` (present in the table, not mapped on the entity) · audit. FK
`trco_acct_info_fk01 (TRCO_KEY) → TRCO_INFM`; BEFORE-INSERT/UPDATE trigger `reject_not_existing_bank_cd()`
on `BANK_CD`.

### `TRCO_OVRS_INFM` — Overseas beneficiaries (PK `(TRCO_KEY, BIZ_KEY, TRCO_OVRS_KEY)`)

`BENE_NAME(200)` (`beneName`) · `BIC_CODE(50)` (`bicCode`, SWIFT) · `BENE_ACCT_NO(100)` (`beneAcctNo`)
· `BENE_ADDR(500)` (`beneAddr`) · `BENE_BANK_CODE(20)` (`beneBankCode`) · `BENE_BANK_NAME(200)`
(`beneBankName`) · audit.

### `PGRO_BSAC_LNKN_INFM` — group links (PK `(BIZ_KEY, USER_ID, GRP_CD, TRCO_KEY)`)

UK `pgro_bsac_lnkn_infm_uk01 (BIZ_KEY, GRP_CD, TRCO_KEY)`; FKs to `BUSI_INFM(BIZ_KEY)` +
`TRCO_INFM(TRCO_KEY)`. Seeded on create with `GRP_CD ∈ {B,S}`, `USER_ID='ALL'`.

### `TRCO_INFO_FILE` — attachments (PK `(TRCO_KEY, FILE_ID)`; **no `BIZ_KEY` column**)

`FILE_ID VARCHAR(100) NN` (per-`TRCO_KEY` MAX+1) · `ORG_FILE_NM` / `SAVE_FILE_NM VARCHAR(500) NN`
(`saveFileNm` = UUID + ext) · `REG_ID/REG_DTM/REG_NM` NN (`REG_NM` materialised from `USER_INFM.USER_NM`)
· `FILE_SIZE VARCHAR(100)` (legacy stores byte count as text) · `TRCO_INFM_TITL VARCHAR(50) NN` (DEV
default `'기타문서'`) · `MOD_ID/MOD_DTM`. FK `trco_info_file_fk01 (TRCO_KEY) → TRCO_INFM`. Tenancy
inherited from the parent.

## Relationships

`TRCO_INFM` (1) ↔ (0..N) `TRCO_ADDR_INFM` / `TRCO_ACCT_INFO` / `TRCO_OVRS_INFM` / `TRCO_INFO_FILE`,
keyed by `TRCO_KEY` (+`BIZ_KEY` on the three child masters). No JPA `@OneToMany` cascade — children are
managed manually in `ContactsService` (combined upsert/delete pass). `PGRO_BSAC_LNKN_INFM` links a
contact to a tenant's group buckets. See [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md).

## PK generation + bank-name resolution (surviving legacy functions)

| Function / sequence | Migration | Consumed by |
|---|---|---|
| `sq_trco_key` (START 0) + `wb_get_next_val('trco_infm','trco_key')` branch | sequence in V159, branch in V140 | `LegacyFunctionRepository.nextTrcoKey` → `ContactsService.create` (OQ-4) |
| `wb_get_next_val('trco_info_file','file_id', trcoKey)` branch | V140 | `ContactsFileService` |
| `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang)` (reads `tb_firm_bank_code`) | **not in any refactor migration — expected to pre-exist in the live JEX DB** | `LegacyFunctionRepository.resolveNapasOrCitadBankName` → `ContactsService.list/getOne` (OQ-7) |

The `wb_get_next_val` body is copied verbatim from DEV; `wb_get_napas_bank_nm_or_citad_bank_nm` is
called directly (the refactor runs against the live JEX DB in all envs and does not redefine it).
Retirement plans are in the `wecms-api/CLAUDE.md` "Surviving legacy functions" table.

## Column-mapping chain (legacy column → entity field → DTO field → UI field)

| Business field | Legacy / DB column | Entity field | DTO field (wire) | UI (frontend) |
|---|---|---|---|---|
| Contact Code | `TRCO_INFM.HAND_TRCO_KEY` | `TrcoInfm.handTrcoKey` | `ContactRow.handTrcoKey` (`hand_trco_key`) | `contactCode` |
| Contact Name | `TRCO_INFM.TRCO_CMNM` | `TrcoInfm.trcoCmnm` | `ContactRow.trcoCmnm` | `contactName` |
| English Name | `TRCO_INFM.TRCO_CMENM` | `TrcoInfm.trcoCmenm` | `ContactRow.trcoCmenm` | `contactEnglishName` (auto = name; no input) |
| Contact Type | `TRCO_INFM.TRCO_TYPE` (`L`/`O`) | `TrcoInfm.trcoType` | `ContactRow.trcoType` | `location` (`local`/`overseas`) |
| Business / Tax Number | `TRCO_INFM.TRCO_TAX_ID` | `TrcoInfm.trcoTaxId` | `ContactRow.trcoTaxId` | `taxId` |
| Phone | `TRCO_INFM.TRCO_RPRS_TLPH` | — (not mapped) | — (not on wire) | — (no field) |
| Email | `TRCO_INFM.EML` | — (not mapped) | — (not on wire) | — (no field) |
| Address (current) | `TRCO_ADDR_INFM.TRCO_BSUN_POST_ADRS` (`disp_ord=1`) | `TrcoAddrInfm.trcoBsunPostAdrs` | `ContactAddressRow.trcoBsunPostAdrs` | `address` |
| Address (old) | `TRCO_ADDR_INFM.TRCO_BSUN_POST_ADRS` (`disp_ord=2`) + `DUE_DT` | same | `ContactAddressRow` (`disp_ord=2`) | `changedAddress` + `validTill` |
| Bank Name (Local) | `TRCO_ACCT_INFO.BANK_NM` + `wb_get_napas_bank_nm…` | `TrcoAcctInfo.bankNm` | `ContactAccountRow.bankNm` | `accounts[].bank` |
| Account Number (Local) | `TRCO_ACCT_INFO.ACCT_NO` | `TrcoAcctInfo.acctNo` | `ContactAccountRow.acctNo` | `accounts[].accountNo` |
| Currency (Local) | `TRCO_ACCT_INFO.ACCT_CCY_CD` | `TrcoAcctInfo.acctCcyCd` | `ContactAccountRow.acctCcyCd` | `accounts[].currency` |
| Recipient (Local) | `TRCO_ACCT_INFO.FINC_MDA_OWNER_NM` | `TrcoAcctInfo.fincMdaOwnerNm` | `ContactAccountRow.fincMdaOwnerNm` | `accounts[].recipient` |
| Beneficiary Name (Overseas) | `TRCO_OVRS_INFM.BENE_NAME` | `TrcoOvrsInfm.beneName` | `ContactOverseasRow.beneName` | `overseas.beneficiaryName` |
| Beneficiary Bank (Overseas) | `TRCO_OVRS_INFM.BENE_BANK_NAME` | `beneBankName` | `ContactOverseasRow.beneBankName` | `overseas.beneficiaryBankName` |
| BIC / SWIFT (Overseas) | `TRCO_OVRS_INFM.BIC_CODE` | `bicCode` | `ContactOverseasRow.bicCode` | `overseas.swiftCode` |
| Beneficiary Account (Overseas) | `TRCO_OVRS_INFM.BENE_ACCT_NO` | `beneAcctNo` | `ContactOverseasRow.beneAcctNo` | `overseas.beneficiaryAccountNo` |
| Beneficiary Address (Overseas) | `TRCO_OVRS_INFM.BENE_ADDR` | `beneAddr` | `ContactOverseasRow.beneAddr` | `overseas.beneficiaryAddress` |
| Currency (master) | `TRCO_INFM.TRSC_CCY_CD` | `TrcoInfm.trscCcyCd` | (derived, not on `ContactRow`) | (derived from type) |
| Status | `TRCO_INFM.TRCO_STTS` (`'C'`/null) | `TrcoInfm.trcoStts` | `ContactRow.trcoStts` | (delete actions only — no status column) |
| Row identity | `TRCO_INFM.TRCO_KEY` | `TrcoInfm.trcoKey` | `ContactRow.trcoKey` | `trcoKey` / `id` |

> **Phone / Email note:** the prompt's example field list includes Phone and Email. They exist as DEV
> columns (`TRCO_RPRS_TLPH`, `EML`) for schema parity but are **not** part of the refactor's mapped
> entity, DTO, grid, or create/edit form — Contacts has no Phone/Email feature surface.

## Schema deviations from DEV (documented, refactor-only)

- `disp_ord` is kept as DEV's `NUMERIC(10,0)` in the current per-table migrations (V112/V114) and the
  entity maps it via `@JdbcTypeCode(NUMERIC)` against a Java `Integer` field — the earlier local-INTEGER
  deviation (old V117 hotfix) was retired in the 2026-06-09 consolidation. No deviation here now.
- Helper indexes (`ix_trco_*`) — the consolidated per-table files copy the live JEX DDL verbatim
  (incl. triggers, `bpchar`, full column sets); extra local-only helper indexes are not part of the
  current scheme.
- `trco_tax_id` length divergence: `ContactCreateRequest` allows `@Size(100)`, `ContactUpdateRequest`
  + DB column cap at 40 — a 41–100-char create passes bean validation then hits the DB constraint
  (logged in [refactor-gap-analysis.md](refactor-gap-analysis.md)).

See [references/database/contacts-schema.md](../../../references/database/contacts-schema.md) for the
preserved schema-design narrative.
