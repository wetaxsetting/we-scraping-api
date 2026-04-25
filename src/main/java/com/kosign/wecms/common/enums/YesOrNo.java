package com.kosign.wecms.common.enums;

/**
 * Legacy JEX boolean flag stored as a single char ({@code 'Y'} / {@code 'N'}).
 * Used across USER_INFM, BUSI_INFM, USER_BUSI_CONN_INFM for DEL_YN / ACTV_YN /
 * LOCK_YN / OVERDUE_YN etc. Not applied as a JPA {@code @Converter} yet — the
 * Sprint 0 entities still map these as {@code String} to mirror the legacy
 * schema column-for-column. Use these helpers at the service layer for clarity
 * instead of raw string comparisons.
 */
public enum YesOrNo {
    Y,
    N;

    public String code() {
        return name();
    }

    public boolean isYes() {
        return this == Y;
    }

    public static boolean isY(String raw) {
        return "Y".equals(raw);
    }

    public static boolean isN(String raw) {
        return "N".equals(raw);
    }
}
