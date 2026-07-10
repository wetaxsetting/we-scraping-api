package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.setting.firmbankingsettings.entity.TbFirmBankCode;
import com.kosign.wecms.domain.setting.firmbankingsettings.repository.TbFirmBankCodeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CutoffValidator}. Pins the cut-off table at a
 * known shape and exercises the weekend / start / end branches, the
 * Shinhan past-cutoff exemption + CITAD VND→VND hard-block, the
 * CITAD-via-API (16:30) split, and the VCB ≥500M tier (16:30). Bank
 * identity is resolved from {@code TB_FIRM_BANK_CODE.ENG_ABRVAT_NM}
 * (mocked) keyed on the real firm out-bank codes.
 */
class CutoffValidatorTest {

    private static final String SHINHAN = "970424";
    private static final String WOORI   = "970457";
    private static final String VCB     = "970436";

    /** VCB CITAD ≥500M tier threshold — mirrors the base application.yml value. */
    private static final BigDecimal THRESHOLD = new BigDecimal("500000000");

    private static CutoffConfigSnapshot.NetworkWindows win(int h1, int m1, int h2, int m2) {
        // warning is FE-only (soft-confirm zone) — the backend gate ignores it,
        // so the tests leave it null.
        return new CutoffConfigSnapshot.NetworkWindows(LocalTime.of(h1, m1), LocalTime.of(h2, m2), null);
    }

    /** Stable property fixture — mirrors the base application.yml table. */
    private static final CutoffConfigSnapshot FIXTURE = new CutoffConfigSnapshot(
            // shinhan: napas, citad(File SFTP), citadApi(16:30), swift, citadLarge(n/a)
            new CutoffConfigSnapshot.BankWindows(
                    win(0, 5, 23, 45), win(9, 0, 15, 30), win(9, 0, 16, 30), win(9, 0, 22, 0), null),
            // woori: napas, citad, citadApi(n/a), swift, citadLarge(n/a)
            new CutoffConfigSnapshot.BankWindows(
                    win(0, 15, 23, 55), win(8, 30, 17, 40), null, win(8, 30, 19, 10), null),
            // vcb: napas, citad(base 15:30), citadApi(n/a), swift(n/a), citadLarge(16:30)
            new CutoffConfigSnapshot.BankWindows(
                    win(0, 5, 23, 45), win(8, 0, 15, 30), null, null, win(8, 0, 16, 30)),
            THRESHOLD
    );

    /** Build a validator with a fixed clock and a bank repo that resolves
     *  the three firm codes to their {@code ENG_ABRVAT_NM} brands. */
    private CutoffValidator clockAt(String iso) {
        Clock clock = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
        TbFirmBankCodeRepository repo = mock(TbFirmBankCodeRepository.class);
        when(repo.findFirstByBankCdAndBankStas(SHINHAN, "1")).thenReturn(Optional.of(bank("ShinhanBank")));
        when(repo.findFirstByBankCdAndBankStas(WOORI, "1")).thenReturn(Optional.of(bank("WooriBank")));
        when(repo.findFirstByBankCdAndBankStas(VCB, "1")).thenReturn(Optional.of(bank("Vietcombank")));
        // Any other code resolves to Optional.empty() (Mockito default) → unknown → pass.
        // The validator reads the effective config through CutoffConfigProvider;
        // mock it to return the fixture snapshot so the validator's decision logic
        // is exercised in isolation (the provider's DB load + fail-fast validation
        // is covered by CutoffConfigProviderTest).
        CutoffConfigProvider provider = mock(CutoffConfigProvider.class);
        when(provider.current()).thenReturn(FIXTURE);
        return new CutoffValidator(provider, clock, repo);
    }

    private static TbFirmBankCode bank(String engAbrvatNm) {
        TbFirmBankCode b = new TbFirmBankCode();
        b.setEngAbrvatNm(engAbrvatNm);
        return b;
    }

    @Test
    void should_allow_when_within_woori_citad_window() {
        // 2026-05-15 (Fri) 10:00 UTC == 17:00 Vietnam — inside Woori CITAD (08:30-17:40).
        CutoffValidator v = clockAt("2026-05-15T10:00:00Z");
        assertThatCode(() -> v.validate(WOORI, TransferNetwork.CITAD))
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_with_WBE2068_when_weekend_non_napas() {
        // 2026-05-16 (Sat) 03:00 UTC == 10:00 Vietnam — weekend, CITAD.
        CutoffValidator v = clockAt("2026-05-16T03:00:00Z");
        assertThatThrownBy(() -> v.validate(WOORI, TransferNetwork.CITAD))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2068");
    }

    @Test
    void should_allow_weekend_napas() {
        // 2026-05-16 (Sat) 03:00 UTC == 10:00 Vietnam — NAPAS runs weekends.
        CutoffValidator v = clockAt("2026-05-16T03:00:00Z");
        assertThatCode(() -> v.validate(WOORI, TransferNetwork.NAPAS))
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_with_WBE2067_before_start_time() {
        // 2026-05-15 (Fri) 01:00 UTC == 08:00 Vietnam — before Woori CITAD start (08:30).
        CutoffValidator v = clockAt("2026-05-15T01:00:00Z");
        assertThatThrownBy(() -> v.validate(WOORI, TransferNetwork.CITAD))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2067");
    }

    @Test
    void should_reject_with_WBE2066_after_cutoff_for_woori() {
        // 2026-05-15 (Fri) 11:00 UTC == 18:00 Vietnam — past Woori CITAD end (17:40).
        CutoffValidator v = clockAt("2026-05-15T11:00:00Z");
        assertThatThrownBy(() -> v.validate(WOORI, TransferNetwork.CITAD))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2066");
    }

    @Test
    void should_log_warn_and_allow_when_past_cutoff_for_shinhan_non_vnd_to_vnd() {
        // 2026-05-15 (Fri) 09:00 UTC == 16:00 Vietnam — past Shinhan CITAD File end (15:30).
        // Legacy parity: Shinhan past-cutoff is logged, NOT blocked (unless CITAD VND→VND).
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatCode(() -> v.validate(SHINHAN, TransferNetwork.CITAD))
                .doesNotThrowAnyException();
    }

    @Test
    void should_allow_shinhan_citad_vnd_to_vnd_file_route_between_1530_and_1630() {
        // 16:00 Vietnam — Shinhan CITAD VND→VND on the FILE route (apiTransport=false,
        // e.g. Payroll). Legacy parity: VND→VND uses the 16:30 cut-off regardless of
        // transport (PymnHelper.java:3257-3263), so 16:00 is still inside the window.
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatCode(() -> v.validate(
                new CutoffCheck(SHINHAN, TransferNetwork.CITAD, false, true, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void should_block_shinhan_citad_vnd_to_vnd_after_1630_with_1630_time() {
        // 17:00 Vietnam — past the 16:30 VND→VND cut-off; hard-blocks with "16:30",
        // on the FILE route (apiTransport=false) too. Legacy WBW1168 → refactor WBE2066.
        CutoffValidator v = clockAt("2026-05-15T10:00:00Z");
        assertThatThrownBy(() -> v.validate(
                new CutoffCheck(SHINHAN, TransferNetwork.CITAD, false, true, false)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("WBE2066");
                    assertThat(be.getArgs()[0]).isEqualTo("16:30");
                });
    }

    @Test
    void should_allow_shinhan_citad_api_between_1530_and_1630() {
        // 16:00 Vietnam — Shinhan CITAD VND→VND on the API route (apiTransport=true):
        // within the 16:30 window. (apiTransport no longer drives the window — vndToVnd does —
        // but this still confirms the API path allows.)
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatCode(() -> v.validate(
                new CutoffCheck(SHINHAN, TransferNetwork.CITAD, true, true, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void should_log_warn_and_allow_shinhan_citad_non_vnd_to_vnd_file_past_cutoff() {
        // 16:00 Vietnam — Shinhan CITAD NON-VND→VND (e.g. USD, File route): base 15:30
        // window, but Shinhan is exempt past cut-off → logged + allowed (legacy parity).
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatCode(() -> v.validate(
                new CutoffCheck(SHINHAN, TransferNetwork.CITAD, false, false, false)))
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_vcb_citad_base_after_1530() {
        // 16:00 Vietnam — past VCB CITAD base end (15:30); no large txn → blocks (VCB not exempt).
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatThrownBy(() -> v.validate(
                new CutoffCheck(VCB, TransferNetwork.CITAD, false, false, false)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2066");
    }

    @Test
    void should_allow_vcb_citad_large_tier_until_1630() {
        // 16:00 Vietnam — a ≥500M txn bumps VCB CITAD to 16:30, so 16:00 is allowed.
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatCode(() -> v.validate(
                new CutoffCheck(VCB, TransferNetwork.CITAD, false, false, true)))
                .doesNotThrowAnyException();
    }

    @Test
    void should_bypass_for_unknown_bank() {
        // Unknown bank → ENG_ABRVAT_NM lookup empty → pass (legacy getCutoffConfig null).
        CutoffValidator v = clockAt("2026-05-15T10:00:00Z");
        assertThatCode(() -> v.validate("99999999", TransferNetwork.CITAD))
                .doesNotThrowAnyException();
    }

    @Test
    void TransferNetwork_fromAntDivCd_should_default_to_citad_for_unknown() {
        assertThat(TransferNetwork.fromAntDivCd(null)).isEqualTo(TransferNetwork.CITAD);
        assertThat(TransferNetwork.fromAntDivCd("")).isEqualTo(TransferNetwork.CITAD);
        assertThat(TransferNetwork.fromAntDivCd("XYZ")).isEqualTo(TransferNetwork.CITAD);
    }

    // ─────────── VCB Internal Transfer 24/7 bypass (legacy PymnHelper:3225-3233) ───────────

    @Test
    void isInternalTransferCutoffExempt_should_be_true_only_for_vcb_source() {
        // Time is irrelevant to the exemption check — it resolves the brand only.
        CutoffValidator v = clockAt("2026-05-16T03:00:00Z"); // Sat, would normally block CITAD
        assertThat(v.isInternalTransferCutoffExempt(VCB)).isTrue();
        assertThat(v.isInternalTransferCutoffExempt(SHINHAN)).isFalse();
        assertThat(v.isInternalTransferCutoffExempt(WOORI)).isFalse();
        assertThat(v.isInternalTransferCutoffExempt("99999999")).isFalse(); // unknown bank
        assertThat(v.isInternalTransferCutoffExempt(null)).isFalse();
    }

    @Test
    void vcb_still_blocks_past_cutoff_for_non_internal_transfer_callers() {
        // Guard: the VCB 24/7 exemption is ONLY the internal-transfer path
        // (isInternalTransferCutoffExempt). The normal validate() path must
        // still block VCB past its CITAD cut-off — so Vendor/Expense/Payroll,
        // which call validate() and never the exempt method, are unaffected.
        // 16:00 Vietnam (09:00Z), no large txn → past VCB base 15:30 → WBE2066.
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatThrownBy(() -> v.validate(
                new CutoffCheck(VCB, TransferNetwork.CITAD, false, false, false)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2066");
    }

    // ─────────── threshold comes from config, not a constant ───────────

    @Test
    void largeTxnThreshold_should_come_from_config() {
        CutoffValidator v = clockAt("2026-05-15T10:00:00Z");
        assertThat(v.largeTxnThreshold()).isEqualByComparingTo(THRESHOLD);
    }

    @Test
    void TransferNetwork_fromAntDivCd_should_map_canonical_codes() {
        assertThat(TransferNetwork.fromAntDivCd("N")).isEqualTo(TransferNetwork.NAPAS);
        assertThat(TransferNetwork.fromAntDivCd("NAPAS")).isEqualTo(TransferNetwork.NAPAS);
        assertThat(TransferNetwork.fromAntDivCd("INTER")).isEqualTo(TransferNetwork.NAPAS);
        assertThat(TransferNetwork.fromAntDivCd("SWIFT")).isEqualTo(TransferNetwork.SWIFT);
        assertThat(TransferNetwork.fromAntDivCd("S")).isEqualTo(TransferNetwork.SWIFT);
        assertThat(TransferNetwork.fromAntDivCd("CITAD")).isEqualTo(TransferNetwork.CITAD);
        assertThat(TransferNetwork.fromAntDivCd("C")).isEqualTo(TransferNetwork.CITAD);
    }
}
