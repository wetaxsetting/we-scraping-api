package com.kosign.wecms.domain.banking.bankcredentials.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM encryption for bank-credential password columns. Replaces
 * the legacy Coocon SEED-CBC ({@code ISASSeedCBC}) cipher used by
 * JEX, which is intentionally NOT ported — JCE is in the JDK, has no
 * vendor licence implications, and is the cleaner choice for new
 * data written by the refactor. Legacy SEED-CBC ciphertexts in JEX
 * DEV are NOT migrated to the refactor DB; {@code BANK_CERT_INFO}
 * starts fresh.
 *
 * <p>Wire format (base64): {@code IV(12) || ciphertext || tag(16)},
 * standard for {@code AES/GCM/NoPadding}.
 *
 * <p>Key source: {@code wecms.crypto.cert-key} (Base64, 32 raw bytes
 * for AES-256). For local development a deterministic default keeps
 * the spike runnable out of the box; production deployments override
 * via env var.
 */
@Service
public class BankCertCryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final String configuredKey;
    private SecretKey secretKey;
    private final SecureRandom random = new SecureRandom();

    public BankCertCryptoService(
            @Value("${wecms.crypto.cert-key:r4QF4QyOKZ5OWlszSjpQNkjL7P5wmAv4z0FpAOPmnGo=}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = Base64.getDecoder().decode(configuredKey);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "wecms.crypto.cert-key must decode to 16, 24, or 32 bytes; got " + keyBytes.length);
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] body = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(body, 0, out, iv.length, body.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    public String decrypt(String ciphertextB64) {
        if (ciphertextB64 == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(ciphertextB64);
            if (in.length <= IV_BYTES) {
                throw new IllegalArgumentException("ciphertext shorter than IV");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(in, 0, iv, 0, IV_BYTES);
            byte[] body = new byte[in.length - IV_BYTES];
            System.arraycopy(in, IV_BYTES, body, 0, body.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(body);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential", e);
        }
    }
}
