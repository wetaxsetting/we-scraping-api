package com.kosign.wecms.domain.payment.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fetches approver rows (EXPT_RESOL_APPRVR_INFM) for a set of approval sequences.
 * Used to hydrate the {@code approvers} field on each
 * {@link com.kosign.wecms.domain.payment.dto.OverseasScheduledRow}.
 *
 * <p>Mirrors pymn_0007_001_r001_act.jsp which calls
 * {@code ExptResolApprvrInfmDAO.selectList} per row. We batch the lookup
 * across all rows in one query to avoid N+1.
 */
@Repository
public class OverseasApproverRepository {

    @PersistenceContext
    private EntityManager em;

    /**
     * Returns all approver rows for the given approval sequences, grouped by
     * {@code appr_seq_no}. Each inner list is ordered by {@code apprvr_line_no ASC}.
     *
     * <p>Column positions of the returned Object[] per row:
     * <pre>
     *  0  appr_seq_no
     *  1  apprvr_line_no
     *  2  apprvr_id
     *  3  apprvr_nm
     *  4  apprvr_stts
     *  5  mod_dtm
     *  6  reg_img_url
     * </pre>
     */
    @SuppressWarnings("unchecked")
    public Map<String, List<Object[]>> findByApprSeqNos(String bizKey, List<String> apprSeqNos) {
        if (apprSeqNos == null || apprSeqNos.isEmpty()) {
            return Map.of();
        }
        List<Object[]> rows = em.createNativeQuery("""
                SELECT appr_seq_no, apprvr_line_no, apprvr_id, apprvr_nm,
                       apprvr_stts, mod_dtm, reg_img_url
                  FROM expt_resol_apprvr_infm
                 WHERE biz_key = :bizKey
                   AND appr_seq_no IN :apprSeqNos
                 ORDER BY appr_seq_no, apprvr_line_no
                """)
                .setParameter("bizKey", bizKey)
                .setParameter("apprSeqNos", apprSeqNos)
                .getResultList();

        return rows.stream().collect(
                Collectors.groupingBy(r -> (String) r[0]));
    }
}
