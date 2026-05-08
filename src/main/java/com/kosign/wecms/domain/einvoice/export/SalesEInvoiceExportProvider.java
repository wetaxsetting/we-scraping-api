package com.kosign.wecms.domain.einvoice.export;

import com.kosign.wecms.domain.einvoice.service.EInvoiceService;
import org.springframework.stereotype.Component;

/**
 * Add-on Service &gt; Sales e-Invoices — Excel export.
 *
 * <p>Binds {@code screenId = "addon-sales-einvoices"} and defaults
 * {@code filters.type} to {@code "SALES"} (mapped server-side to
 * legacy {@code DOC_TYPE='I'} per rule IR-1). Subheader format is
 * inherited from {@link EInvoiceExportProvider#composeSubHeader} —
 * one line per CCY group with count + total + tax + pretax.
 */
@Component
public class SalesEInvoiceExportProvider extends EInvoiceExportProvider {

    public SalesEInvoiceExportProvider(EInvoiceService eInvoiceService) {
        super(eInvoiceService);
    }

    @Override
    public String screenId() {
        return "addon-sales-einvoices";
    }

    @Override
    protected String defaultType() {
        return "SALES";
    }
}
