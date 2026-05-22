package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * One option in the Purpose picker (legacy {@code prch_0011_001}'s
 * USAG combo). {@code use_usag_cd} is the stable id stored on
 * {@code BUSA_DOC_MSTR.BUSA_USAG_CD}; {@code usag_nm} is the
 * already-localized display name resolved server-side from
 * {@code USAG_INFM_LANG_DETL} per the caller's {@code Accept-Language}.
 */
public record PurposeOption(String useUsagCd, String usagNm) {
}
