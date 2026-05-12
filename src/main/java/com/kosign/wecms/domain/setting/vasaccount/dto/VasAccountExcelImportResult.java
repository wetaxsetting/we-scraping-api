package com.kosign.wecms.domain.setting.vasaccount.dto;

/**
 * Response payload for {@code POST /api/v1/settings/vas-account/excel/import}.
 *
 * <p>{@code insertedCount} is the number of fresh rows persisted.
 * {@code replacedCount} is the number of pre-existing live rows the
 * Reset/Replace path soft-deleted before inserting — {@code 0} on the
 * Append path.
 */
public record VasAccountExcelImportResult(
        int insertedCount,
        int replacedCount
) { }
