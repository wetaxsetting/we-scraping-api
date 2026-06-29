package com.kosign.wecms.domain.payment.haengsung.exchangerate;

import java.math.BigDecimal;

/**
 * Mapped row for one HAENGSUNG Oracle {@code TB_FCY_EXRATE} write (Step 6 —
 * legacy {@code ERP_0100_001}). Built by {@link HaengsungExchangeRateSyncService}
 * from a WeCMS {@code BANK_EXCH_RATE_INFM} row; executed by
 * {@link HaengsungFcyExRateOracleRepository}. The mapping is verbatim to legacy
 * {@code ERP_0100_001.UpsertTbFcyExRateData} + the {@code TB_FCY_EXRATE_C002} /
 * {@code _U001} IDOs:
 *
 * <ul>
 *   <li>{@code bizNo}      ← {@code BUSI_INFM.TAX_ID} (the company tax id).</li>
 *   <li>{@code bankCd}     ← the CITAD-mapped bank code (legacy R003 maps the
 *       WeCMS original code → CITAD via {@code CITAD_BANK_CD_LOOKUP}).</li>
 *   <li>{@code refDt}      ← {@code BANK_EXCH_RATE_INFM.LAST_UPDATE_DATE} (YYYYMMDD).</li>
 *   <li>{@code ccyCd}      ← {@code BANK_EXCH_RATE_INFM.REF_CCY_CD} (the base
 *       currency — legacy maps {@code TB_FCY_EXRATE.CCY_CD = getRefCcyCd()},
 *       NOT the counter currency; kept verbatim).</li>
 *   <li>{@code refSeq}     ← {@code BANK_EXCH_RATE_INFM.SEQ_NO} (numeric).</li>
 *   <li>{@code refTm}      ← {@code LAST_UPDATE_TIME} (HHMMSS).</li>
 *   <li>{@code lastInqDttm} ← {@code LAST_UPDATE_DATE + LAST_UPDATE_TIME} (14-char).</li>
 *   <li>{@code cmsPutDttm} ← now in Asia/Ho_Chi_Minh (14-char).</li>
 *   <li>amounts/rate       ← {@code BUY_AMT}/{@code SELL_AMT}/{@code TRAN_SELL_AMT}/
 *       {@code TRAN_BUY_AMT}/{@code BASIC_RATE} verbatim.</li>
 * </ul>
 *
 * <p>{@code ERP_GET_ST} is always {@code '0'} and {@code ERP_GET_DTTM} stays null
 * on insert — both set inside the repository, not carried here.
 */
public record OracleFcyExRate(
        String bizNo,
        String bankCd,
        String refDt,
        String ccyCd,
        BigDecimal refSeq,
        String refTm,
        BigDecimal buyAmt,
        BigDecimal sellAmt,
        BigDecimal tranSellAmt,
        BigDecimal tranBuyAmt,
        BigDecimal basicRate,
        String lastInqDttm,
        String cmsPutDttm) {
}
