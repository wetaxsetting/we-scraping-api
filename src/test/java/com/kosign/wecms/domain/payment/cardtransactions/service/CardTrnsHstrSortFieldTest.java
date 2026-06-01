package com.kosign.wecms.domain.payment.cardtransactions.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sort-whitelist behaviour for the Card Transactions list
 * endpoint. The lenient {@code parse(...)} contract is the gate that
 * keeps user-supplied {@code sort_field} values from ever reaching a
 * raw SQL string — verifying it explicitly here means a regression
 * (e.g. someone adding a {@code throw} on unknown values) shows up
 * immediately.
 *
 * <p>Mirrors {@code InternalTransferSortFieldTest} shape.
 */
class CardTrnsHstrSortFieldTest {

    @Test
    void parse_returnsEmptyForNullBlankOrUnknown() {
        assertThat(CardTrnsHstrSortField.parse(null)).isEmpty();
        assertThat(CardTrnsHstrSortField.parse("")).isEmpty();
        assertThat(CardTrnsHstrSortField.parse("   ")).isEmpty();
        assertThat(CardTrnsHstrSortField.parse("DROP_TABLE")).isEmpty();
        assertThat(CardTrnsHstrSortField.parse("checkbox_1")).isEmpty();
        // Special FIELD_IDs ride through View Setting but are never
        // sortable — absent from the whitelist per CLAUDE.md §"Common
        // table sort".
        assertThat(CardTrnsHstrSortField.parse("MEMO")).isEmpty();
    }

    @Test
    void parse_mapsProjectionOnlyColumnsToProxyConstants() {
        // The V237 reseed flipped every one of the 13 user data columns
        // to SORT_YN='Y', so the projection-only columns now resolve to
        // their proxy-mapped constants (no silent default-ORDER-BY
        // fallback for a column the UI shows as sortable).
        assertThat(CardTrnsHstrSortField.parse("DISP_DOC_ID"))
                .contains(CardTrnsHstrSortField.DISP_DOC_ID);
        assertThat(CardTrnsHstrSortField.parse("APPR_DOC_NO"))
                .contains(CardTrnsHstrSortField.APPR_DOC_NO);
        assertThat(CardTrnsHstrSortField.parse("CHART_ACCT_NM"))
                .contains(CardTrnsHstrSortField.CHART_ACCT_NM);
    }

    @Test
    void parse_mapsCanonicalFieldIdsCaseInsensitively() {
        assertThat(CardTrnsHstrSortField.parse("TRSC_DT"))
                .contains(CardTrnsHstrSortField.TRSC_DT);
        assertThat(CardTrnsHstrSortField.parse("trsc_dt"))
                .contains(CardTrnsHstrSortField.TRSC_DT);
        assertThat(CardTrnsHstrSortField.parse("TRSC_AMT"))
                .contains(CardTrnsHstrSortField.TRSC_AMT);
        assertThat(CardTrnsHstrSortField.parse("STTS"))
                .contains(CardTrnsHstrSortField.STTS);
    }

    @Test
    void entityProperty_resolvesToJpaCamelCaseName() {
        // Catches typos where the enum constant's mapping doesn't match
        // a real entity property.
        assertThat(CardTrnsHstrSortField.TRSC_DT.entityProperty())  .isEqualTo("trscDt");
        assertThat(CardTrnsHstrSortField.CARD_NO.entityProperty())  .isEqualTo("cardNo");
        assertThat(CardTrnsHstrSortField.MERCH_NM.entityProperty()) .isEqualTo("merchNm");
        assertThat(CardTrnsHstrSortField.CCY_CD.entityProperty())   .isEqualTo("ccyCd");
        assertThat(CardTrnsHstrSortField.TRSC_AMT.entityProperty()) .isEqualTo("trscAmt");
        assertThat(CardTrnsHstrSortField.STTS.entityProperty())     .isEqualTo("prchStts");
        assertThat(CardTrnsHstrSortField.CONT.entityProperty())     .isEqualTo("cont");
    }
}
