package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.repository.BulkValidationCounts;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Split-file <b>total reconciliation</b> — the per-file trailer counts/amounts must sum back to the
 * pre-split batch total, with every detail row emitted exactly once.
 *
 * <p>Closes the gap carried in
 * {@code docs/testing/integration-test-audit.md} §6.4 / §11 row 4 ("split trailer totals never
 * reconciled against the pre-split batch total") and {@code docs/testing/missing-tests.md} P2-3.
 * {@code BulkReceptionReceiveTest.eachSplitFile_hasCorrectHeaderDataTrailerCount} checks each file
 * <i>in isolation</i>; nothing asserted the cross-file sum, which is the classic bulk defect (a
 * dropped or double-counted line at a partition edge).
 *
 * <h2>Legacy source of truth — {@code FILE_DEPOSIT_TRANSFER_BATCH1}</h2>
 *
 * Read end-to-end at
 * {@code WECMS_BATCH DEV/source/WECMS_BATCH/WECMS_BATCH/src/batch/FILE_DEPOSIT_TRANSFER_BATCH1.java}:
 *
 * <ol>
 *   <li><b>:463-467</b> {@code calculateBatchDivision(BAT_TRAN_BRANCH_DIV, REQ_CNT,
 *       BAT_TRAN_BRANCH_LIMIT)} (body at {@code :932-961}) →
 *       {@code loopCnt = ceil(REQ_CNT / limit)}, {@code dataCnt = limit} when
 *       {@code DIV='Y' && REQ_CNT > limit}; otherwise {@code loopCnt=1, dataCnt=REQ_CNT}.</li>
 *   <li><b>:470-471, :506-507, :525-526</b> the window is a {@code SEQ} <i>value</i> range —
 *       {@code startSeq=1, endSeq=dataCnt}, both advanced by {@code dataCnt} per iteration — passed
 *       to {@code TB_PAY_FILE_DATA_R001} / {@code _R002} as {@code START_SEQ}/{@code END_SEQ}. Both
 *       IDOs bind it as {@code AND SEQ BETWEEN :START_SEQ AND :END_SEQ}
 *       ({@code WECMS_BATCH_ETC/xml/service/IDO/IDO.TB_PAY_FILE_DATA_R001.xml},
 *       {@code IDO.TB_PAY_FILE_DATA_R002.xml}).</li>
 *   <li><b>:552-555</b> the per-file {@code TB_FIRM_FILE_MST.REQ_CNT}/{@code REQ_AMT} are the
 *       {@code R002} aggregate over that same window
 *       ({@code count(*) AS FILE_COUNT, coalesce(sum(TRAN_AMT),'0') AS FILE_AMT}).</li>
 *   <li><b>:594-687</b> the trailer is accumulated by iterating the {@code R001} rows of the window:
 *       {@code tempSeq += 1} and {@code tempAmt = tempAmt.add(new BigDecimal(amtStr))}, then
 *       {@code deposit_trailer.setRequestCnt(tempSeq)} / {@code setRequestAmt(tempAmt.toPlainString())}
 *       ({@code :684-686}).</li>
 * </ol>
 *
 * Because {@code loopCnt * dataCnt >= REQ_CNT} and the windows are contiguous and disjoint, the union
 * of the windows covers {@code SEQ ∈ [1, loopCnt*dataCnt]}. For the dense {@code 1..N} run the
 * refactor allocates, that is exactly the whole batch, once — so legacy's own invariant is
 * <b>Σ per-file trailer == the pre-split total</b>, and per file
 * {@code MST.REQ_CNT == trailer.requestCnt} / {@code MST.REQ_AMT == trailer.requestAmt}. These tests
 * pin that invariant on the refactor ({@code BulkReceptionService.buildAndDispatchAllFiles:387-402}
 * builds the same value-range windows; {@code buildAndDispatchFile:488-493} derives one
 * {@code reqCnt}/{@code reqAmt} pair per window and feeds it to the MST row, the trailer <i>and</i>
 * the 00301 audit + request).
 *
 * <p>The sparse-{@code SEQ} counterpart (rows outside every window are legitimately not sent, so the
 * sum is deliberately {@code <} the row count) is pinned separately below — it is the same legacy
 * value-range rule that
 * {@code BulkReceptionReceiveTest.split_partitionsBySeqValueRange_notListIndex_nonOneStart} covers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkSplitTotalReconciliationTest {

    private static final String TRAN_DT = "20251023";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "1";
    private static final String OUT_BANK = "970457";
    private static final String WDRL = "100001020691";
    private static final String PRTN = "WRB001";
    private static final String VAN = "GV00000001";

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
    @Mock OpsAlertService opsAlertService;
    @Mock OperatingHoursService operatingHoursService;
    /** Mock tx manager makes the per-split {@code REQUIRES_NEW} template a pass-through. */
    @Mock org.springframework.transaction.PlatformTransactionManager perFileTxManager;

    private final EfsCryptoService crypto = new EfsCryptoService();
    /** Spy so every per-file {@code encode(header, lines, trailer)} can be captured. */
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
        when(payFileDataRepository.aggregateValidationCounts(anyString(), anyString(), anyString()))
                .thenReturn(org.mockito.Mockito.mock(BulkValidationCounts.class));
        // Keep the local staging file out of the project tree.
        sftpProperties.setLocalUploadPath(System.getProperty("java.io.tmpdir"));

        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow()));
    }

    /* ─────────────────────────── fixtures ─────────────────────────── */

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

    private TbFirmConf conf(String branchDiv, int limit) {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setOutBankCd(OUT_BANK);
        c.setWdrlActNo(WDRL);
        c.setBankPrtnId(PRTN);
        c.setVanOrgCd(VAN);
        c.setFileType("WRB");
        c.setOrgStat("1");
        c.setBatTranBranchDiv(branchDiv);
        c.setBatTranBranchLimit(BigDecimal.valueOf(limit));
        c.setRegDttm("20251023000000");
        return c;
    }

    private TbPayFileData detail(int seq, String amt) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo(String.format("06810%05d", seq));
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal(amt));
        d.setBankChnlDiv("T");
        d.setAntDivCd("C");
        d.setFincMdaOwnerNm("NGUYEN VAN A");
        return d;
    }

    /** Dense {@code SEQ} run {@code 1..n} with distinct scale-2 amounts. */
    private List<TbPayFileData> details(int n) {
        List<TbPayFileData> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            list.add(detail(i, (i * 1000) + ".0" + (i % 10)));
        }
        return list;
    }

    private void stub(String branchDiv, int limit, List<TbPayFileData> rows) {
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf(branchDiv, limit)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(rows);
    }

    private BulkReceptionService run() {
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        return spy;
    }

    /* ─────────────────────────── capture helpers ─────────────────────────── */

    private List<DepositTrailer> trailers(int files) {
        ArgumentCaptor<DepositHeader> h = ArgumentCaptor.forClass(DepositHeader.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DepositData>> l = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DepositTrailer> t = ArgumentCaptor.forClass(DepositTrailer.class);
        verify(codec, times(files)).encode(h.capture(), l.capture(), t.capture(), any());
        return t.getAllValues();
    }

    @SuppressWarnings("unchecked")
    private List<List<DepositData>> dataLines(int files) {
        ArgumentCaptor<DepositHeader> h = ArgumentCaptor.forClass(DepositHeader.class);
        ArgumentCaptor<List<DepositData>> l = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<DepositTrailer> t = ArgumentCaptor.forClass(DepositTrailer.class);
        verify(codec, times(files)).encode(h.capture(), l.capture(), t.capture(), any());
        return l.getAllValues();
    }

    private List<TbFirmFileMst> mstRows(int files) {
        ArgumentCaptor<TbFirmFileMst> cap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository, times(files)).save(cap.capture());
        return cap.getAllValues();
    }

    private List<TbFirmFileData> firmDataRows() {
        ArgumentCaptor<TbFirmFileData> cap = ArgumentCaptor.forClass(TbFirmFileData.class);
        verify(firmFileDataRepository, atLeastOnce()).save(cap.capture());
        return cap.getAllValues();
    }

    /**
     * Distinct {@code TB_FIRM_BAT_API_TRAN} rows — one per split file. The 00301 row is saved twice
     * per file (once before the call, once with the RC after it) but it is the <i>same</i> instance,
     * so identity de-duplication yields exactly one row per file.
     */
    private Collection<TbFirmBatApiTran> auditRows() {
        ArgumentCaptor<TbFirmBatApiTran> cap = ArgumentCaptor.forClass(TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository, atLeastOnce()).save(cap.capture());
        return new LinkedHashSet<>(cap.getAllValues());
    }

    private List<BulkReceptionApiCommand> receptionCommands(BulkReceptionService spy, int files) {
        ArgumentCaptor<BulkReceptionApiCommand> cap =
                ArgumentCaptor.forClass(BulkReceptionApiCommand.class);
        verify(spy, times(files)).issueReception(cap.capture());
        return cap.getAllValues();
    }

    /** Reassemble the 00301 wire amount from its legacy {@code data_amt} + {@code data_dcm_amt} split. */
    private static BigDecimal wireAmount(BulkReceptionApiCommand cmd) {
        String dcm = cmd.dataDcmAmt();
        if (com.kosign.wecms.domain.payment.dispatch.van.AmountSplitter.WHOLE_AMOUNT_DCM.equals(dcm)) {
            return new BigDecimal(cmd.dataAmt());
        }
        return new BigDecimal(cmd.dataAmt() + "." + dcm);
    }

    private static BigDecimal sum(List<TbPayFileData> rows) {
        return rows.stream().map(TbPayFileData::getTranAmt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /* ─────────────────────────── the reconciliation ─────────────────────────── */

    /**
     * The core property, exercised across the split boundary: exact multiples of the limit, one over,
     * one under, limit 1 (one file per row), and the no-split degenerate case.
     *
     * <p>Asserts, for each configuration:
     * <ul>
     *   <li>the file count is legacy's {@code ceil(n/limit)};</li>
     *   <li>Σ trailer {@code requestCnt} == {@code n} and Σ trailer {@code requestAmt} == Σ
     *       {@code TRAN_AMT} (the pre-split total);</li>
     *   <li>Σ {@code TB_FIRM_FILE_MST.REQ_CNT}/{@code REQ_AMT} == the same totals (legacy R002
     *       aggregate over the same window);</li>
     *   <li>Σ {@code TB_FIRM_BAT_API_TRAN.BAT_CNT}/{@code BAT_AMT} (00301 audit) == the same totals;</li>
     *   <li>Σ 00301 request {@code data_cnt} / {@code data_amt}+{@code data_dcm_amt} == the same
     *       totals — i.e. what actually reaches the bank reconciles too;</li>
     *   <li>every original {@code SEQ} is emitted <b>exactly once</b> across all files (no dropped or
     *       double-counted line at a partition edge), in both the D records and
     *       {@code TB_FIRM_FILE_DATA}.</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0} rows, limit {1} -> {2} file(s)")
    @CsvSource({
            "1,  3, 1",   // below limit — no split
            "3,  3, 1",   // exactly at the limit — legacy requires REQ_CNT > limit to split
            "4,  3, 2",   // one over
            "6,  3, 2",   // exact multiple
            "7,  3, 3",   // one over a multiple
            "9,  3, 3",
            "10, 3, 4",
            "11, 5, 3",
            "4,  1, 4",   // limit 1 — one file per row
            "5,  2, 3",   // the shape the existing per-file tests use
    })
    void splitFiles_trailerCountsAndAmountsSumToOriginal(int rows, int limit, int expectedFiles) {
        List<TbPayFileData> all = details(rows);
        BigDecimal totalAmt = sum(all);
        stub("Y", limit, all);

        BulkReceptionService spy = run();

        // Legacy calculateBatchDivision:946-958.
        assertEquals(expectedFiles, mstRows(expectedFiles).size(),
                "file count must be legacy ceil(reqCnt/limit)");

        // 1. Trailer totals (legacy :684-686 tempSeq / tempAmt).
        List<DepositTrailer> trailers = trailers(expectedFiles);
        int trailerCnt = 0;
        BigDecimal trailerAmt = BigDecimal.ZERO;
        for (DepositTrailer t : trailers) {
            trailerCnt += Integer.parseInt(t.requestCnt());
            trailerAmt = trailerAmt.add(new BigDecimal(t.requestAmt()));
        }
        assertEquals(rows, trailerCnt, "Σ trailer requestCnt must equal the pre-split row count");
        assertEquals(0, totalAmt.compareTo(trailerAmt),
                "Σ trailer requestAmt must equal the pre-split total amount");

        // 2. TB_FIRM_FILE_MST REQ_CNT / REQ_AMT (legacy :552-553, the R002 aggregate).
        int mstCnt = 0;
        BigDecimal mstAmt = BigDecimal.ZERO;
        for (TbFirmFileMst m : mstRows(expectedFiles)) {
            mstCnt += m.getReqCnt().intValueExact();
            mstAmt = mstAmt.add(m.getReqAmt());
        }
        assertEquals(rows, mstCnt, "Σ MST REQ_CNT must equal the pre-split row count");
        assertEquals(0, totalAmt.compareTo(mstAmt), "Σ MST REQ_AMT must equal the pre-split total");

        // 3. 00301 audit ledger BAT_CNT / BAT_AMT.
        int batCnt = 0;
        BigDecimal batAmt = BigDecimal.ZERO;
        Collection<TbFirmBatApiTran> audits = auditRows();
        assertEquals(expectedFiles, audits.size(), "one 00301 audit row per split file");
        for (TbFirmBatApiTran a : audits) {
            batCnt += a.getBatCnt().intValueExact();
            batAmt = batAmt.add(a.getBatAmt());
        }
        assertEquals(rows, batCnt, "Σ audit BAT_CNT must equal the pre-split row count");
        assertEquals(0, totalAmt.compareTo(batAmt), "Σ audit BAT_AMT must equal the pre-split total");

        // 4. What actually leaves for the bank: the 00301 request header totals.
        int wireCnt = 0;
        BigDecimal wireAmt = BigDecimal.ZERO;
        for (BulkReceptionApiCommand cmd : receptionCommands(spy, expectedFiles)) {
            wireCnt += Integer.parseInt(cmd.dataCnt());
            wireAmt = wireAmt.add(wireAmount(cmd));
        }
        assertEquals(rows, wireCnt, "Σ 00301 data_cnt must equal the pre-split row count");
        assertEquals(0, totalAmt.compareTo(wireAmt), "Σ 00301 amount must equal the pre-split total");

        // 5. Every row emitted exactly once — no partition-edge drop or duplicate.
        List<String> emittedAccts = dataLines(expectedFiles).stream()
                .flatMap(List::stream)
                .map(DepositData::depositAccountNumber)
                .toList();
        assertEquals(rows, emittedAccts.size(), "D-record count must equal the pre-split row count");
        assertEquals(rows, new LinkedHashSet<>(emittedAccts).size(),
                "no account may appear in two split files");
        assertEquals(
                all.stream().map(TbPayFileData::getFincMdaNo).sorted().toList(),
                emittedAccts.stream().sorted().toList(),
                "the union of the split windows must be exactly the original row set");

        // ...and the same holds for the persisted TB_FIRM_FILE_DATA rows (ORG_SEQ carries the
        // pre-split SEQ, so this is the DB-side twin of the assertion above).
        List<Long> orgSeqs = firmDataRows().stream()
                .map(d -> d.getOrgSeq().longValue())
                .sorted()
                .toList();
        assertEquals(all.stream().map(d -> d.getSeq().longValue()).sorted().toList(), orgSeqs,
                "Σ TB_FIRM_FILE_DATA ORG_SEQ must be exactly the original SEQ set, once each");
    }

    /**
     * Decimal precision survives the split. Legacy accumulates with {@code BigDecimal.add} and emits
     * {@code toPlainString()} ({@code :685-686}), so cent-level values must reconcile exactly — a
     * {@code double}/{@code long} accumulator would drift here.
     */
    @Test
    @DisplayName("cent-level amounts reconcile exactly across split files (BigDecimal, not double)")
    void splitFiles_centLevelAmounts_reconcileExactly() {
        List<TbPayFileData> all = List.of(
                detail(1, "0.01"), detail(2, "0.02"), detail(3, "0.03"),
                detail(4, "1234567.89"), detail(5, "0.05"), detail(6, "99999999.99"),
                detail(7, "0.07"));
        BigDecimal totalAmt = sum(all);           // 101234568.06
        stub("Y", 2, all);                        // ceil(7/2) = 4 files

        BulkReceptionService spy = run();

        BigDecimal trailerAmt = BigDecimal.ZERO;
        int trailerCnt = 0;
        for (DepositTrailer t : trailers(4)) {
            trailerCnt += Integer.parseInt(t.requestCnt());
            trailerAmt = trailerAmt.add(new BigDecimal(t.requestAmt()));
        }
        assertEquals(7, trailerCnt);
        assertEquals(0, totalAmt.compareTo(trailerAmt),
                "expected " + totalAmt.toPlainString() + " got " + trailerAmt.toPlainString());

        BigDecimal wireAmt = BigDecimal.ZERO;
        for (BulkReceptionApiCommand cmd : receptionCommands(spy, 4)) {
            wireAmt = wireAmt.add(wireAmount(cmd));
        }
        assertEquals(0, totalAmt.compareTo(wireAmt),
                "the 00301 integer/decimal split must reassemble to the pre-split total");
    }

    /**
     * Null {@code TRAN_AMT} rows contribute zero and are still counted — legacy {@code :645-655}
     * guards {@code amtStr != null && !isEmpty()} before {@code tempAmt.add(...)} but always does
     * {@code tempSeq += 1}, so the count includes the row while the amount does not.
     */
    @Test
    @DisplayName("null TRAN_AMT contributes 0 to the amount but is still counted (legacy :645-655)")
    void splitFiles_nullAmountRow_countedButAddsZero() {
        TbPayFileData nullAmt = detail(2, "0");
        nullAmt.setTranAmt(null);
        List<TbPayFileData> all = List.of(detail(1, "1000.00"), nullAmt, detail(3, "3000.00"));
        stub("Y", 2, all);                       // ceil(3/2) = 2 files

        run();

        int trailerCnt = 0;
        BigDecimal trailerAmt = BigDecimal.ZERO;
        for (DepositTrailer t : trailers(2)) {
            trailerCnt += Integer.parseInt(t.requestCnt());
            trailerAmt = trailerAmt.add(new BigDecimal(t.requestAmt()));
        }
        assertEquals(3, trailerCnt, "the null-amount row still counts toward requestCnt");
        assertEquals(0, new BigDecimal("4000.00").compareTo(trailerAmt),
                "the null-amount row must contribute exactly zero");
    }

    /**
     * The no-split path is the degenerate {@code loopCnt=1} case and must reconcile trivially — one
     * file carrying the whole batch. Guards against a regression that would make the single-file path
     * disagree with the batch total.
     */
    @Test
    @DisplayName("no-split path: the single file's trailer IS the pre-split total")
    void noSplit_singleFileTrailerEqualsTotal() {
        List<TbPayFileData> all = details(7);
        BigDecimal totalAmt = sum(all);
        stub("N", 2, all);                       // DIV='N' → no split regardless of the limit

        run();

        List<DepositTrailer> trailers = trailers(1);
        assertEquals("7", trailers.get(0).requestCnt());
        assertEquals(0, totalAmt.compareTo(new BigDecimal(trailers.get(0).requestAmt())));
        assertEquals(0, totalAmt.compareTo(mstRows(1).get(0).getReqAmt()));
    }

    /**
     * <b>Legacy value-range semantics, stated as a deliberate non-invariant.</b> When {@code SEQ} is
     * sparse or does not start at 1, the union of the windows
     * {@code [1,limit], [limit+1,2*limit], … [(loopCnt-1)*limit+1, loopCnt*limit]} can miss rows —
     * legacy's {@code SEQ BETWEEN :START_SEQ AND :END_SEQ} simply does not select them, so they are
     * never sent and never counted. The reconciliation invariant above therefore holds against the
     * <i>windowed</i> set, not the raw row list.
     *
     * <p>Pinned so the sparse case is a documented legacy behaviour rather than a silently-tolerated
     * gap: {@code SEQ {1, 2, 99}} with limit 2 → {@code loopCnt=2}, windows {@code [1,2]} and
     * {@code [3,4]} → row 99 falls outside both. Σ trailer counts is 2, not 3.
     */
    @Test
    @DisplayName("sparse SEQ: rows outside every value window are not sent (legacy BETWEEN parity)")
    void sparseSeq_rowsOutsideEveryWindow_areNotSentAndNotCounted() {
        List<TbPayFileData> all = List.of(
                detail(1, "1000.00"), detail(2, "2000.00"), detail(99, "9900.00"));
        stub("Y", 2, all);                       // reqCnt 3, limit 2 → loopCnt 2, windows [1,2] [3,4]

        run();

        // Window [3,4] matches nothing, so only one file carries data; the second file is empty.
        List<DepositTrailer> trailers = trailers(2);
        int trailerCnt = 0;
        BigDecimal trailerAmt = BigDecimal.ZERO;
        for (DepositTrailer t : trailers) {
            trailerCnt += Integer.parseInt(t.requestCnt());
            trailerAmt = trailerAmt.add(new BigDecimal(t.requestAmt()));
        }
        assertEquals(2, trailerCnt, "SEQ 99 is outside every legacy value window — not sent");
        assertEquals(0, new BigDecimal("3000.00").compareTo(trailerAmt));
        assertTrue(firmDataRows().stream().noneMatch(d -> d.getOrgSeq().longValue() == 99L),
                "SEQ 99 must not be persisted into any firm file");
    }
}
