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
 * <p><b>TODO(B5) — pass count only.</b> The URL-encoding here is a single,
 * self-consistent encode-on-output / decode-on-input pass that round-trips correctly.
 * Legacy instead attempts decryption <b>twice</b>
 * ({@code SecurityUtil.DecryptAesBase64:263-319}, {@code VerifyMac:534-568}): first
 * without URL-decoding, then — if the first attempt threw <i>or</i> produced a plaintext
 * that failed its own sanity check (14 leading digits parseable as a {@code Long}, and the
 * remainder parseable as JSON, {@code :280-293}) — again <b>with</b> URL-decoding.
 *
 * <p>Note the handover does not merely "hint" at this. {@code 08_기능추출_이관가이드.md} §8.4
 * pitfall ③ states a concrete failure mode:
 * <blockquote>
 * 무결성 VV 2-pass: URL디코딩 유/무 두 번 시도. <b>한쪽만 구현하면 간헐적 검증실패.</b><br>
 * <i>"Integrity VV 2-pass: attempt twice, with/without URL-decoding. If you implement only one
 * side you get <b>intermittent</b> verification failures."</i>
 * </blockquote>
 * The legacy comment at {@code SecurityUtil:278-279} gives the mechanism —
 * 데이터 보호화시 URLEncoding데이터가 그대로 복호화가 성공적으로 되는 경우가 있어서
 * ("there are cases where URL-encoded data decrypts 'successfully' as-is"), which is why legacy
 * validates the plaintext rather than trusting a non-throwing decrypt.
 *
 * <p>That claim is <b>credible but unverified</b>, and "intermittent" is exactly the class of bug a
 * green test suite will not catch. It is not, however, actionable without evidence: adding a second
 * pass speculatively changes what is accepted off the wire. So the decision stays gated on B5 —
 * byte-verify {@code EV}/{@code VV} against a real captured gateway sample and add the second pass
 * if, and only if, the sample requires it. <b>Do not speculatively add passes.</b> If an intermittent
 * verify failure is ever observed in SIT/UAT before B5 closes, treat pitfall ③ as the first hypothesis.
 *
 * <p>The unconditional Base64-alphabet repair legacy applies alongside the 2-pass logic was
 * <b>separable and has been ported</b> — see {@link #repairBase64Alphabet}. It is not part of the
 * pass count and did not need to wait for B5.
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
        // D-8: repair the Base64 alphabet before decoding (legacy SecurityUtil:269-271).
        byte[] cipher = Base64.getDecoder().decode(repairBase64Alphabet(urlDecode(ev)));
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
        // D-8: legacy repairs the VV alphabet too, not just the EV (SecurityUtil.VerifyMac:543-545).
        String providedBase64 = repairBase64Alphabet(urlDecode(vv));
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

    /**
     * Legacy Base64-alphabet repair, ported verbatim from {@code SecurityUtil} (audit <b>D-8</b>).
     *
     * <p>Legacy applies these three substitutions <b>unconditionally</b>, to the inbound {@code EV}
     * ({@code SecurityUtil.DecryptAesBase64:269-271}) and to both the {@code EV} and the {@code VV}
     * before a MAC check ({@code SecurityUtil.VerifyMac:540-545}):
     * <pre>
     * input = input.replaceAll(" ", "+");
     * input = input.replaceAll("_", "/");
     * input = input.replaceAll("-", "+");
     * </pre>
     *
     * <p>Each substitution repairs a distinct, real corruption of a standard-Base64 payload:
     * <ul>
     *   <li>{@code ' '} → {@code '+'} — {@code application/x-www-form-urlencoded} decoding turns
     *       {@code '+'} into a space. A {@code '+'} that reached us already form-decoded (by a proxy,
     *       a gateway, or a servlet container that decoded the body before we saw it) arrives as a
     *       space and is not valid Base64.</li>
     *   <li>{@code '_'} → {@code '/'} and {@code '-'} → {@code '+'} — the <b>base64url</b> alphabet
     *       (RFC 4648 §5) substituted for standard Base64.</li>
     * </ul>
     *
     * <p><b>This is not part of the 2-pass logic</b> and does not depend on it: legacy runs it once,
     * before either decrypt attempt, regardless of the {@code urldecode} flag. It is therefore
     * portable independently of {@code TODO(B5)} and is ported here.
     *
     * <h2>Why the {@code ' '} → {@code '+'} corruption exists: a bug in the vendor's own sample</h2>
     *
     * <p>This was an unexplained legacy workaround until 2026-07-31. It is not defensive
     * over-engineering — it compensates for a defect in the reference implementation the guide ships,
     * and therefore in any counterparty built from that reference.
     *
     * <p><b>REST API guide v1.2.6 §6.2 (C# sample, p.38)</b> — note that the header comment says URL
     * <i>encoding</i> while the body performs URL <i>decoding</i>:
     * <pre>
     * /***********************************
     *  * 전송할 데이터 생성
     *  * Base64 변환 및 <b>URL 인코딩</b>(UTF-8)      &#47;&#47; "Base64 conversion and URL ENCODING"
     *  ***********************************&#47;
     * public String EncryptAesBase64(String input, string key)
     * {
     *    string aesInput = AESEncrypt256(input, key);            &#47;&#47; AES256 암호화
     *    return HttpUtility.<b>UrlDecode</b>(aesInput, Encoding.UTF8); &#47;&#47; <b>URL 디코딩</b>  &lt;-- decode, on encrypt
     * }
     * </pre>
     *
     * <p>Running {@code UrlDecode} over raw standard Base64 converts every {@code '+'} into a
     * <b>space</b> (and would consume any {@code %XX} sequence). That is precisely — and only — the
     * corruption the {@code ' '} → {@code '+'} substitution above reverses. The vendor's normative
     * text disagrees with its own sample: §4's {@code EV} memo specifies
     * {@code DateTime + JSONObject Data -> 암호화 -> BASE64 -> URL Encoding}.
     *
     * <p>The sibling function is inconsistent with it — {@code EncryptHmacSha256} (p.39) calls
     * {@code HttpUtility.UrlEncode(shaInput, Encoding.UTF8)}, the correct direction — so in the
     * vendor's reference implementation <b>{@code EV} and {@code VV} are encoded in opposite
     * directions</b>. That asymmetry is why this repair is applied to both fields rather than to
     * {@code EV} alone, matching legacy {@code VerifyMac:540-545}.
     *
     * <p>(Two further defects in the same sample, for context — they are documentation problems, not
     * things to reproduce: {@code VerifyMac} returns {@code false} when the HMACs are <b>equal</b>
     * (p.41), and the UTF-8 encoding object is named {@code euckrEncoding} (p.36).)
     *
     * <p><b>Consequence: keep this method.</b> It is not speculative hardening — there is a documented
     * mechanism by which a conforming counterparty emits space-corrupted Base64. It also makes the
     * {@code TODO(B5)} 2-pass hypothesis more credible (a counterparty whose encode-side URL handling
     * is this unreliable is exactly the setting for legacy's {@code SecurityUtil:278-279} comment,
     * 데이터 보호화시 URLEncoding데이터가 그대로 복호화가 성공적으로 되는 경우가 있어서 — "there are cases
     * where URL-encoded data decrypts successfully as-is") — but it does <b>not</b> settle it. Do not
     * add the second pass on the strength of this note.
     *
     * <p><b>Ordering.</b> Legacy repairs immediately before <i>its</i> Base64 decode, and its first
     * pass does not URL-decode at all. The equivalent position in this single-pass pipeline is
     * likewise immediately before {@link Base64#getDecoder()} — i.e. <b>after</b> {@link #urlDecode},
     * not before it. Repairing first would be wrong here: our pipeline always URL-decodes, so a
     * literal {@code '+'} repaired early would then be turned back into a space by the decode step.
     *
     * <p><b>Safe for well-formed input.</b> Standard Base64 output contains none of {@code ' '},
     * {@code '_'} or {@code '-'}, so on a correctly-encoded payload — including everything this
     * service itself produces — this method is a no-op.
     */
    private String repairBase64Alphabet(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value
                .replace(' ', '+')
                .replace('_', '/')
                .replace('-', '+');
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
