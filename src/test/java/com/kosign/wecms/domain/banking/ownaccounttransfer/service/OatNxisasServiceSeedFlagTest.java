package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.coocon.securty.util.ISASSeedCBC;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.bankcredentials.repository.BankCertInfoRepository;
import com.kosign.wecms.domain.banking.bankcredentials.service.BankCertCryptoService;
import com.kosign.wecms.domain.banking.nxisas.audit.NxsiasModuleTranLogHisRepository;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.banking.ownaccounttransfer.config.OatSeedDecryptProperties;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.request.NxisasDecryptRequest;
import com.kosign.wecms.domain.banking.ownaccounttransfer.dto.response.NxisasDecryptResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the B1 feature flag {@code wecms.oat.seed-decrypt.enabled} on
 * {@link OatNxisasService}: OFF (default) leaves {@code NXISAS_DATA}
 * unchanged (no runtime behaviour change); ON runs the SEED-CBC decrypt.
 */
class OatNxisasServiceSeedFlagTest {

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

    private OatNxisasService service(boolean enabled) {
        return new OatNxisasService(
                mock(BankCertInfoRepository.class),
                mock(BankCertCryptoService.class),
                mock(NxsiasModuleTranLogHisRepository.class),
                mock(ExtTranCallHisRepository.class),
                mapper,
                seedCbc,
                new OatSeedDecryptProperties(enabled),
                mock(com.kosign.wecms.common.legacy.LegacyFunctionRepository.class));
    }

    private NxisasDecryptRequest balanceRequest() {
        String cipher = ISASSeedCBC.encrypt(BAL, UID, ACTION);
        String envelope = "[{\"Job\":\"BALANCE\",\"Output\":{\"ErrorCode\":\"00000000\",\"Result\":\""
                + cipher + "\"}}]";
        return new NxisasDecryptRequest(envelope, UID, ACTION);
    }

    @Test
    void flagOff_returnsCiphertextUnchanged() {
        NxisasDecryptRequest req = balanceRequest();
        NxisasDecryptResponse resp = service(false).decrypt(req);
        assertThat(resp.NXISAS_DATA()).isEqualTo(req.NXISAS_DATA());   // pre-B1 pass-through
    }

    @Test
    void flagOn_decryptsBalanceObject() throws Exception {
        NxisasDecryptResponse resp = service(true).decrypt(balanceRequest());
        JsonNode result = mapper.readTree(resp.NXISAS_DATA()).get(0).get("Output").get("Result");
        assertThat(result.get("balance").get(0).get("availableBal").asText()).isEqualTo("100000");
    }
}
