package com.kosign.wecms.domain.payment.expensepayment.service;

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
import java.util.Optional;

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
 * Legacy-parity regression tests for Expense Payment Result list mapping
 * (F-3 Approval Title, F-4a Approval Ref, Date/Time display + default sort).
 * Mirrors {@code InternalTransferResultServiceTest}; legacy source
 * {@code SCDUL_PYMN_SUMR_R007} with {@code SCDUL_PYMN_TYPE_CD='02'}.
 */
class ExpensePaymentResultParityTest {

    private ScdulPymnSumrRepository     sumrRepository;
    private ExptResolMstrRepository     exptResolMstrRepository;
    private ScdulPymnSumrDetlRepository detlRepository;
    private ExpensePaymentResultService service;

    @BeforeEach
    void setUp() {
        sumrRepository = mock(ScdulPymnSumrRepository.class);
        exptResolMstrRepository = mock(ExptResolMstrRepository.class);
        detlRepository = mock(ScdulPymnSumrDetlRepository.class);
        DispatchCountRepository dispatchCountRepository = mock(DispatchCountRepository.class);
        TransferLineDetailMapper transferLineDetailMapper = mock(TransferLineDetailMapper.class);
        DispatchResultResolver dispatchResultResolver = mock(DispatchResultResolver.class);
        service = new ExpensePaymentResultService(
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
        s.setScdulPymnTypeCd("02");
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

    private static ExptResolMstr erm(String apprSeqNo, String titl, String docNo) {
        ExptResolMstr e = new ExptResolMstr();
        e.setApprSeqNo(apprSeqNo);
        e.setTitl(titl);
        e.setDocNo(docNo);
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
        stubPage(batch("371", "1385", "WeCMS_Expense_Payment_upload_EN"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "QA TEST", "VO_260114_002")));

        assertThat(list().rows().get(0).titl()).isEqualTo("QA TEST");
    }

    @Test
    void approval_title_falls_back_to_sps_titl_when_erm_titl_blank() {
        stubPage(batch("900", "2001", "User Title"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("2001", "", "VO_260601_001")));

        assertThat(list().rows().get(0).titl()).isEqualTo("User Title");
    }

    @Test
    void approval_ref_comes_from_erm_doc_no_not_erp_file_num() {
        stubPage(batch("371", "1385", "upload"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "QA TEST", "VO_260114_002")));

        assertThat(list().rows().get(0).docNo()).isEqualTo("VO_260114_002");
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

    /* ---------------------------------------------------------------- *
     *  C1 — Result DETAIL Approval Ref (DOC_NO) comes from EXPT_RESOL_MSTR
     * ---------------------------------------------------------------- */

    private ScdulPymnSumr master(String no, String apprSeqNo) {
        ScdulPymnSumr s = batch(no, apprSeqNo, "upload");
        s.setOutBankCd("970436");
        s.setWdrlActNo("0123456789");
        s.setWdrlCcyCd("VND");
        s.setRegId("tester");
        s.setFileSize(null);
        return s;
    }

    @Test
    void result_detail_approval_ref_comes_from_erm_doc_no_not_erp_file_num() {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(master("371", "1385")));
        when(detlRepository.findByBizKeyAndScdulPymnNo(anyString(), anyString(), any()))
                .thenReturn(List.of());
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "QA TEST", "VO_260114_002")));

        assertThat(service.detail("371").docNo()).isEqualTo("VO_260114_002");
    }

    @Test
    void result_detail_approval_ref_is_null_when_no_approval_master() {
        // ERP_FILE_NUM stays null in DEV — a batch with no submitted approval
        // request must surface a null Approval Ref, never the erp_file_num.
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(
                anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(master("900", null)));
        when(detlRepository.findByBizKeyAndScdulPymnNo(anyString(), anyString(), any()))
                .thenReturn(List.of());

        assertThat(service.detail("900").docNo()).isNull();
    }

    /* ---------------------------------------------------------------- *
     *  C2 — status filter is applied to rows ('C'/'P'), never the footer
     * ---------------------------------------------------------------- */

    @Test
    void completed_filter_sends_C_to_row_query_and_null_to_footer() {
        stubPage(batch("371", "1385", "upload"));
        service.list("20260101", "20260131", null, "C", null, null, 0, 10);

        verify(sumrRepository).searchDispatched(
                eq("VN_VNM0004_000001"), eq("02"),
                eq("20260101000000"), eq("20260131235959"),
                isNull(), eq("C"), any(Pageable.class));
        // Footer total is NOT status-filtered — legacy SCDUL_PYMN_SUMR_R008.
        verify(sumrRepository).searchDispatchedCurrencyTotals(
                eq("VN_VNM0004_000001"), eq("02"),
                eq("20260101000000"), eq("20260131235959"),
                isNull(), isNull());
    }

    @Test
    void in_progress_filter_sends_P_to_row_query() {
        stubPage(batch("371", "1385", "upload"));
        service.list("20260101", "20260131", null, "P", null, null, 0, 10);

        verify(sumrRepository).searchDispatched(
                anyString(), anyString(), anyString(), anyString(),
                any(), eq("P"), any(Pageable.class));
    }

    @Test
    void footer_total_stays_unfiltered_even_when_a_status_filter_is_active() {
        stubPage(batch("371", "1385", "upload"));
        service.list("20260101", "20260131", null, "P", null, null, 0, 10);

        // Whatever the row filter, the footer aggregate always gets null for the
        // last (status) arg so the grand total stays stable as the list narrows.
        verify(sumrRepository).searchDispatchedCurrencyTotals(
                anyString(), anyString(), anyString(), anyString(),
                any(), isNull());
    }
}
