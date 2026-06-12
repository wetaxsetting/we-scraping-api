package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BulkReceptionService#receive} — the BATCH1 (00301) request-time
 * flow. The {@code issueReception} API leg is stubbed via a Mockito spy so these
 * tests exercise the orchestration (validate → file build → MST/DATA insert →
 * SFTP → audit → state guarantees) without the EFS gateway / crypto.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionReceiveTest {

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

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final FixedLengthCodec codec = new FixedLengthCodec();
    private final SftpProperties sftpProperties = new SftpProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private BulkReceptionService service;

    @BeforeEach
    void setUp() {
        service = new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, objectMapper,
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, codec, sftpClient, sftpProperties);

        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());
    }

    private TbPayFileMst payRow(String procStat) {
        TbPayFileMst p = new TbPayFileMst();
        p.setTranDt(TRAN_DT);
        p.setBizKey(BIZ_KEY);
        p.setFileSeqNo(FILE_SEQ_NO);
        p.setOutBankCd(OUT_BANK);
        p.setWdrlActNo(WDRL);
        p.setBankPrtnId(PRTN);
        p.setFundTp("0");
        p.setTranTp("1");
        p.setProcStat(procStat);
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
        d.setFincMdaNo("068100000" + seq);
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal(amt));
        d.setBankChnlDiv("T");
        d.setFincMdaOwnerNm("NGUYEN VAN A");
        return d;
    }

    @Test
    void happyPath_insertsFileAtStep0Stat6_uploads_auditsBeforeCall_andDoesNotAdvance() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("N", 2000)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1, "1000.00"), detail(2, "2000.00")));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());

        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // FIRM_FILE_MST created at PROC_STEP=0 / PROC_STAT=6 and NOT advanced.
        ArgumentCaptor<TbFirmFileMst> mstCap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository).save(mstCap.capture());
        TbFirmFileMst saved = mstCap.getValue();
        assertEquals(ProcStep.REQ.code(), saved.getProcStep());
        assertEquals(ProcStat.REQ.code(), saved.getProcStat());
        assertEquals("S_" + VAN + "_WRB_001_" + TRAN_DT, saved.getFirmFileNm());
        assertEquals(new BigDecimal("2"), saved.getReqCnt());

        // Pay-file marked in-process ('6'), not failed.
        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(payCap.capture());
        assertEquals(ProcStat.REQ.code(), payCap.getValue().getProcStat());

        // Two detail rows persisted; upload + API issued.
        verify(firmFileDataRepository, org.mockito.Mockito.times(2)).save(any());
        verify(sftpClient).upload(anyString(), anyString());
        verify(spy).issueReception(any());

        // Audit written BEFORE the call (00301) then updated with the result → 2 saves.
        ArgumentCaptor<com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran> auditCap =
                ArgumentCaptor.forClass(com.kosign.wecms.domain.payment.dispatch.entity.TbFirmBatApiTran.class);
        verify(firmBatApiTranRepository, org.mockito.Mockito.times(2)).save(auditCap.capture());
        assertEquals(com.kosign.wecms.domain.payment.dispatch.enums.ApiTranTp.BULK_RECEPTION.code(),
                auditCap.getAllValues().get(0).getApiTranTp());
    }

    @Test
    void duplicate_payNotWait_isSafeNoOp() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.REQ.code()))); // already '6'

        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        verify(payFileMstRepository, never()).save(any());
        verify(firmFileMstRepository, never()).save(any());
        verify(sftpClient, never()).upload(anyString(), anyString());
    }

    @Test
    void sftpUploadFailure_marksBothFailed_andDoesNotCallApi() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("N", 2000)));
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1, "1000.00")));
        doThrow(new RuntimeException("sftp down")).when(sftpClient).upload(anyString(), anyString());

        BulkReceptionService spy = spy(service);
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // FIRM_FILE_MST ends FAIL (still step 0, never advanced); pay ends FAIL.
        ArgumentCaptor<TbFirmFileMst> mstCap = ArgumentCaptor.forClass(TbFirmFileMst.class);
        verify(firmFileMstRepository, org.mockito.Mockito.atLeastOnce()).save(mstCap.capture());
        TbFirmFileMst last = mstCap.getValue();
        assertEquals(ProcStep.REQ.code(), last.getProcStep());
        assertEquals(ProcStat.FAIL.code(), last.getProcStat());

        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository, org.mockito.Mockito.atLeastOnce()).save(payCap.capture());
        assertEquals(ProcStat.FAIL.code(), payCap.getValue().getProcStat());

        verify(spy, never()).issueReception(any());
    }

    @Test
    void branchSplitRequired_throws_andPersistsNothing() {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(ProcStat.WAIT.code())));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("Y", 1))); // limit 1
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1, "1000.00"), detail(2, "2000.00"))); // 2 > 1

        assertThrows(UnsupportedOperationException.class,
                () -> service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO));

        verify(firmFileMstRepository, never()).save(any());
        verify(payFileMstRepository, never()).save(any());
    }
}
