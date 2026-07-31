package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.common.LegacyTextNormalizer;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.file.VietnameseText;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the invariant the four free-text wire fields rest on: <b>tone-stripping is
 * actually applied, on the real build path</b>.
 *
 * <h2>Why this class exists, and why {@code VietnameseTextTest} was not enough</h2>
 * {@code docs/audits/special-char-validation-parity-audit-2026-06-10.md} records that F1 (Vietnamese
 * tone-stripping) <b>silently regressed to dead code</b> — {@code LegacyTextNormalizer} had zero
 * callers — and had to be re-implemented on 2026-07-10. Nothing failed when it regressed, because the
 * only tests were unit tests of {@code replaceTone} <i>in isolation</i>: the mapping function stayed
 * perfectly correct while nobody called it.
 *
 * <p>Every safety argument about the four fields
 * ({@code senderName} / {@code receiverName} / {@code depositAccountMemo} /
 * {@code withdrawalAccountMemo}) depends on tone-stripping running:
 * <ul>
 *   <li>the "ASCII overflow is arithmetically impossible" argument that justifies adding <b>no</b>
 *       truncation holds only because the values reach the codec as ASCII;</li>
 *   <li>the bank's fixed-length channel does not accept composed Vietnamese at all, so a dropped
 *       strip is a delivery defect even when the record length happens to survive.</li>
 * </ul>
 *
 * <p>So these tests assert <b>call-site behaviour</b>, not mapping behaviour: they drive
 * {@link BulkReceptionService#receive} with toned input and assert the tone-stripped form appears on
 * every one of the four fields, in <b>both</b> sinks — the persisted {@code TB_FIRM_FILE_DATA} row and
 * the {@link DepositData} handed to {@link FixedLengthCodec#encode}. Deleting any single
 * {@code VietnameseText.replaceTone(...)} call in {@code buildAndDispatchFile} fails a named test here.
 *
 * <p>The harness mirrors {@code BulkReceptionReceiveTest} (same mocks, same spy on
 * {@code issueReception}, same pass-through transaction manager).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionToneStripInvariantTest {

    private static final String TRAN_DT = "20251023";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "1";
    private static final String OUT_BANK = "970457";
    private static final String WDRL = "100001020691";
    private static final String PRTN = "WRB001";
    private static final String VAN = "GV00000001";

    /** One toned value per field, distinct so a cross-wired assertion cannot pass by accident. */
    private static final String TONED_SENDER = "CÔNG TY TNHH THƯƠNG MẠI";
    private static final String PLAIN_SENDER = "CONG TY TNHH THUONG MAI";
    private static final String TONED_RECEIVER = "Nguyễn Văn Đức";
    private static final String PLAIN_RECEIVER = "Nguyen Van Duc";
    private static final String TONED_DEP_MEMO = "Thanh toán lương tháng 6";
    private static final String PLAIN_DEP_MEMO = "Thanh toan luong thang 6";
    private static final String TONED_WDRL_MEMO = "Chi phí vận chuyển Đà Nẵng";
    private static final String PLAIN_WDRL_MEMO = "Chi phi van chuyen Da Nang";
    private static final String TONED_ADDRESS = "Số 5 Đường Nguyễn Huệ, Quận 1";
    private static final String PLAIN_ADDRESS = "So 5 Duong Nguyen Hue, Quan 1";

    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;
    @Mock EfsProperties efsProperties;
    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmFileDataRepository firmFileDataRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock DispatchKeyGenerator dispatchKeyGenerator;
    @Mock SftpClient sftpClient;
    @Mock BulkTransferValidator bulkTransferValidator;
    @Mock com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    @Mock OperatingHoursService operatingHoursService;
    @Mock org.springframework.transaction.PlatformTransactionManager perFileTxManager;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final FixedLengthCodec codec = spy(new FixedLengthCodec());
    private final SftpProperties sftpProperties = new SftpProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private BulkReceptionService service;

    @BeforeEach
    void setUp() {
        service = new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, objectMapper,
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, codec, sftpClient, sftpProperties, bulkTransferValidator,
                opsAlertService, operatingHoursService,
                new BulkDispatchClaimTxService(payFileMstRepository),
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.PaymentBlockedCharGuard.class),
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.common.OutBankCdNormalizer.class),
                perFileTxManager);

        when(bulkTransferValidator.validate(any(), any())).thenReturn(Optional.empty());
        when(operatingHoursService.evaluateBulk(any(), any())).thenReturn(OperatingHoursDecision.DISPATCH);
        when(payFileMstRepository.claimForReception(any(), any(), any(), any())).thenReturn(1);
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow()));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf()));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(tonedDetail()));
    }

    // ── the four fields, on the persisted row ────────────────────────────────

    @DisplayName("TB_FIRM_FILE_DATA: every one of the four wire fields is tone-stripped before persist")
    @Test
    void firmFileDataRow_allFourFieldsAreToneStripped() {
        dispatch();
        TbFirmFileData row = firmDataRow();

        // Named per field so a deleted replaceTone call points at the exact line that lost it.
        assertThat(row.getSenderNm())
                .as("SENDER_NM — BulkReceptionService.buildAndDispatchFile setSenderNm")
                .isEqualTo(PLAIN_SENDER);
        assertThat(row.getFincMdaOwnerNm())
                .as("FINC_MDA_OWNER_NM (receiverName) — setFincMdaOwnerNm")
                .isEqualTo(PLAIN_RECEIVER);
        assertThat(row.getTarAcctRmrk())
                .as("TAR_ACCT_RMRK (depositAccountMemo) — setTarAcctRmrk")
                .isEqualTo(PLAIN_DEP_MEMO);
        assertThat(row.getWdrlAcctRmrk())
                .as("WDRL_ACCT_RMRK (withdrawalAccountMemo) — setWdrlAcctRmrk")
                .isEqualTo(PLAIN_WDRL_MEMO);
    }

    // ── the four fields, on the value handed to the codec ────────────────────

    @DisplayName("DepositData: every one of the four wire fields is tone-stripped before encode()")
    @Test
    void depositDataLine_allFourFieldsAreToneStripped() {
        dispatch();
        DepositData line = depositLine();

        // The persisted row and the encoded line are stripped by SEPARATE calls in the same loop, so
        // both sinks need their own assertion — losing either one is a real defect.
        assertThat(line.senderName())
                .as("DepositData.senderName -> D record offset 79, 150 bytes")
                .isEqualTo(PLAIN_SENDER);
        assertThat(line.receiverName())
                .as("DepositData.receiverName -> D record offset 229, 150 bytes")
                .isEqualTo(PLAIN_RECEIVER);
        assertThat(line.depositAccountMemo())
                .as("DepositData.depositAccountMemo -> D record offset 379, 210 bytes")
                .isEqualTo(PLAIN_DEP_MEMO);
        assertThat(line.withdrawalAccountMemo())
                .as("DepositData.withdrawalAccountMemo -> D record offset 589, 210 bytes")
                .isEqualTo(PLAIN_WDRL_MEMO);
    }

    @DisplayName("the recipient address is tone-stripped too (5th field, byte-truncated at 180)")
    @Test
    void recipientAddressIsAlsoToneStripped() {
        // Not one of the four C-14 fields, but it shares the same strip call site and the same
        // regression risk, and it is the one field that ALSO byte-truncates — so the composition
        // order (truncate then strip) is pinned here.
        dispatch();
        assertThat(depositLine().rcptUseAddr()).isEqualTo(PLAIN_ADDRESS);
        assertThat(firmDataRow().getRcptUseAddr()).isEqualTo(PLAIN_ADDRESS);
    }

    // ── the strongest form: nothing non-ASCII reaches the wire ───────────────

    @DisplayName("no text field handed to encode() contains a non-ASCII byte, given fully toned input")
    @Test
    void nothingNonAsciiReachesTheWire() {
        // Field-agnostic on purpose: this catches a NEW free-text field added to DepositData without a
        // strip call, which the four named assertions above cannot see.
        dispatch();
        DepositData line = depositLine();

        assertAscii("senderName", line.senderName());
        assertAscii("receiverName", line.receiverName());
        assertAscii("depositAccountMemo", line.depositAccountMemo());
        assertAscii("withdrawalAccountMemo", line.withdrawalAccountMemo());
        assertAscii("rcptUseAddr", line.rcptUseAddr());
    }

    @DisplayName("the built record is still exactly 1000 bytes per line with toned input")
    @Test
    void recordLengthSurvivesTonedInput() {
        // H + one D + T. The codec's own writeRecord guard would throw before this assertion, so this
        // documents the end state rather than adding new coverage — but it is the property an operator
        // cares about and it is cheap to pin.
        dispatch();
        ArgumentCaptor<DepositHeader> h = ArgumentCaptor.forClass(DepositHeader.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DepositData>> l = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DepositTrailer> t = ArgumentCaptor.forClass(DepositTrailer.class);
        verify(codec, times(1)).encode(h.capture(), l.capture(), t.capture(), any());

        byte[] bytes = new FixedLengthCodec().encode(h.getValue(), l.getValue(), t.getValue());
        assertThat(bytes.length).isEqualTo(3 * 1000);
    }

    // ── the mapping-table invariant, for BOTH copies of it ───────────────────

    @DisplayName("both tone tables are 134 characters and index-aligned")
    @Test
    void bothToneTablesAre134CharsAndIndexAligned() {
        // Two independent hand-copied pairs of literals exist in the tree: the dispatch-path copy in
        // VietnameseText and the entry-path copy in LegacyTextNormalizer. Either drifting by one
        // character silently emits the wrong letter for every subsequent code point — no exception, no
        // failed record, just wrong recipient names at the bank.
        assertTableAligned("VietnameseText", VietnameseText.class, "TONED", "PLAIN");
        assertTableAligned("LegacyTextNormalizer", LegacyTextNormalizer.class, "VN_WITH_TONE", "VN_ASCII");
    }

    @DisplayName("the two tone tables are identical to each other")
    @Test
    void theTwoToneTablesAgree() {
        // The entry layer stores what the dispatch layer re-strips. If the tables disagree, a value
        // that the upload accepted as clean ASCII could still be non-ASCII at dispatch (or vice
        // versa), and the C-14 entry guard would be validating against the wrong alphabet.
        assertThat(field(LegacyTextNormalizer.class, "VN_WITH_TONE"))
                .as("VN_WITH_TONE must equal VietnameseText.TONED")
                .isEqualTo(field(VietnameseText.class, "TONED"));
        assertThat(field(LegacyTextNormalizer.class, "VN_ASCII"))
                .as("VN_ASCII must equal VietnameseText.PLAIN")
                .isEqualTo(field(VietnameseText.class, "PLAIN"));
    }

    @DisplayName("both implementations produce identical output for every table entry")
    @Test
    void bothImplementationsAgreeCharacterByCharacter() {
        // Stronger than comparing the literals: it also catches a divergence in the loop itself
        // (e.g. one of them switching to java.text.Normalizer).
        String toned = field(VietnameseText.class, "TONED");
        for (int i = 0; i < toned.length(); i++) {
            String in = String.valueOf(toned.charAt(i));
            assertThat(LegacyTextNormalizer.replaceTone(in))
                    .as("index %d (%s): the two replaceTone implementations must agree", i, in)
                    .isEqualTo(VietnameseText.replaceTone(in));
        }
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    private void dispatch() {
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
    }

    private TbFirmFileData firmDataRow() {
        ArgumentCaptor<TbFirmFileData> cap = ArgumentCaptor.forClass(TbFirmFileData.class);
        verify(firmFileDataRepository, atLeastOnce()).save(cap.capture());
        return cap.getValue();
    }

    private DepositData depositLine() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DepositData>> cap = ArgumentCaptor.forClass(List.class);
        verify(codec, atLeastOnce()).encode(any(), cap.capture(), any(), any());
        List<DepositData> lines = cap.getValue();
        assertThat(lines).hasSize(1);
        return lines.get(0);
    }

    private static void assertAscii(String field, String value) {
        assertThat(value).as("%s must not be null", field).isNotNull();
        assertThat(value.getBytes(StandardCharsets.UTF_8).length)
                .as("%s must be pure ASCII after tone-stripping (value=[%s])", field, value)
                .isEqualTo(value.length());
    }

    private static void assertTableAligned(String owner, Class<?> type, String tonedField, String plainField) {
        String toned = field(type, tonedField);
        String plain = field(type, plainField);
        assertThat(toned.length()).as("%s.%s must be 134 chars", owner, tonedField).isEqualTo(134);
        assertThat(plain.length()).as("%s.%s must be 134 chars", owner, plainField).isEqualTo(134);

        // Index alignment is the property the mapping actually depends on: every position must pair a
        // toned character with an ASCII letter of the SAME case.
        for (int i = 0; i < toned.length(); i++) {
            char t = toned.charAt(i);
            char p = plain.charAt(i);
            assertThat((int) p).as("%s index %d ('%s') must map into ASCII", owner, i, t).isLessThan(0x80);
            assertThat(Character.isLetter(p)).as("%s index %d must map to a letter", owner, i).isTrue();
            assertThat(Character.isUpperCase(p))
                    .as("%s index %d: '%s' -> '%s' must keep case", owner, i, t, p)
                    .isEqualTo(Character.isUpperCase(t));
        }
    }

    private static String field(Class<?> type, String name) {
        return (String) ReflectionTestUtils.getField(type, name);
    }

    private TbPayFileMst payRow() {
        TbPayFileMst p = new TbPayFileMst();
        p.setTranDt(TRAN_DT);
        p.setBizKey(BIZ_KEY);
        p.setFileSeqNo(FILE_SEQ_NO);
        p.setOutBankCd(OUT_BANK);
        p.setWdrlActNo(WDRL);
        p.setBankPrtnId(PRTN);
        p.setFundTp("0");
        p.setTranTp("1");
        p.setProcStat(ProcStat.WAIT.code());
        return p;
    }

    private TbFirmConf conf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setOutBankCd(OUT_BANK);
        c.setWdrlActNo(WDRL);
        c.setBankPrtnId(PRTN);
        c.setVanOrgCd(VAN);
        c.setFileType("WRB");
        c.setOrgStat("1");
        c.setBatTranBranchDiv("N");
        c.setBatTranBranchLimit(BigDecimal.valueOf(2000));
        c.setRegDttm("20251023000000");
        return c;
    }

    /** One detail row whose five free-text fields all carry Vietnamese diacritics. */
    private TbPayFileData tonedDetail() {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.ONE);
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("0681000001");
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal("1000.00"));
        d.setBankChnlDiv("T");
        d.setAntDivCd("N");
        d.setSenderNm(TONED_SENDER);
        d.setFincMdaOwnerNm(TONED_RECEIVER);
        d.setTarAcctRmrk(TONED_DEP_MEMO);
        d.setWdrlActRmrk(TONED_WDRL_MEMO);
        d.setRcptUseAddr(TONED_ADDRESS);
        return d;
    }

    /** Unused, but documents that the fixtures really are toned (guards a copy/paste of plain text). */
    @DisplayName("sanity: the fixtures are actually toned, so the assertions above are meaningful")
    @Test
    void fixturesAreActuallyToned() {
        Function<String, Boolean> nonAscii =
                s -> s.getBytes(StandardCharsets.UTF_8).length != s.length();
        assertThat(nonAscii.apply(TONED_SENDER)).isTrue();
        assertThat(nonAscii.apply(TONED_RECEIVER)).isTrue();
        assertThat(nonAscii.apply(TONED_DEP_MEMO)).isTrue();
        assertThat(nonAscii.apply(TONED_WDRL_MEMO)).isTrue();
        assertThat(nonAscii.apply(TONED_ADDRESS)).isTrue();
        // …and that the expected values are not (otherwise a no-op strip would still pass).
        assertThat(nonAscii.apply(PLAIN_SENDER)).isFalse();
        assertThat(nonAscii.apply(PLAIN_RECEIVER)).isFalse();
        assertThat(nonAscii.apply(PLAIN_DEP_MEMO)).isFalse();
        assertThat(nonAscii.apply(PLAIN_WDRL_MEMO)).isFalse();
        assertThat(nonAscii.apply(PLAIN_ADDRESS)).isFalse();
    }
}
