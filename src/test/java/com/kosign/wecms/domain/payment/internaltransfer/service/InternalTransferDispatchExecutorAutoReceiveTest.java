package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the auto-receive wiring on {@link InternalTransferDispatchExecutor}:
 * the non-VND File path registers the 00301 hand-off; the VND/API path does not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalTransferDispatchExecutorAutoReceiveTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "0000001";

    @Mock TbPayTargetRepository tbPayTargetRepository;
    @Mock TbPayFileMstRepository tbPayFileMstRepository;
    @Mock TbPayFileDataRepository tbPayFileDataRepository;
    @Mock AcctMstrRepository acctMstrRepository;
    @Mock DispatchKeyGenerator keyGenerator;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock DispatchAutoReceiveRegistrar autoReceiveRegistrar;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC);

    private InternalTransferDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new InternalTransferDispatchExecutor(
                tbPayTargetRepository, tbPayFileMstRepository, tbPayFileDataRepository,
                acctMstrRepository, keyGenerator, clock, legacyFunctions, autoReceiveRegistrar);

        when(tbPayFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbPayFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbPayTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(keyGenerator.nextFileSeqNo(anyString(), anyString())).thenReturn(FILE_SEQ_NO);
        when(keyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(legacyFunctions.nextTbPayFileDataSeq(anyString(), anyString())).thenReturn("1");
    }

    private ScdulPymnSumr master() {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ_KEY);
        m.setScdulPymnNo("SP-TEST-1");
        m.setOutBankCd("970436");      // neutral bank (not Woori/Shinhan) → no special routing
        m.setWdrlActNo("700005285299");
        m.setWdrlCcyCd("USD");
        return m;
    }

    private List<ScdulPymnSumrDetl> lines() {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setTarBankCd("970436");
        d.setFincMdaNo("0681000001");
        d.setTranAmt(new BigDecimal("10.00"));
        d.setRcptNm("BENEFICIARY");
        d.setTrnfCcyCd("USD");
        d.setDepoCcyCd("USD");
        d.setWdrlActRmrk("memo");
        return List.of(d);
    }

    @Test
    void nonVndFilePath_registersAutoReceive() {
        executor.execute(master(), lines(), "USD");

        verify(tbPayFileMstRepository).save(any());                 // file path taken
        verify(tbPayTargetRepository, never()).save(any());          // not the API path
        verify(autoReceiveRegistrar).registerAfterCommit(anyString(), eq(BIZ_KEY), eq(FILE_SEQ_NO));
    }

    @Test
    void vndApiPath_doesNotRegisterAutoReceive() {
        executor.execute(master(), lines(), "VND");

        verify(tbPayTargetRepository).save(any());                   // API path taken
        verify(tbPayFileMstRepository, never()).save(any());         // not the file path
        verify(autoReceiveRegistrar, never()).registerAfterCommit(anyString(), anyString(), anyString());
    }
}
