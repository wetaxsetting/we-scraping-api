package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferListResponse;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferRow;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Scheduled-Transfer list parity coverage for Internal Transfer (legacy
 * {@code pymn_0009_001_r001}). Pure Mockito — mirrors
 * {@code VendorPaymentScheduledListTest}. Internal Transfer already
 * sourced the Schedule Date correctly; this test additionally locks the
 * Requestor = {@code EXPT_RESOL_MSTR.DRAFT_USER_NM} (name, not
 * {@code REG_ID}) parity fix from 2026-06-08, and that the consolidated
 * single-fetch {@code fetchExptResolByApprSeqNo} still feeds PAY_DATE.
 */
@ExtendWith(MockitoExtension.class)
class InternalTransferScheduledListTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private ScdulPymnSumrRepository        sumrRepository;
    @Mock private ExptResolMstrRepository        exptResolMstrRepository;
    @Mock private ExptResolApprvrInfmRepository  exptResolApprvrInfmRepository;

    @InjectMocks
    private InternalTransferService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
        lenient().when(exptResolApprvrInfmRepository.findChain(any(), any()))
                .thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_populatesScheduleDateAndRequestorNameFromExptResolMstr() {
        ScdulPymnSumr batch = batchRow("IT0001", "APS030", "lamine@gmail.com");
        Page<ScdulPymnSumr> page = new PageImpl<>(List.of(batch), PageRequest.of(0, 10), 1L);
        when(sumrRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(any(), any()))
                .thenReturn(List.of(exptMstr("APS030", "20260625", "Pham Van D")));

        ScheduledTransferListResponse res = service.list(
                "20260501", "20260531", null, null, null, null, 0, 10);

        assertThat(res.rows()).hasSize(1);
        ScheduledTransferRow row = res.rows().get(0);
        assertThat(row.payDate()).isEqualTo("20260625");
        assertThat(row.draftUserNm()).isEqualTo("Pham Van D");
        assertThat(row.regId()).isEqualTo("lamine@gmail.com");
    }

    @Test
    void list_nullsScheduleDateAndRequestorWhenNoApprovalRequest() {
        ScdulPymnSumr batch = batchRow("IT0002", null, "lamine@gmail.com");
        Page<ScdulPymnSumr> page = new PageImpl<>(List.of(batch), PageRequest.of(0, 10), 1L);
        when(sumrRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        ScheduledTransferListResponse res = service.list(
                "20260501", "20260531", null, null, null, null, 0, 10);

        ScheduledTransferRow row = res.rows().get(0);
        assertThat(row.payDate()).isNull();
        assertThat(row.draftUserNm()).isNull();
    }

    private static ScdulPymnSumr batchRow(String scdulPymnNo, String apprSeqNo, String regId) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setScdulPymnNo(scdulPymnNo);
        s.setScdulPymnTypeCd("03");
        s.setApprSeqNo(apprSeqNo);
        s.setCcyCd("VND");
        s.setTotalTranAmt(new BigDecimal("100000"));
        s.setTotTrnsCnt(new BigDecimal("1"));
        s.setRegDtm("20260528091320");
        s.setRegId(regId);
        return s;
    }

    private static ExptResolMstr exptMstr(String apprSeqNo, String payDate, String draftUserNm) {
        ExptResolMstr m = new ExptResolMstr();
        m.setApprSeqNo(apprSeqNo);
        m.setPayDate(payDate);
        m.setDraftUserNm(draftUserNm);
        m.setDocNo("VO_260603_002");
        return m;
    }
}
