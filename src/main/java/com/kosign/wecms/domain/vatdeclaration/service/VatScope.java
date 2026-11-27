package com.kosign.wecms.domain.vatdeclaration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Whose declarations a request works on — the value stored in {@code VAT_DECL.BIZ_KEY}.
 *
 * <ul>
 *   <li><b>WeCMS</b>: the tenant {@code BIZ_KEY}; company name / tax code are read from {@code BUSI_INFM} at
 *       create, invoices from the WeCMS e-invoice tables.</li>
 *   <li><b>WeInvoice</b>: one book per (signed-in user, MST) — WeInvoice data is owned by the user, not by a
 *       tenant, and self-signup users all share the sentinel {@code bizKey = WEINVOICE}. The key is
 *       {@code WI:} + 17 hex of SHA-256({@code ownerId|mst}), exactly the 20-character {@code BIZ_KEY} width, so the
 *       existing tables, unique index and period-chain rules apply per (user, MST) without DDL. Invoices come
 *       from the engine tables of that MST.</li>
 * </ul>
 *
 * Record: docs/features/addon-service/weinvoice/weinvoice-vat-declaration-engine-invoices-2026-11-25.md.
 */
public record VatScope(String key, String companyName, String taxId) {

    /** Prefix of a WeInvoice book key; a real WeCMS {@code BIZ_KEY} ({@code VN_VNM0004_000001}) never has a colon. */
    public static final String WEINVOICE_PREFIX = "WI:";

    private static final int KEY_HASH_LEN = 17;

    /** WeCMS tenant scope (company info resolved from {@code BUSI_INFM}). */
    public static VatScope wecms(String bizKey) {
        return new VatScope(bizKey, null, null);
    }

    /** WeInvoice scope of {@code mst} for {@code ownerId}; the caller has already checked the user manages the MST. */
    public static VatScope weinvoice(String ownerId, String mst, String companyName) {
        return new VatScope(weinvoiceKey(ownerId, mst), companyName == null ? "" : companyName, mst);
    }

    public static String weinvoiceKey(String ownerId, String mst) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((ownerId + "|" + mst).getBytes(StandardCharsets.UTF_8));
            return WEINVOICE_PREFIX + HexFormat.of().formatHex(digest).substring(0, KEY_HASH_LEN);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** True when a stored {@code BIZ_KEY} belongs to a WeInvoice book (invoices come from the engine). */
    public static boolean isWeInvoiceKey(String key) {
        return key != null && key.startsWith(WEINVOICE_PREFIX);
    }
}
