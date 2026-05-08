package com.kosign.wecms.common.export;

import java.util.List;
import java.util.Map;

/**
 * Result of an {@link ExportRowProvider#fetch(ExcelExportRequest)} call.
 *
 * @param rows       One map per data row, keyed by {@code FIELD_ID}.
 *                   Values are raw — strings, BigDecimals, numbers, or
 *                   {@code null}. The {@link ExcelWorkbookWriter} coerces
 *                   per-column based on {@link ExcelColumnSpec#format()}.
 *                   {@link ExcelColumnSpec#fieldId()} entries that are
 *                   missing from the map render as an empty cell.
 * @param subHeader  Backend-computed summary line (count + totals) shown
 *                   under the title. {@code null} = no summary.
 */
public record ExportFetch(
        List<Map<String, Object>> rows,
        String subHeader
) {
    public static ExportFetch of(List<Map<String, Object>> rows, String subHeader) {
        return new ExportFetch(rows, subHeader);
    }
}
