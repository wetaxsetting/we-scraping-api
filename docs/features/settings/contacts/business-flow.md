# Contacts — Business Flow (Backend)

End-to-end flow for every Contacts operation, from controller through service / validator /
repository to the projected response. Tenant scope (`TenantContext.currentBizKey()`) is the invariant
on every write. (Audit is **not** written today — see § Invariants; the `'contacts'` slug is the
OQ-6-fixed value for any future audit row, not a row written now.) Controller:
[ContactsController.java](../../../../src/main/java/com/kosign/wecms/domain/setting/contacts/controller/ContactsController.java).

---

## 1. List + search

```
GET /api/v1/settings/contacts?search=&trco_type=&page=0&size=15&sort_field=&sort_direction=
   → ContactsController.list
   → ContactsService.list(bizKey, search, trcoType, page, size, sortField, sortDirection)
```

1. Resolve `bizKey` from `TenantContext`.
2. Build a keyword `LIKE` pattern (`buildKeywordPattern` — lowercases, escapes `% _ !` with `!`,
   wraps `%…%`, returns null when blank). Matched against `TRCO_CMNM` / `TRCO_CMENM` / `HAND_TRCO_KEY`.
3. Normalise `trco_type` (`normaliseTrcoTypeFilter` — only `L`/`O` survive; anything else → no filter).
4. Clamp `size` (`<=0 → 15`, `min(size, 100)`), `page = max(0, page)`.
5. Build `Sort` from `ContactSortField.parse(sortField)` + `SortDirection.parse(sortDirection)`,
   appending the default tiebreaker `Sort.by(asc("handTrcoKey"), desc("trcoKey"))` (legacy
   `comm_0184_001_r001` parity — alphabetical by Contact Code so Local + Overseas interleave).
6. `TrcoInfmRepository.search(...)` returns a `Page<TrcoInfm>` filtered to managed
   (`handTrcoKey IS NOT NULL`) + active (`trcoStts IS NULL OR <> 'C'`) + type + keyword.
7. Batch-fetch the three child collections for the page's `TRCO_KEY`s via
   `findByBizKeyAndTrcoKeyIn` (avoids N+1).
8. Resolve bank names once per unique NAPAS-or-CITAD code (`resolveBankNamesForAccounts` →
   `LegacyFunctionRepository.resolveNapasOrCitadBankName`, language from `TenantContext`).
9. Project each row (`projectRow`) — addresses sorted by `dispOrd` nullsLast then key, accounts by
   `dispOrd` then key, overseas by key.
10. Return `ContactListResponse(rows, totalCount, page, size, totalPages)`.

The list never returns trashed rows; there is no Trash tab (OQ-5).

## 2. Detail

```
GET /api/v1/settings/contacts/{trcoKey}
   → ContactsService.getOne(trcoKey)
```

`findByTrcoKeyAndBizKey`; rows with null `handTrcoKey` or `trcoStts='C'` are treated as not-found →
`WBE_CONTACTS_NOT_FOUND` (the single envelope also covers wrong-tenant). Children loaded, row
projected.

## 3. Create

```
POST /api/v1/settings/contacts   (ContactCreateRequest)
   → ContactsService.create → persistNewContact
```

1. `ContactsValidator.validateCreate` — duplicate `hand_trco_key` → `WBE_CONTACTS_HAND_KEY_DUP`;
   non-blank duplicate `trco_tax_id` → `WBE_CONTACTS_TAX_ID_DUP`; account-dup
   (`WBE_CONTACTS_ACCT_DUP`); overseas-dup (`WBE_CONTACTS_BENE_DUP`); every upsert address must have
   `disp_ord ∈ {1,2}` (`WBE_VALIDATION`, OQ-8).
2. Allocate `trcoKey = LegacyFunctionRepository.nextTrcoKey(bizKey)` (global `sq_trco_key`, OQ-4).
3. Build `TrcoInfm` — `TAX_ID` defaults to `trcoTaxId` else `bizKey` (NOT-NULL fallback),
   `trcoInqYn='Y'` (OQ-3, never on wire), `trscCcyCd` = `"VND"` when type `L` else `"USD"` (satisfies
   the `trco_infm_chk01` CHECK), audit stamps.
4. Save master, then **upsert-only** child rows (delete ops ignored on create): addresses always;
   accounts only when type `L`; overseas only when type `O`.
5. Seed `PGRO_BSAC_LNKN_INFM` rows for `GRP_CD ∈ {B,S}` with `USER_ID='ALL'` (mirrors
   `comm_0184_002_c001_act.jsp:131-147`).
6. On `DataIntegrityViolationException` (PK collision) retry once (OQ-4).
7. Return `getOne(trcoKey)`.

## 4. Update

```
PUT /api/v1/settings/contacts/{trcoKey}   (ContactUpdateRequest)
   → ContactsService.update
```

1. Load existing or `WBE_CONTACTS_NOT_FOUND`; `ContactsValidator.validateUpdate` (same dup checks,
   skipped when the field is unchanged vs existing).
2. **In-use guard (OQ-2):** if the request transitions `trco_stts → 'C'` and
   `AcctTrnsRcvPayInfmRepository.existsByBizKeyAndRcvPayTrcoKey` is true → `WBE1190`.
3. Update master fields; apply `trcoStts` verbatim only when non-null/non-empty.
4. For each child collection, a combined **upsert/delete** pass in one transaction: addresses
   (upsert → save, else `deleteByBizKeyAndTrcoKeyAndTrcoAddrKey`); accounts (upsert/delete + folded
   `accountsToDelete`); overseas (upsert/delete + folded `overseasToDelete`).
5. On a trash transition return a freshly-projected row (children re-read) so the response isn't
   hidden by `getOne`'s active-only filter; otherwise return `getOne`.

## 5. Soft-delete (single + bulk)

```
DELETE /api/v1/settings/contacts/{trcoKey}        → ContactsService.softDelete
POST   /api/v1/settings/contacts/bulk-delete      → ContactsService.bulkSoftDelete
```

- **Single:** load or `WBE_CONTACTS_NOT_FOUND`; `existsByBizKeyAndRcvPayTrcoKey` → `WBE1190`; set
  `trcoStts='C'` + audit.
- **Bulk:** per key load-or-NF, per-key `WBE1190` guard (the first in-use offender throws and rolls
  back any earlier flips in the transaction), set `'C'` + audit.

## 6. Excel import (two-phase)

```
POST /excel/preflight              → ContactsService.preflightExcelImport
POST /excel/resolve-recipients     → ContactsService.resolveExcelImportRecipients
POST /excel/import                 → ContactsImportService.bulkImport
```

1. **Preflight** (read-only): collect non-blank `hand_trco_key` / `trco_tax_id`, two batched IN-queries
   (`findExistingActiveHandTrcoKeys`, `findExistingActiveTrcoTaxIds`), return per-row duplicate flags.
2. **Resolve recipients** (Local only, read-only): one IN-query `findByEngAbrvatNmIn` for all distinct
   bank names; per row resolve NAPAS/CITAD code (NAPAS preferred on ≥2 matches). When the tenant has an
   active firm-banking config, NAPAS-VND rows fire `firmBankingInquiry.inquiryAccount` for the holder
   name; otherwise codes resolve but inquiry is skipped.
3. **Import:** `ContactsImportService.bulkImport` iterates rows, calling
   `ContactsService.createForImport` (routes through `validateImport`, now an alias of
   `validateCreate`). Each row runs in its **own** transactional proxy so a failing row rolls back only
   itself; `BusinessException` → `ContactImportError(index, handTrcoKey, code, message)`, generic
   exception → code `WBE9999`. Always returns `ContactBulkImportResponse(total, imported, failed,
   errors)` with HTTP 200.

## 7. Export (GAP-01)

```
POST /api/v1/settings/contacts/export   (ExcelExportRequest, screenId = settings-contacts-local | settings-contacts-overseas)
   → ExcelExportService.exportTo → ContactsLocalExportProvider | ContactsOverseasExportProvider
```

1. `ExportableScreenRegistry` resolves the provider by `screenId`.
2. The provider calls `ContactsService.listForExport(keyword, contactType())` — the **same query path**
   as the list (tenant scope, active-only, type filter, keyword) but `Pageable.unpaged()`.
3. Each row maps to a FIELD_ID map (`toFieldMap`): contact code/name/tax id from master; primary deposit
   account = `first(accounts)` → bank/account-no/currency; primary overseas beneficiary =
   `first(overseasBeneficiaries)` → beneficiary fields; address split by `disp_ord` (1 = current,
   2 = old); VALID_DATE = old address `dueDt` reformatted `yyyyMMdd → dd-MM-yyyy`.
4. `ExcelExportService` resolves the columns via the provider allow-list ∩ the requested columns and
   streams XLSX. There is **no live contacts View Setting seed** (the old V247 export seed was archived
   2026-06-09), so the allow-list ∩ request is the sole column gate — no `WBE2013`-from-seed path.

## 8. Supporting reads

- `GET /banks` — NAPAS + CITAD bank picker list (`TB_FIRM_BANK_CODE`), global, not tenant-scoped.
- `POST /by-codes` — batch-fetch contacts by beneficiary code (`HAND_TRCO_KEY`); used by overseas
  batch-payment, silently omits unmatched.
- `POST /lookup-recipient` — single recipient inquiry; delegates to `FirmBankingInquiryService` (stub).
- `GET/POST/DELETE /{trcoKey}/files…` — attachment list / upload / download / delete via
  `ContactsFileService` (10 MB cap, extension allow-list, UUID save name, tenant-rooted path).

---

## Invariants

- **Tenant scope** — every query constrains `BIZ_KEY = TenantContext.currentBizKey()`; never on the wire.
- **Audit** — **not written today.** `ContactsService` does not call `AuditLogService` and no AOP audit
  aspect targets it, so Create / Update / single + bulk Delete / Import produce no `AUDIT_LOGS_INFM`
  row. OQ-6 fixes only the slug (`'contacts'`) that such rows would carry once audit writes are wired.
- **Soft-delete only** — no physical delete; list hard-codes `coalesce(trco_stts,'') <> 'C'`.
- **Key generation + bank-name resolution** route through the single `LegacyFunctionRepository` funnel
  (OQ-4, OQ-7) — never scattered `@Query(nativeQuery=true)`.

See [interface-specification.md](interface-specification.md) for request/response shapes and
[functional-specification.md](functional-specification.md) for the field-level rules.
