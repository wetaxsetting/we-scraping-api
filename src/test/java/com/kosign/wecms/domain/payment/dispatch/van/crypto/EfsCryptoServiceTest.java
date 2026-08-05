package com.kosign.wecms.domain.payment.dispatch.van.crypto;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

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

    // ── C-15: legacy's post-decrypt plaintext validation (SecurityUtil:278-293) ──────────────
    //
    // A non-throwing AES decrypt is not evidence that the right bytes went in — CBC + PKCS5 only
    // rejects a wrong plaintext when the final block's padding happens to be invalid. These tests
    // pin the three shapes that must now be REJECTED rather than returned.

    /**
     * The wrong-key case the validation exists for: alphabet-clean, correctly padded, survives
     * PKCS5 unpadding, decrypts to garbage.
     *
     * <p>A random wrong key fails at unpadding ~99.6% of the time, so this key is not arbitrary —
     * it was found by searching {@code "wrongkey" + i} (padded to 32 bytes) against the fixed
     * {@link #JSON} ciphertext for the first value whose decrypt unpads cleanly: {@code i = 112}.
     * It yields 131 bytes of binary garbage with a non-numeric prefix. <b>Before C-15 this call
     * returned that garbage</b>, which then reached the caller's Jackson bind; it must now throw.
     *
     * <p>Do not "simplify" this to a readable wrong key — a readable one almost certainly fails at
     * unpadding instead, which exercises {@code aes()} rather than the validation and silently
     * stops covering this class.
     */
    @Test
    void decryptEv_throws_on_wrongKey_whose_decrypt_survives_pkcs5_unpadding() {
        String ev = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        String wrongKeyThatUnpadsCleanly = "wrongkey112XXXXXXXXXXXXXXXXXXXXX";
        assertThat(wrongKeyThatUnpadsCleanly).hasSize(32).isNotEqualTo(TEST_KEY);

        assertThatThrownBy(() -> crypto.decryptEv(ev, wrongKeyThatUnpadsCleanly))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("C-15"); // the validation rejected it, not the cipher
    }

    /** The same wrong key through verifyVv — inherited via catch (RuntimeException) → false. */
    @Test
    void verifyVv_false_on_wrongKey_whose_decrypt_survives_pkcs5_unpadding() {
        String ev = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        String vv = crypto.buildVv(JSON, TEST_KEY);

        assertThat(crypto.verifyVv(ev, vv, "wrongkey112XXXXXXXXXXXXXXXXXXXXX")).isFalse();
    }

    /**
     * Non-numeric 14-char prefix → reject, never {@code substring(14)}.
     *
     * <p>This is the deliberate divergence from legacy: {@code SecurityUtil:291-293} validates the
     * WHOLE string as JSON on this branch, certifies it intact, and then every caller chops 14
     * characters off it ({@code NAPAS_REAL_TIME_BATCH:373} etc.). We reject instead.
     */
    @Test
    void decryptEv_throws_when_prefix_is_not_14_digits() {
        // Decrypts perfectly with the right key — only the SHAPE is wrong.
        String ev = rawEv("NOT-A-DATETIME" + JSON);

        assertThatThrownBy(() -> crypto.decryptEv(ev, TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("14-digit RQ_DTIME prefix");
    }

    /** 14-digit prefix but the remainder is not JSON — the truncated/garbled-tail class. */
    @Test
    void decryptEv_throws_when_remainder_is_not_json() {
        String ev = rawEv(RQ_DTIME + "not json at all");

        assertThatThrownBy(() -> crypto.decryptEv(ev, TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not parseable JSON");
    }

    /**
     * A JSON array or scalar is rejected too — legacy used {@code JSONObject.fromObject}, which
     * accepts only an object, and every EV payload on every leg is an object (§4: {@code DateTime +
     * JSONObject Data}). Also covers Jackson's {@code readTree("")} returning {@code MissingNode}
     * rather than throwing, which a bare parse check would let through.
     */
    @Test
    void decryptEv_throws_when_remainder_is_json_but_not_an_object() {
        assertThatThrownBy(() -> crypto.decryptEv(rawEv(RQ_DTIME + "[{\"a\":1}]"), TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a JSON object");

        assertThatThrownBy(() -> crypto.decryptEv(rawEv(RQ_DTIME + "42"), TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a JSON object");

        assertThatThrownBy(() -> crypto.decryptEv(rawEv(RQ_DTIME), TEST_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a JSON object"); // empty remainder → MissingNode
    }

    /** The validation must not reject anything legitimate — the happy path is unchanged. */
    @Test
    void decryptEv_still_accepts_a_wellFormed_payload() {
        assertThat(crypto.decryptEv(crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME), TEST_KEY)).isEqualTo(JSON);
        // Nested objects / arrays INSIDE the payload are fine — only the top level must be an object.
        String nested = "{\"list\":[1,2,3],\"obj\":{\"k\":\"v\"},\"n\":null}";
        assertThat(crypto.decryptEv(crypto.encryptEv(nested, TEST_KEY, RQ_DTIME), TEST_KEY)).isEqualTo(nested);
    }

    /**
     * Builds an {@code EV} from an arbitrary plaintext, bypassing {@link EfsCryptoService#encryptEv}'s
     * 14-char {@code rqDtime} guard. Same cipher, key and zero IV, so the result decrypts cleanly —
     * which is the point: these payloads must be rejected by the plaintext validation, not by AES.
     */
    private static String rawEv(String plaintext) {
        try {
            Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
            c.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(TEST_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(new byte[16]));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return URLEncoder.encode(Base64.getEncoder().encodeToString(ct), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("test fixture could not build a raw EV", e);
        }
    }
}
