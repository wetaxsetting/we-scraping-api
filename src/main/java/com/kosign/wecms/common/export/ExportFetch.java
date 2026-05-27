package com.kosign.wecms.common.export;

import java.util.List;
import java.util.Map;

/**
 * Result of an {@link ExportRowProvider#fetch(ExcelExportRequest)} call.
 *
 * @param rows            One map per data row, keyed by {@code FIELD_ID}.
 *                        Values are raw — strings, BigDecimals, numbers, or
 *                        {@code null}. The {@link ExcelWorkbookWriter}
 *                        coerces per-column based on
 *                        {@link ExcelColumnSpec#format()}. Entries that are
 *                        missing from the map render as an empty cell.
 * @param subHeader       Backend-computed summary line shown under the title.
 *                        When {@link #subHeaderRight} is {@code null} this
 *                        renders as a single merged cell across the column
 *                        range (legacy single-line behaviour). When
 *                        {@link #subHeaderRight} is supplied this is the
 *                        left-aligned half. {@code null} = no summary row.
 * @param subHeaderRight  Optional right-aligned half of the subheader row,
 *                        mirroring legacy {@code {left_title, right_title}}
 *                        structure (e.g. {@code resl_0001_001_osmc}
 *                        Approval Box export). When non-null the writer
 *                        splits the row into left+right merged halves
 *                        instead of one centered merged cell.
 */
public record ExportFetch(
        List<Map<String, Object>> rows,
        String subHeader,
        String subHeaderRight
) {
    /** Single-line subheader (legacy single consumer behaviour). */
    public static ExportFetch of(List<Map<String, Object>> rows, String subHeader) {
        return new ExportFetch(rows, subHeader, null);
    }

    /** Left+right subheader, mirroring legacy
     *  {@code {left_title, right_title}} composition. */
    public static ExportFetch of(
            List<Map<String, Object>> rows,
            String subHeaderLeft,
            String subHeaderRight
    ) {
        return new ExportFetch(rows, subHeaderLeft, subHeaderRight);
    }
}
