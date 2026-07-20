package com.kosign.wecms.domain.einvoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for {@code POST /api/v1/addon/einvoices/scrape-detail-results}.
 *
 * <p>Refactor port of the {@code nxisas_einvoice_c002} save action
 * ({@code nxisas_einvoice_c002_act.jsp}). Phase 1 ({@code INVOICE_LIST})
 * persists the master rows; this phase-2 call carries the decrypted
 * {@code INVOICE_DETAIL} payloads — the service enriches the existing
 * {@code BUSA_DOC_MSTR} rows and (re)writes their line items / fees /
 * taxes.
 *
 * <p>Wire shape lowercase snake_case via the global Jackson strategy.
 */
public record EInvoiceDetailScrapeRequest(

        @NotBlank
        @Pattern(regexp = "SALES|EXPENSE|BILL", message = "type must be SALES, EXPENSE or BILL")
        String type,

        @NotBlank @Pattern(regexp = "\\d{8}", message = "from must be YYYYMMDD")
        String fromDt,

        @NotBlank @Pattern(regexp = "\\d{8}", message = "to must be YYYYMMDD")
        String toDt,

        /** Engine {@code ErrorCode} for the detail call ({@code 00000000}
         *  = success). Informational — phase 1 already wrote the audit row. */
        @NotBlank @Size(max = 10)
        String rsltCd,

        @Size(max = 1000)
        String rsltMsg,

        /**
         * Phase-1 batch timestamp (the {@code mod_dtm} returned by
         * {@code /scrape-results}). Optional — when present (14-digit
         * {@code yyyyMMddHHmmss}) the detail save stamps it onto
         * {@code BUSA_DOC_MSTR.MOD_DTM} instead of minting a fresh one, so
         * the phase-1 audit {@code REG_DTM}, the returned batch id, and the
         * enriched master rows all share ONE batch timestamp. That is what
         * lets the modal "View" (and the History → Detail drill-down) reliably
         * select the exact rows this inquiry produced. Blank / omitted →
         * legacy behaviour (fresh timestamp). {@code @Pattern} skips null.
         */
        @Pattern(regexp = "\\d{14}", message = "mod_dtm must be yyyyMMddHHmmss")
        String modDtm,

        @NotNull @Valid
        List<ScrapedEInvoiceDetailRow> details
) { }
