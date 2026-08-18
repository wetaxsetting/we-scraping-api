# Contacts — Migration Guide (JEX → Spring)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

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
| Export | `comm_0184_001_r006_excel_view.jsp` (Apache POI XSSF) | `POST /export` → shared `ExcelExportService` → `ContactsLocalExportProvider` / `ContactsOverseasExportProvider` → `ContactsService.listForExport` (columns via provider allow-list ∩ request — no live View Setting seed) |
| Excel import | `comm_0184_007_*` (IDOs `DAT_IMP_WIZ_TRCO_INFM_*`) | `POST /excel/preflight` + `/excel/resolve-recipients` + `/excel/import` → `ContactsImportService` |
| Address sub-popup | `comm_0184_003_*_act.jsp` | folded into the contact create/update body (address ops) |
| Account sub-popup | `comm_0184_004_*_act.jsp` | folded into the contact create/update body (account ops) |
| Attachments | `comm_0184_005_u002/u003/u008` + `TRCO_INFO_FILE` IDOs | `…/{trcoKey}/files…` → `ContactsFileService` |
| TRCO_KEY gen | `SeqHelper.getNextVal(...)` → `wb_get_next_val('trco_infm','trco_key')` | `LegacyFunctionRepository.nextTrcoKey` (branch in V140 + `sq_trco_key` in V159, OQ-4) |
| Bank-name resolve | IDO `wb_get_napas_bank_nm_or_citad_bank_nm` | `LegacyFunctionRepository.resolveNapasOrCitadBankName` (function pre-exists in the live JEX DB — not a refactor migration; OQ-7) |

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

## Schema migration (Flyway) — current per-table scheme

After the **2026-06-09 consolidation**, the contacts schema lands as **one legacy table per migration
file**, with the DDL copied verbatim from the live JEX DB (triggers, `bpchar`, full column sets). Cite
by the filename version below — earlier doc revisions cited a now-stale scheme (V112=5-table schema,
V113/V115/V117/V139/V154/V187/V247) that no longer matches the tree.

| Filename version | Summary |
|---|---|
| V112 | `trco_acct_info` schema (incl. legacy `bank_cd`/`trco_nm`/`trco_enm`/`owac_nm`/`napas_bank_yn` columns + the entity-mapped `bank_nm`/`napas_bank_cd`/`citad_bank_cd`/`acct_ccy_cd`/`finc_mda_owner_nm`/`owac_addr`; `DISP_ORD NUMERIC(10,0)`; BEFORE-INSERT/UPDATE trigger `reject_not_existing_bank_cd()`) |
| V114 | `trco_addr_infm` schema (`DISP_ORD NUMERIC(10,0)`) |
| V116 | `trco_info_file` schema (attachments) |
| V119 | `trco_infm` master schema (full DEV column set, PK `trco_key` alone) |
| V121 | `trco_ovrs_infm` schema (overseas beneficiaries) |
| V82  | `pgro_bsac_lnkn_infm` schema (group links) |
| V140 | `wb_get_next_val` — carries the `trco_infm.trco_key` (global, via `sq_trco_key`) + `trco_info_file.file_id` (per-contact MAX+1) branches |
| V159 | `sq_trco_key` sequence — **START 0** (regression-repair file; the old V113 START-1,000,000 sequence was dropped in the renumber while the V140 consumer branch survived) |

**Not present in the live set:** there is **no** `wb_get_napas_bank_nm_or_citad_bank_nm` migration (the
function is expected to pre-exist in the live JEX DB and is called directly), **no** demo seed, **no**
`disp_ord → INTEGER` hotfix, and **no** contacts View Setting seed — the old list seed (`V154`,
re-applied `V187`) and the export seed (`V247`) were archived to `db/archive/` (**removed 2026-06-23, approved decision**) and
are not applied. Export columns resolve via the provider allow-list ∩ request columns instead of a seed.

## Surviving legacy functions

Two `wb_get_next_val` branches (`trco_infm.trco_key`, `trco_info_file.file_id`, in V140) + `sq_trco_key`
(V159) + `wb_get_napas_bank_nm_or_citad_bank_nm` are kept by exception (OQ-4 / OQ-7). The
`wb_get_next_val` body is copied verbatim from DEV; `wb_get_napas_bank_nm_or_citad_bank_nm` is **not**
redefined by any refactor migration — it is expected to pre-exist in the live JEX DB and is called
directly. Retirement plans in the `wecms-api/CLAUDE.md` "Surviving legacy functions" table. Per the
DB-function policy these are the documented exceptions; no other legacy helper is called from contacts
code.

## Preserved mapping reference

The original JEX → Spring service-by-service mapping is preserved at
[references/migration/contacts-mapping.md](../../../references/migration/contacts-mapping.md); the
deepest legacy-analysis bundle (with the SIT-readiness audit) is the workspace-root
[docs/features/contacts/](../../../../../docs/features/contacts/README.md) set.
