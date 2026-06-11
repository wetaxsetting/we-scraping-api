# Contacts — Migration Guide (JEX → Spring)

How each JEX layer of the `comm_0184_*` Contacts family became its Spring counterpart. JEX is
read-only functional reference; the refactor is a clean Spring rewrite informed by JEX behaviour, not
a port.

---

## Layer-by-layer

| JEX layer | JEX artifact | Refactor counterpart |
|---|---|---|
| List view | `comm_0184_001_view.jsp` (layer popup, Local/Overseas filter, Add/Delete/Excel) | `GET /api/v1/settings/contacts` → `ContactsService.list` → `ContactsPanel.tsx` |
| List query | IDO `TRCO_INFM_R016` (`json_agg` child sub-selects; `wb_get_napas_bank_nm_or_citad_bank_nm`) | `TrcoInfmRepository.search(...)` + batch child finders + per-code bank-name resolution in Java |
| Create | `comm_0184_002_c001_act.jsp` (+ IDO `TRCO_INFM_C001`; PGRO seed :131-147) | `POST /` → `ContactsService.create` → `persistNewContact` (PGRO `B`/`S` seed, `USER_ID='ALL'`) |
| Edit | `comm_0184_005_u001_act.jsp` (+ IDO `TRCO_INFM_U001`) | `PUT /{trcoKey}` → `ContactsService.update` (one tx, combined child upsert/delete) |
| Bulk soft-delete | `comm_0184_001_u001_act.jsp` (IDO `TRCO_INFM_U004` → `TRCO_STTS='C'`) | `DELETE /{trcoKey}` + `POST /bulk-delete` → `softDelete` / `bulkSoftDelete` |
| In-use guard | IDO `TRCO_INFM_R021_CNT` (counts `ACCT_TRNS_RCV_PAY_INFM`) | `AcctTrnsRcvPayInfmRepository.existsByBizKeyAndRcvPayTrcoKey` → `WBE1190` |
| Export | `comm_0184_001_r006_excel_view.jsp` (Apache POI XSSF) | `POST /export` → shared `ExcelExportService` → `ContactsLocalExportProvider` / `ContactsOverseasExportProvider` → `ContactsService.listForExport` (seeded by V247) |
| Excel import | `comm_0184_007_*` (IDOs `DAT_IMP_WIZ_TRCO_INFM_*`) | `POST /excel/preflight` + `/excel/resolve-recipients` + `/excel/import` → `ContactsImportService` |
| Address sub-popup | `comm_0184_003_*_act.jsp` | folded into the contact create/update body (address ops) |
| Account sub-popup | `comm_0184_004_*_act.jsp` | folded into the contact create/update body (account ops) |
| Attachments | `comm_0184_005_u002/u003/u008` + `TRCO_INFO_FILE` IDOs | `…/{trcoKey}/files…` → `ContactsFileService` |
| TRCO_KEY gen | `SeqHelper.getNextVal(...)` → `wb_get_next_val('trco_infm','trco_key')` | `LegacyFunctionRepository.nextTrcoKey` (V113 + `sq_trco_key`, OQ-4) |
| Bank-name resolve | IDO `wb_get_napas_bank_nm_or_citad_bank_nm` | `LegacyFunctionRepository.resolveNapasOrCitadBankName` (V114, OQ-7) |

> **Screen-id clarification:** the live "Settings > Contacts" screen is the `comm_0184_*` WSVC family,
> NOT `trco_0001_001`. Both operate on `TRCO_INFM`, but `trco_0001_001` is the standalone WABOOKS
> "Trade Company inquiry" page (a different surface with a map, etc.). The refactor targets `comm_0184_*`.

## Identifier translation — slug, not WSVC id

Per the workspace identifier-naming rule, the refactor uses the kebab-case slug **`contacts`** for
every identifier surface — REST URI (`/api/v1/settings/contacts`), Java package
(`domain.setting.contacts`), class names (`ContactsController`, `ContactsService`, `TrcoInfm`), DTOs,
i18n keys (`settings.contacts.*`), log tags, and the audit `AUDIT_LOGS_INFM.WSVC_ID = 'contacts'`
(OQ-6). The export View Setting screenIds are the slug pair `settings-contacts-local` /
`settings-contacts-overseas`; the list View Setting key is `contacts`. Legacy `comm_0184_*` ids appear
only as JEX file:line citations and in migration-traceability prose (this doc + the preserved mapping
reference).

## Behaviour changes (refactor strengthenings, all APPROVED DEVIATION)

- **Envelope** — JEX `COMMON_HEAD`/`REC` → `{ status, data }`; JEX ML codes → `WBE_CONTACTS_*` /
  `WBE1190`.
- **In-use guard scope** — JEX guards single delete; the refactor also guards bulk delete and the
  edit-time `trco_stts → 'C'` transition (OQ-2).
- **disp_ord re-discrimination** — addresses keyed current/old by `disp_ord ∈ {1,2}` (OQ-8), validated
  on every upsert.
- **Excel import** — two-phase preflight + resolve before the per-row import (stronger pre-commit
  feedback than the legacy one-shot wizard); partial success with per-row error rows.
- **Authorization** — explicit `@menuAuthz.canAccess('settings-core-contacts')` vs JEX menu-visibility
  only (same effective boundary, F-3 enforced).

## Dropped / dead legacy flows

- **Trash tab + Restore** — not ported (OQ-5). Soft-deleted rows persist (`TRCO_STTS='C'`) but have no
  UI surface or restore endpoint. **DEAD LEGACY** for the refactor scope.
- **`trco_0001_001` standalone inquiry page** — out of scope (different surface).
- **Out-of-scope tables** — only PROD-active tables were ported. `TRCO_INFO_FILE` was added by an
  explicit 2026-05-13 PO scope-widening (the only contacts table added beyond the original audit).

## Schema migration (Flyway) — numbering drift

Contacts migrations were **drafted at lower numbers and renumbered on commit**; the files' internal
`-- V8x` header comments are stale. **Always cite by filename version:**

| Filename version | Stale internal header | Summary |
|---|---|---|
| V112 | `V84` | 5-table schema (`TRCO_INFM` 47, `TRCO_ADDR_INFM` 11, `TRCO_ACCT_INFO` 20, `TRCO_OVRS_INFM` 13, `PGRO_BSAC_LNKN_INFM` 6) |
| V113 | `V85` | `sq_trco_key` (START 1,000,000) + `wb_get_next_val('trco_infm','trco_key')` branch |
| V114 | `V86` | port `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang)` over `tb_firm_bank_code` |
| V115 | `V87` | demo seed (30 masters + 30 addresses + 28 accounts + 9 overseas + 30 PGRO links, tenant `VN_VNM0004_000001`, DEV TRCO_KEYs 9212–9322) |
| V117 | `V89` | `TRCO_ADDR_INFM.DISP_ORD` / `TRCO_ACCT_INFO.DISP_ORD` `NUMERIC(10,0)` → `INTEGER` |
| V139 / V140 | `V98` / `V99` | `TRCO_INFO_FILE` schema + `trco_info_file.file_id` next-val branch |
| V154 (re-applied V187) | `V88` / `V169` | list View Setting seed (`web_svc_id='contacts'`, 8 cols incl. CHECKBOX + ETC; `BANK_NM` / `TRCO_BSUN_POST_ADRS` `SORT_YN='N'`) |
| V247 | (committed) | export View Setting seed (`settings-contacts-local` 9 cols + `settings-contacts-overseas` 11 cols; all `FIX_YN='Y' SORT_YN='N'`; GAP-01 gate) |

V93 also seeds 18 NAPAS bank rows into `TB_FIRM_BANK_CODE` for the bank picker.

## Surviving legacy functions

Two `wb_get_next_val` branches (`trco_infm.trco_key`, `trco_info_file.file_id`) + `sq_trco_key` +
`wb_get_napas_bank_nm_or_citad_bank_nm` are kept by exception (OQ-4 / OQ-7) — bodies copied verbatim
from DEV. Retirement plans in the `wecms-api/CLAUDE.md` "Surviving legacy functions" table. Per the
DB-function policy these are the documented exceptions; no other legacy helper is called from contacts
code.

## Preserved mapping reference

The original JEX → Spring service-by-service mapping is preserved at
[references/migration/contacts-mapping.md](../../../references/migration/contacts-mapping.md); the
deepest legacy-analysis bundle (with the SIT-readiness audit) is the workspace-root
[docs/features/contacts/](../../../../../docs/features/contacts/README.md) set.
