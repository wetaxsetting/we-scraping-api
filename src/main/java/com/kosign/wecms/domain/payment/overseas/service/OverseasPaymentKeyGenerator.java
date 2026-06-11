package com.kosign.wecms.domain.payment.overseas.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generator for {@code OVRS_PYMN_SUMR.OVRS_PYMN_NO}.
 *
 * <p>As of the MR-2C funnel migration (2026-06-10) this is a thin delegator to the single
 * {@code wb_get_next_val} funnel: {@code Spring → LegacyFunctionRepository.nextOvrsPymnNo →
 * nextVal → wb_get_next_val} (V140 #63 — per-tenant plain-int MAX+1 over
 * {@code OVRS_PYMN_MSTR UNION OVRS_PYMN_SUMR}). This matches the live JEX behaviour and is the
 * overseas equivalent of the MR-1 SCDUL_PYMN_NO reversal.
 *
 * <h2>Format change</h2>
 * <p>The former date-prefixed Java format ({@code <YYYYMMDD><6-digit-tenant-day-seq>}, e.g.
 * {@code 20260513000001}, backed by the {@code OVERSEAS_PAYMENT_KEY_SEQ} table) is retired. The
 * value is now plain legacy numeric text ({@code 1, 2, 3, …}) — identical to what the live JEX
 * system mints. {@code OVRS_PYMN_NO} is an opaque key everywhere it is consumed (PK, detail FK,
 * path-variable id, list/result join), so the format change is transparent to all readers.
 *
 * <p><b>Concurrency:</b> the funnel is a lock-free {@code MAX+1}, so two concurrent creates can
 * read the same {@code MAX} and collide on the PK. The generator runs {@code MANDATORY} (the
 * {@code MAX+1} read and the subsequent {@code OVRS_PYMN_SUMR} INSERT share one transactional
 * view — same stance as {@code InternalTransferKeyGenerator}); the create site
 * ({@code OverseasPaymentExcelService.submit}) owns the boundary-level one-shot retry on
 * {@code DataIntegrityViolationException}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverseasPaymentKeyGenerator {

    private final LegacyFunctionRepository legacyFunctions;

    /**
     * Allocate the next {@code OVRS_PYMN_NO} for the given tenant via the
     * {@code wb_get_next_val} funnel (V140 #63). Runs in the caller's transaction
     * ({@code MANDATORY}) so the {@code MAX+1} read and the INSERT share one transactional view.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String nextOvrsPymnNo(String bizKey) {
        if (bizKey == null || bizKey.isBlank()) throw new IllegalArgumentException("bizKey is required");
        String key = legacyFunctions.nextOvrsPymnNo(bizKey);
        log.debug("feature=overseas-payment allocated ovrs_pymn_no={} bizKey={} (wb_get_next_val funnel)",
                key, bizKey);
        return key;
    }
}
