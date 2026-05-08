package com.kosign.wecms.common.export;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One column in an {@link ExcelExportRequest}. Frontend ships these in
 * the order the user sees them on screen — already filtered through the
 * View Setting popover (visible columns only, special CHECKBOX/ETC
 * stripped). The backend re-validates against the per-screen allow-list
 * in {@link ExportRowProvider#allowedFieldIds()} and re-intersects with
 * the persisted View Setting visibility before writing the workbook.
 *
 * @param fieldId   View Setting {@code FIELD_ID} (uppercase semantic key —
 *                  e.g. {@code BANK}, {@code BALANCE}). Must be in the
 *                  per-screen allow-list or it is dropped silently.
 * @param header    Already-translated label for the column header. The
 *                  frontend owns locale; backend trusts the string.
 * @param format    Value-coercion hint — see {@link ExcelColumnFormat}.
 *                  {@code null} or unknown falls back to {@code TEXT}.
 * @param ccyCd     Currency code for {@code AMOUNT} columns. Currently
 *                  used only for the column header suffix when present
 *                  (e.g. {@code "Balance (VND)"}); cell values stay raw
 *                  numeric so Excel power-users can re-sum the column.
 * @param align     Cell alignment — see {@link ExcelAlignment}.
 *                  {@code null} falls back to {@code LEFT}, except
 *                  {@code AMOUNT} / {@code NUMBER} which default to
 *                  {@code RIGHT} when align is unspecified.
 * @param widthChars Optional explicit column width in '0'-character
 *                  units (the POI {@code setColumnWidth} unit). When
 *                  set, the writer applies it verbatim (with POI's
 *                  internal-padding compensation so what the caller
 *                  asks for is what the user sees). When {@code null},
 *                  the writer falls back to its auto-measure pass —
 *                  {@code max(formatMin, headerWidth, maxRowWidth+padding)}.
 *                  Pages SHOULD pass an explicit width per column so
 *                  exports are immediately readable; the auto-measure
 *                  is the safety net for new screens that haven't
 *                  tuned widths yet.
 */
public record ExcelColumnSpec(
        @JsonProperty("fieldId")    String fieldId,
        @JsonProperty("header")     String header,
        @JsonProperty("format")     String format,
        @JsonProperty("ccyCd")      String ccyCd,
        @JsonProperty("align")      String align,
        @JsonProperty("widthChars") Integer widthChars
) {

    public ExcelColumnFormat resolvedFormat() {
        return ExcelColumnFormat.parse(format);
    }

    public ExcelAlignment resolvedAlign() {
        ExcelAlignment a = ExcelAlignment.parse(align);
        if (a != ExcelAlignment.LEFT) return a;
        // Default amount / number columns to right-align when the caller
        // didn't override — matches the on-screen table convention.
        ExcelColumnFormat f = resolvedFormat();
        if (f == ExcelColumnFormat.AMOUNT || f == ExcelColumnFormat.NUMBER) {
            return ExcelAlignment.RIGHT;
        }
        return ExcelAlignment.LEFT;
    }
}
