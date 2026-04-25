package com.kosign.wecms.common.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonHead {

    @JsonProperty("ERROR")
    private final boolean error;

    @JsonProperty("CODE")
    private final String code;

    @JsonProperty("MESSAGE")
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
