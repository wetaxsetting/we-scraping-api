package com.kosign.wecms.domain.banking.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.banking.dto.OwnAccountListResponse;
import com.kosign.wecms.domain.banking.dto.TransferHistoryResponse;
import com.kosign.wecms.domain.banking.service.AccountService;
import com.kosign.wecms.domain.banking.service.TransferHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Banking → Transfers. Sprint 1 ships read-only:
 *   - {@code /transfers/own-accounts} — STUB account list (no execution).
 *   - {@code /transfers/history}      — real read of NAPAS_REMIT_TRNS_HSTR.
 *
 * Transfer execution (POST /transfers/own) is intentionally NOT
 * implemented — see §12 of task-sprint-1-banking-addon-backend-plan.
 */
@RestController
@RequestMapping("/api/v1/banking/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final AccountService accountService;
    private final TransferHistoryService historyService;

    @GetMapping("/own-accounts")
    public ApiResponse<OwnAccountListResponse> ownAccounts() {
        return ApiResponse.ok(accountService.ownAccounts());
    }

    @GetMapping("/history")
    public ApiResponse<TransferHistoryResponse> history(
            @RequestParam(value = "from",   required = false) String fromDt,
            @RequestParam(value = "to",     required = false) String toDt,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.ok(historyService.list(fromDt, toDt, status));
    }
}
