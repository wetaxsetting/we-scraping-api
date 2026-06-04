package com.kosign.wecms.domain.einvoice.repository;

import com.kosign.wecms.domain.einvoice.entity.TrcoActvHstr;
import com.kosign.wecms.domain.einvoice.entity.TrcoActvHstrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@link TrcoActvHstr}. Tenant-scoped on
 * every method; the e-Invoice auto-registrar is the only writer.
 */
public interface TrcoActvHstrRepository
        extends JpaRepository<TrcoActvHstr, TrcoActvHstrId> {

    /**
     * Dedup guard mirroring legacy {@code c002:895-907}: a counterparty
     * activity row is written only when no row already exists for the
     * {@code (BIZ_KEY, DV_CD, TASK_CD, API_APV_KEY)} tuple, so a re-scrape
     * of the same invoice never doubles the ledger.
     */
    boolean existsByBizKeyAndDvCdAndTaskCdAndApiApvKey(
            String bizKey, String dvCd, String taskCd, String apiApvKey);

    /**
     * Per-tenant next {@code ACTV_SEQ} (MAX+1). Java-side generator
     * preferred over a {@code wb_get_next_val} branch per the DB Function
     * policy (new table, single consumer). The table starts empty locally
     * and only the auto-registrar writes it, so every {@code ACTV_SEQ}
     * value is numeric and the cast is safe.
     */
    @Query(value = """
        SELECT COALESCE(MAX(CAST(actv_seq AS BIGINT)), 0) + 1
        FROM trco_actv_hstr
        WHERE biz_key = :bizKey
        """, nativeQuery = true)
    long nextActvSeq(@Param("bizKey") String bizKey);
}
