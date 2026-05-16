package com.kosign.wecms.domain.banking.nxisas;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 dedup key generator for {@code ACCT_TRNS_HSTR}. Mirrors
 * legacy {@code fnsh_0001_001_c001_act.jsp:170}
 * ({@code generateBankTrnsFullId}) + {@code :204}
 * ({@code generateBankTrnsShortId}) bit-for-bit so a row inserted by
 * the refactor's sync orchestrator hashes to the same FULL_ID a row
 * inserted by the legacy JSP would — important for inter-op when the
 * two systems read the same DB.
 *
 * <p>The {@code cleanRemark} step uppercases + collapses whitespace
 * inside the remark before hashing — Coocon sometimes ships the
 * same logical text with different spacing/case, and we want those
 * to dedupe as one row (legacy parity at
 * {@code fnsh_0001_001_c001_act.jsp:147}).
 *
 * <p>Stateless static utility — no Spring bean. The single SHA-256
 * MessageDigest is allocated per call (cheap; algorithm impls in
 * recent JDKs are stateless wrappers).
 */
public final class BankTrnsIdGenerator {

    private BankTrnsIdGenerator() { /* static helpers only */ }

    /**
     * Build the 64-char lowercase hex SHA-256 of
     * {@code bankCd | trscDt | trscTm | amt | inoutDv | rmrk | rmrk2}.
     *
     * <p>Field semantics match the legacy method one-to-one:
     * <ul>
     *   <li>{@code trscTm} — empty string when null/blank (some banks
     *       return midnight rows with no time)</li>
     *   <li>{@code trscAmt} — {@code BigDecimal.toPlainString()}, or
     *       {@code "0"} when null (defensive — legacy rows always
     *       have an amount)</li>
     *   <li>{@code inoutDv} — the wire string ("1" = IN, "2" = OUT,
     *       legacy {@code InoutDv.getValue()})</li>
     *   <li>{@code rmrk} / {@code rmrk2} — uppercased, whitespace
     *       collapsed, trimmed</li>
     * </ul>
     *
     * <p>Never throws — on {@link NoSuchAlgorithmException} (impossible
     * on any conforming JDK) returns the empty string, mirroring
     * legacy fallback behaviour (caller writes empty FULL_ID, dedup
     * is effectively disabled for that row).
     */
    public static String fullId(String bankCd, String trscDt, String trscTm,
                                BigDecimal trscAmt, String inoutDv,
                                String rmrk, String rmrk2) {
        String dv  = inoutDv == null ? "" : inoutDv;
        String amt = trscAmt == null ? "0" : trscAmt.toPlainString();
        String tm  = (trscTm == null || trscTm.isEmpty()) ? "" : trscTm;
        String raw = bankCd + "|" + trscDt + "|" + tm + "|" + amt + "|"
                   + dv + "|" + cleanRemark(rmrk) + "|" + cleanRemark(rmrk2);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    /**
     * Human-readable cross-reference id: {@code trscDt + "_" + fullId[0..4]}
     * uppercased. Deterministic given {@code fullId} so re-scrape produces
     * the same SHORT_ID without a uniqueness retry loop (legacy parity).
     *
     * @return the SHORT_ID, or {@code trscDt} when {@code fullId} is null /
     *         too short to slice (legacy fallback).
     */
    public static String shortId(String trscDt, String fullId) {
        if (fullId == null || fullId.length() < 4) return trscDt;
        return trscDt + "_" + fullId.substring(0, 4).toUpperCase();
    }

    /**
     * Whitespace + case normalisation for remarks before hashing —
     * uppercases ASCII, trims, collapses any whitespace run into a
     * single space. Banks ship "PAYMENT  FROM  ABC" and "Payment from
     * abc" as separate strings for the same logical event; the hash
     * must collapse them. Legacy parity at
     * {@code fnsh_0001_001_c001_act.jsp:147}.
     */
    private static String cleanRemark(String rmrk) {
        if (rmrk == null) return "";
        String t = rmrk.trim();
        if (t.isEmpty()) return "";
        return t.toUpperCase().replaceAll("\\s+", " ");
    }
}
