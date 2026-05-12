package com.kosign.wecms.domain.setting.erptransactionhistory.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for Settings &gt; ERP Transaction History. Constant
 * names match the {@code data-sort} attributes on the legacy table
 * header ({@code comm_0219_001_view.jsp:347-357}); the mapped property
 * is the matching JPA field on
 * {@link com.kosign.wecms.domain.setting.erptransactionhistory.entity.ErpTrnsHstr}.
 *
 * <p>Columns intentionally NOT sortable:
 * <ul>
 *   <li>{@code FILE} — the legacy header shows a sort glyph but no IDO
 *       column maps to it (sort behaviour was undefined in JEX). The
 *       refactor renders the File column with no sort glyph (frontend
 *       {@code &lt;TableHeaderCell sortable={false}&gt;}). See
 *       {@code docs/refactor-wecms/features/erp-transaction-history.md §9 OQ-5}.</li>
 * </ul>
 *
 * <p>Unknown / blank wire {@code sort_field} values resolve to
 * {@code null} via {@link #parse(String)} so the service falls back to
 * the {@code REG_DTM DESC, SEQ_NO DESC} default rather than rejecting
 * the request. Sort whitelist contract is documented in
 * {@code wecms-api-main/CLAUDE.md §"Common table sort"}.
 */
public enum ErpTrnsHistorySortField implements SortableField {

    REG_DTM("regDtm"),
    ERP_TYPE("erpType"),
    TRNS_TYPE_CD("trnsTypeCd"),
    REG_USER("regId");

    private final String entityProperty;

    ErpTrnsHistorySortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient lookup. Accepts the FIELD_ID in any case
     * ({@code REG_DTM} / {@code reg_dtm} / {@code Reg_Dtm}); blank
     * input or any non-whitelisted value returns {@code null} so the
     * service falls back to the default ORDER BY.
     */
    public static ErpTrnsHistorySortField parse(String fieldId) {
        if (fieldId == null) return null;
        String trimmed = fieldId.trim();
        if (trimmed.isEmpty()) return null;
        try {
            return valueOf(trimmed.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
