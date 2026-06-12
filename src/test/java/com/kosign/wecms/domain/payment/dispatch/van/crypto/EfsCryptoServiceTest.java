package com.kosign.wecms.domain.payment.dispatch.van.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Offline crypto tests for {@link EfsCryptoService} — pure JUnit, no Spring
 * context, no DB, no network.
 *
 * <p>Uses ONLY the <b>published TEST key</b> from the REST API guide ver 1.2.2
 * §6 sample code ({@code analysis/efs-service-mapping-from-pdf.md}). The real
 * per-VAN {@code API_KEY} is never used here (B2).
 */
class EfsCryptoServiceTest {

    /** Published TEST key (PDF §6.1/§6.2) — 32 bytes = AES-256. NOT a real key. */
    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";

    private static final String RQ_DTIME = "20240422103822"; // 14 chars (PDF sample)
    private static final String JSON =
            "{\"bank_cd\":\"970424\",\"prtn_id\":\"ORG000001\",\"tar_bank_cd\":\"970424\","
                    + "\"tar_finc_mda_div\":\"A\",\"tar_finc_mda_no\":\"12345678901111\"}";

    private final EfsCryptoService crypto = new EfsCryptoService();

    @Test
    void encrypt_then_decrypt_round_trips_to_original_json() {
        String ev = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        assertThat(ev).isNotBlank();

        String decrypted = crypto.decryptEv(ev, TEST_KEY);
        assertThat(decrypted).isEqualTo(JSON); // 14-char RQ_DTIME prefix stripped on decrypt
    }

    @Test
    void verifyVv_true_for_matching_envelope() {
        String ev = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        String vv = crypto.buildVv(JSON, TEST_KEY);

        assertThat(crypto.verifyVv(ev, vv, TEST_KEY)).isTrue();
    }

    @Test
    void verifyVv_false_when_json_is_tampered() {
        // EV encrypts the original JSON; VV is computed over a DIFFERENT (tampered) JSON.
        String ev = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        String tamperedJson = JSON.replace("970424", "999999");
        String vvForTampered = crypto.buildVv(tamperedJson, TEST_KEY);

        assertThat(crypto.verifyVv(ev, vvForTampered, TEST_KEY)).isFalse();
    }

    @Test
    void encryptEv_is_deterministic_for_same_rqDtime_json_key() {
        String ev1 = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        String ev2 = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        assertThat(ev1).isEqualTo(ev2); // fixed zero IV ⇒ deterministic ciphertext

        // sanity: different JSON ⇒ different EV
        String evOther = crypto.encryptEv(JSON.replace("970424", "970457"), TEST_KEY, RQ_DTIME);
        assertThat(evOther).isNotEqualTo(ev1);
    }

    @Test
    void encryptEv_rejects_wrong_length_rqDtime() {
        assertThatThrownBy(() -> crypto.encryptEv(JSON, TEST_KEY, "2024"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyVv_false_on_malformed_ev_never_throws() {
        assertThat(crypto.verifyVv("not-a-valid-ev", crypto.buildVv(JSON, TEST_KEY), TEST_KEY)).isFalse();
    }
}
