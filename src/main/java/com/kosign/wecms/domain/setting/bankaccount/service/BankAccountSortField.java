package com.kosign.wecms.domain.setting.bankaccount.service;

import com.kosign.wecms.common.web.SortableField;

/**
 * Sort whitelist for Settings &gt; Bank Account. Constant names mirror
 * the View Setting {@code FIELD_ID} values seeded in {@code
 * SYS_SCR_OPTION_WECMS} for screen {@code settings-bank-account} (V127);
 * the mapped property is the matching JPA field on {@link
 * com.kosign.wecms.domain.banking.entity.AcctMstr}.
 *
 * <p>Columns intentionally NOT in this whitelist (their seeds carry
 * {@code SORT_YN='N'} per the "seed + whitelist must agree" rule in
 * {@code wecms-api-main/CLAUDE.md §"Common table sort"}):
 * <ul>
 *   <li>{@code CHECKBOX} — special row-selection column.</li>
 *   <li>{@code VIEW_PERMISSION} / {@code WITHDRAWAL_PERMISSION} —
 *       derived count + label from {@code ACCT_AUTH_INFO} aggregates.</li>
 *   <li>{@code AUDIT_LOGS} — derived from the latest {@code
 *       AUDIT_LOGS_INFM} row.</li>
 * </ul>
 *
 * <p>{@code VAS_NAME} is sortable as of 2026-05-15 — the denormalised
 * {@code VAS_ACCT_NM} on {@code ACCT_MSTR} is the backing field, so
 * the sort doesn't need to join {@code BUSI_VAS_ACCT_INFM}.
 *
 * <p>{@code BANK} sorts by {@code bankCd} (legacy parity — Account
 * Inquiry uses the same property; an alphabetical-by-display-name
 * variant would require a join, deferred).
 */
public enum BankAccountSortField implements SortableField {

    BANK             ("bankCd"),
    ACCOUNT_NICKNAME ("acctNickNm"),
    DESCRIPTION      ("cont"),
    ACCOUNT_TYPE     ("acctTypeCd"),
    MATURITY_DATE    ("expiDt"),
    VAS_CODE         ("vasAcctCd"),
    VAS_NAME         ("vasAcctNm");

    private final String entityProperty;

    BankAccountSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /** Lenient parse — unknown / blank returns {@code null} so the
     *  service falls back to the default ORDER BY. Defence in depth
     *  against stale frontend bundles. */
    public static BankAccountSortField parse(String fieldId) {
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
