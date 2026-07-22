package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
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
 * NC-2 + BANK_PRTN_ID — Internal Transfer file-path parity with legacy
 * {@code PymnHelper.pymn_0009_001_c001}.
 *
 * <p><b>NC-2 ({@code BANK_CHNL_DIV}):</b> same bank = raw bank-code equality OR
 * same banking group via {@code ENG_ABRVAT_NM} ({@code TB_FIRM_BANK_CODE},
 * active rows only) → {@code "T"}; otherwise {@code "O"} (legacy :2767-2806).
 *
 * <p><b>BANK_PRTN_ID:</b> {@code TB_PAY_FILE_MST.BANK_PRTN_ID} resolves from
 * {@code TB_FIRM_CONF} (legacy :2667); a missing config row blocks dispatch with
 * {@code WBE2089} (mirrors Expense / Vendor).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalTransferDispatchExecutorBankChnlDivTest {

    private static final String BIZ      = "VN_VNM0004_000001";
    private static final String SHINHAN  = "970424";
    private static final String SHINHAN_BRANCH = "970425";
    private static final String WOORI    = "970457";
    private static final String VCB      = "970436";
    private static final String BIDV     = "970418";

    @Mock private TbPayTargetRepository        tbPayTargetRepository;
    @Mock private TbPayFileMstRepository       tbPayFileMstRepository;
    @Mock private TbPayFileDataRepository      tbPayFileDataRepository;
    @Mock private AcctMstrRepository           acctMstrRepository;
    @Mock private TbFirmBankCodeRepository     tbFirmBankCodeRepository;
    @Mock private TbFirmConfRepository         tbFirmConfRepository;
    @Mock private DispatchKeyGenerator         keyGenerator;
    @Mock private LegacyFunctionRepository     legacyFunctions;
    @Mock private DispatchAutoReceiveRegistrar autoReceiveRegistrar;
    @Mock private RealtimeDispatchRegistrar    realtimeDispatchRegistrar;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks private InternalTransferDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "tester", BIZ,
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        ReflectionTestUtils.setField(executor, "shinhanBankCd", SHINHAN);
        ReflectionTestUtils.setField(executor, "wooriBankCd", WOORI);
        when(keyGenerator.nextFileSeqNo(any(), any())).thenReturn("1");
        when(legacyFunctions.nextTbPayFileDataSeq(any(), any())).thenReturn("1");
        // Default TB_FIRM_CONF present so file-path routing tests proceed; the
        // absent case is overridden in its own test (WBE2089 block).
        TbFirmConf defaultConf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(defaultConf.getBankPrtnId()).thenReturn("PRTN-DEFAULT");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(defaultConf));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /** Generic USD/file batch (non-Woori, non-Shinhan) routes to the file path. */
    private ScdulPymnSumr master(String outBankCd) {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ);
        m.setScdulPymnNo("0000000001");
        m.setScdulPymnTypeCd("03");   // Internal Transfer
        m.setOutBankCd(outBankCd);
        m.setWdrlActNo("1001");
        m.setWdrlCcyCd("USD");
        m.setTotTrnsCnt(BigDecimal.ONE);
        m.setTotalTranAmt(new BigDecimal("100"));
        return m;
    }

    private ScdulPymnSumrDetl line(String tarBankCd) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo("0000000001");
        d.setScdulPymnDetlNo("1");
        d.setTarBankCd(tarBankCd);
        d.setTrnfCcyCd("USD");
        d.setFincMdaNo("9999");
        d.setTranAmt(new BigDecimal("100"));
        d.setRcptNm("RECIPIENT");
        d.setTranUsag("INVOICE-123");
        d.setDepoCcyCd("USD");   // NOT VND → not Shinhan/Woori USD→VND → file path
        return d;
    }

    private TbPayFileData captureData() {
        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        return c.getValue();
    }

    private TbPayFileMst captureMst() {
        ArgumentCaptor<TbPayFileMst> c = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(tbPayFileMstRepository).save(c.capture());
        return c.getValue();
    }

    /* ───────────── NC-2 BANK_CHNL_DIV ───────────── */

    @Test
    void rawSameBankCode_currentBankTransfer_T() {
        // outBank == tarBank by raw code → "T" (raw equality short-circuits).
        executor.execute(master(BIDV), List.of(line(BIDV)), "USD");

        assertThat(captureData().getBankChnlDiv()).isEqualTo("T");
    }

    @Test
    void engAbrvatNmGroupMatch_currentBankTransfer_T() {
        // Different raw codes, same ENG_ABRVAT_NM group (Shinhan HQ + branch) → "T".
        firmBank(SHINHAN, "SHB");
        firmBank(SHINHAN_BRANCH, "SHB");

        executor.execute(master(SHINHAN), List.of(line(SHINHAN_BRANCH)), "USD");

        assertThat(captureData().getBankChnlDiv()).isEqualTo("T");
    }

    @Test
    void differentBank_otherBankTransfer_O() {
        // Different raw codes, no ENG_ABRVAT_NM group match → "O".
        executor.execute(master(BIDV), List.of(line(VCB)), "USD");

        assertThat(captureData().getBankChnlDiv()).isEqualTo("O");
    }

    @Test
    void vcbVnd_carriesUploadedAntDivCd_bankChnlDivOther() {
        // OQ-2 (Mr. Jung 2026-07-16 CONFIRMED file-based VCB; legacy PymnHelper:2573 "VCB VND -> Other
        // : NAPAS/CITAD from upload"). VCB VND now carries the uploaded code (was null before OQ-2);
        // VCB USD stays null (same-bank only); non-VCB internal transfers are unchanged.
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        ScdulPymnSumrDetl l = line(BIDV);
        l.setTrnfCcyCd("VND");
        l.setDepoCcyCd("VND");
        l.setAntDivCd("NAPAS");
        executor.execute(master(VCB), List.of(l), "VND");

        TbPayFileData d = captureData();
        assertThat(d.getAntDivCd()).isEqualTo("NAPAS");
        assertThat(d.getBankChnlDiv()).isEqualTo("O");   // VCB->BIDV other-bank
    }

    /* ───────────── BANK_PRTN_ID ───────────── */

    @Test
    void bankPrtnId_readFromTbFirmConf_whenPresent() {
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(conf));

        executor.execute(master(BIDV), List.of(line(BIDV)), "USD");

        assertThat(captureMst().getBankPrtnId()).isEqualTo("PRTN1");
    }

    @Test
    void dispatch_isBlocked_whenFirmConfAbsent() {
        // Legacy pymn_0009_001_c001:2667 NPE-rolls-back when TB_FIRM_CONF is
        // missing; refactor blocks with WBE2089 (same effect, clean code).
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                executor.execute(master(BIDV), List.of(line(BIDV)), "USD"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2089");

        verify(tbPayFileMstRepository, never()).save(any());
        verify(tbPayFileDataRepository, never()).save(any());
    }

    @Test
    void ownerNm_rawRcptNm_blankPreserved() {
        // B — legacy raw RCPT_NM, no fallback (PymnHelper file path).
        ScdulPymnSumrDetl l = line(BIDV);
        l.setRcptNm("");
        executor.execute(master(BIDV), List.of(l), "USD");
        assertThat(captureData().getFincMdaOwnerNm()).isEmpty();
    }

    @Test
    void remarks_bothFromTranUsag_notWdrlActRmrk() {
        // I — legacy PymnHelper:2757-2758: BOTH remark columns come from TRAN_USAG.
        ScdulPymnSumrDetl l = line(BIDV);
        l.setTranUsag("USAGE-X");
        l.setWdrlActRmrk("WDRL-Y");
        executor.execute(master(BIDV), List.of(l), "USD");

        TbPayFileData d = captureData();
        assertThat(d.getWdrlActRmrk()).isEqualTo("USAGE-X");   // TRAN_USAG, not WDRL_ACT_RMRK
        assertThat(d.getTarAcctRmrk()).isEqualTo("USAGE-X");
    }

    private void firmBank(String bankCd, String engAbrvatNm) {
        TbFirmBankCode b = org.mockito.Mockito.mock(TbFirmBankCode.class);
        when(b.getEngAbrvatNm()).thenReturn(engAbrvatNm);
        when(tbFirmBankCodeRepository.findFirstByBankCdAndBankStas(bankCd, "1"))
                .thenReturn(Optional.of(b));
    }
}
