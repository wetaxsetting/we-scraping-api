package com.kosign.wecms.domain.auth.bootstrap;

/**
 * Outcome of a bootstrap call. {@code created=true} means the 5-table chain
 * was written under one transaction; {@code created=false} means the BIZ_KEY
 * already existed (idempotent no-op).
 */
public record BootstrapResult(
        boolean created,
        String bizKey,
        String userId,
        String emplNo
) {
    public static BootstrapResult created(String bizKey, String userId, String emplNo) {
        return new BootstrapResult(true, bizKey, userId, emplNo);
    }

    public static BootstrapResult alreadyExists(String bizKey) {
        return new BootstrapResult(false, bizKey, null, null);
    }
}
