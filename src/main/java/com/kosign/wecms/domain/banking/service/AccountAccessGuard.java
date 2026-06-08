package com.kosign.wecms.domain.banking.service;

import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.setting.bankaccount.repository.AcctAuthInfoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Per-account data-scope guard for the Transaction History (VND/FCY) family.
 *
 * <p>Mirrors legacy {@code FnshHelper.fnsh_0002_001_r001} lines 764-767 — a
 * non-admin user may only read/act on accounts they hold an active
 * <b>View</b> grant for ({@code ACCT_AUTH_INFO.AUTH_TYPE='V'},
 * {@code USE_YN='Y'}). Admins bypass the check (legacy
 * {@code !loginUser.isAdmin()} branch). This is the missing piece called out
 * as audit finding #29 — menu authorization (the {@code @PreAuthorize} on the
 * controller) gates whether the user may reach the screen at all; this guard
 * gates <i>which accounts</i> within their own tenant they may read.
 *
 * <p>Policy is intentionally identical to the Account Inquiry list path
 * ({@code AccountService} — {@code authUserId = currentIsAdmin() ? null :
 * currentUserId()} with the {@code AcctAuthInfo} EXISTS clause on
 * {@code authType='V'}). A user who cannot see an account in the picker also
 * cannot open its transactions by typing the URL.
 *
 * <p>Throws Spring Security's {@link AccessDeniedException} (not a
 * {@code BusinessException}) so the global handler maps it to HTTP
 * <b>403</b> {@code WBE1003}, matching the audit's required behaviour for
 * unauthorized direct-URL access.
 */
@Component
@RequiredArgsConstructor
public class AccountAccessGuard {

    /** Legacy View authority code in {@code ACCT_AUTH_INFO.AUTH_TYPE}. */
    private static final String AUTH_TYPE_VIEW = "V";
    private static final String USE_YN = "Y";

    private final AcctAuthInfoRepository authRepo;

    /**
     * Assert the current user may view transactions for the given account.
     * No-op for tenant admins; throws {@link AccessDeniedException} for a
     * non-admin lacking an active View grant on the account.
     */
    public void assertCanView(String bizKey, String bankCd, String acctNo) {
        if (TenantContext.currentIsAdmin()) {
            return;
        }
        String userId = TenantContext.currentUserId();
        boolean permitted = userId != null
                && authRepo.existsByBizKeyAndUserIdAndBankCdAndAcctNoAndAuthTypeAndUseYn(
                        bizKey, userId, bankCd, acctNo, AUTH_TYPE_VIEW, USE_YN);
        if (!permitted) {
            throw new AccessDeniedException(
                    "User is not permitted to view transactions for account " + bankCd + "/" + acctNo);
        }
    }
}
