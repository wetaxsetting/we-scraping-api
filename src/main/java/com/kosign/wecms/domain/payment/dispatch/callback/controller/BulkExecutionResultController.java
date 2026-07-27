package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkExecutionResultService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
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
 * <p>Returns the EFS acknowledgement envelope (A2 / audit C1): {@code ID/RQ_DTIME/TNO/EV/VV/EM/VM/RC/RM}
 * — NOT the internal {@code ApiResponse} shape. 01302 echoes the request {@code EV/VV} verbatim and
 * sets only {@code RC/RM} (legacy {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} {@code copyDomain} + {@code setResult}).
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
    public EfsEnvelope executionResult(@Valid @RequestBody EfsCallbackEnvelope envelope) {
        return bulkExecutionResultService.handle(envelope);
    }
}
