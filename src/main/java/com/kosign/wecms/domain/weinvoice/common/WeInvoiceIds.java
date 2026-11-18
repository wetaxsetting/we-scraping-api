package com.kosign.wecms.domain.weinvoice.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Wire identifiers. WeInvoice ids travel as strings (UI Spec {@code lib/types.ts} declares
 * {@code id: string}); internally they are the {@code bigint} / {@code numeric(18)} keys.
 * Tax codes (MST) are 10 digits, optionally followed by a 3-digit branch suffix.
 */
public final class WeInvoiceIds {

    private static final Pattern NUMERIC_ID = Pattern.compile("\\d{1,18}");
    private static final Pattern TAX_ID = Pattern.compile("\\d{10}(-\\d{3})?");

    private WeInvoiceIds() {
    }

    /** Parses a numeric id; malformed → {@code IllegalArgumentException} (→ {@code WBE_VALIDATION}). */
    public static long parse(String id, String field) {
        if (id == null || !NUMERIC_ID.matcher(id.trim()).matches()) {
            throw new IllegalArgumentException(field + ": invalid id");
        }
        return Long.parseLong(id.trim());
    }

    public static List<Long> parseAll(Collection<String> ids, String field) {
        List<Long> out = new ArrayList<>(ids == null ? 0 : ids.size());
        if (ids != null) {
            for (String id : ids) out.add(parse(id, field));
        }
        return out;
    }

    public static boolean isTaxId(String value) {
        return value != null && TAX_ID.matcher(value.trim()).matches();
    }

    /** Trimmed tax code, or {@code WBE3001} when it is not 10 / 10-3 digits. */
    public static String requireTaxId(String value) {
        if (!isTaxId(value)) {
            throw new com.kosign.wecms.common.exception.BusinessException("WBE3001");
        }
        return value.trim();
    }
}
