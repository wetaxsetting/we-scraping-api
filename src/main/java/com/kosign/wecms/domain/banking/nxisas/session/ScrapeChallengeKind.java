package com.kosign.wecms.domain.banking.nxisas.session;

/**
 * Kind of user-interaction the gateway is requesting between two
 * scraping steps. Mapped from the legacy {@code CallBackfunc} string
 * — {@code "setOTP"} → {@link #OTP}, {@code "setCaptcha"} →
 * {@link #CAPTCHA}, {@code "setQNA"} → {@link #QNA}.
 */
public enum ScrapeChallengeKind {
    OTP,
    CAPTCHA,
    QNA
}
