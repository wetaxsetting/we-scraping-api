package com.kosign.wecms.domain.setting.zalooa.repository;

import com.kosign.wecms.domain.setting.zalooa.entity.ZaloTmplInfm;
import com.kosign.wecms.domain.setting.zalooa.entity.ZaloTmplInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link ZaloTmplInfm}. Every method constrains on
 * {@code BIZ_KEY}, per the tenant-isolation rule in {@code CLAUDE.md}.
 */
public interface ZaloTmplInfmRepository extends JpaRepository<ZaloTmplInfm, ZaloTmplInfmId> {

    /**
     * Exact template resolution for the send path — a primary-key lookup, so it
     * cannot silently return another language's template.
     */
    Optional<ZaloTmplInfm> findByBizKeyAndOaIdAndTmplTypeAndLangCdAndUseYn(
            String bizKey, String oaId, String tmplType, String langCd, String useYn);

    /** Every template bound to one Official Account, for the settings screen. */
    List<ZaloTmplInfm> findByBizKeyAndOaId(String bizKey, String oaId);

    /** Bulk load for a tenant's list view, so the screen avoids an N+1 per OA. */
    List<ZaloTmplInfm> findByBizKeyAndOaIdIn(String bizKey, List<String> oaIds);

    void deleteByBizKeyAndOaId(String bizKey, String oaId);
}
