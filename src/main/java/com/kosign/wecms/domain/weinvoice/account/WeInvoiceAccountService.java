package com.kosign.wecms.domain.weinvoice.account;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.common.security.JwtTokenProvider;
import com.kosign.wecms.common.tenant.TenantContext;
import com.kosign.wecms.domain.auth.dto.LoginResponse;
import com.kosign.wecms.domain.auth.dto.MeResponse;
import com.kosign.wecms.domain.auth.repository.UserInfmRepository;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * WeInvoice-owned accounts ({@code winv_user_infm}): self sign-up, login, {@code /me}
 * (docs/features/addon-service/weinvoice/weinvoice-self-signup-2026-11-13.md).
 *
 * <p>A token issued here carries {@code bizKey = }{@value #BIZ_KEY} — not a WeCMS company — so {@code AuthService}
 * can route {@code /me} and logout back here. WeInvoice endpoints only read the subject ({@code owner_id}).
 */
@Slf4j
@Service
public class WeInvoiceAccountService {

    /** JWT {@code bizKey} claim of a WeInvoice account. Never a real BIZ_KEY (those are {@code VN_…_000001}). */
    public static final String BIZ_KEY = "WEINVOICE";

    static final String CODE_BAD_CREDENTIALS = "WBE1009";
    static final String CODE_INACTIVE        = "WBE1010";
    static final String CODE_LOCKED          = "WBE1011";
    static final String CODE_DELETED         = "WBE1128";
    static final String CODE_UNAUTHENTICATED = "WBE1401";
    static final String CODE_BAD_EMAIL       = "WBE3038";
    static final String CODE_WEAK_PASSWORD   = "WBE3039";
    static final String CODE_TAKEN           = "WBE3040";
    static final String CODE_UNAVAILABLE     = "WBE3041";

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    static final int MIN_PASSWORD = 8;

    private final WeInvoiceDb db;
    private final WeInvoiceAccountRepository accounts;
    private final UserInfmRepository wecmsUsers;
    private final PasswordEncoder verifier;
    private final JwtTokenProvider tokens;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10);

    public WeInvoiceAccountService(WeInvoiceDb db, WeInvoiceAccountRepository accounts, UserInfmRepository wecmsUsers,
                                   PasswordEncoder verifier, JwtTokenProvider tokens) {
        this.db = db;
        this.accounts = accounts;
        this.wecmsUsers = wecmsUsers;
        this.verifier = verifier;
        this.tokens = tokens;
    }

    public static boolean isWeInvoiceToken(String bizKey) {
        return BIZ_KEY.equals(bizKey);
    }

    /**
     * Login against {@code winv_user_infm}. Empty when WeInvoice is disabled or the id is not a WeInvoice account —
     * the caller then runs the WeCMS login unchanged. Same error codes and flag order as WeCMS (DEL → ACTV → LOCK →
     * password).
     */
    public Optional<LoginResponse> login(String userId, String password, String langCd) {
        if (!db.enabled() || userId == null || userId.isBlank()) return Optional.empty();
        Optional<WeInvoiceAccount> found;
        try {
            found = accounts.find(userId.trim());
        } catch (DataAccessException e) {
            // Table not created yet (V4 not applied) or the WeInvoice DB is down: WeCMS logins must keep working.
            log.warn("WeInvoice account lookup failed, falling back to the WeCMS login: {}", e.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
        if (found.isEmpty()) return Optional.empty();
        WeInvoiceAccount a = found.get();
        if (a.deleted()) throw new BusinessException(CODE_DELETED);
        if (!a.active()) throw new BusinessException(CODE_INACTIVE);
        if (a.locked()) throw new BusinessException(CODE_LOCKED);
        if (!verifier.matches(password, a.userPw())) {
            if (accounts.recordFailure(a.userId())) {
                log.warn("WeInvoice account {} locked after {} wrong passwords", a.userId(), WeInvoiceAccountRepository.LOCK_THRESHOLD);
            }
            throw new BusinessException(CODE_BAD_CREDENTIALS);
        }
        accounts.recordSuccess(a.userId());
        return Optional.of(issue(a, langCd));
    }

    /** Creates the account and signs it in (same response as a login). */
    public LoginResponse signup(SignupRequest req, String langCd) {
        if (!db.enabled()) throw new BusinessException(CODE_UNAVAILABLE);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) throw new BusinessException(CODE_BAD_EMAIL);
        String pw = req.userPw();
        if (pw.length() < MIN_PASSWORD || !LETTER.matcher(pw).find() || !DIGIT.matcher(pw).find()) {
            throw new BusinessException(CODE_WEAK_PASSWORD, MIN_PASSWORD);
        }
        // A WeInvoice account is checked before WeCMS at login, so one with a WeCMS user's id would hijack that login.
        if (accounts.find(email).isPresent() || wecmsUsers.existsByUserIdIgnoreCase(email)) {
            throw new BusinessException(CODE_TAKEN, email);
        }
        boolean created = accounts.insertSignup(email, req.userNm().trim(), blankToNull(req.clphNo()),
                blankToNull(req.cmpnNm()), bcrypt.encode(pw), langCd);
        if (!created) throw new BusinessException(CODE_TAKEN, email);   // lost a race with the same email
        log.info("WeInvoice sign-up: {}", email);
        WeInvoiceAccount a = accounts.find(email).orElseThrow(() -> new IllegalStateException("sign-up row missing"));
        accounts.recordSuccess(a.userId());
        return issue(a, langCd);
    }

    /** {@code /me} for a WeInvoice token: the account must still be usable. */
    public MeResponse me(TenantContext.Principal principal) {
        WeInvoiceAccount a = db.enabled() ? accounts.find(principal.userId()).orElse(null) : null;
        if (a == null || a.deleted() || !a.active() || a.locked()) {
            log.warn("/auth/me rejected: WeInvoice account {} no longer eligible", principal.userId());
            throw new BusinessException(CODE_UNAUTHENTICATED);
        }
        String lang = principal.langCd() != null ? principal.langCd().name() : "EN";
        return new MeResponse(a.userId(), a.userNm(), BIZ_KEY, lang, nullToEmpty(a.cmpnNm()),
                TenantContext.AUTH_GR_USER, null, null, true, List.of());
    }

    private LoginResponse issue(WeInvoiceAccount a, String langCd) {
        String token = tokens.issue(a.userId(), a.userNm(), BIZ_KEY, langCd, TenantContext.AUTH_GR_USER, null);
        return LoginResponse.authenticated(token, "Bearer", tokens.expiresInSeconds(), a.userId(), a.userNm(), BIZ_KEY,
                langCd, nullToEmpty(a.cmpnNm()), TenantContext.AUTH_GR_USER, null, null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
