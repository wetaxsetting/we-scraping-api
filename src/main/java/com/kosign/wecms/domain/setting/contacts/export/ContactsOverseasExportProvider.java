package com.kosign.wecms.domain.setting.contacts.export;

import com.kosign.wecms.domain.setting.contacts.service.ContactsService;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Settings &gt; Contacts (Overseas) — Excel export.
 *
 * <p>Binds {@code screenId = "settings-contacts-overseas"} and
 * {@code TRCO_TYPE = 'O'}. Column set mirrors legacy
 * {@code comm_0184_001_r006_excel_view.jsp} Overseas sheet (11 columns):
 * Contact Code · Contact Name · Tax ID · Beneficiary Name · Beneficiary
 * Bank Name · Bank Identifier Code (BIC) · Beneficiary Account No. ·
 * Beneficiary Address · Current Address · Old Address · Valid Date.
 */
@Component
public class ContactsOverseasExportProvider extends ContactsExportProvider {

    private static final Set<String> ALLOWED = Set.of(
            CONTACT_CODE,
            CONTACT_NAME,
            TAX_ID,
            BENEFICIARY_NAME,
            BENEFICIARY_BANK_NAME,
            BIC_CODE,
            BENEFICIARY_ACCOUNT_NO,
            BENEFICIARY_ADDRESS,
            CURRENT_ADDRESS,
            OLD_ADDRESS,
            VALID_DATE
    );

    public ContactsOverseasExportProvider(ContactsService contactsService) {
        super(contactsService);
    }

    @Override
    public String screenId() {
        return "settings-contacts-overseas";
    }

    @Override
    protected String contactType() {
        return "O";
    }

    @Override
    public Set<String> allowedFieldIds() {
        return ALLOWED;
    }
}
