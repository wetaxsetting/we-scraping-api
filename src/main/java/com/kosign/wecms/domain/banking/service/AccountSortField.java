package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for Account Inquiry (VND/FCY). Constant names mirror the
 * View Setting {@code FIELD_ID} values seeded in {@code SYS_SCR_OPTION_WECMS}
 * for screen {@code banking-account-inquiry-vnd}; the mapped property is
 * the matching JPA field on {@link com.kosign.wecms.domain.banking.entity.AcctMstr}.
 *
 * <p>Columns intentionally NOT in this whitelist:
 * <ul>
 *   <li>{@code CHECKBOX} / {@code ETC} — special row-selection / action
 *       columns (per {@link com.kosign.wecms.domain.setting.service.ScreenOptionService#SPECIAL_FIELD_IDS})
 *       that must never be sortable.
 *   <li>{@code VAS_ACCOUNT} — Account Inquiry's VAS column is currently a
 *       UI placeholder ("—") with no backing entity field; if a user
 *       requests a sort on this column the service falls back to the
 *       legacy default ORDER BY.
 * </ul>
 */
public enum AccountSortField implements SortableField {

    BANK("bankCd"),
    ACCOUNT_NICKNAME("acctNickNm"),
    BALANCE("bal"),
    AVAILABLE_BALANCE("realAmt"),
    LAST_UPDATE("acctBalUpdDtm");

    private final String entityProperty;

    AccountSortField(String entityProperty) {
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
    public static AccountSortField parse(String fieldId) {
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
