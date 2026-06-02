# Approval Box — Backend Design (Spring Boot 3.5.13)

**Feature slug:** `approval-box`
**Module:** `payment`
**Backend package:** `com.kosign.wecms.domain.payment.approvalbox`
**REST base:** `/api/v1/payment/approval-box`
**Audit `WSVC_ID` slug:** `approval-box` (only if/when an audit-writing endpoint is added — the list endpoint does not write audit per [discovery.md](discovery.md) §10 OQ-4).
**View Setting `WEB_SVC_ID`:** `approval-box`
**Date:** 2026-05-13
**Status:** Design only — no Java code in `wecms-api/` yet.

Companion docs: [discovery.md](discovery.md), [design-frontend.md](design-frontend.md), [database-impact.md](database-impact.md), [api-flow.md](api-flow.md), [migration-plan.md](migration-plan.md).
Rules source: [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"API Naming & Serialization Standards", §"Architecture Rules", §"Refactor identifier naming".

---

## 1 · REST URI map (slug-based, lowercase snake_case wire)

> **Hard rule.** Every URL segment, package, class, DTO field-on-wire, log tag, error code, and View Setting `WEB_SVC_ID` uses the **slug** `approval-box`. Legacy ids (`resl_0001_001`) appear only in Javadoc citations.

### 1.1 · In-scope this MR (read-only)

| Method | URI | Auth | Returns | Replaces JEX |
|---|---|---|---|---|
| `GET` | `/api/v1/payment/approval-box` | JWT | `ApiResponse<ApprovalBoxListResponse>` | `resl_0001_001_r001` |
| `POST` | `/api/v1/payment/approval-box/export` | JWT | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` (binary) | OSMC client-side Excel build at [resl_0001_001_osmc.ts:300-306](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L300) |

### 1.2 · Deferred to follow-up MRs (documented here for completeness — DO NOT implement in this MR)

| Method | URI | Replaces JEX |
|---|---|---|
| `GET` | `/api/v1/payment/approval-box/{apprSeqNo}` | `comm_0195_001` (detail popup read) |
| `POST` | `/api/v1/payment/approval-box/{apprSeqNo}/approve` | `comm_0195_001_uxxx` (approve action) |
| `POST` | `/api/v1/payment/approval-box/{apprSeqNo}/reject` | `comm_0195_001_uxxx` (reject action) |
| `POST` | `/api/v1/payment/approval-box/bulk-approve` | not implemented in legacy — depends on OQ-2 |
| `DELETE` | `/api/v1/payment/approval-box/{apprSeqNo}` | `resl_0001_001_d001` |
| `GET` | `/api/v1/payment/approval-box/{apprSeqNo}/files/{fileId}/download` | `comm_0192_*` (file-download is owned by detail popup) |

URI form rationale: `payment` is the module, `approval-box` is the slug (2 kebab segments ≥ 2 minimum, matches `^[a-z][a-z0-9]*(-[a-z0-9]+)+$`). Both halves consistent with the existing precedents `/api/v1/settings/firm-banking-settings/`, `/api/v1/banking/accounts/export`.

---

## 2 · Request / Response shapes

### 2.1 · `GET /api/v1/payment/approval-box`

Query parameters (validated on the controller, all lower snake_case on the wire):

| Param | Java field | Type | Default | Validation | Source in JEX |
|---|---|---|---|---|---|
| `from_dt` | `fromDt` | `String` (YYYYMMDD) | required | `@NotBlank @Pattern("\\d{8}")` | `FROM_DT` ([resl_0001_001_r001_act.jsp:56](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L56)) |
| `to_dt` | `toDt` | `String` (YYYYMMDD) | required | `@NotBlank @Pattern("\\d{8}")`, `toDt >= fromDt` enforced in service | `TO_DT` ([:57](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L57)) |
| `srch_wd` | `srchWd` | `String` | `""` | `@Size(max=100)` (legacy length, [WSVC.resl_0001_001_r001.xml:33](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.resl_0001_001_r001.xml#L33)) | `SRCH_WD` |
| `appr_type_cd` | `apprTypeCd` | `String` | `""` (= all) | `@Pattern("^$|0[0-3]$")` — empty / `00`..`03` | `APPR_TYPE_CD` |
| `page_no` | `pageNo` | `Integer` | `1` | `@Min(1)` | `PAGE_NO` |
| `page_per_cnt` | `pagePerCnt` | `Integer` | `15` | `@Min(1) @Max(200)` (frontend dropdown caps at 100 — see [resl_0001_001_view_osmc.jsp:447-451](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view_osmc.jsp#L447), but we keep 200 server-side ceiling for export-CSV) | `PAGE_PER_CNT` |
| `sort_field` | `sortField` | `String` | `"REG_DTM"` | Whitelist enum (see §6) | sortable `th[data-sort=…]` ([resl_0001_001_view.jsp:189-198](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view.jsp#L189)) |
| `sort_direction` | `sortDirection` | `String` | `"DESC"` | `@Pattern("^(ASC|DESC)$")` | URL suffix on `resl_0001_001_r001.jct${paramSort}` ([resl_0001_001_osmc.ts:247-253](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L247)) |

Successful 200 envelope:
```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "total_count": 18,
    "tab_counts": {
      "pending":     2,
      "in_progress": 1,
      "approved":    2,
      "rejected":    14
    },
    "currency_totals": [
      { "currency": "VND", "amount": "10000000.00" },
      { "currency": "USD", "amount": "0.00" },
      { "currency": "KRW", "amount": "0.00" }
    ],
    "rows": [
      {
        "appr_seq_no":         "1540",
        "doc_no":              "VO_260511_002",
        "title":               "Test",
        "post_title":          "January - Request for internal transfer",
        "appr_dv":             "35",
        "type_label":          "internal_transfer",
        "currency":            "VND",
        "tot_pay_amt":         "2000000",
        "pay_date":            "20260512",
        "scheduled_date":      "20260512",
        "draft_user_id":       "U_12345",
        "draft_user_nm":       "LE THI HAI YEN",
        "reg_dtm":             "20260511075547",
        "appr_stts":           "I",
        "apprvr_tot_cnt":      3,
        "apprvr_appr_tot_cnt": 1,
        "pay_end_yn":          "N",
        "file_nm":             "WeCMS_Internal_Transfer_upload_EN",
        "approvers": [
          { "apprvr_line_no": 1, "apprvr_id": "U_001", "apprvr_nm": "Liam Hawthorne",
            "apprvr_stts": "A", "appr_dtm": "20260512090000",
            "profile_img_url": "/api/v1/users/U_001/profile-image" },
          { "apprvr_line_no": 2, "apprvr_id": "U_002", "apprvr_nm": "Maya Sinclair",
            "apprvr_stts": "P", "appr_dtm": null,
            "profile_img_url": "/api/v1/users/U_002/profile-image" }
        ]
      }
    ]
  }
}
```

Key wire-shape decisions:

- **One amount column** (`tot_pay_amt`) instead of legacy split `TOT_PAY_KHR_AMT` / `TOT_PAY_USD_AMT`. The currency is carried separately. This collapses the legacy currency-by-column model into the standard one-currency-per-row model used in `account-inquiry-vnd` and `firm-banking-history`.
- **`type_label` is a stable enum slug** (`payroll` / `vendor` / `expense` / `corporate_card` / `overseas` / `internal_transfer`), not a localised string. Frontend resolves the label via i18n — this is the same pattern as `erp-transaction-history` (`direction` / `type` keys returned, FE renders via `t(key)`).
- **`appr_stts` is one of `P` / `I` / `C` / `R`** — Pending / In Progress / Completed (Approved) / Rejected — kept UPPERCASE as a stable identifier (per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Stable identifier values stay UPPERCASE").
- **`apprvr_stts` per approver** is `P` / `A` / `R` / `W` / `S`. Same UPPERCASE rule.
- **No `rec`, `rec2`, `rec3`** — those legacy container keys disappear. Approvers nest under `approvers`. Files (deferred) would nest under `files` once the detail endpoint ships.
- **`profile_img_url` is a path, not the legacy JEX `szon_0012_003_r002.act?USER_ID=…`** — the refactor exposes a new `GET /api/v1/users/{userId}/profile-image` (out of scope for this MR; for now the frontend can fall back to a placeholder avatar).

### 2.2 · `POST /api/v1/payment/approval-box/export`

Body matches the shared `ExcelExportRequest` shape used by `/api/v1/banking/accounts/export` ([wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Excel export"). The export pipeline reuses the **same filter+sort path** as `list`, deferring to an `ApprovalBoxExportProvider` `@Component` implementing `ExportRowProvider`. Response is streamed XLSX (Apache POI SXSSF).

---

## 3 · Java class layout

```
com.kosign.wecms.domain.payment.approvalbox
├── controller/
│   └── ApprovalBoxController.java         @RestController @RequestMapping("/api/v1/payment/approval-box")
├── service/
│   ├── ApprovalBoxService.java            @Service (read-only @Transactional(readOnly=true))
│   └── ApprovalBoxSortField.java          enum implements SortableField  (whitelist; see §6)
├── repository/
│   ├── ApprovalBoxRepository.java         JPA repo on ExptResolMstr
│   └── ApprovalBoxProjection.java         interface projection (Spring Data — returns the joined columns)
├── entity/
│   ├── ExptResolMstr.java                 @Table(name="EXPT_RESOL_MSTR")  legacy schema parity
│   ├── ExptResolMstrId.java               @Embeddable composite (bizKey, apprSeqNo) — use @IdClass per CLAUDE.md
│   ├── ExptResolApprvrInfm.java           @Table(name="EXPT_RESOL_APPRVR_INFM")
│   └── ExptResolApprvrInfmId.java         composite (bizKey, apprSeqNo, apprvrId)
├── dto/
│   ├── ApprovalBoxRow.java                Java record — wire shape from §2.1 (row)
│   ├── ApprovalBoxApprover.java           Java record — approver chain item
│   ├── ApprovalBoxTabCounts.java          Java record — tab_counts shape
│   ├── ApprovalBoxCurrencyTotal.java      Java record — currency_totals item
│   ├── ApprovalBoxListResponse.java       Java record — envelope around rows + totals
│   └── ApprovalBoxListRequest.java        Java record carrying the query params (with @Valid)
└── export/
    └── ApprovalBoxExportProvider.java     @Component implements ExportRowProvider (see §8)
```

Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) "lightweight domain-driven packaging — NOT full tactical DDD. controller → service → repository → entity + dto. No cross-layer shortcuts."

---

## 4 · Controller (thin)

```java
@RestController
@RequestMapping("/api/v1/payment/approval-box")
@RequiredArgsConstructor
public class ApprovalBoxController {

    private final ApprovalBoxService service;

    /** Mirrors resl_0001_001_r001_act.jsp:280-352. */
    @GetMapping
    public ApiResponse<ApprovalBoxListResponse> list(@Valid @ModelAttribute ApprovalBoxListRequest req) {
        return ApiResponse.ok(service.list(req));
    }

    // Export endpoint follows the firm-banking-history precedent — body shape comes from common/export.
    @PostMapping("/export")
    public ResponseEntity<Resource> export(@Valid @RequestBody ExcelExportRequest req) {
        return excelExportService.export(SCREEN_ID, req);   // SCREEN_ID = "approval-box"
    }
}
```

Controller responsibilities (thin, per CLAUDE.md):

- Parse request (`@Valid @ModelAttribute` for query params; nothing else).
- Wrap result in `ApiResponse.ok(...)`.
- **NEVER** read `TenantContext` directly — service does that.
- **NEVER** return a `Map<String,Object>` — typed DTO only.
- Throw `BusinessException` via the global handler — no `try/catch` in the controller.

---

## 5 · Service

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApprovalBoxService {

    private final ApprovalBoxRepository repo;

    /** Replaces resl_0001_001_r001_act.jsp:280-352. */
    public ApprovalBoxListResponse list(ApprovalBoxListRequest req) {
        validatePeriod(req);                          // resl_0001_001_r001_act.jsp:54-61

        String bizKey   = TenantContext.currentBizKey();
        String userId   = TenantContext.currentUserId();
        boolean isAdmin = "ADMIN".equals(TenantContext.currentAuthGrCd());

        Page<ApprovalBoxProjection> page = repo.findApprovalBoxList(
            bizKey, userId, isAdmin,
            req.fromDt(), req.toDt(), req.srchWd(), req.apprTypeCd(),
            ApprovalBoxSortField.valueOfWhitelisted(req.sortField()),
            req.sortDirection(),
            PageRequest.of(req.pageNo() - 1, req.pagePerCnt())
        );

        List<ApprovalBoxRow> rows = page.getContent().stream()
            .map(this::toRow)                          // includes approver-chain expansion
            .toList();

        ApprovalBoxTabCounts tabCounts = repo.fetchTabCounts(bizKey, userId, isAdmin,
            req.fromDt(), req.toDt(), req.srchWd());
        List<ApprovalBoxCurrencyTotal> currencyTotals = repo.fetchCurrencyTotals(bizKey, userId, isAdmin,
            req.fromDt(), req.toDt(), req.srchWd(), req.apprTypeCd());

        return new ApprovalBoxListResponse(page.getTotalElements(), tabCounts, currencyTotals, rows);
    }

    private void validatePeriod(ApprovalBoxListRequest req) {
        // Mirrors ValidationHelper.validateDate8 + validatePeriod at resl_0001_001_r001_act.jsp:58-60
        if (req.fromDt().compareTo(req.toDt()) > 0) {
            throw new BusinessException("WBE_APPROVAL_BOX_INVALID_PERIOD");
        }
    }

    /** Status derivation per discovery.md OQ-3 option (a) — service-computed. */
    private String deriveApprStts(ApprovalBoxProjection p) {
        if (p.getApprTypeCd().equals("01")) return "C";   // Completed (Approved)
        if (p.getApprTypeCd().equals("02")) return "R";   // Rejected
        if (p.getApprTypeCd().equals("03")) return "I";   // In progress
        return "P";                                       // Pending (= "00")
    }

    private ApprovalBoxRow toRow(ApprovalBoxProjection p) {
        List<ApprovalBoxApprover> approvers = repo.findApproversByDocument(p.getBizKey(), p.getApprSeqNo());
        return new ApprovalBoxRow(
            p.getApprSeqNo(), p.getDocNo(), p.getTitl(), p.getPostTitle(),
            p.getApprDv(), ApprDv.fromCode(p.getApprDv()).slug(),       // "internal_transfer" etc.
            p.getCcyCd(), p.getTotPayAmt(),
            p.getPayDate(), p.getPayDate(),                            // scheduled_date alias
            p.getDraftUserId(), p.getDraftUserNm(),
            p.getRegDtm(),
            deriveApprStts(p),
            p.getApprvrTotCnt(), p.getApprvrApprTotCnt(),
            p.getPayEndYn(), p.getFileNm(),
            approvers
        );
    }
}
```

Mutating endpoints (deferred) will use `@Transactional` (not `readOnly=true`) and write `AUDIT_LOGS_INFM` via the existing `AuditLogService.append(...)` carrying `WSVC_ID = "approval-box"` per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Refactor identifier naming".

---

## 6 · Sort whitelist (`ApprovalBoxSortField`)

```java
public enum ApprovalBoxSortField implements SortableField {
    REG_DTM        ("erm.reg_dtm"),
    DOC_NM         ("doc_nm"),         // computed alias from query
    DOC_NO         ("erm.doc_no"),
    TITL           ("erm.titl"),
    TOT_PAY_AMT    ("tot_pay_amt"),    // computed alias
    PAY_END_YN     ("erm.pay_end_yn"),
    PAY_DATE       ("erm.pay_date"),
    DRAFT_USER_NM  ("erm.draft_user_nm"),
    APPR_DV        ("erm.appr_dv"),
    CURRENCY       ("ccy_cd");

    public static ApprovalBoxSortField valueOfWhitelisted(String input) {
        try { return valueOf(input); } catch (IllegalArgumentException e) {
            throw new BusinessException("WBE_APPROVAL_BOX_INVALID_SORT");
        }
    }

    private final String column;
    @Override public String column() { return column; }
}
```

These enum constants double as the **View Setting `FIELD_ID` values**. The frontend column-config popover sends them verbatim. Special FIELD_IDs `CHECKBOX` and `ETC` are never sortable per [wecms-web/CLAUDE.md](../../../../../wecms-web/CLAUDE.md) "Sort cycle". Whitelist match: legacy frontend `data-sort` values at [resl_0001_001_view.jsp:189-198](../../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/resl/resl_0001_001_view.jsp#L189) and OSMC TS at [resl_0001_001_osmc.ts:104-107](../../../../../WECMS%20DEV/WECMS_STATIC/ts-src/js/jex/wabooks/resl/resl_0001_001_osmc.ts#L104). OQ-5 — confirm with PO.

---

## 7 · Repository

`ApprovalBoxRepository extends JpaRepository<ExptResolMstr, ExptResolMstrId>` with custom methods backed by JPQL.

Key methods (each carries `bizKey` as the first parameter — never `%`):

```java
public interface ApprovalBoxRepository extends JpaRepository<ExptResolMstr, ExptResolMstrId> {

    @Query("""
        SELECT erm.bizKey      AS bizKey,
               erm.apprSeqNo   AS apprSeqNo,
               erm.docNo       AS docNo,
               erm.titl        AS titl,
               erm.apprDv      AS apprDv,
               erm.payDate     AS payDate,
               erm.payEndYn    AS payEndYn,
               erm.draftUserId AS draftUserId,
               erm.draftUserNm AS draftUserNm,
               erm.regDtm      AS regDtm,
               erm.apprTypeCd  AS apprTypeCd,
               COALESCE(opm.trnfCcyCd, sps.ccyCd, ops.ccyCd, 'VND') AS ccyCd,
               COALESCE(sps.titl, ops.titl)                          AS fileNm,
               bpi.postTitle  AS postTitle,
               CASE WHEN erm.apprDv = '33'
                    THEN (SELECT COALESCE(SUM(c.trscAmt),0) FROM CardTrnsHstr c
                          WHERE c.bizKey = erm.bizKey AND c.apprSeqNo = erm.apprSeqNo)
                    ELSE erm.totPayKhrAmt END                        AS totPayAmt,
               (SELECT COUNT(a) FROM ExptResolApprvrInfm a
                WHERE a.bizKey = erm.bizKey AND a.apprSeqNo = erm.apprSeqNo) AS apprvrTotCnt,
               (SELECT COUNT(a) FROM ExptResolApprvrInfm a
                WHERE a.bizKey = erm.bizKey AND a.apprSeqNo = erm.apprSeqNo AND a.apprvrStts = 'A')
                                                                     AS apprvrApprTotCnt
        FROM ExptResolMstr erm
        LEFT JOIN BltbPostInfm    bpi ON bpi.bizKey = erm.bizKey AND bpi.apprSeqNo = erm.apprSeqNo
        LEFT JOIN OvrsPymnMstr    opm ON opm.bizKey = erm.bizKey AND opm.apprSeqNo = erm.apprSeqNo
        LEFT JOIN ScdulPymnSumr   sps ON sps.bizKey = erm.bizKey AND sps.apprSeqNo = erm.apprSeqNo
        LEFT JOIN OvrsPymnSumr    ops ON ops.bizKey = erm.bizKey AND ops.apprSeqNo = erm.apprSeqNo
        WHERE erm.bizKey  = :bizKey
          AND erm.delYn   = 'N'
          AND erm.apprDv  IN ('21','31','32','33','34','35')
          AND erm.draftDate BETWEEN :fromDt AND :toDt
          AND (COALESCE(bpi.refBltbCd,'') = '' AND COALESCE(CAST(bpi.refPostSeqNo AS string),'') = '')
          AND (:apprTypeCd = '' OR erm.apprTypeCd = :apprTypeCd)
          AND (:srchWd = ''
               OR LOWER(erm.docNm) LIKE LOWER(CONCAT('%', :srchWd, '%'))
               OR LOWER(erm.titl)  LIKE LOWER(CONCAT('%', :srchWd, '%'))
               OR LOWER(erm.docNo) LIKE LOWER(CONCAT('%', :srchWd, '%')))
          AND ( :isAdmin = TRUE
                OR EXISTS (SELECT 1 FROM ScdulPymnSumr s
                           WHERE s.bizKey = erm.bizKey AND s.apprSeqNo = erm.apprSeqNo AND s.regId = :userId)
                OR EXISTS (SELECT 1 FROM OvrsPymnSumr  o
                           WHERE o.bizKey = erm.bizKey AND o.apprSeqNo = erm.apprSeqNo AND o.regId = :userId)
                OR EXISTS (SELECT 1 FROM CardTrnsHstr  c
                           WHERE c.bizKey = erm.bizKey AND c.apprSeqNo = erm.apprSeqNo AND c.regId = :userId)
                OR erm.regId = :userId
                OR EXISTS (SELECT 1 FROM ExptResolApprvrInfm a
                           WHERE a.bizKey = erm.bizKey AND a.apprSeqNo = erm.apprSeqNo AND a.apprvrId = :userId) )
    """)
    Page<ApprovalBoxProjection> findApprovalBoxList(
        @Param("bizKey")     String bizKey,
        @Param("userId")     String userId,
        @Param("isAdmin")    boolean isAdmin,
        @Param("fromDt")     String fromDt,
        @Param("toDt")       String toDt,
        @Param("srchWd")     String srchWd,
        @Param("apprTypeCd") String apprTypeCd,
        ApprovalBoxSortField sortField,   // bound into ORDER BY via @QueryRewriter or Sort.by(...)
        String sortDirection,
        Pageable pageable
    );

    @Query("""
        SELECT a.apprvrLineNo AS apprvrLineNo,
               a.apprvrId     AS apprvrId,
               a.apprvrNm     AS apprvrNm,
               a.apprvrStts   AS apprvrStts,
               a.apprDtm      AS apprDtm
        FROM ExptResolApprvrInfm a
        WHERE a.bizKey = :bizKey AND a.apprSeqNo = :apprSeqNo
        ORDER BY a.apprvrLineNo ASC
    """)
    List<ApprovalBoxApprover> findApproversByDocument(@Param("bizKey") String bizKey,
                                                     @Param("apprSeqNo") String apprSeqNo);

    /** Tab counts ignore apprTypeCd. Mirrors EXPT_RESOL_MSTR_R003_CNT. */
    ApprovalBoxTabCounts fetchTabCounts(String bizKey, String userId, boolean isAdmin,
                                         String fromDt, String toDt, String srchWd);

    /** Currency totals respect apprTypeCd. */
    List<ApprovalBoxCurrencyTotal> fetchCurrencyTotals(String bizKey, String userId, boolean isAdmin,
                                                       String fromDt, String toDt, String srchWd, String apprTypeCd);
}
```

Notes:

- The list query mirrors [IDO.EXPT_RESOL_MSTR_R003.xml](../../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.EXPT_RESOL_MSTR_R003.xml) — every join, every filter line traced.
- The legacy `wb_get_appr_stts(...)` is dropped; status is derived in `ApprovalBoxService.deriveApprStts` (OQ-3 option (a)).
- The legacy `wb_get_expt_resol_mstr_doc_nm(...)` is dropped; the frontend renders the type label via i18n key `approval_box.type.<slug>` from the `type_label` field.
- The legacy memo helpers (`wb_get_memo_cont`, `wb_get_memo_color`) are dropped — OSMC layout does not show sticky notes.
- Approver chain is fetched per-row via a second small query (1 + N pattern) — acceptable for page sizes ≤ 100. If profiling shows hot path, switch to a single LEFT JOIN with `@Query` and result-set mapping in service.

---

## 8 · Entities (legacy schema parity)

Per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Legacy Schema Parity Rule" — every column from the legacy DEV table maps 1:1. Audit columns inherit from `BaseEntity`.

### 8.1 · `ExptResolMstr`

```java
@Entity
@Table(name = "EXPT_RESOL_MSTR")
@IdClass(ExptResolMstrId.class)
@Getter @Setter
public class ExptResolMstr extends BaseEntity {

    @Id @Column(name = "BIZ_KEY")     private String bizKey;
    @Id @Column(name = "APPR_SEQ_NO") private String apprSeqNo;

    @Column(name = "APPR_DV")       private String apprDv;       // '21'/'31'/'32'/'33'/'34'/'35' for AB
    @Column(name = "APPR_STS")      private String apprSts;
    @Column(name = "APPR_TYPE_CD")  private String apprTypeCd;   // '00'..'03' tab key
    @Column(name = "DRAFT_DATE")    private String draftDate;
    @Column(name = "DRAFT_TIME")    private String draftTime;
    @Column(name = "DRAFT_USER_ID") private String draftUserId;
    @Column(name = "DRAFT_USER_NM") private String draftUserNm;
    @Column(name = "TITL")          private String titl;
    @Column(name = "CONT")          private String cont;
    @Column(name = "DOC_NO")        private String docNo;
    @Column(name = "DOC_NM")        private String docNm;
    @Column(name = "TOT_PAY_VND_AMT") private BigDecimal totPayVndAmt;
    @Column(name = "TOT_PAY_USD_AMT") private BigDecimal totPayUsdAmt;
    @Column(name = "TOT_PAY_KHR_AMT") private BigDecimal totPayKhrAmt;
    @Column(name = "PAY_DATE")      private String payDate;
    @Column(name = "PAY_REQ_DATE")  private String payReqDate;
    @Column(name = "PAY_END_YN")    private String payEndYn;
    @Column(name = "PAY_END_DTM")   private String payEndDtm;
    @Column(name = "PAY_END_NM")    private String payEndNm;
    @Column(name = "DEL_YN")        private String delYn;
    @Column(name = "HAND_REG_YN")   private String handRegYn;
    @Column(name = "APPR_BOX_USE_YN")   private String apprBoxUseYn;
    @Column(name = "APPR_BOX_DISP_CNT") private String apprBoxDispCnt;
    @Column(name = "APPR_DAT_DISP_YN")  private String apprDatDispYn;
    @Column(name = "CMPN_LOGO_DISP_YN") private String cmpnLogoDispYn;
    @Column(name = "REG_DISP_YN")   private String regDispYn;
    @Column(name = "CMPN_LOGO")     private String cmpnLogo;
    @Column(name = "USER_NM")       private String userNm;
    @Column(name = "REF_DATA_CNT")  private BigDecimal refDataCnt;
    @Column(name = "PAY_YN")        private String payYn;
    @Column(name = "ADV_PAYM_SEQ")  private BigDecimal advPaymSeq;
    @Column(name = "ADV_PAYM_NM")   private String advPaymNm;
    @Column(name = "BANK_CD")       private String bankCd;
    @Column(name = "BANK_NM")       private String bankNm;
    @Column(name = "ACCT_NO")       private String acctNo;
    @Column(name = "APLY_EXCH_RATE")    private BigDecimal aplyExchRate;
    @Column(name = "REG_CHNL")      private String regChnl;
    @Column(name = "PC_MOD_YN")     private String pcModYn;
    @Column(name = "APPR_FUND_TP")  private String apprFundTp;

    // REG_ID / REG_DTM / MOD_ID / MOD_DTM inherited from BaseEntity
}
```

Full column set matches [discovery.md](discovery.md) §3.1 and the DEV `information_schema.columns` query result. `@IdClass(ExptResolMstrId.class)` mirrors the legacy [ExptResolMstrPk.java](../../../../../WECMS%20DEV/WECMS/src/wabooks/entity/ExptResolMstrPk.java).

### 8.2 · `ExptResolApprvrInfm`

```java
@Entity
@Table(name = "EXPT_RESOL_APPRVR_INFM")
@IdClass(ExptResolApprvrInfmId.class)
@Getter @Setter
public class ExptResolApprvrInfm extends BaseEntity {

    @Id @Column(name = "BIZ_KEY")      private String bizKey;
    @Id @Column(name = "APPR_SEQ_NO")  private String apprSeqNo;
    @Id @Column(name = "APPRVR_ID")    private String apprvrId;

    @Column(name = "APPRVR_STTS")    private String apprvrStts;   // 'P'/'A'/'R'/'W'/'S'
    @Column(name = "APPRVR_NM")      private String apprvrNm;
    @Column(name = "APPR_DTM")       private String apprDtm;
    @Column(name = "READ_YN")        private String readYn;
    @Column(name = "APPRVR_LINE_NO") private BigDecimal apprvrLineNo;
    @Column(name = "APPR_LINE_NO")   private BigDecimal apprLineNo;
}
```

### 8.3 · Sibling entities consumed read-only (already-owned by other modules)

`BltbPostInfm`, `OvrsPymnMstr`, `ScdulPymnSumr`, `OvrsPymnSumr`, `CardTrnsHstr` — when these aren't owned by the refactor yet, the Approval Box MR adds **minimal column projections** (`@Subselect` views, or thin JPQL accessors) instead of full entity classes. Coordination with the owning module ports is captured in [migration-plan.md](migration-plan.md) Risk #3.

---

## 9 · DTOs (records)

```java
public record ApprovalBoxListRequest(
        @NotBlank @Pattern(regexp = "\\d{8}") String fromDt,
        @NotBlank @Pattern(regexp = "\\d{8}") String toDt,
        @Size(max = 100)                       String srchWd,
        @Pattern(regexp = "^$|0[0-3]$")        String apprTypeCd,
        @Min(1)                                Integer pageNo,
        @Min(1) @Max(200)                      Integer pagePerCnt,
        String sortField,
        @Pattern(regexp = "^(ASC|DESC)$")      String sortDirection
) {
    public ApprovalBoxListRequest {
        if (pageNo == null) pageNo = 1;
        if (pagePerCnt == null) pagePerCnt = 15;
        if (srchWd == null) srchWd = "";
        if (apprTypeCd == null) apprTypeCd = "";
        if (sortField == null) sortField = "REG_DTM";
        if (sortDirection == null) sortDirection = "DESC";
    }
}

public record ApprovalBoxRow(
        String  apprSeqNo,
        String  docNo,
        String  title,
        String  postTitle,
        String  apprDv,
        String  typeLabel,                  // "internal_transfer" etc.
        String  currency,
        BigDecimal totPayAmt,
        String  payDate,
        String  scheduledDate,
        String  draftUserId,
        String  draftUserNm,
        String  regDtm,
        String  apprStts,                   // "P"/"I"/"C"/"R"
        Long    apprvrTotCnt,
        Long    apprvrApprTotCnt,
        String  payEndYn,
        String  fileNm,
        List<ApprovalBoxApprover> approvers
) {}

public record ApprovalBoxApprover(
        Integer apprvrLineNo,
        String  apprvrId,
        String  apprvrNm,
        String  apprvrStts,
        String  apprDtm,
        String  profileImgUrl
) {}

public record ApprovalBoxTabCounts(
        Long pending,
        Long inProgress,
        Long approved,
        Long rejected
) {}

public record ApprovalBoxCurrencyTotal(String currency, BigDecimal amount) {}

public record ApprovalBoxListResponse(
        Long                            totalCount,
        ApprovalBoxTabCounts            tabCounts,
        List<ApprovalBoxCurrencyTotal>  currencyTotals,
        List<ApprovalBoxRow>            rows
) {}
```

Wire-naming: every camelCase Java field auto-serializes to snake_case via the global `JacksonConfig` (`PropertyNamingStrategies.SNAKE_CASE`) per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"API Naming & Serialization Standards". No per-field `@JsonProperty`.

---

## 10 · Validation & error model

| Error code (new) | When | Maps to | Notes |
|---|---|---|---|
| `WBE_APPROVAL_BOX_INVALID_PERIOD` | `fromDt > toDt` | HTTP 400 | Mirrors `validatePeriod(...)` at [resl_0001_001_r001_act.jsp:60](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L60). |
| `WBE_APPROVAL_BOX_INVALID_SORT` | `sort_field` not in whitelist | HTTP 400 | Service throws on enum lookup. |
| `WBE_APPROVAL_BOX_INVALID_TAB` | `appr_type_cd` not in `{ '', '00', '01', '02', '03' }` | HTTP 400 | Handled by `@Pattern` then by service. |

Each new code is added to `src/main/resources/messages.properties` **before** any code throws it. Code naming follows [wecms-api/CLAUDE.md](../../../../CLAUDE.md) `WBExxxx` rule — but the refactor convention for feature-scoped non-numeric codes is `WBE_<FEATURE>_<COND>` (see precedent `WBE_FBSET_*` in firm-banking-settings). Confirm with Sonny if numeric range is preferred.

Other errors funnel through `GlobalExceptionHandler`:

- Validation failures → `WBE0002` (Bean Validation envelope).
- Unhandled → `WBE9999` (catch-all logs stack with `traceId`).

No `WBE9001` overrides here — the legacy `_act.jsp` does not surface domain-specific errors for the list path.

---

## 11 · Reusable components consumed (existing in `wecms-api`)

| Component | Path | What Approval Box reuses it for |
|---|---|---|
| `ApiResponse<T>` | [`wecms-api/src/main/java/com/kosign/wecms/common/response/ApiResponse.java`](../../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) | Success envelope. |
| `BusinessException` | [`common/exception/BusinessException.java`](../../../../src/main/java/com/kosign/wecms/common/exception/BusinessException.java) | Domain errors. |
| `GlobalExceptionHandler` | [`common/exception/GlobalExceptionHandler.java`](../../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) | `@RestControllerAdvice` mapping. |
| `MessageResolver` | [`common/i18n/MessageResolver.java`](../../../../src/main/java/com/kosign/wecms/common/i18n/MessageResolver.java) | Localised error / message resolution. |
| `BaseEntity` (audit columns) | [`common/entity/BaseEntity.java`](../../../../src/main/java/com/kosign/wecms/common/entity/BaseEntity.java) | `REG_ID`/`REG_DTM`/`MOD_ID`/`MOD_DTM` inheritance. |
| `TenantContext` | [`common/tenant/TenantContext.java`](../../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) | `currentBizKey()`, `currentUserId()`, `currentAuthGrCd()`, `currentLangCd()`, `currentPrdtType()`. |
| `JwtAuthenticationFilter` | [`common/security/JwtAuthenticationFilter.java`](../../../../src/main/java/com/kosign/wecms/common/security/JwtAuthenticationFilter.java) | Auth — already wired; the endpoint is JWT-protected by default. |
| `SortableField` interface | (already used by `ErpTrnsHistorySortField` — see [`domain/setting/erptransactionhistory/service/ErpTrnsHistorySortField.java`](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/service/ErpTrnsHistorySortField.java)) | Per-domain sort whitelist. |
| `ExcelExportService` + `ExportableScreenRegistry` + `ExcelWorkbookWriter` | `common/export/` (per [wecms-api/CLAUDE.md](../../../../CLAUDE.md) §"Excel export") | Approval Box wires one new `ApprovalBoxExportProvider`. |
| View Setting endpoints | `domain/setting` (`/api/v1/screen-options/...`) | Approval Box consumes `WEB_SVC_ID = "approval-box"` once its View Setting seed lands. |

Reference implementation to copy structure from: [`domain/setting/erptransactionhistory/`](../../../../src/main/java/com/kosign/wecms/domain/setting/erptransactionhistory/) — same shape (list + 4 tab counts + Excel export, no audit on list). The closest parity per playbook Step 5.

---

## 12 · Audit logging (`AUDIT_LOGS_INFM`)

This MR ships **no audit writes** for the Approval Box list path — the legacy `_act.jsp` writes none either. When the Approve / Reject ports land (follow-up MR), they will use `AuditLogService.append(...)` with **`WSVC_ID = "approval-box"`** per workspace [CLAUDE.md](../../../../../CLAUDE.md) §"Cross-Tree Rules → Refactor identifier naming". No legacy `WSVC_ID = "resl_0001_001"` write — that's a forbidden surface per the 2026-05-09 cutover.

---

## 13 · Behaviour deltas from JEX (with justification)

| # | Delta | JEX behaviour | Refactor behaviour | Reason |
|---|---|---|---|---|
| D1 | Currency model | Two amount columns on the wire (`TOT_PAY_KHR_AMT`, `TOT_PAY_USD_AMT`); the `_KHR_` one is repurposed for VN tenants to carry VND. | Single `tot_pay_amt` + `currency` per row. | Removes legacy KHR-overload hack. Mirrors `account-inquiry-vnd`. |
| D2 | Type label | Server returns `APPR_DV` digit + label resolved by `wb_get_expt_resol_mstr_doc_nm`. | Server returns `appr_dv` digit + stable `type_label` slug; FE i18n resolves the visible string. | Drops new `wb_*` dependency. Same pattern as `erp-transaction-history`. |
| D3 | `APPR_STTS` source | DB function `wb_get_appr_stts(...)`. | Java derivation from `appr_type_cd`. | DB function policy. OQ-3. |
| D4 | Memo columns | Returned (`MEMO`, `COLOR` per row). | Dropped from response. | OSMC view does not show sticky notes; legacy Cambodia view does. |
| D5 | Translate-search | VN tenants get `wb_get_translate_text(TITL)` LIKE match. | Search hits raw `TITL` only. | Drops new `wb_*` dependency; PO can re-introduce via dedicated translation service if needed. |
| D6 | `REC` / `REC2` / `REC3` container keys | Used. | Replaced with `rows`, `approvers` (REC2 deferred with detail endpoint). | CTO directive — no legacy envelope. |
| D7 | Profile image URL | `szon_0012_003_r002.act?USER_ID=...` (legacy WSVC). | `/api/v1/users/{userId}/profile-image` (placeholder). | Slug-based REST URI. |
| D8 | Bulk delete | Implemented in `resl_0001_001_d001` (Cambodia view only). | Deferred. | Not in OSMC. OQ-2. |
| D9 | Tab `appr_type_cd` filter | Applied to detail query only ([resl_0001_001_r001_act.jsp:82-86](../../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/resl/resl_0001_001_r001_act.jsp#L82)) — counts ignore it. | Same: `fetchTabCounts` and `findApprovalBoxList` carry `apprTypeCd` differently — counts ignore it, rows respect it. | Behaviour preserved exactly. |

---

## 14 · Open questions surfaced from backend angle

(Cross-references discovery OQ-1..OQ-6.)

- **OQ-B1.** Should the response carry the legacy `APPR_DV` digit alongside the slug, or slug-only? Default: keep both for one MR (cheaper traceability during cut-over).
- **OQ-B2.** Tab counts: should they reflect the same per-user visibility filter as the rows? The legacy IDO does — yes — but it's expensive. Default: yes (parity).
