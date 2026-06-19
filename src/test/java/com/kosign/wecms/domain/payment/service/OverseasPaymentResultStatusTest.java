package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.dto.OverseasResultListResponse;
import com.kosign.wecms.domain.payment.dto.OverseasResultRow;
import com.kosign.wecms.domain.payment.repository.OverseasPaymentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F1 / F2 (Overseas Payment Result list) — verifies the refactor matches
 * legacy {@code IDO.OVRS_PYMN_SUMR_R005} status semantics:
 *
 * <ul>
 *   <li>F2 — the Completed / In-Progress checkboxes (PROC_STAT CSV
 *       {@code "0,3"} / {@code "6"}) map to the computed {@code TRNF_STTS}
 *       letters {@code {C,F}} / {@code {P}} the query filters on (legacy
 *       {@code getDynamicStatusFilter}); footer totals ignore the status
 *       filter (legacy {@code R005_CNT} parity).</li>
 *   <li>F2 — per-batch success / error / pending counts + computed status
 *       round-trip from the query (no longer stubbed to 0).</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentResultStatusTest {

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

    /** A 23-column buildResultSql row with the count/status slots populated. */
    private static Object[] row() {
        Object[] r = new Object[23];
        r[0]  = "OP1";   // ovrs_pymn_no
        r[1]  = "USD";   // ccy_cd
        r[2]  = "batch.xlsx"; // file_nm (TITL — F4)
        r[4]  = 5;       // tot_trns_cnt
        r[5]  = "20260115090000"; // reg_dtm (=TRNF_DT — F1)
        r[11] = "Y";     // trnf_yn
        r[17] = "1385";  // appr_seq_no
        r[18] = 4;       // tot_trns_succ_cnt
        r[19] = 1;       // tot_trns_error_cnt
        r[20] = 0;       // prs_cnt
        r[21] = "C";     // trnf_stts (computed)
        return r;
    }

    @Test
    void result_row_surfaces_real_counts_and_computed_status() {
        when(paymentRepo.findResultById(BIZ_KEY, "OP1")).thenReturn(Optional.of(row()));

        OverseasResultRow out = service.getResultById("OP1");

        assertThat(out.successCount()).isEqualTo(4);
        assertThat(out.failedCount()).isEqualTo(1);
        assertThat(out.prsCnt()).isEqualTo(0);
        assertThat(out.trnfStts()).isEqualTo("C");
        // F4 — File Name sourced from OVRS_PYMN_SUMR.TITL (legacy R005 FILE_NM).
        assertThat(out.fileNm()).isEqualTo("batch.xlsx");
        // F1 — Date/Time sourced from TRNF_DT (aliased reg_dtm in R005).
        assertThat(out.regDtm()).isEqualTo("20260115090000");
    }

    @Test
    void completed_checkbox_maps_to_status_letters_C_and_F() {
        stubList();
        service.listResult("20260101", "20260131", null, "0,3", 0, 10, null, null);
        assertThat(captureStatusLetters()).containsExactly("C", "F");
    }

    @Test
    void in_progress_checkbox_maps_to_status_letter_P() {
        stubList();
        service.listResult("20260101", "20260131", null, "6", 0, 10, null, null);
        assertThat(captureStatusLetters()).containsExactly("P");
    }

    @Test
    void no_status_filter_passes_null() {
        stubList();
        service.listResult("20260101", "20260131", null, null, 0, 10, null, null);
        assertThat(captureStatusLetters()).isNull();
    }

    @Test
    void footer_totals_never_apply_status_filter() {
        stubList();
        service.listResult("20260101", "20260131", null, "0,3", 0, 10, null, null);
        // Legacy R005_CNT parity — totals are fed date+keyword only, no status.
        verify(paymentRepo).sumResultByCurrency(eq(BIZ_KEY), eq("20260101"), eq("20260131"), any());
    }

    @SuppressWarnings("unchecked")
    private List<String> captureStatusLetters() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(paymentRepo).countResult(eq(BIZ_KEY), eq("20260101"), eq("20260131"), any(), captor.capture());
        return captor.getValue();
    }

    private void stubList() {
        when(paymentRepo.countResult(anyString(), anyString(), anyString(), any(), any())).thenReturn(1L);
        when(paymentRepo.findResult(anyString(), anyString(), anyString(), any(), any(),
                anyInt(), anyInt(), any())).thenReturn(java.util.Collections.singletonList(row()));
        when(paymentRepo.sumResultByCurrency(anyString(), anyString(), anyString(), any()))
                .thenReturn(List.of());
    }

    @Test
    void list_returns_mapped_rows() {
        stubList();
        OverseasResultListResponse resp =
                service.listResult("20260101", "20260131", null, null, 0, 10, null, null);
        assertThat(resp.rows()).hasSize(1);
        assertThat(resp.rows().get(0).trnfStts()).isEqualTo("C");
    }
}
