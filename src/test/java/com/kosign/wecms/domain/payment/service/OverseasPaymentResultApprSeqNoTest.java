package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.dto.OverseasResultRow;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * F4b (Overseas) — the Overseas Result row surfaces APPR_SEQ_NO (wire
 * {@code appr_seq_no}) from {@code buildResultSql} column 17 so the frontend can
 * map {@code approvalRefId} and open the shared Approval Details popup. Null
 * round-trips as null (draft → plain text).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentResultApprSeqNoTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";

    @Mock private OverseasPaymentRepository paymentRepo;
    @InjectMocks private OverseasPaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "tester", BIZ_KEY, LangCd.EN,
                TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** buildResultSql row (indices 0..22); only the fields under test are set. */
    private static Object[] row(String apprSeqNo) {
        Object[] r = new Object[23];
        r[0] = "OP1";   // ovrs_pymn_no
        r[4] = 0;       // tot_trns_cnt
        r[11] = "Y";    // trnf_yn
        r[17] = apprSeqNo;
        r[18] = 0;      // tot_trns_succ_cnt
        r[19] = 0;      // tot_trns_error_cnt
        r[20] = 0;      // prs_cnt
        r[21] = "C";    // trnf_stts
        return r;
    }

    @Test
    void result_row_exposes_appr_seq_no_from_query() {
        when(paymentRepo.findResultById(BIZ_KEY, "OP1")).thenReturn(Optional.of(row("1385")));

        OverseasResultRow out = service.getResultById("OP1");

        assertThat(out.apprSeqNo()).isEqualTo("1385");
    }

    @Test
    void result_row_appr_seq_no_is_null_for_draft() {
        when(paymentRepo.findResultById(BIZ_KEY, "OP1")).thenReturn(Optional.of(row(null)));

        assertThat(service.getResultById("OP1").apprSeqNo()).isNull();
    }
}
