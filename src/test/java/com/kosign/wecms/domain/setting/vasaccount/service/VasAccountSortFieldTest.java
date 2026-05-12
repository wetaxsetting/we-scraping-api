package com.kosign.wecms.domain.setting.vasaccount.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whitelist parser parity — covers the four sortable FIELD_IDs seeded
 * in V79, plus the lenient fall-through that the service relies on
 * (unknown → null → default sort, never an exception).
 */
class VasAccountSortFieldTest {

    @Test
    void parse_recognisedCodes_mappedToEntityProperty() {
        assertThat(VasAccountSortField.parse("VAS_ACCT_CD").entityProperty()).isEqualTo("vasAcctCd");
        assertThat(VasAccountSortField.parse("VAS_ACCT_NM").entityProperty()).isEqualTo("vasAcctNm");
        assertThat(VasAccountSortField.parse("VAS_ACCT_TYPE").entityProperty()).isEqualTo("vasAcctType");
        assertThat(VasAccountSortField.parse("STTS").entityProperty()).isEqualTo("stts");
    }

    @Test
    void parse_caseInsensitive() {
        assertThat(VasAccountSortField.parse("vas_acct_cd")).isEqualTo(VasAccountSortField.VAS_ACCT_CD);
        assertThat(VasAccountSortField.parse("Vas_Acct_Cd")).isEqualTo(VasAccountSortField.VAS_ACCT_CD);
    }

    @Test
    void parse_nullOrBlank_returnsNull() {
        assertThat(VasAccountSortField.parse(null)).isNull();
        assertThat(VasAccountSortField.parse("")).isNull();
        assertThat(VasAccountSortField.parse("   ")).isNull();
    }

    @Test
    void parse_unknown_returnsNull() {
        assertThat(VasAccountSortField.parse("BIZ_KEY")).isNull();
        assertThat(VasAccountSortField.parse("'; DROP TABLE x; --")).isNull();
    }
}
