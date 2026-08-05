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
 * claim had no subject. Full analysis:
 * {@code docs/audits/legacy-batch-boundary-rule-sweep-2026-07-31.md} §D-A.
 *
 * <p><b>The remedy applied for it has since been withdrawn too (2026-08-05).</b> D-A was closed by
 * raising {@link #PER_DELAY_DEFAULT} 100 → 3000 ms, on the premise that legacy had no considered value
 * for a third bank. Handover {@code 05_배치스케줄_운영가이드.md §5.3} and
 * {@code 06_환경설정_항목목록.md §6.3} both document 100 ms as the 기본 딜레이, and 05 §5.3 names BIDV /
 * Vietcombank / Kookmin as banks that process normally on it — so the premise was false, and handover
 * {@code 08_기능추출_이관가이드.md §8.4} attributes the 3-second floor specifically to <b>Woori's</b>
 * rate limit. The default is back to legacy's 100 ms. <b>The D-A finding itself still stands</b>, fixed
 * by the per-row keying above — the structural half, and the half that was always doing the work.
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
 * 100&nbsp;ms</b> — legacy's {@code WCConst.PER_DELAY_DEFAULT}, documented as the 기본 딜레이 in handover
 * 05 §5.3 and 06 §6.3. See {@link #PER_DELAY_DEFAULT}.
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
     * Pacing for any bank that is <b>not</b> Shinhan or Woori — <b>100 ms, legacy's
     * {@code WCConst.PER_DELAY_DEFAULT}</b> (restored 2026-08-05).
     *
     * <p><b>100 ms is a documented value, not an unconsidered initialiser.</b> Handover
     * {@code 05_배치스케줄_운영가이드.md §5.3} ("은행/통화/딜레이") states it twice — <i>그 외 은행:
     * 기본 딜레이 100ms 적용</i> and <i>기본 딜레이: 100ms</i> — and {@code 06_환경설정_항목목록.md §6.3}
     * lists {@code WCConst.PER_DELAY_*} as {@code 100/3000ms}. The same 05 §5.3 note warns that Shinhan
     * and Woori are hard-coded <b>because they are delay overrides and specific network-validation rules
     * (EBANK→Shinhan, VCB망→Woori)</b>, not because they are a bank whitelist —
     * <i>실제 처리 은행은 데이터(`OUT_BANK_CD`)로 결정되며 BIDV·국민 등도 정상 처리(기본 딜레이)</i>,
     * naming <b>BIDV and Kookmin</b> as banks that do process normally on the 기본 딜레이. So third banks
     * were expected on this rail and 100 ms is the value documented for them.
     *
     * <p><b>Woori's 3000 ms is specific to Woori.</b> Handover
     * {@code 08_기능추출_이관가이드.md §8.4} (함정 포인트) reads <i>은행별 딜레이: 우리은행 3초.
     * 미적용 시 은행 측 유량제한(rate limit) 위반 가능</i> — the 3-second floor exists to avoid violating
     * <b>that</b> bank's rate limit. It is not a general-purpose conservative default, and generalising
     * it is not a safety improvement: at 3000 ms a 100-row BIDV batch paces ~5 minutes of gateway
     * traffic where legacy paced ~10 seconds. That is a throughput regression against a documented
     * value, justified by no bank's published limit.
     *
     * <p>An earlier revision of this constant was 3000 ms (2026-08-01), on the reasoning that legacy
     * "had no considered value" for banks outside {@code {970424, 970457}}. Handover 05/06 contradict
     * that premise directly, so the divergence is withdrawn and legacy parity restored.
     *
     * <p>Raise it for a specific bank only by adding that bank's own constant, the way legacy does for
     * Woori — never by moving the default.
     */
    static final long PER_DELAY_DEFAULT = 100L;
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
