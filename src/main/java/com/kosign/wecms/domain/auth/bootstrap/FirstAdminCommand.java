package com.kosign.wecms.domain.auth.bootstrap;

/**
 * Inputs for creating the first platform admin of a freshly bootstrapped
 * tenant. Caller is responsible for choosing a stable, human-readable
 * {@code userId}; the password is hashed via
 * {@link com.kosign.wecms.common.security.PasswordHashService} before any
 * write reaches USER_INFM.
 */
public record FirstAdminCommand(
        String userId,
        String userNm,
        String rawPassword,
        String eml,
        String clphNo
) { }
