package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import com.kosign.wecms.domain.banking.entity.BankInfm;
import com.kosign.wecms.domain.banking.repository.BankInfmRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bank-name display parity (Account Inquiry / shared resolver). The displayed
 * bank name must be the legacy SHORT name {@code wb_get_bank_nm(bank_cd,'s')}
 * (e.g. {@code "Shinhan VN"}), not the formal {@code RPRS_BANK_NM}
 * ({@code "Shinhan(Vietnam)"}). Fallback chain: short → formal → raw code.
 *
 * <p>The resolver is constructed directly (warmCache @PostConstruct is NOT
 * invoked), so every lookup exercises the per-call funnel path.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BankCdResolverShortNameTest {

    @Mock BankInfmRepository       bankInfmRepo;
    @Mock LegacyFunctionRepository legacyFunctions;

    @InjectMocks BankCdResolver resolver;

    /** Case 1 — Shinhan: short "Shinhan VN", NOT the formal "Shinhan(Vietnam)". */
    @Test
    void shinhan_displaysShortName() {
        when(legacyFunctions.resolveBankNmShort("11704088")).thenReturn("Shinhan VN");

        assertThat(resolver.resolveBankNm("11704088"))
                .isEqualTo("Shinhan VN")
                .isNotEqualTo("Shinhan(Vietnam)");
        // The displayed name comes from the legacy short-name funnel — the
        // formal RPRS_BANK_NM repository is never consulted on the happy path.
        verify(bankInfmRepo, never()).findById("11704088");
    }

    /** Case 2 — Woori: "Woori VN", not "Woori(Vietnam)". */
    @Test
    void woori_displaysShortName() {
        when(legacyFunctions.resolveBankNmShort("11704020")).thenReturn("Woori VN");

        assertThat(resolver.resolveBankNm("11704020"))
                .isEqualTo("Woori VN")
                .isNotEqualTo("Woori(Vietnam)");
    }

    /** Case 3 — Vietcombank: "Vietcom", not "Vietcom Bank". */
    @Test
    void vietcombank_displaysShortName() {
        when(legacyFunctions.resolveBankNmShort("11704004")).thenReturn("Vietcom");

        assertThat(resolver.resolveBankNm("11704004"))
                .isEqualTo("Vietcom")
                .isNotEqualTo("Vietcom Bank");
    }

    /** Case 4 — short name missing ⇒ fall back to the formal RPRS_BANK_NM. */
    @Test
    void missingShortName_fallsBackToFormalName() {
        when(legacyFunctions.resolveBankNmShort("11704999")).thenReturn(null);
        BankInfm formal = new BankInfm();
        formal.setBankCd("11704999");
        formal.setRprsBankNm("Some Bank (Vietnam)");
        when(bankInfmRepo.findById("11704999")).thenReturn(Optional.of(formal));

        assertThat(resolver.resolveBankNm("11704999")).isEqualTo("Some Bank (Vietnam)");
    }

    /** Blank short name is treated as missing and also falls back to formal. */
    @Test
    void blankShortName_fallsBackToFormalName() {
        when(legacyFunctions.resolveBankNmShort("11704998")).thenReturn("   ");
        BankInfm formal = new BankInfm();
        formal.setBankCd("11704998");
        formal.setRprsBankNm("Formal Only");
        when(bankInfmRepo.findById("11704998")).thenReturn(Optional.of(formal));

        assertThat(resolver.resolveBankNm("11704998")).isEqualTo("Formal Only");
    }

    /** Unknown code (no short, no registry row) ⇒ raw BANK_CD, not cached. */
    @Test
    void unknownCode_fallsBackToRawBankCd() {
        when(legacyFunctions.resolveBankNmShort("00000000")).thenReturn(null);
        when(bankInfmRepo.findById("00000000")).thenReturn(Optional.empty());

        assertThat(resolver.resolveBankNm("00000000")).isEqualTo("00000000");
    }

    /**
     * Case 5 — no hardcoded / static mapping: an arbitrary code returns
     * verbatim whatever the DB funnel resolves (proving the value is
     * DB-sourced, not from any in-code bank map), and a null code is empty.
     */
    @Test
    void noHardcodedMapping_valueComesFromFunnelVerbatim() {
        when(legacyFunctions.resolveBankNmShort("12345678")).thenReturn("Arbitrary Short");

        assertThat(resolver.resolveBankNm("12345678")).isEqualTo("Arbitrary Short");
        assertThat(resolver.resolveBankNm(null)).isEqualTo("");
        verifyNoInteractions(bankInfmRepo); // happy path never touches RPRS_BANK_NM
    }

    /** Resolved names are memoised — a second lookup does not re-hit the funnel. */
    @Test
    void resolvedName_isCached() {
        when(legacyFunctions.resolveBankNmShort("11704088")).thenReturn("Shinhan VN");

        assertThat(resolver.resolveBankNm("11704088")).isEqualTo("Shinhan VN");
        assertThat(resolver.resolveBankNm("11704088")).isEqualTo("Shinhan VN");
        verify(legacyFunctions, times(1)).resolveBankNmShort("11704088");
    }
}
