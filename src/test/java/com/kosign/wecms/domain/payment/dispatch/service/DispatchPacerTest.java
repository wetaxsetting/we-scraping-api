package com.kosign.wecms.domain.payment.dispatch.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the three {@link DispatchPacer} delays.
 *
 * <h2>Why a test exists for three constants</h2>
 * These values are a <b>rate limit toward a bank</b>. Until 2026-08-01 nothing asserted them in either
 * direction — {@code missing-tests.md} §339 and {@code test-coverage-audit.md} §272 both flagged that
 * the rule "is never executed, so a wrong constant would not be caught". A silent edit here changes how
 * fast we hammer a payment gateway, which is not a class of change that should rest on code review
 * alone. The test is cheap; the failure mode is a bank throttling or rejecting us in production.
 *
 * <p>{@link DispatchPacer#pause} is deliberately <b>not</b> exercised — it really sleeps, and a test
 * that sleeps 3 s per case is a test people delete. {@link DispatchPacer#perBankDelay} is the whole
 * decision; {@code pause} only sleeps for what it returns.
 *
 * @see DispatchPacer#PER_DELAY_DEFAULT for the full reasoning behind the non-legacy default
 */
class DispatchPacerTest {

    private final DispatchPacer pacer = new DispatchPacer();

    /* ── the two verified legacy values ────────────────────────────────────── */

    @Test
    @DisplayName("Woori 970457 paces at 3000 ms — verified legacy WCConst.PER_DELAY_WOORI")
    void wooriPacesAt3000() {
        assertThat(pacer.perBankDelay("970457")).isEqualTo(3000L);
    }

    @Test
    @DisplayName("Shinhan 970424 paces at 100 ms — verified legacy WCConst.PER_DELAY_SHINHAN")
    void shinhanPacesAt100() {
        assertThat(pacer.perBankDelay("970424")).isEqualTo(100L);
    }

    /* ── the deliberate divergence ─────────────────────────────────────────── */

    /**
     * The default is <b>3000 ms, not legacy's 100</b> (D-A, 2026-08-01).
     *
     * <p>Legacy's 100 is the initialiser of a variable {@code NAPAS_REAL_TIME_BATCH:68} declares outside
     * its row loop and then overwrites for the only two banks it knew about. No third bank has ever been
     * on the paced rail — JEX DEV {@code TB_FIRM_TRAN TRAN_TP='202'} is {@code 970424} × 46 +
     * {@code 970457} × 6 and nothing else, and the one other bank in {@code TB_PAY_TARGET} (VCB
     * {@code 970436}) is {@code PROC_STAT='3'} FAIL on all four rows and is now file-routed. So 100 ms
     * has never actually been sent to a third bank; it is unexercised, not measured.
     *
     * <p>The first bank to take this value will therefore be one whose throughput tolerance is unknown,
     * against a rate limit documented nowhere. 3000 ms is the slowest rate legacy is known to sustain
     * against a real bank, which makes it the conservative floor.
     *
     * <p><b>If this test fails because someone lowered the default, that is the conversation the test
     * exists to force</b> — it needs a measured throughput answer from the VAN Center for a specific
     * bank, not a parity argument. Legacy's spacing was never a guarantee to restore: it is
     * order-dependent on {@code TB_PAY_TARGET_R001}, which has no {@code ORDER BY} (B-1).
     */
    @ParameterizedTest(name = "unknown bank [{0}] paces at the 3000 ms default, NOT legacy''s 100")
    @NullSource
    @ValueSource(strings = {
            "970436",   // VCB — the only third bank ever seen, and only as failed pay-targets
            "970415",   // Vietinbank — plausible next arrival
            "",         // blank OUT_BANK_CD
            "  ",       // whitespace
            "970424 ",  // Shinhan with trailing space: NOT an exact match, must fall through to default
            "unknown",
    })
    void everyOtherBankPacesAtTheConservativeDefault(String outBankCd) {
        assertThat(pacer.perBankDelay(outBankCd))
                .as("an unmeasured bank must pace at the slowest known-safe rate, not the fastest")
                .isEqualTo(3000L);
    }

    /**
     * Guards the shape of the decision, not just its outputs: the delay must be derived from the
     * argument on every call and hold no state between them. This is D-A itself — legacy's bug is a
     * hoisted variable that leaks across rows, and the refactor's fix is only a fix while this holds.
     * Interleaving matters because the refactor shares one pacer across the 203 scan and the
     * long-lived 202 FIFO worker, which has no pass boundary to reset at.
     */
    @Test
    @DisplayName("D-A: the delay is per-call and stateless — a Woori call never leaks into the next")
    void perBankDelayIsStateless() {
        assertThat(pacer.perBankDelay("970457")).isEqualTo(3000L);
        assertThat(pacer.perBankDelay("970424"))
                .as("Shinhan after Woori must still be 100 ms — legacy leaks 3000 here")
                .isEqualTo(100L);
        assertThat(pacer.perBankDelay("970457")).isEqualTo(3000L);
        assertThat(pacer.perBankDelay("970424")).isEqualTo(100L);
    }
}
