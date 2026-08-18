# Smart Organizer — Business Flow (Backend)


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../../../references/database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../../../references/database/flyway-migration-baseline.md) §5.

How the refactored Spring Boot implementation processes a Smart Organizer session, end to end. The
*what* (fields, columns, rules) lives in [functional-specification.md](functional-specification.md);
this doc is the *how*. Source of truth = JEX `fnsh_0200_001` / `fnsh_0200_002`
([docs/jex/features/smart-organizer/business-flow.md](../../../../../docs/jex/features/smart-organizer/business-flow.md)).

```
Bank Transaction (ACCT_TRNS_HSTR, already synced)
        │
        ▼
Smart Organizer eligibility query   (GET /eligible — account-scoped)
        │   ├─ tenant + account scope (R11)
        │   ├─ account-type filter ('03','04','05','06')
        │   ├─ NOT already in ACCT_TRNS_RCV_PAY_INFM (unconfirmed only)
        │   └─ LATERAL match to RMRK_ORG_INFM (R01)
        ▼
Matched rule supplies the proposal
        ├─ Receive/Pay direction (R03)
        ├─ Reporting Account (CHART_ACCT_CD / CHART_ACCT_SEQ_NO)
        ├─ Purpose (USE_USAG_CD)
        ├─ VAS Account (VAS_ACCT_SEQ_NO)  → resolved to code + name (R05/R07)
        └─ Contact (TRCO_KEY)             → resolved to name + enm (R05/R07)
        ▼
resolveRows() denormalizes codes → display names (R07)
        ▼
User reviews + selects rows, edits classification
        ▼
Bulk confirm   (POST /confirm — account-scoped, R11)
        ├─ per-row validation (R03): tenant existence of purpose / VAS / contact
        ├─ allocate RCV_PAY_SEQ_NO via wb_get_next_val
        └─ write ACCT_TRNS_RCV_PAY_INFM with denormalized VAS + contact (R05)
        ▼
Persisted Transaction (CNFM_YN='Y') — drops out of the next eligibility list
```

---

## 1. Eligibility flow (list)

`SmartOrganizerService.listEligible(bankCd, acctNo, fromDt, toDt, search, sortField, sortDir, page, size)`
— `@Transactional(readOnly = true)`.

1. **Tenant scope.** `requireBizKey()` reads `BIZ_KEY` from `TenantContext`; blank → `WBE1009`.
2. **Account scope (R11).** `bank_cd` + `acct_no` are mandatory (`requireAccount`); missing → client
   error, never a tenant-wide list. `accountAccessGuard.assertCanView(bizKey, bankCd, acctNo)` enforces
   per-account authority (admins pass; non-admins need a `V` grant in `ACCT_AUTH_INFO`).
3. **Native eligibility query** (`EntityManager.createNativeQuery`). One row per eligible transaction:
   - `FROM acct_trns_hstr h`
   - `JOIN LATERAL (… rmrk_org_infm r … ORDER BY r.disp_ord ASC, r.rmrk_seq_no ASC LIMIT 1) r ON TRUE`
     — picks the single winning rule (R01, §2).
   - `WHERE h.biz_key = :bizKey AND h.bank_cd = :bankCd AND h.acct_no = :acctNo`
   - `AND h.trsc_dt BETWEEN :fromDt AND :toDt`
   - `AND EXISTS (… acct_mstr m … m.acct_type_cd IN ('03','04','05','06'))` — only current/savings VAS
     account types are eligible (excludes loan / time-deposit).
   - `AND NOT EXISTS (… acct_trns_rcv_pay_infm c WHERE c.biz_key = h.biz_key AND c.acct_seq_no = h.acct_seq_no)`
     — already-confirmed transactions are excluded.
   - Optional `AND h.rmrk ILIKE '%' || :search || '%'` when a keyword is supplied.
   - Projects `CCY_CD` + `BAL` (R06) and `IN_AMT`/`OUT_AMT` derived from `INOUT_DV`.
4. **Sort (whitelist).** `SmartOrganizerSortField.parse(sortField)` maps a wire key to a safe SQL
   expression; unknown → default `TRSC_DT` DESC. Direction clamped to `ASC`/`DESC`.
5. **Pagination.** `page` (1-based) + `size` (default 15, clamped ≤ 100) → `LIMIT/OFFSET`. A companion
   count query feeds `totalCount`.
6. **Display-name resolution (R07).** `resolveRows(...)` (§5) turns codes into names.

## 2. Matching engine (R01)

The match is expressed entirely in the `LATERAL` sub-join, mirroring legacy
`IDO.ACCT_TRNS_HSTR_R028.xml:87-92`:

```sql
WHERE r.biz_key = h.biz_key
  AND ( ( r.rcv_pay_dv = CASE WHEN h.inout_dv = '1' THEN 'RCV' ELSE 'PAY' END
          AND r.match_type = 'E' AND r.rmrk = h.rmrk )
     OR ( r.match_type <> 'E' AND h.rmrk ILIKE '%' || r.rmrk || '%' ) )
ORDER BY r.disp_ord ASC, r.rmrk_seq_no ASC
LIMIT 1
```

- **Exact rule (`MATCH_TYPE='E'`)** matches only when the remark is identical **and** the rule's
  direction (`RCV_PAY_DV`) aligns with the transaction direction (`INOUT_DV` `1`→RCV, `2`→PAY).
- **Contains rule (`MATCH_TYPE<>'E'`)** matches on case-insensitive substring **regardless of
  direction** — this is the legacy unparenthesized `AND … OR …` precedence, **deliberately preserved**
  per PO decision 2026-06-06.
- **Priority:** lowest `DISP_ORD` wins; `RMRK_SEQ_NO` is the deterministic tiebreaker.
- **Tenant boundary kept:** both arms keep `r.biz_key = h.biz_key`, so the refactor does **not**
  reproduce the legacy cross-tenant leak.

## 3. Confirmation flow (write) — `confirm(ConfirmRequest)`

`@Transactional`. Validates scope once, then iterates the requested rows:

1. **Scope (R11).** `requireBizKey()` + `requireAccount(bankCd, acctNo)` +
   `accountAccessGuard.assertCanView(...)`.
2. **Per row:**
   - Load `ACCT_TRNS_HSTR` by `(bizKey, acctSeqNo)` → `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND` (404)
     if missing.
   - **Anti-smuggling (R11):** the loaded row's `BANK_CD`/`ACCT_NO` must equal the request's scoped
     account, else `WBE_SMART_ORGANIZER_TRANSACTION_NOT_FOUND`.
   - **Idempotency:** if a `CNFM_YN='Y'` row already exists for `(bizKey, acctSeqNo)` →
     `WBE_SMART_ORGANIZER_ALREADY_CONFIRMED`.
   - **Classification existence checks (R03):** non-blank `USE_USAG_CD` must exist
     (`WBE_SMART_ORGANIZER_USAGE_NOT_FOUND`); `VAS_ACCT_SEQ_NO` must exist and be active/non-deleted
     (`WBE_SMART_ORGANIZER_VAS_NOT_FOUND`); `TRCO_KEY` must exist
     (`WBE_SMART_ORGANIZER_CONTACT_NOT_FOUND`).
   - **Sequence:** `legacy.nextVal("acct_trns_rcv_pay_infm","rcv_pay_seq_no", bizKey, acctSeqNo, …)`
     allocates `RCV_PAY_SEQ_NO`; blank → `WBE9999`.
   - **Write `ACCT_TRNS_RCV_PAY_INFM`** (R05, §4) and increment the counter.
3. **Return** `ConfirmResponse(confirmedCount)`.

## 4. Persistence fidelity (R05)

The confirmation row denormalizes the resolved VAS account and contact at write time, matching legacy
`fnsh_0200_001_c001_act.jsp:182-188`:

| Column | Value written |
|---|---|
| `RCV_PAY_TRCO_KEY` | request `trco_key` |
| `RCV_PAY_TRCO_NM` | resolved `TRCO_INFM.TRCO_CMNM` (local name) |
| `RCV_PAY_TRCO_ENM` | resolved `TRCO_INFM.TRCO_CMENM` (English name) |
| `VAS_ACCT_SEQ_NO` | request `vas_acct_seq_no` (surrogate) |
| `VAS_ACCT_CD` | resolved `BUSI_VAS_ACCT_INFM.VAS_ACCT_CD` (the **real code**, not the surrogate) |
| `VAS_ACCT_NM` | resolved `BUSI_VAS_ACCT_INFM.VAS_ACCT_NM` |
| `RCV_PAY_USAG_CD` | request `use_usag_cd` |
| `RCV_PAY_AMT` | source `ACCT_TRNS_HSTR.TRSC_AMT` |
| `CNFM_YN` / `CNFM_BY_USER_ID` / `CNFM_BY_USER_NM` / `CNFM_DTM` | `'Y'` + current user + now |
| `REG_*` / `MOD_*` | current user + now |

> **Pre-remediation bug (now fixed):** the earlier code wrote the VAS surrogate into `VAS_ACCT_CD` and
> never wrote the names. R05 restores legacy denormalization.

## 5. Display-name resolution (R07) — `resolveRows()`

Codes alone are unhelpful in the grid, so each eligible row is enriched with resolved names. Lookups
are **memoised per page** (one cache map per dimension) to avoid N+1 queries:

| Code | Resolved to | Source |
|---|---|---|
| `BANK_CD` | bank name | `BankCdResolver.resolveBankNm()` |
| `BANK_CD` + `ACCT_NO` | account nickname | `AcctMstrRepository.findByBizKeyAndBankCdAndAcctNo()` |
| `CHART_ACCT_CD` | reporting-account name | `ChartOfAccountRepository` |
| `USE_USAG_CD` | purpose label (code + name) | `PurposeSettingsRepository` |
| `VAS_ACCT_SEQ_NO` | VAS label (code + name) | `BusiVasAcctInfmRepository` |
| `TRCO_KEY` | contact label (code + name) | `TrcoInfmRepository` |

`BAL` (running balance) and `CCY_CD` are projected straight from the source row (R06/R07). The chart
"level breadcrumb" field is reserved/leaf-name only in the refactor (see gap analysis).

## 6. VND and FCY — one path

There is **no** currency branch. The eligibility query always projects `h.ccy_cd` and `h.bal`; the
account's own currency (from `ACCT_TRNS_HSTR.CCY_CD`, ultimately `ACCT_MSTR.CCY_CD`) is carried on the
wire and the client formats amounts by it (R06). A VND account renders `₫`; a USD account renders `$`;
a KRW account renders `₩`. The only currency-aware server surface is the dual menu permission
(`banking-vnd-inquire-transactions` OR `banking-fcy-inquire-transactions`).

## 7. Organizer Settings — rule CRUD + reorder

`OrganizerRuleService` backs the settings popup:

- **list()** — `findByBizKeyOrderByDispOrdAscRmrkSeqNoAsc(bizKey)` → `OrganizerRuleListResponse`.
- **create()** — allocates `RMRK_SEQ_NO` via the `wb_get_next_val` rule branch (V173); if `dispOrd`
  is null, computes `MAX(DISP_ORD)+1` for the tenant; stamps `REG_ID`/`REG_DTM` (this table has **no**
  `MOD_*` columns — legacy parity).
- **update()** — loads by `(bizKey, rmrkSeqNo)`, overwrites fields (`DISP_ORD` only when non-null),
  preserves `REG_*`; missing rule → `WBE_SMART_ORGANIZER_RULE_NOT_FOUND`.
- **delete()** — hard delete by `(bizKey, rmrkSeqNo)`; count 0 → `WBE_SMART_ORGANIZER_RULE_NOT_FOUND`.
- **reorder()** — per entry, rejects duplicates (`WBE_SMART_ORGANIZER_RULE_DUPLICATE_REORDER`) then
  `updateDispOrd(bizKey, rmrkSeqNo, dispOrd)`; missing rule → `WBE_SMART_ORGANIZER_RULE_NOT_FOUND`.

Because `RCV_PAY_DV` is stored on the rule, **PAY rules can be created** (R03) — the early refactor
defaulted every rule to RCV, so PAY transactions never matched. Direction is a user choice in the
refactor (legacy derived it from `BUSI_USAG_SET_INFM.USAG_GRP_CD`, which is unpopulated here — see gap
analysis).

---

See also: [database-mapping.md](database-mapping.md) · [interface-specification.md](interface-specification.md) ·
[refactor-gap-analysis.md](refactor-gap-analysis.md).
