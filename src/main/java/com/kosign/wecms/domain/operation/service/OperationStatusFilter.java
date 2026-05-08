package com.kosign.wecms.domain.operation.service;

/**
 * Server-side filter chain for Operation Status Inquiry list / export.
 * Mirrors the JEX {@code aosv_0001_001_r001} input set
 * ({@code SRCH_WD}, {@code COMPANYSTATUS}); refactor wire uses the
 * project-wide {@code q} / {@code companyStatus} names.
 *
 * <p>{@code companyStatus} is a partial-match substring against the raw
 * Vietnamese {@code COMPANYSTATUS} stored value (legacy parity — the
 * legacy localisation helper {@code wb_get_operat_stts(value)} is
 * commented out in the action JSP). {@code keyword} is the free-text
 * input matched case-insensitively against {@code COMPANYNAME} OR
 * {@code TAXCODE}; the service handles {@code %}/{@code _} escaping
 * before binding to JPQL.
 *
 * <p>Both values are nullable; null = "no filter on this dimension".
 */
public record OperationStatusFilter(String keyword, String companyStatus) {

    public static OperationStatusFilter empty() {
        return new OperationStatusFilter(null, null);
    }
}
