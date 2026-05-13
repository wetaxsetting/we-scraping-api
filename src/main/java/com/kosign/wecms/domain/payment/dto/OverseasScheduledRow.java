package com.kosign.wecms.domain.payment.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single row in the overseas-payment scheduled-payment list.
 * Wire names auto-derived via global Jackson SNAKE_CASE strategy.
 *
 * <p>Mirrors WSVC.pymn_0007_001_r001.xml → REC_OUT fields + nested
 * REC_1 approver list. Source query: IDO.OVRS_PYMN_SUMR_R002 (UNION ALL
 * of OVRS_PYMN_SUMR [type E] and OVRS_PYMN_MSTR [type M]).
 *
 * <ul>
 *   <li>{@code ovrsPymnType} — {@code "E"} for batch/Excel upload,
 *       {@code "M"} for individual manual payment.</li>
 *   <li>{@code fileNm} — for type E this is the Excel filename (TITL);
 *       for type M this is {@code BNFC_NM + " " + BNFC_ACCT_NO}.</li>
 *   <li>{@code outBankNm} — stored directly for R&D parity (JEX resolves
 *       via {@code wb_get_bank_name_any_code}).</li>
 * </ul>
 */
public record OverseasScheduledRow(
        String ovrsPymnNo,
        String ccyCd,
        String ovrsPymnTypeCd,
        String titl,
        String apprSeqNo,
        BigDecimal totalTranAmt,
        Integer totTrnsCnt,
        String regDtm,
        String draftUserId,
        String draftUserNm,
        String apprStts,
        String trnfYn,
        Integer apprvrApprTotCnt,
        Integer apprvrTotCnt,
        String apprDv,
        String docNo,
        String fileUrl,
        String outBankCd,
        String outBankNm,
        String wdrlActNo,
        String bnfcNm,
        /** {@code "E"} or {@code "M"} — the UNION branch source. */
        String ovrsPymnType,
        String payDate,
        String fileNm,
        String regId,
        List<OverseasApprover> approvers
) {}
