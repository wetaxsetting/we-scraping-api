# Settings > Contacts — Feature Summary (Refactor)

> ⚠️ This document is a high-level feature summary.
> The detailed source of truth is located in:
> `docs/features/settings/contacts/`

## Feature Overview
- **Purpose:** Contacts (legacy 거래처 — counterparty / trade-company master, table `TRCO_INFM`) is the per-tenant master list of payees and beneficiaries that every payment / transfer / e-invoice write references. A contact is **Local** (`TRCO_TYPE='L'` — a Vietnam-domestic payee with NAPAS/CITAD bank accounts) or **Overseas** (`TRCO_TYPE='O'` — a foreign beneficiary with SWIFT/BIC details).
- **Business role:** A tenant administrator maintains the directory: list/search by type and keyword, read a contact with its addresses + bank accounts + overseas beneficiaries, create/update (master + nested children in one transaction), soft-delete (single or bulk), bulk-import from Excel, export to XLSX, and attach files per contact.

## Menu / Route Mapping
- **Menu / panel:** Settings → Contacts (Settings modal panel; no standalone route)
- **Menu permission ID (slug):** `settings-core-contacts` (`@PreAuthorize("@menuAuthz.canAccess('settings-core-contacts')")`, class-level on the controller)
- **Wire base path:** `/api/v1/settings/contacts`
- **Frontend mount / route:** Contacts panel inside `<SettingsModal>`
- **Legacy JEX counterpart:** WSVC family `comm_0184_001` (list) · `comm_0184_002_c001` (create) · `comm_0184_005_u001` (edit) · `comm_0184_001_u001` (bulk soft-delete) · `comm_0184_007` (Excel import) · `comm_0184_001_r006_excel` (export). Tables `TRCO_INFM` + `TRCO_ADDR_INFM` + `TRCO_ACCT_INFO` + `TRCO_OVRS_INFM` + `PGRO_BSAC_LNKN_INFM` + `TRCO_INFO_FILE`. (Legacy ids cited for traceability only.)

## Current Status
- ✅ **Implemented:** Full-stack — list (search + `L`/`O` type filter + sort + pagination), detail (full nested shape), create, update (master + nested upsert/delete in one `@Transactional`), single + bulk soft-delete (`TRCO_STTS='C'`), Excel import (two-phase: preflight + resolve-recipients → per-row import, ≤500 rows, partial success), XLSX export (GAP-01 closed 2026-06-08, Local 9-col / Overseas 11-col providers), and file attachments (`TRCO_INFO_FILE`, 10 MB cap, type allow-list). TRCO_KEY via `wb_get_next_val('trco_infm','trco_key')` → `sq_trco_key` through the funnel; bank-name resolution via the ported `wb_get_napas_bank_nm_or_citad_bank_nm`. `WBE1190` in-use guard on single delete, bulk delete, and the edit-time `trco_stts → 'C'` transition. 17 endpoints. Legacy parity verified; SIT/UAT verified.
- 🟡 **Partially implemented:** **No `AUDIT_LOGS_INFM` rows are written today** — `ContactsService` makes no audit call and no AOP aspect targets it; the slug `'contacts'` is fixed (OQ-6) for any future audit write. View Setting is **not live-seeded** (neither the list seed nor the export seed is applied — both archived) — the on-screen grid is a fixed 8-column layout with no popover, and export columns resolve via the provider allow-list ∩ request columns.
- ⚪ **Deprecated / Legacy / Not ported:** No Trash-recovery UI (OQ-5 — list hard-codes `coalesce(trco_stts,'') <> 'C'`; soft-deletes are DBA-recovered only). Not ported: responsible-person sub-record (`TRCO_RSPB_INFM`), memo search (`MEMO_INFM` join), tax-agent linkage (`comm_0184_005_c011` / `comm_0184_006_*`), "Duplicate contact" (`comm_0184_002_c002`), the group filter (`GRP_CD` SALE/BUY/ETC/custom), and the standalone address-list / account-list sub-popups (subsumed into the inline edit form).

## Detailed Documentation
| Doc | Link |
|---|---|
| Overview / README | [contacts/README.md](contacts/README.md) |
| Business flow | [contacts/business-flow.md](contacts/business-flow.md) |
| Database mapping | [contacts/database-mapping.md](contacts/database-mapping.md) |
| Functional specification | [contacts/functional-specification.md](contacts/functional-specification.md) |
| Interface specification | [contacts/interface-specification.md](contacts/interface-specification.md) |
| Migration guide | [contacts/migration-guide.md](contacts/migration-guide.md) |
| Refactor gap analysis | [contacts/refactor-gap-analysis.md](contacts/refactor-gap-analysis.md) |
| Source code index | [contacts/source-code-index.md](contacts/source-code-index.md) |
| Test scenarios | [contacts/test-scenarios.md](contacts/test-scenarios.md) |

## Historical Design Notes (preserved)
- **Several design-stage decisions were resolved the other way in shipped code** (the code is authoritative): TRCO_KEY is allocated via the `wb_get_next_val('trco_infm','trco_key')` branch through `LegacyFunctionRepository.nextTrcoKey` (there is no Java `TrcoKeyGeneratorService`); `TRCO_INFO_FILE` **is** in scope (attachments shipped via `ContactsFileService` after the 2026-05-13 PROD-scope widening); bank-name resolution uses the ported `wb_get_napas_bank_nm_or_citad_bank_nm` via `LegacyFunctionRepository.resolveNapasOrCitadBankName` (**not** `BankCdResolver` — that function pre-exists in the live JEX DB and is not created by any refactor migration).
- **Address discriminator (OQ-8).** Current vs. previous address is keyed by `disp_ord` ∈ {1, 2}, not by `trco_addr_key` string equality.
- **[Not ported]** The `wb_get_napas_bank_nm_or_citad_bank_nm` function is expected to pre-exist in the live JEX DB; the refactor calls it directly rather than reimplementing bank-name resolution in Java.
