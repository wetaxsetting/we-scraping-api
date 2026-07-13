package com.kosign.wecms.domain.payment.internaltransfer.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code TB_CUTOFF_CONFIG} — one runtime-editable bank cut-off window per
 * {@code (BANK_KEY, NETWORK)}. Refactor-only table (no legacy JEX counterpart,
 * PO-approved 2026-07-09); created by the DBA-run script
 * {@code Claude Code (AI)/sql/cutoff-config_20260709.sql} and seeded from
 * {@code application.yml}.
 *
 * <p><b>Read-only in Phase 2</b> — {@link CutoffConfigProvider} reads these rows
 * and merges them over the {@code application.yml} fallback. No write path yet
 * (Phase 3). Column shapes match the DDL so {@code ddl-auto: validate} passes.
 */
@Entity
@Table(name = "TB_CUTOFF_CONFIG")
@IdClass(CutoffConfigId.class)
@Getter
@Setter
public class CutoffConfig extends BaseEntity {

    /** SHINHAN | WOORI | VCB (brand — matches CutoffValidator.resolveBank). */
    @Id
    @Column(name = "BANK_KEY", length = 20)
    private String bankKey;

    /** NAPAS | CITAD | CITAD_API | SWIFT | CITAD_LARGE. */
    @Id
    @Column(name = "NETWORK", length = 20)
    private String network;

    /** Vietnam-local {@code "HH:mm"} window open. */
    @Column(name = "START_TIME", length = 5)
    private String startTime;

    /** Vietnam-local {@code "HH:mm"} cut-off. */
    @Column(name = "END_TIME", length = 5)
    private String endTime;

    /** Vietnam-local {@code "HH:mm"} FE soft-confirm zone; {@code null} where none. */
    @Column(name = "WARNING_TIME", length = 5)
    private String warningTime;

    /** {@code 'Y'} active; {@code 'N'} → provider falls back to the YAML default. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ENABLE_YN", length = 1)
    private String enableYn;

    /** Bank-confirmation note / ticket ref (auditability). */
    @Column(name = "CHANGE_REASON", length = 500)
    private String changeReason;
}
