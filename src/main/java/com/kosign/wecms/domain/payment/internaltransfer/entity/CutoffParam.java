package com.kosign.wecms.domain.payment.internaltransfer.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * {@code TB_CUTOFF_PARAM} — runtime-editable scalar cut-off params (e.g.
 * {@code large-txn-threshold}). Refactor-only table (PO-approved 2026-07-09);
 * created + seeded by {@code Claude Code (AI)/sql/cutoff-config_20260709.sql}.
 *
 * <p><b>Read-only in Phase 2</b> — {@link CutoffConfigProvider} reads it and
 * falls back to {@code application.yml} when a param is absent / disabled.
 */
@Entity
@Table(name = "TB_CUTOFF_PARAM")
@Getter
@Setter
public class CutoffParam extends BaseEntity {

    /** e.g. {@code large-txn-threshold}. */
    @Id
    @Column(name = "PARAM_KEY", length = 50)
    private String paramKey;

    /** e.g. {@code 500000000}. */
    @Column(name = "PARAM_VALUE", length = 50)
    private String paramValue;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ENABLE_YN", length = 1)
    private String enableYn;

    @Column(name = "CHANGE_REASON", length = 500)
    private String changeReason;
}
