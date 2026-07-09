package com.kosign.wecms.domain.payment.payrollpayment.controller;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Payment <b>Result</b> detail route validation
 * ({@code GET /api/v1/payment/payroll-payment/results/{id}}).
 *
 * <p>The Result detail service looks up exclusively by numeric
 * {@code SCDUL_PYMN_NO} (legacy {@code pymn_0006_002} navigates by
 * {@code SCDUL_PYMN_NO} from {@code sessionStorage}, never by {@code DOC_NO}).
 * The route pattern must therefore accept the numeric key and REJECT a
 * {@code DOC_NO} (VO_…) id at the boundary — so an unsupported path returns
 * WBE_VALIDATION instead of a misleading 404. The Scheduled route keeps its
 * dual numeric-or-DOC_NO pattern (it has a DOC_NO fallback); this test asserts
 * the Result route diverged correctly.
 */
class PayrollPaymentResultRouteValidationTest {

    private static final Pattern RESULT =
            Pattern.compile(PayrollPaymentController.RESULT_SCDUL_PYMN_NO_PATTERN);

    @Test
    void accepts_numeric_scdul_pymn_no() {
        assertThat(RESULT.matcher("8").matches()).isTrue();
        assertThat(RESULT.matcher("241").matches()).isTrue();
        assertThat(RESULT.matcher("123456789012345").matches()).isTrue();
    }

    @Test
    void rejects_doc_no_style_ids() {
        assertThat(RESULT.matcher("VO_260613_002").matches()).isFalse();
        assertThat(RESULT.matcher("VO_260325_002").matches()).isFalse();
    }

    @Test
    void rejects_empty_and_non_numeric() {
        assertThat(RESULT.matcher("").matches()).isFalse();
        assertThat(RESULT.matcher("abc").matches()).isFalse();
        assertThat(RESULT.matcher("8; DROP TABLE").matches()).isFalse();
    }
}
