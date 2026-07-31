package com.kosign.wecms.domain.payment.dispatch.callback.controller;

import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.callback.service.BulkExecutionResultService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p><b>Including on the error path</b>, which matters more here than on the 01301 twin: this handler
 * genuinely throws in normal operation ({@code WBE_EFS_RESULT_FILE} on a missing / empty / malformed
 * result file), and that throw is load-bearing — it rolls the parse back so the file stays at
 * {@code PROC_STEP='2'} and the callback is replayable. Without a catch here, the gateway received
 * HTTP 500 with a {@code WBE9999} {@code ApiResponse} body for that entirely expected outcome. The
 * handler mirrors legacy's action-wide {@code try/catch}; see {@link CallbackAck} for the
 * exception → {@code RC} mapping.
 *
 * <p><b>TODO(B7):</b> external-facing callback — security allowlist + IP whitelist
 * pending (callback exposure topology, B7/J6).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment/dispatch/callbacks")
@RequiredArgsConstructor
public class BulkExecutionResultController {

    private final BulkExecutionResultService bulkExecutionResultService;

    @PostMapping("/bulk-execution-result")
    public EfsEnvelope executionResult(@Valid @RequestBody EfsCallbackEnvelope envelope) {
        try {
            return bulkExecutionResultService.handle(envelope);
        } catch (RuntimeException e) {
            // Logged at error with the full stack — the envelope carries the RC, the log carries the
            // cause. The rollback is intentional (MST stays at PROC_STEP='2', replayable); the 01302
            // audit row survives it because it is written REQUIRES_NEW.
            // An Error is deliberately allowed to propagate untouched.
            log.error("01302 callback failed vanOrgCd={} tno={} — returning EFS error envelope",
                    envelope == null ? null : envelope.id(),
                    envelope == null ? null : envelope.tno(), e);
            return CallbackAck.error(envelope, e);
        }
    }
}
