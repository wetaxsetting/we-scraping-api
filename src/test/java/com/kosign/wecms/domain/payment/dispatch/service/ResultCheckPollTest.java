package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.entity.TbFirmTran;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import com.kosign.wecms.domain.payment.dispatch.repository.TbFirmTranRepository;
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

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final EfsProperties props = new EfsProperties();
    private final ObjectMapper mapper = new ObjectMapper();

    private ResultCheckService service;

    @BeforeEach
    void setUp() {
        service = new ResultCheckService(efsApiClient, crypto, vanApiKeyService, props, mapper, tbFirmTranRepository);
        when(vanApiKeyService.getApiKey(VAN)).thenReturn(TEST_KEY);
        when(tbFirmTranRepository.save(any())).thenAnswer(i -> i.getArgument(0));
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
}
