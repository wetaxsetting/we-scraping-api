# ERP Transaction History — Migration Guide (JEX → Spring)

How each JEX layer became its Spring counterpart. Authoritative narrative + full JEX endpoint contract:
the preserved flat spec [../transaction-history.md](../transaction-history.md).

---

## Layer-by-layer

| JEX layer | JEX artifact | Spring counterpart |
|---|---|---|
| Screen | `comm_0219_001_view.jsp` + `comm_0219_001.ts` | `<ErpTransactionHistoryPanel>` (Settings modal panel — no route) |
| List read | `comm_0219_001_r001` (IDO `ERP_TRNS_HSTR_R001`) | `GET /erp-transaction-history` → `ErpTrnsHistoryService.list` → `repo.search` |
| Tab counts | `ERP_TRNS_HSTR_R001_CNT` (drops `ERP_TYPE`) | merged into the list call → `repo.countAllDirections` (same WHERE minus `erp_type`) |
| User name | `WB_GET_USER_NM(REG_ID)` DB function | `LEFT JOIN UserInfm u ON u.userId = e.regId` in `repo.search` |
| Download | `comm_0219_001_r002` (IDO + view JSP) | `GET /{seqNo}/download` → `service.resolveDownload` → `ErpTrnsHistoryFileResolver` |
| Thumbnail | `comm_0219_001_r002?cmd=getthumb` (640px JPEG) | `GET /{seqNo}/thumbnail` → **stub** (`WBE9001`) |
| Export | none (no legacy export) | `POST /export` → `ErpTrnsHistoryExportProvider` (shared `common/export/` pipeline) |
| Upload | `comm_0219_001_c001` (IDO `ERP_TRNS_HSTR_C001`) | **NOT ported** (OQ-1) — `ErpTrnsHistoryWriter` is the future per-feature write seam |
| Soft-delete | `ERP_TRNS_HSTR_U001` / `_D001` | **NOT ported** (OQ-8) — written by other Payment features, displayed here read-only |
| Entity | `wabooks.entity.ErpTrnsHstr` + `ErpTrnsHstrPk` | `ErpTrnsHstr` + `ErpTrnsHstrId` (`@IdClass`) |
| DAO | `ErpTrnsHstrDAO` | Spring Data `ErpTrnsHstrRepository` (2 JPQL queries) |
| Key-gen | `SeqHelper` → `wb_get_next_val('erp_trns_hstr','seq_no',…)` | `LegacyFunctionRepository.nextErpTrnsSeqNo` (funnel; used by the inert writer only) |
| Date filter | `SUBSTRING(REG_DTM FROM 1 FOR 8) BETWEEN ? AND ?` | identical JPQL `SUBSTRING(e.regDtm, 1, 8) BETWEEN :fromDate AND :toDate` |
| Validation | `ValidationHelper.validateDate8` / `validatePeriod` | service-side `validateDate` / `validatePeriod` (+ refactor `erp_type` / `trns_type_cd` checks) |
| Sort | `SortHelper.getSortDynamic(util, "comm_0219_001_r001")` | `ErpTrnsHistorySortField` whitelist + Spring Data `Sort` |

---

## Identifier translation (slug, not WSVC id)

Every refactor identifier surface carries the kebab slug **`erp-transaction-history`** — REST URI, Java
package (`…domain.setting.erptransactionhistory`), class names (`ErpTrnsHistoryController`,
`ErpTrnsHistoryService`, `ErpTrnsHstr`), the storage-root config prefix
(`wecms.erp-transaction-history`), the per-tenant storage folder (`SLUG_FOLDER`), the export
`screenId`, and the View Setting `WEB_SVC_ID`. The menu permission code is the route-aligned slug
**`settings-erp-transaction-history`**. Legacy WSVC ids (`comm_0219_001`, …) appear only in JEX
file:line citations and migration prose.

Note the **two distinct "transaction history" features** that must not collide:

| Feature | Slug | JEX | Frontend folder |
|---|---|---|---|
| Banking > Transaction History (account ledger) | `transaction-history` | `comm_0220_001` | `components/banking/transaction-history/` |
| **Settings > ERP Transaction History (this feature)** | **`erp-transaction-history`** | `comm_0219_001` | `components/settings/erp-transaction-history/` |

---

## Behaviour changes from legacy (intentional)

| Area | Legacy | Refactor |
|---|---|---|
| User-name resolution | `WB_GET_USER_NM(REG_ID)` DB function | LEFT JOIN `UserInfm` (no new DB-function dependency) |
| `erp_type` validation | any 1-char accepted | validated against `{W,E,D}` → `WBE_VALIDATION` |
| `trns_type_cd` validation | passed through | validated `"01".."17"` → `WBE_VALIDATION` |
| Paging | 1-based, unclamped | 0-based, clamped to 100 (default 15) |
| Product gating | HAENGSUNG-only tabs/types/File-column (`prdt_type='H'`) | **product-agnostic** — all tabs / all 17 types / File column always shown (OQ-2) |
| Code-label rendering | hardcoded JS switch (`comm_0219_001.ts`) | server-resolved `erp_type_key` / `trns_type_key` on the wire → `t(key)` |
| Export | none | added (shared XLSX pipeline) |
| Authorization | menu visibility + `NORM_USER_ONLY_YN=Y` | explicit `@menuAuthz.canAccess('settings-erp-transaction-history')` (F-3) |
| Audit on read | `mntLogYn=Y` per-read `AUDIT_LOGS_INFM` insert | **no audit row** on read (firm-banking-history precedent) |
| Download filename | `SAVE_FILE_NM` | unchanged — `SAVE_FILE_NM` (RFC 5987 UTF-8) |
| Storage path | `<NAS_ROOT>/<biz_key>/comm_0219_001_c001/<save_file_nm>` | `<storage-root>/<biz_key>/erp-transaction-history/<save_file_nm>` (slug folder, OQ-3) |

---

## Dropped / dead legacy flows (not ported)

| Legacy flow | JEX artifact | Disposition |
|---|---|---|
| Upload | `comm_0219_001_c001` (`ERP_TRNS_HSTR_C001`) | **DEAD LEGACY / out of scope (OQ-1)** — the JSP is broken/unused (requires `ERP_TYPE`/`TRNS_TYPE_CD` it never sets). Future Payment features write their own audit row via `ErpTrnsHistoryWriter`. |
| Soft-delete write | `ERP_TRNS_HSTR_U001` / `_D001` | **Out of scope (OQ-8)** — Deleted (`ERP_TYPE='D'`) rows are written by other Payment screens (Scheduled Payment "Cancel") and only **displayed** here read-only. |
| Batch writers | `ERP_0100_001..004` + `ErpDataRefreshHelper` | **Out of scope** — scheduled jobs not yet ported; the screen renders seeded rows. |
| Thumbnail | `comm_0219_001_r002?cmd=getthumb` | **Stub** (`WBE9001`) — no current consumer. |
| HAENGSUNG product variants | `prdt_type='H'` branches in the view JSP / TS | **DEAD LEGACY (OQ-2)** — refactor is product-agnostic. |

See [refactor-gap-analysis.md](refactor-gap-analysis.md) for the full classification + evidence.

---

## Schema migration (Flyway)

| Live file | Purpose |
|---|---|
| [`V54__erp_trns_hstr.sql`](../../../../src/main/resources/db/migration/V54__erp_trns_hstr.sql) | `CREATE TABLE ERP_TRNS_HSTR` (9 cols, PK `(BIZ_KEY, SEQ_NO)`, column-for-column DEV parity) |

> **Numbering note.** The `wecms-api/CLAUDE.md` migration table narrates this feature's schema as
> "V71/V72" and a "V154" View Setting seed. Those are pre-2026-06-09-renumber numbers and are **stale** —
> the live table creation is `V54__erp_trns_hstr.sql`, and no live migration seeds
> `erp-transaction-history` View Setting rows (the `V156__sys_scr_option_wecms.sql` seed explicitly
> *excludes* the slug). The export-column seed gap is tracked as GAP-4 in
> [refactor-gap-analysis.md](refactor-gap-analysis.md).

Demo seed rows for tenant `VN_VNM0004_000001` are documented in the CLAUDE.md narrative (30 rows from
real JEX DEV, all `FILE_SIZE > 0` but no physical files committed → download surfaces `WBE2015`); the
live seed file (if separate) lives under `db/migration/`.
