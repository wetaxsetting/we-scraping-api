package com.kosign.wecms.domain.payment.internaltransfer.service;

import com.kosign.wecms.domain.payment.internaltransfer.repository.InternalTransferKeySeqRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Java-side generator for {@code SCDUL_PYMN_SUMR.SCDUL_PYMN_NO}.
 * Replaces the legacy
 * {@code wb_get_next_val('scdul_pymn_sumr', 'scdul_pymn_no', BIZ_KEY)}
 * branch — the refactor does NOT add new {@code wb_*} function
 * dependencies per
 * {@code wecms-api/CLAUDE.md} §"DB Function / Legacy Function Policy".
 *
 * <h2>Format</h2>
 *
 * <pre>{@code
 * SCDUL_PYMN_NO = <YYYYMMDD><6-digit-tenant-day-seq>
 *
 * e.g.  20260513000001
 *       20260513000002
 *       ...
 * }</pre>
 *
 * <p>{@code YYYYMMDD} is Vietnam-local date (UTC+7) — same time zone the
 * cut-off-time validator uses — so batches saved on the same Vietnam
 * day share a date prefix regardless of UTC offset.
 *
 * <h2>Concurrency</h2>
 *
 * <p>{@code REQUIRES_NEW} propagation: the sequence advance runs in its
 * own transaction so it commits even if the outer business-write
 * transaction rolls back. Two concurrent saves on the same
 * {@code (tenant, day)} are serialised by PostgreSQL's row-level lock
 * that {@code ON CONFLICT … DO UPDATE} acquires — see
 * {@link InternalTransferKeySeqRepository#allocateNext}. Format
 * divergence from legacy DEV is OQ-3 in
 * {@code docs/refactor-wecms/features/internal-transfer.md}; the
 * refactor is local-only R&amp;D so cross-system traceability does not
 * apply.
 *
 * <h2>Clock</h2>
 *
 * <p>The {@link Clock} is injected so tests can pin the "today" date
 * deterministically. Production wiring binds {@code Clock.systemUTC()}
 * via a {@code @Bean} in
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferConfig}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InternalTransferKeyGenerator {

    /** Vietnam timezone — UTC+7. Matches
     *  {@code DateTimeUtils.gmtNowVanCenter()} on the JEX side. */
    static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final InternalTransferKeySeqRepository keySeqRepository;
    private final Clock clock;

    /**
     * Allocate the next {@code SCDUL_PYMN_NO} for the given tenant. The
     * caller is responsible for tenant scope — pass
     * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
     *
     * @return a new 14-character SCDUL_PYMN_NO that is unique per tenant
     *         (collision-free even under concurrent calls).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextScdulPymnNo(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) {
            throw new IllegalArgumentException("bizKey is required");
        }
        LocalDate keyDt = LocalDate.now(clock.withZone(VIETNAM_ZONE));
        int seq = keySeqRepository.allocateNext(bizKey, keyDt);
        String key = format(keyDt, seq);
        log.debug("feature=internal-transfer allocated scdul_pymn_no={} bizKey={} keyDt={} seq={}",
                key, bizKey, keyDt, seq);
        return key;
    }

    /** Visible for testing. */
    static String format(LocalDate keyDt, int seq) {
        if (seq < 1 || seq > 999_999) {
            throw new IllegalStateException(
                "internal-transfer key sequence out of range (1..999999): " + seq);
        }
        return String.format("%04d%02d%02d%06d",
                keyDt.getYear(), keyDt.getMonthValue(), keyDt.getDayOfMonth(), seq);
    }
}
