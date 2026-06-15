package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalBoxFanoutService;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalDisplayFlagsResolver;
import com.kosign.wecms.domain.payment.common.OutBankCdNormalizer;
import com.kosign.wecms.domain.payment.internaltransfer.dto.RequestApprovalRequest;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.notification.service.ApprovalNotificationService;
import com.kosign.wecms.domain.payment.service.DocNoGenerator;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * H-1 (legacy parity, comm_0192_001_c005_act.jsp:362-371
 * {@code updateScheduledPaymentSummary}): Request Approval must back-stamp the
 * withdrawal account selected in the popup onto {@code SCDUL_PYMN_SUMR} —
 * {@code OUT_BANK_CD} (NAPAS-converted), {@code WDRL_ACT_NO}, {@code WDRL_CCY_CD}
 * — alongside the existing {@code APPR_SEQ_NO} + {@code TITL} stamp.
 *
 * <p>Internal Transfer is the representative {@code ScdulPymnSumr} path; Vendor /
 * Expense / Payroll share the identical {@code stampApproval} block.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalTransferRequestApprovalBackstampTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String USER_ID = "tester";
    private static final String SCDUL_PYMN_NO = "SP-1";

    @Mock private ScdulPymnSumrRepository sumrRepository;
    @Mock private LegacyFunctionRepository legacyFunctions;
    @Mock private Clock clock;
    @Mock private DocNoGenerator docNoGenerator;
    @Mock private BusiInfmRepository busiInfmRepository;
    @Mock private OutBankCdNormalizer outBankCdNormalizer;
    @Mock private ExptResolMstrRepository exptResolMstrRepository;
    @Mock private ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    @Mock private ApprovalBoxFanoutService approvalBoxFanoutService;
    @Mock private ApprovalNotificationService approvalNotificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private ApprovalDisplayFlagsResolver displayFlagsResolver;

    @InjectMocks private InternalTransferService service;

    @BeforeEach
    void setUp() {
        TenantContext.set(USER_ID, "Tester", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestApproval_backstampsWithdrawalAccountOntoScdulPymnSumr() {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(BIZ_KEY);
        master.setScdulPymnNo(SCDUL_PYMN_NO);
        master.setCcyCd("VND");
        master.setTotalTranAmt(new BigDecimal("1500000"));
        // apprSeqNo intentionally null — un-submitted draft.

        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(master));
        when(legacyFunctions.nextApprSeqNo(anyString())).thenReturn("100");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-13T03:00:00Z"));
        when(docNoGenerator.nextDocNo(anyString())).thenReturn("VO_260613_001");
        when(busiInfmRepository.findById(anyString())).thenReturn(Optional.empty());
        // Legacy NAPAS conversion: internal BANK_CD 11704020 → NAPAS 970457 (Woori).
        when(outBankCdNormalizer.normalize("11704020")).thenReturn("970457");
        // M-2: display flags sourced from BUSI_ENV_SET_INFM (resolver mocked).
        when(displayFlagsResolver.resolve(anyString()))
                .thenReturn(new ApprovalDisplayFlagsResolver.DisplayFlags("Y", "N", "Y", "N"));

        RequestApprovalRequest req = new RequestApprovalRequest(
                "My Title",
                "some note",
                "11704020",          // bank_cd (internal code → normalized to NAPAS)
                "001234567",         // acct_no
                "VND",               // ccy_cd
                "20260613",          // pay_date
                null,                // appr_fund_tp (payroll-only)
                5L,                  // appr_line_no
                List.of(new RequestApprovalRequest.ApproverInput("u1", "User One")),
                List.of());

        var resp = service.requestApproval(SCDUL_PYMN_NO, req, null);

        // Capture the row actually persisted to SCDUL_PYMN_SUMR.
        ArgumentCaptor<ScdulPymnSumr> captor = ArgumentCaptor.forClass(ScdulPymnSumr.class);
        verify(sumrRepository).saveAndFlush(captor.capture());
        ScdulPymnSumr saved = captor.getValue();

        // H-1: the three withdrawal columns are persisted.
        assertThat(saved.getOutBankCd())
                .as("OUT_BANK_CD = NAPAS-converted bank code")
                .isEqualTo("970457");
        assertThat(saved.getWdrlActNo())
                .as("WDRL_ACT_NO = request acct_no")
                .isEqualTo("001234567");
        assertThat(saved.getWdrlCcyCd())
                .as("WDRL_CCY_CD = request ccy_cd")
                .isEqualTo("VND");

        // NAPAS conversion was actually exercised (not the raw input).
        verify(outBankCdNormalizer).normalize("11704020");

        // Existing behaviour preserved: APPR_SEQ_NO + TITL still stamped.
        assertThat(saved.getApprSeqNo()).isEqualTo("100");
        assertThat(saved.getTitl()).isEqualTo("My Title");
        assertThat(resp.apprSeqNo()).isEqualTo("100");

        // Existing approval rows still created — capture EXPT_RESOL_MSTR.
        ArgumentCaptor<ExptResolMstr> mstrCaptor = ArgumentCaptor.forClass(ExptResolMstr.class);
        verify(exptResolMstrRepository).save(mstrCaptor.capture());
        ExptResolMstr mstr = mstrCaptor.getValue();
        verify(exptResolApprvrInfmRepository).save(any());
        verify(approvalBoxFanoutService).writeResolutionDetail(anyString(), anyString(), any(), any());

        // L-1: DOC_NM = verbatim legacy apprDv.getLabel().
        assertThat(mstr.getDocNm())
                .as("DOC_NM = legacy ApprDv label")
                .isEqualTo("Internal Transfer (WeCMS)");

        // M-2: display flags sourced from the env resolver (not hardcoded).
        assertThat(mstr.getApprBoxUseYn()).isEqualTo("Y");
        assertThat(mstr.getApprDatDispYn()).isEqualTo("N");
        assertThat(mstr.getCmpnLogoDispYn()).isEqualTo("Y");
        assertThat(mstr.getRegDispYn()).isEqualTo("N");
        verify(displayFlagsResolver).resolve(BIZ_KEY);
    }
}
