package com.kosign.wecms.domain.payment.internaltransfer.service;

import java.util.Optional;

/**
 * {@code SCDUL_PYMN_SUMR.TRNF_YN} — whether the batch has been
 * dispatched. {@code 'N'} = draft / pre-dispatch (default at create
 * time), {@code 'Y'} = dispatched.
 *
 * <p>Mirrors legacy {@code wabooks.enums.TrnfYn}; only the two
 * supported codes are modelled (no {@code etc.} branch).
 */
public enum TrnfYn {

    NOT_TRANSFERRED("N"),
    TRANSFERRED("Y");

    private final String code;

    TrnfYn(String code) { this.code = code; }

    public String code() { return code; }

    public static Optional<TrnfYn> fromCode(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (TrnfYn value : values()) {
            if (value.code.equals(trimmed)) return Optional.of(value);
        }
        return Optional.empty();
    }
}
