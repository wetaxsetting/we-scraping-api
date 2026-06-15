package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.common.TransferLineDetailMapper;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.internaltransfer.dto.ScheduledTransferDetailResponse;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ExptResolFileRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.service.DocNoGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Approval-Content parity coverage for Payment &gt; Expense Payment. Pure
 * Mockito — no Spring context, no DB. Asserts the save path persists
 * {@code postCont → EXPT_RESOL_MSTR.CONT} and the Scheduled-detail read path
 * returns it, matching Internal Transfer (the reference) and the fixed
 * Overseas Payment.
 */
@ExtendWith(MockitoExtension.class)
class ExpensePaymentServiceTest {

    private static final String TENANT = "VN_VNM0004_000001";
    private static final String USER   = "tester";

    @Mock private ScdulPymnSumrRepository       sumrRepository;
    @Mock private ScdulPymnSumrDetlRepository   detlRepository;
    @Mock private ExptResolMstrRepository       exptResolMstrRepository;
    @Mock private ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    @Mock private ExptResolFileRepository       exptResolFileRepository;
    @Mock private BusiInfmRepository            busiInfmRepository;
    @Mock private DocNoGenerator                docNoGenerator;
    @Mock private AuditLogService               auditLogService;
    @Mock private TransferLineDetailMapper      transferLineDetailMapper;
    @Mock private ApprovalBoxFanoutService      approvalBoxFanoutService;
    @Mock private Clock                         clock;

    @InjectMocks
    private ExpensePaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER, TENANT, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestApproval_persists_postCont_into_cont() {
        stubRequestApprovalChain();

        service.requestApproval("SC1", req("My approval note"));

        ArgumentCaptor<ExptResolMstr> captor = ArgumentCaptor.forClass(ExptResolMstr.class);
        verify(exptResolMstrRepository).save(captor.capture());
        assertThat(captor.getValue().getCont()).isEqualTo("My approval note");
        // F-4 (legacy parity): master APPR_STS is "2" (In Progress), not "0".
        assertThat(captor.getValue().getApprSts()).isEqualTo("2");
    }

    @Test
    void requestApproval_blank_postCont_persists_null() {
        stubRequestApprovalChain();

        service.requestApproval("SC1", req("   "));

        ArgumentCaptor<ExptResolMstr> captor = ArgumentCaptor.forClass(ExptResolMstr.class);
        verify(exptResolMstrRepository).save(captor.capture());
        assertThat(captor.getValue().getCont()).isNull();
    }

    @Test
    void detail_returns_cont_when_approval_content_exists() {
        stubDetailChain(mstrWithCont("My approval note"));

        ScheduledTransferDetailResponse resp = service.detail("SC1");

        assertThat(resp.cont()).isEqualTo("My approval note");
    }

    @Test
    void detail_returns_null_cont_when_blank() {
        stubDetailChain(mstrWithCont("   "));

        ScheduledTransferDetailResponse resp = service.detail("SC1");

        assertThat(resp.cont()).isNull();
    }

    /* ---- G1: footer "Requested on" = EXPT_RESOL_MSTR.REG_DTM, not batch ---- */

    @Test
    void detail_requested_on_uses_approval_reg_dtm_not_batch_upload() {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(TENANT);
        master.setScdulPymnNo("SC1");
        master.setApprSeqNo("5");
        master.setCcyCd("VND");
        master.setRegDtm("20260101000000");        // batch upload (SCDUL_PYMN_SUMR.REG_DTM)
        ExptResolMstr mstr = new ExptResolMstr();
        mstr.setApprSeqNo("5");
        mstr.setRegDtm("20260610093000");          // approval request (EXPT_RESOL_MSTR.REG_DTM)
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(any(), any(), any()))
                .thenReturn(Optional.of(master));
        when(transferLineDetailMapper.buildChartIndex(any())).thenReturn(Collections.emptyMap());
        when(detlRepository.findByBizKeyAndScdulPymnNo(any(), any(), any())).thenReturn(List.of());
        when(transferLineDetailMapper.resolveBankNm(any())).thenReturn("Some Bank");
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(any(), any())).thenReturn(List.of(mstr));
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoOrderByFileIdAsc(any(), any()))
                .thenReturn(List.of());

        ScheduledTransferDetailResponse resp = service.detail("SC1");

        assertThat(resp.apprRegDtm()).isEqualTo("20260610093000");   // approval time
        assertThat(resp.regDtm()).isEqualTo("20260101000000");       // batch time, unchanged
    }

    private void stubRequestApprovalChain() {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(TENANT);
        master.setScdulPymnNo("SC1");
        master.setCcyCd("VND");
        master.setTotalTranAmt(BigDecimal.TEN);
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(any(), any(), any()))
                .thenReturn(Optional.of(master));
        when(exptResolMstrRepository.findMaxApprSeqNoByBizKey(any())).thenReturn(null);
        when(sumrRepository.findMaxApprSeqNoByBizKey(any())).thenReturn(null);
        when(busiInfmRepository.findById(any())).thenReturn(Optional.empty());
        when(docNoGenerator.nextDocNo(any())).thenReturn("EO_260609_001");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-09T03:00:00Z"));
    }

    private void stubDetailChain(ExptResolMstr mstr) {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(TENANT);
        master.setScdulPymnNo("SC1");
        master.setApprSeqNo("5");
        master.setCcyCd("VND");
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(any(), any(), any()))
                .thenReturn(Optional.of(master));
        when(transferLineDetailMapper.buildChartIndex(any())).thenReturn(Collections.emptyMap());
        when(detlRepository.findByBizKeyAndScdulPymnNo(any(), any(), any())).thenReturn(List.of());
        when(transferLineDetailMapper.resolveBankNm(any())).thenReturn("Some Bank");
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(any(), any())).thenReturn(List.of(mstr));
        when(exptResolFileRepository.findByBizKeyAndApprSeqNoOrderByFileIdAsc(any(), any()))
                .thenReturn(List.of());
    }

    private static ExptResolMstr mstrWithCont(String cont) {
        ExptResolMstr m = new ExptResolMstr();
        m.setApprSeqNo("5");
        m.setCont(cont);
        return m;
    }

    private static RequestApprovalRequest req(String postCont) {
        return new RequestApprovalRequest(
                "My title", postCont, "11704000", "123456789", "VND",
                null, null, 1L,
                List.of(new RequestApprovalRequest.ApproverInput("u1", "User One")),
                null);
    }
}
