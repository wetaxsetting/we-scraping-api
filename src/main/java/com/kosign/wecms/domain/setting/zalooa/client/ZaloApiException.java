package com.kosign.wecms.domain.setting.zalooa.client;

import lombok.Getter;

/**
 * Raised when a Zalo call cannot produce a usable answer at all — transport
 * failure, non-2xx status, empty body, unparseable body, or a token grant Zalo
 * refused.
 *
 * <p><b>Not used for a failed send.</b> A send that Zalo answers with a non-zero
 * {@code error} is a business outcome and comes back as {@link ZaloSendOutcome},
 * because it still has to be written to {@code ZALO_SEND_HIS}. Throwing there
 * would push the log write into a {@code catch} block and risk losing the row —
 * which is how the reference implementation ends up with failure rows that have
 * no recipient number recorded (finding §10.5).
 *
 * <p>Service code catches this and rethrows a {@code BusinessException} with a
 * {@code WBE} code, so no vendor text reaches the user-facing envelope.
 */
@Getter
public class ZaloApiException extends RuntimeException {

    /** Zalo's {@code error} value where one was parseable, otherwise {@code null}. */
    private final Integer zaloErrorCode;

    public ZaloApiException(String message) {
        this(message, null, null);
    }

    public ZaloApiException(String message, Integer zaloErrorCode) {
        this(message, zaloErrorCode, null);
    }

    public ZaloApiException(String message, Integer zaloErrorCode, Throwable cause) {
        super(message, cause);
        this.zaloErrorCode = zaloErrorCode;
    }
}
