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
 * @see DispatchPacer#PER_DELAY_DEFAULT for the handover citations behind the 100 ms default
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

    /* ── the documented default, for every other bank ──────────────────────── */

    /**
     * The default is <b>100 ms</b> — legacy's {@code WCConst.PER_DELAY_DEFAULT} (restored 2026-08-05).
     *
     * <p>100 ms is a documented operating value, not an unexercised initialiser. Handover
     * {@code 05_배치스케줄_운영가이드.md §5.3} states it twice (<i>그 외 은행: 기본 딜레이 100ms 적용</i>,
     * <i>기본 딜레이: 100ms</i>) and {@code 06_환경설정_항목목록.md §6.3} lists {@code WCConst.PER_DELAY_*}
     * as {@code 100/3000ms}. 05 §5.3 also warns that Shinhan and Woori are hard-coded <b>as delay
     * overrides and network-validation rules, not as a bank whitelist</b>, and names <b>BIDV and
     * Kookmin</b> as banks that process normally on the 기본 딜레이.
     *
     * <p>Woori's 3000 ms is Woori's: {@code 08_기능추출_이관가이드.md §8.4} gives it as <i>우리은행 3초.
     * 미적용 시 은행 측 유량제한(rate limit) 위반 가능</i> — one bank's published-nowhere-else limit, not a
     * general conservative floor. Generalising it costs ~5 minutes on a 100-row BIDV batch where legacy
     * spends ~10 seconds, which is a throughput regression no bank asked for.
     *
     * <p><b>If this test fails because someone raised the default, the fix is a per-bank constant</b> —
     * the way legacy does it for Woori — not a move of the default. A measured throughput answer from
     * the VAN Center belongs to the bank it was measured for.
     */
    @ParameterizedTest(name = "unknown bank [{0}] paces at legacy''s 100 ms default")
    @NullSource
    @ValueSource(strings = {
            "970436",   // VCB — seen only as failed pay-targets, now file-routed
            "970415",   // Vietinbank — plausible next arrival
            "",         // blank OUT_BANK_CD
            "  ",       // whitespace
            "970424 ",  // Shinhan with trailing space: NOT an exact match, must fall through to default
            "unknown",
    })
    void everyOtherBankPacesAtTheLegacyDefault(String outBankCd) {
        assertThat(pacer.perBankDelay(outBankCd))
                .as("a bank with no override must pace at legacy's documented 100 ms (handover 05 "
                        + "§5.3 / 06 §6.3), not at Woori's bank-specific 3000 ms (08 §8.4)")
                .isEqualTo(100L);
    }

    /**
     * The Woori override must be Woori's alone — the guard against re-generalising it.
     *
     * <p>This is the assertion that would have failed the 2026-08-01 change on the way in: the two
     * values must be <i>different</i>, because 3000 ms exists for one bank's rate limit (handover 08
     * §8.4) and collapsing the distinction silently applies it to every bank.
     */
    @Test
    @DisplayName("Woori's 3000 ms is bank-specific — the default must not equal it")
    void wooriOverrideIsNotTheDefault() {
        assertThat(pacer.perBankDelay("970457"))
                .as("Woori's 3000 ms floor (handover 08 §8.4) must not become every bank's floor")
                .isNotEqualTo(pacer.perBankDelay("970999"));
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
