package com.kosign.wecms.domain.setting.contacts.dto;

import java.util.List;

/**
 * Response body for {@code POST /api/v1/settings/contacts/excel/preflight}.
 * One {@link Result} per request row, in the same index order. A
 * {@code true} flag means the row will fail at upload time with the
 * matching {@code WBE_CONTACTS_HAND_KEY_DUP} / {@code WBE_CONTACTS_TAX_ID_DUP}
 * code &mdash; the frontend tints the matching preview cell red so the
 * user fixes it before clicking Upload.
 */
public record ContactsExcelPreflightResponse(
        List<Result> results) {

    public record Result(
            int rowIndex,
            boolean handTrcoKeyExists,
            boolean trcoTaxIdExists) { }
}
