package com.kosign.wecms.domain.payment.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Per-bank request pacing for the scheduled processing-result-check scan (B5 / audit M-5)
 * — refactor of the legacy {@code PROCESSING_RESULT_CHECK_BATCH} per-request
 * {@code Thread.sleep(per_request_delay)} keyed on the outbound bank code
 * ({@code WCConst.PER_DELAY_*}).
 *
 * <p><b>Scope (TO-BE aware).</b> Pacing applies only to the one remaining scan loop —
 * {@link ResultCheckService#runOnce()} iterating aged 202 rows. The event-driven legs
 * (real-time 202/204 dispatch, bulk 00301 reception, the 01301/01302 callbacks) fire one
 * request per business event, never in a back-to-back scan, so they need no pacing (the
 * legacy inter-request delay existed only to throttle the polling batches, which the
 * approved TO-BE removed — see {@code project_batch_event_driven_tobe_recovery}).
 *
 * <p>Delays are the verified legacy values: Woori {@code 970457} = 3000&nbsp;ms, Shinhan
 * {@code 970424} / everything else = 100&nbsp;ms. Extracted into a component so the scan
 * loop stays clean and unit tests can substitute a no-op (avoiding real sleeps).
 */
@Slf4j
@Component
public class DispatchPacer {

    /** Legacy {@code WCConst.BANK_CD_SHINHAN}. */
    static final String BANK_CD_SHINHAN = "970424";
    /** Legacy {@code WCConst.BANK_CD_WOORI}. */
    static final String BANK_CD_WOORI = "970457";

    /** Legacy {@code WCConst.PER_DELAY_DEFAULT} (ms). */
    static final long PER_DELAY_DEFAULT = 100L;
    /** Legacy {@code WCConst.PER_DELAY_SHINHAN} (ms). */
    static final long PER_DELAY_SHINHAN = 100L;
    /** Legacy {@code WCConst.PER_DELAY_WOORI} (ms). */
    static final long PER_DELAY_WOORI = 3000L;

    /**
     * Sleep the legacy per-bank delay for {@code outBankCd}. Interruption is swallowed
     * (the interrupt flag is restored) so pacing can never abort the scan — a paced but
     * interrupted scan simply proceeds to the next row (legacy parity: the batch never
     * failed on a sleep).
     */
    public void pause(String outBankCd) {
        long delay = perBankDelay(outBankCd);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("dispatch pacing interrupted (outBankCd={}) — continuing", outBankCd);
        }
    }

    /** Legacy per-bank delay lookup ({@code PROCESSING_RESULT_CHECK_BATCH:83-89}). */
    long perBankDelay(String outBankCd) {
        if (BANK_CD_WOORI.equals(outBankCd)) {
            return PER_DELAY_WOORI;
        }
        if (BANK_CD_SHINHAN.equals(outBankCd)) {
            return PER_DELAY_SHINHAN;
        }
        return PER_DELAY_DEFAULT;
    }
}
