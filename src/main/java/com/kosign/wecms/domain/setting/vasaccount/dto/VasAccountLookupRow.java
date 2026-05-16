package com.kosign.wecms.domain.setting.vasaccount.dto;

/**
 * Lightweight row for the VAS Account typeahead picker.
 *
 * <p>Mirrors legacy {@code comm_1002_001} — the WSVC that populates the
 * "VAS Account" select on the Edit Bank Account dialog
 * ({@code comm_0183_003_view.jsp}). The picker only needs three fields:
 * the FK ({@code vasAcctSeqNo}), the code ({@code vasAcctCd}) shown
 * primary, and the display name ({@code vasAcctNm}) shown secondary.
 *
 * <p>Returned exclusively from {@code GET
 * /api/v1/settings/vas-account/lookup} — distinct from the full
 * {@link VasAccountRow} list endpoint so a typing user doesn't pull
 * status / audit / description fields they never render. Filtered to
 * {@code STTS='A'} + {@code DEL_YN='N'} server-side: inactive rows are
 * never offered as a new mapping target.
 *
 * <p>{@code vasAcctSeqNo} is a {@code Long} on the wire (matches the
 * raw legacy {@code NUMERIC(10,0)} PK). The full-row endpoint ships it
 * as a String to dodge JS number precision; this lookup payload keeps
 * the numeric type because the consumer never round-trips it through a
 * URL — it's stamped onto a PATCH body and forwarded to the backend
 * unchanged.
 */
public record VasAccountLookupRow(
        Long   vasAcctSeqNo,
        String vasAcctCd,
        String vasAcctNm
) { }
