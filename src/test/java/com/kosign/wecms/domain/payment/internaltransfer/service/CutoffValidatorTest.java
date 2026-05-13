package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link CutoffValidator}. Pins the cut-off table at a
 * known shape and exercises the weekend / start / end branches plus
 * the Shinhan past-cutoff exemption.
 */
class CutoffValidatorTest {

    /** Stable property fixture — Shinhan + Woori windows. Matches the
     *  values seeded in {@code application-local.yml}. */
    private static final CutoffProperties FIXTURE = new CutoffProperties(
            "88886000",
            "11704000",
            Map.of(
                    "napas", new CutoffProperties.NetworkWindows(LocalTime.of(0, 5),   LocalTime.of(23, 45)),
                    "citad", new CutoffProperties.NetworkWindows(LocalTime.of(9, 0),   LocalTime.of(15, 30)),
                    "swift", new CutoffProperties.NetworkWindows(LocalTime.of(9, 0),   LocalTime.of(22, 0))
            ),
            Map.of(
                    "napas", new CutoffProperties.NetworkWindows(LocalTime.of(0, 15),  LocalTime.of(23, 55)),
                    "citad", new CutoffProperties.NetworkWindows(LocalTime.of(8, 30),  LocalTime.of(17, 40)),
                    "swift", new CutoffProperties.NetworkWindows(LocalTime.of(8, 30),  LocalTime.of(19, 10))
            )
    );

    /** Friday 2026-05-15 10:00 UTC == 17:00 Vietnam — inside Woori
     *  CITAD window (08:30-17:40), outside Shinhan CITAD (09:00-15:30).
     *  Used as the "happy-path" baseline. */
    private CutoffValidator clockAt(String iso) {
        Clock clock = Clock.fixed(Instant.parse(iso), ZoneOffset.UTC);
        return new CutoffValidator(FIXTURE, clock);
    }

    @Test
    void should_allow_when_within_woori_citad_window() {
        // 2026-05-15 (Fri) 10:00 UTC == 17:00 Vietnam — inside Woori CITAD (08:30-17:40).
        CutoffValidator v = clockAt("2026-05-15T10:00:00Z");
        assertThatCode(() -> v.validate("11704000", TransferNetwork.CITAD))
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_with_WBE2068_when_weekend_non_napas() {
        // 2026-05-16 (Sat) 03:00 UTC == 10:00 Vietnam — weekend, CITAD.
        CutoffValidator v = clockAt("2026-05-16T03:00:00Z");
        assertThatThrownBy(() -> v.validate("11704000", TransferNetwork.CITAD))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2068");
    }

    @Test
    void should_allow_weekend_napas() {
        // 2026-05-16 (Sat) 03:00 UTC == 10:00 Vietnam — NAPAS runs weekends.
        CutoffValidator v = clockAt("2026-05-16T03:00:00Z");
        assertThatCode(() -> v.validate("11704000", TransferNetwork.NAPAS))
                .doesNotThrowAnyException();
    }

    @Test
    void should_reject_with_WBE2067_before_start_time() {
        // 2026-05-15 (Fri) 01:00 UTC == 08:00 Vietnam — before Woori CITAD start (08:30).
        CutoffValidator v = clockAt("2026-05-15T01:00:00Z");
        assertThatThrownBy(() -> v.validate("11704000", TransferNetwork.CITAD))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2067");
    }

    @Test
    void should_reject_with_WBE2066_after_cutoff_for_woori() {
        // 2026-05-15 (Fri) 11:00 UTC == 18:00 Vietnam — past Woori CITAD end (17:40).
        CutoffValidator v = clockAt("2026-05-15T11:00:00Z");
        assertThatThrownBy(() -> v.validate("11704000", TransferNetwork.CITAD))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("WBE2066");
    }

    @Test
    void should_log_warn_and_allow_when_past_cutoff_for_shinhan() {
        // 2026-05-15 (Fri) 09:00 UTC == 16:00 Vietnam — past Shinhan CITAD end (15:30).
        // Legacy parity: Shinhan past-cutoff is logged, NOT blocked.
        CutoffValidator v = clockAt("2026-05-15T09:00:00Z");
        assertThatCode(() -> v.validate("88886000", TransferNetwork.CITAD))
                .doesNotThrowAnyException();
    }

    @Test
    void should_bypass_for_unknown_bank() {
        // Unknown bank → null window → pass (matches legacy getCutoffConfig returning null).
        // 2026-05-15 (Fri) 10:00 UTC == 17:00 Vietnam — Friday weekday so the
        // weekend block doesn't fire first.
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
