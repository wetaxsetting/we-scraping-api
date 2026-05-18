package com.kosign.wecms.domain.banking.synchistory.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.banking.service.BankCdResolver;
import com.kosign.wecms.domain.banking.synchistory.dto.SyncHistoryListResponse;
import com.kosign.wecms.domain.banking.synchistory.dto.SyncHistoryRow;
import com.kosign.wecms.domain.banking.synchistory.repository.ExtTranCallHisRepository;
import com.kosign.wecms.domain.banking.synchistory.repository.SyncHistoryProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Bank Transaction Sync &gt; History popup service. Mirrors legacy
 * {@code fnsh_0001_003_r001_act.jsp} which reads
 * {@code EXT_TRAN_CALL_HIS} with {@code SVC_ID =
 * 'REAL_TIME_ONLINE_INQUIRY'} filtered by date / currency / bank /
 * result.
 *
 * <p>The legacy popup is per-currency-scope (the wire {@code CCY_CD}
 * input picks VND vs USD). The refactor accepts the same three values
 * the Account Inquiry endpoint does:
 * <ul>
 *   <li>{@code "VND"} → {@code ccy_cd = 'VND'}</li>
 *   <li>{@code "FCY"} → {@code ccy_cd <> 'VND'}</li>
 *   <li>concrete code (e.g. {@code "USD"}) → {@code ccy_cd = 'USD'}</li>
 *   <li>{@code null} / {@code "ALL"} → no currency predicate (refactor
 *       enhancement; legacy required a value)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SyncHistoryService {

    private final ExtTranCallHisRepository repo;
    private final BankCdResolver bankResolver;

    @Transactional(readOnly = true)
    public SyncHistoryListResponse list(
            String fromDt,
            String toDt,
            String currency,
            String bankCd,
            String result) {

        String bizKey = TenantContext.currentBizKey();
        String userId = TenantContext.currentUserId();
        if (bizKey == null || bizKey.isBlank() || userId == null || userId.isBlank()) {
            throw new BusinessException("WBE9999");
        }

        // Currency-scope translation — same convention as
        // AccountService.resolveFilter.
        String ccyEq  = null;
        String ccyNot = null;
        if (currency != null && !currency.isBlank() && !"ALL".equalsIgnoreCase(currency)) {
            String upper = currency.toUpperCase(Locale.ROOT);
            if ("FCY".equals(upper)) {
                ccyNot = "VND";
            } else {
                ccyEq = upper;
            }
        }

        // Result filter — null = All, "success" → TRUE, "failed" → FALSE.
        // Anything else degrades to "all" so a stale frontend bundle
        // never 4xxs the user.
        Boolean successFilter = null;
        if ("success".equalsIgnoreCase(result)) successFilter = Boolean.TRUE;
        else if ("failed".equalsIgnoreCase(result)) successFilter = Boolean.FALSE;

        String safeBankCd = blankToNull(bankCd);

        List<SyncHistoryProjection> hits = repo.findSyncHistory(
                bizKey, userId,
                fromDt, toDt,
                ccyEq, ccyNot,
                safeBankCd,
                successFilter);

        List<SyncHistoryRow> rows = hits.stream().map(this::toRow).toList();
        return new SyncHistoryListResponse(rows, rows.size());
    }

    private SyncHistoryRow toRow(SyncHistoryProjection p) {
        boolean success = "00000000".equals(p.rsltCd());
        return new SyncHistoryRow(
                p.refDate(),
                p.seqNo() == null ? "" : p.seqNo().toPlainString(),
                p.regDtm(),
                p.bankCd(),
                bankResolver.resolveBankNm(p.bankCd()),
                p.acctNo(),
                p.acctNickNm(),
                p.acctTypeCd(),
                bankResolver.resolveAcctTypeNm(p.acctTypeCd()),
                bankResolver.resolveAcctTypeKey(p.acctTypeCd()),
                p.ccyCd() == null ? "" : p.ccyCd().trim(),
                p.startDate(),
                p.endDate(),
                p.rsltCd(),
                success ? "sync_history.result.success" : "sync_history.result.failed",
                p.errCont() == null ? "" : p.errCont()
        );
    }

    private static String blankToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
