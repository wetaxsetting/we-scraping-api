package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.kosign.wecms.common.nxisas.SeedCbcDecryptService;

import com.coocon.securty.util.ISASSeedCBC;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasDecryptRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasDecryptResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins that {@link OatNxisasService#decrypt} and {@link OatNxisasService#encrypt}
 * run the Coocon SEED-CBC cipher <b>unconditionally</b>, matching legacy
 * {@code nxisas_decrypt_act.jsp} and the bundle's decrypt-by-default guard at
 * {@code nxisas_v2.js:2353}.
 *
 * <p>Replaces {@code OatNxisasServiceSeedFlagTest} (deleted 2026-08-18), which
 * asserted the opposite for the OFF case — that a BALANCE payload came back as
 * ciphertext untouched. That was the documented pre-B1 behaviour of the
 * {@code wecms.oat.seed-decrypt.enabled} flag, and it is exactly the defect this
 * change removes: with the flag default-off and set in no profile, Update
 * Balance could never reach {@code Result.balance[0]} and failed with
 * "Balance is required."
 *
 * <p>Regression value: if anyone reintroduces a gate (a flag, a bank allow-list,
 * a "plaintext fast path"), {@link #decrypt_isUnconditional_forBalancePayload}
 * fails because the response still carries the ciphertext string.
 */
class OatNxisasServiceSeedDecryptTest {

    private final ObjectMapper            mapper  = new ObjectMapper();
    private final SeedCbcDecryptService   seedCbc = new SeedCbcDecryptService(mapper);

    private static final String UID    = "U".repeat(40);
    private static final String ACTION = "A".repeat(40);
    private static final String BAL    = "{\"balance\":[{\"availableBal\":\"100000\"}]}";

    @BeforeEach
    void bindTenant() {
        TenantContext.set("tester", "VN_VNM0004_000001", LangCd.EN);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private OatNxisasService service() {
        return new OatNxisasService(
                mock(BankCertInfoRepository.class),
                mock(BankCertCryptoService.class),
                mock(NxsiasModuleTranLogHisRepository.class),
                mock(ExtTranCallHisRepository.class),
                mapper,
                seedCbc,
                mock(com.kosign.wecms.common.legacy.LegacyFunctionRepository.class));
    }

    /** The shape the engine actually returns: {@code Output.Result} is ciphertext. */
    private NxisasDecryptRequest balanceRequest() {
        String cipher = ISASSeedCBC.encrypt(BAL, UID, ACTION);
        String envelope = "[{\"Job\":\"BALANCE\",\"Output\":{\"ErrorCode\":\"00000000\",\"Result\":\""
                + cipher + "\"}}]";
        return new NxisasDecryptRequest(envelope, UID, ACTION);
    }

    @Test
    @DisplayName("decrypt runs with no flag: BALANCE Result becomes a parsed object")
    void decrypt_isUnconditional_forBalancePayload() throws Exception {
        NxisasDecryptRequest req = balanceRequest();

        NxisasDecryptResponse resp = service().decrypt(req);

        assertThat(resp.nxisasData())
                .as("payload must be transformed, not echoed back as ciphertext")
                .isNotEqualTo(req.nxisasData());

        JsonNode result = mapper.readTree(resp.nxisasData()).get(0).get("Output").get("Result");
        assertThat(result.get("balance").get(0).get("availableBal").asText()).isEqualTo("100000");
    }

    @Test
    @DisplayName("decrypt runs on ErrorCode 00000000 too — the engine encrypts success payloads")
    void decrypt_appliesEvenOnSuccessCode() throws Exception {
        NxisasDecryptResponse resp = service().decrypt(balanceRequest());

        JsonNode job = mapper.readTree(resp.nxisasData()).get(0);
        assertThat(job.get("Output").get("ErrorCode").asText()).isEqualTo("00000000");
        assertThat(job.get("Output").get("Result").isObject())
                .as("Result must be an object after decrypt, not the ENC= string")
                .isTrue();
    }

    @Test
    @DisplayName("encrypt runs with no flag: round-trips back to the original payload")
    void encrypt_isUnconditional_andRoundTrips() throws Exception {
        String plain = "[{\"Job\":\"TRANSFER\",\"Output\":{\"ErrorCode\":\"00000000\",\"Result\":"
                + "{\"transfer\":[{\"remitAmt\":\"1000\"}]}}}]";

        NxisasDecryptResponse encrypted =
                service().encrypt(new NxisasDecryptRequest(plain, UID, ACTION));

        assertThat(encrypted.nxisasData())
                .as("encrypt must transform, not pass through")
                .isNotEqualTo(plain);

        NxisasDecryptResponse back =
                service().decrypt(new NxisasDecryptRequest(encrypted.nxisasData(), UID, ACTION));

        JsonNode result = mapper.readTree(back.nxisasData()).get(0).get("Output").get("Result");
        assertThat(result.get("transfer").get(0).get("remitAmt").asText()).isEqualTo("1000");
    }
}
