package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the <b>카드계좌구분 (card-account flag)</b> inversion on the real build path, in both
 * directions, and at the wire byte.
 *
 * <h2>Why this needs its own guard</h2>
 * The mapping is an <b>inversion</b>: the financial-media discriminator {@code 'C'} (card) produces
 * the wire value {@code 'N'}, and everything else produces {@code 'Y'}. Legacy
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1.java:629-630}:
 *
 * <pre>
 * deposit_data.setCardAccountType(
 *         detailData.getString("FINC_MDA_DIV").equals("C") ? "N" : "Y");			// 카드계좌구분
 * </pre>
 *
 * Ours, {@code BulkReceptionService.buildAndDispatchFile}:
 *
 * <pre>
 * "C".equals(trim(d.getFincMdaDiv())) ? "N" : "Y",      // cardAccountType (legacy mapping)
 * </pre>
 *
 * <p>Read quickly, {@code C → N} looks like a transcription error, which is exactly the shape that
 * gets "corrected" into {@code C → Y} by a well-meaning reader. Handover
 * {@code 04_인터페이스_연동명세서.md §4.4} flags it as a derivation to verify for that reason. Until
 * this class existed the mapping had <b>zero test coverage</b> in either direction — a flipped
 * ternary would have shipped green.
 *
 * <p>This is a <b>wire</b> field, so a flip is not a display bug: every D record in the file would
 * carry the wrong account-type byte to the bank, on a channel where handover
 * {@code 08_기능추출_이관가이드.md §8.3} states 길이/패딩 한 글자도 변경 불가 — not one character of
 * length or padding may change. Hence the byte-level assertion as well as the value one.
 *
 * <h2>Offset note</h2>
 * Handover 04 §4.4 places 카드계좌구분 at <b>offset 64</b>. It is at <b>offset 63</b> (0-indexed),
 * 1 byte; {@code depositBankCode} starts at 64. Legacy {@code Deposit_Data} and
 * {@link FixedLengthCodec} agree with each other on 63, and the record still totals 998 + CRLF, so
 * this is a 1-indexed/0-indexed slip in the document rather than a layout disagreement. The
 * absolute-offset assertion below is pinned to the implementation, which is the authority.
 *
 * @see BulkReceptionToneStripInvariantTest the sibling call-site invariant guard this harness follows
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionCardAccountTypeTest {

    private static final String TRAN_DT = "20260805";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "1";
    private static final String OUT_BANK = "970424";
    private static final String WDRL = "700005285280";
    private static final String PRTN = "HGSNG";
    private static final String VAN = "GV00000001";

    /** D record starts at byte 1000 (H occupies 0..999); the flag sits at offset 63 within it. */
    private static final int CARD_FLAG_ABSOLUTE_OFFSET = 1000 + 63;

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
                new BulkDispatchClaimTxService(payFileMstRepository, firmFileMstRepository),
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
    }

    /* ── the inversion, both directions, on the value handed to the codec ──── */

    @Test
    @DisplayName("FINC_MDA_DIV='C' (card) -> cardAccountType 'N' — legacy BATCH1:629-630, an inversion")
    void cardMediaProducesN() {
        givenDetailWithFincMdaDiv("C");
        dispatch();

        assertThat(depositLine().cardAccountType())
                .as("legacy: FINC_MDA_DIV.equals(\"C\") ? \"N\" : \"Y\" — card must produce 'N', NOT 'Y'. "
                        + "If this fails because someone 'fixed' C->Y, re-read "
                        + "FILE_DEPOSIT_TRANSFER_BATCH1.java:629-630 before changing it back.")
                .isEqualTo("N");
    }

    @Test
    @DisplayName("FINC_MDA_DIV='A' (account) -> cardAccountType 'Y' — the else branch")
    void accountMediaProducesY() {
        givenDetailWithFincMdaDiv("A");
        dispatch();

        assertThat(depositLine().cardAccountType())
                .as("legacy's else branch: every non-'C' value produces 'Y'")
                .isEqualTo("Y");
    }

    /**
     * The full else branch, including the {@code bpchar} padding case.
     *
     * <p>{@code TB_PAY_FILE_DATA.FINC_MDA_DIV} is {@code bpchar(1)}, so a stored {@code 'C'} can read
     * back as {@code "C "}. Legacy compares with a bare {@code equals} and would miss it; we
     * {@code trim} first. That is a deliberate hardening over legacy and it is asserted here so it
     * cannot be removed silently — but note it is belt-and-braces in practice, since PostgreSQL
     * clips trailing blanks when assigning to {@code character(1)}.
     */
    @ParameterizedTest(name = "FINC_MDA_DIV [{0}] -> cardAccountType [{1}]")
    @CsvSource({
            "C,   N",     // card
            "'C ',N",     // card, bpchar-padded — our trim; legacy would miss this and emit 'Y'
            "A,   Y",     // account (WCConst.TAR_FINC_MDA_DIV_ACCT)
            "c,   Y",     // lower case is NOT card — legacy is case-sensitive, so we are too
            "'',  Y",     // blank
            "X,   Y",     // unknown code
    })
    void mappingTable(String fincMdaDiv, String expected) {
        givenDetailWithFincMdaDiv(fincMdaDiv);
        dispatch();

        assertThat(depositLine().cardAccountType())
                .as("FINC_MDA_DIV=[%s] must map to [%s]", fincMdaDiv, expected)
                .isEqualTo(expected);
    }

    /* ── the wire byte ──────────────────────────────────────────────────────── */

    @Test
    @DisplayName("wire: the card flag lands at D-record offset 63 as 'N' for a card row")
    void cardFlagByteOnTheWire_card() {
        givenDetailWithFincMdaDiv("C");
        dispatch();

        assertThat(encodedFile()[CARD_FLAG_ABSOLUTE_OFFSET])
                .as("D record offset 63 (absolute %d) must be 'N' for FINC_MDA_DIV='C'",
                        CARD_FLAG_ABSOLUTE_OFFSET)
                .isEqualTo((byte) 'N');
    }

    @Test
    @DisplayName("wire: the card flag lands at D-record offset 63 as 'Y' for an account row")
    void cardFlagByteOnTheWire_account() {
        givenDetailWithFincMdaDiv("A");
        dispatch();

        byte[] file = encodedFile();
        assertThat(file[CARD_FLAG_ABSOLUTE_OFFSET])
                .as("D record offset 63 (absolute %d) must be 'Y' for FINC_MDA_DIV='A'",
                        CARD_FLAG_ABSOLUTE_OFFSET)
                .isEqualTo((byte) 'Y');

        // Guards the offset itself: a 1-byte drift would silently move the flag into the neighbouring
        // field and still pass the assertion above if that field happened to hold the same character.
        assertThat(file[CARD_FLAG_ABSOLUTE_OFFSET - 1])
                .as("offset 62 is otherBankNetworkCode ('N' for NAPAS in this fixture), not the card flag")
                .isEqualTo((byte) 'N');
        assertThat(file).as("H + one D + T, 1000 bytes each").hasSize(3000);
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    private void givenDetailWithFincMdaDiv(String fincMdaDiv) {
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(fincMdaDiv)));
    }

    private void dispatch() {
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
    }

    private DepositData depositLine() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DepositData>> cap = ArgumentCaptor.forClass(List.class);
        verify(codec, atLeastOnce()).encode(any(), cap.capture(), any(), any());
        List<DepositData> lines = cap.getValue();
        assertThat(lines).hasSize(1);
        return lines.get(0);
    }

    /** Re-encode the captured records with a clean codec so the bytes are the real wire bytes. */
    private byte[] encodedFile() {
        ArgumentCaptor<DepositHeader> h = ArgumentCaptor.forClass(DepositHeader.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DepositData>> l = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DepositTrailer> t = ArgumentCaptor.forClass(DepositTrailer.class);
        verify(codec, atLeastOnce()).encode(h.capture(), l.capture(), t.capture(), any());
        return new FixedLengthCodec().encode(h.getValue(), l.getValue(), t.getValue());
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

    /** One ASCII-only detail row; only {@code FINC_MDA_DIV} varies per case. */
    private TbPayFileData detail(String fincMdaDiv) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.ONE);
        d.setTarBankCd("01203001");
        d.setFincMdaDiv(fincMdaDiv);
        d.setFincMdaNo("0681000001");
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal("1000.00"));
        d.setBankChnlDiv("T");
        d.setAntDivCd("N");
        d.setSenderNm("Nguyen Van A");
        d.setFincMdaOwnerNm("Nguyen Van B");
        d.setTarAcctRmrk("Thanh toan");
        d.setWdrlActRmrk("Chi phi");
        d.setRcptUseAddr("So 5 Duong Nguyen Hue");
        return d;
    }

    /** Documents the legacy constants this mapping keys on, so the fixture values are not arbitrary. */
    @Test
    @DisplayName("sanity: 'A' and 'C' are the legacy FINC_MDA_DIV domain (WCConst.TAR_FINC_MDA_DIV_*)")
    void fixtureValuesAreTheLegacyDomain() {
        assertThat(com.kosign.wecms.domain.payment.dispatch.constant.BulkTransferConstants.TAR_FINC_MDA_DIV_ACCT)
                .as("legacy WCConst.TAR_FINC_MDA_DIV_ACCT")
                .isEqualTo("A");
        assertThat(com.kosign.wecms.domain.payment.dispatch.constant.BulkTransferConstants.TAR_FINC_MDA_DIV_CARD)
                .as("legacy WCConst.TAR_FINC_MDA_DIV_CARD — the one value that inverts to 'N'")
                .isEqualTo("C");
    }

    /** Not asserted above because the column is {@code NOT NULL}; recorded so the behaviour is known. */
    @Test
    @DisplayName("null FINC_MDA_DIV maps to 'Y' — our null-safety, where legacy NPEs (column is NOT NULL)")
    void nullMediaProducesY() {
        givenDetailWithFincMdaDiv(null);
        dispatch();

        assertThat(depositLine().cardAccountType())
                .as("legacy calls .equals on the raw value and would NPE; TB_PAY_FILE_DATA.FINC_MDA_DIV "
                        + "is NOT NULL so neither path is reachable with real data")
                .isEqualTo("Y");
    }

    /** Kept out of the byte assertions above; here so the record framing is pinned once. */
    @Test
    @DisplayName("the encoded file is H + D + T at 1000 bytes each, CRLF-terminated")
    void recordFraming() {
        givenDetailWithFincMdaDiv("C");
        dispatch();

        byte[] file = encodedFile();
        assertThat(file).hasSize(3000);
        for (int rec = 0; rec < 3; rec++) {
            int end = rec * 1000 + 1000;
            assertThat(new String(file, end - 2, 2, StandardCharsets.UTF_8))
                    .as("record %d must end CRLF", rec)
                    .isEqualTo("\r\n");
        }
    }
}
