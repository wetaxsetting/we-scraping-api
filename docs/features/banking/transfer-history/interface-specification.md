# Transfer History — Interface Specification (Backend)

> Controller: `TransferHistoryController`
> Base path: `/api/v1/banking/internal-transfer/transfer-history`

## 1. Controller

```java
@RestController
@RequestMapping("/api/v1/banking/internal-transfer/transfer-history")
@RequiredArgsConstructor
@Validated
public class TransferHistoryController {

    private final TransferHistoryQueryService service;

    @GetMapping
    public ApiResponse<TransferHistoryListResponse> list(
        @RequestParam("from_dt")        @NotBlank @Pattern("\\d{8}") String fromDt,
        @RequestParam("to_dt")          @NotBlank @Pattern("\\d{8}") String toDt,
        @RequestParam(name="trns_stts", required=false) @Pattern("|00000000|99999999") String trnsStts,
        @RequestParam(name="page_no", required=false)   @Min(1) Integer pageNo,
        @RequestParam(name="page_per_cnt", required=false) @Pattern("10|15|20|50|100") String pagePerCnt,
        @RequestParam(name="sort_field", required=false) String sortField,
        @RequestParam(name="sort_direction", required=false) String sortDirection) { ... }
}
```

Single `@GetMapping` (no path suffix). Returns `ApiResponse.ok(service.list(req))`.

## 2. Request

```
GET /api/v1/banking/internal-transfer/transfer-history
      ?from_dt=20260501
      &to_dt=20260531
      &trns_stts=00000000        (optional: omit/""=all, 00000000=success, 99999999=failed)
      &page_no=1                  (optional, 1-based, default 1)
      &page_per_cnt=15            (optional, one of 10|15|20|50|100, default 15)
      &sort_field=TRNS_DT         (optional: TRNS_DT|OUT_BANK_NM|IN_BANK_NM|TRSC_AMT|RMRK)
      &sort_direction=DESC        (optional: ASC|DESC)
Authorization: Bearer <jwt>
```

## 3. Response DTOs

### `TransferHistoryListResponse`

| Java field | Wire | Type |
|---|---|---|
| `totalCount` | `total_count` | long |
| `totalVndAmount` | `total_vnd_amount` | BigDecimal |
| `totalUsdAmount` | `total_usd_amount` | BigDecimal |
| `totalVndCount` | `total_vnd_count` | long |
| `totalUsdCount` | `total_usd_count` | long |
| `rows` | `rows` | `List<TransferHistoryRow>` |

### `TransferHistoryRow` (18 fields)

| Java field | Wire | Type | Notes |
|---|---|---|---|
| `seqNo` | `seq_no` | Long | per-tenant seq |
| `trnsDt` | `trns_dt` | String | `YYYYMMDD` (display zone) |
| `trnsTm` | `trns_tm` | String | `HHmm` (display zone) |
| `outBankCode` | `out_bank_code` | String | |
| `outBankNm` | `out_bank_nm` | String | resolved |
| `outAcctNo` | `out_acct_no` | String | |
| `inBankCode` | `in_bank_code` | String | carries `IN_NAPAS_BANK_CODE` |
| `inBankNm` | `in_bank_nm` | String | resolved via NAPAS code |
| `inAcctNo` | `in_acct_no` | String | |
| `owanNm` | `owan_nm` | String | recipient holder |
| `ccyCd` | `ccy_cd` | String | `VND`/`USD` (uppercase value) |
| `trscAmt` | `trsc_amt` | BigDecimal | |
| `fee` | `fee` | BigDecimal | |
| `transType` | `trans_type` | String | nullable |
| `rmrk` | `rmrk` | String | |
| `trnsStts` | `trns_stts` | String | `00000000`=success |
| `trnsMsg` | `trns_msg` | String | null on success |
| `userNm` | `user_nm` | String | from `USER_INFM` |

## 4. JSON examples

### Success

```json
{
  "status": { "error": false, "code": "WBI0000", "message": "Success" },
  "data": {
    "total_count": 1,
    "total_vnd_amount": 50000.0,
    "total_usd_amount": 0,
    "total_vnd_count": 1,
    "total_usd_count": 0,
    "rows": [
      {
        "seq_no": 12,
        "trns_dt": "20260515",
        "trns_tm": "1730",
        "out_bank_code": "11704004",
        "out_bank_nm": "BIDV",
        "out_acct_no": "0441000688209",
        "in_bank_code": "970424",
        "in_bank_nm": "Shinhan Bank",
        "in_acct_no": "700023109656",
        "owan_nm": "NGUYEN NGOC VAN HA",
        "ccy_cd": "VND",
        "trsc_amt": 50000.0,
        "fee": 0,
        "trans_type": "1",
        "rmrk": "salary",
        "trns_stts": "00000000",
        "trns_msg": null,
        "user_nm": "Sonny"
      }
    ]
  }
}
```

### Validation error (missing `from_dt`)

```json
{
  "status": { "error": true, "code": "WBE_VALIDATION", "message": "..." },
  "timestamp": "2026-05-20T03:11:22Z",
  "path": "/api/v1/banking/internal-transfer/transfer-history",
  "trace_id": "20260520031122PT0123456789012DEVL"
}
```

### Business error (inverted period)

```json
{
  "status": { "error": true, "code": "WBE_TRANSFER_HISTORY_INVALID_PERIOD",
              "message": "to_dt must be greater than or equal to from_dt." },
  "timestamp": "...", "path": "...", "trace_id": "..."
}
```

## 5. Deprecated interim endpoint (Sprint 1 holdover) — REMOVED 2026-06-07

`GET /api/v1/banking/transfers/history` and its UPPER_SNAKE DTOs were **deleted 2026-06-07**. Removed: `TransferController.history()`, `TransferHistoryService.java`, `TransferHistoryResponse.java`, `TransferRow.java`. The frontend was always pointed at the new lowercase `/internal-transfer/transfer-history` path (`fetchTransferHistory()`), so there was no consumer to break. `NapasRemitTrnsHstrRepository` / `NapasRemitTrnsHstr` were kept (shared with `OwnAccountTransferService`). The CLAUDE.md "Current API calls" table was corrected to the new path in the same change.

## 6. Out of scope (no endpoint exists)

There is **no** detail, match, unmatch, candidate, or export endpoint on this controller. Match endpoints live on `AccountController` (`/api/v1/banking/accounts/{acctSeqNo}/transactions/{trnsSeqNo}/match[-candidates]`) for the Transaction-History matching feature — a different feature. See [refactor-gap-analysis.md](refactor-gap-analysis.md) §"Scope boundary".
