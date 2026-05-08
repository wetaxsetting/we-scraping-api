package com.kosign.wecms.domain.setting.firmbankinghistory.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for the F-leg {@link TranTp} enum and the multi-leg
 * {@link FirmBankingServiceNameResolver}.
 *
 * <p>These two are the authoritative source for the wire-side
 * {@code svc_name_key} field — the legacy
 * {@code SERVICE_NAME_MAP} JS object at
 * {@code WECMS DEV/.../comm_0221_001.ts:40-52} no longer owns service
 * naming on the refactor. The tests below pin the F-leg enum to the
 * legacy {@code wabooks.enums.TranTp} value set
 * ({@code WECMS DEV/WECMS/src/wabooks/enums/TranTp.java:6-11}) and
 * verify each S/G/B-leg English literal emitted by the V60 view
 * resolves to its expected key.
 */
class FirmBankingServiceNameResolverTest {

    // ------------------------------------------------------------------
    // TranTp — F-leg gateway codes
    // ------------------------------------------------------------------

    @Test
    void tranTp_definesAllFourLegacyCodes() {
        // Mirrors legacy enum at TranTp.java:6-11. If a code is added
        // upstream, this assertion fails so the contributor remembers
        // to add the new constant + matching i18n key.
        assertThat(TranTp.values()).hasSize(4);
    }

    @Test
    void tranTp_codeValuesMatchLegacy() {
        assertThat(TranTp.BANKING_INQUIRY_OF_ACCOUNT_HOLDER.code()).isEqualTo("201");
        assertThat(TranTp.BANKING_DEPOSIT_TRANSFER.code()).isEqualTo("202");
        assertThat(TranTp.BANKING_DEPOSIT_TRANSFER_RESULT_INQUIRY.code()).isEqualTo("203");
        assertThat(TranTp.BANKING_EXCHANGE_TRANSFER_INQUIRY.code()).isEqualTo("204");
    }

    @Test
    void tranTp_i18nKeysFollowFbHistoryServiceNamespace_or_NullForUnverified() {
        for (TranTp t : TranTp.values()) {
            String key = t.i18nKey();
            if (key == null) {
                // Currently only 204 — no verified EN label in legacy
                // SERVICE_NAME_MAP. Document that absence here so a
                // future contributor either fills it in (verified) or
                // explains the new null entry.
                assertThat(t)
                        .as("only TranTp constants without a verified legacy EN label may carry a null i18nKey")
                        .isEqualTo(TranTp.BANKING_EXCHANGE_TRANSFER_INQUIRY);
            } else {
                assertThat(key)
                        .as("i18n key namespace for %s", t.name())
                        .isEqualTo("fb_history.service." + t.code());
            }
        }
    }

    @Test
    void tranTp_fromCode_resolvesKnown() {
        assertThat(TranTp.fromCode("201")).contains(TranTp.BANKING_INQUIRY_OF_ACCOUNT_HOLDER);
        assertThat(TranTp.fromCode("202")).contains(TranTp.BANKING_DEPOSIT_TRANSFER);
        assertThat(TranTp.fromCode("203")).contains(TranTp.BANKING_DEPOSIT_TRANSFER_RESULT_INQUIRY);
        assertThat(TranTp.fromCode("204")).contains(TranTp.BANKING_EXCHANGE_TRANSFER_INQUIRY);
    }

    @Test
    void tranTp_fromCode_trimsWhitespace() {
        assertThat(TranTp.fromCode(" 201 ")).contains(TranTp.BANKING_INQUIRY_OF_ACCOUNT_HOLDER);
    }

    @Test
    void tranTp_fromCode_emptyOrUnknown_isEmpty() {
        assertThat(TranTp.fromCode(null)).isEmpty();
        assertThat(TranTp.fromCode("")).isEmpty();
        assertThat(TranTp.fromCode("   ")).isEmpty();
        assertThat(TranTp.fromCode("999")).isEmpty();
        assertThat(TranTp.fromCode("foo")).isEmpty();
    }

    // ------------------------------------------------------------------
    // FirmBankingServiceNameResolver — multi-leg
    // ------------------------------------------------------------------

    @Test
    void resolver_fLeg_routesThroughTranTp() {
        assertThat(FirmBankingServiceNameResolver.resolve("F", "201"))
                .isEqualTo("fb_history.service.201");
        assertThat(FirmBankingServiceNameResolver.resolve("F", "202"))
                .isEqualTo("fb_history.service.202");
        assertThat(FirmBankingServiceNameResolver.resolve("F", "203"))
                .isEqualTo("fb_history.service.203");
    }

    @Test
    void resolver_fLeg_204_returnsNullForRawFallback() {
        // 204 is in the legacy enum but has no verified EN label in
        // SERVICE_NAME_MAP — resolver returns null so the frontend
        // renders the raw "204" (legacy parity). When a translation
        // is verified, fill in TranTp.BANKING_EXCHANGE_TRANSFER_INQUIRY's
        // i18nKey and update this assertion in the same MR.
        assertThat(FirmBankingServiceNameResolver.resolve("F", "204")).isNull();
    }

    @Test
    void resolver_fLeg_unknownCodeReturnsNull() {
        // Frontend falls back to the raw svcNm in this case; never blank.
        assertThat(FirmBankingServiceNameResolver.resolve("F", "999")).isNull();
        assertThat(FirmBankingServiceNameResolver.resolve("F", "201X")).isNull();
    }

    @Test
    void resolver_sLeg_mapsAllFourViewLiterals() {
        // These four strings are the V60 view's CASE-WHEN literals for
        // SVC_TYPE='S' (NAPAS + EXT_TRAN scraping legs). If the view
        // grows a new scraping label, this test fails to remind us to
        // extend both the resolver and the i18n bundle in lock-step.
        assertThat(FirmBankingServiceNameResolver.resolve("S", "Funds Transfer (Scraping)"))
                .isEqualTo("fb_history.service.funds_transfer_scraping");
        assertThat(FirmBankingServiceNameResolver.resolve("S", "Scraping Balance"))
                .isEqualTo("fb_history.service.scraping_balance");
        assertThat(FirmBankingServiceNameResolver.resolve("S", "Scraping Transaction"))
                .isEqualTo("fb_history.service.scraping_transaction");
        assertThat(FirmBankingServiceNameResolver.resolve("S", "Scraping Card Transaction"))
                .isEqualTo("fb_history.service.scraping_card_transaction");
    }

    @Test
    void resolver_gLeg_mapsBothEInvoiceLiterals() {
        assertThat(FirmBankingServiceNameResolver.resolve("G", "Sale E-Invoice Inquiry"))
                .isEqualTo("fb_history.service.sale_einvoice_inquiry");
        assertThat(FirmBankingServiceNameResolver.resolve("G", "Expense E-Invoice Inquiry"))
                .isEqualTo("fb_history.service.expense_einvoice_inquiry");
    }

    @Test
    void resolver_bLeg_mapsBusinessInquiryLiteral() {
        assertThat(FirmBankingServiceNameResolver.resolve("B", "Vietnam Business Inquiry"))
                .isEqualTo("fb_history.service.vietnam_business_inquiry");
    }

    @Test
    void resolver_unknownSvcType_returnsNull() {
        // Defensive — the view should never emit a non-FSGB svcType,
        // but if a future leg lands without resolver support the
        // frontend falls back to svcNm rather than rendering null.
        assertThat(FirmBankingServiceNameResolver.resolve("X", "201")).isNull();
        assertThat(FirmBankingServiceNameResolver.resolve("",  "201")).isNull();
        assertThat(FirmBankingServiceNameResolver.resolve(null, "201")).isNull();
    }

    @Test
    void resolver_unknownSvcNm_returnsNull() {
        assertThat(FirmBankingServiceNameResolver.resolve("S", "Unknown Scraping Variant")).isNull();
        assertThat(FirmBankingServiceNameResolver.resolve("G", "Unknown GDT Variant")).isNull();
        assertThat(FirmBankingServiceNameResolver.resolve("B", "Unknown Business Variant")).isNull();
        assertThat(FirmBankingServiceNameResolver.resolve("F", null)).isNull();
        assertThat(FirmBankingServiceNameResolver.resolve("F", "")).isNull();
    }

    @Test
    void resolver_trimsWhitespace() {
        // Defensive — DEV occasionally emits trailing whitespace on
        // legacy CHAR columns. Trim keeps the resolver robust.
        assertThat(FirmBankingServiceNameResolver.resolve("F ", " 201")).isEqualTo("fb_history.service.201");
    }
}
