package com.kosign.wecms.domain.payment.approvalbox.event;

/**
 * Generic WeCMS payment approval-lifecycle status carried by
 * {@link PaymentApprovalStatusChangedEvent}. Vendor-neutral — it describes the
 * WeCMS state transition only; downstream subscribers (e.g. the HAENGSUNG Oracle
 * write-back) map it to their own representation.
 */
public enum PaymentApprovalStatus {
    /** Request-approval submitted; approval workflow created (EXPT_RESOL_MSTR '00'). */
    PENDING,
    /** All approvers voted yes; batch fully approved (EXPT_RESOL_MSTR '01'). */
    APPROVED,
    /** An approver rejected; workflow cancelled (EXPT_RESOL_MSTR '02'). */
    REJECTED,
    /** Approved batch dispatched / sent for processing (TRNF_YN = 'Y'). */
    PROCESSING
}
