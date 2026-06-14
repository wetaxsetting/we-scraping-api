package com.kosign.wecms.domain.payment.approvalbox.dto;

import java.math.BigDecimal;

/**
 * One normalized per-line transaction for the read-only Approval Details
 * popup (the {@code GET /api/v1/payment/approval-box/{apprSeqNo}/transactions}
 * response). Field names auto-snake_case via the global Jackson strategy and
 * line up with the frontend {@code RequestApprovalTransaction} contract the
 * shared {@code ApprovalDetailsDialog} consumes.
 *
 * <p>Populated for the four scheduled payment kinds (Internal Transfer /
 * Vendor / Expense / Payroll), which share {@code SCDUL_PYMN_SUMR_DETL}.
 * Card transactions are loaded on the frontend via the existing
 * {@code fetchCardTransactionsByApproval} path (card carries extra
 * card-only fields), and Overseas line loading is deferred — see
 * {@code ApprovalDetailService}.
 *
 * <p>{@link #amount} ships as a raw numeric (not a pre-formatted string);
 * the frontend formats it for display (mirrors the card path's
 * {@code cardRowToApprovalTransaction}), so locale/currency formatting stays
 * on the client per project convention.
 */
public record ApprovalTransactionRow(
        String     id,
        String     depositBankCode,
        String     depositBankName,
        String     depositBankCcyCd,
        String     depositAccountNo,
        BigDecimal amount,
        String     recipientName,
        String     remarks,
        String     contactName,
        String     description,
        String     documentNo,
        String     reportingAccount,
        String     reportingBreadcrumb
) { }
