package com.kosign.wecms.common.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for {@link FirmBankingSvcType} — the F/S/G/B leg
 * discriminator + i18n-key authority for the unified
 * {@code V_FIRM_BANKING_HISTORY} view (V60). Style mirrors the sibling
 * {@code FirmBankingServiceNameResolverTest} so the two enums are
 * tested with the same shape (constant count → code values → i18n-key
 * namespace → {@code fromCode} lookup → leniency).
 */
class FirmBankingSvcTypeTest {

    @Test
    void definesAllFourLegs() {
        // The V60 view emits exactly four discriminator letters
        // (F/S/G/B). If a fifth leg lands without an enum entry,
        // FirmBankingHistoryService#toRow ships svcTypeKey=null for
        // that leg's rows and the frontend silently misclassifies them
        // as F (default in extractFirmBankingServiceType). This guard
        // fails fast in CI when that drift happens.
        assertThat(FirmBankingSvcType.values()).hasSize(4);
    }

    @Test
    void codeValuesMatchViewDiscriminator() {
        assertThat(FirmBankingSvcType.FIRM_BANKING_TRANSACTION.code()).isEqualTo("F");
        assertThat(FirmBankingSvcType.SCRAPING_TRANSFER.code()).isEqualTo("S");
        assertThat(FirmBankingSvcType.VIETNAM_GDT_INQUIRY.code()).isEqualTo("G");
        assertThat(FirmBankingSvcType.VIETNAM_BUSINESS_INQUIRY.code()).isEqualTo("B");
    }

    @Test
    void i18nKeysFollowSettingsServiceTypeNamespace() {
        // Exact strings — these match the keys already registered in
        // locales/{en,ko,vi}.json so the frontend's t(svcTypeKey) call
        // resolves without going through any suffix-extraction logic.
        assertThat(FirmBankingSvcType.FIRM_BANKING_TRANSACTION.i18nKey())
                .isEqualTo("settings.firm_banking_history.service_type.firm_banking_transaction");
        assertThat(FirmBankingSvcType.SCRAPING_TRANSFER.i18nKey())
                .isEqualTo("settings.firm_banking_history.service_type.scraping_transfer");
        assertThat(FirmBankingSvcType.VIETNAM_GDT_INQUIRY.i18nKey())
                .isEqualTo("settings.firm_banking_history.service_type.vietnam_gdt_inquiry");
        assertThat(FirmBankingSvcType.VIETNAM_BUSINESS_INQUIRY.i18nKey())
                .isEqualTo("settings.firm_banking_history.service_type.vietnam_business_inquiry");
    }

    @Test
    void everyConstantHasNonNullI18nKey() {
        // Documented contract on FirmBankingSvcType.i18nKey():
        // "Always non-null for the four enum constants — every leg has
        // a verified EN/KO/VI translation in the existing locale files."
        // Pin that contract so a future refactor can't accidentally
        // null-out a key.
        for (FirmBankingSvcType t : FirmBankingSvcType.values()) {
            assertThat(t.i18nKey())
                    .as("i18n key for %s", t.name())
                    .isNotNull()
                    .startsWith("settings.firm_banking_history.service_type.");
        }
    }

    // ------------------------------------------------------------------
    // fromCode — known / blank / unknown / whitespace
    // ------------------------------------------------------------------

    @Test
    void fromCode_resolvesKnown() {
        assertThat(FirmBankingSvcType.fromCode("F"))
                .contains(FirmBankingSvcType.FIRM_BANKING_TRANSACTION);
        assertThat(FirmBankingSvcType.fromCode("S"))
                .contains(FirmBankingSvcType.SCRAPING_TRANSFER);
        assertThat(FirmBankingSvcType.fromCode("G"))
                .contains(FirmBankingSvcType.VIETNAM_GDT_INQUIRY);
        assertThat(FirmBankingSvcType.fromCode("B"))
                .contains(FirmBankingSvcType.VIETNAM_BUSINESS_INQUIRY);
    }

    @Test
    void fromCode_trimsWhitespace() {
        // Defensive — DEV occasionally emits trailing whitespace on
        // legacy CHAR(1) columns. Trim keeps the resolver robust and
        // matches FirmBankingServiceNameResolver's behaviour.
        assertThat(FirmBankingSvcType.fromCode(" F "))
                .contains(FirmBankingSvcType.FIRM_BANKING_TRANSACTION);
    }

    @Test
    void fromCode_blankInputIsEmpty() {
        assertThat(FirmBankingSvcType.fromCode(null)).isEmpty();
        assertThat(FirmBankingSvcType.fromCode("")).isEmpty();
        assertThat(FirmBankingSvcType.fromCode("   ")).isEmpty();
    }

    @Test
    void fromCode_unknownIsEmpty() {
        // Lookup is case-sensitive (legacy values are uppercase only);
        // lowercase / unknown letters / multi-char input all miss.
        assertThat(FirmBankingSvcType.fromCode("X")).isEmpty();
        assertThat(FirmBankingSvcType.fromCode("f")).isEmpty();
        assertThat(FirmBankingSvcType.fromCode("FF")).isEmpty();
        assertThat(FirmBankingSvcType.fromCode("Firm")).isEmpty();
    }

    // ------------------------------------------------------------------
    // resolveKey — convenience helper used by FirmBankingHistoryService.toRow
    // ------------------------------------------------------------------

    @Test
    void resolveKey_returnsI18nKeyForKnown() {
        assertThat(FirmBankingSvcType.resolveKey("F"))
                .isEqualTo("settings.firm_banking_history.service_type.firm_banking_transaction");
        assertThat(FirmBankingSvcType.resolveKey("S"))
                .isEqualTo("settings.firm_banking_history.service_type.scraping_transfer");
        assertThat(FirmBankingSvcType.resolveKey("G"))
                .isEqualTo("settings.firm_banking_history.service_type.vietnam_gdt_inquiry");
        assertThat(FirmBankingSvcType.resolveKey("B"))
                .isEqualTo("settings.firm_banking_history.service_type.vietnam_business_inquiry");
    }

    @Test
    void resolveKey_returnsNullForUnknownOrBlank() {
        // Frontend treats null as "render the raw svc_type letter
        // verbatim" so the UI never goes blank on an unrecognised code.
        // Pin that contract here.
        assertThat(FirmBankingSvcType.resolveKey(null)).isNull();
        assertThat(FirmBankingSvcType.resolveKey("")).isNull();
        assertThat(FirmBankingSvcType.resolveKey("   ")).isNull();
        assertThat(FirmBankingSvcType.resolveKey("X")).isNull();
        assertThat(FirmBankingSvcType.resolveKey("f")).isNull();
    }
}
