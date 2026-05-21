package com.kosign.wecms.domain.banking.transferhistory.dto;

import java.math.BigDecimal;

/**
 * One row of the Transfer History list. Serialised lowercase snake_case
 * via the global Jackson {@code SNAKE_CASE} strategy (no per-field
 * {@code @JsonProperty}). Stable identifier values ({@code "00000000"},
 * {@code "VND"} / {@code "USD"}) stay uppercase per the workspace
 * naming rule.
 *
 * <p>Maps the columns the legacy IDO
 * {@code IDO.NAPAS_REMIT_TRNS_HSTR_R003} projects — see the field-level
 * table in
 * {@code docs/refactor-wecms/migration/transfer-history.md § 2}. The
 * legacy {@code OWAC_NM AS OWAN_NM} re-aliasing is preserved in the
 * wire name (the frontend's `ApiTransferRow` already expects
 * {@code owan_nm}).
 *
 * <p>{@code RMRK2}, {@code FEE_DFR_SBJ}, {@code TRNS_STLM_ID},
 * {@code TRNS_STLM_YN} are intentionally omitted per OQ-8 (resolved
 * 2026-05-20).
 *
 * @param seqNo       per-tenant sequence (BIZ_KEY + SEQ_NO is the PK)
 * @param trnsDt      date portion of {@code REG_DTM}, {@code YYYYMMDD}
 * @param trnsTm      time portion of {@code REG_DTM}, {@code HHmm} (4 chars)
 * @param outBankCode sender bank code
 * @param outBankNm   sender bank display name (resolved from {@code BANK_INFM})
 * @param outAcctNo   sender account number
 * @param inBankCode  recipient bank code (legacy {@code IN_NAPAS_BANK_CODE} re-aliased to {@code IN_BANK_CODE} by the IDO)
 * @param inBankNm    recipient bank display name (resolved via the V86 legacy function)
 * @param inAcctNo    recipient account number
 * @param owanNm      recipient holder name (legacy {@code OWAC_NM AS OWAN_NM})
 * @param ccyCd       currency code, {@code "VND"} or {@code "USD"}
 * @param trscAmt     transfer amount — engine-scraped (see feature spec § 3)
 * @param fee         fee amount
 * @param transType   transfer-type flag (1 char, nullable)
 * @param rmrk        remark line
 * @param trnsStts    engine error code; {@code "00000000"} on success
 * @param trnsMsg     engine error message; null on success
 * @param userNm      operator display name (resolved from {@code USER_INFM} via JPA LEFT JOIN)
 */
public record TransferHistoryRow(
        Long seqNo,
        String trnsDt,
        String trnsTm,
        String outBankCode,
        String outBankNm,
        String outAcctNo,
        String inBankCode,
        String inBankNm,
        String inAcctNo,
        String owanNm,
        String ccyCd,
        BigDecimal trscAmt,
        BigDecimal fee,
        String transType,
        String rmrk,
        String trnsStts,
        String trnsMsg,
        String userNm
) {
}
