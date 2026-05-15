package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for
 * {@code POST /api/v1/settings/contacts/by-codes}.
 *
 * <p>Replaces the former {@code GET ?codes=…} query-string design,
 * which hit browser/server URL-length limits when an Excel upload
 * contained many beneficiary codes.
 *
 * <p>{@code @Size(max = 500)} matches the overseas-payment Excel
 * upload row limit ({@code OverseasPaymentExcelService#MAX_ROWS}).
 */
public record ContactsByCodesRequest(
        @NotEmpty
        @Size(max = 500)
        List<String> codes) { }
