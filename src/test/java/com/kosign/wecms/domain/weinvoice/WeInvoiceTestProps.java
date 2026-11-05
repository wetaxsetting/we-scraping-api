package com.kosign.wecms.domain.weinvoice;

import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;

import java.util.Base64;

/** {@link WeInvoiceProperties} fixtures for unit tests (nothing connects: the pool is never used). */
public final class WeInvoiceTestProps {

    public static final String KEY_B64 = Base64.getEncoder().encodeToString(new byte[32]);

    private WeInvoiceTestProps() {
    }

    public static WeInvoiceProperties enabled(String partnerKeys, int maxCompanies) {
        return new WeInvoiceProperties(true,
                new WeInvoiceProperties.Db("jdbc:postgresql://localhost:5432/weinvoice", "u", "p", 1),
                "public", "weinvoice", "build/tmp/engine-out", "build/tmp/weinvoice-store", KEY_B64,
                partnerKeys, "invoice.example.vn", maxCompanies, false);
    }

    public static WeInvoiceProperties disabled() {
        return new WeInvoiceProperties(false, null, "${X}", "${Y}", "", "", "", "", "", 5, false);
    }
}
