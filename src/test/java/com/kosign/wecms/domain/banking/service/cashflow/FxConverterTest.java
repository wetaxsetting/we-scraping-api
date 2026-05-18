package com.kosign.wecms.domain.banking.service.cashflow;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FxConverter} against the verified table in
 * docs/refactor-wecms/features/cash-flow-report.md §3.9. Each
 * assertion below mirrors one cell of the legacy
 * {@code filtered_payment_data} CASE expression, including the
 * {@code ROUND()} precision (2 dp for VND/KRW divisors, 4 dp for USD
 * divisor — matches Postgres {@code ROUND(numeric, integer)} with
 * half-up rounding).
 */
class FxConverterTest {

    private final FxConverter fx = new FxConverter();
    private static final BigDecimal USD_RATE = new BigDecimal("26000");
    private static final BigDecimal KRW_RATE = new BigDecimal("19");

    /* base = VND ---------------------------------------------------- */

    @Test
    void vnd_to_vnd_isNoop() {
        assertThat(fx.convert(bd("1000.50"), "VND", "VND", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("1000.50");
    }

    @Test
    void usd_to_vnd_multipliesByUsdRate_round2() {
        // 1.5 * 26000 = 39000.00
        assertThat(fx.convert(bd("1.5"), "USD", "VND", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("39000.00");
    }

    @Test
    void krw_to_vnd_multipliesByKrwRate_round2() {
        // 100 * 19 = 1900.00
        assertThat(fx.convert(bd("100"), "KRW", "VND", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("1900.00");
    }

    /* base = USD ---------------------------------------------------- */

    @Test
    void usd_to_usd_isNoop() {
        assertThat(fx.convert(bd("1.5"), "USD", "USD", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("1.5");
    }

    @Test
    void vnd_to_usd_dividesByUsdRate_round4() {
        // 26000 / 26000 = 1.0000
        assertThat(fx.convert(bd("26000"), "VND", "USD", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("1.0000");
        // 39000.5 / 26000 = 1.5000 (rounded)
        assertThat(fx.convert(bd("39000"), "VND", "USD", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("1.5000");
    }

    @Test
    void krw_to_usd_chainsThroughVnd_round4() {
        // (100 * 19) / 26000 = 0.0731 (rounded half-up at 4 dp)
        assertThat(fx.convert(bd("100"), "KRW", "USD", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("0.0731");
    }

    /* base = KRW ---------------------------------------------------- */

    @Test
    void krw_to_krw_isNoop() {
        assertThat(fx.convert(bd("100"), "KRW", "KRW", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("100");
    }

    @Test
    void vnd_to_krw_dividesByKrwRate_round2() {
        // 1900 / 19 = 100.00
        assertThat(fx.convert(bd("1900"), "VND", "KRW", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("100.00");
    }

    @Test
    void usd_to_krw_chainsThroughVnd_round2() {
        // (1.5 * 26000) / 19 = 2052.63 (rounded half-up at 2 dp)
        assertThat(fx.convert(bd("1.5"), "USD", "KRW", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("2052.63");
    }

    /* edge cases ---------------------------------------------------- */

    @Test
    void nullAmount_returnsNull() {
        assertThat(fx.convert(null, "VND", "VND", USD_RATE, KRW_RATE)).isNull();
    }

    @Test
    void nullSourceCcy_treatedAsVnd() {
        // Defensive — legacy COALESCE(ccy_cd, 'VND') equivalent.
        assertThat(fx.convert(bd("1000"), null, "VND", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("1000");
    }

    @Test
    void unknownSourceCcy_passthrough_whenBaseIsVnd() {
        // ELSE branch in the legacy CASE for VND base → original amount.
        assertThat(fx.convert(bd("999"), "EUR", "VND", USD_RATE, KRW_RATE))
                .isEqualByComparingTo("999");
    }

    private static BigDecimal bd(String s) { return new BigDecimal(s); }
}
