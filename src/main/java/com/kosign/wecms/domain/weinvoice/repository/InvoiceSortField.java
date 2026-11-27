package com.kosign.wecms.domain.weinvoice.repository;

import java.util.Locale;

/**
 * Sort whitelist of the invoice list. The wire value ({@code sort_field}) only selects a
 * constant; the SQL comes from here, never from the request — same contract as
 * {@link com.kosign.wecms.common.web.SortableField}, but over SQL columns because the list
 * is a JDBC query on the engine tables. Accepts {@code issue_date} and {@code issueDate}
 * (UI Spec spelling); unknown values fall back to the default order.
 */
public enum InvoiceSortField {

    ISSUE_DATE("c.ngay_lap"),
    NUMBER("c.khhdon", "c.shdon"),
    PARTNER("c.ptnr_nm"),
    PRE_TAX("c.tien_chua_thue"),
    TAX("c.tien_thue"),
    TOTAL("c.tong_tien");

    private final String[] columns;

    InvoiceSortField(String... columns) {
        this.columns = columns;
    }

    /** {@code ORDER BY} body; {@code c.id} keeps pages stable when values tie. */
    public String orderBy(boolean desc) {
        String dir = desc ? " DESC NULLS LAST" : " ASC NULLS LAST";
        StringBuilder sb = new StringBuilder();
        for (String col : columns) {
            sb.append(col).append(dir).append(", ");
        }
        return sb.append("c.id").append(desc ? " DESC" : " ASC").toString();
    }

    public static InvoiceSortField parse(String wire) {
        if (wire == null || wire.isBlank()) return null;
        String key = wire.trim().replace("_", "").toLowerCase(Locale.ROOT);
        for (InvoiceSortField f : values()) {
            if (f.name().replace("_", "").toLowerCase(Locale.ROOT).equals(key)) return f;
        }
        return null;
    }
}
