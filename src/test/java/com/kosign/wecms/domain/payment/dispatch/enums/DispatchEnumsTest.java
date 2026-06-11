package com.kosign.wecms.domain.payment.dispatch.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the bulk-transfer status/type enums — pure JUnit, no Spring
 * context, no DB. Pins the legacy {@code WCConst} codes so a typo in a code
 * literal fails the build.
 */
class DispatchEnumsTest {

    @Test
    void procStat_codes_match_legacy_WCConst() {
        assertThat(ProcStat.SUCC.code()).isEqualTo("0");
        assertThat(ProcStat.FAIL.code()).isEqualTo("3");
        assertThat(ProcStat.REQ.code()).isEqualTo("6");
        assertThat(ProcStat.WAIT.code()).isEqualTo("9");
        assertThat(ProcStat.fromCode("9")).contains(ProcStat.WAIT);
        assertThat(ProcStat.fromCode("X")).isEmpty();
    }

    @Test
    void procStep_codes_match_legacy_WCConst() {
        assertThat(ProcStep.REQ.code()).isEqualTo("0");
        assertThat(ProcStep.REQ_RECV.code()).isEqualTo("1");
        assertThat(ProcStep.DEPO_REQ.code()).isEqualTo("2");
        assertThat(ProcStep.DEPO_RES_RECV.code()).isEqualTo("3");
        assertThat(ProcStep.fromCode("1")).contains(ProcStep.REQ_RECV);
        assertThat(ProcStep.fromCode("")).isEmpty();
    }

    @Test
    void apiTranTp_codes_match_legacy_interface_codes() {
        assertThat(ApiTranTp.BULK_RECEPTION.code()).isEqualTo("00301");
        assertThat(ApiTranTp.BULK_EXECUTION.code()).isEqualTo("00302");
        assertThat(ApiTranTp.BULK_RECEPTION_RESULT.code()).isEqualTo("01301");
        assertThat(ApiTranTp.BULK_EXECUTION_RESULT.code()).isEqualTo("01302");
        assertThat(ApiTranTp.fromCode("00301")).contains(ApiTranTp.BULK_RECEPTION);
        assertThat(ApiTranTp.fromCode("99999")).isEmpty();
    }
}
