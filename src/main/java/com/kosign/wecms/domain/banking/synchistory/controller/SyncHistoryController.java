package com.kosign.wecms.domain.banking.synchistory.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.synchistory.dto.SyncHistoryListResponse;
import com.kosign.wecms.domain.banking.synchistory.service.SyncHistoryService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bank Transaction Sync &gt; History popup endpoint. Mirrors legacy
 * {@code fnsh_0001_003.act} which reads {@code EXT_TRAN_CALL_HIS}
 * filtered to {@code SVC_ID = 'REAL_TIME_ONLINE_INQUIRY'} and the
 * tenant+user pair.
 *
 * <p>Query params:
 * <ul>
 *   <li>{@code from_dt} / {@code to_dt} — required,
 *       {@code yyyyMMdd}. Filter on {@code REF_DATE}.</li>
 *   <li>{@code currency} — optional, accepts {@code "VND"},
 *       {@code "FCY"} (= NOT VND), a concrete code, or
 *       {@code "ALL"} / blank (no currency predicate).</li>
 *   <li>{@code bank_cd} — optional bank filter; legacy 20-char
 *       BANK_CD value. {@code null} / blank = All Banks.</li>
 *   <li>{@code result} — optional, {@code "success"} /
 *       {@code "failed"} / blank (All).</li>
 * </ul>
 *
 * <p>Pagination intentionally absent — the legacy popup loads the
 * full window. Volumes are bounded by the date chips (default 7
 * days). Add cursor pagination when a real user complains.
 */
@RestController
@RequestMapping("/api/v1/banking/sync-history")
@RequiredArgsConstructor
@Validated
public class SyncHistoryController {

    private final SyncHistoryService syncHistoryService;

    @GetMapping
    public ApiResponse<SyncHistoryListResponse> list(
            @RequestParam("from_dt")
            @Pattern(regexp = "^\\d{8}$", message = "from_dt: must be yyyyMMdd")
            String fromDt,
            @RequestParam("to_dt")
            @Pattern(regexp = "^\\d{8}$", message = "to_dt: must be yyyyMMdd")
            String toDt,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "bank_cd", required = false) String bankCd,
            @RequestParam(value = "result", required = false) String result) {
        return ApiResponse.ok(
                syncHistoryService.list(fromDt, toDt, currency, bankCd, result));
    }
}
