# Firm Banking Settings — Interface Specification (Backend REST contract)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

All endpoints under base path `/api/v1/settings/firm-banking-settings`. Wire format is lowercase
snake_case (global Jackson `SNAKE_CASE`); envelope is `{ status, data, timestamp?, path?, trace_id? }`.
Controller: [FirmBankingSettingsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java).

## Authorization (all endpoints)

```java
@PreAuthorize("@menuAuthz.canAccess('settings-firm-banking-settings')")   // class-level
@Validated                                                                // class-level (path guards)
```

Every endpoint (read and write) requires a valid JWT **and** the `settings-firm-banking-settings` menu
grant (`USER_MENU_AUTH_INFM`; admins bypass the per-user ∩ but not the product tree). Missing/invalid
JWT → 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`.

Path guards (`@Validated`): `out_bank_cd` → `@Size(max=15) @Pattern(^[0-9]{1,15}$)`; `wdrl_act_no` →
`@Size(max=100) @Pattern(^[A-Za-z0-9\-]{1,100}$)`. Frontend URL-encodes both path components.

---

## 1. List

```
GET /api/v1/settings/firm-banking-settings
```

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "biz_key": "VN_VNM0004_000001",
        "out_bank_cd": "970424",
        "bank_nm": "ShinhanBank",
        "ccy_cd": "VND",
        "bank_prtn_id": "PRTN01",
        "van_org_cd": "ORG01",
        "org_stat": "1",
        "wdrl_act_no": "10090030384",
        "file_type": "SHB",
        "bat_tran_branch_div": "Y",
        "bat_tran_branch_limit": 2000,
        "reg_dttm": "20260509101500",
        "start_dttm": null,
        "end_dttm": null,
        "mod_dttm": "20260509101500",
        "last_edtr": "Krong",
        "bal": 19886030287,
        "expi_dt": null
      }
    ]
  }
}
```

`wdrl_act_no` ships **cleartext** here — it is the PK component the frontend addresses for Detail /
Update / Delete / Audit, and the write endpoints' `@Pattern("^[A-Za-z0-9\-]{1,100}$")` guard rejects the
`*` masking characters. The displayed masking (e.g. `1009****0384`) is applied **frontend-side** by
`mapFirmBankingSettingRow`, not by the backend. No filters, no pagination (legacy parity). Rows are
ordered `OUT_BANK_CD ASC, WDRL_ACT_NO ASC`.

## 2. Detail

```
GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}
→ data: FirmBankingSettingDetail (flat; cleartext wdrl_act_no; no bank_nm/ccy_cd/bal/expi_dt)
errors: WBE2020 (not found in tenant) · WBE_VALIDATION (bad path)
```

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "biz_key": "VN_VNM0004_000001", "out_bank_cd": "970424",
    "bank_prtn_id": "PRTN01", "van_org_cd": "ORG01", "org_stat": "1",
    "wdrl_act_no": "10090030384", "file_type": "SHB",
    "bat_tran_branch_div": "Y", "bat_tran_branch_limit": 2000,
    "reg_dttm": "20260509101500", "start_dttm": null, "end_dttm": null,
    "mod_dttm": "20260509101500", "last_edtr": "Krong"
  }
}
```

## 3. Audit log

```
GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}/audit
→ data: FirmBankingAuditListResponse  (caller-locale rows, latest-first)
```

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      { "audit_logs_actv": "Firm Banking Details",
        "audit_logs_actv_dtl": "Update firmbanking details",
        "reg_id": "admin", "reg_nm": "Krong", "reg_dtm": "20260509101500" }
    ]
  }
}
```

Filters `REF_NO='TB_FIRM_CONF'`, `WSVC_ID='firm-banking-settings'`, `LANG_CD=` caller locale. Returns
`[]` for an unknown PK (no existence pre-check).

## 4. Create

```
POST /api/v1/settings/firm-banking-settings
{ "out_bank_cd": "970424", "wdrl_act_no": "10090030384",
  "bank_prtn_id": "PRTN01", "van_org_cd": "ORG01", "org_stat": "1" }
→ 200, data: FirmBankingSettingDetail (post-write echo, cleartext wdrl_act_no)
errors: WBE0001 (bank outside 3-set) · WBE0002 (bank not in TB_FIRM_BANK_CODE) ·
        WBE2021 (PK exists) · WBE_VALIDATION (bean validation)
```

`file_type` is **not** in the request — server-derived from `out_bank_cd`. Writes Create audit rows.

## 5. Update

```
PUT /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}
{ "bank_prtn_id": "PRTN02", "van_org_cd": "ORG02", "org_stat": "1" }
→ 200, data: FirmBankingSettingDetail (refreshed)
errors: WBE2020 (not found) · WBE_VALIDATION (bean validation / bad path)
```

Editable fields only; PK + `file_type` immutable. Writes Update audit rows.

## 6. Delete

```
DELETE /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}
→ 200, data: null    (hard delete; @JsonInclude(NON_NULL) omits the data key)
errors: WBE2020 (not found in tenant) · WBE_VALIDATION (bad path)
```

No audit row (OQ-2). Tenant-scoped — a delete against another tenant's row surfaces `WBE2020`
(indistinguishable from "truly missing" — intentional).

---

## Error-code registry

| Code | HTTP | Meaning |
|---|---|---|
| `WBI0000` | 200 | Success |
| `WBE0001` | 200 | `out_bank_cd` outside the supported 3-bank set (Create, V8) |
| `WBE0002` | 200 | Bank not in `TB_FIRM_BANK_CODE` (Create, V10) |
| `WBE2020` | 200 | Firm-banking-setting not found in tenant (Detail / Update / Delete) |
| `WBE2021` | 200 | Composite PK already exists (Create, V12) |
| `WBE_VALIDATION` | 200/400 | Bean validation, bad/blank path component |
| `WBE1009` | 401 | JWT missing / invalid |
| `WBE1003` | 403 | Authenticated but lacks `settings-firm-banking-settings` |
| `WBE9999` | 500 | Missing tenant context / unexpected server error |

`WBE2022` / `WBE2023` are allocated but **reserved** (OQ-1) — not emitted in v1. Frontend consumption:
[wecms-web/docs/features/settings/firm-banking-settings/api-consumption.md](../../../../../wecms-web/docs/features/settings/firm-banking-settings/api-consumption.md).

## Audit-write behaviour

| Operation | Audit row? | `WSVC_ID` | `REF_NO` / `REF_NO1` / `REF_NO2` |
|---|---|---|---|
| List / Detail / Audit-read | No | — | — |
| Create | Yes (per-locale rows, one `seq_no`) | `firm-banking-settings` | `TB_FIRM_CONF` / `out_bank_cd` / `wdrl_act_no` |
| Update | Yes (per-locale rows, one `seq_no`) | `firm-banking-settings` | `TB_FIRM_CONF` / `out_bank_cd` / `wdrl_act_no` |
| Delete | **No** (OQ-2) | — | — |
