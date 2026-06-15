# Chart of Accounts — Business Flow (Backend)

Documents the shipped per-operation flows. Validation order matters — it mirrors `ChartOfAccountsService` + `ChartOfAccountsValidator`.

---

## 1. End-to-end

```
User (ADMIN, holds settings-core-chart-of-accounts grant)
   │
   ▼
Open Settings → Chart of Accounts panel
   │
   ▼
GET /  ───────────────► list (DEL_YN='N', tenant-scoped, sorted)
GET /tree ────────────► parent-picker tree (levels 1..4 admitted, orphans dropped)
   │
   ├── Create / Update ──► hierarchy + uniqueness validation ──► save ──► audit ──► refetch
   ├── Activate/Deactivate ─► single via PUT(stts) | bulk via POST /bulk-deactivate
   ├── Delete ───────────► re-parent children → soft-delete → audit ──► refetch
   └── Excel Import ─────► FE parse+validate → POST /bulk-upload (per-row create) → refetch
```

There is no Income Statement / Trial Balance screen and no export step — downstream report consumers (banking/card/cash-flow) read `CHART_ACCT_CD` separately, in their own modules.

---

## 2. Create flow (`POST /` → `ChartOfAccountsService.create`)

```
1. Load tenant slice (all active rows) once; build activeByCode index
2. assertParentRequired(parent)             → WBE_..PARENT_REQUIRED   (level 1 reserved)
3. assertNotSelfParent(code, parent)        → WBE_..SELF_PARENT
4. assertParentExists(parent, index)        → WBE_..PARENT_NOT_FOUND
5. assertCalRuleLevel3Only(parent, calRule) → WBE_..CAL_RULE_LEVEL3_ONLY
6. depth check (walk parents, cap 3)        → WBE_..DEPTH_EXCEEDED
7. active dup-code check                    → WBE_..DUPLICATE_CODE
8. soft-deleted row with same code?
      yes → REVIVE (flip DEL_YN='N', overwrite, reuse seq_no)
      no  → allocate seq_no (wb_get_next_val) + INSERT
              └─ on DataIntegrityViolationException → retry once → else DUPLICATE_CODE
9. append AUDIT_LOGS_INFM (wsvc_id="chart-of-accounts")
10. return projected ChartOfAccountRow
```

## 3. Update flow (`PUT /{seq}` → `update`)

```
1. fetch active row by seq (tenant)         → WBE_..NOT_FOUND
2. assertNotRestricted(existing.code)        → WBE_..RESTRICTED_CODE   (ACC-01/02/03)
3. assertNotRenamingToRestricted(old,new)    → WBE_..RESTRICTED_CODE
4. assertParentRequired(parent)              → WBE_..PARENT_REQUIRED
5. assertNotSelfParent
6. if code changed → dup-check (excl self)   → WBE_..DUPLICATE_CODE   (code immutable in UI)
7. assertParentExists
8. assertNoCycle(rowCode, newParent, index)  → WBE_..CYCLE
9. assertDepthCap(rowCode, newParent, index) → WBE_..DEPTH_EXCEEDED   (new depth + subtree height)
10. assertCalRuleLevel3Only
11. apply fields; stamp mod_id/mod_dtm; save
12. append audit; return row
```

> **OQ-3 (dropped):** legacy `u001:238-256` ran a bulk "set parent=NULL where parent=?" cleanup that silently orphaned unrelated rows. The refactor does **per-row update only** — that branch is intentionally not ported.

## 4. Delete flow (`DELETE /{seq}` → `delete`)

```
1. fetch active row                          → WBE_..NOT_FOUND
2. assertNotRestricted                        → WBE_..RESTRICTED_CODE
3. reparentActiveChildren(oldParent = existing.parent)   // single @Modifying UPDATE
4. if a soft-deleted row with same code exists → archive-rename own code
        ( prefix(0..4) + last5(seq_no) + 'D' )            // keep UK (BIZ_KEY, code, 'Y')
5. flip DEL_YN='Y'; stamp audit columns
6. append audit
```

Soft-delete only — the legacy hard-delete IDO `D001` is dead code and not ported.

## 5. Activate / Deactivate

| Action | Path | Behaviour |
|---|---|---|
| Activate (single) | `PUT /{seq}` with `stts:"A"` | Only re-activation route (no bulk-activate) |
| Deactivate (single) | `PUT /{seq}` with `stts:"I"` | — |
| Deactivate (bulk) | `POST /bulk-deactivate` | per-seq flip `STTS='I'`, idempotent, restricted → `failed`; `{processed, failed}` |
| Delete (bulk) | `POST /bulk-delete` | per-seq `delete()`; `{processed, failed}` |

## 6. Excel import flow

```
FRONTEND (useExcelUpload.ts, SheetJS)
  parse .xlsx/.xls → resolve columns (header keywords or positional A..E)
  validate each row: code required/≤10/pattern, in-file dup, name_en required,
                     parent required, depth ≤ 3 (in-file parent walk)
  upload disabled while any error
        │
        ▼ POST /bulk-upload  { items: [CreateRequest...] }  (only error-free rows; stts:"A")
BACKEND (ChartOfAccountsService.bulkUpload)
  for each item → self.create(item) via Spring proxy (own @Transactional)
  tally created / failed; partial success allowed (no global rollback)
  return { created, failed }
        │
        ▼ FE shows success / partial-failure dialog → refetch list
```

No backend Apache POI parsing for COA; backend trusts the FE-parsed JSON (CA-G9: does not force `STTS='A'` server-side — FE always sends `"A"`).

---

## Cross-links
- Endpoints & DTOs: [interface-specification.md](interface-specification.md)
- Rules & validation detail: [functional-specification.md](functional-specification.md)
- Frontend flow: [../../../../../wecms-web/docs/features/settings/chart-of-accounts/screen-flow.md](../../../../../wecms-web/docs/features/settings/chart-of-accounts/screen-flow.md)
