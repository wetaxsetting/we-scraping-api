package com.kosign.wecms.domain.payment.cardtransactions.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Polymorphic memo / note row. One row per memo, attached to any
 * parent record via {@code (WEB_SVC_ID, REF_NO[, REF_NO2..REF_NO9])}.
 *
 * <p>Mirrors legacy {@code wabooks.entity.MemoInfm}. First refactor
 * consumer is the Card Transactions row icon (legacy
 * {@code card_0001_001.ts:1242-1276}); future Memoable parents
 * (Banking inquiry, Internal Transfer, Project / szon) attach by
 * passing their own {@code WEB_SVC_ID} + parent ids.
 *
 * <p>{@code COLOR} stores the legacy {@code wabooks.enums.ColorCd}
 * value verbatim ({@code white / blue / green / yellow / orange /
 * red / gray / purple}). DEV column is {@code varchar(6)} which fits
 * every value.
 *
 * <p>Schema landed in V202; DEV parity verified via
 * {@code postgres_dev} MCP 2026-05-22.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "MEMO_INFM")
@IdClass(MemoInfmId.class)
public class MemoInfm {

    @Id
    @Column(name = "BIZ_KEY", length = 20, nullable = false)
    private String bizKey;

    @Id
    @Column(name = "WEB_SVC_ID", length = 50, nullable = false)
    private String webSvcId;

    @Id
    @Column(name = "SEQ_NO", precision = 10, scale = 0, nullable = false)
    private BigDecimal seqNo;

    @Column(name = "MEMO", length = 200)
    private String memo;

    @Column(name = "COLOR", length = 6)
    private String color;

    @Column(name = "REF_NO", length = 50, nullable = false)
    private String refNo;

    @Column(name = "REF_NO2", length = 50)
    private String refNo2;

    @Column(name = "REF_NO3", length = 50)
    private String refNo3;

    @Column(name = "REF_NO4", length = 50)
    private String refNo4;

    @Column(name = "REF_NO5", length = 50)
    private String refNo5;

    @Column(name = "REF_NO6", length = 50)
    private String refNo6;

    @Column(name = "REF_NO7", length = 50)
    private String refNo7;

    @Column(name = "REF_NO8", length = 50)
    private String refNo8;

    @Column(name = "REF_NO9", length = 50)
    private String refNo9;

    @Column(name = "REG_ID", length = 100, nullable = false)
    private String regId;

    @Column(name = "REG_DTM", length = 14, nullable = false)
    private String regDtm;

    @Column(name = "MOD_ID", length = 100, nullable = false)
    private String modId;

    @Column(name = "MOD_DTM", length = 14, nullable = false)
    private String modDtm;
}
