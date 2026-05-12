package com.kosign.wecms.common.auditlog.repository;

import com.kosign.wecms.common.auditlog.entity.AuditLogEntry;
import com.kosign.wecms.common.auditlog.entity.AuditLogEntryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/**
 * Thin Spring Data wrapper around the legacy {@code wb_get_next_val}
 * PL/pgSQL function for {@code audit_logs_infm.audit_logs_seq_no}
 * allocation. Mirrors DEV's allocation strategy — one seq_no per audit
 * event, reused across the per-locale row fan-out (verified DEV
 * 2026-05-09: rows for KO/VI/EN at the same {@code reg_dtm} share a
 * single {@code audit_logs_seq_no} value).
 *
 * <p>The function is provisioned by Flyway V66 (extends V27's branch
 * set with the {@code audit_logs_infm} entry).
 *
 * <p>Sharing the entity type with {@link AuditLogEntryRepository} keeps
 * Spring Data from creating a second JPA persistence-context binding —
 * the entity is unused on this method (the call goes straight through
 * to the function), but it satisfies the JpaRepository signature.
 */
public interface AuditLogSeqNoRepository
        extends JpaRepository<AuditLogEntry, AuditLogEntryId> {

    /**
     * Allocate the next {@code audit_logs_seq_no} for the given event
     * scope. Mirrors the legacy 5-arg shape — the function uses
     * {@code MAX(audit_logs_seq_no::numeric) + 1} (untenanted, matches
     * DEV byte-for-byte). The args are required by the function's
     * argument-presence guards but otherwise unused in the body.
     *
     * @param bizKey caller's tenant — required (presence check)
     * @param refNo  entity name (e.g. {@code "TB_FIRM_CONF"}) — required
     * @param refNo1 first PK component — required
     * @return next seq_no; never {@code null} for the allocator
     *         branch (function returns NULL only for unknown
     *         table/column combinations, which can't happen here)
     */
    @Query(
            value = "SELECT wb_get_next_val("
                  + "'audit_logs_infm', 'audit_logs_seq_no', "
                  + ":bizKey, :refNo, :refNo1)",
            nativeQuery = true)
    BigDecimal allocateNextSeqNo(
            @Param("bizKey") String bizKey,
            @Param("refNo")  String refNo,
            @Param("refNo1") String refNo1);
}
