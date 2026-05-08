package com.kosign.wecms.domain.operation.dto;

/**
 * Wire row for the Operation Status Inquiry listing.
 *
 * Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 */
public record OperationStatusRow(
        String taxcode,
        String companyNm,
        String companyStatus,
        String statusCd,
        String officeAdd,
        String lastUpdate
) { }
