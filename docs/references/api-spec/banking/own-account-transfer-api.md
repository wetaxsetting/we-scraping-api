# Banking > Own Account Transfer — Backend API Design (Refactor)

**Refactor slug:** `own-account-transfer`
**Spring package:** `com.kosign.wecms.domain.banking.ownaccounttransfer`
**Status:** Phase 1 — design only. No code in this phase.
**Feature spec:** [../../features/banking/own-account-transfer.md](../../../features/banking/own-account-transfer/own-account-transfer.md)
**Migration mapping:** [../../migration/banking/own-account-transfer.md](../../migration/banking/own-account-transfer.md)
**DB design:** [../../database/banking/own-account-transfer-db.md](../../database/banking/own-account-transfer-db.md)

> All wire JSON is **lowercase snake_case** (CTO 2026-05-08 — global Jackson `SNAKE_CASE` strategy). Java DTO fields are camelCase. No per-field `@JsonProperty` unless the wire name truly diverges. See [wecms-api/CLAUDE.md §API Naming](../../../../CLAUDE.md#api-naming--serialization-standards).

---

## 1. Package layout

```
com.kosign.wecms.domain.banking.ownaccounttransfer/
├── controller/
│   └── OwnAccountTransferController.java
├── service/
│   ├── OwnAccountTransferService.java
│   ├── OwnAccountSourceSortField.java   ← enum (whitelist)
│   └── BalanceRefreshTxScript.java       ← package-private; encapsulates the
│                                            ACCT_MSTR patch sequence so the
│                                            service stays readable
├── dto/
│   ├── OwnAccountSourceQuery.java        ← record (request)
│   ├── OwnAccountSourceRow.java          ← record (response row)
│   ├── OwnAccountSourceListResponse.java ← record (response envelope)
│   ├── BalanceRefreshRequest.java        ← record
│   └── BalanceRefreshResponse.java       ← record
└── repository/
    └── AcctAuthInfoRepository.java       ← new (USER-mode auth pivot)
```

Reuses (no new files needed):
- `domain/banking/entity/AcctMstr` + `AcctMstrId`
- `domain/banking/repository/AcctMstrRepository` (extend with two methods)
- `domain/banking/BankCdResolver` (existing)
- `common/tenant/TenantContext`
- `common/exception/BusinessException`
- `common/response/ApiResponse`
- Existing audit-log writer (`domain/audit/...` — match the firm-banking-settings / contacts / chart-of-accounts precedent)

Composer-scope (out of this feature, listed for completeness): `NapasBankInfoRepository`, `NapasBankRemitInfoRepository` (new), `RecipientBankRow` (new DTO).

## 2. REST endpoints

| Endpoint | Method | Auth | Returns |
|---|---|---|---|
| `/api/v1/banking/own-account-transfer/source-accounts` | GET | JWT-protected | `ApiResponse<OwnAccountSourceListResponse>` |
| `/api/v1/banking/own-account-transfer/balance-refresh` | POST | JWT-protected | `ApiResponse<BalanceRefreshResponse>` |
| `/api/v1/banking/own-account-transfer/engine-connection-check` | POST | JWT-protected | `204 No Content` — audit-only sink; see §8 + §11 |
| `/api/v1/banking/own-account-transfer/recipient-banks` | GET | JWT-protected | `ApiResponse<RecipientBankListResponse>` — **composer-scope, ships with the sibling feature MR** |
| `/api/v1/banking/own-account-transfer/napas-bank-info` | GET | JWT-protected | `ApiResponse<NapasBankInfoRow>` — **composer-scope** |

All endpoints inherit:
- CORS / CSRF / session-stateless from `SecurityConfig`
- Bearer auth via `JwtAuthenticationFilter` → `TenantContext` populated per request
- Global exception → envelope mapping via `GlobalExceptionHandler`
- Trace-id propagation via `TraceIdFilter` (the `X-Trace-Id` response header)

## 3. DTOs (Java records)

### 3.1 Request — `OwnAccountSourceQuery`

```java
package com.kosign.wecms.domain.banking.ownaccounttransfer.dto;

import jakarta.validation.constraints.Pattern;

public record OwnAccountSourceQuery(
    @Pattern(regexp = "\\d{8}", message = "{validation.own_account_transfer.bank_cd}")
    String bankCd,

    OwnAccountSourceSortField sortField,        // null → default sort
    String sortDirection                          // "ASC" | "DESC"; null → ASC
) {
    public OwnAccountSourceQuery {
        if (bankCd != null && bankCd.isBlank()) bankCd = null;
        if (sortDirection != null) sortDirection = sortDirection.toUpperCase();
    }
}
```

Controller-side binding: `@RequestParam(required=false)` for each component, then `new OwnAccountSourceQuery(...)`. The whitelist enum `OwnAccountSourceSortField` is bound via Spring's enum converter (case-sensitive); a bad value triggers `MethodArgumentTypeMismatchException` → `WBE_VALIDATION`.

### 3.2 Response row — `OwnAccountSourceRow`

```java
package com.kosign.wecms.domain.banking.ownaccounttransfer.dto;

import java.math.BigDecimal;

public record OwnAccountSourceRow(
    String      bankCd,
    String      bankNm,
    String      bankNm2,
    String      acctNo,
    String      acctNickNm,
    BigDecimal  bal,
    BigDecimal  realAmt,
    String      ccyCd,
    String      acctTypeCd,
    String      acctBalInqStts,
    String      acctBalInqMsg,
    String      acctBalUpdDtm,
    String      acctBalLstInqDtm,
    String      bankCertSeq,
    String      useYnV,
    String      useYnW,
    String      authTypeV,
    String      authTypeW
) { }
```

### 3.3 Response envelope — `OwnAccountSourceListResponse`

```java
public record OwnAccountSourceListResponse(
    Long                       totalCount,
    BigDecimal                 totBal,
    BigDecimal                 totAviBal,
    List<OwnAccountSourceRow>  list
) { }
```

### 3.4 Balance refresh request — `BalanceRefreshRequest`

```java
package com.kosign.wecms.domain.banking.ownaccounttransfer.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BalanceRefreshRequest(
    @NotBlank @Pattern(regexp = "\\d{8}") String bankCd,
    @NotBlank @Size(max = 100)            String acctNo,
    @DecimalMin("0")                       BigDecimal bal,           // optional, engine-reported
    @DecimalMin("0")                       BigDecimal realAmt,       // optional
    @NotBlank @Size(max = 20)              String acctBalInqStts,    // "00000000" = success
    @Size(max = 255)                       String acctBalInqMsg,
    @Pattern(regexp = "[A-Z]{3}")          String ccyCd
) { }
```

### 3.4a Engine connection-check request — `EngineConnectionCheckRequest`

Posted by the frontend once at page mount when `_v2nxisas.openNxisas(...)` returns. Audit-only sink; no domain effect.

```java
package com.kosign.wecms.domain.banking.ownaccounttransfer.dto;

import jakarta.validation.constraints.*;

public record EngineConnectionCheckRequest(
    @NotBlank @Pattern(regexp = "OK|FAIL|TIMEOUT|NOT_INSTALLED")
    String result,

    @NotBlank @Size(max = 50)  String engineVendor,    // e.g. "COCON"
    @NotBlank @Size(max = 50)  String engineVersion,   // e.g. "NXSAS 2025.03.11.0"
    @Size(max = 255)           String userAgent,
    @Size(max = 100)           String osPlatform,      // e.g. "Win32", "MacIntel"
    @Size(max = 100)           String scrapeSessionId, // engine-supplied session id if any
    @Size(max = 20)            String errorCode,       // vendor code on FAIL
    @Size(max = 255)           String errorMessage
) { }
```

### 3.5 Balance refresh response — `BalanceRefreshResponse`

```java
public record BalanceRefreshResponse(
    String      bankCd,
    String      acctNo,
    BigDecimal  bal,
    String      ccyCd,
    String      acctBalLstInqDtm
) { }
```

### 3.6 Sort whitelist — `OwnAccountSourceSortField`

```java
public enum OwnAccountSourceSortField {
    BANK              ("BANK_CD, ACCT_NO"),
    ACCT_NICK_NM      ("ACCT_NICK_NM"),
    BAL               ("BAL"),
    AVI_BAL           ("REAL_AMT"),
    ACCT_BAL_LST_STS  ("ACCT_BAL_LST_INQ_DTM");

    private final String sqlExpression;
    OwnAccountSourceSortField(String sqlExpression) { this.sqlExpression = sqlExpression; }
    public String toSqlOrderBy(String direction) {
        String dir = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        return sqlExpression + " " + dir;
    }
}
```

The legacy `SortHelper.getSortDynamic("trns_0001_001_r001", ...)` accepts any client-supplied string; the refactor enforces this whitelist server-side. Bad value → enum binding fails → `WBE_VALIDATION`.

## 4. Service contract — `OwnAccountTransferService`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OwnAccountTransferService {

    private final AcctMstrRepository       acctMstrRepo;
    private final AcctAuthInfoRepository   acctAuthRepo;     // new
    private final BankCdResolver           bankCdResolver;
    private final AuditLogWriter           auditWriter;      // existing pattern
    private final PrdtTypeGate             prdtGate;         // optional; or inline

    @Transactional(readOnly = true)
    public OwnAccountSourceListResponse listSourceAccounts(OwnAccountSourceQuery q) {
        // 1. Resolve tenant + user + product type from TenantContext
        // 2. Compute allowedBankCds:
        //      H  → ["11704001"]
        //      *  → ["11704088","11704003","11704004","11704001","11704020"]
        // 3. Coerce q.bankCd: if !allowed → null (parity with _r001_act.jsp:160-162)
        // 4. Build effective bank-set (single bank or full allowed list)
        // 5. Query rows + totals in a single tx (one round-trip via WITH)
        // 6. Format acctNickNm + bankNm/bankNm2 via bankCdResolver
        // 7. Write audit row: ACTION=LIST_SOURCE_ACCOUNTS, REQ_DATA={bank_cd?,
        //    sort_field?, sort_direction?, result_count, prdt_type}
        //    (REQUIRES_NEW boundary — read tx stays readOnly)
        // 8. Return list + totals
    }

    public void recordEngineConnectionCheck(EngineConnectionCheckRequest req) {
        // 1. Resolve tenant + user from TenantContext
        // 2. Write a single audit row inside a REQUIRES_NEW boundary:
        //    ACTION=ENGINE_CONNECTION_CHECK,
        //    REQ_DATA={result, engine_vendor, engine_version, user_agent,
        //              os_platform, scrape_session_id?, error_code?,
        //              error_message?}
        // 3. Return void — controller emits 204
    }

    @Transactional
    public BalanceRefreshResponse applyBalanceRefresh(BalanceRefreshRequest req) {
        // 1. Resolve PK from TenantContext + req.bankCd + req.acctNo
        // 2. Find existing AcctMstr; missing → WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND
        // 3. Decide patch shape:
        //      - always: ACCT_BAL_INQ_STTS, ACCT_BAL_INQ_MSG, ACCT_BAL_LST_INQ_DTM (= now)
        //      - status="00000000" AND bal != existing.bal:
        //              also CORC_BF_BAL = existing.bal, BAL = req.bal,
        //                   ACCT_BAL_UPD_DTM = now
        //      - status="00000000" AND req.realAmt present:
        //              also REAL_AMT = req.realAmt
        // 4. Patch row via AcctMstrRepository#applyBalanceRefresh
        // 5. Write audit row: WSVC_ID="own-account-transfer", action="BALANCE_REFRESH",
        //                     payload={bank_cd, acct_no, bal, status}
        // 6. Build response (BAL = patched value when success, existing.bal otherwise)
    }
}
```

**Transaction boundaries:**

- `listSourceAccounts` — outer `@Transactional(readOnly = true)` for the read; the audit-row write inside step 7 uses an inner `REQUIRES_NEW` boundary so the read stays read-only and the audit row survives a read-side rollback. See §8.1.
- `applyBalanceRefresh` — `@Transactional` (default REQUIRED). The audit write **piggybacks** the same tx so the audit row and the balance patch commit/rollback atomically; the firm-banking-settings audit precedent uses REQUIRES_NEW only because that service needs the audit row to survive an outer rollback — this feature's `BALANCE_REFRESH` audit is part of the patch's atomic outcome, so REQUIRED is correct.
- `recordEngineConnectionCheck` — `@Transactional` (default REQUIRED). Single-statement audit insert; tx exists only to make the insert visible atomically.
- The legacy `_u001_act.jsp` opens its own `idoCon.beginTransaction()` + `commitAndCloseQuietly`; the refactor's declarative tx replaces that.

**HAENGSUNG saving-account gate** (refactor PrdtType.H) — pushed into the repository query as additional `WHERE` clauses (see [database doc §5.2](../../database/banking/own-account-transfer-db.md)). The service resolves `PrdtType.fromCode(TenantContext.currentPrdtType())` once and passes a flag to the repository method — never compare to literal `"H"` string per the [PrdtType rule](../../../../CLAUDE.md#product-type-rule).

## 5. Repository methods

### 5.1 `AcctMstrRepository` (existing — extend)

Two new methods. Both are native queries because the legacy `ACCT_AUTH_INFO` pivot uses Postgres-specific `MAX(CASE WHEN ... END)` semantics that JPQL doesn't express cleanly.

```java
public interface AcctMstrRepository extends JpaRepository<AcctMstr, AcctMstrId> {

    // Existing methods retained.

    @Query(value = """
        SELECT
            am.BANK_CD,
            am.ACCT_NO,
            am.ACCT_NICK_NM,
            am.BAL,
            am.REAL_AMT,
            am.CCY_CD,
            am.ACCT_TYPE_CD,
            am.ACCT_BAL_INQ_STTS,
            am.ACCT_BAL_INQ_MSG,
            am.ACCT_BAL_UPD_DTM,
            am.ACCT_BAL_LST_INQ_DTM,
            am.BANK_CERT_SEQ,
            aai.USE_YN_V,
            aai.USE_YN_W,
            aai.AUTH_TYPE_V,
            aai.AUTH_TYPE_W
        FROM ACCT_MSTR am
        LEFT JOIN (
            SELECT BIZ_KEY, BANK_CD, ACCT_NO,
                   MAX(CASE WHEN AUTH_TYPE='V' THEN USE_YN     END) AS USE_YN_V,
                   MAX(CASE WHEN AUTH_TYPE='W' THEN USE_YN     END) AS USE_YN_W,
                   MAX(CASE WHEN AUTH_TYPE='V' THEN AUTH_TYPE END) AS AUTH_TYPE_V,
                   MAX(CASE WHEN AUTH_TYPE='W' THEN AUTH_TYPE END) AS AUTH_TYPE_W
              FROM ACCT_AUTH_INFO
             WHERE USER_ID = :userId
             GROUP BY BIZ_KEY, BANK_CD, ACCT_NO
        ) aai
          ON aai.BIZ_KEY = am.BIZ_KEY
         AND aai.BANK_CD = am.BANK_CD
         AND aai.ACCT_NO = am.ACCT_NO
        WHERE am.BIZ_KEY    = :bizKey
          AND am.CCY_CD     = 'VND'
          AND am.ACTV_YN    = 'Y'
          AND am.ACCT_TYPE_CD IN ('03','05')
          AND am.BANK_CD IN (:bankCds)
          /* HAENGSUNG-only saving-account gate */
          AND (
                :prdtTypeH = false
             OR  am.ACCT_TYPE_CD NOT IN ('05','06','08')
             OR ((am.EXPI_DT IS NULL OR TO_DATE(am.EXPI_DT,'YYYYMMDD') >= CURRENT_DATE)
                  AND am.BAL > 0)
              )
          /* USER-mode auth gate */
          AND (
                :isAdmin = true
             OR ((aai.USE_YN_V = 'Y' AND aai.AUTH_TYPE_V = 'V')
              OR (aai.USE_YN_W = 'Y' AND aai.AUTH_TYPE_W = 'W'))
              )
        ORDER BY /* injected via :orderByExpression */
        """,
        nativeQuery = true)
    List<Object[]> findSourceRowsForOwnTransfer(
        @Param("bizKey")    String bizKey,
        @Param("userId")    String userId,
        @Param("bankCds")   List<String> bankCds,
        @Param("isAdmin")   boolean isAdmin,
        @Param("prdtTypeH") boolean prdtTypeH,
        @Param("orderByExpression") String orderByExpression  // built from OwnAccountSourceSortField
    );

    @Query(value = """
        SELECT COUNT(*)             AS TOT_CNT,
               COALESCE(SUM(am.BAL), 0) AS TOT_BAL,
               COALESCE(SUM(am.BAL), 0) AS TOT_AVI_BAL
          FROM ACCT_MSTR am
          LEFT JOIN ( /* same pivot as above */ ) aai ON ...
         WHERE /* same WHERE as above */
        """, nativeQuery = true)
    Object[] aggregateForOwnTransfer( /* same params */ );

    @Modifying
    @Query(value = """
        UPDATE ACCT_MSTR
           SET ACCT_BAL_INQ_STTS    = :stts,
               ACCT_BAL_INQ_MSG     = :msg,
               ACCT_BAL_LST_INQ_DTM = :nowDtm,
               MOD_ID               = :modId,
               MOD_DTM              = :nowDtm,
               BAL                  = COALESCE(:newBal, BAL),
               REAL_AMT             = COALESCE(:newRealAmt, REAL_AMT),
               CORC_BF_BAL          = COALESCE(:newCorcBfBal, CORC_BF_BAL),
               ACCT_BAL_UPD_DTM     = COALESCE(:newUpdDtm, ACCT_BAL_UPD_DTM)
         WHERE BIZ_KEY = :bizKey AND BANK_CD = :bankCd AND ACCT_NO = :acctNo
        """, nativeQuery = true)
    int applyBalanceRefresh(
        @Param("bizKey") String bizKey, @Param("bankCd") String bankCd, @Param("acctNo") String acctNo,
        @Param("stts") String stts, @Param("msg") String msg, @Param("nowDtm") String nowDtm,
        @Param("modId") String modId,
        @Param("newBal") BigDecimal newBal,
        @Param("newRealAmt") BigDecimal newRealAmt,
        @Param("newCorcBfBal") BigDecimal newCorcBfBal,
        @Param("newUpdDtm") String newUpdDtm
    );
}
```

**Important constraints on the queries:**
- `BIZ_KEY` is **always** in the WHERE clause for the tenant boundary — matches the repository rule in [wecms-api/CLAUDE.md §Coding Rules](../../../../CLAUDE.md#coding-rules).
- The `:bankCds` list parameter must never be empty; the service constructs it with at least one bank (the HAENGSUNG case is `["11704001"]`, the default is the 5-bank allow-list).
- The legacy `decrypt(AM.ACCT_PWD)` / `decrypt(AM.TRNS_PWD)` SELECT-list members are **dropped** — passwords are not on this endpoint's wire.
- The legacy `wb_get_bank_nm(BANK_CD,'s')` / `wb_format_acct_nick_nm(...)` calls are **dropped** from the SQL — the Java service computes them via `BankCdResolver` after the query returns. (Avoids a Postgres `wb_*` function dependency in the refactor schema, consistent with refactor convention.)

### 5.2 `AcctAuthInfoRepository` (new)

Spring Data interface. The pivot lookup is inside the source-account query; this repository's only direct method is a re-validation hook used by `applyBalanceRefresh` to defend the ownership check (the frontend can lie about which row it's refreshing):

```java
@Query("""
    SELECT (COUNT(a) > 0) FROM AcctAuthInfo a
     WHERE a.bizKey = :bizKey AND a.userId = :userId
       AND a.bankCd = :bankCd AND a.acctNo = :acctNo
       AND ((a.useYn = 'Y' AND a.authType IN ('V','W')))
    """)
boolean existsAnyAuthForAccount(String bizKey, String userId, String bankCd, String acctNo);
```

ADMIN bypasses this check (legacy parity — admins can refresh any row).

## 6. Controller

```java
@RestController
@RequestMapping("/api/v1/banking/own-account-transfer")
@RequiredArgsConstructor
public class OwnAccountTransferController {

    private final OwnAccountTransferService service;

    @GetMapping("/source-accounts")
    public ApiResponse<OwnAccountSourceListResponse> listSourceAccounts(
            @RequestParam(required = false) String bankCd,
            @RequestParam(required = false) OwnAccountSourceSortField sortField,
            @RequestParam(required = false) String sortDirection) {
        return ApiResponse.ok(service.listSourceAccounts(
            new OwnAccountSourceQuery(bankCd, sortField, sortDirection)));
    }

    @PostMapping("/balance-refresh")
    public ApiResponse<BalanceRefreshResponse> refreshBalance(
            @Valid @RequestBody BalanceRefreshRequest req) {
        return ApiResponse.ok(service.applyBalanceRefresh(req));
    }

    @PostMapping("/engine-connection-check")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordEngineConnectionCheck(
            @Valid @RequestBody EngineConnectionCheckRequest req) {
        service.recordEngineConnectionCheck(req);
    }
}
```

No `@PreAuthorize` annotations in Phase 1 — see OQ-5 in the firm-banking-settings precedent (per-endpoint admin gating is a later wiring once the global rule lands).

## 7. Validation & exception mapping

| Failure | HTTP | `status.code` | Where it's thrown |
|---|---|---|---|
| Missing JWT | 401 | `WBE1009` | `JwtAuthenticationEntryPoint` (existing) |
| `@Valid` failure on `BalanceRefreshRequest` | 200 | `WBE_VALIDATION` | `GlobalExceptionHandler` |
| Bad `sort_field` enum value | 200 | `WBE_VALIDATION` | `GlobalExceptionHandler` (`MethodArgumentTypeMismatchException`) |
| Account not found in tenant | 200 | `WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND` | service §4, after `acctMstrRepo.findById(...)` |
| Account exists but USER has no auth for it | 200 | `WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN` | service §4, after `acctAuthRepo.existsAnyAuthForAccount(...)` |
| Account inactive (`ACTV_YN='N'`) | 200 | `WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_INACTIVE` | service §4 |
| `ccy_cd` present but not `VND` | 200 | `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY` | service §4 — this page is VND-only by design |
| DB / Hibernate fault | 500 | `WBE_DB` | `GlobalExceptionHandler#DataAccessException` |
| Unmapped runtime | 500 | `WBE9999` | catch-all |

### New error codes — register in `messages.properties` FIRST

| Code | English | Notes |
|---|---|---|
| `WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND` | The account was not found for this tenant. | Single envelope merges "not found" + "wrong tenant" (refactor precedent — chart-of-accounts, contacts). |
| `WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN` | You do not have permission to refresh this account. | USER without V/W grant trying to refresh someone else's row. |
| `WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_INACTIVE` | The account is inactive. | `ACTV_YN='N'` defence (legacy silently filters out inactive rows on the list query, so this case is rare in normal use). |
| `WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY` | This feature only supports VND accounts. | Refactor addition — defends against composer bugs that pass a non-VND `ccy_cd`. |

Translate to `messages_en.properties` + add `messages_ko.properties` / `_vi.properties` / `_km.properties` rows when those locales are real.

## 8. Audit logging

`AUDIT_LOGS_INFM.WSVC_ID = "own-account-transfer"` (the route slug — **not** `trns_0001_001`, per [CLAUDE.md 2026-05-09 directive](../../../../../CLAUDE.md#cross-tree-rules)).

**Per OQ-4 (resolved 2026-05-18 by PO Mr. Jung), this feature audits EVERY action**, not only writes. This diverges from the chart-of-accounts / contacts / firm-banking-settings precedent ("only-on-writes") — the divergence is per-feature and justified by the legacy `mntLogYn=Y` flag and compliance expectations around bank scraping.

| Endpoint | Audit row written? | `ACTION` column | Payload (JSON in `REQ_DATA`) |
|---|---|---|---|
| `GET /source-accounts` | **Yes** | `LIST_SOURCE_ACCOUNTS` | `{ bank_cd?, sort_field?, sort_direction?, result_count, prdt_type }` |
| `POST /balance-refresh` (success **and** failure) | **Yes** | `BALANCE_REFRESH` | `{ bank_cd, acct_no, ccy_cd, prev_bal, new_bal, prev_real_amt, new_real_amt, acct_bal_inq_stts, acct_bal_inq_msg, prdt_type }` |
| `POST /engine-connection-check` | **Yes** (the row IS the only artifact — endpoint has no domain effect) | `ENGINE_CONNECTION_CHECK` | `{ result, engine_vendor, engine_version, user_agent, os_platform, scrape_session_id?, error_code?, error_message? }` |
| `GET /recipient-banks` (composer-scope) | **Yes** | `LIST_RECIPIENT_BANKS` | `{ bank_cd, keyword?, from_trco_yn?, result_count }` |
| `GET /napas-bank-info` (composer-scope) | **Yes** | `LOOKUP_NAPAS_BANK_INFO` | `{ napas_bank_cd, result_count }` |

### 8.1 Transaction boundaries for the audit write

- **Mutation endpoints** (`balance-refresh`): audit row writes inside the same `@Transactional` boundary as the domain patch. On tx rollback both roll back — single atomic outcome. The firm-banking-settings precedent's `REQUIRES_NEW` is intentionally **not** copied here, because this feature needs success/failure parity (a failed refresh is itself a recorded event, not a rolled-back ghost).
- **Read endpoints** (`source-accounts`, composer reads): audit row writes inside a `@Transactional(propagation = REQUIRES_NEW)` boundary that wraps the read+log step, **after** the read query completes. This ensures a read that fails halfway (e.g. DB blip) still writes an attempt row, and a successful read writes the result_count exactly. The read itself stays `readOnly = true` for the JPA hint; only the log writer flips to `REQUIRES_NEW`.
- **Audit-only endpoint** (`engine-connection-check`): single `@Transactional` boundary holding only the audit insert.

### 8.2 Reuse the existing audit writer

There is exactly one audit infrastructure in `wecms-api`. Grep `AUDIT_LOGS_INFM` writes used by `ChartOfAccountsService` / `ContactsService` / `FirmBankingSettingsService`, then reuse the same writer with the slug + action constants this feature introduces. Do **not** create a parallel writer.

The legacy `JEX mntLogYn=Y` framework-level access log is separate and stays at the JEX platform layer — this section concerns the application-level `AUDIT_LOGS_INFM` table only.

### 8.3 Audit action constants

Add the five action strings as `public static final String` constants on a feature-local `OwnAccountTransferAuditActions` holder (mirrors the audit-action constants pattern other features use):

```java
public final class OwnAccountTransferAuditActions {
    public static final String LIST_SOURCE_ACCOUNTS    = "LIST_SOURCE_ACCOUNTS";
    public static final String BALANCE_REFRESH         = "BALANCE_REFRESH";
    public static final String ENGINE_CONNECTION_CHECK = "ENGINE_CONNECTION_CHECK";
    public static final String LIST_RECIPIENT_BANKS    = "LIST_RECIPIENT_BANKS";   // composer
    public static final String LOOKUP_NAPAS_BANK_INFO  = "LOOKUP_NAPAS_BANK_INFO"; // composer
    private OwnAccountTransferAuditActions() {}
}
```

The string values are wire-stable — once shipped, they are part of the audit-row vocabulary and cannot be renamed without a back-fill migration.

## 9. Intake-validation contract for the post-scrape POST endpoint

The frontend posts the **engine result** to `POST /balance-refresh`. The backend cannot trust the frontend's claims — every field requires defensive validation before the patch.

### 9.1 What we trust from the request

- `bank_cd` + `acct_no` — keys; validated against the tenant's `ACCT_MSTR` row.
- `acct_bal_inq_stts` — engine result code (`"00000000"` = success, otherwise vendor code like `"80003270"`).
- `acct_bal_inq_msg` — engine message text. Saved verbatim; no sanitisation beyond `@Size(max=255)`.

### 9.2 What we re-verify server-side

| Claim | Verification |
|---|---|
| "I (the user) am authorised to refresh this account" | Service re-calls `acctAuthRepo.existsAnyAuthForAccount(bizKey, userId, bankCd, acctNo)` (ADMIN bypasses). Failure → `WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN`. |
| "This account belongs to this tenant" | `BIZ_KEY` always comes from `TenantContext`, never from the request. The `findById` lookup uses `(bizKey, bankCd, acctNo)` — wrong tenant → not-found envelope. |
| "The new balance is plausible" | `@DecimalMin("0")` only — we **do not** sanity-check against the prior balance (engine reports authoritative truth; clamping silently masks scraping errors). |
| "The amount tolerance" | None enforced server-side. The legacy code does no comparison either — it simply records what the engine reports. Tolerance is a UX concern; document in feature §6 if added. |
| "The currency matches" | If `ccy_cd` is supplied, it must equal `VND` (the page is VND-only). Otherwise the request is rejected. |
| "Idempotency" | **Optional `idempotency_key` request header** — the service hashes it with `(bizKey, bankCd, acctNo)` and caches the result for 60s in-process. A second POST with the same key returns the cached response without a second DB patch. **Phase 1 design only — implement in Phase 2 if needed; legacy has no idempotency, so absence is parity.** |
| "Bank transaction id format" | **Not applicable.** This endpoint is a **balance refresh**, not a transfer-confirm — there is no bank-side transaction id involved. The engine reports `ACCT_BAL_INQ_STTS` for the balance-inquiry step, separate from the transfer-execution step. The transfer-confirm intake lives in the sibling **composer** feature; see composer doc when it lands. |

### 9.3 Why this endpoint is balance-refresh-only

In the legacy flow, `_u001` only persists balance-inquiry outcome — it does **not** persist transfer-execution outcome. Transfer-execution writes happen in `trns_0002_001_c001` (insert into `NAPAS_REMIT_TRNS_HSTR`) — that's a **composer-feature** endpoint, not this feature's endpoint.

The refactor preserves the separation:
- `POST /banking/own-account-transfer/balance-refresh` = persist balance result (this feature).
- `POST /banking/own-account-transfer/composer/confirm` = persist transfer execution (composer feature MR).

Mixing them in one endpoint would invent behaviour JEX doesn't have. Don't.

## 10. Wiring contracts

### 10.1 i18n — `messages.properties` additions

```
# Own Account Transfer — refactor
WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND     = The account was not found for this tenant.
WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN             = You do not have permission to refresh this account.
WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_INACTIVE      = The account is inactive.
WBE_OWN_ACCOUNT_TRANSFER_INVALID_CCY           = This feature only supports VND accounts.

validation.own_account_transfer.bank_cd        = bank_cd must be 8 digits.
```

### 10.2 Logging tags

- SLF4J logger name = `OwnAccountTransferService` (class name).
- MDC carries `traceId` automatically. The audit row's `WSVC_ID="own-account-transfer"` is the cross-system grep target.

### 10.3 Tests (Phase 2 backend MR — listed here so design is complete)

| Test | What it asserts |
|---|---|
| `OwnAccountTransferServiceListTest#list_admin_returns_all_supported_banks` | ADMIN with no `bank_cd` filter — returns all VND `03/05` accounts across the 5 supported banks. |
| `..._user_filters_by_auth_pivot` | USER with W-grant only on bank A — sees only A's rows, others hidden. |
| `..._haengsung_forces_vietin_only` | `PRDT_TYPE='H'` tenant — only `BANK_CD=11704001` rows returned, regardless of `bank_cd` param. |
| `..._haengsung_saving_expiry_gate` | HAENGSUNG saving account with `EXPI_DT < today` — hidden. |
| `..._haengsung_saving_zero_balance_gate` | HAENGSUNG saving with `BAL=0` — hidden. |
| `..._unknown_bank_cd_coerced_to_all` | `bank_cd=11704999` (not in allow-list) — service silently treats as "no filter" (legacy parity). |
| `..._sort_whitelist_rejects_unknown_field` | `sort_field=NOPE` → 200 with `status.code=WBE_VALIDATION`. |
| `BalanceRefreshTest#success_path_patches_bal_and_corc_bf_bal` | Status=`00000000`, new bal differs → `CORC_BF_BAL` set to old bal, `BAL` updated, `ACCT_BAL_UPD_DTM` updated. |
| `BalanceRefreshTest#success_same_bal_keeps_corc_bf_bal` | Status=`00000000`, bal equal → no `CORC_BF_BAL` write, no `ACCT_BAL_UPD_DTM` write. |
| `BalanceRefreshTest#failure_records_status_only` | Status=`80003270` → only `ACCT_BAL_INQ_STTS/_MSG/_LST_INQ_DTM` written; `BAL`/`REAL_AMT` untouched. |
| `BalanceRefreshTest#forbidden_when_user_has_no_auth` | USER without V/W grant → `WBE_OWN_ACCOUNT_TRANSFER_FORBIDDEN`, no DB write, no audit row. |
| `BalanceRefreshTest#wrong_tenant_returns_not_found` | JWT for tenant A, request bank+acct that belongs to tenant B → `WBE_OWN_ACCOUNT_TRANSFER_ACCOUNT_NOT_FOUND`. |
| `BalanceRefreshTest#audit_row_written_on_success_and_failure` | Both success and failure write an audit row with `WSVC_ID="own-account-transfer"`, `ACTION="BALANCE_REFRESH"`. |
| `OwnAccountTransferServiceListTest#list_writes_audit_row` | Every successful list call writes one audit row with `ACTION="LIST_SOURCE_ACCOUNTS"`, `result_count` matching the returned `list.size()`. Survives a forced read-side rollback (REQUIRES_NEW). |
| `EngineConnectionCheckTest#ok_path_writes_audit_row` | `result="OK"`, `engine_version="NXSAS 2025.03.11.0"`, `engine_vendor="COCON"` → controller returns 204, one row in `AUDIT_LOGS_INFM` with `ACTION="ENGINE_CONNECTION_CHECK"` and the engine version in the payload. |
| `EngineConnectionCheckTest#fail_path_writes_audit_row_with_error` | `result="FAIL"`, `error_code="80000001"` → 204 + audit row carrying the error code/message. |
| `EngineConnectionCheckTest#validation_rejects_unknown_result` | `result="WHATEVER"` → 200 with `status.code=WBE_VALIDATION` (pattern violation), no audit row. |
