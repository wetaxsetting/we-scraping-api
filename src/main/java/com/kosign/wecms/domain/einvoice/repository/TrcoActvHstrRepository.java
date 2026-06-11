package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.TrcoActvHstr;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link TrcoActvHstr}. Tenant-scoped on
 * every method; the e-Invoice auto-registrar is the only writer.
 */
public interface TrcoActvHstrRepository
        extends JpaRepository<TrcoActvHstr, String> {

    /**
     * Dedup guard mirroring legacy {@code c002:895-907}: a counterparty
     * activity row is written only when no row already exists for the
     * {@code (BIZ_KEY, DV_CD, TASK_CD, API_APV_KEY)} tuple, so a re-scrape
     * of the same invoice never doubles the ledger.
     */
    boolean existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(
            String bizKey, String dvCd, String taskCd, String apiApvKey);

    // ACTV_SEQ allocation now routes through
    // LegacyFunctionRepository.nextTrcoActvSeq (V140 #6 — the GLOBAL
    // sq_actv_seq sequence, matching the live JEX function whose ACTV_SEQ PK
    // is global-unique) so every wb_get_next_val call goes through the one funnel.
}
