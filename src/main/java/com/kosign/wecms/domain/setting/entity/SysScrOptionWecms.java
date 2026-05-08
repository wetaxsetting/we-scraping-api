package com.kosign.wecms.domain.setting.entity;

import com.kosign.wecms.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * System default column config for a screen / page (legacy
 * {@code SYS_SCR_OPTION_WECMS}). Each row is one column inside one
 * dynamic area of one page; the {@code FIX_YN='Y'} flag locks a column
 * from being hidden / reordered by users (Bank cell on Account Inquiry,
 * etc.).
 *
 * <p>Schema is column-for-column aligned with DEV (`wecmsvndev`,
 * sampled 2026-05-02) — see V35 migration. The only refactor-only
 * additions are {@code FIX_YN} (column-locking feature, see
 * docs/features/view-setting.md §1.8) and the BaseEntity audit columns
 * (REG_ID/REG_DTM/MOD_ID/MOD_DTM); both kept by explicit approval.
 *
 * <p>{@code DYM_AREA_ID} groups columns into logical sub-tables of the
 * same screen. Legacy area names are short tokens like {@code "table1"},
 * {@code "table2"}, {@code "table3"}; refactor-side single-area pages
 * may pass {@code "default"}. {@code FIELD_ID} must be unique across
 * all areas of one {@code WEB_SVC_ID} — DEV uses suffixed FIELD_IDs
 * ({@code BANK_NM_2}, {@code BANK_NM_3}) when a label repeats across
 * areas.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "SYS_SCR_OPTION_WECMS")
@IdClass(SysScrOptionWecmsId.class)
public class SysScrOptionWecms extends BaseEntity {

    @Id
    @Column(name = "WEB_SVC_ID", length = 50, nullable = false)
    private String webSvcId;

    @Id
    @Column(name = "FIELD_ID", length = 100, nullable = false)
    private String fieldId;

    @Column(name = "FIELD_NM", length = 100, nullable = false)
    private String fieldNm;

    /**
     * 1-based display order. INTEGER on local — refactor-only deviation
     * from DEV's NUMERIC(10,0) (V36 reverted V35's NUMERIC alter that
     * tripped Hibernate's BIGINT/NUMERIC schema validator). Functionally
     * equivalent: the 0..99 DISP_ORD domain is well within INTEGER.
     */
    @Column(name = "DISP_ORD", nullable = false)
    private Integer dispOrd;

    @Column(name = "DISP_YN", length = 1, nullable = false)
    private String dispYn;

    @Column(name = "USE_YN", length = 1, nullable = false)
    private String useYn;

    @Column(name = "FIX_YN", length = 1, nullable = false)
    private String fixYn;

    @Column(name = "SIZE", length = 20, nullable = false)
    private String size;

    @Column(name = "DYM_AREA_ID", length = 50, nullable = false)
    private String dymAreaId;

    /**
     * `Y` = column is sortable, `N` = not. Mapped as CHAR(1) to match
     * DEV's `bpchar(1)` schema — `@JdbcTypeCode(SqlTypes.CHAR)` overrides
     * Hibernate's default String→VARCHAR mapping so `validate` accepts
     * the bpchar column without a CHAR→VARCHAR migration. Decision
     * locked 2026-05-02 (V38): keep DB at DEV-parity CHAR(1), adapt the
     * entity instead.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "SORT_YN", length = 1, nullable = false)
    private String sortYn;

    @Column(name = "HTML_CONT", columnDefinition = "text")
    private String htmlCont;
}
