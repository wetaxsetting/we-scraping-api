package com.kosign.wecms.domain.payment.internaltransfer.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sort whitelist behaviour for both Internal Transfer list
 * endpoints. The lenient {@code parse(...)} contract is the gate that
 * keeps user-supplied {@code sort_field} values from ever reaching a
 * raw SQL string — verifying it explicitly here means a regression
 * (e.g. someone adding a {@code throw} on unknown values) shows up
 * immediately.
 */
class InternalTransferSortFieldTest {

    @Test
    void scheduled_parse_should_return_empty_for_blank_or_unknown() {
        assertThat(InternalTransferSortField.parse(null)).isEmpty();
        assertThat(InternalTransferSortField.parse("")).isEmpty();
        assertThat(InternalTransferSortField.parse("   ")).isEmpty();
        assertThat(InternalTransferSortField.parse("DROP_TABLE")).isEmpty();
        assertThat(InternalTransferSortField.parse("checkbox")).isEmpty();
    }

    @Test
    void scheduled_parse_should_map_canonical_field_ids_case_insensitively() {
        assertThat(InternalTransferSortField.parse("REG_DTM"))
                .contains(InternalTransferSortField.REG_DTM);
        assertThat(InternalTransferSortField.parse("reg_dtm"))
                .contains(InternalTransferSortField.REG_DTM);
        assertThat(InternalTransferSortField.parse("TOTAL_TRAN_AMT"))
                .contains(InternalTransferSortField.TOTAL_TRAN_AMT);
    }

    @Test
    void scheduled_entity_property_should_be_jpa_camel() {
        // Catches typos where the enum constant's value doesn't match the entity property.
        assertThat(InternalTransferSortField.REG_DTM.entityProperty()).isEqualTo("regDtm");
        assertThat(InternalTransferSortField.TITL.entityProperty()).isEqualTo("titl");
        assertThat(InternalTransferSortField.TOTAL_TRAN_AMT.entityProperty()).isEqualTo("totalTranAmt");
        assertThat(InternalTransferSortField.PAY_DATE.entityProperty()).isEqualTo("payTranDt");
    }

    @Test
    void result_parse_should_return_empty_for_unknown() {
        assertThat(InternalTransferResultSortField.parse("ETC")).isEmpty();
        assertThat(InternalTransferResultSortField.parse("APPR_STTS")).isEmpty();
    }

    @Test
    void result_parse_should_map_canonical_field_ids() {
        assertThat(InternalTransferResultSortField.parse("PROC_STAT"))
                .contains(InternalTransferResultSortField.PROC_STAT);
    }
}
