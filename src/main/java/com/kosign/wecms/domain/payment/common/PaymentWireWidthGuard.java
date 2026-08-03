package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.i18n.MessageResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Entry-layer guard for the four free-text slots of the firm-banking fixed-length {@code D} record
 * ({@link WireField}) — C-14.
 *
 * <h2>What it guards, and why the column width is the wrong bound</h2>
 * {@code TB_PAY_FILE_DATA} caps each of these values at {@code varchar(150)}, and the record gives
 * the two memo fields 210 bytes, so <b>pure-ASCII overflow of the wire slot is arithmetically
 * impossible</b>: 150 characters can never exceed 150 (or 210) bytes. No truncation is added anywhere
 * — see the decision recorded in {@link WireField}.
 *
 * <p>The reachable failure is <b>multibyte text surviving tone-stripping</b>. The dispatch pipeline is
 * {@code value → NFC → CR/LF strip → Unicode-space sanitize →
 * LegacyTextNormalizer/VietnameseText.replaceTone → FixedLengthCodec}, and {@code replaceTone} only
 * maps the 134 precomposed Vietnamese code points in its table. Anything else passes through at its
 * full UTF-8 width:
 * <ul>
 *   <li><b>Unicode spaces — handled, not rejected.</b> NBSP (U+00A0) and its {@code \p{Zs}} siblings
 *       are {@code > 0x7F} and are not in the tone table, so they would trip the non-Latin check
 *       below. {@link LegacyTextNormalizer#sanitizeSpaces} folds them to ASCII {@code 0x20} first,
 *       matching what legacy does silently on every one of these fields' setters. An invisible
 *       character is corrected, never reported.</li>
 *   <li><b>Korean / CJK (primary).</b> 50 Hangul syllables = 150 UTF-8 bytes, and 150 syllables = 450
 *       bytes — comfortably inside {@code varchar(150)} and three times over the wire slot.</li>
 *   <li><b>NFD-decomposed Vietnamese (secondary).</b> The tone table holds precomposed code points
 *       only, so a decomposed {@code ế} ({@code e} + U+0302 + U+0301) is not matched and keeps its
 *       combining marks. This is why {@link LegacyTextNormalizer#toNfc} runs first: after NFC the
 *       character is precomposed, the tone table matches it, and it strips to plain {@code e}.</li>
 * </ul>
 *
 * <h2>Two distinct outcomes, deliberately</h2>
 * The message an operator gets has to name the actual problem, because the two causes need opposite
 * corrections:
 * <ul>
 *   <li>{@code WBE_PAYMENT_WIRE_FIELD_NON_LATIN} — the value still contains non-Latin characters
 *       <i>after</i> NFC + tone-stripping. The field cannot carry that text at all; shortening it will
 *       not help. Rewriting it in unaccented Latin letters will.</li>
 *   <li>{@code WBE_PAYMENT_WIRE_FIELD_TOO_LONG} — the value is plain Latin but still over the slot.
 *       Shortening it is the fix. Unreachable through the current write paths (the column gates
 *       first); kept because it is the correct answer if a column is ever widened, and because a
 *       "too long" message for Korean text would send the operator down the wrong road.</li>
 * </ul>
 * Checking non-Latin <i>first</i> is what keeps those two apart: a 60-syllable Korean memo is both
 * non-Latin and over-width, and reporting it as "too long" would be technically true and
 * operationally useless.
 *
 * <h2>Where this runs</h2>
 * Both entry surfaces, mirroring {@link PaymentBlockedCharGuard}:
 * <ul>
 *   <li>{@link #check} returns a message for the <b>Excel upload preview</b>, so the offending cell is
 *       highlighted with the other field errors and nothing is persisted;</li>
 *   <li>{@link #assertClean} throws for the <b>save path</b>, so a direct API call, an edited draft or
 *       a replayed payload cannot walk past the preview.</li>
 * </ul>
 * The {@code FixedLengthCodec} throw stays as the backstop. It is not redundant — it covers values
 * that reach dispatch from legacy JEX rows, from an ERP passthrough, or from a future write path that
 * forgets this guard — but it fires mid-dispatch, when the user can no longer fix the row.
 *
 * <p><b>Values:</b> pass the raw user value. This guard applies NFC + tone-stripping itself to model
 * what the codec will see; it does not mutate anything and does not replace
 * {@link LegacyTextNormalizer#normalize} on the persistence path.
 */
@Component
@RequiredArgsConstructor
public class PaymentWireWidthGuard {

    private final MessageResolver messageResolver;

    /** One (wire slot, raw value) pair to check. */
    public record Field(WireField wireField, String value) {}

    public static Field field(WireField wireField, String value) {
        return new Field(wireField, value);
    }

    /**
     * Check one value against its wire slot.
     *
     * @param value the raw user-entered value; NFC + tone-stripping are applied here, so callers pass
     *              the value before or after {@link LegacyTextNormalizer#normalize} interchangeably
     *              (the pipeline is idempotent).
     * @return a localized error message, or empty when the value fits the slot as plain Latin text.
     *         Null / blank always passes — required-field checks belong to the caller.
     */
    public Optional<String> check(WireField wireField, String value) {
        if (wireField == null || value == null || value.isBlank()) {
            return Optional.empty();
        }

        // Model the dispatch pipeline exactly: NFC (so decomposed Vietnamese becomes matchable),
        // Unicode-space normalization, then the legacy tone table, then measure UTF-8 bytes — which is
        // what FixedLengthCodec.put does. Anything this method accepts, the codec accepts.
        //
        // sanitizeSpaces MUST precede indexOfNonLatin below. NBSP (U+00A0) is > 0x7F and survives
        // replaceTone, so without it the check reports "non-Latin character ' ' at position 12" for a
        // value whose only defect is an invisible space the operator cannot see, find, or delete.
        // Legacy corrects it silently on the setter of all four of these fields
        // (ScdulPymnSumrDetl:220/259/272/285) — see LegacyTextNormalizer.sanitizeSpaces.
        String onWire = LegacyTextNormalizer.replaceTone(
                LegacyTextNormalizer.sanitizeSpaces(
                        LegacyTextNormalizer.stripCrLf(LegacyTextNormalizer.toNfc(value))));

        String label = messageResolver.resolve(wireField.labelKey());

        int firstNonLatin = indexOfNonLatin(onWire);
        if (firstNonLatin >= 0) {
            // Report the offending character so the operator can find it in a long memo.
            return Optional.of(messageResolver.resolve(
                    "WBE_PAYMENT_WIRE_FIELD_NON_LATIN",
                    label,
                    String.valueOf(onWire.charAt(firstNonLatin)),
                    firstNonLatin + 1));
        }

        int bytes = onWire.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > wireField.wireBytes()) {
            return Optional.of(messageResolver.resolve(
                    "WBE_PAYMENT_WIRE_FIELD_TOO_LONG",
                    label, bytes, wireField.wireBytes(), bytes - wireField.wireBytes()));
        }
        return Optional.empty();
    }

    /**
     * Save-path enforcement: throw on the first field that will not fit its wire slot.
     *
     * <p>Throws {@code WBE_PAYMENT_WIRE_FIELD_NON_LATIN} or {@code WBE_PAYMENT_WIRE_FIELD_TOO_LONG}
     * with the already-composed message as its single argument, so the envelope carries the same text
     * the upload preview shows (same pattern as {@code WBE_PAYMENT_BLOCKED_CHARS}).
     */
    public void assertClean(List<Field> fields) {
        if (fields == null) {
            return;
        }
        for (Field f : fields) {
            check(f.wireField(), f.value())
                    .ifPresent(msg -> { throw new BusinessException("WBE_PAYMENT_WIRE_FIELD", msg); });
        }
    }

    /**
     * Index of the first character the bank's fixed-length channel cannot carry, or {@code -1}.
     *
     * <p>"Latin" here means <b>US-ASCII</b> — every byte {@code <= 0x7F}. That is deliberately
     * stricter than "one UTF-8 byte wide" would need to be, and it matches the observed reality of the
     * channel: all six UAT sample files are pure ASCII (0 bytes &gt; 0x7F), and tone-stripping exists
     * precisely because the bank does not accept composed Vietnamese. Accepting Latin-1 accents here
     * would let through exactly the values tone-stripping was written to remove.
     */
    private static int indexOfNonLatin(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return i;
            }
        }
        return -1;
    }
}
