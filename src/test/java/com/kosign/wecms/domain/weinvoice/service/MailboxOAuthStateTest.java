package com.kosign.wecms.domain.weinvoice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Signed stateless state of the Microsoft sign-in: round trip, tamper, expiry, foreign key. */
class MailboxOAuthStateTest {

    private static final byte[] KEY = new byte[32];
    private static final Instant T0 = Instant.parse("2026-11-12T03:00:00Z");
    private static final MailboxOAuthState.Payload PAYLOAD =
            new MailboxOAuthState.Payload("lamine@gmail.com", "ONE", "1201496252", List.of("INBOX", "HDDT"));

    private final ObjectMapper json = new ObjectMapper();

    private MailboxOAuthState at(Instant now, byte[] key) {
        return new MailboxOAuthState(key, json, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void roundTripKeepsOwnerScopeAndFolders() {
        String state = at(T0, KEY).sign(PAYLOAD);
        assertThat(at(T0.plusSeconds(60), KEY).verify(state)).contains(PAYLOAD);
    }

    @Test
    void scopeAllHasNoTaxCode() {
        var p = new MailboxOAuthState.Payload("u1", "ALL", null, List.of("INBOX"));
        assertThat(at(T0, KEY).verify(at(T0, KEY).sign(p))).contains(p);
    }

    @Test
    void twoStatesForTheSameChoiceDiffer() {
        assertThat(at(T0, KEY).sign(PAYLOAD)).isNotEqualTo(at(T0, KEY).sign(PAYLOAD)); // random nonce
    }

    @Test
    void alteredPayloadIsRefused() {
        String state = at(T0, KEY).sign(PAYLOAD);
        int dot = state.indexOf('.');
        String body = new String(Base64.getUrlDecoder().decode(state.substring(0, dot)), StandardCharsets.UTF_8);
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(body.replace("lamine@gmail.com", "attacker@x.vn").getBytes(StandardCharsets.UTF_8))
                + state.substring(dot);
        assertThat(at(T0, KEY).verify(forged)).isEmpty();
    }

    @Test
    void alteredSignatureAndGarbageAreRefused() {
        String state = at(T0, KEY).sign(PAYLOAD);
        String sig = state.substring(state.indexOf('.') + 1);
        String flipped = (sig.charAt(0) == 'A' ? "B" : "A") + sig.substring(1);
        assertThat(at(T0, KEY).verify(state.substring(0, state.indexOf('.') + 1) + flipped)).isEmpty();
        assertThat(at(T0, KEY).verify(null)).isEmpty();
        assertThat(at(T0, KEY).verify("")).isEmpty();
        assertThat(at(T0, KEY).verify("abc")).isEmpty();
        assertThat(at(T0, KEY).verify("a.b.c")).isEmpty();
    }

    @Test
    void expiresAfterTenMinutes() {
        String state = at(T0, KEY).sign(PAYLOAD);
        assertThat(at(T0.plus(MailboxOAuthState.TTL).minusSeconds(1), KEY).verify(state)).isPresent();
        assertThat(at(T0.plus(MailboxOAuthState.TTL), KEY).verify(state)).isEmpty();
    }

    @Test
    void stateSignedWithAnotherKeyIsRefused() {
        byte[] other = new byte[32];
        other[0] = 1;
        assertThat(at(T0, KEY).verify(at(T0, other).sign(PAYLOAD))).isEmpty();
    }

    @Test
    void noKeyVerifiesNothing() {
        assertThat(at(T0, null).verify(at(T0, KEY).sign(PAYLOAD))).isEmpty();
    }
}
