package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.payment.dispatch.entity.TbPayTarget;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
import com.kosign.wecms.domain.payment.dispatch.repository.TbPayTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ResultCheckService#runOnce} — the processing-result poll
 * (PROCESSING_RESULT_CHECK_BATCH). Real {@link EfsCryptoService}; the 203 gateway
 * response is stubbed via {@link EfsApiClient}; {@link TbFirmTranRepository} is mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResultCheckPollTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String VAN = "GV00000001";
    private static final ZoneId ICT = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String TODAY = LocalDateTime.now(ICT).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @Mock EfsApiClient efsApiClient;
    @Mock VanApiKeyService vanApiKeyService;
    @Mock TbFirmTranRepository tbFirmTranRepository;
    @Mock TbPayTargetRepository tbPayTargetRepository;

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final EfsProperties props = new EfsProperties();
    private final ObjectMapper mapper = new ObjectMapper();

    private ResultCheckService service;

    @BeforeEach
    void setUp() {
        service = new ResultCheckService(efsApiClient, crypto, vanApiKeyService, props, mapper,
                tbFirmTranRepository, tbPayTargetRepository,
                org.mockito.Mockito.mock(com.kosign.wecms.domain.payment.dispatch.alert.OpsAlertService.class));
        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
        when(tbFirmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tbPayTargetRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    /** The originating pay-target row, linked by the shared (BIZ_KEY, TRAN_DT, TRAN_SEQ). */
    private TbPayTarget payTarget(String tranSeq) {
        TbPayTarget p = new TbPayTarget();
        p.setTranDt(TODAY);
        p.setBizKey("VN_VNM0004_000001");
        p.setTranSeq(tranSeq);
        p.setOutBankCd("970457");
        p.setWdrlActNo("100001020691");
        p.setRegTm("000001");
        p.setAntDivCd("INTER");
        p.setTarBankCd("970457");
        p.setFincMdaNo("0681000001");
        p.setTranAmt(new java.math.BigDecimal("1000.00"));
        p.setTrscCcyCd("VND");
        p.setProcStat("6"); // in-progress after the original 202 G999
        return p;
    }

    private TbFirmTran agedRow(String tranSeq) {
        TbFirmTran t = new TbFirmTran();
        t.setTranDt(TODAY);
        t.setBizKey("VN_VNM0004_000001");
        t.setTranSeq(tranSeq);
        t.setTranTp("202");
        t.setProcStat("6");
        t.setTranTm("000001");          // well before now-2min → in the re-check window
        t.setVanOrgCd(VAN);
        t.setOutBankCd("970457");
        t.setBankPrtnId("WRB001");
        t.setWdrlActNo("100001020691");
        return t;
    }

    private void stubGateway(String orgRespCd) throws Exception {
        Efs203Response resp = new Efs203Response(
                TODAY, "ORGTNO", "120000", orgRespCd, "msg", "100001020691",
                "VND", "1000", "0", "0681000001", "OWNER", "", "");
        String json = mapper.writeValueAsString(resp);
        String ev = crypto.encryptEv(json, TEST_KEY, "20240101000000");
        EfsEnvelope env = new EfsEnvelope(VAN, "20240101000000", null, "TNO", ev, "VV",
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, "0000", "ok");
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any())).thenReturn(env);
    }

    @Test
    void agedSuccess_setsProcStat0() throws Exception {
        TbFirmTran row = agedRow("100");
        when(tbFirmTranRepository.findByTranTpAndProcStat("202", "6")).thenReturn(List.of(row));
        stubGateway("0000");

        service.runOnce();

        ArgumentCaptor<TbFirmTran> cap = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(tbFirmTranRepository).save(cap.capture());
        assertEquals("0", cap.getValue().getProcStat());
        assertEquals("0000", cap.getValue().getOrgRespCd());
    }

    @Test
    void agedFailure_setsProcStat3AndRsCd() throws Exception {
        TbFirmTran row = agedRow("101");
        when(tbFirmTranRepository.findByTranTpAndProcStat("202", "6")).thenReturn(List.of(row));
        stubGateway("0001"); // no original transaction → not success

        service.runOnce();

        ArgumentCaptor<TbFirmTran> cap = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(tbFirmTranRepository).save(cap.capture());
        assertEquals("3", cap.getValue().getProcStat());
        assertEquals("0001", cap.getValue().getRsCd());
    }

    @Test
    void freshRow_withinDelayWindow_isSkipped() {
        TbFirmTran row = agedRow("102");
        row.setTranTm("235959"); // end-of-day → never <= (now-2min) except the last 2 min
        when(tbFirmTranRepository.findByTranTpAndProcStat("202", "6")).thenReturn(List.of(row));

        service.runOnce();

        verify(tbFirmTranRepository, never()).save(any());
    }

    /* ───────────────── C4 — TB_PAY_TARGET propagation ───────────────── */

    private void stubTransportFailure() {
        // 203 itself returns a non-0000 RC → inquire() throws before any write.
        EfsEnvelope env = new EfsEnvelope(VAN, "20240101000000", null, "TNO", "EV", "VV",
                EfsEnvelope.EM_AES, EfsEnvelope.VM_HMAC_SHA256, "G999", "no response");
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any())).thenReturn(env);
    }

    @Test
    void c4_confirmedSuccess_propagatesPayTargetZero() throws Exception {
        TbFirmTran row = agedRow("200");
        when(tbFirmTranRepository.findByTranTpAndProcStat("202", "6")).thenReturn(List.of(row));
        when(tbPayTargetRepository.findByBizKeyAndTranDtAndTranSeq("VN_VNM0004_000001", TODAY, "200"))
                .thenReturn(Optional.of(payTarget("200")));
        stubGateway("0000");

        service.runOnce();

        ArgumentCaptor<TbFirmTran> firm = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(tbFirmTranRepository).save(firm.capture());
        assertEquals("0", firm.getValue().getProcStat());

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(tbPayTargetRepository).save(pay.capture());
        assertEquals("0", pay.getValue().getProcStat());
    }

    @Test
    void c4_confirmedFailure_propagatesPayTargetThree() throws Exception {
        TbFirmTran row = agedRow("201");
        when(tbFirmTranRepository.findByTranTpAndProcStat("202", "6")).thenReturn(List.of(row));
        when(tbPayTargetRepository.findByBizKeyAndTranDtAndTranSeq("VN_VNM0004_000001", TODAY, "201"))
                .thenReturn(Optional.of(payTarget("201")));
        stubGateway("0001"); // original transaction not found → failure

        service.runOnce();

        ArgumentCaptor<TbFirmTran> firm = ArgumentCaptor.forClass(TbFirmTran.class);
        verify(tbFirmTranRepository).save(firm.capture());
        assertEquals("3", firm.getValue().getProcStat());

        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(tbPayTargetRepository).save(pay.capture());
        assertEquals("3", pay.getValue().getProcStat());
    }

    @Test
    void c4_transportFailure_doesNotTouchEitherTable() {
        TbFirmTran row = agedRow("202");
        when(tbFirmTranRepository.findByTranTpAndProcStat("202", "6")).thenReturn(List.of(row));
        stubTransportFailure();

        assertDoesNotThrow(() -> service.runOnce());

        // inquire() threw before any write → neither the ledger nor the pay-target moved.
        verify(tbFirmTranRepository, never()).save(any());
        verify(tbPayTargetRepository, never()).save(any());
    }

    @Test
    void c4_missingPayTarget_updatesFirmTran_andContinuesToNextRow() throws Exception {
        TbFirmTran missing = agedRow("300"); // no pay-target row
        TbFirmTran present = agedRow("301"); // pay-target present
        when(tbFirmTranRepository.findByTranTpAndProcStat("202", "6"))
                .thenReturn(List.of(missing, present));
        when(tbPayTargetRepository.findByBizKeyAndTranDtAndTranSeq("VN_VNM0004_000001", TODAY, "300"))
                .thenReturn(Optional.empty());
        when(tbPayTargetRepository.findByBizKeyAndTranDtAndTranSeq("VN_VNM0004_000001", TODAY, "301"))
                .thenReturn(Optional.of(payTarget("301")));
        stubGateway("0000");

        assertDoesNotThrow(() -> service.runOnce());

        // Both firm-tran rows updated (the missing pay-target did not abort the batch)...
        verify(tbFirmTranRepository, org.mockito.Mockito.times(2)).save(any());
        // ...but only the row whose pay-target exists is propagated.
        ArgumentCaptor<TbPayTarget> pay = ArgumentCaptor.forClass(TbPayTarget.class);
        verify(tbPayTargetRepository).save(pay.capture());
        assertEquals("301", pay.getValue().getTranSeq());
        assertEquals("0", pay.getValue().getProcStat());
    }
}
