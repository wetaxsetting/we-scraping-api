package com.kosign.wecms.domain.payment.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.dto.OverseasScheduledListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.WithdrawalAccountRow;
import com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferService;
import com.kosign.wecms.domain.payment.service.OverseasPaymentService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for overseas payment.
 * Base path: {@code /api/v1/payment/overseas-payment}.
 *
 * <p>Mirrors the legacy {@code pymn_0007_001} JEX service.
 */
@RestController
@RequestMapping("/api/v1/payment/overseas-payment")
@RequiredArgsConstructor
@Validated
public class OverseasPaymentController {

    private final OverseasPaymentService    overseasPaymentService;
    private final InternalTransferService   internalTransferService;

    /**
     * Overseas scheduled-payment list.
     * Mirrors legacy {@code pymn_0007_001_r001}.
     *
     * <p>Wire parameters (all query-string, snake_case):
     * <ul>
     *   <li>{@code from_dt} — required, yyyyMMdd</li>
     *   <li>{@code to_dt}   — required, yyyyMMdd</li>
     *   <li>{@code appr_type_cd} — repeatable; filters by approval status
     *       (e.g. {@code APPROVED}, {@code PENDING}). Omit for all statuses.</li>
     *   <li>{@code keyword} — partial match on file title (type E) or beneficiary
     *       name (type M). Case-insensitive. Omit to skip.</li>
     *   <li>{@code page} — zero-based page index (default 0).</li>
     *   <li>{@code size} — rows per page; defaults to
     *       {@link OverseasPaymentService#DEFAULT_PAGE_SIZE},
     *       clamped to {@link OverseasPaymentService#MAX_PAGE_SIZE}.</li>
     * </ul>
     */
    @GetMapping("/scheduled")
    public ApiResponse<OverseasScheduledListResponse> listScheduled(
            @RequestParam("from_dt")
            @Pattern(regexp = "^[0-9]{8}$", message = "from_dt must be yyyyMMdd")
            String fromDt,

            @RequestParam("to_dt")
            @Pattern(regexp = "^[0-9]{8}$", message = "to_dt must be yyyyMMdd")
            String toDt,

            @RequestParam(value = "appr_type_cd", required = false)
            List<String> apprTypeCds,

            @RequestParam(value = "keyword", required = false)
            @Size(max = 100, message = "keyword must be ≤ 100 characters")
            String keyword,

            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "0") int size) {

        return ApiResponse.ok(
                overseasPaymentService.list(fromDt, toDt, apprTypeCds, keyword, page, size));
    }

    /**
     * Firm-banking-capable withdrawal accounts for the overseas payment
     * Excel upload dialog Step 3 ("Withdrawal Bank/Account No." picker).
     *
     * <p>Mirrors legacy {@code comm_1001_001} — same query as internal
     * transfer; the upstream service is shared because both features
     * draw from the same {@code ACCT_MSTR} + {@code TB_FIRM_CONF} join.
     *
     * @param ccyCd optional currency filter (e.g. {@code "USD"}); omit
     *              for all firm-banking accounts regardless of currency.
     */
    @GetMapping("/withdrawal-accounts")
    public ApiResponse<List<WithdrawalAccountRow>> listWithdrawalAccounts(
            @RequestParam(value = "ccy_cd", required = false) String ccyCd) {
        return ApiResponse.ok(internalTransferService.listWithdrawalAccounts(ccyCd));
    }
}
