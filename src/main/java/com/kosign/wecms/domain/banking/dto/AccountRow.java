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
        String acctBalUpdDtm,
        /**
         * Transaction last-success timestamp ({@code TRN_LST_SUC_DTM},
         * {@code yyyyMMddHHmmss}) — the source the Bank Transaction Sync
         * popup uses to compute its default scrape window (earliest across
         * the selected accounts, floored to 3 months back). Legacy
         * {@code nxisas_tranList.getInitStartDate()} reads the same column
         * off {@code fnsh_0001_001_r001}. {@code null} when the account has
         * never completed a transaction scrape → the popup falls back to
         * 3-months-back. This is NOT the "Last Update" display value
         * ({@code acctBalUpdDtm} above, sourced from {@code ACCT_HIS_LST_INQ_DT}).
         */
        String trnLstSucDtm,
        /**
         * Manual-registration flag — verbatim {@code "Y"} / {@code "N"}
         * from {@code ACCT_MSTR.HAND_REG_YN}. Frontend disables the row's
         * checkbox with the {@code account_inquiry.disabled.manual} tooltip
         * when {@code "Y"} (legacy {@code fnsh_0005_001.ts:708-709}). Never
         * null on the wire — backend coerces to {@code "N"} when the
         * column carries SQL NULL.
         */
        String handRegYn,
        /**
         * Bank-certificate connection flag — backend-derived per-bank.
         * {@code "Y"} when {@code ACCT_MSTR.BANK_CD} has at least one
         * non-blank credential row in {@code BANK_CERT_INFO} for the
         * tenant; {@code "N"} otherwise. Per-bank rather than per-FK
         * (i.e. NOT resolving via {@code BANK_CERT_SEQ}) so the answer
         * survives Login Credentials add/delete cycles that mint fresh
         * {@code SEQ_NO}s diverging from the legacy FKs on existing
         * accounts.
         *
         * <p>The legacy disconnected-bank check at
         * {@code fnsh_0005_001.ts:711} reads {@code CERT_INFO_CON} and
         * {@code CERT_INFO_DV} from the {@code ACCT_MSTR} row directly,
         * but the legacy IDO {@code ACCT_MSTR_R001} never selects those
         * two columns from that table — so the legacy frontend sees them
         * as {@code null} on every row and the check is always-true.
         * The refactor restores the intended semantics by joining
         * {@code BANK_CERT_INFO} via {@code BANK_CERT_SEQ} where the
         * real credential lives.
         *
         * <p>Frontend disables the row's checkbox with the
         * {@code account_inquiry.disabled.disconnected} tooltip when
         * {@code "N"} AND {@code handRegYn != "Y"} (manual wins over
         * disconnected, matching legacy precedence).
         */
        String certConnYn
) { }
