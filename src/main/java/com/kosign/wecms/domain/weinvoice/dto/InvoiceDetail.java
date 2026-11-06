package com.kosign.wecms.domain.weinvoice.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Screen 05 — every field of the UI Spec {@code Invoice} plus what the detail panes show
 * (tax per rate, fees, signing times, task state). Values come from the XML when the engine
 * has it, otherwise from the GDT list row.
 */
public record InvoiceDetail(
        String id,
        String companyMst,
        String direction,
        String issueDate,
        String templateNo,
        String symbol,
        String number,
        String invoiceTitle,
        Party seller,
        Party buyer,
        String currency,
        BigDecimal exchangeRate,
        BigDecimal preTax,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal totalVnd,
        String totalInWords,
        String paymentMethod,
        String status,
        String relatedId,
        Related related,
        String kind,
        String cqtCode,
        String provider,
        String providerTaxId,
        boolean providerKnown,
        Lookup lookup,
        List<Item> items,
        List<TaxLine> taxes,
        List<Fee> fees,
        Signatures signatures,
        List<InvoiceFileItem> files,
        List<String> tagIds,
        TaskState xmlTask,
        TaskState pdfTask,
        List<String> warnings,
        String source,
        String syncedAt) {

    public record Party(String taxId, String name, String address, String phone, String email,
                        String bankAccount, String bankName, String citizenId) {
    }

    /**
     * @param vatRate      UI Spec number (0 / 5 / 8 / 10, -1 KCT, -2 KKKNT); {@code null} when unreadable
     * @param vatRateLabel rate as written in the XML
     * @param lineKind     1 goods / service, 2 promotion, 3 discount, 4 note, 5 special (QĐ 1233 {@code TChat})
     */
    public record Item(int lineNo, Integer lineKind, String code, String name, String unit, BigDecimal qty,
                       BigDecimal price, BigDecimal discount, BigDecimal amount, BigDecimal vatRate,
                       String vatRateLabel, BigDecimal taxAmount) {
    }

    public record TaxLine(String vatRateLabel, BigDecimal vatRate, BigDecimal preTax, BigDecimal tax) {
    }

    public record Fee(String name, BigDecimal amount) {
    }

    /** Signing times as written in the XML ({@code SigningTime}). */
    public record Signatures(String seller, String buyer, String cqt) {
    }

    /**
     * Related invoice named in the XML ({@code TTHDLQuan}) or the GDT list.
     *
     * @param relation {@code replaces} / {@code adjusts} (this invoice points at it) or
     *                 {@code replaced_by} / {@code adjusted_by} (it points at this invoice)
     */
    public record Related(String relation, String id, String templateNo, String symbol, String number,
                          String issueDate, String reason) {
    }

    /**
     * How to open the original (UI Spec 3.4).
     *
     * @param code        lookup code — typed by a user, else read from the XML
     * @param companyCode second parameter some providers need ("Mã công ty")
     * @param url         link to open: the invoice's own link when known, else the provider lookup page
     * @param direct      {@code url} opens this invoice directly
     * @param manual      {@code code} / {@code url} were typed by a user
     * @param needsCode   the provider is known but no code / link is available ("!" pill)
     */
    public record Lookup(String code, String companyCode, String url, boolean direct, boolean manual,
                         boolean needsCode) {
    }

    /** Engine task of the invoice ({@code tasks}): {@code pending | running | done | dead | no_source}. */
    public record TaskState(String status, int attempts, String errorKind, String error, String nextRunAt,
                            String updatedAt) {
    }
}
