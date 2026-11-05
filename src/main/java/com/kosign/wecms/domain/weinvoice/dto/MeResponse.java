package com.kosign.wecms.domain.weinvoice.dto;

import java.util.List;
import java.util.Map;

/**
 * {@code GET /me} — who is signed in and the WeInvoice context of the header (screen 02).
 *
 * @param langCd             WeInvoice language preference, else the login language
 * @param workingCompanyMst  default MST of the user, else of the tenant, else the first one
 * @param shortcuts          saved keyboard shortcuts (free-form, owned by the UI)
 */
public record MeResponse(
        String userId,
        String userName,
        String langCd,
        boolean admin,
        List<CompanyItem> companies,
        String workingCompanyMst,
        int maxCompanies,
        boolean testDataEnabled,
        Map<String, Object> shortcuts) {
}
