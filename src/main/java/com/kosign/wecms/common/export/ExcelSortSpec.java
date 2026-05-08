package com.kosign.wecms.common.export;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire shape mirroring the list-endpoint sort contract
 * ({@code sortField} = View Setting {@code FIELD_ID},
 * {@code sortDirection} = {@code ASC} / {@code DESC}). Either being
 * {@code null} / blank lets the per-screen provider fall back to its
 * legacy default ORDER BY — same lenient resolution as the list path.
 */
public record ExcelSortSpec(
        @JsonProperty("field")     String field,
        @JsonProperty("direction") String direction
) { }
