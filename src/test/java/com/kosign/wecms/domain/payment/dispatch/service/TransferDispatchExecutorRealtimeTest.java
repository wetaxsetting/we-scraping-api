package com.kosign.wecms.domain.payment.dispatch.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the C2 real-time wiring on {@link TransferDispatchExecutor} (the shared
 * Vendor/Expense executor): the VND/API path hands the just-written {@code TRAN_SEQ}s
 * to {@link RealtimeDispatchRegistrar}; the non-VND File path does not (and still
 * hands off to {@link DispatchAutoReceiveRegistrar}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TransferDispatchExecutorRealtimeTest {

    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "0000001";

    @Mock TbPayTargetRepository tbPayTargetRepository;
    @Mock TbPayFileMstRepository tbPayFileMstRepository;
    @Mock TbPayFileDataRepository tbPayFileDataRepository;
    @Mock AcctMstrRepository acctMstrRepository;
    @Mock DispatchKeyGenerator keyGenerator;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock DispatchAutoReceiveRegistrar autoReceiveRegistrar;
    @Mock RealtimeDispatchRegistrar realtimeDispatchRegistrar;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-13T03:00:00Z"), ZoneOffset.UTC);

    private TransferDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new TransferDispatchExecutor(
                tbPayTargetRepository, tbPayFileMstRepository, tbPayFileDataRepository,
                acctMstrRepository, keyGenerator, clock, legacyFunctions,
                autoReceiveRegistrar, realtimeDispatchRegistrar);

        when(tbPayFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbPayFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbPayTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(keyGenerator.nextFileSeqNo(anyString(), anyString())).thenReturn(FILE_SEQ_NO);
        when(keyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1", "2");
        when(legacyFunctions.nextTbPayFileDataSeq(anyString(), anyString())).thenReturn("1");
    }

    private ScdulPymnSumr master() {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ_KEY);
        m.setScdulPymnNo("SP-TEST-1");
        m.setOutBankCd("970436");
        m.setWdrlActNo("700005285299");
        m.setWdrlCcyCd("VND");
        return m;
    }

    private List<ScdulPymnSumrDetl> twoLines() {
        ScdulPymnSumrDetl a = new ScdulPymnSumrDetl();
        a.setTarBankCd("970436");
        a.setFincMdaNo("0681000001");
        a.setTranAmt(new BigDecimal("10.00"));
        a.setRcptNm("BENEFICIARY A");
        a.setTrnfCcyCd("VND");
        a.setDepoCcyCd("VND");
        a.setWdrlActRmrk("memo");
        ScdulPymnSumrDetl b = new ScdulPymnSumrDetl();
        b.setTarBankCd("970436");
        b.setFincMdaNo("0681000002");
        b.setTranAmt(new BigDecimal("20.00"));
        b.setRcptNm("BENEFICIARY B");
        b.setTrnfCcyCd("VND");
        b.setDepoCcyCd("VND");
        b.setWdrlActRmrk("memo");
        return List.of(a, b);
    }

    @Test
    void vndApiPath_registersRealtimeDispatchWithAllTranSeqs() {
        executor.execute(master(), twoLines(), "VND");

        verify(tbPayTargetRepository, org.mockito.Mockito.times(2)).save(any()); // API path taken
        verify(tbPayFileMstRepository, never()).save(any());                     // not the File path
        verify(realtimeDispatchRegistrar).registerAfterCommit(eq(BIZ_KEY), anyString(), eq(List.of("1", "2")));
        verify(autoReceiveRegistrar, never()).registerAfterCommit(anyString(), anyString(), anyString());
    }

    @Test
    void nonVndFilePath_registersAutoReceive_notRealtime() {
        executor.execute(master(), twoLines(), "USD");

        verify(tbPayFileMstRepository).save(any());                              // File path taken
        verify(tbPayTargetRepository, never()).save(any());                      // not the API path
        verify(autoReceiveRegistrar).registerAfterCommit(anyString(), eq(BIZ_KEY), eq(FILE_SEQ_NO));
        verify(realtimeDispatchRegistrar, never()).registerAfterCommit(anyString(), anyString(), anyList());
    }
}
