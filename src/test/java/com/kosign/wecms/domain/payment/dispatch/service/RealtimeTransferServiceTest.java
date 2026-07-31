package com.kosign.wecms.domain.payment.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsApiClient;
import com.kosign.wecms.domain.payment.dispatch.van.client.EfsProperties;
import com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService;
import com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs202Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.Efs204Response;
import com.kosign.wecms.domain.payment.dispatch.van.dto.EfsEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RealtimeTransferService} (WGVN_EFS_202). Real
 * {@link EfsCryptoService} with the published TEST key; {@link EfsApiClient} and
 * {@link VanApiKeyService} are stubbed → <b>no live network call, no real key</b>.
 *
 * <p><b>B2 (audit H-4/D2):</b> a 202 {@code G999} / no-response / VV-mismatch is now returned as
 * a <i>pending</i> result (no synchronous inline 203) so the caller leaves {@code PROC_STAT='6'}
 * and the scheduled result-check reconciles later. <b>A3 (audit H-5):</b> the 202/204 response VV
 * is verified before decrypt.
 */
@ExtendWith(MockitoExtension.class)
class RealtimeTransferServiceTest {

    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    /** Allocated TRAN_SEQ passed as the gateway TNO (A1 / audit X-2). */
    private static final String TNO = "0000000123";

    private final EfsCryptoService crypto = new EfsCryptoService();
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final EfsProperties props = new EfsProperties(); // D-2: envelope ID is always TB_FIRM_CONF.VAN_ORG_CD (legacy-verified)

    @Mock
    EfsApiClient efsApiClient;
    @Mock
    VanApiKeyService vanApiKeyService;

    private RealtimeTransferService service() {
        return new RealtimeTransferService(
                efsApiClient, crypto, vanApiKeyService, props, mapper);
    }

    private static RealtimeTransferCommand cmd() {
        // Shinhan (970424) + VND → ordinary domestic path, no WGVN_EFS_204 pre-step.
        return new RealtimeTransferCommand(
                "ORG000001", "970424", "ORG000001", "VND", "1000000", "0",
                "A", "12345678901111", "970436", "98765432100000",
                "NGUYEN VAN B", "salary", "NAPAS", null);
    }

    private static RealtimeTransferCommand wooriFxCmd() {
        // Woori (970457) + non-VND (USD) → triggers the WGVN_EFS_204 FX pre-inquiry.
        return new RealtimeTransferCommand(
                "ORG000001", "970457", "ORG000001", "USD", "100", "0",
                "A", "12345678901111", "970436", "98765432100000",
                "NGUYEN VAN B", "salary", "NAPAS", "USD");
    }

    private EfsEnvelope success204() throws Exception {
        // §5.6 response: apl_exc_rate_itg/dcm, withd_amt_itg/dcm, dep_curren_cd, dep_amt_itg/dcm.
        String respJson = mapper.writeValueAsString(
                new Efs204Response("24500", "0", "100", "0", "VND", "2450000", "0"));
        String ev = crypto.encryptEv(respJson, TEST_KEY, "20240101000000");
        String vv = crypto.buildVv(respJson, TEST_KEY);
        return new EfsEnvelope("ORG000001", null, "20240101000000", "TNO-204",
                ev, vv, "AES", "HmacSHA256", "0000", "OK");
    }

    private EfsEnvelope success202(String rc) throws Exception {
        String respJson = mapper.writeValueAsString(
                new Efs202Response("0", "5000000", "0", "NGUYEN VAN B", "NAPAS"));
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
    void success_rc_0000_maps_balance_and_assembles_envelope() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(success202("0000"));

        RealtimeTransferResult result = service().transfer(cmd(), TNO);

        assertThat(result.pending()).isFalse();
        assertThat(result.balAmt()).isEqualTo("5000000");
        assertThat(result.balAmtSign()).isEqualTo("0");
        assertThat(result.fincMdaOwnerNm()).isEqualTo("NGUYEN VAN B");

        ArgumentCaptor<EfsEnvelope> captor = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_202), captor.capture());
        EfsEnvelope sent = captor.getValue();

        assertThat(sent.id()).isEqualTo("ORG000001");
        assertThat(sent.rqDtime()).matches("\\d{14}");
        // A1 (audit X-2): the envelope TNO is the allocated TRAN_SEQ passed by the caller, NOT a UUID.
        assertThat(sent.tno()).isEqualTo(TNO);
        assertThat(sent.ev()).isNotBlank();
        assertThat(sent.vv()).isNotBlank();
        assertThat(sent.em()).isEqualTo("AES");
        assertThat(sent.vm()).isEqualTo("HmacSHA256");

        // Decrypt the EV → request JSON carries the EFS 202 business fields (snake_case).
        String requestJson = crypto.decryptEv(sent.ev(), TEST_KEY);
        assertThat(requestJson)
                .contains("\"bank_cd\":\"970424\"")
                .contains("\"prtn_id\":\"ORG000001\"")
                .contains("\"curren_cd\":\"VND\"")
                .contains("\"amt\":\"1000000\"")
                .contains("\"dcm_amt\":\"0\"")
                .contains("\"tar_finc_mda_div\":\"A\"")
                .contains("\"macct_no\":\"12345678901111\"")
                .contains("\"tar_bank_cd\":\"970436\"")
                .contains("\"tar_finc_mda_no\":\"98765432100000\"")
                .contains("\"ant_div_cd\":\"NAPAS\"");
    }

    @Test
    void blankTno_rejected_beforeGatewayCall() {
        // A1: the caller must supply the allocated TRAN_SEQ as the TNO — a blank tno is a bug.
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);

        assertThatThrownBy(() -> service().transfer(cmd(), "  "))
                .isInstanceOf(IllegalArgumentException.class);

        verify(efsApiClient, never()).send(any(), any());
    }

    @Test
    void g999_returns_pending_and_does_no_synchronous_203() {
        // B2: G999 is "no definitive response" → pending; the service must NOT fire a synchronous
        // 203 (that recovery now belongs to the scheduled ResultCheckService).
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(bareEnvelope("G999"));

        RealtimeTransferResult result = service().transfer(cmd(), TNO);

        assertThat(result.pending()).isTrue();
        assertThat(result.balAmt()).isNull();
        verify(efsApiClient).send(eq(EfsApiClient.EFS_202), any());
        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_203), any());
    }

    @Test
    void noResponse_returnsPending() {
        // B2: a null gateway response is treated like G999 — pending, not a failure.
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(null);

        RealtimeTransferResult result = service().transfer(cmd(), TNO);

        assertThat(result.pending()).isTrue();
    }

    @Test
    void success_rc_0000_withInvalidVv_returnsPending_notTrustedSuccess() throws Exception {
        // A3: a 0000 response whose VV does not verify must NOT be trusted as a completed transfer.
        // It is returned as pending (leave '6', let result-check confirm), never decrypted as success.
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        EfsEnvelope good = success202("0000");
        EfsEnvelope tampered = new EfsEnvelope(good.id(), good.rqDtime(), good.rsDtime(), good.tno(),
                good.ev(), "tampered-vv", good.em(), good.vm(), good.rc(), good.rm());
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(tampered);

        RealtimeTransferResult result = service().transfer(cmd(), TNO);

        assertThat(result.pending()).isTrue();
        assertThat(result.balAmt()).isNull();
    }

    @Test
    void other_non_0000_rc_throws_business_exception() {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(bareEnvelope("G001"));

        assertThatThrownBy(() -> service().transfer(cmd(), TNO))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_EFS_GATEWAY_ERROR");
    }

    @Test
    void missing_api_key_propagates_b2_failure_without_calling_gateway() {
        when(vanApiKeyService.getApiKey("ORG000001"))
                .thenThrow(new IllegalStateException("No TB_VAN_API_INFO.API_KEY provisioned ... B2"));

        assertThatThrownBy(() -> service().transfer(cmd(), TNO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("B2");

        verify(efsApiClient, never()).send(any(), any());
    }

    @Test
    void invalid_input_rejected_before_key_lookup_or_gateway_call() {
        RealtimeTransferCommand bad = new RealtimeTransferCommand(
                "ORG000001", "970424", "ORG000001", "VND", "1000000", "0",
                "A", "12345678901111", "970436", "  ", null, null, "NAPAS", null); // blank tarFincMdaNo

        assertThatThrownBy(() -> service().transfer(bad, TNO))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(efsApiClient, vanApiKeyService);
    }

    // ── WGVN_EFS_204 Woori FX pre-step (guide v1.2.6 §5.6) ────────────────────

    @Test
    void woori_non_vnd_runs_204_first_then_feeds_fx_block_into_202() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_204), any())).thenReturn(success204());
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(success202("0000"));

        RealtimeTransferResult result = service().transfer(wooriFxCmd(), TNO);

        assertThat(result.pending()).isFalse();
        assertThat(result.balAmt()).isEqualTo("5000000");

        // 204 runs strictly before 202.
        InOrder ordered = inOrder(efsApiClient);
        ArgumentCaptor<EfsEnvelope> env204 = ArgumentCaptor.forClass(EfsEnvelope.class);
        ArgumentCaptor<EfsEnvelope> env202 = ArgumentCaptor.forClass(EfsEnvelope.class);
        ordered.verify(efsApiClient).send(eq(EfsApiClient.EFS_204), env204.capture());
        ordered.verify(efsApiClient).send(eq(EfsApiClient.EFS_202), env202.capture());

        // 204 request mapping (§5.6): withd_acct_no=macct, dep_acct_no=tarFincMdaNo,
        // tran_amt_itg=amt, withd_curren_cd=USD, tran_curren_cd=transfer currency (legacy parity).
        String req204 = crypto.decryptEv(env204.getValue().ev(), TEST_KEY);
        assertThat(req204)
                .contains("\"bank_cd\":\"970457\"")
                .contains("\"withd_acct_no\":\"12345678901111\"")
                .contains("\"withd_curren_cd\":\"USD\"")
                .contains("\"dep_acct_no\":\"98765432100000\"")
                .contains("\"tran_amt_itg\":\"100\"")
                .contains("\"tran_curren_cd\":\"USD\"");

        // 202 request now carries the FX block fed from the 204 response.
        String req202 = crypto.decryptEv(env202.getValue().ev(), TEST_KEY);
        assertThat(req202)
                .contains("\"withd_curren_cd\":\"USD\"")
                .contains("\"dep_curren_cd\":\"VND\"")
                .contains("\"apl_exc_rate_itg\":\"24500\"")
                .contains("\"apl_exc_rate_dcm\":\"0\"")
                .contains("\"withd_amt_itg\":\"100\"")
                .contains("\"dep_amt_itg\":\"2450000\"")
                .contains("\"dep_amt_dcm\":\"0\"");
    }

    @Test
    void woori_204_failure_throws_and_never_sends_202() {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_204), any())).thenReturn(bareEnvelope("G001"));

        assertThatThrownBy(() -> service().transfer(wooriFxCmd(), TNO))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_EFS_GATEWAY_ERROR");

        // No transfer without a confirmed rate (legacy parity: skip the target).
        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_202), any());
    }

    @Test
    void woori_204_invalidVv_throws_and_never_sends_202() throws Exception {
        // A3: a 204 response whose VV does not verify is a G003 integrity failure → no 202.
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        EfsEnvelope good = success204();
        EfsEnvelope tampered = new EfsEnvelope(good.id(), good.rqDtime(), good.rsDtime(), good.tno(),
                good.ev(), "tampered-vv", good.em(), good.vm(), good.rc(), good.rm());
        when(efsApiClient.send(eq(EfsApiClient.EFS_204), any())).thenReturn(tampered);

        assertThatThrownBy(() -> service().transfer(wooriFxCmd(), TNO))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("WBE_EFS_GATEWAY_ERROR");

        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_202), any());
    }

    @Test
    void domestic_vnd_transfer_skips_the_204_pre_step() throws Exception {
        when(vanApiKeyService.getApiKey("ORG000001")).thenReturn(TEST_KEY);
        when(efsApiClient.send(eq(EfsApiClient.EFS_202), any())).thenReturn(success202("0000"));

        RealtimeTransferResult result = service().transfer(cmd(), TNO); // Shinhan + VND

        assertThat(result.pending()).isFalse();
        verify(efsApiClient, never()).send(eq(EfsApiClient.EFS_204), any());

        // The FX block is still present on the wire, but empty (legacy param_202 parity).
        ArgumentCaptor<EfsEnvelope> env202 = ArgumentCaptor.forClass(EfsEnvelope.class);
        verify(efsApiClient).send(eq(EfsApiClient.EFS_202), env202.capture());
        String req202 = crypto.decryptEv(env202.getValue().ev(), TEST_KEY);
        assertThat(req202)
                .contains("\"dep_curren_cd\":\"\"")
                .contains("\"apl_exc_rate_itg\":\"\"")
                .contains("\"withd_amt_itg\":\"\"");
    }
}
