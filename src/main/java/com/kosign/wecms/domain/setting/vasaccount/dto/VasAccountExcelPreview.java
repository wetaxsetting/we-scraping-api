package com.kosign.wecms.domain.setting.vasaccount.dto;

import java.util.List;

/**
 * Response payload for {@code POST /api/v1/settings/vas-account/excel/preview}.
 * Carries the parsed + validated rows alongside the summary counts the
 * preview UI renders in its header chips ({@code Completed} /
 * {@code Error}).
 *
 * <p>The valid + invalid rows are both returned in {@code rows} so the
 * UI can show every input row to the user. The frontend persists this
 * whole list and forwards it back to the import endpoint — the import
 * path runs its own validation pass for race-safety, so it doesn't
 * trust the {@code valid} flag from the preview.
 */
public record VasAccountExcelPreview(
        /** Every parsed row (valid + invalid), in Excel sheet order. */
        List<VasAccountExcelPreviewRow> rows,
        /** Total parsed rows. */
        int totalCount,
        /** Rows with no validation errors. */
        int completedCount,
        /** Rows with at least one validation error. */
        int errorCount
) { }
