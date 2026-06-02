# Banking > Account Inquiry (VND) — DB Design

**Refactor screen ID:** `banking-account-inquiry-vnd`
**Feature spec:** [../../features/banking/account-inquiry-vnd.md](../../../features/banking/current-savings-vnd/account-inquiry.md)
**JEX reference:** `ACCT_MSTR` table (read-only — same physical table on both sides)

---

## Legacy tables (JEX — source of truth)

| Table | Role |
|---|---|
| `ACCT_MSTR` | Account master — one row per (tenant × bank × account) |
| `BANK_INFM` | Bank master — bank code → display name + metadata |
| `BANK_CERT_INFO` | Bank certification info (joined in legacy `IDO.ACCT_MSTR_R001_CNT.xml` for status display) |

JEX queries that consume these tables for the Account Inquiry screen:

- `IDO.ACCT_MSTR_R002.xml` — totals (count, sum BAL, sum REAL_AMT)
- `IDO.ACCT_MSTR_R001_CNT.xml` — extended summary with per-currency totals (`TOT_USD_CNT`, `TOT_KHR_REAL_AMT`, …) and a LEFT JOIN to `BANK_CERT_INFO`

---

## Refactor tables (PostgreSQL, mirrors JEX schema column-for-column)

The refactor uses the **same physical schema** as JEX — column names, types, and constraints are mirrored 1:1 in `wecms-api`. Sprint 1 migrations carried `ACCT_MSTR` over with no shape changes (V28 added `acct_trns_hstr_uk01` UNIQUE on the sibling history table; `ACCT_MSTR` itself is unchanged from JEX DEV).

| Refactor table | Refactor entity | Notes |
|---|---|---|
| `ACCT_MSTR` | `AcctMstr` (`com.kosign.wecms.domain.banking.entity.AcctMstr`) | Composite PK via `AcctMstrId` |
| `BANK_INFM` | (entity TBD — bank-name resolution path may use a lookup service) | |

---

## Key columns — `ACCT_MSTR`

| Column | Type | Refactor mapping | Used by this screen |
|---|---|---|---|
| `BIZ_KEY` | VARCHAR(20) | `@Id` (composite, first) | **Tenant filter** — every query constrains on this |
| `BANK_CD` | VARCHAR(20) | `@Id` (composite) | Row identity + sort + summary group |
| `ACCT_NO` | VARCHAR(100) | `@Id` (composite) | Row identity + display |
| `ACCT_TYPE_CD` | VARCHAR(2) | `acctTypeCd` | `01`=Current, `02`=Savings, `03`=Fixed (legacy code values) |
| `CCY_CD` | VARCHAR(3) | `ccyCd` | **Currency filter** — `'VND'` for this screen |
| `ACCT_NICK_NM` | VARCHAR(50) | `acctNickNm` | User-assigned display name |
| `ACCT_NM` | VARCHAR(100) | `acctNm` | Bank account holder name (not directly shown) |
| `BAL` | DECIMAL/NUMERIC | `bal` | **Balance** column on the table |
| `REAL_AMT` | DECIMAL/NUMERIC | `realAmt` | **Available Balance** column on the table |
| `ACCT_BAL_INQ_STTS` | VARCHAR(20) | `acctBalInqStts` | Inquiry status — drives row error indicator. Success: `00000000`, `42110000` |
| `ACCT_BAL_UPD_DTM` | VARCHAR(14) | `acctBalUpdDtm` | Last update timestamp (legacy `YYYYMMDDHHmmss`) |
| `ACCT_BAL_INQ_MSG` | VARCHAR | `acctBalInqMsg` | Tooltip text when status indicates an error |
| `PRN_SEQ` | NUMERIC(2,0) | `prnSeq` | Manual print/display order (V23+, NULLs sort last) |
| `ACTV_YN` | CHAR(1) | `actvYn` | Active flag — repository constrains on `'Y'` |

Audit columns (handled by `BaseEntity` `@PrePersist` / `@PreUpdate`): `REG_ID`, `REG_DTM`, `MOD_ID`, `MOD_DTM`.

The **composite primary key** `(BIZ_KEY, BANK_CD, ACCT_NO)` means a single index serves tenant scope + row identity. Queries that filter on `BIZ_KEY` first hit this index directly — no extra index needed.

---

## Balance logic

- **`BAL`** = current ledger balance for the account.
- **`REAL_AMT`** = available balance (ledger minus holds, frozen amounts, etc.).
- Both are stored as numeric in the DB. The DTO serializes them as **string** to preserve precision across JSON boundaries; the frontend formats via `formatAmount(value, 'VND')`.
- Backend totals (`AccountListResponse.BALANCE`, `.AVAILABLE_BAL`, `TOTALS_BY_CCY[].BALANCE`) are computed via `AccountService` aggregation over the same row set returned in `ACCOUNTS`. They include only rows the current request would see (after tenant + currency + active filters).
- Closed / inactive accounts (`ACTV_YN <> 'Y'`) are excluded from both list and totals — they are **not** soft-shown anywhere on this screen.
- If `ACCT_BAL_INQ_STTS` indicates an error, `BAL` / `REAL_AMT` may still be the last known value — the row is rendered with a warning glyph but the balance is **not** zeroed out.

---

## Tenant filtering (BIZ_KEY)

- `BIZ_KEY` is the **first** column of `ACCT_MSTR`'s composite PK — the index naturally scopes every query to the tenant.
- `AcctMstrRepository` exposes only methods that take `bizKey` as the first argument. There is **no method** that omits `bizKey` for `ACCT_MSTR`.
- Repository signatures used by this screen:
  - `findByBizKeyAndActvYn(bizKey, actvYn, sort)` — `currency=ALL`
  - `findByBizKeyAndCcyCdAndActvYn(bizKey, ccyCd, actvYn, sort)` — `currency=VND` (this screen)
  - `findByBizKeyAndCcyCdNotAndActvYn(bizKey, ccyCd, actvYn, sort)` — `currency=FCY`
- `bizKey` is sourced from `TenantContext.currentBizKey()` (set by `JwtAuthenticationFilter`). It is **never** read from request input. The config property `wecms.tenant.biz-key` rejects `%` wildcards.
- Sprint 1 R&D: single tenant pinned to `BIZ_KEY = VN_VNM0004_000001`.

---

## Migration history (relevant slices)

This screen does **not** introduce its own Flyway migration — it consumes the existing `ACCT_MSTR` table. The relevant cross-cutting migrations:

| Migration | Relevance to this screen |
|---|---|
| V1–V10 | Initial Sprint 0 schema, `ACCT_MSTR` carried over |
| V20 | `BANK_INFM` table created + seed (full bank names) |
| V23 | Added `PRN_SEQ` column for manual sort order (default `NULL`, NULLs sort last) |
| V28 | Sprint 1 final parity audit fixes — widened NUMERIC precision across multiple tables; `ACCT_MSTR` itself unchanged |
| V32 | View Setting cutover — seeded `banking-account-inquiry-vnd` row set in `SYS_SCR_OPTION_WECMS` (View Setting tables, not `ACCT_MSTR`) |
| V33 | Seeded `CHECKBOX` special FIELD_ID for this screen |
| V34 | Aligned `SIZE` column to VARCHAR(10) and seeded Figma widths for this screen's columns |
| V44 | **Nickname-formatting DB functions.** Adds `BANK_INFM_DETL` table (short bank names) + 68-row seed for `BANK_DETL_INFO_CD = '10'`. Installs `wb_get_bank_nm(varchar, varchar)` and `wb_format_acct_nick_nm(text, text, text, text)` verbatim from JEX DEV `pg_proc`. `AcctMstr.formattedAcctNickNm` is a Hibernate `@Formula` that calls `wb_format_acct_nick_nm` on entity load. Read-only legacy function migration — strategy decision: parity-critical SQL helpers stay as DB functions; new logic + write-side procedures stay in Java. |

For the full migration log see [../../../wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Migrations".

---

## Cross-references

- Feature spec: [../../features/banking/account-inquiry-vnd.md](../../../features/banking/current-savings-vnd/account-inquiry.md)
- Backend API: [../../backend/banking/account-inquiry-vnd-api.md](../../api-spec/banking/account-inquiry-vnd-api.md)
- Frontend page: [../../frontend/banking/account-inquiry-vnd-page.md](../../../../../wecms-web/docs/features/banking/current-savings-vnd/account-inquiry.md)
- Cross-feature DB mapping: [../db-mapping.md](../table-mapping/db-mapping.md)
- DB parity audit: [../../audits/db-parity-audit-2026-04-30.md](../../audits/db-parity-audit-2026-04-30.md)
- JEX schema reference: [../../../jex/architecture/database-design.md](../../../../../docs/jex/architecture/database-design.md), [../../../jex/architecture/ERD.md](../../../../../docs/jex/architecture/ERD.md)
