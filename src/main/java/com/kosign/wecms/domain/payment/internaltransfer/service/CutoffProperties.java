package com.kosign.wecms.domain.payment.internaltransfer.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;
import java.util.Map;

/**
 * Bank transfer cut-off-time table. Mirrors legacy
 * {@code PymnHelper.getCutoffConfig} but lives in
 * {@code application*.yml} rather than hard-coded constants — see
 * OQ-6 in {@code docs/refactor-wecms/features/internal-transfer.md}.
 *
 * <p>YAML shape:
 * <pre>{@code
 * wecms:
 *   internal-transfer:
 *     cutoff:
 *       shinhan:
 *         napas: { start: "00:05", end: "23:45" }
 *         citad: { start: "09:00", end: "15:30" }
 *         swift: { start: "09:00", end: "22:00" }
 *       woori:
 *         napas: { start: "00:15", end: "23:55" }
 *         citad: { start: "08:30", end: "17:40" }
 *         swift: { start: "08:30", end: "19:10" }
 *       shinhan-bank-cd: "88886000"
 *       woori-bank-cd:   "11704000"
 * }</pre>
 *
 * <p>Unknown {@code (bank, network)} pairs return an empty
 * {@link Window} → cut-off validation silently passes. This mirrors
 * legacy {@code getCutoffConfig} returning {@code null} for non-Woori,
 * non-Shinhan banks.
 */
@ConfigurationProperties(prefix = "wecms.internal-transfer.cutoff")
public record CutoffProperties(
        /** Bank codes for the two banks the cut-off table currently
         *  covers. Sourced from {@code application-local.yml}. */
        String shinhanBankCd,
        String wooriBankCd,
        /** Per-bank windows keyed by network name (lowercase):
         *  {@code napas} / {@code citad} / {@code swift}. */
        Map<String, NetworkWindows> shinhan,
        Map<String, NetworkWindows> woori) {

    public record NetworkWindows(LocalTime start, LocalTime end) { }

    /** A no-window stub for unknown (bank, network) pairs. */
    public static final NetworkWindows OPEN_ALL_DAY =
            new NetworkWindows(LocalTime.MIN, LocalTime.MAX);
}
