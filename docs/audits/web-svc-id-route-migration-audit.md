# WEB_SVC_ID → Route Migration Audit

- **System:** Refactor (wecms-api + wecms-web)
- **Date:** 2026-07-02
- **Author:** Claude Code (AI)
- **SQL script:** [../sql/web-svc-id-varchar-100-migration.sql](../sql/web-svc-id-varchar-100-migration.sql)
- **Status:** Analysis complete — **DDL + data SQL PREPARED, execution PO-GATED** (no DDL/DML was run; the `postgres_dev_server_*` MCPs are read-only).

> **Document organization.** This audit is split by **identifier type** so
> DBAs, reviewers, and the PO can review each independently. The three
> families are never mixed:
> - **§2 WEB_SVC_ID** — screen / page / route id. DDL **and** the data migration.
> - **§3 WSVC_ID** — audit-log screen id. DDL only (no data migration).
> - **§4 SVC_ID** — external/framework service id. Investigation only — **excluded**.
>
> The SQL file mirrors this: `STEP 1` (WEB_SVC_ID DDL), `STEP 2` (WSVC_ID
> DDL), `STEP 3` (WEB_SVC_ID data migration), `STEP 4` (verification),
> `APPENDIX` (excluded SVC_ID).

---

# 1. Purpose

Prepare and document a safe migration so that the WeCMS **screen / page /
route identifier** columns can hold the **modern route-based identifier**
rather than a legacy JEX screen id, and migrate the one legacy data row
that still carries the old id.

Primary mapping (WEB_SVC_ID only — see §2):

| | Value | Length |
|---|---|---|
| **Legacy** | `card_0001_001` | 13 |
| **Modern route** | `/payment/corporate-card/card-transactions` | **41** |

**Background**

- Memo and other shared refactor features now run in Next.js + Spring.
  `MEMO_INFM.WEB_SVC_ID` is the discriminator each feature's memo thread
  is stored/read under.
- The refactor already treats the memo scope as the **modern route**: the
  single source of truth is the enum
  [`com.kosign.wecms.common.memo.MemoScope`](../../src/main/java/com/kosign/wecms/common/memo/MemoScope.java),
  and `CARD_TRANSACTIONS` **already returns**
  `/payment/corporate-card/card-transactions` on disk today. **The code
  switch is done** — this migration only aligns the **database** (column
  widths + the one stale data row) to what the code already emits.
- Old UAT/PROD (live JEX) schemas define these columns as `varchar(20)`
  or `varchar(50)`. A 41-char route **overflows `varchar(20)`** — this is
  a real risk, confirmed on the audit column (§3).

> **Naming note (important, for reviewer awareness).** The refactor has
> **two distinct** modern-id conventions already live in the DB, and they
> are not the same string:
> - **Memo** (`MemoScope`) uses the **full route path with leading slash**:
>   `/payment/corporate-card/card-transactions`.
> - **View Setting** (`SYS_SCR_OPTION_WECMS`) and **audit logs**
>   (`AUDIT_LOGS_INFM.WSVC_ID`) use a **bare kebab slug** (no slash):
>   `card-transactions`, `firm-banking-settings`, … — and the View Setting
>   `screenId` `@Pattern` (`^[a-z][a-z0-9]*(-[a-z0-9]+)+$`) *rejects*
>   slashes, so the full route path is **not** a valid View Setting id.
>
> This audit follows the mapping the user requested verbatim
> (`/payment/corporate-card/card-transactions`) because that is exactly
> what `MemoScope.CARD_TRANSACTIONS` emits. If the PO instead wants memo to
> use the bare slug `card-transactions` (to match View Setting / audit),
> that is a **one-line change in `MemoScope`** plus the corresponding SQL
> literal — flagged as an open decision in §2.

---

# 2. WEB_SVC_ID Audit

**Meaning:** the screen / page / route identifier used by WeCMS. This is
the only family that carries the data migration.

## 2.1 Tables

All `character varying`, introspected 2026-07-02 on the refactor DB
(`172.168.5.172:5432/wecmsdevdb`) and JEX DEV (`10.254.242.254:54311/wecmsvndevdb`).

| # | Table | Column | Current len | In PK? | Refactor use | Widen? |
|---|---|---|---|---|---|---|
| 1 | `memo_infm` | `web_svc_id` | **50** | ✅ `(biz_key, web_svc_id, seq_no)` | **Memo — writes the route** (MemoScope) | ✅ |
| 2 | `memo_infm_file` | `web_svc_id` | **50** | ✅ `(biz_key, web_svc_id, seq_no, file_id)` | Memo attachments (branch present, unwired) | ✅ |
| 3 | `sys_scr_option_wecms` | `web_svc_id` | **50** | ✅ `(web_svc_id, field_id)` | View Setting system defaults (slugs) | ✅ |
| 4 | `user_scr_option_wecms` | `web_svc_id` | **50** | ✅ `(biz_key, user_id, web_svc_id, field_id)` | View Setting user overrides | ✅ |
| 5 | `menu_auth_admn_sys` | `web_svc_id` | **50** | ✅ `(web_svc_id, prdt_type)` | Menu authorization catalogue | ✅ |
| 6 | `user_menu_auth_infm` | `web_svc_id` | **50** | ✅ `(biz_key, user_id, web_svc_id)` | Per-user menu grants | ✅ |
| 7 | `sys_dshbrd_infm` | `web_svc_id` | **50** | ✅ `(web_svc_id)` | Dashboard (system) | ✅ |
| 8 | `user_dshbrd_infm` | `web_svc_id` | **50** | ✅ `(biz_key, user_id, web_svc_id)` | Dashboard (per-user) | ✅ |
| 9 | `ext_tran_call_his` | `web_svc_id` | **50** | ❌ (PK is `svc_id, ref_date, seq_no`) | External-call history (calling screen) | ✅ |

**Constraints/indexes:** every WEB_SVC_ID column above sits in its table's
**PRIMARY KEY** (except `ext_tran_call_his.web_svc_id`). **No FOREIGN KEY**
anywhere references `web_svc_id` (verified via `pg_constraint`) — so
widening needs no paired change on any other table. Increasing a `varchar`
limit in PostgreSQL is a **catalog-only** operation (≥ PG 9.2): no table
rewrite, **no PK/index rebuild**, no data loss, nullability + defaults
preserved.

**Live-JEX-only WEB_SVC_ID columns (UAT/PROD schema, absent from refactor
DB):** present on the JEX schema but **not written by the refactor** —
widening is optional future-proofing only (STEP 1b, commented out in the
SQL): `busi_user_hide_menu_bar.web_svc_id`, `date_filter.web_svc_id`,
`genl_noti_info.web_svc_id`, `scrn_field_infm.web_svc_id`,
`user_menu_action_auth_infm.web_svc_id` (all `varchar(50)`). **PO
confirmation needed** to include these.

## 2.2 Current length → New length

| Column | Current (refactor) | Current (JEX UAT/PROD) | New | Overflow risk today |
|---|---|---|---|---|
| `memo_infm.web_svc_id` | 50 | 50 | **100** | Route (41) **fits 50** — widen for headroom/consistency |
| all other WEB_SVC_ID (§2.1) | 50 | 50 | **100** | Longest live slug `payment-internal-transfer-scheduled-detail` (42) fits 50; widen for headroom |

Widening to `varchar(100)` covers the 41-char route plus any longer future
route/slug.

## 2.3 DDL

**SQL:** `STEP 1` in [../sql/web-svc-id-varchar-100-migration.sql](../sql/web-svc-id-varchar-100-migration.sql)
— idempotent `DO` loop that alters only WEB_SVC_ID columns present in the
connected schema and currently `< 100`. `STEP 1b` (5 legacy-only columns)
is commented out pending extra PO opt-in. DBA-run manual script (NOT
Flyway; Flyway is disabled per `project_flyway_disabled_ddl_validate`).

## 2.4 Data migration

**SQL:** `STEP 3` (PO-GATED). The data migration belongs **only** to the
WEB_SVC_ID family — it is the only family that ever stored the Card
Transactions screen id.

```sql
-- Run STEP 3a (collision pre-check) first: it MUST return zero rows.
UPDATE memo_infm
   SET web_svc_id = '/payment/corporate-card/card-transactions'
 WHERE web_svc_id = 'card_0001_001';

UPDATE memo_infm_file
   SET web_svc_id = '/payment/corporate-card/card-transactions'
 WHERE web_svc_id = 'card_0001_001';
```

Idempotent (re-run matches 0 rows); applied only where the legacy value
exists.

> **This data step already exists as a standalone script:**
> [`Claude Code (AI)/sql/memo_web_svc_id_card_transactions_20260702.sql`](../../../Claude%20Code%20%28AI%29/sql/memo_web_svc_id_card_transactions_20260702.sql)
> (identical UPDATEs + a `RAISE EXCEPTION` collision guard, wrapped in
> `BEGIN/COMMIT`). **Run EITHER that script OR STEP 3 — not both.** The
> new value-add of *this* migration is the **DDL widening**, which the
> standalone script does not cover.

**Data mapping (counts):** `card_0001_001` → `/payment/corporate-card/card-transactions`
in `memo_infm` and `memo_infm_file`.

> **Re-checked 2026-07-02 (extended audit): the refactor DB is ALREADY
> migrated.** `memo_infm.web_svc_id` now holds exactly **1** row =
> `/payment/corporate-card/card-transactions` and **ZERO** rows carry
> `card_0001_001` in ANY `web_svc_id` / `wsvc_id` / `svc_id` column (full
> sweep in §6). The pre-existing standalone data script has been run on
> `172.168.5.172/wecmsdevdb`. STEP 3 is now a safe **no-op** on the
> refactor DB and is retained only for UAT/PROD DBs that may still carry
> the legacy id.

No other table stores `card_0001_001` in a `WEB_SVC_ID` column, and no
`WSVC_ID` / `SVC_ID` column stores it either.

## 2.5 Application usage impact

**Backend (`wecms-api`):**
- **Source of truth:** `MemoScope` enum. `CardTransactionsService`
  references `MemoScope.CARD_TRANSACTIONS.webSvcId()` (constant
  `MEMO_WEB_SVC_ID_CARD`) — **no raw route literal outside the enum**.
- `web_svc_id` is passed to `wb_get_next_val('memo_infm','seq_no',biz_key,web_svc_id)`
  (via `LegacyFunctionRepository.nextMemoInfmSeqNo`) and used in
  `MemoInfmRepository` PK lookups — all parameterized by the scope value,
  so they follow whatever `MemoScope` emits. No SQL literal to change.
- Entities map `@Column(name="WEB_SVC_ID")` as `String` — **no Java change
  needed** for the width increase (`ddl-auto=validate` checks type, not
  `varchar` length).
- **No API contract, DTO, memo CRUD, author-only edit/delete, colour
  propagation, Transaction History, `wb_get_next_val`, or MemoDrawer
  behaviour is touched.**

**Frontend (`wecms-web`):** `WEB_SVC_ID="card_0001_001"` appears only in
**comments** (legacy citations). No executable frontend code sends or
compares `card_0001_001`; the memo scope value is server-side. No frontend
change required.

## 2.6 Verification

```sql
-- No memo rows still carry the legacy id (expect 0 after STEP 3):
SELECT 'memo_infm' AS tbl, count(*) FROM memo_infm WHERE web_svc_id='card_0001_001'
UNION ALL
SELECT 'memo_infm_file', count(*) FROM memo_infm_file WHERE web_svc_id='card_0001_001';

-- Migrated rows visible under the modern route:
SELECT web_svc_id, count(*) FROM memo_infm
WHERE web_svc_id IN ('card_0001_001','/payment/corporate-card/card-transactions')
GROUP BY web_svc_id;
```

(The `varchar(100)` length check is shared with §3 — see `STEP 4a`.)

## 2.7 Risk analysis (whole migration)

| # | Risk | Severity | Mitigation |
|---|---|---|---|
| R1 | **Live-JEX coexistence break (STEP 3).** On a DB shared with the live JEX system, changing `MEMO_INFM.WEB_SVC_ID` away from `card_0001_001` **hides** existing memos from the still-live JEX Card screen (which filters `WEB_SVC_ID='card_0001_001'`), and JEX keeps creating new memos under `card_0001_001` that the refactor (querying the route) won't see → split-brain. | **HIGH** | **PO decision required** before STEP 3. This is inherent to the code-level switch already in `MemoScope`, not introduced by this SQL. If full JEX↔refactor memo interop must be preserved, keep the legacy id in `MemoScope` and skip STEP 3. Note the prior memory `project_memo_drawer_static_standalone` recorded D1 as "keep `card_0001_001` for coexistence" — the on-disk `MemoScope` now diverges from that; confirm the PO decision is current. |
| R2 | Schema change to **legacy tables on the shared/live DB**. | MED | Widening is catalog-only (no rewrite/rebuild/loss); nullability + defaults preserved; delivered as a **manual DBA script**, PO-gated, not Flyway. |
| R3 | **PK collision** on STEP 3 (`memo_infm` PK includes `web_svc_id`). | MED | STEP 3a collision pre-check must return 0 rows; STEP 3 aborts safely on violation (transaction rolls back). |
| R4 | `varchar(20)` overflow on JEX audit column already truncating/failing slug writes. | MED | STEP 2 raises `audit_logs_infm.wsvc_id` to 100 on every env. |
| R5 | Value-format inconsistency (memo full-path vs slug elsewhere, §1 note). | LOW | Cosmetic; both forms fit `varchar(100)`. Resolve via the open decision below if desired. |
| R6 | Narrowing rollback would truncate. | LOW | Rollback notes flag widening as forward-only. |

## 2.8 Open decision & PO confirmation

**OD — memo scope value format.** `MemoScope.CARD_TRANSACTIONS` currently
emits the **full route** `/payment/corporate-card/card-transactions`,
while View Setting + audit logs use the **bare slug** `card-transactions`.
Either is fine for `varchar(100)`. Options:
- **(a) Full route** (current code) — keep as-is; run this migration verbatim.
- **(b) Bare slug** `card-transactions` — change the one string in
  `MemoScope`, and change the STEP 3 SQL literal to match.

This audit ships option (a) as requested.

| Item | Question |
|---|---|
| 5 legacy-only JEX `web_svc_id` columns (§2.1) | Refactor never writes routes to them. Widen for future-proofing (STEP 1b, commented out) — **yes/no**? |
| Memo value format (OD above) | Full route vs bare slug? |
| STEP 3 on UAT/PROD | The coexistence impact (R1) still applies to any live-JEX DB that has not yet run the memo re-scope. |

---

# 3. WSVC_ID Audit

**Meaning:** the **audit-log screen identifier**. **No data migration, no
memo migration, no route mapping** belongs here — this column holds slugs
only.

## 3.1 Tables

Only one column in the family: **`audit_logs_infm.wsvc_id`**.

| DB | Type | Len | Nullable | PK | Stored values (sample) |
|---|---|---|---|---|---|
| refactor `wecmsdevdb` | varchar | **50** | NO | ✅ `(biz_key, audit_logs_seq_no, wsvc_id, lang_cd)` | route **slugs**: `own-account-transfer`, `card-transactions`, `chart-of-accounts`, `firm-banking-settings`, … |
| JEX `wecmsvndev` | varchar | **20** | NO | ✅ (same) | (live JEX audit ids) |

- **Code:** written via `AuditLog.wsvcId(...)` (builder) across services —
  always a **route slug** (`firm-banking-settings`, `own-account-transfer`,
  `card-transactions`, `contacts`, `chart-of-accounts`, `login-credentials`, …);
  `AuditLogService.append` swallows a blank `wsvcId`. No length/pattern
  validation annotation on the field.
- `card_0001_001` is **not** used here — Card Transactions audit rows use
  the `card-transactions` slug.

Other `WSVC`-named columns (not `WSVC_ID`), **excluded**:
`bltb_noti_infm.noti_wsvc` (varchar **100**, already long enough) and
`menu_item.legacy_wsvc_id` (varchar **100**, refactor-owned, *intentionally*
stores the legacy id).

## 3.2 Current length → New length

| Column | Current (refactor) | Current (JEX UAT/PROD) | New | Overflow risk today |
|---|---|---|---|---|
| `audit_logs_infm.wsvc_id` | 50 | **20** | **100** | **YES on JEX** — `firm-banking-settings` (21) already > 20 |

## 3.3 DDL

**SQL:** `STEP 2` in the migration file — a separate idempotent `DO` block
that widens `audit_logs_infm.wsvc_id` → `varchar(100)`. Same catalog-only
safety and idempotency profile as STEP 1 (`wsvc_id` is part of the PK;
widening does not rebuild it). **No DML.**

## 3.4 Verification

```sql
-- After STEP 2, wsvc_id is varchar(100):
SELECT table_name, column_name, character_maximum_length
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name = 'audit_logs_infm' AND column_name = 'wsvc_id';
```

(Also covered by the combined length check `STEP 4a`.)

---

# 4. SVC_ID Investigation

> **These are external / framework service identifiers, NOT screen
> identifiers. Therefore they are intentionally excluded from this
> migration.** No `SVC_ID` column is altered.

## 4.1 Tables, meaning, why excluded

| DB | Table.column | Type | Len | Nullable | PK | Meaning / stored values | Why excluded |
|---|---|---|---|---|---|---|---|
| refactor + JEX | `ext_tran_call_his.svc_id` | varchar | 50 | NO | ✅ `(svc_id, ref_date, seq_no)` | **External GDT/integration service** — `GDT_INQUIRY_SALES`, `GDT_INQUIRY_EXPENSES`, `REAL_TIME_ONLINE_INQUIRY`, `BI09` | Different meaning — not a screen route. The **screen** for this table lives in the *separate* `ext_tran_call_his.web_svc_id` (`addon-einvoices-scrape`), which **is** widened in §2 |
| refactor + JEX | `api_tran_log_his.svc_id` | varchar | 50 | NO | ✅ `(trsc_dt, trsc_tm, user_id, svc_id)` | **API-transaction-log** service id (empty on refactor DB) | Different meaning — not a screen route |
| JEX only | `jex_ad_lgin_trsc_log.svc_id` | varchar | **100** | — | — | JEX **framework** login-trace log (holds legacy action ids) | Framework table (CLAUDE.md exclude-rule 1), absent from refactor DB + already 100 |
| JEX only | `jex_ad_trsc_log.svc_id` | varchar | 50 | — | — | JEX **framework** transaction log (`comm_0108_001_r001_osmc`, `ERP_0100_002`, `szon_0012_003_r002`, …) | Framework table, not in refactor scope |
| JEX only | `jex_ad_trsc_str_log.svc_id` | varchar | 50 | — | — | JEX **framework** transaction-store log | Framework table, not in refactor scope |

## 4.2 Code evidence

- `ext_tran_call_his.svc_id` — written by `EInvoiceScrapeService.writeAudit`
  as `GDT_INQUIRY_SALES` / `GDT_INQUIRY_EXPENSES`; read/filtered by
  `EInvoiceHistoryService` (`SVC_ID LIKE 'GDT_INQUIRY%'`) and exposed on the
  e-Invoice history API response as `svc_id` + a derived `type`
  (SALES/EXPENSE). It is a PK column. The route slug for the same table
  (`addon-einvoices-scrape`) is on the *separate* `web_svc_id` column.
- `svcId` in Java appears **only** for `ext_tran_call_his` (the write, the
  read, and the `EInvoiceHistoryRow.svcId` DTO field) — never as a screen
  route. No change required or made.

## 4.3 Database evidence

`ext_tran_call_his` (refactor DB) distinct values:

| web_svc_id | svc_id | rows |
|---|---|---|
| `REAL_TIME_ONLINE_INQUIRY` | `REAL_TIME_ONLINE_INQUIRY` | 37 |
| `aosv_0001_001_r001` | `BI09` | 22 |

`svc_id` holds external service names (`BI09`, `REAL_TIME_ONLINE_INQUIRY`);
`api_tran_log_his.svc_id` is empty on the refactor DB; the `jex_ad_*` tables
do not exist on the refactor DB. **Zero `card_0001_001` rows** in any
`svc_id` column.

---

# 5. Deployment Order

Connect to the target DB with the correct WeCMS schema on `search_path`,
then:

1. **WEB_SVC_ID DDL** — `STEP 1` (widen the 9 WEB_SVC_ID columns).
2. **WSVC_ID DDL** — `STEP 2` (widen `audit_logs_infm.wsvc_id`).
3. **WEB_SVC_ID data migration** — `STEP 3` (**PO-approved only**): run the
   `STEP 3a` collision pre-check (must return 0 rows), then the two UPDATEs.
4. **Verification** — `STEP 4` (length check for both families + memo data
   checks).

**Checklist:**

- [ ] **PO (Mr. Jung) sign-off** on: (i) altering legacy tables on the
      shared/live DB (STEP 1 + STEP 2), and (ii) the memo-scope switch + its
      coexistence impact (STEP 3, R1). Confirm the D1 memory is superseded.
- [ ] Confirm `MemoScope.CARD_TRANSACTIONS` value == the STEP 3 SQL literal.
- [ ] Length pre-flight (run `STEP 4a` before) — review current lengths.
- [ ] Take a backup / snapshot of the target tables (DBA standard).
- [ ] Run **STEP 1** and **STEP 2** (DDL widen). Confirm `NOTICE` lines.
- [ ] Run **STEP 4a** — every target column is now `varchar(100)`.
- [ ] (Only if PO-approved) Run **STEP 3a** (0 rows) → **STEP 3b/3c** → then
      **STEP 4b/4c**.
- [ ] Redeploy/verify wecms-api memo CRUD on Card Transactions still works
      end-to-end (create/list/edit/delete + colour).
- [ ] Record execution in the change log.

**Included columns (widen → varchar(100)) — 10 total:** 9 WEB_SVC_ID (§2.1)
+ 1 WSVC_ID (`audit_logs_infm.wsvc_id`, §3.1). The extended WSVC_ID/SVC_ID
sweep added **no new columns**.

**Excluded columns:**

| Column | Reason |
|---|---|
| `ext_tran_call_his.svc_id` | Different meaning — external GDT/integration service id (PK) |
| `api_tran_log_his.svc_id` | Different meaning — API-transaction-log service id (PK) |
| `jex_ad_trsc_log.svc_id` · `jex_ad_trsc_str_log.svc_id` | JEX framework tables (out of refactor scope) |
| `jex_ad_lgin_trsc_log.svc_id` | JEX framework table + already varchar(100) |
| `bltb_noti_infm.noti_wsvc` | Already varchar(100) — long enough |
| `menu_item.legacy_wsvc_id` | Already varchar(100); refactor-owned, intentionally holds legacy id |
| `sys_scr_option`, `sys_scr_option_new`, `user_scr_option`, `user_scr_option_new`, all `*_i<NNNNN>` / `*_<NNNNN>` | Legacy old-version / IMP backup snapshots — out of scope |

---

# 6. Final Conclusion

**Final Legacy Screen ID Sweep (data-driven, 2026-07-02)** — audit-only;
no SQL, entity, or business logic changed. This sweep searches by **stored
value**, not column name.

**Method.** The refactor DB has 2,437 text columns across 158 tables
(1,731 with length ≥ 13). Rather than a banned dynamic `DO` scan, every
column's sampled values (`most_common_vals` + `histogram_bounds`) were
regex-matched in one `pg_stats` metadata query against the legacy
screen-id pattern `[a-z]{3,}_[0-9]{4}_[0-9]{3}` and the route pattern
`/payment/%` / `card-transactions`, followed by exact-count confirmation on
every flagged column and a code grep (comments excluded). On this DB the
seeded tables are small enough that the stats sample effectively covers all
distinct values. **Caveat:** `pg_stats` is a sample; a legacy-looking value
hidden in a high-cardinality free-text column beyond the 100-value stat
target could be missed — combined with the exhaustive by-name `*_svc_id`
audit and the code grep, confidence is high but not a 100% row-by-row scan.

**Discovered locations:**

| Table | Column | Sample Value | Meaning | Current Length | Should migrate? | Reason |
|---|---|---|---|---|---|---|
| `memo_infm` | `web_svc_id` | `/payment/corporate-card/card-transactions` (1 row) | Modern route (memo scope) | varchar(50) | **YES** (STEP 1) | Web route id; in include set |
| `menu_item` | `route` | `/payment/...` (13 rows) | Next.js route column (refactor-owned) | varchar (refactor-owned) | NO | Purpose-built route column; not a `*_svc_id`, not in scope |
| `menu_item` | `legacy_wsvc_id` | legacy ids (26 rows) | **Approved** `LEGACY_WSVC_ID` (intentionally holds legacy ids) | varchar(100) | NO | Already 100; by design stores legacy ids |
| `ext_tran_call_his` | `web_svc_id` | `aosv_0001_001_r001` (22), `REAL_TIME_ONLINE_INQUIRY` (37) | Calling-screen id (WEB_SVC_ID) — refactor writes a legacy WSVC id here | varchar(50) | **YES** (STEP 1) | WEB_SVC_ID column; in include set |
| `ext_tran_call_his` | `svc_id` | `BI09`, `REAL_TIME_ONLINE_INQUIRY` | External integration service id | varchar(50) | NO | Different meaning (§4) — not a screen route |
| `audit_logs_infm` | `wsvc_id` | slugs (`card-transactions`, `firm-banking-settings`, …) | Audit screen slug | varchar(50)/(20) JEX | **YES** (STEP 2) | WSVC_ID; in include set. No legacy-pattern values |
| `sys_scr_option_wecms` · `user_scr_option_wecms` · `menu_auth_admn_sys` · `user_menu_auth_infm` | `web_svc_id` | slugs (`card-transactions`, `banking-account-inquiry-vnd`, …) | View Setting / menu-auth slug | varchar(50) | **YES** (STEP 1) | WEB_SVC_ID; in include set. **No** `_dddd_ddd` values (0 rows) |
| `menu_item_translation` · `product_menu` | `menu_item_id` | node slugs (`banking`, …) | Menu-node FK slug | varchar | NO | Node slug, not a screen/route id; no legacy ids |

**Rule verification — no legacy ids outside approved columns — PASS.**
Every stored legacy screen id (`*_dddd_ddd`) resides in an **approved**
column: `menu_item.legacy_wsvc_id` (`LEGACY_WSVC_ID`, 26 rows, by design)
and `ext_tran_call_his.web_svc_id` (`WEB_SVC_ID`, 22 rows). No legacy
screen id in any non-approved column. Routes appear only in
`memo_infm.web_svc_id` (approved) and `menu_item.route` (purpose-built).
`audit_logs_infm.wsvc_id`, View Setting and menu-auth `web_svc_id` contain
**slugs only** — zero `_dddd_ddd` values.

**Executable hardcoded legacy screen IDs (code).** Backend — 6 executable
occurrences (comments/Javadoc excluded); all **legacy-id constants used for
live-JEX coexistence**, each defined once:

| File:line | Constant / usage | Value(s) | Purpose |
|---|---|---|---|
| `MemoScope.java:40,45` | `TRANSACTION_HISTORY_VND` / `_FCY` | `fnsh_0002_001` / `fnsh_0006_001` | **Provisional** memo-scope placeholders (Phase 2, not wired) — inside the memo SoT enum |
| `LoginCredentialsService.java:112` | audit-history filter | `comm_0218_001`, `comm_0218_004` | Query legacy+refactor audit rows for login-credentials history |
| `SettingsBankAccountService.java:85` | `LEGACY_WSVC_ID` | `comm_0183_001` | Read legacy audit history for bank-account |
| `OperationStatusService.java:83` | `EXT_WEB_SVC_ID_R` | `aosv_0001_001_r001` | Written to `ext_tran_call_his.web_svc_id` (matches the DB finding) |
| `ApprovalNotificationService.java:79` | `NOTI_WSVC` | `comm_0193_001.act` | Written to `bltb_noti_infm.noti_wsvc` |

Frontend — **none.** Every `*_dddd_ddd` hit in `wecms-web` is a comment /
Javadoc / doc. No executable frontend code hardcodes a legacy screen id.

**Modern route identifier — single source of truth.** The memo route
(`/payment/corporate-card/card-transactions`) comes **only** from
`MemoScope.CARD_TRANSACTIONS.webSvcId()` — not duplicated in executable
code. View Setting slugs flow from `wecms-web/lib/viewSettingScreens.ts` +
the matching Flyway seed. Audit slugs are per-service `private static final`
constants, each defined once (no global registry, but no duplication).

**False positives.** `menu_item_translation.menu_item_id` /
`product_menu.menu_item_id` hold menu node slugs, not screen/route ids.
`ext_tran_call_his.svc_id` superficially resembles an id family but is an
external service id (§4).

**Excluded framework tables.** `jex_ad_lgin_trsc_log` · `jex_ad_trsc_log` ·
`jex_ad_trsc_str_log` (`svc_id` holds legacy action ids) — `jex_*`
framework tables, absent from the refactor DB, out of scope per CLAUDE.md
exclude-rule 1.

**Conclusion.** The refactor DB is **clean**: no legacy screen id or modern
route is stored in any column outside the approved `WEB_SVC_ID` / `WSVC_ID`
/ `LEGACY_WSVC_ID` set (plus the purpose-built `menu_item.route`). The
migration's **include set is complete and unchanged** (10 columns) — the
value-driven sweep surfaced no new column to widen and no misplaced
identifier. The only remaining hardcoded legacy ids are intentional
coexistence constants (each single-sourced), and the memo route is
single-sourced in `MemoScope`. This reorganization changed **no** migration
logic, SQL behaviour, included/excluded tables, risk analysis, coexistence
discussion, data mapping, or PostgreSQL compatibility — it only regrouped
the package by identifier type for readability.
