package com.kosign.wecms.domain.setting.bankaccount.dto;

import java.math.BigDecimal;

/**
 * One row in the Settings &gt; Bank Account list. Mirrors the static-UI
 * {@code BankAccountRow} shape in {@code
 * wecms-web-main/components/settings/bank-account/types.ts}.
 *
 * <p>Unlike Banking &gt; Account Inquiry's row (which surfaces balances),
 * this row carries the master-maintenance fields (nickname, description,
 * account type, maturity, VAS mapping). Permission counts + audit-log
 * actor / date are populated in later batches; for Batch A they ship as
 * null / 0 placeholders so the wire shape is stable.
 */
public record BankAccountRow(
        String     bankCd,
        String     bankName,
        String     acctNo,
        String     currency,
        String     nickname,
        String     description,
        /** Legacy 2-char {@code ACCT_TYPE_CD}. Frontend maps to the
         *  semantic "current" / "saving" / "fixed" UI value. */
        String     accountTypeCd,
        /** Maturity date in ISO {@code yyyy-MM-dd} form. Null for
         *  current accounts (legacy stores {@code yyyyMMdd}; the
         *  service formats on the wire so the frontend sees a familiar
         *  ISO date). */
        String     maturityDate,
        /** VAS mapping FK — null when no VAS account is linked. */
        Long       vasAcctSeqNo,
        String     vasAcctCd,
        String     vasAcctNm,
        /** Denormalised "BANK_NM(CCY)" string for the VAS account name
         *  column — for Batch A we just echo {@code vasAcctNm}; full
         *  Bank-Name + Ccy composite lands when VAS mapping CRUD ships
         *  in Batch B. */
        String     vasAccountBank,
        /** Print order. Legacy `PRN_SEQ` — null when not yet assigned. */
        Integer    prnSeq,
        /** Available-balance / balance figures echoed from the master
         *  row so the list can show the same numbers the Account
         *  Inquiry page shows. Both nullable. */
        BigDecimal bal,
        BigDecimal realAmt,
        /** Latest mod-id + mod-dtm — feed into the panel's "audit log
         *  actor + date" cell until the per-row audit-log read endpoint
         *  ships in Batch B. */
        String     lastModifiedBy,
        String     lastModifiedAt,
        /** "View Permission" label aggregate. Format:
         *    null  = no active grantee → frontend renders the "View
         *            Permission" link placeholder.
         *    "X"   = single grantee.
         *    "X + N more" = first grantee plus count of additional
         *            active grantees. Server-side label keeps the
         *            counting logic in one place; frontend just
         *            displays it verbatim. */
        String     viewPermissionLabel,
        /** "Withdrawal Permission" label aggregate — same shape as
         *  {@code viewPermissionLabel} but counts {@code AUTH_TYPE='W'}
         *  rows. */
        String     withdrawalPermissionLabel
) { }
