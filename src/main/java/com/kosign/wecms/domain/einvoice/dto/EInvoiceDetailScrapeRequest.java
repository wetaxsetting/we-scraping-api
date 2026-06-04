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

        @NotNull @Valid
        List<ScrapedEInvoiceDetailRow> details
) { }
