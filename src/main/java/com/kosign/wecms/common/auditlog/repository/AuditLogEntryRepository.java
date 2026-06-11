package com.kosign.wecms.common.auditlog.repository;

import com.kosign.wecms.common.auditlog.entity.AuditLogsInfm;
import com.kosign.wecms.common.auditlog.entity.AuditLogsInfmId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Shared repository over {@code AUDIT_LOGS_INFM}. Read-only here in
 * Phase 2 — the writer service is owned by the future audit-log
 * feature MR (Phase 1 P-1). Today this repo backs only the
 * firm-banking-settings audit-read endpoint; other features adopting
 * audit-log reads consume the same interface.
 *
 * <p>The lookup matches the legacy detail-audit query at
 * {@code comm_0220_003_r001_act.jsp:74-86} — filter by
 * {@code (BIZ_KEY, REF_NO, REF_NO1, REF_NO2, WSVC_ID, LANG_CD)} and
 * order by {@code REG_DTM DESC}. V63 adds a covering index on the
 * filter tuple so the plan is index-driven even as the table grows.
 *
 * <p>Tenant scoping: the caller passes {@code bizKey} explicitly;
 * every consumer reads it from
 * {@link com.kosign.wecms.common.tenant.TenantContext#currentBizKey()}.
 */
public interface AuditLogEntryRepository
        extends JpaRepository<AuditLogsInfm, AuditLogsInfmId> {

    /**
     * Audit history for a single referenced row in the caller's
     * locale, latest-first. Mirrors the legacy detail-audit query
     * shape — every parameter is required and corresponds to a
     * non-nullable predicate, except {@code refNo2} which is allowed
     * to be {@code null} on the legacy schema (pass the actual value
     * for firm-banking-settings; equality on a non-null PK component
     * never matches a null column).
     */
    List<AuditLogsInfm> findByBizKeyAndRefNoAndRefNo1AndRefNo2AndWsvcIdAndLangCdOrderByRegDtmDesc(
            String bizKey,
            String refNo,
            String refNo1,
            String refNo2,
            String wsvcId,
            String langCd);

    /**
     * Variant for features whose audit rows always carry
     * {@code REF_NO2 = NULL} (single-column natural PK on the
     * referenced row — e.g. Login Credentials, where the only
     * locator on {@code BANK_CERT_INFO} / {@code GDT_CERT_INFO} is
     * {@code SEQ_NO}). Derived-name JPA requires the explicit
     * {@code RefNo2IsNull} clause; equality against a {@code null}
     * argument would never match.
     */
    List<AuditLogsInfm> findByBizKeyAndRefNoAndRefNo1AndWsvcIdAndLangCdAndRefNo2IsNullOrderByRegDtmDesc(
            String bizKey,
            String refNo,
            String refNo1,
            String wsvcId,
            String langCd);
}
