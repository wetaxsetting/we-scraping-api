# Settings > VAS Account — Business Flow (Backend)

The *how* of the refactored backend. The *what* (scope, filters, columns) is in
[functional-specification.md](functional-specification.md); wire shapes in
[interface-specification.md](interface-specification.md). JEX source of truth:
[docs/jex/features/vas-account/business-flow.md](../../../../../docs/jex/features/vas-account/business-flow.md).

All flows are tenant-scoped — `VasAccountService.requireBizKey()` reads
`TenantContext.currentBizKey()` and throws `IllegalStateException` if absent. Audit constants:
`AUDIT_WSVC_ID = "vas-account"`, `REF_NO = "BUSI_VAS_ACCT_INFM"`. Timestamps are `yyyyMMddHHmmss`
UTC (`OffsetDateTime.now(ZoneOffset.UTC)`), written to the `VARCHAR(14)` audit columns.

---

## 1. Retrieval flow (list)

```
GET /api/v1/settings/vas-account?search_wd=&stts=A&stts=I&all=true
        ↓ VasAccountController.list
        ↓ all=true ? VasAccountService.listAll : VasAccountService.list
        ↓   requireBizKey → buildKeywordPattern → resolveStatusFilter → buildSort
        ↓   BusiVasAcctInfmRepository.search(bizKey, statusFilter, keywordPattern, pageable)
        ↓   rows → toRow (resolves stts_key + vas_acct_type_key via enums)
        → VasAccountListResponse { list, total_count, page_number, page_size, total_pages }
```

- `resolveStatusFilter` — null/empty or 2 values → `null` (no filter, both A+I). One value → trim +
  upper, validated against `AStts.fromCode` else `WBE_VALIDATION`.
- `buildKeywordPattern` — escapes `% _ !` with an `!` escape char, wraps `%…%`, lowercases. JPQL uses
  `LOWER(...) LIKE :pattern ESCAPE '!'` on `vas_acct_cd` OR `vas_acct_nm`.
- `all=true` → `listAll` wraps `listAllForExport` (PageRequest unpaged, `Integer.MAX_VALUE`). The
  management panel uses this so the **frontend** can build its prefix-tree grouping over the full set;
  the backend itself returns a flat list — it never nests.

## 2. Lookup flow (active-only picker)

```
GET /api/v1/settings/vas-account/lookup?search_wd=cash
        ↓ VasAccountService.lookup
        ↓ BusiVasAcctInfmRepository.findLookup(bizKey, keywordPattern)   // stts='A' AND del_yn='N', ORDER BY code
        ↓ .limit(LOOKUP_MAX_ROWS = 500) → VasAccountLookupRow(seqNo:Long, cd, nm)
        → VasAccountLookupResponse { items }
```

Consumed by the Edit Bank Account dialog's VAS picker (legacy `comm_1002_001`). Note: the lookup row
ships `vas_acct_seq_no` as a **number** here, whereas the full list/detail row ships it as a **string**
(legacy NUMERIC PK kept as string to avoid JS precision loss) — by design.

## 3. Create flow

```
POST /api/v1/settings/vas-account   { vas_acct_cd, vas_acct_nm, stts, ... }
        ↓ @Valid (DTO bean validation: code 3–15 ^[A-Za-z0-9_]+$, name ≤50, stts ^[AI]$, type ^[012]?$, cont ≤200)
        ↓ VasAccountService.create (@Transactional)
        ↓   requireBizKey + requireUserId + nowDtm
        ↓   existsByBizKeyAndVasAcctCdAndDelYn(bizKey, cd, "N") → WBE0003 if duplicate
        ↓   vasAcctSeqNo = seqGen.next(bizKey)         // per-tenant MAX+1
        ↓   type defaults to "0" (Dual) when blank; delYn="N"; regId=modId=userId; regDtm=modDtm=now
        ↓   repository.save → appendAudit(create.detail)
        → VasAccountRow (toRow)
```

## 4. Update flow

```
PUT /api/v1/settings/vas-account/{vasAcctSeqNo}   { vas_acct_cd, vas_acct_nm, stts, ... }
        ↓ VasAccountService.update (@Transactional)
        ↓   validate seqNo > 0 (else WBE_VALIDATION)
        ↓   findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, seqNo, "N")  orElseThrow WBE2040
        ↓   if code changed AND exists(newCode) → WBE0003
        ↓   always set vasAcctCd / vasAcctNm / stts
        ↓   set vasAcctEnm / vasAcctType / cont ONLY when non-blank (else preserve)
        ↓   modId/modDtm → save → appendAudit(update.detail)
        → VasAccountRow
```

Status change (Active ↔ Inactive) is performed here by setting `stts` — there is no separate
activate/deactivate flow.

## 5. Soft-delete flow

```
DELETE /api/v1/settings/vas-account/{vasAcctSeqNo}
        ↓ VasAccountService.softDelete (@Transactional)
        ↓   validate seqNo > 0; load live row orElseThrow WBE2040
        ↓   delYn="Y"; modId/modDtm → save → appendAudit(delete.detail)
        → ApiResponse.ok(null)
```

No hard delete; downstream `ACCT_MSTR` / `ACCT_TRNS_RCV_PAY_INFM` rows that reference the
soft-deleted `VAS_ACCT_SEQ_NO` are untouched (no cascade).

## 6. Sequence generation + concurrency

`VasAcctSeqNoGenerator.next(bizKey)` = `repository.findMaxSeqNoByBizKey(bizKey)` → `null ? 1 : max+1`.
This is the Java reimplementation of the legacy `SeqHelper.getNextVal('BUSI_VAS_ACCT_INFM',
'vas_acct_seq_no', BIZ_KEY)` shared IDO branch (per-`BIZ_KEY` MAX+1).

**Concurrency:** the read + INSERT run inside the caller's `@Transactional`. Under READ COMMITTED two
parallel inserts can compute the same MAX+1 and collide on the composite PK → Hibernate raises
`DataIntegrityViolationException` → `WBE_DB` (HTTP 500). This is accepted for R&D.
**Note** — unlike Chart of Accounts (`comm_0223`), the VAS Account create/import path does **not**
retry once on PK conflict; the import path re-allocates `seqGen.next` per row inside one transaction.

## 7. Excel preview flow

```
POST /api/v1/settings/vas-account/excel/preview   (multipart "file")
        ↓ VasAccountExcelService.preview (@Transactional(readOnly))
        ↓   null/empty file → WBE_VALIDATION
        ↓   WorkbookFactory.create(sheet 0); corrupt → WBE2043
        ↓   validateHeader(row 2, cols B+C) — case-insensitive label match (EN/KO/VI sets) → WBE2042 on mismatch
        ↓   rowCount > MAX_ROWS(500) → WBE2041
        ↓   loadLiveCodes once (findLiveCodesByBizKey)
        ↓   per data row (from index 3): validateCode + validateName, stage valid codes into seenInFile
        → VasAccountExcelPreview { rows[], total_count, completed_count, error_count }
```

- **Per-cell validation** — `validateCode`: empty→`required`, len<3 or >15→`length`, not
  `^[A-Za-z0-9_]+$`→`format`, in `seenInFile`→`dup_file`, in `liveCodes`→`dup_db`. `validateName`:
  empty→`required`, >50→`length`. Messages are pre-localized server-side (`MessageResolver` +
  `TenantContext.currentLangCd()`).
- Only columns **B** (code) and **C** (name) are read; the other 5 template columns (No./Type/English/
  Description/Status) are ignored. Numeric cells coerce to string without a trailing `.0`.

## 8. Excel import flow

```
POST /api/v1/settings/vas-account/excel/import   { upld_optn: "A"|"R", rows: [{vas_acct_cd, vas_acct_nm}] }
        ↓ VasAccountExcelService.import_ (@Transactional)
        ↓   empty rows → WBE_VALIDATION; > 500 → WBE2041
        ↓   replace = "R".equals(upldOptn)
        ↓   re-validate every row vs current DB:
        ↓     Append → loadLiveCodes (dup_db enforced)
        ↓     Replace → Set.of() (skip dup_db; the wipe clears conflicts)
        ↓   first failure → WBE_VALIDATION "row N: <msg>" (atomic abort — nothing inserted)
        ↓   Replace: softDeleteAllLiveForTenant(bizKey, userId, now) → replacedCount
        ↓   insert each row: seqGen.next per row, type "0", stts "A", delYn "N"
        ↓   one audit row: append→excel_append.detail/refNo2="APPEND"; replace→excel_replace.detail/refNo2="REPLACE"
        → VasAccountExcelImportResult { inserted_count, replaced_count }
```

## 9. Export flow

```
POST /api/v1/settings/vas-account/export   (ExcelExportRequest)  + HttpServletResponse
        ↓ ExcelExportService.exportTo → resolves VasAccountExportProvider (screenId "vas-account")
        ↓   reads filters.searchWd + filters.statuses (coerced), sort.field/direction
        ↓   VasAccountService.listAllForExport(...)   // same filter/sort path as the list
        ↓   maps each row → FIELD_ID map; Type + Status resolved to LOCALIZED labels via MessageResolver
        ↓   subheader = "<count> <noun>(s)" (noun from audit.vas_account.activity)
        → streams XLSX bytes (controller returns void)
```

## 10. Audit emission

`VasAccountService.appendAudit` / `VasAccountExcelService` call the shared
`AuditLogService.append(AuditLogContext...)` — one row per write:

| Action | detailKey | refNo1 | refNo2 |
|---|---|---|---|
| Create | `audit.vas_account.create.detail` | seqNo | vasAcctCd |
| Update | `audit.vas_account.update.detail` | seqNo | vasAcctCd |
| Soft-delete | `audit.vas_account.delete.detail` | seqNo | vasAcctCd |
| Excel Append | `audit.vas_account.excel_append.detail` | insertedCount | `"APPEND"` |
| Excel Replace | `audit.vas_account.excel_replace.detail` | insertedCount | `"REPLACE"` |

All rows carry `wsvc_id = "vas-account"`, `ref_no = "BUSI_VAS_ACCT_INFM"`,
`activity_key = "audit.vas_account.activity"`.

## 11. Consumption boundary (sibling features — read-only here)

`BUSI_VAS_ACCT_INFM` is a parent for banking surfaces; those features read it **directly via the
repository**, not through `VasAccountService`:

- **SmartOrganizerService** — injects `BusiVasAcctInfmRepository`; resolves display via
  `findByBizKeyAndVasAcctSeqNoAndDelYn(bizKey, seqNo, "N")` (→ `WBE_SMART_ORGANIZER_VAS_NOT_FOUND`),
  and on confirm writes the surrogate `VAS_ACCT_SEQ_NO` + `VAS_ACCT_CD` + `VAS_ACCT_NM` onto
  `ACCT_TRNS_RCV_PAY_INFM`.
- **TransactionService** (banking "assign VAS account") — validates the picked code via
  `findByBizKeyAndVasAcctCdAndDelYn(bizKey, cd, "N")` (→ `CODE_VAS_NOT_FOUND`), checks
  `"A".equals(stts)`, then writes `ACCT_TRNS_RCV_PAY_INFM.VAS_ACCT_CD`; a clear path nulls it.

Soft-delete must NOT cascade — these dependents keep pointing at soft-deleted parents (legacy
behaviour preserved).

## Transactional summary

| Method | Tx | Notes |
|---|---|---|
| `list` / `listAll` / `listAllForExport` / `lookup` / `detail` | `@Transactional(readOnly=true)` | read paths |
| `create` / `update` / `softDelete` | `@Transactional` | write + audit in one tx |
| `excelService.preview` | `@Transactional(readOnly=true)` | validation only |
| `excelService.import_` | `@Transactional` | atomic; Replace wipe + inserts + audit in one tx |
| `ExcelExportService.exportTo` | (no outer tx) | streaming write; inner list call opens its own scope |
