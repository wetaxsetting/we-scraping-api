package com.kosign.wecms.domain.setting.entity;

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
 * dynamic area of one page.
 *
 * <p>Schema is column-for-column aligned with live JEX — the exact
 * 10 columns ({@code V93}): WEB_SVC_ID, FIELD_ID, FIELD_NM, DISP_ORD,
 * DISP_YN, USE_YN, SIZE, DYM_AREA_ID, SORT_YN, HTML_CONT. The entity maps
 * exactly those columns so {@code ddl-auto=validate} passes against the
 * unmodified live JEX table. It deliberately does NOT extend
 * {@code BaseEntity}: JEX's {@code SYS_SCR_OPTION_WECMS} is a
 * Flyway-seeded config table with NO audit columns (REG/MOD), so mapping
 * them would fail validate. SYS rows are read-only at runtime.
 *
 * <p>Column-locking (a column the user cannot hide / reorder, e.g. the
 * Bank cell on Account Inquiry) is NOT a DB concern: the live JEX
 * {@code SYS_SCR_OPTION_WECMS} carries no such flag. Fixed columns are
 * declared statically in the frontend View Setting registry. The
 * structural CHECKBOX / ETC columns are still locked server-side via
 * {@code ScreenOptionService.SPECIAL_FIELD_IDS}, independent of any DB
 * column.
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
public class SysScrOptionWecms {

    @Id
    @Column(name = "WEB_SVC_ID", length = 50, nullable = false)
    private String webSvcId;

    @Id
    @Column(name = "FIELD_ID", length = 100, nullable = false)
    private String fieldId;

    @Column(name = "FIELD_NM", length = 100, nullable = false)
    private String fieldNm;

    /**
     * 1-based display order. JEX {@code NUMERIC(10,0)} — mapped via
     * {@code @JdbcTypeCode(NUMERIC)} so Hibernate validate passes against
     * the live JEX column (2026-06-09 JEX-parity alignment; supersedes the
     * earlier local-INTEGER convention from V36).
     */
    @JdbcTypeCode(SqlTypes.NUMERIC)
    @Column(name = "DISP_ORD", precision = 10, scale = 0, nullable = false)
    private Integer dispOrd;

    @Column(name = "DISP_YN", length = 1, nullable = false)
    private String dispYn;

    @Column(name = "USE_YN", length = 1, nullable = false)
    private String useYn;

    @Column(name = "SIZE", length = 10, nullable = false)
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
