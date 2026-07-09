package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchRegistrar;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the dispatch hand-off wiring on {@link InternalTransferDispatchExecutor}:
 * the VND/API path registers the real-time 202 hand-off (once, with every
 * {@code TRAN_SEQ}); the non-VND File path registers only the 00301 auto-receive
 * hand-off. The two paths never both register.
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
    @Mock TbFirmBankCodeRepository tbFirmBankCodeRepository;
    @Mock TbFirmConfRepository tbFirmConfRepository;
    @Mock DispatchKeyGenerator keyGenerator;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock DispatchAutoReceiveRegistrar autoReceiveRegistrar;
    @Mock RealtimeDispatchRegistrar realtimeDispatchRegistrar;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-12T03:00:00Z"), ZoneOffset.UTC);

    private InternalTransferDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new InternalTransferDispatchExecutor(
                tbPayTargetRepository, tbPayFileMstRepository, tbPayFileDataRepository,
                acctMstrRepository, tbFirmBankCodeRepository, tbFirmConfRepository,
                keyGenerator, clock, legacyFunctions,
                autoReceiveRegistrar, realtimeDispatchRegistrar);

        TbFirmConf defaultConf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(defaultConf.getBankPrtnId()).thenReturn("PRTN-DEFAULT");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(defaultConf));

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
        m.setOutBankCd("970436");      // neutral bank (not Woori/Shinhan) → no special routing
        m.setWdrlActNo("700005285299");
        m.setWdrlCcyCd("USD");
        return m;
    }

    private List<ScdulPymnSumrDetl> lines() {
        ScdulPymnSumrDetl a = detail("0681000001");
        ScdulPymnSumrDetl b = detail("0681000002");
        return List.of(a, b);
    }

    private ScdulPymnSumrDetl detail(String acctNo) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setTarBankCd("970436");
        d.setFincMdaNo(acctNo);
        d.setTranAmt(new BigDecimal("10.00"));
        d.setRcptNm("BENEFICIARY");
        d.setTrnfCcyCd("USD");
        d.setDepoCcyCd("USD");
        d.setWdrlActRmrk("memo");
        return d;
    }

    @Test
    void nonVndFilePath_registersAutoReceiveOnly() {
        executor.execute(master(), lines(), "USD");

        verify(tbPayFileMstRepository).save(any());                 // file path taken
        verify(tbPayTargetRepository, never()).save(any());          // not the API path
        verify(autoReceiveRegistrar).registerAfterCommit(anyString(), eq(BIZ_KEY), eq(FILE_SEQ_NO));
        verify(realtimeDispatchRegistrar, never()).registerAfterCommit(anyString(), anyString(), anyList());
    }

    @Test
    void vndApiPath_registersRealtimeOnce_withAllTranSeqs() {
        executor.execute(master(), lines(), "VND");

        verify(tbPayTargetRepository, times(2)).save(any());         // API path, one row per line
        verify(tbPayFileMstRepository, never()).save(any());         // not the file path
        // Exactly one realtime registration carrying every TRAN_SEQ — never once per line.
        verify(realtimeDispatchRegistrar, times(1))
                .registerAfterCommit(eq(BIZ_KEY), anyString(), eq(List.of("1", "2")));
        verify(autoReceiveRegistrar, never()).registerAfterCommit(anyString(), anyString(), anyString());
    }
}
