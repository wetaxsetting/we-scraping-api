package com.kosign.wecms.domain.setting.contacts.export;

import com.kosign.wecms.common.export.ExcelExportRequest;
import com.kosign.wecms.common.export.ExportFetch;
import com.kosign.wecms.common.export.ExportRowProvider;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAccountRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactAddressRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactOverseasRow;
import com.kosign.wecms.domain.setting.contacts.dto.ContactRow;
import com.kosign.wecms.domain.setting.contacts.service.ContactsService;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Shared Excel-export logic for Settings &gt; Contacts (Local /
 * Overseas). Reuses {@link ContactsService#listForExport} so the export
 * and the on-screen list draw from one query path — same tenant scope
 * ({@code BIZ_KEY}), same active-only filter ({@code trco_stts<>'C'}),
 * same {@code TRCO_TYPE} + keyword filtering. No second source of truth.
 *
 * <p>Mirrors legacy {@code comm_0184_001_r006_excel_view.jsp}: a row per
 * active contact of the chosen type, the primary deposit account /
 * overseas beneficiary, and the current / old address split (legacy
 * {@code TRCO_ADDR_KEY} 1 = current, 2 = old) with the old address's
 * valid-till date formatted {@code dd-MM-yyyy}.
 *
 * <p>Subclasses bind the concrete {@link #screenId()} +
 * {@link #contactType()} ({@code 'L'} / {@code 'O'}) — the type is a
 * server-bound constant, never user input, exactly like
 * {@code AccountInquiryExportProvider#defaultCurrency()}. Each subclass
 * also declares its own {@link #allowedFieldIds()} (the legacy column
 * set for that type), seeded 1:1 in {@code SYS_SCR_OPTION_WECMS} so the
 * shared {@code ExcelExportService} visibility intersection passes.
 *
 * <p>All FIELD_IDs are semantic refactor names (e.g.
 * {@code CONTACT_CODE}, {@code DEPOSIT_BANK}) that resolve to
 * themselves through {@link com.kosign.wecms.common.export.FieldIdAlias}
 * — deliberately avoiding aliased legacy ids ({@code BANK_NM}→{@code BANK},
 * {@code TRCO_CMNM}→{@code BUYER}) so the seed / provider allow-list /
 * frontend request / row-map keys all speak one key space.
 */
@RequiredArgsConstructor
public abstract class ContactsExportProvider implements ExportRowProvider {

    /* Semantic FIELD_IDs — shared with the V247 seed + the frontend
     * column lists in lib/api/contacts.ts. Identity-resolved by
     * FieldIdAlias (none collide with an alias key). */
    static final String CONTACT_CODE          = "CONTACT_CODE";
    static final String CONTACT_NAME          = "CONTACT_NAME";
    static final String TAX_ID                = "TAX_ID";
    static final String DEPOSIT_BANK          = "DEPOSIT_BANK";
    static final String DEPOSIT_ACCOUNT_NO    = "DEPOSIT_ACCOUNT_NO";
    static final String CURRENCY              = "CURRENCY";
    static final String CURRENT_ADDRESS       = "CURRENT_ADDRESS";
    static final String OLD_ADDRESS           = "OLD_ADDRESS";
    static final String VALID_DATE            = "VALID_DATE";
    static final String BENEFICIARY_NAME      = "BENEFICIARY_NAME";
    static final String BENEFICIARY_BANK_NAME = "BENEFICIARY_BANK_NAME";
    static final String BIC_CODE              = "BIC_CODE";
    static final String BENEFICIARY_ACCOUNT_NO = "BENEFICIARY_ACCOUNT_NO";
    static final String BENEFICIARY_ADDRESS   = "BENEFICIARY_ADDRESS";

    protected final ContactsService contactsService;

    /** {@code 'L'} (Local) or {@code 'O'} (Overseas) — server-bound. */
    protected abstract String contactType();

    @Override
    public ExportFetch fetch(ExcelExportRequest request) {
        Map<String, Object> filters = request.filters() != null ? request.filters() : Map.of();
        String keyword = stringOrNull(filters.get("keyword"));

        List<ContactRow> rows = contactsService.listForExport(keyword, contactType());

        List<Map<String, Object>> mapped = new ArrayList<>(rows.size());
        for (ContactRow r : rows) {
            mapped.add(toFieldMap(r));
        }
        String subHeader = String.format(Locale.ROOT, "%d contact(s)", mapped.size());
        return ExportFetch.of(mapped, subHeader);
    }

    /** Map one projected contact to the full FIELD_ID → value map. Each
     *  subclass's {@code allowedFieldIds()} + the per-type seed decide
     *  which of these actually emit, so populating both the Local and
     *  Overseas fields here is harmless (unused keys are dropped). */
    private static Map<String, Object> toFieldMap(ContactRow r) {
        Map<String, Object> m = new HashMap<>();
        m.put(CONTACT_CODE, nullToEmpty(r.handTrcoKey()));
        m.put(CONTACT_NAME, nullToEmpty(r.trcoCmnm()));
        m.put(TAX_ID, nullToEmpty(r.trcoTaxId()));

        // Primary deposit account (lowest DISP_ORD — projectRow already
        // sorted the list).
        ContactAccountRow acct = first(r.accounts());
        m.put(DEPOSIT_BANK, acct != null ? nullToEmpty(acct.bankNm()) : "");
        m.put(DEPOSIT_ACCOUNT_NO, acct != null ? nullToEmpty(acct.acctNo()) : "");
        m.put(CURRENCY, acct != null ? nullToEmpty(acct.acctCcyCd()) : "");

        // Primary overseas beneficiary.
        ContactOverseasRow ovrs = first(r.overseasBeneficiaries());
        m.put(BENEFICIARY_NAME, ovrs != null ? nullToEmpty(ovrs.beneName()) : "");
        m.put(BENEFICIARY_BANK_NAME, ovrs != null ? nullToEmpty(ovrs.beneBankName()) : "");
        m.put(BIC_CODE, ovrs != null ? nullToEmpty(ovrs.bicCode()) : "");
        m.put(BENEFICIARY_ACCOUNT_NO, ovrs != null ? nullToEmpty(ovrs.beneAcctNo()) : "");
        m.put(BENEFICIARY_ADDRESS, ovrs != null ? nullToEmpty(ovrs.beneAddr()) : "");

        // Address split: DISP_ORD 1 = current, 2 = old (OQ-8).
        ContactAddressRow current = addressByDispOrd(r.addresses(), 1);
        ContactAddressRow old = addressByDispOrd(r.addresses(), 2);
        m.put(CURRENT_ADDRESS, current != null ? nullToEmpty(current.trcoBsunPostAdrs()) : "");
        m.put(OLD_ADDRESS, old != null ? nullToEmpty(old.trcoBsunPostAdrs()) : "");
        // Legacy "Valid Date" = the old address's valid-till, dd-MM-yyyy.
        m.put(VALID_DATE, old != null ? formatValidDate(old.dueDt()) : "");
        return m;
    }

    private static <T> T first(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list.get(0);
    }

    private static ContactAddressRow addressByDispOrd(List<ContactAddressRow> addrs, int dispOrd) {
        if (addrs == null) return null;
        for (ContactAddressRow a : addrs) {
            if (a.dispOrd() != null && a.dispOrd() == dispOrd) return a;
        }
        return null;
    }

    /** {@code yyyyMMdd} → {@code dd-MM-yyyy} (legacy r006 excel format).
     *  Returns the raw value untouched when it isn't an 8-digit date. */
    private static String formatValidDate(String yyyymmdd) {
        if (yyyymmdd == null) return "";
        String s = yyyymmdd.trim();
        if (s.length() != 8 || !s.chars().allMatch(Character::isDigit)) return s;
        return s.substring(6, 8) + "-" + s.substring(4, 6) + "-" + s.substring(0, 4);
    }

    private static String stringOrNull(Object raw) {
        if (raw == null) return null;
        String s = raw.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
