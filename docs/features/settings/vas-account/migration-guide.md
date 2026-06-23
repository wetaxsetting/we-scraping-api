# Settings > VAS Account — Migration Guide (JEX → Spring)

JEX `comm_0217_*` + `comm_1002_001` → refactor `vas-account`. Companion (preserved):
[references/migration/vas-account.md](../../../references/migration/vas-account.md). JEX source of
truth: [docs/jex/features/vas-account/migration-guide.md](../../../../../docs/jex/features/vas-account/migration-guide.md).

---

## 1. WSVC → controller/service mapping

| Legacy WSVC | Operation | Refactor endpoint | Service method |
|---|---|---|---|
| `comm_0217_001` / `_001_r001` | list / search | `GET /api/v1/settings/vas-account` | `VasAccountService.list` / `listAll` |
| `comm_1002_001` | lookup picker | `GET …/lookup` | `VasAccountService.lookup` |
| `comm_0217_004_r001` | detail (edit load) | `GET …/{vasAcctSeqNo}` | `VasAccountService.detail` |
| `comm_0217_003_c001` | create | `POST …` | `VasAccountService.create` |
| `comm_0217_004_u001` | update | `PUT …/{vasAcctSeqNo}` | `VasAccountService.update` |
| `comm_0217_001_d001` | soft delete | `DELETE …/{vasAcctSeqNo}` | `VasAccountService.softDelete` |
| `comm_0217_002_r001` | Excel parse + validate | `POST …/excel/preview` | `VasAccountExcelService.preview` |
| `comm_0217_002_c001` | Excel import save (A/R) | `POST …/excel/import` | `VasAccountExcelService.import_` |
| `comm_0217_002_r002` | Excel per-cell re-validate | **DROPPED** (see §5) | — |
| — | XLSX export (new) | `POST …/export` | `VasAccountExportProvider` (shared `common/export`) |

## 2. IDO → repository mapping

| Legacy IDO | Refactor repository method |
|---|---|
| `BUSI_VAS_ACCT_INFM_R001` (read all) | `search(bizKey, statusFilter, keywordPattern, pageable)` (JPQL) |
| `BUSI_VAS_ACCT_INFM_R002` (lookup, `DISP_VAS_ACCT_NM` concat) | `findLookup(bizKey, keywordPattern)` (active+live, ORDER BY code) |
| `BUSI_VAS_ACCT_INFM_C001` (insert) | `JpaRepository.save` |
| `BUSI_VAS_ACCT_INFM_U001` (full update) | `JpaRepository.save` on a loaded entity |
| `BUSI_VAS_ACCT_INFM_U002` (DEL_YN update) | `save` after `delYn="Y"` (single-record); `softDeleteAllLiveForTenant(...)` for Excel Replace |
| `BUSI_VAS_ACCT_INFM_D001` (physical delete) | **not ported** — dead code on both sides |
| — | `existsByBizKeyAndVasAcctCdAndDelYn` (dup-check), `findByBizKeyAndVasAcctSeqNoAndDelYn` (live load), `findByBizKeyAndVasAcctCdAndDelYn` (banking code lookup), `findLiveCodesByBizKey` (Excel dup); sequence allocation routes through `LegacyFunctionRepository.nextVasAcctSeqNo` (the `wb_get_next_val` funnel), not a repository finder |

## 3. Envelope / naming migration

- Legacy `COMMON_HEAD` / `REC` envelope → `{ status, data, timestamp?, path?, trace_id? }`.
- Wire flipped to lowercase snake_case (global Jackson `SNAKE_CASE`); Java fields stay camelCase.
- Legacy `TOT_CNT` / `SUC_CNT` / `ERR_CNT` / `REC` Excel-preview shape → `total_count` /
  `completed_count` / `error_count` / `rows`.
- Legacy ML codes → `WBExxxx`: duplicate `WBE0003`; not-found `WBE2040`; Excel row-cap `WBE2041`,
  header `WBE2042`, unreadable `WBE2043`.
- Audit `WSVC_ID` writes the slug `vas-account` (never `comm_0217_*`).

## 4. Key-generation migration

Legacy `BusiVasAcctInfmDAO.create()` →
`SeqHelper.getNextVal(util, idoCon, "BUSI_VAS_ACCT_INFM", "vas_acct_seq_no", bizKey)` (per-`BIZ_KEY`
MAX+1 via the shared `GET_NEXT_VAL` IDO — no Postgres sequence object).

Refactor → `VasAcctSeqNoGenerator.next(bizKey)` routes through `LegacyFunctionRepository.nextVasAcctSeqNo`
— the single `wb_get_next_val` funnel (V140 branch #25; per-`BIZ_KEY` MAX+1 copied verbatim from the live
JEX function), keeping allocation non-colliding with the live JEX / WABOOKS systems. See
[business-flow.md §6](business-flow.md#6-sequence-generation--concurrency) for the concurrency note
(no one-shot retry on this path — a deliberate difference from Chart of Accounts).

## 5. Behaviour deliberately diverged / deferred

| Item | Legacy | Refactor |
|---|---|---|
| Excel per-cell re-validate endpoint (`comm_0217_002_r002`) | re-validated one cell on inline preview edit | **Dropped.** The preview grid no longer edits cells in-place (Type / English columns removed by PO call 2026-05-11), so no per-cell endpoint is needed. |
| Excel name cell max length | 100 (legacy `comm_0217_002_r001_act.jsp`) | **50** — standardised on the DB column + single-create rule. |
| `CONT` (notes) max length | 500 (legacy WSVC reservation) | **200** — DB-column authority (DN-3). |
| Excel template columns read | 7-column template | columns **B** (code) + **C** (name) only; the other 5 ignored. |
| Sequence concurrency retry | n/a | **No retry** on PK collision (Chart of Accounts retries once — VAS Account does not). |
| Hierarchy / parent | flat list (no parent column) | **flat list** — unchanged. Frontend prefix-tree grouping is display-only. |
| Bulk activate/deactivate / bulk delete | none | **none** — parity. |
| `VAS_ACCT_SEQ_NO` storage | NUMERIC(10,0) | **NUMERIC(10,0)** (V37 — DEV parity); entity maps `Long` via `@JdbcTypeCode(SqlTypes.NUMERIC)`. No type deviation. |
| Integration tests | n/a | `VasAccountIntegrationTest` **`@Disabled`** (F1) pending Testcontainers + JWT-mock infra. |
