package com.kosign.wecms.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank
        @Size(max = 50)
        @JsonProperty("USER_ID") String userId,

        @NotBlank
        @Size(max = 100)
        @JsonProperty("USER_PW") String userPw
) {
    public LoginRequest {
        if (userId != null) userId = userId.trim();
        if (userPw != null) userPw = userPw.trim();
    }
}
