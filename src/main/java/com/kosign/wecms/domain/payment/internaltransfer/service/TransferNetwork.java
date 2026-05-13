package com.kosign.wecms.domain.payment.internaltransfer.service;

import java.util.Optional;

/**
 * Transfer-network classification used by {@link CutoffValidator}.
 * Derived from {@code SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD} on the first
 * line of the batch (mirrors {@code PymnHelper.validateTransferTiming}
 * which reads from the first detail row).
 */
public enum TransferNetwork {

    NAPAS,
    CITAD,
    SWIFT;

    /**
     * Map a legacy {@code ANT_DIV_CD} value to the network enum.
     * Defaults to {@link #CITAD} when the value is null / unknown,
     * matching legacy {@code PymnHelper.validateTransferTiming}
     * behaviour at JEX line 3144.
     */
    public static TransferNetwork fromAntDivCd(String antDivCd) {
        return parse(antDivCd).orElse(CITAD);
    }

    /** Lenient parser — returns empty for unknown values so callers
     *  can decide whether to default or reject. */
    public static Optional<TransferNetwork> parse(String antDivCd) {
        if (antDivCd == null) return Optional.empty();
        String trimmed = antDivCd.trim().toUpperCase();
        if (trimmed.isEmpty()) return Optional.empty();
        return switch (trimmed) {
            case "N", "NAPAS", "INTER" -> Optional.of(NAPAS);
            case "S", "SWIFT"          -> Optional.of(SWIFT);
            case "C", "CITAD"          -> Optional.of(CITAD);
            default                    -> Optional.empty();
        };
    }
}
