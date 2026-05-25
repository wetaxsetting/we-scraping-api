package com.kosign.wecms.domain.common.country.dto;

import java.util.List;

/**
 * Envelope for the {@code GET /api/v1/common/countries} response.
 * Carries the row list plus the total count for parity with the
 * standard list-response shape used elsewhere in this project.
 */
public record CountryListResponse(

        /** All country rows, sorted alphabetically by {@code natl_nm}. */
        List<CountryRow> countries,

        /** Total row count. */
        int totalCount

) { }
