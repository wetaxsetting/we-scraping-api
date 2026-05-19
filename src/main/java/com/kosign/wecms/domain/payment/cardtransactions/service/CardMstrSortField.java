package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Card Management Setting modal
 * (legacy {@code card_0001_001}'s "Manage Cards" button →
 * {@code card_0001_003_r001}).
 *
 * <p>The "Card Company" column on the modal renders {@code BANK_NM}
 * (resolved via {@link com.kosign.wecms.domain.banking.service.BankCdResolver
 * BankCdResolver}'s in-memory cache). Sorting that column maps to the
 * underlying {@code BANK_CD} key — sorting by the resolved display name
 * would require a JOIN to {@code BANK_INFM} for an effectively
 * identical ordering, given that the bank-code keys and the display
 * names share a stable order in the registry. Same shape as Card
 * Transactions (`CardTrnsHstrSortField`) — legacy {@code BANK_CD} also
 * sorts the ledger's bank column.
 *
 * <p>Lenient {@link #parse} returns {@link Optional#empty()} on any
 * blank / unknown wire token so the service falls back to the default
 * ORDER BY rather than echoing user input into a query.
 */
public enum CardMstrSortField implements SortableField {

    BANK_CD       ("bankCd"),
    CARD_NO       ("cardNo"),
    CARD_HLDR_NM  ("cardHldrNm"),
    CARD_NICK_NM  ("cardNickNm"),
    MOD_DTM       ("modDtm");

    private final String entityProperty;

    CardMstrSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() { return entityProperty; }

    /** Lenient parse — null / blank / unknown returns
     *  {@link Optional#empty()} so the service degrades gracefully to
     *  the default ORDER BY. */
    public static Optional<CardMstrSortField> parse(String raw) {
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
