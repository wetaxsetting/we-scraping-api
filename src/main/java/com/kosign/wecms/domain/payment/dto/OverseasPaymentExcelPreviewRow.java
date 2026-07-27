package com.kosign.wecms.domain.payment.dto;

import java.util.List;

/**
 * One parsed row from an overseas payment Excel upload.
 *
 * <p>Wire field names follow the global lowercase snake_case strategy
 * (Jackson {@code SNAKE_CASE} ObjectMapper).
 *
 * <p>{@code row_index} is 1-based (matches what the user sees in Excel;
 * header is row 1, first data row is row 2).
 *
 * <p>Beneficiary detail fields ({@code bnfc_nm}, {@code bic_code},
 * {@code bnfc_acct_no}, {@code bnfc_addr}, {@code bnfc_bank_nm},
 * {@code bnfc_bank_cd}) are now resolved <b>server-side</b> from the
 * {@code TRCO_INFM} + {@code TRCO_OVRS_INFM} master tables using
 * {@code beneficiary_code} as the lookup key — mirrors legacy
 * {@code pymn_0007_003_r001_act.jsp#getOverseasContactInfo} + the
 * {@code getOverseasContactDetails} cross-fill at lines 125-239. They
 * are {@code null} when the beneficiary code is missing / unknown /
 * blank in the master row; a corresponding entry lands in
 * {@link #errors} so the preview can highlight the row.
 */
public record OverseasPaymentExcelPreviewRow(
        /** 1-based Excel row number. */
        int rowIndex,
        /** Transfer currency code (e.g. {@code "USD"}). */
        String currency,
        /** Transfer amount as raw string from the cell. */
        String amount,
        /** Fee payer code (e.g. {@code "BEN"}, {@code "OUR"}, {@code "SHA"}). */
        String feePayer,
        /** Beneficiary code — used to resolve contact details server-side. */
        String beneficiaryCode,
        /** Memo / payment purpose. */
        String memo,
        /** Reporting account code (optional — may be blank). */
        String reportingAccount,
        /** Beneficiary name (cross-filled from {@code TRCO_OVRS_INFM.BENE_NAME}). */
        String bnfcNm,
        /** SWIFT BIC code (cross-filled from {@code TRCO_OVRS_INFM.BIC_CODE}). */
        String bicCode,
        /** Beneficiary account number (cross-filled from {@code TRCO_OVRS_INFM.BENE_ACCT_NO}). */
        String bnfcAcctNo,
        /** Beneficiary address (cross-filled from {@code TRCO_OVRS_INFM.BENE_ADDR}). */
        String bnfcAddr,
        /** Beneficiary bank name (cross-filled from {@code TRCO_OVRS_INFM.BENE_BANK_NAME}). */
        String bnfcBankNm,
        /** Beneficiary bank code (cross-filled from {@code TRCO_OVRS_INFM.BENE_BANK_CODE}). */
        String bnfcBankCd,
        /** Beneficiary town/city (cross-filled from {@code TRCO_OVRS_INFM.BENE_TOWN_NM},
         *  itself auto-filled from the SWIFT directory). Required on upload
         *  (legacy {@code pymn_0007_003_r001_act.jsp:216-238}). */
        String bnfcTownNm,
        /** {@code true} when {@link #errors} is empty. */
        boolean valid,
        /** Per-field validation errors; empty for valid rows. */
        List<OverseasPaymentExcelFieldError> errors
) { }
