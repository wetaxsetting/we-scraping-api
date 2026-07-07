package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs203Response;
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
 * Unit tests for {@link ResultCheckService} (WGVN_EFS_203). Real
 * {@link EfsCryptoService} with the published TEST key; {@link EfsApiClient} and
 * {@link VanApiKeyService} are stubbed → <b>no live network call, no real key</b>.
 */
@ExtendWith(MockitoExtension.class)
class ResultCheckServiceTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final EfsProperties props = new EfsProperties(); // partnerId blank → ID = vanOrgCd

    @Mock
    EfsApiClient efsApiClient;
    @Mock
    VanApiKeyService vanApiKeyService;

    private ResultCheckService service() {
        // inquire() (the 203 leg under test here) does not touch TB_FIRM_TRAN /
        // TB_PAY_TARGET; the result-check poll (runOnce) is exercised by ResultCheckPollTest.
        return new ResultCheckService(efsApiClient, crypto, vanApiKeyService, props, mapper, null, null, null);
    }

    private static ResultCheckInquiryCommand cmd() {
        return new ResultCheckInquiryCommand(
                "ORG000001", "970424", "ORG000001", "20240101", "TNO-ORIG-0001", "12345678901111");
    }

    private EfsEnvelope success203(String orgRespCd, String orgRespMsg, String rc) throws Exception {
        Efs203Response body = new Efs203Response(
                "20240101", "TNO-ORIG-0001", "101530", orgRespCd, orgRespMsg,
                "12345678901111", "VND", "1000000", "0", "98765432100000",
                "NGUYEN VAN B", "withd-rmrk", "tar-rmrk");
        String respJson = mapper.writeValueAsString(body);
        String ev = crypto.encryptEv(respJson, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(respJson, TEST_KEY);
        return new EfsEnvelope("ORG000001", null, "20240101000000", "TNO-RESP",
                ev, vv, "AES", "HmacSHA256", rc, "OK");
    }

    @Test
    void success_maps_203_fields_and_assembles_envelope() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any()))
                .thenReturn(success203("0000", "OK", "0000"));

        ResultCheckInquiryResult result = service().inquire(cmd());

        assertThat(result.orgRespCd()).isEqualTo("0000");
        assertThat(result.orgTno()).isEqualTo("TNO-ORIG-0001");
        assertThat(result.fincMdaOwnerNm()).isEqualTo("NGUYEN VAN B");
        assertThat(result.amt()).isEqualTo("1000000");

        // Capture the request envelope and assert its fields + the request business JSON.
        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_203), captor.capture());
        EfsEnvelope sent = captor.getValue();

        assertThat(sent.id()).isEqualTo("ORG000001");          // partnerId blank → vanOrgCd
        assertThat(sent.rqDtime()).matches("\\d{14}");
        assertThat(sent.tno()).isNotBlank();
        assertThat(sent.tno().length()).isLessThanOrEqualTo(50);
        assertThat(sent.ev()).isNotBlank();
        assertThat(sent.vv()).isNotBlank();
        assertThat(sent.em()).isEqualTo("AES");
        assertThat(sent.vm()).isEqualTo("HmacSHA256");
        assertThat(sent.rsDtime()).isNull();
        assertThat(sent.rc()).isNull();

        // Decrypt the EV → request JSON carries the EFS 203 business fields (snake_case).
        String requestJson = crypto.decryptEv(sent.ev(), TEST_KEY);
        assertThat(requestJson)
                .contains("\"bank_cd\":\"970424\"")
                .contains("\"prtn_id\":\"ORG000001\"")
                .contains("\"org_tran_dt\":\"20240101\"")
                .contains("\"org_tno\":\"TNO-ORIG-0001\"")
                .contains("\"macct_no\":\"12345678901111\"");
    }

    @Test
    void non_0000_rc_throws_business_exception_and_does_not_decrypt() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_203), any()))
                .thenReturn(success203("", "", "G001"));

        assertThatThrownBy(() -> service().inquire(cmd()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_EFS_GATEWAY_ERROR");
    }

    @Test
    void missing_api_key_propagates_b2_failure_without_calling_gateway() {
        when(vanApiKeyService.getApiKey("ORG000001"))
                .thenThrow(new IllegalStateException("No TB_VAN_API_INFO.API_KEY provisioned ... B2"));

        assertThatThrownBy(() -> service().inquire(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("B2");

        verify(efsApiClient, never()).send(any(), any()); // no live call attempted
    }

    @Test
    void invalid_input_rejected_before_key_lookup_or_gateway_call() {
        ResultCheckInquiryCommand bad = new ResultCheckInquiryCommand(
                "ORG000001", "970424", "ORG000001", "20240101", "  ", "12345678901111"); // blank orgTno

        assertThatThrownBy(() -> service().inquire(bad))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(efsApiClient, vanApiKeyService);
    }
}
