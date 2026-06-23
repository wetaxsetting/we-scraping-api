# Contacts — Functional Specification (Backend)

The implemented behaviour of `domain/setting/contacts`. Source of truth is the shipped code; the
OQ decisions below are the resolved open questions carried forward from [../contacts.md](../contacts.md).

---

## Operations matrix

| Operation | Endpoint | Service method | Writes | Audit |
|---|---|---|---|---|
| List + search | `GET /` | `ContactsService.list` | — | no |
| Detail | `GET /{trcoKey}` | `getOne` | — | no |
| Create | `POST /` | `create` → `persistNewContact` | master + children + PGRO links | no¹ |
| Update | `PUT /{trcoKey}` | `update` | master + child upsert/delete | no¹ |
| Soft-delete | `DELETE /{trcoKey}` | `softDelete` | `TRCO_STTS='C'` | no¹ |
| Bulk soft-delete | `POST /bulk-delete` | `bulkSoftDelete` | `TRCO_STTS='C'` ×N | no¹ |
| Export | `POST /export` | `listForExport` (read) → provider | — | no |
| Import preflight | `POST /excel/preflight` | `preflightExcelImport` | — | no |
| Import resolve | `POST /excel/resolve-recipients` | `resolveExcelImportRecipients` | — | no |
| Import | `POST /excel/import` | `ContactsImportService.bulkImport` | master + children ×N (per-row tx) | no¹ |
| By codes | `POST /by-codes` | `getByBeneficiaryCodes` | — | no |
| Bank list | `GET /banks` | `listBanks` | — | no |
| Lookup recipient | `POST /lookup-recipient` | `lookupRecipient` | — | no |
| Attachments | `…/{trcoKey}/files…` | `ContactsFileService` | `TRCO_INFO_FILE` | no |

¹ **No audit rows are written today.** `ContactsService` does not inject `AuditLogService` and there is
no AOP audit aspect that targets it — Create / Update / Delete / Import currently write **no**
`AUDIT_LOGS_INFM` row. OQ-6 only fixes the *slug* (`'contacts'`) that audit rows would carry **if/when**
audit writes are wired; see [§ Audit-log expectations](#audit-log-expectations).

## Field contract (master)

| Field (wire) | Source column | Required | Rules |
|---|---|---|---|
| `hand_trco_key` | `HAND_TRCO_KEY` | yes | Create: `@NotBlank @Size(100) @Pattern ^\S+$` (no whitespace). Update: `@Size(20) @Pattern ^[A-Za-z0-9_\-.]+$`. Tenant-unique → `WBE_CONTACTS_HAND_KEY_DUP` |
| `trco_cmnm` | `TRCO_CMNM` | yes | `@NotBlank @Size(200)` — local-language name |
| `trco_cmenm` | `TRCO_CMENM` | yes | `@NotBlank @Size(200)` — English name (frontend auto-copies from name; no separate input) |
| `trco_tax_id` | `TRCO_TAX_ID` | no | Create `@Size(100)`, Update `@Size(40)`; DB column `VARCHAR(40)`. Non-blank tenant-unique → `WBE_CONTACTS_TAX_ID_DUP` |
| `trco_type` | `TRCO_TYPE` | yes | `@NotBlank @Pattern ^[LO]$` — Local / Overseas discriminator |
| `trco_stts` | `TRCO_STTS` | update only | `@Pattern ^[C]?$` — `'C'` trashes; null/empty leaves unchanged |
| `addresses[]` | `TRCO_ADDR_INFM` | no | `@Valid @Size(50)` — see Address rules |
| `accounts[]` | `TRCO_ACCT_INFO` | no | Local bank accounts — see Bank rules |
| `overseas_beneficiaries[]` | `TRCO_OVRS_INFM` | no | Overseas beneficiaries — see Bank rules |

Server-managed, never on the wire: `TRCO_KEY` (path / generated), `BIZ_KEY` (tenant), `TAX_ID`
(tenant snapshot fallback), `TRCO_INQ_YN` (default `'Y'`, OQ-3), `TRSC_CCY_CD` (derived from type).

## Local vs Overseas handling

`TRCO_TYPE` is the single discriminator.

- **Local (`L`)** — uses `TRCO_ACCT_INFO`. Each account carries a NAPAS or CITAD bank code, account
  number, currency (`VND`/`USD`), display order, and an optional account-holder name
  (`FINC_MDA_OWNER_NM`). On create, account rows persist only when type is `L`.
- **Overseas (`O`)** — uses `TRCO_OVRS_INFM`. Each beneficiary carries name, SWIFT/BIC, account number,
  bank name, and address. On create, overseas rows persist only when type is `O`.
- Switching type on edit is handled by the frontend (it queues the leaving side's rows for deletion);
  the backend simply applies the upsert/delete child passes it receives.

## Address handling (current / old)

`TRCO_ADDR_INFM.DISP_ORD` is the **current/previous discriminator** (OQ-8), NOT `TRCO_ADDR_KEY`:

- `disp_ord = 1` → **current** address.
- `disp_ord = 2` → **old / previous** address; its `DUE_DT` is the "valid date".
- `ContactsValidator.checkUpsertAddressDispOrd` rejects any upsert address whose `disp_ord ∉ {1,2}`
  with `WBE_VALIDATION`.
- `ISSU_DT` / `DUE_DT` default to today's `yyyyMMdd` on entity build when absent.
- Projection sorts addresses by `dispOrd` (nulls last) then `trcoAddrKey`.

## Bank information

- **Resolution (OQ-7):** the displayed bank name is resolved per unique NAPAS-or-CITAD code via
  `wb_get_napas_bank_nm_or_citad_bank_nm(code, lang)` (V114, ported verbatim from DEV) through
  `LegacyFunctionRepository.resolveNapasOrCitadBankName`. Language: `VI → "vi"`, else `"en"`.
  `BankCdResolver` is **not** consulted in this flow. The stored `BANK_NM` is the fallback.
- **Picker (`GET /banks`):** `bankCodeRepo.findByAntDivCdInOrderByEngAbrvatNmAscBankCdAsc(["NAPAS",
  "CITAD"])` → `ContactBankRow(bankCd, antDivCd, bankNm, bankEngNm, engAbrvatNm, status)`. Status
  normalised `'1'/'Y' → 'Y'`, `'0'/'N' → 'N'`. Global, not tenant-scoped.
- **Account-dup rule:** within an upsert account set, duplicate `bankNm|acctNo` (both present) →
  `WBE_CONTACTS_ACCT_DUP`.
- **Overseas-dup rule:** duplicate `beneAcctNo|bicCode` → `WBE_CONTACTS_BENE_DUP`.
- **Currency:** account `ACCT_CCY_CD` (`VND`/`USD`); master `TRSC_CCY_CD` derived from type
  (CHECK `IN ('VND','USD')`).
- **Retired rule:** the old `WBE_CONTACTS_LOCAL_BANK_REQUIRED` (Local must carry a bank) was **removed
  2026-05-25** — legacy does not require it; a Local contact may save with zero accounts.

## Validation rules

`ContactsValidator` (`@Component`, injects only `TrcoInfmRepository`):

- `validateCreate` — hand-key dup, tax-id dup, account dup, overseas dup, address disp_ord.
- `validateImport` — `@Deprecated` alias of `validateCreate`.
- `validateUpdate` — same dup checks, each skipped when the field is unchanged vs the existing row.

## Soft-delete + WBE1190 in-use guard (OQ-2)

- Delete is `TRCO_STTS='C'` only — never physical (OQ-5).
- The guard fires when the contact is referenced by an active `ACCT_TRNS_RCV_PAY_INFM` row
  (`existsByBizKeyAndRcvPayTrcoKey`) on: single delete, bulk delete, and the edit-time
  `trco_stts → 'C'` transition. Result: `WBE1190`.
- Bulk delete is transactional — the first in-use offender rolls back any earlier flips.

## Attachments (`TRCO_INFO_FILE`)

- PROD active-list scope widened 2026-05-13 (PO call) to support business-registration certificate +
  documents on the Add/Edit popup.
- `ContactsFileService`: 10 MB cap; extensions `jpg/jpeg/png/gif/pdf/doc/docx/xls/xlsx/ppt/pptx`;
  `FILE_ID` via `wb_get_next_val('trco_info_file','file_id', trcoKey)` (V140); `SAVE_FILE_NM` = UUID +
  ext; title defaults `"Business Registration"` (FILE_ID 1) / `"New document"`. Path
  `<root>/<bizKey>/contacts/<saveFileNm>` with traversal guard. Tenant ownership enforced in-service
  (`TRCO_INFO_FILE` has no `BIZ_KEY` column).

## Authorization

`@PreAuthorize("@menuAuthz.canAccess('settings-core-contacts')")` at the class level — every endpoint
(read + write) requires a valid JWT and the `settings-core-contacts` menu grant. Missing/invalid JWT
→ 401 `WBE1009`; authenticated-but-unauthorized → 403 `WBE1003`.

## Audit-log expectations

**Current state (verified against shipped code):** the contacts service writes **no** `AUDIT_LOGS_INFM`
rows. `ContactsService` neither injects `AuditLogService` nor is covered by any AOP audit aspect — so
Create / Update / single + bulk Delete / per-row Import produce no audit row today. Reads, Excel
preflight/resolve, bank list, and lookup do not audit either.

OQ-6 fixes the **slug only**: if/when audit writes are wired, the rows must carry
`WSVC_ID = 'contacts'` (the route slug, never the legacy `comm_0184_001`). The Javadoc on
`ContactsService` references "the existing global audit aspect" as the intended mechanism, but no such
aspect exists in the tree — wiring the writes is open work, not implemented.

## Resolved open questions (OQ-1…OQ-8)

| OQ | Decision |
|---|---|
| OQ-1 | No group filter on list |
| OQ-2 | `WBE1190` in-use guard on single + bulk delete + edit-trash transition |
| OQ-3 | `TRCO_INQ_YN` mapped, defaults `'Y'` on create, never on the wire |
| OQ-4 | `TRCO_KEY` via `wb_get_next_val` + global `sq_trco_key`; one-shot retry on collision |
| OQ-5 | No Trash UI — list hard-codes `coalesce(trco_stts,'') <> 'C'` |
| OQ-6 | Audit slug `contacts` |
| OQ-7 | Bank name via `wb_get_napas_bank_nm_or_citad_bank_nm`; `BankCdResolver` not used |
| OQ-8 | Address current/previous discriminator is `disp_ord ∈ {1,2}`, not `trco_addr_key` |

## Error surface

See [interface-specification.md § Error-code registry](interface-specification.md#error-code-registry)
for the full `WBE_CONTACTS_*` + `WBE1190` table.
