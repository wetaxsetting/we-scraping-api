package com.kosign.wecms.domain.banking.dto;

/**
 * One row in the Smart Organizer candidate list — a payment transaction
 * that could match the bank transaction the popup is anchored on.
 *
 * <p>Mirrors the legacy candidate query {@code comm_0196_001_r001} /
 * {@code OVRS_PYMN_SUMR_DETL_R006} 3-branch UNION output. As of P4 the list
 * carries all three candidate kinds:
 * <ul>
 *   <li>domestic scheduled <b>detail</b> lines — {@code payTypeCd='S'},
 *       {@code scdulPymnTypeCd} in {@code 01/02/03};
 *       {@code payTrnsId = SCDUL_PYMN_NO + "_" + SCDUL_PYMN_DETL_NO};</li>
 *   <li>payroll <b>master/batch</b> rows — {@code payTypeCd='S'},
 *       {@code scdulPymnTypeCd='00'}; {@code payTrnsId = SCDUL_PYMN_NO}
 *       (bare — no {@code "_"}), matching legacy {@code PAY_TRNS_ID};</li>
 *   <li>overseas payment <b>detail</b> lines — {@code payTypeCd='O'};
 *       {@code payTrnsId = OVRS_PYMN_NO + "_" + OVRS_PYMN_DETL_NO}.</li>
 * </ul>
 * The frontend ships {@code payTrnsId} + {@code payTypeCd} verbatim in the
 * match-create request so {@code MatchService.create} routes to the right
 * write path.
 *
 * <p>Wire is lowercase snake_case via the global Jackson SNAKE_CASE strategy.
 */
public record MatchCandidateRow(
        String payTrnsId,
        /** {@code 'S'} = domestic/payroll scheduled, {@code 'O'} = overseas. */
        String payTypeCd,
        String scdulPymnNo,
        String scdulPymnDetlNo,
        String scdulPymnTypeCd,
        String trnfDt,
        String trnfDtm,
        String tranAmt,
        String trnfCcyCd,
        String rcptNm,
        String bnfcBankCd,
        String bnfcBankNm,
        String bnfcAcctNo,
        String tranUsag,
        String titl,
        /** Overseas identity — null on the domestic/payroll branches. */
        String ovrsPymnNo,
        String ovrsPymnDetlNo,
        String ovrsPymnTypeCd,
        /** Legacy {@code MEMO} column — overseas beneficiary memo / domestic NOTE. */
        String memo
) { }
