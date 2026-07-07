package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
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
    private static final String FILE_NM = "S_" + VAN + "_HGS_" + FILE_SEQ + "_" + TRAN_DT;

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

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        sftpProperties.setLocalDownloadPath(tempDir.toString());
        sftpProperties.setRemoteDownloadPath("rdata");
        service = new BulkExecutionResultService(
                crypto, vanApiKeyService, objectMapper, sftpClient, sftpProperties, codec,
                dispatchKeyGenerator, firmFileMstRepository, firmFileDataRepository,
                firmFileRsltRepository, firmBatApiTranRepository, payFileMstRepository, payFileDataRepository,
                eventPublisher,
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService.class));

        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileRsltRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
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
                OUT_BANK, PRTN, FILE_NM, "2", "3000", "0", null, null, null);
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
        m.setFirmFileNm(FILE_NM);
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
        d.setFirmFileNm(FILE_NM);
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
     * ({@code localDownloadPath/FILE_NM}); the (unstubbed) {@code sftpClient.download}
     * is a no-op, standing in for a successful transfer.
     */
    private void placeResultFile(byte[] bytes) throws Exception {
        Files.write(tempDir.resolve(FILE_NM), bytes);
    }

    @Test
    void happyPath_parses_writesResults_advancesStep2to3_andFinalizesPay() throws Exception {
        TbFirmFileMst mst = mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code());
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                TRAN_DT, FILE_NM, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code())).thenReturn(Optional.of(mst));
        when(firmFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(List.of(mst)); // same ref — after advance it reads PROC_STAT='0'
        when(firmFileDataRepository.findByTranDtAndFileSeqNoAndFirmFileNmOrderBySeqAsc(TRAN_DT, FILE_SEQ_NO, FILE_NM))
                .thenReturn(List.of(firmData(1, 10), firmData(2, 20)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(payData(10), payData(20)));
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(new TbPayFileMst()));
        placeResultFile(resultFile());

        service.handle(envelope());

        // 2 result rows written.
        verify(firmFileRsltRepository, times(2)).save(any());

        // MST advanced 2→3 / stat 0 with trailer aggregates.
        ArgumentCaptor<TbFirmFileMst> mstCap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository).save(mstCap.capture());
        TbFirmFileMst saved = mstCap.getValue();
        assertEquals(ProcStep.DEPO_RES_RECV.code(), saved.getProcStep());
        assertEquals(ProcStat.SUCC.code(), saved.getProcStat());
        assertEquals(new BigDecimal("1"), saved.getSuccCnt());
        assertEquals(new BigDecimal("1"), saved.getUnprocCnt());

        // Pay detail rows reflected + pay master finalized (single sibling, all-success file → '0').
        verify(payFileDataRepository, times(2)).save(any());
        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(payCap.capture());
        assertEquals(ProcStat.SUCC.code(), payCap.getValue().getProcStat());
    }

    @Test
    void noFileAtStep2_isSafeNoOp() throws Exception {
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                anyString(), anyString(), eq(ProcStep.DEPO_REQ.code()), eq(ProcStat.REQ.code())))
                .thenReturn(Optional.empty());

        service.handle(envelope());

        verify(sftpClient, never()).download(anyString(), anyString());
        verify(firmFileRsltRepository, never()).save(any());
        verify(firmFileMstRepository, never()).save(any());
    }

    @Test
    void malformedResultFile_throws_andDoesNotAdvanceState() throws Exception {
        when(firmFileMstRepository.findByTranDtAndFirmFileNmAndProcStepAndProcStat(
                TRAN_DT, FILE_NM, ProcStep.DEPO_REQ.code(), ProcStat.REQ.code()))
                .thenReturn(Optional.of(mst(ProcStep.DEPO_REQ.code(), ProcStat.REQ.code())));
        placeResultFile(new byte[500]); // not a 1000-byte-record multiple → codec rejects

        BusinessException ex = assertThrows(BusinessException.class, () -> service.handle(envelope()));
        assertEquals("WBE_EFS_RESULT_FILE", ex.getCode());

        verify(firmFileRsltRepository, never()).save(any());
        verify(firmFileMstRepository, never()).save(any()); // MST never advanced
    }
}
