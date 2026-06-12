package com.kosign.wecms.domain.setting.purposesettings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

/**
 * Read-only "purpose is in use" projection. Drives the
 * {@code used_in_transactions} wire field on every list response AND the
 * recursive delete pre-check (OQ-5).
 *
 * <p><b>Backed by a Hibernate {@link Subselect @Subselect}, NOT a DB table or
 * view.</b> The legacy JEX DB has no {@code v_purpose_in_use} object — legacy
 * enforces the "delete blocked by children" rule with a {@code hasActiveChildren}
 * count over {@code BUSI_USAG_SET_INFM}. An earlier refactor materialized that
 * check as a {@code V_PURPOSE_IN_USE} view (archived V81), but the view does not
 * exist in legacy JEX and a refactor-only view is not provisioned. Instead this
 * entity carries the same query as a {@code @Subselect} over the legacy
 * {@code BUSI_USAG_SET_INFM} table, so {@code ddl-auto: validate} never looks for
 * a {@code v_purpose_in_use} relation.
 *
 * <p>Branch coverage matches V81: (a) an active purpose referenced as a parent by
 * an active child (the legacy {@code hasActiveChildren} semantics). Branch (b)
 * (active payment / transfer / expense references) is a future additive
 * {@code UNION ALL} arm — add it here when those consumer tables port in.
 *
 * <p>{@link Immutable @Immutable} flags the entity read-only. {@link Synchronize
 * @Synchronize} lists the base table so Hibernate auto-flushes pending writes to
 * it before querying this entity. The PK shape mirrors {@link BusiUsagSetInfm}:
 * {@code (BIZ_KEY, USE_USAG_CD)}; {@code SELECT DISTINCT} guarantees the tuple is
 * unique so the IdClass works as a real PK.
 */
@Getter
@Setter
@NoArgsConstructor
@Immutable
@Entity
@Synchronize({"BUSI_USAG_SET_INFM"})
@Subselect("""
        SELECT DISTINCT biz_key, use_usag_cd
        FROM (
            SELECT biz_key, parent_use_usag_cd AS use_usag_cd
            FROM busi_usag_set_infm
            WHERE parent_use_usag_cd IS NOT NULL AND use_yn = 'Y'
        ) t
        """)
@IdClass(BusiUsagSetId.class)
public class VPurposeInUse {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "USE_USAG_CD", length = 20, nullable = false)
    private String useUsagCd;
}
