# Firm Banking Settings — Business Flow (Backend)

End-to-end request flow for every Firm Banking Settings operation. Source of truth: the implemented
Spring code under `com.kosign.wecms.domain.setting.firmbankingsettings`. Legacy behaviour reference:
JEX WSVC family `comm_0220_001/002/003` (read-only).

```
User (tenant admin)
      │
      ▼
Settings modal → Firm Banking Settings panel
      │   (every request carries the JWT; @menuAuthz.canAccess('settings-firm-banking-settings') gates the controller)
      ▼
FirmBankingSettingsController  ──►  FirmBankingSettingsService
      │                                 │
      │                                 ├─ TenantContext.currentBizKey()      (tenant scope, never a param)
      │                                 ├─ FirmBankingSettingViewRepository    (@Subselect list view, enriched)
      │                                 ├─ FirmBankingSettingRepository        (TB_FIRM_CONF CRUD by composite PK)
      │                                 ├─ TbFirmBankCodeRepository            (Create bank-code referential guard)
      │                                 ├─ AuditLogEntryRepository             (audit read for detail-audit)
      │                                 └─ AuditLogService.append(...)         (Create / Update only)
      ▼
ApiResponse<...>  (lowercase snake_case envelope)
```

---

## 1. List

```
GET /api/v1/settings/firm-banking-settings
  → service.list()                                   @Transactional(readOnly = true)
      → bizKey = TenantContext.currentBizKey()        (or WBE9999 if absent)
      → viewRepo.findAllByBizKeyOrderByOutBankCdAscWdrlActNoAsc(bizKey)
            -- @Subselect: TB_FIRM_CONF TFC
            --   LEFT JOIN ACCT_MSTR AM        ON TFC.BIZ_KEY = AM.BIZ_KEY AND TFC.WDRL_ACT_NO = AM.ACCT_NO
            --   LEFT JOIN TB_FIRM_BANK_CODE TFBC ON TFBC.ANT_DIV_CD='NAPAS' AND TFBC.BANK_CD = TFC.OUT_BANK_CD
            -- ORDER BY OUT_BANK_CD ASC, WDRL_ACT_NO ASC
      → each view row → FirmBankingSettingRow  (wdrl_act_no CLEARTEXT — frontend mapper masks the display)
  → FirmBankingSettingListResponse { list: FirmBankingSettingRow[] }
```

- Merges the legacy `comm_0220_001_r001` list (which LEFT-JOINs `ACCT_MSTR` for `CCY_CD` / `BAL` /
  `EXPI_DT`) and the bank-name resolution into one read through the `@Subselect` entity.
- `bank_nm` is `COALESCE(TB_FIRM_BANK_CODE.BANK_NM, BANK_ENG_NM)` from the NAPAS leg of the dictionary.
- No filters, no pagination — the panel renders the full tenant list (legacy parity).
- `wdrl_act_no` ships **cleartext** on the list rows (it is the PK the frontend addresses for write flows;
  an earlier server-side mask broke Delete/Update because the `@Pattern` guard rejects `*`). Display
  masking is applied frontend-side in `mapFirmBankingSettingRow` (OQ-4); the detail endpoint is cleartext too.

## 2. Detail

```
GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}
  → service.detail(outBankCd, wdrlActNo)              @Transactional(readOnly = true)
      → requireField(...) on each path component        (defence-in-depth → WBE_VALIDATION on blank)
      → settingRepo.findById(new TbFirmConfId(bizKey, outBankCd, wdrlActNo))
            .orElseThrow(WBE2020)                       -- not found OR wrong tenant (one envelope)
      → map → FirmBankingSettingDetail  (wdrl_act_no CLEARTEXT — feeds the edit dialog round-trip)
  → FirmBankingSettingDetail (flat — no JOIN columns)
```

## 3. Audit log

```
GET /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}/audit
  → service.auditLog(outBankCd, wdrlActNo)            @Transactional(readOnly = true)
      → langCd = TenantContext.currentLangCd() (or EN fallback)
      → auditRepo.findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(
              bizKey, "TB_FIRM_CONF", outBankCd, wdrlActNo, "firm-banking-settings", langCd)
      → each row → FirmBankingAuditEntry
  → FirmBankingAuditListResponse { list: FirmBankingAuditEntry[] }
```

- Mirrors the legacy `comm_0220_003_r001` audit `REC` sub-result, lifted to its own REST sub-resource.
- Does **not** pre-check the row exists — an audit lookup against an unknown PK simply returns `[]`.
- Filters by the slug `WSVC_ID = 'firm-banking-settings'` (OQ-6) — so it only sees refactor-written rows.

## 4. Create

```
POST /api/v1/settings/firm-banking-settings   body: FirmBankingSettingCreateRequest
  → @Valid (out_bank_cd digits ≤15; wdrl_act_no [A-Za-z0-9-] ≤100; bank_prtn_id/van_org_cd @NotBlank ≤20; org_stat ∈ 0..3)
  → service.create(req)  @Transactional
      1. bank scope guard: out_bank_cd ∈ {970424, 970436, 970457}    → WBE0001 otherwise (V8, OQ-8a)
      2. bank-code referential guard: bankCodeRepo.existsByAntDivCdAndBankCd("NAPAS", outBankCd)
                                                                       → WBE0002 if absent (V10)
      3. duplicate PK guard: settingRepo.existsById(id)               → WBE2021 if present (V12)
      4. file_type  = BANK_TO_FILE_TYPE.get(outBankCd)                 -- server-derived (SHB/VCB/WRB), V14
         batTranBranchDiv   = 'Y'  (V15 default)
         batTranBranchLimit = 2000 (V16 default)
         regDttm = utc-now yyyyMMddHHmmss; lastEdtr / regId from TenantContext
      5. settingRepo.save(entity)
      6. auditLogService.append(CREATE)  -- WSVC_ID=firm-banking-settings, REF_NO=TB_FIRM_CONF, REQUIRES_NEW inside the service
  → FirmBankingSettingDetail (post-write echo, cleartext wdrl_act_no)
```

## 5. Update

```
PUT /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}   body: FirmBankingSettingUpdateRequest
  → service.update(outBankCd, wdrlActNo, req)  @Transactional
      1. load by PK                                                     → WBE2020 if missing
      2. apply editable fields: bank_prtn_id, van_org_cd, org_stat      -- PK + file_type never change
      3. stamp modDttm / lastEdtr
      4. settingRepo.save(entity)
      5. auditLogService.append(UPDATE)  -- detailKey = audit.firm_banking_settings.update.detail
  → FirmBankingSettingDetail (refreshed)
```

## 6. Delete

```
DELETE /api/v1/settings/firm-banking-settings/{out_bank_cd}/{wdrl_act_no}
  → service.delete(outBankCd, wdrlActNo)  @Transactional
      1. existsById(id) ? else                                          → WBE2020
      2. settingRepo.deleteById(id)        -- HARD delete (no soft-delete column)
      3. NO audit row written              -- OQ-2, mirrors comm_0220_001_d001_act.jsp byte-for-byte
  → 200, data: null
```

The legacy delete (`comm_0220_001_d001`) is a physical delete with **no** audit write — the refactor
preserves both exactly. See [refactor-gap-analysis.md](refactor-gap-analysis.md) "Delete parity".

---

## Tenant + audit invariants (every mutating path)

- **Tenant** comes only from `TenantContext.currentBizKey()` (signed JWT) — never a request body/param.
  `bizKey` is the first component of the composite PK on every query.
- **Audit** rows are written for Create / Update only, with `WSVC_ID = 'firm-banking-settings'`,
  `REF_NO = 'TB_FIRM_CONF'`, `REF_NO1 = out_bank_cd`, `REF_NO2 = wdrl_act_no`, a localized activity
  (`audit.firm_banking_settings.activity`) + detail (`audit.firm_banking_settings.{create,update}.detail`).
  The shared `AuditLogService.append(...)` runs the insert in `@Transactional(REQUIRES_NEW)` so an audit
  failure does not roll back the business write. **Delete writes no audit row** (OQ-2). Reads write none.
- See [functional-specification.md §Audit](functional-specification.md) for the column map.
