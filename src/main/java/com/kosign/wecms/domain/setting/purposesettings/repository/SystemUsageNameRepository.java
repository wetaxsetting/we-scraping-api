package com.kosign.wecms.domain.setting.purposesettings.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only lookup of the <b>system usage master</b> localized names
 * ({@code USAG_INFM_LANG_DETL}) keyed by {@code use_usag_cd} +
 * {@code lang_code}. Legacy {@code comm_0187_001} shows the standard
 * name of a system-default purpose (e.g. code 40 → "Loan Repaid") even
 * when the per-business {@code BUSI_USAG_SET_INFM.use_usag_nm} is NULL;
 * the display name is
 * {@code COALESCE(biz use_usag_nm, usag_infm_lang_detl.usag_nm[lang])}.
 *
 * <p><b>Resilience.</b> {@code USAG_INFM_LANG_DETL} is a legacy PROD
 * active-list table that may not yet be present in a partially-migrated
 * refactor dev DB. The query runs in its own {@code REQUIRES_NEW}
 * transaction so a "relation does not exist" failure rolls back only
 * this inner tx — the caller's read transaction is untouched. The
 * caller wraps the call in try/catch and falls back to an empty map
 * (names simply stay blank, matching pre-migration behaviour). Once a
 * DBA seeds the master table, names appear with no code change.
 *
 * <p>Native query is used deliberately: no {@code @Entity} is mapped to
 * the table so Hibernate {@code ddl-auto=validate} never fails at
 * startup on a DB where the table is absent. This is a plain read — the
 * single-executor {@code wb_get_next_val} funnel rule does not apply.
 */
@Slf4j
@Repository
public class SystemUsageNameRepository {

    /** Resolved localized name + description for one system usage code. */
    public record Name(String nm, String cont) { }

    @PersistenceContext
    private EntityManager em;

    /**
     * Resolve localized names for the given {@code use_usag_cd} set in
     * one query. Returns an empty map for an empty input. Throws if the
     * master table is unavailable — the caller catches and degrades.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    @SuppressWarnings("unchecked")
    public Map<String, Name> resolveByLang(Collection<String> useUsagCds, String langCode) {
        Map<String, Name> out = new HashMap<>();
        if (useUsagCds == null || useUsagCds.isEmpty()) return out;
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT use_usag_cd, usag_nm, usag_cont "
                        + "FROM usag_infm_lang_detl "
                        + "WHERE lang_code = :lang AND use_usag_cd IN (:cds)")
                .setParameter("lang", langCode)
                .setParameter("cds", useUsagCds)
                .getResultList();
        for (Object[] r : rows) {
            out.put((String) r[0], new Name((String) r[1], (String) r[2]));
        }
        return out;
    }
}
