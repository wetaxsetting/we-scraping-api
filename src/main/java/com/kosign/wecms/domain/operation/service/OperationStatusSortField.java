package com.kosign.wecms.domain.operation.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for Add-on &gt; Operation Status Inquiry. Constant names
 * mirror the View Setting {@code FIELD_ID} values seeded in
 * {@code SYS_SCR_OPTION_WECMS} for screen {@code addon-operation-status};
 * the mapped property is the matching JPA field on
 * {@link com.kosign.wecms.domain.operation.entity.OprtSttsInfo}.
 *
 * <p>Columns intentionally NOT in this whitelist:
 * <ul>
 *   <li>{@code CHECKBOX} — special row-selection column (per
 *       {@link com.kosign.wecms.domain.setting.service.ScreenOptionService#SPECIAL_FIELD_IDS})
 *       that must never be sortable.
 * </ul>
 *
 * <p>Every other displayed column has a whitelist constant so the
 * frontend's {@code SORT_YN='Y'} flag and the backend whitelist agree
 * (CLAUDE.md "Common table sort" rule — visible-but-inert sort glyphs
 * are a regression).
 */
public enum OperationStatusSortField implements SortableField {

    COMPANY("companyname"),
    ADDRESS("officeadd"),
    OPERATION_STATUS("companystatus"),
    LAST_UPDATE("searchdatetime");

    private final String entityProperty;

    OperationStatusSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient lookup for the wire {@code FIELD_ID}. Returns {@code null}
     * for unknown / blank values so the service falls back to the legacy
     * default ORDER BY rather than rejecting the request.
     */
    public static OperationStatusSortField parse(String fieldId) {
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
