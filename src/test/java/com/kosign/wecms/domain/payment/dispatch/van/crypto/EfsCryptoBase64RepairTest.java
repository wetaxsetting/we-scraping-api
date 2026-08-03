package com.kosign.wecms.domain.payment.dispatch.van.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * D-8 — the unconditional Base64-alphabet repair ported from legacy {@code SecurityUtil:269-271}
 * ({@code EV}) and {@code :540-545} ({@code EV} + {@code VV}).
 *
 * <p>Legacy applies {@code ' '→'+'}, {@code '_'→'/'}, {@code '-'→'+'} before every decrypt attempt.
 * Each repairs a real corruption of a standard-Base64 payload: the space is what
 * {@code application/x-www-form-urlencoded} decoding turns a {@code '+'} into, and {@code '_'}/{@code '-'}
 * are the base64url alphabet. Without the repair those payloads fail to decode — the
 * "intermittent verify failure" the handover warns about ({@code 08 §8.4} pitfall ③).
 *
 * <p>This is <b>separate from the 2-pass URL-decode</b> (still {@code TODO(B5)}): legacy runs the repair
 * once, unconditionally, independent of the pass count — so it was portable now.
 *
 * <p><b>What these tests do NOT cover:</b> whether the gateway actually emits such payloads. That is
 * B5 (byte-verify against a captured sample). These assert only that if one arrives, we decode it
 * the way legacy did rather than throwing.
 */
class EfsCryptoBase64RepairTest {

    /** Published TEST key (PDF §6.1/§6.2) — 32 bytes = AES-256. NOT a real key. */
    private static final String TEST_KEY = "webcashvietnam0002TEST$#%$&*$&^!";
    private static final String RQ_DTIME = "20240422103822";
    private static final String JSON =
            "{\"bank_cd\":\"970424\",\"prtn_id\":\"ORG000001\",\"result_cd\":\"BT00\"}";

    private final EfsCryptoService crypto = new EfsCryptoService();

    /** The raw standard-Base64 EV, as it exists before the service URL-encodes it for the wire. */
    private String rawBase64Ev() {
        return URLDecoder.decode(crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME), StandardCharsets.UTF_8);
    }

    private String rawBase64Vv() {
        return URLDecoder.decode(crypto.buildVv(JSON, TEST_KEY), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("EV: a '+' already form-decoded to a space still decrypts")
    void ev_withPlusDecodedToSpace_stillDecrypts() {
        String ev = rawBase64Ev();
        // Asserted, not assumed: a skip here would silently stop covering the substitution.
        assertThat(ev).as("EV must contain a '+' for this scenario to mean anything").contains("+");

        // A proxy/container that already form-decoded the body hands us spaces where '+' stood.
        String corrupted = ev.replace('+', ' ');

        assertThat(crypto.decryptEv(corrupted, TEST_KEY)).isEqualTo(JSON);
    }

    @Test
    @DisplayName("EV: the base64url alphabet ('-' and '_') still decrypts")
    void ev_inBase64UrlAlphabet_stillDecrypts() {
        String ev = rawBase64Ev();
        assertThat(ev).as("EV must contain '+' or '/' for this scenario to mean anything")
                .matches(s -> s.contains("+") || s.contains("/"));

        // RFC 4648 §5 base64url: '+' -> '-', '/' -> '_'.
        String base64url = ev.replace('+', '-').replace('/', '_');

        assertThat(crypto.decryptEv(base64url, TEST_KEY)).isEqualTo(JSON);
    }

    @Test
    @DisplayName("VV: the repair applies to the MAC too, not just the EV (legacy VerifyMac:543-545)")
    void vv_isRepairedAsWell() {
        // Deterministic rather than assumption-gated: not every HMAC lands on a '+' or '/', and a
        // skipped test is a hole, not a pass. Walk payload variants until one produces a VV that
        // actually exercises the substitution, then assert on that one.
        String payload = null;
        String vv = null;
        for (int i = 0; i < 200; i++) {
            String candidate = "{\"bank_cd\":\"970424\",\"file_nm\":\"S_GV0000000" + i + "_WRB_001_20260602\"}";
            String candidateVv = URLDecoder.decode(crypto.buildVv(candidate, TEST_KEY), StandardCharsets.UTF_8);
            if (candidateVv.contains("+") || candidateVv.contains("/")) {
                payload = candidate;
                vv = candidateVv;
                break;
            }
        }
        assertThat(vv)
                .as("no payload in 200 tries produced a VV containing '+' or '/' — statistically "
                        + "implausible for base64 HMAC output; the generator is probably broken")
                .isNotNull();

        String ev = crypto.encryptEv(payload, TEST_KEY, RQ_DTIME);
        String base64urlVv = vv.replace('+', '-').replace('/', '_');
        assertThat(base64urlVv).isNotEqualTo(vv); // the substitution really happened

        assertThat(crypto.verifyVv(ev, base64urlVv, TEST_KEY)).isTrue();
    }

    @Test
    @DisplayName("The repair is a no-op for well-formed input — the normal round-trip is unchanged")
    void wellFormedEnvelopeIsUnaffected() {
        // Standard Base64 output contains none of ' ', '_' or '-', so nothing this service
        // produces can be altered by the repair. Guards against the repair corrupting good input.
        String ev = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        String vv = crypto.buildVv(JSON, TEST_KEY);

        assertThat(rawBase64Ev()).doesNotContain(" ").doesNotContain("_").doesNotContain("-");
        assertThat(rawBase64Vv()).doesNotContain(" ").doesNotContain("_").doesNotContain("-");
        assertThat(crypto.decryptEv(ev, TEST_KEY)).isEqualTo(JSON);
        assertThat(crypto.verifyVv(ev, vv, TEST_KEY)).isTrue();
    }

    @Test
    @DisplayName("A genuinely tampered VV is still rejected — the repair does not weaken the MAC check")
    void tamperedVvStillRejected() {
        String ev = crypto.encryptEv(JSON, TEST_KEY, RQ_DTIME);
        String vv = crypto.buildVv("{\"bank_cd\":\"999999\"}", TEST_KEY);

        assertThat(crypto.verifyVv(ev, vv, TEST_KEY)).isFalse();
    }
}
