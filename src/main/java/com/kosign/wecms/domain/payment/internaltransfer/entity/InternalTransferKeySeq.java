package com.kosign.wecms.domain.payment.internaltransfer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Refactor-only helper for the Java-side {@code SCDUL_PYMN_NO}
 * generator (see
 * {@link com.kosign.wecms.domain.payment.internaltransfer.service.InternalTransferKeyGenerator}).
 * Replaces the legacy
 * {@code wb_get_next_val('scdul_pymn_sumr', 'scdul_pymn_no', BIZ_KEY)}
 * branch — the refactor intentionally does NOT add new {@code wb_*}
 * function dependencies per
 * {@code wecms-api-main/CLAUDE.md} §"DB Function / Legacy Function Policy".
 *
 * <p>Not present in DEV / PROD — this table exists only in the refactor's
 * local Flyway schema (created in V85).
 *
 * <p>The entity is mapped read/write but in practice every write goes
 * through the generator's native {@code INSERT … ON CONFLICT … DO UPDATE
 * … RETURNING} statement, so the Java setters here are only consulted by
 * test code.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "INTERNAL_TRANSFER_KEY_SEQ")
@IdClass(InternalTransferKeySeqId.class)
public class InternalTransferKeySeq {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "KEY_DT", nullable = false)
    private LocalDate keyDt;

    @Column(name = "LAST_SEQ", nullable = false)
    private Integer lastSeq;

    @Column(name = "REG_DTM", nullable = false)
    private OffsetDateTime regDtm;

    @Column(name = "MOD_DTM", nullable = false)
    private OffsetDateTime modDtm;
}
