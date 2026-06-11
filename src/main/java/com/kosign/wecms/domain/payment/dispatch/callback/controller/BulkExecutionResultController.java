package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkExecutionResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 01302 bulk-execution-result callback (EFS → WeCMS) — refactor of
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp}. 1:1 with the legacy action.
 *
 * <p><b>SKELETON:</b> binds the encrypted {@link EfsCallbackEnvelope} and delegates
 * to {@link BulkExecutionResultService} (skeleton). No business processing yet.
 *
 * <p><b>TODO(B7):</b> external-facing callback — security allowlist + IP whitelist
 * pending (callback exposure topology, B7/J6).
 */
@RestController
@RequestMapping("/api/v1/payment/dispatch/callbacks")
@RequiredArgsConstructor
public class BulkExecutionResultController {

    private final BulkExecutionResultService bulkExecutionResultService;

    @PostMapping("/bulk-execution-result")
    public ApiResponse<Void> executionResult(@Valid @RequestBody EfsCallbackEnvelope envelope) {
        bulkExecutionResultService.handle(envelope);
        return ApiResponse.ok("received", null);
    }
}
