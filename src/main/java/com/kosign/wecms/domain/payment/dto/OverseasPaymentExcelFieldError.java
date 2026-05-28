package com.kosign.wecms.domain.payment.dto;

/**
 * One per-field validation error on an overseas payment Excel preview row.
 *
 * <p>{@code field} matches the frontend {@code col.id} on the shared
 * {@code <ExcelUploadDialog>} so the cell can be highlighted verbatim.
 * Excel-cell values: {@code "currency"}, {@code "amount"},
 * {@code "feePayer"}, {@code "beneficiaryCode"}, {@code "memo"}.
 * Resolved-from-master values: {@code "bnfcNm"}, {@code "bicCode"},
 * {@code "bnfcAcctNo"}, {@code "bnfcAddr"}, {@code "bnfcBankNm"},
 * {@code "bnfcBankCd"} — emitted when the beneficiary code does not
 * resolve to an overseas contact OR the contact's master row carries
 * a blank value for that field (mirrors legacy {@code WBE1189} +
 * per-field {@code WBW1159} from
 * {@code pymn_0007_003_r001_act.jsp:142-148, 218-232}).
 *
 * <p>{@code message} is a short human-readable description of the
 * validation failure.
 */
public record OverseasPaymentExcelFieldError(
        /** Frontend col.id — camelCase, matches the preview table column key. */
        String field,
        /** Short validation failure description. */
        String message
) { }
