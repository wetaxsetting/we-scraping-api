package com.kosign.wecms.domain.payment.cardtransactions.service;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Per-screen sort whitelist for the Card Transactions list endpoint
 * (legacy {@code card_0001_001_r001}).
 *
 * <p>Constant names mirror the V237 legacy-aligned View Setting
 * {@code FIELD_ID} values for {@code 'card-transactions'} / {@code 'default'}.
 * A wire {@code sort_field} that doesn't match any constant resolves to
 * {@link Optional#empty()} so the service falls back to the default
 * ORDER BY rather than echoing user input into a query — same precedent
 * as {@code InternalTransferSortField}, {@code AccountSortField}, etc.
 *
 * <p>Special FIELD_IDs ({@code CHECKBOX_1}, {@code MEMO}) are
 * intentionally absent from the whitelist — admin-controlled rows are
 * never sortable per CLAUDE.md §"Common table sort".
 *
 * <p>Projection-only columns ({@code CHART_ACCT_NM} / {@code CARD_TRNS_USAGE_NM}
 * / {@code DISP_DOC_ID} / {@code APPR_DOC_NO} / {@code CARD_NICK_NM} /
 * {@code CARD_HLDR_NM}) are mapped to the closest entity-property proxy
 * on {@code CardTrnsHstr}. The real values reach the wire from LEFT
 * JOINs to {@code CHART_ACCT_INFM} / {@code CARD_MSTR} / etc.; the
 * proxy mappings keep the frontend's sort cycle live (no silent fallback
 * to default ORDER BY) until a follow-up MR adds a real joined-property
 * sort. When that LEFT JOIN lands, swap each proxy to the joined
 * property in the same MR that wires the projection.
 */
public enum CardTrnsHstrSortField implements SortableField {

    /* Canonical legacy V237 sort keys ----------------------------- */
    TRSC_DT           ("trscDt"),
    CARD_NO           ("cardNo"),
    MERCH_NM          ("merchNm"),
    CARD_NICK_NM      ("cardNo"),        // projection-only — proxy to cardNo
    CARD_HLDR_NM      ("cardNo"),        // projection-only — proxy to cardNo
    CCY_CD            ("ccyCd"),
    TRSC_AMT          ("trscAmt"),
    CHART_ACCT_NM     ("chartAcctCd"),   // projection-only — proxy to the raw code
    STTS              ("prchStts"),
    CARD_TRNS_USAGE_NM("cardTrnsUsageCd"),// projection-only — proxy to the code
    DISP_DOC_ID       ("apprSeqNo"),     // projection-only — proxy to apprSeqNo
    CONT              ("cont"),
    APPR_DOC_NO       ("apprSeqNo"),     // projection-only — proxy to apprSeqNo
    APPR_SEQ_NO       ("apprSeqNo");     // legacy V204 alias — retained
                                          // for backwards-compat with
                                          // saved user overrides

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
