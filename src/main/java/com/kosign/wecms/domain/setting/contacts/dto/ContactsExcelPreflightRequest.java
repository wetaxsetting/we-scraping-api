package com.kosign.wecms.domain.setting.contacts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for {@code POST /api/v1/settings/contacts/excel/preflight}.
 * Carries the parsed Excel rows (just the fields that need a DB
 * existence check &mdash; {@code hand_trco_key} + {@code trco_tax_id})
 * so the frontend can flag duplicate rows in the preview table before
 * the user clicks Upload.
 *
 * <p>Mirrors the row-iteration in legacy
 * {@code comm_0184_007_r001_act.jsp:401-450 / 567-630} which calls
 * {@code getContact()} + {@code getTaxId()} per row and stamps
 * {@code ERR_FLD} / {@code ERR_FLD_CAUS} on the returned record.
 */
public record ContactsExcelPreflightRequest(
        @NotEmpty
        @Size(max = 500, message = "Cannot preflight more than 500 contacts at once.")
        @Valid
        List<Row> rows) {

    public record Row(
            @NotBlank
            @Size(max = 100)
            String handTrcoKey,

            @Size(max = 100)
            String trcoTaxId) { }
}
