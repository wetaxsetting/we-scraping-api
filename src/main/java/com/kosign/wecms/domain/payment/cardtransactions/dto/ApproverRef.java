package com.kosign.wecms.domain.payment.cardtransactions.dto;

/**
 * One approver on a card-transaction row's linked expense resolution.
 * Legacy {@code REC_2} sub-record from {@code IDO.CARD_MSTR_R002.xml}
 * (sourced from {@code EXPT_RESOL_APPRVR_INFM}).
 *
 * <p>Phase B placeholder in this MR — service emits an empty list on
 * every row. The shape is published so the frontend can render the
 * approver chip against the final wire contract.
 */
public record ApproverRef(
        String apprvrId,
        String apprvrNm,
        String apprvrStts) {
}
