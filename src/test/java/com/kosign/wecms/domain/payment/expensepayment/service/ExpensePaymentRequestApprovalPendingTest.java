package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatus;
import com.kosign.wecms.domain.payment.approvalbox.event.PaymentApprovalStatusChangedEvent;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolApprvrInfmRepository;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.approvalbox.service.ApprovalAttachmentWriter;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HAENGSUNG Oracle write-back (Step 4): the Expense Payment Request Approval
 * flow must publish a {@code PENDING} {@link PaymentApprovalStatusChangedEvent}
 * so the HAENGSUNG listener writes {@code TB_TRNS_MAST.LAST_STATUS='10'} (legacy
 * {@code comm_0192_001_c005} → {@code ErpStatusUpdateHelper} PENDING). Mirrors the
 * Vendor / Payroll request-approval tests (shared scheduled-module path).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpensePaymentRequestApprovalPendingTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String SCDUL_PYMN_NO = "SP-E1";

    @Mock private ScdulPymnSumrRepository sumrRepository;
    @Mock private LegacyFunctionRepository legacyFunctions;
    @Mock private Clock clock;
    @Mock private DocNoGenerator docNoGenerator;
    @Mock private BusiInfmRepository busiInfmRepository;
    @Mock private OutBankCdNormalizer outBankCdNormalizer;
    @Mock private ApprovalDisplayFlagsResolver displayFlagsResolver;
    @Mock private ExptResolMstrRepository exptResolMstrRepository;
    @Mock private ExptResolApprvrInfmRepository exptResolApprvrInfmRepository;
    @Mock private ApprovalBoxFanoutService approvalBoxFanoutService;
    @Mock private ApprovalNotificationService approvalNotificationService;
    @Mock private AuditLogService auditLogService;
    @Mock private ApprovalAttachmentWriter approvalAttachmentWriter;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ExpensePaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "Tester", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestApproval_publishesPendingForHaengsungOracleWriteBack() {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(BIZ_KEY);
        master.setScdulPymnNo(SCDUL_PYMN_NO);
        master.setCcyCd("VND");
        master.setTotalTranAmt(new BigDecimal("3000000"));
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(master));
        when(legacyFunctions.nextApprSeqNo(anyString())).thenReturn("500");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-13T03:00:00Z"));
        when(docNoGenerator.nextDocNo(anyString())).thenReturn("VO_260613_005");
        when(busiInfmRepository.findById(anyString())).thenReturn(Optional.empty());
        when(displayFlagsResolver.resolve(anyString()))
                .thenReturn(new ApprovalDisplayFlagsResolver.DisplayFlags("N", "N", "N", "N"));

        RequestApprovalRequest req = new RequestApprovalRequest(
                "Expense Title", "note", "11704020", "001234567", "VND", "20260613",
                null, 5L,
                List.of(new RequestApprovalRequest.ApproverInput("u1", "User One")),
                List.of());

        var resp = service.requestApproval(SCDUL_PYMN_NO, req, null);

        assertThat(resp.apprSeqNo()).isEqualTo("500");
        // Request Approval submitted → PENDING → TB_TRNS_MAST.LAST_STATUS='10'.
        verify(eventPublisher).publishEvent(
                new PaymentApprovalStatusChangedEvent(BIZ_KEY, "500", PaymentApprovalStatus.PENDING));
    }
}
