package com.kosign.wecms.domain.payment.dispatch.callback.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Encrypted inbound envelope common to the EFS → WeCMS callbacks
 * (01301 reception result / 01302 execution result).
 *
 * <p>Field names are the <b>vendor-defined</b> EFS parameter names read by the
 * legacy {@code FILE_DEPOSIT_TRANSFER_API1_act.jsp} /
 * {@code FILE_DEPOSIT_TRANSFER_API2_act.jsp}
 * ({@code request.getString("EV")}, {@code "VV"}, …). Per the WeCMS rule
 * <i>"never migrate vendor gateway DTO casing to lowercase"</i>
 * (memory {@code feedback_vendor_dto_casing.md}), every field is pinned with
 * {@link JsonProperty} so the global SNAKE_CASE strategy does not rewrite it.
 *
 * <p><b>TODO(B5):</b> the exact, complete envelope field set and whether the
 * callback is delivered as form-urlencoded or JSON must be confirmed against a
 * real EFS sample. Modelled here from the legacy {@code _act.jsp} reads only.
 *
 * <p><b>TODO(B2):</b> {@code EV} (AES-256-CBC ciphertext) and {@code VV}
 * (HMAC-SHA256) are decrypted/verified by
 * {@code com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService}
 * using the per-VAN {@code TB_VAN_API_INFO.API_KEY} — blocked on the live key.
 *
 * @param id       {@code ID}        — VAN org / partner identifier
 * @param ev       {@code EV}        — encrypted value (business JSON, AES-256-CBC)
 * @param vv       {@code VV}        — verification value (HMAC-SHA256 of EV)
 * @param em       {@code EM}        — encryption method marker
 * @param vm       {@code VM}        — verification method marker
 * @param rqDtime  {@code RQ_DTIME}  — request datetime (14-char prefix on EV)
 * @param tno      {@code TNO}       — transaction number
 * @param rc       {@code RC}        — result code (envelope-level)
 * @param rm       {@code RM}        — result message (envelope-level)
 * @param cnt      {@code CNT}       — record count
 */
public record EfsCallbackEnvelope(
        @JsonProperty("ID") String id,
        @JsonProperty("EV") String ev,
        @JsonProperty("VV") String vv,
        @JsonProperty("EM") String em,
        @JsonProperty("VM") String vm,
        @JsonProperty("RQ_DTIME") String rqDtime,
        @JsonProperty("TNO") String tno,
        @JsonProperty("RC") String rc,
        @JsonProperty("RM") String rm,
        @JsonProperty("CNT") String cnt
) {
}
