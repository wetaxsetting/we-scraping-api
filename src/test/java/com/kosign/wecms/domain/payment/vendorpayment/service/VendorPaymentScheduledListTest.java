package com.kosign.wecms.domain.payment.vendorpayment.service;

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
 * Scheduled-Payment list parity coverage for Vendor Payment (legacy
 * {@code pymn_0001_001_r001}). Pure Mockito — no Spring context, no DB.
 *
 * <p>Regression guard for the 2026-06-08 Scheduled-page parity bug
 * (see {@code docs/audits/scheduled-page-parity-2026-06-08.md}): the
 * "Schedule Date" column must carry {@code EXPT_RESOL_MSTR.PAY_DATE}
 * (NOT the dispatch-time {@code SCDUL_PYMN_SUMR.PAY_TRAN_DT}, which is
 * null pre-dispatch), and the "Requestor" column must carry
 * {@code EXPT_RESOL_MSTR.DRAFT_USER_NM} (the user NAME), not the
 * {@code REG_ID} login id.
 */
@ExtendWith(MockitoExtension.class)
class VendorPaymentScheduledListTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private ScdulPymnSumrRepository        sumrRepository;
    @Mock private ExptResolMstrRepository        exptResolMstrRepository;
    @Mock private ExptResolApprvrInfmRepository  exptResolApprvrInfmRepository;

    @InjectMocks
    private VendorPaymentService service;

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
        ScdulPymnSumr batch = batchRow("VO0001", "APS001", "lamine@gmail.com");
        Page<ScdulPymnSumr> page = new PageImpl<>(List.of(batch), PageRequest.of(0, 10), 1L);
        when(sumrRepository.search(any(), any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);
        // The approval master carries the requested pay date + the requesting
        // user's display name — the two columns the legacy grid joins in.
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(any(), any()))
                .thenReturn(List.of(exptMstr("APS001", "20260610", "Nguyen Van A")));

        ScheduledTransferListResponse res = service.list(
                "20260501", "20260531", null, null, null, null, 0, 10);

        assertThat(res.rows()).hasSize(1);
        ScheduledTransferRow row = res.rows().get(0);
        // Schedule Date = EXPT_RESOL_MSTR.PAY_DATE (was empty when sourced
        // from SCDUL_PYMN_SUMR.PAY_TRAN_DT — the bug).
        assertThat(row.payDate()).isEqualTo("20260610");
        // Requestor = EXPT_RESOL_MSTR.DRAFT_USER_NM (the NAME) — not reg_id.
        assertThat(row.draftUserNm()).isEqualTo("Nguyen Van A");
        assertThat(row.regId()).isEqualTo("lamine@gmail.com");
    }

    @Test
    void list_nullsScheduleDateAndRequestorWhenNoApprovalRequest() {
        // Draft batch with no APPR_SEQ_NO → no EXPT_RESOL_MSTR join → both
        // columns null (frontend falls back to reg_id for Requestor).
        ScdulPymnSumr batch = batchRow("VO0002", null, "lamine@gmail.com");
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
        s.setScdulPymnTypeCd("01");
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
