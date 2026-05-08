package com.kosign.wecms.common.export;

/**
 * Cell horizontal alignment for the Excel export. Mirrors the on-screen
 * table — text columns left, amount / number columns right, the rare
 * centered column explicit. {@link #parse(String)} is lenient: unknown /
 * blank values fall back to {@link #LEFT}.
 */
public enum ExcelAlignment {
    LEFT,
    RIGHT,
    CENTER;

    public static ExcelAlignment parse(String wire) {
        if (wire == null) return LEFT;
        try {
            return valueOf(wire.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LEFT;
        }
    }
}
