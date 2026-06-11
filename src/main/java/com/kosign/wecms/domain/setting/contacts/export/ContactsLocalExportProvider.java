package com.kosign.wecms.domain.setting.contacts.export;

import com.kosign.wecms.domain.setting.contacts.service.ContactsService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Settings &gt; Contacts (Local) — Excel export.
 *
 * <p>Binds {@code screenId = "settings-contacts-local"} and
 * {@code TRCO_TYPE = 'L'}. Column set mirrors legacy
 * {@code comm_0184_001_r006_excel_view.jsp} Local sheet (9 columns):
 * Contact Code · Contact Name · Tax ID · Deposit Bank · Deposit Account
 * No. · Currency · Current Address · Old Address · Valid Date.
 */
@Component
public class ContactsLocalExportProvider extends ContactsExportProvider {

    private static final Set<String> ALLOWED = Set.of(
            CONTACT_CODE,
            CONTACT_NAME,
            TAX_ID,
            DEPOSIT_BANK,
            DEPOSIT_ACCOUNT_NO,
            CURRENCY,
            CURRENT_ADDRESS,
            OLD_ADDRESS,
            VALID_DATE
    );

    public ContactsLocalExportProvider(ContactsService contactsService) {
        super(contactsService);
    }

    @Override
    public String screenId() {
        return "settings-contacts-local";
    }

    @Override
    protected String contactType() {
        return "L";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED;
    }
}
