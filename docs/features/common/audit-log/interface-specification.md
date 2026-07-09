# audit-log — Interface Specification (Backend)

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

## No REST endpoint

This module has **no controller and no REST endpoint** — it is a write-path-only shared service.
Verified: there is no `auditlog/controller` package; the only public surface is the programmatic
`AuditLogService.append(AuditLogContext)` method.

There is also **no generic Common audit READ API**. Audit rows are surfaced by per-feature read
endpoints that live in their own domains (see below).

## Programmatic contract — `AuditLogService.append(AuditLogContext)`

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void append(AuditLogContext ctx)   // never throws; returns void
```

| Aspect | Value |
|---|---|
| Caller | Any domain `@Service` recording an audited mutation. |
| Input | `AuditLogContext` (record built via `AuditLogContext.builder()`). |
| Output | `void`. Success/failure of the audit row is **not** observable to the caller (failures swallowed, logged WARN). |
| Side effect | One `AUDIT_LOGS_INFM` row per supported locale, in a separate `REQUIRES_NEW` transaction. |

Example call site shape (verified, `AuditLogContext.java:16-24`):

```java
auditLogService.append(AuditLogContext.builder()
    .wsvcId("firm-banking-settings")
    .refNo("TB_FIRM_CONF")
    .refNo1(req.outBankCd())
    .refNo2(req.wdrlActNo())
    .activityKey("audit.firm_banking_settings.activity")
    .detailKey("audit.firm_banking_settings.update.detail")
    .build());
```

## Read side — per-feature endpoints that surface audit rows

Reads run through `AuditLogEntryRepository` (a `JpaRepository<AuditLogsInfm, AuditLogsInfmId>`) from inside
the consuming domain's own service + controller. The repository exposes derived finders that mirror the
legacy detail-audit query shape (`AuditLogEntryRepository.java`):

| Finder | Used by | Notes |
|---|---|---|
| `findBy...RefNo2...OrderByRegDtmDesc` | firm-banking-settings | full `(bizKey, refNo, refNo1, refNo2, wsvcId, langCd)` filter. |
| `findBy...RefNo2IsNull...OrderByRegDtmDesc` | Login Credentials | single-column natural PK (REF_NO2 null) on `BANK_CERT_INFO` / `GDT_CERT_INFO`. |
| `findBy...WsvcIdIn...RefNo2IsNull...` | Login Credentials | reads the refactor slug **and** legacy page ids (`comm_0218_001`, `comm_0218_004`) in one query for shared-DB coexistence — **read-only union**; writes still use the slug only. |
| `findByBizKeyAndRefNoIn...WsvcIdIn...` | Settings → Bank Account | `REF_NO IN ('ACCT_MSTR','ACCT_AUTH_INFO')`, `WSVC_ID IN ('bank-account', legacy 'comm_0183_001')`. |

The one verified wire endpoint that surfaces audit rows: Login Credentials `/audit-logs` (under
`/api/v1/login-credentials`). Each per-feature read endpoint lives in its own feature docs, not here.

## Constraints

- The writer is the only place that **inserts** into `AUDIT_LOGS_INFM`; consumers never `save` audit rows
  directly.
- The legacy JEX detail-audit read query is `comm_0220_003_r001_act.jsp:74-86` — the refactor finders
  reproduce its filter tuple and `ORDER BY REG_DTM DESC`.
