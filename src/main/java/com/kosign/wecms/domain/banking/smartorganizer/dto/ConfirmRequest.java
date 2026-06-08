package com.kosign.wecms.domain.banking.smartorganizer.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bulk confirm payload — one or more eligible transactions the user
 * has reviewed and is committing to {@code ACCT_TRNS_RCV_PAY_INFM}.
 * Mirrors legacy {@code fnsh_0200_001_c001} REC.
 *
 * <p>Per row the frontend ships the auto-classification targets from
 * the matched rule (possibly user-edited). The service:
 * <ol>
 *   <li>Loads the corresponding {@code ACCT_TRNS_HSTR} row (404 if
 *       missing in tenant);</li>
 *   <li>Optionally validates {@code useUsagCd} against
 *       {@code BUSI_USAG_SET_INFM} (presence check only — the
 *       legacy USAG_GRP_CD ↔ INOUT_DV rule is retired in refactor
 *       per § OQ-Backend-1);</li>
 *   <li>Optionally validates {@code vasAcctSeqNo} against
 *       {@code BUSI_VAS_ACCT_INFM};</li>
 *   <li>Optionally validates {@code trcoKey} against
 *       {@code TRCO_INFM};</li>
 *   <li>Allocates a {@code RCV_PAY_SEQ_NO} via
 *       {@code wb_get_next_val('acct_trns_rcv_pay_infm',
 *       'rcv_pay_seq_no', biz_key, acct_seq_no)};</li>
 *   <li>Inserts the row with {@code CNFM_YN='Y'} stamped to the
 *       current login.</li>
 * </ol>
 */
public record ConfirmRequest(
        /** Selected account scope (R11) — Smart Organizer confirm operates
         *  only on the account currently selected in Transaction History. */
        @NotBlank @Size(max = 20) String bankCd,
        @NotBlank @Size(max = 100) String acctNo,
        @NotEmpty
        @Valid
        List<Row> rows
) {
    public record Row(
            @NotBlank @Size(max = 20) String acctSeqNo,
            @Size(max = 20) String useUsagCd,
            Long vasAcctSeqNo,
            @Size(max = 20) String trcoKey,
            @Size(max = 20) String chartAcctCd,
            @Size(max = 10) String chartAcctSeqNo
    ) {}
}
