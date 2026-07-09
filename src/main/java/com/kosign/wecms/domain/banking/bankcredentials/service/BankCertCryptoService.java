package com.kosign.wecms.domain.banking.bankcredentials.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Legacy-compatible symmetric cipher for the bank/GDT credential
 * password columns ({@code BANK_CERT_INFO.CERT_INFO_DETL_CON} and
 * {@code GDT_CERT_INFO.CERT_INFO_DETL_CON}).
 *
 * <p><b>Wire/at-rest format is byte-identical to the legacy JEX DB
 * functions</b> {@code public.encrypt(txt)} / {@code public.decrypt(txt)}:
 * <pre>
 *   public.encrypt(txt) = encode(encrypt(txt::bytea,
 *                                 digest('wabooksvn','sha256'), 'aes'), 'base64')
 *   public.decrypt(txt) = convert_from(decrypt(decode(txt,'base64'),
 *                                 digest('wabooksvn','sha256'), 'aes'), 'utf-8')
 * </pre>
 * pgcrypto's {@code encrypt(data, key, 'aes')} is <b>AES-256-CBC with an
 * all-zero IV and PKCS padding</b> (the key is the raw 32-byte SHA-256
 * digest of the seed, hence AES-256). This service reproduces exactly
 * that so the refactor can:
 * <ul>
 *   <li>read every legacy {@code CERT_INFO_DETL_CON} ciphertext already
 *       in the JEX DB (177 bank + 38 GDT rows on DEV), and</li>
 *   <li>write ciphertext that the live JEX system's {@code decrypt()}
 *       reads back unchanged — true two-way coexistence on a shared DB.</li>
 * </ul>
 *
 * <p>This intentionally replaces the earlier refactor-only AES-GCM
 * cipher, which was mutually unreadable with legacy data (see
 * {@code docs/audits/login-credentials-db-object-audit.md}, gap D1). The
 * AES-GCM key config slot {@code wecms.crypto.cert-key} is retired; this
 * cipher derives its key from {@code wecms.crypto.legacy-cert-seed}
 * (default {@code "wabooksvn"} — the legacy seed; do not change unless
 * the legacy DB function changes).
 *
 * <p><b>Security note:</b> the legacy scheme is a fixed-key, zero-IV CBC
 * cipher (deterministic — equal plaintexts yield equal ciphertexts).
 * This is weaker than an IV-randomised AEAD, but legacy-data
 * compatibility is the overriding requirement here (migration-safety
 * over design improvement, per the workspace refactor rules). Any move
 * to a stronger scheme must be a coordinated PO/DBA migration that
 * re-encrypts every existing row and cuts the live JEX system off these
 * columns first.
 */
@Service
public class BankCertCryptoService {

    /** pgcrypto {@code encrypt(data,key,'aes')} default = AES/CBC/PKCS. */
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";

    /** pgcrypto's no-IV {@code encrypt(...)} uses an all-zero IV. */
    private static final byte[] ZERO_IV = new byte[16];

    private final String seed;
    private SecretKey secretKey;

    public BankCertCryptoService(
            @Value("${wecms.crypto.legacy-cert-seed:wabooksvn}") String seed) {
        this.seed = seed;
    }

    @PostConstruct
    void init() {
        if (seed == null || seed.isEmpty()) {
            throw new IllegalStateException(
                    "wecms.crypto.legacy-cert-seed must not be blank — it derives the "
                    + "legacy-compatible AES key (SHA-256 of the seed).");
        }
        try {
            // SHA-256(seed) → 32 raw bytes → AES-256, matching
            // digest('<seed>','sha256') used by the legacy DB function.
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(seed.getBytes(StandardCharsets.UTF_8));
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive legacy cert key", e);
        }
    }

    /** Encrypt to the legacy base64 format. {@code null} → {@code null}
     *  (mirrors the STRICT legacy SQL function). */
    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(ZERO_IV));
            byte[] out = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypt a legacy base64 ciphertext. {@code null} → {@code null}.
     *
     * <p>Plaintext-tolerant: a value that is not valid base64, or that
     * base64-decodes to a non-decryptable blob (wrong block size / bad
     * padding), is returned <i>verbatim</i>. This protects callers that
     * defensively decrypt columns stored in the clear
     * ({@code CERT_INFO_DETL_CON2}/{@code CON3}) and any manually-imported
     * row, and mirrors the tolerance the previous implementation relied
     * on. Valid legacy ciphertext always decrypts cleanly.
     */
    public String decrypt(String ciphertextB64) {
        if (ciphertextB64 == null) return null;
        try {
            byte[] in = Base64.getDecoder().decode(ciphertextB64);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ZERO_IV));
            byte[] plain = cipher.doFinal(in);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException notBase64) {
            // Column value is not base64 → treat as plaintext.
            return ciphertextB64;
        } catch (Exception cryptoFailure) {
            // Base64 but not a valid AES-CBC payload for this key → echo
            // the input so the surrounding flow can decide (matches legacy
            // tolerance; avoids hard-crashing on a plaintext/legacy blob).
            return ciphertextB64;
        }
    }

    /**
     * Strict decrypt for credential columns that MUST be genuine legacy
     * ciphertext — namely the bank login password
     * {@code CERT_INFO_DETL_CON}, which is forwarded verbatim to the
     * NXiSAS engine (and on to the bank) as the IB login password.
     *
     * <p>Unlike {@link #decrypt(String)}, a value that is not valid
     * base64, is not block-aligned, or does not decrypt cleanly under the
     * legacy key returns {@code null} — <b>never the undecryptable input</b>.
     * The tolerant {@code decrypt(...)} fallback is correct for
     * {@code CON2}/{@code CON3} (sometimes stored in the clear), but for
     * the password it is actively harmful: echoing a foreign-cipher blob
     * (e.g. a row written by the <i>retired</i> refactor AES-GCM cipher,
     * whose base64 decodes to a non-16-byte-multiple length) ships that
     * blob to the bank as the password, which the bank rejects with an
     * opaque institution error (Shinhan {@code 8000FFFF}).
     *
     * <p>Callers treat {@code null} as "no usable credential" — the engine
     * is never handed a value, and the UI surfaces a clear "re-register
     * credential" path instead of a cryptic bank failure. The legitimate
     * cure for a {@code null} here is to re-save the credential via
     * Settings &gt; Login Credentials, which re-encrypts it with this
     * service's legacy-compatible {@link #encrypt(String)}.
     */
    public String decryptStrict(String ciphertextB64) {
        if (ciphertextB64 == null || ciphertextB64.isEmpty()) return null;
        try {
            byte[] in = Base64.getDecoder().decode(ciphertextB64);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(ZERO_IV));
            byte[] plain = cipher.doFinal(in);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception notLegacyCiphertext) {
            // Not decryptable under the legacy key → NOT a usable password.
            // Return null so callers fail safe; never echo the input.
            return null;
        }
    }
}
