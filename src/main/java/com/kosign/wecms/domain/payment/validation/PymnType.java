package com.kosign.wecms.domain.payment.validation;

/**
 * Payment-type code keying {@code BLOCKED_CHARS_RULE.PYMN_TPYE}.
 * Mirrors legacy {@code wabooks.enums.PymnType}.
 *
 * <p>The legacy enum exposes {@code INTERNAL_TRANSFER("INTR")} et al.;
 * refactor consumers use the short DB code directly via {@link #code()}.
 * Excel parsers pass {@code PymnType.INTERNAL_TRANSFER} (etc.) when
 * calling {@link SpecialCharValidator}; the validator reads the code
 * with {@link #code()} for the rule lookup.
 */
public enum PymnType {
    INTERNAL_TRANSFER("INTR"),
    VENDOR_PAYMENT   ("VEND"),
    EXPENSE_PAYMENT  ("EXPN"),
    PAYROLL_PAYMENT  ("PAYR"),
    OVERSEAS_PAYMENT ("OVER");

    private final String code;

    PymnType(String code) { this.code = code; }

    public String code() { return code; }
}
