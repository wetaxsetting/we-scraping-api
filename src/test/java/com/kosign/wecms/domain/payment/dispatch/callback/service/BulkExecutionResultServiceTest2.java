package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileRslt;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
import com.kosign.wecms.domain.payment.dispatch.file.DepositData;
import com.kosign.wecms.domain.payment.dispatch.file.DepositHeader;
import com.kosign.wecms.domain.payment.dispatch.file.DepositTrailer;
import com.kosign.wecms.domain.payment.dispatch.file.FixedLengthCodec;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmBatApiTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmFileRsltRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileDataRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayFileMstRepository;
import com.kosign.wecms.domain.payment.dispatch.service.DispatchKeyGenerator;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpClient;
import com.kosign.wecms.domain.payment.dispatch.sftp.SftpProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BulkExecutionResultService#handle} — the 01302 result-file
 * callback. Builds a real fixed-length result file with {@link FixedLengthCodec},
 * has the (mock) SFTP client write it to the local download path, and verifies the
 * decode → persist → state-advance chain. Crypto is the real {@link EfsCryptoService}
 * with the published TEST key.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkExecutionResultServiceTest2 {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN = "GV00000007";
    private static final String TRAN_DT = "20260602";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "8";
    private static final String FILE_SEQ = "008";
    private static final String OUT_BANK = "970436";
    private static final String PRTN = "HGSNG";
    /**
     * The name BATCH1 <b>sent</b> — what {@code TB_FIRM_FILE_MST.FIRM_FILE_NM} and every
     * {@code TB_FIRM_FILE_DATA.FIRM_FILE_NM} row actually carry ({@code BulkReceptionService:401,440}).
     */
    private static final String SENT_FILE_NM = "S_" + VAN + "_HGS_" + FILE_SEQ + "_" + TRAN_DT;
    /**
     * The name the bank <b>returns</b> in the 01302 callback — a different leading 2-char prefix.
     * Real UAT samples: {@code receive/R_GV00000001_WRB_001_20251223} (see {@code FixedLengthCodecTest}).
     * Keeping this distinct from {@link #SENT_FILE_NM} is the whole point: the previous single
     * {@code FILE_NM} constant conflated the two, so the H-1 prefix mismatch could never fail a test.
     */
    private static final String RESULT_FILE_NM = "R_" + VAN + "_HGS_" + FILE_SEQ + "_" + TRAN_DT;

    @Mock VanApiKeyService vanApiKeyService;
    @Mock SftpClient sftpClient;
    @Mock DispatchKeyGenerator dispatchKeyGenerator;
    @Mock TbFirmFileMstRepository firmFileMstRepository;
    @Mock TbFirmFileDataRepository firmFileDataRepository;
    @Mock TbFirmFileRsltRepository firmFileRsltRepository;
    @Mock TbFirmBatApiTranRepository firmBatApiTranRepository;
    @Mock TbPayFileMstRepository payFileMstRepository;
    @Mock TbPayFileDataRepository payFileDataRepository;
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final FixedLengthCodec codec = new FixedLengthCodec();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SftpProperties sftpProperties = new SftpProperties();

    private BulkExecutionResultService service;
    /** The audit row {@code DispatchApiTranAuditWriter} committed, so {@code findById} can return it. */
    private TbFirmBatApiTran lastAudit;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sftpProperties.setLocalDownloadPath(tempDir.toString());
        sftpProperties.setRemoteDownloadPath("rdata");
        // C-4: the writes live on their own bean/transaction. Wired here with the same mocks so these
        // tests still drive the whole callback end to end.
        BulkExecutionResultTxService txService = new BulkExecutionResultTxService(
                firmFileMstRepository, firmFileDataRepository, firmFileRsltRepository,
                firmBatApiTranRepository, payFileMstRepository, payFileDataRepository, eventPublisher);
        service = new BulkExecutionResultService(
                crypto, vanApiKeyService, objectMapper, sftpClient, sftpProperties, codec,
                dispatchKeyGenerator, firmFileMstRepository,
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService.class),
                new DispatchApiTranAuditWriter(firmBatApiTranRepository),
                txService);

        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileRsltRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> {
            lastAudit = i.getArgument(0);
            return lastAudit;
        });
        when(firmBatApiTranRepository.findById(any())).thenAnswer(i -> Optional.ofNullable(lastAudit));
        when(payFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /**
     * Stand in for the C-4 atomic claim ({@code TbFirmFileMstRepository.claimExecutionResult}) against a
     * mock repository: report one row updated <b>and apply the advance to {@code mst} itself</b>, exactly
     * as the real {@code UPDATE … SET PROC_STEP='3', PROC_STAT='0'} does to the row the subsequent
     * re-read-by-PK and the sibling scan then observe.
     *
     * <p>Applying it here rather than asserting a {@code save(mst)} is the point of the change: after C-4
     * the {@code 2→3} advance IS the claim, so a test that still expected {@code save} would be testing a
     * code path that no longer exists.
     */
    private void stubClaimSucceeds(TbFirmFileMst mst) {
        when(firmFileMstRepository.claimExecutionResult(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyString()))
                .thenAnswer(i -> {
                    mst.setProcStep(ProcStep.DEPO_RES_RECV.code());
                    mst.setProcStat(ProcStat.SUCC.code());
                    mst.setSuccCnt(i.getArgument(4));
                    mst.setSuccAmt(i.getArgument(5));
                    mst.setUnprocCnt(i.getArgument(6));
                    mst.setUnprocAmt(i.getArgument(7));
                    mst.setProcDttm(i.getArgument(8));
                    return 1;
                });
        when(firmFileMstRepository.findById(any())).thenReturn(Optional.of(mst));
    }

    private byte[] resultFile() {
        DepositHeader h = new DepositHeader(OUT_BANK, PRTN, TRAN_DT, "0071371098911", "9", "1", FILE_SEQ);
        // line 1 success (rs 0000), line 2 failed (rs 0999) — both processed.
        DepositData l1 = new DepositData("1", "0681000001", "VND", "1000", "T", "", "Y", "01203001",
                "", "OWNER1", "", "", "", "0000", "VN", "VND", "", "");
        DepositData l2 = new DepositData("2", "0681000002", "VND", "2000", "T", "", "Y", "01203001",
                "", "OWNER2", "", "", "N", "0999", "VN", "VND", "", "");
        DepositTrailer t = new DepositTrailer("2", "3000", "1", "1000", "1", "2000");
        return codec.encode(h, List.of(l1, l2), t);
    }

    private EfsCallbackEnvelope envelope() throws Exception {
        BulkExecutionResultRequest req = new BulkExecutionResultRequest(
                OUT_BANK, PRTN, RESULT_FILE_NM, "2", "3000", "0", null, null, null);
        String json = objectMapper.writeValueAsString(req);
        String ev = crypto.encryptEv(json, TEST_KEY, "20260602000000");
        String vv = crypto.buildVv(json, TEST_KEY);
        return new EfsCallbackEnvelope(VAN, ev, vv, "AES", "HmacSHA256", "20260602000000", "TNO1", null, null, null);
    }

    private TbFirmFileMst mst(String step, String stat) {
        TbFirmFileMst m = new TbFirmFileMst();
        m.setTranDt(TRAN_DT);
        m.setBizKey(BIZ_KEY);
        m.setFileSeqNo(FILE_SEQ_NO);
        m.setFirmFileNm(SENT_FILE_NM);
        m.setFileSeq(FILE_SEQ);
        m.setOutBankCd(OUT_BANK);
        m.setBankPrtnId(PRTN);
        m.setProcStep(step);
        m.setProcStat(stat);
        return m;
    }

    private TbFirmFileData firmData(int seq, int orgSeq) {
        TbFirmFileData d = new TbFirmFileData();
        d.setTranDt(TRAN_DT);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setFirmFileNm(SENT_FILE_NM);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setBizKey(BIZ_KEY);
        d.setFileSeq(FILE_SEQ);
        d.setOrgSeq(BigDecimal.valueOf(orgSeq));
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("068100000" + seq);
        d.setTrscCcyCd("VND");
        d.setTranAmt(BigDecimal.valueOf(1000L * seq));
        return d;
    }

    private TbPayFileData payData(int seq) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("acct" + seq);
        d.setTrscCcyCd("VND");
        d.setTranAmt(BigDecimal.valueOf(1000));
        d.setBankChnlDiv("T");
        d.setFincMdaOwnerNm("OWNER");
        return d;
    }

    /**
     * Pre-place the result file where {@code downloadResultFile} will read it
     * ({@code localDownloadPath/RESULT_FILE_NM}); the (unstubbed) {@code sftpClient.download}
     * is a no-op, standing in for a successful transfer.
     */
    private void placeResultFile(byte[] bytes) throws Exception {
        Files.write(tempDir.resolve(RESULT_FILE_NM), bytes);
    }

    @Test
    void happyPath_parses_writesResults_advancesStep2to3_andFinalizesPay() throws Exception {
        TbFirmFileMst mst = mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code());
        when(firmFileMstRepository.findForExecutionResult(
                TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code())).thenReturn(Optional.of(mst));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(List.of(mst)); // same ref — after advance it reads PROC_STAT='0'
        when(firmFileDataRepository.findForExecutionResult(TRAN_DT, FILE_SEQ, RESULT_FILE_NM))
                .thenReturn(List.of(firmData(1, 10), firmData(2, 20)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(payData(10), payData(20)));
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(new TbPayFileMst()));
        stubClaimSucceeds(mst);
        placeResultFile(resultFile());

        service.handle(envelope());

        // 2 result rows written.
        verify(firmFileRsltRepository, times(2)).save(any());

        // C-4: MST advanced 2→3 / stat '0' with the trailer aggregates BY THE ATOMIC CLAIM — guarded on
        // the state it leaves, so a concurrent callback that lost the race writes nothing.
        verify(firmFileMstRepository).claimExecutionResult(
                eq(TRAN_DT), eq(BIZ_KEY), eq(FILE_SEQ_NO), eq(SENT_FILE_NM),
                eq(new BigDecimal("1")), eq(new BigDecimal("1000")),
                eq(new BigDecimal("1")), eq(new BigDecimal("2000")), anyString());
        assertEquals(ProcStep.DEPO_RES_RECV.code(), mst.getProcStep());
        assertEquals(ProcStat.SUCC.code(), mst.getProcStat());
        assertEquals(new BigDecimal("1"), mst.getSuccCnt());
        assertEquals(new BigDecimal("1"), mst.getUnprocCnt());

        // Pay detail rows reflected + pay master finalized (single sibling, all-success file → '0').
        verify(payFileDataRepository, times(2)).save(any());
        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(payCap.capture());
        assertEquals(ProcStat.SUCC.code(), payCap.getValue().getProcStat());
    }

    /**
     * H-1 regression. The callback carries the bank's {@code R_…} name; the stored detail rows carry
     * the sent {@code S_…} name. The detail lookup must therefore be the prefix-stripped one (legacy
     * {@code TB_FIRM_FILE_DATA_U001:34}), and per-line results must land on BOTH
     * {@code TB_FIRM_FILE_DATA} and — chained via {@code ORG_SEQ} — {@code TB_PAY_FILE_DATA}.
     *
     * <p>Before the fix this test failed on the pay-detail save: the exact-name lookup returned zero
     * rows, every line took the {@code fd == null} branch, and the file still advanced to
     * {@code PROC_STEP='3'} — the silent-loss signature.
     */
    @Test
    void resultCallbackWithRPrefix_propagatesPerLineResults_toFirmAndPayDetail() throws Exception {
        TbFirmFileMst mst = mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code());
        when(firmFileMstRepository.findForExecutionResult(
                TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()))
                .thenReturn(Optional.of(mst));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(List.of(mst));
        when(firmFileDataRepository.findForExecutionResult(TRAN_DT, FILE_SEQ, RESULT_FILE_NM))
                .thenReturn(List.of(firmData(1, 10), firmData(2, 20)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(payData(10), payData(20)));
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(new TbPayFileMst()));
        stubClaimSucceeds(mst);
        placeResultFile(resultFile());

        service.handle(envelope());

        // The prefix-stripped finder is the one consulted, with the R_ callback name.
        verify(firmFileDataRepository).findForExecutionResult(TRAN_DT, FILE_SEQ, RESULT_FILE_NM);
        // The exact-name finder must NOT be used on the callback path (that is the H-1 defect).
        verify(firmFileDataRepository, org.mockito.Mockito.never())
                .findByTranDtAndFileSeqNoAndFirmFileNmOrderBySeqAsc(anyString(), anyString(), anyString());

        // Per-line outcomes reflected onto the firm detail rows: line 1 = 0000, line 2 = 0999/'N'.
        ArgumentCaptor<TbFirmFileData> fdCap = ArgumentCaptor.forClass(TbFirmFileData.class);
        verify(firmFileDataRepository, times(2)).save(fdCap.capture());
        assertEquals("0000", fdCap.getAllValues().get(0).getRsCd());
        assertEquals("0999", fdCap.getAllValues().get(1).getRsCd());
        assertEquals("N", fdCap.getAllValues().get(1).getProcRsltDiv());

        // ...and chained onto the pay detail rows via ORG_SEQ (10, 20).
        ArgumentCaptor<TbPayFileData> pdCap = ArgumentCaptor.forClass(TbPayFileData.class);
        verify(payFileDataRepository, times(2)).save(pdCap.capture());
        assertEquals("0000", pdCap.getAllValues().get(0).getRsCd());
        assertEquals("0999", pdCap.getAllValues().get(1).getRsCd());
    }

    @Test
    void auditRow_tranDt_isProcessingDate_notFilenameDate() throws Exception {
        // E — legacy API2_act.jsp:85,182: the 01302 audit row's TRAN_DT is the callback PROCESSING
        // date (today), NOT the result-filename date (parts[4] = "20260602", a past date here).
        // The file lookup still keys on the filename date (legacy R006 parity, unchanged).
        TbFirmFileMst mst = mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code());
        when(firmFileMstRepository.findForExecutionResult(
                TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code())).thenReturn(Optional.of(mst));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(List.of(mst));
        when(firmFileDataRepository.findForExecutionResult(TRAN_DT, FILE_SEQ, RESULT_FILE_NM))
                .thenReturn(List.of(firmData(1, 10), firmData(2, 20)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(payData(10), payData(20)));
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(new TbPayFileMst()));
        stubClaimSucceeds(mst);
        placeResultFile(resultFile());

        service.handle(envelope());

        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        ArgumentCaptor<TbFirmBatApiTran> auditCap = ArgumentCaptor.forClass(TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository, org.mockito.Mockito.atLeastOnce()).save(auditCap.capture());
        TbFirmBatApiTran audit = auditCap.getValue();
        assertEquals(today, audit.getTranDt());                                 // processing date (today)
        org.junit.jupiter.api.Assertions.assertNotEquals(TRAN_DT, audit.getTranDt()); // NOT the filename date
        // File lookup still keyed on the filename date (legacy R006 parity).
        verify(firmFileMstRepository).findForExecutionResult(
                TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code());
    }

    /**
     * False "all success" — the guard driven through the real service, not a mirror of its logic.
     *
     * <p>A 01302 lands for file A while sibling B is still stranded at {@code '2'/'6'}. Pre-fix the
     * barrier only looked for {@code PROC_STEP='0'} siblings, so B did not defer, and {@code TOT}
     * counted only terminal rows — giving {@code tot=1 / succ=1} and finalising the shared pay-file to
     * {@code '0'} (ALL SUCCESS) while B had never reported.
     */
    @Test
    void strandedSiblingAtStep2_preventsPayFileFinalisation() throws Exception {
        TbFirmFileMst mst = mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code());
        TbFirmFileMst strandedSibling = mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code());
        strandedSibling.setFirmFileNm("S_" + VAN + "_HGS_009_" + TRAN_DT);

        when(firmFileMstRepository.findForExecutionResult(
                TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()))
                .thenReturn(Optional.of(mst));
        // Same mst ref — after the handler advances it, this reads '3'/'0'; the sibling stays '2'/'6'.
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(List.of(mst, strandedSibling));
        when(firmFileDataRepository.findForExecutionResult(TRAN_DT, FILE_SEQ, RESULT_FILE_NM))
                .thenReturn(List.of(firmData(1, 10), firmData(2, 20)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(payData(10), payData(20)));
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(new TbPayFileMst()));
        stubClaimSucceeds(mst);
        placeResultFile(resultFile());

        service.handle(envelope());

        // A itself still advances — this file DID report.
        assertEquals(ProcStep.DEPO_RES_RECV.code(), mst.getProcStep());
        assertEquals(ProcStat.SUCC.code(), mst.getProcStat());

        // ...but the shared pay-file must NOT be finalised while B has not reported.
        verify(payFileMstRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void noFileAtStep2_isSafeNoOp() throws Exception {
        when(firmFileMstRepository.findForExecutionResult(
                anyString(), anyString(), anyString(), anyString(),
                eq(ProcStep.DEPO_REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());

        service.handle(envelope());

        verify(sftpClient, never()).download(anyString(), anyString());
        verify(firmFileRsltRepository, never()).save(any());
        // C-4: the advance is the claim, so "never advanced" is asserted on the claim.
        verify(firmFileMstRepository, never()).claimExecutionResult(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyString());
    }

    @Test
    void malformedResultFile_throws_andDoesNotAdvanceState() throws Exception {
        when(firmFileMstRepository.findForExecutionResult(
                TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()))
                .thenReturn(Optional.of(mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code())));
        placeResultFile(new byte[500]); // not a 1000-byte-record multiple → codec rejects

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(envelope()));
        assertEquals("WBE_EFS_RESULT_FILE", ex.getCode());

        verify(firmFileRsltRepository, never()).save(any());
        // MST never advanced — and after C-4 the throw happens in the ORCHESTRATOR, before the writer
        // (and its transaction) is entered at all, so the claim is never even attempted.
        verify(firmFileMstRepository, never()).claimExecutionResult(
                anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), anyString());
    }
}
