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
        List<ApprovalBoxApprover> approvers

) { }
