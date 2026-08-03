package com.kosign.wecms.domain.payment.dispatch.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Per-bank request pacing for the scheduled processing-result-check scan (B5 / audit M-5)
 * — refactor of the legacy {@code PROCESSING_RESULT_CHECK_BATCH} per-request
 * {@code Thread.sleep(per_request_delay)} keyed on the outbound bank code
 * ({@code WCConst.PER_DELAY_*}).
 *
 * <p><b>Scope.</b> Pacing is applied at exactly <b>two</b> points:
 * <ul>
 *   <li>{@link ResultCheckService#runOnce()} — the aged-202 → 203 scan loop;</li>
 *   <li>{@link RealtimeDispatchQueue} — the single FIFO worker that now drains the whole real-time (202)
 *       rail, fed by both {@link RealtimeDispatchRegistrar} (request-thread hand-off) and
 *       {@link StrandedRecoveryService} (recovery sweep). C-6, 2026-07-31.</li>
 * </ul>
 * <b>Do not add a third.</b> Two threads each honouring a 100 ms / 3000 ms delay against the same gateway
 * is not a rate limit; the value of a single drain is that the delay between consecutive requests is the
 * delay, whichever producer queued them. The earlier arrangement — a {@code pause} inside
 * {@code StrandedRecoveryService.recoverPayTargets} alongside an unpaced request-thread loop — is exactly
 * the shape this note exists to prevent recurring.
 *
 * <p>The remaining bulk legs (00301 reception, the 01301/01302 callbacks) stay unpaced: the legacy
 * inter-request delay throttled the polling batches the approved TO-BE removed (see
 * {@code project_batch_event_driven_tobe_recovery}), and those legs issue one request per file rather than
 * one per payment line. Audit <b>M-6</b> tracked the real-time burst that C-6 has now closed.
 *
 * <p><b>The per-row lookup below is a deliberate divergence from legacy (D-A).</b> Legacy
 * {@code NAPAS_REAL_TIME_BATCH} hoists {@code per_request_delay} out of its row loop ({@code :68} vs the
 * {@code for} at {@code :82}) and only ever raises it inside, never resetting — so after one Woori row
 * every later row in the pass sleeps 3000 ms whatever its bank. {@link #perBankDelay} derives the delay
 * from the row it is given, which matches the sibling batch
 * ({@code PROCESSING_RESULT_CHECK_BATCH:82} declares the variable inside its loop) and is why the
 * hoisted one reads as a defect.
 *
 * <p><b>The "up to 30× faster than legacy" consequence this Javadoc used to claim is withdrawn
 * (2026-08-01).</b> The leak can only mis-pace a bank outside {@code {970424, 970457}} — both of those
 * set the delay explicitly on their own rows — and no such bank has ever been on the paced rail. The
 * claim had no subject. What replaces it is {@link #PER_DELAY_DEFAULT} = 3000 ms, which makes this
 * component {@code >=} legacy's spacing for every row without importing the leak. Full analysis:
 * {@code docs/audits/legacy-batch-boundary-rule-sweep-2026-07-31.md} §D-A.
 *
 * <p><b>That is a scope decision, not a claim that the event-driven legs never burst.</b> An earlier
 * revision of this Javadoc justified the exemption with "they fire one request per business event,
 * never in a back-to-back scan". That is false: {@link RealtimeDispatchRegistrar#registerAfterCommit}
 * receives the whole list of {@code TRAN_SEQ}s a payment just committed and loops them sequentially
 * ({@code RealtimeDispatchRegistrar:61-74}), so one approval of an N-line VND payment produces N
 * back-to-back 202 calls with no delay between them. {@code DispatchAutoReceiveRegistrar} (00301) and
 * the 01301 sibling fan-out have the same shape. The exemption stands only because pacing those legs
 * requires moving dispatch off the request thread — tracked as audit <b>M-6</b> (owner: VAN Center +
 * dev), not because the burst does not exist.
 *
 * <p>Delays for the two banks legacy actually configured are its verified values: Woori
 * {@code 970457} = 3000&nbsp;ms, Shinhan {@code 970424} = 100&nbsp;ms. <b>Every other bank paces at
 * 3000&nbsp;ms</b> — see {@link #PER_DELAY_DEFAULT}, which is deliberately NOT legacy's 100 ms.
 * Extracted into a component so the scan loop stays clean and unit tests can substitute a no-op
 * (avoiding real sleeps).
 */
@Slf4j
@Component
public class DispatchPacer {

    /** Legacy {@code WCConst.BANK_CD_SHINHAN}. */
    static final String BANK_CD_SHINHAN = "970424";
    /** Legacy {@code WCConst.BANK_CD_WOORI}. */
    static final String BANK_CD_WOORI = "970457";

    /**
     * Pacing for any bank that is <b>not</b> Shinhan or Woori — <b>3000 ms, deliberately not legacy's
     * {@code WCConst.PER_DELAY_DEFAULT = 100}</b> (raised 2026-08-01).
     *
     * <p><b>Why this diverges.</b> Legacy has no considered value here. 100 ms is the initialiser of a
     * variable that legacy's own loop then overwrites for the only two banks it knew about; no third
     * bank has ever reached the paced rail, so 100 ms has never actually been sent to one. Evidence
     * (JEX DEV, read-only 2026-08-01): {@code TB_FIRM_TRAN TRAN_TP='202'} carries {@code 970424} × 46
     * and {@code 970457} × 6 and nothing else, ever; the only third bank in {@code TB_PAY_TARGET},
     * VCB {@code 970436} (4 rows, 20260617), is {@code PROC_STAT='3'} FAIL on all four — it never
     * dispatched — and VCB has since been routed off the API rail to file-based entirely
     * (PO-approved 2026-07-16, memory {@code project_vcb_file_transfer_switch}).
     *
     * <p>So the first bank to take this value will be one whose throughput tolerance we have never
     * measured, against a rate limit documented nowhere. 3000 ms is the slowest rate legacy is known
     * to sustain against a real bank (Woori), which makes it the conservative floor: this constant is
     * now {@code >=} legacy's effective spacing for <b>every</b> row, deterministically, rather than
     * only when a Woori row happened to sort ahead in the same pass (see the D-A note below and B-1 —
     * {@code TB_PAY_TARGET_R001} has no {@code ORDER BY}, so legacy's spacing was never a guarantee).
     *
     * <p>Lower it only with a measured throughput answer from the VAN Center for the specific bank.
     */
    static final long PER_DELAY_DEFAULT = 3000L;
    /** Legacy {@code WCConst.PER_DELAY_SHINHAN} (ms) — verified legacy value, unchanged. */
    static final long PER_DELAY_SHINHAN = 100L;
    /** Legacy {@code WCConst.PER_DELAY_WOORI} (ms) — verified legacy value, unchanged. */
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
