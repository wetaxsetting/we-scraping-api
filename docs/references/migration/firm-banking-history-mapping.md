# Settings > Firm Banking History — JEX → Spring Boot Migration Mapping

**Refactor screen ID / slug:** `firm-banking-history`
**Status:** Phase 1 (design only). No code in this phase.
**Feature spec:** [../features/settings/firm-banking-history.md](../../features/settings/firm-banking-history.md)
**DB design:** [../database/settings/firm-banking-history-schema.md](../database/settings/firm-banking-history-schema.md)
**Parity checklist:** [../audits/firm-banking-history-parity-checklist.md](../audits/firm-banking-history-parity-checklist.md)

JEX is **read-only reference** ([../../jex/](../../../../docs/jex/)). This file traces every JEX artefact to its refactor counterpart.

---

## 1. Service mapping table

| JEX WSVC ID | JEX `_act.jsp` | Spring Endpoint | Method | Frontend Route | Domain | Status | Notes |
|---|---|---|---|---|---|---|---|
| `comm_0221_001_r001` | `comm_0221_001_r001_act.jsp` | `/api/v1/settings/firm-banking-history` | GET | `/settings/firm-banking-history` | `setting` (or `setting/firmbankinghistory` sub-package) | **Planned** | Read-only audit list. Five-leg UNION ported as a Postgres view |
| `comm_0221_001` (View JSP — page shell) | `comm_0221_001_view.jsp` | (no API — page is a Next.js route) | — | `/settings/firm-banking-history` | `setting` | **Planned** | Static UI mirrors the JEX layout but reuses approved Account Inquiry baseline |

**Add a row for `comm_0221_001_r001`** to [./service-mapping.md](service-mapping.md) when this design is approved.

---

## 2. WSVC `<in>` → Spring controller `@RequestParam` mapping

Source: `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0221_001_r001.xml:32-39`.

| JEX WSVC field (UPPER_SNAKE) | JEX length | Java field (camelCase, in `FirmBankingHistoryQuery` record) | Wire param (snake_case) | Validation | Source |
|---|---|---|---|---|---|
| `FROM_DT` | 8 | `fromDate` | `from_date` | `@NotBlank @Pattern("\\d{8}")` | `comm_0221_001_r001_act.jsp:30,33` |
| `TO_DT` | 8 | `toDate` | `to_date` | `@NotBlank @Pattern("\\d{8}")` | `comm_0221_001_r001_act.jsp:31,34` |
| `SRCH_WD` | 100 | `keyword` | `keyword` | `@Size(max=100)` | `comm_0221_001_r001_act.jsp:52-55` |
| `SVC_TYPE` | 1 | `svcType` | `svc_type` | optional; `@Pattern("[FSGB]")` (refactor enhancement) | `comm_0221_001_r001_act.jsp:57-60` |
| `PAGE_NO` | 15 | `page` (zero-based — refactor convention) | `page` | `@Min(0)` | `comm_0221_001_r001_act.jsp:113-115` |
| `PAGE_PER_CNT` | 15 | `size` | `size` | `@Min(1) @Max(100)` | `comm_0221_001_r001_act.jsp:113-115` |
| (legacy `paramSort` query-string suffix appended client-side) | — | `sortField`, `sortDirection` | `sort_field`, `sort_direction` | whitelist | `comm_0221_001_r001_act.jsp:117` (`SortHelper.getSortDynamic`) |

**Refactor convention shifts (intentional, not parity gaps):**

- `PAGE_NO` 1-based → `page` 0-based (Spring Data `Pageable` standard).
- Sort moves from a free-form `paramSort` URL suffix to two explicit `sort_field` + `sort_direction` params with a whitelist. JEX's `SortHelper` accepts any client-supplied string; the refactor enforces the whitelist server-side.
- `SVC_TYPE` is enum-validated server-side; legacy was free-form 1-char.

---

## 3. WSVC `<out>` → Spring DTO mapping

Source: `WECMS DEV/WECMS_ETC/xml/service/WSVC/WSVC.comm_0221_001_r001.xml:40-57`.

### 3.1 Top-level response

| JEX WSVC field | Java field (in `FirmBankingHistoryListResponse` record) | Wire field (snake_case) | Notes |
|---|---|---|---|
| `TOT_CNT` | `totalCount` | `total_count` | from `Page#getTotalElements()` |
| `REC` (RECORD) | `list` | `list` | renamed from legacy `REC` per refactor naming directive (CTO 2026-05-08, memory `project_api_naming_lowercase`) |
| (new) | `totalPages` | `total_pages` | refactor adds — required by `<Pagination>` component |
| (new) | `pageNumber` | `page_number` | zero-based |
| (new) | `pageSize` | `page_size` | echoed back |

### 3.2 `REC` row — field-by-field mapping

Source: `WSVC.comm_0221_001_r001.xml:46-55`.

```
JEX WSVC field (UPPER_SNAKE)  →  Java property (camelCase)  →  JSON wire name (snake_case)
TARN_DTM                      →  tarnDtm                    →  tarn_dtm
SVC_TYPE                      →  svcType                    →  svc_type
(refactor-only, Phase 3d.2)   →  svcTypeKey                 →  svc_type_key
SVC_NM                        →  svcNm                      →  svc_nm
(refactor-only, Phase 3d)     →  svcNameKey                 →  svc_name_key
SEQ_NO                        →  seqNo                      →  seq_no
DEP_BANK_CD                   →  depBankCd                  →  dep_bank_cd
RSLT_CD                       →  rsltCd                     →  rslt_cd
RSLT_MSG                      →  rsltMsg                    →  rslt_msg
JOB                           →  job                        →  job
REQ_DATA                      →  reqData                    →  req_data
VAN_ORG_CD                    →  vanOrgCd                   →  van_org_cd
```

**`svc_type_key` (refactor-only — Phase 3d.2).** Server-resolved frontend i18n key for the leg discriminator, derived from `svc_type` via the new Spring [`FirmBankingSvcType`](../../../src/main/java/com/kosign/wecms/common/enums/FirmBankingSvcType.java) enum (4 constants: `F`/`S`/`G`/`B` → `settings.firm_banking_history.service_type.{firm_banking_transaction,scraping_transfer,vietnam_gdt_inquiry,vietnam_business_inquiry}`). Computed in `FirmBankingHistoryService.toRow` via `FirmBankingSvcType.resolveKey(svcType)` and shipped on every row alongside the raw `svc_type` letter. The four target i18n keys are pre-registered in `locales/{en,ko,vi}.json` (the existing keys the static UI already consumed). Wire value is `null` only when `svc_type` is not one of the four known letters — frontend then renders the raw character (never blank). Replaces the legacy frontend-side `coerceFirmBankingServiceType` switch in `mappers.ts`, lifting domain-code meaning back to the backend (the same architectural move that produced `svc_name_key`). **No JPA mapping** — the enum is not persisted under any `@Enumerated` annotation; it's a pure code → key resolver.

**`svc_name_key` (refactor-only — Phase 3d).** Server-resolved frontend i18n key under the `fb_history.service.*` namespace. Computed in `FirmBankingHistoryService.toRow` via `FirmBankingServiceNameResolver.resolve(svcType, svcNm)`. Source of truth for the F-leg's value set is the new Spring [`TranTp`](../../../src/main/java/com/kosign/wecms/common/enums/TranTp.java) enum (mirrors legacy [`wabooks.enums.TranTp`](../../../../WECMS%20DEV/WECMS/src/wabooks/enums/TranTp.java)); S/G/B legs map against the V60 view's CASE-WHEN literals. Wire value is `null` when the resolver doesn't recognise the `(svc_type, svc_nm)` pair — frontend falls back to raw `svc_nm` (legacy parity with `SERVICE_NAME_MAP` missing-key behaviour at `comm_0221_001.ts:646`). The legacy frontend's `SERVICE_NAME_MAP` JS object is **retired** by this field.

**JPA / DB rule for the F-leg `TRAN_TP` column.** Stays mapped as `String` (the V60 view exposes it through `SVC_NM`). `TranTp` is **not** a JPA `@Enumerated` mapping — workspace rule "Do not use `EnumType.ORDINAL`" (and the column carries legacy strings `"201"`/`"202"`/etc. directly). DB values are preserved verbatim.

No `@JsonProperty` annotations needed — global Jackson `SNAKE_CASE` strategy handles it ([wecms-api/CLAUDE.md § Java fields → wire mapping](../../../CLAUDE.md#java-fields--wire-mapping)).

### 3.3 Frontend in-memory shape

Per the [three naming layers rule](../../../../wecms-web/CLAUDE.md#api-naming--serialization-standards):

- **Wire type** in `lib/types/api.ts` → `ApiFirmBankingHistoryRow` with `snake_case` property names.
- **In-memory type** in `components/settings/firm-banking-history/types.ts` → `FirmBankingHistoryRow` with **camelCase** property names: `dateTime`, `serviceType`, `serviceName`, `seqNo`, `depositBankCd`, `resultCode`, `resultMessage`, `job`, `reqData`, `vanOrgCd`.
- **Mapper** in `lib/api/mappers.ts` → `mapFirmBankingHistoryRow(api: ApiFirmBankingHistoryRow): FirmBankingHistoryRow`.

**No component reads snake_case directly.** If a hook or render function is tempted to dereference `row.tarn_dtm` — extend the mapper first.

---

## 4. DAO → Spring Data repository mapping

JEX side reads via the action JSP's inline IDO call. There is no JEX `XxxDAO` for this screen — `comm_0221_001_r001_act.jsp` directly invokes `IdoUtils.executeListWithTxn` with `IDODynamic` parameters and the `TB_FIRM_TRAN_R002` IDO ID:

```java
// comm_0221_001_r001_act.jsp:66-77 (paraphrased)
JexData idoIn = util.createIDOData("TB_FIRM_TRAN_R002");
idoIn.put("BIZ_KEY", loginUser.getBizKey()); // x4 — once per UNION leg
idoIn.put("DYNAMIC_0", dynamic0);            // composed in getDynamic()
JexUtils.setIDOPageInfo(idoIn, pageInfo);
return IdoUtils.executeListWithTxn(util, idoCon, idoIn);
```

Refactor counterpart:

```java
// FirmBankingHistoryRepository.java (Phase 2)
public interface FirmBankingHistoryRepository extends JpaRepository<FirmBankingHistoryView, FirmBankingHistoryViewId> {

    // Spring Data derived method — covers the trivial case
    Page<FirmBankingHistoryView> findByBizKeyAndTarnDtmBetween(
        String bizKey, String tarnDtmFromInclusive, String tarnDtmToExclusive, Pageable pageable);

    // SVC_TYPE filter — derived
    Page<FirmBankingHistoryView> findByBizKeyAndSvcTypeAndTarnDtmBetween(
        String bizKey, String svcType, String fromInclusive, String toExclusive, Pageable pageable);

    // Keyword on SEQ_NO — JPQL + ILIKE (Postgres)
    @Query("""
        SELECT v FROM FirmBankingHistoryView v
        WHERE v.bizKey = :bizKey
          AND v.tarnDtm BETWEEN :fromDtm AND :toDtm
          AND (:svcType IS NULL OR v.svcType = :svcType)
          AND (:keyword IS NULL OR LOWER(v.seqNo) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!')
        """)
    Page<FirmBankingHistoryView> search(
        @Param("bizKey") String bizKey,
        @Param("fromDtm") String fromInclusive,
        @Param("toDtm")   String toExclusive,
        @Param("svcType") String svcType,
        @Param("keyword") String keyword,
        Pageable pageable);
}
```

### 4.1 IDO `<in>` → repository params

| JEX IDO `<in>` parameter | JEX `_act.jsp` source | Refactor parameter |
|---|---|---|
| `BIZ_KEY` (x5 occurrences in `IDO.TB_FIRM_TRAN_R002.xml:131-135`) | `loginUser.getBizKey()` (x4) + dynamic predicate (x1 — `T.biz_key = ?`) | `TenantContext.currentBizKey()` — read by service, passed once to repository (the view body internally constrains all five legs by the same value at the parameter binding) |
| `DYNAMIC_0` | `getDynamic(loginUser, input)` builds `and T.biz_key = ? and left(T.TARN_DTM,8) between ? and ?` plus optional `T.SEQ_NO ilike '%'||?||'%'` and `T.SVC_TYPE = ?` | Spring Data method params (see above) |

The refactor **does not need** the five `BIZ_KEY` placeholders the legacy IDO emits — those exist because the legacy IDO XML expands the parameter into each UNION leg's `WHERE`. In the refactor, the view definition contains `WHERE BIZ_KEY = (current_setting('app.biz_key'))` is **rejected** (request-scoped session settings are fragile in pooled connections); instead, the outer SELECT against the view applies `WHERE BIZ_KEY = :bizKey` — Postgres pushes the predicate into each leg's plan via predicate pushdown over the `UNION ALL`. EXPLAIN-verify in Phase 2.

### 4.2 IDO `<out>` → entity fields

Source: `IDO.TB_FIRM_TRAN_R002.xml:139-150`. The view's column list mirrors this 1:1 — see [§ Database — V_FIRM_BANKING_HISTORY](../database/settings/firm-banking-history-schema.md).

---

## 5. IDO → DTO field-by-field mapping

The refactor consolidates IDO `TB_FIRM_TRAN_R002` (the multi-leg UNION) into one Postgres view (`V_FIRM_BANKING_HISTORY`) backed by one immutable JPA entity (`FirmBankingHistoryView`). The IDO column list, the view column list, and the entity field list are 1:1.

| IDO output (`TB_FIRM_TRAN_R002`) | View column | Entity field (camelCase) | DTO field | Wire | Source legs |
|---|---|---|---|---|---|
| `TARN_DTM` | `TARN_DTM` VARCHAR(14) | `tarnDtm` | `tarnDtm` | `tarn_dtm` | All |
| `SVC_TYPE` | `SVC_TYPE` CHAR(1) | `svcType` | `svcType` | `svc_type` | All — literal `'F'`/`'S'`/`'G'`/`'B'` per leg |
| `SVC_NM` | `SVC_NM` VARCHAR(50) | `svcNm` | `svcNm` | `svc_nm` | All — see [feature spec § 6.2](../../features/settings/firm-banking-history.md#62-read-only-view-v_firm_banking_history) |
| `SEQ_NO` | `SEQ_NO` VARCHAR(50) | `seqNo` | `seqNo` | `seq_no` | All |
| `DEP_BANK_CD` | `DEP_BANK_CD` VARCHAR(200) | `depBankCd` | `depBankCd` | `dep_bank_cd` | F + S(napas) compose `wb_get_*_bank_nm + ' - ' + code`; G(nxisas) uses `NXISAS_MODULE`; B(nxisas) similarly |
| `RSLT_CD` | `RSLT_CD` VARCHAR(20) | `rsltCd` | `rsltCd` | `rslt_cd` | All |
| `RSLT_MSG` | `RSLT_MSG` VARCHAR(2000) | `rsltMsg` | `rsltMsg` | `rslt_msg` | All — F leg's `RSLT_MSG` is just `RS_CD` (frontend resolves via `DICTIONARY_VAN`); others carry message text |
| `JOB` | `JOB` VARCHAR(5000) | `job` | `job` | `job` | F: `TRAN_TP`; S(napas): `'TRANSFER'`; S(ext_tran): `BALANCE`/`TRANSACTION`/`CARD_USING` by `SVC_ID`; G/B: `NXISAS_JOB` |
| `REQ_DATA` | `REQ_DATA` VARCHAR(1000) | `reqData` | `reqData` | `req_data` | Only G/B legs populate; others NULL |
| `BIZ_KEY` | `BIZ_KEY` VARCHAR(20) | `bizKey` | (not exposed) | — | Used for tenant filtering; not surfaced on wire |
| `VAN_ORG_CD` | `VAN_ORG_CD` VARCHAR(20) | `vanOrgCd` | `vanOrgCd` | `van_org_cd` | F leg only; NULL for others |

Total wire fields exposed: 10 (excludes `BIZ_KEY`). Matches legacy WSVC `subRule` `<rule_Sub_0>` exactly.

---

## 6. Frontend asset → Next.js asset mapping

| JEX asset | Path | Refactor counterpart | Status |
|---|---|---|---|
| `comm_0221_001_view.jsp` | `WECMS DEV/WECMS/web/view/jex/wabooks/comm/comm_0221_001_view.jsp` | `app/(dashboard)/settings/firm-banking-history/page.tsx` + `<FirmBankingHistoryPage>` | Planned |
| `comm_0221_001.ts` (1100 LOC) | `WECMS DEV/WECMS_STATIC/ts-src/js/jex/wabooks/comm/comm_0221_001.ts` | Decomposed into: `FirmBankingHistoryClient` (state) + `FirmBankingHistoryTable` (render) + `ServiceTypeFilter` + `ResultBadge` + `DetailsPopover` + `mapFirmBankingHistoryRow` | Planned |
| ML codes `comm_0221_001_m001..m018` (JSP) | (Jex Studio Common View) | i18n keys under `fb_history.*` in `locales/{en,ko,vi,km}.json` | Planned |
| ML codes `comm_0221_001_j001..j046` (TS) | (Jex Studio Common View) | i18n keys under `fb_history.label.*`, `fb_history.result.*`, `fb_history.serviceName.*` | Planned |
| `_osmc.ui.ml.get(...)` lookup | `comm_0221_001.ts:55-100` | `useTranslation()` from `LocaleProvider` | reuse — already wired |
| `jexjs.getCode("DICTIONARY_VAN", code)` | `comm_0221_001.ts:440` | TBD — DICTIONARY_VAN dictionary port (open question 2 in feature spec) | **gating** for SVC_TYPE='F' result-message localization |
| `_v2common.app.showMessage(data)` | system error/success dialog | `useDialog().showError(...)` | reuse |
| `tmsComm.createProgressBar()` / `destroyProgressBar()` | progress overlay | global loading-state from the existing pattern (Account Inquiry uses it) | reuse |
| jQuery dropdown popover for "View Details" | `comm_0221_001.ts:156-201` | shadcn `<Dialog>` modal — **not** a port of the inline dropdown | replace |
| `comm_0106_001_view.jsp` (range date sub-include) + `loadinitializeDate(-30, todayYear)` | range date helper | `<DateRangePicker>` global component (currently UI-only — see [feature spec § 9.3](../../features/settings/firm-banking-history.md#93-frontend-reuse--already-shipped-in-wecms-web) open issue #1) | partial reuse |

---

## 7. Common / framework reuse map (cross-feature)

This page does not introduce a new shared component or new framework code beyond what the existing four Sprint 0/1/2 pages already shipped, **except**:

| New shared piece | Why | Owner | Phase |
|---|---|---|---|
| Postgres function `kr_to_vn_time(varchar)` | First refactor feature using KR↔VN time conversion. Port verbatim from `wecmsvndev.kr_to_vn_time`. | Arthur | Phase 2 |
| Postgres function `wb_get_napas_bank_nm` (1-arg + 3-arg overloads) | First refactor feature using NAPAS-specific bank-name lookup. | Arthur | Phase 2 |
| `V_FIRM_BANKING_HISTORY` view | First refactor view of this style. Sets the precedent for future audit/log consolidation pages. | Arthur | Phase 2 |
| Real `<DateRangePicker>` (calendar popover + range model) | Currently UI-only globally; this page needs it; future pages will too | Arthur | gating — see open question #1 |
| `DICTIONARY_VAN` dictionary port | Required for SVC_TYPE='F' result-message localization. Port the legacy dictionary table + lookup helper. | Sonny | gating — open question #2 |
| i18n bundle `fb_history.result.*` (16 keys) | One-off this page; could be reused by future diagnostic pages | Arthur | Phase 2 |

Everything else — auth filter chain, tenant context, ApiResponse envelope, error handling, Jackson naming strategy, View Setting popover, page chrome, table primitive, pagination, system dialog, Axios layer — is **reused without change**.

---

## 8. Cross-references

- Feature spec: [../features/settings/firm-banking-history.md](../../features/settings/firm-banking-history.md)
- DB design: [../database/settings/firm-banking-history-schema.md](../database/settings/firm-banking-history-schema.md)
- Parity audit checklist: [../audits/firm-banking-history-parity-checklist.md](../audits/firm-banking-history-parity-checklist.md)
- Cross-feature service mapping: [./service-mapping.md](service-mapping.md)
- API mapping (cross-feature): [../backend/api-mapping.md](../api-spec/api-mapping.md)
- Page mapping (cross-feature): [../frontend/page-mapping.md](../../../../wecms-web/docs/references/page-mapping/page-mapping.md)
- DB mapping (cross-feature): [../database/db-mapping.md](../database/table-mapping/db-mapping.md)
- API naming directive: [../../../wecms-api/CLAUDE.md § API Naming & Serialization Standards](../../../CLAUDE.md#api-naming--serialization-standards) and memory `project_api_naming_lowercase`
- View Setting feature: [../features/view-setting.md](../../../../docs/jex/features/view-setting.md) when written; meanwhile see [../../jex/features/view-setting.md](../../../../docs/jex/features/view-setting.md)
