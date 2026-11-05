package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.enums.LangCd;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceJson;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.dto.CompanyItem;
import com.kosign.wecms.domain.weinvoice.dto.MeResponse;
import com.kosign.wecms.domain.weinvoice.dto.PreferenceRequest;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.PreferenceRepository;
import com.kosign.wecms.domain.weinvoice.repository.PreferenceRepository.PrefRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Screen 02 header context: user, language, managed MSTs, working MST, shortcuts. */
@Service
public class MeService {

    private final CompanyAccessGuard guard;
    private final CompanyConnectionRepository companies;
    private final PreferenceRepository preferences;
    private final WeInvoiceJson json;
    private final WeInvoiceProperties props;

    public MeService(CompanyAccessGuard guard, CompanyConnectionRepository companies, PreferenceRepository preferences,
                     WeInvoiceJson json, WeInvoiceProperties props) {
        this.guard = guard;
        this.companies = companies;
        this.preferences = preferences;
        this.json = json;
        this.props = props;
    }

    public MeResponse me() {
        String bizKey = guard.bizKey();
        String userId = guard.userId();
        List<CompanyItem> items = companies.listActive(bizKey).stream().map(CompanyService::toItem).toList();
        PrefRow pref = preferences.find(bizKey, userId).orElse(new PrefRow(null, null, null));
        String working = pickWorking(items, pref.defaultMst());
        LangCd login = TenantContext.currentLangCd();
        String lang = pref.langCd() != null ? pref.langCd() : (login == null ? "vi" : login.name().toLowerCase(Locale.ROOT));
        Map<String, Object> shortcuts = json.map(pref.shortcutsJson());
        return new MeResponse(userId, TenantContext.currentUserNm(), lang, TenantContext.currentIsAdmin(), items,
                working, props.maxCompanies(), props.testDataEnabled(), shortcuts == null ? Map.of() : shortcuts);
    }

    public MeResponse savePreferences(PreferenceRequest req) {
        String defaultMst = null;
        if (req.defaultCompanyMst() != null && !req.defaultCompanyMst().isBlank()) {
            defaultMst = guard.requireCompany(req.defaultCompanyMst()).mst();
        }
        preferences.upsert(guard.bizKey(), guard.userId(),
                req.langCd() == null ? null : req.langCd().toLowerCase(Locale.ROOT),
                defaultMst,
                req.shortcuts() == null ? null : json.write(req.shortcuts()));
        return me();
    }

    /** User default if still managed, else the tenant default, else the first MST. */
    static String pickWorking(List<CompanyItem> items, String userDefault) {
        if (userDefault != null && items.stream().anyMatch(c -> c.taxId().equals(userDefault))) return userDefault;
        return items.stream().filter(CompanyItem::defaultCompany).map(CompanyItem::taxId).findFirst()
                .orElse(items.isEmpty() ? null : items.get(0).taxId());
    }
}
