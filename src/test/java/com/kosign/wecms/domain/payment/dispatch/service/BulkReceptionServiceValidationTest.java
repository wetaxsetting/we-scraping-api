package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BATCH1 pre-flight network/currency validation in {@link BulkReceptionService#receive}
 * (Gate A) — port of legacy {@code FILE_DEPOSIT_TRANSFER_BATCH1.java:113-266} driven by
 * {@code TB_PAY_FILE_DATA_R005}. Uses a REAL {@link BulkTransferValidator}; the R005
 * aggregate is stubbed per case. On failure the file is marked {@code PROC_STAT='3'} +
 * {@code RS_MSG} and skipped (no TB_FIRM_* rows, no SFTP, no EFS).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionServiceValidationTest {

    private static final String TRAN_DT = "20251023";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ_NO = "1";
    private static final String SHINHAN = "970424";
    private static final String WOORI = "970457";
    private static final String BIDV = "970418";
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
    private final BulkTransferValidator validator = new BulkTransferValidator();

    private BulkReceptionService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validator, "shinhanBankCd", SHINHAN);
        ReflectionTestUtils.setField(validator, "wooriBankCd", WOORI);

        service = new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, efsProperties, objectMapper,
                payFileMstRepository, payFileDataRepository, firmConfRepository,
                firmFileMstRepository, firmFileDataRepository, firmBatApiTranRepository,
                dispatchKeyGenerator, codec, sftpClient, sftpProperties, validator);

        when(payFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileMstRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmFileDataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(firmBatApiTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        when(firmFileMstRepository.findByTranDtAndOutBankCdAndBankPrtnId(anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow("9", BIDV))); // default; overridden per test
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(detail(1)));
    }

    /* ---- helpers ---- */

    private TbPayFileMst payRow(String fundTp, String outBank) {
        TbPayFileMst p = new TbPayFileMst();
        p.setTranDt(TRAN_DT);
        p.setBizKey(BIZ_KEY);
        p.setFileSeqNo(FILE_SEQ_NO);
        p.setOutBankCd(outBank);
        p.setWdrlActNo(WDRL);
        p.setBankPrtnId(PRTN);
        p.setFundTp(fundTp);
        p.setTranTp("1");
        p.setProcStat(ProcStat.WAIT.code());
        return p;
    }

    private TbFirmConf conf() {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ_KEY);
        c.setOutBankCd(BIDV);
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

    private TbPayFileData detail(int seq) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("068100000" + seq);
        d.setTrscCcyCd("VND");
        d.setRemitCcyCd("VND");
        d.setTranAmt(new BigDecimal("1000.00"));
        d.setBankChnlDiv("T");
        d.setFincMdaOwnerNm("NGUYEN VAN A");
        return d;
    }

    /** R005 aggregate stub: ebank, ebankOut, vcb, vcbOut, napas, swiftOut, ccy, exchange. */
    private BulkValidationCounts counts(long ebank, long ebankOut, long vcb, long vcbOut,
                                        long napas, long swiftOut, long ccy, long exchange) {
        return new BulkValidationCounts() {
            public Long getEbankCnt()        { return ebank; }
            public Long getEbankOuterCnt()   { return ebankOut; }
            public Long getVcbCnt()          { return vcb; }
            public Long getVcbOuterCnt()     { return vcbOut; }
            public Long getNapasCnt()        { return napas; }
            public Long getSwiftOuterCnt()   { return swiftOut; }
            public Long getCcyCnt()          { return ccy; }
            public Long getExchangeTranCnt() { return exchange; }
        };
    }

    private void arrange(String fundTp, String outBank, BulkValidationCounts c) {
        when(payFileMstRepository.findByTranDtAndBizKeyAndFileSeqNo(TRAN_DT, BIZ_KEY, FILE_SEQ_NO))
                .thenReturn(Optional.of(payRow(fundTp, outBank)));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf()));
        when(payFileDataRepository.aggregateValidationCounts(BIZ_KEY, TRAN_DT, FILE_SEQ_NO)).thenReturn(c);
    }

    private TbPayFileMst assertRejected(String expectedMsg) {
        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(payCap.capture());
        TbPayFileMst saved = payCap.getValue();
        assertThat(saved.getProcStat()).isEqualTo("3");          // PROC_ST_FAIL
        assertThat(saved.getRsMsg()).isEqualTo(expectedMsg);
        verify(firmFileMstRepository, never()).save(any());      // no TB_FIRM_FILE_MST
        verify(firmFileDataRepository, never()).save(any());     // no TB_FIRM_FILE_DATA
        verify(sftpClient, never()).upload(anyString(), anyString()); // no SFTP
        return saved;
    }

    /* ---- failure cases ---- */

    @Test
    void overseas_nonSwiftAntDiv_fails() {
        arrange("S", SHINHAN, counts(0, 0, 0, 0, 0, /*swiftOut*/ 1, 1, 0));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("ANT_DIV_CD value not available for overseas remittance transactions was included.");
    }

    @Test
    void overseas_currencyMix_fails() {
        arrange("S", SHINHAN, counts(0, 0, 0, 0, 0, 0, /*ccy*/ 2, 0));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("Unable to mix remittance currency code.");
    }

    @Test
    void ebank_onNonShinhan_fails() {
        arrange("9", BIDV, counts(/*ebank*/ 1, 0, 0, 0, 0, 0, 1, 0));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("It is a bank that cannot use the EBank network.");
    }

    @Test
    void ebank_mixedWithOtherNetworks_fails() {
        arrange("9", SHINHAN, counts(/*ebank*/ 1, /*ebankOut*/ 1, 0, 0, 0, 0, 1, 0));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("Domestic foreign currency remittance cannot be mixed with other bank codes.");
    }

    @Test
    void vcb_onNonWoori_fails() {
        arrange("9", BIDV, counts(0, 0, /*vcb*/ 1, 0, 0, 0, 1, 0));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("It is a bank that cannot use the VCB network.");
    }

    @Test
    void vcb_mixedWithOtherNetworks_fails() {
        arrange("9", WOORI, counts(0, 0, /*vcb*/ 1, /*vcbOut*/ 1, 0, 0, 1, 0));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("Domestic foreign currency remittance cannot be mixed with other bank codes.");
    }

    @Test
    void woori_withNapas_fails() {
        arrange("9", WOORI, counts(0, 0, 0, 0, /*napas*/ 1, 0, 1, 0));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("No Napas transactions at Woori Bank.");
    }

    @Test
    void woori_withExchangeTransaction_fails() {
        arrange("9", WOORI, counts(0, 0, 0, 0, 0, 0, 1, /*exchange*/ 1));
        service.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
        assertRejected("We can't do currency exchange transfer transactions at Woori Bank.");
    }

    /* ---- valid case ---- */

    @Test
    void validSingleNetworkSingleCurrency_proceedsToBuildAndUpload() {
        arrange("9", BIDV, counts(0, 0, 0, 0, 0, 0, /*ccy*/ 1, 0));

        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);

        // File built (TB_FIRM_FILE_MST written), uploaded, EFS 301 issued.
        verify(firmFileMstRepository).save(any());
        verify(sftpClient).upload(anyString(), anyString());
        verify(spy).issueReception(any());

        // Pay-file marked in-process ('6'), not failed.
        ArgumentCaptor<TbPayFileMst> payCap = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository).save(payCap.capture());
        assertThat(payCap.getValue().getProcStat()).isEqualTo(ProcStat.REQ.code());
    }
}
