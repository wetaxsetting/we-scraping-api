package com.kosign.wecms.domain.payment.common;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.payment.validation.FieldCd;
import com.kosign.wecms.domain.payment.validation.PymnType;
import com.kosign.wecms.domain.payment.validation.SpecialCharValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Authoritative server-side blocked-character enforcement for the payment
 * <b>save</b> and <b>dispatch</b> boundaries (audit task-44508, P0).
 *
 * <p>The Excel-upload preview is advisory only — a direct API call, an edited
 * draft, or a copied payload can carry blocked characters straight past it.
 * This guard re-validates the field values a caller intends to PERSIST (or
 * DISPATCH) against the live {@code BLOCKED_CHARS_RULE} rows for the payment's
 * actual withdrawal bank, and throws {@code WBE_PAYMENT_BLOCKED_CHARS} (which
 * surfaces the same localized "{field} must not contain: {chars}" message the
 * preview shows) on the first violation.
 *
 * <p><b>Bank code:</b> callers must pass the SAME withdrawal bank code the
 * upload preview validated against — i.e. the raw code the user selected in
 * the upload dialog / the internal {@code BANK_CD} the {@code BLOCKED_CHARS_RULE}
 * rows are keyed on — NOT the canonical NAPAS {@code OUT_BANK_CD} persisted on
 * the master (those two forms differ; see {@code OutBankCdNormalizer}). At
 * dispatch the caller resolves the stored withdrawal account back to its
 * internal bank code before calling this guard.
 *
 * <p><b>Values:</b> pass the values already normalized via
 * {@link LegacyTextNormalizer#normalize(String)} so validation runs against —
 * and persistence stores — the same byte-identical value the preview produced.
 * This guard validates; it does not normalize.
 */
@Component
@RequiredArgsConstructor
public class PaymentBlockedCharGuard {

    private final SpecialCharValidator validator;

    /** One (field, value) pair to check. */
    public record Field(FieldCd fieldCd, String value) {}

    public static Field field(FieldCd fieldCd, String value) {
        return new Field(fieldCd, value);
    }

    /**
     * Validate every field against the withdrawal bank's active rules.
     * Throws {@code WBE_PAYMENT_BLOCKED_CHARS} on the first violation. A blank
     * withdrawal bank, a blank value, or a field with no active rule passes.
     *
     * @param withdrawalBankCd the internal withdrawal {@code BANK_CD} (the
     *                         value the rules are keyed on) — never the NAPAS
     *                         {@code OUT_BANK_CD} nor a beneficiary bank.
     */
    public void assertClean(String withdrawalBankCd, PymnType pymnType, List<Field> fields) {
        if (fields == null) return;
        for (Field f : fields) {
            validator.validate(withdrawalBankCd, pymnType, f.fieldCd(), f.value())
                    .ifPresent(msg -> { throw new BusinessException("WBE_PAYMENT_BLOCKED_CHARS", msg); });
        }
    }

    /**
     * Dispatch-time defense-in-depth (audit task-44508, P0): reject on the first
     * field that contains any character blocked for the withdrawal bank + field
     * under ANY payment type. Used at the final bank-file-build boundary where
     * the originating payment type is not cleanly known; see
     * {@link SpecialCharValidator#validateAnyType}.
     *
     * @param internalWithdrawalBankCd the INTERNAL {@code BANK_CD} (resolve the
     *                                 stored NAPAS {@code OUT_BANK_CD} via
     *                                 {@code OutBankCdNormalizer.toInternalBankCd}
     *                                 before calling).
     */
    public void assertCleanForDispatch(String internalWithdrawalBankCd, List<Field> fields) {
        if (fields == null) return;
        for (Field f : fields) {
            validator.validateAnyType(internalWithdrawalBankCd, f.fieldCd(), f.value())
                    .ifPresent(msg -> { throw new BusinessException("WBE_PAYMENT_BLOCKED_CHARS", msg); });
        }
    }
}
