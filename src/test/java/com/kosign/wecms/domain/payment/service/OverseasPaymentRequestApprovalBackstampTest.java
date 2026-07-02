package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalAttachmentWriter;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalDisplayFlagsResolver;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatusChangedEvent;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import com.kosign.wecms.domain.payment.entity.OvrsPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService;
import com.kosign.wecms.domain.payment.overseas.repository.OvrsPymnSumrRepository;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H-1 (legacy parity, comm_0192_001_c005_act.jsp:404-410
 * {@code updateOverseasPaymentSummary}): the Overseas single-payment Request
 * Approval path must back-stamp the withdrawal account selected in the popup onto
 * {@code OVRS_PYMN_SUMR} — {@code OUT_BANK_CD} (NAPAS-converted),
 * {@code WDRL_ACT_NO}, {@code WDRL_CCY_CD} — alongside the existing
 * {@code APPR_SEQ_NO} + {@code TITL} stamp.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OverseasPaymentRequestApprovalBackstampTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String OVRS_PYMN_NO = "OV-1";

    @Mock private OvrsPymnSumrRepository sumrRepository;
    @Mock private LegacyFunctionRepository legacyFunctions;
    @Mock private BusiInfmRepository busiInfmRepository;
    @Mock private DocNoGenerator docNoGenerator;
    @Mock private OutBankCdNormalizer outBankCdNormalizer;
    @Mock private ExptResolMstrRepository exptResolMstrRepository;
    @Mock private ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    @Mock private ApprovalBoxFanoutService approvalBoxFanoutService;
    @Mock private ApprovalNotificationService approvalNotificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private ApprovalDisplayFlagsResolver displayFlagsResolver;
    @Mock private ApprovalAttachmentWriter approvalAttachmentWriter;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private OverseasPaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestApproval_backstampsWithdrawalAccountOntoOvrsPymnSumr() {
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(BIZ_KEY);
        sumr.setOvrsPymnNo(OVRS_PYMN_NO);
        sumr.setCcyCd("USD");
        sumr.setTotalTranAmt(new BigDecimal("2500"));
        // trnfYn / apprSeqNo null — un-submitted, not yet transferred.

        when(sumrRepository.findByBizKeyAndOvrsPymnNo(anyString(), anyString()))
                .thenReturn(Optional.of(sumr));
        when(legacyFunctions.nextApprSeqNo(anyString())).thenReturn("200");
        when(docNoGenerator.nextDocNo(anyString())).thenReturn("VO_260613_002");
        when(busiInfmRepository.findById(anyString())).thenReturn(Optional.empty());
        when(outBankCdNormalizer.normalize("11704088")).thenReturn("970424");
        when(displayFlagsResolver.resolve(anyString()))
                .thenReturn(new ApprovalDisplayFlagsResolver.DisplayFlags("Y", "N", "Y", "N"));

        RequestApprovalRequest req = new RequestApprovalRequest(
                "Overseas Title",
                "note",
                "11704088",          // bank_cd (internal → normalized to NAPAS)
                "007654321",         // acct_no
                "USD",               // ccy_cd
                "20260613",          // pay_date
                null,                // appr_fund_tp
                7L,                  // appr_line_no
                List.of(new RequestApprovalRequest.ApproverInput("u1", "User One")),
                List.of());

        // M-1: a non-empty attachments list must be forwarded to the shared writer.
        List<MultipartFile> attachments = List.of(
                new MockMultipartFile("attachments", "evidence.pdf", "application/pdf", new byte[] {1, 2, 3}));

        var resp = service.requestApproval(OVRS_PYMN_NO, req, attachments);

        // M-1: the service forwards the picked files to the shared EXPT_RESOL_FILE writer.
        verify(approvalAttachmentWriter).persist(BIZ_KEY, "200", attachments);

        ArgumentCaptor<OvrsPymnSumr> captor = ArgumentCaptor.forClass(OvrsPymnSumr.class);
        verify(sumrRepository).save(captor.capture());
        OvrsPymnSumr saved = captor.getValue();

        assertThat(saved.getOutBankCd())
                .as("OUT_BANK_CD = NAPAS-converted bank code")
                .isEqualTo("970424");
        assertThat(saved.getWdrlActNo())
                .as("WDRL_ACT_NO = request acct_no")
                .isEqualTo("007654321");
        assertThat(saved.getWdrlCcyCd())
                .as("WDRL_CCY_CD = request ccy_cd")
                .isEqualTo("USD");

        verify(outBankCdNormalizer).normalize("11704088");

        // Existing behaviour preserved.
        assertThat(saved.getApprSeqNo()).isEqualTo("200");
        assertThat(saved.getTitl()).isEqualTo("Overseas Title");
        assertThat(resp.apprSeqNo()).isEqualTo("200");

        // Existing approval rows still created — capture EXPT_RESOL_MSTR.
        ArgumentCaptor<ExptResolMstr> mstrCaptor = ArgumentCaptor.forClass(ExptResolMstr.class);
        verify(exptResolMstrRepository).save(mstrCaptor.capture());
        ExptResolMstr mstr = mstrCaptor.getValue();
        verify(exptResolApprvrInfmRepository).save(any());

        // L-1: DOC_NM = verbatim legacy apprDv.getLabel().
        assertThat(mstr.getDocNm())
                .as("DOC_NM = legacy ApprDv label")
                .isEqualTo("Overseas Payment (WeCMs)");

        // M-2: display flags sourced from the env resolver (not hardcoded).
        assertThat(mstr.getApprBoxUseYn()).isEqualTo("Y");
        assertThat(mstr.getApprDatDispYn()).isEqualTo("N");
        assertThat(mstr.getCmpnLogoDispYn()).isEqualTo("Y");
        assertThat(mstr.getRegDispYn()).isEqualTo("N");
        verify(displayFlagsResolver).resolve(BIZ_KEY);
    }

    @Test
    void requestApproval_publishesPendingForHaengsungOracleWriteBack() {
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(BIZ_KEY);
        sumr.setOvrsPymnNo(OVRS_PYMN_NO);
        sumr.setCcyCd("USD");
        sumr.setTotalTranAmt(new BigDecimal("2500"));

        when(sumrRepository.findByBizKeyAndOvrsPymnNo(anyString(), anyString()))
                .thenReturn(Optional.of(sumr));
        when(legacyFunctions.nextApprSeqNo(anyString())).thenReturn("200");
        when(docNoGenerator.nextDocNo(anyString())).thenReturn("VO_260613_002");
        when(busiInfmRepository.findById(anyString())).thenReturn(Optional.empty());
        when(outBankCdNormalizer.normalize("11704088")).thenReturn("970424");
        when(displayFlagsResolver.resolve(anyString()))
                .thenReturn(new ApprovalDisplayFlagsResolver.DisplayFlags("Y", "N", "Y", "N"));

        RequestApprovalRequest req = new RequestApprovalRequest(
                "Overseas Title", "note", "11704088", "007654321", "USD", "20260613",
                null, 7L,
                List.of(new RequestApprovalRequest.ApproverInput("u1", "User One")),
                List.of());

        service.requestApproval(OVRS_PYMN_NO, req, null);

        // Request Approval submitted (single AF1 path) → PENDING lifecycle signal →
        // HAENGSUNG Oracle TB_TRNS_MAST.LAST_STATUS='10'. apprSeqNo = "200".
        verify(eventPublisher).publishEvent(
                new PaymentApprovalStatusChangedEvent(BIZ_KEY, "200", PaymentApprovalStatus.PENDING));
    }

    /* =================================================================
     *  removeApprovalLine — withdraw (audit fix, expt_resol_detl_fk01)
     * ================================================================= */

    /**
     * Withdrawing a pending overseas approval (which carries EXPT_RESOL_DETL
     * rows) must route the full approval-graph delete through the shared
     * {@code purgeApprovalGraph} helper (child-first, EXPT_RESOL_DETL before
     * EXPT_RESOL_MSTR — no FK error) and clear {@code OVRS_PYMN_SUMR.APPR_SEQ_NO}.
     */
    @Test
    void removeApprovalLine_pending_purgesGraphAndClearsSummary() {
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(BIZ_KEY);
        sumr.setOvrsPymnNo(OVRS_PYMN_NO);
        sumr.setApprSeqNo("200");   // submitted → pending
        // trnfYn null → not yet transferred

        com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr mstr =
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr();
        mstr.setBizKey(BIZ_KEY);
        mstr.setApprSeqNo("200");
        mstr.setApprTypeCd("00");   // Pending → deriveApprStts == 'P'

        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, OVRS_PYMN_NO))
                .thenReturn(Optional.of(sumr));
        when(exptResolMstrRepository.findById(
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(BIZ_KEY, "200")))
                .thenReturn(Optional.of(mstr));

        service.removeApprovalLine(OVRS_PYMN_NO);

        verify(approvalBoxFanoutService).purgeApprovalGraph(BIZ_KEY, "200", mstr);
        assertThat(sumr.getApprSeqNo()).isNull();
        verify(sumrRepository).save(sumr);
    }

    /** A transferred / approved overseas batch cannot be withdrawn — no
     *  approval-graph delete happens. */
    @Test
    void removeApprovalLine_nonPending_rejectedNoGraphTouched() {
        OvrsPymnSumr sumr = new OvrsPymnSumr();
        sumr.setBizKey(BIZ_KEY);
        sumr.setOvrsPymnNo(OVRS_PYMN_NO);
        sumr.setApprSeqNo("200");

        com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr approved =
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr();
        approved.setBizKey(BIZ_KEY);
        approved.setApprSeqNo("200");
        approved.setApprTypeCd("01");   // Approved → deriveApprStts == 'C'

        when(sumrRepository.findByBizKeyAndOvrsPymnNo(BIZ_KEY, OVRS_PYMN_NO))
                .thenReturn(Optional.of(sumr));
        when(exptResolMstrRepository.findById(
                new com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstrId(BIZ_KEY, "200")))
                .thenReturn(Optional.of(approved));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.removeApprovalLine(OVRS_PYMN_NO))
                .isInstanceOf(com.kosign.wecms.common.exception.BusinessException.class);

        verify(approvalBoxFanoutService, org.mockito.Mockito.never())
                .purgeApprovalGraph(any(), any(), any());
    }
}
