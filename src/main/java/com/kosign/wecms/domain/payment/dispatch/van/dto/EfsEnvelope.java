package com.kosign.wecms.domain.payment.dispatch.van.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * EFS gateway transport envelope — the outer wrapper for every
 * {@code WGVN_EFS_*} request and response (REST API guide ver 1.2.2 §4;
 * see {@code analysis/efs-service-mapping-from-pdf.md}). The decrypted business
 * payload travels inside {@link #ev()}.
 *
 * <p><b>Vendor casing is mandatory.</b> These field names are the vendor's exact
 * UPPER-case wire names and do <b>not</b> map from camelCase via the global
 * {@code SNAKE_CASE} strategy ({@code rqDtime} would become {@code rq_dtime}).
 * Each component is therefore pinned with {@link JsonProperty}; an explicit
 * {@code @JsonProperty} overrides the global {@link com.fasterxml.jackson.databind.PropertyNamingStrategy},
 * so {@code ID}/{@code RQ_DTIME}/… stay upper-case under the production mapper.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so a single record serves both directions:
 * <ul>
 *   <li><b>Request</b> (WeCMS→EFS) populates {@code ID, RQ_DTIME, TNO, EV, VV,
 *       EM=AES, VM=HmacSHA256}; {@code RS_DTIME/RC/RM} stay null and drop out.</li>
 *   <li><b>Response</b> (EFS→WeCMS) populates {@code ID, RS_DTIME, TNO, EV, VV,
 *       EM, VM, RC, RM}; {@code RQ_DTIME} stays null.</li>
 * </ul>
 *
 * <p>{@code EV}/{@code VV} are built and verified by
 * {@link com.kosign.wecms.domain.payment.dispatch.van.crypto.EfsCryptoService};
 * this record is a pure carrier — no crypto, no HTTP. The real {@code RestClient}
 * adapter that assembles/sends it is a later phase (B1/B2).
 *
 * <p><b>TODO(OQ-PDF-1):</b> the service DTOs in this package cover only the
 * services confirmed in guide ver 1.2.2 — {@code WGVN_EFS_201/202/203/301/302}.
 * {@code WGVN_EFS_204} (legacy FX exchange-transfer inquiry) is <b>not present</b>
 * in this guide and is deliberately NOT modelled; confirm whether it exists in
 * another document/version or is retired before adding a 204 DTO.
 *
 * @param id      {@code ID}        partner identifier
 * @param rqDtime {@code RQ_DTIME}  request datetime {@code yyyyMMddHHmmss} (request only)
 * @param rsDtime {@code RS_DTIME}  response datetime {@code yyyyMMddHHmmss} (response only)
 * @param tno     {@code TNO}       transaction number
 * @param ev      {@code EV}        encrypted business JSON (AES-256-CBC, Base64)
 * @param vv      {@code VV}        HMAC-SHA256 verification value (Base64)
 * @param em      {@code EM}        encryption algorithm marker — {@code AES}
 * @param vm      {@code VM}        MAC algorithm marker — {@code HmacSHA256}
 * @param rc      {@code RC}        result code — {@code 0000} success / {@code Gxxx} (response only)
 * @param rm      {@code RM}        result message (response only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EfsEnvelope(
        @JsonProperty("ID") String id,
        @JsonProperty("RQ_DTIME") String rqDtime,
        @JsonProperty("RS_DTIME") String rsDtime,
        @JsonProperty("TNO") String tno,
        @JsonProperty("EV") String ev,
        @JsonProperty("VV") String vv,
        @JsonProperty("EM") String em,
        @JsonProperty("VM") String vm,
        @JsonProperty("RC") String rc,
        @JsonProperty("RM") String rm
) {

    /** {@code EM} marker value. */
    public static final String EM_AES = "AES";
    /** {@code VM} marker value. */
    public static final String VM_HMAC_SHA256 = "HmacSHA256";
}
