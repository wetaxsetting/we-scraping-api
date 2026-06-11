# Transfer History — Business Flow (Backend)

> Feature: Banking › Internal Transfer › Transfer History (`transfer-history`)
> Legacy: `trns_0003_001_r001`

## 1. Where the data comes from

Transfer History does **not** create transfer records — it only reads them. Rows in `NAPAS_REMIT_TRNS_HSTR` are written upstream by the **Internal Transfer / Own Account Transfer register + execute** flows (legacy `trns_0002_001`, refactor `own-account-transfer`). The NAPAS / Coocon firm-banking engine returns a result code (`TRNS_STTS`) and message (`TRNS_MSG`) for each remittance attempt, which are persisted on the history row.

```
Bank / NAPAS engine transfer attempt
        │  (engine returns TRNS_STTS + TRNS_MSG)
        ▼
Row persisted into NAPAS_REMIT_TRNS_HSTR   ← written by Own Account / Internal Transfer execute flow
        │
        ▼
Transfer History list query  (this feature)
        │  date-range + status filter, tenant-scoped, paged, sorted
        ▼
History grid + per-currency totals  → frontend
```

The matching / vendor-expense reconciliation flow shown in the original prompt is a **separate screen** over different tables — it does not participate in this flow. See [refactor-gap-analysis.md](refactor-gap-analysis.md) §"Scope boundary".

## 2. Request → response flow (`TransferHistoryQueryService.list`)

`TransferHistoryQueryService.list(req)` is `@Transactional(readOnly = true)`:

1. **Resolve tenant** — `bizKey = TenantContext.currentBizKey()` (from the signed JWT). Never from the wire.
2. **Period guard** — if `to_dt < from_dt` (string compare) → `throw BusinessException("WBE_TRANSFER_HISTORY_INVALID_PERIOD")`. (Legacy `validatePeriod` only checks `end ≥ start`; there is **no max-range cap** on either side — see open questions.)
3. **Map status filter** to a pair of mutually-exclusive predicates:
   | Wire `trns_stts` | `statusEq` | `statusNeq` | Meaning |
   |---|---|---|---|
   | `"00000000"` | `"00000000"` | `null` | Success only |
   | `"99999999"` | `null` | `"00000000"` | Failed only (= NOT success, null-safe) |
   | `""` / null | `null` | `null` | All |
4. **Build Specification** — `bizKey` equality + `substring(regDtm,1,8) BETWEEN from_dt AND to_dt` + the status branch. The failed branch is null-safe: it matches rows where `trns_stts IS NULL` OR `trns_stts <> '00000000'`.
5. **Page + sort** — page size from `page_per_cnt` (default 15); 1-based `page_no` → 0-based index; sort via the whitelist (default `regDtm DESC, seqNo DESC`).
6. **Query** — `repo.findAll(spec, pageable)`.
7. **Resolve display fields** per row (with per-request caches):
   - `out_bank_nm` ← `BankCdResolver.resolveBankNm(out_bank_code)`
   - `in_bank_nm` ← `LegacyFunctionRepository.resolveNapasOrCitadBankName(IN_NAPAS_BANK_CODE, lang)` — keyed on the **NAPAS** code, not `IN_BANK_CODE`
   - `user_nm` ← batched `UserInfmRepository.findAllById(distinct REG_IDs)`
   - `trns_dt` / `trns_tm` ← UTC `REG_DTM` normalized to `Asia/Ho_Chi_Minh`, then split (date = chars 0–8, time = chars 8–12 → HHmm)
8. **Aggregate totals** — `repo.aggregateTotals(...)` returns VND + USD amount/count.
9. **Assemble** `TransferHistoryListResponse(totalCount, totalVndAmount, totalUsdAmount, totalVndCount, totalUsdCount, rows)`.

## 3. Status resolution logic

Status in the UI is **binary**, driven entirely by `TRNS_STTS`:

- `TRNS_STTS = '00000000'` → **Success**
- any other value (`LOGIN_CAN`, `8000FFFF`, `8000FFFF`, etc.) → **Failed**

Real DEV data shows many distinct engine codes (operator-cancelled, FI errors, timeouts) that all collapse to "Failed". The raw `TRNS_STTS` and `TRNS_MSG` are passed through verbatim on each row; the frontend shows `TRNS_MSG` as a hover tooltip on failed rows. There is **no** intermediate "pending/processing/cancelled" state in the data model — those statuses from the original prompt do not exist for this feature (see [refactor-gap-analysis.md](refactor-gap-analysis.md) §"Transfer status mapping").

## 4. Date / time handling

| Concern | Behaviour |
|---|---|
| Storage | `REG_DTM` is `VARCHAR(14)` UTC `yyyyMMddHHmmss` (no timestamp type) |
| Filter | `substring(REG_DTM,1,8)` compared against `from_dt`/`to_dt` (`YYYYMMDD`) — **raw UTC**, legacy parity |
| Display | normalized to `Asia/Ho_Chi_Minh` (UTC+7) before splitting into `trns_dt` + `trns_tm` |
| Edge case | a transfer at `20260515200000` UTC displays as `trns_dt=20260516, trns_tm=0300` (rolls past midnight) — covered by a unit test |

> ⚠️ **Known asymmetry (legacy parity, carried forward):** the date *filter* uses raw UTC while the *display* uses local time. A transfer near a day boundary can therefore appear under a display date outside the filtered range. This matches legacy `trns_0003_001` behaviour and was retained deliberately.

## 5. Totals / footer

`aggregateTotals` JPQL computes, over the full filtered set (not the page):

- `total_count` — COUNT(rows)
- `total_vnd_amount` / `total_vnd_count` — SUM/COUNT where `CCY_CD='VND'`
- `total_usd_amount` / `total_usd_count` — SUM/COUNT where `CCY_CD='USD'`

The legacy IDO (`NAPAS_REMIT_TRNS_HSTR_R003_CNT`) computes both currencies too, but the legacy footer only renders VND (a known legacy gap, R2). The refactor preserves both on the wire so a future second footer row can render USD without a backend change.

## 6. Tenant & locale

- **Tenant:** every query is constrained on `BIZ_KEY = TenantContext.currentBizKey()`. The export must never cross tenants (no export exists here anyway).
- **Locale:** `currentLangCd()` is collapsed for the legacy bank-name function — `VI → "vi"`, `EN/KO/KM → "en"`. The function has no `ko`/`km` branch, so those fall back to English.
