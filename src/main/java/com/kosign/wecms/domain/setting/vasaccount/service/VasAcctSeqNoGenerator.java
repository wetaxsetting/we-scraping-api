package com.kosign.wecms.domain.setting.vasaccount.service;

import com.kosign.wecms.domain.setting.vasaccount.repository.BusiVasAcctInfmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Java-side replacement for the legacy {@code wb_get_next_val(
 * 'busi_vas_acct_infm', 'vas_acct_seq_no', biz_key, …)} PL/pgSQL branch.
 *
 * <p>Strategy: {@code MAX(vas_acct_seq_no) WHERE biz_key = ?} + 1. The
 * caller's write transaction wraps both the read and the eventual
 * {@code INSERT}, so the same tenant inserting two rows in parallel
 * either serialises naturally on the PK conflict (the loser retries
 * once at the service layer) or — under the default {@code READ
 * COMMITTED} — risks producing two rows with the same {@code MAX+1}
 * that collide on the PK.
 *
 * <p>The PK collision surfaces as a {@code DataIntegrityViolationException}
 * from Hibernate, mapped by {@code GlobalExceptionHandler} to
 * {@code WBE_DB} (500). For R&D this is acceptable — VAS Account writes
 * are admin-driven, low-frequency, and from a single browser tab. If
 * concurrent writes become a real concern, the follow-up is a per-
 * tenant {@code PESSIMISTIC_WRITE} lock on a sentinel row or a tenant-
 * scoped sequence.
 *
 * <p>Per the {@code DB Function / Legacy Function Policy} in
 * {@code wecms-api/CLAUDE.md}, this Java-side generator is preferred
 * over adding a new {@code wb_get_next_val} branch.
 */
@Service
@RequiredArgsConstructor
public class VasAcctSeqNoGenerator {

    private final BusiVasAcctInfmRepository repository;

    /** Return the next {@code vas_acct_seq_no} for the tenant. Returns
     *  {@code 1} when the tenant has no rows yet. Caller is responsible
     *  for running this inside the same transaction as the subsequent
     *  {@code save(...)}. */
    public long next(String bizKey) {
        Long max = repository.findMaxSeqNoByBizKey(bizKey);
        return max == null ? 1L : max + 1L;
    }
}
