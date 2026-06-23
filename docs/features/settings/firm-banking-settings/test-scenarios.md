# Firm Banking Settings — Test Scenarios (Backend)

SIT/UAT scenarios + the existing JUnit coverage map. Frontend scenarios:
[wecms-web/docs/features/settings/firm-banking-settings/test-scenarios.md](../../../../../wecms-web/docs/features/settings/firm-banking-settings/test-scenarios.md).

---

## 1. SIT / UAT scenarios

### List

| # | Scenario | Expected |
|---|---|---|
| L1 | List for a tenant with bindings | rows ordered `OUT_BANK_CD ASC, WDRL_ACT_NO ASC`; `bank_nm` / `ccy_cd` / `bal` populated from the joins |
| L2 | Verify list wire | `wdrl_act_no` ships **cleartext** (the PK component for write flows); display masking is applied frontend-side, not by the backend |
| L3 | List for a tenant with no bindings | `{ "list": [] }` |
| L4 | Cross-tenant isolation | only the JWT-tenant's rows appear; another tenant's bindings never leak |

### Detail

| # | Scenario | Expected |
|---|---|---|
| D1 | Detail by valid PK | flat `FirmBankingSettingDetail`; `wdrl_act_no` **cleartext** (edit round-trip) |
| D2 | Detail for unknown PK | `WBE2020` |
| D3 | Detail for another tenant's PK | `WBE2020` (indistinguishable from missing — intentional) |
| D4 | Blank / bad path component | `WBE_VALIDATION` |

### Audit log

| # | Scenario | Expected |
|---|---|---|
| AU1 | Audit for a row with Create + Update history | rows latest-first, locale-matched to the caller |
| AU2 | Audit for a row with no history (or unknown PK) | `{ "list": [] }` (no existence pre-check) |
| AU3 | Verify slug filter | only rows with `WSVC_ID='firm-banking-settings'` returned (legacy `comm_0220_001` rows not seen) |

### Create

| # | Scenario | Expected |
|---|---|---|
| C1 | Valid create (`970424` Shinhan) | 200; row persisted; `file_type='SHB'`, `bat_tran_branch_div='Y'`, `bat_tran_branch_limit=2000`; Create audit rows written |
| C2 | Valid create (`970436` VCB / `970457` Woori) | 200; `file_type='VCB'` / `'WRB'` |
| C3 | `out_bank_cd` outside the 3-bank set | `WBE0001` (V8) |
| C4 | Bank not in `TB_FIRM_BANK_CODE` | `WBE0002` (V10) |
| C5 | Duplicate composite PK | `WBE2021` (V12) |
| C6 | Missing required field / bad `org_stat` / bad pattern | `WBE_VALIDATION` |
| C7 | Client attempts to send `file_type` | ignored — server derives it (not on the request shape) |

### Update

| # | Scenario | Expected |
|---|---|---|
| U1 | Valid update of `bank_prtn_id` / `van_org_cd` / `org_stat` | 200; `mod_dttm` / `last_edtr` stamped; Update audit rows written |
| U2 | Update unknown PK | `WBE2020` |
| U3 | Verify PK + `file_type` unchanged | path PK and `file_type` are immutable across the update |
| U4 | Bad `org_stat` (e.g. `9`) | `WBE_VALIDATION` |

### Delete

| # | Scenario | Expected |
|---|---|---|
| DEL1 | Delete an existing binding | 200, `data: null`; row physically gone (hard delete) |
| DEL2 | Delete unknown PK | `WBE2020` |
| DEL3 | Verify no audit row | **no** `AUDIT_LOGS_INFM` row written for the delete (OQ-2) |
| DEL4 | Delete another tenant's PK | `WBE2020` |

### Authorization

| # | Scenario | Expected |
|---|---|---|
| A1 | User with `settings-firm-banking-settings` | all operations succeed |
| A2 | Authenticated user without the grant | 403 `WBE1003` |
| A3 | No / invalid JWT | 401 `WBE1009` |

### Download / Upload

N/A — Firm Banking Settings has **no** Excel export/import or file download endpoint. (The frontend
panel's two Automation toggles belong to the separate Environment Settings feature.)

### Error handling

| # | Scenario | Expected |
|---|---|---|
| E1 | Missing tenant context | `WBE9999` |
| E2 | Bean-validation failure | `WBE_VALIDATION` envelope, lowercase snake_case |

---

## 2. Existing JUnit coverage map

### `FirmBankingSettingsServiceTest`

Covers: tenant scoping via `TenantContext`; `list_passesTenantBizKeyToRepo_andShipsCleartextWdrlActNoAsThePkComponent`
(the list ships the **cleartext** PK on the wire — masking is the frontend mapper's job, not the service's);
`wdrl_act_no` cleartext on detail; missing-row `WBE2020` on
detail; the audit read filter shape (`REF_NO='TB_FIRM_CONF'`, `WSVC_ID='firm-banking-settings'`,
`LANG_CD` from context); blank/missing path components → `WBE_VALIDATION`. Mocks
`FirmBankingSettingRepository`, `FirmBankingSettingViewRepository`, `AuditLogEntryRepository`,
`AuditLogService`, `TbFirmBankCodeRepository`; sets/clears `TenantContext` in setUp/tearDown.

### `FirmBankingSettingsControllerTest`

Standalone MockMvc slice (SNAKE_CASE converter + `GlobalExceptionHandler`). Covers: the snake_case wire
contract end-to-end; `BusinessException → ApiResponse` envelope funnel; path-variable validation; service
stub interactions per endpoint.

---

## 3. Coverage notes

- The Create bank-scope (`WBE0001`) and referential (`WBE0002`) guards plus the duplicate-PK (`WBE2021`)
  guard should be asserted at the service level — add explicit cases if not already present.
- Delete's "no audit row" invariant (OQ-2) is a behavioural contract worth an explicit assertion (verify
  `AuditLogService` is **never** invoked on the delete path).
- Controller tests assert the lowercase snake_case wire shape and the `BusinessException → status.code`
  envelope mapping; `@Pattern` path guards are validated at the `@Validated` layer (see the controller
  test comment about MockMvc standalone vs the validation post-processor).
