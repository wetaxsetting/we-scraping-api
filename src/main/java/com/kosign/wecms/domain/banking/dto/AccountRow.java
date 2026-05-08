package com.kosign.wecms.domain.banking.dto;

/**
 * Single account row in the Account Inquiry response. ACCT_SEQ_NO is a
 * synthetic identifier ({@code BANK_CD + "_" + ACCT_NO}) the frontend
 * uses as the URL segment for the per-account Transaction History route
 * — there is no surrogate key on legacy ACCT_MSTR.
 *
 * <p>Wire names auto-derived from camelCase Java fields via the global
 * SNAKE_CASE Jackson naming strategy.
 *
 * <p><b>{@code acctTypeKey} (added 2026-05-08).</b> Frontend i18n key
 * resolved server-side via
 * {@link com.kosign.wecms.common.enums.AcctTypeCd}. Frontend renders
 * {@code t(acctTypeKey)} for the account-type label and falls back to
 * {@code acctTypeNm} when the key is {@code null} (e.g. legacy
 * {@code 01} LOAN, or any future code not yet registered in the enum).
 * The legacy {@code acctTypeNm} field continues to ship the English
 * label for one MR cycle so frontend rollout can stage — eventual
 * removal is tracked in the refactor docs.
 */
public record AccountRow(
        String acctSeqNo,
        String bankCd,
        String bankNm,
        String acctNo,
        String acctTypeCd,
        String acctTypeNm,
        String acctTypeKey,
        String ccyCd,
        String acctNickNm,
        String bal,
        String realAmt,
        String acctBalInqStts,
        String acctBalUpdDtm
) { }
