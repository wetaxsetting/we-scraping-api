package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.common.i18n.RelativeTimeFormatter;
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

    public AccountInquiryVndExportProvider(AccountService accountService,
                                           RelativeTimeFormatter relativeTimeFormatter) {
        super(accountService, relativeTimeFormatter);
    }

    @Override
    public String screenId() {
        return "banking-account-inquiry-vnd";
    }

    @Override
    protected String defaultCurrency() {
        return "VND";
    }

    /** Account Inquiry (VND) → {@code TRNS_TYPE_CD = "09"} (legacy
     *  {@code downloadProc.jsp:1373}, {@code fnsh_0001_001} branch). */
    @Override
    protected String erpExportTrnsTypeCd() {
        return "09";
    }
}
