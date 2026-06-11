package com.kosign.wecms.domain.setting.vasaccount.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Allocates {@code BUSI_VAS_ACCT_INFM.vas_acct_seq_no} through the single
 * {@code wb_get_next_val} funnel (V140 #25 — per-tenant {@code MAX+1},
 * copied verbatim from the live JEX function). Routing through the
 * function — rather than a Java-side {@code MAX+1} — keeps the refactor's
 * allocation identical to, and non-colliding with, the live JEX / WABOOKS
 * systems that still call {@code wb_get_next_val} for this table.
 *
 * <p>Concurrency unchanged: the function uses bare {@code MAX+1} (no lock).
 * The caller's write transaction wraps the call + the {@code INSERT}; a PK
 * collision surfaces as {@code DataIntegrityViolationException} and the
 * service layer retries once.
 */
@Service
@RequiredArgsConstructor
public class VasAcctSeqNoGenerator {

    private final LegacyFunctionRepository legacyFunctions;

    /** Return the next {@code vas_acct_seq_no} for the tenant. Caller is
     *  responsible for running this inside the same transaction as the
     *  subsequent {@code save(...)}. */
    public long next(String bizKey) {
        return Long.parseLong(legacyFunctions.nextVasAcctSeqNo(bizKey));
    }
}
