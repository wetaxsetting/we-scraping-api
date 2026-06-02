# Settings > Firm Banking Settings — Refactor Feature Spec

**Module:** Settings
**Menu:** Firm Banking Settings
**Refactor screen ID / slug:** `firm-banking-settings`
**Frontend placement:** Settings modal panel (same pattern as `firm-banking-history`).
**JEX service IDs:** `comm_0220_001` (list view), `comm_0220_002` (create dialog), `comm_0220_003` (detail/edit dialog) plus the API services `comm_0220_001_r001`, `comm_0220_001_d001`, `comm_0220_002_c001`, `comm_0220_003_r001`, `comm_0220_003_u001`.
**JEX functional reference:** `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0220_*_view.jsp`, action JSPs under `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/api/comm/`, WSVC XMLs under `WECMS DEV/WECMS_ETC/xml/service/WSVC/`, IDO XMLs under `WECMS DEV/WECMS_ETC/xml/service/IDO/`, entity `WECMS DEV/WECMS/src/wabooks/entity/TbFirmConf.java`, DAO `WECMS DEV/WECMS/src/wabooks/dao/TbFirmConfDAO.java`.
**Phase:** 2 + 3 (Path A) — backend + frontend slices **FULLY IMPLEMENTED 2026-05-09**. **Delete slice IMPLEMENTED 2026-05-09**. **Update slice IMPLEMENTED 2026-05-09**. **Create slice IMPLEMENTED 2026-05-09** under **OQ-8a Option (c) — provisional / bounded-bank set** (see §13 + §5.4); Mr. Jung may override later. **Withdrawal Account autocomplete** wired 2026-05-09 to mirror legacy JEX `comm_1004_001` — V68 adds `NAPAS_BANK_INFO`, `GET /api/v1/banking/accounts/by-bank` does the NAPAS→internal `BANK_CD` bridge, `NewFirmBankingDialog` becomes a searchable autocomplete (loads per bank-change, client-side filter, raw `acct_no` submitted, masked + currency-suffixed display). List / Detail / Audit-read / Create / Update / Delete endpoints wired end-to-end with shared `AuditLogService` for write events.

### Phase 2 implementation references (read-only slice — Path A)

| Layer | File |
|---|---|
| Flyway — source table | [V62__firm_banking_settings_source_table.sql](../../../src/main/resources/db/migration/V62__firm_banking_settings_source_table.sql) |
| Flyway — audit-log prerequisite | [V63__audit_logs_infm_prerequisite.sql](../../../src/main/resources/db/migration/V63__audit_logs_infm_prerequisite.sql) |
| Flyway — list view | [V64__firm_banking_settings_view.sql](../../../src/main/resources/db/migration/V64__firm_banking_settings_view.sql) |
| Flyway — demo seed | [V65__firm_banking_settings_demo_seed.sql](../../../src/main/resources/db/migration/V65__firm_banking_settings_demo_seed.sql) |
| Entity (write — Detail) | [FirmBankingSetting.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSetting.java) + [FirmBankingSettingId.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSettingId.java) |
| Entity (read-only view — List) | [FirmBankingSettingView.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/FirmBankingSettingView.java) (`@Immutable`) |
| Shared audit-log entity | [AuditLogEntry.java](../../../src/main/java/com/kosign/wecms/common/auditlog/entity/AuditLogEntry.java) + [AuditLogEntryId.java](../../../src/main/java/com/kosign/wecms/common/auditlog/entity/AuditLogEntryId.java) |
| Repositories | [FirmBankingSettingRepository.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/repository/FirmBankingSettingRepository.java), [FirmBankingSettingViewRepository.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/repository/FirmBankingSettingViewRepository.java), [AuditLogEntryRepository.java](../../../src/main/java/com/kosign/wecms/common/auditlog/repository/AuditLogEntryRepository.java) |
| DTOs | [FirmBankingSettingRow](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingRow.java), [FirmBankingSettingListResponse](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingListResponse.java), [FirmBankingSettingDetail](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingDetail.java), [FirmBankingAuditEntry](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingAuditEntry.java), [FirmBankingAuditListResponse](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingAuditListResponse.java) |
| Service | [FirmBankingSettingsService.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsService.java) |
| Controller | [FirmBankingSettingsController.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java) |
| Shared `LogMask.acct(...)` | [LogMask.java](../../../src/main/java/com/kosign/wecms/common/security/LogMask.java) (P-2) |
| Service tests | [FirmBankingSettingsServiceTest.java](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankingsettings/service/FirmBankingSettingsServiceTest.java) |
| Controller tests | [FirmBankingSettingsControllerTest.java](../../../src/test/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsControllerTest.java) |
| LogMask tests | [LogMaskTest.java](../../../src/test/java/com/kosign/wecms/common/security/LogMaskTest.java) |
| Error code messages | [messages.properties](../../../src/main/resources/messages.properties) + [messages_en.properties](../../../src/main/resources/messages_en.properties) — `WBE0001`, `WBE0002`, `WBE2020`, `WBE2021` added |
| Create DTO (added 2026-05-09) | [FirmBankingSettingCreateRequest.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/dto/FirmBankingSettingCreateRequest.java) — five required wire fields; no `file_type` |
| TB_FIRM_BANK_CODE entity + repository (added 2026-05-09) | [TbFirmBankCode.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/entity/TbFirmBankCode.java) + [TbFirmBankCodeRepository.java](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/repository/TbFirmBankCodeRepository.java) — `existsByAntDivCdAndBankCd(antDivCd, bankCd)` for V10 referential check |
| **Withdrawal Account autocomplete — Flyway** (added 2026-05-09) | [V68__napas_bank_info_for_firm_banking_lookup.sql](../../../src/main/resources/db/migration/V68__napas_bank_info_for_firm_banking_lookup.sql) — `NAPAS_BANK_INFO` dictionary + 3-bank seed (NAPAS code → internal `BANK_CD`) |
| **Withdrawal Account autocomplete — entity / repo** | [NapasBankInfo.java](../../../src/main/java/com/kosign/wecms/domain/banking/entity/NapasBankInfo.java) + [NapasBankInfoRepository.java](../../../src/main/java/com/kosign/wecms/domain/banking/repository/NapasBankInfoRepository.java) |
| **Withdrawal Account autocomplete — DTO + service + endpoint** | [AccountLookupRow.java](../../../src/main/java/com/kosign/wecms/domain/banking/dto/AccountLookupRow.java) + [AccountLookupListResponse.java](../../../src/main/java/com/kosign/wecms/domain/banking/dto/AccountLookupListResponse.java) — `AccountService.lookupForFirmBanking()` + `GET /api/v1/banking/accounts/by-bank?out_bank_cd={NAPAS}` |
| **Withdrawal Account autocomplete — backend tests** | [AccountServiceLookupTest.java](../../../src/test/java/com/kosign/wecms/domain/banking/service/AccountServiceLookupTest.java) — NAPAS resolution, masking, empty list / unknown / null `bank_cd` paths, validation, tenant guard |
| **Withdrawal Account autocomplete — frontend** | `ApiAccountLookupRow` / `ApiAccountLookupListResponse` in [lib/types/api.ts](../../../../wecms-web/lib/types/api.ts) + `fetchAccountsByBank()` in [lib/api/banking.ts](../../../../wecms-web/lib/api/banking.ts) + `AccountLookupOption` type + `mapAccountLookupOption` in [lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts) |
| Frontend wire type (added 2026-05-09) | `ApiFirmBankingSettingCreateRequest` in [lib/api/firmBankingSettings.ts](../../../../wecms-web/lib/api/firmBankingSettings.ts) |
| Frontend API client (added 2026-05-09) | `createFirmBankingSetting()` in [lib/api/firmBankingSettings.ts](../../../../wecms-web/lib/api/firmBankingSettings.ts) |
| New Firm Banking dialog (rewritten 2026-05-09 — searchable autocomplete) | [NewFirmBankingDialog.tsx](../../../../wecms-web/components/settings/firm-banking-settings/NewFirmBankingDialog.tsx) — bounded-bank dropdown + 2 text inputs + **dynamic Withdrawal Account autocomplete** (loads on bank-change via `fetchAccountsByBank`, client-side filtering, disabled until bank selected, raw `acct_no` submitted) + submit-gating + success/error dialogs |

### Phase 3 frontend implementation references (read-only slice — Path A)

| Layer | File |
|---|---|
| Wire types | [lib/types/api.ts](../../../../wecms-web/lib/types/api.ts) — `ApiFirmBankingSettingRow`, `ApiFirmBankingSettingListResponse`, `ApiFirmBankingSettingDetail`, `ApiFirmBankingAuditEntry`, `ApiFirmBankingAuditListResponse` |
| API client | [lib/api/firmBankingSettings.ts](../../../../wecms-web/lib/api/firmBankingSettings.ts) — `fetchFirmBankingSettings()`, `fetchFirmBankingSetting(out, wdr)`, `fetchFirmBankingSettingAudit(out, wdr)` |
| Mappers | [lib/api/mappers.ts](../../../../wecms-web/lib/api/mappers.ts) — `mapFirmBankingSettingRow`, `mapFirmBankingAuditEntry` |
| Panel types | [components/settings/firm-banking-settings/types.ts](../../../../wecms-web/components/settings/firm-banking-settings/types.ts) — extended `FirmBankingLinkedAccount` with `outBankCd` / `wdrlActNo`; added `FirmBankingAuditLogRow` |
| List wire-up | [components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx) — `useApiData(fetchFirmBankingSettings, [])` + loading / empty / mock-fallback states |
| Detail + audit wire-up | [components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx) — fetches detail + audit by composite PK, replaces static rows with live values, replaces static `AUDIT_LOG_ROWS` with mapped wire entries |
| Mock data (offline-dev only) | `mockData.ts` (removed Sprint 1 — Needs Confirmation if still referenced) — retained as offline / failed-fetch fallback; shape mirrors live mapper output |
| i18n — loading / empty | [locales/en.json](../../../../wecms-web/locales/en.json), [locales/ko.json](../../../../wecms-web/locales/ko.json), [locales/vi.json](../../../../wecms-web/locales/vi.json) — `…list.loading`, `…list.empty`, `…audit_logs.loading`, `…audit_logs.empty` |

---

## 1. Overview

Firm Banking Settings is the screen where a tenant administrator registers and maintains the **firm-side bank channel bindings** that WeCMS uses to issue outbound batch transfers (firm banking) on behalf of the tenant.

Each row in `TB_FIRM_CONF` represents one (firm bank, withdrawal account) pair: which outbound bank channel (Shinhan / Woori VAN), under which partner ID, against which company account WeCMS may dispatch firm-banking instructions. The screen is CRUD: list registered bindings, create a new binding, view detail (with audit trail), edit a binding, and delete a binding. Two toggles on the same panel manage tenant-level automation environment flags (`00078` Auto Transfer, `00079` Auto Match) — these are **out of scope for the firm-banking-settings refactor** because they target a separate environment-settings service, not `TB_FIRM_CONF` (see §10).

The refactor target is the **Spring Boot 3.5 + Next.js stack** in `wecms-api/` and `wecms-web/`. The Next.js side already ships a static-UI Settings modal panel under [components/settings/firm-banking-settings/](../../../../wecms-web/components/settings/firm-banking-settings/) — this Phase 1 spec defines the API contract and data model that those panels will be wired against in Phase 3.

> R&D scope (per memory `project_refactoring_scope.md`): localhost + GitLab only. No bank integration, no DEV/UAT/PROD deploy, no coexistence with the live JEX firm-banking dispatcher. The refactor exposes the CRUD endpoints; actual dispatch to Shinhan / Woori VAN stays on the JEX live system in this phase.

---

## 2. JEX baseline — operations and source-of-truth files

| # | Operation | Service ID | Action JSP | WSVC XML | IDO used | Notes |
|---|---|---|---|---|---|---|
| 1 | List | `comm_0220_001_r001` | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/api/comm/comm_0220_001_r001_act.jsp` | `WSVC.comm_0220_001_r001.xml` | `TB_FIRM_CONF_R004` (custom) | LEFT JOIN to `ACCT_MSTR` for `CCY_CD`, `BAL`, `EXPI_DT`. No client filters — returns every row for the current `BIZ_KEY`. |
| 2 | Detail (read by PK + audit log) | `comm_0220_003_r001` | `comm_0220_003_r001_act.jsp` | `WSVC.comm_0220_003_r001.xml` | `TB_FIRM_CONF_R001` (default) + `AUDIT_LOGS_INFM` | Returns the row plus a `REC` of audit entries filtered by `(BIZ_KEY, REF_NO='TB_FIRM_CONF', REF_NO1=OUT_BANK_CD, REF_NO2=WDRL_ACT_NO, WSVC_ID='comm_0220_001', LANG_CD)`. |
| 3 | Create | `comm_0220_002_c001` | `comm_0220_002_c001_act.jsp` | `WSVC.comm_0220_002_c001.xml` | `TB_FIRM_CONF_C001`, `NAPAS_BANK_INFO_R001`, `TB_FIRM_BANK_CODE_R001`, `AUDIT_LOGS_INFM_C001` | Validates required fields, ensures (PK) doesn't already exist, validates the bank code in NAPAS + TB_FIRM_BANK_CODE, sets `FILE_TYPE` from `OUT_BANK_CD`, inserts row, writes 3 audit log entries (EN / KO / local lang). |
| 4 | Update | `comm_0220_003_u001` | `comm_0220_003_u001_act.jsp` | `WSVC.comm_0220_003_u001.xml` | `TB_FIRM_CONF_U001`, `AUDIT_LOGS_INFM_C001` | Validates required fields, loads existing row by PK, copies form fields onto it, sets `LAST_EDTR`, updates, writes 3 audit log entries. |
| 5 | Delete | `comm_0220_001_d001` | `comm_0220_001_d001_act.jsp` | `WSVC.comm_0220_001_d001.xml` | `TB_FIRM_CONF_D001` | Validates PK fields, loads existing row (raises if missing), hard-deletes by PK. **No soft-delete column** — `TB_FIRM_CONF` has no `DEL_YN`. **No audit log written** for delete in JEX (see §13 risk). |

> Bank-test-connection, save-as-draft, and approval-line submit operations **do not exist** in the JEX firm-banking-settings flow. Confirmed by inspecting all five action JSPs and all five WSVC XMLs above.

---

## 3. Operations matrix (refactor target)

Each operation is enumerated with its refactor HTTP verb / URI, JEX source binding, and the required transitions.

| # | Operation | HTTP | URI | Auth | Tenant scope | Maps to JEX | Phase-2 backend | Phase-3 frontend |
|---|---|---|---|---|---|---|---|---|
| 1 | List | GET | `/api/v1/settings/firm-banking-settings` | JWT | implicit `BIZ_KEY` from `TenantContext` | `comm_0220_001_r001` | ✅ **IMPLEMENTED** ([FirmBankingSettingsController.list()](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java#L52)) | ✅ **IMPLEMENTED** ([FirmBankingSettingsPanel.tsx](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx) via [fetchFirmBankingSettings](../../../../wecms-web/lib/api/firmBankingSettings.ts)) |
| 2 | Detail | GET | `/api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` | JWT | implicit | `comm_0220_003_r001` (entity row only — see §6 for the audit sub-resource) | ✅ **IMPLEMENTED** ([FirmBankingSettingsController.detail()](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java#L62)) | ✅ **IMPLEMENTED** ([FirmBankingDetailsDialog.tsx](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx) via [fetchFirmBankingSetting](../../../../wecms-web/lib/api/firmBankingSettings.ts)) |
| 2b | Detail audit log | GET | `/api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}/audit` | JWT | implicit | `comm_0220_003_r001` REC sub-result | ✅ **IMPLEMENTED** ([FirmBankingSettingsController.audit()](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java#L80)) | ✅ **IMPLEMENTED** ([FirmBankingDetailsDialog.tsx](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx) via [fetchFirmBankingSettingAudit](../../../../wecms-web/lib/api/firmBankingSettings.ts)) |
| 3 | Create | POST | `/api/v1/settings/firm-banking-settings` | JWT | implicit | `comm_0220_002_c001` | ✅ **IMPLEMENTED 2026-05-09** under OQ-8a Option (c) ([FirmBankingSettingsController.create()](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java)) — bounded-bank set + server-derived `file_type`; appends 4 locale audit rows under one seq_no via shared `AuditLogService` | ✅ **IMPLEMENTED 2026-05-09** ([NewFirmBankingDialog.tsx](../../../../wecms-web/components/settings/firm-banking-settings/NewFirmBankingDialog.tsx) via [createFirmBankingSetting](../../../../wecms-web/lib/api/firmBankingSettings.ts)) — bank dropdown limited to Shinhan / Vietcombank / Woori; submit gated on schema validation; refetch list on success |
| 4 | Update | PUT | `/api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` | JWT | implicit | `comm_0220_003_u001` | ✅ **IMPLEMENTED 2026-05-09** ([FirmBankingSettingsController.update()](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java)) — appends 4 locale audit rows under one seq_no via shared `AuditLogService` | ✅ **IMPLEMENTED 2026-05-09** ([EditFirmBankingDetailsDialog.tsx](../../../../wecms-web/components/settings/firm-banking-settings/EditFirmBankingDetailsDialog.tsx) via [updateFirmBankingSetting](../../../../wecms-web/lib/api/firmBankingSettings.ts)) — status-toggle modal pattern, Save enabled on data-ready (parity-clarified 2026-05-09) |
| 5 | Delete | DELETE | `/api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` | JWT | implicit | `comm_0220_001_d001` | ✅ **IMPLEMENTED 2026-05-09** ([FirmBankingSettingsController.delete()](../../../src/main/java/com/kosign/wecms/domain/setting/firmbankingsettings/controller/FirmBankingSettingsController.java)). **No AuditLogService dependency** (OQ-2 — JEX writes no audit on delete; correction to earlier "pending P-1" note). | ✅ **IMPLEMENTED 2026-05-09** ([FirmBankingSettingsPanel.openDeleteConfirm](../../../../wecms-web/components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx) via [deleteFirmBankingSetting](../../../../wecms-web/lib/api/firmBankingSettings.ts)) |

**Not in scope for this feature:**
- Save-as-draft — no JEX equivalent; the dialogs commit on Submit.
- Approval-line submit — no JEX equivalent; firm banking changes are immediate in JEX. (See §13.)
- Bank-test-connection — no JEX equivalent.
- Auto-transfer / Auto-match toggles — separate environment-settings endpoint (see §10).

---

## 3.1 Naming conventions — slug vs. legacy WSVC id

The refactor uses **slug-based identifiers** everywhere a new identifier is introduced. Legacy JEX WSVC ids (`comm_0220_001`, `fnsh_0001_001`, …) are JSP-architecture artifacts and **must not** be used as refactor identifiers outside the three explicitly approved buckets below.

**Canonical refactor slug for this feature: `firm-banking-settings`.** Use this slug — not `comm_0220_001` — wherever the refactor stack creates a new name.

| Surface | Refactor identifier | Legacy WSVC id |
|---|---|---|
| Frontend route / mount | `/settings/firm-banking-settings` | ❌ never use `comm_0220_001` here |
| Spring REST URI | `/api/v1/settings/firm-banking-settings/...` | ❌ never |
| Java package | `com.kosign.wecms.domain.setting.firmbankingsettings` | ❌ never |
| Java class names | `FirmBankingSettingsController`, `FirmBankingSettingsService`, `FirmBankingSetting` | ❌ never (no `Comm0220Service`, etc.) |
| i18n message-key prefix | `audit.firm_banking_settings.*`, `settings.firm_banking_settings.*` | ❌ never |
| Frontend component folder | `wecms-web/components/settings/firm-banking-settings/` | ❌ never |
| Wire-type prefix | `ApiFirmBankingSetting*` (in `lib/types/api.ts`) | ❌ never |
| API client filename | `lib/api/firmBankingSettings.ts` | ❌ never |
| Log / trace tags | `firm-banking-settings` | ❌ never |

Legacy WSVC ids are preserved **only** in two buckets going forward (as of the OQ-6 override 2026-05-09):

1. **JEX file:line citations** — every reference to `*_act.jsp`, `WSVC.*.xml`, `IDO.*.xml`, etc. as the source-of-truth pointer for parity verification (e.g. `comm_0220_002_c001_act.jsp:84-88`).
2. **Migration traceability** — narrative paragraphs in [docs/refactor-wecms/migration/firm-banking-settings-mapping.md](../../references/migration/firm-banking-settings-mapping.md) that explain "this refactor service replaces JEX `comm_0220_001`."

> **Bucket retired 2026-05-09 — Audit DB constant for cross-system parity.** Earlier revisions reserved a third bucket for `AUDIT_LOGS_INFM.WSVC_ID` to remain `'comm_0220_001'` so the legacy JEX detail-audit query at `comm_0220_003_r001_act.jsp:74-86` would still find refactor-written rows. That decision (OQ-6 default) was overridden 2026-05-09: refactor-written audit rows now carry `wsvc_id = 'firm-banking-settings'`. The legacy JEX query no longer returns refactor rows; this is intentional — refactor is local-only R&D and JEX never queries the local DB. A future feature with genuine cross-system audit-history needs would need a fresh PO decision before reviving this bucket. See §13 OQ-6 + §11.1 *Identifier separation* + V67 migration.

If you find yourself reaching for `comm_0220_001` outside file:line citations or migration-traceability prose, **stop** — that's a bug. Use `firm-banking-settings` instead, including for `audit_logs_infm.wsvc_id`.

## 4. Target architecture

### 4.1 Backend (Spring Boot)

New domain package: `com.kosign.wecms.domain.setting.firmbankingsettings`. Existing patterns from `domain/setting/firmbankinghistory/` (controller → service → repository → entity + dto, records for DTOs, derived JPA queries) are the model.

```
com.kosign.wecms.domain.setting.firmbankingsettings
├── controller/
│   └── FirmBankingSettingsController.java       — @RequestMapping("/api/v1/settings/firm-banking-settings")
├── service/
│   ├── FirmBankingSettingsService.java          — list / detail / create / update / delete
│   └── FirmBankingAuditService.java             — appends three-language audit rows; @Transactional REQUIRES_NEW
├── repository/
│   ├── FirmBankingSettingRepository.java        — JpaRepository<FirmBankingSetting, FirmBankingSettingId>
│   └── FirmBankingSettingViewRepository.java    — JpaRepository<FirmBankingSettingView, FirmBankingSettingId> (read-only join projection)
├── entity/
│   ├── FirmBankingSetting.java                  — @Table(name="TB_FIRM_CONF"), @IdClass(FirmBankingSettingId)
│   ├── FirmBankingSettingId.java                — Serializable composite key (bizKey, outBankCd, wdrlActNo)
│   └── FirmBankingSettingView.java              — @Immutable @Table(name="V_FIRM_BANKING_SETTING") for the list join
└── dto/
    ├── FirmBankingSettingRow.java               — list row record (incl. CCY_CD / BAL / EXPI_DT from ACCT_MSTR)
    ├── FirmBankingSettingDetail.java            — detail record (no join columns)
    ├── FirmBankingSettingListResponse.java      — { list }
    ├── FirmBankingSettingCreateRequest.java     — record with validation
    ├── FirmBankingSettingUpdateRequest.java     — record with validation
    └── FirmBankingAuditEntry.java               — audit log row
```

Cross-cutting reuse — **must consume the existing shared components**, not re-implement:

| Concern | Reuse |
|---|---|
| Tenant scoping | `TenantContext.currentBizKey()` — every repo method constrains on `BIZ_KEY`. |
| Auth / JWT | `JwtAuthenticationFilter` + `SecurityConfig`. The `/api/v1/settings/**` matcher already requires JWT. |
| Locale resolution | `LangCd` enum + `MessageResolver` (`Accept-Language` → `LangCd`). |
| Error envelope | `ApiResponse<T>` + `BusinessException(code, args…)` + `GlobalExceptionHandler`. **Do not** introduce a parallel envelope. |
| Audit base columns | `BaseEntity` (`REG_ID` / `REG_DTM` / `MOD_ID` / `MOD_DTM`) — see §7.1 for the entity-vs-DEV note. |
| Audit log writer | `domain/common/auditlog/AuditLogService` (to be added — see §11). The append must run in `@Transactional(REQUIRES_NEW)` so an audit failure does not roll back the business transaction once the write has committed; conversely, business failure must roll back the audit insert that was made within the same transaction. |
| Wire format | Lowercase `snake_case` via the global `JacksonConfig` SNAKE_CASE strategy (CTO directive 2026-05-08, memory `project_api_naming_lowercase`). **No `@JsonProperty("UPPER_SNAKE")` — even though the JEX entity has them.** |

### 4.2 Frontend (Next.js)

The static-UI panels already exist at `wecms-web/components/settings/firm-banking-settings/`:

- `FirmBankingSettingsPanel.tsx` — list + automation toggles
- `FirmBankingLinkedAccounts.tsx` — bank-grouped account list
- `FirmBankingAccountItem.tsx` + `FirmBankingRowMenu.tsx` — row + dropdown
- `NewFirmBankingDialog.tsx` — create dialog
- `FirmBankingDetailsDialog.tsx` — view-mode detail dialog
- `EditFirmBankingDetailsDialog.tsx` — edit-mode dialog
- `FirmBankingAutomationSettings.tsx` — Auto Transfer / Auto Match toggles (out of scope — see §10)
- `BankLogo.tsx`, `mockData.ts`, `types.ts`

Phase 3 wiring will replace `mockData.ts` reads with calls into a new `lib/api/firmBankingSettings.ts` client and a new `lib/api/mappers.ts` `mapFirmBankingSettingRow` mapper, keeping the same component surface. No new routes — the panel is mounted inside the Settings modal (same as `firm-banking-history`).

Typography is enforced via the workspace tokens (`text-table-cell`, `text-page-title`, `text-button` etc. — root `CLAUDE.md` §Global Typography). **No ad-hoc `text-[14px]` / inline `fontFamily`** in the new code — Figma WB365 is the source of truth (memory `feedback_typography_source`). Desktop-first per memory `feedback_desktop_first_ui`.

Form-state strategy — match JEX UX, do not invent:
- **Create dialog** — pessimistic: dialog stays open until POST returns success, then closes and refetches the list.
- **Edit dialog** — pessimistic: same shape, plus prefetch via the detail GET.
- **Delete** — confirmation popup → DELETE → refetch (matches `comm_0220_001.ts:127` `successPopup` flow).
- **Dirty-check / unsaved-changes guard** — JEX has none on this screen (the dialog is the unit of work). The refactor follows JEX behavior — no guard added.

> If a Figma URL is provided for any of these dialogs, **call `mcp__claude_ai_Figma__get_design_context` + `get_screenshot` before drafting the spec for that dialog** (memory `feedback_figma_first`). No URL was provided with this Phase 1 task; component shapes are inferred from the existing static UI. Phase 3 should re-confirm against Figma before wiring.

---

## 5. API contract per operation

> All wire field names are **lowercase `snake_case`**. Java DTO fields stay camelCase; the global `JacksonConfig` SNAKE_CASE strategy converts them. **Do NOT** add `@JsonProperty("UPPER_SNAKE")` annotations — the legacy JEX entity carries them but the refactor strips them per §3 of `wecms-api/CLAUDE.md` (Refactoring rules — when porting a JEX module).

### 5.1 List — `GET /api/v1/settings/firm-banking-settings`

**Request:** no body, no query parameters in v1 (matches JEX — the JEX list returns every row for the tenant). Optional `?org_stat=` and `?out_bank_cd=` filters can be added in a later sprint without contract break — guard with `@Pattern` validation.

**Response (success):**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "biz_key":               "VN_VNM0004_000001",
        "out_bank_cd":           "970424",
        "bank_nm":               "Shinhan Bank Vietnam",
        "ccy_cd":                "VND",
        "bank_prtn_id":          "K003",
        "van_org_cd":            "300",
        "org_stat":              "1",
        "wdrl_act_no":           "700-002-123456",
        "file_type":             "SHB",
        "bat_tran_branch_div":   "Y",
        "bat_tran_branch_limit": 2000,
        "reg_dttm":              "20250226153210",
        "start_dttm":            "20250226153210",
        "end_dttm":              "20250226153210",
        "mod_dttm":              "20250227133504",
        "last_edtr":             "Sonny",
        "bal":                   125000000,
        "expi_dt":               "20991231"
      }
    ]
  }
}
```

`bank_nm` is sourced via the JOIN to `TB_FIRM_BANK_CODE` on `(ant_div_cd='NAPAS', bank_cd = out_bank_cd)`. `ccy_cd`, `bal`, `expi_dt` are sourced via the LEFT JOIN to `ACCT_MSTR` on `(biz_key, acct_no = wdrl_act_no)` (`IDO.TB_FIRM_CONF_R004.xml:42-66`).

### 5.2 Detail — `GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}`

Path components are URL-encoded. `out_bank_cd` validates against `[0-9]{1,15}` (length capped at 15 by `TB_FIRM_CONF.OUT_BANK_CD VARCHAR(15)`); `wdrl_act_no` validates against `[A-Za-z0-9\-]{1,100}`.

**Response:**

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "biz_key":               "VN_VNM0004_000001",
    "out_bank_cd":           "970424",
    "bank_prtn_id":          "K003",
    "van_org_cd":            "300",
    "org_stat":              "1",
    "wdrl_act_no":           "700-002-123456",
    "file_type":             "SHB",
    "bat_tran_branch_div":   "Y",
    "bat_tran_branch_limit": 2000,
    "reg_dttm":              "20250226153210",
    "start_dttm":            "20250226153210",
    "end_dttm":              "20250226153210",
    "mod_dttm":              "20250227133504",
    "last_edtr":             "Sonny"
  }
}
```

**Error cases:**

| Condition | Code | HTTP |
|---|---|---|
| Path segment fails `@Pattern` | `WBE_VALIDATION` | 200 |
| Row not found for `(biz_key, out_bank_cd, wdrl_act_no)` | `WBE2020` (firm-banking-setting-not-found, allocated 2026-05-09 per OQ-1) | 200 (use `BusinessException`) |

### 5.3 Detail audit log — `GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}/audit`

Returns audit-log entries for the row, filtered by the user's `LANG_CD` to mirror JEX (`comm_0220_003_r001_act.jsp:74-86`). The mismatch between detail and audit being a single response in JEX vs. two responses in REST is intentional — keeping audit on its own resource is cleaner and lets the dialog lazy-load it.

**`wsvc_id` filter value:** `'firm-banking-settings'` (refactor slug — OQ-6 override 2026-05-09). The legacy JEX query at `comm_0220_003_r001_act.jsp:74-86` filters by `'comm_0220_001'` and will not find refactor-written rows; the refactor's audit-read filters by the slug so it returns refactor-written rows but not pre-V67 legacy rows.

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "list": [
      {
        "audit_logs_actv":     "Firm Banking Details",
        "audit_logs_actv_dtl": "Update firmbanking details",
        "reg_id":              "userA",
        "reg_dtm":             "20250227133504",
        "reg_nm":              "Sonny"
      }
    ]
  }
}
```

### 5.4 Create — `POST /api/v1/settings/firm-banking-settings`

**Request:**

```json
{
  "out_bank_cd":  "970424",
  "bank_prtn_id": "K003",
  "van_org_cd":   "300",
  "org_stat":     "1",
  "wdrl_act_no":  "700-002-123456"
}
```

All five fields required (matches JEX `comm_0220_002_c001_act.jsp:38-43`). `file_type` is **not** sent by the client — server derives it from `out_bank_cd`. The JEX rule (`comm_0220_002_c001_act.jsp:123-127`) only branches on Shinhan / Woori, but the DEV verification 2026-05-09 (OQ-8) revealed an existing Vietcombank row carrying `file_type='VCB'`, inserted via a different code path. The refactor extends the mapping to include Vietcombank — final extensibility behavior pending **OQ-8a** (see §13):

| `out_bank_cd` | `file_type` | Source |
|---|---|---|
| `970424` (Shinhan) | `SHB` | JEX `comm_0220_002_c001_act.jsp:124` |
| `970436` (Vietcombank) | `VCB` | DEV evidence 2026-05-09 (1 row) — JEX action JSP does not set this branch; data inserted via another path |
| `970457` (Woori) | `WRB` | JEX `comm_0220_002_c001_act.jsp:126` |
| any other value | **PENDING OQ-8a** — see §13 | — |

`bat_tran_branch_div`, `bat_tran_branch_limit`, `reg_dttm`, `start_dttm`, `end_dttm`, `mod_dttm`, `last_edtr` are all **server-set** (matches `TbFirmConfDAO.create()` lines 64-89).

**Response (success):** standard `ApiResponse.ok(...)` with the created row in `data` (same shape as Detail in §5.2). HTTP 200, `WBI0000`.

**Error cases** (codes finalized 2026-05-09 per OQ-1):

| Condition | Code | Source rule (JEX) |
|---|---|---|
| Missing required field | `WBE_VALIDATION` (`@NotBlank` on the record) | `comm_0220_002_c001_act.jsp:38-43` |
| `out_bank_cd` outside the supported `file_type` mapping (V8) | `WBE0001` (legacy `Invalid {0}`) with arg `"OUT_BANK_CD"` | `comm_0220_002_c001_act.jsp:123-127`. Behavior pending OQ-8a — strict reject vs accept-with-client-supplied-file_type. |
| Bank not in `NAPAS_BANK_INFO` (V9) | `WBE0002` (legacy `There is no {0}`) with arg `"BANK"` | `comm_0220_002_c001_act.jsp:91-95` |
| Bank not in `TB_FIRM_BANK_CODE` for `(NAPAS, out_bank_cd)` (V10) | `WBE0002` with arg `"BANK"` | `comm_0220_002_c001_act.jsp:121` |
| PK already exists (V12) | `WBE2021` (firm-banking-setting-already-exists) | `comm_0220_002_c001_act.jsp:84-88` (JEX raises `WBE0003` "Duplicate" — refactor uses the more specific feature-level code) |

Reserved codes (not used in v1, allocated for forward use): `WBE2022` (invalid status transition), `WBE2023` (sensitive field access denied / view-secret permission). See OQ-1 in §13.

### 5.5 Update — `PUT /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}`

**Request:** `bank_prtn_id`, `van_org_cd`, `org_stat` (the three editable fields per `comm_0220_003_u001_act.jsp:32-38` and the existing `EditFirmBankingDetailsDialog.tsx`). Required-field semantics are the same as Create. `out_bank_cd` and `wdrl_act_no` come from the path; the request body MUST NOT carry them — if it does, ignore it (per common REST hygiene).

**Response:** `ApiResponse.ok(updatedRow)` with the same shape as Detail.

**Error cases:**

| Condition | Code |
|---|---|
| Missing required field | `WBE_VALIDATION` |
| Row not found for PK | `WBE2020` (firm-banking-setting-not-found, allocated 2026-05-09 per OQ-1) |

`file_type` is **not changed on update** in JEX (it's set only on create) — preserve the same behavior.

### 5.6 Delete — `DELETE /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}`

**Response:** `ApiResponse.ok(null)` on success, HTTP 200, `WBI0000`. Hard delete — `TB_FIRM_CONF` has no soft-delete column.

**Error cases:**

| Condition | Code |
|---|---|
| Path segment fails `@Pattern` | `WBE_VALIDATION` |
| Row not found for PK | `WBE2020` (matches JEX behaviour at `comm_0220_001_d001_act.jsp:66-71` — `ValidationHelper.validateTbFirmConf` throws if absent) |

**Audit on delete** — JEX **does not** write an audit log for delete (the action JSP at `comm_0220_001_d001_act.jsp:1-77` has no `AuditLogsInfm` write; compare with create/update which write three rows each). The refactor **preserves this behavior** — Delete is a non-audited operation. **Decision (OQ-2 resolved 2026-05-09):** keep legacy parity to minimize behavioral divergence during the refactor phase. If forensic-trail-on-delete is later required, it is a forward feature, not a parity item.

---

## 6. Validation rules (field-level + business-level)

| ID | Rule | Field-level / Business-level | Source — JEX file:line |
|---|---|---|---|
| V1 | `out_bank_cd` required | field | `comm_0220_002_c001_act.jsp:38`, `comm_0220_001_d001_act.jsp:24`, `comm_0220_003_u001_act.jsp:33`, `comm_0220_003_r001_act.jsp:31` |
| V2 | `wdrl_act_no` required | field | `comm_0220_002_c001_act.jsp:42`, `comm_0220_001_d001_act.jsp:25`, `comm_0220_003_u001_act.jsp:37`, `comm_0220_003_r001_act.jsp:32` |
| V3 | `bank_prtn_id` required | field (Create / Update) | `comm_0220_002_c001_act.jsp:39`, `comm_0220_003_u001_act.jsp:34` |
| V4 | `van_org_cd` required | field (Create / Update) | `comm_0220_002_c001_act.jsp:40`, `comm_0220_003_u001_act.jsp:35` |
| V5 | `org_stat` required | field (Create / Update) | `comm_0220_002_c001_act.jsp:41`, `comm_0220_003_u001_act.jsp:36` |
| V6 | `org_stat ∈ {WAITING(0), AVAILABLE(1), SUSPENDED(2), TERMINATED(3)}` | field — enum | `WECMS DEV/WECMS/src/wabooks/enums/OrgStat.java:7-10` |
| V7 | `out_bank_cd` length ≤ 15 | field | `TB_FIRM_CONF.OUT_BANK_CD VARCHAR(15)` (DEV `information_schema.columns`) + `WSVC.comm_0220_001_r001.xml:39` (`length="20"` is the wire envelope cap; DB is 15) |
| V8 | `out_bank_cd ∈ {SHINHAN(970424), VIETCOMBANK(970436), WOORI(970457)}` (per DEV evidence 2026-05-09 — see §5.4 mapping). Strict reject vs accept-with-client-supplied-`file_type` for codes outside this set is **pending OQ-8a**. | business — derived/enum | `WECMS DEV/WECMS/src/wabooks/enums/OutBankCd.java:7-9` (legacy enum has only Shinhan/Woori — out of date with DEV) + `comm_0220_002_c001_act.jsp:123-127` (only sets `file_type` for those two) + DEV table check 2026-05-09 confirming a Vietcombank row carrying `file_type='VCB'` |
| V9 | `out_bank_cd` must exist in `NAPAS_BANK_INFO` (`napas_bank_cd`) | business — referential | `comm_0220_002_c001_act.jsp:91-95` |
| V10 | `out_bank_cd` must exist in `TB_FIRM_BANK_CODE` for `(ant_div_cd=NAPAS, bank_cd=out_bank_cd)` | business — referential | `comm_0220_002_c001_act.jsp:116-121` |
| V11 | ~~`wdrl_act_no` must exist in `ACCT_MSTR` for the tenant + the resolved `bank_cd` from V9~~ — **NOT enforced** (legacy parity, OQ-3 resolved 2026-05-09). The check is commented out in JEX behind issue [#42004](https://www.kosign.com.kh/redmine/issues/42004); the refactor preserves the disabled state. | business — referential (intentionally disabled) | `comm_0220_002_c001_act.jsp:99-108` (commented out) |
| V12 | PK `(biz_key, out_bank_cd, wdrl_act_no)` must not already exist on Create | business — uniqueness | `comm_0220_002_c001_act.jsp:84-88` |
| V13 | PK `(biz_key, out_bank_cd, wdrl_act_no)` must exist on Update / Delete / Detail | business — existence | `comm_0220_001_d001_act.jsp:66`, `comm_0220_003_u001_act.jsp:79` (`ValidationHelper.validateTbFirmConf`) |
| V14 | `file_type` is server-derived from `out_bank_cd` (Create only) per the §5.4 mapping table (Shinhan→SHB, Vietcombank→VCB, Woori→WRB). Behavior for unmapped codes pending OQ-8a. | business — derived field | `comm_0220_002_c001_act.jsp:123-127` + DEV verification 2026-05-09 |
| V15 | `bat_tran_branch_div` defaults to `'Y'` if absent on Create | business — default | `TbFirmConfDAO.java:70-72` |
| V16 | `bat_tran_branch_limit` defaults to `2000` if absent on Create | business — default | `TbFirmConfDAO.java:74-77` |
| V17 | `org_stat` defaults to `AVAILABLE('1')` if absent on Create | business — default | `TbFirmConfDAO.java:64-67` (NOTE: DB column default is `'0'` per DEV `information_schema.columns` — DAO override wins) |
| V18 | `reg_dttm`, `start_dttm`, `mod_dttm`, `end_dttm` are server-set on Create | business — server timestamps | `TbFirmConfDAO.java:79-89` |
| V19 | `last_edtr` set to current user's `name` on Create + Update | business — server-set audit | `comm_0220_002_c001_act.jsp:130`, `comm_0220_003_u001_act.jsp:81` |
| V20 | `wdrl_act_no` length ≤ 100 | field | `TB_FIRM_CONF.WDRL_ACT_NO VARCHAR(100)` |
| V21 | `bank_prtn_id` length ≤ 20, `van_org_cd` length ≤ 20 | field | `TB_FIRM_CONF` DEV column lengths |
| V22 | Status transitions are unrestricted | business — none enforced | No FSM logic in `comm_0220_003_u001_act.jsp` — any `org_stat` may move to any other. Refactor preserves this. |

---

## 7. Database

### 7.1 Tables touched (read or write)

| Table | Role | Mode |
|---|---|---|
| `TB_FIRM_CONF` | Owning table — primary CRUD target | RW |
| `TB_FIRM_BANK_CODE` | Bank registry — JOIN source for `bank_nm`; existence check | R |
| `NAPAS_BANK_INFO` | Napas bank registry — existence check on `out_bank_cd` | R |
| `ACCT_MSTR` | Tenant account ledger — JOIN source for `ccy_cd`, `bal`, `expi_dt`; existence check (V11, currently disabled) | R |
| `AUDIT_LOGS_INFM` | Append-only audit log | W |

### 7.2 Schema parity with JEX DEV

`TB_FIRM_CONF` already exists in JEX DEV — see [docs/refactor-wecms/database/settings/firm-banking-settings-schema.md](../../references/database/settings/firm-banking-settings-schema.md) for the full column-for-column DDL extracted from DEV's `information_schema.columns`.

Per the **Legacy Schema Parity Rule** in `wecms-api/CLAUDE.md`, the local Flyway migration must mirror DEV exactly — column names, types, lengths, nullability, defaults, PK. The **JPA entity may map only the columns the refactor reads/writes** (Sprint subset; document in entity Javadoc).

### 7.3 New columns / indexes / migrations

No new columns required for the firm-banking-settings refactor itself — `TB_FIRM_CONF`, `TB_FIRM_BANK_CODE`, `NAPAS_BANK_INFO`, `ACCT_MSTR`, `AUDIT_LOGS_INFM` are all pre-existing in DEV.

Planned **Flyway migrations** (numbers tentative — assigned at implementation time, monotonic from current head V42):

| Version (tentative) | Purpose |
|---|---|
| `V{N}__firm_banking_settings_source_tables.sql` | `TB_FIRM_CONF` + `TB_FIRM_BANK_CODE` + `NAPAS_BANK_INFO` (only if not yet ported — verify against current local schema). Column-for-column from DEV. PK + indexes. |
| `V{N+1}__firm_banking_settings_view.sql` | `V_FIRM_BANKING_SETTING` view replicating `IDO.TB_FIRM_CONF_R004.xml`'s LEFT JOIN to `ACCT_MSTR` + JOIN to `TB_FIRM_BANK_CODE` for `bank_nm`. Read-only; backs `FirmBankingSettingView` entity. |
| `V{N+2}__firm_banking_settings_demo_seed.sql` | Idempotent `INSERT … ON CONFLICT DO NOTHING` for tenant `VN_VNM0004_000001`: 2 firm-conf rows (one Shinhan, one Woori) + matching bank-code + napas-bank rows where missing. |

The `AUDIT_LOGS_INFM` table is shared across the refactor and is provisioned by the audit-log feature migration (see §11). The firm-banking-settings feature does not own that schema.

### 7.4 Encryption-at-rest plan for sensitive fields

Sensitive fields in this feature:

| Field | Sensitivity | Encrypted today (DEV)? | Refactor stance |
|---|---|---|---|
| `wdrl_act_no` | Tenant company account number — **PII / financial** | **No** in DEV (`varchar(100)` cleartext) | **Plain text in Phase 2 — legacy parity.** OQ-4 resolved 2026-05-09. See §7.4.1 below for the binding rules. |
| `bank_prtn_id` | Partner ID issued by Shinhan / Woori — sensitive but not PII | No | Plain text. |
| `van_org_cd` | VAN organization code | No | Plain text. |
| `out_bank_cd`, `org_stat`, `file_type`, `bat_tran_branch_*` | Configuration / enums | No | Plain text. |

**Why parity matters here.** Both `ACCT_MSTR.acct_no` and `TB_FIRM_CONF.wdrl_act_no` are plain `varchar` in legacy DB. The list service's LEFT JOIN (`V_FIRM_BANKING_SETTING`, see [database doc §3](../../references/database/settings/firm-banking-settings-schema.md)) joins on `TFC.WDRL_ACT_NO = AM.ACCT_NO`. Encrypting only one side would break the JOIN, the V11-style existence check (if/when re-enabled), and any data ETL that copies between the two. Encryption is therefore a **project-wide migration** (all account-number columns at once), not a per-feature task.

#### 7.4.1 Implementation rules (binding for Phase 2)

- **Do NOT create feature-local encryption.** No `EncryptedString` wrapper, no `pgcrypto`-style helper local to the firm-banking-settings package.
- **Do NOT apply `@Convert(converter = …)` to `wdrl_act_no`** (or any other column on `FirmBankingSetting`) in Phase 2. The entity field stays `String` mapped to plain `VARCHAR`.
- **Mask `wdrl_act_no` in API responses by default** if and where the Phase-1 spec requires it (see §9). Masking is a **presentation rule**, applied at the DTO/response layer, never at the column level.
- **Never log the full `wdrl_act_no`.** When the field is included in log lines (DEBUG / WARN / ERROR), it must be passed through a masking helper (e.g. `LogMask.acct(wdrlActNo)` returning `***-***-1234`). Same rule for `bank_prtn_id`. The `trace_id` is the lookup key for support, not the account number.
- **Future encryption is out of scope** for this feature. When the project-wide shared encryption utility lands, a separate migration covers `ACCT_MSTR.acct_no`, `TB_FIRM_CONF.wdrl_act_no`, `USER_BUSI_CONN_INFM`, `BUSI_INFM`, and any other account-number columns in lockstep. That work updates this section and the database doc — track in the audit/encryption refactor backlog.

There is **no** project-wide `EncryptionUtility` in `wecms-api` today (verified — no class matching `*Encryption*` / `*Crypto*` exists outside `WecmsPasswordEncoder` for password hashing). Phase 2 does not introduce one.

---

## 8. Permission model

JEX-side: the WSVC for the list service (`WSVC.comm_0220_001_r001.xml:25-26`) carries:

```xml
<WTA_USER_ALLOW_YN>N</WTA_USER_ALLOW_YN>
<NORM_USER_ONLY_YN>Y</NORM_USER_ONLY_YN>
```

So **only normal (non-WTA) authenticated users** may call the list. The same constraint applies (by JEX convention) to all `comm_0220_*` services — confirm at code-port time. There is no per-feature role / permission code beyond authentication + the `BIZ_KEY` tenant scope. JEX does **not** require an explicit "FBS_ADMIN" role.

Refactor mapping to Spring Security:

| Endpoint | Auth | Role |
|---|---|---|
| All five firm-banking-settings endpoints (list, detail, audit, create, update, delete) | JWT required (`SecurityConfig` default) | Authenticated user only — no `@PreAuthorize` role check in v1, matching JEX. The tenant scope (`BIZ_KEY` from `TenantContext`) is the only data-isolation guard. |

If the JEX WTA-vs-NORM gate matters in practice (e.g. WTA admin users should be excluded), add a `@PreAuthorize("@authz.isNormalUser()")` guard against `TenantContext.currentUserType()` once that field is populated by the JWT filter — track in §13 OQ-5.

---

## 9. Encryption & masking (frontend + API)

Per **OQ-4 resolution (2026-05-09)** the column is plain text in the DB; masking is presentation-layer only. See §7.4.1 for the binding implementation rules.

**Correction (2026-05-09):** an earlier revision of this section called for the API to mask `wdrl_act_no` server-side on the list response and return cleartext only on the detail response. That broke every write flow (Delete / Update / Detail / Audit-read all use `wdrl_act_no` as a path PK; the masked value contains `*` characters that the path-validation regex `^[A-Za-z0-9-]{1,100}$` rejects). The corrected rule:

**API surface:**
- The API returns `wdrl_act_no` as **cleartext** on **every** response (list, detail, audit). It is the PK component every endpoint addresses by, and it must be passed through verbatim to subsequent API calls. Mirrors JEX `IDO.TB_FIRM_CONF_R004.xml` which also returns cleartext on the list.
- A future `@PreAuthorize`-gated unmask flow (reserved error code `WBE2023`) is still out of scope for v1; in v1 every authenticated tenant user sees cleartext PKs by virtue of the JWT auth + tenant scoping.
- Audit log entries reference `wdrl_act_no` cleartext via `REF_NO2` (legacy contract — `comm_0220_003_r001_act.jsp:74-86` filters by `REF_NO2 = wdrl_act_no`). Plain text PK — the lookup works because both sides are cleartext.
- **Never log the full `wdrl_act_no`** — every server-side log line that includes it passes through `LogMask.acct(...)` (§7.4.1). The wire returns cleartext but log lines stay redacted.

**Frontend surface:**
- The Settings panel's `FirmBankingAccountItem.tsx` renders the account number partially masked in the row body. Masking is **applied client-side** by the mapper (`mapFirmBankingSettingRow.maskAccountNumber`) — the panel's display string `accountNumber` carries the masked text plus currency suffix (e.g. `7000****5280 (VND)`).
- The mapper output preserves a separate `wdrlActNo` field that holds the **cleartext PK** from the wire — never displayed, only used as the PK in API paths (`fetchFirmBankingSetting(outBankCd, wdrlActNo)`, `deleteFirmBankingSetting(outBankCd, wdrlActNo)`, etc.).
- The create / edit dialogs use the existing `MaskedInput` component for `wdrl_act_no` entry (per task brief).
- The frontend never echoes the masked display string into an API call. This split is enforced at the type level: `FirmBankingLinkedAccount` carries both `accountNumber` (masked display) and `wdrlActNo` (cleartext PK) so that callers can't accidentally use one where the other is required.

---

## 10. Out-of-scope toggles — Auto Transfer / Auto Match

The Firm Banking Settings panel in JEX (`comm_0220_001_view.jsp` + `comm_0220_001.ts`) hosts **two environment toggles**:

| Toggle | Env code | JEX wiring |
|---|---|---|
| Auto Transfer | `00078` | `comm_0220_001.ts:33-35` (read), `:111-114` (write via `functionUpadateEnv`) |
| Auto Match | `00079` | `comm_0220_001.ts:38-40` (read), `:117-120` (write) |

These toggles target a **separate environment-settings service** (`_v2common.app.getEnvSetInfmRealTime()` reads from / `functionUpadateEnv` writes to a tenant-wide `ENV_SET_INFM` table). They are **out of scope for the firm-banking-settings feature** and belong to a separate `domain/setting/environment` (or similar) feature not yet ported.

The frontend can ship the toggle UI as static or stub — the API contract for `00078` / `00079` is **not** defined here. Mark `FirmBankingAutomationSettings.tsx` as static-UI for v1.

---

## 11. Audit & approval hooks

### 11.1 Audit log (`AUDIT_LOGS_INFM`) — shared `AuditLogService` ✅ IMPLEMENTED 2026-05-09

**P-1 prerequisite shipped 2026-05-09 (separate cross-feature MR).** The shared writer lives at [`common/auditlog/service/AuditLogService.java`](../../../src/main/java/com/kosign/wecms/common/auditlog/service/AuditLogService.java) with a four-locale fan-out (EN / KO / VI / KM) and `@Transactional(propagation = REQUIRES_NEW)` semantics per spec. Seq_no allocation runs through a small native-query repo ([`AuditLogSeqNoRepository`](../../../src/main/java/com/kosign/wecms/common/auditlog/repository/AuditLogSeqNoRepository.java)) backed by Flyway [V66](../../../src/main/resources/db/migration/V66__wb_get_next_val_audit_logs_infm.sql), which extends `wb_get_next_val` with the `audit_logs_infm` branch (mirrors DEV byte-for-byte). One seq_no per business event, reused across the per-locale row fan-out — verified against DEV `audit_logs_infm` 2026-05-09 (seq_no=343 across KO/VI/EN for one Update event). Failure handling: every exception inside `append()` is caught + logged at WARN + swallowed so a missed audit row never strands the caller's business write (Phase 1 §13 OQ-6 trade-off).

The Update slice for firm-banking-settings can now consume this writer with a single call — see §3 Operations matrix and the readiness note in [docs/refactor-wecms/audits/firm-banking-settings-parity-checklist.md](../../references/audits/firm-banking-settings-parity-checklist.md).


#### Per-operation audit behavior (legacy parity, confirmed 2026-05-09)

| Operation | JEX runtime writes audit? | Refactor writes audit? | JEX evidence |
|---|---|---|---|
| Create | **yes** — 3 rows (EN / KO / local) | yes — 4 rows (EN / KO / VI / KM) | `comm_0220_002_c001_act.jsp:138-174` |
| Update | **yes** — 3 rows (EN / KO / local) | yes — 4 rows (EN / KO / VI / KM) | `comm_0220_003_u001_act.jsp:88-123` |
| Delete / Deactivate | **no** | **no** (legacy parity, OQ-2 resolved + confirmed 2026-05-09) | `comm_0220_001_d001_act.jsp` — no `AuditLogsInfm` write anywhere in the action JSP |
| List / Detail / Audit-read | no (read-only) | no (read-only) | n/a |

The reasoning in JEX is that `AUDIT_LOGS_INFM` rows are pre-localized (English / Korean / Vietnamese / Khmer) so that the detail dialog can render the audit list directly in the user's locale (`comm_0220_003_r001_act.jsp:74-86` filters by `LANG_CD`).

#### Identifier separation — `wsvc_id` is a DB constant, not a route (clarified 2026-05-09)

`wsvc_id` written to `AUDIT_LOGS_INFM` is the **refactor route slug** (`'firm-banking-settings'`) — the OQ-6 override 2026-05-09 retired the earlier "legacy verbatim" stance in favour of slug-based audit ids across every refactor surface. The legacy JEX audit-history query at `comm_0220_003_r001_act.jsp:74-86` still filters by `WSVC_ID = 'comm_0220_001'` and will NOT find refactor-written rows; that's intentional — refactor is local-only R&D and JEX never queries the local DB.

`wsvc_id` is NOT a routing identifier — Next.js routes (`/settings/firm-banking-settings`) and Spring REST URIs (`/api/v1/settings/firm-banking-settings`) are **independent** of the audit DB column even though both now happen to share the same string value. They evolve independently per the table below; renaming one doesn't rename the others.

| Identifier | Value (this feature) | Used by | Purpose |
|---|---|---|---|
| Audit `wsvc_id` (DB constant) | `'firm-banking-settings'` — **refactor slug** (OQ-6 override 2026-05-09; back-filled by V67) | `AUDIT_LOGS_INFM` rows; refactor-side audit-read endpoint | Stable per-feature identifier; same string as the route/URI/package by convention but conceptually independent (the four columns evolve separately) |
| Frontend mount (Next.js) | Settings modal panel — no standalone route in v1, mounted via `SettingsModalProvider` | `wecms-web/components/settings/firm-banking-settings/` | Frontend navigation |
| REST endpoint (Spring) | `/api/v1/settings/firm-banking-settings/{...}` | `FirmBankingSettingsController` | API contract |
| Java package | `com.kosign.wecms.domain.setting.firmbankingsettings` | Backend code organization | n/a |

The four identifiers above evolve **independently**. Renaming the Next.js panel route, the REST URI, or the Java package does **not** change the audit `wsvc_id` constant. Conversely, a future audit-log feature decision to migrate `wsvc_id` to refactor-style ids (e.g. `'firm-banking-settings'`) would update the service constant alone — no route or URI change required.

**Refactor stance:**
- **Keep the three-language write** to preserve the JEX detail-audit-render contract (the front-end query at `comm_0220_003_r001_act.jsp:74-86` filters by the caller's `LANG_CD` — losing the localized rows would break the detail dialog's audit tab on non-English locales).
- Encapsulate the three-row write in a shared `AuditLogService.append(refNo, refNo1, refNo2, wsvcId, activity, detail)` helper to be added under `domain/common/auditlog/` — **do not re-implement the three-language loop in every feature**. The same helper is needed for all CRUD-with-audit features (purpose, approval-line, etc.).
- The shared helper takes the activity/detail strings either as keys (resolved through `MessageResolver` for each of EN, KO, VI, KM) or as a `Map<LangCd, ActivityCopy>`. **Decide at audit-log feature implementation time** — not in this Phase 1 spec.
- Audit rows are written with `@Transactional(REQUIRES_NEW)` so a partial audit failure cannot strand the business write. (See §13 OQ-6 for the trade-off.)
- **Delete does NOT write an audit row** (parity with JEX, OQ-2 resolved 2026-05-09 — see §5.6). Only Create and Update append audit rows.

### 11.2 Approval line

JEX firm-banking-settings has **no approval-line integration** — Create / Update / Delete are immediate. Confirmed by inspecting all three write action JSPs (no calls to any `Appr*` DAO / helper). Refactor preserves this — no approval client wiring is added.

If the business decides firm-banking changes should later require approval (it's an obvious candidate — these bindings authorize outbound money movement), that is a **new feature**, not a refactor parity item. Track as §13 OQ-7.

---

## 12. Reuse map (refactor)

| Concern | Concrete reuse |
|---|---|
| Auth filter / JWT | `common/security/JwtAuthenticationFilter` |
| Tenant scoping | `common/tenant/TenantContext` — every repo method constrains on `BIZ_KEY` |
| Locale resolver | `common/enums/LangCd` + `common/i18n/MessageResolver` |
| Wire envelope | `common/response/ApiResponse` + `CommonHead` |
| Error model | `BusinessException` + `GlobalExceptionHandler` |
| Audit base columns | `common/entity/BaseEntity` |
| Audit log writer | **NEW shared service** `domain/common/auditlog/AuditLogService` (must be authored alongside this feature; see §11.1 + §13 OQ-6) |
| View Setting | `domain/setting/screenoptions/*` — register `WEB_SVC_ID = 'firm-banking-settings'` with the panel's column set as a follow-up (memory `feedback_view_setting_global` — global feature, register in shared seed migration). The current panel doesn't expose configurable columns, so this can defer to Phase 3b. |
| Excel export | `common/export/ExcelExportService` — **not used in v1**, the JEX panel has no export button |
| File upload | none — JEX firm-banking-settings has no firm-seal / certificate uploads (verified by reading all three view JSPs and all three TS files) |
| Encryption utility | **does not exist** — see §7.4. Track separately. |

---

## 13. Open questions / blockers

| ID | Question | Owner | Blocks |
|---|---|---|---|
| **OQ-1** | ~~Error code allocation~~ **Resolved 2026-05-09.** Verified against legacy `RSPS_CD/` XML registry, `WbMessageCode.java`, and refactor `messages.properties` — codes free. Final allocation: `WBE2020` = firm-banking-setting-not-found (used in v1 — Detail/Update/Delete), `WBE2021` = duplicate-firm-banking-setting (used in v1 — Create), `WBE2022` = invalid-status-transition (reserved, future), `WBE2023` = sensitive-field-access-denied (reserved, future). V8/V9/V10 referential checks reuse generic `WBE0001`/`WBE0002`. Phase 2 must add the four entries to [messages.properties](../../../src/main/resources/messages.properties) + [docs/_shared/error-code-registry.md](../../../../docs/_shared/error-code-registry.md). | Sonny — closed | — |
| **OQ-2** | ~~Should Delete write an audit log?~~ **Resolved 2026-05-09 — NO.** Confirmed against JEX runtime behavior 2026-05-09: Create / Update append audit rows, Delete / Deactivate do not. Refactor mirrors exactly. See §11.1 per-operation matrix. | Mr. Jung (PO) — closed | — |
| **OQ-3** | ~~Re-enable V11 (`wdrl_act_no` must exist in `ACCT_MSTR`)?~~ **Resolved 2026-05-09 — NO.** Keep JEX behavior. The check stays disabled in the refactor; restoring it could break existing business behavior (ticket [#42004](https://www.kosign.com.kh/redmine/issues/42004)). | Mr. Jung — closed | — |
| **OQ-4** | ~~When does the project's shared encryption utility land?~~ **Resolved 2026-05-09 — keep plain text in Phase 2 (legacy parity).** No feature-local encryption, no `@Convert` on `wdrl_act_no`. Mask in API responses by default; never log full account number. Future encryption is a project-wide migration covering `ACCT_MSTR`, `TB_FIRM_CONF`, `USER_BUSI_CONN_INFM`, etc. in lockstep — separate ticket. | Sonny — closed | — |
| **OQ-5** | JEX gates list to `NORM_USER_ONLY_YN=Y` (`WSVC.comm_0220_001_r001.xml:26`). Does the refactor enforce that with a `@PreAuthorize` against `TenantContext.currentUserType()`? Today the JWT filter does not yet populate `userType`. **Default decision (2026-05-09): skip enforcement in v1, document the gap.** Every authenticated user can call the endpoints — matches today's JEX practical behavior since WTA accounts are rare in DEV/UAT. Wiring `userType` into the JWT claim + adding `@PreAuthorize("@authz.isNormalUser()")` is deferred to a cross-cutting auth-hardening sprint and can be applied later without breaking the contract. | Arthur — default accepted | — |
| **OQ-6** | Audit `wsvc_id` field — legacy `comm_0220_001` vs refactor slug `firm-banking-settings`? Plus REQUIRES_NEW semantics on the audit write. **Initial default (2026-05-09 morning): keep legacy `wsvc_id = "comm_0220_001"`** for cross-system audit-history compatibility. **Override (2026-05-09 afternoon): use refactor slug `wsvc_id = "firm-banking-settings"`** — the refactor architecture standardises on slug-based identifiers across every surface, and JEX never queries the local DB anyway (refactor is local-only R&D). Compatibility consequence: legacy JEX audit screens filtering by `WSVC_ID = 'comm_0220_001'` will not see refactor-written rows under that filter — accepted, since no JEX runtime queries this local DB. V67 back-fills any pre-existing local rows. `REQUIRES_NEW` semantics on the writer remain: write audit isolated from caller's tx; audit failure logs at WARN and never propagates. | Audit-log feature owner — overridden 2026-05-09 | — |
| **OQ-7** | Should firm-banking changes go through approval-line in the refactor? JEX does not. **Status: parked — future feature ticket, does not block v1.** v1 preserves JEX behavior (immediate Create/Update/Delete with no approval gate). If the business later requires approval, it is a new feature, not a refactor parity item. | Mr. Jung — parked | — |
| **OQ-8** | ~~Can `out_bank_cd` ever be a value outside the Shinhan / Woori pair in DEV?~~ **Verified 2026-05-09 — YES.** DEV holds 3 distinct values: `970424` Shinhan (10 rows), `970436` Vietcombank (1 row), `970457` Woori (4 rows). The Vietcombank row carries `file_type='VCB'` despite the JEX action JSP at `comm_0220_002_c001_act.jsp:123-127` only branching on Shinhan/Woori — inserted via a different code path. V8 in §6 + the §5.4 `file_type` mapping table are updated to reflect 3 banks. **Spawned OQ-8a below.** | Arthur — verified | OQ-8a (below) |
| **OQ-8a** | **Provisionally resolved 2026-05-09 — Option (c): bounded-bank set + strict reject.** Sonny chose Option (c) without waiting for PO sign-off so the Create slice could ship the same day; Mr. Jung may override later (decision is reversible — flipping to Option (a) is a no-op for the supported set, and flipping to Option (b) requires a single DTO addition). **Rule applied:** `out_bank_cd ∈ {970424 Shinhan, 970436 Vietcombank, 970457 Woori}` only. Anything else → `WBE0001 Invalid OUT_BANK_CD` (rule V8). `file_type` stays server-derived from the §5.4 table (Shinhan→`SHB`, Vietcombank→`VCB`, Woori→`WRB`); the client never sends `file_type`. **Scope caveat:** this is a *temporary bounded-bank implementation* aligned to current DEV/JEX operational reality — three banks in production, three banks supported, one mapping table. **Future bank onboarding:** when a fourth bank goes live in production, the operational change is one row in the §5.4 mapping table + the same row in `BANK_TO_FILE_TYPE` inside `FirmBankingSettingsService` + the existing `out_bank_cd` allow-list constant — never a JEX porting exercise, never a re-architecture. **Tracked as `TODO(future-bank-onboarding)`** in the service constant Javadoc and at `BANK_TO_FILE_TYPE` so a future contributor can grep for it. **V9 referential check (`out_bank_cd ∈ NAPAS_BANK_INFO`)** is **deferred** — `NAPAS_BANK_INFO` is not migrated locally; with Option (c)'s strict 3-bank reject, V9 is strictly weaker than V8 for the supported set and contributes no extra safety. Re-enable V9 when `NAPAS_BANK_INFO` lands locally (separate MR). | Sonny — provisionally resolved (PO override allowed) | — |

---

## 14. Plan summary

**Scope.** Refactor the JEX `comm_0220_*` firm-banking-settings CRUD into Spring Boot 3.5 + Next.js. Five operations: list, detail (+ audit sub-resource), create, update, delete. R&D only — local + GitLab. Wire format lowercase snake_case (CTO directive 2026-05-08). Audit log preserves three-language write parity with JEX. No approval-line, no bank-test-connection, no save-as-draft, no encryption-at-rest in v1.

**Deliverables created (this MR — Phase 1 docs only).**
1. `docs/refactor-wecms/features/settings/firm-banking-settings.md` *(this file)* — feature spec.
2. `docs/refactor-wecms/migration/firm-banking-settings-mapping.md` — JEX → Spring service / DAO / IDO / WSVC / TS field-by-field mapping.
3. `docs/refactor-wecms/database/settings/firm-banking-settings-schema.md` — column-for-column DDL extracted from DEV + planned Flyway migration filenames.
4. `docs/refactor-wecms/audits/firm-banking-settings-parity-checklist.md` — operation × dimension parity matrix for Phases 2–4 verification.

**No code written under `wecms-api/` or `wecms-web/` in this MR.** No JEX file modified.

**Open questions (0 open / 1 provisionally resolved / 2 default-accepted / 1 parked / 5 resolved — see §13).**
- **OQ-1 (resolved 2026-05-09)** — error-code allocation: `WBE2020`=not-found, `WBE2021`=duplicate (used in v1), `WBE2022`/`WBE2023` reserved for future. Generic `WBE0001`/`WBE0002` cover V8/V9/V10 referential checks.
- **OQ-2 (resolved 2026-05-09)** — Delete will NOT write audit rows (runtime-confirmed); legacy parity preserved.
- **OQ-3 (resolved 2026-05-09)** — V11 (`wdrl_act_no ∈ ACCT_MSTR`) stays disabled; legacy parity preserved.
- **OQ-4 (resolved 2026-05-09)** — `wdrl_act_no` stays plain text in DB (DEV-verified); masking is presentation-layer only; future encryption is project-wide.
- **OQ-5 (default 2026-05-09)** — `NORM_USER_ONLY_YN=Y` enforcement skipped in v1; document the gap; defer `userType` JWT wiring to an auth-hardening sprint.
- **OQ-6 (overridden 2026-05-09)** — audit `wsvc_id = "firm-banking-settings"` (refactor slug); the earlier "keep legacy" default was retired in favour of slug-based audit ids across every refactor surface. V67 back-fills existing local rows.
- **OQ-7 (parked)** — approval-line is a future feature ticket; v1 preserves JEX (no approval gate).
- **OQ-8 (verified 2026-05-09)** — DEV has 3 distinct `out_bank_cd` values (Shinhan / Vietcombank / Woori); V8 + §5.4 mapping updated.
- **OQ-8a (provisionally resolved 2026-05-09 — Option c)** — bounded-bank set `{970424, 970436, 970457}`; anything else → `WBE0001`. `file_type` server-derived (`SHB`/`VCB`/`WRB`); client never sends `file_type`. Mr. Jung may override later. V9 (`NAPAS_BANK_INFO` existence) deferred — strictly weaker than V8 for the supported set; re-enable when `NAPAS_BANK_INFO` lands locally. Future bank onboarding tracked via `TODO(future-bank-onboarding)` markers in code.

**Blockers.**
- **Phase 1 documentation:** none.
- **Phase 2 backend implementation:** complete — Create / Update / Delete all wired through the shared `AuditLogService` (Update + Create write audit; Delete by design does not).
- **Phase 2 — Create endpoint:** unblocked under OQ-8a Option (c) provisional resolution. PO sign-off pending; no code change needed if PO confirms (a) or (c). Switching to (b) would add an optional `file_type` to the request DTO without breaking the Option (c) wire shape.
