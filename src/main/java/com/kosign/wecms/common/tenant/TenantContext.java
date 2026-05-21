package com.kosign.wecms.common.tenant;

import com.kosign.wecms.common.enums.LangCd;

/**
 * Request-scoped holder for the authenticated caller's tenant state, populated
 * by {@link com.kosign.wecms.common.security.JwtAuthenticationFilter} from the
 * JWT claims and cleared at the end of the request.
 *
 * <p>The {@code bizKey} held here is the BIZ_KEY the caller selected at
 * login time (auto-picked when the user had exactly one active
 * USER_BUSI_CONN_INFM link, or chosen explicitly through the company-chooser
 * step). The frontend cannot override it on a normal request — the value
 * rides in the signed JWT, not in any header or body, and the filter reads
 * it from the JWT claim.
 *
 * <p>{@code authGrCd} is {@code "ADMIN"} when the user has a
 * {@code BUSI_ADMN_INFM} row for the selected company, {@code "USER"}
 * otherwise. Legacy {@code USER_BUSI_CONN_INFM} carries no authority
 * column (confirmed on DEV 2026-05-11), so the admin-master table is the
 * sole source. Authority is per-link: the same user may be ADMIN at one
 * company and USER at another.
 *
 * <p>{@code prdtType} is the verbatim {@code BUSI_INFM.PRDT_TYPE} letter
 * for the selected company, stamped into the JWT at login finalize.
 * The refactor recognises {@code F} (WeCMS default), {@code M}
 * (WeCMS Lumens) and {@code H} (HAENGSUNG Electronics) explicitly via
 * {@link com.kosign.wecms.common.enums.PrdtType}; legacy DEV stores 11
 * additional letters which are carried verbatim but not modelled
 * (see {@code wecms-api/CLAUDE.md §Product-type rule}). Domain
 * services that need to gate behaviour on product type MUST read from
 * {@link #currentPrdtType()} — never from a request body or query
 * parameter.
 *
 * <p>Implementation is a plain {@link ThreadLocal} — the JWT filter sets on
 * entry and clears in a {@code finally} block. Do not leak access beyond the
 * request lifecycle (e.g. async executors without
 * {@link InheritableThreadLocal}-style propagation).
 */
public final class TenantContext {

    /** {@link Principal#authGrCd()} value for company admins. */
    public static final String AUTH_GR_ADMIN = "ADMIN";
    /** {@link Principal#authGrCd()} value for non-admin users. */
    public static final String AUTH_GR_USER  = "USER";

    private static final ThreadLocal<Principal> HOLDER = new ThreadLocal<>();

    private TenantContext() { }

    /** Convenience overload for early call sites that don't yet have
     *  display name, authority, or product type. Defaults: userNm =
     *  userId, authGrCd = USER, prdtType = null. New callers should
     *  prefer the full-form setter. */
    public static void set(String userId, String bizKey, LangCd langCd) {
        set(userId, userId, bizKey, langCd, AUTH_GR_USER, null);
    }

    /** Convenience overload for call sites that carry display name but
     *  not authority or product type (legacy test fixtures + early
     *  audit-log call sites). Defaults authGrCd to USER, prdtType to
     *  null — production call sites should use the six-arg setter so
     *  the JWT claim and the in-memory context agree. */
    public static void set(String userId, String userNm, String bizKey, LangCd langCd) {
        set(userId, userNm, bizKey, langCd, AUTH_GR_USER, null);
    }

    /** Convenience overload for the post-authority pre-product-type
     *  era (existing JwtAuthenticationFilter tests). Defaults
     *  prdtType to null. */
    public static void set(String userId, String userNm, String bizKey, LangCd langCd, String authGrCd) {
        set(userId, userNm, bizKey, langCd, authGrCd, null);
    }

    /** Full-form setter — populates display name, authority, AND
     *  product type. {@link com.kosign.wecms.common.security.JwtAuthenticationFilter}
     *  uses this overload, reading the {@code userNm},
     *  {@code authGrCd} and {@code prdtType} claims that
     *  {@link com.kosign.wecms.common.security.JwtTokenProvider#issue}
     *  writes at login finalize. {@code prdtType} is the verbatim DB
     *  letter (any of the 14 legacy values); {@link #currentPrdtType()}
     *  exposes it unchanged. */
    public static void set(String userId, String userNm, String bizKey, LangCd langCd,
                           String authGrCd, String prdtType) {
        HOLDER.set(new Principal(userId, userNm, bizKey, langCd,
                                 normaliseAuth(authGrCd), normalisePrdt(prdtType)));
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

    /** Display name of the current caller (legacy {@code WbUser.getName()}
     *  parity — used by the audit-log writer's {@code REG_NM} column,
     *  by the firm-banking-settings {@code LAST_EDTR} column, etc.).
     *  Falls back to {@code userId} when the JWT carried no
     *  {@code userNm} claim — never returns null for an authenticated
     *  request. */
    public static String currentUserNm() {
        Principal p = HOLDER.get();
        return p == null ? null : p.userNm();
    }

    public static String currentBizKey() {
        Principal p = HOLDER.get();
        return p == null ? null : p.bizKey();
    }

    public static LangCd currentLangCd() {
        Principal p = HOLDER.get();
        return p == null ? null : p.langCd();
    }

    /** Authority group code for the current caller within the active
     *  company — {@link #AUTH_GR_ADMIN} or {@link #AUTH_GR_USER}.
     *  Returns null only when no request context is bound (background
     *  thread, startup, etc.). */
    public static String currentAuthGrCd() {
        Principal p = HOLDER.get();
        return p == null ? null : p.authGrCd();
    }

    /** Convenience for service-layer admin-only guards once method
     *  security is wired up (follow-up MR). */
    public static boolean currentIsAdmin() {
        return AUTH_GR_ADMIN.equals(currentAuthGrCd());
    }

    /** Verbatim {@code BUSI_INFM.PRDT_TYPE} letter for the active
     *  company. May be any of the legacy single-character codes; may
     *  be null when no request context is bound or when the company
     *  row carried a NULL value. Domain services that need to branch
     *  on a recognised product type should call
     *  {@link com.kosign.wecms.common.enums.PrdtType#fromCode}
     *  rather than comparing to literal strings — that contains the
     *  list of values the refactor explicitly supports and returns
     *  {@code Optional.empty()} for any deferred legacy letter so
     *  gates degrade safely. */
    public static String currentPrdtType() {
        Principal p = HOLDER.get();
        return p == null ? null : p.prdtType();
    }

    private static String normaliseAuth(String authGrCd) {
        return AUTH_GR_ADMIN.equals(authGrCd) ? AUTH_GR_ADMIN : AUTH_GR_USER;
    }

    private static String normalisePrdt(String prdtType) {
        if (prdtType == null) return null;
        String trimmed = prdtType.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record Principal(String userId, String userNm, String bizKey, LangCd langCd,
                            String authGrCd, String prdtType) { }
}
