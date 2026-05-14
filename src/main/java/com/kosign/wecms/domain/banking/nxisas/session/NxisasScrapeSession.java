package com.kosign.wecms.domain.banking.nxisas.session;

import com.kosign.wecms.domain.banking.nxisas.gateway.dto.NxisasAcctListEntry;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One in-flight scraping attempt. Holds:
 *  - the session id the frontend echoes back on the OTP submit
 *  - the tenant + user the session is bound to (so a stolen session
 *    id can't be replayed across tenants)
 *  - the bank credential being used (looked up server-side, never
 *    on the wire)
 *  - the current step in the flow
 *  - the most recent gateway result (accounts list when DONE,
 *    challenge prompt when CHALLENGE)
 *
 * <p>State transitions are linear: {@code AWAITING_CHALLENGE} or
 * {@code COMPLETED} or {@code FAILED}. The registry evicts a session
 * once it has been idle past the configured timeout.
 */
@Getter
@RequiredArgsConstructor
public class NxisasScrapeSession {

    public enum Status { AWAITING_CHALLENGE, COMPLETED, FAILED }

    private final UUID sessionId = UUID.randomUUID();
    private final String trSeq;
    private final String bizKey;
    private final String userId;
    private final BigDecimal credentialSeqNo;
    private final String bankModule;

    @Setter(AccessLevel.PUBLIC) private Status status = Status.AWAITING_CHALLENGE;
    @Setter(AccessLevel.PUBLIC) private ScrapeChallengeKind challengeKind;
    @Setter(AccessLevel.PUBLIC) private String challengePrompt;
    @Setter(AccessLevel.PUBLIC) private List<NxisasAcctListEntry> resultAccounts;
    @Setter(AccessLevel.PUBLIC) private String errorCode;
    @Setter(AccessLevel.PUBLIC) private String errorMessage;
    @Setter(AccessLevel.PUBLIC) private Instant lastTouched = Instant.now();
    /** Sub-type discriminator captured at session start so resumed
     *  steps (OTP submit, ACCT_LIST) route to the same Coocon adapter
     *  the LOGIN used. 0 = Corporate / 1 = Personal / 2 = VCB DigiBiz
     *  etc. Defaults to 0. */
    @Setter(AccessLevel.PUBLIC) private int nxisasGbSeqNo;
    /** Coocon thread `Class` field (CORPORATE / COMMON / ""). Legacy
     *  FNSH_0100_001:204-205 reads it from the OSMC API config table per
     *  BANK_CD; spike defaults to "" and the same value is reused on
     *  resumed OTP / ACCT_LIST steps so the gateway routes consistently. */
    @Setter(AccessLevel.PUBLIC) private String bankCategory = "";

    // --- Gateway continuation state (FNSH_0100_001:249-254) ---
    // After LOGIN succeeds the gateway returns a SAS_SessionID plus 5
    // Common fields that must be echoed back verbatim on every subsequent
    // call in the same logical thread (OTP, ACCT_LIST). Omitting them
    // causes the gateway to start a fresh anonymous session and return
    // an empty account list even when login was reported as successful.
    @Setter(AccessLevel.PUBLIC) private String sasSessionID = "";
    @Setter(AccessLevel.PUBLIC) private String gwSysNo = "";
    @Setter(AccessLevel.PUBLIC) private String lbsNo = "";
    @Setter(AccessLevel.PUBLIC) private String ssSysNo = "";
    @Setter(AccessLevel.PUBLIC) private String threadNo = "";

    public void touch() {
        lastTouched = Instant.now();
    }
}
