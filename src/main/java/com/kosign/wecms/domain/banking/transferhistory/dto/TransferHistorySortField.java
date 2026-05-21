package com.kosign.wecms.domain.banking.transferhistory.dto;

import com.kosign.wecms.common.web.SortableField;

import java.util.Optional;

/**
 * Sort whitelist for {@code GET /api/v1/banking/internal-transfer/transfer-history}.
 *
 * <p>Mirrors the {@code data-sort} keys declared on the legacy view JSP
 * at {@code WECMS DEV/WECMS/web/view/jex/wabooks/trns/trns_0003_001_view.jsp:168-179}
 * — Transaction Date, Sender Bank, Recipient Bank, Amount, Remarks. The
 * legacy {@code SortHelper.getSortDynamic(util, "trns_0003_001_r001")}
 * surface is a JEX framework attribute; the refactor surfaces the
 * five fields the frontend cares about today.
 *
 * <p>{@code OUT_BANK_NM} and {@code IN_BANK_NM} are projected
 * server-side (resolved from {@code BANK_INFM} / the legacy
 * {@code wb_get_napas_bank_nm_or_citad_bank_nm} funnel) — sorting these
 * client-visible labels is implemented by sorting on the underlying
 * code columns ({@code outBankCode} / {@code inBankCode}). The wire-
 * facing alphabetical order on the resolved label is a known divergence
 * from the legacy behaviour that sorted by code too (the legacy IDO's
 * column aliases meant the {@code data-sort="OUT_BANK_NM"} actually
 * sorted by {@code OUT_BANK_CODE} as well).
 *
 * <p>Default in the service is {@link #TRNS_DT} DESC tie-broken on
 * {@code seqNo DESC} (deterministic for rows that share a timestamp).
 */
public enum TransferHistorySortField implements SortableField {

    TRNS_DT("regDtm"),
    OUT_BANK_NM("outBankCode"),
    IN_BANK_NM("inBankCode"),
    TRSC_AMT("trscAmt"),
    RMRK("rmrk");

    private final String entityProperty;

    TransferHistorySortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    @Override
    public String entityProperty() {
        return entityProperty;
    }

    /**
     * Lenient parse — accepts case-insensitive enum names; unknown /
     * blank values return {@link Optional#empty()} so the service falls
     * back to the default ORDER BY.
     */
    public static Optional<TransferHistorySortField> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(valueOf(trimmed.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
