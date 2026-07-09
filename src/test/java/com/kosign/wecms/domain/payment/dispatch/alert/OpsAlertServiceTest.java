package com.kosign.wecms.domain.payment.dispatch.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OpsAlertService} — the ported {@code WGVN_MSG_SEND_001}
 * ops alert. Verifies the master gate (no-op when disabled), the envelope shape
 * (fixed {@code TNO}, {@code EM}/{@code VM}), the vendor-cased business JSON, and
 * the fire-and-forget guarantee (never propagates). {@code @Async} is inactive in
 * a plain unit test, so {@code notify} runs synchronously here.
 */
@ExtendWith(MockitoExtension.class)
class OpsAlertServiceTest {

    @Mock private EfsApiClient efsApiClient;
    @Mock private EfsCryptoService efsCryptoService;
    @Mock private VanApiKeyService vanApiKeyService;
    @Mock private EfsProperties efsProperties;

    private OpsAlertService service;

    @BeforeEach
    void setUp() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("sit");
        service = new OpsAlertService(
                efsApiClient, efsCryptoService, vanApiKeyService,
                efsProperties, new ObjectMapper(), env);
    }

    private void enable() {
        ReflectionTestUtils.setField(service, "enabled", true);
    }

    @Test
    void disabled_isNoOp() {
        // enabled defaults false — no key lookup, no send.
        service.notify("VAN01", "이체파일 Batch1 검증 오류 발생");

        verifyNoInteractions(vanApiKeyService, efsApiClient, efsCryptoService);
    }

    @Test
    void blankArgs_isNoOp() {
        enable();

        service.notify("  ", "content");
        service.notify("VAN01", "");

        verifyNoInteractions(vanApiKeyService, efsApiClient, efsCryptoService);
    }

    @Test
    void enabled_sendsEnvelopeWithLegacyConstants() {
        enable();
        when(vanApiKeyService.getApiKey("VAN01")).thenReturn("0123456789abcdef0123456789abcdef");
        when(efsCryptoService.encryptEv(anyString(), anyString(), anyString())).thenReturn("EV_VALUE");
        when(efsCryptoService.buildVv(anyString(), anyString())).thenReturn("VV_VALUE");
        when(efsProperties.getPartnerId()).thenReturn("CORP000001");
        when(efsApiClient.send(eq(EfsApiClient.EFS_MSG), any(EfsEnvelope.class)))
                .thenReturn(new EfsEnvelope(null, null, "20260706120000", "M9999999",
                        null, null, "AES", "HmacSHA256", "0000", "OK"));

        service.notify("VAN01", "처리결과조회 Batch");

        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_MSG), captor.capture());
        EfsEnvelope sent = captor.getValue();
        assertThat(sent.tno()).isEqualTo("M9999999");
        assertThat(sent.id()).isEqualTo("CORP000001");
        assertThat(sent.em()).isEqualTo(EfsEnvelope.EM_AES);
        assertThat(sent.vm()).isEqualTo(EfsEnvelope.VM_HMAC_SHA256);
        assertThat(sent.ev()).isEqualTo("EV_VALUE");
        assertThat(sent.vv()).isEqualTo("VV_VALUE");
        assertThat(sent.rc()).isNull();
    }

    @Test
    void businessJson_keepsVendorCasing() {
        enable();
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(efsProperties.getPartnerId()).thenReturn(null);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        when(efsCryptoService.encryptEv(jsonCaptor.capture(), anyString(), anyString())).thenReturn("EV");
        when(efsCryptoService.buildVv(anyString(), anyString())).thenReturn("VV");
        when(efsApiClient.send(anyString(), any())).thenReturn(null);

        service.notify("VAN01", "hello");

        String json = jsonCaptor.getValue();
        // Legacy protocol constants + vendor casing (not snake_case), active-profile mode.
        assertThat(json).contains("\"appDiv\":\"0\"");
        assertThat(json).contains("\"grpID\":\"WECMSBATCH\"");
        assertThat(json).contains("\"content\":\"hello\"");
        assertThat(json).contains("\"mode\":\"sit\"");
    }

    @Test
    void fallsBackToVanOrgCdWhenNoPartnerId() {
        enable();
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(efsProperties.getPartnerId()).thenReturn("");
        when(efsCryptoService.encryptEv(anyString(), anyString(), anyString())).thenReturn("EV");
        when(efsCryptoService.buildVv(anyString(), anyString())).thenReturn("VV");
        when(efsApiClient.send(anyString(), any())).thenReturn(null);

        service.notify("VAN99", "x");

        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_MSG), captor.capture());
        assertThat(captor.getValue().id()).isEqualTo("VAN99");
    }

    @Test
    void gatewayFailure_isSwallowed() {
        enable();
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("0123456789abcdef0123456789abcdef");
        lenient().when(efsProperties.getPartnerId()).thenReturn(null);
        when(efsCryptoService.encryptEv(anyString(), anyString(), anyString())).thenReturn("EV");
        when(efsCryptoService.buildVv(anyString(), anyString())).thenReturn("VV");
        when(efsApiClient.send(anyString(), any())).thenThrow(new RuntimeException("gateway down"));

        // Fire-and-forget: must never propagate.
        assertThatCode(() -> service.notify("VAN01", "x")).doesNotThrowAnyException();
    }

    @Test
    void keyLookupFailure_isSwallowed() {
        enable();
        when(vanApiKeyService.getApiKey(anyString())).thenThrow(new IllegalStateException("no key (B2)"));

        assertThatCode(() -> service.notify("VAN01", "x")).doesNotThrowAnyException();
        verify(efsApiClient, never()).send(anyString(), any());
    }
}
