package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Payroll is file-route only — proves it registers the 00301 hand-off. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollDispatchExecutorAutoReceiveTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "0000001";

    @Mock TbPayFileMstRepository tbPayFileMstRepository;
    @Mock TbPayFileDataRepository tbPayFileDataRepository;
    @Mock AcctMstrRepository acctMstrRepository;
    @Mock DispatchKeyGenerator keyGenerator;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock DispatchAutoReceiveRegistrar autoReceiveRegistrar;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC);

    private PayrollDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new PayrollDispatchExecutor(
                tbPayFileMstRepository, tbPayFileDataRepository, acctMstrRepository,
                keyGenerator, clock, legacyFunctions, autoReceiveRegistrar);
        when(tbPayFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbPayFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(keyGenerator.nextFileSeqNo(anyString(), anyString())).thenReturn(FILE_SEQ_NO);
        when(legacyFunctions.nextTbPayFileDataSeq(anyString(), anyString())).thenReturn("1");
    }

    @Test
    void filePath_registersAutoReceive() {
        ScdulPymnSumr master = new ScdulPymnSumr();
        master.setBizKey(BIZ_KEY);
        master.setScdulPymnNo("PR-TEST-1");
        master.setOutBankCd("970424");
        master.setWdrlActNo("700005285299");

        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setTarBankCd("970424");
        d.setFincMdaNo("0681000001");
        d.setTranAmt(new BigDecimal("10.00"));
        d.setRcptNm("EMPLOYEE");
        d.setTrnfCcyCd("USD");
        d.setWdrlActRmrk("salary");

        executor.execute(master, List.of(d), "USD", "0");

        verify(tbPayFileMstRepository).save(any());
        verify(autoReceiveRegistrar).registerAfterCommit(anyString(), eq(BIZ_KEY), eq(FILE_SEQ_NO));
    }
}
