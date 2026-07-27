package com.kosign.wecms.domain.payment.payrollpayment.service;

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
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import org.springframework.test.util.ReflectionTestUtils;
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
 * Payroll file-path {@code BANK_PRTN_ID} parity with legacy
 * {@code pymn_0005_001_c001_act.jsp:153} — the header partner id resolves from
 * {@code TB_FIRM_CONF}; a missing config row blocks dispatch with {@code WBE2089}
 * (mirrors Expense / Vendor / Internal Transfer).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollDispatchExecutorBankPrtnIdTest {

    private static final String BIZ     = "VN_VNM0004_000001";
    private static final String SHINHAN = "970424";
    private static final String SHINHAN_BRANCH = "970425";
    private static final String WOORI   = "970457";
    private static final String VCB     = "970436";
    private static final String BIDV    = "970418";

    @Mock private TbPayFileMstRepository       tbPayFileMstRepository;
    @Mock private TbPayFileDataRepository      tbPayFileDataRepository;
    @Mock private AcctMstrRepository           acctMstrRepository;
    @Mock private TbFirmConfRepository         tbFirmConfRepository;
    @Mock private TbFirmBankCodeRepository     tbFirmBankCodeRepository;
    @Mock private DispatchKeyGenerator         keyGenerator;
    @Mock private LegacyFunctionRepository     legacyFunctions;
    @Mock private DispatchAutoReceiveRegistrar autoReceiveRegistrar;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-06-17T03:00:00Z"),
            ZoneId.of("Asia/Ho_Chi_Minh"));

    @InjectMocks private PayrollDispatchExecutor executor;

    @BeforeEach
    void setUp() {
        TenantContext.set("tester", "tester", BIZ,
                LangCd.EN, TenantContext.AUTH_GR_USER, "F");
        ReflectionTestUtils.setField(executor, "shinhanBankCd", SHINHAN);
        ReflectionTestUtils.setField(executor, "wooriBankCd", WOORI);
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        when(keyGenerator.nextFileSeqNo(any(), any())).thenReturn("1");
        when(legacyFunctions.nextTbPayFileDataSeq(any(), any())).thenReturn("1");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ScdulPymnSumr master() {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ);
        m.setScdulPymnNo("0000000001");
        m.setScdulPymnTypeCd("04");   // Payroll
        m.setOutBankCd(SHINHAN);
        m.setWdrlActNo("1001");
        m.setWdrlCcyCd("USD");
        m.setTotTrnsCnt(BigDecimal.ONE);
        m.setTotalTranAmt(new BigDecimal("100"));
        return m;
    }

    private ScdulPymnSumrDetl line() {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo("0000000001");
        d.setScdulPymnDetlNo("1");
        d.setTarBankCd(SHINHAN);
        d.setTrnfCcyCd("USD");
        d.setFincMdaNo("9999");
        d.setTranAmt(new BigDecimal("100"));
        d.setRcptNm("EMPLOYEE");
        d.setWdrlActRmrk("salary");
        return d;
    }

    @Test
    void bankPrtnId_readFromTbFirmConf_whenPresent() {
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(conf));

        executor.execute(master(), List.of(line()), "USD", "0");

        ArgumentCaptor<TbPayFileMst> c = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(tbPayFileMstRepository).save(c.capture());
        assertThat(c.getValue().getBankPrtnId()).isEqualTo("PRTN1");
    }

    @Test
    void dispatch_isBlocked_whenFirmConfAbsent() {
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                executor.execute(master(), List.of(line()), "USD", "0"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE2089");

        verify(tbPayFileMstRepository, never()).save(any());
        verify(tbPayFileDataRepository, never()).save(any());
    }

    @Test
    void ownerNm_rawRcptNm_blankPreserved() {
        // B — legacy raw RCPT_NM, no fallback (pymn_0005_001_c001_act.jsp:206).
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(conf));
        ScdulPymnSumrDetl l = line();
        l.setRcptNm("");
        executor.execute(master(), List.of(l), "USD", "0");

        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        assertThat(c.getValue().getFincMdaOwnerNm()).isEmpty();
    }

    /* ── J — BANK_CHNL_DIV parity (pymn_0005_001_c001_act.jsp:179-182, 230-263):
       guarded to Shinhan/Woori/VCB; same-bank = raw code OR ENG_ABRVAT_NM group;
       VCB VND blocked; VCB USD->other blocked. ── */

    private void stubConf() {
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(conf));
    }

    private void firmBank(String bankCd, String engAbrvatNm) {
        TbFirmBankCode b = org.mockito.Mockito.mock(TbFirmBankCode.class);
        when(b.getEngAbrvatNm()).thenReturn(engAbrvatNm);
        when(tbFirmBankCodeRepository.findFirstByBankCdAndBankStas(bankCd, "1"))
                .thenReturn(Optional.of(b));
    }

    private ScdulPymnSumr masterOut(String outBank) {
        ScdulPymnSumr m = master();
        m.setOutBankCd(outBank);
        return m;
    }

    private ScdulPymnSumrDetl lineTar(String tarBank, String ccy) {
        ScdulPymnSumrDetl d = line();
        d.setTarBankCd(tarBank);
        d.setTrnfCcyCd(ccy);
        return d;
    }

    private TbPayFileData captureData() {
        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        return c.getValue();
    }

    @Test
    void sameRawBankCode_currentBank_T() {
        stubConf();
        executor.execute(masterOut(SHINHAN), List.of(lineTar(SHINHAN, "USD")), "USD", "0");
        assertThat(captureData().getBankChnlDiv()).isEqualTo("T");
    }

    @Test
    void engAbrvatNmGroupMatch_currentBank_T() {
        stubConf();
        firmBank(SHINHAN, "SHB");
        firmBank(SHINHAN_BRANCH, "SHB");
        executor.execute(masterOut(SHINHAN), List.of(lineTar(SHINHAN_BRANCH, "USD")), "USD", "0");
        assertThat(captureData().getBankChnlDiv()).isEqualTo("T");
    }

    @Test
    void differentGroup_otherBank_O() {
        stubConf();
        executor.execute(masterOut(SHINHAN), List.of(lineTar(BIDV, "USD")), "USD", "0");
        assertThat(captureData().getBankChnlDiv()).isEqualTo("O");
    }

    @Test
    void unsupportedOutBank_bankChnlDivUnset() {
        // Non Shinhan/Woori/VCB out-bank: legacy has no BANK_CHNL_DIV else branch -> left null.
        stubConf();
        executor.execute(masterOut(BIDV), List.of(lineTar(BIDV, "USD")), "USD", "0");
        assertThat(captureData().getBankChnlDiv()).isNull();
    }

    @Test
    void vcbVnd_blocked() {
        stubConf();
        assertThatThrownBy(() ->
                executor.execute(masterOut(VCB), List.of(lineTar(VCB, "VND")), "VND", "0"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_PAYROLL_VCB_VND_NOT_SUPPORTED");
        verify(tbPayFileDataRepository, never()).save(any());
    }

    @Test
    void vcbUsdOtherBank_blocked() {
        stubConf();
        assertThatThrownBy(() ->
                executor.execute(masterOut(VCB), List.of(lineTar(BIDV, "USD")), "USD", "0"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_PAYROLL_VCB_USD_OTHER_BANK");
        verify(tbPayFileDataRepository, never()).save(any());
    }
}
