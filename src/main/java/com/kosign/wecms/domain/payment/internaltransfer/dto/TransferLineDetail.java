package com.kosign.wecms.domain.payment.internaltransfer.dto;

import java.math.BigDecimal;

/**
 * One row in the per-line table inside the Scheduled Transfer detail
 * response. Carries the recipient, amount, ERP linkage, and free-text
 * fields users can edit on a draft batch.
 */
public record TransferLineDetail(
        String     scdulPymnDetlNo,
        String     antDivCd,
        String     rcptNm,
        String     tarBankCd,
        String     fincMdaNo,
        String     tranUsag,
        BigDecimal tranAmt,
        String     ccyCd,
        String     trcoKey,
        String     handTrcoKey,
        String     docNo,
        String     note,
        String     citadBankCd,
        String     chartAcctCd,
        String     chartAcctSeqNo,
        String     depoCcyCd,
        String     bankTrnsShortId) { }
