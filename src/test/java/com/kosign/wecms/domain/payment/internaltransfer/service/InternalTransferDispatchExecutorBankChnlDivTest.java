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
    void vcbVnd_routesToFilePath_notApi() {
        // Legacy PymnHelper.pymn_0009_001_c001:2585 + :2600 (SVN r36822, soknyran 2026-07-16,
        // redmine 43078 — verified via `svn log`; the r36803 cited previously is a different
        // 2026-07-07 commit by ouddom under redmine 43104):
        //     boolean isVcbFileTransfer = VCB_BANK_CD.equals(scdulPymnSumr.getOutBankCd());
        //     if ((((TRNF_CCY_CD == "VND" && !isShinhanUsdToVnd) || isWooriUsdToVnd)) && !isVcbFileTransfer)
        // "VCB (970436) cannot support the real-time Transfer API without a certificate, so ALL
        //  VCB transfers are now routed to the File path." VND would otherwise take the API
        // branch, so this is the case the guard exists for. Mirrors the Vendor twin
        // (VendorDispatchExecutorTest.vcbVnd_routesToFilePath_notApi).
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        ScdulPymnSumrDetl l = line(BIDV);
        l.setTrnfCcyCd("VND");
        l.setDepoCcyCd("VND");
        l.setAntDivCd("C");
        executor.execute(master(VCB), List.of(l), "VND");

        verify(tbPayFileMstRepository).save(any());          // File path
        verify(tbPayTargetRepository, never()).save(any());  // NOT the real-time / API path
    }

    @Test
    void vcbVnd_longFormAntDivCdIsNulled_bankChnlDivOther() {
        // F-4 / Option A. OQ-2 (Mr. Jung 2026-07-16) confirms VCB VND is file-based and legacy
        // PymnHelper:2573 leaves the copyFrom value in place — but copyFrom narrows through
        // AntDivCdT.fromValue (TbPayFileData.java:301), which accepts ONLY the 1-char codes, so a 5-char
        // "NAPAS" upload is NULL in legacy too. The old expectation could not be persisted:
        // TB_PAY_FILE_DATA.ANT_DIV_CD is bpchar(1) and PostgreSQL rejects it with SQLSTATE 22001.
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        ScdulPymnSumrDetl l = line(BIDV);
        l.setTrnfCcyCd("VND");
        l.setDepoCcyCd("VND");
        l.setAntDivCd("NAPAS");
        executor.execute(master(VCB), List.of(l), "VND");

        TbPayFileData d = captureData();
        assertThat(d.getAntDivCd()).isNull();
        assertThat(d.getBankChnlDiv()).isEqualTo("O");   // VCB->BIDV other-bank
    }

    /* ── F-4 additions: a 1-char upload IS storable and must survive verbatim ── */

    @Test
    void vcbVnd_oneCharAntDivCd_isPreserved() {
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        ScdulPymnSumrDetl l = line(BIDV);
        l.setTrnfCcyCd("VND");
        l.setDepoCcyCd("VND");
        l.setAntDivCd("C");
        executor.execute(master(VCB), List.of(l), "VND");
        assertThat(captureData().getAntDivCd()).isEqualTo("C");
    }

    @Test
    void vcbVnd_paddedOneCharAntDivCd_isPreserved() {
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        ScdulPymnSumrDetl l = line(BIDV);
        l.setTrnfCcyCd("VND");
        l.setDepoCcyCd("VND");
        l.setAntDivCd("N    ");   // bpchar(5) padding
        executor.execute(master(VCB), List.of(l), "VND");
        assertThat(captureData().getAntDivCd()).isEqualTo("N");
    }

    @Test
    void vcbVnd_unknownAntDivCd_isNulled() {
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        ScdulPymnSumrDetl l = line(BIDV);
        l.setTrnfCcyCd("VND");
        l.setDepoCcyCd("VND");
        l.setAntDivCd("T");       // HAENGSUNG raw passthrough — never guessed
        executor.execute(master(VCB), List.of(l), "VND");
        assertThat(captureData().getAntDivCd()).isNull();
    }

    /* ───────────── F-2: legacy USD first pass (PymnHelper:2779-2786) ───────────── */

    @Test
    void shinhanUsd_firstPass_isEbank() {
        // Legacy :2779-2780 — Shinhan + detail TRNF_CCY_CD='USD' -> EBANK 'E'.
        // depoCcyCd stays USD so isShinhanUsdToVnd is false and the second pass does not fire.
        ScdulPymnSumrDetl l = line(BIDV);       // TRNF_CCY_CD='USD', depoCcyCd='USD'
        l.setAntDivCd("CITAD");                 // uploaded code must be overridden, not narrowed
        executor.execute(master(SHINHAN), List.of(l), "USD");

        assertThat(captureData().getAntDivCd()).isEqualTo("E");
    }

    @Test
    void wooriUsdToUsd_firstPass_isVcbChannel() {
        // Legacy :2781-2782 — Woori + detail TRNF_CCY_CD='USD' -> VCB channel 'V'.
        // depoCcyCd='USD' (not VND) so isWooriUsdToVnd is false → file path, not API.
        ScdulPymnSumrDetl l = line(BIDV);
        l.setAntDivCd("NAPAS");
        executor.execute(master(WOORI), List.of(l), "USD");

        assertThat(captureData().getAntDivCd()).isEqualTo("V");
    }

    @Test
    void vcbUsd_firstPass_staysNull() {
        // Legacy :2783-2785 sets an explicit null; the refactor reaches null by never assigning.
        // Guards that the F-2 block did not accidentally give VCB a channel.
        ReflectionTestUtils.setField(executor, "vcbBankCd", VCB);
        ScdulPymnSumrDetl l = line(VCB);        // same-bank, USD
        l.setAntDivCd("CITAD");
        executor.execute(master(VCB), List.of(l), "USD");

        assertThat(captureData().getAntDivCd()).isNull();
    }

    @Test
    void otherBankUsd_firstPass_staysNull() {
        // No legacy branch fires; copyFrom -> fromValue("CITAD") -> null. Refactor must match.
        ScdulPymnSumrDetl l = line(BIDV);
        l.setAntDivCd("CITAD");
        executor.execute(master(BIDV), List.of(l), "USD");

        assertThat(captureData().getAntDivCd()).isNull();
    }

    /* ───────────── F-5: Shinhan USD→VND second pass (legacy :2846-2854) ───────────── */

    /** Shinhan USD→VND: withdrawal USD, deposit VND, batch currency VND → file path. */
    private ScdulPymnSumrDetl shinhanUsdToVndLine(String tarBankCd) {
        ScdulPymnSumrDetl l = line(tarBankCd);
        l.setTrnfCcyCd("VND");    // legacy note :2775-2777 — TRNF_CCY_CD is VND here
        l.setDepoCcyCd("VND");
        l.setAntDivCd("CITAD");   // the uploaded code the second pass must override
        return l;
    }

    @Test
    void shinhanUsdToVnd_sameBank_isNull_notCitad() {
        // F-5 FIX. Legacy :2846-2848 is an explicit setAntDivCd(null) — sFTP layout v2.1
        // "File ebank USD -> VND same bank" has scfm_intf_t empty (Mr. Kang 2026-05-12).
        // The refactor previously wrote 'C', following the STALE legacy comment at :2801-2804
        // which contradicts the code beneath it.
        executor.execute(master(SHINHAN), List.of(shinhanUsdToVndLine(SHINHAN)), "VND");

        TbPayFileData d = captureData();
        assertThat(d.getAntDivCd()).isNull();
        assertThat(d.getBankChnlDiv()).isEqualTo("T");   // same-bank still stamped
    }

    @Test
    void shinhanUsdToVnd_sameBankByEngAbrvatNm_isNull() {
        // Same-bank via ENG_ABRVAT_NM group (different branch code) must behave identically.
        firmBank(SHINHAN, "SHBK");
        firmBank(SHINHAN_BRANCH, "SHBK");
        executor.execute(master(SHINHAN), List.of(shinhanUsdToVndLine(SHINHAN_BRANCH)), "VND");

        TbPayFileData d = captureData();
        assertThat(d.getAntDivCd()).isNull();
        assertThat(d.getBankChnlDiv()).isEqualTo("T");
    }

    @Test
    void shinhanUsdToVnd_otherBank_isEbank_unchanged() {
        // Legacy :2852-2854 — other-bank -> EBANK 'E'. Already matched; guards the F-5 edit
        // from over-reaching into the other-bank branch.
        executor.execute(master(SHINHAN), List.of(shinhanUsdToVndLine(BIDV)), "VND");

        TbPayFileData d = captureData();
        assertThat(d.getAntDivCd()).isEqualTo("E");
        assertThat(d.getBankChnlDiv()).isEqualTo("O");
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
