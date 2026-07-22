package com.kosign.wecms.domain.payment.expensepayment.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.service.RealtimeDispatchRegistrar;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * G2 — Expense Payment USD/File-path dispatch routing parity with legacy
 * {@code PymnHelper.pymn_0001_001_c001:2233-2405}. Verifies per-bank
 * {@code TRSC_CCY_CD} / {@code ANT_DIV_CD} / {@code BANK_CHNL_DIV},
 * {@code BANK_PRTN_ID} from {@code TB_FIRM_CONF}, the {@code ENG_ABRVAT_NM}
 * same-group rule, the VCB USD->other-bank reject, and the
 * {@code TRAN_USAG}-sourced remark columns.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseDispatchExecutorTest {

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String SHINHAN  = "970424";
    private static final String WOORI    = "970457";
    private static final String VCB      = "970436";
    private static final String BIDV     = "970418";

    @Mock private TbPayTargetRepository        tbPayTargetRepository;
    @Mock private TbPayFileMstRepository       tbPayFileMstRepository;
    @Mock private TbPayFileDataRepository      tbPayFileDataRepository;
    @Mock private AcctMstrRepository           acctMstrRepository;
    @Mock private DispatchKeyGenerator         keyGenerator;
    @Mock private LegacyFunctionRepository     legacyFunctions;
    @Mock private DispatchAutoReceiveRegistrar autoReceiveRegistrar;
    @Mock private RealtimeDispatchRegistrar    realtimeDispatchRegistrar;
    @Mock private TbFirmConfRepository         tbFirmConfRepository;
    @Mock private TbFirmBankCodeRepository     tbFirmBankCodeRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks private ExpenseDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "tester", BIZ,
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        ReflectionTestUtils.setField(executor, "shinhanBankCd", SHINHAN);
        ReflectionTestUtils.setField(executor, "wooriBankCd", WOORI);
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        when(keyGenerator.nextFileSeqNo(any(), any())).thenReturn("1");
        when(legacyFunctions.nextTbPayFileDataSeq(any(), any())).thenReturn("1");
        // Default TB_FIRM_CONF present so File-path routing tests proceed; the
        // absent case is overridden in its own test (WBE2089 block).
        TbFirmConf defaultConf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(defaultConf.getBankPrtnId()).thenReturn("PRTN-DEFAULT");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(defaultConf));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ScdulPymnSumr master(String outBankCd, String wdrlCcyCd) {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ);
        m.setScdulPymnNo("0000000001");
        m.setScdulPymnTypeCd("02");
        m.setOutBankCd(outBankCd);
        m.setWdrlActNo("1001");
        m.setWdrlCcyCd(wdrlCcyCd);
        m.setTotTrnsCnt(BigDecimal.ONE);
        m.setTotalTranAmt(new BigDecimal("100"));
        return m;
    }

    private ScdulPymnSumrDetl line(String tarBankCd, String trnfCcyCd) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo("0000000001");
        d.setScdulPymnDetlNo("1");
        d.setTarBankCd(tarBankCd);
        d.setTrnfCcyCd(trnfCcyCd);
        d.setFincMdaNo("9999");
        d.setTranAmt(new BigDecimal("100"));
        d.setRcptNm("RECIPIENT");
        d.setTranUsag("INVOICE-123");
        d.setDepoCcyCd("USD");
        return d;
    }

    private TbPayFileData captureData() {
        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    void shinhanUsdSameBank_routesEbankCurrentBank_trscFromWithdrawalCcy() {
        executor.execute(master(SHINHAN, "USD"), List.of(line(SHINHAN, "USD")), "USD");

        TbPayFileData d = captureData();
        assertThat(d.getTrscCcyCd()).isEqualTo("USD");   // Shinhan -> WDRL_CCY_CD
        assertThat(d.getAntDivCd()).isEqualTo("E");      // EBANK
        assertThat(d.getBankChnlDiv()).isEqualTo("T");   // CURRENT_BANK_TRANSFER
        assertThat(d.getWdrlActRmrk()).isEqualTo("INVOICE-123");  // TRAN_USAG
        assertThat(d.getTarAcctRmrk()).isEqualTo("INVOICE-123");  // TRAN_USAG
        assertThat(d.getRemitCcyCd()).isEqualTo("USD");
    }

    @Test
    void wooriUsdOtherBank_routesVcbOtherBank_trscFromTransferCcy() {
        executor.execute(master(WOORI, "VND"), List.of(line(BIDV, "USD")), "USD");

        TbPayFileData d = captureData();
        assertThat(d.getTrscCcyCd()).isEqualTo("USD");   // Woori -> TRNF_CCY_CD
        assertThat(d.getAntDivCd()).isEqualTo("V");      // VCB
        assertThat(d.getBankChnlDiv()).isEqualTo("O");   // OTHER_BANK_TRANSFER
    }

    @Test
    void vcbUsdSameBank_nullAntDiv_currentBank() {
        executor.execute(master(VCB, "USD"), List.of(line(VCB, "USD")), "USD");

        TbPayFileData d = captureData();
        assertThat(d.getTrscCcyCd()).isEqualTo("USD");   // VCB -> TRNF_CCY_CD
        assertThat(d.getAntDivCd()).isNull();            // VCB USD same-bank -> null
        assertThat(d.getBankChnlDiv()).isEqualTo("T");
    }

    @Test
    void vcbVnd_otherBank_carriesUploadedAntDivCd_bankChnlDivOther() {
        // OQ-2 (CONFIRMED file-based VCB; legacy PymnHelper:2573 "VCB VND -> Other : NAPAS/CITAD from upload").
        ScdulPymnSumrDetl l = line(BIDV, "VND");
        l.setAntDivCd("NAPAS");
        executor.execute(master(VCB, "VND"), List.of(l), "VND");

        TbPayFileData d = captureData();
        assertThat(d.getAntDivCd()).isEqualTo("NAPAS");
        assertThat(d.getBankChnlDiv()).isEqualTo("O");
        assertThat(d.getTrscCcyCd()).isEqualTo("VND");
    }

    @Test
    void vcbVnd_sameBank_carriesUploadedAntDivCd_bankChnlDivCurrent() {
        ScdulPymnSumrDetl l = line(VCB, "VND");
        l.setAntDivCd("CITAD");
        executor.execute(master(VCB, "VND"), List.of(l), "VND");

        TbPayFileData d = captureData();
        assertThat(d.getAntDivCd()).isEqualTo("CITAD");
        assertThat(d.getBankChnlDiv()).isEqualTo("T");
    }

    @Test
    void vcbUsdOtherBank_isRejected() {
        assertThatThrownBy(() ->
                executor.execute(master(VCB, "USD"), List.of(line(BIDV, "USD")), "USD"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2088");

        verify(tbPayFileDataRepository, never()).save(any());
    }

    @Test
    void engAbrvatNmGroupMatch_treatedAsSameBank() {
        String shinhanBranch = "970425";
        TbFirmBankCode out = org.mockito.Mockito.mock(TbFirmBankCode.class);
        TbFirmBankCode tar = org.mockito.Mockito.mock(TbFirmBankCode.class);
        when(out.getEngAbrvatNm()).thenReturn("SHB");
        when(tar.getEngAbrvatNm()).thenReturn("SHB");
        when(tbFirmBankCodeRepository.findFirstByBankCdAndBankStas(SHINHAN, "1"))
                .thenReturn(Optional.of(out));
        when(tbFirmBankCodeRepository.findFirstByBankCdAndBankStas(shinhanBranch, "1"))
                .thenReturn(Optional.of(tar));

        executor.execute(master(SHINHAN, "USD"), List.of(line(shinhanBranch, "USD")), "USD");

        assertThat(captureData().getBankChnlDiv()).isEqualTo("T");  // same group via ENG_ABRVAT_NM
    }

    @Test
    void bankPrtnId_readFromTbFirmConf_whenPresent() {
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(conf));

        executor.execute(master(WOORI, "USD"), List.of(line(WOORI, "USD")), "USD");

        ArgumentCaptor<TbPayFileMst> c = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(tbPayFileMstRepository).save(c.capture());
        assertThat(c.getValue().getBankPrtnId()).isEqualTo("PRTN1");
    }

    @Test
    void dispatch_isBlocked_whenFirmConfAbsent() {
        // Legacy pymn_0001_001_c001:2242-2256 NPE-rolls-back when TB_FIRM_CONF
        // is missing; refactor blocks with WBE2089 (same effect, clean code).
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                executor.execute(master(WOORI, "USD"), List.of(line(WOORI, "USD")), "USD"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2089");

        verify(tbPayFileMstRepository, never()).save(any());
        verify(tbPayFileDataRepository, never()).save(any());
    }
}
