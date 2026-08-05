package com.kosign.wecms.domain.payment.dispatch.van.crypto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <h2>C-15 — one pass, plus legacy's plaintext validation (RESOLVED 2026-08-03)</h2>
 *
 * <p>Legacy attempts decryption <b>twice</b> ({@code SecurityUtil.DecryptAesBase64:263-319},
 * {@code VerifyMac:534-568}): first without URL-decoding, then — if the first attempt threw
 * <i>or</i> produced a plaintext that failed its own sanity check ({@code :278-293}) — again
 * <b>with</b> URL-decoding. We keep a <b>single</b> pass and have ported <b>the validation</b>
 * ({@link #assertPlaintextWellFormed}). The split is deliberate, and the reason is an ordering
 * difference, not a judgement call about risk appetite.
 *
 * <h3>Why the ladder is not ported: repair-before-decode vs repair-after-decode</h3>
 *
 * <p>Legacy repairs the Base64 alphabet <b>before</b> its decode ({@code :269-271}, then
 * {@code :411}); we repair <b>after</b> ({@link #decryptEv}, rationale on
 * {@link #repairBase64Alphabet}). Trace a plain, <b>not</b>-URL-encoded standard-Base64
 * {@code EV} containing a {@code '+'}:
 * <ul>
 *   <li><b>legacy pass 1</b> — repair (no spaces, no-op) → no URL-decode → commons-codec →
 *       <b>correct bytes</b>;</li>
 *   <li><b>legacy pass 2</b> — repair (no-op) → {@code URLDecoder} turns {@code '+'} into a
 *       <b>space</b> → commons-codec <b>silently discards</b> it (its contract: characters outside
 *       the Base64 alphabet are discarded) → <b>wrong bytes, no error</b>;</li>
 *   <li><b>ours</b> — {@link #urlDecode} makes it a space → {@link #repairBase64Alphabet} makes it
 *       a {@code '+'} again → strict decode → <b>correct bytes</b>.</li>
 * </ul>
 *
 * <p>So legacy's pass 2 is <i>broken</i> for the very input class pass 1 exists to serve.
 * <b>Legacy needs two passes because each of its passes handles only one input class; ours handles
 * both.</b> Porting the ladder would mean reproducing legacy's ordering bug in order to reproduce
 * its workaround. Verified across six input classes — all six decode correctly here:
 * <ol>
 *   <li>URL-encoded ({@code %2B}) — the normal wire form;</li>
 *   <li>plain standard Base64 (literal {@code '+'}, {@code '/'}, {@code '='});</li>
 *   <li>base64url ({@code '-'}, {@code '_'}), URL-encoded;</li>
 *   <li>base64url, not URL-encoded;</li>
 *   <li>a {@code '+'} that a proxy form-decoded to a space;</li>
 *   <li>a {@code %2B} double-form-decoded to a space — <i>legacy pass 2 gets this wrong; only its
 *       pass 1 rescues it</i>.</li>
 * </ol>
 * The one class neither we nor legacy handle is the <b>doubly</b> URL-encoded {@code _JSON_}
 * transport variant ({@code %252B}); we use the flat form ({@code RestClientEfsApiClient.toForm}),
 * per the guide §4 / closure-report §5.3.
 *
 * <h3>Why the validation IS ported</h3>
 *
 * <p>It is the half of legacy's mechanism whose value is independent of the encoding. Note the
 * dependency runs one way only: porting the <i>pass count</i> would have required the validation
 * (without it a wrong-but-unpaddable plaintext looks like success and the second pass never fires),
 * but the validation stands alone perfectly well. See {@link #assertPlaintextWellFormed} for what it
 * checks, the deliberate divergence it introduces, and its SIT watch item.
 *
 * <h3>Do NOT reproduce legacy's nesting</h3>
 *
 * <p>{@code VerifyMac:534-568} runs two passes, each calling {@code VerifyMac2:579-591}, which calls
 * {@code DecryptAesBase64} — itself 2-pass. Up to <b>four</b> decrypt attempts per verify. This is
 * <b>accreted patching, not a designed ladder</b>, and two tells show it:
 * <ul>
 *   <li>{@code VerifyMac2:581} passes its own {@code urldecode} straight into
 *       {@code DecryptAesBase64}, so the outer pass 1 ({@code false}) <i>enables</i> the inner
 *       2-pass while the outer pass 2 ({@code true}) <i>disables</i> the inner pass 1 via the
 *       {@code else} at {@code :294} — the nesting depth is <b>asymmetric between the outer
 *       passes</b>. Nobody designs that.</li>
 *   <li>{@code VerifyMac2:582} also feeds {@code urldecode} to {@code getHmacSha256} as its
 *       {@code urlencode} flag, so the outer pass toggles whether the <i>recomputed</i> MAC is
 *       URL-encoded before comparison — except {@code :585-586} then {@code URLDecoder.decode}
 *       <b>both</b> sides, largely neutralising the toggle it just introduced.</li>
 * </ul>
 * {@link #verifyVv} is deliberately flatter: one decrypt, one HMAC, one constant-time compare,
 * {@code false} on any failure.
 *
 * <h3>B5 — what it can and cannot still decide here</h3>
 *
 * <p>The handover's {@code 08_기능추출_이관가이드.md} §8.4 pitfall ③ warns:
 * <blockquote>
 * 무결성 VV 2-pass: URL디코딩 유/무 두 번 시도. <b>한쪽만 구현하면 간헐적 검증실패.</b><br>
 * <i>"Integrity VV 2-pass: attempt twice, with/without URL-decoding. If you implement only one
 * side you get <b>intermittent</b> verification failures."</i>
 * </blockquote>
 * Read against the trace above, that is a warning about <b>legacy's two-input-class design</b> — in
 * which implementing one pass genuinely does drop one class — <b>not about ours</b>, where a single
 * pass covers both. <b>There is no unresolved intermittent-failure risk sitting behind this
 * section.</b>
 *
 * <p>Accordingly the pass count is no longer gated on B5: the only thing a captured sample could
 * reveal about it is whether a counterparty sends {@code EV} <i>not</i> URL-encoded, and cases 2 / 4
 * / 5 / 6 above already cover that. B5's real dependencies are elsewhere and <b>unaffected</b> —
 * the {@code BT00}/{@code BT01}/{@code C999} reception result-code set and D-4. Do not re-attach the
 * pass count to them.
 *
 * <p>The unconditional Base64-alphabet repair was ported earlier and independently — see
 * {@link #repairBase64Alphabet}.
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
     * Private, deliberately un-injected mapper used ONLY to structurally validate decrypted
     * plaintext (C-15 — see {@link #assertPlaintextWellFormed}). It is not the application
     * {@code ObjectMapper}: this service must not inherit the global {@code SNAKE_CASE} strategy or
     * any future serialization config, because it parses to a tree and binds nothing. Callers do
     * their own binding with the injected mapper.
     */
    private static final ObjectMapper PLAINTEXT_VALIDATOR = new ObjectMapper();

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
     *
     * <p>The plaintext is validated before it is returned — see
     * {@link #assertPlaintextWellFormed}. A decrypt that produces garbage fails here rather
     * than reaching the caller's Jackson bind.
     *
     * @throws IllegalStateException if the AES step fails, or the plaintext does not have the
     *                               shape {@code 14 digits + JSON object}
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
        String json = withPrefix.substring(RQ_DTIME_PREFIX_LEN);
        assertPlaintextWellFormed(withPrefix.substring(0, RQ_DTIME_PREFIX_LEN), json);
        return json;
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

    /**
     * Legacy's post-decrypt plaintext validation, ported from {@code SecurityUtil:278-293}
     * (audit <b>C-15</b>, approved 2026-08-03).
     *
     * <p>Legacy's own comment states the mechanism —
     * 데이터 보호화시 URLEncoding데이터가 그대로 복호화가 성공적으로 되는 경우가 있어서 복호화후 데이터
     * 검증 로직을 추가함 ("because there are cases where URL-encoded data decrypts 'successfully'
     * as-is, a post-decrypt validation was added"). A non-throwing AES decrypt is <b>not</b>
     * evidence that the right bytes went in: CBC + PKCS5 only rejects a wrong plaintext when the
     * final block's padding happens to be invalid. So legacy checks the plaintext's shape, and so
     * do we:
     * <ol>
     *   <li>the 14-char prefix parses as a {@code Long} ({@code Long.parseLong}, exactly as legacy —
     *       14 digits fits {@code long});</li>
     *   <li>the remainder parses as a JSON <b>object</b>. Legacy used
     *       {@code JSONObject.fromObject}, which rejects arrays and scalars; the {@code isObject()}
     *       check below is the faithful equivalent. It also closes two holes a brace check would
     *       leave: a truncated tail, and Jackson's {@code readTree("")} returning
     *       {@code MissingNode} rather than throwing.</li>
     * </ol>
     * Every {@code EV} payload on every leg is a JSON object (§4 memo: {@code DateTime +
     * JSONObject Data}; all four call sites bind to a record), so requiring an object is correct
     * and not over-tight.
     *
     * <h2>Only legacy's {@code isPaddingNum == true} branch is ported</h2>
     *
     * <p>Legacy's {@code else} branch ({@code :291-293}) is defective and is deliberately NOT
     * reproduced. When the prefix is not numeric it validates the <b>whole</b> string as JSON and,
     * if that passes, certifies it intact — after which every caller does {@code .substring(14)}
     * ({@code NAPAS_REAL_TIME_BATCH:373}, {@code WB_00000100:157}, {@code API1_act.jsp:106},
     * {@code API2_act.jsp:113}) and chops 14 characters off the JSON document legacy just
     * validated. That branch cannot be parity-preserving because legacy itself is broken there.
     *
     * <h2>Deliberate divergence — we reject where legacy returned garbage</h2>
     *
     * <p><b>This is a divergence, not parity.</b> On a response whose prefix is not 14 digits,
     * legacy tolerates it at validation (the {@code else} branch above) and then corrupts it at
     * the caller. We throw. The refactor's outcome is strictly better — a reported failure instead
     * of a silently mangled payload — but it is a behavioural difference and is recorded as one.
     *
     * <p><b>Watch item for the first SIT run.</b> If the gateway ever omits {@code RQ_DTIME} from a
     * <i>response</i> {@code EV}, we begin rejecting envelopes the previous build accepted (and
     * garbled). That surfaces as {@code G003} on the callback legs and as a gateway error on the
     * outbound legs. It is the better outcome, but it should be <b>recognised</b> on the first SIT
     * run rather than debugged later as a mystery {@code G003} — if a real payload turns out to
     * carry no datetime prefix, the fix is a per-leg prefix-optional policy decision, not the
     * removal of this validation.
     *
     * <h2>What this does and does not catch on our side</h2>
     *
     * <p>Narrower than legacy's exposure, because our decoder is stricter. Legacy uses
     * commons-codec {@code Base64.decodeBase64}, whose contract is to <b>discard</b> characters
     * outside the Base64 alphabet — so a {@code %}-bearing URL-encoded payload decodes to a
     * shortened byte string and can reach AES at all. We use {@code java.util.Base64.getDecoder()},
     * which <b>throws</b> on those characters, so the specific silent success legacy's comment
     * describes cannot happen here; it fails at the decoder. What remains for this validation is
     * the class the decoder cannot see: input that is alphabet-clean and correctly padded but
     * decrypts to garbage — <b>a wrong key, a truncated ciphertext, or a mis-split envelope
     * field</b>. That class is real and encoding-independent, which is why the validation was
     * portable without waiting on B5.
     *
     * <h2>{@code verifyVv} needs no equivalent</h2>
     *
     * <p>It inherits this check through {@link #decryptEv}: {@link #verifyVv} already catches
     * {@code RuntimeException} → {@code false}, which both callback services map to {@code G003}
     * ({@code BulkExecutionResultService:158-160}, {@code BulkReceptionResultService:287-289}).
     * <b>Do not</b> add a second copy there.
     *
     * @param rqDtimePrefix the leading 14 characters of the decrypted plaintext
     * @param json          everything after those 14 characters
     */
    private void assertPlaintextWellFormed(String rqDtimePrefix, String json) {
        try {
            Long.parseLong(rqDtimePrefix);
        } catch (NumberFormatException e) {
            // Never include the plaintext itself — it is business payload, and this message reaches logs.
            throw new IllegalStateException(
                    "decrypted payload does not begin with a 14-digit RQ_DTIME prefix — "
                            + "treating the decrypt as failed (C-15)", e);
        }
        final JsonNode parsed;
        try {
            parsed = PLAINTEXT_VALIDATOR.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "decrypted payload's post-prefix remainder is not parseable JSON — "
                            + "treating the decrypt as failed (C-15)", e);
        }
        if (parsed == null || !parsed.isObject()) {
            throw new IllegalStateException(
                    "decrypted payload's post-prefix remainder is not a JSON object — "
                            + "treating the decrypt as failed (C-15)");
        }
    }

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
     * before either decrypt attempt, regardless of the {@code urldecode} flag. That independence is
     * why it was portable ahead of the C-15 pass-count decision — which has since been made (single
     * pass, see the class Javadoc §"C-15").
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
     * mechanism by which a conforming counterparty emits space-corrupted Base64.
     *
     * <p><b>Superseded note (2026-08-03).</b> This section previously argued that the vendor bug made
     * the "2-pass hypothesis more credible" without settling it. C-15 has since settled it, and in the
     * opposite direction: a second pass is <b>not</b> needed, because repairing <i>after</i> the
     * URL-decode covers both of the input classes legacy splits across two passes (see §"Ordering"
     * below and the class Javadoc). What the vendor bug explains is the <i>repair</i>, not the pass
     * count. Legacy's {@code SecurityUtil:278-279} comment
     * (데이터 보호화시 URLEncoding데이터가 그대로 복호화가 성공적으로 되는 경우가 있어서 — "there are cases
     * where URL-encoded data decrypts successfully as-is") is answered instead by
     * {@link #assertPlaintextWellFormed}, which is the part of legacy's mechanism that was ported.
     *
     * <p><b>Ordering — and it is load-bearing, not stylistic.</b> Legacy repairs immediately before
     * <i>its</i> Base64 decode, and its first pass does not URL-decode at all. The equivalent
     * position in this single-pass pipeline is likewise immediately before
     * {@link Base64#getDecoder()} — i.e. <b>after</b> {@link #urlDecode}, not before it. Repairing
     * first would be wrong here: our pipeline always URL-decodes, so a literal {@code '+'} repaired
     * early would then be turned back into a space by the decode step.
     *
     * <p><b>This ordering is why the C-15 2-pass ladder is not ported</b> (see the class Javadoc
     * §"C-15"). Repair-<i>after</i>-decode makes one pass cover both of the input classes legacy
     * splits across two, because URL-decode's {@code '+'}→space mangling is undone immediately
     * afterwards instead of being fed into the decoder. Legacy's repair-<i>before</i>-decode is what
     * forces it to keep a no-URL-decode pass: with its ordering, pass 2 turns a literal {@code '+'}
     * into a space that commons-codec then <b>silently discards</b>, so plain (not-URL-encoded)
     * Base64 decodes to the wrong bytes with no error — only its pass 1 gets that case right. Six
     * input classes were traced against this pipeline; all six decode correctly, including the two
     * that legacy's pass 2 corrupts. The enumeration lives in the class Javadoc.
     *
     * <p><b>Do not "simplify" by moving this call before {@link #urlDecode} to match legacy's line
     * order.</b> That would reintroduce exactly the defect that makes legacy need two passes, and the
     * repair tests ({@code EfsCryptoBase64RepairTest}) are the guard: cases 5 and 6 fail if it moves.
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
