# Card Transactions — JEX → Spring Migration Mapping

Companion to [docs/refactor-wecms/features/card-transactions.md](../../features/payment/card-transactions.md). Refactor slug: **`card-transactions`**. Legacy JEX WSVC family: `card_0001_001*` (Payments module — `card/` directory, not `pymn/`).

This document is **migration-traceability prose** per the workspace identifier-naming rule. Legacy WSVC ids appear here as historical pointers; every new identifier the refactor introduces uses the kebab-case slug.

---

## 1 · WSVC operation → Spring REST endpoint

| Legacy JEX WSVC | Legacy purpose | Refactor REST endpoint | Refactor controller method | Notes |
|---|---|---|---|---|
| `card_0001_001` (view) | JSP page mount ([card_0001_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp)) | No backend endpoint — page is `app/(dashboard)/payment/card-transactions/page.tsx` | — | Server component shell |
| `card_0001_001_r001` ([_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp)) | Paged list + totals | `GET /api/v1/payment/card-transactions` | `CardTransactionsController#list` | Wire envelope drops the legacy `COMMON_HEAD` / `REC` shape and uses `ApiResponse<CardTransactionListResponse>` |
| `card_0001_001_r002` ([_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r002_act.jsp)) | Distinct active cards (filter dropdown) | `GET /api/v1/payment/card-transactions/cards` | `CardTransactionsController#cards` | Optional `?bank_cd=` query param mirrors legacy `BANK_CD` input |
| `card_0001_001_u001` ([_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_u001_act.jsp)) | Single-field patch (`CONT` or `CARD_TRNS_USAGE_CODE`) | `PATCH /api/v1/payment/card-transactions/{cardSeqNo}` | `CardTransactionsController#patch` | Legacy `FIELD_ID`/`FIELD_VAL` pair lifts to a discriminated request body — see §5 |
| `card_0001_001_u002` ([_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_u002_act.jsp)) | Update reporting account (`CHART_ACCT_CD` + `CHART_ACCT_SEQ_NO`) | `PUT /api/v1/payment/card-transactions/{cardSeqNo}/reporting-account` | `CardTransactionsController#updateReportingAccount` | Full-replace of the two fields; partial PATCH is intentionally NOT split out (always-paired write) |
| _(refactor addition — no legacy twin)_ | Excel export | `POST /api/v1/payment/card-transactions/export` | `CardTransactionsController#export` | Shared `ExcelExportService` + `CardTransactionsExportProvider` |

Filter chain mapping: legacy `MRECGateway` → `JexFilter` → `_act.jsp` collapses to **Spring Security JWT filter → `JwtAuthenticationFilter` → `CardTransactionsController`** in one hop. The `JexConnection` / `IDODynamic` / `JexData` triad is replaced by Spring Data JPA + native `@Query` (only for the `CARD_MSTR_R002` ledger join; see §3).

## 2 · DAO method → Spring Data repository method

The refactor splits the legacy `CardTrnsHstrDAO` ([CardTrnsHstrDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java)) into one repository per table.

| Legacy method (DAO) | Refactor repository method | Notes |
|---|---|---|
| `CardTrnsHstrDAO.findByPk(util, idoCon, pk)` ([line 105](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L105)) | `CardTrnsHstrRepository.findByBizKeyAndCardSeqNo(bizKey, cardSeqNo)` | Composite PK on `(BIZ_KEY, CARD_SEQ_NO)` — JPA `@IdClass(CardTrnsHstrId.class)` |
| `CardTrnsHstrDAO.findByDynamic(util, idoCon, dynamic)` ([line 111](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L111)) | (collapsed) — list endpoint uses the dedicated `findLedgerPage` native query (see §3) | Legacy "dynamic where + IDO" is gone; the refactor exposes only the typed list path |
| `CardTrnsHstrDAO.patch(util, idoCon, entity)` ([line 80](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L80)) | `CardTrnsHstrRepository.patchDescription(bizKey, cardSeqNo, cont, modId, modDtm)` `@Modifying @Query` **and** `patchPurpose(...)` **and** `patchReportingAccount(...)` | Three named writers replace the dynamic `fieldsToUpdateMap` map-builder pattern. Clearer audit, narrower SQL, no field-name validation in the service. |
| `CardTrnsHstrDAO.create(util, idoCon, entity)` ([line 33](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L33)) | (out of scope) — scraping stays on the JEX side. `CARD_SEQ_NO` generation via `SeqHelper.getNextVal` would land here if the refactor ever scraped, but it does not. | No new `wb_get_next_val` branch needed in Flyway. |
| `CardTrnsHstrDAO.update(util, idoCon, entity)` ([line 128](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L128)) | (out of scope) — same reason as `create` | — |
| `CardTrnsHstrDAO.deleteByPk(util, idoCon, pk)` ([line 71](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L71)) | (out of scope) — page has no delete affordance | — |
| `CardTrnsHstrDAO.upsert(...)` ([line 145](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L145)) | (out of scope) — same reason | — |

The `CardMstrDAO` is consumed only as a read for the dropdown — no patch / create / delete on the page. `CardTrnsRcvPayInfmDAO` is **not** touched by `card_0001_001*` — confirmed by reading the four `_act.jsp` files (no import of `CardTrnsRcvPayInfmDAO`).

## 3 · IDO SQL → JPA / native query

The legacy IDO XMLs are **read-only Jex Studio artefacts**. The refactor never edits them; it copies the SQL essence into Spring repositories. Native queries are used where the SQL leans on (a) tenant-scoped JOINs Hibernate's derived-method DSL can't express compactly, (b) sub-selects with `JSON_AGG`, or (c) legacy DB functions we deliberately keep (see §3.4).

### 3.1 `IDO.CARD_MSTR_R002.xml` → `CardTrnsHstrRepository.findLedgerPage(...)`

Source: [IDO.CARD_MSTR_R002.xml:24-160](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CARD_MSTR_R002.xml#L24-L160) — driver query for the list view. **Stays native** because the SQL:

- `LEFT JOIN CARD_MSTR` on the composite key `(BIZ_KEY, BANK_CD, CARD_NO)`
- Five correlated `JSON_AGG` sub-selects for linked docs (`BUSA_DOC_RCV_PAY_HIS` + `BUSA_DOC_MSTR`), approver list (`EXPT_RESOL_APPRVR_INFM`), approval doc (`EXPT_RESOL_MSTR.DOC_NO`), `APPR_DV`, and the bulletin-post pointer
- Reporting-account name resolved via `wb_get_chart_acct_nm` + `wb_get_chart_acct_lv_nm` (see §3.4)
- Status text resolved via `wb_get_prch_stts_nm` (§3.4)
- Purpose name resolved via `wb_get_usag_nm_new` (§3.4)
- Memo/colour resolved via `wb_get_memo_cont` / `wb_get_memo_color` (§3.4)

Refactor approach — keep the **shape** of the query but parametrise the dynamic WHERE in Java rather than via the JEX `IDODynamic`:

```java
@Query(value = """
  SELECT … cleaned native SQL …
    FROM CARD_TRNS_HSTR cth
    LEFT JOIN CARD_MSTR cm
      ON cth.biz_key = cm.biz_key
     AND cth.bank_cd = cm.bank_cd
     AND cth.card_no = cm.card_no
   WHERE cth.biz_key = :bizKey
     AND cth.trsc_dt BETWEEN :fromDt AND :toDt
     AND (cardinality(cast(:bankCds as text[])) = 0 OR cth.bank_cd = ANY(:bankCds))
     AND (cardinality(cast(:cardNos as text[])) = 0 OR cth.card_no = ANY(:cardNos))
     AND ( :srchWd IS NULL
        OR cm.card_hldr_nm ILIKE '%' || :srchWd || '%'
        OR cm.card_nm      ILIKE '%' || :srchWd || '%'
        OR cm.card_nick_nm ILIKE '%' || :srchWd || '%'
        OR cth.merch_nm    ILIKE '%' || :srchWd || '%')
     AND ( :linkYn = ''
        OR ( :linkYn = 'Y' AND EXISTS (SELECT 1 FROM busa_doc_rcv_pay_his …) )
        OR ( :linkYn = 'N' AND NOT EXISTS (SELECT 1 FROM busa_doc_rcv_pay_his …) ) )
  ORDER BY … (Spring `Pageable.getSort()` whitelisted via `CardTrnsHstrSortField`) …
  """, nativeQuery = true)
Page<CardTransactionRow> findLedgerPage(
    @Param("bizKey") String bizKey,
    @Param("fromDt") String fromDt,
    @Param("toDt")   String toDt,
    @Param("bankCds") String[] bankCds,
    @Param("cardNos") String[] cardNos,
    @Param("srchWd")  String srchWd,
    @Param("linkYn")  String linkYn,
    Pageable pageable);
```

The `JSON_AGG`-built `linked_docs` / `approvers` columns return JSONB on the wire — the row mapper deserialises them into the typed Java records (`LinkedDocRef`, `ApproverRef`) using Jackson. **No mid-row `for` loop in the controller**, matching how `domain/setting/firmbankingsettings` already handles `V_FIRM_BANKING_HISTORY`.

The cleaned ORDER BY uses the whitelisted `CardTrnsHstrSortField` enum (see §4) — never concatenate `sortField` into the SQL string, per [wecms-api/CLAUDE.md §"Common table sort"](../../../CLAUDE.md).

The wrapper `T.REC_1 IS NOT NULL` filter the legacy uses ([_r001_act.jsp:140-148](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L140-L148)) gets folded into the WHERE as `EXISTS (SELECT 1 FROM busa_doc_rcv_pay_his …)` so the database doesn't materialise the nested aggregate for every row before filtering.

### 3.2 `IDO.CARD_TRNS_COUNT_R001.xml` → `CardTrnsHstrRepository.findLedgerTotals(...)`

Source: [IDO.CARD_TRNS_COUNT_R001.xml:24-37](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CARD_TRNS_COUNT_R001.xml#L24-L37). One row with `TOTL_CNT`, `TOTL_AMT`, `TOTL_APPR_REQ_CNT/_AMT`, `TOTL_APPR_NOT_REQ_CNT/_AMT`. **Stays native** for the same reason — aggregate + conditional sum.

Refactor merges this with §3.1 into a single `GET /api/v1/payment/card-transactions` response: the service runs `findLedgerPage` + `findLedgerTotals` in parallel `@Async` calls (both bounded by the same WHERE), returns one envelope:

```jsonc
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "page": 1, "page_size": 15, "total_count": 268,
    "totals": {
      "totl_amt":              "169000000.000000",
      "totl_appr_req_cnt":     31,
      "totl_appr_req_amt":     "98000000.000000",
      "totl_appr_not_req_cnt": 237,
      "totl_appr_not_req_amt": "71000000.000000"
    },
    "rows": [ /* see features doc §6.3 */ ]
  }
}
```

`TOTL_LINK_CNT` / `TOTL_UN_LINK_CNT` are dropped — legacy hard-codes them to `0` ([_r001_act.jsp:242-243](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r001_act.jsp#L242-L243)) so neither field is real. Refactor doesn't propagate dead fields.

### 3.3 `IDO.CARD_MSTR_R003.xml` → `CardMstrRepository.findActiveCards(...)`

Source: [IDO.CARD_MSTR_R003.xml:24-49](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CARD_MSTR_R003.xml#L24-L49). Returns `(BANK_CD, BANK_NM, CARD_NO, CARD_NM, CARD_NICK_NM, CARD_HLDR_NM, MOD_ID, MOD_DTM)`. Becomes:

- **Derived JPA query** for the row read: `findByBizKeyOrderByModDtmDesc(bizKey, Pageable.unpaged())` with an optional `bankCd` predicate.
- `BANK_NM` projection routes through the existing **`BankCdResolver` cache** ([wecms-api/CLAUDE.md §"Reference data — single sources of truth"](../../../CLAUDE.md)) — `BANK_INFM` is in the active list and the cache is already warm. The legacy `wb_get_bank_nm` function is **not** ported (single-language, returns one display label; `BankCdResolver` already handles this cleanly).

Note legacy filter `WHERE 1 = 1 ??` ([line 47-48](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.CARD_MSTR_R003.xml#L47-L48)) does **not** restrict `ACTV_YN = 'Y'` — the legacy JSP comment-blocks it out at [card_0001_001_r002_act.jsp:35-36](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r002_act.jsp#L35-L36). Refactor preserves the same behaviour (return both active and inactive cards) — flagged as OQ-A in §6 because the comment-out suggests there may be intent the refactor should clarify with the PO.

### 3.4 Legacy DB functions consumed — porting decision

[wecms-api/CLAUDE.md §"DB Function / Legacy Function Policy"](../../../CLAUDE.md) says **don't introduce new legacy DB-function dependencies in feature code**. For each function the legacy query calls, the refactor takes a stance:

| Function | Legacy call site | Refactor stance | Rationale |
|---|---|---|---|
| `wb_get_bank_nm(bank_cd)` | `IDO.CARD_MSTR_R003.xml:37` | **Drop.** Use `BankCdResolver` (Java cache over `BANK_INFM`). | Already the canonical replacement; `BANK_INFM` is in the active list. |
| `wb_get_prch_stts_nm(prch_stts, lang_cd)` | `IDO.CARD_MSTR_R002.xml:73` | **Drop.** Ship raw `stts` on the wire + sibling `stts_key` i18n key resolved via `MessageResolver` against `payment.card_transactions.status.*`. | Matches the enum-pattern shared rule ([wecms-api/CLAUDE.md §"Domain enums"](../../../CLAUDE.md)). Status value set is OQ-3. |
| `wb_get_usag_nm_new(biz_key, code, lang_cd)` | `IDO.CARD_MSTR_R002.xml:64` | **Drop in Sprint 0** — read `USAG_INFM` + `USAG_INFM_LANG_DETL` directly via a Java service (`UsageCodeResolver`, lightweight cache). | Both tables are in the active list; the function offers no behaviour the Java path can't match. Aligns with the broader refactor direction. |
| `wb_get_memo_cont` / `wb_get_memo_color` | `IDO.CARD_MSTR_R002.xml:65-66` | **Defer.** Side-memo is read-only this sprint (OQ-6); when the memo write-path lands under its own slug, both reads are ported alongside it. For now the column ships null and the side-memo panel collapses. | Out of scope. |
| `wb_get_chart_acct_nm(biz_key, seq, code)` | `IDO.CARD_MSTR_R002.xml:82` | **Drop.** Reuse the resolved name from `CHART_ACCT_INFM` via JPA join — `chart-of-accounts` already ports the tree resolver (`ChartOfAccountsTreeBuilder`). | The refactor owns the same projection; no need for the function. |
| `wb_get_chart_acct_lv_nm(biz_key, seq, code)` | `IDO.CARD_MSTR_R002.xml:85` | **Drop.** Same — the breadcrumb path is computed by `ChartOfAccountsTreeBuilder.composeLevelName`. | Already covered. |

Net legacy-function dependency added by the refactor: **zero**. No new `wb_get_next_val` branch, no new `CREATE FUNCTION` in `db/migration/`. (Sprint 1 audit precedent — Sprint 1 banking work added zero. Consistent.)

### 3.5 `IDO.CARD_TRNS_HSTR_U000.xml` (partial patch) → `CardTrnsHstrRepository.patch*`

Legacy `_U000.xml` (not transcribed here — same pattern as every Default `_U000`) does a generic dynamic-fields PATCH. Refactor replaces with three narrow `@Modifying @Query` writers:

```java
@Modifying
@Query("UPDATE CardTrnsHstr c " +
       "   SET c.cont = :cont, c.modId = :modId, c.modDtm = :modDtm " +
       " WHERE c.bizKey = :bizKey AND c.cardSeqNo = :cardSeqNo")
int patchDescription(@Param("bizKey") String bizKey,
                     @Param("cardSeqNo") String cardSeqNo,
                     @Param("cont") String cont,
                     @Param("modId") String modId,
                     @Param("modDtm") String modDtm);

@Modifying
@Query("UPDATE CardTrnsHstr c " +
       "   SET c.cardTrnsUsageCd = :cd, c.modId = :modId, c.modDtm = :modDtm " +
       " WHERE c.bizKey = :bizKey AND c.cardSeqNo = :cardSeqNo")
int patchPurpose(@Param("bizKey") String bizKey, /* … */);

@Modifying
@Query("UPDATE CardTrnsHstr c " +
       "   SET c.chartAcctSeqNo = :seqNo, " +
       "       c.chartAcctCd    = :cd, " +
       "       c.modId          = :modId, " +
       "       c.modDtm         = :modDtm " +
       " WHERE c.bizKey = :bizKey AND c.cardSeqNo = :cardSeqNo")
int patchReportingAccount(@Param("bizKey") String bizKey, /* … */);
```

Return value `int` (rows affected) — service throws `BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND")` when `0` so the not-found / wrong-tenant pair collapses into a single response code (precedent: `WBE_APPROVAL_LINE_NOT_FOUND`, `WBE_CHART_OF_ACCOUNTS_NOT_FOUND`).

## 4 · Entity / VO → JPA entity + DTO(s)

### 4.1 JPA entities

| Legacy entity | Refactor entity | PK | Notes |
|---|---|---|---|
| [CardTrnsHstr.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/CardTrnsHstr.java) (extends [CardTrnsHstrPk.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/CardTrnsHstrPk.java)) | `domain.payment.cardtransactions.entity.CardTrnsHstr` | `@IdClass(CardTrnsHstrId)` on `(BIZ_KEY, CARD_SEQ_NO)` | Maps the 26 legacy columns 1:1 (per DEV schema query). All UPPER_SNAKE `@Column(name = …)`. Extends `BaseEntity` so `REG_ID/REG_DTM/MOD_ID/MOD_DTM` come from the shared audit superclass. The Sprint 0 subset can omit `auth_code` / `city` / `note` / `using_type` / `inout_dv` / `erp_snd_yn` mappings — they're not read or written by this feature. Document this in the entity Javadoc per [wecms-api/CLAUDE.md §"The schema vs. entity vs. DTO split"](../../../CLAUDE.md). |
| [CardMstr.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/CardMstr.java) (extends [CardMstrPk.java](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/CardMstrPk.java)) | `domain.payment.cardtransactions.entity.CardMstr` | `@IdClass(CardMstrId)` on `(BIZ_KEY, BANK_CD, CARD_NO)` | Same subset rule — only `bankCd`, `cardNo`, `cardNm`, `cardNickNm`, `cardHldrNm`, `actvYn`, `modId`, `modDtm` mapped. Rest of the 25 columns can stay unmapped. |

The composite-PK `getDynamic()` / `copyFrom()` / `copyTo()` plumbing is **gone** — JPA's `@IdClass` does the equivalent natively. No `WbUser` / `JexData` references in the refactor classes.

### 4.2 DTOs (Java records, camelCase → snake_case wire)

```java
package com.kosign.wecms.domain.payment.cardtransactions.dto;

public record CardTransactionListRequest(
    @NotBlank @Pattern(regexp = "^\\d{8}$") String fromDt,
    @NotBlank @Pattern(regexp = "^\\d{8}$") String toDt,
    String srchWd,
    List<@Pattern(regexp = "^\\d{8}$") String> bankCds,
    List<String> cardNos,
    @Pattern(regexp = "^[YN]?$") String linkYn,
    @Min(1) Integer pageNo,
    @Min(1) @Max(100) Integer pagePerCnt,
    String sortField,
    String sortDirection
) { }

public record CardTransactionRow(
    String cardSeqNo,
    String bankCd, String bankNm,
    String cardNo, String cardNm, String cardNickNm, String cardHldrNm,
    String merchNm,
    String ccyCd,
    BigDecimal trscAmt,
    String trscDt, String trscTm,
    String cardTrnsUsageCode, String cardTrnsUsageNm,
    String chartAcctSeqNo, String chartAcctCd,
    String chartAcctNm, String chartAcctNmEn, String chartAcctLevelNm,
    String cont,
    String apprSeqNo, String apprDocNo, String apprDv,
    Long   postSeqNo,
    String postRegId,
    String stts, String sttsKey,
    List<LinkedDocRef> linkedDocs,
    List<ApproverRef>  approvers
) { }

public record LinkedDocRef(String docId, String dispDocId, String handDocId,
                            String docType, String einvYn, BigDecimal totlAmt) { }

public record ApproverRef(String apprvrId, String apprvrNm, String apprvrStts) { }

public record CardTransactionTotals(
    BigDecimal totlAmt,
    long       totlApprReqCnt,    BigDecimal totlApprReqAmt,
    long       totlApprNotReqCnt, BigDecimal totlApprNotReqAmt
) { }

public record CardTransactionListResponse(
    int page, int pageSize, long totalCount,
    CardTransactionTotals totals,
    List<CardTransactionRow> rows
) { }

public sealed interface CardTransactionPatchRequest
    permits PatchDescriptionRequest, PatchPurposeRequest { }

public record PatchDescriptionRequest(@Size(max = 200) String cont)
    implements CardTransactionPatchRequest { }

public record PatchPurposeRequest(@Pattern(regexp = "^\\d{1,20}$") String cardTrnsUsageCode)
    implements CardTransactionPatchRequest { }

public record UpdateReportingAccountRequest(
    @NotBlank @Size(max = 10) String chartAcctSeqNo,
    @NotBlank @Size(max = 20) String chartAcctCd
) { }

public record ActiveCard(String bankCd, String bankNm,
                         String cardNo, String cardNm, String cardNickNm, String cardHldrNm) { }

public record CardListResponse(List<ActiveCard> cards) { }
```

Java fields stay camelCase; the global `SNAKE_CASE` Jackson strategy handles the wire shape. **No `@JsonProperty("UPPER_SNAKE")`** anywhere — the holdover list in [wecms-web/CLAUDE.md §"Holdovers"](../../../../wecms-web/CLAUDE.md) does NOT include card-transactions, so this feature ships clean from day one.

## 5 · Replacing the legacy single-field patch idiom

Legacy `card_0001_001_u001` takes `FIELD_ID` ∈ {`CONT`, `CARD_TRNS_USAGE_CODE`} + `FIELD_VAL` ([u001_act.jsp:75-81](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_u001_act.jsp#L75-L81)). The refactor lifts this into a discriminated `PATCH` body — the URI is one endpoint per PK, the body decides which scalar gets patched, and Bean Validation enforces shape at the boundary.

```http
PATCH /api/v1/payment/card-transactions/{cardSeqNo}
Content-Type: application/json

{ "field": "description", "value": "Updated description" }
```

or

```http
{ "field": "purpose", "value": "1007010" }
```

Service dispatches on `field`:

- `"description"` → `CardTrnsHstrRepository.patchDescription(...)` → audit row (OQ-2) with `WSVC_ID='card-transactions'`.
- `"purpose"` → `CardTrnsHstrRepository.patchPurpose(...)` → audit row.
- Unknown `field` → `BusinessException("WBE_VALIDATION")` from the request validator (Bean Validation rejects unknown literal values via the request mapper).

Why one endpoint, not two — single PK URI scales cleanly when a third single-field patch lands later (e.g., `note`). Adding a new `field` value plus its corresponding repository writer is a localised change; no new route, no new doc.

## 6 · Reusable components already in `wecms-api`

Consume — do not duplicate:

| Concern | Reusable component | Notes |
|---|---|---|
| Tenant filter | `TenantContext.currentBizKey()` | Every repo call binds this. |
| Bank-code → name | `BankCdResolver` ([wecms-api/CLAUDE.md §"Reference data"](../../../CLAUDE.md)) | Replaces `wb_get_bank_nm`. |
| View Setting load | `ScreenOptionService.load("card-transactions", "table1"/"table2")` | Lazy-INSERT on first call per scope. |
| Excel export | `ExcelExportService` + per-screen `ExportRowProvider` | New `CardTransactionsExportProvider` lands in the same MR. |
| Audit log | `AuditLogService.append(WSVC_ID, action, …)` | New write rows in PATCH / PUT. Same pattern as `firm-banking-settings`. |
| Sort whitelist | `common/web/SortableField` + new `CardTrnsHstrSortField` enum | One constant per FIELD_ID in §6.1 of the features doc, mapped to the JPA property. |
| Lang code | `LangCd.fromAcceptLanguage(header)` then echoed via `TenantContext.currentLangCd()` | Drives the `*_key` resolution path. |
| Common envelope | `ApiResponse.ok(data)` / `BusinessException("WBExxxx")` | No bespoke envelope. |
| Trace id | `TraceIdFilter` + `%X{traceId}` MDC | Already in place — every response carries `trace_id` on error. |

## 7 · Reusable components already in `wecms-web`

| Concern | Reusable component |
|---|---|
| Page chrome | `<AppShell>` + `<Header>` + `<Sidebar>` |
| Page title + toolbar | `<PageHeader>` + `<PageActionButtons>` |
| Filter | `<FilterRow>` + `<SearchInput>` + `<DateRangePicker>` |
| Pagination | `usePagination` + `<Pagination>` |
| Table shell + sibling-table footer | `<DataTableShell>` + `<DataTableFooter>` (totals row uses the multi-currency totals pattern) |
| View Setting popover | the existing screen-id-registry-driven popover (`SCREEN_IDS.CARD_TRANSACTIONS`) |
| Excel export | `useExcelExport({ endpoint: …, screenId: "card-transactions", … })` |
| Print | `lib/print.ts printReport({ … })` (replaces legacy `setUpDataForPrint`) |
| Dialog | `useDialog()` — `"error"` for API failures (NOT `"warning"`) per [wecms-web/CLAUDE.md §"Dialog variant"](../../../../wecms-web/CLAUDE.md) |
| Currency formatting | `lib/currency.ts formatAmount(value, ccyCd)` |
| Date formatting | `lib/datetime.ts` (replaces `common.app.formatDateLang`, `common.app.time_format2`) |

No new shared component is needed for this feature — every piece is already in the four approved baselines.

## 8 · Behavior deltas from JEX (intentional)

| Delta | Reason |
|---|---|
| Drop `TOTL_LINK_CNT` / `TOTL_UN_LINK_CNT` from the totals envelope | Legacy hard-codes them to `0` — they're dead fields. |
| Drop `wb_get_*` DB functions from the read query | [wecms-api/CLAUDE.md §"DB Function / Legacy Function Policy"](../../../CLAUDE.md) — no new legacy-function dependencies. Functions in §3.4 are replaced one-for-one by Java services. |
| Single PATCH endpoint (with `field` discriminator) instead of two `_u001` calls | Cleaner URI shape; Bean Validation moves error to the boundary; future single-field patches add a new `field` value, not a new endpoint. |
| Single envelope for list + counts (`/api/v1/payment/card-transactions`) instead of two round-trips | Reduces frontend complexity — the totals card always reflects the same filter snapshot as the table. Legacy calls only `r001` for both already, but combines them in `_act.jsp`. Refactor formalises that as one DTO. |
| Audit-log enabled (legacy writes zero rows for `card_0001_001*`) | OQ-2 default. Consistent with `firm-banking-settings` / `chart-of-accounts` / `contacts`. |
| Drop legacy `inout_dv` / `note` / `auth_code` / `city` / `using_type` / `erp_snd_yn` from wire | Page doesn't render them. Columns remain in the DB; the entity Sprint 0 subset omits them. Re-add in a later sprint if a feature surfaces them. |
| `bank_nm` always resolved server-side via `BankCdResolver` | Frontend never hard-codes bank names. |

## 9 · MR checklist (for the future implementation MR — informational here)

- [ ] One Flyway migration adds the `card-transactions` View Setting seed rows (`table1` + `table2`) — see [database/card-transactions.md](../database/card-transactions.md) §"Seed".
- [ ] No new `wb_get_next_val` branch added.
- [ ] No new `CREATE FUNCTION` in `db/migration/`.
- [ ] `CardTrnsHstr` / `CardMstr` entities document the Sprint 0 column subset in Javadoc.
- [ ] `CardTrnsHstrSortField` enum lists every `SORT_YN='Y'` FIELD_ID from the seed (refactor catches the View-Setting-vs-whitelist agreement at startup via the existing audit).
- [ ] `messages.properties` gains `WBE_CARD_TRANSACTIONS_NOT_FOUND`, `WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID`, `WBE_CARD_TRANSACTIONS_FIELD_UNKNOWN` (and EN translations).
- [ ] Frontend `lib/types/api.ts` gains `ApiCardTransactionRow` + `ApiCardTransactionListResponse` (snake_case); `lib/api/mappers.ts` gains the camelCase mapper; consumer reads camelCase only.
- [ ] `SCREEN_IDS.CARD_TRANSACTIONS = "card-transactions"` registered in [wecms-web/lib/viewSettingScreens.ts](../../../../wecms-web/lib/viewSettingScreens.ts).
- [ ] `locales/en.json` + `locales/ko.json` + `locales/vi.json` gain `card_transactions.*` keys (no `km`).
- [ ] Parity checklist at [audits/card-transactions-parity-checklist.md](../audits/card-transactions-parity-checklist.md) is ticked.

## 10 · Open questions specific to this mapping

| ID | Question | Default |
|---|---|---|
| OQ-A | The `ACTV_YN = 'Y'` filter on the cards dropdown is commented out in [card_0001_001_r002_act.jsp:35-36](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_r002_act.jsp#L35-L36). Intentional or accidental? | Preserve legacy behaviour (no `ACTV_YN` filter). |
| OQ-B | `INOUT_DV` is set by `CardTrnsHstrDAO.create` to `OUT` when null ([CardTrnsHstrDAO.java:57-59](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/CardTrnsHstrDAO.java#L57-L59)). Refactor doesn't write `CARD_TRNS_HSTR` — does the page need to surface `INOUT_DV` (in/out chip on the amount column) like Account Inquiry does? | No — page only renders amount; legacy renders no in/out chip on the visible columns. |
| OQ-C | Legacy `setCardTrnsUsageCd` has a typo at [CardTrnsHstr.java:184-187](../../../../WECMS%20DEV/WECMS/src/wabooks/entity/CardTrnsHstr.java#L184-L187) — `this.apprSeqNo = foo` instead of `this.cardTrnsUsageCd = foo`. Refactor will write the field correctly; flag the JEX bug separately under `docs/bug-reports/` with `System: JEX`. | Fix in refactor; file JEX bug. |

Cross-references to the feature spec's OQ list (OQ-1..10) live in [docs/refactor-wecms/features/card-transactions.md §11](../../features/payment/card-transactions.md).
