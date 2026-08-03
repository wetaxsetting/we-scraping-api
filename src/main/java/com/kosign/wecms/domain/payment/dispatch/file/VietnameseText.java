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
 *
 * <h2>The missing {@code hasVietnameseCharacters} gate is deliberate (D-L)</h2>
 *
 * <p>Legacy never calls {@code replaceTone} directly — every one of its ten call sites is wrapped in
 * {@code if (StrUtil.hasVietnameseCharacters(v))}, at
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1:1075-1077} (the {@code TB_FIRM_FILE_DATA} row) and
 * {@code :647-650} (the wire bytes). <b>This class has no such gate, and that is not an oversight.</b>
 *
 * <p>The gate admits three ranges — {@code U+00C0-U+00FF}, {@code U+0100-U+017F},
 * {@code U+1EA0-U+1EF9} ({@code wabooks.commons.StringUtils:52-54}). {@link #TONED} contains
 * {@code ƠơƯư} = <b>U+01A0, U+01A1, U+01AF, U+01B0</b> — all four between {@code U+017F} and
 * {@code U+1EA0}, so <b>the gate excludes four characters its own table maps</b>. Because the gate is
 * evaluated per string, a value whose only non-ASCII characters are those four is never stripped by
 * legacy at all. Common Vietnamese given names sit entirely inside the gap: {@code Sơn}, {@code Tư},
 * {@code Vương}, {@code Hương}.
 *
 * <p>Handover §8.4 pitfall ④ ({@code 08_기능추출_이관가이드.md:64}) states the rule and its
 * consequence: <i>"베트남어 성조 제거: 송금/수취인명·적요·주소는 펌뱅킹/이체 전 성조 제거.
 * 미적용 시 은행 측 거절"</i> — tones must be removed before dispatch; <b>if not applied, the bank
 * rejects it</b>. So legacy's gate emits files the vendor says will be refused.
 *
 * <p><b>The vendor statement is the whole basis — there is no production instance (corrected
 * 2026-08-01).</b> This Javadoc previously added "and the production record agrees: exactly two files
 * ever carried multibyte receiver names and neither reached {@code PROC_STEP='3'}". That is withdrawn.
 * Both files are dated {@code 20251010}, nineteen days before tone stripping was added to
 * {@code FILE_DEPOSIT_TRANSFER_BATCH1} ({@code IDO.TB_FIRM_FILE_DATA_C001} version
 * {@code 20251029205935}), so they predate the gate rather than demonstrate it leaking; they are also a
 * resubmission pair whose payload was edited between attempts, which points at a data rejection. Since
 * that IMP, no dispatched file has carried an unstripped name. D-L is a latent defect corrected
 * pre-emptively, with <b>zero</b> observed occurrences.
 *
 * <p>Applying the table unconditionally is therefore <b>a legacy defect corrected, not a refactor
 * regression</b> — the same shape as the D-7 {@code ANT_DIV_CD} canonicalisation, where legacy carried
 * two inconsistent encodings of one rule and the refactor adopted the one the bank accepts.
 *
 * <p><b>Accepted consequence — do not "restore parity" here.</b> For {@code Sơn} legacy stores and
 * sends {@code Sơn}; we store and send {@code Son}. The accented form is lost from
 * {@code TB_FIRM_FILE_DATA.FINC_MDA_OWNER_NM} relative to legacy. That is correct: the column records
 * what was dispatched, and what legacy dispatched was refused. Re-adding the gate to preserve the
 * diacritic would reintroduce a rejected-by-the-bank file.
 *
 * <p>Recorded as <b>D-L</b> in {@code wecms-api/docs/audits/legacy-batch-boundary-rule-sweep-2026-07-31.md}
 * §1.
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
