package com.kosign.wecms.domain.payment.haengsung;

import java.util.Optional;

/**
 * Payment-type selector for the HAENGSUNG Oracle Refresh import
 * ({@code haengsung-payment-refresh} §5.2). Maps the wire {@code payment_type}
 * to the Oracle {@code TB_TRNS_MAST} read predicate and the per-master routing.
 *
 * <p>Wire values (uppercase): {@code VENDOR}, {@code EXPENSE}, {@code PAYROLL},
 * {@code OVERSEAS}, {@code ALL}.
 */
public enum HaengsungPaymentType {

    /** {@code FILE_GB NOT IN ('AF1','AC2')} and not an Expense file name. */
    VENDOR,
    /** {@code FILE_NM LIKE 'E%'/'e%'} (M-2 unresolved — see spec §17). */
    EXPENSE,
    /** {@code FILE_GB = 'AC2'}. */
    PAYROLL,
    /** {@code FILE_GB = 'AF1'}. */
    OVERSEAS,
    /** No payment-type narrowing — import every pending master, routed per FILE_GB. */
    ALL;

    /** Case-insensitive parse of the wire value. */
    public static Optional<HaengsungPaymentType> fromWire(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        for (HaengsungPaymentType v : values()) {
            if (v.name().equalsIgnoreCase(trimmed)) return Optional.of(v);
        }
        return Optional.empty();
    }
}
