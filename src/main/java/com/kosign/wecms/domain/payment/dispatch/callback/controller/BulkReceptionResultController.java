package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkReceptionResultService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>Returns the EFS acknowledgement envelope (A2 / audit C1): {@code ID/RQ_DTIME/TNO/EV/VV/EM/VM/RC/RM}
 * — NOT the internal {@code ApiResponse} shape. On reception success 01301 rebuilds {@code EV}/{@code VV}
 * over the six echo fields with {@code RC=0000} (legacy {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:409-423}).
 *
 * <p><b>Including on the error path.</b> An exception escaping the service used to reach
 * {@code GlobalExceptionHandler}, which answers the gateway with HTTP 500 and a {@code WBE9999}
 * {@code ApiResponse} body — a shape the gateway cannot parse, leaving the callback outcome undefined
 * from the bank's side. The handler here mirrors legacy's action-wide {@code try/catch}
 * ({@code FILE_DEPOSIT_TRANSFER_API1_act.jsp:425-441}): echo the request envelope, set {@code RC}/{@code RM}
 * only. See {@link CallbackAck} for the exception → {@code RC} mapping.
 *
 * <p><b>TODO(B7):</b> this endpoint is called by the external EFS gateway, so it
 * must be added to the security allowlist and IP-whitelisted ({@code x-coocon-accessIp})
 * — callback exposure topology is the open B7/J6 item. Until then it is covered by
 * the default {@code authenticated()} filter.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/dispatch/callbacks")
@RequiredArgsConstructor
public class BulkReceptionResultController {

    private final BulkReceptionResultService bulkReceptionResultService;

    @PostMapping("/bulk-reception-result")
    public EfsEnvelope receptionResult(@Valid @RequestBody EfsCallbackEnvelope envelope) {
        try {
            return bulkReceptionResultService.handle(envelope);
        } catch (RuntimeException e) {
            // Logged at error with the full stack — the envelope carries the RC, the log carries the
            // cause. The service's transaction has already rolled back, so the file stays in its prior
            // state and the callback is replayable, which is what the RC tells the gateway.
            // An Error is deliberately allowed to propagate untouched.
            log.error("01301 callback failed vanOrgCd={} tno={} — returning EFS error envelope",
                    envelope == null ? null : envelope.id(),
                    envelope == null ? null : envelope.tno(), e);
            return CallbackAck.error(envelope, e);
        }
    }
}
