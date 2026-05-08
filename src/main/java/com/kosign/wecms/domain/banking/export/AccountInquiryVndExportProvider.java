package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.domain.banking.service.AccountService;
import org.springframework.stereotype.Component;

/**
 * Banking &gt; Account Inquiry (VND) — Excel export.
 *
 * <p>Binds {@code screenId = "banking-account-inquiry-vnd"} and
 * defaults {@code filters.currency} to {@code "VND"}. Subheader is the
 * legacy VND format (count + Balance + Available Balance) inherited
 * from {@link AccountInquiryExportProvider#composeSubHeader}.
 */
@Component
public class AccountInquiryVndExportProvider extends AccountInquiryExportProvider {

    public AccountInquiryVndExportProvider(AccountService accountService) {
        super(accountService);
    }

    @Override
    public String screenId() {
        return "banking-account-inquiry-vnd";
    }

    @Override
    protected String defaultCurrency() {
        return "VND";
    }
}
