package com.kosign.wecms.domain.payment.internaltransfer.service;

import java.util.Optional;

/**
 * Payment-module discriminator value for {@code SCDUL_PYMN_SUMR.SCDUL_PYMN_TYPE_CD}.
 * Mirrors legacy {@code wabooks.enums.ScdulPymnTypeCd}.
 *
 * <p>Per {@code wecms-api-main/CLAUDE.md} §"Domain enums" we do NOT bulk-port
 * every legacy code — Internal Transfer's first consumer needs only its own
 * value ({@code "03"}) and the {@code fromCode(...)} parser. Other Payment
 * modules add their constants when they land.
 */
public enum ScdulPymnTypeCd {

    /** Internal Transfer batch — the only value Internal Transfer endpoints
     *  read or write. Legacy: {@code wabooks.enums.ScdulPymnTypeCd.INTERNAL}. */
    INTERNAL("03");

    private final String code;

    ScdulPymnTypeCd(String code) { this.code = code; }

    public String code() { return code; }

    public static Optional<ScdulPymnTypeCd> fromCode(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (ScdulPymnTypeCd value : values()) {
            if (value.code.equals(trimmed)) return Optional.of(value);
        }
        return Optional.empty();
    }
}
