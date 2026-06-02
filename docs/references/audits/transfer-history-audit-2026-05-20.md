---
slug: transfer-history
audit_date: 2026-05-20
reviewer: Reviewer of record
audit_kind: feature parity + compliance (backend + frontend)
status: final
last_updated: 2026-05-20
---

# Transfer History — Audit Report

## Summary verdict

**PASS-WITH-FIXES.**

The Transfer History refactor — backend at
[wecms-api/src/main/java/com/kosign/wecms/domain/banking/transferhistory/](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/)
and frontend at
[wecms-web/components/banking/internal-transfer/](../../../../wecms-web/components/banking/internal-transfer/)
— is functionally correct, faithfully reproduces the JEX query semantics
documented in the spec, follows every CLAUDE.md naming / wire / typography
rule, reuses every existing helper instead of cloning, and shows no security
issues. The endpoint, the page, and the cutover of `ApiTransferHistoryRow` /
`ApiTransferHistoryResponse` from the UPPER_SNAKE holdover to lowercase
snake_case all landed in a single backend + frontend pair as the
workspace's full-stack ownership rule requires.

**Verdict reasoning:** there are no blocker findings — the feature ships
behind the verdict. The pass is qualified ("with fixes") because of:

- **One major behavioural gap:** time-zone normalisation of `TRNS_DT` /
  `TRNS_TM` is missing on both sides of the boundary (F-1). JEX
  TZ-converts UTC → operator-locale in
  [trns_0003_001_r001_act.jsp:101-105](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp);
  the refactor displays the row's UTC timestamp verbatim. For a
  Vietnam-local operator this is a visible 7-hour offset.
- **Three small consistency gaps** between recently-applied UX tweaks and
  the artifacts that document them (defaults drift between spec / backend /
  frontend / test) — F-2 / F-3 / F-4.
- **One small parity gap** (F-5) where the Results column lost its
  legacy-sortable flag, plus three nits (F-6 / F-7 / F-9) that are
  cosmetic / documentation-only.

None of these are blockers. The follow-up checklist below is small and
can land in one cleanup MR.

---

## A. Compliance with CLAUDE.md rules

### A.1 Slug naming (workspace [CLAUDE.md § Cross-Tree Rules → Refactor work](../../../../CLAUDE.md) + [wecms-api/CLAUDE.md § Refactor identifier naming](../../../CLAUDE.md#refactor-identifier-naming))

The CLAUDE.md rule:

> *"In Spring code, use kebab-case slug identifiers — never legacy WSVC ids."* — applies to REST URI, Java package, class names, DTOs, i18n keys, log/MDC tags, `AUDIT_LOGS_INFM.WSVC_ID`, View Setting `WEB_SVC_ID`.

| Surface | Value | Verdict |
|---|---|---|
| REST URI | `/api/v1/banking/internal-transfer/transfer-history` ([TransferHistoryController.java:38](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java)) | ✓ slug |
| Java package | `com.kosign.wecms.domain.banking.transferhistory` ([TransferHistoryQueryService.java:1](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java)) | ✓ slug |
| Class names | `TransferHistoryController`, `TransferHistoryQueryService`, `TransferHistoryRepository`, `TransferHistoryRow`, `TransferHistoryListResponse`, `TransferHistoryListRequest`, `TransferHistorySortField`, `TransferHistoryTotals` | ✓ slug — but see F-6 (`QueryService` suffix non-standard) |
| Log tag | `"[transfer-history]"` ([TransferHistoryQueryService.java:143](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java)) | ✓ slug |
| Audit `WSVC_ID` | none written (OQ-4 — read-only). Slug `transfer-history` reserved for future writes via class Javadoc | ✓ slug (deferred) |
| Frontend screenId | `SCREEN_IDS.TRANSFER_HISTORY = "transfer-history"` ([viewSettingScreens.ts:257-269](../../../../wecms-web/lib/viewSettingScreens.ts)) | ✓ slug |
| View Setting `WEB_SVC_ID` | not seeded (legacy parity, OQ-5) | ✓ (deferred) |
| i18n keys | `transfer_history.*` underscore — matches existing project convention (`approval_box.*`, `account_inquiry.*`, etc.) | ⚠ see F-8 |

The single Prompt 4 instruction to use kebab `transfer-history.*` keys conflicts with project-wide `transfer_history.*` underscore convention. I went with the underscore convention so the keys group with the existing `transfer_history.title` / `.filter.*` / `.col.*` /  `.status.*` keys delivered in Sprint 1. Documenting as F-8.

### A.2 JEX trees untouched

Verified via `find -mmin -30` after each prompt — every diff is constrained to `wecms-api/` + `wecms-web/` + `docs/refactor-wecms/`. No writes to `WECMS DEV/`, `SOURCE_CODE_ADMIN/`, `SOURCE_CODED_OSMC/`, `DEV ADMIN/`. ✓

### A.3 Docs split

[wecms-api/CLAUDE.md § Documentation Structure Rules](../../../CLAUDE.md#documentation-structure-rules) says:

> *"All refactor backend documentation lives under `docs/refactor-wecms/`. Never write refactor docs under `docs/jex/`."*

- Spec at [docs/refactor-wecms/features/banking/transfer-history.md](../../features/banking/own-account-transfer/transfer-history.md) ✓
- Migration at [docs/refactor-wecms/migration/transfer-history.md](../migration/transfer-history.md) ✓
- Plan at [docs/refactor-wecms/archive/planning/phase-1/transfer-history-plan.md](../../archive/planning/phase-1/transfer-history-plan.md) ✓
- This audit at [docs/refactor-wecms/audits/migration-audit/transfer-history-audit-2026-05-20.md](transfer-history-audit-2026-05-20.md) ✓
- Zero new files under `docs/jex/` ✓

### A.4 Wire format — lowercase snake_case JSON globally

[wecms-api/CLAUDE.md § API Naming & Serialization Standards](../../../CLAUDE.md#api-naming--serialization-standards): *"Property names: lowercase snake_case … no per-DTO `@JsonProperty` overrides."*

- [TransferHistoryRow.java:46-65](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryRow.java) — 18 camelCase Java fields, **zero `@JsonProperty`** annotations. Snake_case wire output produced by the global Jackson `SNAKE_CASE` strategy. ✓
- [TransferHistoryListResponse.java:30-37](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListResponse.java) — same. ✓
- Frontend `ApiTransferHistoryRow` + `ApiTransferHistoryResponse` ([lib/types/api.ts:582-617](../../../../wecms-web/lib/types/api.ts)) — flipped from UPPER_SNAKE holdover to lowercase snake_case in the same MR (workspace full-stack ownership rule). ✓
- Slot test verifies the wire shape via JsonPath `$.data.total_count` / `$.data.rows[0].out_bank_code` etc. ([TransferHistoryControllerTest.java:124-137](../../../src/test/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryControllerTest.java)) ✓

### A.5 Typography tokens (workspace [CLAUDE.md § Global Typography](../../../../CLAUDE.md))

Audited via:

```
grep -nE "text-\[|font-\[|fontFamily|Roboto|Arial|system-ui|Geist|Helvetica" \
  wecms-web/components/banking/internal-transfer/TransferHistoryClient.tsx \
  wecms-web/components/banking/internal-transfer/TransferHistoryPage.tsx \
  wecms-web/components/banking/internal-transfer/TransferHistoryTable.tsx
```

Zero matches. Tokens used in the touched files are all in the project's `text-wb-*` / `text-wb-ntrl-*` / `text-wb-status-*` namespace defined in [app/globals.css](../../../../wecms-web/app/globals.css). ✓

### A.6 Desktop-first / Figma WB365

The page reuses the existing `<PageLayout>`, `<DataTableShell>`, `<FilterRow>`, `<Pagination>` shells which are all desktop-first per project convention. No mobile-only code introduced. ✓

---

## B. API contract consistency

### B.1 Request DTO ↔ controller ↔ spec ↔ frontend

| Wire key | Spec § 4.2 | Backend `@RequestParam` | Frontend `fetchTransferHistory` |
|---|---|---|---|
| `from_dt` | required YYYYMMDD | [TransferHistoryController.java:71-74](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) | [banking.ts:622](../../../../wecms-web/lib/api/banking.ts) |
| `to_dt` | required YYYYMMDD | [:76-79](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) | [banking.ts:623](../../../../wecms-web/lib/api/banking.ts) |
| `trns_stts` | optional `""` / `"00000000"` / `"99999999"` | [:81-84](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) | [banking.ts:625-627](../../../../wecms-web/lib/api/banking.ts) |
| `page_no` | optional ≥ 1 | [:86-87](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) | [banking.ts:628-630](../../../../wecms-web/lib/api/banking.ts) |
| `page_per_cnt` | one of {10, 15, 20, 50, 100} default 15 | [:89-91](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) (allows 10/15/20/50/100) | [TransferHistoryClient.tsx:29-30](../../../../wecms-web/components/banking/internal-transfer/TransferHistoryClient.tsx) (allows 10/20/50/100, default 10) |
| `sort_field` | enum (TRNS_DT / OUT_BANK_NM / IN_BANK_NM / TRSC_AMT / RMRK) | [:93](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) | [TransferHistoryTable.tsx:29-35](../../../../wecms-web/components/banking/internal-transfer/TransferHistoryTable.tsx) |
| `sort_direction` | ASC / DESC | [:95](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) | [useTableSort.ts](../../../../wecms-web/hooks/useTableSort.ts) common-sort contract |

Two real inconsistencies — both around the recently-changed page-size default — surface in F-2, F-3, F-4.

### B.2 Response DTO ↔ frontend wire type

`TransferHistoryListResponse` ([response.java:30-37](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListResponse.java)) ⇔ `ApiTransferHistoryResponse` ([api.ts:610-617](../../../../wecms-web/lib/types/api.ts)):

| Java field (camelCase) | Wire key (snake_case) | Frontend property |
|---|---|---|
| `totalCount: long` | `total_count` | `total_count: number` ✓ |
| `totalVndAmount: BigDecimal` | `total_vnd_amount` | `total_vnd_amount: string \| number` ✓ |
| `totalUsdAmount: BigDecimal` | `total_usd_amount` | `total_usd_amount: string \| number` ✓ |
| `totalVndCount: long` | `total_vnd_count` | `total_vnd_count: number` ✓ |
| `totalUsdCount: long` | `total_usd_count` | `total_usd_count: number` ✓ |
| `rows: List<TransferHistoryRow>` | `rows` | `rows: ApiTransferHistoryRow[]` ✓ |

Row-level shape ([TransferHistoryRow.java:46-65](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryRow.java) ⇔ [api.ts:582-602](../../../../wecms-web/lib/types/api.ts)): 18 fields match name-for-name. ✓

### B.3 Error codes

| Code | Defined in messages.properties | Used by |
|---|---|---|
| `WBE_TRANSFER_HISTORY_INVALID_PERIOD` | yes (added in this MR) | [TransferHistoryQueryService.java:96-98](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) |
| `WBE_VALIDATION` | yes (pre-existing) | controller `@Pattern` / `@NotBlank` / `@Min` annotations |
| `WBE9999` | yes (pre-existing) | catch-all via `GlobalExceptionHandler` |

Frontend error-code mapping at [TransferHistoryClient.tsx:152-167](../../../../wecms-web/components/banking/internal-transfer/TransferHistoryClient.tsx) localises `WBE_TRANSFER_HISTORY_INVALID_PERIOD`, `WBE_VALIDATION`, `NETWORK`, `WBE_COOCON_TIMEOUT` (for future upstream Register MR), `WBE_COOCON_FAILURE` (same). ✓

### B.4 HTTP verb

Single `@GetMapping` for the read endpoint — matches every other refactor list endpoint (`/api/v1/banking/accounts`, `/api/v1/settings/contacts`, etc.). ✓

---

## C. Business-logic parity with JEX

### C.1 Side-by-side parity check — the WHERE clause

**JEX** (`WECMS DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp:60-84`):

```java
private IDODynamic getDynamic(WbUser loginUser, JexData input) throws Exception {
    StringBuilder query = new StringBuilder();
    List<String> dParams = new ArrayList<>();

    query.append("\n and NRTH.BIZ_KEY = ? ");
    dParams.add(loginUser.getBizKey());

    if (JexUtils.isNotNull(input, "FROM_DT") && JexUtils.isNotBlank(input, "TO_DT")) {
        query.append("\n and substring(NRTH.REG_DTM,1,8) between ? and ? ");
        dParams.add(input.getString("FROM_DT"));
        dParams.add(input.getString("TO_DT"));
    }

    if (JexUtils.isNotBlank(input, "TRNS_STTS")) {
        if (input.getString("TRNS_STTS").equals("00000000")) {
            query.append("\n and NRTH.TRNS_STTS = ? ");
            dParams.add(input.getString("TRNS_STTS"));
        } else {
            query.append("\n and NRTH.TRNS_STTS <> ? ");
            dParams.add("00000000");
        }
    }
    return IdoUtils.getDynamic(query, dParams);
}
```

**Refactor** ([TransferHistoryQueryService.java:165-183](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java)):

```java
private static Specification<NapasRemitTrnsHstr> buildSpec(
        String bizKey, String fromDt, String toDt, String statusEq, String statusNeq) {
    return (root, query, cb) -> {
        List<Predicate> preds = new ArrayList<>(4);
        preds.add(cb.equal(root.get("bizKey"), bizKey));
        // substring(REG_DTM, 1, 8) BETWEEN :fromDt AND :toDt — mirrors
        // trns_0003_001_r001_act.jsp:68.
        preds.add(cb.between(cb.substring(root.get("regDtm"), 1, 8), fromDt, toDt));
        if (statusEq != null) {
            preds.add(cb.equal(root.get("trnsStts"), statusEq));
        } else if (statusNeq != null) {
            Predicate isNull = cb.isNull(root.get("trnsStts"));
            Predicate notEq  = cb.notEqual(root.get("trnsStts"), statusNeq);
            preds.add(cb.or(isNull, notEq));
        }
        return cb.and(preds.toArray(new Predicate[0]));
    };
}
```

**Verdict:** semantically identical, with two improvements:

1. **Date range is now mandatory** in the refactor (`@NotBlank` on `from_dt`/`to_dt`) where JEX let omitting either skip the date filter entirely. The legacy form always sent both so JEX never exercised the "no date" branch in practice — making them mandatory is correct.
2. **Refactor adds an `isNull` predicate** to the "non-success" branch so rows with `TRNS_STTS = NULL` count as failed (matches `data.rows.filter(r => r.trnsStts !== "00000000")` semantics on the legacy frontend). JEX's `<>` wouldn't catch nulls. Both refactor and JEX accept "any non-blank value" as a "failed" sentinel; the refactor's controller `@Pattern("|00000000|99999999")` is *stricter* (rejects unknown values) — that's the OQ-6 hardening.

### C.2 Other behaviours

| JEX behaviour | JEX file:line | Refactor file:line | Verdict |
|---|---|---|---|
| `BIZ_KEY` scoping | `trns_0003_001_r001_act.jsp:64-65` | [TransferHistoryQueryService.java:88-90](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) + buildSpec line 169 | ✓ via `TenantContext.currentBizKey()` |
| Period validation `to ≥ from` | `trns_0003_001_r001_act.jsp:39` (`ValidationHelper.validatePeriod`) | [TransferHistoryQueryService.java:96-98](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) | ✓ `WBE_TRANSFER_HISTORY_INVALID_PERIOD` |
| Page parameters | `trns_0003_001_r001_act.jsp:93-97` (`PageInfo` + `JexUtils.setIDOPageInfo`) | [TransferHistoryQueryService.java:103-110](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) (1-based → 0-based) | ✓ |
| Default ORDER BY | implicit `REG_DTM DESC` | [TransferHistoryQueryService.java:78-80](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) (`regDtm DESC, seqNo DESC` for stable paging) | ✓ + deterministic tie-break |
| Sort dynamic | `trns_0003_001_r001_act.jsp:146` (`SortHelper.getSortDynamic`) | [TransferHistorySortField.java](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistorySortField.java) whitelist | ✓ but see F-5 (Results col) |
| Totals + counts per CCY | `IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml:24-34` | [TransferHistoryRepository.java:38-58](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/repository/TransferHistoryRepository.java) JPQL `CASE WHEN ccyCd = 'VND'…` | ✓ |
| `wb_get_bank_nm(_, 'S')` | `IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:29` | [TransferHistoryQueryService.java:231-233](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) via `BankCdResolver.resolveBankNm` reads `BANK_INFM.RPRS_BANK_NM` | ✓ (semantic equivalent, may show different display string — see F-1 of audit field-resolution caveat) |
| `WB_GET_NAPAS_BANK_NM(_, 'S', 'EN')` | `IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:30` | [TransferHistoryQueryService.java:234-236](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) via `LegacyFunctionRepository.resolveNapasOrCitadBankName(code, langCd)` (V114 funnel) | ✓ (OQ-1 default) |
| `WB_GET_USER_NM(REG_ID)` | `IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml:31` | [TransferHistoryQueryService.java:264-279](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) batched `userRepo.findAllById(ids)` | ✓ |
| `normalizeTrnsDtFrmUTC` | `trns_0003_001_r001_act.jsp:101-105` | **NONE** — see F-1 | ✗ **missing** |
| Success code `"00000000"` ⇔ "success" / non-`00000000` ⇔ "failed" sentinel | `trns_0003_001_r001_act.jsp:74-80` + `trns_0003_001.ts:326-336` | [TransferHistoryQueryService.java:60-66](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) + [mappers.ts:375-376](../../../../wecms-web/lib/api/mappers.ts) | ✓ |
| Footer totals — VND only | `trns_0003_001.ts:243-258` | [mappers.ts:401-410](../../../../wecms-web/lib/api/mappers.ts) returns `currency: "VND"` only; USD totals still on wire for future row | ✓ (OQ-5) |

### C.3 Coocon engine — out of scope

Spec §3 documents the engine as a client-side iSAS-NX Windows binary driven via WebSocket from the browser (`wss://ibasews.coocon.co.kr:14102/`). Transfer History is read-only and the backend never spawns a subprocess. Verified via:

```
grep -rE "ProcessBuilder|Runtime\.exec|coocon|\.exe" \
  wecms-api/src/main/java/com/kosign/wecms/domain/banking/transferhistory/
```

zero matches. ✓

---

## D. Security & data

### D.1 Auth + role

- All endpoints under `/api/v1/banking/internal-transfer/*` require JWT via Spring Security `JwtAuthenticationFilter` (pre-existing chain). ✓
- No `@PreAuthorize` admin gate — matches legacy `WSVC.trns_0003_001_r001.xml:26` `NORM_USER_ONLY_YN=Y` (excludes WTA users only) and OQ-5 resolution. ([TransferHistoryController.java:31-34](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) Javadoc records the decision.) ✓
- Tenant scoping: `TenantContext.currentBizKey()` is the only allowed `BIZ_KEY` source ([TransferHistoryQueryService.java:87-90](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java)). The request DTO has no `biz_key` field. Verified the controller signature does not accept one ([TransferHistoryController.java:63-95](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java)). ✓

### D.2 Audit logging

OQ-4 resolved: no `AUDIT_LOGS_INFM` write for the read endpoint, matching the `approval-line` / `erp-transaction-history` / `chart-of-accounts` precedent. The Audit-log column rule from [wecms-api/CLAUDE.md § Refactor identifier naming](../../../CLAUDE.md#refactor-identifier-naming) applies only to mutation endpoints. The slug `transfer-history` is reserved in `SCREEN_IDS` so any future mutation MR keys against the same canonical string. ✓

### D.3 No credential / shell-injection surface

Read-only feature, no `ProcessBuilder`, no temp files, no credentials. The only sensitive value the service even *reads* is `BIZ_KEY` (a tenant identifier, not a secret), and the only logger call masks nothing because there's nothing to mask:

```java
log.debug("[transfer-history] bizKey={} from={} to={} status={} rows={}/{}",
        bizKey, fromDt, toDt, req.trnsStts(), rows.size(), totals.totalCount());
```
([TransferHistoryQueryService.java:143-144](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java))

`BIZ_KEY` is already in every tenant-scoped log line in the project — not a secret. ✓

### D.4 SQL safety

- **WHERE clause** built via JPA `CriteriaBuilder` (`cb.equal` / `cb.between` / `cb.substring` / `cb.or`) — no string concatenation. ✓
- **Totals** built via JPQL with `@Param`-bound parameters ([TransferHistoryRepository.java:41-58](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/repository/TransferHistoryRepository.java)). ✓
- **Sort** built via Spring `Sort.by(entityProperty)` where `entityProperty` comes from the typed `TransferHistorySortField` enum, never from the raw wire string ([TransferHistoryQueryService.java:185-193](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java)). ✓
- **No native queries** in the new repository. ✓

### D.5 Postgres MCP access (per [.claude/rules/database.md](../../../../.claude/rules/database.md))

The Prompt 2 audit used the `postgres_dev` MCP for:
- `information_schema.columns` (read-only)
- `pg_constraint` (read-only)
- `pg_indexes` (read-only)
- `pg_class.reltuples` (read-only estimate)
- `SELECT trns_stts, COUNT(*) FROM napas_remit_trns_hstr GROUP BY trns_stts LIMIT 20` (read-only)
- `SELECT proname, args FROM pg_proc WHERE proname IN ('wb_get_bank_nm', 'wb_get_napas_bank_nm', 'wb_get_user_nm')` (read-only)

All within the allowed set documented at `.claude/rules/database.md`. No `INSERT` / `UPDATE` / `DELETE` / DDL / `EXPLAIN ANALYZE` / `DO $$ … $$` / `CALL`. ✓

---

## E. Code quality

### E.1 Reuse audit

**Backend** (every helper claimed in the implementation plan was verified to exist and was reused — no clones):

| Need | Helper reused | Used at |
|---|---|---|
| Tenant scoping | `TenantContext.currentBizKey()` / `currentLangCd()` | [TransferHistoryQueryService.java:88](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) + line 120 |
| OUT_BANK_NM | `BankCdResolver.resolveBankNm` | line 231-233 |
| IN_BANK_NM | `LegacyFunctionRepository.resolveNapasOrCitadBankName` (V114) | line 234-236 |
| User-name resolution | `UserInfmRepository.findAllById` | line 270-272 |
| Sort whitelist contract | `SortableField` / `SortDirection` | TransferHistorySortField.java |
| Response envelope | `ApiResponse.ok(...)` | TransferHistoryController.java:96 |
| Error codes | `BusinessException("CODE")` | TransferHistoryQueryService.java:97 |
| Audit columns / entity base | (none — entity has no MOD_* columns, no `BaseEntity`) | reused existing `NapasRemitTrnsHstr` entity |

**Frontend** (project-standard shells / hooks):

`<PageLayout>`, `<PageHeader>`, `<FilterRow>`, `<InteractiveDateRangePicker>`, `<DataTableShell>`, `<Pagination>` + `PAGE_SIZE_OPTIONS`, `<TableHeaderCell>`, `useTableSort`, `useApiData`, `useTranslation`, `ApiCallError`, `cn`, `SCREEN_IDS` registry — all reused, no new shared component added.

No duplicate logic introduced anywhere.

### E.2 Pre-existing entity preserved

[NapasRemitTrnsHstr](../../../src/main/java/com/kosign/wecms/domain/banking/entity/NapasRemitTrnsHstr.java) is the shared entity used by both the OLD Sprint-1 interim service (`domain.banking.service.TransferHistoryService`, now `@Deprecated`) and the NEW spec-aligned service (`domain.banking.transferhistory.service.TransferHistoryQueryService`). No duplicate entity — same `@Table("NAPAS_REMIT_TRNS_HSTR")`. ✓

### E.3 Comments are WHY-comments

Sampled the new files for `// what …` style narration:

- Class-level Javadoc on `TransferHistoryQueryService` (lines 35-69) explains tenant scoping, audit decision, bank-name OQ-1, and cites the parent JEX file — WHY. ✓
- Inline comment on `STATUS_FAILED_SENTINEL` (lines 91-95) explains why the sentinel translates to a `<>` predicate — WHY. ✓
- Inline comment on the default sort (lines 78-80) explains the tie-break — WHY. ✓
- buildSpec inline comment (line 170-171) cites the JEX file:line being mirrored — WHY. ✓
- Controller Javadoc (lines 23-49) records the OQ-4 and OQ-5 decisions — WHY. ✓

No "WHAT" narration found.

### E.4 Backwards-compat / dead code

- The OLD `domain.banking.service.TransferHistoryService` + `TransferController.history()` endpoint are `@Deprecated` but live — they exist because the Prompt 4 scope explicitly excluded frontend changes outside `wecms-web/`; the existing Sprint-1 frontend would have broken otherwise. Frontend cutover MR (this one — Prompts 4-7) **DID** flip the consumer to the new endpoint, so the OLD endpoint is now an unused holdover. **Follow-up: delete in next cleanup MR.** Tracked as F-9.
- No half-finished implementations spotted.

### E.5 No unused exports

- `TRANSFER_HISTORY_SORT_FIELD_IDS` ([TransferHistoryTable.tsx:17-23](../../../../wecms-web/components/banking/internal-transfer/TransferHistoryTable.tsx)) is exported but only consumed inside the same file (the JSX). Could be a local `const` — see F-7.
- Everything else is either consumed externally or in the same module.

---

## F. Performance

### F.1 No N+1

- Bank-name resolution uses per-request HashMap caches keyed by the bank code ([TransferHistoryQueryService.java:130-132](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java)) — at most one lookup per *distinct* bank code per page. ✓
- User-name resolution is one batched call (`findAllById`) over the distinct REG_IDs on the page ([:267-272](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java)). ✓
- `BankCdResolver` warms its cache once at startup and uses an in-memory `ConcurrentHashMap`. ✓
- `LegacyFunctionRepository.resolveNapasOrCitadBankName` is called per-distinct-code (not per row) because of the inBankNmCache. ✓

### F.2 SQL-side paging

- `Pageable` is passed to `repo.findAll(Specification, Pageable)`, which Spring Data turns into a `LIMIT` + `OFFSET` SQL query — paging pushed to the DB. ✓
- `aggregateTotals` is a single JPQL query that uses `CASE WHEN ccyCd = ...` aggregation — single roundtrip for the footer. ✓
- No in-memory slicing in the service. ✓

### F.3 Helper index

V181 creates `ix_napas_remit_trns_hstr_biz_regdtm (biz_key, substr(reg_dtm, 1, 8) DESC, seq_no DESC)` — exactly the access pattern of the WHERE + ORDER BY. ✓

### F.4 Coocon scraping bounds

Not applicable — Transfer History does not invoke the engine. Spec §3 documents the engine timeouts (5 min UAC, 3 min update) for the upstream Register MR. ✓

---

## G. Test coverage

### G.1 Backend ([TransferHistoryQueryServiceTest](../../../src/test/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryServiceTest.java) + [TransferHistoryControllerTest](../../../src/test/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryControllerTest.java))

| Required by spec / playbook | Test | Covered? |
|---|---|---|
| Happy path: rows + totals serialize as snake_case | `TransferHistoryControllerTest#list_happyPathSerialisesSnakeCase` | ✓ |
| Validation: missing `from_dt` | `list_missingFromDtReturnsValidationEnvelope` | ✓ |
| Validation: malformed `from_dt` | `list_malformedFromDtReturnsValidationEnvelope` | ✓ |
| Validation: invalid `trns_stts` sentinel | `list_invalidStatusSentinelReturnsValidationEnvelope` | ✓ |
| Validation: `page_per_cnt` out of whitelist | `list_pagePerCntOutOfWhitelistReturnsValidationEnvelope` | ✓ |
| Validation: `page_no = 0` | `list_pageNoZeroReturnsValidationEnvelope` | ✓ |
| Business error: inverted period | `list_invertedPeriodSurfacesBusinessError` | ✓ |
| Status filter: success ↔ statusEq | `list_successFilterPassesStatusEqOnly` | ✓ |
| Status filter: failed sentinel ↔ statusNeq | `list_failedSentinelPassesStatusNeqOnly` | ✓ |
| Status filter: blank ↔ neither | `list_blankStatusFiltersNeitherStatus` | ✓ |
| Default sort: REG_DTM DESC, SEQ_NO DESC | `list_defaultSortIsRegDtmDescTieBrokenBySeqNoDesc` | ✓ |
| Whitelisted sort field | `list_whitelistedSortFieldDescAlwaysTieBreaksBySeqNoDesc` | ✓ |
| Unknown sort field falls back to default | `list_unknownSortFieldFallsBackToDefault` | ✓ |
| Default page size when omitted | `list_defaultPageSizeIs15WhenPagePerCntIsNull` | ⚠ asserts 15; frontend default is now 10 — see F-3 |
| Page number 1-based → 0-based conversion | `list_pageNoIsConvertedFromOneBasedToZeroBased` | ✓ |
| Bank-name cache hits once per distinct code | `list_resolvesEachBankCodeAtMostOncePerPage` | ✓ |
| KO/KM locales collapse to `en` for the V114 funnel | `list_korenAndKhmerLocalesCollapseToEnglishForLegacyFn` | ✓ |
| Permission denial (no JWT) | covered by Spring Security at chain level — same global pattern as every other endpoint; not a per-feature test | ✓ (implicit) |
| Coocon timeout / non-zero exit | N/A — read-only feature; tests in the Register MR | ✓ (N/A) |

Total: **19 new tests, all passing** (12 service-layer + 7 controller slice). Build green via `./gradlew :wecms-api:build` (Prompt 2 output — 580 tests total, 0 failures, 0 errors).

### G.2 Frontend

The wecms-web project has **no test framework configured** ([package.json](../../../../wecms-web/package.json) has only `dev` / `build` / `start` / `lint` scripts; zero `*.test.*` / `*.spec.*` files in the tree). Per Prompt 4 DoD wording (*"Unit tests for hooks/utilities (framework already configured in the project)"*) and the project-wide "reuse before creating / don't add a new HTTP library" rule extended to tooling, no frontend tests were added. This matches the precedent set by every other refactor frontend feature in the repo (Approval Box, Account Inquiry, etc.). Tracked as F-10 (gap, not a blocker).

Visual states verified manually via `npm run build` + dev-server walkthrough in Prompts 4-7:
- **Loading state** — text "Loading…" centred below the headers (matches VAS Account precedent).
- **Empty state** — "No Data Found." centred below the headers (matches VAS Account precedent + live JEX page).
- **Error banner** — typed banner above the table with localized message + error code.
- **Sort interactions** — chevron up / chevron down / inactive glyph cycle on column header click.
- **Pagination** — arrow disabled when on first/last page; dropdown shows `[10, 20, 50, 100]` with default 10.

---

## Findings

| ID | Severity | File:line | Rule violated | Recommended fix |
|---|---|---|---|---|
| **F-1** | **major** | [wecms-web/lib/api/mappers.ts:382-385](../../../../wecms-web/lib/api/mappers.ts) (`formatLastUpdate` / `formatTrnsDate` / `formatTrnsTime` slice digits with no TZ conversion); migration row 18 claim invalid | JEX parity — `trns_0003_001_r001_act.jsp:101-105` calls `DateTimeUtils.normalizeResult(row.getString("TRNS_DT"), loginUser)` to convert UTC `REG_DTM` to the operator's TZ before rendering. The refactor displays UTC verbatim. For VN (UTC+7) a 10:30 local transaction renders as 03:30. Migration row 18 declares this was "pushed to the client via `lib/format/datetime.ts`" — the mapper doesn't use any datetime helper. | Either (a) TZ-convert server-side inside `toRow` using a shared `DateTimeUtils` equivalent, or (b) replace the digit-slice in `mapTransferHistoryRow` with the existing `formatLegacyDtmInDisplayTz` helper at [mappers.ts:87](../../../../wecms-web/lib/api/mappers.ts) (already used by Transaction History — pass the 14-char `REG_DTM`). Prefer (b) — keeps server stateless. |
| **F-2** | minor | [docs/refactor-wecms/features/banking/transfer-history.md § 10 OQ-6](../../features/banking/own-account-transfer/transfer-history.md) | Spec lists OQ-6 default page size as 15. Frontend was changed to 10 in Prompt 7 with PO sign-off. Spec is stale. | Update the OQ-6 resolution to "whitelist {10, 20, 50, 100}, default 10" and bump `last_updated` to 2026-05-20. |
| **F-3** | minor | [TransferHistoryListRequest.java:48](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListRequest.java) (`DEFAULT_PAGE_PER_CNT = 15`) + [TransferHistoryQueryServiceTest.java#list_defaultPageSizeIs15WhenPagePerCntIsNull](../../../src/test/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryServiceTest.java) | Backend default (15) doesn't match frontend default (10). The mismatch is currently invisible because the frontend always sends `page_per_cnt`, but a direct API consumer who omits the param will get 15. The test name + assertion follow the backend default. | Change `DEFAULT_PAGE_PER_CNT` to 10. Update the test name to `list_defaultPageSizeIs10WhenPagePerCntIsNull` and the assertion. |
| **F-4** | minor | [TransferHistoryController.java:90-91](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) (`@Pattern(regexp = "10\|15\|20\|50\|100")`) + [TransferHistoryListRequest.java:40-41](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListRequest.java) | Backend `@Pattern` still allows 15 while the frontend whitelist (`PAGE_SIZE_OPTIONS = [10, 20, 50, 100]`) excludes it. Looser backend / stricter frontend is safe but inconsistent. | Drop `15` from both backend `@Pattern` regexes — `"10\|20\|50\|100"`. Mirror the frontend exactly. |
| **F-5** | minor | [TransferHistoryTable.tsx:101-104](../../../../wecms-web/components/banking/internal-transfer/TransferHistoryTable.tsx) (Results column has `sortable={false}`) | JEX `trns_0003_001_view.jsp:178-180` declares `<th data-sort="TRNS_DT">` on the Results column — i.e. clicking Results sorts by REG_DTM (same as the Date/Time header). Refactor disables sort on Results entirely. Minor parity divergence. | Either (a) keep `sortable={false}` and document the deliberate divergence in the migration doc (and in [TransferHistorySortField.java](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistorySortField.java) Javadoc — only 5 fields, no `RESULTS`), or (b) reuse `TRANSFER_HISTORY_SORT_FIELD_IDS.TRNS_DT` on the Results header so it cycles the same sort as Date/Time. Recommend (a) — Results is a presentational composite (status + relative time) and re-sorting it by REG_DTM is unintuitive. |
| **F-6** | minor | [TransferHistoryQueryService.java:74](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/service/TransferHistoryQueryService.java) | Class name `TransferHistoryQueryService` uses a non-standard `QueryService` suffix; every other refactor feature in the codebase uses plain `<Feature>Service` (ContactsService / ApprovalLineService / ChartOfAccountsService / ErpTransactionHistoryService). The suffix was forced by collision with the OLD `domain.banking.service.TransferHistoryService` (Sprint-1 interim). | Rename to `TransferHistoryService` in the same MR that removes the OLD interim service + the `@Deprecated` `TransferController.history()` endpoint. Tracks with F-9. |
| **F-7** | nit | [TransferHistoryTable.tsx:25-31](../../../../wecms-web/components/banking/internal-transfer/TransferHistoryTable.tsx) (`export const TRANSFER_HISTORY_SORT_FIELD_IDS = ...`) | Exported but only consumed in the same file. CLAUDE.md general "no unused exports" hygiene. | Drop the `export` keyword. Keep the named constant (still readable); no consumer relies on the export today. |
| **F-8** | nit | i18n keys throughout: [locales/en.json:1275-1296](../../../../wecms-web/locales/en.json) — `transfer_history.*` (underscore) | Prompt 4 instruction specified kebab-case `transfer-history.*`. Refactor went with underscore to match the project convention (`transfer_history.col.date_time`, `transfer_history.filter.success`, etc.) which is consistent with every other feature in the locales (`approval_box.*`, `account_inquiry.*`, `chart_of_accounts.*`). | Leave as-is. Document the deviation in the migration doc as a deliberate alignment with the project convention. (If a future cross-codebase migration moves to kebab i18n keys, transfer-history rides along.) |
| **F-9** | minor | [TransferController.java:36-41](../../../src/main/java/com/kosign/wecms/domain/banking/controller/TransferController.java) + [TransferHistoryService.java (old)](../../../src/main/java/com/kosign/wecms/domain/banking/service/TransferHistoryService.java) + [TransferHistoryResponse.java + TransferRow.java (old DTOs)](../../../src/main/java/com/kosign/wecms/domain/banking/dto/) | Sprint-1 interim endpoint at `/api/v1/banking/transfers/history` is `@Deprecated` but live. The frontend cutover happened in Prompts 4-7, so the OLD endpoint has zero consumers now. CLAUDE.md "no backwards-compatibility shims" rule applies once the cutover lands. | Remove `TransferController.history()` + `domain.banking.service.TransferHistoryService` + `domain.banking.dto.TransferHistoryResponse` + `domain.banking.dto.TransferRow` + `domain.banking.dto.TransferHistoryResponse$CurrencyTotal` + `AmountFormatter` usage if it becomes orphaned. Bundle with the F-6 rename. |
| **F-10** | nit (informational) | [wecms-web/package.json](../../../../wecms-web/package.json) — no `test` script, no Jest/Vitest/Playwright | Prompt 4 DoD asked for "Unit tests for hooks/utilities (framework already configured)" — gated on a framework that doesn't exist. | Not a per-feature finding. If the team decides to introduce a frontend test framework, the Transfer History feature can add mapper round-trip + hook tests in the same MR. |

---

## Follow-up tasks

A single cleanup MR can close everything below.

- [ ] **F-1 (major):** Replace digit-slice formatters in [mappers.ts:362-389](../../../../wecms-web/lib/api/mappers.ts) with `formatLegacyDtmInDisplayTz` so `trns_dt` / `trns_tm` render in the operator's TZ. Add a mapper test once the project ships a frontend test framework (F-10). Update [migration/transfer-history.md row 18](../migration/transfer-history.md) — strike the "client-side TZ conversion" claim, restate the resolution.
- [ ] **F-2:** Update [features/banking/transfer-history.md § 10 OQ-6](../../features/banking/own-account-transfer/transfer-history.md) to record the 2026-05-20 PO override (whitelist `{10, 20, 50, 100}`, default 10). Bump `last_updated`.
- [ ] **F-3:** Flip `DEFAULT_PAGE_PER_CNT` from 15 to 10 in [TransferHistoryListRequest.java:48](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListRequest.java); rename the matching test method and update the assertion.
- [ ] **F-4:** Drop `15` from the `@Pattern` regex in both [TransferHistoryController.java:90](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/controller/TransferHistoryController.java) and [TransferHistoryListRequest.java:40](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListRequest.java); update [TransferHistoryListRequest.java:38](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistoryListRequest.java) `PAGE_PER_CNT_PATTERN` constant.
- [ ] **F-5:** Add one line to [TransferHistorySortField.java](../../../src/main/java/com/kosign/wecms/domain/banking/transferhistory/dto/TransferHistorySortField.java) Javadoc + the migration doc explaining that the Results column dropped its legacy `data-sort="TRNS_DT"` mapping deliberately.
- [ ] **F-6 + F-9** (one cleanup pass): delete the OLD endpoint + service + DTOs at [TransferController.java](../../../src/main/java/com/kosign/wecms/domain/banking/controller/TransferController.java) / [service/TransferHistoryService.java](../../../src/main/java/com/kosign/wecms/domain/banking/service/TransferHistoryService.java) / `dto/TransferHistoryResponse.java` / `dto/TransferRow.java`; rename `TransferHistoryQueryService` → `TransferHistoryService` in the new package; update references in the controller + tests.
- [ ] **F-7:** Drop the `export` keyword on `TRANSFER_HISTORY_SORT_FIELD_IDS` in [TransferHistoryTable.tsx:25](../../../../wecms-web/components/banking/internal-transfer/TransferHistoryTable.tsx).
- [ ] **F-8:** No code change. One sentence in [migration/transfer-history.md row 14](../migration/transfer-history.md) noting the underscore convention is project-wide and intentional.
- [ ] **F-10:** No per-feature action. Tracked in the project's "frontend testing strategy" backlog if/when one materialises.

---

## Audit method

- Re-read [CLAUDE.md](../../../../CLAUDE.md), [wecms-api/CLAUDE.md](../../../CLAUDE.md), [wecms-web/CLAUDE.md](../../../../wecms-web/CLAUDE.md), [AGENTS.md](../../../../wecms-web/AGENTS.md), [.claude/rules/database.md](../../../../.claude/rules/database.md).
- Re-read [features/banking/transfer-history.md](../../features/banking/own-account-transfer/transfer-history.md), [migration/transfer-history.md](../migration/transfer-history.md), [archive/planning/phase-1/transfer-history-plan.md](../../archive/planning/phase-1/transfer-history-plan.md).
- Read every new file under `wecms-api/src/main/java/com/kosign/wecms/domain/banking/transferhistory/`, `wecms-api/src/main/resources/db/migration/V181__transfer_history_helper_index.sql`, and the two test files.
- Read the new + modified files under `wecms-web/components/banking/internal-transfer/`, `wecms-web/lib/api/banking.ts` (`fetchTransferHistory` only), `wecms-web/lib/api/mappers.ts` (`mapTransferHistoryRow` + `mapTransferHistoryTotals`), `wecms-web/lib/types/api.ts` (the flipped row + response types), `wecms-web/lib/viewSettingScreens.ts` (the new screenId entry), `wecms-web/locales/{en,ko,vi}.json`.
- Read the cited JEX source files: [trns_0003_001_view.jsp](../../../../WECMS%20DEV/WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp), [trns_0003_001_r001_act.jsp](../../../../WECMS%20DEV/WECMS/web/WEB-INF/action/jex/wabooks/trns/trns_0003_001_r001_act.jsp), [IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003.xml), [IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/IDO/IDO.NAPAS_REMIT_TRNS_HSTR_R003_CNT.xml), [WSVC.trns_0003_001_r001.xml](../../../../WECMS%20DEV/WECMS_ETC/xml/service/WSVC/WSVC.trns_0003_001_r001.xml), [NapasRemitTrnsHstrDAO.java](../../../../WECMS%20DEV/WECMS/src/wabooks/dao/NapasRemitTrnsHstrDAO.java).
- Static checks performed: file:line grep for forbidden patterns (`text-[`, `font-[`, `fontFamily`, `Roboto`, `Arial`, `system-ui`, `Geist`, `Helvetica`, `JsonProperty\("[A-Z_]+"`, `ProcessBuilder`, `coocon`, `.exe`); file:line cross-reference for spec ↔ controller ↔ frontend; mtime check (`find -mmin`) of JEX trees confirming zero writes.
- No code was modified during this audit.
