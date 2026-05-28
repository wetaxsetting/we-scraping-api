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
 *
 * <p>{@code loginType} is the human-readable sub-type label stored in
 * {@code BANK_CERT_INFO.CERT_INFO_DETL_CON3} (e.g. "Corporate",
 * "Personal Connect", "DigiBiz"). The legacy popover did not surface
 * it, which made two rows with the same {@code loginId} but different
 * sub-types render as visual duplicates. The refactor exposes it so
 * the popover can disambiguate. Null/blank for GDT and for legacy
 * rows registered before the sub-type discriminator was captured.
 */
public record LoginCredentialAccount(
        BigDecimal seqNo,
        String     loginId,
        BigDecimal nxisasGbSeqNo,
        String     loginType
) { }
