package com.kosign.wecms.domain.vatdeclaration.dto;

/**
 * Save-draft payload — carries ONLY the user-editable (white-cell) indicators
 * as integer-VND strings; a {@code null} field leaves the stored value
 * unchanged. Every derived (grey-cell) indicator is recomputed server-side by
 * {@code VatIndicatorCalculator} regardless of what the client sends.
 */
public record VatDeclarationUpdateRequest(
        String ct21,
        String ct23,
        String ct24,
        String ct23a,
        String ct24a,
        String ct26,
        String ct29,
        String ct30,
        String ct32,
        String ct32a,
        String ct33a,
        String ct37,
        String ct38,
        String ct39a,
        String ct40b,
        String ct42
) {
}
