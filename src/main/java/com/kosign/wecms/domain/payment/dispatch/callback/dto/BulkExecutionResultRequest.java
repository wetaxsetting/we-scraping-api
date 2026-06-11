package com.kosign.wecms.domain.payment.dispatch.callback.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Decrypted business payload of the <b>01302 bulk execution result</b> callback
 * (EFS → WeCMS), carried inside {@link EfsCallbackEnvelope#ev()}.
 *
 * <p>Field names are the vendor-defined names read by
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} ({@code "bank_cd"}, {@code "file_nm"},
 * {@code "TOT"}, {@code "SUCC"}, {@code "FAIL"}, …), pinned with
 * {@link JsonProperty} (vendor-casing rule).
 *
 * <p>Legacy behaviour: the 01302 path downloaded the fixed-length bank result
 * file over SFTP, parsed each line into {@code TB_FIRM_FILE_RSLT}, advanced
 * {@code PROC_STEP 2 → 3}, and aggregated the per-line outcomes into
 * {@code TB_PAY_FILE_MST} final status (all-success / partial / all-fail) using
 * the {@code TOT}/{@code SUCC}/{@code FAIL} counts.
 *
 * <p><b>TODO(K3):</b> the result-file byte layout that produces these rows is
 * gated on the K3 file-format-version confirmation. <b>TODO(B4):</b> the SFTP
 * download is blocked on the SFTP key/host.
 *
 * <p><b>TODO(B5) — field-set discrepancy:</b> REST API guide ver 1.2.2 §5.12
 * lists only the <b>six base fields</b> ({@code bank_cd, prtn_id, file_nm,
 * data_cnt, data_amt, data_dcm_amt}; {@code file_nm} widened to 400 there),
 * whereas the legacy {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp} reads
 * {@code TOT}/{@code SUCC}/{@code FAIL}. Those three are kept here for legacy
 * parity but are <b>optional / nullable</b> (a record component is null when the
 * key is absent — verified by test). Confirm the authoritative field set against
 * a real captured VAN callback sample before relying on {@code TOT/SUCC/FAIL}.
 *
 * @param bankCd      {@code bank_cd}      — outbound bank code
 * @param prtnId      {@code prtn_id}      — bank partner id
 * @param fileNm      {@code file_nm}      — firm file name
 * @param dataCnt     {@code data_cnt}     — record count
 * @param dataAmt     {@code data_amt}     — integer amount
 * @param dataDcmAmt  {@code data_dcm_amt} — decimal-portion amount
 * @param tot         {@code TOT}          — total processed count
 * @param succ        {@code SUCC}         — success count
 * @param fail        {@code FAIL}         — failure count
 */
public record BulkExecutionResultRequest(
        @JsonProperty("bank_cd") String bankCd,
        @JsonProperty("prtn_id") String prtnId,
        @JsonProperty("file_nm") String fileNm,
        @JsonProperty("data_cnt") String dataCnt,
        @JsonProperty("data_amt") String dataAmt,
        @JsonProperty("data_dcm_amt") String dataDcmAmt,
        @JsonProperty("TOT") String tot,
        @JsonProperty("SUCC") String succ,
        @JsonProperty("FAIL") String fail
) {
}
