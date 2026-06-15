package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generator for {@code SCDUL_PYMN_SUMR.SCDUL_PYMN_NO}, shared by every
 * scheduled-payment module (Vendor / Expense / Payroll / Internal Transfer).
 *
 * <h2>OD-2 reversal (2026-06-09 — MR-1)</h2>
 *
 * <p>This generator now routes key allocation through the single
 * {@link LegacyFunctionRepository} funnel
 * ({@code wb_get_next_val('scdul_pymn_sumr', 'scdul_pymn_no', biz_key)},
 * V140 branch&nbsp;#36 — per-tenant plain-int {@code MAX+1}). It previously minted
 * a date-prefixed {@code <YYYYMMDD><6-digit-seq>} key from a refactor-only
 * counter table (removed); that approach diverged from the live JEX DB, which
 * owns this key via {@code wb_get_next_val}. Routing through the funnel makes the
 * refactor mint the same value the JEX system would for the tenant. See
 * {@code wecms-api/CLAUDE.md} §"DB Function / Legacy Function Policy" and the V140 header.
 *
 * <h2>Detail keys are out of scope (MR-2)</h2>
 *
 * <p>{@code SCDUL_PYMN_SUMR_DETL.SCDUL_PYMN_NO} is still copied verbatim from the
 * master (FK), and {@code SCDUL_PYMN_DETL_NO} is still a Java per-document line
 * ordinal — those are deliberately unchanged here pending separate PO
 * confirmation (JEX scopes {@code scdul_pymn_detl_no} per tenant; the refactor
 * scopes it per document). V140 branches&nbsp;#37/#38 exist but stay dormant.
 *
 * <h2>Concurrency</h2>
 *
 * <p>The funnel {@code MAX+1} has no row lock (unlike the retired
 * {@code ON CONFLICT} counter), so two concurrent saves on the same tenant can
 * read the same {@code MAX} and collide on the {@code SCDUL_PYMN_SUMR} PK at
 * INSERT. This matches every other {@code MAX+1} funnel branch; the documented
 * mitigation is a one-shot retry on {@code DataIntegrityViolationException} at
 * the INSERT site. Adding that retry touches the four create services and is
 * intentionally deferred (single-tenant R&amp;D traffic does not hit the race in
 * practice — same stance the legacy counter's Javadoc recorded).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferKeyGenerator {

    private final LegacyFunctionRepository legacyFunctions;

    /**
     * Allocate the next {@code SCDUL_PYMN_NO} for the given tenant via the
     * {@code wb_get_next_val} funnel. Runs in the caller's transaction
     * ({@code MANDATORY}) so the {@code MAX+1} read and the subsequent
     * {@code SCDUL_PYMN_SUMR} INSERT share one transactional view.
     *
     * @param bizKey tenant scope — pass
     *               {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
     * @return the new per-tenant {@code SCDUL_PYMN_NO} (plain numeric text).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextScdulPymnNo(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        String key = legacyFunctions.nextScdulPymnNo(bizKey);
        log.debug("feature=scheduled-payment allocated scdul_pymn_no={} bizKey={} (wb_get_next_val funnel)",
                key, bizKey);
        return key;
    }
}
