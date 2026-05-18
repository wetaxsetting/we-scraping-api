package com.kosign.wecms.domain.banking.service.cashflow;

import com.kosign.wecms.domain.payment.approvalbox.repository.ExptResolMstrRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;

/**
 * Java replacement for the legacy {@code wb_get_appr_stts(...)}
 * PL/pgSQL helper, scoped to the Cash Flow report's usage pattern.
 *
 * <p>The legacy function returns one of {@code 'P' | 'C' | 'R' | 'I'}
 * based on {@code APPR_TYPE_CD} when the caller is admin or self-draft,
 * else falls back to {@code EXPT_RESOL_APPRVR_INFM.APPRVR_STTS}. The
 * Cash Flow function always calls the helper with
 * {@code apprvr_id = reg_id} (both pulled from {@code erm.reg_id}), so
 * the IF branch always fires and the helper reduces to a simple
 * code switch:
 * <pre>
 *   '00' -> 'P'  (Pending)
 *   '01' -> 'C'  (Completed)   ← Cash Flow gates on this
 *   '02' -> 'R'  (Rejected)
 *   '03' -> 'I'  (In Progress)
 *   else -> NULL
 * </pre>
 *
 * <p>This resolver returns the set of {@code apprSeqNo}s that are
 * Completed (i.e. {@code APPR_TYPE_CD = '01'}) for the supplied tenant
 * and candidate set. Caller filters its source rows by membership.
 *
 * <p>Batched / set-based: the resolver opens one repository call for
 * the full candidate list rather than N per-row lookups, mirroring the
 * legacy CTE's join semantics.
 *
 * <p>Reference: docs/refactor-wecms/features/cash-flow-report.md §3.0.1.
 */
@Component
@RequiredArgsConstructor
public class ApprovalStatusResolver {

    private final ExptResolMstrRepository exptResolRepo;

    /**
     * Resolve which {@code apprSeqNo}s in the candidate set are
     * Completed. Returns an empty set if {@code candidates} is empty.
     *
     * <p>Null entries in {@code candidates} are silently dropped before
     * the IN-list is built — they cannot match anyway and trip the
     * {@code WHERE x IN ()} guard.
     */
    public Set<String> completedSeqNos(String bizKey, Collection<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Set.of();
        }
        Set<String> nonNull = candidates.stream()
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (nonNull.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(exptResolRepo.findCompletedApprSeqNos(bizKey, nonNull));
    }
}
