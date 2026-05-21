package com.kosign.wecms.domain.setting.vasaccount.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for Settings &gt; VAS Account. Constant names mirror the
 * View Setting {@code FIELD_ID} values (uppercase semantic key); the
 * mapped property is the matching JPA field on
 * {@link com.kosign.wecms.domain.setting.vasaccount.entity.BusiVasAcctInfm}.
 *
 * <p>Per the common-sort rule in {@code wecms-api/CLAUDE.md} §"Common
 * table sort", every column with {@code SORT_YN='Y'} in the View Setting
 * seed (V79) MUST appear here:
 * <ul>
 *   <li>{@code VAS_ACCT_CD}  → {@code vasAcctCd}</li>
 *   <li>{@code VAS_ACCT_NM}  → {@code vasAcctNm}</li>
 *   <li>{@code VAS_ACCT_TYPE}→ {@code vasAcctType}</li>
 *   <li>{@code STTS}          → {@code stts}</li>
 * </ul>
 *
 * <p>Unknown / blank wire values resolve to {@code null} via
 * {@link #parse(String)} so the service falls back to the default
 * {@code vasAcctCd ASC} order rather than rejecting the request.
 */
public enum VasAccountSortField implements SortableField {

    VAS_ACCT_CD("vasAcctCd"),
    VAS_ACCT_NM("vasAcctNm"),
    VAS_ACCT_TYPE("vasAcctType"),
    STTS("stts");

    private final String entityProperty;

    VasAccountSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /** Lenient lookup; case-insensitive. Returns {@code null} for unknown
     *  or blank input. */
    public static VasAccountSortField parse(String fieldId) {
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
