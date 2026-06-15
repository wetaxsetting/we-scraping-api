package com.kosign.wecms.domain.payment.dispatch.file;

/**
 * Vietnamese tone (diacritic) stripping — verbatim port of legacy
 * {@code util.StrUtil.replaceTone}. The firm-banking bulk file (00301) is built
 * with sender/receiver names, account memos and the recipient address
 * tone-stripped to their base ASCII letters, because the bank's fixed-length
 * channel does not accept the composed Vietnamese code points.
 *
 * <p>The two mapping strings are copied byte-for-byte from the legacy class so the
 * generated file matches what the live JEX batch would have produced.
 */
public final class VietnameseText {

    private VietnameseText() {
    }

    private static final String TONED =
            "ÀÁÂÃÈÉÊÌÍÒÓÔÕÙÚÝàáâãèéêìíòóôõùúýĂăĐđĨĩŨũƠơƯưẠạẢảẤấẦầẨẩẪẫẬậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨứỪừỬửỮữỰựỲỳỴỵỶỷỸỹ";
    private static final String PLAIN =
            "AAAAEEEIIOOOOUUYaaaaeeeiioooouuyAaDdIiUuOoUuAaAaAaAaAaAaAaAaAaAaAaAaEeEeEeEeEeEeEeEeIiIiOoOoOoOoOoOoOoOoOoOoOoOoUuUuUuUuUuUuUuYyYyYyYy";

    /** Replace each toned Vietnamese character with its base ASCII letter. */
    public static String replaceTone(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            int idx = TONED.indexOf(c);
            out.append(idx != -1 ? PLAIN.charAt(idx) : c);
        }
        return out.toString();
    }
}
