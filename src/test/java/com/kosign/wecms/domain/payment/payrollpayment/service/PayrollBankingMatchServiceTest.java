package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3 — {@link PayrollBankingMatchService} master-level manual candidate
 * transfer-time parity (legacy {@code comm_0197_001_r001}
 * {@code putBankingCandidateIdoInSumr}). The batch {@code TRNF_DTM} (UTC) is
 * converted to VN-local {@code HHmmss} and passed as the query
 * {@code transferTime} floor; null when the batch is not yet transferred.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollBankingMatchServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String TYPE_PAYROLL = "00";
    private static final String PYMN_NO = "PR2507001";
    private static final String ACCT_NO = "9988776655";
    private static final BigDecimal AMT = new BigDecimal("50000000");

    @Mock ScdulPymnSumrRepository sumrRepository;
    @Mock AcctTrnsHstrRepository txnRepository;
    @Mock BusiMatchLogRepository matchLogRepository;
    @Mock LegacyFunctionRepository legacyFunctions;

    @InjectMocks PayrollBankingMatchService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("u1", BIZ, LangCd.EN);
        when(txnRepository.findPayrollMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"), eq(AMT),
                any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listCandidates_passesTransferTimeFloor_convertedUtcToVnLocal() {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(BIZ, PYMN_NO, TYPE_PAYROLL))
                .thenReturn(Optional.of(batch("20260708030000")));   // 03:00 UTC → 10:00 VN

        service.listCandidates(PYMN_NO, null);

        ArgumentCaptor<String> time = ArgumentCaptor.forClass(String.class);
        verify(txnRepository).findPayrollMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"),
                eq(AMT), time.capture(), any());
        assertThat(time.getValue()).isEqualTo("100000");
    }

    @Test
    void listCandidates_passesNullTransferTime_whenBatchNotTransferred() {
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(BIZ, PYMN_NO, TYPE_PAYROLL))
                .thenReturn(Optional.of(batch(null)));

        service.listCandidates(PYMN_NO, null);

        ArgumentCaptor<String> time = ArgumentCaptor.forClass(String.class);
        verify(txnRepository).findPayrollMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"),
                eq(AMT), time.capture(), any());
        assertThat(time.getValue()).isNull();
    }

    @Test
    void listCandidates_throwsWhenBatchAlreadyMatched() {
        ScdulPymnSumr matched = batch("20260708030000");
        matched.setLinkMatchYn("Y");
        when(sumrRepository.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(BIZ, PYMN_NO, TYPE_PAYROLL))
                .thenReturn(Optional.of(matched));
        assertThatThrownBy(() -> service.listCandidates(PYMN_NO, null))
                .isInstanceOf(BusinessException.class);
    }

    private static ScdulPymnSumr batch(String trnfDtm) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ);
        s.setScdulPymnNo(PYMN_NO);
        s.setScdulPymnTypeCd(TYPE_PAYROLL);
        s.setWdrlActNo(ACCT_NO);
        s.setTotalTranAmt(AMT);
        s.setPayTranDt("20260708");
        s.setTrnfDtm(trnfDtm);
        s.setLinkMatchYn("N");
        return s;
    }
}
