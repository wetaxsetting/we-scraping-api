package com.kosign.wecms.common.web;

/**
 * Direction component of the common table-sort contract shared by every
 * list endpoint. Mirrors the frontend cycle {@code default → ASC → DESC →
 * default} — the absence of a direction is encoded as {@code null} so the
 * caller can fall back to the legacy default ORDER BY without inventing a
 * sentinel value.
 */
public enum SortDirection {

    ASC,
    DESC;

    /**
     * Lenient parser for the wire value. Accepts case-insensitive {@code
     * ASC} / {@code DESC}; everything else (including {@code null} and
     * blanks) returns {@code null} so the service treats the request as
     * unsorted instead of rejecting it.
     */
    public static SortDirection parse(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
