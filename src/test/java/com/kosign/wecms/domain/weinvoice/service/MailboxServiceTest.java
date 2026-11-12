package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kosign.wecms.common.exception.BusinessException;
import com.kosign.wecms.domain.weinvoice.WeInvoiceTestProps;
import com.kosign.wecms.domain.weinvoice.common.CompanyAccessGuard;
import com.kosign.wecms.domain.weinvoice.common.WeInvoiceSecretCipher;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceDb;
import com.kosign.wecms.domain.weinvoice.config.WeInvoiceProperties;
import com.kosign.wecms.domain.weinvoice.dto.MailboxItem;
import com.kosign.wecms.domain.weinvoice.dto.MailboxSaveRequest;
import com.kosign.wecms.domain.weinvoice.dto.RequestAccepted;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository;
import com.kosign.wecms.domain.weinvoice.repository.CompanyConnectionRepository.CompanyRow;
import com.kosign.wecms.domain.weinvoice.repository.MailboxRepository;
import com.kosign.wecms.domain.weinvoice.repository.MailboxRepository.MailboxRow;
import com.kosign.wecms.domain.weinvoice.repository.MailboxRepository.MailboxWrite;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.MailLogin;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.MailLoginResult;
import com.kosign.wecms.domain.weinvoice.service.ScrapingClient.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Screen 08 mailbox connections (docs/features/addon-service/weinvoice/weinvoice-mailbox-connections-2026-11-12.md):
 * validation before the engine is asked, login test before anything is stored, secrets encrypted and never
 * returned, blank password on edit keeps the secret, Microsoft callback always redirects.
 */
class MailboxServiceTest {

    private static final String OWNER = "lamine@gmail.com";
    private static final String MST = "1201496252";
    private static final MailLoginResult OK = new MailLoginResult(Outcome.OK, "", List.of());
    private static final byte[] KEY = new byte[32];

    private final MailboxRepository mailboxes = mock(MailboxRepository.class);
    private final CompanyConnectionRepository companies = mock(CompanyConnectionRepository.class);
    private final CompanyAccessGuard guard = mock(CompanyAccessGuard.class);
    private final WeInvoiceSecretCipher cipher = mock(WeInvoiceSecretCipher.class);
    private final ScrapingClient scraping = mock(ScrapingClient.class);
    private final EngineRequestService engine = mock(EngineRequestService.class);
    private final MicrosoftOAuthClient microsoft = mock(MicrosoftOAuthClient.class);
    private final WeInvoiceDb db = mock(WeInvoiceDb.class);
    private final MailboxOAuthState state =
            new MailboxOAuthState(KEY, new ObjectMapper(), Clock.fixed(Instant.parse("2026-11-12T03:00:00Z"), ZoneOffset.UTC));
    private MailboxService service;

    @BeforeEach
    void setUp() {
        when(guard.ownerId()).thenReturn(OWNER);
        when(guard.userId()).thenReturn(OWNER);
        when(db.enabled()).thenReturn(true);
        when(cipher.encrypt(anyString())).thenAnswer(inv -> "v1:enc(" + inv.getArgument(0) + ")");
        when(companies.find(OWNER, MST)).thenReturn(Optional.of(company(MST, false)));
        when(microsoft.configured()).thenReturn(true);
        service = build(WeInvoiceTestProps.enabled("", 5));
    }

    private MailboxService build(WeInvoiceProperties props) {
        return new MailboxService(mailboxes, companies, guard, cipher, scraping, engine, state, microsoft, props, db);
    }

    private static CompanyRow company(String mst, boolean deleted) {
        return new CompanyRow(OWNER, mst, null, null, mst, true, "CONNECTED", null, null, false, deleted, null, null);
    }

    private static MailboxRow row(long id, String kind, String address, String auth, String host) {
        return new MailboxRow(id, OWNER, kind, address, auth, host, 993, "SSL", address, "INBOX,HDDT", "ONE", MST,
                "CONNECTED", null, "20261112030000", 4, "20261111020000");
    }

    private static MailboxSaveRequest gmail(String password) {
        return new MailboxSaveRequest("gmail", "ke.toan@gmail.com", password, null, null, null, null,
                List.of("INBOX", "HDDT"), "ONE", MST);
    }

    private static MailboxSaveRequest imap(String host, String password) {
        return new MailboxSaveRequest("IMAP", "ketoan@congty.vn", password, host, null, null, null,
                null, "ONE", MST);
    }

    // ---- create -----------------------------------------------------------------------------

    @Test
    void gmailAppPasswordWithSpacesIsCompactedTestedAndStoredEncrypted() {
        when(scraping.mailLoginTest(any())).thenReturn(OK);
        when(mailboxes.save(any(), eq(OWNER))).thenReturn(7L);
        when(mailboxes.find(OWNER, 7L)).thenReturn(Optional.of(row(7, "GMAIL", "ke.toan@gmail.com", "APP_PASSWORD", "imap.gmail.com")));

        MailboxItem item = service.create(gmail("abcd efgh ijkl mnop"));

        ArgumentCaptor<MailLogin> login = ArgumentCaptor.forClass(MailLogin.class);
        verify(scraping).mailLoginTest(login.capture());
        assertThat(login.getValue()).isEqualTo(new MailLogin("APP_PASSWORD", "imap.gmail.com", 993, "SSL",
                "ke.toan@gmail.com", "abcdefghijklmnop", null, List.of("INBOX", "HDDT")));
        ArgumentCaptor<MailboxWrite> write = ArgumentCaptor.forClass(MailboxWrite.class);
        verify(mailboxes).save(write.capture(), eq(OWNER));
        assertThat(write.getValue().secretEnc()).isEqualTo("v1:enc(abcdefghijklmnop)");
        assertThat(write.getValue().kind()).isEqualTo("GMAIL");
        assertThat(write.getValue().folders()).isEqualTo("INBOX,HDDT");
        assertThat(write.getValue().toString()).doesNotContain("abcdefghijklmnop", "v1:enc");
        assertThat(item.id()).isEqualTo("7");
        assertThat(item.folders()).containsExactly("INBOX", "HDDT");
        assertThat(item.connectedAt()).isEqualTo("2026-11-11T02:00:00Z");
        assertThat(item.lastScanInvoices()).isEqualTo(4);
    }

    @Test
    void gmailPasswordThatIsNotAnAppPasswordIsRefusedBeforeTheEngine() {
        for (String bad : new String[]{"MyGmailPassword1", "abcd efgh ijkl", "abcd-efgh-ijkl-mnop"}) {
            assertThatThrownBy(() -> service.create(gmail(bad)))
                    .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3033"));
        }
        verifyNoInteractions(scraping);
        verify(mailboxes, never()).save(any(), anyString());
    }

    @Test
    void imapNeedsAHostAndDefaultsTo993Ssl() {
        assertThatThrownBy(() -> service.create(imap("  ", "pw")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3034"));
        verifyNoInteractions(scraping);

        when(scraping.mailLoginTest(any())).thenReturn(OK);
        when(mailboxes.save(any(), eq(OWNER))).thenReturn(3L);
        when(mailboxes.find(OWNER, 3L)).thenReturn(Optional.of(row(3, "IMAP", "ketoan@congty.vn", "PASSWORD", "mail.congty.vn")));
        service.create(imap("mail.congty.vn", "Mail#1"));
        verify(scraping).mailLoginTest(new MailLogin("PASSWORD", "mail.congty.vn", 993, "SSL", "ketoan@congty.vn",
                "Mail#1", null, List.of("INBOX")));
    }

    @Test
    void companyMustBeOneOfTheOwnersTaxCodes() {
        when(companies.find(OWNER, "0318793907")).thenReturn(Optional.empty());
        MailboxSaveRequest foreign = new MailboxSaveRequest("GMAIL", "a@gmail.com", "abcdefghijklmnop", null, null, null,
                null, null, "ONE", "0318793907");
        assertThatThrownBy(() -> service.create(foreign))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3002"));

        when(companies.find(OWNER, "3600823561")).thenReturn(Optional.of(company("3600823561", true)));
        MailboxSaveRequest deleted = new MailboxSaveRequest("GMAIL", "a@gmail.com", "abcdefghijklmnop", null, null, null,
                null, null, "ONE", "3600823561");
        assertThatThrownBy(() -> service.create(deleted)).isInstanceOf(BusinessException.class);

        MailboxSaveRequest missing = new MailboxSaveRequest("GMAIL", "a@gmail.com", "abcdefghijklmnop", null, null, null,
                null, null, "ONE", null);
        assertThatThrownBy(() -> service.create(missing)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(scraping);
    }

    @Test
    void scopeAllStoresNoTaxCode() {
        when(scraping.mailLoginTest(any())).thenReturn(OK);
        when(mailboxes.save(any(), eq(OWNER))).thenReturn(1L);
        when(mailboxes.find(OWNER, 1L)).thenReturn(Optional.of(row(1, "GMAIL", "a@gmail.com", "APP_PASSWORD", "imap.gmail.com")));
        service.create(new MailboxSaveRequest("GMAIL", "a@gmail.com", "abcdefghijklmnop", null, null, null, null, null,
                "all", "9999999999"));
        ArgumentCaptor<MailboxWrite> write = ArgumentCaptor.forClass(MailboxWrite.class);
        verify(mailboxes).save(write.capture(), eq(OWNER));
        assertThat(write.getValue().companyScope()).isEqualTo("ALL");
        assertThat(write.getValue().companyMst()).isNull();
    }

    @Test
    void microsoftCannotBeCreatedWithAPassword() {
        assertThatThrownBy(() -> service.create(new MailboxSaveRequest("MICROSOFT", "a@contoso.com", "pw", null, null,
                null, null, null, "ONE", MST))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(scraping);
    }

    @Test
    void refusedLoginSavesNothingAndCarriesTheServerMessage() {
        when(scraping.mailLoginTest(any())).thenReturn(new MailLoginResult(Outcome.REJECTED, "Invalid credentials", List.of()));
        assertThatThrownBy(() -> service.create(gmail("abcdefghijklmnop")))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("WBE3029");
                    assertThat(e.getArgs()).containsExactly("ke.toan@gmail.com", "Invalid credentials");
                });
        when(scraping.mailLoginTest(any())).thenReturn(new MailLoginResult(Outcome.UNAVAILABLE, "scraping HTTP 502", List.of()));
        assertThatThrownBy(() -> service.create(gmail("abcdefghijklmnop")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3030"));
        when(scraping.mailLoginTest(any())).thenReturn(new MailLoginResult(Outcome.OK, "", List.of("HDDT")));
        assertThatThrownBy(() -> service.create(gmail("abcdefghijklmnop")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3036"));
        verify(mailboxes, never()).save(any(), anyString());
        verify(cipher, never()).encrypt(anyString());
    }

    // ---- no secret in any response ------------------------------------------------------------

    @Test
    void mailboxItemHasNoSecretField() {
        assertThat(Arrays.stream(MailboxItem.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .noneMatch(n -> n.contains("password") || n.contains("secret") || n.contains("token"));
        assertThat(Arrays.stream(MailboxRow.class.getRecordComponents()).map(c -> c.getName().toLowerCase()))
                .noneMatch(n -> n.contains("password") || n.contains("secret") || n.contains("token"));
        assertThat(gmail("abcdefghijklmnop").toString()).doesNotContain("abcdefghijklmnop");
    }

    // ---- update -------------------------------------------------------------------------------

    @Test
    void updateWithBlankPasswordRetestsWithTheStoredSecretAndKeepsIt() {
        when(mailboxes.find(OWNER, 5L)).thenReturn(Optional.of(row(5, "IMAP", "ketoan@congty.vn", "PASSWORD", "mail.congty.vn")));
        when(mailboxes.findSecret(OWNER, 5L)).thenReturn(Optional.of("v1:stored"));
        when(cipher.decrypt("v1:stored")).thenReturn("Old#Pw");
        when(scraping.mailLoginTest(any())).thenReturn(OK);
        when(mailboxes.update(eq(5L), any(), eq(OWNER))).thenReturn(1);

        service.update("5", new MailboxSaveRequest("IMAP", "ketoan@congty.vn", "  ", "mail2.congty.vn", 143, "starttls",
                null, List.of("INBOX"), "ONE", MST));

        verify(scraping).mailLoginTest(new MailLogin("PASSWORD", "mail2.congty.vn", 143, "STARTTLS", "ketoan@congty.vn",
                "Old#Pw", null, List.of("INBOX")));
        ArgumentCaptor<MailboxWrite> write = ArgumentCaptor.forClass(MailboxWrite.class);
        verify(mailboxes).update(eq(5L), write.capture(), eq(OWNER));
        assertThat(write.getValue().secretEnc()).isNull(); // null = keep the stored secret
        verify(cipher, never()).encrypt(anyString());
    }

    @Test
    void updateWithNewPasswordStoresTheNewSecret() {
        when(mailboxes.find(OWNER, 5L)).thenReturn(Optional.of(row(5, "IMAP", "ketoan@congty.vn", "PASSWORD", "mail.congty.vn")));
        when(scraping.mailLoginTest(any())).thenReturn(OK);
        when(mailboxes.update(eq(5L), any(), eq(OWNER))).thenReturn(1);
        service.update("5", imap("mail.congty.vn", "New#Pw"));
        ArgumentCaptor<MailboxWrite> write = ArgumentCaptor.forClass(MailboxWrite.class);
        verify(mailboxes).update(eq(5L), write.capture(), eq(OWNER));
        assertThat(write.getValue().secretEnc()).isEqualTo("v1:enc(New#Pw)");
        verify(mailboxes, never()).findSecret(anyString(), anyLong());
    }

    @Test
    void updateOfAMicrosoftMailboxRetestsWithItsRefreshToken() {
        when(mailboxes.find(OWNER, 9L)).thenReturn(Optional.of(row(9, "MICROSOFT", "a@contoso.com", "OAUTH_MS", "outlook.office365.com")));
        when(mailboxes.findSecret(OWNER, 9L)).thenReturn(Optional.of("v1:rt"));
        when(cipher.decrypt("v1:rt")).thenReturn("refresh-1");
        when(scraping.mailLoginTest(any())).thenReturn(OK);
        when(mailboxes.update(eq(9L), any(), eq(OWNER))).thenReturn(1);
        service.update("9", new MailboxSaveRequest("MICROSOFT", "ignored@x.vn", null, null, null, null, null,
                List.of("Hoa don"), "ALL", null));
        verify(scraping).mailLoginTest(new MailLogin("OAUTH_MS", "outlook.office365.com", 993, "SSL", "a@contoso.com",
                null, "refresh-1", List.of("Hoa don")));
    }

    @Test
    void unknownOrForeignMailboxIsNotFound() {
        when(mailboxes.find(OWNER, 404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update("404", imap("h", "p")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3031"));
        assertThatThrownBy(() -> service.scan("404"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3031"));
        assertThatThrownBy(() -> service.delete("404"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3031"));
    }

    // ---- scan ---------------------------------------------------------------------------------

    @Test
    void scanQueuesMailScanWithTheMailboxId() {
        when(mailboxes.find(OWNER, 7L)).thenReturn(Optional.of(row(7, "GMAIL", "a@gmail.com", "APP_PASSWORD", "imap.gmail.com")));
        when(engine.queue(MST, "MAIL_SCAN", "MANUAL", Map.of("mailbox_id", 7L))).thenReturn(42L);
        RequestAccepted accepted = service.scan("7");
        assertThat(accepted).isEqualTo(new RequestAccepted("42", "7"));
    }

    // ---- Microsoft ----------------------------------------------------------------------------

    @Test
    void authorizeRefusesWhenMicrosoftIsNotConfigured() {
        when(microsoft.configured()).thenReturn(false);
        assertThatThrownBy(() -> service.microsoftAuthorize("ONE", MST, null))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getCode()).isEqualTo("WBE3032"));
    }

    @Test
    void authorizeSignsTheOwnersChoice() {
        when(microsoft.authorizeUrl(anyString())).thenAnswer(inv -> "https://login/authorize?state=" + inv.getArgument(0));
        String url = service.microsoftAuthorize("ONE", MST, List.of("INBOX", "HDDT")).url();
        String signed = url.substring(url.indexOf("state=") + 6);
        assertThat(state.verify(signed)).contains(
                new MailboxOAuthState.Payload(OWNER, "ONE", MST, List.of("INBOX", "HDDT")));
    }

    @Test
    void callbackErrorsRedirectWithAShortReasonAndSaveNothing() {
        String good = state.sign(new MailboxOAuthState.Payload(OWNER, "ONE", MST, List.of("INBOX")));
        String base = "https://invoice.example.vn/sources/mailbox?ms=error&reason=";

        assertThat(service.microsoftCallback(null, good, "access_denied")).isEqualTo(base + "denied");
        assertThat(service.microsoftCallback("code", "forged.state", null)).isEqualTo(base + "state");

        when(companies.find(OWNER, MST)).thenReturn(Optional.empty());
        assertThat(service.microsoftCallback("code", good, null)).isEqualTo(base + "company");
        when(companies.find(OWNER, MST)).thenReturn(Optional.of(company(MST, false)));

        when(microsoft.configured()).thenReturn(false);
        assertThat(service.microsoftCallback("code", good, null)).isEqualTo(base + "not_configured");
        verify(mailboxes, never()).save(any(), anyString());
    }

    @Test
    void callbackRefusedOrUnavailableLoginRedirectsWithReason() throws Exception {
        String good = state.sign(new MailboxOAuthState.Payload(OWNER, "ONE", MST, List.of("INBOX")));
        when(microsoft.exchange("code")).thenReturn(new MicrosoftOAuthClient.Tokens("rt-1", "a@contoso.com"));
        when(scraping.mailLoginTest(any())).thenReturn(new MailLoginResult(Outcome.REJECTED, "AUTHENTICATE failed", List.of()));
        assertThat(service.microsoftCallback("code", good, null)).endsWith("?ms=error&reason=refused");
        when(scraping.mailLoginTest(any())).thenReturn(new MailLoginResult(Outcome.UNAVAILABLE, "down", List.of()));
        assertThat(service.microsoftCallback("code", good, null)).endsWith("?ms=error&reason=unavailable");
        when(microsoft.exchange("bad")).thenThrow(new MicrosoftOAuthClient.OAuthException("exchange", "HTTP 400"));
        assertThat(service.microsoftCallback("bad", good, null)).endsWith("?ms=error&reason=exchange");
        verify(mailboxes, never()).save(any(), anyString());
    }

    @Test
    void callbackSuccessSavesTheEncryptedRefreshTokenAndRedirectsOk() throws Exception {
        String good = state.sign(new MailboxOAuthState.Payload(OWNER, "ONE", MST, List.of("INBOX")));
        when(microsoft.exchange("code")).thenReturn(new MicrosoftOAuthClient.Tokens("rt-1", "a@contoso.com"));
        when(scraping.mailLoginTest(any())).thenReturn(OK);

        String location = service.microsoftCallback("code", good, null);

        assertThat(location).isEqualTo("https://invoice.example.vn/sources/mailbox?ms=ok");
        assertThat(location).doesNotContain("rt-1", "code");
        verify(scraping).mailLoginTest(new MailLogin("OAUTH_MS", "outlook.office365.com", 993, "SSL", "a@contoso.com",
                null, "rt-1", List.of("INBOX")));
        ArgumentCaptor<MailboxWrite> write = ArgumentCaptor.forClass(MailboxWrite.class);
        verify(mailboxes).save(write.capture(), eq(OWNER));
        assertThat(write.getValue().ownerId()).isEqualTo(OWNER);
        assertThat(write.getValue().kind()).isEqualTo("MICROSOFT");
        assertThat(write.getValue().secretEnc()).isEqualTo("v1:enc(rt-1)");
    }
}
