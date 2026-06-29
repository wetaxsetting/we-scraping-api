package com.kosign.wecms.domain.payment.internaltransfer.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalTime;

/**
 * Bank transfer cut-off-time table. Mirrors legacy
 * {@code PymnHelper.getCutoffConfig} but lives in
 * {@code application*.yml} rather than hard-coded constants.
 *
 * <p>The bank a window belongs to is resolved at runtime from
 * {@code TB_FIRM_BANK_CODE.ENG_ABRVAT_NM} ({@code ShinhanBank} /
 * {@code WooriBank} / {@code Vietcombank}) — see
 * {@link CutoffValidator#resolveBank}. The cut-off table is therefore
 * keyed by brand, NOT by a hard-coded {@code OUT_BANK_CD}. (The earlier
 * {@code shinhan-bank-cd} / {@code woori-bank-cd} fields sourced from
 * {@code WECMS_*_BANK_CD} were removed 2026-06-29 — those env values
 * (88886000 / 11704000) never matched the real firm out-bank codes
 * (970424 / 970457 / 970436), so the gate silently passed every transfer.)
 *
 * <p>YAML shape (kebab-case keys bind to the camelCase record components):
 * <pre>{@code
 * wecms:
 *   internal-transfer:
 *     cutoff:
 *       shinhan:
 *         napas:      { start: "00:05", end: "23:45" }
 *         citad:      { start: "09:00", end: "15:30" }   # File SFTP
 *         citad-api:  { start: "09:00", end: "16:30" }   # CITAD via API (Shinhan Bank confirmation 17-Jun-2026)
 *         swift:      { start: "09:00", end: "22:00" }
 *       woori:
 *         napas:      { start: "00:15", end: "23:55" }
 *         citad:      { start: "08:30", end: "17:40" }
 *         swift:      { start: "08:30", end: "19:10" }
 *       vcb:
 *         napas:       { start: "00:05", end: "23:45" }
 *         citad:       { start: "08:00", end: "15:30" }  # base (< 500M)
 *         citad-large: { start: "08:00", end: "16:30" }  # tier when any txn >= 500,000,000 VND
 * }</pre>
 *
 * <p>A {@code null} {@link BankWindows} for a bran­d, or a {@code null}
 * {@link NetworkWindows} for a (bank, network) pair, means the cut-off
 * check silently passes — mirrors legacy {@code getCutoffConfig}
 * returning {@code null}.
 */
@ConfigurationProperties(prefix = "wecms.internal-transfer.cutoff")
public record CutoffProperties(
        BankWindows shinhan,
        BankWindows woori,
        BankWindows vcb) {

    /**
     * Per-bank window set. {@code citadApi} is the CITAD-via-API window
     * (Shinhan only — 16:30); {@code citadLarge} is the VCB ≥500M tier
     * (VCB only — 16:30). Both may be {@code null} for banks that do not
     * carry that variant; callers fall back to {@code citad}.
     */
    public record BankWindows(
            NetworkWindows napas,
            NetworkWindows citad,
            NetworkWindows citadApi,
            NetworkWindows swift,
            NetworkWindows citadLarge) { }

    public record NetworkWindows(LocalTime start, LocalTime end) { }
}
