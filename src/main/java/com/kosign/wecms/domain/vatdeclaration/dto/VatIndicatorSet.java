package com.kosign.wecms.domain.vatdeclaration.dto;

/**
 * Full indicator block of one declaration — every value is an integer-VND
 * string on the wire (HTKK parity; mirrors the e-Invoice amount-as-string
 * convention). Field names match the HTKK XML tags {@code ct21..ct43}.
 */
public record VatIndicatorSet(
        String ct21,
        String ct22,
        String ct23,
        String ct24,
        String ct23a,
        String ct24a,
        String ct25,
        String ct26,
        String ct27,
        String ct28,
        String ct29,
        String ct30,
        String ct31,
        String ct32,
        String ct32a,
        String ct33,
        String ct33a,
        String ct34,
        String ct35,
        String ct36,
        String ct37,
        String ct38,
        String ct39a,
        String ct40a,
        String ct40b,
        String ct40,
        String ct41,
        String ct42,
        String ct43
) {
}
