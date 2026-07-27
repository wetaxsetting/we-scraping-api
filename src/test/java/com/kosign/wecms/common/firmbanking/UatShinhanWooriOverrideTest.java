package com.kosign.wecms.common.firmbanking;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M — UAT-only Shinhan/Woori depositor-inquiry BANK_CD/PRTN_ID override
 * (legacy PymnHelper.java:917-947). Verifies the override fires ONLY under the
 * UAT profile, ONLY for Shinhan (970424) / Woori (970457), ONLY for product
 * types F/H — and is otherwise a pass-through.
 */
class UatShinhanWooriOverrideTest {

    private static final String SHINHAN = "970424";
    private static final String WOORI   = "970457";
    private static final String BIDV    = "970418";
    private static final String CONF_BANK = "conf-bank";
    private static final String CONF_PRTN = "conf-prtn";

    private UatShinhanWooriOverride withProfile(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        return new UatShinhanWooriOverride(env);
    }

    @Test
    void uat_shinhan_F_overridesPrtnIdToWEBC1_keepsBankCd() {
        var r = withProfile("uat").resolve(SHINHAN, "F", CONF_BANK, CONF_PRTN);
        assertThat(r.bankCd()).isEqualTo(SHINHAN);   // OUT_BANK_CD stays the bank code
        assertThat(r.prtnId()).isEqualTo("WEBC1");
    }

    @Test
    void uat_shinhan_H_overridesPrtnIdToHASU1() {
        var r = withProfile("uat").resolve(SHINHAN, "H", CONF_BANK, CONF_PRTN);
        assertThat(r.bankCd()).isEqualTo(SHINHAN);
        assertThat(r.prtnId()).isEqualTo("HASU1");
    }

    @Test
    void uat_woori_F_overridesPrtnIdTo000001008() {
        var r = withProfile("uat").resolve(WOORI, "F", CONF_BANK, CONF_PRTN);
        assertThat(r.bankCd()).isEqualTo(WOORI);
        assertThat(r.prtnId()).isEqualTo("000001008");
    }

    @Test
    void uat_woori_H_overridesPrtnIdTo000001020() {
        var r = withProfile("uat").resolve(WOORI, "H", CONF_BANK, CONF_PRTN);
        assertThat(r.bankCd()).isEqualTo(WOORI);
        assertThat(r.prtnId()).isEqualTo("000001020");
    }

    @Test
    void uat_unrelatedBank_passesThrough() {
        var r = withProfile("uat").resolve(BIDV, "F", CONF_BANK, CONF_PRTN);
        assertThat(r.bankCd()).isEqualTo(CONF_BANK);
        assertThat(r.prtnId()).isEqualTo(CONF_PRTN);
    }

    @Test
    void uat_unknownProductType_passesThrough() {
        // Only F/H are overridden; other product letters keep firm-config values.
        var r = withProfile("uat").resolve(SHINHAN, "M", CONF_BANK, CONF_PRTN);
        assertThat(r.bankCd()).isEqualTo(CONF_BANK);
        assertThat(r.prtnId()).isEqualTo(CONF_PRTN);
    }

    @Test
    void nonUat_shinhan_passesThrough() {
        var r = withProfile("sit").resolve(SHINHAN, "F", CONF_BANK, CONF_PRTN);
        assertThat(r.bankCd()).isEqualTo(CONF_BANK);
        assertThat(r.prtnId()).isEqualTo(CONF_PRTN);
        assertThat(withProfile("sit").isActive()).isFalse();
    }

    @Test
    void nonUat_prod_shinhan_passesThrough() {
        var r = withProfile("prod").resolve(WOORI, "H", CONF_BANK, CONF_PRTN);
        assertThat(r.prtnId()).isEqualTo(CONF_PRTN);
    }

    @Test
    void uat_isActive_true() {
        assertThat(withProfile("uat").isActive()).isTrue();
    }
}
