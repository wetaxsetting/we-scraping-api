package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.auditlog.service.AuditLogService;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.payment.approvalbox.entity.ExptResolMstr;
import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.dto.DispatchRequest;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
import com.kosign.wecms.domain.payment.internaltransfer.service.CutoffValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G1 — Expense Payment dispatch approval-status gate.
 *
 * <p>Legacy {@code PymnHelper.pymn_0001_001_c001:2146} blocks dispatch unless
 * {@code EXPT_RESOL_MSTR.APPR_TYPE_CD = '01'} (fully approved). These tests
 * pin the refactor parity fix in {@link ExpensePaymentService#dispatch}: a
 * batch with an {@code APPR_SEQ_NO} but a non-approved status must NOT reach
 * the dispatch executor.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpensePaymentDispatchApprovalGateTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String ID  = "0000000001";
    private static final String SEQ = "APPRSEQ0001";

    @Mock private ScdulPymnSumrRepository     sumrRepository;
    @Mock private ScdulPymnSumrDetlRepository detlRepository;
    @Mock private CutoffValidator             cutoffValidator;
    @Mock private ExpenseDispatchExecutor     dispatchExecutor;
    @Mock private AuditLogService             auditLogService;
    @Mock private ExptResolMstrRepository     exptResolMstrRepository;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks private ExpensePaymentService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "tester", BIZ,
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ScdulPymnSumr master(String apprSeqNo) {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ);
        m.setScdulPymnNo(ID);
        m.setScdulPymnTypeCd("02");
        m.setTrnfYn("N");
        m.setApprSeqNo(apprSeqNo);
        m.setOutBankCd("970424");
        m.setWdrlActNo("1001");
        m.setWdrlCcyCd("VND");
        return m;
    }

    private void stubMasterLookup(ScdulPymnSumr m) {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(BIZ, ID, "02"))
                .thenReturn(Optional.of(m));
    }

    private ExptResolMstr approvalWithType(String apprTypeCd) {
        ExptResolMstr e = new ExptResolMstr();
        e.setApprTypeCd(apprTypeCd);
        return e;
    }

    @Test
    void noApprovalSeq_isBlocked() {
        stubMasterLookup(master(null));

        assertThatThrownBy(() -> service.dispatch(ID, new DispatchRequest("VND")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2063");

        verify(dispatchExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void pendingApproval_isBlocked() {
        stubMasterLookup(master(SEQ));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(BIZ, List.of(SEQ)))
                .thenReturn(List.of(approvalWithType("00")));   // 00 = pending

        assertThatThrownBy(() -> service.dispatch(ID, new DispatchRequest("VND")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2063");

        verify(dispatchExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void rejectedApproval_isBlocked() {
        stubMasterLookup(master(SEQ));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(BIZ, List.of(SEQ)))
                .thenReturn(List.of(approvalWithType("02")));   // 02 = rejected

        assertThatThrownBy(() -> service.dispatch(ID, new DispatchRequest("VND")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2063");

        verify(dispatchExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void inProgressApproval_isBlocked() {
        stubMasterLookup(master(SEQ));
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(BIZ, List.of(SEQ)))
                .thenReturn(List.of(approvalWithType("03")));   // 03 = in progress

        assertThatThrownBy(() -> service.dispatch(ID, new DispatchRequest("VND")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2063");

        verify(dispatchExecutor, never()).execute(any(), any(), any());
    }

    @Test
    void approvedApproval_isAllowed() {
        ScdulPymnSumr m = master(SEQ);
        stubMasterLookup(m);
        when(exptResolMstrRepository.findByBizKeyAndApprSeqNoIn(BIZ, List.of(SEQ)))
                .thenReturn(List.of(approvalWithType("01")));   // 01 = approved
        when(detlRepository.findByBizKeyAndScdulPymnNo(eq(BIZ), eq(ID), any()))
                .thenReturn(Collections.emptyList());
        when(sumrRepository.saveAndFlush(any(ScdulPymnSumr.class))).thenReturn(m);

        service.dispatch(ID, new DispatchRequest("VND"));

        verify(dispatchExecutor).execute(eq(m), anyList(), eq("VND"));
        verify(cutoffValidator).validate(eq("970424"), any());
        assertThat(m.getTrnfYn()).isEqualTo("Y");
    }
}
