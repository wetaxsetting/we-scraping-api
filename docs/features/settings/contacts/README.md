# Settings > Contacts — Backend Feature (Refactor)

**Status:** ✅ Implemented full-stack — backend (controller + 7 services + documented repositories + 6 entities + 22 DTOs + 3 export providers + per-table Flyway migrations, with unit + controller tests) and frontend wired; legacy parity verified; **Contacts Export (GAP-01) CLOSED 2026-06-08**; SIT/UAT verified. **Audit gap:** the contacts service currently writes **no** `AUDIT_LOGS_INFM` rows (see [§ Audit](#quick-facts) note + [functional-specification.md](functional-specification.md)).
**Module / menu:** Settings → **Contacts** (Settings modal panel — **no standalone route**)
**Refactor slug:** `contacts` (matches the JEX-traceability, backend, and frontend counterparts)
**Wire base path:** `/api/v1/settings/contacts` (list · detail · create · update · single + bulk soft-delete · `/export` · `/excel/import` · `/excel/preflight` · `/excel/resolve-recipients` · `/by-codes` · `/banks` · `/lookup-recipient` · `/{trcoKey}/files…`)
**Menu permission slug:** `settings-core-contacts` (`@PreAuthorize("@menuAuthz.canAccess('settings-core-contacts')")`, class-level)

> This folder is the **primary backend documentation home** for Contacts.
> It consolidates and indexes material that previously lived in a single flat file
> ([../contacts.md](../contacts.md)) and in the reference trees; those files are
> **preserved and cross-linked** (see [§ Related & preserved docs](#related--preserved-docs)),
> not deleted. The frontend counterpart lives at
> [wecms-web/docs/features/settings/contacts/](../../../../../wecms-web/docs/features/settings/contacts/README.md).

---

## What this feature is

Contacts (legacy 거래처 — counterparty / trade-company master, table `TRCO_INFM`) is the per-tenant
master list of payees and beneficiaries every payment / transfer / e-invoice write references. A
contact is **Local** (`TRCO_TYPE='L'` — a Vietnam-domestic payee with one or more NAPAS/CITAD bank
accounts) or **Overseas** (`TRCO_TYPE='O'` — a foreign beneficiary with SWIFT/BIC bank details). A
tenant administrator can:

- **List / search** managed, non-trashed contacts for the current `BIZ_KEY`, filtered by type
  (`L`/`O`) and a keyword across contact code / name / English name, paged + sorted.
- **Read** a single contact with its addresses, bank accounts, and overseas beneficiaries.
- **Create** a contact — code (`HAND_TRCO_KEY`), local name (`TRCO_CMNM`), English name
  (`TRCO_CMENM`), tax id (`TRCO_TAX_ID`), type, plus nested address / account / overseas rows.
- **Update** master + nested children in one transaction (combined upsert/delete pass per child collection).
- **Soft-delete** one contact (`TRCO_STTS='C'`) or many (bulk), blocked when the contact is referenced
  by an active transaction (`WBE1190`).
- **Import** contacts in bulk from an Excel template (two-phase: preflight duplicate check + bank-name /
  recipient resolution → per-row import).
- **Export** the Local or Overseas contact list to XLSX (real live data — **GAP-01**, closed 2026-06-08).
- **Attach files** (e.g. business-registration certificate) per contact (`TRCO_INFO_FILE`).

Local vs Overseas is the single `TRCO_TYPE` discriminator; switching it on edit moves the contact
between the account model (`TRCO_ACCT_INFO`) and the beneficiary model (`TRCO_OVRS_INFM`).

---

## Quick facts

| Aspect | Value |
|---|---|
| Endpoints | 17: GET (list) · GET `/{trcoKey}` · POST (create) · PUT `/{trcoKey}` · DELETE `/{trcoKey}` · POST `/bulk-delete` · POST `/export` · POST `/excel/import` · POST `/excel/preflight` · POST `/excel/resolve-recipients` · POST `/by-codes` · GET `/banks` · POST `/lookup-recipient` · GET/POST `/{trcoKey}/files` · GET/DELETE `/{trcoKey}/files/{fileId}` |
| Authority | `@PreAuthorize("@menuAuthz.canAccess('settings-core-contacts')")` — class-level (F-3 menu-auth, 2026-06-06) |
| Tenant scope | `TenantContext.currentBizKey()` — `biz_key` never on the wire |
| Tables | `TRCO_INFM` (master, 47 cols, PK `TRCO_KEY` alone) + `TRCO_ADDR_INFM` (addresses) + `TRCO_ACCT_INFO` (Local bank accounts) + `TRCO_OVRS_INFM` (Overseas beneficiaries) + `PGRO_BSAC_LNKN_INFM` (group links) + `TRCO_INFO_FILE` (attachments) |
| Soft-delete | `TRCO_STTS='C'`; no physical delete. No Trash UI (OQ-5 — list hard-codes `coalesce(trco_stts,'') <> 'C'`) |
| Delete guard | `WBE1190` when the contact is referenced by an active `ACCT_TRNS_RCV_PAY_INFM` row — on single delete, bulk delete, AND the edit-time `trco_stts → 'C'` transition (OQ-2) |
| PK generation | `wb_get_next_val('trco_infm','trco_key')` → global `sq_trco_key` (OQ-4; branch in V140, sequence in V159 — START 0, not 1,000,000); one-shot retry on PK collision |
| Bank-name resolution | `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang)` (OQ-7) via `LegacyFunctionRepository.resolveNapasOrCitadBankName` — function pre-exists in the live JEX DB (not created by any refactor migration); `BankCdResolver` NOT used in this flow |
| Export (GAP-01) | `POST /export` → shared `ExcelExportService` → `ContactsLocalExportProvider` (9 cols) / `ContactsOverseasExportProvider` (11 cols); reuses `ContactsService.listForExport`. **No live View Setting seed** (old V247 archived) — columns resolve via the provider allow-list ∩ request columns |
| Excel import | two-phase `/excel/preflight` + `/excel/resolve-recipients` → `/excel/import` (per-row, ≤500 rows, partial success, always HTTP 200) |
| Attachments | `TRCO_INFO_FILE` (PROD-scope widened 2026-05-13); 10 MB cap; allow-list jpg/jpeg/png/gif/pdf/doc/docx/xls/xlsx/ppt/pptx |
| Audit | **None written today** — `ContactsService` makes no `AuditLogService` call and no AOP aspect targets it; OQ-6 fixes only the slug `'contacts'` for any future write |
| View Setting | **No live seed** — neither the list seed (old V154/V187) nor the export seed (old V247) is applied (both archived 2026-06-09). Export columns resolve via the provider allow-list ∩ request; the on-screen grid is a fixed 8-col layout (no popover) |
| Wire format | lowercase snake_case (global Jackson `SNAKE_CASE`) |

---

## Backend package layout

```
com.kosign.wecms.domain.setting.contacts
├── controller/ContactsController.java            17 endpoints; class-level @menuAuthz gate
├── service/
│   ├── ContactsService.java                      list/detail/create/update/delete/bulk-delete; listForExport; banks; lookup; import-resolution
│   ├── ContactsImportService.java                bulkImport() — per-row tx isolation, partial success
│   ├── ContactsValidator.java                    create/update/import dup + address-disp_ord rules
│   ├── ContactsFileService.java                  attachment upload/list/download/delete (TRCO_INFO_FILE)
│   ├── ContactsFileResolver.java                 <root>/<bizKey>/contacts/<saveFileNm> path builder + traversal guard
│   ├── ContactsFileStorageProperties.java        @ConfigurationProperties("wecms.contacts") storageRoot
│   ├── ContactsConfig.java                        @EnableConfigurationProperties wiring
│   └── ContactSortField.java                      sort whitelist (HAND_TRCO_KEY / TRCO_CMNM / TRCO_TYPE / TRCO_TAX_ID)
├── export/
│   ├── ContactsExportProvider.java               abstract base — 14 FIELD_IDs, fetch(), toFieldMap()
│   ├── ContactsLocalExportProvider.java          screenId settings-contacts-local; 9 cols; type 'L'
│   └── ContactsOverseasExportProvider.java        screenId settings-contacts-overseas; 11 cols; type 'O'
├── repository/
│   ├── TrcoInfmRepository.java                    search(), findByBeneficiaryCodes(), preflight dup queries, derived finders
│   └── TrcoInfoFileRepository.java                attachment finders (+ TrcoAddrInfmRepository / TrcoAcctInfoRepository / TrcoOvrsInfmRepository / PgroBsacLnknInfmRepository consumed by the service)
├── entity/
│   ├── TrcoInfm.java                              @Table(TRCO_INFM), @Id TRCO_KEY (global), 47-col table / Sprint subset mapped
│   ├── TrcoAddrInfm.java                          @Table(TRCO_ADDR_INFM), @IdClass (TRCO_KEY, BIZ_KEY, TRCO_ADDR_KEY); disp_ord = current/old
│   └── TrcoInfoFile.java                          @Table(TRCO_INFO_FILE), @IdClass (TRCO_KEY, FILE_ID) — no BIZ_KEY column
└── dto/                                           22 records — see source-code-index.md
```

Flyway (current per-table scheme after the 2026-06-09 consolidation — **one legacy table per migration file, DDL copied verbatim from the live JEX DB incl. triggers/`bpchar`/full column sets**): V112 `trco_acct_info` · V114 `trco_addr_infm` · V116 `trco_info_file` · V119 `trco_infm` · V121 `trco_ovrs_infm` · V82 `pgro_bsac_lnkn_infm`; V140 `wb_get_next_val` (carries the `trco_infm.trco_key` + `trco_info_file.file_id` branches) · V159 `sq_trco_key`. **The `wb_get_napas_bank_nm_or_citad_bank_nm` function is NOT defined by any refactor migration — it is expected to pre-exist in the live JEX DB** (refactor runs against the live JEX DB in all envs; `LegacyFunctionRepository.resolveNapasOrCitadBankName` calls it directly). There is **no contacts row in the View Setting seed (V93/V156)** and **no export View Setting seed in the live set** — the old `V247__contacts_export_view_setting_seed.sql` was archived in the 2026-06-09 renumber and **removed with `db/archive/` 2026-06-23 (approved decision)**; export columns resolve via the provider allow-list ∩ request columns (no `WBE2013` gate). No demo seed (V115) and no `disp_ord`→INTEGER hotfix (V117) in the current set — `DISP_ORD` is `NUMERIC(10,0)` and the entities map it via `@JdbcTypeCode(NUMERIC)`. ⚠️ The earlier doc revisions cited a different, now-stale migration layout (V112=5-table schema, V113/V114/V115/V117/V139/V140/V154/V187/V247); those numbers no longer match the tree.

---

## Legacy & Migration Traceability

**Legacy JEX counterpart:** WSVC family `comm_0184_001` (list) · `comm_0184_002_c001` (create) · `comm_0184_005_u001` (edit) · `comm_0184_001_u001` (bulk soft-delete) · `comm_0184_007` (Excel import) · `comm_0184_001_r006_excel` (export). Tables `TRCO_INFM` + `TRCO_ADDR_INFM` + `TRCO_ACCT_INFO` + `TRCO_OVRS_INFM` + `PGRO_BSAC_LNKN_INFM` + `TRCO_INFO_FILE`.

**Migration numbering note:** the schema now lands as per-table migrations (V112/V114/V116/V119/V121 + V140/V159) after the 2026-06-09 consolidation — see [§ Backend package layout](#backend-package-layout) and [migration-guide.md](migration-guide.md).

---

## Documents in this folder

| Doc | Covers |
|---|---|
| [business-flow.md](business-flow.md) | End-to-end flow: search → controller → service → repository → projected row → grid → create/edit/delete → import (preflight/resolve/import) → export. Tenant + audit invariants |
| [functional-specification.md](functional-specification.md) | Operations matrix, field contract, Local/Overseas handling, address current/old, bank resolution, validation rules, soft-delete + WBE1190 guard, audit expectations, OQ-1…OQ-8 |
| [database-mapping.md](database-mapping.md) | The 6 tables, PK-gen + bank-name functions, and the full legacy-column → entity-field → DTO-field → UI-field mapping |
| [interface-specification.md](interface-specification.md) | REST contract — request/response shapes per endpoint, error-code registry, authorization, wire envelope |
| [source-code-index.md](source-code-index.md) | Legacy ↔ refactor file map (backend + frontend), file:line citations |
| [migration-guide.md](migration-guide.md) | How each JEX layer (JSP / `_act.jsp` / IDO / WSVC / entity) became its Spring counterpart; identifier translation; dropped/dead flows; current per-table Flyway scheme |
| [test-scenarios.md](test-scenarios.md) | SIT/UAT scenarios + the existing JUnit coverage map |
| [refactor-gap-analysis.md](refactor-gap-analysis.md) | Final parity review (MATCH / APPROVED DEVIATION / DEAD LEGACY / PARTIAL / MISSING) with completion status + GAP-01 closure evidence |

---

## Related & preserved docs

These existing docs are **preserved** and remain authoritative for their slice. This README is the
entry point; deep detail lives here:

- **Original single-file backend spec** (functional summary, OQ-1…OQ-8, `WBE_CONTACTS_*` error table,
  View Setting seed): [../contacts.md](../contacts.md) — kept as the design narrative. ⚠️ Carries a
  stale banner; its §§ on key-gen / bank-resolution / file-scope are reconciled against shipped code
  here and in [refactor-gap-analysis.md](refactor-gap-analysis.md).
- **Business rules / service design:** [references/business-rules/contacts-design.md](../../../references/business-rules/contacts-design.md)
- **DB schema design:** [references/database/contacts-schema.md](../../../references/database/contacts-schema.md)
- **JEX → Spring mapping:** [references/migration/contacts-mapping.md](../../../references/migration/contacts-mapping.md)
- **Workspace-root JEX-analysis bundle** (richest legacy source-of-truth + SIT-readiness audit + the
  original GAP-01 finding & closure): [docs/features/contacts/](../../../../../docs/features/contacts/README.md)
  — incl. [refactor-audit.md](../../../../../docs/features/contacts/refactor-audit.md) and
  [refactor-gap-analysis.md](../../../../../docs/features/contacts/refactor-gap-analysis.md). Sits at a
  CLAUDE.md-non-compliant workspace-root path (flagged 2026-06-08); cross-linked, not moved.
- **Archived task plan:** archive/planning/phase-1/contacts-tasks.md _(removed — historical `archive/` folder deleted by project decision)_
- **Frontend feature folder:** [wecms-web/docs/features/settings/contacts/](../../../../../wecms-web/docs/features/settings/contacts/README.md)
- **Settings ERD:** [references/database/erd/settings-erd.md](../../../references/database/erd/settings-erd.md)
- **Settings feature index:** [../README.md](../README.md)

> **Note on `docs/refactor-wecms/`:** a deprecated mirror of the contacts migration / schema docs
> exists under the workspace-level `docs/refactor-wecms/features/contacts/`. That tree is
> **read-only legacy** per the workspace CLAUDE.md; the canonical copies are the `wecms-api/docs/...`
> paths linked above.

---

## Open item — JEX `docs/jex/features/contacts*` is missing

Contacts is a live JEX feature, but there is **no** `docs/jex/features/contacts.md` JEX-side spec
today — the legacy source-of-truth role is currently filled by the off-spec workspace-root
[docs/features/contacts/](../../../../../docs/features/contacts/README.md) bundle. Authoring the
canonical JEX spec is a documentation follow-up (tracked, non-blocking for the refactor parity which
is verified here). It does not affect the implemented feature.
