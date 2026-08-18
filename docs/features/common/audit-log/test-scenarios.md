# Audit Log — Test Scenarios (Backend)

**Part of:** Common → audit-log
**Last verified:** 2026-06-23

Test files (verified present):
- `src/test/java/.../common/auditlog/service/AuditLogServiceTest.java` (Mockito unit)
- `src/test/java/.../common/auditlog/service/AuditLogServiceTransactionAnnotationTest.java` (reflection pin)

## Covered scenarios (`AuditLogServiceTest`)

| # | Test | Asserts |
|---|---|---|
| 1 | `append_writesOneRowPerSupportedLocale_allSharingOneSeqNo` | `entryRepository.save` called **4×**; all rows share seq `344`; `lang_cd` is exactly `[EN, KO, VI, KM]`; per-locale activity/detail (`actv-en`…`detl-km`); `bizKey`/`regId`/`regNm`/`wsvcId`/`refNo`/`refNo1`/`refNo2` from context+tenant; `regDtm` length 14; allocator called **exactly once**. |
| 2 | `append_blankActivityKey_leavesActvColumnNull` | Blank `activityKey` → all 4 rows have null `AUDIT_LOGS_ACTV`, detail still resolved. |
| 3 | `append_repositoryThrows_swallowed_noPropagation` | First `save` throws → `append` returns normally (no rethrow). |
| 4 | `append_seqNoAllocatorThrows_swallowed` | Allocator throws → **no `save`** attempted. |
| 5 | `append_seqNoAllocatorReturnsNull_swallowed` | Allocator returns null → **no `save`**. |
| 6 | `append_nullContext_swallowed` | `append(null)` → no allocator call, no save. |
| 7 | `append_blankWsvcId_swallowed` | Blank `wsvcId` → no allocator call, no save. |
| 8 | `append_blankRefNo1_swallowed` | Blank `refNo1` → no allocator call, no save. |
| 9 | `append_blankDetailKey_swallowed` | Blank `detailKey` (NOT-NULL detail column) → no allocator call, no save. |
| 10 | `append_emptyTenantContext_swallowed` | Cleared `TenantContext` → no allocator call, no save. |

These pin the three load-bearing invariants:
- **Locale fan-out** — one event → four rows, one shared seq-no (test 1).
- **Swallow-on-failure** — every failure path leaves the caller undisturbed (tests 3–10).
- **Allocate-once** — seq allocated once per `append`, not per locale (test 1).

## Covered scenarios (`AuditLogServiceTransactionAnnotationTest`)

| # | Test | Asserts |
|---|---|---|
| 1 | `append_isAnnotatedTransactionalRequiresNew` | Reflection: `append` carries `@Transactional` with `propagation = REQUIRES_NEW`. Guards against accidental removal / switch to `REQUIRED`. |

## Not yet covered (gaps)

- **Runtime `REQUIRES_NEW` rollback isolation** — that the audit row commits even when the caller's
  business tx rolls back. Mockito cannot exercise Spring's tx aspect; the annotation pin (above) is the
  current stand-in. The Javadoc references an `AuditLogServiceIntegrationTest` for this, but ⚠️ that file
  was **not found** in the test tree — the runtime test is **deferred** to a future Testcontainers
  `@SpringBootTest` MR (verified manually until then).
- **Seq-no race** under real concurrency (two callers, same `MAX+1`) — accepted legacy parity, no test.
- **i18n missing-key fallback** (key echoes verbatim when a locale lacks the entry) — behaviour documented
  in `AuditLogContext.java:36-40`, not unit-tested here.

## Manual verification recipe (for the deferred rollback test)

1. Call a consumer service whose business write rolls back after `auditLogService.append(...)` ran.
2. Confirm the `AUDIT_LOGS_INFM` rows persisted (4 locale rows, shared seq) despite the business rollback.
3. Confirm an induced audit DB failure leaves the business write intact (audit gap visible in WARN logs).
