package com.kosign.wecms.domain.payment.dispatch.recipient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs201Response;
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
 * Unit tests for {@link RecipientLookupService} (WGVN_EFS_201). Real
 * {@link EfsCryptoService} with the published TEST key; {@link EfsApiClient} and
 * {@link VanApiKeyService} are stubbed → <b>no live network call, no real key</b>.
 */
@ExtendWith(MockitoExtension.class)
class RecipientLookupServiceTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final EfsProperties props = new EfsProperties(); // partnerId blank → ID = vanOrgCd

    @Mock
    EfsApiClient efsApiClient;
    @Mock
    VanApiKeyService vanApiKeyService;

    private RecipientLookupService service() {
        return new RecipientLookupService(efsApiClient, crypto, vanApiKeyService, props, mapper);
    }

    private static RecipientLookupCommand cmd() {
        return new RecipientLookupCommand(
                "ORG000001", "970424", "ORG000001", "970424", "A", "12345678901111", "55555555555555555555");
    }

    private EfsEnvelope successResponse(String ownerName, String antDivCd, String rc) throws Exception {
        String respJson = mapper.writeValueAsString(new Efs201Response(ownerName, antDivCd));
        String ev = crypto.encryptEv(respJson, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(respJson, TEST_KEY);
        return new EfsEnvelope("ORG000001", null, "20240101000000", "TNO-RESP",
                ev, vv, "AES", "HmacSHA256", rc, "OK");
    }

    @Test
    void success_maps_owner_name_and_ant_div_cd_and_assembles_envelope() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_201), any()))
                .thenReturn(successResponse("NGUYEN VAN A", "NAPAS", "0000"));

        RecipientLookupResult result = service().lookup(cmd());

        assertThat(result.ownerName()).isEqualTo("NGUYEN VAN A");
        assertThat(result.antDivCd()).isEqualTo("NAPAS");

        // Capture the request envelope and assert its fields + the request business JSON.
        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_201), captor.capture());
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

        // Decrypt the EV → request JSON carries the EFS 201 business fields (snake_case).
        String requestJson = crypto.decryptEv(sent.ev(), TEST_KEY);
        assertThat(requestJson)
                .contains("\"bank_cd\":\"970424\"")
                .contains("\"prtn_id\":\"ORG000001\"")
                .contains("\"tar_bank_cd\":\"970424\"")
                .contains("\"tar_finc_mda_div\":\"A\"")
                .contains("\"tar_finc_mda_no\":\"12345678901111\"");
    }

    @Test
    void non_0000_rc_throws_business_exception_and_does_not_decrypt() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_201), any()))
                .thenReturn(successResponse("", "", "G001"));

        assertThatThrownBy(() -> service().lookup(cmd()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_EFS_GATEWAY_ERROR");
    }

    @Test
    void missing_api_key_propagates_b2_failure_without_calling_gateway() {
        // VanApiKeyService throws (B2 — TB_VAN_API_INFO.API_KEY not provisioned).
        when(vanApiKeyService.getApiKey("ORG000001"))
                .thenThrow(new IllegalStateException("No TB_VAN_API_INFO.API_KEY provisioned ... B2"));

        assertThatThrownBy(() -> service().lookup(cmd()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("B2");

        verify(efsApiClient, never()).send(any(), any()); // no live call attempted
    }

    @Test
    void invalid_input_rejected_before_key_lookup_or_gateway_call() {
        RecipientLookupCommand bad = new RecipientLookupCommand(
                "ORG000001", "970424", "ORG000001", "970424", "A", "  ", null); // blank tarFincMdaNo

        assertThatThrownBy(() -> service().lookup(bad))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(efsApiClient, vanApiKeyService); // nothing reached the gateway/key
    }
}
