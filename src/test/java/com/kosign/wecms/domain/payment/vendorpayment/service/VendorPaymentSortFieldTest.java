package com.kosign.wecms.domain.payment.vendorpayment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Vendor Payment "Scheduled Payment" sort whitelist
 * ({@link VendorPaymentSortField}) — mirror of
 * {@code InternalTransferSortFieldTest}.
 *
 * <p>The decisive guard here is the <b>sort/display alignment fix
 * (2026-06-16)</b>: Schedule Date, Requestor, and Approval Ref are displayed
 * from the LEFT-joined {@code EXPT_RESOL_MSTR} (C-3 / C-4 + {@code DOC_NO}),
 * so their sort keys must target the {@code erm} join-alias properties — NOT
 * the former {@code SCDUL_PYMN_SUMR} columns. A regression that reverts any of
 * the three to {@code payTranDt} / {@code regId} / {@code erpFileNum} would
 * silently re-introduce the sort/display mismatch (sorting by a column the
 * user can't see), so it is asserted explicitly.
 *
 * <p>Why a unit assertion rather than a {@code @DataJpaTest}: the
 * {@code ScdulPymnSumrRepository.search} query embeds the Postgres function
 * {@code wb_get_appr_stts(...)} (no H2 equivalent, no Testcontainers per
 * wecms-api CLAUDE.md), so it cannot execute in-suite. Runtime ORDER BY
 * resolution of the {@code erm.*} properties is guaranteed by Spring Data
 * JPA's query enhancer leaving an already-alias-qualified sort property
 * un-prefixed (the {@code erm} alias is registered from the query's
 * {@code LEFT JOIN ExptResolMstr erm}); this test pins the mapping so the
 * enhancer receives the correct alias path.
 */
class VendorPaymentSortFieldTest {

    @Test
    void parse_should_return_empty_for_blank_or_unknown() {
        assertThat(VendorPaymentSortField.parse(null)).isEmpty();
        assertThat(VendorPaymentSortField.parse("")).isEmpty();
        assertThat(VendorPaymentSortField.parse("   ")).isEmpty();
        assertThat(VendorPaymentSortField.parse("DROP_TABLE")).isEmpty();
        assertThat(VendorPaymentSortField.parse("CHECKBOX")).isEmpty();
        // APPR_STTS is intentionally NOT a whitelist constant (derived status).
        assertThat(VendorPaymentSortField.parse("APPR_STTS")).isEmpty();
        // APPRVR_NM deferred (needs EXPT_RESOL_APPRVR_INFM join).
        assertThat(VendorPaymentSortField.parse("APPRVR_NM")).isEmpty();
    }

    @Test
    void parse_should_map_canonical_field_ids_case_insensitively() {
        assertThat(VendorPaymentSortField.parse("REG_DTM"))
                .contains(VendorPaymentSortField.REG_DTM);
        assertThat(VendorPaymentSortField.parse("pay_date"))
                .contains(VendorPaymentSortField.PAY_DATE);
        assertThat(VendorPaymentSortField.parse("DRAFT_USER_NM"))
                .contains(VendorPaymentSortField.DRAFT_USER_NM);
    }

    @Test
    void root_entity_columns_sort_by_scdul_pymn_sumr_properties() {
        assertThat(VendorPaymentSortField.REG_DTM.entityProperty()).isEqualTo("regDtm");
        assertThat(VendorPaymentSortField.FILE_NM.entityProperty()).isEqualTo("orgFileNm");
        assertThat(VendorPaymentSortField.TOT_TRNS_CNT.entityProperty()).isEqualTo("totTrnsCnt");
        assertThat(VendorPaymentSortField.TOTAL_TRAN_AMT.entityProperty()).isEqualTo("totalTranAmt");
        assertThat(VendorPaymentSortField.TITL.entityProperty()).isEqualTo("titl");
    }

    /**
     * Sort/display alignment fix — the three EXPT_RESOL_MSTR-displayed columns
     * must sort by the {@code erm} join alias, matching what the user sees.
     */
    @Test
    void erm_displayed_columns_sort_by_join_alias_not_scdul_pymn_sumr() {
        // C-4: Schedule Date displays ERM.PAY_DATE → sort by erm.payDate.
        assertThat(VendorPaymentSortField.PAY_DATE.entityProperty())
                .isEqualTo("erm.payDate");
        // C-3: Requestor displays ERM.DRAFT_USER_NM → sort by erm.draftUserNm.
        assertThat(VendorPaymentSortField.DRAFT_USER_NM.entityProperty())
                .isEqualTo("erm.draftUserNm");
        // Approval Ref displays ERM.DOC_NO → sort by erm.docNo.
        assertThat(VendorPaymentSortField.APPR_DOC_NO.entityProperty())
                .isEqualTo("erm.docNo");

        // Regression guard — none of the three may revert to the old
        // SCDUL_PYMN_SUMR columns (which diverge from the displayed value).
        assertThat(VendorPaymentSortField.PAY_DATE.entityProperty()).isNotEqualTo("payTranDt");
        assertThat(VendorPaymentSortField.DRAFT_USER_NM.entityProperty()).isNotEqualTo("regId");
        assertThat(VendorPaymentSortField.APPR_DOC_NO.entityProperty()).isNotEqualTo("erpFileNum");
    }
}
