package com.kosign.wecms.domain.payment.dispatch.callback.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Decrypted business payload of the <b>01301 bulk reception result</b> callback
 * (EFS → WeCMS), i.e. the JSON carried inside {@link EfsCallbackEnvelope#ev()}.
 *
 * <p>Field names are the vendor-defined names read by
 * {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp}
 * ({@code request.getString("bank_cd")}, {@code "data_cnt"}, …). They are
 * already lowercase but are pinned with {@link JsonProperty} so they are
 * decoupled from the global naming strategy (vendor-casing rule).
 *
 * <p>Legacy reconciliation: the callback compared {@code data_cnt}/{@code data_amt}
 * against the requested {@code REQ_CNT}/{@code REQ_AMT}; a mismatch set the file
 * to {@code PROC_STAT='3'} with {@code REG_RSLT_CD='9999'}
 * ({@link com.kosign.wecms.domain.payment.dispatch.constant.FirmBankingResultCd#AMT_ERR}).
 *
 * <p><b>TODO(B5):</b> confirm the full decrypted field set against a real EFS
 * sample. <b>TODO(K9):</b> on {@code result_cd} success the legacy flow's
 * successor (00302 execution) is issued; the recovery rule when that does not
 * happen is the open K9 decision.
 *
 * @param bankCd      {@code bank_cd}      — outbound bank code
 * @param prtnId      {@code prtn_id}      — bank partner id
 * @param fileNm      {@code file_nm}      — firm file name (joins TB_FIRM_FILE_MST.FIRM_FILE_NM)
 * @param dataCnt     {@code data_cnt}     — received record count
 * @param dataAmt     {@code data_amt}     — received integer amount
 * @param dataDcmAmt  {@code data_dcm_amt} — received decimal-portion amount
 * @param resultCd    {@code result_cd}    — reception result code
 * @param resultMsg   {@code result_msg}   — reception result message
 */
public record BulkReceptionResultRequest(
        @JsonProperty("bank_cd") String bankCd,
        @JsonProperty("prtn_id") String prtnId,
        @JsonProperty("file_nm") String fileNm,
        @JsonProperty("data_cnt") String dataCnt,
        @JsonProperty("data_amt") String dataAmt,
        @JsonProperty("data_dcm_amt") String dataDcmAmt,
        @JsonProperty("result_cd") String resultCd,
        @JsonProperty("result_msg") String resultMsg
) {
}
