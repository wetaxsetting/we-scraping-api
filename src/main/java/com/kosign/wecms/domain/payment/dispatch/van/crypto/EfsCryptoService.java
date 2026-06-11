package com.kosign.wecms.domain.payment.dispatch.van.crypto;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * EFS crypto envelope service — refactor of legacy {@code util.SecurityUtil} /
 * Coocon {@code CcSecurityUtil}. Builds and verifies the {@code EV}/{@code VV}
 * envelope fields used by every {@code WGVN_EFS_*} call.
 *
 * <p><b>Algorithm (verified against the REST API guide ver 1.2.2 §4 + §6 sample
 * code — see {@code analysis/efs-service-mapping-from-pdf.md}):</b>
 * <ul>
 *   <li><b>Cipher:</b> {@code AES-256/CBC/PKCS5Padding}, fixed IV = 16 bytes of
 *       {@code 0x00}. Key = the UTF-8 bytes of the per-VAN {@code API_KEY}
 *       (32 chars = 256-bit), used verbatim (no hashing/padding) — matches the
 *       PDF's {@code aes.Key = Encoding.UTF8.GetBytes(key)}.</li>
 *   <li><b>EV</b> = {@code urlEncode(Base64(AES( RQ_DTIME(14) + businessJson )))}
 *       — the 14-char {@code yyyyMMddHHmmss} datetime is prepended before
 *       encryption.</li>
 *   <li><b>VV</b> = {@code urlEncode(Base64(HMAC-SHA256(businessJson)))} — the
 *       HMAC is over the JSON only (NOT the 14-char-prefixed string).</li>
 *   <li><b>Decrypt</b> = AES-decrypt then strip the leading 14 chars → business JSON.</li>
 *   <li>Wire markers: {@code EM=AES}, {@code VM=HmacSHA256}.</li>
 * </ul>
 *
 * <p>This service is <b>pure offline crypto</b> — it does NOT make EFS calls and
 * is unrelated to the fixed-length file codec (K3) or bulk recovery (K9). It is
 * unit-tested with the <b>published TEST key</b> only; the real per-VAN key is
 * provisioned out-of-band into {@code TB_VAN_API_INFO.API_KEY}
 * ({@link com.kosign.wecms.domain.payment.dispatch.van.client.VanApiKeyService}) — <b>B2</b>.
 *
 * <p><b>TODO(B5):</b> the URL-encoding here is a single, self-consistent
 * encode-on-output / decode-on-input pass that round-trips correctly. The legacy
 * {@code SecurityUtil} hinted at a "2-pass URL-decode" on some gateways, and the
 * PDF's reference snippet applies its own (vendor-quirky) encoding. Before going
 * live, byte-verify {@code EV}/{@code VV} against a real captured gateway sample
 * (B5) and adjust the pass count if — and only if — the sample requires it. Do
 * not speculatively add passes.
 */
@Service
public class EfsCryptoService {

    private static final String AES_TRANSFORM = "AES/CBC/PKCS5Padding";
    private static final String AES_ALGO = "AES";
    private static final String HMAC_ALGO = "HmacSHA256";

    /** Fixed all-zero 16-byte IV (PDF §6: {@code aes.IV = new byte[16]{0x00…}}). */
    private static final byte[] ZERO_IV = new byte[16];

    /** Length of the {@code yyyyMMddHHmmss} datetime prefix prepended before encryption. */
    private static final int RQ_DTIME_PREFIX_LEN = 14;

    /**
     * Build the {@code EV} field: {@code urlEncode(Base64(AES(rqDtime + json)))}.
     *
     * @param businessJson the decrypted business payload (the {@code EV} JSON body)
     * @param apiKey       per-VAN AES key (UTF-8, 16/24/32 bytes)
     * @param rqDtime      exactly 14 chars, {@code yyyyMMddHHmmss}
     */
    public String encryptEv(String businessJson, String apiKey, String rqDtime) {
        if (businessJson == null) {
            throw new IllegalArgumentException("businessJson is required");
        }
        if (rqDtime == null || rqDtime.length() != RQ_DTIME_PREFIX_LEN) {
            throw new IllegalArgumentException("rqDtime must be exactly 14 chars (yyyyMMddHHmmss)");
        }
        byte[] plain = (rqDtime + businessJson).getBytes(StandardCharsets.UTF_8);
        byte[] cipher = aes(Cipher.ENCRYPT_MODE, apiKey, plain);
        return urlEncode(Base64.getEncoder().encodeToString(cipher));
    }

    /**
     * Decrypt an {@code EV} field back to the business JSON, stripping the
     * leading 14-char datetime prefix.
     */
    public String decryptEv(String ev, String apiKey) {
        if (ev == null) {
            throw new IllegalArgumentException("ev is required");
        }
        byte[] cipher = Base64.getDecoder().decode(urlDecode(ev));
        byte[] plain = aes(Cipher.DECRYPT_MODE, apiKey, cipher);
        String withPrefix = new String(plain, StandardCharsets.UTF_8);
        if (withPrefix.length() < RQ_DTIME_PREFIX_LEN) {
            throw new IllegalStateException("decrypted payload is shorter than the 14-char RQ_DTIME prefix");
        }
        return withPrefix.substring(RQ_DTIME_PREFIX_LEN);
    }

    /**
     * Build the {@code VV} field for an outbound request:
     * {@code urlEncode(Base64(HMAC-SHA256(businessJson)))}.
     */
    public String buildVv(String businessJson, String apiKey) {
        if (businessJson == null) {
            throw new IllegalArgumentException("businessJson is required");
        }
        return urlEncode(hmacBase64(businessJson, apiKey));
    }

    /**
     * Verify a response {@code VV} against its {@code EV}: decrypt {@code EV} to
     * the JSON, recompute the HMAC, and constant-time-compare to the supplied
     * {@code VV}. Returns {@code false} (never throws) on any decrypt/format
     * failure so a malformed/tampered envelope is rejected cleanly.
     */
    public boolean verifyVv(String ev, String vv, String apiKey) {
        if (vv == null) {
            return false;
        }
        final String json;
        try {
            json = decryptEv(ev, apiKey);
        } catch (RuntimeException e) {
            return false;
        }
        String expectedBase64 = hmacBase64(json, apiKey);
        String providedBase64 = urlDecode(vv);
        return constantTimeEquals(expectedBase64, providedBase64);
    }

    // ── internals ───────────────────────────────────────────────────────────

    private byte[] aes(int mode, String apiKey, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(AES_TRANSFORM);
            cipher.init(mode, new SecretKeySpec(requireKeyBytes(apiKey), AES_ALGO), new IvParameterSpec(ZERO_IV));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "EFS AES " + (mode == Cipher.ENCRYPT_MODE ? "encrypt" : "decrypt") + " failed", e);
        }
    }

    private String hmacBase64(String json, String apiKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(requireKeyBytes(apiKey), HMAC_ALGO));
            byte[] sig = mac.doFinal(json.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("EFS HMAC-SHA256 failed", e);
        }
    }

    private byte[] requireKeyBytes(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                    "EFS apiKey is required — provision TB_VAN_API_INFO.API_KEY (B2).");
        }
        byte[] bytes = apiKey.getBytes(StandardCharsets.UTF_8);
        if (bytes.length != 16 && bytes.length != 24 && bytes.length != 32) {
            throw new IllegalArgumentException(
                    "EFS apiKey must be 16/24/32 bytes for AES (got " + bytes.length + ").");
        }
        return bytes;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
