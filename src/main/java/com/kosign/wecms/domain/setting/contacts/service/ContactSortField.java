package com.kosign.wecms.domain.setting.contacts.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for {@code GET /api/v1/settings/contacts}. Mirrors the
 * legacy table-header {@code data-sort} attributes from
 * {@code comm_0184_001_view.jsp:155-172}, minus the two sub-table
 * columns ({@code BANK_NM}, {@code TRCO_BSUN_POST_ADRS}) which project
 * from child rows and have no clean entity-property mapping.
 *
 * <p>Constant names mirror the View Setting {@code FIELD_ID}
 * (uppercase semantic key) per the workspace common-sort rule.
 * {@link #entityProperty()} resolves to the {@link
 * com.kosign.wecms.domain.setting.contacts.entity.TrcoInfm} JPA
 * property name — typos fail fast at query time via Spring Data's
 * metamodel validation.
 */
public enum ContactSortField implements SortableField {

    HAND_TRCO_KEY("handTrcoKey"),
    TRCO_CMNM("trcoCmnm"),
    TRCO_TYPE("trcoType"),
    TRCO_TAX_ID("trcoTaxId");
    // BANK_NM + TRCO_BSUN_POST_ADRS deliberately omitted — sub-table
    // columns. V88 seeds them with SORT_YN='N' so the frontend
    // popover never shows a sort glyph for them.

    private final String entityProperty;

    ContactSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient parse — accepts case-insensitive enum names; unknown /
     * blank values return {@link Optional#empty()} so the service
     * falls back to the default ORDER BY.
     */
    public static Optional<ContactSortField> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(valueOf(trimmed.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
