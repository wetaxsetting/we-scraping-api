# Exchange Rate Inquiry — Test Scenarios

Backend coverage exists (service + batch). **Frontend tests are MISSING.**

---

## 1. Backend — existing tests

| Test class | Covers |
|---|---|
| `ExchangeRateServiceTest` | `list` (filters, latest-round vs explicit seq_no, tenant scope, date resolution, sort, on-demand/fallback), `listBanks`, `listCurrencies` |
| `ExchangeRateBatchServiceTest` | the three jobs' tenant fan-out, gateway success-code handling, per-job behaviour |
| `ExchangeRateBatchPersistenceTest` | persistence modes — UPSERT (EXCH_0001), SKIP_IF_EXISTS + UPSERT (EXCH_0010), INSERT_NEW MAX+1 (EXCH_0020); existence key incl. `ref_ccy_cd` |
| `ExchangeRateBatchSchedulerTest` | scheduler gating (registers only when `wecms.exchange-rate.batch.enabled=true`) |

## 2. Backend — scenario checklist (read path)

| # | Scenario | Expected |
|---|---|---|
| R1 | GET with no params | resolves today (Asia/Ho_Chi_Minh), Shinhan default, latest round per currency |
| R2 | GET `seq_no=1` | only round-1 rows for the date |
| R3 | GET `seq_no=2` | only round-2 rows for the date |
| R4 | GET omit `seq_no`, two rounds exist | latest round per currency (DISTINCT ON) — no duplicate currency rows |
| R5 | GET `ref_date=2025-07-02` (dashed) | `WBE_VALIDATION` (must be 8 digits) |
| R6 | GET `ref_date=abc` | `WBE_VALIDATION` |
| R7 | GET `seq_no=123` | `WBE_VALIDATION` (1–2 digits) |
| R8 | GET `bank_cd` > 20 chars | `WBE_VALIDATION` |
| R9 | GET `sort_field=BALANCE` (unknown) | graceful default order, no 4xx |
| R10 | GET `sort_field=CASH_BUY&sort_direction=DESC` | rows sorted by Cash Buy desc, nulls last |
| R11 | GET valid date, no rows, no fallback date | empty `rates`/`rounds`, `ref_date` echoed |
| R12 | GET no rows, no explicit date | on-demand catch-up fires (REQUIRES_NEW), then most-recent-date fallback |
| R13 | tenant A token reading tenant B data | empty — never another tenant's rows |
| R14 | no / invalid JWT | 401 `WBE1009` |
| R15 | authenticated, no `payment-overseas-exchange` access | 403 `WBE1003` |
| R16 | GET `/banks` | distinct bank codes + names for the tenant (may be `[]`) |
| R17 | GET `/currencies` | distinct display currencies (foreign side via CASE) |

## 3. Backend — scenario checklist (batch write)

| # | Scenario | Expected |
|---|---|---|
| W1 | EXCH_0001 first run | inserts seq=1 rows per (tenant × currency) |
| W2 | EXCH_0001 re-run | UPSERTs seq=1 |
| W3 | EXCH_0010 with seq=1 present | seq=1 preserved (skip), seq=2 upserted |
| W4 | EXCH_0020 repeated runs | each inserts a fresh round seq=MAX+1 (probe ccy='VND') |
| W5 | gateway returns non-`00000000` | row rejected, logged, not persisted |
| W6 | gateway transport failure | nothing persisted; audited by `NxisasGatewayClient` |
| W7 | fan-out filter | only `del_yn='N'`, `busi_stts='N'`, `prdt_type IN (F,M,H)` tenants written |
| W8 | scheduler gate off (default) | no `@Scheduled` fires; on-demand still works |

## 4. Frontend — MISSING

No `*.test.*` exists for `ExchangeRateInquiryPage` or the overseas screens
(gap-analysis G-OVRS-4). When added, cover:

| # | Scenario | Expected |
|---|---|---|
| FE1 | initial load | fixed 12-currency grid, `-` for missing rates |
| FE2 | bank dropdown select | re-fetch for new `bank_cd` |
| FE3 | round dropdown (Shinhan) | "Last time" + "1st time" (sends correct seq_no, not duplicated) |
| FE4 | round dropdown (Woori) | HH:MM:SS [Nth] reversed list |
| FE5 | date pick via `<DateField>` | re-fetch with `ref_date` |
| FE6 | compare toggle on | second fetch → 13-col matrix, Diff cells signed |
| FE7 | currency search | client-side filter by code |
| FE8 | column sort cycle | default → ASC → DESC → default; nulls last |
| FE9 | API error | `useDialog` error dialog; no silent mock fallback |
| FE10 | empty result | empty-state copy (and `common.loading` during initial sticky load) |
