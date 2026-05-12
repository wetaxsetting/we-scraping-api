package com.kosign.wecms.domain.setting.vasaccount.dto;

import java.util.List;

/**
 * One parsed row from the uploaded Excel file. Wire field names follow
 * the global lowercase snake_case strategy.
 *
 * <p>{@code errors} carries human-readable validation messages (per
 * field). When non-empty the row is counted in the response's
 * {@code error_count} bucket and the {@code valid} flag is {@code false}.
 *
 * <p>{@code row_index} is the 1-based Excel row number (matches what the
 * user sees in Excel — header is row 1, first data row is row 2, etc.)
 * so error reporting reads naturally in the preview UI.
 */
public record VasAccountExcelPreviewRow(
        /** 1-based Excel row number (sheet position). */
        int rowIndex,
        /** Raw value from the {@code VAS Code} column. May be blank
         *  when invalid — the field error explains why. */
        String vasAcctCd,
        /** Raw value from the {@code VAS Account Name} column. */
        String vasAcctNm,
        /** True when {@link #errors} is empty. */
        boolean valid,
        /** Per-field error messages. Empty for valid rows. */
        List<VasAccountExcelFieldError> errors
) { }
