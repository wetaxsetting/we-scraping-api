package com.kosign.wecms.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    @JsonProperty("COMMON_HEAD")
    private final CommonHead commonHead;

    @JsonProperty("REC")
    private final T rec;

    private ApiResponse(CommonHead commonHead, T rec) {
        this.commonHead = commonHead;
        this.rec = rec;
    }

    public static <T> ApiResponse<T> ok(T rec) {
        return new ApiResponse<>(CommonHead.success(), rec);
    }

    public static <T> ApiResponse<T> ok(String message, T rec) {
        return new ApiResponse<>(CommonHead.success(message), rec);
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(CommonHead.error(code, message), null);
    }
}
