# HAENGSUNG Payment Refresh — Backend Feature Spec

**Slug:** `haengsung-payment-refresh` · **Domain:** Payment · **Status:** Design / pre-implementation (docs only — no Java written yet)
**Product type:** HAENGSUNG (`PRDT_TYPE = 'H'`) only · **Last updated:** 2026-06-28

> **Scope guard.** This is a design document. **No Java, no `build.gradle`, no `application*.yml`, no Flyway migration has been written.** Per the root CLAUDE.md "analyze before implement" rule, every mapping below is validated against the legacy JEX behaviour first.
>
> **2026-06-28 — Oracle documents received & analysed.** The HAENGSUNG Oracle interface table definition, the remittance status-flow workbook, and the SIT connection file (under `C:\WECMS PROJECT\Oracle DB\`) have been read. The analysis is in **[Appendix A (§13–§18)](#appendix-a--oracle-document-analysis-2026-06-28)** and is now the **source of truth** for the Oracle side. It **confirms** the legacy column names + write-back values and surfaces several **mismatches** ([§17](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor)) that must be resolved with the PO before coding. Open items [OQ-2/3/4/8/9](#10-open-items) are now **answered (pending PO confirmation)**; implementation remains gated on the [§17](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor) mismatches + PO sign-off.

---

## 1. Purpose

For HAENGSUNG product type only, four payment types replace **Excel Upload** with a **Refresh** button on their Scheduled-payment screen:

- Vendor Payment
- Expense Payment
- Payroll
- Overseas Payment

**Internal Transfer is excluded** — it keeps Excel Upload for every product type (no Refresh, no Oracle path).

HAENGSUNG does not upload Excel manually for these four. Their ERP writes payment batches into an external **Oracle** DB; WeCMS imports them on demand when the user clicks **Refresh**.

This feature is the backend half (`wecms-api`); the frontend half is tracked in the paired frontend doc — see [§12 Cross-references](#12-cross-references). The frontend toggle (Refresh vs Excel Upload, gated on `PRDT_TYPE = 'H'`) already exists; today its handler only re-fetches the list. The backend import endpoint described here does **not** yet exist.

---

## 2. Two-datasource architecture

The refactor runs against **two physically separate database servers**. This is the central new infrastructure this feature introduces.

| # | Server | Engine | Role | Tables |
|---|---|---|---|---|
| 1 | **WeCMS DB** (`172.168.5.172:5432/wecmsdevdb`) | PostgreSQL | **Primary** datasource. All existing JPA / Flyway / business logic. | `SCDUL_PYMN_SUMR`, `SCDUL_PYMN_SUMR_DETL`, `OVRS_PYMN_SUMR`, `OVRS_PYMN_SUMR_DETL`, `ERP_TRNS_HSTR` (+ every other refactor table) |
| 2 | **HAENGSUNG Oracle ERP** (private; SIT host in a local text file) | Oracle | **Secondary** datasource. **Read-only except** for the two status write-back columns sets. Used **only** by the HAENGSUNG Refresh import. | `TB_TRNS_MAST`, `TB_TRNS_DETAIL` |

### Rules

- **PostgreSQL stays primary.** The WeCMS Postgres `DataSource` keeps `@Primary`; Hibernate / JPA / Flyway / `ddl-auto: validate` all bind to it unchanged. Adding the Oracle datasource **must not** change the primary's autoconfiguration.
- **Oracle is used by one feature only.** No other domain reads or writes the Oracle datasource. The Oracle bean is wired into the Refresh import service alone.
- **Oracle access is narrow.** Read `TB_TRNS_MAST` / `TB_TRNS_DETAIL`; write back only the status columns in [§7](#7-oracle-status-write-back). No DDL, no other Oracle tables.
- **`TB_TRNS_MAST` / `TB_TRNS_DETAIL` are NOT in the WeCMS table inventory** ([legacy-table-inventory.md](../references/database/legacy-table-inventory.md)) and must never be added — they live on the external Oracle server, not in the WeCMS Postgres baseline. They get **no Flyway migration and no `@Entity` on the primary persistence unit.**
- **Driver / bean wiring is deferred to implementation** — this doc does not commit to JPA-vs-`JdbcTemplate` for the Oracle side. A lightweight `JdbcTemplate` (or a narrow secondary JPA persistence unit scoped to the two Oracle tables) is the likely shape; the decision is recorded as [OQ-7](#10-open-items).

```
                         ┌─────────────────────────────────────────────┐
   Refresh API request   │  wecms-api (Spring Boot)                     │
   (HAENGSUNG user)  ───▶ │                                             │
                         │   ErpPaymentRefreshService                  │
                         │      │                                       │
                         │      │ (1) READ batches                      │
                         │      ▼                                       │
                         │   Oracle datasource (SECONDARY) ────────────┼──▶  HAENGSUNG Oracle ERP
                         │      ▲           (5) status write-back       │      TB_TRNS_MAST / TB_TRNS_DETAIL
                         │      │                                       │
                         │      │ (3) insert/update + (4) audit log     │
                         │      ▼                                       │
                         │   Primary datasource (@Primary, JPA) ───────┼──▶  WeCMS PostgreSQL
                         │                                             │      SCDUL_*/OVRS_*/ERP_TRNS_HSTR
                         └─────────────────────────────────────────────┘
```

---

## 3. SIT config example

> **VERIFIED SIT REALITY (do not skip).** On the real SIT host (`wecms@172.168.5.172`, Tomcat at `/DATA/src-wecms/apache-tomcat-11.0.22`), the app loads config from **`bin/setenv.sh` OS environment variables**, **NOT** from `environment/.env.sit` or `environment/application-sit.yml`. Editing `.env.sit` on SIT does nothing — that folder is not on Spring's config path. The authoritative SIT config channel is `setenv.sh` exporting the `WECMS_*` / `HAENGSUNG_*` vars. See memory `reference_sit_server_config_setenv`. Locally, `.env.sit` / `application-local.yml` work as normal because Spring's relative `config.import` resolves against the dev CWD.

### 3.1 YAML shape (binds env vars — no secrets in the file)

```yaml
# application-sit.yml (committed) — values come from env, never inline
haengsung:
  oracle:
    enabled: ${HAENGSUNG_ORACLE_ENABLED:false}   # feature stays OFF until provisioned
    datasource:
      url:      ${HAENGSUNG_ORACLE_URL}           # e.g. jdbc:oracle:thin:@//host:1521/SERVICE
      username: ${HAENGSUNG_ORACLE_USERNAME}
      password: ${HAENGSUNG_ORACLE_PASSWORD}
      driver-class-name: oracle.jdbc.OracleDriver
    pool:
      maximum-pool-size: ${HAENGSUNG_ORACLE_POOL_MAX:5}
      # Keep-alive / dead-connection detection — see §8 and the ORA-17008 lesson
      validation-query: SELECT 1 FROM dual
      keep-alive-time-ms: ${HAENGSUNG_ORACLE_KEEPALIVE_MS:120000}
```

### 3.2 SIT host — authoritative channel (`setenv.sh`, OS env)

```sh
# /DATA/src-wecms/apache-tomcat-11.0.22/bin/setenv.sh   (NOT committed; lives on the SIT box)
export SPRING_PROFILES_ACTIVE=sit
export HAENGSUNG_ORACLE_ENABLED=true
export HAENGSUNG_ORACLE_URL='jdbc:oracle:thin:@//<sit-oracle-host>:1521/<service>'
export HAENGSUNG_ORACLE_USERNAME='<sit-user>'
export HAENGSUNG_ORACLE_PASSWORD='<sit-password>'   # single-quote if it contains $
```

### 3.3 Local dev — `.env.sit` / `application-local.yml`

```sh
# .env.sit  (GITIGNORED — copy from .env.sit.example, fill from the provided local text file)
HAENGSUNG_ORACLE_ENABLED=true
HAENGSUNG_ORACLE_URL=jdbc:oracle:thin:@//<host>:1521/<service>
HAENGSUNG_ORACLE_USERNAME=<user>
HAENGSUNG_ORACLE_PASSWORD=<password>
```

- The **SIT connection info lives in a local text file** handed over out-of-band. Copy the values into `.env.sit` locally (gitignored) or into `setenv.sh` on the SIT box — **never** into a committed YAML/properties file.
- A matching `.env.sit.example` (committed) should list the `HAENGSUNG_ORACLE_*` keys with **placeholder** values only, and carry the header note: *"On the real SIT host these come from setenv.sh / OS env, NOT this file."*

---

## 4. Security rule for DB credentials

1. **Never hardcode** Oracle (or Postgres) username/password/URL in Java, YAML, properties, or any committed file. Bind from env vars only (`${HAENGSUNG_ORACLE_*}`).
2. **Fail-fast** in the datasource `@ConfigurationProperties` constructor if a required value is missing while `haengsung.oracle.enabled=true` — same pattern as `WECMS_JWT_SECRET` / `WECMS_DB_PASSWORD`.
3. **Feature flag default OFF.** `haengsung.oracle.enabled` defaults to `false`. With the flag off, the Oracle datasource bean is not created and the Refresh endpoint returns a "not configured" business error — the app boots cleanly with no Oracle provisioning (mirrors the dispatch-flag-gated pattern).
4. **No credentials in logs.** Never log the resolved URL/username/password. Connection errors log the SQLState/error code only (e.g. `ORA-17008`), never the DSN.
5. **`.env.sit` and `setenv.sh` are gitignored / off-repo.** Only `.env.sit.example` (placeholders) is committed.
6. **Least privilege.** The SIT Oracle account should have `SELECT` on `TB_TRNS_MAST`/`TB_TRNS_DETAIL` and `UPDATE` only on the status columns in [§7](#7-oracle-status-write-back) — nothing wider. (Provisioning note for the DBA; not enforceable in app code.)

> ⚠️ **WARNING — DO NOT COMMIT DB CREDENTIALS.** Do not commit `.env.sit`, `setenv.sh`, any `application-*.yml` containing a real Oracle URL/username/password, or the local text file holding the SIT connection info. Confirm `.gitignore` covers `.env*` (except `.env.sit.example`) **before** the first credential is ever written to disk. If a credential is ever committed, treat it as compromised and rotate it. This repeats the F-04 committed-secret lesson from the security review — do not relearn it here.

---

## 5. Oracle read/write flow

Legacy source of truth: [`ErpDataRefreshHelper.java`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/ErpDataRefreshHelper.java) (shared by all 4 `pymn_000{1,3,5,7}_*_c002_act.jsp` actions + batch `ERP_0100_002`).

### 5.1 End-to-end flow

```
Refresh API (POST, HAENGSUNG user, payment_type + period_date)
  → resolve tenant from JWT (TenantContext.currentBizKey / currentPrdtType)
  → guard: PRDT_TYPE must be 'H'  (PrdtType.fromCode, never literal "H")  → else business error
  → guard: BUSI_INFM row for biz_key has prdt_type='H' AND del_yn='N'      → else 0 rows (no-op)
  → (audit) write an ERP_TRNS_HSTR "refresh attempt" row  (see §6.3 / OQ-6)
  → READ Oracle TB_TRNS_MAST  WHERE last_status='00' AND biz_no=<taxId>
        + period filter (file_date = / BETWEEN)
        + payment-type filter (see §5.2)
  → for each master row  (per-master unit of work — see §8):
        READ Oracle TB_TRNS_DETAIL WHERE file_date=? AND file_num=? AND end_gb='0'
        if no details → skip this master
        branch on FILE_GB:
           'AF1' → Overseas  → upsert OVRS_PYMN_SUMR + OVRS_PYMN_SUMR_DETL
           else  → Scheduled → upsert SCDUL_PYMN_SUMR + SCDUL_PYMN_SUMR_DETL
        if summary upserted AND ≥1 detail succeeded AND TB_TRNS_MAST.ERP_RCV_FLAG != 'Y':
           WRITE-BACK Oracle TB_TRNS_MAST  (status, see §7)
           WRITE-BACK Oracle TB_TRNS_DETAIL (status, per row, see §7)
  → return import result (counts; see §9)
```

### 5.2 Oracle read — `TB_TRNS_MAST` filter + payment-type routing

Base filter: `last_status = '00'` AND `biz_no = <company tax id>` + period (`file_date = <YYYYMMDD>` or `BETWEEN <start> AND <end>`).

| `payment_type` | Oracle condition |
|---|---|
| `OVERSEAS` | `file_gb = 'AF1'` |
| `PAYROLL` | `file_gb = 'AC2'` |
| `EXPENSE` | `file_nm LIKE 'E%' OR file_nm LIKE 'e%'` |
| `VENDOR` | `file_gb NOT IN ('AF1','AC2') AND file_nm NOT LIKE 'E%'/'e%'` |
| `ALL` | no payment-type narrowing (all of the above) |

`TB_TRNS_DETAIL` read filter: `file_date = ? AND file_num = ? AND end_gb = '0'`.

> Exact Oracle column names / types / status code semantics are **pending the Oracle table document** — see [§10 Open items](#10-open-items). The filters above are transcribed from the legacy helper and must be reconciled against the table doc before coding.

---

## 6. WeCMS target insert flow

The Refresh import inserts into the **same** WeCMS tables Excel Upload uses, so a HAENGSUNG batch is indistinguishable from an uploaded one downstream (approval, dispatch, result).

### 6.1 Branch by `FILE_GB`

| `FILE_GB` | Payment type (derived) | WeCMS target tables |
|---|---|---|
| `AF1` | Overseas | `OVRS_PYMN_SUMR` + `OVRS_PYMN_SUMR_DETL` |
| `AC2` | Payroll | `SCDUL_PYMN_SUMR` + `SCDUL_PYMN_SUMR_DETL` |
| `FILE_NM` starts `E`/`e` | Expense | `SCDUL_PYMN_SUMR` + `SCDUL_PYMN_SUMR_DETL` |
| else | Vendor | `SCDUL_PYMN_SUMR` + `SCDUL_PYMN_SUMR_DETL` |

### 6.2 Column mapping (Oracle → WeCMS)

**Scheduled summary** (`SCDUL_PYMN_SUMR`): `FILE_NUM→ERP_FILE_NUM`, `FILE_DATE→ERP_FILE_DATE`, `REGI_AMT→TOTAL_TRAN_AMT`, `REGI_CNT→TOT_TRNS_CNT`, `OUT_BANK_CD→OUT_BANK_CD`, `OUT_ACCT_NO→WDRL_ACT_NO`, `FILE_NM→TITL` (sanitized), type code derived (AC2→Payroll, `E*`→Expense, else Vendor).

**Scheduled detail** (`SCDUL_PYMN_SUMR_DETL`): `FILE_SEQ→ERP_FILE_SEQ`, `TR_ACCT_BANK_CD→TAR_BANK_CD` (spaces stripped), `TR_ACCT_NO→FINC_MDA_NO` (spaces stripped), `ACET_NM→RCPT_NM` (Vietnamese tone removed), `TR_AMT→TRAN_AMT`, `ISO_CODE→TRNF_CCY_CD`, `JEOKYO→TRAN_USAG/TAR_ACCT_RMRK/WDRL_ACCT_RMRK`, `ANT_DIV_CD→AntDivCd` (N/C/E/S/T/V map), `ATTRIBUTE2→ERP_ATTRIBUTE2`.

**Overseas summary** (`OVRS_PYMN_SUMR`): same shape as Scheduled summary (`ERP_FILE_NUM`/`ERP_FILE_DATE`/`TOTAL_TRAN_AMT`/`TOT_TRNS_CNT`/`OUT_BANK_CD`/`WDRL_ACT_NO`/`TITL`).

**Overseas detail** (`OVRS_PYMN_SUMR_DETL`): `FILE_SEQ→ERP_FILE_SEQ`, `TR_ACCT_BANK_CD→BNFC_BANK_BIC/BNFC_BANK_CD`, `TR_ACCT_NO→BNFC_ACCT_NO`, `ACET_NM→BNFC_NM` (tone removed), `TR_AMT→TRNF_AMT`, `ACET_ADDR→BNFC_ADDR`, `TR_ACCT_BANK_NM→BNFC_BANK_NAME`, `FORI_FEE_MAN→FEE_PAYER_CD` (blank/3→S, 1→B, 2→O), `JEOKYO→MEMO`, `ISO_CODE→TRNF_CCY_CD`, `ATTRIBUTE2→ERP_ATTRIBUTE2`.

### 6.3 Verified refactor target-side readiness (2026-06-28)

| Target prerequisite | State |
|---|---|
| Scheduled `ERP_FILE_*` columns | ✅ Present — `ScdulPymnSumr` / `ScdulPymnSumrDetl` entities map `erpFileNum`/`erpFileDate`/`erpFileSeq`/`erpAttribute2`. |
| **Overseas `ERP_FILE_*` columns** | ❌ **MISSING on entities** — `OvrsPymnSumr` / `OvrsPymnSumrDetl` have no `erpFile*` fields. Blocks the Overseas leg until added (verify DDL first). See [OQ-1](#10-open-items). |
| `ERP_TRNS_HSTR` table + writer | ✅ Present & reusable — `setting/erptransactionhistory` module; `ErpTrnsHistoryWriter.recordUploadedFile(...)` writes `ERP_TYPE='E'` with codes `"02"` Vendor / `"03"` Expense / `"12"` Payroll. **Caveat:** the writer no-ops when there is no readable source file, so a fileless "0 rows found" refresh attempt would not be logged — see [OQ-6](#10-open-items). |
| `wb_get_next_val` key funnel | ✅ Present — `LegacyFunctionRepository` is the single executor; `SCDUL_*`/`OVRS_*` key generation must route through it (`scdul_pymn_no`/`ovrs_pymn_no` follow the date-prefixed-key exception, OD-2). |
| Inventory baseline | ✅ `scdul_pymn_sumr(_detl)`, `ovrs_pymn_sumr(_detl)`, `erp_trns_hstr`, `busi_infm` all in baseline; Oracle `TB_TRNS_*` correctly absent. |

### 6.4 Duplicate prevention + approval lock (mandatory)

- **Dedup key** — summary: `(BIZ_KEY, ERP_FILE_NUM, ERP_FILE_DATE)`; detail: `+ ERP_FILE_SEQ`. An existing row is **updated**, not duplicated.
- **Approval lock** — if the existing summary already carries an `APPR_SEQ_NO`, skip the whole batch (summary + details) untouched. Never overwrite a batch already in/through approval.
- Imports land as **draft** — Refresh never sets `APPR_SEQ_NO`. Approval is a separate later step.

---

## 7. Oracle status write-back

After a master's WeCMS upsert succeeds (summary upserted **and** ≥1 detail succeeded **and** `ERP_RCV_FLAG != 'Y'`):

**`TB_TRNS_MAST`** → `ERP_RCV_FLAG='Y'`, `ERP_RCV_FLAG_USR='WEBCASH'`, `ERP_RCV_FLAG_DT=now(Asia/Ho_Chi_Minh)`, `IHB_UDT_USR='WEBCASH'`, `IHB_UDT_DT=now(VN)`.

**`TB_TRNS_DETAIL`** (first import) → `END_GB='2'`, `FLAG='Y'`, `IF_SND_ID='WEBCASH'`, `IF_SND_DT=now(VN)`, `FLAG_USR/FLAG_DT`, `IHB_UDT_USR/IHB_UDT_DT`. (Re-import → only `IHB_UDT_USR/IHB_UDT_DT`.)

These are the **only** writes the Oracle datasource performs. Exact column list / value domains pending the Oracle table document ([OQ-3](#10-open-items)).

---

## 8. Transaction rule

The hard constraint: **WeCMS Postgres and HAENGSUNG Oracle are two separate datasources with two separate transaction managers. There is no distributed (XA) transaction**, so cross-DB atomicity is not guaranteed. The design handles this with per-master ordering + idempotency, exactly as legacy does.

- **Unit of work = one `TB_TRNS_MAST` master.** Each master is processed independently; a failure on one master must not abort the others (legacy: per-master rollback, continue loop).
- **WeCMS writes are transactional** under the primary tx manager: summary + all details for a master commit together (`@Transactional`). On any exception mid-master, roll back that master's WeCMS writes and move on.
- **Oracle write-back happens only after** the WeCMS commit for that master succeeds, and only when `ERP_RCV_FLAG != 'Y'`.
- **No XA / two-phase commit.** If the WeCMS commit succeeds but the Oracle write-back then fails, the WeCMS batch stays imported and the Oracle row stays `last_status='00'` / `ERP_RCV_FLAG != 'Y'`. The **next Refresh re-reads that master**; the dedup keys + approval-lock ([§6.4](#64-duplicate-prevention--approval-lock-mandatory)) make the re-import idempotent (it updates the existing draft, or skips if it reached approval). This is the legacy safety model — **idempotency is the correctness guarantee, not atomicity.**
- **The audit row** (`ERP_TRNS_HSTR`) is written in its own `REQUIRES_NEW` transaction so an audit-write failure never rolls back the import (existing `ErpTrnsHistoryWriter` behaviour).
- **Decision to confirm:** whether the Oracle write-back runs inside its own short Oracle transaction per master, and the exact commit ordering relative to the WeCMS commit, is recorded as [OQ-7](#10-open-items).

---

## 9. API contract (proposed — pending confirmation)

**Recommended: one shared endpoint** (mirrors the single legacy `ErpDataRefreshHelper`):

```
POST /api/v1/payments/haengsung/refresh
Body: { "payment_type": "VENDOR" | "EXPENSE" | "PAYROLL" | "OVERSEAS" | "ALL",
        "period_date":  "YYYYMMDD"  |  "YYYYMMDD-YYYYMMDD" }
Response: ApiResponse<…>  → counts { masters_found, imported, skipped, failed }
```

- Slug-based URI per the refactor naming rule — never a legacy WSVC id.
- `@PreAuthorize("@menuAuthz.canAccess('…')")` + a product-type guard (`PrdtType.fromCode(...) == H`, read from JWT, never a request field).
- Wire = lowercase snake_case; envelope = `ApiResponse<T>`; errors carry `trace_id`.
- Endpoint shape (one shared vs four per-type) is [OQ-5](#10-open-items).

---

## 9A. Implementation boundary / phase plan

The legacy analysis ([Appendix B](#appendix-b--legacy-erp-batch-and-helper-analysis)) shows HAENGSUNG↔Oracle is **four independent flows**, not one. This feature ships **only the Refresh/import flow first**; everything else is an explicitly-deferred follow-on. The phases below are the contract for what is built when.

### Single-service design decision (non-negotiable)

**The refactor must NOT copy the two legacy import implementations separately.** Legacy has the same import twice — `ErpDataRefreshHelper` (Refresh button) and `ERP_0100_002` (batch) — with divergent strategies (upsert vs insert-only; see [§22](#22-refresh-button-vs-scheduled-batch--not-the-same-helper-️), M-8/M-9). The refactor consolidates both triggers onto **one** service:

```
                manual Refresh (Phase 1)  ─┐
                                            ├─▶  ErpPaymentRefreshService  ─▶ Oracle read + WeCMS upsert + receive write-back
   scheduled import batch (Phase 2, opt)  ─┘        (one upsert-based implementation — never two copies)
```

- **`ErpPaymentRefreshService`** is the single import implementation. The Phase 1 controller calls it directly; the Phase 2 scheduled job (if approved) calls the **same** service — it does **not** re-port `ERP_0100_002`'s inline logic.
- Use the **upsert + dedup + approval-lock** model (the `ErpDataRefreshHelper` strategy), not the batch's insert-only model — it is re-runnable and safe under coexistence ([§6.4](#64-duplicate-prevention--approval-lock-mandatory), M-9).

### Phase 1 — Manual Refresh import only (THIS feature)

**In scope:**
- Backend Refresh API — `POST /api/v1/payments/haengsung/refresh` ([§9](#9-api-contract-proposed--pending-confirmation)), product-type `H` guard.
- HAENGSUNG **Oracle datasource** (secondary, flag-gated OFF by default; Postgres stays `@Primary` — [§2](#2-two-datasource-architecture)).
- **Read** Oracle `TB_TRNS_MAST` (`last_status='00'`, biz_no, period, payment-type filter) and `TB_TRNS_DETAIL` (`end_gb='0'`).
- **Insert/update** WeCMS payment **draft** data — `SCDUL_PYMN_SUMR(_DETL)` (Vendor/Expense/Payroll) and `OVRS_PYMN_SUMR(_DETL)` (Overseas), via `ErpPaymentRefreshService` (upsert + dedup + approval-lock; never sets `APPR_SEQ_NO`).
- **Receive write-back to Oracle** ([§7](#7-oracle-status-write-back), [§15](#15-status-flow-confirmed-by-the-status-workbook)):
  - MAST `ERP_RCV_FLAG='Y'` (+ `ERP_RCV_FLAG_USR/DT`, `IHB_UDT_USR/DT`)
  - DETAIL `END_GB='2'`, `FLAG='Y'`, `IF_SND_ID/IF_SND_DT`, `FLAG_USR/FLAG_DT`, `IHB_UDT_USR/IHB_UDT_DT`
- `ERP_TRNS_HSTR` audit row per refresh ([§6.3](#63-verified-refactor-target-side-readiness-2026-06-28), subject to M-7).
- **Frontend** Refresh button calls the backend API (replacing today's list-only `refreshList` handler — [§5 frontend fix](#5-recommended-frontend-fix)).

**Out of scope (Phase 1):**
- Approval-status write-back (`ErpStatusUpdateHelper`) → Phase 3.
- Dispatch-result write-back (`ERP_0100_004`) → Phase 4.
- 5-minute scheduled batch auto-sync (`ERP_0100_002` cron) → Phase 2.
- Exchange-rate sync (`ERP_0100_001`) — out of feature entirely.
- Account-history sync (`ERP_0100_003`, `TB_ACCT_HISTORY`) — out of feature entirely.
- Card-approval sync (`ERP_0100_003`, `TB_CARD_APPROVAL`) — out of feature entirely.
- Writing MAST `LAST_STATUS` or DETAIL `PROC_STS` — **never written by the import** (Phases 3/4 own those — [§23](#23-field-change-matrix-which-file-writes-which-oracle-column)).

### Phase 2 — Optional scheduled import batch (only if PO approves)

- Port the **trigger** of `ERP_0100_002`, not its body.
- **Do NOT copy the legacy duplicate import logic.** Reuse the **same `ErpPaymentRefreshService`** the manual Refresh uses — one consolidated import service, invoked on a schedule (Spring `@Scheduled` / ShedLock) instead of by a user click.
- Cron cadence per Jex Studio / `TB_BATCH_SCHEDULE_*` (OQ-13; ~5-min legacy).

### Phase 3 — Approval-status write-back (only if PO approves)

- Port `ErpStatusUpdateHelper` as a clean Spring service.
- Write Oracle approval-lifecycle statuses: MAST `LAST_STATUS` PENDING→`10`, APPROVED→`20`, REJECTED→`32`, PROCESSING→`51`; on reject also DETAIL `PROC_STS='XXXX'`, `END_GB='3'`.
- Triggered from the refactor approval flow, not the import.

### Phase 4 — Dispatch-result write-back (only if PO approves)

- Port `ERP_0100_004`.
- After bank dispatch result: MAST `LAST_STATUS='52'`, `END_GB='1'` + counts; DETAIL `END_GB='8'`(ok)/`'5'`(fail), `PROC_STS`, `PROC_STS_MSG`, `TR_DATE`; mark WeCMS summary `ERP_PROC_CMPL_YN='Y'`.
- Depends on the firm-banking dispatch engine being active.

### Phase summary

| Phase | Flow | Legacy source | Status |
|---|---|---|---|
| **1** | Manual Refresh import + receive write-back | `ErpDataRefreshHelper` | ✅ **This feature** |
| **2** | Scheduled import (same service) | `ERP_0100_002` (trigger only) | ⏸ Optional — PO approval |
| **3** | Approval-status write-back | `ErpStatusUpdateHelper` | ⏸ Follow-on — PO approval |
| **4** | Dispatch-result write-back | `ERP_0100_004` | ⏸ Follow-on — PO approval |
| — | Exchange-rate / account-history / card sync | `ERP_0100_001`, `ERP_0100_003` | ❌ Out of feature |

### Phase 1 blockers vs follow-on features

**Phase 1 blockers (must clear before coding):**
- **M-1** — code against the physical `FILE_*` column block, not the proposed `TRNS_*` block ([§17](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor)).
- **M-2** — Vendor-vs-Expense split (`FILE_NM 'E%'` contradicted by sample data) — confirm with PO/ERP.
- **M-5** — Overseas `ERP_FILE_*` columns missing on `OvrsPymnSumr(_DETL)` entities — verify DDL + add.
- **Oracle datasource confirmation** — SID vs service for instance `DEV` (OQ-12) + connectivity from local/SIT.
- **PO sign-off** on the Phase 1 scope + the above mismatches.

**Follow-on features (NOT Phase 1 blockers):**
- `ErpStatusUpdateHelper` (Phase 3) · `ERP_0100_004` (Phase 4) · `ERP_0100_001` & `ERP_0100_003` (out of feature).
- M-10 (3 write-back surfaces) is a scoping note, resolved by this phase plan — receive write-back only in Phase 1.

---

## 10. Open items

Tracked here until the Oracle table document arrives and the PO confirms. (If/when the `wecms-api/docs/planning/open-questions/` tracker folder is created, mirror these there — it does not exist yet.)

### From the Oracle table document — ANSWERED 2026-06-28 (pending PO confirmation)

| ID | Status | Resolution |
|---|---|---|
| **OQ-2** | ✅ Answered | Physical columns = the `FILE_*` left-block set in the interface doc, confirmed by the Master-sample / Detail-sample sheets. Full schema in [§14](#14-confirmed-oracle-table-schemas-source-of-truth). |
| **OQ-3** | ✅ Answered | Status values + write-back confirmed by the status-flow workbook — see [§15](#15-status-flow-confirmed-by-the-status-workbook). DETAIL import write-back `END_GB 0→2`, `FLAG='Y'`, `IF_SND_*`, `IHB_UDT_*`; MAST `ERP_RCV_FLAG N→Y` (+`_USR`/`_DT`/`IHB_UDT_*`). |
| **OQ-4** | ⚠️ Answered w/ mismatch | Read filter (`last_status='00'`, `biz_no=<taxId>`, period) + `FILE_GB` routing (AF1=overseas, AC2=payroll, AB0=transfer) confirmed. **BUT** the Vendor-vs-Expense split by `FILE_NM LIKE 'E%'` is contradicted by the sample data — see [§17 M-2](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor). |
| **OQ-8** | ⚠️ Answered w/ mismatch | Mapping confirmed in [§16](#16-oracle--wecms-mapping-confirmed); `ANT_DIV_CD` has a legacy-only `T` value not in the doc — [§17 M-3](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor). |
| **OQ-9** | ✅ Answered | SIT connection in [§13.2](#132-sit-connection-redacted) (host/port/service/user; **password redacted**). |

### Design / PO decisions

| ID | Item |
|---|---|
| **OQ-1** | **Overseas ERP columns missing.** Confirm `OVRS_PYMN_SUMR`/`_DETL` carry `ERP_FILE_NUM/DATE/SEQ` (+ `ERP_ATTRIBUTE2`) in the WeCMS DDL; if not, a manual DBA DDL script + entity field additions are required (Flyway is `validate`-only — DDL is hand-run). Blocks the Overseas leg. |
| **OQ-5** | API surface: one shared `POST /payments/haengsung/refresh` (recommended) vs four per-type endpoints. |
| **OQ-6** | History-log parity: legacy writes an `ERP_TRNS_HSTR` row even for a fileless "0 rows" attempt; the reusable writer no-ops without a file. Accept dropping the no-file attempt log, or add a fileless `recordRefreshAttempt(...)` path? |
| **OQ-7** | Oracle bean shape (`JdbcTemplate` vs narrow secondary JPA unit) + the per-master Oracle write-back transaction/commit ordering relative to the WeCMS commit ([§8](#8-transaction-rule)). |
| **OQ-10** | Batch parity: legacy also auto-imports via `ERP_0100_002` (a **separate inline copy** of the import — NOT the Refresh helper; see [Appendix B](#appendix-b--legacy-erp-batch-and-helper-analysis)). Manual-Refresh only, or port the batch path too? |
| **OQ-11** | Oracle connection reliability: apply the `ORA-17008` keep-alive lessons (DCD / `oracle.net.keepAlive` / pool validation) up front — see memory `project_hs_prod_datasource_ora17008`. |

---

## 11. Legacy source-of-truth pointers

| Concern | Legacy file (read-only reference) |
|---|---|
| Shared refresh logic | [`WECMS DEV/WECMS/src/wabooks/helper/ErpDataRefreshHelper.java`](../../../WECMS%20DEV/WECMS/src/wabooks/helper/ErpDataRefreshHelper.java) |
| Vendor refresh action | `WECMS DEV/.../action/jex/wabooks/pymn/pymn_0001_002_c002_act.jsp` |
| Expense refresh action | `pymn_0003_002_c002_act.jsp` |
| Payroll refresh action | `pymn_0005_002_c002_act.jsp` |
| Overseas refresh action | `pymn_0007_003_c002_act.jsp` |
| Batch auto-sync | `WECMS DEV/WECMS/src/batch/jex/wabooks/erp/ERP_0100_002.java` |
| Oracle read/write IDOs | `IDO.TB_TRNS_MAST_R001.xml`, `IDO.TB_TRNS_DETAIL_R001.xml`, `IDO.TB_TRNS_MAST_U000.xml`, `IDO.TB_TRNS_DETAIL_U000.xml` |
| ERP datasource resolution + ORA-17008 | memory `project_hs_prod_datasource_ora17008` |
| SIT config channel (`setenv.sh`) | memory `reference_sit_server_config_setenv` |

---

## 12. Cross-references

- Reusable WeCMS-target services: [vendor-payment](payment/vendor-payment/), [expense-payment](payment/expense-payment/), [payroll](payment/payroll/), [overseas-payment](payment/overseas-payment/) feature docs (same `SCDUL_*`/`OVRS_*` insert path Refresh reuses).
- Table baseline authority: [references/database/legacy-table-inventory.md](../references/database/legacy-table-inventory.md).
- Frontend counterpart (paired, same slug): `wecms-web/docs/features/payment/haengsung-payment-refresh.md` *(to be created — frontend handler currently re-fetches the list only; must call this endpoint).*
- Legacy investigation summary that produced this spec: see the audit report in the originating session.

---

# Appendix A — Oracle Document Analysis (2026-06-28)

Source documents (read-only, under `C:\WECMS PROJECT\Oracle DB\`):
- `(VN) WeCMS 펌뱅킹 인터페이스 테이블 정의서_20250904_f1.xlsx` — interface table definition (14 sheets; r2, 2025-07-14).
- `송금테이블 상태 변화_20250904.xlsx` — remittance table status-flow (MAST + DETAIL state transitions).
- `SIT oracle db.txt` — SIT Oracle connection (password handled per [§4](#4-security-rule-for-db-credentials), never reproduced here).

**These documents are the source of truth for the Oracle side**, ranking above both the legacy JEX code and the current refactor. Where they disagree with legacy, [§17](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor) records it.

## 13. Oracle architecture

### 13.1 Server / database / schema

| Item | Value |
|---|---|
| Host | `172.168.5.23` |
| Port | `1521` |
| Global database name / service | `DEV` |
| User / schema | `wecms` |
| Password | **redacted — in `SIT oracle db.txt` only; never in repo / code / log / this doc** |
| JDBC URL (service form, recommended) | `jdbc:oracle:thin:@//172.168.5.23:1521/DEV` |
| JDBC URL (SID form, fallback) | `jdbc:oracle:thin:@172.168.5.23:1521:DEV` — "Global database name" reads as a service, so service form is preferred; **confirm SID vs service with the DBA** ([OQ-12](#10-open-items)). |

This is a **SIT/local-only** DB (instance name `DEV`). Local R&D + SIT use only — not a production link.

### 13.2 Interface tables (6 total in the doc; only 2 are in scope)

| # | KO name | EN table | Scope for this feature |
|---|---|---|---|
| 1 | 자금이체MAST | **`TB_TRNS_MAST`** | ✅ **IN** — transfer-batch master |
| 2 | 자금이체상세내역 | **`TB_TRNS_DETAIL`** | ✅ **IN** — transfer-batch detail (VND + FCY unified, r2) |
| 3 | 계좌거래내역 | `TB_ACCT_HISTORY` | ❌ out — account-history sync (separate feature) |
| 4 | 환율조회 | `TB_FCY_EXRATE` | ❌ out — exchange-rate sync |
| 5 | 법인카드MAST | `TB_CARD_MAST` | ❌ out — corporate-card sync |
| 6 | 법인카드승인내역 | `TB_CARD_APPROVAL` | ❌ out — card-approval sync |

Only `TB_TRNS_MAST` + `TB_TRNS_DETAIL` are read/written by the payment Refresh. The other four are unrelated Oracle→WeCMS sync surfaces and stay out of scope.

### 13.3 Relationship / keys

- **`TB_TRNS_MAST`** PK: `(FILE_DATE, FILE_NUM)` — `FILE_NUM` = ERP-assigned 8-digit batch sequence per day.
- **`TB_TRNS_DETAIL`** PK: `(FILE_DATE, FILE_NUM, FILE_SEQ)`.
- **MAST 1 ─→ N DETAIL** on `(FILE_DATE, FILE_NUM)`.
- One MAST row = one transfer batch (one WeCMS `SCDUL_PYMN_SUMR` / `OVRS_PYMN_SUMR` summary). One DETAIL row = one transfer line (one `*_DETL` row).

## 14. Confirmed Oracle table schemas (source of truth)

> ⚠️ **The interface sheets carry TWO column blocks side-by-side.** The **left block** (`FILE_*` / `OREG_*` / `TR_*` …) is the **physical Oracle table** — proven by the `Master-sample` / `Detail-sample` sheets, which show real rows under those exact headers, and it matches the legacy `ErpDataRefreshHelper`. The **right block** (`TRNS_DT`, `TRNS_SEQNO`, `TRNS_GB`, `CMS_GET_ST`, `ERP_GET_ST`, `IN_BANK_CD`, `BANK_CHNL_DIV`, `REMIT_CCY_CD` …) is a **proposed standard-CMS interface layout, NOT the SIT physical table.** **Code against the left block only.** See [§17 M-1](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor).

### 14.1 `TB_TRNS_MAST` (physical — read + status write-back)

| Col | Type | Meaning | Notes / values |
|---|---|---|---|
| `FILE_DATE` | VARCHAR2(8) | batch date (YYYYMMDD) | **PK**; ERP-set |
| `FILE_NUM` | VARCHAR2(8) | batch sequence | **PK**; ERP-set |
| `FILE_GB` | VARCHAR2(3) | business/transfer type | `AF1`=overseas, `AC2`=payroll, `AB0`=general transfer |
| `FILE_NM` | VARCHAR2(32) | file/batch name | doc rule: `V%`=vendor, `E%`=expense — **contradicted by samples**, see M-2 |
| `BIZ_NO` | VARCHAR2(13) | withdrawing company tax id | read filter `biz_no = <taxId>` |
| `OUT_BANK_CD` | VARCHAR2(3*) | withdrawal bank | (*sample shows longer values e.g. `11704088` / `79616001`) |
| `OUT_ACCT_NO` | VARCHAR2(16) | withdrawal account | |
| `REGI_CNT` | NUMBER | total count | → `TOT_TRNS_CNT` |
| `REGI_AMT` | NUMBER | total amount | → `TOTAL_TRAN_AMT` |
| `LAST_STATUS` | VARCHAR2(3) | lifecycle status | default `00`; `10`/`20`/`31`/`32`/`51`/`52`/`91`/`92` — see [§15](#15-status-flow-confirmed-by-the-status-workbook). **Read filter = `'00'`.** |
| `ERP_RCV_FLAG` | VARCHAR2(2) | ERP-transfer flag | `N` before, `S` prep, **`Y` imported**, `C` done. **Write-back sets `Y`.** |
| `ERP_RCV_FLAG_USR` / `_DT` | VARCHAR2(40)/(14) | first-link user/time | write-back `WEBCASH` / now(VN) |
| `IHB_UDT_USR` / `_DT` | VARCHAR2(40)/(14) | last-update user/time | write-back `WEBCASH` / now(VN) |
| `END_GB` | VARCHAR2(3) | completion | `0` incomplete / `1` complete — **set by dispatch lifecycle, NOT by import** |
| `TRAN_SET_YN`/`_DATE`/`_TIME`, `REGI_TIME`, `END_DATETIME`, `NOR_*`, `BUL_*`, `ERR_*`, `ORG_ID`, `INVOICE_ID`, `TRANSFER_YN`, `CREATED_BY`, `CREATION_DATE`, `LAST_UPDATED_BY`, `LAST_UPDATE_DATE`, `LAST_UPDATE_LOGIN`, `ERP_MAPPING_KEY1/2` | various | reservation, result counts, ERP keys | not consumed by the refresh import (read-as-needed only) |

### 14.2 `TB_TRNS_DETAIL` (physical — read + status write-back; 98 columns, key subset)

| Col | Type | Meaning | Notes / values |
|---|---|---|---|
| `FILE_DATE`, `FILE_NUM`, `FILE_SEQ` | VARCHAR2(8)/(8)/NUMBER | **PK** (links to MAST + line seq) | |
| `END_GB` | VARCHAR2(1) | line lifecycle | `0` pre-request→ERP, **`2` taken/processing (import sets this)**, `1` requested, `3` req-cancel, `5` req-error, `6` await, `7` cancel-error, `8` complete, `9` cancel-complete. **Read filter = `'0'`.** |
| `TR_AMT` | NUMBER(12,3) | transfer amount | → `TRAN_AMT` / `TRNF_AMT` |
| `ISO_CODE` | VARCHAR2(3) | currency | → `TRNF_CCY_CD` |
| `SEND_GBN` | VARCHAR2(1) | send type | `T` in-house / `O` other-bank / `S` overseas |
| `ANT_DIV_CD` | VARCHAR2(1) | interbank network | `N` NAPAS, `C` CITAD, `E` Shinhan EBANK FCY, `V` Woori VCB FCY, `S` SWIFT — **no `T`**, see M-3 |
| `ACET_NM` | VARCHAR2(200) | payee name | → `RCPT_NM` / `BNFC_NM` (VN tone stripped) |
| `TR_ACCT_NO` | VARCHAR2(35) | payee account | → `FINC_MDA_NO` / `BNFC_ACCT_NO` (spaces stripped) |
| `TR_ACCT_BANK_CD` | VARCHAR2(11) | payee bank / BIC | → `TAR_BANK_CD` / `BNFC_BANK_BIC`+`BNFC_BANK_CD` |
| `TR_ACCT_BANK_NM` | VARCHAR2(60) | payee bank name | → `BNFC_BANK_NAME` (overseas) |
| `ACET_ADDR` | VARCHAR2(105) | payee address | → `BNFC_ADDR` (overseas) |
| `FORI_FEE_MAN` | VARCHAR2(1) | fee bearer | `1` payee, `2` sender → `FEE_PAYER_CD` (blank/3→S, 1→B, 2→O) |
| `JEOKYO` | VARCHAR2(14) | memo | → `TRAN_USAG`/`TAR_ACCT_RMRK`/`WDRL_ACCT_RMRK` (sched) or `MEMO` (overseas) |
| `ATTRIBUTE2` | VARCHAR2(150) | ERP attribute | → `ERP_ATTRIBUTE2` |
| `FLAG` | VARCHAR2(3) | other-system link state | def says S048 `PS`/`EP`; **sample + legacy use `Y`** — see M-4. Write-back sets `Y`. |
| `IF_SND_ID` / `IF_SND_DT` | VARCHAR2(50)/(14) | ERP-send id/time | write-back `WEBCASH` / now(VN) on first import |
| `FLAG_USR` / `FLAG_DT` | VARCHAR2(40)/(14) | first-link user/time | write-back `WEBCASH` / now(VN) |
| `IHB_UDT_USR` / `_DT` | VARCHAR2(150)/(14) | last-update user/time | write-back `WEBCASH` / now(VN) |
| `PROC_STS` / `PROC_STS_MSG` | VARCHAR2(4)/(50) | transfer result code/msg | set by **dispatch**, default `0000` — NOT touched by import |
| `REMI_NM`, `TR_NAT_CODE`, `PAY_REASON_CODE`, `PAY_REASON`, `HS_CODE`, `INCOME_NUM`, `FEE_ACCT_NO`, `TR_AMT_OUT`, `TR_AMT_RATE`, `FEE*`, `FOREIGN_FEE*`, `ATTRIBUTE1/3..10`, `ORG_ID`, `INVOICE_ID` … | various | overseas remittance detail + result/fee fields | mapped as the legacy helper needs; full list in the doc |

## 15. Status flow (confirmed by the status workbook)

The status workbook validates the legacy write-back **exactly**. Refresh import touches only the transitions marked **★**.

### 15.1 `TB_TRNS_MAST`

```
ERP inserts ──▶ LAST_STATUS=00, ERP_RCV_FLAG=N, END_GB=0
                         │
   ★ Refresh import (firmbanking takes data):  ERP_RCV_FLAG N→Y, +ERP_RCV_FLAG_USR/DT=WEBCASH/now, +IHB_UDT_*
                         │   (LAST_STATUS stays 00, END_GB stays 0 — import does NOT change these)
                         ▼
   dispatch lifecycle (NOT this feature):
        success → LAST_STATUS=52, END_GB=1
        file delete → 92, END_GB=1
        registrant cancel → 31, END_GB=1
        confirmer cancel → 32, END_GB=1
```

### 15.2 `TB_TRNS_DETAIL` (`END_GB`)

```
0  신청전 (ERP saved, before request)            ◀── read filter selects END_GB='0'
│
├─ ★ Refresh import (firmbanking takes data):  END_GB 0→2, FLAG='Y',
│        IF_SND_ID/IF_SND_DT, FLAG_USR/FLAG_DT, IHB_UDT_USR/IHB_UDT_DT = WEBCASH/now(VN)
│        (re-import → only IHB_UDT_USR/IHB_UDT_DT)
▼
2  송금의뢰처리중 (taken / request-processing)
│
└─ dispatch lifecycle (NOT this feature):
       1 송금의뢰 → 6 응답대기 → 8 송금완료
                              → 3/9 cancel,  5/7 error
```

**Who sets what:** ERP sets the initial `00`/`0` rows. **The Refresh import (this feature) performs only the ★ transitions.** All non-★ transitions (`52`/`31`/`32`/`92` on MAST; `1`/`3`/`5`/`6`/`7`/`8`/`9` on DETAIL, plus `PROC_STS`) are owned by the downstream firm-banking **dispatch** engine, not by the import.

**Error / retry behaviour:** there is **no Oracle "import-error" status**. If the WeCMS insert or the Oracle write-back fails, the row's Oracle status is left untouched (`LAST_STATUS='00'` / `END_GB='0'`), so the **next Refresh re-reads it**. Retry = the user clicks Refresh again. Idempotency comes from two mechanisms working together: (1) the DETAIL read filter `END_GB='0'` naturally excludes already-imported lines (now `2`); (2) WeCMS-side dedup on `(BIZ_KEY, ERP_FILE_NUM, ERP_FILE_DATE[, ERP_FILE_SEQ])` + the approval-lock. This matches the legacy model — **idempotency, not atomicity, is the guarantee** ([§8](#8-transaction-rule)).

## 16. Oracle → WeCMS mapping (confirmed)

| Oracle table | Oracle column | WeCMS table | WeCMS column | Description |
|---|---|---|---|---|
| TB_TRNS_MAST | FILE_NUM | SCDUL_PYMN_SUMR / OVRS_PYMN_SUMR | ERP_FILE_NUM | batch seq (dedup key) |
| TB_TRNS_MAST | FILE_DATE | SCDUL/OVRS_PYMN_SUMR | ERP_FILE_DATE | batch date (dedup key) |
| TB_TRNS_MAST | REGI_AMT | SCDUL/OVRS_PYMN_SUMR | TOTAL_TRAN_AMT | total amount |
| TB_TRNS_MAST | REGI_CNT | SCDUL/OVRS_PYMN_SUMR | TOT_TRNS_CNT | total count |
| TB_TRNS_MAST | OUT_BANK_CD | SCDUL/OVRS_PYMN_SUMR | OUT_BANK_CD | withdrawal bank |
| TB_TRNS_MAST | OUT_ACCT_NO | SCDUL/OVRS_PYMN_SUMR | WDRL_ACT_NO | withdrawal account |
| TB_TRNS_MAST | FILE_NM | SCDUL/OVRS_PYMN_SUMR | TITL | batch title (sanitized) |
| TB_TRNS_MAST | FILE_GB (+FILE_NM) | SCDUL_PYMN_SUMR | SCDUL_PYMN_TYPE_CD | type: AC2→Payroll, E*→Expense, else Vendor (⚠ M-2) |
| TB_TRNS_DETAIL | FILE_SEQ | *_SUMR_DETL | ERP_FILE_SEQ | line seq (dedup key, numeric) |
| TB_TRNS_DETAIL | TR_AMT | SCDUL_*_DETL / OVRS_*_DETL | TRAN_AMT / TRNF_AMT | line amount |
| TB_TRNS_DETAIL | ISO_CODE | *_DETL | TRNF_CCY_CD | currency |
| TB_TRNS_DETAIL | TR_ACCT_BANK_CD | SCDUL_*_DETL | TAR_BANK_CD | payee bank (spaces stripped) |
| TB_TRNS_DETAIL | TR_ACCT_NO | SCDUL_*_DETL | FINC_MDA_NO | payee account (spaces stripped) |
| TB_TRNS_DETAIL | ACET_NM | SCDUL_*_DETL | RCPT_NM | payee name (VN tone stripped) |
| TB_TRNS_DETAIL | ANT_DIV_CD | SCDUL_*_DETL | ANT_DIV_CD | N/C/E/S/V map (⚠ M-3) |
| TB_TRNS_DETAIL | JEOKYO | SCDUL_*_DETL | TRAN_USAG / TAR_ACCT_RMRK / WDRL_ACCT_RMRK | memo |
| TB_TRNS_DETAIL | TR_ACCT_BANK_CD | OVRS_*_DETL | BNFC_BANK_BIC / BNFC_BANK_CD | beneficiary bank/BIC |
| TB_TRNS_DETAIL | TR_ACCT_NO | OVRS_*_DETL | BNFC_ACCT_NO | beneficiary account |
| TB_TRNS_DETAIL | ACET_NM | OVRS_*_DETL | BNFC_NM | beneficiary name |
| TB_TRNS_DETAIL | ACET_ADDR | OVRS_*_DETL | BNFC_ADDR | beneficiary address |
| TB_TRNS_DETAIL | TR_ACCT_BANK_NM | OVRS_*_DETL | BNFC_BANK_NAME | beneficiary bank name |
| TB_TRNS_DETAIL | FORI_FEE_MAN | OVRS_*_DETL | FEE_PAYER_CD | blank/3→S, 1→B, 2→O |
| TB_TRNS_DETAIL | JEOKYO | OVRS_*_DETL | MEMO | memo |
| TB_TRNS_DETAIL | ATTRIBUTE2 | *_DETL | ERP_ATTRIBUTE2 | ERP attribute passthrough |

**Write-back (WeCMS → Oracle)** — see [§7](#7-oracle-status-write-back) + [§15](#15-status-flow-confirmed-by-the-status-workbook); confirmed unchanged from legacy.

## 17. Mismatch report (source-of-truth vs legacy vs refactor)

> Per the task rule "report any mismatch before implementing." Each needs a decision before coding.

| ID | Severity | Mismatch | Source-of-truth says | Legacy / refactor | Action |
|---|---|---|---|---|---|
| **M-1** | 🔴 High | **Two column blocks in the MAST/DETAIL sheets.** | Left block (`FILE_*`) is the **physical** table (proven by sample sheets). Right block (`TRNS_*`/`CMS_GET_ST`/`IN_BANK_CD`…) is a **proposed standard layout, not the SIT table.** | Legacy reads the left block. | **Code against the left (`FILE_*`) block.** Ignore the right block. Confirm with PO that the right block is not the deployment target. |
| **M-2** | 🔴 High | **Vendor-vs-Expense split.** | Doc `FILE_NM` rule = `V%` vendor / `E%` expense, **but every `AB0` sample `FILE_NM` is `<date>-<filenum>`** (e.g. `20250903-00003162`) — none start with `V`/`E`. | Legacy classifies Expense by `FILE_NM LIKE 'E%'`, else Vendor. | **Blocking.** Under the sample data, legacy would tag **all** `AB0` batches as Vendor. Confirm with PO/ERP how HAENGSUNG actually distinguishes Vendor vs Expense (maybe Expense isn't used, or uses a different marker). |
| **M-3** | 🟠 Med | **`ANT_DIV_CD` value set.** | Doc lists `N/C/E/V/S` (no `T`). | Legacy `AntDivCd` map includes `T→INTER`. | Drop/keep `T`? Confirm `T` is dead in HAENGSUNG data; otherwise keep the legacy branch defensively. |
| **M-4** | 🟡 Low | **`FLAG` semantics.** | Column def = S048 `PS`/`EP`; **sample data + status workbook show `Y`.** | Legacy + this doc write `FLAG='Y'`. | Keep `FLAG='Y'` (matches the sample + legacy). Note the def discrepancy; confirm no consumer needs `PS`/`EP`. |
| **M-5** | 🟠 Med | **Overseas `ERP_FILE_*` columns missing on refactor entities.** | Overseas batches (`FILE_GB='AF1'`) carry `FILE_NUM/DATE/SEQ` and must dedup on them. | `OvrsPymnSumr`/`OvrsPymnSumrDetl` entities have **no** `erpFile*` fields (verified [§6.3](#63-verified-refactor-target-side-readiness-2026-06-28)). | Verify `OVRS_PYMN_SUMR(_DETL)` DDL has the columns; if missing → manual DBA DDL + entity additions. Blocks the Overseas leg. |
| **M-6** | 🟡 Low | **`OUT_BANK_CD` length.** | MAST def says `VARCHAR2(3)` but samples are 8-digit (`11704088`, `79616001`). | Legacy reads as string. | Treat as string; do not assume length 3. Confirm true Oracle column length with DBA. |
| **M-7** | 🟡 Low | **History-log fileless attempt** (carried from OQ-6). | Legacy writes an `ERP_TRNS_HSTR` row even for a 0-row attempt. | `ErpTrnsHistoryWriter` no-ops without a file. | Decide: accept dropping the no-file log, or add `recordRefreshAttempt(...)`. |

## 18. Development impact

### 18.1 Reuse (already in the refactor)
- **WeCMS insert path** — the existing Vendor/Expense/Payroll services write `SCDUL_PYMN_SUMR(_DETL)`; Overseas writes `OVRS_PYMN_SUMR(_DETL)`. The import reuses these target tables (and ideally the same persistence helpers) so a refreshed batch is identical to an uploaded one.
- **Scheduled entities** — `ScdulPymnSumr`/`ScdulPymnSumrDetl` already map `ERP_FILE_*` ✅.
- **`ErpTrnsHistoryWriter`** — reuse for the audit-history row (`ERP_TYPE='E'`, codes `02`/`03`/`12` + overseas) — subject to M-7.
- **`LegacyFunctionRepository` / `wb_get_next_val`** — reuse for `scdul_pymn_no`/`ovrs_pymn_no` key generation (date-prefixed-key exception OD-2).
- **`PrdtType` / `TenantContext`** — reuse for the `H` product-type guard + tenant/biz-key resolution.

### 18.2 New code required
- **Second (Oracle) datasource** — Oracle JDBC driver in `build.gradle`; secondary `DataSource` bean (`@ConfigurationProperties("haengsung.oracle")`), feature-flag-gated OFF by default; primary Postgres keeps `@Primary` ([§2](#2-two-datasource-architecture)).
- **Oracle access layer** — a narrow `JdbcTemplate` (or scoped secondary JPA unit, OQ-7) for `TB_TRNS_MAST`/`TB_TRNS_DETAIL` read + the two status write-backs. No `@Entity` on the primary unit, no Flyway for these tables.
- **`ErpPaymentRefreshService`** — orchestrates the [§5.1](#51-end-to-end-flow) flow (product-type guard → Oracle read → per-master WeCMS upsert → Oracle write-back → audit), porting the legacy `ErpDataRefreshHelper` logic as clean Spring code.
- **Controller** — `POST /api/v1/payments/haengsung/refresh` ([§9](#9-api-contract-proposed--pending-confirmation)).
- **Overseas entity columns** — add `erpFile*` to `OvrsPymnSumr`/`OvrsPymnSumrDetl` (+ DBA DDL if absent) — M-5.

### 18.3 Transaction boundaries / error / rollback / write-back
- Per-master unit of work; WeCMS writes `@Transactional` (primary tx mgr); **no XA** across the two DBs ([§8](#8-transaction-rule)).
- On WeCMS-write failure: roll back that master's WeCMS writes, skip its Oracle write-back, continue to the next master.
- Oracle write-back only after the WeCMS commit for that master succeeds and only when `ERP_RCV_FLAG != 'Y'`.
- Audit row in its own `REQUIRES_NEW` tx.
- Retry = re-run Refresh; idempotency via DETAIL `END_GB='0'` filter + WeCMS dedup + approval-lock ([§15](#15-status-flow-confirmed-by-the-status-workbook)).

### 18.4 Updated open items (design / PO)
OQ-1/5/6/7/10/11 in [§10](#10-open-items) still stand. New from this analysis:

| ID | Item |
|---|---|
| **OQ-12** | Confirm Oracle **SID vs service name** for instance `DEV` (JDBC URL form) with the DBA. |
| **M-1…M-7** | Resolve the [§17](#17-mismatch-report--source-of-truth-vs-legacy-vs-refactor) mismatches with the PO before coding — M-1 and M-2 are blocking. |

---

# Appendix B — Legacy ERP Batch and Helper Analysis

Source-level read 2026-06-28 of all four `batch/jex/wabooks/erp/ERP_0100_00*.java` batches + both helpers (`wabooks/helper/ErpDataRefreshHelper.java`, `ErpStatusUpdateHelper.java`). **This appendix corrects the earlier assumption that the Refresh button and the scheduled batch share a helper — they do not.**

## 19. The four ERP_0100 batches at a glance

| Batch | Direction | Purpose | Oracle tables | WeCMS tables | In scope (1st impl)? |
|---|---|---|---|---|---|
| **ERP_0100_001** | WeCMS → ERP | Push **exchange rates** to ERP | **writes** `TB_FCY_EXRATE` | reads `BANK_EXCH_RATE_INFM` | ❌ No — exchange-rate sync, unrelated to payment |
| **ERP_0100_002** | **ERP → WeCMS** | **Import transfer batches** (the batch twin of the Refresh button) | **reads** `TB_TRNS_MAST`/`TB_TRNS_DETAIL`; **writes back** receive-status | **writes** `SCDUL_PYMN_SUMR(_DETL)`, `OVRS_PYMN_SUMR(_DETL)`, `ERP_TRNS_HSTR` | ✅ **Yes** — same job the Refresh feature ports |
| **ERP_0100_003** | WeCMS → ERP | Push **account history + card approvals** to ERP | **writes** `TB_ACCT_HISTORY`, `TB_CARD_APPROVAL` | reads `ACCT_TRNS_HSTR`, `CARD_MSTR`/`CARD_TRNS_HSTR` | ❌ No — account/card sync, separate feature |
| **ERP_0100_004** | WeCMS → ERP | Write **dispatch RESULT status** back to ERP after firm-banking executes | **writes back** `TB_TRNS_MAST`/`TB_TRNS_DETAIL` final status | reads `SCDUL_PYMN_SUMR(_DETL)`/`OVRS_PYMN_SUMR(_DETL)` joined to `TB_FIRM_TRAN`/`TB_PAY_FILE_MST`/`TB_PAY_TARGET` | ❌ No — result write-back; **later feature** (needs dispatch) |

**Schedule/frequency:** none is hard-coded in the `.java` (each is an `AbstractBatchTask` with a `@JexDataInfo(type=BAT)` id). The cron is configured in the **Jex Studio batch scheduler / `TB_BATCH_SCHEDULE_INFO`+`_DTL`** (both in the PROD active-table list), not in source. Memory `project_hs_prod_datasource_ora17008` indicates **`ERP_0100_002` ≈ 5-minute auto-sync** in the HGSUNG batch container. Confirm exact cadence in Jex Studio (OQ-13).

All four select tenants via `BUSI_INFM WHERE prdt_type='H' AND del_yn='N'` and use `JexConnectionManager.createBCSIDOConnection()` (BCS/batch connection), per-business + per-record transactions, and always write an `ERP_TRNS_HSTR` audit row per section (even on 0 records — including a fileless `_NO_DATA` row, confirming the M-7 / OQ-6 fileless-attempt behaviour).

## 20. Which batch imports, which writes status back

- **Import (Oracle → WeCMS):** **`ERP_0100_002`** only. Reads `TB_TRNS_MAST` `WHERE last_status='00' AND erp_rcv_flag='N' AND biz_no=<taxId>`, `TB_TRNS_DETAIL WHERE file_date/file_num AND end_gb='0'`; inserts `SCDUL_*`/`OVRS_*`; then write-back.
- **Status write-back to Oracle happens in THREE different places** — not one:
  1. **Receive flag** — `ERP_0100_002` (batch) **and** `ErpDataRefreshHelper` (Refresh button): `TB_TRNS_MAST.ERP_RCV_FLAG='Y'` + `TB_TRNS_DETAIL.END_GB='2', FLAG='Y'`.
  2. **Approval status** — `ErpStatusUpdateHelper` (invoked from the approval flow, not a batch): `TB_TRNS_MAST.LAST_STATUS` = `10`/`20`/`32`/`51`; on reject also `TB_TRNS_DETAIL.PROC_STS='XXXX', END_GB='3'`.
  3. **Dispatch result** — `ERP_0100_004` (batch): `TB_TRNS_MAST.LAST_STATUS='52', END_GB='1'` + counts; `TB_TRNS_DETAIL.END_GB='8'/'5', PROC_STS, PROC_STS_MSG, TR_DATE`.

## 21. ErpDataRefreshHelper vs ErpStatusUpdateHelper — separate flows

They are **separate code paths**, connected only by the shared Oracle tables + `ERP_FILE_*` keys:

| | `ErpDataRefreshHelper` | `ErpStatusUpdateHelper` |
|---|---|---|
| Trigger | Refresh button (`pymn_000X_002_c002`) | Approval lifecycle (pending/approve/reject/processing) |
| Direction | ERP → WeCMS (import) + receive write-back | WeCMS → ERP (approval-status write-back) |
| Oracle write | `ERP_RCV_FLAG='Y'`, DETAIL `END_GB='2'`/`FLAG='Y'` | `LAST_STATUS` 10/20/32/51; reject → DETAIL `PROC_STS='XXXX'`/`END_GB='3'` |
| WeCMS write | inserts/upserts `SCDUL_*`/`OVRS_*` | none (reads `SCDUL_PYMN_SUMR_R017`/`OVRS_PYMN_SUMR_R011` for ERP_FILE_* keys only) |
| Status→ERP map | n/a | PENDING→10, APPROVED→20, REJECTED→32, PROCESSING→51 |

## 22. Refresh button vs scheduled batch — NOT the same helper ⚠️

**The Refresh button and `ERP_0100_002` import the same data the same way conceptually, but do NOT share code.** `ERP_0100_002` has its own inline `insertDataToScdulPymnSumr` / `updateTbTrnsMstr` / `updateTbTrnsDetail`; it does **not** call `ErpDataRefreshHelper`. Key behavioural differences:

| | Refresh button (`ErpDataRefreshHelper`) | Batch (`ERP_0100_002`) |
|---|---|---|
| MAST read filter | `last_status='00'` **only** (+ period + payment-type) | `last_status='00' AND erp_rcv_flag='N'` (no period, no type filter) |
| Detail read filter | `end_gb='0'` | `end_gb='0'` (same) |
| Write strategy | **UPSERT** — dedup by `(biz_key, erp_file_num, erp_file_date)`; **skips if `APPR_SEQ_NO` set** | **INSERT-only** — no dedup, no approval-lock; relies on `erp_rcv_flag='N'` to avoid re-pull |
| Re-run safety | idempotent via dedup + `end_gb='0'` filter | idempotent via `erp_rcv_flag='N'` + `end_gb='0'` filter |
| Scope | user-chosen period + payment type | all un-received batches for all `H` tenants |
| `ANT_DIV_CD='T'` map | `T → INTER` | **`T → NAPAS`** ⚠️ (the two copies disagree — see M-3/M-12) |
| Write-back values | MAST `ERP_RCV_FLAG='Y'`(+USR/DT/IHB); DETAIL `END_GB='2'`,`FLAG='Y'`,`IF_SND_*`,`FLAG_*`,`IHB_UDT_*` | **identical** write-back values |

Coexistence is safe (once a batch is received `erp_rcv_flag='Y'`, the batch skips it; the Refresh upserts rather than duplicates; both skip already-imported details via `end_gb<>'0'`). But the **refactor must pick one strategy** — see M-9.

## 23. Field-change matrix (which file writes which Oracle column)

| File | `TB_TRNS_MAST` | `TB_TRNS_DETAIL` |
|---|---|---|
| **ERP_0100_002** (import) | `ERP_RCV_FLAG='Y'`, `ERP_RCV_FLAG_USR/DT`, `IHB_UDT_USR/DT` | `END_GB='2'`, `FLAG='Y'`, `IF_SND_ID/DT`, `FLAG_USR/DT`, `IHB_UDT_USR/DT` |
| **ErpDataRefreshHelper** (Refresh) | same as ERP_0100_002 (first import sets the receive flag) | same as ERP_0100_002 |
| **ErpStatusUpdateHelper** (approval) | `LAST_STATUS`∈{10,20,32,51}; on `32` also `IHB_UDT_USR/DT`,`END_GB='1'`,`END_DATETIME`,`BUL_CNT/AMT` | reject only: `PROC_STS='XXXX'`, `END_GB='3'`, `IHB_UDT_DT`, `TR_DATE` |
| **ERP_0100_004** (dispatch result) | `LAST_STATUS='52'`, `IHB_UDT_USR/DT`, `END_GB='1'`, `END_DATETIME`, `NOR_CNT/AMT`, `ERR_CNT/AMT` (guarded `WHERE last_status='51'`) | `END_GB='8'`(ok)/`'5'`(fail), `PROC_STS`(=RS_CD or `'0999'`), `PROC_STS_MSG`, `IHB_UDT_DT`, `TR_DATE` |
| ERP_0100_001 / ERP_0100_003 | — (do not touch `TB_TRNS_*`) | — |

`LAST_STATUS` on MAST is therefore **never written by the import** — it is set by the approval helper (10/20/32/51) and the result batch (52). `PROC_STS` is set by the approval helper (XXXX on reject) and the result batch (RS_CD/0999). This matches the status-flow workbook ([§15](#15-status-flow-confirmed-by-the-status-workbook)).

## 24. Relationship to Refresh, dispatch, and result update

```
ERP inserts ─▶ [IMPORT]  ERP_0100_002 (batch)  OR  ErpDataRefreshHelper (Refresh button)
                          MAST ERP_RCV_FLAG=Y · DETAIL END_GB=2,FLAG=Y           ◀── THIS FEATURE
                                   │  (WeCMS draft created)
                                   ▼
               [APPROVAL] ErpStatusUpdateHelper   MAST LAST_STATUS 10→20→51 (reject 32)   ◀── separate
                                   │
                                   ▼
               [DISPATCH] firm-banking EFS engine (TB_FIRM_TRAN / TB_PAY_*)               ◀── separate (wecms-batch)
                                   │
                                   ▼
               [RESULT]   ERP_0100_004 (batch)   MAST LAST_STATUS=52,END_GB=1 · DETAIL END_GB=8/5,PROC_STS   ◀── separate
```

## 25. Scope decision for first implementation

### 25.1 Re-verification matrix (2026-06-28, source-confirmed)

Re-read of all six files. Columns answer: does this file **read/write Oracle**, **read/write WeCMS**, is it **required for the Refresh import**, for **approval**, for **dispatch/result**, and which **domain** it belongs to.

| File | Direction | Oracle R/W | WeCMS R/W | Req. for import? | Req. for approval? | Req. for dispatch/result? | Domain |
|---|---|---|---|---|---|---|---|
| **`ErpDataRefreshHelper`** | ERP→WeCMS | **R** `TB_TRNS_MAST`/`TB_TRNS_DETAIL`; **W** receive-status | **W** `SCDUL_*`/`OVRS_*`, `ERP_TRNS_HSTR` | ✅ **YES — IS the import** | no | no | **Payment Refresh** |
| **`ERP_0100_002`** | ERP→WeCMS | **R** `TB_TRNS_*`; **W** receive-status | **W** `SCDUL_*`/`OVRS_*`, `ERP_TRNS_HSTR` | ✅ batch twin of the import | no | no | **Payment Refresh** |
| **`ErpStatusUpdateHelper`** | WeCMS→ERP | **W** `TB_TRNS_MAST.LAST_STATUS`; reject→`TB_TRNS_DETAIL.PROC_STS/END_GB` | **R** `SCDUL_PYMN_SUMR_R017`/`OVRS_PYMN_SUMR_R011` + detail (for `ERP_FILE_*` keys) | ❌ no | ✅ yes (approval write-back) | no | Payment (approval lifecycle) |
| **`ERP_0100_004`** | WeCMS→ERP | **W** `TB_TRNS_MAST.LAST_STATUS=52`/counts; `TB_TRNS_DETAIL.END_GB=8/5`,`PROC_STS`… | **R** `SCDUL_*`/`OVRS_*` joined to `TB_FIRM_TRAN`/`TB_PAY_FILE_MST`/`TB_PAY_TARGET`; **W** summary `ERP_PROC_CMPL_YN='Y'` | ❌ no | no | ✅ yes (result write-back) | Payment (dispatch result) |
| **`ERP_0100_001`** | WeCMS→ERP | **W** `TB_FCY_EXRATE` | **R** `BANK_EXCH_RATE_INFM`; **W** `erp_snd_yn` flag | ❌ no | no | no | **Exchange rate** (not payment) |
| **`ERP_0100_003`** | WeCMS→ERP | **W** `TB_ACCT_HISTORY`, `TB_CARD_APPROVAL` | **R** `ACCT_TRNS_HSTR`, `CARD_MSTR`/`CARD_TRNS_HSTR`; **W** `erp_snd_yn` flags | ❌ no | no | no | **Account-history + Card** (not payment) |

**Conclusion: only the import (`ErpDataRefreshHelper`, with `ERP_0100_002` as its batch twin) is required for Phase 1.** The receive write-back columns Phase 1 needs (`ERP_RCV_FLAG`, DETAIL `END_GB='2'`, `FLAG='Y'`, `IF_SND_*`/`FLAG_*`/`IHB_UDT_*`) are written **by the import code itself** — `ErpStatusUpdateHelper` and `ERP_0100_004` write **different** columns (`LAST_STATUS`/`PROC_STS`) at **different** lifecycle stages and are not invoked by, nor depended on by, the import. **No mismatch with the phase plan — the ❌ marks stand.**

### 25.2 Scope decision + ❌ legend

> **❌ later** = part of the HAENGSUNG **payment** lifecycle, but **not needed for the manual Refresh import** (different columns, different trigger, later stage).
> **❌ out** = a **different ERP sync feature**, not payment Refresh at all.

| File | First-impl scope | Why | Own doc |
|---|---|---|---|
| `ErpDataRefreshHelper` | ✅ **Primary** | The Refresh-button import — the feature being built. | this doc |
| `ERP_0100_002` | ✅ in scope (decision) | Same import; Phase 2 reuses the Phase 1 service, not a second copy (OQ-10/OQ-14). | this doc §9A |
| `ErpStatusUpdateHelper` | **❌ later** | Approval-status write-back. Writes `LAST_STATUS` 10/20/32/51 (+reject `PROC_STS`) **after** a WeCMS approval action — a stage that doesn't exist yet at import time. | [haengsung-approval-status-writeback.md](haengsung-approval-status-writeback.md) |
| `ERP_0100_004` | **❌ later** | Dispatch-result write-back. Writes `LAST_STATUS=52`/`END_GB=8/5`/`PROC_STS` **after** the firm-banking dispatch engine executes — depends on dispatch being live. | [haengsung-dispatch-result-writeback.md](haengsung-dispatch-result-writeback.md) |
| `ERP_0100_001` | **❌ out** | Exchange-rate sync (`BANK_EXCH_RATE_INFM`→`TB_FCY_EXRATE`). No `TB_TRNS_*`, no payment table. | [haengsung-exchange-rate-sync.md](haengsung-exchange-rate-sync.md) |
| `ERP_0100_003` | **❌ out** | Account-history + card-approval sync (`ACCT_TRNS_HSTR`/`CARD_*`→`TB_ACCT_HISTORY`/`TB_CARD_APPROVAL`). No `TB_TRNS_*`, no payment table. | [haengsung-card-account-sync.md](haengsung-card-account-sync.md) |

### 25.3 What breaks if Phase 1 ships without them

Phase 1 is **self-contained and safe** — none of the deferred files gate the import. The cost is a **one-way visibility gap**: WeCMS pulls drafts and tells Oracle "received", but Oracle never learns what happened next.

| Omitted | What breaks | Severity for Phase 1 |
|---|---|---|
| `ErpStatusUpdateHelper` (Phase 3) | Oracle `TB_TRNS_MAST.LAST_STATUS` stays `'00'` after a WeCMS approve/reject/processing. The HAENGSUNG ERP can't see approval progress; on reject, Oracle isn't told `PROC_STS='XXXX'`/`END_GB='3'`. **WeCMS approval itself still works.** | Acceptable (R&D) — Oracle is stale on approval state only |
| `ERP_0100_004` (Phase 4) | Oracle never gets the final dispatch result (`LAST_STATUS=52`, `END_GB=8/5`, `PROC_STS`/`PROC_STS_MSG`/`TR_DATE`); WeCMS summary `ERP_PROC_CMPL_YN` stays `'N'`. ERP can't reconcile success/fail. **Requires dispatch to be live anyway**, which Phase 1 doesn't include. | Acceptable — dispatch is out of Phase 1 |
| `ERP_0100_001` (out) | Exchange rates aren't pushed to ERP. **Zero impact on payment Refresh.** | None |
| `ERP_0100_003` (out) | Account history + card approvals aren't pushed to ERP. **Zero impact on payment Refresh.** | None |

**Import idempotency does NOT depend on the deferred files.** The import never changes `LAST_STATUS` (stays `'00'`), so a re-Refresh re-reads the master — but the DETAIL filter `end_gb='0'` excludes already-imported lines (now `'2'`) and the upsert dedups, so re-runs are safe without Phase 3/4. The legacy batch `ERP_0100_002` also won't re-pull once `erp_rcv_flag='Y'` (which Phase 1 sets). ✅

## 26. New mismatches (Appendix B) — report before implementing

| ID | Severity | Mismatch | Detail | Action |
|---|---|---|---|---|
| **M-8** | 🟠 Med | **Batch ≠ Refresh helper.** | `ERP_0100_002` does **not** call `ErpDataRefreshHelper`; it is a separate inline copy (INSERT-only + `erp_rcv_flag='N'` filter, no upsert/approval-lock). Earlier framing of "the batch runs the same helper" was wrong. | The refactor must consolidate to **one** import implementation; decide whether the scheduled path is in first scope (OQ-10). |
| **M-9** | 🟠 Med | **Two divergent import strategies.** | Refresh = upsert+dedup+approval-lock; batch = insert-only. Porting both verbatim would create two code paths with different idempotency models. | Pick the **upsert** model (safer, re-runnable) for the refactor and drive both triggers (manual + optional scheduled) through it. |
| **M-10** | 🟠 Med | **Status write-back is 3 separate surfaces, not 1.** | The spec ([§7](#7-oracle-status-write-back)) covers only the **receive** write-back. Approval-status (`ErpStatusUpdateHelper`) and dispatch-result (`ERP_0100_004`) write-backs are additional HAENGSUNG↔Oracle surfaces, out of first scope but required for full parity. | Record them as **follow-on features**; the first implementation does receive write-back only. |
| **M-11** | 🟡 Low | **Result detail `END_GB` codes.** | `ERP_0100_004` writes DETAIL `END_GB='8'` (success) / `'5'` (fail) and MAST `LAST_STATUS='52'` — confirms the status-flow workbook lifecycle is produced by the **result batch**, not the import. | No action for the import feature; documents the boundary. |
| **M-12** | 🟠 Med | **`ANT_DIV_CD='T'` mapping disagrees across the two import copies.** | `ErpDataRefreshHelper`: `T→INTER`; `ERP_0100_002`: `T→NAPAS`; the Oracle doc lists no `T`. | Resolve with PO/ERP which mapping is correct (compounds M-3). |

## 27. Updated open items (Appendix B)

| ID | Item |
|---|---|
| **OQ-13** | Confirm `ERP_0100_002` (and 001/003/004) **cron cadence** in the Jex Studio batch scheduler / `TB_BATCH_SCHEDULE_*` (memory suggests ~5-min for 002). |
| **OQ-14** | Decide the **import-trigger model** for the refactor: Refresh-only, or Refresh + scheduled — and consolidate to one upsert-based service (M-8/M-9). |
| **M-8…M-12** | Resolve with PO before coding; M-8/M-9/M-10/M-12 affect the import design directly. |
