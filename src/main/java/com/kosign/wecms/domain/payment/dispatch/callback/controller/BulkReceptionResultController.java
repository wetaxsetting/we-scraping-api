package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.common.response.ApiResponse;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkReceptionResultService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound 01301 bulk-reception-result callback (EFS → WeCMS) — refactor of
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp}. 1:1 with the legacy action for
 * traceability. Mirrors the inbound-controller layering of
 * {@code banking/nxisas/controller/BankScrapingController}.
 *
 * <p><b>SKELETON:</b> binds the encrypted {@link EfsCallbackEnvelope} and delegates
 * to {@link BulkReceptionResultService} (which is itself a skeleton). No business
 * processing happens yet.
 *
 * <p><b>TODO(B7):</b> this endpoint is called by the external EFS gateway, so it
 * must be added to the security allowlist and IP-whitelisted ({@code x-coocon-accessIp})
 * — callback exposure topology is the open B7/J6 item. Until then it is covered by
 * the default {@code authenticated()} filter.
 */
@RestController
@RequestMapping("/api/v1/payment/dispatch/callbacks")
@RequiredArgsConstructor
public class BulkReceptionResultController {

    private final BulkReceptionResultService bulkReceptionResultService;

    @PostMapping("/bulk-reception-result")
    public ApiResponse<Void> receptionResult(@Valid @RequestBody EfsCallbackEnvelope envelope) {
        bulkReceptionResultService.handle(envelope);
        return ApiResponse.ok("received", null);
    }
}
