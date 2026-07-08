package com.kosign.wecms.domain.payment.match.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.repository.AcctTrnsHstrRepository;
import com.kosign.wecms.domain.banking.repository.BusiMatchLogRepository;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.internaltransfer.repository.ScdulPymnSumrDetlRepository;
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
 * P3 — {@link DomesticPaymentLineMatchService} manual candidate transfer-time
 * parity (legacy {@code comm_0197_001_r001} {@code putBankingCandidateIdoIn}).
 * Verifies the service converts the batch {@code TRNF_DTM} (UTC) to VN-local
 * {@code HHmmss} and passes it as the query's {@code transferTime} floor —
 * null when the batch is not yet transferred (filter skipped).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DomesticPaymentLineMatchServiceTest {

    private static final String BIZ = "VN_VNM0004_000001";
    private static final String TYPE_VENDOR = "01";
    private static final String PYMN_NO = "SP2507001";
    private static final String DETL_NO = "1";
    private static final String ACCT_NO = "9988776655";
    private static final BigDecimal AMT = new BigDecimal("1500.00");

    @Mock AcctTrnsHstrRepository txnRepo;
    @Mock BusiMatchLogRepository matchLogRepo;
    @Mock ScdulPymnSumrRepository sumrRepo;
    @Mock ScdulPymnSumrDetlRepository detlRepo;
    @Mock LegacyFunctionRepository legacyFunctions;

    @InjectMocks DomesticPaymentLineMatchService service;

    @BeforeEach
    void setUp() {
        TenantContext.set("u1", BIZ, LangCd.EN);
        when(detlRepo.findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(BIZ, PYMN_NO, DETL_NO))
                .thenReturn(Optional.of(line("N")));
        when(txnRepo.findDomesticDetailMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"), eq(AMT),
                any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void listCandidates_passesTransferTimeFloor_convertedUtcToVnLocal() {
        when(sumrRepo.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(BIZ, PYMN_NO, TYPE_VENDOR))
                .thenReturn(Optional.of(batch("20260708030000")));   // 03:00 UTC → 10:00 VN

        service.listCandidates(TYPE_VENDOR, PYMN_NO, DETL_NO, null, null);

        ArgumentCaptor<String> time = ArgumentCaptor.forClass(String.class);
        verify(txnRepo).findDomesticDetailMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"),
                eq(AMT), time.capture(), any(), any());
        assertThat(time.getValue()).isEqualTo("100000");
    }

    @Test
    void listCandidates_passesNullTransferTime_whenBatchNotTransferred() {
        when(sumrRepo.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(BIZ, PYMN_NO, TYPE_VENDOR))
                .thenReturn(Optional.of(batch(null)));   // not transferred

        service.listCandidates(TYPE_VENDOR, PYMN_NO, DETL_NO, null, null);

        ArgumentCaptor<String> time = ArgumentCaptor.forClass(String.class);
        verify(txnRepo).findDomesticDetailMatchCandidates(eq(BIZ), eq(ACCT_NO), eq("20260708"),
                eq(AMT), time.capture(), any(), any());
        assertThat(time.getValue()).isNull();
    }

    @Test
    void listCandidates_throwsWhenLineAlreadyMatched() {
        when(sumrRepo.findByBizKeyAndScdulPymnNoAndScdulPymnTypeCd(BIZ, PYMN_NO, TYPE_VENDOR))
                .thenReturn(Optional.of(batch("20260708030000")));
        when(detlRepo.findByBizKeyAndScdulPymnNoAndScdulPymnDetlNo(BIZ, PYMN_NO, DETL_NO))
                .thenReturn(Optional.of(line("Y")));
        assertThatThrownBy(() -> service.listCandidates(TYPE_VENDOR, PYMN_NO, DETL_NO, null, null))
                .isInstanceOf(BusinessException.class);
    }

    private static ScdulPymnSumr batch(String trnfDtm) {
        ScdulPymnSumr s = new ScdulPymnSumr();
        s.setBizKey(BIZ);
        s.setScdulPymnNo(PYMN_NO);
        s.setScdulPymnTypeCd(TYPE_VENDOR);
        s.setWdrlActNo(ACCT_NO);
        s.setTrnfDtm(trnfDtm);
        return s;
    }

    private static ScdulPymnSumrDetl line(String linkMatchYn) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo(PYMN_NO);
        d.setScdulPymnDetlNo(DETL_NO);
        d.setPayTranDt("20260708");
        d.setTranAmt(AMT);
        d.setTranUsag("PAY ABC");
        d.setLinkMatchYn(linkMatchYn);
        return d;
    }
}
