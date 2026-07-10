package com.kosign.wecms.domain.payment.internaltransfer.service;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Immutable in-memory snapshot of the cut-off configuration, built by
 * {@link CutoffConfigProvider} <b>entirely from the database</b>
 * ({@code TB_CUTOFF_CONFIG} + {@code TB_CUTOFF_PARAM}).
 *
 * <p><b>DB-only architecture (2026-07-09).</b> There is no {@code application.yml}
 * fallback: the database is the single source of truth. The provider validates
 * completeness at startup ({@link CutoffConfigProvider#init()}) and refuses to
 * boot if a required window or the threshold is missing/invalid, so a
 * successfully-started application always holds a complete, non-null snapshot —
 * {@link CutoffValidator} never has to cope with a missing required window or a
 * null threshold. (This record replaced the former {@code CutoffProperties}
 * {@code @ConfigurationProperties} bean when the YAML fallback was removed.)
 *
 * <p>Nested {@code NetworkWindows} for a rail a bank genuinely does not offer
 * (e.g. VCB SWIFT) may be {@code null} — that is an intentional "no such rail"
 * (legacy parity: no config → allow), NOT a configuration gap. The set of
 * <i>required</i> rails is enforced at startup (see {@code CutoffConfigProvider}).
 */
public record CutoffConfigSnapshot(
        BankWindows shinhan,
        BankWindows woori,
        BankWindows vcb,
        /** VCB CITAD ≥ this amount (VND) bumps the cut-off to the large tier.
         *  Guaranteed non-null once the app has started (validated at boot). */
        BigDecimal largeTxnThreshold) {

    /**
     * Per-bank window set. {@code citadApi} is the CITAD-via-API window
     * (Shinhan only); {@code citadLarge} is the VCB ≥500M tier (VCB only).
     * A slot is {@code null} only for a rail the bank does not offer.
     */
    public record BankWindows(
            NetworkWindows napas,
            NetworkWindows citad,
            NetworkWindows citadApi,
            NetworkWindows swift,
            NetworkWindows citadLarge) { }

    /**
     * A single {@code (start, end)} banking window. {@code warning} is the
     * frontend soft-confirm zone (surfaced to the FE only; the backend gate
     * uses start/end); it may be {@code null}.
     */
    public record NetworkWindows(LocalTime start, LocalTime end, LocalTime warning) { }
}
