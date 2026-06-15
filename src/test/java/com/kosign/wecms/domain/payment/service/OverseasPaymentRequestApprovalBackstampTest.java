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
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
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
}
