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
    // FCY-A02 (PO decision 2026-06-06 — supersedes HR-1): sort the
    // "Last Update" column by ACCT_HIS_LST_INQ_DT, the source the cell
    // now displays for online accounts (AccountService.resolveLastUpdate
    // / legacy fnsh_0005_001_api_osmc.ts:956-969). Fixed-deposit-only
    // rows display ACCT_BAL_LST_INQ_DTM, but the column sort key is a
    // single property — ACCT_HIS_LST_INQ_DT — matching legacy's
    // server-side single-column ORDER BY.
    LAST_UPDATE("acctHisLstInqDt");

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
