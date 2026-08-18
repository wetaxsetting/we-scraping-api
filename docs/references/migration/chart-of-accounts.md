# Chart of Accounts — JEX → Refactor Migration Map


> ⚠️ **Flyway numbering in this document is PRE-REBASELINE (note added 2026-08-17).**
> Bare `V<n>` references below were written against the migration set as it stood **before** the
> [2026-06-09 rebaseline](../database/flyway-migration-baseline.md), which renumbered every migration to a
> one-table-per-migration scheme and dropped all seed / helper-index / view migrations. **Treat every
> bare `V<n>` here as HISTORICAL** — it does not identify the same migration today. To find the current
> migration for a table, run `ls src/main/resources/db/migration/ | grep <table>` or see
> [flyway-migration-baseline.md](../database/flyway-migration-baseline.md) §5.

**Slug:** `chart-of-accounts`
**Refactor home:** Spring Boot ([wecms-api/](../../../)) + Next.js Settings panel ([wecms-web/components/settings/chart-of-accounts/](../../../../wecms-web/components/settings/chart-of-accounts/)).
**Legacy WSVC ids (traceability only — never echoed in refactor identifiers):** `comm_0223_001` (main view + CRUD), `comm_0223_002` (Excel bulk upload).
**Related docs:** [feature spec](../../features/settings/chart-of-accounts.md) · [backend design](../business-rules/chart-of-accounts.md) · [frontend design](../../../../wecms-web/docs/features/settings/chart-of-accounts.md) · [database](../database/chart-of-accounts.md) · plan _(removed — historical `archive/` folder deleted by project decision)_.

The legacy implementation lives entirely under the WABOOKS `comm` namespace. Every artifact below is read-only reference for the refactor — never edit, never deploy, never wire two-way.

---

## 1. Service-by-service mapping

### 1.1 Web services (WSVC)

| JEX WSVC id | JEX action JSP | Refactor REST endpoint | Refactor handler |
|---|---|---|---|
| `comm_0223_001` (view) | [comm_0223_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0223_001_view.jsp) | n/a (Next.js panel, not a JSP view) | [`<ChartOfAccountsPanel>`](../../../../wecms-web/components/settings/chart-of-accounts/ChartOfAccountsPanel.tsx) |
| `comm_0223_001_r001` | [comm_0223_001_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_r001_act.jsp) | `GET /api/v1/settings/chart-of-accounts` (list) | `ChartOfAccountsController#list` → `ChartOfAccountsService#list` |
| `comm_0223_001_r002` | [comm_0223_001_r002_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_r002_act.jsp) (parent-picker tree) | `GET /api/v1/settings/chart-of-accounts/tree` (parent-picker) | `ChartOfAccountsController#tree` → `ChartOfAccountsService#tree` |
| `comm_0223_001_r003` | [comm_0223_001_r003_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_r003_act.jsp) (variant — unused by view) | **Dropped.** Variant carries no caller; refactor does not port unused branches. | — |
| `comm_0223_001_c001` | [comm_0223_001_c001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_c001_act.jsp) | `POST /api/v1/settings/chart-of-accounts` | `ChartOfAccountsService#create` |
| `comm_0223_001_u001` | [comm_0223_001_u001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_u001_act.jsp) | `PUT /api/v1/settings/chart-of-accounts/{seqNo}` | `ChartOfAccountsService#update` |
| `comm_0223_001_u002` | [comm_0223_001_u002_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_u002_act.jsp) (parent-only patch, used by Delete) | **Inlined.** Refactor folds the parent-null patch into `ChartOfAccountsService#delete` (transactional, single tx) — no separate endpoint. | — |
| `comm_0223_001_d001` | [comm_0223_001_d001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_d001_act.jsp) | `DELETE /api/v1/settings/chart-of-accounts/{seqNo}` | `ChartOfAccountsService#delete` (soft-delete, NOT the IDO `D001` hard delete — see §1.4) |
| `comm_0223_001_d002` | [comm_0223_001_d002_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_001_d002_act.jsp) (variant) | **Dropped.** No caller. | — |
| `comm_0223_002` (view) | [comm_0223_002_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/comm/comm_0223_002_view.jsp) (Excel upload modal) | n/a — modal owned by frontend via shared [`<ExcelUploadDialog>`](../../../../wecms-web/components/shared/excel-upload/ExcelUploadDialog.tsx) per [wecms-web/CLAUDE.md §3a](../../../../wecms-web/CLAUDE.md) | — |
| `comm_0223_002_c001` | [comm_0223_002_c001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_002_c001_act.jsp) (parse Excel + bulk insert) | `POST /api/v1/settings/chart-of-accounts/bulk-import` (multipart/form-data) | `ChartOfAccountsService#bulkImport` (Phase 2 — kept inert in Phase 1 per OQ-5) |
| `comm_0223_002_r001` | [comm_0223_002_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_002_r001_act.jsp) (upload history) | Deferred — depends on `ERP_TRNS_HSTR`-style audit ledger; not in scope for Phase 1. | — |
| `comm_0223_002_r002` | [comm_0223_002_r002_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/comm/comm_0223_002_r002_act.jsp) (history variant) | Deferred. | — |

Legacy WSVC ids appear above strictly for traceability — no refactor REST URI, package, class, DTO field, log tag, i18n key, or `AUDIT_LOGS_INFM.WSVC_ID` value carries them.

### 1.2 IDO mappings

| JEX IDO | Purpose | Refactor counterpart |
|---|---|---|
| [IDO.CHART_ACCT_INFM_R001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_R001.xml) | Default select-all over `CHART_ACCT_INFM` with dynamic WHERE | `ChartOfAccountRepository.findByBizKeyAndDelYnOrderByChartAcctCd(bizKey, "N")` + per-page `LIKE` filter via JPQL `@Query` |
| [IDO.CHART_ACCT_INFM_R001_CNT.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_R001_CNT.xml) | COUNT(*) for paging | Not needed — list is unpaged (load-all matches legacy) |
| [IDO.CHART_ACCT_INFM_R002.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_R002.xml) | Tree projection with computed `CHART_ACCT_LEVEL` (1..4), `HAS_CHILDREN`, `CHART_ACCT_DIPS_NAME` breadcrumb | `ChartOfAccountsService#tree` — service-side recursion in Java over the flat list; backend computes level + breadcrumb in code (no SQL `JOIN`-fest). |
| [IDO.CHART_ACCT_INFM_R003.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_R003.xml) | Unused variant | Dropped |
| [IDO.CHART_ACCT_INFM_R004.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_R004.xml) | Unused variant | Dropped |
| [IDO.CHART_ACCT_INFM_C001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_C001.xml) | `INSERT INTO CHART_ACCT_INFM (…)` all-columns | JPA `chartOfAccountRepo.save(entity)` — Hibernate emits INSERT; no native SQL |
| [IDO.CHART_ACCT_INFM_U000.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_U000.xml) | Partial patch (used by `d001` to soft-delete + by `u001` for child re-parent) | JPA dirty-checking inside `@Transactional` service; or `@Modifying @Query` for the bulk child re-parent in `delete` |
| [IDO.CHART_ACCT_INFM_U001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_U001.xml) | Full update | JPA dirty-checking on managed entity |
| [IDO.CHART_ACCT_INFM_U002.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_U002.xml) | "Set PARENT_CHART_ACCT_CD = NULL where PARENT_CHART_ACCT_CD = ?" — used by the `u001` cleanup branch (OQ-3) | **NOT ported.** OQ-3 recommends dropping the cleanup branch entirely. |
| [IDO.CHART_ACCT_INFM_U003.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_U003.xml) | Unused variant | Dropped |
| [IDO.CHART_ACCT_INFM_D001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CHART_ACCT_INFM_D001.xml) | Hard `DELETE FROM CHART_ACCT_INFM WHERE …` | **NOT ported as a hard delete.** Legacy `d001_act.jsp` does NOT call the `D001` IDO — it sets `DEL_YN='Y'` via the partial patch. Refactor mirrors this — soft delete only; the hard-delete IDO is dead code. |

### 1.3 Java entity / DAO

| JEX artifact | Refactor counterpart |
|---|---|
| [wabooks/entity/ChartAcctInfm.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ChartAcctInfm.java) (extends `ChartAcctInfmPk implements GenericEntity`) | [`com.kosign.wecms.domain.setting.chartofaccounts.entity.ChartOfAccount`](../../../src/main/java/com/kosign/wecms/domain/setting/chartofaccounts/entity/ChartAcctInfm.java) (to be created) extending `BaseEntity`, `@IdClass(ChartOfAccountId.class)`. Field names: camelCase Java + `@Column(name = "UPPER_SNAKE")`. |
| `ChartAcctInfmPk.java` (composite PK) | `ChartOfAccountId` — `Serializable` record with `bizKey: String`, `chartAcctSeqNo: Long` |
| [wabooks/dao/ChartAcctInfmDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/ChartAcctInfmDAO.java) (extends `GenericDAO`) | `ChartOfAccountRepository extends JpaRepository<ChartOfAccount, ChartOfAccountId>` |
| `wabooks.commons.IdoUtils.executeListWithTxn(…)` | `@Transactional` on service methods + Spring Data `@Modifying @Query` for the child re-parent statement |
| `WbMessageCode.WBE0001` / `WBE0003` / `WBE0404` / `WBE1178` | New refactor codes `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND`, `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE`, `WBE_CHART_OF_ACCOUNTS_NOT_FOUND`, `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` in [messages.properties](../../../src/main/resources/messages.properties) |
| `MessageHelper.getDictionary(util, "CHART_ACCT_CD")` | i18n via `MessageResolver` against `messages*.properties`; per [§13a-bis](../../../../wecms-web/CLAUDE.md) the wire ships keys not labels |

### 1.4 Frontend (TypeScript → Next.js)

| JEX TypeScript artifact | Refactor counterpart |
|---|---|
| [WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0223_001.ts](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0223_001.ts) — `buildHierarchy` / `trimHierarchyToMaxLevel` / `flattenHierarchy` / `getParentAccountsSimple` | [`components/settings/chart-of-accounts/ChartOfAccountsPanel.tsx`](../../../../wecms-web/components/settings/chart-of-accounts/ChartOfAccountsPanel.tsx) — already exists in static UI; refactor wires the real fetchers in `settings.ts` (Needs Confirmation — source file not found) (new `fetchChartOfAccounts*` family). Backend ships the computed tree shape so the frontend stops building it client-side. |
| [WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0223_002.ts](../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0223_002.ts) — Excel parse + bulk submit | [`components/settings/chart-of-accounts/ExcelUploadDialog.tsx`](../../../../wecms-web/components/settings/chart-of-accounts/ExcelUploadDialog.tsx) — already exists (canonical example for the shared Upload dialog); refactor wires `onUpload` to `POST /api/v1/settings/chart-of-accounts/bulk-import` in Phase 2. |
| jQuery DOM manipulation for inline create / edit row | React state in `ChartOfAccountsPanel` — `editingSeqNo: number | null` + `<EditRow>` mounted conditionally |

---

## 2. Field-level mapping (request / response)

Java DTO fields stay camelCase; wire is lowercase snake_case per global Jackson `SNAKE_CASE` (see [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"API Naming & Serialization Standards"). Legacy JEX wire used UPPER_SNAKE; **none of the legacy UPPER_SNAKE keys survive in the refactor wire** — full strip per the retire-legacy CTO directive.

### 2.1 List row — `GET /api/v1/settings/chart-of-accounts`

| Legacy wire (JEX) | Refactor wire | Java field | Source DB column |
|---|---|---|---|
| `BIZ_KEY` | — | (dropped — tenant resolved server-side) | `BIZ_KEY` |
| `CHART_ACCT_SEQ_NO` | `chart_acct_seq_no` | `chartAcctSeqNo: Long` | `CHART_ACCT_SEQ_NO` (VARCHAR(14), cast to `bigint`) |
| `CHART_ACCT_CD` | `chart_acct_cd` | `chartAcctCd: String` | `CHART_ACCT_CD` |
| `CHART_ACCT_NM_EN` | `chart_acct_nm_en` | `chartAcctNmEn: String` | `CHART_ACCT_NM_EN` |
| `CHART_ACCT_NM_VN` | `chart_acct_nm_vn` | `chartAcctNmVn: String?` | `CHART_ACCT_NM_VN` |
| `CHART_ACCT_NM_KO` | `chart_acct_nm_ko` | `chartAcctNmKo: String?` | `CHART_ACCT_NM_KO` |
| `CHART_ACCT_TYPE` | `chart_acct_type` | `chartAcctType: String?` | `CHART_ACCT_TYPE` (unused in DEV — all NULL; kept for forward compat) |
| `PARENT_CHART_ACCT_CD` | `parent_chart_acct_cd` | `parentChartAcctCd: String?` | `PARENT_CHART_ACCT_CD` |
| `CAL_RULE` | `cal_rule` | `calRule: CalRule?` (enum P / M) | `CAL_RULE` |
| `STTS` | `stts` | `stts: AcctStts` (enum A / I) | `STTS` |
| `DEL_YN` | — | (dropped — filtered server-side; never surfaced on the wire) | `DEL_YN` |
| `REG_ID` | `reg_id` | `regId: String` | `REG_ID` |
| `REG_DTM` | `reg_dtm` | `regDtm: OffsetDateTime` | `REG_DTM` (VARCHAR(14), parsed) |
| `MOD_ID` | `mod_id` | `modId: String` | `MOD_ID` |
| `MOD_DTM` | `mod_dtm` | `modDtm: OffsetDateTime` | `MOD_DTM` (VARCHAR(14), parsed) |
| — (computed in `comm_0223_001.ts`) | `chart_acct_level` | `chartAcctLevel: Integer` (1..4) | server-computed (Java service walks parents) |
| — (computed) | `has_children` | `hasChildren: Boolean` | server-computed |
| — (computed) | `restricted` | `restricted: Boolean` | `true` iff code ∈ `{ACC-01, ACC-02, ACC-03}` |

### 2.2 Create request — `POST /api/v1/settings/chart-of-accounts`

| Wire | Java | Constraint |
|---|---|---|
| `chart_acct_cd` | `chartAcctCd: String` | `@NotBlank @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9_\\-]+")` |
| `chart_acct_nm_en` | `chartAcctNmEn: String` | `@NotBlank @Size(max = 255)` |
| `chart_acct_nm_vn` | `chartAcctNmVn: String?` | `@Size(max = 255)` |
| `chart_acct_nm_ko` | `chartAcctNmKo: String?` | `@Size(max = 255)` |
| `chart_acct_type` | `chartAcctType: String?` | `@Size(max = 100)` |
| `parent_chart_acct_cd` | `parentChartAcctCd: String?` | `@Size(max = 20)` — `null` ⇒ root; otherwise must reference an active row in tenant |
| `cal_rule` | `calRule: CalRule?` | `@JsonDeserialize` via Jackson enum mapping (`"P"` ⇒ `CalRule.PLUS`, `"M"` ⇒ `CalRule.MINUS`) |
| `stts` | `stts: AcctStts` | `@NotNull` — `"A"` or `"I"` |

### 2.3 Update request — `PUT /api/v1/settings/chart-of-accounts/{seqNo}`

Same shape as Create, plus the path variable `{seqNo}` (the `CHART_ACCT_SEQ_NO`). Body MUST NOT echo `chart_acct_seq_no` — the path is authoritative. Body MUST NOT echo `biz_key` (tenant from JWT).

### 2.4 Tree response — `GET /api/v1/settings/chart-of-accounts/tree`

Server-computed projection used by the inline parent picker. One DTO per row, ordered breadth-first within siblings, sorted by `CHART_ACCT_CD ASC` per level (matches legacy `R002`'s `ORDER BY T.CHART_ACCT_CD ASC`).

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "nodes": [
      {
        "chart_acct_seq_no": 70,
        "chart_acct_cd": "ACC-01",
        "chart_acct_nm_en": "REVENUE",
        "chart_acct_level": 1,
        "parent_chart_acct_cd": null,
        "has_children": true,
        "breadcrumb": "ACC-01 - REVENUE"
      },
      {
        "chart_acct_seq_no": 99,
        "chart_acct_cd": "1007009",
        "chart_acct_nm_en": "Collection 4",
        "chart_acct_level": 2,
        "parent_chart_acct_cd": "ACC-01",
        "has_children": false,
        "breadcrumb": "ACC-01 - REVENUE / 1007009 - Collection 4"
      }
    ]
  }
}
```

`breadcrumb` mirrors the legacy `CHART_ACCT_DIPS_NAME` format (`"<parentCd> - <parentNmEn> / <currentCd> - <currentNmEn>"`).

---

## 3. Error code mapping

| Legacy `WbMessageCode` | Legacy English message | Refactor code | Refactor English message | Throwing call sites |
|---|---|---|---|---|
| `WBE0001` | "Not found." (with arg) | `WBE_CHART_OF_ACCOUNTS_PARENT_NOT_FOUND` | "Parent account not found." | Create / Update / Delete parent-resolve |
| `WBE0003` | "Already exists." (with arg) | `WBE_CHART_OF_ACCOUNTS_DUPLICATE_CODE` | "Account code is already in use." | Create dup-check / Update dup-check |
| `WBE0404` | "Record not found." | `WBE_CHART_OF_ACCOUNTS_NOT_FOUND` | "Chart of accounts entry not found." | Update / Delete lookup |
| `WBE1178` | (Korean / restricted-code message) | `WBE_CHART_OF_ACCOUNTS_RESTRICTED_CODE` | "This account is system-managed and cannot be edited or deleted." | Update / Delete restricted-code guard |
| n/a | — | `WBE_CHART_OF_ACCOUNTS_CYCLE` | "Selected parent would create a cycle in the hierarchy." | Update parent-cycle check (refactor addition, OQ-2) |
| n/a | — | `WBE_CHART_OF_ACCOUNTS_DEPTH_EXCEEDED` | "Account hierarchy cannot exceed 4 levels." | Create / Update depth-cap check (refactor addition, OQ-2) |
| n/a | — | `WBE_CHART_OF_ACCOUNTS_SELF_PARENT` | "An account cannot be its own parent." | Create / Update self-parent guard (refactor addition, OQ-2) |

Add the new codes to [messages.properties](../../../src/main/resources/messages.properties) + `messages_en.properties` in the same MR as the controller.

---

## 4. Identifier surfaces — slug-only

Per [wecms-api/CLAUDE.md](../../../CLAUDE.md) §"Refactor identifier naming — slugs, not WSVC ids" and [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md) §"Routing", every identifier surface uses the kebab-case slug `chart-of-accounts` — never `comm_0223_001`.

| Surface | Value |
|---|---|
| REST URI base | `/api/v1/settings/chart-of-accounts` |
| Java package | `com.kosign.wecms.domain.setting.chartofaccounts` |
| Java classes | `ChartOfAccountsController`, `ChartOfAccountsService`, `ChartOfAccount` (entity), `ChartOfAccountId`, `ChartOfAccountRepository`, `ChartOfAccountRow`, `ChartOfAccountCreateRequest`, `ChartOfAccountUpdateRequest`, `ChartOfAccountTreeNode`, `ChartOfAccountsSortField` (enum), `CalRule` (enum, shared with VAS Account if applicable), `AcctStts` (enum — reuse existing if it exists) |
| Next.js panel | [`components/settings/chart-of-accounts/ChartOfAccountsPanel.tsx`](../../../../wecms-web/components/settings/chart-of-accounts/ChartOfAccountsPanel.tsx) |
| i18n keys | `settings.chart_of_accounts.*` |
| Log / MDC tags | `chart-of-accounts` |
| `AUDIT_LOGS_INFM.WSVC_ID` (if audit ships in Phase 1 — OQ pending) | `chart-of-accounts` (slug, not `comm_0223_001`) |
| View Setting `WEB_SVC_ID` (if adopted — OQ-6 default: NOT adopted) | `chart-of-accounts` — reserved in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts) for slug consistency even when the popover isn't rendered (same precedent as `approval-line`) |

Legacy WSVC ids (`comm_0223_001`, `comm_0223_002`) appear nowhere except this document and Javadoc citations like `// Mirrors comm_0223_001_c001_act.jsp:154-193`.

---

## 5. Behavioural deltas (refactor adds / drops)

| # | Behaviour | Direction | Reason |
|---|---|---|---|
| Δ1 | Cycle detection on parent change | **Added** | Legacy hole; DEV data shows `seq 104` self-references. OQ-2. |
| Δ2 | Depth-cap enforcement at write time (not just read time) | **Added** | Legacy bakes the cap into the read query only; Update can produce depth > 4. OQ-2. |
| Δ3 | Self-parent rejection | **Added** | Legacy allows it; refactor rejects with `WBE_CHART_OF_ACCOUNTS_SELF_PARENT`. OQ-2. |
| Δ4 | "Parent cleanup" branch in `u001_act.jsp:237-256` | **Dropped** | Legacy code silently nullifies an unrelated row's parent; looks like leftover instrumentation. OQ-3. |
| Δ5 | Hard-delete IDO `D001` | **Dropped** | Legacy `d001_act.jsp` never calls it — every delete is soft. Refactor does not expose a hard delete. |
| Δ6 | `comm_0223_001_u002` (parent-null patch) as separate endpoint | **Folded** | Always called from inside `d001`; refactor inlines it in `ChartOfAccountsService#delete` under one transaction. |
| Δ7 | Server-side hierarchy computation (`chart_acct_level`, `has_children`, `breadcrumb`) | **Moved server-side** | Legacy computes in TS (`buildHierarchy` / `flattenHierarchy`); refactor moves to Java so the wire delivers ready-rendered trees and the frontend stays thin. |
| Δ8 | `chart_acct_seq_no` typed as `Long` end-to-end | **Tightened** | Legacy stores `VARCHAR(14)` and parses ad-hoc; refactor entity types `Long`, JPA converter casts at the column boundary. |
| Δ9 | Audit-log write (`AUDIT_LOGS_INFM` row per CRUD op) | **Added (conditional)** | Legacy `comm_0223_*` writes NO audit rows. Refactor adds them by default for parity with `firm-banking-settings` / `contacts` audit precedent; toggle via OQ in [backend doc](../business-rules/chart-of-accounts.md). |
| Δ10 | `wb_get_next_val('chart_acct_infm','chart_acct_seq_no',...)` branch | **Ported** (V?? new migration) | Legacy uses the per-tenant MAX+1 pattern — same shape as V91 `appr_line_no`. Branch added in the schema migration; retire when a Java-side per-tenant generator replaces it. |
| Δ11 | Multilingual restricted-code labels (REVENUE / EXPENSES / INVESTMENT) | **i18n-keyed on wire** | Legacy ships EN literals; refactor follows [§13a-bis](../../../../wecms-web/CLAUDE.md) and ships an `i18n_key` sibling for each restricted row. |

---

## 6. Out-of-scope (defer to later sprints)

- **`fnsh_0200_001` Smart Organizer (VND) / `fnsh_0200_002` Organizer Setting** — the auto-categorizing transaction view (`WSVC.fnsh_0200_001.xml`: "Transactions History >> Smart Organizer") that surfaces each transaction's `CHART_ACCT_CD` as a "Reporting Account" column by joining `ACCT_TRNS_HSTR` ↔ `RMRK_ORG_INFM` organizer rules ↔ chart-account names. Read-side consumer of `CHART_ACCT_INFM`; ports with the Banking transaction module, not Reports. (No Income Statement / Trial Balance screen exists in JEX.)
- **`ACCT_TRNS_HSTR.CHART_ACCT_CD` FK / write-side enforcement** — ports with the Banking transaction module.
- **`CARD_TRNS_HSTR` join** — ports with the Card module (not yet on the refactor roadmap).
- **Upload-history audit ledger** (`comm_0223_002_r001` / `_r002`) — ports with the broader `ERP_TRNS_HSTR`-style audit feature.

These are tracked in docs/refactor-wecms/archive/planning/phase-1/chart-of-accounts-plan.md _(removed — historical `archive/` folder deleted by project decision)_ §"Out of scope".
