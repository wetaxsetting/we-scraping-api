# Card Transactions — Backend Design (Spring Boot)

Companion to [docs/refactor-wecms/features/card-transactions.md](../../features/payment/card-transactions.md) and [docs/refactor-wecms/migration/card-transactions.md](../migration/card-transactions.md). Refactor slug: **`card-transactions`**. Module package: `com.kosign.wecms.domain.payment.cardtransactions`.

> **Identifier-naming rule reminder** — the kebab-case slug `card-transactions` is the canonical id across every Spring surface (REST URI, Java package, class name, DTO name, error code, i18n message key, log/MDC tag, audit `WSVC_ID`). Legacy `card_0001_001*` ids appear only in JEX file:line citations and migration-traceability prose.

---

## 1 · Package layout

```
com.kosign.wecms.domain.payment.cardtransactions
├── controller/
│   └── CardTransactionsController.java
├── service/
│   ├── CardTransactionsService.java
│   ├── CardTrnsHstrSortField.java            (whitelist enum implements SortableField)
│   ├── UsageCodeResolver.java                (replaces wb_get_usag_nm_new — reads USAG_INFM)
│   └── CardTransactionsExportProvider.java   (@Component implements ExportRowProvider)
├── repository/
│   ├── CardTrnsHstrRepository.java
│   └── CardMstrRepository.java
├── entity/
│   ├── CardTrnsHstr.java                     @IdClass(CardTrnsHstrId.class), extends BaseEntity
│   ├── CardTrnsHstrId.java                   Serializable composite key
│   ├── CardMstr.java                         @IdClass(CardMstrId.class), extends BaseEntity
│   └── CardMstrId.java
└── dto/                                       (Java records — see §5)
    ├── CardTransactionListRequest.java
    ├── CardTransactionListResponse.java
    ├── CardTransactionRow.java
    ├── CardTransactionTotals.java
    ├── LinkedDocRef.java
    ├── ApproverRef.java
    ├── CardTransactionPatchRequest.java       (sealed)
    ├── PatchDescriptionRequest.java
    ├── PatchPurposeRequest.java
    ├── UpdateReportingAccountRequest.java
    ├── ActiveCard.java
    └── CardListResponse.java
```

`payment` is the JEX module-code-as-Java-package per the playbook step 5 template. No cross-domain repository access; no controller-to-controller calls. Helpers (`UsageCodeResolver`) stay in this domain until a second consumer materialises, per [wecms-api/CLAUDE.md §"Package rules"](../../../CLAUDE.md).

## 2 · Controller skeleton

```java
@RestController
@RequestMapping("/api/v1/payment/card-transactions")
@RequiredArgsConstructor
public class CardTransactionsController {

    private final CardTransactionsService service;

    @GetMapping
    public ApiResponse<CardTransactionListResponse> list(@Valid @ModelAttribute CardTransactionListRequest req) {
        return ApiResponse.ok(service.list(req));
    }

    @GetMapping("/cards")
    public ApiResponse<CardListResponse> cards(
            @RequestParam(value = "bank_cd", required = false) @Pattern(regexp = "^\\d{0,20}$") String bankCd) {
        return ApiResponse.ok(service.cards(bankCd));
    }

    @PatchMapping("/{cardSeqNo}")
    public ApiResponse<Void> patch(@PathVariable @Pattern(regexp = "^\\d{1,20}$") String cardSeqNo,
                                   @Valid @RequestBody CardTransactionPatchRequest body) {
        service.patch(cardSeqNo, body);
        return ApiResponse.ok();
    }

    @PutMapping("/{cardSeqNo}/reporting-account")
    public ApiResponse<Void> updateReportingAccount(@PathVariable @Pattern(regexp = "^\\d{1,20}$") String cardSeqNo,
                                                    @Valid @RequestBody UpdateReportingAccountRequest body) {
        service.updateReportingAccount(cardSeqNo, body);
        return ApiResponse.ok();
    }

    @PostMapping("/export")
    public void export(@Valid @RequestBody ExcelExportRequest req, HttpServletResponse response) {
        // ExcelExportService streams the bytes; controller returns void per the shared contract.
        service.export(req, response);
    }
}
```

**Controller rules** ([wecms-api/CLAUDE.md §"Coding Rules → Controllers"](../../../CLAUDE.md)):

- Thin — no business logic.
- DTOs are Java records with `@NotBlank` / `@Pattern` / `@Size` on components. Validation failure → `WBE_VALIDATION` via the global handler.
- No `@PreAuthorize` annotation today (parity with legacy + with `approval-line` / `chart-of-accounts`). When per-role gating lands, it lands here via OQ to PO.
- Path-param `{cardSeqNo}` carries `@Pattern(regexp = "^\\d{1,20}$")` so unparseable inputs degrade to 200 / `WBE_VALIDATION` rather than `WBE_NOT_FOUND` from the no-route fallback. Constraint validator hooks into `MethodValidationPostProcessor` (already enabled).

`sort_field` + `sort_direction` arrive as `@RequestParam` strings; service parses them via `CardTrnsHstrSortField.parse(...)` + `SortDirection.parse(...)`, never echoing them into SQL.

## 3 · Service skeleton

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class CardTransactionsService {

    private final CardTrnsHstrRepository  cardTrnsRepo;
    private final CardMstrRepository      cardMstrRepo;
    private final BankCdResolver          bankCdResolver;
    private final UsageCodeResolver       usageResolver;
    private final ChartOfAccountsValidator chartValidator; // verifies parent exists, not soft-deleted
    private final ScreenOptionService     screenOptions;   // for export visibility intersection
    private final AuditLogService         auditLog;
    private final MessageResolver         msgResolver;     // for stts_key / lang-aware labels

    private static final String AUDIT_WSVC_ID = "card-transactions";
    private static final Sort   DEFAULT_SORT  =
        Sort.by(Sort.Order.desc("trscDt"), Sort.Order.desc("trscTm"), Sort.Order.desc("cardSeqNo"));

    @Transactional(readOnly = true)
    public CardTransactionListResponse list(CardTransactionListRequest req) {
        String bizKey = TenantContext.currentBizKey();
        validatePeriod(req.fromDt(), req.toDt());

        Sort sort = CardTrnsHstrSortField.parse(req.sortField())
            .map(f -> Sort.by(SortDirection.parse(req.sortDirection()).orElse(SortDirection.ASC)
                                .toSpring(f.entityProperty())))
            .orElse(Sort.unsorted())
            .and(DEFAULT_SORT);

        int pageNo  = Optional.ofNullable(req.pageNo()).orElse(1);
        int pageSz  = Optional.ofNullable(req.pagePerCnt()).orElse(15);
        Pageable pageable = PageRequest.of(pageNo - 1, pageSz, sort);

        Page<CardTransactionRowProjection> page = cardTrnsRepo.findLedgerPage(
            bizKey,
            req.fromDt(), req.toDt(),
            toArray(req.bankCds()), toArray(req.cardNos()),
            blankToNull(req.srchWd()),
            Optional.ofNullable(req.linkYn()).orElse(""),
            pageable);

        CardTransactionTotals totals = cardTrnsRepo.findLedgerTotals(
            bizKey,
            req.fromDt(), req.toDt(),
            toArray(req.bankCds()), toArray(req.cardNos()),
            blankToNull(req.srchWd()),
            Optional.ofNullable(req.linkYn()).orElse(""));

        List<CardTransactionRow> rows = page.getContent().stream()
            .map(p -> toRow(p))
            .toList();

        return new CardTransactionListResponse(pageNo, pageSz, page.getTotalElements(), totals, rows);
    }

    @Transactional
    public void patch(String cardSeqNo, CardTransactionPatchRequest body) {
        String bizKey = TenantContext.currentBizKey();
        String modId  = TenantContext.currentUserId();
        String modDtm = DateTimeUtils.utcNow14();

        int n = switch (body) {
            case PatchDescriptionRequest r ->
                cardTrnsRepo.patchDescription(bizKey, cardSeqNo, r.cont(), modId, modDtm);
            case PatchPurposeRequest r ->
                cardTrnsRepo.patchPurpose(bizKey, cardSeqNo, r.cardTrnsUsageCode(), modId, modDtm);
        };

        if (n == 0) throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");

        auditLog.append(AUDIT_WSVC_ID,
                        body instanceof PatchDescriptionRequest ? "PATCH_DESCRIPTION" : "PATCH_PURPOSE",
                        Map.of("card_seq_no", cardSeqNo));
    }

    @Transactional
    public void updateReportingAccount(String cardSeqNo, UpdateReportingAccountRequest body) {
        String bizKey = TenantContext.currentBizKey();

        // Defensive: reject reporting account that points to a soft-deleted / non-existent chart row.
        chartValidator.requireActive(bizKey, body.chartAcctSeqNo(), body.chartAcctCd())
            .orElseThrow(() -> new BusinessException("WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID"));

        int n = cardTrnsRepo.patchReportingAccount(
            bizKey, cardSeqNo, body.chartAcctSeqNo(), body.chartAcctCd(),
            TenantContext.currentUserId(), DateTimeUtils.utcNow14());

        if (n == 0) throw new BusinessException("WBE_CARD_TRANSACTIONS_NOT_FOUND");

        auditLog.append(AUDIT_WSVC_ID, "PATCH_REPORTING_ACCOUNT",
                        Map.of("card_seq_no", cardSeqNo,
                               "chart_acct_seq_no", body.chartAcctSeqNo(),
                               "chart_acct_cd", body.chartAcctCd()));
    }

    @Transactional(readOnly = true)
    public CardListResponse cards(String bankCd) {
        String bizKey = TenantContext.currentBizKey();
        List<ActiveCard> rows = cardMstrRepo.findCardsForFilter(bizKey, blankToNull(bankCd))
            .stream()
            .map(p -> new ActiveCard(
                p.getBankCd(),
                bankCdResolver.resolveName(p.getBankCd()).orElse(p.getBankCd()),
                p.getCardNo(), p.getCardNm(), p.getCardNickNm(), p.getCardHldrNm()))
            .toList();
        return new CardListResponse(rows);
    }

    /* private helpers — validatePeriod, blankToNull, toArray, toRow — omitted */
}
```

Behaviour notes:

- **Period validation** mirrors legacy `ValidationHelper.validatePeriod` — `FROM_DT` ≤ `TO_DT`, span ≤ 1 year (legacy implicit cap; refactor enforces explicitly as `WBE_VALIDATION` with a localised message). Confirm cap with PO if 1-year isn't right.
- **`linkYn` "" / "Y" / "N"`** map directly to the SQL EXISTS / NOT EXISTS clauses; service trims unknown values to `""` rather than rejecting (defensive against stale frontend bundles, same precedent as the sort whitelist).
- **`patchPurpose` is intentionally a different writer from `patchDescription`** because the underlying column differs and audit-log action codes differ. The discriminated DTO collapses to one endpoint at the wire boundary but two repository methods.
- **`toRow(...)`** is where the `bank_nm` + `stts_key` + JSON-aggregated lists are composed. `BankCdResolver` is consulted once per unique `bank_cd` per page (cached). `stts_key` is `"payment.card_transactions.status." + p.getStts()` when `stts` is non-blank; null when blank (frontend renders the raw code with a "—" fallback).

## 4 · Repository skeleton

```java
public interface CardTrnsHstrRepository extends JpaRepository<CardTrnsHstr, CardTrnsHstrId> {

    @Query(value = """
        … see migration/card-transactions.md §3.1 …
        """, nativeQuery = true)
    Page<CardTransactionRowProjection> findLedgerPage(
        @Param("bizKey")  String bizKey,
        @Param("fromDt")  String fromDt,
        @Param("toDt")    String toDt,
        @Param("bankCds") String[] bankCds,
        @Param("cardNos") String[] cardNos,
        @Param("srchWd")  String srchWd,
        @Param("linkYn")  String linkYn,
        Pageable pageable);

    @Query(value = """
        … see migration/card-transactions.md §3.2 …
        """, nativeQuery = true)
    CardTransactionTotalsProjection findLedgerTotals(
        @Param("bizKey")  String bizKey,
        @Param("fromDt")  String fromDt,
        @Param("toDt")    String toDt,
        @Param("bankCds") String[] bankCds,
        @Param("cardNos") String[] cardNos,
        @Param("srchWd")  String srchWd,
        @Param("linkYn")  String linkYn);

    @Modifying
    @Query("UPDATE CardTrnsHstr c SET c.cont = :cont, c.modId = :modId, c.modDtm = :modDtm " +
           " WHERE c.bizKey = :bizKey AND c.cardSeqNo = :cardSeqNo")
    int patchDescription(@Param("bizKey") String bizKey, @Param("cardSeqNo") String cardSeqNo,
                         @Param("cont") String cont, @Param("modId") String modId, @Param("modDtm") String modDtm);

    @Modifying
    @Query("UPDATE CardTrnsHstr c SET c.cardTrnsUsageCd = :cd, c.modId = :modId, c.modDtm = :modDtm " +
           " WHERE c.bizKey = :bizKey AND c.cardSeqNo = :cardSeqNo")
    int patchPurpose(@Param("bizKey") String bizKey, @Param("cardSeqNo") String cardSeqNo,
                     @Param("cd") String cd, @Param("modId") String modId, @Param("modDtm") String modDtm);

    @Modifying
    @Query("UPDATE CardTrnsHstr c SET c.chartAcctSeqNo = :seq, c.chartAcctCd = :cd, " +
           "                          c.modId = :modId, c.modDtm = :modDtm " +
           " WHERE c.bizKey = :bizKey AND c.cardSeqNo = :cardSeqNo")
    int patchReportingAccount(@Param("bizKey") String bizKey, @Param("cardSeqNo") String cardSeqNo,
                              @Param("seq") String seq, @Param("cd") String cd,
                              @Param("modId") String modId, @Param("modDtm") String modDtm);
}

public interface CardMstrRepository extends JpaRepository<CardMstr, CardMstrId> {

    @Query("SELECT c FROM CardMstr c " +
           " WHERE c.bizKey = :bizKey " +
           "   AND (:bankCd IS NULL OR c.bankCd = :bankCd) " +
           " ORDER BY c.modDtm DESC")
    List<CardMstrProjection> findCardsForFilter(@Param("bizKey") String bizKey,
                                                @Param("bankCd") String bankCd);
}
```

`CardTransactionRowProjection` / `CardTransactionTotalsProjection` / `CardMstrProjection` are Spring Data interface projections wrapped around the native query column list. They keep the entity free of native-only fields (e.g., the JSON-aggregated columns).

## 5 · DTO catalogue

See [migration/card-transactions.md §4.2](../migration/card-transactions.md#42-dtos-java-records-camelcase--snake_case-wire) for the canonical declarations. Validation summary:

| Record | Field | Annotation |
|---|---|---|
| `CardTransactionListRequest` | `fromDt` | `@NotBlank @Pattern("^\\d{8}$")` |
| | `toDt`   | `@NotBlank @Pattern("^\\d{8}$")` |
| | `srchWd` | `@Size(max = 100)` (optional) |
| | `bankCds[i]` | `@Pattern("^\\d{0,20}$")` |
| | `cardNos[i]` | `@Size(max = 100)` |
| | `linkYn` | `@Pattern("^[YN]?$")` |
| | `pageNo` | `@Min(1)` |
| | `pagePerCnt` | `@Min(1) @Max(100)` |
| | `sortField`, `sortDirection` | (raw strings; service-side enum-parse — see [wecms-api/CLAUDE.md §"Common table sort"](../../../CLAUDE.md)) |
| `PatchDescriptionRequest` | `cont` | `@Size(max = 200)` (nullable — empty string allowed to clear) |
| `PatchPurposeRequest` | `cardTrnsUsageCode` | `@Pattern("^\\d{1,20}$")` (nullable — empty allowed to clear) |
| `UpdateReportingAccountRequest` | `chartAcctSeqNo` | `@NotBlank @Size(max = 10)` |
| | `chartAcctCd` | `@NotBlank @Size(max = 20)` |

The discriminated `CardTransactionPatchRequest` resolves via Jackson polymorphism on the `field` property:

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "field", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PatchDescriptionRequest.class, name = "description"),
    @JsonSubTypes.Type(value = PatchPurposeRequest.class,     name = "purpose")
})
public sealed interface CardTransactionPatchRequest permits PatchDescriptionRequest, PatchPurposeRequest { }
```

Unknown `field` value → `HttpMessageNotReadableException` → `WBE_BAD_REQUEST` per the global handler.

## 6 · Exception mapping

Per [wecms-api/CLAUDE.md §"Exception → response mapping"](../../../CLAUDE.md), all errors funnel through `GlobalExceptionHandler`. New codes this feature contributes to `messages.properties`:

```properties
WBE_CARD_TRANSACTIONS_NOT_FOUND=Card transaction not found.
WBE_CARD_TRANSACTIONS_CHART_ACCT_INVALID=The selected reporting account does not exist or is no longer active.
WBE_CARD_TRANSACTIONS_FIELD_UNKNOWN=Unsupported patch field for this resource.
WBE_CARD_TRANSACTIONS_PERIOD_INVALID=The selected date range is not allowed. From-date must be on or before To-date and the span must be at most 365 days.
```

Each error maps to HTTP 200 (BusinessException) per the existing rule — the frontend reads `status.error`, not the HTTP code.

Validation failures from `@Valid` map to `WBE_VALIDATION`; malformed JSON to `WBE_BAD_REQUEST`. Both are already wired in the global handler.

## 7 · Transaction boundaries

| Method | `@Transactional` | Reason |
|---|---|---|
| `list(...)`      | `readOnly = true`  | Two reads (page + totals) under one read snapshot. |
| `cards(...)`     | `readOnly = true`  | Single read. |
| `patch(...)`     | default (RW)       | One UPDATE + one audit append. Audit append uses `@Transactional(propagation = REQUIRES_NEW)` inside `AuditLogService.append` — survives outer rollback. |
| `updateReportingAccount(...)` | default (RW) | Same pattern. |
| `export(...)`    | **no transaction at the orchestrator** ([wecms-api/CLAUDE.md §"Excel export"](../../../CLAUDE.md)). Inner `service.list(...)` opens its own RO scope. |

The legacy `IdoUtils.commitAndCloseQuietly(idoCon)` calls in the patch JSPs ([_u001_act.jsp:90](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_u001_act.jsp#L90), [_u002_act.jsp:93](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/card/card_0001_001_u002_act.jsp#L93)) are replaced by Spring's transaction manager — no manual `idoCon.beginTransaction()` / `commit` / `close` shenanigans.

## 8 · Paging & sort contract

| Wire param | Type | Default | Notes |
|---|---|---|---|
| `page_no` | int ≥ 1 | 1 | One-based on the wire; converted to zero-based for `PageRequest.of(...)`. |
| `page_per_cnt` | int 1..100 | 15 | Matches legacy default ([_view.jsp:496-498](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/card/card_0001_001_view.jsp#L496-L498)). |
| `sort_field` | string (FIELD_ID) | (unset → default ORDER BY) | Validated via `CardTrnsHstrSortField.parse(...)` — unknown values silently fall back to default. |
| `sort_direction` | `ASC` / `DESC` | `ASC` | Lenient parse — unknown values fall back to `ASC`. |

Default ORDER BY = `TRSC_DT DESC, TRSC_TM DESC, CARD_SEQ_NO DESC` (mirrors legacy ledger view + provides deterministic tie-break for pagination snapshots).

When a primary sort is supplied, the service appends `DEFAULT_SORT` as the tiebreaker: `Sort.by(primary).and(DEFAULT_SORT)` — same pattern as `AccountService`.

## 9 · Audit-log requirements

Default proposal (OQ-2): **enable audit** on the two write endpoints.

| Action key | Trigger | `WSVC_ID` | Payload |
|---|---|---|---|
| `PATCH_DESCRIPTION` | `PATCH /api/v1/payment/card-transactions/{cardSeqNo}` with `field=description` | `card-transactions` | `{ card_seq_no }` |
| `PATCH_PURPOSE`     | `PATCH …` with `field=purpose`              | `card-transactions` | `{ card_seq_no }` |
| `PATCH_REPORTING_ACCOUNT` | `PUT …/{cardSeqNo}/reporting-account`  | `card-transactions` | `{ card_seq_no, chart_acct_seq_no, chart_acct_cd }` |

Old values are NOT logged (legacy parity — neither `firm-banking-settings` nor `chart-of-accounts` logs old values either; the audit ledger is "who did what to which row, when" not "what was the value before"). If diff-style audit becomes a real requirement it lands as a separate refactor concern across all features at once.

## 10 · Security

- **JWT-protected** — same as every endpoint outside `/api/v1/auth/*` per [wecms-api/CLAUDE.md §"Security filter chain"](../../../CLAUDE.md).
- **Tenant scope** — every query carries `BIZ_KEY = TenantContext.currentBizKey()`. Cross-tenant access is impossible at the repository level (every method takes `bizKey` as a parameter, never reads it from the request body).
- **No `@PreAuthorize`** today — legacy parity (any authenticated tenant user can read + patch). OQ to PO for role-restricted variant.
- **`PRDT_TYPE` gate** — none. The menu is mounted for F / H / M per the DEV `MENU_AUTH_ADMN_SYS` rows; no per-product gate inside the service. The HAENGSUNG-specific `dym_area_id = 'table2'` View Setting variant is purely a column-visibility decision the frontend selects.
- **Multi-tenant filter** is the JPA-level WHERE; there is no row-level security policy in PostgreSQL today, so the filter is enforced exclusively in the application layer (consistent with the rest of the refactor).
- **Trace id + log MDC** — `TraceIdFilter` already populates MDC; every log line in this domain carries the request-scoped `traceId`.

## 11 · Sort whitelist enum

```java
package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.web.SortableField;
import java.util.Optional;

public enum CardTrnsHstrSortField implements SortableField {
    TRSC_DT             ("trscDt"),
    CARD_NO             ("cardNo"),
    MERCH_NM            ("merchNm"),
    CARD_NICK_NM        ("cardNickNm"),
    CARD_HLDR_NM        ("cardHldrNm"),
    CCY_CD              ("ccyCd"),
    TRSC_AMT            ("trscAmt"),
    STTS                ("prchStts"),               // wire FIELD_ID = STTS; entity property = prchStts
    CARD_TRNS_USAGE_NM  ("cardTrnsUsageCd"),        // sort by code (the underlying column); display name comes from join
    CHART_ACCT_NM       ("chartAcctCd"),            // same — sort by code; display name is composed
    CONT                ("cont");

    private final String entityProperty;

    CardTrnsHstrSortField(String entityProperty) { this.entityProperty = entityProperty; }

    @Override public String entityProperty() { return entityProperty; }

    public static Optional<CardTrnsHstrSortField> parse(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        try { return Optional.of(valueOf(raw)); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
```

Constants mirror the View Setting `FIELD_ID` values exactly. `CHECKBOX_1`, `CHECKBOX_2`, `MEMO`, `MEMO_2`, `DISP_DOC_ID`, `APPR_DOC_NO` are deliberately NOT in the enum — they're either special FIELD_IDs (silent-drop by the framework) or projection-only columns the page renders but does not sort on (`SORT_YN='N'` in the seed).

The legacy DEV seed has `SORT_YN='Y'` on most non-special columns including `DISP_DOC_ID` and `APPR_DOC_NO`. The refactor seed (see [database/card-transactions.md](../database/card-transactions.md)) flips those to `'N'` because the underlying columns are projection-side artefacts (JSON-aggregated linked-docs / EXPT_RESOL_MSTR.DOC_NO) — sorting by them would mean sorting by a JSONB indicator or a sub-select, neither of which is in the enum-property map. Matches the V41 precedent for `VAS_ACCOUNT.SORT_YN='N'` ([wecms-api/CLAUDE.md §"Database (LOCAL) — V41"](../../../CLAUDE.md)).

## 12 · Excel export — `CardTransactionsExportProvider`

```java
@Component
@RequiredArgsConstructor
public class CardTransactionsExportProvider implements ExportRowProvider {

    private final CardTransactionsService service;

    @Override public String screenId() { return "card-transactions"; }

    @Override public Set<String> allowedFieldIds() {
        return Set.of("TRSC_DT", "CARD_NO", "MERCH_NM", "CARD_NICK_NM", "CARD_HLDR_NM",
                      "CCY_CD", "TRSC_AMT", "CHART_ACCT_NM", "STTS",
                      "CARD_TRNS_USAGE_NM", "DISP_DOC_ID", "CONT", "APPR_DOC_NO");
    }

    @Override public ExportFetch fetch(ExcelExportRequest req) {
        // Decode filters map; delegate to service.list using a max page size.
        CardTransactionListRequest listReq = mapFiltersToListRequest(req);
        CardTransactionListResponse data   = service.list(listReq);

        List<Map<String, Object>> rows = data.rows().stream()
            .map(r -> Map.<String, Object>of(
                "TRSC_DT",            r.trscDt() + " " + safeTime(r.trscTm()),
                "CARD_NO",            r.cardNo(),
                "MERCH_NM",           r.merchNm(),
                "CARD_NICK_NM",       r.cardNickNm(),
                "CARD_HLDR_NM",       r.cardHldrNm(),
                "CCY_CD",             r.ccyCd(),
                "TRSC_AMT",           r.trscAmt(),
                "CHART_ACCT_NM",      r.chartAcctNm(),
                "STTS",               r.stts(),
                "CARD_TRNS_USAGE_NM", r.cardTrnsUsageNm(),
                "DISP_DOC_ID",        firstDispDocId(r.linkedDocs()),
                "CONT",               r.cont(),
                "APPR_DOC_NO",        r.apprDocNo()
            )).toList();

        String subHeader = composeSubHeader(data);   // see §4.5 of features doc
        return new ExportFetch(rows, subHeader);
    }
}
```

The export response intersection rule still applies — even if the request asks for `CHECKBOX_1` or `MEMO`, the framework drops them (`SPECIAL_FIELD_IDS`) and `allowedFieldIds()` here drops anything else. Tenant scope is inherited from the inner `service.list(...)` call.

## 13 · i18n keys this feature adds

Under `payment.card_transactions.*` namespace, in `messages.properties` (EN baseline). Locale rollout via `messages_ko.properties` + `messages_vi.properties` as translations are verified.

| Key | EN |
|---|---|
| `payment.card_transactions.title` | Card Transactions |
| `payment.card_transactions.status.1` | Posted (TBD — confirm with OQ-3) |
| `payment.card_transactions.link.all` | All |
| `payment.card_transactions.link.linked` | Linked |
| `payment.card_transactions.link.unlinked` | Unlinked |
| `payment.card_transactions.field.date_time` | Date/Time |
| `payment.card_transactions.field.card_number` | Card Number |
| `payment.card_transactions.field.merchant` | Merchant |
| `payment.card_transactions.field.card_nickname` | Card Nickname |
| `payment.card_transactions.field.cardholder` | Cardholder |
| `payment.card_transactions.field.currency` | Currency Code |
| `payment.card_transactions.field.amount` | Amount |
| `payment.card_transactions.field.reporting_account` | Reporting Account |
| `payment.card_transactions.field.status` | Status |
| `payment.card_transactions.field.purpose` | Purpose |
| `payment.card_transactions.field.link_reference` | Link Reference |
| `payment.card_transactions.field.description` | Description |
| `payment.card_transactions.field.approval_reference` | Approval Ref. |

Frontend mirrors these under `card_transactions.*` in `locales/{en,ko,vi}.json` — by design backend `messages.properties` carries the **wire-resolution** path (error messages, `stts_key`), frontend locale files carry the **UI text** path. No double-source-of-truth.

## 14 · Observability

- **Log level — `info`** for service entry + audit append; **`warn`** for `BusinessException`; **`error`** for unexpected exceptions (caught at the global handler).
- **Log tag / MDC** — every log line in this domain logs via SLF4J under `com.kosign.wecms.domain.payment.cardtransactions`. The MDC `traceId` from `TraceIdFilter` propagates automatically.
- **No `System.out.println`** — `_u002_act.jsp:75` legacy debug print ("Chart Account Info: ...") is dropped; the refactor's audit-log row is the structured equivalent.

## 15 · Build / dependency impact

No new Gradle dependency. The export pipeline uses Apache POI which is already in `build.gradle`. JSONB → Java mapping for the `linked_docs` / `approvers` columns is handled via Jackson's existing `ObjectMapper` (no PostgreSQL JSONB extension needed at the JPA layer — the projection reads the column as `String` and the row mapper deserialises).

## 16 · Out of scope for this design (handled elsewhere)

- The scraper batch — stays on the JEX side under the existing coexistence carve-out.
- Side-memo CRUD — separate `memo` slug (OQ-6).
- Expense Resolution write-path — separate `expense-resolution` slug (OQ-1).
- Chart-of-accounts tree popover — reuses the existing `chart-of-accounts/tree` endpoint (OQ-4).
- Print rendering — `lib/print.ts` lives on the frontend; backend does not generate print HTML.

## 17 · Open questions (backend-specific)

| ID | Question | Default |
|---|---|---|
| OQ-B1 | Period cap — legacy `validatePeriod` has an undocumented year cap. Confirm 365 days? | 365 days (`WBE_CARD_TRANSACTIONS_PERIOD_INVALID`). |
| OQ-B2 | `CardMstr.actv_yn` filter on the cards dropdown — legacy comment-out preserved? | Preserve legacy behaviour (no filter). Mirrors migration §10 OQ-A. |
| OQ-B3 | Should `findLedgerPage` return `appr_dv` resolved to a human label, or the raw 2-char code? | Ship raw code + sibling `appr_dv_key` once the value set is documented. Until then, raw code only. |
| OQ-B4 | Concurrency on `PATCH_REPORTING_ACCOUNT` — a tenant user editing the same row twice within a tick. Optimistic-lock via `MOD_DTM`? | No — last-write-wins matches legacy. |

Cross-reference master OQ list: [docs/refactor-wecms/features/card-transactions.md §11](../../features/payment/card-transactions.md).
