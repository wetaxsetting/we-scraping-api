package com.kosign.wecms.domain.payment.vendorpayment.service;

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
 * Legacy-parity regression tests for Vendor Payment Result list mapping
 * (F-3 Approval Title, F-4a Approval Ref, Date/Time display + default sort).
 * Mirrors the reference {@code InternalTransferResultServiceTest}; the legacy
 * source is {@code SCDUL_PYMN_SUMR_R007} via {@code PymnHelper.pymn_0002_001_r001}
 * with {@code SCDUL_PYMN_TYPE_CD='01'}.
 */
class VendorPaymentResultParityTest {

    private ScdulPymnSumrRepository  sumrRepository;
    private ExptResolMstrRepository  exptResolMstrRepository;
    private VendorPaymentResultService service;

    @BeforeEach
    void setUp() {
        sumrRepository = mock(ScdulPymnSumrRepository.class);
        exptResolMstrRepository = mock(ExptResolMstrRepository.class);
        ScdulPymnSumrDetlRepository detlRepository = mock(ScdulPymnSumrDetlRepository.class);
        DispatchCountRepository dispatchCountRepository = mock(DispatchCountRepository.class);
        TransferLineDetailMapper transferLineDetailMapper = mock(TransferLineDetailMapper.class);
        DispatchResultResolver dispatchResultResolver = mock(DispatchResultResolver.class);
        service = new VendorPaymentResultService(
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

    /** Batch with distinct REG_DTM vs TRNF_DTM so the two can never be confused. */
    private static ScdulPymnSumr batch(String no, String apprSeqNo, String spsTitl) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey("VN_VNM0004_000001");
        s.setScdulPymnNo(no);
        s.setScdulPymnTypeCd("01");
        s.setCcyCd("VND");
        s.setTitl(spsTitl);                 // SPS.TITL = upload subject (File Name)
        s.setOrgFileNm(spsTitl + ".xlsx");
        s.setTotalTranAmt(new BigDecimal("100000"));
        s.setTotTrnsCnt(BigDecimal.ONE);
        s.setTrnfYn("Y");
        s.setRegDtm("20251218075056");       // creation — must NOT drive display/sort
        s.setTrnfDtm("20260107064804");      // dispatch — the legacy display/sort source
        s.setApprSeqNo(apprSeqNo);
        s.setErpFileNum(null);               // null in DEV — must NOT be the Approval Ref
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
        stubPage(batch("371", "1385", "WeCMS_Vendor_Payment_upload_EN (22)"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "Testing", "VO_260325_002")));

        TransferResultListResponse resp = list();

        assertThat(resp.rows().get(0).titl()).isEqualTo("Testing");          // ERM.TITL
        assertThat(resp.rows().get(0).fileNm()).isEqualTo("WeCMS_Vendor_Payment_upload_EN (22).xlsx"); // ORG_FILE_NM unchanged
    }

    @Test
    void approval_title_falls_back_to_sps_titl_when_erm_titl_blank() {
        stubPage(batch("900", "2001", "User Title"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("2001", "", "VO_260601_001")));      // ERM.TITL = "" (refactor no-post-title)

        assertThat(list().rows().get(0).titl()).isEqualTo("User Title");      // COALESCE(NULLIF(ERM.TITL,''), SPS.TITL)
    }

    @Test
    void approval_ref_comes_from_erm_doc_no_not_erp_file_num() {
        stubPage(batch("371", "1385", "upload"));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(anyString(), anyList()))
                .thenReturn(List.of(erm("1385", "Testing", "VO_260325_002")));

        assertThat(list().rows().get(0).docNo()).isEqualTo("VO_260325_002");  // ERM.DOC_NO, not null ERP_FILE_NUM
    }

    @Test
    void date_time_displayed_from_trnf_dtm_not_reg_dtm() {
        stubPage(batch("371", null, "upload"));   // no appr row — isolates the date mapping

        // Wire field name stays `reg_dtm` (legacy TRNF_DTM AS REG_DTM alias) but
        // the VALUE must be the TRNF_DTM.
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
