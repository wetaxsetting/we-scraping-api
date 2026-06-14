package com.kosign.wecms.domain.payment.overseas.repository;

import com.kosign.wecms.domain.payment.entity.OvrsPymnMstr;
import com.kosign.wecms.domain.payment.entity.OvrsPymnMstrId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link OvrsPymnMstr}. Read-only access from the Cash
 * Flow report aggregator + the Approval Details legacy-M-mode fallback
 * (GAP-OVS-MODE). Other consumers (Overseas Payment screens) add their
 * methods as those features land.
 */
public interface OvrsPymnMstrRepository extends JpaRepository<OvrsPymnMstr, OvrsPymnMstrId> {

    /** GAP-OVS-MODE: all legacy "M-mode" remittance rows attached to one
     *  approval (one approval can fan out to several beneficiary rows).
     *  Used by the Approval Details popup to detect + gracefully render an
     *  approval that lives in {@code OVRS_PYMN_MSTR} but not
     *  {@code OVRS_PYMN_SUMR}. Tenant-scoped on {@code BIZ_KEY}. */
    List<OvrsPymnMstr> findByBizKeyAndApprSeqNo(String bizKey, String apprSeqNo);

    /** Direct {@code OVRS_PYMN_NO} lookup against the M-mode master, for the
     *  fallback where the popup id is an {@code OVRS_PYMN_NO} that only
     *  exists in {@code OVRS_PYMN_MSTR}. Tenant-scoped on {@code BIZ_KEY}. */
    Optional<OvrsPymnMstr> findByBizKeyAndOvrsPymnNo(String bizKey, String ovrsPymnNo);

    /**
     * Cash Flow report — overseas-payment source rows.
     *
     * <p>Mirrors the {@code all_payment_data} overseas UNION branch of
     * {@code get_cashflow_report()}: rows where {@code chart_acct_cd}
     * is non-empty AND the year-month is derived from
     * {@code reg_dtm} (registration date, NOT scheduled execution
     * date — Phase 0 finding #5 in §3.1).
     *
     * <p>Approval-status filter applied by
     * {@link com.kosign.wecms.domain.banking.service.cashflow.ApprovalStatusResolver}
     * downstream — this query returns ALL classified rows for the year.
     */
    @Query("""
            SELECT o FROM OvrsPymnMstr o
             WHERE o.bizKey = :bizKey
               AND o.chartAcctCd IS NOT NULL
               AND o.chartAcctCd <> ''
               AND SUBSTRING(o.regDtm, 1, 4) = :year
            """)
    List<OvrsPymnMstr> findCashFlowSourceRows(@Param("bizKey") String bizKey,
                                              @Param("year") String year);
}
