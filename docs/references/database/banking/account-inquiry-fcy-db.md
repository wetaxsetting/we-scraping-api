# Banking > Account Inquiry (FCY) — DB Design

**Refactor screen ID:** `banking-account-inquiry-fcy`
**Feature spec:** [../../features/banking/account-inquiry-fcy.md](../../../features/banking/current-savings-fcy/account-inquiry/README.md)
**JEX reference:** `ACCT_MSTR` table (read-only — same physical table on both sides; same table the VND screen uses)

---

## Legacy tables (JEX — source of truth)

The FCY screen consumes the **same legacy tables** as the VND screen — there is no FCY-specific table. The currency dimension is encoded in the `ACCT_MSTR.CCY_CD` column.

| Table | Role |
|---|---|
| `ACCT_MSTR` | Account master — one row per (tenant × bank × account). FCY rows are those with `CCY_CD <> 'VND'`. |
| `BANK_INFM` | Bank master — bank code → display name + metadata. |
| `BANK_INFM_DETL` | Short bank names (V44) — used by `wb_format_acct_nick_nm` for nickname formatting. |
| `BANK_CERT_INFO` | Bank certification info (joined in legacy `IDO.ACCT_MSTR_R001_CNT.xml` for status display). |

JEX queries that consume these tables for the Account Inquiry screen (shared with VND):
- `IDO.ACCT_MSTR_R002.xml` — totals (count, sum BAL, sum REAL_AMT)
- `IDO.ACCT_MSTR_R001_CNT.xml` — extended summary with **per-currency totals** (`TOT_USD_CNT`, `TOT_KHR_REAL_AMT`, …) and a LEFT JOIN to `BANK_CERT_INFO`. The per-currency columns in this IDO are the legacy equivalent of the refactor's `TOTALS_BY_CCY` array.

---

## Refactor tables (PostgreSQL, mirrors JEX schema column-for-column)

The refactor uses the **same physical schema** as JEX — column names, types, and constraints are mirrored 1:1 in `wecms-api`. The FCY screen requires **zero schema additions** beyond what the VND screen already uses.

| Refactor table | Refactor entity | Notes |
|---|---|---|
| `ACCT_MSTR` | `AcctMstr` (`com.kosign.wecms.domain.banking.entity.AcctMstr`) | Composite PK via `AcctMstrId`. Shared with VND. |
| `BANK_INFM` | (lookup via `BankCdResolver`) | Shared. |
| `BANK_INFM_DETL` | (consumed by `wb_format_acct_nick_nm` PL/pgSQL function) | V44 added the table + 68-row seed for `BANK_DETL_INFO_CD = '10'`. Shared. |

---

## Key columns — `ACCT_MSTR` (FCY-relevant view)

Same column set as VND. The FCY-relevant cells:

| Column | Type | Refactor mapping | Used by this screen |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | `@Id` (composite, first) | **Tenant filter** — every query constrains on this |
| `BANK_CD` | VARCHAR(20) | `@Id` (composite) | Row identity + sort + summary group |
| `ACCT_NO` | VARCHAR(100) | `@Id` (composite) | Row identity + display |
| `ACCT_TYPE_CD` | VARCHAR(2) | `acctTypeCd` | Per legacy `wabooks.enums.AcctTypeCd`: `02` DEPOSIT (Fixed), `03` CURRENT_KHR (Current), `04` CURRENT_USD (Foreign Current), `05` SAVINGS_KHR (Savings), `06` SAVINGS_USD (Foreign Savings), `07` CURRENT_KRW (Current Accounts), `08` SAVINGS_KRW (Saving Accounts). `01` LOAN never on this screen. |
| **`CCY_CD`** | **VARCHAR(3)** | **`ccyCd`** | **Currency filter — `<> 'VND'` for "All Currency" mode; `= '<CCY>'` for concrete CCY narrow.** Values seen on this screen: `USD`, `KRW`, `EUR`, `JPY`, etc. |
| `ACCT_NICK_NM` | VARCHAR(50) | `acctNickNm` (raw) + `formattedAcctNickNm` (`@Formula` calling `wb_format_acct_nick_nm`) | User-assigned display name — formatted for keyword search and UI display |
| `ACCT_NM` | VARCHAR(100) | `acctNm` | Bank account holder name (not directly shown) |
| `BAL` | DECIMAL/NUMERIC | `bal` | **Balance** column — formatted with the row's own `CCY_CD` |
| `REAL_AMT` | DECIMAL/NUMERIC | `realAmt` | **Available Balance** column — formatted with the row's own `CCY_CD` |
| `ACCT_BAL_INQ_STTS` | VARCHAR(20) | `acctBalInqStts` | Inquiry status — drives row error indicator. Success: `00000000`, `42110000` |
| `ACCT_BAL_UPD_DTM` | VARCHAR(14) | `acctBalUpdDtm` | Last update timestamp (legacy `YYYYMMDDHHmmss`) |
| `ACCT_BAL_INQ_MSG` | VARCHAR | `acctBalInqMsg` | Tooltip text when status indicates an error |
| `PRN_SEQ` | NUMERIC(2,0) | `prnSeq` | Manual print/display order (V23+, NULLs sort last) |
| `ACTV_YN` | CHAR(1) | `actvYn` | Active flag — repository constrains on `'Y'` |

Audit columns (handled by `BaseEntity` `@PrePersist` / `@PreUpdate`): `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM`.

The **composite primary key** `(BIZ_KEY, BANK_CD, ACCT_NO)` means a single index serves tenant scope + row identity. The FCY query `WHERE BIZ_KEY=? AND CCY_CD <> 'VND'` uses the PK index for tenant scope; PostgreSQL evaluates the `CCY_CD <> 'VND'` predicate as a residual filter on the page slice.

### Currency value distribution — DEV + LOCAL (tenant `VN_VNM0004_000001`, 2026-05-03 snapshot)

Verified directly against both databases via the `postgres` MCP. The cell pattern is identical on both sides (DEV simply has more rows):

| `CCY_CD` | `ACCT_TYPE_CD` | Legacy enum | LOCAL rows | DEV rows |
|---|---|---|---:|---:|
| VND | `02` | DEPOSIT | 4 | 5 |
| VND | `03` | CURRENT_KHR | 12 | 21 |
| VND | `05` | SAVINGS_KHR | 4 | 6 |
| USD | `02` | DEPOSIT | 1 | 1 |
| USD | `04` | CURRENT_USD | 2 | 6 |
| KRW | `07` | CURRENT_KRW | 2 | 2 |

**Findings:**

- VND rows (`CCY_CD='VND'`, `ACCT_TYPE_CD ∈ {02, 03, 05}`) are excluded by the FCY page's `CCY_CD <> 'VND'` predicate and never appear in the result.
- USD rows currently use codes `02` and `04`. The legacy enum also defines `06` (SAVINGS_USD / Foreign Savings) but no row in this tenant uses it yet.
- KRW rows currently use code `07` only. The legacy enum also defines `08` (SAVINGS_KRW / Saving Accounts) but no row in this tenant uses it yet.
- No rows with `01` (LOAN) on this screen — consistent with legacy behaviour.
- No mismatched `(ccy_cd, acct_type_cd)` pairs (USD never paired with `03`/`05`, VND never paired with `04`/`07`, etc.).

The refactor `BankCdResolver.ACCT_TYPES` map covers all seven non-LOAN codes (`02`–`08`) so any future row using `06` or `08` will render with the correct label automatically. EUR / JPY / other CCYs are forward-looking — none currently in the active tenant.

---

## Balance logic (FCY)

- **`BAL`** = current ledger balance for the account, denominated in the row's `CCY_CD`.
- **`REAL_AMT`** = available balance (ledger minus holds, frozen amounts, etc.), denominated in the row's `CCY_CD`.
- Both stored as numeric in the DB. The DTO serialises them as **string** to preserve precision; the frontend formats via `formatAmount(value, row.CCY_CD, true)`.
- **Per-row currency formatting.** A single response under wire `currency=FCY` contains rows with different `CCY_CD` values — the table renders each cell using the row's own CCY. There is no page-level currency override (unlike the VND page where every row is `VND`).
- Backend totals (`AccountListResponse.TOTALS_BY_CCY`) are computed via `AcctMstrRepository.aggregateByCcy` running the same WHERE chain. **One aggregation row per CCY** — never a mixed-currency sum. The scalar `BALANCE` / `AVAILABLE_BAL` fields in the response are present for parity with the VND shape but are not rendered by the FCY frontend when `TOTALS_BY_CCY` is non-empty.
- Closed / inactive accounts (`ACTV_YN <> 'Y'`) are excluded from both list and totals.
- If `ACCT_BAL_INQ_STTS` indicates an error, `BAL` / `REAL_AMT` may still be the last known value — the row is rendered with a warning glyph but the balance is **not** zeroed out.

### `wb_format_acct_nick_nm` parity

Account nicknames are formatted via the migrated PL/pgSQL function (V44) — same code path as VND. The function reads `BANK_INFM_DETL` (`BANK_DETL_INFO_CD = '10'`) for short bank names and applies the legacy regex cases (PG Bank / VN-prefix / Letters4Digits). The formatted value is exposed on the entity via `AcctMstr.formattedAcctNickNm` (`@Formula`) and consumed by:

- `AccountService.toRow()` — returned in the JSON DTO.
- The keyword filter — `LOWER(COALESCE(wb_format_acct_nick_nm(...), '')) LIKE ? ESCAPE '!'` so the search matches what the user actually sees on screen.
- The Excel export — inherits the same formatted value because list + export share `AccountService.list(...)`.

No FCY-specific behaviour: the formatter is currency-agnostic.

---

## Tenant filtering (BIZ_KEY)

- `BIZ_KEY` is the **first** column of `ACCT_MSTR`'s composite PK — the index naturally scopes every query to the tenant.
- `AcctMstrRepository` exposes only methods that take `bizKey` as the first argument. There is **no method** that omits `bizKey` for `ACCT_MSTR`.
- Repository signatures used by this screen:
  - **`findByBizKeyAndCcyCdNotAndActvYn(bizKey, "VND", "Y", sort)`** — wire `currency=FCY` (the FCY-distinguishing call).
  - `findByBizKeyAndCcyCdAndActvYn(bizKey, ccyCd, "Y", sort)` — wire `currency=USD/KRW/EUR/…` (concrete CCY narrow).
  - Filtered variants (`findFiltered`) compose the same predicate selection inside one JPQL with bank / keyword / sort.
- `bizKey` is sourced from `TenantContext.currentBizKey()` (set by `JwtAuthenticationFilter`). It is **never** read from request input. The config property `wecms.tenant.biz-key` rejects `%` wildcards.
- Single-tenant R&D: pinned to `BIZ_KEY = VN_VNM0004_000001`.

---

## Migration history (relevant slices)

This screen does **not** introduce its own table-level Flyway migration — it consumes the existing `ACCT_MSTR` table. The relevant cross-cutting migrations:

| Migration | Relevance to this screen |
|---|---|
| V1–V10 | Initial Sprint 0 schema, `ACCT_MSTR` carried over |
| V20 | `BANK_INFM` table created + seed (full bank names) |
| V23 | Added `PRN_SEQ` column for manual sort order (default `NULL`, NULLs sort last) |
| V28 | Sprint 1 final parity audit fixes — widened NUMERIC precision across multiple tables; `ACCT_MSTR` unchanged |
| V32 | View Setting cutover — seeded `banking-account-inquiry-vnd` row set in `SYS_SCR_OPTION_WECMS`. |
| V33 | Seeded `CHECKBOX` special FIELD_ID for `banking-account-inquiry-vnd`. |
| V34 | Aligned `SIZE` column to VARCHAR(10) and seeded Figma widths for `banking-account-inquiry-vnd`. |
| V41 | Flipped `VAS_ACCOUNT.SORT_YN='N'` for `banking-account-inquiry-vnd` (column has no backing entity field yet). |
| V42 | `VAS_ACCOUNT.DISP_YN='N'` for `banking-account-inquiry-vnd` (hide from popover). |
| **V43** | **Seeded the FCY screen** (`banking-account-inquiry-fcy` / `default`) — CHECKBOX (special) + BANK / ACCOUNT_NICKNAME / BALANCE / AVAILABLE_BALANCE / VAS_ACCOUNT (DISP_YN/USE_YN/SORT_YN='N') / LAST_UPDATE. Mirrors the VND final state (V32+V33+V34+V41+V42) under the FCY scope. Idempotent. |
| V44 | **Nickname-formatting DB functions.** Adds `BANK_INFM_DETL` table + 68-row seed; installs `wb_get_bank_nm` and `wb_format_acct_nick_nm` from JEX DEV `pg_proc`. `AcctMstr.formattedAcctNickNm` is a Hibernate `@Formula` calling `wb_format_acct_nick_nm`. **Shared with VND — no FCY-specific work needed.** |

For the full migration log see [../../../wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Migrations".

### V43 seed (already shipped)

The FCY-specific seed lives in [V43__view_setting_seed_banking_account_inquiry_fcy.sql](../../../../src/main/resources/db/migration/V43__view_setting_seed_banking_account_inquiry_fcy.sql) and is idempotent (`ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING`):

```sql
INSERT INTO SYS_SCR_OPTION_WECMS
    (WEB_SVC_ID, DYM_AREA_ID, FIELD_ID, FIELD_NM, DISP_ORD,
     USE_YN, FIX_YN, DISP_YN, size, SORT_YN)
VALUES
    ('banking-account-inquiry-fcy', 'default', 'CHECKBOX',          'Checkbox',          0, 'Y', 'Y', 'Y', '38px',  'Y'),
    ('banking-account-inquiry-fcy', 'default', 'BANK',              'Bank',              1, 'Y', 'Y', 'Y', '240px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'ACCOUNT_NICKNAME',  'Account Nickname',  2, 'Y', 'N', 'Y', '280px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'BALANCE',           'Balance',           3, 'Y', 'N', 'Y', '160px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'AVAILABLE_BALANCE', 'Available Balance', 4, 'Y', 'N', 'Y', '180px', 'Y'),
    ('banking-account-inquiry-fcy', 'default', 'VAS_ACCOUNT',       'VAS Account',       5, 'N', 'N', 'N', '200px', 'N'),
    ('banking-account-inquiry-fcy', 'default', 'LAST_UPDATE',       'Last Update',       6, 'Y', 'N', 'Y', '160px', 'Y')
ON CONFLICT (WEB_SVC_ID, FIELD_ID) DO NOTHING;
```

The seed mirrors the VND final state (V32+V33+V34+V41+V42) under the FCY scope. SIZE values copy the Figma WB365 widths used for VND (no separate FCY Figma frame yet); when the FCY page gets its own widths, ship a follow-up migration with UPDATEs targeting `WEB_SVC_ID = 'banking-account-inquiry-fcy'` only. The frontend `FALLBACK_COLUMNS` in [`AccountInquiryPage.tsx`](../../../../../wecms-web/components/shared/AccountInquiryPage.tsx) matches this seed byte-for-byte so paint-time rendering agrees with the API response.

---

## Cross-references

- Feature spec: [../../features/banking/account-inquiry-fcy.md](../../../features/banking/current-savings-fcy/account-inquiry/README.md)
- Backend API: [../../backend/banking/account-inquiry-fcy-api.md](../../api-spec/banking/account-inquiry-fcy-api.md)
- Frontend page: [../../frontend/banking/account-inquiry-fcy-page.md](../../../../../wecms-web/docs/features/banking/current-savings-fcy/account-inquiry/README.md)
- VND twin (read-only reference): [./account-inquiry-vnd-db.md](account-inquiry-vnd-db.md)
- Cross-feature DB mapping: [../db-mapping.md](../table-mapping/db-mapping.md)
- DB parity audit: [../../audits/db-parity-audit-2026-04-30.md](../../audits/db-parity-audit-2026-04-30.md)
- JEX schema reference: [../../../jex/architecture/database-design.md](../../../../../docs/jex/architecture/database-design.md), [../../../jex/architecture/ERD.md](../../../../../docs/jex/architecture/ERD.md)
