package com.kosign.wecms.domain.common.country.dto;

/**
 * Single country in the {@code /api/v1/common/countries} list response.
 *
 * <p>Wire shape mirrors the legacy JEX {@code comm_0110_001_r001}
 * subRule — three fields: {@code ntnl_cd}, {@code natl_nm},
 * {@code natn_num}, plus the {@code default_yn} flag computed
 * server-side (legacy IDO does the same via
 * {@code CASE WHEN NTNL_CD='VN' THEN 'Y' ELSE 'N' END}).
 *
 * <p>Java fields stay camelCase; the global Jackson
 * {@code SNAKE_CASE} strategy converts to lowercase snake_case on
 * the wire. Frontend never sees the legacy UPPER_SNAKE_CASE.
 */
public record CountryRow(

        /** ISO 3166-1 alpha-2 code — stable per-country key (e.g. {@code "VN"}). */
        String ntnlCd,

        /** Display name in English (e.g. {@code "Vietnam"}). */
        String natlNm,

        /** Dial code WITHOUT the leading {@code '+'} (e.g. {@code "84"}). */
        String natnNum,

        /** {@code "Y"} for the default country (Vietnam — project tenant),
         *  {@code "N"} otherwise. Frontend uses this to highlight the
         *  initial selection when no user value is bound. */
        String defaultYn

) { }
