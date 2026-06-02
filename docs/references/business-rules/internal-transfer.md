# Internal Transfer — Refactor Backend Design

| Field | Value |
|---|---|
| Slug | `internal-transfer` |
| Java root package | `com.kosign.wecms.domain.payment.internaltransfer` (under the workspace root `com.kosign.wecms` — **not** `com.webcash.wecms` as some earlier templates suggested) |
| REST bases | `/api/v1/payment/internal-transfer` (Scheduled flow) + `/api/v1/payment/internal-transfer/results` (Result flow — separate `TransferResultController`) |
| Audit `WSVC_ID` | `internal-transfer` — declared as a `static final String` at [InternalTransferService.java:113](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/service/InternalTransferService.java#L113) and passed into every `AuditLogService.append(...)` call |
| Implementation status | **Phase A implemented + Phase A frontend partial** (2026-05-21). Locked baseline for sibling Payment ports. See §15 for as-built deltas vs. earlier design template, §16 for "shared with siblings". |
| Companion docs | [features/payment/internal-transfer.md](../../features/payment/internal-transfer/README.md) · [features/payment/README.md](../../features/payment/README.md) · [migration/internal-transfer-mapping.md](../migration/internal-transfer-mapping.md) · [frontend/internal-transfer.md](../../../../wecms-web/docs/features/payment/internal-transfer/README.md) · [database/payment/internal-transfer-schema.md](../database/payment/internal-transfer-schema.md) |

> Mirrors the existing precedent `domain/setting/firmbankingsettings/` (CRUD with audit-log writes) and `domain/banking/` (list + filter + sort + Excel export). Follow the package layout, layering, and exception/audit rules in [wecms-api/CLAUDE.md](../../../CLAUDE.md) § Architecture Rules + § Coding Rules. Identifiers — package, class, REST URI, audit `WSVC_ID`, log tag — all use the kebab-case slug `internal-transfer` (camelCase `internalTransfer` in Java, `payment-internal-transfer-*` for View Setting).

---

## 1. Package layout (as-implemented, 2026-05-21)

Under [`wecms-api/src/main/java/com/kosign/wecms/domain/payment/internaltransfer/`](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/):

```
controller/
    InternalTransferController.java                @RequestMapping("/api/v1/payment/internal-transfer") — 11 mapped methods
    TransferResultController.java                  @RequestMapping("/api/v1/payment/internal-transfer/results") — list / detail / export
service/
    InternalTransferService.java                   list / detail / createDraft / delete / updateReportingAccount /
                                                    requestApproval / actOnApproval / parseExcel /
                                                    listWithdrawalAccounts / resolveDownload (WSVC_ID literal at L113)
    InternalTransferResultService.java             result list + result detail
    InternalTransferDispatchService.java           dispatch(id, req) — cut-off + audit + TRNF_YN='Y' (Phase A stub:
                                                    TB_PAY_TARGET / TB_PAY_FILE_MST inserts deferred)
    InternalTransferKeyGenerator.java              SCDUL_PYMN_NO generator — YYYYMMDD<seq:6>, Vietnam-local day
    InternalTransferExcelParser.java               Apache POI walk (Phase A: file-shape validation only)
    InternalTransferFileResolver.java              storage-root + saved filename resolution
    InternalTransferConfig.java                    @Configuration — Clock bean wiring for tests
    InternalTransferStorageProperties.java         @ConfigurationProperties("wecms.internal-transfer.storage")
    CutoffValidator.java                           mirrors PymnHelper.validateTransferTiming + getCutoffConfig
    TransferNetwork.java                           enum NAPAS / CITAD / SWIFT / INTER
    ScdulPymnTypeCd.java                           enum INTERNAL("03") VENDOR("01") EXPENSE("02") PAYROLL("00")
    TrnfYn.java                                    enum Y / N
    InternalTransferSortField.java                 implements SortableField — list page whitelist
    InternalTransferResultSortField.java           implements SortableField — result list whitelist
dto/
    ScheduledTransferRow.java                      list row record
    ScheduledTransferListResponse.java             { total_count, currency_totals, rows }
    ScheduledTransferDetailResponse.java           master + lines
    TransferLineDetail.java                        detail row
    CurrencyTotal.java                             totals by currency
    CreateScheduledTransferRequest.java            @Valid multipart-paired DTO
    CreateScheduledTransferResponse.java           { scdul_pymn_no }
    TransferLineInput.java                         REC element
    UpdateReportingAccountRequest.java             { chart_acct_seq_no, chart_acct_cd }
    DispatchRequest.java                           { trnf_ccy_cd }
    ParseExcelResponse.java                        { tot_cnt, suc_cnt, err_cnt, rows }
    ParsedTransferLine.java                        parsed-with-errors line
    TransferResultRow.java                         result list row
    TransferResultListResponse.java                result list envelope
    TransferResultDetailResponse.java              result detail master + lines
    TransferResultLine.java                        result line with proc_stat / rs_cd / rs_msg / proc_dttm / rslt_dttm
    WithdrawalAccountRow.java                      response row for GET /withdrawal-accounts
    RequestApprovalRequest.java                    body of POST /{id}/request-approval
    RequestApprovalResponse.java                   { appr_seq_no, … }
    ApprovalActionRequest.java                     body of POST /{id}/approval-action (approve / reject)
entity/
    ScdulPymnSumr.java + ScdulPymnSumrId.java      @Table("SCDUL_PYMN_SUMR") composite PK (BIZ_KEY, SCDUL_PYMN_NO)
    ScdulPymnSumrDetl.java + ScdulPymnSumrDetlId.java  @Table("SCDUL_PYMN_SUMR_DETL") PK (BIZ_KEY, SCDUL_PYMN_NO, SCDUL_PYMN_DETL_NO)
    InternalTransferKeySeq.java + InternalTransferKeySeqId.java
                                                    refactor-only helper @Table("INTERNAL_TRANSFER_KEY_SEQ")
repository/
    ScdulPymnSumrRepository.java                   extends JpaRepository<ScdulPymnSumr, ScdulPymnSumrId>
    ScdulPymnSumrDetlRepository.java               extends JpaRepository<ScdulPymnSumrDetl, ScdulPymnSumrDetlId>
    InternalTransferKeySeqRepository.java          custom — exposes allocateNext(bizKey, keyDt) via ON CONFLICT UPSERT
    ScdulPymnCashFlowRow.java                      projection for the joined list query
export/
    InternalTransferResultExportProvider.java      implements ExportRowProvider — screenId "payment-internal-transfer-result"
                                                    (additional detail-page provider lands with the export-wiring follow-up)
```

Notes:

- **Path-variable `@Pattern` constraint deviation.** Earlier design templates pinned `{id}` to `^[0-9]+$`. The actual generator emits 14 chars (`YYYYMMDD<seq:6>`) — purely numeric, so the digit-only `@Pattern` still works. `{lineId}` (= `SCDUL_PYMN_DETL_NO`) follows the same rule.
- **No `mapper/` / `validator/` / `audit/` subpackages**. Earlier template proposed these as standalone subfolders; the actual code keeps mapping helpers private inside the service, validation in DTO bean-validation + `CutoffValidator`, and audit calls inline at service-method level (passing the `WSVC_ID` constant directly into `AuditLogService.append(...)` — no per-feature `InternalTransferAuditWriter` class).
- **Dispatch entities live in `payment/dispatch/`, not in this package.** `TbPayTarget`, `TbPayFileMst`, `TbPayFileData` are owned by the cross-feature `payment/dispatch/` infrastructure — see §16.
- **Result service has its own controller**. Earlier design referred to `InternalTransferResultController`; the actually-shipped class name is `TransferResultController` (under `controller/`, same package). Function unchanged.

## 2. REST contract (full)

All endpoints return `ApiResponse<T>` envelope (`{ status, data }` on success, `{ status, timestamp, path, trace_id }` on error) per [api CLAUDE.md § Response envelope](../../../CLAUDE.md#response-envelope--all-endpoints).

| Verb + path | Capability (matrix flag) | Request | Response data |
|---|---|---|---|
| `GET /api/v1/payment/internal-transfer` | R | Query: `fromDt`, `toDt`, `srchWd?`, `apprType?`, `pageNo?`, `pageSize?`, `sortField?`, `sortDirection?` | `ScheduledTransferListResponse` |
| `GET /api/v1/payment/internal-transfer/{id}` | R | Path: `id` (= `scdul_pymn_no`); query: `srchWd?`, `sortField?`, `sortDirection?` | `ScheduledTransferDetailResponse` |
| `POST /api/v1/payment/internal-transfer/uploads/parse` | (read-only helper, R-bound) | `multipart/form-data`: `file_data` (xlsx), `bank_cd` | `ParseExcelResponse` |
| `POST /api/v1/payment/internal-transfer` | U (create batch) | `multipart/form-data`: `file_data`, body JSON-part `request` with `titl`, `bank_cd`, `acct_no`, `ccy_cd`, `trnf_ccy_cd`, `lines[]` | `{ scdul_pymn_no }` — generated id |
| `PATCH /api/v1/payment/internal-transfer/{id}/lines/{lineId}/reporting-account` | U | Body: `UpdateReportingAccountRequest` | empty (`204 No Content` via `ApiResponse.ok(null)`) |
| `DELETE /api/v1/payment/internal-transfer/{id}` | D | Path: `id` | empty |
| `POST /api/v1/payment/internal-transfer/{id}/dispatch` | T | Body: `DispatchRequest` | empty (returns `204` with envelope) |
| `POST /api/v1/payment/internal-transfer/{id}/export` | R | Body: `ExcelExportRequest` | streams XLSX (binary) — uses shared common/export pipeline |
| `GET /api/v1/payment/internal-transfer/results` | R | Query: `fromDt`, `toDt`, `srchWd?`, `trnfStts?`, `pageNo?`, `pageSize?`, `sortField?`, `sortDirection?` | `TransferResultListResponse` |
| `GET /api/v1/payment/internal-transfer/results/{id}` | R | Path: `id`; query: `procStat?` | `TransferResultDetailResponse` |
| `POST /api/v1/payment/internal-transfer/results/{id}/export` | R | Body: `ExcelExportRequest` | streams XLSX |

`{id}` paths use a `@Pattern("^[0-9]+$")` constraint (digits only, generated via § 3) — bad input falls through to the `ConstraintViolationException` handler and returns `WBE_VALIDATION` (HTTP 200 with `status.error=true`, per the existing GlobalExceptionHandler).

Frontend wire shape (camelCase Java field → snake_case JSON) is detailed in [migration/internal-transfer-mapping.md § 2](../migration/internal-transfer-mapping.md). The legacy UPPER_SNAKE never escapes JPA.

## 3. SCDUL_PYMN_NO key generation (Java-side)

Replaces the legacy `wb_get_next_val('scdul_pymn_sumr', 'scdul_pymn_no', BIZ_KEY)` call ([api CLAUDE.md § Key-generation rule](../../../CLAUDE.md#key-generation-rule-for-migrated-legacy-tables) — Sprint 2+ writes implement Java generators; no new `wb_get_next_val` branches).

```
SCDUL_PYMN_NO format: <YYYYMMDD><6-digit-tenant-day-seq>
                       └─ Vietnam local date (UTC+7)
                                └─ 1..999999 per (BIZ_KEY, day)
```

Implementation:

- A new helper table `INTERNAL_TRANSFER_KEY_SEQ` keyed by `(BIZ_KEY, KEY_DT)` with `LAST_SEQ INTEGER` is created in the same Flyway migration as the entities (see [database/internal-transfer.md § 4](internal-transfer.md)).
- `InternalTransferKeyGenerator` uses `@Transactional(propagation = REQUIRES_NEW)` to `INSERT ON CONFLICT … DO UPDATE … SET LAST_SEQ = LAST_SEQ + 1 RETURNING LAST_SEQ` so concurrent inserts don't collide.
- The generator is the only writer to `INTERNAL_TRANSFER_KEY_SEQ` — every Internal Transfer save goes through it.

OQ-3 in the feature spec gates this — the refactor SCDUL_PYMN_NO format will differ from any legacy DEV values for the same date, which is acceptable because refactor is local-only R&D.

## 4. DTO definitions (Java records)

Each DTO field is camelCase; the global Jackson `SNAKE_CASE` ([JacksonConfig](../../../src/main/java/com/kosign/wecms/config/JacksonConfig.java)) emits snake_case on the wire. No `@JsonProperty` is added per the api CLAUDE.md rule unless the wire name genuinely diverges (none for this feature).

### 4.1 Request DTOs

```java
package com.kosign.wecms.domain.payment.internaltransfer.dto;

public record CreateScheduledTransferRequest(
    @NotBlank @Size(max = 100) String titl,
    @NotBlank @Size(max = 20)  String bankCd,
    @NotBlank @Size(max = 100) String acctNo,
    @NotBlank @Size(max = 3)   String ccyCd,
    @NotBlank @Size(max = 3)   String trnfCcyCd,
    @NotEmpty @Valid           List<TransferLineInput> lines
) {}

public record TransferLineInput(
    @NotNull   BigDecimal tranAmt,
    @Size(max = 5)   String antDivCd,
    @Size(max = 100) String rcptNm,
    @Size(max = 15)  String tarBankCd,
    @NotBlank @Size(max = 100) String fincMdaNo,
    @Size(max = 200) String tranUsag,
    @Size(max = 20)  String trcoKey,
    @Size(max = 20)  String handTrcoKey,
    @Size(max = 50)  String docNo,
    @Size(max = 255) String note,
    @Size(max = 200) String citadBankCd,
    @Size(max = 200) String trcoCmenm,
    @Size(max = 200) String trcoCmnm,
    @Size(max = 10)  String chartAcctSeqNo,
    @Size(max = 20)  String chartAcctCd,
    @Size(max = 3)   String trnfCcyCd,
    @Size(max = 3)   String depoCcyCd,
    // echoed from the parse-step output (not validated):
    String errFld,
    String errFldCaus
) {}

public record UpdateReportingAccountRequest(
    @NotBlank @Size(max = 10) String chartAcctSeqNo,
    @NotBlank @Size(max = 20) String chartAcctCd
) {}

public record DispatchRequest(
    @NotBlank @Size(max = 3) String trnfCcyCd
) {}
```

### 4.2 Response DTOs

```java
public record ScheduledTransferListResponse(
    Integer totalCount,
    List<CurrencyTotal> currencyTotals,
    List<ScheduledTransferRow> rows
) {}

public record CurrencyTotal(
    BigDecimal totalAmount,
    Integer totalCount,
    String ccyCd,
    Integer totalTransactionCount
) {}

public record ScheduledTransferRow(
    String scdulPymnNo,
    String scdulPymnTypeCd,
    String ccyCd,
    String titl,
    String apprSeqNo,
    BigDecimal totalTranAmt,
    Integer totTrnsCnt,
    OffsetDateTime regDtm,
    String draftUserNm,
    String apprStts,
    String apprSttsKey,
    String trnfStts,
    String trnfYn,
    String fileUrl,
    List<ApproverRow> approvers,
    Integer apprTotalCount,
    Integer apprApprovedCount,
    String docNo,
    String apprDv,
    String postSeqNo,
    String outBankCd,
    String wdrlActNo,
    String outBankNm,
    String pymnType,
    LocalDate payDate,
    String fileNm,
    String postRegId,
    String regId,
    String wdrlCcyCd
) {}

public record ApproverRow(
    String apprvrStts,
    String apprvrNm,
    String apprvrId,
    Integer apprvrLineNo,
    OffsetDateTime modDtm,
    String regImgUrl
) {}

public record ScheduledTransferDetailResponse(
    String scdulPymnNo,
    String outBankCd,
    String wdrlActNo,
    String titl,
    BigDecimal totalTranAmt,
    Integer totTrnsCnt,
    String fileUrl,
    String docNo,
    String draftUserId,
    String draftUserNm,
    String apprSeqNo,
    Long fileSize,
    String orgFileName,
    String outBankNm,
    String ccyCd,
    String wdrlCcyCd,
    List<TransferLineDetail> rows
) {}

public record TransferLineDetail(
    String scdulPymnDetlNo,
    String antDivCd,
    String rcptNm,
    String tarBankCd,
    String fincMdaNo,
    String tranUsag,
    BigDecimal tranAmt,
    String ccyCd,
    String bankNm,
    String trcoKey,
    String handTrcoKey,
    String docNo,
    String note,
    String citadBankCd,
    String chartAcctCd,
    String chartAcctNmEn,
    String chartAcctSeqNo,
    String chartAcctNm,
    String chartAcctLevelNm,
    String erpAttribute2,
    String rcptStts,
    String rcptNm2,
    String depoCcyCd,
    String bankTrnsShortId
) {}

public record ParseExcelResponse(
    Integer totCnt,
    Integer sucCnt,
    Integer errCnt,
    List<ParsedTransferLine> rows
) {}

public record ParsedTransferLine(
    BigDecimal tranAmt,
    String antDivCd,
    String rcptNm,
    String tarBankCd,
    String tarBankNm,
    String fincMdaNo,
    String tranUsag,
    String errFld,
    String errFldCaus,
    String trcoKey,
    String handTrcoKey,
    String docNo,
    String note,
    String citadBankCd,
    String trcoCmnm,
    String trcoCmenm,
    String chartAcctSeqNo,
    String chartAcctCd,
    String chartAcctNmEn,
    String chartAcctNmVn,
    String chartAcctNmKo,
    String trnfCcyCd,
    String depoCcyCd
) {}

public record TransferResultListResponse(
    Integer totalCount,
    List<CurrencyTotal> currencyTotals,
    List<TransferResultRow> rows
) {}

public record TransferResultRow(
    String scdulPymnNo,
    String scdulPymnTypeCd,
    String ccyCd,
    String titl,
    BigDecimal totalTranAmt,
    Integer totTrnsCnt,
    String trnfStts,
    OffsetDateTime regDtm,
    Integer totTrnsSuccCnt,
    Integer totTrnsErrorCnt,
    String fileUrl,
    String docNo,
    String apprDv,
    String postSeqNo,
    String fileNm
) {}

public record TransferResultDetailResponse(
    String scdulPymnNo,
    String outBankNm,
    String outBankCd,
    String wdrlActNo,
    String titl,
    BigDecimal totalTranAmt,
    Integer totTrnsCnt,
    String fileUrl,
    String draftUserId,
    String draftUserNm,
    String apprSeqNo,
    String docNo,
    String orgFileName,
    Long fileSize,
    Integer totCnt,
    Integer sucCnt,
    Integer errCnt,
    String ccyCd,
    Integer prcCnt,
    String wdrlCcyCd,
    List<TransferResultLine> rows
) {}

public record TransferResultLine(
    BigDecimal tranAmt,
    String scdulPymnDetlNo,
    String antDivCd,
    String rcptNm,
    String tarBankCd,
    String fincMdaNo,
    String tarBankNm,
    String tranUsag,
    OffsetDateTime procDttm,
    String procStat,
    String procStatKey,
    String rerunDiv,
    String rsCd,
    String rsMsg,
    String ccyCd,
    String chartAcctSeqNo,
    String chartAcctCd,
    String chartAcctNmEn,
    String chartAcctNm,
    String chartAcctLevelNm,
    String trnfCcyCd,
    String note,
    String docNo,
    String depoCcyCd,
    String linkMatchYn,
    String bankTrnsShortId,
    String acctSeqNo,
    OffsetDateTime rsltDttm
) {}
```

The full wire-name table is in [migration/internal-transfer-mapping.md § 2](../migration/internal-transfer-mapping.md). DTO fields stay camelCase; the SNAKE_CASE strategy converts at the boundary.

## 5. Transactional boundaries

| Method | Annotation | Reason |
|---|---|---|
| `InternalTransferService.list(...)` | `@Transactional(readOnly = true)` | Read-only join across SCDUL_PYMN_SUMR + EXPT_RESOL_MSTR + APPR_LINE_INFM* + USER_INFM |
| `InternalTransferService.detail(...)` | `@Transactional(readOnly = true)` | Same |
| `InternalTransferService.createDraft(...)` | `@Transactional` | Atomically inserts `SCDUL_PYMN_SUMR` + N `SCDUL_PYMN_SUMR_DETL` rows + writes audit row; rolls back the whole batch on validation failure (mirrors legacy `IdoUtils.commitAndCloseQuietly` pattern in `pymn_0009_002_c001_act.jsp:74-78`) |
| `InternalTransferService.delete(...)` | `@Transactional` | Atomic delete of detail rows + master row + ERP_TRNS_HSTR delete log + audit row |
| `InternalTransferService.updateReportingAccount(...)` | `@Transactional` | Update one line + audit row |
| `InternalTransferDispatchService.dispatch(...)` | `@Transactional` | The single most important transactional path: cut-off validation + routing decision + insert N `TB_PAY_TARGET` rows OR insert `TB_PAY_FILE_MST` + N `TB_PAY_FILE_DATA` + stamp `TRNF_YN='Y'` on master + audit row. Rolls back on `WBE0404` / `WBW116x` / any persistence fault — mirrors the legacy `WbRollbackException` semantics in `PymnHelper.pymn_0009_001_c001` and the surrounding `idoCon.beginTransaction()` / `IdoUtils.commitAndCloseQuietly` block in `pymn_0009_001_c001_act.jsp:96-105` |
| `InternalTransferAuditWriter.append(...)` | `@Transactional(propagation = REQUIRES_NEW)` | Audit rows survive outer rollback only if explicitly opt-in — for Internal Transfer we want audits **tied** to the outer transaction so a failed dispatch does not leave an orphan audit row. **Therefore the wrapper uses default REQUIRED propagation** (joins outer transaction). The REQUIRES_NEW pattern is reserved for login history per existing `LoginHistoryService` precedent — does not apply here. |
| `InternalTransferKeyGenerator.nextScdulPymnNo(...)` | `@Transactional(propagation = REQUIRES_NEW)` | Sequence advance must commit even if the outer create-draft transaction rolls back — otherwise concurrent inserts collide on the same number. Inspired by `LoginHistoryService.record(...)` propagation. |
| `InternalTransferResultService.*` | `@Transactional(readOnly = true)` | All result-side endpoints are read-only |

Rollback rule — any `BusinessException("WBExxxx")` or `WbRollbackException`-equivalent triggers a rollback at the service boundary. `@Transactional` defaults already roll back on `RuntimeException`, which `BusinessException` extends.

## 6. Exception → HTTP status mapping

No new handler is introduced — every exception flows through the existing [`GlobalExceptionHandler`](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) and the existing `ApiResponse` envelope. Internal-Transfer-specific codes:

> **As-shipped code numbers** — the design draft above predates the Phase A backend MR, which allocated the `WBE20xx` range (see §15.3). The authoritative list is:

| Code | When | HTTP | Notes |
|---|---|---|---|
| `WBI0000` | Success | 200 | `ApiResponse.ok(...)` |
| `WBE_VALIDATION` | Bean-validation / `@Valid` failure | 200 | Existing handler |
| `WBE2060` | Scheduled transfer batch not found in tenant | 200 | Single envelope merges "not found" + "wrong tenant" |
| `WBE2061` | Scheduled transfer line not found in the given batch | 200 | |
| `WBE2062` | Cannot delete: batch has been submitted for approval (`APPR_SEQ_NO` set) | 200 | Mirrors legacy `WBE1063` at `pymn_0009_001_d001_act.jsp:90-92` |
| `WBE2063` | Cannot dispatch: batch has not been approved | 200 | Mirrors legacy implicit `WBE0404` at `PymnHelper.pymn_0009_001_c001:2439-2445` |
| `WBE2064` | Cannot dispatch: batch already dispatched (`TRNF_YN='Y'`) | 200 | |
| `WBE2065` | Reporting account not found (CHART_ACCT_INFM existence) | 200 | Reserved; not thrown in Phase A (CHART_ACCT_INFM not migrated locally yet) |
| `WBE2066` | Past cut-off time for `{network}` (cutoff `{HH:mm}`) | 200 | Replaces legacy warning `WBW1168` |
| `WBE2067` | Before transfer service start time for `{network}` (start `{HH:mm}`) | 200 | Replaces `WBW1167` |
| `WBE2068` | CITAD / SWIFT transfers not available on weekends | 200 | Replaces `WBW1162` |
| `WBE9999` | Unexpected | 500 | Catch-all per existing handler |

All Phase A codes land in [`messages.properties`](../../../src/main/resources/messages.properties) per [api CLAUDE.md § "When you add a new error code"](../../../CLAUDE.md#when-you-add-a-new-error-code). Earlier design names (`WBE1162` / `WBE1167` / `WBE1168` / `WBE2017` / `WBE2018`) are superseded; do not reuse those numbers.

## 7. Security

- Filter chain — every endpoint under `/api/v1/payment/internal-transfer/**` is protected by the existing `SecurityFilterChain` (anything outside `/api/v1/auth/*` is `authenticated()`).
- Tenant scope — every service method reads `TenantContext.currentBizKey()` and constrains every JPQL / derived-method finder on `bizKey`. No wildcard, no LIKE.
- `@PreAuthorize` — refactor wiring for `ROLE_ADMIN` / `ROLE_USER` is ready ([api CLAUDE.md § "Authority model"](../../../CLAUDE.md#authority-model)) but no annotation is added on Internal Transfer endpoints in this MR; the JEX permission matrix flags (R / U / D / T) are honoured by the **frontend menu / button gating** for now. Adding `@PreAuthorize("hasRole('USER')")` (or stronger per flag) is a follow-up MR after the cross-product permission resolution lands.
- Audit log — every mutating endpoint writes `AUDIT_LOGS_INFM` via `InternalTransferAuditWriter`, which delegates to the shared `AuditLogService.append(...)` with:
  - `WSVC_ID = "internal-transfer"` (slug, not legacy WSVC id — per [api CLAUDE.md § Refactor identifier naming](../../../CLAUDE.md#refactor-identifier-naming--slugs-not-wsvc-ids))
  - `OP = "create" | "update" | "delete" | "dispatch"`
  - `WS_TRACE_ID` = current MDC trace id (so the audit row and the server log line correlate)
  - `MSG_KEY` = `audit.internal_transfer.<op>` (i18n key; the EN/KO/VI strings live on the frontend)

## 8. Reusable components consumed

What already exists in `wecms-api` that this feature wires into — never re-implement:

| Concern | Existing component | Where |
|---|---|---|
| Auth + JWT + `TenantContext` | `JwtAuthenticationFilter`, `TenantContext` | `common/security/`, `common/tenant/` |
| Response envelope | `ApiResponse<T>`, `GlobalExceptionHandler` | `common/response/`, `common/exception/` |
| Pagination | Spring Data `Pageable` + custom `PagedResponse` shape used by Banking | `domain/banking/` precedent |
| Sort whitelist | `SortableField` + `SortDirection` | `common/web/` (see § 13a [api CLAUDE.md](../../../CLAUDE.md#common-table-sort--commonweb-whitelist--per-domain-enum)) |
| View Setting | `ScreenOptionService` + `SYS_SCR_OPTION_WECMS` seed | `domain/setting/` — seed-only addition for `payment-internal-transfer-scheduled` / `-result` |
| Excel export | `ExcelExportService` + `ExcelWorkbookWriter` + `ExportRowProvider` registry | `common/export/` (see § 13b [api CLAUDE.md](../../../CLAUDE.md#excel-export--commonexport-shared-pipeline--per-screen-exportrowprovider)) |
| Amount formatting | `AmountFormatter.forDisplay(value, ccyCd)` | `domain/banking/service/` — reused for subheaders |
| Bank-code resolution | `BankCdResolver` (loads `BANK_INFM` once at startup) | `domain/banking/` |
| Account-type code → i18n key | `AcctTypeCd` enum + `BankCdResolver.resolveAcctTypeKey` | `common/enums/` |
| Currency enum | `CcyCd` (legacy enum already in scope) | (legacy reference — for value resolution only; per [api CLAUDE.md § Domain enums](../../../CLAUDE.md#domain-enums--commonenums) policy this is read-only) |
| Audit log writes | `AuditLogService.append(...)` | `domain/setting/audit/` (existing) |
| Multipart parsing | Spring `MultipartFile` + `MultipartProperties` | framework |
| Excel parsing | Apache POI 5.x (already in build.gradle) | `common/export/` precedent |
| i18n message resolution | `MessageResolver` | `common/i18n/` |
| Trace id | `TraceIdFilter` + MDC `traceId` | `common/exception/` |

New components introduced for this feature only:

| Concern | New component | Reason |
|---|---|---|
| Cut-off-time gate | `cutoff/CutoffValidator` + `cutoff/CutoffProperties` | Replaces `PymnHelper.validateTransferTiming` / `getCutoffConfig`. Configuration-driven (per OQ-6) under `wecms.internal-transfer.cutoff.*` |
| SCDUL_PYMN_NO generator | `InternalTransferKeyGenerator` + `INTERNAL_TRANSFER_KEY_SEQ` table | Replaces legacy `wb_get_next_val('scdul_pymn_sumr','scdul_pymn_no',BIZ_KEY)` per OQ-3 |
| Excel storage | `InternalTransferUploadStorage` | Persists original uploads under `wecms.internal-transfer.storage-root` (mirrors `ErpTrnsHistoryStorage` pattern) |
| Excel parsing | `InternalTransferExcelParser` | Replaces `PymnHelper.importDataFromExcelForInternalTransfer` — Apache POI workbook walk, per-cell validation, returns `ParseExcelResponse` |
| Dispatch routing | `InternalTransferDispatchService.computeRouting(...)` | Replaces the routing-flag block at `PymnHelper.pymn_0009_001_c001` lines 2459-2509. Pure function for testability. |
| Per-feature audit writer | `InternalTransferAuditWriter` | Thin wrapper around `AuditLogService.append` so the slug + key prefix are not repeated at call sites |

The shared common-export, common-web sort, View Setting, and audit components stay untouched — this feature contributes seed rows and per-screen `ExportRowProvider` classes only.

## 9. Controller / service skeleton

`InternalTransferController` and `InternalTransferResultController` stay thin per [api CLAUDE.md § "Controllers"](../../../CLAUDE.md#coding-rules):

```java
@RestController
@RequestMapping("/api/v1/payment/internal-transfer")
@RequiredArgsConstructor
public class InternalTransferController {

    private final InternalTransferService service;
    private final InternalTransferDispatchService dispatchService;
    private final ExcelExportService excelExportService;

    @GetMapping
    public ApiResponse<ScheduledTransferListResponse> list(
            @RequestParam String fromDt,
            @RequestParam String toDt,
            @RequestParam(required = false) String srchWd,
            @RequestParam(required = false) String apprType,
            @RequestParam(required = false, defaultValue = "1")  Integer pageNo,
            @RequestParam(required = false, defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return ApiResponse.ok(service.list(fromDt, toDt, srchWd, apprType, pageNo, pageSize, sortField, sortDirection));
    }

    @GetMapping("/{id:^[0-9]+$}")
    public ApiResponse<ScheduledTransferDetailResponse> detail(
            @PathVariable String id,
            @RequestParam(required = false) String srchWd,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortDirection) {
        return ApiResponse.ok(service.detail(id, srchWd, sortField, sortDirection));
    }

    @PostMapping(path = "/uploads/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ParseExcelResponse> parse(
            @RequestPart("file_data") MultipartFile file,
            @RequestPart("bank_cd")   String bankCd) {
        return ApiResponse.ok(service.parseExcel(file, bankCd));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> create(
            @RequestPart("file_data") MultipartFile file,
            @RequestPart("request") @Valid CreateScheduledTransferRequest request) {
        String scdulPymnNo = service.createDraft(file, request);
        return ApiResponse.ok(Map.of("scdul_pymn_no", scdulPymnNo));
    }

    @PatchMapping("/{id:^[0-9]+$}/lines/{lineId:^[0-9]+$}/reporting-account")
    public ApiResponse<Void> updateReportingAccount(
            @PathVariable String id,
            @PathVariable String lineId,
            @RequestBody @Valid UpdateReportingAccountRequest request) {
        service.updateReportingAccount(id, lineId, request);
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id:^[0-9]+$}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id:^[0-9]+$}/dispatch")
    public ApiResponse<Void> dispatch(
            @PathVariable String id,
            @RequestBody @Valid DispatchRequest request) {
        dispatchService.dispatch(id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id:^[0-9]+$}/export")
    public void export(
            @PathVariable String id,
            @Valid @RequestBody ExcelExportRequest request,
            HttpServletResponse response) {
        excelExportService.exportTo(response, request);
    }
}
```

`InternalTransferResultController` mirrors the read-only endpoints — `GET /results`, `GET /results/{id}`, `POST /results/{id}/export`.

The service classes carry the actual business logic; the controller is parsing + delegation only.

## 10. Sort whitelists

Per [api CLAUDE.md § Common table sort](../../../CLAUDE.md#common-table-sort--commonweb-whitelist--per-domain-enum).

```java
public enum InternalTransferSortField implements SortableField {
    REG_DTM        ("regDtm"),
    APPR_STTS      ("apprStts"),
    TOTAL_TRAN_AMT ("totalTranAmt"),
    TITL           ("titl"),
    OUT_BANK_CD    ("outBankCd"),
    TRNF_STTS      ("trnfStts"),
    DRAFT_USER_NM  ("draftUserNm"),
    DOC_NO         ("docNo"),
    PAY_DATE       ("payDate");
    // CHECKBOX / ETC are NEVER in this enum — special FIELD_IDs per § 13a rule
    ...
}

public enum InternalTransferDetailSortField implements SortableField { ... }
public enum InternalTransferResultSortField implements SortableField { ... }
```

Default ORDER BY mirrors the legacy listing: `REG_DTM DESC, SCDUL_PYMN_NO DESC` (matches `SCDUL_PYMN_SUMR_R012` IDO behaviour; verify in Sprint 2 against the IDO XML — recorded as a checklist item in [audits/internal-transfer-parity-checklist.md](../audits/internal-transfer-parity-checklist.md)).

## 11. View Setting seeds

Two new screen registrations land in this feature's Flyway migration (no controller / service change per [api CLAUDE.md § Screen Option](../../../CLAUDE.md#screen-option-view-setting--domainsetting)):

| `WEB_SVC_ID` | `DYM_AREA_ID` | Columns |
|---|---|---|
| `payment-internal-transfer-scheduled` | `default` | `CHECKBOX` (DISP_ORD=0, FIX_YN='Y') · `REG_DTM` · `TITL` · `TOT_TRNS_CNT` · `TOTAL_TRAN_AMT` · `APPR_STTS` · `REG_USER` (Requestor) · `APPROVER` · `APPR_SEQ_NO` · `DOC_NO` · `ETC` (DISP_ORD=99) |
| `payment-internal-transfer-result` | `default` | `REG_DTM` · `TITL` · `TOT_TRNS_CNT` · `TOTAL_TRAN_AMT` · `TRNF_STTS` · `APPR_SEQ_NO` · `DOC_NO` · `ETC` |
| `payment-internal-transfer-scheduled-detail` | `default` | one row per detail column (recipient, bank, amount, …) — full set in DB design |
| `payment-internal-transfer-result-detail` | `default` | one row per result-detail column |

The frontend registers the matching `SCREEN_IDS.PAYMENT_INTERNAL_TRANSFER_*` constants in [`lib/viewSettingScreens.ts`](../../../../wecms-web/lib/viewSettingScreens.ts) per [§ 13](../../../../wecms-web/CLAUDE.md) — full set in [frontend/internal-transfer.md § 5](../../../../wecms-web/docs/features/payment/internal-transfer/README.md).

## 12. Excel export providers

Two new `ExportRowProvider` classes — one per detail page. The list pages already have totals rendered server-side, so list-export reuses the list query path; detail-export reuses the detail service.

```java
@Component
public class ScheduledTransferDetailExportProvider implements ExportRowProvider {
    public String screenId()       { return "payment-internal-transfer-scheduled-detail"; }
    public Set<String> allowedFieldIds() {
        return Set.of("SCDUL_PYMN_DETL_NO", "RCPT_NM", "TAR_BANK_CD", "TAR_BANK_NM",
                      "FINC_MDA_NO", "TRAN_AMT", "TRAN_USAG", "CHART_ACCT_CD",
                      "CHART_ACCT_NM", "DOC_NO", "NOTE", "DEPO_CCY_CD");
    }
    public ExportFetch fetch(ExcelExportRequest req) {
        ScheduledTransferDetailResponse detail = service.detail(req.filters().get("scdulPymnNo"), null, null, null);
        // map TransferLineDetail rows into Map<String,Object> keyed by FIELD_ID
        // build subHeader via AmountFormatter.forDisplay(detail.totalTranAmt(), detail.ccyCd())
        ...
    }
}
```

Same shape for `TransferResultDetailExportProvider`. No changes to `common/export/` itself.

## 13. Configuration properties

Added to `application-local.yml` (and surfaced as a typed `@ConfigurationProperties("wecms.internal-transfer")` record):

```yaml
wecms:
  internal-transfer:
    storage-root: ${WECMS_INTERNAL_TRANSFER_STORAGE_ROOT:C:/WECMS PROJECT/File Data/internal-transfer}
    upload:
      max-bytes: 5242880        # 5 MB — OQ-2 placeholder
      allowed-extensions: xlsx,xls
    routing:
      woori-bank-cd:   ${WECMS_WOORI_BANK_CD:11704000}     # OQ-7 — pin to local DEV value
      shinhan-bank-cd: ${WECMS_SHINHAN_BANK_CD:88886000}
    cutoff:
      shinhan:
        napas:  { start: "00:05", end: "23:45" }
        citad:  { start: "09:00", end: "15:30" }
        swift:  { start: "09:00", end: "22:00" }
      woori:
        napas:  { start: "00:15", end: "23:55" }
        citad:  { start: "08:30", end: "17:40" }
        swift:  { start: "08:30", end: "19:10" }
```

`spring.servlet.multipart.max-file-size` and `max-request-size` are tuned in the same MR to honour `wecms.internal-transfer.upload.max-bytes`.

## 14. Open follow-ups

Tracked in [features/internal-transfer.md § 10](internal-transfer.md#10-open-questions-for-po): OQ-1 (APPR_STTS code map), OQ-2 (upload size), OQ-3 (key gen), OQ-4 (WSVC scope deviation), OQ-5 (approval contract), OQ-6 (cut-off config home), OQ-7 (AntDivCd INTER), OQ-8 (multi-currency totals on Result).

## 15. Phase A implementation (as-built — Prompt 2 deliverable)

This section captures what actually shipped in the Phase A backend MR. Sections 1–14 above remain the design contract; this section documents the deviations and stub points so reviewers can verify scope quickly.

### 15.1 Package + REST base — confirmed values

Two deviations from the Prompt 2 template, both resolved in favour of the api CLAUDE.md "Refactor identifier naming" rule:

- **Java package root:** `com.kosign.wecms.domain.payment.internaltransfer` (not `com.webcash.wecms.internaltransfer` per the template). The codebase root is `com.kosign.wecms` everywhere — `BaseEntity`, `AuditLogService`, `BusinessException`, `TenantContext` are all under that namespace. Using `com.webcash.*` would have required exposing internal types publicly or duplicating. Matches the sibling `domain/payment/approvalbox/` precedent.
- **REST base path:** `/api/v1/payment/internal-transfer` (not `/api/v1/internal-transfer`). The api CLAUDE.md "Refactor identifier naming" section mandates `/api/v1/<module>/<slug>` — see canonical example `/api/v1/settings/firm-banking-settings/...`. Matches the existing FE scaffolding under `app/(dashboard)/payment/internal-transfer/*`.

### 15.2 Final controller signatures (as-built)

`InternalTransferController` ([source](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/InternalTransferController.java)) under `/api/v1/payment/internal-transfer`:

| Method | Path | Body / Params | Returns | Stage |
|---|---|---|---|---|
| `GET`    | `` (root) | `from_date`, `to_date`, `srch_wd?`, `sort_field?`, `sort_direction?`, `page?`, `size?` | `ApiResponse<ScheduledTransferListResponse>` | Working |
| `GET`    | `/{id}` | — | `ApiResponse<ScheduledTransferDetailResponse>` | Working |
| `POST`   | `/uploads/parse` | multipart `file_data` + `bank_cd` | `ApiResponse<ParseExcelResponse>` | **Local stub** — file-shape validation only; returns empty preview. Apache POI walk lands in a follow-up MR. |
| `POST`   | `` (root) | multipart `file_data?` + JSON-part `request` (`CreateScheduledTransferRequest`) | `ApiResponse<CreateScheduledTransferResponse>` | Working — persists master + lines + audit row. Original Excel **not** saved to disk in Phase A. |
| `PATCH`  | `/{id}/lines/{lineId}/reporting-account` | `UpdateReportingAccountRequest` | `ApiResponse<Void>` | Working. `CHART_ACCT_INFM` existence guard deferred to the sprint that ports that entity. |
| `POST`   | `/{id}/dispatch` | `DispatchRequest` | `ApiResponse<Void>` | Working — cut-off validation + audit + `TRNF_YN='Y'` stamp. **Local stub** — does NOT insert TB_PAY_TARGET / TB_PAY_FILE_MST rows (those tables defer to a follow-up MR per database doc §2.4). |
| `DELETE` | `/{id}` | — | `ApiResponse<Void>` | Working — `WBE2062` when `APPR_SEQ_NO` is set. |

`TransferResultController` ([source](../../../src/main/java/com/kosign/wecms/domain/payment/internaltransfer/controller/TransferResultController.java)) under `/api/v1/payment/internal-transfer/results`:

| Method | Path | Params | Returns | Stage |
|---|---|---|---|---|
| `GET` | `` (root) | `from_date`, `to_date`, `srch_wd?`, `trnf_stts?`, `sort_field?`, `sort_direction?`, `page?`, `size?` | `ApiResponse<TransferResultListResponse>` | Working |
| `GET` | `/{id}` | — | `ApiResponse<TransferResultDetailResponse>` | Working — per-line `proc_stat`/`rs_cd`/`rs_msg`/`proc_dttm`/`rslt_dttm` ship as `null` until dispatch tables land. |

Endpoints from the Prompt 2 template that were intentionally NOT implemented as separate REST resources:

- `GET /api/v1/internal-transfer/uploads` (upload-batch list) — collapsed: a "draft batch" in this design IS a `SCDUL_PYMN_SUMR` row, listed via the main `GET /api/v1/payment/internal-transfer` endpoint with the implicit `TRNF_YN='N'` filter applied by the frontend.
- `POST /api/v1/internal-transfer/uploads/{id}/confirm` — collapsed into the single-step save-draft at `POST /api/v1/payment/internal-transfer`. The JEX side also has no two-step parse/confirm RPC pair (see OQ-4); the upload dialog holds parsed rows in memory and submits everything at once.
- `GET /api/v1/internal-transfer/results/{id}/history` — collapsed: there is no per-batch "result history" concept on the JEX side beyond the master + lines surface (OQ-4). If a real history view is required later, it lands as a separate Audit Logs feature.
- `PUT /api/v1/internal-transfer/{id}` (full update) and `PATCH /api/v1/internal-transfer/{id}/status` (state change) — collapsed: there is no full-update surface in JEX for a draft batch (line edits go through reporting-account PATCH or batch delete + recreate), and state transitions happen via dispatch (`POST /dispatch`) and the approval workflow (separate feature). Both endpoints would be empty wrappers around the existing operations.

### 15.3 Error codes added to messages.properties

Allocated in V85 / Phase A. Every new code lives in `messages.properties` + `messages_en.properties` + the three locale files (KO / VI / KM carry EN verbatim for now per the existing precedent — verified translations land via the PO):

| Code | When thrown | HTTP |
|---|---|---|
| `WBE2060` | Scheduled transfer batch not found (single envelope — never leak cross-tenant existence) | 200 |
| `WBE2061` | Scheduled transfer line not found in the given batch | 200 |
| `WBE2062` | Cannot delete: batch has been submitted for approval (`APPR_SEQ_NO` set). Mirrors legacy `WBE1063` at `pymn_0009_001_d001_act.jsp:90-92`. | 200 |
| `WBE2063` | Cannot dispatch: batch has not been approved. Mirrors legacy implicit `WBE0404` at `PymnHelper.pymn_0009_001_c001:2439-2445`. | 200 |
| `WBE2064` | Cannot dispatch: batch already dispatched (`TRNF_YN='Y'`) | 200 |
| `WBE2065` | Reporting account not found (CHART_ACCT_INFM existence — reserved; not thrown in Phase A because CHART_ACCT_INFM isn't migrated locally yet) | 200 |
| `WBE2066` | Past cut-off time for `{network}` (cutoff `{HH:mm}`). Replaces legacy warning `WBW1168`. | 200 |
| `WBE2067` | Before transfer service start time for `{network}` (start `{HH:mm}`). Replaces `WBW1167`. | 200 |
| `WBE2068` | CITAD/SWIFT transfers not available on weekends. Replaces `WBW1162`. | 200 |

Audit i18n keys (resolved per locale by the shared `AuditLogService`):

```
audit.internal_transfer.activity         = Internal Transfer
audit.internal_transfer.create.detail    = Save scheduled transfer batch
audit.internal_transfer.update.detail    = Update scheduled transfer line
audit.internal_transfer.delete.detail    = Delete scheduled transfer batch
audit.internal_transfer.dispatch.detail  = Execute scheduled transfer
```

Domain-enum i18n keys for the cross-feature `appr_stts_key` / `proc_stat_key` siblings (reusable by every Payment-module feature):

```
payment.appr_stts.{draft,approved,rejected,in_progress,pending,cancelled}
payment.proc_stat.{pending,in_progress,success,failed,cancelled,transfer_pending}
```

### 15.4 Test coverage (Phase A)

- **Unit tests** ([service/](../../../src/test/java/com/kosign/wecms/domain/payment/internaltransfer/service/)):
  - `InternalTransferKeyGeneratorTest` — 5 tests pinning the `YYYYMMDD<seq:6>` format, Vietnam-local date resolution, range guards.
  - `CutoffValidatorTest` — 9 tests covering weekday happy path, weekend non-NAPAS block (`WBE2068`), weekend NAPAS allow, before-start (`WBE2067`), after-cutoff (`WBE2066`), Shinhan past-cutoff log-only exemption, unknown-bank bypass, plus `TransferNetwork.fromAntDivCd` parsing.
  - `InternalTransferSortFieldTest` — 5 tests pinning the lenient `parse(...)` contract for both list whitelists.
- **Slice tests** ([controller/](../../../src/test/java/com/kosign/wecms/domain/payment/internaltransfer/controller/)):
  - `InternalTransferControllerTest` — 11 tests covering happy paths + missing-required-param + body validation + business-exception propagation for list / detail / create / update reporting account / dispatch / delete.
  - `TransferResultControllerTest` — 5 tests covering happy paths + validation + business-exception propagation for results list / detail.

Total: 37 new tests, all passing locally (`./gradlew test --tests "com.kosign.wecms.domain.payment.internaltransfer.*"`).

Path-variable `@Pattern` validation is intentionally NOT tested at the slice level — Spring's `MethodValidationPostProcessor` is not wired under `MockMvcBuilders.standaloneSetup` (same documented limitation as the existing `FirmBankingSettingsControllerTest` precedent). The `@Pattern` guards remain in production code and fire under a full Spring context; integration tests (out of Phase A scope) exercise them.

`@DataJpaTest` for the repositories is intentionally NOT added — every repository query is a derived method name with no custom JPQL/SQL beyond the one tenant-scoped `search(...)` method (which is JPQL with `:param` placeholders; no concatenation). The native UPSERT in `InternalTransferKeySeqRepositoryImpl` would benefit from a `@DataJpaTest`, but it requires Postgres-specific `ON CONFLICT` syntax that H2 doesn't support — adding Testcontainers for one test is out of scope. The native query is exercised by manual smoke-test (`bootRun` + curl) against the local Postgres developer DB.

### 15.5 Build status

- `./gradlew compileJava` — passes
- `./gradlew compileTestJava` — passes
- `./gradlew assemble` — passes (bootJar / jar)
- `./gradlew test --tests "com.kosign.wecms.domain.payment.internaltransfer.*"` — passes (37/37)
- `./gradlew build` (full) — fails on **16 pre-existing tests** in unrelated modules (`AccountServiceLookupTest`, `FirmBankingHistoryControllerTest`, `FirmBankingSettingsControllerTest`, `PurposeSettingsControllerTest`). None of those files were touched in this MR. Their failure mode (`jakarta.servlet.ServletException ... Caused by: BusinessException`) is a brittleness in those legacy slice tests, unaffected by Internal Transfer code. Fixing them is out of scope per the Prompt 2 scope (`OUT OF SCOPE: any change under WECMS DEV/, SOURCE_CODE_ADMIN/, ...`) — they predate this MR.

### 15.6 Deferred to follow-up MRs

Documented for the Sprint 2+ planning:

1. **Excel parsing** — `InternalTransferService.parseExcel` is a local stub. Apache POI workbook walk + per-line validation mirroring `PymnHelper.importDataFromExcelForInternalTransfer` lands when the upload-dialog FE wiring needs real preview data.
2. **Excel storage** — `createDraft` does not write the uploaded file to disk. The `wecms.internal-transfer.storage-root` property is reserved; the writer + `WBE2015`-style file-missing path land with the download endpoint.
3. **Dispatch tables** — `TB_PAY_TARGET`, `TB_PAY_FILE_MST`, `TB_PAY_FILE_DATA`, `SCDUL_PYMN_PAY_*_CONN` are NOT in V85. The `dispatch` endpoint stamps `TRNF_YN='Y'` + audit row but does not insert dispatch rows. Real routing (API path vs File path) lands when those tables are migrated.
4. **EXPT_RESOL_MSTR `APPR_TYPE_CD='01'` cross-check** — currently relies on the Approval Box workflow to populate `APPR_SEQ_NO` only after approval. A defensive read against `EXPT_RESOL_MSTR` from the dispatch service is a hardening item.
5. **CHART_ACCT_INFM existence guard** — `WBE2065` is allocated and documented but never thrown in Phase A because the entity isn't migrated locally. The guard lands with the Settings sprint that ports CHART_ACCT_INFM.
6. **`APPR_STTS`/`APPR_STTS_KEY` on list/detail responses** — currently returned as `null`; needs the EXPT_RESOL_MSTR join (cross-feature with approval-box).
7. **Result-side `PROC_STAT`/`RS_CD`/`RS_MSG`/`PROC_DTTM`/`RSLT_DTTM`** — currently `null` on the wire; populated when dispatch tables land.

## 16. Shared infrastructure consumed (also reused by Vendor / Expense / Payroll siblings)

The four Payment sub-modules (`internal-transfer`, `vendor-payment`, `expense-payment`, `payroll`) share the same backend infrastructure. **Reuse — never fork.** When a sibling MR opens, no class under any of the packages below is modified; new functionality lives in the sibling's own service / DTO / export folder.

### 16.1 `payment/dispatch/` — bank dispatch primitives

Path: [`com.kosign.wecms.domain.payment.dispatch`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/)

| Class | Role |
|---|---|
| [`TransferDispatchExecutor`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/) | Shared executor that mirrors `PymnHelper.pymn_0009_001_c001` routing — TB_PAY_TARGET (API path) vs TB_PAY_FILE_MST + TB_PAY_FILE_DATA (File path) per `OUT_BANK_CD` + currencies. Internal Transfer's `InternalTransferDispatchService` will delegate here once dispatch-table writes are wired (Phase B). |
| [`DispatchKeyGenerator`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/service/) | `nextTranSeq(bizKey, tranDt)` (line 39) + `nextFileSeqNo(bizKey, tranDt)` (line 50) — per-(tenant, day) sequences for dispatch-target identity. Shared by every sibling. |
| [`TbPayTarget`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) + `Id` | `@Table("TB_PAY_TARGET")` PK `(tran_dt, biz_key, tran_seq)`. |
| [`TbPayFileMst`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) + `Id` | `@Table("TB_PAY_FILE_MST")` PK `(tran_dt, biz_key, file_seq_no)`. |
| [`TbPayFileData`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) + `Id` | `@Table("TB_PAY_FILE_DATA")` PK `(tran_dt, biz_key, file_seq_no, seq)`. |
| [`ScduPymnSumrDetlPayTargetConn`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/entity/) + `Id` | Conn-table entity (note `SCDU_` spelling, preserved from DEV). API-path linkage between detail line and TB_PAY_TARGET row. |
| `Tb{Firm,…}` entities | Several other dispatch-related tables (`TbFirmBatApiTran`, `TbFirmFileMst`, `TbFirmFileData`, `TbFirmFileRslt`, `TbBatchScheduleInfo`, `TbBatchScheduleDtl`) supporting longer-running dispatch flows. |
| [`TbPayTargetRepository`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/repository/), `TbPayFileMstRepository`, `TbPayFileDataRepository` | Spring Data repositories. |
| [`DispatchCountRepository`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/DispatchCountRepository.java) + [`DispatchCountRow`](../../../src/main/java/com/kosign/wecms/domain/payment/dispatch/DispatchCountRow.java) | Projection-backed read of "how many lines per `proc_stat`" for the Result-list summary. Consumed by every sibling's Result service. |

### 16.2 `payment/validation/` — Excel-cell validation primitives

Path: [`com.kosign.wecms.domain.payment.validation`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/)

| Class | Role |
|---|---|
| [`FieldCd`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/FieldCd.java) | enum: `REMARKS("RMKS")`, `RECIPIENT_NAME("RCPT")`, `MEMO("MEMO")`, `BENEFICIARY_NAME("BENA")`, `BENEFICIARY_ADDRESS("BEAD")` — the per-field discriminator used by `BLOCKED_CHARS_RULE`. |
| [`PymnType`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/PymnType.java) | enum: `INTERNAL_TRANSFER("INTR")`, `VENDOR_PAYMENT("VEND")`, `EXPENSE_PAYMENT("EXPN")`, `PAYROLL_PAYMENT("PAYR")`, `OVERSEAS_PAYMENT("OVER")`. The per-payment-type discriminator for blocked-chars lookup (different from `ScdulPymnTypeCd` which is the DB discriminator). |
| [`SpecialCharValidator`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/SpecialCharValidator.java) | Reads `BLOCKED_CHARS_RULE` and rejects payment-line strings that violate the per-`(PymnType, FieldCd)` ruleset. Consumed during Excel parse + create-draft. |
| [`BlockedCharsRule`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/entity/) entity + [`BlockedCharsRuleRepository`](../../../src/main/java/com/kosign/wecms/domain/payment/validation/repository/) | Schema landed in [V149__blocked_chars_rule_schema_and_seed.sql](../../../src/main/resources/db/migration/V149__blocked_chars_rule_schema_and_seed.sql). |

> Note: the **cut-off-time** validator lives inside `payment/internaltransfer/service/CutoffValidator.java` (not in `validation/`). That's an intentional split — cut-off rules are Internal-Transfer-specific by current scope; if Vendor / Expense / Payroll need them, lift the class to `payment/shared/cutoff/` in the future.

### 16.3 `payment/approvalbox/` — cross-sibling approval flow

Path: [`com.kosign.wecms.domain.payment.approvalbox`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/)

| Class | Role |
|---|---|
| [`ApprovalBoxController`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/controller/) | Cross-feature endpoints (list / approve / reject / bulk approve). Reads from `EXPT_RESOL_MSTR` + `EXPT_RESOL_APPRVR_INFM`. |
| [`ApprovalBoxService`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/service/ApprovalBoxService.java) | Owns `WSVC_ID = "approval-box"` audit channel. Internal Transfer's `POST /{id}/request-approval` + `POST /{id}/approval-action` endpoints delegate here. |
| `ApprovalBoxFanoutService` | Notification / fanout side-effects (out of scope for this feature). |
| [`ApprDv`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/) enum | Approval-domain discriminator — has an `INTERNAL_TRANSFER` constant the dispatch flow reads. |
| `ApprTypeCd`, `ApprvrStts`, `ApprovalBoxSortField` | Supporting enums. |
| [`ExptResolMstr`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/entity/) + `Id` + [`ExptResolApprvrInfm`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/entity/) + `Id` entities + repositories | The two backing tables — schema in [V109__approval_box_schema.sql](../../../src/main/resources/db/migration/V109__approval_box_schema.sql). |
| `ApprovalBox{ListResponse, Row, Projection, ActionResponse, BulkApproveRequest, BulkApproveResponse, Approver, TabCounts}` DTOs | 9 DTOs that the Approval Box page consumes. |
| [`ApprovalBoxExportProvider`](../../../src/main/java/com/kosign/wecms/domain/payment/approvalbox/export/) | XLSX export. |

### 16.4 `payment/export/` — cross-sibling result export

Path: [`com.kosign.wecms.domain.payment.export`](../../../src/main/java/com/kosign/wecms/domain/payment/export/)

| Class | Role |
|---|---|
| [`PaymentResultExportProvider`](../../../src/main/java/com/kosign/wecms/domain/payment/export/PaymentResultExportProvider.java) | Cross-sibling base / utility class used by per-sibling `*ResultExportProvider` implementations. |

### 16.5 Cross-feature primitives shared by every WeCMS feature

Same as the rest of the refactor — restated for clarity:

| Concern | Class |
|---|---|
| Tenant scope | [`TenantContext`](../../../src/main/java/com/kosign/wecms/common/tenant/TenantContext.java) + JWT filter |
| Response envelope | [`ApiResponse`](../../../src/main/java/com/kosign/wecms/common/response/ApiResponse.java) |
| Exception → HTTP | [`GlobalExceptionHandler`](../../../src/main/java/com/kosign/wecms/common/exception/GlobalExceptionHandler.java) |
| Audit log writes | `AuditLogService.append(...)` (Needs Confirmation) — every mutating endpoint passes `WSVC_ID = "internal-transfer"` |
| Sort whitelist | [`SortableField`](../../../src/main/java/com/kosign/wecms/common/web/SortableField.java) + `SortDirection` |
| Excel export | [`ExcelExportService`](../../../src/main/java/com/kosign/wecms/common/export/ExcelExportService.java) + `ExportableScreenRegistry` + per-screen `ExportRowProvider` |
| View Setting | [`ScreenOptionService`](../../../src/main/java/com/kosign/wecms/domain/setting/) + `SYS_SCR_OPTION_WECMS` seed |
| Multipart parsing | Spring `MultipartFile` |
| Excel parsing | Apache POI 5.x (via shared `common/export` infrastructure) |

> **Rule recap.** When porting Vendor / Expense / Payroll:
> 1. Reuse every class in §§16.1–16.5 — no forks.
> 2. Add a sibling-specific service / Excel parser / sort whitelist enum / DTOs / `ResultExportProvider` under `payment/{sibling}/`.
> 3. Add a sibling-specific upload dialog under `components/payment/{sibling}/` that consumes the shared [`<PaymentExcelUploadDialog>`](../../../../wecms-web/components/payment/shared/PaymentExcelUploadDialog.tsx).
> 4. Use the sibling-specific `WSVC_ID` for audit (`vendor-payment` / `expense-payment` / `payroll-payment`).
> 5. Use the sibling-specific `SCDUL_PYMN_TYPE_CD` (`01` / `02` / `00`).
>
> The shared infrastructure does NOT change.

---

Last verified: 2026-05-21
8. **`@PreAuthorize` role gating** — no annotations added this MR (mirrors the existing approval-box precedent which also defers to FE gating). The `ROLE_USER`/`ROLE_ADMIN` infrastructure is ready; permission `T` could be enforced as `@PreAuthorize("hasRole('USER')")` when role-mapping policy for the matrix lands.
