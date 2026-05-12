package com.kosign.wecms.domain.setting.purposesettings.dto;

import java.util.List;

/**
 * Response payload for {@code POST /api/v1/settings/purpose-settings/excel/preview}.
 * Carries every parsed row (valid + invalid) alongside the summary
 * counts the preview UI renders in its header chips ({@code Completed} /
 * {@code Error}).
 *
 * <p>Mirrors legacy {@code comm_0187_004_r001} response shape:
 * {@code TOT_CNT} / {@code SUC_CNT} / {@code ERR_CNT} / {@code REC}.
 *
 * <p>Both valid + invalid rows ride in {@link #rows} so the UI can
 * show every input row to the user. The frontend persists this whole
 * list and forwards it back to the import endpoint — the import path
 * runs its own validation pass for race-safety, so it doesn't trust
 * the {@code valid} flag from the preview.
 */
public record PurposeExcelPreview(
        /** Every parsed row (valid + invalid), in Excel sheet order. */
        List<PurposeExcelPreviewRow> rows,
        /** Total parsed rows. */
        int totalCount,
        /** Rows with no validation errors. */
        int completedCount,
        /** Rows with at least one validation error. */
        int errorCount
) { }
