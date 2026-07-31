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
        when(efsApiClient.send(eq(EfsApiClient.EFS_MSG), any(EfsEnvelope.class)))
                .thenReturn(new EfsEnvelope(null, null, "20260706120000", "M9999999",
                        null, null, "AES", "HmacSHA256", "0000", "OK"));

        service.notify("VAN01", "처리결과조회 Batch");

        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_MSG), captor.capture());
        EfsEnvelope sent = captor.getValue();
        assertThat(sent.tno()).isEqualTo("M9999999");
        // M-12 (audit) + D-2: the envelope ID is ALWAYS the VAN org code (TB_FIRM_CONF.VAN_ORG_CD).
        assertThat(sent.id()).isEqualTo("VAN01");
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
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        when(efsCryptoService.encryptEv(jsonCaptor.capture(), anyString(), anyString())).thenReturn("EV");
        when(efsCryptoService.buildVv(anyString(), anyString())).thenReturn("VV");
        when(efsApiClient.send(anyString(), any())).thenReturn(null);

        service.notify("VAN01", "hello");

        String json = jsonCaptor.getValue();
        // Legacy protocol constants + vendor casing (not snake_case), active-profile mode.
        assertThat(json).contains("\"appDiv\":\"0\"");
        assertThat(json).contains("\"grpID\":\"WECMSBATCH\"");
        // F3 (audit): the legacy ServerGB [ENV][WAS_NO] marker is appended to the content's
        // first line — env-mode unset → the active profile ("sit") is the fallback env label.
        assertThat(json).contains("\"content\":\"hello [sit][]\"");
        assertThat(json).contains("\"mode\":\"sit\"");
    }

    @Test
    void envelopeIdIsAlwaysVanOrgCd() {
        // M-12 (audit) + D-2 (legacy-verified): the envelope ID is the VAN org code on every leg
        // (legacy MsgSendUtil.SendMsg(van_org_cd, ...)); there is no partner-id property any more.
        enable();
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("0123456789abcdef0123456789abcdef");
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

    // ── FIX K — grpID is configurable, with the legacy value as the default ──
    //
    // grpID is the gateway's channel-mapping id. It was a `private static final` here described as
    // "never configurable", which misread legacy: MsgSendUtil.SendMsg takes it as a PARAMETER and all
    // 10 legacy call sites merely pass the same literal. During JEX coexistence both systems post to
    // WECMSBATCH, so ops cannot attribute an alert to a system — hence the knob.

    /** Captures the decrypted business JSON that goes into EV. */
    private String capturedBusinessJson(String vanOrgCd) {
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        when(vanApiKeyService.getApiKey(anyString())).thenReturn("0123456789abcdef0123456789abcdef");
        when(efsCryptoService.encryptEv(jsonCaptor.capture(), anyString(), anyString())).thenReturn("EV");
        lenient().when(efsCryptoService.buildVv(anyString(), anyString())).thenReturn("VV");
        lenient().when(efsApiClient.send(anyString(), any()))
                .thenReturn(new EfsEnvelope(vanOrgCd, null, null, null, null, null, null, null, "0000", "OK"));
        service.notify(vanOrgCd, "body");
        return jsonCaptor.getValue();
    }

    @Test
    void grpId_defaultsToTheLegacyConstant() {
        // The default must be byte-identical to legacy, so enabling nothing changes nothing.
        enable();
        assertThat(capturedBusinessJson("VAN01")).contains("\"grpID\":\"WECMSBATCH\"");
    }

    @Test
    void grpId_isOverridable() {
        enable();
        ReflectionTestUtils.setField(service, "grpId", "WECMSREFACTOR");

        assertThat(capturedBusinessJson("VAN01"))
                .contains("\"grpID\":\"WECMSREFACTOR\"")
                .doesNotContain("WECMSBATCH");
    }

    @Test
    void grpId_blankFallsBackToLegacy_ratherThanSendingAnEmptyChannel() {
        // An empty grpID is an unroutable message, and this service is fire-and-forget — the loss
        // would be silent. A property set to "" by accident must not be able to switch alerting off.
        enable();
        ReflectionTestUtils.setField(service, "grpId", "   ");

        assertThat(capturedBusinessJson("VAN01")).contains("\"grpID\":\"WECMSBATCH\"");
    }

    @Test
    void grpId_isTrimmed() {
        enable();
        ReflectionTestUtils.setField(service, "grpId", "  WECMSREFACTOR  ");

        assertThat(capturedBusinessJson("VAN01")).contains("\"grpID\":\"WECMSREFACTOR\"");
    }
}
