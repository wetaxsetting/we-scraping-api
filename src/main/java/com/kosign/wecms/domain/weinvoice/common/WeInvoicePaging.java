package com.kosign.wecms.domain.weinvoice.common;

/**
 * Lenient paging, same contract as the WeCMS list endpoints: zero-based {@code page}, negative
 * clamped to 0; {@code size <= 0} → default, above the maximum → the maximum.
 */
public final class WeInvoicePaging {

    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 200;

    private WeInvoicePaging() {
    }

    public static int page(int page) {
        return Math.max(0, page);
    }

    public static int size(int size) {
        if (size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }
}
