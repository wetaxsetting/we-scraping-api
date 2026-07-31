package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileData;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C-13 — <b>one</b> ops alert per file carrying non-ASCII bytes, never one per field.
 *
 * <p>The per-field detail is a WARN log; the alert is a single aggregated notification, because the
 * realistic bad case is a whole batch sharing one bad {@code SENDER_NM} (it is resolved once per file
 * from {@code ACCT_MSTR.ACCT_NM}) — one alert per field would mean 500 alerts for one root cause.
 *
 * <p>The alert fires from {@code BulkReceptionService}, not from the codec: {@code OpsAlertService.notify}
 * resolves its API key from the {@code VAN_ORG_CD}, and the codec has no tenant context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkReceptionNonAsciiAlertTest {

    private static final String TRAN_DT = "20260730";
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
    @Mock com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService opsAlertService;
    @Mock OperatingHoursService operatingHoursService;
    @Mock org.springframework.transaction.PlatformTransactionManager perFileTxManager;

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
    }

    @Test
    @DisplayName("an ASCII-only file raises NO non-ASCII alert")
    void asciiOnlyFileRaisesNoAlert() {
        stubDetails(detail(1, "NGUYEN VAN A", "PAYMENT FOR INVOICE"));

        dispatch();

        // The success path fires no ops alert at all, so asserting "never notified" is exact here.
        verify(opsAlertService, never()).notify(anyString(), anyString());
    }

    @Test
    @DisplayName("a file with ONE non-ASCII field raises exactly ONE alert")
    void oneNonAsciiFieldRaisesExactlyOneAlert() {
        stubDetails(detail(1, "한국전자", "PAYMENT FOR INVOICE"));

        dispatch();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService, times(1)).notify(org.mockito.ArgumentMatchers.eq(VAN), body.capture());
        assertThat(body.getValue())
                .contains("non-ASCII bytes on the wire")
                .contains("FIELD_COUNT : 1")
                .contains("receiverName");
    }

    @Test
    @DisplayName("MANY non-ASCII fields across MANY lines still raise exactly ONE alert")
    void manyNonAsciiFieldsStillRaiseOneAlert() {
        // The realistic shape: a bad ACCT_NM / memo repeated down a batch. One root cause, one alert.
        stubDetails(
                detail(1, "한국전자", "円 INVOICE"),
                detail(2, "김철수", "円 INVOICE"),
                detail(3, "한국전자", "円 INVOICE"));

        dispatch();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService, times(1)).notify(anyString(), body.capture());
        // 3 lines x (receiverName + both memo slots) = 9 findings, still ONE notification.
        assertThat(body.getValue()).contains("FIELD_COUNT : 9");
    }

    @Test
    @DisplayName("the alert never contains the offending VALUE — only field / offset / counts")
    void alertNeverContainsTheValue() {
        stubDetails(detail(1, "한국전자", "PAYMENT"));

        dispatch();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService).notify(anyString(), body.capture());
        // These fields carry payer / payee names and memos. The alert goes to an external messaging
        // channel, so the value must stay out of it — offsets are enough to find the row.
        assertThat(body.getValue())
                .doesNotContain("한국전자")
                .contains("offset=");
    }

    @Test
    @DisplayName("the file is still dispatched — detection is observational, not a gate")
    void fileIsStillDispatched() {
        stubDetails(detail(1, "한국전자", "PAYMENT"));

        dispatch();

        // SFTP upload happened and the pay-file was NOT failed: C-13 observes, it does not block.
        verify(sftpClient).upload(anyString(), anyString());
        ArgumentCaptor<TbPayFileMst> pay = ArgumentCaptor.forClass(TbPayFileMst.class);
        verify(payFileMstRepository, org.mockito.Mockito.atLeast(0)).save(pay.capture());
        assertThat(pay.getAllValues())
                .as("no FAIL state may be written for a non-ASCII file")
                .noneMatch(p -> ProcStat.FAIL.code().equals(p.getProcStat()));
    }

    /* ───────────────────────────── helpers ───────────────────────────── */

    private void dispatch() {
        BulkReceptionService spy = spy(service);
        doReturn(null).when(spy).issueReception(any());
        spy.receive(TRAN_DT, BIZ_KEY, FILE_SEQ_NO);
    }

    private void stubDetails(TbPayFileData... details) {
        when(payFileDataRepository.findByBizKeyAndTranDtAndFileSeqNo(BIZ_KEY, TRAN_DT, FILE_SEQ_NO))
                .thenReturn(List.of(details));
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
        c.setRegDttm("20260730000000");
        return c;
    }

    /** {@code ownerNm} lands in receiverName; {@code memo} in BOTH 210-byte memo slots. */
    private TbPayFileData detail(int seq, String ownerNm, String memo) {
        TbPayFileData d = new TbPayFileData();
        d.setTranDt(TRAN_DT);
        d.setBizKey(BIZ_KEY);
        d.setFileSeqNo(FILE_SEQ_NO);
        d.setSeq(BigDecimal.valueOf(seq));
        d.setTarBankCd("01203001");
        d.setFincMdaDiv("Y");
        d.setFincMdaNo("068100000" + seq);
        d.setTrscCcyCd("VND");
        d.setTranAmt(new BigDecimal("1000.00"));
        d.setBankChnlDiv("T");
        d.setAntDivCd("N");
        d.setFincMdaOwnerNm(ownerNm);
        d.setTarAcctRmrk(memo);
        d.setWdrlActRmrk(memo);
        return d;
    }
}
