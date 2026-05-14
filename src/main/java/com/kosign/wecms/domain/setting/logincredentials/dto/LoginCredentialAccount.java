package com.kosign.wecms.domain.setting.logincredentials.dto;

import java.math.BigDecimal;

/**
 * One credential row inside a {@link LoginCredentialGroup}. Multiple
 * accounts (e.g. several VCB logins under one bank) are surfaced as
 * sibling entries under the same group; GDT always has exactly one.
 *
 * <p>The password ciphertext is NEVER on the wire — only the login id
 * (username). Plaintext password is reachable only via the
 * {@code /reveal} endpoint after re-auth.
 */
public record LoginCredentialAccount(
        BigDecimal seqNo,
        String     loginId,
        BigDecimal nxisasGbSeqNo
) { }
