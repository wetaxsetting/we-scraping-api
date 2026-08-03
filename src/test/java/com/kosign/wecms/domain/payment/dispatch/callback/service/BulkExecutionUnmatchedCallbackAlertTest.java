package com.kosign.wecms.domain.payment.dispatch.callback.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.BulkExecutionResultRequest;
import com.kosign.wecms.domain.payment.dispatch.callback.dto.EfsCallbackEnvelope;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmFileMst;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStat;
import com.kosign.wecms.domain.payment.dispatch.enums.ProcStep;
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
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The 01302 unmatched-callback escalation.
 *
 * <p>{@code findForExecutionResult} ({@code PROC_STEP='2'/PROC_STAT='6'}) missing collapses four
 * conditions into one branch. Three of them leave a row behind and are swept by
 * {@code StuckPayFileMonitorService}; the fourth — <b>no row at all for this
 * {@code (TRAN_DT, FIRM_FILE_NM)}</b> — leaves no state anywhere and no audit row is possible
 * ({@code TB_FIRM_BAT_API_TRAN.BIZ_KEY} is a {@code NOT NULL} PK column with no resolvable tenant),
 * so before the escalation it was the only unmatched-callback condition in the dispatch tree with no
 * operator-visible signal. It now logs at {@code error} and raises an ops alert, matching the 01301
 * twin {@code BulkReceptionResultService.handleUnmatchedCallback:560-582}.
 *
 * <p><b>Both tests also pin {@code RC=0000}.</b> The escalation changed the signal, never the wire.
 * Legacy answers {@code G999} here ({@code FILE_DEPOSIT_TRANSFER_API2_act.jsp:163-169}); that
 * divergence is deferred pending §10 q1 and these assertions exist so a later half-applied flip
 * fails the build rather than shipping. See
 * {@code docs/audits/van-center-open-questions-verification-2026-08-01.md}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BulkExecutionUnmatchedCallbackAlertTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN = "GV00000007";
    private static final String TRAN_DT = "20260602";
    private static final String BIZ_KEY = "VN_VNM0004_000001";
    private static final String FILE_SEQ = "008";
    private static final String OUT_BANK = "970436";
    private static final String PRTN = "HGSNG";
    private static final String TNO = "TNO1";
    /** The name BATCH1 sent — what {@code TB_FIRM_FILE_MST.FIRM_FILE_NM} carries. */
    private static final String SENT_FILE_NM = "S_" + VAN + "_HGS_" + FILE_SEQ + "_" + TRAN_DT;
    /** The name the bank returns in the callback — different leading 2-char prefix. */
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
    @Mock OpsAlertService opsAlertService;

    private final EfsCryptoService crypto = new EfsCryptoService();
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
                crypto, vanApiKeyService, objectMapper, sftpClient, sftpProperties,
                new FixedLengthCodec(), dispatchKeyGenerator,
                firmFileMstRepository, firmFileDataRepository, firmFileRsltRepository,
                firmBatApiTranRepository, payFileMstRepository, payFileDataRepository,
                eventPublisher, opsAlertService,
                new DispatchApiTranAuditWriter(firmBatApiTranRepository));

        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
        when(dispatchKeyGenerator.nextTranSeq(anyString(), anyString())).thenReturn("1");
        // The '2'/'6' lookup misses in every scenario here — that is the branch under test.
        when(firmFileMstRepository.findForExecutionResult(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void alertsAndLogsWhenNoRowExistsForTheCallbackFileName() throws Exception {
        // Case (c): nothing in TB_FIRM_FILE_MST matches this file on this date, in any state.
        when(firmFileMstRepository.findAnyStateForExecutionResult(TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ))
                .thenReturn(List.of());

        EfsEnvelope ack = service.handle(envelope());

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(opsAlertService, times(1)).notify(eq(VAN), body.capture());

        String msg = body.getValue();
        assertTrue(msg.contains(TRAN_DT), "alert must name the TRAN_DT the lookup used");
        assertTrue(msg.contains(RESULT_FILE_NM), "alert must name the callback file name");
        assertTrue(msg.contains(VAN), "alert must name the envelope ID");
        assertTrue(msg.contains(TNO), "alert must name the envelope TNO");
        // Never the payload: the decrypted EV carries payee names and amounts.
        assertTrue(!msg.contains("3000"), "alert must not carry the decrypted payload");

        // The wire is deliberately unchanged — see the class Javadoc.
        assertEquals("0000", ack.rc(), "unmatched 01302 still acks 0000 pending VAN Center q1");
    }

    @Test
    void doesNotAlertOnGenuineReplayOfAnAlreadyProcessedFile() throws Exception {
        // Case (a): the file advanced to PROC_STEP='3', so the '2'/'6' lookup misses but the row is
        // right there. A replay is routine — escalating it would make the alert channel useless.
        // Note the stored row carries the SENT S_… name while the callback carries R_… — the probe
        // matches them prefix-insensitively, which is exactly what this test pins.
        when(firmFileMstRepository.findAnyStateForExecutionResult(TRAN_DT, RESULT_FILE_NM, VAN, FILE_SEQ))
                .thenReturn(List.of(mstAt(ProcStep.DEPO_RES_RECV.code(), ProcStat.SUCC.code())));

        EfsEnvelope ack = service.handle(envelope());

        verify(opsAlertService, never()).notify(anyString(), anyString());
        assertEquals("0000", ack.rc(), "a genuine replay acks 0000 so the gateway stops resending");
    }

    private EfsCallbackEnvelope envelope() throws Exception {
        BulkExecutionResultRequest req = new BulkExecutionResultRequest(
                OUT_BANK, PRTN, RESULT_FILE_NM, "2", "3000", "0", null, null, null);
        String json = objectMapper.writeValueAsString(req);
        return new EfsCallbackEnvelope(
                VAN,
                crypto.encryptEv(json, TEST_KEY, "20260602000000"),
                crypto.buildVv(json, TEST_KEY),
                "AES", "HmacSHA256", "20260602000000", TNO, null, null, null);
    }

    /** A row carrying the SENT name — the callback probe keys on the name it was given. */
    private TbFirmFileMst mstAt(String step, String stat) {
        TbFirmFileMst m = new TbFirmFileMst();
        m.setTranDt(TRAN_DT);
        m.setBizKey(BIZ_KEY);
        m.setFileSeqNo("8");
        m.setFirmFileNm(SENT_FILE_NM);
        m.setFileSeq(FILE_SEQ);
        m.setOutBankCd(OUT_BANK);
        m.setBankPrtnId(PRTN);
        m.setProcStep(step);
        m.setProcStat(stat);
        return m;
    }
}
