# Contacts — Refactor Feature Spec
> **📁 Primary home moved (2026-06-08):** the canonical backend Contacts docs now live in the folder
> [contacts/](contacts/README.md) (README · business-flow · functional-specification · database-mapping
> · interface-specification · source-code-index · migration-guide · test-scenarios · refactor-gap-analysis),
> which reconciles the stale §§ below against shipped code and records the **GAP-01 export closure
> (2026-06-08)**. This single-file spec is **preserved** as the original design narrative.
>
> **Status (verified 2026-06-02): Implemented — code is authoritative.** This doc predates the shipped implementation and describes design-stage decisions that were resolved the OTHER way. Where it conflicts with `domain/setting/contacts/`, the code wins. Specifically: TRCO_KEY is allocated via the `wb_get_next_val('trco_infm','trco_key')` branch through `LegacyFunctionRepository.nextTrcoKey` (there is **no** Java `TrcoKeyGeneratorService`); `TRCO_INFO_FILE` **is** in scope (attachments shipped via `ContactsFileService`); bank-name resolution uses the ported `wb_get_napas_bank_nm_or_citad_bank_nm` via `LegacyFunctionRepository.resolveNapasOrCitadBankName` (**not** `BankCdResolver`). See `wecms-api/CLAUDE.md` → Feature Status (contacts) + "Surviving legacy functions". The §§ on key-generation / bank-resolution / file-scope below are stale and flagged for a follow-up rewrite.

| Field | Value |
|---|---|
| System | Refactor (Spring Boot + Next.js) |
| Slug | `contacts` |
| JEX counterpart | `docs/jex/features/contacts.md` (Needs Confirmation) (to be created if not present) |
| Module | Settings → Contacts |
| Status | Implemented (shipped) — see wecms-api/CLAUDE.md → Feature Status (contacts) |
| Spec author | Claude Code (R&D planning), Sonny |

> **Identifier rule.** Every refactor surface — Next.js route, Spring REST URI, Java package, DTO names, frontend feature folder, i18n key prefix, log tag, View Setting `screenId`, and `AUDIT_LOGS_INFM.WSVC_ID` — uses the kebab-case slug `contacts`. Legacy JEX WSVC ids (`comm_0184_001`, `comm_0184_005_u001`, …) appear in this doc **only** as JEX file:line citations or in migration-traceability prose. See workspace [`CLAUDE.md`](../../../../CLAUDE.md) § Cross-Tree Rules and [`wecms-api/CLAUDE.md`](../../../CLAUDE.md) § "Refactor identifier naming".

---

## 1. Functional summary

A tenant-scoped contacts directory used to maintain trading-partner records that the company transacts with (Local Vietnamese partners and Overseas partners). Reached from the **Settings** modal in the global header; renders as a Settings panel inside [`<SettingsModal>`](../../../../wecms-web/components/settings/SettingsModal.tsx) (see frontend design doc).

Capabilities mirrored from JEX:

- **List** with search, type filter (Local / Overseas), pagination, sort.
- **Add** a new contact (Local or Overseas form variant).
- **Edit** an existing contact (master info + addresses + bank accounts + overseas beneficiaries).
- **Delete** — soft delete that moves the contact to "Trash" (`TRCO_STTS = 'C'`). Bulk-delete on selection.
- **View** address tooltip / second-address (DUE_DT) on the row hover.

Excluded from the refactor scope (out of scope, see § 12):

- Excel import (`comm_0184_007_*`)
- Excel export (`comm_0184_001_r006_excel`)
- File attachments (`TRCO_INFO_FILE` not in PROD active table list)
- Responsible-person sub-record (`TRCO_RSPB_INFM` not in PROD active table list)
- Memo search via `MEMO_INFM` (not in PROD active table list)
- Tax-agent linkage (`comm_0184_005_c011`, `comm_0184_006_*`) — depends on tables not in active list
- "Duplicate contact" flow (`comm_0184_002_c002`)
- Address-list / account-list popups (`comm_0184_003_r001`, `comm_0184_004_*`)

Scope is intentionally a clean clone of the **core Local/Overseas CRUD path** under the PROD Active Table List set — `TRCO_INFM`, `TRCO_ADDR_INFM`, `TRCO_ACCT_INFO`, `TRCO_OVRS_INFM`, `PGRO_BSAC_LNKN_INFM`. Any future feature MR can lift one of the excluded items above behind a fresh PO decision.

## 2. JEX entry points (read-only reference)

| JEX WSVC | Role | File:line |
|---|---|---|
| `comm_0184_001` (view) | Contacts list popup | [WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp:74-204](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp) |
| `comm_0184_001_r001` | List read (paged + filtered) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_r001_act.jsp:38-253](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_r001_act.jsp) |
| `comm_0184_001_u001` | Bulk soft-delete (move to trash) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_u001_act.jsp:33-87](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_u001_act.jsp) |
| `comm_0184_002_c001` | Create contact (master + addresses + accounts + overseas) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp:59-250](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp) |
| `comm_0184_005_u001` | Edit contact (full update) | [WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp:64-398](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) |
| `comm_0184_005_r001` | Read single contact (for edit pre-fill) | [WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_r001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_r001.xml) |
| `comm_0184_005_d001` | Delete a single bank account row | [WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_d001.xml:32-40](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_d001.xml) |
| `TRCO_INFM_R016` (IDO) | List SELECT with JSON-aggregated children | [WECMS DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016.xml:24-103](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016.xml) |
| Frontend TS | jQuery list + popup orchestration | [WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0184_001.ts:43-393](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0184_001.ts) |

Entities referenced (all in PROD Active Table List):

- `TrcoInfm` — [WECMS DEV/WECMS/src/wabooks/entity/TrcoInfm.java:33-127](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoInfm.java)
- `TrcoInfmPk` — [WECMS DEV/WECMS/src/wabooks/entity/TrcoInfmPk.java:20-50](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoInfmPk.java)
- `TrcoAddrInfm` — [WECMS DEV/WECMS/src/wabooks/entity/TrcoAddrInfm.java:32-67](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoAddrInfm.java)
- `TrcoAcctInfo` — [WECMS DEV/WECMS/src/wabooks/entity/TrcoAcctInfo.java:28-99](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoAcctInfo.java)
- `TrcoOvrsInfm` — [WECMS DEV/WECMS/src/wabooks/entity/TrcoOvrsInfm.java:26-64](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/TrcoOvrsInfm.java)

## 3. Data model touched

| Table | Role | PROD active list? |
|---|---|---|
| `TRCO_INFM` | Master row — one per contact | Yes |
| `TRCO_ADDR_INFM` | Addresses (current + previous) | Yes |
| `TRCO_ACCT_INFO` | Bank accounts (Local contacts) | Yes |
| `TRCO_OVRS_INFM` | Overseas beneficiary accounts | Yes |
| `PGRO_BSAC_LNKN_INFM` | Group membership rows seeded with `B` + `S` on create — cited at [comm_0184_002_c001_act.jsp:131-147](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp) | Yes |
| `TRCO_MDPH` | Audit-history twin of `TRCO_INFM` written by `TrcoInfmDAO.create/update` | Yes (read-only in refactor; behaviour follows JEX DAO pattern) |
| `TRCO_INFO_FILE` | File attachments | **No — out of scope** |
| `TRCO_RSPB_INFM` | Responsible persons | **No — out of scope** |
| `MEMO_INFM` | Memo search target | **No — out of scope** |

Detailed schema in [`docs/refactor-wecms/database/contacts-schema.md`](../../references/database/contacts-schema.md).

## 4. List view — behaviour

### 4.1 Filtering

The JEX list filter is built dynamically in [comm_0184_001_r001_act.jsp:38-128](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_r001_act.jsp). Refactor mirrors:

- **Tenant**: always filtered to `BIZ_KEY = TenantContext.currentBizKey()` (legacy line 73, 82–83).
- **Soft-delete**: hides rows where `TRCO_STTS = 'C'` (legacy line 75, 86).
- **Managed contacts only**: `HAND_TRCO_KEY IS NOT NULL` (legacy line 76, 87).
- **Search (`q`)**: ILIKE search on `TRCO_CMNM`, `TRCO_CMENM`, `HAND_TRCO_KEY` (legacy lines 96–108). The legacy `wb_get_translate_text(...)` wrapper and the `memo_infm` join (legacy line 110) are **dropped** in the refactor — `MEMO_INFM` is out of scope; the refactor searches the raw column text.
- **TRCO_TYPE filter**: optional `L` (Local) / `O` (Overseas) (legacy lines 115–125).
- **Group filter**: the legacy `GRP_CD` branch (legacy lines 43–71) including the user-scoped custom-group path is **not** ported in the refactor scope — group selection happens via the existing legacy menu (`comm_0184` is invoked with `GRP_CD` from other JEX screens). The refactor `contacts` panel exposes a single "managed contacts" list. A future MR can add the group filter if the PO requests it.

### 4.2 Sort

Legacy table columns with `data-sort` ([comm_0184_001_view.jsp:155-172](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp)): `HAND_TRCO_KEY`, `TRCO_CMNM`, `TRCO_TYPE`, `TRCO_TAX_ID`, `BANK_NM`, `TRCO_BSUN_POST_ADRS`.

Refactor uses the common-sort whitelist contract (see [wecms-api/CLAUDE.md § Common table sort](../../../CLAUDE.md)). Sortable FIELD_IDs:

| FIELD_ID | Entity property |
|---|---|
| `HAND_TRCO_KEY` | `handTrcoKey` |
| `TRCO_CMNM` | `taxInfo.cmnm` |
| `TRCO_TYPE` | `trcoType` |
| `TRCO_TAX_ID` | `taxInfo.taxId` |
| `BANK_NM` | First-account `bankNm` — **not sortable** in refactor scope (sub-table). Seeded `SORT_YN='N'` per the View Setting rule in [wecms-api/CLAUDE.md § Common table sort](../../../CLAUDE.md). |
| `TRCO_BSUN_POST_ADRS` | First-address `trcoBsunPostAdrs` — same reason, `SORT_YN='N'`. |

Default ORDER BY: `mod_dtm DESC, trco_key DESC` — mirrors legacy "latest-edited first" implicit ordering (legacy uses `SortHelper.getSortDynamic` without an explicit default; refactor picks a deterministic explicit default to satisfy [`wecms-api/CLAUDE.md` Common-sort default-ORDER-BY rule](../../../CLAUDE.md)).

### 4.3 Pagination

`page_no` / `page_per_cnt` query params, matching the legacy WSVC fields ([WSVC.comm_0184_001_r001.xml:36-37](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_001_r001.xml)). Page size options seeded by the View Setting `<Pagination>` shared hook: `15 · 20 · 50 · 100` (legacy values at [comm_0184_001_view.jsp:189-192](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp)).

### 4.4 Row shape (response)

JSON-aggregated children in the legacy IDO ([IDO.TRCO_INFM_R016.xml:44-88](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016.xml)) — the refactor returns the same nested shape on the wire, snake_case (see § 7).

```
contact
├── trco_key
├── hand_trco_key                  ← user-facing contact code
├── trco_cmnm  / trco_cmenm        ← local & English company names
├── trco_tax_id                    ← tax number
├── trco_type                      ← 'L' | 'O'
├── trco_stts                      ← null | 'C' (deleted)
├── addresses[]                    ← REC1_1
│   ├── trco_addr_key (1 = current, 2 = previous)
│   ├── trco_bsun_post_adrs
│   ├── disp_ord
│   ├── issu_dt / due_dt
├── accounts[]                     ← REC1_2 (Local)
│   ├── trco_acct_key
│   ├── napas_bank_cd | citad_bank_cd
│   ├── bank_nm  (resolved server-side via bank registry)
│   ├── acct_no
│   ├── finc_mda_owner_nm
│   ├── owac_addr
├── overseas_beneficiaries[]       ← REC1_3 (Overseas)
│   ├── trco_ovrs_key
│   ├── bene_name / bene_acct_no / bic_code
│   ├── bene_bank_name / bene_addr
```

The legacy IDO calls `wb_get_napas_bank_nm_or_citad_bank_nm(...)` ([IDO.TRCO_INFM_R016.xml:63](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016.xml)) to resolve the bank name. The refactor replaces this DB function call with the existing `BankCdResolver` ([wecms-api/CLAUDE.md § Reference data](../../../CLAUDE.md)) — per the "no new DB-function dependencies" rule, the resolution happens in Java service code.

## 5. Add / Edit form — behaviour

### 5.1 Required fields (master)

Mirror [comm_0184_002_c001_act.jsp:59-87](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp) and [comm_0184_005_u001_act.jsp:65-71](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp):

- `hand_trco_key` (user-facing code, max 20)
- `trco_cmnm` (company name, max 200)
- `trco_cmenm` (English company name, max 200)
- `trco_type` (`L` | `O`)
- `trco_tax_id` (tax number, max 40) — required-when-`L` per JEX legacy comment (currently commented out at create line 63 but enforced via duplicate check at line 205).

### 5.2 Duplicate checks (server-side)

- `HAND_TRCO_KEY` must be unique per tenant — legacy call `TrcoHelper.validateTrcoHandCdDuplicate` ([comm_0184_002_c001_act.jsp:203](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp); [comm_0184_005_u001_act.jsp:174-176](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)). Refactor: repository `existsByBizKeyAndHandTrcoKey(...)` + `BusinessException("WBE_CONTACTS_HAND_KEY_DUP")`.
- `TRCO_TAX_ID` must be unique per tenant — legacy call `TrcoHelper.validateTrcoTaxDuplicate` ([comm_0184_002_c001_act.jsp:205](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_002_c001_act.jsp); [comm_0184_005_u001_act.jsp:178-181](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)). Refactor: `existsByBizKeyAndTaxId(...)` + `BusinessException("WBE_CONTACTS_TAX_ID_DUP")`.

### 5.3 Address rules (`addresses[]` = legacy `REC1`)

- `STTS` field per row: `U` (upsert) or anything else (delete) — legacy [comm_0184_005_u001_act.jsp:211-233](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp).
- Refactor maps this onto a discriminator `op: 'upsert' | 'delete'` on each address element; the service performs the same dispatch.
- `disp_ord` 1 = current address, 2 = previous (legacy TS render at [comm_0184_001.ts:324-330](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0184_001.ts)).
- `issu_dt` defaults to `DateTimeUtils.today()` on insert (legacy [comm_0184_005_u001_act.jsp:218](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)).

### 5.4 Account rules (`accounts[]` = legacy `REC2`)

- Duplicate `(BANK_NM, ACCT_NO)` rejected with `WBE_CONTACTS_ACCT_DUP` (mirrors legacy `WbMessageCode.WBE0003` at [comm_0184_005_u001_act.jsp:79-92](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)).
- When `napas_bank_cd` OR `citad_bank_cd` is provided AND the contact is not being trashed (`trco_stts != 'C'`), `BANK_NM` + `ACCT_NO` + `TRCO_ACCT_KEY` are required ([line 93-97, 141-146](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)).
- For Local contacts (`trco_type='L'`): at least one bank account required (`BANK_NM`) — legacy [comm_0184_005_u001_act.jsp:101-116](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp).
- `disp_ord` defaults to `1` on update (legacy [line 249](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)).

### 5.5 Overseas-beneficiary rules (`overseas_beneficiaries[]` = legacy `REC5`)

- Duplicate `(BENE_ACCT_NO, BIC_CODE)` rejected with `WBE_CONTACTS_BENE_DUP` (legacy [comm_0184_005_u001_act.jsp:118-133](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)).

### 5.6 Bulk-delete operations within the same edit transaction

Legacy edit `_u001` accepts four "delete" sub-records (`REC3` files, `REC4` accounts, `REC6` overseas) — see [comm_0184_005_u001_act.jsp:371-392](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp). Refactor scope keeps **only** `REC4` (account deletes) and `REC6` (overseas deletes); `REC3` (file delete) is dropped because `TRCO_INFO_FILE` is out of scope.

### 5.7 Transaction boundary

Legacy wraps the whole update in `idoCon.beginTransaction()` / `endTransaction()` at [comm_0184_005_u001_act.jsp:348-395](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp). Refactor: single `@Transactional` method on `ContactsService.update(...)`.

## 6. Delete — behaviour

- **Soft-delete (move to Trash)** — legacy `comm_0184_001_u001` writes `TRCO_STTS = 'C'` via `TrcoInfmDAO.updateTrcoStts(...)` ([comm_0184_001_u001_act.jsp:82-84](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_001_u001_act.jsp)). Refactor exposes `DELETE /api/v1/settings/contacts/{trcoKey}` (single) and `POST /api/v1/settings/contacts/bulk-delete` (batched). Both set `TRCO_STTS = 'C'` and stamp `MOD_USER`/`MOD_DTM`.
- **Hard-delete + restore** — not in scope. Trash recovery happens through the `GRP_CD = 'DEL'` filter in legacy; refactor lists only managed (`HAND_TRCO_KEY IS NOT NULL`) and non-deleted (`coalesce(TRCO_STTS,'') <> 'C'`) rows.
- **Single bank-account delete** — `comm_0184_005_d001` ([WSVC.comm_0184_005_d001.xml:32-40](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_005_d001.xml)) is folded into the edit PATCH via `accounts_to_delete[]` in the same request body — single transaction, no separate endpoint.

## 7. API surface (refactor)

Detail in [`docs/refactor-wecms/backend/contacts-design.md`](../../references/business-rules/contacts-design.md). Summary:

| Method | URI | Purpose |
|---|---|---|
| `GET` | `/api/v1/settings/contacts` | List (paged, search, sort, trco_type filter) |
| `GET` | `/api/v1/settings/contacts/{trcoKey}` | Read one (full nested shape for edit) |
| `POST` | `/api/v1/settings/contacts` | Create |
| `PUT` | `/api/v1/settings/contacts/{trcoKey}` | Update (master + nested upserts + nested deletes in one tx) |
| `DELETE` | `/api/v1/settings/contacts/{trcoKey}` | Soft-delete one |
| `POST` | `/api/v1/settings/contacts/bulk-delete` | Soft-delete many (body: `{ trco_keys: string[] }`) |

All endpoints are JWT-protected (the global rule at [`wecms-api/CLAUDE.md` § "Auth Rules" → SecurityConfig](../../../CLAUDE.md)). Tenant scoping is always from `TenantContext.currentBizKey()` — request body / query params **never** carry `biz_key`.

## 8. Permissions & audit

- Login required (`<login>Y</login>` on every legacy WSVC).
- All write operations carry `mntLogYn=Y` in legacy WSVC. Refactor: every mutation is wrapped by the existing global audit aspect (see § Reuse), writing one `AUDIT_LOGS_INFM` row per request with `WSVC_ID = 'contacts'` (refactor slug, per the workspace [`CLAUDE.md`](../../../../CLAUDE.md) audit-WSVC override locked 2026-05-09).
- Authority: the legacy `NORM_USER_ONLY_YN = Y` flag (e.g. [WSVC.comm_0184_001_r001.xml:26](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0184_001_r001.xml)) restricts the popup to normal users (not WTA / tax-agent users). Refactor: enforced by the existing `JwtAuthenticationFilter` populating `authGrCd` — `contacts` requires `ROLE_USER` or `ROLE_ADMIN` (i.e. not anonymous). No `@PreAuthorize` gate today; the future role-gated MR (referenced in [`wecms-api/CLAUDE.md`](../../../CLAUDE.md)) will pin the slugged endpoint when it lands.

## 9. i18n keys (refactor namespace)

The legacy ML codes (`comm_0184_001_ml001`–`pl007`, `j001`–`j008`) are **not** ported verbatim — per the [naming rule](../../../../CLAUDE.md), the refactor uses slug-based keys. Mapping:

| Surface | Refactor key | Legacy ML reference |
|---|---|---|
| Panel title "Contacts" | `settings.contacts.title` | [view.jsp:78 `comm_0184_001_ml001`](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0184_001_view.jsp) |
| Panel subtitle | `settings.contacts.subtitle` | view.jsp:79 `comm_0184_001_ml002` |
| "Add" button | `settings.contacts.add` | view.jsp:129 `comm_0184_001_ml004` |
| Search placeholder | `settings.contacts.search_placeholder` | view.jsp:90 `comm_0184_001_pl001` |
| Filter chip "Local" | `settings.contacts.filter.local` | view.jsp:96 `comm_0184_001_pl002` |
| Filter chip "Overseas" | `settings.contacts.filter.overseas` | view.jsp:100 `comm_0184_001_pl003` |
| Column: Contact code | `settings.contacts.column.hand_trco_key` | view.jsp:156 `comm_0184_001_ml005` |
| Column: Company name | `settings.contacts.column.company_name` | view.jsp:159 `comm_0184_001_ml006` |
| Column: Location | `settings.contacts.column.location` | view.jsp:162 `comm_0184_001_pl007` |
| Column: Tax ID | `settings.contacts.column.tax_id` | view.jsp:165 `comm_0184_001_ml007` |
| Column: Bank | `settings.contacts.column.bank` | view.jsp:168 `comm_0184_001_ml008` |
| Column: Address | `settings.contacts.column.address` | view.jsp:171 `comm_0184_001_ml009` |
| Row action: Edit | `settings.contacts.row.edit` | ts:387 `comm_0184_001_j001` |
| Row action: Delete | `settings.contacts.row.delete` | ts:388 `comm_0184_001_j002` |
| Address tooltip "(Current)" | `settings.contacts.address.current` | ts:353 `comm_0184_001_j004` |
| Address tooltip "(Old)" | `settings.contacts.address.old` | ts:365 `comm_0184_001_j003` |
| "Default" badge in address tooltip | `settings.contacts.address.default_badge` | ts:354 `comm_0184_001_j005` |
| Empty state | `settings.contacts.empty` | ts:232 `comm_0184_001_j006` |
| Location label "Local" (row) | `settings.contacts.location.local` | ts:247 `comm_0184_001_j007` |
| Location label "Overseas" (row) | `settings.contacts.location.overseas` | ts:248 `comm_0184_001_j008` |
| TRCO_TYPE enum on wire | `settings.contacts.trco_type.L` / `.O` | (none — generated for `PrdtType`-style frontend rendering) |

EN / KO / VI lands in [`wecms-web/locales/{en,ko,vi}.json`](../../../../wecms-web/locales) in the same MR. KM is omitted (per the workspace policy — no KM translations on the refactor side).

## 10. Error codes (new)

Add to [`wecms-api/src/main/resources/messages.properties`](../../../src/main/resources/messages.properties) and `messages_en.properties` / `messages_ko.properties` / `messages_vi.properties`:

| Code | Meaning | Legacy mapping |
|---|---|---|
| `WBE_CONTACTS_HAND_KEY_DUP` | Contact code already in use for this tenant | `WbMessageCode` thrown by `TrcoHelper.validateTrcoHandCdDuplicate` |
| `WBE_CONTACTS_TAX_ID_DUP` | Tax ID already in use for this tenant | `WbMessageCode` thrown by `TrcoHelper.validateTrcoTaxDuplicate` |
| `WBE_CONTACTS_ACCT_DUP` | Duplicate `(BANK_NM, ACCT_NO)` within the same contact | `WbMessageCode.WBE0003` at [comm_0184_005_u001_act.jsp:89](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) |
| `WBE_CONTACTS_BENE_DUP` | Duplicate `(BENE_ACCT_NO, BIC_CODE)` within the same contact | `WbMessageCode.WBE0003` at [comm_0184_005_u001_act.jsp:128](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) |
| `WBE_CONTACTS_NOT_FOUND` | Contact not found in tenant (single envelope merges "not found" + "wrong tenant") | `WbMessageCode.WBE0404` at [comm_0184_005_u001_act.jsp:170](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) |
| `WBE_CONTACTS_LOCAL_BANK_REQUIRED` | Local contact must have at least one bank account | [comm_0184_005_u001_act.jsp:103-115](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) |
| `WBE_CONTACTS_IN_USE` | Cannot trash a contact currently referenced by `ACCT_TRNS_RCV_PAY_INFM` | `WbMessageCode.WBE1190` at [comm_0184_005_u001_act.jsp:189](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp) — guard on transition to `TRCO_STTS='C'` |

All standard validation errors (missing required field, bad enum value, etc.) fall through to `WBE_VALIDATION` per the global handler in [`wecms-api/CLAUDE.md` § Exception → response mapping](../../../CLAUDE.md).

## 11. View Setting

Contacts adopts the global View Setting feature (see [`docs/jex/features/view-setting.md`](../../../../docs/jex/features/view-setting.md) for behaviour). Seed migration registers the screen at:

- `WEB_SVC_ID = 'contacts'`
- `DYM_AREA_ID = 'default'`

Seed columns (in display order): `CHECKBOX` · `HAND_TRCO_KEY` · `TRCO_CMNM` · `TRCO_TYPE` · `TRCO_TAX_ID` · `BANK_NM` · `TRCO_BSUN_POST_ADRS` · `ETC`. `CHECKBOX` / `ETC` follow the special-FIELD_ID rule. `BANK_NM` and `TRCO_BSUN_POST_ADRS` ship `SORT_YN='N'` (sub-table columns not in the whitelist enum, per the rule in [`wecms-api/CLAUDE.md` § Common table sort](../../../CLAUDE.md)).

Frontend registers `contacts` in [`wecms-web/lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts) — see frontend doc.

## 12. Excluded from refactor scope (deferred or never)

| Item | Reason | Reopen condition |
|---|---|---|
| Excel import (`comm_0184_007_*`) | Bulk-import is a follow-on; UI parity already complex. | PO request after MVP ships. |
| Excel export (`comm_0184_001_r006_excel`) | Use the shared `common/export` pipeline ([`wecms-api/CLAUDE.md` § Excel export](../../../CLAUDE.md)) in a follow-up MR — register a `ContactsExportProvider` keyed on `screenId = 'contacts'`. | Next stabilisation pass after CRUD lands. |
| File attachments (`TRCO_INFO_FILE`) | Table not in PROD Active Table List. | Workspace `CLAUDE.md` widening decision. |
| Responsible persons (`TRCO_RSPB_INFM`) | Table not in PROD Active Table List. | Same. |
| Memo search (`MEMO_INFM` join in list WHERE) | Table not in PROD Active Table List. | Same. |
| Tax-agent linkage (`comm_0184_005_c011`, `comm_0184_006_*`) | Depends on `TAX_AGENT_INFM` not in PROD Active Table List + WeCMS does not target tax-agent users (`NORM_USER_ONLY_YN=Y` everywhere). | Never within `wecms-api` scope. |
| "Duplicate contact" (`comm_0184_002_c002`) | Convenience action; not core CRUD. | PO request. |
| Address-list / account-list sub-popups (`comm_0184_003`, `_004`) | Refactor renders all sub-rows inline within the edit form. | Never (subsumed). |
| Trash recovery (`GRP_CD='DEL'` branch) | Not requested for MVP; soft-delete is one-way until restored by DBA. | PO request. |
| Group filter (`GRP_CD` SALE/BUY/ETC/custom) | Group is set by other JEX screens (Payment, Sales) at create-time; the Settings list view never exposed a group selector. | If a future Settings UX adds group tabs. |

## 13. Reuse manifest (cross-stack)

Backend reuse (existing in `wecms-api/`):

- `common/response/ApiResponse` — envelope wrapper.
- `common/exception/BusinessException` + `GlobalExceptionHandler` — error funnel.
- `common/entity/BaseEntity` — REG/MOD audit columns.
- `common/tenant/TenantContext` — current `BIZ_KEY` + `userId`.
- `common/i18n/MessageResolver` + `messages*.properties` — locale resolution.
- `common/web/SortDirection` + `SortableField` — common-sort primitives.
- `domain/setting/` — View Setting (`SYS_SCR_OPTION_WECMS`, `USER_SCR_OPTION_WECMS`) — `contacts` is a seed-only adopter; no controller / service / DTO changes there.
- `common/security/JwtAuthenticationFilter` — JWT → SecurityContext + TenantContext.
- Bank-name resolver — `BankCdResolver` + `BankInfmRepository` (V20 seed) replaces legacy `wb_get_napas_bank_nm_or_citad_bank_nm`.

Frontend reuse (existing in `wecms-web/`):

- `<SettingsModal>` shell — see [`wecms-web/CLAUDE.md` § 3a](../../../../wecms-web/CLAUDE.md).
- `<SettingsNav>` — declarative addition of a "Contacts" entry in the top group.
- `<DataTableShell>` + `<TableHeaderCell>` + shadcn `<Table>` — list page composition.
- `<SearchInput>`, `<Pagination>` + `usePagination`, `<FilterDropdown>` — filter row.
- `<PageHeader>` + `<PageActionButtons>` — header chrome inside the Settings panel (matching Settings panel header rhythm).
- `<Dialog>` primitive + the §3a feature-dialog rhythm — the Add/Edit dialog uses this with `stackLevel={2}`.
- `useDialog()` system dialog — confirmation for soft-delete + success / error feedback.
- `lib/api/useApiData.ts` — list hook.
- `lib/axios.ts` `api` — HTTP client.
- `hooks/useExcelExport.ts` — for the future export MR; not in this scope but the seed already accommodates the screenId.
- `lib/types/api.ts` — wire types in lowercase snake_case.
- View Setting popover — `ViewSettingPopover` reads from the `contacts` seed automatically.
- Form layer — React Hook Form + Zod (`@hookform/resolvers`), already standard.

Database reuse:

- `BaseEntity` audit columns (TIMESTAMPTZ in local, VARCHAR(14) in legacy).
- `BIZ_KEY` tenant column (every row carries it; FK to `BUSI_INFM`).

## 14. Open questions for Sonny / PO

- **OQ-1 — Group filter scope.** Confirm that the Settings `contacts` panel is the "all managed contacts" view only and that group-scoped views (`GRP_CD = S/B/F/E/D` + per-user custom groups) are exclusively reached via the other JEX screens (Payment / Sales) which are out of refactor scope for now. If the PO wants a group filter exposed in Settings, this is a UI + WSVC-input add — call it out before backend lands.
- **OQ-2 — `WBE_CONTACTS_IN_USE` policy.** Legacy throws `WBE1190` when a contact is being trashed and is still referenced by `ACCT_TRNS_RCV_PAY_INFM` ([comm_0184_005_u001_act.jsp:184-191](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0184_005_u001_act.jsp)). Confirm whether the same guard runs on the new single-DELETE / bulk-delete endpoints (recommended yes, for parity).
- **OQ-3 — `TRCO_INQ_YN` default.** Legacy `TrcoInfmDAO.create` defaults `TRCO_INQ_YN = YES` ([TrcoInfmDAO.java:48](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/TrcoInfmDAO.java)). Is this still relevant in the refactor where the "inquiry / search" pattern is different? Default-true preserves parity; otherwise drop the field from the JPA entity.
- **OQ-4 — `TRCO_KEY` generation.** Legacy `SeqHelper.getNextVal(util, idoCon, getEntityName(), "trco_key")` ([TrcoInfmDAO.java:44](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/TrcoInfmDAO.java)) routes through `wb_get_next_val('trco_infm','trco_key',…)`. The refactor's "DB Function / Legacy Function Policy" rule says implement key-gen in Java for new write paths. Confirm: is **TRCO_KEY** a date+seq composite pattern or a plain monotonic sequence? Audit needed before backend MR (see `wecms-api/CLAUDE.md` § "Key-generation rule"). If composite, write a `TrcoKeyGeneratorService`; if plain, use a Postgres `SEQUENCE`.
- **OQ-5 — Trash recovery.** Confirm whether the MVP needs a "View deleted" toggle on the list (mapping to the legacy `GRP_CD='DEL'` branch), or if soft-deletes are DBA-recovered only.
- **OQ-6 — Audit `WSVC_ID`.** Per the workspace audit-DB-constant override (2026-05-09), refactor audit rows write `WSVC_ID = 'contacts'`. Confirm Sonny is OK with the legacy `comm_0184_*` audit-history queries returning nothing for refactor-written rows (no JEX consumer cares, since refactor is local-only R&D).
- **OQ-7 — Bank-name resolution.** Legacy IDO calls `wb_get_napas_bank_nm_or_citad_bank_nm(NAPAS_OR_CITAD_CD, LANG_CD)` ([IDO.TRCO_INFM_R016.xml:63](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.TRCO_INFM_R016.xml)). Today `BankCdResolver` resolves names from `BANK_INFM` (V20 seed) — confirm `BANK_INFM` carries both NAPAS and CITAD codes, or extend the registry seed.
- **OQ-8 — Address `disp_ord` convention.** Legacy TS uses `TRCO_ADDR_KEY` string equality (`"1"` / `"2"`) to discriminate current vs. previous ([comm_0184_001.ts:324-330](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0184_001.ts)). `TRCO_ADDR_KEY` is technically a generated key in the legacy DAO — relying on `"1"`/`"2"` looks like an implicit ordering assumption. Should the refactor use `disp_ord` instead (the database column the form already populates)? Recommended yes; flag for review.
