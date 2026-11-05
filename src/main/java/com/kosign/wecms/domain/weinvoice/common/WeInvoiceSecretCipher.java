package com.kosign.wecms.domain.weinvoice.common;

import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts the GDT passwords WeInvoice stores for the engine
 * ({@code winv_cmpn_infm.gdt_pw_enc}).
 *
 * <p>Format: {@code "v1:" + base64(iv[12] || AES-256-GCM(ciphertext || tag[16]))}, key =
 * base64-decoded {@code WECMS_WEINVOICE_SECRET_KEY}. The Python engine decrypts with
 * {@code cryptography}'s {@code AESGCM(key).decrypt(iv, data[12:], None)}.
 *
 * <p><b>Why not {@code BankCertCryptoService}.</b> That cipher reproduces the legacy JEX
 * pgcrypto scheme (fixed key, zero IV) only so WeCMS and the live JEX system can read the
 * same {@code BANK_CERT_INFO} / {@code GDT_CERT_INFO} rows. This table is not shared with
 * JEX, so the coexistence constraint does not apply; and giving the engine that key would
 * let it decrypt every WeCMS bank credential. A random IV per value and a WeInvoice-only
 * key avoid both problems.
 */
@Component
public class WeInvoiceSecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public WeInvoiceSecretCipher(WeInvoiceProperties props) {
        // Validated by WeInvoiceProperties when enabled; when disabled the cipher is never used.
        this.key = props.enabled() ? new SecretKeySpec(Base64.getDecoder().decode(props.secretKey().trim()), "AES") : null;
    }

    WeInvoiceSecretCipher(byte[] rawKey) {
        this.key = new SecretKeySpec(rawKey, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array();
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    /** Inverse of {@link #encrypt}. Throws when the value was not produced with this key (tampered or foreign). */
    public String decrypt(String value) {
        if (value == null) return null;
        requireKey();
        if (!value.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported secret format");
        }
        try {
            byte[] in = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("Secret cannot be decrypted with the configured key", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("WeInvoice secret key is not configured");
        }
    }
}
