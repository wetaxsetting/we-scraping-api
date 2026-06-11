package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.OwnAccountListResponse;
import com.kosign.wecms.domain.banking.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Banking → Transfers. Sprint 1 ships read-only:
 *   - {@code /transfers/own-accounts} — STUB account list (no execution).
 *
 * The legacy {@code /transfers/history} endpoint (UPPER_SNAKE wire) was
 * <b>removed 2026-06-07</b> — it was superseded by the spec-aligned, lowercase
 * snake_case {@code GET /api/v1/banking/internal-transfer/transfer-history}
 * in {@code domain.banking.transferhistory.controller.TransferHistoryController},
 * which the frontend already consumes. The deprecated DTOs
 * ({@code TransferRow} / {@code TransferHistoryResponse}) and
 * {@code TransferHistoryService} were deleted in the same change.
 *
 * Transfer execution (POST /transfers/own) is intentionally NOT
 * implemented — see §12 of task-sprint-1-banking-addon-backend-plan.
 */
@RestController
@RequestMapping("/api/v1/banking/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final AccountService accountService;

    @GetMapping("/own-accounts")
    @PreAuthorize("@menuAuthz.canAccess('banking-internal-transfer-own')")
    public ApiResponse<OwnAccountListResponse> ownAccounts() {
        return ApiResponse.ok(accountService.ownAccounts());
    }
}
