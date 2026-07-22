package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs301Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the {@link BulkReceptionService} 00301 <b>API request leg</b>
 * ({@code issueReception}). Real {@link EfsCryptoService} with the published TEST
 * key; {@link EfsApiClient} and {@link VanApiKeyService} are stubbed → <b>no live
 * network call, no real key</b>. The file-gen (K3) / SFTP (B4) / orchestration
 * around the leg are out of scope and not exercised here.
 */
@ExtendWith(MockitoExtension.class)
class BulkReceptionServiceTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final EfsProperties props = new EfsProperties(); // partnerId blank → ID = vanOrgCd

    @Mock
    EfsApiClient efsApiClient;
    @Mock
    VanApiKeyService vanApiKeyService;

    private BulkReceptionService service() {
        // issueReception() (the leg under test here) uses only the EFS/crypto deps;
        // the receive() orchestration deps (repos / key-gen / codec / SFTP) are not
        // exercised by these tests, so null is fine.
        return new BulkReceptionService(
                efsApiClient, crypto, vanApiKeyService, props, mapper,
                null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);
    }

    private static BulkReceptionApiCommand cmd() {
        return new BulkReceptionApiCommand(
                "ORG000001", "970424", "ORG000001", "FB_20240101_0001.txt", "3", "1500000", "0", "0000042");
    }

    private EfsEnvelope success301(String rc) throws Exception {
        Efs301Response body = new Efs301Response(
                "970424", "ORG000001", "FB_20240101_0001.txt", "3", "1500000", "0");
        String respJson = mapper.writeValueAsString(body);
        String ev = crypto.encryptEv(respJson, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(respJson, TEST_KEY);
        return new EfsEnvelope("ORG000001", null, "20240101000000", "TNO-RESP",
                ev, vv, "AES", "HmacSHA256", rc, "OK");
    }

    private EfsEnvelope bareEnvelope(String rc) {
        // No EV — a non-success path must NOT attempt to decrypt.
        return new EfsEnvelope("ORG000001", null, "20240101000000", "TNO-RESP",
                null, null, "AES", "HmacSHA256", rc, "ERR");
    }

    @Test
    void success_maps_301_totals_and_assembles_envelope() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_301), any())).thenReturn(success301("0000"));

        BulkReceptionApiResult result = service().issueReception(cmd());

        assertThat(result.fileNm()).isEqualTo("FB_20240101_0001.txt");
        assertThat(result.dataCnt()).isEqualTo("3");
        assertThat(result.dataAmt()).isEqualTo("1500000");

        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_301), captor.capture());
        EfsEnvelope sent = captor.getValue();

        assertThat(sent.id()).isEqualTo("ORG000001");          // partnerId blank → vanOrgCd
        assertThat(sent.rqDtime()).matches("\\d{14}");
        assertThat(sent.tno()).isEqualTo("0000042"); // A1 (audit X-2): TNO = allocated TRAN_SEQ
        assertThat(sent.ev()).isNotBlank();
        assertThat(sent.vv()).isNotBlank();
        assertThat(sent.em()).isEqualTo("AES");
        assertThat(sent.vm()).isEqualTo("HmacSHA256");
        assertThat(sent.rsDtime()).isNull();
        assertThat(sent.rc()).isNull();

        // Decrypt the EV → request JSON carries the EFS 301 business fields (snake_case).
        String requestJson = crypto.decryptEv(sent.ev(), TEST_KEY);
        assertThat(requestJson)
                .contains("\"bank_cd\":\"970424\"")
                .contains("\"prtn_id\":\"ORG000001\"")
                .contains("\"file_nm\":\"FB_20240101_0001.txt\"")
                .contains("\"data_cnt\":\"3\"")
                .contains("\"data_amt\":\"1500000\"")
                .contains("\"data_dcm_amt\":\"0\"");
    }

    @Test
    void non_0000_rc_throws_business_exception_and_does_not_decrypt() {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_301), any())).thenReturn(bareEnvelope("G001"));

        assertThatThrownBy(() -> service().issueReception(cmd()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_EFS_GATEWAY_ERROR");
    }

    @Test
    void missing_api_key_propagates_b2_failure_without_calling_gateway() {
        when(vanApiKeyService.getApiKey("ORG000001"))
                .thenThrow(new IllegalStateException("No TB_VAN_API_INFO.API_KEY provisioned ... B2"));

        assertThatThrownBy(() -> service().issueReception(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("B2");

        verify(efsApiClient, never()).send(any(), any()); // no live call attempted
    }

    @Test
    void invalid_input_rejected_before_key_lookup_or_gateway_call() {
        BulkReceptionApiCommand bad = new BulkReceptionApiCommand(
                "ORG000001", "970424", "ORG000001", "  ", "3", "1500000", "0", "0000042"); // blank fileNm

        assertThatThrownBy(() -> service().issueReception(bad))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(efsApiClient, vanApiKeyService);
    }
}
