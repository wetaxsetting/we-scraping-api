package com.kosign.wecms.domain.setting.purposesettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Read-only projection over the {@code v_purpose_in_use} view (V81).
 * Drives the {@code used_in_transactions} wire field on every list
 * response AND the recursive delete pre-check (OQ-5).
 *
 * <p>The view fans together branch (a) — purposes referenced as
 * parents by an active child — and branch (b) — purposes referenced
 * by active payment / transfer / expense rows (added per consumer
 * table as those entities port into the refactor). One repository
 * lookup ({@code existsByPk}) covers both branches because they share
 * the same view.
 *
 * <p>The PK shape mirrors {@link BusiUsagSet}: {@code (BIZ_KEY,
 * USE_USAG_CD)}. The view's {@code SELECT DISTINCT} guarantees the
 * tuple is unique, so the IdClass works as a real PK even though the
 * view has no enforced uniqueness in the database.
 */
@Getter
@Setter
@NoArgsConstructor
@Immutable
@Entity
@Table(name = "V_PURPOSE_IN_USE")
@IdClass(BusiUsagSetId.class)
public class PurposeInUseRow {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USE_USAG_CD", length = 20, nullable = false)
    private String useUsagCd;
}
