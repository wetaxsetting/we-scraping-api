package com.kosign.wecms.domain.banking.smartorganizer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create / Update payload for a Smart Organizer rule. The frontend's
 * Organizer Settings popup posts this for both new rows (no
 * {@code rmrkSeqNo}) and edits (PUT path carries the surrogate). Field
 * shape mirrors the legacy {@code fnsh_0200_002_c001} REC1 record.
 *
 * <p>Field rules:
 * <ul>
 *   <li>{@code rmrk}: required, max 500 chars (legacy schema length).</li>
 *   <li>{@code matchType}: required, single char {@code C} (contains)
 *       or {@code E} (exact). Matches legacy {@code MatchType} enum.</li>
 *   <li>{@code rcvPayDv}: required, {@code RCV} or {@code PAY}. Drives
 *       the eligibility query branch on {@code INOUT_DV} (1 → RCV,
 *       2 → PAY).</li>
 *   <li>{@code dispOrd}: optional. When omitted the service assigns
 *       {@code MAX(disp_ord)+1} per tenant.</li>
 *   <li>{@code trcoKey} / {@code useUsagCd} / {@code vasAcctSeqNo} /
 *       {@code chartAcctSeqNo} / {@code chartAcctCd}: optional
 *       auto-classification targets. Legacy required at least one of
 *       (USE_USAG_CD, VAS_ACCT_SEQ_NO); the refactor relaxes this to
 *       allow rules that only set CHART_ACCT_CD (Reporting Account).</li>
 * </ul>
 */
public record OrganizerRuleSaveRequest(
        @NotBlank
        @Size(max = 500)
        String rmrk,

        @NotBlank
        @Pattern(regexp = "^[CE]$", message = "match_type must be 'C' or 'E'")
        String matchType,

        @NotBlank
        @Pattern(regexp = "^(RCV|PAY)$", message = "rcv_pay_dv must be 'RCV' or 'PAY'")
        String rcvPayDv,

        Integer dispOrd,

        @Size(max = 20)
        String trcoKey,

        @Size(max = 20)
        String useUsagCd,

        Long vasAcctSeqNo,

        @Size(max = 10)
        String chartAcctSeqNo,

        @Size(max = 20)
        String chartAcctCd
) {
}
