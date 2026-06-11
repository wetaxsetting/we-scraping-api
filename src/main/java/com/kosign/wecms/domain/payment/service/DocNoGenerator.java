package com.kosign.wecms.domain.payment.service;

import com.kosign.wecms.common.legacy.LegacyFunctionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates the next {@code DOC_NO} for {@code EXPT_RESOL_MSTR}
 * ({@code {prefix}_{date}_{seq}}, e.g. {@code VO_260512_001}).
 *
 * <h2>Funnel migration (Phase 1, 2026-06-10)</h2>
 *
 * <p>This now delegates to the single {@code wb_get_next_val} funnel —
 * {@link LegacyFunctionRepository#nextDocNo(String)} →
 * {@code SELECT wb_get_next_val('expt_resol_mstr','doc_no', biz_key)} (V140 branch&nbsp;#39).
 * The DB branch is <strong>env-set-driven</strong>: prefix (SYS/BUSI_ENV_SET_INFM 00071,
 * default {@code VO}), date format (00072, default {@code YYMMDD}) and zero-pad length
 * (length of 00073, default 3), with per-tenant overrides gated on 00070='Y', a UTC date
 * ({@code timezone('utc', now())}) and a per-tenant daily MAX+1 of the trailing numeric part.
 * That is exactly what this class used to compute in Java, so the migration is
 * behaviour-neutral while making the refactor mint the same {@code DOC_NO} the live
 * JEX/UAT/PROD DB would. The former Java env-set reads + per-day MAX were removed — the
 * SQL branch owns that logic now.
 *
 * <p><strong>Concurrency:</strong> {@code DOC_NO} carries no unique constraint on
 * {@code EXPT_RESOL_MSTR} (PK is {@code (BIZ_KEY, APPR_SEQ_NO)}). Two concurrent requests
 * on the same date may receive the same {@code DOC_NO} in an extreme race — accepted legacy
 * parity (the DB function has the same window); no retry is required.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocNoGenerator {

    private final LegacyFunctionRepository legacyFunctions;

    /**
     * Returns the next {@code DOC_NO} for the tenant via the {@code wb_get_next_val} funnel.
     */
    @Transactional(readOnly = true)
    public String nextDocNo(String bizKey) {
        String docNo = legacyFunctions.nextDocNo(bizKey);
        log.debug("feature=doc-no-generator bizKey={} docNo={} (wb_get_next_val funnel)", bizKey, docNo);
        return docNo;
    }
}
