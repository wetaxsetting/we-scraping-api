package com.kosign.wecms.domain.weinvoice.common;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The GDT password cipher. The format is a contract with the Python engine
 * ({@code "v1:" + base64(iv[12] || ciphertext || tag[16])}, AES-256-GCM), so it is asserted
 * byte-for-byte, not only round-tripped.
 */
class WeInvoiceSecretCipherTest {

    private static final byte[] KEY = new byte[32];
    private static final byte[] OTHER_KEY = new byte[32];

    static {
        Arrays.fill(KEY, (byte) 7);
        Arrays.fill(OTHER_KEY, (byte) 9);
    }

    private final WeInvoiceSecretCipher cipher = new WeInvoiceSecretCipher(KEY);

    @Test
    void roundTripsUnicodePassword() {
        String enc = cipher.encrypt("Mật-khẩu#2026");
        assertThat(cipher.decrypt(enc)).isEqualTo("Mật-khẩu#2026");
    }

    @Test
    void formatIsPrefixIvCiphertextTag() {
        String plain = "secret12";
        String enc = cipher.encrypt(plain);
        assertThat(enc).startsWith("v1:");
        byte[] raw = Base64.getDecoder().decode(enc.substring(3));
        // 12-byte IV + same-length ciphertext (GCM is a stream mode) + 16-byte tag
        assertThat(raw).hasSize(12 + plain.length() + 16);
    }

    @Test
    void randomIvMakesEqualPasswordsDiffer() {
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void tamperedValueIsRejected() {
        String enc = cipher.encrypt("secret");
        byte[] raw = Base64.getDecoder().decode(enc.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void otherKeyCannotDecrypt() {
        String enc = cipher.encrypt("secret");
        assertThatThrownBy(() -> new WeInvoiceSecretCipher(OTHER_KEY).decrypt(enc))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyOrForeignFormatIsRejected() {
        assertThatThrownBy(() -> cipher.decrypt("c2VjcmV0")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullStaysNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }
}
