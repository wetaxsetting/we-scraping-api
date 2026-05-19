package com.kosign.wecms.domain.banking.nxisas.gateway.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * NXISAS job discriminator. Ported from legacy
 * {@code kr.co.coocon.ibasews.enums.NxisasJob}; only the values the
 * spike consumes are kept — extending the enum is a one-line change
 * per new job. Wire value preserved verbatim (uppercase snake), set
 * via {@link #JsonValue}.
 */
public enum NxisasJob {
    LOGIN("LOGIN"),
    LOGOUT("LOGOUT"),
    OTP("OTP"),
    CAPTCHA("CAPTCHA"),
    QNA("QNA"),
    ACCT_LIST("ACCT_LIST"),
    BALANCE("BALANCE"),
    TRANSACTION("TRANSACTION"),
    CARD_INFO("CARD_INFO"),
    CARD_USING("CARD_USING");

    private final String value;

    NxisasJob(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static NxisasJob fromValue(String v) {
        for (NxisasJob j : values()) if (j.value.equals(v)) return j;
        return null;
    }
}
