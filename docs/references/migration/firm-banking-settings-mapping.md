# Firm Banking Settings — JEX → Spring + Next.js Migration Mapping

**Companion to:** [docs/refactor-wecms/features/settings/firm-banking-settings.md](../../features/settings/firm-banking-settings.md)
**JEX functional spec:** WECMS DEV — `comm_0220_*` services + `TbFirmConf` entity / DAO
**Refactor target package:** `com.kosign.wecms.domain.setting.firmbankingsettings`
**Phase:** 1 — design only.

This document is the side-by-side translation table. When Phase 2 implementation begins, every row here becomes a verification target.

---

## 1. Service mapping table

| JEX service | JEX action JSP (file:line) | JEX WSVC (file) | JEX IDO used | Refactor controller method | Refactor service method | HTTP / URI |
|---|---|---|---|---|---|---|
| `comm_0220_001_r001` (List) | `WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/api/comm/comm_0220_001_r001_act.jsp:82-96` | `WECMS_ETC/xml/service/WSVC/WSVC.comm_0220_001_r001.xml` | `IDO.TB_FIRM_CONF_R004.xml` (LEFT JOIN ACCT_MSTR) | `FirmBankingSettingsController.list()` | `FirmBankingSettingsService.list()` | `GET /api/v1/settings/firm-banking-settings` |
| `comm_0220_002_c001` (Create) | `comm_0220_002_c001_act.jsp:79-178` | `WSVC.comm_0220_002_c001.xml` | `TB_FIRM_CONF_R001` (PK existence check), `NAPAS_BANK_INFO_R001`, `TB_FIRM_BANK_CODE_R001`, `TB_FIRM_CONF_C001`, `AUDIT_LOGS_INFM_C001` | `FirmBankingSettingsController.create(req)` | `FirmBankingSettingsService.create(req, principal)` + `AuditLogService.append(...)` | `POST /api/v1/settings/firm-banking-settings` |
| `comm_0220_003_r001` (Detail + audit) | `comm_0220_003_r001_act.jsp:67-91` | `WSVC.comm_0220_003_r001.xml` | `TB_FIRM_CONF_R001`, `AUDIT_LOGS_INFM_R001` | `FirmBankingSettingsController.detail(out, wdr)` + `audit(out, wdr)` | `FirmBankingSettingsService.detail(...)` + `FirmBankingSettingsService.auditLog(...)` | `GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` + `…/audit` |
| `comm_0220_003_u001` (Update) | `comm_0220_003_u001_act.jsp:74-127` | `WSVC.comm_0220_003_u001.xml` | `TB_FIRM_CONF_R001` (existence check via `ValidationHelper.validateTbFirmConf`), `TB_FIRM_CONF_U001`, `AUDIT_LOGS_INFM_C001` | `FirmBankingSettingsController.update(out, wdr, req)` | `FirmBankingSettingsService.update(id, req, principal)` + `AuditLogService.append(...)` | `PUT /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` |
| `comm_0220_001_d001` (Delete) | `comm_0220_001_d001_act.jsp:59-75` | `WSVC.comm_0220_001_d001.xml` | `TB_FIRM_CONF_R001`, `TB_FIRM_CONF_D001` | `FirmBankingSettingsController.delete(out, wdr)` | `FirmBankingSettingsService.delete(id, principal)` — **no audit-log append** (legacy parity, OQ-2 resolved 2026-05-09) | `DELETE /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}` |

The JEX firm-banking-history service `comm_0221_001_r001` is **not** part of the firm-banking-settings refactor — it is owned by the already-shipped `firm-banking-history` feature ([docs/refactor-wecms/features/settings/firm-banking-history.md](../../features/settings/firm-banking-history.md)).

---

## 2. JEX DAO → Spring Repository mapping

| JEX | Refactor | Notes |
|---|---|---|
| `wabooks.dao.TbFirmConfDAO` (`WECMS DEV/WECMS/src/wabooks/dao/TbFirmConfDAO.java`) | `FirmBankingSettingRepository extends JpaRepository<FirmBankingSetting, FirmBankingSettingId>` | One repo handles all writes + PK reads. The list join is owned by a separate read-only `FirmBankingSettingViewRepository`. |
| `TbFirmConfDAO.findByPk(util, idoCon, pk)` (`TbFirmConfDAO.java:39-42`) | `repo.findById(id)` | `id = new FirmBankingSettingId(bizKey, outBankCd, wdrlActNo)` from `TenantContext.currentBizKey()` + path. |
| `TbFirmConfDAO.findByDynamic(util, idoCon, dynamic0)` (`TbFirmConfDAO.java:44-58`) | n/a — list goes through the view repo | The dynamic-WHERE pattern is replaced by JPQL parameters or, where the join is needed, the `V_FIRM_BANKING_SETTING` view. |
| `TbFirmConfDAO.create(util, idoCon, entity)` (`TbFirmConfDAO.java:62-96`) | `repo.save(entity)` (new PK) | Defaults set in `FirmBankingSettingsService` (V14, V15, V16, V17, V18, V19) before calling `save`. |
| `TbFirmConfDAO.update(util, idoCon, entity)` (`TbFirmConfDAO.java:99-110`) | `repo.save(entity)` (existing PK) | Service sets `mod_dttm = utcNow()` and `last_edtr = principal.userNm` before save. |
| `TbFirmConfDAO.upsert(util, idoCon, entity)` (`TbFirmConfDAO.java:113-121`) | n/a | Not used by any of the five firm-banking-settings action JSPs; do not port. |
| `TbFirmConfDAO.deleteByPk(util, idoCon, pk)` (`TbFirmConfDAO.java:124-130`) | `repo.deleteById(id)` | After existence check via `findById(...).orElseThrow(...)`. |
| `wabooks.dao.NapasBankInfoDAO.findByPk(...)` (`comm_0220_002_c001_act.jsp:91-95`) | `domain/banking/NapasBankRepository` (existing if already ported; otherwise add a thin existence-check projection) | Used only for V9 referential validation. |
| `wabooks.dao.TbFirmBankCodeDAO` (existence check) | `domain/setting/firmbankingsettings/repository/FirmBankCodeRepository` (or reuse if a sibling feature already defined it) | V10. PK = `(ant_div_cd, bank_cd)`. |
| `wabooks.dao.AcctMstrDAO.findByPk(...)` (`comm_0220_002_c001_act.jsp:99-101`) | **not invoked** by the refactor service | V11 stays disabled (OQ-3 resolved 2026-05-09 — legacy parity). The `ACCT_MSTR` repository is still consumed via the `V_FIRM_BANKING_SETTING` LEFT JOIN for list display, but no existence check is performed on Create. |
| `wabooks.dao.AuditLogsInfmDAO.create(...)` (`comm_0220_002_c001_act.jsp:173`, `comm_0220_003_u001_act.jsp:122`) | `domain/common/auditlog/AuditLogService.append(...)` (NEW shared service — see feature §11.1) | Three-language insert encapsulated in the helper, not open-coded per feature. |

### 2.1 `JexConnection` / `IDOConnection` → JPA `@Transactional`

| JEX pattern | Refactor pattern |
|---|---|
| `JexConnection idoCon = JexConnectionManager.createIDOConnection();` (every action JSP, ~line 70-75) | Spring-managed connection — no manual lifecycle. |
| `idoCon.beginTransaction();` (e.g. `comm_0220_002_c001_act.jsp:77`) | `@Transactional` on `FirmBankingSettingsService.create / update / delete`. |
| `IdoUtils.commitAndCloseQuietly(idoCon);` (e.g. `comm_0220_002_c001_act.jsp:176`) | implicit on `@Transactional` exit. |
| `WbRollbackException` (`TbFirmConfDAO.java:55`, `comm_0220_002_c001_act.jsp:87`) | `BusinessException(code)` — the global exception handler maps it to HTTP 200 with `error=true`. Code translation per feature §5. |
| `WbException` (non-rollback) | `BusinessException` — same handler. |

---

## 3. JEX IDO → Spring DTO / repository query mapping

| JEX IDO | SQL essence | Refactor equivalent |
|---|---|---|
| `IDO.TB_FIRM_CONF_R001.xml` (read by PK + dynamic) | `SELECT * FROM TB_FIRM_CONF WHERE 1=1 ??` (lines 23-42) | `repo.findById(id)` for the PK case. The dynamic variant is unused outside the list IDO. |
| `IDO.TB_FIRM_CONF_R002.xml` / `R003.xml` | unused by the firm-banking-settings flow (referenced by other helpers) | n/a |
| `IDO.TB_FIRM_CONF_R004.xml` | `SELECT … FROM (SELECT TFC.*, AM.CCY_CD, AM.BAL, AM.EXPI_DT FROM TB_FIRM_CONF TFC LEFT JOIN ACCT_MSTR AM ON TFC.BIZ_KEY = AM.BIZ_KEY AND TFC.WDRL_ACT_NO = AM.ACCT_NO WHERE 1=1 ??)` (lines 24-67) | New read-only **view** `V_FIRM_BANKING_SETTING` (Flyway migration — see [database/settings/firm-banking-settings-schema.md §3](../database/settings/firm-banking-settings-schema.md)) backed by `FirmBankingSettingView` `@Immutable` entity. List service queries `findAllByBizKey(bizKey)` on the view repo. |
| `IDO.TB_FIRM_CONF_C001.xml` | `INSERT INTO TB_FIRM_CONF (...) VALUES (...)` | `repo.save()` (new entity). Defaults applied in service. |
| `IDO.TB_FIRM_CONF_U001.xml` | `UPDATE TB_FIRM_CONF SET … WHERE BIZ_KEY=? AND OUT_BANK_CD=? AND WDRL_ACT_NO=?` | `repo.save()` (existing entity, dirty-tracked). |
| `IDO.TB_FIRM_CONF_D001.xml` | `DELETE FROM TB_FIRM_CONF WHERE BIZ_KEY=? AND OUT_BANK_CD=? AND WDRL_ACT_NO=?` | `repo.deleteById(id)`. |
| `IDO.TB_FIRM_BANK_CODE_R001.xml` (existence check) | `SELECT * FROM TB_FIRM_BANK_CODE WHERE 1=1 ??` | `firmBankCodeRepo.existsById(new FirmBankCodeId(AntDivCd.NAPAS, outBankCd))`. |
| `IDO.AUDIT_LOGS_INFM_R001.xml` (audit log query for detail) | `SELECT … FROM AUDIT_LOGS_INFM WHERE BIZ_KEY=? AND REF_NO=? AND REF_NO1=? AND REF_NO2=? AND WSVC_ID=? AND LANG_CD=?` | `auditLogRepo.findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(...)` — derived method. |

---

## 4. WSVC field → DTO field mapping (lowercase snake_case wire)

### 4.1 List response (`comm_0220_001_r001` REC sub-rule → `FirmBankingSettingRow`)

WSVC source: `WSVC.comm_0220_001_r001.xml:38-52`. Wire form below (Java DTO field names are camelCase; serialized as snake_case via global Jackson strategy — see `wecms-api/CLAUDE.md` §API Naming).

| WSVC `<item id>` | WSVC length | DB column / type | Java DTO field (`FirmBankingSettingRow`) | Wire JSON | Notes |
|---|---|---|---|---|---|
| `OUT_BANK_CD` | 20 | `out_bank_cd VARCHAR(15)` | `String outBankCd` | `out_bank_cd` | NAPAS bank code. |
| `BANK_NM` | 2000 | `tb_firm_bank_code.bank_nm VARCHAR(300)` (joined) | `String bankNm` | `bank_nm` | From `TB_FIRM_BANK_CODE` JOIN — added in service-layer enrichment or via the view. |
| `BANK_PRTN_ID` | 20 | `bank_prtn_id VARCHAR(20)` | `String bankPrtnId` | `bank_prtn_id` | |
| `VAN_ORG_CD` | 20 | `van_org_cd VARCHAR(20)` | `String vanOrgCd` | `van_org_cd` | |
| `ORG_STAT` | 1 | `org_stat VARCHAR(1)` | `String orgStat` | `org_stat` | Enum value (0..3). |
| `WDRL_ACT_NO` | 100 | `wdrl_act_no VARCHAR(100)` | `String wdrlActNo` | `wdrl_act_no` | |
| `FILE_TYPE` | 100 | `file_type CHAR(3)` | `String fileType` | `file_type` | |
| `BAT_TRAN_BRANCH_DIV` | 1 | `bat_tran_branch_div CHAR(1)` | `String batTranBranchDiv` | `bat_tran_branch_div` | |
| `BAT_TRAN_BRANCH_LIMIT` | 7 | `bat_tran_branch_limit NUMERIC(7,0)` | `Integer batTranBranchLimit` | `bat_tran_branch_limit` | |
| `LAST_EDTR` | 20 | `last_edtr VARCHAR(50)` | `String lastEdtr` | `last_edtr` | WSVC length 20 is conservative — DB allows 50. Refactor uses 50. |
| `REG_DTTM` | 8 | `reg_dttm VARCHAR(14)` | `String regDttm` | `reg_dttm` | 14-digit yyyyMMddHHmmss string. WSVC length 8 is wrong in JEX — DB stores 14. Refactor sends 14. |
| `MOD_DTTM` | 8 | `mod_dttm VARCHAR(14)` | `String modDttm` | `mod_dttm` | Same — refactor sends 14. |
| `CCY_CD` | 3 | `acct_mstr.ccy_cd` (joined) | `String ccyCd` | `ccy_cd` | From `ACCT_MSTR` LEFT JOIN. May be null if account ledger not yet populated. |
| (added by R004 only) | — | `acct_mstr.bal NUMERIC` (joined) | `BigDecimal bal` | `bal` | From `ACCT_MSTR` LEFT JOIN. Not in WSVC subRule but returned by IDO — JEX implicitly drops it; refactor includes it for parity with `IDO.TB_FIRM_CONF_R004.xml:88`. |
| (added by R004 only) | — | `acct_mstr.expi_dt` (joined) | `String expiDt` | `expi_dt` | Same. |

> The two WSVC length anomalies (`REG_DTTM`/`MOD_DTTM` declared `length="8"` while DB stores 14, `LAST_EDTR` declared 20 while DB allows 50) are JEX miss-configurations — preserved here only for traceability. The refactor uses the **DB column lengths** as the wire bound, since the JEX 8-length is silently truncated by the IDO and produces broken timestamps in some clients today.

### 4.2 Detail response (`comm_0220_003_r001` → `FirmBankingSettingDetail`)

Same field set as 4.1 minus the join columns (`ccy_cd`, `bal`, `expi_dt`, `bank_nm`). The audit `REC` becomes its own `GET …/audit` resource — see feature §5.3.

### 4.3 Create request (`comm_0220_002_c001` IN → `FirmBankingSettingCreateRequest`)

WSVC source: `WSVC.comm_0220_002_c001.xml:33-37` (verified by inspecting `comm_0220_002_c001_act.jsp:38-43`).

```java
public record FirmBankingSettingCreateRequest(
    @NotBlank @Size(max = 15)  @Pattern(regexp = "^[0-9]{1,15}$")          String outBankCd,
    @NotBlank @Size(max = 100) @Pattern(regexp = "^[A-Za-z0-9\\-]{1,100}$") String wdrlActNo,
    @NotBlank @Size(max = 20)                                              String bankPrtnId,
    @NotBlank @Size(max = 20)                                              String vanOrgCd,
    @NotBlank @Pattern(regexp = "^[0-3]$")                                 String orgStat
) {}
```

### 4.4 Update request (`comm_0220_003_u001` IN → `FirmBankingSettingUpdateRequest`)

`out_bank_cd` and `wdrl_act_no` come from the path; the body carries only the editable trio:

```java
public record FirmBankingSettingUpdateRequest(
    @NotBlank @Size(max = 20)                                              String bankPrtnId,
    @NotBlank @Size(max = 20)                                              String vanOrgCd,
    @NotBlank @Pattern(regexp = "^[0-3]$")                                 String orgStat
) {}
```

### 4.5 Stripped legacy artifacts

| JEX entity / DTO | Refactor stance |
|---|---|
| `@JsonProperty("OUT_BANK_CD")` etc. on `wabooks.entity.TbFirmConf` (`TbFirmConf.java:32, 36, 40, 44, 48, 52, 56, 61, 66, 71, 76`) | **Stripped.** Java field name + global `JacksonConfig` SNAKE_CASE strategy emits the right wire name. (`wecms-api/CLAUDE.md` §Refactoring rules step 1.) |
| `@JsonSerialize(using = DateTime14Serializer.class)` on `regDttm`/`startDttm`/`endDttm`/`modDttm` (`TbFirmConf.java:57-58, 62-63, 67-68, 72-73`) | **Removed.** Legacy column type is `varchar(14)` — refactor entity stores `String` directly to match DEV (`information_schema.columns` confirmed). No conversion to/from joda `DateTime` needed. |
| `OrgStat.fromValue("0..3")` enum (`OrgStat.java:7-10`) | **Reuse the value list as a plain regex.** `@Pattern("^[0-3]$")` keeps the wire surface as the legacy-string convention. Do **not** introduce a typed enum that auto-converts on deserialization — that breaks parity with the JEX `String`-on-the-wire contract and complicates the View Setting / audit-log strings. |
| `OutBankCd` enum (`OutBankCd.java:7-9`) — Shinhan/Woori only | **Do not port as a Java enum.** DEV evidence 2026-05-09 (OQ-8) found a third value (`970436` Vietcombank) the enum doesn't list. Refactor uses a `Map<String, String>` `OUT_BANK_TO_FILE_TYPE` keyed by `out_bank_cd` (Shinhan→SHB, Vietcombank→VCB, Woori→WRB) so adding a bank is data, not code. Strict-vs-flexible behavior for codes outside the map is **pending OQ-8a** (feature §13). |
| `BusiInfmPk` superclass on `TbFirmConfPk` (`TbFirmConfPk.java:19`) | **Replaced.** Refactor uses an explicit `@IdClass(FirmBankingSettingId)` with three fields (bizKey, outBankCd, wdrlActNo). No multi-level PK inheritance. |
| `IDODynamic` filter assembly (`TbFirmConfPk.java:34-47`) | **Removed.** All filters become JPQL parameters or repository derived methods. |
| `JexData.put / getString` in action JSPs | **Removed.** Inputs/outputs are typed records; deserialization through the global `ObjectMapper`. |

---

## 5. JEX TypeScript event handler → Next.js hook mapping

The static-UI panel components already exist under `wecms-web/components/settings/firm-banking-settings/`. This mapping is the wire-up checklist for Phase 3.

| JEX TS handler (file:line) | Behavior | Next.js component target | New hook / API call |
|---|---|---|---|
| `comm_0220_001.ts:28-41` `onload()` — calls `fn_loadData()` then reads env settings | Initial list fetch + restore Auto Transfer / Auto Match toggles | `FirmBankingSettingsPanel.tsx` | `useApiData(fetchFirmBankingSettings, ...)` (mirror `firm-banking-history` pattern). Toggle restoration is a separate hook against the env-settings API (out of scope — see feature §10). |
| `comm_0220_001.ts:51-53` `add-firm-bank` click → `openLayerPopup("comm_0220_002.act")` | Open Create dialog | `FirmBankingSettingsPanel` "+" button → `setCreateDialogOpen(true)` | `NewFirmBankingDialog` already shipped — wire its onSubmit to `createFirmBankingSetting(req)`. |
| `comm_0220_001.ts:56-71` `edit-firm-bank` click | Open Edit dialog with pre-loaded data | `FirmBankingSettingsPanel` row menu → `setEditDialogOpen({outBankCd, wdrlActNo})` | Hook fetches via `getFirmBankingSetting(outBankCd, wdrlActNo)` then populates `EditFirmBankingDetailsDialog` form state. |
| `comm_0220_001.ts:74-89` `.ks-bank-image-block` click | Open View Detail dialog (read-only) | `FirmBankingDetailsDialog.tsx` (already shipped) | Same fetch as Edit; renders read-only. The `…/audit` GET fires lazily when the audit tab is opened. |
| `comm_0220_001.ts:92-101` `delete-firm-bank` click → `successPopup(...)` | Confirm-and-delete flow | Reuse existing `<ConfirmDialog>` from `components/shared/` | onConfirm → `deleteFirmBankingSetting(outBankCd, wdrlActNo)` → invalidate list query. |
| `comm_0220_001.ts:111-114, 117-120` toggle change handlers | Auto Transfer / Auto Match env writes | `FirmBankingAutomationSettings.tsx` | **Out of scope** — separate env-settings feature. Keep static for v1 (feature §10). |
| `comm_0220_002.ts onload + checkFields` | Validate-and-enable Submit | `NewFirmBankingDialog.tsx` | React form state — `useState` + per-field `isValid`. |
| `comm_0220_002.ts fuctionLoadWithdrawalAccounts(bankValue)` (≈L80) | Populate withdrawal-account dropdown for a chosen bank | `NewFirmBankingDialog.tsx` `<AccountPicker>` | `useApiData(fetchAccountsByBank(outBankCd))` against the existing `domain/banking/AccountController`. |
| `comm_0220_002.ts fuctionCreateFirmBank()` (≈L52) | POST + close + refetch | `NewFirmBankingDialog.tsx` onSubmit | `createFirmBankingSetting(req)` then `onSuccess()` from the panel. Pessimistic flow — dialog stays open until 200 OK. |
| `comm_0220_003.ts onload, loadDataToUpdate` | Detect `EDIT_FIRM` flag, fetch, populate form | `FirmBankingDetailsDialog` / `EditFirmBankingDetailsDialog` | Two components already; the wrapper picks based on the menu action. Both share `useApiData(fetchFirmBankingSetting, [outBankCd, wdrlActNo])`. |
| `comm_0220_003.ts updateFirmBank()` | PUT + close + refetch | `EditFirmBankingDetailsDialog` onSubmit | `updateFirmBankingSetting(outBankCd, wdrlActNo, req)` then `onSuccess()`. |
| `comm_0220_003.ts successPopup(...)` (≈L84) | Delete confirmation reused inside the detail dialog | shared `<ConfirmDialog>` | same as the row menu delete path. |

### 5.1 Frontend file inventory (Phase 3 wire-up checklist)

| New / modified file | Purpose |
|---|---|
| `lib/types/api.ts` | Add `ApiFirmBankingSettingRow`, `ApiFirmBankingSettingDetail`, `ApiFirmBankingAuditEntry`, `ApiFirmBankingSettingListResponse`. Wire fields lowercase snake_case. |
| `lib/api/firmBankingSettings.ts` (NEW) | `fetchFirmBankingSettings()`, `getFirmBankingSetting(outBankCd, wdrlActNo)`, `getFirmBankingSettingAudit(...)`, `createFirmBankingSetting(req)`, `updateFirmBankingSetting(outBankCd, wdrlActNo, req)`, `deleteFirmBankingSetting(outBankCd, wdrlActNo)`. Snake_case → camelCase mapping via the wire types. |
| `lib/api/mappers.ts` | Add `mapFirmBankingSettingRow`, `mapFirmBankingAuditEntry`. Mirrors the `firm-banking-history` pattern. |
| `components/settings/firm-banking-settings/FirmBankingSettingsPanel.tsx` | Replace `mockData.ts` import with `useApiData(fetchFirmBankingSettings)`. Keep visual layout. |
| `components/settings/firm-banking-settings/NewFirmBankingDialog.tsx` | Wire onSubmit to `createFirmBankingSetting`. Resolve V8/V9/V10/V12 errors via the global error dialog (`ApiCallError`). |
| `components/settings/firm-banking-settings/EditFirmBankingDetailsDialog.tsx` | Same, calling `updateFirmBankingSetting`. |
| `components/settings/firm-banking-settings/FirmBankingDetailsDialog.tsx` | Audit tab fetches `…/audit` lazily on first open. |
| `components/settings/firm-banking-settings/types.ts` | Drop or extend the static-UI types so they unify with `FirmBankingSettingRow`. |
| `components/settings/firm-banking-settings/mockData.ts` | Demote to offline-dev affordance only — same pattern as `firm-banking-history`. |

Form-state strategy (matches feature §4.2):

- **Pessimistic** create / update — dialogs stay open until 200 OK. `EditFirmBankingDetailsDialog.tsx` already supports a "saving" state.
- **No unsaved-changes guard** — JEX has none; the dialog is the unit of work. (Confirm against Figma at Phase 3.)
- **Error rendering** — every `ApiCallError` flows through the existing global `<ErrorDialog>`, with the `trace_id` displayed for support.

---

## 6. Audit log mapping

JEX writes three rows per Create / Update via `AuditLogsInfmDAO.create(...)` (`comm_0220_002_c001_act.jsp:138-174`, `comm_0220_003_u001_act.jsp:88-123`). **JEX writes no audit row on Delete** (`comm_0220_001_d001_act.jsp` has no `AuditLogsInfm` write). Refactor encapsulates the loop and matches JEX exactly: append on Create / Update, **no append on Delete** (OQ-2 resolved 2026-05-09 — legacy parity).

```java
// Create / Update only — Delete intentionally omits this call (OQ-2 parity).
auditLogService.append(
    AuditLogContext.builder()
        .wsvcId("firm-banking-settings")              // refactor route slug (OQ-6 override 2026-05-09 — slug-based audit ids)
        .refNo("TB_FIRM_CONF")
        .refNo1(req.outBankCd())
        .refNo2(req.wdrlActNo())
        .activityKey("audit.firm_banking_settings.activity")
        .detailKey("audit.firm_banking_settings.create.detail")  // or _.update.detail
        .build()
);
```

The helper resolves `activityKey` / `detailKey` against the four supported locales (EN / KO / VI / KM) via `MessageResolver`, inserts four rows (one per locale), all in `@Transactional(REQUIRES_NEW)` — write executes after the business commit; an audit failure logs at WARN and never propagates.

`wsvc_id` carries the **refactor route slug `firm-banking-settings`** (OQ-6 override 2026-05-09). The earlier "keep legacy `comm_0220_001`" default was retired in favour of slug-based audit ids across every refactor surface. Compatibility consequence: the legacy JEX detail-audit query at `comm_0220_003_r001_act.jsp:74-86` (filters by `wsvc_id = 'comm_0220_001'`) will NOT return refactor-written rows. This is intentional — refactor is local-only R&D and JEX never queries the local DB. V67 back-fills any pre-existing local rows that still carry the old constant.

**Identifier separation (revised 2026-05-09).** `wsvc_id` is a **per-feature stable identifier** for audit rows. Today it happens to share the same string as the route / REST URI / Java package by convention, but the four surfaces remain conceptually independent — renaming one does not rename the others. Legacy JEX URLs of the form `https://dev.wecms.vn/comm_0220_001.act` are JEX-only artifacts that no refactor surface references. The refactor's frontend route (`/settings/firm-banking-settings`), REST URI (`/api/v1/settings/firm-banking-settings/...`), Java package (`com.kosign.wecms.domain.setting.firmbankingsettings`), and audit `wsvc_id` (`firm-banking-settings`) are all independent identifiers — keep them aligned by convention but not coupled by code. See feature §11.1 *Identifier separation* for the full table.

---

## 7. ML / dictionary code mapping

JEX dictionary references found in the firm-banking-settings flow:

| JEX ML code | Surface | Refactor handling |
|---|---|---|
| `comm_0220_001_m001 … m009` (page labels) | `comm_0220_001_view.jsp` | Frontend i18n via `next-intl` — keys under `settings.firm_banking_settings.*`. Translate to EN/KO/VI/KM in `messages/{locale}.json`. |
| `comm_0220_002_m001 … m009` (create dialog labels) | `comm_0220_002_view.jsp` | Same. |
| `comm_0220_003_m001 … m006` (detail dialog labels) | `comm_0220_003_view.jsp` | Same. |
| `comm_0220_*_j001…` (TS-side labels via `.ML.get(...)`) | TS files | Inline messages already replaced by static literals in the existing static UI components — Phase 3 reviews each against the JEX TS to ensure no dictionary string is missed. |
| Audit activity / detail strings | `comm_0220_002_c001_act.jsp:148-157`, `comm_0220_003_u001_act.jsp:98-107` | Move into `messages/{locale}.json` under `audit.firm_banking_settings.*` (consumed by `AuditLogService`). |

JEX ML code IDs are **not** reused verbatim in the refactor (`wecms-api/CLAUDE.md` §i18n: "Do NOT reuse JEX ML code IDs verbatim"). Audit DB rows reference the **refactor message values** in EN/KO/VI/KM, not the JEX ID.

---

## 8. Permission / WSVC ext-flag mapping

| WSVC `<ext>` flag | JEX meaning | Refactor handling |
|---|---|---|
| `<WTA_USER_ALLOW_YN>N</WTA_USER_ALLOW_YN>` (`WSVC.comm_0220_001_r001.xml:25`) | WTA users not allowed | Track as feature §13 OQ-5 — needs `TenantContext.currentUserType()` first. |
| `<NORM_USER_ONLY_YN>Y</NORM_USER_ONLY_YN>` (`WSVC.comm_0220_001_r001.xml:26`) | Only normal users | Same. |
| `<login>Y</login>` | Login required | Default `SecurityConfig` JWT guard. |
| `<mntLogYn>Y</mntLogYn>` | Maintenance log enabled | Out of scope — JEX framework feature with no Spring counterpart. |
| `<trTp>U</trTp>` | Transaction type "U" (read?) | JEX framework metadata; not surfaced in the refactor. |

---

## 9. Unmapped legacy concepts

Items the refactor explicitly does **not** carry forward:

| JEX concept | Why dropped |
|---|---|
| `JexData` request/response envelope | Replaced by typed Java records + `ApiResponse<T>`. |
| `IDODynamic` `??` placeholder query assembly | Replaced by JPQL parameters or derived JPA queries. |
| `BusiInfmPk` multi-level PK inheritance | Replaced by flat `@IdClass`. |
| `WbException` / `WbRollbackException` | Replaced by `BusinessException`; behavior matches via the global handler. |
| `WABOOKSOSMCBaseData.getLocalLang()` (used at `comm_0220_002_c001_act.jsp:141`, `comm_0220_003_u001_act.jsp:91`) | Replaced by tenant `lang_cd` in `TenantContext` + the four-locale loop in `AuditLogService`. |
| `data-jex-ml="…"` / `.ML.get(...)` on the frontend | Replaced by `next-intl` keys. |
| `_v2common.app.openLayerPopup(...)` flow | Replaced by Next.js component-state-driven dialogs. |
