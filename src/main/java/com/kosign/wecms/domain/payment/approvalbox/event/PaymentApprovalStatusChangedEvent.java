package com.kosign.wecms.domain.payment.approvalbox.event;

/**
 * Published when a WeCMS payment batch's approval lifecycle state is finalized
 * (request-approval / approve / reject / dispatch). It is a thin, generic
 * lifecycle signal — it does NOT itself perform any side effect.
 *
 * <p>Subscribers handle it after the publishing transaction commits. The
 * HAENGSUNG Oracle approval-status write-back
 * ({@code haengsung-approval-status-writeback}) is one such subscriber and is
 * the <b>single</b> place that triggers Oracle synchronization; it filters to
 * HAENGSUNG / ERP-imported batches and no-ops for everything else, so emitting
 * this event for every payment module / tenant is safe.
 *
 * @param bizKey    tenant key of the batch.
 * @param apprSeqNo the EXPT_RESOL_MSTR / payment-summary approval sequence number.
 * @param status    the finalized lifecycle status.
 */
public record PaymentApprovalStatusChangedEvent(
        String bizKey,
        String apprSeqNo,
        PaymentApprovalStatus status
) { }
