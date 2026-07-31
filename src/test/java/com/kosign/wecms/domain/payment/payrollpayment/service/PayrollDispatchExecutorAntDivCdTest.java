package com.kosign.wecms.domain.payment.payrollpayment.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.repository.AcctMstrRepository;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchAutoReceiveRegistrar;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumr;
import com.kosign.wecms.domain.payment.internaltransfer.entity.ScdulPymnSumrDetl;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F-1 — Payroll {@code TB_PAY_FILE_DATA.ANT_DIV_CD} parity with legacy
 * {@code pymn_0005_001_c001_act.jsp:214-227} (channel selection) and
 * {@code :256-259} (Woori VND same-bank override).
 *
 * <p>Before the fix the executor never called {@code setAntDivCd}, so every
 * Payroll file row carried {@code NULL}. That produced an empty channel set in
 * {@code BulkReceptionService.presentChannels} and therefore a permanent
 * {@code SKIP} from {@code OperatingHoursService} — the pay-file stalled in
 * {@code PROC_STAT='9'} with no FAIL and no alert.
 *
 * <p>Width note: the source {@code SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD} is
 * {@code bpchar(5)} (Payroll writes the 5-char {@code "CITAD"}) while the target
 * {@code TB_PAY_FILE_DATA.ANT_DIV_CD} is {@code bpchar(1)} — so the value must be
 * narrowed through {@code AntDivChannel.toStorageCode}, never copied.
 *
 * @see com.kosign.wecms.domain.payment.dispatch.constant.AntDivChannelTest
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollDispatchExecutorAntDivCdTest {

    private static final String BIZ     = "VN_VNM0004_000001";
    private static final String SHINHAN = "970424";
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
    private Clock clock = Clock.fixed(Instant.parse("2026-07-28T03:00:00Z"),
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
        TbFirmConf conf = org.mockito.Mockito.mock(TbFirmConf.class);
        when(conf.getBankPrtnId()).thenReturn("PRTN1");
        when(tbFirmConfRepository.findById(any())).thenReturn(Optional.of(conf));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    /* ───────────────────────── fixtures ───────────────────────── */

    private ScdulPymnSumr master(String outBank) {
        ScdulPymnSumr m = new ScdulPymnSumr();
        m.setBizKey(BIZ);
        m.setScdulPymnNo("0000000001");
        m.setScdulPymnTypeCd("04");   // Payroll
        m.setOutBankCd(outBank);
        m.setWdrlActNo("1001");
        m.setTotTrnsCnt(BigDecimal.ONE);
        m.setTotalTranAmt(new BigDecimal("100"));
        return m;
    }

    /** One detail line. {@code antDivCd} is the 5-char detail value. */
    private ScdulPymnSumrDetl line(String tarBank, String ccy, String antDivCd) {
        ScdulPymnSumrDetl d = new ScdulPymnSumrDetl();
        d.setBizKey(BIZ);
        d.setScdulPymnNo("0000000001");
        d.setScdulPymnDetlNo("1");
        d.setTarBankCd(tarBank);
        d.setTrnfCcyCd(ccy);
        d.setAntDivCd(antDivCd);
        d.setFincMdaNo("9999");
        d.setTranAmt(new BigDecimal("100"));
        d.setRcptNm("EMPLOYEE");
        d.setWdrlActRmrk("salary");
        return d;
    }

    /** Run the executor for one line and return the persisted file-data row. */
    private TbPayFileData dispatchOne(String outBank, ScdulPymnSumrDetl line, String ccy) {
        executor.execute(master(outBank), List.of(line), ccy, "0");
        ArgumentCaptor<TbPayFileData> c = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(tbPayFileDataRepository).save(c.capture());
        return c.getValue();
    }

    /* ─────────────────── VND — uploaded detail code, narrowed ─────────────────── */

    @Test
    void shinhanVnd_citad_narrowsToC() {
        // PayrollPaymentService:544 hard-codes the 5-char "CITAD" on every detail row.
        // bpchar(1) target ⇒ must arrive as 'C', never "CITAD". Legacy :225-226.
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "VND", "CITAD"), "VND");
        assertThat(d.getAntDivCd()).isEqualTo("C");
    }

    @Test
    void shinhanVnd_napas_narrowsToN() {
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "VND", "NAPAS"), "VND");
        assertThat(d.getAntDivCd()).isEqualTo("N");
    }

    @Test
    void nonFirmBankingBank_vnd_stillUsesUploadedCode() {
        // Legacy's else branch is "VND currencies OR other banks" — a bank outside
        // Shinhan/Woori/VCB takes the uploaded code even for FCY.
        TbPayFileData d = dispatchOne(BIDV, line(BIDV, "USD", "CITAD"), "USD");
        assertThat(d.getAntDivCd()).isEqualTo("C");
    }

    /* ─────────────────── FCY — fixed per out-bank ─────────────────── */

    @Test
    void shinhanFcy_isEbank() {
        // Legacy :215-217 — non-VND + Shinhan ⇒ EBANK, regardless of the uploaded code.
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "USD", "CITAD"), "USD");
        assertThat(d.getAntDivCd()).isEqualTo("E");
    }

    @Test
    void wooriFcy_isVcbChannel() {
        // Legacy :218-220 — non-VND + Woori ⇒ the VCB channel ('V'), not the VCB bank.
        TbPayFileData d = dispatchOne(WOORI, line(BIDV, "USD", "CITAD"), "USD");
        assertThat(d.getAntDivCd()).isEqualTo("V");
    }

    /* ─────────────────── PRESERVED LEGACY NULL branches ─────────────────── */

    @Test
    void vcbFcy_isNull_preservedLegacyBehaviour() {
        // Legacy :221-223 — VCB FCY is same-bank-only, channel intentionally blank.
        // Target must be VCB too, else WBE_PAYROLL_VCB_USD_OTHER_BANK fires first.
        TbPayFileData d = dispatchOne(VCB, line(VCB, "USD", "CITAD"), "USD");
        assertThat(d.getAntDivCd()).isNull();
    }

    @Test
    void wooriVnd_sameBank_isNull_preservedLegacyBehaviour() {
        // Legacy :256-259 — the override runs INSIDE the same-bank branch and clears
        // the channel that the selection block already set. Order matters.
        TbPayFileData d = dispatchOne(WOORI, line(WOORI, "VND", "CITAD"), "VND");
        assertThat(d.getAntDivCd()).isNull();
        assertThat(d.getBankChnlDiv()).isEqualTo("T");   // same-bank still stamped
    }

    @Test
    void wooriVnd_otherBank_keepsUploadedCode() {
        // Guards the override's scope: it is same-bank-only. Other-bank Woori VND
        // must keep 'C' — otherwise the fix would blank every Woori VND payroll.
        TbPayFileData d = dispatchOne(WOORI, line(BIDV, "VND", "CITAD"), "VND");
        assertThat(d.getAntDivCd()).isEqualTo("C");
        assertThat(d.getBankChnlDiv()).isEqualTo("O");
    }

    /* ─────────────────── input robustness ─────────────────── */

    @Test
    void bpcharPaddedOneCharInput_isAccepted() {
        // ANT_DIV_CD is bpchar(5): a stored 'C' reads back as "C    ".
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "VND", "C    "), "VND");
        assertThat(d.getAntDivCd()).isEqualTo("C");
    }

    @Test
    void bpcharPaddedFiveCharName_isAccepted() {
        // "SWIFT" is already 5 chars; trailing whitespace must not defeat the match.
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "VND", "SWIFT "), "VND");
        assertThat(d.getAntDivCd()).isEqualTo("S");
    }

    @Test
    void lowercaseInput_isAccepted() {
        // Legacy AntDivCdT.parse uses equalsIgnoreCase.
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "VND", "citad"), "VND");
        assertThat(d.getAntDivCd()).isEqualTo("C");
    }

    /* ─────────────────── unknown values fail safe, never guessed ─────────────────── */

    @Test
    void unknownCode_isNull_notGuessed() {
        // HAENGSUNG's ERP import passes raw codes through (documented unresolved 'T').
        // Legacy AntDivCdT.parse returns null for anything unrecognised — the row must
        // NOT be coerced onto a channel nobody chose.
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "VND", "T"), "VND");
        assertThat(d.getAntDivCd()).isNull();
    }

    @Test
    void interCode_isNull_notAFileChannel() {
        // INTER is an own-bank marker with no AntDivCdT constant — not a file channel.
        TbPayFileData d = dispatchOne(SHINHAN, line(BIDV, "VND", "INTER"), "VND");
        assertThat(d.getAntDivCd()).isNull();
    }

    @Test
    void nullAndBlankDetailCode_areNull() {
        assertThat(dispatchOne(SHINHAN, line(BIDV, "VND", null), "VND").getAntDivCd()).isNull();

        org.mockito.Mockito.reset(tbPayFileDataRepository);
        assertThat(dispatchOne(SHINHAN, line(BIDV, "VND", "   "), "VND").getAntDivCd()).isNull();
    }
}
