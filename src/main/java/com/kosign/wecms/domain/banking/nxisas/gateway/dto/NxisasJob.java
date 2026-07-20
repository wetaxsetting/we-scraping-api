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
    /**
     * Fixed-Deposit (Time-Deposit) balance + transaction jobs — legacy
     * {@code kr.co.coocon.ibasews.enums.NxisasJob.BALANCE_FIXED_DEPOSIT} /
     * {@code TRANSACTION_FIXED_DEPOSIT}. Dispatched for {@code ACCT_TYPE_CD "02"}
     * accounts against a fixed bank whitelist: VCB ({@code 11704004}) scrapes
     * both; Shinhan/ACB/HD/Vietin/VP scrape {@code BALANCE_FIXED_DEPOSIT} only
     * (no transaction-FD support). See {@code nxisas_tranList_osmc.js:301-338}.
     */
    BALANCE_FIXED_DEPOSIT("BALANCE_FIXED_DEPOSIT"),
    TRANSACTION_FIXED_DEPOSIT("TRANSACTION_FIXED_DEPOSIT"),
    CARD_INFO("CARD_INFO"),
    CARD_USING("CARD_USING"),
    /**
     * Foreign-exchange rate inquiry — used by the Exchange Rate batch
     * (legacy BCS EXCH_0001_001 / EXCH_0010_001 / EXCH_0020_001). No
     * session is held; one call per (bank, seqNo) pair returns the
     * full {@code foreignExchange[]} list for that round.
     */
    FOREIGN_EXCHANGE("FOREIGN_EXCHANGE");

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
