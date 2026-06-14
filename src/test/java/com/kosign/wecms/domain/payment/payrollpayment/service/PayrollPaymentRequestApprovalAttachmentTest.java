package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.repository.BusiInfmRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

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
 * M-1 (legacy parity): the Payroll Request Approval flow forwards the picked
 * attachments to the shared {@link ApprovalAttachmentWriter}. Payroll carries
 * the extra {@code appr_fund_tp} field but shares the scheduled-module persist
 * wiring with Vendor / Expense.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollPaymentRequestApprovalAttachmentTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String SCDUL_PYMN_NO = "SP-P1";

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

    @InjectMocks private PayrollPaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "Tester", BIZ_KEY, LangCd.EN);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requestApproval_forwardsAttachmentsToSharedWriter() {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(BIZ_KEY);
        master.setScdulPymnNo(SCDUL_PYMN_NO);
        master.setCcyCd("VND");
        master.setTotalTranAmt(new BigDecimal("9000000"));
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(master));
        when(legacyFunctions.nextApprSeqNo(anyString())).thenReturn("400");
        when(clock.instant()).thenReturn(Instant.parse("2026-06-13T03:00:00Z"));
        when(docNoGenerator.nextDocNo(anyString())).thenReturn("VO_260613_004");
        when(busiInfmRepository.findById(anyString())).thenReturn(Optional.empty());
        when(displayFlagsResolver.resolve(anyString()))
                .thenReturn(new ApprovalDisplayFlagsResolver.DisplayFlags("N", "N", "N", "N"));

        // appr_fund_tp = "0" (Salary) — Payroll-specific field.
        RequestApprovalRequest req = new RequestApprovalRequest(
                "Payroll Title", "note", "11704020", "001234567", "VND", "20260613",
                "0", 5L,
                List.of(new RequestApprovalRequest.ApproverInput("u1", "User One")),
                List.of());

        List<MultipartFile> attachments = List.of(
                new MockMultipartFile("attachments", "payroll.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {9}));

        var resp = service.requestApproval(SCDUL_PYMN_NO, req, attachments);

        assertThat(resp.apprSeqNo()).isEqualTo("400");
        verify(approvalAttachmentWriter).persist(BIZ_KEY, "400", attachments);
    }
}
