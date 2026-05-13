package com.kosign.wecms.domain.payment.dto;

/**
 * One approver step in an overseas-payment approval chain.
 * Wire names auto-derived via global Jackson SNAKE_CASE strategy.
 * Mirrors WSVC.pymn_0007_001_r001.xml → REC_1 per row.
 */
public record OverseasApprover(
        String apprvrId,
        String apprvrNm,
        String apprvrStts,
        String apprvrLineNo,
        String modDtm,
        String regImgUrl
) {}
