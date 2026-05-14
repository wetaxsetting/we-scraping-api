package com.kosign.wecms.domain.setting.logincredentials.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Re-auth payload for the {@code POST /{type}/{seqNo}/reveal} endpoint.
 * The user re-enters their own WeCMS login password to confirm intent
 * before the server decrypts and returns the stored credential.
 */
public record RevealRequest(
        @NotBlank @Size(max = 100) String userPassword
) { }
