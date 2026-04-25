package com.kosign.wecms.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoginResponse(
        @JsonProperty("TOKEN")        String token,
        @JsonProperty("TOKEN_TYPE")   String tokenType,
        @JsonProperty("EXPIRES_IN")   long   expiresIn,
        @JsonProperty("USER_ID")      String userId,
        @JsonProperty("USER_NM")      String userNm,
        @JsonProperty("BIZ_KEY")      String bizKey,
        @JsonProperty("LANG_CD")      String langCd,
        @JsonProperty("CMPN_NM")      String cmpnNm
) { }
