# ERP Transaction History — Refactor Gap Analysis (Parity Review)

Legacy JEX (`comm_0219_001` family, `ERP_TRNS_HSTR`) vs. the refactor (`erp-transaction-history`).
Status vocabulary: **MATCH** · **APPROVED DEVIATION** · **DEAD LEGACY** · **PARTIAL** · **MISSING**.

This is a **read-only audit ledger**. The legacy upload write-path is intentionally **not ported**
(OQ-1, classified DEAD LEGACY below).

---

## Parity matrix

| Area | Legacy | Refactor | Status |
|---|---|---|---|
| **List + filters** | `comm_0219_001_r001` (date / `erp_type` / `trns_type_cd` / `reg_id` keyword) | `GET /erp-transaction-history` — same filter set, same `SUBSTRING(REG_DTM,1,8)` window | **MATCH** |
| **Tab counts** | `ERP_TRNS_HSTR_R001_CNT` — 4 counts, `ERP_TYPE` dropped | `countAllDirections` — same WHERE minus `erp_type`, `COALESCE`-guarded | **MATCH** |
| **User name** | `WB_GET_USER_NM(REG_ID)` | LEFT JOIN `UserInfm` (null for `SYSTEM`/removed → FE falls back to `reg_id`) | **APPROVED DEVIATION** (no new DB-function dependency) |
| **Download (full file)** | `comm_0219_001_r002` — stream by `(biz_key, seq_no)`, filename = `SAVE_FILE_NM` | `GET /{seqNo}/download` — same tenant check, same filename, `WBE2014`/`WBE2015` | **MATCH** |
| **Download guards** | tenant mismatch throws; `FILE_SIZE=0` link hidden FE-side | `WBE2014` (not found/tenant) + `WBE2015` (size 0 / missing) | **MATCH** (+ explicit server codes) |
| **Thumbnail** | `?cmd=getthumb` 640px JPEG | `GET /{seqNo}/thumbnail` → `WBE9001` stub | **PARTIAL** (GAP-2 — deferred, no consumer) |
| **Sort** | `SortHelper` free-form `data-sort` (incl. `FILE`) | `ErpTrnsHistorySortField` whitelist; `FILE` non-sortable | **APPROVED DEVIATION** (GAP-3 / OQ-5) |
| **Default sort** | `SortHelper` default (unconfirmed) | `REG_DTM DESC, SEQ_NO DESC` (OQ-7) | **PARTIAL** (confirm vs. SortHelper) |
| **Validation** | `validateDate8` / `validatePeriod` | same + `erp_type ∈{W,E,D}` + `trns_type_cd "01".."17"` | **APPROVED DEVIATION** (stronger) |
| **Code-label rendering** | hardcoded JS switch | server `erp_type_key` / `trns_type_key` → `t(key)` | **APPROVED DEVIATION** |
| **Export (XLSX)** | none | `POST /export` (shared pipeline) | **APPROVED DEVIATION** (refactor add — see GAP-4 seed gap) |
| **Authorization** | menu visibility + `NORM_USER_ONLY_YN=Y` | `@menuAuthz.canAccess('settings-erp-transaction-history')` | **APPROVED DEVIATION** (stronger, explicit) |
| **Audit on read** | `mntLogYn=Y` per-read `AUDIT_LOGS_INFM` insert | no audit row on read | **APPROVED DEVIATION** (firm-banking-history precedent) |
| **Product gating** | HAENGSUNG-only tabs / types / File column | product-agnostic (no `prdt_type`) | **DEAD LEGACY** (OQ-2) |
| **Upload** | `comm_0219_001_c001` | not ported; future per-feature writer | **DEAD LEGACY** (GAP-1 / OQ-1) |
| **Soft-delete write** | `ERP_TRNS_HSTR_U001` / `_D001` | not ported; written elsewhere, displayed read-only | **DEAD LEGACY** (OQ-8) |
| **Batch writers** | `ERP_0100_001..004` + `ErpDataRefreshHelper` | not ported (scheduled jobs deferred) | **MISSING** (out of scope; seeded rows render the screen) |
| **View Setting popover** | (legacy panel has no list-icon) | not adopted in panel (OQ-FE-2) | **MATCH** (nothing to adopt) |

---

## Specially-verified gaps

### GAP-1 — Upload write-path (DEAD LEGACY, OQ-1)

The legacy `comm_0219_001_c001` upload action is **not ported**. Evidence it is dead in production: the
JSP renders no `<input type="file">`, and the action requires `ERP_TYPE` / `TRNS_TYPE_CD` via
`entity.validate()` but never sets them — it would throw at runtime if exercised. The refactor exposes
**no upload endpoint**. Each future Payment / Transfer / Account-Inquiry feature that produces an
ERP-exchange file writes its own `ERP_TRNS_HSTR` row through the shared
[`ErpTrnsHistoryWriter`](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistoryWriter.java)
(`recordUploadedFile` / `recordDeletedFile` / `recordExportedFile`) from its own `@Transactional`
scope. **The writer is present but has no live caller in the current tree** — it is correctly inert
until the first Payment feature lands. **Classification: DEAD LEGACY** (upload) + **deferred wiring**
(writer). No action needed for this feature's scope.

### GAP-2 — Thumbnail stub (PARTIAL)

`comm_0219_001_r002?cmd=getthumb` serves a 640px JPEG. No current frontend consumer surfaces
thumbnails, so the refactor `GET /{seqNo}/thumbnail` returns `WBE9001` ("not yet implemented") — fails
loudly rather than a misleading 404. **Classification: PARTIAL** — port when a real consumer arrives.

### GAP-3 — `FILE` column non-sortable (APPROVED DEVIATION, OQ-5)

The legacy table header carries `<th data-sort="FILE">` but no IDO column is named `FILE` — sorting by
File was undefined in JEX (fell back to default order). `ErpTrnsHistorySortField` deliberately omits
`FILE`; the frontend renders the File header with no sort glyph. **Classification: APPROVED DEVIATION** —
the legacy ambiguity is not a contract worth preserving.

### GAP-4 — View Setting export seed MISSING (export unverified)

`ErpTrnsHistoryExportProvider` (`screenId = erp-transaction-history`) depends on `SYS_SCR_OPTION_WECMS`
rows for that screenId — `ExcelExportService` intersects the request columns with the persisted View
Setting visibility, and **drops every column with `WBE2013` if no seed rows exist**. The `wecms-api/CLAUDE.md`
migration table narrates a "V154" seed of 7 columns for `erp-transaction-history`, but the **live**
`V156__sys_scr_option_wecms.sql` seed file **explicitly excludes** the slug
([V156 line 94](../../../../src/main/resources/db/migration/V156__sys_scr_option_wecms.sql): *"erp-transaction-history
(backend export provider only; no FE popover)"* under "EXCLUDED FROM THIS SEED"). No other live
migration seeds the rows. **Classification: MISSING** — the export endpoint is wired (controller +
provider + allow-list) but **unverified end-to-end**; a hand-run DBA seed of the 7 columns
(`REG_DTM`, `ERP_TYPE`, `TRNS_TYPE_CD`, `ORG_FILE_NM`, `FILE_SIZE`, `REG_USER`, `REG_ID`) under
`web_svc_id='erp-transaction-history'` / `dym_area_id='default'` is required before export works. Raise
with the PO (Mr. Jung) / DBA — the CLAUDE.md narrative and the live seed disagree, and **the seed wins
must be reconciled**.

### Audit Parity

The refactor writes **no** `AUDIT_LOGS_INFM` row on any read path. Legacy `comm_0219_001` carries
`mntLogYn=Y` (per-request audit insert). The refactor follows the `firm-banking-history` precedent —
audit writes are reserved for mutating CRUD; read-only ledgers don't audit. **Classification: APPROVED
DEVIATION.**

### Default-sort Parity (OQ-7)

`DEFAULT_SORT = REG_DTM DESC, SEQ_NO DESC` is the assumed-legacy default. A future read of
`SortHelper.getSortDynamic(util, "comm_0219_001_r001")` may confirm or adjust it — a backend-only change
with no contract impact. **Classification: PARTIAL** (confirm).

---

## Migration status

```
Refactor Status: read paths (list + counts + download) COMPLETE & legacy-verified.
                 Export WIRED but seed-gated (GAP-4 — unverified until the View Setting seed lands).
                 Upload write-path intentionally NOT ported (OQ-1 — DEAD LEGACY).
```

Residual (tracked):
1. **GAP-4** — seed `erp-transaction-history` View Setting columns (or confirm export is out of scope) —
   the CLAUDE.md "V154" narrative vs. the live `V156` exclusion must be reconciled with the PO/DBA.
2. **OQ-7** — confirm default sort against `SortHelper`.
3. **GAP-1 / OQ-1** — `ErpTrnsHistoryWriter` has no live caller; wired when the first Payment feature
   lands (not a defect for this feature).
4. **GAP-2** — thumbnail stub; port when a consumer appears.
5. **Migration-number drift** — `V54` (live) vs. the stale `V71/V72/V154` CLAUDE.md narrative; doc-only.

None of the residual items affect the implemented read-path parity or correctness.
