package com.kosign.wecms.domain.banking.ownaccounttransfer.service;

import com.kosign.wecms.common.web.SortableField;
import com.kosign.wecms.domain.banking.entity.AcctMstr;

/**
 * Sort whitelist for Banking &gt; Own Account Transfer (source-account
 * picker). Constant names mirror the View Setting {@code FIELD_ID} values
 * seeded in {@code SYS_SCR_OPTION_WECMS} for screen
 * {@code own-account-transfer} (V180) and map to the matching JPA field on
 * {@link AcctMstr}.
 *
 * <p>Columns intentionally NOT in this whitelist:
 * <ul>
 *   <li>{@code CHECKBOX} / {@code ETC} — special row-selection / action
 *       columns (per {@link com.kosign.wecms.domain.setting.service.ScreenOptionService#SPECIAL_FIELD_IDS})
 *       that must never be sortable.
 *   <li>{@code ACCT_BAL_LST_STS} — Last Refresh column is a composite
 *       (engine status pill + humanized timestamp). Seeded with
 *       {@code SORT_YN='N'} so the header glyph stays inert.
 * </ul>
 *
 * <p>The service applies the sort in-memory after the per-row filter pass
 * (allow-list, account-type, HAENGSUNG saving gate, ADMIN / W-grant). The
 * source list is bounded to ~tens of rows per tenant so DB-side sort is
 * not load-bearing; the whitelist is the entity-property lookup table
 * used by both DB-side (for any future paginated query) and in-memory
 * comparators.
 */
public enum OwnAccountTransferSortField implements SortableField {

    BANK_CD("bankCd"),
    ACCT_NICK_NM("acctNickNm"),
    BAL("bal"),
    AVI_BAL("realAmt"),
    ACCT_BAL_LST_STS("acctBalUpdDtm");

    private final String entityProperty;

    OwnAccountTransferSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient lookup for the wire {@code FIELD_ID}. Returns {@code null}
     * for unknown / blank values so the service falls back to the default
     * ordering rather than rejecting the request.
     */
    public static OwnAccountTransferSortField parse(String fieldId) {
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
