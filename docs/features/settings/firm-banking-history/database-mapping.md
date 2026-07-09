# Firm Banking History — Database Mapping (Backend)

The source tables, the read-only `@Subselect`-backed consolidation entity, and the full
`legacy column → entity field → DTO field → UI column` mapping. Parent: [README.md](README.md).

> **No DB view.** Despite the historical naming, there is **no** `V_FIRM_BANKING_HISTORY` database
> view. The five-leg union lives in a Hibernate `@Subselect` on `FirmBankingHistoryView` over the legacy
> base tables (`project_v_firm_banking_history_subselect.md`). There is no view-creating Flyway
> migration; `ddl-auto: validate` never looks for a `v_firm_banking_history` relation.
Schema design detail: [references/database/settings/firm-banking-history-schema.md](../../../references/database/settings/firm-banking-history-schema.md).
Legacy DB mapping (read-only): [docs/jex/.../database-mapping.md](../../../../../docs/jex/features/firm-banking-history/database-mapping.md).

---

## 1. Core tables (actual)

All five tables are in the **PROD Active Table List** (wecms-api/CLAUDE.md). The refactor reads them
through one `@Subselect` entity; none is written by this feature.

| Table | Role | Leg | PK (DEV) | Local Flyway | Tenant col |
|---|---|---|---|---|---|
| `TB_FIRM_TRAN` | Korean firm-banking gateway calls | `F` | `(TRAN_DT, BIZ_KEY, TRAN_SEQ)` | **V104** | `BIZ_KEY` |
| `NAPAS_REMIT_TRNS_HSTR` | NAPAS scraping remittance transfers | `S` | `(BIZ_KEY, SEQ_NO)` | V13 (present) | `BIZ_KEY` |
| `EXT_TRAN_CALL_HIS` | Generic scraping balance / transaction / card calls | `S` | `(SVC_ID, REF_DATE, SEQ_NO)` | V16 (present) | `BIZ_KEY` |
| `NXSIAS_MODULE_TRAN_LOG_HIS` | NXiSAS bridge — GDT e-Invoice + Vietnam business inquiries | `G` / `B` | composite `(BIZ_KEY, REG_DTM, UUID)` | **V73** | `BIZ_KEY` |
| `TB_FIRM_BANK_CODE` | NAPAS/firm bank-code lookup (used by `wb_get_napas_bank_nm`) | — | — | **V97** | — |

Supporting Flyway migrations (post 2026-06-09 renumber; the old V59/V60/V61/V165 set no longer exists):

| Migration | Purpose |
|---|---|
| [V73](../../../../src/main/resources/db/migration/V73__nxsias_module_tran_log_his.sql) | Source table `NXSIAS_MODULE_TRAN_LOG_HIS` |
| [V97](../../../../src/main/resources/db/migration/V97__tb_firm_bank_code.sql) | Source table `TB_FIRM_BANK_CODE` |
| [V104](../../../../src/main/resources/db/migration/V104__tb_firm_tran.sql) | Source table `TB_FIRM_TRAN` |

> **No View Setting seed.** Firm Banking History is an **export-only** screen — it renders no View
> Setting popover, so it is **intentionally NOT seeded** into `SYS_SCR_OPTION_WECMS`. The manual
> `V156__sys_scr_option_wecms.sql` DBA script explicitly lists `firm-banking-history` in its
> EXCLUDED set ("backend export provider only; no FE popover"). Export still works: when
> `ScreenOptionService.load('firm-banking-history', …)` finds no sys rows it throws `WBE2001`, and
> `ExcelExportService.resolveColumns` catches that, skips the persisted-visibility intersection, and
> falls back to **request columns ∩ provider allow-list** (memory `project_export_only_screens_no_seed.md`).
> There is no row in any migration that seeds `web_svc_id = 'firm-banking-history'`.

> The helper functions `kr_to_vn_time` / `wb_get_napas_bank_nm` and the five-leg union are **not**
> created by a Flyway migration in the refactor — there is no view-creating migration. The `@Subselect`
> runs the union against the legacy base tables + functions, which exist in the target JEX DB.

---

## 2. The consolidation `@Subselect` — `FirmBankingHistoryView`

A five-leg `UNION ALL` ported from the legacy `IDO.TB_FIRM_TRAN_R002.xml`, carried by a Hibernate
`@Subselect` on one `@Immutable` JPA entity — no DB view, no five-leg fan-out in Java. The columns
below are the `@Subselect`'s output projection (not a relation in `information_schema`):

| View column | Type | F (`TB_FIRM_TRAN`) | S (`NAPAS_REMIT_TRNS_HSTR`) | S (`EXT_TRAN_CALL_HIS`) | G/B (`NXSIAS_MODULE_TRAN_LOG_HIS`) |
|---|---|---|---|---|---|
| `TARN_DTM` | varchar (unbounded) | `kr_to_vn_time(TRAN_DT‖TRAN_TM)` | `REG_DTM` | `REG_DTM` | `REG_DTM` |
| `SVC_TYPE` | varchar(1) | `'F'` | `'S'` | `'S'` | `'G'` / `'B'` |
| `SVC_NM` | varchar(50) | `TRAN_TP` | `'Funds Transfer (Scraping)'` | CASE on `SVC_ID` → Scraping Balance/Transaction/Card | CASE on `NXISAS_TYPE` (G) / `'Vietnam Business Inquiry'` (B) |
| `SEQ_NO` | varchar(50) | `TRAN_SEQ` | `SEQ_NO::varchar` | `SEQ_NO::varchar` | `UUID` |
| `DEP_BANK_CD` | varchar(200) | `wb_get_napas_bank_nm(OUT_BANK_CD)‖' - '‖OUT_BANK_CD` | `wb_get_bank_nm(OUT_BANK_CODE,'s')‖' - '‖OUT_BANK_CODE` | `wb_get_bank_nm(BANK_CD,'s')‖' - '‖BANK_CD` | `NXISAS_MODULE` |
| `RSLT_CD` | varchar(20) | `RS_CD` | `TRNS_STTS` | `RSLT_CD` | `NXISAS_ERR_CODE` |
| `RSLT_MSG` | varchar(2000) | `RS_CD` (frontend resolves via `DICTIONARY_VAN`) | `TRNS_MSG` | `ERR_CONT` | `NXISAS_ERR_CONT` |
| `JOB` | varchar(5000) | `TRAN_TP` | `'TRANSFER'` | `'BALANCE'`/`'TRANSACTION'`/`'CARD_USING'` (by `SVC_ID`) | `NXISAS_JOB` |
| `REQ_DATA` | varchar (unbounded) | `NULL` | `NULL` | `NULL` | `REQ_DATA` |
| `BIZ_KEY` | varchar(20) | `BIZ_KEY` | `BIZ_KEY` | `BIZ_KEY` | `BIZ_KEY` |
| `VAN_ORG_CD` | varchar(20) | `VAN_ORG_CD` | `NULL` | `NULL` | `NULL` |

**Per-leg WHERE filters** (each scoped to `BIZ_KEY = :bizKey` first):
- **F:** `TB_FIRM_TRAN` — tenant only.
- **S/napas:** `NAPAS_REMIT_TRNS_HSTR` — tenant only.
- **S/ext_tran:** `EXT_TRAN_CALL_HIS WHERE SVC_ID IN ('BALANCE_INQUIRY','REAL_TIME_ONLINE_INQUIRY','CARD_INQUIRY')`.
- **G:** `NXSIAS_MODULE_TRAN_LOG_HIS WHERE NXISAS_TYPE IN ('E-INVOICE_SALES_INQUIRY','E-INVOICE_EXPENSE_INQUIRY')`.
- **B:** `NXSIAS_MODULE_TRAN_LOG_HIS WHERE NXISAS_JOB IN ('BUSSINESS_REGISTRATION_DETAIL','BUSSINESS_REGISTRATION_LIST')`.

### Helper functions (ported verbatim from JEX DEV)

| Function | Used by | Notes |
|---|---|---|
| `kr_to_vn_time(varchar)` | F leg `TARN_DTM` | KR `YYYYMMDDHHMMSS` → VN local time. **Applied to the F leg only** — S/G/B `REG_DTM` is already VN-local at insert. |
| `wb_get_napas_bank_nm(varchar)` | F leg `DEP_BANK_CD` | NAPAS-specific bank-name lookup over `TB_FIRM_BANK_CODE`. |
| `wb_get_bank_nm(varchar,'s')` | S legs `DEP_BANK_CD` | Reused from V44 (Account Inquiry). |

---

## 3. Entity mapping

[`FirmBankingHistoryView`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/entity/FirmBankingHistoryView.java) —
`@Entity @Immutable @Subselect("""…5-leg UNION ALL…""") @Synchronize({…base tables…}) @IdClass(FirmBankingHistoryViewId.class)`.

Composite `@IdClass` = `(bizKey, tarnDtm, svcType, seqNo)` — the `@Subselect` has no real PK, so this
tuple provides Hibernate cache identity (the natural per-leg identity).

| Entity field | `@Column` | `@Id` |
|---|---|---|
| `bizKey` | `BIZ_KEY` | ✅ |
| `tarnDtm` | `TARN_DTM` | ✅ |
| `svcType` | `SVC_TYPE` | ✅ |
| `seqNo` | `SEQ_NO` | ✅ |
| `svcNm` | `SVC_NM` | |
| `depBankCd` | `DEP_BANK_CD` | |
| `rsltCd` | `RSLT_CD` | |
| `rsltMsg` | `RSLT_MSG` | |
| `job` | `JOB` | |
| `reqData` | `REQ_DATA` | |
| `vanOrgCd` | `VAN_ORG_CD` | |

> The entity declares conventional `length` values on some fields (e.g. `tarnDtm` 14, `reqData`
> 1000); the `@Subselect` projects `tarn_dtm` and `req_data` as unbounded/wide varchar. A read-only
> `@Immutable` `@Subselect` entity skips schema validation and never issues DML, so the cosmetic
> length mismatch is inert.

---

## 4. End-to-end column mapping

`Legacy column → Refactor entity field → DTO/wire field → UI column`. The DTO
([`FirmBankingHistoryRow`](../../../../src/main/java/com/kosign/wecms/domain/setting/firmbankinghistory/dto/FirmBankingHistoryRow.java))
is a 12-component record; Java camelCase → wire snake_case via the global Jackson strategy.

| Concept | Legacy (IDO `<out>`) | View column | Entity field | Wire field (DTO) | UI column / use |
|---|---|---|---|---|---|
| Request/Process timestamp | `TARN_DTM` | `TARN_DTM` | `tarnDtm` | `tarn_dtm` | Date/Time (split, VN TZ) |
| Service type (leg) | `SVC_TYPE` | `SVC_TYPE` | `svcType` | `svc_type` | Service Type |
| Service type i18n key | — (new) | — | (resolver) | `svc_type_key` | Service Type label via `t()` |
| Service name (raw) | `SVC_NM` | `SVC_NM` | `svcNm` | `svc_nm` | Service Name (fallback) |
| Service name i18n key | — (new) | — | (resolver) | `svc_name_key` | Service Name label via `t()` |
| Transaction / sequence id | `SEQ_NO` | `SEQ_NO` | `seqNo` | `seq_no` | Seq. No. (keyword target) |
| Deposit bank / inst. code | `DEP_BANK_CD` | `DEP_BANK_CD` | `depBankCd` | `dep_bank_cd` | Deposit Bank / Inst. Code |
| Result code | `RSLT_CD` | `RSLT_CD` | `rsltCd` | `rslt_cd` | Results pill + popover |
| Result message | `RSLT_MSG` | `RSLT_MSG` | `rsltMsg` | `rslt_msg` | Popover response message |
| Job identifier | `JOB` | `JOB` | `job` | `job` | Popover (service-name last-resort) |
| Request payload (G/B) | `REQ_DATA` | `REQ_DATA` | `reqData` | `req_data` | Popover (e-Invoice no. / Tax ID parse) |
| VAN org code (F) | `VAN_ORG_CD` | `VAN_ORG_CD` | `vanOrgCd` | `van_org_cd` | Popover (F-leg VAN code) |
| Tenant key | `BIZ_KEY` | `BIZ_KEY` | `bizKey` | — (never on the wire) | Tenant scope filter only |

### Generic-concept ↔ this-screen field crosswalk

The task brief references generic banking-history concepts; here is how each maps onto the actual
five-leg model (several are leg-relative, not literal columns):

| Generic concept | This screen |
|---|---|
| Transaction ID | `seq_no` (F: `TRAN_SEQ`; S: `SEQ_NO`; G/B: `UUID`) |
| Request Date / Process Date | single `tarn_dtm` (F: `kr_to_vn_time(TRAN_DT‖TRAN_TM)`; others: `REG_DTM`) |
| Account Number | not modelled — this is a non-monetary call log, not an account ledger |
| Bank Code / Bank Name | `dep_bank_cd` carries the resolved "`<name> - <code>`" string |
| Currency / Amount | n/a — non-monetary audit log (no amount column) |
| Status | derived `result` from `rslt_cd` (success / failed) |
| Result Code / Result Message | `rslt_cd` / `rslt_msg` |
| User | not modelled — legacy screen has no per-row user column |
| Created Date | `tarn_dtm` (the call timestamp) |

---

## 5. Indexing & tenant safety

- Every leg filters `BIZ_KEY = :bizKey` **first**, hitting each source table's tenant-scoped index
  before the union materialises. Sort + paging apply to the materialised set.
- `BIZ_KEY` is non-null on all five tables, so tenant scoping introduces no null-row leakage.
- At R&D scale (~111K rows across all legs in DEV, narrowed to < 1K by a typical date+tenant window)
  no extra index is required; Phase 2 profiling against a ≥30-day window is the trigger to add
  `(BIZ_KEY, REG_DTM)` per source table if `EXPLAIN` warrants it.
