# Firm Banking Settings — Functional Specification (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

The implemented behaviour of the Firm Banking Settings backend. JEX behaviour reference (read-only):
WSVC family `comm_0220_001/002/003`. Deep design narrative + OQ decision log:
[../firm-banking-settings.md](../firm-banking-settings.md).

---

## 1. Operations matrix

| # | Operation | HTTP | URI | Maps to JEX | Audit |
|---|---|---|---|---|---|
| 1 | List | GET | `/api/v1/settings/firm-banking-settings` | `comm_0220_001_r001` | No |
| 2 | Detail | GET | `/api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` | `comm_0220_003_r001` (entity leg) | No |
| 3 | Audit log | GET | `…/{out_bank_cd}/{wdrl_act_no}/audit` | `comm_0220_003_r001` (audit `REC` leg) | No |
| 4 | Create | POST | `/api/v1/settings/firm-banking-settings` | `comm_0220_002_c001` | Yes |
| 5 | Update | PUT | `…/{out_bank_cd}/{wdrl_act_no}` | `comm_0220_003_u001` | Yes |
| 6 | Delete | DELETE | `…/{out_bank_cd}/{wdrl_act_no}` | `comm_0220_001_d001` | **No** (OQ-2) |

**Not ported (DEAD LEGACY / out of scope):** none specific to `comm_0220` — the legacy family is a flat
CRUD + audit set. Approval-line integration is absent on both sides (OQ-7). See
[refactor-gap-analysis.md](refactor-gap-analysis.md).

---

## 2. Field contract

### 2.1 List row (output — `FirmBankingSettingRow`, 18 fields)

| Wire field (snake) | Type | Source | Notes |
|---|---|---|---|
| `biz_key` | string | `TB_FIRM_CONF.BIZ_KEY` | tenant |
| `out_bank_cd` | string(≤15) | `OUT_BANK_CD` | NAPAS code; PK component 1 |
| `bank_nm` | string \| null | view `COALESCE(BANK_NM, BANK_ENG_NM)` | display name |
| `ccy_cd` | string \| null | `ACCT_MSTR.CCY_CD` | currency suffix |
| `bank_prtn_id` | string | `BANK_PRTN_ID` | partner id |
| `van_org_cd` | string | `VAN_ORG_CD` | VAN org code |
| `org_stat` | string | `ORG_STAT` | `0`/`1`/`2`/`3` |
| `wdrl_act_no` | string(≤100) | `WDRL_ACT_NO` | **cleartext** on the wire (PK component 2); display masking is frontend-only |
| `file_type` | string | `FILE_TYPE` | internal (SHB/VCB/WRB) |
| `bat_tran_branch_div` | string | `BAT_TRAN_BRANCH_DIV` | branch division flag |
| `bat_tran_branch_limit` | number | `BAT_TRAN_BRANCH_LIMIT` | branch limit |
| `reg_dttm` | string `yyyyMMddHHmmss` | `REG_DTTM` | created time |
| `start_dttm` / `end_dttm` | string \| null | `START_DTTM`/`END_DTTM` | optional window |
| `mod_dttm` | string \| null | `MOD_DTTM` | last-updated time |
| `last_edtr` | string \| null | `LAST_EDTR` | last-updated actor |
| `bal` | number \| null | `ACCT_MSTR.BAL` | reserved (balance) |
| `expi_dt` | string \| null `yyyyMMdd` | `ACCT_MSTR.EXPI_DT` | reserved (expiry) |

### 2.2 Detail row (output — `FirmBankingSettingDetail`, 14 fields)

Same `TB_FIRM_CONF` fields as the list row **minus** the four JOIN columns (`bank_nm`, `ccy_cd`, `bal`,
`expi_dt`), and `wdrl_act_no` is returned **cleartext** (edit-dialog round-trip — OQ-4 §9).

### 2.3 Create request (`FirmBankingSettingCreateRequest`, 5 fields)

| Field | Required | Length | Rule |
|---|---|---|---|
| `out_bank_cd` | yes | ≤15 | `@NotBlank @Size(15) @Pattern(^[0-9]{1,15}$)`; must be in `{970424,970436,970457}` → `WBE0001`; must exist in `TB_FIRM_BANK_CODE` for `ANT_DIV_CD='NAPAS'` → `WBE0002` |
| `wdrl_act_no` | yes | ≤100 | `@NotBlank @Size(100) @Pattern(^[A-Za-z0-9\-]{1,100}$)` |
| `bank_prtn_id` | yes | ≤20 | `@NotBlank @Size(20)` |
| `van_org_cd` | yes | ≤20 | `@NotBlank @Size(20)` |
| `org_stat` | yes | 1 | `@NotBlank @Pattern(^[0-3]$)` (must be `0`/`1`/`2`/`3`) |

Server-derived on Create: `file_type` (from `out_bank_cd`), `bat_tran_branch_div = 'Y'`,
`bat_tran_branch_limit = 2000`, `reg_dttm`, `last_edtr`/`reg_id` (from `TenantContext`), `biz_key`.
**`file_type` is never client-supplied** — never add it to the request shape (OQ-8a).

### 2.4 Update request (`FirmBankingSettingUpdateRequest`, 3 fields)

`bank_prtn_id` (`@NotBlank @Size(20)`), `van_org_cd` (`@NotBlank @Size(20)`), `org_stat`
(`@NotBlank @Pattern(^[0-3]$)`). The composite PK (`out_bank_cd` + `wdrl_act_no`) is the immutable path;
`file_type` is set only on Create and never changes on Update.

### 2.5 Audit entry (output — `FirmBankingAuditEntry`)

`audit_logs_actv` (string\|null — activity), `audit_logs_actv_dtl` (string — detail, pre-localized),
`reg_id`, `reg_nm`, `reg_dtm` (`yyyyMMddHHmmss`). Rendered verbatim (already in the caller's locale).

---

## 3. Business rules

### 3.1 Bank scope (V8, OQ-8a Option c — provisional)

`out_bank_cd` is restricted to the three banks verified live in DEV:

```
970424 → Shinhan      → FILE_TYPE = SHB
970436 → Vietcombank  → FILE_TYPE = VCB
970457 → Woori        → FILE_TYPE = WRB
```

Anything else → `WBE0001`. `FILE_TYPE` is derived server-side from this map (the client never sends it).
This is **provisional** (Mr. Jung may widen it); adding a fourth bank is one map entry + one allow-list
entry, never a re-architecture. `V9` (existence in `NAPAS_BANK_INFO`) is **deferred** — that table is not
migrated locally and `V8` is strictly stronger for the supported set.

### 3.2 Referential + duplicate guards (Create)

- `V10` — `TbFirmBankCodeRepository.existsByAntDivCdAndBankCd("NAPAS", out_bank_cd)` → `WBE0002` if absent.
- `V12` — `existsById(id)` → `WBE2021` if the composite PK already exists for the tenant.

### 3.3 Hard delete (no soft-delete)

Delete is a **physical** `deleteById` — `TB_FIRM_CONF` has no soft-delete column. The legacy
`comm_0220_001_d001` is also a physical delete. Delete writes **no** audit row (OQ-2, legacy parity).

### 3.4 Sensitive-field handling (OQ-4)

`WDRL_ACT_NO` stays **plain-text in the DB** (no feature-local encryption, no `@Convert`). The backend
ships it **cleartext on both list and detail** — it is the PK component the frontend uses to address the
row for every write flow, and an earlier server-side mask broke those writes (the `@Pattern` guard rejects
`*`). Display masking (`1009****0384`) lives in the frontend mapper (`mapFirmBankingSettingRow`), not in
this service; server logs still mask via `LogMask.acct(...)`. Project-wide encryption (covering
`ACCT_MSTR`, `TB_FIRM_CONF`, `USER_BUSI_CONN_INFM`, …) is a separate, deferred migration.

### 3.5 Tenant scope

`BIZ_KEY` is read only from `TenantContext.currentBizKey()` (signed JWT). It is the leading PK column on
every query; a missing tenant context throws `WBE9999` (should never happen behind the JWT filter).

---

## 4. Authorization

- Controller class-level: `@PreAuthorize("@menuAuthz.canAccess('settings-firm-banking-settings')")` —
  every endpoint (read and write) requires the `settings-firm-banking-settings` menu grant resolved from
  `USER_MENU_AUTH_INFM` (admins bypass the per-user ∩ but not the product scope). F-3 server-side
  menu-authorization model.
- `@Validated` (class-level) enables the `@Pattern`/`@Size` guards on the path params (`out_bank_cd`
  `^[0-9]{1,15}$`; `wdrl_act_no` `^[A-Za-z0-9\-]{1,100}$`). Bad path input → `WBE_VALIDATION`.

---

## 5. Audit-log expectations

Every Create / Update writes to `AUDIT_LOGS_INFM` via the shared `AuditLogService`:

| Column | Value |
|---|---|
| `WSVC_ID` | `firm-banking-settings` (slug — never `comm_0220_001`) |
| `REF_NO` | `TB_FIRM_CONF` (table marker) |
| `REF_NO1` | `out_bank_cd` |
| `REF_NO2` | `wdrl_act_no` |
| activity | `audit.firm_banking_settings.activity` |
| detail | `audit.firm_banking_settings.{create,update}.detail` |

Reads and **Delete** write no audit row. Append runs `@Transactional(REQUIRES_NEW)` inside the shared
writer so an audit failure does not roll back the business write. One business event writes one
`audit_logs_seq_no` reused across the per-locale rows.

---

## 6. Error surface

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE0001` | 200 | `out_bank_cd` outside the supported 3-bank set (V8) |
| `WBE0002` | 200 | Bank not in `TB_FIRM_BANK_CODE` for `ANT_DIV_CD='NAPAS'` (V10) |
| `WBE2020` | 200 | Firm-banking-setting not found in tenant (Detail / Update / Delete — merges "not found" + "wrong tenant") |
| `WBE2021` | 200 | Composite PK already exists (Create — V12) |
| `WBE_VALIDATION` | 200/400 | Bean validation / blank path component |
| `WBE1009` / `WBE1003` | 401 / 403 | JWT missing-invalid / authenticated-but-unauthorized |
| `WBE9999` | 500 | Missing tenant context / unexpected server error |

`WBE2022` (invalid-status-transition) and `WBE2023` (sensitive-field-access-denied) were allocated
(OQ-1) but are **reserved / not used in v1** — no status FSM and no secret-reveal gate exist today. Full
request/response shapes: [interface-specification.md](interface-specification.md).
