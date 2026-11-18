package com.kosign.wecms.domain.weinvoice.service;

import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceIds;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceTime;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.dto.MailboxItem;
import com.kosign.wecms.domain.weinvoice.dto.MailboxSaveRequest;
import com.kosign.wecms.domain.weinvoice.dto.MicrosoftAuthorizeResponse;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.MailboxRepository;
import com.kosign.wecms.domain.weinvoice.repository.MailboxRepository.MailboxRow;
import com.kosign.wecms.domain.weinvoice.repository.MailboxRepository.MailboxWrite;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.MailLogin;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.MailLoginResult;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Screen 08 — mailboxes a user connects so the engine collects invoice mail for their tax codes
 * (record docs/features/addon-service/weinvoice/weinvoice-mailbox-connections-2026-11-12.md).
 *
 * <p>"Check and connect": every connect / edit first logs in once through the engine
 * ({@link ScrapingClient#mailLoginTest}); only an accepted login is stored — secret encrypted with
 * {@link WeInvoiceSecretCipher}, {@code CONNECTED}. Refused → {@code WBE3029} with the mail server's
 * text; not checkable → {@code WBE3030}; requested folders missing → {@code WBE3036}. Nothing is
 * written in any of those cases. Secrets are never returned and never logged.
 *
 * <p>Microsoft mailboxes come through {@link #microsoftAuthorize} / {@link #microsoftCallback}: the
 * callback has no JWT, so the owner comes from the signed state ({@link MailboxOAuthState}).
 */
@Slf4j
@Service
public class MailboxService {

    public static final String GMAIL = "GMAIL";
    public static final String MICROSOFT = "MICROSOFT";
    public static final String IMAP = "IMAP";
    static final String APP_PASSWORD = "APP_PASSWORD";
    static final String PASSWORD = "PASSWORD";
    static final String OAUTH_MS = "OAUTH_MS";
    static final String SSL = "SSL";
    static final String STARTTLS = "STARTTLS";
    static final String SCOPE_ONE = "ONE";
    static final String SCOPE_ALL = "ALL";
    static final String GMAIL_HOST = "imap.gmail.com";
    static final String MICROSOFT_HOST = "outlook.office365.com";
    static final int IMAPS_PORT = 993;
    static final String DEFAULT_FOLDER = "INBOX";
    static final int FOLDERS_MAX_CHARS = 500;
    public static final String MAIL_SCAN = "MAIL_SCAN";

    private static final Pattern GMAIL_APP_PASSWORD = Pattern.compile("[A-Za-z]{16}");
    private static final Pattern ADDRESS = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final MailboxRepository mailboxes;
    private final CompanyConnectionRepository companies;
    private final CompanyAccessGuard guard;
    private final WeInvoiceSecretCipher cipher;
    private final ScrapingClient scraping;
    private final EngineRequestService engine;
    private final MailboxOAuthState oauthState;
    private final MicrosoftOAuthClient microsoft;
    private final WeInvoiceProperties props;
    private final WeInvoiceDb db;

    public MailboxService(MailboxRepository mailboxes, CompanyConnectionRepository companies, CompanyAccessGuard guard,
                          WeInvoiceSecretCipher cipher, ScrapingClient scraping, EngineRequestService engine,
                          MailboxOAuthState oauthState, MicrosoftOAuthClient microsoft, WeInvoiceProperties props,
                          WeInvoiceDb db) {
        this.mailboxes = mailboxes;
        this.companies = companies;
        this.guard = guard;
        this.cipher = cipher;
        this.scraping = scraping;
        this.engine = engine;
        this.oauthState = oauthState;
        this.microsoft = microsoft;
        this.props = props;
        this.db = db;
    }

    public List<MailboxItem> list() {
        return mailboxes.listActive(guard.ownerId()).stream().map(MailboxService::toItem).toList();
    }

    /** Connects a Gmail (app password) or company IMAP mailbox; the same address again updates its row. */
    public MailboxItem create(MailboxSaveRequest req) {
        db.requireEnabled(); // before the engine is asked anything
        String ownerId = guard.ownerId();
        String kind = code(req.kind());
        if (!GMAIL.equals(kind) && !IMAP.equals(kind)) {
            throw new IllegalArgumentException("kind: GMAIL or IMAP (Microsoft mailboxes use /mailboxes/microsoft/authorize)");
        }
        if (isBlank(req.password())) {
            throw new IllegalArgumentException("password: required");
        }
        Settings s = settings(kind, req);
        String password = secretFor(kind, req.password());
        Scope scope = scope(ownerId, req.companyScope(), req.companyMst());
        requireLogin(s.address(), scraping.mailLoginTest(s.login(password, null)));
        long id = mailboxes.save(s.write(ownerId, cipher.encrypt(password), scope), guard.userId());
        return item(ownerId, id);
    }

    /**
     * Edits a mailbox. Blank {@code password} keeps the stored secret, which is still re-tested with the new
     * settings. A Microsoft mailbox keeps its sign-in: only folders and scope change.
     */
    public MailboxItem update(String mailboxId, MailboxSaveRequest req) {
        String ownerId = guard.ownerId();
        long id = WeInvoiceIds.parse(mailboxId, "id");
        MailboxRow row = mailboxes.find(ownerId, id).orElseThrow(() -> new BusinessException("WBE3031"));
        String kind = code(req.kind());
        if (!row.kind().equals(kind)) {
            throw new IllegalArgumentException("kind: a mailbox keeps its kind (" + row.kind() + ")");
        }
        Settings s = MICROSOFT.equals(kind) ? microsoftSettings(row.address(), req.folders()) : settings(kind, req);
        if (!s.address().equalsIgnoreCase(row.address())) {
            mailboxes.findByAddress(ownerId, s.address()).filter(other -> other.id() != id).ifPresent(other -> {
                throw new BusinessException("WBE3035", s.address());
            });
        }
        Scope scope = scope(ownerId, req.companyScope(), req.companyMst());
        boolean keepSecret = MICROSOFT.equals(kind) || isBlank(req.password());
        String secret = keepSecret
                ? cipher.decrypt(mailboxes.findSecret(ownerId, id).orElseThrow(() -> new BusinessException("WBE3031")))
                : secretFor(kind, req.password());
        MailLogin login = MICROSOFT.equals(kind) ? s.login(null, secret) : s.login(secret, null);
        requireLogin(s.address(), scraping.mailLoginTest(login));
        if (mailboxes.update(id, s.write(ownerId, keepSecret ? null : cipher.encrypt(secret), scope), guard.userId()) == 0) {
            throw new BusinessException("WBE3031");
        }
        return item(ownerId, id);
    }

    /** Disconnects (soft delete, secret erased). Invoices already collected stay. */
    public void delete(String mailboxId) {
        String ownerId = guard.ownerId();
        long id = WeInvoiceIds.parse(mailboxId, "id");
        if (mailboxes.softDelete(ownerId, id, guard.userId()) == 0) {
            throw new BusinessException("WBE3031");
        }
    }

    /** "Scan now": queues engine request {@code MAIL_SCAN {mailbox_id}}; progress via {@code GET /requests/{id}}. */
    public RequestAccepted scan(String mailboxId) {
        String ownerId = guard.ownerId();
        long id = WeInvoiceIds.parse(mailboxId, "id");
        MailboxRow row = mailboxes.find(ownerId, id).orElseThrow(() -> new BusinessException("WBE3031"));
        long requestId = engine.queue(row.companyMst(), MAIL_SCAN, "MANUAL", Map.of("mailbox_id", row.id()));
        return new RequestAccepted(String.valueOf(requestId), String.valueOf(row.id()));
    }

    /** Microsoft sign-in URL with a signed 10-minute state; {@code WBE3032} when sign-in is not configured. */
    public MicrosoftAuthorizeResponse microsoftAuthorize(String companyScope, String companyMst, List<String> folders) {
        db.requireEnabled();
        if (!microsoft.configured()) {
            throw new BusinessException("WBE3032");
        }
        String ownerId = guard.ownerId();
        Scope scope = scope(ownerId, companyScope, companyMst);
        List<String> f = folders(folders);
        String state = oauthState.sign(new MailboxOAuthState.Payload(ownerId, scope.scope(), scope.mst(), f));
        return new MicrosoftAuthorizeResponse(microsoft.authorizeUrl(state));
    }

    /**
     * Browser return from Microsoft. Never throws: every outcome is a redirect to
     * {@code <web base>/sources/mailbox?ms=ok} or {@code ?ms=error&reason=<code>}. Reasons:
     * {@code not_configured}, {@code denied}, {@code state}, {@code exchange}, {@code no_refresh_token},
     * {@code no_address}, {@code company}, {@code refused}, {@code missing_folders}, {@code unavailable},
     * {@code error}. Codes, tokens and the state never appear in the URL or the log.
     */
    public String microsoftCallback(String code, String state, String error) {
        String base = props.webBase();
        String target = (base == null ? "" : base) + "/sources/mailbox";
        String reason = microsoftConnect(code, state, error);
        return reason == null ? target + "?ms=ok"
                : target + "?ms=error&reason=" + URLEncoder.encode(reason, StandardCharsets.UTF_8);
    }

    /** @return {@code null} when the mailbox was saved, else the redirect reason */
    private String microsoftConnect(String code, String state, String error) {
        if (!db.enabled() || !microsoft.configured()) return "not_configured";
        if (!isBlank(error)) return "denied";
        if (isBlank(code)) return "denied";
        MailboxOAuthState.Payload p = oauthState.verify(state).orElse(null);
        if (p == null) return "state";
        try {
            Scope scope;
            try {
                scope = scope(p.ownerId(), p.companyScope(), p.companyMst());
            } catch (BusinessException | IllegalArgumentException e) {
                return "company";
            }
            MicrosoftOAuthClient.Tokens tokens;
            try {
                tokens = microsoft.exchange(code);
            } catch (MicrosoftOAuthClient.OAuthException e) {
                return e.reason();
            }
            String address = tokens.address().trim();
            Settings s = new Settings(MICROSOFT, address, OAUTH_MS, MICROSOFT_HOST, IMAPS_PORT, SSL, address, folders(p.folders()));
            MailLoginResult r = scraping.mailLoginTest(s.login(null, tokens.refreshToken()));
            switch (r.outcome()) {
                case OK -> {
                    if (!r.missingFolders().isEmpty()) return "missing_folders";
                }
                case REJECTED -> {
                    return r.missingFolders().isEmpty() ? "refused" : "missing_folders";
                }
                case UNAVAILABLE -> {
                    return "unavailable";
                }
            }
            mailboxes.save(s.write(p.ownerId(), cipher.encrypt(tokens.refreshToken()), scope), p.ownerId());
            log.info("WeInvoice Microsoft mailbox connected owner={}", p.ownerId());
            return null;
        } catch (RuntimeException e) {
            log.warn("WeInvoice Microsoft callback failed owner={}: {}", p.ownerId(), e.getClass().getSimpleName());
            return "error";
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Engine verdict → exception; {@code OK} with missing folders is refused too (nothing would be scanned there). */
    private static void requireLogin(String address, MailLoginResult r) {
        switch (r.outcome()) {
            case OK -> {
                if (!r.missingFolders().isEmpty()) {
                    throw new BusinessException("WBE3036", address, String.join(", ", r.missingFolders()));
                }
            }
            case REJECTED -> {
                if (!r.missingFolders().isEmpty()) {
                    throw new BusinessException("WBE3036", address, String.join(", ", r.missingFolders()));
                }
                throw new BusinessException("WBE3029", address, r.message());
            }
            case UNAVAILABLE -> throw new BusinessException("WBE3030", address, r.message());
        }
    }

    /** Connection settings of a Gmail / IMAP request (host / port / TLS fixed for Gmail). */
    private Settings settings(String kind, MailboxSaveRequest req) {
        String address = address(req.address());
        List<String> folders = folders(req.folders());
        if (GMAIL.equals(kind)) {
            return new Settings(GMAIL, address, APP_PASSWORD, GMAIL_HOST, IMAPS_PORT, SSL, address, folders);
        }
        String host = isBlank(req.imapHost()) ? null : req.imapHost().trim();
        if (host == null) {
            throw new BusinessException("WBE3034");
        }
        if (host.contains("/") || host.contains(" ") || host.contains(":")) {
            throw new IllegalArgumentException("imap_host: host name only");
        }
        int port = req.imapPort() == null ? IMAPS_PORT : req.imapPort();
        String tls = isBlank(req.tlsMode()) ? SSL : code(req.tlsMode());
        if (!SSL.equals(tls) && !STARTTLS.equals(tls)) {
            throw new IllegalArgumentException("tls_mode: SSL or STARTTLS");
        }
        String login = isBlank(req.loginId()) ? address : req.loginId().trim();
        return new Settings(IMAP, address, PASSWORD, host, port, tls, login, folders);
    }

    private Settings microsoftSettings(String address, List<String> folders) {
        return new Settings(MICROSOFT, address, OAUTH_MS, MICROSOFT_HOST, IMAPS_PORT, SSL, address, folders(folders));
    }

    /** Gmail app passwords are 16 letters, shown in groups of 4: spaces are dropped. */
    static String secretFor(String kind, String password) {
        if (GMAIL.equals(kind)) {
            String compact = WHITESPACE.matcher(password).replaceAll("");
            if (!GMAIL_APP_PASSWORD.matcher(compact).matches()) {
                throw new BusinessException("WBE3033");
            }
            return compact;
        }
        return password;
    }

    /** {@code ONE} needs one of the owner's active tax codes ({@code WBE3001} / {@code WBE3002}); {@code ALL} has none. */
    private Scope scope(String ownerId, String companyScope, String companyMst) {
        String scope = isBlank(companyScope) ? SCOPE_ONE : code(companyScope);
        if (SCOPE_ALL.equals(scope)) {
            return new Scope(SCOPE_ALL, null);
        }
        if (!SCOPE_ONE.equals(scope)) {
            throw new IllegalArgumentException("company_scope: ONE or ALL");
        }
        if (isBlank(companyMst)) {
            throw new IllegalArgumentException("company_mst: required when company_scope is ONE");
        }
        String mst = WeInvoiceIds.requireTaxId(companyMst);
        companies.find(ownerId, mst).filter(c -> !c.deleted())
                .orElseThrow(() -> new BusinessException("WBE3002", mst));
        return new Scope(SCOPE_ONE, mst);
    }

    static String address(String raw) {
        String a = raw == null ? "" : raw.trim();
        if (!ADDRESS.matcher(a).matches() || a.length() > 320) {
            throw new IllegalArgumentException("address: invalid mail address");
        }
        return a;
    }

    /** Trimmed, de-duplicated, no commas (stored comma separated); empty → {@code INBOX}. */
    static List<String> folders(List<String> raw) {
        Set<String> out = new LinkedHashSet<>();
        if (raw != null) {
            for (String f : raw) {
                if (f == null || f.isBlank()) continue;
                String t = f.trim();
                if (t.contains(",")) {
                    throw new IllegalArgumentException("folders: a folder name cannot contain ','");
                }
                out.add(t);
            }
        }
        if (out.isEmpty()) out.add(DEFAULT_FOLDER);
        if (String.join(",", out).length() > FOLDERS_MAX_CHARS) {
            throw new IllegalArgumentException("folders: at most " + FOLDERS_MAX_CHARS + " characters in total");
        }
        return List.copyOf(out);
    }

    private MailboxItem item(String ownerId, long id) {
        return mailboxes.find(ownerId, id).map(MailboxService::toItem).orElseThrow(() -> new BusinessException("WBE3031"));
    }

    public static MailboxItem toItem(MailboxRow r) {
        List<String> folders = r.folders() == null || r.folders().isBlank() ? List.of(DEFAULT_FOLDER)
                : Arrays.stream(r.folders().split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return new MailboxItem(
                String.valueOf(r.id()),
                r.kind(),
                r.address(),
                r.authType(),
                r.imapHost(),
                r.imapPort(),
                r.tlsMode(),
                r.loginId(),
                folders,
                r.companyScope(),
                r.companyMst(),
                r.status(),
                r.statusMessage(),
                WeInvoiceTime.dtmToIso(r.regDtm()),
                WeInvoiceTime.dtmToIso(r.lastScanDtm()),
                r.lastScanCount());
    }

    private static String code(String s) {
        return s == null ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private record Scope(String scope, String mst) {
    }

    private record Settings(String kind, String address, String authType, String host, int port, String tlsMode,
                            String loginId, List<String> folders) {

        MailLogin login(String password, String refreshToken) {
            return new MailLogin(authType, host, port, tlsMode, loginId, password, refreshToken, new ArrayList<>(folders));
        }

        MailboxWrite write(String ownerId, String secretEnc, Scope scope) {
            return new MailboxWrite(ownerId, kind, address, authType, host, port, tlsMode, loginId, secretEnc,
                    String.join(",", folders), scope.scope(), scope.mst());
        }
    }
}
