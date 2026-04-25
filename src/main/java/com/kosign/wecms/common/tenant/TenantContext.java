package com.kosign.wecms.common.tenant;

import com.kosign.wecms.common.enums.LangCd;

/**
 * Request-scoped holder for the authenticated caller's tenant state, populated
 * by {@link com.kosign.wecms.common.security.JwtAuthenticationFilter} from the
 * JWT claims and cleared at the end of the request.
 *
 * <p>Sprint 0 scope is single-tenant ({@code wecms.tenant.biz-key =
 * VN_VNM0004_000001}) so the {@code bizKey} held here always equals the
 * configured tenant for successfully authenticated requests. The holder still
 * exists so that as additional {@code domain/} packages are added, they can
 * read the caller's tenant without threading it through every method
 * signature, and so a future multi-tenant aspect has a single place to wire
 * into.
 *
 * <p>Implementation is a plain {@link ThreadLocal} — the JWT filter sets on
 * entry and clears in a {@code finally} block. Do not leak access beyond the
 * request lifecycle (e.g. async executors without
 * {@link InheritableThreadLocal}-style propagation).
 */
public final class TenantContext {

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private TenantContext() { }

    public static void set(String userId, String bizKey, LangCd langCd) {
        HOLDER.set(new Principal(userId, bizKey, langCd));
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static Principal current() {
        return HOLDER.get();
    }

    public static String currentUserId() {
        Principal p = HOLDER.get();
        return p == null ? null : p.userId();
    }

    public static String currentBizKey() {
        Principal p = HOLDER.get();
        return p == null ? null : p.bizKey();
    }

    public static LangCd currentLangCd() {
        Principal p = HOLDER.get();
        return p == null ? null : p.langCd();
    }

    public record Principal(String userId, String bizKey, LangCd langCd) { }
}
