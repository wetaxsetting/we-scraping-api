package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.dispatch.DispatchCountRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchResultResolver;
import com.kosign.wecms.domain.payment.internaltransfer.dto.TransferResultListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Legacy-parity regression tests for Payroll Payment Result list mapping
 * (F-3 Approval Title, F-4a Approval Ref, Date/Time display + default sort),
 * plus confirmation that the Payroll Type (APPR_FUND_TP) still resolves after
 * the lookup was consolidated into the single approval-master map. Mirrors
 * {@code InternalTransferResultServiceTest}; legacy source
 * {@code SCDUL_PYMN_SUMR_R007} / {@code _R010} with {@code SCDUL_PYMN_TYPE_CD='00'}.
 */
class PayrollPaymentResultParityTest {

    private ScdulPymnSumrRepository   sumrRepository;
    private ExptResolMstrRepository   exptResolMstrRepository;
    private PayrollPaymentResultService service;

    @BeforeEach
    void setUp() {
        sumrRepository = mock(ScdulPymnSumrRepository.class);
        exptResolMstrRepository = mock(ExptResolMstrRepository.class);
        ScdulPymnSumrDetlRepository detlRepository = mock(ScdulPymnSumrDetlRepository.class);
        DispatchCountRepository dispatchCountRepository = mock(DispatchCountRepository.class);
        TransferLineDetailMapper transferLineDetailMapper = mock(TransferLineDetailMapper.class);
        DispatchResultResolver dispatchResultResolver = mock(DispatchResultResolver.class);
        service = new PayrollPaymentResultService(
                sumrRepository, detlRepository, dispatchCountRepository,
                transferLineDetailMapper, dispatchResultResolver, exptResolMstrRepository);

        when(dispatchCountRepository.countByBatch(anyString(), anyList()))
                .thenReturn(Collections.emptyList());
        TenantContext.set("tester", "tester", "VN_VNM0004_000001",
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ScdulPymnSumr batch(String no, String apprSeqNo, String spsTitl) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey("VN_VNM0004_000001");
        s.setScdulPymnNo(no);
        s.setScdulPymnTypeCd("00");
        s.setCcyCd("VND");
        s.setTitl(spsTitl);
        s.setOrgFileNm(spsTitl + ".xlsx");
        s.setTotalTranAmt(new BigDecimal("100000"));
        s.setTotTrnsCnt(BigDecimal.ONE);
        s.setTrnfYn("Y");
        s.setRegDtm("20251218075056");
        s.setTrnfDtm("20260107064804");
        s.setApprSeqNo(apprSeqNo);
        s.setErpFileNum(null);
        return s;
    }

    private static ExptResolMstr erm(String apprSeqNo, String titl, String docNo, String apprFundTp) {
        ExptResolMstr e = new ExptResolMstr();
        e.setApprSeqNo(apprSeqNo);
        e.setTitl(titl);
        e.setDocNo(docNo);
        e.setApprFundTp(apprFundTp);
        return e;
    }

    private void stubPage(ScdulPymnSumr... rows) {
        when(sumrRepository.searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(rows)));
    }

    private TransferResultListResponse list() {
        return service.list("20260101", "20260131", null, null, null, null, 0, 10);
    }

    @Test
    void approval_title_comes_from_erm_titl_not_sps_titl() {
        stubPage(batch("371", "1385", "WeCMS_Payroll_upload_EN"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "March Salary", "VO_260325_002", "0")));

        assertThat(list().rows().get(0).titl()).isEqualTo("March Salary");
    }

    @Test
    void approval_title_falls_back_to_sps_titl_when_erm_titl_blank() {
        stubPage(batch("900", "2001", "User Title"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("2001", "", "VO_260601_001", "1")));

        assertThat(list().rows().get(0).titl()).isEqualTo("User Title");
    }

    @Test
    void approval_ref_comes_from_erm_doc_no_not_erp_file_num() {
        stubPage(batch("371", "1385", "upload"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "March Salary", "VO_260325_002", "0")));

        assertThat(list().rows().get(0).docNo()).isEqualTo("VO_260325_002");
    }

    @Test
    void payroll_type_still_resolves_from_erm_appr_fund_tp() {
        stubPage(batch("371", "1385", "upload"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "March Salary", "VO_260325_002", "0")));

        assertThat(list().rows().get(0).apprFundTp()).isEqualTo("0");
    }

    @Test
    void date_time_displayed_from_trnf_dtm_not_reg_dtm() {
        stubPage(batch("371", null, "upload"));
        assertThat(list().rows().get(0).regDtm()).isEqualTo("20260107064804");
    }

    @Test
    void default_sort_is_trnf_dtm_desc_then_scdul_pymn_no_desc() {
        stubPage(batch("371", null, "upload"));
        list();
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(sumrRepository).searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), captor.capture());
        assertThat(captor.getValue().getSort()).containsExactly(
                Sort.Order.desc("trnfDtm"),
                Sort.Order.desc("scdulPymnNo"));
    }

    @Test
    void completed_filter_sends_C_to_row_query_and_null_to_footer() {
        stubPage(batch("371", "1385", "upload"));
        service.list("20260101", "20260131", null, "C", null, null, 0, 10);

        verify(sumrRepository).searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), eq("C"), any(Pageable.class));
        // Footer total stays unfiltered — legacy SCDUL_PYMN_SUMR_R008 (C2).
        verify(sumrRepository).searchDispatchedCurrencyTotals(
                anyString(), anyString(), anyString(), anyString(),
                any(), isNull());
    }

    @Test
    void in_progress_filter_sends_P_to_row_query() {
        stubPage(batch("371", "1385", "upload"));
        service.list("20260101", "20260131", null, "P", null, null, 0, 10);

        verify(sumrRepository).searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), eq("P"), any(Pageable.class));
    }
}
