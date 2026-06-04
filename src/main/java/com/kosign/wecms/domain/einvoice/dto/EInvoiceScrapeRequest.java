package com.kosign.wecms.domain.einvoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for {@code POST /api/v1/addon/einvoices/scrape-results}.
 *
 * <p>Refactor port of the {@code nxisas_einvoice_c001} save action
 * ({@code nxisas_einvoice_c001_act.jsp}). The browser drives the local
 * NXiSAS engine ({@code LOGIN} → {@code INVOICE_LIST} → optional
 * {@code INVOICE_DETAIL}), then POSTs the decrypted invoice rows here.
 * The service re-validates ownership / branch rules and persists the
 * accepted rows to {@code BUSA_DOC_MSTR}, plus one
 * {@code EXT_TRAN_CALL_HIS} audit row per call.
 *
 * <p>{@code invoices} may be empty (e.g. a login failure or an empty
 * GDT range) — the audit row is still written so the call history /
 * "last scrape" timestamp stays accurate, mirroring legacy.
 *
 * <p>Wire shape is lowercase snake_case via the global Jackson naming
 * strategy.
 */
public record EInvoiceScrapeRequest(

        /** {@code SALES} → {@code DOC_TYPE 'I'}; {@code EXPENSE} /
         *  {@code BILL} → {@code DOC_TYPE 'B'} — same vocabulary as the
         *  list endpoint ({@code EInvoiceService.resolveDocType}). */
        @NotBlank
        @Pattern(regexp = "SALES|EXPENSE|BILL", message = "type must be SALES, EXPENSE or BILL")
        String type,

        @NotBlank @Pattern(regexp = "\\d{8}", message = "from must be YYYYMMDD")
        String fromDt,

        @NotBlank @Pattern(regexp = "\\d{8}", message = "to must be YYYYMMDD")
        String toDt,

        /** Engine {@code ErrorCode} for the call ({@code 00000000} =
         *  success). Recorded verbatim on the audit row. */
        @NotBlank @Size(max = 10)
        String rsltCd,

        /** Engine {@code ErrorMessage} / error content (audit only). */
        @Size(max = 1000)
        String rsltMsg,

        /** Issuer (own-company) tax code for SALES rows. Optional — the
         *  service resolves it from {@code BUSI_INFM.TAX_ID} for the
         *  tenant and only falls back to this when the company row has
         *  none (legacy uses {@code loginUser.getTaxInfo().getTaxId()}).
         *  Ignored for EXPENSE (always {@code 99999999999999999999}). */
        @Size(max = 40)
        String issuerTaxCode,

        /** Audit metadata — local engine vendor / version (optional). */
        @Size(max = 50) String engineVendor,
        @Size(max = 50) String engineVersion,

        @NotNull @Valid
        List<ScrapedEInvoiceRow> invoices
) { }
