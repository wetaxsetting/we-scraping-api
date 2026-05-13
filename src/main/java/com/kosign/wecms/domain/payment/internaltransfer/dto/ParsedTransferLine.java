package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;

/**
 * One parsed line from {@code POST .../uploads/parse}. Each cell
 * carries the value the workbook parser extracted; {@code errFld}
 * (comma-separated FIELD_IDs) + {@code errFldCaus} (cause string)
 * flag per-cell validation failures so the frontend can paint the
 * preview's red highlights.
 *
 * <p>Mirrors legacy {@code WSVC.pymn_0009_002_r001.xml:rule_Sub_0}.
 * Phase A always ships this empty — see
 * {@link ParseExcelResponse} Javadoc.
 */
public record ParsedTransferLine(
        BigDecimal tranAmt,
        String     antDivCd,
        String     rcptNm,
        String     tarBankCd,
        String     tarBankNm,
        String     fincMdaNo,
        String     tranUsag,
        String     errFld,
        String     errFldCaus,
        String     trcoKey,
        String     handTrcoKey,
        String     docNo,
        String     note,
        String     citadBankCd,
        String     trcoCmnm,
        String     trcoCmenm,
        String     chartAcctSeqNo,
        String     chartAcctCd,
        String     trnfCcyCd,
        String     depoCcyCd) { }
