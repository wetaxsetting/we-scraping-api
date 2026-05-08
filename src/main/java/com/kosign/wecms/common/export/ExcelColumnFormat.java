package com.kosign.wecms.common.export;

/**
 * Wire vocabulary for the value-coercion contract between the frontend
 * and the {@link ExcelWorkbookWriter}. The frontend ships the format
 * hint per column; the writer owns the actual cell rendering so every
 * consumer renders amounts / dates the same way.
 *
 * <ul>
 *   <li>{@link #TEXT} — string passthrough (default for unknown values).</li>
 *   <li>{@link #AMOUNT} — wire decimal string parsed to a number cell with
 *       {@code #,##0.##} format and right alignment.</li>
 *   <li>{@link #NUMBER} — like {@link #AMOUNT} but with the integer
 *       format {@code #,##0}.</li>
 *   <li>{@link #DATE} — legacy {@code YYYYMMDD} string rendered as
 *       {@code yyyy-MM-dd}.</li>
 *   <li>{@link #DATETIME} — legacy {@code YYYYMMDDHHmmss} string rendered
 *       as {@code yyyy-MM-dd HH:mm:ss}.</li>
 * </ul>
 *
 * Unknown / blank values fall back to {@link #TEXT}; never throw — a
 * stale frontend contract should not 500 the export.
 */
public enum ExcelColumnFormat {
    TEXT,
    AMOUNT,
    NUMBER,
    DATE,
    DATETIME;

    public static ExcelColumnFormat parse(String wire) {
        if (wire == null) return TEXT;
        try {
            return valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return TEXT;
        }
    }
}
