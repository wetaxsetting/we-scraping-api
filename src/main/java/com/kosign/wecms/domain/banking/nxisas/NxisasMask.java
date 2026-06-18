package com.kosign.wecms.domain.banking.nxisas;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/**
 * Masking helpers for the NXISAS debug-logging layer. Nothing sensitive
 * ever reaches a log line in clear text — account numbers, session tokens,
 * and the secret-bearing gateway request/response JSON are all redacted
 * here before they are emitted by {@link NxisasApiLog} /
 * {@link NxisasGatewayClient}.
 *
 * <p>Mirrors the masking the frontend applies in
 * {@code lib/nxisas/nxisas-debug.ts} so browser and server logs read the
 * same way.
 */
public final class NxisasMask {

    private NxisasMask() { }

    private static final String REDACTED = "***REDACTED***";

    /**
     * Field names whose values are secrets and must be fully redacted.
     * Matched case-insensitively after stripping non-alphanumerics, so
     * {@code Auth_key}, {@code LoginPWD}, {@code loginPwd1}, {@code res}
     * (the OTP/CAPTCHA/QNA answer field), etc. all hit.
     */
    private static final Set<String> REDACT_KEYS = Set.of(
            "authkey", "loginpwd", "loginpwd1", "password", "pwd", "passwd",
            "res", "answer", "otp", "captcha", "captchaanswer",
            "certpassword", "certpwd", "privatekey", "privkey", "secret",
            "hiddenvalue", "corporateid");

    /** Field names whose values are partially masked (first 3 + last 3). */
    private static final Set<String> TOKEN_KEYS = Set.of(
            "sassessionid", "commontoken");

    /** Field names whose values are account / card numbers — masked to the
     *  last 4 digits (e.g. {@code acctNum}, {@code cardNum}, {@code cardNo}). */
    private static final Set<String> ACCT_KEYS = Set.of(
            "acctnum", "acctno", "accountno", "accountnum",
            "cardnum", "cardno", "cardnumber");

    /** Account number → {@code ***1234} (last 4 only). */
    public static String acct(String acctNo) {
        if (acctNo == null || acctNo.isBlank()) return "";
        String t = acctNo.trim();
        if (t.length() <= 4) return "****";
        return "***" + t.substring(t.length() - 4);
    }

    /** Card number → {@code ***1234} (last 4 only). Same rule as
     *  {@link #acct}; named for call-site clarity. */
    public static String card(String cardNo) {
        return acct(cardNo);
    }

    /** Token (SAS_SessionID, Common token, …) → {@code abc***xyz}. */
    public static String token(String value) {
        if (value == null || value.isBlank()) return "";
        String t = value.trim();
        if (t.length() <= 6) return "***";
        return t.substring(0, 3) + "***" + t.substring(t.length() - 3);
    }

    /** True/false flag for "is a token present" without leaking it. */
    public static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Deep-mask an already-parsed gateway envelope tree: redacts secret
     * fields and partially masks session tokens, returning the mutated
     * tree's string form. Used for the DEBUG-level raw request/response
     * dump in {@link NxisasGatewayClient} so the verbose body can be
     * inspected without exposing the password / OTP / SAS token.
     */
    public static String json(JsonNode root) {
        if (root == null) return "null";
        maskInPlace(root);
        return root.toString();
    }

    private static void maskInPlace(JsonNode node) {
        if (node == null) return;
        if (node.isArray()) {
            node.forEach(NxisasMask::maskInPlace);
            return;
        }
        if (!node.isObject()) return;
        ObjectNode obj = (ObjectNode) node;
        // Snapshot names first — we replace values of existing keys, never
        // add/remove, but iterating the live field set while putting is
        // brittle, so copy the names up front.
        java.util.List<String> names = new java.util.ArrayList<>();
        obj.fieldNames().forEachRemaining(names::add);
        for (String name : names) {
            String norm = name.toLowerCase().replaceAll("[^a-z0-9]", "");
            JsonNode child = obj.get(name);
            if (REDACT_KEYS.contains(norm)) {
                obj.put(name, REDACTED);
            } else if (TOKEN_KEYS.contains(norm) && child != null && child.isTextual()) {
                obj.put(name, token(child.asText()));
            } else if (ACCT_KEYS.contains(norm) && child != null && child.isTextual()) {
                obj.put(name, acct(child.asText()));
            } else {
                maskInPlace(child);
            }
        }
    }
}
