package com.kosign.wecms.domain.banking.service;

/**
 * Currency filter for the Daily Cash report. Mirrors legacy
 * {@code rept_0009_001_r001_act.jsp:115-124} logic: empty → ALL;
 * {@code FCY} → every currency except the tenant local; otherwise
 * exact match. Local currency is resolved from the tenant context
 * (VND today; KHR for Cambodia tenants when that lands).
 */
public enum DailyCashCurrencyFilter {
    ALL, FCY, VND, USD, KHR;

    public static DailyCashCurrencyFilter parse(String raw) {
        if (raw == null || raw.isBlank()) return ALL;
        try {
            return DailyCashCurrencyFilter.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return ALL;
        }
    }

    /** Returns the value to match exactly, or {@code null} when this
     *  filter does NOT pin to a single currency. */
    public String exact() {
        return switch (this) {
            case VND -> "VND";
            case USD -> "USD";
            case KHR -> "KHR";
            default  -> null;
        };
    }

    /** Returns the currency to EXCLUDE, or {@code null} when none. */
    public String exclude(String localCcy) {
        return this == FCY ? localCcy : null;
    }
}
