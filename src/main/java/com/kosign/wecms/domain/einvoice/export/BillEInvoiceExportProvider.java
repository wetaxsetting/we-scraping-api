package com.kosign.wecms.domain.einvoice.export;

import com.kosign.wecms.domain.einvoice.service.EInvoiceService;
import org.springframework.stereotype.Component;

/**
 * Add-on Service &gt; Bill / Expense e-Invoices — Excel export.
 *
 * <p>Binds {@code screenId = "addon-expenses-einvoices"} and defaults
 * {@code filters.type} to {@code "EXPENSE"} (mapped server-side to
 * legacy {@code DOC_TYPE='B'} per rule IR-1 — note the legacy enum
 * constant {@code BusaDocType.BILL='B'}, NOT {@code EXPENSE='E'}).
 *
 * <p>The screenId uses the kebab-case slug {@code expenses-einvoices}
 * matching the frontend route at {@code /addon/expenses-einvoices}.
 */
@Component
public class BillEInvoiceExportProvider extends EInvoiceExportProvider {

    public BillEInvoiceExportProvider(EInvoiceService eInvoiceService) {
        super(eInvoiceService);
    }

    @Override
    public String screenId() {
        return "addon-expenses-einvoices";
    }

    @Override
    protected String defaultType() {
        return "EXPENSE";
    }
}
