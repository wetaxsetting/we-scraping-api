package com.kosign.wecms.domain.banking.nxisas;

import java.util.Set;

/**
 * Supported card-scraping bank scope. Card Transactions (Update scrape +
 * Card Management add-card) supports exactly two issuing banks — Shinhan
 * Vietnam and Woori Vietnam. This is the single source of truth the NXISAS
 * debug logging uses to tag a card flow as in-scope; it does NOT add or
 * enable any other bank.
 *
 * <p>The two codes match the per-bank quirk constants already hard-coded in
 * {@code CardTransactionSyncService} (Woori's missing {@code cardType}
 * discriminator, Shinhan's Vietnamese purchase-status text mapping) — the
 * only banks the card CARD_USING / CARD_INFO paths were ever built for.
 */
public final class NxisasCardBanks {

    private NxisasCardBanks() { }

    /** Shinhan Bank Vietnam. */
    public static final String SHINHAN = "11704088";
    /** Woori Bank Vietnam. */
    public static final String WOORI = "11704020";

    private static final Set<String> SUPPORTED = Set.of(SHINHAN, WOORI);

    /** True only for the two supported card banks (Shinhan / Woori). */
    public static boolean isSupported(String bankCd) {
        return bankCd != null && SUPPORTED.contains(bankCd);
    }

    /** Short label for logs — {@code "Shinhan"} / {@code "Woori"} /
     *  {@code "UNSUPPORTED"}. Never an unsupported bank's real name. */
    public static String label(String bankCd) {
        if (SHINHAN.equals(bankCd)) return "Shinhan";
        if (WOORI.equals(bankCd)) return "Woori";
        return "UNSUPPORTED";
    }
}
