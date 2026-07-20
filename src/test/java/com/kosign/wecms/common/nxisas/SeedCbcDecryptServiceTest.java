package com.kosign.wecms.common.nxisas;

import com.coocon.securty.util.ISASSeedCBC;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SeedCbcDecryptService} — the B1 SEED-CBC port.
 *
 * <p>Drives the <b>real</b> Coocon cipher ({@code libs/isas1.0.jar}). We
 * cannot unit-decrypt the captured Shinhan ciphertext because its session
 * {@code NXISAS_UID}/{@code NXISAS_ACTION} were not committed (session
 * secrets); instead these tests round-trip through the same jar
 * ({@code encrypt} → service {@code decrypt}) to prove the integration
 * mirrors legacy {@code nxisas_decrypt_act.jsp} end-to-end. Live BALANCE
 * verification with real session keys remains the final gate
 * (b1-live-verification-plan.md).
 */
class SeedCbcDecryptServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SeedCbcDecryptService service = new SeedCbcDecryptService(mapper);

    /** 40-char session values — the bundle generates 40-char Uid/Action,
     *  so the keyed 3-arg cipher path (legacy :79) is exercised. */
    private static final String UID    = "U".repeat(40);
    private static final String ACTION = "A".repeat(40);

    private static final String BALANCE_JSON =
            "{\"balance\":[{\"acctNum\":\"700005285280\",\"balance\":\"123456\",\"availableBal\":\"100000\"}]}";
    private static final String TRANSFER_JSON =
            "{\"transfer\":[{\"acctNum\":\"700005285280\",\"currency\":\"VND\",\"remitAmt\":\"50000\",\"fee\":\"0\"}]}";

    private String envelope(String job, String result) {
        return "[{\"Module\":\"GLOBAL/GBL/BANK_Shinhan\",\"Class\":\"COMMON\",\"Job\":\"" + job + "\","
                + "\"ErrorLang\":\"en\",\"Output\":{\"ErrorCode\":\"00000000\",\"ErrorMessage\":\"\","
                + "\"Result\":\"" + result + "\"}}]";
    }

    @Test
    void decrypts_balance_result_into_object_like_legacy() throws Exception {
        String cipher = ISASSeedCBC.encrypt(BALANCE_JSON, UID, ACTION);
        String out = service.decryptNxisasData(envelope("BALANCE", cipher), UID, ACTION);

        JsonNode result = mapper.readTree(out).get(0).get("Output").get("Result");
        // Result is now a parsed object (legacy :90), not the ciphertext string.
        assertThat(result.isObject()).isTrue();
        JsonNode bal0 = result.get("balance").get(0);
        assertThat(bal0.get("acctNum").asText()).isEqualTo("700005285280");
        assertThat(bal0.get("balance").asText()).isEqualTo("123456");
        assertThat(bal0.get("availableBal").asText()).isEqualTo("100000");
    }

    @Test
    void decrypts_nested_org_obj_result_array_shape() throws Exception {
        // The REAL engine shape `runScraping` resolves with: an array of
        // org-objs, each with a `result[]` of job records carrying the
        // nested Output.Result ciphertext (legacy trns_0001_007.ts:1635-1656).
        // A flat-only walk misses this — regression guard for the
        // 2026-06-05 nested-traversal fix.
        String cipher = ISASSeedCBC.encrypt(BALANCE_JSON, UID, ACTION);
        String nested = "[{\"bankCd\":\"11704088\",\"job\":[\"BALANCE\"],\"result\":[{"
                + "\"Module\":\"GLOBAL/GBL/BANK_Shinhan\",\"Class\":\"COMMON\",\"Job\":\"BALANCE\","
                + "\"Output\":{\"ErrorCode\":\"00000000\",\"ErrorMessage\":\"\",\"Result\":\"" + cipher + "\"}}]}]";

        String out = service.decryptNxisasData(nested, UID, ACTION);

        JsonNode result = mapper.readTree(out).get(0).get("result").get(0).get("Output").get("Result");
        assertThat(result.isObject()).isTrue();
        assertThat(result.get("balance").get(0).get("availableBal").asText()).isEqualTo("100000");
    }

    @Test
    void decrypts_when_result_carries_legacy_ENC_prefix() throws Exception {
        // Bundle sometimes prefixes ciphertext with "ENC="; the service must
        // strip it (legacy :73) before decrypting. Shinhan's live capture had
        // NO prefix — this proves both forms work.
        String cipher = "ENC=" + ISASSeedCBC.encrypt(BALANCE_JSON, UID, ACTION);
        String out = service.decryptNxisasData(envelope("BALANCE", cipher), UID, ACTION);

        JsonNode result = mapper.readTree(out).get(0).get("Output").get("Result");
        assertThat(result.get("balance").get(0).get("availableBal").asText()).isEqualTo("100000");
    }

    @Test
    void decrypts_transfer_result_into_object() throws Exception {
        String cipher = ISASSeedCBC.encrypt(TRANSFER_JSON, UID, ACTION);
        String out = service.decryptNxisasData(envelope("TRANSFER", cipher), UID, ACTION);

        JsonNode t0 = mapper.readTree(out).get(0).get("Output").get("Result").get("transfer").get(0);
        assertThat(t0.get("remitAmt").asText()).isEqualTo("50000");
        assertThat(t0.get("currency").asText()).isEqualTo("VND");
    }

    @Test
    void encrypt_then_decrypt_roundtrips_through_the_service() throws Exception {
        // Start from a plaintext-Result envelope, encrypt it, then decrypt it.
        String plainEnvelope = "[{\"Job\":\"BALANCE\",\"Output\":{\"ErrorCode\":\"00000000\",\"Result\":"
                + BALANCE_JSON + "}}]";
        String encrypted = service.encryptNxisasData(plainEnvelope, UID, ACTION);
        // After encrypt, Result is a base64 string, not the object.
        assertThat(mapper.readTree(encrypted).get(0).get("Output").get("Result").isTextual()).isTrue();

        String decrypted = service.decryptNxisasData(encrypted, UID, ACTION);
        JsonNode bal0 = mapper.readTree(decrypted).get(0).get("Output").get("Result").get("balance").get(0);
        assertThat(bal0.get("availableBal").asText()).isEqualTo("100000");
    }

    @Test
    void tolerant_keeps_undecryptable_result_as_string_without_throwing() throws Exception {
        // A Result that isn't a real ciphertext must NOT crash the array
        // (legacy :84-86 tolerance). The cipher yields non-JSON output, so
        // the service keeps Result as a string node rather than an object —
        // the key guarantee is "no throw, envelope still valid, Result not
        // parsed into a balance/transfer object".
        String out = service.decryptNxisasData(envelope("BALANCE", "not-a-real-cipher=="), UID, ACTION);
        JsonNode result = mapper.readTree(out).get(0).get("Output").get("Result");
        assertThat(result.isObject()).isFalse();
        assertThat(result.isTextual()).isTrue();
    }

    @Test
    void passes_through_non_array_and_blank_input() {
        assertThat(service.decryptNxisasData(null, UID, ACTION)).isNull();
        assertThat(service.decryptNxisasData("", UID, ACTION)).isEmpty();
        assertThat(service.decryptNxisasData("{\"not\":\"array\"}", UID, ACTION))
                .isEqualTo("{\"not\":\"array\"}");
    }
}
