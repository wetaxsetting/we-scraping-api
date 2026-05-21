package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Card Transactions list endpoint
 * (legacy {@code card_0001_001_r001}).
 *
 * <p>Constant names mirror the View Setting {@code FIELD_ID} values
 * seeded in V176 ({@code 'card-transactions'}). A wire {@code sort_field}
 * that doesn't match any constant resolves to {@link Optional#empty()}
 * so the service falls back to the default ORDER BY rather than echoing
 * user input into a query — same precedent as
 * {@code InternalTransferSortField}, {@code AccountSortField}, etc.
 *
 * <p>Special FIELD_IDs ({@code CHECKBOX_1}, {@code CHECKBOX_2},
 * {@code MEMO}, {@code MEMO_2}) are intentionally absent from the
 * whitelist — admin-controlled rows are never sortable per
 * {@code wecms-api-main/CLAUDE.md} §"Common table sort".
 *
 * <p>Projection-only columns ({@code DISP_DOC_ID}, {@code APPR_DOC_NO},
 * {@code CHART_ACCT_NM}, {@code CARD_TRNS_USAGE_NM}) are also absent
 * because Sprint 0 doesn't materialise them as JPA entity properties —
 * they render from joins / resolvers, not from the entity. The matching
 * V176 seed rows ship {@code SORT_YN='N'} so the View Setting +
 * whitelist agreement rule holds (no UI-visible-but-inert sort glyph).
 */
public enum CardTrnsHstrSortField implements SortableField {

    TRSC_DT       ("trscDt"),
    CARD_NO       ("cardNo"),
    MERCH_NM      ("merchNm"),
    CCY_CD        ("ccyCd"),
    TRSC_AMT      ("trscAmt"),
    STTS          ("prchStts"),
    CONT          ("cont");

    private final String entityProperty;

    CardTrnsHstrSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    /** Lenient parse — null / blank / unknown returns
     *  {@link Optional#empty()} so the service degrades gracefully to
     *  the default ORDER BY. Same shape as
     *  {@code InternalTransferSortField#parse}. */
    public static Optional<CardTrnsHstrSortField> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(valueOf(trimmed.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
