package com.kosign.wecms.domain.setting.logincredentials.dto;

/**
 * Decrypted credential surfaced after a successful re-auth. The
 * password field is the only path on which the password ciphertext is
 * decrypted and returned over the wire — every other endpoint returns
 * only the login id.
 */
public record RevealResponse(
        String loginId,
        String password
) { }
