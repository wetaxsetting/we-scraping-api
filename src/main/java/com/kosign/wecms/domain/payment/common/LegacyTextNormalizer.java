package com.kosign.wecms.domain.payment.common;

/**
 * Verbatim port of the legacy JEX upload-time text normalization so refactor
 * payment uploads persist byte-identical values to the live JEX system.
 *
 * <p>Migration-compatibility corrections from the audit
 * {@code wecms-api/docs/audits/special-char-validation-parity-audit-2026-06-10.md}:
 * <ul>
 *   <li><b>F2</b> — strip CR/LF from every Excel cell value. Mirrors legacy
 *       {@code wabooks.commons.ExcelUtils.getCell:144}
 *       ({@code return value.replaceAll("\\r|\\n", "")}). Tabs are NOT
 *       stripped — legacy preserves them.</li>
 *   <li><b>F1</b> — convert Vietnamese accented characters to ASCII before
 *       special-character validation and before persistence. Mirrors legacy
 *       {@code wabooks.commons.StringUtils.hasVietnameseCharacters} +
 *       {@code replaceTone}, applied at {@code PymnHelper:445-446 / 526-527 /
 *       788-789 / 979-980} and the Payroll / Overseas action JSPs
 *       ({@code pymn_0005_002_r001_act.jsp}, {@code pymn_0007_003_r001_act.jsp}).</li>
 * </ul>
 *
 * <p>This is a behavioral port — the character mapping table is copied
 * verbatim from legacy {@code StringUtils.replaceTone} (lines 73-74). Do not
 * substitute {@code java.text.Normalizer} or otherwise "modernize" it: the
 * stored value feeds bank-file generation and must match legacy exactly.
 */
public final class LegacyTextNormalizer {

    private LegacyTextNormalizer() {
    }

    /** Verbatim from {@code wabooks.commons.StringUtils.replaceTone} (line 73). */
    private static final String VN_WITH_TONE =
            "ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝàáâãèéêìíòóôõùúýĂăĐđĨĩŨũƠơƯưẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨứỪừỬửỮữỰựỲỳỴỵỶỷỸỹ";

    /** Verbatim from {@code wabooks.commons.StringUtils.replaceTone} (line 74). */
    private static final String VN_ASCII =
            "AAAAEEEIIOOOOUUYaaaaeeeiioooouuyAaDdIiUuOoUuAaAaAaAaAaAaAaAaAaAaAaAaEeEeEeEeEeEeEeEeIiIiOoOoOoOoOoOoOoOoOoOoOoOoUuUuUuUuUuUuUuYyYyYyYy";

    /**
     * F2 — remove CR/LF, preserving tabs. Mirrors legacy
     * {@code ExcelUtils.getCell:144}. Null passes through unchanged.
     */
    public static String stripCrLf(String value) {
        return value == null ? null : value.replaceAll("\\r|\\n", "");
    }

    /** Verbatim from {@code wabooks.commons.StringUtils.hasVietnameseCharacters} (lines 45-60). */
    public static boolean hasVietnameseCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (char ch : text.toCharArray()) {
            if ((ch >= 'À' && ch <= 'ÿ')
                    || (ch >= 'Ā' && ch <= 'ſ')
                    || (ch >= 'Ạ' && ch <= 'ỹ')) {
                return true;
            }
        }
        return false;
    }

    /** Verbatim from {@code wabooks.commons.StringUtils.replaceTone} (lines 68-88). */
    public static String replaceTone(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int index = VN_WITH_TONE.indexOf(c);
            if (index != -1) {
                result.append(VN_ASCII.charAt(index));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * F1 — legacy guarded application:
     * {@code if (hasVietnameseCharacters(v)) v = replaceTone(v)}.
     * Mirrors {@code PymnHelper:445-446 / 526-527 / 788-789 / 979-980} and the
     * Payroll / Overseas action JSPs. Null / non-Vietnamese values pass through
     * unchanged (the guard returns {@code false}), so callers' null-handling is
     * preserved.
     */
    public static String stripVietnameseTone(String value) {
        if (hasVietnameseCharacters(value)) {
            return replaceTone(value);
        }
        return value;
    }

    /**
     * Full upload-time normalization = {@link #stripCrLf} then
     * {@link #stripVietnameseTone}, in the same order the parsers apply on the
     * preview path (CR/LF stripped at cell read, Vietnamese accents stripped
     * per validated field). Used by the save-path / dispatch enforcement guard
     * so a direct API call persists the SAME normalized value the upload
     * preview would have produced. Idempotent. Null-safe.
     */
    public static String normalize(String value) {
        return stripVietnameseTone(stripCrLf(value));
    }
}
