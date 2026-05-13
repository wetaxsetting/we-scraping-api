package com.kosign.wecms.domain.setting.contacts.dto;

/**
 * One address sub-row inside a contact's list / detail response.
 *
 * <p><b>OQ-8 resolved 2026-05-12 &mdash;</b> {@code disp_ord} is the
 * current / previous discriminator ({@code 1} = current,
 * {@code 2} = previous). {@code trco_addr_key} is a generated
 * technical key only; never compare it to literal {@code "1"} /
 * {@code "2"} for business meaning.
 *
 * <p>Date fields ({@code issu_dt}, {@code due_dt}) stay in legacy
 * {@code yyyyMMdd} string form on the wire so the frontend can render
 * them with its own locale formatter; conversion to ISO-8601 is a
 * downstream concern, not the API's.
 */
public record ContactAddressRow(
        String trcoAddrKey,
        String trcoBsunPostAdrs,
        Integer dispOrd,
        String issuDt,
        String dueDt) { }
