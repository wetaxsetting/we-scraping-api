package com.kosign.wecms.common.nxisas;

import com.coocon.securty.util.ISASSeedCBC;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Server-side port of legacy {@code nxisas_decrypt_act.jsp} /
 * {@code nxisas_encrypt_act.jsp}, reusing the licensed Coocon cipher
 * {@code com.coocon.securty.util.ISASSeedCBC} (bundled as
 * {@code libs/isas1.0.jar}) so the refactor decrypts NXiSAS engine
 * {@code Output.Result} payloads byte-for-byte like legacy.
 *
 * <p>Walks the engine result array ({@code NXISAS_DATA}); for each job's
 * {@code Output.Result} string it strips a leading {@code ENC=} (when
 * present), SEED-CBC-decrypts with the session key/IV derived from
 * {@code NXISAS_UID}/{@code NXISAS_ACTION}, and replaces the string with
 * the parsed JSON object — matching {@code nxisas_decrypt_act.jsp:56-101}.
 * A BALANCE job then exposes {@code Output.Result.balance[]}; a TRANSFER
 * job exposes {@code Output.Result.transfer[]}.
 *
 * <p>Tolerant like legacy ({@code :84-86}): if decryption returns null
 * the original ciphertext is kept; if the decrypted text is not JSON it
 * is left as a string node rather than crashing the whole array.
 *
 * <p>This service performs the cipher unconditionally. Feature-flag
 * gating is the caller's concern:
 * <ul>
 *   <li>OAT ({@code OatNxisasService}) invokes it only when
 *       {@code wecms.oat.seed-decrypt.enabled} is on (VND payloads mostly
 *       arrive plaintext, so decryption is opt-in there).</li>
 *   <li>e-Invoice ({@code EInvoiceScrapeDecryptService}) invokes it
 *       unconditionally — the GDT engine always returns SEED-CBC
 *       ciphertext, so the two-phase scrape cannot proceed without it.</li>
 * </ul>
 *
 * <p>Promoted to {@code common/nxisas/} on 2026-07-14 when the e-Invoice
 * scrape became the second consumer (architecture "promote to common on
 * second consumer" rule).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedCbcDecryptService {

    /** Coocon ciphertext prefix the engine sometimes adds (legacy :73). */
    private static final String ENC_PREFIX = "ENC=";

    /** Legacy :78 — use the 3-arg keyed cipher only when both session
     *  values are ≥ 30 chars (the bundle generates 40-char Uid/Action). */
    private static final int MIN_KEY_LEN = 30;

    private final ObjectMapper objectMapper;

    /**
     * Decrypt every {@code Output.Result} in the engine result array.
     * Mirrors {@code nxisas_decrypt_act.jsp}.
     *
     * @param nxisasData the {@code NXISAS_DATA} JSON array string the
     *                   bundle posted (one element per job).
     * @param uid        the session {@code NXISAS_UID} (SEED key material).
     * @param action     the session {@code NXISAS_ACTION} (SEED IV material).
     * @return the array re-serialised with each {@code Output.Result}
     *         replaced by its decrypted JSON; the input unchanged on any
     *         structural problem.
     */
    public String decryptNxisasData(String nxisasData, String uid, String action) {
        if (nxisasData == null || nxisasData.isBlank()) {
            return nxisasData;
        }
        try {
            JsonNode root = objectMapper.readTree(nxisasData);
            walkAndDecrypt(root, uid, action);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            log.warn("oat seed-cbc decrypt failed; returning input unchanged", ex);
            return nxisasData;
        }
    }

    /**
     * Recursively find every object that carries an {@code Output} object
     * with a textual {@code Result} and decrypt it in place — mirroring
     * legacy {@code nxisas_decrypt_act.jsp:56-101}.
     *
     * <p>The engine result is NOT flat: {@code runScraping} resolves with
     * an array of org-objs, each holding a {@code result[]} (and/or
     * {@code job[]}) array of job records, each job record carrying the
     * {@code Output.Result} ciphertext (legacy {@code trns_0001_007.ts:1635-1656};
     * the refactor's {@code findBalanceOutput} navigates the same shape).
     * A flat-array-only walk silently misses every nested {@code Output},
     * leaving the payload encrypted — so we recurse through arrays and
     * object fields until each {@code Output} is found, at any depth.
     */
    private void walkAndDecrypt(JsonNode node, String uid, String action) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walkAndDecrypt(child, uid, action);
            }
            return;
        }
        if (!(node instanceof ObjectNode obj)) {
            return;
        }
        if (obj.get("Output") instanceof ObjectNode output) {
            JsonNode resultNode = output.get("Result");
            if (resultNode != null && resultNode.isTextual() && !resultNode.asText().isEmpty()) {
                String encResult = resultNode.asText();
                if (encResult.startsWith(ENC_PREFIX)) {
                    encResult = encResult.substring(ENC_PREFIX.length());
                }
                String decResult = decryptOne(encResult, uid, action);
                if (decResult == null) {
                    decResult = encResult;                 // tolerant (legacy :84-86)
                }
                try {
                    output.set("Result", objectMapper.readTree(decResult));   // legacy :90
                } catch (Exception parseEx) {
                    // Decrypted text is not JSON (e.g. cipher mismatch left
                    // it as ciphertext). Keep it as a string rather than
                    // crash the whole response.
                    output.put("Result", decResult);
                }
            }
        }
        // Recurse into nested holders (result[] / job[] / any nested object)
        // so deeply-nested Outputs are reached. Re-visiting the just-decrypted
        // Output is harmless — it no longer has a child named "Output".
        for (JsonNode child : obj) {
            walkAndDecrypt(child, uid, action);
        }
    }

    /**
     * Inverse of {@link #decryptNxisasData} — encrypt each
     * {@code Output.Result} (used by the 2nd-factor OTP/QnA re-send path).
     * Mirrors {@code nxisas_encrypt_act.jsp}.
     */
    public String encryptNxisasData(String nxisasData, String uid, String action) {
        if (nxisasData == null || nxisasData.isBlank()) {
            return nxisasData;
        }
        try {
            JsonNode root = objectMapper.readTree(nxisasData);
            walkAndEncrypt(root, uid, action);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            log.warn("oat seed-cbc encrypt failed; returning input unchanged", ex);
            return nxisasData;
        }
    }

    /** Recursive inverse of {@link #walkAndDecrypt} — encrypt every nested
     *  {@code Output.Result}. Mirrors {@code nxisas_encrypt_act.jsp}. */
    private void walkAndEncrypt(JsonNode node, String uid, String action) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                walkAndEncrypt(child, uid, action);
            }
            return;
        }
        if (!(node instanceof ObjectNode obj)) {
            return;
        }
        if (obj.get("Output") instanceof ObjectNode output) {
            JsonNode resultNode = output.get("Result");
            if (resultNode != null) {
                // Legacy encrypts the string form of Result; a post-decrypt
                // object is re-serialised first.
                String plain = resultNode.isTextual() ? resultNode.asText() : resultNode.toString();
                String enc = encryptOne(plain, uid, action);
                if (enc == null) {
                    enc = plain;
                }
                output.put("Result", enc);
            }
        }
        for (JsonNode child : obj) {
            walkAndEncrypt(child, uid, action);
        }
    }

    /** Legacy :78-82 — keyed cipher when Uid/Action are long enough,
     *  else the fixed-key fallback. Returns null on any cipher failure so
     *  the caller keeps the original ciphertext (legacy tolerance).
     *
     *  <p>Catches {@link Throwable} deliberately: the bundled Coocon
     *  {@code iSASBase64} decoder throws {@link AssertionError} (not an
     *  {@link Exception}) on malformed base64, so a non-cipher payload
     *  must not crash the whole result array. */
    private String decryptOne(String enc, String uid, String action) {
        try {
            if (hasSessionKey(uid, action)) {
                return ISASSeedCBC.decrypt(enc, uid, action);
            }
            return ISASSeedCBC.decrypt(enc);
        } catch (Throwable ex) {
            log.warn("oat seed-cbc per-job decrypt failed; keeping ciphertext", ex);
            return null;
        }
    }

    private String encryptOne(String plain, String uid, String action) {
        try {
            if (hasSessionKey(uid, action)) {
                return ISASSeedCBC.encrypt(plain, uid, action);
            }
            return ISASSeedCBC.encrypt(plain);
        } catch (Throwable ex) {
            log.warn("oat seed-cbc per-job encrypt failed; keeping plaintext", ex);
            return null;
        }
    }

    private static boolean hasSessionKey(String uid, String action) {
        return uid != null && action != null
                && uid.length() >= MIN_KEY_LEN && action.length() >= MIN_KEY_LEN;
    }
}
