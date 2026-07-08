package com.kosign.wecms.domain.payment.approvalbox.dto;

import java.util.List;

/**
 * One row in the Approval Box list response. Maps approximately to the
 * legacy {@code REC} sub-record from {@code WSVC.resl_0001_001_r001.xml}
 * with the deltas documented in
 * {@code docs/refactor-wecms/features/payment/approval-box/design-backend.md} §13:
 *
 * <ul>
 *   <li><b>D1</b> — single {@code totPayAmt} + {@code currency} replaces
 *       the legacy {@code TOT_PAY_KHR_AMT} / {@code TOT_PAY_USD_AMT}
 *       split. Currency is always {@code "VND"} for the refactor's VN
 *       tenants until the sibling-table joins land in a follow-up MR.</li>
 *   <li><b>D2</b> — {@code typeLabel} is a stable enum slug, not a
 *       localised string. Frontend resolves the visible label via
 *       {@code payment.approval_box.type.<slug>}.</li>
 *   <li><b>D3</b> — {@code apprStts} is Java-derived from
 *       {@code APPR_TYPE_CD} ({@code P}/{@code I}/{@code C}/{@code R}).
 *       Replaces the legacy {@code wb_get_appr_stts(...)} PL/pgSQL helper.</li>
 *   <li><b>D6</b> — chain ships under {@code approvers}, not legacy
 *       {@code REC3}.</li>
 * </ul>
 *
 * <p>Wire names auto-derived from camelCase via the global SNAKE_CASE
 * Jackson naming strategy.
 */
public record ApprovalBoxRow(

        /** Per-tenant document sequence — {@code EXPT_RESOL_MSTR.APPR_SEQ_NO}.
         *  Doubles as the row id for Approve / Reject / Detail endpoints. */
        String apprSeqNo,

        /** Auto-generated document number ({@code VO_YYMMDD_NNN}). */
        String docNo,

        /** User-supplied subject. */
        String title,

        /** Uploaded payment-batch file name — legacy
         *  {@code COALESCE(SCDUL_PYMN_SUMR.TITL, OVRS_PYMN_SUMR.TITL)}
         *  ({@code IDO.EXPT_RESOL_MSTR_R003.xml:146}). Resolved via the
         *  same sibling-table batch fetch as {@link #currency}; blank
         *  when no source batch row carries a title (e.g. an approval
         *  with no matching SCDUL/OVRS summary — same as the legacy
         *  COALESCE of two nulls). Wire name {@code file_name}. */
        String fileName,

        /** Free-form approval content ({@code EXPT_RESOL_MSTR.CONT}).
         *  Optional textarea the requestor fills in on the Request
         *  Approval dialog; rendered on the Approval Details popup's
         *  "Approval Content" section. */
        String cont,

        /** Raw {@code APPR_DV} digit (one of {@code 21}/{@code 31}/
         *  {@code 32}/{@code 33}/{@code 34}/{@code 35}). Kept on the wire
         *  for legacy traceability — frontend reads {@link #typeLabel}. */
        String apprDv,

        /** Stable slug for the payment-type enum:
         *  {@code payroll}/{@code expense}/{@code vendor}/
         *  {@code corporate_card}/{@code overseas}/{@code internal_transfer}.
         *  Frontend resolves the visible label via i18n. */
        String typeLabel,

        /** ISO 4217 currency code. Always {@code "VND"} until the
         *  sibling-table joins land (D1 in design-backend.md §13). */
        String currency,

        /** Amount in the row's currency. Sourced from
         *  {@code EXPT_RESOL_MSTR.TOT_PAY_KHR_AMT} (which holds VND for
         *  VN tenants per legacy convention). Serialised as a string —
         *  mirrors the {@code AccountRow.bal} contract so the frontend
         *  mapper's {@code raw.startsWith("-")} path stays safe and so
         *  large VND values dodge JS number-precision loss. */
        String totPayAmt,

        /** Scheduled payment date ({@code YYYYMMDD}). */
        String payDate,

        /** Requestor user id. */
        String draftUserId,

        /** Requestor display name. */
        String draftUserNm,

        /** Registration timestamp ({@code yyyyMMddHHmmss}). */
        String regDtm,

        /** Derived approval status — {@code P}/{@code I}/{@code C}/
         *  {@code R}. See class-level Javadoc D3. */
        String apprStts,

        /** Total approvers on the chain. */
        long apprvrTotCnt,

        /** Approvers that have already approved (status {@code 'A'}). */
        long apprvrApprTotCnt,

        /** Execution flag — {@code 'Y'} when the approved doc has been
         *  transferred / paid. */
        String payEndYn,

        /** Approver chain, ordered by {@code APPRVR_LINE_NO ASC}. */
        List<ApprovalBoxApprover> approvers,

        /* ── Detail-only withdrawal-account fields (Approval Details popup) ──
         *  Populated ONLY on the single-row detail read
         *  ({@code ApprovalDetailService.master}); always {@code null} on
         *  list rows ({@code ApprovalBoxService.list} → {@code toRow}). Mirror
         *  the legacy {@code comm_0195_001} "Withdrawal Bank/Account No." row,
         *  sourced from the sibling {@code SCDUL_PYMN_SUMR} batch by
         *  {@code APPR_SEQ_NO} (legacy R007 + the SCDUL overlay). The shared
         *  {@code CommonApprovalDetailsDialog} renders the row from these. */

        /** Withdrawal bank code ({@code SCDUL_PYMN_SUMR.OUT_BANK_CD}). */
        String outBankCd,

        /** Resolved withdrawal bank display name (BANK_INFM / NAPAS-CITAD). */
        String outBankNm,

        /** Withdrawal account number ({@code SCDUL_PYMN_SUMR.WDRL_ACT_NO}). */
        String wdrlActNo,

        /** Withdrawal account currency ({@code SCDUL_PYMN_SUMR.WDRL_CCY_CD}). */
        String wdrlCcyCd,

        /** Payroll fund type ({@code EXPT_RESOL_MSTR.APPR_FUND_TP}) —
         *  {@code 0}=Salary / {@code 1}=Bonus / {@code 9}=Other. Detail-read
         *  only (null on list rows; meaningful only when {@code apprDv = '21'}).
         *  The frontend resolves the localized label (legacy
         *  {@code comm_0195_001.ts:885-898}, incl. the BANK_CD 11704088
         *  override) so the dialog stays presentational. */
        String apprFundTp,

        /** Batch id of the sibling payment summary this approval dispatches —
         *  {@code SCDUL_PYMN_SUMR.SCDUL_PYMN_NO} for the scheduled types
         *  ({@code 21}/{@code 31}/{@code 32}/{@code 35}) or
         *  {@code OVRS_PYMN_SUMR.OVRS_PYMN_NO} for overseas ({@code 34}).
         *  Detail-read only (null on list rows; null for Corporate Card and for
         *  any approval with no dispatchable batch, e.g. overseas M-mode). The
         *  auto-transfer-after-full-approval flow uses it as the id for the
         *  per-module {@code /{id}/dispatch} endpoint — the refactor equivalent
         *  of legacy {@code comm_0195_001.js}'s
         *  {@code _scdulPymnNo = data.SCDUL_PYMN_NO || data.OVRS_PYMN_NO}. */
        String batchNo,

        /** Transfer-network discriminator of the batch's first line
         *  ({@code SCDUL_PYMN_SUMR_DETL.ANT_DIV_CD} — {@code N/NAPAS/INTER},
         *  {@code C/CITAD}, {@code S/SWIFT}). Detail-read only (null on list
         *  rows, Corporate Card, overseas). Lets the auto-transfer window check
         *  use the real channel instead of inferring it from currency — the
         *  refactor equivalent of legacy {@code mapTransferType(REC[0].ANT_DIV_CD)}.
         *  Null → the FE falls back to currency inference (backend re-validates
         *  the real window on dispatch either way). */
        String antDivCd

) {

    /**
     * Detail-read enrichment: returns a copy of this row with the
     * withdrawal-account fields populated and {@code cont} overridden with the
     * legacy {@code BLTB_POST_INFM.POST_CONT} note. Used by
     * {@code ApprovalDetailService.master} so the shared Approval Details popup
     * matches the per-type Scheduled detail (and legacy {@code comm_0195_001})
     * regardless of entry point. The list path never calls this — its rows keep
     * null withdrawal fields and the {@code EXPT_RESOL_MSTR.CONT} value.
     * {@code apprFundTp} is preserved (it is enriched separately in
     * {@code findOne} via {@link #withApprFundTp}). {@code batchNo} +
     * {@code antDivCd} are the dispatch batch id and the first-line transfer
     * channel resolved by the same sibling-batch fetch.
     */
    public ApprovalBoxRow withDetail(String outBankCd, String outBankNm,
                                     String wdrlActNo, String wdrlCcyCd, String cont,
                                     String batchNo, String antDivCd) {
        return new ApprovalBoxRow(
                apprSeqNo, docNo, title, fileName, cont, apprDv, typeLabel, currency,
                totPayAmt, payDate, draftUserId, draftUserNm, regDtm, apprStts,
                apprvrTotCnt, apprvrApprTotCnt, payEndYn, approvers,
                outBankCd, outBankNm, wdrlActNo, wdrlCcyCd, apprFundTp, batchNo, antDivCd);
    }

    /**
     * Detail-read enrichment for the Payroll Type row. Populated in
     * {@code ApprovalBoxService.findOne} from {@code EXPT_RESOL_MSTR.APPR_FUND_TP}
     * (which is loaded there); null on every list row. Kept separate from
     * {@link #withDetail} because the value's source is the master entity, not
     * the sibling batch.
     */
    public ApprovalBoxRow withApprFundTp(String apprFundTp) {
        return new ApprovalBoxRow(
                apprSeqNo, docNo, title, fileName, cont, apprDv, typeLabel, currency,
                totPayAmt, payDate, draftUserId, draftUserNm, regDtm, apprStts,
                apprvrTotCnt, apprvrApprTotCnt, payEndYn, approvers,
                outBankCd, outBankNm, wdrlActNo, wdrlCcyCd, apprFundTp, batchNo, antDivCd);
    }
}
