package com.kosign.wecms.domain.setting.zalooa.entity;

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

import java.math.BigDecimal;

/**
 * Settings &gt; Zalo OA — a Zalo-approved message template bound to one
 * Official Account. Maps {@code ZALO_TMPL_INFM} (V254 migration).
 *
 * <p>Templates cannot be created from code: each one is drafted in the Zalo
 * Business console and <b>manually approved by Zalo</b>, which then issues the
 * {@link #tmplId}. This table only records that id so the send path can quote
 * it.
 *
 * <p><b>Language is part of the primary key.</b> WeBill365 keys its equivalent
 * table on a surrogate id and, in one of its two list endpoints, resolves the
 * template with {@code findFirst()} filtered on type alone — so a tenant holding
 * both a Vietnamese and an English template can be shown, and send, the wrong
 * one. Putting {@code LANG_CD} in the key makes resolution exact by
 * construction.
 *
 * <p>{@link #tmplType} is intentionally an open code column: the WeCMS message
 * catalogue has not been decided, so neither the DB nor this entity constrains
 * the value. See {@link com.kosign.wecms.domain.setting.zalooa.enums.ZaloTemplateType}
 * for the provisional Java-side codes.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ZALO_TMPL_INFM")
@IdClass(ZaloTmplInfmId.class)
public class ZaloTmplInfm extends BaseEntity {

    /** Tenant key. PK component. */
    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    /** Owning Official Account. PK component, FK to {@link ZaloOaInfm}. */
    @Id
    @Column(name = "OA_ID", length = 50, nullable = false)
    private String oaId;

    /** Message-type code. PK component. Provisional values in
     *  {@code ZaloTemplateType}; unconstrained at the DB level until the
     *  catalogue is agreed. */
    @Id
    @Column(name = "TMPL_TYPE", length = 20, nullable = false)
    private String tmplType;

    /** {@code EN} / {@code KO} / {@code KM} / {@code VI}, matching
     *  {@link com.kosign.wecms.common.enums.LangCd}. PK component. */
    @Id
    @Column(name = "LANG_CD", length = 3, nullable = false)
    private String langCd;

    /** Template id issued by Zalo on approval — a numeric string such as
     *  {@code 358731}. This is the value sent as {@code template_id}. */
    @Column(name = "TMPL_ID", length = 50, nullable = false)
    private String tmplId;

    /** Zalo's per-message charge for this template. Reference data for the
     *  settings screen; no billing logic reads it yet. */
    @Column(name = "UNIT_PRC", precision = 20, scale = 6)
    private BigDecimal unitPrc;

    /** {@code N} parks a template without deleting it — the send path skips it. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "USE_YN", length = 1, nullable = false, columnDefinition = "CHAR(1) DEFAULT 'Y'")
    private String useYn;
}
