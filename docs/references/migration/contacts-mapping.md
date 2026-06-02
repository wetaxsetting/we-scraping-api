# Contacts — JEX → Spring Migration Mapping

| Field | Value |
|---|---|
| Refactor slug | `contacts` |
| Functional spec | [`docs/refactor-wecms/features/contacts.md`](../../features/settings/contacts.md) |
| Backend design | [`docs/refactor-wecms/backend/contacts-design.md`](../business-rules/contacts-design.md) |
| Frontend design | [`docs/refactor-wecms/frontend/contacts-design.md`](../../../../wecms-web/docs/features/settings/contacts.md) |
| Schema | [`docs/refactor-wecms/database/contacts-schema.md`](../database/contacts-schema.md) |

This file is the single service-by-service traceability matrix from JEX (`comm_0184_*`) to refactor (`contacts`). Every row identifies a legacy artifact (with `file:line` citation), the refactor counterpart, and notes / deferrals.

---

## 1. Endpoint mapping

| Legacy WSVC | Legacy class | Refactor REST | Refactor class | Notes |
|---|---|---|---|---|
| `comm_0184_001_r001` ([WSVC.comm_0184_001_r001.xml:3](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_001_r001.xml)) | [`comm_0184_001_r001_act.jsp:38-253`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_r001_act.jsp) | `GET /api/v1/settings/contacts` | `ContactsController#list` | List + paging + sort + filter. Group filter (`GRP_CD`) NOT ported (see contacts.md OQ-1). Memo join NOT ported (table out of scope). |
| `comm_0184_001_u001` ([WSVC.comm_0184_001_u001.xml:3](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_001_u001.xml)) | [`comm_0184_001_u001_act.jsp:67-87`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_u001_act.jsp) | `POST /api/v1/settings/contacts/bulk-delete` | `ContactsController#bulkDelete` | Soft-delete: `TRCO_STTS='C'`. Same single-tx semantics. |
| `comm_0184_002_c001` ([WSVC.comm_0184_002_c001.xml:3](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_002_c001.xml)) | [`comm_0184_002_c001_act.jsp:59-250`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp) | `POST /api/v1/settings/contacts` | `ContactsController#create` | Creates master + addresses + accounts (Local) OR overseas beneficiaries (Overseas) + seeds `PGRO_BSAC_LNKN_INFM` with `B` + `S`. File upload **NOT** ported. |
| `comm_0184_002_c002` ([WSVC.comm_0184_002_c002.xml:3](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_002_c002.xml)) | [`comm_0184_002_c002_act.jsp`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c002_act.jsp) | — | — | **DEFERRED** — file-only "duplicate" endpoint; file-upload feature out of scope. |
| `comm_0184_003_r001` | (sub-popup address list) | — | — | **DEFERRED** — inlined into edit form. |
| `comm_0184_004_c001` / `_r001` | (sub-popup account list) | — | — | **DEFERRED** — inlined into edit form. |
| `comm_0184_005_r001`–`_r004` | (read for edit form) | `GET /api/v1/settings/contacts/{trcoKey}` | `ContactsController#getOne` | Refactor merges 4 read queries into one response — uses the same JSON-aggregated shape as the list IDO. |
| `comm_0184_005_u001` ([WSVC.comm_0184_005_u001.xml:3](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_u001.xml)) | [`comm_0184_005_u001_act.jsp:64-398`](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) | `PUT /api/v1/settings/contacts/{trcoKey}` | `ContactsController#update` | Full update — master + REC1 (addresses) + REC2 (accounts) + REC5 (overseas) + REC4 (account deletes) + REC6 (overseas deletes), one tx. **REC3 (file deletes) NOT ported.** |
| `comm_0184_005_u002`–`_u012` | (partial-update variants) | — | — | **DEFERRED** — refactor consolidates into a single PUT with discriminated sub-rows. |
| `comm_0184_005_c011` ([WSVC.comm_0184_005_c011.xml:3](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_c011.xml)) | (tax-agent WABOOKS account add) | — | — | **DEFERRED FOREVER** — tax-agent linkage out of refactor scope. |
| `comm_0184_005_d001` ([WSVC.comm_0184_005_d001.xml:32-40](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_d001.xml)) | (single bank-account delete) | folded into PUT (`accounts_to_delete[]`) | `ContactsService#update` | No standalone endpoint. |
| `comm_0184_006_c001` / `_r001` / `_r002` | (external contact / tax-agent) | — | — | **DEFERRED FOREVER** — out of scope. |
| `comm_0184_007_c001` / `_r001` / `_r002` ([WSVC.comm_0184_007.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_007.xml)) | Excel import | — | — | **DEFERRED** — post-MVP. |
| `comm_0184_001_r006_excel` ([WSVC.comm_0184_001_r006_excel.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_001_r006_excel.xml)) | Excel download | `POST /api/v1/settings/contacts/export` (follow-up MR) | `ContactsExportProvider` | **DEFERRED** to second MR — uses the shared `common/export/` pipeline ([`wecms-api/CLAUDE.md` § Excel export](../../../CLAUDE.md)) registered with `screenId='contacts'`. |
| Single-contact soft-delete | (absent — legacy bulks always) | `DELETE /api/v1/settings/contacts/{trcoKey}` | `ContactsController#delete` | New convenience endpoint; same `TRCO_STTS='C'` semantics. |

## 2. Entity / DAO mapping

| Legacy entity | File:line | Refactor entity | Refactor repository | Notes |
|---|---|---|---|---|
| `wabooks.entity.TrcoInfm` | [TrcoInfm.java:33-127](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoInfm.java) | `com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm` | `TrcoInfmRepository` (`JpaRepository<TrcoInfm, TrcoInfmId>`) | Composite `@IdClass(TrcoInfmId.class)` over `(BIZ_KEY, TRCO_KEY)`. Refactor maps only the Sprint columns it reads / writes — Sprint subset rule in [`wecms-api/CLAUDE.md` § "schema vs. entity vs. DTO split"](../../../CLAUDE.md). |
| `wabooks.entity.TrcoInfmPk` | [TrcoInfmPk.java:20-50](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoInfmPk.java) | `TrcoInfmId` | — | Refactor uses `@IdClass`. |
| `wabooks.entity.TrcoAddrInfm` | [TrcoAddrInfm.java:32-67](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoAddrInfm.java) | `TrcoAddrInfm` | `TrcoAddrInfmRepository` | PK `(BIZ_KEY, TRCO_KEY, TRCO_ADDR_KEY)`. |
| `wabooks.entity.TrcoAcctInfo` | [TrcoAcctInfo.java:28-99](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoAcctInfo.java) | `TrcoAcctInfo` | `TrcoAcctInfoRepository` | PK `(BIZ_KEY, TRCO_KEY, TRCO_ACCT_KEY)`. |
| `wabooks.entity.TrcoOvrsInfm` | [TrcoOvrsInfm.java:26-64](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoOvrsInfm.java) | `TrcoOvrsInfm` | `TrcoOvrsInfmRepository` | PK `(BIZ_KEY, TRCO_KEY, TRCO_OVRS_KEY)`. |
| `wabooks.entity.PgroBsacLnknInfm` | (existing) | `PgroBsacLnknInfm` | `PgroBsacLnknInfmRepository` | Refactor reuses if it exists; otherwise adds a minimal mapping just for the `B` + `S` group seed at create-time. |
| `wabooks.entity.TrcoInfoFile` | [TrcoInfoFile.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoInfoFile.java) | — | — | **NOT PORTED** — `TRCO_INFO_FILE` out of scope. |
| `wabooks.entity.TrcoRspbInfm` | [TrcoRspbInfm.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoRspbInfm.java) | — | — | **NOT PORTED** — `TRCO_RSPB_INFM` out of scope. |
| `wabooks.entity.TrcoMdph` | [TrcoMdph.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoMdph.java) | (deferred) | — | Legacy `TrcoInfmDAO` writes a `TRCO_MDPH` history row on every create/update. Refactor: deferred — audit lives in `AUDIT_LOGS_INFM` via the global aspect; per-table history (`*_MDPH`) is a Sprint 2+ concern (legacy triggers / shadow tables are documented as deferred in [`wecms-api/CLAUDE.md` § Legacy Schema Parity Rule](../../../CLAUDE.md)). |

| Legacy DAO | File | Refactor counterpart |
|---|---|---|
| `wabooks.dao.TrcoInfmDAO` | [TrcoInfmDAO.java:29-120](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/TrcoInfmDAO.java) | `ContactsService` (orchestrates) + `TrcoInfmRepository` (persists) |
| `wabooks.dao.TrcoAddrInfmDAO` | [TrcoAddrInfmDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/TrcoAddrInfmDAO.java) | `TrcoAddrInfmRepository` (`upsert` semantics in `ContactsService`) |
| `wabooks.dao.TrcoAcctInfoDAO` | [TrcoAcctInfoDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/TrcoAcctInfoDAO.java) | `TrcoAcctInfoRepository` |
| `wabooks.dao.TrcoOvrsInfmDAO` | [TrcoOvrsInfmDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/TrcoOvrsInfmDAO.java) | `TrcoOvrsInfmRepository` |
| `wabooks.dao.PgroBsacLnknInfmDAO` | [PgroBsacLnknInfmDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/PgroBsacLnknInfmDAO.java) | `PgroBsacLnknInfmRepository` |
| `wabooks.helper.TrcoHelper` | [TrcoHelper.java](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/TrcoHelper.java) | `ContactsValidator` (in-package — Bean-Validation + business-rule checks) |
| `wabooks.helper.ValidationHelper` | [ValidationHelper.java](../../../../WECMS%20DEV/WECMS/src/wabooks/helper/ValidationHelper.java) | Spring Bean Validation (`@NotBlank`, `@Size`, `@Pattern`) on the DTO records |

## 3. IDO query mapping

| Legacy IDO | File:line | Refactor counterpart |
|---|---|---|
| `TRCO_INFM_R016` (list, JSON-aggregated children) | [IDO.TRCO_INFM_R016.xml:24-103](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016.xml) | Spring Data JPA — `TrcoInfmRepository.findByBizKeyAndHandTrcoKeyIsNotNullAndTrcoSttsIsNullOrTrcoSttsNot...` (named query or `Specification`). Sub-rows fetched per row OR batch-fetched via a single `WHERE TRCO_KEY IN (...)` query in `ContactsService.list(...)`. Bank-name resolved via `BankCdResolver`. |
| `TRCO_INFM_R016_CNT` (count) | [IDO.TRCO_INFM_R016_CNT.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016_CNT.xml) | `Page<TrcoInfm>.getTotalElements()` |
| `TRCO_INFM_R021_CNT` (in-use guard for trash) | [IDO.TRCO_INFM_R021_CNT.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R021_CNT.xml) | `AcctTrnsRcvPayInfmRepository.existsByBizKeyAndTrcoKey(...)` — guards transition to `TRCO_STTS='C'`. (`ACCT_TRNS_RCV_PAY_INFM` is in PROD active list.) |
| Default IDOs (`TRCO_INFM_C001` / `_U001` / `_R001`) | (Jex Studio auto-generated) | Spring Data JPA + entity setters; no XML. |

## 4. DTO field mapping (wire snake_case ↔ JEX UPPER_SNAKE)

The contracts in this section are the exact field names that ship on the wire. Validation rules and Java field names live in [`docs/refactor-wecms/backend/contacts-design.md`](../business-rules/contacts-design.md).

### 4.1 Wire ← `comm_0184_001_r001` (list response item)

| Refactor wire | Java field | Legacy JEX (REC sub-rule) | Notes |
|---|---|---|---|
| `trco_key` | `trcoKey` | `TRCO_KEY` | PK |
| `hand_trco_key` | `handTrcoKey` | `HAND_TRCO_KEY` | User-facing contact code |
| `trco_cmnm` | `trcoCmnm` | `TRCO_CMNM` | Local company name |
| `trco_cmenm` | `trcoCmenm` | `TRCO_CMENM` | English company name |
| `trco_tax_id` | `trcoTaxId` | `TRCO_TAX_ID` | |
| `trco_type` | `trcoType` | `TRCO_TYPE` | `L` \| `O` (verbatim legacy code, see `PrdtType`-style guidance) |
| `addresses[]` | `addresses` | `REC1_1` | Array of `AddressRow` |
| `accounts[]` | `accounts` | `REC1_2` | Array of `AccountRow` |
| `overseas_beneficiaries[]` | `overseasBeneficiaries` | `REC1_3` | Array of `OverseasRow` |
| `mod_dtm` | `modDtm` | — | New — used for default sort (legacy used implicit) |

`AddressRow` (legacy `REC1_1`):

| Wire | Java | Legacy | Notes |
|---|---|---|---|
| `trco_addr_key` | `trcoAddrKey` | `TRCO_ADDR_KEY` | |
| `trco_bsun_post_adrs` | `trcoBsunPostAdrs` | `TRCO_BSUN_POST_ADRS` | |
| `disp_ord` | `dispOrd` | `DISP_ORD` | |
| `issu_dt` | `issuDt` | `ISSU_DT` | ISO `YYYY-MM-DD` on the wire |
| `due_dt` | `dueDt` | `DUE_DT` | Same |

`AccountRow` (legacy `REC1_2`):

| Wire | Java | Legacy | Notes |
|---|---|---|---|
| `trco_acct_key` | `trcoAcctKey` | `TRCO_ACCT_KEY` | |
| `napas_bank_cd` | `napasBankCd` | `NAPAS_BANK_CD` | |
| `citad_bank_cd` | `citadBankCd` | `CITAD_BANK_CD` | |
| `bank_nm` | `bankNm` | `BANK_NM` | Resolved server-side via `BankCdResolver` (replaces legacy `wb_get_napas_bank_nm_or_citad_bank_nm`) |
| `acct_no` | `acctNo` | `ACCT_NO` | |
| `acct_ccy_cd` | `acctCcyCd` | `ACCT_CCY_CD` | |
| `finc_mda_owner_nm` | `fincMdaOwnerNm` | `FINC_MDA_OWNER_NM` | |
| `owac_addr` | `owacAddr` | `OWAC_ADDR` | |
| `disp_ord` | `dispOrd` | `DISP_ORD` | |

`OverseasRow` (legacy `REC1_3`):

| Wire | Java | Legacy | Notes |
|---|---|---|---|
| `trco_ovrs_key` | `trcoOvrsKey` | `TRCO_OVRS_KEY` | |
| `bene_name` | `beneName` | `BENE_NAME` | |
| `bene_acct_no` | `beneAcctNo` | `BENE_ACCT_NO` | |
| `bic_code` | `bicCode` | `BIC_CODE` | |
| `bene_bank_name` | `beneBankName` | `BENE_BANK_NAME` | |
| `bene_bank_code` | `beneBankCode` | `BENE_BANK_CODE` | |
| `bene_addr` | `beneAddr` | `BENE_ADDR` | |

### 4.2 Wire ← `comm_0184_005_u001` (update request body)

Combines all legacy REC1–REC6 into a single nested object on `PUT`. Each sub-row carries a discriminator `op` so the service can dispatch upsert vs. delete in one pass.

```jsonc
{
  "hand_trco_key": "VENDOR_001",
  "trco_cmnm": "Công ty TNHH ABC",
  "trco_cmenm": "ABC Co. Ltd",
  "trco_tax_id": "0123456789",
  "trco_type": "L",
  "trco_stts": null,
  "addresses": [
    { "op": "upsert", "trco_addr_key": "1", "trco_bsun_post_adrs": "…", "disp_ord": 1, "due_dt": null },
    { "op": "delete", "trco_addr_key": "2" }
  ],
  "accounts": [
    { "op": "upsert", "trco_acct_key": "1", "bank_nm": "Vietcombank", "acct_no": "1234567",
      "napas_bank_cd": "01310001", "citad_bank_cd": null, "finc_mda_owner_nm": "ABC Co." }
  ],
  "accounts_to_delete": [ { "trco_acct_key": "2" } ],
  "overseas_beneficiaries": [],
  "overseas_to_delete": []
}
```

Legacy mapping:

- `addresses[].op` ↔ legacy `REC1[].STTS` (`U` → upsert, anything else → delete) — [comm_0184_005_u001_act.jsp:211-232](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp).
- `accounts[]` ↔ legacy `REC2`.
- `accounts_to_delete[]` ↔ legacy `REC4` (`{ TRCO_ACCT_KEY }`).
- `overseas_beneficiaries[]` ↔ legacy `REC5`.
- `overseas_to_delete[]` ↔ legacy `REC6`.

### 4.3 Wire ← list query params

| Wire query | Legacy WSVC field | Notes |
|---|---|---|
| `search` | `SRCH_WD` | ILIKE on `TRCO_CMNM`, `TRCO_CMENM`, `HAND_TRCO_KEY`. Memo join dropped. |
| `trco_type` | `TRCO_TYPE` | `L` \| `O` \| omitted |
| `page` | `PAGE_NO` | 1-based |
| `size` | `PAGE_PER_CNT` | `15 / 20 / 50 / 100` |
| `sort_field` | (`SortHelper` data-sort attr) | `HAND_TRCO_KEY` / `TRCO_CMNM` / `TRCO_TYPE` / `TRCO_TAX_ID` |
| `sort_direction` | (`SortHelper`) | `ASC` / `DESC` |

## 5. Frontend mapping (view JSP / TS → Next.js)

| Legacy JSP / TS | Refactor counterpart |
|---|---|
| `comm_0184_001_view.jsp` ([file](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp)) | `components/settings/contacts/ContactsPanel.tsx` |
| `comm_0184_001.ts` ([file](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0184_001.ts)) | `ContactsPanel.tsx` + `useContacts` hook in `hooks/contacts/useContacts.ts` (via shared `useApiData`) |
| `comm_0184_002` (Add popup) | `components/settings/contacts/ContactFormDialog.tsx` (shared shadcn `<Dialog>` per [`wecms-web/CLAUDE.md` § 3a feature-dialog rhythm](../../../../wecms-web/CLAUDE.md)), `mode="create"` |
| `comm_0184_005` (Edit popup) | `ContactFormDialog.tsx`, `mode="edit"` with `key={trcoKey}` re-seed |
| Delete confirm (legacy `_v2common.app.showMessage('WBC1098', …)` at [comm_0184_001.ts:130-135](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0184_001.ts)) | `useDialog().open({ type: "confirmation", … })` per [`wecms-web/CLAUDE.md` § Dialog buttons](../../../../wecms-web/CLAUDE.md) |
| `comm_0184_007` (Excel import popup) | — (deferred) |

## 6. ML code / i18n mapping

Refactor uses slug-namespaced keys; legacy ML codes remain only as cross-references. Full mapping in [`docs/refactor-wecms/features/contacts.md` § 9](../../features/settings/contacts.md).

| Legacy ML | Refactor key |
|---|---|
| `comm_0184_001_ml001` ("Contacts") | `settings.contacts.title` |
| `comm_0184_001_ml002` (subtitle) | `settings.contacts.subtitle` |
| `comm_0184_001_ml004` ("Add") | `settings.contacts.add` |
| `comm_0184_001_pl001` (search placeholder) | `settings.contacts.search_placeholder` |
| `comm_0184_001_pl002` ("Local") | `settings.contacts.filter.local` |
| `comm_0184_001_pl003` ("Overseas") | `settings.contacts.filter.overseas` |
| `comm_0184_001_ml005`–`ml009`, `pl007` (column headers) | `settings.contacts.column.*` |
| `comm_0184_001_j001` ("Edit") | `settings.contacts.row.edit` |
| `comm_0184_001_j002` ("Delete") | `settings.contacts.row.delete` |
| `comm_0184_001_j003`–`j005` (address tooltip) | `settings.contacts.address.*` |
| `comm_0184_001_j006` ("No Data Found") | `settings.contacts.empty` |
| `comm_0184_001_j007`–`j008` ("Local" / "Overseas" row labels) | `settings.contacts.location.*` |

## 7. Error code mapping

| Legacy `WbMessageCode` | Refactor `messages.properties` key |
|---|---|
| `WBE0003` (duplicate) — accounts at [comm_0184_005_u001_act.jsp:89](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) | `WBE_CONTACTS_ACCT_DUP` |
| `WBE0003` — overseas at [comm_0184_005_u001_act.jsp:128](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) | `WBE_CONTACTS_BENE_DUP` |
| `WBE0404` ([line 170](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)) | `WBE_CONTACTS_NOT_FOUND` |
| `WBE1190` ([line 189](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)) | `WBE_CONTACTS_IN_USE` |
| `WbMessageCode` from `TrcoHelper.validateTrcoHandCdDuplicate` | `WBE_CONTACTS_HAND_KEY_DUP` |
| `WbMessageCode` from `TrcoHelper.validateTrcoTaxDuplicate` | `WBE_CONTACTS_TAX_ID_DUP` |
| Bean-validation failure | `WBE_VALIDATION` (global, already wired) |
| `WBC1098` (legacy "Are you sure?" confirm dialog, [comm_0184_001.ts:130](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0184_001.ts)) | Client-side only — `dialog.confirm_description.delete_contact` |

## 8. Migration sequencing (file-by-file)

This is the order the backend MR should land files in to keep the build green at every commit.

1. **Flyway migration** `V{n}__contacts_schema.sql` — see [`docs/refactor-wecms/database/contacts-schema.md`](../database/contacts-schema.md).
2. **Flyway seed** `V{n+1}__contacts_seed.sql` — copy ≤30 most-recent rows per table from DEV (`BIZ_KEY = 'VN_VNM0004_000001'`), per the Data Migration Rule in [`wecms-api/CLAUDE.md` § "Data Migration Rule"](../../../CLAUDE.md).
3. **Flyway View Setting seed** `V{n+2}__contacts_view_setting_seed.sql` — `INSERT INTO SYS_SCR_OPTION_WECMS` with `WEB_SVC_ID='contacts', DYM_AREA_ID='default'`.
4. **Entities** (`TrcoInfm`, `TrcoAddrInfm`, `TrcoAcctInfo`, `TrcoOvrsInfm`, `TrcoInfmId`, …) + IDs.
5. **Repositories** (Spring Data JPA).
6. **DTOs** (records) under `domain/setting/contacts/dto/`.
7. **ContactsValidator** — duplicate checks + business-rule checks.
8. **ContactsService** — read + write methods, `@Transactional`.
9. **ContactsController** — REST endpoints.
10. **Error codes** in `messages.properties` (+ `_en` / `_ko` / `_vi`).
11. **Unit + slice tests** — `@WebMvcTest` for the controller, `@DataJpaTest` for repository derivation.
12. **Frontend** wire types in [`lib/types/api.ts`](../../../../wecms-web/lib/types/api.ts) → mapper in [`lib/api/mappers.ts`](../../../../wecms-web/lib/api/mappers.ts) → service in [`lib/api/contacts.ts`](../../../../wecms-web/lib/api) → register `contacts` in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts) → add to `<SettingsNav>` → write `ContactsPanel.tsx` → write `ContactFormDialog.tsx`.

Each numbered step lands on its own commit; the backend + frontend split into two MRs (backend MR ships steps 1–11, frontend MR ships step 12 against an already-deployed local backend), per the standard refactor workflow.

## 9. Out-of-scope cross-checks

For traceability, the legacy artifacts that are **deliberately not** in refactor scope and the reason:

- `TRCO_INFO_FILE` — table not in PROD Active Table List (see [`wecms-api/CLAUDE.md` § PROD DB Table Scope](../../../CLAUDE.md)).
- `TRCO_RSPB_INFM` — table not in PROD Active Table List.
- `MEMO_INFM` — table not in PROD Active Table List. The legacy memo-search join at [comm_0184_001_r001_act.jsp:110](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_r001_act.jsp) is dropped from the refactor query.
- `TAX_AGENT_INFM` — table not in PROD Active Table List. Legacy WSVCs `comm_0184_005_c011`, `comm_0184_006_*` and the `WTA_USER_ALLOW_YN` ext flag are not ported.
- `wb_get_translate_text(...)` — legacy SQL function for in-row i18n. Refactor leaves search against the raw column; resolver-side translation happens in Java if/when a column ever needs it.
- `wb_get_napas_bank_nm_or_citad_bank_nm(...)` — replaced by `BankCdResolver` per the rule in [`wecms-api/CLAUDE.md` § "DB Function / Legacy Function Policy"](../../../CLAUDE.md).
- `SeqHelper.getNextVal(... "trco_key")` → legacy `wb_get_next_val('trco_infm','trco_key', …)`. Refactor implements `TrcoKeyGeneratorService` in Java (no new branches on `wb_get_next_val`). See OQ-4 in [`docs/refactor-wecms/features/contacts.md` § 14](../../features/settings/contacts.md).

## 10. Open questions

All migration-level open questions are consolidated in the feature spec § 14 (OQ-1 through OQ-8). Do not re-litigate them in this file — pull them from there.
