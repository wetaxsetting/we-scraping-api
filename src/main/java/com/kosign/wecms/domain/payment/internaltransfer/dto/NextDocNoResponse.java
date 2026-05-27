package com.kosign.wecms.domain.payment.internaltransfer.dto;

/**
 * Payment &gt; Internal Transfer &mdash; preview of the next
 * {@code VO_YYMMDD_NNN} document number that will be stamped on
 * {@code EXPT_RESOL_MSTR.DOC_NO} when the user submits the Request
 * Approval popup.
 *
 * <p>The value is a preview only &mdash; it is computed via
 * {@link com.kosign.wecms.domain.payment.service.DocNoGenerator#nextDocNo}
 * without reserving the counter, so if another user submits a
 * Request Approval between this preview and the caller's own
 * submit, the actual assigned value will increment past this one.
 * The frontend uses this purely to populate the read-only
 * "Document No." field on the popup; the authoritative value is
 * stamped server-side inside {@code requestApproval}.
 *
 * <p>Internal Transfer's per-batch parent ({@code SCDUL_PYMN_NO} +
 * legacy generated keys) cannot expose the friendly {@code VO_*}
 * shape until the approval is actually submitted &mdash;
 * {@code DOC_NO} lives on {@code EXPT_RESOL_MSTR}, which is only
 * written at submit. This preview endpoint mirrors the Card
 * Transactions precedent so the Request Approval dialog renders
 * the same friendly id format across every Payment module.
 */
public record NextDocNoResponse(String docNo) {
}
