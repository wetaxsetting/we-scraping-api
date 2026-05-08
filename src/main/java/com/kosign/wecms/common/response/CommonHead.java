package com.kosign.wecms.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Status block of every {@link ApiResponse}. Wire shape:
 * <pre>{@code { "error": false, "code": "WBI0000", "message": "..." }}</pre>
 *
 * Java field names already match the wire (lowercase) so no annotations
 * are needed — the global SNAKE_CASE strategy is a no-op for these.
 */
@Getter
@AllArgsConstructor
public class CommonHead {

    private final boolean error;

    private final String code;

    private final String message;

    public static CommonHead success() {
        return new CommonHead(false, "WBI0000", "Success");
    }

    public static CommonHead success(String message) {
        return new CommonHead(false, "WBI0000", message);
    }

    public static CommonHead error(String code, String message) {
        return new CommonHead(true, code, message);
    }
}
