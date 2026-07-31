package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmConfRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmConf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ResultCheckService#runOnce} — the processing-result poll
 * (PROCESSING_RESULT_CHECK_BATCH). Real {@link EfsCryptoService}; the 203 gateway
 * response is stubbed via {@link EfsApiClient}; repositories, the legacy sequence
 * funnel, the firm-conf gate, the pacer and the atomic finalize service are mocked.
 *
 * <p>The original-firm-tran + pay-target finalize is delegated to
 * {@link DispatchResultTxService} (one REQUIRES_NEW tx per row — Item 1), so these
 * orchestration tests verify the delegation + the {@code TRAN_TP='203'} ledger row
 * (B3). The atomic-update behaviour itself lives in {@code DispatchResultTxServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultCheckPollTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN = "GV00000001";
    private static final String BIZ = "VN_VNM0004_000001";
    private static final String INQ_SEQ = "900"; // fresh firm_seq_no for the 203 row
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    /**
     * D-1: {@code TRAN_DT} lives in the <b>UTC</b> date space — it is the originating
     * {@code TB_PAY_TARGET.TRAN_DT} (legacy {@code PymnHelper:2197} {@code DateTimeUtils.utcNow()}).
     * These fixtures previously used the ICT date, which is the same value for 17 hours a day and
     * one day ahead for the other 7 — so between 00:00 and 07:00 ICT they built rows production
     * never writes, and the suite could neither catch nor reproduce D-1. Boundary behaviour is
     * covered exhaustively in {@code ResultCheckSelectionBoundaryTest}.
     */
    private static final String TODAY =
            java.time.LocalDate.now(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;
    @Mock TbFirmTranRepository tbFirmTranRepository;
    @Mock LegacyFunctionRepository legacyFunctions;
    @Mock TbFirmConfRepository firmConfRepository;
    @Mock DispatchPacer dispatchPacer;
    @Mock OpsAlertService opsAlertService;
    @Mock DispatchResultTxService dispatchResultTxService;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final EfsProperties props = new EfsProperties();
    private final ObjectMapper mapper = new ObjectMapper();

    private ResultCheckService service;

    @BeforeEach
    void setUp() {
        service = new ResultCheckService(efsApiClient, crypto, vanApiKeyService, props, mapper,
                tbFirmTranRepository, opsAlertService,
                legacyFunctions, firmConfRepository, dispatchPacer, dispatchResultTxService);
        // The master gate defaults OFF (wecms.dispatch.result-check.enabled) and there is no Spring
        // context here to inject it; this suite exercises the body, not the gate.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "enabled", true);
        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
        when(tbFirmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(legacyFunctions.nextTbFirmTranSeq()).thenReturn(INQ_SEQ);
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("1")));
    }

    private TbFirmConf conf(String orgStat) {
        TbFirmConf c = new TbFirmConf();
        c.setBizKey(BIZ);
        c.setOutBankCd("970457");
        c.setWdrlActNo("100001020691");
        c.setBankPrtnId("WRB001");
        c.setVanOrgCd(VAN);
        c.setOrgStat(orgStat);
        return c;
    }

    private TbFirmTran agedRow(String tranSeq) {
        TbFirmTran t = new TbFirmTran();
        t.setTranDt(TODAY);
        t.setBizKey(BIZ);
        t.setTranSeq(tranSeq);
        t.setTranTp("202");
        t.setProcStat("6");
        t.setTranTm("000001");          // well before now-2min → in the re-check window
        t.setVanOrgCd(VAN);
        t.setOutBankCd("970457");
        t.setBankPrtnId("WRB001");
        t.setWdrlActNo("100001020691");
        t.setTarBankCd("970457");
        return t;
    }

    private void stubGateway(String orgRespCd) throws Exception {
        Efs203Response resp = new Efs203Response(
                TODAY, "ORGTNO", "120000", orgRespCd, "msg", "100001020691",
                "VND", "1000", "0", "0681000001", "OWNER", "wmemo", "tmemo");
        String json = mapper.writeValueAsString(resp);
        String ev = crypto.encryptEv(json, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(json, TEST_KEY);
        EfsEnvelope env = new EfsEnvelope(VAN, "20240101000000", null, "TNO", ev, vv,
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, "0000", "ok");
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any())).thenReturn(env);
    }

    private List<TbFirmTran> firmSaves() {
        ArgumentCaptor<TbFirmTran> cap = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(tbFirmTranRepository, atLeastOnce()).save(cap.capture());
        return cap.getAllValues();
    }

    private static TbFirmTran last(List<TbFirmTran> all, Predicate<TbFirmTran> p) {
        return all.stream().filter(p).reduce((a, b) -> b).orElse(null);
    }

    /** The confirmed PROC_STAT passed to the atomic finalize service for a given original tranSeq. */
    private String finalizedProcStat(String origTranSeq) {
        ArgumentCaptor<String> stat = ArgumentCaptor.forClass(String.class);
        verify(dispatchResultTxService).finalizeOriginalAndPayTarget(
                eq(BIZ), eq(TODAY), eq(origTranSeq), stat.capture(), any(), any(), any(), any());
        return stat.getValue();
    }

    /**
     * The master gate. Before it existed, {@code result-check.cron} was the only switch on this body,
     * so a single cron variable was enough to start outbound EFS 203 traffic and {@code PROC_STAT}
     * writes — while both sibling sweeps ({@code StrandedRecoveryService},
     * {@code StuckPayFileMonitorService}) required a cron <i>and</i> an {@code enabled} flag.
     */
    @Test
    void disabled_isANoOp_noQuery_noGatewayCall_noStatusWrite() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "enabled", false);
        when(tbFirmTranRepository.findResultCheckCandidates(
                eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(agedRow("199")));

        service.runOnce();

        // Not even the candidate query runs — the gate is checked at method entry.
        verify(tbFirmTranRepository, never()).findResultCheckCandidates(any(), any(), anyCollection(), any());
        verify(efsApiClient, never()).send(any(), any());
        verify(tbFirmTranRepository, never()).save(any());
        verify(dispatchResultTxService, never()).finalizeOriginalAndPayTarget(
                any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void agedSuccess_creates203Row_andFinalizesOriginalZero() throws Exception {
        TbFirmTran row = agedRow("100");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        stubGateway("0000");

        service.runOnce();

        // B3: a TRAN_TP='203' audit row was created (fresh seq) pointing back at the original.
        TbFirmTran inq = last(firmSaves(), t -> "203".equals(t.getTranTp()));
        assertNotNull(inq);
        assertEquals(INQ_SEQ, inq.getTranSeq());
        assertEquals("100", inq.getOrgTranSeq());
        assertEquals("0", inq.getProcStat());
        assertEquals("OWNER", inq.getFincMdaOwnerNm());
        // Item 1: the original + pay-target are finalized atomically to '0'.
        assertEquals("0", finalizedProcStat("100"));
    }

    @Test
    void agedFailure_finalizesOriginalThree() throws Exception {
        TbFirmTran row = agedRow("101");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        stubGateway("0001"); // comms ok (RC=0000) but original response = not-success

        service.runOnce();

        assertEquals("3", finalizedProcStat("101"));
    }

    @Test
    void freshRow_withinDelayWindow_isSkipped() {
        TbFirmTran row = agedRow("102");
        row.setTranTm("235959");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));

        service.runOnce();

        verify(tbFirmTranRepository, never()).save(any());
        verify(dispatchResultTxService, never())
                .finalizeOriginalAndPayTarget(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /* ───────────────── B4 — ORG_STAT gate (audit M-6) ───────────────── */

    @Test
    void orgStatNotAvailable_skipsRow_noInquiry_noWrite() {
        TbFirmTran row = agedRow("150");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        when(firmConfRepository.findById(any())).thenReturn(Optional.of(conf("2"))); // suspended

        service.runOnce();

        verify(tbFirmTranRepository, never()).save(any());
        verify(efsApiClient, never()).send(any(), any());
        verify(dispatchResultTxService, never())
                .finalizeOriginalAndPayTarget(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /* ───────────────── Item 1 — atomic finalize delegation ───────────────── */

    @Test
    void confirmedSuccess_delegatesFinalizeZero() throws Exception {
        TbFirmTran row = agedRow("200");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        stubGateway("0000");

        service.runOnce();

        assertEquals("0", finalizedProcStat("200"));
    }

    @Test
    void confirmedFailure_delegatesFinalizeThree() throws Exception {
        TbFirmTran row = agedRow("201");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        stubGateway("0001");

        service.runOnce();

        assertEquals("3", finalizedProcStat("201"));
    }

    @Test
    void transportFailure_leavesOriginalUntouched_but203RowRecordedFail() {
        TbFirmTran row = agedRow("202");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        // 203 itself returns a non-0000 RC → inquire() throws after the 203 row is inserted.
        EfsEnvelope env = new EfsEnvelope(VAN, "20240101000000", null, "TNO", "EV", "VV",
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, "G999", "no response");
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any())).thenReturn(env);

        assertDoesNotThrow(() -> service.runOnce());

        // No atomic finalize on a transport failure — the original 202 row is left '6'.
        verify(dispatchResultTxService, never())
                .finalizeOriginalAndPayTarget(any(), any(), any(), any(), any(), any(), any(), any());
        // B3: the 203 audit row was created and finalized FAIL (legacy 5-1 실패).
        TbFirmTran inq = last(firmSaves(), t -> "203".equals(t.getTranTp()));
        assertNotNull(inq);
        assertEquals("3", inq.getProcStat());
        verify(opsAlertService).notify(eq(VAN), any());
    }

    /** Item 1: one row whose atomic finalize throws must not block the following row. */
    @Test
    void oneFailedRow_doesNotBlockLaterRows() throws Exception {
        TbFirmTran first = agedRow("600");
        TbFirmTran second = agedRow("601");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(first, second));
        stubGateway("0000");
        // The first row's atomic finalize blows up (e.g. a rolled-back tx surfacing an exception).
        doThrow(new RuntimeException("tx rolled back"))
                .when(dispatchResultTxService).finalizeOriginalAndPayTarget(
                        eq(BIZ), eq(TODAY), eq("600"), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> service.runOnce());

        // The second row was still inquired + finalized despite the first row failing.
        verify(dispatchResultTxService).finalizeOriginalAndPayTarget(
                eq(BIZ), eq(TODAY), eq("601"), eq("0"), any(), any(), any(), any());
    }

    /* ───────────────── B1 — RC=0001 definitive not-found (audit H-1) ───────────────── */

    @Test
    void envelopeRc0001_notFound_finalizesFail() {
        TbFirmTran row = agedRow("400");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        EfsEnvelope env = new EfsEnvelope(VAN, "20240101000000", null, "TNO", "", "",
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, "0001", "no original transaction");
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any())).thenReturn(env);

        service.runOnce();

        // Regression guard: not stuck at '6' — the original is finalized FAIL.
        assertEquals("3", finalizedProcStat("400"));
    }

    /* ───────────────── B5 — per-bank pacing (audit M-5) ───────────────── */

    @Test
    void perBankPacing_pacesOnOriginalFailure() throws Exception {
        TbFirmTran row = agedRow("500");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        stubGateway("0001"); // original transaction failed → legacy paces (:393)

        service.runOnce();

        verify(dispatchPacer).pause("970457");
    }

    @Test
    void perBankPacing_notInvokedOnCleanSuccess() throws Exception {
        TbFirmTran row = agedRow("501");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        stubGateway("0000"); // clean success → legacy 6-1 has no Thread.sleep

        service.runOnce();

        verify(dispatchPacer, never()).pause(any());
    }

    /* ───────────────── A3 — response VV verification (audit H-5) ───────────────── */

    @Test
    void invalidResponseVv_notProcessed_noFinalize() throws Exception {
        TbFirmTran row = agedRow("401");
        when(tbFirmTranRepository.findResultCheckCandidates(eq("202"), eq("6"), anyCollection(), any(org.springframework.data.domain.Pageable.class))).thenReturn(List.of(row));
        Efs203Response resp = new Efs203Response(
                TODAY, "ORGTNO", "120000", "0000", "msg", "100001020691",
                "VND", "1000", "0", "0681000001", "OWNER", "", "");
        String json = mapper.writeValueAsString(resp);
        String ev = crypto.encryptEv(json, TEST_KEY, "20240101000000");
        EfsEnvelope env = new EfsEnvelope(VAN, "20240101000000", null, "TNO", ev, "tampered-vv",
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, "0000", "ok");
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any())).thenReturn(env);

        assertDoesNotThrow(() -> service.runOnce());

        verify(dispatchResultTxService, never())
                .finalizeOriginalAndPayTarget(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
