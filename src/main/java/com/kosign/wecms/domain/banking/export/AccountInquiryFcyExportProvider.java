package com.kosign.wecms.domain.banking.export;

import com.kosign.wecms.common.i18n.RelativeTimeFormatter;
import com.kosign.wecms.domain.banking.dto.AccountListResponse;
import com.kosign.wecms.domain.banking.dto.CurrencyTotal;
import com.kosign.wecms.domain.banking.service.AccountService;
import com.kosign.wecms.domain.banking.service.AmountFormatter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Banking &gt; Account Inquiry (FCY) — Excel export.
 *
 * <p>Binds {@code screenId = "banking-account-inquiry-fcy"} and defaults
 * {@code filters.currency} to {@code "FCY"} (server-side: {@code ccy_cd
 * <> 'VND'}). The page may also pass a concrete CCY (e.g. {@code "USD"},
 * {@code "KRW"}) when the user has narrowed via the in-page currency
 * dropdown — that value flows straight through.
 *
 * <p>Subheader format reuses the legacy single-line VND template
 * ({@code "<count> account(s)  ·  Balance: <bal>  ·  Available Balance:
 * <avail>"}) and renders one such line per currency group, sourced
 * from {@link AccountListResponse#totalsByCcy()}. For wire
 * {@code currency=FCY} the list has multiple entries (USD + KRW + EUR
 * + …) so the subheader spans multiple lines; for a concrete wire CCY
 * (USD/KRW/…) the list has a single entry and the subheader is a single
 * line that reads identically to a VND-style export. Each line uses
 * the group's own CCY for amount formatting (e.g. {@code $12,931.38},
 * {@code ₩0}, {@code 19.886.030.287₫}) — no decimal arithmetic in the
 * provider.
 *
 * <p>Empty result (zero rows in the filtered set) degrades to the
 * legacy {@code "%d account(s)"} count-only string so the subheader is
 * never blank.
 */
@Component
public class AccountInquiryFcyExportProvider extends AccountInquiryExportProvider {

    public AccountInquiryFcyExportProvider(AccountService accountService,
                                           RelativeTimeFormatter relativeTimeFormatter) {
        super(accountService, relativeTimeFormatter);
    }

    @Override
    public String screenId() {
        return "banking-account-inquiry-fcy";
    }

    @Override
    protected String defaultCurrency() {
        return "FCY";
    }

    @Override
    protected String composeSubHeader(int rowCount, AccountListResponse data, String currency) {
        List<CurrencyTotal> totals = data.totalsByCcy();
        if (totals == null || totals.isEmpty()) {
            return String.format(Locale.ROOT, "%d account(s)", rowCount);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totals.size(); i++) {
            if (i > 0) sb.append("\n");        // one line per CCY group
            CurrencyTotal t = totals.get(i);
            sb.append(String.format(Locale.ROOT,
                    "%d account(s)  ·  Balance: %s  ·  Available Balance: %s",
                    t.count(),
                    AmountFormatter.forDisplay(t.balance(), t.ccyCd()),
                    AmountFormatter.forDisplay(t.availableBal(), t.ccyCd())));
        }
        return sb.toString();
    }
}
