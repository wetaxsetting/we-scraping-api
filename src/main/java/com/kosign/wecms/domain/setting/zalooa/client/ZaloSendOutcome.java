package com.kosign.wecms.domain.setting.zalooa.client;

/**
 * Result of one template-message call, success or business failure alike.
 *
 * <p><b>A non-zero {@link #errorCode()} is returned, not thrown.</b> Zalo answers
 * HTTP 200 with {@code {"error": -108, "message": "…"}} for outcomes that are
 * business facts rather than faults — user has not interacted with the OA, quota
 * exhausted, template not approved, recipient blocked the OA. Every one of those
 * must still produce a {@code ZALO_SEND_HIS} row, so the client hands them back
 * and lets {@code ZaloMessageService} log then decide. Only transport faults and
 * a missing/unparseable body throw {@link ZaloApiException}.
 *
 * <p>The reference implementation reads {@code body.get("error").asInt()} with no
 * null check (finding §10.4): a body without {@code error} throws NPE, which its
 * caller then records as a fake HTTP-500 carrying the NPE's message instead of
 * the real failure. {@link RestClientZaloApiClient} checks for the field's
 * presence and reports its absence honestly.
 *
 * @param errorCode   Zalo's {@code error} value. {@code 0} means delivered to Zalo.
 * @param message     Zalo's {@code message} text, or a local description.
 * @param rawResponse the response body verbatim, persisted to
 *                    {@code ZALO_SEND_HIS.RES_DATA} so support can read what Zalo
 *                    actually said rather than our interpretation of it.
 */
public record ZaloSendOutcome(
        int errorCode,
        String message,
        String rawResponse
) {

    /** Zalo's success sentinel. */
    public static final int SUCCESS_CODE = 0;

    public boolean success() {
        return errorCode == SUCCESS_CODE;
    }
}
